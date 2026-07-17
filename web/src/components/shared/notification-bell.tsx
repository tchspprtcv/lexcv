"use client";

import { Bell, Check, CheckCheck } from "lucide-react";
import Link from "next/link";
import * as React from "react";

import { NotificacaoSnoozeControl } from "@/components/shared/notificacao-snooze-control";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { toast } from "@/hooks/use-toast";
import {
  useMarcarNotificacaoLida,
  useMarcarTodasNotificacoesLidas,
  useNotificacoes,
  useNotificacoesUnreadCount,
} from "@/hooks/use-notificacoes";
import { cn } from "@/lib/utils";
import {
  categoriaToBadgeVariant,
  categoriaToLabel,
  isInternalLinkUrl,
} from "@/lib/notificacao-categoria";
import type { Notificacao } from "@/types/notificacoes";

function NotificacaoConteudo({ n }: { n: Notificacao }) {
  return (
    <>
      <div className="flex items-start justify-between gap-2">
        <p
          className={`text-sm truncate text-slate-900 dark:text-slate-100 ${
            n.lida ? "font-normal" : "font-semibold"
          }`}
        >
          {n.titulo}
        </p>
        <Badge variant={categoriaToBadgeVariant(n.categoria)} className="flex-shrink-0">
          {categoriaToLabel(n.categoria)}
        </Badge>
      </div>
      <p className="text-xs text-slate-500 dark:text-slate-400 truncate">{n.mensagem}</p>
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {new Date(n.createdAt).toLocaleDateString("pt-CV", { day: "2-digit", month: "short" })}
      </p>
    </>
  );
}

export function NotificationBell() {
  const [open, setOpen] = React.useState(false);
  const unread = useNotificacoesUnreadCount();
  const count = unread.data?.count ?? 0;
  const showBadge = !unread.isLoading && (unread.isError || count > 0);

  // Over-fetch a larger page than what's displayed (10) so that a handful of
  // currently-snoozed items within the fetched window don't starve the
  // visible list down to zero while the (server-side-filtered) unread badge
  // count still shows non-zero — see 96-REVIEW.md WR-01.
  const list = useNotificacoes({ size: 20 }, { poll: true });
  const marcarLida = useMarcarNotificacaoLida();
  const marcarTodas = useMarcarTodasNotificacoesLidas();

  // Display-only filter: hide currently-snoozed items from the bell preview
  // without altering the useNotificacoes call/query (the history page keeps
  // showing every item, snoozed or not — locked decision in 96-CONTEXT.md).
  const visibleNotificacoes = (list.data?.content ?? [])
    .filter((n) => !(n.snoozedUntil && new Date(n.snoozedUntil) > new Date()))
    .slice(0, 10);

  const handleMarcarTodas = async () => {
    try {
      await marcarTodas.mutateAsync();
      toast.success("Todas as notificações foram marcadas como lidas.");
    } catch {
      // Erro já reportado pelo toast automático do apiFetch.
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          className="h-9 w-9 p-0 rounded-full text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors relative"
        >
          <Bell className="h-[1.1rem] w-[1.1rem]" />
          {showBadge && (
            <Badge
              className={cn(
                "absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full p-0 flex items-center justify-center text-[10px] font-bold leading-none text-white border-transparent",
                unread.isError ? "bg-slate-400 dark:bg-slate-400" : "bg-red-500 dark:bg-red-500",
              )}
            >
              {unread.isError ? "!" : count > 9 ? "9+" : count}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="end">
        <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-2">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">Notificações</p>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={count === 0 || marcarTodas.isPending}
            onClick={handleMarcarTodas}
          >
            <CheckCheck />
            {marcarTodas.isPending ? "A marcar..." : "Marcar todas como lidas"}
          </Button>
        </div>
        {list.isPending ? (
          <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">
            A carregar...
          </p>
        ) : list.isError ? (
          <p className="px-4 py-6 text-sm text-red-600 text-center">
            Não foi possível carregar as notificações. Verifique a ligação e tente novamente.
          </p>
        ) : !visibleNotificacoes.length ? (
          <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">
            Sem notificações por agora.
          </p>
        ) : (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800 max-h-72 overflow-y-auto">
            {visibleNotificacoes.map((n) => (
              <li
                key={n.id}
                className="px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-900 transition-colors"
              >
                <div className="flex items-start gap-2">
                  <div className="flex-1 min-w-0">
                    {isInternalLinkUrl(n.linkUrl) ? (
                      <Link
                        href={n.linkUrl}
                        onClick={() => {
                          marcarLida.mutate(n.id);
                          setOpen(false);
                        }}
                      >
                        <NotificacaoConteudo n={n} />
                      </Link>
                    ) : (
                      <NotificacaoConteudo n={n} />
                    )}
                  </div>
                  <div className="flex-shrink-0 flex items-center gap-1">
                    {!isInternalLinkUrl(n.linkUrl) && !n.lida && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-label="Marcar como lida"
                        disabled={marcarLida.isPending && marcarLida.variables === n.id}
                        onClick={() => marcarLida.mutate(n.id)}
                      >
                        <Check />
                      </Button>
                    )}
                    <NotificacaoSnoozeControl notificacao={n} />
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
        <div className="px-4 py-2 border-t border-slate-200 dark:border-slate-800">
          <Link
            href="/notificacoes"
            className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium"
            onClick={() => setOpen(false)}
          >
            Ver todas as notificações
          </Link>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export default NotificationBell;
