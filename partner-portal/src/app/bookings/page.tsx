"use client";

import { useEffect, useState } from "react";
import { collection, onSnapshot, orderBy, query, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { callFunction } from "@/lib/functions";
import { PartnerGate } from "@/components/PartnerGate";

interface BookingRow {
  id: string;
  status: string;
  amount: number;
  participantCount: number;
  slotId: string;
}

/** §16 — Booking Detail. §20 — check-in/no-show actions live right on the list rather than a
 *  separate detail screen for this first pass; a dedicated detail view is a natural next step
 *  once this is proven out, not a redesign of it. */
function BookingsContent() {
  const { partnerId } = useAuth();
  const [bookings, setBookings] = useState<BookingRow[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!partnerId) return;
    const q = query(collection(db, "bookings"), where("partnerId", "==", partnerId), orderBy("createdAt", "desc"));
    return onSnapshot(q, (snap) => {
      setBookings(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as BookingRow));
    });
  }, [partnerId]);

  async function mark(bookingId: string, status: "CHECKED_IN" | "NO_SHOW" | "COMPLETED") {
    setError(null);
    setBusyId(bookingId);
    try {
      await callFunction<{ bookingId: string; status: string }, { ok: boolean }>("markAttendance")({
        bookingId,
        status,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update attendance.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <h1 className="text-lg font-bold">Bookings</h1>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {bookings.length === 0 ? (
        <p className="mt-6 rounded-lg border border-dashed border-neutral-300 px-4 py-6 text-center text-sm text-neutral-500">
          No bookings yet. Once customers book, they&apos;ll appear here.
        </p>
      ) : (
        <div className="mt-4 flex flex-col gap-2">
          {bookings.map((b) => (
            <div key={b.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
              <div>
                <p className="font-medium text-neutral-900">₹{b.amount} · {b.participantCount} participant{b.participantCount === 1 ? "" : "s"}</p>
                <p className="text-xs text-neutral-500">{b.status}</p>
              </div>
              {b.status === "CONFIRMED" && (
                <div className="flex gap-2">
                  <button
                    disabled={busyId === b.id}
                    className="rounded-md bg-neutral-900 px-2.5 py-1 text-xs font-semibold text-white disabled:opacity-40"
                    onClick={() => mark(b.id, "CHECKED_IN")}
                  >
                    Check in
                  </button>
                  <button
                    disabled={busyId === b.id}
                    className="rounded-md border border-neutral-300 px-2.5 py-1 text-xs font-semibold text-neutral-900 disabled:opacity-40"
                    onClick={() => mark(b.id, "NO_SHOW")}
                  >
                    No-show
                  </button>
                </div>
              )}
              {b.status === "CHECKED_IN" && (
                <button
                  disabled={busyId === b.id}
                  className="rounded-md bg-neutral-900 px-2.5 py-1 text-xs font-semibold text-white disabled:opacity-40"
                  onClick={() => mark(b.id, "COMPLETED")}
                >
                  Mark completed
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </>
  );
}

export default function BookingsPage() {
  return (
    <PartnerGate>
      <BookingsContent />
    </PartnerGate>
  );
}
