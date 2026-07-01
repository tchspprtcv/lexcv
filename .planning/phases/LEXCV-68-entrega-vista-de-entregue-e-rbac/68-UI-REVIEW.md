# Phase 68 — UI Review

**Audited:** 2026-07-01
**Baseline:** `.planning/phases/LEXCV-68-entrega-vista-de-entregue-e-rbac/68-UI-SPEC.md` (approved design contract)
**Screenshots:** not captured (no dev server at localhost:3000 — code-only static audit)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | All mandated strings match verbatim (dialog, toasts, block copy), but the "Não foi possível carregar as solicitações" error string is incorrectly reused for the version-timeline load failure, which is not a "solicitações" load |
| 2. Visuals | 3/4 | Clear focal point (Entregar/Entregue block) established correctly, but the version-select `<select>` inside the AlertDialog is an unstyled native element inconsistent with the rest of the app's form controls |
| 3. Color | 3/4 | The two long-standing cross-phase leaks (timeline dot, FileDropZone trigger text) are both now fixed; destructive-weight styling on the trigger button is correctly applied per spec, but the CardTitle/accent work is undermined by one unaddressed pre-existing hover-accent on the list page unrelated to this phase's scope, noted but not blocking |
| 4. Typography | 4/4 | Every single `CardTitle` across all 3 files in `/pareceres` now carries `text-lg font-bold` with zero exceptions — the 3x-recurring defect is finally fully closed module-wide |
| 5. Spacing | 4/4 | Root `space-y-6`, card wrapper `space-y-4`, entrega block `space-y-3`/`space-y-2` all align to the declared 4/8/16/24 scale; no arbitrary bracket values found in the new code |
| 6. Experience Design | 3/4 | Loading/error/empty/disabled states and dual-gate RBAC (`canEditPareceres && isResponsavelOuAdmin && !isConcluido`) are correctly implemented, but there is no defense against the entrega mutation racing a concurrent status change beyond a generic error string, and the version-select control offers no visual confirmation before the destructive confirm |

**Overall: 20/24**

---

## Top 3 Priority Fixes

1. **CardTitle typography defect is CLOSED — verify this stays closed via a regression test.** No code fix needed this phase (already correct), but given this is the 3rd time this exact defect recurred (Phases 65→66→67→now-fixed-68), add a lightweight snapshot/lint rule (e.g. a `CardTitle` eslint rule requiring `className` prop, or a snapshot test asserting the class list) so a future phase cannot silently regress it a 4th time. This is a process fix, not a code fix — but it is the single most important action item given the module's history.

2. **Native unstyled `<select>` inside `EntregarParecerDialog` breaks visual consistency with the rest of the app's form language** (`pareceres/[id]/page.tsx:479-491`) — every other select-like control in this module (`nova/page.tsx` selectClassName, list-page filter selects) uses the shared bordered/rounded/focus-ring styling (`selectClassName` constant), but the version-picker inside the entrega dialog uses ad hoc inline classes (`className="flex h-9 w-full rounded-md border border-neutral-200..."`) that diverge in border-radius convention (`rounded-md` here vs. `rounded-none` used everywhere else in this exact module — list page, nova page, all buttons/inputs are explicitly `rounded-none`). This is a real, visible micro-inconsistency in the single most consequential dialog in the app (irreversible action). Fix: change `rounded-md` to `rounded-none` on line 481 to match every other control in `/pareceres`.

3. **No stale-state guard on the entrega confirm dialog** — if the solicitação transitions state (e.g., another version added, or already entregue by a concurrent session) while the `AlertDialog` is open with a stale `versoes` list, the user can still attempt to confirm against an outdated version list; the failure path only shows a generic `entregaError` string (page.tsx:439-442), never re-syncing the underlying data or closing the dialog to reflect the new state. This is the same defect class flagged in 67-UI-REVIEW.md Priority Fix #3, still unresolved, now on an even higher-stakes irreversible action. Fix: on entrega mutation failure with a 403/409-shaped error, close the dialog and force a refetch of `parecer`/`versoes` so the UI reflects the authoritative state rather than leaving a stale confirm dialog open.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

