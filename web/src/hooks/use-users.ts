import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

export interface TenantUserOption {
  id: string;
  nome: string;
  // WR-01 (Phase 87 code review, iteration 2): undefined/true means active;
  // only a strict `false` marks a deactivated account (mirrors backend
  // UserSummaryResponse.ativo, which is nullable).
  ativo?: boolean;
}

/**
 * Tenant-scoped user listing for "assign to" pickers (Reatribuir Responsável,
 * Novo Prazo responsável). Unlike useAdminUsers()/`/admin/users`, this is NOT
 * gated behind ADMIN — backend: GET /users, gated by hasAuthority('processos:view'),
 * the same permission that already gates the processo-detail page these pickers
 * live on. Returns only { id, nome } — no roles, permissions, email, or other
 * admin-only fields.
 */
export function useTenantUsers() {
  return useQuery({
    queryKey: ["users", "tenant-list"],
    queryFn: () => apiFetch<TenantUserOption[]>("/users"),
    staleTime: 30_000,
  });
}
