# Phase 33: Processos - Workflow, Gates e Prazos - Pattern Map

**Mapped:** 2026-06-15
**Files analyzed:** 13 new/modified files
**Analogs found:** 13 / 13

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `backend/.../models/Processo.java` (modified — add `responsavel_id`) | model | CRUD | same file (lines 1-54) | exact |
| `backend/.../models/Prazo.java` (new entity) | model | CRUD | `backend/.../models/Evento.java` (lines 1-49) | role-match |
| `backend/.../repositories/PrazoRepository.java` (new) | repository | CRUD | `backend/.../repositories/ConflictCheckDecisaoRepository.java` (lines 1-12) | exact |
| `backend/.../dtos/TransicaoRequest.java` (new) | DTO | request-response | `backend/.../dtos/ConflictCheckDecisaoRequest.java` (full file) | exact |
| `backend/.../dtos/WorkflowResponse.java` (new) | DTO | request-response | `backend/.../dtos/ConflictCheckResponse.java` (full file) | role-match |
| `backend/.../dtos/PrazoRequest.java` (new) | DTO | request-response | `backend/.../dtos/ConflictCheckDecisaoRequest.java` (full file) | role-match |
| `backend/.../controllers/ResourceController.java` (modified — add transition + prazos endpoints) | controller | request-response | same file — `formalizarProcesso` (lines 785-842) + `registarDecisaoConflito` (lines 738-783) | exact |
| `web/src/types/processos.ts` (modified — add Prazo + Workflow types) | types | — | same file (lines 1-142) | exact |
| `web/src/schemas/processos.ts` (modified — add justificativa + prazo schemas) | schema/validation | request-response | same file — `conflictCheckDecisaoFormSchema` (lines 56-77) | exact |
| `web/src/lib/prazos.ts` (new helper) | utility | — | `web/src/lib/conflict-check.ts` (lines 1-31) | exact |
| `web/src/hooks/use-processos.ts` (modified — add workflow + prazos hooks) | hook/service | request-response | same file — `useFormalizarProcesso` (lines 414-432) + `useConflictCheckDecisao` (lines 400-412) | exact |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` (modified — Workflow card + Prazos card) | component/page | CRUD | same file — Conflict Check card block (lines 279-356) | exact |
| `web/src/app/(dashboard)/processos/page.tsx` (modified — responsavel + risco signals in listing) | component/page | CRUD | same file — `estadoVariant` cell + sub-line pattern (lines 306-344) | exact |

---

## Pattern Assignments

### `backend/.../models/Processo.java` — add `responsavel_id` (modified, model)

**Analog:** same file (`backend/src/main/java/com/lexcv/models/Processo.java`, lines 1-54)

**Existing field pattern to replicate** (lines 24-25):
```java
@Column(name = "cliente_id", nullable = false)
private UUID clienteId;
```

**New field to add** — insert after `clienteId`, before `numeroProcesso` (line 28):
```java
@Column(name = "responsavel_id")
private UUID responsavelId;
```

Rule: `nullable = true` (not required at creation; set during transition or explicit update). No `@JoinColumn` / no eager fetch — FK is UUID only, resolved to user name in the response layer.

---

### `backend/.../models/Prazo.java` (new model, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/models/Evento.java` (lines 1-49)

**Entity structure pattern** — copy Evento header verbatim, adapt fields:
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_prazo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prazo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "data_limite", nullable = false)
    private LocalDate dataLimite;

    // ALTA | MEDIA | BAIXA
    @Column(nullable = false)
    @Builder.Default
    private String prioridade = "MEDIA";

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean concluido = false;

    // escalonado: true when risco is proximo or vencido
    @Column(nullable = false)
    @Builder.Default
    private Boolean escalonado = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

Key rules from Evento.java to follow:
- `@Builder.Default` on boolean fields to avoid null (lines 39-41 in Evento).
- `@GeneratedValue(strategy = GenerationType.UUID)` — matches ConflictCheckDecisao (not IDENTITY like Movimentacao/Evento — use UUID for Prazo since it has tenant scope).
- `@Column(name = "tenant_id", nullable = false)` — mandatory on every domain entity.
- `@PrePersist` for `createdAt` with `LocalDateTime.now()` (Evento lines 44-48).

**Risk derivation note:** `risco` (`ok`/`proximo`/`vencido`) is NOT stored as a column — it is computed in the endpoint handler at response time from `dataLimite` vs `LocalDate.now()` + `prioridade`, and returned as a transient field in the response payload. Do not add a `risco` JPA column to Prazo.

---

### `backend/.../repositories/PrazoRepository.java` (new repository, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/repositories/ConflictCheckDecisaoRepository.java` (lines 1-12)

