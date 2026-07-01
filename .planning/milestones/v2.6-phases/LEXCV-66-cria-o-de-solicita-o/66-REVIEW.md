---
phase: 66-cria-o-de-solicita-o
reviewed: 2026-07-01T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - web/src/schemas/pareceres.ts
  - web/src/hooks/use-pareceres.ts
  - web/src/app/(dashboard)/pareceres/nova/page.tsx
  - web/src/app/(dashboard)/pareceres/page.tsx
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase 66: Code Review Report

**Reviewed:** 2026-07-01
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Reviewed the Parecer Jurídico creation form (schema, mutation hook, form page, list CTA) against the plan/summary and the corresponding backend endpoint (`ParecerController.createSolicitacao`). The three specific concerns from the review brief check out:

- **Mass-assignment:** `ParecerCreateRequest` is a hand-typed six-field object and `useCreateParecer`'s `mutationFn` sends exactly that payload (`payload satisfies ParecerCreateRequest`); no `status`/`id`/`tenantId` leak into the request body. Backend independently reconstructs the entity from an explicit allowlist (`ParecerController.java:112-135`) and forces `status` server-side, so this is defense-in-depth on both layers as claimed.
- **RBAC gating:** `pareceres:create` gates both the list-page CTA (`page.tsx:55,100-104`) and the `/pareceres/nova` page render (`nova/page.tsx:32-42`), matching `@PreAuthorize("hasAuthority('pareceres:create')")` on the backend. Frontend gate is UX-only; backend remains authoritative, consistent with the phase's threat model.
- **descricao superRefine dual-message:** Correctly emits the two UI-SPEC copies for empty vs. 1-9 char input (`pareceres.ts:16-33`). Verified against the plan's acceptance criteria.
- **`zodResolver(...) as any` cast:** Genuinely consistent with `processos/[id]/page.tsx:235`'s `prazoForm` pattern (same root cause — `.default("MEDIA")` producing an input/output type split RHF's resolver typing can't reconcile). Not masking a new bug; it's the same known zod/RHF interop gap, applied identically. Notably the parecer form is slightly cleaner than its analog: it does not also need `onSubmit as any` (processos/[id] casts `prazoForm.handleSubmit(onSubmitPrazo as any)` at line 825), because the parecer page's `onSubmit` signature matches `ParecerCreateFormValues` (the output type) exactly.

One real logic bug (processoId can silently persist against the wrong cliente after switching cliente) and a handful of quality issues are documented below.

## Warnings

### WR-01: Stale `processoId` survives a cliente change, allowing a solicitação to reference a processo that does not belong to the newly selected cliente

**File:** `web/src/app/(dashboard)/pareceres/nova/page.tsx:68-70`
**Issue:** `processos` re-fetches based on `clienteIdValue` (`form.watch("clienteId")`), but the currently-selected `processoId` in form state is never cleared when the cliente changes. Sequence: user picks Cliente A → picks Processo X (belongs to A) → changes selection to Cliente B. The `<select>` for processo is repopulated with Cliente B's processos, but RHF's internal value for `processoId` still holds Processo X's id (native `<select>` re-render does not force-reset a bound value that's absent from the new `<option>` list back through RHF state — the field value stays whatever was last registered). If the user does not manually reopen the processo dropdown and reselect, submission sends `{ clienteId: B, processoId: X, ... }`. The backend only checks `processoBelongsToTenant` (`ParecerController.java:107-110`), not that the processo belongs to `clienteId` — so this is accepted and persisted, producing a solicitação that couples a cliente to an unrelated processo within the same tenant.
**Fix:** Reset `processoId` whenever `clienteIdValue` changes, e.g. add:
```tsx
React.useEffect(() => {
  form.setValue("processoId", undefined);
}, [clienteIdValue]);
```
placed after the `clienteIdValue`/`processos` declarations. Alternatively, disable/hide the processo select until a cliente is chosen and reset on change (either approach is acceptable — the CONTEXT.md discretion note only covered whether the select should be *disabled* pre-cliente-selection, not the stale-value-on-change case, which is a separate bug).

