"use client";

import { motion } from "framer-motion";
import { Reveal } from "./motion/Reveal";
import { IconArrowUpRight, IconCalendar, IconCompass } from "./icons";

const PATHS = [
  {
    icon: IconArrowUpRight,
    title: "Through the provider",
    body: "When a venue or studio already has its own booking system, MaxTime takes you straight there to finish reserving your spot.",
  },
  {
    icon: IconCalendar,
    title: "Through MaxTime",
    body: "When a provider doesn't have a booking flow of their own, you can reserve directly inside MaxTime instead.",
  },
];

export function Booking() {
  return (
    <section className="relative bg-bg-1 py-20 sm:py-28">
      <div className="mx-auto max-w-[1240px] px-6 sm:px-8">
        <Reveal className="mb-14 max-w-[56ch] sm:mb-16">
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">Booking</span>
          <h2 className="mt-4 font-satoshi text-[32px] font-black leading-[1.05] tracking-tight text-text-strong sm:text-[44px]">
            One place to find it. Whichever way you book it.
          </h2>
          <p className="mt-5 text-[16px] leading-relaxed text-muted-2">
            Not every provider works the same way, so MaxTime meets them where they are instead of
            forcing one checkout flow on everyone.
          </p>
        </Reveal>

        <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
          {PATHS.map((path, i) => (
            <motion.div
              key={path.title}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-15% 0px" }}
              transition={{ duration: 0.6, delay: i * 0.1, ease: [0.16, 1, 0.3, 1] }}
              className="rounded-[26px] border border-card-border bg-card-bg p-8 sm:p-10"
            >
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-accent/10 text-accent">
                <path.icon className="h-5 w-5" />
              </span>
              <h3 className="mt-6 font-satoshi text-[22px] font-bold text-text-strong">{path.title}</h3>
              <p className="mt-3 text-[15px] leading-relaxed text-muted-2">{path.body}</p>
            </motion.div>
          ))}
        </div>

        <Reveal delay={0.15} className="mt-8 flex items-start gap-3 text-[13.5px] text-muted">
          <IconCompass className="mt-0.5 h-4 w-4 shrink-0" />
          <p>Availability and booking flow depend on the individual provider.</p>
        </Reveal>
      </div>
    </section>
  );
}
