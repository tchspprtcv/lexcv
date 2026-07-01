import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { ParecerPrioridade, ParecerSolicitacao, ParecerVersao } from "@/types/pareceres";

export type ParecerSolicitacoesListFilters = {
  clienteId?: string;
  advogadoId?: string;
  status?: string;
};

function buildParecerSearch(filters: ParecerSolicitacoesListFilters) {
  const sp = new URLSearchParams();
  if (filters.clienteId?.trim()) sp.set("clienteId", filters.clienteId.trim());
  if (filters.advogadoId?.trim()) sp.set("advogadoId", filters.advogadoId.trim());
  if (filters.status?.trim()) sp.set("status", filters.status.trim());
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function usePareceres(filters: ParecerSolicitacoesListFilters = {}) {
  const enabled = typeof window !== "undefined";
  const clienteId = filters.clienteId?.trim() ?? "";
  const advogadoId = filters.advogadoId?.trim() ?? "";
  const status = filters.status?.trim() ?? "";

  return useQuery({
    queryKey: ["pareceres", "list", clienteId, advogadoId, status],
    queryFn: () =>
      apiFetch<ParecerSolicitacao[]>(
        `/pareceres/solicitacoes${buildParecerSearch({ clienteId, advogadoId, status })}`,
      ),
    enabled,
    staleTime: 30_000,
  });
}

export function useParecer(id: string) {
  const enabled = typeof window !== "undefined" && Boolean(id);

  return useQuery({
    queryKey: ["pareceres", "detail", id],
    queryFn: () => apiFetch<ParecerSolicitacao>(`/pareceres/solicitacoes/${encodeURIComponent(id)}`),
    enabled,
    staleTime: 30_000,
  });
}

export function useParecerVersoes(solicitacaoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(solicitacaoId);

  return useQuery({
    queryKey: ["pareceres", "versoes", solicitacaoId],
    queryFn: () =>
      apiFetch<ParecerVersao[]>(`/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/versoes`),
    enabled,
    staleTime: 15_000,
  });
}

export type ParecerCreateRequest = {
  clienteId: string;
  processoId?: string;
  descricao: string;
  prazo?: string;
  prioridade?: ParecerPrioridade;
  advogadoId?: string;
};

export function useCreateParecer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ParecerCreateRequest) =>
      apiFetch<ParecerSolicitacao>("/pareceres/solicitacoes", {
        method: "POST",
        body: JSON.stringify(payload satisfies ParecerCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] });
    },
  });
}

export function useDownloadParecerAnexo(solicitacaoId: string, versaoId: string) {
  return useMutation({
    mutationFn: () =>
      apiFetch<{ url: string; expiresIn: number }>(
        `/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/versoes/${encodeURIComponent(versaoId)}/anexo`,
      ),
  });
}