**Full file pattern:**
```java
package com.lexcv.repositories;

import com.lexcv.models.Prazo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PrazoRepository extends JpaRepository<Prazo, UUID> {
    List<Prazo> findByTenantIdAndProcessoIdOrderByDataLimiteAsc(UUID tenantId, UUID processoId);
    List<Prazo> findByTenantId(UUID tenantId);
}
```

Naming convention: `findByTenantIdAnd*` — copied from ConflictCheckDecisaoRepository line 11 and ClienteRepository pattern. The `OrderByDataLimiteAsc` suffix is a Spring Data derived query — no `@Query` needed.

---

### `backend/.../dtos/TransicaoRequest.java` (new DTO, request-response)

**Analog:** `backend/src/main/java/com/lexcv/dtos/ConflictCheckDecisaoRequest.java` (full file, 7 lines)

**Full file pattern:**
```java
package com.lexcv.dtos;

public record TransicaoRequest(
        String justificativa   // nullable; required only for suspender/encerrar/reabrir
) {}
```

Java record idiom — no Jackson annotations, Spring deserializes automatically (same as ConflictCheckDecisaoRequest).

---

### `backend/.../dtos/WorkflowResponse.java` (new DTO, request-response)

**Analog:** `backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java` (full file, 17 lines)

**Full file pattern — nested record for transition info:**
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
            String acao,           // "ativar" | "suspender" | "encerrar" | "reabrir"
            String label,          // "Ativar Processo" | "Suspender" | "Encerrar" | "Reabrir"
            String permissaoNecessaria,  // "processos:edit" | "processos:manage"
            boolean requerJustificativa
    ) {}
}
```

Pattern: outer record + inner nested record — exact idiom from `ConflictCheckResponse` (lines 6-17).

---

### `backend/.../dtos/PrazoRequest.java` (new DTO, request-response)

**Analog:** `backend/src/main/java/com/lexcv/dtos/ConflictCheckDecisaoRequest.java` (full file)

**Full file pattern:**
```java
package com.lexcv.dtos;

import java.time.LocalDate;
import java.util.UUID;

public record PrazoRequest(
        String descricao,
        LocalDate dataLimite,
        String prioridade,      // ALTA | MEDIA | BAIXA; default MEDIA if null
        UUID responsavelId      // optional
) {}
```

---

### `backend/.../controllers/ResourceController.java` — new endpoints (modified, controller, request-response)

**Analog:** `formalizarProcesso` endpoint (lines 785-842) for gate-checked state transition; `registarDecisaoConflito` (lines 738-783) for `@Transactional` + `Authentication` principal resolution; `createMovimentacao` (lines 960-970) for Movimentacao creation.

**Imports to add** (add to existing import block at lines 1-35):
```java
import com.lexcv.dtos.TransicaoRequest;
import com.lexcv.dtos.WorkflowResponse;
import com.lexcv.dtos.PrazoRequest;
```

**Constructor injection to add** (add to `@RequiredArgsConstructor` fields, after `conflictCheckDecisaoRepository` line 55):
```java
private final PrazoRepository prazoRepository;
private final UserRepository userRepository;
```

**State machine map (static constant, add near CAMPOS_MINIMOS_POR_TIPO at lines 62-70):**
```java
// Permitted transitions per estado: estado -> list of (acao, targetEstado, requiredPermission, requiresJustificativa)
private static final Map<String, List<TransicaoInfo>> TRANSICOES_PERMITIDAS = Map.of(
    "ATIVO",     List.of(
        new TransicaoInfo("suspender", "Suspender",  "processos:manage", true),
        new TransicaoInfo("encerrar",  "Encerrar",   "processos:manage", true)
    ),
    "SUSPENSO",  List.of(
        new TransicaoInfo("ativar",   "Ativar Processo", "processos:edit", false),
        new TransicaoInfo("encerrar", "Encerrar",        "processos:manage", true)
    ),
    "ENCERRADO", List.of(
        new TransicaoInfo("reabrir",  "Reabrir",    "processos:manage", true)
    )
);
// TRIAGEM -> ATIVO is handled by the existing /formalizar endpoint (Phase 32).
// Use a simple record or inner class to hold TransicaoInfo statically.
```

**GET /processos/{id}/workflow — returns WorkflowResponse:**
```java
@PreAuthorize("hasAuthority('processos:view')")
@GetMapping("/processos/{id}/workflow")
public ResponseEntity<?> getWorkflow(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    String estado = processo.getEstado() != null ? processo.getEstado().toUpperCase() : "";
    List<WorkflowResponse.TransicaoInfo> transicoes =
        TRANSICOES_PERMITIDAS.getOrDefault(estado, List.of());

    // Resolve responsavel
    User responsavel = processo.getResponsavelId() != null
        ? userRepository.findById(processo.getResponsavelId()).orElse(null)
        : null;

    return ResponseEntity.ok(new WorkflowResponse(
        estado,
        processo.getResponsavelId(),
        responsavel != null ? responsavel.getNome() : null,
        derivarProximoPasso(estado),   // private helper method
        transicoes
    ));
}
```

**POST /processos/{id}/transicao/{acao} — state-machine transition with gate validation:**
```java
@Transactional
@PreAuthorize("hasAuthority('processos:edit')")   // minimum; manage checked inline for critical
@PostMapping("/processos/{id}/transicao/{acao}")
public ResponseEntity<?> executarTransicao(
        @PathVariable UUID id,
        @PathVariable String acao,
        @RequestBody(required = false) TransicaoRequest payload) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    // ... find allowed transition for acao, validate gates, set new estado,
    // create Movimentacao, save processo
}
```

**Gate validation pattern** — copied from `formalizarProcesso` lines 795-841:
```java
// (0) Estado gate
String estadoAtual = processo.getEstado() != null ? processo.getEstado().toUpperCase() : "";
List<TransicaoInfo> transicoes = TRANSICOES_PERMITIDAS.getOrDefault(estadoAtual, List.of());
TransicaoInfo transicao = transicoes.stream()
    .filter(t -> t.acao().equals(acao))
    .findFirst().orElse(null);
