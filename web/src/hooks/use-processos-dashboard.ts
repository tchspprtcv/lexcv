import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { ProcessosDashboardData } from "@/types/dashboard";

export function useProcessosDashboard() {
  const enabled = typeof window !== "undefined";

  return useQuery({
    queryKey: ["processos", "dashboard"],
    queryFn: async () => {
      const response = await apiFetch<ProcessosDashboardData>(`/processos/dashboard`);
      return response;
    },
    enabled,
    staleTime: 60_000,
  });
}
