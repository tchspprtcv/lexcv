---
phase: 64-auditoria-e-pesquisa-avan-ada
reviewed: 2026-06-30T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
  - backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
status: clean
---

# Phase 64: Code Review Report (Re-review)

**Reviewed:** 2026-06-30
**Depth:** standard
**Files Reviewed:** 2
**Status:** clean

## Summary

Re-reviewed after fixes for CR-01 and WR-02 (commits c0b490b, 2126d3a). Both are
confirmed resolved.

**CR-01 (resolved):** `ParecerSolicitacaoRepository.pesquisar()` now uses
`LEFT JOIN t_parecer_versao v ON v.solicitacao_id = s.id AND v.numero_versao = (SELECT MAX(...))`
instead of an inner `JOIN`. Traced the resulting truth table directly against
the query text:
- Solicitação with zero versions, `texto` null → `v.*` is NULL from the LEFT
  JOIN, and `(:texto IS NULL OR v.conteudo ILIKE ...)` short-circuits true on
  the first disjunct without evaluating `v.conteudo ILIKE ...` — row is kept.
  This matches the intended "PENDENTE with no version yet" case.
- Solicitação with zero versions, `texto` provided → first disjunct is false,
  second evaluates `NULL ILIKE '%texto%'` → NULL → WHERE excludes the row.
  Correctly excluded, since there is no `conteudo` to match.
- Solicitação with versions, `texto` provided, latest version matches →
  included, as before.

This confirms the fix behaves exactly as described in the commit's inline
comment (lines 18-20) and resolves the original bug where `clienteId`/`status`
-only searches silently dropped PENDENTE solicitações.

**WR-02 (resolved):** `@Transactional` is now present on all 5 handler
methods that perform a primary save followed by an audit-log save:
`createSolicitacao` (:91), `atribuirAdvogado` (:230), `aprovarVersao` (:286),
`entregarSolicitacao` (:332), `createVersao` (:406). Checked each:
- All 5 are read-write operations (entity mutation + audit insert) — default
  (non-read-only) `@Transactional` is the correct annotation; no method here
  warrants `readOnly = true`.
- No pre-existing programmatic transaction management (`TransactionTemplate`,
  manual `EntityManager` flush/commit) exists in the file that these new
  annotations could conflict with.
- No nested `@Transactional` calls into other `@Transactional` service beans
  from within these methods that would trigger unexpected propagation
  behavior — the repository/storage calls used here are plain
  `JpaRepository`/`StorageService` calls, not another `@Transactional`
  controller/service layer.
- `createVersao` combines `@Transactional` with a `synchronized
  (ParecerVersaoRepository.class)` block for version-number assignment. This
  is unchanged pre-existing behavior (JVM-local mutual exclusion, not a DB
  lock) and is not weakened or strengthened by the new annotation — the
  transaction commits after the Spring AOP proxy returns, i.e. after the
  synchronized block has already exited. Not a new defect; noted as
  informational only (see IN-02 below) since it could read as if
  `@Transactional` now provides the concurrency safety, when it does not.

No other regressions found in the surrounding tenant-scoping, `@PreAuthorize`,
or validation logic — untouched by this fix and still correct on inspection.

## Warnings

### WR-01: `v.conteudo ILIKE '%' || :texto || '%'` against a nullable column can silently skip real matches when the latest version has NULL conteudo

**Status:** Open by design (carried over, not re-flagged as new).

**File:** `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java:30`
**Issue:** `ParecerVersao.conteudo` is nullable (attachment-only versions have
`conteudo = NULL`). If the most recent version of a solicitação has
`conteudo = NULL`, `NULL ILIKE '%...%'` evaluates to NULL (falsy), so a
`texto` search silently excludes that solicitação even though this may not be
the intent of "attachment-only versions aren't discoverable via text search."
This is consistent with the plan's documented scope but remains untested and
undocumented.
**Fix:** Document explicitly in PATTERNS.md or the endpoint doc that
attachment-only latest versions are not discoverable via `texto` search, or
use `COALESCE(v.conteudo, '')` if the intent should be made explicit rather
than implicit via NULL propagation.

## Info

### IN-01: `dataInicio`/`dataFim` bound as `LocalDateTime` query params without `@DateTimeFormat`

**Status:** Open by design (carried over, not re-flagged as new).

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:177-178`
**Issue:** The plan (64-02-PLAN.md, line 137) suggests adding
`@DateTimeFormat(iso = ...)` for query-string binding of `dataInicio`/
`dataFim`. The implementation omits it, relying on Spring's default
`LocalDateTime` converter, which requires exact ISO-8601 without a `Z`/offset
suffix. Callers sending `Z`-suffixed values get an unhelpful generic 400.
**Fix:**
```java
@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim
```

### IN-02: `createVersao`'s new `@Transactional` does not extend to / interact with the pre-existing `synchronized` block's concurrency guarantee

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java:406, 449-474`
**Issue:** `createVersao` is now `@Transactional`, and separately wraps its
version-number read-increment-save in `synchronized (ParecerVersaoRepository.class)`.
The `synchronized` block provides JVM-local mutual exclusion only (not a DB
lock/`SELECT ... FOR UPDATE`), and the surrounding `@Transactional` proxy
commits only after the whole method returns — i.e., strictly after the
`synchronized` block has already released. This means concurrent requests
across multiple app instances (horizontal scaling) can still race on
`numero_versao` despite both annotations being present; the two mechanisms
solve different problems and neither closes that gap. Not a new defect
introduced by this fix (the `synchronized` block predates it and its scope is
unchanged), but worth flagging so it isn't mistaken for "now transactionally
safe against concurrent version creation across instances."
**Fix:** No change required for this phase. If multi-instance deployment is a
real concern, consider a DB-level unique constraint on
`(solicitacao_id, numero_versao)` with retry-on-conflict, or a
`SELECT MAX(...) FOR UPDATE` inside the transaction, rather than relying on
JVM `synchronized`.

---

_Reviewed: 2026-06-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
