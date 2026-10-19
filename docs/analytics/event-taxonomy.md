# MaxTime Analytics Event Taxonomy

**This is the original Phase 1 planning doc — for the current, verified-against-shipped-code
event list and architecture, see [`analytics-system-handover.md`](./analytics-system-handover.md)
instead.** Kept here for its original planning rationale; superseded on two points below: the
vendor integration approach changed (see next paragraph), and 2 new events
(`group_created_confirmed`/`group_joined_confirmed`) and `category_id`/`app_version` property
additions landed after this was written — not reflected below.

Phase 1 contract, derived from `MaxTime_Analytics_Tracking_Architecture_for_Claude.docx` and the
Phase 0 audit of this repository. This document is the single source of truth for event names,
properties, source, trigger, and identity rules. Only events for features that actually exist in
this codebase today are specified — nothing here assumes unbuilt infrastructure (deep links, push
notifications beyond auth).

Vendor: Mixpanel — **superseded**: the official Mixpanel iOS SDK turned out to be unusable from
Kotlin/Native (pure-Swift API, no cinterop bindings generated), so the shipped implementation
calls Mixpanel's public HTTP tracking API directly via Ktor from one shared `commonMain` object
(`AnalyticsTracker`), not native per-platform SDKs behind an `expect`/`actual` interface as
planned here. Crash reporting: `dev.gitlive:firebase-crashlytics` — this part shipped as planned,
real native SDKs on both platforms (Crashlytics needs genuine native crash-handler hooks that
can't be worked around the way Mixpanel's was).

Consent: analytics fires by default, no consent gate (confirmed decision, 2026-10-19).

## 1. Environments

This project has three real, separate Firebase projects, not a single flag:

| Build flavor | `environment` value | Firebase project | Bundle/package ID |
|---|---|---|---|
| production | `production` | `maxtime-app` | `com.maxtime.android` / `com.maxtime.ios` |
| staging | `staging` | `maxtime-staging` | `com.maxtime.android.staging` / `com.maxtime.ios.staging` |
| beta | `beta` | `maxtime-beta` | `com.maxtime.android.beta` / `com.maxtime.ios.beta` |

`environment` is derived from the existing build flavor/scheme at compile time — never inferred
from a URL or runtime check. Each environment gets its own Mixpanel project (or project token) so
dev/staging traffic never pollutes production dashboards.

## 2. Identity rules

- **Guest (unauthenticated)**: MaxTime has a real "Continue as guest" path (`continueAsGuest()`)
  that bypasses phone auth entirely. Use Mixpanel's anonymous device ID for guests — do not
  fingerprint.
- **Guest → account conversion**: if a guest later signs in via phone OTP, call `identify()` with
  the new opaque `user_id` and alias it to the prior anonymous ID (Mixpanel's standard
  identity-merge mechanism), so activation-speed metrics aren't skewed by treating a converting
  guest as a brand-new user. Fire `account_created_from_guest: true` on that `sign_up_completed`
  event.
- **After authentication**: identify using the Firebase Auth UID (already the opaque internal ID
  used throughout the backend — `customerUserId`, `request.auth.uid`). Never use phone number as
  the identity key.
- **Logout/account switch**: call `reset()` on `AuthRepository.signOut()` (both the explicit sign
  out and the post-delete teardown in `MyProfileScreen.confirmDeleteAccount()`), before navigating
  back to `Screen.Splash`. This must happen in the same place for both exit paths, not duplicated.
- **Account deletion**: `deleteMyAccount` already exists server-side. Analytics must stop
  attributing events to that `user_id` the moment deletion succeeds — the client-side `reset()`
  above handles this for future events; no retroactive anonymization of past events is in scope
  for Phase 1.

## 3. Common properties (every event)

| Property | Definition |
|---|---|
| `schema_version` | `1` |
| `platform` | `android` \| `ios` |
| `app_version` | from the build's `MARKETING_VERSION`/`versionName` |
| `environment` | `production` \| `staging` \| `beta` (see §1) |
| `user_id` | Firebase Auth UID if signed in; omitted for guests (Mixpanel anonymous ID covers it) |
| `app_session_id` | app-foreground session, distinct from `activity_session_id` (a bookable slot) |
| `event_id` | client-generated UUID for dedup; backend events use the natural idempotency key already present (see §6) |

