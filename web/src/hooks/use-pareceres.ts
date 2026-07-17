import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, API_BASE } from "@/lib/api";

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

export type ParecerPesquisaFilters = {
  texto?: string;
  clienteId?: string;
  advogadoId?: string;
  status?: string;
  dataInicio?: string;
  dataFim?: string;
};

function buildParecerPesquisaSearch(filters: ParecerPesquisaFilters) {
  const sp = new URLSearchParams();
  if (filters.texto?.trim()) sp.set("texto", filters.texto.trim());
  if (filters.clienteId?.trim()) sp.set("clienteId", filters.clienteId.trim());
  if (filters.advogadoId?.trim()) sp.set("advogadoId", filters.advogadoId.trim());
  if (filters.status?.trim()) sp.set("status", filters.status.trim());
  if (filters.dataInicio?.trim()) sp.set("dataInicio", `${filters.dataInicio.trim()}T00:00:00`);
  if (filters.dataFim?.trim()) sp.set("dataFim", `${filters.dataFim.trim()}T23:59:59`);
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function usePesquisarPareceres(
  filters: ParecerPesquisaFilters = {},
  options: { enabled?: boolean } = {},
) {
  const enabled = typeof window !== "undefined" && (options.enabled ?? true);
  const texto = filters.texto?.trim() ?? "";
  const clienteId = filters.clienteId?.trim() ?? "";
  const advogadoId = filters.advogadoId?.trim() ?? "";
  const status = filters.status?.trim() ?? "";
  const dataInicio = filters.dataInicio?.trim() ?? "";
  const dataFim = filters.dataFim?.trim() ?? "";

  return useQuery({
    queryKey: ["pareceres", "pesquisa", texto, clienteId, advogadoId, status, dataInicio, dataFim],
    queryFn: () =>
      apiFetch<ParecerSolicitacao[]>(
        `/pareceres/pesquisa${buildParecerPesquisaSearch({ texto, clienteId, advogadoId, status, dataInicio, dataFim })}`,
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
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "pesquisa"] }),
      ]);
    },
  });
}

export type ParecerVersaoCreatePayload = {
  conteudo: string;
  file: File;
};

export function useCreateParecerVersao(
  solicitacaoId: string,
  options?: { onProgress?: (pct: number) => void },
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ParecerVersaoCreatePayload) =>
      new Promise<ParecerVersao>((resolve, reject) => {
        const form = new FormData();
        form.set("conteudo", payload.conteudo);
        form.set("file", payload.file);

        const xhr = new XMLHttpRequest();
        xhr.open(
          "POST",
          `${API_BASE}/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/versoes`,
        );
        xhr.withCredentials = true;
        xhr.timeout = 60_000;

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            options?.onProgress?.(Math.round((e.loaded / e.total) * 100));
          }
        };

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              resolve(JSON.parse(xhr.responseText) as ParecerVersao);
            } catch {
              reject(new Error("Resposta inválida do servidor"));
            }
          } else {
            reject(new Error(`API ${xhr.status}`));
          }
        };

        xhr.onerror = () => reject(new Error("Erro de rede ao enviar ficheiro"));
        xhr.ontimeout = () =>
          reject(new Error("Tempo limite excedido ao enviar o ficheiro. Tente novamente."));

        xhr.send(form);
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["pareceres", "versoes", solicitacaoId] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "detail", solicitacaoId] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "pesquisa"] }),
      ]);
    },
  });
}

export function useEntregarParecer(solicitacaoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { versaoFinalId: string }) =>
      apiFetch<ParecerSolicitacao>(
        `/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/entregar?versaoFinalId=${encodeURIComponent(payload.versaoFinalId)}`,
        { method: "PUT" },
      ),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["pareceres", "detail", solicitacaoId] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "list"] }),
        queryClient.invalidateQueries({ queryKey: ["pareceres", "pesquisa"] }),
      ]);
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
