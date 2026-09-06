import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { PartnerLead } from "./types";

/**
 * §5.2 — public lead-capture form submission. Deliberately unauthenticated (a prospective partner
 * has no MaxTime account yet); routed through a callable rather than a direct client write to
 * `partner_leads` so the shape/required fields are validated server-side rather than trusted from
 * whatever the landing page happens to send.
 */
export const submitPartnerLead = onCall(async (request) => {
  const { name, phone, email, businessName, city, category, websiteOrSocial, preferredContactMethod, notes } =
    request.data ?? {};

  if (!name || !phone || !businessName || !city || !category) {
    throw new HttpsError(
      "invalid-argument",
      "name, phone, businessName, city, and category are required."
    );
  }

  const lead: Omit<PartnerLead, "id"> = {
    name,
    phone,
    email: email || undefined,
    businessName,
    city,
    category,
    websiteOrSocial: websiteOrSocial || undefined,
    preferredContactMethod: preferredContactMethod || undefined,
    notes: notes || undefined,
    status: "NEW",
    source: "partner_landing_page",
    createdAt: FieldValue.serverTimestamp() as never,
    updatedAt: FieldValue.serverTimestamp() as never,
  };

  const ref = await db.collection("partner_leads").add(lead);
  // §5.2 — "Send confirmation to the lead and alert MaxTime Ops" belongs here once a real
  // notification channel (SMS/email) is wired up; deliberately not stubbed with a fake send.
  return { leadId: ref.id };
});
