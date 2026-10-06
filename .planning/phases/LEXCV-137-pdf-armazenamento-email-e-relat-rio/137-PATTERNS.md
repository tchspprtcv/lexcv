# Phase 137: PDF, Armazenamento, Email e Relatório - Pattern Map

**Mapped:** 2026-10-06
**Files analyzed:** 38 new/modified files (names marked *proposed* fall under Claude's Discretion in 137-CONTEXT.md)
**Analogs found:** 34 / 38

All paths are relative to the repo root `/home/user/lexcv`. Backend Java root: `backend/src/main/java/com/lexcv/` (abbreviated `…/`). Test root: `backend/src/test/java/com/lexcv/`.

---

## File Classification

### Backend: email outbox (ENTR-03..06)

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `backend/migrations/137-entrega-documento-fiscal.sql` *(proposed)* | migration | DDL | `backend/migrations/136-efatura-comunicacao.sql` | exact |
| `backend/migrations/README.md` (Path B row 23 + re-run row) | doc/config | n/a | rows 22 / 230 for `136` | exact |
| `…/models/EnvioEmailDocumentoFiscal.java` *(proposed; mutable satellite, 1 row per document)* | model | CRUD (outbox row) | `…/models/ComunicacaoFiscal.java` | exact |
| `…/models/EstadoEnvioEmail.java` + `EstadoEnvioEmailConverter.java` *(proposed)* | model/enum | transform | `…/models/EstadoComunicacaoFiscal.java` + `EstadoComunicacaoFiscalConverter.java` | exact |
| `…/repositories/FilaEnvioEmailFiscal.java` *(proposed)* | repository (native SQL) | batch / SKIP LOCKED queue | `…/repositories/FilaComunicacaoFiscal.java` | exact |
| `…/repositories/EnvioEmailDocumentoFiscalRepository.java` *(proposed)* | repository (narrow) | CRUD by tenant | `…/repositories/ComunicacaoFiscalRepository.java` / `DocumentoFiscalXmlRepository.java` | exact |
| `…/services/fiscal/EnvioEmailFiscalTransacoes.java` *(proposed)* | service (short tx bean) | batch | `…/services/fiscal/ComunicacaoFiscalTransacoes.java` | exact |
| `…/services/fiscal/ProcessadorEnvioEmailFiscal.java` *(proposed)* | service | event-driven (per item) | `…/services/fiscal/ProcessadorComunicacaoFiscal.java` | exact |
| `…/services/fiscal/BackoffEnvioEmail.java` *(proposed, max 5)* | utility | transform | `…/services/fiscal/BackoffComunicacao.java` | exact |
| `…/jobs/EmailFiscalOutboxJob.java` *(proposed)* | job | scheduled batch | `…/jobs/FiscalOutboxJob.java` | exact |
| `…/services/fiscal/ReenvioEmailFiscalService.java` *(proposed)* | service | request-response (tx + audit) | `…/services/fiscal/ReprocessamentoComunicacaoService.java` | exact |
| `…/services/fiscal/NotificacaoEnvioEmailFiscal.java` *(proposed)* | service | event-driven (after commit) | `…/services/fiscal/NotificacaoComunicacaoFiscal.java` | exact |
| `…/models/CategoriaNotificacao.java` (add `EMAIL_FISCAL_FALHOU(false)`) | model/enum | n/a | same file, `COMUNICACAO_FISCAL_FALHOU(false)` line 28 | exact |
| `…/services/fiscal/ProcessadorComunicacaoFiscal.java` / `ComunicacaoFiscalTransacoes.java` (enqueue on ACEITE_SIMULADO) | service (modify) | event-driven | `ProcessadorComunicacaoFiscal.processar` lines 142-149 | exact |
| `…/fiscal/email/EmailFiscalGateway.java` + `SmtpEmailFiscalGateway.java` *(proposed port)* | service (port/adapter) | request-response (SMTP) | `…/fiscal/efatura/EfaturaGateway.java` + `SimuladoEfaturaGateway.java` | role-match |
| `…/config/EmailProperties.java` + `EmailConfig.java` *(proposed)* | config | n/a | `…/fiscal/efatura/EfaturaProperties.java` + `EfaturaConfig.java`; `…/config/MinioConfig.java` | role-match |

### Backend: PDF + storage + downloads (ENTR-01, ENTR-02, DFE-06)

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `…/fiscal/pdf/PdfDocumentoFiscalRenderer.java` *(proposed)* | utility | transform (snapshot -> bytes) | `…/fiscal/efatura/DfeValidador.java` (hardened, classpath-only, fail-fast ctor) | partial |
| `…/fiscal/pdf/ClasspathPdfResolver.java` *(proposed; refuses non-classpath URIs)* | utility | file-I/O (classpath) | `…/fiscal/efatura/ClasspathXsdResolver.java` | exact (policy) |
| `backend/src/main/resources/pdf/documento-fiscal.html` + `pdf/fonts/*.ttf` + licence file *(proposed)* | template/resource | n/a | `backend/src/main/resources/xsd/efatura/` (vendored classpath resources + whitelist) | partial |
| `…/services/fiscal/PdfDocumentoFiscalService.java` *(proposed; generate-once, idempotent on-demand)* | service | file-I/O (MinIO) | `ProcessadorComunicacaoFiscal` (XML reuse-or-generate, lines 171-222) + `StorageService` | role-match |
| `…/services/StorageService.java` (add bytes upload with explicit key, presign with attachment filename, exists/get) | service (modify) | file-I/O | same file lines 39-80 | exact |
| `…/controllers/DocumentoFiscalController.java` (GET pdf/xml download, POST email reenviar, GET CSV) | controller (modify) | request-response | same file lines 198-238 + `ResourceController.downloadDocumento` 2997-3024 | exact |
| `…/services/fiscal/AuditoriaFiscalService.java` (new `registar*` for download pdf/xml, CSV export, email resend) | service (modify) | CRUD (audit insert) | same file lines 145-159 | exact |
| `…/services/fiscal/DownloadDocumentoFiscalService.java` *(proposed; tx owner for read+audit)* | service | request-response | `ReprocessamentoComunicacaoService` (tx owner calling MANDATORY audit) | role-match |
| `…/dtos/EnvioEmailResumo.java` *(proposed)* + `DocumentoFiscalDetalheResponse` / `DocumentoFiscalResumoResponse` (add email state) | dto | transform | `…/dtos/ComunicacaoFiscalResumo.java` | exact |

### Backend: CSV monthly report (RELF-01)

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `…/services/fiscal/RelatorioMensalFiscalService.java` *(proposed)* | service | batch read -> transform | `DocumentoFiscalService.listar` (lines 87-122) | role-match |
| `…/fiscal/csv/CsvFiscal.java` *(proposed; `;`, BOM, decimal comma, guard)* | utility | transform | `web/src/lib/csv.ts` (guard + escape), `web/src/app/(dashboard)/financeiro/page.tsx` lines 22-99 (BOM) | partial (port TS -> Java) |
| `…/repositories/DocumentoFiscalRepository.java` (month finder) | repository (modify) | read | same file `buscar` lines 63-96 | exact |

### Backend: tests

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `…/repositories/MigracaoFiscal137IT.java` *(proposed)* | test (IT) | DDL | `…/repositories/MigracaoFiscal136IT.java` | exact |
| `…/jobs/EmailFiscalOutboxJobIT.java` (+ Mailpit/GreenMail) | test (IT) | batch | `…/jobs/FiscalOutboxJobIT.java` | exact (no Mailpit analog) |
| `…/services/fiscal/FilaEnvioEmailFiscalIT.java` | test (IT) | SKIP LOCKED | `…/services/fiscal/FilaComunicacaoFiscalIT.java` | exact |
| `…/controllers/DocumentoFiscalControllerAutorizacaoTest.java` (modify) | test | RBAC | same file | exact |
| `…/models/CategoriaNotificacaoTest.java`, `…/repositories/DocumentoFiscalImutabilidadeTest.java` (modify deliberately) | test (guard) | n/a | same files | exact |

### Ops / config

| File | Role | Analog | Match |
|---|---|---|---|
| `backend/pom.xml` (spring-boot-starter-mail, openhtmltopdf, test mail lib) | config | lines 85-150 (comment-per-dependency style) | exact |
| `backend/src/main/resources/application.yml` (`app.email.*` / SMTP) | config | `app.efatura` block + `minio.presigned-url-expiry: ${MINIO_PRESIGNED_EXPIRY:3600}` | exact |
| `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml` | config | EFATURA block (yml lines 70-78 / prod 19-26 / hostinger 73-77) | exact |
| `.github/workflows/deploy.yml` | config | `env: EFATURA_MODE: SIMULADO` lines 33-38 | exact |
| `backend/.env.example`, `.env.example` | config | `backend/.env.example` lines 20-28 | exact |

### Web

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `web/src/types/faturacao.ts` (EnvioEmail types, fields on Resumo/Detalhe) | types | n/a | same file lines 118-137 (`EstadoComunicacaoFiscal`, `ComunicacaoFiscal`) | exact |
| `web/src/lib/envio-email-fiscal.ts` + `.test.ts` *(proposed)* | utility (pure) | transform | `web/src/lib/comunicacao-fiscal.ts` + `.test.ts` | exact |
| `web/src/components/shared/envio-email-estado-badge.tsx` *(proposed)* | component | n/a | `web/src/components/shared/comunicacao-estado-badge.tsx` | exact |
| `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/envio-email-card.tsx` + `reenviar-email.tsx` *(proposed)* | component | request-response | `comunicacao-fiscal-card.tsx` + `reprocessar-comunicacao.tsx` (same dir) | exact |
| `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx` (PDF/XML buttons) | page (modify) | request-response | same file header lines 236-263; download in `documentos/[id]/page.tsx` 43-58 | exact |
| `web/src/hooks/use-faturacao.ts` (download PDF/XML, reenviar email, export CSV hooks + exact gates) | hook | request-response | same file lines 186-209, 292-328; `use-documentos.ts` 99-106 | exact |
| `web/src/lib/api.ts` (additive blob variant for CSV) | utility (modify) | file download | same file `apiFetch` lines 52-110 | role-match |
| `web/src/app/(dashboard)/financeiro/page.tsx` ("Exportar mês" + month picker) | page (modify) | request-response | same file lines 186-221 | exact |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` (TabKey + trigger + content) | page (modify) | n/a | same file lines 101-108, 471-480, 884-902 | exact |
| `web/src/app/(dashboard)/clientes/[id]/documentos-fiscais-tab.tsx` *(proposed)* | component | read-only list | `web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx` + `useDocumentosFiscais` | exact |
| `web/src/types/notificacoes.ts`, `web/src/lib/notificacao-categoria.ts` (+ `.test.ts`) | types / utility | n/a | same files (`COMUNICACAO_FISCAL_FALHOU`) | exact |

---

## Pattern Assignments

### `backend/migrations/137-entrega-documento-fiscal.sql` (migration)

**Analog:** `backend/migrations/136-efatura-comunicacao.sql`

**Header convention** (lines 1-45): phase + requirement ids; "IMPORTANT: REQUIRED manual production migration for `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`"; "Requires 134/135/136 first"; a "Why" paragraph (Hibernate `update` never adds CHECKs to existing tables); a "Design" bullet list; "Backfill: none"; "Idempotent: every statement guards its own creation".

**Idempotent DDL** (lines 47-52, 70-87):
```sql
ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS lease_ate TIMESTAMP(6) WITH TIME ZONE;
...
ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS reprocessamentos INTEGER DEFAULT 0 NOT NULL;
CREATE INDEX IF NOT EXISTS idx_comunicacao_fiscal_estado_proxima
    ON t_comunicacao_fiscal (estado, proxima_tentativa_em);
CREATE TABLE IF NOT EXISTS t_documento_fiscal_xml (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    documento_fiscal_id UUID NOT NULL,
    ...
    CONSTRAINT uk_documento_fiscal_xml_documento UNIQUE (documento_fiscal_id),
```
**Guarded CHECK** (lines 54-68): `DO $$ ... IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = ... AND conname = ...) THEN ALTER TABLE ... ADD CONSTRAINT ... END $$;` (use it if the email table gets a CHECK, e.g. `tentativas <= 5`).

Conventions to keep: `TIMESTAMP(6) WITH TIME ZONE`, bare UUID columns with **no foreign keys**, no column defaults except the deliberate `DEFAULT 0 NOT NULL` counter (episode counter = `reenvios`, mirroring `reprocessamentos`), `VARCHAR(500)` for `ultimo_erro`, `VARCHAR(64)` for `ultimo_erro_codigo`, `VARCHAR(32)` enum columns, `versao` bigint for optimistic locking.

**README** (`backend/migrations/README.md`): add Path B row `| 23 | 137-... | <what it does> | **Yes** -- ... |` after line 194 and a re-run row after line 230; also mention it in the Path A list near line 157 if it is redundant-but-harmless on a fresh DB. **Same commit as the script** (CONTEXT).

**Migration IT:** copy `…/repositories/MigracaoFiscal136IT.java` (helpers `aplicar`/`aplicarTodos` lines 100-112, information_schema column comparison 120-140, tests `aplicadoDuasVezes...` 201, `segundoArranqueEmUpdateNaoEmiteDdl` 407).

---

### `…/models/EnvioEmailDocumentoFiscal.java` (model, mutable satellite)

**Analog:** `…/models/ComunicacaoFiscal.java` (whole file, 104 lines)

```java
@Entity
@Table(name = "t_comunicacao_fiscal",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_comunicacao_fiscal_documento",
               columnNames = {"documento_fiscal_id"}),
       indexes = {
               @Index(name = "idx_comunicacao_fiscal_tenant_estado", columnList = "tenant_id, estado"),
               @Index(name = "idx_comunicacao_fiscal_estado_proxima", columnList = "estado, proxima_tentativa_em")})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ComunicacaoFiscal {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Column(name = "documento_fiscal_id", nullable = false, updatable = false) private UUID documentoFiscalId;
    @Convert(converter = EstadoComunicacaoFiscalConverter.class)
    @Column(name = "estado", nullable = false, length = 32) private EstadoComunicacaoFiscal estado;
    @Column(name = "tentativas", nullable = false) @Builder.Default private Integer tentativas = 0;
    @Column(name = "proxima_tentativa_em") private Instant proximaTentativaEm;
    @Column(name = "lease_ate") private Instant leaseAte;
    @Column(name = "ultimo_erro", length = 500) private String ultimoErro;
    @Column(name = "ultimo_erro_codigo", length = 64) private String ultimoErroCodigo;
    @Column(name = "reprocessamentos", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0") @Builder.Default private Integer reprocessamentos = 0;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Version @Column(name = "versao", nullable = false) private Long versao;
}
```
Additions for this phase: `destinatario` (snapshot of resolved email at enqueue time), `enviado_em`, and the PDF storage fields (`pdf_object_key`, `pdf_sha256`, `pdf_gerado_em`) if planning keeps the PDF state on this satellite. **Do not add mutable columns to `DocumentoFiscal`** — it is immutable (see `DocumentoFiscalImutabilidadeTest`). States to model (CONTEXT): `NAO_CONFIGURADO` is derived at read time from config (no row needed or a row with that state), `DESLIGADO`, `SEM_EMAIL` (no attempts), `PENDENTE`, `ENVIADO`, `FALHOU`. `createdAt` from the injected `Clock`, never `Instant.now()`.

**Converter/enum:** copy `EstadoComunicacaoFiscal.java` (has `terminal()`, `reprocessavel()` helpers used by `ComunicacaoFiscalTransacoes.registarResultado` line 153 and `ComunicacaoFiscalResumo` line 53) and `EstadoComunicacaoFiscalConverter.java`.

---

### `…/repositories/FilaEnvioEmailFiscal.java` (native-SQL queue)

**Analog:** `…/repositories/FilaComunicacaoFiscal.java`

**Why a separate @Repository class** (lines 22-36): the narrow Spring Data repo has a method set pinned by `DocumentoFiscalImutabilidadeTest`, and the claim is deliberately multi-tenant (job has no SecurityContext); it returns `tenant_id` and every later statement is bound to that tenant.

**Claim with SKIP LOCKED + lease + attempt counted at claim** (lines 54-75):
```java
private static final String SQL_RECLAMAR = """
        WITH devidas AS (
            SELECT id FROM t_comunicacao_fiscal
             WHERE estado = 'PENDENTE'
               AND tentativas < :maxTentativas
               AND (proxima_tentativa_em IS NULL OR proxima_tentativa_em <= :agora)
               AND (lease_ate IS NULL OR lease_ate < :agora)
             ORDER BY created_at, id
             LIMIT :lote
             FOR UPDATE SKIP LOCKED
        )
        UPDATE t_comunicacao_fiscal c
           SET lease_ate = :leaseAte,
               tentativas = c.tentativas + 1,
               ultima_tentativa_em = :agora,
               versao = c.versao + 1,
               updated_at = :agora
          FROM devidas
         WHERE c.id = devidas.id
        RETURNING c.id, c.tenant_id, c.documento_fiscal_id, c.ambiente, c.tentativas, c.versao,
                  c.reprocessamentos, c.created_at
        """;
```
**Close exhausted rows (WR-01)** lines 83-105; **version-guarded result write** lines 108-119 (`WHERE id = :id AND tenant_id = :tenantId AND versao = :versao`); **lease renewal before the external call (WR-02)** lines 129-134 — renew immediately before SMTP send so two workers never send the same email; **manual reset for a new episode** lines 141-155 (`tentativas = 0`, `reprocessamentos = reprocessamentos + 1`, `WHERE tenant_id = :tenantId AND documento_fiscal_id = :documentoId AND estado IN (...)`) -> resend analog (`estado IN ('FALHOU','ENVIADO')` per product decision).

**Execution style** (lines 166-180): `@Transactional(propagation = Propagation.MANDATORY)`, `entityManager.createNativeQuery(...).unwrap(NativeQuery.class).setParameter("agora", agora, StandardBasicTypes.INSTANT)`; results mapped and re-sorted in Java (lines 200-211); defensive `truncar` to column width (lines 266-271) — **only fixed codes/messages reach the DB, never exception text (T-136-48)**.

---

### `…/services/fiscal/EnvioEmailFiscalTransacoes.java` (short transactions bean)

**Analog:** `…/services/fiscal/ComunicacaoFiscalTransacoes.java`

Rationale comment (lines 25-38): separate bean so the job/processor calls through the Spring proxy; **no transaction ever wraps PDF rendering, MinIO upload or SMTP**. Pattern:
```java
@Transactional
public List<ComunicacaoReclamada> reclamar(int lote, Duration lease) {
    Instant agora = clock.instant();
    return fila.reclamar(agora, agora.plus(lease), lote, EstadoComunicacaoMapper.MAX_TENTATIVAS);
}
...
@Transactional(readOnly = true)
public Optional<SnapshotComunicacao> carregarSnapshot(UUID tenantId, UUID documentoFiscalId) {
    Optional<DocumentoFiscal> documento = documentoRepository.findByIdAndTenantId(documentoFiscalId, tenantId);
```
(lines 65-69, 87-116). Use a dedicated `MAX_TENTATIVAS_EMAIL = 5` constant (CONTEXT), not `EstadoComunicacaoMapper.MAX_TENTATIVAS` (= 8, `EstadoComunicacaoMapper.java:26`). Snapshot loader for email: `DocumentoFiscal` + lines + `DocumentoFiscalXml` (for attachment) + recipient. **Recipient:** `DocumentoFiscal` has **no adquirente email column** (fields at `…/models/DocumentoFiscal.java:132-142`: nif, nome, morada, localidade only), so the "snapshot first" branch only applies if planning adds an email to the snapshot; otherwise fall back to `Cliente.email` (`…/models/Cliente.java:42`) read with `clienteRepository` and checked against `tenantId`.

---

### `…/services/fiscal/ProcessadorEnvioEmailFiscal.java` (per-item processor)

**Analog:** `…/services/fiscal/ProcessadorComunicacaoFiscal.java`

**Never-throws contract + fixed codes** (lines 50-75, 114-150):
```java
public void processar(ComunicacaoReclamada item) {
    ResultadoComunicacao resultado;
    try {
        resultado = comunicar(item, numeroFormatado);
    } catch (LeasePerdido perdido) {
        log.warn(...); return;
    } catch (Exception e) {
        log.error("Falha interna ao comunicar o documento fiscal {} (tentativa {})", item.documentoFiscalId(), item.tentativas(), e);
        resultado = new ResultadoComunicacao.ErroTransitorio(FALHA_INTERNA, MSG_FALHA_INTERNA);
    }
    ...
        Instant proxima = estado == EstadoComunicacaoFiscal.PENDENTE
                ? clock.instant().plus(BackoffComunicacao.atraso(Math.max(1, item.tentativas())))
                : null;
        linhas = transacoes.registarResultado(item, estado, codigo(resultado), mensagem(resultado), proxima);
    ...
    if (linhas != 1) { log.warn("Lease perdido ..."); return; }
    if (estado == EstadoComunicacaoFiscal.ERRO) { notificar(item, numeroFormatado[0]); }
}
```
**Reuse-or-generate artifact** (lines 171-222): reuse existing XML row else build -> validate -> insert-only -> re-read. Mirror this for the PDF: reuse stored PDF object key else render from snapshot -> upload -> record key (idempotent). **Lease renewal before the external call** (lines 224-230). **Private sentinel exception** `LeasePerdido` (lines 257-264). Sealed result `switch` for code/message (lines 266-280). `sha256Hex` helper (lines 282-288) reusable for `pdf_sha256`.

SMTP failure classification: transient (connection/timeout/4xx) -> PENDENTE with backoff; permanent (5xx recipient rejected) -> FALHOU immediately; log only the exception class, never message (it can contain the address) — see `log.warn(..., invalido.getClass().getSimpleName())` line 204-205.

**Enqueue point (modify Phase 136 flow):** enqueue only when the communication reached `ACEITE_SIMULADO`. Two options in the existing code:
- Same short tx as the result write: `ComunicacaoFiscalTransacoes.registarResultado` (lines 149-156) — atomic outbox (recommended: insert the email row with `INSERT ... ON CONFLICT DO NOTHING` when `estado == ACEITE_SIMULADO` and `ConfiguracaoFiscal.envioEmailAutomatico` is true for that tenant).
- After commit in `ProcessadorComunicacaoFiscal.processar` after line 146 (`linhas == 1`), like the ERRO notification at 147-149 (not atomic; a crash loses the enqueue).
Either way it is **outside the payment transaction** (`PagamentoFaturadoService` line 309 / `NotaCreditoService` line 312 only create the PENDENTE communication row and must not change).

---

### `…/services/fiscal/BackoffEnvioEmail.java`

**Analog:** `…/services/fiscal/BackoffComunicacao.java` (lines 13-36): `final` class, private ctor, `List<Duration> TABELA`, `atraso(int tentativas)` throwing `IllegalArgumentException` outside `1..MAX-1`. With max 5 the table has 4 entries. Test analog: `…/services/fiscal/BackoffComunicacaoTest.java`.

---

### `…/jobs/EmailFiscalOutboxJob.java` (scheduled job)

**Analog:** `…/jobs/FiscalOutboxJob.java` (whole file, 106 lines)
```java
@Component
@Slf4j
public class FiscalOutboxJob {
    @Scheduled(fixedDelayString = "${app.efatura.outbox.intervalo:PT30S}",
            initialDelayString = "${app.efatura.outbox.atraso-inicial:PT20S}")
    public void executar() {
        try { executarUmaVez(); }
        catch (Exception e) { log.error("Falha inesperada na execução do job do outbox fiscal", e); }
    }
    int executarUmaVez() {
        encerrarEsgotadas();
        EfaturaProperties.Outbox outbox = propriedades.outbox();
        List<ComunicacaoReclamada> itens = transacoes.reclamar(outbox.lote(), outbox.lease());
        for (ComunicacaoReclamada item : itens) {
            try { processador.processar(item); }
            catch (Exception e) { log.error("Falha ao processar ...", item.id(), item.documentoFiscalId(), e); }
        }
        return itens.size();
    }
```
No `@Transactional` on the job; `Error` is not swallowed (IN-08). Scheduler pool: `application.yml` `spring.task.scheduling.pool.size: 3` (lines 31-36) already exists for 2 jobs — **raise to 4** (and update the comment there and in `…/config/SchedulingConfig.java`) when adding a third periodic job. When SMTP is not configured the job should short-circuit (rows stay / are marked "Não configurado") rather than burn attempts. Unit test analog: `…/jobs/FiscalOutboxJobTest.java` (lines 91-98 assert `Error` propagation).

---

### `…/services/fiscal/NotificacaoEnvioEmailFiscal.java` + category

**Analog:** `…/services/fiscal/NotificacaoComunicacaoFiscal.java` (whole file)

Same recipient rule as COMUNICACAO_FISCAL_FALHOU (CONTEXT): active users of the same tenant whose *effective* permissions contain `financeiro:manage` or `financeiro:edit` (lines 49-55, 109-117):
```java
public static final String CATEGORIA = "COMUNICACAO_FISCAL_FALHOU";
public static final String ENTIDADE_TIPO = "documento_fiscal";
public static final String PERMISSAO = "financeiro:manage";
public static final String PERMISSAO_REPROCESSAR = "financeiro:edit";
static final Set<String> PERMISSOES_DESTINATARIO = Set.of(PERMISSAO, PERMISSAO_REPROCESSAR);
...
String entidadeId = documentoId + ":" + episodio;          // dedup per episode
String linkUrl = "/financeiro/documentos-fiscais/" + documentoId;
...
if (notificacaoService.criar(tenantId, utilizador.getId(), CATEGORIA, titulo, mensagem,
        ENTIDADE_TIPO, entidadeId, linkUrl).isPresent()) { criadas++; }
```
Non-transactional, never throws, called **after** the FALHOU commit. Dedup relies on `NotificacaoRepository` `ON CONFLICT (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria) DO NOTHING` (`…/repositories/NotificacaoRepository.java:121`) — because the category differs from COMUNICACAO_FISCAL_FALHOU, using the same `entidadeId` format is safe. Episode = the new `reenvios` counter.

**Non-silenceable category** — sync points (all must change together):
- `…/models/CategoriaNotificacao.java` lines 17-28: add `EMAIL_FISCAL_FALHOU(false)` (name is discretionary) and update the comment block lines 12-16 / 55-57.
- `…/services/NotificacaoService.java:80` and `:444` use `CategoriaNotificacao.isSilenciavelCategoria` — no change needed, the enum flag drives it.
- `backend/src/test/java/com/lexcv/models/CategoriaNotificacaoTest.java` lines 22-26 (`assertEquals(10, values().length)` -> 11, ordered name list) and lines 39-42 (non-silenceable set).
- `web/src/types/notificacoes.ts` lines 1-11 (union), `web/src/lib/notificacao-categoria.ts` lines 10-21 (label), 33-44 (badge variant, `"red"`), 74-77 (`NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS`), and `web/src/lib/notificacao-categoria.test.ts` lines 13-28.

---

### `…/services/fiscal/ReenvioEmailFiscalService.java` (manual resend, exact `financeiro:edit`)

**Analog:** `…/services/fiscal/ReprocessamentoComunicacaoService.java` (whole file, 65 lines)
```java
@Transactional
public ReprocessarComunicacaoResponse reprocessar(UUID tenantId, UserPrincipal autor, UUID documentoId) {
    DocumentoFiscal documento = documentoRepository.findByIdAndTenantId(documentoId, tenantId)
            .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, CODIGO_NAO_ENCONTRADO,
                    MSG_NAO_ENCONTRADO));
    EstadoComunicacaoFiscal estadoAnterior = comunicacaoRepository
            .findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)
            .map(ComunicacaoFiscal::getEstado).orElse(null);
    if (estadoAnterior == null || fila.reporPendente(tenantId, documentoId, clock.instant()) == 0) {
        throw estadoInvalido();   // 409 COMUNICACAO_ESTADO_INVALIDO
    }
    auditoria.registarReprocessamentoComunicacao(tenantId, autor, documentoId,
            documento.getNumeroFormatado(), estadoAnterior.name());
    return new ReprocessarComunicacaoResponse(EstadoComunicacaoFiscal.PENDENTE.name(), 0);
}
```
Resend must also refuse (409/422 with fixed code) when SMTP is "Não configurado" or the client has no email. New response DTO analog: `…/dtos/ReprocessarComunicacaoResponse.java`.

---

### `…/fiscal/pdf/ClasspathPdfResolver.java` + `PdfDocumentoFiscalRenderer.java` ("no external resources")

**Analog (policy):** `…/fiscal/efatura/ClasspathXsdResolver.java`
```java
public static final String PREFIXO = "classpath:xsd/efatura/";
static final Set<String> LISTA_BRANCA = Set.of("EnvelopedSignature.xsd", ...);
static String resolverRelativo(String systemId, String baseURI) {
    if (systemId == null || baseURI == null || !baseURI.startsWith(PREFIXO)) return null;
    try {
        URI base = new URI(ESQUEMA_SINTETICO, null, CAMINHO_SINTETICO + baseURI.substring(PREFIXO.length()), null);
        ...
        URI resolvido = base.resolve(pedido).normalize();
        if (!ESQUEMA_SINTETICO.equals(resolvido.getScheme()) || resolvido.getRawAuthority() != null
                || resolvido.getRawQuery() != null || resolvido.getRawFragment() != null) return null;
        ...
        return LISTA_BRANCA.contains(relativo) ? relativo : null;
    } catch (URISyntaxException | IllegalArgumentException e) { return null; }
}
static byte[] lerRecurso(String relativo) {
    if (!LISTA_BRANCA.contains(relativo)) return null;
    ClassLoader cl = ClasspathXsdResolver.class.getClassLoader();
    try (InputStream in = cl.getResourceAsStream(RAIZ_RECURSOS + relativo)) { ... }
```
(lines 27-31, 34, 73-109). Use `URI.resolve` on a synthetic base, never filesystem path APIs (comment lines 21-22: FindSecBugs PATH_TRAVERSAL_IN). For OpenHTMLtoPDF, plug this into the builder's URI resolver / stream factory so `http:`, `https:`, `file:`, `data:` and anything outside the whitelist return null/empty.

**Analog (construction):** `…/fiscal/efatura/DfeValidador.java` lines 35-60 and 62-82 — `@Component`, heavy resources (template, fonts) loaded **once in the constructor** and **fail-fast** with a fixed message (`throw new IllegalStateException(MENSAGEM_ARRANQUE)`) if a classpath resource is missing; package-private test ctor taking an alternative resource. Logging rule (lines 116-118, 124): document values only at DEBUG.

Template input: only the stored snapshot (`DocumentoFiscal`, `DocumentoFiscalLinha`, `DocumentoFiscalXml.iud`, NC origin number/reason) — same data `ComunicacaoFiscalTransacoes.carregarSnapshot` (lines 87-116) already loads; never live `Cliente`/`Honorario`. HTML-escape every snapshot string inserted into the template. Simulation watermark + header band text: reuse the existing copy "SIMULAÇÃO — SEM VALIDADE FISCAL" (web badge copy at `documentos-fiscais/[id]/page.tsx:252` is "Simulação — sem validade fiscal").

---

### `…/services/StorageService.java` (modify) and `PdfDocumentoFiscalService.java`

**Analog:** same file
```java
public String upload(UUID tenantId, UUID documentoId, String filename,
                     InputStream inputStream, String contentType, long size) {
    String sanitisedFilename = filename.replaceAll("[/\\\\]", "_");
    String objectKey = tenantId.toString() + "/" + documentoId.toString() + "/" + sanitisedFilename;
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(props.getBucketName()).key(objectKey).contentType(contentType).contentLength(size).build();
    try {
        byte[] contentBytes = inputStream.readAllBytes();
        s3Client.putObject(request, RequestBody.fromBytes(contentBytes));
    } catch (SdkException | java.io.IOException e) {
        throw new StorageUnavailableException("Storage service unavailable", e);
    }
    return objectKey;
}
public String presignedDownloadUrl(String objectKey) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(props.getBucketName()).key(objectKey).build();
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(props.getPresignedUrlExpiry()))
            .getObjectRequest(getObjectRequest).build();
