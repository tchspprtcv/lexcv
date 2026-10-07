import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";

import { apiFetch, apiFetchFicheiro } from "@/lib/api";
import { guardarFicheiro } from "@/lib/guardar-ficheiro";
import type {
  DescargaPdfResposta,
  DocumentoFiscalDetalhe,
  PaginaDocumentosFiscais,
} from "@/types/faturacao";

export const MINHAS_SUBSCRICOES_KEY = ["faturacao", "minhas-subscricoes"] as const;

export function useMinhasSubscricoes({
  page = 0,
  size = 10,
}: {
  page?: number;
  size?: number;
} = {}) {
  const queryParams = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  return useQuery({
    queryKey: [...MINHAS_SUBSCRICOES_KEY, { page, size }] as const,
    queryFn: () => apiFetch<PaginaDocumentosFiscais>(`/faturacao/subscricoes?${queryParams.toString()}`),
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}

export function useMinhaSubscricaoDetalhe(id: string | null, enabled = true) {
  return useQuery({
    queryKey: [...MINHAS_SUBSCRICOES_KEY, "detail", id] as const,
    queryFn: () => apiFetch<DocumentoFiscalDetalhe>(`/faturacao/subscricoes/${encodeURIComponent(id!)}`),
    enabled: enabled && Boolean(id) && typeof window !== "undefined",
    staleTime: 30_000,
  });
}

export function useDescarregarMinhaSubscricaoPdf() {
  return useMutation({
    mutationFn: async ({ documentoId }: { documentoId: string }) => {
      const res = await apiFetch<DescargaPdfResposta>(
        `/faturacao/subscricoes/${encodeURIComponent(documentoId)}/pdf`,
        {},
        { semToastParaStatus: [404, 503] },
      );
      if (typeof window !== "undefined" && res.url) {
        window.location.assign(res.url);
      }
      return res;
    },
  });
}

export function useDescarregarMinhaSubscricaoXml() {
  return useMutation({
    mutationFn: async ({
      documentoId,
      numeroFormatado,
    }: {
      documentoId: string;
      numeroFormatado: string;
    }) => {
      const res = await apiFetchFicheiro(
        `/faturacao/subscricoes/${encodeURIComponent(documentoId)}/xml`,
        {},
        { semToastParaStatus: [404, 503] },
      );
      const fallback = `${numeroFormatado.replace(/[/ ]/g, "-")}.xml`;
      guardarFicheiro(res.blob, res.nomeFicheiro || fallback);
      return res;
    },
  });
}
