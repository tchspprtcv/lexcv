# Phase 108 — UI Review

**Audited:** 2026-07-17
**Baseline:** `108-UI-SPEC.md` (design contract, status: draft, Checker Sign-Off pending)
**Screenshots:** captured (dev server running at `localhost:3000`, authenticated session available via CLI capture) — see `.planning/ui-reviews/108-20260717-111503/`. The "Histórico de Versões" Accordion+Tooltip timeline could **not** be captured live — this tenant's `pareceres` list is empty (`Nenhuma solicitação de parecer encontrada`), so no `ParecerVersao` records exist to render the timeline against. That pattern was audited via source-code cross-reference against `accordion.tsx`/`tooltip.tsx` primitive definitions and the UI-SPEC's line-by-line composition spec instead.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | Every new string (sentinel, placeholders, tooltip copy) matches the Copywriting Contract verbatim; no generic labels introduced. |
| 2. Visuals | 3/4 | Visual hierarchy faithfully preserved, but the `prioridade` NativeSelect visibly defaults to "Alta" on screen although the form's documented default is "Média" (screenshot-verified). |
| 3. Color | 3/4 | Zero new hardcoded colors or accent usage on the migrated components, but the 3 files still carry 23+ pre-existing raw `bg-blue-600`/`ring-blue-500`/hex-color utilities bypassing the `--primary`/`--destructive` tokens. |
| 4. Typography | 3/4 | New Accordion/Tooltip/Select/NativeSelect typography matches shipped primitive defaults and the declared scale exactly; module-wide still carries 6 font sizes / 3 weights of pre-existing, UI-SPEC-acknowledged debt. |
| 5. Spacing | 4/4 | No new arbitrary values introduced; the marker-column `pt-4` rework and the retained `left-[5px]` connecting-line offset match the spec's prescribed recipe exactly. |
| 6. Experience Design | 2/4 | Excellent, textbook-correct `tabIndex={0}`+`aria-label` a11y work on the first-of-kind Tooltip-on-non-focusable-marker pattern, but the same `prioridade` default-value bug is a live, unedited-submission data-integrity defect, and 3 Select fields in "Pesquisa Avançada" remain unlabeled for assistive tech. |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **`prioridade` `NativeSelect` visually and functionally defaults to "Alta," not the documented "Média"** (`web/src/app/(dashboard)/pareceres/nova/page.tsx:178-184`, form default at line 61) — Screenshot-verified: on `/pareceres/nova` the "Prioridade" control renders with **"Alta" pre-selected**, not "Média." Because `<NativeSelect {...form.register("prioridade")}>` is an uncontrolled native `<select>` with no `defaultValue`/`selected` marker, the DOM simply shows its first `<option>` (`ALTA`) regardless of `useForm({defaultValues: {prioridade: "MEDIA"}})` or the Zod schema's `.default("MEDIA")`. Any user who doesn't explicitly touch this field submits `"ALTA"` — silently wrong data, and visually misleading versus the app's own documented intent. **Fix:** add `defaultValue="MEDIA"` to the `<NativeSelect id="prioridade">` element (matching the RHF/schema default), or reorder options so `MEDIA` renders first with an explicit `selected` marker. Note: this bug pre-dates Phase 108 (present in the raw `<select>` before migration, confirmed via `git show b19f946`), but it lives in exactly the field this phase touched and remains unfixed after the swap.

