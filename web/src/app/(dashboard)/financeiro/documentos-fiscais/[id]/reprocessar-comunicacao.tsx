"use client";

import * as React from "react";
import { RefreshCw } from "lucide-react";

import { ComunicacaoEstadoBadge } from "@/components/shared/comunicacao-estado-badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useReprocessarComunicacao } from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import {
  COPY_DIALOGO_CONTADOR,
  COPY_DIALOGO_DESCRICAO,
  COPY_DIALOGO_FECHAR,
  COPY_DIALOGO_FECHAR_SEM_REPROCESSAR,
  COPY_DIALOGO_REJEITADO,
  COPY_REPROCESSAR,
  COPY_REPROCESSAR_A_DECORRER,
  COPY_REPROCESSAR_SUCESSO,
  interpretarErroReprocessar,
} from "@/lib/comunicacao-fiscal";
import type { DocumentoFiscalDetalhe } from "@/types/faturacao";

// Phase 136 (DFE-05; 136-UI-SPEC Surface 2): "Reprocessar comunicação" -- botão + diálogo de
// confirmação de um só passo + mutação. Quem decide se o botão existe é o cartão (gate EXATO
// financeiro:edit, estado ERRO/REJEITADO e modo SIMULADO via mostrarReprocessar); o backend volta
// a verificar a mesma autoridade. Sem chave de idempotência: repor PENDENTE é idempotente. Os
// erros são traduzidos em copy fixa por interpretarErroReprocessar (nunca o texto do backend).

type Banner = { mensagem: string; definitivo: boolean };

const BLOCO = "space-y-2 rounded-md bg-slate-50 p-4 text-sm dark:bg-slate-900";
const AJUDA = "text-xs text-slate-500 dark:text-slate-400";

