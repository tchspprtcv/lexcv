# Phase 107 — UI Review

**Audited:** 2026-07-17
**Baseline:** 107-UI-SPEC.md (approved design contract)
**Screenshots:** not captured — dev server responded `200` on `http://localhost:3000`, but the Playwright CLI is not installed in `web/node_modules` (`npx playwright --version` → `ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL`), and all 6 audited routes sit behind cookie-based auth that a one-shot CLI screenshot cannot establish. Audit performed via direct source inspection of the implemented components instead. Note: this phase's own 107-06 human-verify checkpoint already captured live, authenticated, end-to-end interaction evidence for the Combobox (both modes) — see `deferred-items.md` — which this review treats as corroborating evidence, not a substitute for its own independent code read.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | Every new Combobox string (placeholders, search placeholders, create-item label, empty states) matches the locked contract verbatim; zero generic labels introduced |
| 2. Visuals | 3/4 | Sound hierarchy and no icon-only-button violations, but the "Todos" sentinel option defeats the trigger's own placeholder/selected visual distinction, and one sibling `rounded-none` inconsistency exists in the Cliente ficha dialog |
| 3. Color | 3/4 | New `Progress` accent-reservation entry (`bg-primary`) implemented exactly per spec with zero leakage into Select/NativeSelect/Combobox; pre-existing hardcoded `blue-600`/`blue-400` link color sits untouched in a file this phase heavily edited |
| 4. Typography | 3/4 | The 4 migrated components (Progress/NativeSelect/Select/Combobox) are strictly on-scale; the surrounding pages this phase substantially rewrote still carry pre-existing `font-bold`/`font-medium`/`text-base`/`text-xl` scale drift |
| 5. Spacing | 3/4 | The one new spacing value this phase introduces (`w-80` Combobox popover) is implemented exactly as declared; pre-existing arbitrary bracket values (`text-[10px]`, `min-h-[44px]`) remain scattered through the same files |
| 6. Experience Design | 3/4 | Excellent state/disabled/RBAC coverage confirmed by direct read across all 6 files, but the 2 new closed-list Combobox filters — unlike the sibling `NativeSelect` this same phase ships in `financeiro/novo/page.tsx` — surface no loading affordance while `useProcessos()`/`useClientes({})` are still in flight, risking a misleading "not found" empty state |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **Closed-list Combobox filters show no loading state while options are still fetching** (`documentos/page.tsx:64-91` wires `processoOptions`/`clienteOptions` straight from `processos.data ?? []` / `clientes.data ?? []` with no `isPending` handling passed into the `Combobox`) — user impact: a user who opens the Processo/Cliente filter and searches for a real, existing processo/cliente during the brief client-side fetch window sees `"Nenhum processo encontrado."` / `"Nenhum cliente encontrado."` even though the record exists and simply hasn't loaded yet — a false-negative empty state. Concrete fix: thread `processos.isPending`/`clientes.isPending` into the `Combobox` (add an optional `loading` prop that shows `"A carregar..."` in place of `CommandEmpty`, mirroring the pattern `financeiro/novo/page.tsx:109-113` already established in this same phase for `NativeSelect`).

2. **The "Todos" sentinel option defeats the trigger's own placeholder/selected color distinction** (`combobox.tsx:51,64-66`: `selected = options.find(o => o.value === value)` matches the default `""` field value against the `{value: "", label: "Todos os processos"}` entry seeded at `documentos/page.tsx:77,87`, so `isPlaceholder` is `false` from first paint) — user impact: none functionally, but it silently drops the spec's declared contract ("placeholder in `text-muted-foreground`") for both Documentos filter triggers — the default "Todos..." state always renders at full foreground strength instead of the muted, not-yet-filtered look every other Popover-trigger control in the app uses for its unselected state. Concrete fix: in the 2 closed-list call sites, treat the sentinel value as "no selection" explicitly (e.g. pass `value={field.value || undefined}` and special-case `undefined` → placeholder path) rather than modeling "Todos" as a literal option that matches on mount.

