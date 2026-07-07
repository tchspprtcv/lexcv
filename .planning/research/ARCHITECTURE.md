# Architecture Research

**Domain:** Backend/frontend integration plan for milestone v2.9 (Melhoria Módulo Processos) — LexCV legal practice management, Spring Boot 3.4.1/Java 23 + Next.js 16/React 19
**Researched:** 2026-07-07
**Confidence:** HIGH (all findings verified directly against current repository source, not training-data assumptions)

## Standard Architecture

### System Overview (as it exists today)

```
┌──────────────────────────────────────────────────────────────────────┐
│  web/src/app/(dashboard)/processos/                                  │
│  ┌──────────────┐ ┌──────────────────┐ ┌──────────────────────────┐ │
│  │ novo/page.tsx│ │ [id]/page.tsx     │ │ [id]/editar/page.tsx     │ │
│  │ intake wizard│ │ detail (button-   │ │ separate edit form       │ │
│  │ 3-step        │ │ toggle tabs)      │ │ (NOT unified w/ view —   │ │
│  │              │ │ Timeline/Partes/  │ │  unlike Cliente v2.8)    │ │
│  │              │ │ Fases/Auditoria   │ │                           │ │
│  └──────┬───────┘ └────────┬──────────┘ └──────────────┬────────────┘ │
│         └──────────────────┴──────────────────┬─────────┘             │
│                                                 ▼                       │
│                              web/src/hooks/use-processos.ts (567 lines)│
│                              web/src/hooks/use-financeiro.ts (Honorario)│
│                              web/src/hooks/use-documentos.ts            │
│                              web/src/types/processos.ts (216 lines)     │
│                              web/src/schemas/processos.ts (Zod, 98 ln)  │
├──────────────────────────────────────────────────────────────────────┤
│  next.config.ts rewrite: /api/v1/:path* → BACKEND_API_ORIGIN          │
├──────────────────────────────────────────────────────────────────────┤
│  backend/src/main/java/com/lexcv/controllers/ResourceController.java  │
│  @RequestMapping("/api/v1")  — 2504 lines, ALL Processo sub-resources  │
│  /processos, /processos/{id}/partes|fases|movimentacoes|documentos|   │
│  prazos|timeline|audit|workflow|transicao|conflict-check|formalizar   │
│  + /honorarios, /pagamentos, /clientes/*, /documentos/*, /eventos/*   │
├──────────────────────────────────────────────────────────────────────┤
│  models/  (JPA @Entity, Lombok @Builder/@Data)                        │
│  Processo · Parte · ProcessoFase · Movimentacao · Honorario ·         │
│  Documento · ConflictCheckDecisao · Prazo                             │
├──────────────────────────────────────────────────────────────────────┤
│  repositories/  (Spring Data JpaRepository, one per entity)           │
│  PostgreSQL — ddl-auto=update (dev) / validate (prod)                 │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities (existing, verified)

| Component | Responsibility | File |
|-----------|----------------|------|
| `ResourceController` | Single controller for ~95% of `/api/v1` CRUD, including all Processo sub-resources | `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (2504 lines) |
| `ParecerController` | Separate controller for an entirely separate domain module (Pareceres), own sub-path `/api/v1/pareceres/solicitacoes` | `backend/.../ParecerController.java` (518 lines) |
| `ParecerPesquisaController` | Split out from `ParecerController` **solely to fix a Spring routing collision** (class + method mapping concatenation bug), not an organizational precedent | `backend/.../ParecerPesquisaController.java` (58 lines) |
| `use-processos.ts` | All TanStack Query hooks for Processo + sub-resources (partes, fases, movimentações, prazos, timeline, workflow, conflict-check, formalizar) | `web/src/hooks/use-processos.ts` |
| `use-financeiro.ts` | Hooks for `Honorario`/`Pagamento` — already exists, already has `useHonorarios`, `useCreateHonorario` | `web/src/hooks/use-financeiro.ts` |
| `use-documentos.ts` | Generic `Documento` hooks: list (with `cliente_id`/`processo_id` filters), upload w/ progress, delete, download | `web/src/hooks/use-documentos.ts` |
| `[id]/page.tsx` | Processo detail page — button-toggle tab group (`TabKey = "timeline" \| "partes" \| "fases" \| "auditoria"`), NOT shadcn `Tabs` | `web/src/app/(dashboard)/processos/[id]/page.tsx` (1436 lines) |
| `[id]/editar/page.tsx` | **Separate** edit route — Processo was NOT unified into a single view/edit component the way Cliente was in v2.8 (Phase 75). This is a divergence to be aware of, not necessarily to fix in this milestone. | `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` |

