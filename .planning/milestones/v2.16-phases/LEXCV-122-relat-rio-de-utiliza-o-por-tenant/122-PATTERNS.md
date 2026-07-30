# Phase 122: Relatório de Utilização por Tenant - Pattern Map

**Mapped:** 2026-07-30
**Files analyzed:** 16 read directly (3 backend, 13 frontend), plus a structural grep across all 6 existing `columns.tsx` files in the codebase
**Analogs found:** 4 / 4 (1 partial — see "No Analog Found" for the plain-array columns shape)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/app/(dashboard)/plataforma/relatorio/page.tsx` (new) | component (route) | read (query-only, zero mutations) | `plataforma/page.tsx` (guard + Card/search/DataTable/mobile shape, trimmed) **+** `processos/dashboard/page.tsx` (satellite back-arrow header shape) | role-match (composite of 2 analogs) |
| `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx` (new) | component (column defs) | transform | `plataforma/columns.tsx` | partial (4 cell renderers exact/verbatim; the plain-array top-level shape has no precedent — see below) |
| `web/src/app/(dashboard)/plataforma/page.tsx` (modify — CardHeader only) | component (route) | navigation (add `<Link>`; zero change to existing CRUD/mutation paths) | `processos/page.tsx`'s "Dashboard" (outline) → "Novo Processo" (primary) two-button CardHeader | exact |
| `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` (modify — add 1 test) | test | regression proof | itself — `listTenants_devolve200ComUmResumoPorTenantComAContagemDoSeuProprioId` + `listTenants_nuncaDevolveEntidadesCruas` | exact |

**Reused verbatim, zero modification (confirmed by direct reading, not assumed):**
- `web/src/hooks/use-platform-admin.ts` → `useTenantsAdmin()` (lines 21-30) — pure `useQuery`, no mutation coupling, needs literally no change.
- `web/src/types/platform-admin.ts` → `TenantAdminSummary` type (lines 12-19).
- `web/src/lib/tenant-initials.ts` → `tenantInitials()` (full file, 19 lines).
- `web/src/components/shared/access-denied-state.tsx` → `AccessDeniedState` (full file, 42 lines; `{title?, description?, backHref?, backLabel?}` props already fit this screen's needs with no change).
- `web/src/components/shared/data-table/data-table.tsx` → `DataTable` (full file, 128 lines; its own doc-comment already lists itself as shared across 5 list screens — this phase becomes its 6th non-modifying consumer).
- `web/src/hooks/use-me.ts` → `useMe()` (full file, 16 lines).
- `web/src/components/ui/badge.tsx` → all Badge variants needed (`outline`, `gray`, `purple`, `amber`, `green`, `red`) already exist, confirmed unchanged since Phase 120 — zero new variants.
- `backend/.../controllers/PlatformAdminController.java` `listTenants()` (lines 106-113) / `toSummary()` (lines 191-200) — zero production backend changes; confirmed no `.filter(Tenant::getAtivo)` anywhere in the pipeline.
- `backend/.../dtos/TenantAdminSummaryResponse.java` (full file, 36 lines) — its own doc-comment already names Phase 122 by number as a reuser of exactly these 6 fields.

**Explicitly NOT modified (do not touch, even though precedent exists to do so):**
- `web/src/components/shared/dashboard-shell.tsx` — confirmed at lines 60/76/91-94 this file already has the exact nav-splice mechanism (`isPlatformAdmin ? [...NAV, platformNavItem] : NAV`) Phase 120 used to add `/plataforma` itself to the sidebar. **Do not repeat that mechanism for `/plataforma/relatorio`** — 122-CONTEXT.md's decision and the Phase 89 (v2.10) precedent both require this screen be reached only via an in-context link from `/plataforma`, never a second permanent sidebar item.
- `web/src/components/shared/data-table/data-table.tsx`, `data-table-column-header.tsx` — consumed as-is, no prop/behavior changes needed.

---

## Pattern Assignments

### `web/src/app/(dashboard)/plataforma/relatorio/page.tsx` (new route, read-only)

Two existing files combine to cover this entire screen — nothing here is genuinely novel, it's a trim-and-recombine of two already-shipped shapes.

**Analog 1 — RBAC guard + Card/search/loading/error/DataTable/mobile shape: `web/src/app/(dashboard)/plataforma/page.tsx`**

Guard (lines 76-91, copy structure verbatim, swap only the `description` string):
```tsx
const me = useMe();

