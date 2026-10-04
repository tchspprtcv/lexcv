---
phase: 133-funda-o-fiscal
reviewed: 2026-10-04T13:34:03Z
depth: standard
files_reviewed: 66
files_reviewed_list:
  - backend/migrations/133-create-fiscal-foundation-tables.sql
  - backend/migrations/README.md
  - backend/src/main/java/com/lexcv/config/ClockConfig.java
  - backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java
  - backend/src/main/java/com/lexcv/controllers/FaturacaoController.java
  - backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalRequest.java
  - backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalResponse.java
  - backend/src/main/java/com/lexcv/dtos/EmailAutomaticoRequest.java
  - backend/src/main/java/com/lexcv/dtos/MotivoIsencaoResponse.java
  - backend/src/main/java/com/lexcv/dtos/SerieFiscalResponse.java
  - backend/src/main/java/com/lexcv/exceptions/RecusaFiscalException.java
  - backend/src/main/java/com/lexcv/models/AmbienteFiscal.java
  - backend/src/main/java/com/lexcv/models/AmbienteFiscalConverter.java
  - backend/src/main/java/com/lexcv/models/AuditLog.java
  - backend/src/main/java/com/lexcv/models/CodigoParametroFiscal.java
  - backend/src/main/java/com/lexcv/models/ConfiguracaoFiscal.java
  - backend/src/main/java/com/lexcv/models/MotivoIsencaoIva.java
  - backend/src/main/java/com/lexcv/models/ParametroFiscal.java
  - backend/src/main/java/com/lexcv/models/RegimeIva.java
  - backend/src/main/java/com/lexcv/models/RegimeIvaConverter.java
  - backend/src/main/java/com/lexcv/models/SerieFiscal.java
  - backend/src/main/java/com/lexcv/models/TipoDocumentoFiscal.java
  - backend/src/main/java/com/lexcv/models/TipoDocumentoFiscalConverter.java
  - backend/src/main/java/com/lexcv/repositories/ConfiguracaoFiscalRepository.java
  - backend/src/main/java/com/lexcv/repositories/ParametroFiscalRepository.java
  - backend/src/main/java/com/lexcv/repositories/SerieFiscalRepository.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
  - backend/src/main/java/com/lexcv/services/fiscal/ConfiguracaoFiscalService.java
  - backend/src/main/java/com/lexcv/services/fiscal/NumeracaoService.java
  - backend/src/main/java/com/lexcv/services/fiscal/NumeroFiscalAtribuido.java
  - backend/src/main/java/com/lexcv/services/fiscal/ParametroFiscalService.java
  - backend/src/test/java/com/lexcv/config/GlobalExceptionHandlerRecusaFiscalTest.java
  - backend/src/test/java/com/lexcv/controllers/FaturacaoControllerAutorizacaoTest.java
  - backend/src/test/java/com/lexcv/controllers/FaturacaoControllerTest.java
  - backend/src/test/java/com/lexcv/controllers/FaturacaoDesligadaPagamentoInalteradoTest.java
  - backend/src/test/java/com/lexcv/dtos/ConfiguracaoFiscalRequestValidationTest.java
  - backend/src/test/java/com/lexcv/models/MotivoIsencaoIvaTest.java
  - backend/src/test/java/com/lexcv/models/SerieFiscalCodigoTest.java
  - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal133IT.java
  - backend/src/test/java/com/lexcv/repositories/ParametroFiscalRepositoryIT.java
  - backend/src/test/java/com/lexcv/seed/DatabaseSeederCatalogoPermissoesTest.java
  - backend/src/test/java/com/lexcv/seed/DatabaseSeederInstanciabilidadeMoldesTest.java
  - backend/src/test/java/com/lexcv/seed/DatabaseSeederParametrosFiscaisTest.java
  - backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ConfiguracaoFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/NumeracaoServiceConcorrenciaIT.java
  - backend/src/test/java/com/lexcv/services/fiscal/NumeracaoServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ParametroFiscalServiceTest.java
  - backend/src/test/java/com/lexcv/services/fiscal/ParametrosFiscaisSemConstantesTest.java
  - web/package.json
  - web/scripts/verify-faturacao.mjs
  - web/src/app/(dashboard)/settings/faturacao-ativacao-card.tsx
  - web/src/app/(dashboard)/settings/faturacao-dados-form.tsx
  - web/src/app/(dashboard)/settings/faturacao-email-card.tsx
  - web/src/app/(dashboard)/settings/faturacao-series-card.tsx
  - web/src/app/(dashboard)/settings/faturacao-tab.tsx
  - web/src/app/(dashboard)/settings/page.tsx
  - web/src/components/ui/button.tsx
  - web/src/hooks/use-faturacao.ts
  - web/src/lib/api.test.ts
  - web/src/lib/api.ts
  - web/src/schemas/faturacao.test.ts
  - web/src/schemas/faturacao.ts
  - web/src/types/faturacao.ts
