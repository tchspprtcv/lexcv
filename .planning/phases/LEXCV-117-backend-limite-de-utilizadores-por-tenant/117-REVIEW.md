---
phase: LEXCV-117-backend-limite-de-utilizadores-por-tenant
reviewed: 2026-07-29T01:40:11Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/TenantPlano.java
  - backend/src/main/java/com/lexcv/models/Tenant.java
  - backend/src/main/java/com/lexcv/repositories/UserRepository.java
  - backend/migrations/117-add-tenant-plano-limite-utilizadores.sql
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
findings:
  critical: 1
  warning: 2
  info: 2
  total: 5
status: issues_found
---

# Phase LEXCV-117-backend-limite-de-utilizadores-por-tenant: Code Review Report

**Reviewed:** 2026-07-29T01:40:11Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Reviewed the Phase 117 data layer (`TenantPlano`, `Tenant.limiteUtilizadores`, `UserRepository.countByTenantIdAndAtivoTrue`, the manual migration script) and the new `409 CONFLICT` enforcement block in `AdminController.createUser`, plus its dedicated Mockito test.

The core `createUser` check itself is correct: tenant is derived exclusively from `principal.getTenantId()` (never from the request body — confirmed by tracing `UserPrincipal` back to `JwtAuthenticationFilter`, which populates it server-side from the authenticated user's stored `tenantId`), the `>=` boundary comparison is right (new user doesn't count for itself), the `null`-means-unlimited short-circuit correctly skips the count query entirely, and the live (non-cached) re-count is correctly proven by the chained-mock test case. Migration is safe for the single existing tenant: it backfills `plano='ENTERPRISE'` and deliberately leaves `limite_utilizadores` NULL, so the one row that exists today cannot be locked out by this deploy.

However, the feature as a whole does **not** actually enforce a hard cap on active users, because the check exists at exactly one of the two code paths that can make a user active. `AdminController.updateUser` (pre-existing, untouched by this phase) can flip any user's `ativo` field from `false` to `true` with zero reference to `Tenant.limiteUtilizadores`. This is reachable through entirely ordinary admin workflows (create disabled, activate later; or reactivate a previously deactivated account) — no timing tricks required — and it is not listed anywhere in the phase's own STRIDE threat register (T-117-04 through T-117-09 cover tenant-id spoofing, response-body disclosure, cross-tenant counting, the create/create race, endpoint authorization, and DoS from the count query — none of them address a second write path to `ativo`). This is the primary finding below (CR-01).

