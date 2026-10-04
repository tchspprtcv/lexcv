# Phase 135: Nota de Crédito - Pattern Map

**Mapped:** 2026-10-04
**Files analyzed:** 27 (new + modified, backend + web + tests)
**Analogs found:** 25 / 27 (2 partial, see "No Analog Found")

All paths below are relative to `/home/user/lexcv`. Backend base = `backend/src/main/java/com/lexcv`, tests = `backend/src/test/java/com/lexcv`, web = `web/src`.

## Critical findings the planner must act on (read first)

1. **`t_documento_fiscal.pagamento_id` is `NOT NULL` + `UNIQUE`** (`backend/migrations/134-create-documento-fiscal-tables.sql:70,85`; `DocumentoFiscal.java:148-149`, `uk_documento_fiscal_pagamento` at `:37`). An NC has no FR payment of its own, only `pagamento_estorno_id`. The 135 script must `ALTER COLUMN pagamento_id DROP NOT NULL` (and the entity column becomes nullable). `ddl-auto=update` never relaxes NOT NULL on an existing table, so this is manual-script-only. A Postgres UNIQUE tolerates multiple NULLs, so `uk_documento_fiscal_pagamento` can stay. Add `uk_documento_fiscal_estorno UNIQUE (pagamento_estorno_id)`.
2. **Everything that reads `doc.getPagamentoId()` as non-null breaks for an NC**: `PagamentoFaturadoService.repetirSeJaEmitido` (`:349-350`, `pagamentoRepository.findById(doc.getPagamentoId())` -> NPE/IllegalState), `mesmoPedido` (`:360-376`), `DocumentoFiscalDetalheResponse.pagamentoId` (`Integer`, `:48`), and the web `DocumentoFiscalDetalhe.pagamentoId: number`. The FR emission looks up the key across ALL documents of the tenant (`findByTenantIdAndChaveIdempotencia`), so a key that belongs to an NC must give `CHAVE_REUTILIZADA` (409), never a crash: guard `doc.getTipo() != FR` before `mesmoPedido`.
3. **`MigracaoFiscal134IT.scriptCriaExatamenteOEsquemaDoHibernate` will fail as soon as the entity gains columns** (it asserts `40 + 14 + 10` columns and `colunas("public") == colunas(SCHEMA_SCRIPT)` after applying ONLY the 134 script, `MigracaoFiscal134IT.java:162-163`; unique-set at `:166-175`). Deliberate update required: either apply 134 then 135 inside `aplicarScript()` (preferred; keep the test meaning "scripts == Hibernate") and bump counts to 43 + 14 + 10, add `uk_documento_fiscal_estorno` to the unique map, and add the new index if any; then add `MigracaoFiscal135IT` (copy of the 134 IT scaffold) proving the 135 script is idempotent and applies on top of a DB that already has the 134 schema.
4. **CFG-03 guard (must NOT be touched):** `FaturacaoDesligadaPagamentoInalteradoTest` pins the SHA-256 of `registarPagamentoLegado` (`ResourceController.java:3142-...`, hash at test `:57`), requires exactly ONE occurrence of lowercase `faturacao` in `ResourceController.java` (`:136-...` test `existeUmaSoVerificacaoDaFaturacaoDentroDeCreatePagamento`) and forbids the tokens `ConfiguracaoFiscal`, `NumeracaoService`, `SerieFiscal`, `SerieFiscalRepository`, `DocumentoFiscalRepository`, `ComunicacaoFiscal` in `ResourceController.java`. It also forbids `Fiscal`, `faturacao`, `Faturacao`, `chave`, `retencao` in `models/Pagamento.java`. Consequences: (a) never edit anything inside `registarPagamentoLegado`, not even a comment; (b) in `ResourceController` comments write "faturação" (accented), never ASCII `faturacao`; (c) do not add any column/field with those tokens to `Pagamento`; (d) the controller reaches fiscal data ONLY through `DocumentoFiscalService` (as today).
5. **The `deletePagamento` guard should be extended inside `DocumentoFiscalService.existeParaPagamento`, not in the controller**, so `ResourceControllerDocumentoFiscalGuardasTest` (mocks `existeParaPagamento`, `:135,151,200`) keeps passing untouched. But the 409 `code` will then be `PAGAMENTO_FATURADO` with the FR copy; decide if an estorno needs its own code (`PAGAMENTO_ESTORNO`, add to `lib/erros-emissao.ts` `COPY_GUARDA_POR_CODIGO` and `CodigoErroFaturacao`). If a separate code is wanted, expose a second service method (`existeEstornoParaPagamento`) and add one `if` in the controller using the same shape as `:3290-3294`.
6. **Four "pago" readers, status after this phase:** `Honorario.totalPago` `@Formula` (`models/Honorario.java:34`, `SUM(p.valor_pago)`) and the `AlertasDiariosJob` check (`jobs/AlertasDiariosJob.java:276-277`, uses `getTotalPago()`) ALREADY include a negative `Pagamento` and need NO code change, only a coherence test (and a check that the notification dedup, migration 88 `notificacao` dedup constraint, does not suppress a legitimate re-fire; verify, do not assume). `ContaCorrente.saldo` is moved by the service (debit via `contaCorrenteRepository.debitar(clienteId, valor)` is atomic and already exists at `ContaCorrenteRepository.java`; credit by the locked read-modify-write in `PagamentoFaturadoService:215-219`). `calculateMensalReceived` (`ResourceController.java:3378-3396`) DOES need the fix: month-only compare (`getMonthValue()` without year), `LocalDate.now()` with no zone, and `pag.getValorPago()` has no null check. It has no `Clock` (ResourceController injects none, `:79-94`); use a `private static final ZoneId` constant (do not add a constructor dependency, `@InjectMocks`-style controller tests would get null). No existing test covers `getDashboard`/`calculateMensalReceived` (grep over `src/test` found none), so add `ResourceControllerDashboardKpiTest`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `services/fiscal/NotaCreditoService.java` (new) | service | CRUD, transactional write + locks | `services/fiscal/PagamentoFaturadoService.java` | exact |
| `services/fiscal/NotaCreditoPreVisualizacaoService.java` or method in `PreVisualizacaoFaturaService` (new) | service | request-response (read-only) | `services/fiscal/PreVisualizacaoFaturaService.java` | exact |
| `services/fiscal/ComposicaoNotaCredito.java` (new, pure) | utility | transform | `services/fiscal/ComposicaoFaturaRecibo.java` + `CalculoFiscal.java` | exact |
| `services/fiscal/ProjetoNotaCredito.java` + `ResultadoNotaCredito.java` (new records) | model (record) | transform | `ProjetoFaturaRecibo.java`, `ResultadoPagamentoFaturado.java` | exact |
| `services/fiscal/ValidacaoEmissao.java` (modify, or new `ValidacaoNotaCredito`) | utility | transform | itself (`validarMetodo`/`normalizarValor`, `recusa(...)`) | exact |
| `services/fiscal/AuditoriaFiscalService.java` (modify: `registarNotaCredito`, `ACAO_EMITIR_NC`) | service | event-driven (audit) | itself `registarEmissao` `:109-117` | exact |
| `models/DocumentoFiscal.java` (modify: `pagamentoEstornoId`, `documentoOrigemId`, `motivo*`; `pagamentoId` nullable) | model | CRUD (immutable) | itself | exact |
| `models/MotivoNotaCredito.java` (new enum + `@Convert` converter) | model | transform | `models/MetodoPagamento.java`, `TipoDocumentoFiscalConverter.java` | role-match |
| `backend/migrations/135-add-nota-credito-documento-fiscal.sql` + `migrations/README.md` row | migration | batch / DDL | `migrations/134-create-documento-fiscal-tables.sql` | exact |
| `repositories/DocumentoFiscalRepository.java` (modify: new finders) | repository (narrow) | CRUD read | itself | exact |
| `repositories/PagamentoRepository.java` (unchanged expected) | repository | CRUD | itself | n/a |
| `dtos/NotaCreditoRequest.java`, `PreVisualizacaoNotaCreditoResponse.java`, `NotaCreditoResponse` (new) | dto | request-response | `PagamentoRequest.java`, `PreVisualizacaoFaturaResponse.java`, `PagamentoComDocumentoResponse.java` | exact |
| `dtos/DocumentoFiscalDetalheResponse.java`, `DocumentoFiscalResumoResponse.java` (modify: origem, motivo, creditavel, NC list) | dto | request-response | itself | exact |
| `dtos/PagamentoComDocumentoResponse.java` (modify: `estorno`) | dto | request-response | itself | exact |
| `controllers/DocumentoFiscalController.java` (modify: 2 POST handlers) | controller | request-response | itself (`preVisualizar` `:102-106`) + `ResourceController.createPagamento` `:3120-3136` for 201/200 | exact |
| `services/fiscal/DocumentoFiscalService.java` (modify: guard + refs + detalhe) | service | CRUD read | itself | exact |
| `controllers/ResourceController.java` (modify: `listHonorarioPagamentos` `:3090-3109`, `calculateMensalReceived` `:3378`; do NOT touch `registarPagamentoLegado`) | controller | CRUD | itself | exact |
| `jobs/AlertasDiariosJob.java` (no code change expected) | job | batch | itself `:269-306` | n/a |
| `config/GlobalExceptionHandler` mapping (no change; `RecusaFiscalException` already mapped) | config | request-response | `GlobalExceptionHandlerRecusaFiscalTest` | n/a |
| `web/src/hooks/use-faturacao.ts` (add `usePreVisualizacaoNotaCredito`, `useEmitirNotaCredito`) | hook | request-response | `usePreVisualizacaoFaturacao` `:210-220` + `useCreatePagamento` (`hooks/use-financeiro.ts:120-150`) | exact |
| `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/nota-credito-dialog.tsx` + `nota-credito-form.tsx` (new) | component | request-response | `financeiro/[id]/pagamento-faturado-dialog.tsx` + `pagamento-faturado-form.tsx` | exact |
| `web/src/lib/idempotencia.ts`, `lib/erros-emissao.ts` (reuse; small additions) | utility | transform | themselves | exact |
| `web/src/types/faturacao.ts`, `types/financeiro.ts` (modify) | type | n/a | themselves | exact |
| `web/src/schemas/financeiro.ts` (new NC form schema) | schema | transform | `pagamentoFaturadoFormSchema` / `paraPedidoPagamentoFaturado` | role-match |
| `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx` (modify) | component (page) | request-response | itself | exact |
| `web/src/app/(dashboard)/financeiro/[id]/pagamentos-card.tsx` (modify: estorno row) | component | request-response | itself `:93-178` | exact |
| `web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx` (filter FR+NC) | component | request-response | itself `:72,205` | exact |

