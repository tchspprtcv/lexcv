# Phase 103 — UI Review

**Audited:** 2026-07-16
**Baseline:** 103-UI-SPEC.md (approved, revision 2/2)
**Screenshots:** not captured — dev server at `localhost:3003` was live and responded 200 (confirmed via login page render), but the authenticated `/dashboard` route could not be reached this session: automated Playwright login with the documented credentials (`admin@lexcv.cv` / `Pa$$w0rd`) was rejected by the backend (`API 401: Credenciais inválidas`, confirmed via screenshot of the login form's error toast). This matches `103-HUMAN-UAT.md`'s own note that live browser verification of this phase was already attempted and only partially conclusive; audit below is code-level, cross-referenced against the 3 prior review passes (`103-REVIEW.md`, `103-REVIEW-FIX.md`, `103-HUMAN-UAT.md`) and direct reading of the shipped source.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Empty-state copy matches the contract verbatim (3/3 headings + descriptions); one sibling error message still discards the real backend error instead of surfacing it |
| 2. Visuals | 3/4 | Skeleton/Empty correctly occupy existing slots with no new focal point; the card this phase touched still mixes a real lucide icon with two literal text-glyph "icons" |
| 3. Color | 3/4 | New Skeleton/Empty markup is 100% neutral (`bg-muted`, zero `--primary` usage) as contracted; the page's accent role is implemented entirely via hardcoded Tailwind palette classes rather than the semantic token |
| 4. Typography | 3/4 | Mandatory `EmptyTitle` override (`text-sm font-semibold`) implemented exactly and verified to resolve correctly via `cn`/`twMerge`; file still carries 3 pre-existing off-scale sizes (`text-lg`, `text-[11px]`, `text-[10px]`) beyond the declared 4-size cap |
| 5. Spacing | 3/4 | All new Skeleton dimensions are clean, on-scale, zero arbitrary values; pre-existing off-scale spacing (`mt-5`×5, `mt-1.5`, `gap-1.5`, `gap-3`@12px, `h-[260px]`) remains untouched elsewhere in the same file |
| 6. Experience Design | 3/4 | DASH-01/DASH-02 fully and correctly implemented (RBAC-matched skeleton count, loading-gated empties, no Empty-flash); "Processos Recentes" table still shows a headers-only blank body with zero loading indicator during fetch |

**Overall: 18/24**

---

## Top 3 Priority Fixes

1. **"Processos Recentes" has no loading indicator during fetch — renders a headers-only, zero-row table** (`web/src/app/(dashboard)/dashboard/page.tsx:537-601`) — User impact: on any network slower than localhost, a user opening the dashboard sees an apparently-broken/empty table for the fetch duration, right next to a sibling card ("Atividade Recente") that correctly shows a polished skeleton — an inconsistent, unfinished-looking experience on the very page this phase was meant to harden. Concrete fix: add a row-shaped `Skeleton` branch (3 rows mirroring the table's column count) gated on `isLoading`, evaluated before the `recentProcessos.length === 0 && !isLoading` branch, using the same pattern already proven in `KpiCardSkeleton`/`AtividadeRecenteCard`. (Tracked as `IN-07` in `103-REVIEW.md`; explicitly out-of-scope for this phase's approved contract, but still a real gap visible on today's shipped page.)

2. **`RecentProcessosCard`'s error branch discards the real error, unlike its two sibling components** (`web/src/app/(dashboard)/dashboard/page.tsx:535` vs. `:234` and `:449-450`) — User impact: `DashboardKpis` and `PrazosUrgentesCard` both surface `error.message` when available, but "Processos Recentes" always shows the fixed string `"Não foi possível carregar os processos recentes."`, hiding diagnostic information a support agent or advanced user could otherwise use. Concrete fix: thread `processos.error` through the wrapper components and apply the same `error instanceof Error ? error.message : fallback` pattern used at lines 234 and 450. (Tracked as `IN-02`.)

3. **`AtividadeRecenteCard`'s three entries mix a real lucide icon with two literal text-glyph "icons"** (`web/src/app/(dashboard)/dashboard/page.tsx:138, 146, 154`) — User impact: the design system declares `iconLibrary: lucide` as the single icon source, yet 2 of the card's 3 rows render a bare `"+"` (styled `text-lg font-light`) and a bare `"✓"` (styled `text-sm font-bold`) instead of an actual icon component — a visible inconsistency of weight/size/rendering quality sitting inside the exact card this phase extracted into its own component. Concrete fix: replace the two glyph entries with real `lucide-react` icons (e.g., `UserPlus` for "Novo Cliente", `CheckCircle2` for "Processo Concluído"), matching `FileText`'s treatment on the first entry.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

**What's right:** All three Empty-state headings and descriptions match `103-UI-SPEC.md`'s Loading & Empty State Contract table character-for-character:
- `"Sem prazos urgentes"` / `"Não há eventos urgentes nos próximos dias."` — `page.tsx:455-456`
- `"Sem processos recentes"` / `"Os processos criados mais recentemente aparecerão aqui."` — `page.tsx:541-542`
- `"Sem atividade recente"` / `"A atividade mais recente vai aparecer aqui."` — `page.tsx:190-191`

The previously-flagged ad hoc `"Sem urgências."` string is confirmed fully removed (grep for `Sem urgências` returns no matches). No generic English placeholders (`Submit`, `Click Here`, `OK`, `Cancel`, bare `Save`) exist anywhere in the file.

**What's not:** `page.tsx:535` hardcodes `"Não foi possível carregar os processos recentes."` regardless of the actual `processos.error` content, while the sibling KPI (`:234`) and Prazos Urgentes (`:449-450`) branches both do `error instanceof Error ? error.message : "..."`. This is an inconsistency in error-copy strategy across three visually-identical card patterns on the same page (already tracked as `IN-02` in `103-REVIEW.md`, never fixed — it was Info-severity and out of the `critical_warning` fix scope).

### Pillar 2: Visuals (3/4)

**What's right:** The phase's own scope note in `103-UI-SPEC.md` ("Visual Hierarchy: unchanged... Skeleton and Empty states... occupy the exact same visual slots as the content they temporarily replace") is honored — confirmed by reading the diff: `KpiCardSkeleton` renders inside the identical `Card`/`CardContent p-5` shell (`page.tsx:339-352`), and the Atividade Recente skeleton branch reuses the same `CardHeader`/`CardContent pt-6 space-y-5` wrapper (`page.tsx:167-174`) — no new focal point competes with the KPI row. `EmptyMedia variant="icon"` is used unmodified (no accent override) at all 3 call sites (`page.tsx:50`), correctly neutral per contract. No icon-only interactive buttons exist anywhere in the file (`Novo Processo`, `Ver Agenda Completa`, `Abrir`, `Ver todos` all carry visible text labels) — no aria-label gap.

**What's not:** `ATIVIDADE_RECENTE_ENTRIES` (`page.tsx:135-160`) mixes a real `<FileText className="h-4 w-4 .../>` lucide icon (entry 1) with a literal `"+"` string styled to look like an icon (`text-lg font-light`, entry 2) and a literal `"✓"` string (`text-sm font-bold`, entry 3). This was carried over verbatim from the pre-existing markup (the plan explicitly instructed "keep their existing icon chip... markup verbatim"), so it's inherited, not newly introduced — but it ships today, in the exact card this phase refactored into its own component, and visibly breaks the single-icon-library discipline the design system otherwise enforces.

### Pillar 3: Color (3/4)

**What's right:** Grep for `text-primary|bg-primary|border-primary` in `page.tsx` returns **0** matches — confirming the new Skeleton/Empty markup never touches the accent token, exactly as `103-UI-SPEC.md` mandates ("Skeleton and Empty do not join this list [accent]"). Grep for hex/`rgb(` literals also returns 0 matches — no raw hardcoded hex colors anywhere in the file. `Skeleton` (`bg-muted`) and `EmptyMedia variant="icon"` (`bg-muted text-foreground`) are both neutral by construction, confirmed by direct read of `web/src/components/ui/skeleton.tsx:7` and `web/src/components/ui/empty.tsx:34`.

**What's not:** The "Accent (10%)" role that `103-UI-SPEC.md`'s Color section attributes to `--primary` has, in this file, zero actual `--primary`-token usage — the KPI trend badges and the urgent-prazo number instead use hardcoded Tailwind palette classes (`text-red-600 dark:text-red-400` at `:305`, `bg-emerald-50`/`text-emerald-600` at `:315-316`, `text-blue-600` at `:528`, etc.). This is explicitly disclosed as pre-existing, untouched debt in the spec itself, not something this phase introduced or worsened — but it means the declared 60/30/10 split is not actually realized through the token layer on this page; it's approximated by ad hoc per-element color classes scattered across ~15 locations.

### Pillar 4: Typography (3/4)

**What's right:** The single mandatory requirement in the entire spec — `<EmptyTitle className="text-sm font-semibold">` overriding the shipped `text-lg font-medium` default — is implemented exactly as specified at the sole `EmptyState` call site (`page.tsx:53`), used consistently by all 3 Empty instances. Verified via direct read of `web/src/lib/utils.ts:4-5` (`cn = twMerge(clsx(...))`) that the override class-string, appended after the base string in `empty.tsx:62-65`'s `cn(base, className)` call, correctly wins the `twMerge` conflict resolution — this isn't just present in source, it's confirmed to actually resolve to 14px/600 at runtme, not silently overridden by the shipped default.

**What's not:** Distinct font-size utility classes actually in use across the file: `text-sm`, `text-xs`, `text-2xl`, `text-3xl` (all 4 on the declared scale) **plus** `text-lg` (`:148`, the "+" glyph entry), `text-[11px]` (`:199`, `:439`, arbitrary/off-scale), and `text-[10px]` (`:550,553,556,559`, arbitrary/off-scale table headers) — 7 distinct sizes total against the declared 4-size cap. Font weights in use: `font-semibold`, `font-bold`, `font-medium`, `font-light` — 4 distinct weights against the declared ≤2-weight cap (`400`/`600`); `font-bold` (Tailwind 700) dominates the KPI/label rows where the spec's own Typography table declares `600 semibold` for those roles. All of this is pre-existing, explicitly named in `103-UI-SPEC.md` as untouched debt ("same category of debt as the font-bold KPI-number inconsistency flagged in 101-UI-SPEC.md") — the phase's own new code introduces zero new sizes or weights.

### Pillar 5: Spacing (3/4)

**What's right:** Every new Skeleton dimension matches the spec's exact prescribed values with no deviation: KPI skeleton `h-10 w-10` + `h-5 w-14 rounded-full` (`:344-345`), `h-3 w-24 mt-4` (`:347`), `h-8 w-16 mt-1` (`:348`); Atividade Recente skeleton rows `h-10 w-10` + `h-4 w-40` + `h-3 w-24 mt-1` (`:179-182`). All values are on-scale Tailwind defaults — zero arbitrary bracket values (`[...]`) were introduced by this phase's diff.

**What's not:** The same file still contains pre-existing off-scale spacing outside this phase's diff: `mt-5` (20px, not in the declared 4/8/16/24/32/48/64 scale) appears 5× across the 4 KPI label rows and the chart-placeholder legend (`:262,282,302,322,370`); `mt-1.5` (6px) appears once on the real Prazos Urgentes content row (`:439`); `gap-1.5` (6px) appears 4× on the chart legend dots; `gap-3` (12px, also off-scale) appears 6× including inside the very shell the new `KpiCardSkeleton` deliberately mirrors. `h-[260px]` is an arbitrary bracket value on the untouched "Status dos Processos" placeholder (`:367`). All of these are named in `103-UI-SPEC.md` as deliberate, disclosed exceptions to its own "Exceptions: none" rule, tied to the plan's explicit instruction not to "fix" `mt-5` as part of this narrow phase.

### Pillar 6: Experience Design (3/4)

**What's right:** DASH-01 and DASH-02 are both correctly and verifiably implemented: `DashboardKpis` reads `kpis.isLoading` and renders a `KpiCardSkeleton` count matching the exact same RBAC boolean array used for the real cards (`:223-247` vs. `:250-332` — same 4 `canView*` inputs, same order, confirmed identical in 3 independent code-review passes plus this read). `AtividadeRecenteCard` shows exactly 3 skeleton rows while loading with no fade wrapper (`:175-186`), matching the "no custom fade transition" decision in `103-CONTEXT.md`. `PrazosUrgentesCard`'s empty branch correctly suppresses rendering (`null`) while `urgentes.isLoading` is true, only showing `EmptyState` once settled (`:452-457`) — no Empty-flash. `RecentProcessosCard` correctly threads `isLoading` through both wrapper variants (`:483`, `:496`) so its own Empty branch is also flash-free (`:537-544`). `AccessDeniedState` covers the RBAC-denied path at the page level (`:70-78`). Error branches exist for all 3 data-backed sections (KPI, Prazos, Processos).

**What's not:** `RecentProcessosCard` has no dedicated loading state at all — while `isLoading` is true and `isError` is false, the ternary at `:533-601` falls through to the live `<Table>` branch with an empty `<TableBody>`, rendering column headers with zero rows and no skeleton/spinner until data arrives (tracked as `IN-07`, confirmed still present by direct read). This was an explicit, contract-approved scope exclusion ("Explicitly out of Skeleton scope this phase... Processos Recentes... do not add one" per `103-UI-SPEC.md`) rather than an oversight, which tempers but does not eliminate its real, currently-shipping impact on perceived polish. `AtividadeRecenteCard`'s defensive Empty branch (`:187-192`) is confirmed unreachable dead code today (`ATIVIDADE_RECENTE_ENTRIES` is a fixed 3-item array) — acceptable, explicitly deferred per the `DASH-V2` pattern, not counted against this score.

---

## Registry Safety

Not applicable — `web/components.json` exists (shadcn initialized), but `103-UI-SPEC.md`'s Registry Safety table lists only `shadcn official` (`Skeleton`, `Empty`, both pre-installed since Phase 101) with no third-party registry entries, and explicitly states "No third-party registries — excluded for this entire milestone." Per the registry-audit gating rule (requires both shadcn initialized AND UI-SPEC listing third-party registries), the audit is skipped. Registry audit: 0 third-party blocks checked, no flags.

---

## Files Audited

- `web/src/app/(dashboard)/dashboard/page.tsx` (full file, 605 lines — primary and only in-scope file per `103-UI-SPEC.md`'s Scope note)
- `web/src/components/ui/skeleton.tsx` (verify base classes: `animate-pulse rounded-md bg-muted`)
- `web/src/components/ui/empty.tsx` (verify `Empty`/`EmptyHeader`/`EmptyMedia`/`EmptyTitle`/`EmptyDescription`/`EmptyContent` exports and shipped defaults)
- `web/src/lib/utils.ts` (verify `cn` = `twMerge(clsx(...))`, confirming the mandatory `EmptyTitle` className override actually resolves at runtime)
- `web/components.json` (confirm shadcn preset unchanged: `radix-vega`, `neutral`, `cssVariables: true`, `lucide`)
- `.planning/phases/LEXCV-103-m-dulo-dashboard/103-UI-SPEC.md`, `103-CONTEXT.md`, `103-01-PLAN.md`, `103-01-SUMMARY.md`, `103-REVIEW.md`, `103-REVIEW-FIX.md`, `103-HUMAN-UAT.md` (upstream planning/review artifacts, cross-referenced for prior findings)
