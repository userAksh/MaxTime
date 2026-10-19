package com.maxtime.app.analytics

import com.maxtime.app.data.LocalSettings
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * The one place UI/domain code talks to analytics — per docs/analytics/event-taxonomy.md §12
 * ("UI/domain code should not call vendor SDK everywhere").
 *
 * Talks to Mixpanel's public HTTP tracking API (`POST https://api.mixpanel.com/track`) directly
 * via Ktor, rather than the native Mixpanel SDK. Mixpanel's official iOS (Swift) SDK cannot be
 * called from Kotlin/Native at all — confirmed during Phase 2 by inspecting its generated
 * Objective-C compatibility header, which exposes zero bridged classes (its public API is built
 * entirely on Swift-only generics/protocols). This HTTP approach is one implementation shared by
 * both platforms instead of two divergent native adapters, and needs no cinterop at all.
 *
 * Behavioral trade-off vs. a native SDK: no built-in offline queueing/retry. Every [track] call
 * is fire-and-forget — dropped silently on failure (see event-taxonomy.md §3: "Analytics failures
 * must never block login, discovery, booking, payment or chat"). Revisit if offline reliability
 * becomes a real product need.
 */
object AnalyticsTracker {
    private const val TRACK_URL = "https://api.mixpanel.com/track"
    private const val DISTINCT_ID_KEY = "analytics_distinct_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = HttpClient()

    private var platform: String = ""
    private var appVersionValue: String = ""
    /** Exposed so callable-function wrappers (BookingFunctions/AccountFunctions/ModerationFunctions)
     *  can thread the build version into the handful of backend events actually triggered by a
     *  direct client request — see functions/src/analytics.ts's doc comment for why most backend
     *  events (webhook/scheduled/partner-portal triggered) deliberately don't get one. */
    val appVersion: String get() = appVersionValue
    private var environment: String = ""
    private var token: String = ""
    private var distinctId: String = ""
    private var initialized = false

    fun initialize(platform: String, appVersion: String, environment: String, mixpanelToken: String) {
        this.platform = platform
        this.appVersionValue = appVersion
        this.environment = environment
        this.token = mixpanelToken
        distinctId = LocalSettings.getString(DISTINCT_ID_KEY) ?: generateAndPersistId()
        initialized = true
    }

    fun track(event: AnalyticsEvent) {
        if (!initialized || token.isBlank()) return
        val enriched = event.properties + mapOf(
            "schema_version" to 1,
            "platform" to platform,
            "app_version" to appVersion,
            "environment" to environment,
        )
        if (environment != "production") {
            println("[Analytics] ${event.name} $enriched")
        }
        send(event.name, enriched)
    }

    /** Call after a successful sign-in. [userId] must be the Firebase Auth UID — never
     *  phone/email. Merges the prior anonymous distinct_id into the real account via Mixpanel's
     *  documented `$identify` HTTP event, then switches to the real ID for all future events. */
    fun identify(userId: String) {
        if (!initialized || token.isBlank() || userId == distinctId) return
        val previousId = distinctId
        sendRaw(
            "\$identify",
            mapOf("\$identified_id" to userId, "\$anon_id" to previousId),
        )
        distinctId = userId
        LocalSettings.putString(DISTINCT_ID_KEY, userId)
    }

    /** Call on sign-out and after account deletion — both exit paths, so a later session's events
     *  are never attributed to the previous account. See event-taxonomy.md §2. */
    fun reset() {
        if (!initialized) return
        generateAndPersistId()
    }

    private fun generateAndPersistId(): String {
        val id = "anon_" + (1..4).joinToString("") { Random.nextInt(0, 0xFFFF).toString(16).padStart(4, '0') }
        distinctId = id
        LocalSettings.putString(DISTINCT_ID_KEY, id)
        return id
    }

    private fun send(eventName: String, properties: Map<String, Any?>) {
        sendRaw(eventName, properties)
    }

    private fun sendRaw(eventName: String, properties: Map<String, Any?>) {
        val id = distinctId
        val tkn = token
        scope.launch {
            runCatching {
                val payload = buildJsonObject {
                    put("event", eventName)
                    put("properties", buildJsonObject {
                        put("token", tkn)
                        put("distinct_id", id)
                        for ((key, value) in properties) {
                            put(key, value.toJsonPrimitive())
                        }
                    })
                }
                client.post(TRACK_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(JsonArray(listOf(payload)).toString())
                }
            }
            // Deliberately no error handling beyond runCatching swallowing it — a dropped event
            // must never surface to the caller (event-taxonomy.md §3).
        }
    }

    private fun Any?.toJsonPrimitive() = when (this) {
        null -> JsonNull
        is String -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }
}
