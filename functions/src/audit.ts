import { FieldValue, Transaction } from "firebase-admin/firestore";
import { db } from "./admin";
import { AuditLog } from "./types";

/**
 * §40/§52 — every manual override, refund, payout hold, or permission change must produce one of
 * these. Takes an optional transaction so it can be written atomically alongside the state change
 * it's recording, rather than as a separate (and therefore skippable) follow-up write.
 */
export async function writeAuditLog(
  entry: Omit<AuditLog, "id" | "createdAt">,
  tx?: Transaction
): Promise<void> {
  const ref = db.collection("audit_logs").doc();
  const doc = { ...entry, createdAt: FieldValue.serverTimestamp() };
  if (tx) {
    tx.set(ref, doc);
  } else {
    await ref.set(doc);
  }
}
