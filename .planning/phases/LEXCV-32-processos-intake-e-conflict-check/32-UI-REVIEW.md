# Phase 32 — UI Review

**Audited:** 2026-06-15
**Baseline:** UI-SPEC.md (status: approved, 2026-06-13)
**Screenshots:** Not captured (no dev server detected at localhost:3000 or localhost:5173)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | Six spec copy strings missing or diverged; "Decisor" field entirely absent from wizard |
| 2. Visuals | 2/4 | Spec icons (ShieldCheck, AlertTriangle, ExternalLink) not used; detail page heading wrong weight; no TRIAGEM empty-state copy |
| 3. Color | 3/4 | Neutral color token contamination in [id]/page.tsx forms breaks design-system cohesion |
| 4. Typography | 2/4 | `font-semibold` appears on detail page heading (not in spec weights); arbitrary px sizes `text-[10px]`, `text-[13px]` outside declared scale |
| 5. Spacing | 3/4 | Step indicator uses `py-4` not `py-6` as specified; one arbitrary pixel `[14px]` for icon size (intent clear but off-scale) |
| 6. Experience Design | 3/4 | Decisao form lacks Decisor read-only field; match-row "Ver registo" for `parte` entities falls back to `/processos` (broken link); no triagem-specific empty state |

**Overall: 15/24**

---

## Top 3 Priority Fixes

1. **Missing "Decisor" read-only field in Step 2 decisao form** — Users cannot confirm who is recording the decision; the spec (`UI-SPEC.md` line 130) requires a read-only pre-filled decisor field; it was planned in 32-03-PLAN.md Task 1 but is completely absent from the rendered form. Fix: add a read-only `<Input>` (or `<dd>`) pre-filled from `usePermissions()` current user display name, after the Referência field, matching the spec layout.

2. **"Ver registo" link falls back to `/processos` for `parte` entities** — When a conflict match has `entidadeTipo === "parte"` the link navigates to the top-level processos listing (`novo/page.tsx` line 484), not to the actual record. This breaks the conflict-check audit trail. Fix: route `parte` matches to `/processos/{match.entidadeId}` or the relevant detail URL; if `processoId` is not available on the match object, add it to `ConflictMatch` from the backend or display "Ver registo" as disabled for partes.

3. **Empty-state copy for TRIAGEM-filtered listing is absent** — `UI-SPEC.md` line 178 specifies `"Nenhum processo em triagem."` as the empty state when the estado filter is set to TRIAGEM. The listing page (`page.tsx` line 290) shows generic `"Nenhum processo encontrado."` unconditionally. Fix: compute an `emptyMessage` variable that checks `filters.estado === "TRIAGEM"` and renders the spec copy.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)

**WARNING — Several specified copy strings diverge from or are absent in the implementation.**

**Present and correct (passing):**
- "Continuar para Conflict Check" — novo/page.tsx:408
- "Executar Conflict Check" — novo/page.tsx:437
- "Registar Decisão" — novo/page.tsx:552
- "Formalizar Processo" — novo/page.tsx:691
- "EM TRIAGEM" — page.tsx:317
- "Verificação de Conflitos" card header — novo/page.tsx:423
- "Dados de Intake" card header — novo/page.tsx:261
- "Revisão e Abertura" card header — novo/page.tsx:604
- Impeditivo callout copy — exact match with spec line 138
- Both formalizar block reason strings — exact match with spec lines 181-182
- Permission-denied messages — match spec lines 183-184

**Missing or diverged:**

1. **"Sem conflitos detectados" heading is present but spec empty-state heading says "Sem conflitos detectados" (line 176)** — Implementation uses this text as a span label beside the badge (novo/page.tsx:457), which matches the intent, but the copy at the `<h>` level is absent. The body copy at line 460 matches spec line 177 exactly. MINOR.

2. **"Nenhum processo em triagem."** (spec line 178) — Absent. The listing empty state is a single generic `"Nenhum processo encontrado."` (page.tsx:290) regardless of active TRIAGEM filter. This is user-visible divergence: a user filtered to triagem gets a misleading message.

3. **"Decisor" field copy entirely absent from wizard Step 2** — Spec line 130 requires a "Decisor" read-only field in the decision form. No Decisor label, value, or read-only input appears anywhere in the wizard decision form. The Step 3 summary `dl` also omits a decisor row. The detail page shows `decisao.data.decisorId` (string UUID, not a name) without labeling it as "Decisor" — it is labeled "Decisor" in the `dl` (line 308) but shows the UUID value, not the user's name.

4. **"Data" read-only field in Step 2 decisao form absent** — Spec line 131 requires a pre-filled today date read-only field. The implementation stores `dataDecisao` in state after the save (line 147) but does not render it to the user during the form interaction, only in Step 3 summary.

