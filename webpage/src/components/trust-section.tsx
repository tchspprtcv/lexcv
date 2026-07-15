import { Building2, History, Lock, ShieldCheck } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const CONFIANCA = [
  { icon: Lock, title: "Isolamento Multi-Tenant", desc: "Cada instituição opera em ambiente de dados totalmente isolado, sem partilha entre clientes da plataforma." },
  { icon: ShieldCheck, title: "RBAC Granular", desc: "Perfis Admin, Advogado, Técnico e Assistente com permissões específicas por módulo e ação." },
  { icon: History, title: "Trilha de Auditoria", desc: "Alterações relevantes — fases, atribuições, documentos — ficam registadas e rastreáveis." },
  { icon: Building2, title: "Ecossistema Cabo Verde", desc: "Desenhado para a realidade institucional cabo-verdiana, alinhado ao ecossistema NOSi." },
];

export function TrustSection() {
  return (
    <section id="confianca" className="border-t border-slate-200 py-12 dark:border-slate-800 md:py-16 lg:py-16">
      <div className="mx-auto max-w-7xl px-6">
        <span className="inline-flex items-center border border-slate-300 px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:border-slate-700 dark:text-slate-400">
          CONFIANÇA INSTITUCIONAL
        </span>
        <h2 className="mt-4 text-2xl font-semibold text-slate-900 dark:text-slate-50">
          Construído para instituições exigentes
        </h2>
        <div className="mt-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {CONFIANCA.map(({ icon: Icon, title, desc }) => (
            <Card key={title}>
              <CardHeader>
                <Icon className="h-6 w-6 text-slate-900 dark:text-slate-100" />
                <CardTitle className="text-2xl">{title}</CardTitle>
                <CardDescription className="text-base">{desc}</CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
