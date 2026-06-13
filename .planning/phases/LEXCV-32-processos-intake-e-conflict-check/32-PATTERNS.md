# Phase 32: Processos - Intake e Conflict Check - Pattern Map

**Mapped:** 2026-06-13
**Files analyzed:** 13 new/modified files
**Analogs found:** 12 / 13

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `backend/.../models/ConflictCheckDecisao.java` | model | CRUD | `backend/.../models/Processo.java` | role-match |
| `backend/.../repositories/ConflictCheckDecisaoRepository.java` | repository | CRUD | `backend/.../repositories/ProcessoRepository.java` | exact |
| `backend/.../dtos/ConflictCheckRequest.java` | DTO | request-response | `backend/.../dtos/ClienteMergeRequest.java` | role-match |
| `backend/.../dtos/ConflictCheckResponse.java` | DTO | request-response | `backend/.../dtos/DashboardKpiResponse.java` | role-match |
| `backend/.../controllers/ResourceController.java` (modified) | controller | request-response | same file — existing processo CRUD section (lines 502-660) | exact |
| `backend/.../seed/DatabaseSeeder.java` (modified) | seed/config | batch | same file — `seedRbac()` method (lines 293-350) | exact |
| `web/src/types/processos.ts` (modified) | types | — | same file — existing type declarations | exact |
| `web/src/schemas/processos.ts` (modified) | schema/validation | request-response | `web/src/schemas/clientes.ts` | role-match |
| `web/src/hooks/use-processos.ts` (modified) | hook/service | request-response | same file — existing `useCreateProcesso` / `useMutation` blocks | exact |
| `web/src/app/(dashboard)/processos/novo/page.tsx` (replaced) | component/page | request-response | `web/src/app/(dashboard)/processos/novo/page.tsx` (current) | exact |
| `web/src/app/(dashboard)/processos/page.tsx` (modified) | component/page | CRUD | same file — `estadoVariant` switch + filter select block | exact |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` (modified) | component/page | CRUD | same file — `dl` grid card pattern | exact |
| `web/src/lib/permissions.ts` (read-only reference) | utility | — | — | no-analog-needed |

---

## Pattern Assignments

### `backend/.../models/ConflictCheckDecisao.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/models/Processo.java`

**Entity structure pattern** (lines 1-54):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

    // nivel: sem_conflito | potencial | sanavel | impeditivo
    @Column(name = "nivel", nullable = false)
    private String nivel;

    @Column(name = "justificativa", length = 2000)
    private String justificativa;

    @Column(name = "decisor_id", nullable = false)
    private UUID decisorId;

    @Column(name = "data_decisao", nullable = false)
    private LocalDate dataDecisao;

    @Column(name = "referencia_evidencia")
    private String referenciaEvidencia;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

**Key rules to copy from Processo.java:**
- `@Column(name = "tenant_id", nullable = false)` — every domain entity must carry `tenantId`
- `@GeneratedValue(strategy = GenerationType.UUID)` — UUID primary key (not IDENTITY)
- `@PrePersist` for `createdAt` with `LocalDateTime.now()`
- Lombok `@Builder` / `@Getter` / `@Setter` / `@NoArgsConstructor` / `@AllArgsConstructor` on every entity

---

### `backend/.../repositories/ConflictCheckDecisaoRepository.java` (repository, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java`

**Full file pattern** (lines 1-12):
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

The closest additional model is `ClienteRepository.java` (lines 1-13) for the named finder with NIF exact match — re-use the `findByTenantIdAnd*` naming convention.

---

### `backend/.../dtos/ConflictCheckRequest.java` (DTO, request-response)

**Analog:** `backend/src/main/java/com/lexcv/dtos/ClienteMergeRequest.java`

Java record pattern (full file):
```java
package com.lexcv.dtos;

import java.util.UUID;

public record ConflictCheckRequest(
        UUID processoId
) {}
```

And for the decision payload:
```java
public record ConflictCheckDecisaoRequest(
        UUID processoId,
        String nivel,         // sem_conflito | potencial | sanavel | impeditivo
        String justificativa,
        String referenciaEvidencia
) {}
```

Pattern note: `ClienteMergeRequest.java` shows the Java record idiom used for simple command payloads — no Jackson annotations needed, Spring deserializes automatically.

---

### `backend/.../dtos/ConflictCheckResponse.java` (DTO, request-response)

**Analog:** `backend/src/main/java/com/lexcv/dtos/DashboardKpiResponse.java`