5. **"Continuar para Conflict Check" loading state copy incorrect** — During Step 1 submit, the button shows "A guardar..." (line 408) rather than staying on "Continuar para Conflict Check" with a loading indicator. Minor, but inconsistent with the spec's pattern for loading states ("A executar...", "A verificar conflitos...").

6. **Cancel button in Step 1 reads "Cancelar" (line 411)** — Spec requires "Voltar" as the cancel/back action throughout (spec line 170). "Cancelar" is a divergence for the cancel action in Step 1 CTA bar.

---

### Pillar 2: Visuals (2/4)

**WARNING — Icon spec not followed; detail heading weight deviates; two structural UX gaps.**

**Specified icons not used (UI-SPEC.md lines 238-239):**
- `ShieldCheck` — specified for sem_conflito state; not imported or used in novo/page.tsx (only `Check` and `ChevronRight` imported)
- `AlertTriangle` — specified for impeditivo warning callout; not used (callout renders plain text with no icon)
- `ExternalLink` — specified for "Ver registo" link to matched entity; not used (plain text link rendered)
- `CheckCircle2` and `Circle` — specified for completed/upcoming step indicators; instead `Check` icon from lucide is used for completed steps (minor, Check is acceptable) and steps show plain numbers; `Circle` is not used

**Visual hierarchy issues:**
- `[id]/page.tsx` line 198: page heading is `text-2xl font-semibold`. Spec does not list `font-semibold` as a weight — it specifies font-bold at heading level (spec Typography table). This heading therefore deviates from both the spec weight table and existing conventions (the wizard page uses `text-2xl font-bold` at line 188).
- The "Conflict Check" card in the detail page (`[id]/page.tsx` line 283) uses `<div className="flex items-center justify-between">` inside `<CardHeader>` wrapping `<CardTitle>` — this is a non-standard pattern that bypasses CardHeader semantics. The badge in the header is right-aligned as spec requires, but the wrapper div is not a spec component and may break CardHeader spacing.

**Step indicator has no visual connector line (border or divider):**
- Spec says "Connector line between steps: 1px solid slate-200/slate-700". The implementation uses `<ChevronRight>` icons between steps. ChevronRight is acceptable as a connector indicator, but the spec calls for a line; the icon is a deviation. Minor visual difference.

**Empty-state icon for no-check-yet on detail page:**
- The empty-state text in the detail conflict section has no icon; spec doesn't explicitly mandate one, so this is informational, not a fail.

---

### Pillar 3: Color (3/4)

**WARNING — Two-tier design token contamination in [id]/page.tsx.**

**Accent blue-600 usage is correctly constrained:**
- `bg-blue-600` on primary CTAs only: novo/page.tsx, [id]/page.tsx (Formalizar button)
- Active step indicator: `bg-blue-600 text-white` — correct
- Focus rings: `focus-visible:ring-blue-500` — correct
- Pagination active: `bg-blue-600` on page button — correct
- `text-blue-600` on `hover:text-blue-600` links — acceptable as hover state, not over-used

**Conflict badge colors correctly mapped:**
- `conflictNivelToVariant` returns green/amber/blue/red matching spec lines 79-82 exactly

