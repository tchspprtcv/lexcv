"use client";

import * as React from "react";
import {
  Bell,
  Calendar,
  FileText,
  LayoutDashboard,
  Scale,
  Search,
  ShieldCheck,
  Users,
  Wallet,
} from "lucide-react";

import { cn } from "@/lib/utils";

/**
 * Animação de introdução à plataforma: uma maquete do interface que percorre o
 * percurso real de um processo, do registo do cliente ao acordo de honorários.
 *
 * É UI sintética, não capturas de ecrã. As capturas do manual são de tema
 * escuro e pesam ~100 KB cada; aqui os dois temas do site são respeitados,
 * mantém-se nítido em qualquer tamanho e não há imagens a carregar.
 *
 * A animação pára quando sai do ecrã e quando o visitante pediu menos
 * movimento — nesse caso fica o primeiro passo, navegável pelos botões.
 */

const DURACAO_PASSO_MS = 4200;

type Modulo = {
  icone: React.ComponentType<{ className?: string }>;
  nome: string;
};

/** Barra lateral da maquete, a espelhar a navegação real da aplicação. */
const NAVEGACAO: Modulo[] = [
  { icone: LayoutDashboard, nome: "Dashboard" },
  { icone: Users, nome: "Clientes" },
  { icone: Scale, nome: "Processos" },
  { icone: Calendar, nome: "Agenda" },
  { icone: FileText, nome: "Documentos" },
  { icone: Wallet, nome: "Financeiro" },
];

type Passo = {
  /** Índice em NAVEGACAO que fica ativo. */
  modulo: number;
  etiqueta: string;
  titulo: string;
  descricao: string;
  /** Linha da trilha de auditoria correspondente ao passo. */
  auditoria: string;
  conteudo: React.ReactNode;
};

/* ------------------------------------------------------------------ átomos */

function Etiqueta({ children }: { children: React.ReactNode }) {
  return (
    <span className="text-[9px] font-semibold uppercase tracking-[0.14em] text-slate-400 dark:text-slate-500">
      {children}
    </span>
  );
}

function Chip({
  children,
  tom = "neutro",
  className,
}: {
  children: React.ReactNode;
  tom?: "neutro" | "verde" | "ambar" | "vermelho" | "azul";
  className?: string;
}) {
  const tons = {
    neutro: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300",
    verde: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400",
    ambar: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400",
    vermelho: "bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-400",
    azul: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-400",
  } as const;
  return (
    <span
      className={cn(
        "inline-flex items-center rounded px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wider",
        tons[tom],
        className,
      )}
    >
      {children}
    </span>
  );
}

/** Linha de campo "rótulo → valor", como nas fichas da aplicação. */
function Campo({
  rotulo,
  valor,
  atraso,
  extra,
}: {
  rotulo: string;
  valor: string;
  atraso: number;
  extra?: React.ReactNode;
}) {
  return (
    <div
      className="lex-rise flex items-center justify-between border-b border-slate-100 py-1.5 last:border-0 dark:border-slate-800/80"
      style={{ animationDelay: `${atraso}ms` }}
    >
      <Etiqueta>{rotulo}</Etiqueta>
      <span className="flex items-center gap-1.5 text-[11px] font-medium text-slate-700 dark:text-slate-200">
        {valor}
        {extra}
      </span>
    </div>
  );
}

function Painel({
  titulo,
  acao,
  children,
}: {
  titulo: string;
  acao?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-md border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900/40">
      <div className="mb-1.5 flex items-center justify-between">
        <span className="text-[11px] font-semibold text-slate-900 dark:text-slate-100">{titulo}</span>
        {acao}
      </div>
      {children}
    </div>
  );
}

/* ------------------------------------------------------------------ passos */

/** Grelha de duas colunas, como os ecrãs reais da aplicação. */
function Duas({
  children,
  proporcao = "md:grid-cols-[1.35fr_1fr]",
}: {
  children: React.ReactNode;
  proporcao?: string;
}) {
  return <div className={cn("grid grid-cols-1 gap-2", proporcao)}>{children}</div>;
}

