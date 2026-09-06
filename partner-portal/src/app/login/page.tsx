"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmationResult, RecaptchaVerifier, signInWithPhoneNumber } from "firebase/auth";
import { auth } from "@/lib/firebase";

export default function LoginPage() {
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [confirmation, setConfirmation] = useState<ConfirmationResult | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const recaptchaRef = useRef<RecaptchaVerifier | null>(null);

  async function sendOtp() {
    setError(null);
    setIsLoading(true);
    try {
      if (!recaptchaRef.current) {
        recaptchaRef.current = new RecaptchaVerifier(auth, "recaptcha-container", { size: "invisible" });
      }
      const result = await signInWithPhoneNumber(auth, `+91${phone}`, recaptchaRef.current);
      setConfirmation(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not send OTP. Try again.");
    } finally {
      setIsLoading(false);
    }
  }

  async function verifyOtp() {
    if (!confirmation) return;
    setError(null);
    setIsLoading(true);
    try {
      await confirmation.confirm(otp);
      router.push("/");
    } catch {
      setError("That code didn't work. Check it and try again.");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-sm flex-col justify-center gap-5 px-6">
      <div>
        <h1 className="text-2xl font-bold">MaxTime Partner Portal</h1>
        <p className="mt-1 text-sm text-neutral-500">Onboard, manage, and get paid — in one place.</p>
      </div>

      {!confirmation ? (
        <div className="flex flex-col gap-3">
          <label className="text-sm font-medium text-neutral-700">Mobile number</label>
          <div className="flex gap-2">
            <span className="flex items-center rounded-lg border border-neutral-300 bg-white px-3 text-sm text-neutral-900">+91</span>
            <input
              className="flex-1 rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none focus:border-neutral-900"
              value={phone}
              onChange={(e) => setPhone(e.target.value.replace(/\D/g, ""))}
              placeholder="98765 43210"
              inputMode="numeric"
              maxLength={10}
            />
          </div>
          <button
            className="rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white disabled:opacity-40"
            disabled={isLoading || phone.length !== 10}
            onClick={sendOtp}
          >
            {isLoading ? "Sending…" : "Send OTP"}
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <label className="text-sm font-medium text-neutral-700">Enter the code sent to +91 {phone}</label>
          <input
            className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm text-neutral-900 outline-none focus:border-neutral-900"
            value={otp}
            onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))}
            placeholder="6-digit code"
            inputMode="numeric"
            maxLength={6}
          />
          <button
            className="rounded-lg bg-neutral-900 py-2.5 text-sm font-semibold text-white disabled:opacity-40"
            disabled={isLoading || otp.length !== 6}
            onClick={verifyOtp}
          >
            {isLoading ? "Verifying…" : "Verify"}
          </button>
          <button className="text-sm text-neutral-500 underline" onClick={() => setConfirmation(null)}>
            Use a different number
          </button>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}
      <div id="recaptcha-container" />
    </main>
  );
}
