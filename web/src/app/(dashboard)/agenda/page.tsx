"use client";

import Link from "next/link";
import * as React from "react";
import { ChevronLeft, ChevronRight, Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { usePermissions } from "@/hooks/use-permissions";
import { useEventos } from "@/hooks/use-eventos";
import { useProcessos } from "@/hooks/use-processos";
import type { Evento } from "@/types/eventos";
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

  const processoLabelById = new Map(
    (processos.data ?? []).map((p) => [p.id, p.numero ?? p.titulo ?? p.id] as const),
  );

  const initialMonth = React.useMemo(() => {
    const d = (eventos.data ?? [])
      .map((e) => new Date(e.data_inicio))
      .filter((x) => !Number.isNaN(x.getTime()))
      .sort((a, b) => a.getTime() - b.getTime())[0];
    const base = d ?? new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  }, [eventos.data]);

  const [cursorMonthOverride, setCursorMonthOverride] = React.useState<Date | null>(null);
  const cursorMonth = cursorMonthOverride ?? initialMonth;

  const monthLabel = cursorMonth.toLocaleDateString("pt-CV", { month: "long", year: "numeric" });

  const days = React.useMemo(() => buildMonthGrid(cursorMonth), [cursorMonth]);

  const eventosByDay = React.useMemo(() => {
    const map = new Map<string, Evento[]>();
    for (const e of eventos.data ?? []) {
      const d = new Date(e.data_inicio);
      if (Number.isNaN(d.getTime())) continue;
      const key = dayKey(d);
      map.set(key, [...(map.get(key) ?? []), e]);
    }
    return map;
  }, [eventos.data]);

  const upcoming = React.useMemo(() => {
    const now = new Date().getTime();
    return (eventos.data ?? [])
      .filter((e) => !e.concluido)
      .map((e) => ({ e, t: new Date(e.data_inicio).getTime() }))
      .filter((x) => !Number.isNaN(x.t) && x.t >= now)
      .sort((a, b) => a.t - b.t)
      .slice(0, 4)
      .map((x) => x.e);
  }, [eventos.data]);

  const weekStats = React.useMemo(() => {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const end = new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000);

    const active = (eventos.data ?? []).filter((e) => {
      if (e.concluido) return false;
      const t = new Date(e.data_inicio).getTime();
      return t >= start.getTime() && t < end.getTime();
    });

    const prazosAtivos = active.filter((e) => getCategoria(e).id === "PRAZO").length;
    const audiencias = active.filter((e) => getCategoria(e).id === "AUDIENCIA").length;
    const urgentes = active.filter((e) => e.prioridade === "ALTA").length;

    return { prazosAtivos, audiencias, urgentes };
  }, [eventos.data]);

  const isLoading = processos.isPending || eventos.isPending;
  const isError = processos.isError || eventos.isError;

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

        <div className="flex flex-wrap items-center gap-2">
          <LegendChip label="Prazos Fatais" dotClassName="bg-red-500" />
          <LegendChip label="Audiências" dotClassName="bg-blue-500" />
          <LegendChip label="Diligências" dotClassName="bg-amber-500" />
          <LegendChip label="Reuniões" dotClassName="bg-emerald-500" />
        </div>

        <Card className="border-slate-200 dark:border-slate-800 rounded-none overflow-hidden shadow-sm">
          <CardContent className="p-0">
            <div className="grid grid-cols-7 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 text-[10px] font-bold tracking-widest text-slate-500 dark:text-slate-400 uppercase">
              {["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"].map((d) => (
                <div key={d} className="px-3 py-3 text-center border-r border-slate-200 dark:border-slate-800 last:border-r-0">{d}</div>
              ))}
            </div>

            {isLoading ? (
              <div className="p-6 text-sm text-slate-500">A carregar...</div>
            ) : isError ? (
              <div className="p-6 text-sm text-red-600">
                {eventos.error instanceof Error
                  ? eventos.error.message
                  : processos.error instanceof Error
                    ? processos.error.message
                    : "Erro ao carregar"}
              </div>
            ) : (
              <div className="grid grid-cols-7 bg-slate-200 dark:bg-slate-800 gap-[1px] border-b border-slate-200 dark:border-slate-800">
                {days.map((day) => {
                  const key = dayKey(day.date);
                  const events = (eventosByDay.get(key) ?? []).slice(0, 3);
                  return (
                    <div
                      key={key}
                      className={cn(
                        "min-h-[120px] bg-white dark:bg-[#020617] p-2 transition-colors hover:bg-slate-50 dark:hover:bg-slate-900/50",
                        day.isOutsideMonth && "bg-slate-50/50 dark:bg-slate-900/20 opacity-50",
                      )}
                    >
                      <div className={cn(
                        "text-xs font-bold w-6 h-6 flex items-center justify-center rounded-sm",
                        key === dayKey(new Date()) ? "bg-blue-600 text-white" : "text-slate-700 dark:text-slate-300"
                      )}>{day.date.getDate()}</div>
                      <div className="mt-2 space-y-1.5">
                        {events.map((e) => {
                          const cat = getCategoria(e);
                          return (
                            <Link
                              key={e.id}
                              href={`/agenda/${encodeURIComponent(String(e.id))}`}
                              className={cn(
                                "block truncate rounded-sm px-1.5 py-1 text-[10px] font-bold tracking-wide uppercase border border-transparent hover:border-current transition-colors",
                                cat.pillClassName,
                              )}
                            >
                              <span className="opacity-80 mr-1">{cat.shortLabel}:</span>{e.titulo}
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
                        {new Date(e.data_inicio).toLocaleTimeString("pt-CV", { hour: "2-digit", minute: "2-digit" })}
                      </div>
                    </div>
                    <div className="mt-2 text-sm font-bold text-slate-900 dark:text-white">{e.titulo}</div>
                    <div className="mt-1 text-[11px] font-medium tracking-wider text-slate-500 dark:text-slate-400 uppercase">
                      {e.processo_id ? `Proc. nº ${processoLabelById.get(e.processo_id) ?? e.processo_id}` : "—"}
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

function getCategoria(e: Evento) {
  const t = e.titulo.toLowerCase();
  if (t.includes("prazo")) {
    return {
      id: "PRAZO",
      label: "PRAZO FATAL",
      shortLabel: "Prazo",
      pillClassName: "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-400",
      borderClassName: "border-l-4 border-l-red-500",
      titleColor: "#ef4444",
    } as const;
  }
  if (t.includes("audiência") || t.includes("audiencia")) {
    return {
      id: "AUDIENCIA",
      label: "AUDIÊNCIA",
      shortLabel: "Audiência",
      pillClassName: "bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-400",
      borderClassName: "border-l-4 border-l-blue-500",
      titleColor: "#3b82f6",
    } as const;
  }
  if (t.includes("diligência") || t.includes("diligencia")) {
    return {
      id: "DILIGENCIA",
      label: "DILIGÊNCIA",
      shortLabel: "Diligência",
      pillClassName: "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400",
      borderClassName: "border-l-4 border-l-amber-500",
      titleColor: "#f59e0b",
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