Read `DashboardKpiResponse.java` for the record-of-records pattern if it exists. The conflict check response returns a list of matches plus a suggested level. Inline `Map.of(...)` approach (as used in `mergeClientes` response, ResourceController lines 491-496) is acceptable for a single-use endpoint:

```java
// Inside the endpoint handler body — matches the mergeClientes return style
return ResponseEntity.ok(Map.of(
    "matches", matchList,          // List<Map<String, Object>>
    "nivel_sugerido", nivelSugerido  // String
));
```

Alternatively, define a proper record following the ClienteMergeRequest style:
```java
public record ConflictCheckResponse(
        List<ConflictMatchDto> matches,
        String nivelSugerido
) {}

public record ConflictMatchDto(
        String entidadeId,
        String entidadeTipo,   // "cliente" | "parte"
        String nome,
        String nif,
        String nivelConflito,
        String motivo
) {}
```

---

### `backend/.../controllers/ResourceController.java` — new endpoints (controller, request-response)

**Analog:** existing Processo section in same file (lines 502-660) and `mergeClientes` (lines 429-497).

**Imports pattern** (lines 1-32) — already present in file, no new imports needed except possibly for the string-similarity helper.

**RBAC + tenant scoping pattern** (lines 567-583):
```java
@PreAuthorize("hasAuthority('processos:create')")
@PostMapping("/processos/{id}/conflict-check")
public ResponseEntity<?> runConflictCheck(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    // ... search clientes + partes by tenant, compute matches
}

@PreAuthorize("hasAuthority('processos:manage')")
@PostMapping("/processos/{id}/conflict-check/decisao")
public ResponseEntity<?> registarDecisao(
        @PathVariable UUID id,
        @RequestBody ConflictCheckDecisaoRequest payload) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    // validate: prevent duplicate decision or enforce impeditivo block
}

@PreAuthorize("hasAuthority('processos:manage')")
@PostMapping("/processos/{id}/formalizar")
public ResponseEntity<?> formalizarProcesso(@PathVariable UUID id) {
    UUID tenantId = getTenantId();
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Processo não encontrado"));
    }
    // check: must have decisao AND nivel != impeditivo
    // transition: processo.setEstado("ATIVO"); processoRepository.save(processo);
}
```

**Tenant-scoped error check** (mandatory, copied from lines 578-581):
```java
if (processo == null || !processo.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
}
```

**Conflict match logic** — copy the `contains()` + exact NIF match pattern from `listClientes` (lines 100-120) and `ClienteRepository.findByTenantIdAndNif`:
```java
// Exact NIF match
List<Cliente> nifMatches = clienteRepository.findByTenantIdAndNif(tenantId, nifToSearch);
// Fuzzy name match (reuse contains() helper at line 60)
List<Cliente> nameMatches = clienteRepository.findByTenantId(tenantId).stream()
    .filter(c -> contains(c.getNome(), nomeLower))
    .toList();
```

**POST /processos intake** — the existing `createProcesso` (lines 568-573) sets `estado="TRIAGEM"` implicitly via the payload. The new intake endpoint either extends this or validates post-creation that `estado` is set to `TRIAGEM`:
```java
// Modified createProcesso or new intake endpoint:
processo.setTenantId(getTenantId());
processo.setEstado("TRIAGEM");   // force triagem on intake creation
Processo saved = processoRepository.save(processo);
return ResponseEntity.status(HttpStatus.CREATED).body(saved);
```

---

### `backend/.../seed/DatabaseSeeder.java` — RBAC additions (seed/config, batch)

**Analog:** same file, `seedRbac()` method (lines 293-350).

**New permission keys pattern** (lines 294-301):
```java
List<String> permKeys = Arrays.asList(
    "clientes:view", "clientes:edit",
    "processos:view", "processos:edit",
    "processos:create",   // NEW — conflict check execution
    "processos:manage",   // NEW — decision registration + formalization
    "agenda:view", "agenda:edit",
    ...
);
```

**Role assignment pattern** (lines 329-339):
```java
upsertRolePermissions("ADVOGADO", Arrays.asList(
    permissionMap.get("processos:view"),
    permissionMap.get("processos:edit"),
    permissionMap.get("processos:create"),  // can run conflict check
    permissionMap.get("processos:manage"),  // can decide + formalize
    ...
));
```

---

### `web/src/types/processos.ts` — type additions (types)

**Analog:** same file, existing interface declarations (lines 1-103).

