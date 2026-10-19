"use client";

import { useState, type FormEvent } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { submitPartnerLead } from "@/lib/partner-lead";
import { IconCheck } from "./icons";

const CATEGORIES = [
  "Sports & fitness",
  "Creative workshops",
  "Dance & music",
  "Classes & learning",
  "Social & outdoors",
  "Wellness",
  "Other",
];

const CONTACT_METHODS = ["Phone", "Email", "WhatsApp"];

type Status = "idle" | "submitting" | "success" | "error";

const inputClass =
  "w-full rounded-xl border border-card-border bg-bg-1 px-4 py-3 text-[14.5px] text-text-strong outline-none transition-colors placeholder:text-muted focus:border-accent/50";

const labelClass = "mb-1.5 block text-[12.5px] font-semibold text-muted-2";

export function PartnerForm() {
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);

    const name = String(data.get("name") ?? "").trim();
    const phone = String(data.get("phone") ?? "").trim();
    const businessName = String(data.get("businessName") ?? "").trim();
    const city = String(data.get("city") ?? "").trim();
    const category = String(data.get("category") ?? "").trim();

    if (!name || !phone || !businessName || !city || !category) {
      setError("Please fill in all required fields.");
      return;
    }

    setStatus("submitting");
    setError(null);

    try {
      await submitPartnerLead({
        name,
        phone,
        businessName,
        city,
        category,
        email: String(data.get("email") ?? "").trim() || undefined,
        websiteOrSocial: String(data.get("websiteOrSocial") ?? "").trim() || undefined,
        preferredContactMethod: String(data.get("preferredContactMethod") ?? "").trim() || undefined,
        notes: String(data.get("notes") ?? "").trim() || undefined,
      });
      setStatus("success");
      form.reset();
    } catch (err) {
      console.error(err);
      setStatus("error");
      setError("Something went wrong sending your enquiry. Please try again in a moment.");
    }
  }

  if (status === "success") {
    return (
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col items-center justify-center rounded-[26px] border border-accent/30 bg-accent/[0.06] px-8 py-16 text-center"
      >
        <span className="flex h-14 w-14 items-center justify-center rounded-full bg-accent/15 border border-accent/30">
          <IconCheck className="h-6 w-6 text-accent" />
        </span>
        <p className="mt-5 font-satoshi text-[20px] font-bold text-text-strong">Enquiry sent</p>
        <p className="mt-2 max-w-[38ch] text-[14.5px] leading-relaxed text-muted-2">
          Thanks for reaching out — the MaxTime partnerships team will get back to you shortly.
        </p>
        <button
          type="button"
          onClick={() => setStatus("idle")}
          className="mt-6 text-[13.5px] font-bold text-accent hover:underline"
        >
          Submit another enquiry
        </button>
      </motion.div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="rounded-[26px] border border-card-border bg-card-bg p-6 sm:p-9">
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        <div>
          <label htmlFor="name" className={labelClass}>
            Your name<span className="text-accent"> *</span>
          </label>
          <input id="name" name="name" type="text" required className={inputClass} autoComplete="name" />
        </div>
        <div>
          <label htmlFor="phone" className={labelClass}>
            Phone number<span className="text-accent"> *</span>
          </label>
          <input id="phone" name="phone" type="tel" required className={inputClass} autoComplete="tel" />
        </div>
        <div>
          <label htmlFor="email" className={labelClass}>
            Email
          </label>
          <input id="email" name="email" type="email" className={inputClass} autoComplete="email" />
        </div>
        <div>
          <label htmlFor="businessName" className={labelClass}>
            Business / venue name<span className="text-accent"> *</span>
          </label>
          <input id="businessName" name="businessName" type="text" required className={inputClass} />
        </div>
        <div>
          <label htmlFor="city" className={labelClass}>
            City<span className="text-accent"> *</span>
          </label>
          <input id="city" name="city" type="text" required className={inputClass} />
        </div>
        <div>
          <label htmlFor="category" className={labelClass}>
            Category<span className="text-accent"> *</span>
          </label>
          <select id="category" name="category" required defaultValue="" className={inputClass}>
            <option value="" disabled>
              Select a category
            </option>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="websiteOrSocial" className={labelClass}>
            Website or social link
          </label>
          <input id="websiteOrSocial" name="websiteOrSocial" type="text" className={inputClass} />
        </div>
        <div>
          <label htmlFor="preferredContactMethod" className={labelClass}>
            Preferred contact method
          </label>
          <select id="preferredContactMethod" name="preferredContactMethod" defaultValue="" className={inputClass}>
            <option value="">No preference</option>
            {CONTACT_METHODS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </div>
        <div className="sm:col-span-2">
          <label htmlFor="notes" className={labelClass}>
            Anything else we should know?
          </label>
          <textarea id="notes" name="notes" rows={4} className={`${inputClass} resize-none`} />
        </div>
      </div>

      <AnimatePresence>
        {error && (
          <motion.p
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            role="alert"
            className="mt-5 rounded-xl border border-red/30 bg-red/10 px-4 py-3 text-[13.5px] text-red"
          >
            {error}
          </motion.p>
        )}
      </AnimatePresence>

      <button
        type="submit"
        disabled={status === "submitting"}
        className="mt-7 inline-flex w-full items-center justify-center rounded-full bg-accent px-7 py-4 text-[15px] font-bold text-on-accent transition-transform duration-200 hover:scale-[1.01] active:scale-[0.99] disabled:cursor-wait disabled:opacity-60 sm:w-auto"
      >
        {status === "submitting" ? "Sending…" : "Send enquiry"}
      </button>
    </form>
  );
}
