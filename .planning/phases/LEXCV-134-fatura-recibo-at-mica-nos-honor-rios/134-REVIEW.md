---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
reviewed: 2026-10-04T00:00:00Z
depth: standard
files_reviewed: 82
files_reviewed_list:
  - backend/migrations/134-create-documento-fiscal-tables.sql
  - backend/migrations/README.md
  - backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java
  - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalRef.java
  - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalResumoResponse.java
  - backend/src/main/java/com/lexcv/dtos/EstadoEmissaoResponse.java
  - backend/src/main/java/com/lexcv/dtos/PagamentoComDocumentoResponse.java
  - backend/src/main/java/com/lexcv/dtos/PagamentoRequest.java
  - backend/src/main/java/com/lexcv/dtos/PreVisualizacaoFaturaResponse.java
  - backend/src/main/java/com/lexcv/models/AuditLog.java
  - backend/src/main/java/com/lexcv/models/ComunicacaoFiscal.java
  - backend/src/main/java/com/lexcv/models/DocumentoFiscal.java
  - backend/src/main/java/com/lexcv/models/DocumentoFiscalLinha.java
  - backend/src/main/java/com/lexcv/models/EstadoComunicacaoFiscal.java
  - backend/src/main/java/com/lexcv/models/EstadoComunicacaoFiscalConverter.java
  - backend/src/main/java/com/lexcv/models/MetodoPagamento.java
  - backend/src/main/java/com/lexcv/repositories/ClienteRepository.java
  - backend/src/main/java/com/lexcv/repositories/ComunicacaoFiscalRepository.java
  - backend/src/main/java/com/lexcv/repositories/ConfiguracaoFiscalRepository.java
  - backend/src/main/java/com/lexcv/repositories/ContaCorrenteRepository.java
  - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalLigacaoClienteRepository.java
  - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalLinhaRepository.java
  - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalRepository.java
  - backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java
  - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
  - backend/src/main/java/com/lexcv/services/fiscal/CalculoFiscal.java
  - backend/src/main/java/com/lexcv/services/fiscal/ComposicaoFaturaRecibo.java
  - backend/src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java
  - backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java
  - backend/src/main/java/com/lexcv/services/fiscal/PreVisualizacaoFaturaService.java
  - backend/src/main/java/com/lexcv/services/fiscal/ProjetoFaturaRecibo.java
  - backend/src/main/java/com/lexcv/services/fiscal/ResultadoPagamentoFaturado.java
  - backend/src/main/java/com/lexcv/services/fiscal/TextoDocumentoFiscal.java
  - backend/src/main/java/com/lexcv/services/fiscal/ValidacaoEmissao.java
  - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java
  - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java
  - backend/src/test/java/com/lexcv/controllers/FaturacaoDesligadaPagamentoInalteradoTest.java
  - backend/src/test/java/com/lexcv/controllers/GuardasDocumentoFiscalConcorrenciaIT.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerDocumentoFiscalGuardasTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerListaPagamentosTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerPagamentoTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerProveniencaPapelTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java
  - backend/src/test/java/com/lexcv/models/MetodoPagamentoTest.java
  - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalImutabilidadeTest.java
  - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalRepositoryIT.java
  - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal134IT.java
  - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/CalculoFiscalTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ComposicaoFaturaReciboTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ConfiguracaoFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/DocumentoFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java
  - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoConcorrenciaIT.java
  - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceIT.java
  - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/PreVisualizacaoFaturaServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ValidacaoEmissaoTest.java
  - web/package.json
  - web/scripts/verify-documentos-fiscais.mjs
  - web/src/app/(dashboard)/financeiro/[id]/pagamento-faturado-dialog.tsx
  - web/src/app/(dashboard)/financeiro/[id]/pagamento-faturado-form.tsx
  - web/src/app/(dashboard)/financeiro/[id]/pagamentos-card.tsx
  - web/src/app/(dashboard)/financeiro/[id]/page.tsx
  - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
  - web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx
  - web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx
  - web/src/app/(dashboard)/financeiro/page.tsx
  - web/src/components/shared/data-table/data-table.tsx
  - web/src/components/ui/dialog.tsx
  - web/src/hooks/use-faturacao.ts
  - web/src/hooks/use-financeiro.ts
  - web/src/lib/erros-emissao.test.ts
  - web/src/lib/erros-emissao.ts
  - web/src/lib/idempotencia.test.ts
  - web/src/lib/idempotencia.ts
  - web/src/schemas/financeiro.test.ts
  - web/src/schemas/financeiro.ts
  - web/src/types/faturacao.ts
  - web/src/types/financeiro.ts