if (transicao == null) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
        "message", "Transição '" + acao + "' não é permitida no estado " + estadoAtual
    ));
}

// (1) Permission gate for manage-level transitions
if ("processos:manage".equals(transicao.permissaoNecessaria())) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean hasManage = auth.getAuthorities().stream()
        .anyMatch(a -> "processos:manage".equals(a.getAuthority()));
    if (!hasManage) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "message", "Requer permissão processos:manage"
        ));
    }
}

// (2) Justificativa gate
if (transicao.requerJustificativa() &&
        (payload == null || payload.justificativa() == null || payload.justificativa().isBlank())) {
    return ResponseEntity.badRequest().body(Map.of(
        "message", "Justificativa é obrigatória para esta transição"
    ));
}
```

**Movimentacao creation on transition** — pattern from `createMovimentacao` (lines 960-970):
```java
// Resolve autor
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

Movimentacao mov = new Movimentacao();
mov.setProcessoId(id);
mov.setTipo("TRANSICAO_ESTADO");
mov.setDescricao(estadoAtual + " -> " + novoEstado
    + (payload != null && payload.justificativa() != null
        ? ": " + payload.justificativa() : ""));
mov.setData(LocalDateTime.now());
movimentacaoRepository.save(mov);

// Transition
processo.setEstado(novoEstado);
return ResponseEntity.ok(processoRepository.save(processo));
```

**GET /processos/{id}/prazos — list prazos with computed risco:**
```java
@PreAuthorize("hasAuthority('processos:view')")
@GetMapping("/processos/{id}/prazos")
public ResponseEntity<?> listPrazos(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    List<Prazo> prazos = prazoRepository.findByTenantIdAndProcessoIdOrderByDataLimiteAsc(tenantId, id);
    // Map to response with computed risco
    List<Map<String, Object>> result = prazos.stream().map(p -> {
        String risco = computeRisco(p.getDataLimite(), p.getPrioridade());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("descricao", p.getDescricao());
        m.put("dataLimite", p.getDataLimite());
        m.put("prioridade", p.getPrioridade());
        m.put("responsavelId", p.getResponsavelId());
        m.put("concluido", p.getConcluido());
        m.put("escalonado", p.getEscalonado());
        m.put("risco", risco);
        m.put("createdAt", p.getCreatedAt());
        return m;
    }).toList();
    return ResponseEntity.ok(result);
}
```

**Risk derivation helper** (private method in ResourceController):
```java
private String computeRisco(LocalDate dataLimite, String prioridade) {
    if (dataLimite == null) return "ok";
    LocalDate hoje = LocalDate.now();
    if (dataLimite.isBefore(hoje)) return "vencido";
    long diasRestantes = hoje.until(dataLimite, java.time.temporal.ChronoUnit.DAYS);
    int limiarProximo = "ALTA".equalsIgnoreCase(prioridade) ? 7 : 3;
    return diasRestantes <= limiarProximo ? "proximo" : "ok";
}
```

**POST /processos/{id}/prazos — create prazo:**
```java
@Transactional
@PreAuthorize("hasAuthority('processos:edit')")
@PostMapping("/processos/{id}/prazos")
public ResponseEntity<?> createPrazo(
        @PathVariable UUID id,
        @RequestBody PrazoRequest payload) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    Prazo prazo = Prazo.builder()
        .tenantId(tenantId)
        .processoId(id)
        .descricao(payload.descricao())
        .dataLimite(payload.dataLimite())
        .prioridade(payload.prioridade() != null ? payload.prioridade() : "MEDIA")
        .responsavelId(payload.responsavelId())
        .build();
    Prazo saved = prazoRepository.save(prazo);
    String risco = computeRisco(saved.getDataLimite(), saved.getPrioridade());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "id", saved.getId(), "risco", risco, "prazo", saved
    ));
}
```

**PATCH /processos/{id}/prazos/{prazoId}/concluido — toggle concluido:**
```java
@Transactional
@PreAuthorize("hasAuthority('processos:edit')")
@PatchMapping("/processos/{id}/prazos/{prazoId}/concluido")
public ResponseEntity<?> togglePrazoConcluido(
        @PathVariable UUID id,
        @PathVariable UUID prazoId,
        @RequestBody Map<String, Boolean> body) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    Prazo prazo = prazoRepository.findById(prazoId).orElse(null);
    if (prazo == null || !prazo.getTenantId().equals(tenantId) || !prazo.getProcessoId().equals(id)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Prazo não encontrado"));
    }
    prazo.setConcluido(Boolean.TRUE.equals(body.get("concluido")));
    return ResponseEntity.ok(prazoRepository.save(prazo));
}
```

**Tenant guard — must appear in every handler** (copy from lines 653-655):
```java
if (processo == null || !processo.getTenantId().equals(tenantId)) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
}
```

---

### `web/src/types/processos.ts` — new types (modified, types)

**Analog:** same file (lines 1-142) — follow existing interface style exactly.

**Types to append after line 142:**
```typescript
// --- Phase 33: Workflow, Gates e Prazos ---

