"use client";

import Link from "next/link";
import * as React from "react";
import { Filter } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAdminUsers } from "@/hooks/use-admin";
import { useClientes } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import { usePareceres, type ParecerSolicitacoesListFilters } from "@/hooks/use-pareceres";
import type { ParecerStatus } from "@/types/pareceres";

function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

function statusVariant(status: ParecerStatus) {
  return status === "PENDENTE"
    ? "gray"
    : status === "EM_ELABORACAO"
      ? "blue"
      : status === "EM_REVISAO"
        ? "amber"
        : status === "CONCLUIDO"
          ? "green"
          : "secondary";
}

export default function ParecerPage() {
  const permissions = usePermissions();
  const canView = permissions.can.view("pareceres");

  if (!permissions.isLoading && !canView) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo de pareceres."
        backHref="/dashboard"
      />
    );
  }

  return <ParecerPageContent />;
}

function ParecerPageContent() {
  const [advancedOpen, setAdvancedOpen] = React.useState(false);
  const [draftStatus, setDraftStatus] = React.useState("");
  const [draftAdvogadoId, setDraftAdvogadoId] = React.useState("");
  const [draftClienteId, setDraftClienteId] = React.useState("");
  const [filters, setFilters] = React.useState<ParecerSolicitacoesListFilters>({});

  const clientes = useClientes({});
  const adminUsers = useAdminUsers();
  const advogados = React.useMemo(
    () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO")),
    [adminUsers.data],
  );
  const clienteNomeById = React.useMemo(
    () => new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const)),
    [clientes.data],
  );

  const pareceres = usePareceres(filters);

  const isLoading = pareceres.isLoading || clientes.isLoading;
  const isError = pareceres.isError || clientes.isError;

  const onApply = (e: React.FormEvent) => {
    e.preventDefault();
    setFilters({
      status: draftStatus.trim(),
      advogadoId: draftAdvogadoId.trim(),
      clienteId: draftClienteId.trim(),
    });
  };

  const onClear = () => {
    setDraftStatus("");
    setDraftAdvogadoId("");
    setDraftClienteId("");
    setFilters({});
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-6">
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
          Pareceres Jurídicos
        </h1>
      </div>

      <Card className="border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/20">
        <CardContent className="p-4">
          <form className="flex flex-wrap items-end justify-between gap-3" onSubmit={onApply}>
            <div className="flex flex-wrap items-end gap-2">
              <Button
                type="button"
                variant="secondary"
                className="rounded-none font-bold border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-700 dark:text-slate-300 shadow-sm hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() => setAdvancedOpen((v) => !v)}
              >
                <Filter className="h-4 w-4" />
                Filtros
              </Button>
            </div>
            <div className="flex items-center gap-2">
              <Button type="submit" className="rounded-none font-bold shadow-none">
                Aplicar
              </Button>
              <Button type="button" variant="ghost" className="rounded-none text-slate-500 hover:text-slate-900 dark:hover:text-white" onClick={onClear}>
                Limpar
              </Button>
            </div>
            {advancedOpen ? (
              <div className="w-full grid gap-3 lg:grid-cols-12 pt-2 border-t border-slate-200/70 dark:border-slate-800/70">
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Estado
                  </div>
                  <div className="mt-2">
                    <select
                      value={draftStatus}
                      onChange={(e) => setDraftStatus(e.target.value)}
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                      <option value="">Todos</option>
                      <option value="PENDENTE">Pendente</option>
                      <option value="EM_ELABORACAO">Em elaboração</option>
                      <option value="EM_REVISAO">Em revisão</option>
                      <option value="CONCLUIDO">Concluído</option>
                    </select>
                  </div>
                </div>
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Advogado
                  </div>
                  <div className="mt-2">
                    <select
                      value={draftAdvogadoId}
                      onChange={(e) => setDraftAdvogadoId(e.target.value)}
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                      <option value="">Todos</option>
                      {advogados.map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.nome}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Cliente
                  </div>
                  <div className="mt-2">
                    <select
                      value={draftClienteId}
                      onChange={(e) => setDraftClienteId(e.target.value)}
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                      <option value="">Todos</option>
                      {(clientes.data ?? []).map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.nome}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>
            ) : null}
          </form>
        </CardContent>
        <CardContent className="p-0 bg-white dark:bg-[#020617] border-t border-slate-200 dark:border-slate-800">
          {isLoading ? (
            <div className="p-6 text-sm text-slate-500">A carregar...</div>
          ) : isError ? (
            <div className="p-6 text-sm text-red-600">
              Não foi possível carregar as solicitações. Verifique a ligação e tente novamente.
            </div>
          ) : !pareceres.data?.length ? (
            <div className="p-6 text-sm text-slate-500">
              <p className="font-medium text-slate-700 dark:text-slate-300">
                Nenhuma solicitação de parecer encontrada
              </p>
              <p className="mt-1">Ajuste os filtros ou aguarde a criação de novas solicitações.</p>
            </div>
          ) : (
            <>
              {/* Mobile: cards empilhados */}
              <div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">
                {pareceres.data.map((s) => (
                  <Link
                    key={s.id}
                    href={`/pareceres/${encodeURIComponent(s.id)}`}
                    className="block p-4 hover:bg-slate-50 dark:hover:bg-slate-900/40 transition-colors"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold text-slate-900 dark:text-white">
                        {clienteNomeById.get(s.clienteId) ?? s.clienteId}
                      </span>
                      <Badge variant={statusVariant(s.status)} className="rounded-none font-bold tracking-wide">
                        {s.status}
                      </Badge>
                    </div>
                    <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      Prioridade: {s.prioridade ?? "—"}
                    </div>
                    <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      Prazo: {formatDate(s.prazo)}
                    </div>
                    <div className="mt-1 text-[11px] text-slate-400 dark:text-slate-500">
                      Criado: {formatDate(s.createdAt)}
                    </div>
                  </Link>
                ))}
              </div>

              {/* Desktop: tabela */}
              <div className="hidden md:block">
                <Table>
                  <TableHeader className="bg-slate-50/50 dark:bg-slate-900/50">
                    <TableRow className="border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
                      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ESTADO</TableHead>
                      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">CLIENTE</TableHead>
                      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">PRIORIDADE</TableHead>
                      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">PRAZO</TableHead>
                      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">CRIADO</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {pareceres.data.map((s) => (
                      <TableRow key={s.id} className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors">
                        <TableCell>
                          <Badge variant={statusVariant(s.status)} className="rounded-none font-bold tracking-wide">
                            {s.status}
                          </Badge>
                        </TableCell>
                        <TableCell className="font-bold text-slate-700 dark:text-slate-300">
                          <Link
                            href={`/pareceres/${encodeURIComponent(s.id)}`}
                            className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                          >
                            {clienteNomeById.get(s.clienteId) ?? s.clienteId}
                          </Link>
                        </TableCell>
                        <TableCell className="text-slate-500 dark:text-slate-400 font-medium">{s.prioridade ?? "—"}</TableCell>
                        <TableCell className="text-slate-500 dark:text-slate-400 font-medium">{formatDate(s.prazo)}</TableCell>
                        <TableCell className="text-slate-500 dark:text-slate-400 font-medium">{formatDate(s.createdAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
