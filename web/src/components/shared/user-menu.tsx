"use client";

import Link from "next/link";
import { LogOut, Settings } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

import type { MeResponse } from "@/types/auth";

interface UserMenuProps {
  variant: "topbar" | "sidebar";
  me: MeResponse | undefined;
  onLogout: () => void;
}

/**
 * Shared user menu (avatar + name/role) that opens a DropdownMenu with
 * Perfil / Configurações / separator / Terminar sessão. Consumed at 3 call
 * sites in DashboardShell (topbar header, desktop sidebar footer, mobile
 * Sheet footer) — the trigger visuals differ by `variant`, but the menu
 * content is identical across all 3.
 */
export function UserMenu({ variant, me, onLogout }: UserMenuProps) {
  const initials = (me?.nome ?? "U")
    .split(" ")
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        {variant === "topbar" ? (
          <button
            type="button"
            aria-label="Menu do utilizador"
            className="flex items-center gap-3 pl-1 outline-none rounded-md focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
          >
            <div className="text-right leading-tight hidden sm:block">
              <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">{me?.nome ?? "—"}</div>
              <div className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">{(me?.roles?.[0] ?? "—").toString()}</div>
            </div>
            <div className="h-9 w-9 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center text-xs font-bold text-white shadow-sm ring-2 ring-white dark:ring-[#020617] overflow-hidden hover:opacity-90 transition-opacity">
              {me?.avatar_url ? (
                <img src={me.avatar_url} alt="Avatar" className="w-full h-full object-cover" />
              ) : (
                initials
              )}
            </div>
          </button>
        ) : (
          <button
            type="button"
            aria-label="Menu do utilizador"
            className="flex items-center gap-3 flex-1 min-w-0 text-left outline-none rounded-md focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
          >
            <div className="h-9 w-9 rounded-md bg-slate-800 flex items-center justify-center text-xs font-bold text-slate-300 overflow-hidden hover:opacity-80 transition-opacity">
              {me?.avatar_url ? (
                <img src={me.avatar_url} alt="Avatar" className="w-full h-full object-cover" />
              ) : (
                initials
              )}
            </div>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium truncate text-slate-200">{me?.nome ?? "—"}</div>
              <div className="text-[10px] text-slate-500 truncate uppercase tracking-wider mt-0.5">
                {(me?.roles?.[0] ?? "—").toString()}
              </div>
            </div>
          </button>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-56" align={variant === "topbar" ? "end" : "start"}>
        <DropdownMenuItem asChild>
          <Link href="/profile">Perfil</Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/settings">
            <Settings className="mr-2 h-4 w-4" />
            Configurações
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="default" onSelect={onLogout}>
          <LogOut className="mr-2 h-4 w-4" />
          Terminar sessão
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
