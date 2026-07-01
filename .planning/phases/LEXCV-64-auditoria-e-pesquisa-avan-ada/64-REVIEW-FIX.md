---
phase: 64-auditoria-e-pesquisa-avan-ada
fixed_at: 2026-07-01T00:21:44Z
review_path: .planning/phases/LEXCV-64-auditoria-e-pesquisa-avan-ada/64-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase 64: Code Review Fix Report

**Fixed at:** 2026-07-01T00:21:44Z
**Source review:** .planning/phases/LEXCV-64-auditoria-e-pesquisa-avan-ada/64-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2 (CR-01, WR-02 — fix_scope: critical_warning, with WR-01 and IN-01 explicitly excluded by request)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### CR-01: Inner JOIN silently excludes solicitações with no versions from all search results, not just text search

**Files modified:** `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`
**Commit:** c0b490b
**Applied fix:** Changed the `JOIN t_parecer_versao v ON ...` in `pesquisar()` to a `LEFT JOIN`. The existing `(:texto IS NULL OR v.conteudo ILIKE '%' || :texto || '%')` predicate already short-circuits when `texto` is null, so with the LEFT JOIN a solicitação with zero versions now correctly matches filter-only queries (e.g. `clienteId`/`status`) and is only excluded when a non-null `texto` is supplied and there is no version content to match against.

### WR-02: No transaction boundary around primary-save + audit-save in the 5 transition endpoints

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** 2126d3a
**Applied fix:** Added `org.springframework.transaction.annotation.Transactional` import and `@Transactional` annotation to all 5 transition endpoint methods: `createSolicitacao`, `atribuirAdvogado`, `aprovarVersao`, `entregarSolicitacao`, and `createVersao`. Each method's primary entity save and its corresponding audit-log save now commit or roll back together, eliminating the risk of a committed primary state change accompanied by a misleading 500 response when the audit write fails.

## Skipped Issues

None in scope — WR-01 and IN-01 were explicitly excluded from this fix pass per instructions (WR-01 is expected SQL/NULL behavior, not a bug; IN-01 is a minor `@DateTimeFormat` gap, out of scope).

---

_Fixed: 2026-07-01T00:21:44Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
