"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { AdminNav } from "./AdminNav";

export function AdminGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { user, loading, isAdmin } = useAuth();

  useEffect(() => {
    if (!loading && !user) router.push("/login");
  }, [loading, user, router]);

  if (loading || !user) return null;

  if (!isAdmin) {
    return (
      <main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center gap-2 px-6 text-center">
        <p className="text-sm text-neutral-500">
          This account isn&apos;t authorized for the Admin Portal. Contact a Super Admin to have your access
          granted.
        </p>
      </main>
    );
  }

  return (
    <div className="flex min-h-screen flex-col">
      <AdminNav />
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-6">{children}</main>
    </div>
  );
}
