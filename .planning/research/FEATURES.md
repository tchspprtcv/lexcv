# Feature Research

**Domain:** Legal Opinion (Parecer Jurídico) Request/Version/Approval/Delivery UI
**Researched:** 2026-07-01
**Confidence:** MEDIUM-HIGH (backend contract is HIGH confidence — read directly from existing v2.5 code/audit; ecosystem UI conventions are MEDIUM — synthesized from legal practice management vendor patterns, no single authoritative source for this exact workflow shape)

## Context Recap (from backend, v2.5)

The backend already enforces the full state machine; the frontend must only **present and drive** it, never reimplement business rules (per LexCV's "frontend burro" principle). Confirmed backend shape from `.planning/v2.5-MILESTONE-AUDIT.md` and `PROJECT.md`:

- Entities: `ParecerSolicitacao` (request) → `ParecerVersao` (immutable, append-only versions with content + optional attachment) → optional aprovação (ADMIN-only internal approval) → entrega (irreversible, sets `versaoFinalId`).
- 12 REST endpoints under `/api/v1/pareceres/*`.
- RBAC scopes: `pareceres:view/create/edit/manage` — same `scope:action` convention as every other module.
- Automatic audit logging on 5 transition points (create, atribuir, versão-criar, aprovar, entregar) via the existing `AuditLog` mechanism already surfaced in Processos' timeline/auditoria tab.
- Advanced search (`pesquisar()`): free-text + combined filters.
- Known gap: `versaoFinalId` is a raw field on the generic solicitação JSON — no dedicated "delivered opinion" view consumes it yet (PARC-09).

## Feature Landscape

### Table Stakes (Users Expect These)

Baseline for any request→version→approval→delivery workflow, validated against general legal practice management/document-approval-workflow conventions (Clio-style practice management, contract/document approval tooling) and against LexCV's own Processos module pattern (which already ships an equivalent list/detail/timeline shape).

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Lista de solicitações (`/pareceres`) | Every module in LexCV (Clientes, Processos, Documentos) has a list-first entry point; users need to triage open/assigned pareceres | LOW | Reuse existing table/mobile-card dual-view pattern (`hidden md:block` / `md:hidden`) already used in Clientes/Processos/Documentos |
| Status badges | Users must see solicitação state (aberta, em elaboração, em aprovação, entregue) at a glance | LOW | Reuse existing badge component pattern from Financeiro (honorários status) and Processos (fase/status) |
| Detalhe da solicitação | Single source of truth per parecer: metadata, cliente/processo vinculado, advogado atribuído, versões | LOW-MEDIUM | Mirrors Processos detail page shell |
| Timeline/histórico de versões | Sequential, immutable list of versões with author, timestamp, content/anexo — directly analogous to Processos' existing timeline/auditoria tab | MEDIUM | **Direct reuse** of the Processos timeline/auditoria tab pattern (already built in v1.7) — same visual language (chronological, actor-attributed entries) |
| Criação de solicitação (form) | Table stakes for any request-based workflow | LOW | React Hook Form + Zod, same as every other LexCV form |
| Atribuição de advogado responsável | Backend already requires an atribuído advogado; UI must expose who is assigned and let authorized users reassign | LOW-MEDIUM | Reuse the "advogados ligados a Users via junção tenant-scoped" pattern established in Cliente intake (v2.4) — a searchable user-picker, not free text |
| Criação de versão (conteúdo + anexo opcional) | Core value-add action of the module — an advogado must be able to draft/submit a new version | MEDIUM | Anexo upload should reuse the **Documentos module's existing upload component** (progress bar, drag-and-drop, MinIO-backed) rather than building a new uploader |
| Aprovação (ADMIN-only) action | Backend enforces this as an optional gate; UI must surface an approve/reject-equivalent action only to ADMIN, matching `AdminController`'s class-level `@PreAuthorize("hasRole('ADMIN')")` convention | LOW-MEDIUM | Button visibility gated both by `pareceres:manage` (or equivalent) AND role check in the client, mirroring existing dual-check patterns |
| Entrega (delivery) action, with irreversibility warning | Entrega is irreversible per backend contract — UI must confirm before submitting (destructive-action confirmation dialog) | LOW | Reuse existing confirm-dialog component (already used for deletes elsewhere) |
| RBAC-gated actions/buttons | Every other module (Financeiro visible only to ADMIN/TECNICO, etc.) gates actions via `hasScopedPermission` | LOW | Direct application of `web/src/lib/permissions.ts` — no new logic needed, just new scope strings |
| Pesquisa/filtros (mirroring `pesquisar()`) | Backend already built combined free-text + filter search; not exposing it in UI would repeat the v2.5 "backend built, unusable" mistake | MEDIUM | Search bar + filter chips (status, advogado, período, cliente/processo) — same filter-bar pattern as Processos/Documentos |
| Empty/loading/error states | Consistent with TanStack Query conventions used everywhere else in LexCV | LOW | Standard skeleton + toast-on-error, no new pattern |

### Differentiators (Competitive Advantage)

Not required for MVP usability, but raise the module above "backend exposed as forms."

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Vista dedicada "Parecer Entregue" | Directly resolves the audited gap (PARC-09): `versaoFinalId` currently only exists as a raw field with no consuming view. A dedicated read-only summary (final version content, delivery date/actor, attachment, full version trail collapsed) turns "data exists" into "data is usable." This is explicitly named as a target feature in PROJECT.md's v2.6 scope. | LOW-MEDIUM | This is the single highest-leverage differentiator — it's cheap (mostly a filtered/formatted read view over data the API already returns) and closes a known, named audit gap |
| Rich text editor for versão content | Legal opinion content benefits from structured formatting (headings, lists, emphasis) rather than plain textarea — general legal-document tooling treats formatted content as baseline, not exotic | MEDIUM | Verify current backend content field is stored as plain text vs. HTML/Markdown before committing to an editor — if backend stores plain string, a rich editor implies a content-format decision (Markdown recommended: cheap to render, diffable, avoids sanitization complexity of storing raw HTML) |
| Diff/comparação entre versões | Named directly in PARV-03 audit note as "satisfied by sequential list/detail only — no diff UI exists (deliberately deferred)." Side-by-side or inline diff between any two versões is standard practice in legal document version control tooling (redline-style clause-level diff) | MEDIUM-HIGH | Real value, but genuinely optional for v2.6 — versions are already immutable and browsable sequentially; a diff view is additive polish, not a blocker to usability. Recommend implementing only a simple text-diff (e.g. line-level) rather than clause-level redline — clause-level diff is a legal-drafting-tool-grade feature disproportionate to this milestone |
| Indicador de "versão atual vs. versão final" distinction | Once entregue, distinguishing "latest version" from "the version that was delivered" (they may not be the same if further versions were somehow created — verify backend invariant) matters for legal defensibility/audit trust | LOW | Cheap addition once the dedicated delivered-view exists; mostly a labeling/badge concern |
| Notificação in-app de novo parecer atribuído | LexCV already has an in-app notification system (badge + popover) from v2.1 Agenda | LOW-MEDIUM | Reuse existing notification infrastructure; extending it to a new event type is much cheaper than building notifications from scratch |
| Ficha de parecer imprimível | LexCV already ships a "Ficha Cliente imprimível" pattern (v2.4) reproducing physical office forms; a printable parecer entregue summary follows the same precedent and may match real office workflow (delivering printed legal opinions) | LOW-MEDIUM | Only build if there's a real signal of office need — otherwise defer; flagged here because the *pattern* already exists in the codebase, making it cheap if wanted |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|------------------|-------------|
| Real-time collaborative co-editing of a versão (Google-Docs-style) | Feels modern; other legal tools push "collaboration" | Backend models versões as immutable, sequential, single-author submissions — concurrent co-editing contradicts the entity design entirely and would require a completely different backend model (locking, CRDTs, draft-state) far outside this milestone's scope | Sequential immutable versioning (already built) — if two advogados need to collaborate, they coordinate outside the tool or submit successive versões |
| Editable/deletable versões after submission | Users instinctively want to "fix a typo" | Backend enforces immutability by design (legal defensibility — an opinion history must not be rewritable); allowing edits would break the audit trail's evidentiary value and contradict PARC's core guarantee | Submit a new versão; if truly wrong, the workflow's answer is "create the next version," not "edit the last one" |
| Reversible/undo-able entrega | "What if we delivered by mistake?" | Backend explicitly models entrega as irreversible (a compliance/defensibility feature, not an oversight) — building an "undo" would silently violate the backend contract or require a parallel unofficial mutation path | A strong confirmation dialog before entrega (this is already table stakes above); if a real mistake happens, it's a new solicitação/version, not a rollback |
| Full clause-level redline diff (contract-negotiation-grade) | Seen in dedicated contract lifecycle management (CLM) tools like Ironclad; sounds impressive | Disproportionate engineering cost (structural document parsing, clause alignment) for a first UI milestone whose primary gap is "no UI exists at all" — this is CLM-tier tooling, not practice-management-tier | Simple text/line diff (see differentiator above) or defer diff entirely to a later milestone once basic usability is proven |
| External e-signature / approval routing to non-system users (e.g., emailing an external senior partner for sign-off) | Common in enterprise legal-ops approval automation (Cflow, Ironclad-style templates) | Backend's aprovação step is explicitly internal/ADMIN-only and in-app; introducing external routing means new auth/identity concepts entirely outside PROJECT.md's "Out of Scope" (no Keycloak integration yet, no email notifications this milestone) | Keep approval internal-only, in-app, matching backend; PROJECT.md explicitly defers "Notificações push/email" |
| Custom per-tenant approval workflow builder (multi-step, conditional routing) | Feels like a natural "enterprise" feature | Backend models exactly one optional approval gate (ADMIN) — a configurable workflow engine is an entirely different (and much larger) product surface not supported by any existing entity | Ship the single fixed gate the backend supports; revisit only if multiple tenants request materially different approval chains |
| Building a new generic document viewer/comparison component from scratch | Tempting to make it "parecer-specific" and polished | Wastes effort duplicating what the Documentos module's download/preview already does for the optional anexo, and duplicates upload-progress logic that already exists | Reuse Documentos' existing upload/download components as-is for anexo handling within versões |

## Feature Dependencies

```
[Lista de Solicitações] ──requires──> [use-pareceres.ts hooks] ──requires──> [12 backend endpoints]

[Detalhe da Solicitação]
    ├──requires──> [Status badges]
    ├──requires──> [Timeline de versões] ──reuses──> [Processos timeline/auditoria pattern]
    └──requires──> [Atribuição de advogado] ──reuses──> [Cliente intake user-picker pattern]

[Criação de Versão]
    ├──requires──> [Anexo upload] ──reuses──> [Documentos upload component]
    └──enables────> [Timeline de versões] (each versão appends to timeline)

[Aprovação] ──requires──> [RBAC: ADMIN role check] ──gates──> [Entrega]

[Entrega] (irreversible)
    ├──requires──> [Confirm dialog pattern]
    ├──sets─────> [versaoFinalId]
    └──enables──> [Vista "Parecer Entregue"] ← closes PARC-09 gap

[Pesquisa Avançada] ──requires──> [Backend pesquisar() endpoint] (already built, v2.5 Phase 64)

[Diff entre versões] ──optional, depends on──> [Timeline de versões] (must exist first; diff is additive)
```

### Dependency Notes

- **Timeline de versões directly reuses the Processos timeline/auditoria tab** built in v1.7 — same chronological, actor-attributed entry rendering. This should be treated as a near-verbatim port, not a fresh design.
- **Anexo upload must reuse the Documentos module's existing component** (progress bar + drag-and-drop, MinIO-backed) rather than reimplementing upload UX — the backend's StorageService is already shared infrastructure.
- **Advogado atribuído picker should reuse the user-linking pattern from Cliente intake (v2.4)** — advogados/administrativos are linked to system Users via tenant-scoped junction tables, not free text; the parecer assignment field should follow the same searchable-user-select UX rather than a plain text input.
- **Vista "Parecer Entregue" depends only on entrega having occurred** — it's a read/formatting layer over existing API data (`versaoFinalId` + the referenced `ParecerVersao`), not a new backend capability. This is the cheapest high-value item in the entire feature set.
- **Diff between versions is genuinely optional and last** — it depends on the timeline existing and adds no new backend capability requirement (versões are already fetched); it can be deferred to a v2.7 without harming v2.6's core value.

## MVP Definition

### Launch With (v2.6)

Goal: Make the full parecer lifecycle usable end-to-end through the LexCV web app, closing the "backend-only" gap identified in the v2.5 audit.

- [ ] **`/pareceres` lista** — table/card dual-view, status badges, filters (status, advogado, cliente/processo)
- [ ] **`/pareceres/[id]` detalhe** — metadata, timeline de versões, ações disponíveis conforme RBAC
- [ ] **Formulário de criação de solicitação** — vinculado a cliente/processo, atribuição de advogado
- [ ] **Formulário de criação de versão** — conteúdo + anexo opcional (reusing Documentos upload)
- [ ] **Ação de aprovação (ADMIN)** — visible/actionable only under `hasRole('ADMIN')` + scope check
- [ ] **Ação de entrega** — confirm dialog emphasizing irreversibility
- [ ] **Vista dedicada "Parecer Entregue"** — resolves PARC-09; surfaces `versaoFinalId`'s referenced version as a clean summary
- [ ] **Pesquisa avançada UI** — free-text + filters, mirroring backend `pesquisar()`
- [ ] **RBAC gating in UI** — `pareceres:view/create/edit/manage` mirrored via `hasScopedPermission`, matching backend `@PreAuthorize`
- [ ] **`use-pareceres*.ts` TanStack Query hooks** — full coverage of the 12 endpoints

### Add After Validation (v2.7+)

- [ ] **Diff/comparação entre versões** — simple text/line diff between any two versões (not clause-level redline)
- [ ] **Notificações in-app para atribuição/novas versões/entrega** — extends existing v2.1 notification system to parecer events
- [ ] **Rich text editor for versão content** — upgrade from plain textarea once content-format (Markdown vs. HTML) is decided

### Future Consideration (v3+)

- [ ] **Ficha de parecer imprimível** — only if office workflow explicitly needs printed delivery, following the Ficha Cliente precedent
- [ ] **Distinção visual "versão mais recente" vs. "versão entregue"** if the two can legitimately diverge (verify backend invariant first)

### Explicitly Out of Scope for This Domain

- Real-time co-editing of versões
- Editing/deleting submitted versões
- Undo/reversal of entrega
- External (non-system-user) approval routing or e-signature
- Configurable multi-step/conditional approval workflow engine
- Clause-level redline diff (CLM-grade tooling)

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|----------------------|----------|
| Lista + Detalhe + Timeline de versões | HIGH | MEDIUM (reuses existing patterns) | P1 |
| Criação de solicitação/versão (forms) | HIGH | MEDIUM | P1 |
| Aprovação + Entrega ações (RBAC-gated) | HIGH | LOW-MEDIUM | P1 |
| Vista "Parecer Entregue" | HIGH (closes named audit gap) | LOW | P1 |
| Pesquisa avançada UI | HIGH (backend already built, unused otherwise) | MEDIUM | P1 |
| Notificações in-app | MEDIUM | LOW-MEDIUM (infra exists) | P2 |
| Diff entre versões (text-level) | MEDIUM | MEDIUM | P2 |
| Rich text editor | MEDIUM | MEDIUM | P2 |
| Ficha imprimível | LOW-MEDIUM | LOW-MEDIUM | P3 |
| Clause-level redline diff | LOW (disproportionate for this domain tier) | HIGH | Do not build |

## Sources

- `.planning/PROJECT.md` (HIGH confidence — direct project source of truth, v2.6 scope statement)
- `.planning/v2.5-MILESTONE-AUDIT.md` (HIGH confidence — direct backend contract, named gaps PARC-09/PARV-03)
- [Legal Opinion Approvals Template — Cflow](https://www.cflowapps.com/workflow-templates/legal/legal-opinion-approvals/) (MEDIUM — general approval-workflow pattern confirmation)
- [Legal Document Version Control Guide — Spellbook](https://www.spellbook.legal/briefs/document-version-control) (MEDIUM — version control / diff / attribution conventions in legal tooling)
- [What Is Legal Document Management? — MyCase](https://www.mycase.com/blog/law-firm-operations/legal-document-management/) (MEDIUM — practice-management-tier baseline features)
- [Top 6 Document Approval Workflow Software — SuiteFiles](https://www.suitefiles.com/document-approval-workflow-software/) (LOW-MEDIUM — approval routing patterns, used to justify anti-feature exclusions)
- Internal codebase precedent (HIGH confidence, read directly): Processos timeline/auditoria tab (v1.7), Documentos upload component (v2.2), Cliente intake user-linking (v2.4), notification system (v2.1), Ficha Cliente imprimível (v2.4), permissions.ts scope convention

---
*Feature research for: LexCV Módulo de Parecer Jurídico — UI (v2.6)*
*Researched: 2026-07-01*
