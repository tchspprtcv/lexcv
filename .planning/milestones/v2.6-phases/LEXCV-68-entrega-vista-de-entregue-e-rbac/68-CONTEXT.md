# Phase 68: Entrega, Vista de Entregue e RBAC - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Highest-risk phase of the milestone: the irreversible "entregar" action, a dedicated "Parecer Entregue" read view (closing the PARC-09 gap from the v2.5 audit), and a full RBAC/instance-check audit across every action added so far (create solicitação, create versão, entregar). Depends on Phases 65-67. Does NOT include aprovação (out of milestone scope) or pesquisa (Phase 69).

</domain>

<decisions>
### Entrega Action
- Confirmation dialog uses the existing `AlertDialog`/`AlertDialogTrigger`/`AlertDialogContent`/`AlertDialogAction` primitives from shadcn (already used in `web/src/app/(dashboard)/agenda/[id]/page.tsx` for event deletion) — NOT `window.confirm()` (that pattern exists elsewhere in the codebase but is inferior for an action this consequential; the AlertDialog pattern is the better existing precedent and matches this phase's higher-stakes irreversibility).
- Dialog title: "Entregar Parecer". Body must explicitly state the action is irreversible (mirrors agenda's "Esta ação não pode ser revertida" phrasing) and require the user to pick which version is being delivered as final — the backend's `entregar` endpoint requires an explicit `versaoFinalId` query param, so the UI must let the user select (or default to) a specific version, not silently assume "latest."
- Confirm action calls `POST` — actually `PUT /pareceres/solicitacoes/{id}/entregar?versaoFinalId={versaoId}` per `ParecerController.entregarSolicitacao` (no request body, `versaoFinalId` is a query param, not JSON field).
- Visibility: entrega button/trigger only rendered when `solicitacao.status !== "CONCLUIDO"` AND (current user is `advogadoId` OR is ADMIN) AND `pareceres:edit` scope — mirrors backend's `isAdmin || isResponsavel` check in `entregarSolicitacao` exactly (same instance-check shape already established in Phase 67 for `createVersao`).
- On success: invalidate `["pareceres","detail",id]` and `["pareceres","list"]` (status changes to CONCLUIDO, `versaoFinalId` is now set). Timeline (`["pareceres","versoes",id]`) doesn't need invalidation since entrega doesn't create/modify a version row.

### Vista "Parecer Entregue"
- Not a new route — a conditional section/card on the existing `/pareceres/[id]` detail page, rendered when `solicitacao.status === "CONCLUIDO"` and `solicitacao.versaoFinalId` is set. Resolves the PARC-09 gap (versaoFinalId existed only as a raw field with no consuming view).
- Content: the final version's full data (numeroVersao, conteudo, anexo download link, autor, createdAt) resolved from the already-fetched `useParecerVersoes` timeline data by matching `versao.id === solicitacao.versaoFinalId` — no new fetch needed, just a lookup + dedicated rendering block. Also show entrega metadata: since the backend doesn't store a separate "entregue em/por" timestamp/actor (only `versaoFinalId` + `status`), the "delivered by/at" info is NOT literally available from the API — use the audit log if surfaced elsewhere, otherwise state only what's derivable (the final version's own author/date) and do not fabricate an "entregue por X em Y" claim the API can't support. Flag this as a discretion item for planning to resolve precisely against the actual `ParecerSolicitacao`/`AuditLog` fields.
- This "Parecer Entregue" block replaces/sits above the generic timeline once entregue — the full version history remains visible below it for context, per Phase 65's existing timeline rendering (not removed, just no longer the primary focus).

### RBAC Audit (cross-phase)
- Explicit audit pass across `/pareceres` pages of every action button (criar solicitação, criar versão, entregar) confirming each is gated by BOTH the correct `hasScopedPermission(perms, "pareceres", action)` scope check AND, where the backend has an instance-level check, the matching frontend instance check (advogado responsável or ADMIN) — this phase is explicitly the place to close any gaps rather than assuming Phases 66/67 got it fully right everywhere.
- A solicitação with `status === "CONCLUIDO"` must be fully read-only in the UI: no create-versão form, no entrega button, regardless of role — reinforces immutability post-entrega established as a decision in Phase 67.

### Claude's Discretion
- Exact layout/visual hierarchy of the "Parecer Entregue" block (e.g., prominent banner vs. a dedicated card) — implementation detail per UI-SPEC.
- Precisely which fields are shown in the "Parecer Entregue" summary given the data-availability constraint noted above (delivered-by/delivered-at) — resolve during planning by checking exactly what `ParecerSolicitacao`/`AuditLog` expose, do not invent fields.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/agenda/[id]/page.tsx` (lines ~166-200) — `AlertDialog` confirmation pattern for an irreversible action, including disabled-during-pending state and error display inside the dialog. Direct analog for the entrega confirmation.
- Phase 67's instance-check pattern (`isResponsavelOuAdmin` derivation) in `pareceres/[id]/page.tsx` — reuse identically for gating the entrega trigger.
- Phase 65's version timeline rendering — the "Parecer Entregue" block reads from the same `useParecerVersoes` data already fetched, no new hook needed for that lookup (though a new `useEntregarParecer` mutation hook is needed for the entrega action itself).

### Established Patterns
- `AlertDialogAction` styled with `bg-destructive text-destructive-foreground hover:bg-destructive/90` for the confirm button of an irreversible action (from agenda's delete pattern) — note "destructive" styling convention applies even though entrega isn't a delete; it's still irreversible and should carry equivalent visual weight per the UI-SPEC to be produced for this phase.

### Integration Points
- Backend: `PUT /api/v1/pareceres/solicitacoes/{id}/entregar?versaoFinalId={uuid}` (`@PreAuthorize("hasAuthority('pareceres:edit')")` + `isAdmin || isResponsavel` instance check), confirmed in `ParecerController.entregarSolicitacao`. No request body — `versaoFinalId` is a `@RequestParam UUID`, not JSON.
- Response: updated `ParecerSolicitacao` (status now `CONCLUIDO`, `versaoFinalId` set), 200 OK.
- `ParecerSolicitacao` entity has no dedicated "entregue em/por" fields beyond `versaoFinalId` + `status` — confirmed by re-reading `ParecerSolicitacao.java` during Phase 65 planning. Any "delivered by/at" UI copy must derive from the final version's own `criadoPorId`/`createdAt`, or be omitted, not fabricated from nonexistent fields.

</code_context>

<specifics>
## Specific Ideas

No additional specifics beyond what's captured above.

</specifics>

<deferred>
## Deferred Ideas

- Aprovação interna (ADMIN) — out of milestone scope entirely (PARC-17, v2.7).
- Pesquisa avançada — Phase 69.
- Diff between versions, rich text editor — v2.7.

</deferred>
