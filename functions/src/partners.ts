import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { writeAuditLog } from "./audit";
import { Partner, PartnerType, PartnerUser } from "./types";

function isAdmin(request: { auth?: { token?: Record<string, unknown> } }): boolean {
  return request.auth?.token?.admin === true || request.auth?.token?.adminRole != null;
}

/**
 * §6 step 1-3 — "Partner enters phone number. OTP verifies account. Choose: Venue / Instructor /
 * Both." Phone/OTP happens client-side against Firebase Auth before this is ever called; this is
 * the first server-side step, turning an authenticated-but-otherwise-anonymous user into a real
 * Partner + its OWNER PartnerUser record in one transaction. Deliberately self-serve (any signed-in
 * user can call this) since a prospective partner has no partnerId claim yet to be checked against
 * — but a user who already has one is refused, so nobody accidentally creates (or is tricked into
 * creating) a second, orphaned Partner for themselves.
 */
export const createPartnerAccount = onCall(async (request) => {
  const ownerUserId = request.auth?.uid;
  if (!ownerUserId) throw new HttpsError("unauthenticated", "Sign in required.");
  if (request.auth?.token?.partnerId) {
    throw new HttpsError("already-exists", "This account is already linked to a partner.");
  }

  const type = request.data?.type as PartnerType;
  const displayName = request.data?.displayName as string;
  const legalName = (request.data?.legalName as string | undefined) ?? null;
  if (!type || !["VENUE", "INSTRUCTOR", "BOTH"].includes(type) || !displayName) {
    throw new HttpsError("invalid-argument", "type (VENUE/INSTRUCTOR/BOTH) and displayName are required.");
  }

  const partnerRef = db.collection("partners").doc();
  const partnerUserRef = db.collection("partner_users").doc();

  await db.runTransaction(async (tx) => {
    // The check above only catches the common case — claims here are set by a Firestore trigger
    // (onPartnerUserWrite) after this function returns, not inline, so there's a real window
    // right after a first signup where the client's ID token still has no partnerId claim even
    // though a partner already exists (e.g. a reload before the trigger fires and the token
    // refreshes). Firestore itself, not the token, is the authoritative guard against a duplicate.
    const existing = await tx.get(db.collection("partners").where("ownerUserId", "==", ownerUserId).limit(1));
    if (!existing.empty) {
      throw new HttpsError("already-exists", "This account is already linked to a partner.");
    }

    const partner: Omit<Partner, "id"> = {
      type,
      displayName,
      legalName,
      ownerUserId,
      status: "PENDING_VERIFICATION",
      commercial: null,
      submittedAt: null,
      createdAt: FieldValue.serverTimestamp() as never,
      updatedAt: FieldValue.serverTimestamp() as never,
    };
    tx.set(partnerRef, partner);

    const partnerUser: Omit<PartnerUser, "id"> = {
      partnerId: partnerRef.id,
      userId: ownerUserId,
      invitedPhone: null,
      role: "OWNER",
      status: "ACTIVE",
      invitedAt: FieldValue.serverTimestamp() as never,
      activatedAt: FieldValue.serverTimestamp() as never,
    };
    tx.set(partnerUserRef, partnerUser);
    // onPartnerUserWrite (Firestore trigger) picks this up and sets the partnerId/partnerRole
    // custom claims — not done inline here since claim propagation to the client's ID token
    // needs a token refresh regardless of where the claim gets set.

    await writeAuditLog(
      {
        actorUserId: ownerUserId,
        actorRole: "PARTNER",
        entityType: "Partner",
        entityId: partnerRef.id,
        action: "CREATE",
        after: { type, displayName, status: "PENDING_VERIFICATION" },
      },
      tx
    );
  });

  return { partnerId: partnerRef.id };
});

/**
 * §6 step 14 / §29 — "After approval, publish or activate inventory." Admin-only; the partner
 * cannot self-approve. Rejection preserves the record (never deletes) so the decision and reason
 * stay auditable per §40's "never silently delete."
 */
export const reviewPartnerAccount = onCall(async (request) => {
  if (!isAdmin(request)) throw new HttpsError("permission-denied", "Admin only.");

  const partnerId = request.data?.partnerId as string;
  const decision = request.data?.decision as "APPROVED" | "REJECTED";
  const notes = request.data?.notes as string | undefined;
  if (!partnerId || !["APPROVED", "REJECTED"].includes(decision)) {
    throw new HttpsError("invalid-argument", "partnerId and decision (APPROVED/REJECTED) are required.");
  }
  if (decision === "REJECTED" && !notes?.trim()) {
    throw new HttpsError("invalid-argument", "A rejection reason is required.");
  }

  await db.runTransaction(async (tx) => {
    const ref = db.collection("partners").doc(partnerId);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Partner not found.");
    const before = snap.data() as Partner;
    if (before.status !== "PENDING_VERIFICATION") {
      throw new HttpsError("failed-precondition", `Partner is already ${before.status}, not pending review.`);
    }

    tx.update(ref, { status: decision, verificationNotes: notes ?? null, updatedAt: FieldValue.serverTimestamp() });
    await writeAuditLog(
      {
        actorUserId: request.auth!.uid,
        actorRole: "ADMIN",
        entityType: "Partner",
        entityId: partnerId,
        action: `REVIEW_${decision}`,
        reason: notes,
        before: { status: before.status },
        after: { status: decision },
      },
      tx
    );
  });

  return { ok: true };
});

