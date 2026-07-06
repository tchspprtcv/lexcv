---
phase: LEXCV-79-documentos-entregues-—-upload-real
reviewed: 2026-07-06T00:00:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - web/src/hooks/use-documentos.ts
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
findings:
  critical: 0
  warning: 2
  info: 1
  total: 3
status: issues_found
---

# Phase LEXCV-79: Code Review Report (Re-review)

**Reviewed:** 2026-07-06T00:00:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

This is a re-review after `79-REVIEW-FIX.md` iteration 1, which reported 4/6 findings fixed (CR-01, CR-02, WR-01, WR-03) and 2 skipped (CR-03, WR-02). All four claimed fixes were independently re-traced against the current source and confirmed correct:

- **CR-01** (FormData key mismatch): `useUploadDocumento` and `useUploadDocumentoComProgresso` in `use-documentos.ts` now `form.set("processoId", ...)` / `form.set("clienteId", ...)`, which match `uploadDocumento`'s `@RequestParam(value = "processoId", ...)` / `@RequestParam(value = "clienteId", ...)` exactly (`ResourceController.java:1986-1987`). Traced the full round trip end-to-end: upload sets `clienteId` on the persisted `Documento` → `GET /clientes/{id}/documentos` (`listClienteDocumentos`, line 2074-2081) queries `documentoRepository.findByTenantIdAndClienteId(getTenantId(), id)` → `useDocumentos({cliente_id})` hits this exact route when `cliente_id` is set (`use-documentos.ts:28-30`) → `invalidateQueries({queryKey: ["documentos", "list"]})` on upload success correctly invalidates the list query by prefix regardless of the trailing `clienteId` param in the key tuple. A document uploaded via the new tab will now appear in the list after the dialog closes. This is the core deliverable of the phase and it is fixed correctly.
- **CR-02** (JSON-payload download link): `ClienteDocumentoEntregueRow` now uses `useDownloadDocumento(documento.id)` + `window.open(res.url, ...)`, matching the `ProcuracaoCard` pattern in the same file. Confirmed no raw `<a href="/api/v1/documentos/.../download">` remains anywhere in the reviewed page.
- **WR-01** (wire field name mismatch / "NaN MB"): row component now reads `tamanho`/`createdAt` via a locally-scoped cast instead of the shared `Documento` type's `size`/`created_at`, with a comment explaining the scoping decision. Verified the backend `Documento` entity (`models/Documento.java`) has no Jackson naming override, so this correctly matches the actual wire shape.
- **WR-03** (untrimmed `tipo` combobox): both the datalist dedupe (`d.tipo?.trim()`) and the submit call (`novoTipo.trim()`) are now trimmed.

