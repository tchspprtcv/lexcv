# Pitfalls Research: Módulo de Parecer Jurídico — UI (v2.6)

**Domain:** Frontend UI for a document/opinion-versioning workflow with irreversible state transitions, added to an existing multi-tenant Spring Boot + Next.js legal practice app
**Researched:** 2026-07-01
**Confidence:** HIGH (backend source code read directly; prior milestone audits read directly; frontend conventions read directly from `web/src/lib/permissions.ts` and `web/src/hooks/use-documentos.ts`)

## Critical Pitfalls

### Pitfall 1: Repeat of the v2.4 camelCase/snake_case bug class — confirmed HIGH RISK for this exact module

**What goes wrong:**
Backend persists/serializes correctly (e.g. `versaoFinalId` set on `/entregar`), but the frontend reads/writes a differently-cased or differently-named key and silently gets `undefined`. Data is correct in the DB and in direct curl/Postman tests; it just never renders or never round-trips through forms. This is exactly what happened in v2.4: 9/19 requirements were marked "passed" by phase-level verification and were still broken end-to-end, because verification tested backend-in-isolation (curl) or frontend-code-presence (grep/`tsc --noEmit`), never a live JSON key trace.

**Why it happens (confirmed by reading the actual Parecer entities):**
`ParecerSolicitacao.java` and `ParecerVersao.java` have **zero `@JsonProperty` annotations** — same as the pre-remediation v2.4 `Cliente`/`DadosTipo` entities. There is still no global `spring.jackson.property-naming-strategy` configured anywhere in `backend/` (confirmed absent in the v2.4 audit, and the v2.4 remediation deliberately used per-field `@JsonProperty` instead of a global fix, so the app-wide default remains plain Jackson camelCase). Every field the frontend will need to read is camelCase on the wire:
- `ParecerSolicitacao`: `tenantId`, `clienteId`, `processoId`, `advogadoId`, `versaoFinalId`, `prioridade`, `status`, `prazo`, `createdAt`
- `ParecerVersao`: `tenantId`, `solicitacaoId`, `numeroVersao`, `conteudo`, `caminhoAnexo`, `criadoPorId`, `createdAt`, `aprovado`, `aprovadoPorId`, `aprovadoEm`

If any new `web/src/types/pareceres.ts` or hook is drafted with snake_case fields (`versao_final_id`, `numero_versao`, `criado_por_id`, `aprovado_por_id`, `aprovado_em`, `caminho_anexo`) — which is an easy mistake to make by pattern-matching on other Portuguese-domain fields elsewhere in the app that *are* snake_case at the DB/column level — every read will silently fail exactly like v2.4's `PERF-02`/`PROC-02`/`INT-01` gaps. This is the single highest-probability defect for this milestone, given it already happened once in the same codebase four milestones ago and the underlying root cause (no naming-strategy fix) was explicitly left as unaddressed tech debt.

**How to avoid:**
1. Before writing any TypeScript type or hook, fetch a *live* JSON response shape from the running backend for at least one representative object of each entity (`ParecerSolicitacao`, `ParecerVersao`) — via `curl`/Postman against a real dev instance, not by inferring from the Java class. Do this in the first phase, before building any UI that consumes these fields.
2. Define `web/src/types/pareceres.ts` using the exact camelCase keys confirmed from the live response — `versaoFinalId`, `numeroVersao`, `caminhoAnexo`, `criadoPorId`, `aprovadoPorId`, `aprovadoEm`, `advogadoId`, `processoId`, `clienteId`.
3. Explicitly surface `versaoFinalId` in the "parecer entregue" view planned for this milestone (this closes PARC-09 from the v2.5 audit) — read it as `data.versaoFinalId`, not `data.versao_final_id`.
4. Do not assume other app conventions (many DB columns are snake_case, e.g. `t_parecer_versao.numero_versao`) apply to the JSON wire format — the DB column name and the JSON key are different layers; `@Column(name=...)` does not affect Jackson serialization at all.
5. Add a lightweight runtime check in dev (e.g. log a warning if a critical field like `versaoFinalId` is `undefined` on a `CONCLUIDO` solicitação) as a tripwire.

**Warning signs:**
- A badge/field that "should" show up (e.g. delivered-version reference, aprovado timestamp) renders blank despite the network tab showing the field present under a *different* key.
- `tsc --noEmit` and `pnpm build` pass — this is not sufficient evidence of correctness (this exact false confidence caused the v2.4 miss).
- Any new type file introduces snake_case keys for fields that don't already have `@JsonProperty` in the corresponding Java entity.

