# Phase 101 — UI Review

**Post-audit fix applied (2026-07-15):** Priority Fix #1 (webpage/'s `button.tsx` left on scoped `@radix-ui/react-slot` while `web/` was migrated to unified `radix-ui`) was fixed immediately after this audit — `webpage/src/components/ui/button.tsx` now imports `Slot` from `radix-ui`, `@radix-ui/react-slot` was removed from `webpage/package.json`, `radix-ui` added, and `pnpm build` re-verified clean. Commit `1b39a75`. Priority Fixes #2 (untranslated `CommandDialog` default copy) and #3 (dead `cn-toast` class, `shadcn` CLI dependency placement) remain open — both are dormant (zero page consumers yet) and non-blocking; left for a future pass or Phase 102.

**Audited:** 2026-07-15
**Baseline:** 101-UI-SPEC.md (design contract, status: draft — Checker Sign-Off section still unchecked/"pending")
**Screenshots:** not captured (no dev server detected on :3000, :3001, :5173 — code-only audit)

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Toast contract ("Sucesso"/"Erro") preserved correctly, but `CommandDialog`'s CLI-default title/description ship in untranslated English inside a Portuguese-domain app |
| 2. Visuals | 3/4 | `web/` fully unified on `radix-ui`, but `webpage/`'s mirrored `button.tsx` was left on the scoped `@radix-ui/react-slot` package — the two apps' design systems have already forked on the exact axis FND-05/Pitfall-5 exists to prevent |
| 3. Color | 3/4 | Token merge itself is byte-for-byte correct in both apps (verified directly, not just trusted the SUMMARY), but 3 files ship a magic hex literal (`dark:bg-[#020617]`) that silently duplicates the new `--background` dark token instead of referencing it |
| 4. Typography | 4/4 | Not exercised this phase (contract-declared); new primitives use a contained 3-size/3-weight set, no new violation introduced |
| 5. Spacing | 4/4 | Not exercised this phase (contract-declared); arbitrary-value classes present are unmodified vendor CLI boilerplate for sub-scale pixel corrections, not hand-authored spacing decisions |
| 6. Experience Design | 3/4 | Toast/loading/empty-state primitives correctly in place, but shipped with dead configuration (`cn-toast` no-op class, `shadcn` CLI mis-scoped into production `dependencies`) |

**Overall: 20/24**

---

## Top 3 Priority Fixes

1. **`webpage/` never ran `migrate radix` — its `button.tsx` still imports the scoped `@radix-ui/react-slot` package while `web/`'s equivalent file imports the unified `radix-ui` package** (`webpage/src/components/ui/button.tsx:2` vs `web/src/components/ui/button.tsx:2`) — user impact: none today (no page renders differently), but this is the exact "two apps' design system forking silently" failure mode PITFALLS.md Pitfall 5 and FND-05's own text ("componentes existentes e novos usem o mesmo pacote radix-ui, sem estado de ponte dual" — no app carve-out in that sentence) were written to prevent, and it will compound every time either app adds another Radix-backed primitive independently — concrete fix: run `pnpm dlx shadcn@latest migrate radix -y` inside `webpage/` (or hand-edit the one import line), then `grep -r "@radix-ui/react-slot" webpage/src` to confirm zero remaining imports before pruning it from `webpage/package.json`.
2. **`CommandDialog`'s default copy ships in English inside a Portuguese-only domain app** (`web/src/components/ui/command.tsx:37-38`: `title = "Command Palette"`, `description = "Search for a command to run..."`) — user impact: dormant today (no page consumes `Command` yet per 101-03-SUMMARY.md), but the CLI-generated defaults will render verbatim in English the instant a module phase wires the command palette in, unless someone remembers to override them — every other copy surface this phase touches (`toast.success`→"Sucesso", `toast.error`→"Erro", `dialog.tsx`/`sheet.tsx`'s `sr-only` "Fechar") is correctly localized, making this the one contract gap — concrete fix: change the defaults to `"Paleta de Comandos"` / `"Pesquisar um comando para executar..."`, or drop the defaults entirely and make both props required so no consumer can ship the English fallback by omission.
3. **Two pieces of dead/misplaced configuration shipped un-caught**: (a) `web/src/components/ui/sonner.tsx:42` sets `toastOptions.classNames.toast: "cn-toast"`, a class that matches zero rules anywhere in `web/src/app/globals.css` or any other stylesheet (confirmed via repo-wide search) — it is a pure no-op; (b) `web/package.json:27` lists `"shadcn": "^4.13.0"` under `dependencies` rather than `devDependencies`, next to its correctly-placed build-tool siblings `tailwindcss`/`@tailwindcss/postcss` in `devDependencies` — a CLI tool that never runs at request time is now installed in every production deploy — user impact: none functionally breaking, but both are exactly the kind of "looks configured/done but isn't" drift that compounds silently across phases — concrete fix: remove the dead `classNames.toast` override (or add the real `.cn-toast` rule if styling was actually intended there), and move `"shadcn"` to `devDependencies`.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