Backend compiles cleanly (`mvn -q -DskipTests compile`) and the frontend type-checks with no new errors (`tsc --noEmit` shows only pre-existing, unrelated `vitest` module-resolution errors in test files outside this phase's scope).

The two skip decisions were also re-evaluated:
- **CR-03 skip (generic `GET /documentos` ignores query filters)** — reasonable to skip. Confirmed the new `ClienteDocumentosEntreguesTab` never reaches this code path (`useDocumentos` takes the `cliente_id` branch, which calls the new dedicated endpoint). This is a pre-existing gap in the standalone `/documentos` search page, unrelated to this phase's file scope. Not escalating.
- **WR-02 skip (orphaned `documentos_entregues` field)** — reasonable to skip. `79-CONTEXT.md` explicitly locks this as a deliberate "corte limpo" (clean cut, no migration) decision at the milestone level, matching the same pattern already used for `dados_tipo` in a prior phase. Respecting a locked scope decision over a review suggestion is correct here. Not escalating.

Re-tracing the write path surfaced one issue the prior round did not catch: `uploadDocumento`'s non-replace branch persists the client-supplied `clienteId`/`processoId` onto the new `Documento` row with **no validation that either ID exists or belongs to the caller's own tenant** (contrast with every other cliente/processo-scoped endpoint in this same controller, which uniformly re-checks `X.getTenantId().equals(getTenantId())` before trusting a path/body ID). This does not create a cross-tenant *read* leak (the read-side `listClienteDocumentos` still re-validates the cliente's tenant before querying), but it is a real data-integrity/authorization gap: a user can attach a document to an arbitrary `clienteId` UUID — including one from a different tenant, or one that doesn't exist at all — with no server-side rejection. Filed as WR-04 below (new finding, not one of the original 6, so not double-counted against the prior "fixed 4/6" tally).

## Warnings

### WR-04: `uploadDocumento` never validates `clienteId`/`processoId` belong to the caller's tenant (new-document branch)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1984-2043`

**Issue:** Every other endpoint in `ResourceController` that accepts a cliente/processo id as input re-validates tenant ownership before trusting it, e.g.:
```java
Cliente cliente = clienteRepository.findById(id).orElse(null);
if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)...
}
```
(see `getCliente`, `listClienteContactos`, `addClienteAdvogado`, `listClienteDocumentos`, etc. — this pattern is used dozens of times in this file). `uploadDocumento`'s new-document branch (the `else` at line 2025, i.e. every upload that isn't a `replace_id` re-upload) is the one place that receives a cliente/processo-shaped identifier and skips this check entirely:
```java
UUID documentoId = UUID.fromString(fileId);
InputStream inputStream = file.getInputStream();
String objectKey = storageService.upload(getTenantId(), documentoId,
        originalName, inputStream, file.getContentType(), file.getSize());

documento = Documento.builder()
        .id(documentoId)
        .tenantId(getTenantId())
        .processoId(processoId)   // <- never checked against tenant or existence
        .clienteId(clienteId)     // <- never checked against tenant or existence
        ...
```
Concretely: any authenticated user with `documentos:edit` (any tenant) can `POST /documentos/upload` with `clienteId=<uuid belonging to a different tenant, or a non-existent uuid>` and the call succeeds — the resulting `Documento` row is created with `tenantId` = the uploader's own tenant but `clienteId` pointing at a cliente the uploader has no relationship to or that doesn't exist. This does **not** leak the row into the other tenant's `GET /clientes/{id}/documentos` response (that endpoint independently re-checks `cliente.getTenantId().equals(getTenantId())` before querying by `clienteId`), so it is not a cross-tenant read-disclosure bug. It is, however: (a) a referential-integrity gap — `clienteId` on `Documento` can point at nothing or at the wrong tenant's data with no FK/tenant enforcement, and (b) a latent risk for any future code path that trusts `documento.getClienteId()` without re-deriving/re-checking tenant (the existing endpoints happen to be safe today only because they all re-validate independently — that safety is coincidental, not structural).

**Fix:** Validate both optional ids the same way every other endpoint in this file does, before building the `Documento`:
```java
if (clienteId != null) {
    Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
    if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "clienteId não pertence a este tenant"));
    }
}
if (processoId != null) {
    Processo processo = processoRepository.findById(processoId).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "processoId não pertence a este tenant"));
    }
}
```
(Mirrors the existing `createProcesso`'s `responsavelId` tenant check at line 962-967 in the same file.)

### WR-05: XHR upload path swallows the backend's actual error message on failure

**File:** `web/src/hooks/use-documentos.ts:125-134`

**Issue:** `useUploadDocumentoComProgresso` (the hook powering the new tab's upload dialog) implements its own `XMLHttpRequest` instead of going through `apiFetch`, and on a non-2xx response it rejects with only the status code:
```ts
xhr.onload = () => {
  if (xhr.status >= 200 && xhr.status < 300) {
    ...
  } else {
    reject(new Error(`API ${xhr.status}`));
  }
};
```
`apiFetch` (used by every other hook in this file and across the app) parses the response body and surfaces `json.message || json.error` in the thrown error, which is what `onConfirmarUpload` in `page.tsx:1267-1271` (`e instanceof Error ? e.message : "Erro ao fazer upload"`) and the corresponding toast expect. With the XHR path, a backend validation failure (e.g. a 400 for an oversized file, invalid content type, or the WR-04 tenant check proposed above) surfaces to the user as the unhelpful string `"API 400"` instead of the actual backend message, in the exact new dialog this phase adds.

**Fix:** Parse `xhr.responseText` as JSON on the error branch too, mirroring `apiFetch`'s error-message extraction:
```ts
xhr.onload = () => {
  if (xhr.status >= 200 && xhr.status < 300) {
    try {
      resolve(JSON.parse(xhr.responseText) as DocumentoUploadResponse);
    } catch {
      reject(new Error("Resposta inválida do servidor"));
    }
  } else {
    let message = `API ${xhr.status}`;
    try {
      const body = JSON.parse(xhr.responseText);
      if (body?.message) message = body.message;
    } catch {
      // ignore, keep default message
    }
    reject(new Error(message));
  }
};
```

## Info

### IN-01: Prior fixes independently verified, no regressions found

**File:** n/a

**Issue:** No informational issue — verification note. All four claimed fixes (CR-01, CR-02, WR-01, WR-03) were re-traced against current source (not just the diff/commit description) and confirmed functionally correct, including the full upload→list round trip for CR-01 and the absence of any lingering raw download anchors for CR-02. Backend (`mvn compile`) and frontend (`tsc --noEmit`) both build clean with no new errors attributable to this phase. No action needed.

---

_Reviewed: 2026-07-06T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
