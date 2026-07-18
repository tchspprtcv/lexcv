---
phase: 105-m-dulos-clientes-processos-combinados
verified: 2026-07-16T21:00:00Z
status: human_needed
score: 8/9 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Log in as teste.advogado@lexcv.cv and teste.assistente@lexcv.cv (password Teste123!, both already seeded in the dev tenant per 105-06-SUMMARY.md) and click through both fichas at a real ~375px mobile width"
    expected: "Cliente ficha: ADVOGADO and ASSISTENTE both see Processos+Pareceres tabs (both have processos:view/pareceres:view per DatabaseSeeder.seedRbac); tab bar scrolls horizontally (overflow-x-auto). Processo ficha: ADVOGADO sees the Auditoria tab (has processos:manage); ASSISTENTE does NOT see it (lacks processos:manage, same as already-verified TECNICO); tab bar wraps to multiple rows (flex-wrap) and all tabs remain reachable."
    why_human: "This is the one item 105-06-SUMMARY.md itself documents as not directly click-verified live, due to browser-tooling instability late in the checkpoint session (reproduced across a dev-server restart and a browser-process restart, assessed as session/infrastructure degradation). ADMIN and TECNICO were live click-verified; ADVOGADO/ASSISTENTE were not. Static analysis (backend DatabaseSeeder.seedRbac permission grants + frontend permissions.ts's purely scope-based, role-name-agnostic gating logic) strongly corroborates correct behavior, but does not substitute for an actual DOM/visual check of these 2 specific roles at mobile width, which the phase's own 105-06-PLAN.md explicitly required ("verified against the real 4-role matrix ... at real mobile widths")."
---

# Phase 105: Módulos Clientes + Processos (combinados) Verification Report

**Phase Goal:** A Ficha de Cliente e a Ficha de Processo usam `Tabs` reais e acessíveis em vez de botões-toggle manuais, e os seus formulários/listagens usam `Select`/`Avatar`/`Breadcrumb` oficiais — entregues em conjunto, nunca isoladamente.
**Verified:** 2026-07-16T21:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

