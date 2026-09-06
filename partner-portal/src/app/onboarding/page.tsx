"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { addDoc, collection, serverTimestamp } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { callFunction } from "@/lib/functions";

type PartnerType = "VENUE" | "INSTRUCTOR" | "BOTH";
type ServiceFormat = "COURT_BOOKING" | "CLASS" | "GROUP_SESSION" | "TRAINER_SESSION";
type PricingUnit = "PER_SLOT" | "PER_PERSON" | "PER_SESSION";

const STEPS = ["Business", "Venue", "Activity", "Availability", "Review"] as const;
const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
function formatTime12h(t: string): string {
  const [hStr, mStr] = t.split(":");
  const h = parseInt(hStr, 10);
  const suffix = h >= 12 ? "PM" : "AM";
  const h12 = h % 12 || 12;
  return `${h12}:${mStr} ${suffix}`;
}

function formatOperatingHours(days: number[], start: string, end: string): string {
  if (days.length === 0) return "Closed";
  const sorted = [...days].sort((a, b) => a - b);
  const dayLabel = sorted.length === 7 ? "Daily" : sorted.map((d) => WEEKDAYS[d]).join(", ");
  return `${dayLabel}, ${formatTime12h(start)}–${formatTime12h(end)}`;
}

const VENUE_CATEGORIES = [
  "Sports complex",
  "Badminton court",
  "Tennis court",
  "Football turf",
  "Cricket ground",
  "Swimming pool",
  "Gym / Fitness center",
  "Yoga studio",
  "Multi-sport arena",
  "Other",
];

// A venue's category (what kind of place it is) usually implies its main activity (what
// customers actually book) — prefilling this removes a redundant re-ask on the very next step.
const ACTIVITY_FROM_VENUE_CATEGORY: Record<string, string> = {
  "Badminton court": "Badminton",
  "Tennis court": "Tennis",
  "Football turf": "Football",
  "Cricket ground": "Cricket",
  "Swimming pool": "Swimming",
  "Gym / Fitness center": "Gym",
  "Yoga studio": "Yoga",
};

const DURATION_OPTIONS = [15, 20, 30, 45, 60, 75, 90, 120];

function flatPricingUnitForFormat(f: ServiceFormat): PricingUnit {
  return f === "COURT_BOOKING" ? "PER_SLOT" : "PER_SESSION";
}

