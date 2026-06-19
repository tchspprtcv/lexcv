---
phase: 51-frontend-upload-component
reviewed: 2026-06-19T00:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - web/src/components/shared/file-drop-zone.tsx
  - web/src/hooks/use-documentos.ts
  - web/src/app/(dashboard)/documentos/novo/page.tsx
  - web/src/app/(dashboard)/documentos/[id]/page.tsx
findings:
  critical: 1
  warning: 3
  info: 1
  total: 5
status: issues_found
---

# Phase 51: Code Review Report

**Reviewed:** 2026-06-19T00:00:00Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Four files were reviewed: the new `FileDropZone` component, the `use-documentos` hook (with new XHR-based upload and simplified download), and two page components. Security surface is small — XHR credentials are correctly set, presigned URLs are opened with `noopener,noreferrer`, and there are no new API endpoints.

One critical logic bug was found: the `useEffect` cleanup in the upload page runs on every file selection (not only unmount), revoking the object URL for the preview that was just created. This makes the preview render a broken image/iframe on every file pick after the first. Three warnings address: the progress bar persisting after a failed upload, XHR treating 3xx redirects as success, and drag-leave firing false-negatives on child-element entry. One informational note on the missing XHR abort.

---

## Critical Issues

### CR-01: `useEffect` dependency causes immediate revocation of the active preview URL

**File:** `web/src/app/(dashboard)/documentos/novo/page.tsx:48-52`

**Issue:** The cleanup function inside `useEffect` closes over `preVisualizacao` and revokes its URL. Because `[preVisualizacao]` is listed as a dependency, React runs the cleanup whenever `preVisualizacao` changes — i.e. every time the user selects a new file. The sequence is:

1. User picks file A → `handleFicheiroSelecionado` revokes any old URL, creates new URL_A, calls `setPreVisualizacao({url: URL_A, ...})`.
2. React re-renders with URL_A in state.
3. React runs the previous effect cleanup: revokes URL_A (already gone — no bug yet on first pick).
4. User picks file B → new URL_B created, `setPreVisualizacao({url: URL_B, ...})`.
5. React re-renders with URL_B.
6. React runs cleanup for the previous effect (which closed over the state containing URL_A — **but `preVisualizacao` in the closure now points to the latest ref, URL_B**). This immediately revokes URL_B, breaking the preview.

In practice, after the second file selection the `<img>` or `<iframe>` shows a broken resource because the object URL it references has already been revoked.

The intent is unmount-only cleanup. The correct pattern is a ref for the current URL, combined with an empty dependency array:

```tsx
const previewUrlRef = React.useRef<string | undefined>(undefined);

// In handleFicheiroSelecionado, replace setPreVisualizacao logic with:
const handleFicheiroSelecionado = (file: File) => {
  if (previewUrlRef.current) {
    URL.revokeObjectURL(previewUrlRef.current);
    previewUrlRef.current = undefined;
  }
  // ... determine tipo ...
  const url = tipo !== "outro" ? URL.createObjectURL(file) : undefined;
  previewUrlRef.current = url;
  setPreVisualizacao({ tipo, url, nome: file.name, tamanho: file.size });
};

// Replace the useEffect with:
React.useEffect(() => {
  return () => {
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
  };
}, []); // empty deps — runs cleanup only on unmount
```

Also update the success path (line 91) to use `previewUrlRef.current` instead of `preVisualizacao?.url`.

---

## Warnings

### WR-01: Progress bar stays visible after upload failure

**File:** `web/src/app/(dashboard)/documentos/novo/page.tsx:94-98`

**Issue:** In the `catch` block of `onSubmit`, `setProgresso(null)` is never called. If the XHR upload fails after making partial progress, the progress bar (`progresso !== null` block, line 159) remains rendered in whatever percentage it was at. The user sees a frozen progress bar alongside the error toast, with no way to dismiss it without reloading.

**Fix:**
```tsx
} catch (e) {
  setProgresso(null); // add this line
  const msg = e instanceof Error ? e.message : "Erro ao fazer upload";
  setServerError(msg);
  toast.error(msg);
}
```

---

### WR-02: XHR success check (`status < 400`) accepts 3xx redirect responses as success

**File:** `web/src/hooks/use-documentos.ts:122`

**Issue:** `if (xhr.status < 400)` treats HTTP 3xx responses as success. If the backend ever issues a redirect (e.g. 302 on session expiry sending the user to a login page), the code calls `JSON.parse(xhr.responseText)` on the HTML redirect body, catches the parse error, and rejects with the opaque message "Resposta inválida do servidor" — hiding the real cause (expired session / 302). Limiting the success range to 2xx makes errors actionable.

**Fix:**
```ts
xhr.onload = () => {
  if (xhr.status >= 200 && xhr.status < 300) {
    try {
      resolve(JSON.parse(xhr.responseText) as DocumentoUploadResponse);
    } catch {
      reject(new Error("Resposta inválida do servidor"));
    }
  } else {
    reject(new Error(`API ${xhr.status}`));
  }
};
```

---

### WR-03: `dragLeave` fires when pointer moves over a child element, collapsing the drop highlight

**File:** `web/src/components/shared/file-drop-zone.tsx:28-31`

**Issue:** `handleDragLeave` unconditionally sets `isDragging = false`. When the user drags a file over the text inside the drop zone (`<button>` or `<p>` children), the browser fires `dragLeave` on the parent div and `dragEnter` on the child. The net effect is a rapid flicker: the blue-border highlight disappears every time the pointer crosses into any child element.

The standard fix is to check `relatedTarget` — if the element being entered is still inside the drop zone, the leave should be ignored:

```tsx
const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
  e.preventDefault();
  e.stopPropagation();
  // Only clear when leaving the drop zone entirely
  if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
  setIsDragging(false);
};
```

---

## Info

### IN-01: XHR has no abort mechanism — upload cannot be cancelled on unmount

**File:** `web/src/hooks/use-documentos.ts:111-135`

**Issue:** The `XMLHttpRequest` created in `mutationFn` is not exposed or stored anywhere. If the user navigates away while an upload is in progress, the component unmounts but the XHR continues until the server closes the connection. The TanStack mutation's `reset()` or `cancel()` has no effect on the underlying network request. This is not a correctness bug (the server will complete or time out) but means bandwidth is wasted and a spurious cache invalidation (`onSuccess`) may fire after the component is gone.

**Fix (optional for now):** Expose the XHR via a `React.useRef` at the hook call-site, or use TanStack's `meta` pattern to store the abort handle, and call `xhr.abort()` in a `useEffect` cleanup.

---

_Reviewed: 2026-06-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
