"use client";

import { useEffect, useState } from "react";
import { collection, onSnapshot, orderBy, query, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { PartnerGate } from "@/components/PartnerGate";

interface PayoutRow {
  id: string;
  grossAmount: number;
  commissionAmount: number;
  adjustments: number;
  netAmount: number;
  status: string;
  bookingIds: string[];
}

function PayoutsContent() {
  const { partnerId } = useAuth();
  const [payouts, setPayouts] = useState<PayoutRow[]>([]);

  useEffect(() => {
    if (!partnerId) return;
    return onSnapshot(
      query(collection(db, "payouts"), where("partnerId", "==", partnerId), orderBy("createdAt", "desc")),
      (snap) => setPayouts(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as PayoutRow))
    );
  }, [partnerId]);

  return (
    <>
      <h1 className="text-lg font-bold">Payouts</h1>
      {payouts.length === 0 ? (
        <p className="mt-6 rounded-lg border border-dashed border-neutral-300 px-4 py-6 text-center text-sm text-neutral-500">
          No payouts yet.
        </p>
      ) : (
        <div className="mt-4 flex flex-col gap-2">
          {payouts.map((p) => (
            <div key={p.id} className="rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
              <div className="flex items-center justify-between">
                <p className="font-semibold text-neutral-900">₹{p.netAmount} net</p>
                <span className="rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">{p.status}</span>
              </div>
              <p className="mt-1 text-xs text-neutral-500">
                ₹{p.grossAmount} gross − ₹{p.commissionAmount} commission
                {p.adjustments ? ` − ₹${p.adjustments} adjustments` : ""} · {p.bookingIds.length} booking
                {p.bookingIds.length === 1 ? "" : "s"}
              </p>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

export default function PayoutsPage() {
  return (
    <PartnerGate>
      <PayoutsContent />
    </PartnerGate>
  );
}
