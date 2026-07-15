import { BrandMark } from "@/components/brand-mark";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import type { BrandingResponse } from "@/types/branding";

export function SiteHeader({ branding }: { branding: BrandingResponse }) {
  return (
    <header className="sticky top-0 z-10 h-16 border-b border-slate-200 bg-white/80 backdrop-blur-md dark:border-slate-800 dark:bg-[#020617]/80">
      <div className="mx-auto flex h-full max-w-7xl items-center gap-4 px-6">
        <BrandMark branding={branding} />
        <div className="ml-auto flex items-center gap-6">
          <nav className="hidden md:flex items-center gap-6">
            <a href="#funcionalidades" className="text-sm font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400">Funcionalidades</a>
            <a href="#confianca" className="text-sm font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400">Confiança</a>
            <a href="#contacto" className="text-sm font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400">Contacto</a>
          </nav>
          <div className="flex items-center gap-3">
            <ThemeToggle />
            <Button asChild variant="secondary" className="rounded-none">
              <a href="/login">Entrar</a>
            </Button>
          </div>
        </div>
      </div>
    </header>
  );
}
