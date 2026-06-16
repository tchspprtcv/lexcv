# Phase 34: Processos - Timeline e Auditoria - Research

**Researched:** 2026-06-16
**Domain:** Timeline aggregation endpoint, audit log entity, JPA entity extension, TanStack Query hooks, React dot-and-line UI component
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Timeline Data Model & Aggregation**
- Backend single endpoint `GET /processos/{id}/timeline` agrega todos os tipos em ordem cronologica — sorting cross-type e mais limpo server-side; consistente com o padrao de workflow (backend como fonte de verdade).
- Tipos incluidos na timeline: movimentacoes + transicoes de estado (registadas como Movimentacao pela Phase 33) + eventos + documentos + decisao de conflict check — cobre o PRC-28 na totalidade.
- `Movimentacao` recebe campo `autor_id` (FK User, nullable) — necessario para atribuicao no feed de timeline; transicoes de estado ja criam Movimentacoes (Phase 33), o ator precisa de ser registado.
- Tab "Movimentacoes" substituido por tab "Timeline" no detalhe do processo — a timeline agrega movimentacoes e outros tipos, tornando o tab de movimentacoes autonomo redundante.

**Audit Log Design (AUD-02)**
- Nova entidade `AuditLog` (tenant_id, processo_id, acao, entidade_tipo, entidade_id, autor_id, timestamp) — separacao de responsabilidades: historico operacional (Movimentacao) vs. trilha de conformidade (AuditLog).
- Eventos sensiveis a registar: transicoes de estado + decisao de conflict check + download de documento + eliminacao de documento (apenas eventos de alto risco; "processo view" excluido para evitar flooding).
- Logging manual em `ResourceController` com `auditLogRepository.save()` nos pontos sensiveis especificos — evita perda de contexto por AOP; explicito e facil de debugar.
- RBAC para audit trail: requer `processos:manage` — dados de conformidade devem ser restritos; utilizadores com `processos:view` acedem apenas a timeline operacional.

**Timeline & Audit UI**
- Visual de timeline: feed vertical com dot-and-line (ponto de cor + linha de conexao, ancorado a esquerda), icone por tipo de evento — padrao standard para gestao de casos juridicos, substitui o card list do tab Movimentacoes.
- Filtros da timeline: multiselect chips por tipo (Movimentacao / Evento / Documento / Decisao / Transicao) + seletor de intervalo de datas — cobre SC3 ("tipo de evento, periodo e criticidade"); estado controlado, sem URL params.
- Audit trail: tab "Auditoria" separado do tab "Timeline" — audiencias diferentes (conformidade vs. operacional) e acesso ja restrito por RBAC (`processos:manage`).
- Ordem dos tabs: Timeline (primeiro, mais consultado em workflow juridico), Partes, Fases; tab Movimentacoes removido.

### Claude's Discretion

None noted in CONTEXT.md beyond locked decisions above.

### Deferred Ideas (OUT OF SCOPE)

- Audit trail por entidade nao-processo (clientes, documentos globais) -> AUD-01 futuro.
- Notificacoes por evento auditavel (email/in-app) -> futuro.
- Export da timeline/audit log para PDF/CSV -> PRC-26 (planeado mas nao neste milestone).
- Configuracao de retencao dos registos de auditoria -> Phase 35 (governanca documental).
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PRC-28 | Utilizador consegue consultar uma timeline unificada do processo com movimentacoes, tarefas, documentos, eventos e decisoes ordenadas por data | Backend aggregation endpoint pattern; `Movimentacao.autor_id` field addition; `TimelineItem` discriminated-union DTO; client-side filter state |
| AUD-02 | Utilizador consegue consultar a trilha auditavel de eventos sensiveis do processo, incluindo consulta, alteracao, exportacao, download e eliminacao | New `AuditLog` JPA entity + repository; manual `auditLogRepository.save()` at four injection points; `GET /processos/{id}/audit` with `processos:manage` gate; `useAuditLog` TanStack Query hook |
</phase_requirements>

---

## Summary

Phase 34 adds two distinct capabilities to the processo detail page: a unified timeline that aggregates all operational events (movimentacoes, state transitions, agenda events, documents, conflict-check decision) and an audit log trail restricted to users with `processos:manage`. The phase extends an existing, well-established codebase rather than building from scratch — all patterns (multi-tenant scoping, method-level RBAC, TanStack Query hooks, controlled tab state) are already proven across 33 prior phases.

The largest backend change is the new `AuditLog` JPA entity and its repository. The controller already has all four injection points for audit events (state-transition endpoint, conflict-check decision endpoint, document download endpoint, document delete endpoint) and the `UserPrincipal` resolver to capture `autorId`. Adding `autor_id` to `Movimentacao` is a nullable column addition — JPA `ddl-auto=update` handles it automatically; no migration file is required. The timeline aggregation endpoint joins four source tables in-memory (same pattern used elsewhere in ResourceController), sorts by timestamp, and returns a flat list with a `tipo` discriminator field.

