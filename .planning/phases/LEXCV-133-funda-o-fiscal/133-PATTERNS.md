# Phase 133: Fundação Fiscal - Pattern Map

**Mapped:** 2026-10-04
**Files analyzed:** 22 (new/modified)
**Analogs found:** 20 / 22 (2 partial: Clock bean, DatabaseSeeder demo tenant NIF edit is in-place)

Paths below are relative to `/home/user/lexcv/`. Backend root: `backend/src/main/java/com/lexcv/` (abbrev. `BE/`), tests `backend/src/test/java/com/lexcv/` (`BT/`), frontend `web/src/` (`FE/`).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `BE/models/ConfiguracaoFiscal.java` | model | CRUD (1:1 tenant) | `BE/models/NotificacaoPreferencia.java` (+ `Tenant`) | role-match |
| `BE/models/ParametroFiscal.java` | model | CRUD (global, no tenant) | `BE/models/Permission.java` / `SystemSetting.java` | role-match |
| `BE/models/SerieFiscal.java` | model | CRUD + counter | `BE/models/NotificacaoPreferencia.java` (UniqueConstraint) | role-match |
| `BE/models/RegimeIva.java`, `TipoDocumentoFiscal.java`, `AmbienteFiscal.java`, `MotivoIsencaoIva.java` (enums) | model (enum) | transform | `BE/models/TenantPlano.java`, `TipoCliente.java` | role-match |
| `BE/repositories/ConfiguracaoFiscalRepository.java`, `ParametroFiscalRepository.java` | repository | CRUD | `BE/repositories/NotificacaoPreferenciaRepository.java` | role-match |
| `BE/repositories/SerieFiscalRepository.java` | repository | CRUD + row lock + native upsert | `BE/repositories/ParecerSolicitacaoRepository.java:27` | exact (lock) |
| `BE/services/fiscal/NumeracaoService.java` | service | CRUD, MANDATORY tx | `BE/services/AuditoriaRbacService.java` (MANDATORY) + ParecerController.createVersao (lock+increment) | role-match |
| `BE/services/fiscal/ParametroFiscalService.java` (vigente numa data) | service | request-response | `BE/services/AuditoriaRbacService.java` (listar, readOnly) | partial |
| `BE/services/fiscal/ConfiguracaoFiscalService.java` (activar/desactivar/guardar + auditoria) | service | CRUD, transactional | `BE/controllers/OfficeRolesController.java` (tx + audit) | role-match |
| `BE/services/AuditoriaFiscalService.java` (or new methods in `AuditoriaRbacService`) | service | event-driven (append-only) | `BE/services/AuditoriaRbacService.java` | exact |
| `BE/controllers/FaturacaoController.java` | controller | request-response | `BE/controllers/OfficeRolesController.java` + `AuditoriaRbacController.java` | exact |
| `BE/dtos/ConfiguracaoFiscal{Request,Response}.java`, `SerieFiscalResponse.java` | dto | request-response | `BE/dtos/PapelCreateRequest.java`, `AuditoriaRbacEntradaDto.java` | role-match |
| `BE/config/GlobalExceptionHandler.java` (modify; erro com `code`) | config | request-response | itself (`:66`, `:90`) | exact |
| `BE/config/ClockConfig.java` (new `Clock` bean) | config | n/a | none (zone precedent `AlertasDiariosJob.java:66`) | no analog |
| `BE/seed/DatabaseSeeder.java` (modify: seed `t_parametro_fiscal`, NIF demo) | seed | batch / upsert | `upsertRolePermissions` `:496` in same file | exact |
| `backend/migrations/133-create-fiscal-foundation-tables.sql` | migration | batch | `backend/migrations/126-add-tenant-role-tables.sql` | exact |
| `backend/migrations/README.md` (modify: row 19 + idempotent table) | docs | n/a | rows 15-18 | exact |
| `BT/services/NumeracaoServiceConcorrenciaIT.java` | test (Testcontainers) | concurrency | `BT/repositories/ParecerVersaoConcorrenciaIT.java` | exact |
| `BT/controllers/FaturacaoControllerTest.java` / `...AuditoriaTest.java` | test | request-response | `BT/controllers/OfficeRolesControllerAuditoriaTest.java`, `AuditoriaRbacControllerTest.java` | exact |
| `FE/app/(dashboard)/settings/faturacao-tab.tsx` | component | request-response | `FE/app/(dashboard)/settings/auditoria-tab.tsx` + `criar-papel-panel.tsx` (form) | exact |
| `FE/app/(dashboard)/settings/page.tsx` (modify: TabId + button + panel) | component | n/a | itself `:86`, `:165-176`, `:218-222` | exact |
| `FE/hooks/use-faturacao.ts`, `FE/types/faturacao.ts` | hook/types | request-response | `FE/hooks/use-notificacao-preferencias.ts`, `use-admin.ts:106` | exact |
| `FE/schemas/faturacao.ts` (Zod) | schema | transform | `FE/schemas/papeis-escritorio.ts`, `FE/schemas/clientes.ts:5,39` | exact |