findings:
  critical: 1
  warning: 7
  info: 7
  total: 15
status: fixed
fix:
  fixed_at: 2026-10-04T14:10:00Z
  iteration: 1
  scope: "critical + warning, plus IN-02, IN-05, IN-06, IN-07"
  findings_in_scope: 12
  fixed: 11
  accepted_by_decision: 1
  skipped_out_of_scope: 3
  status: all_fixed
---

# Phase 133: Code Review Report

**Reviewed:** 2026-10-04T13:34:03Z
**Depth:** standard
**Files Reviewed:** 66
**Status:** issues_found

## Narrative Findings (AI reviewer)

## Summary

I read every production file in full: the fiscal entities, converters, repositories, the three fiscal services, the controller, the DTOs, the seeder diff, the migration script and README diff, and all frontend files. I read the test files only to check that they are reliable and to see what they actually prove. Two findings come from tracing into Hibernate 6.6.4: I disassembled `ColumnDefinitions`, `StandardTableMigrator` and `AbstractSchemaValidator` to find the root cause of the boot DDL error.

What holds up:
- **Tenant isolation.** The tenant always comes from `UserPrincipal`. No handler accepts a UUID, a path variable or a query parameter. Every series and configuration finder is scoped by `tenantId`. The only lookup that crosses tenants is the deliberate NIF check (see WR-05).
- **RBAC.** Both layers agree. The backend uses the class-level `hasAuthority('financeiro:manage')`, and the frontend uses `hasScopedPermission(..., "manage")`, whose fallback list for `manage` is just `["manage"]`, so the frontend never grants more than the backend.
- **Error mapping.** `RecusaFiscalException` maps cleanly to `{message, code, campo?}`.

The main defect is in `ConfiguracaoFiscalService`. Its mutating methods read the configuration row without a lock and without a `@Version`. Hibernate then writes the whole row back, so two concurrent fiscal-settings requests silently overwrite each other. This can switch billing back on with no matching audit event, and it breaks the invariant "envio automático ligado ⇒ faturação ativa" (CR-01).

`NumeracaoService` is correct for the scenarios its IT covers. It still has one gap: a series that is already loaded in the persistence context is not re-read under the lock (WR-02).

The known boot DDL error is confirmed and classified as a WARNING. Its root cause is a **length** mismatch, not the `not null` text. The fix proposed in `deferred-items.md` would therefore not stop the ALTER from running on every boot (WR-01).

## Critical Issues

### CR-01: Lost updates on `t_configuracao_fiscal`: concurrent fiscal actions overwrite each other, re-enable billing without an audit event, and break the email/activation invariant

**File:** `backend/src/main/java/com/lexcv/services/fiscal/ConfiguracaoFiscalService.java:103,175,194,220` (and `backend/src/main/java/com/lexcv/models/ConfiguracaoFiscal.java:28`, `backend/src/main/java/com/lexcv/repositories/ConfiguracaoFiscalRepository.java:14`)

**Issue:** `guardar`, `ativar`, `desativar` and `definirEmailAutomatico` all load the row with a plain `findByTenantId`. There is no `PESSIMISTIC_WRITE` lock and no `@Version`. The entity has no `@DynamicUpdate`, so Hibernate's UPDATE writes **every** column from the stale in-memory copy. Under READ COMMITTED the second writer waits for the first writer's row lock and then overwrites its result. Concrete interleavings, each by two `financeiro:manage` users in the same office (or one user double-submitting from two tabs):

