import { ChevronDown } from "lucide-react";

import { EMAIL_CONTACTO } from "@/lib/contacto";

/**
 * As perguntas que um escritório faz antes de decidir.
 *
 * Nenhuma resposta promete o que o produto não faz. Sobre preço: a estrutura
 * de planos ainda é uma decisão comercial em aberto, por isso a resposta
 * encaminha para conversa em vez de inventar uma tabela — uma tabela errada no
 * site é pior do que nenhuma.
 *
 * Em <details>/<summary>: abre sem JavaScript, é navegável por teclado e o
 * conteúdo fica no HTML, portanto conta para pesquisa.
 */
const PERGUNTAS = [
  {
    q: "Outro escritório pode ver os nossos processos?",
    a: "Não. Cada escritório trabalha sobre dados próprios e a separação é verificada no servidor a cada pedido, não apenas escondida no interface. Clientes, processos, documentos e valores de um escritório não são alcançáveis a partir de outro.",
  },
  {
    q: "Dentro do escritório, quem vê o quê?",
    a: "Define-se por perfil. Há administrador, advogado, técnico e assistente, e as permissões são por módulo e por ação — consultar, criar, editar, gerir. Um assistente pode registar clientes sem aceder ao financeiro, por exemplo. A verificação é feita nas duas camadas: o ecrã esconde e o servidor recusa.",
  },
  {
    q: "Onde ficam os ficheiros que carregamos?",
    a: "Em armazenamento de objeto, fora do disco do servidor da aplicação. O acesso é feito por ligações temporárias geradas no momento do download, que expiram — não há pastas públicas nem endereços permanentes para as peças.",
  },
  {
    q: "Fica registo de quem fez o quê?",
    a: "Fica. Criação e edição de registos, anexação de peças e mudanças de estado ficam na trilha de auditoria do processo, com autor e data, disponível para conferência interna.",
  },
  {
    q: "Está em português e adaptado a Cabo Verde?",
    a: "Sim, e não por tradução. A terminologia é a da prática forense cabo-verdiana, os documentos de identificação são NIF e CNI/BI, as comarcas são as do país e os valores estão em escudos.",
  },
  {
    q: "Quanto custa?",
    a: `Depende da dimensão do escritório e do número de utilizadores. Preferimos falar antes de indicar um valor, para não vender mais do que precisa. Escreva para ${EMAIL_CONTACTO} e respondemos com uma proposta concreta.`,
  },
];

export function FaqSection() {
  return (
    <section
      id="perguntas"
      className="scroll-mt-20 border-t border-slate-200 bg-slate-50/60 py-12 dark:border-slate-800 dark:bg-slate-900/20 md:py-16"
    >
      <div className="mx-auto max-w-7xl px-6">
        <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
          Perguntas frequentes
        </span>
        <h2 className="mt-3 text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50 md:text-3xl">
          O que os escritórios perguntam antes de decidir
        </h2>

        <div className="mt-8 max-w-3xl divide-y divide-slate-200 border-y border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {PERGUNTAS.map(({ q, a }) => (
            <details key={q} className="group py-4">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-base font-medium text-slate-900 marker:content-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-4 focus-visible:ring-offset-background dark:text-slate-100">
                {q}
                <ChevronDown className="h-4 w-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180" />
              </summary>
              <p className="mt-3 pr-8 text-base leading-relaxed text-slate-600 dark:text-slate-400">
                {a}
              </p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
