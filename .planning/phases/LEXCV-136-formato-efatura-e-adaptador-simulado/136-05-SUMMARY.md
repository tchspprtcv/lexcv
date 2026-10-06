---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 05
subsystem: backend-fiscal-format
tags: [fiscal, efatura, xsd, xxe, iud, jaxb, tdd]
requires: ["136-01"]
provides:
  - "ClasspathXsdResolver: LSResourceResolver serving only the 22 vendored XSD (LISTA_BRANCA), PREFIXO classpath:xsd/efatura/"
  - "DfeValidador (@Component, final): validar(byte[]) -> ResultadoValidacao; Schema built once at construction (fail-fast IllegalStateException)"
  - "ResultadoValidacao record (valido, codigo, linha, coluna); codes XSD_INVALIDO, XML_PROIBIDO, XML_ILEGIVEL; factories sucesso() / invalido(...)"
  - "DfeMarshaller (@Component, final): marshal(Dfe) -> UTF-8 bytes, declaration without standalone, no formatting"
  - "IudGerador (@Component, final): gerar(repositorio, dataEmissao, nif, led, tipo, numero) -> 45-char IUD; static luhn(payload); TAMANHO = 45"
affects: [136-09, 136-10, 136-13]
tech-stack:
  added: []
  patterns:
    - "Whitelist resolver: URI.resolve over a synthetic hierarchical base (classpath:/xsd/efatura/), then Set membership; no filesystem path API"
    - "Hardened JAXP: FEATURE_SECURE_PROCESSING + ACCESS_EXTERNAL_DTD/SCHEMA '' on SchemaFactory and Validator, SAX parser with disallow-doctype-decl and external entities off"
key-files:
  created:
    - backend/src/main/java/com/lexcv/fiscal/efatura/ClasspathXsdResolver.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/DfeValidador.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/ResultadoValidacao.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/DfeMarshaller.java
    - backend/src/main/java/com/lexcv/fiscal/efatura/IudGerador.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/ClasspathXsdResolverTest.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/DfeValidadorTest.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/DfeMarshallerTest.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/IudGeradorTest.java
  modified: []
decisions:
  - "The valid-result factory is ResultadoValidacao.sucesso(), not valido(): a static valido() collides with the record accessor valido()"
  - "XML_PROIBIDO is classified from the SAXParseException text containing DOCTYPE: the parser refuses before any handler sees the DTD, so no structured signal exists. Only the code depends on it; the refusal itself is the feature"
  - "The entry schema must also be a whitelisted resource read through the resolver's own loader; anything else fails construction"
  - "DfeMarshaller writes its own <?xml version=\"1.0\" encoding=\"UTF-8\"?> and marshals as a fragment, so the stored bytes carry no standalone attribute"
  - "IudGerador formats with Locale.ROOT (a default locale with non-ASCII digits would break the layout); public no-arg constructor (new SecureRandom()) for Spring, package-private one for tests"
  - "DfeValidador, DfeMarshaller and IudGerador are final classes (SpotBugs CT_CONSTRUCTOR_THROW on the fail-fast constructors)"
metrics:
  duration: "~35 min"
  completed: 2026-10-06
  tasks: 2
  files: 9
---

# Phase 136 Plan 05: Hardened DFE validator, whitelist resolver, marshaller and IUD generator Summary

The eFatura format engine now has its safety core.

- **Validator:** the hardened validator loads the official 2024-05-27 schema once, only from the classpath, and only through a fixed list of the 22 vendored XSD files. It accepts both official samples. It rejects every listed negative, including XXE, and reports only a fixed code with a line and column.
- **Marshaller:** turns the JAXB model into exact UTF-8 bytes. A round trip of the official FRE sample still validates.
- **IUD generator:** produces 45-character IUDs with the official layout and a Luhn check digit that reproduces the official sample (DV 4).

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | ClasspathXsdResolver + DfeValidador + ResultadoValidacao | 810065d (RED), 458a7b8 (GREEN) |
| 2 | DfeMarshaller + IudGerador | 43f62e9 (RED), 76faf29 (GREEN) |

## Verification

- **ClasspathXsdResolverTest:** 13 tests, green.
  - Whitelisted files resolve with their exact bytes, including relative includes from `common/`.
  - These resolve to null: `../bindings/efatura.xjb`, `../../application.yml`, `file:///etc/passwd`, the W3C `http:` URL, `README.md`, `common/../../x.xsd`, `common/../README.md`, `//evil.example/...`, `jar:file:...`, a null systemId, and a non-classpath base.
  - The whitelist holds exactly 22 entries.
