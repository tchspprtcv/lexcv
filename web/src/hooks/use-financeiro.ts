import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type {
  Honorario,
  HonorarioCreateRequest,
  HonorarioUpdateRequest,
  Pagamento,
  PagamentoCreateRequest,
} from "@/types/financeiro";

export function useHonorarios(args?: { processoId?: string }) {
  const enabled = typeof window !== "undefined" ;
  const processoId = args?.processoId?.trim() ?? "";

  return useQuery({
    queryKey: ["honorarios", "list", processoId],
    queryFn: () =>
      apiFetch<Honorario[]>(
        `/honorarios${processoId ? `?processo_id=${encodeURIComponent(processoId)}` : ""}`,
      ),
    enabled,
    staleTime: 15_000,
  });
}

export function useHonorario(id: number) {
  const enabled = typeof window !== "undefined"  && Number.isFinite(id);

  return useQuery({
    queryKey: ["honorarios", "detail", id],
    queryFn: () => apiFetch<Honorario>(`/honorarios/${encodeURIComponent(String(id))}`),
    enabled,
    staleTime: 15_000,
  });
}

export function useHonorarioPagamentos(honorarioId: number) {
  const enabled =
    typeof window !== "undefined"  && Number.isFinite(honorarioId);

  return useQuery({
    queryKey: ["honorarios", "pagamentos", honorarioId],
    queryFn: () =>
      apiFetch<Pagamento[]>(
        `/honorarios/${encodeURIComponent(String(honorarioId))}/pagamentos`,
      ),
    enabled,
    staleTime: 10_000,
  });
}

export function useCreateHonorario() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: HonorarioCreateRequest) =>
      apiFetch<Honorario>("/honorarios", {
        method: "POST",
        body: JSON.stringify(payload satisfies HonorarioCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["honorarios", "list"] });
    },
  });
}

export function useUpdateHonorario() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: HonorarioUpdateRequest }) =>
      apiFetch<Honorario>(`/honorarios/${id}`, {
        method: "PUT",
        body: JSON.stringify(data satisfies HonorarioUpdateRequest),
      }),
    onSuccess: async (_updated, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["honorarios", "list"] }),
        queryClient.invalidateQueries({ queryKey: ["honorarios", "detail", variables.id] }),
      ]);
    },
  });
}

export function useDeleteHonorario() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) =>
      apiFetch<void>(`/honorarios/${id}`, { method: "DELETE" }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["honorarios", "list"] });
    },
  });
}

export function useDeletePagamento() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ pagamentoId }: { pagamentoId: number; honorarioId: number }) =>
      apiFetch<void>(`/pagamentos/${pagamentoId}`, { method: "DELETE" }),
    onSuccess: async (_void, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["honorarios", "pagamentos", variables.honorarioId] }),
        queryClient.invalidateQueries({ queryKey: ["clientes", "conta-corrente"] }),
      ]);
    },
  });
}

export function useCreatePagamento() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PagamentoCreateRequest) =>
      apiFetch<Pagamento>("/pagamentos", {
        method: "POST",
        body: JSON.stringify(payload satisfies PagamentoCreateRequest),
      }),
    onSuccess: async (_created, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["honorarios", "pagamentos", variables.honorarioId] }),
        queryClient.invalidateQueries({ queryKey: ["clientes", "conta-corrente"] }),
      ]);
    },
  });
}
