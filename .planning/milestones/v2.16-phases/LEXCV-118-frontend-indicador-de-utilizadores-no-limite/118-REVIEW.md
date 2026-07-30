---
phase: LEXCV-118-frontend-indicador-de-utilizadores-no-limite
reviewed: 2026-07-29T05:55:53Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/AuthController.java
  - web/src/types/auth.ts
  - web/src/app/(dashboard)/settings/page.tsx
  - web/scripts/verify-limite-utilizadores-indicator.mjs
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase LEXCV-118-frontend-indicador-de-utilizadores-no-limite: Code Review Report

**Reviewed:** 2026-07-29T05:55:53Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found (0 blocking) — FINAL RE-REVIEW after fix pass

## Summary

This is the final re-review of Phase 118, run after a targeted fix pass addressing two Warnings from the prior review (`118-REVIEW.md`, reviewed 2026-07-29T05:17:41Z): **WR-01** (`tenant_plano`'s frontend type omitted `| null`) and **WR-02** (`UserManagementTab` never surfaced `useAdminUsers`'s `isError` state). Per the requesting instructions, WR-03/WR-04 were left as-is (accepted trade-offs, not re-litigated unless materially changed — confirmed they weren't) and IN-01/IN-02/IN-03 were left untouched in substance (out of scope), carried forward here with line-number citations refreshed only where WR-02's fix physically shifted later code in `settings/page.tsx` down by 15 lines.

**Methodology:** re-read all four files in full at current HEAD; located and independently diffed the two fix commits (`6ff042b` — WR-01, `a18217c` — WR-02); re-ran `node scripts/verify-limite-utilizadores-indicator.mjs` (8/8 PASS, including both assertions that changed); ran `npx eslint` against both touched files (0 errors — the sole warning, `@next/next/no-img-element` on the pre-existing avatar `<img>` in `settings/page.tsx`, predates both fixes and sits outside both diffs); ran `npx tsc --noEmit` (0 errors touching either reviewed file — 3 pre-existing `vitest`-module-resolution errors in unrelated `*.test.ts` files, outside review scope and outside both diffs); re-grepped `tenant_plano` / `tenant_limite_utilizadores` across `web/src` to confirm consumer counts are unchanged; and confirmed via `git log` that `AuthController.java` and `UserResponse.java` are untouched since before the original review (last touched `fa5cf83`, 2026-07-29 02:52 local — over two hours before the fix commits), so WR-04's backend analysis required no re-verification.

**WR-01 — VERIFIED FIXED, correct and complete, no regressions.**
**WR-02 — VERIFIED FIXED, correct and complete, no regressions.**
See "Resolved Since Previous Review" below for the evidence trail per finding.

**WR-03 and WR-04 — unchanged**, carried forward verbatim as accepted, documented trade-offs; their cited source lines are confirmed byte-identical to the prior review (neither `handleFormSubmit`'s catch block nor `AuthController.getMe()`'s tenant block were touched by this fix pass, and `web/src/lib/api.ts` — WR-03's other reference — hasn't changed since June).

**IN-01, IN-02, IN-03 — unchanged in substance**; IN-02 and IN-03's line citations are refreshed to match the current file state.

One new, narrowly-scoped observation surfaced during this pass's full re-read of `settings/page.tsx` and is recorded as **WR-05**: `RbacTab` — a different tab in the same file, untouched by either fix and outside Phase 118's "X/Y utilizadores" feature scope — has no `isError` branch at all. Unlike the pre-fix `UserManagementTab`, its failure mode isn't a misleading count; it's a permanent, un-escapable loading spinner. This is pre-existing, was not introduced by this fix pass, and does not block Phase 118 — recorded here for a fast-follow rather than left undiscovered.

## Final Verdict — Phase 118: APPROVED TO SHIP

No Critical findings, now or previously. Both WR-01 and WR-02 are verified correct, complete, and regression-free through independent re-execution of the project's own verification script, linter, and type checker, plus direct source inspection, a fresh consumer-count grep, and a git-history check confirming the backend was never in the blast radius of this fix pass. Nothing else in the four reviewed files regressed. The phase is safe to close: the two carried-forward Warnings (WR-03, WR-04) remain intentional, already-adjudicated trade-offs from the original review, and the newly-surfaced WR-05 belongs to a different tab/feature than Phase 118 delivered and does not block it.

## Resolved Since Previous Review

### WR-01 (RESOLVED): `tenant_plano`'s frontend type omitted `| null`

**File:** `web/src/types/auth.ts:29`
**Fix commit:** `6ff042b` — "fix(118): WR-01 add missing null to tenant_plano type"

**Verification performed:**
- Current line 29 reads `tenant_plano?: string | null;`, now matching sibling field `tenant_limite_utilizadores?: number | null;` (line 30) — the asymmetry the original finding was built on is gone.
- Re-confirmed the full backend chain that motivated the finding is unchanged and still consistent with the new type: `UserResponse.tenant_plano` is `private String tenant_plano;` (nullable reference type, `backend/src/main/java/com/lexcv/dtos/UserResponse.java:26`); `AuthController.getMe()` still assigns `t.getPlano() != null ? t.getPlano().name() : null` (`AuthController.java:172`, byte-identical to the prior review); `AuthControllerGetMeTenantPlanoTest.java` still exists at `backend/src/test/java/com/lexcv/controllers/` and is untouched.
- `grep -rn "tenant_plano" web/src` still returns only the type declaration itself — no consumers exist, so widening the type to include `null` has zero ripple effect on any call site (nothing to break).
- The verify script's own `types-auth-tenant-plano` assertion was updated in the same commit (it previously asserted the pre-fix `tenant_plano?: string;` substring, which would otherwise now fail against the corrected type) — re-ran the script and confirmed `PASS types-auth-tenant-plano`.
- `npx tsc --noEmit`: no errors touching `auth.ts` or any of its consumers.

**Verdict:** Correct and complete. No regressions.

### WR-02 (RESOLVED): `UserManagementTab` didn't surface `useAdminUsers`'s error state

**File:** `web/src/app/(dashboard)/settings/page.tsx:181, 346-359`
**Fix commit:** `a18217c` — "fix(118): WR-02 surface error state when user list fails to load"

**Verification performed:**
- Line 181 now destructures `isError` and `refetch`: `const { data: users, isLoading, isError, refetch } = useAdminUsers();`.
- A new guard at lines 346-359 returns early on `isError`, before `filteredUsers` is computed (line 361) and before the indicator/button/table JSX (line 367 onward) can render — so the entire "X/Y utilizadores" card, including the create-button gate, is now correctly unreachable while the fetch is in a failed state. This closes the exact defect WR-02 described: a failed `GET /admin/users` can no longer render a false "0 utilizadores" with "Novo Utilizador" left enabled.
- Directly compared the new block against the pre-existing `NotificationPreferencesTab` error state in the same file (lines 957-970) rather than trusting the commit message's claim of a match — they are structurally identical (`AlertCircle` icon + message paragraph + `Button variant="outline" size="sm" onClick={() => refetch()}` with `RotateCcw` + "Tentar novamente"), differing only in the message copy ("lista de utilizadores" vs. "preferências de notificação").
- `useAdminUsers()` (`web/src/hooks/use-admin.ts:7-16`) is a bare `useQuery(...)` passthrough with no custom return-shape narrowing, so `isError`/`refetch` are genuine, correctly-typed members of its return value — confirmed by reading the hook, not assumed.
- `AlertCircle`, `RotateCcw`, and `Button` were already imported in this file before the fix; no new imports needed, none missing.
- Checked adjacent logic for regressions: `activeUserCount`/`tenantUserLimit`/`atUserLimit`/`userCountLabel` (lines 202-211) are computed *before* the new guard, using `users?.filter(...) ?? 0`, so they remain crash-safe regardless of `isError`; they are simply never rendered when `isError` short-circuits the return, since they live below the new guard in the JSX. `RbacTab`'s own `refetch` (from a separate `useAdminRbac()` call, separate component scope) does not collide with this one. On TanStack Query v5 (confirmed via `web/package.json`), a failed *background* refetch after a prior successful load does not flip `isError` back to `true` while cached data still exists, so this new guard cannot unexpectedly interrupt an in-progress edit/create form session.
- Re-ran `node scripts/verify-limite-utilizadores-indicator.mjs`: 8/8 PASS — none of the 8 assertions target this exact branch, but none regressed either.
- `npx eslint` / `npx tsc --noEmit`: no new errors attributable to this change.

**Verdict:** Correct and complete. No regressions.

## Warnings

### WR-03: `handleFormSubmit`'s local error toast duplicates the automatic toast `apiFetch` already shows — including for the 409 "limite atingido" path

**File:** `web/src/app/(dashboard)/settings/page.tsx:310-317`

**Status:** Unchanged since the prior review. Confirmed byte-identical (not touched by either fix commit); `web/src/lib/api.ts` (the other file this finding depends on) hasn't changed since 2026-06-17. Carried forward per instruction — not re-flagged as new, not re-analyzed.

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
Any create/update-user failure — including the 409 this phase adds UI for — stacks two Sonner toasts. This is pre-existing, file-wide behavior, not introduced by this phase, and is explicitly, knowingly out of scope per `118-02-PLAN.md` Task 1 ("o duplo-toast (automático + local) é comportamento pré-existente para todos os erros e não é alterado por esta fase").

**Fix:** No action required this phase — matches the explicit, documented scope decision. If ever addressed: drop the second `toast.error(msg)` call here (keep only `setMessage(...)` for the inline banner), or centralize prefix-stripping inside `apiFetch` itself.

### WR-04: `tenant_plano`/`tenant_limite_utilizadores` reach every authenticated role via `/auth/me`, not just ADMIN/`users:manage`

**File:** `backend/src/main/java/com/lexcv/controllers/AuthController.java:169-174`

**Status:** Unchanged since the prior review. `AuthController.java` has had no commits since `fa5cf83` (2026-07-29 02:52 local), which predates both fix commits and the original review itself. Carried forward per instruction — not re-flagged as new, not re-analyzed.

**Issue:** `getMe()` has no `@PreAuthorize`/role check by design (self-info endpoint), so any authenticated user — including an ASSISTENTE with no administrative permissions — can read the tenant's subscription plan and contracted user-seat limit via `GET /api/v1/auth/me`. Unlike `tenant_nome`/`tenant_logo_data_url` (consumed broadly for branding, genuine cross-role use case), `tenant_plano`/`tenant_limite_utilizadores` currently have exactly one consumer in the entire frontend (`UserManagementTab`, gated behind `hasUsersManage`). This is a deliberate, threat-modeled decision (`118-CONTEXT.md` "Backend gap" section; `118-02-PLAN.md` `T-118-07`, disposition "accept"), and `/auth/me` itself is correctly behind authentication (not in `SecurityConfig`'s `permitAll()` list).

