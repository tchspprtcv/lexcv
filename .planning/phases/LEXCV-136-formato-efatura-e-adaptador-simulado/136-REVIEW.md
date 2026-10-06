---
phase: 136-formato-efatura-e-adaptador-simulado
reviewed: 2026-10-06T19:06:14Z
depth: standard
files_reviewed: 100
files_reviewed_list:
  - "backend/.env.example"
  - "backend/migrations/136-efatura-comunicacao.sql"
  - "backend/migrations/README.md"
  - "backend/pom.xml"
  - "backend/spotbugs-exclude.xml"
  - "backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java"
  - "backend/src/main/java/com/lexcv/dtos/ComunicacaoFiscalResumo.java"
  - "backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalRequest.java"
  - "backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java"
  - "backend/src/main/java/com/lexcv/dtos/EstadoEmissaoResponse.java"
  - "backend/src/main/java/com/lexcv/dtos/ReprocessarComunicacaoResponse.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/ClasspathXsdResolver.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/DfeMarshaller.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/DfeValidador.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/DfeXmlBuilder.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/DocumentoComunicavel.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/EfaturaConfig.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/EfaturaGateway.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/EfaturaProperties.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/InjetorFalhas.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/IudGerador.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/MapeamentoEfatura.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/PedidoComunicacao.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/RecusaFormatoEfatura.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/ResultadoComunicacao.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/ResultadoValidacao.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/SimuladoEfaturaGateway.java"
  - "backend/src/main/java/com/lexcv/fiscal/efatura/TransmissaoEfatura.java"
  - "backend/src/main/java/com/lexcv/jobs/FiscalOutboxJob.java"
  - "backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java"
  - "backend/src/main/java/com/lexcv/models/ComunicacaoFiscal.java"
  - "backend/src/main/java/com/lexcv/models/DocumentoFiscalXml.java"
  - "backend/src/main/java/com/lexcv/models/EstadoComunicacaoFiscal.java"
  - "backend/src/main/java/com/lexcv/repositories/DocumentoFiscalXmlRepository.java"
  - "backend/src/main/java/com/lexcv/repositories/FilaComunicacaoFiscal.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/BackoffComunicacao.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/ComunicacaoFiscalTransacoes.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/ComunicacaoReclamada.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/EstadoComunicacaoMapper.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscal.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscal.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/ReprocessamentoComunicacaoService.java"
  - "backend/src/main/java/com/lexcv/services/fiscal/SnapshotComunicacao.java"
  - "backend/src/main/resources/application.yml"
  - "backend/src/main/resources/xsd/bindings/efatura.xjb"
  - "backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java"
  - "backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java"
  - "backend/src/test/java/com/lexcv/dtos/ComunicacaoFiscalResumoTest.java"
  - "backend/src/test/java/com/lexcv/dtos/ConfiguracaoFiscalRequestValidationTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/ClasspathXsdResolverTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/DfeMarshallerTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/DfeValidadorTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/DfeXmlBuilderTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/EfaturaConfigTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/IudGeradorTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/MapeamentoEfaturaTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/SimuladoEfaturaGatewayTest.java"
  - "backend/src/test/java/com/lexcv/fiscal/efatura/XsdEfaturaIntegridadeTest.java"
  - "backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobFalhasForcadasIT.java"
  - "backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobIT.java"
  - "backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobTest.java"
  - "backend/src/test/java/com/lexcv/models/CategoriaNotificacaoTest.java"
  - "backend/src/test/java/com/lexcv/repositories/DocumentoFiscalImutabilidadeTest.java"
  - "backend/src/test/java/com/lexcv/repositories/MigracaoFiscal134IT.java"
  - "backend/src/test/java/com/lexcv/repositories/MigracaoFiscal135IT.java"
  - "backend/src/test/java/com/lexcv/repositories/MigracaoFiscal136IT.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/BackoffComunicacaoTest.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/DocumentoFiscalServiceTest.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/EstadoComunicacaoMapperTest.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/FilaComunicacaoFiscalIT.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscalTest.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscalPipelineTest.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscalTest.java"
  - "backend/src/test/java/com/lexcv/services/fiscal/ReprocessarComunicacaoIT.java"
  - "backend/src/test/resources/efatura/2024-05-27/2 InvoiceReceipt.xml"
  - "backend/src/test/resources/efatura/2024-05-27/5 CreditNote.xml"
  - "backend/src/test/resources/efatura/2024-05-27/Read Me.txt"
  - "backend/src/test/resources/efatura/2024-05-27/XML Fields Map.txt"
  - "web/scripts/verify-documentos-fiscais.mjs"
  - "web/src/app/(dashboard)/financeiro/[id]/page.tsx"
  - "web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/comunicacao-fiscal-card.tsx"
  - "web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx"
  - "web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/reprocessar-comunicacao.tsx"
  - "web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx"
  - "web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx"
  - "web/src/components/shared/comunicacao-estado-badge.tsx"
  - "web/src/components/shared/modo-simulado-banner.tsx"
  - "web/src/hooks/use-faturacao.ts"
  - "web/src/lib/comunicacao-fiscal.test.ts"
  - "web/src/lib/comunicacao-fiscal.ts"
  - "web/src/lib/notificacao-categoria.test.ts"
  - "web/src/lib/notificacao-categoria.ts"
  - "web/src/schemas/faturacao.test.ts"
  - "web/src/schemas/faturacao.ts"
  - "web/src/types/faturacao.ts"
  - "web/src/types/notificacoes.ts"
