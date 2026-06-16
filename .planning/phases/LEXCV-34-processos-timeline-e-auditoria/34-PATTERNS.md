# Phase 34: Processos - Timeline e Auditoria - Pattern Map

**Mapped:** 2026-06-16
**Files analyzed:** 9 new/modified files
**Analogs found:** 9 / 9

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/.../models/Movimentacao.java` | model | CRUD | `backend/.../models/Prazo.java` | exact (same: tenant-scoped JPA entity, nullable FK, `@PrePersist`) |
| `backend/.../models/AuditLog.java` | model | event-driven | `backend/.../models/ConflictCheckDecisao.java` | exact (same: UUID tenant_id, LocalDateTime createdAt, `@PrePersist`, Lombok builder) |
| `backend/.../repositories/AuditLogRepository.java` | repository | CRUD | `backend/.../repositories/ConflictCheckDecisaoRepository.java` | exact (same: tenant+entity scoped query method, JpaRepository) |
| `backend/.../dtos/TimelineItemDto.java` | DTO | request-response | `backend/.../dtos/WorkflowResponse.java` | exact (Java record DTO, inner record pattern) |
| `backend/.../controllers/ResourceController.java` (new endpoints + audit saves) | controller | request-response | same file — `listMovimentacoes` (line 1305), `registarDecisaoConflito` (line 849), `executarTransicao` (line 989) | exact |
| `web/src/types/processos.ts` | type definitions | — | same file — `ConflictCheckDecisao`, `Prazo` interfaces | exact (same: camelCase, optional fields with `?`) |
| `web/src/hooks/use-processos.ts` (new hooks) | hook | request-response | same file — `usePrazos` (line 487), `useConflictCheckDecisao` (line 414) | exact |
| `web/src/hooks/use-processos.ts` (invalidation update) | hook | request-response | same file — `useExecutarTransicao.onSuccess` (line 476) | exact |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | component (page) | request-response | same file — `TabKey`, tab buttons (line 851), RBAC conditional (line 496) | exact |

---

## Pattern Assignments

### `backend/.../models/Movimentacao.java` — add `autor_id` column

**Analog:** `backend/src/main/java/com/lexcv/models/Prazo.java`

**Current file** (full, lines 1-29):
```java
@Entity
@Table(name = "t_movimentacao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Movimentacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    private String tipo;
    private String descricao;
    private LocalDateTime data;

    @Column(name = "prazo_id")
    private Integer prazoId;
}
```

**Addition — nullable FK column pattern** from `Prazo.java` (lines 38-39):
```java
// Copy nullable FK pattern: @Column with no nullable=false, UUID type
@Column(name = "responsavel_id")
private UUID responsavelId;
```

**Apply:** Add after `prazoId` field:
```java
@Column(name = "autor_id")
private UUID autorId;
```

No `@PrePersist` needed — `Movimentacao` has no `createdAt`. JPA `ddl-auto=update` adds the column automatically.

---

### `backend/.../models/AuditLog.java` — new entity

**Analog:** `backend/src/main/java/com/lexcv/models/ConflictCheckDecisao.java` (full, lines 1-51)

**Imports pattern** (lines 1-9 of ConflictCheckDecisao.java):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
```

