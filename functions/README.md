# MaxTime Partner/Admin backend (Cloud Functions)

Implements `MaxTime_PARTNER.docx`'s transactional core: inventory generation, atomic booking
holds, payment webhook, attendance, payouts, and partner/staff account management. See
`src/types.ts` for the full data model.

## One-time setup

```bash
cd functions
npm install
```

## Build

```bash
npm run build        # one-off
npm run build:watch  # rebuilds on save
```

`npm run build` also runs automatically before deploy (see `firebase.json`'s `predeploy` hook).

## Deploy

Run from the **repo root** (not `functions/`), since that's where `firebase.json` lives:

```bash
firebase deploy --only functions
firebase deploy --only firestore:rules,firestore:indexes
```

Firestore rules and the two composite indexes these functions need are already live (deployed
during development) — you only need the rules/indexes command above if you change
`firestore.rules` or `firestore.indexes.json` yourself later. **The functions themselves have
never been deployed** — that step needs to happen from your own machine/CI, not from this
session's sandbox (the `firebase` CLI hangs here for reasons specific to this tool environment).

### Local emulator (optional, for testing without deploying)

```bash
npm run serve   # builds, then starts the Functions + Firestore emulators
```

## Bootstrapping your first admin account

Admin access is a Firebase Auth **custom claim** (`admin: true`, `adminRole`), never something a
client can set by writing to a document — see `src/claims.ts`'s comment for why. There's no UI
for this on purpose; it's meant to be done once, out-of-band, by whoever operates the project:

1. Create the person's account in **Firebase Console → Authentication → Add user** (email +
   password — this is what they'll use to log into the Admin Portal).
2. Get a service account key: **Project Settings → Service Accounts → Generate new private key**
   (downloads a JSON file — keep it out of git).
3. Run:
   ```bash
   cd functions
   GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json \
     node scripts/set-admin-claim.js someone@example.com SUPER_ADMIN
   ```
   (Role is one of `OPS`, `FINANCE`, `SUPER_ADMIN` — use `SUPER_ADMIN` for the very first account.)
4. They sign out and back in to the Admin Portal (or wait for their token to silently refresh) to
   see it take effect.

## What's implemented vs. not

| Area | Status |
|---|---|
| Partner account creation, review (approve/reject), staff invite-by-phone + auto-claim | ✅ |
| Availability → inventory generation, slot blocking | ✅ |
| Transactional booking hold (idempotent, capacity-safe) + multi-participant add/remove | ✅ |
| Hold-expiry sweep (scheduled, every 5 min) | ✅ |
| Payment webhook (idempotent, generic shape) | ✅ — **no real gateway wired**; signature verification is a `TODO` in `payments.ts` |
| Attendance (check-in / no-show / completed) | ✅ |
| Payout calculation (commission-rule-aware) + hold/release | ✅ |
| Audit logging | ✅ — written alongside every state-changing operation above |
| Support tickets | ❌ not built yet |
| Notification delivery (SMS/email/push) | ❌ not built — events that should trigger one are commented as `TODO`, never faked |
| Media/photo upload | ❌ not built (Firebase Storage rules exist from the consumer app, but no upload flow) |
