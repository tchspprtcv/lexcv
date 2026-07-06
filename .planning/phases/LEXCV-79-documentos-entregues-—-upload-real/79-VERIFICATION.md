---
phase: LEXCV-79-documentos-entregues-—-upload-real
verified: 2026-07-06T18:00:00Z
status: human_needed
score: 9/9 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Live upload flow: open a cliente ficha, switch to edit mode, open the Documentos Entregues tab, use the Adicionar dialog to select/drop a file and a tipo (existing or new), confirm upload"
    expected: "Progress bar advances during upload; on success a toast 'Documento enviado com sucesso.' appears, the dialog closes, and the new document appears in the list without a page reload"
    why_human: "Requires a running app + PostgreSQL + MinIO to exercise real network I/O, file storage, and browser rendering — not verifiable via static code trace"
  - test: "Live list/download/delete flow: with the tab already showing documents, click Download on a row and click the delete (✕) control, confirming the window.confirm dialog"
    expected: "Download opens the presigned MinIO URL in a new tab and serves the actual file; delete removes the row after confirmation and shows a success toast"
    why_human: "External storage service (MinIO) behavior and browser download/confirm dialogs cannot be exercised by static analysis"
  - test: "Read-mode vs edit-mode gating: view the ficha in read mode (not editing) with a documentos:edit-capable user"
    expected: "The Documentos Entregues list is visible, but the 'Adicionar' button and per-row delete (✕) controls are hidden; toggling to edit mode reveals them"
    why_human: "Conditional rendering based on runtime editable/RBAC state is best confirmed visually in a live session, though the code trace (editable && canEditDocumentos guards) supports this behavior"
---

# Phase 79: Documentos Entregues — Upload Real Verification Report

