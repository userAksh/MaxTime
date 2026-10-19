/**
 * Runs against the REAL Firestore/Auth emulators (via `npm test` -> firebase emulators:exec) —
 * see referral.test.ts's header comment for why. paymentWebhook is an onRequest function (no
 * `.run()` helper like onCall), but firebase-functions v2 exports it as a directly-callable
 * (req, res) handler, so it's invoked here with minimal fake req/res objects rather than a real
 * HTTP round trip. trackBackendEvent is mocked purely to make call counts observable — its own
 * fail-open behavior is covered separately in analytics.test.ts.
 */
import * as crypto from "crypto";
import { Timestamp } from "firebase-admin/firestore";
import { CallableRequest } from "firebase-functions/v2/https";
import { db, auth } from "./admin";
import { createBookingHold } from "./booking";

jest.mock("./analytics", () => ({ trackBackendEvent: jest.fn() }));
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { trackBackendEvent } = require("./analytics") as { trackBackendEvent: jest.Mock };

// Imported after the mock so paymentWebhook's own `import { trackBackendEvent } from "./analytics"`
// resolves to the mocked module.
import { paymentWebhook } from "./payments";

const WEBHOOK_SECRET = "test-webhook-secret";

function fakeRequest<T>(data: T, uid: string | null): CallableRequest<T> {
  return { data, auth: uid ? { uid, token: {} } : undefined, rawRequest: {}, acceptsStreaming: false } as unknown as CallableRequest<T>;
}

let uidCounter = 0;
async function createTestUser(): Promise<string> {
  uidCounter += 1;
  const uid = `test-user-${Date.now()}-${uidCounter}`;
  await auth.createUser({ uid });
  return uid;
}

async function seedAvailableSlot(remaining = 5, price = 500): Promise<string> {
  const ref = db.collection("inventory_slots").doc();
  await ref.set({
    ruleId: null,
    partnerId: "test-partner",
    venueId: "test-venue",
    resourceId: null,
    serviceId: "nonexistent-service",
    activityCategory: "badminton",
    date: "2026-01-01",
    startTime: "18:00",
    endTime: "19:00",
    capacity: 10,
    remaining,
    price,
    status: "AVAILABLE",
    createdAt: Timestamp.now(),
    updatedAt: Timestamp.now(),
  });
  return ref.id;
}

function fakeWebhookCall(event: string, payload: Record<string, unknown>) {
  const body = JSON.stringify({ event, payload });
  const signature = crypto.createHmac("sha256", WEBHOOK_SECRET).update(body).digest("hex");
  const req = {
    headers: { "x-razorpay-signature": signature, "content-type": "application/json" },
    rawBody: Buffer.from(body),
    body: { event, payload },
  };
  let statusCode = 0;
  let sentBody = "";
  const res = {
    status(code: number) {
      statusCode = code;
      return this;
    },
    send(b: string) {
      sentBody = b;
      return this;
    },
  };
  return { req, res, getStatus: () => statusCode, getBody: () => sentBody };
}

describe("paymentWebhook idempotency", () => {
  beforeAll(() => {
    process.env.RAZORPAY_WEBHOOK_SECRET = WEBHOOK_SECRET;
  });

  beforeEach(() => {
    trackBackendEvent.mockClear();
  });

  it("confirms a booking exactly once and fires analytics exactly once, even if the webhook is replayed", async () => {
    const customerUid = await createTestUser();
    const slotId = await seedAvailableSlot(5, 500);
    const holdResult = (await createBookingHold.run(
      fakeRequest({ slotId, participants: [{ displayName: "A" }], idempotencyKey: `idem-${slotId}` }, customerUid)
    )) as { bookingId: string };
    await db.collection("bookings").doc(holdResult.bookingId).update({ providerOrderId: `order-${slotId}` });

    const payload = { payment: { entity: { id: `pay-${slotId}`, order_id: `order-${slotId}`, amount: 50000 } } };

    const first = fakeWebhookCall("payment.captured", payload);
    await paymentWebhook(first.req as never, first.res as never);
    expect(first.getStatus()).toBe(200);

    const bookingAfterFirst = await db.collection("bookings").doc(holdResult.bookingId).get();
    expect(bookingAfterFirst.data()!.status).toBe("CONFIRMED");

    const paymentDocsAfterFirst = await db.collection("payments").where("bookingId", "==", holdResult.bookingId).get();
    expect(paymentDocsAfterFirst.size).toBe(1);

    // payment_succeeded + booking_confirmed, exactly once.
    expect(trackBackendEvent).toHaveBeenCalledTimes(2);
    expect(trackBackendEvent.mock.calls.map((c) => c[0])).toEqual(
      expect.arrayContaining(["payment_succeeded", "booking_confirmed"])
    );
    trackBackendEvent.mockClear();

    // Replay the EXACT same webhook event (Razorpay retries on any non-2xx, or just duplicates).
    const second = fakeWebhookCall("payment.captured", payload);
    await paymentWebhook(second.req as never, second.res as never);
    expect(second.getStatus()).toBe(200);
    expect(second.getBody()).toBe("ok");

    const bookingAfterSecond = await db.collection("bookings").doc(holdResult.bookingId).get();
    expect(bookingAfterSecond.data()!.status).toBe("CONFIRMED"); // unchanged, not re-processed

    const paymentDocsAfterSecond = await db.collection("payments").where("bookingId", "==", holdResult.bookingId).get();
    expect(paymentDocsAfterSecond.size).toBe(1); // still exactly one payment record, not duplicated

    // The replay must not fire payment_succeeded/booking_confirmed a second time.
    expect(trackBackendEvent).not.toHaveBeenCalled();
  });
});
