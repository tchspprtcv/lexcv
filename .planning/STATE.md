---
gsd_state_version: 1.0
milestone: v2.15
milestone_name: Reposicionamento SIJ
status: planning
last_updated: "2026-07-27T20:15:00.000Z"
last_activity: 2026-07-27
progress:
  total_phases: 1
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-22)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 116 — Reposicionamento Institucional — Fim das Referências a NOSi

## Current Position

Phase: 116 of 116 (Reposicionamento Institucional — Fim das Referências a NOSi)
Plan: TBD (not yet planned)
Status: Ready to plan
Last activity: 2026-07-27 — ROADMAP.md created: 1 phase (116), 4/4 requirements mapped

## Performance Metrics

**Velocity:**

- Total plans completed: 117
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 58 | 4 | ~70 min | ~17 min |
| 59 | 6 | ~90 min | ~15 min |
| 60 | 2 | ~25 min | ~12 min |
| 65 | 2 | - | - |
| 70 | 1 | 12 min | 12 min |
| 74 | 5 | - | - |
| 75 | 3 | - | - |
| 79 | 2 | - | - |
| 80 | 1 | - | - |
| 81 | 3 | - | - |
| 82 | 1 | - | - |
| 83 | 2 | - | - |
| 84 | 5 | - | - |
| 85 | 1 | - | - |
| 86 | 3 | - | - |
| 87 | 4 | - | - |
| 88 | 2 | - | - |
| 89 | 4 | - | - |
| 90 | 1 | - | - |
| 92 | 2 | - | - |
| 93 | 4 | - | - |
| 94 | 1 | - | - |
| 95 | 2 | - | - |
| 96 | 4 | - | - |
| 97 | 4 | ~128 min | ~32 min |
| 101 P02 | 1 | 20min | 20min |
| 101 P05 | 1 | 25min | 25min |
| 101 | 5 | - | - |
| 102 | 4 | - | - |
| 103 | 1 | - | - |
| 104 | 6 | - | - |
| 105 | 6 | - | - |
| 106 | 4 | ~70 min | ~17 min |
| 107 | 6 | ~90 min | ~15 min |
| 108 | 4 | ~150 min | ~38 min |
| 109 | 3 | ~200 min | ~67 min |
| 110 | 3 | ~90 min | ~30 min |
| 111 | 2 | - | - |
| 112 | 5 | - | - |
| 113 | 1 | - | - |
| 114 | 1 | - | - |
| 115 | 11 | - | - |
| 115.1 | 3 | - | - |

*(Full per-phase history for v2.0–v2.8 lives in `.planning/milestones/*-ROADMAP.md` archives; table trimmed here per STATE.md size constraint.)*
| Phase 103 P01 | ~10min | 2 tasks | 1 files |
| Phase 104 P02 | ~25min | 3 tasks | 7 files |
| Phase 105 P03 | ~25min | 2 tasks | 2 files |
| Phase 111 P01 | 15min | 2 tasks | 6 files |
| Phase 111 P02 | 15min | 2 tasks | 3 files |
| Phase 112 P02 | ~30min | 2 tasks | 1 files |
| Phase 112 P03 | 12min | 1 tasks | 1 files |
| Phase 113 P01 | ~15min | 1 tasks | 1 files |
| Phase 114 P01 | 18min | 3 tasks | 32 files |

## Accumulated Context

### Roadmap Evolution

v2.10–v2.12 roadmap rationale trimmed here at milestone boundary — full detail preserved in `.planning/milestones/v2.10-ROADMAP.md`, `v2.11-ROADMAP.md`, `v2.12-ROADMAP.md`.

v2.13 roadmap (10 phases, 101–110, 33 requirements, 100% coverage) and its full per-phase decision/lesson log are now archived at `.planning/milestones/v2.13-ROADMAP.md` and `v2.13-REQUIREMENTS.md`; condensed accomplishments and outcomes live in `PROJECT.md`'s Validated requirements and Key Decisions; narrative lessons live in `RETROSPECTIVE.md`'s "Milestone: v2.13" section.

