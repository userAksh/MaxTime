import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Nav } from "@/components/Nav";
import { Footer } from "@/components/Footer";
import { siteConfig } from "@/lib/config";

export const metadata: Metadata = {
  title: "Privacy Policy — MaxTime",
  description:
    "How MaxTime collects, uses, shares, and protects your personal data across the MaxTime app and website.",
};

const LAST_UPDATED = "19 October 2026";

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-28 border-t border-card-border py-10 first:border-t-0 first:pt-0">
      <h2 className="font-satoshi text-[22px] font-bold text-text-strong sm:text-[26px]">{title}</h2>
      <div className="mt-4 flex flex-col gap-4 text-[15px] leading-relaxed text-muted-2">{children}</div>
    </section>
  );
}

export default function PrivacyPolicy() {
  return (
    <>
      <Nav />
      <main className="bg-bg-1 pt-32 pb-24">
        <div className="mx-auto max-w-[760px] px-6 sm:px-8">
          <span className="text-[12px] font-bold uppercase tracking-[0.14em] text-accent">Legal</span>
          <h1 className="mt-4 font-satoshi text-[36px] font-black leading-[1.08] tracking-tight text-text-strong sm:text-[46px]">
            Privacy Policy
          </h1>
          <p className="mt-4 text-[14px] text-muted">Last updated {LAST_UPDATED}</p>

          <p className="mt-8 text-[15.5px] leading-relaxed text-muted-2">
            This policy explains what personal data MaxTime (&quot;MaxTime,&quot; &quot;we,&quot; &quot;us&quot;)
            collects through the MaxTime mobile app and the maxtime.in website, why we collect it, who we share
            it with, and the choices and rights you have over it. It&apos;s written to describe what the product
            actually does, not generic boilerplate — if a section doesn&apos;t apply to how you use MaxTime, it
            won&apos;t be here.
          </p>

          <div className="mt-10 flex flex-col">
            <Section id="who-we-are" title="1. Who we are">
              <p>
                MaxTime is a platform that helps people discover real-world activities, find others to do them
                with, and book sessions with activity providers. This policy covers data processed by MaxTime as
                the data controller for the app and website. If you have questions, you can reach us at{" "}
                <a href={`mailto:${siteConfig.contactEmail}`} className="text-accent hover:underline">
                  {siteConfig.contactEmail}
                </a>
                .
              </p>
            </Section>

            <Section id="data-we-collect" title="2. Information we collect">
              <p>We collect the following categories of personal data:</p>
              <ul className="ml-5 flex list-disc flex-col gap-2 marker:text-accent">
                <li>
                  <span className="font-semibold text-text-strong">Account information</span> — your name, phone
                  number or email address (used to sign in and verify your account), date of birth, and profile
                  photo, if you choose to add one.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Location</span> — with your permission, your
                  device&apos;s precise or approximate location, used to show you nearby activities, available
                  time slots, and people doing the same activity near you. You can deny or revoke this permission
                  at any time in your device settings; some features won&apos;t work without it.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Activity and booking data</span> — the
                  activities you search for or express interest in, groups you join or create, sessions you book,
                  and your skill level or preferences for an activity.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Messages</span> — messages you send in group
                  chats within the app, so other members of that group can see them.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Payment information</span> — when you book a
                  paid session through MaxTime, payment is processed by our payment partner, Razorpay. MaxTime
                  does not receive or store your full card, UPI, or bank account details — Razorpay handles that
                  directly and shares with us only what&apos;s needed to confirm and manage your booking (such as
                  amount and payment status).
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Referral data</span> — if you use or share a
                  referral code, we record that a referral occurred so the relevant reward can be applied.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Technical data</span> — standard information
                  our infrastructure providers (see Section 4) collect to operate the service, such as IP
                  address, device type, and app diagnostics.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Usage and analytics data</span> — events
                  describing how you use the app, such as which screens you view, which activities you search
                  for, and when a booking or payment starts, succeeds, or fails. These events are tied to an
                  anonymous device identifier before you sign in, and to your account afterward. We use this to
                  understand how MaxTime is actually used and to improve it — not for advertising, and not sold
                  to anyone.
                </li>
              </ul>
            </Section>

            <Section id="how-we-use-it" title="3. How we use your information">
              <p>We use the data above to:</p>
              <ul className="ml-5 flex list-disc flex-col gap-2 marker:text-accent">
                <li>Create and secure your account, and verify it&apos;s really you.</li>
                <li>
                  Show you relevant activities, people, and groups near you, and let others find you for the
                  same purpose.
                </li>
                <li>Process bookings and payments, and communicate with you about them.</li>
                <li>Operate group chat and referral features.</li>
                <li>Understand how people use MaxTime, so we know what&apos;s working and what to fix.</li>
                <li>Maintain the security and reliability of the app, and prevent abuse.</li>
                <li>Respond to support requests you send us.</li>
              </ul>
              <p>
                We do not use your data to show you third-party advertising, and we do not sell your personal
                data.
              </p>
            </Section>

            <Section id="who-we-share-with" title="4. Who we share it with">
              <p>
                We share personal data only with the service providers that help us run MaxTime, under
                agreements that limit how they can use it — never for their own marketing:
              </p>
              <ul className="ml-5 flex list-disc flex-col gap-2 marker:text-accent">
                <li>
                  <span className="font-semibold text-text-strong">Google Firebase / Google Cloud</span> — powers
                  our authentication, database, backend functions, file storage, and hosting.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Razorpay</span> — processes payments for paid
                  bookings.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Google Maps</span> — powers location search
                  and the map used to confirm your area.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Mixpanel</span> — processes the usage and
                  analytics data described in Section 2 so we can understand how the app is used.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Other users</span> — your name, photo, and
                  activity/skill info are visible to other users as part of how the group and discovery features
                  work; messages you send in a group are visible to that group&apos;s members.
                </li>
                <li>
                  <span className="font-semibold text-text-strong">Activity providers</span> — when you book a
                  session, the provider receives the booking details needed to host you (such as your name and
                  booking time).
                </li>
              </ul>
              <p>
                We may also disclose data if required by law, or to protect the rights, safety, or property of
                MaxTime, our users, or the public.
              </p>
            </Section>

            <Section id="retention" title="5. How long we keep it">
              <p>
                We retain your account data for as long as your account is active. If you ask us to delete your
                account, we delete or anonymise your personal data within a reasonable period, except where we
                need to retain limited records (such as transaction records) to meet legal, accounting, or
                dispute-resolution obligations.
              </p>
            </Section>

            <Section id="your-rights" title="6. Your rights and choices">
              <p>Depending on where you live, you may have the right to:</p>
              <ul className="ml-5 flex list-disc flex-col gap-2 marker:text-accent">
                <li>Access the personal data we hold about you.</li>
                <li>Correct inaccurate or incomplete data.</li>
                <li>Delete your account and associated personal data.</li>
                <li>Withdraw consent for processing that relies on your consent (e.g. location).</li>
                <li>Ask how your data is being used, and raise a complaint if you&apos;re unhappy with our response.</li>
              </ul>
              <p>
                You can delete your account directly in the app — go to{" "}
                <span className="font-semibold text-text-strong">Profile → Account → Delete account</span>. This
                immediately removes your login and your profile data. Deleting your account doesn&apos;t remove
                messages or booking records that are part of a shared group&apos;s history, for the same reason
                most social apps don&apos;t erase another member&apos;s copy of a shared conversation.
              </p>
              <p>
                For anything else in this section — access, correction, consent withdrawal, or a complaint — email{" "}
                <a href={`mailto:${siteConfig.contactEmail}`} className="text-accent hover:underline">
                  {siteConfig.contactEmail}
                </a>{" "}
                from the email or number linked to your account. We&apos;ll verify your identity before acting on
                the request.
              </p>
            </Section>

            <Section id="childrens-privacy" title="7. Children's privacy">
              <p>
                MaxTime is not directed at children, and we knowingly collect age information at sign-up to keep
                it that way. If you believe a child has provided us with personal data, contact us and we&apos;ll
                delete it.
              </p>
            </Section>

            <Section id="security" title="8. Security">
              <p>
                We rely on our infrastructure providers&apos; security controls (encryption in transit, access
                controls, and standard cloud security practices) to protect your data. No method of transmission
                or storage is 100% secure, so we can&apos;t guarantee absolute security.
              </p>
            </Section>

            <Section id="changes" title="9. Changes to this policy">
              <p>
                We may update this policy as MaxTime changes. If we make a material change, we&apos;ll update the
                &quot;Last updated&quot; date above and, where appropriate, notify you in the app.
              </p>
            </Section>

            <Section id="contact" title="10. Contact us">
              <p>
                Questions, requests, or complaints about this policy or your data can be sent to{" "}
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