findings:
  critical: 0
  warning: 7
  info: 8
  total: 15
status: issues_found
---

# Phase 136: Code Review Report

**Reviewed:** 2026-10-06T19:06:14Z
**Depth:** standard
**Files Reviewed:** 100 (production files read in full; tests scanned for reliability, not line-by-line)
**Status:** issues_found

## Summary

Scope: the eFatura DFE format (XSD resolver and validator, JAXB marshaller, builder, IUD), the
simulated gateway and `EFATURA_MODE` fail-fast, the outbox (claim, lease, version guard, backoff,
processor, job), reprocess, notification, the detail DTOs, and the web badge, card, banner and
polling.

What I checked and found sound:
- **XXE hardening.** The SchemaFactory, the Validator and the SAX parser all set
  `FEATURE_SECURE_PROCESSING`, `disallow-doctype-decl`, external entities off, XInclude off, and
  `ACCESS_EXTERNAL_DTD`/`ACCESS_EXTERNAL_SCHEMA` to an empty string.
- **Resolver whitelist.** It works on the URI after normalisation and checks it against an exact
  set of 22 names, so `..`, `%2E%2E`, `file:` and `http:` all resolve to null.
- **Simulated-never-authorized.** The DB has the CHECK `estado <> 'AUTORIZADO' OR ambiente =
  'PRODUCAO'`. The `switch` on `AmbienteFiscal` is exhaustive with no default branch. The sealed
  result type has no authorized variant. The UI wording never says authorized.
- **Claim SQL.** A CTE does `FOR UPDATE SKIP LOCKED`, then `UPDATE ... RETURNING`. Rows with an
  expired lease can be claimed again. Every write is guarded by `versao` and `tenant_id`.
- **Tenant scoping.** After the cross-tenant claim, the snapshot, XML, result and notification all
  use the claimed row's tenant.
- **XML storage.** The XML is stored insert-only and reused on reprocess. FR is claimed before NC
  (`ORDER BY created_at`).
- **Notification dedup.** The key is `documentoId:reprocessamentos`.
- **Reprocess.** It is a conditional UPDATE limited to `ERRO`/`REJEITADO`, so it cannot race a row
  the job is still processing (PENDENTE).
- **`financeiro:edit` gate.** It is exact on both layers.
- **Luhn.** I recomputed the check digit of the official IUD example and got 4, which matches.
- **Scheduler.** The pool size is 3.
- **`EFATURA_MODE`.** Any value other than exactly `SIMULADO` throws at bean creation.

No BLOCKER was proven. The defects fall into three groups:
1. **Outbox robustness.** Nothing caps a poison row. The lease covers the whole batch, not each
   item. Deterministic failures are classified as transient.
2. **Permanent dead-ends.** A FIRMA_EXCEDE_150 rejection shows an action that cannot fix it. An NC
   whose FR was rejected burns 8 attempts with a misleading message.
3. **Who is told vs who can act.** Notification recipients and the reprocess gate do not match.
   The safety banner fails open.

## Warnings

### WR-01: No upper bound on claims: a poison row is reclaimed forever and holds up its batch

**File:** `backend/src/main/java/com/lexcv/repositories/FilaComunicacaoFiscal.java:49-69` (with `ProcessadorComunicacaoFiscal.java:104-112`)
**Issue:** The claim increments `tentativas`, but only `EstadoComunicacaoMapper` can ever move a row to `ERRO`. That mapper runs only when the processor reaches `registarResultado`. Two cases never get there:
- A row whose processing kills the worker every time (OOM, JVM crash, a hung gateway past the lease).
- A row whose `registarResultado` keeps throwing (the `catch (Throwable)` at line 109 just returns).