On the frontend the key change is the tab system in `ProcessoDetailContent`: `TabKey` loses `"movimentacoes"` and gains `"timeline"` and `"auditoria"`. The Timeline tab renders a pure read-only dot-and-line feed with client-side filter state (no re-fetch per filter). The Auditoria tab renders a flat list and is conditionally absent from the DOM when `canManageProcessos === false`. No new npm packages are required.

**Primary recommendation:** Implement in three waves — (1) backend data model changes (AuditLog entity, `autor_id` on Movimentacao, four audit injection points, `/timeline` and `/audit` endpoints); (2) frontend types + hooks; (3) UI tab restructuring and component implementation.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Timeline aggregation (sort + merge) | API / Backend | — | Cross-entity sort must be authoritative; client cannot reliably merge data it fetches independently from multiple endpoints |
| Audit event recording | API / Backend | — | Events must be captured at the server boundary where auth context is certain; client-side recording is spoofable |
| RBAC gate on audit read | API / Backend | Frontend / Browser | Backend `@PreAuthorize("hasAuthority('processos:manage')")` is the enforcement point; frontend hides the tab as UX convenience only |
| Client-side filter state | Browser / Client | — | Filters operate over already-loaded timeline data; no re-fetch required; URL params explicitly rejected by user decision |
| Audit tab visibility | Browser / Client | — | Tab button absent from DOM when user lacks `processos:manage`; backed by backend RBAC |

---

## Standard Stack

### Core (no new packages)

This phase installs **zero new npm packages** and **zero new Maven dependencies**. All required libraries are already in the project.

| Library | Version (project-pinned) | Purpose | Why Standard |
|---------|--------------------------|---------|--------------|
| Spring Data JPA | via Spring Boot 3.4.1 | `AuditLog` entity + repository | Already in use for all 20+ existing entities |
| `@tanstack/react-query` | already installed | `useTimeline`, `useAuditLog` hooks | Established pattern for all data fetching hooks in `use-processos.ts` |
| `lucide-react` | already installed | Timeline icons (FileText, GitBranch, Calendar, Paperclip, ShieldCheck) | Used throughout app; icons specified in UI-SPEC |
| shadcn-style UI primitives | already installed (Badge, Button, Card, Input) | Timeline feed, filter bar, audit list | No new components to install per UI-SPEC registry table |

[VERIFIED: codebase grep — all libraries confirmed present in `web/package.json` and `pom.xml` area via existing usage]

### Supporting

None — phase is self-contained within the existing dependency tree.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual `auditLogRepository.save()` at each injection point | Spring AOP `@AfterReturning` advice | AOP loses method-local context (justificativa, entidade_id resolved before call); user decision: explicit is easier to debug |
| In-memory server-side sort | DB `ORDER BY` query joining multiple tables | Joining disparate entity tables in JPQL is complex; in-memory sort over bounded data (one processo) is sufficient |
| Client-side timeline fetch from multiple endpoints + merge | Single `/timeline` endpoint | Multiple fetches create race conditions and inconsistent ordering; user decision: server-side aggregation |

**Installation:** None required.

---

## Package Legitimacy Audit

No new packages are introduced in this phase.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
Browser (Timeline tab)
  │
  ├─ useTimeline(processoId)
  │    └─ GET /api/v1/processos/{id}/timeline
  │         └─ ResourceController.getTimeline()
  │              ├─ Validates tenant + processoId
  │              ├─ Loads: Movimentacao[], Evento[byProcessoId], Documento[byProcessoId], ConflictCheckDecisao
  │              ├─ Maps each to TimelineItemDto {tipo, id, timestamp, titulo, descricao?, autorNome?}
  │              ├─ Sorts by timestamp DESC
  │              └─ Returns List<TimelineItemDto>
  │
  ├─ Client-side filter state (tipo chips + date range)
  │    └─ Filters applied over cached data — no re-fetch
  │
  └─ useAuditLog(processoId) [canManageProcessos only]
       └─ GET /api/v1/processos/{id}/audit
            └─ ResourceController.getAuditLog()
                 ├─ @PreAuthorize("hasAuthority('processos:manage')")
                 ├─ Validates tenant + processoId
                 └─ Returns List<AuditLog> ordered by timestamp DESC