if (!me.isFetched) {
  return null;
}

if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
  return (
    <AccessDeniedState
      description="Não tem permissão para aceder à consola de administração de tenants."
      backHref="/dashboard"
    />
  );
}

return <PlataformaPageContent />;
```
New copy for `relatorio/page.tsx` (per UI-SPEC's locked Copywriting Contract): `description="Não tem permissão para aceder ao relatório de utilização de tenants."`, same `backHref="/dashboard"`. The `!me.isFetched` early-return-null branch is not optional boilerplate — it's the fix for a real bug this same codebase hit before (WR-03, Phase 120 code review, documented in the comment directly above this guard in the current file): without it, the page would fire `useTenantsAdmin()`'s `GET /platform/tenants` for any authenticated user during the window before `useMe()` first resolves, before the caller's role is even known.

Search-filter memo (lines 106-111, reuse byte-identical — same field, same data source):
```tsx
const tenantsFiltrados = React.useMemo(() => {
  const termo = searchTerm.trim().toLowerCase();
  const lista = tenants.data ?? [];
  if (!termo) return lista;
  return lista.filter((t) => t.nome.toLowerCase().includes(termo));
}, [tenants.data, searchTerm]);
```

Card/search/loading/error shape to trim from (lines 161-190) — note what to explicitly DROP versus the source: the `isFormOpen` ternary wrapper (no create-panel branch exists on a read-only screen — the Card renders unconditionally), the `CardHeader`'s `flex-row items-center justify-between` override (no button sits opposite the title here — use the bare default `<CardHeader>`), and the trailing `<Button>` inside it entirely:
```tsx
<Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
  <CardHeader>
    <CardTitle className="text-xl font-semibold">Utilização por Tenant</CardTitle>
    <CardDescription>Estado atual de todos os tenants registados na plataforma.</CardDescription>
  </CardHeader>
  <CardContent className="space-y-4">
    <Input
      placeholder="Pesquisar tenant por nome..."
      value={searchTerm}
      onChange={(e) => setSearchTerm(e.target.value)}
      className="bg-slate-50 dark:bg-slate-950"
    />
    {tenants.isLoading ? (
      <div className="p-6 text-sm text-slate-500">A carregar...</div>
    ) : tenants.isError ? (
      <div className="p-6 text-sm text-red-600">
        {tenants.error instanceof Error ? tenants.error.message : "Erro ao carregar tenants."}
      </div>
    ) : (
      <>{/* mobile cards / desktop DataTable split — see below */}</>
    )}
  </CardContent>
</Card>
```

Desktop DataTable call (lines 311-314 — `tenantColumns` swapped for the new static imported array; no `useMemo`/callbacks needed since `relatorioColumns` isn't a factory):
```tsx
<div className="hidden md:block">
  <DataTable columns={relatorioColumns} data={tenantsFiltrados} getRowId={(t) => t.id} />
