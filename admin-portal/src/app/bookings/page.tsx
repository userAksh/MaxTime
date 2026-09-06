"use client";

import { useEffect, useState } from "react";
import { collection, doc, getDoc, onSnapshot, orderBy, query } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { AdminGate } from "@/components/AdminGate";

interface BookingRow {
  id: string;
  partnerId: string;
  customerUserId: string;
  status: string;
  amount: number;
  participantCount: number;
}

/**
 * §32 — Admin Booking Management. Recent-bookings list + direct booking-ID lookup for this first
 * pass; free-text search by customer/partner/activity name needs either denormalized fields or a
 * real search index (Algolia/Typesense), which is a deliberate follow-up, not faked here with a
 * client-side filter over a tiny page of results.
 */
function BookingsContent() {
  const [bookings, setBookings] = useState<BookingRow[]>([]);
  const [lookupId, setLookupId] = useState("");
  const [lookupResult, setLookupResult] = useState<BookingRow | null | undefined>(undefined);

  useEffect(() => {
    return onSnapshot(query(collection(db, "bookings"), orderBy("createdAt", "desc")), (snap) => {
      setBookings(snap.docs.slice(0, 50).map((d) => ({ id: d.id, ...d.data() }) as BookingRow));
    });
  }, []);

  async function lookup() {
    const snap = await getDoc(doc(db, "bookings", lookupId.trim()));
    setLookupResult(snap.exists() ? ({ id: snap.id, ...snap.data() } as BookingRow) : null);
  }

  return (
    <>
      <h1 className="text-lg font-bold">Bookings</h1>

      <div className="mt-4 flex gap-2">
        <input
          className="flex-1 rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
          placeholder="Look up by booking ID"
          value={lookupId}
          onChange={(e) => setLookupId(e.target.value)}
        />
        <button
          className="rounded-lg bg-neutral-900 px-3 py-2 text-sm font-semibold text-white"
          onClick={lookup}
          disabled={!lookupId}
        >
          Find
        </button>
      </div>
      {lookupResult !== undefined && (
        <div className="mt-2">
          {lookupResult ? <BookingCard b={lookupResult} /> : <p className="text-sm text-neutral-500">Not found.</p>}
        </div>
      )}

      <h2 className="mt-6 mb-2 text-sm font-semibold text-neutral-500">Recent bookings</h2>
      <div className="flex flex-col gap-2">
        {bookings.map((b) => (
          <BookingCard key={b.id} b={b} />
        ))}
      </div>
    </>
  );
}

function BookingCard({ b }: { b: BookingRow }) {
  return (
    <div className="rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
      <div className="flex items-center justify-between">
        <p className="font-medium text-neutral-900">₹{b.amount} · {b.participantCount} participant{b.participantCount === 1 ? "" : "s"}</p>
        <span className="rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">{b.status}</span>
      </div>
      <p className="mt-1 text-xs text-neutral-500">{b.id} · partner {b.partnerId}</p>
    </div>
  );
}

export default function BookingsPage() {
  return (
    <AdminGate>
      <BookingsContent />
    </AdminGate>
  );
}
