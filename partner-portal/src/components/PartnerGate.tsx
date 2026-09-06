"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { usePartner } from "@/lib/use-partner";
import { PartnerNav } from "./PartnerNav";

/** Shared guard for every authenticated page except Home (which has its own richer pending/
 *  rejected states) — redirects to /login or /onboarding as needed, and only renders children
 *  once there's an APPROVED partner to show real data for. */
export function PartnerGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, loading: authLoading, partnerId } = useAuth();
  const { partner, loading: partnerLoading } = usePartner();

  useEffect(() => {
    if (!authLoading && !user) router.push("/login");
  }, [authLoading, user, router]);

  useEffect(() => {
    if (!authLoading && !partnerLoading && user && !partnerId) router.push("/onboarding");
  }, [authLoading, partnerLoading, user, partnerId, router]);

  if (authLoading || partnerLoading || !user || !partnerId) return null;

  if (partner?.status !== "APPROVED") {
    return (
      <div className="flex min-h-screen flex-col">
        <PartnerNav />
        <main className="mx-auto flex w-full max-w-md flex-1 flex-col items-center justify-center gap-2 px-6 text-center">
          <p className="text-sm text-neutral-500">
            This is available once your account is approved. Check Home for your current status.
          </p>
        </main>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col">
      <PartnerNav />
      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-6">{children}</main>
    </div>
  );
}