findings:
  critical: 2
  warning: 5
  info: 5
  total: 12
status: fixed
fix:
  fixed_at: 2026-10-04
  in_scope: 11
  fixed: 11
  skipped: 1
  skipped_ids: [IN-03]
---

# Phase 134: Code Review Report

**Reviewed:** 2026-10-04
**Depth:** standard
**Files Reviewed:** 82
**Status:** issues_found

## Summary

I reviewed the atomic Fatura-Recibo emission (`PagamentoFaturadoService`), its pure composition, calculation and validation helpers, the read side and its guards (`DocumentoFiscalService`, `DocumentoFiscalController`), the `ResourceController` integration (delegation, delete guards, merge), the migration, and the frontend payment dialog, list and detail screens.

What holds up:
- **Money math:** a single `HALF_UP` division, IVA taken as the residual, retention charged on the base. The CONTEXT reference example (120 000 → 104 347,83 / 15 652,17 / 20 869,57 / 99 130,43) reproduces exactly, and the client does no money math.
- **Tenant isolation:** every new finder is tenant-scoped. The global `pagamento_id` unique is safe because ids are serial.
- **`@Immutable` and the merge:** `@Immutable` plus the narrow repositories plus a single native `UPDATE ... cliente_id` is sound.
- **CFG-03 (billing off):** the disabled branch is pinned by a source hash.
- **Lock order:** configuração → cliente → processo → conta corrente → série is honoured by every path that takes locks. I found no deadlock cycle.

What does not hold up:
- **Honorário delete race:** the emission never re-checks the honorário after it takes its locks. A concurrent honorário delete can therefore leave an immutable FR (and a payment) pointing at a deleted honorário. The schema has no FKs, so nothing catches it.
- **Idempotency key is dropped too early:** the frontend throws the key away as soon as the dialog closes. That includes the case right after an ambiguous (5xx/network) failure, so one click on "Voltar e editar" plus a re-submit issues a second, undeletable FR.
- **Unlocked writers on the conta corrente:** the emission credits it under `FOR UPDATE`, but other writers (`deletePagamento`, merge, the GET auto-create) neither lock nor version it, so credits can be lost.

## Critical Issues

### CR-01: Honorário can be deleted while an emission for it is in flight, leaving an orphan FR and payment

**File:** `backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java:175-189` (with `backend/src/main/java/com/lexcv/controllers/ResourceController.java:3223-3250`)

**Issue:** `registar` reads the honorário (step 5) before it takes any lock except the configuração lock, and never re-verifies it afterwards. `deleteHonorario` serialises only on the **processo** row lock. This interleaving is possible:

1. The emission reads honorário H (it exists).
2. `deleteHonorario` locks processo P, finds no documents and no payments, deletes H and commits.
3. The emission, which was waiting on P's lock, acquires it. The processo still exists and `cliente == processo.cliente`, so every check passes.
4. The emission inserts `t_pagamento(honorario_id = H)` and `t_documento_fiscal(honorario_id = H)` and commits.

Neither `t_pagamento.honorario_id` nor `t_documento_fiscal.honorario_id` has an FK (the migration deliberately has none, lines 23-24), so the result is an immutable fiscal document and a payment for a non-existent honorário. This is exactly what the D-14 "honorário com documentos fiscais" guard exists to prevent. `GuardasDocumentoFiscalConcorrenciaIT` covers cliente, processo and merge races, but not honorário.

**Fix:** after the processo lock, re-check that the honorário still exists with a query. `findById` will not work here, because it returns the instance already managed in the persistence context.
```java
// after step 7 (processo locked)
if (!honorarioRepository.existsById(honorario.getId())) {   // executes SELECT, not cache
    throw processoAlterado();
}
```
`existsById` is safe here because `deleteHonorario` takes the processo lock before deleting, so its delete has committed by the time this lock is granted. A more robust option is to lock the honorário row (`@Lock(PESSIMISTIC_WRITE)`, ordered after processo) in both `registar` and `deleteHonorario`. Add an IT case `emissaoEApagarHonorarioEmSimultaneoSemOrfaos` to `GuardasDocumentoFiscalConcorrenciaIT`.

### CR-02: Idempotency key is discarded after an ambiguous failure, so a retry from the form issues a duplicate FR

