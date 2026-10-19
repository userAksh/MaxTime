import Link from "next/link";
import { Logo } from "./Logo";
import { siteConfig, isConfigured } from "@/lib/config";

export function Footer() {
  return (
    <footer className="relative border-t border-card-border bg-bg-1 py-14">
      <div className="mx-auto max-w-[1240px] px-6 sm:px-8">
        <div className="flex flex-col gap-10 sm:flex-row sm:justify-between">
          <div className="max-w-[32ch]">
            <Logo />
            <p className="mt-4 text-[13.5px] leading-relaxed text-muted">
              Discover activities, find people to join and book real-world experiences.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-10 sm:flex sm:gap-16">
            <div>
              <p className="mb-3 text-[11.5px] font-bold uppercase tracking-[0.1em] text-muted">Get the app</p>
              <ul className="flex flex-col gap-2 text-[13.5px] text-muted-2">
                <li>
                  <Link href="/#download" className="hover:text-accent">
                    Download
                  </Link>
                </li>
              </ul>
            </div>
            <div>
              <p className="mb-3 text-[11.5px] font-bold uppercase tracking-[0.1em] text-muted">Partners</p>
              <ul className="flex flex-col gap-2 text-[13.5px] text-muted-2">
                <li>
                  <Link href="/#partners" className="hover:text-accent">
                    Partner with MaxTime
                  </Link>
                </li>
              </ul>
            </div>
            <div>
              <p className="mb-3 text-[11.5px] font-bold uppercase tracking-[0.1em] text-muted">Contact</p>
              <ul className="flex flex-col gap-2 text-[13.5px] text-muted-2">
                <li>
                  <a href={`mailto:${siteConfig.contactEmail}`} className="hover:text-accent">
                    {siteConfig.contactEmail}
                  </a>
                </li>
                <li>
                  {isConfigured(siteConfig.instagramUrl) ? (
                    <a href={siteConfig.instagramUrl} target="_blank" rel="noreferrer" className="hover:text-accent">
                      Instagram
                    </a>
                  ) : (
                    <span className="text-muted/70">Instagram — coming soon</span>
                  )}
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div className="mt-12 flex flex-col gap-3 border-t border-card-border pt-6 text-[12px] text-muted sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
            <p>© {new Date().getFullYear()} MaxTime. All rights reserved.</p>
            <Link href="/privacy" className="hover:text-accent">
              Privacy policy
            </Link>
            <Link href="/support" className="hover:text-accent">
              Support
            </Link>
          </div>
          <p className="text-muted/70">Let&apos;s do more together.</p>
        </div>
      </div>
    </footer>
  );
}
