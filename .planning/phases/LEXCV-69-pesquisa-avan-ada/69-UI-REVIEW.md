# Phase 69 — UI Review

**Audited:** 2026-07-01
**Baseline:** 69-UI-SPEC.md (approved design contract)
**Screenshots:** not captured (no Playwright MCP available in session; code-only static audit performed against `web/src/app/(dashboard)/pareceres/page.tsx`)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Contract copy strings match verbatim, but no in-UI label distinguishes which result set ("lista simples" vs "pesquisa avançada") is currently displayed |
| 2. Visuals | 2/4 | No heading/CardTitle identifies the Pesquisa Avançada panel as a distinct section; two simultaneously-open filter panels with no visual separation of "active" state |
| 3. Color | 3/4 | Accent-reservation rule honored inside the search panel (only "Pesquisar" is blue), but two `bg-blue-600` CTAs ("Nova Solicitação" + "Pesquisar") are visible on-screen at once, weakening single-focal-point intent |
| 4. Typography | 2/4 | Empty-state heading for zero search results uses `font-medium` (not bold) at inherited 14px, not the spec-mandated `text-base font-bold` (16px/700) |
| 5. Spacing | 4/4 | Grid gaps (`gap-4` for filter grid, `gap-2` for date-range pair) and root `space-y-6` match declared scale exactly |
| 6. Experience Design | 1/4 | Submitting the simple filter bar silently discards active search results (`setPesquisaSubmitted(false)`) while the advanced panel remains visibly open and populated — violates the spec's explicit "Clearing/reverting" contract (revert must only happen via "Limpar Filtros") |

**Overall: 15/24**

---

## Top 3 Priority Fixes

1. **Simple "Aplicar" submit silently kills active search state** (`page.tsx:96-104`) — User fills advanced search, sees results, then clicks "Aplicar" on the still-visible simple filter bar and their search silently reverts to the plain list with zero warning, while the advanced panel stays open and populated (looks like search is still active when it isn't). Fix: either disable/hide the simple filter bar while `pesquisaOpen` is true, or make `onApply` a no-op with a toast/confirm when a search is active, matching the spec's rule that only "Limpar Filtros" may revert the search state.

2. **Zero-result empty state doesn't meet mandated typography** (`page.tsx:388-390`) — Spec requires the "Nenhum resultado encontrado" heading to be `text-base font-bold` (16px/700); code renders `font-medium` at the parent's inherited `text-sm` (14px/500), making the phase's one new empty-state visually indistinguishable from body copy. Fix: change to `<p className="text-base font-bold text-slate-700 dark:text-slate-300">`.

3. **Pesquisa Avançada panel has no section heading, undermining focal point** (`page.tsx:251-372`) — The expanded Card contains only inputs and buttons, with no `CardTitle`/label naming it "Pesquisa Avançada." Combined with the simple filter bar remaining open simultaneously, users have no persistent visual cue for which query is producing the results below. Fix: add a small heading or `CardTitle` (`text-lg font-bold` per module convention) at the top of the panel, and/or a one-line "A mostrar resultados de pesquisa" status indicator above the results table when `searchActive` is true.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)
- Toggle button strings match spec exactly: `"Pesquisa Avançada"` / `"Ocultar Filtros"` (`page.tsx:176`).
- Texto-livre placeholder matches spec verbatim: `"Pesquisar por conteúdo do parecer..."` (`page.tsx:265`).
- Submit button copy matches: `"Pesquisar"` / pending `"A pesquisar..."` (`page.tsx:358`).
- `"Limpar Filtros"` matches spec (`page.tsx:366`).
- Date-range labels `"Data Início"` / `"Data Fim"` / `"Período"` all match spec (`page.tsx:328, 332, 341`).
- Zero-result empty-state copy matches spec verbatim, both heading and body (`page.tsx:389-393`).
- Error copy matches spec verbatim: `"Não foi possível concluir a pesquisa. Verifique a ligação e tente novamente."` (`page.tsx:381`).
- Deduction: no copy anywhere tells the user which result set is currently rendered (simple list vs. search results) beyond the toggle button's own label — a returning user who reloads mid-session with `pesquisaOpen=false` (default state, not persisted) has no way to tell from copy alone why the list might differ from expectations after using "Aplicar" following a search (see Pillar 6 Finding #1).