function CenaCliente() {
  return (
    <Duas>
      <Painel titulo="Ficha do cliente" acao={<Chip tom="azul">CLI-0004</Chip>}>
        <Campo rotulo="Nome" valor="Edemilson Pereira" atraso={80} />
        <Campo rotulo="NIF" valor="233 309 380" atraso={180} />
        <Campo rotulo="Documento" valor="CNI 19890122M001Z" atraso={280} />
        <Campo rotulo="Localidade" valor="Praia, Santiago" atraso={380} />
      </Painel>
      <div className="space-y-2">
        <Painel titulo="Conta-corrente">
          <div className="lex-pop py-0.5" style={{ animationDelay: "420ms" }}>
            <div className="text-lg font-semibold tracking-tight text-slate-900 tabular-nums dark:text-slate-50">
              0<span className="text-sm text-slate-400">$00</span>
            </div>
            <span className="text-[10px] text-slate-500 dark:text-slate-400">
              aberta com o registo
            </span>
          </div>
        </Painel>
        <Painel titulo="Procuração">
          <div className="lex-rise" style={{ animationDelay: "560ms" }}>
            <Chip tom="ambar">Em falta</Chip>
            <p className="mt-1 text-[10px] leading-snug text-slate-500 dark:text-slate-400">
              Assinalada até ser digitalizada.
            </p>
          </div>
        </Painel>
      </div>
    </Duas>
  );
}

function CenaProcesso() {
  return (
    <Duas>
      <Painel titulo="Processo 7373" acao={<Chip tom="verde">Ativo</Chip>}>
        <Campo rotulo="Tribunal" valor="Comarca da Praia" atraso={80} />
        <Campo rotulo="Juízo" valor="1.º Criminal" atraso={170} />
        <Campo rotulo="Área" valor="Penal" atraso={260} />
        <Campo rotulo="Cliente" valor="Edemilson Pereira" atraso={350} />
      </Painel>
      <div
        className="lex-pop flex flex-col justify-center rounded-md border border-emerald-200 bg-emerald-50 p-3 dark:border-emerald-500/30 dark:bg-emerald-500/10"
        style={{ animationDelay: "520ms" }}
      >
        <ShieldCheck className="mb-1.5 h-5 w-5 text-emerald-600 dark:text-emerald-400" />
        <div className="text-[11px] font-semibold text-emerald-800 dark:text-emerald-300">
          Conflict check
        </div>
        <div className="mt-0.5 text-sm font-semibold text-emerald-700 dark:text-emerald-400">
          Sem conflito
        </div>
        <p className="mt-1 text-[10px] leading-snug text-emerald-700/75 dark:text-emerald-400/75">
          Verificado contra a carteira do escritório antes de formalizar.
        </p>
      </div>
    </Duas>
  );
}

function CenaAgenda() {
  // Setembro de 2026 começa a uma terça: duas células vazias à cabeça.
  const celulas = [null, null, ...Array.from({ length: 30 }, (_, i) => i + 1)];
  return (
    <Duas proporcao="md:grid-cols-[0.85fr_1fr]">
      <Painel titulo="Setembro 2026" acao={<Chip tom="vermelho">1 prazo fatal</Chip>}>
        <div className="grid max-w-[232px] grid-cols-7 gap-[3px]">
          {["S", "T", "Q", "Q", "S", "S", "D"].map((d, i) => (
            <div key={i} className="text-center text-[8px] font-semibold uppercase text-slate-300 dark:text-slate-600">
              {d}
            </div>
          ))}
          {celulas.map((dia, i) => {
            if (dia === null) return <div key={`v${i}`} />;
            const audiencia = dia === 23;
            const prazo = dia === 21;
            return (
              <div
                key={dia}
                className={cn(
                  "lex-fade flex aspect-square items-center justify-center rounded-[3px] text-[9px] font-medium",
                  audiencia
                    ? "bg-red-500 font-semibold text-white"
                    : prazo
                      ? "bg-amber-400 font-semibold text-amber-950"
                      : "bg-slate-100 text-slate-400 dark:bg-slate-800/70 dark:text-slate-500",
                )}
                style={{ animationDelay: `${i * 11}ms` }}
              >
                {dia}
              </div>
            );
          })}
        </div>
      </Painel>
      <div className="space-y-1.5">
        <Etiqueta>Próximos atos</Etiqueta>
        <div className="lex-rise flex items-center justify-between rounded border-l-2 border-red-500 bg-red-50 px-2 py-1.5 dark:bg-red-500/10" style={{ animationDelay: "480ms" }}>
          <span className="text-[10px] font-medium text-slate-700 dark:text-slate-200">Audiência de julgamento</span>
          <span className="text-[10px] font-semibold text-red-600 dark:text-red-400">23/09</span>
        </div>
        <div className="lex-rise flex items-center justify-between rounded border-l-2 border-amber-400 bg-amber-50 px-2 py-1.5 dark:bg-amber-500/10" style={{ animationDelay: "600ms" }}>
          <span className="text-[10px] font-medium text-slate-700 dark:text-slate-200">Alegações escritas</span>
          <span className="text-[10px] font-semibold text-amber-600 dark:text-amber-400">21/09</span>
        </div>
        <div className="lex-rise flex items-center justify-between rounded border-l-2 border-slate-300 bg-slate-50 px-2 py-1.5 dark:border-slate-600 dark:bg-slate-800/50" style={{ animationDelay: "720ms" }}>
          <span className="text-[10px] font-medium text-slate-700 dark:text-slate-200">Diligência na conservatória</span>
          <span className="text-[10px] font-semibold text-slate-500 dark:text-slate-400">25/09</span>
        </div>
      </div>
    </Duas>
  );
}

