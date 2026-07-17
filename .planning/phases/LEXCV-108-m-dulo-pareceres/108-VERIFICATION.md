---
phase: 108-m-dulo-pareceres
verified: 2026-07-17T12:19:23Z
status: human_needed
score: 10/11 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Open a non-CONCLUIDO parecer with 2+ versions AND a CONCLUIDO/delivered parecer at /pareceres/{id} in dark mode, and inspect the 'Histórico de Versões' Accordion + Tooltip composition specifically (not just the Select filter popover already checked)."
    expected: "AccordionTrigger/AccordionContent surfaces, the chevron icon, and the TooltipContent bubble on each timeline marker all render with correct elevation/contrast in dark mode (no flat/invisible surface); the connecting-line/dot marker column remains visually legible against the dark background."
    why_human: "108-04-SUMMARY.md documents that only the quick-filter Select's popover was inspected via computed style in dark mode; the Accordion/Tooltip-specific dark-mode re-check on the detail page 'could not be completed live' due to a Browser-pane JS-interactivity failure that emerged mid-session. This is a visual-rendering check that cannot be confirmed by reading source (the primitives resolve theme tokens at runtime)."
  - test: "Log in as a role lacking the `pareceres:view` permission (e.g. a role without that scope) and load /pareceres, /pareceres/{id}, and /pareceres/nova."
    expected: "AccessDeniedState renders immediately, with no flash of denied-then-content or content-then-denied while the permissions query resolves (confirming the permissions.isFetched fix actually closes the race in the browser, not just in source)."
    why_human: "108-04-SUMMARY.md explicitly states this item was 'NOT independently re-confirmed live this pass' — the executor reasoned by analogy to the isFetched pattern already proven in Phases 103/105/106/107 rather than performing an actual role-switch click-through, which the plan's own acceptance criteria required ('confirmed by an actual role switch, not inferred from code')."
---

# Phase 108: Módulo Pareceres Verification Report

**Phase Goal:** Os formulários de Pareceres usam `Select`, a timeline usa `Tooltip` e o histórico de versionamento colapsa versões antigas via `Accordion`. (PARC-18, PARC-19, PARC-20)
**Verified:** 2026-07-17T12:19:23Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

