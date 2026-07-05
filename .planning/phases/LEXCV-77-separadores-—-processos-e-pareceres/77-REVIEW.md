---
phase: LEXCV-77-separadores-—-processos-e-pareceres
reviewed: 2026-07-05T11:33:09Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase LEXCV-77: Code Review Report

**Reviewed:** 2026-07-05T11:33:09Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed `web/src/app/(dashboard)/clientes/[id]/page.tsx`, focusing on the two components added in this
phase: `ClienteProcessosTab` (commit `bce42f2`) and `ClienteParecerTab` (commit `d78dc77`). Both are
mounted only when their tab is the active branch of the `tab === "..."` ternary chain, which is a real
unmount/remount (not `display:none`), so React Query's per-hook `useQuery` correctly performs a lazy
fetch on first activation and no `enabled` flag is needed for that purpose.

**Threat-model check (cliente/tenant scoping) — verified safe.** I traced both new hooks
(`useProcessos({ cliente_id })` in `web/src/hooks/use-processos.ts` and `usePareceres({ clienteId })` in
`web/src/hooks/use-pareceres.ts`) through to the backend endpoints they call
(`GET /api/v1/processos` and `GET /api/v1/pareceres/solicitacoes` in `ResourceController.java` /
`ParecerController.java`). Both endpoints resolve `tenantId` from the security context first and filter
the tenant's own data set before applying the client-supplied `cliente_id`/`clienteId` as an additional
filter — a client cannot use this parameter to read another tenant's or another cliente's records by
tampering with the query string; a mismatched/foreign id simply yields zero rows within the caller's own
tenant. `id` (the route param, and therefore the value passed to both new tabs) always comes from the
already-tenant-scoped `useCliente(id)` load on the same page, so the two lists shown are always scoped to
the currently open cliente. No cross-cliente leakage path was found.

**Tab-switch / isEditing interaction — no re-fetch loop or state leakage found.** The two new tab
components are independent, fully unmounted when not selected, and hold no state that needs resetting on
tab exit (unlike `ClienteContactosCard`/`ClienteNotasCard`/`ResponsaveisCard`, which do need — and have —
`useEffect` resets keyed on `editable`). `staleTime: 30_000` on both queries means rapid tab flapping
within 30s won't refetch, and there is no unbounded loop risk since neither query key depends on
component-local mutable state.

The issues below are UX/permission-consistency gaps: the two new tabs are visible and their sub-fetches
fire for any user who can view the cliente record, regardless of whether that user actually holds
`processos:view` / `pareceres:view`, and one of the two components (`ClienteParecerTab`) issues an
additional request to an admin-only endpoint that will 403 for the vast majority of real users
(ADVOGADO/TECNICO/ASSISTENTE roles).

## Warnings

### WR-01: `ClienteParecerTab` unconditionally calls the admin-only `useAdminUsers()` hook

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1202`
**Issue:** `ClienteParecerTab` calls `useAdminUsers()` (which hits `GET /api/v1/admin/users`, gated
`@PreAuthorize("hasRole('ADMIN')")` at the class level in `AdminController`) purely to resolve
`advogadoId → nome` for display. For any non-ADMIN user viewing the Pareceres tab — i.e. the normal case
for ADVOGADO/TECNICO/ASSISTENTE roles who have `pareceres:view` but not `ROLE_ADMIN` — this request will
fail with 403 on every mount of the tab. It fails silently (401/403 are exempted from the toast in
`apiFetch`), so there's no user-visible crash, but every advogado name column will always render "—" for
those roles, which defeats the purpose of the column for most users, and it's a wasted failing request on
every tab activation. (Note: this exact pattern already exists for `ResponsaveisCard` since phase 59, so
it is not unique to this phase, but this phase adds a second, avoidable instance of it.)
**Fix:** Expose a non-admin-gated endpoint (e.g. a lightweight `/api/v1/users?role=ADVOGADO` behind a
scope every authenticated tenant member holds, or embed `responsavel_nome`-style server-side enrichment
like `listProcessos` already does for `responsavel_id`) instead of relying on the admin users list from
the client. At minimum, gate the `useAdminUsers()` call behind a permission check (e.g. only fetch if the
current user has `hasRole('ADMIN')` via `usePermissions()`), and fall back to displaying the raw
`advogadoId` (or omitting the column data) for everyone else so the request isn't fired needlessly.

### WR-02: New Processos/Pareceres tabs are not gated by `processos:view` / `pareceres:view` on the frontend

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:436-449, 1059-1062`
**Issue:** The "Processos" and "Pareceres" tab buttons, and the `ClienteProcessosTab` /
`ClienteParecerTab` components they mount, are rendered unconditionally for any user who can reach this
page (i.e. anyone with `clientes:view`). The backend does correctly enforce
`hasAuthority('processos:view')` / `hasAuthority('pareceres:view')` on the underlying list endpoints, so
this is not a data-exposure bug, but a user without those scopes will click into a tab that always fails,
see a raw `API 403: ...` error string (see WR-03), and have no indication up front that the tab is
unavailable to them. Every other cross-cutting permission check on this page (`canEditClientes`,
`AccessDeniedState` at the top level) follows the "gate both layers" convention from `CLAUDE.md`; these
two new tabs don't.
**Fix:** Gate tab visibility/enablement using `usePermissions()` the same way `canViewClientes` /
`canEditClientes` are derived at the top of the file, e.g.:
```tsx
const canViewProcessos = permissions.can.view("processos");
const canViewPareceres = permissions.can.view("pareceres");
...
{canViewProcessos ? (
  <Button ... onClick={() => setTab("processos")}>Processos</Button>
) : null}
```
and render an `AccessDeniedState`-style message (or hide the tab entirely) instead of mounting the
fetching component when the permission is absent.

