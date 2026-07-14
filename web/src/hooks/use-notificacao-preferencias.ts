import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { NotificacaoCategoria, NotificacaoPreferenciasResponse } from "@/types/notificacoes";

export function useNotificacaoPreferencias() {
  return useQuery({
    queryKey: ["notificacao-preferencias"],
    queryFn: () => apiFetch<NotificacaoPreferenciasResponse>("/notificacoes/preferencias"),
    enabled: typeof window !== "undefined",
    staleTime: 30_000,
  });
}

export function useSilenciarCategoria() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (categoria: NotificacaoCategoria) =>
      apiFetch(`/notificacoes/preferencias/${encodeURIComponent(categoria)}`, {
        method: "PUT",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["notificacao-preferencias"] });
    },
  });
}

export function useReativarCategoria() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (categoria: NotificacaoCategoria) =>
      apiFetch(`/notificacoes/preferencias/${encodeURIComponent(categoria)}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["notificacao-preferencias"] });
    },
  });
}
