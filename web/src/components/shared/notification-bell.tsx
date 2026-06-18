"use client";

import { Bell } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useUpcomingEventos } from "@/hooks/use-eventos";
import type { UpcomingEvento } from "@/types/eventos";

function NotificationItem({ ev }: { ev: UpcomingEvento }) {
  return (
    <>
      <p className="text-sm font-medium text-slate-900 dark:text-slate-100 truncate">{ev.titulo}</p>
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {new Date(ev.dataInicio).toLocaleDateString("pt-PT", { day: "2-digit", month: "short" })}
        {ev.tipo ? ` · ${ev.tipo}` : ""}
      </p>
    </>
  );
}

export function NotificationBell() {
  const { data, isLoading } = useUpcomingEventos();
  const count = data?.length ?? 0;
  const showBadge = !isLoading && count > 0;

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
            <span className="absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full bg-red-500 text-[10px] font-bold text-white flex items-center justify-center leading-none">
              {count > 9 ? "9+" : count}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="end">
        <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">Próximos eventos</p>
        </div>
        {count === 0 ? (
          <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">
            Sem eventos nos próximos 7 dias
          </p>
        ) : (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800 max-h-72 overflow-y-auto">
            {data!.slice(0, 10).map((ev) => (
              <li key={ev.id} className="px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-900 transition-colors">
                {ev.processoId ? (
                  <Link href={`/processos/${ev.processoId}`} className="block">
                    <NotificationItem ev={ev} />
                  </Link>
                ) : (
                  <NotificationItem ev={ev} />
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="px-4 py-2 border-t border-slate-200 dark:border-slate-800">
          <Link href="/agenda" className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium">
            Ver agenda
          </Link>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export default NotificationBell;
