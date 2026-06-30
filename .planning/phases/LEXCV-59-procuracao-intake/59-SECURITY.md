# Security Audit — Phase LEXCV-59 (Procuração + Intake)

**ASVS Level:** 1
**Block on:** high
**Audited:** 2026-06-30
**Threats:** 13 registered (T-59-01 .. T-59-12, T-59-SC) | **Closed:** 12/13 | **Open:** 0 (registered) | **Unregistered gap flagged:** 1 (WR-02)

This audit verifies each threat's *declared* mitigation exists in the implemented code. It does not re-derive the STRIDE model from scratch (per phase config `register_authored_at_plan_time=true`), but flags one coverage gap surfaced by the prior code review (59-REVIEW.md WR-02) that falls outside the original register's scope.

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-59-01 | Tampering | mitigate | CLOSED | `ResourceController.updateCliente` (lines 258–293) never calls `setProcuracaoKey()`; field only mutated in `uploadProcuracao`/`deleteProcuracao` |
| T-59-02 | Elevation of Privilege | mitigate | CLOSED | `ResourceController.java:414-415` — `addAdvogado` rejects with 400 unless `user.getRoles().stream().anyMatch(r -> "ADVOGADO".equals(r.getNome()))` before persisting `ClienteAdvogado` |
| T-59-03 | Elevation of Privilege | mitigate | CLOSED | `ResourceController.java:476-477` — `addAdministrativo` rejects with 400 unless role is `ASSISTENTE` or `TECNICO` before persisting `ClienteAdministrativo` |
| T-59-04 | Spoofing | mitigate | CLOSED | `ResourceController.java:300-329` — `uploadProcuracao` derives tenant exclusively via `getTenantId()` (security-context-bound); no client-supplied tenantId field accepted or used |
| T-59-05 | Elevation of Privilege | mitigate | CLOSED | Same evidence as T-59-02 (duplicate threat, same endpoint) |
| T-59-06 | Tampering | mitigate | CLOSED | Same evidence as T-59-01 (duplicate threat) |
| T-59-07 | Denial of Service | accept | CLOSED | Accepted risk logged below. Spring Boot multipart size limits apply globally (`application.yml` `spring.servlet.multipart.max-file-size`); single-file low-volume upload path |
| T-59-08 | Information Disclosure | accept | CLOSED | Accepted risk logged below. `ResourceController.java:352` returns `expiresIn: 3600`; URL is MinIO presigned, time-bound server-side |
| T-59-09 | Information Disclosure | accept | CLOSED | Duplicate of T-59-08; same evidence and accepted-risk entry |
| T-59-10 | Tampering | mitigate | CLOSED | `web/src/app/(dashboard)/clientes/[id]/page.tsx:419` — `window.confirm("Remover este utilizador?")` guards `remove.mutateAsync(userId)` for both Advogados and Administrativos cards |
| T-59-11 | Tampering | accept | CLOSED | Accepted risk logged below. Confirmed no `dangerouslySetInnerHTML` anywhere under `web/src/app/(dashboard)/clientes/[id]/` — `descricao_caso` is rendered as plain React text content (auto-escaped) |
| T-59-12 | Tampering | mitigate | CLOSED | `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx:516-520` — `honorarios_propostos.total` input uses `type="number"` and `register(..., { valueAsNumber: true })`; backend `HonorariosPropostos.total` is `BigDecimal` |
| T-59-SC | Tampering (supply chain) | accept | CLOSED | Accepted risk logged below. Verified no new dependencies added across all 6 plan SUMMARY.md files (`files_modified` lists contain no `pom.xml`/`package.json` entries) |

## Accepted Risks Log

The following risks are formally accepted for this phase (ASVS Level 1, low-sensitivity single-tenant-scoped file/JSON intake feature). Re-evaluate if procuração documents start carrying higher sensitivity classification or file size grows materially.

- **T-59-07** (DoS via large procuração upload): Accepted. Mitigated at infrastructure layer by existing Spring multipart size limits; procuração is a single low-frequency upload per cliente, not a bulk path.
- **T-59-08 / T-59-09** (Information disclosure via presigned URL): Accepted. URL TTL is 3600s, matches the existing `/documentos` download pattern already in production; no broader exposure than prior art.
- **T-59-11** (XSS via `descricao_caso`): Accepted. React's default text-node rendering escapes all string content; no `dangerouslySetInnerHTML` usage introduced in this phase's files.
- **T-59-SC** (Supply-chain risk via new installs): Accepted. No new package installs occurred in any of the 6 implementation plans (verified against `files_modified` and plan SUMMARY.md task commit lists).

## Unregistered Attack Surface (WARNING — not a blocker per `block_on: high`)

### WR-02 — Procuração upload accepts arbitrary file content-type (unregistered, distinct from T-59-07)

**Status:** OPEN — confirmed still present in implementation; not fixed since prior code review.

**Location:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, `uploadProcuracao` (lines 300–337).

**Finding:** The endpoint validates only `file.isEmpty()` and that `file.getOriginalFilename()` is non-blank (lines 306–313). It never inspects `file.getContentType()` or the filename extension against an allowlist before calling `storageService.upload(...)`. This permits arbitrary file types — including executables and scripts — to be stored as a cliente's "procuração" object and later served back via the presigned download URL (`downloadProcuracao`).

**Why this is distinct from T-59-07:** T-59-07 (accepted) addresses *volume/size* DoS via existing multipart limits. WR-02 is a *content-type/stored-malicious-content* threat (Tampering / Elevation via served content) with no disposition in the original register — it was not derived at plan-authoring time and was only surfaced by the subsequent code review (59-REVIEW.md).

**This was NOT a registered threat at PLAN.md authoring time** (`register_authored_at_plan_time=true` for this phase), so per audit scope it is logged here as an `unregistered_flag` / WARNING rather than re-classified as a BLOCKER threat-register OPEN item. However, it is a real, currently-unmitigated gap and should be triaged before the procuração feature is considered production-hardened.

**Recommended fix (not applied — implementation files are read-only for this audit):**
```java
private static final Set<String> ALLOWED_PROCURACAO_TYPES = Set.of(
    "application/pdf", "image/jpeg", "image/png");
```
Add a content-type allowlist check in `uploadProcuracao` before the `storageService.upload(...)` call, returning 400 for disallowed types.

**Disposition recommendation for next planning cycle:** `mitigate` — register as a new threat ID (e.g. T-59-13) in a follow-up plan or hotfix, with the allowlist check above as the mitigation.

## Result

`block_on: high` — no registered threat is OPEN, all `mitigate` dispositions verified present in code, all `accept` dispositions now have corresponding entries in this Accepted Risks Log. WR-02 is logged as an unregistered WARNING flag per audit scope; it does not block this phase under the configured `block_on: high` policy since it was not a registered threat, but it is strongly recommended for prompt remediation given it allows arbitrary file storage in a per-tenant document store.
