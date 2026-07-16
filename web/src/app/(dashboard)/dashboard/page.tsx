"use client";

import type { ReactNode } from "react";

import Link from "next/link";
import {
  CalendarCheck,
  FileText,
  FolderOpen,
  Inbox,
  Plus,
  Scale,
  Timer,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useClientes } from "@/hooks/use-clientes";
import { useDashboardKpis } from "@/hooks/use-dashboard-kpis";
import { useEventos } from "@/hooks/use-eventos";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";

function EmptyState({
  icon: Icon,
  title,
  description,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
}) {
  return (
    <Empty>
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <Icon />
        </EmptyMedia>
        <EmptyTitle className="text-sm font-semibold">{title}</EmptyTitle>
        <EmptyDescription>{description}</EmptyDescription>
      </EmptyHeader>
    </Empty>
  );
}

export default function DashboardPage() {
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");
  const canViewProcessos = permissions.can.view("processos");
  const canViewAgenda = permissions.can.view("agenda");
  const canViewFinanceiro = permissions.can.view("financeiro");
  const canCreateProcessos = permissions.can.create("processos");
  const canViewAny =
    canViewClientes || canViewProcessos || canViewAgenda || canViewFinanceiro;

  if (permissions.isFetched && !canViewAny) {
    return (
      <AccessDeniedState
        description="Não tem permissões suficientes para aceder ao dashboard."
        backHref="/login"
        backLabel="Ir para login"
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-white">Dashboard Institucional</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Bem-vindo de volta. Aqui está o resumo da sua atividade judicial hoje.
          </p>
        </div>
        {canCreateProcessos ? (
          <Button asChild>
            <Link href="/processos/novo">
              <Plus className="h-4 w-4" />
              Novo Processo
            </Link>
          </Button>
        ) : null}
      </div>

      <DashboardKpis
        canViewClientes={canViewClientes}
        canViewProcessos={canViewProcessos}
        canViewAgenda={canViewAgenda}
        canViewFinanceiro={canViewFinanceiro}
      />

      <div className="grid gap-6 lg:grid-cols-3">
        {canViewProcessos ? <ProcessosStatusCard /> : null}

        {canViewAgenda ? <PrazosUrgentesCard /> : null}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {canViewProcessos ? (
          canViewClientes ? (
            <RecentProcessosCardWithClientes />
          ) : (
            <RecentProcessosCardNoClientes />
          )
        ) : null}

        <AtividadeRecenteCard />
      </div>
    </div>
  );
}

type AtividadeRecenteEntry = {
  id: string;
  icon: ReactNode;
  iconWrapperClassName: string;
  titulo: string;
  timestamp: string;
};

const ATIVIDADE_RECENTE_ENTRIES: AtividadeRecenteEntry[] = [
  {
    id: "documento-submetido",
    icon: <FileText className="h-4 w-4 text-blue-600 dark:text-blue-400" />,
    iconWrapperClassName:
      "h-10 w-10 rounded-none bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center border border-blue-100 dark:border-blue-500/20 group-hover:bg-blue-100 dark:group-hover:bg-blue-500/20 transition-colors",
    titulo: "Documento Submetido",
    timestamp: "Há 45 minutos",
  },
  {
    id: "novo-cliente",
    icon: "+",
    iconWrapperClassName:
      "h-10 w-10 rounded-none bg-emerald-50 dark:bg-emerald-500/10 flex items-center justify-center border border-emerald-100 dark:border-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-lg font-light group-hover:bg-emerald-100 dark:group-hover:bg-emerald-500/20 transition-colors",
    titulo: "Novo Cliente",
    timestamp: "Há 2 horas",
  },
  {
    id: "processo-concluido",
    icon: "✓",
    iconWrapperClassName:
      "h-10 w-10 rounded-none bg-slate-100 dark:bg-slate-800 flex items-center justify-center border border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-400 text-sm font-bold group-hover:bg-slate-200 dark:group-hover:bg-slate-700 transition-colors",
    titulo: "Processo Concluído",
    timestamp: "Ontem, 17:40",
  },
];

function AtividadeRecenteCard() {
  const kpis = useDashboardKpis();
  const entries = ATIVIDADE_RECENTE_ENTRIES;

  return (
    <Card>
      <CardHeader className="border-b border-slate-100 dark:border-slate-800 pb-4 bg-slate-50/50 dark:bg-slate-900/20">
        <CardTitle className="text-slate-900 dark:text-white flex items-center gap-2">
          <Users className="h-4 w-4 text-slate-500" />
          Atividade Recente
        </CardTitle>
      </CardHeader>
      <CardContent className="pt-6 space-y-5">
        {kpis.isLoading ? (
          <>
            {Array.from({ length: 3 }).map((_, index) => (
              <div key={index} className="flex gap-4">
                <Skeleton className="h-10 w-10" />
                <div className="min-w-0 pt-1">
                  <Skeleton className="h-4 w-40" />
                  <Skeleton className="h-3 w-24 mt-1" />
                </div>
              </div>
            ))}
          </>
        ) : entries.length === 0 ? (
          <EmptyState
            icon={Inbox}
            title="Sem atividade recente"
            description="A atividade mais recente vai aparecer aqui."
          />
        ) : (
          entries.map((entry) => (
            <div key={entry.id} className="flex gap-4 group">
              <div className={entry.iconWrapperClassName}>{entry.icon}</div>
              <div className="min-w-0 pt-1">
                <div className="text-sm font-bold text-slate-900 dark:text-white">{entry.titulo}</div>
                <div className="text-[11px] font-medium tracking-wider text-slate-500 dark:text-slate-400 uppercase mt-1">
                  {entry.timestamp}
                </div>
              </div>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
}

function DashboardKpis({
  canViewClientes,
  canViewProcessos,
  canViewAgenda,
  canViewFinanceiro,
}: {
  canViewClientes: boolean;
  canViewProcessos: boolean;
  canViewAgenda: boolean;
  canViewFinanceiro: boolean;
}) {
  const kpis = useDashboardKpis();
  const visibleKpiCount = [
    canViewClientes,
    canViewProcessos,
    canViewAgenda,
    canViewFinanceiro,
  ].filter(Boolean).length;

  if (kpis.isError) {
    if (!visibleKpiCount) return null;
    return (
      <p className="text-sm text-red-600">
        {kpis.error instanceof Error ? kpis.error.message : "Não foi possível carregar os indicadores."}
      </p>
    );
  }

  if (kpis.isLoading) {
    if (!visibleKpiCount) return null;
    return (
      <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: visibleKpiCount }).map((_, index) => (
          <KpiCardSkeleton key={index} />
        ))}
      </div>
    );
  }

  const cards = [
    canViewClientes ? (
      <Card key="clientes">
        <CardContent className="p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="h-10 w-10 rounded-none bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center border border-blue-100 dark:border-blue-500/20">
              <Users className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <Badge variant="green" className="rounded-none font-bold tracking-wide">
              +12%
            </Badge>
          </div>
          <div className="mt-5 text-xs font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">
            Clientes Ativos
          </div>
          <div className="mt-1 text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            {kpis.data?.total_clientes ?? "—"}
          </div>
        </CardContent>
      </Card>
    ) : null,
    canViewProcessos ? (
      <Card key="processos">
        <CardContent className="p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="h-10 w-10 rounded-none bg-slate-100 dark:bg-slate-800 flex items-center justify-center border border-slate-200 dark:border-slate-700">
              <Scale className="h-5 w-5 text-slate-700 dark:text-slate-300" />
            </div>
            <Badge variant="gray" className="rounded-none font-bold tracking-wide">
              Estável
            </Badge>
          </div>
          <div className="mt-5 text-xs font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">
            Processos em Curso
          </div>
          <div className="mt-1 text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            {kpis.data?.processos_ativos ?? "—"}
          </div>
        </CardContent>
      </Card>
    ) : null,
    canViewAgenda ? (
      <Card key="agenda">
        <CardContent className="p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="h-10 w-10 rounded-none bg-red-50 dark:bg-red-500/10 flex items-center justify-center border border-red-100 dark:border-red-500/20">
              <Timer className="h-5 w-5 text-red-600 dark:text-red-400" />
            </div>
            <Badge variant="red" className="rounded-none font-bold tracking-wide">
              Urgente
            </Badge>
          </div>
          <div className="mt-5 text-xs font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">
            Prazos Próximos
          </div>
          <div className="mt-1 text-3xl font-bold text-red-600 dark:text-red-400 tracking-tight">
            {kpis.data?.prazos_vencer ?? "—"}
          </div>
        </CardContent>
      </Card>
    ) : null,
    canViewFinanceiro ? (
      <Card key="financeiro">
        <CardContent className="p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="h-10 w-10 rounded-none bg-emerald-50 dark:bg-emerald-500/10 flex items-center justify-center border border-emerald-100 dark:border-emerald-500/20">
              <Wallet className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
            </div>
            <Badge variant="green" className="rounded-none font-bold tracking-wide">
              +8%
            </Badge>
          </div>
          <div className="mt-5 text-xs font-bold tracking-wider text-slate-500 dark:text-slate-400 uppercase">
            Honorários/Mês
          </div>
          <div className="mt-1 text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
            {kpis.data ? `${Math.round(kpis.data.valores_recebidos_mes / 1000)}K` : "—"}{" "}
            <span className="text-xs font-bold text-slate-500 dark:text-slate-400">CVE</span>
          </div>
        </CardContent>
      </Card>
    ) : null,
  ].filter(Boolean);

  if (!cards.length) return null;

  return <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">{cards}</div>;
}

function KpiCardSkeleton() {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-3">
          <Skeleton className="h-10 w-10" />
          <Skeleton className="h-5 w-14 rounded-full" />
        </div>
        <Skeleton className="h-3 w-24 mt-4" />
        <Skeleton className="h-8 w-16 mt-1" />
      </CardContent>
    </Card>
  );
}

function ProcessosStatusCard() {
  return (
    <Card className="lg:col-span-2 relative overflow-hidden">
      <CardHeader className="flex-row items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-4 bg-slate-50/50 dark:bg-slate-900/20">
        <CardTitle className="text-slate-900 dark:text-white flex items-center gap-2">
          <Scale className="h-4 w-4 text-blue-500" />
          Status dos Processos
        </CardTitle>
        <Badge variant="secondary" className="rounded-none font-medium">
          Últimos 6 meses
        </Badge>
      </CardHeader>
      <CardContent className="pt-6">
        <div className="h-[260px] border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900/50 relative group">
          <div className="absolute inset-0 bg-[url('/grid.svg')] opacity-5 dark:opacity-10" />
        </div>
        <div className="mt-5 flex items-center justify-center gap-6 text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
          <span className="flex items-center gap-1.5">
            <div className="h-2 w-2 bg-blue-500" />
            Novos
          </span>
          <span className="flex items-center gap-1.5">
            <div className="h-2 w-2 bg-red-500" />
            Prazos
          </span>
          <span className="flex items-center gap-1.5">
            <div className="h-2 w-2 bg-emerald-500" />
            Ativos
          </span>
          <span className="flex items-center gap-1.5">
            <div className="h-2 w-2 bg-slate-500" />
            Concluídos
          </span>
          <span className="flex items-center gap-1.5">
            <div className="h-2 w-2 bg-slate-300 dark:bg-slate-700" />
            Arquivados
          </span>
        </div>
      </CardContent>
    </Card>
  );
}

function isSameCalendarDay(a: Date, b: Date) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

function PrazosUrgentesCard() {
  const urgentes = useEventos({ concluido: false });
  const urgentEventos = (urgentes.data ?? [])
    .slice()
    .sort((a, b) => new Date(a.dataInicio).getTime() - new Date(b.dataInicio).getTime())
    .slice(0, 2);

  return (
    <Card className="border-t-4 border-t-red-500 dark:border-t-red-500">
      <CardHeader className="pb-4">
        <CardTitle className="text-red-600 dark:text-red-400 flex items-center gap-2">
          <Timer className="h-4 w-4" />
          Prazos Urgentes
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {urgentEventos.length ? (
          urgentEventos.map((e) => {
            const dataInicio = new Date(e.dataInicio);
            return (
              <div
                key={e.id}
                className="border-l-2 border-red-500 pl-3 py-1 bg-red-50/50 dark:bg-red-500/5 p-3 group hover:bg-red-50 dark:hover:bg-red-500/10 transition-colors"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="text-sm font-bold text-slate-900 dark:text-white group-hover:text-red-600 dark:group-hover:text-red-400 transition-colors">
                    {e.titulo}
                  </div>
                  <Badge variant="red" className="rounded-none">
                    {isSameCalendarDay(dataInicio, new Date())
                      ? "HOJE"
                      : dataInicio.toLocaleDateString("pt-CV", { day: "2-digit", month: "2-digit" })}
                  </Badge>
                </div>
                <div className="mt-1.5 text-[11px] font-medium tracking-wider text-slate-500 dark:text-slate-400 uppercase">
                  {e.processoId ? `Processo ${e.processoId}` : "Sem processo"}
                </div>
                <div className="mt-1 text-xs text-slate-600 dark:text-slate-300">
                  {dataInicio.toLocaleString("pt-CV")}
                </div>
              </div>
            );
          })
        ) : urgentes.isError ? (
          <p className="text-sm text-red-600">
            {urgentes.error instanceof Error ? urgentes.error.message : "Não foi possível carregar os prazos."}
          </p>
        ) : urgentes.isLoading ? null : (
          <EmptyState
            icon={CalendarCheck}
            title="Sem prazos urgentes"
            description="Não há eventos urgentes nos próximos dias."
          />
        )}

        <Button
          asChild
          variant="outline"
          className="w-full rounded-none border-dashed border-slate-300 dark:border-slate-700 hover:border-slate-400 dark:hover:border-slate-500"
        >
          <Link href="/agenda">Ver Agenda Completa</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

function RecentProcessosCardWithClientes() {
  const processos = useProcessos();
  const clientes = useClientes({});

  const clienteNomeById = new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const));
  const recentProcessos = (processos.data ?? []).slice(0, 3);

  return (
    <RecentProcessosCard
      recentProcessos={recentProcessos}
      clienteNomeById={clienteNomeById}
      isLoading={processos.isLoading || clientes.isLoading}
      isError={processos.isError}
    />
  );
}

function RecentProcessosCardNoClientes() {
  const processos = useProcessos();
  const recentProcessos = (processos.data ?? []).slice(0, 3);
  return (
    <RecentProcessosCard
      recentProcessos={recentProcessos}
      clienteNomeById={undefined}
      isLoading={processos.isLoading}
      isError={processos.isError}
    />
  );
}

function RecentProcessosCard({
  recentProcessos,
  clienteNomeById,
  isLoading,
  isError,
}: {
  recentProcessos: Array<{
    id: string;
    numero?: string;
    titulo?: string;
    cliente_id: string;
    estado?: string;
  }>;
  clienteNomeById: Map<string, string> | undefined;
  isLoading: boolean;
  isError: boolean;
}) {
  return (
    <Card className="lg:col-span-2">
      <CardHeader className="flex-row items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-4 bg-slate-50/50 dark:bg-slate-900/20">
        <CardTitle className="text-slate-900 dark:text-white flex items-center gap-2">
          <Scale className="h-4 w-4 text-blue-500" />
          Processos Recentes
        </CardTitle>
        <Link
          href="/processos"
          className="text-xs font-bold uppercase tracking-wider text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 transition-colors"
        >
          Ver todos
        </Link>
      </CardHeader>
      {isError ? (
        <CardContent>
          <p className="text-sm text-red-600">Não foi possível carregar os processos recentes.</p>
        </CardContent>
      ) : recentProcessos.length === 0 && !isLoading ? (
        <CardContent>
          <EmptyState
            icon={FolderOpen}
            title="Sem processos recentes"
            description="Os processos criados mais recentemente aparecerão aqui."
          />
        </CardContent>
      ) : (
      <CardContent className="p-0">
        <Table>
          <TableHeader className="bg-slate-50 dark:bg-slate-900/50">
            <TableRow className="border-slate-200 dark:border-slate-800 hover:bg-transparent">
              <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">
                ID Processo
              </TableHead>
              <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">
                Cliente
              </TableHead>
              <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">
                Estado
              </TableHead>
              <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px] text-right">
                Ação
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {recentProcessos.map((p) => (
              <TableRow
                key={p.id}
                className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors"
              >
                <TableCell>
                  <Link
                    href={`/processos/${encodeURIComponent(p.id)}`}
                    className="font-medium text-blue-600 hover:underline dark:text-blue-400"
                  >
                    {p.numero || p.titulo || "Sem número"}
                  </Link>
                </TableCell>
                <TableCell className="text-slate-600 dark:text-slate-300 font-medium">
                  {clienteNomeById?.get(p.cliente_id) ?? "—"}
                </TableCell>
                <TableCell>
                  <Badge variant="green" className="rounded-none">
                    {p.estado ?? "Ativo"}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    asChild
                    size="sm"
                    variant="ghost"
                    className="h-8 text-xs font-semibold text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
                  >
                    <Link href={`/processos/${encodeURIComponent(p.id)}`}>Abrir</Link>
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
      )}
    </Card>
  );
}