**New types to append** (following the existing interface style):
```typescript
export type ConflictNivel =
  | "sem_conflito"
  | "potencial"
  | "sanavel"
  | "impeditivo";

export interface ConflictMatch {
  entidadeId: string;
  entidadeTipo: "cliente" | "parte";
  nome: string;
  nif?: string;
  nivelConflito: ConflictNivel;
  motivo?: string;
}

export interface ConflictCheckResponse {
  matches: ConflictMatch[];
  nivelSugerido: ConflictNivel;
}

export interface ConflictCheckDecisao {
  id: string;
  tenant_id: string;
  processo_id: string;
  nivel: ConflictNivel;
  justificativa?: string;
  decisorId: string;         // UUID, resolved to name in UI
  dataDecisao: string;       // ISO date
  referenciaEvidencia?: string;
  created_at: string;
}

export interface ConflictCheckDecisaoRequest {
  nivel: ConflictNivel;
  justificativa?: string;
  referenciaEvidencia?: string;
}
```

---

### `web/src/schemas/processos.ts` — new Zod schemas (schema/validation)

**Analog:** `web/src/schemas/clientes.ts` (full file) for the `optionalTrimmedString` helper and `superRefine` cross-field validation; `web/src/schemas/processos.ts` (existing, lines 1-47) for the `processoFormSchema` baseline.

**optionalTrimmedString helper** (lines 3-7 of clientes.ts — identical pattern already in processos.ts lines 3-7):
```typescript
const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();
```

**New intake schema** (extends the existing `processoFormSchema`):
```typescript
export const conflictNivelEnum = z.enum([
  "sem_conflito",
  "potencial",
  "sanavel",
  "impeditivo",
]);

export const conflictCheckDecisaoFormSchema = z
  .object({
    nivel: conflictNivelEnum,
    justificativa: optionalTrimmedString,
    referenciaEvidencia: optionalTrimmedString,
  })
  .superRefine((data, ctx) => {
    // justificativa required when nivel is potencial or sanavel
    if (
      (data.nivel === "potencial" || data.nivel === "sanavel") &&
      !data.justificativa
    ) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Justificativa é obrigatória para este nível de conflito",
        path: ["justificativa"],
      });
    }
  });

export type ConflictCheckDecisaoFormValues = z.infer<
  typeof conflictCheckDecisaoFormSchema
>;
```

**Cross-field validation pattern** copied from `clientes.ts` lines 36-53 (`superRefine` with `ctx.addIssue` + `path` targeting).

---

### `web/src/hooks/use-processos.ts` — new hooks (hook/service, request-response)

**Analog:** same file — `useCreateProcesso` (lines 156-171) for mutation pattern; `useProcessoPartes` (lines 213-221) for sub-resource query pattern.

**Mutation hook pattern** (lines 156-171):
```typescript
export function useRunConflictCheck(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await apiFetch<ConflictCheckResponse>(
        `/processos/${encodeURIComponent(processoId)}/conflict-check`,
        { method: "POST" },
      );
      return response;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["processos", "conflict-check", processoId],
      });
    },
  });
}

export function useRegistarDecisaoConflito(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: ConflictCheckDecisaoRequest) => {
      const response = await apiFetch<ConflictCheckDecisao>(
        `/processos/${encodeURIComponent(processoId)}/conflict-check/decisao`,
        {
          method: "POST",
          body: JSON.stringify(payload),
        },
      );
      return response;
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["processos", "conflict-check", processoId],
        }),
        queryClient.invalidateQueries({
          queryKey: ["processos", "detail", processoId],
        }),
      ]);
    },
  });
}

export function useFormalizarProcesso(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await apiFetch<Processo>(
        `/processos/${encodeURIComponent(processoId)}/formalizar`,
        { method: "POST" },
      );
      return normalizeProcesso(response as ProcessoApi);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", processoId], updated),
      ]);
    },
  });
}
```

**Sub-resource query hook** (lines 213-221) — for fetching saved decisao:
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

**Import additions needed** — add the new types from `@/types/processos` to the existing import block (line 4-16 of the hook file).

---

### `web/src/app/(dashboard)/processos/novo/page.tsx` (replaced — 3-step wizard)

**Analog:** `web/src/app/(dashboard)/processos/novo/page.tsx` (current, full file) for page shell, permission gate, RHF form, and `router.push` on success.

**Permission gate pattern** (lines 26-39):
```typescript
export default function ProcessoCreatePage() {
  const permissions = usePermissions();
  const canCreateProcessos = permissions.can.create("processos");

  if (!permissions.isLoading && !canCreateProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar processos."
        backHref="/processos"
      />
    );
  }

  return <ProcessoCreateContent />;
}
```

