"use client";

import * as React from "react";
import { Info } from "lucide-react";

import { ComunicacaoEstadoBadge } from "@/components/shared/comunicacao-estado-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { podeReprocessarComunicacao } from "@/hooks/use-faturacao";
import { usePermissions } from "@/hooks/use-permissions";
import {
  COPY_CARD_TITULO,
  COPY_IUD_A_GERAR,
  COPY_IUD_TESTE,
  COPY_SEM_COMUNICACAO,
  COPY_SEM_TENTATIVAS,
  COPY_TENTATIVAS_AJUDA,
  COPY_ULTIMA_FALHA,
  ROTULO_AMBIENTE,
  descricaoEstadoComunicacao,
  mostrarReprocessar,
} from "@/lib/comunicacao-fiscal";
import type { DocumentoFiscalDetalhe } from "@/types/faturacao";

import { ReprocessarComunicacao } from "./reprocessar-comunicacao";

// Phase 136 (DFE-04, DFE-05, DFE-06; 136-UI-SPEC Surface 2): cartão SÓ DE LEITURA "Comunicação
// fiscal". Todos os valores vêm do backend (documento.comunicacao); nada é calculado aqui. A
// mutação de reprocessamento vive em reprocessar-comunicacao.tsx -- este ficheiro não faz pedidos.
// O IUD é só texto selecionável (sem QR, sem botão de copiar, sem link), sempre com a marca de
// ambiente de teste. A última falha é texto simples, já sanitizado pelo backend.

const NOTICE_CLASSES =
  "rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300";
const AJUDA = "text-xs text-slate-500 dark:text-slate-400";
const AUSENTE = "text-sm text-slate-500 dark:text-slate-400";

/** dd/MM/yyyy HH:mm, hora de Cabo Verde. */
function formatarDataHora(valor: string): string {
  const d = new Date(valor);
  if (Number.isNaN(d.getTime())) return valor;
  const partes = new Intl.DateTimeFormat("pt-CV", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone: "Atlantic/Cape_Verde",
  }).formatToParts(d);
  const p = (tipo: Intl.DateTimeFormatPartTypes) => partes.find((x) => x.type === tipo)?.value ?? "";
  return `${p("day")}/${p("month")}/${p("year")} ${p("hour")}:${p("minute")}`;
}

export function ComunicacaoFiscalCard({
  documento,
  modoComunicacao,
}: {
  documento: DocumentoFiscalDetalhe;
  /** Modo reportado pelo backend em GET /faturacao/estado-emissao. */
  modoComunicacao: string | null | undefined;
}) {
  const { permissions } = usePermissions();
  const podeReprocessar = podeReprocessarComunicacao(permissions);
  const tituloRef = React.useRef<HTMLHeadingElement>(null);
  const c = documento.comunicacao;
  const mostrarBotao = mostrarReprocessar(podeReprocessar, c, modoComunicacao);
  // IN-06: o reprocessamento atualiza o documento ANTES de o diálogo fechar; sem isto o componente
  // (e o diálogo, com o banner de erro) desmontava a meio e o foco ficava perdido no <body>.
  const [dialogoAberto, setDialogoAberto] = React.useState(false);

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 space-y-0">
        <CardTitle ref={tituloRef} tabIndex={-1} className="text-xl font-semibold outline-none">
          {COPY_CARD_TITULO}
        </CardTitle>
        {mostrarBotao || dialogoAberto ? (
          <ReprocessarComunicacao
            documento={documento}
            tituloCardRef={tituloRef}
            botaoVisivel={mostrarBotao}
            onAbertoChange={setDialogoAberto}
          />
        ) : null}
      </CardHeader>
      <CardContent className="space-y-4">
        {!c ? (
          <p className={AUSENTE}>{COPY_SEM_COMUNICACAO}</p>
        ) : (
          <>
            <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-[1fr_auto]">
              <dt>Estado</dt>
              <dd className="space-y-1 sm:text-right">
                <ComunicacaoEstadoBadge
                  estado={c.estado}
                  descricao={descricaoEstadoComunicacao(c.estado, podeReprocessar)}
                />
                <p className={AJUDA}>{descricaoEstadoComunicacao(c.estado, podeReprocessar)}</p>
              </dd>

              <dt>Ambiente</dt>
              <dd className="sm:text-right">{ROTULO_AMBIENTE[c.ambiente] ?? c.ambiente}</dd>

              <dt>IUD</dt>
              <dd className="sm:text-right">
                {c.iud ? (
                  <div className="space-y-2 rounded-md bg-slate-50 p-4 text-left dark:bg-slate-900">
                    <p className="font-mono text-sm break-all select-all">{c.iud}</p>
                    <p className="flex items-center gap-1 text-xs text-slate-600 dark:text-slate-400">
                      <Info className="h-3 w-3 shrink-0" aria-hidden="true" />
                      {COPY_IUD_TESTE}
                    </p>
                  </div>
                ) : (
                  <span className={AUSENTE}>{COPY_IUD_A_GERAR}</span>
                )}
              </dd>

              <dt>Tentativas</dt>
              <dd className="space-y-1 sm:text-right">
                <p className="tabular-nums">{c.tentativas}</p>
                <p className={AJUDA}>{COPY_TENTATIVAS_AJUDA}</p>
              </dd>

              <dt>Última tentativa</dt>
              <dd className="tabular-nums sm:text-right">
                {c.ultimaTentativaEm ? formatarDataHora(c.ultimaTentativaEm) : COPY_SEM_TENTATIVAS}
              </dd>

              {c.estado === "PENDENTE" && c.proximaTentativaEm ? (
                <>
                  <dt>Próxima tentativa</dt>
                  <dd className="tabular-nums sm:text-right">{formatarDataHora(c.proximaTentativaEm)}</dd>
                </>
              ) : null}
            </dl>

            {/* IN-04 da revisão: o backend decide quando há última falha a mostrar (REJEITADO/ERRO e
                PENDENTE depois de uma tentativa falhada); ACEITE_SIMULADO nunca a traz. */}
            {c.ultimoErro && c.estado !== "ACEITE_SIMULADO" ? (
              <div role="status" className={NOTICE_CLASSES}>
                <p className="font-semibold">{COPY_ULTIMA_FALHA}</p>
                <p className="line-clamp-3 break-words whitespace-pre-line">{c.ultimoErro}</p>
              </div>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}
