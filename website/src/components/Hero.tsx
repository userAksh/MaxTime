"use client";

import { useRef } from "react";
import { motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { activities } from "@/lib/activities";

// Kept strictly in the left/right margins — never crossing the headline/CTA column (roughly
// 13%–62% of the viewport width) so the floating field never visually collides with the nav,
// the type, or the buttons underneath it. Anchored from whichever side they sit on (not always
// "left") so a chip's text-driven width grows inward, toward empty space, never toward the edge.
const HERO_TILES = [
  { activity: activities[0], top: "16%", side: "left" as const, offset: "2%", rot: -6, delay: 0.1 },
  { activity: activities[2], top: "48%", side: "left" as const, offset: "0%", rot: 4, delay: 0.3 },
  { activity: activities[3], top: "80%", side: "left" as const, offset: "3%", rot: -5, delay: 0.46 },
  { activity: activities[7], top: "14%", side: "right" as const, offset: "4%", rot: 6, delay: 0.38 },
  { activity: activities[5], top: "46%", side: "right" as const, offset: "2%", rot: -4, delay: 0.14 },
  { activity: activities[4], top: "79%", side: "right" as const, offset: "5%", rot: 5, delay: 0.22 },
];

// Problem-led, staged in three beats: two relatable situations, then the payoff line — which is
// the only one that carries the lime accent, so the resolution visually lands differently from
// the setup.
const HEADLINE_LINES = [
  { text: "Friends are busy?", accent: false },
  { text: "Got free time?", accent: false },
  { text: "Let's make it count.", accent: true },
];

export function Hero() {
  const ref = useRef<HTMLDivElement>(null);
  const prefersReducedMotion = useReducedMotion();
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start start", "end start"] });
  const fieldY = useTransform(scrollYProgress, [0, 1], [0, prefersReducedMotion ? 0 : 160]);
  const contentOpacity = useTransform(scrollYProgress, [0, 0.7], [1, 0]);

  return (
    <section
      id="top"
      ref={ref}
      className="relative flex min-h-[100svh] flex-col overflow-hidden pt-28"
    >
      {/* Atmospheric background — radial glow matching MaxTimeColors.screenGradient */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(120% 70% at 50% -10%, #1a2010 0%, #10130a 45%, #0a0c07 100%)",
        }}
      />
      <div
        className="pointer-events-none absolute left-1/2 top-[-10%] h-[640px] w-[640px] -translate-x-1/2 rounded-full opacity-40 blur-[120px]"
        style={{ background: "radial-gradient(circle, #c6ff3d55 0%, transparent 70%)" }}
      />

      {/* Floating activity field — only shown once there's enough margin either side of the
          headline column to avoid it (lg+, ~1024px). At rest after entrance: no perpetual
          bobbing, per the brief's own instruction to avoid constant floating. */}
      <motion.div
        aria-hidden
        style={{ y: fieldY }}
        className="pointer-events-none absolute inset-0 z-0 hidden lg:block"
      >
        {HERO_TILES.map(({ activity, top, side, offset, rot, delay }, i) => (
          <motion.div
            key={activity.id + i}
            className="absolute flex items-center gap-2.5 rounded-full border border-white/[0.08] py-2 pl-2 pr-4 shadow-[0_24px_50px_-18px_rgba(0,0,0,0.65)] backdrop-blur-md"
            style={{
              top,
              [side]: offset,
              background: "rgba(18,21,12,0.78)",
              rotate: rot,
            }}
            initial={{ opacity: 0, scale: 0.9, y: 24 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1], delay }}
          >
            <span
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[16px]"
              style={{ background: activity.color }}
            >
              {activity.emoji}
            </span>
            <span className="whitespace-nowrap text-[12.5px] font-bold text-text-strong">
              {activity.short}
            </span>
          </motion.div>
        ))}
      </motion.div>

      <motion.div
        style={{ opacity: contentOpacity }}
        className="relative z-10 mx-auto flex w-full max-w-[1240px] flex-1 flex-col items-center justify-center px-6 pb-24 text-center sm:px-8"
      >
        <motion.span
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.1 }}
          className="mb-6 inline-flex w-fit items-center gap-2 rounded-full border border-chip-border bg-card-bg/60 px-3.5 py-1.5 text-[11.5px] font-bold uppercase tracking-[0.14em] text-muted-2"
        >
          <span className="h-1.5 w-1.5 rounded-full bg-accent" />
          Activities, people, plans
        </motion.span>

        <h1 className="font-satoshi text-[9.5vw] font-black leading-[1.1] tracking-[-0.02em] sm:text-[8.5vw] md:text-[6.4vw] lg:text-[80px]">
          {HEADLINE_LINES.map((line, i) => (
            <span key={line.text} className="block overflow-hidden">
              <motion.span
                className={`block ${line.accent ? "text-accent" : "text-text-strong"}`}
                initial={{ y: "110%" }}
                animate={{ y: "0%" }}
                transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1], delay: 0.15 + i * 0.14 }}
              >
                {line.text}
              </motion.span>
            </span>
          ))}
        </h1>

        <motion.p
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, delay: 0.72 }}
          className="mt-7 max-w-[46ch] text-[17px] leading-relaxed text-muted-2 sm:text-[19px]"
        >
          Discover things you love doing, find people who are up for it, and turn free time into
          plans worth making.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.84 }}
          className="mt-6 flex flex-wrap items-center justify-center gap-x-2.5 gap-y-1.5 text-[11.5px] font-bold uppercase tracking-[0.16em] text-muted-2"
        >
          <span>Go solo</span>
          <span className="text-accent">·</span>
          <span>Join a group</span>
          <span className="text-accent">·</span>
          <span>Invite friends</span>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, delay: 0.96 }}
          className="mt-10 flex flex-wrap items-center justify-center gap-4"
        >
          <a
            href="#download"
            className="group inline-flex items-center gap-2 rounded-full bg-accent px-7 py-4 text-[15px] font-bold text-on-accent transition-transform duration-200 hover:scale-[1.03] active:scale-[0.98]"
          >
            Download the app
          </a>
          <a
            href="#discover"
            className="inline-flex items-center gap-2 rounded-full border border-card-border px-7 py-4 text-[15px] font-bold text-text-strong transition-colors duration-200 hover:border-accent/50 hover:text-accent"
          >
            Explore activities
          </a>
        </motion.div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.5, duration: 0.6 }}
        className="relative z-10 mx-auto mb-8 hidden items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted sm:flex"
      >
        <span className="h-8 w-[1px] bg-gradient-to-b from-transparent via-muted to-transparent" />
        Scroll
      </motion.div>
    </section>
  );
}