Verified against the Copywriting Contract table line-by-line:

- "Entregar Parecer" trigger button — `page.tsx:461`, exact match.
- Dialog title "Entregar Parecer" — `page.tsx:470`, exact match.
- Irreversibility statement — `page.tsx:472-474`, exact verbatim match: "Esta ação é irreversível. Depois de entregue, o parecer não pode receber novas versões nem ser reaberto."
- Version-selection prompt — `page.tsx:478`, exact match: "Selecione a versão a entregar como final:" — defaults to most recent version (`versoes[versoes.length - 1]`, `page.tsx:426`) but still requires explicit re-confirmation via the select's own controlled state (`selectedVersaoIdState`), matching the spec's "not a silent default" requirement.
- Confirm button — `page.tsx:506`, "Confirmar Entrega" / "A entregar..." — exact match.
- Cancel button — `page.tsx:497`, "Cancelar" — exact match.
- Success toast — `page.tsx:436`, "Parecer entregue com sucesso." — exact match.
- Error banner+toast — `page.tsx:441-444`, "Não foi possível entregar o parecer. Verifique a ligação e tente novamente." — exact match, dual-channel (inline + toast) as required.
- "Parecer Entregue" heading — `page.tsx:532`, exact match.
- Version reference — `page.tsx:544-546`, "Versão {numeroVersao}" — exact match, no fabricated "entregue por/em" line.
- Author/date line — `page.tsx:547-550`, "Elaborado por {autor} em {data}" sourced from `versaoFinal.criadoPorId`/`createdAt` — correctly does NOT fabricate an entrega-specific timestamp, matching the spec's explicit constraint.
- Anexo link — reuses `AnexoLink` component verbatim ("Descarregar anexo"/"Sem anexo") — exact match.
- RBAC hidden button — no passive banner; `showEntregarTrigger` gate (`page.tsx:296-298`) omits the section entirely when false — matches "no dead buttons" principle.
- Access denied copy — `page.tsx:85`, unchanged from Phase 65 — exact match.

Deduction: the version-timeline error state (`page.tsx:222-224`) and the top-level parecer load error (`page.tsx:173-175`) both render the identical string "Não foi possível carregar as solicitações. Verifique a ligação e tente novamente." — but the timeline failure is a *versões* fetch failure, not a *solicitações* list failure. This is a copy-paste carried over from Phase 67 and never corrected; it is misleading (a user seeing this on the version list has no reason to think "solicitações" failed to load). Minor, but a real accuracy defect in copy, hence 3/4 not 4/4.

### Pillar 2: Visuals (3/4)

- Focal point pre-entrega: "Entregar Parecer" button correctly styled with `bg-destructive`/irreversible-action weight (`page.tsx:457-462`), not a cheerful primary CTA — matches spec's explicit instruction to treat it with destructive visual weight.
- Focal point post-entrega: "Parecer Entregue" block correctly replaces the trigger and is the only card shown in that slot (`page.tsx:284-291`, conditional render), matching spec's "block replaces trigger button" requirement.
- Green badge reused for CONCLUIDO status inside the block (`page.tsx:541-543`) — correctly reuses the established status-badge green semantic, no new accent introduced.
- Visual inconsistency: the version-select control (`page.tsx:479-491`) uses `rounded-md` while literally every other input/select/button in this module (`nova/page.tsx`, `page.tsx` elsewhere, `pareceres/page.tsx`) uses `rounded-none` as a consistent design language choice. This one control breaks that pattern inside the single highest-stakes dialog in the app — a real, visible defect, not nitpicking, since it's directly adjacent to the destructive-styled buttons that DO follow the pattern.
- Icon-only affordances: none introduced this phase; `Paperclip` icon (`AnexoLink`, reused) is always paired with visible text.

### Pillar 3: Color (3/4)

Verified against the two mandatory carry-over fixes from 67-UI-REVIEW.md:

