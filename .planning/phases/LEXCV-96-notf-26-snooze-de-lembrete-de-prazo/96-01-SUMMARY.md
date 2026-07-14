---
phase: 96-notf-26-snooze-de-lembrete-de-prazo
plan: 01
subsystem: api
tags: [spring-boot, jpa, jpql, notifications, snooze, mockito, tdd]

# Dependency graph
requires:
  - phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
    provides: CategoriaNotificacao.isSilenciavelCategoria (reused verbatim as the snooze-block flag)
  - phase: 94-notf-27-corrigir-colis-o-de-dedup-admin
    provides: stable NotificacaoRepository.inserirSeNaoDuplicado upsert path (unaffected by this plan)
  - phase: 95-notf-25-notificar-toda-a-equipa-do-processo
    provides: current shape of NotificacaoService/NotificacaoServiceTest this plan builds on top of
provides:
  - Notificacao.snoozedUntil nullable column + manual production migration
  - Snooze-aware visibility predicate on the two "unread" repository queries (badge count, mark-all-read)
  - NotificacaoService.snooze(tenantId, destinatarioId, id, dias) write method
  - PATCH /api/v1/notificacoes/{id}/snooze endpoint
affects: [97-cross-cutting-milestone-audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Orthogonal visibility toggle on an existing row (snoozedUntil) instead of soft-delete/recreate"
    - "Derived query -> explicit @Query JPQL conversion to add an additive time-based predicate without touching tenant/destinatario scoping"

key-files:
  created:
    - backend/migrations/96-add-notificacao-snoozed-until.sql
  modified:
    - backend/src/main/java/com/lexcv/models/Notificacao.java
    - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
    - backend/src/main/java/com/lexcv/services/NotificacaoService.java
    - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
    - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java

key-decisions:
  - "Reused CategoriaNotificacao.isSilenciavelCategoria as the snooze-block flag for PRAZO_VENCIDO (96-CONTEXT.md locked decision) rather than a second exception mechanism; documented both the justification (silenciavel==adiavel coincide today only because PRAZO_VENCIDO is the sole non-silenciable category) and the forward risk (a future non-silenciable category for an unrelated reason would silently also become non-adiavel) directly at the block, per the plan's revised requirement."
  - "Converted countByTenantIdAndDestinatarioIdAndLidaFalse and findByTenantIdAndDestinatarioIdAndLidaFalse from derived queries to explicit JPQL @Query methods (additive snoozedUntil predicate) while leaving buscarPorFiltros (native, history endpoint) byte-for-byte untouched."
  - "snooze() mirrors marcarLida's exact find-then-mutate-then-save + 404-via-empty-Optional shape; snoozedUntil is set only after both the preset and PRAZO_VENCIDO checks pass, keeping it orthogonal to lida and to uk_notificacao_dedup."

patterns-established:
  - "Snooze/visibility toggles on notification-like rows should add an additive time predicate to the read-side query rather than mutate or duplicate the row."

requirements-completed: [NOTF-26]

# Metrics
duration: ~20min
completed: 2026-07-14
---

# Phase 96 Plan 01: NOTF-26 Snooze Backend Summary

**Adds a nullable `Notificacao.snoozedUntil` column, a snooze-aware visibility predicate on the two unread-surface queries, and a `PATCH /notificacoes/{id}/snooze` endpoint that lets a user defer a reminder by a fixed 1/3/7-day preset while blocking PRAZO_VENCIDO and never touching `AlertasDiariosJob` or `uk_notificacao_dedup`.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-14T17:03:00Z (approx, worktree fast-forward + context read)
- **Completed:** 2026-07-14T17:23:56Z
- **Tasks:** 2/2 completed
- **Files modified:** 6 (5 modified, 1 created)

## Accomplishments
- `Notificacao.snoozedUntil` (nullable, `@Setter`, mirrors `lida`'s mutability pattern) plus the paired manual migration `backend/migrations/96-add-notificacao-snoozed-until.sql`.
- Badge-count and mark-all-read queries now hide currently-snoozed rows (`n.snoozedUntil IS NULL OR n.snoozedUntil <= :agora`) while the `/notificacoes` history query (`buscarPorFiltros`) stays unfiltered and byte-for-byte unchanged.
- `NotificacaoService.snooze(...)` enforces preset validation (`{1,3,7}`, before any repository access), dual-scoped ownership (404 via empty `Optional`), and the PRAZO_VENCIDO block (400), persisting via a single `save()`.
- `PATCH /api/v1/notificacoes/{id}/snooze` endpoint, gated `notificacoes:view`, translating `IllegalArgumentException` -> 400 and empty `Optional` -> 404, tenant/destinatario always from the JWT.
- Full RED -> GREEN TDD cycle for the 4 required Mockito behaviors; full backend suite (65 tests) green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add snoozedUntil column + migration + read-side visibility predicate** - `b5b8f9c` (feat)
2. **Task 2 (RED): add failing test for NotificacaoService.snooze()** - `df1b974` (test)
3. **Task 2 (GREEN): implement NotificacaoService.snooze() + PATCH endpoint** - `1a7fc19` (feat)

_No REFACTOR commit was needed — the GREEN implementation was already clean._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/Notificacao.java` - adds nullable `snoozedUntil` field (`@Setter`, `@Column(name = "snoozed_until")`), not part of `uk_notificacao_dedup`.
- `backend/migrations/96-add-notificacao-snoozed-until.sql` - required manual production migration (`ALTER TABLE t_notificacao ADD COLUMN snoozed_until TIMESTAMP`).
- `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` - `countByTenantIdAndDestinatarioIdAndLidaFalse` and `findByTenantIdAndDestinatarioIdAndLidaFalse` converted to explicit JPQL `@Query` with an `agora` param and the snooze-visibility predicate; `buscarPorFiltros` untouched.
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java` - adds `SNOOZE_PRESETS_DIAS` constant and `@Transactional public Optional<Notificacao> snooze(UUID, UUID, UUID, int)`; `marcarTodasLidas` now passes `LocalDateTime.now()` to the updated query.
- `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` - adds `PATCH /{id}/snooze`; `contarNaoLidas` now passes `LocalDateTime.now()` to the updated query.
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` - updates the existing `marcarTodasLidas` test's stub for the new 3-arg signature; adds 4 new snooze tests (valid preset, invalid dias, not-owned, PRAZO_VENCIDO).

## Decisions Made
- Reused `CategoriaNotificacao.isSilenciavelCategoria` for the snooze block on PRAZO_VENCIDO (96-CONTEXT.md locked decision) instead of a parallel exception mechanism; the reuse is documented in-code with both the current justification and the forward-risk caveat (a future non-silenciable category for an unrelated reason would silently also become non-adiavel through this shared flag — split into a dedicated `isAdiavelCategoria` flag if/when that happens).
- Converted the two `LidaFalse` queries from Spring Data derived queries to explicit JPQL to add the additive `snoozedUntil` predicate, per the plan's required approach; `buscarPorFiltros` (native, history) and `findByIdAndTenantIdAndDestinatarioId` (single-row load reused by `snooze()`) were left untouched, as required.
- Placed `snooze()` and its `SNOOZE_PRESETS_DIAS` constant at the end of `NotificacaoService`, mirroring `marcarLida`'s find-then-mutate-then-save shape rather than introducing a new pattern.

## Deviations from Plan

None - plan executed exactly as written. The only file not explicitly named as "modified" in the plan's frontmatter `files_modified` list that still required a one-line edit was the existing `marcarTodasLidas_marcaTodasNaoLidasDoDestinatarioEChamaSaveAllUmaVez` test's stub call (updated from a 2-arg to a 3-arg `findByTenantIdAndDestinatarioIdAndLidaFalse` stub) — this is a direct, in-scope consequence of Task 1's required repository signature change (already covered by `NotificacaoServiceTest.java` being in the plan's `files_modified` list) and was required for the codebase to compile; not a deviation in scope, just part of the planned file.

## Issues Encountered
- The worktree branch (`worktree-agent-ad0a89232a4374b8c`) was behind local `master` by 157 commits (missing Phases 90-95 entirely, including all of `NotificacaoService.java`'s intervening changes). Verified `HEAD` was a strict ancestor of `master` with zero unique commits on the worktree branch (`git log --oneline master..HEAD` = 0 lines), then fast-forwarded (`git merge --ff-only master`) before starting any plan work. No conflicts, no lost work.

## Next Phase Readiness
- This is the last plan in the 93->94->95->96 sequential `NotificacaoService.java`/`NotificacaoController.java`/`Notificacao.java`/`NotificacaoRepository.java` chain — Phase 97 (cross-cutting milestone audit) can now proceed treating this file set as stable.
- Frontend snooze UI (Popover + RadioGroup with 1/3/7-day presets, per 96-CONTEXT.md) is not part of this plan and remains for a subsequent plan/phase in this milestone's roadmap.
- `backend/migrations/96-add-notificacao-snoozed-until.sql` is a required manual production migration (no automated runner in this repo) — must be run against staging/prod before/alongside the deploy that ships this change, same as the 88/91/93 precedents.

---
*Phase: 96-notf-26-snooze-de-lembrete-de-prazo*
*Completed: 2026-07-14*

## Self-Check: PASSED

All 6 modified/created source files and the plan's manual migration confirmed present on disk;
all 3 task commit hashes (`b5b8f9c`, `df1b974`, `1a7fc19`) confirmed present in `git log --oneline --all`.
