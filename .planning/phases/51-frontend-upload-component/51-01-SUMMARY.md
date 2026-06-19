---
phase: "51"
plan: "01"
subsystem: frontend-documentos
tags: [upload, download, drag-and-drop, presigned-url, progress, preview]
dependency_graph:
  requires: [50-02]
  provides: [FileDropZone, useUploadDocumentoComProgresso, useDownloadDocumento-v2]
  affects: [documentos/novo, documentos/[id]]
tech_stack:
  added: []
  patterns: [XHR-onprogress, createObjectURL, DataTransfer-FileList, window-open-presigned-url]
key_files:
  created:
    - web/src/components/shared/file-drop-zone.tsx
  modified:
    - web/src/hooks/use-documentos.ts
    - web/src/app/(dashboard)/documentos/novo/page.tsx
    - web/src/app/(dashboard)/documentos/[id]/page.tsx
decisions:
  - FileDropZone renders both a hidden input and a visible button so drag-drop and click-to-open work independently
  - useUploadDocumentoComProgresso uses raw XHR because fetch/apiFetch does not expose upload progress
  - useDownloadDocumento simplified to a plain apiFetch GET returning JSON presigned URL — no blob handling
  - Object URLs revoked both on unmount (useEffect cleanup) and immediately after successful upload
metrics:
  duration: ~25m
  completed: "2026-06-19"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 51 Plan 01: Frontend Upload Component Summary

**One-liner:** Native drag-and-drop FileDropZone with XHR upload progress, inline image/PDF preview, and presigned-URL download replacing the old blob stream.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Criar FileDropZone e reescrever hooks | d8e9228 | file-drop-zone.tsx, use-documentos.ts |
| 2 | Atualizar página de upload e página de detalhe | 865b157 | documentos/novo/page.tsx, documentos/[id]/page.tsx |

## What Was Built

**FileDropZone (`web/src/components/shared/file-drop-zone.tsx`):** Reusable component with `onDragEnter`/`onDragOver`/`onDragLeave`/`onDrop` handlers and a hidden `<input type="file">` opened by a visible button. Uses `isDragging` local state to toggle a blue dashed border highlight. No external libraries.

**`useUploadDocumentoComProgresso`:** New hook wrapping `XMLHttpRequest` with `xhr.withCredentials = true` and `xhr.upload.onprogress` to fire a caller-supplied `onProgress(pct)` callback with 0–100 percentages. Resolves/rejects as a Promise to integrate cleanly with TanStack `useMutation`. On success, invalidates `["documentos", "list"]`.

**`useDownloadDocumento` (rewritten):** Replaced 30-line blob/Content-Disposition stream logic with a single `apiFetch<{ url: string; expiresIn: number }>` call. The mutation result now carries `url` directly.

**`/documentos/novo`:** Replaced `<Input type="file">` with `<FileDropZone>`. Added `handleFicheiroSelecionado` that populates the form field via `DataTransfer`, determines preview type, and creates an object URL. Preview renders `<img>` for images, `<iframe>` for PDFs, filename+size text for others. Progress bar appears while `progresso !== null`. Object URLs revoked on unmount and after submit.

**`/documentos/[id]`:** Replaced 6-line blob anchor-click download with `window.open(res.url, "_blank", "noopener,noreferrer")`.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints or auth paths introduced. Object URLs are local-only and revoked. Presigned URL opened with `noopener,noreferrer`.

## Self-Check: PASSED

- `web/src/components/shared/file-drop-zone.tsx` — FOUND
- `web/src/hooks/use-documentos.ts` — FOUND (modified)
- `web/src/app/(dashboard)/documentos/novo/page.tsx` — FOUND (modified)
- `web/src/app/(dashboard)/documentos/[id]/page.tsx` — FOUND (modified)
- Commit d8e9228 — FOUND
- Commit 865b157 — FOUND
- `pnpm build` — PASSED