```
(lines 39-80). Add additive methods (do not change existing signatures; `ResourceController`/`ParecerController` call them): e.g. `uploadBytes(String objectKey, byte[] bytes, String contentType)` with a key under the tenant prefix such as `<tenantId>/documentos-fiscais/<documentoId>/<serie-numero>.pdf`, and a presign overload that sets `responseContentDisposition("attachment; filename=\"...\"")` on the `GetObjectRequest` so the attachment name follows série/número (ENTR-02). Error type: `…/exceptions/StorageUnavailableException` -> 503, as in `ResourceController` lines 3020-3023.

Generation timing: background (after issue, e.g. when the email processor or a PDF step in the fiscal flow runs) **outside any tx**, then record the key in a short tx; download endpoint falls back to on-demand generation (idempotent: same deterministic key, overwrite is harmless; record the key with a conditional update).

---

### `…/controllers/DocumentoFiscalController.java` (modify: downloads, resend, CSV)

**Analog:** same file. Conventions to copy:

**Tenant + principal from security context only** (lines 112-119):
```java
private UserPrincipal getPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (UserPrincipal) auth.getPrincipal();
}
private UUID getTenantId() { return getPrincipal().getTenantId(); }
```
**Read gate + 404-without-oracle** (lines 198-206, 240-255):
```java
@PreAuthorize("hasAuthority('financeiro:view')")
@GetMapping("/documentos-fiscais/{id}")
public ResponseEntity<?> detalhe(@PathVariable String id) {
    Optional<UUID> documentoId = idDocumento(id);
    if (documentoId.isEmpty()) { return naoEncontrado(); }
    return ResponseEntity.ok(documentoFiscalService.detalhe(getTenantId(), documentoId.get()));
}
```
**Exact edit gate for mutating action** (lines 229-238):
```java
@PreAuthorize("hasAuthority('financeiro:edit')")
@PostMapping("/documentos-fiscais/{id}/comunicacao/reprocessar")
public ResponseEntity<?> reprocessarComunicacao(@PathVariable String id) { ... }
```
**Query-param parsing returns 400 with fixed message** (lines 143-186: `LocalDate.parse` in try/catch -> `pedidoInvalido(MSG_DATA)`). For the CSV month use `YearMonth.parse` the same way.

Suggested routes (discretion): `GET /documentos-fiscais/{id}/pdf`, `GET /documentos-fiscais/{id}/xml` (both `financeiro:view`, return `{url, expiresIn}`), `POST /documentos-fiscais/{id}/email/reenviar` (`financeiro:edit`), `GET /documentos-fiscais/exportacao-mensal?mes=AAAA-MM` (`financeiro:view`). Keep them here, **not** in `FaturacaoController` (class gate `financeiro:manage`, `…/controllers/FaturacaoController.java:52`) and **not** in `ResourceController` (see CFG-03 below). Update the Javadoc list (lines 36-88) as previous phases did.

**Presigned download + audit shape** — `…/controllers/ResourceController.java` lines 2997-3024:
```java
// Audit record — T-34-03: placed before response so record is written even if downstream error occurs
auditLogRepository.save(AuditLog.builder()
        .tenantId(dlPrincipal.getTenantId())
        .acao("documento_download")
        .entidadeTipo("documento")
        .entidadeId(id.toString())
        .autorId(dlPrincipal.getUserId())
        .build());