export function ReprocessarComunicacao({
  documento,
  tituloCardRef,
  botaoVisivel = true,
  onAbertoChange,
}: {
  documento: DocumentoFiscalDetalhe;
  /** Título do cartão: recebe o foco depois do sucesso (o botão desaparece com o estado PENDENTE). */
  tituloCardRef?: React.RefObject<HTMLHeadingElement | null>;
  /**
   * IN-06: o cartão mantém este componente montado enquanto o diálogo está aberto, mesmo que o
   * estado já não seja ERRO/REJEITADO (o documento é atualizado antes de `mutateAsync` resolver).
   * Com `false`, o botão vai desaparecer: ao fechar, o foco vai para o título do cartão.
   */
  botaoVisivel?: boolean;
  /** Avisa o cartão de que o diálogo abriu/fechou (para o manter montado enquanto está aberto). */
  onAbertoChange?: (aberto: boolean) => void;
}) {
  const reprocessar = useReprocessarComunicacao(documento.id);
  const [aberto, setAbertoLocal] = React.useState(false);
  const setAberto = (valor: boolean) => {
    setAbertoLocal(valor);
    onAbertoChange?.(valor);
  };
  const [banner, setBanner] = React.useState<Banner | null>(null);
  const [aReprocessar, setAReprocessar] = React.useState(false);
  // Guarda síncrona contra duplo clique (o estado só é visível no render seguinte).
  const aReprocessarRef = React.useRef(false);
  const triggerRef = React.useRef<HTMLButtonElement>(null);
  const tituloRef = React.useRef<HTMLHeadingElement>(null);
  const bannerRef = React.useRef<HTMLDivElement>(null);
  const focarCartaoRef = React.useRef(false);

  const comunicacao = documento.comunicacao;

  React.useEffect(() => {
    if (banner) bannerRef.current?.focus();
  }, [banner]);

  const abrir = () => {
    setBanner(null);
    setAberto(true);
  };

  const fechar = () => {
    if (aReprocessarRef.current) return;
    setAberto(false);
  };

  const confirmar = async () => {
    if (aReprocessarRef.current) return;
    aReprocessarRef.current = true;
    setAReprocessar(true);
    setBanner(null);
    try {
      await reprocessar.mutateAsync();
      focarCartaoRef.current = true;
      aReprocessarRef.current = false;
      setAberto(false);
      toast.success(COPY_REPROCESSAR_SUCESSO);
    } catch (e) {
      const erro = interpretarErroReprocessar(e);
      aReprocessarRef.current = false;
      if (!erro || erro.fecharDialogo) {
        // 401/403: o apiFetch já tratou. 404: a página mostra o estado de não encontrado.
        setAberto(false);
      } else {
        // `refrescar` fica a cargo da invalidação em onSettled do hook.
        setBanner({ mensagem: erro.mensagem, definitivo: erro.definitivo });
      }
    } finally {
      aReprocessarRef.current = false;
      setAReprocessar(false);
    }
  };

  const bloquearFecho = (e: Event) => {
    if (aReprocessar) e.preventDefault();
  };

  const definitivo = banner?.definitivo ?? false;

  return (
    <>
      <Button asChild variant="outline">
        <button ref={triggerRef} type="button" onClick={abrir}>
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
          {COPY_REPROCESSAR}
        </button>
      </Button>

      <Dialog
        open={aberto}
        onOpenChange={(abrirDialogo) => {
          // Enquanto o pedido está em curso o diálogo não pode ser fechado (Esc, overlay ou X).
          if (!abrirDialogo) fechar();
        }}
      >
        <DialogContent
          closeLabel="Fechar reprocessamento da comunicação"
          onEscapeKeyDown={bloquearFecho}
          onPointerDownOutside={bloquearFecho}
          onInteractOutside={bloquearFecho}
          onOpenAutoFocus={(e) => {
            // O foco vai para o título; o botão de confirmação nunca recebe foco automático.
            e.preventDefault();
            tituloRef.current?.focus();
          }}
          onCloseAutoFocus={(e) => {
            e.preventDefault();
            // IN-06: depois do sucesso, ou quando o botão já não existe (ex. 409 com o estado
            // atualizado), o foco vai para o título do cartão e nunca fica perdido no <body>.
            if (focarCartaoRef.current || !botaoVisivel) {
              focarCartaoRef.current = false;
              tituloCardRef?.current?.focus();
            } else {
              triggerRef.current?.focus();
            }
          }}
        >
          <DialogHeader>
            <DialogTitle ref={tituloRef} tabIndex={-1} className="text-xl font-semibold outline-none">
              {COPY_REPROCESSAR}
            </DialogTitle>
            <DialogDescription className="text-sm text-slate-500 dark:text-slate-400">
              {COPY_DIALOGO_DESCRICAO}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <dl className={`${BLOCO} grid grid-cols-[1fr_auto] gap-2 space-y-0`}>
              <dt>Documento</dt>
              <dd className="text-right font-mono">{documento.numeroFormatado}</dd>
              <dt>Estado atual</dt>
              <dd className="text-right">
                <ComunicacaoEstadoBadge estado={comunicacao?.estado} />
              </dd>
              <dt>Tentativas</dt>
              <dd className="text-right tabular-nums">{comunicacao?.tentativas ?? 0}</dd>
            </dl>
            <p className={AJUDA}>{COPY_DIALOGO_CONTADOR}</p>
            {comunicacao?.estado === "REJEITADO" ? <p className={AJUDA}>{COPY_DIALOGO_REJEITADO}</p> : null}

            {banner ? (
              <div
                ref={bannerRef}
                tabIndex={-1}
                role="alert"
                className="text-sm text-red-600 outline-none dark:text-red-400"
              >
                {banner.mensagem}
              </div>
            ) : null}
          </div>

          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={fechar} disabled={aReprocessar}>
              {definitivo ? COPY_DIALOGO_FECHAR : COPY_DIALOGO_FECHAR_SEM_REPROCESSAR}
            </Button>
            <Button type="button" onClick={confirmar} disabled={aReprocessar || definitivo}>
              {aReprocessar ? COPY_REPROCESSAR_A_DECORRER : COPY_REPROCESSAR}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
