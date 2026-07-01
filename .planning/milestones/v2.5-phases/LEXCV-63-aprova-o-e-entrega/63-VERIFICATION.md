---
phase: 63-aprova-o-e-entrega
verified: 2026-06-30T00:00:00Z
status: passed
score: 6/6 must-haves verified
overrides_applied: 0
---

# Phase 63: Aprovação e Entrega — Verification Report

**Phase Goal:** O parecer pode ser revisto internamente antes de ser entregue, e uma vez entregue fica disponível para consulta pela equipa/cliente
**Verified:** 2026-06-30T00:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | ADMIN (pareceres:manage) pode marcar uma versão específica como aprovada internamente | VERIFIED | `ParecerController.java:235-237` `@PreAuthorize("hasAuthority('pareceres:manage')")` + `@PutMapping("/{id}/versoes/{versaoId}/aprovar")`. `pareceres:manage` seeded only for ADMIN (`DatabaseSeeder.java:312` — full permissionMap; not granted to ADVOGADO/TECNICO/ASSISTENTE at lines 314-348). |
| 2 | Aprovar uma versão de uma solicitação PENDENTE/EM_ELABORACAO transiciona o status para EM_REVISAO | VERIFIED | `ParecerController.java:262-265`: `if ("PENDENTE".equals(...) || "EM_ELABORACAO".equals(...)) { solicitacao.setStatus("EM_REVISAO"); ...save(...); }` |
| 3 | Advogado responsável ou ADMIN pode marcar uma versão como final e entregar, mudando o status para CONCLUIDO | VERIFIED | `entregarSolicitacao` (`ParecerController.java:270-303`): `@PreAuthorize("hasAuthority('pareceres:edit')")` (held by ADVOGADO and ADMIN per seeder lines 333-348/312) + in-method `isAdmin`/`isResponsavel` check (lines 291-297), 403 otherwise. Sets `status="CONCLUIDO"` at line 300. Note: PLAN text specified `pareceres:manage` for this endpoint; code review (CR-01) caught that this would make the in-method advogado-responsável check unreachable (ADVOGADO lacks `pareceres:manage`) and the fix changed it to `pareceres:edit`, which ADVOGADO does hold — this is the correct implementation of the truth, documented in 63-REVIEW.md and 63-REVIEW-FIX.md. |
| 4 | Solicitação concluída regista a versaoFinalId apontando para a versão entregue | VERIFIED | `ParecerSolicitacao.java:36-37` field `versaoFinalId`; `entregarSolicitacao` line 299 `solicitacao.setVersaoFinalId(versaoFinalId)` where `versaoFinalId` is the `@RequestParam UUID` validated against `versao.getSolicitacaoId().equals(id)` (lines 284-287) before use. |
| 5 | Qualquer escrita (aprovar/entregar) sobre uma solicitação já CONCLUIDO é rejeitada | VERIFIED | Both methods guard immediately after tenant-scoped lookup: `aprovarVersao` lines 244-247, `entregarSolicitacao` lines 279-282, both return 400. Guard ordering fixed per WR-01 (runs before version-ownership lookup, confirmed in 63-REVIEW-FIX.md and present in current code). |
| 6 | Parecer entregue permanece consultável/descarregável via os endpoints GET existentes das Fases 61/62 | VERIFIED | No new GET endpoint added; existing `GET /{id}` (line 153), `GET /{solicitacaoId}/versoes` (line 306), `GET /{solicitacaoId}/versoes/{versaoId}` (line 317), `GET /{solicitacaoId}/versoes/{versaoId}/anexo` (line 405) unchanged and unaffected by the CONCLUIDO guards (guards only apply to the two new PUT endpoints). |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/ParecerVersao.java` | Campos aprovado/aprovadoPorId/aprovadoEm | VERIFIED | Lines 42-50: `Boolean aprovado` (`@Builder.Default = false`), `UUID aprovadoPorId` (`@Column(name = "aprovado_por_id")`), `LocalDateTime aprovadoEm` (`@Column(name = "aprovado_em")`). `onCreate()` (lines 52-55) unchanged, does not set `aprovadoEm`. |
| `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` | Campo versaoFinalId | VERIFIED | Lines 36-37: `@Column(name = "versao_final_id") private UUID versaoFinalId;`, placed after `advogadoId` per plan. |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` | Endpoints /aprovar e /entregar | VERIFIED | `aprovarVersao` (line 237) and `entregarSolicitacao` (line 272) both present and substantive (full guard logic, no stubs). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `ParecerController.aprovarVersao` | `ParecerVersao.setAprovado/setAprovadoPorId/setAprovadoEm` | `parecerVersaoRepository.save` | WIRED | Lines 257-260: all three setters called, then `parecerVersaoRepository.save(versao)`. |
| `ParecerController.entregarSolicitacao` | `ParecerSolicitacao.setVersaoFinalId/setStatus` | `parecerSolicitacaoRepository.save` | WIRED | Lines 299-302: both setters called, then `ResponseEntity.ok(parecerSolicitacaoRepository.save(solicitacao))`. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with new fields/endpoints | `mvn -DskipTests compile -q` | No output (BUILD SUCCESS) | PASS |
| `pareceres:edit` granted to ADVOGADO (enables CR-01 fix to actually work) | grep DatabaseSeeder.java lines 333-348 | `pareceres:edit` present in ADVOGADO's permission list | PASS |
| `pareceres:manage` ADMIN-only (enables PARC-07 restriction) | grep DatabaseSeeder.java roles | Only `upsertRolePermissions("ADMIN", permissionMap.values())` includes full map; ASSISTENTE/TECNICO/ADVOGADO lists at lines 314-348 omit `pareceres:manage` | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PARC-07 | 63-01-PLAN.md | Supervisor/ADMIN pode marcar uma versão como aprovada internamente | SATISFIED | Truth #1, #2 above |
| PARC-08 | 63-01-PLAN.md | Utilizador pode marcar a versão final como entregue, concluindo a solicitação | SATISFIED | Truth #3, #4 above |
| PARC-09 | 63-01-PLAN.md | Parecer entregue fica disponível para consulta/download pelo cliente/equipa | SATISFIED | Truth #6 above |

