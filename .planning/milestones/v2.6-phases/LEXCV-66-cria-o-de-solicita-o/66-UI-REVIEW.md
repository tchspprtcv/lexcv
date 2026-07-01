# Phase 66 — UI Review

**Audited:** 2026-07-01
**Baseline:** .planning/phases/LEXCV-66-cria-o-de-solicita-o/66-UI-SPEC.md (approved)
**Screenshots:** not captured (no dev server running at localhost:3000/5173/8080) — code-only audit

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All copy strings (labels, placeholders, errors, CTA states) match the spec verbatim, including present-progressive and present-perfect conventions. |
| 2. Visuals | 3/4 | Focal point (submit button) correctly weighted, but `processoId` select has no loading/disabled state while `clienteId` does — visual/interaction asymmetry between two structurally identical fields. |
| 3. Color | 3/4 | Accent (`blue-600`) correctly reserved for primary CTA only; but focus rings use `blue-500` (`focus-visible:ring-blue-500`) not `blue-600`/`blue-700` as the spec's Color table declares for "input focus-visible ring." |
| 4. Typography | 3/4 | Sizes/weights match spec's declared set (text-sm/text-xs/text-2xl, font-normal/font-bold), but `CardTitle` renders at browser-default `h3` size with `font-semibold` (600) — not the spec-mandated `text-lg`/`font-bold` (700) override. |
| 5. Spacing | 4/4 | `space-y-6` (root), `space-y-4` (form), `space-y-2` (field groups) match the declared scale exactly, byte-for-byte with `processos/novo/page.tsx`. |
| 6. Experience Design | 2/4 | No error/loading state on `processoId` select (silent empty dropdown on fetch failure); no submit-disabled guard while `clientes`/`processos` are still loading; no min-date validation on `prazo`; access-guard duplicated in two components creating a race-condition-prone double-render pattern. |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **`processoId` select has no loading/error state, unlike the structurally identical `clienteId` select** (`web/src/app/(dashboard)/pareceres/nova/page.tsx:151-164`) — user impact: if the processos fetch fails or is slow, the user sees a plain empty "Nenhum processo associado" dropdown with zero feedback, inconsistent with the cliente field two rows above (`clientes.isPending`/`clientes.isError` handling at lines 130, 140-144). Fix: mirror the cliente select's pattern — add `disabled={processos.isPending || processos.isError}` and an inline `text-sm text-red-600` error line when `processos.isError`.

2. **Focus ring color does not match the spec's declared accent value** (`web/src/app/(dashboard)/pareceres/nova/page.tsx:26,29,184` — `focus-visible:ring-blue-500`) — UI-SPEC.md Color table declares accent as `blue-600`/`blue-700` and states it's "Reserved for: primary submit button... and input focus-visible ring only" (line 70-73), implying the ring should use the same accent family, not a lighter `blue-500` shade. This is a real but pre-existing deviation (also present in `processos/novo/page.tsx`'s `selectClassName`/`textareaClassName` constants, which this phase explicitly copied verbatim per its own Design System section). Fix: either amend the spec to explicitly carve out `blue-500` as the correct focus-ring shade (if intentional, matching Radix/shadcn convention), or update both `nova/page.tsx` and its `processos/novo` source-of-truth to `focus-visible:ring-blue-600`.

3. **`CardTitle` does not receive the spec-mandated `text-lg font-bold` override** (`web/src/app/(dashboard)/pareceres/nova/page.tsx:121` — `<CardTitle>Dados da Solicitação</CardTitle>`) — the base component (`web/src/components/ui/card.tsx:28-34`) only applies `font-semibold leading-none tracking-tight` with no explicit text size, meaning "Dados da Solicitação" renders at the browser default `h3` size (~1.17em, ~600 weight) rather than the spec's declared 18px/700 CardTitle role. This exactly matches `processos/novo/page.tsx`'s existing (unfixed) pattern, so it is a systemic gap inherited by this phase rather than newly introduced — but the phase's own Typography section explicitly claims "18px (text-lg)... 700 (bold, explicit `font-bold` override)" is in effect, which the shipped code does not satisfy. Fix: add `className="text-lg font-bold"` to each `<CardTitle>` usage, or update the base `CardTitle` component to include these defaults, and backport to `processos/novo/page.tsx` for consistency.

Additional notable issues (not in top 3, but real):
- No guard preventing form submission while `clientes`/`processos`/`adminUsers` queries are still loading — a user on a slow connection could submit before the cliente list populates, though RHF/Zod validation would catch a truly empty `clienteId`.
- No min-date constraint on the `prazo` date input — a user can select a date in the past for a deadline field.
- Access-guard logic (`canCreatePareceres` check) is duplicated identically in both `ParecerCreatePage` and `ParecerCreateFormContent` (lines 33-42 and 50-51/85-88) — functionally correct (defense in depth) but indicates copy-paste rather than a shared guard hook, raising future-maintenance risk if the permission scope changes.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)
- H1 "Nova Solicitação de Parecer" — matches spec exactly (`nova/page.tsx:108`).
- Subheading matches spec exactly (`nova/page.tsx:111`).
- List page CTA "Nova Solicitação" matches spec (`page.tsx:102`).
- Submit CTA "Criar Solicitação" / "A criar..." matches spec's present-progressive pattern (`nova/page.tsx:223`).
- Cancel button correctly uses "Cancelar" (not "Voltar") per spec's explicit single-step-form distinction (`nova/page.tsx:115, 226`).
- All field labels match verbatim: "Cliente", "Processo (opcional)", "Descrição", "Prazo (opcional)", "Prioridade", "Advogado responsável (opcional)" (`nova/page.tsx:126,151,167,180,190,199`).
- Select placeholders match verbatim: "Selecionar cliente" / "A carregar...", "Nenhum processo associado", "Atribuir mais tarde" (`nova/page.tsx:133,157,201`).
- Prioridade default "MEDIA" pre-selected, options "Alta"/"Média"/"Baixa" match (`nova/page.tsx:63,192-194`, `schemas/pareceres.ts:35`).
- Textarea placeholder matches verbatim (`nova/page.tsx:171`).
- Validation errors match verbatim, including the exact 10-character threshold copy (`schemas/pareceres.ts:14,23,30`).
- Submit error copy matches verbatim (`nova/page.tsx:97`).
- Success toast copy matches verbatim (`nova/page.tsx:91`).
- Success redirect matches spec: invalidate `["pareceres","list"]` (inside `useCreateParecer`, `use-pareceres.ts:81`), toast, then `router.push` to detail page (`nova/page.tsx:92`).
- Access-denied copy matches verbatim (`nova/page.tsx:38`).
No deviations found. This is the strongest pillar.

