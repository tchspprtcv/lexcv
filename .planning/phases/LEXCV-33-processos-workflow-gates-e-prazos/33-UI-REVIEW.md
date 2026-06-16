---
phase: 33
plan: "03"
audited: 2026-06-16
baseline: UI-SPEC.md (approved 2026-06-15)
screenshots: not captured (no dev server)
---

# Phase 33 — UI Review

**Audited:** 2026-06-16
**Baseline:** UI-SPEC.md (approved, status: approved)
**Screenshots:** not captured (no dev server — code-only audit)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | "PRAZO PROXIMO" missing accent; prazo Responsável field omitted from dialog; toggle error copy missing |
| 2. Visuals | 3/4 | Critical transition buttons use raw className with a stale "variant-outline" string instead of variant prop; ChevronRight icon omitted |
| 3. Color | 3/4 | No hardcoded hex values in new code; `dark:bg-[#020617]` token is an established project convention, not a deviation; color contract substantially met |
| 4. Typography | 3/4 | DialogTitle uses shadcn default `font-semibold` instead of spec `font-bold`; `text-[13px]` appears in listing (undeclared in spec) |
| 5. Spacing | 3/4 | Spec-declared `space-y-4` / `p-6` / `py-3 px-4` all present; `h-9 px-3` on "Novo Prazo" button deviates from spec `h-10 px-4`; minor arbitrary-value proliferation |
| 6. Experience Design | 2/4 | Toggle-concluido failure is silently swallowed; "Novo Prazo" dialog missing Responsável field; dialog-open transicaoError is not cleared on close |

**Overall: 16/24**

---

## Top 3 Priority Fixes

1. **"PRAZO PROXIMO" label missing accent character** — Users and lawyers reading the badge will see malformed Portuguese. In `web/src/lib/prazos.ts` line 25, change `"PRAZO PROXIMO"` to `"PRAZO PRÓXIMO"` (and line 28 fallback). This is also the value surfaced in the listing badge, so both places are affected by a single fix.

2. **Toggle-concluido failure silently swallowed** — When `toggleConcluido.mutateAsync` rejects, the catch block at `[id]/page.tsx` line 284 swallows the error with a comment "silent — optimistic update will revert". The spec requires the error copy "Erro ao atualizar o prazo. Tente novamente." to be shown. Add a `setPrazoError(...)` call inside that catch block and render `prazoError` below the prazos list (or inside the prazo row inline).

3. **Critical transition buttons use a stale "variant-outline" string in className instead of the Button `variant` prop** — `[id]/page.tsx` line 590 reads `"variant-outline border border-slate-300 ..."`. The string `variant-outline` is not a Tailwind class and will be ignored, so the button will render with whatever the default Button variant applies rather than the intended outline styling. Replace with `variant="outline"` prop and apply border/color overrides separately in className.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)

**WARNING — Three distinct copy deviations found:**

**1a. "PRAZO PROXIMO" missing accent (BLOCKER-level copy defect)**
- File: `web/src/lib/prazos.ts` lines 25 and 28
- Spec contract (UI-SPEC.md line 207): `"PRAZO PRÓXIMO"`
- Actual value: `"PRAZO PROXIMO"` (no accent on Ó)
- Impact: Every "proximo" risco badge on the listing and the prazo detail row shows malformed Portuguese. This affects all users.

**1b. Prazo "Novo Prazo" dialog missing Responsável field**
- File: `web/src/app/(dashboard)/processos/[id]/page.tsx` lines 754-820
- Spec contract (UI-SPEC.md lines 193-196): The Novo Prazo dialog must include a `Responsável` select populated from the user list, with label "Responsável".
- Actual: The dialog renders Descrição, Data Limite, Prioridade — but has no Responsável field at all. `responsavelId` is in the form default values and schema but is never presented to the user for input.
- Impact: Users cannot assign a responsible party when creating a prazo, defeating a key requirement of AGD-22.

