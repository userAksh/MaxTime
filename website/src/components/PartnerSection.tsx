import { Reveal } from "./motion/Reveal";
import { PartnerForm } from "./PartnerForm";

export function PartnerSection() {
  return (
    <section id="partners" className="relative bg-bg-1 py-20 sm:py-28">
      <div className="mx-auto grid max-w-[1240px] grid-cols-1 gap-14 px-6 sm:px-8 md:grid-cols-5 md:gap-16">
        <Reveal className="md:col-span-2">
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">For partners</span>
          <h2 className="mt-4 font-satoshi text-[32px] font-black leading-[1.05] tracking-tight text-text-strong sm:text-[40px]">
            Run a venue or studio? Let&apos;s talk.
          </h2>
          <p className="mt-5 max-w-[42ch] text-[15.5px] leading-relaxed text-muted-2">
            MaxTime works with activity providers and venues to help more people discover what
            they offer — from a single class to a full calendar of sessions. Tell us about your
            business and the team will get back to you.
          </p>
        </Reveal>

        <div className="md:col-span-3">
          <PartnerForm />
        </div>
      </div>
    </section>
  );
}
