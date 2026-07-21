import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { ResultadoPesquisa } from "@/types/search";

export function useGlobalSearch(termo: string) {
  const trimmed = termo.trim();
  const enabled = typeof window !== "undefined" && trimmed.length >= 2;

  return useQuery({
    queryKey: ["pesquisa", trimmed],
    queryFn: () =>
      apiFetch<ResultadoPesquisa[]>(`/pesquisa?q=${encodeURIComponent(trimmed)}`),
    enabled,
    staleTime: 30_000,
  });
}
