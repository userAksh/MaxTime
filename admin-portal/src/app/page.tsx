"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { collection, getCountFromServer, query, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { AdminGate } from "@/components/AdminGate";

interface Counts {
  livePartners: number | null;
  pendingApprovals: number | null;
  payoutsOnHold: number | null;
}

/** §28 — Admin home shows operational health via live counts (Firestore aggregation queries,
 *  not a full document download) rather than static example numbers. */
function DashboardContent() {
  const [counts, setCounts] = useState<Counts>({ livePartners: null, pendingApprovals: null, payoutsOnHold: null });

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const [live, pending, held] = await Promise.all([
        getCountFromServer(query(collection(db, "partners"), where("status", "==", "APPROVED"))),
        getCountFromServer(query(collection(db, "partners"), where("status", "==", "PENDING_VERIFICATION"))),
        getCountFromServer(query(collection(db, "payouts"), where("status", "==", "ON_HOLD"))),
      ]);
      if (cancelled) return;
      setCounts({
        livePartners: live.data().count,
        pendingApprovals: pending.data().count,
        payoutsOnHold: held.data().count,
      });
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <>
      <h1 className="text-lg font-bold">Dashboard</h1>
      <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Card label="Live partners" value={counts.livePartners} />
        <Card label="Pending approvals" value={counts.pendingApprovals} />
        <Card label="Payouts on hold" value={counts.payoutsOnHold} />
      </div>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-semibold text-neutral-500">Quick actions</h2>
        <div className="flex flex-wrap gap-2">
          <Link href="/partners" className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm font-medium text-neutral-900">
            Review approvals
          </Link>
          <Link href="/bookings" className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm font-medium text-neutral-900">
            Search booking
          </Link>
          <Link href="/payouts" className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm font-medium text-neutral-900">
            Review payouts
          </Link>
        </div>
      </section>
    </>
  );
}

function Card({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="rounded-lg border border-neutral-200 bg-white p-4">
      <p className="text-2xl font-bold text-neutral-900">{value ?? "…"}</p>
      <p className="mt-1 text-xs text-neutral-500">{label}</p>
    </div>
  );
}

export default function DashboardPage() {
  return (
    <AdminGate>
      <DashboardContent />
    </AdminGate>
  );
}