Audit injection points (write path):
  ┌─ executarTransicao()   → auditLogRepository.save(TRANSICAO_ESTADO)
  ├─ registarDecisaoConflito() → auditLogRepository.save(CONFLICT_CHECK_DECISAO)
  ├─ downloadDocumento()   → auditLogRepository.save(DOCUMENTO_DOWNLOAD)
  └─ deleteDocumento()     → auditLogRepository.save(DOCUMENTO_ELIMINACAO)
```

### Recommended Project Structure (changes only)

```
backend/src/main/java/com/lexcv/
├── models/
│   ├── Movimentacao.java          # ADD: autor_id column (UUID, nullable)
│   └── AuditLog.java              # NEW entity (t_audit_log table)
├── repositories/
│   └── AuditLogRepository.java    # NEW: extends JpaRepository<AuditLog, Long>
└── controllers/
    └── ResourceController.java    # ADD: getTimeline(), getAuditLog(), audit saves at 4 points
                                   # ADD: auditLogRepository field injection

web/src/
├── types/processos.ts             # ADD: TimelineItem, TimelineItemType, AuditLogEntry
├── hooks/use-processos.ts         # ADD: useTimeline(id), useAuditLog(id)
└── app/(dashboard)/processos/[id]/page.tsx
                                   # CHANGE: TabKey loses "movimentacoes", gains "timeline" | "auditoria"
                                   # CHANGE: remove movimentacoes hook usage
                                   # ADD: TimelineFeed component (inline or extracted)
                                   # ADD: AuditoriaList component (inline or extracted)
                                   # ADD: TimelineFilterBar state + logic
```

---

### Pattern 1: Backend Timeline Aggregation DTO

**What:** Single flattened DTO per timeline entry with a `tipo` discriminator and common fields. Type-specific data carried in optional fields to avoid polymorphic Jackson complexity.

**When to use:** Any time heterogeneous entities must be presented as a unified chronological feed.

**Example:**
```java
// Source: project convention — same pattern as WorkflowResponse.TransicaoInfo record
// New DTO (inner record or separate file in dtos/ if needed)
public record TimelineItemDto(
    String tipo,         // "movimentacao" | "transicao" | "evento" | "documento" | "decisao"
    String id,           // source entity id as String (different ID types across entities)
    LocalDateTime timestamp,
    String titulo,
    String descricao,    // nullable
    String autorNome     // nullable — resolved from User table
) {}
```

[ASSUMED — specific field names; aligns with CONTEXT.md specifics section which defines the same fields]

### Pattern 2: AuditLog JPA Entity

**What:** New entity following the exact Lombok/JPA conventions used across all 20 existing models.

**When to use:** Whenever a new persistent entity is needed.

**Example:**
```java
// Source: mirrors ConflictCheckDecisao.java structure [VERIFIED: codebase]
@Entity
@Table(name = "t_audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Long is appropriate for a high-volume log table

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    // Values: "transicao_estado" | "conflict_check_decisao" | "documento_download" | "documento_eliminacao"
    @Column(name = "acao", nullable = false)
    private String acao;

    @Column(name = "entidade_tipo", nullable = false)
    private String entidadeTipo;  // "processo" | "documento" | "conflict_check_decisao"

    @Column(name = "entidade_id", nullable = false)
    private String entidadeId;    // String to accommodate UUID and Integer IDs

    @Column(name = "autor_id")
    private UUID autorId;  // nullable: system events without a user actor

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) this.timestamp = LocalDateTime.now();
    }
}
```

[ASSUMED — specific table name and field definitions; pattern is [VERIFIED: codebase] from existing entities]

### Pattern 3: Movimentacao.autor_id Addition

**What:** Nullable FK column added to existing entity. JPA `ddl-auto=update` adds the column automatically — no migration file.

**Critical detail:** The `executarTransicao()` endpoint already resolves `UserPrincipal` for the justificativa check. The `autor_id` must be set on the `Movimentacao` saved in that same method.

**Example:**
```java
// Source: ResourceController.java lines 1047-1060 [VERIFIED: codebase]
// Current code (Phase 33):
Movimentacao mov = new Movimentacao();
mov.setProcessoId(id);
mov.setTipo("TRANSICAO_ESTADO");
mov.setDescricao(descricaoMov);
mov.setData(LocalDateTime.now());
movimentacaoRepository.save(mov);

// Phase 34 addition — principal already resolved at line 1048:
mov.setAutorId(principal.getUserId());  // NEW — principal already in scope
movimentacaoRepository.save(mov);
```

### Pattern 4: Audit Save at Injection Points

**What:** Manual `auditLogRepository.save()` calls at the four sensitive endpoints, using the `UserPrincipal` already resolved in each method.

**Example (document download — no `principal` currently resolved there):**
```java
// Source: ResourceController.java lines 1468-1485 [VERIFIED: codebase]
// Current downloadDocumento has no auth context read; must add it:
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

