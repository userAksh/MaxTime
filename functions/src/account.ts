import { onCall, HttpsError } from "firebase-functions/v2/https";
import { db, auth } from "./admin";
import { trackBackendEvent } from "./analytics";

/**
 * Account deletion, required for Apple App Review Guideline 5.1.1(v) (any app that supports
 * account creation must also support in-app account deletion). Runs as the signed-in user via
 * the Admin SDK, so it bypasses Firestore rules by design — this is the one place a user's own
 * data is allowed to be wiped outright rather than just mutated through normal rule-gated writes.
 *
 * Deletes the Firebase Auth identity (immediately invalidates the session) and the user's
 * Firestore profile document. Content the user contributed to shared spaces they don't solely
 * own (group messages, bookings already made, referral records already attributed) is left in
 * place — removing it would corrupt other users' history of a shared plan/group, the same reason
 * most social apps don't cascade-delete those on account closure.
 */
export const deleteMyAccount = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const appVersion = request.data?.appVersion as string | undefined;

  await db.collection("users").doc(uid).delete();
  await auth.deleteUser(uid);

  // Fired after both deletes have actually gone through — this is the last moment `uid` is a
  // usable distinct_id for this user at all.
  trackBackendEvent("account_deleted", uid, { app_version: appVersion });

  return { deleted: true };
});