</div>
```

Mobile-cards block (lines 191-309): reuse verbatim EXCEPT the action-icon row (lines 237-304, the two Tooltip-wrapped icon buttons) — UI-SPEC already fully specifies the replacement (the `utilizadores` line, `pl-[52px]`-indented, quoted in full in 122-UI-SPEC.md's "Mobile cards" section) so it is not re-derived here; the source for that replacement's two halves is the `utilizadores` cell in Analog 2 below (the "X/Y" / "limite atingido" logic) and this same file's own `pl-[52px]` indent convention (line 237, already used for the current action-icon row — same indent, different content).

**Analog 2 — satellite-screen header shape (back-arrow + h1 + subtitle, no breadcrumb): `web/src/app/(dashboard)/processos/dashboard/page.tsx` lines 29-39**
```tsx
<div className="flex items-center gap-4">
  <Button asChild variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-slate-900 dark:hover:text-white">
    <Link href="/processos">
      <ArrowLeft className="h-4 w-4" />
    </Link>
  </Button>
  <div>
    <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Dashboard de Processos</h1>
    <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">Acompanhamento operacional e indicadores executivos</p>
  </div>
</div>
```
UI-SPEC deviates from this exact precedent in 2 deliberate, already-locked ways — implement the deviations, don't silently "fix" them back to match the source: (1) `href="/plataforma"` with an explicit `aria-label="Voltar"` added to the `<Link>`'s wrapping `Button` — this precedent has none, a small accessibility improvement this codebase has made before (e.g. Phase 115.1's user-search clear button); (2) the `<h1>` uses `font-semibold` (600), not `font-bold` (700) — the one disclosed, orchestrator-force-approved Dimension-4 typography exception in the whole UI-SPEC (see its "Disclosed third weight" note). Do not "correct" this to font-bold — it's a recorded, deliberate one-off, not an oversight.

**Imports needed** (all pre-existing modules — zero new dependencies beyond the local `relatorioColumns` import): `"use client"`, `React`, `Link` (`next/link`), `ArrowLeft` (`lucide-react`), `AccessDeniedState`, `Badge`, `Button`, `Card`/`CardContent`/`CardDescription`/`CardHeader`/`CardTitle`, `Input`, `DataTable`, `useMe`, `useTenantsAdmin`, `tenantInitials`, `TENANT_RESERVADO` (imported from `../columns` — the existing exported constant at `plataforma/columns.tsx` line 18, not re-declared; needed for the mobile card's inline "Plataforma" badge check), `relatorioColumns` (from `./columns`).

---

### `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx` (new, transform)

**Analog:** `web/src/app/(dashboard)/plataforma/columns.tsx` (full file, 221 lines) — reuse 4 of its 5 column defs' cell renderers verbatim; drop the `acoes` column def and its backing `TenantAcoesCell` component (lines 32-107, 205-218) entirely; and restructure from a factory function to a plain static array, since a read-only screen has no row callbacks to receive.

**Structural change — factory function → plain array.** This is the one piece of this file with no direct precedent (see "No Analog Found" below). Current shape (`plataforma/columns.tsx` lines 114-120):
```tsx
export function columns({
  onEdit,
  onToggleAtivo,
}: {
  onEdit: (tenant: TenantAdminSummary) => void;
  onToggleAtivo: (tenant: TenantAdminSummary) => void;
}): ColumnDef<TenantAdminSummary>[] {
  return [ /* ... */ ];
}
```
New shape for `relatorio/columns.tsx`:
```tsx
export const relatorioColumns: ColumnDef<TenantAdminSummary>[] = [
  // same 4 column defs below, no factory wrapper, no callback params
];
```

**`nome` cell** — copied verbatim (lines 122-148):
```tsx
{
  id: "nome",
  accessorKey: "nome",
  enableHiding: false,
  meta: { label: "Nome" },
  header: ({ column }) => <DataTableColumnHeader column={column} title="Nome" />,
  cell: ({ row }) => {
    const tenant = row.original;
    const initials = tenantInitials(tenant.nome);

    return (
      <div className="flex items-center gap-3">
        <div className="h-10 w-10 rounded-md bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold shadow-sm">
          {initials}
        </div>
        <div className="min-w-0">
          <span className="font-bold text-slate-900 dark:text-white">{tenant.nome}</span>
          {tenant.nome === TENANT_RESERVADO ? (
            <div className="mt-1">
              <Badge variant="outline">Plataforma</Badge>
            </div>
          ) : null}
        </div>
      </div>
    );
  },
},
```
Plain text (`<span>`), not a `<Link>` — same as the source, since there is still no tenant detail page anywhere in this app.

**`plano` cell** — copied verbatim (lines 149-162), plus the `PLANO_BADGE_VARIANT` const it depends on (lines 20-24):
```tsx
const PLANO_BADGE_VARIANT: Record<TenantPlano, "gray" | "purple" | "amber"> = {
  STARTER: "gray",
  STANDARD: "purple",
  ENTERPRISE: "amber",
};
```
```tsx
{
  id: "plano",
  accessorKey: "plano",
  meta: { label: "Plano" },
  header: ({ column }) => <DataTableColumnHeader column={column} title="Plano" />,
  cell: ({ row }) => {
    const plano = row.original.plano;
    return (
      <Badge variant={PLANO_BADGE_VARIANT[plano]} className="font-bold tracking-wide">
        {plano}
      </Badge>
    );
  },
},
```
`PLANO_BADGE_VARIANT` is a private (non-exported) const in `../columns.tsx` — nothing to import, it must be re-declared byte-identical in the new file.

**`utilizadores` cell** — copied verbatim (lines 163-194). This is the column ROADMAP Success Criterion 2 hinges on: it renders `tenant.utilizadoresAtivos` straight off the API response with zero client-side recomputation, so there remains exactly one source of truth (`UserRepository.countByTenantIdAndAtivoTrue`) for this figure everywhere it's shown:
```tsx
{
  id: "utilizadores",
  accessorFn: (tenant) => tenant.utilizadoresAtivos,
  meta: { label: "Utilizadores" },
  header: ({ column }) => <DataTableColumnHeader column={column} title="Utilizadores" />,
  cell: ({ row }) => {
    const tenant = row.original;
    const atingiuLimite =
      tenant.limiteUtilizadores !== null && tenant.utilizadoresAtivos >= tenant.limiteUtilizadores;

    return (
      <div className="flex flex-col">
        <span
          className={
            atingiuLimite
              ? "font-semibold text-red-600 dark:text-red-400"
              : "text-slate-800 dark:text-slate-200"
          }
        >
          {tenant.limiteUtilizadores !== null
            ? `${tenant.utilizadoresAtivos}/${tenant.limiteUtilizadores}`
            : `${tenant.utilizadoresAtivos} · sem limite`}
        </span>
        {atingiuLimite ? (
          <span className="text-[12px] uppercase tracking-wide text-red-600 dark:text-red-400">
            limite atingido
          </span>
        ) : null}
      </div>
    );
  },
},
```

**`estado` cell** — copied verbatim (lines 195-204). This is the column that makes Success Criterion 3 concretely true: a suspended tenant keeps its row (no filter anywhere removes it, confirmed both server-side and in the client search-filter memo above) with this exact Badge reading "Suspenso":
```tsx
{
  id: "estado",
  accessorKey: "ativo",
  meta: { label: "Estado" },
  header: ({ column }) => <DataTableColumnHeader column={column} title="Estado" />,
  cell: ({ row }) => {
    const ativo = row.original.ativo;
    return <Badge variant={ativo ? "green" : "red"}>{ativo ? "Ativo" : "Suspenso"}</Badge>;
  },
},
```

**Dropped entirely:** the `acoes` column def (lines 205-218) and its backing `TenantAcoesCell` component (lines 32-107) — no row callbacks exist on a read-only screen, so `Button`/`Tooltip`/`TooltipContent`/`TooltipTrigger`/`Lock`/`Pencil`/`Unlock`/`cn` are not imported into the new file either (they'd be dead code).

**Import list for the new file:** `ColumnDef` (`@tanstack/react-table`), `Badge` (`@/components/ui/badge`), `DataTableColumnHeader` (`@/components/shared/data-table/data-table-column-header`), `tenantInitials` (`@/lib/tenant-initials`), `TenantAdminSummary`/`TenantPlano` (`@/types/platform-admin`), `TENANT_RESERVADO` (`../columns`).

---

### `web/src/app/(dashboard)/plataforma/page.tsx` (modify — CardHeader only)

**Analog:** `web/src/app/(dashboard)/processos/page.tsx`'s existing two-button CardHeader (lines 142-157) — the exact "outline secondary button, then accent-blue primary button, both equal height" ordering UI-SPEC directs this phase to copy:
```tsx
{canCreateProcessos ? (
  <div className="flex items-center gap-2">
    <Button asChild variant="outline" className="font-bold tracking-wide shadow-none border-slate-300 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-900">
      <Link href="/processos/dashboard">
        <LayoutDashboard className="h-4 w-4" />
        Dashboard
      </Link>
    </Button>
    <Button asChild className="font-bold tracking-wide shadow-none">
      <Link href="/processos/novo">
        <Plus className="h-4 w-4" />
        Novo Processo
      </Link>
    </Button>
  </div>
) : null}
```

**Current block to modify** (`plataforma/page.tsx` lines 162-174 — single button only, no wrapping two-button cluster yet):
```tsx
<CardHeader className="flex flex-row items-center justify-between space-y-0">
  <div>
    <CardTitle className="text-xl font-semibold">Tenants Registados</CardTitle>
    <CardDescription>Lista de organizações com acesso à plataforma LexCV.</CardDescription>
  </div>
  <Button
    onClick={() => setIsFormOpen(true)}
    className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
  >
    <Plus className="h-4 w-4" />
    Criar Tenant
  </Button>
