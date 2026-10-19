"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import Link from "next/link";
import { Logo } from "./Logo";

const LINKS = [
  { href: "/#how-it-works", label: "How it works" },
  { href: "/#discover", label: "Discover activities" },
  { href: "/#partners", label: "For partners" },
];

export function Nav() {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    document.body.style.overflow = menuOpen ? "hidden" : "";
    return () => {
      document.body.style.overflow = "";
    };
  }, [menuOpen]);

  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-colors duration-300 ${
        scrolled ? "bg-bg-1/80 backdrop-blur-xl border-b border-card-border" : "bg-transparent"
      }`}
    >
      <nav className="mx-auto flex max-w-[1240px] items-center justify-between px-5 py-3.5 sm:px-8">
        <Link href="/#top" className="shrink-0" aria-label="MaxTime home">
          <Logo />
        </Link>

        <ul className="hidden items-center gap-8 md:flex">
          {LINKS.map((link) => (
            <li key={link.href}>
              <Link
                href={link.href}
                className="text-[13.5px] font-medium text-muted-2 transition-colors hover:text-text-strong"
              >
                {link.label}
              </Link>
            </li>
          ))}
        </ul>

        <div className="hidden items-center md:flex">
          <Link
            href="/#download"
            className="rounded-full bg-accent px-5 py-2.5 text-[13.5px] font-bold text-on-accent transition-transform duration-200 hover:scale-[1.03] active:scale-[0.98]"
          >
            Download the app
          </Link>
        </div>

        <button
          type="button"
          onClick={() => setMenuOpen((v) => !v)}
          className="relative z-50 flex h-9 w-9 items-center justify-center rounded-full border border-card-border text-text-strong md:hidden"
          aria-label={menuOpen ? "Close menu" : "Open menu"}
          aria-expanded={menuOpen}
        >
          <span className="relative block h-3.5 w-4">
            <span
              className={`absolute left-0 top-0 h-[1.5px] w-4 bg-current transition-transform duration-300 ${
                menuOpen ? "translate-y-[6px] rotate-45" : ""
              }`}
            />
            <span
              className={`absolute left-0 bottom-0 h-[1.5px] w-4 bg-current transition-transform duration-300 ${
                menuOpen ? "-translate-y-[6px] -rotate-45" : ""
              }`}
            />
          </span>
        </button>
      </nav>

      <AnimatePresence>
        {menuOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="fixed inset-0 top-0 z-40 bg-bg-1/98 backdrop-blur-xl md:hidden"
          >
            <motion.ul
              initial="hidden"
              animate="visible"
              variants={{ visible: { transition: { staggerChildren: 0.06, delayChildren: 0.15 } } }}
              className="flex h-full flex-col items-center justify-center gap-7 pb-16"
            >
              {LINKS.map((link) => (
                <motion.li
                  key={link.href}
                  variants={{ hidden: { opacity: 0, y: 14 }, visible: { opacity: 1, y: 0 } }}
                >
                  <Link
                    href={link.href}
                    onClick={() => setMenuOpen(false)}
                    className="font-satoshi text-[28px] font-bold text-text-strong"
                  >
                    {link.label}
                  </Link>
                </motion.li>
              ))}
              <motion.li
                variants={{ hidden: { opacity: 0, y: 14 }, visible: { opacity: 1, y: 0 } }}
                className="mt-4"
              >
                <Link
                  href="/#download"
                  onClick={() => setMenuOpen(false)}
                  className="rounded-full bg-accent px-7 py-3.5 text-[15px] font-bold text-on-accent"
                >
                  Download the app
                </Link>
              </motion.li>
            </motion.ul>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
