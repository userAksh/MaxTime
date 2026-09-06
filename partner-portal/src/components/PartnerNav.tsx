"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { signOut } from "firebase/auth";
import { auth } from "@/lib/firebase";

const TABS = [
  { href: "/", label: "Home" },
  { href: "/bookings", label: "Bookings" },
  { href: "/calendar", label: "Calendar" },
  { href: "/activities", label: "Activities" },
  { href: "/payouts", label: "Payouts" },
  { href: "/profile", label: "Profile" },
];

/** §44 — "Recommended Partner Home Navigation." A simple top nav works for both mobile browsers
 *  and desktop per §43/§0's "responsive web, not a dedicated app" decision — no separate mobile
 *  nav component needed, it just wraps. */
export function PartnerNav() {
  const pathname = usePathname();
  const router = useRouter();

  return (
    <nav className="sticky top-0 z-10 flex flex-wrap items-center gap-1 border-b border-neutral-200 bg-white px-4 py-2">
      <span className="mr-3 text-sm font-bold text-neutral-900">MaxTime Partner</span>
      {TABS.map((tab) => (
        <Link
          key={tab.href}
          href={tab.href}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            pathname === tab.href ? "bg-neutral-900 text-white" : "text-neutral-600 hover:bg-neutral-100"
          }`}
        >
          {tab.label}
        </Link>
      ))}
      <button
        className="ml-auto text-sm text-neutral-500 underline"
        onClick={async () => {
          await signOut(auth);
          router.push("/login");
        }}
      >
        Sign out
      </button>
    </nav>
  );
}
