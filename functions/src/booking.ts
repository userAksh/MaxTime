import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./admin";
import { writeAuditLog } from "./audit";
import { revertAppliedReward, writeReferralEvent } from "./referral";
import { Booking, BookingParticipant, InventorySlot, Referral, ReferralReward, Service } from "./types";

const HOLD_MINUTES = 15;

export interface ParticipantInput {
  userId?: string | null;
  displayName: string;
}

function computeAmount(service: Service | undefined, price: number, participantCount: number): number {
  return service?.pricingUnit === "PER_PERSON" ? price * participantCount : price;
}

/**
 * §14/§39 — the single most correctness-critical operation in this whole system: "two customers
 * must never successfully buy the same final inventory unit." Firestore transactions serialize
 * concurrent writes to the same document, so two simultaneous calls against the same slot cannot
 * both read the same `remaining` and both succeed — the second transaction retries against the
 * already-decremented value and fails cleanly with resource-exhausted instead of overselling.
 *
 * §17 — a booking may carry multiple participants from the start ("me + 3 friends"), each getting
 * its own BookingParticipant document rather than being flattened into a bare headcount, so a
 * partner sees who's actually coming and individual participants can be added/removed later
 * (see addParticipant/removeParticipant) without losing that identity.
 *
 * idempotencyKey (client-generated, one per checkout attempt) makes a retried call — e.g. a
 * flaky-network double-tap — resolve to the same booking instead of creating a second hold.
 */