</CardHeader>
```

**Target shape** (already fully specified by 122-UI-SPEC.md, lines 119-141 — quoted here for traceability against the current file's exact lines above):
```tsx
<CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0">
  <div>
    <CardTitle className="text-xl font-semibold">Tenants Registados</CardTitle>
    <CardDescription>Lista de organizações com acesso à plataforma LexCV.</CardDescription>
  </div>
  <div className="flex items-center gap-2">
    <Button asChild variant="outline" className="text-xs py-1.5 px-3 h-auto flex items-center gap-1.5">
      <Link href="/plataforma/relatorio">
        <FileChartColumn className="h-4 w-4" />
        Ver Relatório
      </Link>
    </Button>
    <Button
      onClick={() => setIsFormOpen(true)}
      className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
    >
      <Plus className="h-4 w-4" />
      Criar Tenant
    </Button>
  </div>
</CardHeader>
```
Three differences from a naive copy of the current block, all deliberate: the `CardHeader`'s own className gains `flex-wrap` + `gap-3` (a new wrap-on-narrow-viewport consideration — Phase 120 never needed this with only one button in the header); the previously-bare `<Button>` is now wrapped in a `<div className="flex items-center gap-2">` alongside the new button; "Ver Relatório" renders first (secondary-before-primary, matching the `processos/page.tsx` analog), "Criar Tenant" stays second, completely unchanged.

**Import edits.** Current import lines (file lines 3-4):
```tsx
import * as React from "react";
import { Lock, Pencil, Plus, Unlock } from "lucide-react";
```
No `next/link` import exists anywhere in this file today (confirmed — it has never navigated anywhere before, only toggled local state and opened Dialogs/AlertDialogs). Both edits needed:
```tsx
import * as React from "react";
import Link from "next/link";
import { FileChartColumn, Lock, Pencil, Plus, Unlock } from "lucide-react";
```
`FileChartColumn` is confirmed present in the installed `lucide-react` package (`web/node_modules/lucide-react/dist/lucide-react.d.ts`, exported at line 23486) — no version bump needed.

**Visibility condition:** "Ver Relatório" only needs to render inside the same branch that already conditionally renders the whole Card — the existing `{isFormOpen ? (<CriarTenantPanel .../>) : (<Card>...)}` ternary (line 154 in the current file) already gates this; no new condition to add, the new button is simply a second child of the CardHeader that's already conditionally shown.

---

### `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` (modify — add 1 regression test)

**Analog:** itself. Two existing tests in the file's own "Grupo A: comportamento de listTenants" section are the structural templates:

`listTenants_devolve200ComUmResumoPorTenantComAContagemDoSeuProprioId` (lines 259-282) — the 2-tenant-fixture arrange/act/assert shape to clone:
```java
@Test
void listTenants_devolve200ComUmResumoPorTenantComAContagemDoSeuProprioId() {
    UUID tenantAId = UUID.randomUUID();
    UUID tenantBId = UUID.randomUUID();
    Tenant tenantA = Tenant.builder().id(tenantAId).nome("Escritorio A").plano(TenantPlano.STARTER)
            .limiteUtilizadores(5).ativo(true).build();
    Tenant tenantB = Tenant.builder().id(tenantBId).nome("Escritorio B").plano(TenantPlano.STANDARD)
            .limiteUtilizadores(null).ativo(true).build();
    when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
    when(userRepository.countByTenantIdAndAtivoTrue(tenantAId)).thenReturn(3L);
    when(userRepository.countByTenantIdAndAtivoTrue(tenantBId)).thenReturn(7L);

    ResponseEntity<?> response = novoController().listTenants();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    @SuppressWarnings("unchecked")
    List<TenantAdminSummaryResponse> corpo = (List<TenantAdminSummaryResponse>) response.getBody();
    assertNotNull(corpo);
    assertEquals(2, corpo.size());
    TenantAdminSummaryResponse resumoA = corpo.stream().filter(r -> r.getId().equals(tenantAId)).findFirst().orElseThrow();
    TenantAdminSummaryResponse resumoB = corpo.stream().filter(r -> r.getId().equals(tenantBId)).findFirst().orElseThrow();
    assertEquals(3L, resumoA.getUtilizadoresAtivos());
    assertEquals(7L, resumoB.getUtilizadoresAtivos());
}
```
Confirmed by reading the full test file: every one of this class's existing `listTenants_*` fixtures builds tenants with `.ativo(true)` only — this is exactly the gap 122-CONTEXT.md identifies. The `.orElseThrow()`-as-assertion idiom already used at lines 278-279 (fail with a `NoSuchElementException` if the filtered stream is empty — an implicit "this ID must be present" assertion, stronger than asserting `corpo.size()` alone) is the exact idiom to reuse.

**Pattern-derived new test** (matches every existing naming/structure convention in this file — `listTenants_` prefix, Portuguese camelCase test name describing the outcome, `Escritorio X` fixture naming, same mock-stub shape):
```java
@Test
void listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta() {
    UUID tenantAtivoId = UUID.randomUUID();
    UUID tenantSuspensoId = UUID.randomUUID();
    Tenant tenantAtivo = Tenant.builder().id(tenantAtivoId).nome("Escritorio Ativo")
            .plano(TenantPlano.STARTER).ativo(true).build();
    Tenant tenantSuspenso = Tenant.builder().id(tenantSuspensoId).nome("Escritorio Suspenso")
            .plano(TenantPlano.STANDARD).ativo(false).build();
    when(tenantRepository.findAll()).thenReturn(List.of(tenantAtivo, tenantSuspenso));
    when(userRepository.countByTenantIdAndAtivoTrue(any())).thenReturn(0L);

    ResponseEntity<?> response = novoController().listTenants();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    @SuppressWarnings("unchecked")
    List<TenantAdminSummaryResponse> corpo = (List<TenantAdminSummaryResponse>) response.getBody();
    assertNotNull(corpo);
    assertEquals(2, corpo.size());
    TenantAdminSummaryResponse resumoSuspenso = corpo.stream()
            .filter(r -> r.getId().equals(tenantSuspensoId))
            .findFirst()
            .orElseThrow();
    assertEquals(false, resumoSuspenso.getAtivo());
}
```

**Placement:** append inside the existing `// ---- Grupo A: comportamento de listTenants/updateTenant/setTenantAtivo (Phase 120 Plan 02) ----` block, directly after `listTenants_devolveOrdenadoPorNomeCaseInsensitiveMesmoQuandoFindAllDevolveForaDeOrdem` closes at line 319 and before `updateTenant_comPlanoELimiteValidosDevolve200EGravaComAtivoInalterado` begins at line 322 — same section, same group (Group A: direct instantiation, no method-security proxy), since this is a behavioral test, not a Group B authorization test.

