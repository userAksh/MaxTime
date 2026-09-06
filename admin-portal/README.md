# MaxTime Admin Portal

Internal operations console for MaxTime Ops/Finance — partner approvals, bookings, and payouts.
See `MaxTime_PARTNER.docx` §28-35.

## One-time setup

```bash
npm install
```

`.env.local` already exists with the same Firebase Web App config as the Partner Portal (same
Firebase project — this is one operations surface on the same backend, not a separate app).

### Firebase Console steps required before this actually works

1. **Authentication → Sign-in method → enable Email/Password.** Admin accounts sign in with
   email/password, not phone — these are internal MaxTime staff, not customers.
2. **You need at least one admin account bootstrapped** before anyone can get past the login
   screen usefully — see "Bootstrapping your first admin account" in `functions/README.md`. There
   is deliberately no self-serve signup here.
3. **Cloud Functions must be deployed** (see `functions/README.md`) — approvals, payout
   calculation, and holds all go through callables, never a direct client write.

## Run locally

```bash
npm run dev
```

Visit `http://localhost:3000` → `/login` with the email/password you bootstrapped → `/` (Dashboard).
If you see "This account isn't authorized," the custom claim didn't take — sign out, sign back in
(this forces a fresh token), and confirm you ran the bootstrap script against the right project.

## Golden path to test end-to-end

1. Log in.
2. Go to **Partners** — a partner who just finished onboarding in the Partner Portal shows up here
   as `PENDING_VERIFICATION`. Approve them.
3. Go to **Dashboard** — the "Live partners" / "Pending approvals" counts update (these are real
   Firestore aggregation queries, not static numbers — refresh to see them move).
4. Once bookings exist against that partner, **Bookings** lists them and can look one up by ID.
5. Once a booking reaches `COMPLETED`, **Payouts → Calculate payout** (enter the partner's ID and
   a date range covering that booking) produces a real payout record, splitting gross/commission/
   net using whatever `CommissionRule` is in effect for that partner (there isn't one seeded yet —
   without one, commission comes out to ₹0, which is correct-but-unhelpful; you'll want to seed a
   `commission_rules` document for a partner before this step means anything).

## Known gaps (not built yet, not faked)

- **Support tickets** (§33) — no page, no callable.
- **Booking free-text search** — only ID lookup + a recent-bookings list; searching by customer/
  partner/activity name needs denormalized fields or a real search index.
- **Inventory management** (§31 — search/block/override across all partners) — only the Partner
  Portal's own per-partner Calendar exists; there's no cross-partner admin view yet.
- **Activity catalogue moderation** (§30 — approving a partner-proposed DRAFT activity into the
  published shared catalogue) — the Firestore rule supports it (`activities` update is
  admin-only), but there's no UI page for it yet.

## Build for production

```bash
npm run build
npm start
```

No hosting target configured yet — same options as the Partner Portal.
