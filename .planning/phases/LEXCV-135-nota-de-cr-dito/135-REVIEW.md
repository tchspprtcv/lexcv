---
phase: LEXCV-135-nota-de-cr-dito
reviewed: 2026-10-05T12:00:00Z
depth: standard
files_reviewed: 64
files_reviewed_list:
  - backend/migrations/135-add-nota-credito-documento-fiscal.sql
  - backend/migrations/README.md
  - backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java
  - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalResumoResponse.java
  - backend/src/main/java/com/lexcv/dtos/NotaCreditoRequest.java
  - backend/src/main/java/com/lexcv/dtos/NotaCreditoResponse.java
  - backend/src/main/java/com/lexcv/dtos/PagamentoComDocumentoResponse.java
  - backend/src/main/java/com/lexcv/dtos/PreVisualizacaoNotaCreditoResponse.java
  - backend/src/main/java/com/lexcv/models/DocumentoFiscal.java
  - backend/src/main/java/com/lexcv/models/MotivoNotaCredito.java
  - backend/src/main/java/com/lexcv/models/MotivoNotaCreditoConverter.java
  - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalRepository.java
  - backend/src/main/java/com/lexcv/services/RecebidoNoMes.java
  - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
  - backend/src/main/java/com/lexcv/services/fiscal/ComposicaoNotaCredito.java
  - backend/src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java
  - backend/src/main/java/com/lexcv/services/fiscal/NotaCreditoService.java
  - backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java
  - backend/src/main/java/com/lexcv/services/fiscal/ProjetoNotaCredito.java
  - backend/src/main/java/com/lexcv/services/fiscal/ResultadoNotaCredito.java
  - backend/src/main/java/com/lexcv/services/fiscal/TextoDocumentoFiscal.java
  - backend/src/main/java/com/lexcv/services/fiscal/TipoCredito.java
  - backend/src/main/java/com/lexcv/services/fiscal/ValidacaoNotaCredito.java
  - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java
  - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerDashboardKpiTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerDocumentoFiscalGuardasTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerListaPagamentosTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerPagamentoTest.java
  - backend/src/test/java/com/lexcv/dtos/DocumentoFiscalNotaCreditoDtosTest.java
  - backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java
  - backend/src/test/java/com/lexcv/models/MotivoNotaCreditoTest.java
  - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalImutabilidadeTest.java
  - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalRepositoryIT.java
  - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal134IT.java
  - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal135IT.java
  - backend/src/test/java/com/lexcv/services/RecebidoNoMesTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ComposicaoNotaCreditoTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/DocumentoFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java
  - backend/src/test/java/com/lexcv/services/fiscal/NotaCreditoConcorrenciaIT.java
  - backend/src/test/java/com/lexcv/services/fiscal/NotaCreditoServiceIT.java
  - backend/src/test/java/com/lexcv/services/fiscal/NotaCreditoServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceIT.java
  - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ValidacaoNotaCreditoTest.java
  - web/scripts/verify-documentos-fiscais.mjs
  - web/src/app/(dashboard)/financeiro/[id]/pagamentos-card.tsx
  - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/nota-credito-dialog.tsx
  - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
  - web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx
  - web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx
  - web/src/hooks/use-faturacao.ts
  - web/src/lib/erros-emissao.test.ts
  - web/src/lib/erros-emissao.ts
  - web/src/lib/nota-credito-dialogo.test.ts
  - web/src/lib/nota-credito-dialogo.ts
  - web/src/schemas/financeiro.test.ts
  - web/src/schemas/financeiro.ts
  - web/src/types/faturacao.ts
  - web/src/types/financeiro.ts
findings:
  critical: 2
  warning: 4
  info: 6
  total: 12
status: issues_found
---

# Phase 135: Code Review Report

**Reviewed:** 2026-10-05T12:00:00Z
**Depth:** standard
**Files Reviewed:** 64
**Status:** issues_found

## Summary

Scope: Nota de Crédito (NC) backend (composition, emission service, read side, guards, KPI) and
web (dialog, detail, lists, hooks, error mapping).

