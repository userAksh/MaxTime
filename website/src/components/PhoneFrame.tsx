import type { ReactNode } from "react";

/** A deliberately simplified device frame for illustrative product mockups — not a photo of a
 *  real device, and not claiming to be a literal screenshot of the live app. */
export function PhoneFrame({ children }: { children: ReactNode }) {
  return (
    <div className="relative mx-auto w-full max-w-[300px]">
      <div className="absolute -inset-6 -z-10 rounded-[48px] bg-accent/[0.06] blur-2xl" />
      <div className="relative rounded-[42px] border border-white/10 bg-gradient-to-b from-[#15180f] to-[#0a0c07] p-2.5 shadow-[0_40px_80px_-30px_rgba(0,0,0,0.8)]">
        <div className="absolute left-1/2 top-2.5 z-10 h-5 w-24 -translate-x-1/2 rounded-full bg-[#060702]" />
        <div className="relative aspect-[9/19.5] overflow-hidden rounded-[34px] bg-bg-2">
          {children}
        </div>
      </div>
    </div>
  );
}
