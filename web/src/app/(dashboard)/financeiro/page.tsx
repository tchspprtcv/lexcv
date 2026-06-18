"use client";

import * as React from "react";
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

function escapeField(value: string): string {
  if (value.includes(",") || value.includes('"') || value.includes("\n")) {
    return '"' + value.replace(/"/g, '""') + '"';
  }
  return value;
}

interface HonorarioRow {
  id: string | number;
  processoId: string;
  valorTotal: number;
  totalPago: number;
  dataAcordo?: string;
}

interface ProcessoRef {
  id: string;
  numero?: string;
  titulo?: string;
  cliente_id?: string;
}

function exportHonorariosCsv(
  rows: HonorarioRow[],
  processoById: Map<string, ProcessoRef>,
  clienteNomeById: Map<string, string>,
) {
  const header = ["ID", "Processo", "Cliente", "Valor Total", "Total Pago", "Estado", "Data do Acordo"];
  const lines: string[] = [header.map(escapeField).join(",")];

  for (const h of rows) {
    const proc = processoById.get(h.processoId);
    const processoLabel = proc ? (proc.numero ?? proc.titulo ?? h.processoId) : h.processoId;
    const clienteId = proc?.cliente_id ?? "";
    const clienteLabel = clienteNomeById.get(clienteId) ?? "";
    const estado = calcHonorarioStatus(h.totalPago, h.valorTotal);
    const dataAcordo = h.dataAcordo ?? "";

    lines.push(
      [
        escapeField(String(h.id)),
        escapeField(processoLabel),
        escapeField(clienteLabel),
        escapeField(String(h.valorTotal)),
        escapeField(String(h.totalPago)),
        escapeField(estado),
        escapeField(dataAcordo),
      ].join(","),
    );
  }

  const bom = "﻿";
  const content = bom + lines.join("\n");
  const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const today = new Date().toISOString().slice(0, 10);
  const a = document.createElement("a");
  a.href = url;
  a.download = `honorarios-${today}.csv`;
  a.click();
  URL.revokeObjectURL(url);
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

  return <FinanceiroContent canCreateFinanceiro={canCreateFinanceiro} canViewFinanceiro={canViewFinanceiro} />;
}

function FinanceiroContent({
  canCreateFinanceiro,
  canViewFinanceiro,
}: {
  canCreateFinanceiro: boolean;
  canViewFinanceiro: boolean;
}) {
  const honorarios = useHonorarios();
  const processos = useProcessos();
  const clientes = useClientes({});

  const processoById = new Map((processos.data ?? []).map((p) => [p.id, p] as const));
  const clienteNomeById = new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const));

  const isLoading = honorarios.isPending || processos.isPending || clientes.isPending;
  const isError = honorarios.isError || processos.isError || clientes.isError;

  const [filtroProcesso, setFiltroProcesso] = React.useState("");
  const [filtroStatus, setFiltroStatus] = React.useState<"" | "Pendente" | "Parcialmente Pago" | "Pago">("");
  const [filtroDataDe, setFiltroDataDe] = React.useState("");
  const [filtroDataAte, setFiltroDataAte] = React.useState("");

  const list = honorarios.data ?? [];

  let filteredList = list;
  if (filtroProcesso) filteredList = filteredList.filter((h) => h.processoId === filtroProcesso);
  if (filtroStatus) filteredList = filteredList.filter((h) => calcHonorarioStatus(h.totalPago, h.valorTotal) === filtroStatus);
  if (filtroDataDe) filteredList = filteredList.filter((h) => h.dataAcordo != null && h.dataAcordo >= filtroDataDe);
  if (filtroDataAte) filteredList = filteredList.filter((h) => h.dataAcordo != null && h.dataAcordo <= filtroDataAte);
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

        <div className="flex items-center gap-2">
          {canViewFinanceiro && filteredList.length > 0 ? (
            <Button
              variant="outline"
              onClick={() => exportHonorariosCsv(filteredList, processoById, clienteNomeById)}
            >
              Exportar CSV
            </Button>
          ) : null}
          {canCreateFinanceiro ? (
            <Button asChild>
              <Link href="/financeiro/novo">Novo honorário</Link>
            </Button>
          ) : null}
        </div>
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

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Processo</label>
          <select
            value={filtroProcesso}
            onChange={(e) => setFiltroProcesso(e.target.value)}
            className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
          >
            <option value="">Todos</option>
            {(processos.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.numero ?? p.titulo ?? p.id}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Estado</label>
          <select
            value={filtroStatus}
            onChange={(e) => setFiltroStatus(e.target.value as "" | "Pendente" | "Parcialmente Pago" | "Pago")}
            className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
          >
            <option value="">Todos</option>
            <option value="Pendente">Pendente</option>
            <option value="Parcialmente Pago">Parcialmente Pago</option>
            <option value="Pago">Pago</option>
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Data de</label>
          <input
            type="date"
            value={filtroDataDe}
            onChange={(e) => setFiltroDataDe(e.target.value)}
            className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Data até</label>
          <input
            type="date"
            value={filtroDataAte}
            onChange={(e) => setFiltroDataAte(e.target.value)}
            className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
          />
        </div>

        {(filtroProcesso || filtroStatus || filtroDataDe || filtroDataAte) ? (
          <Button
            variant="outline"
            onClick={() => {
              setFiltroProcesso("");
              setFiltroStatus("");
              setFiltroDataDe("");
              setFiltroDataAte("");
            }}
          >
            Limpar filtros
          </Button>
        ) : null}
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
          ) : filteredList.length === 0 ? (
            <div className="text-sm text-neutral-500 dark:text-neutral-400">
              Nenhum honorário corresponde aos filtros aplicados.
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
                  {filteredList.map((h) => {
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
