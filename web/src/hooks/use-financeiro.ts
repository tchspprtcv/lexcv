import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { DOCUMENTOS_FISCAIS_KEY, ESTADO_EMISSAO_KEY } from "@/hooks/use-faturacao";
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

  // Phase 134 (D-14): os 409 (pagamentos registados, HONORARIO_COM_DOCUMENTOS_FISCAIS) são
  // mostrados inline no diálogo; invalidar em onSettled porque uma recusa também indica dados
  // desatualizados no ecrã (133 WR-04).
  return useMutation({
    mutationFn: (id: number) =>
      apiFetch<void>(`/honorarios/${id}`, { method: "DELETE" }, { semToastParaStatus: [409] }),
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["honorarios", "list"] });
    },
  });
}

export function useDeletePagamento() {
  const queryClient = useQueryClient();

  // Phase 134 (D-14): 409 PAGAMENTO_FATURADO é mostrado inline; invalidação em onSettled.
  return useMutation({
    mutationFn: ({ pagamentoId }: { pagamentoId: number; honorarioId: number }) =>
      apiFetch<void>(`/pagamentos/${pagamentoId}`, { method: "DELETE" }, { semToastParaStatus: [409] }),
    onSettled: async (_void, _error, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["honorarios", "pagamentos", variables.honorarioId] }),
        queryClient.invalidateQueries({ queryKey: ["clientes", "conta-corrente"] }),
      ]);
    },
  });
}

export function useCreatePagamento() {
  const queryClient = useQueryClient();

  // Phase 134 (D-10, 133 WR-04): com a faturação ativa, os 409/422 da emissão são tratados inline
  // (interpretarErroEmissao); a invalidação corre em onSettled porque um erro também pode
  // significar estado desatualizado (faturação desligada entretanto, documento já emitido com a
  // mesma chave). O payload continua a ser PagamentoCreateRequest: a chamada com a faturação
  // desligada não muda.
  return useMutation({
    mutationFn: (payload: PagamentoCreateRequest) =>
      apiFetch<Pagamento>(
        "/pagamentos",
        {
          method: "POST",
          body: JSON.stringify(payload satisfies PagamentoCreateRequest),
        },
        { semToastParaStatus: [409, 422] },
      ),
    onSettled: async (_created, _error, variables) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["honorarios", "pagamentos", variables.honorarioId] }),
        queryClient.invalidateQueries({ queryKey: ["honorarios", "detail", variables.honorarioId] }),
        queryClient.invalidateQueries({ queryKey: ["honorarios", "list"] }),
        queryClient.invalidateQueries({ queryKey: ["clientes", "conta-corrente"] }),
        queryClient.invalidateQueries({ queryKey: DOCUMENTOS_FISCAIS_KEY }),
        queryClient.invalidateQueries({ queryKey: ESTADO_EMISSAO_KEY }),
      ]);
    },
  });
}
