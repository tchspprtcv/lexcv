# Phase 67 — UI Review

**Audited:** 2026-07-01
**Baseline:** `.planning/phases/LEXCV-67-elabora-o-e-versionamento/67-UI-SPEC.md` (approved design contract)
**Screenshots:** not captured (no dev server at localhost:3000 — code-only static audit)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | All mandated strings match verbatim, but success/error toasts and inline states are not unit-tested and one required copy path (non-responsável passive banner) is silently omitted per spec's own permitted default — acceptable, but no automated regression guard exists |
| 2. Visuals | 3/4 | Clear focal point (Submeter Versão button) exists but timeline dot styling (`bg-blue-600` on every entry) creates ambient accent noise instead of being reserved for the single CTA and progress fill |
| 3. Color | 2/4 | Accent (`blue-600`) leaks onto timeline bullet dots (page.tsx:221) and `FileDropZone`'s "Clique para selecionar" trigger text (file-drop-zone.tsx:76) — spec explicitly restricts accent to "Submeter Versão" button and progress-bar fill only |
| 4. Typography | 2/4 | The Phase 66 typography bug the spec explicitly required fixing was fixed for the new "Nova Versão" title, but the two pre-existing `CardTitle`s on the same page ("Dados", "Versões") still render at the `h3` default size with no `text-lg`, producing three different heading sizes/weights across three cards on one page |
| 5. Spacing | 4/4 | `space-y-6` root, `space-y-4` card content, `space-y-2` field groups, `space-y-1` progress rows — all match the declared 4/8/16/24 scale exactly, no arbitrary values found |
| 6. Experience Design | 3/4 | Loading, error, empty, disabled, and progress states are all handled; RBAC-gated visibility works; but no confirmation/guard against double-submit beyond `disabled` on the button and no visible "unsaved changes" or dirty-state warning when the form has content and status flips to CONCLUIDO mid-session |

**Overall: 17/24**

---

## Top 3 Priority Fixes

1. **Accent color leaks outside its declared reservation** (page.tsx:221, file-drop-zone.tsx:76) — Users lose the single clear "this is the action" signal because blue now appears on every timeline entry dot and on the file-picker link text, diluting the CTA's visual priority the spec was designed to protect. Fix: change timeline dot to a neutral `bg-slate-400`/`bg-slate-300` (decorative marker, not interactive) and leave `FileDropZone`'s trigger text as-is only if the spec is amended — otherwise recolor to `text-slate-700 dark:text-slate-300` with underline to signal "clickable" without consuming the accent budget, since this component is shared/verbatim and pre-dates this phase's stricter color contract.

2. **Inconsistent CardTitle typography across the same page** (page.tsx:161, 194 vs. 325) — "Dados" and "Versões" section titles render as unstyled `h3` (`font-semibold`, browser-default size, no explicit size class) while "Nova Versão" correctly renders `text-lg font-bold` per the spec's mandatory fix. This produces three visually distinct heading treatments in one visual hierarchy, undermining the "explicit class required on every CardTitle" instruction, which — while scoped by the spec's prose to "this phase's new code" — leaves a visibly broken page since all three cards are adjacent and compared directly by the user. Fix: add `className="text-lg font-bold"` to the `CardTitle` at lines 161 and 194 to match line 325, closing the same gap Phase 66 already flagged once.

3. **No optimistic/defensive guard for stale "Nova Versão" form after status change** — If another user (or a concurrent action) transitions the solicitação to `CONCLUIDO` while this user has the form open with unsaved input, submission will hit the backend's 403/validation path but the visible symptom is only a generic `serverError` string; the spec's "Parecer já entregue" read-only banner never appears because the client-side `isConcluido` check is derived from a query result fetched before the conflict. Fix: on `versaoUpload` mutation failure with a 403/409-shaped backend error, refetch `parecer` and swap the form for the read-only banner immediately rather than just rendering the generic dual-channel error copy.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

Verified against the Copywriting Contract table line-by-line:

