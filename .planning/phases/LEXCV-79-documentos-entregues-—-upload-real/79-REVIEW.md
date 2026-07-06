---
phase: LEXCV-79-documentos-entregues-—-upload-real
reviewed: 2026-07-06T17:10:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - web/src/hooks/use-documentos.ts
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
findings:
  critical: 0
  warning: 0
  info: 1
  total: 1
status: issues_found
---

# Phase LEXCV-79: Code Review Report (Re-review, iteration 3 of 3 — final)

**Reviewed:** 2026-07-06T17:10:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found (info only — no blockers or warnings remain)

## Summary

Final re-review of the auto-fix loop. Prior rounds resolved, in order: CR-01 (FormData key mismatch), CR-02/WR-01 (download link + wire field mapping), WR-03 (tipo trimming), and this round's two carry-overs, WR-04 (missing tenant validation on upload's `clienteId`/`processoId`) and WR-05 (swallowed XHR error messages). Both were re-traced against current source, not the fix-report's description, and confirmed correctly applied with no regressions.

**WR-04 fix verified (`ResourceController.java:2001-2014`):**
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
This sits before the `try` block and both branches (`replaceId != null` and the new-document `else`), so the check applies uniformly regardless of which downstream path executes. Critically, both guards are gated on `!= null`, so the specific case called out for careful tracing — **a standalone upload with neither `clienteId` nor `processoId` set** (the common case for a generic/unlinked documento) — skips both blocks entirely and falls straight through to `storageService.upload(...)` with no behavior change from before the fix. Traced this by hand against all three call sites in the reviewed files:
- `useUploadDocumento`/`useUploadDocumentoComProgresso` in `use-documentos.ts` only call `form.set("clienteId", ...)` / `form.set("processoId", ...)` when the respective value is a non-empty trimmed string (`if (payload.processo_id?.trim()) ...`, `if (payload.cliente_id?.trim()) ...`), so a payload built with neither field (or with only `tipo`/`file`) never sends the param, and Spring binds the corresponding `@RequestParam(required = false)` to `null` — reaching the skip path correctly.
- `ClienteDocumentoEntregueRow`'s upload call in `page.tsx` (`onConfirmarUpload`, line 1262) always supplies `cliente_id: clienteId` (this tab is cliente-scoped), so it always exercises the validated branch, and `clienteId` here is the real path param from the loaded cliente detail page — always same-tenant by construction (the page itself 404s on cross-tenant `getCliente` before rendering this tab). No behavior change for this call site either; the added check is a no-op success path for it.
- No call site in the reviewed files sends `processoId` at all (this phase's tab is cliente-only), so the `processoId` branch is currently dead for these three files but correctly wired for any current/future processo-scoped caller.

Confirmed the reject uses `400 Bad Request` (not `404`), which is intentional and matches the fix report's stated design — the ID itself may not exist at all (not just belong to another tenant), and `400` reads correctly for "the client sent a bad value" versus reserving `404` for "you're looking at a specific resource that isn't there."

**WR-05 fix verified (`use-documentos.ts:125-146`):** the XHR error branch now parses `xhr.responseText` as JSON inside a `try/catch`, extracts `json.message || json.error`, and falls back through `xhr.statusText` to a generic string, matching `apiFetch`'s extraction order in `web/src/lib/api.ts:30-39`. The rejected `Error` message format (`API {status}: {errorMessage}`) now matches `apiFetch`'s thrown format exactly, so a WR-04 `400` rejection (`"clienteId não pertence a este tenant"`) surfaces verbatim to the user via `onConfirmarUpload`'s catch block and toast in `page.tsx:1267-1272`, instead of the previous generic `"API 400"` string.

No new bugs, security gaps, or quality regressions were found in this iteration. Backend and frontend were not re-built as part of this review pass; source-level tracing was used to verify both fixes end-to-end across all three files in scope.

## Info

### IN-01: `replaceId` branch silently ignores `clienteId`/`processoId` even though both are now validated

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2001-2039`

**Issue:** Not a regression from WR-04 and not a security issue (this is a validated-but-unused value, which is safe) — flagging only as a pre-existing quirk made slightly more visible by this round's fix. When `replaceId` is supplied, `clienteId`/`processoId` are still validated (lines 2001-2014 run unconditionally for every upload), but the replace branch (lines 2020-2039) never applies either value to the existing `documento` being replaced — only `nome`, `tipo`, `confidencialidade`, `caminhoArquivo`, `tamanho`, `mimeType`, and `versao` are updated. A caller replacing a documento while also passing a different `clienteId` would have that value silently validated then discarded, with the original documento's `clienteId` association unchanged. This is very likely intentional (replace = "new version of the same file," not "reassign to a different cliente"), and no current frontend call site in this phase sends `clienteId` together with `replace_id`, so it has no observable effect today. Not escalating to a Warning since there's no evidence this is unintended behavior, but worth a one-line comment if a future phase adds a "replace + reassign" flow, to make the intentional no-op explicit at the call site.

**Fix:** No action required for this phase. If desired, add a short comment above the replace branch noting that `clienteId`/`processoId` are intentionally not reapplied on replace, to preempt future confusion:
```java
if (replaceId != null) {
    documento = documentoRepository.findById(replaceId).orElse(null);
    if (documento == null || !documento.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento a substituir não encontrado"));
    }
    // Note: clienteId/processoId (validated above) are intentionally not reapplied here —
    // "replace" means a new version of the same file, not reassignment to a different
    // cliente/processo. The existing documento's associations are preserved as-is.
    ...
}
```

---

_Reviewed: 2026-07-06T17:10:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
