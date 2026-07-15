import { Bell, Calendar, FileText, Scale, Users, Wallet } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

const MODULOS = [
  { icon: Users, title: "Clientes", desc: "Gestão completa de clientes, particulares e empresas, com ficha, procuração e conta corrente." },
  { icon: Scale, title: "Processos", desc: "Processos com partes, fases, decisões, factos e testemunhas, com timeline completa." },
  { icon: Calendar, title: "Agenda/Prazos", desc: "Calendário unificado com deteção automática de prazos críticos e eventos recorrentes." },
  { icon: FileText, title: "Documentos", desc: "Upload, download e organização de documentos por cliente e por processo." },
  { icon: Wallet, title: "Financeiro", desc: "Honorários, pagamentos e conta corrente, com termo de honorários imprimível." },
  { icon: Bell, title: "Notificações", desc: "Alertas automáticos de prazos, novas fases, documentos e atribuições, direto no sino." },
];

export function FeaturesSection() {
  return (
    <section id="funcionalidades" className="py-12 md:py-16 lg:py-16">
      <div className="mx-auto max-w-7xl px-6">
        <span className="inline-flex items-center border border-slate-300 px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:border-slate-700 dark:text-slate-400">
          MÓDULOS
        </span>
        <h2 className="mt-4 text-2xl font-semibold text-slate-900 dark:text-slate-50">
          Tudo o que a sua instituição precisa, num só lugar
        </h2>
        <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {MODULOS.map(({ icon: Icon, title, desc }) => (
            <Card key={title}>
              <CardHeader>
                <Icon className="h-6 w-6 text-blue-600 dark:text-blue-400" />
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
