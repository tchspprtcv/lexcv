# Phase 71: Frontend Types, Schema & API Integration - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 2 (both existing files to be modified, no new files)
**Analogs found:** 2 / 2 (self-analogous — closest pattern for each file is an established sibling pattern within the SAME file or a sibling schema file)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|-----------------|---------------|
| `web/src/types/clientes.ts` | model (TS type defs, no runtime) | CRUD (request/response DTOs) | `web/src/types/clientes.ts` itself (flatten in place) + sibling `documento_tipo` string usage already flat elsewhere in the file | in-file refactor |
| `web/src/schemas/clientes.ts` | utility (Zod validation schema for react-hook-form) | request-response (form submit validation) | `web/src/schemas/setup.ts` (regex-based mandatory field validation) + `clienteFormSchema`'s own existing NIF-digit-check block (lines 68-77) | role-match (setup.ts) / exact (existing NIF logic in same file) |

**Important scoping note:** This phase touches only `web/src/types/clientes.ts` and `web/src/schemas/clientes.ts`. The consumers of these files — `web/src/app/(dashboard)/clientes/novo/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`, and `web/src/app/(dashboard)/clientes/page.tsx` — all currently reference `dados_tipo`/`DadosTipoParticular`/`DadosTipoEmpresa` and will **break at compile time** once this phase lands. Per CONTEXT.md, adapting those forms is explicitly out of scope (Phase 72 for create/edit forms, Phase 73 for the ficha/detail page). The planner should note this as an expected, acceptable transient break (or, at minimum, flag it) rather than attempt to fix the consumers in this phase. `web/src/hooks/use-clientes.ts` does NOT reference `dados_tipo` anywhere and needs no changes.

## Pattern Assignments

### `web/src/types/clientes.ts` (model, CRUD DTOs)

**Analog:** itself — apply the flattening pattern already partially present (the file already has both snake_case and camelCase flat fields like `documento_tipo`/`documentoTipo`, `ramo_atividade`/`ramoAtividade` sitting side by side; the same dual-casing convention should be preserved, just without a JSON-blob nested type).

**Current `dados_tipo`-related types to REMOVE** (lines 1-12):
```typescript
export interface DadosTipoParticular {
  idade?: number;
  sexo?: string;
  nacionalidade?: string;
}

export interface DadosTipoEmpresa {
  nome_comercial?: string;
  sede?: string;
  representante_legal?: string;
  cargo?: string;
}
```

**Current `dados_tipo` field references to REMOVE** (3 occurrences):
```typescript
// Cliente interface, line 49
dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;

// ClienteCreateRequest interface, line 80
dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;

// ClienteUpdateRequest interface, line 101
dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;
```

**Loose fields that were the "flattened" siblings of `DadosTipoParticular`, already present directly on `Cliente`** (lines 72-74) — decide whether to keep these as the flat replacement or drop them; CONTEXT.md gives Claude discretion here:
```typescript
idade?: number;
sexo?: string;
nacionalidade?: string;
```
Note: there is no equivalent flat sibling for `DadosTipoEmpresa`'s fields (`nome_comercial`, `sede`, `representante_legal`, `cargo`) anywhere else in the file — these were only ever accessible via `dados_tipo`. If Empresa-specific fields are still needed (nome_comercial etc.), they must be added as new flat optional fields on `Cliente`/`ClienteCreateRequest`/`ClienteUpdateRequest`, OR dropped entirely if out of scope for CLI-06 (CONTEXT.md says CLI-06 is just "remover `dados_tipo` dos tipos TS" — doesn't mandate preserving those Empresa sub-fields).

