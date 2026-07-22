# Phase 105 — UI Review

**Audited:** 2026-07-16
**Baseline:** `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-UI-SPEC.md` (approved design contract)
**Screenshots:** Not usable as evidence — dev server responded (`localhost:3000`, HTTP 200) but this app's auth is httpOnly-cookie JWT, and a stateless `npx playwright screenshot` invocation cannot authenticate (confirmed: a captured screenshot of `/clientes` renders the shell in an unauthenticated/loading state, KPIs stuck at 0, generic "U" avatar). A scripted-login attempt against the seeded `admin@lexcv.cv`/`Pa$$w0rd` credentials returned `401 Credenciais inválidas` (likely rotated/reseeded since 105-06's live checkpoint). This audit is therefore **code-only**, per the task's own framing (no live browser access) — every finding below is a direct source-code citation, not a visual screenshot comparison.

This phase already went through a code review (2 iterations, 4 findings fixed: CR-01 data loss, WR-01 missing `w-full`, WR-02 nested RBAC race, WR-03 CSV validation) and a live human checkpoint (2 regressions found and fixed: mobile tab-wrap, `isLoading`→`isFetched` RBAC race across 10 files) plus a `human_needed` verification pass (ADVOGADO/ASSISTENTE role matrix not live-clicked, a disclosed procedural gap, not a known defect). All of that is independently re-confirmed correct in the current source below. **This review focuses on the 6-pillar visual/UX surface those process-and-bug-focused passes did not examine** — cross-page copy/layout/typography/color consistency.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | H1↔Breadcrumb casing mismatch on `clientes/novo` ("Novo cliente" vs "Novo Cliente"); Documentos tab shows raw byte counts while the sibling Cliente ficha shows human-readable KB/MB for the same concept |
| 2. Visuals | 2/4 | Breadcrumb is nested under the `<h1>` on 4 of 6 pages but hoisted above/outside the entire header block on the other 2 (`processos/novo`, `processos/[id]/editar`) — the exact cross-page consistency this phase exists to deliver is not uniform |
| 3. Color | 3/4 | Zero accent-color leakage into Tabs/Avatar/Breadcrumb/NativeSelect (clean) — but NativeSelect's new neutral focus ring now sits next to untouched sibling date/number `<input>`s that still carry the old stray `ring-blue-500` + hardcoded `dark:bg-[#020617]`, creating a new intra-dialog seam that didn't exist pre-migration |
| 4. Typography | 2/4 | The declared 2-weight cap (400/600) was enforced surgically on exactly one `<h1>` per ficha, while the same two files ship `font-medium` (500) and `font-bold` (700) dozens of times each, plus off-scale `text-[10px]`/`text-[11px]` sizes — the "cap" does not hold once you look past the one locked line item |
| 5. Spacing | 3/4 | New insertions (Avatar `gap-2`, NativeSelect `w-full`) correctly match the declared scale; pre-existing arbitrary `min-w-[…px]` table widths were correctly preserved verbatim per the migration's own "markup-only" lock, not a new defect |
| 6. Experience Design | 3/4 | All loading/error/empty states, RBAC-gated tab omission, and both prior-review race-condition fixes independently re-confirmed correct in the current source; scored down one point because the phase's own required RBAC matrix (ADVOGADO/ASSISTENTE tab visibility) is still not live-verified, a real open item per `105-VERIFICATION.md`'s own `human_needed` status |

**Overall: 16/24**

---

## Top 3 Priority Fixes

1. **Breadcrumb is structurally hoisted above the header on `processos/novo`/`processos/[id]/editar` but nested under the `<h1>` everywhere else** — Users moving between the Clientes and Processos modules see the breadcrumb "jump" position relative to the page title (full-width bar above the header row vs. small text hugging the `<h1>`), undercutting the exact "never leave the two modules visibly inconsistent" goal this phase states as its own reason for existing. **Fix:** move the `<Breadcrumb>` block in `web/src/app/(dashboard)/processos/novo/page.tsx:206-218` and `web/src/app/(dashboard)/processos/[id]/editar/page.tsx:116-134` inside the header `<div>`, directly under the `<h1>`, matching the pattern already used in `clientes/novo/page.tsx:136-149`, `clientes/merge/page.tsx:78-91`, `clientes/[id]/page.tsx:379-396`, and `processos/[id]/page.tsx:718-...`.

