"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { onIdTokenChanged, User } from "firebase/auth";
import { auth } from "./firebase";

interface AuthState {
  user: User | null;
  loading: boolean;
  isAdmin: boolean;
  adminRole: string | null;
}

const AuthContext = createContext<AuthState>({ user: null, loading: true, isAdmin: false, adminRole: null });

/**
 * §41/§52 — admin/adminRole claims are bootstrapped out-of-band by MaxTime Ops via a one-off
 * Admin SDK script (see functions/README below), never granted through any document a client can
 * write to. This context just reads whatever claim already exists on the token — it never sets one.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [isAdmin, setIsAdmin] = useState(false);
  const [adminRole, setAdminRole] = useState<string | null>(null);

  useEffect(() => {
    return onIdTokenChanged(auth, async (u) => {
      if (!u) {
        setUser(null);
        setIsAdmin(false);
        setAdminRole(null);
        setLoading(false);
        return;
      }
      const tokenResult = await u.getIdTokenResult();
      setUser(u);
      setIsAdmin(tokenResult.claims.admin === true || tokenResult.claims.adminRole != null);
      setAdminRole((tokenResult.claims.adminRole as string) ?? null);
      setLoading(false);
    });
  }, []);

  return <AuthContext.Provider value={{ user, loading, isAdmin, adminRole }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
