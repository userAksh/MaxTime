import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

const base = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
};

export function IconRacket(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <ellipse cx="13" cy="11" rx="8" ry="9.5" transform="rotate(-18 13 11)" />
      <path d="M8 18.5 3 27M18.5 15.5l6 8.5" />
      <path d="M13 3v16M7 7.5h12M6 11h14M7 14.5h12" strokeWidth="0.9" opacity="0.6" />
    </svg>
  );
}

export function IconPottery(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <path d="M11 6h10l-1.4 16.5a3 3 0 0 1-3 2.75h-1.2a3 3 0 0 1-3-2.75L11 6Z" />
      <path d="M9.5 6h13" />
      <path d="M14 11.5c1.3 1 2.7 1 4 0" strokeWidth="0.9" opacity="0.6" />
    </svg>
  );
}

export function IconMusic(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <path d="M12 23V8.5L24 6v14" />
      <circle cx="9" cy="24" r="3.2" />
      <circle cx="21" cy="21" r="3.2" />
    </svg>
  );
}

export function IconTrail(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <path d="M4 24 12 9l4.5 7.5L19 12l9 12" />
      <circle cx="23" cy="7" r="2.6" />
    </svg>
  );
}

export function IconPaint(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <path d="M23 5 9 19a4 4 0 1 0 5 5L28 10a3.3 3.3 0 0 0-5-5Z" />
      <path d="M6 23c1.6-1.6 3.6-1 4.4 0 1 1.1 0 3-1.4 4-1.6 1.1-3.6.4-4.4-.6" strokeWidth="0.9" opacity="0.6" />
    </svg>
  );
}

export function IconYoga(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <circle cx="16" cy="6.5" r="2.4" />
      <path d="M16 10v7M16 17l-8 8M16 17l8 8M9 15l7-2 7 2" />
    </svg>
  );
}

export function IconDice(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <rect x="6" y="6" width="20" height="20" rx="5" />
      <circle cx="12" cy="12" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="20" cy="12" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="16" cy="16" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="12" cy="20" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="20" cy="20" r="1.3" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function IconClimb(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <path d="M5 27 14 9l4 7 3-4 6 15" />
      <circle cx="10" cy="15" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="19" cy="18" r="1.3" fill="currentColor" stroke="none" />
      <circle cx="23" cy="12" r="1.3" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function IconUsers(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <circle cx="12" cy="11" r="4" />
      <path d="M4 26c0-4.4 3.6-7.5 8-7.5s8 3.1 8 7.5" />
      <circle cx="22.5" cy="12.5" r="3.2" />
      <path d="M19.5 18.6c3.6.5 6 3.3 6 7.4" strokeWidth="1.3" />
    </svg>
  );
}

export function IconChat(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <path d="M5 8.5A3.5 3.5 0 0 1 8.5 5h15A3.5 3.5 0 0 1 27 8.5v9A3.5 3.5 0 0 1 23.5 21H14l-6 5v-5H8.5A3.5 3.5 0 0 1 5 17.5v-9Z" />
    </svg>
  );
}

export function IconCalendar(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <rect x="5" y="7.5" width="22" height="19" rx="3.5" />
      <path d="M5 13.5h22M11 4.5v5M21 4.5v5" />
      <path d="M16 18.5l2 2 4-4.5" strokeWidth="1.5" />
    </svg>
  );
}

export function IconCompass(props: IconProps) {
  return (
    <svg viewBox="0 0 32 32" {...base} {...props}>
      <circle cx="16" cy="16" r="11.5" />
      <path d="M20.5 11.5 17 17l-5.5 3.5L15 15l5.5-3.5Z" strokeLinejoin="round" />
    </svg>
  );
}

export function IconArrowUpRight(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="M7 17 17 7M9 7h8v8" />
    </svg>
  );
}

export function IconCheck(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" {...base} {...props}>
      <path d="m5 12.5 4.5 4.5L19 7" />
    </svg>
  );
}