**`documento_tipo` union to ADD** — currently `documento_tipo?: string` / `documentoTipo?: string` (untyped, lines 57, 61, 88, 92, 109, 113). Backend `DocumentoTipo` enum (from Phase 70 SUMMARY) is now `NIF | CNI | PASSAPORTE | REG_COMERCIAL`. The UI `<select>` in `novo/page.tsx` (lines 365-368) currently only offers `NIF`, `CNI`, `PASSAPORTE` as plain strings with no shared type — this is exactly the gap CLI-06 asks to close:
```typescript
// BEFORE (current, all 6 occurrences across Cliente/ClienteCreateRequest/ClienteUpdateRequest):
documento_tipo?: string;
documentoTipo?: string;

// Recommended AFTER — introduce a shared union type and reuse it everywhere:
export type DocumentoTipo = "NIF" | "CNI" | "PASSAPORTE" | "REG_COMERCIAL";
// ... then in each interface:
documento_tipo?: DocumentoTipo;
documentoTipo?: DocumentoTipo;
```

**Existing dual-casing convention to preserve** (already established in the file, e.g. lines 57-64) — new/changed fields should follow this same snake_case + camelCase duplication pattern rather than picking one:
```typescript
documento_tipo?: string;
documento_numero?: string;
ramo_atividade?: string;
detalhes_adicionais?: string;
documentoTipo?: string;
documentoNumero?: string;
ramoAtividade?: string;
detalhesAdicionais?: string;
```

**`nif` field — currently optional, needs to become required** (per CLI-05). Current declarations (3 occurrences, `Cliente` line 51, `ClienteCreateRequest` line 82, `ClienteUpdateRequest` line 103):
```typescript
nif?: string;
```
CLI-05 requires NIF mandatory for both Particular and Empresa at the **schema/validation** level (Zod). Whether the TS *type* itself should drop the `?` on `Cliente`/`ClienteCreateRequest` is a judgment call — `ClienteUpdateRequest` fields are conventionally partial/optional for PATCH-style updates in this codebase (see `nome?: string` staying optional there while `ClienteCreateRequest.nome` is required — compare line 81 `nome: string` (required in Create) vs line 102 `nome?: string` (optional in Update)). Recommended: make `nif: string` required (non-optional) in `Cliente` and `ClienteCreateRequest` (mirroring how `nome` is handled), keep `nif?: string` optional in `ClienteUpdateRequest` for partial-update semantics.

---

### `web/src/schemas/clientes.ts` (utility, Zod schema, request-response validation)

**Analog for "mandatory field with digit-count regex validation":** `web/src/schemas/setup.ts` (lines 3-4, 10-15) — this is the closest established pattern in the codebase for "required string field validated against a regex with a Portuguese error message":
```typescript
export const strongPasswordPattern =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;

adminPassword: z
  .string()
  .regex(
    strongPasswordPattern,
    "A password deve ter 8+ caracteres, maiúscula, minúscula, número e símbolo.",
  ),
```
Apply the same top-level named-pattern-constant + `.regex()` approach for NIF, e.g. `export const nifPattern = /^\d{9}$/;` then `nif: z.string().trim().regex(nifPattern, "NIF deve conter exatamente 9 dígitos numéricos")`. This is preferable to the current codebase's own ad-hoc `superRefine` digit-check (below) because it's simpler for an unconditionally-required field (no longer needs conditional "if type is NIF" branching since CLI-05 makes NIF mandatory regardless of `documento_tipo`).

**Current in-file NIF validation logic to be SUPERSEDED** (lines 60, 68-77) — this is the existing `superRefine` block that conditionally validates 9-digit NIF only when `documento_tipo === "NIF"`; CLI-05 makes NIF unconditionally required, so this conditional branch should be replaced by a direct required-field regex on the top-level `nif` field:
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
    ...
```
Note this existing logic validates `documento_numero` (the generic document-number field), not the dedicated `nif` field — CLI-05 is about the `nif` field specifically being mandatory+validated for ALL client types, independent of what `documento_tipo`/`documento_numero` holds. Keep the `documento_tipo`/`documento_numero` pairing-requirement check (first `if` block) since that's an orthogonal, still-valid rule; replace/extend only the NIF-specific digit check.

**`dados_tipo` object schema to REMOVE entirely** (lines 22-34):
```typescript
dados_tipo: z
  .object({
    // Particular
    idade: z.number().int().positive().optional(),
    sexo: optionalTrimmedString,
    nacionalidade: optionalTrimmedString,
    // Empresa
    nome_comercial: optionalTrimmedString,
    sede: optionalTrimmedString,
    representante_legal: optionalTrimmedString,
    cargo: optionalTrimmedString,
  })
  .optional(),
