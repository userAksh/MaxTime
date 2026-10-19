"use client";

import { motion } from "framer-motion";
import { Reveal } from "./motion/Reveal";
import { AppleBadge, GooglePlayBadge } from "./StoreBadges";
import { PhoneFrame } from "./PhoneFrame";
import { DoItMock } from "./mockups/DoItMock";
import { DiscoverMock } from "./mockups/DiscoverMock";

export function DownloadSection() {
  return (
    <section id="download" className="relative overflow-hidden bg-bg-2 py-24 sm:py-32">
      <div
        className="pointer-events-none absolute left-1/2 top-1/2 h-[520px] w-[820px] -translate-x-1/2 -translate-y-1/2 opacity-30 blur-[140px]"
        style={{ background: "radial-gradient(circle, #c6ff3d55 0%, transparent 70%)" }}
      />

      <div className="relative mx-auto grid max-w-[1240px] grid-cols-1 items-center gap-16 px-6 sm:px-8 md:grid-cols-2">
        <Reveal>
          <h2 className="max-w-[14ch] font-satoshi text-[36px] font-black leading-[1.04] tracking-tight text-text-strong sm:text-[52px]">
            Your next thing starts here.
          </h2>
          <p className="mt-5 max-w-[42ch] text-[17px] leading-relaxed text-muted-2">
            Get the MaxTime app and start discovering activities, people and plans near you.
          </p>
          <div className="mt-9 flex flex-wrap gap-3.5">
            <AppleBadge />
            <GooglePlayBadge />
          </div>
        </Reveal>

        <motion.div
          initial={{ opacity: 0, y: 40 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-15% 0px" }}
          transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="relative mx-auto flex flex-col items-center justify-center gap-10 sm:flex-row sm:gap-5"
        >
          <div className="w-[62vw] max-w-[180px] opacity-80 sm:mt-14 sm:w-[180px] md:w-[210px]">
            <PhoneFrame>
              <DiscoverMock />
            </PhoneFrame>
          </div>
          <div className="w-[68vw] max-w-[200px] sm:w-[200px] md:w-[230px]">
            <PhoneFrame>
              <DoItMock />
            </PhoneFrame>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
