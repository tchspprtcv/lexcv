# Technology Stack

**Project:** LexCV — Módulo de Parecer Jurídico (Frontend UI), milestone v2.6
**Researched:** 2026-07-01

## Summary Verdict

**No new libraries are required.** Every UI capability needed for the Parecer Jurídico frontend (list/detail/create pages, version history, approval/delivery actions, advanced search, file upload for versão attachments) has a directly reusable pattern already implemented in the codebase for the Documentos, Processos, and Clientes modules. This milestone should be pure additive work following existing conventions — zero new npm packages.

## Recommended Stack (= existing stack, unchanged)

### Core Framework (already in place, do not touch)
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Next.js | 16.2.6 | App Router pages, route groups | Already the app's framework; `web/AGENTS.md` warns this version has breaking changes vs. training data — no new routing APIs needed here, just new pages under `(dashboard)/pareceres/` mirroring `(dashboard)/processos/` |
| React | 19.2.4 | UI runtime | Existing |
| TypeScript | ^5 (strict) | Type safety | Existing, `strict: true` — no `any` |
| Tailwind CSS | ^4 | Styling | Existing, utility classes only, no new theme tokens needed |

### Data Fetching
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| @tanstack/react-query | ^5.87.4 | All 12 `/api/v1/pareceres/*` calls | Same pattern as `use-documentos.ts` / `use-processos.ts` — `useQuery` for list/detail/search, `useMutation` for create/atribuir/versionar/aprovar/entregar, with `queryClient.invalidateQueries` on mutation success |

### Forms
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| react-hook-form | ^7.62.0 | Solicitação form, versão form, aprovação/entrega dialogs | Existing pattern across all modules |
| zod + @hookform/resolvers | ^4.1.5 / ^5.2.2 | Schema validation for new `schemas/pareceres.ts` | Mirror `schemas/clientes.ts` / `schemas/processos.ts` structure |

### UI Primitives (all already exist — reuse, do not re-add via shadcn CLI)
| Component | Location | Reused for |
|-----------|----------|------------|
| `card.tsx`, `table.tsx`, `badge.tsx`, `button.tsx` | `web/src/components/ui/` | List/detail layout, status badges (estado da solicitação: pendente/atribuído/em elaboração/aprovado/entregue) |
| `dialog.tsx`, `alert-dialog.tsx` | `web/src/components/ui/` | Create solicitação modal, confirm entrega (irreversible) |
| `sheet.tsx` | `web/src/components/ui/` | Mobile bottom-sheet for forms, per existing responsiveness pattern (v2.3) |
| `popover.tsx` + manual filter panel | `web/src/components/ui/` | Advanced search filters, same as Processos/Agenda filter popovers |
| `input.tsx`, `textarea.tsx`, `radio-group.tsx`, `switch.tsx`, `label.tsx` | `web/src/components/ui/` | Solicitação/versão/aprovação forms |

**No `Tabs` primitive exists in the repo.** The Processos detail page (`web/src/app/(dashboard)/processos/[id]/page.tsx`) implements tabs manually with local `useState<TabKey>` + conditional rendering + toggle `Button`s (`variant={tab === "x" ? "secondary" : "outline"}`), not a Radix Tabs component. **Reuse this exact pattern** for the parecer detail page (tabs: "Versões" / "Aprovação" / "Auditoria") rather than adding `@radix-ui/react-tabs`. This keeps the component inventory consistent and avoids introducing a second tab paradigm into the app.

## Existing Repo Patterns to Reuse Directly

### 1. File upload for versão attachments — reuse Documentos module pattern verbatim
`web/src/hooks/use-documentos.ts` already implements everything needed for parecer versão anexos:
- `useUploadDocumentoComProgresso` — raw `XMLHttpRequest` (not fetch) against `API_BASE`, `withCredentials: true`, `FormData`, `xhr.upload.onprogress` for a progress bar. This is required because `fetch` cannot reliably report upload progress; keep using XHR for the parecer versão upload mutation.
- `useDownloadDocumento` — calls a `/download` sub-resource that returns `{ url, expiresIn }` (MinIO pre-signed URL), then the caller opens/redirects to `url`. Apply the identical shape for the parecer versão attachment download route if the backend follows the same presigned-URL convention (confirm exact backend route name from the pareceres API surface — do not assume path parity, only response-shape parity, since the backend uses `StorageService`/MinIO for versão anexos per the v2.5 audit).
- Drag-and-drop upload chrome already exists in the Documentos upload dialog (v2.2 milestone) — reuse that component/visual pattern for the versão-attachment upload step rather than building a new dropzone.

**Do not add** a dropzone library (e.g. `react-dropzone`) — the existing native drag-and-drop + XHR implementation already covers this.