**Core entity pattern** (lines 8-51 of ConflictCheckDecisao.java):
```java
@Entity
@Table(name = "t_conflict_check_decisao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflictCheckDecisao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(name = "nivel", nullable = false)
    private String nivel;

    @Column(name = "decisor_id", nullable = false)
    private UUID decisorId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

**New AuditLog.java — adapting the pattern:**
- Use `GenerationType.IDENTITY` with `Long id` (high-volume log table)
- `processo_id` is nullable (documents not linked to a processo)
- `timestamp` auto-set in `@PrePersist`
- All string columns (acao, entidade_tipo, entidade_id) are `nullable = false`
- `autor_id` is nullable (system events)

```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id")          // nullable: doc events without processo
    private UUID processoId;

    @Column(name = "acao", nullable = false)
    private String acao;                   // "transicao_estado" | "conflict_check_decisao" | "documento_download" | "documento_eliminacao"

    @Column(name = "entidade_tipo", nullable = false)
    private String entidadeTipo;           // "processo" | "documento" | "conflict_check_decisao"

    @Column(name = "entidade_id", nullable = false)
    private String entidadeId;             // String — accommodates UUID and Integer IDs

    @Column(name = "autor_id")             // nullable: system events
    private UUID autorId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) this.timestamp = LocalDateTime.now();
    }
}
```

---

### `backend/.../repositories/AuditLogRepository.java` — new repository

**Analog:** `backend/src/main/java/com/lexcv/repositories/ConflictCheckDecisaoRepository.java` (full, lines 1-12)

**Full analog:**
```java
package com.lexcv.repositories;

import com.lexcv.models.ConflictCheckDecisao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConflictCheckDecisaoRepository extends JpaRepository<ConflictCheckDecisao, UUID> {
    List<ConflictCheckDecisao> findByTenantId(UUID tenantId);
    Optional<ConflictCheckDecisao> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);
}
```

**New AuditLogRepository.java — adapting the pattern:**
- Key query: `findByTenantIdAndProcessoIdOrderByTimestampDesc` (ordered list, not Optional)
- ID type: `Long` (not UUID) matching `AuditLog.id`

```java
package com.lexcv.repositories;

import com.lexcv.models.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTenantIdAndProcessoIdOrderByTimestampDesc(UUID tenantId, UUID processoId);
}
```

---

### `backend/.../dtos/TimelineItemDto.java` — new DTO record

**Analog:** `backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java` (full, lines 1-19)

**Full analog (Java record pattern):**
```java
package com.lexcv.dtos;

import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        String estadoAtual,
        UUID responsavelId,
        String responsavelNome,    // resolved from User; null -> "Não atribuído" in UI
        String proximoPasso,       // derived from state map; null -> "—" in UI
        List<TransicaoInfo> transicoesDisponiveis
) {
    public record TransicaoInfo(
            String acao,
            String label,
            String permissaoNecessaria,
            boolean requerJustificativa
    ) {}
}
```

**New TimelineItemDto.java — top-level record (no inner records needed):**
```java
package com.lexcv.dtos;

import java.time.LocalDateTime;

public record TimelineItemDto(
        String tipo,          // "movimentacao" | "transicao" | "evento" | "documento" | "decisao"
        String id,            // source entity id as String (different ID types across entities)
        LocalDateTime timestamp,
        String titulo,
        String descricao,     // nullable
        String autorNome      // nullable — resolved from User
) {}
```

---

### `backend/.../controllers/ResourceController.java` — new endpoints + field injection + audit saves

**Analog A — `listMovimentacoes` endpoint** (lines 1305-1312): the simplest processo sub-resource read endpoint.

```java
// Pattern: processos:view gate, tenant+processo validation, repository call
@PreAuthorize("hasAuthority('processos:view')")
@GetMapping("/processos/{id}/movimentacoes")
public ResponseEntity<?> listMovimentacoes(@PathVariable UUID id) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    return ResponseEntity.ok(movimentacaoRepository.findByProcessoId(id));
}
```

**Analog B — `registarDecisaoConflito` endpoint** (lines 849-893): `processos:manage` gate + UserPrincipal resolution from SecurityContext.

```java
// Pattern: getTenantId() + processo null-check + SecurityContextHolder for UserPrincipal
@PreAuthorize("hasAuthority('processos:manage')")
@PostMapping("/processos/{id}/conflict-check/decisao")
public ResponseEntity<?> registarDecisaoConflito(@PathVariable UUID id, ...) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    // Resolve decisorId from SecurityContext
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    UUID decisorId = principal.getUserId();
    ...
}
```

**Analog C — `executarTransicao` (lines 989-1065):** principal already in scope for Movimentacao save — shows exactly where `autor_id` must be set.

```java
// Lines 1046-1060: principal resolved, then Movimentacao built and saved
// Phase 34 adds mov.setAutorId(principal.getUserId()) before save
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