**File:** `web/src/app/(dashboard)/financeiro/[id]/pagamento-faturado-form.tsx:89-93, 111-124, 143-151`

**Issue:** The key is created in `onSubmit` (line 118), and `fecharDialogo` sets it back to `null` (line 91). After a 5xx or network error, `emitir` keeps the dialog open with the error message, but "Voltar e editar", Esc and the X button are all enabled again (`emitindo` is false). The 5xx case includes a 504 from the Next rewrite proxy while the backend is still waiting on the 5 s lock and then commits.

If the user clicks "Voltar e editar" and then "Registar pagamento" again without changing anything, a new preview runs and **a new key** is generated (line 118). The second POST is then a fresh emission. If the first request actually committed, the result is two payments and two immutable FRs for one payment intent, and nothing can cancel them until Phase 135 (Nota de Crédito). D-10 exists precisely to make the ambiguous-failure retry safe, and the most prominent button beside the error defeats it.

A related gap: `onSettled` refetches the payments list after the error, but the form stays filled, which invites the re-submit.

**Fix:** tie the key to the request payload, not to the dialog instance. Keep the key while an ambiguous outcome is unresolved, and only mint a new one when the payload changes.
```tsx
const ultimaTentativa = React.useRef<{ pedidoJson: string; chave: string; ambigua: boolean } | null>(null);

// onSubmit, after the preview succeeds:
const json = JSON.stringify(novoPedido);
const anterior = ultimaTentativa.current;
const chaveUsar = anterior?.ambigua && anterior.pedidoJson === json ? anterior.chave : gerarChaveIdempotencia();
ultimaTentativa.current = { pedidoJson: json, chave: chaveUsar, ambigua: false };
setChave(chaveUsar);

// emitir catch, when erro?.tipo === "rede":
if (ultimaTentativa.current) ultimaTentativa.current.ambigua = true;
// on success / definitive 4xx: ultimaTentativa.current = null;
```
Also consider keeping "Voltar e editar" disabled after a "rede" error until either a retry succeeds or the refreshed payments list shows the payment is absent.

## Warnings

### WR-01: Conta corrente credit can be lost; the emission's `FOR UPDATE` is bypassed by unlocked, unversioned writers

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:3281-3284` (`deletePagamento`), `:932-945` (merge), `:666-669` (`getClienteContaCorrente`); `backend/src/main/java/com/lexcv/models/ContaCorrente.java` (no `@Version`)

**Issue:** `PagamentoFaturadoService` sets `saldo = S + T` under `PESSIMISTIC_WRITE`. Two other writers break this:
- `deletePagamento` for a legacy (unbilled) payment reads the same row without a lock, computes `S − v`, and calls `save`. Hibernate emits `UPDATE ... SET saldo = ?` with the absolute value, so if its read happened before the emission committed, the write overwrites `S + T` and the FR's credit disappears.
- `mergeClientes` reads both conta-corrente rows without a lock. It holds the cliente locks, but `deletePagamento` takes none, so the same lost update can happen between those two.
- `getClienteContaCorrente` (a GET) can `save` a new row and race the emission's `INSERT ... ON CONFLICT` on the unique index, giving a 500 on a read endpoint.

The phase's design claims conta-corrente correctness through the lock, but only one of the writers takes it.

**Fix:** route every conta-corrente mutation through `criarSeNaoExiste` + `bloquearPorCliente` (respecting the cliente → processo → cc order where a cliente lock is taken), or add `@Version` to `ContaCorrente` so that stale writes fail instead of silently overwriting. At minimum, make `deletePagamento` use an atomic `UPDATE t_conta_corrente SET saldo = saldo - :v WHERE cliente_id = :c`.

### WR-02: Adquirente `localidade` is not validated, so a long value fails at INSERT with a 500 after all 422 checks pass

**File:** `backend/src/main/java/com/lexcv/services/fiscal/ValidacaoEmissao.java:279-291`, `ComposicaoFaturaRecibo.java:82`; migration line 66

**Issue:** `Cliente.localidade` is an unbounded `String` (VARCHAR(255) by default), while `t_documento_fiscal.adquirente_localidade` is `VARCHAR(100)`. `validarAdquirente` checks NIF, nome (≤150) and morada (≤100), but never localidade. A cliente whose localidade is 101-255 characters passes the preview, then fails at flush with `value too long`. The result is a rolled-back emission and a generic 500, and the error mapper turns that into "Verifique a ligação", a retry with the same key that fails forever. The other snapshot columns are bounded; this one was missed.

**Fix:** either validate it (`ADQUIRENTE_INCOMPLETO`, campo `localidade`, ≤100, with matching frontend copy), or truncate it explicitly in `ComposicaoFaturaRecibo` the same way `TextoDocumentoFiscal.descricaoLinhaHonorarios` truncates. Add a test with a 101-character localidade.

### WR-03: The billing-mode gate silently falls back to the legacy form, which can never succeed when billing is active

**File:** `web/src/app/(dashboard)/financeiro/[id]/page.tsx:150-152, 495-541`

**Issue:** The billed form renders only when `useEstadoEmissao` returned `ativa: true`. In every other state the legacy form renders: query disabled, still loading, or errored.
- **Edit without exact view:** a role with `financeiro:edit` (or `manage`, through the fallback) but without exact `financeiro:view` never enables the query.
- **Query error:** the same happens if `/faturacao/estado-emissao` fails (500 or network). `isLoading` is false after the error, so the "Adicionar" button is enabled.

On submit, the backend (billing active) returns 422 `CHAVE_IDEMPOTENCIA_OBRIGATORIA` and the legacy handler shows "Pedido sem chave de idempotência. Reabra a confirmação…". That message refers to a confirmation dialog the user never saw, and the user can never register a payment. Nothing is wrongly persisted, so this is not a data issue, but the UI and the backend disagree about which mode is active.

**Fix:** treat "unknown" as its own state.
```tsx
const modoFaturacao = !podeLerDocumentosFiscais(perms) ? "desconhecido"
  : estadoEmissao.isError ? "erro"
  : estadoEmissao.data == null ? "a-carregar"
  : estadoEmissao.data.ativa ? "ativa" : "desligada";
