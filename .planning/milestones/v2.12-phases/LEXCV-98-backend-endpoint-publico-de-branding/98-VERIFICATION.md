---
phase: 98-backend-endpoint-publico-de-branding
verified: 2026-07-15T10:30:00Z
status: human_needed
score: 7/7 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Live end-to-end curl against a running backend confirms real HTTP/Jackson/security-filter-chain behavior"
    expected: "curl -s -i http://localhost:8080/api/v1/public/branding (no cookies/headers) returns 200, Content-Type application/json, body containing exactly nome + logoDataUrl (nif/email/telefone/id/tipoEntidade/createdAt absent)"
    why_human: "Requires a live Spring Boot context + reachable PostgreSQL with an already-initialized tenant. This is the plan's own Task 2 <human-check> item, not exercised by the executor (SUMMARY.md 'Next Phase Readiness' confirms this explicitly). All code-level equivalents (4/4 Mockito unit tests, SecurityConfig grep, DTO field review, JwtAuthenticationFilter trace) independently verified and pass. Consistent with project precedent (STATE.md: Phase 91 Testcontainers, Phase 89 NEEDS-HUMAN-VISUAL) of tracking live-infra-dependent checks as human_needed rather than blocking gaps."
---

# Phase 98: Backend — Endpoint Público de Branding Verification Report

**Phase Goal:** Existe um endpoint público e não-autenticado que devolve exclusivamente o nome e logo da tenant, através de um DTO explícito de cópia campo-a-campo (nunca a entidade `Tenant`), registado na allowlist de segurança como entrada exact-literal.
**Verified:** 2026-07-15T10:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

