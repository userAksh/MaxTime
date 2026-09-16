import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { writeAuditLog } from "./audit";
import { Booking, BookingParticipant, InventorySlot, Service } from "./types";

/** Only while the slot is actually still held/confirmed does changing headcount make sense —
 *  once checked in, completed, or cancelled, this is the wrong operation (use the cancellation
 *  flow instead). */
const MODIFIABLE_STATUSES = ["PENDING_PAYMENT", "CONFIRMED"];

function canModify(booking: Booking, callerUserId: string, callerPartnerId?: string): boolean {
  return booking.customerUserId === callerUserId || (!!callerPartnerId && booking.partnerId === callerPartnerId);
}

function recomputeAmount(service: Service | undefined, price: number, participantCount: number): number {
  return service?.pricingUnit === "PER_PERSON" ? price * participantCount : price;
}

/**
 * §17 — "Participant additions/removals must update capacity safely." Joining an existing
 * booking consumes one more unit of the same slot's `remaining` — the same atomic-transaction
 * guarantee as createBookingHold, so a booking can never grow past what the slot actually has
 * left, even if two people try to join the same booking at once.
 */
export const addParticipant = onCall(async (request) => {
  const callerUserId = request.auth?.uid;
  if (!callerUserId) throw new HttpsError("unauthenticated", "Sign in required.");
  const callerPartnerId = request.auth?.token?.partnerId as string | undefined;

  const bookingId = request.data?.bookingId as string;
  const displayName = request.data?.displayName as string;
  const participantUserId = (request.data?.userId as string | undefined) ?? null;
  if (!bookingId || !displayName) {
    throw new HttpsError("invalid-argument", "bookingId and displayName are required.");
  }

  return db.runTransaction(async (tx) => {
    const bookingRef = db.collection("bookings").doc(bookingId);
    const bookingSnap = await tx.get(bookingRef);
    if (!bookingSnap.exists) throw new HttpsError("not-found", "Booking not found.");
    const booking = bookingSnap.data() as Booking;

    if (!canModify(booking, callerUserId, callerPartnerId)) {
      throw new HttpsError("permission-denied", "Not authorized to modify this booking.");
    }
    if (!MODIFIABLE_STATUSES.includes(booking.status)) {
      throw new HttpsError("failed-precondition", `Cannot add participants to a ${booking.status} booking.`);
    }

    const slotRef = db.collection("inventory_slots").doc(booking.slotId);
    const slotSnap = await tx.get(slotRef);
    if (!slotSnap.exists) throw new HttpsError("not-found", "Slot not found.");
    const slot = slotSnap.data() as InventorySlot;

    const serviceSnap = await tx.get(db.collection("services").doc(booking.serviceId));
    const service = serviceSnap.data() as Service | undefined;
    // An exclusive court slot is already fully held by this booking (slotUnitsHeld=1 regardless
    // of headcount) — adding another player doesn't need another unit from a pool that was never
    // shared to begin with, unlike a class/group session where each extra person really does need
    // its own spot out of the slot's shared capacity.
    const isExclusiveCourt = service?.format === "COURT_BOOKING";
    if (!isExclusiveCourt && slot.remaining < 1) {
      throw new HttpsError("resource-exhausted", "No spots left in this slot.");
    }

    const newParticipantCount = booking.participantCount + 1;
    const newAmount = recomputeAmount(service, slot.price, newParticipantCount);

    if (!isExclusiveCourt) {
      tx.update(slotRef, { remaining: slot.remaining - 1, updatedAt: FieldValue.serverTimestamp() });
    }
    tx.update(bookingRef, {
      participantCount: newParticipantCount,
      amount: newAmount,
      updatedAt: FieldValue.serverTimestamp(),
    });

    const participantRef = db.collection("booking_participants").doc();
    const participant: Omit<BookingParticipant, "id"> = {
      bookingId,
      userId: participantUserId,
      displayName,
      status: "ACTIVE",
    };
    tx.set(participantRef, participant);

    await writeAuditLog(
      {
        actorUserId: callerUserId,
        actorRole: callerPartnerId ? "PARTNER" : "CUSTOMER",
        entityType: "Booking",
        entityId: bookingId,
        action: "ADD_PARTICIPANT",
        before: { participantCount: booking.participantCount, amount: booking.amount },
        after: { participantCount: newParticipantCount, amount: newAmount },
      },
      tx
    );

    return { participantId: participantRef.id, participantCount: newParticipantCount, amount: newAmount };
  });
});