2. **The declared 2-weight typography cap (400/600) is fiction beyond the one `<h1>` line item the spec locked** — `processos/[id]/page.tsx` alone uses `font-bold` (700) at ~20 sites (e.g. lines 808, 828, 851, 866, 906, 931, 950, 980, 1008-1009, 1030, 1064, 1081, 1125, 1139, 1177, 1260, 2315) and `font-medium` (500) at ~15 more, and `clientes/[id]/page.tsx` mirrors this (lines 624, 629, 1080-1184, 1199 for `font-bold`). The UI-SPEC treats the h1 fix as "preserving the 2-weight cap," but the surrounding page content it ships alongside contradicts that claim on every scroll. **Fix:** either update the UI-SPEC to honestly declare the weights actually in production use (400/500/600/700), or use this phase's own touched-file window to reconcile the Badge/TableHead `font-bold` instances down to `font-semibold` the same way the `<h1>` was reconciled — don't let a locked design-system constraint apply to one line while the rest of the same file ignores it.

3. **NativeSelect's neutral focus ring now clashes with its sibling date/number `<input>` fields inside the same "Adicionar Prazo/Decisão/Facto" dialogs** — Before this migration, the `<select>` and its sibling `<input type="date"/type="number">` shared the same `focus-visible:ring-blue-500` styling (visually consistent). After the migration, `NativeSelect` correctly switched to the neutral `ring-ring/50` (per UI-SPEC Color section, an intentional loss on the select itself) — but the untouched `<input>` siblings at `processos/[id]/page.tsx:1201, 1795, 1979, 1994` still carry `ring-blue-500` + hardcoded `dark:bg-[#020617]`, so the same dialog now visibly mixes a neutral-ring control next to blue-ring controls where it previously didn't. **Fix:** since CLP-03 already established the correct target ring, extend it to these 4 sibling `<input>` call sites in the same dialogs (drop `ring-blue-500`/`bg-[#020617]`, adopt the shared `Input` component or its equivalent neutral-ring classes) so the forms this phase visibly touched read as one consistent control set, not two.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

- **H1/Breadcrumb casing mismatch, `clientes/novo/page.tsx`:** the `<h1>` reads "Novo cliente" (sentence case, line 136) while the new `<BreadcrumbPage>` immediately below it reads "Novo Cliente" (title case, line 146). The UI-SPEC's own Copywriting Contract calls for the breadcrumb label to be "title-case matching each page's existing `<h1>` text" — but the actual `<h1>` isn't title case, so the literal instruction and the visible result don't match each other. Two adjacent text elements referring to the identical page now disagree on capitalization. `processos/novo/page.tsx` does not have this problem (h1 "Novo Processo" at line 221 already matches its breadcrumb "Novo Processo" at line 215) — the inconsistency is Clientes-module-specific.
- **Byte-size formatting diverges between the two fichas' Documentos surfaces, both delivered in this same phase:** `processos/[id]/documentos-columns.tsx:171` renders `${tamanho.toLocaleString("pt-CV")} bytes` (e.g. "2458624 bytes") for the new processo-scoped `DataTable`, while `clientes/[id]/page.tsx:1219-1223`'s `formatDocumentoSize()` helper (used by the sibling "Documentos Entregues" tab in the very same phase) renders human-readable units ("2.4 MB"). This was inherited verbatim from Phase 104's `documentos/columns.tsx:212` per the pattern doc's explicit "model directly on Phase 104" instruction, so it isn't a new defect this phase invented — but shipping the two fichas side-by-side with two different size-formatting conventions for the same field is a real, user-visible copy inconsistency a reviewer should catch before calling the combined migration done.
- What's correct: all 7 Cliente `TabsTrigger` labels and 8 Processo `TabsTrigger` labels match the Copywriting Contract verbatim (`Dados`, `Contactos e Notas`, `Timeline`, `Auditoria`, etc. — confirmed via direct read, `clientes/[id]/page.tsx:454-460`, `processos/[id]/page.tsx:1271-1278`). All 6 breadcrumb root labels (`Clientes`/`Processos`) and leaf labels (`Merge`, `Editar`, dynamic `numero`/`nome` fallback chains) match the contract exactly. No generic "Submit"/"OK"/"Click Here" placeholder copy found anywhere in the 9 in-scope files. Empty states (`"Sem partes."`, `"Nenhuma testemunha registada."`, `"Nenhum documento registado."`) are descriptive, not generic.