try {
    String url = storageService.presignedDownloadUrl(doc.getCaminhoArquivo());
    return ResponseEntity.ok(Map.of("url", url, "expiresIn", 3600));
} catch (StorageUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "Storage service unavailable"));
}
```
For fiscal downloads write the audit through `AuditoriaFiscalService` (below), not `auditLogRepository` directly.

**XML download:** either upload the stored XML (`DocumentoFiscalXml.getXml()`, `…/models/DocumentoFiscalXml.java:61`) to MinIO once and presign (uniform `{url}` contract with the PDF; the frontend opens both with `window.open`) or stream bytes. **There is no existing byte/attachment response in the backend** (grep for `ContentDisposition` finds nothing), so streaming would be a new pattern; the MinIO copy reuses existing ones.

---

### `…/services/fiscal/AuditoriaFiscalService.java` (modify) + `DownloadDocumentoFiscalService.java`

**Analog:** same file, lines 63-67 (action constants) and 145-159:
```java
public static final String ACAO_REPROCESSAR_COMUNICACAO = "documento_fiscal_reprocessar_comunicacao";
...
@Transactional(propagation = Propagation.MANDATORY)
public void registarReprocessamentoComunicacao(UUID tenantId, UserPrincipal autor, UUID documentoId,
                                               String numeroFormatado, String estadoAnterior) {
    Map<String, Object> detalhe = new LinkedHashMap<>();
    put(detalhe, "autorNome", nomeDoAutor(autor));
    put(detalhe, "numeroFormatado", numeroFormatado);
    put(detalhe, "estadoAnterior", estadoAnterior);
    gravar(tenantId, autor, ACAO_REPROCESSAR_COMUNICACAO, ENTIDADE_TIPO_DOCUMENTO, idTexto(documentoId), detalhe);
}
```
New actions (discretion): `documento_fiscal_download_pdf`, `documento_fiscal_download_xml` (detail `kind` per CONTEXT: "document id, kind, user"), `documento_fiscal_reenviar_email`, `documento_fiscal_exportar_csv` (entidadeTipo for the CSV: e.g. `relatorio_fiscal`, entidadeId = `AAAA-MM`). **Privacy rule** (lines 31-33, 161-168): only author display name + numbers/kind — never NIF, client email/recipient address, or author email; `AuditoriaFiscalServiceTest` has a source gate forbidding the email getter name.

**Pitfall:** every `registar*` is `Propagation.MANDATORY` — calling it from the non-transactional controller throws `IllegalTransactionStateException`. Download/CSV need a `@Transactional` service method (lookup by `findByIdAndTenantId` -> audit -> return key), exactly like `ReprocessamentoComunicacaoService` (tx owner) — keep the MinIO/PDF work outside that tx (presign is local signing, cheap; PDF on-demand generation must happen before/after, not inside).

---

### `…/services/fiscal/RelatorioMensalFiscalService.java` + `…/fiscal/csv/CsvFiscal.java` (CSV, RELF-01)

**Read-side analog:** `…/services/fiscal/DocumentoFiscalService.java` lines 87-122 — `@Transactional(readOnly = true)`, tenant first, batch-load comunicação states with `findByTenantIdAndDocumentoFiscalIdIn` and NC origin numbers with `findByTenantIdAndIdIn` (no N+1). IUDs: batch from `DocumentoFiscalXmlRepository` (needs a new `findByTenantIdAndDocumentoFiscalIdIn`).

**Query analog:** `…/repositories/DocumentoFiscalRepository.java` lines 63-96 (`buscar`): `tenant_id` first predicate, `CAST(:p AS date)` idiom, all `@Param`, nothing concatenated. A month export needs an unpaged finder (e.g. `findByTenantIdAndDataEmissaoBetweenOrderByDataEmissaoAscAnoAscNumeroAsc`). **Every new repository method must be added deliberately to `DocumentoFiscalImutabilidadeTest`'s expected set** (its Javadoc: "a correção é acrescentar esse nome ao conjunto esperado, deliberadamente").

**Formula-injection guard (v2.13 Phase 104 lesson)** — source is frontend-only today; port to Java:
`web/src/lib/csv.ts` lines 36-53:
```ts
const FORMULA_TRIGGER_CHARS = ["=", "+", "-", "@", "\t", "\r"];
/** Apply this ONLY to genuinely free-text, attacker-influenced field values ... Structured data
 * such as phone numbers legitimately starts with `+` ... blanket-applying it corrupts data */
