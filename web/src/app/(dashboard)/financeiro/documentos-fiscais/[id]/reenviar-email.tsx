"use client";

import { Send } from "lucide-react";
import * as React from "react";

import { EntregaEmailBadge } from "@/components/shared/entrega-email-badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useReenviarEmail } from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import {
  COPY_ENVIAR,
  COPY_REENVIAR,
  interpretarErroReenvio,
  rotuloReenviar,
} from "@/lib/entrega-email";
import type { DocumentoFiscalDetalhe, EntregaEmail } from "@/types/faturacao";

// Phase 137 (ENTR-04; 137-UI-SPEC Surface 1d): "Reenviar email" / "Enviar email" -- botão + diálogo
// de confirmação de um só passo + mutação. Quem decide se o botão existe é o cartão (gate EXATO
// financeiro:edit e entregaEmail.reenviavel); o backend volta a verificar a autoridade e regras.
// Os erros são traduzidos em copy fixa por interpretarErroReenvio (nunca o texto do backend).

type Banner = { mensagem: string; definitivo: boolean };

const BLOCO = "space-y-2 rounded-md bg-slate-50 p-4 text-sm dark:bg-slate-900";
const AJUDA = "text-xs text-slate-600 dark:text-slate-400";

export function ReenviarEmail({
  documento,
  entregaEmail,
  tituloCardRef,
  botaoVisivel = true,
  onAbertoChange,
}: {
  documento: DocumentoFiscalDetalhe;
  entregaEmail: EntregaEmail | null | undefined;
  tituloCardRef?: React.RefObject<HTMLHeadingElement | null>;
  botaoVisivel?: boolean;
  onAbertoChange?: (aberto: boolean) => void;
}) {
  const reenviar = useReenviarEmail(documento.id);
  const [aberto, setAbertoLocal] = React.useState(false);
  const setAberto = (valor: boolean) => {
    setAbertoLocal(valor);
    onAbertoChange?.(valor);
  };
  const [banner, setBanner] = React.useState<Banner | null>(null);
  const [aReenviar, setAReenviar] = React.useState(false);
  const aReenviarRef = React.useRef(false);
  const triggerRef = React.useRef<HTMLButtonElement>(null);
  const tituloRef = React.useRef<HTMLHeadingElement>(null);
  const bannerRef = React.useRef<HTMLDivElement>(null);
  const focarCartaoRef = React.useRef(false);

  React.useEffect(() => {
    if (banner) bannerRef.current?.focus();
  }, [banner]);

  const abrir = () => {
    setBanner(null);
    setAberto(true);
  };

  const fechar = () => {
    if (aReenviarRef.current) return;
    setAberto(false);
  };

  const rotulo = rotuloReenviar(entregaEmail?.estado);
  const aDecorrer = entregaEmail?.estado === "SEM_EMAIL" ? "A enviar..." : "A reenviar...";

  const confirmar = async () => {
    if (aReenviarRef.current) return;
    aReenviarRef.current = true;
    setAReenviar(true);
    setBanner(null);
    try {
      await reenviar.mutateAsync();
      focarCartaoRef.current = true;
      aReenviarRef.current = false;
      setAberto(false);
      toast.success("Email colocado na fila de envio.");
    } catch (e) {
      const erro = interpretarErroReenvio(e);
      aReenviarRef.current = false;
      if (!erro || erro.fecharDialogo) {
        setAberto(false);
      } else {
        setBanner({ mensagem: erro.mensagem, definitivo: erro.definitivo });
      }
    } finally {
      aReenviarRef.current = false;
      setAReenviar(false);
    }
  };

  const bloquearFecho = (e: Event) => {
    if (aReenviar) e.preventDefault();
  };

  const definitivo = banner?.definitivo ?? false;

  return (
    <>
      <Button asChild variant="outline">
        <button ref={triggerRef} type="button" onClick={abrir}>
          <Send className="h-4 w-4" aria-hidden="true" />
          {rotulo}
        </button>
      </Button>

      <Dialog
        open={aberto}
        onOpenChange={(abrirDialogo) => {
          if (!abrirDialogo) fechar();
        }}
      >
        <DialogContent
          closeLabel="Fechar reenvio do email"
          onEscapeKeyDown={bloquearFecho}
          onPointerDownOutside={bloquearFecho}
          onInteractOutside={bloquearFecho}
          onOpenAutoFocus={(e) => {
            e.preventDefault();
            tituloRef.current?.focus();
          }}
          onCloseAutoFocus={(e) => {
            e.preventDefault();
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
              {rotulo}
            </DialogTitle>
            <DialogDescription className="text-sm text-slate-500 dark:text-slate-400">
              O documento será enviado ao cliente com o PDF e o XML em anexo. O envio é feito em segundo
              plano e o documento não é alterado.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <dl className={`${BLOCO} grid grid-cols-[1fr_auto] gap-2 space-y-0`}>
              <dt>Documento</dt>
              <dd className="text-right font-mono">{documento.numeroFormatado}</dd>

              <dt>Destinatário</dt>
              <dd className="text-right text-sm break-all">
                {entregaEmail?.estado === "SEM_EMAIL" ? (
                  entregaEmail.emailDestinatario ? (
                    entregaEmail.emailDestinatario
                  ) : (
                    <span className="text-slate-600 dark:text-slate-400">
                      O cliente não tem email na ficha
                    </span>
                  )
                ) : (
                  entregaEmail?.destinatario ?? "—"
                )}
              </dd>

              <dt>Estado atual</dt>
              <dd className="text-right">
                <EntregaEmailBadge estado={entregaEmail?.estado} />
              </dd>

              <dt>Tentativas</dt>
              <dd className="text-right tabular-nums">{entregaEmail?.tentativas ?? 0}</dd>
            </dl>

            <div className="space-y-1">
              <p className={AJUDA}>O contador de tentativas volta a zero.</p>
              {entregaEmail?.estado === "ENVIADO" ? (
                <p className={AJUDA}>
                  Este documento já foi enviado. O cliente vai receber um novo email.
                </p>
              ) : null}
              {entregaEmail?.estado === "SEM_EMAIL" ? (
                <p className={AJUDA}>Será usado o email registado agora na ficha do cliente.</p>
              ) : null}
            </div>

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
            <Button type="button" variant="outline" onClick={fechar} disabled={aReenviar}>
              {definitivo ? "Fechar" : "Fechar sem reenviar"}
            </Button>
            <Button type="button" onClick={confirmar} disabled={aReenviar || definitivo}>
              {aReenviar ? aDecorrer : rotulo}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
