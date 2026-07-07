---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
verified: 2026-07-07T21:28:41Z
status: human_needed
score: 12/12 must-haves verified (code-level); live HTTP round-trip outstanding
re_verification: false
human_verification:
  - test: "POST /api/v1/processos/intake without 'origem' in body"
    expected: "HTTP 422 with camposEmFalta containing 'origem', no row persisted"
    why_human: "No executor (across 81-01/02/03) could complete an authenticated HTTP round-trip against the local dev DB — admin@lexcv.cv/admin123 returns 401 because this DB already contains real, non-seed data and AuthController enforces a 5-attempt lockout. All evidence for this behavior is code-level (direct read of the 422 gate in createProcessoIntake) plus a successful mvn package + spring-boot:run startup, not a live curl/Postman assertion."
  - test: "POST /api/v1/processos/intake with valid origem, then PUT /api/v1/processos/{id} with a different origem + a juizo value, then GET both /processos/{id} and /processos (list)"
    expected: "origem unchanged after PUT (immutability), juizo updated, and GET /processos (list) surfaces juizo/origem matching the detail view"
    why_human: "Same credential/lockout constraint as above — verified by code review of updateProcesso's field-copy exclusion and listProcessos' enriched map only, not exercised live."
  - test: "Full lifecycle (create -> list -> update -> cross-processo 404 -> delete) for Decisão (incl. multipart file upload), Testemunha, and Facto, using two different processo ids to prove the double-check ownership pattern rejects a mismatched processoId/childId pair with 404"
    expected: "Create succeeds (201), list shows the new row, update succeeds (200), a PUT/DELETE using a different processo's id in the path but the first processo's child id returns 404, delete succeeds (200) and the row disappears from a subsequent list"
    why_human: "This IDOR-relevant behavior is the single highest-risk claim in this phase (PROC-17) and has never been exercised against a running server with a real HTTP client — only verified by static code inspection of the double-check pattern (parent tenant check + child.getProcessoId().equals(id) check) in updateDecisao/deleteDecisao/updateTestemunha/deleteTestemunha/updateFacto/deleteFacto, all of which are present and correctly ordered, but a grep confirming the pattern exists is not the same as a 404 actually observed at runtime."
  - test: "Two near-simultaneous POST /api/v1/processos/{id}/factos requests with a client-forged 'ordem': 999 in both payloads"
    expected: "Both succeed with distinct, server-computed sequential ordem values (not 999); no 409 under normal (non-adversarial-timing) concurrency; a genuine same-millisecond race should still produce two valid ordem values without a duplicate, given the synchronized block + unique-constraint backstop"
    why_human: "Concurrency behavior cannot be reliably verified by static code reading alone — the synchronized(FactoRepository.class) block and DataIntegrityViolationException-to-409 catch are both present in source, but nobody has actually fired two concurrent requests at a running instance to confirm the race window is closed as designed."
  - test: "Run backend/migrations/81-add-facto-ordem-unique-constraint.sql against a database where ddl-auto=validate (i.e. a prod-like environment) and confirm createFacto/updateFacto return 409 (not 500) on a genuine (processo_id, ordem) collision post-migration"
    expected: "The manual SQL script applies cleanly and the unique constraint is enforced identically to how it already is in dev (where ddl-auto=update auto-created it)"
    why_human: "This is an operational/deployment step outside the codebase itself — the script's correctness (right table/column names) was verified by reading Facto.java, but its actual execution against a prod-like schema has not been performed by any agent in this phase."
---

# Phase 81: Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem Verification Report

