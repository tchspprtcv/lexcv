"use client";

import Link from "next/link";
import { NotificationBell } from "@/components/shared/notification-bell";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import {
  Calendar,
  FileText,
  Home,
  LifeBuoy,
  LogOut,
  Scale,
  Search,
  Settings,
  Users,
  Wallet,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { clearTokens } from "@/lib/auth";
import { hasPermission } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { useMe } from "@/hooks/use-me";

import { ThemeToggle } from "@/components/theme-toggle";

type NavItem = {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  requiredPermission?: string;
};

const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: Home },
  { href: "/clientes", label: "Clientes", icon: Users, requiredPermission: "clientes:view" },
  { href: "/processos", label: "Processos", icon: Scale, requiredPermission: "processos:view" },
  { href: "/agenda", label: "Agenda", icon: Calendar, requiredPermission: "agenda:view" },
  { href: "/documentos", label: "Documentos", icon: FileText, requiredPermission: "documentos:view" },
  { href: "/financeiro", label: "Financeiro", icon: Wallet, requiredPermission: "financeiro:view" },
];

export function DashboardShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const me = useMe();

  React.useEffect(() => {
    if (me.isError) {
      const currentPath = pathname + (searchParams?.toString() ? `?${searchParams.toString()}` : "");
      router.replace(`/login?returnUrl=${encodeURIComponent(currentPath)}`);
    }
  }, [router, pathname, searchParams, me.isError]);

  const onLogout = async () => {
    await clearTokens();
    
    // Invalidamos a cache do React Query para forçar a verificação de estado e limpar dados
    await import("@tanstack/react-query");
    // Mas não podemos chamar hook dentro de função callback. 
    // Em vez disso fazemos um hard reload ou só o replace que já vai forçar no auth check
    window.location.href = "/login";
  };

  return (
    <div className="h-screen w-full flex overflow-hidden bg-slate-50 dark:bg-[#020617] transition-colors duration-300">
      <aside className="w-[270px] flex-shrink-0 text-white bg-slate-950 dark:bg-[#04091a] border-r border-slate-900/50 dark:border-slate-800/50 flex flex-col z-20">
        <div className="px-5 pt-6 pb-4">
          <div className="text-[11px] font-bold tracking-widest text-slate-400">LEXCV <span className="text-slate-600 font-normal">| INSTITUCIONAL</span></div>
        </div>

        <nav className="px-3 space-y-1 mt-4">
          {NAV.filter((item) => hasPermission(me.data?.permissions, item.requiredPermission)).map((item) => {
            const active = pathname === item.href || (item.href === "/processos" && pathname.startsWith("/processos/dashboard"));
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-all duration-200",
                  active
                    ? "bg-blue-600/10 text-blue-400 dark:bg-blue-500/10 dark:text-blue-400 shadow-[inset_2px_0_0_0_theme(colors.blue.500)]"
                    : "text-slate-400 hover:bg-slate-900 hover:text-slate-200 dark:hover:bg-slate-900/50",
                )}
              >
                <Icon className={cn("h-4 w-4", active ? "text-blue-500" : "text-slate-500")} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="mt-8 px-5">
          <div className="text-[10px] font-semibold tracking-wider text-slate-500 uppercase mb-3">Sistema</div>
          <div className="space-y-1">
            <Link
              href="/settings"
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-all duration-200",
                pathname === "/settings"
                  ? "bg-blue-600/10 text-blue-400 dark:bg-blue-500/10 dark:text-blue-400 shadow-[inset_2px_0_0_0_theme(colors.blue.500)]"
                  : "text-slate-400 hover:bg-slate-900 hover:text-slate-200 dark:hover:bg-slate-900/50"
              )}
            >
              <Settings className={cn("h-4 w-4", pathname === "/settings" ? "text-blue-500" : "text-slate-500")} />
              Configurações
            </Link>
            <Link
              href="#"
              className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-slate-400 hover:bg-slate-900 hover:text-slate-200 transition-colors"
            >
              <LifeBuoy className="h-4 w-4 text-slate-500" />
              Suporte
            </Link>
          </div>
        </div>

        <div className="mt-auto p-4">
          <div className="flex items-center gap-3 rounded-lg bg-slate-900/50 dark:bg-slate-900/30 px-3 py-3 border border-slate-800/50">
            <Link href="/settings" className="h-9 w-9 rounded-md bg-slate-800 flex items-center justify-center text-xs font-bold text-slate-300 overflow-hidden hover:opacity-80 transition-opacity">
              {me.data?.avatar_url ? (
                <img src={me.data.avatar_url} alt="Avatar" className="w-full h-full object-cover" />
              ) : (
                (me.data?.nome ?? "U")
                  .split(" ")
                  .slice(0, 2)
                  .map((p) => p[0])
                  .join("")
                  .toUpperCase()
              )}
            </Link>
            <div className="min-w-0 flex-1">
              <Link href="/settings" className="text-sm font-medium truncate text-slate-200 hover:underline">{me.data?.nome ?? "—"}</Link>
              <div className="text-[10px] text-slate-500 truncate uppercase tracking-wider mt-0.5">
                {(me.data?.roles?.[0] ?? "—").toString()}
              </div>
            </div>
            <Button type="button" variant="ghost" className="h-8 w-8 p-0 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors" onClick={onLogout}>
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </aside>

      <main className="flex-1 min-w-0 flex flex-col">
        <header className="h-16 bg-white/80 dark:bg-[#020617]/80 backdrop-blur-md border-b border-slate-200 dark:border-slate-800/60 flex items-center px-8 gap-6 sticky top-0 z-10 transition-colors duration-300">
          <div className="flex-1 max-w-md relative group">
            <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 transition-colors group-focus-within:text-blue-500" />
            <Input
              placeholder="Pesquisar processos, entidades..."
              className="pl-9 bg-slate-100/50 dark:bg-slate-900/50 border-slate-200 dark:border-slate-800 focus-visible:ring-1 focus-visible:ring-blue-500/50 rounded-full text-sm h-9 shadow-sm transition-all"
            />
          </div>
          
          <div className="text-[13px] font-medium text-slate-500 dark:text-slate-400 hidden md:flex items-center gap-2">
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-500"></div>
            Tribunal da Comarca da Praia
          </div>
          
          <div className="ml-auto flex items-center gap-3">
            <ThemeToggle />
            <NotificationBell />
            <div className="h-5 w-px bg-slate-200 dark:bg-slate-800 mx-1"></div>
            <div className="flex items-center gap-3 pl-1">
              <div className="text-right leading-tight hidden sm:block">
                <Link href="/settings" className="text-sm font-semibold text-slate-900 dark:text-slate-100 hover:underline">{me.data?.nome ?? "—"}</Link>
                <div className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">{(me.data?.roles?.[0] ?? "—").toString()}</div>
              </div>
              <Link href="/settings" className="h-9 w-9 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center text-xs font-bold text-white shadow-sm ring-2 ring-white dark:ring-[#020617] overflow-hidden hover:opacity-90 transition-opacity">
                {me.data?.avatar_url ? (
                  <img src={me.data.avatar_url} alt="Avatar" className="w-full h-full object-cover" />
                ) : (
                  (me.data?.nome ?? "U")
                    .split(" ")
                    .slice(0, 2)
                    .map((p) => p[0])
                    .join("")
                    .toUpperCase()
                )}
              </Link>
            </div>
          </div>
        </header>

        <div className="flex-1 overflow-auto p-8">{children}</div>
      </main>
    </div>
  );
}