1. `ativar` (T1) and `guardar` (T2) both read `ativa=false`. T1 commits `ativa=true` and writes a `faturacao_ativar` audit event. T2 then writes the full row with `ativa=false`. Billing ends up off, but the audit log says it was activated.
2. `desativar` (T1) and email-on (T2) both read `ativa=true, envio=false`. T1 commits `ativa=false, envio=false`. T2 then writes `ativa=true, envio=true`. Billing is back **on** with no `faturacao_ativar` event (the audit gap), and the invariant comment at line 205 no longer holds.
3. Looking ahead to Phase 134, the irreversibility rule has the same problem. `desativar` (line 195) and the NIF lock in `guardar` (line 104) are check-then-act against `existsByTenantIdAndUltimoNumeroGreaterThan`, and nothing serializes them with an emission that increments the series. A document can be issued between the check and the commit, after which billing is turned off or the NIF changes.

The tests miss this because they use Mockito, and the only concurrency IT covers `NumeracaoService`.

**Fix:** Lock the configuration row in every mutating method, and require Phase 134 emission to lock the same row before it takes the series lock. This keeps the documented lock order (configuration, then conta corrente, then series last).
```java
// ConfiguracaoFiscalRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select c from ConfiguracaoFiscal c where c.tenantId = :tenantId")
Optional<ConfiguracaoFiscal> bloquearPorTenant(@Param("tenantId") UUID tenantId);

// ConfiguracaoFiscalService.guardar/ativar/desativar/definirEmailAutomatico
ConfiguracaoFiscal config = configuracaoFiscalRepository.bloquearPorTenant(tenantId).orElse(null);
```
Alternatively, or in addition, add `@Version private Long versao;` to `ConfiguracaoFiscal` (plus the column in the migration script) and map `ObjectOptimisticLockingFailureException` to `409 CONFIGURACAO_FISCAL_CONCORRENTE`. Add a Testcontainers IT covering interleaving 2.

## Warnings

### WR-01: DDL error on every boot for `t_serie_fiscal.ambiente` / `tipo_documento` (known issue confirmed). The root cause is a length mismatch, so the fix in deferred-items is incomplete

**File:** `backend/src/main/java/com/lexcv/models/SerieFiscal.java:39,48`; also `backend/src/main/java/com/lexcv/models/ConfiguracaoFiscal.java:72`

**Issue:** This is confirmed in `/tmp/claude-0/backend.log` (lines 40-46 and 174-180): every boot after the first runs `alter column ambiente set data type varchar(32) not null` and `alter column tipo_documento ...`, and both fail with `syntax error at or near "not"`.

The trigger is not the converter's type. In Hibernate 6.6.4, `StandardTableMigrator` emits the ALTER when `!hasMatchingType || !hasMatchingLength`. `ColumnDefinitions.hasMatchingLength` returns `true` immediately unless the SQL type contains `(`. When it does contain `(`, it compares the database column size (32) with the mapped `Column.getColumnSize().getLength()`, which is the JPA default of **255** because no `length` is set. 32 ≠ 255, so Hibernate emits an ALTER that copies the whole `columnDefinition`.

Consequences:
- The fix proposed in `deferred-items.md` (`columnDefinition = "varchar(32)"`) would only make the ALTER valid SQL. It would still run on every boot and take an ACCESS EXCLUSIVE lock each time.
- By the same mechanism, `ConfiguracaoFiscal.regimeIva` (`columnDefinition = "varchar(32)"`) is very likely already running a silent, successful `ALTER ... SET DATA TYPE varchar(32)` on every boot. It is not visible because successful DDL is not logged at the current log level; turning on DEBUG for `org.hibernate.SQL` would confirm it.
- `validate` mode is **not** affected. `AbstractSchemaValidator` only calls `hasMatchingType`, which matches VARCHAR to VARCHAR, so this stays a WARNING rather than a BLOCKER.
- The boot log now contains a recurring ERROR/WARN, which makes real DDL failures easy to overlook during deploys.

**Fix:** Remove `columnDefinition` and declare the length, which keeps the column a plain varchar with no CHECK (the converter maps to `String`):
```java
@Convert(converter = AmbienteFiscalConverter.class)
@Column(name = "ambiente", nullable = false, length = 32)
private AmbienteFiscal ambiente;
// same for tipoDocumento and ConfiguracaoFiscal.regimeIva (nullable)
```
Then re-run `MigracaoFiscal133IT.hibernateNaoGeraCheckNasColunasDeEnum` and the parity test, boot twice against the same database, and check that no `set data type` statement appears with `logging.level.org.hibernate.SQL=DEBUG`.

