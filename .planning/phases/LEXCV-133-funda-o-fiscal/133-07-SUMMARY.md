---
phase: 133-funda-o-fiscal
plan: 07
subsystem: web-fiscal
tags: [fiscal, efatura, frontend, settings, react-hook-form, zod]
requires:
  - "133-06: use-faturacao hooks, faturacao types, configuracaoFiscalSchema, ApiError/isApiError"
provides:
  - "Definições > Faturação tab (TabId 'faturacao', Receipt icon) gated by can.manage(\"financeiro\")"
  - "FaturacaoTab shell: guard (A carregar... / AccessDeniedState), header + status badge, simulation notice, holds useConfiguracaoFiscal result"
  - "FaturacaoDadosForm({ configuracao, onAlteracoesPorGravarChange? }) — Block 3"
  - "FaturacaoSeriesCard({ habilitado }) — Block 6, read-only"
affects: [133-08]
tech-stack:
  added: []
  patterns:
    - "useWatch instead of form.watch so the React Compiler doesn't flag the component (no incompatible-library lint warning)"
    - "Controlled motivo select (value from useWatch) so the DOM shows the saved code once the async options arrive"
    - "Locked NIF rendered as an unregistered disabled input; the form value stays at the loaded NIF from defaultValues"
key-files:
  created:
    - web/src/app/(dashboard)/settings/faturacao-tab.tsx
    - web/src/app/(dashboard)/settings/faturacao-dados-form.tsx
    - web/src/app/(dashboard)/settings/faturacao-series-card.tsx
  modified:
    - web/src/app/(dashboard)/settings/page.tsx
decisions:
  - "Form resets to the saved response right away on success, and again whenever the config query data changes. Structural sharing keeps the same reference on identical refetches, so in-progress edits survive a window-focus refetch."
  - "On save error the inline card message appears only for 400/409/422, the statuses that 133-06 suppresses from the auto toast. Other statuses are left to apiFetch's toast so the user is not toasted twice."
  - "NIF_BLOQUEADO is mapped to the NIF field with the UI-SPEC locked copy. Any other `campo` or `camposValidacao` key is mapped only if it is a known form field."
metrics:
  duration: 12 min
  completed: 2026-10-04
  tasks: 2
  files: 4
---

# Phase 133 Plan 07: Faturação tab, fiscal data form and read-only series Summary

Users with `financeiro:manage` now see a "Faturação" tab right after "Auditoria" in Definições. It shows, in order: the header with an "ativa" / "desligada" badge, the simulation notice (always shown), the "Dados fiscais do escritório" form, and the read-only "Séries de numeração" table. The form validates with `configuracaoFiscalSchema` and puts server field errors on the matching fields. Once the first document exists, the NIF field is locked.

## Tasks

| # | Task | Commit |
|---|------|--------|
| 1 | Tab shell + series card + page.tsx wiring | 51330a4 |
| 2 | Fiscal data form (Block 3) with server error mapping and NIF lock | 099694b |

## Verification

- `pnpm exec tsc --noEmit`: clean.
- `pnpm lint`: 0 errors. The 20 warnings were already there; none are in the new files.
- `pnpm exec vitest run`: 86/86 pass.
- `pnpm build` (with `NEXT_PUBLIC_API_BASE_PATH` and `BACKEND_API_ORIGIN` set): succeeds, and `/settings` prerenders as static.
- Acceptance greps all pass:
  - `"faturacao"` appears in `TabId`, and `activeTab === "faturacao" && hasFinanceiroManage` and `can.manage("financeiro")` are both in page.tsx.
  - The simulation notice and "Sem séries de numeração" strings are present.
  - The series card has 0 matches for mutation hooks, `<Button` or `<Input`.
  - In the form: `zodResolver(configuracaoFiscalSchema)` and `mode: "onTouched"` are present, `aria-describedby` appears 10 times (at least 7 required), `useMotivosIsencao` is used, "Tributo Especial Unificado" does not appear, and the NIF-locked, CTA and success strings plus `nifBloqueado` are all present.
  - No `dangerouslySetInnerHTML` in the new files.

## Deviations from Plan

**1. [Rule 1 - Bug] Controlled motivo select.** The motivo options load asynchronously. With an uncontrolled registered `<select>`, a saved motivo code would not be selected in the DOM if the options arrived after the select mounted. The select now takes its `value` from `useWatch`, while keeping the `register` `onChange`/`onBlur`/`ref`.

**2. [Rule 3 - Lint hygiene] `useWatch` instead of `form.watch`.** `form.watch` triggers the React Compiler `incompatible-library` lint warning. Switching to `useWatch` kept the new files at zero warnings.

**3. Additive a11y.** The simulation notice has `role="note"`, the inline save error has `role="alert"`, and the skeleton containers have `aria-label="A carregar..."`. The UI-SPEC copy is unchanged.

## Threat Mitigations

- T-133-29: the tab button, the panel and the queries (`enabled = podeGerir`) are all gated by `can.manage("financeiro")`. If the tab is reached without the permission, `AccessDeniedState` is shown. The backend `@PreAuthorize` remains the authority.
- T-133-30 (accepted): the locked NIF input is cosmetic; the backend enforces `NIF_BLOQUEADO`. The UI also maps that error onto the NIF field.
- T-133-31: all data is rendered as React text; there is no `dangerouslySetInnerHTML`.

## Notes for Plan 08

- `FaturacaoTab` already holds `configuracao` (the `useConfiguracaoFiscal` result). To add the unsaved-changes state, wire it through `FaturacaoDadosForm`'s optional `onAlteracoesPorGravarChange` prop.
- Insert the activation and email cards between `<FaturacaoDadosForm …/>` and `<FaturacaoSeriesCard …/>`.

## Known Stubs

None.

## Requirements

CFG-01, CFG-02 and CFG-05 are delivered on the UI side. They stay Pending in REQUIREMENTS.md until 133-05 (controller) and 133-08 (activation, email and end-to-end verification) close the phase.

## Self-Check: PASSED

- All 4 files exist on disk. Commits 51330a4 and 099694b are present in `git log`.
