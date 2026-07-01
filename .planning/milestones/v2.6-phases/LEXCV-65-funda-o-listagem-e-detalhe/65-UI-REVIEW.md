# Phase 65 — UI Review

**Audited:** 2026-07-01
**Baseline:** 65-UI-SPEC.md (approved)
**Screenshots:** not captured (no dev server available in this environment — code-only static audit against UI-SPEC.md)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 2/4 | Status badges render raw enum constants (`PENDENTE`, `EM_ELABORACAO`) instead of Portuguese labels, contradicting the spec's Portuguese-tone contract; cliente name unresolved on detail page (shows raw UUID) |
| 2. Visuals | 3/4 | Focal points mostly correct (badge column on list, version timeline on detail) but detail page's primary "focal point" data point (cliente) displays a raw UUID, undermining scannability |
| 3. Color | 3/4 | Badge variant mapping matches spec's declared table exactly; accent (blue) usage stays within declared bounds (link hover, active badge, focus rings) |
| 4. Typography | 3/4 | Matches declared 2-weight (400/700) and size-scale contract, but introduces an undeclared `text-[11px]` arbitrary size not in the spec's type scale |
| 5. Spacing | 4/4 | `space-y-6` root wrapper, `p-4`/`gap-2`/`gap-3` spacing all trace to the declared 4px-multiple scale; consistent with `processos` convention |
| 6. Experience Design | 3/4 | Loading/error/empty states implemented per spec copy exactly; but AnexoLink's error path silently swallows failures with only a code comment, no visible fallback beyond the global toast |

**Overall: 18/24**

---

## Top 3 Priority Fixes

1. **Status badges display raw enum values, not the Portuguese labels already defined elsewhere in the same file** — `web/src/app/(dashboard)/pareceres/page.tsx:212-214,245-247` and `web/src/app/(dashboard)/pareceres/[id]/page.tsx:143-145` render `{s.status}` / `{parecer.data.status}` directly, producing badges reading "PENDENTE", "EM_ELABORACAO", "EM_REVISAO", "CONCLUIDO" verbatim in the UI. The Portuguese sentence-case labels ("Pendente", "Em elaboração", "Em revisão", "Concluído") already exist as `<option>` text in the same file (lines 135-138) but are never reused for badge display. User impact: breaks the copywriting contract's "consistent Portuguese tone, sentence case" requirement and looks unfinished/untranslated to end users scanning the primary visual anchor of the list page. Fix: extract a `STATUS_LABELS: Record<ParecerStatus, string>` map (or a `statusLabel()` helper alongside `statusVariant()`) and use it for badge children in both files.

2. **Cliente name not resolved on the detail page — raw UUID shown in the "Dados" card** — `web/src/app/(dashboard)/pareceres/[id]/page.tsx:134`: `<dd className="col-span-2 font-medium">{parecer.data.clienteId}</dd>`. The list page (`page.tsx:60,66-69,210,254`) already fetches `useClientes({})` and builds a `clienteNomeById` map to resolve this exact field, but the detail page never imports or calls `useClientes`, so it falls back to displaying the raw UUID. User impact: the detail page's header metadata — the first thing a lawyer reads to confirm they've opened the right parecer — shows an unreadable UUID instead of the client's name, actively harming the page's usability. Fix: add `useClientes({})` in `ParecerDetailContent`, resolve `parecer.data.clienteId` to a name the same way `advogadoId` is resolved via `resolveUserNome`.

3. **`AnexoLink` swallows download failures with no visible in-component feedback** — `web/src/app/(dashboard)/pareceres/[id]/page.tsx:71-78`: the `catch` block is empty apart from a comment claiming `apiFetch` already surfaces a toast. This is plausible given the codebase's `apiFetch` toast convention, but it means if the toast is missed/dismissed, the user has zero on-screen indication that "Descarregar anexo" failed — the button just returns to its normal (non-loading) state with no error text, no retry affordance, no visual distinction from a successful click. User impact: silent failure on a core read action of this phase (anexo download is explicitly called out as the primary reason to open the detail page in some flows). Fix: surface a lightweight inline error state (e.g. `download.isError` → small red text "Falha ao obter o anexo" beside the button) in addition to the global toast, matching the resilience pattern used for list/detail fetch errors elsewhere on the same page.

---

## Detailed Findings

### Pillar 1: Copywriting (2/4)

