---
phase: 48-recorr-ncia-de-eventos
verified: 2026-06-18T00:00:00Z
status: passed
score: 4/4 must-haves verified
gaps:
  - truth: "pnpm lint passes with no errors"
    status: failed
    reason: "5 lint errors in agenda/page.tsx introduced by Phase 48 (4x @typescript-eslint/no-explicit-any, 1x React Compiler impure call)"
    artifacts:
      - path: "web/src/app/(dashboard)/agenda/page.tsx"
        issue: "Lines 69, 88, 130, 131 use `e as any` casts; line 306 uses `getCategoria(e as any)` — all trigger no-explicit-any errors. Line 69 also triggers react-hooks/incompatible-library (impure call in render)."
    missing:
      - "Replace `as any` casts with a proper union type or typed helper that accepts both Evento and the unified event shape"
---

# Phase 48: Recorrência de Eventos — Verification Report

**Phase Goal:** O utilizador pode criar eventos com regra de recorrência (diária, semanal ou mensal) e o calendário exibe todas as instâncias geradas; ao apagar, o utilizador escolhe entre apagar esta instância ou toda a série.

**Verified:** 2026-06-18
**Status:** GAPS FOUND (1 blocker — lint errors in modified file)

---

## Checks

| # | Criterion | Status | Evidence |
|---|-----------|--------|---------|
| 1 | Formulário tem secção Recorrência (Nenhuma/Diária/Semanal/Mensal) + campo fim obrigatório quando selecionado | PASS | `agenda/novo/page.tsx` lines 211-235: `<select id="recurrenceRule">` with four options; conditional `recurrenceEndDate` input rendered when `recurrenceRule !== "NONE"`. Schema `superRefine` in `schemas/eventos.ts` lines 37-44 enforces the field as required. |
| 2 | Backend armazena regra + GET /eventos expande instâncias | PASS | `Evento.java` has `recurrenceRule`, `recurrenceEndDate`, `recurrenceExceptions` columns + `@Transient isRecurrenceInstance`. `ResourceController.java` lines 1532-1600 expand recurring events in `listEventos`; `DELETE /eventos/{id}/instances` endpoint present at line 1689. `mvn -DskipTests compile` exits 0. |
| 3 | Calendário mostra instâncias com indicador visual ↻ | PASS | `agenda/page.tsx` line 319: `{!e.isPrazo && (e as Evento).isRecurrenceInstance ? <span className="mr-1" title="Evento recorrente">&#x21BB;</span> : null}` |
| 4 | Ao apagar evento recorrente surge diálogo com "Apagar esta instância" / "Apagar toda a série" | PASS | `agenda/[id]/page.tsx` lines 164-199: `AlertDialog` branches on `evento.data.recurrenceRule`; renders two buttons ("Apagar esta instância" / "Apagar toda a série") when rule is set. Hooks `useDeleteEvento` and `useDeleteEventoInstance` both exported from `use-eventos.ts`. |

---

## Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|---------|
| AGE-03 | Form with recurrence rule (Nenhuma/Diária/Semanal/Mensal) + required end date | PASS | See check #1 above |
| AGE-04 | Backend stores rule + GET /eventos expands instances | PASS | See check #2 above |
| AGE-05 | Calendar shows instances with visual indicator | PASS | See check #3 above |
| AGE-06 | Delete dialog with "this instance" vs "whole series" | PASS | See check #4 above |

---

## Compile / Lint

| Tool | Result | Detail |
|------|--------|--------|
| `mvn -DskipTests compile` | PASS | Exits 0, no output |
| `pnpm lint` | FAIL | 6 errors total; 5 in `agenda/page.tsx` (4x `@typescript-eslint/no-explicit-any` at lines 69, 88, 130, 131, 306; 1x `react-hooks/incompatible-library` at line 69). These are in a file modified by Phase 48. |

Note: the remaining lint error (`processos/page.tsx`) and warnings (`no-img-element`, `react-hooks/incompatible-library` in other files) are pre-existing and unrelated to Phase 48.

---

## Gaps Summary

All four functional success criteria are implemented and wired end-to-end. The single blocker is lint: `agenda/page.tsx` uses `as any` casts to reconcile the unified event type (Evento + prazo shape) with the `getCategoria` helper. This introduces 5 lint errors that cause `pnpm lint` to exit 1.

**Fix:** Introduce a typed union or narrow the helper signature so the `as any` casts are not needed. For example:

```typescript
type UnifiedEvent = (Evento & { isPrazo: false }) | PrazoEvent;
function getCategoria(e: UnifiedEvent | { titulo: string; tipo?: string }) { ... }
```

Once lint passes the phase goal is fully achieved.

---

## Verdict

**FAILED** — functional goal is achieved but `pnpm lint` fails with errors in a Phase 48 file (`agenda/page.tsx`). Fix the `as any` casts before marking the phase complete.

---

_Verified: 2026-06-18_
_Verifier: Claude (gsd-verifier)_
