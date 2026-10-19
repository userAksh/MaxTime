import { Nav } from "@/components/Nav";
import { Hero } from "@/components/Hero";
import { ProductReveal } from "@/components/ProductReveal";
import { ActivityShowcase } from "@/components/ActivityShowcase";
import { SocialLayer } from "@/components/SocialLayer";
import { Booking } from "@/components/Booking";
import { DownloadSection } from "@/components/DownloadSection";
import { PartnerSection } from "@/components/PartnerSection";
import { Footer } from "@/components/Footer";

export default function Home() {
  return (
    <>
      <Nav />
      <main>
        <Hero />
        <ProductReveal />
        <ActivityShowcase />
        <SocialLayer />
        <Booking />
        <DownloadSection />
        <PartnerSection />
      </main>
      <Footer />
    </>
  );
}
