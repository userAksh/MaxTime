# MaxTime Analytics — System Handover

Written as a standalone reference for someone (human or AI) who has no access to the codebase but
needs a complete, accurate picture of what analytics exists in MaxTime: what's tracked, how it's
wired, what it deliberately excludes, and what's still missing. Every event listed here is
verified against the actual source as of this writing — not aspirational, not from a spec doc that
was never implemented.

Companion docs in the same repo (`docs/analytics/`): `event-taxonomy.md` (the original planning
doc this was built from), `dashboards-spec.md` (what to build once Mixpanel is live — the
retention and referral-funnel reports described there are now actually built), and
`store-privacy-disclosures.md` (Play Store / App Store privacy label updates this required).

---

## 1. System architecture

**Stack:** Mixpanel, called directly over its public HTTP tracking API (`POST
https://api.mixpanel.com/track`), not the official Mixpanel SDK.

**Why not the SDK:** the official `Mixpanel-swift` pod turned out to be unusable from
Kotlin/Native — its public API is pure Swift (generics, protocols, no `@objc` bridging), so
Kotlin's cinterop generates zero usable bindings from it, confirmed by inspecting the pod's
generated Objective-C compatibility header directly and finding no `SWIFT_CLASS` macros at all.
Rather than maintain two separate native SDK integrations (one per platform, breaking the
project's single-shared-implementation pattern), the whole thing is one `commonMain` Kotlin
implementation using Ktor (`HttpClient`, already a project dependency) to call Mixpanel's HTTP API
directly. This works identically on Android, iOS, and in principle any future KMP target.

**Where it lives:**
- Client: `shared/src/commonMain/kotlin/com/maxtime/app/analytics/AnalyticsTracker.kt` (the
  object that actually sends events) and `AnalyticsEvent.kt` (every event as a typed sealed
  class — no UI code ever calls `track()` with a bare string, so a renamed/missing property is a
  compile error, not a silently broken dashboard).
- Backend: `functions/src/analytics.ts` — a thin `trackBackendEvent(name, distinctId,
  properties)` helper using the official `mixpanel` npm package, called from six Cloud Functions
  files (five transactional + one Firestore trigger, see §5) after their respective writes commit.

**Fail-open, everywhere:** every single call site — client and backend — is wrapped so a Mixpanel
outage, a blank token, or a network failure can never affect a booking, a payment, or any other
core flow. Client: `AnalyticsTracker.track()` runs on its own fire-and-forget coroutine
(`CoroutineScope(SupervisorJob() + Dispatchers.Default)`) wrapped in `runCatching`. Backend:
`trackBackendEvent()` is a synchronous, non-throwing function — if the Mixpanel client isn't
initialized (blank token) it's a no-op; if the actual HTTP call throws, it's caught and logged as
a warning, never re-thrown. This was verified with real unit tests (`functions/src/analytics.test.ts`),
including a test that a thrown Mixpanel client error does not propagate to the caller.

**Backend events always fire after the transaction commits, never inside it.** Firestore
transaction callbacks can be silently retried on write contention, so nothing with a side effect
(an analytics call) can safely live inside one. Every backend event call site captures the data it
needs from inside the transaction into a value the transaction *returns*, then fires
`trackBackendEvent` after `await db.runTransaction(...)` resolves. (Note: an earlier draft tried
mutating an outer `let` variable from inside the transaction closure instead — this doesn't work,
because TypeScript's control-flow narrowing doesn't track reassignments made inside a nested
closure, so the pattern was changed to "transaction returns the outcome" everywhere.)

**`category_id` is denormalized onto `Booking` at creation time.** `Booking` originally had no
category field — only `InventorySlot` did. `createBookingHold` now copies
`slot.activityCategory` onto the booking document at the moment it's created (same pattern already
used for `serviceId`/`venueId`), so every backend event with a booking in scope
(`payment_succeeded`, `payment_failed`, `booking_confirmed`, `booking_expired`,
`check_in_completed`, `activity_attended`, `no_show_recorded`) can report a category breakdown
without an extra Firestore read.