**1c. Toggle-concluido error copy missing**
- File: `web/src/app/(dashboard)/processos/[id]/page.tsx` line 284
- Spec contract (UI-SPEC.md): "Erro ao atualizar o prazo. Tente novamente."
- Actual: The catch block is empty (`// silent — optimistic update will revert`). The spec-required error message is never shown.
- Impact: Users get no feedback when the concluido toggle fails. An optimistic revert with no explanation causes confusion.

**1d. Dialog title concatenation diverges from spec**
- File: `[id]/page.tsx` line 612
- Spec contract (UI-SPEC.md lines 227-229): Dialog titles should be "Suspender Processo", "Encerrar Processo", "Reabrir Processo" (specific fixed strings).
- Actual: `{activeTransicao?.label} Processo` — this depends on the backend-supplied `label` field matching the expected string prefix. If the backend sends "Suspender" the result is "Suspender Processo" which matches. However if the backend sends a different label (e.g., already includes "Processo" or uses Portuguese accents differently) the title diverges. Low-severity, but the copy spec cannot be verified without backend output.

**Score justification:** Two confirmed deviations (1a missing accent, 1b missing form field) with direct user impact push this to 2/4. The silent error (1c) adds a third confirmed miss.

---

### Pillar 2: Visuals (3/4)

**WARNING — Critical button variant applied incorrectly:**

**2a. Critical transition buttons: `variant-outline` is not a Tailwind class**
- File: `[id]/page.tsx` line 590
- Spec contract (UI-SPEC.md line 111): `variant="outline"` on the Button component + border/color overrides.
- Actual className: `"variant-outline border border-slate-300 dark:border-slate-700 bg-white dark:bg-transparent rounded-none font-bold h-10 px-4 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800"`
- The string `variant-outline` is an invalid Tailwind class — Tailwind does not generate this utility. The Button component will render using its default variant (likely `default`, which applies `bg-primary` fill). This means the critical/manage-level transitions receive the same primary fill styling as normal transitions, removing the visual distinction between edit-level and manage-level actions.
- Fix: Pass `variant="outline"` as a prop to the Button component; move border color overrides into className only.

**2b. ChevronRight icon ("Próximo Passo" separator) omitted**
- File: `[id]/page.tsx` — the Próximo Passo dl row (line 555-558)
- Spec contract (UI-SPEC.md Component Inventory line 294): `ChevronRight` (12px, slate-400) should appear as a separator on the Próximo passo field.
- Actual: No ChevronRight imported or rendered anywhere in the new code.
- Impact: Minor visual detail; the field still conveys information without the icon. Low-severity.

**2b. Workflow card estado badge is duplicated**
- File: `[id]/page.tsx` lines 519-526 (CardHeader) and lines 539-544 (dl grid Estado row)
- The spec shows the header badge as a summary indicator and the dl row Estado as one of the three data fields. The implementation renders the same badge in both places, creating redundancy. Not a blocker, but adds visual noise.

**Score justification:** The `variant-outline` issue breaks the intended visual distinction between normal and critical transitions — this is the primary role of the visual design for this feature. Deducting a point for this concrete defect.

---

### Pillar 3: Color (3/4)

**WARNING — Color contract substantially met with one finding:**

**3a. No hardcoded hex colors in new Phase 33 code**
- The `dark:bg-[#020617]` pattern used in `[id]/page.tsx` line 775 and `page.tsx` throughout is an established project-wide token for the dark background, matching the design system's Dominant Dark value. This is intentional per the existing codebase convention and matches Phase 32 precedent. Not flagged.

**3b. Accent (blue-600) usage is correctly scoped**
- Normal transition buttons: `bg-blue-600 hover:bg-blue-700` — correct (edit-level actions, matches spec).
- "Novo Prazo" button: `bg-blue-600 hover:bg-blue-700` — correct (primary CTA, matches spec).
- Justificativa Textarea focus ring: `focus-visible:ring-blue-500` — correct.
- No blue-600 applied to informational text, decoration, or critical transitions.