---

## Pattern Assignments

### `services/fiscal/NotaCreditoService.java` (service, transactional CRUD with locks)

**Analog:** `backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java` (read in full; this is the Phase 134 current state after CR-01/WR-01/WR-05/IN-04/IN-05).

**Class skeleton / injection** (lines 91-119): `@Service @RequiredArgsConstructor @Slf4j`, constants `FUSO_CABO_VERDE`, `MOEDA`, `MSG_*`; final repos for configuracao, serie, honorario, processo, cliente, contaCorrente, pagamento, documentoFiscal, documentoFiscalLinha, comunicacaoFiscal, plus `NumeracaoService`, `ParametroFiscalService` (NC does NOT need it: IVA rate is the FR snapshot), `AuditoriaFiscalService`, `Clock`. Reuse `FUSO_CABO_VERDE` and `MOEDA` (package-private statics at `:96-98`), and the package-private `MSG_FATURACAO_OCUPADA`.

**Transaction entry and lock order** (lines 139-165, copy verbatim in spirit):
```java
@Transactional
public ResultadoPagamentoFaturado registar(UUID tenantId, UserPrincipal autor, PagamentoRequest req) {
    Objects.requireNonNull(tenantId, "tenantId");
    UUID chave = ValidacaoEmissao.exigirChave(req.chaveIdempotencia());      // before any DB access
    serieFiscalRepository.definirLockTimeoutLocal();                          // first statement: bounds ALL locks
    ConfiguracaoFiscal cfg = bloquear(() -> configuracaoFiscalRepository.bloquearPorTenant(tenantId))
            .orElseThrow(PagamentoFaturadoService::faturacaoDesligada);
    Optional<ResultadoPagamentoFaturado> repetido = repetirSeJaEmitido(tenantId, chave, req);  // idempotency under the config lock, BEFORE the `ativa` check (WR-05)
    if (repetido.isPresent()) { return repetido.get(); }
    if (!Boolean.TRUE.equals(cfg.getAtiva())) { throw faturacaoDesligada(); }
```
NC difference: the ativa-check decision. CONTEXT says "Requer faturação ativa", so keep the same order (idempotent replay first, then ativa).

**Origin document lookup replaces the honorario lookup (steps 5-7).** FR flow reads honorario unlocked, then locks cliente (`:181-182`), processo (`:185-189`), then re-checks honorario still exists (`:195-199`, CR-01 fix: `honorarioRepository.processoIdPorId(...)`). For the NC: first read the origin FR with `documentoFiscalRepository.findByIdAndTenantId(id, tenantId)` (404 `DOCUMENTO_FISCAL_NAO_ENCONTRADO` for missing or cross-tenant, same as `DocumentoFiscalService.detalhe` `:102-104`), reject `tipo != FR` with 422/409 `NC_SOBRE_NC`, then take locks from its `clienteId`/`processoId`/`honorarioId` (immutable snapshot columns; note `cliente_id` is the only one the merge repoints, so re-read the lock result and compare, as `:187-189`). Copy:
```java
Cliente cliente = bloquear(() -> clienteRepository.bloquearPorIdETenant(clienteId, tenantId))
        .orElseThrow(PagamentoFaturadoService::clienteNaoEncontrado);
Processo processo = bloquear(() -> processoRepository.bloquearPorIdETenant(processoId, tenantId))
        .orElseThrow(PagamentoFaturadoService::processoAlterado);
if (!cliente.getId().equals(processo.getClienteId())) { throw processoAlterado(); }
if (!honorarioRepository.processoIdPorId(honorarioId).map(processo.getId()::equals).orElse(false)) {
    throw honorarioNaoEncontrado();   // CR-01 pattern
}
```
Gotcha: because a merge may have repointed `cliente_id` of the FR, take the cliente id from the processo scalar (`processoRepository.clienteIdPorIdETenant`, as `:174`) rather than blindly from `doc.getClienteId()`, to avoid locking a stale cliente then failing `processoAlterado`.

**Cumulative cap under the lock (new, rule `NC_EXCEDE_ORIGINAL`).** After the config lock (which already serializes all emissions of the tenant) sum prior NC totals of the FR: add a narrow repo method `somaTotalPorDocumentoOrigem(UUID tenantId, UUID documentoOrigemId)` (see repository section). Since the config lock is held and READ COMMITTED gives a fresh snapshot per statement, the sum is race-free. Recusa as `new RecusaFiscalException(HttpStatus.CONFLICT /*or 422*/, "NC_EXCEDE_ORIGINAL", msg, "valor")`.

