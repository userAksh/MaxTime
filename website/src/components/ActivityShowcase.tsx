"use client";

import { motion } from "framer-motion";
import { activities } from "@/lib/activities";
import { Reveal } from "./motion/Reveal";

// Asymmetric span pattern for an editorial, non-grid feel across 8 tiles.
const SPANS = [
  "md:col-span-7 md:row-span-2",
  "md:col-span-5 md:row-span-1",
  "md:col-span-5 md:row-span-1",
  "md:col-span-4 md:row-span-1",
  "md:col-span-4 md:row-span-1",
  "md:col-span-4 md:row-span-1",
  "md:col-span-6 md:row-span-1",
  "md:col-span-6 md:row-span-1",
];

export function ActivityShowcase() {
  return (
    <section id="discover" className="relative bg-bg-1 py-20 sm:py-28">
      <div className="mx-auto max-w-[1240px] px-6 sm:px-8">
        <Reveal className="mb-14 flex max-w-[60ch] flex-col gap-4 sm:mb-20">
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">Discover</span>
          <h2 className="font-satoshi text-[32px] font-black leading-[1.05] tracking-tight text-text-strong sm:text-[44px]">
            Every kind of thing worth doing.
          </h2>
          <p className="text-[16px] leading-relaxed text-muted-2">
            No single scene to belong to. Browse by what you&apos;re into, mix it up week to week, and
            try something new whenever you feel like it.
          </p>
        </Reveal>

        {/* Jewel-tone cards with a bleeding emoji — the same treatment as the app's own "Popular
            near you" promo carousel (HomeScreen.kt's ActivityPromoCard), not an invented style. */}
        <div className="grid grid-cols-1 gap-3 sm:gap-4 md:auto-rows-[160px] md:grid-cols-12">
          {activities.map((activity, i) => {
            const featured = i === 0;
            return (
              <motion.div
                key={activity.id}
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-10% 0px -10% 0px" }}
                transition={{ duration: 0.6, delay: (i % 4) * 0.07, ease: [0.16, 1, 0.3, 1] }}
                whileHover={{ scale: 1.015 }}
                className={`group relative overflow-hidden rounded-[22px] border border-white/[0.08] ${SPANS[i] ?? ""}`}
                style={{ background: activity.color }}
              >
                <div className="absolute inset-0 bg-gradient-to-t from-black/35 via-transparent to-transparent" />
                {/* Fully contained within the card — no negative offsets, so figure emoji
                    (runner, climber, yoga pose) never lose a limb to the rounded-corner clip.
                    The featured tile only gets a bigger emoji from md: up, where its span is
                    actually larger — below that (grid-cols-1) every tile is the same width, so
                    scaling just this one up would make it look oversized next to the rest. */}
                <span
                  aria-hidden
                  className={`pointer-events-none absolute select-none leading-none opacity-90 transition-transform duration-500 group-hover:scale-110 ${
                    featured
                      ? "bottom-4 right-4 text-[60px] sm:text-[70px] md:bottom-5 md:right-5 md:text-[110px] lg:text-[140px]"
                      : "bottom-4 right-4 text-[60px] sm:text-[70px]"
                  }`}
                >
                  {activity.emoji}
                </span>
                <div
                  className={`relative flex h-full min-h-[180px] flex-col justify-end p-5 sm:p-6 ${
                    featured ? "md:p-8" : ""
                  }`}
                >
                  <p className="text-[10.5px] font-bold uppercase tracking-[0.1em] text-white/75">
                    {activity.category}
                  </p>
                  <p
                    className={`mt-1 font-satoshi font-extrabold leading-tight text-white ${
                      featured ? "text-[22px] sm:text-[28px]" : "text-[17px] sm:text-[19px]"
                    }`}
                  >
                    {activity.label}
                  </p>
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
