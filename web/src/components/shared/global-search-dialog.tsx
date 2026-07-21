"use client";

import { FileText, Scale, ScrollText, Search, Users, type LucideIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import * as React from "react";

import {
  Command,
  CommandDialog,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { useGlobalSearch } from "@/hooks/use-global-search";
import { usePermissions } from "@/hooks/use-permissions";
import { highlightMatch } from "@/lib/highlight-match";
import { isInternalLinkUrl } from "@/lib/notificacao-categoria";
import { pushRecent, readRecents } from "@/lib/search-recents";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import type { PesquisaResultadoTipo, ResultadoPesquisa } from "@/types/search";

/**
 * Window event name the topbar triggers (Plan 112-03) dispatch to open this
 * dialog without prop-drilling — mirrors the self-contained, mounted-once
 * shape already established by `NotificationBell`.
 */
export const SEARCH_OPEN_EVENT = "lexcv:search-open";

type TipoMeta = {
  icon: LucideIcon;
  grupoLabel: string;
  verTodosLabel: string;
  segment: string;
  scope: string;
};

// Locked order (112-CONTEXT.md): same as the sidebar NAV — Clientes, Processos, Documentos, Pareceres.
const TIPO_ORDER: PesquisaResultadoTipo[] = ["cliente", "processo", "documento", "parecer"];

const TIPO_META: Record<PesquisaResultadoTipo, TipoMeta> = {
  cliente: {
    icon: Users,
    grupoLabel: "Clientes",
    verTodosLabel: "Ver todos os Clientes",
    segment: "clientes",
    scope: "clientes",
  },
  processo: {
    icon: Scale,
    grupoLabel: "Processos",
    verTodosLabel: "Ver todos os Processos",
    segment: "processos",
    scope: "processos",
  },
  documento: {
    icon: FileText,
    grupoLabel: "Documentos",
    verTodosLabel: "Ver todos os Documentos",
    segment: "documentos",
    scope: "documentos",
  },
  parecer: {
    icon: ScrollText,
    grupoLabel: "Pareceres",
    verTodosLabel: "Ver todos os Pareceres",
    segment: "pareceres",
    scope: "pareceres",
  },
};

function ResultRow({
  icon: Icon,
  titulo,
  subtitulo,
}: {
  icon: LucideIcon;
  titulo: React.ReactNode;
  subtitulo?: React.ReactNode;
}) {
  return (
    <>
      <Icon />
      <div className="flex min-w-0 flex-col">
        <span className="truncate">{titulo}</span>
        {subtitulo ? (
          <span className="truncate text-xs text-muted-foreground">{subtitulo}</span>
        ) : null}
      </div>
    </>
  );
}

export function GlobalSearchDialog() {
  const router = useRouter();
  const permissions = usePermissions();

  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState("");
  const debouncedQuery = useDebouncedValue(query, 300);
  const search = useGlobalSearch(debouncedQuery);

  // Global Ctrl+K / ⌘K toggle — mounted once, self-contained (same shape as NotificationBell's own state).
  React.useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && (event.key === "k" || event.key === "K")) {
        event.preventDefault();
        setOpen((current) => !current);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  // Same-origin open-event — lets the topbar triggers (Plan 112-03) open this dialog without prop-drilling.
  React.useEffect(() => {
    function onSearchOpen() {
      setOpen(true);
    }
    window.addEventListener(SEARCH_OPEN_EVENT, onSearchOpen);
    return () => window.removeEventListener(SEARCH_OPEN_EVENT, onSearchOpen);
  }, []);

  // Reset the query whenever the dialog closes, so reopening starts fresh (no stale-result
  // flash). Handled here (not a useEffect) so setState isn't called synchronously from an
  // effect body — react-hooks/set-state-in-effect flags that pattern.
  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      setQuery("");
    }
  }

  function navigate(rota: string) {
    // T-112-04 mitigation: server-provided `rota` must resolve as an internal path before navigating.
    if (!isInternalLinkUrl(rota)) return;
    router.push(rota);
    setOpen(false);
  }

  function onSelectResult(resultado: ResultadoPesquisa) {
    pushRecent(resultado);
    navigate(resultado.rota);
  }

  function onSelectVerTodos(segment: string) {
    navigate(`/${segment}?q=${encodeURIComponent(query.trim())}`);
  }

  const termo = debouncedQuery.trim();
  const hasQuery = termo.length >= 2;
  const resultados = search.data ?? [];
  // Read directly during render (no effect/memo needed) — sessionStorage reads are cheap for a
  // 5-item cap, and this guarantees the list always reflects the latest recents on every open.
  const recents = readRecents();

  return (
    <CommandDialog
      open={open}
      onOpenChange={handleOpenChange}
      title="Pesquisa global"
      description="Pesquisar clientes, processos, documentos e pareceres da sua instituição"
    >
      <Command shouldFilter={false}>
        <CommandInput
          placeholder="Pesquisar clientes, processos, documentos ou pareceres..."
          value={query}
          onValueChange={setQuery}
        />
        <CommandList>
          {!hasQuery ? (
            recents.length > 0 ? (
              <CommandGroup heading="Visitados recentemente">
                {recents.map((recente) => (
                  <CommandItem
                    key={`recent:${recente.tipo}:${recente.id}`}
                    value={`recent:${recente.tipo}:${recente.id}`}
                    onSelect={() => onSelectResult(recente)}
                  >
                    <ResultRow
                      icon={TIPO_META[recente.tipo].icon}
                      titulo={recente.titulo}
                      subtitulo={recente.subtitulo}
                    />
                  </CommandItem>
                ))}
              </CommandGroup>
            ) : (
              <Empty>
                <EmptyHeader>
                  <EmptyMedia variant="icon">
                    <Search />
                  </EmptyMedia>
                  <EmptyTitle>Comece a escrever para pesquisar</EmptyTitle>
                  <EmptyDescription>
                    Encontre Clientes, Processos, Documentos e Pareceres da sua instituição por
                    nome, número ou NIF.
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            )
          ) : search.isFetching ? (
            // T-112-07 mitigation: display-only skeleton gate — never the real RBAC boundary
            // (that stays 100% server-side, Phase 111). Must gate on isFetched, never !isLoading.
            TIPO_ORDER.filter(
              (tipo) => permissions.isFetched && permissions.can.view(TIPO_META[tipo].scope),
            ).map((tipo) => (
              <CommandGroup key={tipo} heading={TIPO_META[tipo].grupoLabel}>
                {Array.from({ length: 3 }).map((_, index) => (
                  <div key={index} className="px-2 py-1.5">
                    <Skeleton className="h-8 w-full rounded-md" />
                  </div>
                ))}
              </CommandGroup>
            ))
          ) : search.isError ? (
            <p className="px-4 py-6 text-center text-sm text-red-600">
              Não foi possível pesquisar. Verifique a ligação e tente novamente.
            </p>
          ) : resultados.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Search />
                </EmptyMedia>
                <EmptyTitle>Sem resultados</EmptyTitle>
                <EmptyDescription>
                  Não foram encontrados registos para “{termo}”. Tente outro termo.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            // The Command root's filtering is disabled above (T-112-06), which already stops
            // cmdk from re-ranking this list — group by tipo in TIPO_ORDER, preserving the
            // backend's own within-type order (no client re-sort).
            TIPO_ORDER.map((tipo) => {
              const items = resultados.filter((resultado) => resultado.tipo === tipo);
              if (items.length === 0) return null;
              const meta = TIPO_META[tipo];
              return (
                <CommandGroup key={tipo} heading={meta.grupoLabel}>
                  {items.map((resultado) => (
                    <CommandItem
                      key={`${resultado.tipo}:${resultado.id}`}
                      value={`${resultado.tipo}:${resultado.id}`}
                      onSelect={() => onSelectResult(resultado)}
                    >
                      <ResultRow
                        icon={meta.icon}
                        titulo={highlightMatch(resultado.titulo, query)}
                        subtitulo={
                          resultado.subtitulo
                            ? highlightMatch(resultado.subtitulo, query)
                            : undefined
                        }
                      />
                    </CommandItem>
                  ))}
                  <CommandItem
                    value={`vertodos:${tipo}`}
                    onSelect={() => onSelectVerTodos(meta.segment)}
                    className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium"
                  >
                    {meta.verTodosLabel}
                  </CommandItem>
                </CommandGroup>
              );
            })
          )}
        </CommandList>
      </Command>
    </CommandDialog>
  );
}
