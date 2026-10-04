# Phase 134: Fatura-Recibo Atómica nos Honorários - Research

**Researched:** 2026-10-04
**Domain:** Spring Boot 3.4.1 / Hibernate 6.6.4 transactional fiscal emission (PostgreSQL row locks, immutable entities, idempotency) + Next.js 16 / TanStack Query UI
**Confidence:** HIGH for code facts and locking design (verified in repo + Phase 133 ITs); MEDIUM for eFatura payment-means codes; LOW/pending for fiscal rules (contabilista gate, already recorded in STATE.md)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Fluxo de emissão (UI)
- Pré-visualização calculada no BACKEND num endpoint sem efeitos (`POST /api/v1/faturacao/pre-visualizacao`) — fonte única do cálculo; frontend só apresenta (decisão "frontend burro")
- Emissão no formulário de pagamento existente em `web/src/app/(dashboard)/financeiro/[id]/page.tsx`: com faturação ativa, "Registar" abre diálogo de confirmação com a pré-visualização; com faturação desligada, comportamento atual inalterado
- Retenção: checkbox "Aplicar retenção na fonte" + taxa pré-preenchida pelo parâmetro `RETENCAO_SUGERIDA` (20), editável (0 < taxa ≤ 100)
- Método de pagamento obrigatório com faturação ativa; tabela fixa no backend `metodo` atual → código de meio de pagamento eFatura (dinheiro, transferência, cheque, cartão/multibanco, outro); códigos exatos UN/ECE D19B confirmados na 136

#### Documento e cálculo
- Uma linha por Fatura-Recibo; descrição controlada "Honorários por serviços jurídicos — Processo n.º {numero}" (nunca a descrição livre do honorário — sigilo profissional)
- Arredondamento: `base = round(total / (1 + iva/100), 2, HALF_UP)`; `iva = total − base`; `retencao = round(base × taxa/100, 2, HALF_UP)`; `liquidoRecebido = total − retencao`; regime ISENTO: base = total, IVA = 0, motivo de isenção no documento. Valor pago é sempre IVA incluído (decisão do marco). Taxa IVA lida de `ParametroFiscalService.valorVigenteHoje(IVA_TAXA_NORMAL)`
- Snapshot em colunas planas em `t_documento_fiscal` (emitente: NIF, firma, morada, localidade, regime, motivo; adquirente: NIF, nome, morada, cliente_id) + `t_documento_fiscal_linha`; entidade `@Immutable`, repositório estreito sem `delete*` (padrão AuditLog/AuditLogRepository)
- Estado de comunicação em tabela satélite `t_comunicacao_fiscal` com linha `PENDENTE` criada na mesma transação (consumida na 136); documento guarda `ambiente` (SIMULADO)
- Conta corrente creditada do TOTAL (dinheiro + imposto retido)

#### Guardas e atomicidade
- Idempotência: `chaveIdempotencia` (UUID gerado com `crypto.randomUUID()` ao abrir o diálogo) no corpo; `UNIQUE(tenant_id, chave_idempotencia)` em `t_documento_fiscal`; mesma chave → devolve o mesmo resultado; mesma chave com payload diferente → 409 `CHAVE_REUTILIZADA`
- Com faturação ativa, `POST /pagamentos` delega num `PagamentoFaturadoService` `@Transactional` (pagamento + lock da conta corrente + `NumeracaoService.proximoNumero` + documento + linha + comunicação PENDENTE, tudo ou nada; ordem de locks: configuração → conta corrente → série); com faturação desligada, o caminho atual de `createPagamento` fica EXATAMENTE como está (CFG-03). O teste-guarda `FaturacaoDesligadaPagamentoInalteradoTest` (133-05) tem de ser atualizado deliberadamente para permitir só a delegação condicional, mantendo a prova de que o ramo desligado não muda
- Data: com faturação ativa, `dataPagamento` omitida = hoje (`Clock` em `Atlantic/Cape_Verde`); data diferente → 422 `DATA_PAGAMENTO_RETROATIVA`
- Adquirente: recusa 422 com campo em falta quando o cliente não tem NIF `^[1-9]\d{8}$`, nome (3–150) ou morada (≤100)
- Guardas 409: apagar pagamento faturado; apagar cliente, processo ou honorário com documentos fiscais
- Fusão de clientes: `cliente_id` dos documentos passa para o cliente resultante (UPDATE nativo da coluna de ligação; snapshot do adquirente inalterado) — verificar por IT que funciona com `@Immutable`

#### Consulta
- Nova página `/financeiro/documentos-fiscais` (separador/botão no topo do Financeiro), gated `financeiro:view` em ambas as camadas
- Lista: filtros cliente, período, tipo, estado; paginação server-side; padrão `DataTable` partilhado (`web/src/components/shared/data-table/`)
- Detalhe `/financeiro/documentos-fiscais/[id]` com snapshot, valores, estado de comunicação, ligação a pagamento/honorário; marca "Simulação — sem validade fiscal" visível
- Lista de pagamentos do honorário ganha coluna "Documento fiscal" (n.º da FR ou "Sem documento fiscal")

### Claude's Discretion
- Nomes de DTOs/serviços, divisão em planos/ondas, formato de número apresentado (ex.: `SIM-FR-2026/1`)
- Teste de serviço com dois emitentes (tenant escritório + outro tenant) para provar a parametrização do núcleo desde já (pesquisa C12)

### Deferred Ideas (OUT OF SCOPE)
- Clientes não residentes / consumidor final — futuro (DOCX-02)
- Despesas/provisões fora da base de IVA — futuro (DOCX-03)

**Gate do contabilista:** o utilizador decidiu avançar com as regras parametrizadas e validar com o contabilista depois (STATE.md Pending Todos) — antes de ativar faturação real.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EMIS-01 | Pagamento + FR + conta corrente na mesma operação, tudo ou nada | §Pattern 1 (transaction skeleton), §Pattern 2 (lock order), Pitfalls 1-3; IT `PagamentoFaturadoServiceIT` rollback cases |
| EMIS-02 | Pré-visualização antes de confirmar | §Pattern 6 (pure calculator shared by preview and emission), endpoint in a NEW controller (FaturacaoController is pinned to 7 handlers + `financeiro:manage`) |
| EMIS-03 | Decomposição base/IVA segundo regime; isento mostra motivo | §Code Examples "CalculoFiscal" + test vectors; `MotivoIsencaoIva.mencao()` exists |
| EMIS-04 | Retenção opcional, taxa editável, sobre a base; CC creditada do total | Same calculator; tie vector `0.04 @ 12.5%` discriminates HALF_UP |
| EMIS-05 | Data ≠ hoje (CV) recusada; sem data = hoje | `Clock` + `Atlantic/Cape_Verde`; reuse the date that `NumeracaoService` returns |
| EMIS-06 | Recusa com campo quando cliente sem NIF/nome/morada válidos | `RecusaFiscalException(422, code, msg, campo)` already mapped to `{message, code, campo}` |
| EMIS-07 | Duplo envio → 1 pagamento + 1 FR | §Pattern 3: idempotency check AFTER the per-tenant configuração lock = race-free; UNIQUE as safety net |
| EMIS-08 | Snapshot imutável, sem edição/apagamento | `@Immutable` + narrow `Repository<…>` + reflection test (copy `AuditLogImutabilidadeTest`); avoid `JpaSpecificationExecutor` (has `delete(Specification)`) |
| EMIS-09 | Guardas 409 em deletes; fusão repõe `cliente_id` | §Pattern 5 (guards + row locks), native `@Modifying` UPDATE bypasses `@Immutable` |
| EMIS-10 | `metodo` → meio de pagamento eFatura | `MetodoPagamento` enum (backend), codes tagged ASSUMED for 136 |
| EMIS-11 | Lista/detalhe por tenant, filtros, paginação | Native query + `countQuery` + `CAST(:p AS text) IS NULL` idiom (`AuditLogRepository`, `NotificacaoRepository.buscarPorFiltros`) |
| EMIS-12 | Pagamentos anteriores = "sem documento fiscal", sem faturação retroativa | No backfill code, no "emitir para pagamento existente" endpoint; `listHonorarioPagamentos` returns DTO with nullable `documentoFiscal` |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Domain language is Portuguese (entities, routes, DTOs): `documentoFiscal`, `pagamentoFaturado`, `preVisualizacao`, etc.
- Backend: Spring Boot 3.4.1, run with system `mvn` (no wrapper). In this container only JDK 21: always `-Dmaven.compiler.release=21` (133-01 decision).
- Frontend: pnpm only (`pnpm-lock.yaml` authoritative); ignore stale `package-lock.json`.
- Frontend never calls the backend host; everything through `apiFetch` (`credentials: "include"`) and TanStack Query hooks in `web/src/hooks/use-*.ts`.
- RBAC: `@PreAuthorize("hasAuthority('financeiro:<action>')")` on backend AND `hasScopedPermission`/`permissions.can.*` on frontend — both layers must agree.
- Multi-tenancy: every read/write filtered by `tenant_id` from `UserPrincipal`; never from body/path. This is the primary isolation boundary.
- No Flyway/Liquibase: new tables need a hand-written idempotent script in `backend/migrations/` + a row in `backend/migrations/README.md` in the same phase.
- No files written to backend filesystem (irrelevant here; no PDF in 134).
- Next.js 16: check `web/node_modules/next/dist/docs/` before framework code (done — see Pattern 8).
- Do not build against `web/src/app/_api-backup/` or `web/src/server/` mocks.
- `.planning/config.json`: plan check + verifier + ASVS L1 security enforcement (`block_on: high`), nyquist validation on, docs committed.
- SAST: `mvn spotbugs:check` must pass (CI blocks on it); `spotbugs-exclude.xml` warns "não acrescentar sem a mesma revisão".

