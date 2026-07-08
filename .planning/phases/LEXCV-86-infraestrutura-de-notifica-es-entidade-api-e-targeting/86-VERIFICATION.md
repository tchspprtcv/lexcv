---
phase: LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting
verified: 2026-07-08T23:55:00Z
status: human_needed
score: 13/15 must-haves verified (2 uncertain, 0 failed)
overrides_applied: 0
human_verification:
  - test: "Run one live round trip against GET /api/v1/notificacoes on a real dev DB (migration applied, seeded tenant + 2 test users + notification rows), covering all four filter combinations: no filters, categoria only, lida only, both. Also hit GET /unread-count, PATCH /{id}/lida (own row and a foreign row), and POST /ler-todas."
    expected: "Correct pagination envelope (content/totalElements/totalPages/page/size), correct categoria/lida filtering, correct per-user isolation (user B never sees/affects user A's rows), 404 (not 403/500) for a foreign notification id."
    why_human: "NotificacaoRepository.buscarPorFiltros is the first-ever combination in this codebase of nativeQuery=true + a hand-written countQuery + a Pageable argument. It has never executed against a real PostgreSQL instance — no H2/Testcontainers or any integration-test infrastructure exists in this backend (confirmed: grep of backend/pom.xml for h2/testcontainers returns zero matches), so it has only ever run through Mockito mocks that don't validate real SQL. Three independent AI code-review cycles (86-REVIEW.iter2.md, .iter3.md, 86-REVIEW.md) each re-raised this exact gap and each explicitly declined to resolve it (0 Critical, Warning severity each time) because a live DB round trip is outside an automated fixer's/verifier's authority. No concrete defect was found in the SQL itself by static reading (column names match the entity, the CAST(:param AS type) null-guard idiom is a proven pattern already used successfully in ParecerSolicitacaoRepository.pesquisar), but this is genuinely un-executed code on the phase's primary read path."
  - test: "Load the Admin Settings screen (web/src/app/(dashboard)/settings/page.tsx) as an ADMIN and confirm a 'Notificações' permission group with a 'Visualizar Notificações' / notificacoes:view checkbox renders in both the RBAC matrix and the per-user permission-override grid, and that toggling it persists."
    expected: "The new scope is visible and manageable exactly like the existing pareceres:* entries, per Plan 86-03's explicit must-have truth #4."
    why_human: "The PermissionDefDto data entry is confirmed present in AdminController.getRbac() (verified by direct code read), and the settings page is confirmed to already iterate systemPermissions generically — but no browser session was used to confirm actual rendering/toggling. Low risk (mechanical reuse of an already-working pattern), included for completeness rather than as a serious concern."
---

# Phase 86: Infraestrutura de Notificações — Entidade, API e Targeting Verification Report

**Phase Goal:** Existe uma API de notificações persistidas, funcional e segura — cada notificação é dirigida apenas à entidade diretamente ligada mais ADMIN, nunca em massa por permissão de visualização, com estado lido/não-lido isolado por destinatário.
**Verified:** 2026-07-08T23:55:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Methodology Note

This phase went through a 3-plan wave sequence (86-01 → 86-02 → 86-03) and then a **3-iteration adversarial code-review/auto-fix loop** (`86-REVIEW.md`/`.iter2.md`/`.iter3.md` + matching `86-REVIEW-FIX*.md`) that materially changed the code after the three `*-SUMMARY.md` files were written. I verified the **current committed state of the code** (confirmed clean via `git status --porcelain` on every touched file — no uncommitted drift), not the SUMMARY narratives, and independently re-ran the compiler and test suite rather than trusting any prior report's claims. I also independently reconstructed the true chronology of the 6 review/fix documents from cross-referenced finding IDs (their frontmatter timestamps are internally inconsistent/out of order) to confirm the final code state reflects the last (3rd) fix iteration, not an intermediate one.

Commits cited below were verified to exist via `git cat-file -e` and to be present in `master`'s history (not an abandoned branch): `6e77068`, `009c441`, `84f96c6`, `625e935`, `70da997`, `f734ad4`, `1b095af`, `2f48092`, `35afb15`, `aa8dd78`, `66ad927`, `429767e`, `dae0d14`, `c6baef6` — all found, all with matching commit messages.

