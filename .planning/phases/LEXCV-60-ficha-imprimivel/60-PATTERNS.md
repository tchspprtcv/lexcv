# Phase 60: Ficha Imprimível — Pattern Map

**Mapped:** 2026-06-29
**Files analyzed:** 4 (1 create, 3 modify)
**Analogs found:** 4 / 4

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` | page/component | request-response (read-only) | `web/src/app/(dashboard)/clientes/[id]/page.tsx` | exact — same hook, same permission guard, same params pattern |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | page/component | request-response | itself (modification) | self — add Button asChild + Link pattern already present in lines 75-82 |
| `web/src/app/(dashboard)/clientes/page.tsx` | page/component | request-response | itself (modification) | self — add icon Button asChild + Link inside ClienteRow (lines 538-543) |
| `web/src/types/clientes.ts` | type | N/A | itself (modification) | self — extend `Cliente` interface with optional fields |

---

## Pattern Assignments

### `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` (CREATE)

**Analog:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`

**Imports pattern** (lines 1-25 of analog):
```tsx
"use client";

import Link from "next/link";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useCliente } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import { useMe } from "@/hooks/use-me";
```

Note: add `useMe` to read `me.data?.tenant_nome` for the office header. `useMe` is the hook used in `dashboard-shell.tsx` line 54 to read `me.data?.tenant_nome` (line 265 of shell).

**Auth/permission guard pattern** (analog lines 37-50):
```tsx
export default function FichaPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");

  if (!permissions.isLoading && !canViewClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este cliente."
        backHref="/clientes"
      />
    );
  }

  return <FichaContent id={id} />;
}
```

**Params pattern** (analog lines 27-29 and 36):
```tsx
type PageProps = {
  params: Promise<{ id: string }>;
};
// Inside component:
const { id } = React.use(params);
```

**Data loading pattern** (analog `ClienteDetailContent`, lines 53-58):
```tsx
function FichaContent({ id }: { id: string }) {
  const cliente = useCliente(id);
  const me = useMe();

  if (cliente.isLoading) {
    return <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>;
  }
  if (!cliente.data) {
    return <div className="text-sm text-neutral-500 dark:text-neutral-400">Cliente não encontrado.</div>;
  }
  // render ficha
}
```

**Print CSS injection pattern** (inline `<style>` in JSX — avoids polluting globals.css):
```tsx
// CSS selectors from actual dashboard-shell.tsx structure:
// - aside (line 80): `<aside className="hidden md:flex w-[270px] ...`
// - header (line 241): `<header className="h-16 bg-white/80 ...`
// - BottomNav (line 303): rendered via <BottomNav /> component
// - The print button itself: use data-print-hide attribute

const PRINT_STYLES = `
  @media print {
    aside,
    header,
    [data-print-hide] {
      display: none !important;
    }
    body {
      background: white !important;
    }
    .ficha-a4 {
      width: 100%;
      max-width: 100%;
    }
  }
  @page {
    size: A4;
    margin: 2cm;
  }
`;

// In JSX:
<>
  <style dangerouslySetInnerHTML={{ __html: PRINT_STYLES }} />
  <div className="ficha-a4 font-serif text-black">
    {/* ficha content */}
  </div>
  <div data-print-hide className="mt-6 flex justify-end gap-3">
    <Button asChild variant="outline">
      <Link href={`/clientes/${encodeURIComponent(id)}`}>Voltar</Link>
    </Button>
    <Button onClick={() => window.print()}>Imprimir</Button>
  </div>
</>
```

**BottomNav print hide note:** `BottomNav` is rendered inside `DashboardShell` as `<BottomNav permissions={me.data?.permissions} />` (line 303 of shell). Check `web/src/components/shared/bottom-nav.tsx` for the actual root element tag — add it to the `@media print` selector list if it is not `header` or `aside`.

**Blank field pattern** (D-03):
```tsx
// Utility function — place inside ficha/page.tsx
function val(v: string | number | undefined | null): React.ReactNode {
  if (v === undefined || v === null || v === "") {
    return <span className="border-b border-black inline-block min-w-[180px]">&nbsp;</span>;
  }
  return <>{v}</>;
}
```

**Tenant name in header:**
```tsx
// me.data?.tenant_nome is available via useMe() — used in dashboard-shell.tsx line 265
const tenantNome = me.data?.tenant_nome ?? "LexCV";
```

---

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` (MODIFY — add "Imprimir Ficha" button)

