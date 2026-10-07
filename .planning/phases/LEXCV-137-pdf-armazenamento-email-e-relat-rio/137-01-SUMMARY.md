---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 01
subsystem: backend/fiscal (PDF, email infrastructure)
tags: [spike, openhtmltopdf, pdfbox, spring-mail, greenmail, mailpit, fonts, supply-chain]
requires: []
provides:
  - 137-SPIKE.md (verified library facts + exact OpenHTMLtoPDF builder API for 137-06; mail rule for 137-08/137-09)
  - pom dependencies spring-boot-starter-mail, openhtmltopdf-pdfbox 1.1.87, greenmail-junit5 2.1.14 (test)
  - classpath fonts pdf/fonts/DejaVuSans.ttf, DejaVuSans-Bold.ttf, DejaVuSansMono.ttf + LICENSE-DejaVu.txt
affects: [137-06, 137-08, 137-09, email ITs, live UAT]
tech-stack:
  added: [io.github.openhtmltopdf:openhtmltopdf-pdfbox 1.1.87, org.apache.pdfbox 3.0.7 (transitive), spring-boot-starter-mail (Boot 3.4.1), com.icegreen:greenmail-junit5 2.1.14]
  patterns: [vendored resource + README provenance + SHA-256 integrity test (mirrors Phase 136 XSD)]
key-files:
  created:
    - .planning/phases/LEXCV-137-pdf-armazenamento-email-e-relat-rio/137-SPIKE.md
    - backend/src/main/resources/pdf/README.md
    - backend/src/main/resources/pdf/fonts/DejaVuSans.ttf
    - backend/src/main/resources/pdf/fonts/DejaVuSans-Bold.ttf
    - backend/src/main/resources/pdf/fonts/DejaVuSansMono.ttf
    - backend/src/main/resources/pdf/fonts/LICENSE-DejaVu.txt
    - backend/src/test/java/com/lexcv/fiscal/pdf/FontesPdfIntegridadeTest.java
  modified:
    - backend/pom.xml
    - .gitattributes
decisions:
  - "PDF: io.github.openhtmltopdf:openhtmltopdf-pdfbox 1.1.87 (maintained fork; PDFBox 3.0.7; LGPL-2.1-or-later / Apache-2.0; bytecode 52)"
  - "Mail: never use spring.mail.* (an empty spring.mail.host still auto-creates a JavaMailSender); SMTP lives under app.email.smtp.* and the adapter builds a private JavaMailSenderImpl only when host is non-blank"
  - "Email ITs use GreenMail 2.1.14 in-process; live UAT uses Mailpit axllent/mailpit:v1.27.10 (SMTP 1025, HTTP 8025)"
  - "Fiscal PDF fonts: DejaVu 2.37 Sans/Bold/Mono vendored and pinned by SHA-256"
metrics:
  duration: ~45min
  completed: 2026-10-07
  tasks: 3
  files: 9
---

# Phase 137 Plan 01: Library spike, dependencies and vendored PDF fonts Summary

The spike checked the library facts against Maven Central metadata, the jars and running code. OpenHTMLtoPDF 1.1.87 (io.github fork, PDFBox 3.0.7) renders the exact "SIMULAÇÃO — SEM VALIDADE FISCAL" string on every page with embedded DejaVu fonts and fetches nothing. Spring Boot 3.4.1 still auto-creates a JavaMailSender when `spring.mail.host` is empty, so SMTP config goes under `app.email.smtp.*`. GreenMail 2.1.14 is the IT harness and Mailpit v1.27.10 the live UAT mail catcher. Three DejaVu 2.37 fonts are vendored and pinned by SHA-256.

## Tasks

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Spike A–E -> 137-SPIKE.md | 4dbe942 | 137-SPIKE.md |
| 2 | pom dependencies | 8b32206 | backend/pom.xml |
| 3 (RED) | Font integrity test | 93359b8 | FontesPdfIntegridadeTest.java |
| 3 (GREEN) | Fonts + licence + README + .gitattributes | 5a9048f | pdf/fonts/*, pdf/README.md, .gitattributes |

## Key findings (details in 137-SPIKE.md)

- **A:** latest io.github fork release is 1.1.87 (the original com.openhtmltopdf line stopped at 1.0.10). PDFBox/FontBox/XMPBox are 3.0.7 in both the spike and the backend `dependency:tree`. All jars are class-file major version 52.
- **B:** a 3-page render had the header and watermark string twice per page, the `thead` repeated, "Página N de 3" on every page, and every font embedded. `file:`, `http:` and `data:` URIs all reach the `FSUriResolver`. Returning null rejects them, and the HTTP/protocol stream factories were never called. Two things 137-06 must include:
  - `-fs-table-paginate: paginate` on the table, or `thead` does not repeat.
  - `white-space: nowrap` on the watermark, or the string wraps and can't be extracted whole.
  - The exact builder API is recorded verbatim in the spike.
- **B side note:** PDFBox's FontMapper scans system fonts and writes `~/.pdfbox.cache` because OpenHTMLtoPDF loads the Standard-14 fallback fonts. No system font ends up in the output. The spike records two ways 137-06 can avoid the scan: `FontMappers.set` or `-Dpdfbox.fontcache`.
- **C:** with no `spring.mail.*`: 0 beans. `spring.mail.host=` (empty): 1 bean. `=false`: 0 beans. A real host: 1 bean.
- **D:** GreenMail received the message with 2 attachments and the UTF-8 subject intact. Mailpit's REST API was checked: `/api/v1/messages`, `/api/v1/message/{ID}` and `/api/v1/message/{ID}/part/{PartID}`.
- **E:** archive SHA-256 is `fa9ca4d1…e17d7`; per-file hashes are in the README and the test.

## Verification

- Spike verify (sections A–E + exact string): passed.
- `mvn -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check`: clean. `java.version` is still 23 (1 match). `dependency:tree` shows pdfbox 3.0.7.
- `FontesPdfIntegridadeTest`: failed before the fonts were added (RED, 3 of 4 failing), then **4/4 passed** (GREEN).
- Throwaway spike project tests (not in the repo): RenderSpikeTest, MailAutoConfigSpikeTest (4 cases), GreenMailSpikeTest — all passed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] OSV advisory query could not be run**
- **Found during:** Task 1 (A)
- **Issue:** the egress proxy refuses `api.osv.dev` with CONNECT 403 (organisation policy). The fallbacks are unavailable too: NVD is refused, and GitHub GraphQL and `/advisories` are not available in this session.
- **Action:** recorded as **PENDING** in 137-SPIKE.md A, with the exact package@version list to query. Nothing was substituted. The stop rule did not fire: no criterion failed, one could not be checked.
- **Follow-up for the user/CI:** run the OSV query (or `mvn dependency-check:check`, failBuildOnCVSS=7) before the phase closes.

**2. [Rule 2 - Correctness] .gitattributes rule for the fonts**
- The repo already has a `.gitattributes`, so per the plan I added `pdf/fonts/** -text` and `*.ttf binary`. `git diff --stat` shows the fonts as Bin.

## TDD Gate Compliance

Task 3: `test(137-01)` commit 93359b8 (RED), then `feat(137-01)` commit 5a9048f (GREEN). No refactor needed.

## Threat Flags

None. No new endpoints or trust-boundary surface; T-137-SC is only partly mitigated because of the pending OSV check (deviation 1).

## Self-Check: PASSED

All 9 created/modified files are present; commits 4dbe942, 8b32206, 93359b8 and 5a9048f are in `git log`.
