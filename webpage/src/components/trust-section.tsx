import { Building2, History, Lock, ShieldCheck } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * Os mesmos compromissos, ditos na língua de quem compra.
 *
 * "Isolamento Multi-Tenant" e "RBAC Granular" são termos de engenharia: quem
 * decide a compra quer saber se o escritório ao lado vê os seus processos e
 * quem, dentro da casa, consegue abrir o quê. O termo técnico fica na etiqueta,
 * para quem o procura.
 */
const CONFIANCA = [
  {
    icon: Lock,
    etiqueta: "Isolamento por escritório",
    title: "Nenhum outro escritório vê os seus processos",
    desc: "Cada instituição trabalha sobre dados próprios, separados dos restantes. A separação é imposta no servidor, não apenas escondida no ecrã.",
  },
  {
    icon: ShieldCheck,
    etiqueta: "Perfis e permissões",
    title: "Cada pessoa vê o que lhe compete",
    desc: "Perfis de administrador, advogado, técnico e assistente, com permissões por módulo e por ação — consultar, criar, editar, gerir.",
  },
  {
    icon: History,
    etiqueta: "Trilha de auditoria",
    title: "Fica registado quem fez o quê",
    desc: "Criações, edições, anexação de peças e mudanças de estado ficam registadas com autor e data, para conferência interna.",
  },
  {
    icon: Building2,
    etiqueta: "Contexto cabo-verdiano",
    title: "Feito para a prática em Cabo Verde",
    desc: "Terminologia forense em português, NIF e CNI/BI, comarcas do país e valores em escudos — sem adaptações improvisadas.",
  },
];

export function TrustSection() {
  return (
    <section
      id="confianca"
      className="scroll-mt-20 border-t border-slate-200 py-12 dark:border-slate-800 md:py-16"
    >
      <div className="mx-auto max-w-7xl px-6">
        <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
          Confiança institucional
        </span>
        <h2 className="mt-3 max-w-2xl text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50 md:text-3xl">
          O sigilo profissional não é uma opção de configuração
        </h2>
        <p className="mt-3 max-w-2xl text-base text-slate-600 dark:text-slate-300">
          Num escritório de advogados, quem vê o quê é matéria deontológica. A plataforma foi
          construída a pensar nisso.
        </p>

        <div className="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {CONFIANCA.map(({ icon: Icon, etiqueta, title, desc }) => (
            <Card key={title}>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Icon className="h-5 w-5 text-slate-900 dark:text-slate-100" />
                  <span className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                    {etiqueta}
                  </span>
                </div>
                <CardTitle className="mt-2 text-xl">{title}</CardTitle>
                <CardDescription className="text-base">{desc}</CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
