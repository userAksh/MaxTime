"use client";

import { createContext, useContext, useEffect, useRef, useState } from "react";
import { onIdTokenChanged, User } from "firebase/auth";
import { auth } from "./firebase";
import { callFunction } from "./functions";

export type PartnerStatus = "PENDING_VERIFICATION" | "APPROVED" | "REJECTED" | "SUSPENDED" | null;

interface AuthState {
  user: User | null;
  loading: boolean;
  partnerId: string | null;
  partnerRole: string | null;
  /** Bumps whenever a caller forces a token refresh (e.g. right after creating a Partner, since
   *  the custom claim doesn't exist on the token until the next refresh) so consumers re-render. */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState>({
  user: null,
  loading: true,
  partnerId: null,
  partnerRole: null,
  refresh: async () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [partnerId, setPartnerId] = useState<string | null>(null);
  const [partnerRole, setPartnerRole] = useState<string | null>(null);
  const triedClaimingInvite = useRef(false);

  async function applyClaims(u: User | null) {
    if (!u) {
      setUser(null);
      setPartnerId(null);
      setPartnerRole(null);
      setLoading(false);
      triedClaimingInvite.current = false;
      return;
    }
    const tokenResult = await u.getIdTokenResult();
    const claimedPartnerId = (tokenResult.claims.partnerId as string) ?? null;

    // §22 — a staff member invited by phone before they had an account: the first time they
    // sign in with no partnerId claim yet, check (once per session) whether their phone number
    // matches a pending invite, and if so pick up the freshly-set claim immediately.
    if (!claimedPartnerId && !triedClaimingInvite.current) {
      triedClaimingInvite.current = true;
      try {
        const result = await callFunction<Record<string, never>, { claimed: boolean }>("claimStaffInvite")({});
        if (result.claimed) {
          await u.getIdToken(true);
          const refreshed = await u.getIdTokenResult();
          setUser(u);
          setPartnerId((refreshed.claims.partnerId as string) ?? null);
          setPartnerRole((refreshed.claims.partnerRole as string) ?? null);
          setLoading(false);
          return;
        }
      } catch {
        // No matching invite (or offline) — proceed as a normal user with no partner yet.
      }
    }

    setUser(u);
    setPartnerId(claimedPartnerId);
    setPartnerRole((tokenResult.claims.partnerRole as string) ?? null);
    setLoading(false);
  }

  useEffect(() => {
    return onIdTokenChanged(auth, (u) => {
      void applyClaims(u);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function refresh() {
    if (!auth.currentUser) return;
    await auth.currentUser.getIdToken(true);
    await applyClaims(auth.currentUser);
  }

  return (
    <AuthContext.Provider value={{ user, loading, partnerId, partnerRole, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