---

## Pattern Assignments

### `BE/models/SerieFiscal.java` / `ConfiguracaoFiscal.java` (model, CRUD)

**Analog:** `BE/models/NotificacaoPreferencia.java` (lines 1-52). Convention: UUID id via `GenerationType.UUID`, `tenant_id` as bare UUID column (no `@ManyToOne`), unique constraint named in `@Table`, Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

```java
@Entity
@Table(name = "t_notificacao_preferencia",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_notificacao_preferencia",
               columnNames = {"tenant_id", "user_id", "categoria"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificacaoPreferencia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    ...
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

Apply:
- `ConfiguracaoFiscal`: `@UniqueConstraint(name="uk_configuracao_fiscal_tenant", columnNames={"tenant_id"})`; fields per CONTEXT (nif, firma, morada <=100, localidade, email_contacto, telefone_contacto, regime_iva, motivo_isencao_codigo, ativa, envio_email_automatico, `envio_email_aceite_por` UUID, `envio_email_aceite_em`). Add `completa()` (ARCHITECTURE §3.1).
- `SerieFiscal`: `@UniqueConstraint(columnNames={"tenant_id","tipo_documento","ano","ambiente"})` (CONTEXT: key is tenant+tipo+ano+ambiente; ARCHITECTURE §3.1 also lists `codigo` but CONTEXT supersedes), `codigo`, `led_codigo` nullable, `ultimo_numero` int/long default 0.
- NIF bean-validation precedent: `BE/models/Cliente.java:39-41` `@NotBlank @Pattern(regexp = "^\\d{9}$", ...)`. Tighten to `^[1-9]\\d{8}$` (CONTEXT). Test analog: `BT/models/ClienteNifValidationTest.java`.
- `@Immutable` NOT used here (AuditLog does use it, `BE/models/AuditLog.java:17`); these entities are mutable.
- Enum-typed columns: use `@Enumerated(EnumType.STRING)` (check `TenantPlano` usage in `Tenant.java`).

### `BE/repositories/SerieFiscalRepository.java` (repository, lock + native upsert)

**Analog:** `BE/repositories/ParecerSolicitacaoRepository.java:19-27` (lock idiom, imports at :1-11).

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from ParecerSolicitacao s where s.id = :id")
Optional<ParecerSolicitacao> findByIdForUpdate(@Param("id") UUID id);
```

Apply (ARCHITECTURE §4.2 gives the exact shape, adapted to CONTEXT key):
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from SerieFiscal s where s.tenantId = :t and s.tipoDocumento = :tipo and s.ano = :ano and s.ambiente = :amb")
Optional<SerieFiscal> bloquear(@Param("t") UUID t, ...);

@Modifying
@Query(value = "INSERT INTO t_serie_fiscal (id, tenant_id, tipo_documento, ano, ambiente, codigo, ultimo_numero) VALUES (...) ON CONFLICT DO NOTHING", nativeQuery = true)
void criarSeNaoExiste(...);   // never findFirst+save (race)
```
Every finder takes `tenantId` (ARCHITECTURE §3.4). Read-only list for the tab: `findByTenantIdOrderByAnoDescTipoDocumentoAsc(tenantId)`. Gate for "can deactivate": `existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0)`.
Other lock precedent: `BE/repositories/SystemSettingRepository.java:11-14`.
Note: `lock_timeout` — JPA hint is unreliable on Postgres; use `SET LOCAL lock_timeout = '5s'` via `EntityManager.createNativeQuery` at start of `proximoNumero` (ARCHITECTURE §4.2; verify in IT).

### `BE/services/fiscal/NumeracaoService.java` (service, MANDATORY tx)

**Analog (annotation + wiring):** `BE/services/AuditoriaRbacService.java:47-50, 65`.
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaRbacService {
    ...
    @Transactional(propagation = Propagation.MANDATORY)
    public void registarPapelCriado(UUID tenantId, UserPrincipal autor, TenantRole papel) {
```
Rationale to copy into the javadoc: MANDATORY turns a missing transaction into an immediate `IllegalTransactionStateException` instead of silent self-commit (`AuditoriaRbacService.java:40-45`).

**Analog (lock + increment sequence):** `BT/repositories/ParecerVersaoConcorrenciaIT.java:109-117`:
```java
parecerSolicitacaoRepository.findByIdForUpdate(solicitacaoId);
int next = parecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId(solicitacaoId).orElse(0) + 1;
parecerVersaoRepository.save(...numeroVersao(next)...);
```
Apply: `criarSeNaoExiste` -> `bloquear` -> `serie.setUltimoNumero(serie.getUltimoNumero()+1)`. Services never read SecurityContext: take `(UUID tenantId, TipoDocumentoFiscal tipo, AmbienteFiscal amb)`; year from injected `Clock`.

