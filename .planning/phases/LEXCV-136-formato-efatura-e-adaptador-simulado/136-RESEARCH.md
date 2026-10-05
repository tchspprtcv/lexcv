# Phase 136: Formato eFatura e Adaptador Simulado - Research

**Researched:** 2026-10-05
**Domain:** eFatura CV DFE XML (XSD package 2024-05-27) via JAXB 4 + hardened `javax.xml.validation`; transactional outbox poller on PostgreSQL (`FOR UPDATE SKIP LOCKED`, lease, backoff) in Spring Boot 3.4.1 / Hibernate 6.6.4; in-app notification; Next.js 16 / TanStack Query status UI
**Confidence:** HIGH for the XSD content, the JAXB/validator build and the repo integration (all re-executed in this session on JDK 21, including `java -jar` of a Boot 3.4.1 fat jar). MEDIUM for "the 2024-05-27 package is still the current one" (primary source still blocked). LOW for the fiscal semantics the XSD cannot settle (IssueReasonCode mapping, PaymentAmount gross/net, Contacts, UnitCode). Those go to the end-of-phase gate table.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Formato e IUD
- Pacote XSD oficial 2024-05-27 (cópias públicas `Kowts/efatura-cv-php` e `kriolos/kriolos-efatura`, idênticas) vendorizado em `backend/src/main/resources/xsd/efatura/`; modelo JAXB gerado com `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.16 (sem `generatePackage` global — colisão XAdES 1.3.2/1.4.1; usar `.xjb`); `jakarta.xml.bind-api`/`jaxb-runtime` geridos pelo BOM em compile (pesquisa STACK.md §3, §11)
- Validação contra o XSD com `javax.xml.validation` endurecido contra XXE (acesso externo vazio) e `LSResourceResolver` de classpath (carregamento `file:` falha no `java -jar`)
- XML gerado em segundo plano a partir do snapshot imutável do documento; guardado numa tabela satélite nova (XML em texto + SHA-256 + IUD + timestamps), NÃO em `t_documento_fiscal` (imutável) nem no MinIO (o MinIO fica para o PDF na 137)
- IUD gerado no processamento em segundo plano (não na transação de emissão) e guardado na satélite; estrutura oficial 45 chars = `CV` + repositório(1) + `AAMMDD` + NIF(9) + LED(5) + tipo(2) + número(9) + aleatório(10, `SecureRandom`) + DV Luhn(1); FR = tipo 02, NC = tipo 05
- `RepositoryCode = 3` (Teste) sempre em SIMULADO; LED sintético reservado declarado como constante, só válido em SIMULADO (o `led_codigo` da série continua nulo)
- `Transmission` (`TransmitterTaxId`, `Software{Code,Name,Version}`) por propriedades de configuração com valores de teste válidos só em SIMULADO
- NC referencia o IUD da FR original em `References` e leva `IssueReasonCode` mapeado de `MotivoNotaCredito`
- Códigos de meio de pagamento (`MetodoPagamento` → PaymentMeansCode) confirmados contra a lista do pacote XSD nesta fase

#### Adaptador
- `EFATURA_MODE` ao nível do deployment, default `SIMULADO`; qualquer outro valor (incluindo `REAL`) aborta o arranque com mensagem explícita (não há implementação real neste build); gravado em cada linha de comunicação (`ambiente`)
- `EfaturaGateway` (porta) com resultado selado (`AceiteSimulado`, `Rejeitado`, `ErroTransitorio`); `SimuladoEfaturaGateway` valida o XML contra o XSD → `ACEITE_SIMULADO` se válido, `REJEITADO` se inválido; falhas injetáveis em teste
- Estados: `PENDENTE` → `ACEITE_SIMULADO` | `REJEITADO` | `ERRO` (tentativas esgotadas); `AUTORIZADO` só com `ambiente = PRODUCAO`, garantido por CHECK na BD e por uma única função de mapeamento (resultado, ambiente) → estado; sem transição simulado→real
- `FiscalOutboxJob` `@Scheduled` (≈30 s) com `SELECT … FOR UPDATE SKIP LOCKED`, lease, backoff exponencial, máximo 8 tentativas → `ERRO`; padrão `AlertasDiariosJob` (sem SecurityContext, tenantId explícito, `catch Throwable` por item); NÃO salta tenants suspensos; aumentar `spring.task.scheduling.pool.size` para ≥ 3 (hoje 1 thread — bloquearia o job das 06:00)
- A comunicação corre sempre fora da transação de emissão; nenhum I/O dentro dela

#### UI e notificação
- Badge de estado neutro na lista e no detalhe (Pendente, Aceite (simulação), Rejeitado, Erro); estados nunca usam a cor de acento
- Botão "Reprocessar comunicação" no detalhe para `ERRO`/`REJEITADO`, gated `financeiro:edit` (backend + frontend exatos); repõe a linha em PENDENTE com tentativas a zero
- IUD mostrado em texto no detalhe com a marca "Ambiente de teste — sem validade fiscal"; banner permanente "Modo simulado" enquanto `EFATURA_MODE=SIMULADO`
- Notificação in-app quando a comunicação passa a `ERRO` definitivo, para os utilizadores do escritório com `financeiro:manage`; nova categoria (ex. `COMUNICACAO_FISCAL_FALHOU`) no sistema `Notificacao` existente (dedup por documento)

#### Portão das fontes primárias
- A pesquisa da fase tenta outra vez aceder a `efatura.cv` (docs/xsd, manual); se continuar bloqueado, compara as duas cópias públicas e regista as diferenças/suposições; no fim da fase o orquestrador pergunta ao utilizador se fecha a fase com o portão pendente

### Claude's Discretion
- Nomes de classes/tabelas, intervalo exato do job, formato do backoff, divisão em planos

### Deferred Ideas (OUT OF SCOPE)
- Assinatura XAdES, OAuth2/credenciais, certificados ICP-CV, modo real por emitente, contingência — marco de ligação real (EFAT-01..06)
</user_constraints>

> **Correction to a locked-decision premise (needs the user's attention):** CONTEXT says the two public copies are "idênticas". **They are not.** Diffed file by file in this session: `kriolos/kriolos-efatura` holds the **2021-12-19** package, not 2024-05-27. Only `Kowts/efatura-cv-php` is 2024-05-27. Every difference matches an entry in the 2024 package's own "Read Me.txt" changelog (2022-01-27 … 2024-05-27). The earlier research compared only `EnvelopedSignature.xsd`, which is whitespace-identical in both. The decision still stands: vendor the 2024-05-27 package from Kowts. But that package now has **one** public source, plus a consistency check against an older independent copy. See §Primary-Source Gate.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DFE-01 | Cada documento emitido gera o XML no formato eFatura (DFE), validado contra o esquema oficial antes de ser dado como pronto | §Primary-Source Gate (files to vendor + SHA-256 manifest); §XSD → snapshot mapping (FR/NC element tree, every field mapped to file:line); Pattern 2 (JAXB build) and Pattern 3 (hardened classpath validator), both executed: generated FR and NC validate with JDK Xerces **and** libxml2 |
| DFE-02 | IUD de 45 caracteres com a estrutura oficial, marcado como ambiente de teste | Pattern 4 (IUD pure function, Luhn parity verified against the official sample and the Kowts SDK); `RepositoryCode=3` + IUD digit 3; synthetic LED 99999 |
| DFE-03 | Adaptador de interface única; modo por configuração do deployment; modo desconhecido impede o arranque | Pattern 6 (`EfaturaConfig` fail-fast bean + `ApplicationContextRunner` test); compose `${EFATURA_MODE:-SIMULADO}` pitfall |
| DFE-04 | Estado de comunicação visível, atualizado em segundo plano, sem atrasar o pagamento; retentativa automática | Pattern 5 (outbox claim/lease/backoff on `t_comunicacao_fiscal`, separate tx bean, pool size 3, verified property); UI polling while PENDENTE |
| DFE-05 | `financeiro:edit` reprocessa um documento em erro | Pattern 7 (conditional UPDATE, 409 code, audit event, exact gate on both layers, separate client component because `verify:documentos-fiscais` forbids mutations in the detail page) |
| DFE-06 | Simulado nunca aparece como autorizado | Defence layers: sealed result + exhaustive switch over `AmbienteFiscal`; DB `CHECK (estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO')` via `@Check` + script; `RepositoryCode=3`; `SIM-` series; labels/banner; source-gate test |
| DFE-07 | Falha persistente gera notificação in-app aos responsáveis | Pattern 8 (`COMUNICACAO_FISCAL_FALHOU`, recipients resolved through `ResolucaoPapeisService.resolverPermissoesEfectivas`, `NotificacaoService.criar` ON CONFLICT dedup; dedup-after-reprocess caveat) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Domain language is Portuguese: new classes/tables/DTOs/routes use Portuguese names (`comunicacao`, `reprocessar`, `documento-fiscal`).
- Multi-tenancy: every read and write is scoped by `tenant_id`. The job has no SecurityContext, so it takes `tenantId` from the outbox row and passes it to every repository call (`findByTenantIdAndId…`). The endpoint uses the principal's `getTenantId()`.
- RBAC: `@PreAuthorize("hasAuthority('financeiro:edit')")` on reprocessar, with the **exact** same authority in the UI. Phase 134 set this rule after WR-04, because the frontend fallback chain (`hasScopedPermission`) would show controls that the backend refuses.
- Schema: `ddl-auto: update` everywhere, and some installs run `validate` via `SPRING_JPA_HIBERNATE_DDL_AUTO`. No Flyway or Liquibase. Each new column, table or constraint needs an idempotent script in `backend/migrations/` **and** a README row in the same phase.
- Backend commands: `mvn test` / `mvn verify` (failsafe runs `*IT`) / `mvn spotbugs:check`. Locally, use `-Dmaven.compiler.release=21`, because the pom pins `java.version` 23.
- Frontend: pnpm only; `pnpm test` (vitest), `pnpm lint`, `pnpm build`. Read `web/node_modules/next/dist/docs/` before touching framework APIs (Next 16).
- `application.yml`: by convention every value is a required env var. Defaults exist as precedents (`MINIO_PUBLIC_ENDPOINT`, `MINIO_PRESIGNED_EXPIRY`), and SUMMARY C4 documents `EFATURA_MODE` default `SIMULADO` as an accepted deviation.
- Legacy `web/src/server/` and `_api-backup/` must not be used.
- ASVS L1 security enforcement, `security_block_on: high`. Phase artifacts are committed.

## Summary

The format side is now settled to the extent public material allows. I fetched the full 2024-05-27 package (22 XSD + changelog + fields map + samples) from Kowts via `raw.githubusercontent.com`. I generated the JAXB model with the exact plugin configuration from STACK.md: 137 classes in 7 packages, `com.lexcv.fiscal.efatura.xsd` = 52. I then built a Fatura-Recibo and a Nota de Crédito with the field mapping proposed below, from values shaped like our snapshot: `SIM-FR-2026` series, `15.0000` IVA rate, IR withholding, `Payments` with code `30`, NC `References` to the FR IUD, `IssueReasonCode` and a ≥10-char `Note`. Both validate with a hardened validator, through a classpath `LSResourceResolver`, from exploded classes **and** under `java -jar` of a Spring Boot 3.4.1 fat jar. libxml2 (`xmllint`) independently agrees. Negative cases are rejected as expected: unknown PaymentMeansCode, RepositoryCode 4, Note shorter than 10 characters, double spaces in text, too many decimals, external entity. The SpotBugs package filters remove all 387 findings in the generated code, and `PATH_TRAVERSAL_IN` fires on a resolver that uses `Paths.get`, so the resolver must use a whitelist.

`efatura.cv` and all its subdomains are still blocked (`CONNECT tunnel failed, response 403`, WebFetch `EGRESS_BLOCKED`). The "two identical copies" premise turned out to be false: kriolos is the 2021-12-19 package. Two things still support the Kowts copy. The older copy plus the changelog entries reconstruct the newer one exactly, and the official sample IUD passes the Luhn check. What remains unknowable without the primary source is the platform-level rules: IssueReasonCode semantics, whether `PaymentAmount` is gross or net, whether `Contacts` is required, UnitCode, and rounding tolerance. These are tabulated for the user's end-of-phase gate decision.

The runtime side is a classic transactional outbox. The PENDENTE row that 134/135 already insert is the outbox entry. A `@Scheduled(fixedDelay≈30s)` job claims a batch with `FOR UPDATE SKIP LOCKED` in a short transaction and stamps a lease. It builds the IUD and XML and validates them outside any transaction. It persists the XML into an insert-only satellite, calls the gateway, and records the result with a conditional `UPDATE … WHERE versao = :claimed`. The pool must go to 3 threads (Boot default is 1, verified in Boot 3.4.1 metadata). Several existing tests pin behaviour this phase must change on purpose: the 6-handler count on `DocumentoFiscalController`, the column counts in `MigracaoFiscal134IT`/`135IT`, and the `verify:documentos-fiscais` "no mutation in detail page" rule.

**Primary recommendation:** Vendor the Kowts 2024-05-27 package pinned by the SHA-256 manifest below. Generate JAXB with `.xjb`. Build one pure `DfeXmlBuilder` (snapshot → `Dfe`) and one pure `IudGerador`. Validate with a whitelist classpath resolver plus `disallow-doctype-decl`. Run the outbox on `t_comunicacao_fiscal`, with tx boundaries in a separate bean and every state change in native conditional UPDATEs. Put every unverifiable fiscal choice behind a single constant table that is listed in the gate.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| XSD package, JAXB model | Backend build (Maven generate-sources) | — | Generated at build time from vendored, checksummed XSD; never edited by hand |
| DFE XML construction (snapshot → XML) | API / Backend (pure function) | — | Must read only the immutable snapshot (`t_documento_fiscal`/`_linha`), so it can be re-run deterministically |
| IUD generation | API / Backend (pure function + `SecureRandom`) | Database (unique index) | Built by the emitter per spec; uniqueness enforced in DB |
| XSD validation | API / Backend | — | `javax.xml.validation`, schema loaded once at startup (fail-fast) |
| Outbox claim / lease / backoff / result | Database (row locks, conditional UPDATE) | API / Backend (`@Scheduled` job) | Correctness under >1 instance comes from SQL, not from JVM locks |
| Mode selection (`EFATURA_MODE`) | API / Backend (startup config) | Deployment (compose/env) | Deployment-level lever; never a DB row an office admin can flip |
| AUTORIZADO⇒PRODUCAO invariant | Database (CHECK) | API / Backend (exhaustive mapping) | Defense in depth (P-08) |
| Reprocessar | API / Backend (`financeiro:edit`) | Browser (exact gate, separate component) | Both layers must agree (CLAUDE.md) |
| Persistent-failure notification | API / Backend (`NotificacaoService.criar`) | Browser (category label/badge) | Single write point for notifications (NOTF-14) |
| Status badge / IUD display / "Modo simulado" banner | Browser | API (fields on existing DTOs) | Frontend shows only backend-computed values ("frontend burro") |

## Primary-Source Gate (Task 1)

### What happened when accessing primary sources (2026-10-05)

| URL | Tool | Result |
|-----|------|--------|
| `https://efatura.cv/docs/xsd` | curl via agent proxy | `curl: (56) CONNECT tunnel failed, response 403` |
| `https://efatura.cv/docs/xsd` | WebFetch | `EGRESS_BLOCKED` ("Access to efatura.cv is blocked by the network egress proxy") |
| `https://efatura.cv/docs/manual/`, `https://efatura.cv/` | curl | 403 CONNECT |
| `https://services.efatura.cv/api-list/` | curl | 403 CONNECT |
| `https://dev.efatura.cv/docs/next/manual/servicos-eletronicos/` | curl | 403 CONNECT |
| `tst.`, `middleware.`, `iam.`, `pe.efatura.cv` | curl | 403 CONNECT (proxy log: `connect_rejected … organization policy`) |
| CIVA texts (`mf.gov.cv/…/IVA.pdf`, `lobocarmona.com`, `macedovitorino.com`) | curl/WebFetch | blocked |
| `api.github.com`, `codeload.github.com`, `github.com` HTML | curl/gh | 403 (session has no GitHub access for these repos) |
| `raw.githubusercontent.com` | curl/python | **200**, which made it possible to fetch both copies file by file by following `schemaLocation` |

No bypass was attempted. The gate stays **pending**. The orchestrator must ask the user at the end of the phase, per CONTEXT.

### The two public copies are different versions

| File (relative to package root) | Kowts vs kriolos | Explained by Read Me entry |
|---|---|---|
| `EnvelopedSignature.xsd`, `InternallyDetachedSignature.xsd`, `CV_EFatura_{Invoice,InvoiceReceipt,SalesReceipt,Receipt,DebitNote,ReturnNote,RegistrationNote,Transport,MainElements}_v1.0.xsd`, `ETSI_XAdESv132.xsd`, `W3C_XMLDSig.xsd`, `ISO_ISO3AlphaCurrencyCode_2012-08-31.xsd` | whitespace/CRLF only | — |
| `CV_EFatura_TaxExemptionReason_v1.0.xsd`, `UNECE_PaymentMeansCode_D19B.xsd` | byte-identical | — |
| `CV_EFatura_Types_v1.0.xsd` | Serie maxLength 20 vs 10; `stYear` minExclusive 2020; `stTaxIdValue` minLength 5 vs 1; `IR` uncommented (kriolos lists IR **and** has TEU commented differently); `DRP` added; `stReferencePeriod` regex fixed for month 10; `stSelfBillingAuthorizationCode`, `stUUID`, `ctDatePeriod` added | 2022-01-27, 2022-01-29, 2022-02-19, 2022-03-10, 2023-12-15, 2024-05-27 |
| `CV_EFatura_Elements_v1.0.xsd` | `Year`, `WithholdingTaxTotalAmount`, `TaxTotal`, `SelfBilling` added; `Tax` maxOccurs 2 vs unbounded; `InnerDocumentNumber` `stCode` vs `stDigits` | 2022-01-28, 2022-01-29, 2022-02-19, 2022-02-23, 2023-12-15 |
| `CV_EFatura_MainTypes_v1.0.xsd` | `Year` in Event | 2022-01-29 |
| `CV_EFatura_CreditNote_v1.0.xsd` | `RappelPeriod` | 2024-05-27 |
| `ISO_ISOTwo-letterCountryCode_SecondEdition2006.xsd` | `ID` vs `Id` (Indonesia) | 2022-03-10 |
| `ETSI_XAdESv141.xsd` | kriolos commented out `ArchiveTimeStamp` ("KRIOLOS.CHANGE DUPLICADO") | local kriolos edit, not DNRE |
| `Read Me.txt` | kriolos ends at **2021-12-19**; Kowts continues to **2024-05-27** | — |
| `XML Fields Map.txt` | differences match the above (SelfBilling, RappelPeriod, WithholdingTaxTotalAmount, InnerDocumentNumber, DatePeriod) | — |

[VERIFIED: file-by-file diff of both copies fetched in this session via raw.githubusercontent.com]

**Interpretation:** the older independent copy, with the documented changelog applied, gives exactly the Kowts copy. That is good evidence that the Kowts copy is a faithful 2024-05-27 package. It is **not** evidence that 2024-05-27 is still current. The 2024-05-27 package therefore has one public source, plus this consistency check.

### Licences (can we vendor?)

| Source | Licence file | What it says about the XSD |
|---|---|---|
| `Kowts/efatura-cv-php` | `LICENSE`: MIT, "Copyright (c) 2026 Kowts" | `NOTICE`: "Este projecto inclui artefactos XSD oficiais do sistema e-Fatura de Cabo Verde, publicados em efatura.cv, na versão técnica datada de 27 de Maio de 2024. Os ficheiros XSD são redistribuídos apenas para validação local … A sua inclusão não implica aprovação … por parte da DNRE." |
| `kriolos/kriolos-efatura` | `LICENSE`: Apache-2.0 (pom `<licenses>` Apache-2.0) | no NOTICE about the XSD |

[VERIFIED: LICENSE/NOTICE fetched from raw.githubusercontent.com]

**Conclusion:** neither licence grants rights over the DNRE files. Kowts' MIT covers Kowts' code, and the NOTICE explicitly treats the XSD as third-party official artefacts redistributed "only for local validation". The XSD's own copyright and terms are DNRE's and unverified (A1). Our use is the same as Kowts' use: local validation and code generation, in a private repository. That is a low-risk, defensible position, but it is **ASSUMED**. Record it in the vendored folder as a `README.md` (provenance, date, SHA-256 manifest, "não oficial / não implica aprovação da DNRE"). Do **not** vendor anything from kriolos: it is the old version and was edited locally.

### Exact files to vendor

`backend/src/main/resources/xsd/efatura/` (from `https://raw.githubusercontent.com/Kowts/efatura-cv-php/main/resources/xsd/efatura/2024-05-27/`, fetched 2026-10-05). These are the 22 XSD files. STACK.md said "20 ficheiros"; it is 2 entry points + **20** in `common/`.

```
3366a38ee5632818da0ac830b608e8449aea27e744ea28bc200a68129ffc2fa0  EnvelopedSignature.xsd
b2c669415dede8cf9e6a6c67578db1952c40d064a48d5743e301ec394df40aa6  InternallyDetachedSignature.xsd
6eab59302f2dd0cad0cbba0e5b640a79a8d99e5eed511ed390095f27f05cfe27  common/CV_EFatura_CreditNote_v1.0.xsd
aea1fcea8c214204089bfcfec2e21c23b51bc7c59334662f8213b748ef413dd1  common/CV_EFatura_DebitNote_v1.0.xsd
8ac4cfa0d2e8125335f3fdbb9fd23bf4b1a6897a7aba81f4ead5311d9c61667f  common/CV_EFatura_Elements_v1.0.xsd
d5bdf74d5baad6a7794f7923f5e2f73f70d2d1ce05f1c805f5b51fc63a05c523  common/CV_EFatura_InvoiceReceipt_v1.0.xsd
c2be64f05f2dcc78f5d270ab1620217428935a16958dbc5caa037f1173aa8c01  common/CV_EFatura_Invoice_v1.0.xsd
8c0ef2a8d3fdefa78b3e55234fa991a7398d434df10f3e0aae3d1be2bb63f15d  common/CV_EFatura_MainElements_v1.0.xsd
e1ac68ae79d36c13a0354f7b405e64841060a7eeeec5da4478b04569078d0fb0  common/CV_EFatura_MainTypes_v1.0.xsd
6ce312ba730ec76ade4c8c2c5a8d84b54409b69f0f3fa6e5628a3853a85a7afd  common/CV_EFatura_Receipt_v1.0.xsd
7f8435b918d5577017302f016dd1e73d1af25bff8642f84b33f8bc26e87f1ce1  common/CV_EFatura_RegistrationNote_v1.0.xsd
9b0886b5770745f66ca4e4da99ce2eac1e6beb84fdbbbdd69b5da766f01ba48b  common/CV_EFatura_ReturnNote_v1.0.xsd
aa2231dd32ad2aec44627eb48ac35e7fbfc17ed8f32057a6ddd06ab6bb9787ec  common/CV_EFatura_SalesReceipt_v1.0.xsd
188c74e95e166159d2ec8e8cd74f568dcaed6f5bb64ba3639d78448aefd6cadf  common/CV_EFatura_TaxExemptionReason_v1.0.xsd
9154f0286f5c79851c0fe7a9ff2d52302aa3218a345ba63e35fc51ff0b7be57a  common/CV_EFatura_Transport_v1.0.xsd
c0f34a7115f48d395566a05b7331194def4940df3e4374cb029a611d7975f487  common/CV_EFatura_Types_v1.0.xsd
dcec1fae271c8b1d7a92acc79cf3504e6fab0aa235d8cb2f4d412e889809660b  common/ETSI_XAdESv132.xsd
b202675d8ef478ea74ed1f4bde9b2d661236d9dfb7546d9c7fd7c7894c8d0630  common/ETSI_XAdESv141.xsd
1f19cb4196d9a4b533de3f1df7cd317cdd6af38fdfe98297b8a929a5018197d2  common/ISO_ISO3AlphaCurrencyCode_2012-08-31.xsd
e0d1162144f1126d292ec5adcc3f3fbb83398e527a00e8f3bcdfd8dbb1e0d462  common/ISO_ISOTwo-letterCountryCode_SecondEdition2006.xsd
56b1100a7f14d1d68abd73108d12947c62a0b44ad44eefa9ba1a33e454c786ae  common/UNECE_PaymentMeansCode_D19B.xsd
b4716e1d9ad185b43bdc3464aa584fa5a24b33617361d73240d79858a550474c  common/W3C_XMLDSig.xsd
```

Documentation and fixtures go to `backend/src/test/resources/efatura/2024-05-27/`. They are not runtime code, and keeping them out of the classpath under `main` avoids shipping them:

```
6f31ac6a67cc77fb2ec90d6079ff7d9ed6ab3bf35bdb39d8154824f8e1be4492  Read Me.txt
95a1aede7d29b25afc04d36a98241b1e1057a2dc150ea573ac0e0914116b48ba  XML Fields Map.txt
667e592d33e66f3bdcfd983ea94712705327299476e32ec6b0b05337fc63832b  2 InvoiceReceipt.xml
3b6a080fdb8af5fe45c27248c587364a1a1aa1742b627c703501e38c8ea00b13  5 CreditNote.xml
```

Recommended: a `XsdEfaturaIntegridadeTest` that recomputes these SHA-256 values from the classpath. Any edit to a vendored file then fails CI, and when the primary source becomes reachable, the gate check becomes "compare these 22 hashes".

### Remaining assumptions for the end-of-phase gate (user decision)

| # | Topic | What the XSD/package fixes | What remains assumed | Proposed v3.0 handling | If wrong |
|---|---|---|---|---|---|
| G1 | Package currency | 2024-05-27 content (HIGH) | Still the version in force, and the manual is v11 | Vendor with manifest; `VERSAO_FORMATO = "2024-05-27"` stored per XML row | Rebuild XML from snapshot (cheap: XML is derived, snapshot is the source) |
| G2 | IUD layout | Regex `CV(\d)(\d{2})(0[1-9]\|1[012])(0[1-9]\|[12]\d\|3[01])([1-9]\d{8})\d{27}`, length 45 (HIGH) | Split of the trailing 27 digits into LED(5) + tipo(2) + número(9) + aleatório(10) + DV(1). The XSD does not constrain it; the sample IUD's "tipo" digits are `23`, so the sample values are fictitious | Layout from manual v10 excerpts + Kowts `Iud.php` (MEDIUM) | IUD rejected by the real platform; no effect in SIMULADO |
| G3 | Luhn | Sample IUD DV = 4 = Luhn over the 42 digits (verified again in this session) | No official test vectors (10% chance of coincidence) | Kowts parity: double every second digit starting from the rightmost payload digit | Same as G2 |
| G4 | LED in SIMULADO | `stLedCode` 1–99999 | Whether any value is reserved for tests | Constant `LED_SIMULADO = 99999`, refused for any `ambiente ≠ SIMULADO` | None in SIMULADO |
| G5 | PaymentMeansCode | List = UN/ECE D19B, 83 codes; **10, 20, 30, 48, ZZZ all present** (HIGH) | Semantic fit (e.g. `CARTAO`→48 "Bank card" vs 54/55), and whether DNRE restricts the list | Keep the Phase 134 mapping (already snapshotted in `meio_pagamento_codigo`); now verified as valid codes | Only semantics; would need a new snapshot value for future documents |
| G6 | IssueReasonCode (NC) | NCE accepts `2,3,6,7,8,9` (Art.º 65 n.º x CIVA), `IN` (transition), `DRP` | Which paragraph applies to each `MotivoNotaCredito`. A search excerpt says Art.º 65 n.º 2 covers annulment or reduction of the taxable value (invalidity, resolution, discounts) (LOW). Manual v7 used other numbers (1 for n.º 2, …), now superseded | Map all four motivos to `"2"` in one constant table; contabilista to confirm | NC rejected or misclassified in real mode |
| G7 | References on NC | XSD `References` optional; Fields Map: required on NCE (`{1}?`, "Opcional na FTE e FRE"); 2021-11-04: optional only with `IN` | — | Always send `Reference/FiscalDocument` = FR IUD (no `IsOldDocument`) | — |
| G8 | Line Tax on NC | Fields Map: Line `Tax` "Opcional no DTE, NCE, DVE" | Whether sending the FR's IVA/IR on the NC line is expected | Send the same `Tax` elements as the FR snapshot rates (validated) | Possibly rejected; can drop later |
| G9 | Transmission block | Structure fixed (IssueMode, TransmitterTaxId, Software{Code `[A-Z0-9]{1,10}`, Name 3–150, Version}) | Real `Software.Code` (homologation) and transmitter NIF | Config props with SIMULADO-only test defaults; `IssueMode=1` (Online) | Needs real values in the real-connection milestone |
| G10 | Totals tolerance | Read Me 2022-02-23: `TaxTotal` per Tax validated within `floor(X)…ceil(X)`, X = NetTotal × % / 100; amounts ≤5 decimals | Rounding tolerance for document totals | Do not send `TaxTotal` (optional); totals from snapshot (2 decimals, HALF_UP already used) | Rejection on rounding |
| G11 | `PaymentAmount` | `stDecimal5MinExc0` | Gross (`total_documento`) or net of withholding (`valor_liquido`) | `valor_liquido`, so that it equals `PayableAmount` | Semantics only |
| G12 | `Party.Contacts` | XSD optional; Fields Map `{1}?` "Opcional no TVE" (i.e. required elsewhere) | Whether the platform rejects its absence | Omit (no snapshot columns; no fabricated data); listed as gap | Rejection in real mode; needs snapshot columns |
| G13 | `Quantity@UnitCode` | Required, `[aA-zZ0-9]{1,10}`, internal units allowed (`IsStandardUnitCode` default false) | Expected code for services | `"EA"` (as in the official samples) | Cosmetic |
| G14 | Licence/redistribution of the DNRE XSD | — | DNRE terms | Vendor for local validation, provenance README | Remove files and fetch at build time from the official source |
| G15 | `IsSpecimen` | Optional, only `"true"`; Read Me: "para permitir testes no repositório principal" | — | Do **not** send in SIMULADO (`RepositoryCode=3` already marks test). Optional extra layer if the user wants it | — |

## XSD → Snapshot Mapping (Task 2)

Element order is fixed by the XSD `xs:sequence`. JAXB enforces it, which is why hand-rolled XML is banned.

### Root `Dfe` (both types)

| XSD element/attr | Rule | Source in our model |
|---|---|---|
| `@Version` | `stDocVersion` | constant `"1.0"` |
| `@Id` | `stDfeId` (45, `xs:ID`) | generated IUD (new satellite) |
| `@DocumentTypeCode` | 2 = FR, 5 = NC | `DocumentoFiscal.tipo` (`DocumentoFiscal.java:75`) → `FR→2`, `NC→5` |
| `IsSpecimen` | optional | omitted (G15) |
| `InvoiceReceipt` \| `CreditNote` | choice | by `tipo` |
| `Transmission` | required | config (G9) |
| `RepositoryCode` | 1/2/3 | `3` when `ambiente = SIMULADO` (`DocumentoFiscal.java:79`) |
| `ds:Signature` | `minOccurs=0` in `EnvelopedSignature.xsd` | omitted (XAdES deferred) |

### Header group `grDfeHeaderCommon` (FR and NC)

| Element | Type/rule | Source |
|---|---|---|
| `IsIsolatedAct`, `SelfBilling` | optional | omitted |
| `LedCode` | 1–99999 | `LED_SIMULADO` constant (series `led_codigo` stays null) |
| `Serie` | 1–20, `[aA-zZ0-9]+([_-][aA-zZ0-9]+)*` | `serie_codigo` (`DocumentoFiscal.java:85`), e.g. `SIM-FR-2026` ✓ (validated) |
| `DocumentNumber` | 1–999 999 999 | `numero` (`DocumentoFiscal.java:91`), a `Long` → JAXB `int`. Refuse if > 999 999 999 |
| `InnerDocumentNumber` | optional `stCode` (no spaces) | omitted. `numero_formatado` `SIM-FR-2026/1` contains `/`; allowed, but redundant |
| `IssueDate` | `xs:date` ≥ 2021-01-01 | `data_emissao` (`DocumentoFiscal.java:97`) |
| `IssueTime` | `xs:time` | `emitido_em` (`DocumentoFiscal.java:100`) → `Atlantic/Cape_Verde` `LocalTime`, truncated to seconds, **no timezone** (`FIELD_UNDEFINED`) |

### FR only (`InvoiceReceipt`)

| Element | Rule | Source |
|---|---|---|
| `OrderReference`, `TaxPointDate`, `PaymentParty`, `Delivery`, `ExtraFields` | optional | omitted |
| `EmitterParty` | `ctParty`: `TaxId@CountryCode`, `Name` 3–150 no extra spaces, `Address@CountryCode` + `AddressDetail` ≤100 required, `City` ≤100 optional, `Contacts` | `emitente_nif` (`:105`), `emitente_firma` (`:108`, **varchar 200 > 150, gap**), `emitente_morada` (`:111`) → `AddressDetail`, `emitente_localidade` (`:114`) → `City`; CountryCode `CV`; Contacts omitted (G12) |
| `ReceiverParty` | same | `adquirente_nif` (`:132`, validated `^[1-9]\d{8}$` by `ValidacaoEmissao:124`), `adquirente_nome` (`:135`, 3–150 validated), `adquirente_morada` (`:138`), `adquirente_localidade` (`:142`) → `City`; CountryCode `CV` |
| `Lines/Line` (one) | see Line table | `t_documento_fiscal_linha` |
| `Totals` | see Totals table | document totals |
| `References` | optional on FRE | omitted |
| `Payments/Payment` | `PaymentMeansCode` (D19B), `PaymentDate`, `PaymentAmount` > 0 | `meio_pagamento_codigo` (`:188`), `data_emissao`, `valor_liquido` (`:211`) (G11) |
| `Note` | optional, 10–500, no extra spaces | omitted |

### NC only (`CreditNote`)

| Element | Rule | Source |
|---|---|---|
| `IssueReasonCode` | required enum | `motivo_codigo` (`DocumentoFiscal.java:175`) → constant table (G6) |
| `RappelPeriod` | only with `DRP` | omitted |
| `EmitterParty`/`ReceiverParty` | as FR | NC snapshot copies FR emitter/buyer (Phase 135) |
| `Lines`/`Totals` | as FR | NC line/totals (positive magnitudes, Phase 135) ✓ (`stDecimal5MinInc0`) |
| `References/Reference/FiscalDocument` | `stFiscalDocument` (IUD or old-doc format) | **IUD of the FR**, from the FR's XML satellite row via `documento_origem_id` (`:170`) |
| `Note` | 10–500, `stNoExtraSpaces` (no leading/trailing/double spaces, no newlines) | **Controlled text**, e.g. `"Nota de crédito: {motivo.rotulo()} — {FR numero_formatado}"`. Not `motivo_texto` (`:179`): it can be 1 character long (`ValidacaoNotaCredito:64-67` only checks 1–200), may contain newlines, and is free text (sigilo, same reasoning as D-05) |
| `Payments` | absent in CreditNote | — |

### `Lines/Line` (one line per document)

| Element | Rule | Source (`DocumentoFiscalLinha.java`) |
|---|---|---|
| `@LineTypeCode` | default `N` | omitted (N) |
| `Id` | optional `stId` | `numero_linha` (`:42`) as string |
| `Quantity` + `@UnitCode` | ≥0, 5 decimals (Read Me says 6 for quantity; XSD says 5) | `quantidade` (`:48`, scale 4) + `"EA"` (G13) |
| `Price` | ≥0 | `preco_unitario` (`:51`) = base |
| `PriceExtension` | ≥0 | `quantidade × preco_unitario` = `valor_base` (`:54`) |
| `Discount` | optional | omitted |
| `NetTotal` | ≥0 | `valor_base` (`:54`) |
| `Tax` (IVA) | `@TaxTypeCode` + exactly one of `TaxPercentage` (>0, ≤100, ≤3 dec) / `TaxAmount` / `TaxExemptionReasonCode` | regime NORMAL: `IVA` + `taxa_iva` (`:57`). ISENTO: `NA` + `motivo_isencao_codigo` (`:63`, codes 1–21 match `CV_EFatura_TaxExemptionReason_v1.0.xsd`, 42 enumeration entries incl. docs) |
| `Tax` (IR) | max **2** `Tax` per line | only if `valor_retencao > 0`: `IR` + `taxa_retencao` (`:66`) |
| `Item/Description` | 1–300, no extra spaces | `descricao` (`:45`, controlled text ≤200) |
| `Item/EmitterIdentification` | required `stCode` ≤50, no spaces | constant (`"HONORARIOS"` for FR, `"NOTACREDITO"` for NC) |

`taxa_iva` scale 4 (`15.0000`) **validates** against `fractionDigits=3`: Xerces ignores trailing zeros (verified in this session). Still normalise with `stripTrailingZeros()` so the stored XML is clean. Use `toPlainString` semantics; JAXB prints `BigDecimal` with `toPlainString` (verified: no `1E+1`).

### `Totals`

| Element | XSD | Fields Map | Source |
|---|---|---|---|
| `PriceExtensionTotalAmount` | required | `{1}` | `total_base` (`DocumentoFiscal.java:199`) |
| `ChargeTotalAmount` | optional | `{1}` | `0.00` (send it; the map says required) |
| `DiscountTotalAmount` | optional | `{1}` | `0.00` (send it) |
| `NetTotalAmount` | required | `{1}` | `total_base` |
| `Discount` | optional | `?` | omitted |
| `TaxTotalAmount` | required | `{1}` | `total_iva` (`:202`) (0 when ISENTO) |
| `WithholdingTaxTotalAmount` | optional | `{1}?` | `total_retencao` (`:205`). Send it whenever > 0. Read Me 2022-02-19: "soma dos impostos IR … subtraído em PayableAmount" |
| `PayableRoundingAmount` | optional | `?` | omitted |
| `PayableAmount` | required | `{1}` | `valor_liquido` (`:211`) = `total − retenção` (`CalculoFiscal.java:22,80`), consistent with the Read Me rule |
| `PayableAlternativeAmount` | optional | `*` | omitted |

### Gaps (fields the snapshot lacks or violates)

| Gap | Impact | Recommendation |
|---|---|---|
| `emitente_firma` up to 200 (`ConfiguracaoFiscalRequest.java:26`, `ConfiguracaoFiscal.java:50`) vs `Name` 3–150 | A firma with 151–200 characters makes **every** document of that office `REJEITADO` | Builder refuses with a clear code (`FIRMA_EXCEDE_150`, stored in `ultimo_erro`). Also tighten `ConfiguracaoFiscalRequest` firma to `@Size(min=3, max=150)` for new edits (a Phase 133 behaviour change, so confirm with the user, Q1) |
| Internal whitespace (double spaces, tab, newline) in nome/firma/morada/localidade | `stNoExtraSpaces` rejects it (verified) | Builder collapses `[\s]+`→`" "` and trims every text value before JAXB (documented normalisation; unit-tested) |
| Contacts (email/phone of emitter and buyer) not snapshotted | G12 | Omit in v3.0; add snapshot columns in the real-connection milestone |
| `moeda` (`:191`, CVE) | The FR/NC structure has no currency element (only `PayableAlternativeAmount@CurrencyCode`) | Not mapped; CVE is implicit |
| `metodo_pagamento` (`:185`) | Only the code is mapped | — |
| `InnerDocumentNumber`, `TaxPointDate`, `OrderReference` | optional | not needed |
| IUD of the FR for the NC's `References` | Exists only after the FR has been processed | Outbox ordering + transient wait (Pitfall 4) |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.2 (BOM 3.4.1, no `<version>`) | JAXB API in **compile** scope (today only transitive/runtime via Hibernate) | Jakarta EE 10 [VERIFIED: spring-boot-dependencies-3.4.1.pom `jakarta-xml-bind.version` 4.0.2] |
| `org.glassfish.jaxb:jaxb-runtime` | 4.0.5 (BOM) | marshal/unmarshal | [VERIFIED: BOM `glassfish-jaxb.version` 4.0.5] |
| `org.jvnet.jaxb:jaxb-maven-plugin` | 4.0.16 (latest; Central `lastUpdated` 2026-06-11) | generate classes from vendored XSD | Jakarta lineage of the highsource plugin; executed in this session [VERIFIED: repo1.maven.org maven-metadata.xml; local generation run] |
| JDK `javax.xml.validation` / `SecureRandom` / `MessageDigest` | JDK 21/23 | validate, random part of the IUD, SHA-256 | no dependency |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-test` `ApplicationContextRunner` | 3.4.1 (already in `spring-boot-starter-test`) | prove that an unknown `EFATURA_MODE` fails startup without a full `@SpringBootTest` | DFE-03 test |
| Testcontainers PostgreSQL | BOM 1.20.4 (existing) | SKIP LOCKED / lease / CHECK / migration parity ITs | all outbox ITs |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Separate `@Service` with `@Transactional` methods for claim/result | `TransactionTemplate` inside the job | Equivalent. The separate bean is more consistent with `NumeracaoService`/`DocumentoFiscalService` and avoids the self-invocation trap |
| Native conditional UPDATE for state changes | JPA load + `@Version` + save | JPA cannot express "only if still the version I claimed" without an extra read. Native is explicit and testable |
| `@Scheduled` poller only | `@TransactionalEventListener(AFTER_COMMIT)` nudge | Not needed. `@EnableAsync` does not exist; the poller is the source of truth (ARCH §5.3) |

**Installation (`backend/pom.xml`):** add the two dependencies (no version) and the plugin block from STACK.md §5, keeping `<args><arg>-no-header</arg></args>` and **no** `<generatePackage>`. Bindings file `src/main/resources/xsd/bindings/efatura.xjb` exactly as in STACK.md §5 (verified again). Place the `.xjb` **outside** `xsd/efatura/` so the resolver whitelist only covers XSDs.

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `jakarta.xml.bind:jakarta.xml.bind-api` | Maven Central | many years (4.0.2 in BOM) | very high | github.com/jakartaee/jaxb-api | ERR (registry unreachable) | Approved [ASSUMED]: BOM-managed by Spring Boot 3.4.1, already on the classpath transitively |
| `org.glassfish.jaxb:jaxb-runtime` | Maven Central | many years | very high | github.com/eclipse-ee4j/jaxb-ri | ERR (registry unreachable) | Approved [ASSUMED]: BOM-managed, already transitive |
| `org.jvnet.jaxb:jaxb-maven-plugin` | Maven Central | 4.x line since 2023; 4.0.16 from 2026-06 | n/a (build plugin) | github.com/highsource/jaxb-tools | not checked (build plugin) | Approved [ASSUMED]: build-time only, no runtime footprint, already cached in `~/.m2` and executed |

`slopcheck scan` was installed and run. It could not reach `search.maven.org` (proxy 403), so every package is tagged `[ASSUMED]`, per protocol. Existence and versions were confirmed on `repo1.maven.org` metadata and in the Boot BOM. Risk is minimal (two BOM-managed artifacts already on the classpath + one build plugin), but the protocol requires the planner to add a `checkpoint:human-verify` before the pom change. **Packages removed:** none. **Flagged [SUS]:** none.

## Architecture Patterns

### System Architecture Diagram

```
 [Emission tx (134/135, UNCHANGED)]
   documento + linha + t_comunicacao_fiscal(PENDENTE, tentativas 0, proxima_tentativa_em NULL) ── COMMIT
                                     │
                                     ▼  (≤ 30 s later)
 [FiscalOutboxJob @Scheduled fixedDelay]  (no SecurityContext; catch Throwable at top and per item)
   │ tx#1 (short, ComunicacaoFiscalTransacoes.reclamar)
   │   SELECT id … WHERE estado='PENDENTE' AND due AND lease free
   │   ORDER BY created_at LIMIT n FOR UPDATE SKIP LOCKED
   │   UPDATE lease_ate=now+lease, tentativas=tentativas+1, versao=versao+1  → claimed (id, tenantId, docId, versao)
   ▼
 for each claimed item (outside any tx):
   load snapshot by (tenantId, docId) [readOnly tx]   ──► doc ambiente ≠ gateway.modo()? → Rejeitado(AMBIENTE_INCOMPATIVEL)
   existing XML row? ──yes──► reuse (crash recovery; same IUD)
        │no
        ▼
   NC? → FR XML row present? ──no──► ErroTransitorio("origem sem IUD")
        ▼
   IudGerador (repo=3, data, NIF, LED 99999, tipo, numero, SecureRandom, Luhn)
   DfeXmlBuilder (snapshot → JAXB Dfe) → marshal UTF-8 → DfeValidador (XSD)
        │ invalid → Rejeitado(XSD_INVALIDO, first error)        (no XML row written)
        ▼ valid
   tx#2: INSERT t_documento_fiscal_xml (iud, xml, sha256, versao_formato) ON CONFLICT (documento_fiscal_id) DO NOTHING
        ▼
   EfaturaGateway.comunicar(DocumentoComunicavel)   [SimuladoEfaturaGateway: validate again → AceiteSimulado | Rejeitado; injectable faults]
        ▼
   ResultadoComunicacao ──► EstadoMapper.estadoPara(resultado, ambiente, tentativas, max)
        ▼
   tx#3: UPDATE … SET estado, ultimo_erro, proxima_tentativa_em (backoff), lease_ate=NULL, versao+1
         WHERE id=? AND versao=:claimed            (0 rows → lease lost, discard)
        ▼
   estado == ERRO? → notificar(tenantId, docId) best effort (own try/catch; dedup ON CONFLICT)

 [User] POST /documentos-fiscais/{id}/comunicacao/reprocessar (financeiro:edit)
   tx: UPDATE … SET estado='PENDENTE', tentativas=0, proxima_tentativa_em=now, ultimo_erro=NULL, lease_ate=NULL
       WHERE tenant_id=? AND documento_fiscal_id=? AND estado IN ('ERRO','REJEITADO')  + audit event
```

### Recommended Project Structure
```
backend/src/main/resources/xsd/efatura/            # 22 vendored XSD + README.md (provenance, manifest)
backend/src/main/resources/xsd/bindings/efatura.xjb
backend/src/main/java/com/lexcv/fiscal/efatura/    # hand-written; generated code goes to com.lexcv.fiscal.efatura.xsd (target/)
  EfaturaConfig.java             # @ConfigurationProperties + fail-fast mode bean + gateway bean
  EfaturaGateway.java            # port; ResultadoComunicacao sealed (AceiteSimulado | Rejeitado | ErroTransitorio)
  SimuladoEfaturaGateway.java
  DfeXmlBuilder.java             # pure: DocumentoComunicavel → Dfe
  DfeMarshaller.java             # JAXBContext singleton, Marshaller per call
  DfeValidador.java              # Schema loaded at startup; whitelist resolver
  IudGerador.java                # pure + SecureRandom injected
  DocumentoComunicavel.java      # immutable projection of the snapshot (no JPA entity crosses the port)
  MapeamentoEfatura.java         # constant tables: tipo→code, motivo→IssueReasonCode, LED_SIMULADO, UnitCode, EmitterIdentification
backend/src/main/java/com/lexcv/services/fiscal/
  ComunicacaoFiscalTransacoes.java  # @Transactional reclamar / registarResultado / reprocessar / gravarXml
  EstadoComunicacaoMapper.java      # the single (resultado, ambiente) → estado function
  BackoffComunicacao.java           # pure delay table
  NotificacaoComunicacaoFiscal.java # recipients + criar
backend/src/main/java/com/lexcv/jobs/FiscalOutboxJob.java
backend/src/main/java/com/lexcv/models/DocumentoFiscalXml.java  (@Immutable) + repository (save + finders only)
backend/migrations/136-efatura-comunicacao.sql
```
Note: putting hand-written code in `com.lexcv.fiscal.efatura` and generated code in the sub-package `com.lexcv.fiscal.efatura.xsd` means the SpotBugs filter `<Package name="com.lexcv.fiscal.efatura.xsd"/>` (exact match, not a regex) excludes only generated code. That is the rule "nenhuma exclusão para código próprio".

### Pattern 1: Outbox columns and migration (`t_comunicacao_fiscal`)

Existing columns (`ComunicacaoFiscal.java`, script 134 line 112+): `id, tenant_id, documento_fiscal_id (unique), ambiente, estado, tentativas, proxima_tentativa_em, created_at, updated_at, versao`. **Reuse `proxima_tentativa_em`**; do not add a `proximo_em`.

Add:
| Column | Type | Purpose |
|---|---|---|
| `lease_ate` | `timestamptz NULL` | claimed-until; NULL = free |
| `ultimo_erro` | `varchar(500) NULL` | short **user-safe** PT message + code, never a stack trace |
| `ultimo_erro_codigo` | `varchar(64) NULL` | machine code (`XSD_INVALIDO`, `ORIGEM_SEM_IUD`, `FALHA_TRANSITORIA`, `AMBIENTE_INCOMPATIVEL`, `FIRMA_EXCEDE_150`) |
| `concluido_em` | `timestamptz NULL` | terminal timestamp (optional but useful for the 137 email gate) |

Constraint and index (entity + script):
```java
@org.hibernate.annotations.Check(name = "ck_comunicacao_fiscal_autorizado_producao",
        constraints = "estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO'")
@Table(name = "t_comunicacao_fiscal", ..., indexes = {
    @Index(name = "idx_comunicacao_fiscal_tenant_estado", columnList = "tenant_id, estado"),
    @Index(name = "idx_comunicacao_fiscal_estado_proxima", columnList = "estado, proxima_tentativa_em")})
```
`@Check` has `name()` and `constraints()` in Hibernate 6.6.4 [VERIFIED: javap on hibernate-core-6.6.4.Final.jar]. `ddl-auto: update` does **not** add check constraints to an already existing table [ASSUMED]: the script adds the constraint idempotently (`DO $$ … IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_…') THEN ALTER TABLE … ADD CONSTRAINT …`). A 136 IT asserts that it exists and that an `UPDATE … SET estado='AUTORIZADO'` on a SIMULADO row raises `23514`.

New insert-only satellite `t_documento_fiscal_xml` (entity `DocumentoFiscalXml`, `@Immutable`, `updatable=false`, no setters, repository extends `Repository` with `save` + `findByTenantIdAndDocumentoFiscalId` only, pinned like `DocumentoFiscalImutabilidadeTest`):
| Column | Type |
|---|---|
| `id` | uuid PK |
| `tenant_id` | uuid not null |
| `documento_fiscal_id` | uuid not null, `uk_documento_fiscal_xml_documento` |
| `iud` | varchar(45) not null, `uk_documento_fiscal_xml_iud` |
| `ambiente` | varchar(32) not null (converter) |
| `repositorio_codigo` | smallint/integer not null |
| `led_codigo` | integer not null |
| `versao_formato` | varchar(20) not null (`"2024-05-27"`) |
| `xml` | text not null |
| `xml_sha256` | varchar(64) not null (hex of the exact UTF-8 bytes stored) |
| `gerado_em` | timestamptz not null (from injected `Clock`) |

Only valid XML is written. A pre-flight validation failure → `REJEITADO` without a row, and reprocess then makes a fresh IUD. That is safe because nothing ever left the system in SIMULADO. When a row exists, it is reused forever (same IUD, same bytes). This answers "IUD guardado na satélite" and keeps the satellite immutable.

### Pattern 2: JAXB build (verified)
```xml
<!-- backend/pom.xml: plugin block per STACK.md §5; the experiment used exactly this -->
<plugin>
  <groupId>org.jvnet.jaxb</groupId><artifactId>jaxb-maven-plugin</artifactId><version>4.0.16</version>
  <executions><execution><goals><goal>generate</goal></goals></execution></executions>
  <configuration>
    <schemaDirectory>src/main/resources/xsd/efatura</schemaDirectory>
    <schemaIncludes><include>EnvelopedSignature.xsd</include></schemaIncludes>
    <bindingDirectory>src/main/resources/xsd/bindings</bindingDirectory>
    <bindingIncludes><include>*.xjb</include></bindingIncludes>
    <args><arg>-no-header</arg></args>
  </configuration>
</plugin>
```
Result (JDK 21, `-Dmaven.compiler.release=21`): `com/lexcv/fiscal/efatura/xsd` 52, `org/etsi/uri/_01903/v1_3` 53, `…/v1_4` 3, `org/w3/_2000/_09/xmldsig_` 24, `un/unece/uncefact/…` 2+1+2 = **137** files. Relevant generated types: `CtDfe` (`id`, `version`, `documentTypeCode` BigInteger, `repositoryCode` BigInteger), `InvoiceReceipt`/`CreditNote` (`ledCode` int, `documentNumber` int, `issueDate`/`issueTime` XMLGregorianCalendar, `issueReasonCode` **String**), `Payment.paymentMeansCode` **String** (not an enum: only the validator enforces the list), `StTaxId{value, countryCode: ISOTwoletterCountryCodeContentType}`, `Tax{taxTypeCode: StTaxTypeCode, taxPercentage, taxExemptionReasonCode String}`. `JAXBContext.newInstance(Dfe.class)` is thread-safe; make it a singleton bean and create a `Marshaller` per call (not thread-safe). Do not use `JAXB_FORMATTED_OUTPUT`; store the exact bytes that were hashed. [VERIFIED: local run]

JDK 23 (Dockerfile build stage `maven:3.9-eclipse-temurin-23`): not runnable here (JDK 21 only). Treat the first `docker build backend` (or the CI job) as the JDK 23 smoke test.

### Pattern 3: Hardened validator with whitelist classpath resolver (verified)
```java
// Verified in this session: works from exploded classes AND `java -jar` (Boot 3.4.1 nested jar).
SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");      // nothing may be fetched by URL
sf.setResourceResolver(new ClasspathXsdResolver());            // serves ONLY the 22 vendored files
Schema schema = sf.newSchema(new StreamSource(
        getClass().getClassLoader().getResourceAsStream("xsd/efatura/EnvelopedSignature.xsd"),
        "classpath:xsd/efatura/EnvelopedSignature.xsd"));       // systemId gives the resolver a base

// Validation of our own bytes, defense in depth:
SAXParserFactory spf = SAXParserFactory.newInstance();
spf.setNamespaceAware(true);
spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
Validator v = schema.newValidator();                            // per call (not thread-safe); Schema is
v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");            // thread-safe -> singleton built at startup
v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
v.validate(new SAXSource(spf.newSAXParser().getXMLReader(), new InputSource(new ByteArrayInputStream(xml))));
```
The resolver resolves `systemId` relative to the base `classpath:xsd/efatura/…`. It normalises with **string operations or `URI.resolve`, not `Paths.get`**: SpotBugs flagged `PATH_TRAVERSAL_IN` on `Paths.get` in the experiment. It returns a resource only if the normalised path is in a `Set.of(…22 names…)`, and `null` otherwise. `null` plus `ACCESS_EXTERNAL_SCHEMA=""` means the load fails, which is what we want. The XXE probe (external entity `file:///…secret`) is rejected by both `ACCESS_EXTERNAL_DTD=""` ("'file' access is not allowed") and `disallow-doctype-decl` ("DOCTYPE is disallowed"). [VERIFIED: local run]

### Pattern 4: IUD (pure, verified Luhn)
```java
// payload = repo(1) + AAMMDD + NIF(9) + LED(5) + tipo(2) + numero(9) + aleatorio(10); IUD = "CV" + payload + luhn(payload)
static int luhn(String payload) {               // double every 2nd digit starting at the RIGHTMOST payload digit
    int s = 0;
    for (int i = 0; i < payload.length(); i++) {
        int d = payload.charAt(payload.length() - 1 - i) - '0';
        if (i % 2 == 0) { d *= 2; if (d > 9) d -= 9; }
        s += d;
    }
    return (10 - s % 10) % 10;
}
// aleatorio: 10 digits from an injected SecureRandom: String.format("%010d", random.nextLong(10_000_000_000L))
```
Test vectors: the official sample `CV1200520123456789000112345678901112345678904` gives DV 4 (verified). Also round-trip with the regex `stDfeId` and the Kowts `isValid` regex `^CV([1-3])(\d{2})(\d{2})(\d{2})([1-9]\d{8})(\d{5})(\d{2})(\d{9})(\d{10})(\d)$`. Refuse when: NIF not `^[1-9]\d{8}$`, `numero` > 999 999 999, `LED_SIMULADO` used with `ambiente ≠ SIMULADO`. `AAMMDD` comes from `data_emissao` (Cabo Verde local date, already in the snapshot). Use `SecureRandom.nextLong(bound)` (Java 17+). `Random` triggers FindSecBugs `PREDICTABLE_RANDOM`.

### Pattern 5: Outbox job (claim / process / record)
```sql
-- reclamar (tx#1, @Transactional in ComunicacaoFiscalTransacoes; native query, returns ids + versao)
SELECT id FROM t_comunicacao_fiscal
 WHERE estado = 'PENDENTE'
   AND (proxima_tentativa_em IS NULL OR proxima_tentativa_em <= :agora)   -- 134/135 insert NULL!
   AND (lease_ate IS NULL OR lease_ate < :agora)                         -- expired lease = crash recovery
 ORDER BY created_at, id
 LIMIT :lote
 FOR UPDATE SKIP LOCKED;
UPDATE t_comunicacao_fiscal
   SET lease_ate = :agora + :lease, tentativas = tentativas + 1, versao = versao + 1, updated_at = :agora
 WHERE id IN (:ids);
-- registarResultado (tx#3)
UPDATE t_comunicacao_fiscal
   SET estado = :estado, ultimo_erro = :msg, ultimo_erro_codigo = :cod,
       proxima_tentativa_em = :proxima, lease_ate = NULL, concluido_em = :concluido,
       versao = versao + 1, updated_at = :agora
 WHERE id = :id AND versao = :versaoReclamada;      -- 0 rows => lease lost, drop result
```
- Counting the attempt at claim time means a crash mid-flight still counts toward the 8.
- Job: `@Scheduled(fixedDelayString = "${app.efatura.outbox.intervalo:PT30S}", initialDelayString = "PT20S")`. `fixedDelay` cannot overlap with itself within an instance; SKIP LOCKED + lease covers several instances. Batch 20, lease 2 min (≫ simulated work).
- Backoff (pure `BackoffComunicacao.atraso(tentativas)`): 1→30 s, 2→2 min, 3→5 min, 4→15 min, 5→30 min, 6→1 h, 7→3 h. After the 8th failed attempt → `ERRO`. Deterministic, no jitter. Make the maximum a constant (8) and test the table.
- `REJEITADO` is terminal immediately (definitive); `ErroTransitorio` → PENDENTE + backoff, or `ERRO` when `tentativas >= 8`.
- **No tenant iteration**: rows carry `tenant_id`, and suspended tenants are **not** skipped (CONTEXT). Every downstream read uses `(tenantId, documentoId)`.
- Isolation: top-level `catch (Throwable)` in `executar()`, plus per-item `catch (Throwable)`. An unexpected exception for one item becomes `ErroTransitorio("FALHA_INTERNA")` (never the exception message verbatim) so it retries and eventually reaches ERRO, instead of looping forever.
- `application.yml`: `spring.task.scheduling.pool.size: 3` (Boot 3.4.1 metadata: default `1`, "Doesn't have an effect if virtual threads are enabled" [VERIFIED: spring-boot-autoconfigure-3.4.1 metadata]). `SchedulingConfig` defines no custom `TaskScheduler`, so the property applies.
- The job class has no `@Transactional`. All tx methods live in `ComunicacaoFiscalTransacoes` (another bean), so Spring proxies apply (self-invocation trap).

### Pattern 6: `EFATURA_MODE` fail-fast + gateway
```java
@ConfigurationProperties("app.efatura")
public record EfaturaProperties(String modo, Transmissao transmissao, Outbox outbox) { ... }

@Configuration @EnableConfigurationProperties(EfaturaProperties.class)
class EfaturaConfig {
  @Bean EfaturaGateway efaturaGateway(EfaturaProperties p, DfeValidador v) {
    String modo = p.modo() == null ? "" : p.modo().trim();
    if (!"SIMULADO".equals(modo)) {
      throw new IllegalStateException("EFATURA_MODE='" + modo + "' não é suportado neste build: "
          + "só SIMULADO existe (não há implementação real). Corrija EFATURA_MODE e reinicie.");
    }
    validarTransmissaoSimulada(p.transmissao());   // Code [A-Z0-9]{1,10}, Name 3–150, NIF ^[1-9]\d{8}$
    return new SimuladoEfaturaGateway(v);
  }
}
```
`application.yml`:
```yaml
app:
  efatura:
    modo: ${EFATURA_MODE:SIMULADO}
    transmissao:
      nif-transmissor: ${EFATURA_TRANSMISSOR_NIF:<valid test NIF>}
      software-codigo: ${EFATURA_SOFTWARE_CODIGO:LEXCVSIM}
      software-nome: ${EFATURA_SOFTWARE_NOME:LexCV}
      software-versao: ${EFATURA_SOFTWARE_VERSAO:3.0.0}
spring:
  task:
    scheduling:
      pool:
        size: 3
```
A set-but-empty env var resolves to `""`, not to the default [ASSUMED: Spring placeholder semantics]. Hence: (a) compose uses `EFATURA_MODE: "${EFATURA_MODE:-SIMULADO}"` (`:-` also covers empty), and (b) blank → abort with the message above. Test with `ApplicationContextRunner().withUserConfiguration(EfaturaConfig.class, …).withPropertyValues("app.efatura.modo=REAL").run(ctx -> assertThat(ctx).hasFailed())`, plus `""`, `simulado` (decide: case-sensitive; recommended) and `SIMULADO` → has a gateway bean. Plumbing: `backend/.env.example` (commented, optional), `docker-compose.yml` and `docker-compose.hostinger.yml` backend env blocks (both already interpolate `SPRING_JPA_HIBERNATE_DDL_AUTO` the same way). `.github/workflows/deploy.yml` only builds/pushes images (no backend env), so add nothing there except, at most, a comment. Verify against how 133–135 handled it.

Sealed result and the single mapping function:
```java
public sealed interface ResultadoComunicacao {
  record AceiteSimulado(String referencia) implements ResultadoComunicacao {}
  record Rejeitado(String codigo, String mensagem) implements ResultadoComunicacao {}
  record ErroTransitorio(String codigo, String mensagem) implements ResultadoComunicacao {}
}
static EstadoComunicacaoFiscal estadoPara(ResultadoComunicacao r, AmbienteFiscal a, int tentativas, int max) {
  return switch (r) {
    case AceiteSimulado s -> switch (a) { case SIMULADO -> EstadoComunicacaoFiscal.ACEITE_SIMULADO; };
    case Rejeitado x -> EstadoComunicacaoFiscal.REJEITADO;
    case ErroTransitorio e -> tentativas >= max ? EstadoComunicacaoFiscal.ERRO : EstadoComunicacaoFiscal.PENDENTE;
  };
}
```
**Do not add `AUTORIZADO` or `PRODUCAO` constants in v3.0.** The exhaustive `switch (a)` turns adding `PRODUCAO` to `AmbienteFiscal` into a compile error at exactly this line, which forces the future author to decide. The DB CHECK uses string literals, so it needs no Java constants. `EstadoComunicacaoFiscal` gains `ACEITE_SIMULADO("Aceite (simulação)")`, `REJEITADO("Rejeitado")`, `ERRO("Erro")`. The gateway also refuses documents whose `ambiente != SIMULADO` (`Rejeitado("AMBIENTE_INCOMPATIVEL")`).

### Pattern 7: Reprocessar
- Route: `POST /api/v1/documentos-fiscais/{id}/comunicacao/reprocessar` on `DocumentoFiscalController`, `@PreAuthorize("hasAuthority('financeiro:edit')")`. A malformed or foreign id → the same 404 body as detalhe (shared `idDocumento` helper, no oracle).
- Service (`@Transactional`): conditional UPDATE (diagram). 0 rows and the document exists → `409 COMUNICACAO_NAO_REPROCESSAVEL` ("Só é possível reprocessar comunicações em erro ou rejeitadas."). Audit `documento_fiscal_reprocessar_comunicacao` through `AuditoriaFiscalService` (MANDATORY, same tx), with author name and document number only.
- Response 200: the new `estado` + `tentativas` (DTO record, never an entity: `ENTITY_MASS_ASSIGNMENT`).
- Update `DocumentoFiscalControllerTest.exatamenteSeisHandlersSemRotasQueAlterem` → 7, deliberately, in the same plan.
- Web: `podeReprocessarComunicacao(perms) = hasPermission(perms, "financeiro:edit")` (exact, same as `podeRegistarPagamentos`, `use-faturacao.ts:202`). The button lives in a **new component file** (e.g. `documentos-fiscais/[id]/reprocessar-comunicacao.tsx`), because `scripts/verify-documentos-fiscais.mjs` forbids `useMutation`/`method: "POST"` in the detail page (`page.tsx`). The NC dialog set the same precedent. Invalidate the detail and list queries on settle.

### Pattern 8: Notification on ERRO
- Backend `CategoriaNotificacao` gains `COMUNICACAO_FISCAL_FALHOU(false)`. Recommendation: **not silenceable**, because it is a fiscal obligation (ARCH §5.3); confirm with the user (Q3). Web: add it to `NotificacaoCategoria` (`types/notificacoes.ts`), `CATEGORIA_LABEL_MAP` ("Comunicação fiscal falhou"), the badge map (`"red"`), and the non-silenceable list in `lib/notificacao-categoria.ts`. The `Record<NotificacaoCategoria,…>` types make the compiler find every site.
- Recipients: `userRepository.findByTenantId(tenantId)` filtered by `ativo` and `resolucaoPapeisService.resolverPermissoesEfectivas(user).contains("financeiro:manage")`. This is **not** `findByTenantIdAndRoleName("ADMIN")` as in `AlertasDiariosJob`: v2.17 office roles mean ADMIN ≠ "has financeiro:manage".
- Write: `notificacaoService.criar(tenantId, userId, "COMUNICACAO_FISCAL_FALHOU", titulo, mensagem, "documento_fiscal", documentoId.toString(), "/financeiro/documentos-fiscais/" + documentoId)`, per recipient in its own try/catch, **after** the ERRO tx commits. `criar` uses `INSERT … ON CONFLICT DO NOTHING` on `uk_notificacao_dedup (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)` (`migrations/88-…sql`), so re-running is idempotent.
- **Dedup caveat:** with `entidade_id = documentoId`, a document that fails, is reprocessed and fails again does **not** notify again (the row already exists, even if read). This matches "dedup por documento" literally; Q2 asks whether that is acceptable.

### Anti-Patterns to Avoid
- **XML/gateway inside the emission tx**: never touch `PagamentoFaturadoService`/`NotaCreditoService` beyond what exists. The PENDENTE row is the hook (P-07).
- **`@Transactional` on the job, or self-invoked tx methods**: hidden long transactions or no transaction at all.
- **JPA dirty-checking for state transitions**: use explicit conditional UPDATEs and bump `versao` yourself, so the claimed-version guard is real.
- **Persisting invalid XML**: it would be reused by reprocess forever.
- **`Paths.get` in the resolver**: SpotBugs `PATH_TRAVERSAL_IN` (observed).
- **New SpotBugs exclusions for own code**: only the generated packages.
- **`AUTORIZADO`/`PRODUCAO` enum constants "for later"**: dead dangerous paths (P-08).
- **Free text (`motivo_texto`, honorário descrição) in the XML**: sigilo + XSD whitespace rules.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| DFE XML serialisation | String concatenation / DOM / StAX by hand | JAXB classes generated from the vendored XSD | Strict element order, namespaces, decimals, enums |
| Structural validation | Own checks of lengths/patterns | `javax.xml.validation` against the XSD | The XSD is the arbiter; the validator caught 6 classes of error in the experiment |
| Job concurrency across instances | `synchronized`, in-memory flags, ShedLock | `FOR UPDATE SKIP LOCKED` + lease + conditional UPDATE | Works with N instances, survives crashes |
| Notification dedup | `exists…` then `save` | `NotificacaoService.criar` (ON CONFLICT) | Already race-free (Phase 94 CR-01) |
| Random IUD part | `Random`, `Math.random`, UUID digits | `SecureRandom.nextLong(10^10)` | FindSecBugs `PREDICTABLE_RANDOM` |
| Permission resolution for recipients | Role-name queries | `ResolucaoPapeisService.resolverPermissoesEfectivas` | Single authority-resolution point since Phase 126 |

**Key insight:** every "format" decision that the XSD does not settle must live in **one** constant table (`MapeamentoEfatura`) with a test, so that the end-of-phase gate answer, or the real-connection milestone, changes one file.

## Runtime State Inventory

Not a rename/refactor phase. Runtime-state facts that matter here:
| Category | Items Found | Action Required |
|---|---|---|
| Stored data | Every FR/NC emitted since 134/135 already has a `t_comunicacao_fiscal` row `PENDENTE` with `proxima_tentativa_em = NULL` (`PagamentoFaturadoService:309-316`, `NotaCreditoService:312-319`) | Claim query must treat NULL as due; the first job run will process the backlog (ordered by `created_at`, so FR before its NC) |
| Live service config | None. No external service is contacted in SIMULADO | none |
| OS-registered state | None | none |
| Secrets/env vars | New optional env vars `EFATURA_MODE`, `EFATURA_TRANSMISSOR_NIF`, `EFATURA_SOFTWARE_*` (none secret) | `.env.example` + 2 compose files |
| Build artifacts | `target/generated-sources/xjc` appears on the first build; IDEs need "generate sources" | Note in README/plan; nothing committed |

## Common Pitfalls

### Pitfall 1: Tests that pin today's behaviour break on purpose
**What goes wrong:** `DocumentoFiscalControllerTest.exatamenteSeisHandlersSemRotasQueAlterem` (6 handlers), `MigracaoFiscal134IT` (`43 + 14 + 10` Hibernate columns) and `MigracaoFiscal135IT`, plus `verify:documentos-fiscais` (no mutation/POST in the detail page; copy tokens) all fail once this phase lands.
**How to avoid:** the plan that adds the handler or columns updates these deliberately. Make the 134/135 parity ITs apply script 136 too, or exclude the new columns explicitly, and add a `MigracaoFiscal136IT` that proves script 136 == Hibernate for the new columns, the XML table, the CHECK and the index.
**Warning signs:** a "fix" that weakens a guard instead of extending it.

### Pitfall 2: `proxima_tentativa_em IS NULL` rows never picked up
Existing PENDENTE rows have NULL. A claim with only `<= now()` silently ignores the whole backlog.

### Pitfall 3: Lease lost, double result
A slow item exceeds its lease and another worker reclaims it, so both record results. Prevent this with `WHERE versao = :versaoReclamada` on the result UPDATE, and test it with a Testcontainers IT that runs two claims with an expired lease in between.

### Pitfall 4: NC processed before its FR has an IUD
The NC's `References/FiscalDocument` needs the FR IUD. Ordering by `created_at` handles the normal case. Otherwise, return `ErroTransitorio("ORIGEM_SEM_IUD")`, which consumes an attempt. Then a FR stuck in `ERRO`/`REJEITADO` drives its NCs to `ERRO` too. That is correct and visible, and reprocessing the FR then the NC recovers.

### Pitfall 5: XSD whitespace/length rules vs. snapshot text
`stNoExtraSpaces` (`Name`, `AddressDetail`, `City`, `Description`, `Note`) forbids leading, trailing and double spaces and newlines; `Name` is 3–150, but firma is ≤200; `Note` needs at least 10 characters. Normalise in the builder and refuse (REJEITADO) with a specific code when it cannot be fixed (firma > 150).

### Pitfall 6: Empty `EFATURA_MODE`
A compose line `EFATURA_MODE: ${EFATURA_MODE}` with the variable unset passes `""`, so startup aborts. Use `${EFATURA_MODE:-SIMULADO}`.

### Pitfall 7: Scheduler starvation
With pool size 1, a slow outbox run delays the 06:00 `AlertasDiariosJob` cron, and vice versa. Set pool size 3, and keep each outbox run bounded (batch size).

### Pitfall 8: Notification inside the result transaction
If `criar` throws (e.g. an orphan recipient raises `IllegalArgumentException`) inside the ERRO tx, the ERRO is rolled back and the row loops. Notify after the commit, with a try/catch per recipient (the `AlertasDiariosJob.notificar` pattern).

### Pitfall 9: `ddl-auto: update` and the CHECK
Dev databases that already have the table will not get the `@Check` from Hibernate [ASSUMED]. Only script 136 adds it. Run the script locally and in the IT.

### Pitfall 10: Docker daemon not running
`/var/run/docker.sock` exists, but `dockerd` is not running in this environment right now. Testcontainers ITs fail until it is started (`dockerd > /tmp/dockerd.log 2>&1 &`). `~/.docker-java.properties` already has `api.version=1.44`.

## Code Examples

### Builder fragment (verified to validate)
```java
// Source: executed experiment (scratchpad), generated package com.lexcv.fiscal.efatura.xsd
InvoiceReceipt fr = new InvoiceReceipt();
fr.setLedCode(MapeamentoEfatura.LED_SIMULADO);              // 99999
fr.setSerie(doc.serieCodigo());                             // "SIM-FR-2026"
fr.setDocumentNumber(Math.toIntExact(doc.numero()));        // refuse > 999_999_999 first
fr.setIssueDate(df.newXMLGregorianCalendarDate(y, m, d, DatatypeConstants.FIELD_UNDEFINED));
fr.setIssueTime(df.newXMLGregorianCalendarTime(hh, mm, ss, DatatypeConstants.FIELD_UNDEFINED));
fr.setEmitterParty(party(doc.emitenteNif(), norm(doc.emitenteFirma()), norm(doc.emitenteMorada()), norm(doc.emitenteLocalidade())));
fr.setReceiverParty(party(doc.adquirenteNif(), norm(doc.adquirenteNome()), norm(doc.adquirenteMorada()), norm(doc.adquirenteLocalidade())));
fr.setLines(linhas(...));      // Tax IVA (or NA + motivo) [+ IR when valor_retencao > 0]
fr.setTotals(totais(...));     // PriceExt=base, Charge=0, Discount=0, Net=base, Tax=iva, Withholding=ret, Payable=liquido
Payment p = new Payment(); p.setPaymentMeansCode(doc.meioPagamentoCodigo()); p.setPaymentDate(data); p.setPaymentAmount(doc.valorLiquido());
CtPaymentsPayment pays = new CtPaymentsPayment(); pays.getPayment().add(p); fr.setPayments(pays);
Dfe dfe = new Dfe(); dfe.setVersion("1.0"); dfe.setId(iud); dfe.setDocumentTypeCode(BigInteger.TWO);
dfe.setInvoiceReceipt(fr); dfe.setTransmission(transmissao()); dfe.setRepositoryCode(BigInteger.valueOf(3));
```

### SpotBugs exclusion (verified: removes all 387 generated-code findings, keeps own-code findings)
```xml
<Match><Package name="com.lexcv.fiscal.efatura.xsd"/></Match>
<Match><Package name="~org\.etsi\.uri\._01903\..*"/></Match>
<Match><Package name="org.w3._2000._09.xmldsig_"/></Match>
<Match><Package name="~un\.unece\.uncefact\..*"/></Match>
```
Without these, SpotBugs reports 391 findings (221 `EI_EXPOSE_REP` and others in generated getters). With them, only the 4 findings in the experiment's own code remained. Add a comment block in `spotbugs-exclude.xml` explaining that these are categorical exclusions of generated code.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Serie ≤10 | Serie ≤20 | 2022-01-27 | `SIM-FR-2026` valid |
| `Tax` unbounded per line | max 2 | 2022-02-19 | IVA + IR fits exactly; no third tax |
| Withholding not in Totals | `WithholdingTaxTotalAmount` subtracted from `PayableAmount` | 2022-02-19 | `PayableAmount = valor_liquido` |
| `TEU` tax type | removed | 2022-02-19 | — |
| Manual v7 IssueReasonCode numbering (1 = n.º 2 …) | value = paragraph number (2,3,6,7,8,9) + IN + DRP | ≤ 2024 | Do not use v7 tables |
| — | `SelfBilling` (2023-12-15), `DRP`/`RappelPeriod` (2024-05-27) | | not used by LexCV |

**Deprecated/outdated:** the `kriolos-efatura` copy (2021-12-19); manual v7 tables; `javax.xml.bind`/JAXB 2.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Vendoring the DNRE XSD in a private repo for local validation is permitted | Primary-Source Gate | Must remove files and fetch at build time |
| A2 | 2024-05-27 is the package in force | Gate G1 | XML rebuilt from snapshot; low cost |
| A3 | IUD trailing-27 layout + Luhn parity | Gate G2/G3 | Real platform rejects; none in SIMULADO |
| A4 | All `MotivoNotaCredito` → IssueReasonCode `"2"` | Gate G6 | Misclassified NC in real mode |
| A5 | `PaymentAmount` = `valor_liquido` | Gate G11 | Semantics only |
| A6 | Omitting `Contacts` is acceptable | Gate G12 | Real-mode rejection |
| A7 | `UnitCode="EA"`; `EmitterIdentification` constants | Gate G13 | Cosmetic |
| A8 | `ddl-auto: update` does not add `@Check` to existing tables | Pattern 1 / Pitfall 9 | Harmless either way (script is idempotent) |
| A9 | Set-but-empty env var resolves to `""` (default not applied) | Pattern 6 | Only changes which test case matters |
| A10 | `COMUNICACAO_FISCAL_FALHOU` should be non-silenceable | Pattern 8 | UX only |
| A11 | Synthetic `LED_SIMULADO = 99999` | Gate G4 | None in SIMULADO |
| A12 | Art.º 65 n.º 2 CIVA covers annulment/reduction of taxable value | Gate G6 | See A4 |
| A13 | All three JAXB packages are legitimate (slopcheck could not reach the registry) | Package audit | Very low (BOM-managed) |

## Open Questions (RESOLVED — Q1 firma 150, Q2 episode dedup, Q3 not silenceable: see 136-CONTEXT.md research_resolutions; Q4 → gate table G6/G11-G13 in plan 16; Q5 modoComunicacao on estado-emissao, plans 04/07/11/14)

1. **Firma > 150 characters (Q1)**
   - What we know: snapshot allows 200; the XSD `Name` allows 150.
   - Recommendation: the builder refuses with `FIRMA_EXCEDE_150`, and `ConfiguracaoFiscalRequest` is tightened to 3–150 (changes Phase 133 validation for new edits). Ask the user during planning.
2. **Re-notification after reprocess (Q2)**
   - What we know: dedup is per (recipient, documento, categoria).
   - Recommendation: accept (literal CONTEXT). Alternative: `entidade_id = documentoId + ":" + ciclo`.
3. **Silenceable or not (Q3)**: recommend not silenceable.
4. **IssueReasonCode / PaymentAmount / Contacts / UnitCode**: these belong to the end-of-phase gate table (G6, G11–G13), with the contabilista where relevant. They do not block planning, because each is one constant.
5. **Where the "Modo simulado" banner lives**
   - Recommendation: add `modoComunicacao` (`"SIMULADO"`) to `EstadoEmissaoResponse` (`GET /faturacao/estado-emissao`, view OR edit). Show the banner on the documentos-fiscais list and detail (UI-SPEC decides exact placement).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build/tests | ✓ | 21.0.11 (pom says 23; use `-Dmaven.compiler.release=21`) | — |
| Maven | build | ✓ | 3.9.11 | — |
| `jaxb-maven-plugin` 4.0.16 in `~/.m2` | generate-sources | ✓ | 4.0.16 (offline OK) | — |
| Docker daemon | Testcontainers ITs | ✗ (socket present, `dockerd` not running) | — | Start `dockerd` before `mvn verify`; `~/.docker-java.properties` api 1.44 present |
| JDK 23 build | Dockerfile | ✗ locally | — | CI / `docker build` smoke test |
| `xmllint` | optional cross-validation | ✓ | libxml2 | — |
| `efatura.cv` | primary-source gate | ✗ (egress 403) | — | Gate pending → user decision at phase end |
| pnpm/node | web tests | assumed ✓ (used in 135 gate) | — | — |

## Validation Architecture

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

## Security Domain

### Applicable ASVS Categories (L1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (no new auth; no DNRE credentials in v3.0) | — |
| V3 Session Management | no | existing JWT cookies |
| V4 Access Control | yes | `@PreAuthorize("hasAuthority('financeiro:edit')")` on reprocessar; tenant from principal; 404 without oracle for foreign ids; job uses the row's `tenant_id` for every read |
| V5 Input Validation | yes | Path id parsed as UUID (404 on malformed); XML built only from the snapshot via JAXB; XSD validation; whitespace normalisation; DTO records (no entity binding) |
| V5.5 Deserialization / XXE | yes | `FEATURE_SECURE_PROCESSING`, `ACCESS_EXTERNAL_DTD/SCHEMA=""`, `disallow-doctype-decl`, whitelist resolver (verified) |
| V6 Cryptography | partial | `SecureRandom` for the IUD; SHA-256 (`MessageDigest`) for the XML fingerprint; no custom crypto |
| V7 Error Handling & Logging | yes | `ultimo_erro` is a sanitized code + PT message (no stack traces or exception text to UI/DB); logs without document free text or NIF beyond what is needed; audit event for reprocessar |
| V8 Data Protection | yes | XML contains NIFs/names: stored in DB only (tenant-scoped); not exposed by a download endpoint in this phase (137 does that); no free-text motive (sigilo) in the XML |
| V10 Malicious code / V14 Config | yes | Vendored XSD pinned by SHA-256 test; `EFATURA_MODE` fail-fast; no new SpotBugs exclusions for own code |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| XXE / external schema fetch during validation | Information disclosure / SSRF | Hardened factories + whitelist resolver (verified rejection) |
| Simulated document presented as authorised | Spoofing / Repudiation | Sealed result + exhaustive switch; DB CHECK; `RepositoryCode=3`; `SIM-` series; UI copy gate |
| Cross-tenant reprocess / read of comunicação | Elevation / Info disclosure | Tenant-scoped UPDATE `WHERE tenant_id = ?`; same 404 for foreign ids |
| Double processing across instances | Tampering (duplicate results) | SKIP LOCKED + lease + versao-guarded result UPDATE |
| Tampering with vendored XSD | Tampering | SHA-256 manifest test |
| Predictable IUD random part | Spoofing | `SecureRandom` (FindSecBugs `PREDICTABLE_RANDOM`) |
| Log/DB injection via error text | Tampering | Fixed error codes + controlled PT messages; never persist raw exception messages |
| Scheduler DoS (one bad item loops) | DoS | Attempt counted at claim; max 8 → ERRO; per-item `catch Throwable` |

## Sources

### Primary (HIGH confidence)
- DNRE eFatura XSD package 2024-05-27 as published in https://github.com/Kowts/efatura-cv-php (`resources/xsd/efatura/2024-05-27/`, fetched via raw.githubusercontent.com): all 22 XSD, "Read Me.txt", "XML Fields Map.txt", `2 InvoiceReceipt.xml`, `5 CreditNote.xml`; `LICENSE` (MIT), `NOTICE`; `src/Domain/Iud.php`
- https://github.com/kriolos/kriolos-efatura (`kriolos-efatura-datamodel/src/main/resources/schemas/efatura1/`, `LICENSE` Apache-2.0, `pom.xml`): 2021-12-19 copy used for the diff
- Local experiments (JDK 21.0.11, Maven 3.9.11, Boot 3.4.1 parent): JAXB generation (137 classes), FR/NC build + validation (exploded and `java -jar`), XXE probe, negative cases, SpotBugs 4.10.2.0 + FindSecBugs 1.14.0 with and without the package filter, xmllint cross-check
- `spring-boot-dependencies-3.4.1.pom` (JAXB versions); `spring-boot-autoconfigure-3.4.1.jar` metadata (`spring.task.scheduling.pool.size` default 1); `hibernate-core-6.6.4.Final.jar` (`@Check(name, constraints)`); repo1.maven.org `maven-metadata.xml` (jaxb-maven-plugin 4.0.16 latest)
- Repository code read directly: `DocumentoFiscal.java`, `DocumentoFiscalLinha.java`, `ComunicacaoFiscal.java`, `EstadoComunicacaoFiscal.java`, `AmbienteFiscal.java`, `MetodoPagamento.java`, `MotivoNotaCredito.java`, `CalculoFiscal.java`, `PagamentoFaturadoService.java`, `NotaCreditoService.java`, `ValidacaoEmissao.java`, `ValidacaoNotaCredito.java`, `TextoDocumentoFiscal.java`, `ConfiguracaoFiscalRequest.java`, `AlertasDiariosJob.java`, `SchedulingConfig.java`, `NotificacaoService.java`, `CategoriaNotificacao.java`, `UserRepository.java`, `ResolucaoPapeisService.java`, `DocumentoFiscalController(Test).java`, `MigracaoFiscal134IT.java`, `migrations/134-…sql`, `migrations/88-…sql`, `spotbugs-exclude.xml`, `pom.xml`, `application.yml`, web `use-faturacao.ts`, `notificacao-categoria.ts`, `types/notificacoes.ts`, `types/faturacao.ts`, `documentos-fiscais/[id]/page.tsx`, `scripts/verify-documentos-fiscais.mjs`

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md` §2, §5, §10, §11 (E1–E10), `SUMMARY.md` C4/C6/C9, `ARCHITECTURE.md` §5, `PITFALLS.md` P-07/08/09/17/27

### Tertiary (LOW confidence)
- Web search excerpts on Art.º 65 CIVA (Cabo Verde) and manual v7 IssueReasonCode numbering: [manual técnico v7](https://efatura.cv/assets/files/manual-tecnico-v7-216ba5a0643ea57e50cfbbf26b47e746.pdf) (not opened), [CIVA Cabo Verde (Lobo Vasques)](https://lobocarmona.com/storage/74/CIVA_Cabo-Verde-12.01.2022.pdf) (blocked), [Lei 21/VI/2003 (mf.gov.cv)](https://www.mf.gov.cv/documents/54571/591130/IVA.pdf) (blocked)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Versions verified in BOM/Central and the build executed.
- XSD mapping: HIGH for structure (executed and validated by two validators); LOW for semantic choices G6/G11–G13 (gate).
- Architecture (outbox): HIGH. It follows the repo's own job/lock patterns; SQL semantics are standard PostgreSQL; ITs will prove it.
- Pitfalls: HIGH. Most were found in this repo's code and tests.

**Research date:** 2026-10-05
**Valid until:** 2026-11-04 for the stack; the format holds until the primary source is reachable (then re-check the 22 hashes).