**Phase to address:**
Phase 1 (read-only listing/detail views) — verify field naming against a live backend response *before* building the versioning/aprovar/entregar mutation forms. Do not defer this check to a final integration phase; the v2.4 lesson is that verification-by-grep does not catch this, only a live JSON trace does.

---

### Pitfall 2: UI implies an editable/actionable state after "entregar" (irreversible transition) has occurred

**What goes wrong:**
`entregarSolicitacao` is a one-way, backend-enforced irreversible transition (`status` → `CONCLUIDO`; the controller explicitly rejects re-entrega and re-aprovar and re-atribuir once `CONCLUIDO`). If the frontend doesn't mirror this state machine, users will see action buttons (versionar, aprovar, atribuir, editar prazo/prioridade) that are still clickable but will 400 at the backend — or worse, users believe they can still change something about a "closed" legal opinion, which is a serious trust/compliance issue for a legal practice tool.

**Why it happens:**
The backend enforces the state machine only in **imperative if-checks scattered across each endpoint** (`if ("CONCLUIDO".equals(solicitacao.getStatus()))` appears independently in `atribuirAdvogado`, `aprovarVersao`, `entregarSolicitacao`) rather than a single declarative state-transition table. If the frontend developer reads only one endpoint's logic (e.g. copies the pattern from `atribuir`) they may miss that `updateSolicitacao` (PUT `/{id}`) has **no such guard at all** — a `CONCLUIDO` parecer's `prazo`/`prioridade` can currently still be edited via that endpoint even after delivery. The frontend must decide independently whether to hide/disable that edit form for `CONCLUIDO` records, because the backend won't stop it.

**How to avoid:**
1. Build a single frontend state-derivation helper (e.g. `getParecerActions(status, isAdmin, isResponsavel)`) that centralizes which actions are visible/enabled for each of the 4 statuses (`PENDENTE`, `EM_ELABORACAO`, `EM_REVISAO`, `CONCLUIDO`), used consistently across list, detail, and form views — don't scatter `status === "CONCLUIDO"` checks ad hoc across components.
2. Treat `CONCLUIDO` as fully read-only in the UI: hide/disable "nova versão", "atribuir advogado", "aprovar", "entregar", and the prazo/prioridade edit form — even though the backend endpoint for prazo/prioridade edit doesn't itself reject it server-side. Flag this backend gap (missing guard in `updateSolicitacao` for `CONCLUIDO`) as a security/consistency note for backend review — don't rely on the backend to block it.
3. Show a clear, persistent visual indicator (e.g. a lock icon + "Parecer entregue — imutável" banner) rather than just disabling buttons silently, so users understand *why* actions are unavailable, not just that they're greyed out.
4. Because versions themselves are immutable once created (no PUT/PATCH endpoint exists for `ParecerVersao` at all — only POST to create a new one), never build an "editar versão" affordance anywhere in the UI, even for non-`CONCLUIDO` solicitações. The versioning model is append-only by design.

**Warning signs:**
- Any component checks `status !== "CONCLUIDO"` inline in JSX rather than through a shared helper — a strong signal of drift risk if the state machine gets a 5th status later.
- Clicking a disabled-looking button still fires the mutation (disabled only via CSS, not by omitting the handler/route).
- A form for editing prazo/prioridade is reachable from a `CONCLUIDO` parecer's detail page.

**Phase to address:**
The phase that builds the "parecer entregue"/detail view and the phase that builds the aprovar/entregar action UI — both should share the same status-derivation logic, ideally introduced once in an early phase (listing/detail) and reused, not reimplemented per phase.

---

### Pitfall 3: RBAC scope/action drift between backend `@PreAuthorize` and frontend `hasScopedPermission` calls

