import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type {
  Notificacao,
  NotificacoesListFilters,
  NotificacoesPageResponse,
} from "@/types/notificacoes";

function buildNotificacoesSearch(filters: NotificacoesListFilters): string {
  const params = new URLSearchParams();
  if (filters.categoria) params.set("categoria", filters.categoria);
  if (filters.lida !== undefined) params.set("lida", String(filters.lida));
  if (filters.page !== undefined) params.set("page", String(filters.page));
  if (filters.size !== undefined) params.set("size", String(filters.size));
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export function useNotificacoes(
  filters: NotificacoesListFilters = {},
  opts: { poll?: boolean } = {},
) {
  return useQuery({
    queryKey: [
      "notificacoes",
      "list",
      filters.categoria ?? "",
      filters.lida ?? null,
      filters.page ?? 0,
      filters.size ?? 20,
    ],
    queryFn: () =>
      apiFetch<NotificacoesPageResponse>(`/notificacoes${buildNotificacoesSearch(filters)}`),
    enabled: typeof window !== "undefined",
    staleTime: 30_000,
    ...(opts.poll === true ? { refetchInterval: 30_000, refetchOnWindowFocus: true } : {}),
  });
}

export function useNotificacoesUnreadCount() {
  return useQuery({
    queryKey: ["notificacoes", "unread-count"],
    queryFn: () => apiFetch<{ count: number }>("/notificacoes/unread-count"),
    enabled: typeof window !== "undefined",
    staleTime: 30_000,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  });
}

export function useMarcarNotificacaoLida() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<Notificacao>(`/notificacoes/${encodeURIComponent(id)}/lida`, {
        method: "PATCH",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["notificacoes"] });
    },
  });
}

export function useMarcarTodasNotificacoesLidas() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<{ marcadas: number }>("/notificacoes/ler-todas", {
        method: "POST",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["notificacoes"] });
    },
  });
}
