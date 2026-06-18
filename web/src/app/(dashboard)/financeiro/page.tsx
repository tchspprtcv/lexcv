"use client";

import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useClientes } from "@/hooks/use-clientes";
import { useHonorarios } from "@/hooks/use-financeiro";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";

function formatMoneyCVE(v: number) {
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

type HonorarioStatus = "Pendente" | "Parcialmente Pago" | "Pago";

function calcHonorarioStatus(totalPago: number, valorTotal: number): HonorarioStatus {
  if (totalPago <= 0) return "Pendente";
  if (totalPago < valorTotal) return "Parcialmente Pago";
  return "Pago";
}

const statusBadgeClass: Record<HonorarioStatus, string> = {
  Pendente:
    "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400",
  "Parcialmente Pago":
    "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400",
  Pago: "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400",
};

function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

export default function FinanceiroPage() {
  const permissions = usePermissions();
  const canViewFinanceiro = permissions.can.view("financeiro");
  const canCreateFinanceiro = permissions.can.create("financeiro");

  if (!permissions.isLoading && !canViewFinanceiro) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo financeiro."
        backHref="/dashboard"
      />
    );
  }

  return <FinanceiroContent canCreateFinanceiro={canCreateFinanceiro} />;
}

function FinanceiroContent({ canCreateFinanceiro }: { canCreateFinanceiro: boolean }) {
  const honorarios = useHonorarios();
  const processos = useProcessos();
  const clientes = useClientes({});

  const processoById = new Map((processos.data ?? []).map((p) => [p.id, p] as const));
  const clienteNomeById = new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const));

  const isLoading = honorarios.isPending || processos.isPending || clientes.isPending;
  const isError = honorarios.isError || processos.isError || clientes.isError;

  const list = honorarios.data ?? [];
  const now = new Date();
  const currentYearMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;

  const kpiFaturado = list.reduce((acc, h) => acc + h.valorTotal, 0);
  const kpiRecebido = list.reduce((acc, h) => acc + h.totalPago, 0);
  const kpiDivida = kpiFaturado - kpiRecebido;
  const kpiMes = list
    .filter((h) => h.dataAcordo?.startsWith(currentYearMonth))
    .reduce((acc, h) => acc + h.totalPago, 0);

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Financeiro</h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">Honorários e pagamentos.</p>
        </div>

        {canCreateFinanceiro ? (
          <Button asChild>
            <Link href="/financeiro/novo">Novo honorário</Link>
          </Button>
        ) : null}
      </div>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        {[
          { label: "Total Faturado", value: kpiFaturado },
          { label: "Total Recebido", value: kpiRecebido },
          { label: "Em Dívida", value: kpiDivida },
          { label: "Receita do Mês", value: kpiMes },
        ].map(({ label, value }) => (
          <Card key={label}>
            <CardHeader className="pb-1 pt-4 px-4">
              <CardTitle className="text-xs font-medium text-neutral-500 dark:text-neutral-400">
                {label}
              </CardTitle>
            </CardHeader>
            <CardContent className="px-4 pb-4">
              <p className="text-xl font-semibold">{formatMoneyCVE(value)}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Honorários</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
          ) : isError ? (
            <div className="text-sm text-red-600">
              {honorarios.error instanceof Error
                ? honorarios.error.message
                : processos.error instanceof Error
                  ? processos.error.message
                  : clientes.error instanceof Error
                    ? clientes.error.message
                    : "Erro ao carregar"}
            </div>
          ) : !honorarios.data?.length ? (
            <div className="text-sm text-neutral-500 dark:text-neutral-400">
              Nenhum honorário encontrado.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="text-left text-neutral-500 dark:text-neutral-400">
                  <tr className="border-b border-neutral-200 dark:border-neutral-800">
                    <th className="py-2 pr-4 font-medium">Honorário</th>
                    <th className="py-2 pr-4 font-medium">Processo</th>
                    <th className="py-2 pr-4 font-medium">Cliente</th>
                    <th className="py-2 pr-4 font-medium">Total</th>
                    <th className="py-2 pr-4 font-medium">Data do acordo</th>
                    <th className="py-2 pr-4 font-medium">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {honorarios.data.map((h) => {
                    const processo = processoById.get(h.processoId);
                    const clienteId = processo?.cliente_id;
                    return (
                      <tr
                        key={h.id}
                        className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800"
                      >
                        <td className="py-2 pr-4">
                          <Link
                            href={`/financeiro/${encodeURIComponent(String(h.id))}`}
                            className="font-medium text-neutral-900 hover:underline dark:text-neutral-50"
                          >
                            #{h.id}
                          </Link>
                        </td>
                        <td className="py-2 pr-4">
                          {processo ? (
                            <Link
                              href={`/processos/${encodeURIComponent(processo.id)}`}
                              className="hover:underline"
                            >
                              {processo.numero ?? processo.titulo ?? processo.id}
                            </Link>
                          ) : (
                            h.processoId
                          )}
                        </td>
                        <td className="py-2 pr-4">
                          {clienteId ? (
                            <Link
                              href={`/clientes/${encodeURIComponent(clienteId)}`}
                              className="hover:underline"
                            >
                              {clienteNomeById.get(clienteId) ?? clienteId}
                            </Link>
                          ) : (
                            "—"
                          )}
                        </td>
                        <td className="py-2 pr-4">{formatMoneyCVE(h.valorTotal)}</td>
                        <td className="py-2 pr-4">{formatDate(h.dataAcordo)}</td>
                        <td className="py-2 pr-4">
                          {(() => {
                            const status = calcHonorarioStatus(h.totalPago, h.valorTotal);
                            return <span className={statusBadgeClass[status]}>{status}</span>;
                          })()}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
