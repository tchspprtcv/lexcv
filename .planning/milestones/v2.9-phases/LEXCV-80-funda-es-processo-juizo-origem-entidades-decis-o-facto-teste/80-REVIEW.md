---
phase: LEXCV-80-funda-es-processo-juizo-origem-entidades-decis-o-facto-teste
reviewed: 2026-07-07T12:15:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/OrigemProcesso.java
  - backend/src/main/java/com/lexcv/models/TipoDecisao.java
  - backend/src/main/java/com/lexcv/models/TipoTestemunha.java
  - backend/src/main/java/com/lexcv/models/Decisao.java
  - backend/src/main/java/com/lexcv/models/Facto.java
  - backend/src/main/java/com/lexcv/models/Testemunha.java
  - backend/src/main/java/com/lexcv/repositories/DecisaoRepository.java
  - backend/src/main/java/com/lexcv/repositories/FactoRepository.java
  - backend/src/main/java/com/lexcv/repositories/TestemunhaRepository.java
  - backend/src/main/java/com/lexcv/models/Processo.java
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
status: issues_found
---

# Phase LEXCV-80: Code Review Report

**Reviewed:** 2026-07-07T12:15:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

This phase is a pure data-layer change (3 enums, 3 lean JPA entities, 3 Spring Data repositories, one modified entity). Structurally it faithfully mirrors `Parte.java`'s shape (Integer IDENTITY id, `processo_id` FK, no `tenant_id` column) and the `Cliente.documentoTipo` enum-as-string convention, exactly as the SUMMARY.md and PATTERNS.md describe. The two deliberate, already-tracked deferrals — no `tenant_id` on the three new entities and the unvalidated `Decisao.documentoId` FK — are explicitly out of scope per the review brief and are not re-flagged here.

Cross-referencing the new entities against other free-text-bearing entities in the same package (`ClienteNota`, `Cliente`, `ParecerVersao`, `ParecerSolicitacao`) surfaced one real gap: three of the new long-form text columns don't follow the codebase's established `columnDefinition = "TEXT"` convention and will silently default to `VARCHAR(255)`, which is very likely to be too short for their real-world content. Two minor code-quality notes round out the findings. No security issues, no logic bugs in the enums/repositories, and no violations of the mandated `Parte.java`-mirroring shape were found otherwise.

## Warnings

### WR-01: Long-form text fields default to VARCHAR(255) instead of following the codebase's TEXT convention

**File:** `backend/src/main/java/com/lexcv/models/Decisao.java:30`, `backend/src/main/java/com/lexcv/models/Facto.java:24`, `backend/src/main/java/com/lexcv/models/Testemunha.java:30`

**Issue:** `Decisao.resumo`, `Facto.descricao`, and `Testemunha.notas` are plain `String` fields with no `@Column(columnDefinition = "TEXT")`, so Hibernate/PostgreSQL will create them as `VARCHAR(255)`. Every other long-form free-text field in the codebase that holds prose content (as opposed to a short label/code) explicitly opts into `TEXT`:
- `ClienteNota.conteudo` — `@Column(nullable = false, columnDefinition = "TEXT")`
- `Cliente.descricaoCaso` — `@Column(name = "descricao_caso", columnDefinition = "TEXT")`
- `ParecerVersao` / `ParecerSolicitacao` fields — `@Column(columnDefinition = "TEXT", ...)`

A court decision summary (`resumo`), a legal fact narrative (`descricao`), and free-form witness notes (`notas`) are exactly this class of content and will realistically exceed 255 characters in normal use. Once Phase 81's controller starts persisting real data through these entities, ordinary-length input will fail with a PostgreSQL `value too long for type character varying(255)` error on `save()`/`flush()`. This is a latent correctness bug being carried forward into the next phase rather than something visible today (no controller writes to these fields yet), which is why it's a Warning rather than a Critical for *this* phase, but it should be fixed before Phase 81 builds CRUD on top of these entities so the bug isn't inherited silently.

**Fix:**
```java
// Decisao.java
@Column(columnDefinition = "TEXT")
private String resumo;

// Facto.java
@Column(nullable = false, columnDefinition = "TEXT")
private String descricao;

// Testemunha.java
@Column(columnDefinition = "TEXT")
private String notas;
```
Note: since `ddl-auto=update` only adds/alters columns and this is greenfield (no existing rows), this change is safe to make now without a manual migration; it gets materially harder to fix for free once Phase 81 starts writing rows into `t_decisao`/`t_facto`/`t_testemunha`.

## Info

### IN-01: `FactoRepository.findByProcessoId` is redundant given `findByProcessoIdOrderByOrdemAsc`

**File:** `backend/src/main/java/com/lexcv/repositories/FactoRepository.java:9-10`

**Issue:** `FactoRepository` declares both `findByProcessoId(UUID processoId)` and `findByProcessoIdOrderByOrdemAsc(UUID processoId)`. Since `Facto.ordem` exists specifically to give facts a stable per-processo order (per CONTEXT.md: "Facto.ordem — integer, scoped per processo_id"), any real caller (Phase 81's listing endpoint) will want the ordered variant, leaving the unordered `findByProcessoId` as unused surface area/dead code once that phase lands.

**Fix:** Consider dropping the unordered `findByProcessoId` now and keeping only `findByProcessoIdOrderByOrdemAsc`, unless there's a known future caller that specifically wants unordered results. Low priority — harmless as-is, just unnecessary API surface.

### IN-02: No uniqueness guard on `(processo_id, ordem)` for `Facto`

**File:** `backend/src/main/java/com/lexcv/models/Facto.java:28-29`

**Issue:** `ordem` is documented as scoped per `processo_id` (i.e., an ordering index within a given processo's facts), but nothing in the entity or repository prevents two `Facto` rows for the same `processo_id` from sharing the same `ordem` value. This is fine to leave as a data-layer concern for now (Phase 81's controller is the natural place to serialize/renumber on insert), but worth flagging so Phase 81 doesn't assume the DB enforces it.

**Fix:** Either add `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"processo_id", "ordem"}))` here, or explicitly own the invariant in Phase 81's controller (e.g., compute `ordem` server-side as `max(ordem)+1` per processo rather than trusting client input). No entity change required if the latter approach is chosen — just flagging so it isn't silently skipped in both layers.

---

_Reviewed: 2026-07-07T12:15:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
