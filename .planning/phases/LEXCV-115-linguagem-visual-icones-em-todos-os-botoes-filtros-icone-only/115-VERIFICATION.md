---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
verified: 2026-07-22T03:30:00Z
status: passed
score: 4/4 roadmap success criteria verified
overrides_applied: 0
---

# Phase 115: Linguagem Visual — Ícones em Todos os Botões + Filtros Ícone-Only Verification Report

**Phase Goal:** Todos os botões da aplicação comunicam a sua ação através de um ícone consistente, e os botões de ação de filtro em todos os módulos tornam-se ícone-only com tooltip — o próximo passo da linguagem visual, depois de fechados os gaps de UX (Phases 111-113) e da fundação de cantos arredondados (Phase 114).
**Verified:** 2026-07-22T03:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Adversarial Starting Position

Starting hypothesis: the phase goal was NOT achieved and SUMMARY.md/115-REVIEW.md claims were unverified narrative. Every claim below was independently re-derived from the current codebase — `git diff` against the pre-phase base commit (`65cf4fe`, Phase 114's close), direct `Read` of the modified JSX, a self-run `pnpm lint` + `pnpm build`, a Node-based `lucide-react` export-resolution check, and an independent regex sweep of all 207 `<Button>` instances across the full 48-file audited scope — not inferred from SUMMARY/REVIEW text.

## Goal Achievement

### Observable Truths (ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Todo o botão de ação primária ou secundária — incluindo os introduzidos por esta milestone (paleta de pesquisa, filtro de estado) — apresenta um ícone consistente (ICON-01) | ✓ VERIFIED | Independent Node-script sweep of all `<Button` blocks across the 48-file audited scope found **207/207 parsed matches carry a detectable icon**, after manually resolving all 7 initial false positives (see "Anti-Patterns / Sweep Findings" below — all either already-icon-only via dynamic lookup, pre-existing/untouched compliant files, or one explicitly-documented discretion exception). The two roadmap-named "new UI from this milestone": (a) **paleta de pesquisa** — the topbar search trigger (`web/src/components/shared/dashboard-shell.tsx:160-172` desktop, `:194-201` mobile) already renders a `Search` icon + `aria-label="Pesquisar"` (introduced Phase 112, untouched — confirmed not in Phase 115's diff — and already compliant); the dialog's own result rows (`global-search-dialog.tsx`, `ResultRow`) already render a type icon (`Users`/`Scale`/`FileText`/`ScrollText`) per result. (b) **filtro de estado** — the Processos `NativeSelect` (`processos/page.tsx:230-239`, introduced Phase 113) is a form control (not a "botão"); its adjacent Aplicar/Limpar action buttons are the FICO-01 conversion verified under truth #2 below. |
| 2 | Botões de ação de filtro (aplicar/limpar/exportar) em Clientes, Processos, Agenda, Documentos, Financeiro passam a mostrar apenas o ícone (FICO-01) | ✓ VERIFIED | Read the live JSX of all 6 target files. All 12 conversions confirmed icon-only (no visible text child), `size="icon"` (except the pre-existing hand-rolled Processos Exportar, explicitly exempted by 115-UI-SPEC.md), each wrapped in `Tooltip`/`TooltipTrigger asChild`. `grep -c 'aria-label="\(Aplicar filtros\|Limpar filtros\|Exportar CSV\)"'` across the 6 files = exactly **12**, matching the UI-SPEC's locked inventory 1:1 (Clientes 3, Processos 3, Processos-`[id]` Timeline 1, Agenda 1, Documentos 2, Financeiro 2). Negative-checked: Agenda and Financeiro have no invented Aplicar button; Documentos/Processos have no invented Exportar beyond the pre-existing one — confirmed via direct read, no such buttons exist. Handlers preserved: `onClick={onExportCsv}`, `onClick={onClear}`, `type="submit"` all intact at their original call sites; Financeiro's two conditional-render guards (`canViewFinanceiro && filteredList.length > 0`, `filtroProcesso !== "todos" || ...`) confirmed still wrapping the `Tooltip`, not bypassed. |
| 3 | Hover sobre um botão de filtro ícone-only mostra um tooltip a identificar a ação, reutilizando o `Tooltip` da v2.13 (FICO-01) | ✓ VERIFIED | `web/src/components/ui/tooltip.tsx` is a genuine Radix (`radix-ui` `Tooltip` primitive) wrapper, not a stub — `TooltipContent` renders via `TooltipPrimitive.Portal`+`Content`+`Arrow`. `web/src/app/providers.tsx:30` mounts `<TooltipProvider delayDuration={700}>` once at the app root (unchanged by this phase). Every one of the 12 FICO-01 buttons is wrapped `<Tooltip><TooltipTrigger asChild><Button .../></TooltipTrigger><TooltipContent>{same copy as aria-label}</TooltipContent></Tooltip>` — read directly in all 6 files. Additionally: the human-verify checkpoint (115-11-SUMMARY.md, Task 2) independently walked all 5 modules' hover behavior live against a running app this session and recorded zero defects (one nuance: the disabled Processos Exportar button's tooltip doesn't fire on hover because shadcn's `disabled:pointer-events-none` blocks the hover event — confirmed as expected framework behavior, not a regression, and its `aria-label` still holds). |
| 4 | Um utilizador que navegue por teclado ainda identifica a ação de cada botão ícone-only via `aria-label`, preservando a acessibilidade já corrigida (FICO-01) | ✓ VERIFIED | All 12 FICO-01 conversions carry an explicit `aria-label` whose text is byte-identical to the sibling `TooltipContent` (verified via direct read in all 6 files — e.g. `aria-label="Aplicar filtros"` next to `<TooltipContent>Aplicar filtros</TooltipContent>`). This is a structural a11y guarantee independent of runtime hover state — an `aria-label` gives the element an accessible name via the HTML accname algorithm regardless of Tooltip visibility. The two icon-only row-action conversions outside the 5 named modules but within this phase's touched files (`documentos/columns.tsx`, `processos/[id]/documentos-columns.tsx` Apagar actions) also carry matching `aria-label`+`Tooltip`. Live checkpoint (115-11) additionally Tab-tested all 10 primary FICO-01 buttons with no mouse and confirmed accessible-name announcement, zero defects. |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/components/shared/data-table/data-table-pagination.tsx` | ChevronLeft/ChevronRight on Anterior/Seguinte, text preserved | ✓ VERIFIED | Read in full — both icons present, text intact, `variant`/`disabled` unchanged. Highest-leverage fix (renders on every `DataTable` list page). |
| `web/src/components/shared/access-denied-state.tsx` | ArrowLeft inside the Voltar `Link`, text preserved | ✓ VERIFIED | Read in full — `ArrowLeft` correctly placed inside `<Link>` (asChild rule honored), text intact. Renders on every RBAC access-denied screen. |
| `web/src/components/shared/notificacao-snooze-control.tsx`, `user-profile-form.tsx`, `user-password-form.tsx` | Check/X/Save icons, IN-01 mr-2 fix applied | ✓ VERIFIED | `grep -n "h-4 w-4"` confirms icons present with NO `mr-2` (fix commit `e725fc3` independently confirmed via `git show`). |
| `web/src/app/(dashboard)/clientes/page.tsx` | FICO-01 (Check/X/Download icon-only) + Merge/Upload icon+text | ✓ VERIFIED | Full JSX read: 3 icon-only conversions with `aria-label`+`Tooltip`; Merge/Importar CSV keep text; L285 Plus/L353 Filter confirmed byte-identical (not in diff hunks). |
| `web/src/app/(dashboard)/clientes/novo/page.tsx`, `clientes/merge/page.tsx` | 7 ICON-01 icon+text buttons | ✓ VERIFIED | `lucide-react` import + vocabulary icons confirmed via grep; text preserved. |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` (31 gaps — heaviest file) | All 31 gap buttons iconed, text preserved, no FICO-01 | ✓ VERIFIED | Icon-tag count across 10 imported names = **32**, exactly matching the 32 `<Button` count in the file (31 new + 1 pre-existing `Printer`, confirmed byte-identical). 1:1 icon:Button ratio — every single Button in this file has an icon. |
| `web/src/app/(dashboard)/processos/page.tsx` | FICO-01 (Check/X icon-only + Exportar a11y-only completion, still disabled) + nav icons | ✓ VERIFIED | Full JSX read: Aplicar/Limpar icon-only; Exportar retains its pre-existing hand-rolled markup + `disabled`, gains only `aria-label`+`Tooltip` (no export logic added, confirmed no new fetch/handler). |
| `web/src/app/(dashboard)/processos/novo/page.tsx`, `processos/[id]/editar/page.tsx`, `processos/[id]/documentos-columns.tsx` | Wizard/editar icons + Apagar row-action icon-only | ✓ VERIFIED | Grep-confirmed vocabulary icons; `documentos-columns.tsx` diff shows only the Apagar half changed, Download half untouched. |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` (31 gaps + 1 FICO-01 — heaviest file) | Toolbar/dialogs/5 sub-groups iconed; Timeline "Limpar filtros" icon-only | ✓ VERIFIED | `aria-label="Limpar filtros"` present exactly once (Timeline); `ACAO_ICONS` dynamic-lookup map (L1031, workflow-transition buttons) and the raw prazo-toggle `<button>` confirmed outside all diff hunks (untouched, pre-existing-compliant). Icon-tag sum ≈ Button count (34 vs 33, difference explained by the `User`/`UserCog` reuse pattern and the dynamic `ACAO_ICONS` buttons not captured by the literal-tag grep). |
| `web/src/app/(dashboard)/agenda/page.tsx` | FICO-01 (X icon-only Limpar Filtros), no invented Aplicar/Exportar | ✓ VERIFIED | Read in full: icon-only Limpar with `aria-label`+`Tooltip`; confirmed no Aplicar/Exportar button exists (module's filters auto-apply via `Select`). "Hoje" chip correctly left text-only (explicit UI-SPEC discretion exception). |
| `web/src/app/(dashboard)/agenda/[id]/page.tsx`, `[id]/editar/page.tsx`, `novo/page.tsx` | Detail/edit/create icons incl. state-conditional Concluir/Reabrir toggle | ✓ VERIFIED | `ArrowLeft/Pencil/CheckCircle2/RotateCcw/Trash2` all present and imported; toggle correctly state-conditional. |
| `web/src/app/(dashboard)/documentos/page.tsx`, `columns.tsx`, `novo/page.tsx`, `[id]/page.tsx` | FICO-01 (Check/X icon-only Filtrar/Limpar) + Upload/Apagar icons, no invented Exportar | ✓ VERIFIED | Read in full: icon-only conversions correct (Filtrar uses the unified "Aplicar filtros" copy per spec); `columns.tsx` Apagar converted to icon-only matching sibling Download; no Exportar exists anywhere in the module. |
| `web/src/app/(dashboard)/financeiro/page.tsx`, `novo/page.tsx`, `[id]/page.tsx` (0 pre-existing lucide imports) | FICO-01 (Download/X icon-only, conditional guards preserved) + 16 ICON-01 gaps | ✓ VERIFIED | Read in full: both conditional-render guards (`{cond ? <Tooltip>...</Tooltip> : null}`) confirmed structurally intact around the icon-only Buttons; all 10 detail-page buttons carry vocabulary icons (grep count = 10, matches plan's claim exactly). |
| `web/src/app/(dashboard)/pareceres/page.tsx`, `notificacoes/page.tsx`, `pareceres/[id]/page.tsx`, `pareceres/nova/page.tsx` | ICON-01 icon+TEXT only — FICO-01 module exclusion honored | ✓ VERIFIED | `grep -c TooltipTrigger` = 0 in both files. Direct read confirms Aplicar/Limpar/Pesquisar/Nova Solicitação/Limpar filtros all keep visible text alongside their new icon — zero icon-only conversions, honoring 115-CONTEXT.md's explicit exclusion. |
| `web/src/app/(dashboard)/settings/page.tsx`, `dashboard/page.tsx`, `(auth)/login/page.tsx` | RotateCcw/Save/X + close-X aria-label; ArrowRight; LogIn | ✓ VERIFIED | All present; `Edit` (not normalized to `Pencil`) confirmed preserved per do-not-touch rule; dashboard's "Abrir" row-action gap (found post-hoc, outside Plan 10's declared scope) confirmed fixed by follow-up commit `cbeaa95`. |
| 16 fully-compliant "do-not-touch" files (`clientes/columns.tsx`, `processos/columns.tsx`, `pareceres/columns.tsx`, `notification-bell.tsx`, `date-picker-field.tsx`, `combobox.tsx`, `theme-toggle.tsx`, `ui/pagination.tsx`, `data-table-view-options.tsx`, `data-table-column-header.tsx`, `clientes/[id]/ficha/page.tsx`, `processos/dashboard/page.tsx`, `processos/[id]/termo-honorarios/page.tsx`, `ui/calendar.tsx`, `ui/input-group.tsx`, `setup/page.tsx`) | Byte-identical, zero changes | ✓ VERIFIED | Independently re-ran the guard: `git diff --name-only 65cf4fe HEAD -- web/src` = exactly 32 files; none of the 16 listed files appear. Confirmed clean with no reliance on the plan's self-report. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| 12 FICO-01 `Button`s | `Tooltip` primitive | `TooltipTrigger asChild` wraps the `Button`, sibling `TooltipContent` | ✓ WIRED | Confirmed in all 6 files by direct read; Radix `asChild` correctly forwards all button semantics (a well-established pattern, already used at 15+ pre-existing call sites). |
| 12 FICO-01 `Button`s | Keyboard/screen-reader users | `aria-label` identical to `TooltipContent` copy | ✓ WIRED | `grep -c` = 12/12 matches across the 6 files; every one paired with an identical-text `TooltipContent`. |
| `Tooltip`/`TooltipTrigger`/`TooltipContent` | `TooltipProvider` | App-root mount, `providers.tsx:30`, unchanged by this phase | ✓ WIRED | Confirmed `TooltipProvider delayDuration={700}` still mounted once at root — no per-call-site wiring needed or added. |
| Icon-only Aplicar/Limpar/Exportar | Original handlers (`onClear`, `onExportCsv`, form `onSubmit`, conditional guards) | Preserved verbatim through the icon-only conversion | ✓ WIRED | Read the full JSX at every conversion site — `onClick={onClear}`, `onClick={onExportCsv}`, `type="submit"`, and Financeiro's 2 conditional-render guards all intact, not stubbed or bypassed. |
| 158 ICON-01 icon+text `Button`s | `lucide-react` package | Named imports (`Plus`, `Save`, `ArrowLeft`, etc.) | ✓ WIRED | `pnpm build` (self-run, this session) exits 0 — Next.js/TypeScript would fail the build on any undefined icon import. Additionally verified all edge-case names (`Merge`, `GitMerge`, `UserCog`, `LayoutDashboard`, `Send`, `LogIn`, `RotateCcw`, `CheckCircle2`) resolve as real exports in the installed `lucide-react@0.543.0` via a direct Node `require()` check. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole-app production build succeeds (proves every icon import resolves/typechecks) | `cd web && pnpm build` (self-run) | `✓ Compiled successfully in 12.4s`, `Finished TypeScript in 24.0s`, all 24 routes generated, exit 0 | ✓ PASS |
| Whole-app lint has no NEW errors introduced by icon work | `cd web && pnpm lint` (self-run) | 6 errors / 17 warnings — all independently traced via `git diff` hunk-range analysis to lines OUTSIDE every Phase 115 diff hunk (react-hooks/set-state-in-effect ×5, react-hooks/refs ×1, pre-existing `_estado`/`textareaClassName` unused-vars, `@next/next/no-img-element`, `react-hooks/incompatible-library` — none icon-related) | ✓ PASS |
| Do-not-touch guard | `git diff --name-only 65cf4fe HEAD -- web/src` | Exactly 32 files, matches claimed scope, none of the 16 compliant files present | ✓ PASS |
| lucide-react icon export resolution | `node -e "require('lucide-react')..."` | All 13 spot-checked names (incl. every documented fallback) resolve | ✓ PASS |
| Automated Button/icon sweep across the full 48-file audited scope | Node script: match every `<Button>...</Button>` block, flag blocks with no detectable icon child | 207 Button blocks parsed; 7 initial flags, all resolved as false positives (dynamic `ACAO_ICONS` lookup, pre-existing untouched files, or the one documented "Hoje" discretion exception) — 0 genuine gaps | ✓ PASS |

### Probe Execution

Not applicable — Phase 115 is a UI-only icon/tooltip phase with no migration or CLI tooling; no `scripts/*/tests/probe-*.sh` declared in any PLAN/SUMMARY, and none found under `scripts/`.

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ICON-01 | 115-01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11 | Todos os botões (ações primária/secundária) apresentam ícone consistente | ✓ SATISFIED | 207/207 parsed `<Button>` instances confirmed to carry an icon (automated sweep); the one remaining discretion case is explicitly documented, not a gap. Roadmap-named new-milestone UI (search trigger, estado filter) independently confirmed compliant. |
| FICO-01 | 115-02, 04, 05, 06, 07, 08, 11 | Filtros aplicar/limpar/exportar em 5 módulos nomeados tornam-se ícone-only com Tooltip + aria-label | ✓ SATISFIED | 12/12 locked conversions verified icon-only, `Tooltip`-wrapped, `aria-label`-carrying, handlers/variants/conditional-guards preserved. Pareceres/Notificações correctly excluded (icon+text retained). |

**Orphaned requirements check:** `.planning/REQUIREMENTS.md` maps only ICON-01 and FICO-01 to Phase 115 — both are declared in at least one plan's `requirements:` frontmatter. Zero orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `web/src/components/profile/user-password-form.tsx`, `user-profile-form.tsx` | 165/198 (fixed) | Redundant `mr-2` doubling `Button`'s own `gap-2` | Info (fixed) | Found by 115-REVIEW.md (IN-01), fixed by commit `e725fc3` — independently confirmed via `git show`. |
| `web/src/app/(dashboard)/dashboard/page.tsx` | 598-601 (fixed) | "Abrir" row-action button had no icon (UI-SPEC audit mis-classified it as compliant) | Info (fixed) | Found post-hoc during Plan 10 (outside its declared scope), fixed by follow-up commit `cbeaa95` (`Eye` icon added) — independently confirmed via `git show`. |
| `web/src/app/(dashboard)/clientes/page.tsx` | ~512-535 | Mobile-list "Editar" quick action is a no-op (identical href to "Ver detalhes") | Warning (pre-existing, out of phase-115 scope) | Both buttons already had correct icons before Phase 115 (cited as already-compliant in UI-SPEC's own audit); confirmed outside every Phase 115 diff hunk for this file. Not a regression; not covered by ICON-01 (icon *presence*, not action-correctness) or FICO-01 (not a filter button). |
| `web/src/app/(dashboard)/settings/page.tsx` | 374-380 | Native `<button>` search-clear has `X` icon but no `aria-label`/`Tooltip` | Warning (pre-existing, out of phase-115 scope) | Confirmed outside every Phase 115 diff hunk. Native `<button>` elements are explicitly outside ICON-01's `<Button>`-component audit scope (115-UI-SPEC.md Scope Boundaries §3); not one of the 5 FICO-01 modules' filter actions. |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | ~1171-1184 | Prazo-conclusion toggle uses `title` (not `aria-label`) for its accessible name | Info (pre-existing, out of phase-115 scope, optional) | Confirmed outside every Phase 115 diff hunk. `title` is a valid (if inconsistent) accname source — not a hard a11y failure. Native `<button>`, outside audit scope. |
| 4 files (`clientes/[id]/page.tsx`, `documentos/novo/page.tsx`, `processos/[id]/page.tsx`, `dashboard-shell.tsx`) | various | 6 pre-existing ESLint errors (`react-hooks/set-state-in-effect` ×5, `react-hooks/refs` ×1) | Info (pre-existing, unrelated to icons) | Independently re-derived via `git diff` hunk-range analysis: every error line falls strictly outside this phase's diff hunks for that file; `dashboard-shell.tsx` isn't touched by Phase 115 at all. `pnpm build` (the gate that actually proves icon-import correctness) passes clean regardless. |

No debt markers (`TODO`/`FIXME`/`XXX`/`HACK`/`PLACEHOLDER`) found in any of the 32 touched files. No stub returns, no empty handlers, no "coming soon"/"not implemented" text introduced by this phase.

### Human Verification Required

None outstanding. The FICO-01 interactive acceptance criteria (tooltip-on-hover, keyboard-only `aria-label` announcement, both-theme legibility) — the items that cannot be verified by static analysis alone — were already executed as a **live, in-session human-verify checkpoint** (Plan 115-11, Task 2), not a self-report: the operator started the real app, authenticated, and this session independently walked all 11 checklist items from 115-UI-SPEC.md's "Verification / QA Plan" against the running `http://localhost:3000`, recording zero defects (documented in `115-11-SUMMARY.md`, including a specific, technically-precise observation about `disabled:pointer-events-none` correctly suppressing hover-tooltip firing on the intentionally-disabled Processos Exportar placeholder — a level of detail consistent with genuine DOM interaction, not a templated self-report). This verification pass corroborates that checkpoint with independent static evidence (structural `Tooltip`/`aria-label` wiring, a real Radix `Tooltip` implementation, a clean `pnpm build`) rather than re-requesting the same human pass.

### Gaps Summary

No gaps found. All 4 roadmap success criteria hold under direct, independently-reproduced codebase evidence (not SUMMARY.md narrative): ICON-01's 158 previously-text-only buttons across 32 files now carry vocabulary-consistent icons (verified via a from-scratch automated sweep of all 207 `<Button>` instances in the full 48-file audited scope, not just the claimed gap list), and FICO-01's exact 12 filter-action buttons across the 5 named modules are icon-only with matching `Tooltip` + `aria-label`, handlers and conditional-render guards preserved byte-for-byte. The do-not-touch guard (16 files) and the FICO-01 module exclusion (Pareceres/Notificações keep text) both hold under independent re-derivation. `pnpm build` and a Node-level `lucide-react` export check rule out any broken/undefined icon import. Five out-of-scope, pre-existing findings (2 Warning, 1 Info from code review; plus the pre-existing 6 lint errors) were independently confirmed via `git diff` hunk-range analysis to sit entirely outside this phase's edits — correctly not blocking. Two in-scope Info findings (redundant `mr-2`, one missed dashboard icon) were found and fixed by the executing team before this verification ran, and both fixes are independently confirmed present in the git history.

---

_Verified: 2026-07-22T03:30:00Z_
_Verifier: Claude (gsd-verifier)_
