"use client";

import * as React from "react";
import { Menu } from "lucide-react";

import { BrandMark } from "@/components/brand-mark";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { getLoginUrl } from "@/lib/get-login-url";
import type { BrandingResponse } from "@/types/branding";

const NAV_LINKS = [
  { href: "#funcionalidades", label: "Funcionalidades" },
  { href: "#confianca", label: "Confiança" },
  { href: "#contacto", label: "Contacto" },
] as const;

export function SiteHeader({ branding }: { branding: BrandingResponse }) {
  const [open, setOpen] = React.useState(false);

  React.useEffect(() => {
    const mq = window.matchMedia("(min-width: 768px)");
    const handleChange = (e: MediaQueryListEvent) => {
      if (e.matches) setOpen(false);
    };
    mq.addEventListener("change", handleChange);
    return () => mq.removeEventListener("change", handleChange);
  }, []);

  return (
    <header className="sticky top-0 z-10 h-16 border-b border-slate-200 bg-white/80 backdrop-blur-md dark:border-slate-800 dark:bg-[#020617]/80">
      <div className="mx-auto flex h-full max-w-7xl items-center gap-4 px-6">
        <BrandMark branding={branding} />
        <div className="ml-auto flex items-center gap-6">
          <nav className="hidden md:flex items-center gap-6">
            {NAV_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="text-sm font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400"
              >
                {link.label}
              </a>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            <ThemeToggle />
            <Button asChild variant="secondary" className="hidden md:inline-flex">
              <a href={getLoginUrl()}>Entrar</a>
            </Button>
            <Sheet open={open} onOpenChange={setOpen}>
              <SheetTrigger asChild>
                <button
                  type="button"
                  aria-label="Abrir menu"
                  className="md:hidden flex items-center justify-center h-9 w-9 rounded-md text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                >
                  <Menu className="h-5 w-5" />
                </button>
              </SheetTrigger>
              <SheetContent side="right" className="w-72 sm:max-w-sm">
                <SheetTitle className="sr-only">Menu</SheetTitle>
                <nav className="mt-10 flex flex-col gap-1 px-6">
                  {NAV_LINKS.map((link) => (
                    <a
                      key={link.href}
                      href={link.href}
                      onClick={() => setOpen(false)}
                      className="rounded-md px-3 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-100 hover:text-blue-600 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-blue-400"
                    >
                      {link.label}
                    </a>
                  ))}
                  <Button asChild variant="secondary" className="mt-4">
                    <a href={getLoginUrl()} onClick={() => setOpen(false)}>
                      Entrar
                    </a>
                  </Button>
                </nav>
              </SheetContent>
            </Sheet>
          </div>
        </div>
      </div>
    </header>
  );
}