**3c. Critical transition buttons may incorrectly receive blue-600 fill (consequence of 2a)**
- Because `variant-outline` is not a valid Tailwind class and the Button default is `bg-primary` (blue), the critical transitions may actually render with blue-600 fill, violating the color contract that specifies neutral outline for manage-level transitions. This is a side-effect of finding 2a.

**Score justification:** The color mapping in `lib/prazos.ts` is correct (ok→green, proximo→amber, vencido→red). No unauthorized hardcoded colors in new code. One secondary defect from the variant bug. Score 3/4.

---

### Pillar 4: Typography (3/4)

**WARNING — Minor deviations from the declared type scale:**

**4a. DialogTitle uses `font-semibold` instead of spec `font-bold`**
- File: `web/src/components/ui/dialog.tsx` line 82
- The shadcn-generated DialogTitle applies `font-semibold leading-none tracking-tight`.
- Spec contract (UI-SPEC.md Design System): Heading role = `font-bold`. All CTAs and section labels throughout the detail page use `font-bold`.
- Fix: Override in the dialog usage: `<DialogTitle className="font-bold">` or patch `dialog.tsx`.

**4b. `text-[13px]` appears in listing**
- File: `page.tsx` line 152-153 (the "Próximas Audiências" card): `text-[13px] leading-relaxed`
- Declared type scale: xs(11px), sm(14px), base(14px), lg(24px), 3xl(30px). 13px is between `text-xs` (12px via Tailwind default) and `text-sm` (14px), neither of which is 11px or 14px exactly.
- This is pre-existing code (the card is not Phase 33 work) but it is present in the audited file.

**4c. Font sizes in scope for Phase 33 new additions**
- New workflow card and prazos card: `text-sm`, `text-xs`, `text-[11px]` — all within declared scale.
- No `text-lg`, `text-xl`, `text-2xl` introduced in the new cards.
- Typography in new sections is disciplined.

**Score justification:** DialogTitle `font-semibold` vs. `font-bold` is a minor but verifiable deviation from the heading weight contract. The `text-[13px]` is pre-existing. Score 3/4.

---

### Pillar 5: Spacing (3/4)

**WARNING — One confirmed deviation; declared scale otherwise followed:**

**5a. "Novo Prazo" button height and padding deviate from spec**
- File: `[id]/page.tsx` line 663
- Spec contract (UI-SPEC.md Interaction States line 302): transition buttons and primary CTAs should be `h-10 px-4`.
- Actual: `h-9 px-3` on the "Novo Prazo" button header CTA.
- Impact: The "Novo Prazo" button is 4px shorter and 4px narrower on each side than the spec requires. Below the 44px minimum touch target specified for transition action buttons (h-9 = 36px).

**5b. Spec spacing scale correctly applied in new sections:**
- Card inner padding: `CardContent className="space-y-4"` — spec requires `space-y-4`, correct.
- Prazo row: `px-4 py-3` — matches spec exactly (`py-3 px-4`).
- Dialog body: `space-y-4 py-4` — matches spec (`space-y-4`).
- Transition buttons: `gap-2` — matches spec (`gap-2` for action buttons).

**5c. Arbitrary values in new code**
- `min-h-[100px]` on Textarea — spec-approved (UI-SPEC.md line 152 explicitly calls this out).
- `h-[14px] w-[14px]` on icons — spec-approved (UI-SPEC.md Component Inventory).
- `h-[14px] w-[14px]` icons are within the spec's `xs` (4px) icon-to-label gap convention.

**Score justification:** The `h-9 px-3` CTA button misses the 44px accessibility minimum stated in the spacing spec. All other spacing is per contract. Score 3/4.

---

### Pillar 6: Experience Design (2/4)