3. **Sibling `rounded-none` convention is inconsistent inside the Cliente ficha's own "Adicionar Documento Entregue" dialog** (`clientes/[id]/page.tsx:1307,1336,1352,1362`: the new `Combobox` trigger carries `triggerClassName="rounded-none"`, but its own `DialogTrigger`/`Cancelar`/`Confirmar` `Button`s in the identical dialog do not — contrast with `processos/[id]/page.tsx:2573,2604,2623,2633` where all 4 controls in the equivalent dialog uniformly carry `rounded-none`) — user impact: zero today (global `--radius: 0rem` makes every one of these classes a no-op), but it's a literal-class hygiene regression against the exact rationale the UI-SPEC itself gives for adding `rounded-none` to the Combobox ("matches the literal-class convention already used by sibling elements in the same Dialog") — that convention doesn't actually hold in this file. Concrete fix: either add `rounded-none` to the 3 sibling buttons in `ClienteDocumentosEntreguesTab`'s dialog for consistency, or drop it from the Combobox trigger there since no sibling precedent exists to match.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

- Creatable-mode strings match exactly: trigger placeholder `"Selecionar ou escrever tipo..."`, search placeholder `"Pesquisar ou escrever novo tipo..."`, empty state `"Nenhuma sugestão."` — confirmed identical at `processos/[id]/page.tsx:2601-2603` and `clientes/[id]/page.tsx:1333-1335`.
- Create-item label uses the shared component's default `Usar "${query}"` (`combobox.tsx:32`), matching the contract's `Usar "{query}"` exactly — no per-call-site override needed or made.
- Closed-list filter strings match exactly: `"Todos os processos"` / `"Todos os clientes"` (`documentos/page.tsx:140,162`), search placeholders `"Pesquisar processo por número ou título..."` / `"Pesquisar cliente por nome..."` (`:141,163`), empty states `"Nenhum processo encontrado."` / `"Nenhum cliente encontrado."` (`:142,164`) — all verbatim.
- Upload progress label `"A enviar..."` + `"{progresso}%"` is untouched and byte-identical across all 3 sites (`documentos/novo/page.tsx:184-185`, `processos/[id]/page.tsx:2611-2612`, `clientes/[id]/page.tsx:1343-1344`), as required — DOF-01 was scoped to the bar only.
- No generic `Submit`/`Click Here`/`OK` strings found in any of the 8 audited files; `Cancelar`/`Guardar`/`Filtrar`/`Limpar` are pre-existing, unchanged, and are the established Portuguese domain vocabulary, not generic English placeholders.

### Pillar 2: Visuals (3/4)