export function guardCsvFormula(value: string) {
  if (FORMULA_TRIGGER_CHARS.some((c) => value.startsWith(c))) { return "'" + value; }
  return value;
}
```
`web/src/lib/csv.ts` lines 72-77 (quote when value contains `"`, `\n`, `\r` or the delimiter; double the quotes). BOM: `web/src/app/(dashboard)/financeiro/page.tsx` lines 91-93 (`const bom = "﻿"; const content = bom + lines.join("\n");`). Field-by-field example of "guard free text only": `web/src/app/(dashboard)/clientes/page.tsx` lines 135-143 (`guardCsvFormula(c.nome)`, NIF left alone with justification comment). In this phase: guard `cliente` (adquirente nome) and `motivo de isenção`/free-text reason; leave série, número, IUD, NIF, dates and amounts untouched. Note: NC amounts are **negative** and start with `-` — they are numbers and must NOT be guarded (this is exactly why the guard is field-scoped). Decimal comma (`;` separator makes `,` safe), dates `dd/MM/yyyy`; use `Locale`-independent formatting (`DecimalFormatSymbols` with `,`) rather than default locale.

Response: `text/csv; charset=UTF-8` body with `Content-Disposition: attachment; filename="documentos-fiscais-AAAA-MM.csv"` (new pattern, see controller note), or upload to MinIO and presign like the PDF.