export type PrazoRisco = "ok" | "proximo" | "vencido";
export type PrazoPrioridade = "ALTA" | "MEDIA" | "BAIXA";
export type TransicaoAcao = "ativar" | "suspender" | "encerrar" | "reabrir";

export interface TransicaoInfo {
  acao: TransicaoAcao;
  label: string;
  permissaoNecessaria: "processos:edit" | "processos:manage";
  requerJustificativa: boolean;
}

export interface WorkflowResponse {
  estadoAtual: string;
  responsavelId: string | null;
  responsavelNome: string | null;
  proximoPasso: string | null;
  transicoesDisponiveis: TransicaoInfo[];
}

export interface Prazo {
  id: string;
  processo_id: string;
  tenant_id: string;
  descricao: string;
  dataLimite: string;   // ISO date string
  prioridade: PrazoPrioridade;
  responsavelId: string | null;
  concluido: boolean;
  escalonado: boolean;
  risco: PrazoRisco;
  createdAt: string;
}

export interface PrazoCreateRequest {
  descricao: string;
  dataLimite: string;   // ISO date string "YYYY-MM-DD"
  prioridade?: PrazoPrioridade;
  responsavelId?: string;
}

export interface TransicaoRequest {
  justificativa?: string;
}
```

Also add `responsavel_id` to the existing `Processo` interface (line 15, after `data_fim`):
```typescript
responsavel_id?: string;
```

And update `ProcessoApi` type in `use-processos.ts` (line 21-44) to include:
```typescript
responsavelId?: string;
responsavel_id?: string;
```

And update `normalizeProcesso` to map it:
```typescript
responsavel_id: api.responsavel_id ?? api.responsavelId,
```

---

### `web/src/schemas/processos.ts` — new schemas (modified, schema/validation)

**Analog:** same file — `conflictCheckDecisaoFormSchema` (lines 56-73) for the required-field-with-Zod-min pattern; `processoMovimentacaoFormSchema` (lines 41-47) for simple string form.

**Justificativa schema** (for critical transition Dialog):
```typescript
export const transicaoJustificativaFormSchema = z.object({
  justificativa: z
    .string()
    .trim()
    .min(10, "Justificativa deve ter pelo menos 10 caracteres"),
});

export type TransicaoJustificativaFormValues = z.infer<
  typeof transicaoJustificativaFormSchema
>;
```

**Prazo creation schema** (for Novo Prazo Dialog):
```typescript
export const prazoFormSchema = z.object({
  descricao: z.string().trim().min(1, "A descrição é obrigatória"),
  dataLimite: z.string().trim().min(1, "A data limite é obrigatória"),
  prioridade: z.enum(["ALTA", "MEDIA", "BAIXA"]).default("MEDIA"),
  responsavelId: optionalTrimmedString,
});

export type PrazoFormValues = z.infer<typeof prazoFormSchema>;
```

`optionalTrimmedString` is already defined at lines 3-7 of the same file — reuse without re-declaring.

---

### `web/src/lib/prazos.ts` (new utility)

**Analog:** `web/src/lib/conflict-check.ts` (lines 1-31) — exact same structural pattern (Record-based map, two exported functions, JSDoc comment, return type union).

**Full file:**
```typescript
import type { PrazoRisco } from "@/types/processos";

/**
 * Fonte unica de verdade para o mapeamento de risco de prazo -> variant de badge.
 * Usada na lista de prazos no detalhe do processo e na listagem de processos.
 */
