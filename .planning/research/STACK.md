# Technology Stack

**Project:** LexCV — v2.9 Melhoria Módulo Processos
**Researched:** 2026-07-07

## Headline Finding

**No new dependency is required for this milestone.** All seven target features map directly onto patterns already shipped and validated in v2.4 (Ficha Cliente imprimível) and v2.8 (Documentos upload, lazy-mount tabs, `ClienteContacto`/`ClienteNota`-style child entities). This milestone is pure "apply existing pattern to a new module" work, not new-capability work.

| # | Feature | Existing pattern to reuse | New dependency? |
|---|---------|---------------------------|------------------|
| 1 | Campo `juizo` (free text) | Flat column on `Processo`, same as `tribunal`/`tipoProcesso`/`areaJuridica` | No |
| 2 | Campo `origem` (enum) | Free-text string column validated client+server, same as `documento_tipo` restriction pattern (v2.8) | No |
| 3 | Entidade `Decisao` | `ClienteContacto`/`ClienteNota` child-entity pattern | No |
| 4 | Entidade `Facto` | Same child-entity pattern | No |
| 5 | Entidade `Testemunha` | Same child-entity pattern | No |
| 6 | Aba "Documentos" no processo | `ClienteDocumentosEntreguesTab` pattern — and `GET /processos/{id}/documentos` **already exists** in the backend | No |
| 7 | Honorário automático + Termo de Honorários imprimível | `formalizarProcesso()` hook point (already exists) + Ficha Cliente `window.print()`/CSS-print pattern (v2.4) | No |

## Recommended Stack (unchanged)

### Core Framework
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring Boot | 3.4.1 (Java 23) | Backend REST API | Existing, unchanged |
| Next.js | 16.2.6 (App Router) | Frontend | Existing, unchanged |
| React | 19.2.4 | UI | Existing, unchanged |

### Data / Forms (unchanged)
| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| @tanstack/react-query | ^5.87.4 | Server state / data fetching | Existing hook pattern (`use-*.ts`) extends cleanly to 3 new entities |
| react-hook-form | ^7.62.0 | Forms | Existing |
| zod | ^4.1.5 | Schema validation | Existing |
| PostgreSQL | (project-pinned) | Database | Existing; 3 new tables, 1 new column, no engine change |

### Explicitly NOT Adding

| Candidate library | Why it looks tempting | Why to reject it |
|---|---|---|
| PDF generator (e.g. `pdf-lib`, `jsPDF`, `react-pdf`, or a backend lib like OpenPDF/iText/Flying Saucer) | "Termo de Honorários" sounds like a document-generation problem | The v2.4 Ficha Cliente already solved the identical problem — a printable, office-form-accurate one-page document — with a CSS `@media print` stylesheet + browser-native `window.print()`, zero dependencies. The Termo de Honorários brief (advogada + cliente + valor + forma de pagamento + local/data + assinaturas) is materially simpler than the Ficha Cliente (which already prints multi-section forms with checkboxes and blank-fill lines). No server-side rendering, no headless-Chrome/wkhtmltopdf sidecar, no client PDF bundle needed. |
| `docx` generation library (e.g. `docx` npm package, Apache POI on backend) | Reference attachment is a `.docx` template | Rendering a web page styled to match the reference document and letting the user print / "Save as PDF" via the OS print dialog is the same approach already accepted for Ficha Cliente. Introducing docx generation would require binary template manipulation, a new backend dependency (POI adds significant JAR weight), and font/layout fidelity work disproportionate to a one-page contract. |
| shadcn/ui `Tabs` component (`@radix-ui/react-tabs`) | 3rd/4th dedicated tab feels like it needs a real tabs primitive | Already explicitly rejected in v2.8 (Key Decision in PROJECT.md: "Ficha de cliente reestruturada em 7 separadores estilo botões-toggle (não `Tabs` do shadcn)"). Processos already uses the same button-toggle tab pattern (`TabKey` state + `Button variant={tab === x ? "secondary" : "outline"}`) since before v2.8. No Tabs primitive exists anywhere in `web/src/components/ui/`. Follow the established pattern, don't diverge. |
| A generic multi-tenant "child list entity" abstraction/ORM helper | 3 new near-identical entities (Decisao, Facto, Testemunha) could invite a generics/abstraction push | Every existing child entity (`ClienteContacto`, `ClienteNota`, `ClienteAdvogado`, `Movimentacao`, `ProcessoFase`) is a hand-written flat JPA entity + repository + controller block, not a shared abstraction. Introducing generics here would be inconsistent with 5+ prior instances of the same shape and would increase risk for no benefit at this scale (single-digit new tables). |
| A rich-text/markdown editor for `resumo`/`descricao`/`notas` fields | `Decisao.resumo`, `Facto.descricao` could look like "content" fields worth a WYSIWYG | All comparable existing fields (`Cliente.descricao_caso`, `Movimentacao.descricao`, `ClienteNota` body) use plain `<textarea>`/`Input`. No prior use of a rich text editor anywhere in the codebase — do not introduce one now. |

