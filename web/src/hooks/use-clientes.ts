import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type {
  Cliente,
  ClienteAdvogadoUser,
  ClienteContaCorrenteResponse,
  ClienteCreateRequest,
  ClientesListFilters,
  ClienteUpdateRequest,
} from "@/types/clientes";

function buildClientesSearch(filters: ClientesListFilters) {
  const sp = new URLSearchParams();
  if (filters.q?.trim()) sp.set("q", filters.q.trim());
  if (filters.nome?.trim()) sp.set("nome", filters.nome.trim());
  if (filters.nif?.trim()) sp.set("nif", filters.nif.trim());
  if (filters.tipo?.trim()) sp.set("tipo", filters.tipo.trim());
  if (filters.ativo !== undefined) sp.set("ativo", String(filters.ativo));
  if (filters.localidade?.trim()) sp.set("localidade", filters.localidade.trim());
  if (filters.createdFrom?.trim()) sp.set("createdFrom", filters.createdFrom.trim());
  if (filters.createdTo?.trim()) sp.set("createdTo", filters.createdTo.trim());
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}

export function useClientes(filters: ClientesListFilters) {
  const enabled = typeof window !== "undefined" ;
  const q = filters.q?.trim() ?? "";
  const nome = filters.nome?.trim() ?? "";
  const nif = filters.nif?.trim() ?? "";
  const tipo = filters.tipo?.trim() ?? "";
  const ativo = filters.ativo;
  const localidade = filters.localidade?.trim() ?? "";
  const createdFrom = filters.createdFrom?.trim() ?? "";
  const createdTo = filters.createdTo?.trim() ?? "";

  return useQuery({
    queryKey: ["clientes", "list", q, nome, nif, tipo, ativo, localidade, createdFrom, createdTo],
    queryFn: () =>
      apiFetch<Cliente[]>(
        `/clientes${buildClientesSearch({ q, nome, nif, tipo, ativo, localidade, createdFrom, createdTo })}`,
      ),
    enabled,
    staleTime: 30_000,
  });
}

export function useCliente(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["clientes", "detail", id],
    queryFn: () => apiFetch<Cliente>(`/clientes/${encodeURIComponent(id)}`),
    enabled,
    staleTime: 30_000,
  });
}

export function useClienteContaCorrente(id: string) {
  const enabled = typeof window !== "undefined"  && Boolean(id);

  return useQuery({
    queryKey: ["clientes", "conta-corrente", id],
    queryFn: () =>
      apiFetch<ClienteContaCorrenteResponse>(`/clientes/${encodeURIComponent(id)}/conta-corrente`),
    enabled,
    staleTime: 15_000,
  });
}

export function useCreateCliente() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ClienteCreateRequest) =>
      apiFetch<Cliente>("/clientes", {
        method: "POST",
        body: JSON.stringify(payload satisfies ClienteCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "list"] });
    },
  });
}

export function useUpdateCliente(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ClienteUpdateRequest) =>
      apiFetch<Cliente>(`/clientes/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify(payload satisfies ClienteUpdateRequest),
      }),
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["clientes", "list"] }),
        queryClient.setQueryData(["clientes", "detail", id], updated),
      ]);
    },
  });
}

export function useDeleteCliente(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<void>(`/clientes/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["clientes", "list"] }),
        queryClient.removeQueries({ queryKey: ["clientes", "detail", id] }),
        queryClient.removeQueries({ queryKey: ["clientes", "conta-corrente", id] }),
      ]);
    },
  });
}

export function useMergeClientes() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { primaryId: string; secondaryId: string }) =>
      apiFetch<{
        primary_id: string;
        moved_processos: number;
        moved_contactos: number;
        moved_notas: number;
      }>("/clientes/merge", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes"] });
    },
  });
}

export function useUploadProcuracao(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (formData: FormData) =>
      apiFetch<Cliente>(`/clientes/${encodeURIComponent(clienteId)}/procuracao`, {
        method: "POST",
        body: formData,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "detail", clienteId] });
    },
  });
}

export function useDownloadProcuracao() {
  return useMutation({
    mutationFn: (clienteId: string) =>
      apiFetch<{ url: string; expiresIn: number }>(
        `/clientes/${encodeURIComponent(clienteId)}/procuracao/download`,
      ),
  });
}

export function useDeleteProcuracao(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<void>(`/clientes/${encodeURIComponent(clienteId)}/procuracao`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "detail", clienteId] });
    },
  });
}

export function useClienteAdvogados(clienteId: string) {
  const enabled = typeof window !== "undefined" && Boolean(clienteId);

  return useQuery({
    queryKey: ["clientes", clienteId, "advogados"],
    queryFn: () =>
      apiFetch<ClienteAdvogadoUser[]>(`/clientes/${encodeURIComponent(clienteId)}/advogados`),
    enabled,
    staleTime: 30_000,
  });
}

export function useAddAdvogado(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (userId: string) =>
      apiFetch<void>(
        `/clientes/${encodeURIComponent(clienteId)}/advogados/${encodeURIComponent(userId)}`,
        { method: "POST" },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", clienteId, "advogados"] });
    },
  });
}

export function useRemoveAdvogado(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (userId: string) =>
      apiFetch<void>(
        `/clientes/${encodeURIComponent(clienteId)}/advogados/${encodeURIComponent(userId)}`,
        { method: "DELETE" },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", clienteId, "advogados"] });
    },
  });
}

export function useClienteAdministrativos(clienteId: string) {
  const enabled = typeof window !== "undefined" && Boolean(clienteId);

  return useQuery({
    queryKey: ["clientes", clienteId, "administrativos"],
    queryFn: () =>
      apiFetch<ClienteAdvogadoUser[]>(`/clientes/${encodeURIComponent(clienteId)}/administrativos`),
    enabled,
    staleTime: 30_000,
  });
}

export function useAddAdministrativo(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (userId: string) =>
      apiFetch<void>(
        `/clientes/${encodeURIComponent(clienteId)}/administrativos/${encodeURIComponent(userId)}`,
        { method: "POST" },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", clienteId, "administrativos"] });
    },
  });
}

export function useRemoveAdministrativo(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (userId: string) =>
      apiFetch<void>(
        `/clientes/${encodeURIComponent(clienteId)}/administrativos/${encodeURIComponent(userId)}`,
        { method: "DELETE" },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", clienteId, "administrativos"] });
    },
  });
}