**No production code change is implied.** `listTenants()` (`PlatformAdminController.java` lines 106-113) has no `.filter(...)` in its stream pipeline today — the test is expected to pass immediately against the current implementation. It is a regression guard closing the one real gap 122-CONTEXT.md found, not a bugfix.

---

## Shared Patterns

### Two-layer RBAC enforcement (backend authoritative + frontend UX mirror)
**Source:** `PlatformAdminController`'s class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (unchanged, already covers `listTenants()` — no new endpoint added) + `plataforma/page.tsx`'s `useMe()` + `!me.isFetched` + role-check guard (lines 76-91).
**Apply to:** `plataforma/relatorio/page.tsx`'s own page guard — copy verbatim, swap only the `AccessDeniedState` description string per UI-SPEC's locked copy.

### Zero-mutation read hook reuse
**Source:** `useTenantsAdmin()` (`use-platform-admin.ts` lines 21-30) — already a pure `useQuery`, no coupling to any of the file's 3 mutation hooks.
**Apply to:** `plataforma/relatorio/page.tsx` imports and calls this hook completely unmodified — the one hook of four in `use-platform-admin.ts` this phase touches, needing zero changes.

### DataTable + mobile-card breakpoint split
**Source:** `plataforma/page.tsx` lines 191-316 (`md:hidden` stacked-card block / `hidden md:block` DataTable block).
**Apply to:** `plataforma/relatorio/page.tsx`, same breakpoint convention, same `getRowId={(t) => t.id}` prop, columns swapped for the new `relatorioColumns`.

