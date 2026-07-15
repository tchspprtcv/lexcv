---
phase: 98-backend-endpoint-publico-de-branding
reviewed: 2026-07-15T03:15:20Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - backend/src/main/java/com/lexcv/dtos/TenantPublicInfoResponse.java
  - backend/src/main/java/com/lexcv/controllers/PublicController.java
  - backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java
  - backend/src/main/java/com/lexcv/repositories/TenantRepository.java
  - backend/src/main/java/com/lexcv/config/SecurityConfig.java
findings:
  critical: 0
  warning: 2
  info: 1
  total: 3
status: issues_found
---

# Phase 98: Code Review Report

**Reviewed:** 2026-07-15T03:15:20Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the new public, unauthenticated `GET /api/v1/public/branding` endpoint (`PublicController`, `TenantPublicInfoResponse`, the new `TenantRepository.findFirstByOrderByCreatedAtAsc()` derived query, the corresponding `SecurityConfig` allowlist entry) and its Mockito test.

The core implementation is sound: the DTO structurally excludes every sensitive `Tenant` field (`nif`, `email`, `telefone`, `tipoEntidade`, `id`, `createdAt`), the entity is never serialized directly, the 404/no-tenant path is handled without risk of an unhandled exception, `logoDataUrl == null` correctly serializes as an explicit JSON `null`, and the `SecurityConfig` change is an exact-literal allowlist entry (no wildcard), scoped to only the one new path. `git diff --stat` against `6675d22` confirms the change touches only the 5 files listed plus planning artifacts — no existing endpoint, `@PreAuthorize` scope, or security filter was modified. I traced the immediate callees (`TenantRepository`, `Tenant` entity, `SetupService`, `DatabaseSeeder`, `AlertasDiariosJob`, `GlobalExceptionHandler`) to check assumptions embedded in this endpoint rather than taking them at face value.

Two robustness/integration gaps are worth fixing before this ships as the data source for the Phase 99 landing page: (1) the endpoint's "singleton tenant" assumption has no defensive safeguard if it is ever violated, and (2) the endpoint's CORS reachability for its actual stated consumer (a separate `webpage/` app) is unverified. Neither is a defect in the literal behavior specified by the phase plan, but both are latent risks introduced by this change that aren't covered by any test or check today.

## Narrative Findings (AI reviewer)

## Critical Issues

None found.

## Warnings

### WR-01: No safeguard if the "singleton tenant" assumption is ever violated

**File:** `backend/src/main/java/com/lexcv/repositories/TenantRepository.java:9`, `backend/src/main/java/com/lexcv/controllers/PublicController.java:30`
**Issue:** `findFirstByOrderByCreatedAtAsc()` deterministically returns *a* tenant but silently assumes there is at most one. Today that invariant holds only because two independent, uncoordinated code paths happen to enforce it: `SetupService.initializeSystem()` (gated by the global `SystemSetting.SINGLETON_ID.initialized` flag) and `DatabaseSeeder` (gated by `tenantRepository.count() > 0`). Nothing in the schema (no unique/check constraint) or in this endpoint enforces it directly.

Meanwhile `AlertasDiariosJob` (`backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:90`) explicitly iterates `tenantRepository.findAll()` as a "cross-tenant" scheduled job, i.e. the data model and the rest of the codebase already treat coexisting tenants as a normal, expected condition, not something structurally impossible. If a future change (a tenant-onboarding feature, a manual DB insert, a migration/backfill script) ever creates a second `Tenant` row, this public endpoint will not error or log — it will silently keep serving tenant #1's `nome`/`logoDataUrl` to every anonymous visitor, including ones who should see tenant #2's branding, with zero signal that anything is wrong. This is a strictly worse failure mode than "feature not supported" (which the phase's own CONTEXT.md correctly scopes out for slug/subdomain-based multi-tenant public branding) because it fails silently-wrong rather than loudly.

**Fix:** Add a cheap defensive check/log so the violation is observable instead of silent, e.g.:
```java
@GetMapping("/branding")
public ResponseEntity<?> getBranding() {
    long tenantCount = tenantRepository.count();
    if (tenantCount > 1) {
        log.warn("PublicController.getBranding(): {} tenants found; singleton assumption violated, returning oldest by createdAt", tenantCount);
    }

    Optional<Tenant> tenant = tenantRepository.findFirstByOrderByCreatedAtAsc();
    ...
}
```
At minimum, add a Javadoc comment directly on `TenantRepository.findFirstByOrderByCreatedAtAsc()` stating explicitly that it assumes a single-tenant deployment and is not a general-purpose "most recent tenant" query, so a future maintainer adding multi-tenant onboarding is forced to revisit this call site.

### WR-02: CORS allowlist not verified for the endpoint's stated consumer

**File:** `backend/src/main/java/com/lexcv/config/SecurityConfig.java:51-59` (new permitAll entry), `:68-85` (`corsConfigurationSource()`)
**Issue:** `permitAll()` on `/api/v1/public/branding` only bypasses Spring Security *authentication* — it does not bypass the CORS filter. The `CorsConfigurationSource` bean applies the same `app.cors.allowed-origins`-derived allowlist to `"/**"` (line 83), including this new public path, with `allowCredentials(true)` (which also precludes ever widening this to `*`). Per `backend/.env.example`, `CORS_ALLOWED_ORIGINS=http://localhost:3000` — i.e. today only the `web/` dashboard origin is allowed.

Per this phase's own plan (`98-01-PLAN.md`), the entire purpose of this endpoint is to be consumed by a *different* app: "A app `webpage/` (Phase 99) precisa de obter o nome/logo da instituição para personalizar a landing page pública." If that app performs a client-side (browser) `fetch()` to this endpoint from an origin that isn't in `app.cors.allowed-origins` (e.g. a marketing-site domain distinct from the dashboard), the browser will block the response due to CORS even though the endpoint is fully `permitAll()` and functionally correct. There is no test, threat-model entry, or env var change in this diff that addresses this — it's an unverified integration assumption for a consumer that doesn't exist in the repo yet.

**Fix:** Before Phase 99/100 wiring, confirm one of:
- `webpage/` fetches this endpoint server-side (SSR/server component or reverse-proxy rewrite analogous to `web/`'s `next.config.ts` pattern), in which case browser CORS is not in play and no change is needed; or
- `webpage/` fetches client-side from its own origin, in which case that origin must be added to `CORS_ALLOWED_ORIGINS`/`app.cors.allowed-origins` (or this specific read-only, non-sensitive path could get its own narrower `CorsConfigurationSource` registered on `/api/v1/public/**` without `allowCredentials`, since no cookies are needed for a public unauthenticated read).

## Info

### IN-01: New fully-public endpoint has no rate limiting / abuse throttling

**File:** `backend/src/main/java/com/lexcv/controllers/PublicController.java:28-44`, `backend/src/main/java/com/lexcv/config/SecurityConfig.java:51-59`
**Issue:** This adds a third category of credential-free, `permitAll()` endpoint (alongside `auth/login` and `setup/*`) that hits the database on every request with no caching, throttling, or rate limiting. Individually low risk (a single-row lookup), but it is a new, trivially-repeatable, zero-friction entry point into the DB layer for anonymous clients.
**Fix:** Not a regression specific to this change (the same gap already exists for `setup/status` and `auth/login`), so no action is required to ship this phase. Worth tracking as a follow-up if/when the project introduces rate limiting (e.g. Bucket4j filter or a reverse-proxy rule) — apply it uniformly to all `permitAll()` routes, this one included.

---

_Reviewed: 2026-07-15T03:15:20Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
