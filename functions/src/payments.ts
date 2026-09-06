import { onRequest } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { Booking, InventorySlot, PaymentStatus } from "./types";

/**
 * §14/§25/§40 — shaped as a generic payment-provider webhook so a real gateway (Razorpay/Stripe/
 * etc.) is a drop-in swap later: point its webhook at this URL, verify its signature at the top
 * of this handler, and everything below is unchanged. No real gateway is wired yet — this accepts
 * a plain JSON body so it can be driven by a mock/admin action in the meantime.
 *
 * Idempotency (§32/§39 — "payment webhook" is explicitly called out): a Payment document is keyed
 * by providerTransactionId, and its existence is checked inside the same transaction that would
 * otherwise process it — a repeated webhook call for a transaction already recorded is a clean
 * no-op, never a double-confirm or a double-refund-trigger.
 */
export const paymentWebhook = onRequest(async (req, res) => {
  // TODO: verify provider signature here once a real gateway is chosen — reject unsigned/invalid
  // requests before touching any booking/inventory state.
  const { bookingId, providerTransactionId, status, amount, provider } = req.body ?? {};
  if (!bookingId || !providerTransactionId || (status !== "SUCCEEDED" && status !== "FAILED")) {
    res.status(400).send("Missing or invalid required fields.");
    return;
  }

  try {
    await db.runTransaction(async (tx) => {
      const paymentRef = db.collection("payments").doc(String(providerTransactionId));
      const existingPayment = await tx.get(paymentRef);
      if (existingPayment.exists) return; // already processed — safe to ignore a retried webhook

      const bookingRef = db.collection("bookings").doc(String(bookingId));
      const bookingSnap = await tx.get(bookingRef);
      if (!bookingSnap.exists) throw new Error("Booking not found.");
      const booking = bookingSnap.data() as Booking;

      if (booking.status !== "PENDING_PAYMENT") {
        // Already resolved (expired, or a previous webhook attempt already landed) — do not
        // re-process, and specifically never re-release inventory that's already been settled.
        return;
      }

      const paymentStatus: PaymentStatus = status === "SUCCEEDED" ? "SUCCEEDED" : "FAILED";
      tx.set(paymentRef, {
        bookingId: booking.id ?? bookingId,
        customerUserId: booking.customerUserId,
        grossAmount: typeof amount === "number" ? amount : booking.amount,
        currency: booking.currency,
        provider: provider ?? "mock",
        providerTransactionId: String(providerTransactionId),
        status: paymentStatus,
        createdAt: FieldValue.serverTimestamp(),
        confirmedAt: paymentStatus === "SUCCEEDED" ? FieldValue.serverTimestamp() : null,
      });

      if (paymentStatus === "SUCCEEDED") {
        tx.update(bookingRef, { status: "CONFIRMED", updatedAt: FieldValue.serverTimestamp() });
      } else {
        // This attempt actually failed (not merely expired) — release the hold now rather than
        // waiting for expireStaleHolds, so the slot is immediately rebookable by someone else.
        const slotRef = db.collection("inventory_slots").doc(booking.slotId);
        const slotSnap = await tx.get(slotRef);
        if (slotSnap.exists) {
          const slot = slotSnap.data() as InventorySlot;
          tx.update(slotRef, {
            remaining: slot.remaining + booking.participantCount,
            updatedAt: FieldValue.serverTimestamp(),
          });
        }
        tx.update(bookingRef, { status: "PAYMENT_FAILED", updatedAt: FieldValue.serverTimestamp() });
      }
    });

    res.status(200).send("ok");
  } catch (err) {
    res.status(500).send(err instanceof Error ? err.message : "Internal error");
  }
});
