---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
reviewed: 2026-07-22T00:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - web/src/app/(auth)/login/page.tsx
  - web/src/app/(dashboard)/agenda/[id]/editar/page.tsx
  - web/src/app/(dashboard)/agenda/[id]/page.tsx
  - web/src/app/(dashboard)/agenda/novo/page.tsx
  - web/src/app/(dashboard)/agenda/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/app/(dashboard)/clientes/merge/page.tsx
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/dashboard/page.tsx
  - web/src/app/(dashboard)/documentos/[id]/page.tsx
  - web/src/app/(dashboard)/documentos/columns.tsx
  - web/src/app/(dashboard)/documentos/novo/page.tsx
  - web/src/app/(dashboard)/documentos/page.tsx
  - web/src/app/(dashboard)/financeiro/[id]/page.tsx
  - web/src/app/(dashboard)/financeiro/novo/page.tsx
  - web/src/app/(dashboard)/financeiro/page.tsx
  - web/src/app/(dashboard)/notificacoes/page.tsx
  - web/src/app/(dashboard)/pareceres/[id]/page.tsx
  - web/src/app/(dashboard)/pareceres/nova/page.tsx
  - web/src/app/(dashboard)/pareceres/page.tsx
  - web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx
  - web/src/app/(dashboard)/processos/[id]/editar/page.tsx
  - web/src/app/(dashboard)/processos/[id]/page.tsx
  - web/src/app/(dashboard)/processos/novo/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
  - web/src/app/(dashboard)/settings/page.tsx
  - web/src/components/profile/user-password-form.tsx
  - web/src/components/profile/user-profile-form.tsx
  - web/src/components/shared/access-denied-state.tsx
  - web/src/components/shared/data-table/data-table-pagination.tsx
  - web/src/components/shared/notificacao-snooze-control.tsx
findings:
  critical: 0
  warning: 2
  info: 2
  total: 4
status: issues_found
---

# Phase 115: Code Review Report

**Reviewed:** 2026-07-22T00:00:00Z
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

Reviewed all 32 files touched by Phase 115 (ICON-01: fill missing Lucide icons on text-only `<Button>`s app-wide; FICO-01: convert Aplicar/Limpar/Exportar filter buttons to icon-only+`Tooltip` in Clientes/Processos/Agenda/Documentos/Financeiro). This review was cross-checked line-by-line against the phase's own approved `115-UI-SPEC.md` design contract (icon vocabulary table, FICO-01 button inventory, scope boundaries, do-not-touch list), which let several apparent issues be correctly ruled either "pre-existing and explicitly sanctioned" or "consciously out of scope" rather than misattributed to this phase.

**Verified clean (no issues found):**
- No broken or unused icon imports in any of the 32 files (every imported Lucide icon is used exactly once or more; every icon name resolves to a real `lucide-react` export, verified by direct module introspection, including the `type LucideIcon` import in `dashboard/page.tsx`).
- All 12 FICO-01 conversions (Clientes ×3, Processos ×3 incl. the pre-existing-icon Exportar special case, Processos-detail Timeline ×1, Agenda ×1, Documentos ×2, Financeiro ×2) use unified `aria-label`/`TooltipContent` copy ("Aplicar filtros" / "Limpar filtros" / "Exportar CSV"), correctly preserve each button's pre-existing `variant`, and correctly use the `size="icon"` prop (except the one explicitly-exempted Processos Exportar special case, which the spec says to leave as its pre-existing hand-rolled `h-9 w-9 p-0`).
- Pareceres and Notificações correctly keep icon+text (not converted to icon-only), matching the explicit FICO-01 module exclusion.
- `TooltipProvider` is correctly mounted once at the app root (`providers.tsx`), so every new `Tooltip` usage works without additional wiring.
- Icon vocabulary (`Plus`=create, `Trash2`=delete, `Pencil`=edit, `Download`=export, `Check`=apply/confirm, `X`=clear/cancel, `Eye`=view details, `ArrowRight`=forward navigation) is applied consistently across all 32 files.
- The one pre-existing `Edit` vs. `Pencil` inconsistency in `settings/page.tsx` is explicitly documented in `115-UI-SPEC.md` §"Do-not-touch list" as intentionally left alone — correctly not touched by this phase, not a defect.
- Several native `<button>` elements (Unicode "✕" instead of a Lucide icon, plain-text "Editar" links with no icon) remain in the Decisões/Factos/Testemunhas tables of `processos/[id]/page.tsx` and the intake lists of `clientes/[id]/page.tsx`. These are **not** a defect of this phase: `115-UI-SPEC.md` §"Scope Boundaries" #3 explicitly and consciously excludes native `<button>` elements from the ICON-01 audit (only the shadcn `<Button>` component was in scope).

