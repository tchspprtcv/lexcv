---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 09
subsystem: backend-fiscal-format
tags: [fiscal, efatura, jaxb, xsd, mapping, tdd]
requires: ["136-05"]
provides:
  - "MapeamentoEfatura: single constant table (VERSAO_FORMATO, VERSAO_DFE, PAIS, LED_SIMULADO, REPOSITORIO_TESTE, ISSUE_MODE_ONLINE, UNIT_CODE, EMITTER_ID_FR/NC, MAX_NOME, MAX_NUMERO, text limits) + codigoTipo, codigoTipoIud, repositorioPara, ledPara, issueReasonCode, emitterIdentification, notaNotaCredito"
  - "DocumentoComunicavel record (+ nested Linha) with static de(DocumentoFiscal, DocumentoFiscalLinha, iudOrigemOuNulo, numeroFormatadoOrigemOuNulo)"
  - "TransmissaoEfatura record(nifTransmissor, softwareCodigo, softwareNome, softwareVersao), validated in the compact constructor (IllegalArgumentException)"
  - "RecusaFormatoEfatura(Codigo) with codigo()/mensagem(); codes FIRMA_EXCEDE_150, NUMERO_FORA_DO_LIMITE, TEXTO_INVALIDO, ORIGEM_SEM_IUD"
  - "DfeXmlBuilder (@Component, final): construir(DocumentoComunicavel, String iud, TransmissaoEfatura) -> Dfe"
affects: [136-12, 136-13, 136-16]
tech-stack:
  added: []
  patterns:
    - "Exhaustive switches without default over AmbienteFiscal/TipoDocumentoFiscal/MotivoNotaCredito/RegimeIva: a new constant is a compile error in the format table"
    - "Builder tests marshal + validate every output against the vendored XSD, then assert values by namespace-aware XPath"
key-files:
  created:
    - backend/src/main/java/com/lexcv/fiscal/efatura/MapeamentoEfatura.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/DocumentoComunicavel.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/TransmissaoEfatura.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/RecusaFormatoEfatura.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/DfeXmlBuilder.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/MapeamentoEfaturaTest.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/DfeXmlBuilderTest.java
  modified:
    - backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalRequest.java
    - backend/src/test/java/com/lexcv/dtos/ConfiguracaoFiscalRequestValidationTest.java
decisions:
  - "DocumentoComunicavel.de also refuses an FR that is given an origin (IllegalArgumentException), on top of the plan's NC-without-origin refusal; an NC additionally needs a motivo"
  - "RecusaFormatoEfatura takes a nested Codigo enum that owns the fixed message; codigo() returns the enum name (the string 136-13 stores)"
  - "The NC Note template lives in MapeamentoEfatura.notaNotaCredito (format choice, one table), not in the builder"
  - "TEXTO_INVALIDO covers text the builder cannot fix: Name < 3 or > 150 (adquirente), empty or > 100 morada/localidade, empty or > 300 description, ISENTO without motivo, IR withholding without a rate, Note outside 10-500"
  - "ChargeTotalAmount/DiscountTotalAmount are emitted as 0 (BigDecimal.ZERO); every decimal goes through stripTrailingZeros with scale >= 0, so no exponent"
metrics:
  duration: "~30 min"
  completed: 2026-10-06
  tasks: 2
  files: 9
---

# Phase 136 Plan 09: Snapshot to eFatura DFE mapping Summary

The fiscal snapshot can now be turned into official eFatura XML. A Fatura-Recibo (FR) or a Nota de Crédito (NC) becomes a JAXB `Dfe` that the vendored 2024-05-27 XSD accepts.

- **One table for format choices:** every choice the XSD does not settle lives in `MapeamentoEfatura`. This covers the tipo codes, `IssueReasonCode` "2" for every motivo, LED 99999, `UnitCode` "EA", `EmitterIdentification`, RepositoryCode 3 for SIMULADO and `IssueMode` 1. Each constant cites the gate item it answers.
- **Pure builder:** `DfeXmlBuilder` reads only that table and an immutable plain-value projection of the snapshot (`DocumentoComunicavel`). It normalises whitespace in every text value, and it refuses what it cannot fix with a fixed-code `RecusaFormatoEfatura`.
- **Firma limit:** new configuration edits now cap the firma at 150 characters.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | Contracts: MapeamentoEfatura, DocumentoComunicavel, TransmissaoEfatura, RecusaFormatoEfatura, firma <= 150 | e91bc1c (RED), 0fe3fbe (GREEN) |
| 2 | DfeXmlBuilder (FR + NC), every output marshalled and XSD-validated | 0db2c7c (RED), b4f159b (GREEN) |

## Verification

