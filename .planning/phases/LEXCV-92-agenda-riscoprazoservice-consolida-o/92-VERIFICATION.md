---
phase: 92-agenda-riscoprazoservice-consolida-o
verified: 2026-07-13T23:02:27Z
status: human_needed
score: 8/8 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Abrir /agenda autenticado, confirmar o número 'Urgentes' no cartão 'Visão Geral da Semana'"
    expected: "Urgentes reflete risco === proximo || risco === vencido do RiscoPrazoService (não prioridade === ALTA); um prazo ALTA distante já não conta, um prazo próximo de prioridade média agora conta"
    why_human: "Requer backend em execução com dados reais; startup local bloqueado pelo gap ambiental pré-existente MINIO_ENDPOINT (mesmo bloqueio já documentado nas Phases 87/89 em STATE.md), não um defeito de código desta fase"
  - test: "Verificar na aba Network do browser que não há chamadas a GET /eventos/upcoming ao carregar /agenda"
    expected: "Zero requisições de rede para /eventos/upcoming"
    why_human: "Mesma dependência de backend/browser ao vivo do item acima"
---

# Phase 92: Agenda ↔ RiscoPrazoService — Consolidação Verification Report

**Phase Goal:** A página de Agenda deixa de calcular o seu próprio veredito de "prazo crítico" e passa a confiar inteiramente no `RiscoPrazoService` já usado pelo resto do sistema, tanto para Prazos como para Eventos.
**Verified:** 2026-07-13T23:02:27Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | (Roadmap SC1/AGD-34) `agenda/page.tsx` usa o campo `risco` do backend para Prazos em vez de recalcular veredito próprio | ✓ VERIFIED | `web/src/app/(dashboard)/agenda/page.tsx:106` `risco: p.risco,` in `pzs` mapping (previously discarded); `page.tsx:167` `weekStats.urgentes` reads `e.risco`, zero `prioridade === "ALTA"` left in file (grep confirmed) |
| 2 | (Roadmap SC2/AGD-35) `GET /eventos` devolve `risco` por evento, calculado no backend via `RiscoPrazoService.computeRiscoEvento` | ✓ VERIFIED | `Evento.java:61-62` `@Transient private String risco;`; `ResourceController.java:2478-2480` loops `expanded` and calls `e.setRisco(riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade()))` immediately before `return ResponseEntity.ok(expanded)` |
| 3 | Instâncias recorrentes expandidas recebem risco calculado a partir da sua própria data de ocorrência (não do master) | ✓ VERIFIED | The `setRisco` loop (line 2478) runs AFTER the recurrence-expansion loop (lines ~2410-2476) that builds each `instance` with its own `dataInicio(cursor)` — risk is computed per-instance post-expansion, not copied from master |
| 4 | `GET /eventos/upcoming` (endpoint órfão) deixa de existir no backend | ✓ VERIFIED | `getUpcomingEventos`/`"/eventos/upcoming"` absent from `ResourceController.java` (grep: 0 matches); commit `7ca5b2c` shows clean 27-line deletion, no orphaned helper types left behind |
| 5 | (Roadmap SC3) Prazos e Eventos na Agenda concordam com Dashboard/job diário sobre "próximo"/"vencido", para os mesmos dados | ✓ VERIFIED | All consumers (`listEventos`, `isEventoCritico` dashboard KPI, `listPrazos`) call the same `RiscoPrazoService.computeRisco`/`computeRiscoEvento`; no second/local threshold table exists anywhere. Note: a pre-existing `dataInicio` (Agenda) vs `dataFim` (dashboard `isEventoCritico`) argument divergence for `computeRiscoEvento` remains — explicitly locked as out-of-scope by `92-CONTEXT.md` and deferred to Phase 97 audit; not introduced by this phase |
| 6 | (Roadmap SC4) Nenhuma 5ª implementação divergente de "prazo crítico" resta no frontend | ✓ VERIFIED | `grep -rn 'prioridade === "ALTA"'` in `web/src` → 0 matches; `grep -rn "prazoCritico\|isCritico\|isUrgente\|diasRestantes\|calcularRisco\|computeRisco"` in `web/src` → 0 matches; only `web/src/lib/prazos.ts` helpers exist, and they are display-mapping (`prazosRiscoToVariant`/`Label`), not risk computation |
| 7 | O hook morto `useUpcomingEventos` e o tipo `UpcomingEvento` são removidos | ✓ VERIFIED | `useUpcomingEventos`/`UpcomingEvento`/`eventos/upcoming` absent from `web/src` (grep: 0 matches); `use-eventos.ts` and `types/eventos.ts` read confirm clean removal, 4 orphaned `["eventos","upcoming"]` invalidations also removed |
| 8 | Código compila/typechecks/builda/lint limpo nos ficheiros tocados (nenhuma regressão introduzida) | ✓ VERIFIED | `cd backend && mvn -q -DskipTests compile` → success; `cd web && npx tsc --noEmit` → 3 pre-existing unrelated errors (missing `vitest` types in 3 test files untouched by this phase, present since commits `f07fe89`/`f825b2e`/`360cdd3`, before Phase 92); `cd web && pnpm build` → succeeds, all 24 routes incl. `/agenda` compile; `npx eslint` on the 3 touched files → "No issues found" |

