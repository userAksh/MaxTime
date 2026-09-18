import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { writeAuditLog } from "./audit";
import { qualifyReferralIfApplicable, redeemAppliedReward, revertAppliedReward } from "./referral";
import { AttendanceStatus, Booking, BOOKING_TRANSITIONS, BookingStatus, Referral, ReferralReward } from "./types";

const ATTENDANCE_TO_BOOKING_STATUS: Record<AttendanceStatus, BookingStatus> = {
  CHECKED_IN: "CHECKED_IN",
  NO_SHOW: "NO_SHOW",
  COMPLETED: "COMPLETED",
};

/**
 * §20 — "Partner opens Today's Bookings -> Check In / Mark Attended." One callable for all three
 * transitions since they share the same authorization + audit-logging shape; BOOKING_TRANSITIONS
 * is the single source of truth for which one is actually legal from the booking's current state
 * (e.g. you can't mark COMPLETED a booking that was never CHECKED_IN).
 */
export const markAttendance = onCall(async (request) => {
  const partnerId = request.auth?.token?.partnerId as string | undefined;
  if (!partnerId) throw new HttpsError("permission-denied", "Not a partner user.");

  const bookingId = request.data?.bookingId as string;
  const status = request.data?.status as AttendanceStatus;
  if (!bookingId || !(status in ATTENDANCE_TO_BOOKING_STATUS)) {
    throw new HttpsError("invalid-argument", "bookingId and a valid status are required.");
  }
  const nextBookingStatus = ATTENDANCE_TO_BOOKING_STATUS[status];

  await db.runTransaction(async (tx) => {
    const bookingRef = db.collection("bookings").doc(bookingId);
    const bookingSnap = await tx.get(bookingRef);
    if (!bookingSnap.exists) throw new HttpsError("not-found", "Booking not found.");
    const booking = bookingSnap.data() as Booking;

    if (booking.partnerId !== partnerId) {
      throw new HttpsError("permission-denied", "This booking belongs to a different partner.");
    }
    if (!BOOKING_TRANSITIONS[booking.status].includes(nextBookingStatus)) {
      throw new HttpsError(
        "failed-precondition",
        `Cannot mark ${status} from booking status ${booking.status}.`
      );
    }

    // Referral reads — must happen here, before any of this transaction's writes below (the
    // Firestore transaction rule: all reads before any writes).
    let referralSnap: FirebaseFirestore.DocumentSnapshot | null = null;
    let referrerRewardSnap: FirebaseFirestore.DocumentSnapshot | null = null;
    // Independent of the above: whichever single reward (if any) was applied to THIS booking at
    // checkout — could be the referee's own reward (the common case) or a referrer redeeming
    // their own later reward. Orthogonal to qualification: this finalizes the specific reward
    // spent here, regardless of whose it is or whether it's even related to this referral.
    let redeemRewardSnap: FirebaseFirestore.DocumentSnapshot | null = null;
    if (nextBookingStatus === "COMPLETED") {
      // Doc ID of `referrals` IS the referee's uid — see types.ts's Referral doc comment for why
      // this alone (no query) is enough to know whether this booking's customer was ever
      // referred, and whether anyone has already qualified this referral before.
      referralSnap = await tx.get(db.collection("referrals").doc(booking.customerUserId));
      if (referralSnap.exists && (referralSnap.data() as Referral).status === "PENDING") {
        const referral = referralSnap.data() as Referral;
        referrerRewardSnap = await tx.get(db.collection("referral_rewards").doc(referral.referrerRewardId));
      }
      if (booking.appliedReferralRewardId) {
        redeemRewardSnap = await tx.get(db.collection("referral_rewards").doc(booking.appliedReferralRewardId));
      }
    }
    let revertRewardSnap: FirebaseFirestore.DocumentSnapshot | null = null;
    let revertReferralSnap: FirebaseFirestore.DocumentSnapshot | null = null;
    if (nextBookingStatus === "NO_SHOW" && booking.appliedReferralRewardId) {
      revertRewardSnap = await tx.get(db.collection("referral_rewards").doc(booking.appliedReferralRewardId));
      if (revertRewardSnap.exists) {
        revertReferralSnap = await tx.get(
          db.collection("referrals").doc((revertRewardSnap.data() as ReferralReward).referralId)
        );
      }
    }

    tx.update(bookingRef, { status: nextBookingStatus, updatedAt: FieldValue.serverTimestamp() });

    const attendanceRef = db.collection("attendance").doc();
    tx.set(attendanceRef, {
      bookingId,
      participantId: null,
      status,
      markedBy: request.auth!.uid,
      createdAt: FieldValue.serverTimestamp(),
    });

    await writeAuditLog(
      {
        actorUserId: request.auth!.uid,
        actorRole: "PARTNER",
        entityType: "Booking",
        entityId: bookingId,
        action: `ATTENDANCE_${status}`,
        before: { status: booking.status },
        after: { status: nextBookingStatus },
      },
      tx
    );

    const actor = { userId: request.auth!.uid, role: "PARTNER" };
    if (referralSnap) {
      qualifyReferralIfApplicable(tx, referralSnap, referrerRewardSnap, bookingId, actor);
    }
    if (redeemRewardSnap) {
      redeemAppliedReward(tx, redeemRewardSnap, actor);
    }
    if (revertRewardSnap && revertReferralSnap?.exists) {
      const revertReferral = revertReferralSnap.data() as Referral;
      revertAppliedReward(
        tx,
        revertRewardSnap,
        { referrerUserId: revertReferral.referrerUserId, referredUserId: revertReferral.referredUserId },
        bookingRef,
        "NO_SHOW",
        actor
      );
    }
  });

  return { ok: true };
});