Such a row stays `PENDENTE` and is reclaimed every lease expiry (2 min). `tentativas` grows without limit, the row never reaches `ERRO`, the users with `financeiro:manage` are never notified, and each reclaim takes a slot in the batch. The claim SQL has no `tentativas` predicate at all.
**Fix:** Close out over-attempted rows inside the claim, or exclude them and sweep them:
```sql
-- in SQL_RECLAMAR's devidas CTE
AND c.tentativas < :maxTentativas
-- plus a sweep in the same tx 1:
UPDATE t_comunicacao_fiscal
   SET estado = 'ERRO', ultimo_erro_codigo = 'FALHA_INTERNA',
       ultimo_erro = 'Falha interna ao comunicar o documento.', lease_ate = NULL,
       concluido_em = :agora, versao = versao + 1, updated_at = :agora
 WHERE estado = 'PENDENTE' AND tentativas >= :maxTentativas
   AND lease_ate IS NOT NULL AND lease_ate < :agora
RETURNING id, tenant_id, documento_fiscal_id, reprocessamentos;  -- notify these
```

### WR-02: One lease for the whole batch; items whose lease has expired are still sent to the gateway

**File:** `backend/src/main/java/com/lexcv/jobs/FiscalOutboxJob.java:62-73`, `backend/src/main/resources/application.yml` (`outbox.lote: 20`, `lease: PT2M`)
**Issue:** All 20 rows get `lease_ate = now + 2 min` at claim time and are then processed one after another. If the batch takes longer than the lease, the later items' leases expire before they are processed. Slow DB, GC pauses or, later, a real gateway with network latency can each cause this. Another instance can then claim those rows, and the first instance still calls `gateway.comunicar` for them. The version guard throws away the stale result, but the document has already been sent twice. A real eFatura connection would see duplicate submissions. The lease length and the batch size are configured separately, with no check that one fits the other.
**Fix:** Before each item, check `Instant.now() < claimInstant + lease` and skip the item if the lease has expired (leave it for the next claim). Or claim one row per short transaction. Or renew the lease per item (`UPDATE ... SET lease_ate = :novo WHERE id=:id AND versao=:versao`, and skip on 0 rows).

### WR-03: Deterministic snapshot defects are classified as transient `FALHA_INTERNA` and retried 8 times over about 5 h

**File:** `backend/src/main/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscal.java:145-149, 95-99`; `backend/src/main/java/com/lexcv/fiscal/efatura/IudGerador.java:54-71`; `DocumentoComunicavel.java:106-121`
**Issue:** These throw `IllegalArgumentException`:
- `IudGerador.gerar`, for a bad NIF, a number above 999 999 999, LED or type out of range.
- `DocumentoComunicavel.de`, for an NC without a motive, or an FR with an origin.
- The record constructors, through `requireNonNull`.

Only `RecusaFormatoEfatura` is mapped to `Rejeitado`. Everything else reaches the outer `catch (Throwable)` and becomes `ErroTransitorio(FALHA_INTERNA)`. The snapshot is immutable, so these failures can never succeed. They still go through 8 attempts (30 s ... 3 h), end in `ERRO` with "Falha interna", and log an ERROR stack trace on every attempt. As a result, the builder's own `NUMERO_FORA_DO_LIMITE` check (`DfeXmlBuilder.java:73-75`) is unreachable: `iudGerador.gerar` rejects the number first.
**Fix:** Validate the projection and IUD inputs before generating the IUD, and throw `RecusaFormatoEfatura` (e.g. `NUMERO_FORA_DO_LIMITE`, `TEXTO_INVALIDO`). Or catch `IllegalArgumentException` around `de` and `gerar` and return `Rejeitado`:
```java
} catch (RecusaFormatoEfatura recusa) { ... }
  catch (IllegalArgumentException invalido) {
    log.warn("Snapshot do documento fiscal {} não exprimível em eFatura", item.documentoFiscalId(), invalido);
    return new ResultadoComunicacao.Rejeitado(RecusaFormatoEfatura.Codigo.TEXTO_INVALIDO.name(),
            RecusaFormatoEfatura.Codigo.TEXTO_INVALIDO.mensagem());
}
```

### WR-04: FIRMA_EXCEDE_150 is a permanent dead-end, but the UI tells the user to fix it and reprocess