Merged from ROADMAP.md Phase 98 Success Criteria (4 items) + `98-01-PLAN.md` frontmatter `must_haves.truths` (6 items), deduplicated to 7 distinct truths.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `GET /api/v1/public/branding`, sem cookie/header de autenticação, devolve 200 com JSON contendo exatamente `nome`+`logoDataUrl` quando existe tenant, construído via `TenantPublicInfoResponse` (cópia explícita), nunca a entidade | ✓ VERIFIED | `PublicController.java:30` maps `@GetMapping("/branding")` under `@RequestMapping("/api/v1/public")`; no `@PreAuthorize`/`@Secured` on class or method (confirmed: only `AdminController`, `ParecerPesquisaController`, `ParecerController`, `NotificacaoController`, `ResourceController` use `@PreAuthorize` — 106 occurrences across those 5 files, `PublicController` absent). Response built via `TenantPublicInfoResponse.builder().nome(t.getNome()).logoDataUrl(t.getLogoDataUrl()).build()` (lines 52-57) — never `ResponseEntity.ok(t)`. Ran `mvn test -Dtest=PublicControllerTest` independently: **4 tests, 0 failures, 0 errors** (`getBranding_comTenantELogo_devolve200ComNomeELogoDataUrl` asserts this exact behavior). Also traced `JwtAuthenticationFilter.doFilterInternal` (runs on every request regardless of `permitAll`): when no JWT cookie/header present, `getJwtFromRequest` returns `null`, the `if (StringUtils.hasText(jwt) ...)` block is skipped entirely, and the filter falls through unconditionally to `filterChain.doFilter(...)` — no early rejection, confirming the request truly reaches the controller with zero auth artifacts. Real HTTP round-trip (Jackson serialization + full filter chain) not exercised — see Human Verification. |
| 2 | A resposta nunca contém `nif`, `email`, `telefone`, `id`, `tipoEntidade` ou `createdAt` | ✓ VERIFIED | `TenantPublicInfoResponse.java` (15 lines) declares **exactly two fields**: `nome`, `logoDataUrl`. No other field exists in the class — structurally impossible to leak the forbidden fields via Jackson since it only serializes declared properties. No `@JsonProperty`/Jackson import present (native camelCase serialization, per CONTEXT.md decision). |
| 3 | Sem tenant (sistema não inicializado) → 404 com `{"message": "Sistema não inicializado."}`, nunca uma exceção 500 | ✓ VERIFIED | `PublicController.java:46-49`: `if (tenant.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Sistema não inicializado."));`. Test `getBranding_semTenant_devolve404ComMensagemSistemaNaoInicializado` (lines 37-49) asserts exactly this; independently re-ran, passes. |
| 4 | `logoDataUrl` null (tenant existe mas sem logo) é serializado como JSON null explícito, sem exceção, status 200 | ✓ VERIFIED | `PublicController.java:55`: `.logoDataUrl(t.getLogoDataUrl())` copies `null` through with no special-casing/`@JsonInclude(NON_NULL)`. Test `getBranding_comTenantSemLogo_devolve200ComLogoDataUrlNullExplicito` (lines 69-84) asserts `HttpStatus.OK` + `assertNull(body.getLogoDataUrl())`; independently re-ran, passes. |
| 5 | `SecurityConfig.permitAll()` ganha exatamente uma nova entrada literal `/api/v1/public/branding`, nunca um wildcard `/api/v1/public/**` | ✓ VERIFIED | Direct `Grep` of `SecurityConfig.java` for `public/branding\|public/\*\*\|public/\*`: 3 matches, all expected (1 code literal at line 67 inside `requestMatchers(...).permitAll()`, 2 comment mentions at lines 78/80) — **zero wildcard forms found**. `git diff 6675d22 86d5a4a -- SecurityConfig.java` shows the only functional change is `+16/-1` adding this one literal + comments; the 5 pre-existing entries (`auth/login`, `auth/refresh`, `auth/logout`, `setup/status`, `setup/initialize`) are byte-for-byte unchanged. |
| 6 | Endpoint resolve a tenant deterministicamente via `findFirstByOrderByCreatedAtAsc()`, sem depender de `SecurityContextHolder`/JWT | ✓ VERIFIED | `PublicController.java` has zero import/usage of `SecurityContextHolder` (full file read, imports list confirmed: Tenant/TenantRepository/TenantPublicInfoResponse/Lombok/Spring-web/HttpStatus/ResponseEntity/Map/Optional only). `TenantRepository.java:20` declares `Optional<Tenant> findFirstByOrderByCreatedAtAsc();` — a genuine Spring Data JPA derived query (interface-only; Spring Data generates the implementation from the method name, the same pattern used by every repository in this codebase), called at `PublicController.java:44`. |
| 7 | Nenhum endpoint existente nem scope `@PreAuthorize` é modificado — fase puramente aditiva | ✓ VERIFIED | `git diff --stat 6675d22 86d5a4a -- backend/` shows **exactly 5 files touched** (`SecurityConfig.java`, `PublicController.java` [new], `TenantPublicInfoResponse.java` [new], `TenantRepository.java`, `PublicControllerTest.java` [new]) — matches `98-01-PLAN.md`'s `files_modified` frontmatter exactly, even after all 3 code-review/fix iterations. `@PreAuthorize` grep across `controllers/` confirms it remains present/unmodified in the 5 controllers that use it; none appear in the diff. Full backend suite independently re-run: **73/73 tests, 0 failures, 0 errors** across 6 suites (`PublicControllerTest`=4, `ResourceControllerUploadDocumentoTest`=2, `AlertasDiariosJobTest`=9, `ClienteNifValidationTest`=4, `NotificacaoServiceTest`=39, `RiscoPrazoServiceTest`=15) — zero regressions. |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/dtos/TenantPublicInfoResponse.java` | DTO público com exatamente `nome`+`logoDataUrl`, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` | ✓ VERIFIED | 15 lines; exactly 2 fields; all 4 Lombok annotations present; wired — imported and constructed via `.builder()` in `PublicController.java`, asserted against in `PublicControllerTest.java` |
| `backend/src/main/java/com/lexcv/repositories/TenantRepository.java` | derived query `findFirstByOrderByCreatedAtAsc` devolvendo `Optional<Tenant>` | ✓ VERIFIED | Method declared line 20; `JpaRepository<Tenant, UUID>` inheritance intact; wired — called in `PublicController.java:44` and stubbed in all 4 `PublicControllerTest` cases |
| `backend/src/main/java/com/lexcv/controllers/PublicController.java` | endpoint `GET /api/v1/public/branding`, stateless, sem `SecurityContextHolder` | ✓ VERIFIED | 59 lines; `@GetMapping("/branding")` under `@RequestMapping("/api/v1/public")`; no `SecurityContextHolder`; wired — registered in `SecurityConfig` allowlist, exercised by `PublicControllerTest` |
| `backend/src/main/java/com/lexcv/config/SecurityConfig.java` | entrada exact-literal na allowlist `permitAll` | ✓ VERIFIED | Line 67, confirmed via direct grep; zero wildcard variants |
| `backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java` | teste Mockito 404 (`Optional.empty`) + 200 (tenant incl. logo null) | ✓ VERIFIED | 130 lines, 4 `@Test` methods; independently re-run via `mvn test -Dtest=PublicControllerTest`: 4/4 pass |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `PublicController.java` | `TenantRepository.findFirstByOrderByCreatedAtAsc` | constructor-injected repository call | ✓ WIRED | Line 44: `tenantRepository.findFirstByOrderByCreatedAtAsc()` |
| `PublicController.java` | `TenantPublicInfoResponse` | explicit getter-to-builder copy | ✓ WIRED | Lines 52-57: `TenantPublicInfoResponse.builder().nome(t.getNome()).logoDataUrl(t.getLogoDataUrl()).build()` |
| `SecurityConfig.java` | `/api/v1/public/branding` | `requestMatchers(...).permitAll()` exact literal | ✓ WIRED | Line 67, inside the `authorizeHttpRequests` block |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `PublicController.getBranding()` | `tenant` (`Optional<Tenant>`) | `tenantRepository.findFirstByOrderByCreatedAtAsc()` — genuine Spring Data JPA derived query against the `t_tenant` table (`JpaRepository<Tenant, UUID>`), not a hardcoded/static return | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `PublicControllerTest` behaviors (404 / 200 / 200-null-logo / 200-warn-branch) | `mvn test -Dtest=PublicControllerTest` (run independently by verifier, not trusted from SUMMARY) | `Tests run: 4, Failures: 0, Errors: 0` | ✓ PASS |
| No regression to existing suite | `mvn test` (full backend suite, independently re-run by verifier) | `73/73` across 6 suites, 0 failures/errors | ✓ PASS |
| Backend compiles | `mvn -DskipTests compile` | Exit 0, no errors | ✓ PASS |
| SAST (SpotBugs + FindSecBugs) | `mvn spotbugs:check` | Exit 0, 0 `BugInstance` entries | ✓ PASS |
| Live HTTP round-trip (`curl` against running server) | N/A — requires live Spring context + reachable PostgreSQL | Not runnable within this verifier's constraints | ? SKIP — routed to Human Verification |

