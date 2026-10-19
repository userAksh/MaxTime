# Mixpanel dashboards & funnels — build list

Status (2026-10-19): **#3 (payment health), #4 (referral program health), #5 (trust & safety),
#6 (attendance & no-show), and #7 (retention) are built**, saved to a **"Core Product Health"**
board — see each section below for the exact report name. This assumes the event names/properties
in [`event-taxonomy.md`](./event-taxonomy.md), which are fully instrumented in code — see
[`analytics-system-handover.md`](./analytics-system-handover.md) for the authoritative, current
event list and what's actually deployed where.

**Plan ceiling hit: #8 (account deletion) and #1/#2 (onboarding and discovery→payment funnels)
are NOT saved as reports.** The current Mixpanel plan caps saved reports at 5 per user per
project — the 5 slots above are full. Account deletion's `account_deletion_requested` →
`account_deleted` completion rate (§8 below) is still fully instrumented in code and can be built
ad hoc in the Funnels UI any time without saving, or saved once a slot frees up or the plan is
upgraded. Not silently dropped — flagged here so it isn't lost.

As of this pass, every real event checked in the picker (`payment_succeeded`, `payment_failed`,
`booking_confirmed`, `booking_expired`, `check_in_completed`, `activity_attended`) still has zero
real occurrences — all were entered as literal-string event names, same as the earlier-built
referral/retention reports. They'll populate once real bookings/payments/attendance flow through
production. `category_id` breakdown on the attendance funnel (§6) was also skipped for the same
reason — Mixpanel can't offer a property for breakdown until at least one real event carries it.

## Project setup (do this first, once you create the account)

- **One Mixpanel project, not three.** Use a single project across production/staging/beta and
  rely on the `environment` property every event already carries (`production` / `staging` /
  `beta`, derived from the Firebase project / build flavor — see event-taxonomy.md §3). This means
  the *same* token goes into all three Android flavors' `local.properties` and the iOS init call,
  and the *same* `MIXPANEL_TOKEN` value is set for all three Cloud Functions projects. Cheaper than
  3 projects, and lets you compare staging vs. production side by side instead of switching
  projects. Every dashboard below should default its environment filter to `production`.
- Set `distinct_id` as the canonical user identifier — already handled by the app
  (`AnalyticsTracker.identify()`/`.reset()`), no Mixpanel-side config needed.
- Mixpanel Lexicon: register the event names and property types from event-taxonomy.md §5 so
  typos in ad-hoc exploration get flagged. Not required to build the boards below, just keeps them
  from silently breaking later.

## 1. Onboarding funnel

**Question it answers:** where do new users drop off between opening the app and actually signing
up?

Funnel steps: `app_opened` → `onboarding_started` → `onboarding_completed` → `sign_up_started` →
`login_completed`. Breakdown by `platform` (iOS vs Android — the two have different OTP delivery
paths, so a platform-specific drop is a real signal, not noise). Add `guest_mode_entered` as a
branch/alternate step, since guests skip straight past `sign_up_started`.

## 2. Discovery → booking → payment funnel

**Question it answers:** the core activation funnel — of everyone who opens the app, how many
actually pay for something?

Funnel steps: `home_viewed` → `activity_feed_viewed` → `activity_detail_viewed` →
`booking_started` → `payment_started` → `payment_succeeded`. This is the single most important
funnel in the product — put it on the default dashboard.

Companion report, not a funnel: **search-no-results rate** — `activity_search_no_results` count ÷
`activity_feed_viewed` count, by `area_id`. A rising rate in a specific area means inventory supply
is behind demand there.

## 3. Payment health — ✅ built

**"Payment Health: Succeeded/Failed, Confirmed/Expired"**, saved to the Core Product Health
board. 4-metric Mixpanel Insights time series (`payment_succeeded`, `payment_failed`,
`booking_confirmed`, `booking_expired`), last 3 months. Simplified from the original spec below —
one combined time series instead of a separate failure-rate line and a separate ratio chart —
since both answer the same "is checkout reliable" question and Mixpanel's own chart UI lets you
toggle any subset of the 4 lines on/off per view.

**Question it answers:** is checkout reliable, and is it getting worse?

- Time series: `payment_succeeded` vs. `payment_failed`, daily, as a failure-rate line (not raw
  counts — raw counts just track volume).