### Pillar 2: Visuals (2/4)

- **Breadcrumb placement is not uniform across the 6 pages CLP-05 targets — a direct, evidenced contradiction of the phase's own stated purpose.** Confirmed via direct read of all 6 pages:
  - Nested under `<h1>`, inside the same header `<div>` (4 pages): `clientes/[id]/page.tsx:379-396` (h1 at 380, Breadcrumb 381-395), `processos/[id]/page.tsx` (h1 at 719, Breadcrumb 720+), `clientes/novo/page.tsx:136-149`, `clientes/merge/page.tsx:78-91`.
  - Hoisted entirely above/outside the header `<div>` (2 pages): `processos/novo/page.tsx:206-218` (Breadcrumb) then a *separate* `<div className="flex items-start justify-between gap-4">` starting at line 219 containing the `<h1>`; `processos/[id]/editar/page.tsx:116-134` (Breadcrumb) then a separate header `<div>` starting at line 135.

  The practical visual effect: on 4 of 6 pages the breadcrumb is a small, muted, content-width element that hugs the left edge of the `<h1>` column. On the other 2, it becomes a full-page-width row sitting above the entire header (title + subtitle + "Voltar"/"Cancelar" button), because it's now a sibling of that whole flex row rather than a child of the title's own column. This is exactly the "visible inconsistency window" the phase's own Phase Boundary text says the combined delivery exists to prevent — except here it's introduced by the delivery itself, in the specific feature (Breadcrumb) meant to standardize the header.
- Icon-only affordances are correctly labeled: the new `DocumentoAcoesCell`'s Download icon button carries `aria-label="Download"` + a `Tooltip` (`documentos-columns.tsx:97, 104`), and all `window.confirm`-gated delete icon buttons in the ficha (Testemunha, Decisão, Facto) carry explicit `aria-label`s (`processos/[id]/page.tsx:1918, 2071, 2255`). No unlabeled icon-only button was found in any of the 9 in-scope files.
- `Avatar` correctly stays a small, secondary element (no visual competition with the primary name text) in both `ResponsaveisCard` (`clientes/[id]/page.tsx:1673-1675`) and the Testemunhas "Nome" cell (`processos/[id]/page.tsx:2233-2236`) — matches the "does not change row height" and "not accent-tinted" locks in the UI-SPEC.

### Pillar 3: Color (3/4)