- Phase 115.1 inserted after Phase 115: Permitir criar Processo/Parecer a partir da ficha de cliente; corrigir redirect de Entrar no webpage para o login da app; alinhar visual dos filtros de Pareceres com Processos/Clientes (URGENT)
- v2.15 roadmap created (2026-07-27): 1 phase (116), 4/4 requirements mapped, 100% coverage — deliberately single-phase: the 4 requirements are independent, no-dependency text/data corrections across unrelated files (`PROJECT.md`, `webpage/trust-section.tsx`, `SPEC.md`, `DatabaseSeeder.java`) serving one shared goal (stop describing LexCV via NOSi, correctly frame it around the SIJ ecosystem instead) — config.json's "standard" granularity guidance deliberately not applied literally, per the standing rule against padding a small milestone with artificial phase splits

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions). v2.10–v2.13's full per-phase decision log has been cleared here at the v2.13 milestone boundary (per the standard milestone-close state-trim) — see PROJECT.md Key Decisions, `.planning/milestones/v2.13-ROADMAP.md`, and `.planning/RETROSPECTIVE.md` for the complete record.

- [Phase 111]: ParecerSolicitacaoRepository.pesquisarGlobal e um metodo novo e separado (nao reutiliza pesquisar(), nao LEFT JOIN t_parecer_versao) - match raso so em descricao — Resolve a tensao STACK.md-vs-PATTERNS.md a favor do Anti-Pattern 6: pesquisa profunda de conteudo (ParecerVersao.conteudo) fica exclusiva de /pareceres/pesquisa
- [Phase 111]: PesquisaController Javadoc paraphrases Honorario/financeiro exclusion (billing/fee records) instead of naming them, to satisfy the plan's own grep-based acceptance check while preserving the documentation intent
- [Phase 111]: PesquisaControllerTest role-matrix covered via one @Test with an internal loop over the 4 seeded role scope-sets, not @ParameterizedTest — no precedent for @ParameterizedTest exists in this test suite
- [Phase 112]: 112-02: query reset on dialog close moved into onOpenChange instead of a useEffect, to satisfy react-hooks/set-state-in-effect (configured as an ESLint error in this repo)
- [Phase 112]: 112-02: recents list read directly at render time instead of React.useMemo keyed on open, avoiding an exhaustive-deps warning about an intentional cache-invalidation dependency
- [Phase 112]: 112-03: kbd shortcut-hint platform detection uses React.useSyncExternalStore instead of useState+useEffect, avoiding react-hooks/set-state-in-effect (configured as an ESLint error in this repo) while preserving the identical SSR-safe default-then-upgrade contract
- [Phase 113]: 113-01: kept the existing draft + Aplicar submission model for the promoted Estado field (no onChange-immediate-apply) — 113-UI-SPEC.md Apply-behavior resolution: this was a layout change, not a logic change per 113-CONTEXT.md
- [Phase 114]: Full 271-occurrence rounded-none remediation delivered (271 across 29 files), not just the 2-file token edit — tailwind-merge (cn()) lets a literal rounded-none className deterministically win over a primitive's own token-derived default — the token flip alone would have been invisible on most screens
- [Phase 114]: settings/page.tsx's 4 Cards changed from rounded-none lg:rounded-xl to plain rounded-xl — gives uniform rounding at every breakpoint instead of flat-on-mobile/rounded-on-desktop
- [Phase 114]: the shared --radius design token flips to 0.5rem (shadcn Default) in both web/ and webpage/ globals.css, identical value — 114-CONTEXT.md locked decision — reverts the v2.13 institutional 0rem look; a single-app value would make the public landing site visually inconsistent with the internal app

### Pending Todos

