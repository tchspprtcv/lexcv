---
phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
verified: 2026-07-14T00:00:00Z
status: human_needed
score: 15/15 must-haves verified at code level (0 failed, 0 uncertain)
overrides_applied: 0
human_verification:
  - test: "Login as a seeded user, go to /settings -> 'Notificações' tab, confirm exactly 8 category toggles are visible and 'Prazo vencido' is absent from the list"
    expected: "8 toggles shown; 'Prazo vencido' (PRAZO_VENCIDO) never rendered as a mutable option"
    why_human: "Requires a running backend + reachable Postgres + real browser session; blocked in this sandbox by the pre-existing, documented MINIO_ENDPOINT environment gap (backend/.env has zero MINIO_* vars) that has also blocked live UAT for Phases 87/89/91/92 per STATE.md"
  - test: "Turn off the 'Nova fase' (FASE_ENTRADA) toggle, confirm a success toast, reload the page (F5), return to the tab"
    expected: "Toggle remains off after reload (PUT /notificacoes/preferencias/FASE_ENTRADA persisted, GET on reload reflects it)"
    why_human: "Live persistence-across-reload behavior; same MINIO_ENDPOINT/backend-start blocker"
  - test: "Turn 'Nova fase' back on, confirm toast, reload, confirm it stays on"
    expected: "DELETE /notificacoes/preferencias/FASE_ENTRADA removes the mute row; category resumes default-on delivery"
    why_human: "Live persistence-across-reload behavior; same blocker"
  - test: "(Optional, if a 2nd user in the same tenant is available) confirm user A's mute does not affect user B's toggles"
    expected: "Preferences are isolated per (tenant_id, user_id) — user B sees FASE_ENTRADA as still delivered"
    why_human: "Requires two live authenticated sessions against a running backend; same blocker. Code-level evidence (dual-scoped repository methods, guard queries by destinatarioId) strongly supports this but cannot be observed live in this sandbox"
  - test: "Direct API call: PUT /api/v1/notificacoes/preferencias/PRAZO_VENCIDO against a running backend"
    expected: "HTTP 400 with a message body (categoria não silenciável)"
    why_human: "Requires a running backend to observe the actual HTTP response; same blocker. Code-level evidence (controller catch block + service IllegalArgumentException + unit test asserting the exception is thrown) strongly supports this"
---

# Phase 93: NOTF-24 — Preferências de Notificação por Utilizador Verification Report

**Phase Goal:** Cada utilizador pode silenciar, só para si próprio, as categorias de notificação que não lhe interessam, com pelo menos uma categoria crítica sempre entregue e sem escape possível pelo job diário.
**Verified:** 2026-07-14
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

