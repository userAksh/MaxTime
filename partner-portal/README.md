# MaxTime Partner Portal

Responsive web app for venue owners, managers, and instructors — onboarding, activities,
calendar, bookings, and payouts. See `MaxTime_PARTNER.docx` §6-27, §44.

## One-time setup

```bash
npm install
```

`.env.local` already exists with the real Firebase Web App config (`NEXT_PUBLIC_FIREBASE_*`) and
is gitignored. If you're setting this up somewhere fresh, get the same values from **Firebase
Console → Project Settings → General → Your apps → (the Web app named "MaxTime Partner & Admin
Portals")**.

### Firebase Console steps required before this actually works

1. **Authentication → Sign-in method → enable Phone.** This portal signs partners in with
   phone/OTP, same as the consumer app.
2. **Authentication → Settings → Authorized domains** — `localhost` is allowed by default; add
   your real domain once you deploy somewhere.
3. For local testing without burning real SMS, a test number is already configured on this
   project (**Authentication → Sign-in method → Phone → Phone numbers for testing**): enter
   `1234567890` in the login screen's phone field, and use OTP code `000000` when prompted — no
   real SMS is sent for this number.
4. **Cloud Functions must be deployed** (see `functions/README.md`) — every write except the
   direct Firestore ones (venue/service/availability during onboarding) goes through a callable.

## Run locally

```bash
npm run dev
```

Then visit `http://localhost:3000` → `/login` → phone/OTP → you'll land in `/onboarding` (no
partner account yet) or `/` (if you already have one).

## Golden path to test end-to-end

1. Sign in with a test phone number.
2. Complete onboarding (business → venue → activity/service → availability → submit). You'll land
   on a "pending review" screen — this is correct, not a bug.
3. In the **Admin Portal**, approve the new partner (see `admin-portal/README.md`).
4. Back here, refresh — Home now shows the real dashboard. Go to **Activities** and publish both
   your venue and your service (§6 step 14 — nothing goes live on its own just because it was
   approved).
5. Go to **Calendar → Generate slots** on your availability rule to actually create bookable
   inventory.
6. There's no consumer-facing booking UI in this portal (bookings come from a direct
   `createBookingHold` call for now — see the note below about the consumer app) — but once one
   exists, it'll show up live under **Bookings**, and you can check it in from there.

## Important: this is not yet connected to the MaxTime consumer app

The consumer (Android/iOS) app still reads from its own `sessions`/`venue_availability`
collections, built earlier and unrelated to this schema. Venues/services/inventory created here
via onboarding do **not** yet show up for a customer browsing the consumer app — bridging the two
(having the consumer app read from `inventory_slots`/`services` instead of, or alongside, its
current mock-ish collections) is a real integration step that hasn't been done yet, not an
oversight to assume away.

## Known simplifications (not bugs)

- **Calendar** is a list grouped by date, not a visual grid — the data underneath is real, the
  drag/tap grid UI is a follow-up.
- **Venue location** is plain lat/lng number inputs, no map picker (§46 explicitly says not to
  build real maps in V1).
- **Media upload** isn't built — venues/services publish without photos for now.
- **Commercial terms** intentionally has no partner-facing form — per the permissions matrix
  (§35), only Admin can set commission, so there's nothing for a partner to fill in here.

## Build for production

```bash
npm run build
npm start
```

No hosting target is configured yet (Vercel and Firebase Hosting both work fine with this stack —
pick one when you're ready to actually deploy it somewhere).
