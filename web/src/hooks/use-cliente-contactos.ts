import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type {
  ClienteContacto,
  ClienteContactoCreateRequest,
  ClienteContactoUpdateRequest,
} from "@/types/clientes-contactos";

export function useClienteContactos(clienteId: string) {
  const enabled = typeof window !== "undefined" && Boolean(clienteId);

  return useQuery({
    queryKey: ["clientes", "contactos", clienteId],
    queryFn: () => apiFetch<ClienteContacto[]>(`/clientes/${encodeURIComponent(clienteId)}/contactos`),
    enabled,
    staleTime: 15_000,
  });
}

export function useCreateClienteContacto(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ClienteContactoCreateRequest) =>
      apiFetch<ClienteContacto>(`/clientes/${encodeURIComponent(clienteId)}/contactos`, {
        method: "POST",
        body: JSON.stringify(payload satisfies ClienteContactoCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "contactos", clienteId] });
    },
  });
}

export function useUpdateClienteContacto(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: { contactoId: string; payload: ClienteContactoUpdateRequest }) =>
      apiFetch<ClienteContacto>(
        `/clientes/${encodeURIComponent(clienteId)}/contactos/${encodeURIComponent(variables.contactoId)}`,
        {
          method: "PUT",
          body: JSON.stringify(variables.payload satisfies ClienteContactoUpdateRequest),
        },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "contactos", clienteId] });
    },
  });
}

export function useDeleteClienteContacto(clienteId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (contactoId: string) =>
      apiFetch<void>(
        `/clientes/${encodeURIComponent(clienteId)}/contactos/${encodeURIComponent(contactoId)}`,
        {
          method: "DELETE",
        },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "contactos", clienteId] });
    },
  });
}