export default function OnboardingPage() {
  const router = useRouter();
  const { user, loading, partnerId, refresh } = useAuth();
  const [step, setStep] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Step 1 — business
  const [type, setType] = useState<PartnerType>("VENUE");
  const [displayName, setDisplayName] = useState("");

  // Step 2 — venue
  const [venueId, setVenueId] = useState<string | null>(null);
  const [venueName, setVenueName] = useState("");
  const [address, setAddress] = useState("");
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [contactNumber, setContactNumber] = useState("");
  const [category, setCategory] = useState("");
  const [description, setDescription] = useState("");
  const [operatingDays, setOperatingDays] = useState<number[]>([0, 1, 2, 3, 4, 5, 6]);
  const [operatingStartTime, setOperatingStartTime] = useState("06:00");
  const [operatingEndTime, setOperatingEndTime] = useState("22:00");
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);

  // Step 3 — activity/service
  const [serviceId, setServiceId] = useState<string | null>(null);
  const [activityName, setActivityName] = useState("");
  const [serviceName, setServiceName] = useState("");
  const [format, setFormat] = useState<ServiceFormat>("COURT_BOOKING");
  const [durationMinutes, setDurationMinutes] = useState("60");
  const [capacity, setCapacity] = useState("4");
  const [price, setPrice] = useState("600");
  const [pricingUnit, setPricingUnit] = useState<PricingUnit>("PER_SLOT");

  // Step 4 — availability
  const [days, setDays] = useState<number[]>([1, 3, 5]);
  const [startTime, setStartTime] = useState("18:00");
  const [endTime, setEndTime] = useState("22:00");
  const [slotDurationMinutes, setSlotDurationMinutes] = useState("60");
  const [advanceBookingDays, setAdvanceBookingDays] = useState("30");
  const [bookingLeadMinutes, setBookingLeadMinutes] = useState("30");
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.push("/login");
  }, [loading, user, router]);

  // "Per slot" vs "per session" are just different words for the same flat-price behavior
  // depending on format — there's no real distinction, so keep the stored value in sync with
  // format automatically instead of asking the partner to pick between two things that mean the
  // same thing (unless they've explicitly chosen "per person", which IS a real behavioral difference).
  useEffect(() => {
    if (pricingUnit !== "PER_PERSON") {
      setPricingUnit(flatPricingUnitForFormat(format));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [format]);

  if (loading || !user) return null;

  async function submitBusiness() {
    setError(null);
    setBusy(true);
    try {
      if (!partnerId) {
        await callFunction<{ type: PartnerType; displayName: string }, { partnerId: string }>(
          "createPartnerAccount"
        )({ type, displayName });
        await refresh();
      }
      setVenueName((prev) => prev || displayName);
      setStep(1);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not create your partner account.");
    } finally {
      setBusy(false);
    }
  }

  function useCurrentLocation() {
    setLocationError(null);
    if (!navigator.geolocation) {
      setLocationError("Your browser doesn't support location — enter coordinates manually.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLat(pos.coords.latitude.toFixed(6));
        setLng(pos.coords.longitude.toFixed(6));
        setLocating(false);
      },
      () => {
        setLocationError("Couldn't get your location — check browser permissions, or enter coordinates manually.");
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }

  async function submitVenue() {
    setError(null);
    setBusy(true);
    try {
      const ref = await addDoc(collection(db, "venues"), {
        partnerId,
        name: venueName,
        address,
        lat: Number(lat) || 0,
        lng: Number(lng) || 0,
        contactNumber,
        category,
        description,
        images: [],
        operatingHours: formatOperatingHours(operatingDays, operatingStartTime, operatingEndTime),
        published: false,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setVenueId(ref.id);
      setActivityName((prev) => prev || ACTIVITY_FROM_VENUE_CATEGORY[category] || "");
      setStep(2);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save your venue.");
    } finally {
      setBusy(false);
    }
  }

  async function submitActivity() {
    setError(null);
    setBusy(true);
    try {
      const activityRef = await addDoc(collection(db, "activities"), {
        category,
        name: activityName,
        description,
        media: [],
        status: "DRAFT",
      });
      const policyRef = await addDoc(collection(db, "cancellation_policies"), {
        partnerId,
        label: "Standard",
        cutoffHoursBeforeStart: 2,
        refundPercentBeforeCutoff: 100,
        refundPercentAfterCutoff: 0,
      });
      const serviceRef = await addDoc(collection(db, "services"), {
        partnerId,
        activityId: activityRef.id,
        venueId,
        resourceId: null,
        instructorPartnerId: null,
        name: serviceName,
        format,
        durationMinutes: Number(durationMinutes),
        capacity: Number(capacity),
        price: Number(price),
        pricingUnit,
        description,
        cancellationPolicyId: policyRef.id,
        published: false,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setServiceId(serviceRef.id);
      setSlotDurationMinutes(durationMinutes);
      setStep(3);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save your activity.");
    } finally {
      setBusy(false);
    }
  }

  async function submitAvailability() {
    setError(null);
    setBusy(true);
    try {
      await addDoc(collection(db, "availability_rules"), {
        partnerId,
        serviceId,
        resourceId: null,
        daysOfWeek: days,
        startTime,
        endTime,
        slotDurationMinutes: Number(slotDurationMinutes),
        capacity: Number(capacity),
        price: Number(price),
        bookingLeadMinutes: Number(bookingLeadMinutes),
        advanceBookingDays: Number(advanceBookingDays),
        active: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      setStep(4);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save your availability.");
    } finally {
      setBusy(false);
    }
  }

  function toggleOperatingDay(d: number) {
    setOperatingDays((prev) => (prev.includes(d) ? prev.filter((x) => x !== d) : [...prev, d].sort()));
  }

  function toggleDay(d: number) {
    setDays((prev) => (prev.includes(d) ? prev.filter((x) => x !== d) : [...prev, d].sort()));
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-lg flex-col gap-6 px-6 py-10">
      <div>
        <h1 className="text-xl font-bold">Set up your MaxTime partner account</h1>
        <div className="mt-3 flex gap-1.5">
          {STEPS.map((label, i) => (
            <div
              key={label}
              className={`h-1.5 flex-1 rounded-full ${i <= step ? "bg-neutral-900" : "bg-neutral-200"}`}
            />
          ))}
        </div>
        <p className="mt-2 text-xs font-medium text-neutral-500">
          Step {step + 1} of {STEPS.length} — {STEPS[step]}
        </p>
      </div>

      {step === 0 && (
        <section className="flex flex-col gap-3">
          <Label>What are you onboarding?</Label>
          <div className="flex gap-2">
            {(["VENUE", "INSTRUCTOR", "BOTH"] as PartnerType[]).map((t) => (
              <button
                key={t}
                className={`flex-1 rounded-lg border px-3 py-2 text-sm font-medium ${type === t ? "border-neutral-900 bg-neutral-900 text-white" : "border-neutral-300"}`}
                onClick={() => setType(t)}
              >
                {t === "VENUE" ? "Venue" : t === "INSTRUCTOR" ? "Instructor" : "Both"}
              </button>
            ))}
          </div>
          <Label>Business / display name</Label>
          <Input value={displayName} onChange={setDisplayName} placeholder="New Greenfield Sports Facility" />
          <PrimaryButton disabled={busy || !displayName} onClick={submitBusiness}>
            {busy ? "Saving…" : "Continue"}
          </PrimaryButton>
        </section>
      )}

      {step === 1 && (
        <section className="flex flex-col gap-3">
          <Label>Venue name</Label>
          <Input value={venueName} onChange={setVenueName} placeholder="Saket venue" />
          <Label>Address</Label>
          <Input value={address} onChange={setAddress} placeholder="Full address" />
          <Label>Location</Label>
          <button
            type="button"
            className="self-start rounded-lg border border-neutral-300 px-3 py-2 text-sm font-semibold text-neutral-500 disabled:opacity-40"
            disabled={locating}
            onClick={useCurrentLocation}
          >
            {locating ? "Locating…" : "📍 Use my current location"}
          </button>
          {locationError && <p className="text-xs text-red-600">{locationError}</p>}
          <div className="flex items-center gap-3">
            <div className="h-px flex-1 bg-neutral-300" />
            <span className="text-xs font-semibold text-neutral-500">OR</span>
            <div className="h-px flex-1 bg-neutral-300" />
          </div>
          <p className="-mt-2 text-xs text-neutral-500">Enter coordinates manually.</p>
          <div className="flex gap-2">
            <div className="flex-1">
              <Label>Latitude</Label>
              <Input value={lat} onChange={setLat} placeholder="28.5245" />
            </div>
            <div className="flex-1">
              <Label>Longitude</Label>
              <Input value={lng} onChange={setLng} placeholder="77.2066" />
            </div>
          </div>
          <Label>Contact number</Label>
          <Input
            value={contactNumber}
            onChange={(v) => setContactNumber(v.replace(/\D/g, "").slice(0, 10))}
            placeholder="98765 43210"
            type="tel"
            maxLength={10}
          />
          <Label>Category</Label>
          <select
            className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <option value="">Select a category</option>
            {VENUE_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <Label>Operating days</Label>
          <div className="flex flex-wrap gap-2">
            {WEEKDAYS.map((d, i) => (
              <button
                key={d}
                type="button"
                className={`rounded-full border px-3 py-1.5 text-xs font-medium ${
                  operatingDays.includes(i) ? "border-neutral-900 bg-neutral-900 text-white" : "border-neutral-300"
                }`}
                onClick={() => toggleOperatingDay(i)}
              >
                {d}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            <div className="flex-1">
              <Label>Opens at</Label>
              <input
                type="time"
                className="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
                value={operatingStartTime}
                onChange={(e) => setOperatingStartTime(e.target.value)}
              />
            </div>
            <div className="flex-1">
              <Label>Closes at</Label>
              <input
                type="time"
                className="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
                value={operatingEndTime}
                onChange={(e) => setOperatingEndTime(e.target.value)}
              />
            </div>
          </div>
          <Label>Short description</Label>
          <Input value={description} onChange={setDescription} placeholder="What makes this venue great?" />
          <PrimaryButton disabled={busy || !venueName || !address} onClick={submitVenue}>
            {busy ? "Saving…" : "Continue"}
          </PrimaryButton>
        </section>
      )}

      {step === 2 && (
        <section className="flex flex-col gap-3">
          <Label>Activity category</Label>
          <Input value={activityName} onChange={setActivityName} placeholder="Badminton" />
          <Label>Service name (what a customer books)</Label>
          <Input value={serviceName} onChange={setServiceName} placeholder="Badminton Court — 60 Minutes" />
          <Label>Format</Label>
          <select
            className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
            value={format}
            onChange={(e) => setFormat(e.target.value as ServiceFormat)}
          >
            <option value="COURT_BOOKING">Court booking</option>
            <option value="CLASS">Class</option>
            <option value="GROUP_SESSION">Group session</option>
            <option value="TRAINER_SESSION">Trainer session</option>
          </select>
          <div className="flex gap-2">
            <div className="flex-1">
              <Label>Duration</Label>
              <select
                className="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(e.target.value)}
              >
                {DURATION_OPTIONS.map((m) => (
                  <option key={m} value={m}>
                    {m} min
                  </option>
                ))}
              </select>
            </div>
            <div className="flex-1">
              <Label>Capacity</Label>
              <Input value={capacity} onChange={setCapacity} type="number" />
            </div>
          </div>
          <div className="flex gap-2">
            <div className="flex-1">
              <Label>Price (₹)</Label>
              <Input value={price} onChange={setPrice} type="number" />
            </div>
            <div className="flex-1">
              <Label>Pricing</Label>
              <select
                className="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
                value={pricingUnit === "PER_PERSON" ? "PER_PERSON" : "FLAT"}
                onChange={(e) =>
                  setPricingUnit(e.target.value === "PER_PERSON" ? "PER_PERSON" : flatPricingUnitForFormat(format))
                }
              >
                <option value="FLAT">Flat price (any group size)</option>
                <option value="PER_PERSON">Per person (price × participants)</option>
              </select>
            </div>
          </div>
          <PrimaryButton disabled={busy || !activityName || !serviceName} onClick={submitActivity}>
            {busy ? "Saving…" : "Continue"}
          </PrimaryButton>
        </section>
      )}

      {step === 3 && (
        <section className="flex flex-col gap-3">
          <Label>Days available</Label>
          <div className="flex flex-wrap gap-2">
            {WEEKDAYS.map((d, i) => (
              <button
                key={d}
                className={`rounded-full border px-3 py-1.5 text-xs font-medium ${days.includes(i) ? "border-neutral-900 bg-neutral-900 text-white" : "border-neutral-300"}`}
                onClick={() => toggleDay(i)}
              >
                {d}
              </button>
            ))}
          </div>
          <div className="flex gap-2">
            <div className="flex-1">
              <Label>Start time</Label>
              <input
                type="time"
                className="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
              />
            </div>
            <div className="flex-1">
              <Label>End time</Label>
              <input
                type="time"
                className="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
              />
            </div>
          </div>
          <Label>Slot duration</Label>
          <select
            className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
            value={slotDurationMinutes}
            onChange={(e) => setSlotDurationMinutes(e.target.value)}
          >
            {DURATION_OPTIONS.map((m) => (
              <option key={m} value={m}>
                {m} min
              </option>
            ))}
          </select>
          <div className="flex gap-2">
            <div className="flex-1">
              <Label>Booking closes (min before start)</Label>
              <Input value={bookingLeadMinutes} onChange={setBookingLeadMinutes} type="number" />
            </div>
            <div className="flex-1">
              <Label>Bookable up to (days ahead)</Label>
              <Input value={advanceBookingDays} onChange={setAdvanceBookingDays} type="number" />
            </div>
          </div>
          <PrimaryButton disabled={busy || days.length === 0} onClick={submitAvailability}>
            {busy ? "Saving…" : "Continue"}
          </PrimaryButton>
        </section>
      )}

      {step === 4 && (
        <section className="flex flex-col gap-3">
          <div className="rounded-lg border border-neutral-200 bg-white p-4 text-sm">
            <p className="font-semibold text-neutral-900">{displayName}</p>
            <p className="mt-1 text-neutral-500">{venueName}, {address}</p>
            <p className="mt-1 text-neutral-500">{serviceName} · ₹{price} · {durationMinutes} min · cap {capacity}</p>
            <p className="mt-1 text-neutral-500">
              {days.map((d) => WEEKDAYS[d]).join("/")} · {startTime}–{endTime}
            </p>
          </div>
          {submitted ? (
            <div className="rounded-lg bg-amber-50 p-4 text-sm text-amber-800">
              Submitted! Your account is <strong>pending MaxTime review</strong>. We&apos;ll notify you once
              it&apos;s approved and live.
            </div>
          ) : (
            <PrimaryButton
              disabled={busy}
              onClick={async () => {
                setSubmitted(true);
                setTimeout(() => router.push("/"), 1200);
              }}
            >
              Submit for review
            </PrimaryButton>
          )}
        </section>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}
    </main>
  );
}

function Label({ children }: { children: React.ReactNode }) {
  return <label className="text-xs font-semibold uppercase tracking-wide text-neutral-500">{children}</label>;
}

function Input({
  value,
  onChange,
  placeholder,
  type = "text",
  maxLength,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  type?: string;
  maxLength?: number;
}) {
  return (
    <input
      className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none focus:border-neutral-900"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      type={type}
      maxLength={maxLength}
    />
  );
}

function PrimaryButton({
  children,
  onClick,
  disabled,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      className="rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white disabled:opacity-40"
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}
