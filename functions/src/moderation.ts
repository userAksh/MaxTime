import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { trackBackendEvent } from "./analytics";

const VALID_REASONS = ["spam", "harassment", "inappropriate_content", "other"] as const;

/**
 * Apple App Review Guideline 1.2 (UGC safety) requires a way to report abusive users/content in
 * any app with user-to-user communication. Writes to `reports` for an admin to review and act on
 * (removing content / banning a user) — this function only records the report, it doesn't take
 * any automated action itself.
 */
export const reportUser = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const { groupId, reportedUserId, reason, messageText, appVersion } = request.data ?? {};
  if (typeof groupId !== "string" || !groupId) {
    throw new HttpsError("invalid-argument", "groupId is required.");
  }
  if (typeof reportedUserId !== "string" || !reportedUserId) {
    throw new HttpsError("invalid-argument", "reportedUserId is required.");
  }
  if (reportedUserId === uid) {
    throw new HttpsError("invalid-argument", "Cannot report yourself.");
  }
  if (typeof reason !== "string" || !VALID_REASONS.includes(reason as typeof VALID_REASONS[number])) {
    throw new HttpsError("invalid-argument", "reason must be one of: " + VALID_REASONS.join(", "));
  }

  await db.collection("reports").add({
    reporterUserId: uid,
    reportedUserId,
    groupId,
    reason,
    messageText: typeof messageText === "string" ? messageText.slice(0, 2000) : null,
    status: "open",
    createdAt: FieldValue.serverTimestamp(),
  });

  // messageText is deliberately excluded — free-text user content, not an analytics property.
  trackBackendEvent("user_reported", uid, { group_id: groupId, reported_user_id: reportedUserId, reason, app_version: appVersion });

  return { reported: true };
});
