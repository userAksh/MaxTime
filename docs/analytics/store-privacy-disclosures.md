# Store privacy disclosure updates — analytics addition

Adding Mixpanel analytics is a new third-party data collection, which means the Play Console
**Data safety** section and the App Store Connect **App Privacy** ("nutrition label") section both
need an update — both are already live from the original submission and currently don't mention
analytics at all. This doc is the draft answers; it does not submit anything itself. I haven't
touched either console — let me know if you want me to walk through re-submitting these with you
the same way we did the original Play Store form, screen by screen.

**Update (2026-10-19): Firebase Crashlytics shipped after this doc's first pass (see
[`analytics-system-handover.md`](./analytics-system-handover.md) §9) — a second, separate data
collector, Google/Firebase rather than Mixpanel. Its disclosure additions are in their own section
near the bottom of this doc; everything above this note still only covers Mixpanel.**

Also update: the website's [privacy policy](../../website/src/app/privacy/page.tsx) already got
this addition (Mixpanel listed in Section 4, usage/analytics data listed in Section 2) — done as
part of this same pass.

## Google Play Console → App content → Data safety

Add a new data type under **App activity**:

| Field | Answer |
|---|---|
| Data type | App interactions |
| Collected? | Yes |
| Shared? | Yes — with Mixpanel (analytics service provider) |
| Processed ephemerally? | No |
| Required or optional? | Required — there's no analytics opt-out in the app today (Phase 1 decision: fire-by-default, no consent gate) |
| Purpose | Analytics |

Also add, under **Device or other IDs**:

| Field | Answer |
|---|---|
| Data type | Device or other IDs |
| Collected? | Yes |
| Shared? | Yes — with Mixpanel |
| Required or optional? | Required |
| Purpose | Analytics |

Reasoning for the second one: before sign-in, events are tagged with an anonymous device-generated
ID (`AnalyticsTracker`'s `distinct_id`, stored in `LocalSettings`) rather than an account
identifier — that's exactly what this category covers. After sign-in, it's merged to the Firebase
UID via `identify()`, which is why "App interactions" above is also marked as shared with a
service provider rather than kept on-device.

Leave every other existing section (location, financial info, messages, personal info) as they
already are — this pass only adds the two rows above.

## Apple App Store Connect → App Privacy

Add a new entry under **Usage Data**:

| Field | Answer |
|---|---|
| Data type | Product Interaction |
| Used for | Analytics |
| Linked to user's identity? | Yes (Firebase UID, after sign-in) |
| Used for tracking? | No — this is first-party product analytics, not used to track users across other companies' apps/websites for advertising. (If this answer were "Yes," Apple would require an ATT prompt — it should stay "No" unless that scope changes.) |

And under **Identifiers**, if not already declared for something else:

| Field | Answer |
|---|---|
| Data type | User ID |
| Used for | Analytics |
| Linked to user's identity? | Yes |
| Used for tracking? | No |

Leave the existing declarations (location, payment info, contact info, user content) untouched —
this only adds the usage-data/analytics rows.

## Why both say "linked to identity: yes"

Because `AnalyticsTracker.identify(firebaseUid)` is called on every successful sign-in
(`OnboardingScreens.kt`), and `reset()` is called on sign-out and account deletion
(`MyProfileScreen.kt`) — so for a signed-in user, analytics events ARE tied back to their account,
not anonymous. Declaring otherwise would be inaccurate and is the kind of mismatch Apple's review
and Play's policy team actively check for (comparing the declared data-safety answers against
what the app's actual network traffic does).

## Firebase Crashlytics addition (2026-10-19)

Crashlytics is a second, separate collector from Mixpanel — same "linked to identity" reasoning
applies (`CrashReporter.identify(uid)` is called at the same three call sites as
`AnalyticsTracker.identify()`: App.kt's stale-session branch, post-OTP in `OnboardingScreens.kt`,
and `reset()` on sign-out/delete in `MyProfileScreen.kt`), but it collects different data: crash
stack traces and device diagnostics, not product-usage events.

### Google Play Console → App content → Data safety

Add a new data type under **App activity**:

| Field | Answer |
|---|---|
| Data type | Crash logs |
| Collected? | Yes |
| Shared? | Yes — with Google/Firebase (Crashlytics) |
| Processed ephemerally? | No |
| Required or optional? | Required — no opt-out exists; fatal/uncaught crash capture is automatic the moment the SDK is present, same fire-by-default posture as Mixpanel |
| Purpose | App functionality (crash diagnostics) |

And under **Device or other IDs**, if not already sufficiently covered by the Mixpanel addition
above:

| Field | Answer |
|---|---|
| Data type | Device or other IDs |
| Collected? | Yes |
| Shared? | Yes — with Google/Firebase |
| Required or optional? | Required |
| Purpose | Analytics (crash diagnostics) |

### Apple App Store Connect → App Privacy

Add a new entry under **Diagnostics**:

| Field | Answer |
|---|---|
| Data type | Crash Data |
| Used for | App Functionality |
| Linked to user's identity? | Yes (Firebase UID, after sign-in — via `setUserId`) |
| Used for tracking? | No — crash diagnostics only, not used to track users across other companies' apps/websites for advertising |

| Field | Answer |
|---|---|
| Data type | Performance Data |
| Used for | App Functionality |
| Linked to user's identity? | Yes |
| Used for tracking? | No |

Leave every other existing declaration (location, payment info, contact info, user content, and
the Mixpanel rows added above) untouched — this only adds the crash/diagnostics rows.
