import { IconUsers } from "@/components/icons";

export function ConnectMock() {
  return (
    <div className="flex h-full flex-col px-3.5 pb-4 pt-9">
      <div className="mb-4 flex items-center justify-between">
        <span className="font-satoshi text-[13px] font-extrabold text-text-strong">Trail run club</span>
        <IconUsers className="h-4 w-4 text-muted-2" />
      </div>

      <div className="mb-4 flex items-center -space-x-2.5">
        {["#C6FF3D", "#8A9174", "#E2FF8E", "#4D5C22"].map((c, i) => (
          <span
            key={i}
            className="h-8 w-8 rounded-full border-2 border-bg-2"
            style={{ background: c }}
          />
        ))}
        <span className="ml-3.5 text-[10px] font-semibold text-muted-2">4 going · 2 maybe</span>
      </div>

      <div className="flex flex-1 flex-col gap-2">
        <div className="self-start max-w-[78%] rounded-2xl rounded-bl-sm bg-card-bg border border-card-border px-3 py-2 text-[10px] text-text">
          6am start, meeting at the park gate 🏃
        </div>
        <div className="self-end max-w-[78%] rounded-2xl rounded-br-sm bg-accent/15 border border-accent/30 px-3 py-2 text-[10px] text-text-strong">
          In! I&apos;ll bring the group an extra route option
        </div>
        <div className="self-start max-w-[78%] rounded-2xl rounded-bl-sm bg-card-bg border border-card-border px-3 py-2 text-[10px] text-text">
          Count me in too, inviting Priya
        </div>
      </div>

      <div className="mt-3 flex items-center gap-2 rounded-full border border-chip-border px-3 py-2">
        <span className="h-1.5 w-1.5 rounded-full bg-accent" />
        <span className="flex-1 text-[9.5px] text-muted">Message the group…</span>
      </div>
    </div>
  );
}
