/**
 * One-off bootstrap for MaxTime Ops accounts — the only way an admin/adminRole claim is ever set
 * (§41: "never rely only on hiding UI buttons," and no Firestore document write can grant this).
 *
 * Usage (from the functions/ directory, after `npm install`):
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json \
 *     node scripts/set-admin-claim.js someone@maxtime.example OPS
 *
 * Role is one of: OPS, FINANCE, SUPER_ADMIN.
 * The user must already exist in Firebase Auth (e.g. created via the console) before running this.
 */
const admin = require("firebase-admin");

const [, , email, role] = process.argv;
const VALID_ROLES = ["OPS", "FINANCE", "SUPER_ADMIN"];

if (!email || !VALID_ROLES.includes(role)) {
  console.error(`Usage: node set-admin-claim.js <email> <${VALID_ROLES.join("|")}>`);
  process.exit(1);
}

admin.initializeApp();

admin
  .auth()
  .getUserByEmail(email)
  .then((user) =>
    admin.auth().setCustomUserClaims(user.uid, { admin: true, adminRole: role }).then(() => user)
  )
  .then((user) => {
    console.log(`Granted adminRole=${role} to ${email} (uid: ${user.uid}).`);
    console.log("They must sign out and back in (or wait for their token to refresh) to see it take effect.");
    process.exit(0);
  })
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
