---
phase: 136
slug: formato-efatura-e-adaptador-simulado
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-10-05
---

# Phase 136 — Validation Strategy

> Derived from 136-RESEARCH.md "## Validation Architecture".

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (surefire), Testcontainers `@DataJpaTest` (failsafe `*IT`), vitest (web), Node `verify:*.mjs` gates |
| **Quick run command** | `cd backend && mvn -q -Dmaven.compiler.release=21 test -Dtest='<touched classes>'` ; `cd web && pnpm test` |
| **Full suite command** | `cd backend && mvn -Dmaven.compiler.release=21 verify && mvn -Dmaven.compiler.release=21 spotbugs:check` ; `cd web && pnpm test && npx tsc --noEmit && pnpm lint && pnpm verify:faturacao && pnpm verify:documentos-fiscais` |
| **Estimated runtime** | ~400 s backend verify |

Docker must be running for ITs (`docker info`; else `rm -f /var/run/docker.pid; (dockerd > /tmp/claude-0/dockerd.log 2>&1 &); sleep 8`).

## Sampling Rate

- After every task commit: quick command for touched classes
- After every plan wave: full suite
- Before verify-work: full suite green with ITs actually executed
- Max feedback latency: 120 s

## Validation Architecture (from research)


### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ (spring-boot-starter-test 3.4.1); Testcontainers 1.20.4 PostgreSQL; vitest 4.1 (web) |
| Config file | `backend/src/test/resources/application.properties` (bogus datasource, forces `@ServiceConnection`); `web/vitest.config.ts` |
| Quick run command | `cd backend && mvn -Dmaven.compiler.release=21 test -Dtest='Iud*Test,DfeXml*Test,DfeValidador*Test,Estado*Test,Backoff*Test,EfaturaConfig*Test'` |
| Full suite command | `cd backend && mvn -Dmaven.compiler.release=21 verify && mvn -Dmaven.compiler.release=21 spotbugs:check`; `cd web && pnpm test && pnpm lint && pnpm verify:documentos-fiscais && pnpm build` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DFE-01 | Vendored XSD hashes unchanged | unit | `mvn test -Dtest=XsdEfaturaIntegridadeTest` | ❌ Wave 0 |
| DFE-01 | Official samples `2 InvoiceReceipt.xml`/`5 CreditNote.xml` validate via classpath resolver | unit | `-Dtest=DfeValidadorTest` | ❌ |
| DFE-01 | Builder FR (NORMAL+IR, NORMAL sem IR, ISENTO→NA+motivo) and NC (partial, total) → valid XML; element-level asserts (Totals, Payments, References=FR IUD, IssueReasonCode, Note ≥10) | unit | `-Dtest=DfeXmlBuilderTest` | ❌ |
| DFE-01 | Negatives rejected: PaymentMeansCode not in D19B, RepositoryCode ≠1..3, Note <10, double space, >5 decimals, firma >150, XXE DOCTYPE | unit | `-Dtest=DfeValidadorTest` | ❌ |
| DFE-01 | Schema loads from a packaged jar (`jar:nested:`) | smoke (CI) | `mvn -DskipTests package && java -cp … ` or a test that loads via `URLClassLoader` over the built jar | ❌ (manual-ish; optional) |
| DFE-02 | IUD length 45, matches `stDfeId` + Kowts regex, repo digit 3, tipo 02/05, Luhn = official vector (DV 4), SecureRandom injected (deterministic test) | unit | `-Dtest=IudGeradorTest` | ❌ |
| DFE-02 | IUD unique + XML row immutable (repository has no update/delete; pinned) | unit + IT | `-Dtest=DocumentoFiscalXmlImutabilidadeTest`; `MigracaoFiscal136IT` | ❌ |
| DFE-03 | Unknown/blank/REAL mode fails context; SIMULADO yields gateway; invalid Transmission defaults fail | unit (`ApplicationContextRunner`) | `-Dtest=EfaturaConfigTest` | ❌ |
| DFE-03 | Gateway refuses `ambiente ≠ SIMULADO` | unit | `-Dtest=SimuladoEfaturaGatewayTest` | ❌ |
| DFE-04 | Mapping matrix (resultado × tentativas) incl. 8th failure → ERRO; backoff table | unit | `-Dtest=EstadoComunicacaoMapperTest,BackoffComunicacaoTest` | ❌ |
| DFE-04 | Claim: NULL `proxima_tentativa_em` due; SKIP LOCKED (2 concurrent claims disjoint); expired lease reclaimed; lost-lease result discarded (versao); suspended tenant processed; FR before NC; emission tx unaffected (job never runs inside it) | IT (Testcontainers) | `mvn verify -Dit.test=FiscalOutboxJobIT` | ❌ |
| DFE-04 | Job isolation: one item throwing Throwable doesn't stop others | unit | `-Dtest=FiscalOutboxJobTest` | ❌ |
| DFE-04/06 | Detail/list DTO carry estado/IUD/tentativas; web badge labels, neutral (no accent) variant, polling while PENDENTE | unit (backend DTO) + vitest | `-Dtest=DocumentoFiscalDetalheResponseTest`; `pnpm test -- comunicacao` | ❌ |
| DFE-05 | Reprocessar: 200 from ERRO/REJEITADO resets tentativas=0; 409 from PENDENTE/ACEITE_SIMULADO; 404 foreign/malformed; `@PreAuthorize("hasAuthority('financeiro:edit')")` exact; 7 handlers; audit event | unit + IT | `-Dtest=DocumentoFiscalControllerTest,DocumentoFiscalControllerAutorizacaoTest`; `ReprocessarComunicacaoIT` | ⚠ existing files to extend |
| DFE-05 | Web gate exact `financeiro:edit`; button only for ERRO/REJEITADO; mutation not in `page.tsx` | vitest + script | `pnpm test`; `pnpm verify:documentos-fiscais` (extended) | ⚠ extend |
| DFE-06 | DB CHECK rejects AUTORIZADO on SIMULADO (`23514`) | IT | `MigracaoFiscal136IT` | ❌ |
| DFE-06 | Source gate: no "Autorizado"/"Comunicado à DNRE"/"Validado pela DNRE" copy in web fiscal pages; "Ambiente de teste — sem validade fiscal" + "Modo simulado" present | script | `pnpm verify:documentos-fiscais` | ⚠ extend |
| DFE-07 | ERRO → one notification per active user with effective `financeiro:manage`, none for others/other tenants; second run idempotent; notify failure doesn't revert ERRO | unit + IT | `-Dtest=NotificacaoComunicacaoFiscalTest`; `FiscalOutboxJobIT` | ❌ |
| DFE-07 | New category in backend enum (non-silenceable) and web maps | unit + vitest/tsc | `-Dtest=CategoriaNotificacaoTest`; `pnpm exec tsc --noEmit` | ⚠ |
| all | Scripts 134+135+136 == Hibernate | IT | `MigracaoFiscal134IT,135IT,136IT` | ⚠ update counts |

### Sampling Rate
- **Per task commit:** the quick run command (unit tests touched) + `pnpm test` for web tasks.
- **Per wave merge:** `mvn -Dmaven.compiler.release=21 verify` (Docker up) + `spotbugs:check` + web `pnpm test && pnpm lint`.
- **Phase gate:** full suite green + `pnpm build` + `verify:documentos-fiscais` + live UAT (emit FR and NC → badge moves PENDENTE→Aceite (simulação) within ~30 s; inject a fault → ERRO + notification; reprocess).

### Wave 0 Gaps
- [ ] Vendored XSD + `XsdEfaturaIntegridadeTest` + test fixtures (`src/test/resources/efatura/2024-05-27/`).
- [ ] pom: JAXB deps + plugin; first `generate-sources` green; SpotBugs package exclusions.
- [ ] `FixturaEmissaoFiscal` extension (or a new fixture) to insert FR/NC + PENDENTE rows for `FiscalOutboxJobIT`.
- [ ] `MigracaoFiscal136IT` skeleton.
- [ ] Start `dockerd` in the environment.


## Validation Sign-Off

- [ ] All tasks have automated verify or Wave 0 dependencies
- [ ] Wave 0 covers all MISSING references
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
