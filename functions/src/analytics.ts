import { defineString } from "firebase-functions/params";
import { logger } from "firebase-functions/v2";
import Mixpanel from "mixpanel";

/**
 * Server-side counterpart to shared/src/commonMain/.../analytics/AnalyticsTracker.kt — emits the
 * `Backend`/`Backend-preferred` events from docs/analytics/event-taxonomy.md §5. Every call site
 * fires this AFTER its Firestore transaction has already committed, using values captured from
 * inside that transaction — never from inside the transaction callback itself, since a Firestore
 * transaction body can be retried on contention and must stay a pure, side-effect-free function of
 * its reads. The idempotency these events inherit comes from the same existing-document check
 * each transaction already does (payments/{providerTransactionId}, BOOKING_TRANSITIONS, etc.) —
 * this module adds no new idempotency mechanism of its own.
 *
 * Not a secret — defineString (plain runtime config), not defineSecret — matching the client's own
 * treatment of this same token. Value comes from functions/.env.<project-id> (one Mixpanel
 * project shared across all three Firebase projects — see docs/analytics/dashboards-spec.md), not
 * checked into git. Still blank-safe: if a deploy ever runs without that file, mixpanel is simply
 * not initialized, so every track() call below stays a safe no-op rather than failing the deploy.
 */
const mixpanelToken = defineString("MIXPANEL_TOKEN", { default: "" });

let client: Mixpanel.Mixpanel | null = null;
let initializedToken: string | null = null;

function getClient(): Mixpanel.Mixpanel | null {
  const token = mixpanelToken.value();
  if (!token) return null;
  if (client && initializedToken === token) return client;
  client = Mixpanel.init(token);
  initializedToken = token;
  return client;
}

function environmentFromProjectId(): string {
  const projectId = process.env.GCLOUD_PROJECT ?? "";
  if (projectId === "maxtime-app") return "production";
  if (projectId === "maxtime-staging") return "staging";
  if (projectId === "maxtime-beta") return "beta";
  return "unknown";
}

/**
 * [distinctId] must be the same opaque user_id the client-side AnalyticsTracker.identify() call
 * uses (the Firebase Auth UID) — never phone/email, per event-taxonomy.md §4's sensitive-data
 * denylist. Fire-and-forget: a Mixpanel outage must never affect the booking/payment/attendance
 * transaction that already committed by the time this runs (event-taxonomy.md §3).
 */
export function trackBackendEvent(name: string, distinctId: string, properties: Record<string, unknown>): void {
  const mp = getClient();
  if (!mp) return;
  try {
    mp.track(name, {
      distinct_id: distinctId,
      schema_version: 1,
      platform: "backend",
      environment: environmentFromProjectId(),
      ...properties,
    });
  } catch (err) {
    logger.warn("trackBackendEvent failed", { name, error: err instanceof Error ? err.message : String(err) });
  }
}