function CenaDocumentos() {
  const pecas = [
    { nome: "Procuração forense", chip: "Restrito", tom: "vermelho" as const, versao: "v1" },
    { nome: "Contestação", chip: "Peça", tom: "azul" as const, versao: "v2" },
    { nome: "Taxa de justiça", chip: "Comprovativo", tom: "neutro" as const, versao: "v1" },
  ];
  return (
    <Duas>
      <Painel titulo="Documentos do processo" acao={<Chip tom="neutro">3 peças</Chip>}>
        <div className="space-y-1">
          {pecas.map((p, i) => (
            <div
              key={p.nome}
              className="lex-rise flex items-center justify-between rounded bg-slate-50 px-2 py-1.5 dark:bg-slate-800/50"
              style={{ animationDelay: `${120 + i * 130}ms` }}
            >
              <span className="flex min-w-0 items-center gap-1.5">
                <FileText className="h-3 w-3 shrink-0 text-slate-400" />
                <span className="truncate text-[10px] font-medium text-slate-700 dark:text-slate-200">{p.nome}</span>
              </span>
              <span className="flex shrink-0 items-center gap-1">
                <Chip tom={p.tom}>{p.chip}</Chip>
                <span className="text-[9px] font-semibold text-slate-400">{p.versao}</span>
              </span>
            </div>
          ))}
        </div>
      </Painel>
      <Painel titulo="Nova versão">
        <div className="lex-rise" style={{ animationDelay: "520ms" }}>
          <div className="mb-1 flex items-center justify-between">
            <span className="truncate text-[10px] font-medium text-slate-600 dark:text-slate-300">
              Contestacao_v2.pdf
            </span>
            <span className="text-[9px] font-semibold text-slate-400">100%</span>
          </div>
          <div className="h-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
            <div className="lex-fill h-full rounded-full bg-blue-600 dark:bg-blue-400" style={{ animationDelay: "580ms" }} />
          </div>
          <p className="mt-2 text-[10px] leading-snug text-slate-500 dark:text-slate-400">
            Substitui a versão anterior sem apagar o histórico.
          </p>
        </div>
      </Painel>
    </Duas>
  );
}

function CenaFinanceiro() {
  return (
    <Duas>
      <Painel titulo="Honorários — processo 7373">
        <div className="lex-pop py-0.5" style={{ animationDelay: "120ms" }}>
          <div className="text-2xl font-semibold tracking-tight text-slate-900 tabular-nums dark:text-slate-50">
            180.000<span className="text-base text-slate-400">$00</span>
            <span className="ml-1.5 text-[10px] font-medium uppercase text-slate-400">CVE</span>
          </div>
        </div>
        <Campo rotulo="Acordo" valor="13/09/2026" atraso={280} />
        <Campo rotulo="Condições" valor="50% entrada · 50% julgamento" atraso={380} />
      </Painel>
      <div className="space-y-2">
        <Painel titulo="Recebido">
          <div className="lex-rise" style={{ animationDelay: "460ms" }}>
            <div className="mb-1 flex items-baseline justify-between">
              <span className="text-sm font-semibold tabular-nums text-slate-900 dark:text-slate-50">
                90.000<span className="text-[11px] text-slate-400">$00</span>
              </span>
              <span className="text-[9px] font-semibold text-slate-400">50%</span>
            </div>
            <div className="h-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
              <div
                className="lex-fill h-full w-1/2 rounded-full bg-emerald-500"
                style={{ animationDelay: "520ms" }}
              />
            </div>
          </div>
        </Painel>
        <Painel titulo="Termo de honorários" acao={<Chip tom="verde">Pronto</Chip>}>
          <p className="lex-rise text-[10px] leading-snug text-slate-500 dark:text-slate-400" style={{ animationDelay: "640ms" }}>
            Só sai depois do valor estar lançado.
          </p>
        </Painel>
      </div>
    </Duas>
  );
}

