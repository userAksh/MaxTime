import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  DocumentReference,
  DocumentSnapshot,
  FieldValue,
  Timestamp,
  Transaction,
} from "firebase-admin/firestore";
import { db, auth } from "./admin";
import { writeAuditLog } from "./audit";
import {
  AttributionSource,
  REFERRAL_TRANSITIONS,
  REWARD_TRANSITIONS,
  Referral,
  ReferralCode,
  ReferralConfig,
  ReferralEvent,
  ReferralEventType,
  ReferralRedemptionAttempts,
  ReferralReward,
  RewardStatus,
} from "./types";

// MaxTime_Claude_Product_Design_Context.docx §6-11 — V1 referral system. Kept entirely separate
// from Activity Invite and MaxCoins (different collections, no shared code paths). See
// functions/src/types.ts for the full schema/state-machine doc comments.

const MAX_FAILED_REDEMPTIONS_PER_WINDOW = 5;
const FAILED_REDEMPTION_WINDOW_MS = 60 * 60 * 1000;

interface Actor {
  userId: string;
  role: string;
}
const SYSTEM_ACTOR: Actor = { userId: "system", role: "SYSTEM" };

function isAdmin(request: { auth?: { token?: Record<string, unknown> } }): boolean {
  return request.auth?.token?.admin === true || request.auth?.token?.adminRole != null;
}

// "MXT" (not a random letter) is the padding filler for a name with fewer than 3 letters (empty
// name, emoji-only, etc.) — deliberately a real, neutral, on-brand string rather than a filler
// that could accidentally spell something unintended.
const NAME_PART_PADDING = "MXT";

/** Deterministic base code: first 3 letters of the user's first name + last 2 digits of their
 *  phone number (explicit product decision — not random, so it's personal/memorable, not an
 *  opaque string). Collisions are expected and handled by the caller (see generateMyReferralCode)
 *  by appending a number only when this exact base is already taken — most users get the clean
 *  5-character code, a minority get one extra digit. */
function deriveReferralCodeBase(name: string, phoneNumber: string | null | undefined): string {
  const firstName = name.trim().split(/\s+/)[0] ?? "";
  const lettersOnly = firstName.replace(/[^a-zA-Z]/g, "").toUpperCase();
  const namePart = (lettersOnly + NAME_PART_PADDING).slice(0, 3);
  const digitsOnly = (phoneNumber ?? "").replace(/[^0-9]/g, "");
  const phonePart = digitsOnly.length >= 2 ? digitsOnly.slice(-2) : digitsOnly.padStart(2, "0");
  return namePart + phonePart;
}

// V1 built-in defaults — used whenever no admin has configured a `referral_configs` document
// (or none is currently active). The referral feature must work for every user out of the box;
// an admin document is an optional override for the percentages/windows, never a precondition for
// the feature existing at all. Matches the client's own FALLBACK_* constants in
// ReferAndEarnScreen.kt, which were always meant to be shown if this call "fails" — the real fix
// is this call no longer fails, not that the client silently guesses instead.
const DEFAULT_REFERRAL_CONFIG: Omit<ReferralConfig, "id" | "effectiveFrom" | "effectiveTo" | "createdAt" | "updatedAt"> = {
  referrerDiscountPercent: 20,
  referrerDiscountCapAmount: 100,
  refereeDiscountPercent: 10,
  refereeDiscountCapAmount: 50,
  currency: "INR",
  attributionWindowDays: 30,
  rewardValidityDays: 90,
  redemptionGraceHours: 168,
  codeLength: 6,
  active: true,
};

/** Mirrors payouts.ts's commissionFor() effective-date lookup. Read fresh on every call (not
 *  cached) so flipping every config's `active` to false is a real, immediate kill switch. Never
 *  throws — falls back to [DEFAULT_REFERRAL_CONFIG] rather than blocking the whole referral
 *  feature behind a document an admin may never have created (see this constant's doc comment). */