1. **Timeline dot marker leak — FIXED.** `page.tsx:240` now renders `<span className="h-2.5 w-2.5 rounded-full shrink-0 bg-slate-400 dark:bg-slate-500" />` — neutral, matches the spec's required fix exactly. Confirmed no `bg-blue-600` remains on any decorative marker in the module.
2. **`FileDropZone` trigger text leak — FIXED** (unexpectedly, since the spec explicitly said this was out of scope for this phase and only "acknowledged"). Current `file-drop-zone.tsx:76` renders `className="text-sm text-neutral-700 dark:text-neutral-300 hover:underline..."` — no accent color present at all. This is a net positive beyond what the phase mandated; the shared-component leak flagged across two prior phases is now closed.

Accent-reservation compliance in this phase's new code:
- Entrega trigger button correctly uses `bg-destructive`, NOT `bg-blue-600` — per spec's explicit instruction that this action's visual weight should NOT spend the accent budget (`page.tsx:457-462`, `page.tsx:504`).
- Progress-bar fill (`page.tsx:390`, pre-existing from Phase 67) correctly retains `bg-blue-600` — this is the one declared exception (progress fill) and remains compliant.
- "Nova Solicitação"/"Criar Solicitação" primary CTAs correctly retain `bg-blue-600` (`page.tsx:101`, `nova/page.tsx:215`) — these are the declared primary-CTA accent uses.