### WR-02: `NumeracaoService` can hand out a duplicate number if the series entity is already in the persistence context

**File:** `backend/src/main/java/com/lexcv/services/fiscal/NumeracaoService.java:83-93`; `backend/src/main/java/com/lexcv/repositories/SerieFiscalRepository.java:37-43`

**Issue:** `bloquear` is a JPQL query with `PESSIMISTIC_WRITE`. If the same `SerieFiscal` was loaded earlier in the caller's transaction without a lock (for example, a Phase 134 caller that reads or lists the series first), Hibernate still runs `SELECT ... FOR UPDATE`. However, it returns the **already-managed instance without re-hydrating it** from the locked row. If another transaction committed an increment in between, `serie.getUltimoNumero() + 1` uses the stale value and produces the same number twice. The gapless guarantee then depends entirely on the Phase 134 UNIQUE constraint. `NumeracaoServiceConcorrenciaIT` cannot catch this because each worker starts with a fresh persistence context.

**Fix:** Make the increment atomic in SQL so it never depends on persistence-context state:
```java
@Modifying(flushAutomatically = true, clearAutomatically = false)
@Query(nativeQuery = true, value = "UPDATE t_serie_fiscal SET ultimo_numero = ultimo_numero + 1 "
     + "WHERE tenant_id = :tenantId AND tipo_documento = :tipo AND ano = :ano AND ambiente = :ambiente "
     + "RETURNING id, codigo, ultimo_numero")
```
Or keep the current flow and call `entityManager.refresh(serie, LockModeType.PESSIMISTIC_WRITE)` after `bloquear`. Either way, add a unit or IT case that loads the series before calling `proximoNumero`.

### WR-03: The fiscal data form silently discards unsaved edits whenever another card's mutation (or a refetch) changes the configuration

**File:** `web/src/app/(dashboard)/settings/faturacao-dados-form.tsx:181-183`; `web/src/app/(dashboard)/settings/faturacao-email-card.tsx:293`; `web/src/app/(dashboard)/settings/faturacao-ativacao-card.tsx:124`

**Issue:** `useEffect(() => { if (data) form.reset(valoresIniciais(data)); }, [data, form])` resets the form every time the query data **reference** changes. Structural sharing only keeps the same reference when the data is deep-equal. All four mutations invalidate `FATURACAO_CONFIG_KEY`. Only "Ativar" is disabled while `dadosPorGravar`; the email switch and "Desativar faturação" stay enabled. So a user who is halfway through editing the NIF or address and then toggles the email switch loses those edits without warning, because the refetched object differs (`envioEmailAutomatico`). The same happens on a window-focus refetch after another admin changes anything.

**Fix:** Do not reset over dirty fields:
```tsx
React.useEffect(() => {
  if (data) form.reset(valoresIniciais(data), { keepDirtyValues: true });
}, [data, form]);
```
Also consider disabling the email switch and the deactivate button while `dadosPorGravar`, matching the activate button.

### WR-04: Fiscal mutations only invalidate on success, so after a 409 the UI keeps showing the stale state that caused it

**File:** `web/src/hooks/use-faturacao.ts:60-65,79-84,98-103,117-122`

**Issue:** The 409/422 errors (`FATURACAO_JA_EMITIU`, `FATURACAO_DESLIGADA`, `CONFIGURACAO_FISCAL_CONCORRENTE`, `NIF_BLOQUEADO`) mean the client's view is out of date. Because invalidation runs only in `onSuccess`, the card keeps rendering the old flags. For example, after `FATURACAO_JA_EMITIU` the "Desativar faturação" button stays visible next to the inline error. After `NIF_BLOQUEADO` the NIF field stays editable, because `nifBloqueado` comes from the stale cache. Correct state returns only after the 30-second `staleTime` or a focus refetch.

**Fix:** Move the invalidation to `onSettled` (or invalidate in `onError` when `isApiError(e) && [409, 422].includes(e.status)`).

### WR-05: Cross-tenant NIF uniqueness (`NIF_JA_REGISTADO`) is check-then-act with no database constraint, and the product rule is unconfirmed