## Backend Considerations (Java/Spring Boot)

### (b1) Three new JPA entities — follow the `ClienteContacto`/`ClienteNota` template exactly

Each of `Decisao`, `Facto`, `Testemunha` should be its own `@Entity` (not a JSON column — see the v2.7 reversal decision logged in PROJECT.md: `"dados_tipo" (coluna JSON única) removida por completo ... o padrão JSON-por-tipo mostrou-se mais difícil de validar/manter do que colunas planas para este caso específico`). Confirmed template, taken directly from `backend/src/main/java/com/lexcv/models/ClienteContacto.java`:

```java
@Entity
@Table(name = "t_processo_decisao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Decisao {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    private LocalDate data;
    private String tipo;      // sentença | despacho | acórdão
    private String resumo;
    @Column(name = "documento_id")
    private UUID documentoId; // optional FK to existing t_documento (anexo)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

Each of the three needs: entity + `*Repository extends JpaRepository<X, UUID>` with a `findByTenantIdAndProcessoId(...)` query method + a CRUD block appended to `ResourceController.java` (the ~1000+-line controller is the established convention — do not split it out for these three; `ClienteContacto`/`ClienteNota`/`ClienteAdvogado`/`Parte`/`Movimentacao`/`ProcessoFase` all live there too) + `@PreAuthorize("hasAuthority('processos:view'|'processos:edit')")` reusing the existing `processos:*` scope (do not invent new RBAC scopes — Decisão/Facto/Testemunha are sub-resources of Processo, same as Partes/Fases/Movimentações already are).

**`Decisao.anexo` (optional):** store as a nullable FK (`documento_id UUID`) to the existing generic `t_documento` table rather than inventing file-handling logic. Upload flow reuses `POST /documentos/upload` with a `processoId` param (already supported — confirmed at `ResourceController.java` line 1983-2062) — then the created `Decisao` row is updated with the returned `documento.id`. This avoids duplicating file-column logic and stays consistent with how `Cliente.procuracao` and the new processo "Documentos" tab both point at `t_documento`.

**`Facto.ordem`:** plain `Integer` column, client-managed — since explicit manual ordering is requested (unlike other list entities in the app, which sort by `createdAt`), add the column and let the frontend submit/edit the position; sort `ORDER BY ordem` in the repository query.

### (b2) One new enum value (`origem`) and one new free-text column (`juizo`) on `Processo`

Both are trivial additive columns on the existing `t_processo` table/entity — same shape as `tribunal`/`areaJuridica` today. Standard `ALTER TABLE ADD COLUMN`, nullable by default under `ddl-auto=update` (dev). See PITFALLS.md for the gap this creates against the "obrigatório" (required) requirement on `origem` for pre-existing rows.

`origem` is specified as an enum with two Portuguese-labeled values ("Petição Inicial" | "Notificações Avulsas"). Recommend a **plain `String` column with backend-validated allowed values** (not a JPA `@Enumerated` Java enum) — this matches how `documento_tipo`/`estado`/`tipo` are already handled throughout the codebase, which consistently avoids native Java enums for domain state in favor of validated strings (`Processo.estado`, `Cliente.tipo`, `Documento.tipo`, `Documento.confidencialidade` are all plain `String`). This lets `origem` reuse the exact v2.8 precedent — `documento_tipo` per-cliente-type allowed-value validation, enforced in both layers — for its own allowed-value validation.

### (b3) `jakarta.persistence.validation.mode: none` gotcha applies again

PROJECT.md logs a hard-won lesson from Phase 73.1: adding Bean Validation annotations (`@NotBlank`) directly to an entity field activates JPA-lifecycle validation on **every** `save()` of that entity, including unrelated codepaths that don't touch the field. If `origem` is "obrigatório" (required), enforce it at the **controller/DTO level** (explicit null/blank check in `createProcesso`, mirroring how required-field checks already happen in `formalizarProcesso`'s `camposEmFalta` block) rather than via a Bean Validation annotation on the `Processo` JPA field — otherwise every existing `processoRepository.save()` call elsewhere (fases, movimentações, workflow transitions, this milestone's own new Decisão/Facto/Testemunha flows if they touch `Processo`) risks breaking on legacy rows missing `origem`.

### (b4) Honorário auto-creation hook point — confirmed exact location

`formalizarProcesso()` in `ResourceController.java` (starts at line 1181) already performs the `TRIAGEM → ATIVO` transition with all its guard clauses (campos mínimos, conflict-check decision present, no impeditivo conflict) before `processo.setEstado("ATIVO")` at line 1234. Insert `Honorario` auto-creation immediately after that line, before `return ResponseEntity.ok(processoRepository.save(processo))`. `Honorario` already has every field needed (`processoId`, `valorTotal`, `descricao`, `dataAcordo`) — no entity change required. Source the initial `valorTotal`/terms from the client's existing `honorarios_propostos` intake data (already captured on `Cliente` since v2.4) when available; otherwise create with nulls for the user to complete via the existing Financeiro UI.

**Flag for phase-level planning (not a stack concern):** decide whether Honorário auto-creation should be wrapped in the same transaction as the state transition, so a failure there doesn't silently leave the processo ATIVO with no Honorário.

## Frontend Considerations (React/TanStack Query)

### (c1) Documentos tab — easier than its Clientes v2.8 counterpart, endpoint already exists

Unlike Clientes v2.8 (which needed a **new** `GET /clientes/{id}/documentos` endpoint because the generic `GET /documentos` ignores `cliente_id`/`processo_id`), `GET /processos/{id}/documentos` **already exists** (confirmed at `ResourceController.java` line 2079). This tab is lower-effort than the Clientes version: wire a `ProcessoDocumentosTab` sub-component mirroring `ClienteDocumentosEntreguesTab` — upload via the existing `useUploadDocumentoComProgresso` hook (`web/src/hooks/use-documentos.ts`), list via a processo-scoped documentos query, `documentos:view`/`documentos:edit` RBAC gating identical to the Clientes version.

### (c2) 3rd/4th lazy-mount tab — pattern is proven, no TanStack Query changes needed

PROJECT.md explicitly documents the chosen approach (v2.8 Key Decision): `useProcessos`/`usePareceres`/`useDocumentos` deliberately did **not** get an `enabled` parameter added; instead each tab is a separate sub-component (`ClienteProcessosTab`, `ClienteParecerTab`, `ClienteDocumentosEntreguesTab` — all confirmed present in `web/src/app/(dashboard)/clientes/[id]/page.tsx`) that only mounts — and therefore only fires its query — when its button-toggle tab is active. Apply the identical shape for Processos: `ProcessoDecisoesTab`, `ProcessoFactosTab`, `ProcessoTestemunhasTab`, `ProcessoDocumentosTab` as four new sub-components, each taking `processoId` (and `editable`/RBAC props where mutation is needed), each backed by its own new hook (`use-decisoes.ts`/`use-factos.ts`/`use-testemunhas.ts` — trivial, same shape as `useClienteAdvogados`/`useClienteAdministrativos` in `use-clientes.ts`).

No TanStack Query version change or new plugin needed (no need for v5's `enabled: () => boolean` callback form, no query-level lazy-loading library) — conditional mounting is sufficient and is the codebase's established idiom.

### (c3) Tab-switch state hygiene — apply the documented gotcha proactively

PROJECT.md's Clientes page carries an explicit effect (around line 208-224 of `clientes/[id]/page.tsx`) closing any open "add" dialog and resetting its draft state whenever the active tab changes away from the tab that owns it — because sub-components are **unmounted** (not just hidden) on tab switch, a controlled `open={true}` dialog left over from before the switch would otherwise reopen with stale draft text the moment the user navigates back. If Decisões/Factos/Testemunhas get "Adicionar" dialogs (likely, given the field lists), replicate this same `useEffect` reset keyed off `tab` from day one rather than discovering the bug later.

### (c4) Processo detail page already uses the identical button-toggle tab pattern

`web/src/app/(dashboard)/processos/[id]/page.tsx` already has `const [tab, setTab] = React.useState<TabKey>("timeline")` (line 142) with existing tabs `timeline` / `partes` / `fases` / `auditoria`. Adding `juizo`/`origem` fields is a same-tab addition (goes wherever `tribunal`/`areaJuridica` currently render, not a new tab). Adding `decisoes` / `factos` / `testemunhas` / `documentos` extends the `TabKey` union with four new string literals and four new `<Button variant={tab === x ? "secondary" : "outline"}>` entries, RBAC-gated the same way `canViewProcessos`/`canViewPareceres` already gate tabs on the Clientes page.

### (c5) Termo de Honorários print page — clone the Ficha Cliente route shape

Create a new route (e.g. `web/src/app/(dashboard)/processos/[id]/termo-honorario/page.tsx`), following `clientes/[id]/ficha/page.tsx` closely:
- Same `PRINT_CSS` constant (`@media print { aside, header, [data-print-hide], .bottom-nav, .ficha-print-btn { display: none !important; } ... } @page { size: A4; margin: 2cm; }`).
- Same `BLANK = "___________"` placeholder-fill convention for empty fields — useful here for assinatura lines, valor por extenso, local/data.
- Triggered the same way as Ficha Cliente: a `<Printer />` (lucide-react, already a dependency) icon button opening the route in a new tab (`target="_blank" rel="noopener noreferrer"`), which the user prints via the browser's native print dialog (`window.print()`).
- Data source: the auto-created `Honorario` record (existing Financeiro hooks) joined with `Processo` (for tribunal/juízo context if needed) and `Cliente` (nome, morada) plus the responsável advogado — no new hooks needed beyond what Financeiro and `useProcesso`/`useCliente` already expose.

## Installation

No new packages. No `pnpm add`, no `mvn` dependency additions — this milestone adds only new source files (3 entities + repositories, controller endpoints, 4 frontend hook files, 4+ tab sub-components, 1 print route, 2 new/changed `Processo` fields).

```bash
# Backend: no pom.xml changes
# Frontend: no package.json changes
```

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Termo de Honorários rendering | `window.print()` + scoped `@media print` CSS (Ficha Cliente pattern) | Server-generated PDF (headless Chrome, wkhtmltopdf, iText) | Zero new dependencies, proven pattern already shipped and audited in v2.4; the contract is simpler (one page) than the Ficha Cliente it would clone from |
| Decisão/Facto/Testemunha storage | Dedicated JPA entities, one table each | Single JSON column on `Processo` (like the old `dados_tipo`) | Explicitly reversed as a project-wide pattern in v2.7 for exactly this reason — JSON-per-type proved harder to validate/maintain than flat entities |
| `origem` field type | Validated `String` column | JPA `@Enumerated(EnumType.STRING)` Java enum | No existing domain-state field in the codebase uses a native Java enum; `estado`, `tipo`, `documento_tipo`, `confidencialidade` are all validated strings — consistency with established convention |
| 3rd/4th tab navigation | Manual `useState<TabKey>` + button toggles | shadcn/Radix `Tabs` | No Tabs primitive exists in the repo; explicitly rejected in the v2.8 Key Decision log to preserve a single tab paradigm |
| Documentos tab data source | Existing `GET /processos/{id}/documentos` | New tenant-scoped endpoint (as Clientes needed in v2.8) | Unlike Clientes, the processo-scoped documentos endpoint already exists and is correct — no backend gap to fill here |

## Sources

- Direct repository inspection (HIGH confidence — primary source, not training data):
  - `backend/src/main/java/com/lexcv/models/Processo.java` — current entity shape (confirms `tribunal`, `tipoProcesso`, `areaJuridica` as flat `String` columns; no existing Java enum usage)
  - `backend/src/main/java/com/lexcv/models/ClienteContacto.java` — child-entity template (id/tenantId/parentId/fields/createdAt/`@PrePersist`)
  - `backend/src/main/java/com/lexcv/models/Honorario.java` — existing fields confirm no entity change needed for auto-creation
  - `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (lines 1181-1236: `formalizarProcesso`; lines 1979-2096: Documentos section including upload endpoint and both `/processos/{id}/documentos` and `/clientes/{id}/documentos` GET endpoints) — confirms exact hook point and confirms the processo-scoped documentos endpoint already exists
  - `web/src/app/(dashboard)/clientes/[id]/page.tsx` (lines 190-460 and 1053-1260+) — lazy-mount tab pattern, button-toggle tab UI, dialog-reset-on-tab-switch effect, `ClienteProcessosTab`/`ClienteParecerTab`/`ClienteDocumentosEntreguesTab` implementations
  - `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` — CSS-print + `window.print()` pattern, `PRINT_CSS` constant, `BLANK` placeholder convention
  - `web/src/app/(dashboard)/processos/[id]/page.tsx` (line 142+) — existing `TabKey` state pattern already in Processos, current tab set
  - `web/package.json` — pinned versions (Next 16.2.6, React 19.2.4, TanStack Query ^5.87.4, RHF ^7.62.0, Zod ^4.1.5)
  - `.planning/PROJECT.md` — Key Decisions log: JSON-column reversal (v2.7), no-new-dependency precedent (v2.6 Pareceres), Tabs-vs-button-toggle decision (v2.8), `jakarta.persistence.validation.mode: none` gotcha (Phase 73.1), `enabled`-param-avoided-in-favor-of-lazy-mount-subcomponents decision (v2.8 Phase 77)
