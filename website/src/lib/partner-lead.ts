import { httpsCallable } from "firebase/functions";
import { functions } from "./firebase";

export interface PartnerLeadInput {
  name: string;
  phone: string;
  email?: string;
  businessName: string;
  city: string;
  category: string;
  websiteOrSocial?: string;
  preferredContactMethod?: string;
  notes?: string;
}

/** Calls the existing `submitPartnerLead` Cloud Function (functions/src/leads.ts) — field shape
 *  and required-field validation live there, not duplicated here. */
export async function submitPartnerLead(input: PartnerLeadInput): Promise<{ leadId: string }> {
  const callable = httpsCallable<PartnerLeadInput, { leadId: string }>(functions, "submitPartnerLead");
  const result = await callable(input);
  return result.data;
}