## Goal Achievement

### ROADMAP Success Criteria (authoritative contract, Step 2a)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | `t_notificacao` table persisted (manual migration documented) + 4 endpoints (`GET /notificacoes` filters+pagination, `GET /unread-count`, `PATCH /{id}/lida`, `POST /ler-todas`) **funcionam**, all scoped by tenant AND destinatario | ? UNCERTAIN | Migration `backend/migrations/86-create-notificacao-table.sql` exists with correct `CREATE TABLE t_notificacao` (11 columns matching the entity exactly) + `CREATE INDEX idx_notificacao_tenant_destinatario_lida_created ON t_notificacao (tenant_id, destinatario_id, lida, created_at)`. All 4 endpoints exist in `NotificacaoController.java`, each `@PreAuthorize("hasAuthority('notificacoes:view')")`, each sourcing `tenantId`/`userId` exclusively from `getTenantId()`/`getUserId()` (never request input). `mvn -o -DskipTests compile`: **BUILD SUCCESS** (ran independently). However, "funcionam" (function/work) has never been demonstrated live: no HTTP round-trip against a running server + real Postgres was performed (86-03-SUMMARY.md itself states this), and the riskiest endpoint, `GET /notificacoes`, is backed by `NotificacaoRepository.buscarPorFiltros` — the first-ever `nativeQuery=true` + hand-written `countQuery` + `Pageable` combination in this codebase — which 3 independent review cycles flagged as never executed against Postgres. See Human Verification. |
| 2 | Two test users in the same tenant receive independent notification lists; marking one read by a user never affects the same notification's state for another destinatario | ? UNCERTAIN (compound truth — one half solid, one half shares Criterion 1's gap) | **Mark-read isolation half — solid:** `NotificacaoServiceTest.marcarLida_naoPertenceAoDestinatarioOuInexistente_retornaVazioENuncaChamaSave` (asserts `Optional.empty()` + `save()` never called for a foreign/nonexistent row) and `marcarLida_pertenceAoDestinatario_marcaLidaTrueEChamaSaveUmaVez` both pass (independently re-ran: 9/9 green). The backing finder, `findByIdAndTenantIdAndDestinatarioId`, is a plain Spring-Data-derived query (not custom SQL) — architecturally low residual risk. **Independent-lists half — unverified:** same `buscarPorFiltros` native-query gap as Criterion 1. |
| 3 | A notification directed at "ADMIN" generates its own row per current ADMIN of the tenant at creation time (fan-out), each with independent read state | ✓ VERIFIED | `NotificacaoService.notificarAdmins` (lines 81-87, now `@Transactional` after the final review-fix iteration) loops `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` and calls `criar(...)` once per admin. `NotificacaoServiceTest.notificarComFanOutAdmin_umaLinhaPorAdminAtualDoTenant` stubs 2 admins, asserts exactly 2 `save()` calls via `ArgumentCaptor`, asserts each captured row's `destinatarioId` matches the correct admin (not a shared row), and asserts `lida==false` for each. Independently re-ran: **PASS** (part of 9/9 `NotificacaoServiceTest`, 24/24 full suite). `findByTenantIdAndRoleName` is a pre-existing, already-proven derived query — no native-SQL risk here. |
| 4 | `notificacoes:view` scope seeded for all 4 profiles (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) in both backend (`DatabaseSeeder`) and frontend (`KNOWN_SCOPES`) | ✓ VERIFIED | `DatabaseSeeder.seedRbac()`: `"notificacoes:view"` in `permKeys` (line 303, → ADMIN via `upsertRolePermissions("ADMIN", permissionMap.values())`, line 313) plus explicit grants to ASSISTENTE (line 322), TECNICO (line 333), ADVOGADO (line 351) — 4 total occurrences, matching the plan's own acceptance bound. `web/src/lib/permissions.ts` `KNOWN_SCOPES` includes `"notificacoes"` (line 13). Bonus (required by Plan 86-03, not literally in this SC's text): `AdminController.getRbac()`'s `systemPermissions` also lists a `notificacoes:view` `PermissionDefDto` (line 232, módulo "Notificações"). |