- **MapeamentoEfaturaTest:** 14 tests, green.
  - Tipo, repository and LED codes are correct, and so are all the constants.
  - `IssueReasonCode` "2" is checked inside the `stIssueReasonCode` enumeration of the vendored Types XSD.
  - All five `MetodoPagamento` codes (10, 30, 20, 48, ZZZ) appear in `UNECE_PaymentMeansCode_D19B.xsd`. This confirms G5.
  - `de(FR)` carries every field. The hour is 12:34:56Z converted to 11:34:56 in Cape Verde, truncated to seconds.
  - `de(NC)` carries the origin. An NC without an IUD is refused, and so is an FR given an origin.
  - Reflection over `DocumentoComunicavel` and `Linha` finds no `@Entity` component type.
  - `TransmissaoEfatura` accepts the valid values and refuses each invalid one.
  - Every `RecusaFormatoEfatura` code has a fixed message and no cause.
- **ConfiguracaoFiscalRequestValidationTest:** a 151-character firma is refused with "A firma não pode ter mais de 150 caracteres.", and a new case shows 150 passes. `ConfiguracaoFiscalServiceTest` and `FaturacaoControllerTest` are green.
- **DfeXmlBuilderTest:** 13 tests, green. Every positive case is marshalled with `DfeMarshaller` and asserts `DfeValidador.validar(...).valido()`. The cases are:
  - FR NORMAL with IR at 20%, checked element by element: Id, Version, type 2, RepositoryCode 3, no IsSpecimen, LED 99999, `SIM-FR-2026`, IssueTime `11:34:56` with no timezone, parties with CV and no Contacts, two Tax (IVA 15, IR 20), Totals, Payment "10" with amount = `valor_liquido`, and the Transmission block.
  - FR without IR.
  - FR ISENTO, with `NA` + motivo "1" and TaxTotal 0.
  - NC partial: type 5, `IssueReasonCode` 2, `References/FiscalDocument` = FR IUD, Note "Nota de crédito: Correção de valor — SIM-FR-2026/1", no Payments.
  - NC total with IR.
  - Whitespace normalisation: "Ana   Lopes\tSilva" becomes "Ana Lopes Silva", and the firma and description are normalised the same way.
  - Decimals: `15`, not `15.0000`, and no `E+`.
  - Refusals: `FIRMA_EXCEDE_150` at 151 characters, while 150 after trimming passes. `NUMERO_FORA_DO_LIMITE` at 1 000 000 000. `TEXTO_INVALIDO` for a blank name. `ORIGEM_SEM_IUD`.
  - A distinctive `motivo_texto` on the entity never appears in the XML.
- **Also run:** DfeValidador and IudGerador tests are green, and `mvn -DskipTests compile spotbugs:check` is clean with no new exclusion.
- **Acceptance greps:**
  - `LED_SIMULADO = 99999` appears once.
  - `max = 150` appears once.
  - `motivoTexto|getMotivoTexto` in the builder appears 0 times.
  - `"<|StringBuilder` in the builder appears 0 times.

## Deviations from Plan

**1. [Rule 2 - Correctness] Stricter DocumentoComunicavel.de**
- **Change:** besides the planned refusal of an NC without an origin IUD, `de` refuses a blank IUD, an NC without a motivo, and an FR given an origin. All three throw IllegalArgumentException.
- **Why:** the snapshot can then never present inconsistent origin data to the builder.
- **Commit:** 0fe3fbe.

**2. [Rule 2 - Correctness] Builder refuses more unfixable text**
- **Change:** besides the planned codes, `TEXTO_INVALIDO` is raised for text the builder cannot normalise into the XSD limits (listed in the decisions above).
- **Why:** these cases now fail before marshalling with a clear code. Otherwise they would surface only as a generic `XSD_INVALIDO`.
- **Commit:** b4f159b.

## TDD Gate Compliance

- **Task 1:** RED e91bc1c failed to compile because the classes were missing. GREEN 0fe3fbe.
- **Task 2:** RED 0db2c7c failed to compile because `DfeXmlBuilder` was missing. GREEN b4f159b.

## Notes for downstream plans

- **136-12:** build `TransmissaoEfatura` from `app.efatura.transmissao.*`. Its compact constructor throws IllegalArgumentException with a fixed message; convert it to IllegalStateException.
- **136-13:** check `snapshot.iudOrigem()` before calling `DocumentoComunicavel.de`. An NC without an origin IUD throws IllegalArgumentException there, so map it to `ErroTransitorio("ORIGEM_SEM_IUD")` before the call.
- **136-13:** catch `RecusaFormatoEfatura` and store `codigo()` / `mensagem()`.
- **136-13:** IUD = `iudGerador.gerar(MapeamentoEfatura.repositorioPara(a), data, emitenteNif, MapeamentoEfatura.ledPara(a), MapeamentoEfatura.codigoTipoIud(tipo), numero)`.

## Threat Flags

None.
- T-136-32: the Note is controlled text and `motivo_texto` is not part of the projection, with a test asserting its absence.
- T-136-33: JAXB only, normalisation, and XSD validation of every output in the tests.
- T-136-34: exhaustive switches with only SIMULADO, giving 3 / 99999.
- T-136-35: accepted, isolated in `MapeamentoEfatura` with gate references.

## Self-Check: PASSED

- FOUND: all 7 created and 2 modified files listed in key-files
- FOUND: commits e91bc1c, 0fe3fbe, 0db2c7c, b4f159b
