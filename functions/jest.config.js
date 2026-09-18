/** Runs against the real Firestore/Auth emulators via `npm test` (firebase emulators:exec) —
 *  not mocked reads/writes. See referral.test.ts's header comment for why that matters for this
 *  feature specifically (transaction/race behavior can't be exercised against a mock). */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  rootDir: "src",
  testMatch: ["**/*.test.ts"],
  testTimeout: 20000,
};