**Score:** 2/4 ROADMAP criteria cleanly verified; 2/4 UNCERTAIN (both trace to the same single root cause: one never-executed native SQL query).

### Observable Truths (Plan-Level Detail, Step 2b)

| # | Truth (source plan) | Status | Evidence |
|---|-------|--------|----------|
| 1 | `t_notificacao` table exists with `tenant_id`/`destinatario_id` `NOT NULL` + composite index `(tenant_id, destinatario_id, lida, created_at)` (86-01) | ✓ VERIFIED | Migration file read directly: both columns `NOT NULL`, index created on the exact 4-column order specified. |
| 2 | One row per (event, recipient) with its own per-row `lida` flag — never a shared row with computed visibility (86-01) | ✓ VERIFIED | `Notificacao.java`: `destinatarioId` is a first-class `NOT NULL` column, `lida` is `@Builder.Default = false` per row. `criar()` is called once per recipient (see Truth 6/5 below); no "visibility" computation exists anywhere in the codebase. |
| 3 | Every `NotificacaoRepository` finder filters by BOTH `tenant_id` AND `destinatario_id` — no tenant-only finder exists (86-01) | ✓ VERIFIED | Full file read: exactly 4 methods (`buscarPorFiltros`, `countByTenantIdAndDestinatarioIdAndLidaFalse`, `findByTenantIdAndDestinatarioIdAndLidaFalse`, `findByIdAndTenantIdAndDestinatarioId`), all dual-scoped. Grep for a `findByTenantId(`-only signature (excluding `AndDestinatario`) → 0 matches. |
| 4 | `NotificacaoService` is the sole code path calling `notificacaoRepository.save(...)/saveAll(...)` — no other class ever persists a `Notificacao` directly (86-02) | ✓ VERIFIED | Full-tree grep (`grep -rn "notificacaoRepository\.(save|saveAll)\(" backend/src`) → production hits only at `NotificacaoService.java:60,104,114` (plus 3 test-mock stubs). Full-tree grep for `NotificacaoRepository`/`NotificacaoService` references → only the service, the controller, and the repository itself reference either — no Phase-87-style caller has leaked in early. |
| 5 | A notification directed at ADMIN produces one independent row per current ADMIN, each with its own `lida` state (86-02) | ✓ VERIFIED | Same evidence as ROADMAP SC3 above. |
| 6 | Two distinct recipients get independent rows — never a shared row — proven by an automated Mockito test (86-02) | ✓ VERIFIED | `criar_doisDestinatariosDistintos_geramLinhasIndependentesComEstadoLidaProprio`: `ArgumentCaptor` shows the first saved row's `destinatarioId == DESTINATARIO_A`, the second's `== DESTINATARIO_B`. Re-ran independently: PASS. |
| 7 | `marcarLida(...)` returns empty `Optional` (never calls save) when the id doesn't belong to the given tenant+destinatario (86-02) | ✓ VERIFIED | `marcarLida_naoPertenceAoDestinatarioOuInexistente_retornaVazioENuncaChamaSave` — re-ran independently: PASS. Code at `NotificacaoService.java:97-105` matches exactly. |
| 8 | All 4 endpoints scope by tenant AND caller userId — 2 GET via repository directly, 2 write via `NotificacaoService`, never a direct `notificacaoRepository.save/saveAll` call in the controller (86-03) | ✓ VERIFIED | This truth is specifically about **request-scoping wiring**, which is fully verified by code inspection: `NotificacaoController.java` — `listar`/`contarNaoLidas` call `notificacaoRepository.*` directly with both ids; `marcarLida`/`marcarTodasLidas` call `notificacaoService.*`; grep for `notificacaoRepository.save` in the controller file → 0 matches. (The separate question of whether the underlying native SQL correctly executes live is captured under ROADMAP SC1/SC2 above, not re-litigated here.) |
| 9 | Marking read returns 404 (never 403) for a cross-recipient id — no existence leak (86-03) | ✓ VERIFIED | `NotificacaoController.marcarLida` (lines 95-101): `HttpStatus.NOT_FOUND` on empty `Optional` from the service. Grep confirms no `FORBIDDEN`/403 anywhere in the file. |
| 10 | `notificacoes:view` seeded for all 4 roles + registered in `KNOWN_SCOPES` (86-03) | ✓ VERIFIED | Same evidence as ROADMAP SC4. |
| 11 | `notificacoes:view` listed in `AdminController.getRbac()`'s `systemPermissions` — visible/manageable on Admin Settings RBAC screen, not just silently seeded (86-03) | ✓ VERIFIED (data-level) | `AdminController.java:232` confirmed. Live browser rendering not confirmed — see secondary Human Verification item (low risk, generic `systemPermissions.map(...)` rendering already used by `pareceres:*`). |

