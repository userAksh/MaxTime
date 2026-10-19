import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { FieldValue } from "firebase-admin/firestore";
import * as crypto from "crypto";
import Razorpay from "razorpay";
import { db } from "./admin";
import { revertAppliedReward } from "./referral";
import { Booking, InventorySlot, PaymentStatus, Referral, ReferralReward } from "./types";
import { razorpayWebhookSecret } from "./razorpay";
import { trackBackendEvent } from "./analytics";

type WebhookOutcome =
  | { kind: "already-processed" }
  | { kind: "booking-not-pending"; status: string }
  | { kind: "confirmed"; booking: Booking; bookingId: string }
  | { kind: "payment-failed"; booking: Booking; bookingId: string; rewardReverted: boolean };

/**
 * §14/§25/§40 — Razorpay webhook. Everything below the signature check is unchanged from this
 * function's original generic-gateway shape.
 *
 * Idempotency (§32/§39 — "payment webhook" is explicitly called out): a Payment document is keyed
 * by providerTransactionId (Razorpay's payment id), and its existence is checked inside the same
 * transaction that would otherwise process it — a repeated webhook call for a transaction already
 * recorded is a clean no-op, never a double-confirm or a double-refund-trigger.
 */
export const paymentWebhook = onRequest({ secrets: [razorpayWebhookSecret] }, async (req, res) => {
  const signature = req.headers["x-razorpay-signature"] as string | undefined;
  // Must verify against the RAW body bytes, not a re-serialized req.body — re-serializing (even
  // via JSON.stringify) can reorder keys or change whitespace and silently break the signature.
  const rawBody = (req as unknown as { rawBody?: Buffer }).rawBody;
  const validSignature =
    !!signature && !!rawBody && Razorpay.validateWebhookSignature(rawBody.toString(), signature, razorpayWebhookSecret.value());
  if (!validSignature) {
    // TEMP diagnostic — computes the HMAC independently of Razorpay's SDK helper so we can tell
    // apart "wrong secret" from "wrong body bytes" from "SDK helper bug", without ever logging
    // the secret value or the received signature in full (only short prefixes — safe, since a
    // partial HMAC digest reveals nothing about the key).
    const manualDigest = rawBody
      ? crypto.createHmac("sha256", razorpayWebhookSecret.value()).update(rawBody).digest("hex")
      : null;
    logger.warn("paymentWebhook signature check failed", {
      hasSignatureHeader: !!signature,
      hasRawBody: !!rawBody,
      rawBodyLength: rawBody?.length ?? 0,
      contentType: req.headers["content-type"],
      receivedSignaturePrefix: signature?.slice(0, 10),
      manualDigestPrefix: manualDigest?.slice(0, 10),
      digestsMatch: manualDigest === signature,
      secretLength: razorpayWebhookSecret.value().length,
    });
    res.status(400).send("Invalid or missing signature.");
    return;
  }

  logger.info("paymentWebhook signature verified", { event: req.body?.event });

  const event = req.body?.event as string | undefined;
  const paymentEntity = req.body?.payload?.payment?.entity as
    | { id: string; order_id: string; amount: number }
    | undefined;
  if (!paymentEntity?.id || !paymentEntity.order_id) {
    logger.warn("paymentWebhook missing payment entity", { event, bodyKeys: Object.keys(req.body ?? {}) });
    res.status(400).send("Missing payment entity in webhook payload.");
    return;
  }
  if (event !== "payment.captured" && event !== "payment.failed") {
    // Not an event this function needs to act on (e.g. order.paid fires alongside
    // payment.captured for the same payment) — acknowledge so Razorpay doesn't retry it forever.
    logger.info("paymentWebhook ignoring event", { event });
    res.status(200).send("ignored");
    return;
  }

  const providerTransactionId = paymentEntity.id;
  const status: "SUCCEEDED" | "FAILED" = event === "payment.captured" ? "SUCCEEDED" : "FAILED";

  try {
    const outcome: WebhookOutcome = await db.runTransaction(async (tx) => {
      const paymentRef = db.collection("payments").doc(providerTransactionId);
      const existingPayment = await tx.get(paymentRef);
      if (existingPayment.exists) {
        return { kind: "already-processed" };
      }

      const bookingQuery = await tx.get(
        db.collection("bookings").where("providerOrderId", "==", paymentEntity.order_id).limit(1)
      );
      if (bookingQuery.empty) throw new Error(`No booking found for order ${paymentEntity.order_id}.`);
      const bookingRef = bookingQuery.docs[0].ref;
      const booking = bookingQuery.docs[0].data() as Booking;

      if (booking.status !== "PENDING_PAYMENT") {
        // Already resolved (expired, or a previous webhook attempt already landed) — do not
        // re-process, and specifically never re-release inventory that's already been settled.
        return { kind: "booking-not-pending", status: booking.status };
      }

      // Read now — before this transaction's first write below — regardless of whether this
      // attempt will actually turn out to be SUCCEEDED or FAILED, since Firestore transactions
      // require every read before any write and the referral relevance isn't known until after
      // `paymentStatus` is checked further down.
      const rewardSnap = booking.appliedReferralRewardId
        ? await tx.get(db.collection("referral_rewards").doc(booking.appliedReferralRewardId))
        : null;
      const referralSnap =
        rewardSnap?.exists ? await tx.get(db.collection("referrals").doc((rewardSnap.data() as ReferralReward).referralId)) : null;

      const paymentStatus: PaymentStatus = status;
      tx.set(paymentRef, {
        bookingId: bookingRef.id,
        customerUserId: booking.customerUserId,
        grossAmount: typeof paymentEntity.amount === "number" ? paymentEntity.amount / 100 : booking.amount,
        currency: booking.currency,
        provider: "razorpay",
        providerTransactionId,
        status: paymentStatus,
        createdAt: FieldValue.serverTimestamp(),
        confirmedAt: paymentStatus === "SUCCEEDED" ? FieldValue.serverTimestamp() : null,
      });

      if (paymentStatus === "SUCCEEDED") {
        tx.update(bookingRef, { status: "CONFIRMED", updatedAt: FieldValue.serverTimestamp() });
        return { kind: "confirmed", booking, bookingId: bookingRef.id };
      } else {
        // This attempt actually failed (not merely expired) — release the hold now rather than
        // waiting for expireStaleHolds, so the slot is immediately rebookable by someone else.
        const slotRef = db.collection("inventory_slots").doc(booking.slotId);
        const slotSnap = await tx.get(slotRef);
        if (slotSnap.exists) {
          const slot = slotSnap.data() as InventorySlot;
          tx.update(slotRef, {
            remaining: slot.remaining + booking.slotUnitsHeld,
            updatedAt: FieldValue.serverTimestamp(),
          });
        }
        tx.update(bookingRef, { status: "PAYMENT_FAILED", updatedAt: FieldValue.serverTimestamp() });

        let rewardReverted = false;
        if (rewardSnap && referralSnap?.exists) {
          const referral = referralSnap.data() as Referral;
          revertAppliedReward(
            tx,
            rewardSnap,
            { referrerUserId: referral.referrerUserId, referredUserId: referral.referredUserId },
            bookingRef,
            "PAYMENT_FAILED",
            { userId: "system", role: "SYSTEM" }
          );
          rewardReverted = true;
        }
        return { kind: "payment-failed", booking, bookingId: bookingRef.id, rewardReverted };
      }
    });

    logger.info("paymentWebhook processed", {
      event,
      providerTransactionId,
      orderId: paymentEntity.order_id,
      outcome: outcome.kind,
    });

    // Fired after the transaction has committed, using values captured from inside it — never
    // from inside the runTransaction callback itself (see analytics.ts doc comment).
    if (outcome.kind === "confirmed") {
      const amountMinor = Math.round(outcome.booking.amount * 100);
      trackBackendEvent("payment_succeeded", outcome.booking.customerUserId, {
        booking_id: outcome.bookingId,
        category_id: outcome.booking.activityCategory,
        amount_minor: amountMinor,
        currency: outcome.booking.currency,
        provider: "razorpay",
      });
      trackBackendEvent("booking_confirmed", outcome.booking.customerUserId, {
        booking_id: outcome.bookingId,
        category_id: outcome.booking.activityCategory,
        service_id: outcome.booking.serviceId,
        venue_id: outcome.booking.venueId,
        amount_minor: amountMinor,
        currency: outcome.booking.currency,
      });
    } else if (outcome.kind === "payment-failed") {
      const amountMinor = Math.round(outcome.booking.amount * 100);
      trackBackendEvent("payment_failed", outcome.booking.customerUserId, {
        booking_id: outcome.bookingId,
        category_id: outcome.booking.activityCategory,
        amount_minor: amountMinor,
        currency: outcome.booking.currency,
        provider: "razorpay",
      });
      if (outcome.rewardReverted) {
        trackBackendEvent("referral_reward_reverted", outcome.booking.customerUserId, {
          booking_id: outcome.bookingId,
          reason: "PAYMENT_FAILED",
        });
      }
    }

    res.status(200).send("ok");
  } catch (err) {
    logger.error("paymentWebhook processing error", {
      event,
      providerTransactionId,
      orderId: paymentEntity.order_id,
      error: err instanceof Error ? err.message : String(err),
    });
    res.status(500).send(err instanceof Error ? err.message : "Internal error");
  }
});