### WR-02: Backend has no server-side check that `processoId` belongs to the same `clienteId` on create

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:107-110` (referenced from `web/src/app/(dashboard)/pareceres/nova/page.tsx`, since the frontend is the only current guard against this)
**Issue:** `processoBelongsToTenant` validates tenant ownership only. Combined with WR-01 (or a maliciously crafted request bypassing the UI entirely, since `pareceres:create` is a normal non-admin authority many users will hold), any user with `pareceres:create` can submit `{clienteId: A, processoId: <processo of cliente C>}` and the backend will happily persist the mismatched pairing — there is no cross-check. This is out of scope for this frontend-only phase to fix, but should be flagged since the frontend threat model (T-66-02) claims IDOR is "mitigated" by tenant-scoped hooks alone, and that mitigation doesn't cover cliente/processo consistency, only tenant boundary.
**Fix:** File a backend follow-up to validate `processo.clienteId == body.clienteId` (or explicitly document that a parecer's processo need not belong to the same cliente, if that's an intentional domain rule) in `ParecerController.createSolicitacao` and `updateSolicitacao`.

## Info

### IN-01: `prazo` accepted as an unvalidated free-form trimmed string despite being rendered via `<input type="date">`

**File:** `web/src/schemas/pareceres.ts:34`, `web/src/app/(dashboard)/pareceres/nova/page.tsx:174-181`
**Issue:** `prazo: optionalTrimmedString` performs no format validation (no regex/date-parse check). In practice the native date input constrains user-facing entry, but nothing stops a malformed/garbage value from reaching the API if the DOM value is manipulated (devtools, browser extension, or a future non-date-input reuse of this schema) or if `prazo` is later included in a bulk-import path that reuses this schema. `formatDate` in `page.tsx:18-23` (the list page) does defend against unparseable dates on render (`Number.isNaN(d.getTime())` fallback), so this is not a crash risk, just weak input validation.
**Fix:** Add a light format check, e.g. `.refine((v) => v === undefined || !Number.isNaN(Date.parse(v)), "Data inválida.")` on the `prazo` field, or a stricter `YYYY-MM-DD` regex to match the native date input's format contract.

### IN-02: Duplicate `usePermissions()` calls across `ParecerCreatePage` / `ParecerCreateFormContent` and across `ParecerPage` / `ParecerPageContent`

**File:** `web/src/app/(dashboard)/pareceres/nova/page.tsx:32,49`; `web/src/app/(dashboard)/pareceres/page.tsx:38,54`
**Issue:** Both the outer gate component and the inner content component call `usePermissions()` independently to recompute `canCreatePareceres`/`canView`. The summary calls this "cheap — TanStack Query dedupes," which is true for the network fetch, but it still means two separate hook subscriptions, two `isLoading` transitions, and two `permissions.can.create(...)` recomputations per render — acceptable given the existing `processos/novo` precedent, but worth flagging as duplication that a shared context/prop-drill could avoid.
**Fix:** Optional — could lift the `usePermissions()` result once in the outer component and pass `canCreatePareceres` as a prop to the inner content component, removing the duplicate call. Low priority; matches established repo convention.

### IN-03: `advogados` filter silently drops users with `roles === undefined`, with no visible fallback fetch/error surface for `adminUsers.isError`

**File:** `web/src/app/(dashboard)/pareceres/nova/page.tsx:72-75`, `web/src/app/(dashboard)/pareceres/page.tsx:64-67`
**Issue:** `adminUsers.data ?? []` combined with `u.roles?.includes(...)` means if `useAdminUsers()` fails (`adminUsers.isError`), the advogado select simply renders empty (just "Atribuir mais tarde") with no error indicator — unlike the `clientes.isError` branch which does render an inline red message (`nova/page.tsx:134-138`). A user might assume there really are no advogados in the tenant rather than that the request failed.
**Fix:** Add an inline error state for `adminUsers.isError` mirroring the `clientes.isError` treatment, e.g. disable the select and show "Erro ao carregar advogados" when `adminUsers.isError`.

---

_Reviewed: 2026-07-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
