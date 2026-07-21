"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import * as React from "react";
import { Download, Filter, Plus, Scale, Search, Sparkles } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { DataTable } from "@/components/shared/data-table/data-table";
import { columns } from "./columns";
import { useClientes } from "@/hooks/use-clientes";
import { useEventos } from "@/hooks/use-eventos";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";

export default function ProcessosPage() {
  const permissions = usePermissions();
  const canViewProcessos = permissions.can.view("processos");
  const canCreateProcessos = permissions.can.create("processos");

  if (permissions.isFetched && !canViewProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo de processos."
        backHref="/dashboard"
      />
    );
  }

  return <ProcessosPageContent canCreateProcessos={canCreateProcessos} />;
}

function ProcessosPageContent({ canCreateProcessos }: { canCreateProcessos: boolean }) {
  const clientes = useClientes({});
  const [advancedOpen, setAdvancedOpen] = React.useState(false);
  const [draftQuery, setDraftQuery] = React.useState("");
  const [draftEstado, setDraftEstado] = React.useState("");
  const [draftTribunal, setDraftTribunal] = React.useState("");
  const [draftArea, setDraftArea] = React.useState("");
  const [draftClienteId, setDraftClienteId] = React.useState("");
  const [filters, setFilters] = React.useState({
    q: "",
    estado: "",
    tribunal: "",
    area_juridica: "",
    cliente_id: "",
    sortBy: "created_at" as const,
    sortDir: "desc" as const,
  });
  const processos = useProcessos(filters);

  const searchParams = useSearchParams();
  const seededQ = searchParams.get("q");
  // Seed draftQuery from ?q= without a useEffect (avoids react-hooks/set-state-in-effect):
  // React's documented "adjusting state during render" pattern — setDraftQuery only fires
  // when the seed key actually changes (tracked via lastSeededQ), so it re-seeds on a new ?q=
  // navigation but never fights the user's own edits to the search box.
  // WR-04 (Phase 112 code review): keyed on the palette's one-shot `_seed` nonce, falling back
  // to the raw `q` value when no nonce is present (e.g. a hand-typed/bookmarked URL), instead of
  // `q` alone — otherwise re-clicking "Ver Todos Processos" with the same search term would not
  // re-seed if the user had manually cleared the box in between (same `q` value compares equal
  // to the last seed).
  const seedNonce = searchParams.get("_seed");
  const seedKey = seedNonce ?? seededQ;
  const [lastSeededQ, setLastSeededQ] = React.useState<string | null>(null);
  if (seededQ && seedKey !== lastSeededQ) {
    setLastSeededQ(seedKey);
    setDraftQuery(seededQ);
  }

  const clienteNomeById = React.useMemo(
    () => new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const)),
    [clientes.data],
  );
  const processoColumns = React.useMemo(() => columns(clienteNomeById), [clienteNomeById]);

  const isLoading = processos.isPending || clientes.isPending;
  const isError = processos.isError || clientes.isError;

  const totalAtivos =
    processos.data?.filter((p) => (p.estado ?? "").toUpperCase() === "ATIVO").length ?? 0;
  const totalSuspensos =
    processos.data?.filter((p) => (p.estado ?? "").toUpperCase() === "SUSPENSO").length ?? 0;

  const eventos = useEventos({ concluido: false });
  const now7days = new Date().getTime() + 7 * 24 * 60 * 60 * 1000;
  const proximasAudiencias = (eventos.data ?? []).filter((e) => {
    const titulo = (e.titulo ?? "").toLowerCase();
    const isAudiencia = e.tipo?.toUpperCase() === "AUDIENCIA" || titulo.includes("audiência") || titulo.includes("audiencia");
    if (!isAudiencia) return false;
    return new Date(e.dataInicio).getTime() < now7days;
  }).length;

  React.useEffect(() => {
    const t = window.setTimeout(() => {
      setFilters((c) => ({ ...c, q: draftQuery.trim() }));
    }, 300);
    return () => window.clearTimeout(t);
  }, [draftQuery]);

  const onApply = (e: React.FormEvent) => {
    e.preventDefault();
    setFilters((c) => ({
      ...c,
      q: draftQuery.trim(),
      estado: draftEstado.trim(),
      tribunal: draftTribunal.trim(),
      area_juridica: draftArea.trim(),
      cliente_id: draftClienteId.trim(),
    }));
  };

  const onClear = () => {
    setDraftQuery("");
    setDraftEstado("");
    setDraftTribunal("");
    setDraftArea("");
    setDraftClienteId("");
    setFilters({
      q: "",
      estado: "",
      tribunal: "",
      area_juridica: "",
      cliente_id: "",
      sortBy: "created_at",
      sortDir: "desc",
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-6">
        <div className="flex items-center gap-3">
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Processos Jurídicos</h1>
        </div>
        {canCreateProcessos ? (
          <div className="flex items-center gap-2">
            <Button asChild variant="outline" className="font-bold tracking-wide shadow-none border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-900">
              <Link href="/processos/dashboard">
                Dashboard
              </Link>
            </Button>
            <Button asChild className="font-bold tracking-wide shadow-none">
              <Link href="/processos/novo">
                <Plus className="h-4 w-4" />
                Novo Processo
              </Link>
            </Button>
          </div>
        ) : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <div className="grid gap-4 grid-cols-1 md:grid-cols-2 lg:grid-cols-2">
            <Card className="border-slate-200 dark:border-slate-800">
              <CardContent className="p-5">
                <div className="flex items-start justify-between">
                  <div className="h-10 w-10 bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center border border-blue-100 dark:border-blue-500/20">
                    <Scale className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                  </div>
                  <Badge variant="green" className="font-bold tracking-wide">+12%</Badge>
                </div>
                <div className="mt-5 text-[11px] font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">TOTAL ATIVOS</div>
                <div className="mt-1 text-3xl font-bold text-slate-900 dark:text-white">{totalAtivos.toLocaleString("pt-CV")}</div>
              </CardContent>
            </Card>
            <Card className="border-slate-200 dark:border-slate-800">
              <CardContent className="p-5">
                <div className="flex items-start justify-between">
                  <div className="h-10 w-10 bg-amber-50 dark:bg-amber-500/10 flex items-center justify-center border border-amber-100 dark:border-amber-500/20">
                    <Sparkles className="h-5 w-5 text-amber-600 dark:text-amber-400" />
                  </div>
                  <Badge variant="gray" className="font-bold tracking-wide">Estável</Badge>
                </div>
                <div className="mt-5 text-[11px] font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">SUSPENSOS</div>
                <div className="mt-1 text-3xl font-bold text-slate-900 dark:text-white">{totalSuspensos.toLocaleString("pt-CV")}</div>
              </CardContent>
            </Card>
          </div>
        </div>

        <Card className="bg-slate-950 dark:bg-[#020617] text-white border-slate-900 dark:border-slate-800 shadow-xl overflow-hidden relative group">
          <div className="absolute inset-0 bg-gradient-to-br from-blue-600/20 to-transparent opacity-50" />
          <CardContent className="p-6 relative z-10">
            <div className="text-sm font-bold tracking-wide text-blue-400">Próximas Audiências</div>
            <p className="mt-3 text-[13px] leading-relaxed text-slate-300">
              <strong className="text-white text-lg">{proximasAudiencias} audiências</strong> agendadas para os próximos 7 dias.
            </p>
            <Button asChild className="mt-6 w-full bg-blue-600 hover:bg-blue-500 text-white font-bold transition-colors shadow-none">
              <Link href="/agenda">Ver Agenda Completa</Link>
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card className="border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/20">
        <CardContent className="p-4">
          <form className="flex flex-wrap items-end justify-between gap-3" onSubmit={onApply}>
            <div className="flex flex-wrap items-end gap-2">
              <div className="w-[320px] max-w-full">
                <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                  Pesquisar
                </div>
                <div className="mt-2 relative">
                  <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <Input
                    value={draftQuery}
                    onChange={(e) => setDraftQuery(e.target.value)}
                    placeholder="Número, cliente, tribunal, área, estado..."
                    className="pl-9 bg-white dark:bg-[#020617] border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                  />
                </div>
              </div>
              <div className="w-40 max-w-full">
                <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                  Estado
                </div>
                <div className="mt-2">
                  <NativeSelect
                    value={draftEstado}
                    onChange={(e) => setDraftEstado(e.target.value)}
                    size="default"
                    className="w-full"
                  >
                    <option value="">Todos</option>
                    <option value="TRIAGEM">Em triagem</option>
                    <option value="ATIVO">Ativo</option>
                    <option value="SUSPENSO">Suspenso</option>
                    <option value="ENCERRADO">Encerrado</option>
                    <option value="CONCLUIDO">Concluído</option>
                  </NativeSelect>
                </div>
              </div>
              <Button
                type="button"
                variant="secondary"
                className="font-bold border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-700 dark:text-slate-300 shadow-sm hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() => setAdvancedOpen((v) => !v)}
              >
                <Filter className="h-4 w-4" />
                Filtros
              </Button>
            </div>
            <div className="flex items-center gap-2">
              <Button type="button" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-slate-900 dark:hover:text-white" disabled>
                <Download className="h-4 w-4" />
              </Button>
              <Button type="submit" className="font-bold shadow-none">
                Aplicar
              </Button>
              <Button type="button" variant="ghost" className="text-slate-500 hover:text-slate-900 dark:hover:text-white" onClick={onClear}>
                Limpar
              </Button>
              {canCreateProcessos ? (
                <Button asChild className="font-bold shadow-none">
                  <Link href="/processos/novo">
                    <Plus className="h-4 w-4" />
                    Novo Processo
                  </Link>
                </Button>
              ) : null}
            </div>
            {advancedOpen ? (
              <div className="w-full grid gap-3 lg:grid-cols-12 pt-2 border-t border-slate-200/70 dark:border-slate-800/70">
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Tribunal
                  </div>
                  <div className="mt-2">
                    <Input
                      value={draftTribunal}
                      onChange={(e) => setDraftTribunal(e.target.value)}
                      placeholder="Ex.: Tribunal da Praia"
                      className="bg-white dark:bg-[#020617] border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                    />
                  </div>
                </div>
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Área jurídica
                  </div>
                  <div className="mt-2">
                    <Input
                      value={draftArea}
                      onChange={(e) => setDraftArea(e.target.value)}
                      placeholder="Ex.: Cível"
                      className="bg-white dark:bg-[#020617] border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                    />
                  </div>
                </div>
                <div className="lg:col-span-4">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                    Cliente
                  </div>
                  <div className="mt-2">
                    <NativeSelect
                      value={draftClienteId}
                      onChange={(e) => setDraftClienteId(e.target.value)}
                      size="default"
                      className="w-full"
                    >
                      <option value="">Todos</option>
                      {(clientes.data ?? []).map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.nome}
                        </option>
                      ))}
                    </NativeSelect>
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
              {processos.error instanceof Error
                ? processos.error.message
                : clientes.error instanceof Error
                  ? clientes.error.message
                  : "Erro ao carregar"}
            </div>
          ) : !processos.data?.length ? (
            <div className="p-6 text-sm text-slate-500">
              {filters.estado === "TRIAGEM" ? "Nenhum processo em triagem." : "Nenhum processo encontrado."}
            </div>
          ) : (
            <DataTable columns={processoColumns} data={processos.data} getRowId={(p) => p.id} />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
