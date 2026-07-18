# Phase 105: Módulos Clientes + Processos (combinados) - Pattern Map

**Mapped:** 2026-07-16
**Files analyzed:** 9 (8 modified + 1 new)
**Analogs found:** 9 / 9

This phase is a **pure migration inside existing files** — every target file already
exists and already implements the "old" version of the pattern (manual `Button`
tab bars, raw `<select>`, raw `<table>`, ad hoc `<div>` breadcrumb nav). There is no
new business logic. Consequently the most useful "analog" for most files is:
1. the shadcn primitive itself (`tabs.tsx`, `native-select.tsx`, `avatar.tsx`,
   `breadcrumb.tsx`, `table.tsx`) — defines the target shape, and
2. an **already-migrated sibling** elsewhere in the same codebase that proves the
   pattern compiles and renders correctly in this project (Phase 104's
   `documentos/columns.tsx` + shared `DataTable`, and — a very strong, previously
   unlisted find — `clientes/[id]/page.tsx`'s own `ClienteProcessosTab`/
   `ClienteParecerTab` sub-components, which **already** use the reconciled
   `Table`/`TableHeader`/`TableRow`/`TableHead`/`TableBody`/`TableCell` primitives
   today, in the very file this phase touches).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | component (detail page) | request-response (CRUD read + inline edit) | `web/src/app/(dashboard)/processos/[id]/page.tsx` (sibling, same-shape tab bar/header) + `web/src/components/ui/tabs.tsx`, `breadcrumb.tsx`, `native-select.tsx`, `avatar.tsx` | exact (primitives) / role-match (sibling page) |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | component (detail page) | request-response (CRUD read + inline edit) | `web/src/app/(dashboard)/clientes/[id]/page.tsx` (sibling) + same 4 primitives + clientes/[id]'s own `ClienteProcessosTab` (Table-primitive precedent) | exact (primitives) / role-match (sibling) |
| `web/src/app/(dashboard)/clientes/page.tsx` | component (list page) | request-response (filtered CRUD list) | `web/src/app/(dashboard)/processos/page.tsx` (sibling list filters) + `native-select.tsx` | role-match |
| `web/src/app/(dashboard)/processos/page.tsx` | component (list page) | request-response (filtered CRUD list) | `web/src/app/(dashboard)/clientes/page.tsx` (sibling list filters) + `native-select.tsx` | role-match |
| `web/src/app/(dashboard)/clientes/novo/page.tsx` | component (create form page) | request-response (CRUD create) | `web/src/app/(dashboard)/processos/novo/page.tsx` (sibling create form) + `breadcrumb.tsx`, `native-select.tsx` | role-match |
| `web/src/app/(dashboard)/processos/novo/page.tsx` | component (create form page) | request-response (CRUD create) | `web/src/app/(dashboard)/clientes/novo/page.tsx` (sibling) + `breadcrumb.tsx`, `native-select.tsx` | role-match |
| `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` | component (edit form page) | request-response (CRUD update) | `web/src/app/(dashboard)/processos/novo/page.tsx` (same-module create form) + `breadcrumb.tsx` (3-level variant) | role-match |
| `web/src/app/(dashboard)/clientes/merge/page.tsx` | component (action page) | request-response (CRUD merge action) | `web/src/app/(dashboard)/clientes/novo/page.tsx` (sibling header/breadcrumb shape) + `native-select.tsx` | role-match |
| `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx` (**new**) | component (TanStack column-def factory) | CRUD (list) | `web/src/app/(dashboard)/documentos/columns.tsx` (Phase 104, exact same role) | exact |

---

## Shared Patterns

### 1. Tabs migration (`Button` array → `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent`)

**Source primitive:** `web/src/components/ui/tabs.tsx` (90 lines, read in full)

```tsx
// tabs.tsx — variant="default" is a bg-muted pill container; active trigger gets
// data-active:bg-background data-active:shadow-sm. variant="line" (NOT used this phase)
// is an underline style.
const tabsListVariants = cva(
  "group/tabs-list inline-flex w-fit items-center justify-center rounded-lg p-[3px] text-muted-foreground ...",
  {
    variants: { variant: { default: "bg-muted", line: "gap-1 bg-transparent" } },
    defaultVariants: { variant: "default" },
  }
);
```
`Tabs` renders `group/tabs flex gap-2 data-horizontal:flex-col` — a controlled
`value`/`onValueChange` root. `TabsContent` has **no `forceMount`**, matching the
locked "replicate current mount/unmount" decision.