**Score:** 8/8 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/Evento.java` | `@Transient private String risco` field | ✓ VERIFIED | Line 61-62, alongside existing `isRecurrenceInstance`/`recurrenceInstanceDate` transient fields; class-level Lombok `@Getter @Setter` generates accessors; Jackson serializes, JPA ignores (no schema/migration impact) |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | `listEventos` enriched with per-event risco; `getUpcomingEventos` removed | ✓ VERIFIED | `setRisco(riscoPrazoService.computeRiscoEvento(...))` at line 2479; `getUpcomingEventos` and its `@GetMapping("/eventos/upcoming")` fully absent |
| `web/src/types/eventos.ts` | `risco?: PrazoRisco` on `Evento`; `UpcomingEvento` removed | ✓ VERIFIED | Line 21 `risco?: PrazoRisco;`, line 1 imports `PrazoRisco`; no `UpcomingEvento` interface present |
| `web/src/hooks/use-eventos.ts` | `useUpcomingEventos` and orphaned `["eventos","upcoming"]` invalidations removed | ✓ VERIFIED | File read in full — no `useUpcomingEventos`, no `UpcomingEvento` import, no `["eventos","upcoming"]` invalidation calls; `useEventos`/mutations intact |
| `web/src/app/(dashboard)/agenda/page.tsx` | `allUnifiedEvents` transports `risco`; `weekStats.urgentes` uses backend risco | ✓ VERIFIED | Line 106 `risco: p.risco,`; line 167 `const urgentes = active.filter((e) => e.risco === "proximo" \|\| e.risco === "vencido").length;` |

Cross-checked via `gsd-sdk query verify.artifacts` for both plans — `all_passed: true` (2/2 for 92-01, 3/3 for 92-02, zero issues).

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ResourceController.listEventos` | `riscoPrazoService.computeRiscoEvento` | `setRisco` per expanded event, using `dataInicio` + `prioridade` | ✓ VERIFIED (manual) | `gsd-sdk query verify.key-links` returned "Source file not found" because the `from` value is a method name, not a file path (tool limitation) — manually confirmed via `Read`/`grep`: line 2479 `e.setRisco(riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade()));` inside the `for (Evento e : expanded)` loop directly preceding `return ResponseEntity.ok(expanded);` |
| `agenda/page.tsx weekStats.urgentes` | campo `risco` do backend (`GET /eventos` e `GET /prazos`) | filtro `risco === "proximo" \|\| risco === "vencido"` | ✓ VERIFIED (manual) | Same tool limitation as above (non-file `from`) — manually confirmed at `page.tsx:167` |
| `allUnifiedEvents` (mapeamento `pzs`) | `Prazo.risco` | cópia explícita `risco: p.risco` | ✓ VERIFIED (manual) | Manually confirmed at `page.tsx:106` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `agenda/page.tsx` `weekStats.urgentes` | `e.risco` on unified events | `GET /eventos` (`listEventos`, backend-computed via `RiscoPrazoService.computeRiscoEvento`) and `GET /prazos` (`p.risco`, pre-existing backend field) | Yes — `RiscoPrazoService.computeRisco` performs a real `ChronoUnit.DAYS.between` calculation against `LocalDate.now()`, not a static/hardcoded value | ✓ FLOWING |
| `Evento.risco` (backend) | per-event `risco` string | `riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade())`, called after tenant-scoped `eventoRepository` fetch + recurrence expansion | Yes — real computation over each instance's own `dataInicio` | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles with both modified files | `cd backend && mvn -q -DskipTests compile` | Exit 0, no output | ✓ PASS |
| Frontend typechecks (project baseline, not phase-introduced) | `cd web && npx tsc --noEmit` | 3 pre-existing errors, all in untouched `*.test.ts` files (missing `vitest` module, present before Phase 92 per `git log`) | ✓ PASS (no new errors) |
| Frontend builds for production | `cd web && pnpm build` | Succeeds; `/agenda` and 23 other routes compile/generate | ✓ PASS |
| Lint on the 3 touched files | `cd web && npx eslint src/types/eventos.ts src/hooks/use-eventos.ts "src/app/(dashboard)/agenda/page.tsx"` | "No issues found" | ✓ PASS |
| No residual client-side risk computation | `grep -rn 'prioridade === "ALTA"' web/src` and dead-code grep (`useUpcomingEventos`, `UpcomingEvento`, `eventos/upcoming`) | 0 matches for all | ✓ PASS |
| Commits referenced in SUMMARYs exist and match claimed diffs | `git show --stat 19ce082 / 7ca5b2c / d80054d / d513438` | All 4 commits exist, diffs match SUMMARY claims exactly | ✓ PASS |

