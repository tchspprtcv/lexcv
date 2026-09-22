import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { AdminUser, AdminUserSavePayload } from "@/types/admin-users";
import type {
  AuditoriaRbacListFilters,
  AuditoriaRbacPageResponse,
} from "@/types/auditoria-rbac";
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
      // Atribuir/alterar o papel de um utilizador escreve um evento de auditoria RBAC
      // (AuditoriaRbacService.registarAtribuicoes) -- o registo de auditoria tem de refletir
      // essa mudança na mesma sessão, por isso OFFICE_RBAC_AUDITORIA_KEY é invalidado a par de
      // "admin"/"users".
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
        queryClient.invalidateQueries({ queryKey: ["auth", "me"] }),
        queryClient.invalidateQueries({ queryKey: OFFICE_RBAC_AUDITORIA_KEY }),
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
      // Eliminar um utilizador escreve um evento papel_retirar por cada papel que ele tinha
      // (motivo utilizador_eliminado) -- o registo de auditoria tem de refletir essa mudança na
      // mesma sessão.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
        queryClient.invalidateQueries({ queryKey: OFFICE_RBAC_AUDITORIA_KEY }),
      ]);
    },
  });
}

const OFFICE_RBAC_KEY = ["admin", "rbac"] as const;

// Phase 128 (AUDT-01/02/03), Plano 08: deliberadamente aninhada sob OFFICE_RBAC_KEY (nunca uma
// chave irmã) -- o TanStack Query invalida por prefixo, por isso as quatro mutações de papel e
// matriz (useSaveOfficeRbac / useCreateOfficeRole / useRenameOfficeRole / useDeleteOfficeRole),
// que já invalidam OFFICE_RBAC_KEY, atualizam também o registo de auditoria sem nenhuma mudança
// nelas. Só as duas mutações de utilizador (acima) precisam de uma invalidação explícita, porque
// a chave delas ("admin"/"users") não é prefixo de OFFICE_RBAC_AUDITORIA_KEY.
const OFFICE_RBAC_AUDITORIA_KEY = ["admin", "rbac", "auditoria"] as const;

function buildAuditoriaSearch(filters: AuditoriaRbacListFilters): string {
  const params = new URLSearchParams();
  if (filters.utilizadorAlvoId) params.set("utilizadorAlvoId", filters.utilizadorAlvoId);
  if (filters.papelId) params.set("papelId", filters.papelId);
  if (filters.page !== undefined) params.set("page", String(filters.page));
  if (filters.size !== undefined) params.set("size", String(filters.size));
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

// Hook só de leitura (useQuery) -- per o Read-Only Guarantee de 128-UI-SPEC.md (AUDT-04), nenhum
// hook de mutação contra o endpoint de auditoria pode alguma vez ser acrescentado a este
// ficheiro.
export function useOfficeRbacAuditoria(filters: AuditoriaRbacListFilters = {}) {
  return useQuery({
    queryKey: [
      ...OFFICE_RBAC_AUDITORIA_KEY,
      filters.utilizadorAlvoId ?? "",
      filters.papelId ?? "",
      filters.page ?? 0,
      filters.size ?? 20,
    ],
    queryFn: () =>
      apiFetch<AuditoriaRbacPageResponse>("/admin/rbac/auditoria" + buildAuditoriaSearch(filters)),
    enabled: typeof window !== "undefined",
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}

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