## Summary

The phase is one atomic write path plus guards plus read screens. The repo already has every primitive: `NumeracaoService.proximoNumero` (MANDATORY, FOR UPDATE, 5 s `lock_timeout`, refresh-after-lock), `ConfiguracaoFiscalRepository.bloquearPorTenant` (PESSIMISTIC_WRITE, must be the first lock), `ParametroFiscalService`, `RecusaFiscalException` → `{message, code, campo}`, a UTC `Clock` bean, and the `AuditLog` narrow-repository pattern with a reflection test. No new libraries are needed.

The most important design finding: **because every emission takes the tenant's `t_configuracao_fiscal` row lock first, all emissions of one tenant are serialized.** If the idempotency lookup by `(tenant_id, chave_idempotencia)` runs *after* that lock, a racing duplicate waits on the lock and then (READ COMMITTED) sees the first request's committed document and returns it. You do not need to catch `DataIntegrityViolationException` on the normal path. The UNIQUE constraint stays as a safety net. The second key finding: **open-in-view is ON** (documented in `RecusaTransacional.java:20-27` and `AdminController.java:88`). An entity that was already loaded without a lock in the request is returned *stale* by a later `@Lock` query, which is the exact WR-02 bug class Phase 133 proved for `SerieFiscal`. The service must therefore make the locking query the first read of each locked row, or refresh after locking. The controller must decide "ativa?" through a scalar query, never by loading `ConfiguracaoFiscal`.

CFG-03 (disabled path byte-identical) is best kept by moving the current `createPagamento` body (`ResourceController.java:3041-3081`) verbatim into a private method and putting one `if (pagamentoFaturadoService.faturacaoAtiva(tenantId))` in front of it. Prove it three ways: (a) the evolved source guard pins a SHA-256 of the extracted method text and still forbids `NumeracaoService`/`SerieFiscal`/`ConfiguracaoFiscal` in `ResourceController` and any fiscal token in `Pagamento.java`; (b) a Mockito behavioural test covers the disabled branch, including the swallowed `DataAccessException`; (c) an IT checks that no fiscal rows appear for a disabled tenant.

**Primary recommendation:** Build `RegraFiscal` (pure calculator) → entities + script 134 + parity IT → `PagamentoFaturadoService` (lock order configuração → cliente → processo → conta corrente → série, idempotency under the configuração lock) → guarded `ResourceController` edits (delegation, delete guards, merge reponting, list DTO) → new `DocumentoFiscalController` (estado, pré-visualização, list, detail) → frontend (dialog, list, detail, column), each with its own tests.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Fiscal calculation (base/IVA/retenção) | API / Backend (pure service) | — | "Frontend burro": the backend is the single source, so the preview and the emission share one function |
| Atomic emission (pagamento+CC+número+documento+comunicação) | API / Backend (`@Transactional` service) | Database (row locks, UNIQUE) | Atomicity and serialization live in one DB transaction |
| Idempotency | API / Backend | Database (`UNIQUE(tenant_id, chave)`) | The lookup runs under the config lock, and the constraint is the safety net |
| Key generation for idempotency | Browser | — | Generated when the dialog opens, regenerated only after success or close |
| Immutability | Database/ORM (`@Immutable`, `updatable=false`) | API (no PUT/DELETE routes, narrow repo) | Defence in depth: the type system, the routes and a reflection test |
| Delete/merge guards | API / Backend (ResourceController) | Database (row locks) | Must run in the same transaction as the delete or merge |
| Listing + filters + pagination | API / Backend (native query) | Browser (DataTable manual pagination) | Tenant-first SQL; the browser only renders |
| RBAC `financeiro:view/edit` | API (`@PreAuthorize`) | Browser (`permissions.can.*`) | Both layers must agree (CLAUDE.md) |
| "Simulação — sem validade fiscal" marking | Browser | API (`ambiente` field) | Display rule driven by stored `ambiente` |

## Standard Stack

### Core (all already in the project; nothing to install)
| Library | Version (verified) | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.4.1 (`pom.xml:9`) | DI, `@Transactional`, `@PreAuthorize` | Project baseline |
| Hibernate ORM | 6.6.4.Final (`~/.m2/.../hibernate-core/6.6.4.Final`) | `@Immutable`, `@Lock`, native queries | BOM-managed |
| Spring Data JPA | 3.4.1 (`~/.m2/.../spring-data-jpa/3.4.1`) | `Repository<T,ID>`, `@Query(nativeQuery, countQuery)`, `Pageable` | Existing pagination precedent |
| PostgreSQL | 16 (Testcontainers `postgres:16-alpine`) | `FOR UPDATE`, `ON CONFLICT DO NOTHING`, `set_config('lock_timeout')` | Phase 133 idioms |
| Testcontainers | 1.20.4 (BOM; needs `~/.docker-java.properties api.version=1.44` with Docker 29 — present) | ITs against real PG | `NumeracaoServiceConcorrenciaIT`, `ConfiguracaoFiscalConcorrenciaIT` precedent |
| TanStack Query | ^5.87.4 | hooks | Project pattern |
| TanStack Table | ^8.21.3 | DataTable (needs `manualPagination` extension) | Shared DataTable |
| react-hook-form + zod | ^7.62 / ^4.1.5 | form + schema | Project pattern |
| vitest | ^4.1.10 | pure-function tests (no jsdom/RTL installed) | `pnpm test` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Native `@Query` + `countQuery` for the listing | `JpaSpecificationExecutor` | **Rejected.** In Spring Data JPA 3.4.1 it declares `long delete(Specification)` and an un-tenanted `findAll(Specification)` (verified with `javap` on the 3.4.1 jar). That breaks the narrow-repository immutability invariant |
| Idempotency check under the config lock | Catch `DataIntegrityViolationException` outside the transaction and re-read (ARCHITECTURE §4.3) | The catch is still useful as a fallback, but the lock makes it unnecessary for correctness. Catching inside the transaction is impossible: PostgreSQL aborts the transaction |
| `MetodoPagamento` Java enum stored as name in existing `t_pagamento.metodo` | New column / new table | No schema change to `t_pagamento`, so `Pagamento.java` is untouched (CFG-03 guard) |

**Installation:** none.

## Package Legitimacy Audit

No external packages are installed in this phase. Every dependency above is already in `backend/pom.xml` / `web/package.json` and was verified in the local Maven repository or `node_modules`. slopcheck was not needed.

| Package | Registry | Disposition |
|---------|----------|-------------|
| (none new) | — | — |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
Browser (financeiro/[id])                          Backend                                   PostgreSQL
──────────────────────────                         ───────                                   ──────────
GET /faturacao/estado-emissao ───────────────────► DocumentoFiscalController (financeiro:view)
   {ativa:false} ─► old form, POST /pagamentos ──► ResourceController.createPagamento
                                                     └─ faturacaoAtiva(tenant)? (scalar query) ─► select ativa
                                                        NO ─► registarPagamentoSemFaturacao(pag)  [verbatim legacy]
   {ativa:true}
   "Registar" ─► chave = uuid()  ─► POST /faturacao/pre-visualizacao ─► PreVisualizacaoService (readOnly, no locks)
                 dialog shows base/IVA/retenção/total ◄── same CalculoFiscal + same validations (422 + campo)
   "Confirmar" ─► POST /pagamentos {…, chave} ────► createPagamento ─ YES ─► PagamentoFaturadoService.registar (@Transactional)
                                                      1 set_config(lock_timeout 5s)
                                                      2 bloquearPorTenant (config)  ──────────────► FOR UPDATE t_configuracao_fiscal  [serializes tenant]
                                                      3 findByTenantIdAndChave → found? same payload→200 / else 409
                                                      4 honorario→processo→tenant (404) ; lock cliente ─► FOR UPDATE t_cliente
                                                         lock processo, re-check processo.clienteId ─► FOR UPDATE t_processo
                                                      5 validate data / adquirente / metodo / valor / taxa (422)
                                                      6 config.completa() + regime ; IVA param (if NORMAL)
                                                      7 CalculoFiscal (pure)
                                                      8 CC: insert-on-conflict + lock ; saldo += total ─► FOR UPDATE t_conta_corrente
                                                      9 save Pagamento (data = hoje CV, metodo = enum name) ─► INSERT t_pagamento (IDENTITY id)
                                                     10 NumeracaoService.proximoNumero ─► FOR UPDATE t_serie_fiscal (last lock)
                                                     11 save DocumentoFiscal + Linha + ComunicacaoFiscal(PENDENTE)
                                                     any RuntimeException ⇒ full rollback
   201 {pagamento fields…, documentoFiscal:{id,numeroFormatado,…}} ◄─ commit
   toast + invalidate [honorarios,pagamentos,id] [clientes,conta-corrente] [documentos-fiscais]

