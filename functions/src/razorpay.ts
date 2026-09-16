import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import Razorpay from "razorpay";
import { db } from "./admin";
import { Booking } from "./types";

/** Google Secret Manager-backed (not a .env file — this project has no prior secrets convention,
 *  and Secret Manager is Firebase's current recommendation over the deprecated functions.config()).
 *  Set via `firebase functions:secrets:set RAZORPAY_KEY_ID` etc. — never checked into source. */
export const razorpayKeyId = defineSecret("RAZORPAY_KEY_ID");
export const razorpayKeySecret = defineSecret("RAZORPAY_KEY_SECRET");
export const razorpayWebhookSecret = defineSecret("RAZORPAY_WEBHOOK_SECRET");

/**
 * Creates a real Razorpay Order for an already-held booking (see createBookingHold in
 * booking.ts) and stores its id on the booking so the webhook (payments.ts) can map a payment
 * back to it without depending on Razorpay's notes-propagation behavior. Only ever returns the
 * key **id** to the caller — the key secret never leaves this function.
 */
export const createRazorpayOrder = onCall({ secrets: [razorpayKeyId, razorpayKeySecret] }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const bookingId = request.data?.bookingId as string;
  if (!bookingId) throw new HttpsError("invalid-argument", "bookingId is required.");

  const bookingRef = db.collection("bookings").doc(bookingId);
  const bookingSnap = await bookingRef.get();
  if (!bookingSnap.exists) throw new HttpsError("not-found", "Booking not found.");
  const booking = bookingSnap.data() as Booking;

  if (booking.customerUserId !== uid) {
    throw new HttpsError("permission-denied", "This booking belongs to a different customer.");
  }
  if (booking.status !== "PENDING_PAYMENT") {
    throw new HttpsError("failed-precondition", `Booking is ${booking.status}, not awaiting payment.`);
  }

  const razorpay = new Razorpay({ key_id: razorpayKeyId.value(), key_secret: razorpayKeySecret.value() });
  const order = await razorpay.orders.create({
    // Razorpay amounts are in the currency's smallest subunit — paise for INR.
    amount: booking.amount * 100,
    currency: booking.currency,
    receipt: bookingId,
    notes: { bookingId },
  });

  await bookingRef.update({ providerOrderId: order.id });

  return {
    orderId: order.id,
    amount: booking.amount,
    currency: booking.currency,
    keyId: razorpayKeyId.value(),
  };
});