**Conta corrente (step 9) mirrors `:211-219` but DEBITS** (`saldo.subtract(total)`):
```java
bloquear(() -> contaCorrenteRepository.criarSeNaoExiste(clienteId));
ContaCorrente cc = bloquear(() -> contaCorrenteRepository.bloquearPorCliente(clienteId))
        .orElseThrow(() -> new IllegalStateException("Conta corrente inexistente depois do INSERT ON CONFLICT"));
BigDecimal saldo = cc.getSaldo() == null ? BigDecimal.ZERO : cc.getSaldo();
cc.setSaldo(saldo.subtract(calculo.total()));
contaCorrenteRepository.save(cc);
```
**Estorno payment (step 10)** mirrors `:222-227` with a NEGATIVE value:
```java
Pagamento estorno = pagamentoRepository.save(Pagamento.builder()
        .honorarioId(honorarioId)
        .valorPago(calculo.total().negate())
        .dataPagamento(hoje)
        .metodo(doc.getMetodoPagamento())   // copy FR method name; keep it a MetodoPagamento name for rotuloMetodoPagamento
        .build());
```
**Série (step 11) is ALWAYS the last lock**, with `TipoDocumentoFiscal.NC` and the same `DATA_EMISSAO_ALTERADA` re-check:
```java
NumeroFiscalAtribuido numero = numeracaoService.proximoNumero(tenantId, TipoDocumentoFiscal.NC, AmbienteFiscal.SIMULADO);
if (!hoje.equals(numero.dataEmissao())) {
    throw new RecusaFiscalException(HttpStatus.CONFLICT, "DATA_EMISSAO_ALTERADA", MSG_DATA_EMISSAO_ALTERADA);
}
```
**Document build (step 12)** copies the builder at `:239-279` but: `tipo(NC)`, emitente/adquirente copied FROM THE ORIGIN FR (`doc.getEmitenteNif()`, ... not from `cfg`/`cliente`; CONTEXT: "adquirente da NC é o da FR"), `pagamentoId(null)`, `pagamentoEstornoId(estorno.getId())`, `documentoOrigemId(doc.getId())`, motivo fields, `taxaIva(doc.getTaxaIva())` (snapshot, not today's rate), `taxaRetencao(doc.getTaxaRetencao())`, `chaveIdempotencia(chave)`. Sign convention to decide in planning: store NC totals as POSITIVE magnitudes (document totals mirror the FR and the cap sum is a plain SUM); the NEGATIVE sign lives only in the estorno `Pagamento`.
**Line + comunicação PENDENTE (step 13)** copy `:282-304` verbatim with NC values (description via `TextoDocumentoFiscal`, add an NC description helper there).
**Audit (step 14)** call a new `auditoriaFiscalService.registarEmissaoNotaCredito(...)` (see audit section).
**Idempotent replay** (`repetirSeJaEmitido` `:339-353`): copy the structure, but `mesmoPedido` for the NC compares `documentoOrigemId`, `valor` (`compareTo`), `motivo` and `motivoTexto`; and the replay needs the estorno payment via `pagamentoEstornoId`. Add the WR-05-style `resultadoGuardado` equivalent only if the controller needs it (it does not: NC requires active billing, the replay inside `registar` already runs before the `ativa` check).
**`bloquear` helper and exception factories** (`:385-413`): copy (they are `private`; either duplicate in `NotaCreditoService` or extract a package-private `LocksFiscais` helper; the 134 unit test pins behaviour via mocks so extraction is safe but not required).

**Imports pattern** (lines 3-50): same set minus `ParametroFiscalService`/`CodigoParametroFiscal`/`RegimeIva` unless the isento branch needs them; add `DocumentoFiscal`'s repository only (never import `DocumentoFiscalRepository` into `ResourceController`).

---

### `services/fiscal/ComposicaoNotaCredito.java` (pure utility, transform) and `ProjetoNotaCredito` record

**Analog:** `ComposicaoFaturaRecibo.java:46-84` (pure `static compor(...)`, no Spring, no clock, refusals as `RecusaFiscalException` 422 with `codigo` + `campo`) and `CalculoFiscal.java:53-81`.

**Money logic to reuse, not duplicate.** `CalculoFiscal.calcular(total, regime, taxaIvaPct, taxaRetencaoPctOuNull)` already does the single HALF_UP division with IVA as residual and retention on the base (`:72-78`). For a PARTIAL NC call it with the FR snapshot: `calcular(valorCreditar, doc.getEmitenteRegimeIva(), doc.getTaxaIva(), doc.getTaxaRetencao())`. `doc.getTaxaIva()` for ISENTO FRs is `0` (`CalculoFiscal:66`), and `calcular` ignores it for ISENTO, so passing it is safe. Retention "proportional to the FR's": the FR's `taxaRetencao` applied to the NC base gives the proportional amount. For the TOTAL NC ("credita exatamente os valores remanescentes") do NOT recompute: remaining = FR value minus the sum of prior NCs per column (`totalBase`, `totalIva`, `totalRetencao`, `totalDocumento`), so the cumulative base/IVA/retention across NCs equals the FR exactly with no rounding drift. That needs sums per column in the repo (see repository). Pitfall: the rounding of partial NCs can make `sum(NC.base) > FR.base` by 0.01 while `sum(total) <= FR.total`; the cap is on `total` per CONTEXT, but a final "total remaining" NC must use column remainders (clamp so no column goes negative).

**Validation reuse:** `ValidacaoEmissao.normalizarValor(valor)` (`:62-71`, positive, <=2 decimals, fits numeric(19,2), throws `VALOR_PAGO_INVALIDO` on `valorPago`; for NC prefer a new code/campo `VALOR_CREDITO_INVALIDO` on `valor` so the web maps it to the right field). `ValidacaoEmissao.exigirChave` for the key. Add a motivo validator in the same style as `validarMetodo` (closed enum + mandatory free text, max length matching the new `motivo_texto` column) using the private `recusa(codigo, mensagem, campo)` helper at `:56-58` (same 422 shape). Messages are Portuguese UI copy constants named `MSG_*` (`:41-55`).

**Preview shares the same compose function** (frontend burro rule, `ComposicaoFaturaRecibo` javadoc `:17-21`): the preview service and `NotaCreditoService` MUST call the same `ComposicaoNotaCredito.compor`, with the origin document + prior-credited sums + request as inputs.

---

### `services/fiscal/` NC preview (new method or class)

**Analog:** `PreVisualizacaoFaturaService.java:78-108`.

```java
@Transactional(readOnly = true)
public PreVisualizacaoFaturaResponse preVisualizar(UUID tenantId, PagamentoRequest req) {
    ConfiguracaoFiscal cfg = configuracaoFiscalRepository.findByTenantId(tenantId)
            .filter(c -> Boolean.TRUE.equals(c.getAtiva()))
            .orElseThrow(() -> new RecusaFiscalException(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA", MSG_FATURACAO_DESLIGADA));
    ...
    ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(...);
    return PreVisualizacaoFaturaResponse.de(projeto);
}
```
Copy: `readOnly`, no locks, no writes, `FATURACAO_DESLIGADA` 409 first, then `findByIdAndTenantId` 404, `NC_SOBRE_NC`, then compose and map to `PreVisualizacaoNotaCreditoResponse` (fields like `PreVisualizacaoFaturaResponse.java:19-43`: tipo `NC`, rotulo, adquirente from the origin, base/iva/retencao/total/liquido, `valorCreditavelAntes`, `valorCreditavelDepois`, `motivoRotulo`, `dataEmissao = hoje`). The preview must also return the cap error (`NC_EXCEDE_ORIGINAL`) so the user sees it before confirming (the emission re-checks under the lock). Reuse `PreVisualizacaoFaturaService.MSG_FATURACAO_DESLIGADA` (package-private static, `:46`). Use the same `FUSO_CABO_VERDE` "hoje" derivation (`:99`).

---

### `models/DocumentoFiscal.java` (model, immutable entity)

**Analog:** itself (read in full). Additions follow the existing column style, all `updatable = false`, no setters (Teste 7 in `DocumentoFiscalImutabilidadeTest` forbids public `set*`):
```java
@Column(name = "pagamento_id", updatable = false)            // was nullable=false (line 148); NC has none
private Integer pagamentoId;

@Column(name = "pagamento_estorno_id", updatable = false)
private Integer pagamentoEstornoId;

@Column(name = "documento_origem_id", updatable = false)
private UUID documentoOrigemId;

@Convert(converter = MotivoNotaCreditoConverter.class)       // enum varchar with @Convert + explicit length (P-15/WR-01 rule, see lines 61-68)
@Column(name = "motivo_codigo", length = 32, updatable = false)
private MotivoNotaCredito motivoCodigo;

@Column(name = "motivo_texto", length = 500, updatable = false)
private String motivoTexto;
```
Add to `@Table`: `@UniqueConstraint(name = "uk_documento_fiscal_estorno", columnNames = {"pagamento_estorno_id"})` and `@Index(name = "idx_documento_fiscal_tenant_origem", columnList = "tenant_id, documento_origem_id")` (the cap sum and the NC list on the FR detail query by origem). Update the class javadoc (`:12-30`, "nesta fase só a Fatura-Recibo"). `metodo_pagamento` and `meio_pagamento_codigo` are NOT NULL (`:154,157`): copy from the FR so they stay NOT NULL (no migration relaxation needed for those).

**Enum + converter analog:** `models/TipoDocumentoFiscal.java` + `TipoDocumentoFiscalConverter.java`; `models/MetodoPagamento.java` for the `porNome(...)`/`rotulo()` idiom (`MetodoPagamentoTest` pins that idiom; mirror it with a `MotivoNotaCreditoTest`). Constants per CONTEXT: Anulação total, Correção de valor, Erro nos dados do cliente, Outro; the XSD `IssueReasonCode` mapping is confirmed in 136, so keep the code in the enum as a placeholder field.

---

### `backend/migrations/135-add-nota-credito-documento-fiscal.sql` (+ README row)

**Analog:** `backend/migrations/134-create-documento-fiscal-tables.sql` (read in full). Copy the header convention (`:1-41`): REQUIRED manual script for `validate` installs, what/why/what breaks, "Deliberately NO constraint restricting enum columns", no backfill, idempotent. Statements, each guarded:
```sql
ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS pagamento_estorno_id INTEGER;
ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS documento_origem_id UUID;
ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS motivo_codigo VARCHAR(32);
ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS motivo_texto VARCHAR(500);
ALTER TABLE t_documento_fiscal ALTER COLUMN pagamento_id DROP NOT NULL;   -- idempotent by nature
-- uk_documento_fiscal_estorno: ALTER TABLE ... ADD CONSTRAINT has no IF NOT EXISTS; use a DO $$ block
-- checking pg_constraint (pattern: the DO block at 134 lines 128-156 checks the catalogue first),
-- or CREATE UNIQUE INDEX IF NOT EXISTS (but then MigracaoFiscal IT's `unicas()` query, which reads
-- information_schema.table_constraints, will not see it: prefer the DO + ADD CONSTRAINT form so the
-- constraint name matches the Hibernate @UniqueConstraint name exactly).
CREATE INDEX IF NOT EXISTS idx_documento_fiscal_tenant_origem ON t_documento_fiscal (tenant_id, documento_origem_id);
```
No new tables, so the "create tables" half of 134 does not apply. Column order does not matter to the IT (it compares a Set of per-column rows). Careful: the 134 IT compares `column_default` and `is_nullable`; the new columns have no default.

**README row (same commit):** `backend/migrations/README.md` has the inventory table (row 20 for 134 at line 183; idempotency table at `:217`; status table at `:256`; `:148` note). Add a row 21 in each of the three tables and keep the "Pending, new in this phase" wording. Rule at README "Adding a new migration": name `<phase>-<kebab>.sql`, header convention, README row in the same commit.

**IT analog:** `repositories/MigracaoFiscal134IT.java` (scaffold at `:56-104`: `@DataJpaTest(properties = "...integrator_provider=com.lexcv.repositories.CapturaMetadataHibernate")`, `@Testcontainers`, `aplicarScript()` using `SET search_path`, helpers `colunas/unicas/indices`). New `MigracaoFiscal135IT` should: (1) apply 134 then 135 into a scratch schema and assert equals the Hibernate (`public`) schema; (2) be idempotent (apply 135 twice); (3) apply 135 on a DB that holds a populated 134 FR row and assert the FR row is untouched and a second row with `pagamento_id NULL` + `pagamento_estorno_id` inserts; (4) `uk_documento_fiscal_estorno` rejects a duplicate estorno id but allows many NULLs; (5) `segundoArranqueEmUpdateNaoEmiteDdlFiscal`-style test (`:213-...`) still emits no DDL.

---

### `repositories/DocumentoFiscalRepository.java` (narrow repo)

**Analog:** itself. It extends only `Repository<DocumentoFiscal, UUID>` (`:30`), every finder takes `tenantId` first, no `@Modifying`, no `delete*/update*/remove*` prefix. New finders (derived-query names containing `TenantId` satisfy Test 8 `todoFinderRecebeTenantId`; native/`@Query` ones need `@Param("tenantId")`):
```java
List<DocumentoFiscal> findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoAscNumeroAsc(UUID tenantId, UUID documentoOrigemId);
List<DocumentoFiscal> findByTenantIdAndPagamentoEstornoIdIn(UUID tenantId, Collection<Integer> pagamentoEstornoIds);
boolean existsByTenantIdAndPagamentoEstornoId(UUID tenantId, Integer pagamentoEstornoId);
// sums for the cap / remaining columns (JPQL aggregates are fine, read-only):
@Query("select coalesce(sum(d.totalDocumento),0) from DocumentoFiscal d where d.tenantId = :tenantId and d.documentoOrigemId = :documentoOrigemId")
BigDecimal somaTotalDocumentoPorOrigem(@Param("tenantId") UUID tenantId, @Param("documentoOrigemId") UUID documentoOrigemId);
```
(or fetch the NC list and sum in Java, avoiding one repo method per column.) **The method set is pinned**: `DocumentoFiscalImutabilidadeTest.documentoFiscalRepositoryTemExatamenteOsMetodosFixados` (`:88-93`) asserts `Set.of("save","findByIdAndTenantId","findByTenantIdAndChaveIdempotencia","findByTenantIdAndPagamentoIdIn","existsByTenantIdAndPagamentoId","existsByTenantIdAndClienteId","existsByTenantIdAndProcessoId","existsByTenantIdAndHonorarioId","buscar")`. Add each new name to that set deliberately (do not relax the check). The `buscar` native query (`:56-80`) already filters by `d.tipo`, so FR+NC listing works with no SQL change; `estado` LEFT JOIN works because the NC also gets a `t_comunicacao_fiscal` row.
The merge repoint (`DocumentoFiscalLigacaoClienteRepository`, SQL pinned in Test 4 at `:104-115`) is unaffected: NCs carry `cliente_id` and are repointed by the same UPDATE. Do not edit that SQL string.
`DocumentoFiscalRepositoryIT` (13 tests) needs new cases for the new finders (tenant isolation + the unique estorno index).

---

### `services/fiscal/DocumentoFiscalService.java` (read side + guard)

**Analog:** itself.
- **Guard (D-14)** `existeParaPagamento` (`:131-135`): extend to `existsByTenantIdAndPagamentoId(...) || existsByTenantIdAndPagamentoEstornoId(...)`. Unit test `DocumentoFiscalServiceTest.java:238-247` pins current behaviour with Mockito stubs: with strict stubs an un-stubbed `existsByTenantIdAndPagamentoEstornoId` returns `false`, so those tests keep passing; add tests for the estorno branch.
- **`referenciasPorPagamento`** (`:119-129`) feeds the payments list; for estornos add a second map `estornosPorPagamento(tenantId, ids)` using `findByTenantIdAndPagamentoEstornoIdIn` (one extra batched query, no N+1), return `Map<Integer, DocumentoFiscalRef>` and let the controller put it in `PagamentoComDocumentoResponse.estorno`.
- **`detalhe`** (`:100-112`): for an FR also load its NCs (`findByTenantIdAndDocumentoOrigemId...`) and compute `valorCreditavel = totalDocumento - sum(NC.total)`; for an NC load the origin FR ref (`DocumentoFiscalRef`). Keep 404 behaviour unchanged.
- `existeParaHonorario/Cliente/Processo` (`:138-153`) already cover NCs because the NC copies those ids.

---

### `dtos/` (new + modified)

**`NotaCreditoRequest`** analog `PagamentoRequest.java:` record, no tenant/id fields (mass-assignment safe by construction), fields: `String tipo` ("TOTAL"|"PARCIAL") or derive from `valor == null`, `BigDecimal valor`, `String motivoCodigo` (enum name), `String motivoTexto`, `UUID chaveIdempotencia`. Document-origin id comes from the PATH, never the body.
**`PreVisualizacaoNotaCreditoResponse`** analog `PreVisualizacaoFaturaResponse.java:19-73` (record + `static de(Projeto)`).
**`NotaCreditoResponse`** analog `PagamentoComDocumentoResponse.java:17-35` (superset of the Pagamento JSON + `DocumentoFiscalRef`), plus `ResultadoNotaCredito.novo/repetido` like `ResultadoPagamentoFaturado` (201 vs 200).
**`DocumentoFiscalDetalheResponse.java`** (`:22-63`, factory `:91-135`): add `documentoOrigem` (`DocumentoFiscalRef`), `motivoCodigo/motivoRotulo/motivoTexto`, `pagamentoEstornoId`, `valorCreditavel`, `notasCredito` (List of ref+total+data) and make `pagamentoId` nullable. Note the compact constructor copies `linhas` with `List.copyOf` (EI_EXPOSE_REP idiom `:66-68`); copy the same for the new list. Positional record constructor means every caller/test constructing it (`DocumentoFiscalServiceTest`, `DocumentoFiscalControllerTest`) must be updated.
**`DocumentoFiscalResumoResponse`**: no field change required (tipo/tipoRotulo already present); optionally add `documentoOrigemNumero` for the list.
**`PagamentoComDocumentoResponse.java`** (`:17-35`): add `DocumentoFiscalRef estorno` as the last component and a second `de(pagamento, doc, estorno)` factory. Existing callers: `PagamentoFaturadoService:311` and `:352`, `ResourceController:3107`; keep the 2-arg factory delegating with `estorno=null` to avoid touching the CFG-03 text. `ResourceControllerListaPagamentosTest` pins the JSON shape; extend it.

---

### `controllers/DocumentoFiscalController.java` (controller, request-response)

**Analog:** itself, `preVisualizar` (`:102-106`) and `detalhe` (`:172-183`) for id parsing; `ResourceController.createPagamento` (`:3120-3136`) for the 201/200 split.

```java
@PreAuthorize("hasAuthority('financeiro:manage')")
@PostMapping("/documentos-fiscais/{id}/notas-credito/pre-visualizacao")
public ResponseEntity<?> preVisualizarNotaCredito(@PathVariable String id, @RequestBody NotaCreditoRequest req) {
    return ResponseEntity.ok(notaCreditoService.preVisualizar(getTenantId(), parseId(id), req));
}

@PreAuthorize("hasAuthority('financeiro:manage')")
@PostMapping("/documentos-fiscais/{id}/notas-credito")
public ResponseEntity<?> emitirNotaCredito(@PathVariable String id, @RequestBody NotaCreditoRequest req) {
    ResultadoNotaCredito r = notaCreditoService.emitir(getTenantId(), getPrincipal(), parseId(id), req);
    return ResponseEntity.status(r.novo() ? HttpStatus.CREATED : HttpStatus.OK).body(r.resposta());
}
```
Id parsing: a non-UUID id gives 404 `DOCUMENTO_FISCAL_NAO_ENCONTRADO` exactly as `detalhe` `:174-181` (extract that block into a private helper). Tenant only from `getTenantId()` (`:92-94`); the author from `getPrincipal()` (`:87-90`; `ResourceController` uses `principalAtual()`). No `@Transactional` on the controller (class javadoc `:63-66`); the service owns the transaction.
**Pinned structure that WILL fail and must be updated deliberately:**
- `DocumentoFiscalControllerTest.exatamenteQuatroHandlersSemRotasQueAlterem` (`:267-290`): asserts `hs.size() == 4`, path/method per handler, and no PUT/PATCH/DELETE/RequestMapping on methods. Update to 6 handlers (name changes: `exatamenteSeisHandlers...`), add the two new path assertions (`PostMapping.value()[0]`).
- `DocumentoFiscalControllerTest.gatesPorMetodoSemGateDeClasse` (`:293-...`): add `financeiro:manage` assertions for the two new handlers (exact string `hasAuthority('financeiro:manage')`), keep "no class-level gate".
- `DocumentoFiscalControllerAutorizacaoTest` (proxy with `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()`, `:72-75`): add allow/deny cases (manage allowed; edit-only and view-only get `AccessDeniedException`; remember the backend checks the EXACT authority, no `manage => edit` fallback).
- `DocumentoFiscalImutabilidadeTest.nenhumControladorAlteraOuApagaDocumentosFiscais` (Test 10, `:178-...`): only blocks PUT/PATCH/DELETE on `documentos-fiscais` paths; POST is allowed, so the new handlers pass. Do not add DELETE/PUT.
- Controller constructor changes (new `NotaCreditoService` field): update the mock set in `DocumentoFiscalControllerTest` (`@Mock`/`@InjectMocks` or manual construction; check how `controller` is built there).

**Class javadoc** (`:30-66`) says "nao ha rotas para ... emitir um documento para um pagamento ja registado" and "pre-visualizacao exige edit"; update it to describe the NC endpoints and the `financeiro:manage` gate.

---

### `controllers/ResourceController.java` (modify minimally)

**1. `listHonorarioPagamentos`** (`:3090-3109`): add the estorno refs:
```java
Map<Integer, DocumentoFiscalRef> refs = documentoFiscalService.referenciasPorPagamento(getTenantId(), ids);
Map<Integer, DocumentoFiscalRef> estornos = documentoFiscalService.referenciasPorEstorno(getTenantId(), ids);   // new
... .map(p -> PagamentoComDocumentoResponse.de(p, refs.get(p.getId()), estornos.get(p.getId())))
```
**2. `deletePagamento`** (`:3271-3317`): the guard at `:3290-3294` is `if (documentoFiscalService.existeParaPagamento(getTenantId(), id)) -> 409 {message, code:"PAGAMENTO_FATURADO"}`. With the service-side extension (finding 5) the controller needs no change; if an estorno-specific message/code is wanted, add a second `if` right after it with the same shape and a new test next to `ResourceControllerDocumentoFiscalGuardasTest.apagarPagamentoFaturadoDevolve409SemTocarNaContaCorrente` (`:132-146`). Keep the handler WITHOUT `@Transactional` (comment `:3286-3289`, P-02).
**3. `calculateMensalReceived`** (`:3378-3396`) fix, current buggy line `:3388`:
```java
if (pag.getDataPagamento() != null && pag.getDataPagamento().getMonthValue() == LocalDate.now().getMonthValue()) {
    total = total.add(pag.getValorPago());
```
Replace with a `YearMonth` compare in `Atlantic/Cape_Verde` (`YearMonth.from(pag.getDataPagamento()).equals(YearMonth.now(ZONA_CABO_VERDE))`) and `pag.getValorPago() != null`. The estorno has `dataPagamento = hoje` (CV), so it lands in the NC emission month, as decided (cash view). The loop is an N+1 over processos/honorarios/pagamentos; optional but out of scope. Imports: add `java.time.YearMonth`/`ZoneId` if not present (check existing imports, the file is ~3500 lines; use Grep before editing). Remember finding 4 (no ASCII `faturacao`, no forbidden tokens).

---

### `services/fiscal/AuditoriaFiscalService.java` (audit)

**Analog:** itself `registarEmissao` (`:105-117`) and constants (`:48-56`).
```java
public static final String ACAO_EMITIR_NC = "documento_fiscal_emitir_nc";
@Transactional(propagation = Propagation.MANDATORY)
public void registarEmissaoNotaCredito(UUID tenantId, UserPrincipal autor, UUID documentoId,
                                       String numeroFormatado, String numeroOrigem) {
    Map<String, Object> detalhe = new LinkedHashMap<>();
    put(detalhe, "autorNome", nomeDoAutor(autor));
    put(detalhe, "numeroFormatado", numeroFormatado);
    put(detalhe, "documentoOrigem", numeroOrigem);
    gravar(tenantId, autor, ACAO_EMITIR_NC, ENTIDADE_TIPO_DOCUMENTO, documentoId == null ? null : documentoId.toString(), detalhe);
}
```
Privacy rules (javadoc `:31-33`): only the author display name and formatted numbers, never NIF/address/email, and NEVER the free-text motivo (could hold client data). `AuditoriaFiscalServiceTest` has a source-code gate that forbids writing the email getter's literal name (see comment `:121-123`); avoid it in new code/comments. Add tests mirroring the existing `registarEmissao` ones. Do not add the new action to the RBAC audit read path (`AuditLogRepository.buscarEventosRbac` filters by entity type; `documento_fiscal` is already outside it).

---

### Web: `hooks/use-faturacao.ts` (hooks)

**Analog:** `usePreVisualizacaoFaturacao` (`:210-220`, mutation + `STATUS_INLINE_EMISSAO` so 409/422/5xx are inline, no toast) and `useCreatePagamento` (`hooks/use-financeiro.ts:120-150`; `semToastParaStatus: payload.chaveIdempotencia ? STATUS_INLINE_EMISSAO : [409, 422]`; invalidation in `onSettled`, because an error can also mean stale state).
```ts
export function useEmitirNotaCredito(documentoId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: NotaCreditoRequest) =>
      apiFetch<NotaCreditoResponse>(`/documentos-fiscais/${encodeURIComponent(documentoId)}/notas-credito`,
        { method: "POST", body: JSON.stringify(payload) }, { semToastParaStatus: STATUS_INLINE_EMISSAO }),
    onSettled: async () => { await Promise.all([
      queryClient.invalidateQueries({ queryKey: DOCUMENTOS_FISCAIS_KEY }),            // covers list + detail of FR and NC
      queryClient.invalidateQueries({ queryKey: ["honorarios", "pagamentos"] }),      // payments list (estorno row)
      queryClient.invalidateQueries({ queryKey: ["honorarios", "detail"] }),          // totalPago
      queryClient.invalidateQueries({ queryKey: ["honorarios", "list"] }),
      queryClient.invalidateQueries({ queryKey: ["clientes", "conta-corrente"] }),    // saldo
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),                     // KPI mensal: verify actual key with Grep in hooks before use
    ]); },
  });
}
```
Pre-visualization hook mirrors `usePreVisualizacaoFaturacao`. The detail query key is `[...DOCUMENTOS_FISCAIS_KEY, "detail", id]` (`:240`), so invalidating `DOCUMENTOS_FISCAIS_KEY` refreshes the FR's NC list/creditable value. Permission helper: add `podeEmitirNotaCredito(permissions)` = exact `hasPermission(permissions, "financeiro:manage")` next to `podeRegistarPagamentos` (`:187-189`, same WR-04 lesson: backend checks exact authority, so no `hasScopedPermission` fallback; CONTEXT says "frontend hasScopedPermission exato" - treat that as the exact check, which `hasPermission` is; confirm against `lib/permissions.ts`).

### Web: `nota-credito-form.tsx` / `nota-credito-dialog.tsx` (new, idempotency key lifecycle)

**Analog:** `app/(dashboard)/financeiro/[id]/pagamento-faturado-form.tsx` (orchestration) and `pagamento-faturado-dialog.tsx` (pure presentational confirm dialog).

**Key lifecycle helpers to reuse unchanged** (`lib/idempotencia.ts`): `tentativaParaPedido(anterior, pedido)` (`:62-71`, reuse key only if `porResolver` and the canonical payload is identical), `marcarPorResolver(tentativa)` (`:74-76`), `pedidoCanonico` (`:44-56`), `gerarChaveIdempotencia` (`:18-27`); and from `lib/erros-emissao.ts`: `desfechoDefinitivo(e)` (`:134-139`), `interpretarErroEmissao(e)` (`:94-125`). The payload fed to `tentativaParaPedido` MUST include the origin document id (the key belongs to the content: `{ documentoOrigemId, tipo, valor, motivoCodigo, motivoTexto }`), otherwise the same key could be reused across different FRs.

Form-state skeleton to copy (`pagamento-faturado-form.tsx:60-72` state, `:93-127` `onSubmit`, `:129-158` `emitir`):
```tsx
const [tentativa, setTentativa] = React.useState<TentativaEmissao | null>(null);
const emitindoRef = React.useRef(false);                       // sync double-click guard
const onSubmit = async (values) => {
  const novoPedido = paraPedidoNotaCredito(values, documentoId);
  try {
    const resposta = await preVisualizar.mutateAsync(novoPedido);
    setTentativa((anterior) => tentativaParaPedido(anterior, novoPedido));   // CR-02: payload-bound key
    setDialogoAberto(true);
  } catch (e) { mostrarErro(interpretarErroEmissao(e)); }
};
const emitir = async () => {
  if (emitindoRef.current || !pedido || !tentativa) return;
  emitindoRef.current = true; setEmitindo(true);
  try {
    await emitirNC.mutateAsync({ ...pedido, chaveIdempotencia: tentativa.chave });
    setTentativa(null); /* close, reset, toast */
  } catch (e) {
    setTentativa((atual) => (desfechoDefinitivo(e) ? null : marcarPorResolver(atual)));
    const erro = interpretarErroEmissao(e);
    if (erro?.tipo === "rede") setErroRede(erro.mensagem);   // dialog stays open, same key on retry
    else { fecharDialogo(); mostrarErro(erro); }
  } finally { emitindoRef.current = false; setEmitindo(false); }
};
```
`fecharDialogo` must NOT clear `tentativa` (comment at `:55-60`/`:89-93`). Known remaining gap from the 134 Fix Report: the key lives in component state and is lost if the user navigates away after an ambiguous failure; for the NC, mounting the form inside the FR detail page (not a separate route) keeps the state while the page is open; the refetched FR detail (`valorCreditavel`/NC list via `onSettled`) is the signal. Do not claim more.

Dialog analog points (`pagamento-faturado-dialog.tsx`): `Dialog` with `open={open && p !== null}`, `onOpenChange` blocked while `emitindo` (`:62-67`), `bloquearFecho` for Esc/overlay (`:57-59,74-76`), autofocus on the title not the confirm button (`:69-73`), simulated-document `NOTICE_CLASSES` + `Badge` "Simulação — sem validade fiscal" (`:20-21,112-124`), values only from the preview response (no money math in the client), `formatarCVE`/`formatarData` helpers (`:23-33`, duplicated in several files; copy, or import if extracted). NC dialog texts: "Confirmar nota de crédito", "Emitir nota de crédito", show "Valor a creditar", base/IVA/retenção from the preview, `Valor ainda creditável depois: ...`, and "A conta corrente do cliente é debitada do total" (reverse of `:152-154`). Selector for TOTAL/PARCIAL, motivo select via `NativeSelect` (as `pagamento-faturado-form.tsx` `METODOS_PAGAMENTO` select), mandatory textarea.

**Schema analog:** `schemas/financeiro.ts` `pagamentoFaturadoFormSchema` / `paraPedidoPagamentoFaturado` / `METODOS_PAGAMENTO` / `rotuloMetodoPagamento` (see `financeiro.test.ts` for the test style). Add `MOTIVOS_NOTA_CREDITO`, `notaCreditoFormSchema` (valor required and > 0 only when PARCIAL; motivo texto required, max length same as backend), `paraPedidoNotaCredito`. Tests: extend `schemas/financeiro.test.ts`; `lib/idempotencia.test.ts` and `lib/erros-emissao.test.ts` need only additions (new codes), the existing assertions stay valid.

### Web: `lib/erros-emissao.ts` (additions)

**Analog:** itself. Add copy constants for `NC_EXCEDE_ORIGINAL`, `NC_SOBRE_NC`, motivo errors, and extend `CampoFormularioEmissao` (`:60`) with `"valor"`, `"motivoTexto"` so `interpretarErroEmissao` (`:117-119`, 422 + campo) maps them inline. `erros-emissao.test.ts` has table-driven cases for existing codes; keep them green and add NC ones. `STATUS_INLINE_EMISSAO` (`:34`) is reused as is. `types/faturacao.ts` `CodigoErroFaturacao` (`:65-91`): add the new codes.

### Web: `documentos-fiscais/[id]/page.tsx` (detail)

**Analog:** itself (358 lines). Structure: `usePermissions()` + `podeLerDocumentosFiscais` gate at `:62-77`; breadcrumb header `:85-110`; cards Emitente/Adquirente/Linha/Valores/Ligações (see offsets `+84..+226` from line 120 in the earlier grep: Valores ~`:277-310`, Ligações ~`:323-350`, where `Link` to `/financeiro/${honorarioId}#pagamento-${pagamentoId}` is built). Changes:
- Header actions: next to "Voltar aos documentos fiscais" add `{podeEmitirNC && d.tipo === "FR" && d.valorCreditavel > 0 ? <NotaCreditoForm .../> : null}` (button "Emitir Nota de Crédito" opening the form/dialog).
- New card "Notas de crédito" (FR only): table of `d.notasCredito` (link, date, total, motivo) + "Valor ainda creditável".
- "Ligações" card: for an NC show a link "Fatura-recibo original" to `/financeiro/documentos-fiscais/${d.documentoOrigem.id}`, and guard the existing pagamento link for `pagamentoId == null` (an NC has `pagamentoEstornoId` instead; the `#pagamento-<id>` anchor exists in `pagamentos-card.tsx:120`, `id={`pagamento-${p.id}`}`, so link the estorno id to the honorario page the same way).
- Show motivo (rotulo + texto) for an NC in a "Motivo" block. The header comment says "Este ecrã não tem nenhuma ação que altere o documento" (`:29-31`): update it (the NC is a new document, not an alteration). Types: `DocumentoFiscalDetalhe.pagamentoId: number` -> `number | null`, add the new fields.
- `scripts/verify-documentos-fiscais.mjs` is a source-text "prova executável" of the 134 UI (updated for CR-02/IN-02 in the fix); it greps for strings in these files. After changing the page/columns/filter, run `pnpm verify:documentos-fiscais` and `pnpm verify:faturacao`, and update the expectations deliberately (e.g. the type filter options, "sem ação que altere" assertions) rather than loosening them. Read the script before editing (`web/scripts/verify-documentos-fiscais.mjs`, not read in this mapping beyond its header).

### Web: `financeiro/[id]/pagamentos-card.tsx` (estorno row)

**Analog:** itself. Row render `:116-146`; the action cell `:140-148` currently reads `!canManage ? null : p.documentoFiscal ? <span>Pagamento faturado...</span> : <ApagarPagamento/>`. Add the estorno branch BEFORE the delete branch: `p.estorno ? <span className="text-xs ...">Estorno: não pode ser apagado.</span>`. In the "Documento fiscal" cell (`:127-139`) show `Estorno (NC n.º {p.estorno.numeroFormatado})` as a `Link` to `/financeiro/documentos-fiscais/{p.estorno.id}`; the value cell `formatMoneyCVE(p.valorPago)` already formats negatives. `types/financeiro.ts` `Pagamento` (`:25-33`): add `estorno?: DocumentoFiscalRef | null`. Method column `rotuloMetodoPagamento(p.metodo)` still works because the estorno copies the FR method name. Guard `mensagemGuardaFiscal` (`lib/erros-emissao.ts:144-150`) handles 409 + code; add the estorno code to `COPY_GUARDA_POR_CODIGO` if a separate code is chosen.

### Web: documentos fiscais list (filter FR + NC)

`app/(dashboard)/financeiro/documentos-fiscais/page.tsx:72` currently `searchParams.get("tipo") === "FR" ? "FR" : ""`, and the select at `:197-205` has only `<NativeSelectOption value="FR">`. Change to accept `"FR" | "NC"` and add `<NativeSelectOption value="NC">Nota de Crédito</NativeSelectOption>`. The backend list endpoint already accepts any `TipoDocumentoFiscal` name (`DocumentoFiscalController:148-153`, `MSG_TIPO` only on invalid), and `columns.tsx:58-63` renders `tipoRotulo` in a badge, so no column change is strictly needed (optionally show a negative/"NC" styling for the total).

---

## Existing tests that pin structure and need DELIBERATE updates

| Test file | What it pins | Required deliberate change |
|-----------|--------------|----------------------------|
| `repositories/DocumentoFiscalImutabilidadeTest` | Test 2: exact method-name set of `DocumentoFiscalRepository` (`:88-93`); Test 7: no public setters on `@Immutable` entities (`:164-...`); Test 8: every finder has `tenantId`; Test 9: no source contains `delete from t_documento_fiscal` / `update t_documento_fiscal_linha` / `update t_documento_fiscal` outside the ligação repo (`:195-212`) | Add the new finder names to the set; new finders must carry `tenantId`; the new native SQL must not contain those strings. |
| `repositories/MigracaoFiscal134IT` | column count `40 + 14 + 10` and Hibernate == 134-script schema (`:162-163`), unique map (`:166-175`), idempotence (`:186-195`), `segundoArranqueEmUpdateNaoEmiteDdlFiscal` (`:213-...`) | Apply 134 + 135 in the scaffold (or split expectations), bump counts (+3 columns: `pagamento_estorno_id`, `documento_origem_id`, `motivo_codigo`, `motivo_texto` = +4, so `44 + 14 + 10`; recount before editing), add `uk_documento_fiscal_estorno` and `idx_documento_fiscal_tenant_origem`; `is_nullable` for `pagamento_id` flips to YES. Also `unicidadesPorTenant` (`:317-...`) and the `inserirDocumento` helper (`:302-316`) insert FR rows with explicit columns; they stay valid because new columns are nullable. |
| `controllers/DocumentoFiscalControllerTest` | exactly 4 handlers + path assertions (`:267-290`), gate strings per handler | 6 handlers, 2 new `manage` gates, new constructor mock. |
| `controllers/DocumentoFiscalControllerAutorizacaoTest` | method-security proxy per endpoint (`:72-75`, cases from `:120-233`) | add allow/deny cases for the two new handlers. |
| `controllers/FaturacaoDesligadaPagamentoInalteradoTest` (CFG-03) | SHA of `registarPagamentoLegado` body (`:57,148-153`); `faturacao` token count == 1 (`:136-...`); forbidden tokens in `ResourceController` and `Pagamento`; `createPagamento` gate `hasAuthority('financeiro:edit')` and neither method `@Transactional` | Must NOT change. Verify it still passes after the `ResourceController` edits (re-run the test; if it fails, the edit was wrong, not the hash). Do NOT regenerate `HASH_CORPO_LEGADO`. |
| `controllers/ResourceControllerDocumentoFiscalGuardasTest` | `deletePagamento` guard mocks `existeParaPagamento` (`:132-205`) | unchanged if the guard is extended inside the service; add new cases for the estorno. |
| `controllers/ResourceControllerListaPagamentosTest` | JSON shape of `/honorarios/{id}/pagamentos` | extend for the `estorno` field (new mock `referenciasPorEstorno`). |
| `controllers/ResourceControllerPagamentoTest`, `ResourceControllerProveniencaPapelTest`, `ResourceControllerUploadDocumentoTest` | construct `ResourceController` with Mockito mocks | unchanged if no new constructor dependency is added to `ResourceController` (do not add `Clock`). |
| `services/fiscal/DocumentoFiscalServiceTest` | `existeParaPagamento` stubs (`:238-247`), `detalhe` mapping | add estorno branch + detalhe NC cases; update `DocumentoFiscalDetalheResponse` constructor usage. |
| `services/fiscal/PagamentoFaturadoServiceTest` / `PagamentoFaturadoServiceIT` / `PagamentoFaturadoConcorrenciaIT` | `InOrder` of locks (`:232-257`) and builder usage of `DocumentoFiscal`/`PagamentoComDocumentoResponse` | unchanged unless the shared helpers are refactored; if `repetirSeJaEmitido` gains the `tipo != FR` guard, add a test there. |
| `services/fiscal/AuditoriaFiscalServiceTest` | source-gate on forbidden getter name, event shape | add the NC event tests. |
| `services/fiscal/FixturaEmissaoFiscal` | JDBC fixture (`criarTenantComFaturacao`, `criarCliente`, `criarProcesso`, `criarHonorario`, `criarContaCorrente`, `contarPagamentos`, `contarDocumentos`, `saldo`, `numerosEmitidos`, ...) | extend, do not fork: add `emitirFr(...)`/`contarNotasCredito`, `totalPagoHonorario(honorarioId)` for the coherence test. |
| `jobs/AlertasDiariosJobTest` | builds `Honorario` with explicit `totalPago` (`:296-341`) | add a case: partially credited honorário (`totalPago < valorTotal` after NC) fires `HONORARIO_ATRASADO` again; no code change expected in the job. |
| `config/GlobalExceptionHandlerRecusaFiscalTest` | `RecusaFiscalException` -> `{message, code, campo}` | unchanged; new codes ride the same handler. |
| web `lib/idempotencia.test.ts`, `lib/erros-emissao.test.ts`, `schemas/financeiro.test.ts` | key lifecycle, error mapping, form schema | stay green; add NC cases. |
| web `scripts/verify-documentos-fiscais.mjs`, `scripts/verify-faturacao.mjs` | source-level UI contracts | re-run, update expectations deliberately (type filter, detail page "no action" copy, payments-card). |

New tests to create (patterns): `NotaCreditoServiceTest` (copy structure of `PagamentoFaturadoServiceTest`: Mockito, `InOrder` over `serieRepo.definirLockTimeoutLocal -> configuracaoRepo.bloquearPorTenant -> documentoRepo.findByTenantIdAndChaveIdempotencia -> ... -> clienteRepo.bloquearPorIdETenant -> processoRepo.bloquearPorIdETenant -> honorarioRepo.processoIdPorId -> ccRepo.criarSeNaoExiste -> ccRepo.bloquearPorCliente -> pagamentoRepo.save -> numeracao.proximoNumero(NC) -> documentoRepo.save -> linhaRepo.save -> comunicacaoRepo.save -> auditoria`), `NotaCreditoServiceIT` (copy `PagamentoFaturadoServiceIT`: `@DataJpaTest` + `Replace.NONE` + `@ServiceConnection` + `@Import({...})` + `Propagation.NOT_SUPPORTED` + fixed `Clock` `2026-06-15T13:00:00Z` at `:70-76`; include the coherence test "after total and partial NC: `ContaCorrente.saldo`, `Honorario.totalPago` @Formula, monthly KPI contribution and the `HONORARIO_ATRASADO` condition agree"), `NotaCreditoConcorrenciaIT` (copy `PagamentoFaturadoConcorrenciaIT`: 8 threads, latch, two simultaneous NCs on one FR must not exceed the cap; same key twice -> one NC; NC vs `deleteHonorario`; no 40P01), `ComposicaoNotaCreditoTest` (copy `ComposicaoFaturaReciboTest`/`CalculoFiscalTest` style, including the 120 000 reference example partial/total and cumulative remainder), `MigracaoFiscal135IT`, `ResourceControllerDashboardKpiTest`.

---

## Shared Patterns

### Locks, timeouts and 503 mapping
**Source:** `PagamentoFaturadoService.java:148,151,386-393` and `NumeracaoService.java` (série lock last, `proximoNumero` is `@Transactional(MANDATORY)` with its own 503 `SERIE_INDISPONIVEL`).
**Apply to:** `NotaCreditoService`. Order: `definirLockTimeoutLocal` -> config -> (origin FR read, scalar) -> cliente -> processo -> honorário re-check -> conta corrente (`criarSeNaoExiste` wrapped in `bloquear` per IN-04, then `bloquearPorCliente`) -> série. Rule (OSIV): each lock must be the FIRST read of its row in the transaction; never `findById`/`findByClienteId` for a row you are about to lock (the 134 unit test `caminhoAtivoNuncaLeSemLockAsLinhasQueBloqueia` `:260-269` enforces `never()` on `clienteRepo.findById`, `processoRepo.findById`, `configuracaoRepo.findByTenantId`, `ccRepo.findByClienteId`; replicate for the NC). A DB constraint violation inside the transaction is never caught (the Postgres txn is already aborted); the global handler answers 500 with reference.

### RecusaFiscalException (error contract)
**Source:** `exceptions/RecusaFiscalException.java:19-48`; used as `new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "CODIGO", MSG, "campo")`.
**Apply to:** all NC refusals. Proposed codes: 409 `NC_EXCEDE_ORIGINAL`; 409/422 `NC_SOBRE_NC`; 422 `VALOR_CREDITO_INVALIDO` (campo `valor`); 422 `MOTIVO_NC_INVALIDO` / `MOTIVO_TEXTO_OBRIGATORIO` (campos `motivoCodigo`/`motivoTexto`); 404 `DOCUMENTO_FISCAL_NAO_ENCONTRADO` (also cross-tenant); 409 `FATURACAO_DESLIGADA`; 422 `CHAVE_IDEMPOTENCIA_OBRIGATORIA`; 409 `CHAVE_REUTILIZADA`; 503 `FATURACAO_OCUPADA`/`SERIE_INDISPONIVEL`. Messages in Portuguese, safe for the client, never a library message. Thrown inside `@Transactional` so rollback is automatic and leaves no audit event.

### Tenant isolation
**Source:** `DocumentoFiscalController.getTenantId()` `:92-94`; every finder tenant-first; service methods take `tenantId` as first param and never read `SecurityContextHolder` (`DocumentoFiscalService` javadoc `:39-40`). Cross-tenant origin = same 404 as non-existent (no oracle).

### Idempotency (backend)
**Source:** `PagamentoFaturadoService.repetirSeJaEmitido` `:339-353`, `mesmoPedido` `:360-383`; `UNIQUE(tenant_id, chave_idempotencia)` is shared across FR and NC (one key space per tenant).

### Authorization
**Source:** `DocumentoFiscalController` `@PreAuthorize("hasAuthority('financeiro:edit')")` per method (`:102`); `FaturacaoController` uses a class-level `financeiro:manage` gate and is pinned at 7 handlers (do NOT add the NC endpoints there). Frontend mirrors with exact-authority helpers in `hooks/use-faturacao.ts:176-189` (`hasPermission`, not the fallback `hasScopedPermission`), so UI and backend agree.

### Immutability / merge
`DocumentoFiscal`/`DocumentoFiscalLinha` are `@Immutable`, builder-only, no setters; the only mutation is the native `cliente_id` repoint on merge. NC rows inherit this. `ComunicacaoFiscal` is the mutable satellite; the NC creates its `PENDENTE` row exactly as `PagamentoFaturadoService.java:297-304`.

### Web error/key conventions
`apiFetch` does NOT toast 401/403 (CLAUDE.md); `interpretarErroEmissao` maps 403 to a banner (`erros-emissao.ts:101-102`). Mutations that are handled inline pass `semToastParaStatus: STATUS_INLINE_EMISSAO`. Invalidate in `onSettled`, not `onSuccess`.

---

## No Analog Found / Partial Analogs

| File / Concern | Role | Data Flow | Reason |
|----------------|------|-----------|--------|
| Cumulative cap sum (`NC_EXCEDE_ORIGINAL`) under the config lock | service rule | aggregate read | No existing aggregate query on `t_documento_fiscal`; use the JPQL `sum` / list-then-sum pattern shown above; correctness argument comes from the config lock (serializes all emissions of the tenant) + READ COMMITTED per-statement snapshots, same argument as the idempotency lookup in 134 (`PagamentoFaturadoService` javadoc `:75-84`). |
| `ALTER COLUMN ... DROP NOT NULL` + `ADD CONSTRAINT` idempotent guard | migration | DDL | 134 only has `CREATE ... IF NOT EXISTS` and one catalogue-check `DO` block (`:128-156`); use that block as the template for the constraint guard. Other migrations (`81`, `82`, `88`, `91`) add unique constraints; check one (e.g. `backend/migrations/81-add-facto-ordem-unique-constraint.sql`) for the established idempotent `ADD CONSTRAINT` idiom before writing. |
| Dashboard KPI unit test | test | n/a | No existing test of `getDashboard`/`calculateMensalReceived`; build from `ResourceControllerListaPagamentosTest` scaffolding (Mockito controller with `SecurityContext` principal). |

## Notes on items judged "no change"
- `Honorario.totalPago` `@Formula` (`models/Honorario.java:34`) and `AlertasDiariosJob.processarHonorarios` (`:276-279`: `getTotalPago() >= getValorTotal()` -> skip) already treat a negative payment correctly. Only a test is needed. `honorarioRepository.findByProcessoIdIn(...)` loads fresh entities each run, so the formula is evaluated at load.
- `registarPagamentoLegado` and `createPagamento` stay untouched; the NC estorno is never created through `POST /pagamentos` (`PagamentoRequest.paraPagamentoLegado()` has no way to produce a negative: `registarPagamentoLegado` rejects `valorPago <= 0` at `:3154`, a useful invariant to cite in the test "POST /pagamentos never creates an estorno").
- `deleteHonorario` guard (`:3258-3262`) already blocks on any document with that `honorario_id`, so an NC also blocks it.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{services/fiscal,models,repositories,controllers,dtos,jobs,exceptions}`, `backend/src/test/java/com/lexcv/{services/fiscal,controllers,repositories,jobs}`, `backend/migrations`, `web/src/{hooks,lib,types,schemas,app/(dashboard)/financeiro}`, `web/scripts`.
**Files read in full or by targeted range:** ~40 (PagamentoFaturadoService, DocumentoFiscal, 134 migration, DocumentoFiscalController, PreVisualizacaoFaturaService, CalculoFiscal, ComposicaoFaturaRecibo, DocumentoFiscalService, DocumentoFiscalRepository, three DTOs, AuditoriaFiscalService, ValidacaoEmissao, NumeracaoService, ContaCorrenteRepository, ResourceController ranges 3085-3140/3236-3420, AlertasDiariosJob 240-310, 5 test files, 8 web files).
**Not read (planner should read before editing):** `ResourceController` imports block, `web/scripts/verify-documentos-fiscais.mjs` body, `web/src/lib/permissions.ts`, `web/src/schemas/financeiro.ts`, `DocumentoFiscalControllerTest` constructor setup, `DocumentoFiscalResumoResponse`, `MetodoPagamento`/converters, `migrations/81-*.sql`.
**Pattern extraction date:** 2026-10-04
