"use client";

import { motion, useReducedMotion, type Variants } from "framer-motion";
import type { ReactNode } from "react";

interface RevealProps {
  children: ReactNode;
  className?: string;
  delay?: number;
  y?: number;
  once?: boolean;
  as?: "div" | "span";
}

/** Fade + rise entrance, triggered once an element crosses into the viewport. Collapses to a
 *  plain opacity fade (no transform) when the user has requested reduced motion. */
export function Reveal({ children, className, delay = 0, y = 22, once = true, as = "div" }: RevealProps) {
  const prefersReducedMotion = useReducedMotion();
  const Comp = motion[as];

  const variants: Variants = {
    hidden: { opacity: 0, y: prefersReducedMotion ? 0 : y },
    visible: {
      opacity: 1,
      y: 0,
      transition: { duration: prefersReducedMotion ? 0.2 : 0.7, ease: [0.16, 1, 0.3, 1], delay },
    },
  };

  return (
    <Comp
      className={className}
      initial="hidden"
      whileInView="visible"
      viewport={{ once, margin: "-10% 0px -10% 0px" }}
      variants={variants}
    >
      {children}
    </Comp>
  );
}

export function RevealGroup({
  children,
  className,
  stagger = 0.08,
}: {
  children: ReactNode;
  className?: string;
  stagger?: number;
}) {
  return (
    <motion.div
      className={className}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: "-10% 0px -10% 0px" }}
      variants={{
        hidden: {},
        visible: { transition: { staggerChildren: stagger } },
      }}
    >
      {children}
    </motion.div>
  );
}