AuditLog entry = AuditLog.builder()
    .tenantId(getTenantId())
    .processoId(doc.getProcessoId())  // nullable — documento may not be linked to processo
    .acao("documento_download")
    .entidadeTipo("documento")
    .entidadeId(id.toString())
    .autorId(principal.getUserId())
    .build();
auditLogRepository.save(entry);
```

**Note:** `downloadDocumento` and `deleteDocumento` are not processo-scoped endpoints. They operate on any tenant documento. AuditLog records for documents without a `processo_id` will have `processoId = null` — the audit query endpoint filters by `processoId`, so only processo-linked documents appear in the audit tab. This is the correct behavior.

[ASSUMED — processoId nullable on AuditLog accepted for document-level events]

### Pattern 5: Frontend Hook for Read-Only Query

**What:** `useTimeline` and `useAuditLog` follow the exact pattern of all existing read-only hooks in `use-processos.ts`.

**Example:**
```typescript
// Source: use-processos.ts lines 487-499 [VERIFIED: codebase]
// Direct model — useTimeline mirrors usePrazos
export function useTimeline(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);
  return useQuery({
    queryKey: ["processos", "timeline", processoId],
    queryFn: () => apiFetch<TimelineItem[]>(
      `/processos/${encodeURIComponent(processoId)}/timeline`
    ),
    enabled,
    staleTime: 15_000,
  });
}