export function prazosRiscoToVariant(
  risco: PrazoRisco,
): "green" | "amber" | "red" {
  const map: Record<PrazoRisco, "green" | "amber" | "red"> = {
    ok: "green",
    proximo: "amber",
    vencido: "red",
  };
  return map[risco] ?? "amber";
}

/**
 * Fonte unica de verdade para o mapeamento de risco de prazo -> label de badge.
 * Usada na listagem de processos (apenas proximo/vencido renderizados).
 */
export function prazosRiscoToLabel(risco: PrazoRisco): string {
  const map: Record<PrazoRisco, string> = {
    ok: "PRAZO OK",
    proximo: "PRAZO PROXIMO",
    vencido: "PRAZO VENCIDO",
  };
  return map[risco] ?? "PRAZO PROXIMO";
}
```

---

### `web/src/hooks/use-processos.ts` — new hooks (modified, hook/service, request-response)

**Analog:** same file — `useFormalizarProcesso` (lines 414-432) for state-transition mutation + cache update; `useConflictCheckDecisao` (lines 400-412) for sub-resource query; `useUpdateProcessoFaseStatus` (lines 289-308) for optimistic update pattern.

**Import additions needed** — add to existing type imports (line 4-19):
```typescript
import type {
  // ... existing imports ...
  WorkflowResponse,
  Prazo,
  PrazoCreateRequest,
  TransicaoRequest,
} from "@/types/processos";
```

**Workflow query hook** (mirrors `useConflictCheckDecisao` lines 400-412):
```typescript
export function useWorkflow(processoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(processoId);

  return useQuery({
    queryKey: ["processos", "workflow", processoId],
    queryFn: () =>
      apiFetch<WorkflowResponse>(
        `/processos/${encodeURIComponent(processoId)}/workflow`,
      ),
    enabled,
    staleTime: 15_000,
  });
}
```

**State transition mutation** (mirrors `useFormalizarProcesso` lines 414-432 — invalidates both list and detail + workflow):
```typescript
export function useExecutarTransicao(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (args: { acao: string; payload?: TransicaoRequest }) => {
      const response = await apiFetch<ProcessoApi>(
        `/processos/${encodeURIComponent(processoId)}/transicao/${args.acao}`,
        {
          method: "POST",
          body: JSON.stringify(args.payload ?? {}),
        },
      );
      return normalizeProcesso(response);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", processoId], updated),
        queryClient.invalidateQueries({ queryKey: ["processos", "workflow", processoId] }),
        queryClient.invalidateQueries({ queryKey: ["processos", "movimentacoes", processoId] }),
      ]);
    },
  });
}
```

**Prazos list query** (mirrors `useProcessoPartes` lines 216-225):
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

**Create prazo mutation** (mirrors `useAddProcessoParte` lines 227-239):
```typescript
export function useCreatePrazo(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PrazoCreateRequest) =>
      apiFetch<Prazo>(
        `/processos/${encodeURIComponent(processoId)}/prazos`,
        {
          method: "POST",
          body: JSON.stringify(payload satisfies PrazoCreateRequest),
        },
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["processos", "prazos", processoId],
      });
    },
  });
}
```

**Toggle prazo concluido mutation** (mirrors `useUpdateProcessoFaseStatus` lines 289-308 for optimistic cache update):
```typescript
export function useTogglePrazoConcluido(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { prazoId: string; concluido: boolean }) =>
      apiFetch<Prazo>(
        `/processos/${encodeURIComponent(processoId)}/prazos/${encodeURIComponent(args.prazoId)}/concluido`,
        {
          method: "PATCH",
          body: JSON.stringify({ concluido: args.concluido }),
        },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<Prazo[] | undefined>(
        ["processos", "prazos", processoId],
        (current) => {
          if (!current) return current;
          return current.map((p) => (p.id === updated.id ? updated : p));
        },
      );
    },
  });
}
```

---

### `web/src/app/(dashboard)/processos/[id]/page.tsx` — Workflow + Prazos cards (modified, component/page, CRUD)

**Analog:** same file — Conflict Check card block (lines 279-356) for Card structure + permission-gated action + error display; `dl` grid (lines 242-274) for metadata rows.

**Shadcn components to add before implementation:**
```
npx shadcn add dialog
npx shadcn add textarea
```
Both land in `web/src/components/ui/dialog.tsx` and `web/src/components/ui/textarea.tsx`.

**Import additions** (add to existing import block):
```typescript
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Play, Pause, XCircle, RotateCcw, Plus, CheckCircle2, Circle, AlertCircle, User, ChevronRight } from "lucide-react";
import { prazosRiscoToVariant, prazosRiscoToLabel } from "@/lib/prazos";
import { useWorkflow, useExecutarTransicao, usePrazos, useCreatePrazo, useTogglePrazoConcluido } from "@/hooks/use-processos";
import { transicaoJustificativaFormSchema, prazoFormSchema } from "@/schemas/processos";
import type { TransicaoAcao } from "@/types/processos";
```

**Workflow card structure** — copy Card shell from Conflict Check block (lines 279-294):
```tsx
<Card>
  <CardHeader>
    <div className="flex items-center justify-between">
      <CardTitle>Workflow</CardTitle>
      <Badge variant={estadoVariant as "green" | "amber" | "gray" | "purple"} className="rounded-none font-bold tracking-wide">
        {estadoLabel}
      </Badge>
    </div>
  </CardHeader>
  <CardContent className="space-y-4">
    {workflow.isLoading ? (
      <p className="text-sm text-slate-500 dark:text-slate-400">A carregar workflow...</p>
    ) : workflow.isError ? (
      <p className="text-sm text-red-600">Erro ao carregar o workflow. Tente novamente.</p>
    ) : workflow.data ? (
      <>
        {/* dl grid — Estado / Responsavel / Proximo Passo */}
        <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
          <dt className="text-neutral-500 dark:text-neutral-400">Estado</dt>
          <dd className="col-span-2 font-medium">
            <Badge variant={estadoVariant} className="rounded-none font-bold tracking-wide">{estadoLabel}</Badge>
          </dd>
          <dt className="text-neutral-500 dark:text-neutral-400">Responsável</dt>
          <dd className="col-span-2 font-medium flex items-center gap-1">
            <User className="h-[14px] w-[14px] text-slate-400" />
            {workflow.data.responsavelNome
              ? workflow.data.responsavelNome
              : <span className="text-slate-400 dark:text-slate-500 italic">Não atribuído</span>}
          </dd>
          <dt className="text-neutral-500 dark:text-neutral-400">Próximo Passo</dt>
          <dd className="col-span-2 text-sm text-slate-500 dark:text-slate-400">
            {workflow.data.proximoPasso ?? "—"}
          </dd>
        </dl>

        {/* Transitions section */}
        <div className="pt-4 border-t border-slate-200 dark:border-slate-800">
          <p className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400 mb-2">
            ACOES DE TRANSICAO
          </p>
          {workflow.data.transicoesDisponiveis.length === 0 ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Nenhuma transicao disponivel neste estado.
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {workflow.data.transicoesDisponiveis.map((t) => {
                const hasPermission =
                  t.permissaoNecessaria === "processos:manage"
                    ? canManageProcessos
                    : canEditProcessos;
                const isCritical = t.permissaoNecessaria === "processos:manage";
                return (
                  <Button
                    key={t.acao}
                    type="button"
                    disabled={!hasPermission || transicao.isPending}
                    title={!hasPermission ? `Sem permissao: requer ${t.permissaoNecessaria}` : undefined}
                    className={isCritical
                      ? "variant-outline border-slate-300 dark:border-slate-700 rounded-none font-bold h-10 px-4 text-slate-700 dark:text-slate-300"
                      : "bg-blue-600 hover:bg-blue-700 text-white rounded-none font-bold h-10 px-4"}
                    onClick={() => onTransicaoClick(t)}
                  >
                    {/* icon per acao */}
                    {t.label}
                  </Button>
                );
              })}
            </div>
          )}
        </div>
      </>
    ) : null}
  </CardContent>