**Before (`clientes/[id]/page.tsx` lines 420-476 — identical shape in `processos/[id]/page.tsx` lines 1252-1311, just `flex flex-wrap` instead of `overflow-x-auto`):**
```tsx
<div className="overflow-x-auto">
  <div className="flex gap-2 w-max">
    <Button type="button" variant={tab === "dados" ? "secondary" : "outline"} onClick={() => setTab("dados")}>
      Dados
    </Button>
    <Button type="button" variant={tab === "contactosNotas" ? "secondary" : "outline"} onClick={() => setTab("contactosNotas")}>
      Contactos e Notas
    </Button>
    {canViewProcessos ? (
      <Button type="button" variant={tab === "processos" ? "secondary" : "outline"} onClick={() => setTab("processos")}>
        Processos
      </Button>
    ) : null}
    {/* ...pareceres (also RBAC-gated), documentosEntregues, documentosATratar, deslocacoes */}
  </div>
</div>

{tab === "dados" ? ( ... ) : tab === "contactosNotas" ? ( ... ) : tab === "processos" ? (
  canViewProcessos ? <ClienteProcessosTab clienteId={id} /> : <AccessDeniedState .../>
) : /* ... */ null}
```

**Target shape (Clientes — 7 triggers, 2 RBAC-gated, `overflow-x-auto` preserved):**
```tsx
<Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
  <div className="overflow-x-auto">
    <TabsList variant="default">
      <TabsTrigger value="dados">Dados</TabsTrigger>
      <TabsTrigger value="contactosNotas">Contactos e Notas</TabsTrigger>
      {canViewProcessos ? <TabsTrigger value="processos">Processos</TabsTrigger> : null}
      {canViewPareceres ? <TabsTrigger value="pareceres">Pareceres</TabsTrigger> : null}
      <TabsTrigger value="documentosEntregues">Documentos Entregues</TabsTrigger>
      <TabsTrigger value="documentosATratar">Documentos a Tratar</TabsTrigger>
      <TabsTrigger value="deslocacoes">Deslocações</TabsTrigger>
    </TabsList>
  </div>

  <TabsContent value="dados">{/* existing dados JSX, unwrapped from the `tab === "dados" ? (` conditional */}</TabsContent>
  <TabsContent value="contactosNotas">{/* ... */}</TabsContent>
  <TabsContent value="processos">
    {canViewProcessos ? <ClienteProcessosTab clienteId={id} /> : <AccessDeniedState description="Não tem permissão para consultar os processos deste cliente." />}
  </TabsContent>
  {/* ... */}
</Tabs>
```

**Processos-specific addition — preserve the `?tab=` sync exactly (lines 233-250):**
```tsx
const searchParams = useSearchParams();
const tabParam = searchParams.get("tab");
const initialTab: TabKey =
  tabParam && (TAB_KEYS as string[]).includes(tabParam) ? (tabParam as TabKey) : "timeline";
const [tab, setTab] = React.useState<TabKey>(initialTab);

React.useEffect(() => {
  const p = searchParams.get("tab");
  if (p && (TAB_KEYS as string[]).includes(p) && p !== tab) {
    setTab(p as TabKey);
  }
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [searchParams]);
```
This is untouched by the `Tabs` migration — `Tabs value={tab} onValueChange={...}`
just becomes the controlled consumer of the same `tab`/`setTab` state; the
`useSearchParams`/`useEffect` sync above needs zero changes.

**Auditoria trigger gating (`processos/[id]/page.tsx` lines 1302-1310):**
```tsx
{canManageProcessos ? (
  <TabsTrigger value="auditoria">Auditoria</TabsTrigger>
) : null}
```
And the content dispatch keeps its RBAC check inside the `TabsContent`, exactly
as today's `tab === "auditoria" && canManageProcessos ? (...)` (line 2293)
becomes `<TabsContent value="auditoria">{canManageProcessos ? (...) : null}</TabsContent>`.

**Apply to:** `clientes/[id]/page.tsx` (7 triggers), `processos/[id]/page.tsx` (8 triggers).

---

### 2. NativeSelect migration (`<select className={selectClassName}>` → `<NativeSelect>`)

**Source primitive:** `web/src/components/ui/native-select.tsx` (61 lines, read in full) —
`NativeSelect`/`NativeSelectOption`/`NativeSelectOptGroup`, `size?: "sm" | "default"`
(default `"default"`), built-in `h-9`/`rounded-md`/`border-input`/
`focus-visible:ring-ring/50` styling, chevron icon baked in. `React.ComponentProps<"select">`-compatible, so `{...form.register("field")}` spreads straight onto it.

**Variant A — Clientes RHF-bound (`selectClassName` = `rounded-none ... ring-neutral-950`), `clientes/[id]/page.tsx` lines 587-604 and `clientes/novo/page.tsx` lines 236-247:**
```tsx
// BEFORE
<select id="documento_tipo" className={selectClassName} {...form.register("documento_tipo")}>
  <option value="">Nenhum</option>
  {getDocumentoTipoOptions(form.watch("tipo")).map((opt) => (
    <option key={opt.value} value={opt.value}>{opt.label}</option>
  ))}
</select>

// AFTER
<NativeSelect id="documento_tipo" size="default" {...form.register("documento_tipo")}>
  <option value="">Nenhum</option>
  {getDocumentoTipoOptions(form.watch("tipo")).map((opt) => (
    <option key={opt.value} value={opt.value}>{opt.label}</option>
  ))}
</NativeSelect>
```
Plain `<option>` tags are fine inside `NativeSelect` (it renders a real `<select>`);
`NativeSelectOption` is optional sugar, not required — matches existing usage in
this codebase where no file uses `NativeSelectOption` yet.

**Variant B — Processos RHF-bound (`selectClassName` = `rounded-md ... ring-neutral-950`), `processos/[id]/editar/page.tsx` lines 140-150, `processos/novo/page.tsx` lines 283-339, `processos/[id]/page.tsx` line 1761 (Fase status):** same substitution pattern as Variant A, just a different source file — `className={selectClassName}` is deleted entirely, `NativeSelect` supplies its own shape.

**Variant C — list-filter / dialog controlled selects (`h-10 bg-white ... rounded-none border-slate-300 ... ring-blue-500`), `clientes/page.tsx` lines 343-351 & 357-365, `processos/page.tsx` lines 235-246 & 280-289, `processos/[id]/page.tsx`'s `ReatribuirResponsavelControl` (lines 2433-2450) and Testemunha `tipo` dialog select (lines 2155-2166):**
```tsx
// BEFORE (clientes/page.tsx:343-351)
<select
  value={draftTipo}
  onChange={(e) => setDraftTipo(e.target.value)}
  className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
>
  <option value="">Todos</option>
  <option value="PARTICULAR">Particular</option>
  <option value="EMPRESA">Empresa</option>
</select>

// AFTER
<NativeSelect value={draftTipo} onChange={(e) => setDraftTipo(e.target.value)} size="default">
  <option value="">Todos</option>
  <option value="PARTICULAR">Particular</option>
  <option value="EMPRESA">Empresa</option>
</NativeSelect>
```
The stray `focus-visible:ring-blue-500` is intentionally lost (UI-SPEC Color
section, locked) — `NativeSelect`'s own `ring-ring/50` is correct.

**Variant D — `clientes/merge/page.tsx` controlled selects (`h-10 bg-white ... rounded-md border-neutral-200 ... ring-neutral-950`), lines 92-103 and 108-119:**
```tsx
// BEFORE
<select
  value={primaryId}
  onChange={(e) => setPrimaryId(e.target.value)}
  className="h-10 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
>
  <option value="">Selecionar...</option>
  {options.map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
</select>

// AFTER
<NativeSelect value={primaryId} onChange={(e) => setPrimaryId(e.target.value)} size="default">
  <option value="">Selecionar...</option>
  {options.map((o) => <option key={o.id} value={o.id}>{o.label}</option>)}
</NativeSelect>
```

**Apply to:** every one of the 8 in-scope pages. **Also delete** both
`const selectClassName = "..."` declarations (`clientes/[id]/page.tsx` line 100,
`processos/[id]/page.tsx` line 162, plus the identical re-declarations in
`clientes/novo/page.tsx`, `processos/novo/page.tsx`, `processos/[id]/editar/page.tsx`)
once every consumer in that file has migrated — `textareaClassName` is a
**separate, unrelated constant** (used by `<textarea>`, not `<select>`) and must
**not** be deleted; only the `<textarea>`-facing declaration stays.

---

### 3. Avatar (Advogados/Administrativos/Testemunhas)

**Source primitive:** `web/src/components/ui/avatar.tsx` (112 lines, read in full) —
`Avatar` (`size="sm"` → `size-6`/24px via `data-[size=sm]:size-6`), `AvatarFallback`
(`bg-muted text-muted-foreground`, `group-data-[size=sm]/avatar:text-xs`).
`AvatarImage` not used (no photo source).

**Initials derivation to reuse verbatim** (`clientes/columns.tsx` lines 130-136, Phase 104):
```tsx
const initials = cliente.nome
  .split(" ")
  .filter(Boolean)
  .slice(0, 2)
  .map((p) => p[0])
  .join("")
  .toUpperCase();
```
Reuse the derivation only — **not** the chip's `bg-blue-50 text-blue-700 border
border-blue-100` styling (`clientes/columns.tsx` line 140); `AvatarFallback`'s
shipped default (`bg-muted text-muted-foreground`) is correct here (UI-SPEC Color
section, locked — no 5th accent-tinted surface).

**Placement — `ResponsaveisCard` row (`clientes/[id]/page.tsx` lines 1662-1674):**
```tsx
// BEFORE
<div key={u.id} className="flex items-center justify-between gap-3 rounded-md border border-neutral-200 dark:border-neutral-800 p-2">
  <div className="min-w-0">
    <div className="font-medium text-sm truncate">{u.nome}</div>
    <div className="text-xs text-neutral-500 dark:text-neutral-400 truncate space-x-2">
      {u.numeroCedula ? <span>Cédula: {u.numeroCedula}</span> : null}
      {u.telefone ? <span>Tel: {u.telefone}</span> : null}
      {u.email ? <span>{u.email}</span> : null}
    </div>
  </div>
  {canEditClientes && editable ? (
    <Button type="button" variant="outline" onClick={() => onRemove(u.id)} disabled={remove.isPending}>...</Button>
  ) : null}
</div>

// AFTER
<div key={u.id} className="flex items-center justify-between gap-3 rounded-md border border-neutral-200 dark:border-neutral-800 p-2">
  <div className="flex items-center gap-2 min-w-0">
    <Avatar size="sm">
      <AvatarFallback>{deriveInitials(u.nome)}</AvatarFallback>
    </Avatar>
    <div className="min-w-0">
      <div className="font-medium text-sm truncate">{u.nome}</div>
      <div className="text-xs text-neutral-500 dark:text-neutral-400 truncate space-x-2">{/* unchanged */}</div>
    </div>
  </div>
  {canEditClientes && editable ? ( <Button .../> ) : null}
</div>
```
The outer `justify-between` (name block vs. "Remover" button) is preserved by
wrapping the new `Avatar` + existing `min-w-0` block in one `flex items-center
gap-2` sub-container, per UI-SPEC.

**Placement — Testemunhas table "Nome" cell (`processos/[id]/page.tsx` line 2259, becomes a `TableCell` per Pattern 4 below):**
```tsx
// BEFORE
<td className="py-2 pr-4 font-medium">{t.nome}</td>

// AFTER
<TableCell>
  <div className="flex items-center gap-2">
    <Avatar size="sm">
      <AvatarFallback>{deriveInitials(t.nome)}</AvatarFallback>
    </Avatar>
    <span className="font-medium">{t.nome}</span>
  </div>
</TableCell>
```
Tipo/Contacto/Ações cells (`processos/[id]/page.tsx` lines 2260-2282) are unchanged
besides the `<td>`→`TableCell` swap.

**Apply to:** `ResponsaveisCard` (both Advogados + Administrativos instances,
`clientes/[id]/page.tsx` lines 835 & 844) and the Testemunhas table
(`processos/[id]/page.tsx`). **Not** Partes (excluded explicitly by CLP-04).

---

### 4. Table primitive migration (Partes/Fases/Testemunhas: raw `<table>` → reconciled `Table`)

**Source primitive:** `web/src/components/ui/table.tsx` (85 lines, read in full) —
`Table` (wraps in `<div className="w-full overflow-auto">`), `TableHeader`,
`TableBody`, `TableRow` (`hover:bg-muted/50`), `TableHead` (`h-12 px-4 text-left
font-semibold`), `TableCell` (`p-4 align-middle`).

**Strongest in-repo analog — already migrated, in the same file being touched:**
`clientes/[id]/page.tsx`'s `ClienteProcessosTab` (lines 1072-1119) **already**
renders through `Table`/`TableHeader`/`TableRow`/`TableHead`/`TableBody`/`TableCell`
(imported at line 25 of that same file) instead of a raw `<table>`. This is a
proven, in-project precedent for exactly the migration `processos/[id]/page.tsx`'s
Partes/Fases/Testemunhas tabs need — copy its shape, not its accent styling
(`ClienteProcessosTab`'s `bg-slate-50/50`/`uppercase tracking-wider` treatment is
its own, unrelated to this phase's scope — keep Partes/Fases/Testemunhas visually
as close to their current raw-`<table>` styling as the primitives allow, per the
"only the table markup changes" lock in `105-CONTEXT.md`):
```tsx
<Table>
  <TableHeader className="bg-slate-50/50 dark:bg-slate-900/50">
    <TableRow className="border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">NÚMERO</TableHead>
      {/* ... */}
    </TableRow>
  </TableHeader>
  <TableBody>
    {processos.data.map((p) => (
      <TableRow key={p.id} className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 ...">
        <TableCell>{/* ... */}</TableCell>
      </TableRow>
    ))}
  </TableBody>
</Table>
```

**Before — Partes tab (`processos/[id]/page.tsx` lines 1653-1675, near-identical shape for Fases at lines 1744-1792 and Testemunhas at lines 2244-2287):**
```tsx
<div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
  <table className="w-full min-w-[400px] text-sm">
    <thead className="text-left text-neutral-500 dark:text-neutral-400">
      <tr className="border-b border-neutral-200 dark:border-neutral-800">
        <th className="py-2 pr-4 font-medium">Tipo</th>
        <th className="py-2 pr-4 font-medium">Nome</th>
        <th className="py-2 pr-4 font-medium">NIF</th>
      </tr>
    </thead>
    <tbody>
      {partes.data.map((p) => (
        <tr key={p.id} className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800">
          <td className="py-2 pr-4">{p.tipo ?? "—"}</td>
          <td className="py-2 pr-4 font-medium">{p.nome}</td>
          <td className="py-2 pr-4">{p.nif ?? "—"}</td>
        </tr>
      ))}
    </tbody>
  </table>
</div>
```

**After (import `Table, TableHeader, TableBody, TableRow, TableHead, TableCell` from `"@/components/ui/table"` — not yet imported in `processos/[id]/page.tsx`, unlike its sibling):**
```tsx
<div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
  <Table className="min-w-[400px]">
    <TableHeader>
      <TableRow>
        <TableHead>Tipo</TableHead>
        <TableHead>Nome</TableHead>
        <TableHead>NIF</TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      {partes.data.map((p) => (
        <TableRow key={p.id}>
          <TableCell>{p.tipo ?? "—"}</TableCell>
          <TableCell className="font-medium">{p.nome}</TableCell>
          <TableCell>{p.nif ?? "—"}</TableCell>
        </TableRow>
      ))}
    </TableBody>
  </Table>
</div>
```
Note `Table` already wraps itself in `<div className="w-full overflow-auto">` —
the existing outer `overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0` wrapper can stay
(different horizontal-scroll technique, harmless nesting) since 105-CONTEXT.md
only asks for the element swap, not a wrapper redesign.

**Fases tab** keeps its inline `<select>` (now `NativeSelect`, Pattern 2 Variant B)
inside a `TableCell` (was `<td>`, line 1760) and its "Guardar" `Button` unchanged
(lines 1777-1786) — only the table scaffolding changes.

**Apply to:** Partes tab, Fases tab, Testemunhas tab (all inside
`processos/[id]/page.tsx`). **Not** a full `DataTable` (no sort/paginate/toolbar) —
locked in CONTEXT.md, these are small per-processo lists.

---

### 5. Breadcrumb (`<div>+Link+"/"` → `Breadcrumb`)

**Source primitive:** `web/src/components/ui/breadcrumb.tsx` (122 lines, read in full) —
`Breadcrumb` (nav), `BreadcrumbList` (`text-sm text-muted-foreground`),
`BreadcrumbItem`, `BreadcrumbLink` (`asChild` supported via Radix `Slot`),
`BreadcrumbPage` (`font-normal text-foreground`, `aria-current="page"`),
`BreadcrumbSeparator` (defaults to `ChevronRightIcon`).

**Before — the identical 2-level shape in both fichas (`clientes/[id]/page.tsx` lines 355-364, `processos/[id]/page.tsx` lines 709-719):**
```tsx
<h1 className="text-2xl font-semibold">Cliente</h1>
<div className="text-sm text-neutral-500 dark:text-neutral-400">
  <Link href="/clientes" className="hover:underline">Clientes</Link>{" "}
  <span>/</span>{" "}
  <span className="text-neutral-900 dark:text-neutral-50">
    {cliente.data?.numero_cliente ?? cliente.data?.nome ?? "…"}
  </span>
</div>
```

**After (2-level, `clientes/[id]/page.tsx` and `processos/[id]/page.tsx`):**
```tsx
<h1 className="text-2xl font-semibold">Cliente</h1>
<Breadcrumb>
  <BreadcrumbList>
    <BreadcrumbItem>
      <BreadcrumbLink asChild>
        <Link href="/clientes">Clientes</Link>
      </BreadcrumbLink>
    </BreadcrumbItem>
    <BreadcrumbSeparator />
    <BreadcrumbItem>
      <BreadcrumbPage>{cliente.data?.numero_cliente ?? cliente.data?.nome ?? "…"}</BreadcrumbPage>
    </BreadcrumbItem>
  </BreadcrumbList>
</Breadcrumb>
```
For `processos/[id]/page.tsx`: identical shape, `Processos`/`/processos` root,
`{processo.data?.numero ?? processo.data?.titulo ?? "…"}` current page, and the
`<h1 className="text-2xl font-bold">Processo</h1>` (line 710) becomes
`font-semibold` (locked correction, see UI-SPEC Scope note #4 — do not use
`font-bold`).

**Net-new, no existing div to replace — `clientes/novo/page.tsx` lines 129-136** (identical shape in `processos/novo/page.tsx` lines 201-209, `clientes/merge/page.tsx` lines 68-76):
```tsx
// BEFORE
<div>
  <h1 className="text-2xl font-semibold">Novo cliente</h1>
  <p className="text-sm text-neutral-500 dark:text-neutral-400">Criar um novo cliente.</p>
</div>
<Button asChild variant="outline"><Link href="/clientes">Voltar</Link></Button>

// AFTER — Breadcrumb inserted between h1 and p (or directly below h1); "Voltar" Button untouched
<div>
  <h1 className="text-2xl font-semibold">Novo cliente</h1>
  <Breadcrumb>
    <BreadcrumbList>
      <BreadcrumbItem>
        <BreadcrumbLink asChild><Link href="/clientes">Clientes</Link></BreadcrumbLink>
      </BreadcrumbItem>
      <BreadcrumbSeparator />
      <BreadcrumbItem><BreadcrumbPage>Novo Cliente</BreadcrumbPage></BreadcrumbItem>
    </BreadcrumbList>
  </Breadcrumb>
  <p className="text-sm text-neutral-500 dark:text-neutral-400">Criar um novo cliente.</p>
</div>
<Button asChild variant="outline"><Link href="/clientes">Voltar</Link></Button>
```
`processos/novo/page.tsx`'s own `<h1 className="text-2xl font-bold text-slate-900
dark:text-white">Novo Processo</h1>` (line 202) is **explicitly out of scope** for
the font-weight fix (UI-SPEC Scope note #4 — only the 2 detail pages converge on
`font-semibold`) — add the `Breadcrumb` only, leave the `<h1>` untouched.
`clientes/merge/page.tsx` breadcrumb: `Clientes` → `Merge` (shorter than the h1's
"Merge de clientes" per the Copywriting Contract).

**3-level upgrade — `processos/[id]/editar/page.tsx` lines 111-118 (single-level "Voltar ao detalhe" link today):**
```tsx
// BEFORE
<h1 className="text-2xl font-semibold">Editar processo</h1>
<div className="text-sm text-neutral-500 dark:text-neutral-400">
  <Link href={`/processos/${encodeURIComponent(id)}`} className="hover:underline">
    Voltar ao detalhe
  </Link>
</div>

// AFTER
<h1 className="text-2xl font-semibold">Editar processo</h1>
<Breadcrumb>
  <BreadcrumbList>
    <BreadcrumbItem>
      <BreadcrumbLink asChild><Link href="/processos">Processos</Link></BreadcrumbLink>
    </BreadcrumbItem>
    <BreadcrumbSeparator />
    <BreadcrumbItem>
      <BreadcrumbLink asChild>
        <Link href={`/processos/${encodeURIComponent(id)}`}>{processo.data?.numero ?? "…"}</Link>
      </BreadcrumbLink>
    </BreadcrumbItem>
    <BreadcrumbSeparator />
    <BreadcrumbItem><BreadcrumbPage>Editar</BreadcrumbPage></BreadcrumbItem>
  </BreadcrumbList>
</Breadcrumb>
```
`useProcesso(id)` is already loaded on this page (line 50, `const processo =
useProcesso(id)`), so `processo.data?.numero` is available with no new fetch.
This page's `<h1>` already renders `font-semibold` (verified, no color override
present) — no change needed there.

