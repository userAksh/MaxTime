import { IconCalendar } from "@/components/icons";

const slots = ["6:00 PM", "7:00 PM", "8:00 PM"];

export function BookMock() {
  return (
    <div className="flex h-full flex-col px-3.5 pb-4 pt-9">
      <div className="mb-4 flex items-center justify-between">
        <span className="font-satoshi text-[13px] font-extrabold text-text-strong">Confirm your spot</span>
        <IconCalendar className="h-4 w-4 text-muted-2" />
      </div>

      <div className="mb-3 rounded-2xl border border-card-border bg-card-bg p-3">
        <p className="text-[10.5px] font-bold text-text-strong">Padel · Court 2</p>
        <p className="mt-0.5 text-[9px] text-muted-2">HSR Layout · Sat, 7:00 PM · 2 spots held</p>
      </div>

      <p className="mb-2 text-[9px] font-bold uppercase tracking-wide text-muted">Pick a time</p>
      <div className="mb-4 flex gap-2">
        {slots.map((slot, i) => (
          <span
            key={slot}
            className={`flex-1 rounded-xl border py-2 text-center text-[9.5px] font-bold ${
              i === 1 ? "border-accent bg-accent text-on-accent" : "border-chip-border text-muted-2"
            }`}
          >
            {slot}
          </span>
        ))}
      </div>

      <div className="mt-auto rounded-2xl bg-accent px-4 py-3 text-center text-[11px] font-extrabold text-on-accent">
        Confirm booking
      </div>
    </div>
  );
}