Every truth below was checked directly against the current committed source (not SUMMARY.md claims), via `Read`/`Grep`, a live `pnpm build`, a live `pnpm lint`, `git show`/diff cross-checks against the pre-phase base commit (`88343be`), and a commit-hash existence sweep — not by trusting the four SUMMARYs, the REVIEW, or the REVIEW-FIX report.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All 6 Pareceres list filters (3 quick + 3 advanced-search) render as Radix `Select` with a working `"todos"` sentinel; zero native `<select>` remain for filtering | VERIFIED | `pareceres/page.tsx`: 6× `<Select value={...} onValueChange={...}>` (draftStatus/draftAdvogadoId/draftClienteId, pesquisaClienteId/pesquisaAdvogadoId/pesquisaStatus), each first `<SelectItem value="todos">Todos</SelectItem>`. `grep -n "<select"` across the file returns zero matches (only `<input type="text"/"date">` remain for the non-select fields, correctly out of scope). |
| 2 | `onApply`/`onClear`/`onPesquisar`/`onLimparPesquisa` correctly translate `"todos"` to "no filter", and the underlying hooks never leak the literal string to the backend | VERIFIED | `onApply` (lines 84-88): `status: draftStatus === "todos" ? "" : draftStatus` (×3). `onClear` (92-95): resets all 3 drafts to `"todos"`. `onPesquisar` (108-110): `if (x && x !== "todos") next.x = x` (×3). `onLimparPesquisa` (119-121): resets the 3 select states to `"todos"`, leaves text/date states `""`. Traced into `use-pareceres.ts`'s `buildParecerSearch`/`buildParecerPesquisaSearch` — both `if (filters.x?.trim())` guard before adding to the querystring, so `""` (post-translation) is correctly dropped, never `"todos"`. |
| 3 | All 5 `NativeSelect` fields (4 in `pareceres/nova/page.tsx` + 1 in `EntregarParecerDialog`) are real `NativeSelect` components, not native `<select>` or a different primitive | VERIFIED | `nova/page.tsx`: `<NativeSelect id="clienteId"`, `id="processoId"`, `id="prioridade"`, `id="advogadoId"`, all `size="default" className="w-full rounded-none"`, all `{...form.register(...)}`-bound. `[id]/page.tsx`: `<NativeSelect id="versaoFinalId" size="default" className="w-full" value={selectedVersaoId ?? ""} onChange={...}>` (controlled, no `rounded-none`/height change, matching UI-SPEC finding #4). Zero `<select` anywhere in either file. |
| 4 | `nova/page.tsx`'s dead `selectClassName` constant is deleted; `textareaClassName` is preserved (still feeds the `descricao` textarea) | VERIFIED | `grep -c "const selectClassName"` → 0. `const textareaClassName = "..."` still present (line 26) and still consumed by the `descricao` `<textarea className={textareaClassName}>` (line 159). |
| 5 | The "Histórico de Versões" timeline uses `Accordion`, with exactly one item open by default — the most-recent version, or the delivered version (`versaoFinalId`) when `CONCLUIDO` | VERIFIED | `[id]/page.tsx`: `<Accordion key={defaultOpenVersaoId} type="single" collapsible defaultValue={defaultOpenVersaoId}>` wraps `sortedVersoes.map(...)` → `<AccordionItem value={versao.id}>`. `sortedVersoes` is a `React.useMemo` sorted descending by `numeroVersao`. `defaultOpenVersaoId = isConcluido ? (parecer.data?.versaoFinalId ?? sortedVersoes[0]?.id) : sortedVersoes[0]?.id` — correctly branches on delivery status. This is the project's first and only `Accordion` consumer (`grep -rn "Accordion"` over `web/src` returns matches only in `accordion.tsx` itself and this one file). |
| 6 | Every timeline version marker carries a keyboard-accessible `Tooltip` (the marker `<span>` is not natively focusable) labeled "Versão atual"/"Versão entregue"/"Versão anterior" | VERIFIED | Marker `<span tabIndex={0} aria-label={versaoTooltipLabel(versao, index)} className="h-2.5 w-2.5 rounded-full ...">` wrapped in `<Tooltip><TooltipTrigger asChild>...</TooltipTrigger><TooltipContent>{versaoTooltipLabel(versao, index)}</TooltipContent></Tooltip>`. `versaoTooltipLabel` returns exactly the 3 contracted strings, branching on `isConcluido`/`index === 0`/`versao.id === defaultOpenVersaoId`. `aria-label` and `TooltipContent` share the same derivation, so they can't drift. |
| 7 | All 7 bundled RBAC sites across the 3 files use `permissions.isFetched`, not `permissions.isLoading` | VERIFIED | Counted exactly 7: `page.tsx:30` (view gate); `nova/page.tsx:33` (view gate), `:208` (submit-disable, `!permissions.isFetched`); `[id]/page.tsx:92` (view gate), `:183` (`showNovaVersaoForm`), `:184-185` (`showEntregarTrigger`), `:242` (`!permissions.isFetched ? (` loading-skeleton guard, correctly inverted polarity). `grep -rn "permissions.isLoading"` across all 3 files returns **zero** matches. |
| 8 | `isResponsavelOuAdmin` instance-level RBAC logic (advogado responsável OR ADMIN) was left untouched, not modified beyond what the plan allowed | VERIFIED | `git show 88343be:"web/src/app/(dashboard)/pareceres/[id]/page.tsx"` vs. current: the boolean expression `Boolean(me?.roles.includes("ADMIN")) \|\| Boolean(parecer.data?.advogadoId && parecer.data.advogadoId === me?.id)` is byte-identical in both — only its assignment now spans 3 lines instead of 1 due to unrelated surrounding reflow. No logic change. |
| 9 | The 9 code-review findings from the fix loop (CR-01, WR-01 through WR-08) are genuinely reflected in the current file state, not just claimed in the REVIEW-FIX report | VERIFIED | Spot-checked all 9 directly against current source: **CR-01** — `EntregarParecerDialog` now receives `sortedVersoes` (line 261) and its `defaultVersaoId` correctly indexes `[0]` (newest-first, line 483). **WR-01** — `usePesquisarPareceres(pesquisaFilters, { enabled: pesquisaSubmitted })` (page.tsx:74). **WR-02** — `advogados` filter includes `&& u.ativo !== false` in both `page.tsx:64` and `nova/page.tsx:77`. **WR-03** — `form.setValue("processoId", "")` (nova/page.tsx:72), not `undefined`. **WR-04** — `<Accordion key={defaultOpenVersaoId} ...>` (line 290) forces a remount when the default-open target changes. **WR-05** — `use-pareceres.ts`'s `xhr.onload` failure branch parses `JSON.parse(xhr.responseText)` for `message`/`error` (lines 176-177). **WR-06** — `formatDate` in both `lib/pareceres.ts` and `[id]/page.tsx` uses the regex-based local-`Date` construction (`/^(\d{4})-(\d{2})-(\d{2})/`) instead of raw `new Date(dateOnlyString)`, avoiding the UTC-midnight-off-by-one-day bug. **WR-07** — `id="pesquisa-texto"`/`htmlFor`, `id="pesquisa-data-inicio"`/`id="pesquisa-data-fim"` pairings present. **WR-08** (iteration-3 fix) — `EntregarParecerDialog`'s `onOpenChange` now has `if (!next) setSelectedVersaoId(null);` (line 511). All 17 cited commit hashes (`56e0c47` … `c6ebaa2`, plus the review-loop commits `c871554`/`4c48a41`/`70c0a7b`/`f83c711`/`16edcb5`/`9baf465`/`4598c80`/`1d319c0`/`7055e0c`) exist in git history (`git cat-file -e`, all resolved). |
| 10 | `pnpm build` and `pnpm lint` pass with zero new errors introduced by this phase | VERIFIED | Independently re-ran both (not trusting 108-04-SUMMARY.md's claim): `pnpm build` → `Compiled successfully in 20.0s`, TypeScript pass clean, all 24/24 routes generated. `pnpm lint` → 6 errors / 18 warnings project-wide; **zero** are in `pareceres/page.tsx` or `pareceres/[id]/page.tsx`; the single issue in `pareceres/nova/page.tsx` (`react-hooks/incompatible-library` on `form.watch("clienteId")`) is confirmed pre-existing via `git show 88343be:...` — the same `form.watch("clienteId")` line already existed at the pre-phase base commit. |
| 11 | A human has visually confirmed the 6 `Select` filters, 5 `NativeSelect` fields, timeline `Tooltip`s (hover + keyboard), and `Accordion` default-open behavior in **both** light and dark themes and across the RBAC role matrix, via live interaction | **UNCERTAIN — human_needed** | 108-04-SUMMARY.md documents 6 of 8 checklist items fully live-verified with concrete network/DOM evidence (Select filters, NativeSelect fields, non-CONCLUIDO Accordion/Tooltip incl. a genuine keyboard-Tab reachability check, CONCLUIDO "Versão entregue" branch, EntregarParecerDialog, and the `isResponsavelOuAdmin` positive/negative case). **2 of 8 were not completed live**, per the executor's own transparent admission: (a) dark-mode rendering of the Accordion/Tooltip composition specifically on the detail page (only the Select popover was dark-mode-inspected via computed style), and (b) the RBAC no-flash check for a role lacking `pareceres:view` (reasoned by analogy to prior phases rather than an actual role-switch click-through). Both were blocked by a documented, plausible Browser-pane JS-interactivity failure (reproduced app-wide, not route-specific, survived 2 dev-server restarts) — not a code defect, but the plan's own acceptance criteria required these two specifically be "confirmed by an actual role switch, not inferred from code" and real dark-mode visual inspection, which did not happen. Routed to human verification below rather than accepted on source-analysis alone. |

**Score:** 10/11 truths verified programmatically + directly observed; 1 truth is UNCERTAIN pending human live confirmation of 2 specific checklist items.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/pareceres/page.tsx` | 6 Radix `Select` filters + `"todos"`-sentinel filter logic + `isFetched` gate | VERIFIED | All markers present (Truths #1-#2, #7); `pnpm build` compiles it clean; zero lint issues. |
| `web/src/app/(dashboard)/pareceres/nova/page.tsx` | 4 `NativeSelect` form fields + `selectClassName` deleted + `isFetched` (2 sites) | VERIFIED | All markers present (Truths #3-#4, #7); 1 pre-existing lint warning unrelated to this phase's diff. |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` | dialog `NativeSelect` + `Tooltip`/`Accordion` timeline composition + `isFetched` (4 sites) + `isResponsavelOuAdmin` preserved | VERIFIED | All markers present (Truths #3, #5-#9); zero lint issues; first project-wide `Accordion` consumer confirmed via repo-wide grep. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `pareceres/page.tsx` | `@/components/ui/select` | `import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue }` | WIRED | Import present (line 10); all 6 call sites use it; `pnpm build` confirms it resolves and type-checks. |
| `pareceres/nova/page.tsx` | `@/components/ui/native-select` | `import { NativeSelect }` | WIRED | Import present (line 13); 4 call sites, all `register()`-bound and functioning per the successful build. |
| `pareceres/[id]/page.tsx` | `@/components/ui/native-select` | `import { NativeSelect }` | WIRED | Import present (line 29); 1 call site, controlled `value`/`onChange`. |
| `pareceres/[id]/page.tsx` | `@/components/ui/accordion` | `import { Accordion, AccordionContent, AccordionItem, AccordionTrigger }` | WIRED | Import present (lines 19-24); `Accordion`/`AccordionItem`/`AccordionTrigger`/`AccordionContent` all rendered in the timeline JSX (lines 289-348). |
| `pareceres/[id]/page.tsx` | `@/components/ui/tooltip` | `import { Tooltip, TooltipContent, TooltipTrigger }` | WIRED | Import present (line 31); rendered once per version marker inside the `.map` (lines 302-311). |
| `pareceres/page.tsx`/`[id]/page.tsx` filter/select state | `use-pareceres.ts`'s `buildParecerSearch`/`buildParecerPesquisaSearch` | `filters.x?.trim()` empty-string drop | WIRED | Confirmed the `"todos"`→`""` sentinel translation happens before these hooks are called, and both builder functions correctly omit empty values from the querystring (Truth #2). |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `Select` filter options (Advogado/Cliente, both quick + advanced) | `advogados`/`clientes.data` | `useAdminUsers()` (filtered by role+`ativo`) / `useClientes({})` — real TanStack Query hooks against `/admin/users` and `/clientes` | Yes — live-scoped backend data, not hardcoded | FLOWING |
| `NativeSelect` `clienteId`/`processoId`/`advogadoId` options (`nova/page.tsx`) | `clientes.data`/`processos.data`/`advogados` | `useClientes`, `useProcessos` (cliente-scoped), `useAdminUsers` — all real hooks | Yes | FLOWING |
| `Accordion`/`Tooltip` timeline | `sortedVersoes` (derived from `versoes.data`) | `useParecerVersoes(id)` — real backend-scoped TanStack Query | Yes — not a static/empty array; empty-state branch (`!versoes.data?.length`) correctly short-circuits before the Accordion renders | FLOWING |
| `EntregarParecerDialog`'s `versaoFinalId` `NativeSelect` options | `versoes` prop (= `sortedVersoes`) | Passed down from the parent's real `useParecerVersoes` data (post-CR-01 fix) | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full app builds with all 3 migrated Pareceres files included | `cd web && pnpm build` | `Compiled successfully in 20.0s`, TypeScript clean, 24/24 routes generated | PASS |
| No new lint regressions introduced by this phase's diffs | `cd web && pnpm lint` (cross-checked via `git show` against base commit `88343be`) | 6 errors/18 warnings project-wide; 0 in `page.tsx`/`[id]/page.tsx`; the 1 warning in `nova/page.tsx` traced to a pre-existing `form.watch()` call | PASS |
| Zero native `<select>` remain across the 3 modified files | `grep -n "<select" <3 files>` | 0 matches | PASS |
| Zero `permissions.isLoading` remain across the 3 modified files | `grep -rn "permissions.isLoading" <3 files>` | 0 matches | PASS |
| `isResponsavelOuAdmin` logic unchanged vs. pre-phase base commit | `git show 88343be:.../[id]/page.tsx` vs. current, diffed manually | Byte-identical boolean expression | PASS |
| All 9 code-review fix commits (CR-01, WR-01–08) exist and their diffs are present in current source | `git cat-file -e <17 hashes>` + direct `Read` spot-checks | All 17 hashes resolve; all 9 fixes confirmed present in source | PASS |

### Probe Execution

No formal `scripts/*/tests/probe-*.sh` convention exists in this repository for frontend UI-migration phases (no automated behavioral test suite for this kind of change) — SKIPPED. The equivalent evidence for this phase is the `pnpm build`/`pnpm lint` gate plus the 8-point human checkpoint transcript in `108-04-SUMMARY.md` (6/8 items with live evidence, 2/8 routed to Human Verification below), consistent with the same pattern Phase 105's verification used.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PARC-18 | 108-01, 108-02, 108-03 | Campos de formulário de Pareceres usam `Select` (list filters) / `NativeSelect` (RHF-bound + single-purpose modal fields, per the established project convention) | SATISFIED | Truths #1-#4, #7-#8 |
| PARC-19 | 108-03 | Eventos da timeline de Pareceres usam `Tooltip` | SATISFIED | Truth #6 |
| PARC-20 | 108-03 | Histórico de versionamento usa `Accordion` para colapsar versões antigas | SATISFIED | Truth #5 |

**Documentation-accuracy finding (not a functional gap):** `.planning/REQUIREMENTS.md` still marks **PARC-18, PARC-19, and PARC-20 as `[ ]`/"Pending"** in both the checkbox list (lines 58-60) and the Traceability table (lines 130-132), even though every plan's own frontmatter (`requirements: [PARC-18]`/`[PARC-18, PARC-19, PARC-20]`) and every SUMMARY's `requirements-completed` list confirms all 3 were worked in this phase, and this verification pass independently confirmed all 3 are implemented in the current codebase. This is the same class of sync gap Phase 105's own VERIFICATION.md flagged for CLP-01/03/05. **Recommend:** update the REQUIREMENTS.md checkboxes/traceability rows for PARC-18/19/20 to `[x]`/"Complete" as part of closing this phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` debt markers found in any of the 3 files modified by this phase (word-boundary scan; the string `"todos"` is the intentional Select sentinel value, not a `TODO` marker; `placeholder=` matches are legitimate HTML input-placeholder attributes) | — | None — clean |
| `pareceres/page.tsx:61`, `nova/page.tsx:71`, `[id]/page.tsx:143` | — | Pre-existing bug (not introduced by this phase, confirmed via `git show 88343be`): `useAdminUsers()` called unconditionally with no `enabled` guard, but its backend endpoint (`GET /api/v1/admin/users`) is `hasRole('ADMIN')`-gated — causes a spurious `500` toast and an unpopulated "Advogado" `NativeSelect` for any non-ADMIN role | INFO | Out of this phase's Select/NativeSelect/Tooltip/Accordion component-migration scope; already discovered, root-caused, and tracked as a background task in `deferred-items.md` (`task_477b81da`) — not silently dropped. |
| `108-REVIEW.md` IN-01 through IN-05 | various | 5 Info-level code-review findings left intentionally unfixed (duplicated `formatDate`/`statusVariant` copy, `as any` resolver cast, repeated `undefined as unknown as FileList` casts, double `usePermissions()` calls, un-paired `<label>`s for the 3 Pesquisa Avançada `Select` fields) | INFO | Explicitly scoped out of the Critical/Warning-only fix pass per `108-REVIEW-FIX.md`; none affect PARC-18/19/20 functionality. |

### Human Verification Required

### 1. Dark-mode Accordion + Tooltip rendering on the detail-page timeline

**Test:** Open a non-CONCLUIDO parecer with 2+ versions at `/pareceres/{id}` in dark mode. Inspect the "Histórico de Versões" `Accordion`/`Tooltip` composition specifically — expand/collapse a version, hover a marker dot to trigger its tooltip.
**Expected:** `AccordionTrigger`/`AccordionContent` surfaces, the chevron icon, and the `TooltipContent` bubble all render with correct elevation/contrast against the dark background (no flat, invisible, or unreadable surface); the dot-and-connecting-line marker column remains legible.
**Why human:** 108-04-SUMMARY.md documents that only the quick-filter `Select` popover was dark-mode-inspected via computed style; the Accordion/Tooltip-specific dark-mode check on the detail page "could not be completed live" due to a Browser-pane JS-interactivity failure. This is a rendering/visual check that source-reading cannot substitute for.

### 2. RBAC no-flash confirmation for a role lacking `pareceres:view`

**Test:** Log in as a role/user that lacks the `pareceres:view` scope and load `/pareceres`, `/pareceres/{id}`, and `/pareceres/nova`.
**Expected:** `AccessDeniedState` renders immediately on each route, with no flash of denied-then-content or content-then-denied while the `permissions` query resolves.
**Why human:** 108-04-SUMMARY.md explicitly states this check was "NOT independently re-confirmed live this pass" for Phase 108 — it was accepted by analogy to the same `isFetched` pattern already proven working in Phases 103/105/106/107, not by an actual role-switch click-through, which the plan's own Task 2 acceptance criteria required ("confirmed by an actual role switch, not inferred from code").

### Gaps Summary

No functional gaps were found in the shipped code: all 6 `Select` filters, all 5 `NativeSelect` fields, the `Accordion`+`Tooltip` timeline composition, the 7 bundled RBAC `isFetched` fixes, the untouched `isResponsavelOuAdmin` logic, and all 9 code-review fixes (CR-01, WR-01–08) are genuinely present and correct in the current source — independently confirmed via direct file reads, targeted greps, a live `pnpm build`/`pnpm lint`, and `git show` diffs against the pre-phase base commit, not by trusting the SUMMARY/REVIEW/REVIEW-FIX narratives.

The phase is held at `human_needed` status for one reason only: the Wave-2 blocking human-verify checkpoint (108-04's Task 2) itself was not fully completed live — 6 of its 8 checklist items were, with concrete evidence, but 2 (dark-mode Accordion/Tooltip rendering, and the RBAC no-flash check for an unauthorized role) were substituted with source/pattern analysis after a documented Browser-pane tooling failure. Since the plan's own acceptance criteria required these be confirmed by actual live interaction rather than code inference, this verification routes them to a human for final sign-off rather than accepting the substitution — consistent with how Phase 105's verification handled an analogous incomplete-live-check gap (2 of 4 RBAC roles not click-verified). A separate, pre-existing `useAdminUsers()` bug was found during UAT, is out of this phase's scope, and is already tracked as a background task — it does not block this phase's closure.

---

_Verified: 2026-07-17T12:19:23Z_
_Verifier: Claude (gsd-verifier)_
