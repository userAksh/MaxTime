"use client";

import { motion } from "framer-motion";
import { Reveal } from "./motion/Reveal";
import { IconChat, IconUsers, IconCompass } from "./icons";

const FEATURES = [
  {
    icon: IconUsers,
    title: "Invite friends",
    body: "Pull your people into a plan in a couple of taps, or let them find it on their own.",
  },
  {
    icon: IconCompass,
    title: "Join a group",
    body: "Find others already interested in the same activity and fall in with the plan.",
  },
  {
    icon: IconChat,
    title: "Chat it out",
    body: "Sort times, splits and logistics in a group chat built around the plan itself.",
  },
];

const NODES = [
  { x: 50, y: 10, size: 14 },
  { x: 14, y: 42, size: 11 },
  { x: 86, y: 42, size: 11 },
  { x: 24, y: 82, size: 10 },
  { x: 76, y: 82, size: 10 },
];

export function SocialLayer() {
  return (
    <section className="relative overflow-hidden bg-bg-2 py-20 sm:py-28">
      <div className="mx-auto grid max-w-[1240px] grid-cols-1 items-center gap-16 px-6 sm:px-8 md:grid-cols-2 md:gap-10">
        <Reveal>
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">Together, your way</span>
          <h2 className="mt-4 max-w-[16ch] font-satoshi text-[32px] font-black leading-[1.05] tracking-tight text-text-strong sm:text-[44px]">
            Better with people. Still great alone.
          </h2>
          <p className="mt-5 max-w-[46ch] text-[16px] leading-relaxed text-muted-2">
            MaxTime isn&apos;t a platform for making friends — it&apos;s what happens after you already know
            what you want to do. Bring people along if you want to. Don&apos;t if you&apos;d rather not.
          </p>

          <div className="mt-10 flex flex-col gap-6">
            {FEATURES.map((f, i) => (
              <motion.div
                key={f.title}
                initial={{ opacity: 0, x: -16 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true, margin: "-20% 0px" }}
                transition={{ duration: 0.5, delay: i * 0.1 }}
                className="flex items-start gap-4"
              >
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-chip-border text-accent">
                  <f.icon className="h-[18px] w-[18px]" />
                </span>
                <div>
                  <p className="font-satoshi text-[16px] font-bold text-text-strong">{f.title}</p>
                  <p className="mt-1 text-[14.5px] leading-relaxed text-muted-2">{f.body}</p>
                </div>
              </motion.div>
            ))}
          </div>
        </Reveal>

        <motion.div
          initial={{ opacity: 0, scale: 0.92 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true, margin: "-15% 0px" }}
          transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="relative mx-auto aspect-square w-full max-w-[440px]"
        >
          <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full" aria-hidden>
            {NODES.map((n, i) => (
              <motion.line
                key={i}
                x1="50"
                y1="50"
                x2={n.x}
                y2={n.y}
                stroke="#c6ff3d"
                strokeOpacity="0.28"
                strokeWidth="0.4"
                initial={{ pathLength: 0 }}
                whileInView={{ pathLength: 1 }}
                viewport={{ once: true }}
                transition={{ duration: 0.8, delay: 0.3 + i * 0.08 }}
              />
            ))}
          </svg>

          <div className="absolute left-1/2 top-1/2 flex h-24 w-24 -translate-x-1/2 -translate-y-1/2 flex-col items-center justify-center rounded-full border border-accent/40 bg-accent/10 text-center backdrop-blur-sm">
            <span className="text-[9px] font-bold uppercase tracking-wide text-accent">Trail run</span>
            <span className="text-[8px] text-muted-2">Sat · 6 AM</span>
          </div>

          {NODES.map((n, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, scale: 0.5, x: "-50%", y: "-50%" }}
              whileInView={{ opacity: 1, scale: 1, x: "-50%", y: "-50%" }}
              viewport={{ once: true }}
              transition={{ duration: 0.5, delay: 0.5 + i * 0.08 }}
              className="absolute rounded-full border-2 border-bg-2"
              style={{
                left: `${n.x}%`,
                top: `${n.y}%`,
                width: n.size * 4,
                height: n.size * 4,
                background: `linear-gradient(145deg, #c6ff3d, #7fae10)`,
              }}
            />
          ))}
        </motion.div>
      </div>
    </section>
  );
}