**Phase Goal:** A API expõe CRUD completo e seguro para Decisões, Factos e Testemunhas, e os campos Juízo/Origem estão totalmente integrados no ciclo de vida do Processo (criação, edição, intake e listagem).
**Verified:** 2026-07-07T21:28:41Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `POST /processos/intake` sem `origem` é rejeitado com 422 e `camposEmFalta` contendo `origem` | VERIFIED (code) | `ResourceController.java:1032-1037` — gate placed before `processo.setEstado("TRIAGEM")`/save |
| 2 | `POST /processos/intake` com `origem` válida persiste e devolve `origem` | VERIFIED (code) | Same method continues to `processoRepository.save(processo)` and returns the saved entity (Jackson serializes `origem`) when the gate passes |
| 3 | `PUT /processos/{id}` com `origem` diferente no payload não altera o valor persistido, sem erro 400 | VERIFIED | `updateProcesso` (line ~1004) has comment "origem is intentionally excluded..." and no `setOrigem(...)` call anywhere in the method body |
| 4 | `PUT /processos/{id}` com `juizo` persiste esse valor | VERIFIED | `ResourceController.java:1002` — `processo.setJuizo(payload.getJuizo());` |
| 5 | `GET /processos` devolve `juizo`/`origem` no mapa enriquecido | VERIFIED | `ResourceController.java:923-924` — `m.put("juizo", ...)`, `m.put("origem", ...)` |
| 6 | `formalizarProcesso` rejeita com 422 um processo com `origem` null, para TODOS os `tipo_processo` incl. `default` | VERIFIED | `CAMPOS_MINIMOS_POR_TIPO` (lines 76-84) has `"origem"` appended to all 7 `List.of(...)` entries including `"default"`; switch has `case "origem" -> ...` (line 1224) |
| 7 | Utilizador `processos:edit` cria Decisão com upload multipart opcional num único pedido, sem seletor de documento pré-existente | VERIFIED | `createDecisao` (line 1677), `consumes = MULTIPART_FORM_DATA_VALUE`, builds `Documento` internally and links `decisao.setDocumentoId(savedDoc.getId())` — no document-picker parameter exists |
| 8 | Utilizador `processos:view` lista Decisões de um processo | VERIFIED | `listDecisoes` (line 1666), `@PreAuthorize("hasAuthority('processos:view')")` |
| 9 | Utilizador `processos:edit` edita e remove Decisão existente | VERIFIED | `updateDecisao` (1752), `deleteDecisao` (1800) both `processos:edit` |
| 10 | PUT/DELETE numa Decisão de outro processo (mesmo tenant) é rejeitado com 404 | VERIFIED (code) | Double-check pattern present in `updateDecisao`/`deleteDecisao`: parent tenant check + `decisao.getProcessoId().equals(id)` check, both before any mutation |
| 11 | Utilizador `processos:edit` cria/edita/remove Testemunhas; `processos:view` lista | VERIFIED | `createTestemunha`/`updateTestemunha`/`deleteTestemunha` (edit), `listTestemunhas` (view) all present and correctly gated |
| 12 | PUT/DELETE numa Testemunha de outro processo é rejeitado com 404 | VERIFIED (code) | Double-check pattern present in `updateTestemunha`/`deleteTestemunha` |
| 13 | Utilizador `processos:edit` cria Facto; `ordem` é sempre calculado no servidor (`max+1`), ignorando `ordem` do payload de criação | VERIFIED | `createFacto` (1944) never reads `facto.getOrdem()` from the incoming payload before overwriting it inside the `synchronized` block; `nextOrdem` is always server-computed |
| 14 | Utilizador `processos:view` lista Factos ordenados por `ordem` ascendente | VERIFIED | `listFactos` calls `factoRepository.findByProcessoIdOrderByOrdemAsc(id)`, not the unordered variant |
| 15 | Utilizador `processos:edit` reordena Facto via PUT com `ordem` explícito | VERIFIED | `updateFacto` (1968) sets `facto.setOrdem(payload.getOrdem())` unconditionally (no recompute) |
| 16 | Utilizador `processos:edit` remove Facto | VERIFIED | `deleteFacto` (2002), `processos:edit` |
| 17 | PUT/DELETE num Facto de outro processo é rejeitado com 404 | VERIFIED (code) | Double-check pattern in `updateFacto`/`deleteFacto` |
| 18 | Dois Factos criados em sequência no mesmo processo nunca recebem o mesmo `ordem`, mesmo sob concorrência | VERIFIED (code, single-JVM) + DB backstop | `synchronized (FactoRepository.class)` wraps both the max-lookup and the save (closing the intra-JVM race); `Facto.java` has `@UniqueConstraint(columnNames = {"processo_id", "ordem"})` as a DB-level backstop for multi-instance deployments, and both `createFacto`/`updateFacto` now catch `DataIntegrityViolationException` → 409. A manual production migration script (`backend/migrations/81-add-facto-ordem-unique-constraint.sql`) exists to apply the constraint where `ddl-auto=validate`. **Concurrency behavior itself was never exercised live** — see human_verification. |

**Score:** 18/18 truths verified at the code level. 0 failed. All items requiring live-server behavioral confirmation are routed to human verification (not treated as failures), per this phase's documented constraint that no executor could obtain authenticated HTTP access to the local dev DB.

### Second-Round Code Review Fixes — Verified on Disk (not just SUMMARY/FIX-report claims)