**`app_version` reaches the 3 backend events that are actually triggered by a direct client
request.** Backend events auto-get `schema_version`/`platform`/`environment` from
`trackBackendEvent`, but not `app_version` — unlike client events, which get it automatically from
`AnalyticsTracker`. Threading it through turned out to only make sense for 3 of the 10 backend
events: `referral_reward_applied` (fired from `createBookingHold`), `account_deleted` (from
`deleteMyAccount`), and `user_reported` (from `reportUser`) — the client passes
`AnalyticsTracker.appVersion` (now a public read accessor) as part of each callable's request
payload, and the handler threads it into the event properties. The other 7 backend events
deliberately don't get one: `payment_succeeded`/`payment_failed`/`booking_confirmed` are triggered
by Razorpay's webhook (not a request from our app at all), `booking_expired`/
`referral_reward_reverted` by a scheduled function (no request, no client), and
`check_in_completed`/`activity_attended`/`no_show_recorded` by the separate partner portal (a
different, unversioned web client) — "app version" isn't a coherent concept for any of those.

---

## 2. Identity model

- **`distinct_id`** is the join key across everything — client events, backend events, and (once
  looked up) the actual Firestore user record.
- **Before sign-in:** an anonymous ID, generated locally and persisted via `LocalSettings` under
  the key `analytics_distinct_id`.
- **On sign-in:** `AnalyticsTracker.identify(firebaseUid)` is called, which both sets the local
  distinct_id to the real Firebase Auth UID going forward *and* fires Mixpanel's `$identify` event
  to merge the pre-sign-in anonymous history into the same user profile. Called from
  `OnboardingScreens.kt`'s `sendOtp()` after real phone verification succeeds —
  `CrashReporter.identify(uid)` (§9) fires alongside it, so crash reports carry the same user
  identity as Mixpanel events.
- **On sign-out or account deletion:** `AnalyticsTracker.reset()` (and `CrashReporter.reset()`) is
  called, from `MyProfileScreen.kt`'s `signOutTapped()` and `confirmDeleteAccount()`.
- **Stale-session cold start also resets identity — a real gap that was found and fixed.**
  `App.kt` signs out a stale session (a Firebase session left over from before a fresh
  install/reinstall — see the Keychain-survives-uninstall comment in that file) on cold start. The
  original code fired `AppOpened` *before* checking for a stale session, meaning that event — and
  anything else in that cold start — could carry the *previous* account's `distinct_id`.
  Fixed by reordering: `signOut()` → `AnalyticsTracker.reset()`/`CrashReporter.reset()` → *then*
  `AppOpened` fires, so it's always correctly anonymous.
- **Once identified, `distinct_id` literally IS the Firebase Auth UID** — meaning: to find a real
  user behind any Mixpanel event, take the `distinct_id`, open Firebase Console → Firestore →
  `users/{that id}`. That document has their actual profile: `name`, `phoneNumber`,
  `dateOfBirth`, `photoUrl`, `interests`, `onboardingComplete`. No email field exists anywhere —
  this app only does phone-number auth.

---

## 3. Environment model