- **A new visual seam inside the "Adicionar Prazo"/"Adicionar Decisão"/"Adicionar Facto" dialogs.** Before this migration, the `<select>` and its sibling `<input type="date">`/`<input type="number">` inside these same dialogs shared one styling convention (`rounded-none border-slate-300 ... focus-visible:ring-blue-500`). After the migration, the `<select>` → `NativeSelect` swap correctly picked up the primitive's neutral `focus-visible:ring-ring/50` (an intentional, spec-authorized loss of the outlier blue ring on the select itself) — but the untouched sibling `<input>`s at `processos/[id]/page.tsx:1201` ("Data Limite"), `:1795` ("Data" for Decisão), `:1979` ("Data" for Facto), and `:1994` ("Ordem" for Facto) still carry `focus-visible:ring-blue-500` and the hardcoded `dark:bg-[#020617]` hex. The net result: these 3 dialogs used to be internally consistent (all controls sharing one ring color) and are now internally inconsistent (one neutral-ring control next to 1-2 blue-ring controls in the same form). This is out of CLP-03's literal scope (Input isn't a `<select>`) but it's a concrete, newly-created regression in the exact surfaces this phase edited.
- Confirmed zero accent-color (`text-primary`/`bg-primary`/`border-primary`/`ring-primary`) usage anywhere in `clientes/[id]/page.tsx` or `processos/[id]/page.tsx` (0 matches, both files) — the "closed accent list" from the UI-SPEC Color section (sidebar nav, avatar gradient badge, search focus ring, Dashboard KPI) is correctly not extended to any of this phase's new components. `TabsTrigger`'s active state (`data-active:bg-background`, `tabs.tsx:68`) is neutral, not accent-tinted, confirmed by direct read of the primitive.
- Hardcoded hex (`dark:bg-[#020617]`) also persists (pre-existing, not newly introduced) on the `ClienteProcessosTab`/`ClienteParecerTab` `CardContent` wrappers (`clientes/[id]/page.tsx:1067, 1168`) — these predate Phase 105 (their own migration was Phase 102/104) but remain in a file this phase repeatedly edits, so the hardcoded-color debt is still visibly present in the audited surface.
- `--radius: 0rem` correctly resolves `NativeSelect`'s `rounded-md` to sharp corners (verified via `globals.css:44` `--radius-md: calc(var(--radius) * 0.8)` = 0) — the UI-SPEC's claim that non-`Avatar` primitives resolve to 0 automatically holds.

### Pillar 4: Typography (2/4)

- **The declared 2-weight cap (400 regular / 600 semibold) is enforced on exactly the one `<h1>` line item the UI-SPEC locked, and nowhere else in the same files.** Direct grep of font-weight utility classes in the two ficha files:
  - `processos/[id]/page.tsx`: `font-semibold` (1, the fixed h1), `font-medium` (~15 occurrences), `font-bold` (~20 occurrences, e.g. lines 808, 828, 851, 866, 906, 931, 950, 980, 1008, 1009, 1030, 1064, 1081, 1125, 1139, 1177, 1260, 2315).
  - `clientes/[id]/page.tsx`: `font-semibold` (3), `font-medium` (~14), `font-normal` (3), `font-bold` (~14, e.g. lines 624, 629, 1080-1084, 1106, 1112, 1181-1184, 1190, 1199).
  This means the two files this phase's own UI-SPEC singles out as needing a weight reconciliation "to preserve the project's 2-weight cap" in fact ship **four** distinct weights (400/500/600/700) once you look past the single `<h1>` the spec fixated on. `TabsTrigger`'s own `font-medium` (500, `tabs.tsx:66`) is correctly documented in the UI-SPEC as an accepted primitive default — but the dozens of `font-bold` (700) instances on Badges and `TableHead`s in these same files are not acknowledged anywhere in the UI-SPEC's Typography section at all.
- **Off-scale arbitrary font sizes** appear in the exact tab content these files render: `text-[10px]` (`clientes/[id]/page.tsx:624, 629, 1080-1184` — Badge/TableHead labels in `ClienteProcessosTab`/`ClienteParecerTab`) and `text-[11px]` (`processos/[id]/page.tsx:980, 1125, 1139, 2315` — Timeline/Testemunhas Badge labels), neither of which appears in the declared 4-size scale (`text-xs`/`text-sm`/`text-2xl`/`text-3xl`). These predate Phase 105 in most cases, but they remain the actual shipped typography of the pages this phase's own audit target is.
- What's correct: the one locked item — `processos/[id]/page.tsx:719`'s `<h1>` reads `font-semibold`, matching `clientes/[id]/page.tsx:380` exactly, confirmed via direct read (both now converge, the documented fix is real and correctly applied).

### Pillar 5: Spacing (3/4)

- New insertions correctly match the declared scale: `Avatar` + name wrapped in `flex items-center gap-2` (8px, the declared `sm` token) at both `ResponsaveisCard` (`clientes/[id]/page.tsx:1672`) and the Testemunhas "Nome" cell (`processos/[id]/page.tsx:2232`) — exact match to the UI-SPEC's Spacing Scale table.
- `NativeSelect`'s `w-full` fix (from the code-review WR-01 finding) is present and correct at every call site checked (`clientes/[id]/page.tsx:576, 709, 1712`; `processos/[id]/page.tsx:1729` (Fases inline status), etc.) — re-confirmed independently, no regression.
- Table width values (`min-w-[400px]` for Partes, `min-w-[480px]` for Fases/Testemunhas/Decisões/Factos) are arbitrary bracket values, but these are pre-existing raw-`<table>` widths the migration was explicitly instructed to preserve verbatim ("markup-only swap," `105-PATTERNS.md` Pattern 4) — not a new defect, correctly scoped as inherited.
- The Breadcrumb placement inconsistency (Pillar 2) also has a spacing dimension — the two structurally-different placements produce different vertical rhythm between page elements (h1→breadcrumb→subtitle vs. breadcrumb→h1+button-row→(no subtitle on the editar page)) — already counted under Visuals to avoid double-scoring, noted here for completeness.

### Pillar 6: Experience Design (3/4)

- Independently re-verified (not taken on the prior reports' word) that both prior fixes hold in current source: the page-level RBAC guard uses `permissions.isFetched && !canViewClientes` (`clientes/[id]/page.tsx:130`), and all 3 nested tab-content RBAC gates use the same `!permissions.isFetched` 3-way gate (`clientes/[id]/page.tsx:865, 875, 885`) — no regression from the WR-02 fix. The mobile flex-wrap fix (`processos/[id]/page.tsx:1270`, `className="h-auto w-full flex-wrap"` directly on `TabsList`) is present and correct.
- Loading/error/empty states are consistently covered across every migrated surface: `ClienteProcessosTab`/`ClienteParecerTab` (`isLoading`/`isError`/empty, `clientes/[id]/page.tsx:1068-1075, 1169-1176`), the new Documentos `DataTable` (guarded by the pre-existing `isLoading`/`isError`/`length === 0` branches, unchanged), and Partes/Fases/Testemunhas (`"Sem partes."`, `"Sem fases."`, equivalent Testemunhas empty text).
- RBAC-gated `TabsTrigger`s are correctly omitted (not `disabled`) — confirmed `{canViewProcessos ? <TabsTrigger .../> : null}` pattern at `clientes/[id]/page.tsx:456-457` and `{canManageProcessos ? <TabsTrigger value="auditoria">.../> : null}` at `processos/[id]/page.tsx:1278`.
- **Scored down one point because a required piece of this exact pillar's coverage — the RBAC-conditional tab set verified against the full 4-role matrix (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) at mobile width — is still an open, disclosed gap.** `105-VERIFICATION.md` itself routes the phase to `human_needed` specifically because ADVOGADO/ASSISTENTE were never live-clicked (browser-tooling instability, not a suspected defect) — this is Experience Design's own subject matter (RBAC-driven UI visibility), so a UI-focused pillar audit should not silently treat it as already resolved just because the code path is static-analysis-confirmed role-agnostic. This is not double-counting the verification report's finding as new; it's this pillar correctly reflecting that its own closing gate is not yet fully closed.
- Minor, pre-existing (not introduced by Phase 105) observation: the Fases tab's "Guardar" button (`processos/[id]/page.tsx:1745-1753`) has no dirty-check — it's enabled whenever `canEditProcessos` is true and no mutation is in flight, regardless of whether `faseDraftStatus[f.id]` actually differs from the persisted `f.status`, so a user can trigger a no-op save. Not part of this phase's diff (state logic untouched, only `<td>`→`TableCell`/`<select>`→`NativeSelect` changed here) — noted for completeness, not scored.

---

## Registry Safety

`web/components.json` exists (shadcn initialized), but `105-UI-SPEC.md`'s own Registry Safety table lists no third-party registries for this phase (`shadcn official` and `shared internal` only; the milestone explicitly excludes third-party registry/package installation). Per the registry-audit gate, this means the audit does not apply.

Registry audit: 0 third-party blocks checked, no flags.

---

## Files Audited

- `web/src/app/(dashboard)/clientes/[id]/page.tsx`
- `web/src/app/(dashboard)/processos/[id]/page.tsx`
- `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx`
- `web/src/app/(dashboard)/documentos/columns.tsx` (Phase 104 reference, for the byte-format comparison)
- `web/src/app/(dashboard)/clientes/page.tsx`
- `web/src/app/(dashboard)/clientes/novo/page.tsx`
- `web/src/app/(dashboard)/clientes/merge/page.tsx`
- `web/src/app/(dashboard)/processos/page.tsx`
- `web/src/app/(dashboard)/processos/novo/page.tsx`
- `web/src/app/(dashboard)/processos/[id]/editar/page.tsx`
- `web/src/components/ui/tabs.tsx`
- `web/src/components/ui/native-select.tsx`
- `web/src/app/globals.css` (for `--radius` token verification)
- `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-UI-SPEC.md`, `105-CONTEXT.md`, `105-PATTERNS.md`
- `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-01..06-PLAN.md` / `105-01..06-SUMMARY.md`
- `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-REVIEW.md`, `105-REVIEW.iter2.md`, `105-VERIFICATION.md` (cross-referenced to avoid duplicating already-found issues)

One screenshot captured for evidence of the auth limitation: `.planning/ui-reviews/105-20260716-191116/clientes-desktop.png` (unauthenticated shell state — not used as visual evidence for any pillar finding above).