/**
 * The final "Submit for review" step of onboarding, for BOTH of the two cases where a partner
 * record already exists by the time the partner reaches it:
 *  - REJECTED, resubmitting after fixing whatever got them rejected (the original purpose here —
 *    createPartnerAccount refuses anyone who already has a partnerId claim, so a rejected partner
 *    otherwise has no way back in; this flips the SAME record back to pending, keeping their
 *    venue/service data rather than redoing onboarding from scratch).
 *  - PENDING_VERIFICATION with submittedAt still null — a first-time signup finishing onboarding
 *    for the first time (possibly across sessions, having left mid-flow and come back). status
 *    alone flips to PENDING_VERIFICATION at step 0, well before the venue/service steps, so
 *    admins need submittedAt, not status, to tell "actually submitted" apart from "still filling
 *    out the form" or "abandoned partway."
 * Anything else (APPROVED, SUSPENDED, or already-submitted PENDING_VERIFICATION) has nothing to
 * (re)submit.
 */
export const resubmitPartnerAccount = onCall(async (request) => {
  const callerPartnerId = request.auth?.token?.partnerId as string | undefined;
  const callerRole = request.auth?.token?.partnerRole as string | undefined;
  if (!callerPartnerId || (callerRole !== "OWNER" && callerRole !== "MANAGER")) {
    throw new HttpsError("permission-denied", "Only the owner or a manager can submit this account for review.");
  }

  // The onboarding form's Step 0 (business name/type) is shown again on resubmit so the partner
  // can fix whatever got them rejected — these are optional overrides for exactly that; without
  // them here, anything retyped in that step would silently never reach Firestore.
  const displayName = request.data?.displayName as string | undefined;
  const type = request.data?.type as PartnerType | undefined;
  if (type && !["VENUE", "INSTRUCTOR", "BOTH"].includes(type)) {
    throw new HttpsError("invalid-argument", "type must be VENUE, INSTRUCTOR, or BOTH.");
  }

  await db.runTransaction(async (tx) => {
    const ref = db.collection("partners").doc(callerPartnerId);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "Partner not found.");
    const before = snap.data() as Partner;
    const isFirstSubmission = before.status === "PENDING_VERIFICATION" && !before.submittedAt;
    if (before.status !== "REJECTED" && !isFirstSubmission) {
      throw new HttpsError("failed-precondition", `Partner is ${before.status} — nothing to (re)submit.`);
    }

    tx.update(ref, {
      status: "PENDING_VERIFICATION",
      verificationNotes: null,
      submittedAt: FieldValue.serverTimestamp(),
      ...(displayName ? { displayName } : {}),
      ...(type ? { type } : {}),
      updatedAt: FieldValue.serverTimestamp(),
    });
    await writeAuditLog(
      {
        actorUserId: request.auth!.uid,
        actorRole: "PARTNER",
        entityType: "Partner",
        entityId: callerPartnerId,
        action: isFirstSubmission ? "SUBMIT" : "RESUBMIT",
        before: { status: before.status, displayName: before.displayName },
        after: { status: "PENDING_VERIFICATION", displayName: displayName ?? before.displayName },
      },
      tx
    );
  });

  return { ok: true };
});

/**
 * §22 — "Owner can invite staff by phone/email." Only the owner (or an existing manager, per the
 * permissions matrix) can grow their own partner's staff list — never a bare client write to
 * partner_users, which would let anyone assign themselves any role on any partner.
 */
export const invitePartnerStaff = onCall(async (request) => {
  const callerUserId = request.auth?.uid;
  const callerPartnerId = request.auth?.token?.partnerId as string | undefined;
  const callerRole = request.auth?.token?.partnerRole as string | undefined;
  if (!callerUserId || !callerPartnerId || (callerRole !== "OWNER" && callerRole !== "MANAGER")) {
    throw new HttpsError("permission-denied", "Only an owner or manager can invite staff.");
  }

  const phone = request.data?.phone as string;
  const role = request.data?.role as "MANAGER" | "INSTRUCTOR";
  if (!phone || !["MANAGER", "INSTRUCTOR"].includes(role)) {
    throw new HttpsError("invalid-argument", "phone and role (MANAGER/INSTRUCTOR) are required.");
  }

  const partnerUserRef = db.collection("partner_users").doc();
  const partnerUser: Omit<PartnerUser, "id"> = {
    partnerId: callerPartnerId,
    userId: null,
    invitedPhone: phone,
    role,
    status: "INVITED",
    invitedAt: FieldValue.serverTimestamp() as never,
    activatedAt: null,
  };
  await partnerUserRef.set(partnerUser);
  await writeAuditLog({
    actorUserId: callerUserId,
    actorRole: "PARTNER",
    entityType: "PartnerUser",
    entityId: partnerUserRef.id,
    action: "INVITE",
    after: { partnerId: callerPartnerId, invitedPhone: phone, role },
  });

  return { partnerUserId: partnerUserRef.id };
});

/**
 * The other half of invitePartnerStaff: once the invited phone number actually signs in (their
 * own phone/OTP login, same as any partner), the Partner Portal calls this once to link that
 * fresh uid onto the matching INVITED record — this is what actually turns "invited by phone"
 * into "activated with real claims," not a UI label that never resolves to anything.
 */
export const claimStaffInvite = onCall(async (request) => {
  const userId = request.auth?.uid;
  const phone = request.auth?.token?.phone_number as string | undefined;
  if (!userId || !phone) throw new HttpsError("unauthenticated", "Sign in with the invited phone number first.");

  const pendingSnap = await db
    .collection("partner_users")
    .where("invitedPhone", "==", phone)
    .where("status", "==", "INVITED")
    .limit(1)
    .get();
  if (pendingSnap.empty) return { claimed: false };

  const ref = pendingSnap.docs[0].ref;
  await ref.update({
    userId,
    status: "ACTIVE",
    activatedAt: FieldValue.serverTimestamp(),
  });
  return { claimed: true, partnerId: (pendingSnap.docs[0].data() as PartnerUser).partnerId };
});