/financeiro/documentos-fiscais ─► GET /documentos-fiscais?clienteId&de&ate&tipo&estado&page&size ─► native query tenant-first + LEFT JOIN t_comunicacao_fiscal
/financeiro/documentos-fiscais/[id] ─► GET /documentos-fiscais/{id} ─► findByIdAndTenantId → 404 cross-tenant

DELETE /pagamentos/{id} | /clientes/{id} | /processos/{id} | /honorarios/{id} ─► exists document? 409
POST /clientes/merge ─► lock both clientes (UUID order) → … → native UPDATE t_documento_fiscal SET cliente_id
```

### Recommended Project Structure
```
backend/src/main/java/com/lexcv/
├── models/
│   ├── DocumentoFiscal.java            # @Immutable, @Getter+@Builder, no setters
│   ├── DocumentoFiscalLinha.java       # @Immutable
│   ├── ComunicacaoFiscal.java          # mutable satellite, @Version
│   ├── EstadoComunicacaoFiscal.java (+Converter)   # PENDENTE (+ future states as values only)
│   └── MetodoPagamento.java            # DINHEIRO/TRANSFERENCIA/CHEQUE/CARTAO/OUTRO + codigoMeioPagamento
├── repositories/
│   ├── DocumentoFiscalRepository.java          # extends Repository<DocumentoFiscal, UUID> — save, finders by tenant, exists*, buscar (Page)
│   ├── DocumentoFiscalLinhaRepository.java     # Repository<…> — save, findByTenantIdAndDocumentoFiscalId…
│   ├── DocumentoFiscalLigacaoClienteRepository.java  # ONLY the native @Modifying repontarCliente (pinned)
│   └── ComunicacaoFiscalRepository.java        # JpaRepository OK (mutable) but tenant finders only
├── services/fiscal/
│   ├── CalculoFiscal.java / RegraFiscalService.java  # pure, no Spring state
│   ├── ValidacaoEmissao.java                 # adquirente/data/metodo/valor/taxa → RecusaFiscalException 422
│   ├── PagamentoFaturadoService.java          # faturacaoAtiva(), registar()
│   ├── PreVisualizacaoFaturaService.java      # readOnly, shares Calculo + Validacao
│   └── DocumentoFiscalConsultaService.java    # list/detail DTOs, guard helpers (existePara*)
├── controllers/DocumentoFiscalController.java # NEW: estado, pre-visualizacao, list, detail
└── dtos/ PagamentoRequest, PreVisualizacaoRequest/Response, DocumentoFiscalResumo/Detalhe, PagamentoComDocumentoResponse
backend/migrations/134-create-documento-fiscal-tables.sql  (+ README row 20)
web/src/
├── hooks/use-documentos-fiscais.ts
├── types/documentos-fiscais.ts, schemas/financeiro.ts (extended)
├── lib/idempotencia.ts                       # uuid with getRandomValues fallback
├── app/(dashboard)/financeiro/[id]/pagamento-faturado-dialog.tsx
├── app/(dashboard)/financeiro/documentos-fiscais/page.tsx + columns.tsx
└── app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
```

### Pattern 1: Current behaviour to preserve / change (cited)

| Handler | Lines | Current behaviour | Phase 134 change |
|---|---|---|---|
| `createPagamento` | `ResourceController.java:3039-3082` | `@RequestBody Pagamento` (entity, SpotBugs `ENTITY_MASS_ASSIGNMENT` excluded by name, `spotbugs-exclude.xml:52`), NOT transactional. Order: 400 if `honorarioId` null → 404 honorário → 404 processo/tenant → 400 `valorPago` ≤ 0 → `setId(null)` → `save` (own tx, `:3058`) → CC `findByClienteId` + `orElseGet(save)` + read-modify-write inside `try` whose `catch (DataAccessException)` only `log.warn`s (`:3062-3078`) → 201 entity | Extract `:3042-3081` verbatim into `private ResponseEntity<?> registarPagamentoSemFaturacao(Pagamento pag)`; handler takes `PagamentoRequest` DTO, maps to `Pagamento` for the legacy branch. **Do NOT put `@Transactional` on the handler** (P-02 rollback-only trap) |
| `listHonorarioPagamentos` | `:3025-3037` | returns `List<Pagamento>` entities | return `List<PagamentoComDocumentoResponse>` (same 5 fields + nullable `documentoFiscal {id, numeroFormatado}`), batch lookup `findByTenantIdAndPagamentoIdIn` → `Map` (no N+1) |
| `deleteHonorario` | `:3137-3155` | already 409 when any `Pagamento` exists (`:3149-3151`) | Already covered transitively (a faturado pagamento can't be deleted), but add an explicit `existsByTenantIdAndHonorarioId` → 409 `HONORARIO_COM_DOCUMENTOS_FISCAIS` before it (defence for 135's estornos) |
| `deletePagamento` | `:3156-3187` | non-transactional; subtract CC (swallowed `DataAccessException`, `:3180`) then `deleteById` (`:3184`) | After the tenant check and before any CC change: `existsByTenantIdAndPagamentoId` → 409 `PAGAMENTO_FATURADO`. No other change |
| `deleteCliente` | `:616-630` | `clientes:edit`, non-transactional; deletes CC then cliente; no FK checks (bare UUIDs) | Make `@Transactional`; first lock cliente (FOR UPDATE, tenant-scoped); `existsByTenantIdAndClienteId` → 409 `CLIENTE_COM_DOCUMENTOS_FISCAIS` via `RecusaTransacional.recusar` |
| `deleteProcesso` | `:1245-1255` | `processos:edit`, deletes without checking honorários | Same as deleteCliente with `existsByTenantIdAndProcessoId` → 409 `PROCESSO_COM_DOCUMENTOS_FISCAIS` |
| `mergeClientes` | `:833-956` | `@Transactional`; copies blank fields, moves processos/contactos/notas/CC saldo/documentos/advogados/administrativos/pareceres, deletes secondary (`:941`) | Lock BOTH clientes first (FOR UPDATE, ascending UUID to avoid merge-vs-merge deadlock); before `clienteRepository.delete(secondary)` call `repontarCliente(tenantId, secondaryId, primaryId)`; add `moved_documentos_fiscais` to the response map |
| `ContaCorrente` | `ContaCorrente.java` | `Integer` IDENTITY, `cliente_id UNIQUE`, no `@Version`, no lock finder; `@PrePersist/@PreUpdate` uses `LocalDateTime.now()` | Add `ContaCorrenteRepository.bloquearPorCliente` (`@Lock(PESSIMISTIC_WRITE)` JPQL) + native `criarSeNaoExiste` (`INSERT … ON CONFLICT (cliente_id) DO NOTHING`). The legacy path keeps `findByClienteId` |

Constructor impact: `ResourceController` is `@RequiredArgsConstructor` with 27 dependencies (`:52-85`). Two tests construct it by hand, `ResourceControllerProveniencaPapelTest.java:105-113` and `ResourceControllerUploadDocumentoTest`. Any new field breaks them, so update both in the same task.

### Pattern 2: Lock order (extends the locked order; deadlock-free)

The locked order is **configuração → conta corrente → série**. Research recommends inserting **cliente → processo** between configuração and conta corrente. Without it, `deleteCliente`, `deleteProcesso` and `mergeClientes` have a check-then-act race with an in-flight emission: the document commits after the guard has already passed, and ends up pointing at a deleted cliente. The row locks close that window:

```
Emission:     configuração → cliente → processo → conta corrente → série   (série always last; NumeracaoService javadoc)
Merge:        cliente(min UUID) → cliente(max UUID) → [processos UPDATE] → contas correntes
deleteCliente: cliente → (exists check) → CC delete → cliente delete
deleteProcesso: processo → (exists check) → delete
```
No cycle: every path takes cliente before processo before conta corrente, and only emission takes configuração/série. Merge's `processoRepository.saveAll` (`:880`) writes processos *after* the cliente locks, which matches emission (cliente before processo). If emission reads `processo.clienteId` before it locks the cliente, it must re-check `processo.clienteId == cliente.id` after locking the processo. On a mismatch (a merge moved it), refuse with 409 `PROCESSO_ALTERADO_TENTE_NOVAMENTE`. If the planner keeps exactly the CONTEXT order, record the residual race as an accepted risk. [VERIFIED: codebase reading; deadlock reasoning is analysis, not tested — the IT below must prove it]

`lock_timeout`: today only `NumeracaoService` calls `serieFiscalRepository.definirLockTimeoutLocal()` (`NumeracaoService.java:83`). In emission the config, cliente and CC locks come *before* it, so call `definirLockTimeoutLocal()` as the first statement of `registar`. Calling it twice is harmless. Catch `PessimisticLockingFailureException | PessimisticLockException | LockTimeoutException` around the early locks and convert to 503 `FATURACAO_OCUPADA`, the same mechanism the 133 IT proved for `SERIE_INDISPONIVEL`. [VERIFIED: NumeracaoService.java:79-92; 133-03-SUMMARY scenario 9]

### Pattern 3: Idempotency under the configuração lock

```java
@Transactional
public ResultadoPagamentoFaturado registar(UUID tenantId, UserPrincipal autor, PagamentoRequest req) {
    exigirChave(req);                                  // 400/422 CHAVE_IDEMPOTENCIA_OBRIGATORIA (UUID, not null)
    serieFiscalRepository.definirLockTimeoutLocal();   // bounds ALL locks below
    ConfiguracaoFiscal cfg = bloquear(() -> configuracaoFiscalRepository.bloquearPorTenant(tenantId))
        .filter(ConfiguracaoFiscal::getAtiva)
        .orElseThrow(() -> recusa(409, "FATURACAO_DESLIGADA", ...)); // activation flipped between check and lock
    // Under the config lock, concurrent emissions of this tenant are serialized:
    Optional<DocumentoFiscal> existente = documentoFiscalRepository
        .findByTenantIdAndChaveIdempotencia(tenantId, req.chaveIdempotencia());
    if (existente.isPresent()) {
        DocumentoFiscal d = existente.get();
        if (!mesmoPedido(d, req)) throw recusa(409, "CHAVE_REUTILIZADA", ...);
        return ResultadoPagamentoFaturado.repetido(d);  // controller → 200
    }
    ... // steps 4-11 of the diagram
    return ResultadoPagamentoFaturado.novo(...);       // controller → 201
}
```
- `mesmoPedido` compares the stored `honorario_id`, `total_documento` (`compareTo`), `metodo_pagamento`, `taxa_retencao` (null-safe) and, when sent, `dataPagamento` against the request.
- Why it is race-free: request B blocks in `bloquearPorTenant` until A commits. PostgreSQL READ COMMITTED gives each statement a fresh snapshot, so B's lookup query sees A's committed row. A JPQL query always hits the DB (it auto-flushes first), not the first-level cache. [VERIFIED: PostgreSQL default isolation READ COMMITTED; no isolation override in repo — grep]
- Safety net: `UNIQUE(tenant_id, chave_idempotencia)` + `UNIQUE(pagamento_id)` + `UNIQUE(tenant_id, serie_id, numero)`. If one fires, it fires at commit outside the service → `DataIntegrityViolationException` → catch-all 500 with `referencia` (WR-06 fix). Optional belt-and-braces: the controller catches DIV, re-reads by chave in a new transaction, and returns 200 if found (precedent: `PlatformAdminController.java:103-132` catches outside the transaction).
- A failed attempt (422) stores nothing, so a retry with the same key is processed fresh. The frontend regenerates the key only after success or when the dialog closes.

### Pattern 4: Immutable entity + narrow repository + native re-pointing

- `DocumentoFiscal`/`DocumentoFiscalLinha`: `@Entity @Immutable`, every `@Column(updatable = false)`, `@Getter @Builder @NoArgsConstructor(access = PROTECTED) @AllArgsConstructor(access = PRIVATE)`, no `@Setter`/`@Data`. UUID PK (`GenerationType.UUID`). `createdAt`/`emitidoEm` set from the injected `Clock` (no `@PrePersist`; 133-01 note).
- Repository `extends Repository<DocumentoFiscal, UUID>`, never `JpaRepository`, `CrudRepository` or `JpaSpecificationExecutor`. Methods: `save`, `findByIdAndTenantId`, `findByTenantIdAndChaveIdempotencia`, `findByTenantIdAndPagamentoIdIn`, `existsByTenantIdAndPagamentoId|ClienteId|ProcessoId|HonorarioId`, `buscar(...)` (Page). Pin the set with a test copied from `AuditLogImutabilidadeTest.java` (tests 1-6, path token `documentos-fiscais`/`documento_fiscal`).
- Hibernate 6.6.4 `@Immutable` behaviour:
  - Dirty changes on a managed immutable entity are ignored at flush (no UPDATE). [CITED: Hibernate `@Immutable` javadoc — behaviour also relied on by `AuditLog`]
  - A JPQL/HQL `UPDATE` on an immutable entity runs with a warning by default. `ImmutableEntityUpdateQueryHandlingMode.interpret(null)` returns `WARNING`, with `EXCEPTION` as the alternative. [VERIFIED: `javap -c` on hibernate-core-6.6.4.Final]
  - A **native** SQL UPDATE bypasses the check entirely.
  - Recommendation: re-point with a native query in its own tiny repository, so the main repository keeps "zero `@Modifying`":
```java
public interface DocumentoFiscalLigacaoClienteRepository extends Repository<DocumentoFiscal, UUID> {
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE t_documento_fiscal SET cliente_id = :novo "
            + "WHERE tenant_id = :tenantId AND cliente_id = :antigo")
    int repontarCliente(@Param("tenantId") UUID tenantId, @Param("antigo") UUID antigo, @Param("novo") UUID novo);
}
```
  - Optional hardening: set `spring.jpa.properties.hibernate.query.immutable_entity_update_query_handling_mode: exception` in `application.yml`, so any future JPQL UPDATE on `DocumentoFiscal` or `AuditLog` throws. This is a fixed property, not an env var. Grep shows no JPQL updates on `AuditLog` today. [ASSUMED: no other immutable entity is JPQL-updated — grep before enabling]
  - Whether `em.remove()` on an `@Immutable` entity is blocked by Hibernate was not verified. The narrow repository makes it uncallable regardless.

### Pattern 5: Delete guards and merge (see table in Pattern 1)

Guard checks go through a small service, e.g. `DocumentoFiscalConsultaService.existePara*`, so `ResourceController` never references `ConfiguracaoFiscal`/`NumeracaoService`/`SerieFiscal`. That keeps the evolved guard test simple. All 409s use `RecusaFiscalException(HttpStatus.CONFLICT, code, msg)` or `ResponseEntity` with `{message, code}`. Inside the newly `@Transactional` delete handlers, return refusals through `RecusaTransacional.recusar` (precedent, `RecusaTransacional.java`), because OSIV plus commit-on-normal-return would otherwise flush earlier in-memory edits.

### Pattern 6: Pure calculator shared by preview and emission

```java
// services/fiscal/CalculoFiscal.java — no Spring, no constants for rates (gate ParametrosFiscaisSemConstantesTest)
public record ResultadoCalculo(BigDecimal base, BigDecimal iva, BigDecimal taxaIva,
                               BigDecimal retencao, BigDecimal taxaRetencao,
                               BigDecimal total, BigDecimal liquidoRecebido) {}