Merged from ROADMAP.md Success Criteria (5) + 105-06-PLAN.md's own closing-gate must-haves (4). Every truth below was checked directly against the current committed source (not SUMMARY.md claims) via `grep`/`Read`, a live `pnpm build`, a live `pnpm lint`, and `git blame` cross-checks for pre-existing-vs-introduced classification.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Ficha de Cliente's 7 tabs implemented with real `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent`, RBAC-conditional trigger count preserved, `overflow-x-auto` preserved | VERIFIED | `clientes/[id]/page.tsx`: 7×`<TabsTrigger`, 7×`<TabsContent`, `<Tabs value={tab} onValueChange=...>` (line 451), `processos`/`pareceres` triggers conditionally rendered (`{canViewProcessos ? <TabsTrigger.../> : null}`, lines 456-457), each with an inner `!permissions.isFetched / canView... / <AccessDeniedState>` 3-way gate (lines 864-882) so gated content never renders from tab state alone. `overflow-x-auto` present (1 occurrence, wrapping `<TabsList>`). Zero `variant={tab ===` toggle-button remnants. |
| 2 | Ficha de Processo (Partes/Fases/Decisões/Factos/Testemunhas/Documentos) uses the same `Tabs` pattern, delivered in the same phase as the Cliente ficha | VERIFIED | `processos/[id]/page.tsx`: 8×`<TabsTrigger`, 8×`<TabsContent` (timeline/partes/fases/decisoes/factos/testemunhas/documentos/auditoria). `auditoria` trigger omitted when `!canManageProcessos` (line 1278) with matching inner gate (line 2276). Partes/Fases/Testemunhas render via reconciled `Table`/`TableHeader`/`TableRow`/`TableHead`/`TableBody`/`TableCell` (56 matches); Documentos renders via shared `<DataTable columns={columns(canEditDocumentos)} data={documentos} getRowId={(d) => d.id} />` (line 2658) fed by a new `documentos-columns.tsx`. Decisões/Factos remain raw `<table>` — confirmed via line numbers (1886, 2039) that this is exactly the 2 tabs explicitly excluded by 105-CONTEXT.md/105-UI-SPEC.md (only Partes/Fases/Testemunhas/Documentos were named), not a missed migration. |
| 3 | All native `<select>` in Clientes/Processos forms/listings replaced with `NativeSelect`/`Select` | VERIFIED | Zero raw `<select` in all 8 in-scope files (`clientes/[id]/page.tsx`, `processos/[id]/page.tsx`, `clientes/page.tsx`, `processos/page.tsx`, `clientes/novo/page.tsx`, `processos/novo/page.tsx`, `processos/[id]/editar/page.tsx`, `clientes/merge/page.tsx`) — the only 2 remaining string matches (`processos/[id]/page.tsx` lines 359, 2367) are code-comment prose, not JSX, confirmed by direct read. All `selectClassName` consts deleted (0 matches across all 5 files that had one); all `textareaClassName` consts retained (1 each) since they feed unrelated `<textarea>` elements. Zero `@/components/ui/select` (Radix combobox) imports anywhere — `NativeSelect` used uniformly per the locked decision. All `NativeSelect` call sites (24 across the ficha files + 13 across the secondary pages = confirmed count-for-count) carry `className="w-full"` (post-review-fix WR-01), preventing the shrink-to-fit visual regression the code review caught. |
| 4 | `Avatar` used for advogados/administrativos/testemunhas in listings/pickers | VERIFIED | `clientes/[id]/page.tsx`: shared `ResponsaveisCard` (2 call sites — Advogados + Administrativos, line 822/831) renders `<Avatar size="sm"><AvatarFallback>{deriveInitials(u.nome)}</AvatarFallback></Avatar>` (lines 1673-1674). `processos/[id]/page.tsx`: Testemunhas "Nome" `TableCell` renders the same pattern (lines 2233-2234); Tipo/Contacto/Ações cells unchanged. Partes table (lines 1620-1642) confirmed to have NO Avatar — correctly excluded per CLP-04. `AvatarFallback`'s default styling (`bg-muted text-muted-foreground`, `avatar.tsx:52`) is used with no className override anywhere — the accent-tinted `bg-blue-50` chip styling from the Clientes list (Phase 104, out of scope) was correctly NOT reused. |
| 5 | Ficha headers use `Breadcrumb` instead of `<div>+Link+"/"` | VERIFIED | All 6 in-scope pages (`clientes/[id]`, `processos/[id]`, `clientes/novo`, `processos/novo`, `clientes/merge`, `processos/[id]/editar`) render `BreadcrumbList`/`BreadcrumbItem`/`BreadcrumbLink asChild`/`BreadcrumbSeparator`/`BreadcrumbPage`. `processos/[id]/editar` correctly renders a 3-level breadcrumb (`Processos` → `{processo.data?.numero}` link back to the ficha → `Editar`), confirmed via direct read (lines 119-131). `clientes/page.tsx`/`processos/page.tsx` (list pages) correctly have NO Breadcrumb — out of the 6-page scope per 105-CONTEXT.md. |
| 6 | A holistic `pnpm build` + `pnpm lint` across `web/` passes with zero new errors | VERIFIED | Ran both directly (not trusting SUMMARY claims): `pnpm build` → `Compiled successfully in 15.7s`, TypeScript clean, all 24 routes generated, zero errors. `pnpm lint` → 6 errors / 18 warnings; every one of the 6 issues in the 2 ficha files (`clientes/[id]/page.tsx` ×4, `processos/[id]/page.tsx` ×2) was traced via `git blame` to commits dated 2026-06-17 / 2026-07-04 — weeks before Phase 105 started (2026-07-16). None are on lines this phase's diffs touched (confirmed: `textareaClassName` unused-var and the `?tab=` re-sync `set-state-in-effect` finding match `deferred-items.md`'s own pre-existing-and-out-of-scope documentation exactly). |
| 7 | A regression sweep confirms zero raw `<select>`/`<table>` and zero `variant={tab===...}` toggle buttons remain across the 9 in-scope files, both `selectClassName` declarations gone, both `textareaClassName` declarations survive | VERIFIED | Independently re-ran every one of these assertions directly against the current committed source (see Truths #1-#3 evidence above for the concrete grep outputs) rather than trusting 105-06-SUMMARY.md's reported sweep — all hold. |
| 8 | A human reviewer confirms both fichas render correct Tabs/NativeSelect/Avatar/Breadcrumb in light AND dark themes | VERIFIED (as documented, live) | 105-06-SUMMARY.md documents this directly observed for ADMIN in both dark and light theme on the Cliente ficha (`role="tablist"`/`role="tab"` confirmed via accessibility tree, `overflow-x-auto` measured via computed layout at 375px: wrapper `clientWidth=311px` vs content `scrollWidth=803px`) and for the Processo ficha (8 tabs, `?tab=documentos` deep link, live Parte/Testemunha creation rendering correctly through the new `Table`/`Avatar` primitives, Fases inline-select+Guardar flow exercised end-to-end). Treated as directly-observed evidence per this task's framing, not a SUMMARY claim taken on faith — it is corroborated by the code-level evidence in Truths #1-#5 above. |
| 9 | The RBAC-conditional tab set is verified against the real 4-role matrix (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) at real mobile widths | **UNCERTAIN — human_needed** | ADMIN and TECNICO were live click-verified (105-06-SUMMARY.md: "TECNICO ... Processo ficha correctly showed 7 tabs with Auditoria omitted ... confirmed the gated trigger is entirely absent from the DOM"). ADVOGADO and ASSISTENTE were NOT live click-verified — 105-06-SUMMARY.md transparently documents this as a residual gap caused by browser-tooling instability late in the session (reproduced across a dev-server restart and a full browser-process restart, ruling out server-side state). I independently cross-checked the gating *code path* (`web/src/lib/permissions.ts`'s `hasScopedPermission`) is purely scope-array-based with **zero role-name branching**, and confirmed via `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:315-352` that ASSISTENTE lacks `processos:manage` (same as TECNICO, so Auditoria should be correctly omitted) while ADVOGADO has it (so Auditoria should correctly render) — both have `processos:view`/`pareceres:view` (so Cliente ficha's 2 gated tabs should render for both). This is strong static corroboration but does not substitute for the live DOM/mobile-width check the phase's own plan required for all 4 roles. |

**Score:** 8/9 truths verified programmatically + directly observed; 1 truth is UNCERTAIN pending human click-through (2 of 4 roles).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | Tabs + NativeSelect + Avatar + Breadcrumb | VERIFIED | All markers present (see Truths #1, #3, #4, #5); `pnpm build` compiles it clean. |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` | Tabs (8) + Table primitives + Avatar + Breadcrumb + DataTable | VERIFIED | All markers present (see Truths #2-#5); mobile flex-wrap fix confirmed live in source (`className="h-auto w-full flex-wrap"`, line 1270). |
| `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx` | `columns(canEditDocumentos): ColumnDef<Documento>[]` factory | VERIFIED | File exists, 195 lines, exports `columns()`, reuses `confidencialidadeVariant()`, no `id: "processo"` column, RBAC-gated `Apagar` action (`canEditDocumentos` check in `DocumentoAcoesCell`, lines 106-110). |
| `web/src/app/(dashboard)/clientes/page.tsx`, `clientes/novo/page.tsx`, `clientes/merge/page.tsx` | NativeSelect (+ Breadcrumb on novo/merge) | VERIFIED | Confirmed per-file (Truth #3, #5); list page correctly has no Breadcrumb. |
| `web/src/app/(dashboard)/processos/page.tsx`, `processos/novo/page.tsx`, `processos/[id]/editar/page.tsx` | NativeSelect (+ Breadcrumb on novo/editar) | VERIFIED | Confirmed per-file; `processos/novo`'s `h1` deliberately preserved as `font-bold text-slate-900` (out of scope for the weight fix per UI-SPEC Scope note #4) — confirmed still present, not swept. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `clientes/[id]/page.tsx` | existing `tab`/`setTab` `useState` | `Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}` | WIRED | Confirmed at line 451; state untouched, Tabs is a pure controlled consumer. |
| `clientes/[id]/page.tsx` | `canViewProcessos`/`canViewPareceres` | conditional `TabsTrigger` omission + inner `TabsContent` gate | WIRED | Confirmed both layers present (lines 456-457 for triggers; 864-882 for content, now additionally gated by `permissions.isFetched` per the WR-02 code-review fix). |
| `processos/[id]/page.tsx` | existing `?tab=` `useSearchParams`/`useEffect` sync | `Tabs value={tab} onValueChange` reading/writing the same state | WIRED | Confirmed `searchParams.get("tab")` sync block present and untouched (line ~254, matches `deferred-items.md`'s documented WR-03/Phase-87 effect, unmodified by this phase). |
| `processos/[id]/page.tsx` (`ProcessoDocumentosTab`) | `documentos-columns.tsx` | `<DataTable columns={columns(canEditDocumentos)} data={documentos} getRowId={(d) => d.id} />` | WIRED | Confirmed at line 2658; `documentos` sourced from the pre-existing `useDocumentos({ processo_id: processoId })` hook (line 2507) — no hardcoded/empty data, real backend-scoped query. `isLoading`/`isError`/"Nenhum documento registado." guards preserved around it. |
| `processos/[id]/editar/page.tsx` | `useProcesso(id)` (already loaded) | 3-level Breadcrumb middle crumb via `processo.data?.numero` | WIRED | Confirmed lines 125-127; no new fetch introduced. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| Processo Documentos `DataTable` | `documentos` | `useDocumentos({ processo_id: processoId })` (pre-existing hook, unchanged) | Yes — real backend-scoped TanStack Query, not a static/empty return | FLOWING |
| Cliente/Processo `Avatar` initials | `deriveInitials(nome)` | `u.nome`/`t.nome` from already-loaded entity data (`ResponsaveisCard` props / `testemunhas.data`) | Yes — derived from real loaded records, not hardcoded | FLOWING |
| Both `Tabs` roots | `tab` | Pre-existing `useState<TabKey>` (Cliente) / `useState` seeded from `?tab=` (Processo) | Yes — unchanged existing state, not newly hardcoded | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full app builds with the migrated fichas included | `cd web && pnpm build` | `Compiled successfully in 15.7s`, TypeScript clean, 24/24 routes generated | PASS |
| No new lint regressions introduced by this phase's diffs | `cd web && pnpm lint` (cross-checked via `git blame`) | 6 errors/18 warnings, all traced to pre-Phase-105 commits (2026-06-17 / 2026-07-04) | PASS |
| Zero raw `<select>` remain (JSX, not comments) | `grep -n "<select" <8 files>` | 2 matches total, both confirmed code-comment prose via direct read | PASS |
| Zero manual toggle-button tab bars remain | `grep -c "variant={tab ===" <2 ficha files>` | 0 in both | PASS |

Live-server/browser behavioral checks (dev server, RBAC login flows) were not re-run by this verification pass — see Human Verification section; this mirrors the project's established pattern of trusting a *documented, evidenced* live checkpoint (105-06-SUMMARY.md's screenshots-via-computed-layout and accessibility-tree evidence) rather than re-running the dev server from a stateless verifier pass.

### Probe Execution

No formal `scripts/*/tests/probe-*.sh` convention exists in this repository for frontend UI phases (no automated test suite for frontend UI behavior, per the phase's own framing) — SKIPPED. The equivalent evidence for this phase is the `pnpm build`/`pnpm lint` gate (Behavioral Spot-Checks above) plus the human checkpoint transcript in 105-06-SUMMARY.md.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| CLP-01 | 105-01 | Ficha de Cliente → Tabs, RBAC-conditional, overflow-x-auto | SATISFIED | Truth #1 |
| CLP-02 | 105-02, 105-03 | Ficha de Processo → Tabs, same phase as CLP-01 | SATISFIED | Truth #2 |
| CLP-03 | 105-01, 105-02, 105-04, 105-05 | All native `<select>` → NativeSelect/Select | SATISFIED | Truth #3 |
| CLP-04 | 105-01, 105-03 | Avatar for advogados/administrativos/testemunhas | SATISFIED | Truth #4 |
| CLP-05 | 105-01, 105-02, 105-04, 105-05 | Breadcrumb replacing ad hoc div headers | SATISFIED | Truth #5 |

**Documentation-accuracy finding (not a functional gap):** `.planning/REQUIREMENTS.md`'s checkbox list and Traceability table still mark **CLP-01, CLP-03, and CLP-05 as `[ ]`/"Pending"**, while CLP-02 and CLP-04 are correctly marked `[x]`/"Complete" — even though every plan's own frontmatter (`105-01-PLAN.md requirements: [CLP-01, CLP-03, CLP-04, CLP-05]`, etc.) and every SUMMARY's `requirements-completed` list confirms all 5 were worked in this phase, and this verification pass independently confirmed all 5 are implemented in the current codebase. This looks like an incomplete requirements-checklist sync step, not a missing implementation. **Recommend:** update `.planning/REQUIREMENTS.md` checkboxes/traceability rows for CLP-01/CLP-03/CLP-05 to `[x]`/"Complete" before closing the phase, so the requirements ledger matches the verified reality.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` debt markers found in any of the 11 files modified by this phase (precise word-boundary scan, false positives from `placeholder=` HTML attributes excluded) | — | None — clean |
| `processos/[id]/page.tsx:177` | 177 | `const textareaClassName` unused (`@typescript-eslint/no-unused-vars`) | INFO | Pre-existing, confirmed via `deferred-items.md` + `git blame` (present in the base commit before Phase 105 started); explicitly out of scope per this plan's own instructions (`textareaClassName` retained deliberately for a different, untouched `<textarea>` consumer). |
| `processos/[id]/page.tsx:256` | 256 | `setTab(p as TabKey)` inside `useEffect` (`react-hooks/set-state-in-effect`) | INFO | Pre-existing, by-design (WR-03, Phase 87 code review) — the `?tab=` deep-link re-sync effect. Confirmed unchanged by this phase via `deferred-items.md` + `git blame`. |
| `clientes/[id]/page.tsx` (4 lines: 370, 1615, 1766, 1970) | various | `react-hooks/incompatible-library` (1) + `react-hooks/set-state-in-effect` (3) | INFO | All confirmed pre-existing via `git blame` (2026-06-17 and 2026-07-04 commits, weeks before this phase started 2026-07-16). |
| `documentos-columns.tsx:2658` (`processos/[id]/page.tsx`) | 2658 | `columns(canEditDocumentos)` factory called inline on every render instead of memoized | INFO (carried forward from 105-REVIEW.md IN-03) | Not a functional bug — `DataTable`'s own internal `useState` retains sort/visibility state independent of the columns array identity. Cosmetic inconsistency with `clientes/page.tsx`/`processos/page.tsx`'s memoized equivalents. Non-blocking. |
| `clientes/[id]/page.tsx` + `processos/[id]/page.tsx` + `clientes/page.tsx` | multiple | `deriveInitials` logic duplicated 3× (IN-02, carried forward) | INFO | Cosmetic duplication, not a defect — all 3 copies are byte-identical and correctly produce the same initials. |

No Critical or Warning-level anti-patterns found in the current codebase. This matches 105-REVIEW.iter2.md's independent re-review finding (0 critical, 0 warning, 4 info — all 4 fixes from iteration 1 confirmed correctly and completely applied with zero regression), which this verification pass independently corroborated by re-deriving the same evidence from source rather than trusting the review report's conclusions.

### Human Verification Required

### 1. ADVOGADO/ASSISTENTE live RBAC × mobile-width click-through

**Test:** Log in as `teste.advogado@lexcv.cv` and `teste.assistente@lexcv.cv` (password `Teste123!`, both already seeded in the dev tenant). On the Cliente ficha, confirm the `Processos`/`Pareceres` `TabsTrigger`s render for both roles (both have `clientes:view`/`processos:view`/`pareceres:view` per `DatabaseSeeder.seedRbac`). On the Processo ficha, confirm the `Auditoria` `TabsTrigger` renders for ADVOGADO (has `processos:manage`) and is entirely absent from the DOM for ASSISTENTE (lacks `processos:manage`). Narrow the viewport to ~375px and confirm the Cliente tab bar scrolls (`overflow-x-auto`) and the Processo tab bar wraps to multiple rows (`flex-wrap`, the mobile-wrap bug already fixed this phase) for both roles, with all tabs remaining reachable.

**Expected:** Cliente ficha shows 7/7 tabs for both roles (both have the view permissions needed for all non-gated + the 2 gated tabs); Processo ficha shows 7/8 tabs for ASSISTENTE (Auditoria omitted, matching TECNICO's already-verified behavior) and 8/8 for ADVOGADO (Auditoria included, matching ADMIN's already-verified behavior); both fichas remain fully navigable at mobile width for both roles.

**Why human:** This is the one item the phase's own closing plan (105-06-PLAN.md) explicitly required ("verified against the real 4-role matrix ... at real mobile widths") that was not completed live for 2 of the 4 roles, per 105-06-SUMMARY.md's own transparent disclosure (browser-tooling instability, reproduced across a dev-server restart and a browser-process restart — assessed as session/infrastructure degradation, not a suspected code defect). This verification pass independently confirmed the gating code path (`web/src/lib/permissions.ts`) has zero role-name branching (purely `scope:action` array lookups) and cross-checked the actual seeded permission grants per role in `backend/.../seed/DatabaseSeeder.java:315-352` — both corroborate that the expected behavior above should hold, but a DOM/visual click-through was not performed for these 2 specific roles and cannot be substituted by static code reading alone.

### Gaps Summary

No FAILED truths, no MISSING/STUB artifacts, and no broken key links were found. All 5 CLP requirements (CLP-01 through CLP-05) are implemented, wired, and confirmed via direct source inspection, a live `pnpm build`, a live `pnpm lint` cross-checked against `git blame`, and independent re-verification of the code-review fix commits (CR-01/WR-01/WR-02/WR-03). Two real regressions were found and fixed by the executor during the phase's own mandatory human checkpoint (mobile tab-wrap bug; `isLoading`→`isFetched` RBAC race across 10 files) — both fixes were independently re-confirmed in the current source by this verification pass, not merely taken from the SUMMARY's word.

The single open item is a **verification-coverage gap, not a known or suspected defect**: 2 of the 4 required RBAC roles (ADVOGADO, ASSISTENTE) were not click-verified live against the tab-visibility matrix at mobile width, due to disclosed browser-tooling instability late in the 105-06 checkpoint session. Static code analysis (role-agnostic scope-based gating + the actual seeded permission grants for both roles) strongly corroborates the expected behavior, but does not fully substitute for the live check the phase's own plan required. This routes the phase to `human_needed` rather than `passed` — per the verification workflow's rule that any open human-verification item takes priority over an otherwise-clean score. A quick manual spot-check with the 3 test accounts already seeded in the dev tenant (documented in 105-06-SUMMARY.md) would close this.

Additionally flagged (non-blocking): `.planning/REQUIREMENTS.md`'s checkbox/traceability rows for CLP-01, CLP-03, and CLP-05 were not updated to "Complete" despite being verified implemented — a documentation-bookkeeping gap, not a functional one.

---

_Verified: 2026-07-16T21:00:00Z_
_Verifier: Claude (gsd-verifier)_