**WARNING — Multiple interaction state gaps:**

**6a. Toggle-concluido error is silently dropped (BLOCKER)**
- File: `[id]/page.tsx` lines 281-287
- The `onToggleConcluido` catch block is empty. When the mutation fails, the user sees nothing (no error state, no toast, no badge change explanation). Optimistic UI revert without explanation is confusing.
- Fix: Add `setPrazoError("Erro ao atualizar o prazo. Tente novamente.")` inside the catch and render it.

**6b. "Novo Prazo" dialog missing Responsável field**
- Already flagged in Pillar 1. From an XD perspective: users can create a prazo but cannot assign it. The form renders `responsavelId` only in state, never as a visible input.
- Impact: Partial task completion — the core create-prazo flow works, but the responsibility assignment capability is absent.

**6c. transicaoError not cleared when justificativa dialog closes**
- File: `[id]/page.tsx` lines 609-653
- When the user clicks "Cancelar" to close the Dialog, `setDialogOpen(false)` is called (line 638) but `setTransicaoError(null)` is not. If a transition previously failed showing an error, then the user opens the justificativa dialog and cancels, the error from the previous attempt persists in the UI below the transition buttons.
- Fix: Add `setTransicaoError(null)` in the Dialog `onOpenChange` handler or in the Cancelar onClick.

**6d. Missing Responsável in the prazo row center/meta section**
- File: `[id]/page.tsx` lines 702-707
- The spec requires a "responsável name (text-xs text-slate-500)" alongside the prioridade tag in the prazo row's center/meta area.
- Actual: Only prioridade is rendered. No responsável display in the prazo row.
- Impact: After a prazo is created (even if the Responsável field were added), the list row cannot show who owns the prazo.

**6e. Loading/error states for workflow and prazos cards are correct**
- `workflow.isLoading` → text "A carregar workflow..." — matches spec.
- `workflow.isError` → text "Erro ao carregar o workflow. Tente novamente." — matches spec.
- `prazos.isLoading` → text "A carregar prazos..." — matches spec.
- `prazos.isError` → text "Erro ao carregar os prazos. Tente novamente." — matches spec.
- Permission gating on transitions via `disabled={!hasPermission || transicao.isPending}` — correct.
- `title` tooltip on disabled buttons renders the spec-required "Sem permissão: requer {permissaoNecessaria}" — correct.
- Empty prazos: "Nenhum prazo registado para este processo." — matches spec.
- Empty transitions: "Nenhuma transição disponível neste estado." — matches spec.

**Score justification:** Two functional gaps (toggle error swallowed, Responsável field absent from dialog and list row) mean user task completion is degraded for two spec-required features. Error state leakage (6c) is a secondary UX issue. Score 2/4.

---

## Registry Safety

`components.json` not found at `web/components.json` — shadcn is not initialized at the project root. Registry audit skipped. (The shadcn CLI was used to generate the files during Plan 03 task execution; the resulting files are standard shadcn official components. No third-party registries used per UI-SPEC Registry Safety table.)

---

## Files Audited

- `web/src/app/(dashboard)/processos/[id]/page.tsx` (1116 lines — primary audit target)
- `web/src/app/(dashboard)/processos/page.tsx` (397 lines — listing signals)
- `web/src/lib/prazos.ts` (30 lines — risco helper)
- `web/src/components/ui/dialog.tsx` (114 lines — newly added shadcn component)
- `web/src/components/ui/textarea.tsx` (17 lines — newly added shadcn component)
- `.planning/phases/LEXCV-33-processos-workflow-gates-e-prazos/33-UI-SPEC.md` (design contract)
- `.planning/phases/LEXCV-33-processos-workflow-gates-e-prazos/33-03-PLAN.md` (execution contract)
- `.planning/phases/LEXCV-33-processos-workflow-gates-e-prazos/33-03-SUMMARY.md` (what was built)
