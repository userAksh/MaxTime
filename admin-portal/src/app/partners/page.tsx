"use client";

import { useEffect, useState } from "react";
import { collection, getDocs, onSnapshot, orderBy, query, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { callFunction } from "@/lib/functions";
import { AdminGate } from "@/components/AdminGate";

interface PartnerRow {
  id: string;
  type: string;
  displayName: string;
  legalName: string | null;
  status: string;
  verificationNotes?: string | null;
}

interface VenueRow {
  id: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  contactNumber: string;
  category: string;
  description: string;
  operatingHours: string;
  published: boolean;
}

interface ServiceRow {
  id: string;
  name: string;
  format: string;
  durationMinutes: number;
  capacity: number;
  price: number;
  pricingUnit: string;
  published: boolean;
}

/** §29 — Admin Partner Management: the other half of the onboarding golden path (§6 step 14 —
 *  "After approval, publish or activate inventory" starts here). */
function PartnersContent() {
  const [partners, setPartners] = useState<PartnerRow[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  useEffect(() => {
    return onSnapshot(query(collection(db, "partners"), orderBy("createdAt", "desc")), (snap) => {
      setPartners(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as PartnerRow));
    });
  }, []);

  async function review(partnerId: string, decision: "APPROVED" | "REJECTED") {
    setError(null);
    let notes: string | undefined;
    if (decision === "REJECTED") {
      const input = window.prompt("Reason for rejecting? (required)");
      if (!input || !input.trim()) {
        setError("Rejecting requires a reason — nothing was changed.");
        return;
      }
      notes = input.trim();
    }
    setBusyId(partnerId);
    try {
      await callFunction<{ partnerId: string; decision: string; notes?: string }, { ok: boolean }>(
        "reviewPartnerAccount"
      )({ partnerId, decision, notes });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update this partner.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <>
      <h1 className="text-lg font-bold">Partners</h1>
      <p className="mt-1 text-xs text-neutral-500">
        <strong className="text-amber-700">PENDING_VERIFICATION</strong> — submitted onboarding, not yet reviewed;
        invisible to customers. <strong className="text-green-700">APPROVED</strong> — reviewed and allowed to
        publish. <strong className="text-red-700">REJECTED</strong> — reviewed and denied. Tap a partner to see what
        they submitted before deciding.
      </p>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      <div className="mt-4 flex flex-col gap-2">
        {partners.map((p) => (
          <div key={p.id} className="rounded-lg border border-neutral-200 bg-white text-sm">
            <div
              role="button"
              tabIndex={0}
              className="flex w-full cursor-pointer items-center justify-between gap-2 px-4 py-3 text-left"
              onClick={() => setExpandedId(expandedId === p.id ? null : p.id)}
              onKeyDown={(e) => e.key === "Enter" && setExpandedId(expandedId === p.id ? null : p.id)}
            >
              <div>
                <p className="font-medium text-neutral-900">{p.displayName}</p>
                <p className="text-xs text-neutral-500">
                  {p.type} · {expandedId === p.id ? "Hide details ▲" : "View details ▼"}
                </p>
              </div>
              <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                <StatusPill status={p.status} />
                {p.status === "PENDING_VERIFICATION" && (
                  <>
                    <button
                      disabled={busyId === p.id}
                      className="rounded-md bg-neutral-900 px-2.5 py-1 text-xs font-semibold text-white disabled:opacity-40"
                      onClick={() => review(p.id, "APPROVED")}
                    >
                      Approve
                    </button>
                    <button
                      disabled={busyId === p.id}
                      className="rounded-md border border-neutral-300 px-2.5 py-1 text-xs font-semibold text-neutral-900 disabled:opacity-40"
                      onClick={() => review(p.id, "REJECTED")}
                    >
                      Reject
                    </button>
                  </>
                )}
              </div>
            </div>
            {expandedId === p.id && <PartnerDetails partner={p} />}
          </div>
        ))}
      </div>
    </>
  );
}

function PartnerDetails({ partner }: { partner: PartnerRow }) {
  const [venues, setVenues] = useState<VenueRow[] | null>(null);
  const [services, setServices] = useState<ServiceRow[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const [venueSnap, serviceSnap] = await Promise.all([
        getDocs(query(collection(db, "venues"), where("partnerId", "==", partner.id))),
        getDocs(query(collection(db, "services"), where("partnerId", "==", partner.id))),
      ]);
      if (cancelled) return;
      setVenues(venueSnap.docs.map((d) => ({ id: d.id, ...d.data() }) as VenueRow));
      setServices(serviceSnap.docs.map((d) => ({ id: d.id, ...d.data() }) as ServiceRow));
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [partner.id]);

  return (
    <div className="border-t border-neutral-200 px-4 py-3">
      {partner.legalName && <p className="text-xs text-neutral-500">Legal name: {partner.legalName}</p>}
      {partner.status === "REJECTED" && partner.verificationNotes && (
        <p className="mt-1 text-xs text-red-600">Rejection reason: {partner.verificationNotes}</p>
      )}

      <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-neutral-500">Venue(s)</p>
      {venues === null ? (
        <p className="mt-1 text-xs text-neutral-500">Loading…</p>
      ) : venues.length === 0 ? (
        <p className="mt-1 text-xs text-neutral-500">No venue submitted.</p>
      ) : (
        <div className="mt-1 flex flex-col gap-2">
          {venues.map((v) => (
            <div key={v.id} className="rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-xs text-neutral-900">
              <Field label="Venue name" value={v.name} />
              <Field label="Address" value={v.address} />
              <Field label="Category" value={v.category} />
              <Field label="Contact number" value={v.contactNumber} />
              <Field label="Coordinates" value={`${v.lat.toFixed(4)}, ${v.lng.toFixed(4)}`} />
              <Field label="Operating hours" value={v.operatingHours} />
              {v.description && <Field label="Description" value={v.description} />}
            </div>
          ))}
        </div>
      )}

      <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-neutral-500">Service(s)</p>
      {services === null ? (
        <p className="mt-1 text-xs text-neutral-500">Loading…</p>
      ) : services.length === 0 ? (
        <p className="mt-1 text-xs text-neutral-500">No service submitted.</p>
      ) : (
        <div className="mt-1 flex flex-col gap-2">
          {services.map((s) => (
            <div key={s.id} className="rounded-lg border border-neutral-200 bg-neutral-50 px-3 py-2 text-xs text-neutral-900">
              <Field label="Service name" value={s.name} />
              <Field label="Format" value={s.format} />
              <Field label="Duration" value={`${s.durationMinutes} min`} />
              <Field label="Capacity" value={String(s.capacity)} />
              <Field label="Price" value={`₹${s.price} (${s.pricingUnit.replace("_", " ").toLowerCase()})`} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <p className="mt-0.5 first:mt-0">
      <span className="font-medium text-neutral-500">{label}: </span>
      <span className="text-neutral-900">{value}</span>
    </p>
  );
}

function StatusPill({ status }: { status: string }) {
  const color =
    status === "APPROVED"
      ? "bg-green-100 text-green-700"
      : status === "REJECTED" || status === "SUSPENDED"
        ? "bg-red-100 text-red-700"
        : "bg-amber-100 text-amber-700";
  return <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${color}`}>{status}</span>;
}

export default function PartnersPage() {
  return (
    <AdminGate>
      <PartnersContent />
    </AdminGate>
  );
}