**File:** `backend/src/main/java/com/lexcv/services/fiscal/ConfiguracaoFiscalService.java:111-114`; `backend/src/main/java/com/lexcv/repositories/ConfiguracaoFiscalRepository.java:18`; `backend/migrations/133-create-fiscal-foundation-tables.sql:33-53`

**Issue:**
1. Two offices saving the same NIF at the same time both pass `existsByNifAndTenantIdNot` and both commit. Nothing at the database level enforces the rule, so it fails exactly in the concurrent case.
2. The 133-04 SUMMARY says this rule is **not** in 133-CONTEXT.md and "needs user confirmation". It shipped anyway and blocks legitimate setups (for example, a firm that operates two LexCV tenants under one NIF).
3. Any `financeiro:manage` user can check whether a given NIF is registered as an issuer in *another* tenant, which is a cross-tenant existence oracle. The impact is low because company NIFs are semi-public, but it is a deliberate crossing of the primary isolation boundary and should be a conscious decision.

**Fix:** Get the rule confirmed. If it stays, add a partial unique index both in the entity (via `@Table(indexes=...)`, or the migration only plus a parity test) and in the script: `CREATE UNIQUE INDEX IF NOT EXISTS uk_configuracao_fiscal_nif ON t_configuracao_fiscal (nif) WHERE nif IS NOT NULL;`. Then map the resulting `DataIntegrityViolationException` to `NIF_JA_REGISTADO`. At the moment every integrity violation at line 159 is reported as `CONFIGURACAO_FISCAL_CONCORRENTE`, so you would need to distinguish them by constraint name. If the rule is dropped, remove the cross-tenant query.

### WR-06: New fiscal endpoints can reach the catch-all handler, which returns `ex.getMessage()` and the exception class name to the client

**File:** `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java:122-129`; reachable from `backend/src/main/java/com/lexcv/services/fiscal/ConfiguracaoFiscalService.java:187,208,241,252`, `NumeracaoService.java:84-85`, `AmbienteFiscalConverter.java:23` / `TipoDocumentoFiscalConverter.java:23` / `RegimeIvaConverter.java:23`

**Issue:** The handler itself predates this phase, but this phase adds new ways to reach it:
- `ativar`, `desativar` and email use `save()` rather than `saveAndFlush()`, so the UPDATE is flushed at commit, **outside** any try/catch. A commit-time `DataIntegrityViolationException`, `JpaSystemException` or (once CR-01 is fixed with `@Version`) an optimistic-lock failure is returned with Hibernate/PostgreSQL text, which can include constraint names and SQL.
- `NumeracaoService`'s `IllegalStateException` message includes the series code.
- The enum converters' `valueOf` throws `IllegalArgumentException("No enum constant ...")` for any unknown value in the database. That happens, for example, if a Phase 136 `ambiente` value is written and the deploy is then rolled back. `GET /series` would then return 500 with the class path in the message.

`RecusaFiscalException`'s contract says the client only ever receives text written by LexCV, but these paths break that contract.

**Fix:** In the catch-all, return a fixed message (`"Erro interno. Tente novamente."`) and keep `ex` in the server log only. In the fiscal services, use `saveAndFlush` inside the existing try blocks so integrity and lock failures become `RecusaFiscalException` codes.

### WR-07: Reusing `financeiro:manage` silently gives every existing holder control over the office's fiscal identity

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (catalog entry `financeiro:manage`, diff around line 379); `backend/src/main/java/com/lexcv/controllers/FaturacaoController.java:52`

**Issue:** Until this phase, `financeiro:manage` meant "Eliminar Lançamentos Financeiros". Office admins may have given it to custom roles (Phase 126/128 office roles) just so someone could delete entries. After this deploy, the seeder relabels the permission in place, and every such role can now change the issuing NIF, turn billing on and off, and accept the email declaration. There is no migration note, no audit event and no notice to the office. This follows the CONTEXT decision ("esta fase NÃO cria permissões novas"), but it widens privileges for existing installs and should be an explicit, documented choice.

**Fix:** Either introduce a dedicated `faturacao:manage` permission (granted by default only to ADMIN) and gate both layers on it, or document the widening in `backend/migrations/README.md` and the release notes with a query that lists custom roles holding `financeiro:manage`, so operators can review them before upgrading.