</Card>
```

**Justification Dialog** — `Dialog` from shadcn (to be installed). RHF form pattern from existing form blocks (lines 108-124):
```tsx
<Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
  <DialogContent className="rounded-none shadow-2xl">
    <DialogHeader>
      <DialogTitle>{activeTransicao?.label} Processo</DialogTitle>
      <DialogDescription>
        Esta acao requer justificativa e regista uma movimentacao no processo.
      </DialogDescription>
    </DialogHeader>
    <form onSubmit={justificativaForm.handleSubmit(onSubmitTransicao)}>
      <div className="space-y-4 py-4">
        <div className="space-y-2">
          <Label htmlFor="justificativa">Justificativa</Label>
          <Textarea
            id="justificativa"
            placeholder="Descreva o motivo desta transicao (minimo 10 caracteres)..."
            className="min-h-[100px] rounded-none border-slate-300 dark:border-slate-700"
            {...justificativaForm.register("justificativa")}
          />
          {justificativaForm.formState.errors.justificativa ? (
            <p className="text-sm text-red-600">
              {justificativaForm.formState.errors.justificativa.message}
            </p>
          ) : null}
        </div>
      </div>
      <DialogFooter>
        <Button type="button" variant="ghost" className="rounded-none" onClick={() => setDialogOpen(false)}>
          Cancelar
        </Button>
        <Button
          type="submit"
          disabled={transicao.isPending}
          className="rounded-none font-bold bg-blue-600 hover:bg-blue-700"
        >
          {transicao.isPending ? "A processar..." : "Confirmar"}
        </Button>
      </DialogFooter>
    </form>
  </DialogContent>