### Client-side name-substring search filter
**Source:** `plataforma/page.tsx` lines 106-111 (`tenantsFiltrados` `useMemo`).
**Apply to:** `plataforma/relatorio/page.tsx`, byte-identical — same field (`nome`), same data source, same placeholder copy ("Pesquisar tenant por nome...").

### Satellite-screen header (back-arrow + h1 + subtitle, no breadcrumb)
**Source:** `processos/dashboard/page.tsx` lines 29-39.
**Apply to:** `plataforma/relatorio/page.tsx`'s header row, with UI-SPEC's 2 deliberate deviations (explicit `aria-label`; `<h1>` at `font-semibold` not `font-bold`).

### Secondary-then-primary two-button CardHeader ordering
**Source:** `processos/page.tsx` lines 142-157 ("Dashboard" outline → "Novo Processo" primary).
**Apply to:** `plataforma/page.tsx`'s CardHeader edit ("Ver Relatório" outline → "Criar Tenant" primary, unchanged).

### Badge variant reuse, zero new variants
**Source:** `web/src/components/ui/badge.tsx` (re-read this session, confirmed unchanged since Phase 120): `default | secondary | outline | blue | green | amber | red | purple | gray`.
**Apply to:** all 3 Badge usages in `relatorio/columns.tsx` (`outline` for "Plataforma"; `gray`/`purple`/`amber` for `plano`; `green`/`red` for `estado`).