**Exact insertion point** — line 74-83 of the file (the `<div className="flex gap-2">` block):
```tsx
// BEFORE (lines 74-83):
<div className="flex gap-2">
  <Button asChild variant="outline">
    <Link href="/clientes">Voltar</Link>
  </Button>
  {canEditClientes ? (
    <Button asChild>
      <Link href={`/clientes/${encodeURIComponent(id)}/editar`}>Editar</Link>
    </Button>
  ) : null}
</div>

// AFTER — add Imprimir Ficha button (same Button asChild + Link pattern):
<div className="flex gap-2">
  <Button asChild variant="outline">
    <Link href="/clientes">Voltar</Link>
  </Button>
  <Button asChild variant="outline">
    <Link
      href={`/clientes/${encodeURIComponent(id)}/ficha`}
      target="_blank"
      rel="noopener noreferrer"
    >
      Imprimir Ficha
    </Link>
  </Button>
  {canEditClientes ? (
    <Button asChild>
      <Link href={`/clientes/${encodeURIComponent(id)}/editar`}>Editar</Link>
    </Button>
  ) : null}
</div>
```

No new imports needed — `Button` and `Link` are already imported (lines 6 and 3).

---

### `web/src/app/(dashboard)/clientes/page.tsx` (MODIFY — add Printer icon to ClienteRow)

**Import addition** (line 5 of file):
```tsx
// BEFORE:
import { Eye, Filter, Pencil, Plus, Search, Trash2 } from "lucide-react";

// AFTER:
import { Eye, Filter, Pencil, Plus, Printer, Search, Trash2 } from "lucide-react";
```

**ClienteRow action buttons — exact insertion point** (lines 537-562 of file, the `<div className="inline-flex items-center gap-1">` block):

The desktop table row action cell (lines 537-563). Add a `Printer` button between the `Eye` button and the `Pencil` button:
```tsx
// Pattern copied from existing Eye button (lines 539-543):
<Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
  <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
    <Eye className="h-4 w-4" />
  </Link>
</Button>

// NEW — Printer button, same pattern, after Eye button:
<Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-violet-600 dark:hover:text-violet-400 transition-colors" title="Imprimir Ficha">
  <Link
    href={`/clientes/${encodeURIComponent(cliente.id)}/ficha`}
    target="_blank"
    rel="noopener noreferrer"
  >
    <Printer className="h-4 w-4" />
  </Link>
</Button>
```

**Mobile card — also add Printer button** (lines 414-428 of file). The mobile card has `<div className="mt-3 pl-[52px] flex items-center gap-1">` with Eye and conditional Pencil. Add Printer button using the same mobile icon pattern (lines 415-419):
```tsx
// Existing mobile Eye button pattern:
<Button asChild size="sm" variant="ghost" className="h-12 w-12 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
  <Link href={`/clientes/${encodeURIComponent(c.id)}`}>
    <Eye className="h-4 w-4" />
  </Link>
</Button>

// NEW mobile Printer button (same 48px touch target h-12 w-12 for WCAG compliance):
<Button asChild size="sm" variant="ghost" className="h-12 w-12 p-0 text-slate-500 hover:text-violet-600 dark:hover:text-violet-400 transition-colors" title="Imprimir Ficha">
  <Link
    href={`/clientes/${encodeURIComponent(c.id)}/ficha`}
    target="_blank"
    rel="noopener noreferrer"
  >
    <Printer className="h-4 w-4" />
  </Link>
</Button>
```

Note: `Printer` is available in lucide-react (already installed in project). No new package needed.

---

### `web/src/types/clientes.ts` (MODIFY — add Phase 57/59 optional fields)

