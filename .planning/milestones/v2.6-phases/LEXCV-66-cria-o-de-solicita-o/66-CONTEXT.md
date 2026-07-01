# Phase 66: Criação de Solicitação - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning

<domain>
## Phase Boundary

First write path for the Parecer Jurídico UI: a form to create a `ParecerSolicitacao` (cliente required, processo optional, advogado optional-at-creation via `POST /pareceres/solicitacoes`). Depends on Phase 65's types/hooks/pages. Does NOT include version creation, entrega, aprovação, or pesquisa — those are later phases.

**Scope correction (2026-07-01):** NOTF-05 (in-app notification on assignment) was removed from this milestone's v1 scope — see `.planning/REQUIREMENTS.md` v2 Requirements/Notificações. No generic notification backend exists (`NotificationBell` only surfaces upcoming Agenda events via `useUpcomingEventos`); building it would require new backend work, which contradicts this milestone's "no new backend work" constraint. This phase now covers only PARC-13.

</domain>

<decisions>
### Create Form
- New route `web/src/app/(dashboard)/pareceres/nova/page.tsx`, following the exact page shell pattern of `processos/novo/page.tsx` (access-guard via `usePermissions()` + `AccessDeniedState`, React Hook Form + Zod, plain `<select>` with `selectClassName`/`textareaClassName` — no shadcn Combobox/Command primitives exist in this codebase, don't introduce one).
- Fields: `clienteId` (required, `<select>` from `useClientes({})`), `processoId` (optional, `<select>` from `useProcessos` filtered/searchable by the chosen cliente if that hook supports it — otherwise plain unfiltered select, confirm during planning), `descricao` (required, textarea), `prazo` (optional date input), `prioridade` (select: ALTA/MEDIA/BAIXA, default MEDIA to match backend default), `advogadoId` (optional at creation — `<select>` from `useAdminUsers()` client-filtered to users whose roles include `"ADVOGADO"`, same filtering approach already used in Phase 65's list-page advogado filter).
- Submit calls a new `useCreateParecer()` mutation hook (`POST /pareceres/solicitacoes`, JSON body — NOT multipart, unlike version creation in Phase 67). On success, invalidate `["pareceres", "list"]` and redirect to the new solicitação's detail page (`/pareceres/${created.id}`).
- Entry point: "Nova Solicitação" button on the `/pareceres` list page header (was explicitly deferred from Phase 65 — CTA now belongs here), gated by `hasScopedPermission(perms, "pareceres", "create")`.
- Cancel/back navigates to `/pareceres`.

### Advogado Assignment
- Assigning an advogado at creation time is done by simply including `advogadoId` in the create POST body — the backend already sets `status = EM_ELABORACAO` automatically when `advogadoId` is present at creation (confirmed in `ParecerController.createSolicitacao`). No separate "atribuir" call is needed for the at-creation case; the dedicated `PUT /{id}/atribuir` endpoint (for reassignment after creation) is out of scope for this phase — it may be added later if a "reassign from detail page" UI is wanted, but it's not part of PARC-13's create-form scope.

### Claude's Discretion
- Whether `processoId` select should be searchable/filtered by the selected cliente, or a flat list of all tenant processos — implementation detail, resolve based on what `use-processos.ts` already exposes.
- Exact validation error copy (follow existing Portuguese tone).
- Whether prazo uses a native `<input type="date">` or an existing date-picker component — check what `processos/novo/page.tsx` or `agenda` forms already use and reuse it.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/processos/novo/page.tsx` — exact page-shell and form-styling pattern (`selectClassName`, `textareaClassName`, RHF+Zod, access-guard).
- `web/src/hooks/use-admin.ts#useAdminUsers` — already used in Phase 65 for the advogado filter; reuse identically here for the picker, client-filtered to role `"ADVOGADO"`.
- `web/src/hooks/use-clientes.ts#useClientes` — cliente select data source, already consumed in Phase 65.
- `web/src/hooks/use-pareceres.ts` (Phase 65) — add `useCreateParecer()` alongside the existing read hooks; follow the same `apiFetch` + query-key-invalidation convention already established (`["pareceres", "list"]`).

### Established Patterns
- No shadcn Combobox/Command component exists anywhere in the codebase — all "picker" UI is plain native `<select>` populated from a TanStack Query list, exactly as done for filters in Phase 65.
- Create-mutation hooks return the created entity; pages redirect via `useRouter().push()` on success.

### Integration Points
- Backend: `POST /api/v1/pareceres/solicitacoes` (`@PreAuthorize("hasAuthority('pareceres:create')")`) — confirmed in `ParecerController.java`. Body accepts `clienteId` (required), `processoId` (optional), `descricao` (required), `prazo`, `prioridade`, `advogadoId` (optional). Response is the created `ParecerSolicitacao`, `201 Created`.
- No `NOTF-05` integration point — removed from scope (see Domain section above).

</code_context>

<specifics>
## Specific Ideas

No additional specifics — this phase closely follows the existing Processos "novo" form pattern by design.

</specifics>

<deferred>
## Deferred Ideas

- Version creation, entrega, aprovação — later phases (67/68).
- Pesquisa avançada — Phase 69.
- Reassignment of advogado after creation (`PUT /{id}/atribuir`) via a dedicated UI action — not part of PARC-13, may be a small addition to a later phase if wanted.
- In-app notification on assignment (NOTF-05) — removed from milestone scope entirely (no generic notification backend exists); would need a dedicated future milestone.

</deferred>
