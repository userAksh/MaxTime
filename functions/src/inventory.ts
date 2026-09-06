import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { writeAuditLog } from "./audit";
import { AvailabilityRule, InventorySlot } from "./types";

function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

function toDateString(d: Date): string {
  return d.toISOString().slice(0, 10);
}

function addMinutes(time: string, minutes: number): string {
  const [h, m] = time.split(":").map(Number);
  const total = h * 60 + m + minutes;
  const hh = Math.floor(total / 60) % 24;
  const mm = total % 60;
  return `${hh.toString().padStart(2, "0")}:${mm.toString().padStart(2, "0")}`;
}

function timeToMinutes(time: string): number {
  const [h, m] = time.split(":").map(Number);
  return h * 60 + m;
}

/**
 * §11 — expands a recurring AvailabilityRule into concrete, individually-bookable InventorySlot
 * documents, one call at a time (triggered when a partner saves/edits a rule, or re-run daily to
 * roll the advance-booking window forward). Deterministic slot IDs make this idempotent: re-running
 * never resets or duplicates a slot that already exists, so an active hold or confirmed booking on
 * a slot is never disturbed by regenerating the rule it came from ("§39 — never publish overlapping
 * inventory that can be double-booked").
 */
export const generateInventoryFromRule = onCall(async (request) => {
  const partnerId = request.auth?.token?.partnerId as string | undefined;
  if (!partnerId) throw new HttpsError("permission-denied", "Not a partner user.");

  const ruleId = request.data?.ruleId as string;
  if (!ruleId) throw new HttpsError("invalid-argument", "ruleId is required.");

  const ruleSnap = await db.collection("availability_rules").doc(ruleId).get();
  if (!ruleSnap.exists) throw new HttpsError("not-found", "Availability rule not found.");
  const rule = ruleSnap.data() as AvailabilityRule;
  if (rule.partnerId !== partnerId) {
    throw new HttpsError("permission-denied", "This rule belongs to a different partner.");
  }
  if (!rule.active) throw new HttpsError("failed-precondition", "Rule is not active.");

  const today = new Date();
  let created = 0;
  const batch = db.batch();

  for (let offset = 0; offset <= rule.advanceBookingDays; offset++) {
    const date = addDays(today, offset);
    if (!rule.daysOfWeek.includes(date.getDay())) continue;
    const dateStr = toDateString(date);

    let slotStart = rule.startTime;
    while (timeToMinutes(slotStart) + rule.slotDurationMinutes <= timeToMinutes(rule.endTime)) {
      const slotEnd = addMinutes(slotStart, rule.slotDurationMinutes);
      const slotId = `${ruleId}_${dateStr}_${slotStart.replace(":", "")}`;
      const ref = db.collection("inventory_slots").doc(slotId);
      // eslint-disable-next-line no-await-in-loop
      const existing = await ref.get();
      if (!existing.exists) {
        const slot: Omit<InventorySlot, "id"> = {
          ruleId,
          partnerId,
          venueId: (rule as unknown as { venueId?: string }).venueId ?? "",
          resourceId: rule.resourceId,
          serviceId: rule.serviceId,
          activityCategory: (rule as unknown as { activityCategory?: string }).activityCategory ?? "",
          date: dateStr,
          startTime: slotStart,
          endTime: slotEnd,
          capacity: rule.capacity,
          remaining: rule.capacity,
          price: rule.price,
          status: "AVAILABLE",
          createdAt: FieldValue.serverTimestamp() as never,
          updatedAt: FieldValue.serverTimestamp() as never,
        };
        batch.set(ref, slot);
        created++;
      }
      slotStart = slotEnd;
    }
  }

  await batch.commit();
  return { created };
});

/**
 * §19 — partner blocking a slot. Never allowed to silently remove a confirmed booking: if one
 * exists, the caller must go through cancellation instead, so the customer is always notified.
 */
export const blockInventorySlot = onCall(async (request) => {
  const partnerId = request.auth?.token?.partnerId as string | undefined;
  if (!partnerId) throw new HttpsError("permission-denied", "Not a partner user.");

  const slotId = request.data?.slotId as string;
  const reason = request.data?.reason as string;
  if (!slotId || !reason) throw new HttpsError("invalid-argument", "slotId and reason are required.");

  await db.runTransaction(async (tx) => {
    const ref = db.collection("inventory_slots").doc(slotId);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Slot not found.");
    const slot = snap.data() as InventorySlot;
    if (slot.partnerId !== partnerId) {
      throw new HttpsError("permission-denied", "This slot belongs to a different partner.");
    }
    if (slot.remaining < slot.capacity) {
      // Capacity has been drawn down by holds/bookings — refuse the direct block.
      throw new HttpsError(
        "failed-precondition",
        "This slot has active bookings. Cancel them first instead of blocking directly."
      );
    }
    tx.update(ref, { status: "BLOCKED", blockedReason: reason, updatedAt: FieldValue.serverTimestamp() });
    await writeAuditLog(
      {
        actorUserId: request.auth!.uid,
        actorRole: "PARTNER",
        entityType: "InventorySlot",
        entityId: slotId,
        action: "BLOCK",
        reason,
        before: { status: slot.status },
        after: { status: "BLOCKED" },
      },
      tx
    );
  });

  return { ok: true };
});