### DTO/response reuse, zero backend production changes
**Source:** `TenantAdminSummaryResponse.java`'s own doc-comment (lines 11-23), which already names Phase 122 by number as a reuser of these exact 6 fields; `PlatformAdminController.listTenants()`/`toSummary()` (lines 106-113, 191-200), confirmed to include every tenant unconditionally.
**Apply to:** nothing to change in production backend code — only the new regression test proves this existing behavior.

### Explicitly NOT applicable: Tooltip + disabled `<span tabIndex={0}>` composition
**Source:** `plataforma/page.tsx`/`columns.tsx`'s disabled-Suspender-icon pattern (triggered when `tenant.nome === TENANT_RESERVADO && tenant.ativo`).
**Do NOT bring into `relatorio/columns.tsx` or `relatorio/page.tsx`:** there are no action buttons, disabled or otherwise, on a read-only report — importing `Tooltip`/`TooltipContent`/`TooltipTrigger`/`Lock`/`Unlock`/`cn` into either new file would be dead code with no call site.

### Explicitly NOT applicable: nav-splice mechanism
**Source:** `dashboard-shell.tsx` lines 60/76/91-94 (`isPlatformAdmin ? [...NAV, platformNavItem] : NAV`).
**Do NOT reuse for `/plataforma/relatorio`:** CONTEXT.md's decision and the Phase 89 (v2.10) precedent both require this screen be reached only via the new in-context link on `/plataforma`, never a second permanent sidebar entry — even though the mechanism to add one trivially exists and was used for `/plataforma` itself one phase ago.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx` — specifically its top-level export shape (`export const relatorioColumns: ColumnDef<...>[] = [...]`, a plain static array) | component (column defs) | transform | Confirmed by grepping all 6 existing `columns.tsx` files in the codebase (`clientes`, `processos`, `pareceres`, `financeiro`, `documentos`, `plataforma`): every single one exports a **factory function** (`export function columns(...)`), never a plain array — because every one of them needs at least one row-action callback or lookup map threaded in (`onEdit`/`onToggleAtivo`, `canEditClientes`, `clienteNomeById`, etc.). `relatorio/columns.tsx` is the first columns file in this codebase with zero such parameters, because it's the first fully read-only list screen. The individual cell renderers it's built from all have exact, verbatim analogs (see Pattern Assignments above) — only this outer "plain array, no factory wrapper" shape is a first-of-its-kind. Low risk (it's a strict simplification, dropping parameters rather than adding new structure), but flagged here rather than silently claimed as "exact," consistent with how 120-PATTERNS.md flagged its own first-of-its-kind (`TenantUpdateRequest.java`, a typed request DTO where every comparable endpoint used a raw `Map` instead). |

