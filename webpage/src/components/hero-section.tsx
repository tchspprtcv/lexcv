import { ArrowRight } from "lucide-react";
import { AppPreview } from "@/components/app-preview";
import { BrandMark } from "@/components/brand-mark";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { getLoginUrl } from "@/lib/get-login-url";
import type { BrandingResponse } from "@/types/branding";

export function HeroSection({ branding }: { branding: BrandingResponse }) {
  return (
    <section className="border-b border-slate-200 py-12 dark:border-slate-800 md:py-16 lg:py-16">
      <div className="mx-auto max-w-3xl px-6">
        <BrandMark branding={branding} className="mb-6" />
        <div className="mb-4 h-px w-12 bg-blue-600 dark:bg-blue-400" />
        <Card>
          <CardHeader>
            <Badge variant="secondary" className="px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em]">
              PLATAFORMA DE GESTÃO JURÍDICA
            </Badge>
          </CardHeader>
          <CardContent>
            <h1 className="text-5xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">
              Gestão jurídica completa para a sua instituição
            </h1>
            <p className="mt-6 text-base text-slate-600 dark:text-slate-300">
              Clientes, processos, prazos e documentos — tudo num único painel, com isolamento total por tenant.
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-4">
              <Button asChild variant="secondary" className="">
                <a href={getLoginUrl()}>Entrar</a>
              </Button>
              <Button asChild variant="ghost" className="">
                <a href="#funcionalidades">Ver Funcionalidades<ArrowRight className="h-4 w-4" /></a>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* A demonstração fica fora do cartão e num contentor mais largo: é o
          argumento principal da página e ganha em ter espaço próprio. */}
      <div className="mx-auto mt-12 max-w-5xl px-6 md:mt-16">
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