private static final BigDecimal CEM = new BigDecimal("100");   // allowed: not 15/20

public static ResultadoCalculo calcular(BigDecimal total, RegimeIva regime, BigDecimal taxaIvaPct,
                                        BigDecimal taxaRetencaoPctOuNull) {
    BigDecimal t = total.setScale(2, RoundingMode.UNNECESSARY);         // caller already normalized & validated scale ≤ 2
    BigDecimal base, iva, taxaIva;
    if (regime == RegimeIva.ISENTO) { base = t; iva = BigDecimal.ZERO.setScale(2); taxaIva = BigDecimal.ZERO; }
    else {
        taxaIva = taxaIvaPct;
        base = t.multiply(CEM).divide(CEM.add(taxaIvaPct), 2, RoundingMode.HALF_UP); // one division, no 1.15 intermediate
        iva = t.subtract(base);                                                      // residue goes to IVA
    }
    BigDecimal ret = taxaRetencaoPctOuNull == null ? BigDecimal.ZERO.setScale(2)
        : base.multiply(taxaRetencaoPctOuNull).divide(CEM, 2, RoundingMode.HALF_UP);
    return new ResultadoCalculo(base, iva, taxaIva, ret, taxaRetencaoPctOuNull, t, t.subtract(ret));
}
```
Invariants to assert for every vector: `base + iva == total`, `total − retencao == liquido`, every scale is 2, and every amount ≥ 0.

### Pattern 7: Listing query (tenant-first native + countQuery)

```java
@Query(value = "SELECT d.* FROM t_documento_fiscal d "
  + "LEFT JOIN t_comunicacao_fiscal c ON c.documento_fiscal_id = d.id AND c.tenant_id = d.tenant_id "
  + "WHERE d.tenant_id = :tenantId "
  + "AND (CAST(:clienteId AS text) IS NULL OR d.cliente_id = CAST(CAST(:clienteId AS text) AS uuid)) "
  + "AND (CAST(:tipo AS text) IS NULL OR d.tipo = CAST(:tipo AS text)) "
  + "AND (CAST(:estado AS text) IS NULL OR c.estado = CAST(:estado AS text)) "
  + "AND (CAST(:de AS date) IS NULL OR d.data_emissao >= CAST(:de AS date)) "
  + "AND (CAST(:ate AS date) IS NULL OR d.data_emissao <= CAST(:ate AS date)) "
  + "ORDER BY d.data_emissao DESC, d.ano DESC, d.numero DESC",
  countQuery = "SELECT count(*) FROM t_documento_fiscal d LEFT JOIN … same WHERE …",
  nativeQuery = true)