---

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/{plataforma,processos,clientes,financeiro,pareceres,documentos}`, `web/src/{components/shared,components/ui,hooks,types,lib}`, `backend/src/main/java/com/lexcv/{controllers,dtos}`, `backend/src/test/java/com/lexcv/controllers`
**Files read directly (full or targeted ranges):** `plataforma/page.tsx`, `plataforma/columns.tsx`, `use-platform-admin.ts`, `types/platform-admin.ts`, `processos/dashboard/page.tsx`, `processos/page.tsx` (CardHeader range), `tenant-initials.ts`, `access-denied-state.tsx`, `data-table/data-table.tsx`, `use-me.ts`, `components/ui/badge.tsx`, `financeiro/page.tsx` (CSV-export range, for the deferred/optional export idea only), `PlatformAdminController.java`, `TenantAdminSummaryResponse.java`, `PlatformAdminControllerTest.java`, plus a dependency-existence check against installed `lucide-react` typings for `FileChartColumn`.
**Verification checks performed:** `FileChartColumn` icon confirmed present in `web/node_modules/lucide-react/dist/lucide-react.d.ts`; all Badge variants this phase needs confirmed present and unchanged in `badge.tsx`; `dashboard-shell.tsx`'s nav-splice mechanism confirmed present (and confirmed as the thing NOT to reuse); no existing `plataforma/relatorio/` directory confirmed via directory listing (clean new-file territory, no naming collision).
**Pattern extraction date:** 2026-07-30
