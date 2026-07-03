# Phase 74: Enum `documento_tipo` (BI/NIF/Restrição por Tipo) - Pattern Map

**Mapped:** 2026-07-03
**Files analyzed:** 7
**Analogs found:** 6 / 7

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` | model (enum) | CRUD | `backend/src/main/java/com/lexcv/models/TipoCliente.java` | exact |
| `backend/src/main/java/com/lexcv/models/Cliente.java` | model (entity) | CRUD | itself (modify in place) | n/a — modify existing |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (createCliente/updateCliente) | controller | request-response | same file, other ad-hoc `BAD_REQUEST` validation blocks (e.g. `mergeClientes`, `createProcesso` responsavelId check) | exact |
| `web/src/schemas/clientes.ts` | schema (Zod validation) | transform | itself (modify existing `superRefine`) | exact |
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | component (form page) | request-response | `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (near-duplicate sibling) | exact |
| `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` | component (form page) | request-response | `web/src/app/(dashboard)/clientes/novo/page.tsx` (near-duplicate sibling) | exact |
| `web/src/lib/cliente-documento-tipo.ts` (new) | utility (shared lookup module) | transform | `web/src/lib/prazos.ts` | role-match (strong) |
| `backend` migration script (new, defensive NIF cleanup) | migration | batch | none — no SQL migration files exist in this repo | no analog |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` (model enum, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/models/TipoCliente.java` (full file, 7 lines — read in full)

**Full pattern** — this is the simplest possible Java enum shape used throughout `models/`:
```java
package com.lexcv.models;

public enum TipoCliente {
    PARTICULAR,
    EMPRESA
}
```

**Current `DocumentoTipo.java`** (to be edited, not replaced) — full file:
```java
package com.lexcv.models;

public enum DocumentoTipo {
    NIF,
    CNI,
    PASSAPORTE,
    REG_COMERCIAL
}
```

**Required change:** remove `NIF`, add `BI`, keep `CNI`, `PASSAPORTE`, `REG_COMERCIAL`. Same flat structure as `TipoCliente` — no annotations, no `@JsonProperty`, one enum constant per line, alphabetical/logical grouping not enforced elsewhere in the codebase (existing order is arbitrary, so no strict ordering constraint).

---

### `backend/src/main/java/com/lexcv/models/Cliente.java` (entity, CRUD)

**Analog:** itself — no cross-field validation currently exists on this entity; the `documentoTipo`/`documentoNumero` fields are plain optional columns (lines 46-51, already read in full above). No Bean Validation annotation pattern to copy for cross-field (tipo × documentoTipo) checks — per CONTEXT.md decision, that validation is explicitly done in the controller, not here. No entity-level change needed beyond what the enum change already causes via `@Enumerated(EnumType.STRING)` (line 46-48) — Hibernate will simply accept the new enum's value set.

**Reference — existing field declaration** (`Cliente.java` lines 46-51):
```java
@Enumerated(EnumType.STRING)
@Column(name = "documento_tipo")
private DocumentoTipo documentoTipo;

@Column(name = "documento_numero")
private String documentoNumero;
```

No structural change to this class is required by this phase (confirmed against CONTEXT.md — only the enum + controller + frontend need changes). Listed here only because CONTEXT.md named it as an integration point to double-check.

---

### `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — `createCliente` / `updateCliente` (controller, request-response)

**Analog (ad-hoc BAD_REQUEST validation style):** same file, `mergeClientes` (lines 717-724) and `createProcesso` responsavelId tenant check (lines 922-929), and the date-range check in `createEvento` (lines 1857-1863).

**Imports already present** (file header, lines 1-41 — no new imports needed for a manual `if` check using existing `Cliente`, `HttpStatus`, `Map`, `ResponseEntity` already imported via `com.lexcv.models.*`, `org.springframework.http.*`, `java.util.*`):
```java
import com.lexcv.models.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.*;
```