### 2. Timeline / audit tab — reuse Processos detail page pattern verbatim
`web/src/app/(dashboard)/processos/[id]/page.tsx` demonstrates the exact shape needed for parecer version history + audit trail:
- Local tab state (`TabKey` union type), manual button toggles, conditional render blocks per tab — no Tabs library.
- `useTimeline(id)` and `useAuditLog(id)` hooks (in `use-processos.ts`) each return a plain array (`TimelineItem[]`, `AuditLogEntry[]`) rendered as a chronological list with date-range filters (`timeline_date_from` / `timeline_date_to` inputs) and a type-filter `Set`.
- **For pareceres:** build `use-pareceres.ts` hooks `useParecerVersoes(id)` (version history — reuse the timeline list-rendering shape, since versions are inherently chronological/immutable) and `useParecerAuditoria(id)` (reuse `AuditLogEntry` rendering verbatim — the backend's audit log entity is the same `AuditLog` used by Processos per v2.5 audit notes, so the frontend type/rendering can likely be shared as-is, not reimplemented).
- RBAC gate on the Auditoria tab in Processos is `canManageProcessos` (derived from `hasScopedPermission`) — mirror with `hasScopedPermission(perms, "pareceres", "manage")` gating the Auditoria tab, and `"view"/"create"/"edit"` gating the rest, per `web/src/lib/permissions.ts` fallback chain already documented in CLAUDE.md.

### 3. Print-friendly / dedicated summary view — reuse Ficha Cliente pattern for the "parecer entregue" view
`web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` is the direct template for resolving the v2.5 audit gap PARC-09 (`versaoFinalId` set correctly but not surfaced through any dedicated view):
- `PRINT_CSS` constant with `@media print` rules hiding `aside, header, [data-print-hide], .bottom-nav, .ficha-print-btn` — copy verbatim into a new `pareceres/[id]/entregue/page.tsx` (or a print-mode section of the detail page).
- `<Printer />` icon button (lucide-react, already a dependency) calling `window.print()`, marked `data-print-hide` so it disappears in the print output itself.
- This gives a "parecer entregue" view that both resolves to a normal in-app page and doubles as a printable delivery record — exactly matching the office's need for physical/PDF handoff of a finished parecer, same as the client intake form is used today for Ficha Cliente.

### 4. Advanced search UI — reuse Processos/Agenda filter-popover pattern
The backend's `pesquisar()` (Phase 64) supports free-text + combined filters. The frontend should reuse the existing filter-popover UI convention (visible in the Processos list page and Agenda) built on `popover.tsx` + controlled `Input`/`Select`-style controls + a "clear filters" action, rather than introducing a new search-bar component or a client-side search library (no need for something like `fuse.js` — search is server-side via the existing `pesquisar()` endpoint).

## What NOT to Add

| Temptation | Why not | Use instead |
|------------|---------|-------------|
| `@radix-ui/react-tabs` or a shadcn Tabs component | Repo already has an established manual-tab-state pattern in Processos; adding a second tab paradigm fragments the component inventory | Copy the `TabKey` + button-toggle pattern from `processos/[id]/page.tsx` |
| `react-dropzone` or similar upload library | Documentos module already has working drag-and-drop + XHR progress upload | `useUploadDocumentoComProgresso`-style hook, adapted for the pareceres versão endpoint |
| A diff/comparison library (e.g. `jsdiff`) for "comparar versões" | Explicitly deferred per Phase 62 CONTEXT.md and the v2.5 audit (PARV-03 satisfied via sequential list/detail only, diff UI deliberately out of scope) | Sequential version list, most-recent-first, same as timeline rendering |
| A dedicated print/PDF library (e.g. `react-to-print`, `jspdf`) | `window.print()` + scoped `@media print` CSS already solves this for Ficha Cliente | Copy `PRINT_CSS` + `Printer` button pattern |
| A client-side full-text search library | Search is server-side (`pesquisar()` already combines free text + filters in the backend) | Debounced controlled input triggering the existing TanStack Query search hook |
| New date/calendar libraries | Existing `Input type="date"` fields used for timeline filters | Reuse the same native date input pattern |

## Installation

No installation required — all dependencies already present in `web/package.json`. This milestone adds only new source files, no new packages:
- New route files under `web/src/app/(dashboard)/pareceres/`
- New hook file `web/src/hooks/use-pareceres.ts`
- New type file `web/src/types/pareceres.ts`
- New schema file `web/src/schemas/pareceres.ts`
- Permission scope additions to `web/src/lib/permissions.ts` mirroring the already-seeded backend scope `pareceres:view/create/edit/manage`

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Tab navigation | Manual `useState` + button toggles (existing pattern) | Radix/shadcn Tabs | Would introduce a second UI paradigm for the same concept already solved in Processos |
| File upload | XHR-based hook, adapted from Documentos | `fetch` with `ReadableStream` progress | `fetch` upload-progress support is inconsistent/unavailable via plain `fetch` in the browsers this app targets; XHR is what's already proven working in this codebase |
| Version comparison UI | None (sequential list) | Side-by-side diff component | Explicitly deferred scope per backend Phase 62 decision; adding it now would exceed this milestone's UI-only mandate |
| Print output | `window.print()` + print CSS | `jspdf`/`react-to-print` | Zero new dependencies, proven pattern already shipped and audited (Ficha Cliente, v2.4) |

## Sources

- Direct repository inspection (HIGH confidence — primary source, not training data):
  - `web/src/hooks/use-documentos.ts` (upload/download patterns)
  - `web/src/app/(dashboard)/processos/[id]/page.tsx` (tab state, timeline, audit log rendering)
  - `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` (print pattern)
  - `web/src/components/ui/*.tsx` (component inventory — confirms no Tabs primitive exists)
  - `web/package.json` (confirmed dependency versions, no unused/available libraries for diff, dropzone, or PDF generation)
  - `.planning/v2.5-MILESTONE-AUDIT.md` (backend API surface, PARC-09 gap, PARV-03 deferred-diff decision)
  - `.planning/PROJECT.md` (milestone scope, constraints, target features)