---

### `…/config/EmailProperties.java` + `EmailConfig.java` + `application.yml` (optional SMTP)

**Properties analog:** `…/fiscal/efatura/EfaturaProperties.java` (lines 20-43): `@ConfigurationProperties("app.efatura") public record ... (@DefaultValue Outbox outbox, ...)` with nested records and `@DefaultValue("PT30S") Duration intervalo`.

**Config analog:** `…/fiscal/efatura/EfaturaConfig.java` (lines 24-53): `@Configuration @EnableConfigurationProperties(...)`, one `@Bean` that decides the adapter from properties and logs a WARN for test levers. `…/config/MinioConfig.java` lines 16-47 for building a client bean from properties.

**How optional env vars are already handled** (contradicts the "every value is required" line in CLAUDE.md — that line predates Phase 136): `application.yml` uses `${VAR:default}`:
```yaml
  efatura:
    modo: ${EFATURA_MODE:SIMULADO}
    transmissao:
      nif-transmissor: ${EFATURA_TRANSMISSOR_NIF:999999999}
...
  public-endpoint: ${MINIO_PUBLIC_ENDPOINT:${MINIO_ENDPOINT}}
  presigned-url-expiry: ${MINIO_PRESIGNED_EXPIRY:3600}
```
For SMTP use empty defaults, e.g. `host: ${SMTP_HOST:}`, `port: ${SMTP_PORT:587}`, `username: ${SMTP_USERNAME:}`, `password: ${SMTP_PASSWORD:}`, `from: ${SMTP_FROM:}`, `starttls: ${SMTP_STARTTLS:true}`. 

