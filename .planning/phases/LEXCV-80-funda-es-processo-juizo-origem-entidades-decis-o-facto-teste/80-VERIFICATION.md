---
phase: LEXCV-80-funda-es-processo-juizo-origem-entidades-decis-o-facto-teste
verified: 2026-07-07T14:20:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 80: Fundações — Processo.juizo/origem + Entidades Decisão/Facto/Testemunha Verification Report

**Phase Goal:** A estrutura de dados jurídicos do processo (Juízo, Origem, Decisões, Factos, Testemunhas) existe na base de dados, estável e pronta para os endpoints e a UI construírem sobre ela, sem qualquer mudança visível para o utilizador ainda.
**Verified:** 2026-07-07T14:20:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `Processo` entity persists a free-text `juizo` column and an `OrigemProcesso`-enum-backed `origem` column on `t_processo` | ✓ VERIFIED | `Processo.java:41` `private String juizo;` (bare field, mirrors `tribunal`/`estado`); `Processo.java:43-45` `@Enumerated(EnumType.STRING) @Column(name = "origem") private OrigemProcesso origem;` |
| 2 | `OrigemProcesso`, `TipoDecisao` and `TipoTestemunha` enums exist with the exact confirmed constant values | ✓ VERIFIED | `OrigemProcesso.java`: `PETICAO_INICIAL, NOTIFICACOES_AVULSAS`; `TipoDecisao.java`: `DESPACHO, DECISAO_INTERLOCUTORIA, SENTENCA, ACORDAO`; `TipoTestemunha.java`: `AUTOR, REU` — all match plan/ROADMAP exactly, no extra/missing constants |
| 3 | `Decisao`, `Facto` and `Testemunha` are JPA `@Entity` classes with an `Integer` IDENTITY id, a `processo_id` FK, and explicitly NO `tenant_id` column | ✓ VERIFIED | All three files carry `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;` and `@Column(name = "processo_id", nullable = false) private UUID processoId;`. `grep -ic "tenant"` on all three files returns `0` |
| 4 | Each of `Decisao`, `Facto`, `Testemunha` has a Spring Data JPA repository extending `JpaRepository<Entity, Integer>` with `findByProcessoId(UUID)` | ✓ VERIFIED | `DecisaoRepository`, `FactoRepository`, `TestemunhaRepository` all extend `JpaRepository<X, Integer>` and declare `findByProcessoId(UUID processoId)`; `FactoRepository` additionally has `findByProcessoIdOrderByOrdemAsc(UUID)` |
| 5 | The backend compiles and starts cleanly against the existing PostgreSQL database with `ddl-auto=update`, applying the new columns/tables without any Hibernate schema error and without breaking existing `Processo` persistence | ✓ VERIFIED | `mvn -DskipTests package` re-run independently by the verifier: exit 0, no compile errors. Live `mvn spring-boot:run` re-run independently (local Postgres confirmed reachable on `5432`, MinIO vars supplied as process-level env vars per plan's documented fallback): log contains `Started BackendApplication`; `grep -c` for `SchemaManagementException/PropertyAccessException/could not execute statement/Unknown column/MappingException` = 0 |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/OrigemProcesso.java` | Enum `PETICAO_INICIAL \| NOTIFICACOES_AVULSAS` | ✓ VERIFIED | Exact 2 constants, plain enum, no annotations (mirrors `DocumentoTipo.java`) |
| `backend/src/main/java/com/lexcv/models/TipoDecisao.java` | Enum `DESPACHO \| DECISAO_INTERLOCUTORIA \| SENTENCA \| ACORDAO` | ✓ VERIFIED | Exact 4 constants |
| `backend/src/main/java/com/lexcv/models/TipoTestemunha.java` | Enum `AUTOR \| REU` | ✓ VERIFIED | Exact 2 constants |
| `backend/src/main/java/com/lexcv/models/Processo.java` | `juizo` (String) and `origem` (OrigemProcesso, EnumType.STRING) columns added to existing entity | ✓ VERIFIED | Both fields present, all pre-existing fields (tenant_id, cliente_id, tribunal, estado, etc.) untouched |
| `backend/src/main/java/com/lexcv/models/Decisao.java` | Entity: Integer id, processoId FK, data, tipo (TipoDecisao), resumo, documentoId (nullable FK) | ✓ VERIFIED | All fields present; `resumo` carries `@Column(columnDefinition = "TEXT")` (post-review-fix state, see below) |
| `backend/src/main/java/com/lexcv/models/Facto.java` | Entity: Integer id, processoId FK, descricao, data, ordem (Integer, scoped per processo_id) | ✓ VERIFIED | `descricao` carries `@Column(nullable = false, columnDefinition = "TEXT")` (post-review-fix state) |
| `backend/src/main/java/com/lexcv/models/Testemunha.java` | Entity: Integer id, processoId FK, nome, contacto, tipo (TipoTestemunha), notas | ✓ VERIFIED | `notas` carries `@Column(columnDefinition = "TEXT")` (post-review-fix state) |
| `backend/src/main/java/com/lexcv/repositories/DecisaoRepository.java` | `JpaRepository<Decisao, Integer>` with `findByProcessoId(UUID)` | ✓ VERIFIED | Matches exactly |
| `backend/src/main/java/com/lexcv/repositories/FactoRepository.java` | `JpaRepository<Facto, Integer>` with `findByProcessoId(UUID)` and `findByProcessoIdOrderByOrdemAsc(UUID)` | ✓ VERIFIED | Both methods present |
| `backend/src/main/java/com/lexcv/repositories/TestemunhaRepository.java` | `JpaRepository<Testemunha, Integer>` with `findByProcessoId(UUID)` | ✓ VERIFIED | Matches exactly |

**Code-review fix verification (commit `4594aa6`, applied after original SUMMARY.md):** The fix claimed by `80-REVIEW-FIX.md` — adding `@Column(columnDefinition = "TEXT")` to `Decisao.resumo`, `Facto.descricao`, `Testemunha.notas` — was independently confirmed on disk (not just trusted from the fix report): all three fields carry the `TEXT` columnDefinition in the current file contents (see table above and full file reads). `git show --stat 4594aa6` confirms exactly these 3 files were touched, 3 insertions / 1 deletion, consistent with the claimed diff (Facto.descricao's existing `nullable = false` annotation was extended in place, the other two got a new line).

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `Processo.java` | `OrigemProcesso.java` | `@Enumerated(EnumType.STRING) @Column(name = "origem") private OrigemProcesso origem;` | ✓ WIRED | Exact match at `Processo.java:43-45` |
| `Decisao.java` | `TipoDecisao.java` | `@Enumerated(EnumType.STRING) @Column(nullable = false) private TipoDecisao tipo;` | ✓ WIRED | Exact match at `Decisao.java:26-28` |
| `Testemunha.java` | `TipoTestemunha.java` | `@Enumerated(EnumType.STRING) private TipoTestemunha tipo;` | ✓ WIRED | Exact match at `Testemunha.java:27-28` |
| `Decisao.java` | `Documento (t_documento)` | `@Column(name = "documento_id") private UUID documentoId;` — plain nullable FK, no relationship mapping | ✓ WIRED (as designed) | Present exactly as specified; deliberately no `@ManyToOne`/ownership validation at this layer (deferred to Phase 81 per threat model T-80-03, explicitly tracked) |
| `DecisaoRepository.java` / `FactoRepository.java` / `TestemunhaRepository.java` | `t_decisao` / `t_facto` / `t_testemunha` | `extends JpaRepository<Entity, Integer>`, derived-query `findByProcessoId(UUID)` | ✓ WIRED | Confirmed at compile time and at live-startup time (Hibernate mapped the entities to these repositories without error) |

### Data-Flow Trace (Level 4)

Not applicable — this phase introduces no controllers, no HTTP endpoints, and no UI. There is no rendering path or data-fetch call to trace; the entities/repositories are inert JPA mappings by design (explicitly documented in the plan's threat model as "the new entities are inert POJOs/JPA mappings until Phase 81 wires controller endpoints on top of them"). Level 4 trace is skipped as genuinely inapplicable to a pure data-layer phase.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full changeset compiles cleanly | `cd backend && mvn -DskipTests package -q` (re-run independently by verifier) | Exit 0, no output/errors | ✓ PASS |
| Application starts cleanly against local PostgreSQL with `ddl-auto=update` applying new schema | `MINIO_* ... SEED_ENABLED=false mvn spring-boot:run` (re-run independently by verifier, not just trusting SUMMARY.md's claim) | Log contains `Started BackendApplication`; 0 occurrences of `SchemaManagementException/PropertyAccessException/could not execute statement/Unknown column/MappingException` | ✓ PASS |
| No `tenant_id`/`tenant` string leaked into the three new entities | `grep -ic "tenant" Decisao.java Facto.java Testemunha.java` | `0`, `0`, `0` | ✓ PASS |

### Probe Execution

No conventional `scripts/*/tests/probe-*.sh` files exist in the repository and no probe paths are declared in this phase's PLAN/SUMMARY. Step 7c: SKIPPED (no probes declared for this phase).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PROC-01 | 80-01-PLAN.md | Utilizador pode registar o Juízo do processo (campo texto livre) | ✓ SATISFIED (data-layer scope) | `Processo.juizo` column now exists and persists via `ddl-auto=update`. Note: this requirement's full user-facing capability (actually registering Juízo via UI/API) is explicitly out of scope for Phase 80 per the ROADMAP goal wording ("sem qualquer mudança visível para o utilizador ainda") and is completed end-to-end only once Phase 81 (backend wiring) and Phase 83/84 (frontend) land. REQUIREMENTS.md's traceability table marks Phase 80 as the owning phase for PROC-01's data foundation, consistent with this plan's frontmatter `requirements: [PROC-01, ...]` |
| PROC-06 | 80-01-PLAN.md | Utilizador pode registar uma Decisão associada ao processo (data, tipo, resumo, anexo opcional) | ✓ SATISFIED (data-layer scope) | `Decisao` entity + `DecisaoRepository` created with all specified fields (`data`, `tipo` enum, `resumo` TEXT, `documentoId` nullable FK). Same caveat as PROC-01 — end-to-end user capability lands in Phase 81 |
| PROC-09 | 80-01-PLAN.md | Utilizador pode registar um Facto associado ao processo (descrição, data, ordem) | ✓ SATISFIED (data-layer scope) | `Facto` entity + `FactoRepository` created with `descricao` (TEXT, not-null), `data`, `ordem` (not-null, scoped per `processo_id`). Same caveat as PROC-01 |
| PROC-11 | 80-01-PLAN.md | Utilizador pode registar uma Testemunha associada ao processo (nome, contacto, tipo, notas) | ✓ SATISFIED (data-layer scope) | `Testemunha` entity + `TestemunhaRepository` created with `nome` (not-null), `contacto`, `tipo` enum, `notas` (TEXT). Same caveat as PROC-01 |

No orphaned requirements — all 4 requirement IDs declared in `80-01-PLAN.md` frontmatter (`PROC-01, PROC-06, PROC-09, PROC-11`) match exactly the 4 IDs REQUIREMENTS.md's traceability table maps to "Phase 80" (lines 65, 70, 73, 75). No gaps between declared and expected.

### Anti-Patterns Found

None. Scanned all 10 phase-modified files (`OrigemProcesso.java`, `TipoDecisao.java`, `TipoTestemunha.java`, `Processo.java`, `Decisao.java`, `Facto.java`, `Testemunha.java`, `DecisaoRepository.java`, `FactoRepository.java`, `TestemunhaRepository.java`) for `TODO|FIXME|XXX|TBD|HACK|PLACEHOLDER|placeholder|not implemented|coming soon` — zero matches. No stub returns, no empty handlers (none of these files have handlers — pure model/repository layer). `git status` confirms no uncommitted changes remain scoped to these files; only pre-existing unrelated untracked files (docx/pptx exports, a deleted unrelated audit doc) are present in the working tree.

### Human Verification Required

None. This phase is explicitly pure data-layer work with zero UI-visible change (per its own goal statement) and zero new HTTP endpoints — there is nothing requiring visual, UX, or real-time human verification. All must-haves are structurally/programmatically verifiable and were verified directly against the codebase and via an independently re-run live application startup.

### Gaps Summary

No gaps. All 5 derived must-have truths verified directly against on-disk code (not SUMMARY.md claims): the two new `Processo` columns, the three enums with exact locked constant values, the three lean child entities with correctly absent `tenant_id`, the three repositories with the required derived-query methods, and a live, independently-reproduced Spring Boot startup against local PostgreSQL confirming `ddl-auto=update` applied the new schema with zero Hibernate schema errors. The post-SUMMARY code-review fix (commit `4594aa6`, adding `TEXT` columnDefinition to the three prose fields) was independently confirmed present in the current file contents, not merely trusted from `80-REVIEW-FIX.md`'s narrative. All 4 requirement IDs (PROC-01/06/09/11) are accounted for in REQUIREMENTS.md's traceability table with matching phase assignment, satisfied at the data-foundation scope this phase's goal explicitly targets (full end-to-end user capability correctly deferred to Phase 81/83/84, as documented in ROADMAP.md and CONTEXT.md). Ready to proceed to Phase 81.

---

_Verified: 2026-07-07T14:20:00Z_
_Verifier: Claude (gsd-verifier)_