### Pillar 2: Visuals (2/4)
- No `CardTitle`/`CardHeader` used anywhere in the file (confirmed via grep) — the advanced-search Card at `page.tsx:252-371` is unlabeled beyond the toggle button that opened it.
- Once scrolled, or once the toggle button is out of view, there is no persistent visual marker indicating "this is the Pesquisa Avançada panel."
- Two filter panels (`Filtros` simple bar and `Pesquisa Avançada` panel) can be open at the same time with identical card styling (`border-slate-200 ... bg-slate-50/50`), giving no visual hierarchy cue for which one is "in control" of the results below.
- Icon-only affordance is fine — both toggle buttons pair an icon (`Filter`, `Search`) with a text label, satisfying icon+label accessibility.
- Focal point: within the search panel alone, "Pesquisar" is the only blue element (correct), but the page overall has two blue CTAs on screen simultaneously ("Nova Solicitação" top-right + "Pesquisar" mid-page) competing for attention once the panel is open.

### Pillar 3: Color (3/4)
- Grep for `bg-blue-600|text-blue|border-blue` in the file returns exactly 3 hits: `Nova Solicitação` link button (`:150`), `Pesquisar` submit (`:356`), and a hover-only `text-blue-600` on cliente name links in the results table (`:459`, not a static accent element, acceptable per convention).
- Toggle buttons ("Filtros", "Pesquisa Avançada") correctly avoid blue — styled with neutral/slate outline/secondary variants (`:160-168`, `:169-177`), matching spec's explicit prohibition.
- "Aplicar" button (`:180`) uses the Button component's `default` variant with no color override, which resolves to `bg-neutral-900` (near-black) per `button.tsx:12` — not blue, and not flagged by spec (out of scope, pre-existing Phase 65 pattern), but does add a third strong-contrast CTA to the page alongside the two blue ones.
- No hardcoded hex/rgb colors found in the file — all colors go through Tailwind slate/blue/neutral utility classes, consistent with the declared palette.
- Deduction: the "one accent CTA" principle is satisfied *within* the search panel but not enforced *across* the page when the panel is open — two blue CTAs visible at once is a soft violation of the spirit of the 60/30/10 rule the spec repeatedly emphasizes.

