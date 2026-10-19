package com.maxtime.app.analytics

/**
 * Typed client-side events, per docs/analytics/event-taxonomy.md §5. Only events whose `Source`
 * in that table is `Client` live here — `Backend`/`Backend-preferred` events are emitted from the
 * Cloud Functions codebase (TypeScript), not this KMP facade, since they fire inside the same
 * Firestore transaction that performs the durable state change.
 *
 * No UI code should ever call [AnalyticsTracker.track] with a bare string — every event is a
 * sealed subtype here, so a missing/renamed property is a compile error, not a silent gap in a
 * dashboard discovered weeks later.
 */
sealed class AnalyticsEvent(val name: String) {
    abstract val properties: Map<String, Any?>

    data class AppOpened(val entryPoint: String) : AnalyticsEvent("app_opened") {
        override val properties = mapOf("entry_point" to entryPoint)
    }

    /** Fired from a single choke point — Router.navigate()/goBack()/reset() (see Router.kt) —
     *  rather than from each screen individually, so coverage is automatic and can't drift out of
     *  date as screens are added. This is the raw navigation trace: every screen transition, in
     *  order, for every user. The business-specific *_viewed events (home_viewed,
     *  activity_detail_viewed, etc.) stay separate — they carry properties (activity_id, price,
     *  result_count...) this generic event deliberately doesn't try to reconstruct. Use this one
     *  for "what path did this user take" / drop-off analysis (Mixpanel's Flows report, or just
     *  sorting one user's raw events by time — their last screen_viewed before a gap is where they
     *  left); use the specific ones for funnels that need real business dimensions. The app's very
     *  first screen (Splash, set as Router's constructor default) never fires this — app_opened
     *  already marks cold start, and Splash itself is a momentary transition screen, not a
     *  destination. */
    data class ScreenViewed(val screenName: String, val previousScreenName: String?) : AnalyticsEvent("screen_viewed") {
        override val properties = mapOf("screen_name" to screenName, "previous_screen_name" to previousScreenName)
    }

    /** MaxTime-specific — not in the source architecture doc's taxonomy; see Phase 1 additions. */
    data object GuestModeEntered : AnalyticsEvent("guest_mode_entered") {
        override val properties = emptyMap<String, Any?>()
    }

    data object OnboardingStarted : AnalyticsEvent("onboarding_started") {
        override val properties = emptyMap<String, Any?>()
    }

    data class OnboardingCompleted(val stepsCompleted: Int) : AnalyticsEvent("onboarding_completed") {
        override val properties = mapOf("steps_completed" to stepsCompleted)
    }

    data object SignUpStarted : AnalyticsEvent("sign_up_started") {
        override val properties = mapOf("method" to "phone_otp")
    }

    data class LoginCompleted(val accountCreatedFromGuest: Boolean = false) : AnalyticsEvent("login_completed") {
        override val properties = mapOf(
            "method" to "phone_otp",
            "account_created_from_guest" to accountCreatedFromGuest,
        )
    }

    data object AccountDeletionRequested : AnalyticsEvent("account_deletion_requested") {
        override val properties = emptyMap<String, Any?>()
    }

    data class HomeViewed(val entryPoint: String) : AnalyticsEvent("home_viewed") {
        override val properties = mapOf("entry_point" to entryPoint)
    }

    /** Fired from HomeScreen's "Popular near you" carousel tap — distinguishes which banner was
     *  tapped, which the generic ScreenViewed (fired right after, by the navigate() call this
     *  triggers) can't do on its own. [position] is the card's index in the carousel at tap time. */
    data class HomeBannerTapped(val activityId: String, val position: Int) : AnalyticsEvent("home_banner_tapped") {
        override val properties = mapOf("activity_id" to activityId, "position" to position)
    }

    data class ActivityFeedViewed(
        val categoryFilter: String?,
        val areaId: String?,
        val resultCount: Int,
    ) : AnalyticsEvent("activity_feed_viewed") {
        override val properties = mapOf(
            "category_filter" to categoryFilter,
            "area_id" to areaId,
            "result_count" to resultCount,
        )
    }

    data class ActivityDetailViewed(
        val activityId: String,
        val categoryId: String?,
        val priceMinor: Int?,
        val currency: String,
        val availabilityStatus: String,
    ) : AnalyticsEvent("activity_detail_viewed") {
        override val properties = mapOf(
            "activity_id" to activityId,
            "category_id" to categoryId,
            "price_minor" to priceMinor,
            "currency" to currency,
            "availability_status" to availabilityStatus,
        )
    }

    data class ActivitySearchNoResults(val categoryId: String?, val areaId: String?) :
        AnalyticsEvent("activity_search_no_results") {
        override val properties = mapOf("category_id" to categoryId, "area_id" to areaId)
    }

    data class ChatOpened(val groupId: String, val activityId: String) : AnalyticsEvent("chat_opened") {
        override val properties = mapOf("group_id" to groupId, "activity_id" to activityId)
    }

    /** Fired from FindResultsScreen — the "Join a group" list a user sees for one activity after
     *  viewing its detail page. [resultCount] is the real group count shown (not the session
     *  count), so a 0 here means "explored this activity, no groups existed to join," distinct
     *  from never having opened this screen at all. */
    data class GroupsViewed(val activityId: String, val resultCount: Int) : AnalyticsEvent("groups_viewed") {
        override val properties = mapOf("activity_id" to activityId, "result_count" to resultCount)
    }