These areas held up when traced:
- **Money math in `ComposicaoNotaCredito`.** Remainders are tracked per column. `remTotal == remBase + remIva` holds by induction. The clamps cannot drive a column negative or past the FR. A TOTAL NC closes every column to the cent.
- **Cumulative cap.** It is read under the tenant configuration lock, and only `NotaCreditoService` writes NCs.
- **Shared FR/NC idempotency key space.** It is enforced on both sides.
- **Tenant scoping.** Every new finder and route is scoped by tenant.
- **Permissions.** `financeiro:manage` is exact on both layers.
- **CFG-03.** `registarPagamentoLegado` is untouched.
- **Estorno sign.** The sign is consistent in `totalPago`, the conta corrente, `RecebidoNoMes` and the payments list.

Two defects must be fixed before ship:
1. The NC debits the processo's *current* cliente, not the cliente the FR credited. `PUT /processos/{id}` can reassign a processo to another cliente without moving any balance, so the conta corrente ledger becomes wrong.
2. In the web dialog, several retryable emission errors leave the "Emitir nota de crédito" button enabled but doing nothing.

## Critical Issues

### CR-01: NC debits the wrong cliente's conta corrente after a processo is reassigned (not merged)

**File:** `backend/src/main/java/com/lexcv/services/fiscal/NotaCreditoService.java:185-219, 262`
**Issue:** The service takes `clienteId` from `processoRepository.clienteIdPorIdETenant(origem.getProcessoId(), ...)`, the processo's *current* cliente. It then debits that cliente's conta corrente and stamps that id on the NC (`.clienteId(cliente.getId())`). The code comment says merges are the only reason the processo's cliente can change. That is false: `ResourceController.updateProcesso` (`ResourceController.java:1284`, `processo.setClienteId(payload.getClienteId())`, gated only by `processos:edit`) can move a processo with issued FRs to any other cliente in the tenant. It does not move the conta corrente balance and does not repoint fiscal documents.

Sequence: FR of 100 000 issued while processo P belongs to A, so A's saldo goes up by 100 000. P is then edited to belong to B. An NC TOTAL on the FR now:
- debits **B** by 100 000, so B goes negative;
- leaves A with the 100 000 credit;
- writes an NC with `cliente_id = B` but an adquirente snapshot (NIF and name) that is A's.

The NC then shows in B's fiscal-document filter with A's NIF. The "saldo / totalPago coherence" property this phase claims is broken for any reassigned processo.

The merge case is already covered without the processo lookup: `repontarCliente` rewrites `t_documento_fiscal.cliente_id` during a merge, so `origem.getClienteId()` always follows merges.

**Fix:** Debit, and record on the NC, the cliente the FR belongs to. Also refuse to reassign a processo that has fiscal documents:
```java
// NotaCreditoService.emitir, steps 6-8
UUID clienteId = origem.getClienteId(); // follows merges (repontarCliente); immune to updateProcesso
Cliente cliente = bloquear(() -> clienteRepository.bloquearPorIdETenant(clienteId, tenantId))
        .orElseThrow(NotaCreditoService::processoAlterado); // merge raced: retryable 409
// re-read the FR's cliente_id as a scalar under the cliente lock; if it changed (merge committed), 409 retry
Processo processo = bloquear(() -> processoRepository.bloquearPorIdETenant(origem.getProcessoId(), tenantId))
        .orElseThrow(NotaCreditoService::processoAlterado);
// drop the `cliente.getId().equals(processo.getClienteId())` check, or replace it with a check against the FR

// ResourceController.updateProcesso
if (!payload.getClienteId().equals(processo.getClienteId())
        && documentoFiscalService.existeParaProcesso(tenantId, id)) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "message", "Não é possível mudar o cliente de um processo com documentos fiscais emitidos.",
        "code", "PROCESSO_COM_DOCUMENTOS_FISCAIS"));
}
```
Add an IT: FR, then `updateProcesso` to another cliente, then NC. Assert A's saldo returns to its value before the FR and B's saldo is unchanged.

### CR-02: "Emitir nota de crédito" button does nothing after retryable 4xx errors (DATA_EMISSAO_ALTERADA, PROCESSO_ALTERADO_TENTE_NOVAMENTE, other non-field 409/422)

