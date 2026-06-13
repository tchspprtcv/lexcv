---
phase: 32-processos-intake-e-conflict-check
plan: "03"
subsystem: frontend
tags: [wizard, intake, conflict-check, badge, triagem, processos, ui]
dependency_graph:
  requires:
    - Phase 32 Plan 02 (hooks, types, schemas, conflict-check helper — all consumed here)
    - Phase 32 Plan 01 (backend endpoints: /intake, /conflict-check, /conflict-check/decisao, /formalizar)
  provides:
    - 3-step intake wizard at /processos/novo (Intake -> Conflict Check -> Abertura)
    - EM TRIAGEM badge (purple variant) and filter option in processos listing
    - Conflict Check section in processo detail page with inline Formalizar
  affects:
    - web/src/app/(dashboard)/processos/novo/page.tsx
    - web/src/app/(dashboard)/processos/page.tsx
    - web/src/app/(dashboard)/processos/[id]/page.tsx
tech_stack:
  added: []
  patterns:
    - Multi-step wizard with local useState<1|2|3> — no router navigation between steps
    - Permission-gated action buttons: canCreateProcessos for check trigger, canManageProcessos for decisao + formalizar
    - Disabled Formalizar with text-red-600 reason message (UI reflects backend enforcement)
    - Single source of truth: conflictNivelToVariant/conflictNivelToLabel from lib/conflict-check.ts shared by wizard and detail
    - Anti-Safe Harbor CSS: rounded-none, font-bold shadow-none, ring-blue-500 on all new elements
key_files:
  created: []
  modified:
    - web/src/app/(dashboard)/processos/novo/page.tsx
    - web/src/app/(dashboard)/processos/page.tsx
    - web/src/app/(dashboard)/processos/[id]/page.tsx
decisions:
  - "Wizard uses local step state (no router.push between steps) — all 3 steps live on /processos/novo as specified"
  - "estado field destructured and excluded from intake payload — backend forces TRIAGEM, UI never sends estado on intake"
  - "Conflict Check card in detail visible when estado===TRIAGEM OR decisao.data exists — both conditions per UI-SPEC"
  - "Formalizar disabled check: !decisao.data || nivel==='impeditivo' — mirrors backend enforcement without re-implementing business logic"
  - "Backend error (4xx) from formalizarProcesso.mutateAsync shown inline via formalizarError state — not relying on disable alone (T-32-08 mitigation)"
metrics:
  duration: "~35 minutes"
  completed_date: "2026-06-13"
  tasks_completed: 3
  files_created: 0
  files_modified: 3
---

# Phase 32 Plan 03: UI Surface — Wizard, Listing Badge, and Detail Conflict Section Summary

**One-liner:** 3-step intake wizard (Intake -> Conflict Check -> Abertura) at /processos/novo consuming all Plan 02 hooks, EM TRIAGEM purple badge + filter in listing, and Conflict Check detail card with inline formalizar — full UI surface for INT-01 and CFL-01.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Wizard de 3 passos em /processos/novo | `d454776` | web/src/app/(dashboard)/processos/novo/page.tsx |
| 2 | Badge EM TRIAGEM e filtro na listagem de processos | `3ba0cba` | web/src/app/(dashboard)/processos/page.tsx |
| 3 | Seccao de Conflict Check no detalhe do processo | `9857228` | web/src/app/(dashboard)/processos/[id]/page.tsx |

## What Was Built

### Task 1: 3-Step Intake Wizard

`/processos/novo` replaced with a single-route wizard using `useState<1|2|3>(1)`:

**Step indicator (inline divs):** 3 steps with 32px circles — active=blue-600, completed=emerald-600+Check icon, locked=border-slate-300. Connectors via `ChevronRight`.

**Step 1 (Dados de Intake):** RHF + `processoFormSchema` + `useCreateIntake`. Fields: Cliente select, Tipo de Processo select, Número, Área Jurídica, Tribunal, Data Início, Data Fim, Descrição (textarea). On submit: destructures `estado` out of payload — backend forces TRIAGEM. Saves `processoId` and `processoData`, advances to step 2. CTA "Continuar para Conflict Check". Permission gate: `canCreateProcessos`.

**Step 2 (Verificação de Conflitos):** Panel A — informational copy + "Executar Conflict Check" button (canCreateProcessos). Loading state: "A verificar conflitos...". Panel B (after check) — match rows with `conflictNivelToVariant/Label` badges + entity name + "Ver registo" link. Empty state: green SEM CONFLITO badge + copy from UI-SPEC line 177. Decision form (canManageProcessos): `conflictCheckDecisaoFormSchema` — Nível select, Justificativa textarea, Referência input; CTA "Registar Decisão". After decisao with nivel != impeditivo: "Continuar para Abertura" enabled. Impeditivo: amber callout + Continuar disabled. Not canManageProcessos: Registar Decisão disabled with message per UI-SPEC line 184.