**File:** `backend/src/main/java/com/lexcv/fiscal/efatura/RecusaFormatoEfatura.java:17`; `DfeXmlBuilder.java:236-239`; `backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalRequest.java:26`; `web/src/lib/comunicacao-fiscal.ts:57`
**Issue:** The firma limit was lowered from 200 to 150, but only on the configuration request. Several things follow:
- Tenants whose stored firma is 151–200 characters keep emitting. Nothing on the emission path (`PagamentoFaturadoService`, `NotaCreditoService`) checks the length, and `t_documento_fiscal.emitente_firma` is `length = 200`.
- Every one of those FR and NC documents becomes `REJEITADO` with "A firma do escritório tem mais de 150 caracteres. Corrija os dados fiscais."
- The snapshot is immutable and reprocess reuses it, so correcting the configuration never fixes those documents.
- The card says "Pode reprocessar a comunicação." That promises an action which, for this code, is guaranteed to fail again.
**Fix:**
- (a) Block emission (or show a warning before it) when the stored firma is longer than 150, so no new impossible documents are created.
- (b) Change the message to state that already-issued documents cannot be corrected ("Os documentos já emitidos mantêm a firma antiga; corrija os dados fiscais para os próximos").
- (c) Optionally, have the UI stop offering reprocess for codes known to be deterministic. `ultimo_erro_codigo` is stored but not exposed.

### WR-05: An NC whose FR has no XML (FR REJEITADO) burns 8 attempts with a misleading message

**File:** `backend/src/main/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscal.java:137-140`
**Issue:** The FR's XML row is written only when the FR passes the builder and the XSD. If the FR is `REJEITADO` (e.g. WR-04: the NC copies the FR's emitter snapshot, so it fails the same way), the NC can never get `iudOrigem`. It returns `ErroTransitorio(ORIGEM_SEM_IUD, "A fatura-recibo de origem ainda não foi comunicada.")` 8 times over about 5 h, then goes to `ERRO`. It also sends a "falhou após várias tentativas" notification. That message implies the FR is still being worked on; in fact the FR is terminally rejected. Reprocessing the NC alone cannot help, and reprocessing the FR does not re-queue the NC.
**Fix:** In `carregarSnapshot`, also load the origin's `t_comunicacao_fiscal.estado` (same tenant). If the origin is terminal without an XML row (`REJEITADO`/`ERRO`), return `Rejeitado("ORIGEM_REJEITADA", "A fatura-recibo de origem foi rejeitada; reprocesse-a primeiro.")` instead of the transient result.

### WR-06: The notification goes to `financeiro:manage` holders, but the action it asks for requires exact `financeiro:edit`

**File:** `backend/src/main/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscal.java:110, 131-132, 164-171`; `backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java:229`; `web/src/hooks/use-faturacao.ts` (`podeReprocessarComunicacao`)
**Issue:** The notification text says: Abra o documento e use "Reprocessar comunicação". Recipients are chosen by exact `financeiro:manage` in their effective permissions. The button and the endpoint require exact `financeiro:edit`. `ResolucaoPapeisService.resolverPermissoesEfectivas` does not expand `manage` into `edit`, and office roles are configurable from the catalogue. So:
- A custom role with `financeiro:manage` but not `financeiro:edit` is notified but has no button (the card even says "Peça a um utilizador com permissão").
- Users with only `financeiro:edit` can act but are never told.

Each gate matches its CONTEXT line, but together they contradict the notification text.
**Fix:** Notify users whose effective permissions contain `financeiro:edit` (the permission that can actually act), or `edit` OR `manage`. Or reword the notification for recipients who cannot reprocess. Either way, add a test with a manage-only role.

### WR-07: The "Modo simulado" banner fails open: no banner while loading, on error, or when the field is missing

**File:** `web/src/components/shared/modo-simulado-banner.tsx:53-57`
**Issue:** The requirement is a *permanent* banner while `EFATURA_MODE=SIMULADO`. The component shows it only when `estadoEmissao.data?.modoComunicacao === "SIMULADO"`. In each of these cases the safety disclosure silently disappears:
- The request is loading.
- The request failed (network, 5xx, toast suppressed).
- An older backend omits the field.

This build has no non-simulated mode, so the only safe default is to show the banner. `mostrarReprocessar` uses the same strict comparison, and that one is correct (fail closed for an action). A disclosure should fail the other way.
**Fix:**
```tsx
const modo = estadoEmissao.data?.modoComunicacao;
// Hide only when the backend explicitly reports a different, known mode.
const simulado = modo == null || modo === "SIMULADO";
if (!enabled || !simulado) return null;
```

## Info

### IN-01: Unreachable branches in the builder