## 4. Sensitive-data denylist — never sent in any event or property

Phone numbers, email addresses, OTP codes, Razorpay card/UPI details or tokens, government ID
numbers, raw chat message bodies, exact GPS coordinates (use `area_id`/the existing `UserLocation.area`
string's coarse label only), full address strings, auth tokens/secrets.

## 5. Canonical events

Source `Client` = fired from Compose UI code. Source `Backend` = fired from the Cloud Function at
the point the Firestore transaction commits, piggybacking on existing idempotency — never a
separate best-effort write.

### Auth & onboarding

| Event | Source | Trigger | Properties | Notes |
|---|---|---|---|---|
| `app_opened` | Client | App foreground, new session | `entry_point` | — |
| `guest_mode_entered` | Client | `continueAsGuest()` invoked | — | New vs. doc — MaxTime-specific path |
| `onboarding_started` / `onboarding_completed` | Client | `AgeGateScreen` shown / `InterestsScreen` finish | `steps_completed` | |
| `sign_up_started` | Client | `AuthView.ENTRY` shown | — | |
| `sign_up_completed` | Backend-preferred | First successful phone verification for a new UID | `method: "phone_otp"`, `account_created_from_guest` | |
| `login_completed` | Client | Returning user verifies OTP | `method: "phone_otp"` | |
| `account_deletion_requested` | Client | `showDeleteConfirm` dialog confirmed | — | New vs. doc — promoted from use-case table to first-class event |
| `account_deleted` | Backend | `deleteMyAccount` Cloud Function returns `{deleted: true}` | — | New vs. doc |

### Discovery

| Event | Source | Trigger | Properties | Notes |
|---|---|---|---|---|
| `home_viewed` | Client | Home tab composition | `entry_point` | |
| `activity_feed_viewed` | Client | Discover tab/category grid visible | `category_filter, area_id, result_count` | |
| `activity_detail_viewed` | Client | `ActivityDetailScreen` visible | `activity_id, category_id, price_minor, currency, availability_status` | |
| `activity_search_no_results` | Client | Zero sessions within radius | `category_id, area_id` | Maps to the real "No sessions found within Xkm" state |

### Groups & chat (live features)

| Event | Source | Trigger | Properties | Notes |
|---|---|---|---|---|
| `group_created` | Backend-preferred | `getOrCreateMyGroup` persists | `group_id, activity_id, activity_session_id, participation_mode: "group_create"` | |
| `group_joined` | Backend-preferred | `joinExistingGroup` persists | `group_id, activity_id, participation_mode: "group_join"` | |
| `chat_opened` | Client | `GroupChatScreen` composed | `group_id, activity_id` | |
| `chat_message_sent` | Backend-preferred | `ChatRepository.sendMessage` write succeeds | `group_id, message_type: "text"` | Never message body |
| `user_reported` | Backend | `reportUser` Cloud Function writes to `reports` | `group_id, reason` | **New — not in source doc.** Trust & safety has zero coverage otherwise |
| `user_blocked` | Client | `BlockedUsersRepository.blockUser` write succeeds | `group_id` | **New — not in source doc** |

### Booking & payment

`participation_mode: "solo" | "group_join" | "group_create"` is attached to every booking event
below — **new vs. source doc**, required to answer the document's own "social value" business
question (§1, bullet 6), which is unanswerable without it.

| Event | Source | Trigger | Properties | Notes |
|---|---|---|---|---|
| `booking_started` | Client | "Book Solo" tapped / BookingReview entered | `activity_id, activity_session_id, participation_mode, quantity, amount_minor, currency` | Attempt, not confirmation |
| `payment_started` | Client | Razorpay checkout launched | `booking_id, amount_minor, currency, provider: "razorpay"` | |
| `payment_succeeded` | Backend | `paymentWebhook`, inside the transaction, only on first-time `payment.captured` (existing `providerTransactionId` idempotency check) | `booking_id, amount_minor, currency, payment_method_category` | Authoritative, inherits existing dedup for free |
| `payment_failed` | Backend | `paymentWebhook`, `payment.failed` branch | `booking_id, normalized_failure_category` | |
| `booking_confirmed` | Backend | `paymentWebhook` transitions booking to `CONFIRMED` | `booking_id, activity_id, activity_session_id, amount_minor, fee_minor, currency, participation_mode` | See §7 for `amount_minor` derivation |
| `booking_expired` | Backend | `expireStaleHolds` transitions `PENDING_PAYMENT → EXPIRED` | `booking_id` | **New — distinct from `booking_cancelled` in source doc.** A silent checkout-abandonment timeout is a different funnel signal than an active cancellation; conflating them blurs the payment funnel (document's own business question #4) |
| `booking_cancelled` | Backend | `CANCELLED_BY_USER` / `CANCELLED_BY_PARTNER` transition | `booking_id, actor_type, reason_category` | |
| `referral_reward_applied` | Backend | `appliedReferralRewardId` set on booking at checkout | `booking_id, reward_type` | **New — not in source doc's earned/redeemed split** |
| `referral_reward_reverted` | Backend | `revertAppliedReward` called (payment failure path) | `booking_id, reason: "payment_failed"` | **New** — a real, existing code path with no prior event |

### Attendance

| Event | Source | Trigger | Properties | Notes |
|---|---|---|---|---|
| `check_in_completed` | Backend | `markAttendance` status `CHECKED_IN` | `booking_id, activity_session_id` | Partner/admin-triggered, not QR/self-report |
| `activity_attended` | Backend | `markAttendance` status `COMPLETED` | `booking_id, activity_id, activity_session_id` | Canonical completed attendance |
| `no_show_recorded` | Backend | `markAttendance` status `NO_SHOW` | `booking_id, activity_session_id` | |

### Not instrumented in Phase 1 — explicitly out of scope

Per the document's own instruction not to instrument unimplemented flows as if live:

- **Push notification events** (`activity_reminder_sent`, `push_notification_opened`) — no product
  notification system exists; the only push usage today is Firebase Phone Auth's silent
  verification push.
- **Deep-link-based events** (`activity_share_opened`, `invite_accepted` via link,
  campaign-attributed `referral_attributed`) — no Associated Domains / App Links / deep link
  infrastructure exists. Referral today is in-app code entry only.
- **Dispute flow** (`DISPUTED`/`RESOLVED` booking states exist server-side, but no user-facing
  dispute UI was found) — revisit if/when a dispute flow ships.

## 6. Backend event emission & idempotency

Every `Backend`-sourced event above is emitted from inside the same Firestore transaction that
performs the durable state change, gated on the same check that already makes that transaction
idempotent:

- `paymentWebhook`: the `payments/{providerTransactionId}` existence check already present in
  `functions/src/payments.ts` — a repeated webhook call short-circuits to `outcome = "already-processed"`
  before any write, so no duplicate analytics event will fire either.
- `markAttendance`: gated on `BOOKING_TRANSITIONS[booking.status].includes(nextBookingStatus)` —
  an illegal/repeat transition throws before any write.
- `expireStaleHolds`: already only acts on bookings still in `PENDING_PAYMENT` past the hold
  window.

No new idempotency mechanism needs to be built — analytics rides on infrastructure that already
exists.

## 7. `amount_minor` convention

The source document specifies amounts as **integer paise**. This repo's existing Firestore data
does **not** use that convention today — `payments.grossAmount` is stored in **rupees**
(`functions/src/payments.ts`: `paymentEntity.amount / 100`). To avoid silently corrupting revenue
metrics:

- `amount_minor` in every analytics event = **rupees × 100**, computed at the point of event
  emission, never read directly off `grossAmount`.
- `fee_minor` is a new field (not currently split out anywhere in Firestore) — Phase 3 needs to
  confirm where the fee amount shown at checkout (₹18 on a ₹299 session in the manual test) is
  actually computed, since it is not currently a separate stored field on the booking/payment
  documents. **Flagging as a Phase 3 blocker**, not resolved by this document.

## 8. Attribution

No deep link or campaign infrastructure exists (§5, "Not instrumented"). `campaign_source` /
`campaign_id` properties are reserved in the schema but will be empty/absent for all events until
that infrastructure is built — not fabricated from referrer heuristics.
