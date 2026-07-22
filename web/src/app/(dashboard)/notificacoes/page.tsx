"use client";

import { Check, CheckCheck, X } from "lucide-react";
import Link from "next/link";
import * as React from "react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { NotificacaoSnoozeControl } from "@/components/shared/notificacao-snooze-control";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  useMarcarNotificacaoLida,
  useMarcarTodasNotificacoesLidas,
  useNotificacoes,
  useNotificacoesUnreadCount,
} from "@/hooks/use-notificacoes";
import { usePermissions } from "@/hooks/use-permissions";
import { toast } from "@/hooks/use-toast";
import {
  categoriaToBadgeVariant,
  categoriaToLabel,
  isInternalLinkUrl,
  NOTIFICACAO_CATEGORIA_OPTIONS,
} from "@/lib/notificacao-categoria";
import type { Notificacao, NotificacaoCategoria } from "@/types/notificacoes";

const LIDA_FILTER_CHIPS: { label: string; value: boolean | undefined }[] = [
  { label: "Todas", value: undefined },
  { label: "Não lidas", value: false },
  { label: "Lidas", value: true },
];

export default function NotificacoesPage() {
  const permissions = usePermissions();
  const canView = permissions.can.view("notificacoes");

  if (permissions.isFetched && !canView) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar notificações."
        backHref="/dashboard"
      />
    );
  }

  return <NotificacoesContent />;
}

