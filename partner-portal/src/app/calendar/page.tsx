"use client";

import { useEffect, useState } from "react";
import { collection, onSnapshot, orderBy, query, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { callFunction } from "@/lib/functions";
import { PartnerGate } from "@/components/PartnerGate";

interface RuleRow {
  id: string;
  serviceId: string;
  daysOfWeek: number[];
  startTime: string;
  endTime: string;
  active: boolean;
}

interface SlotRow {
  id: string;
  date: string;
  startTime: string;
  endTime: string;
  capacity: number;
  remaining: number;
  status: string;
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/**
 * §12 — a simplified, list-based stand-in for the full visual Calendar grid: grouped by date,
 * shows each slot's real remaining/capacity and status. The full drag/tap grid UI is a follow-up,
 * not something this pass fakes with static data — everything shown here is live.
 */
function CalendarContent() {
  const { partnerId } = useAuth();
  const [rules, setRules] = useState<RuleRow[]>([]);
  const [slots, setSlots] = useState<SlotRow[]>([]);
  const [busyRuleId, setBusyRuleId] = useState<string | null>(null);
  const [busySlotId, setBusySlotId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!partnerId) return;
    const rulesUnsub = onSnapshot(
      query(collection(db, "availability_rules"), where("partnerId", "==", partnerId)),
      (snap) => setRules(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as RuleRow))
    );
    const slotsUnsub = onSnapshot(
      query(collection(db, "inventory_slots"), where("partnerId", "==", partnerId), orderBy("date")),
      (snap) => setSlots(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as SlotRow))
    );
    return () => {
      rulesUnsub();
      slotsUnsub();
    };
  }, [partnerId]);

  async function generate(ruleId: string) {
    setError(null);
    setBusyRuleId(ruleId);
    try {
      await callFunction<{ ruleId: string }, { created: number }>("generateInventoryFromRule")({ ruleId });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not generate inventory.");
    } finally {
      setBusyRuleId(null);
    }
  }

  async function block(slotId: string) {
    const reason = window.prompt("Reason for blocking this slot?");
    if (!reason) return;
    setError(null);
    setBusySlotId(slotId);
    try {
      await callFunction<{ slotId: string; reason: string }, { ok: boolean }>("blockInventorySlot")({
        slotId,
        reason,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not block this slot — it may already have bookings.");
    } finally {
      setBusySlotId(null);
    }
  }

  const grouped = slots.reduce<Record<string, SlotRow[]>>((acc, s) => {
    (acc[s.date] ??= []).push(s);
    return acc;
  }, {});

  return (
    <>
      <h1 className="text-lg font-bold">Calendar</h1>

      <section className="mt-4">
        <h2 className="mb-2 text-sm font-semibold text-neutral-500">Availability rules</h2>
        {rules.length === 0 ? (
          <p className="rounded-lg border border-dashed border-neutral-300 px-4 py-6 text-center text-sm text-neutral-500">
            No availability yet. Add your operating hours during onboarding to create slots.
          </p>
        ) : (
          <div className="flex flex-col gap-2">
            {rules.map((r) => (
              <div key={r.id} className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white px-4 py-3 text-sm">
                <p className="text-neutral-900">
                  {r.daysOfWeek.map((d) => WEEKDAYS[d]).join("/")} · {r.startTime}–{r.endTime}
                </p>
                <button
                  disabled={busyRuleId === r.id}
                  className="rounded-md bg-neutral-900 px-2.5 py-1 text-xs font-semibold text-white disabled:opacity-40"
                  onClick={() => generate(r.id)}
                >
                  {busyRuleId === r.id ? "Generating…" : "Generate slots"}
                </button>
              </div>
            ))}
          </div>
        )}
      </section>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-semibold text-neutral-500">Upcoming slots</h2>
        {Object.keys(grouped).length === 0 ? (
          <p className="rounded-lg border border-dashed border-neutral-300 px-4 py-6 text-center text-sm text-neutral-500">
            No slots yet — generate some from a rule above.
          </p>
        ) : (
          <div className="flex flex-col gap-4">
            {Object.entries(grouped).map(([date, daySlots]) => (
              <div key={date}>
                <p className="mb-1 text-xs font-semibold text-neutral-400">{date}</p>
                <div className="flex flex-col gap-1.5">
                  {daySlots.map((s) => (
                    <div key={s.id} className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white px-4 py-2.5 text-sm">
                      <p className="text-neutral-900">
                        {s.startTime}–{s.endTime} · {s.remaining}/{s.capacity} open
                      </p>
                      <div className="flex items-center gap-2">
                        <StatusPill status={s.status} />
                        {s.status === "AVAILABLE" && s.remaining === s.capacity && (
                          <button
                            disabled={busySlotId === s.id}
                            className="rounded-md border border-neutral-300 px-2.5 py-1 text-xs font-semibold text-neutral-900 disabled:opacity-40"
                            onClick={() => block(s.id)}
                          >
                            Block
                          </button>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </>
  );
}

function StatusPill({ status }: { status: string }) {
  return <span className="rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">{status}</span>;
}

export default function CalendarPage() {
  return (
    <PartnerGate>
      <CalendarContent />
    </PartnerGate>
  );
}