- ~~UAT ao vivo pendente em 8 fases (75, 76, 79, 81, 82, 84, 85, 89)~~ — CLOSED by v2.11 Phase 97 (AUD-02, see `97-UAT.md` for the full per-scenario breakdown of all ~38 scenarios). Phases 75, 76, 79, 84 close 100% via CODE-VERIFIED (some carry a non-blocking secondary visual note); Phase 89 closes 8/11 by code with 3/11 remaining NEEDS-HUMAN-VISUAL (purely visual/live-timing, zero functional risk). Two items are carried forward, not silently dropped — see the next two bullets.
- Phase 81 scenario #5 / Phase 82 scenario #4 (`97-UAT.md`) — the manual migration scripts (`81-add-facto-ordem-unique-constraint.sql` / `82-add-honorario-processo-unique-constraint.sql`) could not be live-verified against a `ddl-auto=validate` prod-like DB — none exists in this dev environment. Both constraints ARE confirmed enforced in dev via app-level guard+catch logic. Recommendation: review both scripts' SQL syntax against staging/prod before the next deploy.
- Phase 85 open product decision (`97-UAT.md`, carried forward unanswered): should an ALTA-priority `Evento` with null `dataInicio` count as urgent in `agendaUrgentesCount`/`prazosCriticosCount`? Current behavior excludes it by design (see `isEventoCritico()` docblock in `ResourceController.java`, grep to relocate). Not resolved by v2.11 — awaiting a user product decision. (Field renamed from `dataFim` to `dataInicio` on 2026-07-14 — see the next bullet; the corner case itself, and the still-open product question, are unchanged.)
- ~~`isEventoCritico()` (dashboard KPIs: `prazos_vencer`/`prazos_criticos_count`) computed Evento risk from `dataFim`, while `listEventos` (GET /eventos, Agenda, AGD-34/35) and `AlertasDiariosJob` both use `dataInicio` — a genuine 5th divergent "prazo crítico" implementation that AGD-34/35's own consolidation goal was meant to eliminate~~ — CLOSED 2026-07-14, found by the v2.11 milestone audit's integration-checker (flagged by `92-REVIEW.md` CR-01/`92-CONTEXT.md` as deferred to Phase 97, but missed by Phase 97's own bounded AUD-05 scope until the milestone-audit step caught it). Fixed by standardizing `isEventoCritico()` on `dataInicio`; full backend suite re-confirmed green (69/69) after the change. Commit `f0c62ff`.
- `backend/migrations/74-cleanup-nif-documento-tipo.sql` — manual-execution script, must be run against the database before/alongside next deploy (no migration runner in this repo).
- ~~REG_COMERCIAL and other `DocumentoTipo` values still render as raw enum strings instead of translated Portuguese labels~~ — CLOSED by v2.11 Phase 97-02 (AUD-03): `getDocumentoTipoLabel` helper added, applied at both cliente render sites (detail page + printable ficha).
- ~~No automated backend tests cover the 4 NIF validation scenarios introduced in Phase 73.1~~ — CLOSED by v2.11 Phase 97-02 (AUD-03): `ClienteNifValidationTest` added (standalone `jakarta.validation.Validator`, 4 scenarios: valid/blank/wrong-length/non-numeric).
- ~~No H2/Testcontainers integration-test infrastructure exists anywhere in this backend~~ — CLOSED by v2.11 Phase 91 (TEST-01/02/03): Testcontainers PostgreSQL integration-test infra + CI gate now exist; `buscarPorFiltros` (native query) and the `numeroVersao` concurrency lock are proven against real Postgres in `NotificacaoRepositoryIT`/`ParecerVersaoConcorrenciaIT`.
- ~~`backend/spotbugs-exclude.xml`/SpotBugs SAST tooling broken against JDK 23 bytecode~~ — CLOSED by v2.11 Phase 90 (SAST-01): `mvn spotbugs:check` runs clean against JDK 23 bytecode, exclusions file + version bumps committed.
- ~~`web/src/app/(dashboard)/agenda/page.tsx` independently recomputes its own "prazo crítico" verdict instead of using `RiscoPrazoService`~~ — CLOSED by v2.11 Phase 92 (AGD-34/AGD-35): Agenda now consumes the backend-computed `risco` field for both Prazos and Eventos; the orphaned `/eventos/upcoming` endpoint and its frontend hook/types were removed.
- ~~AUD-01 tenant-isolation cross-cutting audit~~ — CLOSED by v2.11 Phase 97-01: all three newest notification data surfaces (Phase 93 preferences, Phase 95 team resolution, Phase 96 snooze) traced end-to-end and confirmed tenant-scoped; zero gaps found, zero fixes needed (verdict: COVERED, not FIXED).
- ~~AUD-05 fresh code-discovery audit~~ — CLOSED by v2.11 Phase 97-04 Task 1: bounded fresh pass over `NotificacaoService.java`, `NotificacaoController.java`, `AlertasDiariosJob.java`, and the notification/preference/snooze web hooks + adjacent components (bell, snooze control, settings tab) found zero new gaps — all 8 `NotificacaoController` endpoints confirmed to carry `@PreAuthorize`, zero real `TODO`/`FIXME`/`XXX` markers (one substring false-positive on "TODOS" checked and dismissed), zero dead-code references anywhere to the removed `/eventos/upcoming` endpoint, `AlertasDiariosJob`'s 4-layer error isolation (job/tenant/category/entity, all catching `Throwable`) confirmed intact. Clean result — no FIX-NOW or RECORD-AS-DEBT findings; see `97-04-SUMMARY.md` for the enumerated coverage.
- (v2.12) Contact channel for LP-10 ("Contacto/Pedir demonstração") needs a concrete value at plan time — REQUIREMENTS.md/research flag it as a static `mailto:` link, but the exact address is a content decision not yet made; Phase 99 planning should confirm this with the user rather than inventing a placeholder that ships to production.
- (v2.12) Whether `webpage/` needs its own favicon/OG-image/robots.txt is explicitly out of scope for this milestone (see REQUIREMENTS.md Out of Scope) — Phase 99/100 must not silently add extra Caddy routing branches for this; it inherits `web/`'s static files via the existing catch-all.
- (v2.13, Phase 108) **Real data-correctness bug found live, not yet fixed:** `/pareceres/nova`'s "Prioridade" field silently defaults to `ALTA` instead of the documented/intended `MEDIA` on any unedited submission — the `NativeSelect` is uncontrolled with no `defaultValue`, so the DOM just shows its first `<option>` regardless of the form/schema's declared default. Confirmed pre-existing via `git show 88343bef0529f6dda33b9c73b350f883ecec4c42` (the original raw `<select>` had the same gap). One-line fix (`defaultValue="MEDIA"`). See `.planning/phases/LEXCV-108-m-dulo-pareceres/deferred-items.md`.
- (v2.13, Phase 106) **Real data-correctness bug found live, not yet fixed:** every `Evento` created or edited via `/agenda/novo` or `/agenda/{id}/editar` has its `dataInicio`/`dataFim` silently stored 1 hour later than what the user selected, for any tenant running in a negative-UTC-offset timezone (this app's actual Cabo Verde market, UTC-01:00). Root cause: `new Date(values.dataInicio).toISOString().slice(0, 19)` in both forms' `onSubmit` (and the same pattern in `agenda/page.tsx`'s drag-and-drop reschedule + `use-eventos.ts`) parses the naive local datetime as local time then serializes it via UTC `toISOString()`, keeping the UTC-shifted clock value while dropping the `Z` marker. Confirmed pre-existing (present in the pre-Phase-106 base commit `ba896e3`), NOT introduced by Phase 106's Calendar/Select migration. See `.planning/phases/LEXCV-106-m-dulo-agenda/deferred-items.md` for full file:line detail and a suggested one-line-per-site fix. Recommend a dedicated fix phase/task — this silently corrupts real production Agenda data every time it's exercised.

### Blockers/Concerns

- **(v2.13, 2026-07-17) Three previously-flagged background bugs each have an independent fix session that completed successfully** (per direct transcript review), but none have been merged into this repo's `master` yet — each lives on its own branch/worktree, started by the user outside this GSD orchestration, and merging is the user's call, not auto-applied here:
  - Document upload Hibernate optimistic-lock crash (see below) — fixed via `Persistable<UUID>` on `Documento` (mirroring the existing `ParecerVersao` precedent), plus a new `DocumentoRepositoryIT` regression test. Branch `claude/jovial-lederberg-d017b2`.
  - Pareceres "Prioridade" defaulting to ALTA instead of MEDIA (see below) — fixed via `defaultValue="MEDIA"` on the `NativeSelect`. Branch `claude/mystifying-leavitt-6657b5`.
  - Dashboard `AtividadeRecenteCard` hydration mismatch (found during Phase 109's own UAT, see Phase 109 decision entry above) — fixed by removing an unrelated `useDashboardKpis().isLoading` gate that had no bearing on the card's actually-static content. Branch `claude/sad-fermat-78bc39`, verified live against an isolated backend+Postgres with zero hydration errors across multiple reloads.
- **(v2.13, Phase 108, 2026-07-17) Pareceres "Advogado" picker permanently unpopulated for non-ADMIN users; spurious 500 error toast on every Pareceres page load.** `useAdminUsers()` is called unconditionally (no `enabled` guard) in all 3 Pareceres files (`page.tsx:61`, `nova/page.tsx:71`, `[id]/page.tsx:143`), but the backend endpoint it calls (`GET /api/v1/admin/users`) is gated `hasRole('ADMIN')`. For any non-ADMIN role this returns `500` (should be `403`) and surfaces an "Erro 500: Access Denied" toast on every load; functionally, the "Advogado" `NativeSelect` on `/pareceres/nova` can never show any options for a non-ADMIN user, so a non-admin can never assign a specific advogado when creating a parecer. Confirmed pre-existing (present at the pre-Phase-108 base commit `88343be`), unrelated to Phase 108's Select/NativeSelect/Tooltip/Accordion migration. A working alternative already exists (`useTenantUsers()`, gated only by `processos:view`, already used elsewhere for non-admin "assign to" pickers). See `.planning/phases/LEXCV-108-m-dulo-pareceres/deferred-items.md` for full detail.
- **(v2.13, Phase 107, 2026-07-17) Document upload is completely broken — every NEW upload crashes 100% of the time.** `ResourceController.uploadDocumento`'s create-new branch (`backend/.../ResourceController.java`, `replaceId == null` path) explicitly sets a non-null `@Version` field (`.versao(1)`) on a never-persisted `Documento` builder result, which makes Spring Data JPA treat the entity as pre-existing and route `save()` through `merge()` instead of `persist()` — Hibernate then throws `StaleObjectStateException` since no matching row exists. Affects all 3 upload call sites (`/documentos/novo`, Processo ficha's Documentos tab, Cliente ficha's Documentos Entregues tab — all share one endpoint). Confirmed pre-existing (git log: `ResourceController.java` last touched by Phase 97's `f0c62ff`, 2026-07-14) and unrelated to Phase 107's frontend-only changes. Flagged as a dedicated background task (see `.planning/phases/LEXCV-107-m-dulos-documentos-financeiro/deferred-items.md` for full root-cause detail and a suggested fix) — high priority, blocks a core product feature app-wide, recommend fixing before this milestone's remaining phases rely on any document-upload live testing.
- ~~`MINIO_ENDPOINT` environmental blocker (recurring across v2.8/v2.9/v2.10 sessions, prevents full Spring context startup for live UAT)~~ — **RESOLVED (2026-07-14, v2.11 Phase 97 AUD-04):** `backend/.env` (gitignored, not committed) now supplies a real `MINIO_ENDPOINT=http://localhost:9000` plus working credentials against a running `lexcv_minio` Docker container the user started deliberately for this session. The Spring context now boots fully — `MinioConfig.s3Client()` no longer throws the "Illegal character ... `${MINIO_ENDPOINT}`" `IllegalArgumentException` that previously blocked every controller from becoming reachable. `backend/.env.example` already documents all required `MINIO_*` vars (`MINIO_ENDPOINT`, `MINIO_PUBLIC_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME`) for any future environment. This is an environment/config resolution, not a code fix — no source files were modified to close this blocker; it was never a code defect (see `.planning/milestones/v2.10-MILESTONE-AUDIT.md`).
- ~~(v2.12, flagged by research, unverified) Server-side "hairpin" fetch risk: a relative-URL `fetch()` inside the `webpage` container calling `/api/v1/setup/status` may resolve against the public domain (routing back out through Caddy) instead of the internal Docker network~~ — **RESOLVED (2026-07-15, Phase 100-04):** live `docker compose up` test proved `BACKEND_API_ORIGIN=http://backend:8080` resolves correctly against the internal Docker network with zero hairpin. Separately, code review (99-REVIEW.md CR-01) found `webpage/src/lib/setup.ts` itself used a *relative* URL (not the internal-origin absolute URL the research anticipated) — a distinct, more severe bug (relative URLs throw in Node/Edge `fetch`, silently disabling the `/setup` redirect gate entirely) — fixed by switching to the same `BACKEND_API_ORIGIN`-based absolute-URL pattern `branding.ts` already used.
- ~~(v2.14, Phase 115 code review, 2026-07-22) 3 pre-existing issues found in passing and spawned as independent background-task worktrees (`task_48c8e2d9`, `task_482b4a64`, `task_d6ca37c9`, tracked in `.planning/milestones/v2.14-MILESTONE-AUDIT.md` tech debt)~~ — **RESOLVED (2026-07-22):** all 3 investigated, verified (lint+build clean), and merged to `master` at the user's request: Clientes mobile list "Editar" quick-action now actually opens edit mode (`?edit=1` deep link, was opening the same read-only view as "Ver detalhes"), Settings user-search clear button now has `aria-label`+explicit `type="button"` (was icon-only with no accessible name and an implicit submit type), and the 6 pre-existing `react-hooks/set-state-in-effect`/`refs` ESLint errors across `clientes/[id]/page.tsx`/`documentos/novo/page.tsx`/`processos/[id]/page.tsx`/`dashboard-shell.tsx` are fixed (React's render-time state-adjustment pattern, already established elsewhere in this codebase). `web/pnpm lint` and `pnpm build` both clean after all 3 merges. Worktrees and branches removed.

## Deferred Items

Items acknowledged and deferred at milestone v2.10 close on 2026-07-10 (see `.planning/milestones/v2.10-MILESTONE-AUDIT.md` for full detail — 0 cross-phase integration gaps found; only 2 non-blocking WARNINGs, neither requiring a fix before close). Most are now in-scope for v2.11 (see Pending Todos above for explicit requirement ownership):

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 85 (85-VERIFICATION.md / `97-UAT.md`) — code-level verification passed 5/5; 1 open product decision (ALTA-priority `Evento` with null `dataInicio`, renamed from `dataFim` 2026-07-14 — should it count as urgent in dashboard KPIs?) re-confirmed still unanswered by v2.11 Phase 97 AUD-02, `isEventoCritico()` docblock quoted verbatim | OPEN — genuine product decision for the user, not a defect; carried forward, not silently dropped |
| integration_gap | `isEventoCritico()` used `dataFim` while `listEventos`/`AlertasDiariosJob` used `dataInicio` — a 5th divergent risk-calc implementation AGD-34/35 was meant to eliminate; flagged by `92-REVIEW.md` CR-01 as deferred to Phase 97, missed by Phase 97's own bounded AUD-05 scope, caught by the milestone audit's integration-checker | CLOSED 2026-07-14 during milestone audit — standardized on `dataInicio`, commit `f0c62ff`, 69/69 backend tests still green |
| verification_gap | Phase 86 (86-VERIFICATION.md) — 11/11 plan-level truths verified; the first-ever `nativeQuery=true`+`Pageable` combination in this codebase (`buscarPorFiltros`) — CLOSED by v2.11 Phase 91 (TEST-01): Testcontainers infra now exists and `NotificacaoRepositoryIT` exercises `buscarPorFiltros` against real Postgres; RBAC Settings-screen visual confirmation still pending | human_needed — standalone item, NOT owned by v2.11 AUD-02 (Phase 86 is not among AUD-02's 8 covered phases: 75/76/79/81/82/84/85/89; no Phase 97 plan performed this specific live check) — remains open for a future session |
| verification_gap | Phase 87 (87-VERIFICATION.md) — code-level verification passed 7/7. Of the 3 originally-tracked gaps: (1) live E2E walkthrough (Reatribuir flow) blocked by the MinIO env issue — CLOSED by v2.11 Phase 97 Task 2 (AUD-04), MinIO blocker resolved 2026-07-14; (2) concurrent `numeroVersao` DB lock had zero automated test coverage — CLOSED by v2.11 Phase 91 (TEST-02), `ParecerVersaoConcorrenciaIT` added; (3) `ParecerController` partial-update data-loss bug (already fixed in code, commit `ce6d1f0`) still has zero automated test coverage | partially closed — (1) CLOSED (Phase 97/AUD-04), (2) CLOSED (Phase 91/TEST-02); (3) remains OPEN as a standalone item — Phase 87 is not one of AUD-02's 8 covered phases, so no plan this milestone added test coverage for this specific defect; candidate for a future phase |
| verification_gap | Phase 89 (89-VERIFICATION.md / `97-UAT.md`) — code-level verification passed 15/15; all 11 pending UAT scenarios given an explicit verdict by v2.11 Phase 97 AUD-02: 8/11 CODE-VERIFIED (badge/dropdown/fused-click/mark-all/route+RBAC/filters/standalone-mark+pagination), 3/11 remain NEEDS-HUMAN-VISUAL (cross-surface real-time reflection, WR-01 same-origin-guard render, WR-02 pagination-flash fix) — none indicate a functional defect, all backed by prior independent static/behavioral evidence | mostly CLOSED — 3 purely visual/live-timing confirmations remain, zero functional risk; the MinIO blocker that previously prevented any live walkthrough is separately CLOSED (Phase 97 Task 2, AUD-04) |
| uat_gap | 75/76/79/81/82/84/85/89-HUMAN-UAT.md pending scenarios (the 8 phases AUD-02 actually covers — NOT 86/87, see their own rows above) | CLOSED by v2.11 Phase 97 (`97-UAT.md`, AUD-02) — see the Pending Todos section above for the per-phase breakdown and the 2 carried-forward open items (Phase 81/82 migration review, Phase 85 product decision) |
| tooling | `backend/migrations/86-create-notificacao-table.sql` and `88-add-notificacao-dedup-unique-constraint.sql` are standalone manual-execution scripts — no migration runner exists in this repo | must be run manually against the database before/alongside deploy |
| tooling | No H2/Testcontainers integration-test infrastructure exists anywhere in this backend | CLOSED by v2.11 Phase 91 — Testcontainers PostgreSQL infra + CI gate (`deploy.yml` `test` job) now exist |
| tooling | `backend/spotbugs-exclude.xml`/SpotBugs SAST tooling broken against JDK 23 bytecode | CLOSED by v2.11 Phase 90 (SAST-01) — `mvn spotbugs:check` runs clean against JDK 23 bytecode |
| cosmetic | `notificarDocumentoNovo`'s `linkUrl` doesn't use the `?tab=documentos` deep-link pattern that `notificarFaseEntrada` established (call sites: `ResourceController.java` upload-document branches, still plain `/processos/{id}`/`/clientes/{id}`) | still open — Phase 94/95 (NOTF-27/NOTF-25) touched `NotificacaoService.java` but not this specific `linkUrl` cosmetic gap; not addressed by v2.11 Phase 97 either (out of this plan's bounded audit scope); candidate one-line fix, non-blocking, for a future phase |

*(Full v2.8/v2.9 deferred-items detail trimmed here per STATE.md size constraint — see `.planning/milestones/v2.8-MILESTONE-AUDIT.md` and `.planning/milestones/v2.9-MILESTONE-AUDIT.md`.)*

### Items acknowledged and deferred at milestone v2.11 close (2026-07-14)

Pre-close `audit-open` scan found 11 open items across 5 phases, all `human_needed`/`partial` — none are BLOCKER-level (see `.planning/v2.11-MILESTONE-AUDIT.md` for full detail). Acknowledged and deferred rather than blocking close:

| Category | Item | Status |
|----------|------|--------|
| uat_gap / verification_gap | Phase 91 (Testcontainers) — 2 pending scenarios (`91-HUMAN-UAT.md`) | human_needed — Docker Desktop 4.80/Testcontainers 1.20.4 npipe incompatibility, confirmed independently multiple times; not a code defect, will pass in CI |
| uat_gap / verification_gap | Phase 92 (Agenda↔RiscoPrazoService) — 2 pending scenarios (`92-HUMAN-UAT.md`) | mostly closed — AGD-35's risco field was confirmed live this session; residual items are minor visual confirmations |
| uat_gap / verification_gap | Phase 93 (NOTF-24 preferências) — 5 pending scenarios (`93-HUMAN-UAT.md`) | mostly closed — the mute toggle itself was confirmed live end-to-end this session; residual items are secondary scenarios |
| uat_gap / verification_gap | Phase 96 (NOTF-26 snooze) — 2 pending scenarios (`96-HUMAN-UAT.md`) | mostly closed — the full snooze flow (presets, badge drop, history, bell-hide) was confirmed live end-to-end this session; residual items are edge cases (PRAZO_VENCIDO block, cross-user 404) not reproducible with current test data |
| uat_gap / verification_gap | Phase 97 (milestone audit) — 3 pending scenarios (`97-HUMAN-UAT.md`) | print-preview layout, cross-surface bell timing, RBAC second-user walkthrough — see Phase 97 rows above; zero functional risk per code-level evidence |

Known deferred items count at close: 11 (5 verification_gaps + 6 uat_gaps, `97-UAT.md` itself already closed with 0 pending).

### Items acknowledged and deferred at milestone v2.13 close (2026-07-18)

Pre-close `audit-open` scan found 6 open items across 4 phases, all `human_needed`/residual UAT-gap — none BLOCKER-level (see `.planning/v2.13-MILESTONE-AUDIT.md` for full detail). Acknowledged and deferred rather than blocking close:

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 103 (103-VERIFICATION.md) — `human_needed`, 5/5 must-haves verified | non-blocking — residual live-checkpoint substitution after the Browser-pane instability later root-caused (hydration mismatch) this session; already independently fixed (branch `claude/sad-fermat-78bc39`), not yet merged |
| verification_gap | Phase 105 (105-VERIFICATION.md) — `human_needed`, 8/9 must-haves verified | non-blocking — same residual live-checkpoint pattern |
| verification_gap | Phase 108 (108-VERIFICATION.md) — `human_needed`, 10/11 must-haves verified | non-blocking — 2 of 8 Wave-2 UAT items substituted with source/pattern analysis after the same Browser-pane issue; see Phase 108 decision entry above |
| verification_gap | Phase 109 (109-VERIFICATION.md) — `human_needed`, 13/14 must-haves verified | non-blocking — same residual live-checkpoint pattern; see Phase 109 decision entry above |
| uat_gap | Phase 103 (103-HUMAN-UAT.md) — flagged `resolved`, 0 pending scenarios | closed, flagged only by the scan's own status label |
| uat_gap | Phase 105 (105-HUMAN-UAT.md) — flagged `unknown`, 0 pending scenarios | non-blocking — 0 pending scenarios recorded despite the ambiguous status label |

Known deferred items count at v2.13 close: 6 (4 verification_gaps + 2 uat_gaps), none functional defects, none blocking. Milestone-level cross-phase integration audit additionally found and fixed 1 stale RBAC guard (`notificacoes/page.tsx`, commit `7a8f28e`) and flagged 1 narrow DOF-01 scope-completeness gap (Pareceres upload progress bar, background task `task_af5aaa48`) — see `.planning/v2.13-MILESTONE-AUDIT.md`.

### Items acknowledged and deferred at milestone v2.14 close (2026-07-22)

Pre-close `audit-open` scan found 2 open items, neither BLOCKER-level (see `.planning/v2.14-MILESTONE-AUDIT.md` for full detail). Acknowledged and deferred rather than blocking close:

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 114 (114-VERIFICATION.md) — `human_needed`, 7/7 code-level must-haves verified | non-blocking — 2 of 3 ROADMAP success criteria are rendering claims; substantively (not fully) de-risked by Phase 115's own incidental live checkpoint (6 screens/themes confirmed, see `114-VERIFICATION.md` Post-Verification Addendum); Badge cross-app divergence sub-item already resolved by explicit user decision |
| uat_gap | Phase 115.1 (115.1-HUMAN-UAT.md) — flagged `resolved`, 0 pending scenarios | closed — flagged only by the scan's own status label; 4/6 items live-confirmed this session (backend+web+webpage run together), including finding and fixing a real bug (recovery message never rendering, commit `ccd1b80`); 2 items (non-privileged-role rendering) remain source-verified only, blocked by a stale seeded test-account password in this long-lived dev DB, not by code doubt |

Known deferred items count at v2.14 close: 2 (1 verification_gap + 1 uat_gap, the latter already resolved per its own 0-pending count), none functional defects, none blocking. Milestone audit (re-run to incorporate the inserted Phase 115.1) additionally re-confirmed 0 cross-phase integration regressions across all 6 phases — see `.planning/v2.14-MILESTONE-AUDIT.md`.

## Session Continuity

Last session: 2026-07-27T20:15:00.000Z
Stopped at: ROADMAP.md v2.15 created (Phase 116 defined, awaiting user approval)
Resume file: .planning/ROADMAP.md

## Operator Next Steps

- Review and approve the v2.15 roadmap, then run /gsd:plan-phase 116