function NotificacoesContent() {
  const [categoria, setCategoria] = React.useState("");
  const [lidaFilter, setLidaFilter] = React.useState<boolean | undefined>(undefined);
  const [page, setPage] = React.useState(0);

  const list = useNotificacoes({
    categoria: categoria === "" ? undefined : (categoria as NotificacaoCategoria),
    lida: lidaFilter,
    page,
    size: 20,
  });

  // Clamp `page` back into range whenever the server reports fewer pages than
  // currently selected (e.g. marking the last item on the final page as read
  // shrinks totalPages via refetch, but `page` itself never changes on its
  // own). Derived during render — React's documented "adjusting state when a
  // prop changes" pattern — instead of via useEffect + setState: the effect
  // version committed the stale/out-of-range page first and only corrected
  // it on a second render/commit (a visible flash of stale content, plus an
  // eslint `react-hooks/set-state-in-effect` error). The `lastTotalPages`
  // guard only lets the block run when `totalPages` actually changes, so
  // React discards the in-progress render and re-renders immediately with
  // the corrected page before anything paints, instead of looping.
  const [lastTotalPages, setLastTotalPages] = React.useState(list.data?.totalPages);
  if (list.data && list.data.totalPages !== lastTotalPages) {
    setLastTotalPages(list.data.totalPages);
    if (list.data.totalPages > 0 && page >= list.data.totalPages) {
      setPage(list.data.totalPages - 1);
    }
  }

  const unreadCount = useNotificacoesUnreadCount();
  const marcarLida = useMarcarNotificacaoLida();
  const marcarTodas = useMarcarTodasNotificacoesLidas();

  const onCategoriaChange = (value: string) => {
    setCategoria(value);
    setPage(0);
  };

  const onLidaFilterChange = (value: boolean | undefined) => {
    setLidaFilter(value);
    setPage(0);
  };

  const hasFilters = categoria !== "" || lidaFilter !== undefined;

  const onLimparFiltros = () => {
    setCategoria("");
    setLidaFilter(undefined);
    setPage(0);
  };

  const onMarcarTodas = async () => {
    try {
      await marcarTodas.mutateAsync();
      toast.success("Todas as notificações foram marcadas como lidas.");
    } catch {
      // Erro já reportado pelo toast automático do apiFetch (exceto 401/403).
    }
  };

  const marcarTodasDisabled = marcarTodas.isPending || (unreadCount.data?.count ?? 0) === 0;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Notificações</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Histórico completo de alertas dos seus processos, documentos e pareceres.
          </p>
        </div>
        <Button type="button" variant="outline" onClick={onMarcarTodas} disabled={marcarTodasDisabled}>
          <CheckCheck className="h-4 w-4" />
          {marcarTodas.isPending ? "A marcar..." : "Marcar todas como lidas"}
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filtros</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <label
              htmlFor="notificacoes-categoria-filter"
              className="text-sm text-slate-700 dark:text-slate-300"
            >
              Categoria
            </label>
            <select
              id="notificacoes-categoria-filter"
              className="h-10 w-full bg-white dark:bg-[#020617] border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              value={categoria}
              onChange={(e) => onCategoriaChange(e.target.value)}
            >
              <option value="">Todas as categorias</option>
              {NOTIFICACAO_CATEGORIA_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <span className="text-sm text-slate-700 dark:text-slate-300">Estado</span>
            <div
              className="flex items-center gap-2"
              role="group"
              aria-label="Filtrar por estado de leitura"
            >
              {LIDA_FILTER_CHIPS.map((chip) => {
                const active = lidaFilter === chip.value;
                return (
                  <button
                    key={chip.label}
                    type="button"
                    aria-pressed={active}
                    className={
                      active
                        ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-md h-8 px-3 text-xs"
                        : "border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-transparent text-slate-700 dark:text-slate-300 rounded-md h-8 px-3 text-xs hover:bg-slate-50 dark:hover:bg-slate-800"
                    }
                    onClick={() => onLidaFilterChange(chip.value)}
                  >
                    {chip.label}
                  </button>
                );
              })}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Resultados</CardTitle>
        </CardHeader>
        <CardContent>
          {list.isPending ? (
            <div className="text-sm text-slate-500 dark:text-slate-400">A carregar...</div>
          ) : list.isError ? (
            <div className="text-sm text-red-600">
              Não foi possível carregar as notificações. Verifique a ligação e tente novamente.
            </div>
          ) : !list.data?.content.length ? (
            hasFilters ? (
              <div className="space-y-2 py-6 text-center">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Nenhuma notificação encontrada para os filtros selecionados.
                </p>
                <Button type="button" variant="ghost" size="sm" onClick={onLimparFiltros}>
                  <X className="h-4 w-4" />
                  Limpar filtros
                </Button>
              </div>
            ) : (
              <div className="space-y-1 py-6 text-center">
                <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">Sem notificações</p>
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Vai receber um alerta aqui sempre que algo relevante acontecer nos seus processos,
                  documentos ou pareceres.
                </p>
              </div>
            )
          ) : (
            <>
              <div className="divide-y divide-slate-100 dark:divide-slate-800">
                {list.data.content.map((n) => (
                  <NotificacaoRow
                    key={n.id}
                    notificacao={n}
                    onMarcarLida={(id) => marcarLida.mutate(id)}
                    isMarking={marcarLida.isPending && marcarLida.variables === n.id}
                  />
                ))}
              </div>
              {list.data.totalPages > 1 ? (
                <Pagination className="pt-4">
                  <PaginationContent className="w-full justify-between">
                    <PaginationItem>
                      <PaginationPrevious
                        text="Anterior"
                        href="#"
                        aria-disabled={page === 0}
                        className={page === 0 ? "pointer-events-none opacity-50" : undefined}
                        onClick={(e) => {
                          e.preventDefault();
                          if (page === 0) return;
                          setPage((p) => Math.max(0, p - 1));
                        }}
                      />
                    </PaginationItem>
                    <PaginationItem>
                      <span className="px-2 text-sm text-slate-500 dark:text-slate-400">
                        Página {page + 1} de {list.data.totalPages}
                      </span>
                    </PaginationItem>
                    <PaginationItem>
                      <PaginationNext
                        text="Seguinte"
                        href="#"
                        aria-disabled={page + 1 >= list.data.totalPages}
                        className={
                          page + 1 >= list.data.totalPages ? "pointer-events-none opacity-50" : undefined
                        }
                        onClick={(e) => {
                          e.preventDefault();
                          if (page + 1 >= list.data.totalPages) return;
                          setPage((p) => p + 1);
                        }}
                      />
                    </PaginationItem>
                  </PaginationContent>
                </Pagination>
              ) : null}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function NotificacaoRow({
  notificacao,
  onMarcarLida,
  isMarking,
}: {
  notificacao: Notificacao;
  onMarcarLida: (id: string) => void;
  isMarking: boolean;
}) {
  const { id, categoria, titulo, mensagem, linkUrl, lida, createdAt, snoozedUntil } = notificacao;
  const isInternalLink = isInternalLinkUrl(linkUrl);
  const isSnoozed = Boolean(snoozedUntil) && new Date(snoozedUntil as string) > new Date();
  const titleClassName = lida
    ? "text-sm font-normal text-slate-900 dark:text-slate-100"
    : "text-sm font-semibold text-slate-900 dark:text-slate-100";

  const handleTitleClick = () => {
    if (!lida) onMarcarLida(id);
  };

  return (
    <div className="p-4 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-1 min-w-0">
          {!lida && <span className="h-2 w-2 rounded-full bg-blue-600 flex-shrink-0" aria-hidden="true" />}
          {isInternalLink ? (
            <Link
              href={linkUrl as string}
              onClick={handleTitleClick}
              className={`${titleClassName} hover:underline`}
            >
              {titulo}
            </Link>
          ) : (
            <span className={titleClassName}>{titulo}</span>
          )}
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <Badge variant={categoriaToBadgeVariant(categoria)}>{categoriaToLabel(categoria)}</Badge>
          {isSnoozed && (
            <Badge variant="gray">
              Adiado até{" "}
              {new Date(snoozedUntil as string).toLocaleDateString("pt-CV", {
                day: "2-digit",
                month: "short",
              })}
            </Badge>
          )}
          {!lida && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="h-8 w-8 p-0"
              aria-label="Marcar como lida"
              title="Marcar como lida"
              disabled={isMarking}
              onClick={() => onMarcarLida(id)}
            >
              <Check className="h-4 w-4" />
            </Button>
          )}
          <NotificacaoSnoozeControl notificacao={notificacao} />
        </div>
      </div>
      <p className="text-sm text-slate-600 dark:text-slate-300">{mensagem}</p>
      <p className="text-[11px] text-slate-500 dark:text-slate-400">
        {new Date(createdAt).toLocaleString("pt-CV", {
          day: "2-digit",
          month: "short",
          year: "numeric",
          hour: "2-digit",
          minute: "2-digit",
        })}
      </p>
    </div>
  );
}
