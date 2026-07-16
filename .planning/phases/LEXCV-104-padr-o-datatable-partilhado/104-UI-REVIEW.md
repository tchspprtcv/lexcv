# Phase 104 — UI Review

**Audited:** 2026-07-16
**Baseline:** `.planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-UI-SPEC.md`
**Screenshots:** Attempted, not usable — dev server responded on `localhost:3000` (verified `curl` 200), and `npx playwright screenshot` successfully captured `.planning/ui-reviews/104-20260716-131245/clientes-desktop.png`, but the session resolved to an `AccessDeniedState` wrapper page (no valid authenticated cookie for this headless run), so no authenticated DataTable view could be photographed. This audit is therefore effectively **code-only**, cross-checked against the already-completed live human checkpoint in `104-06-SUMMARY.md` (which had valid credentials) — findings below are scoped to defects that checkpoint did **not** surface, per the task brief.

**Scope note:** This is a re-audit after a full `gsd-code-reviewer` + 3-iteration `gsd-code-fixer` loop (`104-REVIEW.md` → `104-REVIEW.iter3.md`) already fixed a CSV-formula-injection bug, a Documentos Download-action gap, column-visibility label friendliness, Clientes NIF sort/display mismatch, memoization, and duplicated-helper extraction, and after a live human visual checkpoint (`104-06-SUMMARY.md`) confirmed sorting/pagination/column-visibility/badges/mobile/RBAC across all 5 screens in both themes. Findings here are net-new, found by reading the current source directly (not duplicating either prior pass).

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All mandated contract strings verified byte-exact; zero generic-label hits |
| 2. Visuals | 2/4 | Ações column header misaligned vs. right-aligned action cells (4/5 screens); Documentos Download icon-button lacks the Tooltip every sibling icon-button has; row-hover gives a false "whole row is clickable" affordance signal |
| 3. Color | 2/4 | The 5 "shared pattern" screens split into two different gray families (`slate-*` in 3 screens vs `neutral-*` in Financeiro/Documentos) plus two different link-hover idioms — undermines the single-pattern premise of this exact phase |
| 4. Typography | 2/4 | Header-typography fix (12px/600 uppercase) is correctly and uniformly applied — but 2 brand-new Badge cells (Financeiro Estado, Documentos Tipo+Confidencialidade) ship at `text-[10px]`, reintroducing the exact "below the 12px Label floor" defect this phase's own contract explicitly closed for headers |
| 5. Spacing | 3/4 | The one shared toolbar wrapper (`data-table.tsx`) uses `py-3` (12px) and the shared sort-button uses `gap-1.5` (6px) — both off the declared 7-point scale, though the footer (`px-6 py-4`) correctly reuses the mandated precedent |
| 6. Experience Design | 2/4 | Documentos' Processo/Cliente columns render raw internal UUIDs (never resolved to names) — a defect the human checkpoint could not have caught since its dev tenant had zero documents; Processos' DataTable (new toolbar + functional pagination footer) renders unconditionally with no `hidden md:block` mobile gate, unlike all 4 siblings |

**Overall: 15/24**

---

## Top 3 Priority Fixes

1. **Documentos' "Processo" and "Cliente" columns display raw UUIDs instead of resolved names** — Lawyers/staff scanning the Documentos list see meaningless internal ids (`documentos/columns.tsx:133-143`: `cell: ({ row }) => row.original.processo_id ?? "—"` / `cliente_id ?? "—"`) where every sibling screen (Processos, Pareceres, Financeiro) built specifically for this phase resolves the same kind of foreign key through a `clienteNomeById`/`processoById` lookup Map — Documentos is the only one of the 5 "unified" screens that didn't get this treatment. **Fix:** add `useClientes()`/`useProcessos()` calls in `documentos/page.tsx` (not currently imported at all — confirmed via import list), build `clienteNomeById`/`processoById` Maps exactly as `financeiro/page.tsx` already does, and thread them into `columns(canEditDocumentos, processoById, clienteNomeById)`.