**RHF form + submit pattern** (lines 50-78):
```typescript
const form = useForm<ProcessoFormValues>({
  resolver: zodResolver(processoFormSchema),
  defaultValues: { cliente_id: "", estado: "TRIAGEM", ... },
});

const onSubmit = async (values: ProcessoFormValues) => {
  setServerError(null);
  if (!canCreateProcessos) {
    setServerError("Não tem permissão para criar processos");
    return;
  }
  try {
    const res = await create.mutateAsync(values satisfies ProcessoCreateRequest);
    router.push(`/processos/${encodeURIComponent(res.id)}`);
  } catch (e) {
    setServerError(e instanceof Error ? e.message : "Erro ao criar processo");
  }
};
```

**Step indicator pattern** — no analog exists; must be built from scratch using shadcn `Badge` + lucide `Check`/`Circle` as documented in UI-SPEC.

**Stepper state management** — use `React.useState<1 | 2 | 3>("step", 1)` local state; each step's "Continue" button advances `setStep(n + 1)` after its async action completes. Do not use router navigation between steps (all 3 steps live on `/processos/novo`).

**Error display pattern** (line 199 of current novo/page.tsx):
```typescript
{serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}
```

**Blocked action pattern for formalizar** (from UI-SPEC, copy this exact structure):
```typescript
<Button
  type="button"
  disabled={isBlocked}
  className={cn(
    "rounded-none font-bold shadow-none",
    isBlocked && "opacity-50 cursor-not-allowed",
  )}
  onClick={onFormalizar}
>
  Formalizar Processo
</Button>
{isBlocked ? (
  <p className="text-sm text-red-600">{blockedReason}</p>
) : null}
```

---

### `web/src/app/(dashboard)/processos/page.tsx` — triagem badge + filter (modified)

**Analog:** same file — `estadoVariant` switch (lines 304-312) and estado filter select (lines 215-226).

**Estado badge mapping extension** (lines 304-312, add TRIAGEM case):
```typescript
const estadoVariant =
  estado === "ATIVO"
    ? "green"
    : estado === "SUSPENSO"
      ? "amber"
      : estado === "TRIAGEM"
        ? "purple"           // NEW
        : estado === "CONCLUIDO" || estado === "ENCERRADO"
          ? "gray"
          : "secondary";

// Label override for TRIAGEM:
const estadoLabel =
  estado === "TRIAGEM" ? "EM TRIAGEM" : (p.estado ?? "—");
```

**Filter select extension** (lines 220-225, add TRIAGEM option):
```tsx
<select value={draftEstado} onChange={(e) => setDraftEstado(e.target.value)}
  className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500">
  <option value="">Todos</option>
  <option value="TRIAGEM">Em triagem</option>   {/* NEW */}
  <option value="ATIVO">Ativo</option>
  <option value="SUSPENSO">Suspenso</option>
  <option value="ENCERRADO">Encerrado</option>
  <option value="CONCLUIDO">Concluído</option>
</select>
```

**Badge render** (line 335):
```tsx
<Badge variant={estadoVariant as "green" | "amber" | "gray" | "purple" | "secondary"}
  className="rounded-none font-bold tracking-wide">
  {estadoLabel}
</Badge>
```

---

### `web/src/app/(dashboard)/processos/[id]/page.tsx` — conflict section (modified)

**Analog:** same file — `dl` grid card block (lines 211-218) for the metadata display; existing `Card`/`CardHeader`/`CardContent` pattern.

**`dl` grid pattern** (lines 216-218):
```tsx
<dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
  <dt className="text-neutral-500 dark:text-neutral-400">Nível</dt>
  <dd className="col-span-2 font-medium">
    <Badge variant={nivelToVariant(decisao.nivel)} className="rounded-none font-bold tracking-wide">
      {nivelLabel(decisao.nivel)}
    </Badge>
  </dd>
  <dt className="text-neutral-500 dark:text-slate-400">Decisor</dt>
  <dd className="col-span-2 font-medium">{decisao.decisorNome ?? decisao.decisorId}</dd>
  <dt className="text-neutral-500 dark:text-slate-400">Data</dt>
  <dd className="col-span-2 font-medium">{decisao.dataDecisao}</dd>
</dl>
```

**Conditional section visibility** pattern (copy from existing `processo.data ?` block, lines 209-210):
```tsx
{(processo.data?.estado === "TRIAGEM" || decisao.data) ? (
  <Card> ... </Card>
) : null}
```

**Empty state pattern** (copy from processos listing, line 289):
```tsx
<div className="p-6 text-sm text-slate-500">
  O conflict check ainda não foi executado para este processo.
</div>
```

