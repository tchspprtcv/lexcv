import { AlertTriangle, FolderX, ScaleIcon, Wallet } from "lucide-react";

/**
 * Nomeia a dor antes de apresentar a solução.
 *
 * A página começava pelos módulos, o que só convence quem já decidiu comprar.
 * Cada entrada aqui é uma situação reconhecível no dia a dia de um escritório,
 * seguida do que muda na plataforma — sem prometer nada que o produto não faça.
 */
const DORES = [
  {
    icon: AlertTriangle,
    dor: "Um prazo que ninguém viu",
    detalhe:
      "O prazo estava na agenda de uma pessoa, ou em papel. Quem ficou com o processo não soube.",
    resposta:
      "Prazos, audiências e diligências numa agenda única, ligados ao processo, com os críticos assinalados.",
  },
  {
    icon: FolderX,
    dor: "Peças espalhadas por email e pen drives",
    detalhe:
      "A versão boa da contestação está na caixa de correio de alguém. Ninguém sabe qual é a última.",
    resposta:
      "Cada peça fica no processo a que pertence, com versão e nível de confidencialidade.",
  },
  {
    icon: ScaleIcon,
    dor: "Conflitos verificados de memória",
    detalhe:
      "Aceitar um mandato contra quem já é constituinte do escritório é um risco deontológico real.",
    resposta:
      "A abertura de processo passa obrigatoriamente por uma verificação de conflito, com decisão registada.",
  },
  {
    icon: Wallet,
    dor: "Honorários que ficam por cobrar",
    detalhe:
      "O valor foi combinado numa reunião. Meses depois, ninguém sabe o que foi pago e o que falta.",
    resposta:
      "Valor acordado, recebimentos e saldo ligados ao processo, com termo de honorários a partir daí.",
  },
];

export function ProblemSection() {
  return (
    <section
      id="problema"
      className="scroll-mt-20 border-t border-slate-200 bg-slate-50/60 py-12 dark:border-slate-800 dark:bg-slate-900/20 md:py-16"
    >
      <div className="mx-auto max-w-7xl px-6">
        <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
          O problema
        </span>
        <h2 className="mt-3 max-w-2xl text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50 md:text-3xl">
          O que custa gerir processos fora de um sistema
        </h2>
        <p className="mt-3 max-w-2xl text-base text-slate-600 dark:text-slate-300">
          Não é falta de competência — é falta de um sítio só. Estas quatro situações repetem-se em
          escritórios de todos os tamanhos.
        </p>

        <div className="mt-10 grid grid-cols-1 gap-x-8 gap-y-8 md:grid-cols-2">
          {DORES.map(({ icon: Icon, dor, detalhe, resposta }) => (
            <div key={dor} className="flex gap-4">
              <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-white shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
                <Icon className="h-4 w-4 text-slate-500 dark:text-slate-400" />
              </div>
              <div className="min-w-0">
                <h3 className="text-base font-semibold text-slate-900 dark:text-slate-50">{dor}</h3>
                <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{detalhe}</p>
                <p className="mt-2 border-l-2 border-blue-600 pl-3 text-sm font-medium text-slate-800 dark:border-blue-400 dark:text-slate-200">
                  {resposta}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
