import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { apiFetch, apiFetchFicheiro } from "@/lib/api";
import { guardarFicheiro } from "@/lib/guardar-ficheiro";

import type {
  DescargaPdfResposta,
  DocumentoFiscalDetalhe,
  PaginaDocumentosFiscais,
  SerieFiscal,
} from "@/types/faturacao";
import type {
  CriarNotaCreditoSubscricaoRequest,
  PlatformConfiguracaoFiscal,
  PlatformConfiguracaoFiscalPayload,
  PlatformDocumentoFiscalFiltros,
  RegistarPagamentoSubscricaoRequest,
  SubscricaoFaturaResponse,
} from "@/types/platform-faturacao";

export const PLATFORM_FATURACAO_CONFIG_KEY = ["platform", "faturacao", "configuracao"] as const;
export const PLATFORM_FATURACAO_SERIES_KEY = ["platform", "faturacao", "series"] as const;
export const PLATFORM_DOCUMENTOS_FISCAIS_KEY = ["platform", "documentos-fiscais"] as const;

export function usePlatformConfiguracaoFiscal(enabled = true) {
  return useQuery({
    queryKey: PLATFORM_FATURACAO_CONFIG_KEY,
    queryFn: () => apiFetch<PlatformConfiguracaoFiscal>("/platform/faturacao/configuracao"),
    enabled: enabled && typeof window !== "undefined",
    staleTime: 30_000,
  });
}

export function usePlatformSeries(enabled = true) {
  return useQuery({
    queryKey: PLATFORM_FATURACAO_SERIES_KEY,
    queryFn: () => apiFetch<SerieFiscal[]>("/platform/faturacao/series"),
    enabled: enabled && typeof window !== "undefined",
    staleTime: 30_000,
  });
}

export function useUpdatePlatformConfiguracaoFiscal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PlatformConfiguracaoFiscalPayload) =>
      apiFetch<PlatformConfiguracaoFiscal>("/platform/faturacao/configuracao", {
        method: "PUT",
        body: JSON.stringify(payload),
      }),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: PLATFORM_FATURACAO_CONFIG_KEY }),
        queryClient.invalidateQueries({ queryKey: PLATFORM_FATURACAO_SERIES_KEY }),
      ]);
    },
  });
}

export function usePlatformDocumentos(filtros: PlatformDocumentoFiscalFiltros = {}) {
  const queryParams = new URLSearchParams();
  if (filtros.tipo) queryParams.set("tipo", filtros.tipo);
  if (filtros.estado) queryParams.set("estado", filtros.estado);
  if (filtros.de) queryParams.set("de", filtros.de);
  if (filtros.ate) queryParams.set("ate", filtros.ate);
  if (filtros.page !== undefined) queryParams.set("page", String(filtros.page));
  if (filtros.size !== undefined) queryParams.set("size", String(filtros.size));

  const queryStr = queryParams.toString();
  const url = `/platform/documentos-fiscais${queryStr ? `?${queryStr}` : ""}`;

  return useQuery({
    queryKey: [...PLATFORM_DOCUMENTOS_FISCAIS_KEY, "list", filtros] as const,
    queryFn: () => apiFetch<PaginaDocumentosFiscais>(url),
    placeholderData: keepPreviousData,
    staleTime: 10_000,
  });
}

export function usePlatformDocumentoDetalhe(id: string | null, enabled = true) {
  return useQuery({
    queryKey: [...PLATFORM_DOCUMENTOS_FISCAIS_KEY, "detail", id] as const,
    queryFn: () => apiFetch<DocumentoFiscalDetalhe>(`/platform/documentos-fiscais/${encodeURIComponent(id!)}`),
    enabled: enabled && Boolean(id) && typeof window !== "undefined",
    staleTime: 30_000,
  });
}

export function useRegistarPagamentoSubscricao() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: RegistarPagamentoSubscricaoRequest) =>
      apiFetch<SubscricaoFaturaResponse>("/platform/subscricoes/pagamentos", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: PLATFORM_DOCUMENTOS_FISCAIS_KEY }),
        queryClient.invalidateQueries({ queryKey: PLATFORM_FATURACAO_SERIES_KEY }),
      ]);
    },
  });
}

export function useEmitirNotaCreditoSubscricao(documentoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CriarNotaCreditoSubscricaoRequest) =>
      apiFetch<SubscricaoFaturaResponse>(
        `/platform/documentos-fiscais/${encodeURIComponent(documentoId)}/notas-credito`,
        {
          method: "POST",
          body: JSON.stringify(payload),
        },
      ),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: PLATFORM_DOCUMENTOS_FISCAIS_KEY }),
        queryClient.invalidateQueries({ queryKey: PLATFORM_FATURACAO_SERIES_KEY }),
      ]);
    },
  });
}

export function useDescarregarPlatformPdf() {
  return useMutation({
    mutationFn: async ({ documentoId }: { documentoId: string }) => {
      const res = await apiFetch<DescargaPdfResposta>(
        `/platform/documentos-fiscais/${encodeURIComponent(documentoId)}/pdf`,
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

export function useDescarregarPlatformXml() {
  return useMutation({
    mutationFn: async ({
      documentoId,
      numeroFormatado,
    }: {
      documentoId: string;
      numeroFormatado: string;
    }) => {
      const res = await apiFetchFicheiro(
        `/platform/documentos-fiscais/${encodeURIComponent(documentoId)}/xml`,
        {},
        { semToastParaStatus: [404, 503] },
      );
      const fallback = `${numeroFormatado.replace(/[/ ]/g, "-")}.xml`;
      guardarFicheiro(res.blob, res.nomeFicheiro || fallback);
      return res;
    },
  });
}