## Info

### IN-01: `lock_timeout = 5s` stays in effect for the rest of the caller's transaction

**File:** `backend/src/main/java/com/lexcv/repositories/SerieFiscalRepository.java:57-58`; `backend/src/main/java/com/lexcv/services/fiscal/NumeracaoService.java:80`
**Issue:** `set_config('lock_timeout','5s', true)` applies until the end of the transaction, not just to the series statements. Any statement Phase 134 runs after numbering, such as the document INSERT or other row updates, silently inherits the 5-second limit. The javadoc covers lock order but not this side effect.
**Fix:** Document it in the `NumeracaoService` javadoc. If needed, restore the previous value after `bloquear` (read `current_setting('lock_timeout')` first, then `set_config(..., prev, true)`).

### IN-02: Several bean-validation constraints have no Portuguese message, and the frontend schema does not mirror the server's maximum lengths

**File:** `backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalRequest.java:26,34,39,43`; `backend/src/main/java/com/lexcv/dtos/EmailAutomaticoRequest.java:10`; `web/src/schemas/faturacao.ts:365-369`
**Issue:** `@Size(max=200/100/254/32)` and `@NotNull ligado` use the Hibernate Validator default message, which depends on the locale and may be English. The frontend shows it inline as-is. The Zod schema has no matching `.max()` for firma, localidade, email or telefone, so these limits only show up after a round trip.
**Fix:** Add `message = "..."` in Portuguese to each constraint, and add `.max(200)`, `.max(100)`, `.max(254)` and `.max(32)` in `configuracaoFiscalSchema`.

### IN-03: `useMotivosIsencao(true)` bypasses the gating convention

**File:** `web/src/app/(dashboard)/settings/faturacao-dados-form.tsx:166`
**Issue:** The hook comment says callers pass `can.manage("financeiro")`. Today this is safe only because the parent tab is already gated; if the form is reused elsewhere, a user without the permission would trigger a 403.
**Fix:** Pass the permission flag in from `FaturacaoTab`, or call `usePermissions()` here.

### IN-04: The enum converters throw on unknown database values

**File:** `backend/src/main/java/com/lexcv/models/AmbienteFiscalConverter.java:23`, `TipoDocumentoFiscalConverter.java:23`, `RegimeIvaConverter.java:23`
**Issue:** The plain varchar with no CHECK was chosen so that new enum values can be added later. In return, any older binary that reads a newer value fails the whole query (see WR-06).
**Fix:** Accept this as a deploy-ordering constraint and document it in the Phase 136 plan, or throw a domain exception with a clear message.

### IN-05: `CONFIGURACAO_FISCAL_CONCORRENTE` shows "Verifique os campos assinalados" when no field is marked

**File:** `web/src/app/(dashboard)/settings/faturacao-dados-form.tsx:214-216`
**Issue:** For a 409 that has no `campo`, the generic `ERRO_GUARDAR` points the user at highlighted fields that don't exist, and the backend's useful message ("Atualize a página e tente de novo") is thrown away.
**Fix:** `setErroGuardar(!erro.campo && !erro.camposValidacao && erro.mensagem ? erro.mensagem : ERRO_GUARDAR)`.

### IN-06: The migration parity IT does not compare column defaults, and the script differs from Hibernate on `ultimo_numero`

**File:** `backend/migrations/133-create-fiscal-foundation-tables.sql:72`; `backend/src/test/java/com/lexcv/repositories/MigracaoFiscal133IT.java:81-94`
**Issue:** The script declares `ultimo_numero BIGINT NOT NULL DEFAULT 0`. Hibernate generates no default for it (no `columnDefinition`). `colunas()` does not select `column_default`, so the "column-for-column" claim in the script header has not actually been tested for defaults. It is harmless today because the native INSERT always supplies `0`, but databases created by the script and by `ddl-auto` differ.
**Fix:** Add `column_default` to the compared tuple. Then either remove `DEFAULT 0` from the script or add `columnDefinition`/`@ColumnDefault("0")` to the entity (keeping WR-01 in mind).

### IN-07: The javadoc gives the wrong endpoint path