export function useAuditLog(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);
  return useQuery({
    queryKey: ["processos", "audit", processoId],
    queryFn: () => apiFetch<AuditLogEntry[]>(
      `/processos/${encodeURIComponent(processoId)}/audit`
    ),
    enabled,
    staleTime: 30_000,  // audit log changes less frequently
  });
}
```

### Pattern 6: Tab Key Extension

**What:** `type TabKey` in `ProcessoDetailContent` gains two values and loses one.

**Example:**
```typescript
// Source: page.tsx line 82 [VERIFIED: codebase]
// Current: type TabKey = "partes" | "fases" | "movimentacoes";
// Phase 34: 
type TabKey = "timeline" | "partes" | "fases" | "auditoria";
// Default initial tab changes from "partes" to "timeline" (first and most-consulted)
const [tab, setTab] = React.useState<TabKey>("timeline");
```

### Pattern 7: Auditoria Tab Conditional Rendering

**What:** Tab button is absent from DOM entirely (not just disabled) for users without `processos:manage`. This follows the same RBAC pattern used for the Formalizar button.

**Example:**
```typescript
// Source: page.tsx line 496 [VERIFIED: codebase] — analogous to Formalizar block
{canManageProcessos ? (
  <Button
    type="button"
    variant={tab === "auditoria" ? "secondary" : "outline"}
    onClick={() => setTab("auditoria")}
  >
    Auditoria
  </Button>
) : null}
```

### Anti-Patterns to Avoid

- **Polymorphic Jackson serialization for timeline:** Do not use `@JsonSubTypes` / `@JsonTypeInfo` for the timeline DTO. The unified flat record with nullable fields is simpler and avoids frontend discriminated-union type complexity.
- **URL params for filter state:** CONTEXT.md explicitly rejects this. Filters must be `React.useState` local to the tab component.
- **AOP for audit logging:** CONTEXT.md explicitly rejects this. Manual saves are the chosen approach.
- **Fetching timeline data per filter change:** Filters must be applied client-side over the already-loaded array — never trigger a new `useQuery` per filter interaction.
- **Re-using existing movimentacoes cache for timeline:** The `/timeline` endpoint is separate from `/movimentacoes`. The old `useProcessoMovimentacoes` hook and its cache key `["processos", "movimentacoes", id]` should remain for backward compatibility but the Timeline tab does NOT use it.
- **Removing the `useProcessoMovimentacoes` import/hook:** The `createMovimentacao` POST endpoint and its form in the old "Movimentacoes" tab no longer appears as a tab, but `useAddProcessoMovimentacao` may still be useful for programmatic movimentacao creation elsewhere. Do not remove hooks — just remove the tab UI.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Entity persistence | Custom SQL via JDBC | Spring Data JPA `JpaRepository` | Already the project standard; `ddl-auto=update` handles schema |
| Auth context resolution | Custom token parsing | `SecurityContextHolder.getContext().getAuthentication()` → `UserPrincipal` | Already used in `executarTransicao`, `registarDecisaoConflito` (lines 1047-1048, 877-879) |
| Data fetching + caching | Custom fetch + useState | TanStack Query `useQuery` | Established pattern; handles stale, loading, error states automatically |
| Timeline sorting | Custom merge sort | Java `Stream.sorted()` with `Comparator.comparing(TimelineItemDto::timestamp).reversed()` | One-liner after building the unified list |

**Key insight:** Every non-trivial technical problem in this phase already has a solved, in-use pattern in the codebase. The implementation risk is low; the main discipline is applying existing patterns consistently.

---

## Common Pitfalls

### Pitfall 1: `autor_id` Not Set on Movimentacao in Phase 33 Code

**What goes wrong:** The `executarTransicao` endpoint already saves a `Movimentacao` with `tipo="TRANSICAO_ESTADO"`. If `autor_id` is not set at that save point, all Phase 33 transition records will have `autor_nome = null` in the timeline — degrading attribution for existing data.

**Why it happens:** `Movimentacao.java` currently has no `autorId` field (confirmed by reading the model). Adding the field to the entity is step 1; updating the `executarTransicao` save call is step 2. Both must land in the same plan task.

**How to avoid:** The task that adds `autor_id` to `Movimentacao.java` must also update `ResourceController.executarTransicao()` to set `mov.setAutorId(principal.getUserId())`. The `principal` variable is already in scope at line 1048.

**Warning signs:** Timeline shows "Transição de estado" entries with `autor_nome: null` after implementation.

### Pitfall 2: AuditLog Records for Documents Without `processo_id`

**What goes wrong:** `downloadDocumento` and `deleteDocumento` accept any documento by UUID — they are not scoped to a processo. A documento may have `processoId = null` (e.g., documentos linked directly to a cliente). Saving an AuditLog with `processoId = null` is fine, but the `GET /processos/{id}/audit` query that filters `WHERE processo_id = :id` will never surface these records.

**Why it happens:** The download/delete endpoints are generic, not processo-scoped.

**How to avoid:** Accept this behavior — only processo-linked document events appear in the audit tab of a processo. Records with `processoId = null` are orphan audit entries (acceptable for now; AUD-01 will handle entity-level audit). Mark `processoId` nullable on `AuditLog` (`@Column(name="processo_id")` without `nullable=false`).

**Warning signs:** Unit test that downloads a cliente-scoped documento and then checks the processo audit log finds zero results — this is correct behavior, not a bug.

### Pitfall 3: Timeline Timestamp Field Inconsistency Across Source Entities

**What goes wrong:** Each source entity uses a different field for its "timestamp":
- `Movimentacao.data` (`LocalDateTime`)
- `Evento.dataInicio` (`LocalDateTime`)
- `Documento.createdAt` (`LocalDateTime`)
- `ConflictCheckDecisao.createdAt` (`LocalDateTime`), but `dataDecisao` is `LocalDate` (not `LocalDateTime`)

Sorting by a unified `timestamp` requires picking the right field per type. Using the wrong field (e.g., `dataDecisao` for ConflictCheckDecisao which is `LocalDate`, not `LocalDateTime`) will cause a compile error or runtime NPE.

**Why it happens:** Entity models were built independently over multiple phases.

**How to avoid:** For `ConflictCheckDecisao`, use `createdAt` (which is `LocalDateTime`) as the timeline timestamp. `dataDecisao` is a business date (when the lawyer made the decision, possibly backdated) — use `createdAt` for chronological ordering.

**Warning signs:** `ClassCastException` or compilation error when sorting the merged list.

### Pitfall 4: `useProcessoMovimentacoes` Cache Invalidation After Phase 34

**What goes wrong:** `useExecutarTransicao.onSuccess` currently invalidates `["processos", "movimentacoes", processoId]` (line 481 of `use-processos.ts`). After Phase 34, the Timeline tab uses `["processos", "timeline", processoId]` — the old invalidation no longer refreshes the Timeline.

**Why it happens:** The hook was written when "movimentacoes" was the only view of process history.

**How to avoid:** Update `useExecutarTransicao.onSuccess` to also invalidate `["processos", "timeline", processoId]`. Similarly, `useAddProcessoMovimentacao.onSuccess` should invalidate timeline as well.

**Warning signs:** User executes a transition, Workflow card updates, but Timeline tab still shows stale data until manual page reload.

### Pitfall 5: AuditLog Not Scoped by Tenant in Query

**What goes wrong:** `GET /processos/{id}/audit` must filter by `tenant_id` AND `processo_id`. Forgetting the tenant scope allows cross-tenant data leakage.

**Why it happens:** Simple single-column query by `processoId` is intuitive but breaks multi-tenancy.

**How to avoid:** `AuditLogRepository` must have `List<AuditLog> findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID tenantId, UUID processoId)`. The endpoint validates processo belongs to tenant first (same pattern as all other endpoints), then queries using `getTenantId()`.

**Warning signs:** During testing with two tenants, audit logs from tenant B appear in tenant A's audit view.

### Pitfall 6: Tab State Reset When Auditoria Tab Is Inaccessible

**What goes wrong:** If `canManageProcessos` changes (e.g., permissions refresh mid-session) and the user is on the "auditoria" tab, the tab state becomes `"auditoria"` but the button and content are not rendered — invisible blank tab content.

**Why it happens:** `React.useState<TabKey>("timeline")` initializes to "timeline", but if somehow `"auditoria"` ends up as the current tab for a non-manager user, the tab content block has no handler for it.

**How to avoid:** Add a guard: if `tab === "auditoria" && !canManageProcessos`, fallback to `"timeline"` render. Or simply ensure the tab button is never rendered for non-managers (which prevents this state from being set).

---

## Code Examples

### Backend: `/processos/{id}/timeline` Endpoint Structure

```java
// Source: pattern from listMovimentacoes (lines 1305-1313) and getWorkflow (lines 958-986) [VERIFIED: codebase]
@PreAuthorize("hasAuthority('processos:view')")
@GetMapping("/processos/{id}/timeline")
public ResponseEntity<?> getTimeline(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", "Processo não encontrado"));
    }

    List<TimelineItemDto> items = new ArrayList<>();

    // 1. Movimentacoes (includes TRANSICAO_ESTADO type from Phase 33)
    List<Movimentacao> movs = movimentacaoRepository.findByProcessoId(id);
    for (Movimentacao m : movs) {
        String autorNome = resolveAutorNome(m.getAutorId(), tenantId);
        String tipo = "TRANSICAO_ESTADO".equals(m.getTipo()) ? "transicao" : "movimentacao";
        items.add(new TimelineItemDto(tipo, String.valueOf(m.getId()),
            m.getData(), m.getTipo() != null ? m.getTipo() : "Movimentação",
            m.getDescricao(), autorNome));
    }

    // 2. Eventos linked to this processo
    List<Evento> eventos = eventoRepository.findByTenantIdAndProcessoId(tenantId, id);
    for (Evento e : eventos) {
        items.add(new TimelineItemDto("evento", String.valueOf(e.getId()),
            e.getDataInicio(), e.getTitulo(), e.getDescricao(), null));
    }

    // 3. Documentos linked to this processo
    List<Documento> docs = documentoRepository.findByTenantIdAndProcessoId(tenantId, id);
    for (Documento d : docs) {
        items.add(new TimelineItemDto("documento", d.getId().toString(),
            d.getCreatedAt(), d.getNome(), d.getTipo(), null));
    }

    // 4. ConflictCheckDecisao
    conflictCheckDecisaoRepository.findByTenantIdAndProcessoId(tenantId, id)
        .ifPresent(dec -> {
            String autorNome = resolveAutorNome(dec.getDecisorId(), tenantId);
            items.add(new TimelineItemDto("decisao", dec.getId().toString(),
                dec.getCreatedAt(), "Decisão de Conflict Check",
                "Nível: " + dec.getNivel(), autorNome));
        });

    // Sort chronologically descending
    items.sort(Comparator.comparing(TimelineItemDto::timestamp,
        Comparator.nullsLast(Comparator.reverseOrder())));

    return ResponseEntity.ok(items);
}

