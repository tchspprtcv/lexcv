import { ContactSection } from "@/components/contact-section";
import { FaqSection } from "@/components/faq-section";
import { FeaturesSection } from "@/components/features-section";
import { HeroSection } from "@/components/hero-section";
import { ProblemSection } from "@/components/problem-section";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { TrustSection } from "@/components/trust-section";
import { fetchBranding } from "@/lib/branding";

export const dynamic = "force-dynamic";

export default async function Home() {
  const branding = await fetchBranding();

  return (
    <>
      <SiteHeader branding={branding} />
      <main>
        <HeroSection branding={branding} />
        <ProblemSection />
        <FeaturesSection />
        <TrustSection />
        <FaqSection />
        <ContactSection />
      </main>
      <SiteFooter branding={branding} />
    </>
  );
}
