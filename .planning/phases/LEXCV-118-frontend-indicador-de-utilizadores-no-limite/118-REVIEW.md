---
phase: LEXCV-118-frontend-indicador-de-utilizadores-no-limite
reviewed: 2026-07-29T05:17:41Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/AuthController.java
  - web/src/types/auth.ts
  - web/src/app/(dashboard)/settings/page.tsx
  - web/scripts/verify-limite-utilizadores-indicator.mjs
findings:
  critical: 0
  warning: 4
  info: 3
  total: 7
status: issues_found
---

# Phase LEXCV-118-frontend-indicador-de-utilizadores-no-limite: Code Review Report

**Reviewed:** 2026-07-29T05:17:41Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found (0 blocking)

## Summary

Phase 118 extends `GET /api/v1/auth/me` with `tenant_plano`/`tenant_limite_utilizadores` and adds an "X/Y utilizadores" indicator plus a disabled-button-with-firing-tooltip to `UserManagementTab`. I read all four files in full, cross-referenced them against `AdminController.limiteUtilizadoresExcedido` (the backend's authoritative 409 enforcement, Phase 117), the dedicated `AuthControllerGetMeTenantPlanoTest`, and the phase's own planning artifacts (`118-CONTEXT.md`, `118-UI-SPEC.md`, `118-02-PLAN.md`, `118-02-SUMMARY.md`), then independently re-ran `pnpm verify:limite-utilizadores` (8/8 PASS) and `pnpm lint` (0 errors, the same 18 pre-existing warnings, none in the touched files) rather than trusting the SUMMARY's claims.

Verdicts on the four areas flagged for particular attention:

1. **Exposing `tenant_plano`/`tenant_limite_utilizadores` to every authenticated role via `/auth/me`** — appropriate and consistent with the existing `tenant_nome`/`tenant_logo_data_url` precedent; `/auth/me` itself is correctly gated behind authentication only (not `permitAll`, confirmed in `SecurityConfig`), and this was a deliberate, threat-modeled decision (`118-02-PLAN.md` `T-118-07`, disposition "accept"). See WR-04 for the residual least-privilege trade-off worth recording, and IN-01 for a related dead-code observation specific to `tenant_plano`.
2. **Disabled-Button + Tooltip composition** — correct. `TooltipProvider` is mounted once at the app root (`providers.tsx:30`), the `<span tabIndex={0}>` wraps the natively-`disabled` `<Button>` as the real `TooltipTrigger asChild` target (required because `buttonVariants` bakes in `disabled:pointer-events-none`), and the ordering/adjacency is independently verified by the executable `span-wrapper-tooltip` gate, which I re-ran and confirmed passing. See IN-02 for a minor, hedged accessible-naming nuance and IN-03 for a gap in the gate script's own rigor.
3. **Generalized `API NNN: ` prefix-stripping regex** — the regex itself (`/^API \d{3}: /`) is correct: anchored, matches the exact `API ${status}: ` prefix `apiFetch` always throws, and status codes are always 3 digits. See WR-03 for a real (but pre-existing and explicitly out-of-scope-by-plan) duplicate-toast side effect that this exact code path inherits.
4. **`null`-means-unlimited semantics** — preserved correctly at every hop for `tenant_limite_utilizadores` specifically (`Tenant.limiteUtilizadores` → `UserResponse.tenant_limite_utilizadores` → `MeResponse.tenant_limite_utilizadores?: number | null` → `?? null` → `!== null` comparison, never a truthy/falsy shortcut, `0` handled correctly). However, the sibling field `tenant_plano` does **not** get the same `| null` treatment in its TypeScript type despite the backend provably sending `null` for it — see WR-01, the most concrete finding in this review.

No Critical findings. The four Warnings below split into two genuinely new, actionable observations (WR-01, WR-02) and two real-but-consciously-accepted trade-offs that this repo's review convention (see `117-REVIEW.md` WR-01/WR-02) records rather than omits (WR-03, WR-04).

## Warnings

### WR-01: `tenant_plano`'s frontend type omits `| null`, contradicting the backend's own tested contract

**File:** `web/src/types/auth.ts:29`

**Issue:** `tenant_plano?: string;` claims the field is `string | undefined` — never `null`. But `AuthController.getMe()` explicitly sends `null` when the tenant has no plan:

```java
// backend/src/main/java/com/lexcv/controllers/AuthController.java:172-173
response.setTenant_plano(t.getPlano() != null ? t.getPlano().name() : null);
response.setTenant_limite_utilizadores(t.getLimiteUtilizadores());
```

and this exact case is dedicated-tested: `AuthControllerGetMeTenantPlanoTest.getMe_comPlanoNull_naoLancaNullPointerException` asserts `assertNull(body.getTenant_plano())`. Because `apiFetch` does an unchecked `(await res.json()) as TResponse` with no runtime schema validation, `me.tenant_plano` really can be `null` at runtime while its static type says it can't be. The very next line in the same interface, `tenant_limite_utilizadores?: number | null;`, correctly carries `| null` for the identical nullable-tenant-column pattern — the asymmetry confirms this is an omission, not an intentional choice. Currently latent (no code in `web/src` reads `tenant_plano` — see IN-01), so nothing crashes today, but the first future consumer that does `me.tenant_plano.toLowerCase()` or similar without a null guard (Phase 120, per `118-02-SUMMARY.md`, is expected to touch this exact area next) will get an unguarded runtime `TypeError` that TypeScript should have caught.

**Fix:**
```typescript
export interface MeResponse {
  // ...
  tenant_plano?: string | null;
  tenant_limite_utilizadores?: number | null;
}
```

### WR-02: New "X/Y utilizadores" indicator silently reports "0 utilizadores" (and leaves "Novo Utilizador" enabled) when the user list fails to load

**File:** `web/src/app/(dashboard)/settings/page.tsx:181, 202-211, 338-344`

**Issue:** `const { data: users, isLoading } = useAdminUsers();` never destructures `isError`. When `GET /admin/users` fails — network error, backend 5xx, or a permission mismatch (a user granted only the `users:manage` custom permission override, via the RBAC tab's own "Permissões Customizadas" feature, satisfies the frontend's `hasUsersManage = can.manage("users") || isAdmin` gate but not `AdminController`'s class-level `@PreAuthorize("hasRole('ADMIN')")`, so the request would 403) — TanStack Query settles with `isLoading: false`, `data: undefined`, `isError: true`. Nothing in `UserManagementTab` branches on that: execution falls through the `if (isLoading) return <Loader2 .../>` guard and renders the full card. `activeUserCount = users?.filter((u) => u.ativo === true).length ?? 0` silently becomes `0`, so the new counter confidently reads "0 utilizadores" (or "0/Y utilizadores"), and because `0 >= Y` is false for any real limit, "Novo Utilizador" stays fully enabled. This inverts the fail-safe posture the rest of the feature is careful about (an unknown/absent limit is deliberately never treated as "0 seats," per `118-CONTEXT.md`) — here an unknown/failed *count* is presented as "definitely zero, plenty of headroom" instead of "unknown." The backend 409 remains the authoritative gate (no over-limit user can actually be persisted), so this is a display/UX correctness bug, not a data-integrity one — but it is misleading exactly when an admin needs the number to be trustworthy.

**Fix:**
```tsx
const { data: users, isLoading, isError } = useAdminUsers();
// ...
if (isLoading) {
  return ( /* existing Loader2 */ );
}
if (isError) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 h-48 text-center px-4">
      <AlertCircle className="h-6 w-6 text-red-500" />
      <p className="text-sm text-slate-600 dark:text-slate-400">
        Não foi possível carregar a lista de utilizadores.
      </p>
    </div>
  );
}
```
(`AlertCircle` is already imported in this file for the Notifications tab's equivalent error state, so this mirrors an existing pattern rather than introducing a new one.)

### WR-03: `handleFormSubmit`'s local error toast duplicates the automatic toast `apiFetch` already shows — including for the new 409 "limite atingido" path

**File:** `web/src/app/(dashboard)/settings/page.tsx:310-317`

**Issue:** `apiFetch` (`web/src/lib/api.ts:43-44`) already calls `toast.error(...)` for every failed request except 401/403. `handleFormSubmit`'s `catch` block unconditionally calls `toast.error(msg)` again after stripping the prefix:
```tsx
} catch (err: unknown) {
  const msg =
    err instanceof Error
      ? err.message.replace(/^API \d{3}: /, "")
      : "Erro ao gravar dados.";
  setMessage({ text: msg || "Erro ao gravar dados.", type: "error" });
  toast.error(msg || "Erro ao gravar dados.");
}
```
Any create/update-user failure — including the 409 this exact phase adds UI for — stacks two Sonner toasts: "Erro 409: Limite de utilizadores atingido para o vosso plano." (from `apiFetch`) and "Limite de utilizadores atingido para o vosso plano." (from this block). This is pre-existing, file-wide behavior, not introduced by this phase, and it is explicitly, knowingly out of scope: `118-02-PLAN.md`'s Task 1 forbids touching `api.ts` and states verbatim "o duplo-toast (automático + local) é comportamento pré-existente para todos os erros e não é alterado por esta fase." Recorded here (per this repo's own review convention — see `117-REVIEW.md` WR-01/WR-02 for the same "accepted, documented trade-off" treatment) because the regex change under review in this pass sits directly inside the block that produces it, and the 409 flow this phase surfaces is the most likely real-world trigger.

**Fix:** No action required this phase — matches the explicit, documented scope decision above. If ever addressed, drop the second `toast.error(msg)` call here (keep only `setMessage(...)` for the inline banner) rather than duplicating `apiFetch`'s own toast, or centralize prefix-stripping inside `apiFetch` itself so every call site in the app gets a single clean toast for free.

### WR-04: `tenant_plano`/`tenant_limite_utilizadores` reach every authenticated role via `/auth/me`, not just ADMIN/`users:manage`

**File:** `backend/src/main/java/com/lexcv/controllers/AuthController.java:169-174`

**Issue:** `getMe()` has no `@PreAuthorize`/role check by design (it's the self-info endpoint), so any authenticated user — including an ASSISTENTE with no administrative permissions — can read the tenant's subscription plan and contracted user-seat limit simply by calling `GET /api/v1/auth/me`. Unlike `tenant_nome`/`tenant_logo_data_url` (consumed broadly for branding across the authenticated app, so universal exposure has a genuine cross-role use case — confirmed via `dashboard-shell.tsx` and other consumers), `tenant_plano`/`tenant_limite_utilizadores` currently have exactly one consumer in the entire frontend (`UserManagementTab`, gated behind `hasUsersManage`); every other role receives billing-tier/seat-capacity data with no legitimate use for it. This is a deliberate, threat-modeled decision (`118-CONTEXT.md` "Backend gap" section; `118-02-PLAN.md` `T-118-07`, disposition "accept" — reasoning: mirrors the `tenant_nome` precedent, low sensitivity, render surface stays client-side-gated), and `/auth/me` itself is correctly behind authentication (confirmed in `SecurityConfig`, not in the `permitAll()` list). Recording as a Warning per this codebase's own convention for consciously-accepted trade-offs: CLAUDE.md states the `scope:action` RBAC pattern requires "both layers must agree," and this is a case where only the frontend layer gates the *display* of tenant-capacity data while the backend gates none of its *exposure* — a nominal (if low-impact) exception to that stated rule.

**Fix:** No action required this phase — accept as documented, low-sensitivity, precedent-consistent. If tightened later, either scope these two fields to principals satisfying `hasRole('ADMIN')`/`users:manage` before populating them in `getMe()`, or formally document `/auth/me` as an intentional exception to CLAUDE.md's "both layers must agree" rule for non-PII, plan-capacity-only fields.

## Info

### IN-01: `tenant_plano` is fully wired (backend DTO, `/auth/me` response, TypeScript type) but has zero frontend consumers

**File:** `web/src/types/auth.ts:29`; `backend/src/main/java/com/lexcv/controllers/AuthController.java:172`

**Issue:** A repo-wide search (`grep -rn "tenant_plano" web/src`) finds only the type declaration itself — no component reads `me.tenant_plano` anywhere. This is intentional forward-scaffolding (the two fields were added together per the JSON contract Plan 01 delivered, likely anticipating Phase 120's plan/limit-editing UI), but as shipped it is exposed, backend-tested, typed dead data with no current reader.

**Fix:** No action required if a near-term consumer is confirmed (e.g. Phase 120 PROV-04). Otherwise, consider deferring `tenant_plano`'s addition to `/auth/me` until it has one, to minimize speculative surface (see also WR-04).

### IN-02: The focusable tooltip-trigger `<span>` around the disabled "Novo Utilizador" button carries no accessible name of its own

**File:** `web/src/app/(dashboard)/settings/page.tsx:386-402`

**Issue:** `<span tabIndex={0}>` is the real `TooltipTrigger asChild` target (necessarily so — `disabled:pointer-events-none` on `Button` would otherwise kill hover/focus, and the executable gate correctly enforces this wrapper exists). `118-02-PLAN.md`'s Task 2 explicitly decided against adding any manual ARIA attribute to the span, reasoning that Radix already wires `aria-describedby` from the trigger to the tooltip content. `aria-describedby` supplies a *description*, not an accessible *name*, though — worth validating with an actual screen reader (NVDA/VoiceOver) that focusing the span announces something meaningful (e.g. via name-from-content of the nested disabled `<button>`'s visible text) rather than nothing, since cross-engine behavior for a bare, unnamed, tabbable `<span>` wrapping a `disabled` button is not fully consistent. Flagged as a hedged, low-confidence observation, not an asserted defect — the plan's reasoning may well be correct in practice.

**Fix (optional, only if manual verification shows a gap):**
```tsx
<span tabIndex={0} aria-label="Novo Utilizador (limite atingido)">
```

### IN-03: `span-wrapper-tooltip` gate's `hasDisabled` check doesn't confirm `disabled` is actually on the wrapped `<Button>`

**File:** `web/scripts/verify-limite-utilizadores-indicator.mjs:144, 149`

**Issue:**
```js
const hasDisabled = /\bdisabled\b/.test(block);
```
This tests for the word "disabled" anywhere in the whole delimited `<Tooltip>...</Tooltip>` block, not specifically as a JSX attribute on the `<Button>` element. It passes correctly today because the only occurrence happens to be `<Button disabled ...>`, but it would equally pass if `disabled` appeared in an unrelated class name or string elsewhere in the same block, which would silently weaken this gate's one job: catching a future regression where the `disabled` attribute is accidentally dropped from `Button` while some other "disabled"-containing token remains nearby in the same Tooltip.

**Fix:**
```js
const hasDisabled = /<Button\b[^>]*\bdisabled\b/.test(block);
```

---

_Reviewed: 2026-07-29T05:17:41Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