```
Render the legacy form only for `"desligada"`. For `"erro"`, show a retry. For `"desconhecido"`, show an explicit message, or align the gate by allowing `estado-emissao` for `financeiro:edit` on the backend as well.

### WR-04: A 403 on preview or emission shows nothing, so the "Registar pagamento" click silently does nothing

**File:** `web/src/lib/erros-emissao.ts:108`; `web/src/app/(dashboard)/financeiro/[id]/pagamento-faturado-form.tsx:96-97`

**Issue:** `interpretarErroEmissao` returns `null` for 401/403, assuming `apiFetch` already showed a toast. Per CLAUDE.md, `apiFetch` does **not** toast 401/403, and `mostrarErro(null)` returns early. The form shows `canEditFinanceiro`, which uses the frontend fallback (`manage` ⇒ `edit`), while the backend checks exact `financeiro:edit` on both `/faturacao/pre-visualizacao` and `/pagamentos`. A `manage`+`view` role without `edit` therefore clicks the button and gets no feedback at all.

**Fix:** map 403 to a banner ("Não tem permissão para registar pagamentos."), and gate the billed form on the exact `financeiro:edit` authority, mirroring `podeLerDocumentosFiscais`.

### WR-05: A replay of an already-emitted key is refused (or bypassed) when billing is deactivated in between

**File:** `backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java:151-168`; `ResourceController.createPagamento`

**Issue:** The idempotency lookup runs only after `cfg.ativa` passes the filter. If an admin deactivates billing between the original (committed) request and the client's retry, two things go wrong:
- **Lost result:** the retry gets 409 `FATURACAO_DESLIGADA` instead of the stored result, so the client never learns that its FR was emitted.
- **Legacy duplicate:** when the controller's scalar check already says "off", the request goes to `registarPagamentoLegado`, which ignores `chaveIdempotencia` and inserts a second, unbilled payment and a second conta-corrente credit.

The window is narrow, but it breaks the "same key → same result" contract in D-10.

**Fix:** when `req.chaveIdempotencia() != null`, look the key up first, regardless of `ativa`, and return the stored result if it exists (still under a lock, e.g. `bloquearPorTenant` without the `ativa` filter). Do this in `createPagamento` before branching to the legacy path. The hashed legacy body stays untouched, because the lookup runs before the branch.

## Info

### IN-01: Payments table "Método" column shows the raw enum for billed payments (known follow-up)

**File:** `web/src/app/(dashboard)/financeiro/[id]/pagamentos-card.tsx:146`

**Issue:** With billing on, `t_pagamento.metodo` stores `MetodoPagamento.name()` (`TRANSFERENCIA`, `CARTAO`…), and the card renders it verbatim. Legacy rows hold free text. This is UX only; no data is affected.

**Fix:** map known enum names to their labels using `METODOS_PAGAMENTO` from `schemas/financeiro.ts`, and fall back to the raw text for legacy rows: `METODOS_PAGAMENTO.find(m => m.valor === p.metodo)?.rotulo ?? p.metodo ?? "—"`.

### IN-02: 503 `FATURACAO_OCUPADA` is shown as a network problem, and 5xx errors are double-reported

**File:** `web/src/lib/erros-emissao.ts:109`; `web/src/hooks/use-financeiro.ts` (`semToastParaStatus: [409, 422]`)

**Issue:** Every status ≥500 becomes `COPY_REDE` ("Verifique a ligação"), including the backend's own 503 "A faturação está ocupada…" message. `apiFetch` also toasts the 5xx, so the user sees both a toast and the inline error. Retrying with the same key is still the correct behaviour.

**Fix:** for `status === 503` with a `code`, use the backend `message` inline, and add 503 to `semToastParaStatus`.

### IN-03: The emitted document is not checked against the preview the user confirmed

**File:** `backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java:191-199`

**Issue:** `registar` recomposes everything from live data. If the cliente's name, NIF or morada is edited, or the IVA parameter changes, between the preview and "Emitir", the immutable FR differs from what the user confirmed, with no warning. Recomposing is consistent with "frontend burro", but nothing detects the divergence.

**Fix (optional):** return a hash of the preview (the snapshot fields plus the totals), send it back with the emission, and refuse with 409 `PRE_VISUALIZACAO_DESATUALIZADA` on mismatch.

### IN-04: A lock timeout on the conta-corrente `INSERT ... ON CONFLICT` is not mapped to 503

**File:** `backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java:202`

**Issue:** `criarSeNaoExiste` can wait on an uncommitted conflicting insert from another writer that holds no cliente lock (for example `getClienteContaCorrente`). With `lock_timeout = 5s`, that wait throws outside `bloquear(...)` and surfaces as a 500 instead of `FATURACAO_OCUPADA`.

**Fix:** wrap it: `bloquear(() -> contaCorrenteRepository.criarSeNaoExiste(clienteId));`.

### IN-05: Preview and emission answer a cross-tenant or missing cliente with different codes

**File:** `backend/src/main/java/com/lexcv/services/fiscal/PreVisualizacaoFaturaService.java:237-240` vs `PagamentoFaturadoService.java:181-182`

**Issue:** The preview returns 404 `CLIENTE_NAO_ENCONTRADO`, while the emission returns 409 `PROCESSO_ALTERADO_TENTE_NOVAMENTE` for the same state. This contradicts the documented intent that "both refuse with the same codes". It is harmless, but it makes `interpretarErroEmissao` behave differently for the two calls.

**Fix:** align the codes, or document the divergence (the emission's 409 is a race signal).

---

_Reviewed: 2026-10-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

## Fix Report

**Fixed:** 2026-10-04 · **Fixer:** Claude (gsd-code-fixer) · **Scope:** CR + WR + IN-01, IN-02, IN-04, IN-05 (IN-03 optional)
**Result:** 11 of 12 findings fixed in 10 atomic commits. IN-03 was skipped as out of scope (not trivial).

| ID | Status | Commit | Summary |
|----|--------|--------|---------|
| CR-01 | fixed | 59609cd | After the processo lock, `registar` re-reads the honorário's processo as a scalar query (`HonorarioRepository.processoIdPorId`, which never reads the persistence-context cache). If the honorário is gone or now belongs to another processo, it refuses with 404 `HONORARIO_NAO_ENCONTRADO`. New ITs: `emissaoEmCursoBloqueiaApagarHonorario` and `emissaoEApagarHonorarioEmSimultaneoSemOrfaos`. The second one fails on the old code. |
| CR-02 | fixed: requires human verification | dd79836 | The idempotency key now belongs to the request payload, not to the dialog (`tentativaParaPedido` / `marcarPorResolver` / `pedidoCanonico` in `lib/idempotencia.ts`, plus `desfechoDefinitivo` in `lib/erros-emissao.ts`). After a network error, 5xx, 401/403, 408 or 429, the key survives the dialog closing and is reused when the same payload is sent again. It is dropped after a success, after a processed 4xx, or when the payload changes. Vitest covers the key lifecycle. **Deliberate deviation from the UI-SPEC line "regenerated whenever the dialog is closed and reopened"**: that rule caused the bug. |
| WR-01 | fixed: requires human verification | 1b48dce | `deletePagamento` debits atomically (`UPDATE saldo = saldo - :v`). If no row changes because a concurrent merge moved the processo, it re-reads the processo's current cliente and debits that one. The handler stays non-transactional (P-02). `mergeClientes` reads both contas correntes with `bloquearPorCliente`, after the cliente locks. The GET conta-corrente creates a missing row with `INSERT ... ON CONFLICT` instead of `save()`. New ITs `apagarPagamentoLegadoDuranteEmissaoNaoPerdeOCredito` and `...DuranteFusaoDebitaOClienteQueFica` both fail on the old code. |
| WR-02 | fixed | 0072301 | `ValidacaoEmissao.validarAdquirente(..., localidade)` refuses a localidade longer than 100 characters with 422 `ADQUIRENTE_INCOMPLETO`, campo `localidade`. The preview and the emission share this check through `ComposicaoFaturaRecibo`. The frontend shows the adquirente banner with `COPY_LOCALIDADE`. |
| WR-03 | fixed | c884f4c | The page derives an explicit mode with `modoFormularioPagamento`: sem-permissao, a-carregar (legacy form with submit disabled, per UI-SPEC), erro (red message plus "Tentar novamente"), ativa, or desligada. Only "desligada" enables the legacy submit. `GET /faturacao/estado-emissao` now accepts `financeiro:view` OR `financeiro:edit`, so whoever registers payments can learn the mode. The authorization tests were updated. |
| WR-04 | fixed | c884f4c | The payment card is gated on the exact `financeiro:edit` authority (`podeRegistarPagamentos`), as the backend requires. A 403 now shows the banner "Não tem permissão para registar pagamentos.". This shares a commit with WR-03 because both change the same gate in `page.tsx`. |
| WR-05 | fixed: requires human verification | fc583cb | `registar` looks up the key under the configuração lock *before* it checks `ativa`. With billing off, `createPagamento` first calls `PagamentoFaturadoService.resultadoGuardado` (same lock, no `ativa` filter). That returns the stored result (200) or 409 `CHAVE_REUTILIZADA`. `registarPagamentoLegado` is untouched, and the CFG-03 SHA guard passes. New IT: `repeticaoDepoisDeDesligarAFaturacaoDevolveOMesmoResultado`. |
| IN-01 | fixed | cc2c447 | `rotuloMetodoPagamento` maps enum names to their labels and leaves legacy free text as it is. |
| IN-02 | fixed | 2f8f917 | A 503 that carries a `code` keeps the retryable "rede" outcome but shows the backend message. The preview and the keyed `POST /pagamentos` no longer toast 409/422/5xx (`STATUS_INLINE_EMISSAO`). The unkeyed legacy request keeps its old toasts. |
| IN-03 | skipped | — | Optional, and it needs a new preview-hash protocol on both sides plus a new 409 code. That is beyond a trivial fix, so it was left for a later phase. |
| IN-04 | fixed | c0be4df | `criarSeNaoExiste` is now wrapped in `bloquear(...)`, so its lock wait answers 503 `FATURACAO_OCUPADA`. |
| IN-05 | fixed | 3bf4076 | When the cliente is missing or was deleted while the emission waited for its lock, the emission answers 404 `CLIENTE_NAO_ENCONTRADO`, the same as the preview. The 409 `PROCESSO_ALTERADO` is kept for the processo-moved race. |

**Verification:**
- Backend: `mvn -Dmaven.compiler.release=21 test` gives 821 tests, 0 failures. The phase-134 ITs ran for real through Testcontainers (`GuardasDocumentoFiscalConcorrenciaIT` 11, `PagamentoFaturadoServiceIT` 10, `PagamentoFaturadoConcorrenciaIT` 4, `DocumentoFiscalRepositoryIT` 13, `MigracaoFiscal134IT` 6), all green. `spotbugs:check` reports 0 bugs.
- Frontend: `tsc --noEmit` is clean. `pnpm lint` has 0 errors; the 20 warnings were there before these fixes. `pnpm test` gives 189 tests passing. `verify:faturacao` and `verify:documentos-fiscais` pass; the latter was updated on purpose for the CR-02 and IN-02 contracts.

**What is still open:**
- The CR-02 key lives in component state, so it is lost if the user leaves the page after an ambiguous failure. The refreshed payments list (`onSettled`) is the remaining signal.
- The legacy `registarPagamentoLegado` still does an unlocked read-modify-write on the conta corrente. Its body is frozen by CFG-03, and it only runs with billing off.