**Fix:** No action required this phase — accept as documented, low-sensitivity, precedent-consistent. If tightened later, either scope these two fields to principals satisfying `hasRole('ADMIN')`/`users:manage` before populating them in `getMe()`, or formally document `/auth/me` as an intentional exception to CLAUDE.md's "both layers must agree" rule for non-PII, plan-capacity-only fields.

### WR-05 (NEW — outside Phase 118's feature scope, non-blocking): `RbacTab` has no error state at all; a failed `GET /admin/rbac` produces a permanent, un-escapable loading spinner

**File:** `web/src/app/(dashboard)/settings/page.tsx:761, 769-778`

**Issue:** `RbacTab` (the "Controlo de Acesso (RBAC)" tab, adjacent to but distinct from `UserManagementTab`) destructures `useAdminRbac()` without `isError`:
```tsx
const { data: rbac, isLoading, refetch } = useAdminRbac();
...
const effectiveRolePermissions =
  localRolePermissions ?? (rbac?.rolePermissions as RolePermissionsMap | undefined);

if (isLoading || !effectiveRolePermissions) {
  return (
    <div className="flex justify-center items-center h-48">
      <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
    </div>
  );
}
```
When `GET /admin/rbac` fails (network error, 5xx, or a permission mismatch), TanStack Query settles with `isLoading: false`, `data: undefined`, `isError: true`. Because `rbac` is `undefined` and `localRolePermissions` is still `null` (the user never got a chance to interact), `effectiveRolePermissions` evaluates to `undefined`, so `!effectiveRolePermissions` stays `true` forever — the component is permanently stuck in the *same* branch as the loading state, rendering an infinite spinner with no error message and no retry button. This is a strictly worse failure mode than the pre-fix `UserManagementTab` bug (WR-02): there, the UI at least rendered something interactive (if misleading); here, the tab becomes permanently unusable until a full page reload. `refetch` is captured but only ever invoked later, from `handleSave`'s success path (line 813) — there is no user-facing way to trigger it from the stuck loading state.