**What goes wrong:**
Backend and frontend each independently encode which `pareceres:{view,create,edit,manage}` scope gates which action. If the frontend guesses the wrong action tier for a button (e.g. gates "atribuir advogado" behind `pareceres:create` when the backend actually requires `pareceres:edit`), a user with `edit` permission will see the action hidden in the UI (false negative — annoying but safe) or a user with only `view` will see a button that 403s on click (false positive — worse, since `hasScopedPermission`'s fallback chain treats `edit`/`manage` as implying `create`/`view` but the reverse isn't true, so under-gating in the UI is the more likely failure mode when a developer isn't careful about which tier each backend endpoint actually needs).

**Why it happens:**
Read directly from `ParecerController.java`, here is the exact scope-to-action-to-endpoint map that the frontend MUST mirror exactly (not approximate):

| Endpoint | Backend scope required |
|---|---|
| `POST /pareceres/solicitacoes` (criar) | `pareceres:create` |
| `GET /pareceres/solicitacoes`, `GET /{id}`, `GET .../versoes`, `GET .../versoes/{id}`, `GET .../anexo`, `GET /pareceres/pesquisa` | `pareceres:view` |
| `PUT /{id}` (update prazo/prioridade) | `pareceres:edit` |
| `PUT /{id}/atribuir` | `pareceres:edit` |
| `PUT /{id}/versoes/{versaoId}/aprovar` | `pareceres:manage` (note: NOT `edit` — this is the highest tier, likely ADMIN-only in practice per seeded roles) |
| `PUT /{id}/entregar` | `pareceres:edit` (but ALSO has an additional in-code authorization check: `isAdmin || isResponsavel` — a user could have `pareceres:edit` scope generally yet still be blocked from entregar a *specific* parecer they aren't assigned to) |
| `POST .../versoes` (criar versão) | `pareceres:edit` (same additional `isAdmin || isResponsavel` check as entregar) |

The two extra findings that are easy to miss: (a) `aprovar` uses `manage`, not `edit` — a naive frontend implementation would likely gate it the same as other edit-tier actions and get it wrong; (b) `entregar` and `createVersao` both layer a **resource-instance-level check on top of the scope check** (must be the assigned `advogadoId` or ADMIN) — `hasScopedPermission` alone cannot express this; the frontend needs the current parecer's `advogadoId` plus the current user's id/role to correctly show/hide these two specific actions, not just the permission list from the JWT/session.

**How to avoid:**
1. Build the permission-gating table above explicitly into the phase's plan/CONTEXT before writing any component — don't infer scope tiers by pattern-matching other modules.
2. For "nova versão" and "entregar" specifically, gate visibility on **both** `hasScopedPermission(perms, "pareceres", "edit")` **and** `(currentUser.roles.includes("ADMIN") || solicitacao.advogadoId === currentUser.id)`. A helper co-located with the status-derivation helper from Pitfall 2 is the cleanest way to keep this consistent.
3. For "aprovar", gate on `hasScopedPermission(perms, "pareceres", "manage")`, not `"edit"`.
4. Since backend authorization is the actual security boundary, UI-side gating errors are a UX problem (confusing 403s or hidden-but-permitted actions), not a security hole — but they still need explicit verification because they were exactly the kind of cross-layer drift the v2.4 audit flagged as a systemic risk class in this codebase.
5. `pareceres` is already present in `web/src/lib/permissions.ts`'s `KNOWN_SCOPES` — reuse `hasScopedPermission` as-is, no new permission utility needed.

**Warning signs:**
- A frontend "aprovar" button gated by `pareceres:edit` instead of `pareceres:manage` (would show the button to a broader group of users than backend actually allows, resulting in a silent 403 on click).
- No frontend check for "is this user the assigned advogado or ADMIN" on the entregar/nova-versão actions — button shown to any user with generic `pareceres:edit`, then blocked server-side with a confusing error.

**Phase to address:**
The phase that builds the aprovar/entregar/versionar action UI. Recommend a short explicit checklist step cross-referencing each frontend gate against the `@PreAuthorize` line in `ParecerController.java` before considering that phase done — this is cheap to do and was exactly the class of gap the v2.4 audit's remediation step performed after the fact (a "field-by-field static trace"); doing the RBAC equivalent up front avoids needing a repeat audit-driven fix.

---

### Pitfall 4: File upload/download UX inconsistencies reusing the Documentos/MinIO pattern

**What goes wrong:**
The Parecer versão attachment flow (`POST .../versoes` multipart, `GET .../versoes/{id}/anexo` presigned URL) is structurally similar to Documentos but has different semantics that a copy-paste reuse of `use-documentos.ts` patterns would get wrong:
- Documentos supports **delete** and **replace** (`replace_id` field); Parecer versões are immutable — there is no delete/replace endpoint for a `ParecerVersao` or its `caminhoAnexo`. A hook copied from `useUploadDocumento`/`useDeleteDocumento` that includes a delete/replace affordance for a versão attachment would be building UI for an operation the backend does not support (confirmed: no DELETE endpoint exists on `ParecerVersao` anywhere in `ParecerController.java`).
- Documentos' upload accepts `nome`/`tipo`/`confidencialidade`/`processo_id`/`cliente_id` as separate form fields; Parecer's `createVersao` only accepts `conteudo` (text) and `file` (multipart) as form params — a much narrower payload. Blindly reusing the Documentos upload form fields would send fields the backend controller doesn't read (silently ignored, not an error, but a source of confusion/wasted UI).
- The attachment is **optional** per version (`conteudo` and `file` are each optional but at least one is required — backend validates `(conteudo == null || blank) && (file == null || empty)` → 400). The UI must support "content-only" versions (no attachment) and "attachment-only" versions (no text) as equally valid, not force both fields to be filled.
- Download uses the same presigned-URL pattern as Documentos (`GET .../anexo` → `{url, expiresIn}`), which is good — reuse `useDownloadDocumento`'s pattern directly for `useDownloadParecerAnexo`, including its 3600s (1 hour) expiry assumption; don't assume a longer-lived direct link is safe to cache/reuse across a user session.
- No progress-bar variant equivalent to `useUploadDocumentoComProgresso` exists yet for pareceres — if attachments for pareceres are expected to be large (legal documents/PDFs), the plain `apiFetch`-based mutation (no progress UI) may feel broken on slow connections; decide explicitly whether to port the XHR-progress variant or accept the simpler mutation.

**Why it happens:**
Reuse-by-analogy is the fastest path to a working UI (and is explicitly encouraged since a MinIO-backed `StorageService` already exists and is proven), but the two modules' business rules diverge (mutable/deletable/replaceable documents vs. immutable, append-only, one-way versions), and a straight copy of hook logic imports operations that don't map to real endpoints.

**How to avoid:**
1. Write `use-pareceres.ts` hooks fresh, referencing `use-documentos.ts` only for the shared low-level patterns (FormData construction, `apiFetch` with `credentials: include`, presigned-URL download-then-navigate), not for the higher-level CRUD shape (no delete/replace mutations for versões).
2. Build the "nova versão" form with `conteudo` as an optional `<textarea>` and `file` as an optional file input, with client-side validation requiring at least one non-empty — mirroring the backend's exact validation message, not inventing new copy.
3. Do not add a delete or replace button anywhere in the versão UI.
4. Decide progress-bar UX intentionally (likely: reuse the XHR pattern if attachments are typically PDFs of non-trivial size) rather than defaulting to the simpler no-progress mutation just because it's less code.

**Warning signs:**
- A "delete versão" or "replace anexo" button appears anywhere in the UI — there is no backend support for it.
- Upload form requires both content and file (blocks valid single-field submissions the backend would accept).
- Upload form sends `nome`/`tipo`/`confidencialidade` fields that `createVersao`'s `@RequestParam`s never declare (silently dropped server-side, wasted client code).

**Phase to address:**
The phase that builds versioning/attachment UI (mirrors backend Phase 62 "Elaboração e Versionamento").

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|--------------------|-----------------|------------------|
| Reusing Documentos' full CRUD hook shape (delete/replace) for pareceres versões | Faster to scaffold | Builds dead/misleading UI for unsupported operations, confuses future maintainers about the immutability model | Never — write pareceres hooks against pareceres' actual endpoint set |
| Inferring JSON field casing from the Java class instead of a live response | Saves a manual curl/Postman round-trip | Repeats the exact v2.4 defect class that broke 9/19 requirements silently | Never for this project, given the documented history |
| Scattering `status === "CONCLUIDO"` checks ad hoc per component instead of one shared helper | Faster initial implementation | Inconsistent action-gating across list/detail/form views as statuses evolve | Acceptable only for a single, very small, throwaway prototype — not for this milestone's shipped UI |
| Gating "aprovar" and "entregar"/"nova versão" using only scope-level `hasScopedPermission` (ignoring the backend's instance-level `isAdmin \|\| isResponsavel` check) | Simpler permission logic | Buttons shown to users who will get a 403 on click; confusing UX for an irreversible/compliance-sensitive action | Never for entregar/nova-versão; acceptable for view-only actions where there's no instance-level backend check |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|-----------------|-------------------|
| Spring Boot JSON serialization (Jackson default, no naming strategy) | Assuming DB column snake_case (`@Column(name="versao_final_id")`) implies JSON key is also snake_case | JSON key always follows the Java field name (camelCase) unless `@JsonProperty` overrides it — confirmed no such override exists on `ParecerSolicitacao`/`ParecerVersao`; treat every field as camelCase on the wire until proven otherwise by a live response |
| MinIO-backed StorageService via existing `/documentos/*` pattern | Copying Documentos' full CRUD hook surface (delete/replace) onto pareceres versões, which have no such endpoints | Scope pareceres hooks to only the 3 real endpoints: create-versão (multipart POST), list/get-versão (GET), download-anexo (GET presigned URL) |
| RBAC (`@PreAuthorize` vs `hasScopedPermission`) | Assuming all pareceres mutating actions use the same scope tier (`edit`) | `aprovar` requires `manage`, a stricter tier than `edit`/`entregar`/`nova-versão` — verify each endpoint's actual `@PreAuthorize` string individually, don't assume uniformity |
| Instance-level authorization layered on top of scope-based RBAC (`entregar`, `nova versão`: `isAdmin \|\| isResponsavel`) | Gating UI only on the JWT-derived permission list, ignoring per-record ownership | Fetch/derive `advogadoId` for the current solicitação and compare to the logged-in user's id (plus ADMIN role check) before showing entregar/nova-versão actions |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| `listSolicitacoes` fetches all tenant rows then filters in-memory in Java (`.stream().filter(...)`) — no DB-level pagination | Fine for demo/small tenants; slow list responses as row count grows | Frontend should still build proper filter/search UI now (mirroring `pesquisar()`), but don't assume the backend paginates — avoid rendering unbounded lists without client-side virtualization/pagination controls once volumes grow | Noticeable once a tenant accumulates hundreds+ of `t_parecer_solicitacao` rows; not a v2.6 blocker but worth flagging as a backend follow-up, not silently working around it with heavier client logic |
| `synchronized (ParecerVersaoRepository.class)` block for `numeroVersao` generation | Works correctly for reasonably low concurrent version-creation rates | No frontend action needed — but don't build UI that assumes instantaneous version numbering under high concurrency (e.g. optimistic-UI showing "v4" before the server confirms) — wait for the server response before displaying the new version number | Only relevant under heavy concurrent multi-user editing of the same solicitação simultaneously, which is an unlikely real-world pattern for this workflow but still means optimistic version-number UI would occasionally be wrong |

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|------------------|------------|
| Listagem/detalhe (read-only views) | camelCase/snake_case mismatch (Pitfall 1) — this is the phase where the mistake would first be introduced and is cheapest to catch | Trace a live JSON response before writing `types/pareceres.ts`; explicitly test `versaoFinalId` rendering to close PARC-09 |
| Vista "parecer entregue" (surfacing `versaoFinalId`) | Same field-naming risk, plus risk of the view rendering as if editable | Read `versaoFinalId` correctly (Pitfall 1); render this view as fully read-only/locked (Pitfall 2) |
| Formulário de solicitação (criação) | RBAC gating for `pareceres:create` is the simplest case (no instance-level check) — lowest risk phase | Standard `hasScopedPermission(perms, "pareceres", "create")`, straightforward |
| Versionamento (conteúdo/anexo) | File upload UX drift from Documentos pattern (Pitfall 4); instance-level RBAC for nova-versão (Pitfall 3) | Write hooks fresh scoped to actual endpoints; gate on `edit` scope AND `isAdmin||isResponsavel` |
| Aprovação/entrega | Wrong RBAC tier for aprovar (`manage` not `edit`) (Pitfall 3); irreversibility not reflected in UI post-entrega (Pitfall 2) | Explicit scope-to-endpoint table check per action; shared status-derivation helper disabling all mutating actions once `CONCLUIDO` |
| Pesquisa avançada | Lower risk — mostly read-only GET with query params; still subject to Pitfall 1 for any result fields displayed | Verify search result JSON shape against live response, same discipline as listagem phase |

## Sources

- `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` (read directly — confirms no `@JsonProperty` overrides)
- `backend/src/main/java/com/lexcv/models/ParecerVersao.java` (read directly — confirms no `@JsonProperty` overrides)
- `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (read directly — confirms exact `@PreAuthorize` scope per endpoint, state-machine guard locations, instance-level authorization checks on entregar/createVersao)
- `.planning/milestones/v2.4-MILESTONE-AUDIT.md` (read directly — documented root cause and mechanism of the prior camelCase/snake_case defect class in this exact codebase)
- `.planning/v2.5-MILESTONE-AUDIT.md` (read directly — confirms backend-only v2.5 scope, the `versaoFinalId` surfacing gap (PARC-09), and the milestone's own recommendation to build v2.6 as a dedicated UI milestone)
- `.planning/PROJECT.md` (read directly — Key Decisions table entry on the v2.4 `@JsonProperty` remediation choice and its explicitly-scoped, non-global nature)
- `web/src/lib/permissions.ts` (read directly — confirms `pareceres` is already a registered scope, and the `ACTION_FALLBACKS` semantics that `edit`/`manage`/`create` gating must respect)
- `web/src/hooks/use-documentos.ts` (read directly — the concrete hook pattern for MinIO-backed upload/download/progress that this milestone should partially reuse and partially deliberately diverge from)

All findings above are HIGH confidence: derived from direct reads of this repository's actual backend source and prior milestone audit documents, not from general domain knowledge or web search.