**Phase Goal:** O separador "Documentos Entregues" passa a gerir ficheiros carregados de facto, reutilizando o sistema genérico de Documentos
**Verified:** 2026-07-06T18:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `GET /clientes/{id}/documentos` returns only Documento records whose tenant matches the caller | VERIFIED | `ResourceController.java:2088-2096` — `listClienteDocumentos` looks up `Cliente` by id, 404s on tenant mismatch, then calls `documentoRepository.findByTenantIdAndClienteId(getTenantId(), id)`. Backend compiles and packages cleanly (`mvn -DskipTests package` = BUILD SUCCESS). |
| 2 | A request for a cliente in another tenant returns 404, not a cross-tenant document list | VERIFIED | Same method: `if (cliente == null \|\| !cliente.getTenantId().equals(getTenantId()))` → `404` with `Map.of("message", "Cliente não encontrado")`, guarding the query call — matches `listProcessoDocumentos`'s established idiom exactly. |
| 3 | The endpoint requires the `documentos:view` authority | VERIFIED | `@PreAuthorize("hasAuthority('documentos:view')")` annotates `listClienteDocumentos` (line 2088). |
| 4 | Utilizador vê a lista de ficheiros já carregados para o cliente no separador Documentos Entregues, em modo leitura e edição | VERIFIED | `ClienteDocumentosEntreguesTab` (page.tsx:1216-1383) calls `useDocumentos({ cliente_id: clienteId })` and renders the `<ul>` of documents unconditionally (not gated by `editable`) — loading/error/empty states all present. |
| 5 | Utilizador com `documentos:edit` e ficha em modo edição carrega um ficheiro via Dialog Adicionar, associado ao `clienteId` atual | VERIFIED | `onConfirmarUpload` calls `upload.mutateAsync({ file: novoFicheiro, tipo: novoTipo.trim(), cliente_id: clienteId })` via `useUploadDocumentoComProgresso`; Adicionar trigger gated by `editable && canEditDocumentos` (page.tsx:1280). FormData keys (`clienteId`, `tipo`, etc.) match backend `@RequestParam` names exactly (verified CR-01 fix). |
| 6 | O campo tipo do upload é um combobox (input list + datalist) alimentado pelos tipos distintos já usados nos documentos deste cliente | VERIFIED | `tipoOptions` derived via `Array.from(new Set(documentosData.map(d => d.tipo?.trim()).filter(Boolean)))`; rendered as native `<input list={...}>` + `<datalist>` (page.tsx:1237-1247, 1309-1322). Trimming fix (WR-03) confirmed present. |
| 7 | Utilizador descarrega um documento por link direto e remove-o com confirmação | VERIFIED (with implementation deviation from literal plan wording — see note) | Download uses `useDownloadDocumento` (presigned-URL hook) opened via `window.open(res.url, ...)`, not a raw `<a href>` as originally specified in the plan text — this was a deliberate review fix (CR-02) because the generic `/documentos/{id}/download` endpoint returns a JSON `{url, expiresIn}` body, not a raw file stream, so a literal `<a href>` would not have worked correctly. Delete uses `window.confirm("Apagar este documento?")` + `useDeleteDocumento` exactly as specified (page.tsx:1394-1425, 1447-1457). |
| 8 | A antiga secção de texto Documentos Entregues (state `documentosEntregues`/`newDocEntre`/`addDocEntreModal`) já não existe e o campo `documentosEntregues` já não é enviado no payload Guardar | VERIFIED | Zero matches for `documentosEntregues`/`newDocEntre`/`addDocEntreModal`/`confirmAddDocEntre`/`setDocumentosEntregues` in `clientes/[id]/page.tsx` (only the `"documentosEntregues"` tab-routing string literal remains). `onSubmit`'s `ClienteUpdateRequest` payload spread (`...values` from Zod schema + explicit fields) contains no `documentosEntregues`/`documentos_entregues` key; the Zod schema (`schemas/clientes.ts`) never declared it. |
| 9 | Dados antigos de documentos entregues (texto sem ficheiro) deixam de ser editáveis na nova UI; coluna fica órfã (sem migração) | VERIFIED | No migration/backfill script added (matches REQUIREMENTS.md "Out of Scope" — deliberate clean-cut, same pattern as `dados_tipo` in v2.7). The read-only print view `clientes/[id]/ficha/page.tsx` still displays the legacy field as text, but this is explicitly listed in REQUIREMENTS.md "Out of Scope" ("Ficha impressa ... Mantém-se inalterada — não faz parte do fluxo de pesquisa central desta milestone") — not a gap. |