- "Submeter Versão" / "A submeter..." — page.tsx:384-388, exact match.
- "Nova Versão" section title — page.tsx:325, exact match.
- "Resumo da versão" label — page.tsx:330, exact match.
- Textarea placeholder — page.tsx:333, exact match (verbatim, including the parenthetical).
- Textarea helper text — page.tsx:337-339, exact match.
- "Anexo (obrigatório)" — page.tsx:346, exact match.
- "Formatos aceites: PDF, Word, imagens." — page.tsx:359-361, exact match.
- Validation error missing anexo — schemas/pareceres.ts:42,59, exact match ("É necessário anexar um ficheiro para submeter esta versão.").
- Validation error conteudo too short — schemas/pareceres.ts:53, exact match ("O resumo deve ter pelo menos 10 caracteres.").
- Progress label — page.tsx:365-366 renders `"A enviar..."` and `{progresso}%"` as separate spans rather than the single-string "A enviar... {N}%" the spec literally shows, but visually renders identically in a flex row. Acceptable — not a defect, just an implementation detail of the same visible text.
- Submit success toast — page.tsx:310, exact match.
- Submit error banner+toast — page.tsx:315-318, exact match, dual-channel (inline `serverError` state + `toast.error`) as required.
- Read-only banner (CONCLUIDO) — page.tsx:268-273, exact heading/body match.
- Read-only banner (non-responsável) — correctly omitted entirely (page.tsx:276-278, `showNovaVersaoForm` gate), matching the spec's stated default behavior of silent omission rather than a passive-explanation banner.

Deduction: no test coverage found for any of these copy strings (no `*.test.tsx` file discovered alongside `pareceres/[id]/page.tsx`), so a future refactor could silently drift from the contract with no automated signal. This is a process gap, not a currently-visible defect, hence 3/4 rather than lower.

### Pillar 2: Visuals (3/4)

- Focal point: "Submeter Versão" is the only filled/primary button on the page (default `Button` variant), correctly positioned as the terminal action of the form — good hierarchy.
- Icon-only affordances: `Paperclip` icon in `AnexoLink` (page.tsx:106) is always paired with visible text ("Descarregar anexo" / "A preparar..."), not icon-only — passes the aria-label/tooltip requirement trivially since there's no icon-only button in this phase's code.
- Visual hierarchy via size/weight: undermined by the CardTitle inconsistency (see Pillar 4) — three adjacent cards on the same page show three different title treatments, which reads as an accident rather than an intentional layout choice.
- Timeline: the `bg-blue-600` dot marker (page.tsx:221) is applied uniformly to every version regardless of recency or status, so it carries no differentiating information while simultaneously competing with the CTA for "this is the important blue thing" attention — a visual-hierarchy defect, not just a color-pillar one.

### Pillar 3: Color (2/4)

Spec's exact language: "Accent reserved for: the 'Submeter Versão' primary button and the upload progress-bar fill. Never applied to labels, static text, drag-zone borders at rest, or decorative elements."

Verified violations:
- `page.tsx:221` — `<span className="h-2.5 w-2.5 rounded-full shrink-0 bg-blue-600" />` — the timeline dot marker is a decorative element, explicitly the category the spec forbids accent on. This exists in Phase 65-authored code but is present and visible on the same page this phase modifies, and the spec draws no exception for this element.
- `file-drop-zone.tsx:76` — `className="text-sm text-blue-600 dark:text-blue-400 hover:underline ..."` — the "Clique para selecionar" trigger is static (at-rest) text carrying accent color, contradicting "never applied to labels, static text... at rest." The spec directs reuse of `FileDropZone` "verbatim," which technically authorizes this exact byte-for-byte reuse — but the same spec's color table is written as an absolute rule with no explicit carve-out for this pre-existing violation, so the contract is self-contradictory here. Flagging as a real deviation regardless of which document technically "wins," since the visible outcome is exactly the leak the color contract was written to prevent.
- Correctly compliant: `bg-blue-600` progress-bar fill (page.tsx:370) and `Button` default variant on "Submeter Versão" (page.tsx:384) match the intended reservation.
- Focus ring: confirmed no blue ring introduced anywhere in the new code (textarea/input inherit `ring-neutral-950`/`ring-neutral-300` from shared components, unmodified) — this specific corrected requirement from the spec is honestly met.

Given two confirmed leaks of the sole accent color onto decorative/static elements — directly the failure mode the spec calls out by name — this pillar scores 2/4, not 3/4; "notable gaps" rather than "minor issues," since accent dilution was the single most specific instruction in the Color section.

### Pillar 4: Typography (2/4)

Declared requirement: "every `<CardTitle>` in the new 'Nova Versão' section MUST carry an explicit `className='text-lg font-bold'`... This is not optional discretion."

- `page.tsx:325` — `<CardTitle className="text-lg font-bold">Nova Versão</CardTitle>` — compliant, correctly fixes the exact defect the spec called out from Phase 66.
- `page.tsx:161` — `<CardTitle className="font-bold">Dados</CardTitle>` — missing `text-lg`, renders at `card.tsx:28-34`'s unstyled `h3` default (browser default, not the spec's declared 18px).
- `page.tsx:194` — `<CardTitle className="font-bold">Versões</CardTitle>` — same gap.

