import Image from "next/image";

export function Logo({ className = "", markOnly = false }: { className?: string; markOnly?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <Image
        src="/brand/mark-512.png"
        alt="MaxTime"
        width={28}
        height={28}
        className="h-7 w-7 rounded-[8px]"
        priority
      />
      {!markOnly && (
        <span className="font-satoshi text-[17px] font-black tracking-tight text-text-strong">
          MaxTime
        </span>
      )}
    </span>
  );
}
