import { ArrowRight, Mail } from "lucide-react";
import { AppPreview } from "@/components/app-preview";
import { BrandMark } from "@/components/brand-mark";
import { Button } from "@/components/ui/button";
import { getDemoUrl } from "@/lib/contacto";
import type { BrandingResponse } from "@/types/branding";

export function HeroSection({ branding }: { branding: BrandingResponse }) {
  return (
    <section className="border-b border-slate-200 py-12 dark:border-slate-800 md:py-16">
      <div className="mx-auto max-w-3xl px-6">
        <BrandMark branding={branding} className="mb-6" />
        <div className="mb-6 h-px w-12 bg-blue-600 dark:bg-blue-400" />

        <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
          Gestão jurídica · Cabo Verde
        </span>

        <h1 className="mt-3 text-4xl font-semibold tracking-tight text-slate-900 dark:text-slate-50 md:text-5xl">
          Nenhum prazo perdido.
          <br />
          Nenhuma peça fora do lugar.
        </h1>

        <p className="mt-6 max-w-2xl text-lg text-slate-600 dark:text-slate-300">
          Plataforma de gestão jurídica para escritórios de advogados e instituições de Cabo Verde.
          Clientes, processos, prazos, documentos e honorários num só sistema — e o seu escritório
          isolado de todos os outros.
        </p>

        <div className="mt-8 flex flex-wrap items-center gap-3">
          <Button asChild size="lg">
            <a href={getDemoUrl("hero")}>
              Pedir demonstração
              <Mail className="h-4 w-4" />
            </a>
          </Button>
          <Button asChild variant="ghost" size="lg">
            <a href="#demonstracao">
              Ver como funciona
              <ArrowRight className="h-4 w-4" />
            </a>
          </Button>
        </div>
      </div>

      {/* A demonstração fica fora do cartão e num contentor mais largo: é o
          argumento principal da página e ganha em ter espaço próprio. */}
      <div id="demonstracao" className="mx-auto mt-14 max-w-5xl scroll-mt-20 px-6 md:mt-20">
        <div className="mb-5 flex flex-col gap-1 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
              A plataforma em 40 segundos
            </span>
            <h2 className="mt-1.5 text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">
              Do primeiro contacto à sentença, sem sair do sistema
            </h2>
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400 sm:max-w-xs sm:text-right">
            O mesmo processo, visto de cinco ângulos.
          </p>
        </div>

        <AppPreview />
      </div>
    </section>
  );
}