async function getActiveReferralConfig(): Promise<ReferralConfig> {
  const now = Timestamp.now();
  const snap = await db.collection("referral_configs").where("active", "==", true).get();
  const applicable = snap.docs
    .map((d) => ({ id: d.id, ...d.data() } as ReferralConfig))
    .find(
      (c) =>
        c.effectiveFrom.toMillis() <= now.toMillis() &&
        (c.effectiveTo === null || c.effectiveTo.toMillis() >= now.toMillis())
    );
  if (applicable) return applicable;
  return { id: "default", effectiveFrom: now, effectiveTo: null, createdAt: now, updatedAt: now, ...DEFAULT_REFERRAL_CONFIG };
}

export function writeReferralEvent(
  tx: Transaction,
  entry: {
    referralId: string;
    eventType: ReferralEventType;
    actor: Actor;
    referrerUserId: string;
    referredUserId: string;
    metadata?: Record<string, unknown>;
  }
): void {
  const ref = db.collection("referral_events").doc();
  const doc: Omit<ReferralEvent, "id" | "createdAt"> = {
    referralId: entry.referralId,
    eventType: entry.eventType,
    actorUserId: entry.actor.userId,
    referrerUserId: entry.referrerUserId,
    referredUserId: entry.referredUserId,
    metadata: entry.metadata ?? {},
  };
  tx.set(ref, { ...doc, createdAt: FieldValue.serverTimestamp() });
}

/** Rolling-window counter, self-contained (no App Check/rate-limit precedent exists elsewhere in
 *  this codebase to extend) — cheap brute-force deterrent for a 6-character code space — AND the
 *  REDEMPTION_REJECTED referral_events entry for the same rejected attempt, always written
 *  together so the two can't drift (one recording the rejection for rate-limiting purposes, the
 *  other for the participant-facing/audit timeline). Accepts an in-flight transaction so it can
 *  be called from redeemReferralCode's own transaction without nesting a second one; runs its own
 *  tiny transaction when called standalone (e.g. from the account-age check, which happens before
 *  redeemReferralCode's main transaction even starts). */
async function recordFailedRedemption(uid: string, code: string, reason: string, tx?: Transaction): Promise<void> {
  const ref = db.collection("referral_redemption_attempts").doc(uid);
  const apply = async (t: Transaction) => {
    const snap = await t.get(ref);
    const now = Date.now();
    const data = snap.exists ? (snap.data() as ReferralRedemptionAttempts) : null;
    const windowStillOpen = !!data && now - data.windowStartedAt.toMillis() < FAILED_REDEMPTION_WINDOW_MS;
    t.set(ref, {
      failedCount: windowStillOpen ? data!.failedCount + 1 : 1,
      windowStartedAt: windowStillOpen ? data!.windowStartedAt : FieldValue.serverTimestamp(),
    });
    // No Referral doc exists for a rejected attempt (that's the whole point), so referralId is
    // just the uid itself for grouping purposes — not a real referrals/{id} doc reference. Only
    // this user themselves can read it back (referrerUserId left empty — see the rules: reading
    // requires matching either side, and there IS no referrer side for a rejected attempt).
    writeReferralEvent(t, {
      referralId: uid,
      eventType: "REDEMPTION_REJECTED",
      actor: { userId: uid, role: "USER" },
      referrerUserId: "",
      referredUserId: uid,
      metadata: { code, reason },
    });
  };
  if (tx) {
    await apply(tx);
  } else {
    await db.runTransaction(apply);
  }
}

async function isRedemptionRateLimited(uid: string): Promise<boolean> {
  const snap = await db.collection("referral_redemption_attempts").doc(uid).get();
  if (!snap.exists) return false;
  const data = snap.data() as ReferralRedemptionAttempts;
  const windowStillOpen = Date.now() - data.windowStartedAt.toMillis() < FAILED_REDEMPTION_WINDOW_MS;
  return windowStillOpen && data.failedCount >= MAX_FAILED_REDEMPTIONS_PER_WINDOW;
}

/**
 * The ONLY code path in this feature permitted to create a referrals/{referredUserId} doc. Any
 * future attribution source (Play Install Referrer, Branch, AppsFlyer) must funnel through this
 * exact helper — see the plan's "provider-agnostic attribution" note — never tx.set a Referral
 * doc directly anywhere else. Caller must have already read codeSnap/referralSnap (all reads
 * before any writes, the Firestore transaction rule) before calling this.
 *
 * Returns { created: false } if a referral already existed for this uid (first-valid-attribution-
 * wins — callers should treat this as a clean no-op, not an error).
 */
