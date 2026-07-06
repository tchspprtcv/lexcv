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
  critical: 3
  warning: 3
  info: 1
  total: 7
status: issues_found
---

# Phase LEXCV-79: Code Review Report

**Reviewed:** 2026-07-06T00:00:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Reviewed the new tenant/cliente-scoped `GET /clientes/{id}/documentos` endpoint, the repointed `useDocumentos` hook, and the new `ClienteDocumentosEntreguesTab`/`ClienteDocumentoEntregueRow` sub-components in the cliente detail page. The new read-side endpoint itself is correctly tenant- and cliente-scoped (mirrors the existing processo-scoped endpoint, checks `cliente.getTenantId()` before querying). However, tracing the upload write-path that feeds this new tab surfaced a **request-parameter name mismatch between the frontend upload FormData and the backend `@RequestParam` names**, which means documents uploaded through the new "Documentos Entregues" tab are never actually associated with the cliente server-side — the core feature this phase claims to deliver does not work end-to-end. Tracing the download link in the new list row surfaced a second correctness defect: it points directly at an endpoint that returns JSON (a presigned URL payload), not a file stream, so "download" opens a JSON blob instead of the file. A third defect (pre-existing, but sitting immediately next to the code this phase touched) is that the generic `GET /documentos` fallback branch in `useDocumentos` ignores all filters server-side and returns every document in the tenant. The legacy text-based "Documentos Entregues" list was successfully removed from the editable cliente form (no state/handlers/payload references remain in `page.tsx`), but the underlying `documentos_entregues` field was left live in the `Cliente`/`ClienteUpdateRequest` types, the backend entity/update handler, and the printable "ficha" page — dead but not fully excised. `PlaceholderEmBreve` removal is clean (zero remaining references anywhere in `web/`).

## Critical Issues

### CR-01: Upload FormData field names don't match backend `@RequestParam` names — cliente/processo association silently dropped

**File:** `web/src/hooks/use-documentos.ts:60-61` (also `:111-112`), cross-referenced with `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1986-1987`

**Issue:** `useUploadDocumento` and `useUploadDocumentoComProgresso` (the hook used by the new `ClienteDocumentosEntreguesTab`) build the multipart form with:
```ts
if (payload.processo_id?.trim()) form.set("processo_id", payload.processo_id.trim());
if (payload.cliente_id?.trim()) form.set("cliente_id", payload.cliente_id.trim());
```
But `uploadDocumento` on the backend declares:
```java
@RequestParam(value = "processoId", required = false) UUID processoId,
@RequestParam(value = "clienteId", required = false) UUID clienteId,
```
Spring's `@RequestParam` binds by exact name with no snake_case-to-camelCase fallback configured anywhere in this codebase (no custom `ServletModelAttributeMethodProcessor`, no Jackson naming strategy applies to `@RequestParam`/multipart binding). The `cliente_id` field the frontend sends is therefore never read; `clienteId` is always `null` inside the controller for every upload originating from the web app.

Concretely: a user opens a cliente's "Documentos Entregues" tab, uploads a file through `ClienteDocumentosEntreguesTab`'s dialog. The `POST /documentos/upload` call succeeds (201 Created, `documentos:edit` passes), a `Documento` row is persisted with `tenantId` set correctly but `clienteId = null`. The new tab's list (`GET /clientes/{id}/documentos`, filtered by `clienteId`) will never show the just-uploaded file — the toast says "Documento enviado com sucesso" but the list appears unchanged after `invalidateQueries` runs and refetches. The document instead becomes a tenant-wide orphan visible only via the generic `/documentos` page with no cliente/processo link. This defeats the entire purpose of the phase (upload real documents scoped to a cliente).

The same bug affects `processo_id` uploads via the generic `documentos/novo` page (pre-existing, not introduced by this phase, but confirms the defect is systemic rather than a one-off typo).

**Fix:** Align the FormData field names with the backend's actual `@RequestParam` names (or vice versa — pick one convention and use it consistently). Minimal fix on the frontend:
```ts
if (payload.processo_id?.trim()) form.set("processoId", payload.processo_id.trim());
if (payload.cliente_id?.trim()) form.set("clienteId", payload.cliente_id.trim());
```
Apply the same change in both `useUploadDocumento` (line 60-61) and `useUploadDocumentoComProgresso` (line 111-112). Add an integration/e2e test that uploads via `ClienteDocumentosEntreguesTab` and asserts the resulting document appears in a subsequent `GET /clientes/{id}/documentos` call — this class of bug is invisible to unit tests that mock `apiFetch`/`XMLHttpRequest`.

---

### CR-02: Download link opens a JSON payload instead of the file — "direct-link download" is broken

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1412-1419`

**Issue:** `ClienteDocumentoEntregueRow` renders:
```tsx
<a
  href={`/api/v1/documentos/${documento.id}/download`}
  target="_blank"
  rel="noreferrer"
  className="..."