**Exact insertion point** — extend the `Cliente` interface (lines 1-21 of file):
```tsx
// BEFORE (lines 1-21):
export interface Cliente {
  id: string;
  tenant_id: string;
  tipo?: string;
  nome: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: string;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: string;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
  created_at: string;
}

// AFTER — add optional Phase 57 + 59 fields at the end, before created_at:
export interface Cliente {
  id: string;
  tenant_id: string;
  tipo?: string;
  nome: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: string;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: string;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
  // Phase 57 fields
  numero_cliente?: string;
  numeroCliente?: string;
  idade?: number;
  sexo?: string;
  nacionalidade?: string;
  avencado?: boolean;
  // Phase 59 intake fields
  descricao_caso?: string;
  descricaoCaso?: string;
  advogados_nomes?: string;
  advogadosNomes?: string;
  administrativos?: string;
  documentos_entregues?: string;
  documentosEntregues?: string;
  documentos_a_tratar?: string;
  documentosATratar?: string;
  deslocacoes?: string;
  honorarios_propostos?: string;
  honorariosPropostos?: string;
  honorarios_por_extenso?: string;
  honorariosPorExtenso?: string;
  honorarios_previsao?: string;
  honorariosPrevisao?: string;
  created_at: string;
}
```

Pattern: all new fields are `optional` (`?`) following the existing convention. Both snake_case and camelCase variants are listed where the API may return either (matching the existing pattern of `documentoTipo` / `documento_tipo`).

---

## Shared Patterns

### Permission Guard
**Source:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 37-50
**Apply to:** `ficha/page.tsx` — must include the same `canViewClientes` guard before rendering any client data.

```tsx
const permissions = usePermissions();
const canViewClientes = permissions.can.view("clientes");

if (!permissions.isLoading && !canViewClientes) {
  return (
    <AccessDeniedState
      description="Não tem permissão para consultar este cliente."
      backHref="/clientes"
    />
  );
}
```

### Button asChild + Link (open in new tab)
**Source:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 75-77
**Apply to:** all three files that add navigation to `/ficha`

```tsx
<Button asChild variant="outline">
  <Link href={`/clientes/${encodeURIComponent(id)}/ficha`} target="_blank" rel="noopener noreferrer">
    Imprimir Ficha
  </Link>
</Button>
```

### Icon Button in Table Row
**Source:** `web/src/app/(dashboard)/clientes/page.tsx` lines 539-543
**Apply to:** `ClienteRow` Printer button (desktop and mobile variants)

```tsx
<Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-[color] dark:hover:text-[color] transition-colors">
  <Link href="...">
    <Icon className="h-4 w-4" />
  </Link>
</Button>
// Mobile: h-12 w-12 (48px touch target)
```

### Loading / Error / Not Found states
**Source:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` lines 86-95
**Apply to:** `ficha/page.tsx`

```tsx
{isLoading ? (
  <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
) : isError ? (
  <div className="text-sm text-red-600">Erro ao carregar</div>
) : !data ? (
  <div className="text-sm text-neutral-500 dark:text-neutral-400">Cliente não encontrado.</div>
) : (
  /* render content */
)}
```

### Tenant Name
**Source:** `web/src/components/shared/dashboard-shell.tsx` line 265
**Apply to:** `ficha/page.tsx` header section

```tsx
// In dashboard-shell.tsx line 265:
{me.data?.tenant_nome ?? "LexCV"}
// Use same pattern in ficha page:
const me = useMe();
const tenantNome = me.data?.tenant_nome ?? "LexCV";
```

---

## CSS Print Selectors Reference

Derived from `web/src/components/shared/dashboard-shell.tsx`:

| Element | Line in Shell | CSS Selector |
|---------|--------------|--------------|
| Sidebar | line 80 — `<aside className="hidden md:flex w-[270px]...` | `aside` |
| Top bar | line 241 — `<header className="h-16 bg-white/80...` | `header` |
| Mobile drawer | line 159 — `<Sheet open={drawerOpen}...` | Sheet renders as a portal — already hidden when not open; no selector needed |
| Bottom nav | line 303 — `<BottomNav />` | Check `bottom-nav.tsx` for root element tag; add `nav` or specific selector |
| Print button | in ficha/page.tsx | `[data-print-hide]` attribute on the action div |

---

## No Analog Found

None. All four files have clear analogs or are self-modifications.

---

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/clientes/`, `web/src/hooks/`, `web/src/types/`, `web/src/components/shared/`
**Files read:** 6 (clientes/[id]/page.tsx, clientes/page.tsx, types/clientes.ts, hooks/use-clientes.ts, hooks/use-permissions.ts, components/shared/dashboard-shell.tsx)
**Pattern extraction date:** 2026-06-29