2. **3 Select-based fields in "Pesquisa Avançada" still have no accessible label association** (`web/src/app/(dashboard)/pareceres/page.tsx:262-264` Cliente, `:282-284` Advogado, `:302-305` Estado) — Each renders a bare `<label>` with no `htmlFor`/`id` pairing to its `SelectTrigger`, so screen-reader and keyboard users cannot programmatically associate the field name with the control (Radix `Select` doesn't accept a native `htmlFor` target). This is the still-open `IN-05` finding from `108-REVIEW.md`'s code-review pass — its suggested fix was never applied. **Fix:** give each `<label>` an `id` (e.g. `id="pesquisa-cliente-label"`) and add `aria-labelledby="pesquisa-cliente-label"` to the corresponding `SelectTrigger`.

3. **Hardcoded color utilities bypass the project's token system across all 3 files** — 23 occurrences of `bg-blue-600`/`hover:bg-blue-700`/`ring-blue-500`/`text-red-600` (e.g. `page.tsx:135,165,258,333,343,353,376`; `nova/page.tsx:134,139,164,173,204`; `[id]/page.tsx:202,278,415,455,552,596`) plus 7 occurrences of the literal hex `bg-[#020617]` (e.g. `page.tsx:148,157,258,333,343,372`; `nova/page.tsx:27`) instead of the `--primary`/`--destructive`/`--card` design tokens the UI-SPEC itself documents as the 60/30/10 system. This is pre-existing debt, not introduced by Phase 108's migration (the new `Select`/`NativeSelect`/`Tooltip`/`Accordion` call sites add **zero** new hardcoded colors — verified clean), but it remains visible, systemic, and untouched despite this phase editing all 3 files. **Fix:** replace with `bg-primary hover:bg-primary/90`, `focus-visible:ring-ring`, `text-destructive`, and `bg-card`/`bg-popover` token classes.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

- **Select "todos" sentinel** — all 6 filter `Select`s (`page.tsx:184,203,223,272,292,312`) render `<SelectItem value="todos">Todos</SelectItem>` verbatim, matching the Copywriting Contract's declared sentinel copy exactly.
- **Tooltip copy** — `versaoTooltipLabel` (`[id]/page.tsx:175-180`) returns exactly `"Versão atual"` / `"Versão entregue"` / `"Versão anterior"` per the state-derivation logic prescribed in the UI-SPEC (`108-UI-SPEC.md` lines 106-116), and the same function is called for both the `aria-label` (`:306`) and `TooltipContent` children (`:310`) — guaranteed to never drift out of sync.
- **NativeSelect placeholders** — `"A carregar..."`/`"Selecionar cliente"` (`nova/page.tsx:126`), `"Nenhum processo associado"` (`:146`), `"Atribuir mais tarde"` (`:190`) all match the contract verbatim.
- No generic `Submit`/`Click Here`/`OK` strings found via grep across the 3 files; `"Cancelar"` is the project's established Portuguese convention, not a generic English leftover.
- No new empty/error-state copy shipped this phase (correctly — the spec states none was required); existing `"Nenhuma solicitação de parecer encontrada"`, `"Nenhum resultado encontrado"`, `"Nenhuma versão ainda"` are unchanged and reused as declared.

### Pillar 2: Visuals (3/4)

- Filter grid, sticky "Histórico de Versões" card (`[id]/page.tsx:267`, `sticky top-6`), and marker/connecting-line column are pixel-for-pixel preserved per the spec's "No new page-level focal point" directive — confirmed both by code inspection and live screenshot of `/pareceres` (desktop 1440×900 and mobile 375×812).
- `AnexoLink` (`[id]/page.tsx:104-133`) correctly stays text+icon (`Paperclip` + "Descarregar anexo") and does **not** gain a `Tooltip`, matching the Phase 102 icon-only-only convention the spec reaffirms.
- **Defect:** `prioridade` `NativeSelect` (`nova/page.tsx:178-184`) shows "Alta" as the visually-selected option on page load (confirmed via screenshot at `.planning/ui-reviews/108-20260717-111503/pareceres-nova.png`) despite the field's actual intended/documented default being "Média" — a visible mismatch between what the user sees and what the form/schema declares as default. See Top Fix #1.
- Chevron auto-render on `AccordionTrigger` (no manual icon import needed, confirmed absent from `[id]/page.tsx` imports) — matches spec exactly, zero visual redundancy.

### Pillar 3: Color (3/4)

- Grep for `text-primary|bg-primary|border-primary|ring-primary` across all 3 files returned **zero matches** — confirms the spec's claim that this phase adds no new accent-reservation entries; `Select`/`NativeSelect`/`Tooltip`/`Accordion` all inherit muted/popover/foreground tokens only, and the timeline marker correctly stays `bg-slate-400 dark:bg-slate-500` (not `bg-primary`) per the spec's explicit rationale (Accordion's own expanded state is the primary signal).
- Grep for hardcoded hex/`rgb()` returned only pre-existing `bg-[#020617]` (7 occurrences, dark-mode surface color) — none on any newly-migrated `Select`/`NativeSelect` element; all sit on unrelated `Button`/`input`/`CardContent` elements untouched by PARC-18/19/20.
- `bg-blue-600`/`ring-blue-500`/`text-red-600` (23 occurrences across the 3 files) are raw Tailwind palette colors doing the job of `--primary`/`--ring`/`--destructive` tokens — a real, visible deviation from the project's declared 60/30/10 token system, but confirmed pre-existing (not touched by this phase's scope) via `git log`/`git show` on the touched line ranges. See Top Fix #3.
- `--radius: 0rem` resolution confirmed consistent — every new `rounded-none`/`rounded-md` class on the migrated components resolves visually to `0`, matching `globals.css:75` project-wide.

### Pillar 4: Typography (3/4)

- `AccordionTrigger`'s header (`"Versão N"` / timestamp, `[id]/page.tsx:319-324`) renders at `text-sm font-medium` / `text-xs text-slate-500 dark:text-slate-400`, matching the primitive's own shipped trigger typography and the spec's Typography section verbatim — one trivial addendum: the "Versão N" paragraph carries `text-slate-900 dark:text-white` (not present in the spec's literal JSX snippet), a harmless, purely-cosmetic addition consistent with the rest of the page's heading-color convention.
- `TooltipContent` renders at its own shipped `text-xs` (12px) inverted (`bg-foreground`/`text-background`) — identical to the pre-existing "Ver detalhes" tooltip in `pareceres/columns.tsx`, confirmed no new type treatment introduced.
- `SelectTrigger`/`SelectItem`/`NativeSelect` all render unweighted `text-sm` (14px), a like-for-like match with the legacy raw `<select>`s they replace — zero visible type-scale change from the migration itself, as the spec predicts.
- Module-wide grep (`text-(xs|sm|base|lg|xl|2xl|3xl|...)`) across the 3 files surfaces 6 distinct sizes in active use (`text-xs`, `text-sm`, `text-base`, `text-lg`, `text-2xl`, `text-3xl`) and 3 weights (`font-bold`/700, `font-medium`/500, default/400) against the declared 4-role/2-weight (400/600) scale — all of this is the h1 (`text-3xl font-bold`/`text-2xl font-bold`), CTA `font-bold` overrides, and `11px` label-chip debt the UI-SPEC itself explicitly flags as pre-existing and out of this phase's scope (not touched by PARC-18/19/20). Scored 3/4 rather than 4/4 because the audited files, as they stand today, do exceed the declared scale — even though phase 108 is not the cause.