private String resolveAutorNome(UUID autorId, UUID tenantId) {
    if (autorId == null) return null;
    User user = userRepository.findById(autorId).orElse(null);
    return (user != null && tenantId.equals(user.getTenantId())) ? user.getNome() : null;
}
```

### Backend: `/processos/{id}/audit` Endpoint Structure

```java
// Source: pattern from listMovimentacoes [VERIFIED: codebase]
@PreAuthorize("hasAuthority('processos:manage')")
@GetMapping("/processos/{id}/audit")
public ResponseEntity<?> getAuditLog(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", "Processo não encontrado"));
    }
    List<AuditLog> entries = auditLogRepository
        .findByTenantIdAndProcessoIdOrderByTimestampDesc(tenantId, id);
    // Enrich with autorNome
    List<Map<String, Object>> result = entries.stream().map(e -> {
        String autorNome = resolveAutorNome(e.getAutorId(), tenantId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("acao", e.getAcao());
        m.put("entidadeTipo", e.getEntidadeTipo());
        m.put("entidadeId", e.getEntidadeId());
        m.put("autorNome", autorNome);
        m.put("timestamp", e.getTimestamp());
        return m;
    }).toList();
    return ResponseEntity.ok(result);
}
```

### Frontend: TimelineItem TypeScript Type

```typescript
// Source: pattern from existing types in processos.ts [VERIFIED: codebase]
export type TimelineItemType =
  | "movimentacao"
  | "transicao"
  | "evento"
  | "documento"
  | "decisao";

