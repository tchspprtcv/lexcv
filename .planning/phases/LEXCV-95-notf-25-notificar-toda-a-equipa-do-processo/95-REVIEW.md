---
phase: LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo
reviewed: 2026-07-14T16:13:12Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
status: issues_found
---

# Phase LEXCV-95: Code Review Report

**Reviewed:** 2026-07-14T16:13:12Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

This is a re-review of the NOTF-25 "notify the entire process team" phase after the iteration-1 fix pass (commits `e513e5f`, `5205398`, `690f55d`) closed the three Warnings from the prior review (`95-REVIEW.md` / `95-REVIEW-FIX.md`): the `uploadDocumento` cliente-only branch now delegates to `resolverEquipaCliente` instead of hand-rolling the query, `resolverEquipaCliente`'s declared return type was narrowed to `LinkedHashSet<UUID>` to make the documented insertion-order contract compiler-enforced, and a new controller-level test (`ResourceControllerUploadDocumentoTest`) was added.

Verified all three fixes against the actual diff (`git diff 7fd8bb0..690f55d`) rather than trusting the fix report's prose: the cliente-only branch (`ResourceController.java:2781`) now reads `new ArrayList<>(notificacaoService.resolverEquipaCliente(tenantId, saved.getClienteId()))`, `resolverEquipaCliente`'s signature is `public LinkedHashSet<UUID> resolverEquipaCliente(...)`, and the new test exists and exercises the processo-branch dedup/team-assembly path. Compiled the backend offline and ran both `NotificacaoServiceTest` (35/35 passing) and `ResourceControllerUploadDocumentoTest` (1/1 passing) — no regression.

Re-traced the full NOTF-25 surface against `95-CONTEXT.md`'s decisions: `notificarFaseEntrada` and `notificarProcessoAtribuido` (via the `criarComFanOutAdmin` 11-arg overload) now include the client's team, `notificarParecerAtribuido` is confirmed untouched (still the individual-only 10-arg call, matching the explicit "PARECER_ATRIBUIDO mantém-se individual" decision), and no other call site of `notificarDocumentoNovo`/`notificarFaseEntrada`/`notificarProcessoAtribuido` was missed. Tenant scoping in `resolverEquipaCliente` remains enforced at the join-table level (`(clienteId, tenantId)` pair) on every call path checked.

No Critical/Blocker-level defect was found in this iteration. Two residual issues remain, both introduced or left behind by the iteration-1 fix pass itself: the WR-01 fix migrated the `uploadDocumento` cliente-only branch's logic but did not extend the new WR-02 test to cover it, leaving that branch's controller-level wiring untested; and the WR-03 fix (narrowing the return type away from `Set<UUID>`) left a now-dead `import java.util.Set;` behind in `NotificacaoService.java`.

## Warnings

### WR-01: uploadDocumento's cliente-only branch (migrated by the WR-01 fix) still has no controller-level test coverage

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2780-2789`
**Issue:** The prior review's WR-01 fix (commit `e513e5f`) correctly migrated the cliente-only branch of `uploadDocumento` from a hand-rolled inline query to `notificacaoService.resolverEquipaCliente(tenantId, saved.getClienteId())`, changing the actual code path exercised when a document is uploaded with `clienteId` but no `processoId`. The prior review's WR-02 fix (commit `690f55d`) then added `ResourceControllerUploadDocumentoTest`, but that new test class contains exactly one test method, `uploadDocumento_comProcessoId_notificaEquipaDoClienteMaisResponsavel`, which only exercises the **processo** branch (`saved.getProcessoId() != null`, lines 2764-2779). The cliente-only branch that WR-01 itself modified (lines 2780-2789) is still not exercised by any controller-level test — it is only indirectly covered by `NotificacaoServiceTest`'s isolated tests of `resolverEquipaCliente` and `criarComFanOutAdmin`, which do not prove the controller correctly wires `saved.getClienteId()` into the call (e.g., a future edit that accidentally passes `processoId`'s cliente instead of `saved.getClienteId()`, or drops the `dests` assembly entirely, would not be caught). This is the same class of gap the original WR-02 finding described, just for the sibling branch that a fix commit modified without matching test coverage.
**Fix:** Add a second `@Test` method to `ResourceControllerUploadDocumentoTest` mirroring the existing one but calling `controller.uploadDocumento(file, null, CLIENTE_ID, null, null, null)` (no `processoId`), asserting that `notificacaoRepository.inserirSeNaoDuplicado` is invoked once per team member (advogado + administrativo) resolved via `resolverEquipaCliente(tenantId, CLIENTE_ID)`, e.g.:
```java
@Test
void uploadDocumento_comClienteIdSemProcesso_notificaEquipaDoCliente() throws Exception {
    // ... same auth/file/storage stubbing as the existing test ...
    when(clienteAdvogadoRepository.findByClienteIdAndTenantId(CLIENTE_ID, TENANT_ID))
            .thenReturn(List.of(ClienteAdvogado.builder()
                    .clienteId(CLIENTE_ID).tenantId(TENANT_ID).userId(ADVOGADO_EQUIPA).build()));
    when(clienteAdministrativoRepository.findByClienteIdAndTenantId(CLIENTE_ID, TENANT_ID))
            .thenReturn(List.of(ClienteAdministrativo.builder()
                    .clienteId(CLIENTE_ID).tenantId(TENANT_ID).userId(ADMINISTRATIVO_EQUIPA).build()));
    // ... userRepository.findById stubs for both team members, no processoRepository stub needed ...

    ResponseEntity<?> response = controller.uploadDocumento(file, null, CLIENTE_ID, null, null, null);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    verify(notificacaoRepository, times(2)).inserirSeNaoDuplicado(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
}
```

## Info

### IN-01: Unused `import java.util.Set;` left behind by the WR-03 fix

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:24`
**Issue:** The original NOTF-25 implementation declared `resolverEquipaCliente` as returning `Set<UUID>`, which needed `import java.util.Set;`. The WR-03 fix (commit `5205398`) narrowed the declared return type to `LinkedHashSet<UUID>` to make the ordering guarantee compiler-checked, but did not remove the now-unused `import java.util.Set;` at line 24. Grepping the file confirms `Set` no longer appears in any executable code — only in this import and in three unrelated Portuguese comments ("Set deduplica por construção..."). This doesn't break the build (Java only warns, doesn't error, on unused imports, and this project has no checkstyle/unused-import enforcement), but it's dead code that a linter or a future `mvn -o compile -Xlint` pass would flag.
**Fix:**
```diff
 import java.util.LinkedHashSet;
 import java.util.List;
 import java.util.Optional;
-import java.util.Set;
 import java.util.UUID;
```

---

_Reviewed: 2026-07-14T16:13:12Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