## Key Precedent Clarification (governs decision `(a)` in the question)

**`ParecerPesquisaController` is NOT a precedent for "split out sub-resources into a dedicated controller."** Its own doc comment states plainly it exists because `ParecerController` is mapped at class-level `@RequestMapping("/api/v1/pareceres/solicitacoes")`, and Spring concatenates class + method mappings regardless of leading `/` — a sibling top-level route (`/api/v1/pareceres/pesquisa`) could not be reached from inside that controller. `ResourceController` has no such problem: its class-level mapping is the bare `@RequestMapping("/api/v1")`, so any new path segment (`/processos/{id}/decisoes`, `/processos/{id}/factos`, `/processos/{id}/testemunhas`) can be added as new methods inside it with zero routing risk.

**Recommendation: add all three new entities' endpoints inside the existing `ResourceController`.** This is consistent with how `Parte`, `ProcessoFase`, and `Movimentacao` (the three existing Processo child entities) are already implemented — all live in `ResourceController`, not separate controllers. Introducing a new controller for Decisão/Facto/Testemunha would be an unforced architectural inconsistency with zero technical justification (no routing collision exists, no separate RBAC domain is being introduced — all three reuse `processos:*` scopes). Only split into a dedicated controller if `ResourceController` triggers a real problem (e.g., file becomes unmanageable for the team, or a genuine routing collision appears) — neither condition applies here.

## Recommended Project Structure (additions only — no restructuring)

```
backend/src/main/java/com/lexcv/
├── models/
│   ├── Processo.java                # MODIFIED: + juizo (String), + origem (enum, @Enumerated STRING)
│   ├── OrigemProcesso.java          # NEW: enum { PETICAO_INICIAL, NOTIFICACOES_AVULSAS }
│   ├── Decisao.java                 # NEW: entity, mirrors Parte.java's shape (Integer IDENTITY id, processo_id FK, no tenant_id — tenant is derived via processo)
│   ├── TipoDecisao.java             # NEW: enum for `tipo` field (values TBD by legal domain — flag for phase-specific research)
│   ├── Facto.java                   # NEW: entity, mirrors Parte.java's shape
│   └── Testemunha.java              # NEW: entity, mirrors Parte.java's shape
├── repositories/
│   ├── DecisaoRepository.java       # NEW: extends JpaRepository<Decisao, Integer>; findByProcessoId(UUID)
│   ├── FactoRepository.java         # NEW: extends JpaRepository<Facto, Integer>; findByProcessoId(UUID), ordered by `ordem`
│   └── TestemunhaRepository.java    # NEW: extends JpaRepository<Testemunha, Integer>; findByProcessoId(UUID)
└── controllers/
    └── ResourceController.java      # MODIFIED: + 3 new @Autowired repositories in constructor,
                                      #   + list/create/update/delete endpoints per new entity,
                                      #   + juizo/origem wiring in createProcesso/updateProcesso/
                                      #     createProcessoIntake/listProcessos (enriched map),
                                      #   + Honorario auto-creation hook inside formalizarProcesso()

web/src/
├── types/processos.ts               # MODIFIED: + Processo.juizo, Processo.origem
                                      #   + Decisao, DecisaoCreateRequest, DecisaoUpdateRequest
                                      #   + Facto, FactoCreateRequest, FactoUpdateRequest
                                      #   + Testemunha, TestemunhaCreateRequest, TestemunhaUpdateRequest
├── schemas/processos.ts             # MODIFIED: + processoFormSchema.juizo/origem
                                      #   + decisaoFormSchema, factoFormSchema, testemunhaFormSchema
├── hooks/use-processos.ts           # MODIFIED: + useProcessoDecisoes/useAddProcessoDecisao/useUpdateProcessoDecisao/useDeleteProcessoDecisao
                                      #   (same trio for Facto, Testemunha)
├── hooks/use-financeiro.ts          # UNCHANGED (already has useHonorarios/useCreateHonorario — reused as-is by the printable Termo page)
└── app/(dashboard)/processos/
    ├── novo/page.tsx                # MODIFIED: + campo "origem" (required, radio/select) in step 1 intake form
    ├── [id]/page.tsx                # MODIFIED: + TabKey union grows to include "decisoes" | "factos" | "testemunhas" | "documentos"
                                      #   + 4 new button-toggle tab bodies (functions colocated in this file,
                                      #     matching the existing pattern — NOT split into separate component files,
                                      #     since [id]/page.tsx keeps all tab bodies inline today)
                                      #   + "Dados" card gains Juízo/Origem <dt>/<dd> rows
    ├── [id]/editar/page.tsx         # MODIFIED: + Juízo input (origem is NOT editable here — immutable-ish per spec)
    └── [id]/termo-honorarios/page.tsx  # NEW: printable page, CSS-print pattern copied from
                                      #   web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
```