### Pillar 5: Spacing (4/4)

- Marker-column rework matches the spec's prescribed recipe exactly: outer row's `py-4` removed, marker column gets `pt-4` instead (`[id]/page.tsx:301`), `AccordionTrigger`'s own shipped `py-4` supplies the header's vertical rhythm — verified against `accordion.tsx:45`.
- The connecting line's `top-3 bottom-0 left-[5px]` (`[id]/page.tsx:313`) is the one retained arbitrary value, and it's the same pre-existing offset (paired with the `h-2.5 w-2.5` / 10px dot, so `5px` is its exact center) — not a new value, not sloppy, precisely justified per the spec's Verified-against-source finding #7.
- Grep for `space-y-|gap-|p[xytblr]?-|m[xytblr]?-` in `[id]/page.tsx` returns only standard-scale values (`gap-2/3/6`, `space-y-2/3/4/6`, `mt-1/2`, `py-4/6/12`) — no new off-scale spacing introduced by this phase.
- Only other arbitrary values present are the 11 pre-existing `text-[11px]` label chips and one `max-sm:min-h-[48px]` touch-target on `nova/page.tsx:204` — both pre-date this phase and are unrelated to the migrated components.

### Pillar 6: Experience Design (2/4)

**Strength — first-of-kind Tooltip-on-non-focusable-marker pattern, rigorously verified:**
- The marker `<span>` (`[id]/page.tsx:304-308`) correctly carries `tabIndex={0}` and `aria-label={versaoTooltipLabel(versao, index)}`, wrapped in `<TooltipTrigger asChild>` — this satisfies WCAG SC 1.4.13 (content on hover/focus must also be reachable via keyboard focus), which Radix `Tooltip` supports out of the box for any focusable trigger (`onFocus`/`onBlur` handlers are baked into `TooltipPrimitive.Trigger`, confirmed in `tooltip.tsx`). The `aria-label` and `TooltipContent` children call the exact same helper function, so they can never drift out of sync. This is genuinely careful work given there was zero in-repo precedent for tooltip-on-a-non-button element (every prior consumer — sidebar `LogOut`, `pareceres/columns.tsx`'s icon `Button` — wraps an already-focusable element).
- Every marker gets a tooltip (not just current/delivered), a deliberate, spec-authorized choice for predictable UX rather than a silently-inconsistent subset.

**Strength — first-of-kind Accordion default-open behavior, verified correct:**
- `defaultOpenVersaoId` derivation (`[id]/page.tsx:171-173`) exactly matches the spec's prescribed logic, and the `key={defaultOpenVersaoId}` addition (added post-hoc as fix `WR-04`, commit `16edcb5`) correctly forces the uncontrolled `Accordion` to re-seed its `defaultValue` when the intended default-open version changes (e.g., after "Entregar Parecer" flips `isConcluido`) — verified this fix doesn't fire prematurely, since the whole `Accordion` only mounts after `versoes.data` is loaded and non-empty, by which point `isConcluido` is already stable.
- `AccordionItem` is correctly nested **inside** the per-version marker wrapper (not a flat sibling), preserving the dot+connecting-line column exactly as specified.

**Defects:**
- **`prioridade` NativeSelect default-value bug** (see Top Fix #1) is, in this pillar, a genuine task-completion defect: a user who creates a parecer without touching "Prioridade" — a very plausible path, since the field is rendered as if "Alta" were already a deliberate, pre-set choice — silently submits `"ALTA"` instead of the intended `"MEDIA"` default. This is a live, reproducible, screenshot-confirmed defect in code this phase directly modified.
- **IN-05 accessibility gap remains open** (see Top Fix #2) — 3 Select fields in "Pesquisa Avançada" have no `htmlFor`/`aria-labelledby` pairing, a real (if narrow) screen-reader/keyboard-navigation regression risk that predates this phase but was left unaddressed despite the exact lines being touched by the Select migration.
- Otherwise, state coverage is thorough and consistent: loading (`resultsLoading`, `versoes.isLoading` skeletons, `clientes.isPending`, `pesquisa.isFetching` → `"A pesquisar..."`), error (`resultsError`, `versoes.isError`, `clientes.isError`, `formError`/`serverError`/`entregaError`), empty (3 distinct empty-state messages, all correctly gated), and disabled states (all mutation-in-flight buttons/selects) are present and correctly wired throughout all 3 files.
- Destructive-action confirmation (`EntregarParecerDialog`'s `AlertDialog`, `"Esta ação é irreversível..."`) is unchanged and correctly preserved through the `NativeSelect` swap, including the `disabled={entregar.isPending}` guard on `AlertDialogCancel`/`NativeSelect`/`AlertDialogAction` and the WR-08 fix (commit `7055e0c`) that resets `selectedVersaoIdState` on dialog close.

---

## Registry Safety

`components.json` exists, but `108-UI-SPEC.md`'s Registry Safety table lists only `shadcn official` (no third-party registries: `Select`, `NativeSelect`, `Tooltip`, `Accordion` all already installed since Phase 101, zero new `shadcn add` commands this phase). Registry audit: 0 third-party blocks checked, no flags — full registry audit skipped per the gating rule (third-party registries required to trigger it).

---

## Files Audited

- `web/src/app/(dashboard)/pareceres/page.tsx` (list + quick filters + Pesquisa Avançada)
- `web/src/app/(dashboard)/pareceres/nova/page.tsx` (create-request form)
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx` (detail: `NovaVersaoForm`, `EntregarParecerDialog`, `ParecerEntregueBlock`, "Histórico de Versões" timeline)
- `web/src/components/ui/accordion.tsx` (primitive source, cross-referenced for shipped defaults)
- `web/src/components/ui/tooltip.tsx` (primitive source, cross-referenced for shipped defaults)
- `web/src/components/ui/native-select.tsx` (primitive source, confirmed no built-in `defaultValue` handling)
- `.planning/phases/LEXCV-108-m-dulo-pareceres/108-CONTEXT.md`
- `.planning/phases/LEXCV-108-m-dulo-pareceres/108-UI-SPEC.md`
- `.planning/phases/LEXCV-108-m-dulo-pareceres/108-REVIEW.md` / `108-REVIEW-FIX.md` (prior code-review pass, cross-referenced for IN-05 and WR-04/WR-08 verification)
- Git history (`git log`/`git show` on `b19f946`, `16edcb5`, `7055e0c`, `16a6c50`) — used to confirm which findings are pre-existing vs. introduced by Phase 108
- Screenshots: `.planning/ui-reviews/108-20260717-111503/pareceres-desktop.png`, `pareceres-desktop-2.png`, `pareceres-mobile.png`, `pareceres-nova.png`, `pareceres-filters-open.png` (unauthenticated-context capture, shows RBAC "Acesso negado" state — informational only, not a scored finding), `pareceres-both-open.png`
