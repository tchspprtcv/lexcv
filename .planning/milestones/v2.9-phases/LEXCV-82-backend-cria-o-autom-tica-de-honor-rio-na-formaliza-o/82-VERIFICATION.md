---
phase: LEXCV-82-backend-cria-o-autom-tica-de-honor-rio-na-formaliza-o
verified: 2026-07-07T22:40:00Z
status: human_needed
score: 4/4 must-haves verified (code-level); 2/2 code-review fixes (CR-01, WR-01) confirmed on disk; live HTTP/UI round-trip outstanding
re_verification: false
human_verification:
  - test: "Formalize a real processo (TRIAGEM→ATIVO) via `POST /api/v1/processos/{id}/formalizar` against a running backend with valid credentials, then immediately open `/financeiro` and `/financeiro/{honorarioId}` in a browser"
    expected: "Both pages render without crashing; the auto-created Honorário's total shows 'A confirmar' (not a thrown TypeError, not a blank/broken page); the 'Editar' dialog on the detail page opens with an empty (not literal 'null') Valor Total field"
    why_human: "No executor in this milestone (Phase 80/81/82) has been able to complete an authenticated HTTP round-trip against the local dev DB — admin@lexcv.cv/admin123 returns 401 because this DB already contains real, non-seed data, and further password attempts risk an account lockout. CR-01's fix (null-guarded formatMoneyCVE in both financeiro pages) was verified by direct code read and a clean `tsc --noEmit`, but nobody has observed the actual rendered page after a live formalizar call."
  - test: "Formalize the same processo a second time (retry/replay) → expect HTTP 409 (pre-existing estado guard); then query `SELECT processo_id, COUNT(*) FROM t_honorario GROUP BY processo_id HAVING COUNT(*) > 1` against the dev DB"
    expected: "Second call returns 409; the detection query returns zero rows; exactly one Honorario row exists for that processo_id with valorTotal literally JSON null"
    why_human: "Same credential/lockout constraint as above. The idempotency guard (`honorarioRepository.findByProcessoId(id).isEmpty()`) and the estado guard were both confirmed present and correctly ordered by code review, but the actual duplicate-prevention behavior has never been exercised against a live server."
  - test: "Fire two genuinely concurrent `POST /processos/{id}/formalizar` requests for the same processo id (e.g. two parallel curl/Postman requests, or two browser tabs racing) and confirm only one Honorario row is persisted, with no unhandled 500"
    expected: "Exactly one Honorario row survives; either both requests succeed (one creates, one silently no-ops after DataIntegrityViolationException) or the second is rejected cleanly by the pre-existing estado guard — no stack trace, no duplicate row"
    why_human: "WR-01's fix (unique constraint on Honorario.processoId + `catch (DataIntegrityViolationException ex) { }` around the save) was verified present in `Honorario.java` and `ResourceController.java` by direct code read, and `mvn package` compiles cleanly, but concurrency behavior cannot be verified by static reading alone — nobody has fired two simultaneous requests at a running instance to confirm the DB-level race window is actually closed as designed."
  - test: "Run `backend/migrations/82-add-honorario-processo-unique-constraint.sql` against a database where `ddl-auto=validate` (i.e. a prod-like environment) and confirm the constraint applies cleanly without a pre-existing duplicate-processo_id violation blocking it"
    expected: "The manual SQL script applies without error, and the unique constraint is enforced identically to how it is auto-created in dev (`ddl-auto=update`)"
    why_human: "This is an operational/deployment step outside the codebase itself. The script's correctness (table/column names matching `Honorario.java`) was verified by reading the file, but its actual execution against a prod-like schema — and confirmation that no pre-existing `t_honorario` rows already violate the new constraint — has not been performed by any agent in this phase."
---

# Phase 82: Backend — Criação Automática de Honorário na Formalização Verification Report

**Phase Goal:** Formalizar um processo (TRIAGEM→ATIVO) cria automaticamente e de forma segura um registo de Honorário associado, sem nunca preencher um valor financeiro sem confirmação explícita do utilizador.
**Verified:** 2026-07-07T22:40:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Formalizar um processo em TRIAGEM cria um Honorario associado, dentro da mesma transação de `formalizarProcesso` | VERIFIED | `ResourceController.java:1193` (`@Transactional` on the method) — the Honorario-creation block (lines 1251-1264) sits between `processo.setEstado("ATIVO")` (line 1249) and the final `return` (line 1266), inside the same method, no new transaction boundary introduced |
| 2 | Formalizar o mesmo processo uma segunda vez (retry/replay) não cria um segundo Honorario | VERIFIED | Two independent layers: (a) app-level `honorarioRepository.findByProcessoId(id).isEmpty()` guard (line 1253) runs before every creation, independent of the `estado != TRIAGEM` guard above it; (b) DB-level backstop added post-review — `Honorario.java:12` `@Table(uniqueConstraints = @UniqueConstraint(columnNames = "processo_id"))` + `catch (DataIntegrityViolationException ex)` around the save (lines 1261-1263) |
| 3 | O Honorario criado automaticamente tem `valorTotal = null` — nunca um número, nunca copiado de `Cliente.honorariosPropostos` | VERIFIED | `ResourceController.java:1257` — literal `.valorTotal(null)`; `grep -n "honorariosPropostos" ResourceController.java` returns zero matches anywhere in the file; `grep -n "getValorTotal\|\.valorTotal" ResourceController.java` shows exactly one write site (the literal `null`) and no reads |
| 4 | `POST /processos/{id}/formalizar` continua a devolver o Processo atualizado (sem mudança de contrato) | VERIFIED | Final statement unchanged: `return ResponseEntity.ok(processoRepository.save(processo));` (line 1266) — still serializes `Processo`, not `Honorario` |