### Structure Rationale

- **New entities as siblings of `Parte`, not `Documento`-style with `tenant_id`:** `Parte` and `ProcessoFase` have no `tenant_id` column — tenant isolation is enforced transitively by first loading and tenant-checking the parent `Processo`, then trusting the `processo_id` FK. `Decisao`/`Facto`/`Testemunha` should follow the exact same shape (leaner schema, one less column, one less thing to keep in sync) rather than the `Documento`/`Movimentacao`-with-own-`tenant_id` pattern. Every existing endpoint for `partes`/`fases` already does `processoRepository.findById(id)` + tenant check first — replicate this guard verbatim for the three new resources.
- **All three new entities' repositories/endpoints inside `ResourceController`:** see precedent clarification above. Avoids introducing an unjustified second controller for what is functionally identical to `Parte`/`Movimentacao`.
- **No new RBAC scopes:** `processos:view/edit/create/manage` already exist and are seeded (`DatabaseSeeder.java` line ~296-297); the frontend already mirrors them via `permissions.ts`'s `"processos"` scope entry. All new endpoints reuse these four scopes exactly as `Parte`/`Movimentacao` do today — no `DatabaseSeeder` changes needed.
- **`[id]/page.tsx` stays a single growing file, not split into sub-components:** Unlike the Cliente detail page (which extracted `ClienteDocumentosEntreguesTab`, `ClienteProcessosTab`, `ClienteParecerTab` as named functions *within the same file*, not separate files), Processo's `[id]/page.tsx` currently keeps all tab bodies inline in the render tree with `tab === "x" ? (...) : ...` ternaries. Follow whichever pattern already exists in the file at time of implementation — but note the Cliente precedent (named tab-body functions in the same file) is the more maintainable version and the one explicitly reused for the new "Documentos" tab per the milestone brief.

## Architectural Patterns

### Pattern 1: Child-entity CRUD scoped through parent tenant check

**What:** For any `/processos/{id}/<child>` resource, every endpoint (GET list, POST create, PUT/PATCH update, DELETE) begins by loading the parent `Processo`, checking `processo.getTenantId().equals(getTenantId())`, and 404-ing otherwise — the child entity itself carries no `tenant_id`.
**When to use:** All three new entities (Decisão, Facto, Testemunha), and the new "Documentos" tab reuses the *existing* `GET /processos/{id}/documentos` which already does this.
**Trade-offs:** Simpler schema (no redundant `tenant_id`), but every child endpoint pays one extra `findById` — acceptable at current data volumes; matches `Parte`/`ProcessoFase`/`Movimentacao` exactly.

**Example (from `ResourceController.java:1521-1539`, `createParte`):**
```java
@PreAuthorize("hasAuthority('processos:edit')")
@PostMapping("/processos/{id}/decisoes")
public ResponseEntity<?> createDecisao(@PathVariable UUID id, @RequestBody Decisao decisao) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    decisao.setProcessoId(id);
    return ResponseEntity.status(HttpStatus.CREATED).body(decisaoRepository.save(decisao));
}
```

### Pattern 2: List+Create+Update+Delete vs. List+Append-only — decided per entity by mutability of real-world data

**What:** `Parte`/`Movimentacao` are list+create only (append-only — no PUT/DELETE exists for either in `ResourceController` today). `ProcessoFase` gets list+create+update (status transitions). No existing Processo child entity has DELETE.
**When to use — applied to the 3 new entities:**
- **Decisão** (data, tipo, resumo, anexo): court decisions are historical record — once entered, corrections are rare but real (a typo in `resumo`, wrong `anexo`). Recommend **list + create + update + delete** (full CRUD) — closer to `Prazo`'s CRUD shape than `Movimentacao`'s append-only shape, because a Decisão is a discrete user-managed record, not an automatically-generated log line.
- **Facto** (descrição, data, ordem): needs reordering (`ordem` field implies drag-to-reorder or up/down UI), so update is mandatory. Recommend **list + create + update + delete**.
- **Testemunha** (nome, contacto, tipo/arrolada por, notas): contact details change (phone numbers, notes), so update is needed. Recommend **list + create + update + delete**.