const PASSOS: Passo[] = [
  {
    modulo: 1,
    etiqueta: "Clientes",
    titulo: "O cliente entra uma vez",
    descricao:
      "Registo único do constituinte, com NIF, documento e conta-corrente — e serve todos os módulos seguintes.",
    auditoria: "cliente_criado · CLI-0004",
    conteudo: <CenaCliente />,
  },
  {
    modulo: 2,
    etiqueta: "Processos",
    titulo: "Nenhum processo abre sem verificação",
    descricao:
      "A abertura passa obrigatoriamente pelo conflict check. Um conflito impeditivo bloqueia a formalização.",
    auditoria: "conflict_check_decisao · sem conflito",
    conteudo: <CenaProcesso />,
  },
  {
    modulo: 3,
    etiqueta: "Agenda",
    titulo: "Os prazos deixam de se perder",
    descricao:
      "Audiências, diligências e prazos fatais numa só agenda, com aviso antecipado do que é urgente.",
    auditoria: "evento_criado · audiência 23/09",
    conteudo: <CenaAgenda />,
  },
  {
    modulo: 4,
    etiqueta: "Documentos",
    titulo: "O arquivo deixa de andar por email",
    descricao:
      "Cada peça fica no processo a que pertence, com versão e nível de confidencialidade, servida por ligação temporária.",
    auditoria: "documento_versao · Contestacao v2",
    conteudo: <CenaDocumentos />,
  },
  {
    modulo: 5,
    etiqueta: "Financeiro",
    titulo: "O que foi acordado não se perde de vista",
    descricao:
      "Valor, recebimentos e saldo em escudos, ligados ao processo — e o termo de honorários sai daqui.",
    auditoria: "honorario_atualizado · 180.000$00",
    conteudo: <CenaFinanceiro />,
  },
];

/* --------------------------------------------------------------- componente */

