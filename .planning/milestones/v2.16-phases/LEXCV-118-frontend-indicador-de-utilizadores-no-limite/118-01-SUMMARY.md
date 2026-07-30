---
phase: 118-frontend-indicador-de-utilizadores-no-limite
plan: 01
subsystem: auth
tags: [spring-boot, jwt-session, dto, multi-tenant, rbac]

# Dependency graph
requires:
  - phase: 117
    provides: "Tenant.plano (TenantPlano enum, nullable) and Tenant.limiteUtilizadores (Integer, nullable = sem limite) columns, plus UserRepository.countByTenantIdAndAtivoTrue — the authoritative 409 enforcement in AdminController.limiteUtilizadoresExcedido"
provides:
  - "GET /api/v1/auth/me now returns tenant_plano (String enum-name or null) and tenant_limite_utilizadores (Integer or null) to every authenticated session, any role"
  - "UserResponse DTO carries the 2 new snake_case fields alongside the pre-existing tenant_nome/tenant_logo_data_url"
  - "Zero new queries: both fields are read from the Tenant row already fetched by the pre-existing tenantRepository.findById(...).ifPresent(...) block"
affects: [118-02, 118-03, "Phase 120 (PROV provisioning console will read the same /auth/me contract for its own tenant context)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Tenant-scoped fields exposed via /auth/me are added inside the existing tenantRepository.findById(...).ifPresent(...) lambda, never a second query or a second ifPresent block"
    - "Nullable enum-to-JSON: t.getPlano() != null ? t.getPlano().name() : null (never String.valueOf/toString, which would produce the literal string \"null\")"
    - "Nullable Integer passed straight through with no ternary/orElse, so the backend's 'null = sem limite' contract crosses the HTTP boundary intact"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeTenantPlanoTest.java
  modified:
    - backend/src/main/java/com/lexcv/dtos/UserResponse.java
    - backend/src/main/java/com/lexcv/controllers/AuthController.java

key-decisions:
  - "Extended the existing GET /auth/me endpoint instead of creating a new one — reuses the Tenant row already loaded by the pre-existing tenantRepository.findById(...).ifPresent(...) block, zero new queries, zero new authorization surface"
  - "tenant_limite_utilizadores kept as boxed Integer (never int/primitive) so null (sem limite) is never coerced to 0"
  - "tenant_plano serialized via .name() with an explicit null-guard ternary, never String.valueOf()/toString() (which would emit the literal string \"null\")"
  - "No @PreAuthorize added to getMe or any AuthController method — the 2 new fields are commercial plan metadata (plan tier name + seat-capacity integer), same class/audience as the pre-existing tenant_nome/tenant_logo_data_url already served to every role since before this phase; role-gating for the indicator itself lives in the frontend (Plan 02, users:manage tab visibility)"

patterns-established:
  - "Extend-don't-duplicate: any future tenant-scoped field destined for every authenticated session goes inside the same getMe() tenantRepository.findById(...).ifPresent(...) lambda"

requirements-completed: [PLAN-03]

# Metrics
duration: ~9min
completed: 2026-07-29
---

# Phase 118 Plan 01: Backend — expor tenant_plano/tenant_limite_utilizadores em /auth/me Summary

**`GET /api/v1/auth/me` agora devolve `tenant_plano` (String do enum ou `null`) e `tenant_limite_utilizadores` (Integer ou `null` = sem limite) a qualquer sessão autenticada, adicionados ao bloco `ifPresent` já existente do `Tenant`, sem query nova nem endpoint novo — fecha o gap de dados que bloqueava o indicador "X/Y" do Plan 02.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-07-29T02:49:00-01:00 (approx.)
- **Completed:** 2026-07-29T02:57:00-01:00 (approx.)
- **Tasks:** 2/2 completed
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `UserResponse.java` gained `tenant_plano` (String) and `tenant_limite_utilizadores` (boxed `Integer`) fields, matching the exact snake_case convention of the sibling `tenant_nome`/`tenant_logo_data_url` fields.
- `AuthController.getMe()` maps both fields inside the pre-existing `tenantRepository.findById(principal.getTenantId()).ifPresent(t -> {...})` lambda — confirmed by gate to still fire `findById` exactly once per request.
- `AuthControllerGetMeTenantPlanoTest` (new, 4 cases, RED then GREEN as two separate commits) proves: numeric plano+limite pass through, `null` limite is never coerced to `0`, `null` plano never throws `NullPointerException`, and the sibling fields (`tenant_nome`/`tenant_logo_data_url`) plus the single-query invariant are unbroken.
- Full backend regression gate green: 97/97 unit tests (93 pre-existing + 4 new), including `AdminControllerLimiteUtilizadoresTest` (9/9, Phase 117's authoritative 409 enforcement untouched), `mvn spotbugs:check` clean (0 findings), and `mvn -DskipTests package` producing the Spring Boot jar without errors.

## Task Commits

Each task was committed atomically (TDD RED/GREEN split per plan instruction):

1. **Task 1 (RED): add failing test** - `2901ebb` (test) — `AuthControllerGetMeTenantPlanoTest` written first, confirmed failing to compile (`getTenant_plano()`/`getTenant_limite_utilizadores()` did not exist yet).
2. **Task 1 (GREEN): implement fields** - `fa5cf83` (feat) — `UserResponse.java` + `AuthController.java` extended; all 4 tests pass.
3. **Task 2: regression/SAST/STRIDE gate** - no commit (verification-only task; every gate passed cleanly on first run, nothing required fixing — see below).

**Plan metadata:** committed separately after this SUMMARY (see final commit).

_Note: Task 1 is `tdd="true"` — RED and GREEN are intentionally two separate commits, matching the Phase 117 precedent recorded in STATE.md._

## Files Created/Modified

- `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeTenantPlanoTest.java` (created) — 4 Mockito-based cases (no MockMvc/`@SpringBootTest` in this codebase, matches `AdminControllerLimiteUtilizadoresTest`/`PublicControllerTest` convention).
- `backend/src/main/java/com/lexcv/dtos/UserResponse.java` (modified) — 2 new fields: `private String tenant_plano;` and `private Integer tenant_limite_utilizadores;`.
- `backend/src/main/java/com/lexcv/controllers/AuthController.java` (modified) — 2 new setter calls inside the existing `tenantRepository.findById(...).ifPresent(...)` lambda in `getMe()`.

## Decisions Made

See `key-decisions` in frontmatter. No decisions deviated from what `118-CONTEXT.md`/`118-PATTERNS.md` had already locked — this plan was pure "extend an existing, fully-specified block."

## Deviations from Plan

None — plan executed exactly as written. All acceptance-criteria gates (test count, `assertNull` count, single-query proof, boxed-`Integer` type, zero `@JsonProperty`/`@PreAuthorize`, zero `pom.xml`/`AdminController.java` diff) passed on the first run; no auto-fixes were needed under Rules 1-3.

## Issues Encountered

None. One local environment note: this session's shell has a global `rtk` hook that intercepts/summarizes piped Bash output (per the executor's own `<known_environment_note>`); a piped `git diff --name-only ... | wc -l` momentarily reported a misleading non-zero count. Re-running the same `git diff` unpiped (and via `git diff --stat`) confirmed the true, empty result — `pom.xml` and `AdminController.java` are genuinely untouched. No code issue, no plan issue — just a reminder that raw (unpiped) commands are the source of truth in this environment.

## STRIDE Mitigation Verdicts (Task 2)

Per the plan's `<threat_model>`, verified by direct assertion against the delivered code (audit style, not generic checklist):

- **T-118-01 (Information Disclosure — campos novos visíveis a todos os papéis): CONFIRMADO.** `tenant_plano`/`tenant_limite_utilizadores` são metadados comerciais do próprio tenant do chamador (nome de escalão + inteiro de capacidade) — não são segredo, PII, nem credencial. São da mesma classe e audiência que `tenant_nome`/`tenant_logo_data_url`, já servidos a todos os papéis autenticados por este mesmo método desde antes desta fase (precedente: v2.15 Phase 116, `/auth/me` já serve `tenant_nome` a qualquer utilizador autenticado). Ambos os campos novos vêm exclusivamente de `tenantRepository.findById(principal.getTenantId())` — nenhum caminho observa o plano de outro tenant. ASVS L1 V8.1 satisfeito.
- **T-118-02 (Elevation of Privilege — confiança no limite exposto ao cliente): CONFIRMADO.** `git diff --name-only -- pom.xml src/main/java/com/lexcv/controllers/AdminController.java` (a partir de `backend/`) devolve zero linhas — `AdminController.java` não aparece no diff desta fase. `AdminControllerLimiteUtilizadoresTest` continua 9/9 verde na suite completa. O valor exposto por `/auth/me` é espelho de UX; `AdminController.limiteUtilizadoresExcedido` continua a única fonte de verdade autoritária do `409`.
- **T-118-03 (Tampering — origem do tenant id em `getMe`): CONFIRMADO.** `tenantRepository.findById` aparece exatamente 1 vez em `AuthController.java`, sempre com `principal.getTenantId()`. Zero ocorrências de `body.get("tenant_id")`/`body.get("tenantId")`/`containsKey("tenant_id")` no ficheiro — nenhum tenant id é lido do pedido.
- **T-118-SC (Tampering — cadeia de fornecimento): CONFIRMADO.** `backend/pom.xml` não aparece no diff desta fase (mesmo gate acima) — zero dependências novas instaladas, Package Legitimacy Gate não se aplica.

## Verification Gate Results (Task 2)

| Gate | Result |
|------|--------|
| `mvn test -Dtest=AuthControllerGetMeTenantPlanoTest` | 4/4 tests, 0 failures, 0 errors |
| `mvn test` (full unit suite) | 97/97 tests, 0 failures, 0 errors, BUILD SUCCESS |
| `mvn test -Dtest=AdminControllerLimiteUtilizadoresTest` | 9/9 tests green — Phase 117 authoritative enforcement intact |
| `mvn spotbugs:check` | 0 bug instances, 0 errors, BUILD SUCCESS — no `spotbugs-exclude.xml` changes needed |
| `mvn -DskipTests package` | BUILD SUCCESS, `backend-0.0.1-SNAPSHOT.jar` produced |
| `tenantRepository.findById` occurrence count in `AuthController.java` | 1 (zero new queries) |
| `@PreAuthorize` / `@JsonProperty` count in touched files | 0 / 0 |
| `git diff --stat` (RED+GREEN commits, `backend/`) | exactly 3 files — matches `files_modified` in plan frontmatter |

## Exact JSON Contract for Plan 02

`GET /api/v1/auth/me` response shape (new fields only shown inline; unaffected pre-existing fields omitted here for brevity — see `UserResponse.java` for the full DTO):

Tenant with a numeric plan limit:
```json
{
  "tenant_nome": "Escritório X",
  "tenant_logo_data_url": "data:image/png;base64,AAA",
  "tenant_plano": "STANDARD",
  "tenant_limite_utilizadores": 5
}
```

Tenant with "sem limite" (Enterprise, or any plan with a null `limiteUtilizadores`):
```json
{
  "tenant_plano": "ENTERPRISE",
  "tenant_limite_utilizadores": null
}
```

Tenant with `plano` not yet set (legacy/never-configured row):
```json
{
  "tenant_plano": null,
  "tenant_limite_utilizadores": 10
}
```

Plan 02 should extend `web/src/types/auth.ts`'s `MeResponse` with:
```typescript
tenant_plano?: string;
tenant_limite_utilizadores?: number | null;
```
and treat `null`/`undefined` on `tenant_limite_utilizadores` identically (no indicator, "Novo Utilizador" never disabled by this rule) — per `118-CONTEXT.md`.

## User Setup Required

None — no external service configuration required. No new environment variables, no new migrations (this phase adds zero columns; it only reads columns Phase 117 already created).

## Next Phase Readiness

- The frontend data gap identified in `118-CONTEXT.md` (`tenant_limite_utilizadores` unreachable from any endpoint) is now closed. Plan 02 (frontend `MeResponse` type + `UserManagementTab` indicator) can proceed without further backend work.
- No blockers. The backend contract is proven by 4 automated tests and matches `118-UI-SPEC.md`'s and `118-PATTERNS.md`'s exact field names/types (`tenant_plano?: string`, `tenant_limite_utilizadores?: number | null`).

---
*Phase: 118-frontend-indicador-de-utilizadores-no-limite*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeTenantPlanoTest.java`
- FOUND: `backend/src/main/java/com/lexcv/dtos/UserResponse.java`
- FOUND: `backend/src/main/java/com/lexcv/controllers/AuthController.java`
- FOUND: `.planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-01-SUMMARY.md`
- FOUND commit: `2901ebb` (test - RED)
- FOUND commit: `fa5cf83` (feat - GREEN)