**Score:** 11/11 plan-level truths verified — every specific engineering claim the 3 plans made was actually implemented, matching or exceeding (see review-driven hardening below) what was specified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/Notificacao.java` | Entity, contains `destinatarioId`, ≥40 lines | ✓ VERIFIED | 58 lines. `destinatario_id UUID NOT NULL` present; `lida` `@Builder.Default=false`; class-level `@Setter` was removed during code review (WR-03) leaving `@Setter` only on `lida` — a stricter, intentional hardening beyond the plan's literal text, confirmed by full-tree grep showing `setLida(...)` is the only setter ever called on a `Notificacao` anywhere in the codebase. |
| `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` | Dual-scoped surface incl. `Pageable`, contains `findByIdAndTenantIdAndDestinatarioId` | ✓ VERIFIED (existence/substance); native query untested live | 52 lines, 4 methods, all dual-scoped. See Human Verification for `buscarPorFiltros`. |
| `backend/migrations/86-create-notificacao-table.sql` | Manual prod DDL, contains `CREATE TABLE t_notificacao` | ✓ VERIFIED | 34 lines; header comment correctly documents the manual-migration requirement and `ddl-auto` rationale (cross-checked against `application.yml`: dev=`update`, `application-prod.yml`: `validate` — matches). |
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | Write choke point, contains `public Notificacao criar` | ✓ VERIFIED | 117 lines. Substantially hardened beyond the original plan text via 3 review-fix iterations: input validation (tenant-ownership check, non-blank checks, `VARCHAR(255)` length guards), `@Transactional` on all 3 public/package-private write methods. |
| `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` | Automated proof, contains `notificarComFanOutAdmin` | ✓ VERIFIED | 225 lines, **9 `@Test` methods** (grew from the original plan's 5 via 2 review-fix iterations adding `criar_destinatarioDeOutroTenant_...`, `criar_tituloExcede255Caracteres_...`, `criar_camposComTamanhoExcedido_...`, `criar_camposObrigatoriosEmBranco_...`). Independently re-ran: **9/9 passing**, no `@Disabled`/`@Ignore` found. |
| `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` | REST surface, contains `getUserId`, ≥60 lines | ✓ VERIFIED | 109 lines. All 4 endpoints present. Gained a `page`/`size` bounds check (`page < 0 \|\| size < 1 \|\| size > 100`) via 2 review-fix iterations (WR-05 then WR-04) beyond the original plan text. |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | Contains `notificacoes:view` in `systemPermissions` | ✓ VERIFIED | Line 232, correct módulo grouping ("Notificações"), positioned after the `pareceres:*` block as specified. |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` | Contains `notificacoes:view` granted to 4 roles | ✓ VERIFIED | 4 occurrences confirmed (permKeys + ASSISTENTE + TECNICO + ADVOGADO; ADMIN via `permissionMap.values()`). |
| `web/src/lib/permissions.ts` | Contains `notificacoes` in `KNOWN_SCOPES` | ✓ VERIFIED | Line 13, additive, `resolveScopedPermissions`/`hasScopedPermission` already scope-agnostic. |