    /** Fired whenever GroupDetailScreen is shown, whether the group was just created, just
     *  joined, or merely opened to look at — a plain view, not an action. */
    data class GroupDetailViewed(val groupId: String, val activityId: String) : AnalyticsEvent("group_detail_viewed") {
        override val properties = mapOf("group_id" to groupId, "activity_id" to activityId)
    }

    /** Fired once, from CreateGroupScreen's "Create activity" tap — the one unambiguous create
     *  action in the group flow (getOrCreateMyGroup() is otherwise also called as a fallback
     *  default when GroupDetailScreen is opened with no selectedGroup, which must NOT re-fire
     *  this — see GroupDetailScreen.kt's comment). */
    data class GroupCreated(val groupId: String, val activityId: String) : AnalyticsEvent("group_created") {
        override val properties = mapOf("group_id" to groupId, "activity_id" to activityId)
    }

    /** Fired only from GroupDetailScreen's explicit "Join Group" button — the one call site that
     *  actually adds the current user as a member (state.joinGroup -> addMember). Deliberately
     *  NOT fired from FindResultsScreen's "Start it" tap on an unclaimed session, since that path
     *  (joinExistingGroup()) creates a group seeded with placeholder members, not a real join by
     *  the current user. */
    data class GroupJoined(val groupId: String, val activityId: String) : AnalyticsEvent("group_joined") {
        override val properties = mapOf("group_id" to groupId, "activity_id" to activityId)
    }

    /** New vs. source doc — trust & safety had zero taxonomy coverage; see Phase 1 additions. */
    data class UserBlocked(val groupId: String) : AnalyticsEvent("user_blocked") {
        override val properties = mapOf("group_id" to groupId)
    }

    /** Fired from GroupChatScreen's send() — client-only, like ChatOpened/UserBlocked, since chat
     *  messages are plain client Firestore writes with no Cloud Function to mirror this from.
     *  Carries [messageLength] (character count), never the message text itself — the actual
     *  content has no reason to leave the device into analytics. */
    data class ChatMessageSent(val groupId: String, val activityId: String, val messageLength: Int) :
        AnalyticsEvent("chat_message_sent") {
        override val properties = mapOf("group_id" to groupId, "activity_id" to activityId, "message_length" to messageLength)
    }

    /** Fired once per ReferAndEarnScreen load — the referral program's own "viewed" signal,
     *  previously completely untracked (confirmed by grep: zero AnalyticsTracker calls anywhere
     *  under ui/screens/referral/ before this). */
    data object ReferAndEarnViewed : AnalyticsEvent("refer_and_earn_viewed") {
        override val properties = emptyMap<String, Any?>()
    }

    /** Fired from inside ReferAndEarnScreen's shareCard()/onInstagramIconClick()/
     *  shareCardToInstagramStory()/shareCardToInstagramDirect() — one event for every share
     *  channel, distinguished by [channel], rather than a separate event per button. Does NOT
     *  carry the user's actual referral code — channel is enough signal for "which share button
     *  gets used," and the code itself has no reason to leave the device into analytics. */
    data class ReferralCodeShared(val channel: String) : AnalyticsEvent("referral_code_shared") {
        override val properties = mapOf("channel" to channel)
    }

    /** Fired from both of ReferAndEarnScreen's two "copy code" tap targets (the code card itself,
     *  and the dedicated Copy Code quick-share icon) — kept distinct from
     *  [ReferralCodeShared] since copying isn't itself a completed share, just intent. */
    data object ReferralCodeCopied : AnalyticsEvent("referral_code_copied") {
        override val properties = emptyMap<String, Any?>()
    }

    /** The referee side of the same relationship ReferralCodeShared/ReferralCodeCopied cover from
     *  the referrer side — fired from onboarding's ReferralScreen after redeemReferralCode()
     *  returns. Deliberately does NOT carry the raw backend rejection message as a property —
     *  that string's wording isn't a stable analytics dimension and can change independent of
     *  this event's schema — just whether it succeeded, not why in free text. */
    data class ReferralCodeRedeemed(val outcome: String, val alreadyAttributed: Boolean?) :
        AnalyticsEvent("referral_code_redeemed") {
        override val properties = mapOf("outcome" to outcome, "already_attributed" to alreadyAttributed)
    }

    /** `participationMode` — new vs. source doc; required to answer the doc's own "social value"
     *  business question (§1), which is unanswerable without a way to segment bookings by it. */
    data class BookingStarted(
        val activityId: String,
        val categoryId: String?,
        val activitySessionId: String,
        val participationMode: ParticipationMode,
        val quantity: Int,
        val amountMinor: Int,
        val currency: String,
    ) : AnalyticsEvent("booking_started") {
        override val properties = mapOf(
            "activity_id" to activityId,
            "category_id" to categoryId,
            "activity_session_id" to activitySessionId,
            "participation_mode" to participationMode.wireValue,
            "quantity" to quantity,
            "amount_minor" to amountMinor,
            "currency" to currency,
        )
    }

    data class PaymentStarted(
        val bookingId: String,
        val amountMinor: Int,
        val currency: String,
    ) : AnalyticsEvent("payment_started") {
        override val properties = mapOf(
            "booking_id" to bookingId,
            "amount_minor" to amountMinor,
            "currency" to currency,
            "provider" to "razorpay",
        )
    }
}

enum class ParticipationMode(val wireValue: String) {
    SOLO("solo"),
    GROUP_JOIN("group_join"),
    GROUP_CREATE("group_create"),
}
