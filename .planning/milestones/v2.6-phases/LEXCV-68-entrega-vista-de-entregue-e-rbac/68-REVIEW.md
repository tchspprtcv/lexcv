---
phase: 68-entrega-vista-de-entregue-e-rbac
reviewed: 2026-07-01T00:00:00Z
depth: deep
files_reviewed: 3
files_reviewed_list:
  - web/src/hooks/use-pareceres.ts
  - "web/src/app/(dashboard)/pareceres/[id]/page.tsx"
  - "web/src/app/(dashboard)/pareceres/nova/page.tsx"
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 68: Code Review Report

**Reviewed:** 2026-07-01
**Depth:** deep
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Reviewed the entrega/RBAC/read-only closure for the pareceres lifecycle at deep depth, cross-referencing `ParecerController.entregarSolicitacao` (backend, lines 353-397) against the frontend gate. The core claims in the summary hold up under adversarial reading:

- **RBAC gate correctness (T-68-01):** `showEntregarTrigger = !permissions.isLoading && canEditPareceres && isResponsavelOuAdmin && !isConcluido` (page.tsx:155-156) is derived from the same `isResponsavelOuAdmin`/`isConcluido` used for the Nova Versão form, and the boolean logic exactly mirrors the backend's `isAdmin || isResponsavel` (`ParecerController.java:375-380`) plus the class-level `pareceres:edit` `@PreAuthorize`. No window exists where the button renders before `permissions`/`parecer` data resolves — both loading states are guarded. Confirmed correct.
- **versaoFinalId selection (T-68-02):** the `<select>` in `EntregarParecerDialog` (page.tsx:471-475) is populated exclusively from the `versoes` prop, which is `versoes.data` passed down from the already-fetched `useParecerVersoes(id)` — no free-text field exists, and the backend independently re-validates `versao.getSolicitacaoId().equals(id)` (404 otherwise). Defense-in-depth confirmed on both sides.
- **Cache invalidation / no-manual-reload (T-68-03):** `useEntregarParecer.onSuccess` invalidates `["pareceres","detail",id]` and `["pareceres","list"]` (use-pareceres.ts:155-160), which is sufficient to flip `parecer.data.status` and re-render the CONCLUIDO branch without touching `versoes` (correctly not invalidated, since entrega doesn't mutate a version row).
- **No fabricated entrega metadata (T-68-05):** `ParecerEntregueBlock` derives "Elaborado por / em" strictly from `versaoFinal.criadoPorId` / `versaoFinal.createdAt` (page.tsx:529-531) — no synthetic entregue-por/em field, matching the `ParecerSolicitacao`/`ParecerVersao` type definitions which have no such fields.
- **CardTitle typography:** all four `CardTitle` instances across the reviewed files ("Dados", "Versões", "Parecer Entregue", "Nova Versão" on the detail page; "Dados da Solicitação" on nova/page.tsx) carry `text-lg font-bold`. Timeline dot uses `bg-slate-400 dark:bg-slate-500` (page.tsx:236), zero remaining `bg-blue-600` on the dot span. Confirmed complete — no leftover unstyled instance found in either file.

No Critical/Blocker-severity issues were found. Three Warnings and three Info-level issues were found, detailed below — mostly around a data-loading edge case in the entrega dialog and defensive robustness gaps that don't currently manifest as exploitable bugs but should be hardened.

## Warnings

### WR-01: Entrega dialog can be confirmed with a stale/empty version list if `versoes` hasn't loaded yet when the trigger becomes visible

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:410-476`
**Issue:** `showEntregarTrigger` depends only on `permissions.isLoading`, `canEditPareceres`, `isResponsavelOuAdmin`, and `isConcluido` — it does NOT gate on `versoes.isLoading`. Since `parecer` and `versoes` are two independent `useQuery` calls (page.tsx:132-133) that can resolve at different times, there is a real window where `parecer.data` has already resolved (so `showEntregarTrigger` is `true`) while `versoes.data` is still `undefined` (still loading). In that window `EntregarParecerDialog` renders with `versoes={undefined}`, the `<select>` renders zero `<option>` elements, `defaultVersaoId` is `null`, and `selectedVersaoId` is `null`. The Confirm button IS correctly disabled in this state (`disabled={entregar.isPending || !selectedVersaoId}`), so no invalid submission is possible — but the dialog is fully openable and shows an empty, confusing selector with no loading indicator, and nothing tells the user why the button is inert.
**Fix:** Gate the trigger (or at minimum the dialog body) on `versoes.data` being loaded, or show a "A carregar versões..." placeholder inside the `<select>` area when `versoes.isLoading`:
```tsx
const showEntregarTrigger =
  !permissions.isLoading &&
  !versoes.isLoading &&
  canEditPareceres &&
  isResponsavelOuAdmin &&
  !isConcluido;
```

### WR-02: Escape key / overlay click can dismiss the entrega AlertDialog mid-mutation, silently orphaning the in-flight request from the UI's perspective

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:444, 482`
**Issue:** `AlertDialogCancel` is `disabled={entregar.isPending}` (page.tsx:482), which blocks the Cancel *button*, but the underlying Radix `AlertDialog.Root` still responds to the Escape key and (per Radix's alert-dialog semantics, unlike a plain Dialog) — actually Radix `AlertDialog` intentionally disables outside-click dismissal by default, but Escape-key dismissal is NOT disabled unless `onEscapeKeyDown` is intercepted. Since `confirmOpen`/`onOpenChange={setConfirmOpen}` is a plain controlled boolean with no guard, pressing Escape while `entregar.isPending` is `true` calls `setConfirmOpen(false)` and closes the dialog while the PUT is still in flight. The mutation still resolves in the background and its `toast.success`/`toast.error` will fire after the user believes they cancelled — this isn't a data-integrity bug (the backend still enforces RBAC/state correctly) but it is a misleading UX/audit-trail issue given this is described as the highest-risk, irreversible action of the phase.
**Fix:** Intercept dismissal while pending:
```tsx
<AlertDialog
  open={confirmOpen}
  onOpenChange={(next) => {
    if (entregar.isPending) return;
    setConfirmOpen(next);
  }}
>
```

### WR-03: `ParecerEntregueBlock` silently and permanently shows "A carregar versão final..." if `versaoFinalId` points to a version not present in `versoes.data` (e.g. pagination, future soft-delete, or a data inconsistency), with no error state or retry affordance

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:510-519`
**Issue:** `versaoFinal = versoes?.find((v) => v.id === versaoFinalId)` returns `undefined` both while `versoes.data` is loading AND when it has finished loading but genuinely contains no matching row (e.g., a version was deleted out-of-band, or a future backend change paginates `versoes`). The current code can't distinguish these two cases — it always renders the transient "A carregar versão final..." copy, which becomes a permanently misleading message in the second case instead of a genuine error state. Not currently reachable given the current backend guarantees (versoes endpoint returns full unpaginated list, entrega only accepts an existing version id), but it's a latent trap for the next person who adds pagination or soft-delete to `versoes`.
**Fix:** Disambiguate on `versoes.isLoading`:
```tsx
{versoes.isLoading ? (
  <p className="text-sm text-slate-500 dark:text-slate-400">A carregar versão final...</p>
) : !versaoFinal ? (
  <p className="text-sm text-red-600">Não foi possível localizar a versão final entregue.</p>
) : ( /* ... */ )}
```

## Info

### IN-01: `defaultVersaoId` / `showEntregarTrigger` correctness relies on an unenforced ordering contract (`versoes.data` must be chronological ascending) that lives only in a PLAN.md comment, not in the type or the hook

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:421`
**Issue:** `versoes[versoes.length - 1].id` is used as the default "most recent version" — this is only correct because `useParecerVersoes` is documented (in the plan's interfaces block, not in code) to return "chronological ascending." Nothing in `use-pareceres.ts` or the `ParecerVersao[]` type enforces or asserts this ordering; if the backend ever changes sort order (or a future `orderBy` param is added), this silently picks the wrong "final" default with no runtime signal.
**Fix:** Either sort defensively by `createdAt`/`numeroVersao` client-side before taking the last element, or add a code comment at the call site referencing the ordering contract so a future ordering change is caught in review:
```tsx
// NOTE: relies on useParecerVersoes returning chronological-ascending order (see backend contract)
const defaultVersaoId = versoes && versoes.length > 0 ? versoes[versoes.length - 1].id : null;
```

### IN-02: `resolveUserNome` fallback returns the raw internal user ID (a UUID) to end users when `adminUsers` hasn't finished loading or the id isn't found

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:141-142`
**Issue:** `resolveUserNome` returns `userNomeById.get(userId) ?? userId` — if a user id genuinely isn't found in `adminUsers.data` (e.g., a deactivated/deleted user no longer returned by the admin-users listing), the UI displays a raw UUID string as the "author" in both the timeline and the "Parecer Entregue" block, rather than a graceful fallback. Cosmetic, not a security issue (no PII beyond an internal id, which isn't secret), but is user-facing noise.
**Fix:** Fall back to a neutral label instead of the raw id: `userNomeById.get(userId) ?? "Utilizador removido"`.

### IN-03: `entregaError` state on `EntregarParecerDialog` is never reset when the dialog is reopened after a prior failed attempt (only reset at the start of `handleEntregar`)

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:417-441`
**Issue:** If entrega fails once (`entregaError` set, dialog stays open per catch branch), then the user closes the dialog via Cancel without retrying, and reopens it later, the stale error message from the previous attempt is still displayed until the user clicks Confirm again (since `entregaError` is only cleared at the top of `handleEntregar`, not on dialog open/close). Minor UX confusion, not a correctness bug.
**Fix:** Clear `entregaError` in the `AlertDialog`'s `onOpenChange` handler when reopening, or reset all local state on open transition.

---

_Reviewed: 2026-07-01_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
