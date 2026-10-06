---
status: passed
phase: 136-formato-efatura-e-adaptador-simulado
source: [136-16-PLAN.md Task 1, Task 2, Task 3]
started: 2026-10-06T17:58:00Z
updated: 2026-10-06T18:25:00Z
---

# Phase 136: Gate, live end-to-end verification and primary-source gate record

## Gate (Task 1)

I ran every command from a clean tree on 2026-10-06. Docker was available (`~/.docker-java.properties` api.version=1.44), so every Testcontainers IT ran against a real PostgreSQL 16.

| Gate | Result |
|------|--------|
| `mvn -Dmaven.compiler.release=21 verify` | BUILD SUCCESS. Surefire **1207** tests, 0 failures, 0 errors, 0 skipped. Failsafe **159** tests, 0 / 0 / 0. |
| `mvn -q -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check` | exit 0 |
| `mvn -q -Dmaven.compiler.release=21 -DskipTests package` | exit 0; `backend-0.0.1-SNAPSHOT.jar` contains the 22 vendored `xsd/efatura/*.xsd` |
| web `pnpm exec vitest run` | 14 files, **318** tests, all passed |
| web `pnpm exec tsc --noEmit` | clean |
| web `pnpm lint` | 0 errors (20 pre-existing warnings in unrelated files) |
| `pnpm verify:faturacao` / `pnpm verify:documentos-fiscais` | OK / OK (the latter includes the Phase 136 checks: neutral badge, card, reprocess `financeiro:edit`, banner, no authorisation wording) |
| `pnpm build` (BACKEND_API_ORIGIN, NEXT_PUBLIC_API_BASE_PATH inline) | success; `/financeiro/documentos-fiscais` (static) and `/financeiro/documentos-fiscais/[id]` (dynamic) listed |
| `git diff --quiet HEAD -- web/package-lock.json` | unchanged |

**Fiscal ITs (failsafe, executed, 0 skipped):**

| IT | Tests |
|----|-------|
| MigracaoFiscal133IT | 8 |
| MigracaoFiscal134IT | 6 |
| MigracaoFiscal135IT | 4 |
| MigracaoFiscal136IT | 8 |
| DocumentoFiscalRepositoryIT | 18 |
| PagamentoFaturadoServiceIT | 11 |
| PagamentoFaturadoConcorrenciaIT | 4 |
| GuardasDocumentoFiscalConcorrenciaIT | 11 |
| NotaCreditoServiceIT | 11 |
| NotaCreditoConcorrenciaIT | 3 |
| FilaComunicacaoFiscalIT | 13 |
| ReprocessarComunicacaoIT | 8 |
| FiscalOutboxJobIT | 6 |
| FiscalOutboxJobFalhasForcadasIT (extra, from 136-15) | 1 |

## Live end-to-end run (Task 2, checkpoint auto-verified)

### Stack and method

- **Database:** PostgreSQL 16 in Docker (`lexcv_pg`), schema created by Hibernate (`ddl-auto: update`) on first boot.
- **Backend:** the **packaged jar** (`java -jar target/backend-0.0.1-SNAPSHOT.jar`, run from `backend/`, so `.env` is imported). `SEED_ENABLED=true`, a random throwaway JWT secret, `EFATURA_MODE=SIMULADO`. Running from the jar also proves the vendored XSD resolves through the classpath inside the nested jar (research Pattern 3): every emission passed `EfaturaXmlValidator` there.
- **Web:** `pnpm dev` on :3000. Every browser and curl request went through `:3000/api/v1` (the Next rewrite).
- **Drivers:** Playwright with the preinstalled Chromium (`/tmp/claude-0/e2e136/step6.cjs`, `step8.cjs`), curl with login cookie jars, psql through `docker exec`, and xmllint 2.x against `backend/src/main/resources/xsd/efatura/EnvelopedSignature.xsd`.
- **Env files:** `backend/.env` and `web/.env.local` are git-ignored (`git check-ignore`: `backend/.gitignore:4:*.env`, `web/.gitignore:34:.env*`). Both were deleted at the end.