No orphaned requirements — REQUIREMENTS.md maps exactly PARC-07/08/09 to Phase 63, all three appear in 63-01-PLAN.md `requirements:` frontmatter and are addressed in code. (REQUIREMENTS.md traceability table still shows "Pending" status text for all phases including already-verified Phase 61/62 — this is a pre-existing doc-maintenance gap unrelated to Phase 63's code, not a phase-63 regression.)

### Anti-Patterns Found

None. No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER markers in `ParecerController.java`. No empty handlers, no static/stub returns in the two new endpoints.

### Code Review Status

`63-REVIEW.md`: status `clean`, 0 critical, 0 warning, 1 info (informational only, no action needed). Two findings from the initial review (CR-01 elevation-of-privilege/unreachable-authorization bug, WR-01 guard-ordering inconsistency) were fixed per `63-REVIEW-FIX.md` (commits `5478c56`, `abd0f57`) and confirmed resolved in the re-review and in the current source.

### Human Verification Required

None. All truths are verifiable via static code inspection (compile success, RBAC seed data, guard logic) — no UI, real-time, or external-service behavior in this backend-only phase.

### Gaps Summary

No gaps. All 6 derived observable truths verified directly against current source (not SUMMARY.md claims). The one deviation from the PLAN's literal text (`entregarSolicitacao` using `pareceres:edit` instead of the originally specified `pareceres:manage`) is a documented, reviewed, and justified fix that is necessary to make truth #3 actually achievable (PLAN's original spec would have made the in-method advogado-responsável branch unreachable dead code, contradicting PARC-08's "advogado responsável ou ADMIN" intent). This does not require an override — it is the correct realization of the stated must-have, not a reduction in scope.

---

_Verified: 2026-06-30T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
