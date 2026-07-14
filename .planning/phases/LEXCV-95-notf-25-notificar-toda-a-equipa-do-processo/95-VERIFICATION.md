---
phase: 95-notf-25-notificar-toda-a-equipa-do-processo
verified: 2026-07-14T15:50:00Z
status: passed
score: 10/10 must-haves verified
overrides_applied: 0
---

# Phase 95: NOTF-25 — Notificar Toda a Equipa do Processo Verification Report

**Phase Goal:** Eventos de processo (entrada de fase, novo documento, atribuição) deixam de notificar apenas o responsável único, passando a alcançar toda a equipa de advogados/administrativos ligada ao cliente do processo.
**Verified:** 2026-07-14T15:50:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

Merged from ROADMAP.md Success Criteria (5) + PLAN frontmatter must_haves (95-01: 5 truths, 95-02: 4 truths), deduplicated.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `resolverEquipaCliente` returns the tenant-scoped union of advogados+administrativos, empty on null clienteId | VERIFIED | `NotificacaoService.java:145-157` — `LinkedHashSet` union of `clienteAdvogadoRepository.findByClienteIdAndTenantId` + `clienteAdministrativoRepository.findByClienteIdAndTenantId`, both dual-scoped `(clienteId, tenantId)`; null-guard returns empty set with zero repo calls. Independently ran `mvn test -Dtest=NotificacaoServiceTest` → `resolverEquipaCliente_uniaoAdvogadosEAdministrativos_dedupTenantScoped` and `resolverEquipaCliente_clienteIdNulo_devolveVazio` both pass (35/35 total, surefire report confirms 0 failures/errors). |
| 2 | Fase entrada / novo documento / atribuição reach the whole client team, not just responsavelId (ROADMAP SC1) | VERIFIED | `notificarFaseEntrada` (line 263-274) builds `primarios = resolverEquipaCliente(...) ∪ responsavelId`. `uploadDocumento` processo branch (`ResourceController.java:2764-2772`) builds `dests` from `resolverEquipaCliente(tenantId, proc.getClienteId())` + responsavel. `notificarProcessoAtribuido` (line 282-302) builds `equipa = resolverEquipaCliente(...) \ responsavelId` as `destinatariosSecundarios`. Test `notificarFaseEntrada_equipaDoCliente_todaEquipaMaisResponsavelMaisAdmin` asserts 4 distinct rows (advogado + administrativo + responsavel + admin), passing. |
| 3 | Responsável recebe 2ª pessoa, resto da equipa recebe 3ª pessoa informativa (ROADMAP SC2) | VERIFIED | `notificarProcessoAtribuido` calls the 11-arg `criarComFanOutAdmin` with `List.of(responsavelId)` as primários (→ `mensagemDest` = "Foi-lhe atribuído...") and `equipa` (team minus responsavel) as secundários (→ `mensagemInformativo` = "O processo ... foi atribuído a um novo responsável."). Test `notificarProcessoAtribuido_equipa_responsavel2aPessoaEquipa3aPessoa` asserts the responsavel's message starts with "Foi-lhe atribuído" and the team member's/admin's message starts with "O processo" — passes. |
| 4 | Team member who is also ADMIN produces exactly one row, no `uk_notificacao_dedup` collision (ROADMAP SC3, inherits Phase 94) | VERIFIED | 11-arg `criarComFanOutAdmin` merges primários ∪ secundários ∪ ADMIN fan-out into one `LinkedHashSet<UUID> todos` before the write loop (lines 216-243); write loop iterates `todos` once per UUID. Phase 94 collision-regression tests (`notificarFaseEntrada_responsavelTambemAdmin_umaUnicaLinhaSemExcecao`, `notificarProcessoAtribuido_responsavelTambemAdmin_umaUnicaLinha2aPessoaSemExcecao`) were updated to the new signatures and still assert exactly 1 row — pass. |
| 5 | Categoria silenciada continua a não notificar o membro que a silenciou (ROADMAP SC3, inherits Phase 93) | VERIFIED | The silencing guard lives in the unchanged `criar()` choke point (lines 80-86), which every write in the widened fan-out still routes through — no parallel write path was introduced (confirmed by reading the full write loop, single `criar(...)` call site at line 250). |
| 6 | Explicit decision recorded on whether `AlertasDiariosJob` categories get team expansion this milestone (ROADMAP SC4) | VERIFIED | `.planning/PROJECT.md` line 184: "NOTF-25: expansão de fan-out de equipa cobre apenas os 3 gatilhos de evento...; `AlertasDiariosJob` (PRAZO_*, EVENTO_*, HONORARIO_ATRASADO) mantém-se responsavelId-only" — well-formed 3-column row, "Deferred (job team-expansion)". |
| 7 | Notificação de parecer atribuído permanece individual, salvo decisão em contrário (ROADMAP SC5) | VERIFIED | `.planning/PROJECT.md` line 185: "NOTF-25: PARECER_ATRIBUIDO mantém-se individual..., NÃO alarga à equipa do cliente" — "By design". Corroborated by code: `notificarParecerAtribuido` (line 322-330) unchanged, still calls the 10-arg `criarComFanOutAdmin` overload with `advogadoId`-only primário. |
| 8 | `AlertasDiariosJob.java` is completely unmodified by this phase | VERIFIED | `git diff <last-pre-phase-95-commit> HEAD -- backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` returns empty. None of the 4 phase-95 commits (`c0622d3`, `ae2f5a8`, `77412e6`, `e4198c5`) touch this file (confirmed via `git show --stat` on each). |
| 9 | `notificarParecerAtribuido` remains individual-assignment only, unchanged behavior | VERIFIED | Method body byte-for-byte identical (grep + read confirms 5-arg signature, single `advogadoId` primário, 10-arg helper call). `ParecerController.java` (last touched Phase 87, commit `dd5c1a1` — untouched by phase 95) still calls it with the original 5-arg form at lines 181-182 and 337-338. Phase 94 test `notificarParecerAtribuido_advogadoTambemAdmin_umaUnicaLinha2aPessoaSemExcecao` unchanged and passing. |
| 10 | The team-resolution query is genuinely tenant-scoped (not a blind join) | VERIFIED | `ClienteAdvogadoRepository.findByClienteIdAndTenantId(UUID clienteId, UUID tenantId)` and `ClienteAdministrativoRepository.findByClienteIdAndTenantId(...)` are Spring Data derived queries compiling to `WHERE cliente_id = ? AND tenant_id = ?` at the SQL level — tenant_id is a real predicate on the junction row, not appended post-hoc. Test explicitly verifies the `(clienteId, TENANT_ID)` argument pair was used (`verify(clienteAdvogadoRepository, times(1)).findByClienteIdAndTenantId(clienteId, TENANT_ID)`). |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | `resolverEquipaCliente` helper + 11-arg `criarComFanOutAdmin` overload + team-expanded triggers | VERIFIED | All present, substantive (real logic, not stubs), compiles, exercised by tests. |
| `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` | Mockito proof of team fan-out + resolution helper | VERIFIED | 35/35 tests pass (31 pre-existing regression-checked + 4 new), independently run via `mvn test -Dtest=NotificacaoServiceTest`. |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | 4 call sites wired for team fan-out | VERIFIED | `createProcesso` (1085), `atribuirResponsavel` (1150), `createProcessoFase` (1847) pass `getClienteId()`; `uploadDocumento` processo branch (2764-2772) calls `resolverEquipaCliente`. Cliente branch (2780-2793) confirmed byte-for-byte unchanged via `git show 77412e6`. |
| `.planning/PROJECT.md` | Recorded NOTF-25 scope decisions | VERIFIED | Two well-formed rows at lines 184-185 (`AlertasDiariosJob` deferred; `PARECER_ATRIBUIDO` by-design individual). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `notificarFaseEntrada` / `notificarProcessoAtribuido` | `criarComFanOutAdmin` | single deduplicated creation path | WIRED | Both call the (10-arg or 11-arg) `criarComFanOutAdmin` — no parallel write path found; single `criar(...)` call site inside the shared write loop. |
| `resolverEquipaCliente` | `ClienteAdvogadoRepository` + `ClienteAdministrativoRepository` | `findByClienteIdAndTenantId` (dual-scoped) | WIRED | Confirmed by source read + Mockito `verify(...)` assertion in tests. |
| `ResourceController.uploadDocumento` (processo branch) | `NotificacaoService.resolverEquipaCliente` | shared team-resolution helper | WIRED | Line 2768: `dests.addAll(notificacaoService.resolverEquipaCliente(tenantId, proc.getClienteId()))` — no second inline junction lookup introduced for this branch. |
| `ResourceController` triggers | `notificarFaseEntrada` / `notificarProcessoAtribuido` | `getClienteId()` argument | WIRED | All 3 relevant call sites pass `saved.getClienteId()` / `processo.getClienteId()` as the 3rd argument, matching the new signatures exactly. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `NotificacaoServiceTest` suite green | `mvn -f backend/pom.xml test -Dtest=NotificacaoServiceTest` (run independently by verifier) | `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0` (surefire report) | PASS |
| Full backend suite green (SUMMARY claims 59/59) | `mvn -f backend/pom.xml test` (run independently by verifier) | `AlertasDiariosJobTest: 9/9`, `NotificacaoServiceTest: 35/35`, `RiscoPrazoServiceTest: 15/15` = 59/59, 0 failures, 0 errors | PASS |
| Backend compiles with wired call sites | `mvn -f backend/pom.xml -DskipTests package` (run independently by verifier) | Exit 0, no compile errors | PASS |
| `AlertasDiariosJob.java` untouched by phase 95 | `git diff <pre-phase-95> HEAD -- .../AlertasDiariosJob.java` | Empty diff | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| NOTF-25 | 95-01, 95-02 | Notificações de eventos do processo chegam a toda a equipa, não só ao responsável único | SATISFIED | All 5 ROADMAP success criteria verified above; both plans declare `requirements: [NOTF-25]` and `requirements-completed: [NOTF-25]` in SUMMARY frontmatter. |

