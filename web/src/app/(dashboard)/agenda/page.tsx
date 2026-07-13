"use client";

import Link from "next/link";
import * as React from "react";
import { ChevronLeft, ChevronRight, Plus, Loader2 } from "lucide-react";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { usePermissions } from "@/hooks/use-permissions";
import { useEventos } from "@/hooks/use-eventos";
import { useProcessos, useAllPrazos } from "@/hooks/use-processos";
import type { Evento } from "@/types/eventos";
import { apiFetch } from "@/lib/api";
import { toast } from "@/hooks/use-toast";
import { cn } from "@/lib/utils";

export default function AgendaPage() {
  const permissions = usePermissions();
  const canViewAgenda = permissions.can.view("agenda");
  const canCreateAgenda = permissions.can.create("agenda");

  if (!permissions.isLoading && !canViewAgenda) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar a agenda."
        backHref="/dashboard"
      />
    );
  }

  return <AgendaPageContent canCreateAgenda={canCreateAgenda} />;
}

function AgendaPageContent({ canCreateAgenda }: { canCreateAgenda: boolean }) {
  const processos = useProcessos();
  const eventos = useEventos({});
  const prazos = useAllPrazos();

  const [selectedProcessoId, setSelectedProcessoId] = React.useState<string>("");
  const [selectedCategoria, setSelectedCategoria] = React.useState<string>("todos");
  const [selectedConcluido, setSelectedConcluido] = React.useState<string>("todos");

  const processoLabelById = new Map(
    (processos.data ?? []).map((p) => [p.id, p.numero || p.titulo || "Sem número"] as const),
  );

  const initialMonth = React.useMemo(() => {
    const base = new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  }, []);

  const [dragState, setDragState] = React.useState<{ eventId: number; originalDataInicio: string; originalDataFim: string } | null>(null);
  const [dragOverKey, setDragOverKey] = React.useState<string | null>(null);
  const [optimisticOverrides, setOptimisticOverrides] = React.useState<Map<number, string>>(new Map());

  const queryClient = useQueryClient();
  const dragDropMutation = useMutation({
    mutationFn: ({ id, dataInicio, dataFim }: { id: number; dataInicio: string; dataFim: string }) =>
      apiFetch<Evento>(`/eventos/${encodeURIComponent(String(id))}`, {
        method: "PUT",
        body: JSON.stringify({ dataInicio, dataFim }),
      }),
    onSuccess: () => {
      setOptimisticOverrides(new Map());
      queryClient.invalidateQueries({ queryKey: ["eventos", "list"] });
      toast.success("Evento reagendado com sucesso.");
    },
    onError: (error: unknown) => {
      setOptimisticOverrides(new Map());
      toast.error(error instanceof Error ? error.message : "Erro ao reagendar evento.");
    },
  });

  const [cursorMonthOverride, setCursorMonthOverride] = React.useState<Date | null>(null);
  const cursorMonth = cursorMonthOverride ?? initialMonth;

  const monthLabel = cursorMonth.toLocaleDateString("pt-CV", { month: "long", year: "numeric" });

  const days = React.useMemo(() => buildMonthGrid(cursorMonth), [cursorMonth]);

  const allUnifiedEvents = React.useMemo(() => {
    const evs = (eventos.data ?? []).map((e) => {
      if (optimisticOverrides.has(e.id)) {
        const newInicio = optimisticOverrides.get(e.id)!;
        const durMs = new Date(e.dataFim).getTime() - new Date(e.dataInicio).getTime();
        const newFim = new Date(new Date(newInicio).getTime() + durMs).toISOString().slice(0, 19);
        return { ...e, isPrazo: false, dataInicio: newInicio, dataFim: newFim };
      }
      return { ...e, isPrazo: false };
    });
    const pzs = (prazos.data ?? []).map((p) => ({
      id: p.id,
      tenantId: p.tenantId,
      processoId: p.processoId,
      titulo: `[Prazo] ${p.descricao}`,
      descricao: p.descricao,
      dataInicio: `${p.dataLimite}T09:00:00`,
      dataFim: `${p.dataLimite}T18:00:00`,
      prioridade: p.prioridade as Evento["prioridade"],
      concluido: p.concluido,
      isPrazo: true,
      risco: p.risco,
    }));
    return [...evs, ...pzs];
  }, [eventos.data, prazos.data, optimisticOverrides]);

  const filteredEvents = React.useMemo(() => {
    return allUnifiedEvents.filter((e) => {
      if (selectedProcessoId && e.processoId !== selectedProcessoId) {
        return false;
      }
      if (selectedConcluido === "concluidos" && !e.concluido) {
        return false;
      }
      if (selectedConcluido === "pendentes" && e.concluido) {
        return false;
      }
      if (selectedCategoria !== "todos") {
        const cat = getCategoria(e);
        if (cat.id !== selectedCategoria) {
          return false;
        }
      }
      return true;
    });
  }, [allUnifiedEvents, selectedProcessoId, selectedConcluido, selectedCategoria]);

  const eventosByDay = React.useMemo(() => {
    const map = new Map<string, typeof filteredEvents>();
    for (const e of filteredEvents) {
      const d = new Date(e.dataInicio);
      if (Number.isNaN(d.getTime())) continue;
      const key = dayKey(d);
      map.set(key, [...(map.get(key) ?? []), e]);
    }
    return map;
  }, [filteredEvents]);

  const upcoming = React.useMemo(() => {
    const now = new Date().getTime();
    return filteredEvents
      .filter((e) => !e.concluido)
      .map((e) => ({ e, t: new Date(e.dataInicio).getTime() }))
      .filter((x) => !Number.isNaN(x.t) && x.t >= now)
      .sort((a, b) => a.t - b.t)
      .slice(0, 4)
      .map((x) => x.e);
  }, [filteredEvents]);

  const weekStats = React.useMemo(() => {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const end = new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000);

    const active = filteredEvents.filter((e) => {
      if (e.concluido) return false;
      const t = new Date(e.dataInicio).getTime();
      return t >= start.getTime() && t < end.getTime();
    });

    const prazosAtivos = active.filter((e) => getCategoria(e).id === "PRAZO").length;
    const audiencias = active.filter((e) => getCategoria(e).id === "AUDIENCIA").length;
    const urgentes = active.filter((e) => e.risco === "proximo" || e.risco === "vencido").length;

    return { prazosAtivos, audiencias, urgentes };
  }, [filteredEvents]);

  const isLoading = processos.isPending || eventos.isPending || prazos.isPending;
  const isError = processos.isError || eventos.isError || prazos.isError;

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
      <div className="space-y-4">
        <div className="flex items-start justify-between gap-6">
          <div>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-white capitalize tracking-tight">{monthLabel}</h1>
            <p className="text-sm font-medium text-slate-500 dark:text-slate-400 mt-1">Gestão de prazos e audiências institucionais</p>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1 rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-1 py-1 shadow-sm">
              <Button
                type="button"
                variant="ghost"
                className="h-8 w-8 p-0 rounded-none text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800"
                onClick={() =>
                  setCursorMonthOverride((d) => {
                    const base = d ?? initialMonth;
                    return new Date(base.getFullYear(), base.getMonth() - 1, 1);
                  })
                }
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="h-8 px-3 rounded-none font-bold text-slate-700 dark:text-slate-300 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-500/10"
                onClick={() => setCursorMonthOverride(new Date(new Date().getFullYear(), new Date().getMonth(), 1))}
              >
                Hoje
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="h-8 w-8 p-0 rounded-none text-slate-500 hover:text-slate-900 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-slate-800"
                onClick={() =>
                  setCursorMonthOverride((d) => {
                    const base = d ?? initialMonth;
                    return new Date(base.getFullYear(), base.getMonth() + 1, 1);
                  })
                }
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>

            {canCreateAgenda ? (
              <Button asChild className="h-10 rounded-none font-bold shadow-none">
                <Link href="/agenda/novo">
                  <Plus className="h-4 w-4" />
                  Novo Evento
                </Link>
              </Button>
            ) : null}
          </div>
        </div>

        <div className="bg-white dark:bg-[#020617] border border-slate-200 dark:border-slate-800 p-4 rounded-none flex flex-wrap items-center gap-4 shadow-sm">
          <div className="flex flex-col gap-1.5">
            <span className="text-[10px] font-bold tracking-wider text-slate-500 uppercase">Processo</span>
            <select
              value={selectedProcessoId}
              onChange={(e) => setSelectedProcessoId(e.target.value)}
              className="h-9 w-48 rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 text-xs font-bold text-slate-700 dark:text-slate-300 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-blue-500"
            >
              <option value="">Todos os Processos</option>
              {(processos.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>
                  {p.numero || p.titulo || "Sem número"}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <span className="text-[10px] font-bold tracking-wider text-slate-500 uppercase">Categoria</span>
            <select
              value={selectedCategoria}
              onChange={(e) => setSelectedCategoria(e.target.value)}
              className="h-9 w-40 rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 text-xs font-bold text-slate-700 dark:text-slate-300 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-blue-500"
            >
              <option value="todos">Todas</option>
              <option value="PRAZO">Prazos Criticos</option>
              <option value="AUDIENCIA">Audiências</option>
              <option value="DILIGENCIA">Diligências</option>
              <option value="REUNIAO">Reuniões</option>
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <span className="text-[10px] font-bold tracking-wider text-slate-500 uppercase">Estado</span>
            <select
              value={selectedConcluido}
              onChange={(e) => setSelectedConcluido(e.target.value)}
              className="h-9 w-36 rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 text-xs font-bold text-slate-700 dark:text-slate-300 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-blue-500"
            >
              <option value="todos">Todos</option>
              <option value="pendentes">Pendentes</option>
              <option value="concluidos">Concluídos</option>
            </select>
          </div>

          <Button
            type="button"
            variant="ghost"
            className="self-end h-9 px-3 rounded-none font-bold text-slate-500 hover:text-slate-900 text-xs hover:bg-slate-100 dark:hover:bg-slate-800"
            onClick={() => {
              setSelectedProcessoId("");
              setSelectedCategoria("todos");
              setSelectedConcluido("todos");
            }}
          >
            Limpar Filtros
          </Button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <LegendChip label="Prazos Criticos" dotClassName="bg-red-500" />
          <LegendChip label="Audiências" dotClassName="bg-blue-500" />
          <LegendChip label="Diligências" dotClassName="bg-amber-500" />
          <LegendChip label="Reuniões" dotClassName="bg-emerald-500" />
        </div>

        {/* Mobile: próximos eventos antes do calendário */}
        {upcoming.length > 0 && (
          <div className="md:hidden space-y-3">
            <div className="text-[10px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">Próximos Eventos</div>
            {upcoming.map((e) => {
              const cat = getCategoria(e);
              const hora = new Date(e.dataInicio).toLocaleTimeString("pt-CV", { hour: "2-digit", minute: "2-digit" });
              const processoLabel = e.processoId ? `Proc. nº ${processoLabelById.get(e.processoId) ?? e.processoId}` : null;
              return (
                <div key={e.id} className={cn("rounded-none border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617] p-4 shadow-sm", cat.borderClassName)}>
                  <div className="flex items-start justify-between gap-3">
                    <div className="text-[10px] font-bold tracking-wider uppercase" style={{ color: cat.titleColor }}>
                      {cat.label}
                    </div>
                    <div className="text-xs font-bold text-slate-500 dark:text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-sm">
                      {hora}
                    </div>
                  </div>
                  <div className="mt-2 text-sm font-bold text-slate-900 dark:text-white">{e.titulo}</div>
                  {processoLabel && (
                    <div className="mt-1 text-[11px] font-medium tracking-wider text-slate-500 dark:text-slate-400 uppercase">
                      {processoLabel}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Mobile: eventos de hoje */}
        <div className="md:hidden space-y-4">
          <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            Hoje — {new Date().toLocaleDateString("pt-CV", { weekday: "long", day: "numeric", month: "long" })}
          </h3>
          {(() => {
            const todayKey = dayKey(new Date());
            const todayEvents = eventosByDay.get(todayKey) ?? [];
            if (!todayEvents.length) {
              return <p className="text-sm text-slate-500 dark:text-slate-400">Nenhum evento hoje.</p>;
            }
            return todayEvents.map((e) => {
              const cat = getCategoria(e);
              const hora = new Date(e.dataInicio).toLocaleTimeString("pt-CV", { hour: "2-digit", minute: "2-digit" });
              const processoLabel = e.processoId ? `Proc. nº ${processoLabelById.get(e.processoId) ?? e.processoId}` : null;
              return (
                <div key={e.id} className={cn("rounded-none border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617] p-4 shadow-sm", cat.borderClassName)}>
                  <div className="flex items-start justify-between gap-3">
                    <div className="text-[10px] font-bold tracking-wider uppercase" style={{ color: cat.titleColor }}>
                      {cat.label}
                    </div>
                    <div className="text-xs font-bold text-slate-500 dark:text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-sm">
                      {hora}
                    </div>
                  </div>
                  <div className="mt-2 text-sm font-bold text-slate-900 dark:text-white">{e.titulo}</div>
                  {processoLabel && (
                    <div className="mt-1 text-[11px] font-medium tracking-wider text-slate-500 dark:text-slate-400 uppercase">
                      {processoLabel}
                    </div>
                  )}
                </div>
              );
            });
          })()}
        </div>

        <Card className="hidden md:block border-slate-200 dark:border-slate-800 rounded-none overflow-hidden shadow-sm">
          <CardContent className="p-0">
            <div className="grid grid-cols-7 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 text-[10px] font-bold tracking-widest text-slate-500 dark:text-slate-400 uppercase">
              {["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"].map((d) => (
                <div key={d} className="px-3 py-3 text-center border-r border-slate-200 dark:border-slate-800 last:border-r-0">{d}</div>
              ))}
            </div>

            {isLoading ? (
              <div className="flex flex-col items-center justify-center p-20 space-y-4">
                <Loader2 className="h-10 w-10 animate-spin text-blue-600 dark:text-blue-400" />
                <p className="text-sm font-bold text-slate-500 uppercase tracking-widest">A carregar agenda...</p>
              </div>
            ) : isError ? (
              <div className="p-6 text-sm text-red-600">
                {eventos.error instanceof Error
                  ? eventos.error.message
                  : processos.error instanceof Error
                    ? processos.error.message
                    : prazos.error instanceof Error
                      ? prazos.error.message
                      : "Erro ao carregar"}
              </div>
            ) : (
              <div className="grid grid-cols-7 bg-slate-200 dark:bg-slate-800 gap-[1px] border-b border-slate-200 dark:border-slate-800">
                {days.map((day) => {
                  const key = dayKey(day.date);
                  const dayEvents = (eventosByDay.get(key) ?? []).slice(0, 3);
                  return (
                    <div
                      key={key}
                      className={cn(
                        "min-h-[120px] bg-white dark:bg-[#020617] p-2 transition-colors hover:bg-slate-50 dark:hover:bg-slate-900/50",
                        !day.isOutsideMonth && dragOverKey === key && "ring-2 ring-inset ring-blue-400 bg-blue-50 dark:bg-blue-900/20",
                        day.isOutsideMonth && "bg-slate-50/50 dark:bg-slate-900/20 opacity-50",
                      )}
                      onDragOver={(ev) => {
                        if (day.isOutsideMonth || !dragState) return;
                        ev.preventDefault();
                        setDragOverKey(key);
                      }}
                      onDragLeave={() => setDragOverKey((k) => (k === key ? null : k))}
                      onDrop={(ev) => {
                        ev.preventDefault();
                        if (day.isOutsideMonth || !dragState) return;
                        const originKey = dayKey(new Date(dragState.originalDataInicio));
                        if (key === originKey) { setDragState(null); setDragOverKey(null); return; }
                        const newInicio = moveDatePreservingTime(dragState.originalDataInicio, key);
                        const durMs = new Date(dragState.originalDataFim).getTime() - new Date(dragState.originalDataInicio).getTime();
                        const newFim = new Date(new Date(newInicio).getTime() + durMs).toISOString().slice(0, 19);
                        setOptimisticOverrides((m) => new Map(m).set(dragState.eventId, newInicio));
                        const id = dragState.eventId;
                        setDragState(null);
                        setDragOverKey(null);
                        dragDropMutation.mutate({ id, dataInicio: newInicio, dataFim: newFim });
                      }}
                    >
                      <div className={cn(
                        "text-xs font-bold w-6 h-6 flex items-center justify-center rounded-sm",
                        key === dayKey(new Date()) ? "bg-blue-600 text-white" : "text-slate-700 dark:text-slate-300"
                      )}>{day.date.getDate()}</div>
                      <div className="mt-2 space-y-1.5">
                        {dayEvents.map((e) => {
                          const cat = getCategoria(e);
                          const href = e.isPrazo
                            ? `/processos/${encodeURIComponent(String(e.processoId))}`
                            : `/agenda/${encodeURIComponent(String(e.id))}`;
                          const canDrag = !e.isPrazo && !(e as Evento).isRecurrenceInstance;
                          return (
                            <Link
                              key={e.id}
                              href={href}
                              draggable={canDrag}
                              className={cn(
                                "block truncate rounded-sm px-1.5 py-1 text-[10px] font-bold tracking-wide uppercase border border-transparent hover:border-current transition-colors",
                                cat.pillClassName,
                                canDrag && "cursor-grab active:cursor-grabbing",
                              )}
                              onDragStart={(ev) => {
                                if (!canDrag) { ev.preventDefault(); return; }
                                ev.dataTransfer.setData("text/plain", String(e.id));
                                ev.dataTransfer.effectAllowed = "move";
                                setDragState({ eventId: e.id as number, originalDataInicio: e.dataInicio, originalDataFim: e.dataFim });
                              }}
                              onDragEnd={() => { setDragState(null); setDragOverKey(null); }}
                            >
                              {!e.isPrazo && (e as Evento).isRecurrenceInstance ? <span className="mr-1" title="Evento recorrente">&#x21BB;</span> : null}<span className="opacity-80 mr-1">{cat.shortLabel}:</span>{e.titulo}
                            </Link>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="space-y-6">
        <Card className="border-t-4 border-t-blue-600 dark:border-t-blue-500 rounded-none shadow-sm border-slate-200 dark:border-slate-800 bg-slate-50/30 dark:bg-slate-900/20">
          <CardHeader className="flex-row items-center justify-between pb-4">
            <CardTitle className="text-slate-900 dark:text-white">Próximos Eventos</CardTitle>
            <Badge variant="secondary" className="rounded-none font-bold">BREVEMENTE</Badge>
          </CardHeader>
          <CardContent className="space-y-4">
            {upcoming.length ? (
              upcoming.map((e) => {
                const cat = getCategoria(e);
                return (
                  <div key={e.id} className={cn("rounded-none border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617] p-4 shadow-sm hover:shadow-md transition-all", cat.borderClassName)}>
                    <div className="flex items-start justify-between gap-3">
                      <div className="text-[10px] font-bold tracking-wider uppercase" style={{ color: cat.titleColor }}>
                        {cat.label}
                      </div>
                      <div className="text-xs font-bold text-slate-500 dark:text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-sm">
                        {new Date(e.dataInicio).toLocaleTimeString("pt-CV", { hour: "2-digit", minute: "2-digit" })}
                      </div>
                    </div>
                    <div className="mt-2 text-sm font-bold text-slate-900 dark:text-white">{e.titulo}</div>
                    <div className="mt-1 text-[11px] font-medium tracking-wider text-slate-500 dark:text-slate-400 uppercase">
                      {e.processoId ? `Proc. nº ${processoLabelById.get(e.processoId) ?? e.processoId}` : "—"}
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="text-sm text-slate-500 dark:text-slate-400 italic">Sem eventos futuros.</div>
            )}

            <Button asChild variant="outline" className="w-full rounded-none border-dashed border-slate-300 dark:border-slate-700 hover:border-slate-400 dark:hover:border-slate-500 bg-transparent font-bold">
              <Link href="/agenda">Ver Lista Completa</Link>
            </Button>
          </CardContent>
        </Card>

        <Card className="bg-slate-950 dark:bg-[#020617] text-white border-slate-900 dark:border-slate-800 rounded-none shadow-xl relative overflow-hidden group">
          <div className="absolute inset-0 bg-[url('/grid.svg')] opacity-10" />
          <CardContent className="p-6 relative z-10">
            <div className="text-[11px] font-bold tracking-wider uppercase text-blue-400">Visão Geral da Semana</div>
            <div className="mt-5 flex items-end justify-between">
              <div>
                <div className="text-4xl font-black">{weekStats.prazosAtivos}</div>
                <div className="text-xs font-bold tracking-wider uppercase text-slate-400 mt-1">Prazos ativos</div>
              </div>
            </div>
            <div className="mt-6 grid grid-cols-2 gap-4">
              <div className="border-t border-slate-800 pt-3">
                <div className="text-[10px] font-bold tracking-wider uppercase text-slate-500">Audiências</div>
                <div className="mt-1 text-2xl font-bold">{String(weekStats.audiencias).padStart(2, "0")}</div>
              </div>
              <div className="border-t border-slate-800 pt-3">
                <div className="text-[10px] font-bold tracking-wider uppercase text-red-500/70">Urgentes</div>
                <div className="mt-1 text-2xl font-bold text-red-400">{String(weekStats.urgentes).padStart(2, "0")}</div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function moveDatePreservingTime(originalISO: string, newDateKey: string): string {
  return newDateKey + originalISO.slice(10);
}

function dayKey(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function buildMonthGrid(monthStart: Date) {
  const start = new Date(monthStart.getFullYear(), monthStart.getMonth(), 1);
  const end = new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 0);

  const firstDow = start.getDay();
  const gridStart = new Date(start);
  gridStart.setDate(start.getDate() - firstDow);

  const days: { date: Date; isOutsideMonth: boolean }[] = [];
  for (let i = 0; i < 42; i += 1) {
    const d = new Date(gridStart);
    d.setDate(gridStart.getDate() + i);
    days.push({ date: d, isOutsideMonth: d.getMonth() !== monthStart.getMonth() });
  }

  if (end.getDay() === 6 && days.length === 42) return days;
  return days;
}

function LegendChip({ label, dotClassName }: { label: string; dotClassName: string }) {
  return (
    <div className="inline-flex items-center gap-2 rounded-none border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#020617] px-3 py-1.5 text-[11px] font-bold tracking-wider uppercase text-slate-700 dark:text-slate-300 shadow-sm">
      <span className={cn("h-2 w-2 rounded-none", dotClassName)} />
      {label}
    </div>
  );
}

function getCategoria(e: { titulo?: string | null; tipo?: string | null }) {
  const t = (e.titulo ?? "").toLowerCase();
  const tipo = e.tipo?.toUpperCase();

  if (tipo === "PRAZO" || (!tipo && t.includes("prazo"))) {
    return {
      id: "PRAZO",
      label: "PRAZO FATAL",
      shortLabel: "Prazo",
      pillClassName: "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400",
      borderClassName: "border-l-4 border-l-red-500",
      titleColor: "#ef4444",
    } as const;
  }
  if (tipo === "AUDIENCIA" || (!tipo && (t.includes("audiência") || t.includes("audiencia")))) {
    return {
      id: "AUDIENCIA",
      label: "AUDIÊNCIA",
      shortLabel: "Audiência",
      pillClassName: "bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-400",
      borderClassName: "border-l-4 border-l-blue-500",
      titleColor: "#3b82f6",
    } as const;
  }
  if (tipo === "DILIGENCIA" || (!tipo && (t.includes("diligência") || t.includes("diligencia")))) {
    return {
      id: "DILIGENCIA",
      label: "DILIGÊNCIA",
      shortLabel: "Diligência",
      pillClassName: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400",
      borderClassName: "border-l-4 border-l-amber-500",
      titleColor: "#f59e0b",
    } as const;
  }
  if (tipo === "OUTRO" || (!tipo && t.includes("outro"))) {
    return {
      id: "OUTRO",
      label: "OUTRO",
      shortLabel: "Outro",
      pillClassName: "bg-neutral-100 text-neutral-700 dark:bg-neutral-500/10 dark:text-neutral-400",
      borderClassName: "border-l-4 border-l-neutral-500",
      titleColor: "#737373",
    } as const;
  }
  return {
    id: "REUNIAO",
    label: "REUNIÃO",
    shortLabel: "Reunião",
    pillClassName: "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400",
    borderClassName: "border-l-4 border-l-emerald-500",
    titleColor: "#10b981",
  } as const;
}
