import { IconClimb, IconPottery, IconTrail } from "@/components/icons";

const rows = [
  { icon: IconClimb, title: "Climbing gym session", meta: "Koramangala · Tonight 7:00 PM" },
  { icon: IconPottery, title: "Pottery studio: wheel basics", meta: "Indiranagar · Sat 11:00 AM" },
  { icon: IconTrail, title: "Sunrise trail run club", meta: "Cubbon Park · Sun 6:00 AM" },
];

export function DiscoverMock() {
  return (
    <div className="flex h-full flex-col px-3.5 pb-4 pt-9">
      <div className="mb-3 flex items-center justify-between">
        <span className="font-satoshi text-[13px] font-extrabold text-text-strong">Discover</span>
        <span className="h-5 w-5 rounded-full border border-chip-border" />
      </div>
      <div className="mb-3 flex flex-wrap gap-1.5">
        {["Nearby", "Tonight", "Creative"].map((chip, i) => (
          <span
            key={chip}
            className={`shrink-0 rounded-full border px-2.5 py-1 text-[9px] font-bold ${
              i === 0 ? "border-accent/40 bg-accent/10 text-accent" : "border-chip-border text-muted-2"
            }`}
          >
            {chip}
          </span>
        ))}
      </div>
      <div className="flex flex-1 flex-col gap-2">
        {rows.map((row) => (
          <div
            key={row.title}
            className="flex items-center gap-2.5 rounded-2xl border border-card-border bg-card-bg p-2.5"
          >
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent/10">
              <row.icon className="h-4 w-4 text-accent" />
            </div>
            <div className="min-w-0">
              <p className="truncate text-[10.5px] font-bold text-text-strong">{row.title}</p>
              <p className="truncate text-[9px] text-muted-2">{row.meta}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