Live browser/network verification of the "Urgentes" counter and confirmation of zero `/eventos/upcoming` network calls could not be executed in this sandbox — see Human Verification section. Per explicit task context, this is the same pre-existing `MINIO_ENDPOINT` environmental blocker documented in `STATE.md` (already blocking Phases 87/89 the same way), not a defect introduced by this phase. A live docker-compose stack is running on this machine, but the orchestrator deliberately avoided pointing the freshly-built backend at it (dev profile uses `ddl-auto: update` against what appears to be a persistent, possibly shared database) — correctly treated as too risky for ad-hoc verification rather than skipped for convenience.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AGD-34 | 92-02 | Prazos na Agenda usam o campo `risco` já calculado pelo backend em vez de recomputar veredito próprio no cliente | ✓ SATISFIED | `page.tsx:106` (`risco: p.risco`), `page.tsx:167` (`urgentes` derived from `risco`), zero `prioridade === "ALTA"` remaining |
| AGD-35 | 92-01, 92-02 | Eventos na Agenda refletem o mesmo veredito de risco que o resto do sistema (`GET /eventos` passa a devolver `risco`) | ✓ SATISFIED | `Evento.java` `@Transient risco` field + `ResourceController.listEventos` sets it via shared `RiscoPrazoService`; frontend `Evento.risco?: PrazoRisco` type added and consumed via existing `{...e}` spread |

No orphaned requirements: `REQUIREMENTS.md` traceability table maps only AGD-34 and AGD-35 to Phase 92, and both are declared in `92-01-PLAN.md`/`92-02-PLAN.md` frontmatter `requirements:` fields. (Note: `REQUIREMENTS.md`'s checkboxes and traceability-table status column still show unchecked/"Pending" for AGD-34/AGD-35 — this is a documentation-sync item normally closed by the orchestrator at phase/milestone bookkeeping, not a code-level gap; flagged as informational only, does not affect this phase's goal achievement.)

### Anti-Patterns Found

None. No `TODO`/`FIXME`/`XXX`/`TBD`/`HACK`/`PLACEHOLDER` markers, no empty/stub handlers, no hardcoded empty risk values, and no dead-code fallbacks in any of the 5 files modified across both plans.

### Human Verification Required

### 1. Agenda "Urgentes" counter reflects backend risco

**Test:** Start backend (with a working `MINIO_ENDPOINT`) + frontend, log in, open `/agenda`, read the "Urgentes" number in the "Visão Geral da Semana" card.
**Expected:** The count reflects `risco ∈ {proximo, vencido}` (7-day ALTA / 3-day other threshold), not `prioridade === "ALTA"`. A distant ALTA-priority prazo no longer counts; a near-term MEDIA-priority prazo now does.
**Why human:** Requires a live Spring Boot context + real tenant data + visual confirmation of a number driven by "today's date" logic; static analysis already proves the code path is wired correctly (see Behavioral Spot-Checks), but the live value can only be eyeballed against real data. Local backend startup is blocked by the pre-existing `MINIO_ENDPOINT` env gap (same blocker as Phases 87/89, tracked in `STATE.md`), not a defect of this phase.

### 2. No network calls to the removed endpoint

**Test:** With the Network tab open, load `/agenda`.
**Expected:** Zero requests to `/eventos/upcoming`.
**Why human:** Same live-backend/browser dependency as item 1.

### Gaps Summary

No code-level gaps found. All 8 derived truths (4 roadmap Success Criteria + 4 plan-specific must-haves) are VERIFIED against the actual codebase — backend `Evento.java`/`ResourceController.java` and frontend `types/eventos.ts`/`use-eventos.ts`/`agenda/page.tsx` — with matching commits, clean compiles/build/typecheck/lint, and zero residual client-side risk computation or dead code. The only open item is the live browser/network confirmation of the "Urgentes" counter, which is blocked by a pre-existing, already-documented environmental gap (`MINIO_ENDPOINT`) unrelated to this phase's code, and is therefore routed to human verification rather than treated as a failure.

---

*Verified: 2026-07-13T23:02:27Z*
*Verifier: Claude (gsd-verifier)*
