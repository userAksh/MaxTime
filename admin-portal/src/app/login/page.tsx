"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { signInWithEmailAndPassword } from "firebase/auth";
import { auth } from "@/lib/firebase";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function signIn() {
    setError(null);
    setIsLoading(true);
    try {
      await signInWithEmailAndPassword(auth, email, password);
      router.push("/");
    } catch {
      setError("Invalid email or password.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-sm flex-col justify-center gap-4 px-6">
      <div>
        <h1 className="text-2xl font-bold">MaxTime Admin</h1>
        <p className="mt-1 text-sm text-neutral-500">Ops console — internal access only.</p>
      </div>
      <label className="text-sm font-medium text-neutral-700">Email</label>
      <input
        className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none focus:border-neutral-900"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        type="email"
      />
      <label className="text-sm font-medium text-neutral-700">Password</label>
      <input
        className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none focus:border-neutral-900"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        type="password"
        onKeyDown={(e) => e.key === "Enter" && signIn()}
      />
      <button
        className="rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white disabled:opacity-40"
        disabled={isLoading || !email || !password}
        onClick={signIn}
      >
        {isLoading ? "Signing in…" : "Sign in"}
      </button>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </main>
  );
}