Movimentacao mov = new Movimentacao();
mov.setProcessoId(id);
mov.setTipo("TRANSICAO_ESTADO");
mov.setDescricao(descricaoMov);
mov.setData(LocalDateTime.now());
// ADD: mov.setAutorId(principal.getUserId());
movimentacaoRepository.save(mov);
```

**Repository injection pattern** (lines 49-64 — class fields in `@RequiredArgsConstructor`):
```java
// All repositories are final fields — @RequiredArgsConstructor generates constructor injection
private final MovimentacaoRepository movimentacaoRepository;
private final ConflictCheckDecisaoRepository conflictCheckDecisaoRepository;
// ADD: private final AuditLogRepository auditLogRepository;
```

**`getTenantId()` helper** (lines 110-114 — shared across all endpoints):
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
```

**Existing `downloadDocumento` endpoint** (lines 1467-1485) — where audit save must be injected:
```java
@PreAuthorize("hasAuthority('documentos:view')")
@GetMapping("/documentos/{id}/download")
public ResponseEntity<?> downloadDocumento(@PathVariable UUID id) {
    Documento doc = documentoRepository.findById(id).orElse(null);
    if (doc == null || !doc.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento não encontrado"));
    }
    File file = new File(doc.getCaminhoArquivo());
    if (!file.exists()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Arquivo físico não encontrado"));
    }
    Resource resource = new FileSystemResource(file);
    return ResponseEntity.ok()
            .contentType(...)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getNome() + "\"")
            .body(resource);
    // ADD audit save before return (or just before .body(resource))
}
```

**Existing `deleteDocumento` endpoint** (lines 1487-1501) — where audit save must be injected:
```java
@PreAuthorize("hasAuthority('documentos:edit')")
@DeleteMapping("/documentos/{id}")
public ResponseEntity<?> deleteDocumento(@PathVariable UUID id) {
    Documento doc = documentoRepository.findById(id).orElse(null);
    if (doc == null || !doc.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Documento não encontrado"));
    }
    try { Files.deleteIfExists(Paths.get(doc.getCaminhoArquivo())); } catch (IOException ignored) {}
    documentoRepository.delete(doc);
    return ResponseEntity.ok(Map.of("message", "Documento removido com sucesso!"));
    // ADD audit save before documentoRepository.delete(doc)
}
```

**`EventoRepository.findByTenantIdAndProcessoId` — already exists** (confirmed, line 11 of EventoRepository.java):
```java
List<Evento> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);
```
No changes needed to EventoRepository.

**Timeline timestamp fields per source entity** (confirmed from model reads):
- `Movimentacao.data` (`LocalDateTime`) — use as timestamp
- `Evento.dataInicio` (`LocalDateTime`) — use as timestamp
- `Documento.createdAt` (`LocalDateTime`) — use as timestamp
- `ConflictCheckDecisao.createdAt` (`LocalDateTime`) — use as timestamp (NOT `dataDecisao` which is `LocalDate`)

---

### `web/src/types/processos.ts` — add `TimelineItem`, `TimelineItemType`, `AuditLogEntry`

**Analog:** existing interfaces in the same file — `ConflictCheckDecisao` (lines 137-147) and `Prazo` (lines 170-180).

**`ConflictCheckDecisao` interface pattern** (lines 137-147):
```typescript
export interface ConflictCheckDecisao {
  id: string;
  tenantId: string;
  processoId: string;
  nivel: ConflictNivel;
  justificativa?: string;
  decisorId: string;
  dataDecisao: string;
  referenciaEvidencia?: string;
  createdAt: string;
}
```