- **Confirmed correct against spec:** Page H1 "Pareceres Jurídicos" (`page.tsx:96`) and "Parecer Jurídico" (`[id]/page.tsx:115`) — matches spec table exactly (spec only declares the list H1 explicitly; detail H1 is a reasonable extension, not contradicted).
- **Confirmed correct:** Empty state list — "Nenhuma solicitação de parecer encontrada" / "Ajuste os filtros ou aguarde a criação de novas solicitações." (`page.tsx:194,196`) — exact match to spec.
- **Confirmed correct:** Empty state detail — "Nenhuma versão ainda" / "Aguarda elaboração pelo advogado atribuído." (`[id]/page.tsx:176,178`) — exact match.
- **Confirmed correct:** Error copy — "Não foi possível carregar as solicitações. Verifique a ligação e tente novamente." reused verbatim in both list (`page.tsx:189`) and detail (`[id]/page.tsx:123,172`) — matches spec exactly (spec explicitly calls for reuse of this phrasing).
- **Confirmed correct:** Access denied — "Não tem permissão para consultar o módulo de pareceres." (`page.tsx:44`, `[id]/page.tsx:55`) — exact match.
- **Confirmed correct:** Anexo copy — "Sem anexo" (`[id]/page.tsx:68`) and "Descarregar anexo" (`[id]/page.tsx:91`) — exact match. Note the transient "A preparar..." loading label (`[id]/page.tsx:90`) is a reasonable addition not contradicted by spec.
- **Violation:** Status badges show raw enum text (`s.status` / `parecer.data.status`) rather than a human label — see Priority Fix #1. The filter `<option>` elements do carry correct Portuguese labels ("Pendente", "Em elaboração", "Em revisão", "Concluído" at `page.tsx:135-138`), which is what makes the badge omission an obvious inconsistency rather than a hard blocker — one code path clearly demonstrates the intended labels were known but not reused for the badge.
- **Nav item label:** "Pareceres" (`dashboard-shell.tsx:48`) — exact spec match.

### Pillar 2: Visuals (3/4)

- List page focal point matches spec: status badge column is first `<TableHead>` and leftmost visual element in both card and table layouts (`page.tsx:212-214, 234, 245-247`) — correctly draws the eye per spec's stated intent.
- Detail page focal point matches spec: version timeline (`[id]/page.tsx:182-222`) sits as the dominant content block in its own Card below the "Dados" metadata card, with a vertical connector line and dot markers giving clear chronological hierarchy — matches spec's stated intent well.
- Icon-only affordance check: `AnexoLink`'s `Paperclip` icon is always paired with visible text ("Descarregar anexo" / "A preparar...") — no bare icon-only buttons found in either file. Passes icon-label pairing check.
- Deduction: the detail page's primary metadata (cliente identity) is visually present but semantically empty (raw UUID) — see Fix #2, which undermines the "clear focal point" requirement since the reader can't actually identify the case at a glance.

### Pillar 3: Color (3/4)

- Status badge variant mapping (`statusVariant()` duplicated identically in both files, `page.tsx:25-35` / `[id]/page.tsx:35-45`) matches the spec's declared table exactly: `PENDENTE→gray`, `EM_ELABORACAO→blue`, `EM_REVISAO→amber`, `CONCLUIDO→green`. Verified against `badge.tsx` variant definitions (blue/green/amber/gray all exist as declared CVA variants with the correct hex families: `blue-100/blue-700`, `emerald-100/emerald-700` [spec says "green" — badge.tsx uses emerald, a reasonable interpretation], `amber-100/amber-700`, `neutral-100/neutral-700`).
- Accent (blue) usage confirmed scoped correctly: `hover:text-blue-600` only on the client-name link in the table row (`page.tsx:252`) and `focus-visible:ring-blue-500` on filter selects (interactive focus state, not decorative) — matches spec's "never applied to static body text or decorative elements" rule.
- No hardcoded hex/rgb colors found in either file (`grep` for `#[0-9a-fA-F]` / `rgb(` returned no matches in the two audited files) — all color via Tailwind utility classes, consistent with the design system.
- Minor deduction: `statusVariant()` logic is duplicated verbatim across both files rather than extracted to a shared module (e.g. `web/src/lib/parecer-status.ts`). Not a visual defect per se, but a maintenance risk that increases the odds the two badge implementations drift out of sync in later phases — flagged here since color-mapping correctness depends entirely on this function staying identical in both places.

### Pillar 4: Typography (3/4)

