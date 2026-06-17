export type PermissionAction = "view" | "create" | "edit" | "manage";

const ACTION_FALLBACKS: Record<PermissionAction, PermissionAction[]> = {
  view: ["view"],
  create: ["create", "edit", "manage"],
  edit: ["edit", "manage"],
  manage: ["manage"],
};

export function resolveScopedPermissions(scope: string, action: PermissionAction) {
  return ACTION_FALLBACKS[action].map((candidate) => `${scope}:${candidate}`);
}

export function hasScopedPermission(
  permissions: readonly string[] | undefined,
  scope: string,
  action: PermissionAction,
) {
  if (!permissions?.length) return false;
  const allowed = resolveScopedPermissions(scope, action);
  return allowed.some((permission) => permissions.includes(permission));
}

export function hasPermission(
  permissions: readonly string[] | undefined,
  permission: string | undefined,
) {
  if (!permission) return true;
  return permissions?.includes(permission) ?? false;
}