**File:** `backend/src/main/java/com/lexcv/fiscal/efatura/DfeXmlBuilder.java:73-75, 111-114`; `ProcessadorComunicacaoFiscal.java:152-154`
**Issue:**
- `NUMERO_FORA_DO_LIMITE` can never fire (see WR-03).
- The NC `ORIGEM_SEM_IUD` throw can never fire either. The processor returns `ORIGEM_SEM_IUD` before building, and `DocumentoComunicavel.de` throws `IllegalArgumentException` for the same condition first.
- Because of that, the processor's `recusa.tipo() == ORIGEM_SEM_IUD` branch is dead.

**Fix:** Remove these branches, or make WR-03's fix the place where these codes are raised.

### IN-02: Lost notification is never retried

**File:** `backend/src/main/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscal.java:176-190`
**Issue:** The notification is sent after the `ERRO` commit. If it fails (DB blip), the failure is only logged and nothing retries it: the row is terminal and no job looks at it again. Per-episode dedup would make a retry safe.
**Fix:** Optionally sweep `ERRO` rows whose `concluido_em` is recent and that have no matching notification, or record a `notificado_em` column.

### IN-03: `REJEITADO` never notifies

**File:** `ProcessadorComunicacaoFiscal.java:118-120`
**Issue:** Only `ERRO` triggers a notification. A terminal `REJEITADO` (WR-04, WR-05) is just as much an unmet fiscal obligation, and it is silent. This follows CONTEXT ("ERRO definitivo"), but the gap is worth an explicit product decision.

### IN-04: The reason is hidden during retries

**File:** `backend/src/main/java/com/lexcv/dtos/ComunicacaoFiscalResumo.java:37`
**Issue:** `ultimoErro` is shown only in `REJEITADO`/`ERRO`. A row that keeps failing with `ORIGEM_SEM_IUD` or `FALHA_INTERNA` stays "Pendente" for hours with no visible reason.
**Fix:** Consider exposing the last transient message while the row is `PENDENTE` with `tentativas > 0`.

### IN-05: DOCTYPE classification relies on the localised parser text

**File:** `backend/src/main/java/com/lexcv/fiscal/efatura/DfeValidador.java:128-131`
**Issue:** `mencionaDoctype` matches `"DOCTYPE"` in `SAXParseException.getMessage()`, and the JDK Xerces messages are localised by the JVM locale. Only the code changes (`XML_PROIBIDO` vs `XSD_INVALIDO`); the document is rejected either way. The validator also logs the raw parser message at DEBUG, which may include document values (NIF, names). Keep DEBUG off in production.

### IN-06: Focus is lost after a successful reprocess

**File:** `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/reprocessar-comunicacao.tsx:221-226`; `web/src/hooks/use-faturacao.ts` (`onSettled`)
**Issue:** `mutateAsync` resolves only after `onSettled` has awaited the invalidation. By then the detail has refetched as `PENDENTE`, `mostrarReprocessar` is false, and the whole component (with its Dialog) has unmounted. `onCloseAutoFocus` never runs, so focus never moves to the card title and is left on `<body>`. For a 409 with `refrescar`, the inline banner can likewise disappear before it is read.
**Fix:** Move the post-success focus into the card (e.g. a `useEffect` when the state changes from ERRO/REJEITADO to PENDENTE), or don't await the invalidation inside the mutation.

### IN-07: Duplicated or unused helpers

**File:** `web/src/lib/comunicacao-fiscal.ts:103-107` vs `web/src/hooks/use-faturacao.ts:261-264`; `ProcessadorComunicacaoFiscal.java:209` vs `SimuladoEfaturaGateway.java:190`
**Issue:**
- `intervaloAtualizacaoListaComunicacao` is tested but not used: the list hook re-implements the same rule inline, so the two can drift apart.
- `mensagemFormato` is copied in two classes.

**Fix:** Have the hook call the lib function, and keep one `mensagemFormato`.

### IN-08: Stale comment and catch-all `Throwable`

**File:** `backend/src/main/java/com/lexcv/config/SchedulingConfig.java:6-7`; `FiscalOutboxJob.java:56, 70`; `ProcessadorComunicacaoFiscal.java:95, 109, 186`
**Issue:**
- `SchedulingConfig` still says "single-thread ThreadPoolTaskScheduler", but `application.yml` now sets `pool.size: 3`.
- `catch (Throwable)` also swallows `OutOfMemoryError`/`VirtualMachineError` and keeps the loop running in a degraded JVM.

**Fix:** Update the comment. Catch `Exception` (plus `LinkageError` if desired) and let `VirtualMachineError` propagate.

---

_Reviewed: 2026-10-06T19:06:14Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