### Probe Execution

No `scripts/*/tests/probe-*.sh` files exist anywhere in this repository, and neither `98-01-PLAN.md` nor `98-01-SUMMARY.md` declare any probe script for this phase. N/A — skipped.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| LP-01 | 98-01-PLAN.md | Endpoint público `GET /api/v1/public/branding` devolve exclusivamente `nome`+`logoDataUrl` via DTO explícito, nunca a entidade `Tenant` | ✓ SATISFIED | Truths 1, 2, 4, 6 above; `TenantPublicInfoResponse.java` structurally limited to 2 fields; explicit builder copy in `PublicController.java` |
| LP-02 | 98-01-PLAN.md | Endpoint registado na allowlist do `SecurityConfig` como entrada exata, nunca wildcard | ✓ SATISFIED | Truth 5 above; `SecurityConfig.java:67` exact literal, zero wildcard matches |

**Orphaned requirements check:** REQUIREMENTS.md's Traceability table maps only LP-01 and LP-02 to Phase 98; `98-01-PLAN.md` frontmatter declares `requirements: [LP-01, LP-02]`. Exact match — no orphaned requirements for this phase.

**Note (non-blocking):** REQUIREMENTS.md still shows LP-01/LP-02 checkboxes unchecked (`- [ ]`) and Traceability status "Pending". This is consistent with this project's established convention of bulk-reconciling REQUIREMENTS.md status at milestone close rather than per-phase (confirmed via git history: v2.11's REQUIREMENTS.md was only updated/archived at milestone-close commits `ebabf82`/`db6fd46`, "15/15 requirements satisfied"). Not a phase-98 gap.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `SecurityConfig.java` | 58-67 (comment), 77-98 (comment) | WR-01 (code review): `permitAll()` exempts authentication only, not Spring's CORS filter; `corsConfigurationSource()` still applies to `"/**"` including this new path; no `webpage/` origin configured in `CORS_ALLOWED_ORIGINS` yet | INFO for Phase 98 (out of this phase's must-have scope) | Does not affect Phase 98's goal — `curl`/server-to-server calls are unaffected by browser CORS, and Phase 98 has no browser caller. Independently re-verified across all 3 review/fix iterations (`98-REVIEW.md`, `98-REVIEW-FIX.md`) as a **deliberate, documented deferral** pending Phase 99's fetch-strategy decision (server-side vs. client-side), not an unresolved defect or oversight. Recommend Phase 99/100 plan carries this as an explicit acceptance criterion (already recommended in `98-REVIEW-FIX.md` iteration 3). |
| `PublicController.java:32`, `TenantRepository.java:13`, `SecurityConfig.java:58,77` | — | IN-03: hardcoded `WR-01`/`WR-02` review-finding-ID labels in comments no longer match current review numbering (labels drift every re-review pass) | INFO | Cosmetic/traceability only, zero runtime impact |
| `PublicController.java:39,44` | — | IN-04: `count()` + `findFirstByOrderByCreatedAtAsc()` are two independent non-transactional reads; the diagnostic WARN log could rarely mis-report tenant count under a race | INFO | Zero effect on data returned to callers (response always reflects the true oldest tenant); affects only a best-effort diagnostic log line's precision |
| `PublicController.java:39,44` | — | IN-02: unguarded repository calls could surface raw `DataAccessException` detail via `GlobalExceptionHandler` on a DB failure | INFO | Pre-existing, app-wide pattern; not introduced or worsened by this phase (`GlobalExceptionHandler.java` untouched by this diff) |
| `PublicController.java` (endpoint overall) | — | IN-01: no rate limiting on this fully-public endpoint | INFO | By design, consistent with other `permitAll()` routes; tracked as a general follow-up, not phase-98-specific |

