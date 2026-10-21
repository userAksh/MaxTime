/**
 * Runs against the REAL Firestore + Auth emulators (via `npm test` -> firebase emulators:exec),
 * never mocked db/auth — the entire point of the highest-value tests here (transaction/race
 * behavior for a fraud/money-adjacent feature) would be untestable against a mock. Cloud
 * Functions v2's `onCall`/`onSchedule` exports have a `.run(request)` method that invokes the
 * handler directly, bypassing HTTPS dispatch entirely, while still hitting the real emulated
 * Firestore/Auth underneath (firebase-admin auto-targets FIRESTORE_EMULATOR_HOST/
 * FIREBASE_AUTH_EMULATOR_HOST, which emulators:exec sets for this process automatically) — no
 * Functions emulator needed at all.
 */
import * as fs from "fs";
import * as path from "path";
import { Timestamp } from "firebase-admin/firestore";
import { CallableRequest } from "firebase-functions/v2/https";
import { initializeTestEnvironment, RulesTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { db, auth } from "./admin";
import {
  generateMyReferralCode,
  redeemReferralCode,
  getMyReferralSummary,
} from "./referral";
import { createBookingHold } from "./booking";
import { markAttendance } from "./attendance";
import {
  ReferralConfig,
  REFERRAL_TRANSITIONS,
  REWARD_TRANSITIONS,
  ReferralStatus,
  RewardStatus,
} from "./types";

function fakeRequest<T>(data: T, uid: string | null, token: Record<string, unknown> = {}): CallableRequest<T> {
  return {
    data,
    auth: uid ? { uid, token } : undefined,
    rawRequest: {},
    acceptsStreaming: false,
  } as unknown as CallableRequest<T>;
}

let uidCounter = 0;
async function createTestUser(): Promise<string> {
  uidCounter += 1;
  const uid = `test-user-${Date.now()}-${uidCounter}`;
  await auth.createUser({ uid });
  return uid;
}

async function seedActiveConfig(overrides: Partial<ReferralConfig> = {}): Promise<void> {
  const now = Timestamp.now();
  const config: Omit<ReferralConfig, "id"> = {
    referrerDiscountPercent: 20,
    referrerDiscountCapAmount: 100,
    refereeDiscountPercent: 10,
    refereeDiscountCapAmount: 50,
    currency: "INR",
    attributionWindowDays: 30,
    rewardValidityDays: 90,
    redemptionGraceHours: 24,
    codeLength: 6,
    active: true,
    effectiveFrom: Timestamp.fromMillis(now.toMillis() - 60_000),
    effectiveTo: null,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
  // Clear any prior config docs so getActiveReferralConfig's unordered `.find()` over
  // active-and-effective docs can't accidentally pick up a stale one from an earlier test.
  const existing = await db.collection("referral_configs").get();
  await Promise.all(existing.docs.map((d) => d.ref.delete()));
  await db.collection("referral_configs").add(config);
}

async function seedAvailableSlot(remaining = 5, price = 500): Promise<string> {
  const ref = db.collection("inventory_slots").doc();
  await ref.set({
    ruleId: null,
    partnerId: "test-partner",
    venueId: "test-venue",
    resourceId: null,
    serviceId: "nonexistent-service", // absent Service doc -> computeAmount falls back to flat `price`
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

async function generateCode(ownerUid: string): Promise<string> {
  const result = (await generateMyReferralCode.run(fakeRequest({}, ownerUid))) as { code: string };
  return result.code;
}

describe("referral state machines", () => {
  it("REFERRAL_TRANSITIONS only allows the explicitly listed next states", () => {
    const all: ReferralStatus[] = ["PENDING", "QUALIFIED", "EXPIRED", "INVALIDATED"];
    for (const from of all) {
      for (const to of all) {
        const allowed = REFERRAL_TRANSITIONS[from].includes(to);
        const shouldBeAllowed = REFERRAL_TRANSITIONS[from].some((s) => s === to);
        expect(allowed).toBe(shouldBeAllowed); // sanity — the table is its own source of truth
      }
    }
    // Explicit lock on the shape so an accidental future edit is caught in review.
    expect(REFERRAL_TRANSITIONS.EXPIRED).toEqual([]);
    expect(REFERRAL_TRANSITIONS.INVALIDATED).toEqual([]);
    expect(REFERRAL_TRANSITIONS.QUALIFIED).toEqual(["INVALIDATED"]);
  });

  it("REWARD_TRANSITIONS reverts APPLIED to either RESERVED (referee) or ISSUED (referrer)", () => {
    const all: RewardStatus[] = ["PENDING", "RESERVED", "APPLIED", "ISSUED", "REDEEMED", "EXPIRED", "VOID"];
    for (const s of all) {
      expect(Array.isArray(REWARD_TRANSITIONS[s])).toBe(true);
    }
    expect(REWARD_TRANSITIONS.APPLIED).toEqual(expect.arrayContaining(["RESERVED", "ISSUED", "REDEEMED", "VOID"]));
    expect(REWARD_TRANSITIONS.EXPIRED).toEqual([]);
    expect(REWARD_TRANSITIONS.VOID).toEqual([]);
    expect(REWARD_TRANSITIONS.REDEEMED).toEqual(["VOID"]);
  });
});

async function setReferralIdentity(uid: string, name: string, phoneNumber: string): Promise<void> {
  await db.collection("users").doc(uid).set({ name, phoneNumber });
  await auth.updateUser(uid, { phoneNumber });
}

async function deactivateAllReferralConfigs(): Promise<void> {
  const snap = await db.collection("referral_configs").get();
  await Promise.all(snap.docs.map((d) => d.ref.update({ active: false })));
}

describe("generateMyReferralCode", () => {
  // The regression this guards against: production had zero referral_configs documents at all,
  // so every single call failed with "Referral program is not currently active" — the whole
  // feature was gated behind a document nobody had ever created. A referral code must exist for
  // every user regardless of whether an admin has configured anything.
  it("still generates a working code with built-in defaults when no referral_configs document is active", async () => {
    await deactivateAllReferralConfigs();
    const uid = await createTestUser();
    await setReferralIdentity(uid, "Akshit", "+917895477553");

    const result = (await generateMyReferralCode.run(fakeRequest({}, uid))) as {
      code: string;
      refereeDiscountPercent: number;
      referrerDiscountPercent: number;
    };

    expect(result.code).toBe("AKS53");
    expect(result.refereeDiscountPercent).toBe(10); // built-in default — no config doc exists
    expect(result.referrerDiscountPercent).toBe(20);
  });

  it("derives the code from the first 3 letters of the first name + last 2 phone digits", async () => {
    await seedActiveConfig();
    const uid = await createTestUser();
    await setReferralIdentity(uid, "Priya Verma", "+919876543210");

    expect(await generateCode(uid)).toBe("PRI10");
  });

  it("pads a short/empty name rather than failing", async () => {
    await seedActiveConfig();
    const uid = await createTestUser();
    await setReferralIdentity(uid, "Al", "+919876543299");

    expect(await generateCode(uid)).toBe("ALM99"); // "AL" + "MXT" padding, truncated to 3 chars
  });

  it("appends a numeric suffix when two different users derive the same base code", async () => {
    await seedActiveConfig();
    const uidA = await createTestUser();
    const uidB = await createTestUser();
    // Same first name, different phone numbers that happen to share the same last 2 digits.
    await setReferralIdentity(uidA, "Aman", "+911112223344");
    await setReferralIdentity(uidB, "Aman", "+919998887744");

    const codeA = await generateCode(uidA);
    const codeB = await generateCode(uidB);

    expect(codeA).toBe("AMA44");
    expect(codeB).toBe("AMA441");
  });
});

describe("redeemReferralCode", () => {
  beforeEach(async () => {
    await seedActiveConfig();
  });

  it("getMyReferralSummary reflects the new relationship for both sides", async () => {
    const referrer = await createTestUser();
    const referee = await createTestUser();
    const code = await generateCode(referrer);
    await redeemReferralCode.run(fakeRequest({ code }, referee));

    const referrerSummary = (await getMyReferralSummary.run(fakeRequest({}, referrer))) as {
      asReferrer: { referredUserId: string; status: string }[];
    };
    expect(referrerSummary.asReferrer.some((r) => r.referredUserId === referee && r.status === "PENDING")).toBe(true);

    const refereeSummary = (await getMyReferralSummary.run(fakeRequest({}, referee))) as {
      asReferee: { referrerUserId: string; status: string } | null;
    };
    expect(refereeSummary.asReferee?.referrerUserId).toBe(referrer);
    expect(refereeSummary.asReferee?.status).toBe("PENDING");
  });

  it("happy path snapshots the active config's values onto the referee's reward", async () => {
    const referrer = await createTestUser();
    const referee = await createTestUser();
    const code = await generateCode(referrer);

    const result = (await redeemReferralCode.run(fakeRequest({ code }, referee))) as {
      referralId: string;
      alreadyAttributed: boolean;
      refereeDiscountPercent: number;
      refereeDiscountCapAmount: number;
    };

    expect(result.alreadyAttributed).toBe(false);
    expect(result.refereeDiscountPercent).toBe(10);
    expect(result.refereeDiscountCapAmount).toBe(50);

    const referralSnap = await db.collection("referrals").doc(referee).get();
    expect(referralSnap.exists).toBe(true);
    expect(referralSnap.data()!.status).toBe("PENDING");
    expect(referralSnap.data()!.referrerUserId).toBe(referrer);

    const refereeRewardSnap = await db.collection("referral_rewards").doc(referralSnap.data()!.refereeRewardId).get();
    expect(refereeRewardSnap.data()!.status).toBe("RESERVED");
    expect(refereeRewardSnap.data()!.discountPercent).toBe(10);

    const referrerRewardSnap = await db.collection("referral_rewards").doc(referralSnap.data()!.referrerRewardId).get();
    expect(referrerRewardSnap.data()!.status).toBe("PENDING");
    expect(referrerRewardSnap.data()!.discountPercent).toBe(20);
  });

  it("rejects a self-referral and logs a REDEMPTION_REJECTED event for it", async () => {
    const uid = await createTestUser();
    const code = await generateCode(uid);
    await expect(redeemReferralCode.run(fakeRequest({ code }, uid))).rejects.toThrow();
    const referralSnap = await db.collection("referrals").doc(uid).get();
    expect(referralSnap.exists).toBe(false);

    const eventsSnap = await db
      .collection("referral_events")
      .where("referredUserId", "==", uid)
      .where("eventType", "==", "REDEMPTION_REJECTED")
      .get();
    expect(eventsSnap.size).toBe(1);
    expect(eventsSnap.docs[0].data().metadata.reason).toBe("SELF_REFERRAL");
  });

  it("rejects an unknown code", async () => {
    const referee = await createTestUser();
    await expect(redeemReferralCode.run(fakeRequest({ code: "NOTREAL" }, referee))).rejects.toThrow();
  });

  it("rate-limits repeated failed attempts (regression test: the counter write must survive the same-transaction throw)", async () => {
    // Regression coverage for a real bug found in review: recordFailedRedemption's write was
    // originally followed by a `throw` inside the SAME db.runTransaction callback, which rolls
    // back the whole transaction (Firestore only commits if the callback resolves without
    // throwing) — so the counter increment silently never persisted, and this rate limit never
    // actually engaged for invalid-code/self-referral rejections. Fixed by returning a
    // discriminated result from the transaction and throwing only after it commits.
    const referee = await createTestUser();
    for (let i = 0; i < 5; i++) {
      await expect(redeemReferralCode.run(fakeRequest({ code: "NOTREAL" }, referee))).rejects.toThrow();
    }
    // The 6th attempt should be rejected for being rate-limited specifically, not "code not
    // found" — proving the counter actually reached the cap.
    await expect(redeemReferralCode.run(fakeRequest({ code: "NOTREAL" }, referee))).rejects.toMatchObject({
      code: "resource-exhausted",
    });
  });

  it("a second redemption attempt for an already-attributed user is a clean no-op, not a second relationship", async () => {
    const referrerA = await createTestUser();
    const referrerB = await createTestUser();
    const referee = await createTestUser();
    const codeA = await generateCode(referrerA);
    const codeB = await generateCode(referrerB);

    const first = (await redeemReferralCode.run(fakeRequest({ code: codeA }, referee))) as { alreadyAttributed: boolean };
    expect(first.alreadyAttributed).toBe(false);

    const second = (await redeemReferralCode.run(fakeRequest({ code: codeB }, referee))) as { alreadyAttributed: boolean };
    expect(second.alreadyAttributed).toBe(true);

    const referralSnap = await db.collection("referrals").doc(referee).get();
    expect(referralSnap.data()!.referrerUserId).toBe(referrerA); // first valid attribution wins, not overwritten by B
  });

  it("rejects redemption once the account is older than the configured grace window", async () => {
    await seedActiveConfig({ redemptionGraceHours: 0 }); // any account is "too old" the instant it's created
    const referrer = await createTestUser();
    const referee = await createTestUser();
    const code = await generateCode(referrer);

    await expect(redeemReferralCode.run(fakeRequest({ code }, referee))).rejects.toThrow();
    const referralSnap = await db.collection("referrals").doc(referee).get();
    expect(referralSnap.exists).toBe(false);
  });

  it("CONCURRENCY: two simultaneous redemptions for the same new uid create exactly one referral", async () => {
    const referrerA = await createTestUser();
    const referrerB = await createTestUser();
    const referee = await createTestUser();
    const codeA = await generateCode(referrerA);
    const codeB = await generateCode(referrerB);

    const results = await Promise.allSettled([
      redeemReferralCode.run(fakeRequest({ code: codeA }, referee)),
      redeemReferralCode.run(fakeRequest({ code: codeB }, referee)),
    ]);

    // Neither call should throw — the loser gets a clean alreadyAttributed:true, not an error,
    // since both codes are independently valid and the race is over who attributes first, not
    // over code validity.
    for (const r of results) expect(r.status).toBe("fulfilled");

    const referralsSnap = await db.collection("referrals").where("referredUserId", "==", referee).get();
    expect(referralsSnap.size).toBe(1); // exactly one relationship, never two
    expect(["ATTRIBUTED_A", "ATTRIBUTED_B"]).toContain(
      referralsSnap.docs[0].data().referrerUserId === referrerA ? "ATTRIBUTED_A" : "ATTRIBUTED_B"
    );
  });
});

describe("createBookingHold with a referral reward", () => {
  beforeEach(async () => {
    await seedActiveConfig();
  });

  async function setUpQualifiedReferee(): Promise<{ referrerUid: string; refereeUid: string; refereeRewardId: string }> {
    const referrerUid = await createTestUser();
    const refereeUid = await createTestUser();
    const code = await generateCode(referrerUid);
    await redeemReferralCode.run(fakeRequest({ code }, refereeUid));
    const referralSnap = await db.collection("referrals").doc(refereeUid).get();
    return { referrerUid, refereeUid, refereeRewardId: referralSnap.data()!.refereeRewardId };
  }

  it("applies the referee's 10% discount, capped, at checkout", async () => {
    const { refereeUid, refereeRewardId } = await setUpQualifiedReferee();
    const slotId = await seedAvailableSlot(5, 500); // 10% of 500 = 50, exactly at the cap

    const result = (await createBookingHold.run(
      fakeRequest(
        {
          slotId,
          participants: [{ displayName: "Referee" }],
          idempotencyKey: `idem-${slotId}`,
          applyReferralRewardId: refereeRewardId,
        },
        refereeUid
      )
    )) as { amount: number; bookingId: string };

    expect(result.amount).toBe(450); // 500 - 50

    const rewardSnap = await db.collection("referral_rewards").doc(refereeRewardId).get();
    expect(rewardSnap.data()!.status).toBe("APPLIED");
    expect(rewardSnap.data()!.bookingId).toBe(result.bookingId);
    expect(rewardSnap.data()!.appliedDiscountAmount).toBe(50);
  });

  it("caps the discount rather than letting a large booking exceed it", async () => {
    const { refereeUid, refereeRewardId } = await setUpQualifiedReferee();
    const slotId = await seedAvailableSlot(5, 2000); // 10% of 2000 = 200, cap is 50

    const result = (await createBookingHold.run(
      fakeRequest(
        { slotId, participants: [{ displayName: "Referee" }], idempotencyKey: `idem-${slotId}`, applyReferralRewardId: refereeRewardId },
        refereeUid
      )
    )) as { amount: number };

    expect(result.amount).toBe(1950); // 2000 - 50 (capped), not 2000 - 200
  });

  it("rejects a reward that doesn't belong to the caller", async () => {
    const { refereeRewardId } = await setUpQualifiedReferee();
    const someoneElse = await createTestUser();
    const slotId = await seedAvailableSlot();

    await expect(
      createBookingHold.run(
        fakeRequest(
          { slotId, participants: [{ displayName: "X" }], idempotencyKey: `idem-${slotId}`, applyReferralRewardId: refereeRewardId },
          someoneElse
        )
      )
    ).rejects.toThrow();
  });

  it("rejects reusing a reward that's already APPLIED", async () => {
    const { refereeUid, refereeRewardId } = await setUpQualifiedReferee();
    const slotId1 = await seedAvailableSlot();
    await createBookingHold.run(
      fakeRequest(
        { slotId: slotId1, participants: [{ displayName: "Referee" }], idempotencyKey: `idem-${slotId1}`, applyReferralRewardId: refereeRewardId },
        refereeUid
      )
    );

    const slotId2 = await seedAvailableSlot();
    await expect(
      createBookingHold.run(
        fakeRequest(
          { slotId: slotId2, participants: [{ displayName: "Referee" }], idempotencyKey: `idem-${slotId2}`, applyReferralRewardId: refereeRewardId },
          refereeUid
        )
      )
    ).rejects.toThrow();
  });

  it("CONCURRENCY: two simultaneous bookings applying the same reward — exactly one succeeds", async () => {
    const { refereeUid, refereeRewardId } = await setUpQualifiedReferee();
    const slotIdA = await seedAvailableSlot(5, 500);
    const slotIdB = await seedAvailableSlot(5, 500);

    const results = await Promise.allSettled([
      createBookingHold.run(
        fakeRequest(
          { slotId: slotIdA, participants: [{ displayName: "A" }], idempotencyKey: `idem-${slotIdA}`, applyReferralRewardId: refereeRewardId },
          refereeUid
        )
      ),
      createBookingHold.run(
        fakeRequest(
          { slotId: slotIdB, participants: [{ displayName: "B" }], idempotencyKey: `idem-${slotIdB}`, applyReferralRewardId: refereeRewardId },
          refereeUid
        )
      ),
    ]);

    const succeeded = results.filter((r) => r.status === "fulfilled");
    const failed = results.filter((r) => r.status === "rejected");
    expect(succeeded.length).toBe(1);
    expect(failed.length).toBe(1);

    const rewardSnap = await db.collection("referral_rewards").doc(refereeRewardId).get();
    expect(rewardSnap.data()!.status).toBe("APPLIED"); // never double-spent
  });
});

describe("markAttendance referral qualification", () => {
  beforeEach(async () => {
    await seedActiveConfig();
  });

  const PARTNER_ID = "test-partner";

  async function bookAndConfirm(customerUid: string): Promise<string> {
    const slotId = await seedAvailableSlot(5, 500);
    const bookingRef = db.collection("bookings").doc();
    await bookingRef.set({
      slotId,
      serviceId: "nonexistent-service",
      partnerId: PARTNER_ID,
      venueId: "test-venue",
      customerUserId: customerUid,
      participantCount: 1,
      slotUnitsHeld: 1,
      status: "CONFIRMED", // skip straight past payment — attendance.ts only cares about BOOKING_TRANSITIONS from here
      amount: 500,
      currency: "INR",
      idempotencyKey: `idem-${slotId}`,
      holdExpiresAt: null,
      createdAt: Timestamp.now(),
      updatedAt: Timestamp.now(),
    });
    return bookingRef.id;
  }

  it("qualifies the referral and issues the referrer's reward when the referee's booking completes", async () => {
    const referrerUid = await createTestUser();
    const refereeUid = await createTestUser();
    const code = await generateCode(referrerUid);
    await redeemReferralCode.run(fakeRequest({ code }, refereeUid));
    const referral = (await db.collection("referrals").doc(refereeUid).get()).data()!;

    const bookingId = await bookAndConfirm(refereeUid);
    await markAttendance.run(
      fakeRequest({ bookingId, status: "CHECKED_IN" }, "partner-staff-1", { partnerId: PARTNER_ID })
    );
    await markAttendance.run(
      fakeRequest({ bookingId, status: "COMPLETED" }, "partner-staff-1", { partnerId: PARTNER_ID })
    );

    const referralSnap = await db.collection("referrals").doc(refereeUid).get();
    expect(referralSnap.data()!.status).toBe("QUALIFIED");
    expect(referralSnap.data()!.qualifyingBookingId).toBe(bookingId);

    const referrerRewardSnap = await db.collection("referral_rewards").doc(referral.referrerRewardId).get();
    expect(referrerRewardSnap.data()!.status).toBe("ISSUED");
  });

  it("does not re-issue the referrer's reward for a second completed booking (status-as-mutex regression test)", async () => {
    const referrerUid = await createTestUser();
    const refereeUid = await createTestUser();
    const code = await generateCode(referrerUid);
    await redeemReferralCode.run(fakeRequest({ code }, refereeUid));
    const referral = (await db.collection("referrals").doc(refereeUid).get()).data()!;

    const firstBookingId = await bookAndConfirm(refereeUid);
    await markAttendance.run(fakeRequest({ bookingId: firstBookingId, status: "CHECKED_IN" }, "p1", { partnerId: PARTNER_ID }));
    await markAttendance.run(fakeRequest({ bookingId: firstBookingId, status: "COMPLETED" }, "p1", { partnerId: PARTNER_ID }));

    const secondBookingId = await bookAndConfirm(refereeUid);
    await markAttendance.run(fakeRequest({ bookingId: secondBookingId, status: "CHECKED_IN" }, "p1", { partnerId: PARTNER_ID }));
    await markAttendance.run(fakeRequest({ bookingId: secondBookingId, status: "COMPLETED" }, "p1", { partnerId: PARTNER_ID }));

    const referralSnap = await db.collection("referrals").doc(refereeUid).get();
    expect(referralSnap.data()!.qualifyingBookingId).toBe(firstBookingId); // untouched by the second completion

    const referrerRewardSnap = await db.collection("referral_rewards").doc(referral.referrerRewardId).get();
    expect(referrerRewardSnap.data()!.status).toBe("ISSUED"); // still ISSUED, not re-issued/duplicated
  });

  it("reverts an APPLIED referee reward back to RESERVED on NO_SHOW", async () => {
    const referrerUid = await createTestUser();
    const refereeUid = await createTestUser();
    const code = await generateCode(referrerUid);
    await redeemReferralCode.run(fakeRequest({ code }, refereeUid));
    const referral = (await db.collection("referrals").doc(refereeUid).get()).data()!;

    const slotId = await seedAvailableSlot(5, 500);
    const bookingResult = (await createBookingHold.run(
      fakeRequest(
        {
          slotId,
          participants: [{ displayName: "Referee" }],
          idempotencyKey: `idem-${slotId}`,
          applyReferralRewardId: referral.refereeRewardId,
        },
        refereeUid
      )
    )) as { bookingId: string };
    await db.collection("bookings").doc(bookingResult.bookingId).update({ status: "CONFIRMED", partnerId: PARTNER_ID });

    await markAttendance.run(
      fakeRequest({ bookingId: bookingResult.bookingId, status: "NO_SHOW" }, "p1", { partnerId: PARTNER_ID })
    );

    const rewardSnap = await db.collection("referral_rewards").doc(referral.refereeRewardId).get();
    expect(rewardSnap.data()!.status).toBe("RESERVED"); // reverted, not stuck APPLIED — usable again
    expect(rewardSnap.data()!.bookingId).toBeNull();

    const referralSnap = await db.collection("referrals").doc(refereeUid).get();
    expect(referralSnap.data()!.status).toBe("PENDING"); // NO_SHOW never qualifies anything
  });

  it("redeems the referee's own applied reward (APPLIED -> REDEEMED) when their booking completes", async () => {
    const referrerUid = await createTestUser();
    const refereeUid = await createTestUser();
    const code = await generateCode(referrerUid);
    await redeemReferralCode.run(fakeRequest({ code }, refereeUid));
    const referral = (await db.collection("referrals").doc(refereeUid).get()).data()!;

    const slotId = await seedAvailableSlot(5, 500);
    const bookingResult = (await createBookingHold.run(
      fakeRequest(
        {
          slotId,
          participants: [{ displayName: "Referee" }],
          idempotencyKey: `idem-${slotId}`,
          applyReferralRewardId: referral.refereeRewardId,
        },
        refereeUid
      )
    )) as { bookingId: string };
    await db.collection("bookings").doc(bookingResult.bookingId).update({ status: "CONFIRMED", partnerId: PARTNER_ID });

    await markAttendance.run(
      fakeRequest({ bookingId: bookingResult.bookingId, status: "CHECKED_IN" }, "p1", { partnerId: PARTNER_ID })
    );
    await markAttendance.run(
      fakeRequest({ bookingId: bookingResult.bookingId, status: "COMPLETED" }, "p1", { partnerId: PARTNER_ID })
    );

    const rewardSnap = await db.collection("referral_rewards").doc(referral.refereeRewardId).get();
    expect(rewardSnap.data()!.status).toBe("REDEEMED"); // final state — not stuck at APPLIED forever
    expect(rewardSnap.data()!.redeemedAt).not.toBeNull();
    expect(rewardSnap.data()!.bookingId).toBe(bookingResult.bookingId); // kept, unlike a revert

    // This same COMPLETED also independently qualifies the referral / issues the referrer's
    // separate reward — the two effects fire in the same transaction, from the same trigger.
    const referralSnap = await db.collection("referrals").doc(refereeUid).get();
    expect(referralSnap.data()!.status).toBe("QUALIFIED");
    const referrerRewardSnap = await db.collection("referral_rewards").doc(referral.referrerRewardId).get();
    expect(referrerRewardSnap.data()!.status).toBe("ISSUED");
  });
});

describe("firestore.rules — referral collections", () => {
  let testEnv: RulesTestEnvironment;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "maxtime-referral-rules-test",
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, "../../firestore.rules"), "utf8"),
      },
    });
  });

  afterAll(async () => {
    await testEnv.cleanup();
  });

  afterEach(async () => {
    await testEnv.clearFirestore();
  });

  it("lets the referrer and referee read their own referral, not a third party", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection("referrals").doc("referee-1").set({
        referrerUserId: "referrer-1",
        referredUserId: "referee-1",
      });
    });

    const referrerDb = testEnv.authenticatedContext("referrer-1").firestore();
    const refereeDb = testEnv.authenticatedContext("referee-1").firestore();
    const strangerDb = testEnv.authenticatedContext("stranger-1").firestore();

    await assertSucceeds(referrerDb.collection("referrals").doc("referee-1").get());
    await assertSucceeds(refereeDb.collection("referrals").doc("referee-1").get());
    await assertFails(strangerDb.collection("referrals").doc("referee-1").get());
  });

  it("never allows a direct client write to referrals/referral_rewards/referral_codes", async () => {
    const uidDb = testEnv.authenticatedContext("someone").firestore();
    await assertFails(uidDb.collection("referrals").doc("someone").set({ referrerUserId: "x", referredUserId: "someone" }));
    await assertFails(uidDb.collection("referral_rewards").doc("r1").set({ beneficiaryUserId: "someone" }));
    await assertFails(uidDb.collection("referral_codes").doc("ABC123").set({ ownerUserId: "someone", status: "ACTIVE" }));
  });

  it("lets any signed-in user read referral_configs, but only an admin can write it", async () => {
    const regularDb = testEnv.authenticatedContext("regular-user").firestore();
    await assertSucceeds(regularDb.collection("referral_configs").doc("c1").get());
    await assertFails(regularDb.collection("referral_configs").doc("c1").set({ active: true }));

    const adminDb = testEnv.authenticatedContext("admin-user", { admin: true }).firestore();
    await assertSucceeds(adminDb.collection("referral_configs").doc("c1").set({ active: true }));
  });
});