- Confirmed weights in use: `font-bold` (700) on H1s, CardTitles (with explicit override per spec's exact prescription at `[id]/page.tsx:129,162`), badges, and table headers; `font-medium` / default (400) on body/table cells. No third distinct weight introduced — matches spec's 2-weight-max contract.
- Confirmed sizes: `text-3xl` (H1), `text-lg` implied via CardTitle default, `text-sm` (body/dl values), `text-xs` (secondary metadata, badge text) — all within spec's declared scale.
- Deduction: `text-[11px]` arbitrary value used for filter section labels ("ESTADO", "ADVOGADO", "CLIENTE" uppercase labels, `page.tsx:125,143,162`) and `text-[10px]` for table column headers (`page.tsx:234-238`). Neither 10px nor 11px appears in the spec's declared type scale (12px/14px/18px/30px only). This is a pre-existing pattern almost certainly copied from `processos/page.tsx` (per CONTEXT.md's "port near-verbatim" instruction), so it's a pattern inherited from elsewhere in the app rather than a new invention — but the UI-SPEC's own typography table does not carve out an exception for it, so it remains a literal deviation from the written contract.

### Pillar 5: Spacing (4/4)

- Root wrapper `space-y-6` (24px) on both pages (`page.tsx:93`, `[id]/page.tsx:112`) — exact match to spec's declared `lg` token and explicit "matches processos/page.tsx exactly" exception clause.
- Card/section spacing (`space-y-4` inner content, `p-4` card padding, `gap-2`/`gap-3` filter row gaps, `gap-x-4 gap-y-3` on the definition list grid) all trace to 4px-multiple values matching the declared xs/sm/md scale.
- No arbitrary pixel/rem spacing values found in either file (the `text-[11px]`/`text-[10px]` arbitrary values are typography, not spacing, and are counted under Pillar 4).
- Timeline connector spacing (`py-4`, `pb-4`, absolute-positioned `top-3 bottom-0 left-[5px]`) uses one arbitrary positioning value (`left-[5px]`) for the vertical connector line alignment — this is a sub-pixel visual alignment necessity (centering a 2px line under a 10px dot) rather than a spacing-scale violation, and is not a token-scale value per se; not deducting for this, as it is structurally necessary and not a spacing choice.

### Pillar 6: Experience Design (3/4)

- Loading states: list ("A carregar...", `page.tsx:186`) and detail (`[id]/page.tsx:120`) both present; detail's Versões card additionally has a proper skeleton (`animate-pulse` blocks, `[id]/page.tsx:167-169`) rather than plain text, which is a nice touch above the bare-minimum bar.
- Error states: both fetch failure paths render the exact spec-declared copy, in visually distinct red text (`text-red-600`) — consistent and correctly styled as the "Destructive" color role.
- Empty states: both list and detail have distinct, spec-matching empty state copy and are visually centered/de-emphasized appropriately (`py-12 text-center` on detail, `p-6` with muted text on list).
- Access denied state: handled via shared `AccessDeniedState` component with spec-exact copy, correctly gated on `permissions.can.view("pareceres")` before rendering any data-fetching content — good defense against a flash-of-unauthorized-content.
- Deduction: `AnexoLink`'s silent-catch pattern (see Priority Fix #3) — no in-component error surfacing beyond an assumed global toast, and no `download.isError` handling at all despite the mutation exposing that state. This is the one interaction on either page with a real failure mode (network call to a presigned-URL endpoint) that has no defensive UI treatment, in an otherwise consistently defensive pair of pages.
- Read-only scope correctly respected: no stray CTA buttons or mutating affordances found anywhere in either file, consistent with CONTEXT.md's explicit phase boundary (create/version/entrega are Phases 66-68).

---

## Registry Safety

Not applicable — `components.json` does not exist in `web/` (confirmed absent per UI-SPEC.md's own Design System section); no shadcn CLI/registry is in use. Registry audit skipped entirely, matching the UI-SPEC's own "not applicable" declaration.

---

## Files Audited

- `web/src/app/(dashboard)/pareceres/page.tsx`
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
- `web/src/hooks/use-pareceres.ts` (referenced for query/mutation shape verification)
- `web/src/components/ui/badge.tsx` (referenced for variant/color verification)
- `web/src/components/shared/dashboard-shell.tsx` (referenced for nav item verification)
- `web/src/app/(dashboard)/processos/page.tsx` (referenced for `rounded-none` convention cross-check)
</content>
