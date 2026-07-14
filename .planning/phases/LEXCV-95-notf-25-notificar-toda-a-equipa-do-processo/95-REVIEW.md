---
phase: LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo
reviewed: 2026-07-14T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
findings:
  critical: 0
  warning: 3
  info: 0
  total: 3
status: issues_found
---

# Phase LEXCV-95: Code Review Report

**Reviewed:** 2026-07-14T00:00:00Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Reviewed the NOTF-25 "notify the entire process team" expansion: `NotificacaoService.resolverEquipaCliente` (new), the `criarComFanOutAdmin` 11-arg overload with a `destinatariosSecundarios` tier (new), the corresponding rewiring of `notificarFaseEntrada`/`notificarProcessoAtribuido` to accept `clienteId`, and the three `ResourceController` call sites that were updated (`createProcesso`, `atribuirResponsavel`, `createProcessoFase`, and the `uploadDocumento` processo branch).

Traced the merge/dedup logic in `criarComFanOutAdmin` (primários ∪ secundários ∪ ADMIN fan-out, all folded into one `LinkedHashSet` before the write loop) against all three call sites and confirmed: no double-write is possible when a user is simultaneously primary/secondary/ADMIN (`Set` dedup), `equipa.remove(responsavelId)` in `notificarProcessoAtribuido` correctly prevents the newly-assigned responsible from receiving the "informativo" 3rd-person message instead of the 2nd-person one, tenant scoping in `resolverEquipaCliente` is enforced at the join-table level (`(clienteId, tenantId)` pair, never `clienteId` alone), and `excluirUserId` filtering is applied uniformly across primary/secondary/admin tiers. Compiled the backend and ran `NotificacaoServiceTest` (35/35 passing) to confirm no regression at the unit level.

No Critical/Blocker-level defect was found — no injection, no tenant-isolation bypass, no null-pointer crash, no observable double-notification. The three findings below are Warnings: two are quality/maintainability gaps (an unmigrated duplicate of the exact logic `resolverEquipaCliente` was introduced to consolidate, and a wiring change with no automated test coverage), and one is an API-contract fragility (an ordering guarantee relied upon by callers/tests that the declared return type does not encode).

## Warnings

### WR-01: uploadDocumento's cliente-only branch still hand-rolls team resolution that resolverEquipaCliente exists to consolidate

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2780-2785`
**Issue:** `NotificacaoService.resolverEquipaCliente` (`backend/src/main/java/com/lexcv/services/NotificacaoService.java:135-157`) is documented, in the method's own comment block, as the single source of truth for this exact query: "não deve existir uma segunda resolução de equipa inline noutro sítio (Pitfall 3)". Plan 95-02 rewired the `uploadDocumento` *processo* branch (line 2768) through the new helper, but the sibling *cliente-only* branch (no `processoId`, only `clienteId`) a few lines below was left as its original inline implementation, calling `clienteAdvogadoRepository.findByClienteIdAndTenantId` / `clienteAdministrativoRepository.findByClienteIdAndTenantId` directly and appending into a plain `ArrayList` (lines 2781-2785) instead of delegating to `resolverEquipaCliente(tenantId, saved.getClienteId())`. Functionally the two implementations currently compute the same set (the raw `ArrayList` can contain a transient duplicate if a user is both `ClienteAdvogado` and `ClienteAdministrativo` for the same cliente, but that's absorbed by the `LinkedHashSet` dedup inside `criarComFanOutAdmin`, so there's no user-visible bug today). The risk is drift: any future change to team-resolution semantics (a third link type, an `ativo`/deactivated-user filter analogous to the ADMIN fan-out's `AndAtivoTrue`, an exclusion rule, etc.) only needs to touch `resolverEquipaCliente` to be correct everywhere — except here, where it will silently continue using the stale logic.
**Fix:**
```java
} else if (saved.getClienteId() != null) {
    List<UUID> dests = new ArrayList<>(notificacaoService.resolverEquipaCliente(tenantId, saved.getClienteId()));
    try {
        notificacaoService.notificarDocumentoNovo(tenantId, saved.getId().toString(), dests,
                saved.getNome(), "/clientes/" + saved.getClienteId(), atorId);
    } catch (IllegalArgumentException ex) {
        log.warn("DOCUMENTO_NOVO: falha ao notificar (destinatário possivelmente órfão) documento={}",
                saved.getId(), ex);
    }
}
```

### WR-02: New uploadDocumento team-notification wiring has no automated test coverage

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2764-2775`
**Issue:** Plan 95-02's actual code change — building `dests` from `resolverEquipaCliente(tenantId, proc.getClienteId())` plus `proc.getResponsavelId()` and passing it into `notificarDocumentoNovo` — lives entirely in `ResourceController`, but there is no controller/integration-level test that exercises `POST /api/v1/documentos/upload` with a `processoId` and asserts the resulting notification set includes the client's team. `NotificacaoServiceTest` thoroughly covers `resolverEquipaCliente` and `criarComFanOutAdmin` in isolation (mocked repositories), and `notificarDocumentoNovo` itself is tested with a hand-built `destinatarios` collection — but the integration point that actually assembles that collection from `proc.getClienteId()` in the controller is untested. A regression here (e.g., someone "simplifies" the `dests.addAll(...)` call, or an NPE guard around `proc` gets removed) would not be caught by the existing suite.
**Fix:** Add a `ResourceController`-level (MockMvc or `@SpringBootTest`) test for `uploadDocumento` that creates a processo with a clienteId that has both a `ClienteAdvogado` and a `ClienteAdministrativo` link, uploads a document against that `processoId`, and asserts (via `NotificacaoRepository`) that notification rows exist for both linked users plus the responsável — mirroring the pattern already used for `notificarFaseEntrada_equipaDoCliente_todaEquipaMaisResponsavelMaisAdmin` in `NotificacaoServiceTest.java:403`.

### WR-03: resolverEquipaCliente's declared return type doesn't encode the ordering guarantee its callers depend on

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:145`
**Issue:** The method is documented as returning "a união (deduplicada, ordem de inserção preservada: advogados antes de administrativos)" and is declared `public Set<UUID> resolverEquipaCliente(...)`. The plain `java.util.Set` interface makes no ordering guarantee — the deterministic advogados-before-administrativos order that the comment promises, that `notificarFaseEntrada`/`notificarProcessoAtribuido` re-wrap into `LinkedHashSet`s to preserve, and that `NotificacaoServiceTest` (e.g. `notificarFaseEntrada_equipaDoCliente_todaEquipaMaisResponsavelMaisAdmin`, line 403) asserts on, is only true because of the concrete implementation type (`LinkedHashSet`) chosen inside the method body. A future maintainer refactoring the method internals (e.g. to a `HashSet`, or to a stream `.collect(Collectors.toSet())`) would still satisfy the compiler and the current unit tests could start failing nondeterministically (order-dependent assertions on a now-unordered `Set`) without any signal at the type level that the contract was broken.
**Fix:** Either narrow the declared return type to `LinkedHashSet<UUID>` (making the ordering contract explicit and compiler-checked), or explicitly document on the public signature (Javadoc, not just an inline comment) that callers must not rely on iteration order and adjust the test assertions to be order-independent (`assertThat(...).containsExactlyInAnyOrder(...)`) if determinism was never actually meant to be a supported contract.

---

_Reviewed: 2026-07-14T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