**Permission-gated inline action** (copy from existing `canEditProcessos` guards, lines 184-189):
```tsx
{canManageProcessos && processo.data?.estado === "TRIAGEM" ? (
  <Button
    className="rounded-none font-bold shadow-none"
    disabled={isFormalizarBlocked}
    onClick={onFormalizar}
  >
    Formalizar Processo
  </Button>
) : null}
```

---

## Shared Patterns

### Multi-Tenant Scoping
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 54-58 (`getTenantId()`) and lines 578-581 (tenant guard check)
**Apply to:** All 3 new backend endpoints (`/conflict-check`, `/conflict-check/decisao`, `/formalizar`) and the modified `createProcesso`
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}

// In every handler:
if (processo == null || !processo.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
}
```

### RBAC Method-Level Authorization
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 502, 567, 575, 607
**Apply to:** All new endpoints
```java
@PreAuthorize("hasAuthority('processos:create')")   // run conflict check
@PreAuthorize("hasAuthority('processos:manage')")   // register decision + formalize
```
Note: `processos:create` and `processos:manage` must be seeded in `DatabaseSeeder.seedRbac()` before use.

### Frontend Permission Gate (Page Level)
**Source:** `web/src/app/(dashboard)/processos/novo/page.tsx` lines 26-37
**Apply to:** New wizard page; action buttons in detail page
```typescript
const permissions = usePermissions();
const canCreateProcessos = permissions.can.create("processos");
const canManageProcessos = permissions.can.manage("processos");

if (!permissions.isLoading && !canCreateProcessos) {
  return <AccessDeniedState ... />;
}
```

### apiFetch + useMutation Error Handling
**Source:** `web/src/app/(dashboard)/processos/novo/page.tsx` lines 66-78; `web/src/lib/api.ts` lines 26-37
**Apply to:** All mutation hooks (`useRunConflictCheck`, `useRegistarDecisaoConflito`, `useFormalizarProcesso`)
```typescript
try {
  const res = await mutate.mutateAsync(payload);
  // success path
} catch (e) {
  setServerError(e instanceof Error ? e.message : "Mensagem de erro padrão");
}
```
`apiFetch` already shows error toasts for non-401/403 errors — don't add a redundant toast in the component; just set local `serverError` state for inline display.

### Badge Conflict Level Helper
**Source:** `web/src/app/(dashboard)/processos/page.tsx` lines 304-312 (`estadoVariant` pattern)
**Apply to:** Conflict level badges in Step 2, Step 3, and detail page section
```typescript
// Shared helper (define once, import where needed):
export function conflictNivelToVariant(nivel: ConflictNivel) {
  return nivel === "sem_conflito"
    ? "green"
    : nivel === "potencial"
      ? "amber"
      : nivel === "sanavel"
        ? "blue"
        : "red"; // impeditivo
}

export function conflictNivelToLabel(nivel: ConflictNivel) {
  return nivel === "sem_conflito"
    ? "SEM CONFLITO"
    : nivel === "potencial"
      ? "POTENCIAL"
      : nivel === "sanavel"
        ? "SANÁVEL"
        : "IMPEDITIVO";
}
```
Place in `web/src/lib/conflict-check.ts` or inline in each component — no new component file needed, just a utility.

### Anti-Safe Harbor CSS Convention
**Source:** `web/src/app/(dashboard)/processos/page.tsx` lines 107, 126, 175, 182
**Apply to:** All new UI elements in this phase
```
rounded-none   (no border-radius on any interactive element)
font-bold tracking-wide  (badges and CTA buttons)
focus-visible:ring-blue-500  (all inputs)
shadow-none    (CTA buttons)
```

### `dl` Grid for Read-Only Metadata
**Source:** `web/src/app/(dashboard)/processos/[id]/page.tsx` lines 216-218
**Apply to:** Step 3 (Abertura) summary card; conflict check decisao display in detail page
```tsx
<dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
  <dt className="text-neutral-500 dark:text-neutral-400">Label</dt>
  <dd className="col-span-2 font-medium">Value</dd>
</dl>
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Step indicator UI component (inline in wizard page) | component | — | No multi-step wizard exists in the codebase. Build inline in the novo/page.tsx using raw div + lucide icons as described in UI-SPEC lines 97-101. No separate component file needed per UI-SPEC component inventory. |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/`, `web/src/hooks/`, `web/src/schemas/`, `web/src/app/(dashboard)/processos/`, `web/src/types/`, `web/src/lib/`, `web/src/components/ui/`
**Files scanned:** 23
**Pattern extraction date:** 2026-06-13
