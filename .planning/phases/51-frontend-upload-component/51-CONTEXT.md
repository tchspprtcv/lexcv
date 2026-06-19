# Phase 51: Frontend Upload Component — Context

**Gathered:** 2026-06-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Update three things in the frontend to match the Phase 50 backend contract change:

1. **`useDownloadDocumento` hook** — currently downloads a binary blob; must change to call `GET /documentos/{id}/download`, parse the JSON `{"url":"...","expiresIn":3600}` response, and open the URL directly (`window.open(url, "_blank")`) instead of creating a blob URL. No binary ever touches Next.js.
2. **Upload page (`/documentos/novo`)** — add progress bar (using XHR `onprogress`), drag-and-drop on the file input area, and inline preview (image/PDF) before submit.
3. **Documentos detail page** — update `onDownload` to work with the new hook response shape (no more `res.blob`/`res.filename`).

All other modules are unaffected.
</domain>

<decisions>
## Implementation Decisions

### Download hook
- Change `useDownloadDocumento` to: fetch JSON → extract `url` → open in new tab with `window.open(url, "_blank")`. Remove all blob/FileSystemResource/parseContentDispositionFilename logic.
- The hook mutation returns `void` (or `{ url: string; expiresIn: number }`) — the detail page's `onDownload` opens the tab, no URL manipulation needed in the page itself.

### Upload progress
- Use XHR (`XMLHttpRequest`) instead of `fetch` — `fetch` does not expose upload progress natively.
- Track `xhr.upload.onprogress` → derive percentage → show in a `<progress>` bar or a styled div under the file input.
- Keep the existing `useUploadDocumento` hook signature; add a new `useUploadDocumentoWithProgress` variant (or extend the existing one with an `onProgress` callback option) — whichever fits more cleanly in `use-documentos.ts`.
- Progress bar is only shown while upload is in flight; resets to hidden after completion.

### Drag-and-drop
- Implement drag-and-drop on the file input area using `onDragOver` / `onDrop` React events on a wrapper `<div>`.
- Extract a reusable `<FileDropZone>` component in `web/src/components/shared/file-drop-zone.tsx`.
- `FileDropZone` wraps the existing `<Input type="file">` and handles: hover state (border highlight), `dragover` prevention of default, `drop` event to call `react-hook-form`'s `setValue("file", e.dataTransfer.files)`.
- No external drag-and-drop library — native browser APIs only.

### Inline preview
- After file selection (or drop), show a preview:
  - **Images** (PNG, JPG, GIF, WEBP): `URL.createObjectURL(file)` → `<img>` element.
  - **PDF**: `URL.createObjectURL(file)` → `<iframe>` with `height: 300px`.
  - **Other types**: show filename + size badge only.
- Revoke object URL on component unmount or when file changes.
- Preview is shown inside the upload form, above the submit button.

### No new libraries
- No react-dropzone, no pdf.js, no axios — use XHR for progress and native browser APIs for preview.
- Existing shadcn/ui primitives only.
</decisions>

<code_context>
## Existing Code

### Files to modify
- `web/src/hooks/use-documentos.ts` — `useDownloadDocumento` (binary blob → JSON presigned URL); optionally add `useUploadDocumentoWithProgress`
- `web/src/app/(dashboard)/documentos/novo/page.tsx` — add `<FileDropZone>`, progress bar, inline preview
- `web/src/app/(dashboard)/documentos/[id]/page.tsx` — update `onDownload` to handle new hook shape (open tab instead of blob download)

### New file
- `web/src/components/shared/file-drop-zone.tsx` — drag-and-drop + file input wrapper component

### Current upload flow (to preserve)
- Form uses `react-hook-form` with `documentoUploadFormSchema` (Zod)
- `useUploadDocumento()` builds a `FormData` and calls `apiFetch("/documentos/upload", { method: "POST", body: form })`
- On success: `toast.success()` + `router.push("/documentos/{id}")`

### Download API contract (NEW after Phase 50)
- `GET /api/v1/documentos/{id}/download` → `200 { url: string; expiresIn: number }`
- Previous contract was binary stream with `Content-Disposition` header — now removed

### Design system
- Tailwind + shadcn/ui primitives in `web/src/components/ui/`
- Existing classes: `border-2 border-dashed` for drop zone style (to be added)
- Dark mode: use `dark:` variants consistent with existing patterns in the project
</code_context>

<deferred>
## Deferred

- Chunked/resumable upload (TUS protocol) — not needed for current file sizes
- S3 direct multipart upload from browser — bypasses backend auth, out of scope
- Preview for DOCX/ODT — not possible without a library
</deferred>