**`Prazo` interface pattern** (lines 170-180) — shows optional fields with `?`:
```typescript
export interface Prazo {
  id: string;
  descricao: string;
  dataLimite: string;
  prioridade: PrazoPrioridade;
  responsavelId: string | null;
  concluido: boolean;
  escalonado: boolean;
  risco: PrazoRisco;
  createdAt: string;
}
```

**New types to add** — follow camelCase convention matching backend DTO field names:
```typescript
export type TimelineItemType =
  | "movimentacao"
  | "transicao"
  | "evento"
  | "documento"
  | "decisao";

export interface TimelineItem {
  tipo: TimelineItemType;
  id: string;
  timestamp: string;   // ISO datetime string
  titulo: string;
  descricao?: string;
  autorNome?: string;
}

export interface AuditLogEntry {
  id: number;
  acao: string;   // "transicao_estado" | "conflict_check_decisao" | "documento_download" | "documento_eliminacao"
  entidadeTipo: string;
  entidadeId: string;
  autorNome?: string;
  timestamp: string;
}
```

---

### `web/src/hooks/use-processos.ts` — add `useTimeline`, `useAuditLog`; update `useExecutarTransicao` and `useAddProcessoMovimentacao`

**Analog A — `usePrazos`** (lines 487-499): the closest read-only hook — `processoId` scoped, `enabled` guard, `staleTime`.

```typescript
export function usePrazos(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "prazos", processoId],
    queryFn: () =>
      apiFetch<Prazo[]>(
        `/processos/${encodeURIComponent(processoId)}/prazos`,
      ),
    enabled,
    staleTime: 15_000,
  });
}
```

**Analog B — `useConflictCheckDecisao`** (lines 414-426): nullable return type pattern (`| null`).

```typescript
export function useConflictCheckDecisao(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "conflict-check", processoId],
    queryFn: () =>
      apiFetch<ConflictCheckDecisao | null>(
        `/processos/${encodeURIComponent(processoId)}/conflict-check/decisao`,
      ),
    enabled,
    staleTime: 15_000,
  });
}
```

**Analog C — `useExecutarTransicao.onSuccess`** (lines 476-484): shows the exact invalidation pattern to extend.

```typescript
onSuccess: async (updated) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
    queryClient.setQueryData(["processos", "detail", processoId], updated),
    queryClient.invalidateQueries({ queryKey: ["processos", "workflow", processoId] }),
    queryClient.invalidateQueries({ queryKey: ["processos", "movimentacoes", processoId] }),
    // ADD: queryClient.invalidateQueries({ queryKey: ["processos", "timeline", processoId] }),
  ]);
},
```

**Analog D — `useAddProcessoMovimentacao.onSuccess`** (lines 344-348): also needs timeline invalidation.

```typescript
onSuccess: async () => {
  await queryClient.invalidateQueries({ queryKey: ["processos", "movimentacoes", id] });
  // ADD: await queryClient.invalidateQueries({ queryKey: ["processos", "timeline", id] });
},
```

---

### `web/src/app/(dashboard)/processos/[id]/page.tsx` — TabKey extension, tab buttons, conditional Auditoria tab

**Analog A — `TabKey` definition** (line 82):
```typescript
// Current (Phase 33):
type TabKey = "partes" | "fases" | "movimentacoes";

// Phase 34 change:
type TabKey = "timeline" | "partes" | "fases" | "auditoria";
```

**Analog B — `useState` initial value** (line 130):
```typescript
// Current:
const [tab, setTab] = React.useState<TabKey>("partes");

// Phase 34 change (Timeline is now the first/default tab):
const [tab, setTab] = React.useState<TabKey>("timeline");
```

**Analog C — tab button rendering pattern** (lines 851-872):
```tsx
// Current tab buttons — copy this pattern for new tabs
<div className="flex flex-wrap gap-2">
  <Button
    type="button"
    variant={tab === "partes" ? "secondary" : "outline"}
    onClick={() => setTab("partes")}
  >
    Partes
  </Button>
  <Button
    type="button"
    variant={tab === "fases" ? "secondary" : "outline"}
    onClick={() => setTab("fases")}
  >
    Fases
  </Button>
  {/* "Movimentações" button removed in Phase 34 */}
</div>
```

