import { siteConfig, isConfigured } from "@/lib/config";

function BadgeShell({
  href,
  label,
  sublabel,
  glyph,
}: {
  href: string;
  label: string;
  sublabel: string;
  glyph: React.ReactNode;
}) {
  const configured = isConfigured(href);

  const content = (
    <>
      <span className="shrink-0">{glyph}</span>
      <span className="flex flex-col items-start leading-none">
        <span className="text-[10px] text-muted-2">{sublabel}</span>
        <span className="mt-1 font-satoshi text-[15px] font-bold text-text-strong">{label}</span>
      </span>
      {!configured && (
        <span className="ml-auto shrink-0 rounded-full border border-chip-border px-2 py-0.5 text-[9px] font-bold uppercase tracking-wide text-muted">
          Soon
        </span>
      )}
    </>
  );

  const className =
    "flex min-w-[196px] items-center gap-3 rounded-2xl border border-card-border bg-card-bg px-4 py-3 transition-all duration-200";

  if (!configured) {
    return (
      <button
        type="button"
        disabled
        aria-disabled
        className={`${className} cursor-not-allowed opacity-60`}
        title="Coming soon"
      >
        {content}
      </button>
    );
  }

  return (
    <a href={href} target="_blank" rel="noreferrer" className={`${className} hover:border-accent/40 hover:scale-[1.02]`}>
      {content}
    </a>
  );
}

export function AppleBadge() {
  return (
    <BadgeShell
      href={siteConfig.appStoreUrl}
      label="App Store"
      sublabel="Download on the"
      glyph={
        <svg viewBox="0 0 24 24" className="h-6 w-6 text-text-strong" fill="currentColor">
          <path d="M16.365 1.43c0 1.14-.468 2.1-1.218 2.823-.808.776-2.13 1.37-3.09 1.29-.12-1.098.46-2.25 1.17-2.966.81-.83 2.19-1.43 3.138-1.147ZM20.62 17.18c-.52 1.2-.77 1.73-1.44 2.78-.94 1.46-2.26 3.28-3.9 3.3-1.46.02-1.83-.95-3.8-.94-1.97.01-2.38.96-3.84.94-1.64-.02-2.9-1.66-3.84-3.12C1.3 16.7.63 12.9 2.2 10.3c1.1-1.84 2.9-2.92 4.58-2.92 1.7 0 2.77 1.03 4.17 1.03 1.36 0 2.2-1.03 4.17-1.03 1.5 0 3.08.82 4.2 2.24-3.7 2.03-3.1 7.3 1.28 7.56Z" />
        </svg>
      }
    />
  );
}

export function GooglePlayBadge() {
  return (
    <BadgeShell
      href={siteConfig.playStoreUrl}
      label="Google Play"
      sublabel="Get it on"
      glyph={
        <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none">
          <path d="M3.6 2.3c-.4.3-.6.7-.6 1.3v17c0 .5.2 1 .6 1.3l9.2-9.8L3.6 2.3Z" fill="#C6FF3D" />
          <path d="M16.6 9 13.7 12l2.9 3 3.2-1.9c.9-.5.9-1.7 0-2.2L16.6 9Z" fill="#E2FF8E" />
          <path d="m3.6 2.3 9.2 9.7 3.8-3.9L5.5 1.7c-.7-.4-1.4-.2-1.9.6Z" fill="#8A9174" />
          <path d="m3.6 21.7 12-6.7-3.9-3.9-9.2 9.7c.5.8 1.2 1 2 .9Z" fill="#8A9174" />
        </svg>
      }
    />
  );
}
