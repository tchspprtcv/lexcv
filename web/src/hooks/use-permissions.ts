import { useMemo } from "react";

import { hasPermission, hasScopedPermission, type PermissionAction } from "@/lib/permissions";

import { useMe } from "./use-me";

export function usePermissions() {
  const me = useMe();
  const permissions = useMemo(() => me.data?.permissions ?? [], [me.data?.permissions]);

  const can = useMemo(
    () => ({
      exact: (permission: string) => hasPermission(permissions, permission),
      scope: (scope: string, action: PermissionAction) =>
        hasScopedPermission(permissions, scope, action),
      view: (scope: string) => hasScopedPermission(permissions, scope, "view"),
      create: (scope: string) => hasScopedPermission(permissions, scope, "create"),
      edit: (scope: string) => hasScopedPermission(permissions, scope, "edit"),
      manage: (scope: string) => hasScopedPermission(permissions, scope, "manage"),
    }),
    [permissions],
  );

  return {
    ...me,
    permissions,
    can,
  };
}
