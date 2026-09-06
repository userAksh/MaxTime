import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "./admin";
import { writeAuditLog } from "./audit";
import { Booking, CommissionRule, Payout } from "./types";

function isAdmin(request: { auth?: { token?: Record<string, unknown> } }): boolean {
  return request.auth?.token?.admin === true || request.auth?.token?.adminRole != null;
}

/**
 * §27 — "Partner should see money without doing calculations." Aggregates every COMPLETED
 * booking for a partner not already claimed by a prior payout (bookings are marked with
 * `payoutId` as part of this same run, so a booking can never be paid out twice), applies
 * whichever CommissionRule was actually in effect for each booking's date rather than whatever
 * rule is active today (§26 — "store commercial terms with effective dates so historical
 * bookings retain the correct calculation").
 *
 * MaxTime Ops/Finance only — a partner must never be able to trigger their own payout run.
 */
export const calculatePayout = onCall(async (request) => {
  if (!isAdmin(request)) throw new HttpsError("permission-denied", "Admin/Finance only.");

  const partnerId = request.data?.partnerId as string;
  const periodStartMillis = request.data?.periodStartMillis as number;
  const periodEndMillis = request.data?.periodEndMillis as number;
  if (!partnerId || !periodStartMillis || !periodEndMillis) {
    throw new HttpsError("invalid-argument", "partnerId, periodStartMillis, and periodEndMillis are required.");
  }
  const periodStart = Timestamp.fromMillis(periodStartMillis);
  const periodEnd = Timestamp.fromMillis(periodEndMillis);

  const bookingsSnap = await db
    .collection("bookings")
    .where("partnerId", "==", partnerId)
    .where("status", "==", "COMPLETED")
    .where("updatedAt", ">=", periodStart)
    .where("updatedAt", "<=", periodEnd)
    .get();

  const eligible = bookingsSnap.docs.filter((d) => !(d.data() as Booking).payoutId);
  if (eligible.length === 0) {
    return { payoutId: null, bookingCount: 0, netAmount: 0 };
  }

  const rulesSnap = await db
    .collection("commission_rules")
    .where("partnerId", "==", partnerId)
    .get();
  const rules = rulesSnap.docs.map((d) => d.data() as CommissionRule);

  function commissionFor(booking: Booking): number {
    const applicable = rules.find(
      (r) =>
        r.effectiveFrom.toMillis() <= booking.updatedAt.toMillis() &&
        (r.effectiveTo === null || r.effectiveTo.toMillis() >= booking.updatedAt.toMillis())
    );
    if (!applicable) return 0;
    switch (applicable.model) {
      case "PERCENTAGE":
        return Math.round((booking.amount * applicable.value) / 100);
      case "FIXED_FEE":
        return applicable.value;
      case "NET_PAYOUT":
        return Math.max(0, booking.amount - applicable.value);
      default:
        return 0;
    }
  }

  const grossAmount = eligible.reduce((sum, d) => sum + (d.data() as Booking).amount, 0);
  const commissionAmount = eligible.reduce((sum, d) => sum + commissionFor(d.data() as Booking), 0);
  const adjustments = 0; // refunds/manual adjustments layered in via a follow-up pass, not fabricated here
  const netAmount = grossAmount - commissionAmount - adjustments;

  const payoutRef = db.collection("payouts").doc();
  await db.runTransaction(async (tx) => {
    const payout: Omit<Payout, "id"> = {
      partnerId,
      periodStart,
      periodEnd,
      grossAmount,
      commissionAmount,
      adjustments,
      netAmount,
      status: "SCHEDULED",
      bookingIds: eligible.map((d) => d.id),
      expectedPayoutDate: Timestamp.fromMillis(Date.now() + 7 * 24 * 60 * 60 * 1000),
      createdAt: FieldValue.serverTimestamp() as never,
      paidAt: null,
    };
    tx.set(payoutRef, payout);
    for (const doc of eligible) {
      tx.update(doc.ref, { payoutId: payoutRef.id });
    }
    await writeAuditLog(
      {
        actorUserId: request.auth!.uid,
        actorRole: "ADMIN",
        entityType: "Payout",
        entityId: payoutRef.id,
        action: "CALCULATE",
        after: { partnerId, grossAmount, commissionAmount, netAmount, bookingCount: eligible.length },
      },
      tx
    );
  });

  return { payoutId: payoutRef.id, bookingCount: eligible.length, netAmount };
});

/** §27 — "Admin can place a payout on hold with a reason." */
export const setPayoutHold = onCall(async (request) => {
  if (!isAdmin(request)) throw new HttpsError("permission-denied", "Admin/Finance only.");

  const payoutId = request.data?.payoutId as string;
  const hold = request.data?.hold as boolean;
  const reason = request.data?.reason as string | undefined;
  if (!payoutId || typeof hold !== "boolean") {
    throw new HttpsError("invalid-argument", "payoutId and hold are required.");
  }
  if (hold && !reason) throw new HttpsError("invalid-argument", "reason is required to place a hold.");

  await db.runTransaction(async (tx) => {
    const ref = db.collection("payouts").doc(payoutId);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Payout not found.");
    const before = snap.data() as Payout;
    const nextStatus = hold ? "ON_HOLD" : "SCHEDULED";
    tx.update(ref, { status: nextStatus, holdReason: hold ? reason : FieldValue.delete() });
    await writeAuditLog(
      {
        actorUserId: request.auth!.uid,
        actorRole: "ADMIN",
        entityType: "Payout",
        entityId: payoutId,
        action: hold ? "HOLD" : "RELEASE_HOLD",
        reason,
        before: { status: before.status },
        after: { status: nextStatus },
      },
      tx
    );
  });

  return { ok: true };
});