### Pillar 4: Typography (2/4)
- Field labels use `text-[11px] font-bold tracking-wider uppercase` consistently across the search panel and simple filter bar (`:257, 271, 290, 309, 327` etc.) — consistent internally, though this 11px size is not one of the spec's declared type roles (spec declares 12px/`text-xs` for "Label"); this is a pre-existing module convention (identical usage appears in the simple filter bar built in earlier phases) so not a new-phase defect, but worth flagging as a latent spec/implementation mismatch inherited into this phase's new fields.
- Date sub-labels ("Data Início"/"Data Fim") use `text-xs font-medium` (`:332, 341`) — correct size, but `font-medium` (500) is a weight not declared anywhere in the spec's "two weights only: 400/700" rule. This is a new weight introduced this phase, however minor, and should be `font-normal` (400) or `font-bold` (700) per the closed weight contract.
- **Empty-state heading defect (spec-mandated, new this phase):** spec explicitly requires `text-base` (16px) + `font-bold` (700) for the "Nenhum resultado encontrado" heading (spec line 60, 104). Actual code (`:388-390`) renders `<p className="font-medium text-slate-700 dark:text-slate-300">` — no `text-base` class (inherits parent's `text-sm`, 14px) and `font-medium` (500) instead of `font-bold` (700). This is a direct, unambiguous deviation from an explicitly-declared new contract line item.
- Toggle button label correctly non-bold at 14px (spec: `text-sm`, regular) — button uses default Button component text sizing without a bold override for the "Pesquisa Avançada"/"Ocultar Filtros" button specifically (`:169-177`), matching spec's "not bold" requirement.

### Pillar 5: Spacing (4/4)
- Advanced-filters grid: `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4` (`:255`) — matches spec's declared `gap-4`/16px/`md` token exactly.
- Date-range sub-grid: `grid grid-cols-2 gap-2` (`:330`) — matches spec's declared `gap-2`/8px/`sm` token exactly, consistent with the `agenda/page.tsx` precedent cited in the spec.
- Root wrapper: `space-y-6` (`:144`) — matches `lg` token (24px) for section-to-section spacing.
- Card content padding: `p-4` (16px, `md` token) used consistently across all cards (`:157, 253, 375`) — matches spec's declared card-padding usage.
- No arbitrary bracket-value spacing (`[Npx]`/`[Nrem]`) found in the file.
- No deductions — spacing implementation is a clean pass against the declared scale.

### Pillar 6: Experience Design (1/4)
- **Blocker-level state bug:** `onApply` (`:96-104`) unconditionally calls `setPesquisaSubmitted(false)`, reverting `searchActive` to false and swapping `rows` back to the simple list's data — even when the user never touched "Limpar Filtros." Since the simple "Filtros" bar's own submit button ("Aplicar") remains rendered and functional at all times regardless of `pesquisaOpen` state, a user who has an active search open, then absent-mindedly hits "Aplicar" on the still-visible simple bar above it (e.g., to adjust an unrelated filter), gets silently bounced out of search mode with no confirmation, toast, or visual change to the still-expanded advanced panel. This directly contradicts the spec's Interaction Contract: *"Clearing/reverting: 'Limpar Filtros' resets the advanced fields and returns the page to the simple list view... not to a blank/empty search-results state"* — implying reversion should be an intentional, singular action, not a side effect of an unrelated form's submit handler.
- Loading state present and reused correctly (`resultsLoading` branches to `"A carregar..."`, `:377`) satisfying spec's "reuse Phase 65 loading pattern" requirement.
- Error state present and correctly branches copy by `searchActive` (`:378-383`), matching both the search-specific and list-specific error copy from spec.
- Empty state correctly branches by `searchActive` for distinct copy (`:384-404`), satisfying the spec's core requirement that zero-result search be semantically distinct from the simple list's empty state — good coverage of the *content* requirement, undermined by the typography defect noted above.
- Submit button disables during fetch (`disabled={pesquisa.isFetching}`, `:355`) — correct affordance for pending state.
- No destructive actions in scope (read-only feature, per spec) — correctly, no confirmation dialogs are present, satisfying the spec's explicit non-requirement.
- `onLimparPesquisa` correctly resets both draft fields and `pesquisaSubmitted` (`:132-141`) — the "correct" reversion path works as specified; the defect is specifically that it's not the *only* path to reversion.

---

## Registry Safety

Not applicable — `components.json` does not exist in `web/` (confirmed per 69-UI-SPEC.md's Design System section and CLAUDE.md's project description of a hand-maintained component library). No shadcn CLI or third-party registry blocks are in use. Registry audit skipped per its own gating condition.

---

## Files Audited

- `web/src/app/(dashboard)/pareceres/page.tsx` (full file, 478 lines)
- `web/src/components/ui/button.tsx` (Button variant/weight reference)
- `.planning/phases/LEXCV-69-pesquisa-avan-ada/69-UI-SPEC.md` (design contract)
- `.planning/phases/LEXCV-69-pesquisa-avan-ada/69-CONTEXT.md` (phase context/decisions)

Not audited (out of scope for this phase per CONTEXT.md): `web/src/hooks/use-pareceres.ts` (data layer, not visual), `web/src/app/(dashboard)/pareceres/[id]/page.tsx`, `web/src/app/(dashboard)/pareceres/nova/page.tsx` (unmodified this phase).