**Minimal endpoint set (all three entities, identical shape):**
```
GET    /processos/{id}/decisoes         processos:view
POST   /processos/{id}/decisoes         processos:edit
PUT    /processos/{id}/decisoes/{did}   processos:edit
DELETE /processos/{id}/decisoes/{did}   processos:edit
```
(repeat verbatim for `/factos` and `/testemunhas`)

This is a deliberate *addition* of DELETE beyond what `Parte`/`Movimentacao` support today — justified because none of the three new entities are pure event logs (unlike `Movimentacao`, which is explicitly an append-only audit trail feeding the Timeline). If the roadmapper wants to minimize scope, DELETE can be deferred to a later phase without blocking the rest — GET+POST+PUT alone is a functioning MVP for all three.

**Trade-offs:** Full CRUD is 4 endpoints × 3 entities = 12 new endpoints (vs. 6 for append-only). Given `processos:edit` already gates all of them, this is a controller-size cost only, not an RBAC cost.

### Pattern 3: Server-side auto-creation of a related entity inside an existing state-transition endpoint

**What:** `formalizarProcesso()` already performs multiple validation gates (state check, required-fields check, conflict-decision check) before mutating `processo.setEstado("ATIVO")` and saving. This is the correct and *only* place to hook Honorario auto-creation — it is the single authoritative "processo just became ATIVO" transition point in the codebase (the generic `PUT /processos/{id}` explicitly excludes `estado` changes, and `/transicao/{acao}` handles ATIVO→SUSPENSO/ENCERRADO/REABERTO, not TRIAGEM→ATIVO).

**Exact hook point — `ResourceController.java`, inside `formalizarProcesso`, immediately after all gates pass and before/alongside the final save (around current line 1233-1235):**

```java
// Transition TRIAGEM -> ATIVO
processo.setEstado("ATIVO");
Processo saved = processoRepository.save(processo);

// Auto-create Honorario for the newly formalized processo (idempotency guard —
// formalizar can only run once per processo because of the TRIAGEM-only gate at
// the top of this method, so no duplicate-check is strictly required, but a
// defensive existsByProcessoId check costs one query and protects against any
// future relaxation of that gate)
if (honorarioRepository.findByProcessoId(saved.getId()).isEmpty()) {
    Honorario honorario = Honorario.builder()
            .processoId(saved.getId())
            .valorTotal(BigDecimal.ZERO)   // left blank/0 for the user to fill in Financeiro
            .descricao(null)
            .dataAcordo(LocalDate.now())
            .build();
    honorarioRepository.save(honorario);
}

return ResponseEntity.ok(saved);
```