**Score:** 9/9 truths verified (0 failed; 3 items additionally require live-environment human confirmation per below)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/.../ResourceController.java` — `listClienteDocumentos` | `@GetMapping("/clientes/{id}/documentos")` endpoint | VERIFIED | Present at line 2088-2096, tenant-scoped, gated by `documentos:view`, mirrors `listProcessoDocumentos`. |
| `web/src/hooks/use-documentos.ts` — `useDocumentos` queryFn | Repointed to `/clientes/{id}/documentos` when `cliente_id` present | VERIFIED | Lines 25-35: branches on `clienteId` truthiness; generic `/documentos` fallback retained; no `enabled` param added. |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` — `ClienteDocumentosEntreguesTab` | Lazy-mount sub-component + gated tab branch | VERIFIED | Defined at line 1216; rendered from `tab === "documentosEntregues"` branch (line 879-888) gated by `canViewDocumentos`/`AccessDeniedState`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `useDocumentos` | `GET /clientes/{id}/documentos` | queryFn cliente_id branch | WIRED | `apiFetch<Documento[]>(\`/clientes/${encodeURIComponent(clienteId)}/documentos\`)` called when `clienteId` truthy. |
| `tab === "documentosEntregues"` branch | `ClienteDocumentosEntreguesTab` | canViewDocumentos-gated render | WIRED | Ternary at page.tsx:879-888 confirmed. |
| `ClienteDocumentosEntreguesTab` | `useUploadDocumentoComProgresso` / `useDeleteDocumento` | immediate mutations, no Guardar payload | WIRED | `onConfirmarUpload`/`onDelete` call these hooks directly; neither touches the cliente `onSubmit` payload. |
| `listClienteDocumentos` | `documentoRepository.findByTenantIdAndClienteId` | tenant-scoped repository query | WIRED | Confirmed at ResourceController.java:2095; repository method pre-existing at `DocumentoRepository.java:11`. |
| `listClienteDocumentos` | `clienteRepository.findById` + tenant check | tenant-mismatch 404 guard | WIRED | Confirmed at ResourceController.java:2091-2094. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `ClienteDocumentosEntreguesTab` | `list.data` (Documento[]) | `useDocumentos({cliente_id})` → `GET /clientes/{id}/documentos` → `documentoRepository.findByTenantIdAndClienteId` (real JPA query, not a static return) | Yes | FLOWING |
| `ClienteDocumentoEntregueRow` | `documento.nome`/`tipo` + `wireDocumento.tamanho`/`createdAt` | Same query above; field-name mismatch (`size`/`created_at` in shared TS type vs actual `tamanho`/`createdAt` on the wire) resolved via an explicit unsafe-cast workaround documented in a code comment (WR-01 review fix) | Yes | FLOWING (workaround verified correct against `Documento.java` entity fields — no `@JsonProperty` renames exist, so `tamanho`/`createdAt` are the true wire field names) |
| Upload dialog `tipoOptions` (datalist) | `documentosData.map(d => d.tipo)` | Same `list.data`, deduped client-side | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with new endpoint | `cd backend && mvn -q -DskipTests compile` | Clean exit, no errors | PASS |
| Backend packages end-to-end | `cd backend && mvn -q -DskipTests package` | BUILD SUCCESS | PASS |
| Frontend builds with new tab/component | `cd web && pnpm build` | "Compiled successfully", all 23 routes generated including `/clientes/[id]` | PASS |
| Frontend lint shows no new issues | `cd web && pnpm lint` | 5 errors / 18 warnings — identical count to SUMMARY's claimed pre-existing baseline; none in files touched by this phase (`use-documentos.ts`, `clientes/[id]/page.tsx`, `ResourceController.java`) | PASS |
| Live upload/list/download/delete round-trip against a running app | N/A — no running app/DB/MinIO in this environment | Not executed | SKIP → routed to human verification |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| CLI-25 | 79-02 | "Documentos Entregues" passa a lista de ficheiros carregados, em vez de texto | SATISFIED | Legacy text section fully removed; `ClienteDocumentosEntreguesTab` lists real `Documento` records with filename/tipo/size/date. |
| CLI-26 | 79-02 | Upload reutiliza sistema genérico `Documento`/`/documentos/upload` com `clienteId` | SATISFIED | `useUploadDocumentoComProgresso` posts to `/documentos/upload` with `clienteId` form field; backend `uploadDocumento` handles it via the pre-existing generic endpoint (now with WR-04 tenant validation added). |
| CLI-27 | 79-01 | Novo endpoint de listagem de documentos por cliente | SATISFIED | `GET /clientes/{id}/documentos` added, tenant-scoped, `documentos:view` gated. |
| CLI-28 | 79-02 | Campo "tipo" no upload é combobox — escolher tipo existente ou escrever novo | SATISFIED | Native `<input list>` + `<datalist>` sourced from distinct `tipo` values of this client's documents; free text also accepted. |
| CLI-29 | 79-02 | Dados antigos de documentos entregues deixam de ser editáveis na nova UI; coluna fica órfã (sem migração) | SATISFIED | Legacy state/handlers/JSX/payload field fully removed from the editable ficha; no migration added (matches REQUIREMENTS.md "Out of Scope" directive); print-only ficha view retaining the old text field is explicitly out of scope per REQUIREMENTS.md. |