**Zone/Clock:** `BE/jobs/AlertasDiariosJob.java:66` (and comment :62-65):
```java
private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");
... LocalDate.now(FUSO_CABO_VERDE)
```
Apply `LocalDate.now(clock.withZone(FUSO_CABO_VERDE))`. `AlertasDiariosJob` uses an overload with injected `hoje` instead of Clock; no `Clock` bean exists in `BE/` (grep) -> see "No Analog Found".
Code format: `SIM-` + tipo (`FR`/`NC`) + `-` + ano (e.g. `SIM-FR-2026`). No `0.15`/`0.20` literal anywhere (CONTEXT specifics).

### `BE/services/AuditoriaRbacService.java` pattern for fiscal audit events (service, event-driven)

**Analog:** same file. Reuse the writer idiom exactly (lines 214-225, 233-239, 170-172, 196-204):
```java
private void gravar(UUID tenantId, UserPrincipal autor, String acao, String entidadeTipo,
                     String entidadeId, Map<String, Object> detalheCampos) {
    AuditLog auditLog = AuditLog.builder()
            .tenantId(tenantId).acao(acao).entidadeTipo(entidadeTipo).entidadeId(entidadeId)
            .autorId(autor == null ? null : autor.getUserId())
            .detalhe(serializarDetalhe(detalheCampos))
            .build();
    auditLogRepository.save(auditLog);
}
```
Per-event shape (lines 65-73): `Map<String,Object> detalhe = new LinkedHashMap<>(); put(detalhe,"autorNome", nomeDoAutor(autor)); ... gravar(...)`, `@Transactional(propagation = MANDATORY)`.
Privacy rule: `detalhe` stores only `autorNome` (`UserPrincipal.getNome()`), never email (`:162-169`; `AuditoriaRbacServiceTest` greps for the getter name -- do not write it even in comments). NIF/morada values should not be dumped into `detalhe`; record only changed field NAMES (`camposAlterados`).

Recommended: a NEW `AuditoriaFiscalService` (same pattern) rather than growing `AuditoriaRbacService`, because:
- `AuditoriaRbacService.listar` / `AuditLogRepository.buscarEventosRbac` filter `entidade_tipo IN ('papel_escritorio','atribuicao_papel')` (`AuditoriaRbacService.java:257-263`), so fiscal events with `entidade_tipo = "configuracao_fiscal"` will NOT show in the existing Auditoria tab (UI-SPEC flags this as out-of-scope; confirmed).
- `AuditLogRepository` is narrowed (`BE/repositories/AuditLogRepository.java:12-40`: `Repository<AuditLog, Long>`, method set pinned by `BT/repositories/AuditLogImutabilidadeTest`, Test 2). Use only `save`; do NOT add repo methods without updating that test deliberately.
- Update the vocabulary comments on `BE/models/AuditLog.java:38-48` (acao values: `faturacao_ativar`, `faturacao_desativar`, `faturacao_dados_alterar`, `faturacao_email_ligar`, `faturacao_email_desligar`; entidadeTipo `configuracao_fiscal`, entidadeId = config id or tenantId). `AuditLogImutabilidadeTest.nenhumControladorTemHandlerMutanteSobreAudit` fails if any controller handler path contains "audit" with POST/PUT/DELETE -- `/faturacao/...` is safe, don't name any path `auditoria`.
- Portuguese sentence copy lives in the frontend mapper (`FE/lib/auditoria-rbac.ts`, `auditoriaEventoToSentence`) if the planner chooses to extend the tab later (UI-SPEC: out of scope unless endpoint surfaces them).

Test analog: `BT/services/AuditoriaRbacServiceTest.java` (Mockito, `@ExtendWith(MockitoExtension.class)`, service built in `@BeforeEach` :55-60, distinctive-email privacy assertion :41).

### `BE/controllers/FaturacaoController.java` (controller, request-response)

**Analog:** `BE/controllers/OfficeRolesController.java` (class gate + tx + audit + RecusaTransacional) and `BE/controllers/AuditoriaRbacController.java` (read endpoint).

Class header (OfficeRolesController.java:72-75; 81-84 for the principal helpers):
```java
@RestController
@RequestMapping("/api/v1/admin/rbac/roles")
@PreAuthorize("hasAuthority('rbac:manage')")
@RequiredArgsConstructor
@Slf4j
public class OfficeRolesController {
    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }
    private UUID getTenantId() { return getPrincipal().getTenantId(); }
```
Apply: `@RequestMapping("/api/v1/faturacao")` + CLASS-level `@PreAuthorize("hasAuthority('financeiro:manage')")` (a method-level annotation REPLACES the class one, so class-level means a forgotten handler stays gated; reasoning documented in OfficeRolesController javadoc :48-64). CONTEXT says no new permissions: `financeiro:manage` already exists. Tenant only from principal, never URL/body. Autor (`getPrincipal()`) passed to audit service. `FaturacaoController` is a separate class -- do not add to the ~3400-line `ResourceController`.

