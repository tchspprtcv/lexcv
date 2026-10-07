"use client";

import * as React from "react";

import { EntregaEmailBadge } from "@/components/shared/entrega-email-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { podeReenviarEmail } from "@/hooks/use-faturacao";
import { usePermissions } from "@/hooks/use-permissions";
import { descricaoEntregaEmail, mostrarReenviar } from "@/lib/entrega-email";
import type { DocumentoFiscalDetalhe } from "@/types/faturacao";

import { ReenviarEmail } from "./reenviar-email";

// Phase 137 (ENTR-03..06; 137-UI-SPEC Surface 1b): cartão SÓ DE LEITURA "Entrega por email". Todos os
// valores vêm do backend (documento.entregaEmail); nada é calculado aqui. A mutação de reenvio vive
// em reenviar-email.tsx -- este ficheiro não faz pedidos. A última falha é texto simples, já sanitizado
// pelo backend (nunca HTML nem transcrições SMTP).

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

export function EntregaEmailCard({
  documento,
}: {
  documento: DocumentoFiscalDetalhe;
}) {
  const { permissions } = usePermissions();
  const podeReenviar = podeReenviarEmail(permissions);
  const tituloRef = React.useRef<HTMLHeadingElement>(null);
  const e = documento.entregaEmail;
  const mostrarBotao = mostrarReenviar(podeReenviar, e);
  const [dialogoAberto, setDialogoAberto] = React.useState(false);

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 space-y-0">
        <CardTitle ref={tituloRef} tabIndex={-1} className="text-xl font-semibold outline-none">
          Entrega por email
        </CardTitle>
        {mostrarBotao || dialogoAberto ? (
          <ReenviarEmail
            documento={documento}
            entregaEmail={e}
            tituloCardRef={tituloRef}
            botaoVisivel={mostrarBotao}
            onAbertoChange={setDialogoAberto}
          />
        ) : null}
      </CardHeader>
      <CardContent className="space-y-4">
        {!e ? (
          <p className={AUSENTE}>O envio por email deste documento ainda não foi registado.</p>
        ) : (
          <>
            <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-[1fr_auto]">
              <dt>Estado</dt>
              <dd className="space-y-1 sm:text-right">
                <EntregaEmailBadge
                  estado={e.estado}
                  titulo={descricaoEntregaEmail(e.estado, {
                    podeReenviar,
                    reenviavel: e.reenviavel,
                    tentativas: e.tentativas,
                  })}
                />
                <p className={AJUDA}>
                  {descricaoEntregaEmail(e.estado, {
                    podeReenviar,
                    reenviavel: e.reenviavel,
                    tentativas: e.tentativas,
                  })}
                </p>
              </dd>

              {e.destinatario ? (
                <>
                  <dt>Destinatário</dt>
                  <dd className="sm:text-right text-sm break-all">{e.destinatario}</dd>
                </>
              ) : null}

              {e.estado === "PENDENTE" || e.estado === "ENVIADO" || e.estado === "FALHOU" ? (
                <>
                  <dt>Tentativas</dt>
                  <dd className="space-y-1 sm:text-right">
                    <p className="tabular-nums">{e.tentativas}</p>
                    <p className={AJUDA}>O envio é tentado automaticamente até 5 vezes.</p>
                  </dd>
                </>
              ) : null}

              {e.estado === "ENVIADO" && e.enviadoEm ? (
                <>
                  <dt>Enviado em</dt>
                  <dd className="tabular-nums sm:text-right">{formatarDataHora(e.enviadoEm)}</dd>
                </>
              ) : null}

              {e.estado === "PENDENTE" || e.estado === "FALHOU" ? (
                <>
                  <dt>Última tentativa</dt>
                  <dd className="tabular-nums sm:text-right">
                    {e.ultimaTentativaEm ? formatarDataHora(e.ultimaTentativaEm) : "Ainda sem tentativas"}
                  </dd>
                </>
              ) : null}

              {e.estado === "PENDENTE" && e.proximaTentativaEm ? (
                <>
                  <dt>Próxima tentativa</dt>
                  <dd className="tabular-nums sm:text-right">{formatarDataHora(e.proximaTentativaEm)}</dd>
                </>
              ) : null}
            </dl>

            {(e.estado === "FALHOU" || e.estado === "PENDENTE") && e.ultimoErro ? (
              <div role="status" className={NOTICE_CLASSES}>
                <p className="font-semibold">Última falha</p>
                <p className="line-clamp-3 break-words whitespace-pre-line">{e.ultimoErro}</p>
              </div>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}
