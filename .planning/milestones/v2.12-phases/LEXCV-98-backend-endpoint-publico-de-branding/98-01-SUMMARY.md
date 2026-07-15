---
phase: 98-backend-endpoint-publico-de-branding
plan: 01
subsystem: api
tags: [spring-security, spring-data-jpa, lombok, rest-api, multi-tenant, public-endpoint]

# Dependency graph
requires: []
provides:
  - "GET /api/v1/public/branding — unauthenticated public endpoint returning {nome, logoDataUrl} for the singleton tenant"
  - "TenantPublicInfoResponse DTO (exactly nome+logoDataUrl, zero sensitive fields)"
  - "TenantRepository.findFirstByOrderByCreatedAtAsc() derived query"
  - "SecurityConfig permitAll exact-literal entry for /api/v1/public/branding"
affects: [99-webpage-landing-app, 100-infra-wiring-deployment]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Narrow single-purpose public controller (PublicController mirrors SetupController: @RestController/@RequestMapping/@RequiredArgsConstructor, constructor injection, ResponseEntity<?>, Map.of(\"message\", ...) errors)"
    - "Explicit getter-to-builder field copy for public DTOs — never serialize the JPA entity directly (mirrors AuthController.getMe())"
    - "Mockito-only controller test convention (no MockMvc/@SpringBootTest anywhere in this codebase) — instantiate controller directly with mocked repository"

key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/TenantPublicInfoResponse.java
    - backend/src/main/java/com/lexcv/controllers/PublicController.java
    - backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/TenantRepository.java
    - backend/src/main/java/com/lexcv/config/SecurityConfig.java

key-decisions:
  - "TenantPublicInfoResponse has exactly two fields (nome, logoDataUrl) with no @JsonProperty — native camelCase serialization matches the CONTEXT.md decision and the existing UserResponse.tenant_logo_data_url precedent for null handling"
  - "SecurityConfig allowlist entry is the exact literal \"/api/v1/public/branding\", never a wildcard, so no future /api/v1/public/* endpoint is silently pre-authorized"
  - "PublicController is stateless and never touches SecurityContextHolder, identical to SetupController's public endpoints"

requirements-completed: [LP-01, LP-02]

# Metrics
duration: ~20min
completed: 2026-07-15
---

# Phase 98 Plan 01: Backend Endpoint Público de Branding Summary

**New unauthenticated `GET /api/v1/public/branding` endpoint returning `{nome, logoDataUrl}` via an explicit copy-DTO, backed by a new `findFirstByOrderByCreatedAtAsc()` repository query and an exact-literal SecurityConfig allowlist entry — built RED/GREEN via Mockito, zero new dependencies, purely additive to the existing security surface.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-15
- **Tasks:** 2 completed (Task 2 executed as TDD: RED then GREEN)
- **Files modified:** 5 (2 created source, 1 created test, 2 modified)

## Accomplishments
- `PublicController` exposes `GET /api/v1/public/branding`: 200 with `{nome, logoDataUrl}` when a tenant exists (logo `null` serialized as explicit JSON null, no `@JsonInclude(NON_NULL)` anywhere in this codebase to suppress it), 404 with `{"message": "Sistema não inicializado."}` when no tenant exists — never a 500.
- `TenantPublicInfoResponse` DTO carries exactly `nome` + `logoDataUrl`; `nif`/`email`/`telefone`/`tipoEntidade`/`id`/`createdAt` are structurally impossible to leak because the DTO has no such fields.
- `TenantRepository.findFirstByOrderByCreatedAtAsc()` added as a Spring Data derived query (no JPQL) resolving the singleton tenant deterministically.
- `SecurityConfig` allowlist gained exactly one new literal entry, `"/api/v1/public/branding"` — confirmed no wildcard (`/api/v1/public/**`) was introduced.
- `PublicControllerTest` (Mockito, no Spring context) proves all 3 required behaviors: 404/no-tenant, 200/tenant-with-logo, 200/tenant-with-null-logo.
- Full backend unit suite re-run after the change: 72/72 tests green (6 suites: PublicControllerTest, ResourceControllerUploadDocumentoTest, AlertasDiariosJobTest, ClienteNifValidationTest, NotificacaoServiceTest, RiscoPrazoServiceTest) — confirms the phase is purely additive with zero regressions to existing `@PreAuthorize`-guarded endpoints.

## Task Commits

Task 1 was a single `auto` task; Task 2 was `tdd="true"` and produced a RED + GREEN commit pair (no REFACTOR commit — implementation was already minimal, mirroring existing controller patterns with nothing to clean up):

1. **Task 1: DTO TenantPublicInfoResponse + derived query em TenantRepository** - `6833c9e` (feat)
2. **Task 2 RED: failing PublicControllerTest** - `7f94cd2` (test)
3. **Task 2 GREEN: PublicController + SecurityConfig allowlist entry** - `025635c` (feat)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/dtos/TenantPublicInfoResponse.java` - New DTO, exactly `nome`+`logoDataUrl`, Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- `backend/src/main/java/com/lexcv/controllers/PublicController.java` - New controller, `GET /api/v1/public/branding`, stateless, explicit builder copy
- `backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java` - New Mockito test, 3 tests covering 404/200/200-with-null-logo
- `backend/src/main/java/com/lexcv/repositories/TenantRepository.java` - Added `Optional<Tenant> findFirstByOrderByCreatedAtAsc()`
- `backend/src/main/java/com/lexcv/config/SecurityConfig.java` - Added exact-literal `"/api/v1/public/branding"` to the `permitAll()` array

## Decisions Made
- Followed the plan and CONTEXT.md exactly — no new decisions required beyond what CONTEXT.md and the plan's `<interfaces>` block already specified (method naming, error message text, and DTO shape were all pre-decided).
- No REFACTOR commit for Task 2: the GREEN implementation already matched the established narrow-controller/explicit-copy patterns with no cleanup opportunity, so an empty REFACTOR commit was correctly skipped per the TDD execution rules ("only commit if changes made").

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Bash tool's `grep` (proxied through the environment's `rtk` hook per user global config) produced false negatives on patterns containing double-quote characters (e.g. `grep -F '"/api/v1/public/branding"'` reported no match against a file confirmed byte-for-byte to contain that exact literal). Root-caused via `xxd`/isolated test files, then resolved by switching to the dedicated `Grep` tool for all subsequent content-search verification, per this agent's tool-selection instructions. Not a code defect — purely a verification-tooling issue in this session, and all downstream verification was redone and confirmed with the `Grep` tool.

## User Setup Required

None - no external service configuration required. Zero new dependencies (per threat model T-98-SC), so no package installation or environment variables are needed.

## Next Phase Readiness

- Phase 99 (`webpage/` landing app) can now consume a real `GET /api/v1/public/branding` response shape (`{nome, logoDataUrl}`, 404-on-empty) instead of its hardcoded stub, whenever that phase is ready to wire it in — this was the one real data dependency blocking Phase 99 per PROJECT.md.
- The plan's item #5 verification (human-check: live `curl` against a running backend with an initialized system) was not run in this session — it requires a running Spring context + reachable PostgreSQL, which is outside this executor's automated gate. All 4 automated verification items (Mockito test, SecurityConfig literal/no-wildcard, DTO field review, `git diff --name-only` scope) passed. This live-server check is the same category of pending item the project already tracks via `NEEDS-HUMAN-VISUAL`/`human_needed` entries in STATE.md for prior phases — recommend a quick manual/orchestrator-level `curl -s -i http://localhost:8080/api/v1/public/branding` once the backend is running with `SEED_ENABLED=true` or a real `/setup` initialization, before Phase 100's full `docker compose up` integration check.
- No blockers for Phase 99 or Phase 100.

## Self-Check: PASSED

All created files verified present on disk:
- FOUND: backend/src/main/java/com/lexcv/dtos/TenantPublicInfoResponse.java
- FOUND: backend/src/main/java/com/lexcv/controllers/PublicController.java
- FOUND: backend/src/main/java/com/lexcv/repositories/TenantRepository.java
- FOUND: backend/src/main/java/com/lexcv/config/SecurityConfig.java
- FOUND: backend/src/test/java/com/lexcv/controllers/PublicControllerTest.java

All commits verified present in git log:
- FOUND: 6833c9e (feat: DTO + derived query)
- FOUND: 7f94cd2 (test: RED — failing PublicControllerTest)
- FOUND: 025635c (feat: GREEN — PublicController + SecurityConfig)

TDD gate sequence confirmed: `test(98-01)` commit precedes `feat(98-01)` GREEN commit in `git log --oneline` order.

`git diff --name-only` against the pre-plan base commit (`6675d22`) shows exactly the 5 files declared in the plan's `files_modified` frontmatter — no scope creep.

Full backend unit suite (`mvn test`, all 6 `*Test.java` classes): 72/72 passing, 0 failures, 0 errors.

---
*Phase: 98-backend-endpoint-publico-de-branding*
*Completed: 2026-07-15*
