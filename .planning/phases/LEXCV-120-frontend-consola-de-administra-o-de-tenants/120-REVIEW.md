---
phase: LEXCV-120-frontend-consola-de-administra-o-de-tenants
reviewed: 2026-07-29T16:00:00Z
depth: deep
files_reviewed: 14
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Tenant.java
  - backend/migrations/120-add-tenant-ativo.sql
  - backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java
  - backend/src/main/java/com/lexcv/controllers/AuthController.java
  - backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
  - backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java
  - backend/src/main/java/com/lexcv/dtos/TenantUpdateRequest.java
  - backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java
  - web/src/types/auth.ts
  - web/src/types/platform-admin.ts
  - web/src/hooks/use-platform-admin.ts
  - web/src/components/shared/dashboard-shell.tsx
  - web/src/app/(dashboard)/plataforma/page.tsx
  - web/src/app/(dashboard)/plataforma/columns.tsx
  - web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx
findings:
  critical: 1
  warning: 3
  info: 3
  total: 7
status: issues_found
---

# Phase LEXCV-120: Code Review Report

**Reviewed:** 2026-07-29T16:00:00Z (round 1) / round-2 verification 2026-07-29
**Depth:** deep
**Files Reviewed:** 14
**Status:** issues_found (0 blocking — see Final Verdict)

## Summary

This review traced the tenant-suspension mechanism across its three enforcement points
(`AuthController.login`, `AuthController.refresh`, `JwtAuthenticationFilter`), the three new
`PlatformAdminController` endpoints, and the new `/plataforma` console end-to-end, plus the
call chains those files feed into (`SetupService`, `DatabaseSeeder`, `UserPrincipal`,
`SecurityConfig`, `AlertasDiariosJob`, `AdminController`).

**What checks out:**
- **Suspension enforcement (3 paths).** Login, refresh, and the per-request filter all
  independently re-check `tenant.getAtivo()` with the same null-safe `Boolean.TRUE.equals(...)`
  pattern and fail closed (missing tenant == suspended). No 4th token-minting/validating path was
  found — `SetupController` never issues cookies, `/auth/me` and `/auth/change-password` only ever
  read the `SecurityContext` the filter already gated, and there is no WebSocket/SSE/query-string
  token endpoint anywhere in the backend.
- **Reserved "LexCV" tenant guard.** Enforced backend-authoritatively in
  `PlatformAdminController.setTenantAtivo` (`TENANT_RESERVADO.equals(tenant.getNome()) &&
  !novoAtivo` → 400) independently of the frontend's disabled button; a crafted `PATCH` bypassing
  the UI would still be rejected. The frontend mirror (`columns.tsx`, `page.tsx`) uses the same
  literal and degrades safely.
- **Response DTOs.** `TenantAdminSummaryResponse`/`TenantProvisionResponse` are minimal
  projections (no password hash, NIF, email, telefone, or logo `@Lob`) — no cross-tenant data
  leakage to a `PLATAFORMA_ADMIN`.
- **No permission/role-escalation regression.** Nothing in the reviewed files trusts a
  client-supplied role/permission string; `JwtAuthenticationFilter` and `AuthController` always
  re-derive `roles`/`permissions` from the `User`/`Role` DB entities, never from the JWT's own
  `roles` claim, so the Phase 119 `User.permissions`-array bypass class of bug is not reintroduced
  here.

One **Critical** finding remains: every tenant this application can currently create (including
the reserved `LexCV` platform tenant itself) is provisioned with `plano = NULL`, and the new
admin console's types/UI never account for that — this is not a hypothetical edge case, it is
independently confirmed against the real dev database by this phase's own `120-HUMAN-UAT.md`
(point 10: both existing tenants have `plano=NULL`). See CR-01.

## Round 2 — Fix Verification

A fix pass produced 7 commits addressing all 7 findings below (6 direct fixes + 1 follow-up
comment correction discovered during this same round-2 pass):
`2c55eac1` (CR-01), `f116b024` (WR-01), `8e544551` (WR-02), `57900ff1` (WR-03), `09c79146`
(IN-01), `6af129e2` (IN-02), `64ad226` (WR-01 comment-accuracy follow-up). IN-03 was deliberately
left unchanged (see its entry below).

