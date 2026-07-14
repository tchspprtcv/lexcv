---
phase: LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo
fixed_at: 2026-07-14T00:00:00Z
review_path: .planning/phases/LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo/95-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase LEXCV-95: Code Review Fix Report

**Fixed at:** 2026-07-14T00:00:00Z
**Source review:** .planning/phases/LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo/95-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: uploadDocumento's cliente-only branch still hand-rolls team resolution that resolverEquipaCliente exists to consolidate

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** e513e5f
**Applied fix:** Replaced the hand-rolled inline `clienteAdvogadoRepository.findByClienteIdAndTenantId` / `clienteAdministrativoRepository.findByClienteIdAndTenantId` calls (appending into a plain `ArrayList`) in `uploadDocumento`'s cliente-only branch with a single call to `notificacaoService.resolverEquipaCliente(tenantId, saved.getClienteId())`, matching the pattern already used a few lines above in the processo branch. Verified the code context matched the review exactly (lines 2780-2785) before editing. Confirmed with `mvn -o compile` that the change compiles; `clienteAdvogadoRepository`/`clienteAdministrativoRepository` fields remain used elsewhere in the controller so no unused-field issue was introduced. Recipient set is behaviorally equivalent (same two repository queries, same dedup via `LinkedHashSet` inside `resolverEquipaCliente`/`criarComFanOutAdmin`), so this closes the "second inline team-resolution" drift risk (Pitfall 3) without changing observable notification behavior.

### WR-02: New uploadDocumento team-notification wiring has no automated test coverage

**Files modified:** `backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java` (new file)
**Commit:** 690f55d
**Applied fix:** Added a new test class covering `uploadDocumento`'s processo-branch `dests` assembly (`resolverEquipaCliente(tenantId, proc.getClienteId())` + `proc.getResponsavelId()` fed into `notificarDocumentoNovo`). Checked this codebase's actual test conventions before choosing an approach: there is no MockMvc/`@SpringBootTest` harness anywhere in the repo (`NotificacaoServiceTest`, `AlertasDiariosJobTest`, `RiscoPrazoServiceTest` all instantiate the class under test directly with Mockito-mocked collaborators and call the method under test as a plain Java call). Followed that established pattern: `ResourceController` is instantiated directly (26-arg `@RequiredArgsConstructor`, all but `NotificacaoService` mocked) with a REAL `NotificacaoService` wired from mocked repositories (mirroring `AlertasDiariosJobTest`'s "collaborator with no further collaborators is used real" convention), a mocked `MultipartFile`, and a manually-populated `SecurityContextHolder`/`UserPrincipal` (cleared in `@AfterEach`). The test creates a processo whose cliente has both a `ClienteAdvogado` and a `ClienteAdministrativo` link, uploads a document against that `processoId`, and asserts via `NotificacaoRepository.inserirSeNaoDuplicado` that exactly 3 notification rows are produced for the advogado, administrativo, and responsável — mirroring the assertion depth of `notificarFaseEntrada_equipaDoCliente_todaEquipaMaisResponsavelMaisAdmin` (`NotificacaoServiceTest.java:403`). Verified: `mvn -o test-compile` succeeds, and `mvn -o test -Dtest=ResourceControllerUploadDocumentoTest` passes (1/1), along with `NotificacaoServiceTest` (35/35), `AlertasDiariosJobTest` (9/9), and `RiscoPrazoServiceTest` (15/15) — no regression introduced.

### WR-03: resolverEquipaCliente's declared return type doesn't encode the ordering guarantee its callers depend on

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
**Commit:** 5205398
**Applied fix:** Narrowed `resolverEquipaCliente`'s declared return type from `Set<UUID>` to `LinkedHashSet<UUID>`, matching the concrete type already constructed and returned inside the method body. Chose this option over the Javadoc-only alternative because it makes the documented "advogados antes de administrativos" insertion-order contract compiler-enforced rather than convention-only, per the finding's stated preference. Confirmed via grep that every existing caller/test remains source-compatible: `notificarFaseEntrada`/`notificarProcessoAtribuido` already re-wrap the result in `new LinkedHashSet<>(...)` (works for any `Set` supertype), `NotificacaoServiceTest` declares its local variables as `Set<UUID> equipa = service.resolverEquipaCliente(...)` (valid widening assignment from `LinkedHashSet<UUID>`), and the newly-migrated WR-01 call site wraps the result in `new ArrayList<>(...)`. Verified: `mvn -o test-compile` succeeds and `mvn -o test -Dtest=NotificacaoServiceTest` passes 35/35 with no regression.

## Skipped Issues

None — all findings were fixed.

---

_Fixed: 2026-07-14T00:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