- Clear page-level focal points on both list pages: `h1` + subtitle + `Card`-sectioned "Filtros"/"Resultados" (Documentos) and 4 KPI cards + filter row + "Honorários" table (Financeiro) — good size/weight differentiation (`text-2xl font-semibold` headings vs `text-sm`/`text-xs` body/labels, `text-xl font-semibold` KPI values vs `text-xs font-medium` KPI labels at `financeiro/page.tsx:199,204`).
- No icon-only buttons introduced this phase: the Combobox's `ChevronsUpDown` (`combobox.tsx:101`) and `CommandInput`'s baked-in `SearchIcon` are both decorative companions to visible text, never the sole content of an interactive element.
- **Finding (this phase's regression):** `combobox.tsx:64-66`'s `isPlaceholder` logic never fires for the 2 closed-list filters, because the "Todos" option is seeded into the same `options` array the trigger matches `value` against (`documentos/page.tsx:76-91`). This is dead code for these 2 call sites specifically (works correctly for the 2 creatable sites, where there's no synthetic sentinel option). See Top 3 Fix #2.
- **Finding (minor, this phase):** `rounded-none` sibling-consistency break in the Cliente ficha's upload dialog — see Top 3 Fix #3.

### Pillar 3: Color (3/4)

- New accent-reservation entry implemented exactly as specified: `Progress`'s `Indicator` uses `bg-primary` (`progress.tsx:24`), zero per-call-site override at any of the 3 sites — confirmed the hardcoded `bg-blue-600` hand-rolled fill is fully gone from `documentos/novo/page.tsx`, `processos/[id]/page.tsx`, `clientes/[id]/page.tsx`.
- Confirmed **zero** `bg-primary`/`text-primary`/`border-primary` usage anywhere in the 2 new `Select` filters, the 2 `NativeSelect` fields, or the `Combobox` composition itself — matches the spec's explicit exclusion list.
- `--radius: 0rem` interaction confirmed consistent: no component in this phase needed a radius override.
- **Finding (pre-existing, not introduced by 107):** `documentos/page.tsx:293` still hardcodes `text-blue-600 dark:text-blue-400` on the mobile-card "Download" link, sitting one screen away from the newly-tokenized filter Card — not part of this phase's migration scope, but flagged since Color pillar audits the whole file, not just the diff.
- **Finding:** the "Todos" sentinel color deviation described under Visuals is also a Color-pillar contract miss — the spec's own table states "placeholder in `text-muted-foreground`" as a binding rule for the trigger, and it silently doesn't apply for the default state of both Documentos filters.

### Pillar 4: Typography (3/4)

- Grep across `documentos/**` + `financeiro/**`: `text-sm` ×57, `text-xs` ×9, `text-2xl` ×7, `text-base` ×3, `text-xl` ×1 — 5 distinct sizes in use against the declared 4-role scale (Body 14/Label 12/Heading 24/Display 30).
- Font weights: `font-medium` ×18, `font-semibold` ×8, `font-bold` ×8 — 3 distinct weight utilities in use against the declared 2 (400 regular / 600 semibold).
- The 4 components this phase actually migrated are clean: `SelectTrigger`/`SelectItem`/`NativeSelect`/`CommandInput`/`CommandItem` all render at `text-sm` unweighted, exactly as the spec requires, and the Combobox trigger correctly overrides to `font-normal` (`combobox.tsx:96`) matching the `DatePickerField` precedent.
- **Finding (pre-existing, not introduced by 107, but present in every touched file):** `font-bold` on data-table/mobile-card badges (`documentos/columns.tsx:142,194`, `documentos/page.tsx:269,274`, `financeiro/columns.tsx:105`, `financeiro/page.tsx:317,324,335`), `text-xl` on the Financeiro KPI values (`financeiro/page.tsx:204`), and `text-base` on 3 mobile-only input overrides (`financeiro/novo/page.tsx:142,152,161`) all sit outside the declared scale. The UI-SPEC itself flags the `font-medium` filter-label instance as known pre-existing debt (Typography section, "Pre-existing, out-of-scope note") — the `font-bold`/`text-xl`/`text-base` instances are the same class of debt, just not individually called out.

### Pillar 5: Spacing (3/4)

- The single new spacing value this phase introduces — Combobox `PopoverContent` at `w-80 p-0` (`combobox.tsx:104`) — matches the declared on-scale exception exactly, and every new form/filter block uses the established scale (`grid gap-4 sm:grid-cols-3` filter row, `space-y-4` form bodies, `space-y-2` field groups, `space-y-1` progress label stack) with zero deviation.
- **Finding (pre-existing, not introduced by 107):** arbitrary bracket values remain scattered through the same files this phase substantially rewrote: `text-[10px]`/`text-[11px]` on badges and metadata rows (`documentos/page.tsx:274,280,284`, `financeiro/page.tsx:324,330,339`), `min-h-[44px]` on mobile action buttons (`documentos/page.tsx:293,304`), `min-h-[48px]` on the Financeiro create-form submit button (`financeiro/novo/page.tsx:172`). These predate the phase (mobile-card/touch-target patterns) and are functionally defensible (44px is the standard touch-target minimum), but they are non-scale values the audit is required to surface.

### Pillar 6: Experience Design (3/4)

- Loading/error/empty state coverage is comprehensive and correctly layered everywhere this phase touched: `documentos/page.tsx:190-199` (pending/error/empty), `financeiro/page.tsx:283-302` (pending/error/global-empty/**filtered**-empty — 4 distinct states, better than average), `documentos/[id]/page.tsx:107-113`, `financeiro/[id]/page.tsx:363-378` all correctly gate on `isLoading`/`isError`/not-found before rendering content.
- Disabled-state gating is thorough: every mutating `Button` correctly composes `isSubmitting || mutation.isPending || permissions.isLoading || !canX` (e.g. `documentos/novo/page.tsx:254`, `financeiro/novo/page.tsx:173`, `financeiro/[id]/page.tsx:478`) — the RBAC `isFetched` fix bundled into this phase is present and correct at all 6 gates (`documentos/page.tsx:35`, `documentos/novo/page.tsx:122`, `documentos/[id]/page.tsx:25`, `financeiro/page.tsx:104`, `financeiro/novo/page.tsx:26`, `financeiro/[id]/page.tsx:80`).
- The `Combobox`'s own `disabled` prop is correctly wired at both creatable call sites (`processos/[id]/page.tsx:2605`, `clientes/[id]/page.tsx:1337`, both `disabled={upload.isPending}`), preventing tipo changes mid-upload — a real, correct interaction-state guard.
- Destructive actions are unaffected and intact: `window.confirm("Apagar este documento?")` for document delete (`documentos/page.tsx:252`, `documentos/[id]/page.tsx:60`), full `AlertDialog` confirmations for honorário/pagamento delete (`financeiro/[id]/page.tsx:333-354,522-546`) — correctly out of this phase's scope per the UI-SPEC and untouched.
- **Finding (this phase's own inconsistency):** the 2 new closed-list Combobox filters (`documentos/page.tsx:135-145,157-167`) pass `processoOptions`/`clienteOptions` straight through with no `isPending` awareness, unlike the sibling `NativeSelect` this same phase ships in `financeiro/novo/page.tsx:109-113`, which explicitly renders `"A carregar..."`/`"Erro ao carregar"` inside the control while its data source is in flight. See Top 3 Fix #1.

---

## Registry Safety

Registry audit: `web/components.json` exists (shadcn initialized), but `107-UI-SPEC.md`'s Registry Safety table lists only `shadcn official` blocks (`Progress`, `Select`, `NativeSelect`, `Popover`, `Command`, `Button`, `Input` — all pre-existing since Phase 101, zero new `add` command this phase, `cmdk` already vetted as a transitive shadcn dependency). No third-party registry rows exist to audit — 0 third-party blocks checked, registry audit skipped per the "no third-party registries" exit condition.

---

## Files Audited

- `web/src/components/shared/combobox.tsx` (new shared component, first Command consumer)
- `web/src/components/ui/command.tsx`, `web/src/components/ui/progress.tsx`, `web/src/components/ui/native-select.tsx` (primitives, read for contract verification)
- `web/src/app/(dashboard)/documentos/page.tsx`
- `web/src/app/(dashboard)/documentos/novo/page.tsx`
- `web/src/app/(dashboard)/documentos/[id]/page.tsx`
- `web/src/app/(dashboard)/financeiro/page.tsx`
- `web/src/app/(dashboard)/financeiro/novo/page.tsx`
- `web/src/app/(dashboard)/financeiro/[id]/page.tsx`
- `web/src/app/(dashboard)/processos/[id]/page.tsx` (skimmed: `ProcessoDocumentosTab` section, lines ~2540-2658, plus a full-file `rounded-none` grep for sibling-convention cross-check)
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` (skimmed: `ClienteDocumentosEntreguesTab` section, lines ~1280-1400, plus a full-file `rounded-none` grep)
- `web/src/schemas/documentos.ts` (spot-checked `processo_id`/`cliente_id` typing to confirm the spec's "no schema change needed" claim)
- `.planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-UI-SPEC.md`
- `.planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-CONTEXT.md`
- `.planning/phases/LEXCV-107-m-dulos-documentos-financeiro/deferred-items.md`