### WR-03: Raw backend error text surfaced verbatim in the new tabs' error states

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1116-1121, 1217-1222`
**Issue:** Both new tabs render `processos.error.message` / `pareceres.error.message` directly when
`isError` is true. `apiFetch` throws `Error("API ${res.status}: ${errorMessage}")`
(`web/src/lib/api.ts:47`), so on a 403 (see WR-02) or any other backend failure the user sees a raw
string like `API 403: Access is denied` rendered inline in the card body — not localized, not
user-friendly, and inconsistent with the rest of the page (e.g. the top-level `AccessDeniedState`
component, or the plain "Erro ao carregar." messages used by `ResponsaveisCard`/`ClienteContactosCard`
elsewhere in this same file).
**Fix:** Normalize the error message the same way the rest of the file does for other cards, e.g.:
```tsx
) : processos.isError ? (
  <div className="p-6 text-sm text-red-600">
    Não foi possível carregar os processos deste cliente.
  </div>
```
and drop the `instanceof Error ? error.message : ...` branch that leaks the raw `"API <status>: ..."`
string, or route 403s specifically to a shared "sem permissão" message.

## Info

### IN-01: Redundant/inconsistent type assertion on `estadoVariant`

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1160`
**Issue:** `estadoVariant` is already inferred as a literal union from the nested ternary at
lines 1137-1146, so the `as "green" | "amber" | "gray" | "purple" | "secondary"` cast at the usage site is
unnecessary. It's also inconsistent with `parecerStatusVariant` (used identically a few dozen lines
later, in `ClienteParecerTab`), which needs no cast at its call site. An unnecessary `as` cast is a code
smell because it can silently mask a real type mismatch if the ternary is later edited to return an
invalid variant string.
**Fix:** Give `estadoVariant` an explicit return type via a small helper (mirroring
`parecerStatusVariant`'s pattern) instead of inlining the ternary + cast at the `<Badge variant={...}>`
call site.

### IN-02: `estado`/`estadoLabel` derivation duplicated inline instead of extracted like `parecerStatusVariant`

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1136-1147`
**Issue:** `ClienteProcessosTab` computes `estado`, `estadoVariant`, and `estadoLabel` inline inside the
`.map()` callback, whereas the equivalent logic for pareceres was correctly extracted into standalone
helper functions (`parecerStatusVariant`, `formatParecerDate`) placed above `ClienteParecerTab`. This is a
minor inconsistency in the codebase's own established pattern within the same file/phase, and it makes
`ClienteProcessosTab`'s render callback harder to read than it needs to be.
**Fix:** Extract a `processoEstadoVariant(estado: string)` / `processoEstadoLabel(estado: string)` helper
pair next to `ClienteProcessosTab`, following the same shape as `parecerStatusVariant`.

---

_Reviewed: 2026-07-05T11:33:09Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
