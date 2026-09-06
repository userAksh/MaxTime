"use client";

import { useEffect, useState } from "react";
import { collection, doc, onSnapshot, query, updateDoc, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { PartnerGate } from "@/components/PartnerGate";

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

interface VenueRow {
  id: string;
  name: string;
  published: boolean;
}

/** §6 step 14 — "After approval, publish or activate inventory." Nothing a partner creates during
 *  onboarding goes live on its own; this is where they actually flip the switch, for both their
 *  venue and each service, once they're happy with what they've set up. */
function ActivitiesContent() {
  const { partnerId } = useAuth();
  const [venues, setVenues] = useState<VenueRow[]>([]);
  const [services, setServices] = useState<ServiceRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!partnerId) return;
    const unsubVenues = onSnapshot(query(collection(db, "venues"), where("partnerId", "==", partnerId)), (snap) => {
      setVenues(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as VenueRow));
    });
    const unsubServices = onSnapshot(query(collection(db, "services"), where("partnerId", "==", partnerId)), (snap) => {
      setServices(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as ServiceRow));
    });
    return () => {
      unsubVenues();
      unsubServices();
    };
  }, [partnerId]);

  async function togglePublish(collectionName: "venues" | "services", id: string, next: boolean) {
    setError(null);
    try {
      await updateDoc(doc(db, collectionName, id), { published: next });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not update publish status.");
    }
  }

  return (
    <>
      <h1 className="text-lg font-bold">Activities</h1>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      <section className="mt-4">
        <h2 className="mb-2 text-sm font-semibold text-neutral-500">Venues</h2>
        <div className="flex flex-col gap-2">
          {venues.map((v) => (
            <PublishRow key={v.id} label={v.name} published={v.published} onToggle={(next) => togglePublish("venues", v.id, next)} />
          ))}
        </div>
      </section>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-semibold text-neutral-500">Services</h2>
        {services.length === 0 ? (
          <p className="rounded-lg border border-dashed border-neutral-300 px-4 py-6 text-center text-sm text-neutral-500">
            No activities yet.
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {services.map((s) => (
              <div key={s.id} className="rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
                <div className="flex items-center justify-between">
                  <p className="font-medium text-neutral-900">{s.name}</p>
                  <PublishToggle published={s.published} onToggle={(next) => togglePublish("services", s.id, next)} />
                </div>
                <p className="mt-1 text-xs text-neutral-500">
                  {s.format} · {s.durationMinutes} min · capacity {s.capacity} · ₹{s.price} (
                  {s.pricingUnit.replace("_", " ").toLowerCase()})
                </p>
              </div>
            ))}
          </div>
        )}
      </section>
    </>
  );
}

function PublishRow({ label, published, onToggle }: { label: string; published: boolean; onToggle: (next: boolean) => void }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
      <p className="font-medium text-neutral-900">{label}</p>
      <PublishToggle published={published} onToggle={onToggle} />
    </div>
  );
}

function PublishToggle({ published, onToggle }: { published: boolean; onToggle: (next: boolean) => void }) {
  return (
    <button
      className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
        published ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
      }`}
      onClick={() => onToggle(!published)}
    >
      {published ? "Published — unpublish" : "Awaiting publish — go live"}
    </button>
  );
}

export default function ActivitiesPage() {
  return (
    <PartnerGate>
      <ActivitiesContent />
    </PartnerGate>
  );
}
