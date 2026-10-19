/**
 * Covers the groups/{groupId} and groups/{groupId}/messages/{messageId} security rules — the
 * fix for Phase 0 audit finding #1 (any signed-in user could previously write to any group or
 * post into any group's chat, not just ones they're a member of). See firestore.rules' own
 * comment above these two match blocks for the membership model this relies on
 * (Group.memberUids, populated client-side from the real Firebase Auth uid).
 */
import * as fs from "fs";
import * as path from "path";
import { initializeTestEnvironment, RulesTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";

describe("firestore.rules — groups collections", () => {
  let testEnv: RulesTestEnvironment;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: "maxtime-groups-rules-test",
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

  async function seedGroup(overrides: Record<string, unknown> = {}): Promise<void> {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection("groups").doc("group-1").set({
        activity: "Badminton",
        whenLabel: "Today, 6:00 PM",
        where: "Koramangala",
        venue: "Court A",
        needed: 1,
        capacity: 4,
        creator: "creator-name",
        priceNormal: 500,
        priceIfDeadlineMissed: 600,
        icon: "🏸",
        status: "FILLING",
        paymentStatus: "NOT_REQUESTED",
        bookingStatus: "UNBOOKED",
        members: [],
        connections: [],
        memberUids: ["creator-uid"],
        participantNames: [],
        ...overrides,
      });
    });
  }

  it("any signed-in user can read a group", async () => {
    await seedGroup();
    const strangerDb = testEnv.authenticatedContext("stranger-1").firestore();
    await assertSucceeds(strangerDb.collection("groups").doc("group-1").get());
  });

  it("any signed-in user can create a brand-new group document", async () => {
    const creatorDb = testEnv.authenticatedContext("creator-uid").firestore();
    await assertSucceeds(
      creatorDb.collection("groups").doc("group-new").set({
        activity: "Pottery",
        memberUids: ["creator-uid"],
        members: [],
        connections: [],
        participantNames: [],
        status: "FILLING",
      })
    );
  });

  it("a non-member CANNOT change another group's price, capacity, venue, or creator", async () => {
    await seedGroup();
    const strangerDb = testEnv.authenticatedContext("stranger-1").firestore();
    await assertFails(
      strangerDb.collection("groups").doc("group-1").update({ priceNormal: 1 })
    );
    await assertFails(
      strangerDb.collection("groups").doc("group-1").update({ capacity: 999 })
    );
    await assertFails(
      strangerDb.collection("groups").doc("group-1").update({ venue: "Hijacked Venue" })
    );
    await assertFails(
      strangerDb.collection("groups").doc("group-1").update({ creator: "stranger-1" })
    );
  });

  it("a non-member CAN add themselves as a member (self-join)", async () => {
    await seedGroup();
    const joinerDb = testEnv.authenticatedContext("joiner-uid").firestore();
    await assertSucceeds(
      joinerDb.collection("groups").doc("group-1").update({
        memberUids: ["creator-uid", "joiner-uid"],
        members: [{ personName: "Joiner", role: "MEMBER", joinedAt: "Just now", photoUrl: null }],
        connections: ["Joiner"],
        participantNames: ["Joiner"],
      })
    );
  });

  it("a non-member self-joining CANNOT sneak in an unrelated field change in the same write", async () => {
    await seedGroup();
    const joinerDb = testEnv.authenticatedContext("joiner-uid").firestore();
    await assertFails(
      joinerDb.collection("groups").doc("group-1").update({
        memberUids: ["creator-uid", "joiner-uid"],
        priceNormal: 1, // smuggled alongside a legitimate-looking self-join
      })
    );
  });

  it("an already-verified member CAN update broader fields (e.g. venue/status resolution)", async () => {
    await seedGroup();
    const memberDb = testEnv.authenticatedContext("creator-uid").firestore();
    await assertSucceeds(
      memberDb.collection("groups").doc("group-1").update({ venue: "Court B", status: "FULL" })
    );
  });

  it("deletes are always blocked", async () => {
    await seedGroup();
    const memberDb = testEnv.authenticatedContext("creator-uid").firestore();
    await assertFails(memberDb.collection("groups").doc("group-1").delete());
  });

  it("a verified member CAN post a message; a non-member CANNOT", async () => {
    await seedGroup();
    const memberDb = testEnv.authenticatedContext("creator-uid").firestore();
    const strangerDb = testEnv.authenticatedContext("stranger-1").firestore();

    await assertSucceeds(
      memberDb.collection("groups").doc("group-1").collection("messages").add({
        senderId: "creator-uid",
        senderName: "Creator",
        text: "hello",
      })
    );
    await assertFails(
      strangerDb.collection("groups").doc("group-1").collection("messages").add({
        senderId: "stranger-1",
        senderName: "Stranger",
        text: "intruder",
      })
    );
  });

  it("messages cannot be edited or deleted by any client", async () => {
    let messageId = "";
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await seedGroup();
      const ref = await ctx
        .firestore()
        .collection("groups")
        .doc("group-1")
        .collection("messages")
        .add({ senderId: "creator-uid", text: "hello" });
      messageId = ref.id;
    });
    const memberDb = testEnv.authenticatedContext("creator-uid").firestore();
    await assertFails(
      memberDb.collection("groups").doc("group-1").collection("messages").doc(messageId).update({ text: "edited" })
    );
    await assertFails(
      memberDb.collection("groups").doc("group-1").collection("messages").doc(messageId).delete()
    );
  });
});