**File:** `backend/src/main/java/com/lexcv/dtos/EmailAutomaticoRequest.java:6`
**Issue:** It says `PUT /api/v1/faturacao/configuracao/envio-email`; the actual mapping is `PUT /api/v1/faturacao/email-automatico` (`FaturacaoController.java:88`).
**Fix:** Correct the javadoc.

---

_Reviewed: 2026-10-04T13:34:03Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

## Fix Report

**Fixed at:** 2026-10-04T14:10:00Z
**Fixer:** Claude (gsd-code-fixer), iteration 1
**Scope:** all Critical and Warning findings, plus IN-02, IN-05, IN-06 and IN-07. IN-01, IN-03 and IN-04 were left out by request.
**Branch:** `claude/laughing-babbage-ny2z3n` (fast-forwarded, not pushed)

| Finding | Result | Commit(s) |
|---|---|---|
| CR-01 | fixed: requires human verification (concurrency logic) | `03b8126` |
| WR-01 | fixed | `b9c1707` |
| WR-02 | fixed: requires human verification (concurrency logic) | `6d7baaf`, `9c2f78d` |
| WR-03 | fixed | `e59dd9e` |
| WR-04 | fixed | `db7bb21` |
| WR-05 | fixed (rule removed, per user decision) | `d9e0c91` |
| WR-06 | fixed | `5425e7d` |
| WR-07 | accepted by decision, no change | none |
| IN-02 | fixed | `7b6f1af` |
| IN-05 | fixed | `cb50fef` |
| IN-06 | fixed | `cfb0c0d` |
| IN-07 | fixed | `a9e3422` |
| IN-01, IN-03, IN-04 | skipped: out of scope for this round | none |

### CR-01: lost updates on `t_configuracao_fiscal`
- New `ConfiguracaoFiscalRepository.bloquearPorTenant` (`@Lock(PESSIMISTIC_WRITE)` JPQL). `guardar`, `ativar`, `desativar` and `definirEmailAutomatico` use it as their first DB operation, before the series check. `obter` keeps the unlocked `findByTenantId`.
- Lock order is documented in the repository, the service and `NumeracaoService`: **configuração fiscal → (conta corrente) → série**. Phase 134 emission must take the configuration lock first.
- When no row exists yet (first `guardar`) there is nothing to lock. `uk_configuracao_fiscal_tenant` still turns that race into `CONFIGURACAO_FISCAL_CONCORRENTE`.
- Tests:
  - Unit test `mutacoesBloqueiamAConfiguracaoAntesDasSeries` checks that each mutator locks before the series check and never uses the unlocked finder.
  - New Testcontainers IT `ConfiguracaoFiscalConcorrenciaIT` covers interleaving 2 (desativar vs. email on → `FATURACAO_DESLIGADA`, row stays off, no `faturacao_email_ligar` event) and interleaving 1 (ativar vs. guardar → activation not lost).
  - Both IT cases were run against a real PostgreSQL 16 (see "Verification"). They pass with the lock and both fail when the service is switched back to `findByTenantId`.

### WR-01: ALTER on every boot
- Removed `columnDefinition` from `SerieFiscal.tipoDocumento` and `ambiente` (now `nullable = false, length = 32`) and from `ConfiguracaoFiscal.regimeIva` (`length = 32`). The migration script is unchanged and the parity test still holds.
- New test `MigracaoFiscal133IT.segundoArranqueEmUpdateNaoEmiteDdlFiscal`. It captures the boot `Metadata` through a test-only `IntegratorProvider` (`CapturaMetadataHibernate`), runs Hibernate's `update` SchemaMigrator in SCRIPT mode against the schema Hibernate just created, and asserts that no DDL touches the fiscal tables.
- Against the pre-fix entities this test reproduces exactly the reviewer's diagnosis: three ALTERs, `regime_iva set data type varchar(32)`, `ambiente ... varchar(32) not null` and `tipo_documento ... varchar(32) not null`. That confirms the silent `regime_iva` ALTER as well.
- A real double boot with `ddl-auto=update` and `org.hibernate.SQL=DEBUG` on one database gave: boot 1 created the tables; boot 2 emitted 0 fiscal ALTERs and 0 "Error executing DDL". `deferred-items.md` item 2 is annotated as resolved.