**One Mixpanel project, not three.** All three real environments — production (`maxtime-app`
Firebase project), staging (`maxtime-staging`), beta (`maxtime-beta`) — send to the **same**
Mixpanel project token, and every event carries an `environment` property (`"production"` /
`"staging"` / `"beta"`) to distinguish them. To look at only one environment's data in Mixpanel:
add a filter where `environment is <value>` (Insights/Live View support this directly; save it as
a view so you don't have to rebuild the filter every time).

**Where the token lives:**
- Android: `local.properties` (gitignored) → `MIXPANEL_TOKEN=...` → injected as
  `BuildConfig.MIXPANEL_TOKEN` for all three flavors.
- iOS: hardcoded directly in `iosApp/iosApp/AppDelegate.swift`'s
  `AnalyticsTracker.shared.initialize(...)` call — intentional, not a shortcut: a Mixpanel project
  token is write-only (can only ingest events) and ships inside every client binary regardless of
  where it's stored.
- Backend: `functions/.env.maxtime-app`, `functions/.env.maxtime-staging`,
  `functions/.env.maxtime-beta` (gitignored via `functions/.env*`), one per Firebase project, all
  currently holding the same token value.

**A real token now exists and events are confirmed flowing** — verified directly in Mixpanel's own
Events/Live View UI, with the correct `environment`, `distinct_id`, and event-specific properties.

---

## 4. Client events — complete list (26 events)

Source of truth: `shared/src/commonMain/kotlin/com/maxtime/app/analytics/AnalyticsEvent.kt`.

| Event name | Properties | Fires from | Notes |
|---|---|---|---|
| `app_opened` | `entry_point` | `App.kt`, cold start | |
| `screen_viewed` | `screen_name`, `previous_screen_name` | `Router.kt` — `navigate()`/`goBack()`/`reset()` | Fires on every screen transition in the entire app from one choke point. Splash never fires this (Router's constructor default, not a `navigate()` call). |
| `guest_mode_entered` | — | Onboarding, after anonymous sign-in succeeds | |
| `onboarding_started` | — | Onboarding's `AgeGateScreen` | |
| `onboarding_completed` | `steps_completed` | Onboarding's `InterestsScreen` (final step) | |
| `sign_up_started` | `method: "phone_otp"` | Onboarding's `AuthScreen`, on compose | |
| `login_completed` | `method`, `account_created_from_guest` | After real phone OTP verification succeeds | `account_created_from_guest` is defined but never populated — guest and phone sign-in are independent Firebase flows with no linking mechanism. |
| `account_deletion_requested` | — | `MyProfileScreen.kt`, delete-account confirm tap | |
| `home_viewed` | `entry_point` | `HomeScreen.kt` | |
| `home_banner_tapped` | `activity_id`, `position` | `HomeScreen.kt`'s "Popular near you" carousel tap | |
| `activity_feed_viewed` | `category_filter`, `area_id`, `result_count` | `DiscoverScreen.kt` | |
| `activity_detail_viewed` | `activity_id`, `category_id`, `price_minor`, `currency`, `availability_status` | `ActivityDetailScreen.kt` | |
| `activity_search_no_results` | `category_id`, `area_id` | Same screen, when no sessions are found | |
| `groups_viewed` | `activity_id`, `result_count` | `FindResultsScreen.kt`, once the groups list loads | `result_count` is the real group count, not session count. |
| `group_detail_viewed` | `group_id`, `activity_id` | `GroupDetailScreen.kt` | Fires on every view regardless of how the user got there. |
| `group_created` | `group_id`, `activity_id` | `CreateGroupScreen.kt`'s "Create activity" tap | Not fired from `GroupDetailScreen`'s fallback `getOrCreateMyGroup()` call. Has a server-side confirmation mirror — see §5. |
| `group_joined` | `group_id`, `activity_id` | `GroupDetailScreen.kt`'s "Join Group" button | Not fired from `FindResultsScreen`'s "Start it" tap on an unclaimed session (demo-seeded placeholder members, not a real join). Has a server-side confirmation mirror — see §5. |
| `chat_opened` | `group_id`, `activity_id` | `GroupChatScreen.kt`, on screen load | |
| `chat_message_sent` | `group_id`, `activity_id`, `message_length` | `GroupChatScreen.kt`'s `send()` | Carries character count only, never the message text. |
| `user_blocked` | `group_id` | `GroupChatScreen.kt`, block-confirm | |
| `refer_and_earn_viewed` | — | `ReferAndEarnScreen.kt`, on load | |
| `referral_code_shared` | `channel` (`whatsapp`/`snapchat`/`instagram`/`instagram_story`/`instagram_direct`/`share_sheet`) | `ReferAndEarnScreen.kt`'s share functions | Never carries the actual referral code value. |
| `referral_code_copied` | — | Both of the screen's two copy-code tap targets | |
| `referral_code_redeemed` | `outcome` (`success`/`rejected`), `already_attributed` | Onboarding's `ReferralScreen.submit()` | The referee side of the relationship the two events above cover from the referrer side. |
| `booking_started` | `activity_id`, `category_id`, `activity_session_id`, `participation_mode`, `quantity`, `amount_minor`, `currency` | `BookingReviewScreen.kt`, on load | |
| `payment_started` | `booking_id`, `amount_minor`, `currency`, `provider: "razorpay"` | Same screen, right before Razorpay checkout launches | |

---

## 5. Backend events — complete list (12 events)

Source of truth: every `trackBackendEvent(...)` call site in `functions/src/*.ts`. All fire with
`platform: "backend"`, `schema_version: 1`, and `environment` (derived from
`process.env.GCLOUD_PROJECT`) automatically added by the `trackBackendEvent` helper — not repeated
per row.

| Event name | Properties | Fires from | Notes |
|---|---|---|---|
| `payment_succeeded` | `booking_id`, `category_id`, `amount_minor`, `currency`, `provider` | `payments.ts`'s `paymentWebhook`, on a `payment.captured` webhook that transitions a booking | |
| `booking_confirmed` | `booking_id`, `category_id`, `service_id`, `venue_id`, `amount_minor`, `currency` | Same function, same successful-payment branch | |
| `payment_failed` | `booking_id`, `category_id`, `amount_minor`, `currency`, `provider` | Same function, `payment.failed` branch | |
| `referral_reward_reverted` | `booking_id`, `reason` (`PAYMENT_FAILED`/`EXPIRED`) | `payments.ts` and `booking.ts`'s `expireStaleHolds` | Only fires if a reward was applied and had to be reverted. |
| `referral_reward_applied` | `booking_id`, `reward_id`, `discount_amount_minor`, `app_version` | `booking.ts`'s `createBookingHold` | |
| `booking_expired` | `booking_id`, `category_id`, `amount_minor`, `currency` | `booking.ts`'s `expireStaleHolds` (scheduled, every 5 min) | |
| `check_in_completed` | `booking_id`, `category_id`, `service_id`, `venue_id` | `attendance.ts`'s `markAttendance`, status `CHECKED_IN` | Same call site for all three attendance events. |
| `activity_attended` | same as above | `markAttendance`, status `COMPLETED` | |
| `no_show_recorded` | same as above | `markAttendance`, status `NO_SHOW` | |
| `account_deleted` | `app_version` | `account.ts`'s `deleteMyAccount`, after both the Firestore doc and Auth user are gone | The last moment the deleted uid is a valid `distinct_id`. |
| `user_reported` | `group_id`, `reported_user_id`, `reason`, `app_version` | `moderation.ts`'s `reportUser` | `messageText` deliberately excluded. |
| `group_created_confirmed` / `group_joined_confirmed` | `group_id`, `activity_id` | `groups.ts`'s `onGroupWrite` — a Firestore `onDocumentWritten` trigger on `groups/{groupId}` | **New.** Server-side, independent confirmation that a `group_created`/`group_joined` client event corresponded to a write that actually landed. Distinct_id is the relevant uid from `Group.memberUids`: `memberUids[0]` (the creator) on a brand-new doc, or whichever uid is newly present in `memberUids` on an update. Additive, not a replacement — the richer, faster client events stay primary. Not fired for the demo-seeded `joinExistingGroup()` path when no real uid was tagged. |

**Idempotency, verified by test:** `payments.ts`'s webhook handler is naturally idempotent — a
replayed `payment.captured` webhook hits an "already-processed" branch and fires zero additional
analytics calls. Tested in `functions/src/payments.test.ts` against real Firestore/Auth emulators:
the same webhook payload sent twice asserts exactly 2 calls on the first send, zero on the replay.

---

## 6. Security: group membership is now UID-verified, not name-based

A Phase 0 audit found `groups`/`groups/{groupId}/messages` were the only two collections in the
whole ruleset with a blanket `request.auth != null` write rule and no ownership check — any
signed-in user could edit *any* group's price/venue/capacity/status, or post into *any* group's
chat. The real fix required more than a rules change: **group membership was stored as plain
display-name strings**, with no Firebase UID anywhere on the document to check against — a rules
check can verify `request.auth.uid`, but there was nothing to compare it to.

Fixed by adding `Group.memberUids: List<String>` (real Firebase UIDs, populated at
creation/join time from `Firebase.auth.currentUser?.uid`) to the data model
(`Models.kt`, `FirestoreGroupRepository.kt`, threaded through `AppState.kt`'s
`getOrCreateMyGroup`/`joinGroup`/`joinExistingGroup`), then writing `firestore.rules` against it:
reads stay open (needed for discovery), creates stay open (a fresh doc can't tamper with existing
data), but **updates** require either already being a verified member, or the write being exactly
"add myself" with nothing else changed in the same request — closing the "disguise a price
tamper as a join" loophole specifically. Messages require verified membership to post and are
immutable (no edit/delete from any client). Covered by 9 new tests
(`functions/src/groups-rules.test.ts`) against the real Firestore emulator. **Deployed** to all
three Firebase projects' Firestore rules.

This also fixed a live correctness bug, not just a security gap: the app's own "am I already a
member of this group" check compared display names, which breaks for two users sharing a name or
anyone who changes their name mid-group.

---

## 7. What the combination actually enables

**Per-user screen-by-screen journey tracing, including drop-off point.** `screen_viewed` fires on
every navigation from one central place — filter Mixpanel to one `distinct_id`, sort by time, and
the last event before a gap is where they stopped. No mobile SDK gets a true callback on a
force-quit or crash, so this remains the honest proxy even with Crashlytics in place (§9) — crash
reporting tells you *that* the app died, not necessarily which in-app screen it was on at that
exact instant unless a crash happened to occur mid-navigation.

**Full post-tap attribution from a Home banner to a completed booking.** `home_banner_tapped` →
the `screen_viewed` it triggers → every subsequent event for that `distinct_id`
(`groups_viewed`, `activity_detail_viewed`, `booking_started`, `payment_started`, and the
backend's `payment_succeeded`/`booking_confirmed`) is one traceable chain, or a Mixpanel Flows
report seeded on the banner tap.

**"Most-clicked category" / "category with most bookings/attendance."** Answerable via a
breakdown by `category_id`, now present on `activity_detail_viewed`, `booking_started`, and every
booking-scoped backend event through `activity_attended`/`no_show_recorded`.

**Both sides of the referral loop**, now with a built dashboard (§8) — `referral_code_shared`/
`referral_code_copied` (referrer) and `referral_code_redeemed` (referee) join to the backend's
`referral_reward_applied`/`referral_reward_reverted` for the reward-state side.

**Retention — first→second completed activity.** A built Mixpanel retention report (§8) anchored
on `login_completed`, returning on `activity_attended`.

---

## 8. Dashboards — built, live in Mixpanel

Five reports are saved to a **"Core Product Health"** board in the MaxTime Mixpanel project — the
plan's full 5-saved-report ceiling:

- **"Retention: Login → Activity Attended (30/60d)"** — Mixpanel Retention report, anchor event
  `login_completed`, return event `activity_attended`, 3-month range. Shows "no data" honestly
  right now — `activity_attended` has zero real occurrences since the backend functions were only
  just (re)deployed; this will populate as real attendance happens.
- **"Referral Funnel: Share → Redeem → Reward → Attendance"** — 4-step Mixpanel Funnel
  (`referral_code_shared` → `referral_code_redeemed` → `referral_reward_applied` →
  `activity_attended`), 90-day conversion window (matches the referral reward's real 90-day
  validity period, not Mixpanel's 7-day default).
- **"Payment Health: Succeeded/Failed, Confirmed/Expired"** — 4-metric Insights time series
  (`payment_succeeded`, `payment_failed`, `booking_confirmed`, `booking_expired`), 3-month range.
- **"Attendance Funnel: Confirmed to Check-in to Attended"** — 3-step Funnel (`booking_confirmed`
  → `check_in_completed` → `activity_attended`), 3-month range. No `category_id` breakdown yet —
  Mixpanel won't offer a property for breakdown until a real event carries it.
- **"Trust and Safety: Reports vs Blocks"** — 2-metric Insights time series (`user_reported`,
  `user_blocked`), 3-month range. No `reason` breakdown or spike alert configured yet.

All four newly-added reports (everything but Retention/Referral) currently show "no data" —
`payment_succeeded`, `payment_failed`, `booking_confirmed`, `booking_expired`,
`check_in_completed`, and `activity_attended` all still have zero real occurrences in Mixpanel as
of this pass, so each was entered as a literal quoted event name in the report builder rather than
picked from the event list. They'll populate once real traffic flows.

**Not saved as a report — plan ceiling, not an oversight:** account-deletion completion rate
(`account_deletion_requested` → `account_deleted`) from `dashboards-spec.md` §8 is fully
instrumented in code and can be built ad hoc in Funnels any time; it just doesn't have a saved
slot. Same for the onboarding (§1) and discovery→payment (§2) funnels from that spec — not built
at all yet, saved or otherwise.

---

## 9. Crash reporting — Firebase Crashlytics

**Added.** `dev.gitlive:firebase-crashlytics` (pinned to `2.6.0`, matching the rest of the
`dev.gitlive` Firebase family already in this project) plus the real native SDKs on both
platforms — `FirebaseCrashlytics` CocoaPod on iOS, the `com.google.firebase.crashlytics` Gradle
plugin (`3.0.8`) on Android.

**Where it lives:** `shared/src/commonMain/kotlin/com/maxtime/app/analytics/CrashReporter.kt` —
same "one place UI code talks to the vendor" pattern as `AnalyticsTracker`, but for a genuinely
different reason: Crashlytics' actual crash *capture* (an uncaught-exception/signal handler
installed at process start, NDK/dSYM symbolication) needs real native SDK hooks that can't be
reimplemented over a bare HTTP API the way Mixpanel's calls were — there's no cinterop workaround
here the way there was for Mixpanel-swift.

- `CrashReporter.identify(userId)` / `.reset()` — called from the exact same places as
  `AnalyticsTracker.identify()`/`.reset()` (§2), so crash reports carry the same user identity as
  Mixpanel events.
- `CrashReporter.recordException(throwable, context)` — for a caught exception worth knowing
  about; `context` is logged as a breadcrumb rather than passed as Crashlytics' own `customKeys`
  parameter, because the pinned `2.6.0` version's `recordException` only has the single-argument
  overload (the two-argument version is newer than what's pinned here — confirmed by diffing the
  exact `v2.6.0` tagged source against what's on `master`, after an initial build broke on the
  assumption they matched).
- Fatal (uncaught) crashes are captured automatically the moment the dependency is present —
  nothing to call for that.

**Verified on real devices, both platforms** — not just compiled: `./gradlew
:androidApp:assembleBetaDebug` succeeded; the iOS build initially **failed to link**
(`framework 'FirebaseCrashlytics' not found`) because `shared.podspec` is a generated file that
`pod install` doesn't regenerate on its own — fixed by running `./gradlew :shared:podspec`
explicitly before `pod install`, confirmed by diffing the podspec's dependency list before/after.
Rebuilt clean, installed via `xcrun devicectl`, and launched successfully on a physical iPhone.

---

## 10. Explicitly NOT instrumented — and why

Real, known gaps — each blocked by something that would need to be built first, not just an
analytics call added:

- **`booking_cancelled`** — no cancellation Cloud Function exists anywhere (only unused enum
  values `CANCELLED_BY_USER`/`CANCELLED_BY_PARTNER` in `types.ts`). Can't instrument a feature
  that isn't built.
- **`sign_up_completed` as distinct from `login_completed`** — no separate post-signup milestone
  exists in the current onboarding flow to hang a distinct event on.
- **Sandbox/test-mode payment verification** — Razorpay is on **live** keys for production, so
  there's no safe way to generate a real end-to-end `payment_succeeded` without real money moving.
  Transaction-level correctness is covered by the automated webhook-replay test instead.
- **A real `applyReferralRewardId` on the client's `createBookingHold` request** — found while
  wiring `app_version` through the same request: the backend (`booking.ts`) reads
  `request.data?.applyReferralRewardId`, but the client's `CreateBookingHoldRequest`
  (`BookingFunctions.kt`) has no such field at all. This means a referral reward can likely never
  actually be applied at checkout today, regardless of this analytics work. Flagged, not fixed —
  out of scope for an analytics pass, but a real product bug worth a separate look.
- **Crash-to-screen correlation** — Crashlytics (§9) tells you a crash happened and roughly when;
  it doesn't automatically tell you which Compose screen was on-screen at that instant unless that
  context is explicitly logged via `CrashReporter.log()` before the crash. Not wired into every
  screen transition — would mean threading a breadcrumb call into the same `Router.kt` choke point
  `screen_viewed` already uses, which wasn't done here.
- **`search_submitted` / `filter_applied` / `activity_saved` / `activity_shared` / support-issue
  events** — checked directly against the codebase (2026-10-19), not assumed: there is no search
  text input anywhere (`DiscoverScreen`/`FindWhatScreen` are browse/pick flows, no `SearchBar` or
  search query state exists), no bookmark/save/favorite feature (no `isSaved`/`isFavorite` field on
  any activity model, no saved-activities repository), and no user-facing booking-issue/support
  flow (only the existing `user_reported` moderation flow, already instrumented). Category
  filtering does exist (`InterestChip` in `DiscoverScreen`), but it's already captured as the
  `category_filter` property on `activity_feed_viewed` — no separate `filter_applied` event is
  needed. None of these are being instrumented because none of the underlying features exist;
  revisit if/when they're built.

---

## 11. Verification performed (not just written, actually checked)

- `functions/src/analytics.test.ts` — 4 unit tests on `trackBackendEvent`'s fail-open contract.
- `functions/src/payments.test.ts` — webhook idempotency/replay test against real emulators.
- `functions/src/groups-rules.test.ts` — 9 tests on the UID-based group membership rules (§6)
  against the real Firestore emulator.
- Full suite: **36/36 passing**, `npx tsc --noEmit` clean, re-confirmed after every backend change
  in this pass (the `category_id`/`app_version` additions, the `onGroupWrite` trigger).
- `./gradlew :shared:compileCommonMainKotlinMetadata` clean after every client change.
- `./gradlew :androidApp:assembleBetaDebug` — real Android build, confirms the Crashlytics Gradle
  plugin and `google-services.json` registration are correctly wired, not just that Kotlin
  compiles.
- Real iOS device verification: built via `xcodebuild`, installed via `xcrun devicectl`, launched
  on a physical iPhone on the **Beta** flavor (points at the separate `maxtime-beta` Firebase
  project — test taps never touch real user data or live Razorpay charges). Confirmed live client
  events landing in Mixpanel's Live View with correct `environment`/`distinct_id`/properties.
- **Firestore rules deployed** to all three environments (beta → staging → production).
- **Cloud Functions deployed** to staging and production, including the `onGroupWrite` trigger
  (`group_created_confirmed`/`group_joined_confirmed` are live) and `app_version` threading on the
  3 applicable events. Beta skipped — missing Razorpay secrets, a pre-existing gap, not something
  this work introduced.