function createAttribution(
  tx: Transaction,
  params: {
    referredUserId: string;
    code: string;
    codeOwnerUserId: string;
    source: AttributionSource;
    metadata?: Record<string, unknown>;
  },
  referralSnap: DocumentSnapshot,
  config: ReferralConfig
): { created: boolean; referralId: string } {
  if (referralSnap.exists) {
    return { created: false, referralId: referralSnap.id };
  }

  const referralRef = referralSnap.ref;
  const refereeRewardRef = db.collection("referral_rewards").doc();
  const referrerRewardRef = db.collection("referral_rewards").doc();

  const referral: Omit<Referral, "id"> = {
    referrerUserId: params.codeOwnerUserId,
    referredUserId: params.referredUserId,
    code: params.code,
    status: "PENDING",
    attributionSource: params.source,
    // Optional field — Firestore rejects an explicit `undefined` value outright (throws, doesn't
    // just ignore it), so it must be omitted entirely rather than set to undefined when no
    // metadata was passed.
    ...(params.metadata ? { attributionMetadata: params.metadata } : {}),
    refereeRewardId: refereeRewardRef.id,
    referrerRewardId: referrerRewardRef.id,
    qualifyingBookingId: null,
    createdAt: FieldValue.serverTimestamp() as never,
    qualifiedAt: null,
    updatedAt: FieldValue.serverTimestamp() as never,
  };
  tx.set(referralRef, referral);

  // RESERVED, not PENDING — confirmed decision: the referee's discount is usable immediately at
  // their first booking's checkout (see redeemReferralCode's doc comment), not gated on
  // qualification the way the referrer's reward is.
  const refereeReward: Omit<ReferralReward, "id"> = {
    referralId: params.referredUserId,
    beneficiaryUserId: params.referredUserId,
    role: "REFEREE",
    status: "RESERVED",
    discountPercent: config.refereeDiscountPercent,
    discountCapAmount: config.refereeDiscountCapAmount,
    currency: config.currency,
    bookingId: null,
    appliedDiscountAmount: null,
    createdAt: FieldValue.serverTimestamp() as never,
    appliedAt: null,
    redeemedAt: null,
    issuedAt: null,
    updatedAt: FieldValue.serverTimestamp() as never,
  };
  tx.set(refereeRewardRef, refereeReward);

  // PENDING — not usable until qualifyReferralIfApplicable flips it to ISSUED on completion.
  const referrerReward: Omit<ReferralReward, "id"> = {
    referralId: params.referredUserId,
    beneficiaryUserId: params.codeOwnerUserId,
    role: "REFERRER",
    status: "PENDING",
    discountPercent: config.referrerDiscountPercent,
    discountCapAmount: config.referrerDiscountCapAmount,
    currency: config.currency,
    bookingId: null,
    appliedDiscountAmount: null,
    createdAt: FieldValue.serverTimestamp() as never,
    appliedAt: null,
    redeemedAt: null,
    issuedAt: null,
    updatedAt: FieldValue.serverTimestamp() as never,
  };
  tx.set(referrerRewardRef, referrerReward);

  writeReferralEvent(tx, {
    referralId: params.referredUserId,
    eventType: "ATTRIBUTED",
    actor: { userId: params.referredUserId, role: "USER" },
    referrerUserId: params.codeOwnerUserId,
    referredUserId: params.referredUserId,
    metadata: { code: params.code, source: params.source },
  });

  return { created: true, referralId: params.referredUserId };
}

/**
 * Called from markAttendance's transaction once a booking reaches COMPLETED, after the caller has
 * already read referralSnap/referrerRewardSnap itself (this function does no reads, only queues
 * writes, since Firestore requires all reads before any writes and attendance.ts's own booking
 * update happens first). No-ops if the referral isn't PENDING — which, since markAttendance is
 * the only code path that can ever transition a booking to COMPLETED, is proof no earlier booking
 * has already qualified this referral ("status as mutex" — see the architecture plan for why this
 * needs no query over the referee's booking history).
 */
