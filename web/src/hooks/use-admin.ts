import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { MockUser, MockRolePermissions, MockPermissionDef } from "@/server/mock-db";

export function useAdminUsers() {
  const enabled = typeof window !== "undefined" ;

  return useQuery({
    queryKey: ["admin", "users"],
    queryFn: () => apiFetch<MockUser[]>("/admin/users"),
    enabled,
    staleTime: 30_000,
  });
}

export function useAdminSaveUser(id?: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: Partial<MockUser> & { password?: string }) => {
      const url = id ? `/admin/users/${encodeURIComponent(id)}` : "/admin/users";
      const method = id ? "PUT" : "POST";
      return apiFetch<MockUser>(url, {
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

export interface RbacResponse {
  rolePermissions: MockRolePermissions;
  systemPermissions: MockPermissionDef[];
}

export function useAdminRbac() {
  const enabled = typeof window !== "undefined" ;

  return useQuery({
    queryKey: ["admin", "rbac"],
    queryFn: () => apiFetch<RbacResponse>("/admin/rbac"),
    enabled,
    staleTime: 60_000,
  });
}

export function useAdminSaveRbac() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: { rolePermissions: MockRolePermissions }) =>
      apiFetch<{ rolePermissions: MockRolePermissions; message: string }>("/admin/rbac", {
        method: "PUT",
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["admin", "rbac"] }),
        queryClient.invalidateQueries({ queryKey: ["auth", "me"] }),
      ]);
    },
  });
}
