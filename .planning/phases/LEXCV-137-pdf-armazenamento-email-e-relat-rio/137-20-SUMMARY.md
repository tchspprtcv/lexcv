---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 20
subsystem: web/data-layer & shared download buttons
tags: [attachment-download, blob, rbac, hooks, react-query, download-buttons]
requires: [137-12, 137-15, 137-16, 137-18]
provides:
  - apiFetchFicheiro (additive attachment fetch with Content-Disposition parsing)
  - guardarFicheiro (browser Object URL anchor download)
  - PERMISSAO_REENVIAR_EMAIL, podeReenviarEmail (exact financeiro:edit gate)
  - useReenviarEmail, useDescarregarPdf, useDescarregarXml, useExportarMesCsv
  - DescarregarDocumentoBotoes (full and compact download buttons with independent spinners and UI-SPEC toasts)
affects: [137-21, 137-22]
tech-stack:
  added: []
  patterns: [additive apiFetchFicheiro, exact hasPermission without fallback, TanStack Query mutation wrappers, Blob + Object URL trigger]
key-files:
  created:
    - web/src/lib/guardar-ficheiro.ts
    - web/src/components/shared/descarregar-documento-botoes.tsx
  modified:
    - web/src/lib/api.ts
    - web/src/lib/api.test.ts
    - web/src/hooks/use-faturacao.ts
    - web/src/lib/entrega-email.test.ts
decisions:
  - "apiFetchFicheiro uses the shared error handling from apiFetch, preserving all ApiError properties and semToastParaStatus options"
  - "Content-Disposition parser supports standard filename=\"...\" and RFC 5987 filename*=UTF-8''... forms, returning null when missing"
  - "podeReenviarEmail enforces exact financeiro:edit without scoped fallback, matching the backend @PreAuthorize"
  - "DescarregarDocumentoBotoes supports full outline buttons and compact icon buttons with Tooltips and separate pending states"
metrics:
  duration: ~20min
  completed: 2026-10-07
  tasks: 2
  files: 6
---

# Phase 137 Plan 20: Web data layer and shared download buttons Summary

Added the client-side data layer for Phase 137 and the shared PDF/XML download buttons component.

## Key Deliverables

1. **`apiFetchFicheiro` in `web/src/lib/api.ts`**: Additive fetch wrapper that shares the exact error/toast pipeline with `apiFetch`, returning `{ blob, nomeFicheiro }` with filename parsed from `Content-Disposition`.
2. **`guardarFicheiro` in `web/src/lib/guardar-ficheiro.ts`**: Browser utility for triggering attachment downloads via temporary anchor and object URLs.
3. **Exact Resend Gate**: `PERMISSAO_REENVIAR_EMAIL = "financeiro:edit"` and `podeReenviarEmail` in `use-faturacao.ts`, tested against fallback over-privilege.
4. **Phase 137 Hooks**:
   - `useReenviarEmail`: POST `/documentos-fiscais/{id}/email/reenviar` with status suppression and cache invalidation.
   - `useDescarregarPdf`: GET `/documentos-fiscais/{id}/pdf` redirecting to presigned URL.
   - `useDescarregarXml`: GET `/documentos-fiscais/{id}/xml` saving attachment with server name or fallback.
   - `useExportarMesCsv`: GET `/documentos-fiscais/exportacao-mensal?mes=...` saving monthly CSV.
5. **Shared Component `DescarregarDocumentoBotoes`**: Renders PDF/XML download buttons with independent spinners and UI-SPEC toast error copy.

## Verification

- `vitest run src/lib/api.test.ts src/lib/entrega-email.test.ts`: **36/36 passed**.
- `tsc --noEmit`: Clean.
- Grep gate: `hasScopedPermission` is absent from executable code in `use-faturacao.ts`.

## Self-Check: PASSED
