"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import * as React from "react";
import { Filter, Search } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { DataTable } from "@/components/shared/data-table/data-table";
import { columns } from "./columns";
import { useAdminUsers } from "@/hooks/use-admin";
import { useClientes } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import {
  usePareceres,
  usePesquisarPareceres,
  type ParecerPesquisaFilters,
  type ParecerSolicitacoesListFilters,
} from "@/hooks/use-pareceres";
import { formatDate, statusVariant } from "@/lib/pareceres";
import type { ParecerSolicitacao } from "@/types/pareceres";

export default function ParecerPage() {
  const permissions = usePermissions();
  const canView = permissions.can.view("pareceres");

  if (permissions.isFetched && !canView) {
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
  const permissions = usePermissions();
  const canCreatePareceres = permissions.can.create("pareceres");
  const [advancedOpen, setAdvancedOpen] = React.useState(false);
  const [draftStatus, setDraftStatus] = React.useState("todos");
  const [draftAdvogadoId, setDraftAdvogadoId] = React.useState("todos");
  const [draftClienteId, setDraftClienteId] = React.useState("todos");
  const [filters, setFilters] = React.useState<ParecerSolicitacoesListFilters>({});

  const [pesquisaOpen, setPesquisaOpen] = React.useState(false);
  const [pesquisaTexto, setPesquisaTexto] = React.useState("");
  const [pesquisaClienteId, setPesquisaClienteId] = React.useState("todos");
  const [pesquisaAdvogadoId, setPesquisaAdvogadoId] = React.useState("todos");
  const [pesquisaStatus, setPesquisaStatus] = React.useState("todos");
  const [pesquisaDataInicio, setPesquisaDataInicio] = React.useState("");
  const [pesquisaDataFim, setPesquisaDataFim] = React.useState("");
  const [pesquisaFilters, setPesquisaFilters] = React.useState<ParecerPesquisaFilters>({});
  const [pesquisaSubmitted, setPesquisaSubmitted] = React.useState(false);

  const searchParams = useSearchParams();
  const seededQ = searchParams.get("q");
  // WR-04 (Phase 112 code review): keyed on the palette's one-shot `_seed` nonce, falling back
  // to the raw `q` value when no nonce is present (e.g. a hand-typed/bookmarked URL), instead of
  // `q` alone — otherwise re-clicking "Ver Todos Pareceres" with the same search term would not
  // re-seed if the user had manually closed/cleared the search in between (same `q` value
  // compares equal to the last seed).
  const seedNonce = searchParams.get("_seed");
  const seedKey = seedNonce ?? seededQ;
  const [pesquisaSeedKey, setPesquisaSeedKey] = React.useState<string | null>(null);

  if (seededQ && seedKey !== pesquisaSeedKey) {
    setPesquisaSeedKey(seedKey);
    setPesquisaTexto(seededQ);
    setPesquisaFilters({ texto: seededQ });
    setPesquisaSubmitted(true);
    setPesquisaOpen(true);
  }

  const clientes = useClientes({});
  const adminUsers = useAdminUsers();
  const advogados = React.useMemo(
    () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO") && u.ativo !== false),
    [adminUsers.data],
  );
  const clienteNomeById = React.useMemo(
    () => new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const)),
    [clientes.data],
  );
  const parecerColumns = React.useMemo(() => columns(clienteNomeById), [clienteNomeById]);

  const pareceres = usePareceres(filters);
  const pesquisa = usePesquisarPareceres(pesquisaFilters, { enabled: pesquisaSubmitted });

  const searchActive = pesquisaSubmitted;
  const rows: ParecerSolicitacao[] = searchActive ? (pesquisa.data ?? []) : (pareceres.data ?? []);
  const resultsLoading = searchActive ? pesquisa.isLoading : pareceres.isLoading;
  const resultsError = searchActive ? pesquisa.isError : pareceres.isError;

  const onApply = (e: React.FormEvent) => {
    e.preventDefault();
    setPesquisaSubmitted(false);
    setFilters({
      status: draftStatus === "todos" ? "" : draftStatus,
      advogadoId: draftAdvogadoId === "todos" ? "" : draftAdvogadoId,
      clienteId: draftClienteId === "todos" ? "" : draftClienteId,
    });
  };

  const onClear = () => {
    setDraftStatus("todos");
    setDraftAdvogadoId("todos");
    setDraftClienteId("todos");
    setFilters({});
  };

  const onPesquisar = (e: React.FormEvent) => {
    e.preventDefault();
    const next: ParecerPesquisaFilters = {};
    const texto = pesquisaTexto.trim();
    const clienteId = pesquisaClienteId.trim();
    const advogadoId = pesquisaAdvogadoId.trim();
    const status = pesquisaStatus.trim();
    const dataInicio = pesquisaDataInicio.trim();
    const dataFim = pesquisaDataFim.trim();
    if (texto) next.texto = texto;
    if (clienteId && clienteId !== "todos") next.clienteId = clienteId;
    if (advogadoId && advogadoId !== "todos") next.advogadoId = advogadoId;
    if (status && status !== "todos") next.status = status;
    if (dataInicio) next.dataInicio = dataInicio;
    if (dataFim) next.dataFim = dataFim;
    setPesquisaFilters(next);
    setPesquisaSubmitted(true);
  };

  const onLimparPesquisa = () => {
    setPesquisaTexto("");
    setPesquisaClienteId("todos");
    setPesquisaAdvogadoId("todos");
    setPesquisaStatus("todos");
    setPesquisaDataInicio("");
    setPesquisaDataFim("");
    setPesquisaFilters({});
    setPesquisaSubmitted(false);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-6">
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
          Pareceres Jurídicos
        </h1>
        {canCreatePareceres ? (
          <Button asChild className="font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white">
            <Link href="/pareceres/nova">Nova Solicitação</Link>
          </Button>
        ) : null}
      </div>

      <Card className="border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/20">
        <CardContent className="p-4">
          <form className="flex flex-wrap items-end justify-between gap-3" onSubmit={onApply}>
            <div className="flex flex-wrap items-end gap-2">
              <Button
                type="button"
                variant="secondary"
                className="font-bold border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-700 dark:text-slate-300 shadow-sm hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() => setAdvancedOpen((v) => !v)}
              >
                <Filter className="h-4 w-4" />
                Filtros
              </Button>
              <Button
                type="button"
                variant="outline"
                className="font-medium border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-700 dark:text-slate-300 shadow-sm hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() => setPesquisaOpen((v) => !v)}
              >
                <Search className="h-4 w-4" />
                {pesquisaOpen ? "Ocultar Filtros" : "Pesquisa Avançada"}
              </Button>
            </div>
            <div className="flex items-center gap-2">
              <Button type="submit" className="font-bold shadow-none">
                Aplicar
              </Button>
              <Button type="button" variant="ghost" className="text-slate-500 hover:text-slate-900 dark:hover:text-white" onClick={onClear}>
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
                    <Select value={draftStatus} onValueChange={setDraftStatus}>
                      <SelectTrigger size="default" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="todos">Todos</SelectItem>
                        <SelectItem value="PENDENTE">Pendente</SelectItem>
                        <SelectItem value="EM_ELABORACAO">Em elaboração</SelectItem>
                        <SelectItem value="EM_REVISAO">Em revisão</SelectItem>
                        <SelectItem value="CONCLUIDO">Concluído</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Advogado
                  </div>
                  <div className="mt-2">
                    <Select value={draftAdvogadoId} onValueChange={setDraftAdvogadoId}>
                      <SelectTrigger size="default" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="todos">Todos</SelectItem>
                        {advogados.map((u) => (
                          <SelectItem key={u.id} value={u.id}>
                            {u.nome}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Cliente
                  </div>
                  <div className="mt-2">
                    <Select value={draftClienteId} onValueChange={setDraftClienteId}>
                      <SelectTrigger size="default" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="todos">Todos</SelectItem>
                        {(clientes.data ?? []).map((c) => (
                          <SelectItem key={c.id} value={c.id}>
                            {c.nome}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>
            ) : null}
          </form>
        </CardContent>
      </Card>

      {pesquisaOpen ? (
        <Card className="border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/20">
          <CardContent className="p-4">
            <form className="space-y-4" onSubmit={onPesquisar}>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <div>
                  <label
                    htmlFor="pesquisa-texto"
                    className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400"
                  >
                    Pesquisar
                  </label>
                  <div className="mt-2">
                    <input
                      id="pesquisa-texto"
                      type="text"
                      value={pesquisaTexto}
                      onChange={(e) => setPesquisaTexto(e.target.value)}
                      placeholder="Pesquisar por conteúdo do parecer..."
                      className="h-10 w-full bg-white dark:bg-[#020617] border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    />
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Cliente
                  </label>
                  <div className="mt-2">
                    <Select value={pesquisaClienteId} onValueChange={setPesquisaClienteId}>
                      <SelectTrigger size="default" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="todos">Todos</SelectItem>
                        {(clientes.data ?? []).map((c) => (
                          <SelectItem key={c.id} value={c.id}>
                            {c.nome}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Advogado
                  </label>
                  <div className="mt-2">
                    <Select value={pesquisaAdvogadoId} onValueChange={setPesquisaAdvogadoId}>
                      <SelectTrigger size="default" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="todos">Todos</SelectItem>
                        {advogados.map((u) => (
                          <SelectItem key={u.id} value={u.id}>
                            {u.nome}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Estado
                  </label>
                  <div className="mt-2">
                    <Select value={pesquisaStatus} onValueChange={setPesquisaStatus}>
                      <SelectTrigger size="default" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="todos">Todos</SelectItem>
                        <SelectItem value="PENDENTE">Pendente</SelectItem>
                        <SelectItem value="EM_ELABORACAO">Em elaboração</SelectItem>
                        <SelectItem value="EM_REVISAO">Em revisão</SelectItem>
                        <SelectItem value="CONCLUIDO">Concluído</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
                <div>
                  <label className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Período
                  </label>
                  <div className="mt-2 grid grid-cols-2 gap-2">
                    <div className="flex flex-col gap-1">
                      <label htmlFor="pesquisa-data-inicio" className="text-xs font-medium text-slate-500 dark:text-slate-400">Data Início</label>
                      <input
                        id="pesquisa-data-inicio"
                        type="date"
                        value={pesquisaDataInicio}
                        onChange={(e) => setPesquisaDataInicio(e.target.value)}
                        className="h-10 w-full bg-white dark:bg-[#020617] border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                      />
                    </div>
                    <div className="flex flex-col gap-1">
                      <label htmlFor="pesquisa-data-fim" className="text-xs font-medium text-slate-500 dark:text-slate-400">Data Fim</label>
                      <input
                        id="pesquisa-data-fim"
                        type="date"
                        value={pesquisaDataFim}
                        onChange={(e) => setPesquisaDataFim(e.target.value)}
                        className="h-10 w-full bg-white dark:bg-[#020617] border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                      />
                    </div>
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  type="submit"
                  disabled={pesquisa.isFetching}
                  className="font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white"
                >
                  {pesquisa.isFetching ? "A pesquisar..." : "Pesquisar"}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  className="text-slate-500 hover:text-slate-900 dark:hover:text-white"
                  onClick={onLimparPesquisa}
                >
                  Limpar Filtros
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      ) : null}

      <Card className="border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/20">
        <CardContent className="p-0 bg-white dark:bg-[#020617] border-t border-slate-200 dark:border-slate-800">
          {resultsLoading ? (
            <div className="p-6 text-sm text-slate-500">A carregar...</div>
          ) : resultsError ? (
            <div className="p-6 text-sm text-red-600">
              {searchActive
                ? "Não foi possível concluir a pesquisa. Verifique a ligação e tente novamente."
                : "Não foi possível carregar as solicitações. Verifique a ligação e tente novamente."}
            </div>
          ) : !rows.length ? (
            <div className="p-6 text-sm text-slate-500">
              {searchActive ? (
                <>
                  <p className="font-medium text-slate-700 dark:text-slate-300">
                    Nenhum resultado encontrado
                  </p>
                  <p className="mt-1">
                    Não foram encontrados pareceres para os critérios indicados. Tente ajustar o texto ou os
                    filtros de pesquisa.
                  </p>
                </>
              ) : (
                <>
                  <p className="font-medium text-slate-700 dark:text-slate-300">
                    Nenhuma solicitação de parecer encontrada
                  </p>
                  <p className="mt-1">Ajuste os filtros ou aguarde a criação de novas solicitações.</p>
                </>
              )}
            </div>
          ) : (
            <>
              {/* Mobile: cards empilhados */}
              <div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">
                {rows.map((s) => (
                  <Link
                    key={s.id}
                    href={`/pareceres/${encodeURIComponent(s.id)}`}
                    className="block p-4 hover:bg-slate-50 dark:hover:bg-slate-900/40 transition-colors"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-bold text-slate-900 dark:text-white">
                        {clienteNomeById.get(s.clienteId) ?? s.clienteId}
                      </span>
                      <Badge variant={statusVariant(s.status)} className="font-bold tracking-wide">
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
                <DataTable columns={parecerColumns} data={rows} getRowId={(s) => s.id} />
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
