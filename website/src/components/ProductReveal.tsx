"use client";

import { useEffect, useRef, useState } from "react";
import { motion, useInView, AnimatePresence } from "framer-motion";
import { PhoneFrame } from "./PhoneFrame";
import { DiscoverMock } from "./mockups/DiscoverMock";
import { ConnectMock } from "./mockups/ConnectMock";
import { BookMock } from "./mockups/BookMock";
import { DoItMock } from "./mockups/DoItMock";
import { IconCompass, IconUsers, IconCalendar, IconCheck } from "./icons";

const STEPS = [
  {
    key: "discover",
    tag: "01 — Discover",
    icon: IconCompass,
    title: "Find something to do nearby.",
    body: "Sports, fitness, pottery, art, dance, music, classes and more — browse what's happening around you, filtered to what you're actually into.",
    Mock: DiscoverMock,
  },
  {
    key: "connect",
    tag: "02 — Connect",
    icon: IconUsers,
    title: "Find people, or don't.",
    body: "Invite friends, join people already interested, or open a group chat to sort out the details. Going solo works just as well.",
    Mock: ConnectMock,
  },
  {
    key: "book",
    tag: "03 — Book",
    icon: IconCalendar,
    title: "Book it, right there.",
    body: "Reserve through the provider's own booking flow where it's supported, or book directly through MaxTime when it isn't.",
    Mock: BookMock,
  },
  {
    key: "do-it",
    tag: "04 — Do it",
    icon: IconCheck,
    title: "Show up. Do the thing.",
    body: "Your plan is confirmed and saved — then it's back to MaxTime to line up whatever's next.",
    Mock: DoItMock,
  },
];

function StepTracker({
  step,
  index,
  onActive,
}: {
  step: (typeof STEPS)[number];
  index: number;
  onActive: (i: number) => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { margin: "-42% 0px -42% 0px" });
  const Icon = step.icon;

  useEffect(() => {
    if (inView) onActive(index);
  }, [inView, index, onActive]);

  return (
    <div ref={ref} className="flex flex-col justify-center py-12 md:py-20 lg:py-28">
      <motion.div animate={{ opacity: inView ? 1 : 0.35 }} transition={{ duration: 0.4 }}>
        <div className="mb-5 flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-full border border-chip-border text-accent">
            <Icon className="h-[18px] w-[18px]" />
          </span>
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-muted">{step.tag}</span>
        </div>
        <h3 className="max-w-[14ch] font-satoshi text-[34px] font-black leading-[1.05] tracking-tight text-text-strong sm:text-[42px]">
          {step.title}
        </h3>
        <p className="mt-4 max-w-[42ch] text-[16px] leading-relaxed text-muted-2">{step.body}</p>
      </motion.div>

      <div className="mt-10 md:hidden">
        <PhoneFrame>
          <step.Mock />
        </PhoneFrame>
      </div>
    </div>
  );
}

export function ProductReveal() {
  const [activeIndex, setActiveIndex] = useState(0);
  const ActiveMock = STEPS[activeIndex].Mock;

  return (
    <section id="how-it-works" className="relative bg-bg-2 py-20 sm:py-28">
      <div className="mx-auto max-w-[1240px] px-6 sm:px-8">
        <div className="mb-16 max-w-[52ch] sm:mb-24">
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">How it works</span>
          <h2 className="mt-4 font-satoshi text-[32px] font-black leading-[1.05] tracking-tight text-text-strong sm:text-[44px]">
            Four steps between &ldquo;what should we do&rdquo; and doing it.
          </h2>
        </div>

        <div className="grid grid-cols-1 gap-x-16 md:grid-cols-2">
          <div>
            {STEPS.map((step, i) => (
              <StepTracker key={step.key} step={step} index={i} onActive={setActiveIndex} />
            ))}
          </div>

          <div className="relative hidden md:block">
            <div className="sticky top-28 flex h-[70vh] items-center justify-center">
              <AnimatePresence mode="wait">
                <motion.div
                  key={STEPS[activeIndex].key}
                  initial={{ opacity: 0, y: 16 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -16 }}
                  transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                >
                  <PhoneFrame>
                    <ActiveMock />
                  </PhoneFrame>
                </motion.div>
              </AnimatePresence>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