Mutating handler shape (OfficeRolesController.java:116-137, 163-176):
```java
@PostMapping("")
@Transactional
public ResponseEntity<?> createRole(@RequestBody PapelCreateRequest request) {
    ...
    if (tenantRoleRepository.findByTenantIdAndNome(tenantId, nome).isPresent()) {
        return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Já existe um papel com este nome neste escritório.")));
    }
    ...
    try {
        papelGravado = tenantRoleRepository.saveAndFlush(novoPapel);   // flush INSIDE try
    } catch (DataIntegrityViolationException ex) { ... 409 ... }
    auditoriaRbacService.registar...(tenantId, principal, ...);
```
Apply to `PUT /configuracao`, `POST /ativar`, `POST /desativar`, `PUT /email-automatico`: `@Transactional`; every non-2xx goes through `RecusaTransacional.recusar(...)` (`BE/controllers/RecusaTransacional.java:42-50`) so a refused request writes no audit event and doesn't dirty-flush managed entities (open-in-view). `saveAndFlush` inside `try`. Error body: `Map.of("message", ..., "code", "CONFIGURACAO_FISCAL_INCOMPLETA")` (UI needs `code` for 409/422; ARCHITECTURE §2.1 lists the codes: `CONFIGURACAO_FISCAL_INCOMPLETA`; add `FATURACAO_JA_EMITIU` / `NIF_BLOQUEADO`).

Read handler shape (AuditoriaRbacController.java:93-111) for `GET /configuracao` and `GET /series`: no tenant param; `getPrincipal().getTenantId()`; return DTO (never the entity); `ResponseEntity.ok(...)`. Pagination not needed (series list is small).

Business rules to enforce in the service/controller (CONTEXT): activate only if `completa()` else 409/422 + code; deactivate refused if any series `ultimo_numero > 0`; NIF edit refused (409) if any series `ultimo_numero > 0`; email switch only if `ativa`; storing `envio_email_aceite_por` = `principal.getUserId()` and `_em` = now (CV zone via Clock).

Validation analog (DTO): `BE/dtos/PapelCreateRequest.java`; handler-side manual validation precedent `OfficeRolesController.validarNome` (:99-114) returns `ResponseEntity.badRequest().body(Map.of("message", ...))`. `MethodArgumentNotValidException` is already mapped globally (`BE/config/GlobalExceptionHandler.java:21`).

Controller unit-test analogs: `BT/controllers/OfficeRolesControllerTest.java`, `OfficeRolesControllerAuditoriaTest.java` (audit event written on success, none on refusal), `AdminControllerRbacAutorizacaoTest.java` (proves class-level gate via real proxy), `AuditoriaRbacControllerTest.java`. New controllers are constructed with positional args by tests: add new dependencies at the END of the field list (see OfficeRolesController.java:92-96 comment).

### `BE/config/GlobalExceptionHandler.java` (modify, optional)

