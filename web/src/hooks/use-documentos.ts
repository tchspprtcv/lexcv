import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, API_BASE } from "@/lib/api";

import type {
  Documento,
  DocumentoUploadPayload,
  DocumentoUploadResponse,
  DocumentosListFilters,
} from "@/types/documentos";

function buildDocumentosSearch(filters: DocumentosListFilters) {
  const sp = new URLSearchParams();
  if (filters.processo_id?.trim()) sp.set("processo_id", filters.processo_id.trim());
  if (filters.cliente_id?.trim()) sp.set("cliente_id", filters.cliente_id.trim());
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function useDocumentos(filters: DocumentosListFilters) {
  const enabled = typeof window !== "undefined" ;
  const processoId = filters.processo_id?.trim() ?? "";
  const clienteId = filters.cliente_id?.trim() ?? "";

  return useQuery({
    queryKey: ["documentos", "list", processoId, clienteId],
    queryFn: () =>
      apiFetch<Documento[]>(`/documentos${buildDocumentosSearch({ processo_id: processoId, cliente_id: clienteId })}`),
    enabled,
    staleTime: 30_000,
  });
}

export function useDocumento(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["documentos", "detail", id],
    queryFn: () => apiFetch<Documento>(`/documentos/${encodeURIComponent(id)}`),
    enabled,
    staleTime: 30_000,
  });
}

export function useUploadDocumento() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: DocumentoUploadPayload) => {
      const form = new FormData();
      form.set("file", payload.file);
      if (payload.nome?.trim()) form.set("nome", payload.nome.trim());
      if (payload.tipo?.trim()) form.set("tipo", payload.tipo.trim());
      if (payload.confidencialidade?.trim()) form.set("confidencialidade", payload.confidencialidade.trim());
      if (payload.replace_id?.trim()) form.set("replace_id", payload.replace_id.trim());
      if (payload.processo_id?.trim()) form.set("processo_id", payload.processo_id.trim());
      if (payload.cliente_id?.trim()) form.set("cliente_id", payload.cliente_id.trim());

      return apiFetch<DocumentoUploadResponse>("/documentos/upload", {
        method: "POST",
        body: form,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["documentos", "list"] });
    },
  });
}

export function useDeleteDocumento(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<void>(`/documentos/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["documentos", "list"] }),
        queryClient.removeQueries({ queryKey: ["documentos", "detail", id] }),
      ]);
    },
  });
}

function parseContentDispositionFilename(headerValue: string | null): string | null {
  if (!headerValue) return null;
  const utf8Match = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(headerValue);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }
  const match = /filename\s*=\s*"([^"]+)"/i.exec(headerValue) ?? /filename\s*=\s*([^;]+)/i.exec(headerValue);
  return match?.[1]?.trim() ?? null;
}

export function useDownloadDocumento(id: string) {
  return useMutation({
    mutationFn: async () => {
      const res = await fetch(
        `${API_BASE}/documentos/${encodeURIComponent(id)}/download`,
        {
          credentials: "include",
        },
      );

      if (!res.ok) {
        const body = await res.text().catch(() => "");
        throw new Error(`API ${res.status}: ${body || res.statusText}`);
      }

      const blob = await res.blob();
      const filename =
        parseContentDispositionFilename(res.headers.get("content-disposition")) ?? `documento-${id}`;
      const contentType = res.headers.get("content-type") ?? blob.type ?? "application/octet-stream";

      return { blob, filename, contentType };
    },
  });
}