- `booking_confirmed` vs. `booking_expired` ratio — an expired hold means someone started
  checkout and never finished; a rising ratio points at a checkout UX or payment-friction problem,
  not a payment-gateway outage (that would show up in #1 instead).
- Breakdown both by `currency` (should be 100% INR right now — a non-INR event is a data bug, not
  a new market) and once `payment_method_category` is actually captured (it isn't yet — see
  event-taxonomy.md's note that this field doesn't exist in the current webhook payload capture).

## 4. Referral program health — ✅ built

**"Referral Funnel: Share → Redeem → Reward → Attendance"**, saved to the Core Product Health
board. 4-step Mixpanel Funnel (`referral_code_shared` → `referral_code_redeemed` →
`referral_reward_applied` → `activity_attended`), 90-day conversion window (matches the reward's
real 90-day validity, not Mixpanel's 7-day default). Extended past the original spec below to
include the referee's redemption and real attendance, not just the reward-application step.

**Question it answers:** is the referral discount mechanism being abused, or failing silently?

- `referral_reward_applied` → `booking_confirmed` conversion (does an applied reward actually turn
  into a paid booking, or get abandoned?).
- **Reversion rate**: `referral_reward_reverted` ÷ `referral_reward_applied`, broken down by
  `reason` (`PAYMENT_FAILED` vs `EXPIRED` vs `NO_SHOW`). A reversion rate that's high and
  concentrated on `NO_SHOW` for a specific `referrerUserId`/`beneficiaryUserId` pattern is the
  actual fraud signal this report exists to surface — referral abuse usually looks like "apply
  reward, book, no-show, repeat," not random noise.

## 5. Trust & safety — ✅ built

**"Trust and Safety: Reports vs Blocks"**, saved to the Core Product Health board. 2-metric
Mixpanel Insights time series (`user_reported`, `user_blocked`), last 3 months. Breakdown by
`reason` not added yet — see the plan-ceiling note at the top of this doc; also, Mixpanel can't
offer `reason` as a breakdown property until at least one real `user_reported` event has fired
with it. No alert configured on day-over-day spikes yet (needs a decision on who gets paged).

**Question it answers:** is abuse increasing, and where?

- `user_reported` count over time, by `reason` (spam / harassment / inappropriate_content /
  other). Set a Mixpanel alert on a day-over-day spike — this is the one dashboard here worth
  actually paging someone over, not just looking at weekly.
- `user_blocked` count over time — compare trend direction against `user_reported`; a divergence
  (reports up, blocks flat) suggests a reporting-flow problem, not an abuse problem.

## 6. Attendance & no-show — ✅ built

**"Attendance Funnel: Confirmed to Check-in to Attended"**, saved to the Core Product Health
board. 3-step Mixpanel Funnel (`booking_confirmed` → `check_in_completed` → `activity_attended`),
last 3 months. Breakdown by `category_id`/`service_id`/`venue_id` not added yet — see the
plan-ceiling note at the top of this doc for why.

**Question it answers:** which activities/venues have a quality or reliability problem?

Funnel: `booking_confirmed` → `check_in_completed` → `activity_attended` (vs. the branch to
`no_show_recorded`). Break down by `service_id`/`venue_id`, or now also directly by `category_id`
— `Booking` was later denormalized with `activityCategory` at creation time, so this no longer
needs the service/venue workaround the original version of this doc assumed. A venue with a
persistently high no-show rate is either mis-listed (wrong time/location) or a trust problem worth
a partner conversation.

## 7. Retention — ✅ built

**"Retention: Login → Activity Attended (30/60d)"**, saved to the Core Product Health board.
Anchor event `login_completed`, return event `activity_attended` (not `booking_confirmed` as
originally planned below — attendance is the stronger "did this actually work for them" signal
than a second payment alone), 3-month range. Currently shows no data — `activity_attended` has
zero real occurrences yet since the backend functions were only just (re)deployed.

**Question it answers:** do people come back and book again?

Cohort retention report, anchored on first `login_completed` (or first `guest_mode_entered` for
guests), measuring return via subsequent `booking_confirmed` events at D7/D30. This is the
standard Mixpanel retention report type — no custom build needed beyond picking the anchor/return
events.

## 8. Account deletion

**Question it answers:** is deletion itself broken, and is churn increasing?

- `account_deletion_requested` → `account_deleted` completion rate — should be ~100%; anything
  lower means the delete flow is silently failing for some users (see
  `functions/src/account.ts`'s `deleteMyAccount`).
- `account_deletion_requested` volume as a trend line — this is your churn signal until a more
  formal churn definition exists.

## What this spec deliberately leaves out

Per event-taxonomy.md's own "not instrumented" section: nothing here references
`booking_cancelled`, `group_created`, `group_joined`, `chat_message_sent`, or
`sign_up_completed` (as distinct from `login_completed`) — none of these are actually emitted yet
(see the Phase 3b report: no cancellation Cloud Function exists, group/chat writes are
client-only with no server hook, and there's no separate post-signup completion event). Building a
dashboard panel for an event that never fires would silently show "0" forever and look like a
product problem instead of a missing-instrumentation one.
