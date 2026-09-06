"use client";

import { useEffect, useState } from "react";
import { collection, onSnapshot, query, where } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { usePartner } from "@/lib/use-partner";
import { callFunction } from "@/lib/functions";
import { PartnerGate } from "@/components/PartnerGate";

interface StaffRow {
  id: string;
  role: string;
  status: string;
  invitedPhone: string | null;
}

function ProfileContent() {
  const { partnerId, partnerRole } = useAuth();
  const { partner } = usePartner();
  const [staff, setStaff] = useState<StaffRow[]>([]);
  const [invitePhone, setInvitePhone] = useState("");
  const [inviteRole, setInviteRole] = useState<"MANAGER" | "INSTRUCTOR">("MANAGER");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const canInvite = partnerRole === "OWNER" || partnerRole === "MANAGER";

  useEffect(() => {
    if (!partnerId) return;
    return onSnapshot(query(collection(db, "partner_users"), where("partnerId", "==", partnerId)), (snap) => {
      setStaff(snap.docs.map((d) => ({ id: d.id, ...d.data() }) as StaffRow));
    });
  }, [partnerId]);

  async function invite() {
    setError(null);
    setBusy(true);
    try {
      await callFunction<{ phone: string; role: string }, { partnerUserId: string }>("invitePartnerStaff")({
        phone: invitePhone,
        role: inviteRole,
      });
      setInvitePhone("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not send invite.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1 className="text-lg font-bold">Profile</h1>
      <div className="mt-4 rounded-lg border border-neutral-200 bg-white p-4 text-sm">
        <p className="font-semibold text-neutral-900">{partner?.displayName}</p>
        <p className="mt-1 text-neutral-500">{partner?.type} · {partner?.status}</p>
      </div>

      <section className="mt-6">
        <h2 className="mb-2 text-sm font-semibold text-neutral-500">Staff</h2>
        <div className="flex flex-col gap-2">
          {staff.map((s) => (
            <div key={s.id} className="flex items-center justify-between rounded-lg border border-neutral-200 bg-white px-4 py-2.5 text-sm">
              <p className="text-neutral-900">{s.invitedPhone ?? s.id} · {s.role}</p>
              <span className="rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">{s.status}</span>
            </div>
          ))}
        </div>

        {canInvite && (
          <div className="mt-3 flex flex-col gap-2 rounded-lg border border-dashed border-neutral-300 p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-neutral-500">Invite staff</p>
            <div className="flex gap-2">
              <input
                className="flex-1 rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900"
                placeholder="Phone (+91XXXXXXXXXX)"
                value={invitePhone}
                onChange={(e) => setInvitePhone(e.target.value)}
              />
              <select
                className="rounded-lg border border-neutral-300 bg-white px-2 py-2 text-sm text-neutral-900"
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value as "MANAGER" | "INSTRUCTOR")}
              >
                <option value="MANAGER">Manager</option>
                <option value="INSTRUCTOR">Instructor</option>
              </select>
            </div>
            <button
              disabled={busy || !invitePhone}
              className="rounded-lg bg-neutral-900 py-2 text-sm font-semibold text-white disabled:opacity-40"
              onClick={invite}
            >
              {busy ? "Inviting…" : "Send invite"}
            </button>
            {error && <p className="text-sm text-red-600">{error}</p>}
          </div>
        )}
      </section>
    </>
  );
}

export default function ProfilePage() {
  return (
    <PartnerGate>
      <ProfileContent />
    </PartnerGate>
  );
}
