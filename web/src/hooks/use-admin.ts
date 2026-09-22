import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { AdminUser, AdminUserSavePayload } from "@/types/admin-users";
import type {
  OfficeRbac,
  OfficeRbacUpdateRequest,
  PapelCreateRequest,
  PapelRenameRequest,
} from "@/types/office-rbac";

// Phase 127, Plano 06: este ficheiro deixou de importar do módulo mock pré-backend em
// `@/server` (ver CLAUDE.md) -- os seus tipos (`MockUser`, `MockRolePermissions`, ...) já não
// correspondiam ao payload real que o backend serve desde os Planos 02-05 desta fase. Antes
// desta reescrita, `useAdminSaveRbac` existia mas nunca era
// chamado -- `RbacTab` (`settings/page.tsx`) fazia `apiFetch("/admin/rbac", { method: "PUT" })`
// directamente, contornando o hook. Essa duplicação fica fechada aqui: o ecrã reescrito (Plano
// 07) passa a usar exclusivamente os hooks abaixo -- não deve voltar a chamar `apiFetch`
// directamente para nenhuma rota `/admin/rbac*`.

export function useAdminUsers(options?: { enabled?: boolean }) {
  const enabled = typeof window !== "undefined" && (options?.enabled ?? true);

  return useQuery({
    queryKey: ["admin", "users"],
    queryFn: () => apiFetch<AdminUser[]>("/admin/users"),
    enabled,
    staleTime: 30_000,
  });
}

export function useAdminSaveUser(id?: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: Partial<AdminUserSavePayload>) => {
      const url = id ? `/admin/users/${encodeURIComponent(id)}` : "/admin/users";
      const method = id ? "PUT" : "POST";
      return apiFetch<AdminUser>(url, {
        method,
        body: JSON.stringify(payload),
      });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
        queryClient.invalidateQueries({ queryKey: ["auth", "me"] }),
      ]);
    },
  });
}

export function useAdminDeleteUser(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () =>
      apiFetch<void>(`/admin/users/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

// DEPRECATED (Phase 127, Plano 06): shim de compatibilidade só para o `RbacTab` ainda por
// reescrever de `settings/page.tsx` (forma anterior a esta fase, tipada contra o módulo mock
// pré-backend). O Plano 07 reescreve esse ecrã inteiro contra `useOfficeRbac`/
// `useSaveOfficeRbac` abaixo -- remover este bloco nessa altura, e não antes: sem ele,
// `settings/page.tsx` deixa de resolver o import e o build inteiro fica bloqueado por uma
// razão que não é o objectivo deste plano (ver 127-06-SUMMARY.md). O tipo é estrutural, não
// importado de `@/server`, para não violar a proibição de import mock deste ficheiro -- mas
// não é a forma que o backend hoje devolve em `/admin/rbac` (que já é `OfficeRbac`, não isto);
// esse desalinhamento é pré-existente a este plano e é exactamente o que o Plano 07 fecha.
export interface RbacResponse {
  rolePermissions: Record<string, string[]>;
  systemPermissions: { key: string; nome: string; descricao: string; modulo: string }[];
}

/** @deprecated Use `useOfficeRbac` — removido quando o Plano 07 reescrever `RbacTab`. */
export function useAdminRbac() {
  const enabled = typeof window !== "undefined";

  return useQuery({
    queryKey: ["admin", "rbac", "legacy"],
    queryFn: () => apiFetch<RbacResponse>("/admin/rbac"),
    enabled,
    staleTime: 60_000,
  });
}

const OFFICE_RBAC_KEY = ["admin", "rbac"] as const;

export function useOfficeRbac() {
  const enabled = typeof window !== "undefined";

  return useQuery({
    queryKey: OFFICE_RBAC_KEY,
    queryFn: () => apiFetch<OfficeRbac>("/admin/rbac"),
    enabled,
    staleTime: 30_000,
  });
}

export function useSaveOfficeRbac() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: OfficeRbacUpdateRequest) =>
      apiFetch<OfficeRbac>("/admin/rbac", {
        method: "PUT",
        body: JSON.stringify(payload satisfies OfficeRbacUpdateRequest),
      }),
    onSuccess: async () => {
      // Uma mudança de permissões pode alterar a própria autoridade do utilizador atual --
      // useMe() é quem determina o que a shell da app renderiza, por isso é sempre invalidado
      // a par da matriz, nunca só a matriz.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: OFFICE_RBAC_KEY }),
        queryClient.invalidateQueries({ queryKey: ["auth", "me"] }),
      ]);
    },
  });
}

export function useCreateOfficeRole() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PapelCreateRequest) =>
      apiFetch<{ id: string; nome: string }>("/admin/rbac/roles", {
        method: "POST",
        body: JSON.stringify(payload satisfies PapelCreateRequest),
      }),
    onSuccess: async () => {
      // Criar um papel muda o que o seletor de papéis da gestão de utilizadores oferece.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: OFFICE_RBAC_KEY }),
        queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
      ]);
    },
  });
}

export function useRenameOfficeRole() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, nome }: { id: string; nome: string }) =>
      apiFetch<{ id: string; nome: string }>(`/admin/rbac/roles/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify({ nome } satisfies PapelRenameRequest),
      }),
    onSuccess: async () => {
      // O nome exibido do papel muda em toda a app (seletor de papéis, badges), e pode ser o
      // próprio papel do utilizador atual -- por isso useMe() é invalidado a par da matriz e
      // da lista de utilizadores.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: OFFICE_RBAC_KEY }),
        queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
        queryClient.invalidateQueries({ queryKey: ["auth", "me"] }),
      ]);
    },
  });
}

export function useDeleteOfficeRole() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/admin/rbac/roles/${encodeURIComponent(id)}`, {
        method: "DELETE",
      }),
    onSuccess: async () => {
      // Apagar um papel muda o que o seletor de papéis oferece e pode alterar a autoridade do
      // utilizador atual (defesa em profundidade -- o backend já recusa apagar um papel com
      // utilizadores atribuídos, mas invalidar auth/me aqui custa nada e fecha qualquer corrida).
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: OFFICE_RBAC_KEY }),
        queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
        queryClient.invalidateQueries({ queryKey: ["auth", "me"] }),
      ]);
    },
  });
}
