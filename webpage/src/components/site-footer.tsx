import { ArrowRight } from "lucide-react";
import { BrandMark } from "@/components/brand-mark";
import { Button } from "@/components/ui/button";
import { getLoginUrl } from "@/lib/get-login-url";
import type { BrandingResponse } from "@/types/branding";

export function SiteFooter({ branding }: { branding: BrandingResponse }) {
  const ano = new Date().getFullYear();
  return (
    <footer className="border-t border-slate-200 py-12 dark:border-slate-800 md:py-16 lg:py-16">
      <div className="mx-auto flex max-w-7xl flex-col items-center gap-6 px-6 text-center">
        <BrandMark branding={branding} />
        <Button asChild size="lg" className="">
          <a href={getLoginUrl()}>Entrar<ArrowRight className="h-4 w-4" /></a>
        </Button>
        <p className="text-sm text-slate-500 dark:text-slate-400">
          © {ano} LexCV. Plataforma institucional de gestão jurídica.
        </p>
      </div>
    </footer>
  );
}
