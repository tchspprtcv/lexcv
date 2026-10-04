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
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAtivarFaturacao, useDesativarFaturacao } from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import { isApiError } from "@/lib/api";
import type { ConfiguracaoFiscal } from "@/types/faturacao";

// Card "Ativação da faturação" (133-UI-SPEC Block 4, CFG-02/CFG-03). Os três estados derivam
// SÓ das flags calculadas no servidor (`ativa`, `podeDesativar`, `documentosEmitidos`) -- nunca
// da lista de séries. A UI esconder o botão de desativar é apenas UX: a regra de
// irreversibilidade é aplicada no backend (FATURACAO_JA_EMITIU).

const ERRO_ATIVAR =
  "Não foi possível ativar a faturação: os dados fiscais estão incompletos. Preencha e guarde todos os campos e tente de novo.";
const ERRO_DESATIVAR =
  "Não é possível desativar a faturação porque já foram emitidos documentos.";

function eErroDeRegra(error: unknown, status: number[]) {
  return isApiError(error) && status.includes(error.status);
}

export function FaturacaoAtivacaoCard({
  configuracao,
  dadosPorGravar,
}: {
  configuracao: ConfiguracaoFiscal | undefined;
  dadosPorGravar: boolean;
}) {
  const ativar = useAtivarFaturacao();
  const desativar = useDesativarFaturacao();
  const [ativarAberto, setAtivarAberto] = React.useState(false);
  const [desativarAberto, setDesativarAberto] = React.useState(false);
  const [erroInline, setErroInline] = React.useState<string | null>(null);

  const abrirAtivar = () => {
    setErroInline(null);
    setAtivarAberto(true);
  };

  const abrirDesativar = () => {
    setErroInline(null);
    setDesativarAberto(true);
  };

  const confirmarAtivar = async (e: React.MouseEvent) => {
    e.preventDefault();
    try {
      await ativar.mutateAsync();
      setAtivarAberto(false);
      toast.success("Faturação ativada.");
    } catch (error) {
      if (eErroDeRegra(error, [409, 422])) {
        setAtivarAberto(false);
        setErroInline(ERRO_ATIVAR);
      }
      // Outros erros: apiFetch ja mostrou o toast; mantemos o AlertDialog aberto.
    }
  };

  const confirmarDesativar = async (e: React.MouseEvent) => {
    e.preventDefault();
    try {
      await desativar.mutateAsync();
      setDesativarAberto(false);
      toast.success("Faturação desativada.");
    } catch (error) {
      if (eErroDeRegra(error, [409])) {
        setDesativarAberto(false);
        setErroInline(ERRO_DESATIVAR);
      }
      // Outros erros: apiFetch ja mostrou o toast; mantemos o AlertDialog aberto.
    }
  };

  const ativarDesabilitado = !configuracao?.completa || dadosPorGravar;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl font-semibold">Ativação da faturação</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {!configuracao ? (
          <div className="text-sm text-slate-500 dark:text-slate-400">A carregar...</div>
        ) : !configuracao.ativa ? (
          <>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              A faturação está desligada. Os pagamentos continuam a ser registados como até agora,
              sem documento fiscal.
            </p>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Button
                type="button"
                onClick={abrirAtivar}
                disabled={ativarDesabilitado}
                aria-describedby={ativarDesabilitado ? "faturacao-ativar-ajuda" : undefined}
              >
                Ativar faturação
              </Button>
              {ativarDesabilitado ? (
                <p id="faturacao-ativar-ajuda" className="text-sm text-slate-500 dark:text-slate-400">
                  Preencha e guarde os dados fiscais para ativar a faturação.
                </p>
              ) : null}
            </div>
          </>
        ) : configuracao.podeDesativar && !configuracao.documentosEmitidos ? (
          <>
            <p className="text-sm text-slate-700 dark:text-slate-300">A faturação está ativa.</p>
            <Button type="button" variant="outline" onClick={abrirDesativar}>
              Desativar faturação
            </Button>
          </>
        ) : (
          <p className="text-sm text-slate-700 dark:text-slate-300">
            A faturação está ativa e não pode ser desligada porque já foram emitidos documentos.
          </p>
        )}

        {erroInline ? (
          <p role="alert" className="text-sm text-red-600 dark:text-red-400">
            {erroInline}
          </p>
        ) : null}
      </CardContent>

      <AlertDialog
        open={ativarAberto}
        onOpenChange={(aberto) => {
          if (!ativar.isPending) setAtivarAberto(aberto);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Ativar faturação?</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-2">
                <p>
                  Ao ativar, cada pagamento registado passa a poder gerar um documento fiscal
                  simulado, com numeração sequencial por série.
                </p>
                <p>
                  Antes de ser emitido o primeiro documento pode voltar a desligar a faturação.
                  Depois do primeiro documento, a faturação já não pode ser desligada e o NIF deixa
                  de poder ser alterado.
                </p>
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={ativar.isPending}>Cancelar</AlertDialogCancel>
            <AlertDialogAction onClick={confirmarAtivar} disabled={ativar.isPending}>
              {ativar.isPending ? "A ativar..." : "Ativar faturação"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={desativarAberto}
        onOpenChange={(aberto) => {
          if (!desativar.isPending) setDesativarAberto(aberto);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Desativar faturação?</AlertDialogTitle>
            <AlertDialogDescription>
              Os pagamentos voltam a ser registados sem documento fiscal. Só é possível desativar
              enquanto não tiver sido emitido nenhum documento.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={desativar.isPending}>Cancelar</AlertDialogCancel>
            {/* Button simples (não AlertDialogAction asChild): o Slot juntaria as classes do
                Action às do Button e o tailwind-merge faria o fundo neutro ganhar ao vermelho.
                O fecho é controlado pelo estado `desativarAberto`. */}
            <Button
              type="button"
              variant="destructive"
              onClick={confirmarDesativar}
              disabled={desativar.isPending}
            >
              {desativar.isPending ? "A desativar..." : "Desativar faturação"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