### WR-02: duplicate number with a series that is already managed
- After `bloquear` (FOR UPDATE), `NumeracaoService` calls `serieFiscalRepository.refrescar(serie)`, a new Spring Data fragment (`SerieFiscalRepositoryCustom`/`Impl`, `@PersistenceContext` EntityManager) that re-reads the locked row before the increment.
- The first version (`6d7baaf`) injected `EntityManager` through the constructor, which SpotBugs flagged as EI_EXPOSE_REP2. `9c2f78d` moved the refresh into the fragment, so the `NumeracaoService(repo, clock)` constructor is back to its original form.
- Tests: a unit test checks that the refresh happens after the lock, and the new IT case `serieJaCarregadaNoPersistenceContextNaoDuplicaNumero` preloads the series. Without the refresh that case returns the duplicate `2` instead of `3`, which reproduces the bug.

### WR-03: unsaved edits discarded
- The form reset now uses `{ keepDirtyValues: true, keepErrors: true }`. When `nifBloqueado` is set, the `nif` field is reset to the server value.
- Not done: the reviewer's optional suggestion to disable the email switch and the deactivate button while `dadosPorGravar`. The form no longer loses edits, so the main defect is gone.

### WR-04: stale UI after a 409/422
- All four mutations invalidate in `onSettled` through a shared `invalidarFaturacao` helper.

### WR-05: cross-tenant NIF uniqueness
- Removed by user decision. The `NIF_JA_REGISTADO` check in `guardar`, `existsByNifAndTenantIdNot`, its unit test and the frontend `CodigoErroFaturacao` entry are all gone. A replacement test asserts that `guardar` never queries other tenants and that the repository only has tenant-scoped finders.

### WR-06: catch-all leaks exception details
- `GlobalExceptionHandler.handleAllExceptions` now returns `{"message": "Erro interno. Tente novamente.", "referencia": <UUID>}` and logs the same `referencia` at ERROR level with the full exception. The `error` key (class name) and `ex.getMessage()` are no longer sent to the client.
- No existing test asserted the old body. Controllers catch their own expected exceptions (for example `SetupController`), so no user-facing message depended on the catch-all.
- New test `GlobalExceptionHandlerCatchAllTest`. `saveAndFlush` inside the fiscal try blocks was not added, per the decision to keep the fix minimal.

### WR-07: `financeiro:manage` reuse
- Accepted by decision. Reusing `financeiro:manage` is a locked milestone decision (REQUIREMENTS "Decisões de âmbito"), so RBAC is unchanged.

### IN-02 / IN-05 / IN-06 / IN-07
- **IN-02:** `@Size` on firma, localidade, emailContacto and telefoneContacto, and `@NotNull` on `ligado`, now have Portuguese messages. The Zod schema mirrors the limits with `.max(200/100/254/32)`. Tests added on both sides.
- **IN-05:** a field-less 400/409/422 shows the backend message instead of the generic "Verifique os campos assinalados".
- **IN-06:** the parity IT now compares `column_default`. `SerieFiscal.ultimoNumero` has `@ColumnDefault("0")` (not `columnDefinition`, so WR-01 is unaffected) to match the script's `DEFAULT 0`. The stricter parity check fails if that annotation is removed.
- **IN-07:** the javadoc path is corrected to `PUT /api/v1/faturacao/email-automatico`.

### Verification
- Backend: `mvn -Dmaven.compiler.release=21 test` gives 550 tests, 0 failures. `mvn spotbugs:check` passes with 0 bugs.
- **Failsafe ITs did not run under Testcontainers.** The Docker daemon was not running in this session (stale `/run/docker.pid`), and restarting it was not permitted, so the failsafe run fails with "Could not find a valid Docker environment".
- As a substitute, untracked scratch copies of `MigracaoFiscal133IT`, `ConfiguracaoFiscalConcorrenciaIT`, `NumeracaoServiceConcorrenciaIT` and `ParametroFiscalRepositoryIT` (identical test bodies, datasource pointed at a throwaway local PostgreSQL 16 instead of a container) all passed: 8 + 2 + 10 + 2 cases. The scratch copies were deleted and never committed.
- **Re-run `mvn verify` with Docker available before closing the phase.**
- Frontend: `npx tsc --noEmit` is clean. `pnpm lint` reports 0 errors and the same 20 warnings as before these changes. `pnpm test` passes 90/90. `pnpm verify:faturacao` passes.

