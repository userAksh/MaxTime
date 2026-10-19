import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { trackBackendEvent } from "./analytics";

interface GroupDocShape {
  activity: string;
  memberUids?: string[];
}

/**
 * Phase 0 audit finding #4 — group_created/group_joined were client-only events, with no
 * independent server-side confirmation the underlying write actually happened or was legitimate
 * (see firestore.rules' groups/{groupId} comment for the membership model this reads).
 * Deliberately additive, not a replacement: the client events stay as the primary signal (richer
 * timing, fires the instant the user acts); these _confirmed variants exist so a client that lied
 * about firing the event — or crashed before the write actually landed — doesn't silently produce
 * a false "group_created" with no group to back it up.
 *
 * One trigger, not two, so create-vs-join is read from the same before/after diff rather than
 * needing two separate listeners to agree on ordering.
 */
export const onGroupWrite = onDocumentWritten("groups/{groupId}", async (event) => {
  const after = event.data?.after?.data() as GroupDocShape | undefined;
  if (!after) return; // deleted — groups are never actually deletable per the rules, but be safe

  const groupId = event.params.groupId;
  const afterUids = after.memberUids ?? [];
  const before = event.data?.before?.data() as GroupDocShape | undefined;

  if (!before) {
    // Newly created. The creator's uid is always memberUids[0] when known (getOrCreateMyGroup) —
    // absent for the demo-seeded "Start it" path (joinExistingGroup), which is intentional, not a
    // bug: see Group.memberUids' doc comment in Models.kt.
    const creatorUid = afterUids[0];
    if (creatorUid) {
      trackBackendEvent("group_created_confirmed", creatorUid, {
        group_id: groupId,
        activity_id: after.activity,
      });
    }
    return;
  }

  const beforeUids = new Set(before.memberUids ?? []);
  const newlyJoinedUids = afterUids.filter((uid) => !beforeUids.has(uid));
  for (const uid of newlyJoinedUids) {
    trackBackendEvent("group_joined_confirmed", uid, {
      group_id: groupId,
      activity_id: after.activity,
    });
  }
});