2. **Processos' DataTable has no mobile gate, unlike all 4 sibling screens** — `processos/page.tsx:308-315` renders `<DataTable columns={processoColumns} data={processos.data} />` unconditionally (no `hidden md:block` wrapper), while Clientes/Pareceres/Financeiro/Documentos all correctly wrap their `<DataTable>` in `<div className="hidden md:block">` with a dedicated `md:hidden` mobile-card fallback. This predates the phase (Processos never had a card view), but this phase made it materially worse: it now renders a brand-new toolbar (`DataTableViewOptions` icon button) and a brand-new *functional* pagination footer (rows-per-page `Select` + live Anterior/Seguinte) — UI chrome that did not exist at this density before — at all viewport widths including narrow mobile, with zero responsive fallback. **Fix:** either build a `ProcessoMobileCard` mirroring Clientes' pattern and gate the DataTable behind `hidden md:block` (closing full parity with the other 4 screens), or, at minimum, file this as a tracked follow-up rather than let it ship silently as a "no worse than before" deviation — the actual UI surface exposed on mobile did increase.

3. **Two brand-new Badge cells ship at `text-[10px]`, undoing the exact typography fix this phase shipped for headers** — `financeiro/columns.tsx:105` (Estado Badge, the phase's own flagship "close Financeiro's Badge gap" fix) and `documentos/columns.tsx:125,153` (Tipo, Confidencialidade — both invented fresh this phase) all override the Badge's own `text-xs` (12px) default down to `text-[10px]`, below the Label-role 12px floor that `104-UI-SPEC.md`'s Typography section explicitly named and fixed for column headers ("a 10px size that is off the declared scale — below Label's 12px floor"). Meanwhile Processos' Estado Badge and Área Jurídica Badge, and Clientes' Tipo Badge — built in the very same phase — correctly ship with no size override (12px). **Fix:** drop the `text-[10px]` override from these 3 Badge instances (both files) so all "status/type" Badges across the 5 screens render at the same 12px Label size, matching Processos/Pareceres/Clientes.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

Grep-verified across `web/src/components/shared/data-table/` and all 5 `columns.tsx` files: zero hits for `Submit`/`Click Here`/`OK`/`Cancel`/`Save` as literal UI copy. All mandated Pagination-Contract strings are present verbatim and shared by a single component (`data-table-pagination.tsx:37,56-77`): "Linhas por página", "Página {n} de {total}", "Anterior", "Seguinte" — since this is one shared component, all 5 screens get byte-identical copy with zero drift risk. The empty-fallback copy "Sem resultados para os filtros aplicados." is present exactly as specified (`data-table.tsx:117`). Column-visibility trigger carries `aria-label="Colunas visíveis"` (`data-table-view-options.tsx:45`) matching the Copywriting Contract. No new CTA/destructive-confirmation copy was introduced (matches the "N/A this phase" contract entries). No defects found — contract fully met.

### Pillar 2: Visuals (2/4)

- **Ações header/cell misalignment (Clientes, Processos, Pareceres, Documentos — 4/5 screens).** `DataTableColumnHeader`'s non-sortable branch renders a plain `<span>` with no alignment control (`data-table-column-header.tsx:37-38`), which inherits `TableHead`'s default `text-left` (`table.tsx:59`). Every Ações cell, however, right-aligns its buttons: `clientes/columns.tsx:237` (`<div className="flex justify-end">`), `processos/columns.tsx:132`, `pareceres/columns.tsx:85` (`<div className="text-right">`). Result: the column header text "Ações" sits flush-left while every action button below it sits flush-right — a small but real, consistently-reproduced visual-hierarchy mismatch across 4 of 5 migrated screens, all built in this phase from the same `DataTableColumnHeader`.
- **Row-hover gives a false "whole row is clickable" signal.** `TableRow`'s `hover:bg-muted/50` (`table.tsx:46`) applies uniformly to header rows too, so hovering anywhere over a header row — including over non-sortable cells like "Contacto" or "Ações" — tints the entire row identically to hovering over a sortable cell. Meanwhile the sort button's own dedicated hover state is explicitly cancelled: `data-table-column-header.tsx:48` sets `hover:bg-transparent`, which (via `twMerge` in `cn()`, confirmed in `lib/utils.ts`) overrides the ghost `Button` variant's default `hover:bg-neutral-100 dark:hover:bg-neutral-800` (`button.tsx:16`). Net effect: there is no cell-level differentiation between "this header sorts" and "this header doesn't" on hover — only a static icon (present regardless of hover) signals sortability.
- **Documentos' Download icon-button breaks the established Tooltip convention.** Every other icon-only action button in the 5 migrated tables pairs `aria-label` with a visible `Tooltip` (`clientes/columns.tsx:39-54,55-74,76-91,94-109`; `processos/columns.tsx:133-148`; `pareceres/columns.tsx:86-101`; `data-table-view-options.tsx:38-53`). The Download button added to `documentos/columns.tsx:78-86` (`<Button asChild variant="ghost" size="sm" aria-label="Download">`) has the `aria-label` but is not wrapped in `Tooltip`/`TooltipTrigger`/`TooltipContent` — a real, reproducible inconsistency with the DSR-03 convention this same codebase applies everywhere else. (This button was added by the later code-review fix loop, after the human visual checkpoint ran, so it was never eyeballed live.)
- Positive: focal point/hierarchy is otherwise intact — Badges create real visual differentiation for status/type columns, avatar-initial chips give Clientes' Nome column a clear anchor, and no icon-only button is missing an `aria-label`.

### Pillar 3: Color (2/4)

Grep-quantified gray-family split across the 5 newly-authored `columns.tsx` files (all built fresh in this phase, not carried-over markup):

| Screen | `slate-*` hits | `neutral-*` hits |
|---|---|---|
| Clientes | 10 | 0 |
| Processos | 6 | 0 |
| Pareceres | 5 | 0 |
| Financeiro | 0 | 1 |
| Documentos | 0 | 1 |

Clientes/Processos/Pareceres consistently use the `slate` palette for body/secondary/link text (e.g. `clientes/columns.tsx:146`: `text-slate-900 dark:text-white`); Financeiro/Documentos exclusively use `neutral` for the same role (`financeiro/columns.tsx:39`, `documentos/columns.tsx:113`: `text-neutral-900 ... dark:text-neutral-50`). `slate` and `neutral` are visually distinct Tailwind palettes (slate carries a blue-gray tint; neutral is true gray) — having 3 of 5 "unified DataTable" screens render one hue family and 2 of 5 render another is a real, freshly-introduced inconsistency, not a pre-existing one carried forward unmodified (these files did not exist before this phase).

This pairs with a second split: Clientes/Processos/Pareceres's primary identity links use a bold weight + color-swap-on-hover idiom (`font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors`), while Financeiro/Documentos's primary links use a medium weight + plain-underline idiom with zero color feedback (`font-medium text-neutral-900 hover:underline dark:text-neutral-50`, `financeiro/columns.tsx:39`, `documentos/columns.tsx:113`) — two different interactive-link treatments for what is presented as the same "primary identity column" role across a supposedly unified pattern.

Positive: the explicit accent-reservation rule is fully respected — `grep -rn "text-primary\|bg-primary\|border-primary"` across `data-table/` + all 5 `columns.tsx` returns **zero** hits, matching the UI-SPEC's "DataTable does not join the accent list" rule exactly. No hardcoded hex/`rgb()` colors found either. The violation here is internal cross-screen consistency, not accent misuse.

### Pillar 4: Typography (2/4)

The mandated header fix is correctly and uniformly shipped: `grep -c 'text-xs font-semibold uppercase tracking-wider text-muted-foreground' data-table-column-header.tsx` = 1 (single shared implementation, `data-table-column-header.tsx:35,38,51`), consumed identically by all 5 screens' `DataTableColumnHeader` usage — no drift risk since it's one file. This closes the pre-existing 10px/3-weight header inconsistency exactly as specified.

However, quantifying font sizes actually in use across the shared pattern + all 5 `columns.tsx`:

```
6 text-[10px]
4 text-[11px]
3 text-xs
3 text-sm
```

The off-scale sizes (`10px`/`11px`, both below the declared 12px Label floor) now outnumber the on-scale `text-xs` usage. Two of the `text-[10px]` hits are **brand-new this phase**, not ported-forward legacy: `financeiro/columns.tsx:105` (the Estado Badge — the phase's own explicitly-mandated "close Financeiro's Badge gap" fix) and `documentos/columns.tsx:125,153` (Tipo and Confidencialidade Badges — both invented from scratch this phase, confirmed via `104-05-SUMMARY.md`: "no prior mapping existed"). Meanwhile Processos' Estado Badge (`processos/columns.tsx:101-106`) and Área Jurídica Badge (`:74-77`), and Clientes' Tipo Badge (`clientes/columns.tsx:178`) — all built in this same phase — correctly ship with **no** size override, landing on the Badge component's own `text-xs` (12px) default. The same phase that wrote "do not preserve either off-scale value 'for continuity'" for headers reintroduced the identical off-scale pattern, at a higher occurrence count, in brand-new Badge cells.

Font-weight distribution (`font-bold` ×15, `font-medium` ×10, `font-semibold` ×1, `font-normal` ×1 — 4 distinct weights) is noted for completeness but not double-penalized here since the UI-SPEC's 2-weight (400/600) contract was scoped explicitly to header labels, not cell body content, and this pattern was ported from pre-existing per-screen styling rather than invented this phase.

### Pillar 5: Spacing (3/4)

The declared 7-point scale (`xs`4/`sm`8/`md`16/`lg`24/`xl`32/`2xl`48/`3xl`64) states "Exceptions: none." Two off-scale values were found in the shared, foundational files (used by all 5 screens, so any deviation here has system-wide reach):

- `data-table.tsx:79`: `<div className="flex items-center justify-end px-6 py-3">` — `py-3` = 12px, not one of the 7 declared values (nearest are `sm`=8 or `md`=16).
- `data-table-column-header.tsx:48`: `gap-1.5` = 6px, also off-scale (nearest are `xs`=4 or `sm`=8).

Both are narrow, isolated deviations (2 instances in 2 files) rather than a pervasive pattern — the mandated footer spacing precedent (`px-6 py-4`, matching Clientes'/Processos' pre-existing decorative footer) is correctly reused verbatim in `data-table-pagination.tsx:35`, and the bulk of spacing across all 5 `columns.tsx` files uses on-scale values (`gap-1`=4px, `mt-1`=4px, `gap-3`=12px is itself borderline but matches the pre-existing `ClienteAcoesCell`/row layout convention, not newly invented). Scored 3/4 rather than lower because the deviation, while a genuine literal violation of "Exceptions: none," is small in surface area and does not visibly break any layout.

### Pillar 6: Experience Design (2/4)

- **Documentos' Processo/Cliente columns never resolve to names.** `documentos/columns.tsx:133-143` renders `row.original.processo_id ?? "—"` and `row.original.cliente_id ?? "—"` directly — confirmed the `Documento` type (`types/documentos.ts`) has no display-name fields, only raw `processo_id`/`cliente_id` foreign keys, and `documentos/page.tsx`'s import list (lines 3-20) confirms `useClientes`/`useProcessos` are not imported at all in this screen, unlike `financeiro/page.tsx` which builds exactly this kind of lookup Map for the same phase. This is a genuine usability gap in a legal case-management tool (raw UUIDs are meaningless to end users) that the live human checkpoint could not have caught: `104-06-SUMMARY.md` states outright that "this dev tenant has zero documents" and the Confidencialidade mapping was "closed via code-level verification instead" — meaning this exact code path (a populated Documentos row) was never actually rendered and looked at by a human during that checkpoint. My adversarial read surfaces a real, live defect in that unverified path.
- **Processos' DataTable has no responsive/mobile gate**, unlike Clientes/Pareceres/Financeiro/Documentos (all four wrap `<DataTable>` in `hidden md:block` + a dedicated `md:hidden` card branch — confirmed via grep across all 5 `page.tsx`). `processos/page.tsx:308-315` renders `<DataTable columns={processoColumns} data={processos.data} />` completely unconditionally. This predates the phase (Processos never had a mobile-card split, per `104-03-SUMMARY.md`'s own documented deviation), but the phase measurably increased the UI surface now exposed at all viewport widths: a brand-new `DataTableViewOptions` icon-button toolbar and a brand-new **functional** pagination footer (rows-per-page `Select`, live Anterior/Seguinte) did not exist on this screen at all before — they now render unguarded on a 375px viewport alongside 6 sortable-header buttons per row, with no card-based fallback.
- Positive, confirmed unchanged/solid: RBAC gating (`canEditClientes`/`canEditDocumentos` threaded correctly into every Ações cell), `window.confirm()` destructive-action guards preserved 1:1 in every migrated screen, loading/error/empty outer guards left untouched per the locked architecture decision, and no `getFilteredRowModel()` anywhere (verified: zero hits repo-wide) — the core client-side-sort-only architecture is intact and correctly implemented.

---

## Registry Safety

`components.json` exists (shadcn initialized), but `104-UI-SPEC.md`'s Registry Safety table lists only official-registry blocks (`Table`, `Select`, `DropdownMenu`, `Tooltip`, `Button`, `Pagination` — all already installed/vetted in Phase 101/102) and explicitly states "No third-party shadcn registries — excluded for this entire milestone." `@tanstack/react-table` is a plain npm dependency (not a shadcn `--registry` block); its legitimacy gate was already run and approved in `104-01-SUMMARY.md` prior to this phase's execution. Per the registry-audit trigger condition (third-party registries must be listed), this audit does not apply this cycle. Registry audit: 0 third-party shadcn blocks used this phase, no flags.

---

## Files Audited

- `web/src/components/shared/data-table/data-table.tsx`
- `web/src/components/shared/data-table/data-table-column-header.tsx`
- `web/src/components/shared/data-table/data-table-pagination.tsx`
- `web/src/components/shared/data-table/data-table-view-options.tsx`
- `web/src/components/ui/table.tsx`
- `web/src/components/ui/button.tsx`
- `web/src/components/ui/badge.tsx`
- `web/src/lib/utils.ts`
- `web/src/app/(dashboard)/clientes/columns.tsx`
- `web/src/app/(dashboard)/clientes/page.tsx`
- `web/src/app/(dashboard)/processos/columns.tsx`
- `web/src/app/(dashboard)/processos/page.tsx`
- `web/src/app/(dashboard)/pareceres/columns.tsx`
- `web/src/app/(dashboard)/pareceres/page.tsx`
- `web/src/app/(dashboard)/financeiro/columns.tsx`
- `web/src/app/(dashboard)/financeiro/page.tsx`
- `web/src/app/(dashboard)/documentos/columns.tsx`
- `web/src/app/(dashboard)/documentos/page.tsx`
- `web/src/types/documentos.ts`
- `.planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-UI-SPEC.md`
- `.planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-CONTEXT.md`
- `.planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-PATTERNS.md`
- `.planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-01-SUMMARY.md` through `104-06-SUMMARY.md`
- `.planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-REVIEW.iter3.md`, `104-REVIEW-FIX.iter3.md`
