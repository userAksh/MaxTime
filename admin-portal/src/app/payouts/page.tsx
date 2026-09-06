"use client";

import { useEffect, useState } from "react";
import { collection, onSnapshot, orderBy, query } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { callFunction } from "@/lib/functions";
import { AdminGate } from "@/components/AdminGate";

interface PayoutRow {
  id: string;
  partnerId: string;
  netAmount: number;
  status: string;
  bookingIds: string[];
}

function PayoutsContent() {
  const [payouts, setPayouts] = useState<PayoutRow[]>([]);
  const [partnerId, setPartnerId] = useState("");
  const [periodStart, setPeriodStart] = useState("");
  const [periodEnd, setPeriodEnd] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    return onSnapshot(query(collection(db, "payouts"), orderBy("createdAt", "desc")), (snap) => {
      setPayouts(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as PayoutRow));
    });
  }, []);

  async function calculate() {
    setError(null);
    setBusy(true);
    try {
      await callFunction<
        { partnerId: string; periodStartMillis: number; periodEndMillis: number },
        { payoutId: string | null; bookingCount: number; netAmount: number }
      >("calculatePayout")({
        partnerId,
        periodStartMillis: new Date(periodStart).getTime(),
        periodEndMillis: new Date(periodEnd).getTime(),
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not calculate payout.");
    } finally {
      setBusy(false);
    }
  }

  async function toggleHold(payoutId: string, hold: boolean) {
    setError(null);
    try {
      const reason = hold ? window.prompt("Reason for holding this payout?") ?? undefined : undefined;
      if (hold && !reason) return;
      await callFunction<{ payoutId: string; hold: boolean; reason?: string }, { ok: boolean }>("setPayoutHold")({
        payoutId,
        hold,
        reason,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update this payout.");
    }
  }

  return (
    <>
      <h1 className="text-lg font-bold">Payouts</h1>

      <div className="mt-4 flex flex-wrap items-end gap-2 rounded-lg border border-dashed border-neutral-300 p-4">
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wide text-neutral-500">Partner ID</label>
          <input className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900" value={partnerId} onChange={(e) => setPartnerId(e.target.value)} />
        </div>
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wide text-neutral-500">Period start</label>
          <input type="date" className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
        </div>
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wide text-neutral-500">Period end</label>
          <input type="date" className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
        </div>
        <button
          disabled={busy || !partnerId || !periodStart || !periodEnd}
          className="rounded-lg bg-neutral-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-40"
          onClick={calculate}
        >
          {busy ? "Calculating…" : "Calculate payout"}
        </button>
      </div>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      <div className="mt-4 flex flex-col gap-2">
        {payouts.map((p) => (
          <div key={p.id} className="flex items-center justify-between gap-2 rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
            <div>
              <p className="font-medium text-neutral-900">₹{p.netAmount} net · {p.bookingIds.length} bookings</p>
              <p className="text-xs text-neutral-500">partner {p.partnerId}</p>
            </div>
            <div className="flex items-center gap-2">
              <span className="rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">{p.status}</span>
              {p.status === "ON_HOLD" ? (
                <button className="rounded-md border border-neutral-300 px-2.5 py-1 text-xs font-semibold text-neutral-900" onClick={() => toggleHold(p.id, false)}>
                  Release
                </button>
              ) : (
                <button className="rounded-md border border-neutral-300 px-2.5 py-1 text-xs font-semibold text-neutral-900" onClick={() => toggleHold(p.id, true)}>
                  Hold
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

export default function PayoutsPage() {
  return (
    <AdminGate>
      <PayoutsContent />
    </AdminGate>
  );
}
