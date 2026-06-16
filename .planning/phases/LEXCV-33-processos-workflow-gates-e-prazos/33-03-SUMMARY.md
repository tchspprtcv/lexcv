---
plan: 33-03
phase: 33-processos-workflow-gates-e-prazos
status: complete
completed: 2026-06-16
tasks_total: 3
tasks_done: 3
key-files:
  created:
    - web/src/components/ui/dialog.tsx
    - web/src/components/ui/textarea.tsx
  modified:
    - web/src/app/(dashboard)/processos/[id]/page.tsx
    - web/src/app/(dashboard)/processos/page.tsx
    - web/package.json
    - web/pnpm-lock.yaml
requirements: [PRC-27, AGD-22]
---

# Plan 33-03 Summary — Frontend UI: Workflow card, Prazos, Dialogs, listing signals

## What was built

**Task 1 — shadcn Dialog + Textarea install** (commit `593e8f4`)
- Ran `pnpm dlx shadcn@latest add dialog textarea` in `web/`.
- Added `src/components/ui/dialog.tsx` and `src/components/ui/textarea.tsx` (official shadcn components, per UI-SPEC registry requirement).

**Task 2 — Workflow + Prazos on processo detail** (commit `3c04ea9`, `[id]/page.tsx` +465)
- **Workflow card**: shows current `estado` badge, `responsavel`, and próximo passo; renders transition actions from `useWorkflow(id).transicoesDisponiveis` (no hardcoded transition list — backend is the source of truth).
- Normal transitions (edit) vs critical transitions (manage) are visually distinguished and permission-gated via `hasScopedPermission`/`usePermissions`.
- Critical/justification-required transitions (suspender/encerrar/reabrir) open a **Dialog** with a justificativa `Textarea` (RHF + `transicaoJustificativaFormSchema`, min 10) → `useExecutarTransicao`.
- **Prazos card**: `usePrazos(id)` list ordered by data_limite with risk badges via `prazosRiscoToVariant`/`prazosRiscoToLabel` (single source of truth — `lib/prazos.ts`, not re-implemented); "Novo Prazo" Dialog → `useCreatePrazo`; concluído toggle → `useTogglePrazoConcluido` (backend returns recomputed `risco` so the badge stays correct after toggle).

**Task 3 — Listing signals** (commit `4176829`, `processos/page.tsx` +20/-1)
- Listing now shows `Resp.: {responsavel_nome}` and a prazo risco signal (`PRAZO PRÓXIMO`/`PRAZO VENCIDO`) rendered only when `risco_mais_critico` is `proximo`/`vencido`, plus an escalonado indicator (`tem_prazo_escalonado`).

## Verification

- `pnpm exec tsc --noEmit` — clean (exit 0)
- `pnpm lint` — 0 errors (6 pre-existing `<img>` warnings, unrelated to this plan)
- Detail page consumes all 5 Wave-2 hooks and the `lib/prazos.ts` helper; renders `transicoesDisponiveis` from the backend.

## Human verification needed (deferred UAT — checkpoint:human-verify)

The plan's final task is a visual checkpoint requiring a running app (backend + PostgreSQL + seeded users), unavailable in this autonomous session. Deferred items to test live:
1. Workflow card shows correct estado/responsável/próximo passo and only the allowed transitions per estado.
2. Critical transition Dialog enforces justificativa (min 10) and the action is blocked server-side without `processos:manage`.
3. Illegal transition attempts are rejected (409) server-side.
4. Prazos list shows correct risk badges (ok/proximo/vencido); toggling concluído keeps the risco badge correct; "Novo Prazo" creates and refreshes.
5. Listing shows responsável + prazo risco/escalonado signals consistently with the detail.

## Notes / deviations

- This SUMMARY was written by the orchestrator after the executor agent's transport connection dropped post-implementation (all three task commits had already landed cleanly and scoped only to processos pages + the two new shadcn components). No code was re-run or modified during SUMMARY reconstruction; tsc/lint were re-verified green.
- Pre-existing uncommitted changes in the `web` repo (LEXCV-17 permission-gating on dashboard/agenda/documentos/financeiro/settings and `api-backup` deletions) were intentionally left untouched — they are out of scope for this phase.

## Self-Check: PASSED