export interface TimelineItem {
  tipo: TimelineItemType;
  id: string;
  timestamp: string;  // ISO datetime
  titulo: string;
  descricao?: string;
  autorNome?: string;
}

export interface AuditLogEntry {
  id: number;
  acao: string;  // "transicao_estado" | "conflict_check_decisao" | "documento_download" | "documento_eliminacao"
  entidadeTipo: string;
  entidadeId: string;
  autorNome?: string;
  timestamp: string;
}
```

### Frontend: Client-Side Filter Logic

```typescript
// Source: [ASSUMED] — pattern consistent with existing filter patterns in clientes/processos pages
const [selectedTipos, setSelectedTipos] = React.useState<Set<TimelineItemType>>(
  new Set(["movimentacao", "transicao", "evento", "documento", "decisao"])
);
const [dateFrom, setDateFrom] = React.useState<string>("");
const [dateTo, setDateTo] = React.useState<string>("");

const filteredItems = React.useMemo(() => {
  return (timeline.data ?? []).filter((item) => {
    if (!selectedTipos.has(item.tipo)) return false;
    if (dateFrom && item.timestamp < dateFrom) return false;
    if (dateTo && item.timestamp > dateTo + "T23:59:59") return false;
    return true;
  });
}, [timeline.data, selectedTipos, dateFrom, dateTo]);
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `TabKey = "partes" \| "fases" \| "movimentacoes"` | `TabKey = "timeline" \| "partes" \| "fases" \| "auditoria"` | Phase 34 | "Movimentacoes" tab removed; Timeline is now the default first tab |
| `Movimentacao` has no `autorId` | `Movimentacao.autorId` nullable UUID | Phase 34 | Transitions created by Phase 33 will have null author until Phase 34 lands |

**Deprecated/outdated:**
- `useProcessoMovimentacoes` hook: Not deprecated — still exists and POST endpoint remains. But the Timeline tab replaces its display role. The hook's `onSuccess` invalidation must be extended to also invalidate the timeline cache.
- `movimentacoes` tab button and its rendered content block: Removed in Phase 34.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `EventoRepository` has `findByTenantIdAndProcessoId` query method | Architecture Patterns (timeline endpoint) | Compilation failure — would need to add the query method to `EventoRepository.java` |
| A2 | `DocumentoRepository` has `findByTenantIdAndProcessoId` (already used in `listProcessoDocumentos`) | Architecture Patterns (timeline endpoint) | Low risk — confirmed pattern exists at line 1464 |
| A3 | `TimelineItemDto` implemented as Java record, not a separate DTO class file | Standard Stack, Code Examples | Compile error if inner records are not supported; would extract to `dtos/TimelineItemDto.java` |
| A4 | `processoId` is nullable on `AuditLog` (for non-processo-linked document events) | Common Pitfalls (Pitfall 2) | If non-nullable, inserting a documento_download for a cliente-only document will throw a DB constraint error |
| A5 | Default initial tab changes from "partes" to "timeline" | Architecture Patterns (Pattern 6) | UX deviation if user expectation was preserved "partes" default — low risk |

---

## Open Questions

1. **Does `EventoRepository` have `findByTenantIdAndProcessoId`?**
   - What we know: `EventoRepository` is not shown in context, but `DocumentoRepository` is confirmed to have the analogous method (used at line 1464).
   - What's unclear: Whether `EventoRepository` already has the `findByTenantIdAndProcessoId` query or only `findByTenantId`.
   - Recommendation: The plan task for the timeline endpoint should include "verify or add `findByTenantIdAndProcessoId` to `EventoRepository.java`" as an explicit sub-step.

2. **Should `useExecutarTransicao` invalidate `["processos", "timeline", ...]` in addition to `["processos", "movimentacoes", ...]`?**
   - What we know: Currently invalidates movimentacoes (line 481); Phase 34 timeline endpoint is separate.
   - What's unclear: Whether the plan intends to keep both cache keys in sync or let the timeline stale naturally.
   - Recommendation: Yes — invalidate both. State transitions are the most significant timeline events; stale timeline after a transition is a poor UX.

3. **How should the timeline handle `Movimentacao.tipo = "TRANSICAO_ESTADO"` vs regular movimentacoes?**
   - What we know: Phase 33 set `tipo = "TRANSICAO_ESTADO"` on transition-created movimentacoes (line 1057). CONTEXT.md says timeline type discriminator maps `TRANSICAO_ESTADO` to `"transicao"`.
   - What's unclear: What `titulo` to show for transicao entries (the current `tipo` field is "TRANSICAO_ESTADO"; `descricao` is "ATIVO -> SUSPENSO: justificativa").
   - Recommendation: Use the `descricao` field's first segment (before ":") as the `titulo` in the TimelineItemDto, and the remainder as `descricao`. Or use a fixed label "Transição de Estado" as titulo and full descricao as body.