export function AppPreview({ className }: { className?: string }) {
  const [passo, setPasso] = React.useState(0);
  const [aCorrer, setACorrer] = React.useState(false);
  const [menosMovimento, setMenosMovimento] = React.useState(false);
  const referencia = React.useRef<HTMLDivElement | null>(null);

  // Lido num efeito, não na renderização: o servidor não sabe a preferência, e
  // decidir aqui manteria o primeiro render igual nos dois lados.
  React.useEffect(() => {
    const consulta = window.matchMedia("(prefers-reduced-motion: reduce)");
    const aplicar = () => setMenosMovimento(consulta.matches);
    aplicar();
    consulta.addEventListener("change", aplicar);
    return () => consulta.removeEventListener("change", aplicar);
  }, []);

  // Só anima enquanto está à vista: fora do ecrã não há nada a mostrar e o
  // temporizador continuaria a forçar repinturas.
  React.useEffect(() => {
    const alvo = referencia.current;
    if (!alvo) return;
    const observador = new IntersectionObserver(
      ([entrada]) => setACorrer(entrada.isIntersecting),
      { threshold: 0.25 },
    );
    observador.observe(alvo);
    return () => observador.disconnect();
  }, []);

  React.useEffect(() => {
    if (!aCorrer || menosMovimento) return;
    const temporizador = window.setInterval(
      () => setPasso((p) => (p + 1) % PASSOS.length),
      DURACAO_PASSO_MS,
    );
    return () => window.clearInterval(temporizador);
  }, [aCorrer, menosMovimento, passo]);

  const atual = PASSOS[passo];

  return (
    <div ref={referencia} className={cn("w-full", className)}>
      {/* Uma descrição para quem não vê a animação. O resto é decorativo: o
          texto de cada passo é apresentado a seguir, em conteúdo real. */}
      <p className="sr-only">
        Demonstração da plataforma em cinco passos: registo de cliente, abertura de processo com
        verificação de conflito de interesses, agenda de prazos, arquivo de documentos e honorários.
      </p>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-[#020617]">
        {/* Barra de janela */}
        <div className="flex items-center gap-1.5 border-b border-slate-200 bg-slate-50 px-3 py-2 dark:border-slate-800 dark:bg-slate-900/60">
          <span className="h-2 w-2 rounded-full bg-slate-300 dark:bg-slate-700" />
          <span className="h-2 w-2 rounded-full bg-slate-300 dark:bg-slate-700" />
          <span className="h-2 w-2 rounded-full bg-slate-300 dark:bg-slate-700" />
          <div className="ml-2 flex flex-1 items-center gap-1.5 rounded bg-white px-2 py-1 text-[10px] text-slate-400 dark:bg-slate-800/60 dark:text-slate-500">
            <Search className="h-2.5 w-2.5" />
            Pesquisar…
          </div>
          <Bell className="h-3 w-3 text-slate-400" />
          <span className="h-4 w-4 rounded-full bg-blue-600 text-center text-[8px] font-bold leading-4 text-white dark:bg-blue-500">
            A
          </span>
        </div>

        <div className="flex">
          {/* Navegação lateral */}
          <nav
            aria-hidden
            className="hidden w-36 shrink-0 border-r border-slate-200 bg-slate-50/60 p-2 dark:border-slate-800 dark:bg-slate-900/30 sm:block"
          >
            {NAVEGACAO.map((item, i) => {
              const ativo = i === atual.modulo;
              const Icone = item.icone;
              return (
                <div
                  key={item.nome}
                  className={cn(
                    "relative mb-0.5 flex items-center gap-2 rounded px-2 py-1.5 text-[11px] transition-colors duration-500",
                    ativo
                      ? "bg-blue-600/10 font-medium text-blue-700 dark:bg-blue-500/15 dark:text-blue-300"
                      : "text-slate-500 dark:text-slate-500",
                  )}
                >
                  {ativo ? (
                    <span className="absolute inset-y-1 left-0 w-0.5 rounded-full bg-blue-600 dark:bg-blue-400" />
                  ) : null}
                  <Icone className="h-3.5 w-3.5 shrink-0" />
                  {item.nome}
                  {ativo ? (
                    <span
                      key={passo}
                      aria-hidden
                      className="lex-cursor absolute -right-1 top-1/2 h-2 w-2 rounded-full bg-blue-600 ring-2 ring-white dark:bg-blue-400 dark:ring-slate-950"
                    />
                  ) : null}
                </div>
              );
            })}
          </nav>

          {/* Conteúdo do passo. A chave força a nova cena a reanimar. */}
          <div className="flex min-h-[252px] flex-1 flex-col overflow-hidden bg-slate-50/40 p-3 dark:bg-transparent md:h-[252px]">
            <div key={passo} className="flex-1">
              {atual.conteudo}
            </div>
            {/* Tudo o que acontece fica registado — o rodapé mostra-o em cada passo. */}
            <div className="mt-2 flex items-center gap-2 border-t border-slate-200 pt-2 dark:border-slate-800">
              <ShieldCheck className="h-3 w-3 shrink-0 text-slate-400" />
              <Etiqueta>Trilha de auditoria</Etiqueta>
              <span
                key={passo}
                className="lex-fade truncate font-mono text-[10px] text-slate-500 dark:text-slate-400"
              >
                {atual.auditoria}
              </span>
              <span className="ml-auto shrink-0 text-[9px] text-slate-400">Administrador</span>
            </div>
          </div>
        </div>
      </div>

      {/* Legenda e navegação por passos */}
      <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div key={passo} className="lex-fade min-h-[3.5rem] sm:max-w-md">
          <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-blue-600 dark:text-blue-400">
            {atual.etiqueta}
          </span>
          <p className="mt-1 text-sm font-medium text-slate-900 dark:text-slate-100">{atual.titulo}</p>
          <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">{atual.descricao}</p>
        </div>

        <div className="flex shrink-0 items-center gap-2" role="tablist" aria-label="Passos da demonstração">
          {PASSOS.map((p, i) => (
            <button
              key={p.etiqueta}
              type="button"
              role="tab"
              aria-selected={i === passo}
              aria-label={`${i + 1}. ${p.titulo}`}
              onClick={() => setPasso(i)}
              className={cn(
                "h-1.5 rounded-full transition-all duration-300",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 focus-visible:ring-offset-background",
                i === passo
                  ? "w-6 bg-blue-600 dark:bg-blue-400"
                  : "w-1.5 bg-slate-300 hover:bg-slate-400 dark:bg-slate-700 dark:hover:bg-slate-600",
              )}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