**Workaround 1, demo tenant (environment only, no product change):** the first-run wizard still 500s on a fresh DB (pre-existing `Set.of` bug, 133 deferred-items #1). As in 133-08 / 134-14 / 135-13, I booted once, then:
- inserted `system_settings(id=1, is_initialized=true)`;
- ran `truncate t_tenant, t_user cascade`;
- restarted, which seeded admin@lexcv.cv / Pa$$w0rd in "Gabinete Jurídico Demonstração".

**Workaround 2, second tenant (environment only):** tenant B "Escritório B (E2E)" and admin.b@lexcv.cv (global ADMIN role, the admin's password hash) were inserted with SQL, as in 135-13.

**Test users (product admin API):**
- **leitor.fin@lexcv.cv**, view-only: `POST /admin/users` with the TECNICO office role → 201. `/auth/me` shows financeiro = `[financeiro:view]`.
- **gestor.fat@lexcv.cv**, manage-only (no `financeiro:edit`): created with ADVOGADO → 201. It then holds `financeiro:view` + `financeiro:manage`.
  - First I added `financeiro:manage` to the ADVOGADO office role via `PUT /admin/rbac` (200).
  - The next restart then aborted with the **pre-existing** role-drift check (`VerificacaoDerivaPapeisService`, logged as `deferred-items.md` #1, not a Phase 136 defect).
  - Workaround 3 (environment only): I moved `financeiro:manage` to a direct `t_user_permission` row for that user. `/auth/me` = `[financeiro:manage, financeiro:view]`.

### Results

| Step | Req | Result | Evidence |
|------|-----|--------|----------|
| 1 | DFE-02, DFE-06 | PASS | `psql -v ON_ERROR_STOP=1 < backend/migrations/136-efatura-comunicacao.sql` on the live Hibernate-created DB, **twice**. Both runs exit 0, with only `NOTICE ... already exists, skipping` (`reprocessamentos` column, `idx_comunicacao_fiscal_estado_proxima`, `t_documento_fiscal_xml`). `pg_constraint` afterwards: `ck_comunicacao_fiscal_autorizado_producao CHECK (estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO')`. |
| 2 | DFE-03 | PASS | **`EFATURA_MODE=REAL java -jar …`:** the process exits with code **1**, and the log contains `EFATURA_MODE='REAL' não é suportado neste build: só SIMULADO existe (não há implementação real).`<br>**`EFATURA_MODE=` (empty):** exit code **1** with `EFATURA_MODE='' não é suportado neste build: só SIMULADO existe (não há implementação real).`<br>**Restart with SIMULADO:** `Started BackendApplication`. |
| 3 | DFE-02, DFE-04 | PASS | **Faturação:** `PUT /faturacao/configuracao` (NIF 212345678, regime NORMAL) → 200 `completa:true`; `POST /faturacao/ativar` → 200 `ativa:true`; `estado-emissao` = `{"ativa":true,"ambiente":"SIMULADO","taxaRetencaoSugerida":20.0000,"modoComunicacao":"SIMULADO"}`.<br>**Data:** cliente João Andrade NIF set to 512345679 (PUT 200); processo PROC-EF-136; honorário id 3, valorTotal 120 000.<br>**Payment:** `POST /pagamentos` 120 000, TRANSFERENCIA, 20% → **SIM-FR-2026/1** (`50b6de55-…`). POST latency **0.104 s**, so communication is not on the request path.<br>**Immediately after:** `comunicacao` = `{"estado":"PENDENTE","ambiente":"SIMULADO","iud":null,"tentativas":0,…}`.<br>**After ~25 s** (first job tick, initial delay 20 s): `ACEITE_SIMULADO`, tentativas **1**, IUD `CV3261006212345678999990200000000132498643528`, length **45**, starts with `CV3`. |
| 4 | DFE-01 | PASS | **`t_documento_fiscal_xml` for the FR:** repositorio_codigo **3**, led_codigo **99999**, versao_formato **2024-05-27**, iud equal to the comunicação IUD, xml_sha256 `22a9e76b8e657f3408757bcac6ede5da16a7dcb3e2ed8df049a381efafd98f8b`.<br>**Exported** (`encode(convert_to(xml,'UTF8'),'base64')` → `fr1.xml`): `sha256sum` = **22a9e76b…f98f8b**, identical.<br>**`xmllint --noout --schema …/EnvelopedSignature.xsd fr1.xml`** → **`fr1.xml validates`**.<br>**Content:** `Dfe Id=<IUD> DocumentTypeCode="2"`, `InvoiceReceipt` with LedCode 99999, Serie SIM-FR-2026, DocumentNumber 1, emitter 212345678, receiver 512345679, Line IVA 15 + IR 20, Totals Net 104347.83 / Tax 15652.17 / Withholding 20869.57 / Payable 99130.43, PaymentMeansCode 30, PaymentAmount 99130.43, Transmission (IssueMode 1, 999999999, LEXCVSIM/LexCV/3.0.0), RepositoryCode 3. |
| 5 | DFE-01 | PASS | **Partial NC** (20 000, CORRECAO_VALOR) on FR1 → 201 **SIM-NC-2026/1** (`5da8a622-…`). ACEITE_SIMULADO after ~9 s (next tick), tentativas 1, IUD `CV3261006212345678999990500000000187884240096` (45).<br>**XML row:** repo 3 / LED 99999 / 2024-05-27, sha `d1b888c0…0e66c321`. The `sha256sum` of the exported bytes is **identical**.<br>**`xmllint`** → **`nc1.xml validates`**.<br>**Content:** `DocumentTypeCode="5"`, `CreditNote`, `IssueReasonCode` **2**, `References/Reference/FiscalDocument` = **CV3261006212345678999990200000000132498643528** (the FR's IUD), and `Note` = "Nota de crédito: Correção de valor — SIM-FR-2026/1". The Note is controlled: built from the motivo label and the FR number, while the free text "Valor acordado revisto" is not included. `RepositoryCode` 3. |
| 6 | DFE-04, DFE-06 | PASS | **Playwright as admin, list** `/financeiro/documentos-fiscais`:<br>• column "Comunicação" present;<br>• both rows show **"Aceite (simulação)"**. The badge is outline/neutral (`border border-neutral-200 text-neutral-900`, no green).<br>• **Filter "Comunicação":** options Todas / Pendente / Aceite (simulação) / Rejeitado / Erro. Choosing Erro → `?estado=ERRO` and "Nenhum documento corresponde aos filtros. Altere ou limpe os filtros."<br>**Detail FR1 and NC1:**<br>• header badges `Fatura-Recibo`/`Nota de Crédito`, `Aceite (simulação)`, `Simulação — sem validade fiscal`;<br>• card "Comunicação fiscal" shows Estado Aceite (simulação) ("Aceite pelo serviço de simulação. Não foi comunicado à administração fiscal e não tem validade fiscal."), Ambiente **Teste (simulado)**, the IUD in `.font-mono`, **"Ambiente de teste — sem validade fiscal"**, Tentativas 1 and Última tentativa.<br>**Banner:** exactly 1 `role=status` "Modo simulado. Os documentos são comunicados a um serviço de simulação, não à administração fiscal (DNRE). …" on the list, both details and the honorário page `/financeiro/3`.<br>**Text scan:** the `main` text of all four screens contains none of "Autorizado", "Aprovado", "Validado pela DNRE", "Comunicado à DNRE". |
| 7 | DFE-07 | PASS | **Restart** with `--app.efatura.simulado.falhas-forcadas=true`; the log warns "falhas-forcadas está ligado …".<br>**New FR SIM-FR-2026/2** (20 000, DINHEIRO, `e89f5678-…`): the first attempt gives PENDENTE, tentativas 1, `FALHA_SIMULADA`, next attempt +30 s. The XML row is stored (IUD `CV3261006212345678999990200000000225338505830`).<br>**psql:** `tentativas = 7, proxima_tentativa_em = now()`. After ~27 s: **ERRO**, tentativas 8, `ultimo_erro_codigo` **FALHA_SIMULADA**, `ultimo_erro` "Falha simulada do serviço de comunicação.", `proxima_tentativa_em` null.<br>**`GET /notificacoes`:**<br>• admin: **1** `COMUNICACAO_FISCAL_FALHOU`, titulo **"Falha na comunicação do documento SIM-FR-2026/2"**, mensagem "A comunicação do documento SIM-FR-2026/2 falhou após várias tentativas. Abra o documento e use \"Reprocessar comunicação\".", linkUrl `/financeiro/documentos-fiscais/e89f5678-…`;<br>• gestor.fat (manage holder): also 1;<br>• leitor.fin (view-only): **0**.<br>**Mute:** `PUT /notificacoes/preferencias/COMUNICACAO_FISCAL_FALHOU` → **400** "categoria não silenciável: COMUNICACAO_FISCAL_FALHOU" (refused). |
| 8 | DFE-05 | PASS | **Playwright as admin on FR2 (ERRO):**<br>• the card shows "Erro", "Última falha Falha simulada do serviço de comunicação." and an **outline** "Reprocessar comunicação" trigger (`border border-neutral-200 bg-white`);<br>• the dialog shows title "Reprocessar comunicação", the exact UI-SPEC description, Documento SIM-FR-2026/2, Estado atual Erro, Tentativas 8, "O contador de tentativas volta a zero.", "Fechar sem reprocessar" + "Reprocessar comunicação", and sr-only "Fechar reprocessamento da comunicação".<br>**Confirm:** toast **"Comunicação reposta como pendente."**. DB right after: `PENDENTE|0|1` (estado, tentativas, reprocessamentos). The card then shows Pendente, Tentativas 0 and the same IUD.<br>**Restart without the lever:** after ~21 s **ACEITE_SIMULADO**, tentativas 1, IUD **CV3261006212345678999990200000000225338505830**, the **same** as before the reprocess. `t_documento_fiscal_xml` still has **1** row for FR2 (reused, not regenerated), and `fr2.xml validates`. |
| 9 | DFE-05 | PASS | **curl, all `POST /documentos-fiscais/{id}/comunicacao/reprocessar`:**<br>• FR1 in ACEITE_SIMULADO → **409** `COMUNICACAO_ESTADO_INVALIDO` "Só é possível reprocessar comunicações em erro ou rejeitadas.";<br>• malformed id `nao-e-um-uuid` → **404** `DOCUMENTO_FISCAL_NAO_ENCONTRADO`, the same answer as a random UUID;<br>• leitor.fin (view-only) → **403** "Acesso negado.";<br>• gestor.fat (manage-only, no exact `financeiro:edit`) → **403**;<br>• tenant B admin on tenant A's FR1 and FR2 → **404** `DOCUMENTO_FISCAL_NAO_ENCONTRADO`, and GET FR1 → 404.<br>**UI (Playwright):** leitor.fin and gestor.fat open FR2 in ERRO and see **0** "Reprocessar comunicação" buttons.<br>**After the refusals:** reprocessamentos is unchanged (FR1 0, NC1 0, FR2 1). |
| 10 | DFE-06 | PASS | `UPDATE t_comunicacao_fiscal SET estado='AUTORIZADO' WHERE documento_fiscal_id=<FR1> AND ambiente='SIMULADO'` → **`ERROR: 23514: new row for relation "t_comunicacao_fiscal" violates check constraint "ck_comunicacao_fiscal_autorizado_producao"`**. The row remains `ACEITE_SIMULADO\|SIMULADO`. |
| 11 | — | PASS (access result recorded: still blocked) | `curl -sS -o /dev/null -w '%{http_code}' https://efatura.cv/docs/xsd` → `000`, `curl: (56) CONNECT tunnel failed, response 403`. Same for `https://efatura.cv` and `https://pe.efatura.cv`: the egress proxy refuses the host. The WebFetch tool is not available to this executor, so I did not run that retry. The primary source is still unreachable, so the G1–G15 gate stays as researched. |
| 12 | — | PASS | **Stopped:** the backend jar (PID file) and the Next dev server. `ps` shows no `java -jar` or `next` process.<br>**Removed:** the `lexcv_pg` container (`docker ps -a` is empty).<br>**Deleted:** `backend/.env`, `web/.env.local` and the cookie jars.<br>`git status --porcelain` is clean. |

### Defects found

None in Phase 136 scope. No product code changed during the run.

### Notes and observations (not defects of this phase)

- **Pre-existing role-drift boot abort** (`deferred-items.md` #1): after an office role that has users is customised through `/admin/rbac`, the next backend start aborts in `MigracaoPapeisEscritorioService`. This comes from Phases 126/127 and is unrelated to eFatura. I used the environment workaround 3 above.
- **Pre-existing, unchanged:** the setup wizard / tenant provisioning `Set.of` 500 (133 deferred-items #1).
- **Times:** the UI shows times in Atlantic/Cape_Verde (UTC−1), e.g. "Última tentativa 17:05" for 18:05 UTC, consistent with the XML `IssueTime`.
- **Notification episode:** `entidadeId` = `<documentoId>:0`. This is the per-episode dedup from research Q2. The link is `linkUrl`, not part of `entidadeId`.

## Portão das fontes primárias (G1–G15) — Task 3, decision pending

**Step 11 retry result (2026-10-06):** `https://efatura.cv/docs/xsd`, `https://efatura.cv` and `https://pe.efatura.cv` all fail with `CONNECT tunnel failed, response 403` (egress proxy). The WebFetch tool was not available to the executor. **No item could be checked against the primary source.** The status of every row below is the same as in research. The format rests on the Kowts copy of the 2024-05-27 package: 22 XSDs, SHA-256 pinned in `XsdEfaturaIntegridadeTest`, consistent with the older kriolos copy and the package changelog.

**Columns:**
- "Fixed by package" = what the vendored XSD/package settles.
- "Assumed" = what remains unverified.
- "v3.0 handling / where" = the exact file and constant.
- "Live 136-16" = what the live run showed.

| # | Topic | Fixed by package | Assumed | v3.0 handling / where | If wrong | Live 136-16 | Recommendation |
|---|-------|------------------|---------|------------------------|----------|-------------|----------------|
| G1 | Package currency | 2024-05-27 content (HIGH) | Still the version in force, and the manual is v11 | `MapeamentoEfatura.VERSAO_FORMATO = "2024-05-27"`, stored per row in `t_documento_fiscal_xml.versao_formato`. Provenance + 22 hashes in `backend/src/main/resources/xsd/efatura/README.md`; integrity in `XsdEfaturaIntegridadeTest` | Rebuild the XML from the immutable snapshot (cheap: the XML is derived) | versao_formato 2024-05-27 on all 3 rows | Accept; re-check the 22 hashes in the real-connection milestone (EFAT) |
| G2 | IUD layout | Regex `CV(\d)(\d{2})(MM)(DD)([1-9]\d{8})\d{27}`, length 45 (HIGH) | Split of the trailing 27 digits: LED(5) + tipo(2) + número(9) + aleatório(10) + DV(1) | `IudGerador` (`TAMANHO = 45`, `gerar`); type digits from `MapeamentoEfatura.codigoTipoIud` | Real platform rejects the IUD; no effect in SIMULADO | 3 IUDs, 45 chars, `CV3…`, all accepted by the XSD `Id` pattern | Accept; confirm with official test vectors in EFAT |
| G3 | Luhn check digit | Sample IUD DV = Luhn over 42 digits | No official test vectors | `IudGerador.luhn(payload)` (Kowts parity) | Same as G2 | — | Accept; same as G2 |
| G4 | LED in SIMULADO | `stLedCode` 1–99999 | Whether any value is reserved for tests | `MapeamentoEfatura.LED_SIMULADO = 99999` via `ledPara(AmbienteFiscal)` (SIMULADO only) | None in SIMULADO | led_codigo 99999 on every row | Accept |
| G5 | PaymentMeansCode | UN/ECE D19B list; 10, 20, 30, 48, ZZZ present (HIGH) | Semantic fit (`CARTAO`→48 vs 54/55); whether DNRE restricts the list | `MetodoPagamento` enum codes (`CARTAO("…","48")`, `OUTRO("…","ZZZ")`, …), snapshotted in `meio_pagamento_codigo` (Phase 134); written by `DfeXmlBuilder` (`setPaymentMeansCode`) | Semantics only; new snapshot value for future documents | TRANSFERENCIA → 30 in FR1 XML | Accept; contabilista may confirm CARTAO |
| G6 | IssueReasonCode (NC) | NCE accepts 2, 3, 6, 7, 8, 9, IN, DRP | Which Art.º 65 CIVA paragraph applies to each `MotivoNotaCredito` (LOW) | `MapeamentoEfatura.issueReasonCode(MotivoNotaCredito)`: all four motivos → `"2"` | NC rejected or misclassified in real mode | NC1 XML `IssueReasonCode` 2 | **Contabilista to confirm** (candidate for `adjust-items`) |
| G7 | References on NC | XSD optional; Fields Map required on NCE | — | `DfeXmlBuilder` `nc.setReferences(referencias(doc.iudOrigem()))`: `Reference/FiscalDocument` = FR IUD, no `IsOldDocument` | — | NC1 references FR1's IUD exactly | Accept |
| G8 | Line Tax on NC | Fields Map: Line `Tax` optional on NCE | Whether sending the FR's IVA/IR on the NC line is expected | `DfeXmlBuilder` line taxes: the same `Tax` elements as the FR snapshot rates | Possibly rejected; can be dropped later | NC1 validates with IVA/IR line taxes | Accept |
| G9 | Transmission block | Structure fixed (IssueMode, TransmitterTaxId, Software Code/Name/Version) | Real `Software.Code` (homologation) and transmitter NIF | `application.yml` `app.efatura.transmissao.*` (`EFATURA_TRANSMISSOR_NIF` 999999999, `EFATURA_SOFTWARE_CODIGO` LEXCVSIM, `EFATURA_SOFTWARE_NOME` LexCV, `EFATURA_SOFTWARE_VERSAO` 3.0.0), validated by `TransmissaoEfatura`; `MapeamentoEfatura.ISSUE_MODE_ONLINE = 1` | Real values required in the real-connection milestone | Transmission block present with the synthetic values | Accept for SIMULADO; real values in EFAT |
| G10 | Totals tolerance | `TaxTotal` validated within floor/ceil; ≤5 decimals | Rounding tolerance for document totals | `TaxTotal` per Tax not sent (comment block in `MapeamentoEfatura`); totals from snapshot, 2 decimals HALF_UP (`DfeXmlBuilder` totals) | Rejection on rounding | Totals equal to the snapshot | Accept |
| G11 | PaymentAmount | `stDecimal5MinExc0` | Gross (`total_documento`) or net (`valor_liquido`) | `DfeXmlBuilder` `setPaymentAmount(decimal(doc.valorLiquido()))` (documented in `MapeamentoEfatura`) | Semantics only | FR1 PaymentAmount 99130.43 = PayableAmount | Contabilista may confirm (candidate for `adjust-items`) |
| G12 | Party.Contacts | XSD optional; Fields Map required except TVE | Whether the platform rejects its absence | Omitted (comment block in `MapeamentoEfatura`; no snapshot columns, no fabricated data) | Rejection in real mode; needs snapshot columns | Omitted; validates | Accept for SIMULADO; add snapshot columns in EFAT if required |
| G13 | Quantity@UnitCode | Required, `[aA-zZ0-9]{1,10}` | Expected code for services | `MapeamentoEfatura.UNIT_CODE = "EA"` (+ `EMITTER_ID_FR`/`EMITTER_ID_NC`) | Cosmetic | `UnitCode="EA"` | Accept |
| G14 | Licence/redistribution of the DNRE XSD | — | DNRE terms | Vendored for local validation; provenance in `backend/src/main/resources/xsd/efatura/README.md` | Remove the files and fetch at build time from the official source | Jar contains the 22 XSDs; validation works from the nested jar | Accept (private repo); revisit before any public distribution |
| G15 | IsSpecimen | Optional, only `"true"` | — | Not sent (comment block in `MapeamentoEfatura`); `MapeamentoEfatura.REPOSITORIO_TESTE = 3` (`RepositoryCode` 3) already marks the test | — | RepositoryCode 3 on every XML | Accept, or send `IsSpecimen=true` as an extra layer (candidate for `adjust-items`) |

### Decision (to be taken by the user)

Close Phase 136 with the primary-source gate (G1–G15) still pending, or keep it open?

| Option | Pros | Cons |
|--------|------|------|
| **close-pending** (recommended) | Phase 137 can start. All risk is confined to SIMULADO. The gate items are tracked here and in STATE.md for the real-connection milestone (EFAT-01..06) | Format correctness against the current official package remains an assumption (A1–A13) |
| keep-open | The format is verified before anything depends on it | Blocks 137 on an external access problem, with no fiscal benefit in SIMULADO |
| adjust-items | Applies the contabilista's or user's answers now (e.g. G6 IssueReasonCode per motivo, G11 PaymentAmount, G15 IsSpecimen) | Needs a small follow-up plan touching `MapeamentoEfatura`/`DfeXmlBuilder` and their tests |

**User decision (2026-10-06):** close-pending — fechar a fase com o portão G1–G15 pendente; os itens ficam registados para confirmação contra as fontes primárias (efatura.cv) e o contabilista (G6, G11, G15) no marco de ligação real (EFAT-01..06).

## Post-review items (from 136-VERIFICATION.md, status human_needed)

The live UAT (12/12) ran before the 13 code-review fixes. Re-check from the packaged jar:

1. Live smoke: an FR and an NC reach "Aceite (simulação)" with a 45-character CV3 IUD; the stored XML passes xmllint.
2. WR-04: a firma over 150 characters returns 422 `FIRMA_EXCEDE_150`; the REJEITADO copy no longer promises that reprocessing fixes it.
3. WR-03 / WR-05: an NC on a rejected FR, or a document with a fixed data defect, goes straight to REJEITADO with a clear message.
4. WR-07 / IN-06: the "Modo simulado" banner stays visible while the page loads or errors; focus lands on the card title after a reprocess.

Open product decisions (review IN-02 / IN-03): a lost notification is never retried; a REJEITADO document sends no notification.
