import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Nav } from "@/components/Nav";
import { Footer } from "@/components/Footer";
import { siteConfig } from "@/lib/config";

export const metadata: Metadata = {
  title: "Support — MaxTime",
  description: "Get help with the MaxTime app — account, bookings, payments, and more.",
};

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-28 border-t border-card-border py-10 first:border-t-0 first:pt-0">
      <h2 className="font-satoshi text-[22px] font-bold text-text-strong sm:text-[26px]">{title}</h2>
      <div className="mt-4 flex flex-col gap-4 text-[15px] leading-relaxed text-muted-2">{children}</div>
    </section>
  );
}

export default function Support() {
  return (
    <>
      <Nav />
      <main className="bg-bg-1 pt-32 pb-24">
        <div className="mx-auto max-w-[760px] px-6 sm:px-8">
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">Help</span>
          <h1 className="mt-4 font-satoshi text-[36px] font-black leading-[1.08] tracking-tight text-text-strong sm:text-[46px]">
            Support
          </h1>
          <p className="mt-8 text-[15.5px] leading-relaxed text-muted-2">
            Stuck on something, or found a bug? Here&apos;s how to get help, plus answers to the questions we hear
            most from MaxTime users.
          </p>

          <div className="mt-10 flex flex-col">
            <Section id="contact" title="Contact us">
              <p>
                Email us at{" "}
                <a href={`mailto:${siteConfig.contactEmail}`} className="text-accent hover:underline">
                  {siteConfig.contactEmail}
                </a>{" "}
                and we&apos;ll get back to you. Include your account&apos;s phone number so we can look you up
                faster.
              </p>
            </Section>

            <Section id="account" title="Account &amp; sign-in">
              <p>
                MaxTime signs you in with your phone number — enter it on the welcome screen and we&apos;ll text
                you a one-time code. Didn&apos;t get a code? Check that your number is correct and that you have
                signal, then tap &quot;Resend code.&quot;
              </p>
            </Section>

            <Section id="delete-account" title="Delete your account">
              <p>
                Go to <span className="font-semibold text-text-strong">Profile → Account → Delete account</span> in
                the app. This immediately removes your login and your profile data. Deleting your account
                doesn&apos;t remove messages or booking records that are part of a shared group&apos;s history —
                see our{" "}
                <a href="/privacy#your-rights" className="text-accent hover:underline">
                  Privacy Policy
                </a>{" "}
                for the full details. If you can&apos;t get into the app to do this yourself, email us and
                we&apos;ll action it for you after verifying it&apos;s really you.
              </p>
            </Section>

            <Section id="bookings-payments" title="Bookings &amp; payments">
              <p>
                Payments for paid sessions are processed securely through Razorpay. If a payment was deducted but
                your booking doesn&apos;t show as confirmed, email us with the date, time, and activity — don&apos;t
                try the payment again until we&apos;ve checked.
              </p>
              <p>
                To cancel a booking, open it from your bookings list — cancellation policies are shown on the
                booking before you confirm it.
              </p>
            </Section>

            <Section id="report" title="Report a problem with another user">
              <p>
                If something happens in a group chat or at a session that isn&apos;t okay, email us with as much
                detail as you can — the group/activity, the date, and what happened. We review every report.
              </p>
            </Section>

            <Section id="other" title="Something else">
              <p>
                For anything not covered here — bugs, feedback, partnership questions — email{" "}
                <a href={`mailto:${siteConfig.contactEmail}`} className="text-accent hover:underline">
                  {siteConfig.contactEmail}
                </a>
                .
              </p>
            </Section>
          </div>
        </div>
      </main>
      <Footer />
    </>
  );
}
