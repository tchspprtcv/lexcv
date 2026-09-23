# Phase 127 — UI Review

**Audited:** 2026-09-22
**Baseline:** `127-UI-SPEC.md` (note: frontmatter `status: draft`, Checker Sign-Off section shows all six dimensions unchecked and `Approval: pending` — this audit treats it as the best-available design contract per task instructions, but the spec itself was never formally marked approved)
**Screenshots:** not captured — no dev server / database available in this session (retroactive code-only audit, per task constraints)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | Every declared string (CTAs, dialog copy, toasts, refusal text, empty/error states) matches the spec's Copywriting Contract byte-for-byte. |
| 2. Visuals | 3/4 | Matrix focal point and accessibility are faithfully built, but the "must never have to guess" secondary anchor (unsaved-edit state of "Guardar Alterações") is signalled only by the button's default disabled/opacity-50 state — no dirty indicator, and switching Settings tabs silently discards unsaved matrix edits. |
| 3. Color | 3/4 | Matrix/button/dialog blue usage matches the contract exactly, but `CriarPapelPanel`'s permission-checklist selected-row border/background (`border-blue-500/50 bg-blue-500/5`) and checkbox tint (`text-blue-600`) are blue elements the Color section's "no other element may use blue" clause doesn't enumerate. |
| 4. Typography | 3/4 | The two authored sizes/weights and all four inherited-component weights (400/500/600/700) check out exactly against the cited component source — but `CriarPapelPanel`'s permission description uses `text-[10px]`, a 7th font size never itemized anywhere in the spec's Typography table (which only documents 11px for the matrix's sub-label). |
| 5. Spacing | 4/4 | `p-3` and `min-w-[140px]` exceptions land exactly where declared; no undeclared arbitrary spacing values found. |
| 6. Experience Design | 4/4 | Loading/error/empty states, disabled-while-pending on every mutation, AlertDialog-gated delete, and defense-in-depth cache invalidation (`auth/me` invalidated alongside RBAC/users on save/rename/delete) are all present and correctly wired. |

**Overall: 21/24**

---

## Top 3 Priority Fixes

1. **Unsaved-edit state ("Guardar Alterações") has no signal beyond default button opacity** — An administrator who has ticked several checkboxes, then switches to the "Gestão de Utilizadores" tab and back (or navigates away entirely), loses the edit with no warning; the only "something changed" cue in the interim is the button going from faded to solid blue, which is easy to miss on a screen whose focal point is deliberately the matrix, not the header toolbar. Add a small always-visible dirty indicator (e.g. `"{N} papéis com alterações por gravar"` text next to the button, or a dot on each changed role's column header) so the spec's own bar — "the one piece of status an administrator must never have to guess at" — is met by more than a CSS disabled-state. `web/src/app/(dashboard)/settings/page.tsx:1033-1044`.

2. **Color contract is violated by inherited create-panel markup** — `127-UI-SPEC.md`'s Color section states accent blue is "Reserved for" a specific list (save/create/rename buttons, matrix checked checkboxes, focus rings) and explicitly says "No other element may use blue as a fill or as badge/text color." `CriarPapelPanel`'s permission checklist uses `border-blue-500/50 bg-blue-500/5 dark:bg-blue-500/10` for a selected row and `text-blue-600` on its checkbox — reused verbatim from Phase 125's `criar-molde-panel.tsx:130,139`, but never carved out as a declared exception the way `p-3`/`min-w-[140px]` were for Spacing. Either amend the Color section to list this as an inherited exception, or restyle the create-panel checklist's selected state to a neutral tone (e.g. `border-slate-300 bg-slate-100/50`, matching the role-picker's neutral selected state at `page.tsx:724-727`) so blue stays confined to the declared set. `web/src/app/(dashboard)/settings/criar-papel-panel.tsx:124-136`.

3. **`text-[10px]` is a 7th, undocumented font size** — The Typography table's inherited-elements list enumerates exactly one micro sub-label size (`text-[11px]`, matrix permission description). `CriarPapelPanel`'s own permission description uses `text-[10px]` (`criar-papel-panel.tsx:139`), inherited verbatim from `criar-molde-panel.tsx:143` but never itemized in the spec. The visual delta is negligible, but the contract's own closing claim about "the full set of weights/sizes in play" is incomplete without it. Either add this row to the Typography table, or normalize to `text-[11px]` to match the matrix's own sub-label size and shrink the actual size inventory by one.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

Every string in the Copywriting Contract table was checked against the shipped source and matches exactly, including punctuation and em-dashes:
- Card title/description, "Guardar Alterações", "Criar Papel" — `page.tsx:1017-1044`.
- Repurposed note box, verbatim including the second (live-effect) sentence — `page.tsx:1049-1057`.
- Provenance badges ("Predefinido"/"Criado por si"), user-count badge pluralization — `page.tsx:1118-1124`.
- Protected-role lock tooltip — `page.tsx:1103-1107`.
- Rename dialog title/description/field labels/submit — `page.tsx:1209-1244`.
- Reserved-name validation error — `schemas/papeis-escritorio.ts` (`nomeReservado` refine message).
- Delete refusal (assigned-users and protected-role variants), pluralization — `papel-acoes-menu.tsx:64-67`.
- Delete confirmation title/body/action — `page.tsx:1258-1276`.
- All four success toasts (save/create/rename/delete) — `page.tsx:935,957,977,991`.
- Empty state heading/body — `page.tsx:1062-1066`.
- Error state fetch copy + "Tentar novamente" — `page.tsx:896-901`.
- User picker section label "Papéis do Escritório" — `page.tsx:716`.

No generic "Submit"/"OK"/"Click Here" patterns found in the audited files; "Cancelar" is the one recurring label, matching the spec and the rest of the codebase's convention, not a lazy default.

### Pillar 2: Visuals (3/4)

Strengths: the matrix is unambiguously the dominant visual element of `CardContent` (note box is a thin strip above it, badges/kebab are compact); every icon-only control has an `aria-label` (`Lock` → `"Papel protegido"`, kebab trigger → `"Mais ações para o papel {nome}"`, close `X` → `"Fechar"`); hierarchy is carried through size/weight differentiation exactly as the Typography table prescribes (bold header, medium row-label, 11px sub-label).

Gap: the spec explicitly calls out a **secondary anchor** — "whether there are unsaved changes is the one piece of status an administrator must never have to guess at, because this tab now lets them lose work by navigating away" (`127-UI-SPEC.md` §Focal point). The implementation's only signal is the native `disabled:opacity-50` on the "Guardar Alterações" `Button` (`page.tsx:1033-1044`, `existeDiff` gates `disabled`). There is no persistent count, no per-column dirty marker, and no confirmation before the state is lost — `RbacTab` is conditionally rendered only while `activeTab === "rbac"` (`page.tsx:192-196`), so switching to "Gestão de Utilizadores" and back unmounts/remounts it, silently dropping any local, unsaved matrix edits. This meets the letter of "disabled until dirty" but not the spirit of "must never have to guess" as strongly as the spec's own framing implies.

### Pillar 3: Color (3/4)

Confirmed correct: `bg-blue-600 hover:bg-blue-700` on "Guardar Alterações", "Criar Papel" trigger + panel submit, and "Guardar Nome"; `text-blue-600` on matrix checked checkboxes (`page.tsx:1178`); `bg-red-600 hover:bg-red-700 text-white` on the delete `AlertDialogAction` (`page.tsx:1269`), matching the cited precedent verbatim (`plataforma/page.tsx:563`, `financeiro/[id]/page.tsx:370,563`). No hardcoded hex/rgb literals in any of the three audited files. Badges are neutral-only (`gray`/`outline` variants, confirmed in `components/ui/badge.tsx` — both map to neutral-100/800 backgrounds, no color tint), correctly avoiding the "coloring provenance" trap the spec's discretion notes call out. No amber introduced, as declared.

Gap: `CriarPapelPanel`'s permission-checklist selected-row treatment (`border-blue-500/50 bg-blue-500/5 dark:bg-blue-500/10`, `criar-papel-panel.tsx:124-128`) and its checkbox's `text-blue-600` tint (`:135`) are blue usages the Color section's enumerated list does not cover. This is byte-for-byte inherited from Phase 125's `criar-molde-panel.tsx:126-139` (confirmed identical), so it is not novel drift introduced by this phase's implementer — but the Phase 127 spec's own Color section states "No other element may use blue as a fill or as badge/text color" without carving out this reused snippet as an exception (unlike the Spacing section, which explicitly lists `p-3`/`min-w-[140px]` as accepted carry-overs). The contract text and the shipped screen disagree on this one point.

### Pillar 4: Typography (3/4)

Verified against actual component source, not just the spec's claims: `Button` is `text-sm font-medium` (`components/ui/button.tsx:8`); `Label` is `text-sm font-medium` (`label.tsx:13`); `DialogTitle`/`AlertDialogTitle` are `text-lg font-semibold` (`dialog.tsx:83`, `alert-dialog.tsx:77`); `DialogDescription`/`AlertDialogDescription` are `text-sm` with no weight class, i.e. inherited 400 (`dialog.tsx:96`, `alert-dialog.tsx:90`); `DropdownMenuItem` is `text-sm` with no weight class (`dropdown-menu.tsx:76`); `Tooltip` content is `text-xs` inherited (`tooltip.tsx:45`). All of these match the spec's Typography table exactly — the spec's citations are accurate, not aspirational. Matrix header/module/row-label classes (`font-bold`, `font-medium`, `text-[11px]`) are reproduced identically at `page.tsx:1094,1150,1153`. Authored prose (note box, dialog descriptions) stays within the declared 14px/400 and 12px/400+700(`<strong>`) discipline.

Gap: `criar-papel-panel.tsx:139` uses `text-[10px]` for the permission description inside the create panel — inherited verbatim from `criar-molde-panel.tsx:143`, but this size is not listed anywhere in the Typography table's inherited-elements inventory (which only documents `text-[11px]` for the *matrix's* sub-label, sourced from a different snippet). The screen therefore ships one more distinct font size than the spec accounts for. Low visual severity — 10px vs 11px is barely perceptible — but it's a real gap between the documented size inventory and the shipped DOM.

### Pillar 5: Spacing (4/4)

`p-3` appears on every matrix `<td>`/`<th scope="row">` cell exactly as declared, matching `RbacTab`'s and `/plataforma/moldes`'s precedent byte-for-byte. `min-w-[140px]` is present only on the per-role column header `<th>` (`page.tsx:1094`), not applied elsewhere. The first column keeps `min-w-[280px]` (inherited from `RbacTab`/moldes precedent, not separately flagged by the spec but consistent with reuse mandate). No other arbitrary bracket-pixel spacing values were found in any of the three phase files; the standard 4/8/16/24 scale (`gap-1`, `gap-1.5`, `gap-2`, `gap-2.5`, `space-y-2`, `space-y-4`, `space-y-6`, `p-4`, `p-6` via `CardContent`/`CardFooter`) is used consistently.

### Pillar 6: Experience Design (4/4)

Loading (`Loader2` spinner), error (`AlertCircle` + "Tentar novamente" retry), and empty (`Empty`/`EmptyTitle`/`EmptyDescription`/`EmptyContent` with a "Criar Papel" CTA) states are all present for the RBAC tab, matching the current `RbacTab` and `/plataforma/moldes` recipes. Every mutating action disables its trigger while pending and shows a localized in-flight label ("A gravar...", "A criar...", "A apagar..."). Delete is gated behind an `AlertDialog` that is only ever reachable from the `podeApagar === true` branch — there is no dead/disabled "Apagar" affordance anywhere, exactly as the spec's §9 closing paragraph requires. The floor-lock rule is driven by `papel.protegido` and `papel.permissoes` (the server-persisted set), never by a string comparison on `nome` — confirmed both in the JSX comment and in `types/office-rbac.ts`'s doc-comment, which explicitly states `protegido`/`podeApagar` "são computados no SERVIDOR e nunca devem ser re-derivados no cliente." Mutation hooks (`use-admin.ts`) invalidate `auth/me` alongside the RBAC and users queries on save/rename/delete — defense-in-depth against a user editing their own effective authority mid-session, beyond what the spec strictly required. The unsaved-edit merge (`merge-local-papeis.ts`) correctly generalizes Phase 125's `mesclarEstadoLocal` to the three-mutation case (create/rename/delete) with id-keyed reconciliation, matching UI-SPEC §6's contract clause-by-clause, including the "deleting the role currently being edited must not throw" requirement.

---

## Files Audited

- `.planning/phases/LEXCV-127-pap-is-e-permiss-es-do-escrit-rio/127-UI-SPEC.md`
- `web/src/app/(dashboard)/settings/page.tsx` (`RbacTab`, `UserManagementTab`)
- `web/src/app/(dashboard)/settings/criar-papel-panel.tsx`
- `web/src/app/(dashboard)/settings/papel-acoes-menu.tsx`
- `web/src/app/(dashboard)/settings/merge-local-papeis.ts`
- `web/src/schemas/papeis-escritorio.ts`
- `web/src/types/office-rbac.ts`
- `web/src/hooks/use-admin.ts` (RBAC-related hooks: `useOfficeRbac`, `useSaveOfficeRbac`, `useCreateOfficeRole`, `useRenameOfficeRole`, `useDeleteOfficeRole`)
- `web/src/app/(dashboard)/plataforma/moldes/page.tsx` (Phase 125 analog, matrix accessibility precedent)
- `web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx` (Phase 125 analog, create-panel precedent)
- `web/src/components/ui/{button,label,card,badge,dialog,alert-dialog,dropdown-menu,tooltip}.tsx` (inherited-typography verification)
- `web/src/app/(dashboard)/plataforma/page.tsx`, `web/src/app/(dashboard)/financeiro/[id]/page.tsx` (destructive-red precedent verification)

Registry audit: not applicable — `components.json` not found at repository root, and `127-UI-SPEC.md`'s Registry Safety table declares no third-party registries for this phase.