Page<DocumentoFiscal> buscar(@Param("tenantId") UUID tenantId, @Param("clienteId") String clienteId,
                             @Param("tipo") String tipo, @Param("estado") String estado,
                             @Param("de") LocalDate de, @Param("ate") LocalDate ate, Pageable pageable);
```
- Bind optional UUIDs as `String`, following the `AuditLogRepository.buscarEventosRbac` precedent: PostgreSQL cannot type a bare null bind. The IT must exercise every filter both null and non-null. The `CAST(:de AS date)` with a null `LocalDate` must be proven by the IT. [VERIFIED idiom: AuditLogRepository.java, NotificacaoRepository.java:38]
- Controller: `page ≥ 0`, `1 ≤ size ≤ 100`, otherwise 400 (precedent `AuditoriaRbacController.java:97-111`). Response `{content, totalElements, totalPages, page, size}`. Use an unsorted `PageRequest.of(page, size)` so no Sort is appended to the native SQL. Validate `tipo`/`estado` against the enums and `de ≤ ate`, otherwise 400.
- Hydrate `estadoComunicacao` for the page with one `findByTenantIdAndDocumentoFiscalIdIn` → Map (no N+1).

### Pattern 8: Frontend

- **Routing (Next 16.2.6):** dynamic segments get `params` as a Promise. Client pages use `use(params)`, server pages use `await params`. [CITED: web/node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/dynamic-routes.md] A static `financeiro/documentos-fiscais/` folder next to the existing `financeiro/[id]/` works: the repo already has `financeiro/novo/` beside `[id]`, and static segments match before dynamic ones. Nest `documentos-fiscais/[id]/page.tsx`, and type its id as a UUID string (the existing `[id]` page parses `Number(params.id)` and is unaffected).
- **DataTable** (`components/shared/data-table/data-table.tsx:51-75`) only does client-side pagination (`getPaginationRowModel`, internal state). Add optional, backward-compatible props `manualPagination`, `pageCount`, `pagination`, `onPaginationChange` and pass them to `useReactTable`. Existing 5 callers stay unchanged. `DataTablePagination` already reads `table.getPageCount()`/`setPageSize`, so it works with manual mode. Reset `pageIndex` to 0 when filters change, and clamp when `totalPages` shrinks (precedent `settings/auditoria-tab.tsx:97-101`).
- **Hooks** (`use-documentos-fiscais.ts`): `useEstadoEmissao()` (`["faturacao","estado-emissao"]`), `usePreVisualizacaoFatura()` (mutation), `useDocumentosFiscais(filters)` (`["documentos-fiscais","list",filters]`), `useDocumentoFiscal(id)`. `useCreatePagamento` must pass `semToastParaStatus: [409, 422]` (the `ApiError` contract already exists in `lib/api.ts:20-100`). It must also invalidate `["documentos-fiscais"]` and `["honorarios","detail",id]` in `onSettled` (133 WR-04 lesson: invalidate on error too).
- **Key:** `crypto.randomUUID()` is "Available only in secure contexts" [VERIFIED: TypeScript lib.dom.d.ts:8444-8447, sourced from MDN]. Prod is HTTPS (`Caddyfile.prod` uses `{$DOMAIN_NAME}`), but dev `Caddyfile` serves `:80`, so LAN access over http would throw. Add a `lib/idempotencia.ts` helper with a `crypto.getRandomValues` v4 fallback.
- **Form with faturação ativa:** method becomes a required `Select` over the 5 `MetodoPagamento` values. The date field becomes read-only "Hoje" (fewer 422s). Show an `AlertDialog`/`Dialog` with the adquirente (nome, NIF, morada), base, IVA (or isenção mention), retenção checkbox + taxa (prefilled from the preview response `taxaRetencaoSugerida`; changing it re-requests the preview), total, líquido, and the "Simulação — sem validade fiscal" badge. The Confirm button is disabled while `isPending`. Map `campo` from 422 to the form field or a link to the cliente page.
- **No rate constants** in frontend files (`scripts/verify-faturacao.mjs:102-108` regex `\b0\.(15|20)\b`). Extend that script or add `verify:documentos-fiscais`.

### Anti-Patterns to Avoid
- **`@Transactional` on `createPagamento`:** the swallowed `DataAccessException` would turn into `UnexpectedRollbackException` (P-02).
- **Loading `ConfiguracaoFiscal` in the controller to decide delegation:** under OSIV the later `bloquearPorTenant` returns the stale managed instance (WR-02 class). Use `@Query("select c.ativa from ConfiguracaoFiscal c where c.tenantId = :t")` returning `Optional<Boolean>`.
- **Unlocked read then lock of the same row in one transaction:** same stale-instance bug. The lock query must be the first read, or refresh it (`SerieFiscalRepositoryCustomImpl.refrescar` precedent).
- **Reading the IVA rate with `valorVigenteHoje` and the date separately:** they can straddle midnight. Compute `hoje` once and use `valorVigente(codigo, hoje)`. After `proximoNumero`, assert `numero.dataEmissao().equals(hoje)`, otherwise throw (rollback).
- **`LocalDate.now()` without a zone** anywhere in the fiscal package (P-20).
- **Copying `Honorario.descricao` into the line** (sigilo; locked decision).
- **`@Enumerated` or `columnDefinition` on new enum columns:** use `@Convert` + `length = 32` (133 WR-01).
- **`@Check` on new tables:** `ddl-auto` would generate CHECKs that conflict with the "no CHECK on enums" convention, and the parity IT would flag them.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gapless numbering | MAX+1, SEQUENCE, synchronized | `NumeracaoService.proximoNumero` (MANDATORY) | Proven by 9 + 1 Testcontainers scenarios (133-03, WR-02) |
| Config consistency during emission | Re-reading `ativa` without a lock | `ConfiguracaoFiscalRepository.bloquearPorTenant` | CR-01 fix; it also serializes idempotency |
| Rate lookup | Constants | `ParametroFiscalService.valorVigente(codigo, data)` | CFG-04 + source gate test |
| Error contract | Ad-hoc maps | `RecusaFiscalException(status, code, msg, campo)` | Global handler already maps it; frontend `ApiError` reads `code`/`campo` |
| Immutability test | New reflection logic | Copy `AuditLogImutabilidadeTest` | Already covers repository base types, method set, `@Modifying`, routes, SQL fragments |
| Pagination plumbing | Custom paging | Native `@Query` + `countQuery` + `Pageable`, `{content,totalElements,totalPages,page,size}` | `AuditoriaRbacController` precedent |
| Migration/schema parity | Eyeballing | Copy `MigracaoFiscal133IT` (column parity incl. `column_default`, idempotency, no CHECK, second boot emits no DDL) | Caught 3 real bugs in 133 |
| Exemption text | Free text | `MotivoIsencaoIva.porCodigo(code).mencao()` | 21 official codes already in the enum |

## Runtime State Inventory

Not a rename/refactor phase, but it touches existing data, so for completeness:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Existing `t_pagamento.metodo` holds free text (seed uses `TRANSFERENCIA`, `DINHEIRO`: `DatabaseSeeder.java:306,314`) | None. Legacy values are displayed raw. Only new faturado payments must use the enum names |
| Live service config | None — verified by grep (no external service in 134) | None |
| OS-registered state | None | None |
| Secrets/env vars | None new (no new `application.yml` env keys; the optional Hibernate property is a fixed value) | None |
| Build artifacts | None | None |
| Schema prerequisite | `ON CONFLICT (cliente_id)` needs a unique index on `t_conta_corrente.cliente_id`. It exists where `ddl-auto` created the table from `ContaCorrente.java` (`unique = true`), but no migration script asserts it | The 134 script adds `CREATE UNIQUE INDEX IF NOT EXISTS` guarded by a duplicate check (abort with a clear message if duplicates exist) |

## Common Pitfalls

### Pitfall 1: The CFG-03 guard test blocks the phase, or is relaxed into meaninglessness
**What goes wrong:** `FaturacaoDesligadaPagamentoInalteradoTest` forbids the tokens `ConfiguracaoFiscal`, `NumeracaoService`, `SerieFiscal`, `faturacao`, `Faturacao` in `ResourceController.java` and `Pagamento.java`. A developer deletes it.
**How to avoid:** Evolve it in the same task that adds delegation:
- (1) `Pagamento.java`: keep the full token ban.
- (2) `ResourceController.java`: keep the ban on `ConfiguracaoFiscal`, `NumeracaoService`, `SerieFiscal`. Allow exactly one occurrence of `pagamentoFaturadoService.faturacaoAtiva(` inside `createPagamento`.
- (3) Pin the SHA-256 of the whitespace-normalized text of `registarPagamentoSemFaturacao`. Its content must equal the old `:3042-3081` body.
- (4) Add a Mockito behavioural test of the disabled branch: same repository calls, 201 with the entity, swallowed `DataAccessException`, and `registar` never called.

Beware: the path segment `/faturacao/` in the new controller's paths is not in `ResourceController`, so it's fine.
**Warning signs:** the test file deleted or `@Disabled`; tokens list shortened without a replacement.

### Pitfall 2: OSIV stale instance under `@Lock`
**What goes wrong:** Lost update on `ContaCorrente.saldo`, or a decision made on a stale `ativa`.
**Why it happens:** Open-in-view keeps one persistence context per request. Hibernate returns the already-managed instance from a locking query without re-hydrating it. Phase 133 reproduced this (WR-02: the number `2` was issued twice).
**How to avoid:** lock-first reads; a scalar `ativa` query in the controller; a unit test (like 133's `mutacoesBloqueiamAConfiguracaoAntesDasSeries`) asserting via Mockito `InOrder` that each lock happens before any other finder on the same entity.

### Pitfall 3: lock_timeout only covers the series
**What goes wrong:** A stuck config or CC lock holds the HTTP thread indefinitely.
**How to avoid:** call `definirLockTimeoutLocal()` first in `registar`; map lock failures to 503.

### Pitfall 4: Midnight / year boundary
**What goes wrong:** Validation says "today = 31 Dec" while `proximoNumero` picks the 2027 series.
**How to avoid:** one `hoje` from `LocalDate.now(clock.withZone(Atlantic/Cape_Verde))`; assert it equals `numero.dataEmissao()`; tests with `Clock.fixed` at 23:59:59 and 00:00:01 CV (= 00:59:59 / 01:00:01 UTC).

### Pitfall 5: BigDecimal scale/precision
**What goes wrong:** `100.005`, `1e3` or `-0` get through; the `numeric(38,2)` column rounds silently, so the document totals ≠ the stored pagamento (P-19).
**How to avoid:** reject `stripTrailingZeros().scale() > 2`, ≤ 0, and a magnitude that doesn't fit `numeric(19,2)`; normalize with `setScale(2)` before calculating; store the normalized value in the `Pagamento`. Taxa: `0 < taxa ≤ 100`, scale ≤ 2 (decide; `numeric(7,4)` column).

### Pitfall 6: Narrow repository accidentally widened
**What goes wrong:** Someone adds `JpaSpecificationExecutor` for filters → `delete(Specification)` appears (verified on 3.4.1).
**How to avoid:** a reflection test asserts it is not assignable to `JpaSpecificationExecutor`, `CrudRepository` or `JpaRepository`.

### Pitfall 7: New endpoints in the wrong controller
**What goes wrong:** Putting pré-visualização/estado in `FaturacaoController`. Its class gate is `financeiro:manage` (`FaturacaoController.java:51-52`) and `FaturacaoControllerTest` pins exactly 7 handlers and no method-level `@PreAuthorize`, so edit users get 403 and the tests fail.
**How to avoid:** a new `DocumentoFiscalController` with method-level gates. Two controllers may share the `/api/v1/faturacao` prefix.

### Pitfall 8: Constructor-injection test breakage
`ResourceController` tests use `new ResourceController(27 args)`. Adding services breaks compilation of `ResourceControllerProveniencaPapelTest` and `ResourceControllerUploadDocumentoTest`. Update them in the same task.

### Pitfall 9: Rate literals
`ParametrosFiscaisSemConstantesTest` fails on any `0.15`/`0.20` and on `BigDecimal("15"|"20")`/`valueOf(15|20)` outside `DatabaseSeeder`, comments included. Tests may use them (`src/test` is not scanned); production code must not.

### Pitfall 10: Response shape break in the frontend
The disabled path returns the `Pagamento` entity. Make the active path return a **superset** (`id, honorarioId, valorPago, dataPagamento, metodo` + `documentoFiscal`) so `useCreatePagamento<Pagamento>` keeps working. Type `documentoFiscal?` as optional.

## Code Examples

### Rounding test vectors (computed with Python `decimal`, HALF_UP)
| total | IVA % | retenção % | base | IVA | retenção | líquido |
|---|---|---|---|---|---|---|
| 120000.00 | 15 | 20 | 104347.83 | 15652.17 | 20869.57 | 99130.43 (CONTEXT example — matches) |
| 0.01 | 15 | — | 0.01 | 0.00 | 0.00 | 0.01 |
| 0.05 | 15 | 20 | 0.04 | 0.01 | 0.01 | 0.04 |
| 1.00 | 15 | 20 | 0.87 | 0.13 | 0.17 | 0.83 |
| 100.00 | 15 | 20 | 86.96 | 13.04 | 17.39 | 82.61 |
| 115.00 | 15 | 20 | 100.00 | 15.00 | 20.00 | 95.00 |
| 33.33 | 15 | 20 | 28.98 | 4.35 | 5.80 | 27.53 |
| 999999.99 | 15 | 20 | 869565.21 | 130434.78 | 173913.04 | 826086.95 |
| 12345.67 | 15 | 100 | 10735.37 | 1610.30 | 10735.37 | 1610.30 |
| 50000.00 | 15 | 0.01 | 43478.26 | 6521.74 | 4.35 | 49995.65 |
| 120000.00 | ISENTO | 20 | 120000.00 | 0.00 | 24000.00 | 96000.00 |
| 0.04 | ISENTO | 12.5 | 0.04 | 0.00 | **0.01** (HALF_EVEN would give 0.00 — discriminating tie) | 0.03 |

With 15% IVA no exact `.xx5` tie can occur in the base (`total·100/115` has denominator 23), so the HALF_UP-vs-HALF_EVEN discrimination comes from the retention vector. Add a parameterized property test (e.g. every total 0.01..2000.00 step 0.01) asserting the invariants.

### Payment-method mapping (EMIS-10)
```java
public enum MetodoPagamento {
    DINHEIRO("Dinheiro", "10"),           // UNCL4461 10 = In cash        [CITED: UNCL4461 via search results]
    TRANSFERENCIA("Transferência bancária", "30"), // 30 = Credit transfer [CITED]; 42 "Payment to bank account" is an alternative [ASSUMED]
    CHEQUE("Cheque", "20"),               // 20 = Cheque                  [CITED]
    CARTAO("Cartão / Multibanco", "48"),  // 48 = Bank card               [CITED]
    OUTRO("Outro", "ZZZ");                // ZZZ = Mutually defined (or "1" Instrument not defined) [ASSUMED]
    // codigoMeioPagamento snapshotted in t_documento_fiscal.meio_pagamento_codigo; 136 validates against CV's PaymentMeansCode_D19B XSD
}
```
Store `metodo.name()` in the existing `t_pagamento.metodo` (legacy `TRANSFERENCIA`/`DINHEIRO` already match). Reject unknown values with 422 `METODO_PAGAMENTO_INVALIDO` (campo `metodo`) only on the active path.

### Proposed `t_documento_fiscal` columns (planner adjusts names)
`id uuid pk, tenant_id uuid not null, tipo varchar(32), ambiente varchar(32), serie_id uuid, serie_codigo varchar(20), ano int, numero bigint, numero_formatado varchar(40)` (e.g. `SIM-FR-2026/1`), `data_emissao date, emitido_em timestamptz, emitente_nif varchar(9), emitente_firma varchar(200), emitente_morada varchar(100), emitente_localidade varchar(100), emitente_regime_iva varchar(32), emitente_motivo_isencao_codigo varchar(2), emitente_motivo_isencao_mencao varchar(…), adquirente_nif varchar(9), adquirente_nome varchar(150), adquirente_morada varchar(100), cliente_id uuid, processo_id uuid, honorario_id int, pagamento_id int, metodo_pagamento varchar(32), meio_pagamento_codigo varchar(3), moeda varchar(3)='CVE', taxa_iva numeric(7,4), total_base/total_iva/total_retencao/total_documento/valor_liquido numeric(19,2), taxa_retencao numeric(7,4) null, chave_idempotencia uuid, emitido_por_id uuid, emitido_por_nome varchar(255)`.
Constraints: `uk_documento_fiscal_numero (tenant_id, serie_id, numero)`, `uk_documento_fiscal_pagamento (pagamento_id)`, `uk_documento_fiscal_chave (tenant_id, chave_idempotencia)`. Indexes `(tenant_id, data_emissao)`, `(tenant_id, cliente_id)`, `(tenant_id, processo_id)`, `(tenant_id, honorario_id)`.
`t_documento_fiscal_linha`: `id, tenant_id, documento_fiscal_id, numero_linha, descricao varchar(200), quantidade numeric(19,4), preco_unitario, valor_base, taxa_iva, valor_iva, motivo_isencao_codigo, taxa_retencao, valor_retencao, total_linha`; `uk (documento_fiscal_id, numero_linha)`.
`t_comunicacao_fiscal`: `id, tenant_id, documento_fiscal_id (unique), ambiente, estado varchar(32), tentativas int default 0, proxima_tentativa_em timestamptz, created_at, updated_at, versao bigint`. Keep it minimal; 136 adds error/reference columns with its own script.
Note: SUMMARY.md also mentions `EntregaDocumento` (email) PENDENTE. CONTEXT only locks `t_comunicacao_fiscal`, so defer email delivery rows to 137.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@Enumerated(STRING)` columns | `@Convert` + `length=32` (no CHECK, no ALTER each boot) | Phase 133 (WR-01) | Mandatory for new enum columns |
| Catch-all returned `ex.getMessage()` | `{"message":"Erro interno…","referencia":uuid}` | Phase 133 (WR-06) | Commit-time DIV no longer leaks SQL |
| Next params as object | `params: Promise<…>` + `use()`/`await` | Next 15+ (docs in node_modules 16.2.6) | Detail page must unwrap params |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | eFatura payment-means codes: 10/20/30/48 (UNCL4461, from search results), 42 or 30 for transfer, ZZZ or 1 for "outro", and CV's D19B XSD accepts them | Code Examples | Low in 134 (snapshot only); 136 may need a remap → keep the code as data in the enum, not hard-wired elsewhere |
| A2 | Spring Boot's ObjectMapper ignores unknown JSON properties by default, so switching the `createPagamento` parameter from the entity to a DTO doesn't change accepted payloads | Pattern 1 | Medium — the behavioural test for the disabled path must post a payload with an extra field and a legacy `id` field |
| A3 | Every install has a unique index on `t_conta_corrente.cliente_id` | Runtime State | `ON CONFLICT` fails at runtime → the script must create it, guarded |
| A4 | Static `documentos-fiscais` segment wins over the sibling `[id]` | Pattern 8 | Low — `financeiro/novo` already proves it in this repo |
| A5 | Adding cliente/processo row locks to the locked lock order is acceptable | Pattern 2 | Needs planner/user ack; otherwise document the residual TOCTOU |
| A6 | Active-path response as a superset of the `Pagamento` JSON | Pitfall 10 | Low |
| A7 | Fiscal rules (IVA included, rounding, retention on the base, isento) | Summary | Already gated: contabilista validation pending (STATE.md) before real billing |
| A8 | Hibernate `@Immutable` does not block `remove()` | Pattern 4 | None if the narrow repository is enforced |
| A9 | Setting `hibernate.query.immutable_entity_update_query_handling_mode=exception` breaks nothing | Pattern 4 | Grep JPQL updates before enabling; optional |

## Open Questions (RESOLVED — see 134-CONTEXT.md <research_resolutions> R-01..R-05)

1. **Overpayment** (valorPago > restante of the honorário)
   - What we know: the legacy path allows it; FR on an overpayment may be legitimate (an advance).
   - Recommendation: keep it allowed (no change from legacy), and flag it for the contabilista.
2. **Audit event for emission** (`AuditLog`)
   - Not required by CONTEXT. `AuditoriaFiscalService` exists (MANDATORY).
   - Recommendation: optional and cheap; planner's choice.
3. **Adquirente localidade** — `Cliente.localidade` exists, but CONTEXT's snapshot list omits it. Recommendation: snapshot it as a nullable column (no validation) so 136/137 don't need a schema change.
4. **Lock order extension (A5)** — confirm with the user, or accept the residual race explicitly.
5. **Should the preview require `financeiro:edit` or `view`?** Recommendation: `edit` (the same gate as `POST /pagamentos`; the preview reveals nothing new, but it belongs to the write flow).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | backend build/tests | ✓ | 21.0.11 (pom says 23) | `-Dmaven.compiler.release=21` |
| Maven | backend | ✓ | 3.9.11 | — |
| Docker daemon | Testcontainers ITs | ✓ (server 29.6.2 responding now) | 29.6.2 | `~/.docker-java.properties` `api.version=1.44` is present; if the daemon is down, note it (starting it is out of scope) |
| Node/pnpm, vitest, tsc | frontend | ✓ (`node_modules/.bin/vitest`, `tsc`) | vitest ^4.1.10 | — |
| PostgreSQL image | ITs | pulled by Testcontainers (`postgres:16-alpine`) | 16 | — |

**Missing dependencies with no fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (surefire), Testcontainers 1.20.4 `@DataJpaTest` + `@ServiceConnection` (failsafe `*IT`), vitest 4 (web), Node `verify:*.mjs` source gates |
| Config file | `backend/src/test/resources/application.properties` (bogus datasource forces `Replace.NONE`+`@ServiceConnection`), `web/vitest.config.ts` |
| Quick run command | `cd backend && mvn -q -Dmaven.compiler.release=21 test -Dtest='CalculoFiscalTest,PagamentoFaturadoServiceTest,FaturacaoDesligadaPagamentoInalteradoTest'` |
| Single IT | `cd backend && mvn -Dmaven.compiler.release=21 -Dtest=NenhumTeste -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PagamentoFaturadoServiceIT verify` |
| Full suite command | `cd backend && mvn -Dmaven.compiler.release=21 verify && mvn -Dmaven.compiler.release=21 spotbugs:check` ; `cd web && pnpm test && npx tsc --noEmit && pnpm lint && pnpm verify:faturacao && pnpm verify:documentos-fiscais` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EMIS-01 | All-or-nothing: an injected failure after the pagamento save / after numbering / at the document insert (e.g. a duplicated `pagamento_id`) leaves 0 pagamento, 0 doc, CC unchanged, series counter unchanged | IT | `-Dit.test=PagamentoFaturadoServiceIT` | ❌ Wave 0 |
| EMIS-01 | Lock order: config → cliente → processo → CC → série; lock before any other finder (Mockito `InOrder`) | unit | `-Dtest=PagamentoFaturadoServiceTest` | ❌ |
| EMIS-01 | Concurrency: N parallel emissions, same tenant/different clients → numbers 1..N, CC sums exact; two tenants independent (two emitters, C12) | IT | `-Dit.test=PagamentoFaturadoConcorrenciaIT` | ❌ |
| EMIS-02 | Preview = emission figures for the same input; preview writes nothing (row counts and series counter unchanged); 422s identical | unit + IT | `-Dtest=PreVisualizacaoFaturaServiceTest`, IT case | ❌ |
| EMIS-03/04 | Vectors table + invariants property test; isento uses `mencao`; CC += total | unit | `-Dtest=CalculoFiscalTest` | ❌ |
| EMIS-05 | null → hoje; yesterday/tomorrow → 422 `DATA_PAGAMENTO_RETROATIVA`; `Clock.fixed` 23:59:59 / 00:00:01 CV; year rollover uses the new series | unit | `-Dtest=PagamentoFaturadoServiceTest#data*` | ❌ |
| EMIS-06 | NIF null / `012345678` / 8 digits; nome blank/2/151 chars; morada blank/101 → 422 with `campo`; nothing written | unit | `-Dtest=ValidacaoEmissaoTest` | ❌ |
| EMIS-07 | Same key sequential → 200, same ids; same key + different value → 409 `CHAVE_REUTILIZADA`; **two threads, same key, released together** → exactly 1 pagamento, 1 doc, CC credited once, the loser returns the same doc | IT | `-Dit.test=PagamentoFaturadoConcorrenciaIT#mesmaChave*` | ❌ |
| EMIS-08 | Narrow repositories (reflection, copy of `AuditLogImutabilidadeTest`); `@Immutable` present; no PUT/PATCH/DELETE route on `documentos-fiscais`; an attempted dirty change + flush leaves the DB row unchanged; editing Cliente/ConfiguracaoFiscal after emission doesn't change the snapshot | unit + IT | `-Dtest=DocumentoFiscalImutabilidadeTest`, `-Dit.test=DocumentoFiscalRepositoryIT` | ❌ |
| EMIS-09 | delete pagamento/cliente/processo/honorário with doc → 409 (Mockito); without doc → legacy behaviour; `repontarCliente` native UPDATE works on the `@Immutable` entity, touches only that tenant + client, leaves snapshot columns untouched; merge calls it before `delete(secondary)`; deleteCliente vs in-flight emission is serialized (holding-lock IT, `ConfiguracaoFiscalConcorrenciaIT` style) | unit + IT | `-Dtest=ResourceControllerDocumentoFiscalGuardasTest`, `-Dit.test=DocumentoFiscalRepositoryIT,GuardasDocumentoFiscalConcorrenciaIT` | ❌ |
| EMIS-10 | Every `MetodoPagamento` has a code; unknown/blank metodo on the active path → 422; the code is snapshotted | unit | `-Dtest=MetodoPagamentoTest` | ❌ |
| EMIS-11 | Listing: tenant A never sees B (same client NIF/ids), each filter null/non-null, pagination totals, detail of B's id from A → 404; `financeiro:view` gate via real `preAuthorize()` interceptor (copy `FaturacaoControllerAutorizacaoTest`) | IT + unit | `-Dit.test=DocumentoFiscalRepositoryIT`, `-Dtest=DocumentoFiscalControllerAutorizacaoTest` | ❌ |
| EMIS-12 | Payments created before activation show `documentoFiscal: null`; no endpoint emits for an existing pagamento (reflection: no handler takes `pagamentoId` to emit) | unit | `-Dtest=ResourceControllerListaPagamentosTest` | ❌ |
| CFG-03 | Disabled branch: same repository calls, swallowed `DataAccessException`, 201 entity, service `registar` never called; source hash of the extracted method; `Pagamento.java` token ban | unit | `-Dtest=FaturacaoDesligadaPagamentoInalteradoTest` (evolved) | ✅ (must be evolved) |
| Schema | Script 134 = Hibernate schema (incl. defaults), idempotent, no CHECK, second `update` boot emits no fiscal DDL | IT | `-Dit.test=MigracaoFiscal134IT` | ❌ |
| Frontend | Schema: metodo required when active, taxa 0<t≤100, ≤2 decimals; key helper falls back without `randomUUID`; preview/response mappers; no rate literals; gating tokens | vitest + gate | `cd web && pnpm test && pnpm verify:documentos-fiscais` | ❌ |
| UI end-to-end | Dialog flow, 422 inline, list/detail, "Simulação" mark, column in pagamentos | manual / HUMAN-UAT | live run (backend+web) | manual-only (no RTL/jsdom installed) |

### Sampling Rate
- **Per task commit:** the quick command for the touched classes, plus `pnpm test` for web tasks.
- **Per wave merge:** `mvn -Dmaven.compiler.release=21 verify` (with Docker) + `spotbugs:check`; web `pnpm test && npx tsc --noEmit && pnpm lint`.
- **Phase gate:** full suite green with ITs actually executed by Testcontainers (133 closed with scratch substitutes; do not repeat that).

### Wave 0 Gaps
- [ ] `CalculoFiscalTest` (vectors + property), `ValidacaoEmissaoTest`, `MetodoPagamentoTest`
- [ ] `MigracaoFiscal134IT` (copy 133 harness incl. `CapturaMetadataHibernate`)
- [ ] `DocumentoFiscalImutabilidadeTest`, `DocumentoFiscalRepositoryIT`
- [ ] `PagamentoFaturadoServiceTest`, `PagamentoFaturadoServiceIT`, `PagamentoFaturadoConcorrenciaIT`, `GuardasDocumentoFiscalConcorrenciaIT`
- [ ] Evolved `FaturacaoDesligadaPagamentoInalteradoTest` + new disabled-branch Mockito test
- [ ] `DocumentoFiscalControllerTest` / `…AutorizacaoTest`
- [ ] Update `ResourceControllerProveniencaPapelTest`/`ResourceControllerUploadDocumentoTest` constructors
- [ ] `web/src/schemas/financeiro.test.ts` (extended), `web/src/lib/idempotencia.test.ts`, `web/scripts/verify-documentos-fiscais.mjs` + `package.json` script

## Security Domain

### Applicable ASVS Categories (L1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (unchanged cookie JWT) | — |
| V3 Session Management | no | — |
| V4 Access Control | **yes** | `@PreAuthorize` `financeiro:view` (list/detail/estado), `financeiro:edit` (preview, POST pagamentos), `financeiro:manage` (delete pagamento/honorário, unchanged); tenant from `UserPrincipal` only; `findByIdAndTenantId` → 404 |
| V5 Input Validation | **yes** | DTO records (no `tenantId`/emitente fields → no mass assignment); BigDecimal scale/sign/magnitude; `MetodoPagamento` enum; UUID `chaveIdempotencia`; taxa range; page/size bounds; enum filters |
| V6 Cryptography | no (the client UUID is not a secret) | — |
| V7 Error Handling | yes | `RecusaFiscalException` Portuguese messages; catch-all returns `referencia` only (WR-06) |
| V8 Data Protection | yes | Controlled line description (no `Honorario.descricao`); listing DTO excludes nothing sensitive beyond what `financeiro:view` already sees; `emitido_por_nome` stays within tenant |
| V11 Business Logic | **yes** | Atomicity, idempotency, gapless numbering, date rule, delete guards, immutability |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR on `/documentos-fiscais/{id}` (UUID guessed/leaked) | Information disclosure | `findByIdAndTenantId` → 404; IT with 2 tenants |
| Cross-tenant emission via a foreign `honorarioId` (Integer, enumerable) | Elevation/Tampering | honorário → processo → `tenantId` check before any lock or number (`ResourceController:3045-3052` idiom) → 404 |
| Replay/double submit | Tampering/Repudiation | Idempotency under the config lock + UNIQUE |
| Key reuse with altered payload | Tampering | 409 `CHAVE_REUTILIZADA` |
| Snapshot tampering via the API | Tampering | No PUT/PATCH/DELETE routes; `@Immutable`; narrow repository; reflection test |
| Delete/merge race orphaning documents | Tampering | Row locks + guards in the same transaction |
| Lock-hold DoS on the series/config | DoS | `lock_timeout 5s` set first; 503 |
| CSRF on POST (CSRF disabled, no SameSite) | Spoofing | JSON `@RequestBody` requires `application/json` (preflight); no new GET with side effects (preview is POST without writes) |
| SQL injection via filters | Tampering | `@Param` binds only, no concatenation (repository precedent) |

## Sources

### Primary (HIGH confidence)
- Codebase: `ResourceController.java` (lines cited above), `Pagamento.java`, `Honorario.java`, `ContaCorrente.java`, `Cliente.java`, `Processo.java`, repositories, `NumeracaoService.java`, `ConfiguracaoFiscalRepository.java`, `SerieFiscalRepository.java`, `ParametroFiscalService.java`, `RecusaFiscalException.java`, `RecusaTransacional.java`, `AuditLogRepository.java`, `AuditLogImutabilidadeTest.java`, `FaturacaoDesligadaPagamentoInalteradoTest.java`, `ParametrosFiscaisSemConstantesTest.java`, `ConfiguracaoFiscalConcorrenciaIT.java`, `spotbugs-exclude.xml`, `application.yml`, web `financeiro/[id]/page.tsx`, `use-financeiro.ts`, `types/schemas financeiro.ts`, `data-table.tsx`, `lib/api.ts`, `verify-faturacao.mjs`
- `javap` on `hibernate-core-6.6.4.Final.jar` (`ImmutableEntityUpdateQueryHandlingMode` default WARNING) and `spring-data-jpa-3.4.1.jar` (`JpaSpecificationExecutor.delete`)
- `web/node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/dynamic-routes.md` (Next 16.2.6)
- `web/node_modules/typescript/lib/lib.dom.d.ts:8444-8447` (randomUUID secure context, MDN-sourced)
- Phase 133 SUMMARY 01-08 + REVIEW fix report (CR-01 lock order, WR-01, WR-02, WR-06)

### Secondary (MEDIUM confidence)
- UNCL4461 payment means codes 10/20/30/48 — web search results (peppol.eu, unece.org listings; pages themselves blocked by egress, so names come from the search summary)
- `.planning/research/ARCHITECTURE.md` §3-§4, §9; `PITFALLS.md` P-02..P-06, P-10, P-19..P-21, P-28; `SUMMARY.md` fiscal rules, C8, C11

### Tertiary (LOW confidence)
- Fiscal rules themselves (pending contabilista); codes 42/1/ZZZ.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — nothing new; versions read from pom/.m2/node_modules
- Architecture: HIGH for the transaction/locking design (it builds on Phase 133 ITs). MEDIUM for the cliente/processo lock extension, which is reasoned and must be proven by IT
- Pitfalls: HIGH — most are reproduced bugs from Phase 133 or verified API facts

**Research date:** 2026-10-04
**Valid until:** 2026-11-03 (stable stack; revisit codes in 136)