**Analog D — RBAC conditional rendering** (lines 495-509): Formalizar button gated by `canManageProcessos` — exact pattern for the Auditoria tab button.

```tsx
// Source: lines 495-509 — conditional render (not disabled, entirely absent)
{processo.data?.estado === "TRIAGEM" && canManageProcessos ? (
  // ... button content
) : null}

// Apply same pattern for Auditoria tab button:
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

**Analog E — tab content switching** (lines 875, 954): ternary chain pattern.

```tsx
// Current ternary chain:
{tab === "partes" ? (
  <div className="grid gap-4 lg:grid-cols-2">
    {/* partes content */}
  </div>
) : tab === "fases" ? (
  <div className="grid gap-4 lg:grid-cols-2">
    {/* fases content */}
  </div>
) : /* tab === "movimentacoes" */ (
  <div className="grid gap-4 lg:grid-cols-2">
    {/* movimentacoes content */}
  </div>
)}

// Phase 34 restructure:
{tab === "timeline" ? (
  /* TimelineFeed with FilterBar */
) : tab === "partes" ? (
  /* unchanged partes content */
) : tab === "fases" ? (
  /* unchanged fases content */
) : tab === "auditoria" && canManageProcessos ? (
  /* AuditoriaList */
) : null}
```

**Analog F — movimentacoes list card** (lines 1109-1134): existing card list pattern that Timeline replaces — reference for the "Movimentações" entry structure.

```tsx
// Current movimentacoes list (basis for timeline item card structure):
<div key={m.id} className="rounded-md border border-neutral-200 p-3 dark:border-neutral-800">
  <div className="flex flex-wrap items-baseline justify-between gap-2">
    <div className="font-medium">{m.titulo}</div>
    <div className="text-xs text-neutral-500 dark:text-neutral-400">{formatDateTime(m.data)}</div>
  </div>
  {m.descricao ? (
    <div className="mt-2 text-sm text-neutral-700 dark:text-neutral-200 whitespace-pre-wrap">
      {m.descricao}
    </div>
  ) : null}
</div>
```

**Analog G — imports block** (lines 1-77): pattern for adding new lucide icons and hook imports.

```tsx
// Existing lucide imports (line 7-17) — add FileText, GitBranch, Calendar, Paperclip, ShieldCheck:
import {
  AlertCircle, CheckCircle2, Circle,
  Pause, Play, Plus, RotateCcw, User, XCircle,
  // ADD: FileText, GitBranch, Calendar, Paperclip, ShieldCheck
} from "lucide-react";

// Existing hook imports (lines 37-53) — add useTimeline, useAuditLog:
import {
  useAddProcessoFase, useAddProcessoMovimentacao, useAddProcessoParte,
  useConflictCheckDecisao, useCreatePrazo, useExecutarTransicao,
  useFormalizarProcesso, usePrazos, useProcesso, useProcessoFases,
  useProcessoMovimentacoes, useProcessoPartes, useTogglePrazoConcluido,
  useUpdateProcessoFaseStatus, useWorkflow,
  // ADD: useTimeline, useAuditLog
} from "@/hooks/use-processos";

// Existing types import (lines 69-76) — add TimelineItem, AuditLogEntry:
import type {
  ProcessoFaseCreateRequest, ProcessoFaseStatus, ProcessoFaseUpdateRequest,
  ProcessoMovimentacaoCreateRequest, ProcessoParteCreateRequest, TransicaoInfo,
  // ADD: TimelineItem, AuditLogEntry
} from "@/types/processos";
```

---

## Shared Patterns

### Multi-tenant Scoping
**Source:** `ResourceController.java` lines 110-114 (`getTenantId()`) and every endpoint
**Apply to:** `getTimeline()` and `getAuditLog()` endpoints
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
// Then in every endpoint:
UUID tenantId = getTenantId();
Processo processo = processoRepository.findById(id).orElse(null);
if (processo == null || !processo.getTenantId().equals(tenantId)) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", "Processo não encontrado"));
}
```

