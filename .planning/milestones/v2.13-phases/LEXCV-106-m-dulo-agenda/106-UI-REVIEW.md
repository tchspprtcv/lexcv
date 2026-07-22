# Phase 106 — UI Review

**Audited:** 2026-07-16
**Baseline:** `106-UI-SPEC.md` (design contract; "Approval: pending" in its own sign-off block, but phase already shipped and passed its own 106-04 human-verify checkpoint + 106-VERIFICATION.md goal-backward check — audited against it as the authoritative contract per this retroactive review's brief)
**Screenshots:** Partially captured. Dev server was running (`localhost:3000`, HTTP 200). One incidental authenticated capture of `/agenda` was obtained (before the session's auth cookie expired), showing the migrated filter bar, calendar grid, and KPI panel rendering correctly in position/layout. Subsequent capture attempts (CLI `npx playwright screenshot`, and a scripted login via `admin@lexcv.cv`/`Pa$$w0rd` from `CLAUDE.md`) hit a login wall — the seeded credentials returned `401 Credenciais inválidas` against this dev instance, so authenticated multi-page/multi-viewport screenshots (novo form, editar form, open Calendar popover, mobile breakpoint) could not be produced this session. **Audit is primarily code-level**, cross-checked against the one live capture obtained and against the phase's own already-recorded live UAT evidence (`106-04-SUMMARY.md`, `106-VERIFICATION.md`) rather than re-doing that verification from scratch.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All new/touched copy (placeholder, filter "all" options, validation messages) matches the contract verbatim; no generic labels found. |
| 2. Visuals | 3/4 | Composite date+time field shares one `Label` across two visually distinct controls with no sub-caption distinguishing them; pre-existing icon-only month-nav chevrons (untouched, but on the same page) still lack `aria-label`. |
| 3. Color | 4/4 | Zero new accent (`primary`) reservations added; all hardcoded hex/legacy chrome confirmed pre-existing via `git diff` against the pre-phase base commit, correctly out of this phase's scope. |
| 4. Typography | 3/4 | New/touched code matches the declared 400/600 scale exactly, but every field in both migrated forms renders its `Label` via the shared `label.tsx` primitive at `font-medium` (500) — a 3rd weight value outside the declared pair (pre-existing primitive, not introduced by 106, but currently shipping on this phase's exact deliverable). |
| 5. Spacing | 4/4 | `DatePickerField`, `Select`, `NativeSelect` all use on-scale, spec-exact values (`w-24`, `gap-2`, `w-48`/`w-40`/`w-36`); zero arbitrary values in any file this phase touched. |
| 6. Experience Design | 3/4 | The phase's own bundled RBAC race fix (`isFetched`) is applied inconsistently within the very files it targets: submit-button `disabled` still reads stale `permissions.isLoading` in both forms, and `agenda/[id]/editar/page.tsx`'s `onSubmit` lacks the explicit `canEditAgenda` re-check that `agenda/novo/page.tsx`'s `onSubmit` has. |

**Overall: 21/24**

---

## Top 3 Priority Fixes

1. **RBAC guard applied inconsistently within the phase's own fix scope** — A user whose permissions query is mid-flight sees the Criar/Guardar button briefly enabled (guarded by stale `permissions.isLoading`, not the `isFetched` fix this same phase applied one line above for the page-level access gate), and `agenda/[id]/editar/page.tsx`'s `onSubmit` (lines 112–125) has no `if (!canEditAgenda) return` guard at all, unlike `agenda/novo/page.tsx`'s `onSubmit` (lines 69–72) which does. Backend `@PreAuthorize` still blocks an unauthorized PUT, so this fails safe, but the frontend no longer "agrees" with the backend the way `CLAUDE.md`'s RBAC convention requires — **fix:** change `permissions.isLoading` → `!permissions.isFetched` in both submit-disabled conditions (`agenda/novo/page.tsx:276`, `agenda/[id]/editar/page.tsx:297`), and add the same explicit `if (!canEditAgenda) { setServerError(...); return; }` guard to `editar/page.tsx`'s `onSubmit` that `novo/page.tsx` already has.

2. **`DatePickerField`'s time `<Input>` has no accessible label** (`web/src/components/shared/date-picker-field.tsx:82-90`) — a screen-reader user tabbing to the `type="time"` control adjacent to the date trigger hears only "time, edit text," with no indication it belongs to "Início"/"Fim"/etc.; the single `<Label>` above the composite field only associates (via `htmlFor`) with the date button. This was already triaged as a non-blocking `ℹ️ Info` item in `106-VERIFICATION.md`, but for a first-of-its-kind, 4-call-site composition it's worth closing rather than carrying forward — **fix:** add `aria-label={\`Hora (${id})\`}` or `aria-labelledby` pointing at the field's existing `<Label>` id.

3. **Shared `Label` primitive ships a 3rd type weight (500) on every field of both migrated forms** (`web/src/components/ui/label.tsx:13`, consumed 8+ times per form in `agenda/novo/page.tsx`/`agenda/[id]/editar/page.tsx`) — `text-sm font-medium` doesn't match either the declared Body (14px/400) or Label (12px/600) role in `106-UI-SPEC.md`'s Typography table. Pre-existing (unchanged since Phase 101, used identically app-wide), not introduced by this phase, but it is what ships on every field this phase's own forms render — **fix:** track as a design-system-level cleanup (drop to `font-normal` or formally add "Field Label: 14px/500" as a named 3rd role in a future UI-SPEC), not a Phase 106 patch.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

- Date-picker placeholder matches the contract exactly: `<span className="text-muted-foreground">Selecionar data</span>` (`date-picker-field.tsx:63`) vs. spec's `"Selecionar data"` requirement.
- Filter "all" options reused verbatim, first `SelectItem` in each list: `"Todos os Processos"` (`agenda/page.tsx:251`), `"Todas"` (`:268`), `"Todos"` (`:284`) — exact match to spec's Copywriting Contract table and to the pre-migration native `<option>` text (confirmed via `git diff` — text unchanged across the migration).
- Validation copy (`schemas/eventos.ts`) is specific and in-domain Portuguese throughout: `"O título é obrigatório"`, `"A data de fim não pode ser anterior à data de início"`, `"A data de fim da recorrência é obrigatória"` — no generic "Invalid input" messages.
- Grep for generic patterns (`Submit`, `Click Here`, bare `OK`/`Cancel`/`Save`) inside `web/src/app/(dashboard)/agenda/` returned only legitimate matches (`onSubmit` handler names, `"A guardar..."`/`"Criar"`/`"Guardar"` button copy) — no generic-label violations.
- No new empty/error-state copy ships this phase (spec correctly declares this N/A); existing `"Sem eventos futuros."` / `"Nenhum evento hoje."` unchanged, confirmed present verbatim.

### Pillar 2: Visuals (3/4)

- Focal point is preserved correctly: the monthly calendar grid remains the dominant visual element on `/agenda`, untouched by this phase (confirmed via `git diff e1d43d3` — `buildMonthGrid`/`dayKey`/DnD handlers byte-identical).
- **Finding (new-composition affordance gap):** the date+time variant (`dataInicio`/`dataFim`) renders one `<Label htmlFor="dataInicio">Início</Label>` above a `flex gap-2` row containing a date-trigger `Button` **and** a separate `Input type="time"`, with no sub-caption ("Data"/"Hora") distinguishing which control does what. First-time users must infer the split from control shape alone. Low severity — the calendar icon and `HH:mm` placeholder make the split legible after one glance, but it's a UX affordance the spec's shared trigger anatomy didn't explicitly account for.
- **Finding (pre-existing, same page):** the month-navigation chevrons (`agenda/page.tsx:196-229`, `<Button variant="ghost"><ChevronLeft className="h-4 w-4" /></Button>`) are icon-only with no `aria-label`. Confirmed pre-existing (untouched by this phase's diff), but flagged per the Visuals audit method since it's on the exact page this phase modified.
- The one live screenshot captured (`.planning/ui-reviews/106-20260716-225352/agenda-desktop.png`) showed all three migrated `SelectTrigger`s rendering as empty chevron-only boxes with no visible label text, while `filteredEvents`/`isLoading` state was still mid-fetch. This is very likely a one-frame hydration artifact (Radix `SelectValue` needs client mount to resolve its portal-rendered label) rather than a real defect — flagged `needs_human_review: true` since it could not be re-confirmed with a second authenticated capture this session.

### Pillar 3: Color (4/4)

- `grep -rn "text-primary\|bg-primary\|border-primary"` across `web/src/app/(dashboard)/agenda/` → 0 matches. This phase adds zero new accent-reserved elements, exactly matching the spec's explicit rule ("Neither `Calendar`, `Popover`, `Select`, nor `NativeSelect` join this list this phase").
- Hardcoded hex colors found (`agenda/page.tsx:605,615,625,635,644` — `getCategoria()`'s `titleColor` values; `:195,243,321,358,411,490,516,587` — `bg-white dark:bg-[#020617]` chrome) are all confirmed pre-existing via `git diff e1d43d3 -- agenda/page.tsx` — none appear in this phase's diff. Matches the spec's explicit carve-out ("filter bar's own container... not retokenized... out of scope").
- `Calendar`'s own `bg-primary`/`text-primary-foreground` selected-day treatment (`calendar.tsx:238`) is the primitive's shipped Phase-101 default, pre-approved by the spec as not a new accent-reservation decision — confirmed unchanged.
- `date-picker-field.tsx` introduces zero hardcoded colors of its own.

### Pillar 4: Typography (3/4)

- `date-picker-field.tsx`'s trigger `Button` uses `font-normal` explicitly (`:57`), correctly overriding `buttonVariants`' `font-medium` default — matches the spec's explicit instruction and introduces no new weight value (400 is already Body's weight).
- `SelectTrigger`/`NativeSelect` render at their own shipped `text-sm`, unweighted (inherits 400) in all migrated call sites (3 filters + 7 form fields) — matches spec exactly, and correctly **replaces** the pre-existing `text-xs font-bold` (12px/700) on the 3 filters, resolving the 3rd-weight violation the spec flagged as a deliberate correction.
- **Finding:** `web/src/components/ui/label.tsx:13` (`text-sm font-medium`) is used for every `<Label>` in both migrated forms (Processo, Categoria, Título, Prioridade, Início, Fim, Descrição, Recorrência, Concluído, Fim da recorrência — 9-10 per form). `font-medium` (500) is a 3rd weight value beyond the declared Body(400)/Label(600) pair, and doesn't match either named role's size+weight combination. This is an unchanged, pre-Phase-106 primitive (used identically across the whole app since Phase 101) — not something 106 introduced — but it is real, currently-shipping drift visible on every field of the phase's own deliverable, so it's cited here rather than silently excluded.
- H1 unchanged (`text-2xl font-semibold`, `novo/page.tsx:101`, `editar/page.tsx:150`) — matches the Heading role's declared 24px/600 exactly (and matches the `105-UI-SPEC.md` h1-weight-cap fix precedent referenced in this repo's own commit history).

### Pillar 5: Spacing (4/4)

- `date-picker-field.tsx`: zero arbitrary-value classes (`grep "\[.*px\]|\[.*rem\]"` → no matches). `w-24` (time input), `gap-2` (flex row), `mr-2 h-4 w-4` (icon) all on-scale, matching the spec's explicit exceptions table verbatim.
- Filter widths (`agenda/page.tsx:247,264,280` — `w-48`/`w-40`/`w-36`) match spec exactly and are unchanged from the pre-migration native `<select>`'s own widths (confirmed via diff — same values carried forward, only the element swapped).
- All arbitrary-value classes found in `agenda/page.tsx` (`text-[10px]`, `text-[11px]`, `min-h-[120px]`, `gap-[1px]`, `grid-cols-[1fr_360px]`) are confirmed pre-existing via `git diff e1d43d3` — none touched by this phase.
- **Unverified, not scored:** the spec's documented fallback ("if cramped at the `sm:` breakpoint, switch `flex-row` → `flex-col gap-2`") was not implemented — `date-picker-field.tsx:50` uses a static `cn(withTime && "flex gap-2")` with no responsive variant. Spec explicitly left this to implementation discretion contingent on real-rendering feedback; without an authenticated screenshot at the `sm:` (640px) breakpoint this session, it's impossible to confirm whether the fallback was actually needed and skipped, or correctly judged unnecessary. Flagged `needs_human_review: true`, not deducted from the score.

### Pillar 6: Experience Design (3/4)

- Loading/error/empty state coverage for the new/touched surfaces is solid: `processos.isPending`/`isError` disables the `NativeSelect` and shows an inline `"A carregar..."`/`"Erro ao carregar processos"` message (both forms); `agenda/page.tsx`'s calendar grid shows a `Loader2` spinner + `"A carregar agenda..."` and a red error message keyed off the first failing query.
- Destructive-action confirmation (`agenda/[id]/page.tsx`'s delete `AlertDialog`, series-vs-instance choice) is unchanged and untouched by this phase, confirmed still present and correctly gated behind `canEditAgenda`.
- **Finding:** the phase's own bundled RBAC-race fix (`permissions.isLoading` → `permissions.isFetched`) was applied to all 4 files' top-level access gates (confirmed: `agenda/page.tsx:27`, `novo/page.tsx:30`, `[id]/page.tsx:62`, `[id]/editar/page.tsx:64` — 4/4 present, 0 remaining `!permissions.isLoading && !canX`, matching `106-VERIFICATION.md`'s own grep-confirmed claim), but the **same files** still gate their submit button's `disabled` prop on the stale `permissions.isLoading` (`novo/page.tsx:276`, `editar/page.tsx:297`) rather than `!permissions.isFetched` — the exact race class this phase's fix targeted, left unresolved one line away in the same component.
- **Finding:** `agenda/[id]/editar/page.tsx`'s `onSubmit` (lines 112-133) has no `if (!canEditAgenda) return` guard, while `agenda/novo/page.tsx`'s `onSubmit` (lines 67-72) does have the equivalent `canCreateAgenda` guard. Both forms are gated at the page level by `permissions.isFetched && !canX`, and the backend enforces `@PreAuthorize` regardless, so this is not an exploitable gap — but it's an inconsistency between two sibling files touched by the same phase for the same concern.
- `DatePickerField`'s time input accessibility gap (no `aria-label`) — see Top 3 Fix #2. Already triaged `ℹ️ Info`/non-blocking in `106-VERIFICATION.md`, re-confirmed present in current source (`date-picker-field.tsx:83-89`, no `aria-label` prop).
- Radix `Select` filter triggers (`agenda/page.tsx`) have no explicit `aria-label` or `id`/`htmlFor` association with their adjacent `<span>` captions ("Processo"/"Categoria"/"Estado") — confirmed pre-existing pattern (the prior native `<select>`s had the identical gap), not a regression introduced by the `Select` migration, but a missed opportunity to fix during a primitive swap.

---

## Registry Safety

`components.json` exists (shadcn initialized). `106-UI-SPEC.md`'s Registry Safety table lists only `shadcn official` for all blocks used this phase (`Calendar`, `Popover`, `Select`, `NativeSelect`, `Button`, `Input`) — no third-party registry entries. Per the registry-audit gate (only runs when a third-party registry is listed), the deep `shadcn view`/`shadcn diff` check was skipped.

Registry audit: 0 third-party blocks checked (shadcn official only, all pre-installed since Phase 101), no flags.

---

## Files Audited

- `web/src/app/(dashboard)/agenda/page.tsx`
- `web/src/app/(dashboard)/agenda/novo/page.tsx`
- `web/src/app/(dashboard)/agenda/[id]/page.tsx`
- `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx`
- `web/src/components/shared/date-picker-field.tsx`
- `web/src/components/ui/calendar.tsx`
- `web/src/components/ui/select.tsx`
- `web/src/components/ui/native-select.tsx`
- `web/src/components/ui/button.tsx`
- `web/src/components/ui/label.tsx`
- `web/src/schemas/eventos.ts`
- `.planning/phases/LEXCV-106-m-dulo-agenda/106-UI-SPEC.md`
- `.planning/phases/LEXCV-106-m-dulo-agenda/106-CONTEXT.md`
- `.planning/phases/LEXCV-106-m-dulo-agenda/deferred-items.md`
- `.planning/phases/LEXCV-106-m-dulo-agenda/106-VERIFICATION.md`
- `git diff e1d43d3..HEAD -- "web/src/app/(dashboard)/agenda/page.tsx"` (used to separate this phase's changes from pre-existing legacy code in the same file)

Screenshots (partial, dev-server-only, one authenticated frame): `.planning/ui-reviews/106-20260716-225352/` (gitignored).