**File:** `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/nota-credito-dialog.tsx:169, 187-188`; `web/src/lib/nota-credito-dialogo.ts:533-539`; `web/src/lib/erros-emissao.ts:225-231`
**Issue:** When emission fails with any 4xx other than 401/403/408/429:
1. `desfechoDefinitivo(e)` is true, so `setTentativa(null)`.
2. `interpretarErroNotaCredito` maps `DATA_EMISSAO_ALTERADA`, `PROCESSO_ALTERADO_TENTE_NOVAMENTE` and any unknown code to `{tipo: "banner", definitivo: false}`.
3. `reagirAErroNotaCredito` therefore keeps `passo = "pre-visualizacao"`.

The user stays on step 2 with the "Emitir nota de crédito" button enabled. The banner says "tente de novo". Clicking the button hits `if (emitindoRef.current || !pedido || !tentativa) return;` and silently does nothing: no request, no feedback. This is the designed retry path for midnight rollover and merge races. The only way out is the undocumented "Voltar e editar" followed by a new preview. The existing tests cover `reagirAErroNotaCredito` and `desfechoDefinitivo` separately, so they do not catch this.
**Fix:** Pick one. (a) After a definitive 4xx, always return to the form: in `reagirAErroNotaCredito`, use `passo: "formulario"` for any banner when the emission outcome was definitive. (b) Regenerate the key in place:
```ts
setTentativa((atual) =>
  desfechoDefinitivo(e)
    ? tentativaParaPedido(null, { documentoOrigemId: documento.id, ...pedido }) // fresh key, same content
    : marcarPorResolver(atual));
```
Also add a guard so the button is never enabled while `!tentativa`.

## Warnings

### WR-01: A TOTAL NC can be emitted for a different amount than the user confirmed

**File:** `backend/src/main/java/com/lexcv/services/fiscal/NotaCreditoService.java:209-211`; `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/nota-credito-dialog.tsx:174`
**Issue:** The preview shows "Total a creditar = X" (the remainder at preview time). The emission request carries only `{tipo: TOTAL, valor: null, ...}`. If another user emits an NC on the same FR between preview and confirm, the TOTAL silently credits the new, smaller remainder. The resulting immutable fiscal document differs from what the user confirmed. A PARCIAL is safer but can also be clamped (base/IVA split) differently after an intervening NC.
**Fix:** Send the previewed total (for example `totalEsperado`, or the `valorCreditavelAntes` seen in the preview) in the emission body. Under the configuration lock, refuse with 409 `NC_VALORES_ALTERADOS` when `projeto.calculo().total()` or `valorCreditavelAntes` differs, and have the UI re-preview. Keep the field out of the idempotency comparison, or include it consistently.

### WR-02: 404 CLIENTE_NAO_ENCONTRADO / HONORARIO_NAO_ENCONTRADO during emission closes the dialog silently

**File:** `web/src/lib/erros-emissao.ts:216`; `web/src/lib/nota-credito-dialogo.ts:542-543`; `backend/src/main/java/com/lexcv/services/fiscal/NotaCreditoService.java:186-204`
**Issue:**
- The frontend treats every 404 as "document not found": the dialog closes and the page is expected to show its not-found state. `apiFetch` suppresses the toast because 404 is in `STATUS_INLINE_NOTA_CREDITO`.
- The backend also returns 404 for `CLIENTE_NAO_ENCONTRADO`. This happens in the merge race: the scalar read returns cliente A, a merge commits and deletes A, then `bloquearPorIdETenant(A)` comes back empty.
- The backend can also return 404 `HONORARIO_NAO_ENCONTRADO`.

In both cases the FR still exists, the refetched page renders normally, and the dialog just vanishes with no message. The merge race should also be the retryable 409 `PROCESSO_ALTERADO_TENTE_NOVAMENTE`, not 404.
**Fix:** In `interpretarErroNotaCredito`, close only for `code === "DOCUMENTO_FISCAL_NAO_ENCONTRADO"` (or no code), and show other 404 codes as a banner. In the service, map an empty cliente lock to `processoAlterado()` (see CR-01).

### WR-03: The idempotency key lives in component state that is unmounted by query-driven conditions

