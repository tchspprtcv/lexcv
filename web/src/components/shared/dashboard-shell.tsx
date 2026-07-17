"use client";

import { NotificationBell } from "@/components/shared/notification-bell";
import { SidebarNav, type NavItem } from "@/components/shared/sidebar-nav";
import { UserMenu } from "@/components/shared/user-menu";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import {
  Building2,
  Calendar,
  FileText,
  Home,
  Menu,
  Scale,
  ScrollText,
  Search,
  Users,
  Wallet,
} from "lucide-react";

import { Sheet, SheetContent } from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { clearTokens } from "@/lib/auth";
import { useMe } from "@/hooks/use-me";

import { ThemeToggle } from "@/components/theme-toggle";
import { BottomNav } from "@/components/shared/bottom-nav";

const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: Home },
  { href: "/clientes", label: "Clientes", icon: Users, requiredPermission: "clientes:view" },
  { href: "/processos", label: "Processos", icon: Scale, requiredPermission: "processos:view" },
  { href: "/agenda", label: "Agenda", icon: Calendar, requiredPermission: "agenda:view" },
  { href: "/documentos", label: "Documentos", icon: FileText, requiredPermission: "documentos:view" },
  { href: "/financeiro", label: "Financeiro", icon: Wallet, requiredPermission: "financeiro:view" },
  { href: "/pareceres", label: "Pareceres", icon: ScrollText, requiredPermission: "pareceres:view" },
];

export function DashboardShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const me = useMe();
  const [drawerOpen, setDrawerOpen] = React.useState(false);

  React.useEffect(() => {
    if (me.isError) {
      const currentPath = pathname + (searchParams?.toString() ? `?${searchParams.toString()}` : "");
      router.replace(`/login?returnUrl=${encodeURIComponent(currentPath)}`);
    }
  }, [router, pathname, searchParams, me.isError]);

  React.useEffect(() => {
    setDrawerOpen(false);
  }, [pathname]);

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
      <aside className="hidden md:flex w-[270px] flex-shrink-0 text-white bg-slate-950 dark:bg-[#04091a] border-r border-slate-900/50 dark:border-slate-800/50 flex-col z-20">
        <div className="px-5 pt-6 pb-4">
          <div className="text-[11px] font-bold tracking-widest text-slate-400">LEXCV <span className="text-slate-600 font-normal">| INSTITUCIONAL</span></div>
        </div>

        <SidebarNav nav={NAV} pathname={pathname} permissions={me.data?.permissions} />

        <div className="mt-auto p-4">
          <div className="flex items-center gap-3 rounded-lg bg-slate-900/50 dark:bg-slate-900/30 px-3 py-3 border border-slate-800/50">
            <UserMenu variant="sidebar" me={me.data} onLogout={onLogout} />
          </div>
        </div>
      </aside>

      <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
        <SheetContent side="left" className="w-[270px] p-0 bg-slate-950 dark:bg-[#04091a] border-r border-slate-900/50 dark:border-slate-800/50 text-white flex flex-col">
          <div className="px-5 pt-6 pb-4">
            <div className="text-[11px] font-bold tracking-widest text-slate-400">LEXCV <span className="text-slate-600 font-normal">| INSTITUCIONAL</span></div>
          </div>

          <SidebarNav nav={NAV} pathname={pathname} permissions={me.data?.permissions} />

          <div className="mt-auto p-4">
            <div className="flex items-center gap-3 rounded-lg bg-slate-900/50 dark:bg-slate-900/30 px-3 py-3 border border-slate-800/50">
              <UserMenu variant="sidebar" me={me.data} onLogout={onLogout} />
            </div>
          </div>
        </SheetContent>
      </Sheet>

      <main className="flex-1 min-w-0 flex flex-col">
        <header className="h-16 bg-white/80 dark:bg-[#020617]/80 backdrop-blur-md border-b border-slate-200 dark:border-slate-800/60 flex items-center px-8 gap-6 sticky top-0 z-10 transition-colors duration-300">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            className="md:hidden flex items-center justify-center h-9 w-9 rounded-md text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            aria-label="Abrir menu"
          >
            <Menu className="h-5 w-5" />
          </button>

          <div className="hidden md:flex flex-1 max-w-md relative group">
            <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 transition-colors group-focus-within:text-blue-500" />
            <Input
              placeholder="Pesquisar processos, entidades..."
              className="pl-9 bg-slate-100/50 dark:bg-slate-900/50 border-slate-200 dark:border-slate-800 focus-visible:ring-1 focus-visible:ring-blue-500/50 rounded-full text-sm h-9 shadow-sm transition-all"
            />
          </div>

          <div className="text-[13px] font-medium text-slate-500 dark:text-slate-400 hidden md:flex items-center gap-2">
            {me.data?.tenant_logo_data_url ? (
              <img src={me.data.tenant_logo_data_url} alt="" className="h-5 w-5 object-contain rounded-sm" />
            ) : (
              <Building2 className="h-4 w-4 text-slate-400" />
            )}
            {me.data?.tenant_nome ?? "LexCV"}
          </div>

          <div className="flex-1 flex items-center justify-center gap-2 md:hidden text-[13px] font-medium text-slate-700 dark:text-slate-300 truncate">
            {me.data?.tenant_logo_data_url ? (
              <img src={me.data.tenant_logo_data_url} alt="" className="h-5 w-5 object-contain rounded-sm flex-shrink-0" />
            ) : (
              <Building2 className="h-4 w-4 text-slate-400 flex-shrink-0" />
            )}
            <span className="truncate">{me.data?.tenant_nome ?? "LexCV"}</span>
          </div>

          <div className="ml-auto flex items-center gap-3">
            <ThemeToggle />
            <NotificationBell />
            <div className="h-5 w-px bg-slate-200 dark:bg-slate-800 mx-1"></div>
            <UserMenu variant="topbar" me={me.data} onLogout={onLogout} />
          </div>
        </header>

        <div className="flex-1 overflow-auto p-8 pb-24 md:pb-8">{children}</div>
        <BottomNav permissions={me.data?.permissions} />
      </main>
    </div>
  );
}
