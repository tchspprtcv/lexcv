export type PermissionAction = "view" | "create" | "edit" | "manage";

// Canonical scope registry, mirrored from backend DatabaseSeeder.seedRbac().
// Additive only — resolveScopedPermissions/hasScopedPermission remain scope-agnostic
// (typed as `scope: string`) so existing ad-hoc call sites keep working unchanged.
export const KNOWN_SCOPES = [
  "clientes",
  "processos",
  "agenda",
  "documentos",
  "financeiro",
  "pareceres",
  "notificacoes",
] as const;
export type PermissionScope = (typeof KNOWN_SCOPES)[number];

const ACTION_FALLBACKS: Record<PermissionAction, PermissionAction[]> = {
  view: ["view", "edit", "manage", "create"],
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
