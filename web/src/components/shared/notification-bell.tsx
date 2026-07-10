"use client";

import { Bell, Check, CheckCheck } from "lucide-react";
import Link from "next/link";

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
  const unread = useNotificacoesUnreadCount();
  const count = unread.data?.count ?? 0;
  const showBadge = !unread.isLoading && (unread.isError || count > 0);

  const list = useNotificacoes({ size: 10 }, { poll: true });
  const marcarLida = useMarcarNotificacaoLida();
  const marcarTodas = useMarcarTodasNotificacoesLidas();

  const handleMarcarTodas = async () => {
    try {
      await marcarTodas.mutateAsync();
      toast.success("Todas as notificações foram marcadas como lidas.");
    } catch {
      // Erro já reportado pelo toast automático do apiFetch.
    }
  };

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          className="h-9 w-9 p-0 rounded-full text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors relative"
        >
          <Bell className="h-[1.1rem] w-[1.1rem]" />
          {showBadge && (
            <span
              className={`absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full text-[10px] font-bold text-white flex items-center justify-center leading-none ${
                unread.isError ? "bg-slate-400" : "bg-red-500"
              }`}
            >
              {unread.isError ? "!" : count > 9 ? "9+" : count}
            </span>
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
        ) : !list.data?.content.length ? (
          <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">
            Sem notificações por agora.
          </p>
        ) : (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800 max-h-72 overflow-y-auto">
            {list.data!.content.slice(0, 10).map((n) => (
              <li
                key={n.id}
                className="px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-900 transition-colors"
              >
                {isInternalLinkUrl(n.linkUrl) ? (
                  <Link href={n.linkUrl} className="block" onClick={() => marcarLida.mutate(n.id)}>
                    <NotificacaoConteudo n={n} />
                  </Link>
                ) : (
                  <div className="flex items-start gap-2">
                    <div className="flex-1 min-w-0">
                      <NotificacaoConteudo n={n} />
                    </div>
                    {!n.lida && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="flex-shrink-0"
                        aria-label="Marcar como lida"
                        disabled={marcarLida.isPending && marcarLida.variables === n.id}
                        onClick={() => marcarLida.mutate(n.id)}
                      >
                        <Check />
                      </Button>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="px-4 py-2 border-t border-slate-200 dark:border-slate-800">
          <Link
            href="/notificacoes"
            className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium"
          >
            Ver todas as notificações
          </Link>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export default NotificationBell;