This is pre-existing (not introduced by the WR-01/WR-02 fix commits — neither touches `RbacTab`) and sits outside Phase 118's delivered feature ("X/Y utilizadores" indicator in `UserManagementTab`), so it does not block this phase. Recorded here because it was directly observed during this pass's mandated full re-read of `settings/page.tsx`, and per this review's own adversarial mandate it should be surfaced rather than left for someone to rediscover.

**Fix:**
```tsx
function RbacTab() {
  const { data: rbac, isLoading, isError, refetch } = useAdminRbac();
  ...
  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 h-48 text-center px-4">
        <AlertCircle className="h-6 w-6 text-red-500" />
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Não foi possível carregar a matriz de permissões (RBAC).
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RotateCcw className="h-4 w-4" />
          Tentar novamente
        </Button>
      </div>
    );
  }

  if (!effectiveRolePermissions) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
      </div>
    );
  }
  ...
```
(Mirrors the exact pattern now used by both `NotificationPreferencesTab` and, post-WR-02-fix, `UserManagementTab` — three near-identical hand-written copies of this block now exist in the same file; a shared `<QueryErrorState onRetry={refetch} message={...} />` component would be a reasonable follow-up to avoid a fourth divergence, but that is a suggestion, not a requirement of this fix.)

## Info