**Score:** 4/4 truths verified at the code level. 0 failed.

### Code Review Fixes — Independently Confirmed on Disk (not just SUMMARY/REVIEW claims)

`82-REVIEW.md` found 1 Critical (CR-01) and 1 Warning (WR-01) against the initial implementation. Both were re-verified directly against current source, not trusted from commit messages.

| # | Fix | Status | Evidence |
|---|-----|--------|----------|
| CR-01 | Auto-created `Honorario.valorTotal: null` no longer crashes `/financeiro` or `/financeiro/[id]` | VERIFIED | `web/src/types/financeiro.ts:4` — `valorTotal: number \| null;` (was non-nullable `number`). `web/src/app/(dashboard)/financeiro/page.tsx:15-17` — `formatMoneyCVE(v: number \| null \| undefined)` returns `"A confirmar"` on `v == null` before ever calling `.toLocaleString()`; `calcHonorarioStatus` (line 22-27) and `kpiFaturado` (line 156, `h.valorTotal ?? 0`) are also null-guarded. `web/src/app/(dashboard)/financeiro/[id]/page.tsx:58-61` — same null-guarded `formatMoneyCVE`; `restante` (lines 232-235) computed only when `valorTotal != null`, else `null`; edit-form `defaultValues`/`reset` (lines 149, 158) now use `honorario.data?.valorTotal != null ? String(...) : ""` instead of prefilling the literal string `"null"`. Confirmed no other frontend consumer of the `Honorario` entity's `valorTotal` exists outside these two files (grepped `web/src/app` — the only other `honorario`-related hits are the unrelated `Cliente.honorarios_propostos` field on `clientes/[id]` pages, and legacy `_api-backup/` mock routes, out of scope). Confirmed no backend consumer of `Honorario.getValorTotal()` exists outside the single write site in `formalizarProcesso` (dashboard KPIs and `ContaCorrente` do not read it). |
| WR-01 | Check-then-act idempotency race now has a DB-level backstop | VERIFIED | `Honorario.java:12` — `@Table(name = "t_honorario", uniqueConstraints = @UniqueConstraint(columnNames = "processo_id"))`. `ResourceController.java:1253-1264` — the `save(...)` call is wrapped in `try { ... } catch (DataIntegrityViolationException ex) { /* outro formalizar concorrente já criou o honorário — seguro ignorar */ }`, matching the pattern already established for `Facto` in Phase 81. `backend/migrations/82-add-honorario-processo-unique-constraint.sql` exists (`ALTER TABLE t_honorario ADD CONSTRAINT uk_honorario_processo UNIQUE (processo_id);`), documenting the required manual prod migration since `ddl-auto=validate` in `application-prod.yml` won't auto-create the constraint there. |