This round independently re-read every diff (not just the fix commits' own messages), re-ran
`mvn test` (172/172, exit 0), `mvn spotbugs:check` (0 findings), `pnpm lint` (0 errors, 18
pre-existing warnings, none in touched files) and `pnpm build` (succeeds, `/plataforma` present in
the route list) directly from the real project checkout — not the fixer's isolated worktree, whose
own `pnpm build` run hit an unrelated sandbox path-resolution failure that does not reproduce here.
A second, independent adversarial review (fresh agent, no access to the fixer's own reasoning) then
verified each of the 6 direct fixes against the original finding. Verdicts below; full per-finding
detail follows in place in each section.

- **CR-01 — CONFIRMED-RESOLVED.** `Tenant.plano` now defaults to `STARTER` via `@Builder.Default`,
  mirroring `ativo` exactly. All three tenant-creation paths (`provisionTenant`,
  `initializeSystem`, `seedTenantPlataforma`) proven by new tests to persist `STARTER`, never
  `null`. Migration `120b-backfill-tenant-plano.sql` backfills existing rows and tightens the
  column to `NOT NULL`. Frontend `EditarTenantForm` seeds state with `tenant.plano ??
  PLANO_OPTIONS[0]` as defense in depth. Residual (non-blocking): the two pre-existing dev-DB rows
  stay `NULL` until an operator actually runs the migration script (this repo's standing manual-SQL
  convention, same as every other migration here); `columns.tsx`/`platform-admin.ts` were not
  additionally typed `| null`, but the `page.tsx` fallback means the previously-reported
  confusing-400 failure mode cannot recur regardless.
- **WR-01 — PARTIALLY RESOLVED.** The reported mechanical bug (session-id key changes every
  request → lockout never fires) is genuinely fixed and proven: `AuthControllerLoginLockoutTest`
  drives the real `login()` method 6 times through a stable mocked IP and shows the 6th attempt
  correctly gets `429`, plus a second test shows two different IPs don't share a lockout. This is a
  strict improvement over the pre-fix state (no throttle at all) and does not reintroduce the
  original vulnerability. However, this round's independent re-review found the fix's own comment
  claimed this backend has no reverse proxy in front of it — false for this repo's own shipped
  topology: `Caddyfile` fronts `/api/*` via `reverse_proxy backend:8080`, and `docker-compose.yml`
  additionally publishes the backend's own port directly to the host (`8089:8080`). In the
  Caddy-fronted path, `request.getRemoteAddr()` likely resolves to Caddy's constant container
  address rather than the real client IP, which collapses the lockout key to effectively
  per-email rather than per-attacker. Fixing this correctly requires first confirming, at the
  infrastructure level, that direct backend access is actually blocked externally in production —
  a fact not verifiable from source alone, and not safe to guess (trusting `X-Forwarded-For`
  without that guarantee would let an attacker with direct backend access forge the header and
  dodge the lockout entirely, worse than today). The comment was corrected to accurately describe
  this limitation (`64ad226`) rather than leave a misleading claim in place, and the deeper
  infrastructure verification + correct trusted-proxy configuration is tracked as a follow-up
  (session task `task_0ccb6ccf`) rather than guessed at here. Not a blocker: no regression, no new
  vulnerability, strictly better than the pre-existing state this finding described.
- **WR-02 — CONFIRMED-RESOLVED.** `AlertasDiariosJob` now skips any tenant where
  `!Boolean.TRUE.equals(tenant.getAtivo())` as the very first statement in its per-tenant loop, so
  100% of per-tenant side effects are skipped, not a subset. New tests prove a suspended tenant has
  zero interaction with any downstream repository/service, and that an active tenant elsewhere in
  the same run is unaffected.
- **WR-03 — CONFIRMED-RESOLVED.** `if (!me.isFetched) return null;` now precedes the role check,
  closing the fail-open window rather than narrowing it — confirmed against `useMe()`'s actual
  `isFetched` semantics (false until the query settles, true on both success and error), with no
  rules-of-hooks violation from the added early return. Non-blocking note: `clientes/page.tsx` and
  25 other dashboard pages still share the exact pre-existing `isFetched && !canX` pattern this
  finding called systemic; this fix was correctly scoped to `/plataforma` only, and the wider sweep
  is tracked separately (session task `task_a78e2d45`), not silently dropped.
- **IN-01 — CONFIRMED-RESOLVED.** Shared `tenantInitials()` helper extracted, verified
  byte-for-byte equivalent to both prior inline implementations.
- **IN-02 — CONFIRMED-RESOLVED.** Editar Tenant dialog's Cancel button now disables on the same
  `isSubmitting` flag already gating its Guardar button, matching the sibling AlertDialog.
- **IN-03 — deliberately unchanged.** Still maps `IllegalStateException` to `403` in
  `createTenant`; fixing it means either changing that mapping (which would break the existing
  `createTenant_comIllegalStateExceptionDevolve403` test) or introducing a dedicated exception
  type — a real design decision, not a mechanical fix, consistent with the original finding's own
  "low real impact" framing. No action taken this round.

Cross-cutting: none of the 6 fix commits touch `tenant_id` derivation or repository scoping, and
none trust client-supplied role/permission data — the Phase 119 escalation-bug class is not
reintroduced anywhere in this diff set.

## Critical Issues

### CR-01: Newly-provisioned tenants (including the reserved "LexCV" tenant) get `plano = NULL`, which the new console's types/UI assume can never happen (RESOLVED — see Round 2)

**Files:**
- `backend/src/main/java/com/lexcv/models/Tenant.java:35-37` (vs. `:59-61`)
- `backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java:31`
- `web/src/types/platform-admin.ts:15`
- `web/src/app/(dashboard)/plataforma/columns.tsx:159-166`
- `web/src/app/(dashboard)/plataforma/page.tsx:207-218, 411, 446-457`
- `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java:133-135`

**Issue:**

`Tenant.plano` has no `@Builder.Default` (unlike `ativo`, three fields below it in the exact same
class):

```java
// Tenant.java:35-37
@Enumerated(EnumType.STRING)
@Column(name = "plano")
private TenantPlano plano;               // no default, nullable in DB (migration 117 never added NOT NULL)

// Tenant.java:59-61 — the pattern this phase itself already applies for `ativo`
@Column(name = "ativo", nullable = false, columnDefinition = "boolean not null default true")
@Builder.Default
private Boolean ativo = true;
```

Every tenant-creation path builds a `Tenant` without ever calling `.plano(...)`:
`SetupService.initializeSystem`/`provisionTenant` (the exact endpoint this phase's "Criar Tenant"
panel calls) and `DatabaseSeeder.seedTenantPlataforma()` (which unconditionally creates the
reserved `LexCV` tenant on every boot). The result is `plano = NULL` in the database for every
tenant this application has ever created, unless a `PLATAFORMA_ADMIN` separately runs `PUT
/platform/tenants/{id}` afterward.

This is not hypothetical: `120-HUMAN-UAT.md` point 10 independently confirms it against the real
dev database — *"`SELECT` final confirma exatamente os 2 tenants originais ('Escritorio A',
'LexCV'), ambos com `plano=NULL`... idêntico ao estado registado no início da Task 1."*

The new console's types/components assume `plano` is always a valid enum member:

```ts
// platform-admin.ts:15 — no `| null`
export type TenantAdminSummary = { id: string; nome: string; plano: TenantPlano; ... };
```

```tsx
// columns.tsx:159-166 (desktop table)
const plano = row.original.plano;
return <Badge variant={PLANO_BADGE_VARIANT[plano]} ...>{plano}</Badge>;
// PLANO_BADGE_VARIANT[null] -> undefined -> Badge silently falls back to its
// cva "secondary" default and renders no label text at all.
```

```tsx
// page.tsx:207-218 (mobile cards) — worse: silently mis-colors as ENTERPRISE
<Badge variant={
  tenant.plano === "STARTER" ? "gray"
  : tenant.plano === "STANDARD" ? "purple"
  : "amber"                                   // null falls through to "amber" (Enterprise's color)
}>{tenant.plano}</Badge>
```

```tsx
// page.tsx:411 + 446-457 (Editar Tenant dialog)
const [plano, setPlano] = React.useState<TenantPlano>(tenant.plano);   // seeded with null
<NativeSelect value={plano} onChange={...}>       // value={null} -> React treats the <select>
  {PLANO_OPTIONS.map(...)}                        // as uncontrolled; the browser visually
</NativeSelect>                                    // defaults to the first <option> ("STARTER")
                                                     // without `plano` state ever syncing to it.
```

Because `<select value={null}>` is functionally uncontrolled (React only forces the DOM selection
when `value != null`), the dropdown visually shows "STARTER" (the first `<option>`) while React
state stays `null`. If the operator's intended plan happens to be "STARTER" — very plausible,
since it visually looks already selected — and they click "Guardar" without touching the
dropdown, `handleSubmit` submits `{ plano: null, limiteUtilizadores: ... }`. The backend correctly
rejects this:

```java
// PlatformAdminController.java:133-135
if (request.getPlano() == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "O plano é obrigatório."));
}
```
...producing a confusing 400 "O plano é obrigatório." toast for a dropdown that visually already
showed a plan. The operator must actively deselect-then-reselect (or pick a different plan and
back) to make the save succeed — a real, reproducible break in the phase's own core "create a
tenant, then configure it" workflow, and the ENTERPRISE-colored blank badge in the mobile view
renders unconditionally for every such tenant regardless of any interaction.

Notably, the backend's own `/auth/me` handler (`AuthController.java:200`) already treats this
exact field as nullable (`t.getPlano() != null ? t.getPlano().name() : null`) and
`MeResponse.tenant_plano` is correctly typed `string | null` in `web/src/types/auth.ts` — so the
codebase already knows this value can be null elsewhere; only the new Phase 120 console regressed
on that.

**Fix:** Close the null state at the source, mirroring exactly how this same phase already fixed
`ativo`:

```java
// Tenant.java
@Enumerated(EnumType.STRING)
@Column(name = "plano", nullable = false, columnDefinition = "varchar(255) not null default 'STARTER'")
@Builder.Default
private TenantPlano plano = TenantPlano.STARTER;
```

...plus a companion migration backfilling any existing `NULL` rows (same shape as
`120-add-tenant-ativo.sql`), and drop the now-redundant null branch in `AuthController.getMe`. If
a non-null default is intentionally out of scope for this PR, at minimum: type
`TenantAdminSummary.plano`/`MeResponse`-style as `TenantPlano | null` on the frontend, add a
`PLANO_BADGE_VARIANT` fallback (e.g. a "Sem plano" gray badge instead of indexing with a
potentially-`null` key), and initialize `EditarTenantForm`'s state to a concrete `PLANO_OPTIONS[0]`
default (or force a real `onChange` before enabling "Guardar") so the visible selection and the
submitted value can never disagree.

## Warnings

### WR-01: Login brute-force lockout key is trivially bypassable (pre-existing, but sits in one of the 3 access paths this phase depends on) (PARTIALLY RESOLVED — see Round 2)

**File:** `backend/src/main/java/com/lexcv/controllers/AuthController.java:58-59`

**Issue:** The "5 failed attempts → 15 min lockout" throttle keys on
`RequestContextHolder.currentRequestAttributes().getSessionId()` as an "Ideally real IP"
placeholder:

```java
String ip = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes().getSessionId(); // Ideally real IP
String attemptKey = loginRequest.getEmail() + "-" + ip;
```

`ServletRequestAttributes#getSessionId()` creates (or returns) an `HttpSession`; since this app
runs `SessionCreationPolicy.STATELESS` with no `JSESSIONID` cookie ever persisted by a normal
JWT-cookie client, any caller that does not replay a previous session cookie (the default
behavior of essentially every scripted HTTP client, e.g. `curl`/`requests` without an explicit
cookie jar) gets a brand-new random session id on every single request. `attemptKey` is therefore
different on every attempt for such a caller, so `loginAttempts`/`lockoutTimers` never accumulate
and the lockout never triggers — the only brute-force throttle on `/auth/login` is bypassable by
simply not persisting cookies, which is the default posture of any real attack script. This
pre-dates Phase 120 (no Phase 120 tag near it) but sits directly inside the `login` access path
this phase's suspension model depends on, so it was in scope for this pass.

**Fix:** Key the limiter on the real client IP instead (respecting a trusted
`X-Forwarded-For`/`X-Real-IP` if the app sits behind a reverse proxy), e.g.
`request.getRemoteAddr()` via an injected `HttpServletRequest`, not a servlet session id.

### WR-02: `AlertasDiariosJob` (existing scheduled job) does not honor the new `Tenant.ativo` suspension semantics (RESOLVED — see Round 2)

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:90` (not in this phase's
file list, but a direct downstream consumer of `Tenant`, the entity this phase modifies)

**Issue:** The daily cross-tenant alert job iterates `tenantRepository.findAll()` unconditionally:

```java
for (Tenant tenant : tenantRepository.findAll()) {
    processarTenant(tenant.getId(), hoje);
    ...
```

It has not been updated to skip `tenant.getAtivo() == false`. This is not a cross-tenant data leak
(all queries stay scoped to the iterated tenant's own id), but it does mean a suspended tenant —
whose users are supposed to have "no access, immediately" per this phase's stated intent —
continues to be fully processed every morning: `Prazo`/`Evento`/`Honorario` risk is recomputed and
new `Notificacao` rows keep being written for users who can no longer log in to see them. Worth an
explicit decision (skip suspended tenants, or document that background processing intentionally
continues during a suspension) rather than a silent gap.

**Fix:** `for (Tenant tenant : tenantRepository.findAll()) { if (!Boolean.TRUE.equals(tenant.getAtivo())) continue; ... }`,
or an equivalent repository-level filter, if the product decision is that suspended tenants should
stop accruing background work too.

### WR-03: `/plataforma`'s authorization gate fails open during the initial `useMe()` load (RESOLVED — see Round 2)

**File:** `web/src/app/(dashboard)/plataforma/page.tsx:63-75`

**Issue:**

```tsx
const me = useMe();
if (me.isFetched && !me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
  return <AccessDeniedState ... />;
}
return <PlataformaPageContent />;
```

The `AccessDeniedState` branch only activates once `me.isFetched` is `true`. Before that first
resolve (the normal state on first navigation to `/plataforma`, e.g. a direct URL hit), the
condition is `false` regardless of the caller's actual role, so `PlataformaPageContent` renders
unconditionally — including firing `useTenantsAdmin()`'s `GET /platform/tenants` — for **any**
authenticated user (a regular tenant's ADMIN/ADVOGADO/etc.), not just `PLATAFORMA_ADMIN`. Contrast
with `DashboardShell`'s nav-item gate (`dashboard-shell.tsx:91-95`), which defaults `isPlatformAdmin`
to `false` while loading (fails *closed*, hides the link) — this page-level gate fails *open*
(shows the protected content) for the same loading window. The backend's class-level
`@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` still blocks the actual data (403, no leak), so the
practical impact is a page-shell/loading-state flash rather than exposed tenant data, but the gate
itself does not do what its own comment claims ("guarda de página (defesa em profundidade)") during
that window. Note this exact `me.isFetched && !hasRole` pattern is reused from elsewhere in the app
(per this file's own comment, "a mesma disciplina... que `clientes/page.tsx` já pratica"), so this
is a systemic gap being surfaced here, not one unique to this file.

**Fix:** Render a loading state (or `null`) while `!me.isFetched`, and only render
`PlataformaPageContent` once `me.isFetched && me.data?.roles?.includes("PLATAFORMA_ADMIN")` is
explicitly true:
```tsx
if (!me.isFetched) return <LoadingState />; // or null
if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) return <AccessDeniedState ... />;
return <PlataformaPageContent />;
```

## Info

### IN-01: Duplicated "initials" computation between desktop and mobile (RESOLVED — see Round 2)

**Files:** `web/src/app/(dashboard)/plataforma/columns.tsx:129-135`,
`web/src/app/(dashboard)/plataforma/page.tsx:182-188`

**Issue:** The exact same `nome.split(" ").filter(Boolean).slice(0, 2).map(w => w[0]).join("").toUpperCase()`
logic is duplicated verbatim between the desktop `columns.tsx` cell renderer and the mobile card
block in `page.tsx`.

**Fix:** Extract a small shared `tenantInitials(nome: string): string` helper (e.g. in
`web/src/types/platform-admin.ts` or a small `lib` util) and import it in both places.

### IN-02: "Editar Tenant" dialog's Cancel button isn't disabled while a save is in flight (RESOLVED — see Round 2)

**File:** `web/src/app/(dashboard)/plataforma/page.tsx:477-482`

**Issue:** The AlertDialog used for suspend/reactivate disables its Cancel button while pending
(`AlertDialogCancel disabled={isPending}`, `page.tsx:518`), but the sibling Edit dialog's Cancel
does not:
```tsx
<DialogClose asChild>
  <Button type="button" variant="outline">Cancelar</Button>   // no disabled={isSubmitting}
</DialogClose>
```
Not harmful (the in-flight `PUT` still completes and the always-refetched list stays correct
regardless of dialog visibility), but it's an inconsistent guard between the two dialogs in the
same file.

**Fix:** `<Button type="button" variant="outline" disabled={isSubmitting}>Cancelar</Button>`.

### IN-03: `createTenant` maps a server-misconfiguration to `403 Forbidden` (deliberately unchanged — see Round 2)

**File:** `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java:76-79`

**Issue:**
```java
} catch (IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
}
```
The only `IllegalStateException` `SetupService.provisionTenant` can throw is "O papel ADMIN não
está configurado" (the ADMIN role catalog row is missing) — a server-side seeding/configuration
defect, not a caller-permission problem. Mapping it to 403 (reused verbatim from
`SetupController.initialize`, where `IllegalStateException` genuinely means "already initialized",
a legitimately-403 case) is misleading for this different call site. Extremely unlikely to trigger
in practice (`DatabaseSeeder.seedRbac()` creates the ADMIN role on every boot), so low real impact.

**Fix:** Either map this specific case to `500`, or give `provisionTenant` a more specific
exception type than the borrowed `IllegalStateException` so the two call sites don't share a
status-code mapping that only fits one of them.

## Final Verdict

**Phase 120 (frontend consola de administração de tenants) is APPROVED — no blockers.**

- 0 open Critical findings: CR-01 is resolved and proven by new tests exercising all three
  tenant-creation paths.
- 0 open blocking Warnings: WR-02 and WR-03 are fully resolved. WR-01 is substantially improved —
  the reported bug (lockout never fires) is genuinely fixed and is a strict security improvement
  over the pre-existing state — with one residual precision gap (real attacker-IP vs. per-email
  granularity behind this repo's own Caddy deployment) that neither regresses nor introduces a new
  vulnerability, tracked as a follow-up rather than guessed at inside this phase.
- 0 open blocking Info items: IN-01 and IN-02 are resolved; IN-03 is a deliberate, documented
  non-fix consistent with its own original "low real impact" framing, same disposition class as
  Phase 117's own deferred Info items.
- Regression gates independently re-run and green: backend unit suite (172/172), SpotBugs/
  FindSecBugs (0 findings), frontend lint (0 errors), frontend production build (succeeds,
  `/plataforma` present in the route list) — all run directly against the real project checkout,
  not just taken from the fix pass's own report.
- Two genuinely out-of-scope-but-real issues surfaced incidentally during this round were
  deliberately NOT fixed inline (would have expanded this phase's scope beyond its own findings):
  the `isFetched && !canX` fail-open pattern exists identically in 25 other dashboard pages besides
  `/plataforma` (session task `task_a78e2d45`), and confirming this backend's production network
  topology actually blocks direct-to-backend access is needed before WR-01's lockout key can be
  tightened further (session task `task_0ccb6ccf`).

No further fix iteration is warranted for this phase.

---

_Reviewed: 2026-07-29T16:00:00Z (round 1)_
_Round-2 verification: 2026-07-29_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