### Pillar 2: Visuals (3/4)
- Primary submit button correctly styled as the visual endpoint: `bg-blue-600 hover:bg-blue-700 text-white font-bold`, paired with lower-weight outline "Cancelar" (`nova/page.tsx:213-227`) — matches spec's focal-point description exactly.
- Single-column Card layout, no wizard/step indicator — matches spec (one-step form).
- No asterisk convention on required fields — matches spec's explicit "do not introduce one" instruction (labels for `clienteId`/`descricao` are visually identical to optional fields).
- **Gap:** `processoId` select (optional field) has no visual distinction for its loading state — while `clienteId` shows "A carregar..." during fetch, `processoId` always renders the same static placeholder regardless of fetch status, creating inconsistent visual feedback between two adjacent, structurally similar controls (`nova/page.tsx:130-144` vs `151-164`).
- Filter icon on the list page correctly paired with visible text "Filtros" (not icon-only), so no aria-label gap there (`page.tsx:111-119`).

### Pillar 3: Color (3/4)
- Accent (`blue-600`/`blue-700`) correctly restricted to the primary submit button and "Nova Solicitação" CTA only — not applied to labels, static text, or the Cancelar/outline buttons (verified via grep: only 2 `bg-blue-600` occurrences in the two files, both on primary CTAs).
- Destructive `red-600` correctly restricted to validation error text and submit-failure banner, no delete/destructive actions present — matches spec.
- **Gap:** Both `selectClassName` and `textareaClassName` constants, and the `prazo` `<Input>`, use `focus-visible:ring-blue-500` (`nova/page.tsx:26,29,184`), not `blue-600`/`blue-700` as the Color table specifies for the "input focus-visible ring." This is inherited unchanged from `processos/novo/page.tsx` (confirmed same constant is duplicated verbatim per the phase's own Design System instruction), so it's a pre-existing systemic mismatch between the written contract and the actual reused pattern, not a new regression — but it does mean the contract as literally written is not satisfied.
- Badge status colors on the list page (`gray`/`blue`/`amber`/`green`) are inherited from Phase 65 and out of this phase's scope; not re-audited here.

### Pillar 4: Typography (3/4)
- Body/label text consistently `text-sm`/`text-xs`, weight 400 (default, no explicit `font-normal` needed since it's the browser/Tailwind default and no bold override is applied to body text) — matches spec.
- Page H1 correctly `text-2xl font-bold` (not `text-3xl`, which the list page correctly uses instead) — matches spec's explicit sub-page vs. list-root distinction (`nova/page.tsx:107` vs `page.tsx:97`).
- Validation error text correctly `text-sm text-red-600` throughout (`nova/page.tsx:141,146,175,210`).
- Only two weights in use in the create form (`font-bold` and default/regular) — matches "do not introduce a third weight" instruction.
- **Gap:** `<CardTitle>Dados da Solicitação</CardTitle>` (`nova/page.tsx:121`) receives no `className` override. The shared `CardTitle` primitive (`web/src/components/ui/card.tsx:28-34`) applies only `font-semibold leading-none tracking-tight` — no `text-lg`, and weight is 600 not 700. The spec's Typography table explicitly states this role should be "18px (text-lg) / 700 (bold, explicit `font-bold` override per Phase 65 convention)" — this override is absent both here and in the `processos/novo/page.tsx` source pattern it was copied from, so the "Phase 65 convention" referenced in the spec does not actually exist in the codebase as written.

### Pillar 5: Spacing (4/4)
- Root wrapper: `space-y-6` (24px) — matches spec (`nova/page.tsx:104`).
- Form field rows: `space-y-4` (16px) — matches spec (`nova/page.tsx:124`).
- Field-internal label-to-input gap: `space-y-2` (8px), applied consistently across all 6 field groups (`nova/page.tsx:125,150,166,179,189,198`) — matches spec exactly.
- Button row: `flex gap-2` (8px) — consistent with "compact element spacing" token.
- Header row: `gap-4` (16px) between title block and Cancelar button (`nova/page.tsx:105`) — consistent with declared md token.
- No arbitrary/non-scale spacing values (`[Npx]`/`[Nrem]`) found in the audited files.

### Pillar 6: Experience Design (2/4)
- Access-guard: correctly implemented via `usePermissions()` + `AccessDeniedState`, gating both the create page and (redundantly) the form submit handler — functionally safe but duplicated (lines 33-42, 50-51, 85-88), a maintenance smell rather than a user-facing defect.
- Loading state: `clienteId` select shows "A carregar..." and disables during `clientes.isPending`/`isError` (lines 130,133,140-144) — good. **`processoId` select has zero loading/error state** (lines 151-164) — if `useProcessos` is slow or errors, the user sees a plain, seemingly-final "Nenhum processo associado" with no indication data is still loading or failed to load. This is the most significant Experience Design gap: two adjacent fields backed by identical async-query patterns behave inconsistently.
- Submit-pending state: correctly disables the submit button and swaps label to "A criar..." during `form.formState.isSubmitting || createParecer.isPending` (lines 216-223) — good.
- Submit-error state: correctly surfaces both an inline `formError` banner and a `toast.error` (lines 93-100, 210) — good, matches spec's dual-channel error pattern.
- No client-side guard preventing submission while `clientes`/`processos`/`adminUsers` are still in-flight — a fast typist could submit with `clienteId` populated from a stale/partial list, though Zod validation on `clienteId` (required, non-empty) would still catch a fully-empty submission; this is a minor race, not a broken flow.
- No min-date (`min={today}`) constraint on the native date input for `prazo` (line 181-186) — spec doesn't explicitly require this, but it's a common expectation for a "deadline" field; flagged as a quality gap rather than a spec violation.
- Destructive-action confirmation: not applicable, correctly absent (create-only form, no delete action) — matches spec.
- List page (`page.tsx`) empty/loading/error states are all present and copy-appropriate (lines 192-204) — good, reinforces that the omission on `processoId` in the create form is an isolated inconsistency rather than a systemic pattern failure.

---

## Registry Safety

`components.json` not found in `web/` — shadcn CLI is not initialized, consistent with UI-SPEC.md's "Registry Safety: Not applicable" declaration. Registry audit skipped entirely per protocol.

---

## Files Audited

- `web/src/app/(dashboard)/pareceres/nova/page.tsx`
- `web/src/app/(dashboard)/pareceres/page.tsx`
- `web/src/schemas/pareceres.ts`
- `web/src/hooks/use-pareceres.ts` (partial — `useCreateParecer`, types)
- `web/src/hooks/use-processos.ts` (partial — filter param naming)
- `web/src/components/ui/card.tsx` (partial — `CardTitle` default styling)
- `web/src/app/(dashboard)/processos/novo/page.tsx` (partial — cross-reference for pattern fidelity)
- `.planning/phases/LEXCV-66-cria-o-de-solicita-o/66-UI-SPEC.md`
- `.planning/phases/LEXCV-66-cria-o-de-solicita-o/66-CONTEXT.md`
</content>
