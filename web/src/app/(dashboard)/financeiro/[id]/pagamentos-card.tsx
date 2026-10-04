"use client";

import Link from "next/link";
import * as React from "react";
import { Trash2 } from "lucide-react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useDeletePagamento } from "@/hooks/use-financeiro";
import { toast } from "@/hooks/use-toast";
import { mensagemGuardaFiscal } from "@/lib/erros-emissao";
import type { Pagamento } from "@/types/financeiro";

// Card "Pagamentos" do honorário (134-UI-SPEC Surface 2 e Surface 5). Coluna "Documento fiscal"
// (D-19, EMIS-12); um pagamento faturado não mostra o botão de apagar (a guarda real é o 409
// PAGAMENTO_FATURADO do backend, mostrado inline no diálogo se alguma vez chegar).

function formatMoneyCVE(v: number | null | undefined) {
  if (v == null) return "A confirmar";
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v.includes("T") ? v : `${v}T00:00:00`);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

function ApagarPagamento({ pagamentoId, honorarioId }: { pagamentoId: number; honorarioId: number }) {
  const deletePagamento = useDeletePagamento();
  const [aberto, setAberto] = React.useState(false);
  const [erroGuarda, setErroGuarda] = React.useState<string | null>(null);

  const confirmar = async (e: React.MouseEvent) => {
    e.preventDefault();
    setErroGuarda(null);
    try {
      await deletePagamento.mutateAsync({ pagamentoId, honorarioId });
      setAberto(false);
      toast.success("Pagamento apagado com sucesso.");
    } catch (error) {
      const guarda = mensagemGuardaFiscal(error);
      if (guarda) {
        setErroGuarda(guarda);
        return;
      }
      toast.error("Erro ao apagar pagamento.");
    }
  };

  return (
    <AlertDialog
      open={aberto}
      onOpenChange={(abrir) => {
        if (deletePagamento.isPending) return;
        setAberto(abrir);
        if (!abrir) setErroGuarda(null);
      }}
    >
      <AlertDialogTrigger asChild>
        <Button variant="ghost" size="sm" className="text-red-600 hover:text-red-700">
          <Trash2 className="h-4 w-4" />
          Apagar
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Apagar pagamento?</AlertDialogTitle>
          <AlertDialogDescription>
            O valor pago será revertido na conta-corrente do cliente.
          </AlertDialogDescription>
          {erroGuarda ? (
            <p role="alert" className="text-sm text-red-600 dark:text-red-400">
              {erroGuarda}
            </p>
          ) : null}
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={deletePagamento.isPending}>
            {erroGuarda ? "Fechar" : "Cancelar"}
          </AlertDialogCancel>
          <AlertDialogAction
            className="bg-red-600 hover:bg-red-700 text-white"
            onClick={confirmar}
            disabled={deletePagamento.isPending}
          >
            {deletePagamento.isPending ? "A apagar..." : "Apagar"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

export function PagamentosCard({
  pagamentos,
  honorarioId,
  canManageFinanceiro,
}: {
  pagamentos: Pagamento[] | undefined;
  honorarioId: number;
  canManageFinanceiro: boolean;
}) {
  return (
    <Card className="lg:col-span-2">
      <CardHeader>
        <CardTitle>Pagamentos</CardTitle>
      </CardHeader>
      <CardContent>
        {!pagamentos?.length ? (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum pagamento registado.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-neutral-500 dark:text-neutral-400">
                <tr className="border-b border-neutral-200 dark:border-neutral-800">
                  <th className="py-2 pr-4 font-medium">Data</th>
                  <th className="py-2 pr-4 font-medium">Valor</th>
                  <th className="py-2 pr-4 font-medium">Método</th>
                  <th className="py-2 pr-4 font-medium">ID</th>
                  <th className="py-2 pr-4 font-medium">Documento fiscal</th>
                  <th className="py-2 font-medium"></th>
                </tr>
              </thead>
              <tbody>
                {pagamentos.map((p) => (
                  <tr
                    key={p.id}
                    id={`pagamento-${p.id}`}
                    className="scroll-mt-24 border-b border-neutral-200 last:border-b-0 dark:border-neutral-800"
                  >
                    <td className="py-2 pr-4">{formatDate(p.dataPagamento)}</td>
                    <td className="py-2 pr-4 tabular-nums">{formatMoneyCVE(p.valorPago)}</td>
                    <td className="py-2 pr-4">{p.metodo ?? "—"}</td>
                    <td className="py-2 pr-4">#{p.id}</td>
                    <td className="py-2 pr-4 whitespace-nowrap">
                      {p.documentoFiscal ? (
                        <Link
                          href={`/financeiro/documentos-fiscais/${encodeURIComponent(p.documentoFiscal.id)}`}
                          className="font-mono text-sm text-blue-600 hover:underline dark:text-blue-400"
                        >
                          {p.documentoFiscal.numeroFormatado}
                        </Link>
                      ) : (
                        <span className="text-sm text-slate-500 dark:text-slate-400">Sem documento fiscal</span>
                      )}
                    </td>
                    <td className="py-2 pl-4">
                      {!canManageFinanceiro ? null : p.documentoFiscal ? (
                        <span className="text-xs text-slate-500 dark:text-slate-400">
                          Pagamento faturado: não pode ser apagado.
                        </span>
                      ) : (
                        <ApagarPagamento pagamentoId={p.id} honorarioId={honorarioId} />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