| # | Fix | Status | Evidence |
|---|-----|--------|----------|
| a | `createDecisao` AND `deleteDecisao` both `@Transactional` | VERIFIED | `ResourceController.java:1674` (`createDecisao`) and `:1797` (`deleteDecisao`) both carry `@Transactional` directly above `@PreAuthorize` |
| b | `deleteDecisao` cleans up the linked `Documento` (storage object + DB row) | VERIFIED | `ResourceController.java:1810-1822` — tenant-scoped `Documento` lookup, `storageService.delete(...)` wrapped in try/catch for `StorageUnavailableException`, then `documentoRepository.delete(d)`, all inside the now-transactional method |
| c | `updateDecisao`/`createTestemunha`/`updateTestemunha` validate required fields, return clean 400s on invalid enum values | VERIFIED | All three use `@RequestBody Map<String, Object>`; `updateDecisao` null/blank-checks `data`/`tipo` (400) and wraps `TipoDecisao.valueOf(...)` in try/catch (400); `createTestemunha`/`updateTestemunha` null/blank-check `nome` (400) and wrap `TipoTestemunha.valueOf(...)` in try/catch (400) |
| d | `createFacto` AND `updateFacto` both catch `DataIntegrityViolationException` on `(processo_id, ordem)` → 409 | VERIFIED | `createFacto`: lines 1953-1962; `updateFacto`: lines 1992-1997 — both catch and return `HttpStatus.CONFLICT` with the same Portuguese message |
| e | `Facto.java` has `@UniqueConstraint` | VERIFIED | `Facto.java:9` — `@Table(name = "t_facto", uniqueConstraints = @UniqueConstraint(columnNames = {"processo_id", "ordem"}))` |
| f | `backend/migrations/81-add-facto-ordem-unique-constraint.sql` exists with correct table/column names | VERIFIED | File exists, contains `ALTER TABLE t_facto ADD CONSTRAINT uk_facto_processo_ordem UNIQUE (processo_id, ordem);` — table/column names match `Facto.java`'s `@Table(name = "t_facto")` / `@Column(name = "processo_id")` / `ordem` |

All 6 items from the second review-fix round are confirmed present and correctly wired in the code currently on disk (not merely claimed in REVIEW-FIX-2.md). `git status` shows no uncommitted changes to any of the affected files, and all referenced commit hashes (91031c9, 40955ec, 9be1ebc, 819f658, 62d158b, 47be5c3, 60ff17a, plus the three `feat` commits 47cbcd6/97dc5a2/f2c6304/b0f4af2/7233de5) exist in `git log`.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | origem/juizo wiring + 12 new CRUD endpoints | VERIFIED | All edits present, confirmed by direct read; compiles cleanly |
| `backend/src/main/java/com/lexcv/repositories/FactoRepository.java` | `findMaxOrdemByProcessoId` `@Query` method | VERIFIED | Present, `@Query`/`@Param`, returns `Optional<Integer>` |
| `backend/src/main/java/com/lexcv/models/Facto.java` | `@UniqueConstraint(processo_id, ordem)` | VERIFIED | Present |
| `backend/migrations/81-add-facto-ordem-unique-constraint.sql` | Manual prod migration script | VERIFIED | Present, correct table/column names, documents why it's needed |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `createProcessoIntake` | `Processo.origem` | 422 gate before `save()` | WIRED | Confirmed, gate precedes persistence |
| `formalizarProcesso` switch | `Processo.origem` | `case "origem"` arm | WIRED | Confirmed |
| `listProcessos` enriched map | `Processo.juizo`/`origem` | `m.put(...)` | WIRED | Confirmed |
| `updateProcesso` | `Processo.juizo` | `setJuizo(payload...)` | WIRED | Confirmed |
| `createDecisao` (multipart) | `Documento` + `Decisao.documentoId` | `storageService.upload → documentoRepository.save → decisao.setDocumentoId` | WIRED | Confirmed, entire chain present and (post-fix) transactional |
| `updateDecisao`/`deleteDecisao`/`updateTestemunha`/`deleteTestemunha`/`updateFacto`/`deleteFacto` | parent `Processo` + child `processoId` re-check | double-check pattern | WIRED (statically) | All 6 confirmed by direct code read; NOT exercised at runtime (see human_verification) |
| `createFacto`/`updateFacto` | `(processo_id, ordem)` unique constraint | `DataIntegrityViolationException` → 409 | WIRED | Confirmed for both endpoints (this closes the WR-02 gap from REVIEW-2) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PROC-02 | 81-01 | Juízo visível na ficha (view/edit) | SATISFIED (backend) | `juizo` persisted/returned by create/update/list; UI "ficha" rendering is Phase 84 scope, not this phase's — non-blocking per phase-checker's prior note |
| PROC-03 | 81-01 | Origem obrigatória no intake | SATISFIED | 422 gate confirmed |
| PROC-04 | 81-01 | Origem validada em backend (`intake` + `CAMPOS_MINIMOS_POR_TIPO`) | SATISFIED | Both confirmed; frontend Zod half is out of this phase's scope (Phase 83/84) |
| PROC-05 | 81-01 | Origem imutável após formalização | SATISFIED | `updateProcesso` exclusion confirmed |
| PROC-07 | 81-02 | Upload multipart direto na criação da Decisão | SATISFIED | Confirmed, no document picker |
| PROC-08 | 81-02 | Listar/editar/remover Decisões | SATISFIED (backend) | All 4 endpoints present; "sub-secção dedicada" UI phrasing is Phase 84 scope |
| PROC-10 | 81-03 | Listar/editar/remover/reordenar Factos | SATISFIED (backend) | All 4 endpoints + ordem sequencing/reorder confirmed; UI sub-section is Phase 84 |
| PROC-12 | 81-02 | Listar/editar/remover Testemunhas | SATISFIED (backend) | All 4 endpoints present; UI sub-section is Phase 84 |
| PROC-17 | 81-02, 81-03 | Double-check tenant+processoId em todas as escritas | SATISFIED (code-level) | Confirmed across all 6 write endpoints (Decisão×2, Testemunha×2, Facto×2); not yet exercised live (routed to human_verification) |