**60/30/10 distribution:**
- Dominant surface: slate-50/white/bg-[#020617] — correct
- Secondary: Card components with white/[#020617] — correct
- Accent blue-600: constrained to buttons, step indicator, focus rings, one pagination dot — within 10%

**Token contamination issue — [id]/page.tsx:**
- `selectClassName` (line 53) and `textareaClassName` (line 56) use `rounded-md`, `border-neutral-200`, `bg-neutral-950`, `focus-visible:ring-neutral-950`, `dark:focus-visible:ring-neutral-300` — these are old shadcn defaults, not the Anti-Safe Harbor tokens (`rounded-none`, `border-slate-300`, `bg-[#020617]`, `ring-blue-500`). This means any select or textarea in the fases/partes/movimentacoes forms uses mismatched rounded corners, wrong focus ring color, and wrong dark background. 32 occurrences of `neutral-` token in this file vs. 0 in the newly written wizard and listing pages.
- `rounded-md` on movimentacoes rows (line 626): `rounded-md` is explicitly prohibited by Anti-Safe Harbor.
- The Conflict Check card itself correctly uses `rounded-none` for badges and buttons, so the new Phase 32 additions are clean — the contamination is in pre-existing form sections of [id]/page.tsx that were not migrated as part of this phase.

**Hardcoded values:** `#020617` used as a dark background value (same token used consistently across all files). This is a repeated CSS custom-property stand-in for `bg-[#020617]`, which is the defined dominant dark background in UI-SPEC color table. Acceptable — it matches the declared `#020617` color role exactly.

---

### Pillar 4: Typography (2/4)

**WARNING — Undeclared font weights and out-of-scale arbitrary sizes.**

**Declared sizes in UI-SPEC:** 14px (text-sm), 11px (text-[11px]), 24px (heading text-2xl), 30px (display text-3xl).

**Actual sizes found in phase-touched files:**
- `text-3xl` (30px) — display: page.tsx:103 — matches spec display role
- `text-2xl` (24px) — heading: novo/page.tsx:188, [id]/page.tsx:198 — matches spec heading role
- `text-lg` (18px) — page.tsx:152 — NOT in spec scale; used for "8 processos" strong text in the dark promo card
- `text-sm` (14px) — body throughout — matches spec body role
- `text-xs` (12px) — page.tsx and novo/page.tsx match row entity type label — NOT in spec scale
- `text-[10px]` — page.tsx:295-300 (table headers) — arbitrary value, not in declared scale
- `text-[11px]` — numerous locations — matches spec Label role (declared)
- `text-[13px]` — page.tsx:151 (promo card description) — arbitrary, not in scale

Out-of-scale sizes: `text-lg`, `text-xs`, `text-[10px]`, `text-[13px]` — four arbitrary or unlisted sizes beyond the four declared.

**Declared weights in UI-SPEC:** 400 (normal, body) and 700 (bold, headings/labels). The spec does not list `font-semibold` or `font-medium`.

**Actual weights found:**
- `font-bold` — throughout, spec-compliant
- `font-medium` — used for `dd` values in `dl` grids throughout. This is beyond the two declared weights, though it is a common pairing pattern and doesn't break legibility.
- `font-semibold` — [id]/page.tsx:198 heading, editar/page.tsx:102. Spec weight table has no semibold entry. The new Phase 32 detail heading at line 198 uses `font-semibold` while the equivalent novo/page.tsx heading at line 188 uses `font-bold` — an internal inconsistency introduced by this phase.

Net: 3 weight classes in use (bold, medium, semibold) where spec declares 2 (bold and normal). The `font-semibold` on the detail page heading is the most visible deviation.

---

### Pillar 5: Spacing (3/4)

**WARNING — Step indicator padding and one arbitrary pixel value diverge from spec.**

**UI-SPEC declared step indicator spacing:** `px-6 py-6` from page edges, `gap-4` between steps.

**Actual step indicator (novo/page.tsx:199):** `px-6 py-4 gap-4`. The `py-6` (24px vertical) is implemented as `py-4` (16px). This reduces the vertical breathing room on the step indicator block by 8px — visually the steps will appear closer to the content above than the spec intends.

**Spacing scale compliance (non-arbitrary):**
- `space-y-6`, `space-y-4`, `space-y-3`, `space-y-2`, `gap-4`, `gap-2`, `gap-3` — all standard Tailwind scale values within 4px multiples. Clean.
- `p-4`, `p-5`, `p-6` on cards — within scale.
- `px-3 py-1`, `px-3 py-2` on inputs — standard scale.

**Arbitrary values in phase-touched files:**
- `h-[14px] w-[14px]` (novo/page.tsx:211, 231) for Check icon in step circles — intent is to fit a 14px icon in a 32px circle, which is reasonable, but this is off the 4px spacing grid. The spec says "14px" icon (`Check` 14px), so this matches spec intent but uses an arbitrary class.
- `w-[320px]` on search input (page.tsx:165) — pre-existing arbitrary width, not introduced by Phase 32.
- `text-[11px]`, `text-[10px]`, `text-[13px]` — already counted in typography pillar.

Overall spacing is disciplined. The `py-6` → `py-4` regression and the `h-[14px]` icon size are the notable issues.

---

### Pillar 6: Experience Design (3/4)

**WARNING — Three interaction gaps: missing decisor field, broken parte link, missing triagem-specific empty state.**

**Loading states — PASS:**
- All async buttons show in-button text changes: "A verificar conflitos...", "A guardar...", "A formalizar..." with `disabled` state during pending. Covers all 4 mutation hooks.
- `clientes.isPending` disables the client select (line 272) with placeholder copy.
- Page-level loading in [id]/page.tsx (line 219) with text fallback.

**Error states — PASS:**
- Step-level error state variables (`step1Error`, `step2Error`, `step3Error`, `formalizarError`) with `<p className="text-sm text-red-600">` pattern.
- Backend 4xx from formalizar shown inline even when button is disabled (lines 705-707).
- Client load error shown inline (line 282-285 novo/page.tsx).

**Empty states — PARTIAL PASS:**
- No-matches empty state in conflict results: green badge + spec copy (line 460) — PASS.
- Conflict section no-decisao state in detail: "O conflict check ainda não foi executado para este processo." (line 331) — PASS.
- TRIAGEM-filtered listing empty state: missing spec copy (see copywriting). — FAIL.
- Partes/fases/movimentacoes "Sem X." empty states — PASS (pre-existing).

**Permission gate coverage — PASS:**
- `canCreateProcessos` gates Step 1 form mount, "Executar Conflict Check" button, and Step 2 form display.
- `canManageProcessos` gates decisao form visibility and Formalizar button in both wizard and detail.
- `canEditProcessos` gates parte/fase/movimentacao add forms in detail.
- `AccessDeniedState` rendered when `!canViewProcessos` on listing and detail.

**Decisor read-only field — FAIL (WARNING):**
- Spec requires "Decisor (read-only, pre-filled from current user)" in the Step 2 decision form (line 130).
- `usePermissions()` is already called in the component and could supply this.
- The field is absent entirely — users recording a decision cannot confirm their identity in context.
- Step 3 summary also omits decisor from the `dl` grid, so the review step shows nivel/date/referência/justificativa but no decisor name.
- The detail page shows `decisao.data.decisorId` (UUID string) as "Decisor" — raw ID, not a human-readable name.

**"Ver registo" for `parte` entities — FAIL (BLOCKER-adjacent WARNING):**
- novo/page.tsx line 484: `href={match.entidadeTipo === "cliente" ? /clientes/${match.entidadeId} : /processos}`. When a match is a `parte`, the link goes to the top-level `/processos` listing, not the associated processo detail. This produces a confusing navigation: clicking "Ver registo" on a conflict match that is a parte brings the user to a completely unrelated list page.
- Fix: route to `/processos/{processoId}` using the `processoId` already in scope, or use `match.entidadeId` if it is the processo ID for parte entities.

**Disabled state completeness:**
- Formalizar disabled state with `opacity-50 cursor-not-allowed` + reason text — PASS, matches spec exactly.
- "Continuar para Abertura" disabled with same classes when nivel is impeditivo — PASS.
- "Registar Decisão" disabled variant when `!canManageProcessos` — PASS, renders disabled button + permission message per spec line 184.

**No destructive confirmation needed in this phase — confirmed correct (spec line 185).**

**Step navigation back-tracking:** "Voltar" in Step 3 navigates back to Step 2 (line 697). No spec back-tracking from Step 2 to Step 1 — omission may be intentional given that Step 1 creates the processo; no findings raised.

---

## Additional Findings (not captured in top 3)

4. **`[id]/page.tsx` `selectClassName` and `textareaClassName` use old shadcn defaults** — 32 `neutral-*` token occurrences, `rounded-md` borders, `ring-neutral-950` focus color. These forms are pre-existing in the file but were not migrated during this phase, creating visual inconsistency on the same page that now hosts the new Anti-Safe Harbor Conflict Check section.

5. **Detail page heading `font-semibold` vs. wizard `font-bold`** — `[id]/page.tsx:198` `text-2xl font-semibold` vs. `novo/page.tsx:188` `text-2xl font-bold`. Same semantic role (page heading), different weight. Inconsistency within phase-touched files.

6. **Step 1 CTA says "Cancelar" not "Voltar"** — `novo/page.tsx:411`. UI-SPEC line 170 reserves "Voltar" as the cancel/back label throughout the wizard. "Cancelar" breaks that pattern.

7. **No `Título` field in Step 1** — `processoFormSchema` includes a `titulo` field and the `Processo` type exposes it, but the wizard Step 1 form has no Título input (no Row for it). The spec grid layout at UI-SPEC lines 106-111 does not list Título as a required row either; however the schema includes it and [id]/page.tsx line 247 falls back to `processo.data.titulo`. Minor: if Título is schema-required, intake will silently submit with undefined titulo. Needs investigation at schema level.

8. **Match row `key={idx}` instead of `key={match.entidadeId}`** — novo/page.tsx:465. Using array index as React key on a conflict result list is incorrect; if results are re-ordered or rerun, React will not correctly identify changed rows.

---

## Registry Safety

Registry audit: no third-party shadcn registry blocks detected (no `components.json` found in web/). Registry audit skipped per protocol.

---

## Files Audited

- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/phases/LEXCV-32-processos-intake-e-conflict-check/32-UI-SPEC.md`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/phases/LEXCV-32-processos-intake-e-conflict-check/32-03-SUMMARY.md`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/phases/LEXCV-32-processos-intake-e-conflict-check/32-03-PLAN.md`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/phases/LEXCV-32-processos-intake-e-conflict-check/32-02-SUMMARY.md`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/phases/LEXCV-32-processos-intake-e-conflict-check/32-01-SUMMARY.md`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/.planning/phases/LEXCV-32-processos-intake-e-conflict-check/32-CONTEXT.md`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/web/src/app/(dashboard)/processos/novo/page.tsx`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/web/src/app/(dashboard)/processos/page.tsx`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/web/src/app/(dashboard)/processos/[id]/page.tsx`
- `C:/Users/francisco.horta/Documents/projects/personal/lexcv/web/src/lib/conflict-check.ts`