**Apply to:** all 6 pages named in CLP-05 — `clientes/[id]/page.tsx`,
`processos/[id]/page.tsx`, `clientes/novo/page.tsx`, `processos/novo/page.tsx`,
`processos/[id]/editar/page.tsx`, `clientes/merge/page.tsx`. The "Voltar" `Button`
next to the header stays untouched on every page (redundant-but-harmless,
CLP-05 doesn't ask for its removal).

---

### 6. Processo Documentos tab: `<ul>` list → shared `DataTable` (Phase 104 pattern)

**Analog:** `web/src/app/(dashboard)/documentos/columns.tsx` (231 lines, read in
full, Phase 104) + `web/src/components/shared/data-table/data-table.tsx` (127
lines, read in full — configures `getCoreRowModel`/`getSortedRowModel`/
`getPaginationRowModel` only, **no** `getFilteredRowModel`, `pageSize: 10`,
zero-row fallback: `"Sem resultados para os filtros aplicados."`).

**Imports pattern (from `documentos/columns.tsx` lines 1-15):**
```tsx
"use client";

import Link from "next/link";
import * as React from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { Download } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTableColumnHeader } from "@/components/shared/data-table/data-table-column-header";
import { useDeleteDocumento } from "@/hooks/use-documentos";
import { toast } from "@/hooks/use-toast";
import type { Documento } from "@/types/documentos";
```

**Confidencialidade Badge mapping to reuse verbatim (`documentos/columns.tsx` lines 28-43):**
```tsx
function confidencialidadeVariant(confidencialidade: string | undefined): "gray" | "blue" | "amber" | "red" {
  switch (confidencialidade ?? "PUBLICO") {
    case "PUBLICO": return "gray";
    case "INTERNO": return "blue";
    case "CONFIDENCIAL": return "amber";
    case "RESTRITO": return "red";
    default: return "gray";
  }
}
```

**Ações cell component pattern (`documentos/columns.tsx` lines 53-103) — reuse the
`DocumentoAcoesCell` shape (per-row hook via a small component, not inline in the
column def) but note the processo-scoped tab's existing `ProcessoDocumentoRow`
(lines 2683-2757 of `processos/[id]/page.tsx`) already has this exact download +
delete + `window.confirm` logic — port its `onDelete`/`onDownload` handlers into
the new cell component instead of re-deriving from `documentos/columns.tsx`,
since `ProcessoDocumentoRow` additionally handles the wire-shape workaround
(`tamanho`/`createdAt` vs. `size`/`created_at`, lines 2699-2701) that
`documentos/columns.tsx` doesn't need:**
```tsx
// ProcessoDocumentoRow's existing workaround (processos/[id]/page.tsx lines 2693-2701) —
// port into the new cell's size/date accessors, do not drop it
const wireDocumento = documento as unknown as { tamanho?: number; createdAt?: string };
const tamanho = wireDocumento.tamanho ?? 0;
const criadoEm = wireDocumento.createdAt;
```

**Column set (per UI-SPEC): drop the "Processo" column (redundant — every row is
already scoped to this processo), keep Nome/Tipo/Cliente/Confid./Ver./Tamanho/
Criado/Ações — model directly on `documentos/columns.tsx`'s `columns()` factory
shape (lines 116-231) minus the `id: "processo"` block (lines 149-169).**

**Page-level wiring — replace (`processos/[id]/page.tsx` `ProcessoDocumentosTab`, lines 2664-2677):**
```tsx
// BEFORE
{list.isLoading ? (
  <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
) : list.isError ? (
  <p className="text-sm text-red-600">Não foi possível carregar os documentos deste processo.</p>
) : documentos.length === 0 ? (
  <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento registado.</p>
) : (
  <ul className="space-y-1">
    {documentos.map((doc: Documento) => (
      <ProcessoDocumentoRow key={doc.id} documento={doc} canEditDocumentos={canEditDocumentos} />
    ))}
  </ul>
)}

// AFTER
{list.isLoading ? (
  <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
) : list.isError ? (
  <p className="text-sm text-red-600">Não foi possível carregar os documentos deste processo.</p>
) : documentos.length === 0 ? (
  <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento registado.</p>
) : (
  <DataTable columns={columns(canEditDocumentos)} data={documentos} getRowId={(d) => d.id} />
)}
```
`useDocumentos({ processo_id: processoId })` (line 2521, existing) is unchanged —
no new hook needed. `documentosPage.tsx`'s `<DataTable columns={tableColumns}
data={list.data} getRowId={(d) => d.id} />` wiring (line 158 of
`documentos/page.tsx`) is the exact reference call shape.

**Apply to:** new file `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx`
(name at Claude's discretion) + its call site inside `ProcessoDocumentosTab`.

---

## Pattern Assignments (per file)

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` (component, request-response)
**Analogs:** `processos/[id]/page.tsx` (sibling), `tabs.tsx`, `native-select.tsx`, `avatar.tsx`, `breadcrumb.tsx`, `clientes/columns.tsx` (initials derivation).
Applies Shared Patterns 1 (Tabs, 7 triggers), 2 (NativeSelect Variant A ×2 + Variant C ×3), 3 (Avatar in `ResponsaveisCard` ×2 call sites), 5 (Breadcrumb, 2-level, `font-semibold` already correct).
**Imports to add:** `Tabs, TabsList, TabsTrigger, TabsContent` from `"@/components/ui/tabs"`; `NativeSelect` from `"@/components/ui/native-select"`; `Avatar, AvatarFallback` from `"@/components/ui/avatar"`; `Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator` from `"@/components/ui/breadcrumb"`.
**Existing imports to keep as-is:** `Table, TableBody, TableCell, TableHead, TableHeader, TableRow` (line 25) — already used by `ClienteProcessosTab`/`ClienteParecerTab`, untouched by this phase.

### `web/src/app/(dashboard)/processos/[id]/page.tsx` (component, request-response)
**Analogs:** `clientes/[id]/page.tsx` (sibling + Table-primitive precedent), `tabs.tsx`, `native-select.tsx`, `avatar.tsx`, `breadcrumb.tsx`, `table.tsx`.
Applies Shared Patterns 1 (Tabs, 8 triggers, `?tab=` sync preserved), 2 (NativeSelect Variant B ×3 + Variant C ×2), 3 (Avatar in Testemunhas table), 4 (Table primitives for Partes/Fases/Testemunhas), 5 (Breadcrumb 2-level + `font-bold`→`font-semibold` h1 fix), 6 (Documentos tab → DataTable).
**Imports to add:** `Tabs, TabsList, TabsTrigger, TabsContent` from `"@/components/ui/tabs"`; `NativeSelect` from `"@/components/ui/native-select"`; `Avatar, AvatarFallback` from `"@/components/ui/avatar"`; `Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator` from `"@/components/ui/breadcrumb"`; `Table, TableHeader, TableBody, TableRow, TableHead, TableCell` from `"@/components/ui/table"` (**not currently imported in this file** — unlike its sibling); `DataTable` from `"@/components/shared/data-table/data-table"`; `columns` from `"./documentos-columns"` (new file).

### `web/src/app/(dashboard)/clientes/page.tsx` (component, request-response)
**Analog:** `processos/page.tsx` (sibling list filters), `native-select.tsx`.
Applies Shared Pattern 2 (NativeSelect Variant C ×2 — `draftTipo` line 343, `draftAtivo` line 357). No Tabs/Breadcrumb/Avatar/Table work on this file (out of the 6-page Breadcrumb scope, no tabs or tables on a list page).
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`.

### `web/src/app/(dashboard)/processos/page.tsx` (component, request-response)
**Analog:** `clientes/page.tsx` (sibling), `native-select.tsx`.
Applies Shared Pattern 2 (NativeSelect Variant C ×2 — `draftEstado` line 235, `draftClienteId` line 280).
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`.

### `web/src/app/(dashboard)/clientes/novo/page.tsx` (component, request-response)
**Analog:** `processos/novo/page.tsx` (sibling create form), `breadcrumb.tsx`, `native-select.tsx`.
Applies Shared Pattern 2 (NativeSelect Variant A ×2 — `documento_tipo` line 236, `ramo_atividade` line 271) and Pattern 5 (Breadcrumb, net-new, 2-level: `Clientes` → `Novo Cliente`). `<h1>` already `font-semibold` — no change.
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`; `Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator` from `"@/components/ui/breadcrumb"`.

### `web/src/app/(dashboard)/processos/novo/page.tsx` (component, request-response)
**Analog:** `clientes/novo/page.tsx` (sibling), `breadcrumb.tsx`, `native-select.tsx`.
Applies Shared Pattern 2 (NativeSelect Variant B ×4 — `cliente_id` line 283, `tipo_processo` line 308, `origem` line 331, `nivel_final` line 538) and Pattern 5 (Breadcrumb, net-new: `Processos` → `Novo Processo`). `<h1 className="text-2xl font-bold text-slate-900 dark:text-white">` **stays untouched** — the h1 weight fix is explicitly scoped to only the 2 detail pages (UI-SPEC Scope note #4).
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`; `Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator` from `"@/components/ui/breadcrumb"`.

### `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` (component, request-response)
**Analog:** `processos/novo/page.tsx` (same-module RHF select pattern), `breadcrumb.tsx` (3-level).
Applies Shared Pattern 2 (NativeSelect Variant B ×1 — `cliente_id` line 140) and Pattern 5 (Breadcrumb, net-new 3-level upgrade from single-level "Voltar ao detalhe": `Processos` → `{processo.data?.numero}` (Link) → `Editar`). `<h1>` already `font-semibold` — no weight change; drop `text-slate-900 dark:text-white` only if present (verified: **not present** on this page today, so no-op here).
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`; `Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator` from `"@/components/ui/breadcrumb"`.

### `web/src/app/(dashboard)/clientes/merge/page.tsx` (component, request-response)
**Analog:** `clientes/novo/page.tsx` (sibling header shape), `native-select.tsx`.
Applies Shared Pattern 2 (NativeSelect Variant D ×2 — `primaryId` line 92, `secondaryId` line 108) and Pattern 5 (Breadcrumb, net-new 2-level: `Clientes` → `Merge`). `<h1>` already `font-semibold` — no change.
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`; `Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator` from `"@/components/ui/breadcrumb"`.

### `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx` (**new**, component/CRUD)
**Analog:** `web/src/app/(dashboard)/documentos/columns.tsx` (Phase 104, exact same role — `ColumnDef<Documento>[]` factory).
See Shared Pattern 6 above for the full imports/Badge-mapping/Ações-cell/column-set breakdown. Factory signature: `columns(canEditDocumentos: boolean): ColumnDef<Documento>[]` (no `processoById`/`clienteNomeById` params needed — the "Processo" column is dropped entirely since every row is already processo-scoped; "Cliente" column can stay if the `Documento` type carries `cliente_id`, resolved the same way `documentos/columns.tsx` does at lines 170-184, or be dropped too — Claude's discretion per CONTEXT.md, not locked by UI-SPEC).

---

## No Analog Found

None — every file in scope is an existing file being migrated in place, and every
new UI primitive it needs was already installed and has at least one existing
consumer pattern (`documentos/columns.tsx` for the DataTable factory shape,
`ClienteProcessosTab` for the Table-primitive shape) to model against.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/clientes/`, `web/src/app/(dashboard)/processos/`, `web/src/app/(dashboard)/documentos/`, `web/src/components/ui/`, `web/src/components/shared/data-table/`
**Files scanned:** 15 (9 in-scope target files + `tabs.tsx`, `native-select.tsx`, `avatar.tsx`, `breadcrumb.tsx`, `table.tsx`, `data-table.tsx`, `documentos/columns.tsx`, `documentos/page.tsx`, `clientes/columns.tsx`)
**Pattern extraction date:** 2026-07-16
