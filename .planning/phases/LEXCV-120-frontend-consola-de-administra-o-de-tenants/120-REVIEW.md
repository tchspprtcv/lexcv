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

**Reviewed:** 2026-07-29T16:00:00Z
**Depth:** deep
**Files Reviewed:** 14
**Status:** issues_found

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

## Critical Issues

### CR-01: Newly-provisioned tenants (including the reserved "LexCV" tenant) get `plano = NULL`, which the new console's types/UI assume can never happen

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

### WR-01: Login brute-force lockout key is trivially bypassable (pre-existing, but sits in one of the 3 access paths this phase depends on)

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

### WR-02: `AlertasDiariosJob` (existing scheduled job) does not honor the new `Tenant.ativo` suspension semantics

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

### WR-03: `/plataforma`'s authorization gate fails open during the initial `useMe()` load

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

### IN-01: Duplicated "initials" computation between desktop and mobile

**Files:** `web/src/app/(dashboard)/plataforma/columns.tsx:129-135`,
`web/src/app/(dashboard)/plataforma/page.tsx:182-188`

**Issue:** The exact same `nome.split(" ").filter(Boolean).slice(0, 2).map(w => w[0]).join("").toUpperCase()`
logic is duplicated verbatim between the desktop `columns.tsx` cell renderer and the mobile card
block in `page.tsx`.

**Fix:** Extract a small shared `tenantInitials(nome: string): string` helper (e.g. in
`web/src/types/platform-admin.ts` or a small `lib` util) and import it in both places.

### IN-02: "Editar Tenant" dialog's Cancel button isn't disabled while a save is in flight

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

### IN-03: `createTenant` maps a server-misconfiguration to `403 Forbidden`

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

---

_Reviewed: 2026-07-29T16:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