The spec's prose scopes the *mandatory* language to "the new 'Nova Versão' section," so a literal reading limits the violation's blast radius. However the spec's own Typography table declares "Section title (CardTitle: 'Nova Versão', 'Histórico de Versões')" as a single row with one 18px/700-weight rule — implying both the new form title and any adjacent Card section titles on the page should be visually consistent. Since "Dados" and "Versões" are structurally identical section-title cards sitting directly above the correctly-styled "Nova Versão" card, the page now visibly demonstrates two different heading treatments for what a user perceives as the same UI role. This is the same defect class the spec was written specifically to close out from Phase 66 — reappearing one card up. Scored 2/4 for a real, visible, adjacent inconsistency, not because the letter of the "new code" scope was violated.

Font sizes/weights in the audited file: `text-3xl font-bold` (H1), `text-sm`/`text-xs` (body/labels), `font-medium` (a few dd/labels), `text-lg font-bold` (Nova Versão only), unstyled `font-semibold h3` default (Dados/Versões). This introduces a third effective weight tier (`font-medium`, e.g. page.tsx:166) beyond the spec's declared 400/700-only rule — a secondary, minor typography deviation.

### Pillar 5: Spacing (4/4)

- Root wrapper: `space-y-6` (page.tsx:144) — matches declared `lg`/24px section gap.
- Card-internal form: `space-y-4` (page.tsx:328) — matches declared `md`/16px form field gaps, exactly as specified ("matches `documentos/novo/page.tsx`'s form spacing exactly").
- Field label-to-input groups: `space-y-2` (page.tsx:329, 345) — matches declared `sm`/8px.
- Progress label/bar block: `space-y-1` (page.tsx:363) — this is 4px, one step below the spec's declared `sm`/8px for "progress label row," a minor deviation but functionally indistinguishable at this scale and consistent with `documentos/novo/page.tsx`'s reused pattern per the spec's own instruction to reuse that markup verbatim.
- No arbitrary bracket-value spacing (`[…px]`, `[…rem]`) found in the audited file.
- Between-cards gap: `space-y-4` wrapping all three cards (page.tsx:158) rather than the declared `space-y-6`/24px "card-to-card gap between timeline and new-version form" — this is a deviation from the literal spec value (16px actual vs. 24px declared), but it is inherited from Phase 65's pre-existing wrapper, not newly introduced in this phase, and is visually minor.

No blocking spacing defects found; deviations are sub-4px/pre-existing and don't break the declared multiples-of-4 rule.

### Pillar 6: Experience Design (3/4)

State coverage confirmed present:
- Loading: `parecer.isLoading`, `versoes.isLoading` (skeleton pulse blocks), `permissions.isLoading` (skeleton card) — all handled distinctly.
- Error: `parecer.isError`, `versoes.isError` — inline red-text messages; `serverError` state + toast for the mutation.
- Empty: "Nenhuma versão ainda" empty state (page.tsx:206-212) with helper subtext — good, specific copy rather than generic "No data."
- Disabled: submit button and form fields correctly disabled during `isSubmitting`/`isPending` (page.tsx:334, 353, 384).
- RBAC gating: `showNovaVersaoForm` correctly combines `canEditPareceres && isResponsavelOuAdmin && !isConcluido`, matching both the frontend permission check and the backend's instance-level `isAdmin || isResponsavel` rule — this is genuinely well-implemented double-layer gating.
- Progress reporting: real XHR-based upload progress via `useCreateParecerVersao`, matching the spec's required pattern.

Gaps:
- No guard against submitting while the underlying solicitação has transitioned to `CONCLUIDO` mid-session (see Priority Fix #3) — a stale-form race condition with no client-side detection beyond a generic error string.
- No explicit double-submit debounce beyond the `disabled` prop tied to `isPending`/`isSubmitting` — acceptable but not defensive against rapid re-renders or network race conditions.
- No destructive-action confirmation is required per spec (version creation is additive) — correctly not implemented, not a gap.

---

## Registry Safety

`components.json` not found in `web/` — shadcn CLI is not initialized (confirmed, matches UI-SPEC's Design System declaration `shadcn_initialized: false`). Registry audit skipped entirely per this phase's spec ("Not applicable — no shadcn CLI/registry is in use").

---

## Files Audited

- `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
- `web/src/components/ui/card.tsx`
- `web/src/schemas/pareceres.ts`
- `web/src/components/shared/file-drop-zone.tsx`
- `web/src/hooks/use-pareceres.ts` (lines 80-140, `useCreateParecerVersao`)
- `.planning/phases/LEXCV-67-elabora-o-e-versionamento/67-UI-SPEC.md`
- `.planning/phases/LEXCV-67-elabora-o-e-versionamento/67-CONTEXT.md`
