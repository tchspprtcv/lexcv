---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 01
subsystem: backend-fiscal-format
tags: [fiscal, efatura, xsd, jaxb, spotbugs, supply-chain, tdd]
requires: []
provides:
  - "22 eFatura 2024-05-27 XSD on the classpath at xsd/efatura/** (byte-pinned)"
  - "4 package fixtures at test classpath efatura/2024-05-27/ (Read Me, XML Fields Map, FRE and NCE samples)"
  - "Generated JAXB model: com.lexcv.fiscal.efatura.xsd (Dfe, CtDfe, InvoiceReceipt, CreditNote, ...)"
  - "XsdEfaturaIntegridadeTest: SHA-256 pin of the 22 + 4 files"
affects: [136-05, 136-09, 136-10, 136-13, 136-16]
tech-stack:
  added:
    - "jakarta.xml.bind:jakarta.xml.bind-api 4.0.2 (compile, Spring Boot BOM)"
    - "org.glassfish.jaxb:jaxb-runtime 4.0.5 (compile, Spring Boot BOM)"
    - "org.jvnet.jaxb:jaxb-maven-plugin 4.0.16 (build only)"
  patterns:
    - "Vendored third-party schema pinned by a SHA-256 manifest test plus .gitattributes -text"
    - "Package binding via .xjb for one namespace only; imported namespaces keep JAXB-derived packages"
key-files:
  created:
    - .gitattributes
    - backend/src/main/resources/xsd/efatura/README.md
    - backend/src/main/resources/xsd/efatura/EnvelopedSignature.xsd
    - backend/src/main/resources/xsd/efatura/InternallyDetachedSignature.xsd
    - backend/src/main/resources/xsd/efatura/common/ (20 XSD)
    - backend/src/main/resources/xsd/bindings/efatura.xjb
    - backend/src/test/resources/efatura/2024-05-27/ (4 fixtures)
    - backend/src/test/java/com/lexcv/fiscal/efatura/XsdEfaturaIntegridadeTest.java
  modified:
    - backend/pom.xml
    - backend/spotbugs-exclude.xml
decisions:
  - "The 26 files were copied with cp from the scratchpad Kowts copy only after sha256sum -c passed on all 26 lines of the research manifest; nothing was re-saved through an editor"
  - "The integrity test also asserts the exact set of .xsd under xsd/efatura (no extra schema can slip in) and that README.md exists"
  - "The pom comment avoids the literal word generatePackage so the acceptance grep (== 0) stays meaningful"
metrics:
  duration: "~25 min"
  completed: 2026-10-05
  tasks: 2
  files: 33
---

# Phase 136 Plan 01: Vendored eFatura XSD, SHA-256 pin and JAXB model Summary

The eFatura DFE package (version 2024-05-27, 22 XSD from the Kowts public copy) is now part of the backend. Each file is byte-identical to the research manifest. A test pins all 22 schemas and the 4 package fixtures by SHA-256, and `.gitattributes` stops git from rewriting their line endings. Every build now generates the JAXB model from `EnvelopedSignature.xsd`. The eFatura namespace goes into `com.lexcv.fiscal.efatura.xsd`, and SpotBugs skips only the four generated package families.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | Vendor 22 XSD + 4 fixtures, provenance README, .gitattributes, integrity test | 351c1b3 |
| 2 | JAXB deps, jaxb-maven-plugin 4.0.16 + .xjb, SpotBugs exclusions for generated code | 7d81a96 |

## Verification

- **Hashes:** `sha256sum -c` passed for all 22 XSD and all 4 fixtures, first on the scratchpad copy and again on the files in place. Every hash matches 136-RESEARCH.md "Exact files to vendor".
- **XsdEfaturaIntegridadeTest:** 4 tests, green.
  - RED was run first, before the files existed: 3 failures for missing resources.
  - The test checks the 22 and 4 hashes, the manifest sizes, the exact `.xsd` set and that the README exists.
- **Generated model:** `mvn -DskipTests compile` produced 137 classes, matching research Pattern 2 exactly:
  - `com/lexcv/fiscal/efatura/xsd`: 52, including `Dfe.java`
  - `org/etsi/uri/_01903/v1_3`: 53
  - `org/etsi/uri/_01903/v1_4`: 3
  - `org/w3/_2000/_09/xmldsig_`: 24
  - `un/unece/uncefact/...`: 2 + 1 + 2
- **SpotBugs:** `mvn -DskipTests compile spotbugs:check` exits 0.
- **Full backend unit suite:** `mvn -Dmaven.compiler.release=21 test` ran 1022 tests with 0 failures, 0 errors and 0 skipped.
- **Acceptance greps:** all pass.
  - 22 `.xsd` files.
  - The README contains "não implica aprovação da DNRE" and "2024-05-27".
  - Two `-text` lines in `.gitattributes`.
  - `jaxb-maven-plugin` appears once, `4.0.16` is present and `generatePackage` appears 0 times.
  - The two JAXB dependencies have no version.
  - The exact `com.lexcv.fiscal.efatura.xsd` exclusion is present, and no `com.lexcv.fiscal.efatura"/>` exclusion exists.
  - No "KRIOLOS" under `xsd/`.

## TDD Gate Compliance

The plan required the XSD, README, fixtures, `.gitattributes` and test to land in one commit. So there is no separate `test(...)` commit. The RED run did happen before the files were copied (3 failures for missing resources), and GREEN followed in the same commit 351c1b3. Task 2 is build configuration and has no RED step.

## Deviations from Plan

**1. [Rule 1 - Acceptance consistency] README and pom wording adjusted to keep the acceptance greps meaningful**
- **Found during:** Tasks 1 and 2.
- **Issue:** Two acceptance greps matched explanatory text instead of real content:
  - The README quoted the kriolos edit marker, which matched `grep -rl "KRIOLOS"`.
  - The pom comment named the forbidden `generatePackage` option, which matched `grep -c generatePackage`.
- **Fix:** Rephrased both. The meaning is unchanged, and no kriolos file or `generatePackage` configuration exists.
- **Commits:** 351c1b3, 7d81a96.

Otherwise the plan was executed as written.

## Notes for downstream plans

- Use `JAXBContext.newInstance(Dfe.class)` as a singleton and create one `Marshaller` per call (research Pattern 2).
- The README sits inside `xsd/efatura/`, so the later classpath resolver whitelist must serve only `.xsd` names.
- The JDK 23 build (Dockerfile) is still unexercised here; only JDK 21 is available. The first `docker build backend` or CI run is the JDK 23 smoke test.

## Threat Flags

None. T-136-01 (byte pin plus `-text`), T-136-02 (provenance README) and T-136-03 (exact or regex exclusions on generated packages only) are mitigated as planned.

## Self-Check: PASSED

- FOUND: backend/src/main/resources/xsd/efatura/README.md
- FOUND: backend/src/main/resources/xsd/bindings/efatura.xjb
- FOUND: backend/src/test/java/com/lexcv/fiscal/efatura/XsdEfaturaIntegridadeTest.java
- FOUND: .gitattributes
- FOUND: commits 351c1b3, 7d81a96
