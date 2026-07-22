import { ArrowRight } from "lucide-react";
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
    </section>
  );
}