**What's correct:** The one load-bearing copy contract this phase owns — `toast.success(message)` → title **"Sucesso"**, `toast.error(message)` → title **"Erro"** — survives the Sonner swap verbatim. Verified directly in `web/src/hooks/use-toast.ts:25-29`:
```
toast.success = (message, options) => sonnerToast.success("Sucesso", { description: message, ...options })
toast.error = (message, options) => sonnerToast.error("Erro", { description: message, ...options })
```
Pre-existing `sr-only` "Fechar" close-button labels in `dialog.tsx:49` and `sheet.tsx:67` (untouched, correctly Portuguese) are also intact.

**Finding (WARNING):** `web/src/components/ui/command.tsx:37-38` — `CommandDialog`'s default props (`title = "Command Palette"`, `description = "Search for a command to run..."`) are the raw, untranslated shadcn CLI boilerplate strings. `grep` confirms no other new primitive introduces hardcoded placeholder/empty-state English copy (`select.tsx`/`native-select.tsx` take no default text; consumers must supply it) — this is isolated to `command.tsx`. Not user-facing yet (no page imports `Command`), but it is a real, discoverable contract gap that a purely code-level audit is specifically positioned to catch before it ships silently in a later phase.

### Pillar 2: Visuals (3/4)

**What's correct:** All 16 new primitives spot-checked (`select.tsx`, `dropdown-menu.tsx`, `calendar.tsx`, `command.tsx`, `breadcrumb.tsx`, `empty.tsx`) carry `data-slot`, use the Radix Portal/Overlay/Content idiom consistently, and drive corners from semantic `rounded-*`/`--radius` tokens (zero hardcoded `rounded-none` in the new files — confirmed via grep). Icon-only interactive elements are correctly labeled: `breadcrumb.tsx:109` gives the ellipsis button `sr-only` "More" text with `aria-hidden`/`role="presentation"` on the decorative separator; calendar nav chevrons inherit `react-day-picker`'s built-in `aria-label`.

**Finding (WARNING):** `webpage/src/components/ui/button.tsx:2` imports `Slot` from the scoped `@radix-ui/react-slot` package; `web/src/components/ui/button.tsx` imports the same primitive from the unified `radix-ui` package (post `migrate radix`). `webpage/package.json:12` still lists `@radix-ui/react-slot` as a dependency — confirmed this is the only Radix package left in `webpage/`. This is a genuine, verifiable divergence between the two apps' component internals that the 101-05-SUMMARY.md's own reasoning ("FND-05 doesn't require ambas as apps") does not hold up against the requirement's actual text in `REQUIREMENTS.md:16`, which draws no such app-scope distinction.

**Finding (INFO, low severity):** `web/src/components/ui/breadcrumb.tsx:2` (`import { Slot } from "radix-ui"`, used as `Slot.Root`) and `web/src/components/ui/button.tsx:2` (`import { Slot as SlotPrimitive } from "radix-ui"`, used as `SlotPrimitive.Slot`) alias the same import two different ways within the same, now-unified app — functionally identical but an avoidable inconsistency from the same `migrate radix` pass (already logged as IN-04 in `101-REVIEW.md`).

**Finding (INFO, low severity):** `calendar.tsx:21-45` duplicates `button.tsx`'s `buttonVariants` CVA configuration byte-for-byte rather than importing it, because `button.tsx` doesn't export it and is out of this phase's edit scope (`button.tsx` is one of the 14 files reserved for Phase 102's reconciliation). Documented and bounded, but it is a second source of truth for the same visual variants today.

### Pillar 3: Color (3/4)

**What's correct:** Directly read (not just trusted the SUMMARY) `web/src/app/globals.css` and `webpage/src/app/globals.css` — both contain exactly one `:root` block (declared first) and one `.dark` block, with the exact institutional values the UI-SPEC requires: `--background:#f8fafc`/`--foreground:#020617` (light), `--background:#020617`/`--foreground:#f8fafc` (dark), `--radius:0rem`, `--primary:#2563eb` (light)/`#3b82f6` (dark) — byte-identical between the two apps. No corruption, no duplicate block, no drift.

