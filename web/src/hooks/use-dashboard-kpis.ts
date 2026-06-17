import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { DashboardKpis } from "@/types/dashboard";

export function useDashboardKpis() {
  const enabled = typeof window !== "undefined" ;

  return useQuery({
    queryKey: ["dashboard", "kpis"],
    queryFn: () => apiFetch<DashboardKpis>("/dashboard"),
    enabled,
    staleTime: 30_000,
  });
}
