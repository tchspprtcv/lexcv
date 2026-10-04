"use client";

import * as React from "react";
import { Info } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Separator } from "@/components/ui/separator";
import type { PreVisualizacaoFatura } from "@/types/faturacao";

// Diálogo de confirmação da Fatura-Recibo (134-UI-SPEC Surface 1, D-02). Mostra SÓ os valores
// devolvidos por POST /faturacao/pre-visualizacao: este ficheiro não calcula nenhum montante
// (D-01, "frontend burro"); apenas os formata em CVE.

const NOTICE_CLASSES =
  "rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300";

function formatarCVE(valor: number) {
  return valor.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

function formatarData(valor: string) {
  const d = new Date(valor.includes("T") ? valor : `${valor}T00:00:00`);
  if (Number.isNaN(d.getTime())) return valor;
  return d.toLocaleDateString("pt-CV");
}

export function PagamentoFaturadoDialog({
  open,
  preVisualizacao,
  emitindo,
  erroRede,
  onVoltar,
  onEmitir,
}: {
  open: boolean;
  preVisualizacao: PreVisualizacaoFatura | null;
  emitindo: boolean;
  erroRede: string | null;
  onVoltar: () => void;
  onEmitir: () => void;
}) {
  const tituloRef = React.useRef<HTMLHeadingElement>(null);
  const p = preVisualizacao;
  const isento = p?.regimeIva === "ISENTO";

  const bloquearFecho = (e: Event) => {
    if (emitindo) e.preventDefault();
  };

  return (
    <Dialog
      open={open && p !== null}
      onOpenChange={(aberto) => {
        // Enquanto a emissão está em curso o diálogo não pode ser fechado (Esc, overlay ou X).
        if (!aberto && !emitindo) onVoltar();
      }}
    >
      <DialogContent
        closeLabel="Fechar pré-visualização"
        className="max-h-[85vh] space-y-4 overflow-y-auto"
        onOpenAutoFocus={(e) => {
          // Foco no título: o botão de confirmação nunca recebe foco automático (evita um Enter
          // acidental emitir o documento).
          e.preventDefault();
          tituloRef.current?.focus();
        }}
        onEscapeKeyDown={bloquearFecho}
        onPointerDownOutside={bloquearFecho}
        onInteractOutside={bloquearFecho}
      >
        {p ? (
          <>
            <DialogHeader>
              <DialogTitle ref={tituloRef} tabIndex={-1} className="text-xl font-semibold outline-none">
                Confirmar fatura-recibo
              </DialogTitle>
              <DialogDescription className="text-sm text-slate-500 dark:text-slate-400">
                Confira os dados do documento antes de emitir. Depois de emitido, não pode ser alterado nem
                apagado.
              </DialogDescription>
            </DialogHeader>

            <div className={NOTICE_CLASSES}>
              Documento simulado: não tem validade fiscal. Continue a emitir as suas faturas válidas no
              software de faturação homologado.
            </div>

            <div>
              <Badge variant="outline" className="gap-1">
                <Info className="h-3 w-3" aria-hidden="true" />
                Simulação — sem validade fiscal
              </Badge>
            </div>

            <div className="space-y-1 rounded-md bg-slate-50 p-4 text-sm dark:bg-slate-900">
              <p className="font-semibold">Adquirente</p>
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
                <dt className="text-slate-500 dark:text-slate-400">Nome</dt>
                <dd>{p.adquirenteNome}</dd>
                <dt className="text-slate-500 dark:text-slate-400">NIF</dt>
                <dd className="font-mono">{p.adquirenteNif}</dd>
                <dt className="text-slate-500 dark:text-slate-400">Morada</dt>
                <dd>{p.adquirenteMorada}</dd>
              </dl>
            </div>

            <p className="text-sm">{p.descricaoLinha}</p>

            <div className="space-y-2 rounded-md bg-slate-50 p-4 dark:bg-slate-900">
              <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
                <dt>Base tributável</dt>
                <dd className="text-right tabular-nums">{formatarCVE(p.base)}</dd>

                {isento ? (
                  <>
                    <dt>IVA</dt>
                    <dd className="text-right">Isento</dd>
                    <dt>Motivo de isenção</dt>
                    <dd className="text-right">
                      {p.motivoIsencaoCodigo} — {p.motivoIsencaoDescricao}
                    </dd>
                  </>
                ) : (
                  <>
                    <dt>IVA ({p.taxaIva}%)</dt>
                    <dd className="text-right tabular-nums">{formatarCVE(p.iva)}</dd>
                  </>
                )}

                {p.retencao > 0 ? (
                  <>
                    <dt>Retenção na fonte ({p.taxaRetencao}%)</dt>
                    <dd className="text-right tabular-nums">- {formatarCVE(p.retencao)}</dd>
                  </>
                ) : null}
              </dl>

              <Separator />

              <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm font-semibold">
                <dt>Total</dt>
                <dd className="text-right tabular-nums">{formatarCVE(p.total)}</dd>
                <dt>Líquido recebido</dt>
                <dd className="text-right tabular-nums">{formatarCVE(p.liquidoRecebido)}</dd>
              </dl>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                A conta corrente do cliente é creditada do total.
              </p>
            </div>

            <p className="text-sm text-slate-700 dark:text-slate-300">
              Método de pagamento: {p.metodoRotulo} · Data: {formatarData(p.dataEmissao)}
            </p>

            {erroRede ? (
              <p role="alert" className="text-sm text-red-600 dark:text-red-400">
                {erroRede}
              </p>
            ) : null}

            <DialogFooter className="gap-2">
              <Button type="button" variant="outline" onClick={onVoltar} disabled={emitindo}>
                Voltar e editar
              </Button>
              <Button type="button" onClick={onEmitir} disabled={emitindo}>
                {emitindo ? "A emitir..." : "Emitir fatura-recibo"}
              </Button>
            </DialogFooter>
          </>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
