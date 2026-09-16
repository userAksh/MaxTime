"use client";

import { useEffect, useState } from "react";
import { doc, onSnapshot } from "firebase/firestore";
import { db } from "./firebase";
import { useAuth } from "./auth-context";

export interface PartnerDoc {
  id: string;
  type: "VENUE" | "INSTRUCTOR" | "BOTH";
  displayName: string;
  status: "PENDING_VERIFICATION" | "APPROVED" | "REJECTED" | "SUSPENDED";
  verificationNotes?: string;
  /** Null until the partner actually hits "Submit for review" — status alone flips to
   *  PENDING_VERIFICATION at onboarding step 0, well before that. */
  submittedAt?: unknown;
}

/** Live (onSnapshot, not a one-off get) since a partner's status can change out from under them —
 *  e.g. Ops approves them in another tab — and the portal should reflect that without a refresh. */
export function usePartner() {
  const { partnerId } = useAuth();
  const [partner, setPartner] = useState<PartnerDoc | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!partnerId) {
      setPartner(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    return onSnapshot(doc(db, "partners", partnerId), (snap) => {
      setPartner(snap.exists() ? ({ id: snap.id, ...snap.data() } as PartnerDoc) : null);
      setLoading(false);
    });
  }, [partnerId]);

  return { partner, loading };
}
