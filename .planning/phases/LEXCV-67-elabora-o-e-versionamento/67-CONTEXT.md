# Phase 67: Elaboração e Versionamento - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning

<domain>
## Phase Boundary

The advogado responsável drafts the actual parecer by submitting successive, immutable versions (resumo/conteúdo + attachment) from the solicitação detail page. Depends on Phases 65/66 (types/hooks/list/detail/create already exist). Does NOT include aprovação (out of scope for milestone), entrega, or the "Parecer Entregue" view (Phase 68), or pesquisa (Phase 69).

</domain>

<decisions>
### Version Creation Form
- Location: inline on `/pareceres/[id]` detail page (Phase 65's detail page), not a separate route — a "Nova Versão" form/section, visible only when the solicitação is not yet `CONCLUIDO` and the current user is the `advogadoId` or an ADMIN (matches backend's `isAdmin || isResponsavel` check in `createVersao`).
- Fields: `conteudo` (textarea, required — user clarified this is a **resumo/summary**, not the full parecer text) and `file` (attachment, **required in this UI** — stricter than backend, which treats it as optional; the form must block submission without a file).
- Submits as `multipart/form-data` to `POST /pareceres/solicitacoes/{solicitacaoId}/versoes` with fields `conteudo` (string) and `file` (binary) — confirmed exact param names from `ParecerController.createVersao`.
- Upload UX: reuse `web/src/components/shared/file-drop-zone.tsx` (`FileDropZone`) verbatim, plus a progress-bar pattern modeled on `useUploadDocumentoComProgresso` (XHR-based) from `use-documentos.ts` — but implemented as a new `useCreateParecerVersao` hook (not the Documentos hook itself; Parecer versão has different fields/endpoint).
- On success: invalidate `["pareceres", "versoes", solicitacaoId]` (timeline) AND `["pareceres", "detail", solicitacaoId]` (status may have changed — backend doesn't auto-transition status on version-create beyond the initial `atribuir`, but invalidate defensively) AND `["pareceres", "list"]` (status badge visibility). Clear the form (reset conteudo + file + progress) after success, do not navigate away — stay on the detail page so the new version appears in the timeline immediately.

### Immutability in the UI
- No edit/delete affordance for any existing version — timeline entries remain strictly read-only display (already true from Phase 65).
- The version creation form itself disappears/is disabled once `status === "CONCLUIDO"` (entregue) — reinforces immutability post-entrega, anticipating Phase 68's entrega action.

### Claude's Discretion
- Exact placement/visual treatment of the "Nova Versão" form on the detail page (e.g., a card below the timeline vs. above it) — implementation detail.
- Whether to show a running character count for `conteudo` (resumo) or just a plain textarea — cosmetic, not load-bearing.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/shared/file-drop-zone.tsx` (`FileDropZone`) — drag-and-drop file input, generic and unstyled to a specific domain; reuse directly.
- `web/src/app/(dashboard)/documentos/novo/page.tsx` (lines ~14-15, 37, 41, 96-104, 143-149) — exact wiring pattern: `useState<number|null>` for progress, `useUploadDocumentoComProgresso({onProgress})`-style hook shape, `<FileDropZone>` usage, progress reset on success/error. Port this shape for a new `useCreateParecerVersao` hook rather than reusing the Documentos hook directly (different endpoint/fields).
- `web/src/hooks/use-documentos.ts#useUploadDocumentoComProgresso` — XHR + FormData + `onprogress` pattern to replicate (not import) for the new hook, since it POSTs to a different, parecer-specific endpoint.
- Phase 65's `/pareceres/[id]/page.tsx` — existing detail page and timeline rendering; this phase adds a form section to that same page (edit, not new file).
- Phase 66's RBAC/permission-gating pattern (`hasScopedPermission(perms, "pareceres", "edit")` for the create-version action, since `POST .../versoes` requires `pareceres:edit` per `ParecerController.java`).

### Established Patterns
- Multipart uploads use `FormData` + raw `XMLHttpRequest` (not `apiFetch`) when progress reporting is needed, exactly as in `use-documentos.ts`.
- Query key invalidation cascades across list/detail/nested-resource namespaces after a mutation that could affect any of them.

### Integration Points
- Backend: `POST /api/v1/pareceres/solicitacoes/{solicitacaoId}/versoes`, `consumes = MULTIPART_FORM_DATA_VALUE`, `@PreAuthorize("hasAuthority('pareceres:edit')")`, plus an instance-level check (`isAdmin || isResponsavel`) inside the handler returning 403 otherwise — confirmed in `ParecerController.createVersao` (lines ~404-487 in the source read during Phase 65 planning).
- Response: the created `ParecerVersao` (201), fields `id, tenantId, solicitacaoId, numeroVersao, conteudo, caminhoAnexo, criadoPorId, createdAt, aprovado, aprovadoPorId, aprovadoEm` — pure camelCase, same as confirmed in Phase 65.

</code_context>

<specifics>
## Specific Ideas

User explicitly stated: "conteúdo é apenas um resumo, o anexo é obrigatório" — this is now the locked interpretation of the `conteudo` field's role (a short summary of the version, not the full legal opinion text) and of the attachment's UI-level requiredness. Reflected verbatim in REQUIREMENTS.md PARV-05.

</specifics>

<deferred>
## Deferred Ideas

- Aprovação interna (ADMIN) — out of milestone scope entirely (PARC-17, v2.7).
- Entrega action and "Parecer Entregue" view — Phase 68.
- Pesquisa avançada — Phase 69.
- Diff between versions, rich text editor — v2.7 (PARV-07/08).

</deferred>