All 9 declared artifacts: exist, are substantive (no stubs, no placeholder returns, no debt markers beyond a Portuguese-language false-positive grep hit on "método"), and are wired.

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `NotificacaoRepository` | `t_notificacao (tenant_id, destinatario_id)` | derived + native finders bind both columns | ✓ WIRED | `TenantIdAndDestinatario` pattern found in 3 derived method names; native query's `WHERE` clause hard-codes both as non-optional predicates. |
| `NotificacaoService.notificarAdmins` | `UserRepository.findByTenantIdAndRoleName` | per-ADMIN fan-out loop | ✓ WIRED | Confirmed at `NotificacaoService.java:84`. |
| `NotificacaoService.criar` | `NotificacaoRepository.save` | single build-then-save choke point | ✓ WIRED | Confirmed at `NotificacaoService.java:60`. |
| `NotificacaoService.marcarLida`/`marcarTodasLidas` | `NotificacaoRepository.save`/`saveAll` | scoped find → mutate → persist | ✓ WIRED | Confirmed at lines 104, 114. |
| `NotificacaoController.listar`/`contarNaoLidas` | `NotificacaoRepository` dual-scoped finders | `getTenantId()`+`getUserId()` | ✓ WIRED | Both ids passed at lines 75-76, 89. |
| `NotificacaoController.marcarLida`/`marcarTodasLidas` | `NotificacaoService.marcarLida`/`marcarTodasLidas` | controller delegates, no direct repo write | ✓ WIRED | Lines 96, 106; 0 `notificacaoRepository.save` hits in the controller file. |
| `NotificacaoController` `@PreAuthorize` | `notificacoes:view` scope | `hasAuthority` gate on every endpoint | ✓ WIRED | 4/4 endpoints carry the annotation (grep count = 4). |
| `AdminController.getRbac()` | `systemPermissions` list | `PermissionDefDto` entry mirroring `pareceres:*` | ✓ WIRED | Confirmed at line 232. |

All 8 key links: WIRED.

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `NotificacaoController.listar` | `pageResult` | `notificacaoRepository.buscarPorFiltros(...)` — native SQL against `t_notificacao` | Structurally real (not hardcoded/static); live correctness unconfirmed | ⚠️ FLOWING (structural) — see Human Verification |
| `NotificacaoController.contarNaoLidas` | `count` | `notificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse` — derived query | Yes (low-risk derived query) | ✓ FLOWING |
| `NotificacaoController.marcarLida`/`marcarTodasLidas` | mutated `Notificacao`/list | `NotificacaoService` → derived finders + `save`/`saveAll` | Yes | ✓ FLOWING |

No hardcoded empty returns (`Map.of()`/`[]`/`null`) found in any response path — every controller method builds its response from an actual repository/service call result.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with all Notificacao classes | `cd backend && mvn -o -DskipTests compile` | BUILD SUCCESS | ✓ PASS |
| `NotificacaoServiceTest` passes fully | `cd backend && mvn -o test -Dtest=NotificacaoServiceTest` | `Tests run: 9, Failures: 0, Errors: 0` | ✓ PASS |
| No regressions in full backend suite | `cd backend && mvn -o test` | `Tests run: 24, Failures: 0, Errors: 0` (9 Notificacao + 15 RiscoPrazo) | ✓ PASS |
| No tenant-only finder exists | `grep` for `findByTenantId(` excluding `AndDestinatario` in `NotificacaoRepository.java` | 0 matches | ✓ PASS |
| `notificacaoRepository.save`/`saveAll` called only from `NotificacaoService` | full-tree grep across `backend/src` | Only `NotificacaoService.java` (+ test mocks) | ✓ PASS |
| Live HTTP round trip against `GET /api/v1/notificacoes` | N/A — no running server started (out of scope for a fast static verifier pass; a Postgres-like listener was observed on `localhost:5432` but was not touched, to avoid unintended writes to what may be a live dev database) | Not executed | ? SKIP → routed to Human Verification |

### Probe Execution

No `scripts/*/tests/probe-*.sh` convention exists in this repository (no `scripts/` directory at all) and neither PLAN nor SUMMARY files for this phase reference any probe script. **Step 7c: SKIPPED (no probes declared or found for this phase).**

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| NOTF-14 | 86-01, 86-02, 86-03 (all 3 declare it in frontmatter) | "Sistema dirige cada notificação apenas à entidade diretamente ligada ... mais ADMIN — nunca em massa por permissão de visualização" | ✓ SATISFIED | `NotificacaoService.criar`/`notificarAdmins` is the sole write path; one row per recipient/admin (never a shared/computed-visibility row); no tenant-only finder exists anywhere; proven by 9 passing automated tests plus full-tree grep confirmation. |