No unreferenced `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers found in any of the 5 phase-98 files (word-boundary grep confirmed zero matches; earlier substring matches on "TODO" were false positives from the Portuguese word "todos"). 0 Critical, 1 Warning (fully accounted for as a deliberate, cross-verified, out-of-phase-scope deferral), 4 Info.

### Human Verification Required

#### 1. Live end-to-end curl against a running backend confirms real HTTP/Jackson/security-filter-chain behavior

**Test:** With the backend running (`mvn spring-boot:run`), PostgreSQL reachable, and the system already initialized (via `/setup` or `SEED_ENABLED=true`): run `curl -s -i http://localhost:8080/api/v1/public/branding` with no cookies/headers.
**Expected:** HTTP `200`, `Content-Type: application/json`, body containing exactly the keys `nome` and `logoDataUrl` (confirm `nif`/`email`/`telefone`/`id`/`tipoEntidade`/`createdAt` are absent), no `Set-Cookie`/auth challenge.
**Why human:** Requires a live Spring Boot application context plus a reachable PostgreSQL connection with an already-initialized tenant — this exceeds this verifier's grep/compile/unit-test-level automated gate. This is the exact `<human-check>` item declared in `98-01-PLAN.md` Task 2's `<verify>` block, and the executor's own `98-01-SUMMARY.md` ("Next Phase Readiness") explicitly states it was not run in that session. All code-level equivalents were independently verified by this pass regardless: `PublicControllerTest` (4/4 passing, re-run independently), `SecurityConfig` allowlist grep (literal present, wildcard absent), DTO field-shape review (exactly 2 fields, no Jackson suppression), and a trace of `JwtAuthenticationFilter` confirming it does not reject cookie-less requests before they reach the controller. Consistent with this project's own precedent (`STATE.md`: Phase 91 Testcontainers npipe incompatibility, Phase 89 `NEEDS-HUMAN-VISUAL` items) of tracking live-infrastructure-dependent checks as `human_needed` rather than a blocking gap when all automated/code-level equivalents already pass.

### Gaps Summary

No gaps. All 7 derived observable truths (merging ROADMAP.md's 4 Success Criteria with `98-01-PLAN.md`'s 6 frontmatter truths) are VERIFIED against the actual codebase — not merely asserted by SUMMARY.md. All 5 required artifacts exist, are substantive, and are wired. All 3 key links are confirmed WIRED by direct code inspection. The full backend test suite (73/73) and SpotBugs SAST (0 findings) were independently re-run by this verifier, not taken on faith from `98-REVIEW.md`. `git diff --stat` across the entire commit range (including all 3 code-review/fix iterations) confirms the phase touched exactly the 5 files declared in the plan — genuinely additive, with existing `@PreAuthorize` scopes and other endpoints untouched.

The single open item is the plan's own declared human-check (a live `curl` round-trip against a running backend + database), which requires infrastructure this verifier cannot start within its automated gate. This routes the phase to `human_needed` rather than `passed`, per the verification workflow's decision tree (human items take priority over an otherwise-clean score) — it is not a gap in the implementation itself. The CORS finding (WR-01) raised during code review is explicitly out of Phase 98's must-have scope (no browser caller exists yet) and is tracked as a deliberate, cross-verified deferral for Phase 99/100, not a defect in this phase.

---

*Verified: 2026-07-15T10:30:00Z*
*Verifier: Claude (gsd-verifier)*