**Ad-hoc cross-field validation pattern to copy** (`ResourceController.java` lines 1857-1863, `createEvento`):
```java
@PreAuthorize("hasAuthority('agenda:edit')")
@PostMapping("/eventos")
public ResponseEntity<?> createEvento(@RequestBody Evento evento) {
    if (evento.getDataInicio() != null && evento.getDataFim() != null && evento.getDataFim().isBefore(evento.getDataInicio())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "A data de fim não pode ser anterior à data de início"));
    }
```

**Tenant-scoped combination-check pattern to copy** (`ResourceController.java` lines 922-929, `createProcesso`):
```java
if (processo.getResponsavelId() != null) {
    User responsavel = userRepository.findById(processo.getResponsavelId()).orElse(null);
    if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "responsavelId não pertence a este tenant"));
    }
}
```

**Existing `createCliente`** (lines 218-242, full method — insertion point for new validation, right after `@Valid` runs, before `cliente.setTenantId`/save):
```java
@PreAuthorize("hasAuthority('clientes:edit')")
@PostMapping("/clientes")
public ResponseEntity<?> createCliente(@Valid @RequestBody Cliente cliente) {
    cliente.setTenantId(getTenantId());
    if (cliente.getAtivo() == null) {
        cliente.setAtivo(true);
    }
    synchronized (ClienteRepository.class) {
        java.util.Optional<Integer> result = clienteRepository.findMaxNumeroSequencialByTenantId(getTenantId());
        int maxSeq = result.orElse(0);
        int nextSeq = maxSeq + 1;
        cliente.setNumeroSequencial(nextSeq);
        cliente.setNumeroCliente(String.format("CLI-%04d", nextSeq));
    }
    Cliente saved = clienteRepository.save(cliente);
    // ...
}
```

**Existing `updateCliente`** (lines 254-288, full method — insertion point right after the `cliente == null` NOT_FOUND guard, before field copies):
```java
@PreAuthorize("hasAuthority('clientes:edit')")
@PutMapping("/clientes/{id}")
public ResponseEntity<?> updateCliente(@PathVariable UUID id, @Valid @RequestBody Cliente payload) {
    Cliente cliente = clienteRepository.findById(id).orElse(null);
    if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Cliente não encontrado"));
    }

    cliente.setNome(payload.getNome());
    cliente.setTipo(payload.getTipo());
    // ...
    cliente.setDocumentoTipo(payload.getDocumentoTipo());
    cliente.setDocumentoNumero(payload.getDocumentoNumero());
    // ...
}
```

**Recommended shape for the new check** (both create and update, using `payload`/`cliente` tipo + documentoTipo — combine the "not-empty" ad-hoc style + `Map.of("message", ...)` response style shown above):
```java
if (cliente.getDocumentoTipo() != null) {
    boolean particularOk = "PARTICULAR".equals(cliente.getTipo())
            && Set.of(DocumentoTipo.CNI, DocumentoTipo.BI, DocumentoTipo.PASSAPORTE).contains(cliente.getDocumentoTipo());
    boolean empresaOk = "EMPRESA".equals(cliente.getTipo())
            && cliente.getDocumentoTipo() == DocumentoTipo.REG_COMERCIAL;
    if (!particularOk && !empresaOk) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Tipo de documento inválido para o tipo de cliente selecionado"));
    }
}
```
(Exact message text left to implementer per CONTEXT.md "Claude's Discretion".)

---

### `web/src/schemas/clientes.ts` (Zod schema, transform)

**Analog:** itself — full file already read (69 lines).

**Current `superRefine` block to modify** (lines 49-67):
```typescript
.superRefine((data, ctx) => {
    if (data.documento_tipo && !data.documento_numero) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: "Número de documento é obrigatório se o tipo estiver selecionado",
        path: ["documento_numero"],
      });
    }
    if (data.documento_tipo === "NIF" && data.documento_numero) {
      const isDigitsOnly = /^\d+$/.test(data.documento_numero);
      if (data.documento_numero.length !== 9 || !isDigitsOnly) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: "NIF de Cabo Verde deve ter exatamente 9 dígitos",
          path: ["documento_numero"],
        });
      }
    }
  });
```