>
  Download
</a>
```
But `GET /documentos/{id}/download` (`ResourceController.java:2084-2110`) does not stream the file — it returns a JSON body `{"url": "...", "expiresIn": 3600}` (a presigned storage URL the client is expected to follow in a second step). Navigating a browser tab directly to this endpoint via a plain anchor tag will display/download the raw JSON text, not the document. This is exactly why `useDownloadDocumento` exists and is used correctly elsewhere in the same codebase (`web/src/app/(dashboard)/documentos/[id]/page.tsx:40`, and mirrored by `ProcuracaoCard`'s `useDownloadProcuracao` + `window.open(res.url)` pattern in this very file, lines 1464-1471). The new row component bypasses this hook entirely.

**Fix:** Use the existing `useDownloadDocumento` hook and open the resolved presigned URL, matching the pattern already used by `ProcuracaoCard` in the same file:
```tsx
function ClienteDocumentoEntregueRow({ documento, editable, canEditDocumentos }: {...}) {
  const download = useDownloadDocumento(documento.id);
  const del = useDeleteDocumento(documento.id);

  const onDownload = async () => {
    try {
      const res = await download.mutateAsync();
      window.open(res.url, "_blank", "noopener,noreferrer");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao gerar link de download.");
    }
  };
  // ...
  <button type="button" onClick={onDownload} disabled={download.isPending}>Download</button>
```

---

### CR-03: Generic `GET /documentos` ignores `processo_id`/`cliente_id` query params — returns every tenant document unfiltered

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2057-2061`, exercised via `web/src/hooks/use-documentos.ts:26-32`

**Issue:** `useDocumentos`'s fallback branch (taken whenever `cliente_id` is absent, e.g. on the generic `/documentos` search page with only a `processo_id` filter typed in) calls:
```ts
return apiFetch<Documento[]>(`/documentos${buildDocumentosSearch({ processo_id: processoId, cliente_id: clienteId })}`);
```
which produces a request like `GET /documentos?processo_id=<uuid>`. The backend handler for this route is:
```java
@GetMapping("/documentos")
public ResponseEntity<?> listDocumentos() {
    return ResponseEntity.ok(documentoRepository.findByTenantId(getTenantId()));
}
```
It declares no `@RequestParam`s at all and silently ignores any query string, always returning the full tenant document set. A user filtering by a specific `processo_id` on the `/documentos` page (`web/src/app/(dashboard)/documentos/page.tsx`) will see every document belonging to every processo/cliente in the tenant, not just the one they filtered for — an information-disclosure-adjacent correctness bug within the tenant boundary (not a cross-tenant leak, but a cross-processo/cross-cliente leak inside the tenant, which can expose confidential documents belonging to unrelated clients/processos to any user with `documentos:view`).

This is pre-existing behavior, not newly introduced by this phase, but it sits directly beside the cliente-scoping logic this phase added and is part of the same hook/endpoint family this phase was meant to harden. The dedicated `GET /processos/{id}/documentos` endpoint (line 2064) already exists and correctly filters — the generic listing path was simply never wired to it or to real query filtering.

**Fix:** Either route `processo_id`-filtered requests through the existing `GET /processos/{id}/documentos` endpoint (mirroring what this phase just did for `cliente_id`), or add real filtering to `listDocumentos`:
```java
@GetMapping("/documentos")
public ResponseEntity<?> listDocumentos(
        @RequestParam(required = false) UUID processo_id,
        @RequestParam(required = false) UUID cliente_id) {
    UUID tenantId = getTenantId();
    List<Documento> docs = documentoRepository.findByTenantId(tenantId);
    if (processo_id != null) docs = docs.stream().filter(d -> processo_id.equals(d.getProcessoId())).toList();
    if (cliente_id != null) docs = docs.stream().filter(d -> cliente_id.equals(d.getClienteId())).toList();
    return ResponseEntity.ok(docs);
}
```

## Warnings

### WR-01: `Documento` field name mismatch between backend entity JSON and frontend `Documento` type — size/date render as `NaN`/blank

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1202-1213, 1407-1409`, cross-referenced with `backend/src/main/java/com/lexcv/models/Documento.java` and `web/src/types/documentos.ts`

**Issue:** The backend `Documento` entity has no Jackson naming override and no global snake_case `ObjectMapper` config is set anywhere in `application.yml`/`application.properties`, so `ResourceController`'s raw-entity responses (`GET /clientes/{id}/documentos`, `GET /documentos`, etc.) serialize with the entity's camelCase Java field names: `tenantId`, `processoId`, `clienteId`, `tamanho`, `mimeType`, `caminhoArquivo`, `createdAt`. The frontend `Documento` type (`web/src/types/documentos.ts`) and the new row component instead expect snake_case/renamed fields that don't exist on the wire: `tenant_id`, `processo_id`, `cliente_id`, `size`, `content_type`, `filename`, `created_at`. Concretely, `formatDocumentoSize(doc.size)` and `formatDocumentoDate(doc.created_at)` (lines 1407-1408) will receive `undefined` for every document rendered in the new tab, since the actual keys on the response are `tamanho` and `createdAt`. `formatDocumentoSize(undefined)` evaluates `undefined < 1024` as `false`, falls through to the final branch, and returns the string `"NaN MB"`; `formatDocumentoDate(undefined)` correctly falls back to `"—"` only because of the explicit `!v` guard, but the size string will visibly render as `NaN MB` in the UI for every row.

This is a pre-existing contract mismatch across the whole documentos feature, not something newly introduced in this phase's diff, but the newly-added `ClienteDocumentosEntreguesTab`/`ClienteDocumentoEntregueRow` directly inherits and displays the broken fields, so the phase ships a visibly broken list (every row will show "NaN MB").

**Fix:** Either add a response DTO in the backend that maps to the exact snake_case/renamed shape the frontend expects (preferred, keeps API contract explicit and decoupled from JPA field names), or fix the frontend `Documento` type and all consumers to use the entity's actual camelCase field names (`tamanho`, `mimeType`, `createdAt`, `tenantId`, etc.). Given the amount of code already written against the snake_case shape, a dedicated `DocumentoResponseDto` with `@JsonProperty`-annotated snake_case fields is the lower-risk fix.

### WR-02: Legacy `documentos_entregues` field left live in types/entity/ficha page despite tab removal

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:293`, `web/src/types/clientes.ts:56,104,109`, `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:154-156,213`

**Issue:** The task description states the legacy text-based "Documentos Entregues" list (state/handlers/JSX/payload field) was fully removed. The editable form in `page.tsx` no longer builds or sends a `documentosEntregues`/`documentos_entregues` payload field (confirmed — no such reference remains in the submit handler), so this is correctly excised from the write path used by the UI. However:
- `Cliente.java` (backend entity) still has the `documentosEntregues` field/column and `updateCliente` (line 293) still applies it whenever a caller sends it: `if (payload.getDocumentosEntregues() != null) cliente.setDocumentosEntregues(payload.getDocumentosEntregues());`
- `web/src/types/clientes.ts` still declares `documentos_entregues`/`documentosEntregues` on both `Cliente` and `ClienteUpdateRequest`.
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` still reads and prints `cliente.documentos_entregues` on the printable ficha (lines 154-156, 213).

Net effect: any cliente record that already has legacy `documentos_entregues` data will continue to display it forever on the printed ficha, with no UI path left to edit or clear it (the tab that used to manage it is gone), and the backend still silently accepts/persists it if any other caller (e.g. a future API consumer, or a stale cached frontend bundle) submits the field. This is dead-but-reachable code, not a functional break for the current UI, but it's an inconsistent half-migration that will confuse future maintainers and leaves orphaned data with no lifecycle.

**Fix:** Decide explicitly: either (a) fully remove `documentosEntregues` from `Cliente.java`, `ClienteUpdateRequest`/`Cliente` TS types, and the ficha page (requires a data migration decision for existing legacy rows), or (b) keep it intentionally as a read-only legacy display field and document that decision with a comment (similar to the `documentoTipoUnchanged` legacy-tolerance comment already present at `ResourceController.java:267-270`) so the next reader understands it's deliberate, not an oversight.

### WR-03: `datalist`/tipo combobox has no length/format validation before submission

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1300-1315`

**Issue:** The free-text `tipo` input backing the datalist combobox (`novoTipo` state) is sent to the backend unvalidated and untrimmed at the point of use (`upload.mutateAsync({ file: novoFicheiro, tipo: novoTipo, cliente_id: clienteId })` at line 1254) — contrast with `useUploadDocumentoComProgresso`'s internal `payload.tipo?.trim()` guard, which does trim, but only strips the value entirely if it's all-whitespace; there's no length cap, so an accidentally pasted long string becomes a `tipo` value with no upper bound, and no dedicated pre-submit trim/normalization on the input itself (e.g. two entries differing only by trailing whitespace or case will show as separate options in the datalist next time, since `tipoOptions` in the `useMemo` at lines 1236-1239 dedupes only by exact string equality via `Set`).

**Fix:** Trim `novoTipo` before both the datalist dedupe (`d.tipo?.trim()`) and the submit call, and consider a reasonable `maxLength` on the `<input>` to match whatever the backend's `tipo` column constraint is (if any).

## Info

### IN-01: `PlaceholderEmBreve` removal confirmed clean

**File:** n/a (verified via repo-wide search)

**Issue:** No informational issue — noted for completeness per the review brief. A repo-wide search for `PlaceholderEmBreve` across `web/` returns zero matches, confirming its removal did not leave any dangling imports or references. No action needed.

---

_Reviewed: 2026-07-06T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