Both fix commits (`035f2f9` for CR-01, `8d73927` for WR-01) exist in `git log`, and `git status --porcelain` shows no uncommitted drift on any of the affected files (`ResourceController.java`, `Honorario.java`, `financeiro.ts`, `financeiro/page.tsx`, `financeiro/[id]/page.tsx`, the migration script) — the code on disk matches the commits, not just their messages.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | Idempotent Honorario creation in `formalizarProcesso`, `valorTotal` always null | VERIFIED | Confirmed present at lines 1251-1264; `mvn -DskipTests package` exits 0 |
| `backend/src/main/java/com/lexcv/models/Honorario.java` | Unique constraint on `processo_id` (WR-01 fix) | VERIFIED | `@Table(uniqueConstraints = @UniqueConstraint(columnNames = "processo_id"))` present |
| `backend/migrations/82-add-honorario-processo-unique-constraint.sql` | Manual prod migration for the unique constraint | VERIFIED | Present, correct table/column name (`t_honorario`, `processo_id`), documents why manual application is required |
| `web/src/types/financeiro.ts` | `Honorario.valorTotal` typed nullable (CR-01 fix) | VERIFIED | `valorTotal: number \| null;` |
| `web/src/app/(dashboard)/financeiro/page.tsx` | Null-guarded rendering of `valorTotal` (CR-01 fix) | VERIFIED | `formatMoneyCVE`, `calcHonorarioStatus`, `kpiFaturado`, CSV export all null-guarded |
| `web/src/app/(dashboard)/financeiro/[id]/page.tsx` | Null-guarded rendering + edit-form defaults (CR-01 fix) | VERIFIED | `formatMoneyCVE`, `restante` calc, and `editForm` `defaultValues`/`reset` all null-guarded |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `formalizarProcesso` | `HonorarioRepository.findByProcessoId` | idempotency existence-check before creation | VERIFIED | `honorarioRepository.findByProcessoId(id).isEmpty()` at line 1253, confirmed by direct grep and read (the automated `gsd-sdk query verify.key-links` tool reported "Source file not found" because `from` is a method name rather than a resolvable file path — a tooling limitation, not an actual gap; manually confirmed instead) |
| `formalizarProcesso` | `Honorario.valorTotal` | hardcoded `null`, never read from `Cliente.honorariosPropostos` | VERIFIED | `.valorTotal(null)` at line 1257; zero matches for `honorariosPropostos` anywhere in the file |
| `formalizarProcesso` (Honorario save) | DB unique constraint | `DataIntegrityViolationException` catch | VERIFIED (code) | Present at lines 1253-1264; runtime race behavior not live-tested — see human_verification |
| `financeiro/page.tsx`, `financeiro/[id]/page.tsx` | `Honorario.valorTotal: number \| null` | null-guarded formatter/calc functions | VERIFIED | Confirmed by direct read in both files; `tsc --noEmit` passes cleanly on both (see below) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `financeiro/page.tsx` list rows | `h.valorTotal` | `useHonorarios()` → `GET /honorarios` → `honorarioRepository.findByProcessoId`/tenant-scoped iteration in backend | Yes — real DB rows, including the new auto-created `valorTotal: null` rows | FLOWING |
| `financeiro/[id]/page.tsx` detail | `honorario.data.valorTotal` | `useHonorario(id)` → `GET /honorarios/{id}` → `honorarioRepository.findById` | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles cleanly with all Phase 82 + review-fix changes combined | `cd backend && mvn -DskipTests package -q` | Exit 0, no errors | PASS |
| Frontend type-checks cleanly with all Phase 82 + review-fix changes combined | `cd web && npx tsc --noEmit` | Exit 0; only 2 pre-existing, unrelated errors (`Cannot find module 'vitest'` in `src/lib/cliente-documento-tipo.test.ts` and `src/schemas/clientes.legacy-documento-tipo.test.ts`, neither touched by this phase, both predating it per `git log`) | PASS |
| Live authenticated formalize → financeiro page render | N/A — could not authenticate | Not run | SKIP (routed to human_verification) |

### Probe Execution

No `scripts/*/tests/probe-*.sh` conventions or phase-declared probes found for this phase or repository. Skipped — no runnable probes.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| PROC-14 | 82-01-PLAN.md | Ao formalizar um processo (TRIAGEM→ATIVO), o sistema cria automaticamente um registo de Honorário associado — operação idempotente, com `valorTotal` em branco | SATISFIED | All 4 must-have truths verified at code level; `REQUIREMENTS.md:78` already marks it `Complete`; no orphaned requirements found (`REQUIREMENTS.md:70-80` shows only PROC-14 mapped to Phase 82, coverage line confirms 17/17 mapped) |

### Anti-Patterns Found

None. No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` markers, no empty stub returns, and no hardcoded-empty-data patterns found in any file modified by this phase (`ResourceController.java`, `Honorario.java`, `financeiro.ts`, `financeiro/page.tsx`, `financeiro/[id]/page.tsx`, the migration script).

### Human Verification Required

See YAML frontmatter `human_verification` for full detail. Summary:

1. **Live formalize → financeiro render check** — confirm `/financeiro` and `/financeiro/{id}` actually render "A confirmar" instead of crashing after a real `formalizar` call, in a running browser.
2. **Live double-formalize duplicate check** — confirm the 409/idempotency behavior and zero-duplicate SQL detection query against a live DB.
3. **Live concurrency race check** — fire two genuinely simultaneous `formalizar` requests and confirm the DB unique constraint + exception-catch actually prevents a duplicate row at runtime (not just in code).
4. **Prod migration dry-run** — apply `82-add-honorario-processo-unique-constraint.sql` against a `ddl-auto=validate`-style schema and confirm it applies cleanly.

All four items are blocked by the same environment constraint already documented and accepted as non-blocking in Phase 80/81 verifications: the local dev database contains real project data, the default admin credentials (`admin@lexcv.cv`/`admin123`) return 401, and further password attempts risk an account lockout, so no executor or verifier in this session could complete an authenticated HTTP round-trip.

### Gaps Summary

No code-level gaps. All 4 phase must-have truths, both post-review fixes (CR-01, WR-01), `mvn package`, and `tsc --noEmit` are independently confirmed against the current code on disk — not merely trusted from SUMMARY.md or REVIEW.md narration. The only outstanding items are live-server behavioral confirmations that no agent in this environment can currently perform (same constraint as Phases 80 and 81), routed to human verification rather than treated as failures.

---

_Verified: 2026-07-07T22:40:00Z_
_Verifier: Claude (gsd-verifier)_