Merged from ROADMAP.md Success Criteria (canonical contract) and the 4 plans' `must_haves.truths` frontmatter.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | (Roadmap SC1 / 93-02) Silenciar uma categoria (ex. `FASE_ENTRADA`) impede `criar()` de persistir novas notificações dessa categoria para esse user, sem afetar outros users do mesmo tenant | ✓ VERIFIED (code level) | `NotificacaoService.criar()` guard: `isSilenciavelCategoria(categoria) && existsByTenantIdAndUserIdAndCategoria(tenantId, destinatarioId, categoria)` → `Optional.empty()`, no `save()` call. Dual-scoped by `(tenantId, destinatarioId, categoria)` — never by user alone. Unit test `criar_categoriaSilenciada_naoPersisteEDevolveOptionalVazio` (line 466) asserts `resultado.isEmpty()` + `verify(notificacaoRepository, never()).save(any())`. Live persistence/isolation across reload/users is human_needed (see below). |
| 2 | (Roadmap SC2 / 93-01, 93-02, 93-03, 93-04) `PRAZO_VENCIDO` não pode ser silenciado — ausente da UI, e uma tentativa direta via API é rejeitada com 400 | ✓ VERIFIED (code level) | `CategoriaNotificacao.PRAZO_VENCIDO(false)` is the only `false` among 9 constants. `criar()` checks `isSilenciavelCategoria` **before** the preference lookup — confirmed by test `criar_prazoVencidoComPreferenciaDeSilenciamento_aindaPersiste` (line 502), which asserts `resultado.isPresent()`, `save()` called once, and `existsByTenantIdAndUserIdAndCategoria` **never** invoked (true short-circuit, not just a stubbed false). `NotificacaoService.silenciarCategoria` throws `IllegalArgumentException` for `PRAZO_VENCIDO` (test `silenciarCategoria_prazoVencido_lancaIllegalArgumentException`, line 520). `NotificacaoController.silenciar()` (PUT) catches that exception and returns `ResponseEntity.badRequest().body(Map.of("message", ...))`. Frontend `NotificationPreferencesTab` filters `NOTIFICACAO_CATEGORIA_OPTIONS` with `.filter(o => o.value !== "PRAZO_VENCIDO")` — confirmed 8 remaining categories rendered. Live HTTP 400 confirmation is human_needed. |
| 3 | (Roadmap SC3 / 93-02) O job diário (`AlertasDiariosJob`), que chama `criar()` diretamente sem passar pelos 4 métodos `notificar*`, também respeita o silenciamento, sem alteração própria | ✓ VERIFIED | **Independently confirmed, not just trusted from SUMMARY:** `git diff 513ca7e..HEAD -- backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` produces **zero output** — the file is byte-for-byte unmodified across the entire phase (commits `594b89a`..`67bcabc`). `AlertasDiariosJob.notificar()` (line 317) calls `notificacaoService.criar(tenantId, destinatarioId, categoria, ...)` directly. Since the mute guard lives inside `criar()` itself (verified below, truth #6), the job inherits it automatically by construction — the exact mechanism the plan and CONTEXT.md require. |
| 4 | (Roadmap SC4 / 93-02, 93-03) Reativar uma categoria previamente silenciada volta a entregar notificações futuras dessa categoria normalmente | ✓ VERIFIED (code level) | `NotificacaoService.reativarCategoria` (`@Transactional`) calls `deleteByTenantIdAndUserIdAndCategoria` — confirmed idempotent derived delete, no `@Modifying`/`@Query`. Test `reativarCategoria_chamaDeleteDerivado` (line 561) verifies the exact call. `NotificacaoController.reativar()` (DELETE `/preferencias/{categoria}`) delegates directly. Once the preference row is gone, `criar()`'s guard condition (`existsBy...`) becomes false, so delivery resumes by construction. Live confirmation of resumed delivery after reload is human_needed. |
| 5 | (93-01) `NotificacaoPreferencia` join-table entity + `CategoriaNotificacao` enum (9 categories, only `PRAZO_VENCIDO` non-silenciable) + dual-scoped repository + manual migration exist and compile | ✓ VERIFIED | All 4 files read directly and confirmed on disk: `NotificacaoPreferencia.java` (`@Table(name="t_notificacao_preferencia")`, unique constraint on `(tenant_id, user_id, categoria)`, `@PrePersist`), `CategoriaNotificacao.java` (exactly 9 constants, `PRAZO_VENCIDO(false)`, non-throwing `fromString`/`isSilenciavelCategoria`), `NotificacaoPreferenciaRepository.java` (3 dual-scoped derived methods, zero `@Modifying`), `93-create-notificacao-preferencia-table.sql` (table + unique index, manual-migration header). `mvn -q -DskipTests compile` succeeds. |
| 6 | (93-02) Mute guard lives **only** inside `criar()`, not duplicated in any of the 5 `notificar*` trigger methods | ✓ VERIFIED (critical property, independently re-checked) | Full read of `NotificacaoService.java`: the guard block (`isSilenciavelCategoria(...) && existsByTenantIdAndUserIdAndCategoria(...)`) appears exactly once, inside `criar()` (lines 71-77). `notificarAdmins` (2 overloads), `notificarFaseEntrada`, `notificarProcessoAtribuido`, `notificarDocumentoNovo`, `notificarParecerAtribuido` contain no reference to `existsByTenantIdAndUserIdAndCategoria` or `isSilenciavelCategoria` — each simply calls `criar(...)` as a statement. This is the exact anti-pattern the phase was designed to avoid (guard duplicated at N call sites, job escaping) — confirmed absent. |
| 7 | (93-02) `silenciarCategoria` rejeita categoria desconhecida E `PRAZO_VENCIDO`; `reativarCategoria` remove a preferência; `listarCategoriasSilenciadas` devolve as categorias silenciadas do user | ✓ VERIFIED | All 3 methods read directly in `NotificacaoService.java` (lines 291-324); 6 dedicated unit tests exercise unknown-category rejection, PRAZO_VENCIDO rejection, idempotent insert (silenciar), idempotent delete (reativar), and listing. `mvn -q test -Dtest=NotificacaoServiceTest` → **29/29 passing** (re-run independently, not trusted from SUMMARY — surefire report confirms `Tests run: 29, Failures: 0, Errors: 0`). |
| 8 | (93-03) 3 REST endpoints (GET/PUT/DELETE `/notificacoes/preferencias(/{categoria})`), dual-scoped by JWT-derived `getTenantId()/getUserId()`, never accepting `userId` from the request body | ✓ VERIFIED | `NotificacaoController.java` read directly: `listarPreferencias()` (GET), `silenciar()` (PUT, catches `IllegalArgumentException` → 400), `reativar()` (DELETE) — all `@PreAuthorize("hasAuthority('notificacoes:view')")`, all scoped by `getTenantId(), getUserId()`, zero references to a request-body `userId`. `mvn -q -DskipTests compile` succeeds; independently re-ran the plan's own grep-assertion script — all 8 conditions pass. |
| 9 | (93-04) `/settings` gains a notification-preferences tab with one toggle per silenciable category (8 categories, `PRAZO_VENCIDO` omitted), deriving from `NOTIFICACAO_CATEGORIA_OPTIONS` without duplicating the enumeration | ✓ VERIFIED | `settings/page.tsx` read directly: `TabId` extended with `"notificacoes"`, gated button + panel via `can.view("notificacoes")`, `NotificationPreferencesTab` (line 861) renders `NOTIFICACAO_CATEGORIA_OPTIONS.filter(o => o.value !== "PRAZO_VENCIDO")` (8 rows), each with `checked = !silenciadas.includes(o.value)`. Toggling off calls `silenciar.mutateAsync` (PUT), toggling on calls `reativar.mutateAsync` (DELETE). No categoria list is hardcoded in this file. |
| 10 | (93-04) Frontend data layer: `useNotificacaoPreferencias` (GET), `useSilenciarCategoria` (PUT), `useReativarCategoria` (DELETE) hooks wired to the backend endpoints | ✓ VERIFIED | `use-notificacao-preferencias.ts` read directly: distinct query key `["notificacao-preferencias"]`, `PUT`/`DELETE` to `/notificacoes/preferencias/{categoria}`, both mutations invalidate the preferences query key on success. `NotificacaoPreferenciasResponse` type present in `types/notificacoes.ts`. |

**Score:** 15/15 must-haves verified at the code level (unit tests, compilation, static analysis, independent grep re-verification). 0 failed. 0 uncertain at the code level. 5 items require live human/browser verification against a running backend (see Human Verification Required) — this is what drives the `human_needed` status, not a code gap.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java` | Join-table entity, tenant+user+categoria unique constraint | ✓ VERIFIED | Exists, substantive, correct annotations, compiles |
| `backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java` | 9-category enum, only PRAZO_VENCIDO non-silenciable | ✓ VERIFIED | Exists, exactly 9 constants, non-throwing helpers |
| `backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java` | 3 dual-scoped derived methods | ✓ VERIFIED | Exists, no `@Modifying`, all 3 methods present |
| `backend/migrations/93-create-notificacao-preferencia-table.sql` | Manual migration (table + unique index) | ✓ VERIFIED | Exists, correct DDL, manual-migration header present |
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | Mute guard inside `criar()` + 3 preference methods | ✓ VERIFIED | Guard at lines 71-77 (single location); `silenciarCategoria`/`reativarCategoria`/`listarCategoriasSilenciadas` present; wired to `NotificacaoPreferenciaRepository` |
| `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` | 20 constructors migrated + mute/preference tests | ✓ VERIFIED | 29 `@Test` methods, 29/29 `new NotificacaoService(...)` calls use the 3-arg constructor, 0 remaining 2-arg calls; `mvn test` → 29/29 green |
| `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` | 3 preference REST endpoints | ✓ VERIFIED | GET/PUT/DELETE `/preferencias(/{categoria})` present, delegate to service, dual-scoped, gated, 400 on rejection |
| `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` | **Must be completely unmodified by this phase** | ✓ VERIFIED | `git diff 513ca7e..HEAD` on this file: zero output. Confirmed calls `notificacaoService.criar(...)` directly at line 317 |
| `web/src/types/notificacoes.ts` | `NotificacaoPreferenciasResponse` type | ✓ VERIFIED | Present, correctly shaped `{ silenciadas: NotificacaoCategoria[] }` |
| `web/src/hooks/use-notificacao-preferencias.ts` | 3 TanStack Query hooks | ✓ VERIFIED | Exists, correct query key, PUT/DELETE endpoints, invalidation wired |
| `web/src/app/(dashboard)/settings/page.tsx` | Notification preferences tab | ✓ VERIFIED | `TabId` extended, gated tab + panel, `NotificationPreferencesTab` sub-component, 8 toggles, PRAZO_VENCIDO filtered |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `NotificacaoService.criar` | `NotificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria` | Mute guard, after validations, before builder | ✓ WIRED | Confirmed at source lines 71-73; short-circuits on `isSilenciavelCategoria` first |
| `NotificacaoService.criar` | `CategoriaNotificacao.isSilenciavelCategoria` | PRAZO_VENCIDO never silenced (short-circuit) | ✓ WIRED | Test proves `existsBy...` is never called for PRAZO_VENCIDO (true short-circuit, not merely stubbed false) |
| `AlertasDiariosJob.notificar` | `NotificacaoService.criar` | Direct call, no `notificar*` intermediary | ✓ WIRED | Confirmed at `AlertasDiariosJob.java:317`; file unmodified this phase |
| `NotificacaoController` (PUT/DELETE/GET `/preferencias`) | `NotificacaoService.silenciarCategoria/reativarCategoria/listarCategoriasSilenciadas` | Full delegation, no direct repository writes from controller | ✓ WIRED | Confirmed — controller never imports `NotificacaoPreferenciaRepository` |
| `settings/page.tsx` (tab "Notificações") | `use-notificacao-preferencias.ts` | State read + silenciar/reativar mutations | ✓ WIRED | `useNotificacaoPreferencias`/`useSilenciarCategoria`/`useReativarCategoria` all imported and invoked in `NotificationPreferencesTab` |
| tab de preferências | `NOTIFICACAO_CATEGORIA_OPTIONS` | `.filter(o => o.value !== "PRAZO_VENCIDO")` | ✓ WIRED | Confirmed at `settings/page.tsx:876` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `NotificationPreferencesTab` | `data?.silenciadas` | `useNotificacaoPreferencias()` → `GET /notificacoes/preferencias` → `NotificacaoService.listarCategoriasSilenciadas` → `NotificacaoPreferenciaRepository.findByTenantIdAndUserId` | Real DB query (no static/empty fallback) | ✓ FLOWING |
| `criar()` mute decision | `existsByTenantIdAndUserIdAndCategoria` result | Derived Spring Data query against `t_notificacao_preferencia` | Real DB query | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with all phase 93 artifacts | `cd backend && mvn -q -DskipTests compile` | Clean, no output/errors | ✓ PASS |
| `NotificacaoServiceTest` full suite (mute guard, PRAZO_VENCIDO, preference methods) | `cd backend && mvn -q test -Dtest=NotificacaoServiceTest` | `Tests run: 29, Failures: 0, Errors: 0` (surefire report) | ✓ PASS |
| Frontend typecheck — zero errors in phase-93 files | `cd web && pnpm exec tsc --noEmit` | 3 pre-existing `vitest`-module errors in unrelated test files (documented in `deferred-items.md`); zero errors in `use-notificacao-preferencias.ts`, `types/notificacoes.ts`, `settings/page.tsx` | ✓ PASS |
| Frontend lint — zero new issues in phase-93 files | `cd web && pnpm lint` | 6 errors/17 warnings, all pre-existing in unrelated files; `settings/page.tsx` has exactly 1 warning at line 403 (pre-existing `UserManagementTab` `<img>`, not the new code); `use-notificacao-preferencias.ts` and `types/notificacoes.ts` have zero issues | ✓ PASS |
| `AlertasDiariosJob.java` unmodified | `git diff 513ca7e..HEAD -- backend/.../AlertasDiariosJob.java` | Zero output | ✓ PASS |
| Mute guard exists only in `criar()`, not in trigger methods | Manual source read of all 5 `notificar*` methods | No guard-related code in any trigger method | ✓ PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` conventional probes exist for this phase, and neither PLAN nor SUMMARY files declare any probe. This is a standard backend+frontend feature phase, not a migration/tooling phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| NOTF-24 | 93-01, 93-02, 93-03, 93-04 | Utilizador pode silenciar categorias de notificação específicas para si próprio, exceto categorias críticas não-silenciáveis (mínimo: `PRAZO_VENCIDO`) | ✓ SATISFIED (code level) | Full stack implemented: data model (93-01), mute guard + service methods (93-02), REST API (93-03), UI (93-04). All code-level truths verified above. Live E2E persistence/isolation/400-rejection remain human_needed per the blocked MINIO_ENDPOINT gap. |

No orphaned requirements: REQUIREMENTS.md maps only `NOTF-24` to Phase 93, and the plan's `requirements: [NOTF-24]` frontmatter across all 4 plans matches exactly.

### Anti-Patterns Found

None. Scanned all phase-modified files (`NotificacaoPreferencia.java`, `CategoriaNotificacao.java`, `NotificacaoPreferenciaRepository.java`, `93-create-notificacao-preferencia-table.sql`, `NotificacaoService.java`, `NotificacaoServiceTest.java`, `NotificacaoController.java`, `use-notificacao-preferencias.ts`, `types/notificacoes.ts`, `settings/page.tsx`) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER|not yet implemented|coming soon` — zero matches. No empty-implementation patterns (`return null`, `=> {}`, hardcoded empty arrays feeding render) found in the new code paths.

### Human Verification Required

The following items are genuine runtime/live-browser behaviors that static analysis and unit tests cannot substitute for. They are blocked in this sandbox by the pre-existing, documented `MINIO_ENDPOINT` environment gap (confirmed: `backend/.env` exists but contains zero `MINIO_*` variables), which has also blocked live UAT for Phases 87/89/91/92 per `.planning/STATE.md`. This is a known environmental limitation, not a code defect discovered by this verification.

### 1. UI shows exactly 8 toggles, "Prazo vencido" absent

**Test:** Login (e.g. admin@lexcv.cv / Pa$$w0rd), navigate to `/settings` → "Notificações" tab.
**Expected:** 8 category toggles visible; "Prazo vencido" (PRAZO_VENCIDO) does not appear anywhere in the list.
**Why human:** Requires a running backend + reachable Postgres + real browser session — blocked by the MINIO_ENDPOINT gap.

### 2. Silencing persists across reload

**Test:** Turn off "Nova fase" (FASE_ENTRADA), confirm success toast, reload (F5), return to the tab.
**Expected:** Toggle remains off after reload.
**Why human:** Live persistence behavior requiring a running backend + DB.

### 3. Reactivating resumes delivery and persists

**Test:** Turn "Nova fase" back on, confirm toast, reload, confirm it stays on.
**Expected:** Toggle remains on; category resumes default-on delivery.
**Why human:** Live persistence behavior requiring a running backend + DB.

### 4. Cross-user isolation (optional, if 2nd user available)

**Test:** With a second user in the same tenant, confirm user A's mute does not affect user B's toggles.
**Expected:** Preferences are isolated per (tenant_id, user_id).
**Why human:** Requires two live authenticated sessions. Code-level evidence (dual-scoped repository methods keyed by `destinatarioId`) strongly supports this, but cannot be observed live in this sandbox.

### 5. Direct API rejection of PRAZO_VENCIDO

**Test:** `PUT /api/v1/notificacoes/preferencias/PRAZO_VENCIDO` against a running backend.
**Expected:** HTTP 400 with a `message` body.
**Why human:** Requires a running backend to observe the actual HTTP response. Code-level evidence (controller catch block + service exception + unit test) strongly supports this.

### Gaps Summary

No code-level gaps found. All 4 plans' artifacts exist, are substantive (not stubs), are correctly wired, compile cleanly, and pass their respective test/lint/typecheck suites — independently re-verified rather than trusted from SUMMARY.md. The critical architectural property this phase depends on (single choke-point mute guard inside `NotificacaoService.criar()`, with `AlertasDiariosJob.java` completely untouched) was independently confirmed via `git diff` showing zero changes to that file, plus a manual read of all 5 `notificar*` trigger methods confirming no guard duplication. `PRAZO_VENCIDO` non-mutability was confirmed end-to-end at the code level: enum flag (`false`) → service validation (`IllegalArgumentException`) → controller translation (400) → UI filter (omitted from render).

The only open item is genuine runtime/live-browser verification (persistence across reload, cross-user isolation, live HTTP 400), which this sandbox cannot perform due to the pre-existing, already-documented `MINIO_ENDPOINT` blocker (STATE.md tracks this as owned by v2.11 Phase 97/AUD-04). This routes the phase to `human_needed` rather than `passed`, per the verification process's decision tree (Step 9, rule 2) — it does not indicate a code defect.

---

*Verified: 2026-07-14*
*Verifier: Claude (gsd-verifier)*
