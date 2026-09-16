import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { auth, db } from "./admin";
import { PartnerUser } from "./types";

/**
 * §41 — "Server-side role-based authorization... never rely only on hiding UI buttons." Firestore
 * rules can only see request.auth.token, so every PartnerUser write mirrors {partnerId, role, status}
 * onto that user's custom claims here — rules then check the token directly instead of doing a
 * second document read (which security rules can do, but at real latency/complexity cost for
 * every single request).
 *
 * ADMIN/SUPER_ADMIN/FINANCE claims are NOT set here — those are bootstrapped out-of-band by
 * MaxTime Ops via a one-off Admin SDK script, never through a client-writable document, since a
 * partner must never be able to grant themselves admin access by writing to a collection.
 */
export const onPartnerUserWrite = onDocumentWritten(
  "partner_users/{partnerUserId}",
  async (event) => {
    const after = event.data?.after?.data() as PartnerUser | undefined;
    const before = event.data?.before?.data() as PartnerUser | undefined;
    const userId = after?.userId ?? before?.userId;
    if (!userId) return;

    if (after && after.status !== "DEACTIVATED") {
      await auth.setCustomUserClaims(userId, {
        partnerId: after.partnerId,
        partnerRole: after.role,
      });
      return;
    }

    // This particular membership was deleted or deactivated — but the same person can still have
    // a DIFFERENT active partner_users record (e.g. a leftover duplicate signup being cleaned up,
    // or genuinely belonging to more than one partner) whose claims must not be wiped out just
    // because this unrelated one went away.
    const stillActive = await db
      .collection("partner_users")
      .where("userId", "==", userId)
      .where("status", "==", "ACTIVE")
      .limit(1)
      .get();

    if (stillActive.empty) {
      await auth.setCustomUserClaims(userId, null);
      return;
    }

    const other = stillActive.docs[0].data() as PartnerUser;
    await auth.setCustomUserClaims(userId, {
      partnerId: other.partnerId,
      partnerRole: other.role,
    });
  }
);