export function qualifyReferralIfApplicable(
  tx: Transaction,
  referralSnap: DocumentSnapshot,
  referrerRewardSnap: DocumentSnapshot | null,
  qualifyingBookingId: string,
  actor: Actor
): void {
  if (!referralSnap.exists) return;
  const referral = referralSnap.data() as Referral;
  if (referral.status !== "PENDING") return;
  if (!referrerRewardSnap || !referrerRewardSnap.exists) return; // defensive — should never happen
  if (!REFERRAL_TRANSITIONS.PENDING.includes("QUALIFIED")) return; // defensive, always true

  tx.update(referralSnap.ref, {
    status: "QUALIFIED",
    qualifyingBookingId,
    qualifiedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  tx.update(referrerRewardSnap.ref, {
    status: "ISSUED",
    issuedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  writeAuditLog(
    {
      actorUserId: actor.userId,
      actorRole: actor.role,
      entityType: "Referral",
      entityId: referralSnap.id, // NOT referral.id — `.data()` never includes the doc's own id
      action: "REFERRAL_QUALIFIED",
      before: { status: "PENDING" },
      after: { status: "QUALIFIED", qualifyingBookingId },
    },
    tx
  );
  writeReferralEvent(tx, {
    referralId: referral.referredUserId, // == referralSnap.id by construction (see types.ts)
    eventType: "REFERRAL_QUALIFIED",
    actor,
    referrerUserId: referral.referrerUserId,
    referredUserId: referral.referredUserId,
    metadata: { qualifyingBookingId },
  });
  writeReferralEvent(tx, {
    referralId: referral.referredUserId,
    eventType: "REWARD_ISSUED",
    actor,
    referrerUserId: referral.referrerUserId,
    referredUserId: referral.referredUserId,
    metadata: { rewardId: referral.referrerRewardId },
  });
}

/**
 * Reverts an APPLIED reward back to whichever state it came from (RESERVED for a REFEREE reward,
 * ISSUED for a REFERRER reward) because the booking it was applied to failed to complete
 * (PAYMENT_FAILED / EXPIRED / NO_SHOW). Caller must have already read rewardSnap and the
 * corresponding Referral doc before any writes. No-ops if the reward isn't currently APPLIED.
 */
export function revertAppliedReward(
  tx: Transaction,
  rewardSnap: DocumentSnapshot,
  referral: { referrerUserId: string; referredUserId: string },
  bookingRef: DocumentReference,
  reason: "PAYMENT_FAILED" | "EXPIRED" | "NO_SHOW",
  actor: Actor
): void {
  if (!rewardSnap.exists) return;
  const reward = rewardSnap.data() as ReferralReward;
  if (reward.status !== "APPLIED") return;
  const revertTarget: RewardStatus = reward.role === "REFEREE" ? "RESERVED" : "ISSUED";
  if (!REWARD_TRANSITIONS.APPLIED.includes(revertTarget)) return; // defensive, always true

  tx.update(rewardSnap.ref, {
    status: revertTarget,
    bookingId: null,
    appliedDiscountAmount: null,
    updatedAt: FieldValue.serverTimestamp(),
  });
  tx.update(bookingRef, { appliedReferralRewardId: null });

  writeAuditLog(
    {
      actorUserId: actor.userId,
      actorRole: actor.role,
      entityType: "ReferralReward",
      entityId: rewardSnap.id, // NOT reward.id — `.data()` never includes the doc's own id
      action: "REWARD_REVERTED",
      reason,
      before: { status: "APPLIED" },
      after: { status: revertTarget },
    },
    tx
  );
  writeReferralEvent(tx, {
    referralId: reward.referralId,
    eventType: "REWARD_REVERTED",
    actor,
    referrerUserId: referral.referrerUserId,
    referredUserId: referral.referredUserId,
    metadata: { reason, rewardId: rewardSnap.id },
  });
}

/**
 * Marks an APPLIED reward REDEEMED — its final, fully-used state — because the specific booking
 * it was applied to (referenced by its own `bookingId` field) reached COMPLETED. Applies to
 * either role: a REFEREE's reward applied to their own first booking, or a REFERRER's reward
 * applied to one of their own later bookings — both go through the exact same checkout path
 * (createBookingHold's `applyReferralRewardId`) and both should reach the same terminal state
 * once that booking genuinely completes. This is independent of, and can fire in the same
 * transaction alongside, qualifyReferralIfApplicable — that function's job is unlocking the
 * REFERRER's separate reward when the REFEREE's booking completes; this one's job is finalizing
 * whichever single reward doc was actually spent on THIS booking, regardless of whose it is.
 * No-ops if the reward isn't currently APPLIED (e.g. already reverted or redeemed).
 */
export function redeemAppliedReward(tx: Transaction, rewardSnap: DocumentSnapshot, actor: Actor): void {
  if (!rewardSnap.exists) return;
  const reward = rewardSnap.data() as ReferralReward;
  if (reward.status !== "APPLIED") return;
  if (!REWARD_TRANSITIONS.APPLIED.includes("REDEEMED")) return; // defensive, always true

  tx.update(rewardSnap.ref, {
    status: "REDEEMED",
    redeemedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  writeAuditLog(
    {
      actorUserId: actor.userId,
      actorRole: actor.role,
      entityType: "ReferralReward",
      entityId: rewardSnap.id,
      action: "REWARD_REDEEMED",
      before: { status: "APPLIED" },
      after: { status: "REDEEMED" },
    },
    tx
  );
  // Same partial-denormalization tradeoff as expireStaleRewards: only the side derivable
  // directly from the reward doc is populated, since reading the full Referral doc here too
  // would be a third read this call site doesn't otherwise need. The affected beneficiary can
  // still read this event (their own uid is always the populated side); the other party simply
  // won't see this specific entry, which is fine — it's this beneficiary's own reward.
  writeReferralEvent(tx, {
    referralId: reward.referralId,
    eventType: "REWARD_REDEEMED",
    actor,
    referrerUserId: reward.role === "REFERRER" ? reward.beneficiaryUserId : "",
    referredUserId: reward.role === "REFEREE" ? reward.beneficiaryUserId : reward.referralId,
    metadata: { rewardId: rewardSnap.id, bookingId: reward.bookingId },
  });
}

/** Idempotent — a user always gets exactly one stable, permanent, shareable code, never a second
 *  one minted on a later call. */
export const generateMyReferralCode = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const config = await getActiveReferralConfig();

  const existing = await db.collection("referral_codes").where("ownerUserId", "==", uid).limit(1).get();
  if (!existing.empty) {
    const code = existing.docs[0].id;
    return {
      code,
      refereeDiscountPercent: config.refereeDiscountPercent,
      refereeDiscountCapAmount: config.refereeDiscountCapAmount,
      referrerDiscountPercent: config.referrerDiscountPercent,
      referrerDiscountCapAmount: config.referrerDiscountCapAmount,
    };
  }

  // Deterministic, not random — see deriveReferralCodeBase's doc comment. users/{uid} is a
  // client-writable profile doc (see UserProfileRepository.kt), so `name` may still be blank this
  // early (e.g. called before onboarding's profile-save step) — deriveReferralCodeBase already
  // pads a short/empty name with NAME_PART_PADDING rather than failing, so this never blocks code
  // generation. Phone number comes from Firebase Auth directly (always set — it's how every user
  // signed in), not the profile doc, so it's never missing regardless of onboarding progress.
  const [profileSnap, authUser] = await Promise.all([db.collection("users").doc(uid).get(), auth.getUser(uid)]);
  const name = (profileSnap.data()?.name as string | undefined) ?? "";
  const phoneNumber = authUser.phoneNumber ?? (profileSnap.data()?.phoneNumber as string | undefined) ?? "";
  const base = deriveReferralCodeBase(name, phoneNumber);

  const MAX_SUFFIX_ATTEMPTS = 50;
  for (let suffix = 0; suffix <= MAX_SUFFIX_ATTEMPTS; suffix++) {
    const candidate = suffix === 0 ? base : `${base}${suffix}`;
    const created = await db.runTransaction(async (tx) => {
      const ref = db.collection("referral_codes").doc(candidate);
      const snap = await tx.get(ref);
      if (snap.exists) return false;
      const doc: Omit<ReferralCode, "id"> = { ownerUserId: uid, status: "ACTIVE", createdAt: FieldValue.serverTimestamp() as never };
      tx.set(ref, doc);
      writeReferralEvent(tx, {
        referralId: uid,
        eventType: "CODE_GENERATED",
        actor: { userId: uid, role: "USER" },
        referrerUserId: uid,
        referredUserId: "",
        metadata: { code: candidate },
      });
      return true;
    });
    if (created) {
      return {
        code: candidate,
        refereeDiscountPercent: config.refereeDiscountPercent,
        refereeDiscountCapAmount: config.refereeDiscountCapAmount,
        referrerDiscountPercent: config.referrerDiscountPercent,
        referrerDiscountCapAmount: config.referrerDiscountCapAmount,
      };
    }
  }
  throw new HttpsError("internal", "Could not generate a unique referral code, please try again.");
});

/**
 * New-user-only. The referee's 10% discount is created RESERVED here — immediately usable at
 * their next checkout — rather than only after their booking completes, so the "10% off your
 * first booking" promise in the share copy is literally true: they see the discount applied
 * before they've paid, not after. It's only *confirmed* (REDEEMED) once that booking actually
 * completes — see createBookingHold and markAttendance. The referrer's reward, by contrast, stays
 * PENDING (unusable) until that same completion, per the brief's explicit "after the referred
 * user qualifies" language, which has no equivalent ambiguity to resolve.
 */
export const redeemReferralCode = onCall(async (request) => {
  const referredUserId = request.auth?.uid;
  if (!referredUserId) throw new HttpsError("unauthenticated", "Sign in required.");

  const rawCode = request.data?.code as string | undefined;
  if (!rawCode || typeof rawCode !== "string" || !rawCode.trim()) {
    throw new HttpsError("invalid-argument", "code is required.");
  }
  const code = rawCode.trim().toUpperCase();

  if (await isRedemptionRateLimited(referredUserId)) {
    throw new HttpsError("resource-exhausted", "Too many attempts. Please try again later.");
  }

  const config = await getActiveReferralConfig();

  // New-user proxy: there's no server-authoritative "onboarding complete" flag anywhere in this
  // codebase today (users/{uid} is a client-writable profile doc with no such marker), so account
  // age via Firebase Auth is the cheapest available signal. Known weakness (delete+recreate could
  // reset the clock) accepted given the small reward caps at stake — see types.ts's doc comment
  // on ReferralConfig.redemptionGraceHours.
  const userRecord = await auth.getUser(referredUserId);
  const createdAtMs = new Date(userRecord.metadata.creationTime).getTime();
  const graceMs = config.redemptionGraceHours * 60 * 60 * 1000;
  if (Date.now() - createdAtMs > graceMs) {
    await recordFailedRedemption(referredUserId, code, "ACCOUNT_TOO_OLD");
    throw new HttpsError("failed-precondition", "Referral codes can only be used when setting up a new account.");
  }

  // Returns a discriminated result instead of throwing for the two "soft" rejections below —
  // throwing INSIDE a db.runTransaction callback rolls back the entire transaction, including
  // any writes already queued earlier in that same callback (Firestore transactions only commit
  // if the callback resolves without throwing). recordFailedRedemption's rate-limit-counter and
  // REDEMPTION_REJECTED-event writes would silently never persist if a throw immediately followed
  // them in the same transaction — exactly the bug this shape avoids. The transaction always
  // commits normally; the outer function below decides whether to throw only after that commit
  // has actually happened.
  type RedeemResult =
    | {
        ok: true;
        referralId: string;
        alreadyAttributed: boolean;
        refereeDiscountPercent?: number;
        refereeDiscountCapAmount?: number;
        referrerDiscountPercent?: number;
        referrerDiscountCapAmount?: number;
      }
    | { ok: false; code: "not-found" | "invalid-argument"; message: string };

  const result = await db.runTransaction<RedeemResult>(async (tx) => {
    const codeRef = db.collection("referral_codes").doc(code);
    const referralRef = db.collection("referrals").doc(referredUserId);
    const [codeSnap, referralSnap] = await Promise.all([tx.get(codeRef), tx.get(referralRef)]);

    if (referralSnap.exists) {
      // First-valid-attribution-wins: whether this is a genuine repeat call or the loser of a
      // concurrent race, there is already a referral for this uid — clean no-op, never an error.
      return { ok: true, referralId: referralSnap.id, alreadyAttributed: true };
    }

    if (!codeSnap.exists || (codeSnap.data() as ReferralCode).status !== "ACTIVE") {
      await recordFailedRedemption(referredUserId, code, "INVALID_CODE", tx);
      return { ok: false, code: "not-found", message: "That referral code isn't valid." };
    }
    const codeData = codeSnap.data() as ReferralCode;

    if (codeData.ownerUserId === referredUserId) {
      await recordFailedRedemption(referredUserId, code, "SELF_REFERRAL", tx);
      return { ok: false, code: "invalid-argument", message: "You can't use your own referral code." };
    }

    const { referralId } = createAttribution(
      tx,
      { referredUserId, code, codeOwnerUserId: codeData.ownerUserId, source: "MANUAL_CODE_ENTRY" },
      referralSnap,
      config
    );

    return {
      ok: true,
      referralId,
      alreadyAttributed: false,
      refereeDiscountPercent: config.refereeDiscountPercent,
      refereeDiscountCapAmount: config.refereeDiscountCapAmount,
      referrerDiscountPercent: config.referrerDiscountPercent,
      referrerDiscountCapAmount: config.referrerDiscountCapAmount,
    };
  });

  if (!result.ok) {
    throw new HttpsError(result.code, result.message);
  }
  return {
    referralId: result.referralId,
    alreadyAttributed: result.alreadyAttributed,
    refereeDiscountPercent: result.refereeDiscountPercent,
    refereeDiscountCapAmount: result.refereeDiscountCapAmount,
    // Snapshotted from the same config read as the referee fields above, at the moment of
    // redemption — the client previously had no way to show the referrer's real percentage
    // without depending on an earlier, unrelated generateMyReferralCode() call having already
    // succeeded and populated it; if that call failed (or hadn't resolved yet), the referrer's
    // share showed as a hardcoded 0% even though the referee's own 10% displayed correctly.
    referrerDiscountPercent: result.referrerDiscountPercent,
    referrerDiscountCapAmount: result.referrerDiscountCapAmount,
  };
});

/** Convenience read aggregating a user's own code + their referrals-as-referrer + their own
 *  referee status/reward into one round trip. Doubles as the "minimal test harness" for manually
 *  verifying the backend without any new app screen — call this alongside the Firebase emulator's
 *  Firestore UI. */
export const getMyReferralSummary = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const [codeSnap, asReferrerSnap, asRefereeSnap] = await Promise.all([
    db.collection("referral_codes").where("ownerUserId", "==", uid).limit(1).get(),
    db.collection("referrals").where("referrerUserId", "==", uid).get(),
    db.collection("referrals").doc(uid).get(),
  ]);

  const asReferrer = await Promise.all(
    asReferrerSnap.docs.map(async (doc) => {
      const referral = doc.data() as Referral;
      const rewardSnap = await db.collection("referral_rewards").doc(referral.referrerRewardId).get();
      return {
        referredUserId: referral.referredUserId,
        status: referral.status,
        rewardStatus: rewardSnap.exists ? (rewardSnap.data() as ReferralReward).status : null,
      };
    })
  );

  let asReferee: {
    referrerUserId: string;
    code: string;
    status: string;
    refereeReward: { status: string; discountPercent: number; discountCapAmount: number } | null;
  } | null = null;
  if (asRefereeSnap.exists) {
    const referral = asRefereeSnap.data() as Referral;
    const rewardSnap = await db.collection("referral_rewards").doc(referral.refereeRewardId).get();
    // Trimmed to plain client-serializable fields — NOT the raw doc (which carries Firestore
    // Timestamps for createdAt/appliedAt/etc. that don't cross a callable HTTPS response cleanly).
    // Read-only response shape only; no referral rule/state-machine logic touched.
    const reward = rewardSnap.exists ? (rewardSnap.data() as ReferralReward) : null;
    asReferee = {
      referrerUserId: referral.referrerUserId,
      code: referral.code,
      status: referral.status,
      refereeReward: reward
        ? { status: reward.status, discountPercent: reward.discountPercent, discountCapAmount: reward.discountCapAmount }
        : null,
    };
  }

  return {
    myCode: codeSnap.empty ? null : codeSnap.docs[0].id,
    asReferrer,
    asReferee,
  };
});

/** Daily sweep — a referral that's sat PENDING past the configured attribution window will never
 *  qualify now; cascades the referrer's still-PENDING reward straight to EXPIRED (it never had a
 *  chance to become usable, so EXPIRED rather than VOID, which is reserved for fraud/dispute
 *  reasons). Mirrors expireStaleHolds' per-document-transaction shape so one bad doc can't block
 *  the rest of the sweep. */
export const expireStaleReferrals = onSchedule("every 24 hours", async () => {
  const config = await getActiveReferralConfig().catch(() => null);
  if (!config) return;
  const cutoffMs = Date.now() - config.attributionWindowDays * 24 * 60 * 60 * 1000;
  const cutoff = Timestamp.fromMillis(cutoffMs);

  const staleSnap = await db
    .collection("referrals")
    .where("status", "==", "PENDING")
    .where("createdAt", "<=", cutoff)
    .limit(200)
    .get();

  for (const doc of staleSnap.docs) {
    // eslint-disable-next-line no-await-in-loop
    await db.runTransaction(async (tx) => {
      const referralSnap = await tx.get(doc.ref);
      if (!referralSnap.exists) return;
      const referral = referralSnap.data() as Referral;
      if (referral.status !== "PENDING") return; // resolved concurrently

      const referrerRewardRef = db.collection("referral_rewards").doc(referral.referrerRewardId);
      const referrerRewardSnap = await tx.get(referrerRewardRef);

      tx.update(referralSnap.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
      if (referrerRewardSnap.exists && (referrerRewardSnap.data() as ReferralReward).status === "PENDING") {
        tx.update(referrerRewardRef, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
      }
      writeReferralEvent(tx, {
        referralId: referral.referredUserId, // == referralSnap.id by construction
        eventType: "REFERRAL_EXPIRED",
        actor: SYSTEM_ACTOR,
        referrerUserId: referral.referrerUserId,
        referredUserId: referral.referredUserId,
      });
    });
  }
});

/** Daily sweep — a RESERVED (referee) or ISSUED (referrer) reward that's sat unused past the
 *  configured validity window auto-expires. Deliberately does NOT touch APPLIED/PENDING/REDEEMED/
 *  VOID rewards — APPLIED is mid-flight (resolved by the booking's own outcome), PENDING isn't
 *  usable yet (resolved by expireStaleReferrals), REDEEMED/VOID are already terminal. */
export const expireStaleRewards = onSchedule("every 24 hours", async () => {
  const config = await getActiveReferralConfig().catch(() => null);
  if (!config) return;
  const cutoffMs = Date.now() - config.rewardValidityDays * 24 * 60 * 60 * 1000;
  const cutoff = Timestamp.fromMillis(cutoffMs);

  for (const status of ["RESERVED", "ISSUED"] as const) {
    const staleSnap = await db
      .collection("referral_rewards")
      .where("status", "==", status)
      .where("createdAt", "<=", cutoff)
      .limit(200)
      .get();

    for (const doc of staleSnap.docs) {
      // eslint-disable-next-line no-await-in-loop
      await db.runTransaction(async (tx) => {
        const rewardSnap = await tx.get(doc.ref);
        if (!rewardSnap.exists) return;
        const reward = rewardSnap.data() as ReferralReward;
        if (reward.status !== status) return; // resolved concurrently

        tx.update(rewardSnap.ref, { status: "EXPIRED", updatedAt: FieldValue.serverTimestamp() });
        writeReferralEvent(tx, {
          referralId: reward.referralId,
          eventType: "REWARD_EXPIRED",
          actor: SYSTEM_ACTOR,
          referrerUserId: reward.role === "REFERRER" ? reward.beneficiaryUserId : "",
          referredUserId: reward.role === "REFEREE" ? reward.beneficiaryUserId : reward.referralId,
        });
      });
    }
  }
});

// Exported so booking.ts/attendance.ts/payments.ts can reference the config type check + admin
// helper without duplicating the effective-date lookup. isAdmin is currently unused here (no
// admin-only callable in this file yet — referral_configs is directly admin-writable per
// firestore.rules, no Cloud Function needed for basic CRUD) but kept for parity with every other
// feature file's local-helper convention and near-certain future use (e.g. a disableReferralCode
// admin callable).
export { getActiveReferralConfig, isAdmin };