No orphaned requirements — `.planning/REQUIREMENTS.md` Traceability table maps NOTF-25 solely to Phase 95, and both plans in this phase declare exactly that ID. Note: `.planning/REQUIREMENTS.md`'s own checkbox/Traceability-status for NOTF-25 still shows unchecked/"Pending" — this is expected to be updated by the orchestrator's separate `docs(phase-95): complete phase execution` step (the same pattern observed for Phase 93/94, where their REQUIREMENTS.md status flips occur in a dedicated "complete phase execution" commit, not inside the plan's own task commits), not a gap in this phase's own deliverables.

### Anti-Patterns Found

None. Scanned `NotificacaoService.java` and `ResourceController.java` (the two files modified by this phase's production code) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER`, `console.log`-equivalent (`System.out.println`), and empty-return stubs. The only regex hit was the Portuguese word "TODOS" (= "all") inside a comment, a false positive, not a debt marker.

### Human Verification Required

None. This is a pure backend service/controller-wiring phase proven end-to-end by Mockito unit tests at the service layer and grep/compile verification at the controller layer. Live/E2E notification delivery is blocked by the pre-existing `MINIO_ENDPOINT` environmental issue (tracked separately as AUD-04, Phase 97) — not a phase-95 concern, and not required to prove this phase's goal since the fan-out logic itself has no external-service dependency.

### Gaps Summary

No gaps. All 5 ROADMAP success criteria and all 9 PLAN-frontmatter must-have truths are independently verified against the actual codebase (not SUMMARY.md claims): `resolverEquipaCliente` is genuinely tenant-scoped and dedup-safe; `notificarFaseEntrada`/`notificarProcessoAtribuido` genuinely reach the full client team with the correct 2nd/3rd-person split; all 4 ResourceController call sites are correctly wired; `AlertasDiariosJob.java` has a verified-empty diff across the entire phase; `notificarParecerAtribuido` is verified byte-for-byte unchanged in both source and its only caller (`ParecerController.java`, untouched since Phase 87); the full 59/59 backend test suite was re-run independently by the verifier (not trusted from SUMMARY.md) and passes; the backend compiles cleanly.

---

_Verified: 2026-07-14T15:50:00Z_
_Verifier: Claude (gsd-verifier)_
