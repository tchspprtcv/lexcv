"use client";

import * as React from "react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Info } from "lucide-react";
import { mensagemErroFaturacao, useEmailAutomatico } from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import { isApiError } from "@/lib/api";
import type { ConfiguracaoFiscal } from "@/types/faturacao";

// Card "Envio automático por email" (133-UI-SPEC Block 5, CFG-06; 137-UI-SPEC Surface 7). O Switch NÃO muda de forma
// otimista: o valor mostrado é sempre o do servidor. Ligar exige a declaração explícita (checkbox
// obrigatória, reposta a desmarcada em cada abertura); o backend volta a exigir
// `aceiteDeclaracao` (DECLARACAO_NAO_ACEITE) e regista quem aceitou e quando.

const SWITCH_ID = "faturacao-email-automatico";
const CHECKBOX_ID = "faturacao-email-declaracao";

const NOTICE_CLASSES =
  "rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300";

/** Reutilizado verbatim de processos/[id]/page.tsx:190-195 (toLocaleString("pt-CV")). */
function formatDateTime(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString("pt-CV");
}

export function FaturacaoEmailCard({
  configuracao,
}: {
  configuracao: ConfiguracaoFiscal | undefined;
}) {
  const email = useEmailAutomatico();
  const [dialogoAberto, setDialogoAberto] = React.useState(false);
  const [declaracaoAceite, setDeclaracaoAceite] = React.useState(false);
  const [erroInline, setErroInline] = React.useState<string | null>(null);

  const tratarErroDeRegra = (error: unknown) => {
    if (isApiError(error) && (error.status === 409 || error.status === 422)) {
      const { mensagem } = mensagemErroFaturacao(error);
      setErroInline(mensagem ?? "Não foi possível alterar o envio automático. Tente novamente.");
      return true;
    }
    return false;
  };

  const onCheckedChange = async (ligado: boolean) => {
    setErroInline(null);
    if (ligado) {
      setDeclaracaoAceite(false);
      setDialogoAberto(true);
      return;
    }
    try {
      await email.mutateAsync({ ligado: false });
      toast.success("Envio automático desligado.");
    } catch (error) {
      tratarErroDeRegra(error);
      // Outros erros: apiFetch ja mostrou o toast.
    }
  };

  const confirmarLigar = async (e: React.MouseEvent) => {
    e.preventDefault();
    if (!declaracaoAceite) return;
    try {
      await email.mutateAsync({ ligado: true, aceiteDeclaracao: true });
      setDialogoAberto(false);
      toast.success("Envio automático ligado.");
    } catch (error) {
      if (tratarErroDeRegra(error)) setDialogoAberto(false);
      // Outros erros: apiFetch ja mostrou o toast; mantemos o AlertDialog aberto.
    }
  };

  const desabilitado = !configuracao?.ativa || email.isPending;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl font-semibold">Envio automático por email</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {!configuracao ? (
          <div className="text-sm text-slate-500 dark:text-slate-400">A carregar...</div>
        ) : (
          <>
            <div className="flex items-start justify-between gap-4">
              <div className="space-y-1">
                <Label htmlFor={SWITCH_ID}>Enviar faturas por email ao cliente</Label>
                <p
                  id={`${SWITCH_ID}-ajuda`}
                  className="text-sm text-slate-500 dark:text-slate-400"
                >
                  Desligado por omissão. Quando ligado, cada documento aceite na comunicação é enviado ao cliente por email, com o PDF e o XML em anexo.
                </p>
                {!configuracao.ativa ? (
                  <p
                    id={`${SWITCH_ID}-desligado`}
                    className="text-sm text-slate-500 dark:text-slate-400"
                  >
                    Ative a faturação para poder ligar o envio automático.
                  </p>
                ) : null}
              </div>
              <Switch
                id={SWITCH_ID}
                checked={configuracao.envioEmailAutomatico}
                onCheckedChange={onCheckedChange}
                disabled={desabilitado}
                aria-describedby={
                  configuracao.ativa
                    ? `${SWITCH_ID}-ajuda`
                    : `${SWITCH_ID}-ajuda ${SWITCH_ID}-desligado`
                }
              />
            </div>

            {configuracao.smtpConfigurado === false ? (
              <div role="status" className={NOTICE_CLASSES}>
                <div className="flex items-start gap-2">
                  <Info className="h-4 w-4 shrink-0 mt-0.5 text-slate-500" aria-hidden="true" />
                  <p>
                    O servidor de email não está configurado nesta instalação. Pode ligar a opção, mas os emails só são enviados depois de o administrador configurar o SMTP.
                  </p>
                </div>
              </div>
            ) : null}

            {configuracao.envioEmailAutomatico ? (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {configuracao.envioEmailAceitePorNome
                  ? `Aceite por ${configuracao.envioEmailAceitePorNome} em ${formatDateTime(configuracao.envioEmailAceiteEm ?? undefined)}.`
                  : `Aceite em ${formatDateTime(configuracao.envioEmailAceiteEm ?? undefined)}.`}
              </p>
            ) : null}

            {erroInline ? (
              <p role="alert" className="text-sm text-red-600 dark:text-red-400">
                {erroInline}
              </p>
            ) : null}
          </>
        )}
      </CardContent>

      <AlertDialog
        open={dialogoAberto}
        onOpenChange={(aberto) => {
          if (!email.isPending) setDialogoAberto(aberto);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Ligar envio automático de faturas?</AlertDialogTitle>
            <AlertDialogDescription>
              Os documentos emitidos nesta versão são simulados. Se forem enviados por email a
              clientes, não têm validade fiscal.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="flex items-start gap-3">
            <Checkbox
              id={CHECKBOX_ID}
              checked={declaracaoAceite}
              onCheckedChange={(v) => setDeclaracaoAceite(v === true)}
              disabled={email.isPending}
              className="mt-0.5"
            />
            <Label htmlFor={CHECKBOX_ID} className="leading-snug">
              Compreendo que os documentos simulados não têm validade fiscal
            </Label>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={email.isPending}>Cancelar</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmarLigar}
              disabled={!declaracaoAceite || email.isPending}
            >
              Ligar envio automático
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