Existing handlers `:21, :33, :66, :90, :98` (catch-all returns 500 at :98-100). Only add a handler if the service throws a domain exception (e.g. `SerieIndisponivelException` -> 503 `SERIE_INDISPONIVEL` on lock timeout). Pattern (`:66`): generic client message, never echo `ex.getMessage()`; shape `{"message": ...}` plus `code`. Services throw `RuntimeException` for automatic rollback (don't return `ResponseEntity` from inside a service transaction).

### `BE/seed/DatabaseSeeder.java` (modify, seed/upsert)

**Analog:** `upsertRolePermissions` at `:496-510` (non-destructive find-or-create/converge):
```java
Role role = roleRepository.findByNome(roleName)
        .orElseGet(() -> roleRepository.save(Role.builder().nome(roleName).instanciavel(instanciavel).build()));
boolean changed = role.getPermissions().addAll(permissions);
...
if (changed || instanciavelDivergente) { roleRepository.save(role); }
```
Apply: new `private void seedParametrosFiscais()` called UNCONDITIONALLY next to `seedRbac()` as the first thing in `run()` (`:47-49`; `run()` is `@Transactional`, class is `@Order(LOWEST_PRECEDENCE - 100)`, `:24`). For each `(codigo, valor, vigenteDesde)` (`IVA_TAXA_NORMAL=15`, `RETENCAO_SUGERIDA=20`): `findByCodigoAndVigenteDesde(...)` -> create if absent; never update/delete an existing row (non-destructive; a future rule change = new row with later `vigente_desde`). Add the `ParametroFiscalRepository` field at the END of the field list (`:28-42`) -- `DatabaseSeeder*Test` construct it positionally (`BT/seed/DatabaseSeeder*Test.java`; check and update them).
Demo tenant NIF: `:88` `.nif("000000000")` is inside the demo block guarded by `bdVaziaAntesDoSeedPlataforma` (:50-58) -> change to a valid 9-digit value starting 1-9 (the demo CLIENTES already use `"123456789"` `:122` and `"512345678"` `:138`; pick a different valid value, e.g. `"500000001"`; verify any test that asserts `000000000`). Also seed a `ConfiguracaoFiscal` for the demo tenant if the planner wants a complete demo (optional; keep faturacao `ativa=false` so default behaviour is unchanged).
`CATALOGO_PERMISSOES` (`DatabaseSeeder.java:346-388`) and `UserPrincipal.java:41-52` are NOT touched in this phase (no new permissions; `UserPrincipalCatalogoSyncTest` stays green).

### `backend/migrations/133-create-fiscal-foundation-tables.sql` (migration)

**Analog:** `backend/migrations/126-add-tenant-role-tables.sql` (header comment convention + `CREATE TABLE IF NOT EXISTS` + named constraints + `CREATE INDEX IF NOT EXISTS`). Structure to copy:
```sql
-- Phase 133 (CFG-01..06): create t_configuracao_fiscal, t_parametro_fiscal, t_serie_fiscal
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run manually
-- (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying ...
-- Why: `application.yml` runs `ddl-auto: update` ... `validate` refuses to start without them ...
-- Idempotent: every statement below guards its own creation.

CREATE TABLE IF NOT EXISTS t_configuracao_fiscal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ...
    CONSTRAINT uk_configuracao_fiscal_tenant UNIQUE (tenant_id)
);
CREATE TABLE IF NOT EXISTS t_parametro_fiscal (id UUID PRIMARY KEY, codigo VARCHAR(64) NOT NULL, valor NUMERIC(19,4) NOT NULL, vigente_desde DATE NOT NULL, CONSTRAINT uk_parametro_fiscal_codigo_vigencia UNIQUE (codigo, vigente_desde));
CREATE TABLE IF NOT EXISTS t_serie_fiscal (... CONSTRAINT uk_serie_fiscal UNIQUE (tenant_id, tipo_documento, ano, ambiente));
```
Column names/types MUST match the entity mappings exactly (validate mode). No backfill UPDATE: the seeder populates `t_parametro_fiscal` on next boot (same statement 126 makes about `instanciavel`). Name: `<phase-number>-<kebab-description>.sql` (README "Adding a new migration"); latest existing is `131-...`, phase number here is 133 -> `133-create-fiscal-foundation-tables.sql`.

### `backend/migrations/README.md` (modify, same commit)

Add row 19 to the numbered table (after row 18, `:172`):
`| 19 | \`133-create-fiscal-foundation-tables.sql\` | Creates \`t_configuracao_fiscal\`, \`t_parametro_fiscal\`, \`t_serie_fiscal\` (Phase 133, Fundação Fiscal); no backfill -- \`DatabaseSeeder\` upserts \`t_parametro_fiscal\` on next boot. | **Yes** -- every statement uses \`IF NOT EXISTS\`. |\`
and a line in the "Safe to re-run" table (`:195-204`) in the style: ``| `133-create-fiscal-foundation-tables.sql` | every statement uses `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` |``. Check README `:98-115` path A/B text for any "N scripts" count that needs updating.

### `BT/services/NumeracaoServiceConcorrenciaIT.java` (Testcontainers IT)

**Analog:** `BT/repositories/ParecerVersaoConcorrenciaIT.java` (whole file, 188 lines). Copy verbatim the scaffolding:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ParecerVersaoConcorrenciaIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)   // real independent commits
    void ...() throws InterruptedException, ExecutionException, TimeoutException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch latch = new CountDownLatch(1);
        Runnable r = () -> transactionTemplate.executeWithoutResult(status -> {
            try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            ... });
        ExecutorService executor = Executors.newFixedThreadPool(N);
        try { futures...; latch.countDown(); future.get(20, TimeUnit.SECONDS); }
        finally { executor.shutdownNow(); executor.awaitTermination(5, TimeUnit.SECONDS); }
```
(lines 55-62, 85-140). Constraint-backstop test: `saveAndFlush` + `assertThrows(DataIntegrityViolationException.class, ...)` (lines 163-186).
Adaptations:
- `@DataJpaTest` does not scan `@Service` beans: add `@Import({NumeracaoService.class, <ClockTestConfig>})` (the IT's own javadoc :43-49 explains why not `@SpringBootTest`: it would instantiate `MinioConfig`/`SecurityConfig`). Provide a fixed `Clock` bean in a nested `@TestConfiguration`.
- Cases required by CONTEXT/ARCHITECTURE §4.2 (N >= 8 threads): (1) numbers `1..N` no dups/no gaps on same series; (2) tx that rolls back after allocating -> next caller reuses the number (no gap); (3) two tenants same tipo/ano -> independent counters; (4) concurrent first-use (series doesn't exist yet) -> exactly one series row, numbers 1..N (ON CONFLICT DO NOTHING); (5) year boundary with `Clock.fixed(Instant 2026-01-01T00:30:00Z)` -> year 2025 in `Atlantic/Cape_Verde` (UTC-1), and 01:30Z -> 2026; (6) ambiente SIMULADO code has prefix `SIM-` (e.g. `SIM-FR-2026`); (7) `proximoNumero` outside a tx throws `IllegalTransactionStateException` (MANDATORY); (8) lock-timeout -> domain exception, not hang.
- Wrap each call in `transactionTemplate` (the service is MANDATORY, caller supplies the tx).
Other Testcontainers IT analogs (2-tenant isolation): `BT/repositories/NotificacaoRepositoryIT.java`, `NotificacaoPreferenciaRepositoryIT.java`, `AuditLogRepositoryIT.java`.

---

### Frontend

### `FE/app/(dashboard)/settings/page.tsx` (modify)

Three edits, all by copying existing markup verbatim.
1. `:86` -> `type TabId = "profile" | "security" | "users" | "rbac" | "auditoria" | "notificacoes" | "faturacao";`
2. Permission flag next to `:100-101`: `const hasFinanceiroManage = can.manage("financeiro");` (`usePermissions` at `:89`; `can.manage` -> `hasScopedPermission(perms, scope, "manage")`, `FE/hooks/use-permissions.ts:12-24`).
3. Tab button after Auditoria (`:165-176`), icon `Receipt` imported from `lucide-react` (import block `:6-25`):
```tsx
{hasFinanceiroManage && (
  <button
    onClick={() => setActiveTab("faturacao")}
    className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${activeTab === "faturacao"
        ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
        : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
      }`}
  >
    <Receipt className="h-4 w-4" />
    Faturação
  </button>
)}
```
4. Panel after `:218-222`:
```tsx
{activeTab === "faturacao" && hasFinanceiroManage && (
  <div className="animate-in fade-in duration-200"><FaturacaoTab /></div>
)}
```
plus `import { FaturacaoTab } from "./faturacao-tab";` beside `:81`. UI-SPEC wants `AccessDeniedState` (`FE/components/shared/access-denied-state`, usage example `FE/app/(dashboard)/agenda/[id]/page.tsx:25`) inside the tab if reached without permission.
Note: `"financeiro"` is already in `KNOWN_SCOPES` (`FE/lib/permissions.ts:5-13`) -- no change to permissions.ts.

### `FE/app/(dashboard)/settings/faturacao-tab.tsx` (component)

**Analog A (tab structure/states):** `FE/app/(dashboard)/settings/auditoria-tab.tsx`
- Imports `:1-26`; `"use client"`; shadcn primitives from `@/components/ui/*` (card, badge, button, label, empty, table, switch, checkbox, alert-dialog, skeleton, native-select all exist in `FE/components/ui/`).
- Date helper `:35-42` (copy verbatim, UI-SPEC requires same helper):
```tsx
function formatDateTime(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString("pt-CV");
}
```
- Per-Card loading / error / empty states (`:202-230`), never replacing the whole tab:
```tsx
{auditoria.isPending ? (<div className="text-sm text-slate-500 dark:text-slate-400">A carregar...</div>)
 : auditoria.isError ? (<div className="text-sm text-red-600">Não foi possível carregar ... Tente novamente.</div>)
 : !auditoria.data?.content.length ? (<Empty><EmptyHeader><EmptyTitle>..</EmptyTitle><EmptyDescription>..</EmptyDescription></EmptyHeader></Empty>) : ...}
```
- Rules-of-hooks comment `:45-49`: no early return above hooks. Read-only guarantee for the series table: no mutation hooks referenced in that Card (auditoria header `:19-23`; `web/scripts/verify-auditoria-rbac.mjs` is the automated precedent for such a grep gate -- optional).
- Native `<select>` styling used at `auditoria-tab.tsx:173` (or use `native-select`).

**Analog B (form with react-hook-form + zod):** `FE/app/(dashboard)/settings/criar-papel-panel.tsx:3-5, 42-43, 96-104`:
```tsx
const form = useForm<CriarPapelFormValues>({ resolver: zodResolver(criarPapelSchema), ... });
<Input aria-invalid={!!form.formState.errors.nome} {...form.register("nome")} />
{form.formState.errors.nome ? (<p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>) : null}
```
UI-SPEC adds `mode: "onTouched"`, `aria-describedby` on each field, `Guardar dados fiscais` disabled when `!formState.isDirty || isSubmitting`.

**Analog C (AlertDialog with pending/keep-open-on-failure):** `FE/app/(dashboard)/settings/page.tsx:1062-1070` (`catch { // apiFetch ja mostrou o toast; mantemos o AlertDialog aberto. }`), AlertDialog imports `:61-70`. Use `AlertDialogAction` with `e.preventDefault()` while async so the dialog stays open on network failure.

Gotchas for the planner (from `FE/lib/api.ts:12-48`):
- `apiFetch` throws `new Error("API 409: msg")` and DISCARDS `status` and body `code`; it also fires `toast.error` for every non-401/403 (including 409/422). UI-SPEC wants inline copy for 409/422 and field-error mapping from 4xx. Options: (a) parse status from the message prefix (fragile), or (b) minimally extend `apiFetch` to throw an `ApiError` with `status` and `code` (ARCHITECTURE §2.1 already plans this: "Propagar `status` e `code` do corpo de erro") and add an opt-out for the auto-toast. Pick one in the plan; (b) touches a shared file used by every hook -> additive only (keep message format `API ${status}: ${msg}`).
- Next.js 16: `web/AGENTS.md` -- check `web/node_modules/next/dist/docs/` before any routing change (this phase adds none; tab is client-side state).

### `FE/hooks/use-faturacao.ts` (hook)

**Analog:** `FE/hooks/use-notificacao-preferencias.ts` (whole file) and `FE/hooks/use-admin.ts:106-120`:
```ts
export function useNotificacaoPreferencias() {
  return useQuery({
    queryKey: ["notificacao-preferencias"],
    queryFn: () => apiFetch<NotificacaoPreferenciasResponse>("/notificacoes/preferencias"),
    enabled: typeof window !== "undefined",
    staleTime: 30_000,
  });
}
export function useSilenciarCategoria() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (categoria) => apiFetch(`/notificacoes/preferencias/${...}`, { method: "PUT" }),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ["notificacao-preferencias"] }); },
  });
}
```
Apply: `useConfiguracaoFiscal` (GET `/faturacao/configuracao`), `useGuardarConfiguracaoFiscal` (PUT, body `JSON.stringify`), `useAtivarFaturacao`, `useDesativarFaturacao` (POST), `useEmailAutomatico` (PUT `{ligado, aceiteDeclaracao}`), `useSeriesFiscais` (GET `/faturacao/series`). Path is relative to `API_BASE` (`/api/v1`) -- write `"/faturacao/..."`, not `/api/v1/...`. Query keys constants `FATURACAO_CONFIG_KEY`, `FATURACAO_SERIES_KEY` (as `OFFICE_RBAC_AUDITORIA_KEY` in `use-admin.ts`); every mutation invalidates both keys (activation changes `podeDesativar`; saves change completeness). Gate queries with `enabled` = has `financeiro:manage` to avoid a 403 on a stray mount. Types in `FE/types/faturacao.ts` (mirror DTOs; precedent `FE/types/office-rbac.ts`, `FE/types/auditoria-rbac.ts`).

### `FE/schemas/faturacao.ts` (Zod)

**Analog:** `FE/schemas/clientes.ts:5, 39` (NIF) and `FE/schemas/papeis-escritorio.ts:20-26` (Portuguese messages, `trim()`, `refine`):
```ts
export const nifPattern = /^\d{9}$/;
nif: z.string().trim().regex(nifPattern, "NIF deve conter exatamente 9 dígitos numéricos"),
```
Apply: do NOT reuse `nifPattern` for fiscal (client rule is `\d{9}`; fiscal rule is `^[1-9]\d{8}$`). Define `nifFiscalPattern` locally with message "O NIF deve ter 9 dígitos e começar por um algarismo de 1 a 9." Fields/messages per UI-SPEC Copywriting table ("Preencha este campo.", "A morada não pode ter mais de 100 caracteres.", "Introduza um email válido.", "Escolha o motivo de isenção."). `regimeIva: z.enum(["NORMAL","ISENTO"])` + `.superRefine` -> `motivoIsencao` required iff ISENTO. Export `type ConfiguracaoFiscalFormValues = z.infer<...>` (as `CriarPapelFormValues`, `papeis-escritorio.ts:37`). The 21 `TaxExemptionReasonCode` options: a constant list in `FE/lib/faturacao-motivos-isencao.ts` (precedent for label tables: `FE/lib/notificacao-categoria.ts`, `FE/lib/auditoria-rbac.ts`); better, have the backend return them from `GET /faturacao/configuracao` (or a `/faturacao/motivos-isencao` endpoint) so the enum lives in one place. The 21 codes themselves come from the eFatura XSD (RESEARCH, not in codebase).

---

## Shared Patterns

### Tenant isolation (primary data boundary)
**Source:** `BE/controllers/OfficeRolesController.java:81-86`, `AuditoriaRbacController.java:93-111`
**Apply to:** `FaturacaoController`, all new services/repositories
`tenantId` from `UserPrincipal` only; no `tenantId` request param/body/path. Every new repository finder has `tenantId` in its signature (ARCHITECTURE §3.4), except `ParametroFiscalRepository` (global legal rule, no tenant -- by decision). Isolation test: two tenants, same key (IT precedent `NotificacaoRepositoryIT`).

### Authorization (both layers agree)
**Source:** `OfficeRolesController.java:72-74` (class-level `@PreAuthorize`); frontend `FE/hooks/use-permissions.ts`.
**Apply to:** `FaturacaoController` -> `hasAuthority('financeiro:manage')`; tab gated by `can.manage("financeiro")`. Backend is exact-match (`hasAuthority`), frontend `manage` has no fallback widening (`FE/lib/permissions.ts` ACTION_FALLBACKS: `manage: ["manage"]`), so both agree.

### Transactional refusal (no audit on refusal)
**Source:** `BE/controllers/RecusaTransacional.java:42-50`
**Apply to:** every `@Transactional` handler in `FaturacaoController` that can return 4xx/409/422.
```java
return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "...", "code", "...")));
```
Also pin `@Transactional` presence by reflection like the Phase 128 tests (`BT/controllers/OfficeRolesControllerAuditoriaTest.java`, `RecusaTransacionalTest.java`).

### Audit (immutable, same transaction)
**Source:** `BE/services/AuditoriaRbacService.java:65-73, 214-239`; `BE/models/AuditLog.java`; `BE/repositories/AuditLogRepository.java` (narrowed).
**Apply to:** activation, deactivation, fiscal-data change, email-switch on/off. MANDATORY propagation; `detalhe` JSON with `autorNome` only; serialization failure -> `IllegalStateException` so the change rolls back with its event (`:227-239`).

### Idempotent schema change + README row
**Source:** `backend/migrations/126-add-tenant-role-tables.sql`, `README.md:153-172, 195-204, "Adding a new migration"`.
**Apply to:** the 133 migration script; README row in the SAME commit.

### Seeder convergence (non-destructive)
**Source:** `DatabaseSeeder.java:496-510`
**Apply to:** `t_parametro_fiscal` seeding; values are data, never constants (`0.15` forbidden in code -- add a grep gate test in the style of the `AuditoriaRbacServiceTest` getter-name grep).

### Time zone
**Source:** `BE/jobs/AlertasDiariosJob.java:62-66`
**Apply to:** `NumeracaoService` (year), `envio_email_aceite_em` timestamp, series list. Inject `Clock`; never bare `LocalDate.now()`.

### Frontend data fetching
**Source:** `FE/lib/api.ts#apiFetch` (credentials: "include", toast on non-401/403) via TanStack Query hooks in `FE/hooks/use-*.ts`.
**Apply to:** `use-faturacao.ts`. See the `apiFetch` gotcha above (status/code lost; auto-toast on 409/422).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `BE/config/ClockConfig.java` (`@Bean Clock`) | config | n/a | No `Clock` bean or injection exists in `backend/src/main` (only `ZoneId` constant in `AlertasDiariosJob`). Add `@Bean Clock clock() { return Clock.systemUTC(); }` and derive CV date with `ZoneId.of("Atlantic/Cape_Verde")`; tests inject `Clock.fixed(...)`. Check no existing test config conflicts. |
| `TaxExemptionReasonCode` 21-entry enum/table | model | transform | Official list comes from the eFatura XSD (RESEARCH.md), not present in the codebase. Closest structural analog: PT-labelled enums (`TenantPlano`, `TipoCliente`) and `FE/lib/notificacao-categoria.ts` for the label table. |
| Series "ano desc, tipo" read-only `Table` | component | request-response | `FE/components/ui/table.tsx` exists but no tab currently renders a plain `Table` in settings; closest is the list rendering in `auditoria-tab.tsx` (after `:230`). Use `Table` primitives with `overflow-x-auto` per UI-SPEC. |

## Planner Notes / Risks

1. **Class-vs-method `@PreAuthorize`:** a method-level annotation replaces the class-level one (OfficeRolesController javadoc). Keep ONLY the class-level `financeiro:manage` on `FaturacaoController`.
2. **`ParecerVersaoConcorrenciaIT` uses `@DataJpaTest`** (no `@Service` scanning, no Minio/Security context). `NumeracaoService` IT needs explicit `@Import`; do not switch to `@SpringBootTest`.
3. **Positional-constructor tests:** `DatabaseSeeder*Test`, `OfficeRolesController*Test`, `ResourceController*Test` build objects with positional args; new `@RequiredArgsConstructor` fields go LAST.
4. **`AuditLogImutabilidadeTest` pins the `AuditLogRepository` method set** -- do not add methods there; read-only listing of fiscal events is out of scope for 133.
5. **Existing Auditoria tab will not show fiscal events** (`buscarEventosRbac` filters on `entidade_tipo`); UI-SPEC already marks extending it as out of scope -- confirm and state in the plan.
6. **Demo seed NIF** `000000000` (`DatabaseSeeder.java:88`) must become valid, else activating faturação for the demo tenant is refused.
7. **Series key mismatch:** CONTEXT keys `t_serie_fiscal` by (tenant, tipo, ano, ambiente) with a generated `codigo`; ARCHITECTURE §3.1 also includes `codigo` in the unique key. Follow CONTEXT (newer, locked decision).
8. **`GET /series` deactivation hook:** "existe série com `ultimo_numero > 0`" is the only gate in 133; the frontend derives `podeDesativar`/NIF-lock from this too (UI-SPEC blocks 3/4) -- expose a boolean (`documentosEmitidos`) in the config DTO rather than making the client infer it from the series list.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{controllers,services,models,repositories,config,seed,jobs,dtos}`, `backend/src/test/java/com/lexcv/**`, `backend/migrations/`, `web/src/{app/(dashboard)/settings,hooks,schemas,lib,components/ui}`
**Files scanned/read:** ~25 read in full or by range; ~200 listed
**Pattern extraction date:** 2026-10-04
