import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { SetupInitializeRequest } from "@/types/setup";
import type {
  TenantAdminSummary,
  TenantAtivoRequest,
  TenantUpdateRequest,
} from "@/types/platform-admin";

// Todas as mutacoes abaixo invalidam TENANTS_LIST_KEY em vez de recarregar a
// pagina inteira. O recarregamento total do browser, usado por
// `UserManagementTab` (settings/page.tsx) para sincronizar a antiga base de
// dados simulada, e um artefacto dessa era legada e esta explicitamente
// proibido pelo UI-SPEC desta fase. Tambem nao existe nenhuma cache de
// "detalhe" por tenant nesta consola — a listagem completa e sempre refeita,
// por isso nenhuma mutacao chama setQueryData.
const TENANTS_LIST_KEY = ["platform", "tenants", "list"] as const;

export function useTenantsAdmin() {
  const enabled = typeof window !== "undefined";

  return useQuery({
    queryKey: TENANTS_LIST_KEY,
    queryFn: () => apiFetch<TenantAdminSummary[]>("/platform/tenants"),
    enabled,
    staleTime: 30_000,
  });
}

export function useCreateTenant() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: SetupInitializeRequest) =>
      apiFetch<{ id: string; nome: string }>("/platform/tenants", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: TENANTS_LIST_KEY });
    },
  });
}

export function useUpdateTenant() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: TenantUpdateRequest }) =>
      apiFetch<TenantAdminSummary>(`/platform/tenants/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify(payload satisfies TenantUpdateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: TENANTS_LIST_KEY });
    },
  });
}

export function useSetTenantAtivo() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, ativo }: { id: string; ativo: boolean }) =>
      apiFetch<TenantAdminSummary>(`/platform/tenants/${encodeURIComponent(id)}/ativo`, {
        method: "PATCH",
        body: JSON.stringify({ ativo } satisfies TenantAtivoRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: TENANTS_LIST_KEY });
    },
  });
}