**File:** `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx:84, 210, 233`; `nota-credito-dialog.tsx:95`
**Issue:** `NotaCreditoDialog` is only mounted while `mostrarEmissaoNc` is true. That depends on `useEstadoEmissao` (`faturacaoDesligada`) and `valorCreditavelRestante`, and both change on the `onSettled` invalidation that follows every emission attempt. If the dialog unmounts after an ambiguous failure, the `porResolver` key is lost. The same happens on navigation or reload. A later identical request then gets a new key, so a PARCIAL that actually committed can be duplicated: a second immutable NC, limited only by the cap. This is the CR-02 (134) guarantee the phase claims to inherit.
**Fix:** Keep unresolved attempts outside the component, keyed by `documentoOrigemId`, for example in a module-level `Map` or `sessionStorage`. Read them back in `tentativaParaPedido`. Alternatively, render the dialog unconditionally and only hide or disable the trigger.

### WR-04: Partial-NC retention is computed on the unclamped base

**File:** `backend/src/main/java/com/lexcv/services/fiscal/ComposicaoNotaCredito.java:103-111`
**Issue:** `retencao = c.retencao().min(remRetencao)` uses `c.base()`, the base before the P-13 clamp. When the base clamp fires (`base = c.base().min(remBase)`, or `base = valor - remIva`), the NC's `totalRetencao` no longer equals `round(base * taxaRetencao / 100)` for its own base. The CONTEXT asks for retention proportional to the FR. In the worst case the document shows a base of 0.00 with a non-zero retention. The drift is only cents, but it is persisted on an immutable fiscal document that phase 136 will serialize.
**Fix:** Recompute after clamping: `retencao = taxaRetencao == null ? 0.00 : base.multiply(taxaRetencao).divide(CEM, 2, HALF_UP).min(remRetencao)`. Add a test vector where the base clamp fires.

## Info

### IN-01: "Fully credited" refusal shows the wrong copy

**File:** `web/src/lib/erros-emissao.ts:222`
**Issue:** The backend sends `NC_EXCEDE_ORIGINAL` with `MSG_TOTALMENTE_CREDITADA` when `remTotal <= 0`. The UI always replaces it with `COPY_NC_EXCEDE`, which says "Reduza o valor ou escolha crédito total", even when the user already chose TOTAL.
**Fix:** Use `mensagemDoCorpo(error.body) ?? COPY_NC_EXCEDE`, or use a separate code such as `NC_TOTALMENTE_CREDITADA`.

### IN-02: NC ambiente is hardcoded to SIMULADO instead of copied from the FR

**File:** `NotaCreditoService.java:231, 242, 303`; `PreVisualizacaoNotaCreditoResponse.java:62`
**Issue:** The NC's `ambiente` is hardcoded to `SIMULADO`. Once phase 136 adds a real environment, an NC on a production FR would be numbered in the SIMULADO series.
**Fix:** Use `origem.getAmbiente()`, or refuse when the FR's ambiente differs.

### IN-03: The "remaining creditable" formula is implemented three times

**File:** `ComposicaoNotaCredito.java:73-89`, `NotaCreditoService.java:394-402`, `DocumentoFiscalDetalheResponse.java` (`de(...)`)
**Issue:** The three copies already differ slightly: the service filters by `tipo == NC` and null totals, while the DTO does neither.
**Fix:** Expose one static helper and use it in all three places.

### IN-04: KPI month uses the system clock rather than the injected `Clock`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:3400`
**Issue:** `YearMonth.now(RecebidoNoMes.FUSO_CABO_VERDE)` cannot be pinned in tests, and the estorno date comes from the injected `Clock`. Also, `RecebidoNoMes.FUSO_CABO_VERDE` duplicates `PagamentoFaturadoService.FUSO_CABO_VERDE`.
**Fix:** Inject `Clock` and use `YearMonth.now(clock.withZone(...))`. Keep a single zone constant.

### IN-05: Replay returns the current remainder, not the remainder at emission time

**File:** `NotaCreditoService.java:338, 344`
**Issue:** A 200 replay returns a `valorCreditavelRestante` that differs from the original 201 body if another NC was emitted in between. This is harmless, but the field's meaning is not documented.
**Fix:** Document the behaviour, or derive the value from the notes up to and including this NC.

### IN-06: Preview skips checks that emission performs

**File:** `NotaCreditoService.java:124-139`
**Issue:** The preview does not check that the processo and honorário are still linked, or the cliente lock path. A preview can therefore succeed while emission refuses with 404 or 409. The design says preview and emission refuse "com os mesmos códigos".
**Fix:** Run the same scalar checks in the preview, without taking locks.

---

_Reviewed: 2026-10-05T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