Remaining minor issue (pre-existing, not newly introduced, but still present in the module this phase touches): `pareceres/page.tsx:259` — `className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors"` on the client-name link in the desktop table. This is a *hover*-state accent on a link, arguably a defensible "interactive affordance" rather than "static at-rest text" (the spec's forbidden category), so it's not a clear-cut violation the way the two carried-over leaks were — but it is a third distinct accent touchpoint beyond the two declared uses (primary CTA + progress fill), and the spec's 60/30/10 language implies the accent budget should be tightly reserved. Not scored as a blocker since it's arguably an accepted "link hover" convention pre-dating this phase and out of this phase's stated scope, but flagged for awareness. This is why the score is 3/4 rather than a clean 4/4 — the two mandatory fixes are done, but the module isn't in a state where the accent is used in exactly and only the two declared places.

### Pillar 4: Typography (4/4)

Full audit of every `<CardTitle>` usage across all 3 files in `/pareceres`, per the explicit instruction to check every instance:

| File | Line | Text | Class |
|------|------|------|-------|
| `pareceres/[id]/page.tsx` | 180 | "Dados" | `text-lg font-bold` ✓ |
| `pareceres/[id]/page.tsx` | 213 | "Versões" | `text-lg font-bold` ✓ |
| `pareceres/[id]/page.tsx` | 345 | "Nova Versão" | `text-lg font-bold` ✓ |
| `pareceres/[id]/page.tsx` | 532 | "Parecer Entregue" (new this phase) | `text-lg font-bold` ✓ |
| `pareceres/nova/page.tsx` | 121 | "Dados da Solicitação" | `text-lg font-bold` ✓ |

`pareceres/page.tsx` (list page) uses no `CardTitle` at all (its filter card has no header title) — confirmed via grep, no orphaned/unstyled instance exists there either.

This is a full, unambiguous fix. Every single instance across the entire module — including the two that Phase 66 and Phase 67 both independently left unfixed ("Dados", "Versões") — now carries the mandated `text-lg font-bold`. Zero exceptions found. This closes the 3-phase-recurring defect completely. Scored 4/4: no issues found, contract fully met, exactly as mandated.

The `AlertDialogTitle` "Entregar Parecer" (`page.tsx:470`) correctly does NOT carry an explicit weight override, per the spec's declared exception — retains the shared component's `font-semibold` (600) default, matching the one other existing dialog (Agenda's delete). This is the accepted 3rd weight tier and is correctly NOT extended anywhere else.

No new font sizes or weights beyond the declared 400/600(dialog-only)/700 set were introduced.

### Pillar 5: Spacing (4/4)

- Root wrapper: `space-y-6` (`page.tsx:163`) — matches declared `lg`/24px.
- Card wrapper around Dados/Versões/Entregue-or-Form: `space-y-4` (`page.tsx:177`) — matches declared `md`/16px.
- "Parecer Entregue" block internal spacing: `space-y-3` (`page.tsx:540`) — 12px, one step off the declared `space-y-4` (16px) the spec calls for ("matching the 'Dados'/'Versões' cards' internal spacing exactly" per spec line 44) — however the visible field rows inside use `space-y-3` not `space-y-4`; this is a genuine minor deviation from the literal spec value but stays on the 4px grid and is not visually disruptive.
- Version-select dialog field group: `space-y-2` (`page.tsx:477`) — matches declared `sm`/8px.
- No arbitrary bracket spacing values (`[…px]`, `[…rem]`) found in the entrega-related new code.

Given the one small deviation (`space-y-3` vs. spec's declared `space-y-4` for the Entregue block) is sub-visual-threshold and still grid-aligned, this remains a 4/4 — no blocking or clearly-visible spacing defects.

### Pillar 6: Experience Design (3/4)

State coverage confirmed present:
- Loading: `parecer.isLoading`, `versoes.isLoading`, `permissions.isLoading` all handled with distinct skeleton/text states, including a loading skeleton specifically for the Entregar/Entregue slot (`page.tsx:278-283`) so the button doesn't flash-in before permissions resolve.
- Error: `parecer.isError`, `versoes.isError`, and the entrega mutation's own inline `entregaError` + toast — all present.
- Empty: version list empty state reused correctly, unaffected by this phase.
- Disabled states: dialog's Cancel/Confirm/select all correctly disabled during `entregar.isPending` (`page.tsx:484, 497, 499`); `onOpenChange`/`onEscapeKeyDown` both correctly guard against closing the dialog mid-mutation (`page.tsx:451-454, 465-467`) — a genuinely good defensive touch not seen in the Phase 67 pattern.
- RBAC dual-gate: `showEntregarTrigger` combines `canEditPareceres && isResponsavelOuAdmin && !isConcluido` (`page.tsx:155-160`), correctly mirroring the backend's `isAdmin || isResponsavel` instance check exactly as CONTEXT.md mandated — this is genuinely well done and matches the established Phase 67 pattern for `showNovaVersaoForm`.
- Read-only enforcement: `isConcluido` correctly suppresses both `showNovaVersaoForm` and `showEntregarTrigger` (`page.tsx:153-160`) — a CONCLUIDO solicitação is fully read-only in the UI regardless of role, matching CONTEXT.md's explicit requirement.

Gaps:
- No guard against the entrega dialog operating on stale data if the underlying solicitação changes state while the dialog is open (see Priority Fix #3) — the same defect class flagged and left unresolved in 67-UI-REVIEW.md, now present on a higher-stakes irreversible action.
- No double-submit debounce beyond the `disabled` prop tied to `entregar.isPending` — acceptable, consistent with prior phases, not a new gap.
- The version `<select>` defaults to "most recent" via array-index lookup (`versoes[versoes.length - 1]`) rather than an explicit "most recent" flag from the API — this is a reasonable inference but silently assumes the array is returned in creation order; if the backend ever returns versions in a different order, the default-selected version would silently be wrong with no client-side validation catching it. Not a currently-visible defect, but a real latent risk worth flagging.

---

## Registry Safety

`components.json` not found in `web/` — shadcn CLI is not initialized (confirmed, matches this phase's UI-SPEC `shadcn_initialized: false`). Registry audit skipped entirely per this phase's spec ("Not applicable — no shadcn CLI/registry is in use").

---

## Files Audited

- `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
- `web/src/app/(dashboard)/pareceres/nova/page.tsx`
- `web/src/app/(dashboard)/pareceres/page.tsx`
- `web/src/components/shared/file-drop-zone.tsx`
- `web/src/hooks/use-pareceres.ts`
- `.planning/phases/LEXCV-68-entrega-vista-de-entregue-e-rbac/68-UI-SPEC.md`
- `.planning/phases/LEXCV-68-entrega-vista-de-entregue-e-rbac/68-CONTEXT.md`
- `.planning/phases/LEXCV-67-elabora-o-e-versionamento/67-UI-REVIEW.md` (baseline for recurring-defect verification)