**Pitfall (verify in research/plan):** mapping `SMTP_HOST:` (empty) straight into `spring.mail.host` still *defines* the property; Boot's `MailSenderAutoConfiguration` property condition matches any value other than `false`, so a `JavaMailSender` with an empty host would be created and only fail at send time. Safer: keep values under `app.email.smtp.*`, and in `EmailConfig` build `JavaMailSenderImpl` only when `host` is non-blank (expose `configurado()`), otherwise wire a "não configurado" gateway — mirrors the `EfaturaConfig` single-port decision. Startup must never fail without SMTP (CONTEXT), unlike `EfaturaConfig`, which deliberately fails fast.

**Compose analog** (`docker-compose.yml` lines 70-78; prod lines 19-26 with the "Compose merges environment maps across -f layers" comment; hostinger lines 73-77):
```yaml
      # eFatura (Phase 136): ... A forma ":-" tambem cobre uma variavel definida mas vazia ...
      EFATURA_MODE: "${EFATURA_MODE:-SIMULADO}"
      EFATURA_TRANSMISSOR_NIF: "${EFATURA_TRANSMISSOR_NIF:-999999999}"
```
Use `"${SMTP_HOST:-}"` etc. in all three files. **deploy.yml** (lines 33-38): the only backend runtime env is in the `test` job (`env: EFATURA_MODE: SIMULADO` on the `mvn -B verify` step); build/push jobs carry no runtime env. Add SMTP vars there only as explicitly empty (CI must never hit real SMTP), with a comment like the existing Phase 136 one. **.env.example**: `backend/.env.example` lines 20-28 style (comment + commented-out optional vars); root `.env.example` currently has no EFATURA block (lines 38-45 hold MINIO) — add an SMTP block there too since compose reads the root `.env`.

