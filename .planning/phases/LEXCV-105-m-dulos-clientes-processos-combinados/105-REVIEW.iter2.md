---
phase: LEXCV-105-m-dulos-clientes-processos-combinados
reviewed: 2026-07-16T12:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/app/(dashboard)/clientes/merge/page.tsx
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx
  - web/src/app/(dashboard)/processos/[id]/editar/page.tsx
  - web/src/app/(dashboard)/processos/[id]/page.tsx
  - web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx
  - web/src/app/(dashboard)/processos/novo/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
findings:
  critical: 1
  warning: 3
  info: 3
  total: 7
status: issues_found
---

# Phase 105: Code Review Report

**Reviewed:** 2026-07-16T12:00:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed the Clientes+Processos shadcn migration (manual toggle-buttons → `Tabs`, native `<select>` → `NativeSelect`, ad-hoc breadcrumb divs → `Breadcrumb`, `Avatar` for advogados/administrativos/testemunhas, Partes/Fases/Testemunhas → reconciled `Table` primitives, Processo's Documentos tab → shared `DataTable`/`documentos-columns.tsx`).

**Orchestrator-requested verification — both confirmed correctly and completely applied:**

1. **Processo ficha tab-bar mobile wrap.** `processos/[id]/page.tsx:1268` now reads `<TabsList variant="default" className="h-auto w-full flex-wrap">` — `cn()` uses `tailwind-merge`, so `h-auto`/`w-full`/`flex-wrap` correctly override the primitive's default `w-fit`/`h-9` (`web/src/components/ui/tabs.tsx:28`). No stray wrapper div remains. Confirmed working as intended.
2. **`permissions.isFetched` RBAC race fix.** All 10 page-level guards across the reviewed files (`clientes/page.tsx:30`, `clientes/[id]/page.tsx:130`, `clientes/[id]/ficha/page.tsx:50`, `clientes/novo/page.tsx:119`, `clientes/merge/page.tsx:34`, `processos/page.tsx:25`, `processos/[id]/page.tsx:218`, `processos/[id]/editar/page.tsx:42`, `processos/[id]/termo-honorarios/page.tsx:55`, `processos/novo/page.tsx:51`) consistently use `permissions.isFetched && !canX` instead of the old `!permissions.isLoading && !canX`. Confirmed applied uniformly. (Note: one narrower residual instance of the same race class remains inside nested tab content — see WR-02 below.)

One data-loss-capable bug (pre-existing, not introduced by this migration but present in a file under review — see CR-01) and a widespread visual regression newly introduced by the `NativeSelect` migration (WR-01) are the most actionable findings.

## Critical Issues

### CR-01: Cancelling an edit after a tipo change silently clears a legacy `documento_tipo` on the next save

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:348-361` (the `onCancel` handler), in conjunction with `confirmTipoChange` (lines 204-214) and the load-time effect (lines 273-291)

**Issue:** When a cliente was loaded with a legacy `documento_tipo` that is invalid for its current `tipo` (e.g. from data predating the `74-cleanup-nif-documento-tipo.sql` migration), the component tracks that in `legacyDocumentoTipo` state so the schema (`buildClienteFormSchema(legacyDocumentoTipo)`) exempts exactly that value from its "valid option" check — this is explicitly what the surrounding code comments (lines 278-289, 310-313) say they are protecting against: *"an unrelated save would then clear the field as a side effect."*

That protection has a gap:
1. Cliente loads with an invalid legacy combo → `legacyDocumentoTipo` = `"XPTO"` (say).
2. User clicks **Editar**, changes **Tipo de Cliente** (radio) → `onTipoChange` opens the "Mudar tipo de cliente" confirm dialog.
3. User confirms → `confirmTipoChange` detects the combo is invalid for the new tipo and calls `setLegacyDocumentoTipo(null)` (line 210), plus clears the form's `documento_tipo`/`documento_numero`.
4. User clicks the top-level **Cancelar** button → `onCancel` calls `form.reset(buildDefaultValues(cliente.data))`, which correctly restores `tipo`/`documento_tipo` back to the original loaded values in the **form** — but never calls `setLegacyDocumentoTipo(...)` to restore the flag. `legacyDocumentoTipo` stays `null`.
5. User clicks **Editar** again and **Guardar** without touching `documento_tipo`. The `<select>` for `documento_tipo` no longer renders an `<option value="XPTO">` (the `{legacyDocumentoTipo ? <option .../> : null}` block at line 577 is now skipped), so the browser has no matching option for the registered value — and `clienteFormSchema` (rebuilt from `legacyDocumentoTipo === null` via the `useMemo` at line 164) no longer exempts `"XPTO"` either. The field's value is silently lost/blocked on a save the user believed only concerned unrelated fields.

**Fix:** Extract the legacy-detection logic into a small helper and call it from both the load effect and `onCancel`:
```tsx
const computeLegacyDocumentoTipo = React.useCallback((data: Cliente) => {
  const loadedTipo = (data.tipo as "PARTICULAR" | "EMPRESA" | undefined) ?? undefined;
  const loadedDocumentoTipo = data.documento_tipo ?? data.documentoTipo ?? "";
  const isValidCombo =
    !loadedDocumentoTipo ||
    getDocumentoTipoOptions(loadedTipo).some((opt) => opt.value === loadedDocumentoTipo);
  return isValidCombo ? null : loadedDocumentoTipo;
}, []);

// in the load effect:
setLegacyDocumentoTipo(computeLegacyDocumentoTipo(cliente.data));

// in onCancel:
const onCancel = () => {
  if (cliente.data) {
    form.reset(buildDefaultValues(cliente.data));
    setLegacyDocumentoTipo(computeLegacyDocumentoTipo(cliente.data)); // <-- add this
    setDocumentosATratar(cliente.data.documentos_a_tratar ?? []);
    setDeslocacoes(cliente.data.deslocacoes ?? []);
  }
  ...
};
```

## Warnings

### WR-01: `NativeSelect` migration dropped `w-full` on 16 of 23 converted selects — selects now shrink to fit instead of stretching to the field width

**Files/lines:**
- `clientes/page.tsx:344, 358` (Tipo, Estado filters)
- `clientes/novo/page.tsx:255, 290` (Tipo de Documento, Ramo de Atividade)
- `clientes/[id]/page.tsx:566, 698, 1693, 1816, 1855` (Tipo de Documento, Ramo de Atividade, "Adicionar a {title}" utilizador select, contacto tipo x2)
- `clientes/merge/page.tsx:114, 130` (Cliente principal, Cliente duplicado)
- `processos/[id]/page.tsx:1213, 1226, 1725, 1802, 2125, 2413` (Prioridade, Responsável, fase status, decisão tipo, testemunha tipo, reatribuir responsável)

**Issue:** `NativeSelect`'s wrapper `<div>` defaults to `w-fit` (`web/src/components/ui/native-select.tsx:18`), unlike `Input`, which is `w-full` unconditionally by default (`web/src/components/ui/input.tsx:11`). Every pre-migration `<select className="...w-full...">` in this codebase had `w-full` baked into its inline class string; the migration replaced that string with `size="default"` but, at the 16 call sites above, never re-added `className="w-full"`. The result: these dropdowns now render at their shrink-to-fit content width instead of stretching to fill their grid/flex column, unlike the `Input` siblings they sit next to in the same form rows (e.g. `clientes/page.tsx`'s advanced-filter grid, `clientes/[id]/page.tsx`'s "Identificação" two-column grid). This is a real, easily reproducible visual regression, not a hypothetical one.

Contrast with the 7 call sites that got it right: `processos/page.tsx:236, 282`, `processos/novo/page.tsx:302, 328, 352, 560`, `processos/[id]/editar/page.tsx:160` all correctly append `className="w-full"` (or `className="w-full"` alongside other classes). The inconsistency shows the pattern was known but not applied uniformly.

**Fix:** Add `className="w-full"` (or merge it into an existing `className` prop) at each of the 16 sites listed above, e.g.:
```tsx
<NativeSelect id="documento_tipo" size="default" className="w-full" {...form.register("documento_tipo")}>
```

### WR-02: Nested-tab RBAC gates in Cliente ficha are not guarded by `permissions.isFetched` — same race class as the fixed page-level guards, one level deeper

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:856-881` (`canViewProcessos`, `canViewPareceres`, `canViewDocumentos` used inside `TabsContent` to choose between the real tab and `<AccessDeniedState />`)

**Issue:** The page-level guard at line 130 was correctly fixed to `permissions.isFetched && !canViewClientes`. However, `canViewProcessos`/`canViewPareceres`/`canViewDocumentos` (computed again inside `ClienteDetailContent`, lines 144-146) are used directly, without an `isFetched` check, to decide whether the Processos/Pareceres/Documentos Entregues tab content shows real data or `<AccessDeniedState>`. Before `permissions` has resolved, `can.view(...)` resolves to `false` for everyone (empty permissions array), so a user who switches to one of these tabs before the permissions query settles would see a brief "Acesso negado" flash that self-corrects once permissions load — the exact bug class already fixed at the page level, just nested one level down inside the tab content. In practice this window is narrow (cliente data and permissions typically resolve together), but it's the same defect the orchestrator's fix targeted and it wasn't covered here.

**Fix:** Gate these the same way the page-level checks now do, e.g.:
```tsx
<TabsContent value="processos">
  {!permissions.isFetched ? (
    <div className="p-6 text-sm text-neutral-500">A carregar...</div>
  ) : canViewProcessos ? (
    <ClienteProcessosTab clienteId={id} />
  ) : (
    <AccessDeniedState description="Não tem permissão para consultar os processos deste cliente." />
  )}
</TabsContent>
```
(repeat for the Pareceres and Documentos Entregues tabs).

### WR-03: CSV cliente import blindly casts the `tipo` column to the enum type, bypassing the same validation the manual form enforces

**File:** `web/src/app/(dashboard)/clientes/page.tsx:179-181`

**Issue:**
```tsx
tipo: idxTipo >= 0
  ? ((r[idxTipo] ?? "").trim() || undefined) as "PARTICULAR" | "EMPRESA" | undefined
  : undefined,
```
Any string in the CSV's `tipo` column (`"Empresa"`, `"individual"`, `"xyz"`, ...) is force-cast to `"PARTICULAR" | "EMPRESA" | undefined` with no runtime check, unlike `clientes/novo/page.tsx`'s manual form, which validates `tipo` through `z.enum(["PARTICULAR", "EMPRESA"])` (`schemas/clientes.ts:34-36`) before submission. Malformed values are sent straight to `createCliente.mutateAsync`, relying entirely on the backend to reject them (caught generically as an "erro desconhecido" import failure, with no specific "tipo inválido" feedback to the importer).

**Fix:**
```tsx
const rawTipo = idxTipo >= 0 ? (r[idxTipo] ?? "").trim().toUpperCase() : "";
const tipo = rawTipo === "PARTICULAR" || rawTipo === "EMPRESA" ? rawTipo : undefined;
if (idxTipo >= 0 && rawTipo && !tipo) {
  failed++;
  failureReasons.push(`linha ${i + 1}: tipo inválido ("${rawTipo}")`);
  continue;
}
```

## Info

### IN-01: Documento wire-shape workaround (`tamanho`/`createdAt`) duplicated across two files

**Files:** `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx:38-48` (`wireSizeAndDate`) and `web/src/app/(dashboard)/clientes/[id]/page.tsx:1398-1405` (inline in `ClienteDocumentoEntregueRow`)

**Issue:** Both independently re-derive `{ tamanho, createdAt }` from the same `Documento as unknown as {...}` cast, with near-identical comments explaining the same backend/DTO field-naming mismatch. New Documentos tabs added later would likely re-duplicate this a third time.

**Fix:** Extract a single `getDocumentoWireFields(documento: Documento)` helper into e.g. `web/src/lib/documento-wire.ts` and import it from both call sites.

### IN-02: `deriveInitials` duplicated three times

**Files:** `clientes/[id]/page.tsx:111-119`, `processos/[id]/page.tsx:194-202` (identical function bodies), plus an inline third copy of the same logic in `clientes/page.tsx:425` (mobile card initials).

**Fix:** Move to a shared `web/src/lib/utils.ts` (or a new `lib/initials.ts`) export and import from all three call sites.

### IN-03: `ProcessoDocumentosTab`'s `columns(canEditDocumentos)` is not memoized, unlike its sibling list pages

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:2652`

**Issue:** `<DataTable columns={columns(canEditDocumentos)} data={documentos} getRowId={(d) => d.id} />` calls the `columns()` factory inline on every render (e.g. on every upload-progress tick via `setProgresso`), creating a new array/object reference each time. `clientes/page.tsx:65` (`React.useMemo(() => columns(canEditClientes), [canEditClientes])`) and `processos/page.tsx:60` (`React.useMemo(() => columns(clienteNomeById), [clienteNomeById])`) both memoize equivalent factories. Not a functional bug here (sorting/visibility state lives in `DataTable`'s own `useState`, so it survives), but it's an inconsistency with the established pattern from the same phase/PR.

**Fix:**
```tsx
const documentoColumns = React.useMemo(() => columns(canEditDocumentos), [canEditDocumentos]);
...
<DataTable columns={documentoColumns} data={documentos} getRowId={(d) => d.id} />
```

---

_Reviewed: 2026-07-16T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