**Pre-fill values — verified against actual `Honorario` entity shape (`Honorario.java`), which has NO `clienteId` and NO `estado` field:**
| Field | Value | Rationale |
|-------|-------|-----------|
| `processoId` | `saved.getId()` | The only FK the entity has — `clienteId` is NOT a column on `Honorario` (it's reachable transitively via `Processo.clienteId` if ever needed in a DTO/join, but do not add a redundant column for this milestone — out of scope, not requested) |
| `valorTotal` | `BigDecimal.ZERO` | Per spec: "valor left blank/0 for the user to fill" |
| `descricao` | `null` | No sensible default; user fills via existing Financeiro edit flow (`useUpdateHonorario`) |
| `dataAcordo` | `LocalDate.now()` | Reasonable default (date processo became ATIVO); user can edit |
| `totalPago` | N/A — `@Formula`-computed, always 0 until a `Pagamento` exists | No action needed |
| `estado` | **N/A — field does not exist on `Honorario`.** If the roadmap genuinely wants an explicit honorario lifecycle status, that is a new column + migration, out of the "auto-create on formalizar" scope — flag as a possible follow-up requirement, not something to silently invent during this integration. | |

**Transaction/atomicity note:** `formalizarProcesso` is not currently annotated `@Transactional` (verify at implementation time — several other multi-write methods in the file, e.g. `runConflictCheck`, are not either, following the existing project convention of relying on each `repository.save()` being its own transaction). Since Honorario auto-creation is a second write in the same method, wrap the method in `@Transactional` when implementing, so a failure saving the Honorario rolls back the `estado` change too — this is a **new** requirement this feature introduces (the method currently only does one write, so atomicity was never a concern before).

**RBAC note:** Auto-creation happens via direct `honorarioRepository.save()` inside a method gated by whatever `@PreAuthorize` `formalizarProcesso` already carries (verify at the method — currently no explicit `@PreAuthorize` annotation is visible directly above it in the excerpt reviewed; confirm during implementation whether it inherits a class-level rule or needs one added). This bypasses the `financeiro:edit` check that normally gates `POST /honorarios` — that is intentional and correct (the user is not creating a Honorario, they are formalizing a Processo; the system creates the Honorario as a side effect), but it means a user with `processos:manage` but NOT `financeiro:edit` can indirectly cause a Honorario row to exist. Confirm this is acceptable — flagged as a design decision, not a research gap.

### Pattern 4: Printable document via CSS-print, not a PDF library

**What:** `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` establishes the pattern: a dedicated Next.js page (own route, e.g. `[id]/ficha`) that renders a `<style dangerouslySetInnerHTML>` block with `@media print { aside, header, [data-print-hide] { display:none } } @page { size:A4; margin:2cm }`, a `data-print-hide`-marked "Voltar"/"Imprimir" button bar, blank-line placeholders (`___________`) for empty fields via a `fmt()` helper, and a `window.print()` trigger button. No PDF library (`jsPDF`, `react-pdf`, `puppeteer`, etc.) is used or should be introduced.
**When to use:** The new "Termo de Honorários" printable page.
**Trade-offs:** Relies on the browser's print-to-PDF (user must choose "Save as PDF" in the print dialog if a file is needed) — acceptable, matches existing UX and explicitly avoids adding a new dependency (consistent with the v2.6 Key Decision: "Nenhuma nova dependência frontend... reuso total de padrões existentes").

**Recommended new route:** `web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx`, structurally copying `clientes/[id]/ficha/page.tsx`:
```typescript
const PRINT_CSS = `
  @media print {
    aside, header, [data-print-hide], .bottom-nav, .ficha-print-btn {
      display: none !important;
    }
    body { background: white !important; }
  }
  @page { size: A4; margin: 2cm; }
`;
// Data sources: useProcesso(id), useCliente(processo.data.cliente_id),
// useHonorarios({ processoId: id }) — ALL THREE HOOKS ALREADY EXIST, no new hooks needed
// for the printable page itself beyond what use-financeiro.ts already exports.
```

## Data Flow

### Request Flow — new Decisão example
```
[User clicks "Adicionar Decisão" in Processo detail tab]
    ↓
[Dialog form, react-hook-form + Zod decisaoFormSchema] → [useAddProcessoDecisao(id) mutation]
    ↓
apiFetch("/processos/{id}/decisoes", POST) → Next.js rewrite → Spring Boot
    ↓
ResourceController.createDecisao() → tenant-check via Processo → decisaoRepository.save()
    ↓
[queryClient.invalidateQueries(["processos","decisoes", id])] ← 201 Created ← saved entity
    ↓
[Tab re-renders with new Decisão in list]
```

### Request Flow — formalizar + Honorario auto-creation
```
[User clicks "Formalizar" in novo/page.tsx step 3, or wherever formalizar is triggered]
    ↓
useFormalizarProcesso(processoId).mutateAsync() → POST /processos/{id}/formalizar
    ↓
ResourceController.formalizarProcesso():
  1. state gate (must be TRIAGEM)
  2. required-fields gate (per tipo_processo)
  3. conflict-decision gate (must exist, must not be "impeditivo")
  4. processo.setEstado("ATIVO"); save
  5. [NEW] honorarioRepository.save(prefilled Honorario) if none exists yet
    ↓
200 OK ← updated Processo (Honorario is a side effect, not in the response body —
         frontend does NOT need to change its response handling; if the UI wants
         to surface "Honorário criado automaticamente" it should separately call
         useHonorarios({processoId}) after formalizar succeeds, or just navigate
         the user to Financeiro/the new Termo page where useHonorarios already fetches it)
```

### Key Data Flows

1. **Juízo/origem enrichment must be threaded through 3 places, not just the entity:** `Processo.java` (column), `createProcesso`/`updateProcesso`/`createProcessoIntake` (accept + persist), AND the `listProcessos` enriched-map builder (`ResourceController.java:909-952`) which manually constructs a `LinkedHashMap` field-by-field rather than serializing the entity directly — **omitting `juizo`/`origem` from that map means the list view will silently never show them even though the detail view (`getProcesso`, which returns the raw entity) does.** This is the single highest-risk integration gap in this milestone; flag for the phase that implements the list endpoint.
2. **`origem` is set once at intake, never exposed for edit:** `createProcessoIntake` sets `estado` unconditionally; `origem` should be set the same way (required field on the intake DTO/form, written once, then excluded from `updateProcesso`'s field-copy list exactly the way `estado` already is — see `ResourceController.java:991-1001`, which explicitly comments "estado is intentionally excluded"). Add a matching comment for `origem` when implementing.
3. **Documentos tab is read-only wiring, not new backend work:** `GET /processos/{id}/documentos` already exists and is already used implicitly by the generic `/documentos` hook infrastructure (`useDocumentos({ processo_id })` — confirm exact filter param name matches `use-documentos.ts`'s `DocumentosListFilters` type before wiring the new tab; the Cliente-side equivalent used `cliente_id`, verify the Processo-side hook already supports `processo_id` symmetrically or needs a one-line addition).

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|---------------------|
| Current (single ResourceController, ~10s of processos/tenant) | No change needed — adding 3 entities × ~4 endpoints to a 2504-line controller keeps it under ~2700 lines, still manageable by the project's own established tolerance (already larger than this before Pareceres was split into its own *domain* module, not a sub-resource split) |
| If ResourceController exceeds ~3500-4000 lines | Consider splitting by *domain*, mirroring the Pareceres precedent (a controller per top-level resource — `ProcessoController`, `ClienteController`, `FinanceiroController`) rather than per sub-resource. Out of scope for this milestone; note for a future "controller cleanup" milestone if it becomes a real pain point. |
| Decisão/Facto/Testemunha row counts per processo | Low cardinality (tens, not thousands) — no pagination needed on `GET /processos/{id}/<child>` list endpoints, consistent with existing `Parte`/`Movimentacao` lists. |

## Anti-Patterns to Avoid

### Anti-Pattern 1: Adding `tenant_id` to the three new entities
**What people might do:** Copy `Documento`'s shape (which does carry `tenant_id`) out of an abundance of caution.
**Why it's wrong:** Inconsistent with the *actual* nearest sibling (`Parte`, `ProcessoFase`, `Movimentacao` — the other three Processo child entities), adds a column that must be kept in sync with the parent, and provides no additional security since every access path already tenant-checks via the parent `Processo`.
**Instead:** Follow `Parte.java` exactly — `Integer` IDENTITY `id`, `processo_id` FK column, no `tenant_id`.

### Anti-Pattern 2: Introducing a PDF-generation library for "Termo de Honorários"
**What people might do:** Reach for `jsPDF`/`pdfmake`/`puppeteer` because "printable legal document" sounds like it needs real PDF generation.
**Why it's wrong:** Directly contradicts the milestone brief's explicit instruction ("reuse the CSS-print pattern... not a new PDF library") and the project's own v2.6 Key Decision precedent of avoiding new frontend dependencies when an existing pattern suffices.
**Instead:** Copy `clientes/[id]/ficha/page.tsx`'s `PRINT_CSS` + `window.print()` approach verbatim.

### Anti-Pattern 3: Splitting Decisão/Facto/Testemunha into a new controller "for organization"
**What people might do:** Cite `ParecerPesquisaController` as precedent for splitting sub-resources out of a large controller.
**Why it's wrong:** That split fixed a genuine Spring routing bug (class+method mapping concatenation), not a stylistic preference. No such bug exists or would be introduced by adding `/processos/{id}/decisoes` etc. to `ResourceController`, which is mapped at bare `/api/v1`.
**Instead:** Add the new endpoints to `ResourceController`, matching where `Parte`/`ProcessoFase`/`Movimentacao` already live.

### Anti-Pattern 4: Forgetting the `listProcessos` enriched-map when adding `juizo`/`origem`
**What people might do:** Add the columns to `Processo.java` and to `createProcesso`/`updateProcesso`, verify the detail page works (because `getProcesso` returns the raw entity), and ship — missing that the *list* endpoint hand-builds a `Map<String,Object>` and will silently drop the new fields from `GET /processos`.
**Why it's wrong:** Any list-view surfacing of Juízo/Origem (e.g., a future filter or column) would silently fail to work, and it's easy to not notice during manual testing if only the detail page is checked.
**Instead:** Explicitly add `m.put("juizo", p.getJuizo())` and `m.put("origem", p.getOrigem())` to the enrichment loop at `ResourceController.java` (~line 909-952) in the same phase that adds the columns.

## Integration Points

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `Processo` ↔ `Decisao`/`Facto`/`Testemunha` | Direct FK (`processo_id`), same-controller REST endpoints | No event bus, no async — synchronous request/response like every other Processo child entity |
| `Decisao.anexo` ↔ `Documento` | Nullable FK column `documento_id` on `Decisao`, pointing at an existing `t_documento` row uploaded via the existing generic `/documentos/upload?processoId=...` flow | Do NOT duplicate `ParecerVersao`'s embedded `caminhoAnexo` string pattern — that pattern exists specifically to solve a `Persistable.isNew()` edge case tied to pre-assigning UUIDs for MinIO key prefixes at upload time (see `ParecerVersao.java` comment), which does not apply here since Decisão attaches an *already-uploaded* Documento by reference, it doesn't perform the upload itself |
| `formalizarProcesso()` ↔ `Honorario` auto-creation | Direct in-process `honorarioRepository.save()` call inside the existing controller method, no new endpoint | Bypasses `financeiro:edit` `@PreAuthorize` intentionally (side effect of a `processos:*`-gated action) — confirm this is accepted before implementing |
| Frontend `[id]/page.tsx` new tabs ↔ `use-processos.ts` new hooks | TanStack Query, same `queryKey` convention (`["processos", "<subresource>", id]`) already used by `partes`/`fases`/`movimentacoes` | New hooks should follow the exact `useProcessoPartes`/`useAddProcessoParte` naming/shape convention already in the file |
| "Documentos" tab ↔ existing `GET /processos/{id}/documentos` | Reuses `useDocumentos` (or a processo-scoped variant) — **no new backend endpoint needed**, only frontend wiring | Mirror `ClienteDocumentosEntreguesTab` (`web/src/app/(dashboard)/clientes/[id]/page.tsx:1223-1390`) — same upload dialog, same `FileDropZone`, same datalist-for-tipo pattern, swap `cliente_id` for `processo_id` |

## Suggested Build Order (backend entities → endpoints → frontend types/hooks → frontend tabs)

This follows the same dependency-first principle the milestone context cites from v2.8 (Phase 74-before-75: enum/entity foundations land before UI that depends on their final shape).

**Phase A — Foundations (no UI-visible change yet):**
1. `Processo.java`: add `juizo` (String) and `origem` (new `OrigemProcesso` enum: `PETICAO_INICIAL`, `NOTIFICACOES_AVULSAS`) columns.
2. New entities: `Decisao.java` (+ `TipoDecisao` enum — needs the actual enum values confirmed against legal-domain requirements, flag as open question), `Facto.java`, `Testemunha.java`, each mirroring `Parte.java`'s shape, plus their three repositories.
3. Confirm `ddl-auto=update` picks up new columns/tables in dev; no manual migration needed per existing project convention (see `Cliente.numero_cliente`/`documento_tipo` precedents — no separate migration files exist in this repo's established pattern).

**Phase B — Backend endpoints:**
4. Wire `juizo`/`origem` into `createProcesso`, `updateProcesso` (with `origem` excluded from update, matching the `estado`-exclusion comment pattern), `createProcessoIntake` (required field), and — critically — the `listProcessos` enriched map (Anti-Pattern 4 above).
5. Add list/create/update/delete endpoints for `/processos/{id}/decisoes`, `/factos`, `/testemunhas` inside `ResourceController`, gated by existing `processos:view`/`processos:edit` scopes, following the `Parte`/`ProcessoFase` tenant-check pattern.
6. Hook Honorario auto-creation into `formalizarProcesso()` (Pattern 3 above); add `@Transactional` to the method.
7. No backend work needed for the "Documentos" tab — endpoint already exists.

**Phase C — Frontend types/schemas/hooks:**
8. `types/processos.ts`: extend `Processo`/`ProcessoCreateRequest`/`ProcessoUpdateRequest` with `juizo`/`origem`; add `Decisao`/`Facto`/`Testemunha` types + Create/Update request types.
9. `schemas/processos.ts`: extend `processoFormSchema` with `juizo`/`origem`; add `decisaoFormSchema`/`factoFormSchema`/`testemunhaFormSchema`.
10. `hooks/use-processos.ts`: add the list/create/update/delete hook trio (quad, with delete) for each of the 3 new entities, following `useProcessoPartes`/`useAddProcessoParte` naming.
11. Verify/extend `use-documentos.ts`'s filter type to confirm `processo_id` filtering works identically to the `cliente_id` filtering already proven in v2.8 Phase 79.

**Phase D — Frontend UI (depends entirely on B and C being stable):**
12. `novo/page.tsx`: add required "Origem" field to intake step 1 form.
13. `[id]/page.tsx` "Dados" card: add Juízo/Origem display rows.
14. `[id]/editar/page.tsx`: add Juízo input (origem NOT editable here).
15. `[id]/page.tsx`: add 3 new tabs (Decisões, Factos, Testemunhas) to the `TabKey` union and button group, each with its own list+add(+edit+delete) UI, following the existing `partes`/`fases` tab bodies as templates.
16. `[id]/page.tsx`: add "Documentos" tab, copying `ClienteDocumentosEntreguesTab` structure verbatim (swap cliente_id→processo_id, swap permission scope if needed — confirm `documentos:view`/`documentos:edit` vs `processos:*` for this tab, matching whatever the existing `GET /processos/{id}/documentos` endpoint's `@PreAuthorize` already requires: `documentos:view`).
17. New route `[id]/termo-honorarios/page.tsx`: printable Termo de Honorários, copying `clientes/[id]/ficha/page.tsx`'s CSS-print pattern, sourcing data from `useProcesso`, `useCliente`, and the already-existing `useHonorarios({ processoId })`.

**Ordering rationale:** Steps 1-3 (entity/enum foundations) must land before step 5 (endpoints reference these types) and before step 8 (frontend types mirror the backend shape — if the backend enum values for `TipoDecisao`/`OrigemProcesso` change after frontend Zod schemas are written, every dependent form breaks, exactly the failure mode the v2.8 Phase 74→75 ordering was designed to avoid). Step 6 (Honorario auto-creation) has no dependency on the other 6 target features and can be built/shipped independently/in parallel with the Decisão/Facto/Testemunha work if the roadmapper wants to parallelize phases. Step 17 (Termo printable) depends only on `Honorario` existing (already true today) — it does NOT depend on step 6 being done first, since a manually-created Honorario would also render fine; sequencing them together is a convenience, not a hard dependency.

## Open Questions / Gaps for Phase-Specific Research

- **`TipoDecisao` enum values:** the milestone context says "tipo enum" for Decisão but does not enumerate the values (e.g., `SENTENCA`, `ACORDAO`, `DESPACHO`, `DECISAO_INTERLOCUTORIA`...). This needs a domain-expert decision before Phase A, Step 2 can be finalized — flag as a roadmap-blocking question, not something this research can resolve from the codebase alone.
- **`Testemunha.tipo` ("arrolada por") values:** likely `AUTOR`/`REU` or similar — same gap, needs legal-domain input.
- **Whether `formalizarProcesso()` currently has an explicit `@PreAuthorize`:** verify at implementation time (not conclusively visible in the reviewed excerpt) — affects who can trigger the Honorario side-effect.
- **Whether Processo should be unified into a single view/edit component like Cliente was in v2.8 Phase 75:** out of this milestone's stated scope (`[id]/editar/page.tsx` still exists as a separate route), but worth flagging to the roadmapper as a known architectural divergence between the two modules, in case a future milestone wants parity.
- **`TimelineItemType` already contains a string literal `"decisao"`** (`web/src/types/processos.ts` line ~197), currently used for `ConflictCheckDecisao` entries in the unified timeline. The new "Decisão" entity (court decisions) is a *different* concept sharing the same Portuguese word — decide during Phase D whether the new entity should also feed the Timeline (and if so, how to disambiguate the two "decisao" kinds in the `TimelineItem` shape) or stay purely in its own tab. Flag this naming collision explicitly to avoid an accidental merge of unrelated concepts.

## Sources

- Direct repository inspection (all findings HIGH confidence, verified against current source — no training-data assumptions used):
  - `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
  - `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
  - `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java`
  - `backend/src/main/java/com/lexcv/models/Processo.java`, `Parte.java`, `Movimentacao.java`, `Honorario.java`, `Documento.java`, `ParecerVersao.java`, `TipoCliente.java`, `DocumentoTipo.java`
  - `backend/src/main/java/com/lexcv/repositories/ParteRepository.java`, `MovimentacaoRepository.java`
  - `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java`
  - `web/src/hooks/use-processos.ts`, `use-financeiro.ts`, `use-documentos.ts`
  - `web/src/types/processos.ts`, `financeiro.ts`
  - `web/src/schemas/processos.ts`
  - `web/src/app/(dashboard)/processos/[id]/page.tsx`, `novo/page.tsx`, `[id]/editar/page.tsx`
  - `web/src/app/(dashboard)/clientes/[id]/page.tsx` (ClienteDocumentosEntreguesTab)
  - `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` (CSS-print pattern)
  - `.planning/PROJECT.md` (Key Decisions log, v2.8/v2.6 precedents)

---
*Architecture research for: LexCV v2.9 Melhoria Módulo Processos*
*Researched: 2026-07-07*