**pom.xml** (lines 85-150): one comment per dependency; BOM-managed versions have no `<version>` ("do NOT add an explicit <version>"). `spring-boot-starter-mail` is BOM-managed; OpenHTMLtoPDF (`io.github.openhtmltopdf:openhtmltopdf-pdfbox` or the newer fork) is **not** -> explicit version + licence note; GreenMail (`greenmail-junit5`) is not BOM-managed; a Mailpit container uses the already-present `org.testcontainers:junit-jupiter` `GenericContainer` (no new artifact). Re-run `mvn spotbugs:check` (CI step `deploy.yml` lines 42-44).

---

### Tests: Testcontainers IT

**There are no IT base classes** — every `*IT` declares its own container. Analog `…/jobs/FiscalOutboxJobIT.java` lines 80-95, 128-139:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({FiscalOutboxJob.class, ProcessadorComunicacaoFiscal.class, ComunicacaoFiscalTransacoes.class,
        FilaComunicacaoFiscal.class, ..., NotificacaoComunicacaoFiscal.class, NotificacaoService.class,
        PagamentoFaturadoService.class, NotaCreditoService.class, ..., AuditoriaFiscalService.class,
        FiscalOutboxJobIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FiscalOutboxJobIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @TestConfiguration
    static class Apoio {
        @Bean Clock clock() { return RELOGIO; }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
    @MockitoBean private ResolucaoPapeisService resolucaoPapeis;
```
The job is invoked directly via `executarUmaVez()` (package-private), not via the scheduler; movable `RelogioMovel` clock (lines 101-125); fixture `…/services/fiscal/FixturaEmissaoFiscal.java` builds tenant/cliente/config rows with `JdbcTemplate`. `backend/src/test/resources/application.properties` points the datasource at a bogus host so a missing `@ServiceConnection` fails loudly — keep both annotations. For Mailpit: add a second `@Container static GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:<tag>").withExposedPorts(1025, 8025)` and feed host/port into `app.email.smtp.*` with `@DynamicPropertySource`, or use GreenMail in-process. **No real SMTP** in automated runs; human checkpoint before any real provider (`safety.always_confirm_external_services`). MinIO is not containerised anywhere — mock `S3Client`/`StorageService` (see `…/controllers/ResourceControllerUploadDocumentoTest.java`).

**RBAC test analog:** `…/controllers/DocumentoFiscalControllerAutorizacaoTest.java` (proxy with method security; `assertThrows(AccessDeniedException.class, ...)` per handler, lines 149-154, 224-250). Add: pdf/xml/csv accepted with `financeiro:view`, refused without; resend accepted only with exact `financeiro:edit` (refused with `financeiro:manage` alone).

---

### Web: `web/src/lib/envio-email-fiscal.ts` + badge + card + resend dialog

**Lib analog:** `web/src/lib/comunicacao-fiscal.ts` — pure functions, no React; exhaustive `Record<Estado, Apresentacao>` (lines 42-72), unknown-state fallback (lines 74-91), `descricao` vs `descricaoSemPermissao` (lines 93-97), polling rule (lines 99-112), button visibility rule (lines 114-126), error interpreter mapping status/code to fixed copy (lines 161-187), and all copy as exported constants (lines 192-220). Badge variants only `outline`/`secondary`; state conveyed by text + icon, never colour (lines 7-8, 16-17). Test analog: `web/src/lib/comunicacao-fiscal.test.ts`. States (CONTEXT): Não configurado / Desligado / Sem email / Pendente / Enviado / Falhou (tentativas, último erro).

**Badge analog:** `web/src/components/shared/comunicacao-estado-badge.tsx` (whole file, 34 lines) — `ICONES: Record<Icone, LucideIcon>`, `<Badge variant={apresentacao.variante} className="gap-1" title={...}>`.

**Card analog:** `web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/comunicacao-fiscal-card.tsx` — read-only `<dl>` grid (lines 91-136), `formatarDataHora` in `Atlantic/Cape_Verde` (lines 37-52), last-error notice block (lines 140-145), dialog kept mounted while open (lines 67-84). Exact gate from `use-faturacao.ts` (`podeReprocessarComunicacao`, line 300) — add `podeReenviarEmail` with the same `hasPermission(permissions, "financeiro:edit")`.

**Dialog analog:** `.../[id]/reprocessar-comunicacao.tsx` lines 1-80 (Dialog imports, double-click guard `aReprocessarRef`, focus management, error banner from `interpretarErro*`).

---

### Web: hooks in `web/src/hooks/use-faturacao.ts` and downloads

**Gate + mutation analog** (lines 292-328):
```ts
export const PERMISSAO_REPROCESSAR_COMUNICACAO = "financeiro:edit";
export function podeReprocessarComunicacao(permissions: readonly string[] | undefined): boolean {
  return hasPermission(permissions, PERMISSAO_REPROCESSAR_COMUNICACAO);
}
const STATUS_INLINE_REPROCESSAR: readonly number[] = [404, 409, 422, 500, 502, 503, 504];
export function useReprocessarComunicacao(documentoId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<ReprocessarComunicacaoResposta>(
        `/documentos-fiscais/${encodeURIComponent(documentoId)}/comunicacao/reprocessar`,
        { method: "POST" },
        { semToastParaStatus: STATUS_INLINE_REPROCESSAR },
      ),
    onSettled: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: DOCUMENTOS_FISCAIS_KEY }),
        queryClient.invalidateQueries({ queryKey: ["notificacoes"] }),
      ]);
    },
  });
}
```
Read gate is **exact** `financeiro:view` (`podeLerDocumentosFiscais`, lines 186-197, with the rationale that `hasScopedPermission`'s fallback would show screens the backend 403s).

**Presigned download analog:** `web/src/hooks/use-documentos.ts` lines 99-106 and usage `web/src/app/(dashboard)/documentos/[id]/page.tsx` lines 43-58:
```ts
export function useDownloadDocumento(id: string) {
  return useMutation({
    mutationFn: () =>
      apiFetch<{ url: string; expiresIn: number }>(`/documentos/${encodeURIComponent(id)}/download`),
  });
}
...
const res = await download.mutateAsync();
window.open(res.url, "_blank", "noopener,noreferrer");
```
**Blob download (CSV):** `apiFetch` always ends with `return (await res.json())` (`web/src/lib/api.ts` lines 105-110), so it cannot return a CSV body. Either the CSV endpoint also returns `{url}` (MinIO + presign) or add an **additive** `apiFetchBlob` in `lib/api.ts` that reuses the same error branch (lines 69-103: `ApiError`, toast except 401/403/`semToastParaStatus`, `credentials: "include"`) and returns `res.blob()`; then save with the object-URL pattern from `web/src/app/(dashboard)/clientes/page.tsx` lines 145-153 (`URL.createObjectURL`, temporary `<a download>`, `revokeObjectURL`). Keep the existing `apiFetch` signature unchanged (comment lines 10-19: additive changes only).

---

### Web: `web/src/app/(dashboard)/financeiro/page.tsx` ("Exportar mês")

**Analog:** same file, header action group lines 186-221 (icon `Button` + `Tooltip`, `Link` buttons gated by `canVerDocumentosFiscais = podeLerDocumentosFiscais(permissions.permissions)` at line 105). Add a month picker (`<Input type="month">` or the `Select` used at lines 249-260) + "Exportar mês" button, gated by `canVerDocumentosFiscais` (exact `financeiro:view`, matching the backend). Leave the existing client-side `exportHonorariosCsv` (lines 61-99) alone — it is a different export.

---

### Web: client page tab `web/src/app/(dashboard)/clientes/[id]/page.tsx` + new tab component

**Analog:** same file
- `type TabKey` lines 101-108 -> add `"documentosFiscais"`.
- Triggers lines 473-480: `{canViewPareceres ? <TabsTrigger value="pareceres">Pareceres</TabsTrigger> : null}` -> `{podeLerFiscais ? <TabsTrigger value="documentosFiscais">Documentos fiscais</TabsTrigger> : null}` with `podeLerDocumentosFiscais(permissions.permissions)`.
- Content lines 894-902 (loading -> component -> `AccessDeniedState`).
- Tab component: list via `useDocumentosFiscais({ clienteId, ... }, enabled)` (`use-faturacao.ts` lines 255-268 — backend already supports `clienteId` filter, `DocumentoFiscalController.listar` lines 136-160); columns from `web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx` (Número link, Data, Tipo badge, Comunicação badge, Total, plus new Email badge column) with PDF/XML download actions only. **No delete/edit actions**, and the tab must not use `use-documentos.ts` (fiscal documents never enter `t_documento` / generic delete endpoints).

---

## Shared Patterns

### Tenant isolation
**Source:** `DocumentoFiscalController.java` lines 112-119; every service method takes `tenantId` first (`DocumentoFiscalService` Javadoc lines 44-45); repos use `findByIdAndTenantId` (`ReprocessamentoComunicacaoService.java:47`). Another tenant's id -> same 404 `DOCUMENTO_FISCAL_NAO_ENCONTRADO` as a missing id (`DocumentoFiscalController.java:103-104, 252-255`). Background job: the only multi-tenant statement is the claim; everything after uses the claimed row's `tenant_id` (`FilaComunicacaoFiscal.java:26-31`).
**Apply to:** all new endpoints, services, queue SQL, CSV query.

### RBAC, both layers agree
**Source:** `DocumentoFiscalController.java` Javadoc lines 63-75 + `use-faturacao.ts` lines 186-209, 292-302. Backend checks the exact authority; frontend uses `hasPermission(perms, "<exact>")`, not the `hasScopedPermission` fallback.
**Apply to:** downloads + CSV (`financeiro:view`), resend (`financeiro:edit` exact).

### Audit in the same transaction
**Source:** `AuditoriaFiscalService.java` lines 49-50, 185-213 (`MANDATORY`; serialization failure rolls back the change). Privacy: author display name only.
**Apply to:** download PDF/XML, CSV export, email resend.

### Short transactions, no tx around external I/O
**Source:** `ComunicacaoFiscalTransacoes.java` lines 25-38; `FiscalOutboxJob.java` lines 31-36.
**Apply to:** PDF rendering, MinIO upload, SMTP send, on-demand PDF in the download path.

### Fixed error codes/messages only
**Source:** `ProcessadorComunicacaoFiscal.java` lines 50-55, 60-75, 198-207; `ComunicacaoFiscalResumo.java` (sanitised `ultimoErro` exposed only in failure states); web never shows backend text (`comunicacao-fiscal.ts` lines 161-187).
**Apply to:** email `ultimo_erro`, PDF generation failure, resend errors.

### Errors as `RecusaFiscalException`
**Source:** `…/exceptions/RecusaFiscalException.java` + `GlobalExceptionHandler.java` lines 101-120 (`{message, code, campo?}` with the exception's HTTP status). Storage outage -> 503 (`ResourceController.java:3020-3023`).

### CFG-03: never touch `registarPagamentoLegado`
**Source:** `backend/src/test/java/com/lexcv/controllers/FaturacaoDesligadaPagamentoInalteradoTest.java` lines 50-74: SHA-256 of the normalised body of `private ResponseEntity<?> registarPagamentoLegado(Pagamento pag)` (`ResourceController.java:3161`) is pinned (`HASH_CORPO_LEGADO`), and `ResourceController` may not contain the tokens `ConfiguracaoFiscal`, `NumeracaoService`, `SerieFiscal`, `SerieFiscalRepository`, `DocumentoFiscalRepository`, `ComunicacaoFiscal`; exactly one `pagamentoFaturadoService.faturacaoAtiva(` call, inside `createPagamento` (lines 3139-3155). Consequences: no enqueue/PDF/email code in `ResourceController` or in the payment transaction; put all new fiscal endpoints in `DocumentoFiscalController`; do not edit `createPagamento`/`registarPagamentoLegado`, not even comments.

### Guard tests to update deliberately
- `…/repositories/DocumentoFiscalImutabilidadeTest.java` — `REPOSITORIOS_FISCAIS` list and per-repo expected method sets; a new email-satellite repository and any new finder must be added there on purpose.
- `…/models/CategoriaNotificacaoTest.java` — count and non-silenceable set.
- `…/services/fiscal/AuditoriaFiscalServiceTest.java` — source gate against the author-email getter.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| OpenHTMLtoPDF renderer internals (builder, font embedding, watermark CSS) | utility | transform | No PDF library in the project yet; use RESEARCH.md for API. Only the hardening/whitelist policy has an analog (`ClasspathXsdResolver`). |
| SMTP adapter (`JavaMailSender`, MIME with 2 attachments) | service | request-response | No mail infrastructure exists (pom has no mail dep). Port shape analog: `EfaturaGateway`. |
| Mailpit/GreenMail test container | test | n/a | No `GenericContainer` usage in the repo; only `PostgreSQLContainer`. |
| Backend byte/attachment HTTP response (if CSV/XML are streamed) | controller | file download | No `ContentDisposition`/`byte[]` responses exist; prefer MinIO + presign to reuse existing patterns. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{controllers,services,services/fiscal,fiscal/efatura,jobs,repositories,models,config,dtos}`, `backend/src/test/java/com/lexcv/**`, `backend/migrations/`, `backend/src/main/resources/`, `backend/pom.xml`, compose files, `.github/workflows/deploy.yml`, `.env.example` files, `web/src/{lib,hooks,components/shared,types,app/(dashboard)/{financeiro,clientes,documentos}}`
**Files scanned:** ~60 (32 read in depth)
**Pattern extraction date:** 2026-10-06
