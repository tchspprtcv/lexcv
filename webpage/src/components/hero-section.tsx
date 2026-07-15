import { ArrowRight } from "lucide-react";
import { BrandMark } from "@/components/brand-mark";
import { Button } from "@/components/ui/button";
import type { BrandingResponse } from "@/types/branding";

export function HeroSection({ branding }: { branding: BrandingResponse }) {
  return (
    <section className="border-b border-slate-200 py-12 dark:border-slate-800 md:py-16 lg:py-16">
      <div className="mx-auto max-w-3xl px-6">
        <BrandMark branding={branding} className="mb-6" />
        <div className="mb-4 h-px w-12 bg-blue-600 dark:bg-blue-400" />
        <span className="inline-flex items-center border border-slate-300 px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:border-slate-700 dark:text-slate-400">
          PLATAFORMA DE GESTÃO JURÍDICA
        </span>
        <h1 className="mt-6 text-5xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">
          Gestão jurídica completa para a sua instituição
        </h1>
        <p className="mt-6 text-base text-slate-600 dark:text-slate-300">
          Clientes, processos, prazos e documentos — tudo num único painel, com isolamento total por tenant.
        </p>
        <div className="mt-8 flex flex-wrap items-center gap-4">
          <Button asChild variant="secondary" className="rounded-none">
            <a href="/login">Entrar</a>
          </Button>
          <Button asChild variant="ghost" className="rounded-none">
            <a href="#funcionalidades">Ver Funcionalidades<ArrowRight className="h-4 w-4" /></a>
          </Button>
        </div>
      </div>
    </section>
  );
}
