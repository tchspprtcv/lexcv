import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch, API_BASE } from "@/lib/api";

import type {
  Documento,
  DocumentoUploadPayload,
  DocumentoUploadResponse,
  DocumentosListFilters,
} from "@/types/documentos";

/**
 * GET /documentos espera `processoId`/`clienteId`, como o upload — os nomes em
 * snake_case que aqui estavam vinham da API mock e o backend ignorava-os, pelo
 * que escolher um processo no formulário não alterava a lista.
 */
function buildDocumentosSearch(filters: DocumentosListFilters) {
  const sp = new URLSearchParams();
  if (filters.processo_id?.trim()) sp.set("processoId", filters.processo_id.trim());
  if (filters.cliente_id?.trim()) sp.set("clienteId", filters.cliente_id.trim());
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function useDocumentos(filters: DocumentosListFilters) {
  const enabled = typeof window !== "undefined" ;
  const processoId = filters.processo_id?.trim() ?? "";
  const clienteId = filters.cliente_id?.trim() ?? "";

  return useQuery({
    queryKey: ["documentos", "list", processoId, clienteId],
    // Um único endpoint para os quatro casos. Antes, cada filtro era encaminhado
    // para a sua rota dedicada (/clientes/{id}/documentos, /processos/{id}/documentos),
    // o que funcionava isoladamente mas ignorava o processo quando ambos estavam
    // preenchidos: a primeira condição ganhava e devolvia os documentos do cliente
    // inteiro, em vez da interseção.
    queryFn: () =>
      apiFetch<Documento[]>(
        `/documentos${buildDocumentosSearch({ processo_id: processoId, cliente_id: clienteId })}`,
      ),
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
      if (payload.processo_id?.trim()) form.set("processoId", payload.processo_id.trim());
      if (payload.cliente_id?.trim()) form.set("clienteId", payload.cliente_id.trim());

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

export function useDownloadDocumento(id: string) {
  return useMutation({
    mutationFn: () =>
      apiFetch<{ url: string; expiresIn: number }>(
        `/documentos/${encodeURIComponent(id)}/download`,
      ),
  });
}

export function useUploadDocumentoComProgresso(options?: { onProgress?: (pct: number) => void }) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: DocumentoUploadPayload) =>
      new Promise<DocumentoUploadResponse>((resolve, reject) => {
        const form = new FormData();
        form.set("file", payload.file);
        if (payload.nome?.trim()) form.set("nome", payload.nome.trim());
        if (payload.tipo?.trim()) form.set("tipo", payload.tipo.trim());
        if (payload.confidencialidade?.trim()) form.set("confidencialidade", payload.confidencialidade.trim());
        if (payload.replace_id?.trim()) form.set("replace_id", payload.replace_id.trim());
        if (payload.processo_id?.trim()) form.set("processoId", payload.processo_id.trim());
        if (payload.cliente_id?.trim()) form.set("clienteId", payload.cliente_id.trim());

        const xhr = new XMLHttpRequest();
        xhr.open("POST", `${API_BASE}/documentos/upload`);
        xhr.withCredentials = true;

        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            options?.onProgress?.(Math.round((e.loaded / e.total) * 100));
          }
        };

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              resolve(JSON.parse(xhr.responseText) as DocumentoUploadResponse);
            } catch {
              reject(new Error("Resposta inválida do servidor"));
            }
          } else {
            let errorMessage = xhr.statusText || "Falha na comunicação com o servidor.";
            try {
              if (xhr.responseText) {
                const json = JSON.parse(xhr.responseText);
                if (json && typeof json === "object") {
                  errorMessage = json.message || json.error || errorMessage;
                }
              }
            } catch {
              // Not a JSON object, use fallback message
            }
            reject(new Error(`API ${xhr.status}: ${errorMessage}`));
          }
        };

        xhr.onerror = () => reject(new Error("Erro de rede ao enviar ficheiro"));

        xhr.send(form);
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["documentos", "list"] });
    },
  });
}