---

## Environment Availability

Step 2.6: SKIPPED (no external tools, services, or runtimes beyond project dependencies are needed — zero new packages, all existing Spring Boot / Next.js toolchain is confirmed operational as the project is actively in development at Phase 33 complete).

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None detected (no test files found in project; `mvn test` would run JUnit if tests existed) |
| Config file | none — no test infrastructure committed |
| Quick run command | `cd backend && mvn test -pl . -q 2>&1` (runs zero tests if none exist) |
| Full suite command | `cd backend && mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PRC-28 | Timeline endpoint returns items from all 4 source types in timestamp order | Integration | manual | No test infrastructure |
| PRC-28 | Client-side filter by tipo excludes correct entries | Unit (frontend) | manual | No test infrastructure |
| AUD-02 | AuditLog saved when state transition executed | Integration | manual | No test infrastructure |
| AUD-02 | AuditLog saved when document downloaded | Integration | manual | No test infrastructure |
| AUD-02 | `GET /audit` returns 403 for `processos:view` users | Integration | manual | No test infrastructure |

### Wave 0 Gaps

No test infrastructure exists in this project (`mvn test` yields zero tests). Nyquist validation for this phase relies on manual verification steps in the plan. The plan should include manual smoke-test checkpoints:
- After Wave 1 (backend): `curl -H "Cookie: access_token=..." http://localhost:8080/api/v1/processos/{id}/timeline` returns a 200 with items.
- After Wave 1 (backend): Audit endpoint returns 403 for a `processos:view`-only user.
- After Wave 2 (frontend): Timeline tab renders with dot-and-line entries; filter chips hide/show entries.

---

## Security Domain

Security enforcement is enabled (`security_enforcement: true`, `security_asvs_level: 1`).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | JWT cookies already established; no auth changes in this phase |
| V3 Session Management | no | No session changes |
| V4 Access Control | YES | `@PreAuthorize("hasAuthority('processos:manage')")` on audit endpoint; frontend tab hidden (not just disabled) |
| V5 Input Validation | minimal | No user input in read endpoints; `processoId` is a path variable validated by UUID type on @PathVariable |
| V6 Cryptography | no | No crypto operations |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Insecure Direct Object Reference (IDOR) on audit endpoint | Tampering / Info Disclosure | Validate `processo.getTenantId().equals(getTenantId())` before returning audit data — same pattern used on all processo sub-resource endpoints |
| Audit log data exposure via timeline endpoint | Info Disclosure | Timeline endpoint (`processos:view`) must NOT return AuditLog entries — it only aggregates Movimentacao, Evento, Documento, ConflictCheckDecisao |
| Missing audit record on document download | Repudiation | Manual `auditLogRepository.save()` call must be placed before the file response is returned, inside the same request scope |

**Critical security note:** The audit trail is the phase's primary security deliverable. The `GET /processos/{id}/audit` endpoint must enforce `hasAuthority('processos:manage')` at the method level, not just the frontend. The frontend hiding the tab is a UX measure, not a security boundary.

---

## Sources

### Primary (HIGH confidence)
- `ResourceController.java` (full read, lines 1-1325+) — confirmed existing patterns for tenant scoping, `@PreAuthorize`, `UserPrincipal` resolution, movimentacao creation
- `Movimentacao.java` — confirmed exact current field set (no `autorId`, no `tenant_id`)
- `Evento.java`, `Documento.java`, `ConflictCheckDecisao.java` — confirmed field names and types for timeline aggregation
- `use-processos.ts` — confirmed hook patterns, query key conventions, `apiFetch` usage
- `web/src/app/(dashboard)/processos/[id]/page.tsx` — confirmed `TabKey` definition (line 82), RBAC guards, tab rendering pattern
- `34-CONTEXT.md` — locked decisions constraining all research
- `34-UI-SPEC.md` — icon mapping, dot colors, filter bar spec, accessibility requirements

### Secondary (MEDIUM confidence)
- `REQUIREMENTS.md` — PRC-28 and AUD-02 requirement text
- `STATE.md` — phase 33 completion confirmed; Phase 34 is the active work item
- `lib/prazos.ts`, `lib/conflict-check.ts` — confirmed pattern for standalone utility/mapper files

### Tertiary (LOW confidence)
- None — all significant claims are verified against actual codebase files.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new packages; all verified in codebase
- Architecture: HIGH — all patterns directly verified in existing code
- Pitfalls: HIGH — derived from reading actual code paths that would be touched
- Security: HIGH — RBAC pattern directly verified; audit trail ownership clear

**Research date:** 2026-06-16
**Valid until:** 2026-07-16 (stable project; 30-day horizon)