- **DfeValidadorTest:** 11 tests, green.
  - The FRE and NCE samples are valid.
  - These are rejected as `XSD_INVALIDO` with a line > 0 and without echoing the injected value: PaymentMeansCode 999, RepositoryCode 4, a double space in a Name, and an amount with 6 decimals.
  - A Note of "Curta" is rejected.
  - A DOCTYPE with an external `file:///etc/hostname` entity is rejected as `XML_PROIBIDO`, and so is a bare DOCTYPE.
  - Empty, null and malformed input never throw.
  - A missing entry XSD, and an entry that is not on the whitelist (the `.xjb`), both throw IllegalStateException.
- **DfeMarshallerTest:** 3 tests, green.
  - Unmarshal then marshal of the FRE sample still validates.
  - The output starts with the UTF-8 declaration, has no `standalone` and no indentation.
  - Each call returns a new array.
- **IudGeradorTest:** 6 tests, green.
  - The official vector gives DV 4.
  - `gerar(3, 2026-06-15, 512345679, 99999, 2, 1)` produces a 45-character IUD starting with `CV3260615512345679` and containing `9999902000000001`. It matches both `stDfeId` and the Kowts regex.
  - Tipo 5 produces `05`.
  - The random part is zero-padded to 10 digits and differs between calls.
  - The production constructor works.
  - All argument refusals throw IllegalArgumentException.
- **SpotBugs:** `mvn -DskipTests compile spotbugs:check` is clean, with no new exclusion.
- **Full backend unit suite:** `mvn -Dmaven.compiler.release=21 test` ran 1070 tests with 0 failures, 0 errors and 0 skipped.
- **Acceptance greps:**
  - `disallow-doctype-decl` appears once.
  - `ACCESS_EXTERNAL_SCHEMA` appears 3 times.
  - `Paths.get` and `java.nio.file` appear 0 times in the resolver.
  - `SecureRandom` appears 5 times.
  - `new Random(` and `Math.random` appear 0 times.
  - `JAXB_FORMATTED_OUTPUT` appears 0 times.
  - The official vector string is present in IudGeradorTest.

## Deviations from Plan

**1. [Rule 3 - Blocking] Valid-result factory named `sucesso()`**
- **Found during:** Task 1 (compile error).
- **Issue:** A record component `valido` and a static `valido()` factory cannot coexist ("invalid accessor method").
- **Fix:** The factory is `ResultadoValidacao.sucesso()`. `invalido(codigo, linha, coluna)` is unchanged.
- **Commit:** 458a7b8.

**2. [Rule 1 - SAST] Final classes for the fail-fast constructors**
- **Found during:** Task 1 (SpotBugs CT_CONSTRUCTOR_THROW).
- **Fix:** Made `DfeValidador` final. `DfeMarshaller` and `IudGerador` are final for the same reason. No exclusion was added.
- **Commit:** 458a7b8, 76faf29.

**3. [Rule 1 - Correctness] Locale-independent IUD formatting**
- **Found during:** Task 2.
- **Fix:** Every `String.format` in `IudGerador` uses `Locale.ROOT`.
- **Commit:** 76faf29.

**4. [Rule 2 - Hardening] Extra resolver and validator cases**
- These cases were added on top of the plan's list: a protocol-relative authority, a `jar:` scheme, `common/../README.md`, a non-classpath base, and a bare DOCTYPE.
- The entry schema is also checked against the whitelist.

Javadoc was reworded so that the acceptance greps for `disallow-doctype-decl` (== 1) and `java.nio.file` (== 0) stay meaningful.

## TDD Gate Compliance

- **Task 1:** RED 810065d failed to compile because the classes were missing. GREEN 458a7b8.
- **Task 2:** RED 43f62e9 failed to compile. GREEN 76faf29.
- No refactor commits were needed.

## Notes for downstream plans

- **136-09 and 136-13:** validate with `dfeValidador.validar(bytes)` and check `.valido()`. On failure, persist only `codigo()`, never parser text.
- Use `IudGerador.gerar(3, documento.getDataEmissao(), nif, 99999, tipo, numero)` with repository 3 (Teste) and the synthetic LED 99999 (research).

## Threat Flags

None. T-136-16 through T-136-20 are mitigated as planned:
- T-136-16: XXE refused.
- T-136-17: whitelist resolver.
- T-136-18: SecureRandom.
- T-136-19: code plus position only.
- T-136-20: fail-fast constructor.

## Self-Check: PASSED

- FOUND: all 5 main classes and 4 test classes under backend/src/{main,test}/java/com/lexcv/fiscal/efatura/
- FOUND: commits 810065d, 458a7b8, 43f62e9, 76faf29
