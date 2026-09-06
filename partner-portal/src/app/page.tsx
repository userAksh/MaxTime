"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { signOut } from "firebase/auth";
import { collection, limit, onSnapshot, orderBy, query, where } from "firebase/firestore";
import { auth, db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { usePartner } from "@/lib/use-partner";
import { callFunction } from "@/lib/functions";
import { PartnerNav } from "@/components/PartnerNav";

interface BookingRow {
  id: string;
  status: string;
  amount: number;
  participantCount: number;
  createdAt?: { toDate: () => Date };
}

interface PayoutRow {
  id: string;
  netAmount: number;
  status: string;
  expectedPayoutDate?: { toDate: () => Date };
}

export default function HomePage() {
  const router = useRouter();
  const { user, loading: authLoading, partnerId } = useAuth();
  const { partner, loading: partnerLoading } = usePartner();
  const [bookings, setBookings] = useState<BookingRow[]>([]);
  const [payout, setPayout] = useState<PayoutRow | null>(null);
  const [resubmitting, setResubmitting] = useState(false);
  const [resubmitError, setResubmitError] = useState<string | null>(null);

  async function submitAnotherRequest() {
    setResubmitError(null);
    setResubmitting(true);
    try {
      await callFunction<Record<string, never>, { ok: boolean }>("resubmitPartnerAccount")({});
    } catch (e) {
      setResubmitError(e instanceof Error ? e.message : "Could not resubmit. Try again.");
    } finally {
      setResubmitting(false);
    }
  }

  useEffect(() => {
    if (!authLoading && !user) router.push("/login");
  }, [authLoading, user, router]);

  useEffect(() => {
    if (!authLoading && !partnerLoading && user && !partnerId) router.push("/onboarding");
  }, [authLoading, partnerLoading, user, partnerId, router]);

  useEffect(() => {
    if (!partnerId || partner?.status !== "APPROVED") return;
    const bookingsQuery = query(
      collection(db, "bookings"),
      where("partnerId", "==", partnerId),
      orderBy("createdAt", "desc"),
      limit(10)
    );
    const unsubBookings = onSnapshot(bookingsQuery, (snap) => {
      setBookings(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as BookingRow));
    });

    const payoutsQuery = query(
      collection(db, "payouts"),
      where("partnerId", "==", partnerId),
      orderBy("createdAt", "desc"),
      limit(1)
    );
    const unsubPayouts = onSnapshot(payoutsQuery, (snap) => {
      setPayout(snap.empty ? null : ({ id: snap.docs[0].id, ...snap.docs[0].data() } as PayoutRow));
    });

    return () => {
      unsubBookings();
      unsubPayouts();
    };
  }, [partnerId, partner?.status]);

  if (authLoading || partnerLoading || !user || !partnerId) return null;

  if (partner?.status === "PENDING_VERIFICATION") {
    return (
      <main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center gap-3 px-6 text-center">
        <h1 className="text-lg font-bold">Thanks, {partner.displayName}!</h1>
        <p className="text-sm text-neutral-500">
          Your account is being reviewed by the MaxTime team. We&apos;ll let you know as soon as you&apos;re live.
        </p>
        <SignOutLink />
      </main>
    );
  }

  if (partner?.status === "REJECTED" || partner?.status === "SUSPENDED") {
    return (
      <main className="mx-auto flex min-h-screen w-full max-w-md flex-col items-center justify-center gap-3 px-6 text-center">
        <h1 className="text-lg font-bold">Account {partner.status === "REJECTED" ? "not approved" : "suspended"}</h1>
        <p className="text-sm text-neutral-500">
          {partner.verificationNotes || "Contact MaxTime support for details."}
        </p>
        {partner.status === "REJECTED" && (
          <>
            <button
              disabled={resubmitting}
              className="mt-2 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
              onClick={submitAnotherRequest}
            >
              {resubmitting ? "Submitting…" : "Submit another request"}
            </button>
            {resubmitError && <p className="text-sm text-red-600">{resubmitError}</p>}
          </>
        )}
        <SignOutLink />
      </main>
    );
  }

  return (
    <div className="flex min-h-screen flex-col">
      <PartnerNav />
      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-6">
        <h1 className="text-lg font-bold">Welcome back, {partner?.displayName}</h1>

        <section className="mt-6">
          <h2 className="mb-2 text-sm font-semibold text-neutral-500">Recent bookings</h2>
          {bookings.length === 0 ? (
            <EmptyNote>No bookings yet. Once customers book, they&apos;ll appear here.</EmptyNote>
          ) : (
            <div className="flex flex-col gap-2">
              {bookings.map((b) => (
                <div key={b.id} className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
                  <div>
                    <p className="font-medium text-neutral-900">₹{b.amount} · {b.participantCount} participant{b.participantCount === 1 ? "" : "s"}</p>
                    <p className="text-xs text-neutral-500">{b.id}</p>
                  </div>
                  <StatusPill status={b.status} />
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="mt-6">
          <h2 className="mb-2 text-sm font-semibold text-neutral-500">Payout snapshot</h2>
          {payout ? (
            <div className="rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
              <p className="font-medium text-neutral-900">₹{payout.netAmount} — {payout.status}</p>
            </div>
          ) : (
            <EmptyNote>No payouts yet.</EmptyNote>
          )}
        </section>
      </main>
    </div>
  );
}

function SignOutLink() {
  const router = useRouter();
  return (
    <button
      className="mt-2 text-sm text-neutral-500 underline"
      onClick={async () => {
        await signOut(auth);
        router.push("/login");
      }}
    >
      Sign out
    </button>
  );
}

function EmptyNote({ children }: { children: React.ReactNode }) {
  return <p className="rounded-lg border border-dashed border-neutral-300 px-4 py-6 text-center text-sm text-neutral-500">{children}</p>;
}

function StatusPill({ status }: { status: string }) {
  return <span className="rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">{status}</span>;
}
