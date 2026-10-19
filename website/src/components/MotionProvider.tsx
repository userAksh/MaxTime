"use client";

import { MotionConfig } from "framer-motion";
import type { ReactNode } from "react";

/** Makes every Framer Motion component in the tree respect the OS-level reduced-motion
 *  preference automatically — individual components no longer need to branch on
 *  useReducedMotion() themselves for this to work. */
export function MotionProvider({ children }: { children: ReactNode }) {
  return <MotionConfig reducedMotion="user">{children}</MotionConfig>;
}
