---
phase: LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo
fixed_at: 2026-07-14T16:20:46Z
review_path: .planning/phases/LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo/95-REVIEW.md
iteration: 2
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase LEXCV-95: Code Review Fix Report

**Fixed at:** 2026-07-14T16:20:46Z
**Source review:** .planning/phases/LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo/95-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 1
- Fixed: 1
- Skipped: 0

Scope is `critical_warning` (Critical/Blocker + Warning findings only). This iteration's REVIEW.md reported 0 Critical, 1 Warning (WR-01), 1 Info (IN-01) — only WR-01 was in scope. The Info finding (IN-01) was out of scope but explicitly permitted as an optional trivial cleanup by the orchestrator prompt, so it was also fixed and committed separately (see "Bonus fix" below); it is excluded from the `findings_in_scope`/`fixed` counts above since it was never in scope for this run.

## Fixed Issues

### WR-01: uploadDocumento's cliente-only branch (migrated by the WR-01 fix) still has no controller-level test coverage

**Files modified:** `backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java`
**Commit:** 693d8b9
**Applied fix:** Read the actual controller code at `ResourceController.java:2780-2789` and confirmed it matched the review's description exactly: the cliente-only branch (`saved.getProcessoId() == null && saved.getClienteId() != null`) delegates to `notificacaoService.resolverEquipaCliente(tenantId, saved.getClienteId())`, and `ResourceControllerUploadDocumentoTest` had exactly one test (`uploadDocumento_comProcessoId_notificaEquipaDoClienteMaisResponsavel`), covering only the processo branch. Added a second `@Test` method, `uploadDocumento_comClienteIdSemProcesso_notificaEquipaDoCliente`, mirroring the existing test's Mockito-direct-instantiation style (no MockMvc/`@SpringBootTest` harness exists in this codebase) but calling `controller.uploadDocumento(file, null, CLIENTE_ID, null, null, null)` (no `processoId`). Adapted the fix suggestion in one way the review's snippet omitted: since `clienteId != null` triggers the controller's own tenant-ownership guard (`clienteRepository.findById(clienteId)` + tenant check, lines 2686-2692) — which the review's example didn't stub — added `when(clienteRepository.findById(CLIENTE_ID))...` returning a `Cliente` with matching `tenantId`, otherwise the call would 400 before ever reaching the notification-wiring code under test. Also stubbed `userRepository.findById(...)` for both team members and `userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")` (empty), matching the existing test — these are required because `NotificacaoService.criar()` looks up each destinatario via `userRepository.findById` before persisting, and an unstubbed lookup would silently swallow the notification (caught internally as `IllegalArgumentException`) rather than fail loudly. Used an `ArgumentCaptor<UUID>` (matching the existing test's assertion depth) rather than the review's plain `times(2)` count, asserting the exact destinatario set (`{ADVOGADO_EQUIPA, ADMINISTRATIVO_EQUIPA}`) is produced. Verified: `mvn -o test -Dtest=ResourceControllerUploadDocumentoTest` passes 2/2 (both the pre-existing processo-branch test and the new cliente-only-branch test), and `mvn -o test -Dtest=NotificacaoServiceTest` passes 35/35 — no regression.

## Bonus fix (out of scope, explicitly permitted)

### IN-01: Unused `import java.util.Set;` left behind by the WR-03 fix

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
**Commit:** b19e818
**Applied fix:** Confirmed via grep that `Set` (as a type, not inside comments) no longer appears anywhere in `NotificacaoService.java` — `resolverEquipaCliente`'s return type was already narrowed to `LinkedHashSet<UUID>` by the prior WR-03 fix. Removed the now-dead `import java.util.Set;` line. Verified: `mvn -o test -Dtest=NotificacaoServiceTest` passes 35/35 (no compile error, no behavior change) — this was a pure import cleanup with no executable-code impact.

## Skipped Issues

None — the one in-scope finding (WR-01) was fixed.

---

_Fixed: 2026-07-14T16:20:46Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
