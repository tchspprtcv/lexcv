import { Bell, Calendar, FileText, Scale, Users, Wallet } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * Os mesmos seis módulos, descritos pelo que mudam para quem trabalha no
 * escritório em vez da lista de campos que cada ecrã tem. O título de cada
 * cartão passou a ser o resultado; o nome do módulo fica como etiqueta, para
 * quem procura uma funcionalidade concreta continuar a encontrá-la.
 */
const MODULOS = [
  {
    icon: Users,
    modulo: "Clientes",
    titulo: "O constituinte registado uma só vez",
    desc: "Particulares e empresas com NIF, documento e morada. O registo serve processos, agenda, documentos e conta-corrente — sem voltar a escrever nada.",
  },
  {
    icon: Scale,
    modulo: "Processos",
    titulo: "O processo inteiro num sítio",
    desc: "Partes, fases, decisões, factos e testemunhas, com timeline do que aconteceu e quando. Quem pega no processo percebe o estado em minutos.",
  },
  {
    icon: Calendar,
    modulo: "Agenda e prazos",
    titulo: "O prazo deixa de depender de memória",
    desc: "Audiências, diligências e prazos fatais no mesmo calendário, ligados ao processo e assinalados quando se aproximam.",
  },
  {
    icon: FileText,
    modulo: "Documentos",
    titulo: "A última versão é sempre a que está lá",
    desc: "Peças, procurações e comprovativos guardados no processo, com histórico de versões e marcação de confidencialidade.",
  },
  {
    icon: Wallet,
    modulo: "Financeiro",
    titulo: "Saber o que está por cobrar",
    desc: "Honorários, pagamentos e conta-corrente em escudos, ligados ao processo — e o termo de honorários sai daqui.",
  },
  {
    icon: Bell,
    modulo: "Notificações",
    titulo: "O aviso chega a quem tem de agir",
    desc: "Prazos a vencer, fases alteradas e documentos novos chegam ao responsável, em vez de dependerem de alguém se lembrar.",
  },
];

export function FeaturesSection() {
  return (
    <section id="funcionalidades" className="scroll-mt-20 py-12 md:py-16">
      <div className="mx-auto max-w-7xl px-6">
        <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
          Módulos
        </span>
        <h2 className="mt-3 max-w-2xl text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50 md:text-3xl">
          Tudo o que o escritório precisa, num só lugar
        </h2>

        <div className="mt-10 grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {MODULOS.map(({ icon: Icon, modulo, titulo, desc }) => (
            <Card key={modulo}>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <Icon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                  <span className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                    {modulo}
                  </span>
                </div>
                <CardTitle className="mt-2 text-xl">{titulo}</CardTitle>
                <CardDescription className="text-base">{desc}</CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