</Dialog>
```

**Prazos card structure** — follows same Card/CardContent shell as Conflict Check. List row pattern from UI-SPEC:
```tsx
<Card>
  <CardHeader>
    <div className="flex items-center justify-between">
      <CardTitle>Prazos</CardTitle>
      {canEditProcessos ? (
        <Button
          type="button"
          className="rounded-none font-bold bg-blue-600 hover:bg-blue-700 text-white h-9 px-3"
          onClick={() => setPrazoDialogOpen(true)}
        >
          <Plus className="h-4 w-4 mr-1" />
          Novo Prazo
        </Button>
      ) : null}
    </div>
  </CardHeader>
  <CardContent className="p-0">
    {prazos.isLoading ? (
      <p className="p-6 text-sm text-slate-500 dark:text-slate-400">A carregar prazos...</p>
    ) : prazos.isError ? (
      <p className="p-6 text-sm text-red-600">Erro ao carregar os prazos. Tente novamente.</p>
    ) : (prazos.data ?? []).length === 0 ? (
      <p className="p-6 text-sm text-slate-500 dark:text-slate-400">
        Nenhum prazo registado para este processo.
      </p>
    ) : (
      <div className="divide-y divide-slate-100 dark:divide-slate-800">
        {(prazos.data ?? []).map((p) => (
          <div key={p.id}
            className="px-4 py-3 flex items-center justify-between gap-4 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors">
            {/* left: descricao + data_limite */}
            {/* center: prioridade + responsavel */}
            {/* right: risco badge + escalonado icon + concluido toggle */}
          </div>
        ))}
      </div>
    )}
  </CardContent>
</Card>
```

**State management additions** in `ProcessoDetailContent`:
```typescript
const [dialogOpen, setDialogOpen] = React.useState(false);
const [activeTransicao, setActiveTransicao] = React.useState<TransicaoInfo | null>(null);
const [prazoDialogOpen, setPrazoDialogOpen] = React.useState(false);
const [transicaoError, setTransicaoError] = React.useState<string | null>(null);

const workflow = useWorkflow(id);
const prazos = usePrazos(id);
const transicao = useExecutarTransicao(id);
const createPrazo = useCreatePrazo(id);
const toggleConcluido = useTogglePrazoConcluido(id);

const justificativaForm = useForm<TransicaoJustificativaFormValues>({
  resolver: zodResolver(transicaoJustificativaFormSchema),
  defaultValues: { justificativa: "" },
});
const prazoForm = useForm<PrazoFormValues>({
  resolver: zodResolver(prazoFormSchema),
  defaultValues: { descricao: "", dataLimite: "", prioridade: "MEDIA" },
});
```

**onTransicaoClick handler** (critical transitions open Dialog; non-critical execute directly):
```typescript
const onTransicaoClick = (t: TransicaoInfo) => {
  setTransicaoError(null);
  if (t.requerJustificativa) {
    setActiveTransicao(t);
    justificativaForm.reset({ justificativa: "" });
    setDialogOpen(true);
  } else {
    onSubmitTransicaoDirecta(t.acao);
  }
};

const onSubmitTransicaoDirecta = async (acao: string) => {
  try {
    await transicao.mutateAsync({ acao });
  } catch (e) {
    setTransicaoError(e instanceof Error ? e.message : "Nao foi possivel executar a transicao. Verifique os requisitos e tente novamente.");
  }
};

const onSubmitTransicao = async (values: TransicaoJustificativaFormValues) => {
  if (!activeTransicao) return;
  try {
    await transicao.mutateAsync({ acao: activeTransicao.acao, payload: { justificativa: values.justificativa } });
    setDialogOpen(false);
  } catch (e) {
    setTransicaoError(e instanceof Error ? e.message : "Nao foi possivel executar a transicao. Verifique os requisitos e tente novamente.");
  }
};
```

---

### `web/src/app/(dashboard)/processos/page.tsx` — listing signals (modified, component/page, CRUD)

**Analog:** same file — existing `estadoVariant` switch (lines 308-317) and sub-line pattern (lines 330-332).

**Responsavel sub-line** (insert after `Entrada:` div at line 331):
```tsx
{/* Responsavel signal — inserted below "Entrada:" line */}
<div className="text-[11px] font-medium text-slate-400 dark:text-slate-500 mt-0.5">
  Resp.: {p.responsavel_nome ?? "—"}