### UserPrincipal Resolution
**Source:** `ResourceController.java` lines 1046-1048 (`executarTransicao`) and lines 877-879 (`registarDecisaoConflito`)
**Apply to:** audit save injection at all 4 injection points; `getTimeline()` for `resolveAutorNome()`
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
UUID autorId = principal.getUserId();
```

### RBAC Method-Level Gate
**Source:** `ResourceController.java` lines 849, 896 (manage-gated endpoints)
**Apply to:** `getAuditLog()` — must use `processos:manage`; `getTimeline()` — `processos:view`
```java
@PreAuthorize("hasAuthority('processos:manage')")
@GetMapping("/processos/{id}/audit")
public ResponseEntity<?> getAuditLog(@PathVariable UUID id) { ... }

@PreAuthorize("hasAuthority('processos:view')")
@GetMapping("/processos/{id}/timeline")
public ResponseEntity<?> getTimeline(@PathVariable UUID id) { ... }
```

### JPA Entity — Lombok + @PrePersist
**Source:** `ConflictCheckDecisao.java` (lines 8-51), `Prazo.java` (lines 8-57)
**Apply to:** `AuditLog.java`
```java
@Entity
@Table(name = "t_<table_name>")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EntityName {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

### TanStack Query Hook — Read-Only
**Source:** `use-processos.ts` lines 487-499 (`usePrazos`)
**Apply to:** `useTimeline`, `useAuditLog`
```typescript
const enabled = typeof window !== "undefined" && Boolean(processoId);
return useQuery({
  queryKey: ["processos", "<resource>", processoId],
  queryFn: () => apiFetch<Type[]>(`/processos/${encodeURIComponent(processoId)}/<resource>`),
  enabled,
  staleTime: 15_000,
});
```

### Frontend RBAC — Absent (not disabled)
**Source:** `page.tsx` lines 495-509 (Formalizar block) and lines 877, 917, 956, 995 (`canEditProcessos` guards)
**Apply to:** Auditoria tab button — render `null` not disabled when user lacks `processos:manage`
```tsx
{canManageProcessos ? (
  <Button ...>Auditoria</Button>
) : null}
```

### Client-Side Filter State
**Source:** no existing exact analog (first client-side filter in this page). Closest: `ProcessosListFilters` in `use-processos.ts` lines 68-76 (server-side filter state shape).
**Apply to:** Timeline filter bar in `page.tsx`
```typescript
// React.useState for each filter dimension — no URL params (per CONTEXT.md decision)
const [selectedTipos, setSelectedTipos] = React.useState<Set<TimelineItemType>>(
  new Set(["movimentacao", "transicao", "evento", "documento", "decisao"])
);
const [dateFrom, setDateFrom] = React.useState<string>("");
const [dateTo, setDateTo] = React.useState<string>("");

// React.useMemo for derived filtered list — no re-fetch
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

## No Analog Found

No files in this phase lack a codebase analog. All patterns are directly observable from existing code.

| File | Note |
|------|------|
| Timeline dot-and-line UI component | No existing dot-and-line feed in the codebase. The closest structural analog is the movimentacoes card list (page.tsx lines 1117-1131). The visual pattern (vertical line + colored dot + icon) must be built from `div` + `className` using Tailwind — no new component library. Use `Badge` variants from existing `conflictNivelToVariant` / `prazosRiscoToVariant` for the color-per-tipo mapping pattern. |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/` (models, repositories, dtos, controllers, config) + `web/src/` (types, hooks, app/(dashboard)/processos)
**Files read:** 18
**Pattern extraction date:** 2026-06-16