**Step 3 (Revisão e Abertura):** Read-only `dl` grid with intake data + EM TRIAGEM badge + decisao data (nivel badge, data, referência, justificativa). CTA "Formalizar Processo" calls `useFormalizarProcesso`. Disabled + `text-red-600` reason when `!decisaoData || nivel==='impeditivo'`. Backend 4xx shown inline. On success: `router.push(/processos/{id})`.

**Copy strings exact per UI-SPEC:** "Continuar para Conflict Check", "Executar Conflict Check", "Registar Decisão", "Formalizar Processo", "Voltar", error messages for impeditivo and no-decision.

### Task 2: EM TRIAGEM Badge and Filter

`estadoVariant` switch extended: `TRIAGEM -> "purple"` (inserted between SUSPENSO and CONCLUIDO/ENCERRADO).  
`estadoLabel` computed: `estado === "TRIAGEM" ? "EM TRIAGEM" : p.estado ?? "—"`.  
`<Badge variant>` union extended with `"purple"` — no new variant needed (already in badge.tsx).  
Filter `<select>`: `<option value="TRIAGEM">Em triagem</option>` added before Ativo.

### Task 3: Conflict Check Section in Detail

New imports: `Badge`, `useConflictCheckDecisao`, `useFormalizarProcesso`, `conflictNivelToLabel`, `conflictNivelToVariant`.  
`canManageProcessos` calculated at page level, passed to `ProcessoDetailContent`.  
`useConflictCheckDecisao(id)` and `useFormalizarProcesso(id)` called in content component.

Card inserted between Dados and tab panel — visibility: `estado === "TRIAGEM" || decisao.data`:
- CardHeader: "Conflict Check" title + nivel badge (right-aligned) when decisao present
- CardContent dl grid: decisor, data, referência, justificativa block
- Empty state: "O conflict check ainda não foi executado para este processo."
- Inline Formalizar: only when `estado === "TRIAGEM" && canManageProcessos`; disabled+reason when `!decisao.data || nivel==='impeditivo'`; 4xx error shown via `formalizarError` state

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None. All components wire live data from the hooks established in Plan 02. The wizard stores `processoData` and `decisaoData` in local state derived from actual API responses.

## Threat Flags

No new threat surfaces introduced. All three threat mitigations from `<threat_model>` confirmed:
- **T-32-08** (Formalizar tampering): Backend error (4xx) from `formalizarProcesso.mutateAsync()` always shown inline — never assuming success from UI state alone.
- **T-32-09** (Elevation of Privilege): "Registar Decisão" and "Formalizar Processo" gated by `canManageProcessos` (mirrors `processos:manage` @PreAuthorize); "Executar Conflict Check" gated by `canCreateProcessos`.
- **T-32-SC** (Supply chain): No new packages installed — only lucide-react icons already present.

## Self-Check: PASSED

Files verified:
- FOUND: web/src/app/(dashboard)/processos/novo/page.tsx (replaced with wizard)
- FOUND: web/src/app/(dashboard)/processos/page.tsx (modified — TRIAGEM badge + filter)
- FOUND: web/src/app/(dashboard)/processos/[id]/page.tsx (modified — Conflict Check section)

Commits verified (web submodule):
- d454776: feat(32-03): add 3-step intake wizard to /processos/novo
- 3ba0cba: feat(32-03): add EM TRIAGEM badge (purple) and filter to processos listing
- 9857228: feat(32-03): add Conflict Check section to processo detail page

Build: `pnpm exec tsc --noEmit` -> no errors (TSC_PASS)

Acceptance criteria verified:
- Wizard uses 4 action hooks (useCreateIntake, useRunConflictCheck, useRegistarDecisaoConflito, useFormalizarProcesso) and conflictNivelToLabel — grep 11 (>= 5)
- estado NOT sent in Step 1 payload — destructured as `_estado`
- "Formalizar Processo" has `opacity-50 cursor-not-allowed` + `text-red-600` reason when blocked
- All 4 copy strings ("Continuar para Conflict Check", "Executar Conflict Check", "Registar Decisão", "Formalizar Processo") present in wizard
- conflictCheckDecisaoFormSchema used in Step 2 decisao form
- page.tsx: "TRIAGEM", "EM TRIAGEM", "purple" all present — grep 5 (>= 3)
- [id]/page.tsx: useConflictCheckDecisao + Conflict Check + conflictNivelToLabel + useFormalizarProcesso — grep 9 (>= 3)
- Empty state "O conflict check ainda não foi executado..." present
- Formalizar inline disabled+reason text-red-600 present
