import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { ClienteNota, ClienteNotaCreateRequest, ClienteNotaUpdateRequest } from "@/types/clientes-notas";

export function useClienteNotas(clienteId: string) {
  const enabled = typeof window !== "undefined" && Boolean(clienteId);

  return useQuery({
    queryKey: ["clientes", "notas", clienteId],
    queryFn: () => apiFetch<ClienteNota[]>(`/clientes/${encodeURIComponent(clienteId)}/notas`),
    enabled,
    staleTime: 15_000,
  });
}

export function useCreateClienteNota(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ClienteNotaCreateRequest) =>
      apiFetch<ClienteNota>(`/clientes/${encodeURIComponent(clienteId)}/notas`, {
        method: "POST",
        body: JSON.stringify(payload satisfies ClienteNotaCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "notas", clienteId] });
    },
  });
}

export function useUpdateClienteNota(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: { notaId: string; payload: ClienteNotaUpdateRequest }) =>
      apiFetch<ClienteNota>(
        `/clientes/${encodeURIComponent(clienteId)}/notas/${encodeURIComponent(variables.notaId)}`,
        {
          method: "PUT",
          body: JSON.stringify(variables.payload satisfies ClienteNotaUpdateRequest),
        },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "notas", clienteId] });
    },
  });
}

export function useDeleteClienteNota(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (notaId: string) =>
      apiFetch<void>(
        `/clientes/${encodeURIComponent(clienteId)}/notas/${encodeURIComponent(notaId)}`,
        {
          method: "DELETE",
        },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "notas", clienteId] });
    },
  });
}