Two lower-severity items round out the review: the accepted create/create race condition (T-117-07 in the plan, already consciously risk-accepted, but the "worst case is 1 user over" framing undersells unbounded-N concurrent-request exposure) and a non-idempotent migration script (consistent with this repo's existing migration convention, so not a regression introduced by this phase, but still a real re-run risk given migrations here are 100% manual).

## Critical Issues

### CR-01: Active-user limit is fully bypassable via `PUT /api/v1/admin/users/{id}` (no re-check on reactivation)

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:169` (interacting with the new check at `:105-112`)

**Issue:**
`createUser`'s new limit check only fires at creation time, and only blocks a *creation* — it never considers what `ativo` value the new user itself will have. `updateUser` sets `ativo` from the request body with no limit check at all:

```java
// updateUser, line 169 — unchanged by Phase 117
if (body.containsKey("ativo")) user.setAtivo((Boolean) body.get("ativo"));
...
user = userRepository.save(user);   // line 200 — no limiteUtilizadores check anywhere above this
```

Because the `createUser` check compares the *current live active count* against the limit — not the count the tenant would have *after* the new row is inserted — an admin can create any number of users with `"ativo": false` in the body: each one passes the check (the active count never grows, since every new row starts inactive), so none of them ever trips `utilizadoresAtivos >= tenant.getLimiteUtilizadores()`. Once created, each of those inactive accounts can be flipped to `ativo: true` via `PUT /api/v1/admin/users/{id}`, which performs no limit check whatsoever. The same bypass works even more directly by simply reactivating any single previously-deactivated user instead of creating a new one — `updateUser` doesn't distinguish "this reactivation would push us over the limit" from any other field edit.

This is not a documented, consciously-accepted scope cut the way the race condition (T-117-07) is. The phase's STRIDE threat register (117-02-PLAN.md) enumerates T-117-04 (tenant-id origin), T-117-05 (message content), T-117-06 (cross-tenant count), T-117-07 (create/create race — accepted), T-117-08 (authz surface — unchanged), T-117-09 (DoS via count query) — none of them models "a second endpoint can set `ativo=true` without going through the check." Confirmed via `grep -rn limiteUtilizadores backend/src/main/java` that `Tenant.limiteUtilizadores` is read in exactly one place in the entire codebase: `AdminController.createUser`. The plan text explicitly instructed "NAO alterar `updateUser`" for this specific plan, which explains why the code looks this way, but the net effect is that the feature's stated goal — "o backend aplica um limite de utilizadores ativos por tenant" (ROADMAP Phase 117 goal) — is not actually true after this phase ships; it's only true for one specific way of getting a user to `ativo=true`.

Both endpoints already require `hasRole('ADMIN')` (class-level `@PreAuthorize`, unchanged), so this isn't a privilege escalation — it's a monetization/business-rule control that any legitimate tenant admin can trivially route around, defeating the entire purpose of the phase.

**Fix:**
Apply the same check in `updateUser`, gated to only run when the request would actually transition a user from inactive to active (so ordinary edits that don't touch `ativo`, or that set it to `false`, pay no extra cost). Extracting a shared private helper avoids duplicating the count-then-compare logic in two places, which also keeps `UserRepository.countByTenantIdAndAtivoTrue`'s "single source of truth" comment honest for the two enforcement points, not just the one:

```java
// AdminController — new private helper, called from both createUser and updateUser
private Optional<ResponseEntity<?>> limiteUtilizadoresExcedido(UUID tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    if (tenant != null && tenant.getLimiteUtilizadores() != null) {
        long utilizadoresAtivos = userRepository.countByTenantIdAndAtivoTrue(tenantId);
        if (utilizadoresAtivos >= tenant.getLimiteUtilizadores()) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Limite de utilizadores atingido para o vosso plano.")));
        }
    }
    return Optional.empty();
}
```

```java
// updateUser — only check when ativo is actually flipping false -> true
if (body.containsKey("ativo")) {
    boolean novoAtivo = (Boolean) body.get("ativo");
    if (novoAtivo && !Boolean.TRUE.equals(user.getAtivo())) {
        var bloqueado = limiteUtilizadoresExcedido(principal.getTenantId());
        if (bloqueado.isPresent()) return bloqueado.get();
    }
    user.setAtivo(novoAtivo);
}
```

Add regression coverage mirroring `AdminControllerLimiteUtilizadoresTest`'s case 1/2 for `updateUser` (reactivation blocked at limit, reactivation allowed below limit, edits that don't touch `ativo` never call `countByTenantIdAndAtivoTrue`) before shipping the fix.

## Warnings

### WR-01: Count-then-compare is not atomic — concurrent requests can exceed the limit by more than the "1 user" the plan's risk acceptance assumes

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:105-112`

**Issue:** The tenant lookup, the count query, and the eventual `save()` run as three separate statements/transactions with no lock, no `SELECT ... FOR UPDATE`, no optimistic `@Version`, and no DB `CHECK`/trigger. This is already registered and consciously accepted in the phase's own threat model as T-117-07 ("Corrida entre dois pedidos concorrentes com a contagem em limite-1... pior caso é 1 utilizador acima do limite... Registado, não mitigado"), so this isn't a missed defect — it's flagged here because the stated worst case understates the actual exposure: with exactly 2 concurrent requests racing at `count == limit - 1`, the overshoot is indeed 1. But nothing bounds concurrency to 2 — N simultaneous `POST /api/v1/admin/users` calls issued while `count == limit - 1` (e.g. a bulk-onboarding script, a retried/duplicated request from a flaky admin UI, or several admins working at once) can all observe the same pre-insert count and all pass the check, producing up to `limit - 1 + N` active users, not `limit + 1`.