**Required change:** remove the `documento_tipo === "NIF"` branch entirely (NIF no longer a valid `documento_tipo` value — it's already handled by the dedicated `nif` field at line 25). Add a new branch validating `documento_tipo` against the allowed set for `data.tipo`, using the same `ctx.addIssue` / `path: ["documento_tipo"]` shape as the existing branch. Should call `getDocumentoTipoOptions(data.tipo)` from the new `web/src/lib/cliente-documento-tipo.ts` module (see below) rather than hardcoding the set again, to keep a single source of truth.

**`nifPattern` already exported** (line 3) — reusable if BI/CNI/PASSAPORTE need their own patterns later, though CONTEXT.md does not ask for per-type number format validation in this phase.

---

### `web/src/app/(dashboard)/clientes/novo/page.tsx` (component, request-response)

**Analog:** sibling file `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (near-identical structure — both will consume the same new shared module).

**Current local duplication to remove** (lines 28-34):
```typescript
import type { ClienteCreateRequest, DocumentoTipo } from "@/types/clientes";

const DOCUMENTO_TIPOS: readonly DocumentoTipo[] = ["NIF", "CNI", "PASSAPORTE", "REG_COMERCIAL"];

function toDocumentoTipo(value: string | undefined): DocumentoTipo | undefined {
  return DOCUMENTO_TIPOS.includes(value as DocumentoTipo) ? (value as DocumentoTipo) : undefined;
}
```
Replace with an import from the new `@/lib/cliente-documento-tipo` module (e.g. `getDocumentoTipoOptions`, `toDocumentoTipo`).

**`onTipoChange` / `confirmTipoChange` pattern** (lines 68-81) — the Particular↔Empresa switch confirmation dialog already exists; CONTEXT.md requires `confirmTipoChange` to also clear `documento_tipo`/`documento_numero` when the currently-selected value becomes invalid for the new tipo:
```typescript
function onTipoChange(newTipo: "PARTICULAR" | "EMPRESA") {
  const currentTipo = form.getValues("tipo");
  if (currentTipo && currentTipo !== newTipo) {
    setPendingTipo(newTipo);
  } else {
    form.setValue("tipo", newTipo, { shouldValidate: true });
  }
}

function confirmTipoChange() {
  if (!pendingTipo) return;
  form.setValue("tipo", pendingTipo, { shouldValidate: true });
  setPendingTipo(null);
}
```

**Dropdown render pattern to replace with a `.map()` over shared options** (lines 234-250):
```tsx
<Label htmlFor="documento_tipo">Tipo de Documento</Label>
<select
  id="documento_tipo"
  className={selectClassName}
  {...form.register("documento_tipo")}
>
  <option value="">Nenhum</option>
  <option value="NIF">NIF</option>
  <option value="CNI">CNI</option>
  <option value="PASSAPORTE">Passaporte</option>
  <option value="REG_COMERCIAL">Registo Comercial</option>
</select>
{form.formState.errors.documento_tipo ? (
  <p className="text-sm text-red-600">{form.formState.errors.documento_tipo.message}</p>
) : null}
```
Should become a `.map()` over `getDocumentoTipoOptions(form.watch("tipo"))` (label + value pairs), preserving the "Nenhum" empty option and the existing `selectClassName` styling constant (defined at line 36-37).

**Type-switch confirmation dialog** (lines 337-352, full block) — reuse as-is, only the `confirmTipoChange` body needs the new clearing logic:
```tsx
<Dialog open={!!pendingTipo} onOpenChange={(open) => { if (!open) setPendingTipo(null); }}>
  <DialogContent>
    <DialogHeader>
      <DialogTitle>Mudar tipo de cliente</DialogTitle>
      <DialogDescription>
        Mudar o tipo irá limpar os dados de {pendingTipo === "PARTICULAR" ? "Empresa" : "Particular"}. Continuar?
      </DialogDescription>
    </DialogHeader>
    <DialogFooter>
      <Button variant="outline" onClick={() => setPendingTipo(null)}>
        Cancelar
      </Button>
      <Button onClick={confirmTipoChange}>Continuar</Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
```

---

### `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` (component, request-response)

**Analog:** sibling `novo/page.tsx` (same patterns apply — this file has the identical `DOCUMENTO_TIPOS`/`toDocumentoTipo` duplication at lines 30-34, identical `onTipoChange`/`confirmTipoChange` at lines 97-110, and an identical dropdown block further down plus an identical confirmation `Dialog` at lines 683-698).

**Extra detail specific to this file** — form reset reads both snake_case and camelCase API shapes (line 141):
```typescript
documento_tipo: cliente.data.documento_tipo ?? cliente.data.documentoTipo ?? "",
documento_numero: cliente.data.documento_numero ?? cliente.data.documentoNumero ?? "",
```
This dual-read pattern must be preserved when refactoring — it is not touched by the enum change but should not regress.

**Same dropdown + dialog + toDocumentoTipo/DOCUMENTO_TIPOS refactor applies here as in `novo/page.tsx`.** Apply identical replacement using the shared `web/src/lib/cliente-documento-tipo.ts` module so both pages stay in sync (this is the explicit reason CONTEXT.md calls for extracting the shared module now, ahead of the Phase 75 page unification).

---

### `web/src/lib/cliente-documento-tipo.ts` (new file — utility, transform)

**Analog:** `web/src/lib/prazos.ts` (full file, 30 lines, read above) — this is the closest existing precedent: a small pure-function module keyed off a domain enum, explicitly documented as "single source of truth" (`Fonte única de verdade`), consumed by multiple UI call sites (list + detail pages) exactly like `documento_tipo` will be consumed by `novo/page.tsx` + `[id]/editar/page.tsx`.

**Structural pattern to copy** (`web/src/lib/prazos.ts`, full file):
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
```

**Secondary analog for the fallback-table style** — `web/src/lib/permissions.ts` (lines 16-25) shows the project's convention for a `Record<Key, Value[]>` lookup table plus a small resolver function, useful if `getDocumentoTipoOptions` needs a lookup table keyed by `TipoCliente`:
```typescript
const ACTION_FALLBACKS: Record<PermissionAction, PermissionAction[]> = {
  view: ["view", "edit", "manage", "create"],
  create: ["create", "edit", "manage"],
  edit: ["edit", "manage"],
  manage: ["manage"],
};

export function resolveScopedPermissions(scope: string, action: PermissionAction) {
  return ACTION_FALLBACKS[action].map((candidate) => `${scope}:${candidate}`);
}
```

**Existing type to import** (`web/src/types/clientes.ts` line 1, needs `BI` added and `NIF` removed by this phase):
```typescript
export type DocumentoTipo = "NIF" | "CNI" | "PASSAPORTE" | "REG_COMERCIAL";
```

**Recommended shape for the new module** (combining both analog patterns — a `Record<TipoCliente, {value, label}[]>` table + a `getDocumentoTipoOptions` accessor + the relocated `toDocumentoTipo` type guard, replacing the duplicated versions currently in both page files):
```typescript
import type { DocumentoTipo } from "@/types/clientes";

export interface DocumentoTipoOption {
  value: DocumentoTipo;
  label: string;
}

const OPTIONS_BY_TIPO: Record<"PARTICULAR" | "EMPRESA", DocumentoTipoOption[]> = {
  PARTICULAR: [
    { value: "CNI", label: "CNI" },
    { value: "BI", label: "BI" },
    { value: "PASSAPORTE", label: "Passaporte" },
  ],
  EMPRESA: [{ value: "REG_COMERCIAL", label: "Registo Comercial" }],
};

export function getDocumentoTipoOptions(
  tipo: "PARTICULAR" | "EMPRESA" | undefined,
): DocumentoTipoOption[] {
  if (!tipo) return [];
  return OPTIONS_BY_TIPO[tipo];
}

export function toDocumentoTipo(
  value: string | undefined,
  tipo: "PARTICULAR" | "EMPRESA" | undefined,
): DocumentoTipo | undefined {
  const allowed = getDocumentoTipoOptions(tipo).map((o) => o.value);
  return allowed.includes(value as DocumentoTipo) ? (value as DocumentoTipo) : undefined;
}
```

---

## Shared Patterns

### Ad-hoc `BAD_REQUEST` validation with `{"message": "..."}` body
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, consistently at lines 559-561, 649-651, 719-724, 927-929, 1409-1411, 1721-1722, 1730-1731, 1736-1737, 1825-1826, 1861-1862, 1878-1880, 1925-1926, 2075, 2209-2211.
**Apply to:** the new tipo/documentoTipo combination check in `createCliente` and `updateCliente`.
```java
return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("message", "<mensagem em português>"));
```

### Zod cross-field validation via `superRefine`
**Source:** `web/src/schemas/clientes.ts` lines 49-67 (existing, to be edited not replaced).
**Apply to:** the new `documento_tipo` × `tipo` combination check.
```typescript
ctx.addIssue({
  code: z.ZodIssueCode.custom,
  message: "<mensagem>",
  path: ["documento_tipo"],
});
```

### Type-switch confirmation Dialog (Particular↔Empresa)
**Source:** `novo/page.tsx` lines 337-352 and `[id]/editar/page.tsx` lines 683-698 (identical), driven by `pendingTipo` state + `onTipoChange`/`confirmTipoChange` handlers (lines 68-81 / 97-110 respectively).
**Apply to:** both page files — `confirmTipoChange` must be extended to also clear `documento_tipo`/`documento_numero` via `form.setValue(...)` when the previously-selected value is no longer valid for the new tipo (use `getDocumentoTipoOptions` from the new shared module to check validity).

### Shared frontend lookup-table module keyed by domain enum
**Source:** `web/src/lib/prazos.ts` (full file) and `web/src/lib/permissions.ts` lines 16-25.
**Apply to:** new `web/src/lib/cliente-documento-tipo.ts`.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| Backend migration/cleanup script for `documento_tipo = 'NIF'` rows | migration | batch | No SQL migration mechanism exists in this repo — no Flyway/Liquibase dependency, no `data.sql`, no `backend/scripts/` directory (confirmed via glob: zero `.sql` files under `backend/`). `hibernate.ddl-auto=update` is the only schema-management mechanism (`backend/src/main/resources/application.yml` line 19). `DatabaseSeeder.java` (`backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java`) is a `CommandLineRunner` used for RBAC/user seeding, not data cleanup, and is gated by `SEED_ENABLED` — reusing it would tie a one-time defensive cleanup to a flag meant for demo/dev seeding, which does not fit. **Recommendation for planner:** since there is no established pattern, the defensive `UPDATE t_cliente SET documento_tipo = NULL, documento_numero = NULL WHERE documento_tipo = 'NIF'` cleanup should run as a standalone, clearly-named one-off SQL script (e.g. `backend/migrations/74-cleanup-nif-documento-tipo.sql`) documented in the plan with explicit manual-execution instructions (psql/DBeaver), since no automated migration runner exists to pick it up. This must run **before** deploying the code change that removes `NIF` from the Java enum, per the sequencing already specified in CONTEXT.md. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/models/`, `backend/src/main/java/com/lexcv/controllers/`, `backend/src/main/resources/`, `web/src/app/(dashboard)/clientes/`, `web/src/schemas/`, `web/src/lib/`, `web/src/types/`
**Files scanned:** ~15 (DocumentoTipo.java, TipoCliente.java, Cliente.java, ResourceController.java, clientes.ts schema, novo/page.tsx, [id]/editar/page.tsx, types/clientes.ts, prazos.ts, permissions.ts, application.yml, DatabaseSeeder.java, plus glob searches across backend/web trees)
**Pattern extraction date:** 2026-07-03
