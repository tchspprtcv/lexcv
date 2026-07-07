---
phase: LEXCV-77-separadores-—-processos-e-pareceres
fixed_at: 2026-07-05T11:40:00Z
review_path: .planning/phases/LEXCV-77-separadores-—-processos-e-pareceres/77-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase LEXCV-77: Code Review Fix Report

**Fixed at:** 2026-07-05T11:40:00Z
**Source review:** .planning/phases/LEXCV-77-separadores-—-processos-e-pareceres/77-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: `ClienteParecerTab` unconditionally calls the admin-only `useAdminUsers()` hook

**Files modified:** `web/src/hooks/use-admin.ts`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** 13ea850
**Applied fix:** Added an optional `{ enabled?: boolean }` options parameter to `useAdminUsers()`
(backward compatible — defaults to `true`, so the four other pre-existing call sites in the app are
unaffected). In `ClienteParecerTab`, called `usePermissions()` to read the current user's roles and
derived `isAdmin`, then passed `useAdminUsers({ enabled: isAdmin })` so the admin-only
`GET /api/v1/admin/users` request only fires for ADMIN users. Non-admin users now simply see an empty
`advogadoNomeById` map, which the existing `.get(...) ?? "—"` fallback already renders gracefully — no
failing request is made and no console/network noise is generated for the common ADVOGADO/TECNICO/
ASSISTENTE roles.

### WR-02: New Processos/Pareceres tabs are not gated by `processos:view` / `pareceres:view` on the frontend

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** 9b7659a
**Applied fix:** Added `usePermissions()` to `ClienteDetailContent` (the component that owns the tab
state and renders the tab bar) and derived `canViewProcessos` / `canViewPareceres` via
`permissions.can.view("processos")` / `permissions.can.view("pareceres")`, mirroring how
`canViewClientes` / `canEditClientes` are already derived at the top of the file. The "Processos" and
"Pareceres" tab buttons are now only rendered when the corresponding permission is present. As a
defense-in-depth second layer, the tab body itself now also checks the permission before mounting
`ClienteProcessosTab` / `ClienteParecerTab`, rendering the existing `AccessDeniedState` shared component
(already used at the top-level `clientes:view` gate) with a scoped Portuguese message instead, in the
unlikely event `tab` state is on one of these keys without the permission.

### WR-03: Raw backend error text surfaced verbatim in the new tabs' error states

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** d28b218
**Applied fix:** Removed the `processos.error instanceof Error ? processos.error.message : ...` and
`pareceres.error instanceof Error ? pareceres.error.message : ...` branches in `ClienteProcessosTab` and
`ClienteParecerTab`. Both error states now always render the existing friendly Portuguese fallback text
("Não foi possível carregar os processos/pareceres deste cliente."), so a raw `API 403: ...` /
`API 500: ...` string from `apiFetch` is never shown to the user, consistent with the plain
"Erro ao carregar ..." messages used by `ResponsaveisCard` / `ClienteContactosCard` / `ClienteNotasCard`
elsewhere in this same file.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-05T11:40:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