**Finding (WARNING):** `web/src/components/ui/alert-dialog.tsx:39`, `dialog.tsx:41`, and `card.tsx:10` all contain the literal Tailwind arbitrary value `dark:bg-[#020617]` — a magic hex that happens to equal the new `--background` dark-mode token's value but does not reference it. This means these 3 files' dark-mode surface color and the semantic `--background` token are two disconnected sources of truth that currently agree by coincidence; retuning `--background` in a future phase would silently desync them with zero compile-time signal. This is explicitly UI-SPEC-sanctioned debt for *this* phase (UI-SPEC's own Color section says "Secondary (30%)... not touched by this phase's token merge... until a future module phase deliberately migrates them") and is already tracked as WR-02 in `101-REVIEW.md`/`101-REVIEW-FIX.md` (deliberately deferred to Phase 102, not silently missed) — noted here for the audit record, not scored as a phase-101 regression, but it is real code sitting in the repo today.

**Finding (INFO):** Accent-token usage (`bg-primary`/`text-primary`/`border-primary`) in the new primitives (`avatar.tsx:65`, `calendar.tsx:271`, `progress.tsx:24`, `checkbox.tsx:17`, `empty.tsx:76`) is appropriately scoped to component-internal selected/active states — consistent with the token's intended semantic role and does not conflict with UI-SPEC's closed accent-usage list (which governs the *existing*, still-hardcoded `dashboard-shell.tsx` surfaces, not new unwired primitives).

### Pillar 4: Typography (4/4)

Not exercised this phase per UI-SPEC's own scope declaration (no visible page changes). Verified the new primitive files stay within a contained typography footprint: only `text-xs`/`text-sm`/`text-lg` and `font-normal`/`font-medium`/`font-semibold` appear across all 16 new files (grep-verified) — no `font-bold` or additional size steps introduced. This does not touch or worsen the pre-existing `font-bold` ad hoc inconsistency UI-SPEC already flags as untouched debt in `dashboard/page.tsx`.

### Pillar 5: Spacing (4/4)

Not exercised this phase per UI-SPEC's own scope declaration. Arbitrary-bracket spacing values found in the new primitives (`calendar.tsx:133,142` `text-[0.8rem]`; `checkbox.tsx:17` `rounded-[4px]`; `tabs.tsx:66` `h-[calc(100%-1px)]`; `dropdown-menu.tsx:247` `min-w-[96px]`; `input-group.tsx:31,33` `mr-[-0.15rem]`) were checked against the official shadcn registry source and are unmodified vendor boilerplate — sub-token-scale pixel corrections inherent to these specific Radix components (e.g., a checkbox corner radius smaller than the smallest scale step, a tab-underline offset), not spacing decisions made by this phase's executor.

### Pillar 6: Experience Design (3/4)

**What's correct:** The full toast feedback loop survives the Radix→Sonner migration with zero call-site changes across ~26 files (verified the wrapper's public shape in `use-toast.ts` and the root mount in `layout.tsx:5,36`). `sonner.tsx`'s icon map (`success`/`info`/`warning`/`error`/`loading`, lines 15-31) is actually more complete than the deleted `toaster.tsx` it replaces. `skeleton.tsx` (loading) and `empty.tsx` (empty state, post WR-01 fix verified: `EmptyDescription` now correctly renders a `<p>`, `empty.tsx:71-82`) are both structurally correct and available for module phases to consume. Disabled-state classes (`disabled:opacity-50`/`disabled:pointer-events-none`) are present consistently across the new interactive primitives.

**Finding (WARNING):** `web/src/components/ui/sonner.tsx:40-44` configures `toastOptions.classNames.toast: "cn-toast"` — grep confirms `.cn-toast` matches no rule anywhere in the repo, making this a dead, no-op override that looks like intentional styling but does nothing.

**Finding (WARNING):** `web/package.json:27` — `"shadcn": "^4.13.0"` sits in `dependencies`, meaning the CLI scaffolding tool (never invoked at runtime) is installed into every production build alongside real runtime deps, rather than in `devDependencies` next to `tailwindcss`/`@tailwindcss/postcss`.

**Finding (INFO, documented deviation):** `command.tsx`'s `CommandDialog` always renders its close button (the hand-rolled `DialogContent` it composes against has no `showCloseButton` prop), a behavior difference from upstream's hide-by-default — low severity since `Command` has zero page consumers yet, but worth re-checking once Phase 102 reconciles `dialog.tsx`.

---

## Registry Safety

Registry audit: 0 third-party blocks checked — `101-UI-SPEC.md`'s Registry Safety table lists only "shadcn official" rows for every block/package this phase adds (`.planning/REQUIREMENTS.md` explicitly excludes third-party registries from the whole milestone); no `npx shadcn view --registry <url>` vetting gate applies, so the registry-audit trigger condition (third-party registry named in UI-SPEC) is not met. No flags.

---

## Files Audited

- `web/components.json`, `webpage/components.json`
- `web/src/app/globals.css`, `webpage/src/app/globals.css`
- `web/package.json`, `webpage/package.json`
- `web/src/app/layout.tsx`
- `web/src/hooks/use-toast.ts`
- `web/src/components/ui/sonner.tsx`
- `web/src/components/ui/calendar.tsx`
- `web/src/components/ui/command.tsx`
- `web/src/components/ui/empty.tsx`
- `web/src/components/ui/breadcrumb.tsx`
- `web/src/components/ui/select.tsx`, `native-select.tsx`, `dropdown-menu.tsx`, `tabs.tsx`, `checkbox.tsx`, `avatar.tsx`, `progress.tsx`, `input-group.tsx`, `tooltip.tsx` (grep-audited for structural conventions, color, typography, spacing)
- `webpage/src/components/ui/button.tsx`
- `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-01-SUMMARY.md` through `101-05-SUMMARY.md`
- `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-01-PLAN.md`, `101-02-PLAN.md`, `101-03-PLAN.md`, `101-05-PLAN.md`
- `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-UI-SPEC.md`
- `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-CONTEXT.md`
- `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-REVIEW.md`, `101-REVIEW-FIX.md` (cross-referenced, independently re-verified rather than trusted at face value)
- `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/deferred-items.md`
- `.planning/REQUIREMENTS.md` (FND-01 through FND-08 wording)