All 5 requirement IDs (CLI-25 through CLI-29) declared in PLAN frontmatter are accounted for and satisfied. No orphaned requirements — REQUIREMENTS.md traceability table maps exactly these 5 IDs to Phase 79 and no others.

Note: REQUIREMENTS.md's checkbox list (lines 34-38) and traceability table (lines 78-82) still show these as unchecked `[ ]`/"Pending" — this is a documentation-staleness issue (the table should be updated to "Complete" now that the phase is implemented and reviewed), not a code implementation gap.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | None found | — | Grep for TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER/"not yet implemented" across all three phase-modified files returned zero true matches (only legitimate HTML `placeholder` attributes and an unrelated `toDocumentoTipo` function name substring match). |

### Human Verification Required

### 1. Live upload flow

**Test:** Open a cliente ficha in edit mode, open the Documentos Entregues tab, use the "Adicionar" dialog to select a file and a tipo (existing or freshly typed), confirm the upload.
**Expected:** Progress bar advances during upload; on success a toast "Documento enviado com sucesso." appears, the dialog closes, and the new document appears in the list without manual refresh (via TanStack Query invalidation).
**Why human:** Requires a running app + PostgreSQL + MinIO to exercise real network I/O, multipart file handling, and browser rendering — the code paths are traced and consistent with the plan/spec, but the actual round trip was not executed in this environment (no DB/MinIO available), and was explicitly deferred per both SUMMARY.md files ("no browser/backend environment was available in this worktree").

### 2. Live list/download/delete flow

**Test:** With documents already listed, click "Download" on a row, and separately click the delete (✕) control and confirm the `window.confirm` dialog.
**Expected:** Download opens the MinIO presigned URL in a new tab and serves the real file; delete removes the row after confirmation and shows a success toast, refetching the list.
**Why human:** External storage service (MinIO) behavior and native browser confirm/download dialogs are outside static-analysis reach.

### 3. Read-mode vs edit-mode gating

**Test:** View a cliente ficha in read mode (not editing) as a user with `documentos:edit`, then toggle into edit mode.
**Expected:** The Documentos Entregues list is visible in both modes; the "Adicionar" button and per-row delete controls appear only in edit mode.
**Why human:** Visual/conditional-rendering confirmation is best done in a live session, though the `editable && canEditDocumentos` guards in the code (verified statically) support this being correct.

### Gaps Summary

No blocking gaps found. Backend endpoint, frontend hook repointing, new upload/list/download/delete tab, and legacy section removal are all implemented, wired, and verified against the actual codebase (not just SUMMARY claims). Backend compiles and packages cleanly; frontend builds cleanly with no new lint issues. The prior code review (79-REVIEW.md) closed 3 critical + 4 warning findings across two fix rounds and the final re-review is clean (0 critical, 0 warning, 1 info-only note that is explicitly a non-issue).

One deliberate, review-driven implementation deviation from the plan's literal wording was found and judged correct: the download control uses the existing `useDownloadDocumento` presigned-URL hook instead of a raw `<a href>` anchor, because the underlying `/documentos/{id}/download` endpoint returns a JSON body (`{url, expiresIn}`), not a raw file stream — a literal anchor tag would not have worked. This is documented in the codebase's own commit history (`6ed25d3 fix(79): CR-02 use useDownloadDocumento hook instead of raw anchor for cliente doc row download`) and confirmed correct by tracing the actual backend endpoint. No override is needed since this does not fail the underlying truth ("utilizador descarrega um documento") — it fulfills it correctly, just via a different (and more correct) mechanism than literally specified.

The only reason this phase is not marked `passed` is the presence of three items that legitimately require a live running environment (upload/download/delete round trip, read/edit mode visual gating) — these were already flagged as deferred-to-human-verification in both plan `<verification>` sections and both SUMMARY.md files. No further code changes are indicated; this is a request for human confirmation, not a gap requiring rework.

---

_Verified: 2026-07-06T18:00:00Z_
_Verifier: Claude (gsd-verifier)_