export const createBookingHold = onCall(async (request) => {
  const customerUserId = request.auth?.uid;
  if (!customerUserId) throw new HttpsError("unauthenticated", "Sign in required.");

  const slotId = request.data?.slotId as string;
  const participants = request.data?.participants as ParticipantInput[] | undefined;
  const idempotencyKey = request.data?.idempotencyKey as string;
  const applyReferralRewardId = request.data?.applyReferralRewardId as string | undefined;
  if (!slotId || !idempotencyKey || !participants || participants.length < 1) {
    throw new HttpsError("invalid-argument", "slotId, a non-empty participants array, and idempotencyKey are required.");
  }
  if (participants.some((p) => !p.displayName)) {
    throw new HttpsError("invalid-argument", "Every participant needs a displayName.");
  }
  const participantCount = participants.length;

  const idemRef = db.collection("idempotency_keys").doc(idempotencyKey);

  return db.runTransaction(async (tx) => {
    const idemSnap = await tx.get(idemRef);
    if (idemSnap.exists) {
      return { bookingId: idemSnap.data()!.bookingId as string, alreadyExisted: true };
    }

    const slotRef = db.collection("inventory_slots").doc(slotId);
    const slotSnap = await tx.get(slotRef);
    if (!slotSnap.exists) throw new HttpsError("not-found", "Slot not found.");
    const slot = slotSnap.data() as InventorySlot;

    if (slot.status !== "AVAILABLE") {
      throw new HttpsError("failed-precondition", "This slot is no longer available.");
    }

    const serviceSnap = await tx.get(db.collection("services").doc(slot.serviceId));
    const service = serviceSnap.data() as Service | undefined;
    // A court is an exclusive resource for that slot — whoever books it first gets the whole
    // thing regardless of headcount (1 unit), unlike a class/group session where independent
    // customers genuinely share the same slot up to its headcount capacity.
    const isExclusiveCourt = service?.format === "COURT_BOOKING";
    const unitsNeeded = isExclusiveCourt ? 1 : participantCount;

    if (slot.remaining < unitsNeeded) {
      throw new HttpsError(
        "resource-exhausted",
        isExclusiveCourt ? "This slot is already booked." : "Not enough spots left in this slot."
      );
    }

    // db.collection(...).doc() with no argument only generates an ID client-side — no network
    // call, so creating this ref here (before the reward read below needs to reference its id)
    // doesn't violate "all reads before any writes".
    const bookingRef = db.collection("bookings").doc();

    // Read alongside the other pre-write reads above (idemRef/slotRef/serviceSnap) — Firestore
    // transactions require every read before any write, and the writes below (slot decrement,
    // booking creation) can't be reordered after this.
    const rewardRef = applyReferralRewardId ? db.collection("referral_rewards").doc(applyReferralRewardId) : null;
    const rewardSnap = rewardRef ? await tx.get(rewardRef) : null;
    // Only needed to fully denormalize the referral_events entry below (referrerUserId isn't
    // always derivable from the reward doc alone — see revertAppliedReward's doc comment for the
    // same asymmetry) — read now, alongside everything else, before any writes.
    const referralSnap =
      rewardSnap?.exists ? await tx.get(db.collection("referrals").doc((rewardSnap.data() as ReferralReward).referralId)) : null;

    let amount = computeAmount(service, slot.price, participantCount);
    let referralDiscountAmount: number | null = null;

    if (rewardRef && rewardSnap) {
      if (!rewardSnap.exists) throw new HttpsError("not-found", "Referral reward not found.");
      const reward = rewardSnap.data() as ReferralReward;
      if (reward.beneficiaryUserId !== customerUserId) {
        throw new HttpsError("permission-denied", "This referral reward doesn't belong to you.");
      }
      const usableStatus = reward.role === "REFEREE" ? "RESERVED" : "ISSUED";
      if (reward.status !== usableStatus) {
        throw new HttpsError("failed-precondition", `This referral reward is ${reward.status}, not usable right now.`);
      }
      referralDiscountAmount = Math.min(Math.round((amount * reward.discountPercent) / 100), reward.discountCapAmount);
      amount = Math.max(0, amount - referralDiscountAmount);
      // Same double-spend prevention as the inventory_slots.remaining decrement below: reading
      // then writing this doc inside one transaction means a second concurrent call applying the
      // SAME reward retries against the post-write state and fails cleanly on the status check
      // above, instead of both bookings getting the discount.
      tx.update(rewardRef, {
        status: "APPLIED",
        bookingId: bookingRef.id,
        appliedDiscountAmount: referralDiscountAmount,
        appliedAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      writeAuditLog(
        {
          actorUserId: customerUserId,
          actorRole: "USER",
          entityType: "ReferralReward",
          entityId: rewardRef.id, // NOT reward.id — `.data()` never includes the doc's own id
          action: "REWARD_APPLIED",
          before: { status: reward.status },
          after: { status: "APPLIED", bookingId: bookingRef.id },
        },
        tx
      );
      if (referralSnap?.exists) {
        const referral = referralSnap.data() as Referral;
        writeReferralEvent(tx, {
          referralId: referral.referredUserId, // == referralSnap.id by construction
          eventType: "REWARD_APPLIED",
          actor: { userId: customerUserId, role: "USER" },
          referrerUserId: referral.referrerUserId,
          referredUserId: referral.referredUserId,
          metadata: { rewardId: rewardRef.id, bookingId: bookingRef.id, discountAmount: referralDiscountAmount },
        });
      }
    }

    const holdExpiresAt = Timestamp.fromMillis(Date.now() + HOLD_MINUTES * 60_000);

    tx.update(slotRef, {
      remaining: slot.remaining - unitsNeeded,
      updatedAt: FieldValue.serverTimestamp(),
    });

    const booking: Omit<Booking, "id"> = {
      slotId,
      serviceId: slot.serviceId,
      partnerId: slot.partnerId,
      venueId: slot.venueId,
      customerUserId,
      participantCount,
      slotUnitsHeld: unitsNeeded,
      status: "PENDING_PAYMENT",
      amount,
      currency: "INR",
      idempotencyKey,
      holdExpiresAt,
      appliedReferralRewardId: applyReferralRewardId ?? null,
      referralDiscountAmount,
      createdAt: FieldValue.serverTimestamp() as never,
      updatedAt: FieldValue.serverTimestamp() as never,
    };
    tx.set(bookingRef, booking);

    for (const participant of participants) {
      const participantRef = db.collection("booking_participants").doc();
      const doc: Omit<BookingParticipant, "id"> = {
        bookingId: bookingRef.id,
        userId: participant.userId ?? null,
        displayName: participant.displayName,
        status: "ACTIVE",
      };
      tx.set(participantRef, doc);
    }

    tx.set(idemRef, { bookingId: bookingRef.id, createdAt: FieldValue.serverTimestamp() });

    return { bookingId: bookingRef.id, alreadyExisted: false, amount, holdExpiresAtMillis: holdExpiresAt.toMillis() };
  });
});

/**
 * §40 — "Customer abandons checkout -> Hold expires and inventory returns." Runs on a timer
 * rather than waiting for anyone to notice, since an abandoned checkout has no natural trigger of
 * its own. Each stale booking is resolved in its own transaction so one bad document can't block
 * the rest of the sweep.
 */
export const expireStaleHolds = onSchedule("every 5 minutes", async () => {
  const now = Timestamp.now();
  const staleSnap = await db
    .collection("bookings")
    .where("status", "==", "PENDING_PAYMENT")
    .where("holdExpiresAt", "<=", now)
    .limit(200)
    .get();

  for (const doc of staleSnap.docs) {
    // eslint-disable-next-line no-await-in-loop
    await db.runTransaction(async (tx) => {
      const bookingRef = doc.ref;
      const bookingSnap = await tx.get(bookingRef);
      if (!bookingSnap.exists) return;
      const booking = bookingSnap.data() as Booking;
      if (booking.status !== "PENDING_PAYMENT") return; // resolved concurrently, e.g. payment landed just now

      const slotRef = db.collection("inventory_slots").doc(booking.slotId);
      const slotSnap = await tx.get(slotRef);
      // Read alongside slotSnap, before the writes below — see revertAppliedReward's doc comment.
      const rewardSnap = booking.appliedReferralRewardId
        ? await tx.get(db.collection("referral_rewards").doc(booking.appliedReferralRewardId))
        : null;
      const referralSnap =
        rewardSnap?.exists ? await tx.get(db.collection("referrals").doc((rewardSnap.data() as ReferralReward).referralId)) : null;

      if (slotSnap.exists) {
        const slot = slotSnap.data() as InventorySlot;
        tx.update(slotRef, {
          remaining: slot.remaining + booking.slotUnitsHeld,
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
      tx.update(bookingRef, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });

      if (rewardSnap && referralSnap?.exists) {
        const referral = referralSnap.data() as Referral;
        revertAppliedReward(
          tx,
          rewardSnap,
          { referrerUserId: referral.referrerUserId, referredUserId: referral.referredUserId },
          bookingRef,
          "EXPIRED",
          { userId: "system", role: "SYSTEM" }
        );
      }
    });
  }
});