No orphaned requirements: all 9 IDs declared across the three plans' frontmatter (PROC-02/03/04/05/07/08/10/12/17) match exactly the 9 IDs listed in this phase's ROADMAP.md entry and are all marked "Complete" with "Phase 81" in REQUIREMENTS.md's traceability table.

**Note on requirement text vs. phase scope:** REQUIREMENTS.md's full text for PROC-02/08/10/12 includes frontend-UI phrasing ("visível na ficha", "sub-secção dedicada dentro de Informação do Processo") that this backend-only phase does not deliver. That UI work is correctly scoped to Phase 84 per ROADMAP.md's explicit phase sequencing (fundação → backend → frontend types/hooks → frontend UI). This was already flagged by the plan-checker as a known, non-blocking artifact of the requirements-tracking table, not a defect in Phase 81's execution — Phase 81's own ROADMAP-defined goal and 5 success criteria are backend-only and are fully met by the evidence above. This verifier concurs with that framing.

### Anti-Patterns Found

None. Grep for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER` and case-insensitive `placeholder|coming soon|will be here|not yet implemented|not available` across `ResourceController.java`, `Facto.java`, and `FactoRepository.java` returned zero matches. No debt markers, no stub returns, no empty handlers found in any of the 12 new endpoints or the juizo/origem wiring.

### Build Verification

`cd backend && mvn -DskipTests package -q` exits 0 (confirmed independently by this verifier, not just cited from SUMMARY.md). `git status` shows no uncommitted changes to any of the phase's modified files — the code on disk matches what's committed.

### Behavioral Spot-Checks

Skipped — this phase's endpoints require an authenticated session (JWT httpOnly cookie) and a running Postgres+MinIO stack; no safe, non-destructive, credential-free spot-check is available. This constraint is identical to the one all three executors already documented (local DB has real data, default admin credentials return 401, and further guesses were correctly avoided to prevent lockout). This verifier did not attempt to start the server or exercise live endpoints for the same reasons, and routes the equivalent checks to Human Verification below instead of marking them FAILED.

### Probe Execution

Skipped — no `scripts/*/tests/probe-*.sh` files exist in this repository and neither the PLAN files nor SUMMARY files for this phase reference any probe script. Not a migration/tooling phase in the probe-execution sense.

### Human Verification Required

See YAML frontmatter `human_verification` for the full structured list. In summary, 5 items need a human (or a future agent with valid credentials / a fresh seeded DB) to exercise live HTTP calls against a running backend:

1. Intake 422-on-missing-origem and 201-with-echoed-origem-on-valid-value.
2. Origem-immutability-after-PUT + juizo-persisted-after-PUT + list-view parity, exercised live.
3. Full CRUD lifecycle + cross-processo 404 (IDOR) proof for Decisão/Testemunha/Facto, exercised live — this is the single most load-bearing check for PROC-17 and has zero live confirmation across all three plans in this phase.
4. Facto `ordem` concurrency-safety under actual concurrent requests.
5. Execution of the manual production migration script against a `ddl-auto=validate` environment.

None of these are treated as failures — the code-level evidence for all of them is solid (correct patterns, correct ordering, correct exception handling, all confirmed by direct file reads in this verification, not by trusting SUMMARY.md). They are deferred to human verification because this is exactly the kind of runtime behavior static analysis cannot fully close out, and three separate executor agents independently hit the same credential/lockout wall trying to do so.

### Gaps Summary

No code-level gaps. All must-haves from all three plans' frontmatter, all 5 ROADMAP success criteria, and all 6 second-round code-review fixes are verified present and correctly wired in the code currently on disk. The only reason this report is not `status: passed` is that a meaningful set of runtime/IDOR/concurrency behaviors have never been exercised against a live server by any agent in this phase's history (all three plan executors independently documented the same credential-lockout constraint), which per this verification's process must route to `human_needed` rather than being either waved through as `passed` or incorrectly marked `gaps_found`/`FAILED` when the underlying code is demonstrably correct.

---

_Verified: 2026-07-07T21:28:41Z_
_Verifier: Claude (gsd-verifier)_