/**
 * Symmetric to addParticipant — releases the capacity back to the slot immediately rather than
 * waiting for anyone else to notice a spot opened up. Refuses to remove the last participant:
 * that's a cancellation, a different (and more consequential) flow with its own policy/refund
 * handling, not something this endpoint should do as a side effect.
 */
export const removeParticipant = onCall(async (request) => {
  const callerUserId = request.auth?.uid;
  if (!callerUserId) throw new HttpsError("unauthenticated", "Sign in required.");
  const callerPartnerId = request.auth?.token?.partnerId as string | undefined;

  const bookingId = request.data?.bookingId as string;
  const participantId = request.data?.participantId as string;
  if (!bookingId || !participantId) {
    throw new HttpsError("invalid-argument", "bookingId and participantId are required.");
  }

  return db.runTransaction(async (tx) => {
    const bookingRef = db.collection("bookings").doc(bookingId);
    const bookingSnap = await tx.get(bookingRef);
    if (!bookingSnap.exists) throw new HttpsError("not-found", "Booking not found.");
    const booking = bookingSnap.data() as Booking;

    if (!canModify(booking, callerUserId, callerPartnerId)) {
      throw new HttpsError("permission-denied", "Not authorized to modify this booking.");
    }
    if (!MODIFIABLE_STATUSES.includes(booking.status)) {
      throw new HttpsError("failed-precondition", `Cannot remove participants from a ${booking.status} booking.`);
    }
    if (booking.participantCount <= 1) {
      throw new HttpsError(
        "failed-precondition",
        "Cannot remove the last participant — cancel the booking instead."
      );
    }

    const participantRef = db.collection("booking_participants").doc(participantId);
    const participantSnap = await tx.get(participantRef);
    if (!participantSnap.exists) throw new HttpsError("not-found", "Participant not found.");
    const participant = participantSnap.data() as BookingParticipant;
    if (participant.bookingId !== bookingId) {
      throw new HttpsError("invalid-argument", "Participant does not belong to this booking.");
    }
    if (participant.status !== "ACTIVE") {
      throw new HttpsError("failed-precondition", "Participant already removed.");
    }

    const slotRef = db.collection("inventory_slots").doc(booking.slotId);
    const slotSnap = await tx.get(slotRef);
    const serviceSnap = await tx.get(db.collection("services").doc(booking.serviceId));
    const service = serviceSnap.data() as Service | undefined;

    const newParticipantCount = booking.participantCount - 1;
    const newAmount = recomputeAmount(service, slotSnap.exists ? (slotSnap.data() as InventorySlot).price : 0, newParticipantCount);

    // Symmetric to addParticipant: an exclusive court slot's single held unit never changed when
    // headcount changed, so removing a player doesn't release anything back to a pool either.
    const isExclusiveCourt = service?.format === "COURT_BOOKING";
    if (!isExclusiveCourt && slotSnap.exists) {
      const slot = slotSnap.data() as InventorySlot;
      tx.update(slotRef, { remaining: slot.remaining + 1, updatedAt: FieldValue.serverTimestamp() });
    }
    tx.update(bookingRef, {
      participantCount: newParticipantCount,
      amount: newAmount,
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(participantRef, { status: "REMOVED" });

    await writeAuditLog(
      {
        actorUserId: callerUserId,
        actorRole: callerPartnerId ? "PARTNER" : "CUSTOMER",
        entityType: "Booking",
        entityId: bookingId,
        action: "REMOVE_PARTICIPANT",
        before: { participantCount: booking.participantCount, amount: booking.amount },
        after: { participantCount: newParticipantCount, amount: newAmount },
      },
      tx
    );

    return { participantCount: newParticipantCount, amount: newAmount };
  });
});
