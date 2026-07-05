---
phase: LEXCV-77-separadores-—-processos-e-pareceres
reviewed: 2026-07-05T12:05:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/hooks/use-admin.ts
findings:
  critical: 0
  warning: 0
  info: 2
  total: 2
status: clean
---

# Phase LEXCV-77: Code Review Report (Re-review)

**Reviewed:** 2026-07-05T12:05:00Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** clean

## Summary

Re-reviewed `web/src/app/(dashboard)/clientes/[id]/page.tsx` and `web/src/hooks/use-admin.ts` after the
`77-REVIEW-FIX.md` iteration-1 pass that addressed WR-01, WR-02, and WR-03 from the prior round
(`13ea850`, `9b7659a`, `d28b218`). All three fixes were verified against the actual diffs and current
file contents (not just the fix report's claims). All three are complete, correct, and introduce no new
regressions. Findings from this round are informational only (carried over, unchanged, from the prior
round's IN-01/IN-02 — still not addressed, but out of scope for a warning-only fix pass and not blocking).

**WR-01 (admin-only `useAdminUsers()` 403 for non-admin) — confirmed fixed, and confirmed non-breaking
for the other 5 call sites.** `useAdminUsers(options?: { enabled?: boolean })` now computes
`enabled = typeof window !== "undefined" && (options?.enabled ?? true)`. This is backward compatible by
construction: `options?.enabled ?? true` evaluates to `true` whenever `options` is `undefined` or
`options.enabled` is `undefined`, which is exactly the call shape used at all 5 pre-existing call sites
(`settings/page.tsx:149`, `pareceres/page.tsx:78`, `pareceres/nova/page.tsx:71`, `pareceres/[id]/page.tsx:134`,
`processos/[id]/page.tsx:156` — all call `useAdminUsers()` with zero arguments). I traced each of these 5
sites and confirmed none passes an options object, so their `enabled` value is bit-for-bit identical
before and after this change. Only the new 6th call site in `ClienteParecerTab`
(`web/src/app/(dashboard)/clientes/[id]/page.tsx:1217`) passes `{ enabled: isAdmin }`, where
`isAdmin = Boolean(permissions.data?.roles?.includes("ADMIN"))` — `permissions.data` is `usePermissions()`'s
spread of `useMe()`'s query result, and `MeResponse.roles: Role[]` (`web/src/types/auth.ts:22`) is the
correct field, matching the pattern already used in `settings/page.tsx:44` (`me?.roles?.includes("ADMIN")`).
Backend confirms `/api/v1/admin/users` is gated `@PreAuthorize("hasRole('ADMIN')")` at the `AdminController`
class level, so this is the correct role to gate on. The user-visible outcome for non-admin roles is
unchanged (advogado name column already fell back to "—" before the fix, via
`advogadoNomeById.get(...) ?? "—"`); the fix only eliminates the wasted failing request. No regression.

**WR-02 (missing client-side permission gating on the new tabs) — confirmed fixed, defense-in-depth
applied correctly.** `canViewProcessos` / `canViewPareceres` are derived once in `ClienteDetailContent` via
`permissions.can.view("processos")` / `permissions.can.view("pareceres")` and used consistently in two
places: (1) to conditionally render the tab buttons (`web/src/app/(dashboard)/clientes/[id]/page.tsx:439-456`),
and (2) to gate the tab body, rendering `AccessDeniedState` instead of mounting the fetching component
when the permission is absent (`:1066-1077`). This mirrors the existing `canViewClientes` pattern at the
top-level page component and correctly matches the backend's `hasAuthority('processos:view')` /
`hasAuthority('pareceres:view')` enforcement described in CLAUDE.md's "both layers must agree" rule.

**WR-03 (raw error strings) — confirmed fixed.** Both `ClienteProcessosTab` (`:1131-1134`) and
`ClienteParecerTab` (`:1232-1234`) now unconditionally render the friendly Portuguese fallback string and
no longer branch on `error instanceof Error ? error.message : ...`, so a raw `API 403: ...` /
`API 500: ...` string from `apiFetch` can no longer leak into these two cards.

**No new issues introduced by the fix commits.** I checked for common regression patterns specific to
this kind of change (optional-param signature changes breaking other callers, permission-check timing
issues, duplicate/shadowed `usePermissions()` calls) — none found. `ClienteDetailContent` and
`ClienteParecerTab` each call `usePermissions()` independently; this is safe since both ultimately read the
same deduplicated `["auth", "me"]` React Query cache entry, not distinct fetches. The
`isAdmin`/`enabled` flag being momentarily `false` while `useMe()` is still loading (before the
first successful `/auth/me` response) only delays the `/admin/users` fetch until permissions resolve; it
does not cause a stuck-disabled state, since React Query re-evaluates `enabled` on every render and will
fire the query as soon as `isAdmin` flips true.

## Info

### IN-01: Redundant/inconsistent type assertion on `estadoVariant` (carried over, unfixed)

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1173`
**Issue:** `estadoVariant` is already inferred as a literal union from the nested ternary a few lines
above, so the `as "green" | "amber" | "gray" | "purple" | "secondary"` cast at the `<Badge variant={...}>`
call site is unnecessary, and inconsistent with `parecerStatusVariant` (same file, used a few dozen lines
later for the Pareceres tab), which needs no cast at its call site because it's a real function with an
inferred return type. This was flagged as IN-01 in the prior review round and was correctly left unfixed
(out of scope for a warning-only fix pass); repeating it here only for completeness of this re-review.
**Fix:** Extract a small `processoEstadoVariant(estado: string)` helper (mirroring `parecerStatusVariant`)
so the return type is nominal and the inline cast can be removed.

### IN-02: `estado`/`estadoLabel` derivation duplicated inline instead of extracted like `parecerStatusVariant` (carried over, unfixed)

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1149-1160`
**Issue:** `ClienteProcessosTab` computes `estado`, `estadoVariant`, and `estadoLabel` inline inside the
`.map()` callback, whereas the equivalent Pareceres logic was extracted into standalone helpers
(`parecerStatusVariant`, `formatParecerDate`) placed above `ClienteParecerTab`. Minor inconsistency within
the same file/phase; makes `ClienteProcessosTab`'s render callback slightly harder to read. Also flagged
in the prior round and correctly left unfixed as an Info item.
**Fix:** Extract `processoEstadoVariant(estado: string)` / `processoEstadoLabel(estado: string)` helper
pair next to `ClienteProcessosTab`, following the same shape as `parecerStatusVariant`.

---

_Reviewed: 2026-07-05T12:05:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