</div>
```

**Risco badge signal in ESTADO cell** (insert after the estado Badge at line 344):
```tsx
{/* Prazo risco signal — only render for proximo or vencido */}
{p.risco_mais_critico && p.risco_mais_critico !== "ok" ? (
  <div className="mt-1 flex items-center gap-1">
    <Badge
      variant={prazosRiscoToVariant(p.risco_mais_critico)}
      className="rounded-none font-bold tracking-wide text-[11px]"
    >
      {prazosRiscoToLabel(p.risco_mais_critico)}
    </Badge>
    {p.tem_prazo_escalonado ? (
      <AlertCircle className="h-3 w-3 text-amber-500 dark:text-amber-400 inline-block ml-1" />
    ) : null}
  </div>
) : null}
```

**Note on data source for listing signals:** The processos list endpoint (`GET /api/v1/processos`) will need to be extended to include `responsavel_nome`, `risco_mais_critico`, and `tem_prazo_escalonado` fields in its response — computed server-side by joining User and Prazo tables. The planner should include this as a sub-task of the listing page modification.

---

## Shared Patterns

### Multi-Tenant Scoping
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 72-75 (`getTenantId()`) and lines 653-655 (tenant guard check)
**Apply to:** All new endpoints (`/workflow`, `/transicao/{acao}`, `/prazos`, `/prazos/{id}/concluido`)
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}

// In every handler:
if (processo == null || !processo.getTenantId().equals(tenantId)) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
}
```

### RBAC Method-Level Authorization
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 640, 649, 739, 786
**Apply to:** All new endpoints
```java
@PreAuthorize("hasAuthority('processos:view')")   // GET /workflow, GET /prazos
@PreAuthorize("hasAuthority('processos:edit')")   // POST /prazos, PATCH /concluido, POST /transicao (normal transitions)
// manage-level transitions checked inline via SecurityContextHolder (see gate pattern above)
```
Note: `processos:edit` and `processos:manage` are already seeded in `DatabaseSeeder.seedRbac()` (lines 296-297, 334-336). No seed changes needed.

### @Transactional for State-Changing Operations
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 738, 785
**Apply to:** `executarTransicao`, `createPrazo`, `togglePrazoConcluido`
```java
@Transactional
@PreAuthorize("hasAuthority('processos:edit')")
@PostMapping("/processos/{id}/transicao/{acao}")
```

### Frontend Permission Gate (Component Level)
**Source:** `web/src/app/(dashboard)/processos/[id]/page.tsx` lines 65-69
**Apply to:** Workflow transition buttons, "Novo Prazo" button, concluido toggle
```typescript
const canEditProcessos = permissions.can.edit("processos");
const canManageProcessos = permissions.can.manage("processos");
// For buttons:
disabled={!canEditProcessos}   // normal transitions
disabled={!canManageProcessos} // critical transitions
```

### apiFetch + useMutation Error Handling
**Source:** `web/src/app/(dashboard)/processos/[id]/page.tsx` lines 137-144 (`onFormalizar`)
**Apply to:** `onSubmitTransicao`, `onSubmitTransicaoDirecta`, prazo create/toggle handlers
```typescript
try {
  await mutate.mutateAsync(payload);
  // success path
} catch (e) {
  setError(e instanceof Error ? e.message : "Mensagem de erro padrao");
}
```
`apiFetch` already shows error toasts for non-401/403 errors — set local error state for inline display only, do not add a redundant toast.

### Anti-Safe Harbor CSS Convention
**Source:** `web/src/app/(dashboard)/processos/page.tsx` lines 107, 126, 339, 342
**Apply to:** All new UI elements (workflow card, transition buttons, risco badges, prazo list rows, Dialog)
```
rounded-none   (no border-radius on any interactive element)
font-bold tracking-wide  (all badges and CTA buttons)
focus-visible:ring-blue-500  (all inputs and textareas)
shadow-none    (CTA buttons)
```

### dl Grid for Read-Only Metadata
**Source:** `web/src/app/(dashboard)/processos/[id]/page.tsx` lines 242-274
**Apply to:** Workflow card Row 1 (Estado / Responsavel / Proximo Passo)
```tsx
<dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
  <dt className="text-neutral-500 dark:text-neutral-400">Label</dt>
  <dd className="col-span-2 font-medium">Value</dd>
</dl>
```

### Badge Variant Pattern
**Source:** `web/src/app/(dashboard)/processos/page.tsx` lines 308-317 (`estadoVariant` switch)
**Apply to:** Risco badges in prazos list and listing; estado badge in workflow card header
```typescript
// Risco variant — use prazosRiscoToVariant() from lib/prazos.ts
// Estado variant — reuse existing estadoVariant switch (no changes needed)
```

---

## No Analog Found

All files have close analogs. No gaps.

| File | Note |
|------|------|
| `backend/.../dtos/WorkflowResponse.java` | No workflow/state-machine response DTO exists yet. Nearest is `ConflictCheckResponse` (nested record pattern). The inner `TransicaoInfo` record may alternatively be defined as a top-level record in the same package if the static map reference requires it. |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/` (models, repositories, dtos, controllers, seed), `web/src/hooks/`, `web/src/schemas/`, `web/src/lib/`, `web/src/types/`, `web/src/app/(dashboard)/processos/`
**Files scanned:** 28
**Pattern extraction date:** 2026-06-15
