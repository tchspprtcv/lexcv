"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Users, Scale, Calendar, FileText } from "lucide-react";
import { hasPermission } from "@/lib/permissions";
import { cn } from "@/lib/utils";

const BOTTOM_NAV = [
  { href: "/dashboard", label: "Dashboard", icon: Home, requiredPermission: undefined },
  { href: "/clientes", label: "Clientes", icon: Users, requiredPermission: "clientes:view" },
  { href: "/processos", label: "Processos", icon: Scale, requiredPermission: "processos:view" },
  { href: "/agenda", label: "Agenda", icon: Calendar, requiredPermission: "agenda:view" },
  { href: "/documentos", label: "Documentos", icon: FileText, requiredPermission: "documentos:view" },
];

export function BottomNav({ permissions }: { permissions?: string[] }) {
  const pathname = usePathname();

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-50 flex md:hidden bg-slate-950 dark:bg-[#04091a] border-t border-slate-800/60">
      {BOTTOM_NAV.filter((item) => hasPermission(permissions ?? [], item.requiredPermission)).map((item) => {
        const active = pathname === item.href || (item.href === "/processos" && pathname.startsWith("/processos/dashboard"));
        const Icon = item.icon;
        return (
          <Link
            key={item.href}
            href={item.href}
            className={cn(
              "flex flex-col items-center gap-0.5 flex-1 py-2 px-1 text-xs font-medium transition-colors",
              active ? "text-blue-400" : "text-slate-400"
            )}
          >
            <Icon className={cn("h-5 w-5", active ? "text-blue-500" : "text-slate-500")} />
            <span className="text-[10px] leading-tight truncate">{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