**Fix:** No action required if the existing risk acceptance still holds after the CR-01 fix (which matters more, since it's unbounded and requires no timing at all). If tightened later, the standard options are a pessimistic lock on the tenant row (`SELECT ... FOR UPDATE` inside a transaction wrapping the count+save) or a DB-level guard; both were explicitly ruled out for this plan ("NAO acrescentar @Transactional, locks nem constraints de base de dados"), which is a reasonable call for a single-office, ADMIN-only, manually-billed endpoint — just worth re-confirming that assumption once Phase 119/120 make tenant provisioning/limits self-service instead of manual.

### WR-02: Migration's two `ADD COLUMN` statements are not idempotent, unlike the deliberately-idempotent backfill

**File:** `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql:28-29`

**Issue:**
```sql
ALTER TABLE t_tenant ADD COLUMN plano VARCHAR(255);
ALTER TABLE t_tenant ADD COLUMN limite_utilizadores INTEGER;

UPDATE t_tenant SET plano = 'ENTERPRISE' WHERE plano IS NULL;
```
The `UPDATE` was deliberately written to be safe on re-execution (`WHERE plano IS NULL` — per 117-01-PLAN.md, "a cláusula WHERE torna a re-execução segura"), but the two `ALTER TABLE ... ADD COLUMN` statements above it have no equivalent guard. Since this repo has no migration-tracking table (manual `psql`/DBeaver execution is the only mechanism, confirmed by the script's own header), a second accidental run of this exact file against an already-migrated database — plausible with a manual runbook, e.g. re-running "just to be sure" across a staging/prod pair sharing one checklist — fails both `ALTER TABLE` statements with "column already exists". This doesn't corrupt data (the idempotent `UPDATE` still no-ops correctly), but it's an inconsistency within the same script: one statement was hardened against re-run, two were not. Note this pattern (no `IF NOT EXISTS`) matches every other existing script in `backend/migrations/` (e.g. `96-add-notificacao-snoozed-until.sql`), so this is a pre-existing repo-wide convention, not a regression specific to this phase — raised here per the review's explicit instruction to assess migration safety, not as a Phase-117-specific defect.

**Fix:**
```sql
ALTER TABLE t_tenant ADD COLUMN IF NOT EXISTS plano VARCHAR(255);
ALTER TABLE t_tenant ADD COLUMN IF NOT EXISTS limite_utilizadores INTEGER;
```
(Postgres supports `ADD COLUMN IF NOT EXISTS` since 9.6.) If adopted, consider applying the same treatment repo-wide rather than only in this file, for consistency.

## Info

### IN-01: No DB-level guard against a negative/zero `limite_utilizadores` sentinel

**File:** `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql:29`

**Issue:** `Tenant.java`'s own comment is emphatic that `null` is the only valid "no limit" representation and a sentinel like `-1`/`MAX_VALUE` must never be used — but nothing in the schema enforces that. Today this is low-risk: `grep -rn limiteUtilizadores backend/src/main/java` shows no application code path writes this column at all (it's set only by hand via SQL until Phase 120 adds a console). If a future manual edit or the Phase 120 write-path ever inserts a negative value, the existing `createUser` comparison (`utilizadoresAtivos >= tenant.getLimiteUtilizadores()`) would silently treat that tenant as permanently at capacity (count `>= -1` is always true) rather than raising any error — indistinguishable from an outage to whoever hits it first. The plan explicitly deferred all constraints for this migration ("NAO acrescentar NOT NULL, DEFAULT, CHECK nem índices"), so this is intentional minimalism for now, not an oversight — noting it here so it's addressed when Phase 120 introduces the first code path that actually writes this column.

**Fix:** When a write-path for `limiteUtilizadores` is introduced (Phase 120), add `CHECK (limite_utilizadores IS NULL OR limite_utilizadores >= 0)` in that phase's migration (or validate the range in the service layer before persisting).

### IN-02: `createUser`'s limit check fails open if the caller's own tenant row is missing

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:105-106`

**Issue:** `Tenant tenant = tenantRepository.findById(principal.getTenantId()).orElse(null); if (tenant != null && tenant.getLimiteUtilizadores() != null) { ... }` — if the tenant row referenced by the authenticated principal's JWT doesn't exist (e.g. data-integrity drift; `User.tenantId` has no DB-level foreign key to `t_tenant`), the limit check is silently skipped entirely rather than treated as an error, and user creation proceeds unbounded. This is a deliberate, documented decision (117-CONTEXT.md: "Se o tenant não for encontrado, não bloquear"; reconfirmed in 117-02-SUMMARY.md's Decisions Made) and low-risk today given this codebase's confirmed single-tenant-per-deployment model (see `TenantRepository.findFirstByOrderByCreatedAtAsc`'s Javadoc) and the absence of any tenant-deletion endpoint. Flagging only so it's re-examined once Phase 119/120 introduce multi-tenant provisioning and any tenant lifecycle operations (suspension/deletion) that could make a dangling `tenantId` reachable in practice.

**Fix:** No action needed now. When tenant deletion/suspension is introduced, consider whether "tenant not found" for an otherwise-authenticated principal should instead be treated as a hard failure (401/403) rather than a silent limit bypass.

---

_Reviewed: 2026-07-29T01:40:11Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
