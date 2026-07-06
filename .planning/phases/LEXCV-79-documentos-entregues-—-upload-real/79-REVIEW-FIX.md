---
phase: LEXCV-79-documentos-entregues-—-upload-real
fixed_at: 2026-07-06T16:45:00Z
review_path: .planning/phases/LEXCV-79-documentos-entregues-—-upload-real/79-REVIEW.md
iteration: 1
findings_in_scope: 6
fixed: 4
skipped: 2
status: partial
---

# Phase LEXCV-79: Code Review Fix Report

**Fixed at:** 2026-07-06T16:45:00Z
**Source review:** .planning/phases/LEXCV-79-documentos-entregues-—-upload-real/79-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 6 (3 critical, 3 warning — `fix_scope: critical_warning`, IN-01 excluded)
- Fixed: 4 (CR-01, CR-02, WR-01, WR-03)
- Skipped: 2 (CR-03, WR-02 — both intentional, documented below)

## Fixed Issues

### CR-01: Upload FormData field names don't match backend `@RequestParam` names

**Files modified:** `web/src/hooks/use-documentos.ts`
**Commit:** bbac022
**Applied fix:** Changed `form.set("processo_id", ...)` → `form.set("processoId", ...)` and `form.set("cliente_id", ...)` → `form.set("clienteId", ...)` in both `useUploadDocumento` and `useUploadDocumentoComProgresso`. Verified against the actual backend `@RequestParam(value = "processoId", ...)`/`@RequestParam(value = "clienteId", ...)` declarations in `ResourceController.java:1986-1987`, which matched the review exactly. No other code depends on the old snake_case FormData keys — this is an internal fetch-to-backend wire format, not a shared JSON contract.

### CR-02: Download link opens a JSON payload instead of the file

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** 6ed25d3
**Applied fix:** Replaced the raw `<a href="/api/v1/documentos/{id}/download">` anchor in `ClienteDocumentoEntregueRow` with the existing `useDownloadDocumento` hook, matching the `ProcuracaoCard` pattern already present in the same file (`download.mutateAsync()` → `window.open(res.url, "_blank", "noopener,noreferrer")`), with a toast on failure. Added the `useDownloadDocumento` import.

### WR-01: `Documento` field name mismatch — size/date render as `NaN`/blank

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` (same commit as CR-02, since both touched `ClienteDocumentoEntregueRow`)
**Commit:** 6ed25d3
**Applied fix:** Confirmed via `backend/src/main/java/com/lexcv/models/Documento.java` that the entity has no Jackson naming override and serializes with its Java field names (`tamanho`, `createdAt`, `mimeType`), not the frontend `Documento` type's declared shape (`size`, `created_at`, `content_type`). This mismatch is systemic across the whole documentos feature (also present in `web/src/app/(dashboard)/documentos/page.tsx` and `web/src/app/(dashboard)/documentos/[id]/page.tsx`, which are outside this phase's file scope), so per the review's own preference for "the smaller, more localized change," I did not widen the shared `Documento` type or add a backend DTO (which would require touching 6 controller endpoints, several outside phase 79's diff). Instead, `ClienteDocumentoEntregueRow` now reads the actual wire fields (`tamanho`, `createdAt`) via a locally-scoped cast, with a comment explaining why the shared type isn't touched. This fixes the "NaN MB" / blank-date rendering in the new tab without expanding scope into the pre-existing generic `/documentos` pages.

### WR-03: `tipo` combobox has no trim/normalization before submit or dedupe

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** c9ded7f
**Applied fix:** Trimmed `d.tipo` in the `tipoOptions` dedupe memo (using a type-narrowing filter instead of `Boolean` cast) and trimmed `novoTipo` at the point of use in `onConfirmarUpload`'s `upload.mutateAsync(...)` call. Left `maxLength` unaddressed — no backend column-length constraint was found for `tipo` to align to, and adding an arbitrary cap wasn't part of the core defect (duplicate options from whitespace-only differences), so it was left out to keep the change minimal.

## Skipped Issues

### CR-03: Generic `GET /documentos` ignores `processo_id`/`cliente_id` query params

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2057-2061`
**Reason:** Confirmed via `web/src/hooks/use-documentos.ts` that the new cliente-scoped tab (`ClienteDocumentosEntreguesTab`) never hits this code path — `useDocumentos` takes the `GET /clientes/{id}/documentos` branch whenever `cliente_id` is set, which is exactly the dedicated endpoint this phase added specifically because the generic endpoint ignores filters. This is a pre-existing gap in the generic `/documentos` search page (`web/src/app/(dashboard)/documentos/page.tsx`), not introduced or newly exercised by phase 79's diff, and fixing it properly (adding real query filtering or routing through `GET /processos/{id}/documentos`) would expand scope into the generic documentos page/backend listing endpoint, which this phase deliberately routed around rather than touched. Recorded here as an accepted pre-existing gap for a future phase to address, per the phase's own scoping decision.

**Original issue:** `listDocumentos()` declares no `@RequestParam`s and always returns the full tenant document set regardless of query string, which can expose documents belonging to unrelated clientes/processos within the same tenant to any user with `documentos:view` filtering by a specific `processo_id`.

### WR-02: Legacy `documentos_entregues` field left live in types/entity/ficha page

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:293`, `web/src/types/clientes.ts:56,104,109`, `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx:154-156,213`
**Reason:** `79-CONTEXT.md` explicitly locks this as a deliberate decision: "Migração de dados antigos de 'documentos entregues' — corte limpo deliberado, decisão da milestone (v2.8)" (deferred section), and "O campo `documentos_entregues` deixa de ser enviado no payload de 'Guardar' — o backend não recebe mais este campo do frontend; a coluna/campo backend fica órfã, sem processo de migração (mesmo padrão usado para `dados_tipo` na v2.7)." The editable form correctly no longer sends this field (confirmed — no reference in the submit handler), and the ficha page printing the last-known legacy value is consistent with "corte limpo" (clean cut, no migration) rather than a bug. Forcing removal of the entity field, TS types, or ficha rendering would contradict this phase's own locked scope decision. Not fixed, per explicit instruction to respect locked scope decisions over the review's option (a)/(b) suggestion.

---

_Fixed: 2026-07-06T16:45:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
