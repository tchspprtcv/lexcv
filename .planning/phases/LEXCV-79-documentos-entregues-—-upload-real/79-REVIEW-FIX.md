---
phase: LEXCV-79-documentos-entregues-—-upload-real
fixed_at: 2026-07-06T16:50:00Z
review_path: .planning/phases/LEXCV-79-documentos-entregues-—-upload-real/79-REVIEW.md
iteration: 2
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-79: Code Review Fix Report

**Fixed at:** 2026-07-06T16:50:00Z
**Source review:** .planning/phases/LEXCV-79-documentos-entregues-—-upload-real/79-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 2 (both warnings — `fix_scope: critical_warning`, IN-01 excluded, 0 critical findings this round)
- Fixed: 2 (WR-04, WR-05)
- Skipped: 0

## Fixed Issues

### WR-04: `uploadDocumento` never validates `clienteId`/`processoId` belong to the caller's tenant (new-document branch)

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** 8e1d7d3
**Applied fix:** Added tenant-ownership validation for both optional `clienteId` and `processoId` request params in `uploadDocumento`, before the new-document branch builds the `Documento` entity. When `clienteId` is non-null, looks up the `Cliente` via `clienteRepository.findById` and rejects with `400 Bad Request` + `"clienteId não pertence a este tenant"` if it does not exist or its `tenantId` does not match the caller's tenant. Same treatment added for `processoId` against `processoRepository`, rejecting with `"processoId não pertence a este tenant"`. This mirrors the existing `createProcesso`'s `responsavelId` tenant check (same file, ~line 962) and the dozens of other tenant-ownership checks already used throughout `ResourceController`. Verified with `mvn -q -DskipTests compile` — clean compile, no errors.

### WR-05: XHR upload path swallows the backend's actual error message on failure

**Files modified:** `web/src/hooks/use-documentos.ts`
**Commit:** 2c60340
**Applied fix:** `useUploadDocumentoComProgresso`'s `xhr.onload` error branch (non-2xx status) now parses `xhr.responseText` as JSON and extracts `json.message || json.error`, falling back to `xhr.statusText` or a generic message if the body is not JSON or the fields are absent. The rejected `Error` message is now `API {status}: {errorMessage}`, matching `apiFetch`'s existing error-message extraction and rejection format in `web/src/lib/api.ts` exactly (same field-precedence order and same `API {status}: ...` message shape), so `onConfirmarUpload`'s `e.message` display and toast now surface the real backend validation message (e.g. the WR-04 tenant-check message above) instead of a generic `"API 400"` string. Verified with `tsc --noEmit -p tsconfig.json` — no new errors in `use-documentos.ts` (only pre-existing, unrelated `vitest` module-resolution errors in two `.test.ts` files outside this phase's scope).

## Skipped Issues

None — all findings were fixed.

---

_Fixed: 2026-07-06T16:50:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