### IN-01: `tenant_plano` is fully wired (backend DTO, `/auth/me` response, TypeScript type) but has zero frontend consumers

**File:** `web/src/types/auth.ts:29`; `backend/src/main/java/com/lexcv/controllers/AuthController.java:172`

**Status:** Unchanged in substance; left untouched per instruction. Re-confirmed via a fresh grep that the consumer count is still zero even after WR-01 widened the type to `string | null` (a type change alone cannot add a consumer).

**Issue:** A repo-wide search (`grep -rn "tenant_plano" web/src`) finds only the type declaration itself — no component reads `me.tenant_plano` anywhere. This is intentional forward-scaffolding (likely anticipating a future plan/limit-editing UI), but as shipped it is exposed, backend-tested, typed dead data with no current reader.

**Fix:** No action required if a near-term consumer is confirmed. Otherwise, consider deferring `tenant_plano`'s addition to `/auth/me` until it has one (see also WR-04).

### IN-02: The focusable tooltip-trigger `<span>` around the disabled "Novo Utilizador" button carries no accessible name of its own

**File:** `web/src/app/(dashboard)/settings/page.tsx:402-418` (previously cited as `386-402`; shifted by WR-02's 16-line insertion earlier in the file — same code, refreshed citation only)

**Status:** Unchanged in substance; left untouched per instruction.

**Issue:** `<span tabIndex={0}>` (line 404) is the real `TooltipTrigger asChild` target (necessarily so — `disabled:pointer-events-none` on `Button` would otherwise kill hover/focus). `118-02-PLAN.md`'s Task 2 explicitly decided against adding a manual ARIA attribute to the span, reasoning that Radix already wires `aria-describedby` from the trigger to the tooltip content. `aria-describedby` supplies a *description*, not an accessible *name*, though — worth validating with an actual screen reader (NVDA/VoiceOver) that focusing the span announces something meaningful rather than nothing. Flagged as a hedged, low-confidence observation, not an asserted defect.

**Fix (optional, only if manual verification shows a gap):**
```tsx
<span tabIndex={0} aria-label="Novo Utilizador (limite atingido)">
```

### IN-03: `span-wrapper-tooltip` gate's `hasDisabled` check doesn't confirm `disabled` is actually on the wrapped `<Button>`

**File:** `web/scripts/verify-limite-utilizadores-indicator.mjs:145` (previously cited as `144`; the WR-01 commit edited an unrelated, earlier assertion block in the same file — same code, refreshed citation only)

**Status:** Unchanged in substance; left untouched per instruction. Confirmed the WR-01 fix commit did not touch the `span-wrapper-tooltip` assertion (lines 133-151) — only the earlier, unrelated `types-auth-tenant-plano` assertion.

**Issue:**
```js
const hasDisabled = /\bdisabled\b/.test(block);
```
This tests for the word "disabled" anywhere in the whole delimited `<Tooltip>...</Tooltip>` block, not specifically as a JSX attribute on the `<Button>` element. It passes correctly today because the only occurrence happens to be `<Button disabled ...>`, but it would equally pass if "disabled" appeared in an unrelated class name or string elsewhere in the same block, weakening this gate's ability to catch a future regression where the `disabled` attribute is accidentally dropped from `Button`.

**Fix:**
```js
const hasDisabled = /<Button\b[^>]*\bdisabled\b/.test(block);
```

---

_Reviewed: 2026-07-29T05:55:53Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_This is a final re-review after a fix pass; see "Resolved Since Previous Review" for WR-01/WR-02 verification evidence._
