import { IconCheck } from "@/components/icons";

export function DoItMock() {
  return (
    <div className="flex h-full flex-col items-center justify-center px-5 pb-4 pt-9 text-center">
      <div className="mb-5 flex h-14 w-14 items-center justify-center rounded-full bg-accent/15 border border-accent/30">
        <IconCheck className="h-6 w-6 text-accent" />
      </div>
      <p className="font-satoshi text-[15px] font-black text-text-strong">You&apos;re in!</p>
      <p className="mt-1.5 text-[10px] leading-relaxed text-muted-2">
        Padel · Court 2 · HSR Layout
        <br />
        Saturday, 7:00 PM · 4 going
      </p>
      <div className="mt-5 w-full rounded-2xl border border-dashed border-chip-border p-3">
        <p className="text-[9px] font-bold uppercase tracking-wide text-muted">Added to Plans</p>
        <p className="mt-1 text-[9.5px] text-muted-2">Reminder set for 6:30 PM</p>
      </div>
    </div>
  );
}