```

**`dados_tipo`-dependent `superRefine` EMPRESA validation to REMOVE or migrate** (lines 78-93) — this currently validates that Empresa clients have `nome_comercial` and `representante_legal` inside `dados_tipo`. Since CLI-06 removes `dados_tipo`, this block must either be deleted (if those Empresa sub-fields are dropped per CONTEXT.md's "Claude's Discretion") or rewritten to validate flat top-level fields (if those fields are preserved flat in the type):
```typescript
if (data.tipo === "EMPRESA") {
  if (!data.dados_tipo?.nome_comercial?.trim()) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Nome comercial é obrigatório para Empresa",
      path: ["dados_tipo", "nome_comercial"],
    });
  }
  if (!data.dados_tipo?.representante_legal?.trim()) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Representante legal é obrigatório para Empresa",
      path: ["dados_tipo", "representante_legal"],
    });
  }
}
```

**Reusable helper pattern already established in this file** (lines 3-14) — keep using these for the new mandatory NIF field's `.trim()` behavior, though NIF should NOT use `optionalTrimmedString` since it must become required:
```typescript
const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();

const optionalEmail = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .refine((v) => !v || z.string().email().safeParse(v).success, "Email inválido")
  .optional();
```

**`superRefine` structure pattern** (also seen in `web/src/schemas/processos.ts` line 64 and `web/src/schemas/eventos.ts` line 37) — this codebase consistently uses `.superRefine((data, ctx) => { ctx.addIssue({ code: z.ZodIssueCode.custom, message, path }) })` for cross-field/conditional validation rather than `.refine()`. Keep this convention if any conditional logic remains after removing the `dados_tipo` branches.

---

## Shared Patterns

### Regex-based mandatory field validation
**Source:** `web/src/schemas/setup.ts` lines 3-4, 10-15
**Apply to:** `nif` field in `clienteFormSchema`
```typescript
export const strongPasswordPattern =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
// pattern: export a named regex constant, then z.string().regex(pattern, "<PT error message>")
```

### Portuguese error messages
**Source:** `web/src/schemas/clientes.ts` (existing), `web/src/schemas/setup.ts`
**Apply to:** Any new Zod `.regex()`/`ctx.addIssue()` messages — all validation messages in this codebase are in Portuguese, matching CLAUDE.md's domain-language convention (e.g. "NIF de Cabo Verde deve ter exatamente 9 dígitos", "O nome é obrigatório").

### Dual snake_case/camelCase field duplication
**Source:** `web/src/types/clientes.ts` lines 57-64, 88-95, 109-116 (existing, pre-Phase-71 pattern)
**Apply to:** Any new/modified fields in `Cliente`, `ClienteCreateRequest`, `ClienteUpdateRequest` — the backend apparently serializes both cases (likely Jackson `@JsonProperty` aliasing per field), so new fields like the `DocumentoTipo` union should keep both `documento_tipo`/`documentoTipo` variants rather than picking one.

## No Analog Found

None — both target files are pre-existing files being edited in place; there is no "new file with zero prior art" scenario in this phase. The `superRefine`-based conditional cross-field validation and the `.regex()`-based mandatory-field validation both have direct precedent within the same file or a sibling schema file.

## Metadata

**Analog search scope:** `web/src/schemas/*.ts` (7 files), `web/src/types/clientes.ts`, `web/src/hooks/use-clientes.ts`, `web/src/app/(dashboard)/clientes/**/*.tsx` (5 consumer pages)
**Files scanned:** ~10
**Pattern extraction date:** 2026-07-01
