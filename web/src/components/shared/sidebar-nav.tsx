"use client";

import Link from "next/link";
import { LifeBuoy, Settings } from "lucide-react";
import * as React from "react";

import { hasPermission } from "@/lib/permissions";
import { cn } from "@/lib/utils";

export type NavItem = {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  requiredPermission?: string;
};

interface SidebarNavProps {
  nav: NavItem[];
  pathname: string;
  permissions: string[] | undefined;
}

/**
 * Shared sidebar navigation (primary NAV links + "Sistema" Configurações/Suporte
 * links) consumed at 2 call sites in DashboardShell (desktop `<aside>` and mobile
 * `<Sheet>`) — the markup and active-state logic are identical across both.
 */
export function SidebarNav({ nav, pathname, permissions }: SidebarNavProps) {
  return (
    <>
      <nav className="px-3 space-y-1 mt-4">
        {nav.filter((item) => hasPermission(permissions, item.requiredPermission)).map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
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
    </>
  );
}

export default SidebarNav;