**Orphan check:** `.planning/REQUIREMENTS.md` traceability table maps exactly one requirement (NOTF-14) to Phase 86 (`| NOTF-14 | Phase 86 | Pending |`), and all 3 plans declare exactly `requirements: [NOTF-14]`. No orphaned requirements — full match, nothing declared-but-unclaimed, nothing expected-but-undeclared.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `Notificacao.java` | 51 | `createdAt` column missing `nullable = false` (migration DDL correctly has `NOT NULL`) | ℹ️ INFO | Cosmetic self-documentation inconsistency; harmless (`@PrePersist` always populates it). Re-verified valid across all 3 review cycles, left unfixed by design (`fix_scope: critical_warning` excludes Info). |
| `NotificacaoService.java` | 82-87 (`notificarAdmins`) | ADMIN fan-out doesn't filter by `ativo` (active) status | ℹ️ INFO | A deactivated admin still accumulates notification rows. Flagged 3x by review as "may be intentional, needs explicit product decision" — not a defect. |
| `NotificacaoService.java` | 82-87 (`notificarAdmins`) | No null-check on `notificarAdmins`'s own `tenantId` parameter (unlike its sibling `criar`) | ℹ️ INFO (confirmed during my own disconfirmation pass — see below) | A null `tenantId` would silently no-op (0 admins matched) rather than throw, unlike `criar`'s explicit guard. No live path reaches this today (no HTTP endpoint calls `notificarAdmins` yet — Phase 87 will be the first real caller). |
| `NotificacaoService.java` | `criar()` | `linkUrl` validated only for length, not scheme/format | ℹ️ INFO | No reachable untrusted-input path today (no HTTP endpoint exposes `criar()`'s raw params to a client). |
| `NotificacaoController.java` | `listar`/`marcarLida` | `Notificacao` JPA entity serialized directly as API response (no DTO) | ℹ️ INFO | No sensitive/lazy fields on this entity today; architectural nice-to-have before Phase 89 builds a frontend contract against it. |
| `NotificacaoController.java` | all 4 endpoints | `notificacoes:view` gates both read AND mark-read/mark-all-read (deviates from the project's documented `scope:action` convention, which normally reserves mutations for `edit`/`manage`) | ℹ️ INFO | Explicitly self-documented as deliberate in `86-CONTEXT.md` and the RBAC screen's own permission description ("Ver e marcar como lidas as notificações próprias") — not an oversight. |

No 🛑 BLOCKER and no ⚠️ WARNING anti-patterns remain in the current code — the 3-iteration adversarial review already drove every Warning-level finding to resolution or an explicit, reasoned, human-flagged deferral (the one exception, the native-query verification gap, is carried into this report's Human Verification section rather than silently dropped).

### Confirmation Bias Counter (disconfirmation pass, run deliberately against my own initial findings)

1. **Partially-met requirement found:** ROADMAP SC1/SC2's "funcionam"/"listas independentes" clauses — already the primary UNCERTAIN finding above, not newly discovered here but re-confirmed as the correct single most material gap.
2. **A test that passes but doesn't fully test its own name's claim:** `criar_doisDestinatariosDistintos_geramLinhasIndependentesComEstadoLidaProprio` asserts distinct `destinatarioId` values per saved row (proving "independent rows") but does **not** independently assert `lida==false` on either row in this specific test (unlike the sibling fan-out test, which does assert this). The "own read state" half of this test's own name is only indirectly covered — via the entity's `@Builder.Default` mechanism (separately verified to still be present) and via the fan-out test asserting it elsewhere in the suite — not through a direct assertion in this test itself. Cosmetic test-hygiene gap, not a functional one.
3. **An error path with no test coverage:** Calling `NotificacaoService.criar(null, ...)` or `criar(tenantId, null, ...)` (a literally-null `tenantId`/`destinatarioId`, as opposed to a wrong-tenant `destinatarioId`) is never exercised by any test — only the "wrong tenant" and "blank/oversized field" branches are tested, not the very first null-check branch (`if (tenantId == null || destinatarioId == null)`). Similarly, `notificarAdmins` has no test for a null `tenantId` input at all. Both are low-risk today (no live caller passes client-controlled values into either method yet), but are genuine, currently-uncovered branches.

None of these three findings change the overall verdict — they are minor test-hygiene observations, correctly not elevated to Warning/Blocker by 3 independent review cycles, and are reported per the mandatory disconfirmation-pass requirement rather than being silently omitted.

## Human Verification Required

### 1. Live round-trip test of the notification REST API against a real database

**Test:** Apply `backend/migrations/86-create-notificacao-table.sql` (or start the app in dev with `ddl-auto=update`) against a real PostgreSQL instance, seed a tenant with 2 users and several `Notificacao` rows across different `categoria`/`lida` combinations, then call `GET /api/v1/notificacoes` with all four filter combinations (none, `categoria` only, `lida` only, both), `GET /api/v1/notificacoes/unread-count`, `PATCH /api/v1/notificacoes/{id}/lida` (once for the caller's own row, once for the other user's row), and `POST /api/v1/notificacoes/ler-todas`.
**Expected:** Correct pagination envelope and filtering from `buscarPorFiltros`; each user only ever sees/affects their own rows; the foreign-row `PATCH` returns `404` (not `403`/`500`).
**Why human:** This is the first-ever `nativeQuery=true` + hand-written `countQuery` + `Pageable` combination in this codebase, never executed against real Postgres in any automated test (no H2/Testcontainers exists in this project). Three independent AI code-review cycles raised this exact gap and each explicitly declined to resolve it unilaterally, since doing so requires a reachable database, seeded data, and a running server — squarely a live/human verification action, not a source-code fix.

### 2. Visual confirmation of the `notificacoes:view` scope on the Admin Settings RBAC screen

**Test:** As an ADMIN, open Settings → RBAC and confirm a "Notificações" permission group with a "Visualizar Notificações" checkbox appears in both the main RBAC matrix and the per-user permission-override grid, and that toggling it for a non-ADMIN role persists correctly.
**Expected:** Renders and behaves identically to the existing `pareceres:*` entries.
**Why human:** The backing data (`AdminController.getRbac()`'s `systemPermissions` entry) is confirmed present by direct code read, and the settings page is confirmed to already iterate `systemPermissions` generically — but no browser session was used to confirm actual rendering. Low risk (mechanical reuse of an already-working, already-shipped pattern); included for completeness.

## Gaps Summary

No BLOCKER-level gaps. Every artifact the 3 plans specified exists, is substantive, and is correctly wired; every plan-level must-have truth (11/11) is verified against the actual current code, not just against the `*-SUMMARY.md` narratives — which in fact under-describe the final state (the summaries were written before 3 rounds of adversarial code review meaningfully hardened `NotificacaoService` with input validation and full `@Transactional` coverage, grew `NotificacaoServiceTest` from 5 to 9 tests, added `page`/`size` bounds checking to the controller, removed `Notificacao`'s class-level `@Setter`, and closed a real `UserPrincipal` permission-list drift). I independently re-ran the full test suite (24/24 passing, 0 regressions) and a clean compile, and cross-checked all 14 cited commit hashes against git history rather than trusting any prior report's claims.

The reason this is not a clean `passed` is a single, repeatedly-and-correctly-flagged **verification gap, not a code defect**: the phase's one native SQL query (`buscarPorFiltros`, backing `GET /notificacoes` — the very first endpoint in Success Criterion 1) has never executed against a real PostgreSQL instance, because no integration-test infrastructure (H2/Testcontainers) exists anywhere in this backend. This is a **pre-existing, project-wide testing-infrastructure limitation**, not something introduced or hidden by this phase — the same class of "static verification passed, live execution not performed" gap was identically deferred to `human_needed` for Phase 85 (this same milestone) and for Phases 75/76/79 (prior milestone, see `.planning/STATE.md`'s Deferred Items). Three independent AI review cycles found 0 Critical issues and explicitly, correctly declined to resolve this unilaterally, recommending exactly the manual verification captured in Human Verification Item 1 above.

All other findings (6 Info-level anti-patterns, 3 disconfirmation-pass observations) are either pre-existing patterns, explicitly documented deliberate decisions, or low-risk untested edge-branches with no live caller yet — none rise to Warning or Blocker severity.

---

*Verified: 2026-07-08T23:55:00Z*
*Verifier: Claude (gsd-verifier)*