Two real issues remain (both pre-existing, surfaced here because they match this review's explicit accessibility / icon-action-correctness criteria) plus two minor informational notes.

## Warnings

### WR-01: "Editar" quick action on the Clientes mobile list is a no-op — identical href to "Ver detalhes"

**File:** `web/src/app/(dashboard)/clientes/page.tsx:512-535`
**Issue:** The mobile card view renders two adjacent icon-only buttons per row: `Eye` labelled "Ver detalhes" and `Pencil` labelled "Editar". Both `<Link>`s point to the exact same href:

```tsx
// Ver detalhes
<Link href={`/clientes/${encodeURIComponent(c.id)}`}>
  <Eye className="h-4 w-4" />
</Link>
// Editar
<Link href={`/clientes/${encodeURIComponent(c.id)}`}>
  <Pencil className="h-4 w-4" />
</Link>
```

`clientes/[id]/page.tsx` always opens read-only (`isEditing` defaults to `false` and is only flipped by clicking the in-page "Editar" button — there is no query param or route that opens it directly in edit mode). So the `Pencil` "Editar" button doesn't actually enter edit mode; it opens the identical read-only detail view as "Ver detalhes", and the user still has to find and click the in-page Editar button. The icon promises an action (per this app's own vocabulary, `Pencil` = edit) that this control does not deliver — a direct icon/action mismatch. Both buttons are cited as already-compliant/pre-existing in `115-UI-SPEC.md`'s own audit (`L494 Eye+Tooltip`, `L505 Pencil+Tooltip`), so this predates Phase 115 and is not a regression from this icon-insertion work — it's flagged here because it matches this review's explicit "icon/action mismatch" criterion.
**Fix:** Either drop the redundant "Editar" quick action (since the target page can't be deep-linked into edit mode today), or wire it properly, e.g.:
```tsx
// clientes/page.tsx — pass an intent flag
<Link href={`/clientes/${encodeURIComponent(c.id)}?edit=1`}>
  <Pencil className="h-4 w-4" />
</Link>

// clientes/[id]/page.tsx — honor it once, on mount
const searchParams = useSearchParams();
const [isEditing, setIsEditing] = React.useState(() => searchParams.get("edit") === "1");
```

### WR-02: Icon-only search-clear button has no accessible name

**File:** `web/src/app/(dashboard)/settings/page.tsx:374-380`
**Issue:** In the user-management search box, the clear button is a plain native `<button>` (not the shadcn `Button` used everywhere else) with only an `X` icon inside — no `aria-label`, no `title`, no visually-hidden text, and it isn't wrapped in `Tooltip`:
```tsx
{searchTerm && (
  <button
    onClick={() => setSearchTerm("")}
    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
  >
    <X className="h-4 w-4" />
  </button>
)}
```
A screen reader announces this as an unlabeled "button" with no way to know it clears the search field — a direct violation of this review's accessibility bar ("icon-only buttons must retain an accessible name via aria-label/sr-only/Tooltip"). `115-UI-SPEC.md` §"Scope Boundaries" #3 explicitly places native `<button>` elements outside ICON-01's tracked audit, so this predates/sits outside this phase's formal scope — but it's a live defect in a file within this review's file list and worth fixing regardless of attribution.
**Fix:**
```tsx
{searchTerm && (
  <button
    type="button"
    onClick={() => setSearchTerm("")}
    aria-label="Limpar pesquisa"
    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
  >
    <X className="h-4 w-4" />
  </button>
)}
```

## Info

### IN-01: Redundant icon margin doubles up with Button's own flex gap

**File:** `web/src/components/profile/user-password-form.tsx:165,167`; `web/src/components/profile/user-profile-form.tsx:198,200`
**Issue:** `buttonVariants` (`components/ui/button.tsx`) already applies `gap-2` between an icon and its label, and `115-UI-SPEC.md` documents "`h-4 w-4` on every icon inside a `Button`, with zero exceptions found across all 44 already-compliant instances" (no margin utility is part of the established pattern — confirmed by every other icon+text button across all 32 reviewed files, which render the icon bare). Per the spec's own audit, both of these files had **zero** compliant icons before this phase (`Compliant: 0`), so the icons here — and their `mr-2` — were added by this phase's work:
```tsx
{mutation.isPending ? (
  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
) : (
  <Save className="h-4 w-4 mr-2" />
)}
```
This produces a visibly wider icon-to-label gap on these two buttons ("Atualizar Palavra-passe", "Guardar Alterações") than on every other button in the app.
**Fix:** Drop the redundant margin utility and let `Button`'s own `gap-2` handle spacing, matching every other instance:
```tsx
{mutation.isPending ? (
  <Loader2 className="h-4 w-4 animate-spin" />
) : (
  <Save className="h-4 w-4" />
)}
```

### IN-02: Prazo-conclusion toggle's accessible name comes from `title`, not the codebase's `aria-label` convention

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:1171-1184`
**Issue:**
```tsx
<button
  type="button"
  title="Marcar como concluído"
  onClick={() => void onToggleConcluido(p.id, !p.concluido)}
  className="cursor-pointer"
>
  {p.concluido ? <CheckCircle2 .../> : <Circle .../>}
</button>
```
`title` does contribute a fallback accessible name per the HTML accname spec, so this isn't a hard accessibility failure like WR-02 — but every other icon-only control in the app (40+ instances) uses `aria-label` (in several cases paired with `Tooltip`), so this is the sole outlier relying on `title` alone. `115-UI-SPEC.md` explicitly cites this exact button (`processos/[id]/page.tsx:1154-1165` in the spec's line numbering) as a native `<button>` "outside this audit's bounded scope" — i.e., consciously not part of this phase's mandate. No fix required for phase completion; noting for awareness only.
**Fix (optional, low priority):** `<button type="button" title="Marcar como concluído" aria-label="Marcar como concluído" ...>` to align with the dominant convention.

---

_Reviewed: 2026-07-22T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
