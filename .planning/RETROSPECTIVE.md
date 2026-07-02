# Retrospective: LexCV

Living retrospective — one section per shipped milestone.

---

## Milestone: v2.3 — Responsividade App

**Shipped:** 2026-06-21
**Phases:** 4 | **Plans:** 8 | **Commits:** 28

### What Was Built

- Hamburger drawer + Sheet component (manual, no CLI); BottomNav `fixed bottom-0 md:hidden` with permission filter
- Dual-view CSS pattern: `hidden md:block` table + `md:hidden` cards in Clientes, Agenda, Documentos, Financeiro
- Horizontal scroll in Partes + Fases tables (`overflow-x-auto` + `min-w-[400/480px]`)
- 24x `grid-cols-1 md:grid-cols-2` across 12 form files; `max-sm:fixed bottom-0` on 3 DialogContent
- KPI grid `grid-cols-1 sm:grid-cols-2 lg:grid-cols-4` in dashboard; "Hoje" mobile block in Agenda

### What Worked

- **CSS-only dual-view pattern** (`hidden md:block` / `md:hidden`) — zero rerenders, no JS branching, easily reviewable in diff. Clear winner over JS-based responsive switching.
- **Single-day sprint** — all 4 phases completed in a single day with autonomous workflow. Tight scope (CSS className changes) made this extremely fast.
- **Audit gate caught a real bug** — integration checker found mobile card buttons were 36px (h-9) in clientes, below the 48px FORM-03 requirement. Fixed before tag. The audit step earned its cost.
- **Wave-based execution** — Phase 54's 3 plans in 3 waves enabled parallelism where appropriate while respecting data flow dependencies.

### What Was Inefficient

- **shadcn CLI interactive setup** — `npx shadcn add sheet` required interactive terminal wizard, blocked automation. Manual sheet.tsx creation was a workaround; the pattern for future CLI-blocked components should be documented.
- **Verifier stale state in Phase 55** — verifier agent ran against cached grep results showing 3 stale `sm:grid-cols-2` occurrences that no longer existed. Re-run confirmed clean. Pattern suggests verifiers should use fresh `git status` rather than cached results.
- **ROADMAP.md progress table staleness** — table showed Phase 54 as "2/3 In Progress" and Phase 55 as "0/TBD" even after execution completed. STATE.md + ROADMAP.md drift requires a reconciliation step after each wave.

### Patterns Established

- `md:` (768px) is the mobile/desktop breakpoint; `max-sm:` (< 640px) for bottom-sheet positioning; `sm:` reserved for tablet-only.
- React fragments (`<>...</>`) are mandatory when two sibling JSX elements appear inside a ternary branch.
- `sheet.tsx` can be created manually by copying `dialog.tsx` pattern with `@radix-ui/react-dialog` when CLI is unavailable.
- `pb-24 md:pb-8` on the scroll container in DashboardShell is the standard clearance for `fixed bottom-0` BottomNav.

### Key Lessons

1. **Audit step is load-bearing for FORM-03** — touch target requirements are easy to miss in code review but catchable by integration checker looking at className patterns. Keep the audit gate.
2. **Manual component creation as documented fallback** — when shadcn/UI CLI is unavailable (interactive setup required), copying the closest primitive (dialog.tsx → sheet.tsx) with the same @radix-ui dependency is reliable. Document this in CLAUDE.md if it happens again.
3. **CSS-only responsive is fast and safe** — milestone v2.3 shipped 51 files with 0 logic changes, 0 API changes, 0 backend changes. Pure CSS responsive keeps blast radius minimal.
4. **Duplicate event display warning (CAL-01)** — "Hoje" block + "Próximos Eventos" block in Agenda can show the same event twice on mobile when today's events haven't started yet. Deferred to v2.4+ but worth monitoring in user feedback.

### Cost Observations

- Model: claude-sonnet-4-6 throughout (autonomous workflow)
- Phases: All CSS-only — no research needed, no new dependencies (except sheet.tsx manual)
- Notable: Single-day sprint was possible because scope was well-defined (CSS breakpoints) and non-blocking across phases (53→54→55 and 53→56 in parallel)

---

## Milestone: v2.4 — Ficha de Cliente

**Shipped:** 2026-06-30
**Phases:** 4 (57–60) | **Plans:** 14 | **Commits:** ~85

### What Was Built

- Numeração sequencial automática de clientes (CLI-0001), MAX+1 por tenant, gerada no backend (synchronized block) — não editável pelo utilizador
- `TipoCliente` enum (PARTICULAR/EMPRESA) + `DadosTipo` POJO + `DadosTipoConverter` (JSON column, mesmo padrão reutilizado para 4 listas de intake em Phase 59)
- Formulário dinâmico: RadioGroup de tipo no topo, secções condicionais (Particular vs Empresa) mutuamente exclusivas, Dialog de confirmação ao trocar tipo, Switch "Avençado"
- Procuração obrigatória com aviso não-bloqueante ("Procuração em falta"), upload/substituição via MinIO presigned URLs
- Advogados/administrativos ligados a `User` do sistema via tabelas de junção tenant-scoped, com validação de papel server-side
- Ficha imprimível de alta fidelidade (`/clientes/[id]/ficha`), 8 secções, `@media print` + `@page A4`, `window.print()` sem dependência externa

### What Worked

- **Reutilização do padrão JSON-column+POJO+Converter** (Phase 57 `dados_tipo` → Phase 59 4x listas de intake) — consistente, sem migração de schema a cada novo campo.
- **Modelo de confirmação `pendingTipo` + Dialog** para trocar tipo sem perder dados acidentalmente — replicado identicamente entre `novo/page.tsx` e `editar/page.tsx`.
- **Gates por fase (verify + security + code-review) todos passaram individualmente** — cada fase, isolada, estava genuinamente correcta.
- **A auditoria de milestone (passo final, antes de completar) apanhou um bug que nenhum gate de fase apanhou**: um mismatch sistémico snake_case/camelCase entre backend e frontend que invalidava 9/19 requisitos a nível de display (dados gravados correctamente, nunca visíveis). Sem este passo, o milestone teria sido marcado como "completo" com a maioria das suas entregas-bandeira invisíveis para o utilizador.

### What Was Inefficient

- **Múltiplos agentes em background atingiram o limite de sessão a meio de waves paralelas** (58-03, 58-04, 59-04, e o orquestrador do security audit da Phase 60) — exigiu intervenção manual repetida: merge de worktrees, reescrita retroactiva de SUMMARY.md, e relançamento de sub-agentes. Consumiu tempo de coordenação significativo.
- **Um agente de segurança suspeitou (correctamente, por cautela) de mensagens "coordinator" relayadas** e recusou-se a agir sem verificação independente — correcto do ponto de vista de segurança, mas exigiu duas rondas extra de mensagens para desbloquear.
- **Verificação de fase confirmou presença de código e sucesso de build, mas nunca traçou as chaves JSON reais ponta-a-ponta** — `pnpm build`/`tsc --noEmit` a passar não prova que os dados fluem correctamente entre backend e frontend quando a tipagem estrutural do TypeScript não consegue detectar um nome de chave incorrecto em runtime.
- **Sem ambiente live (backend+DB+MinIO) disponível na sessão** para testar a correcção do mismatch — a correcção foi apenas verificada estaticamente (mapeamento campo-a-campo + builds limpos), não com um pedido HTTP real.

### Patterns Established

- `@JsonProperty` cirúrgico por campo é preferível a uma `property-naming-strategy` global do Jackson quando só um subconjunto de campos (os novos de uma milestone) está desalinhado — limita o raio de impacto a campos sob controlo, evita regressões em fluxos pré-existentes que já dependem (correcta ou incorrectamente) do comportamento por defeito.
- Padrão JSON-column (`@Convert` + `AttributeConverter` com Jackson `ObjectMapper`) para listas/objectos estruturados num único campo TEXT — replicável sempre que um novo conjunto de dados não justifica tabelas dedicadas.
- `pendingTipo`/Dialog de confirmação para trocas destrutivas em formulários React Hook Form — intercepta o `onChange` antes de `setValue`, só aplica após confirmação explícita.

### Key Lessons

1. **Verificação de fase isolada não substitui auditoria de integração entre fases.** Cada fase pode passar todos os seus próprios gates (verify, security, review) e ainda assim o sistema, como um todo, estar quebrado nas costuras entre fases. O passo `/gsd:audit-milestone` antes de `/gsd:complete-milestone` não é burocracia — é o único ponto do workflow que efectivamente testa fluxos ponta-a-ponta cruzando fases.
2. **"Build verde" e "tipos TypeScript correctos" não são prova de que os dados fluem.** Um campo `numero_cliente?: string` no tipo TypeScript compila e tipa correctamente mesmo que a resposta HTTP real nunca contenha essa chave — é preciso traçar o nome de campo real, não confiar na presença estrutural do campo no tipo.
3. **Quando vários sub-agentes em background atingem o limite de sessão em simultâneo durante waves paralelas, a recuperação manual (merge de worktrees, verificação cruzada com grep, reescrita de SUMMARY.md) é viável mas cara em tempo de coordenação** — considerar gates de "wave size" mais pequenos ou checkpoints intermédios em milestones com muitas waves paralelas.
4. **Um sub-agente de segurança a recusar agir sobre afirmações não verificadas de um "coordinator" é o comportamento correcto**, mesmo quando o coordinator é legítimo — o custo de uma ronda extra de verificação é baixo comparado ao risco de um agente de segurança agir sobre uma instrução injectada.

### Cost Observations

- Model: claude-sonnet-4-6 throughout (autonomous + interactive workflow)
- Sessões: múltiplas, com pelo menos 4 agentes em background a atingir o limite de sessão a meio de tarefa
- Notable: a auditoria de milestone (passo final) encontrou mais problemas reais (2 blockers genuínos) do que todos os 4 code-reviews por fase combinados (que encontraram apenas 5 críticos, todos menores em comparação) — sinal forte de que o investimento no passo de auditoria de integração vale o custo

---

## Milestone: v2.6 — Módulo de Parecer Jurídico — UI

**Shipped:** 2026-07-01
**Phases:** 5 | **Plans:** 6 | **Tasks:** 15

### What Was Built

Frontend UI for the Módulo de Parecer Jurídico, closing the "backend-only, zero product usability" gap left by v2.5: `/pareceres` list (dual-view, badges, filters), detail page with immutable version timeline, create-solicitação form (cliente/processo/advogado), version-elaboration form (resumo + required attachment, reusing the Documentos upload component), irreversible entrega action with confirmation dialog and a dedicated "Parecer Entregue" summary view (closing the v2.5 audit's PARC-09 gap), and an advanced-search panel exposing the backend's previously-unused `pesquisar()` endpoint. RBAC mirrored end-to-end, including the non-uniform instance-level check (`isAdmin || isResponsavel`) the backend requires for versioning/entrega that a plain scope check can't express.

### What Worked

- **Milestone-level research (4 parallel researchers + synthesis) before any phase planning** correctly predicted zero new dependencies were needed and flagged the exact defect classes (camelCase/snake_case repeat, RBAC scope-tier asymmetry) that later phases had to actively guard against — the research paid for itself multiple times over.
- **Direct (non-worktree) execution for plan executors.** A `isolation="worktree"` executor spawn early in the milestone pointed at a stale checkout missing recent planning commits and failed outright; switching every subsequent executor to run directly on the main working tree worked reliably across all 5 phases with no repeat failures.
- **The scope-correction pattern.** Mid-milestone, exploring the actual codebase for Phase 66 revealed that 3 requirements (NOTF-05/06/07) assumed a generic notification backend that doesn't exist — the existing `NotificationBell` only surfaces Agenda events. Rather than silently building unplanned backend work or silently dropping the requirements, this was surfaced to the user immediately with a concrete question, confirmed, and REQUIREMENTS.md/ROADMAP.md were corrected same-session. This is the second milestone in a row (after v2.4/v2.5) where a mid-execution discovery reshaped scope — worth treating as a standing expectation, not a rare exception.
- **Code-review + fix + re-review loop, every phase, no exceptions.** All 5 phases had real findings (16 total warnings) and all were fixed and re-verified same-session before moving on — including one case (Phase 68) where the fixer's own fix didn't actually type-check and was caught by an independent `tsc --noEmit` run before being trusted.

### What Was Inefficient

- **The single biggest miss of this milestone was NOT caught by any phase-level review — only by the final milestone integration audit.** The backend's `pesquisar()` endpoint had a routing bug (Spring concatenates class-level + method-level `@RequestMapping` regardless of leading `/`, so the actual bound route never matched the documented path) that existed since v2.5 Phase 64 and made the entire Phase 69 feature 404 at runtime. Every phase-level code review, plan-checker pass, and static verification for Phase 69 passed cleanly because none of them actually traced the fully-resolved Spring route — they checked the annotation text matched, not what Spring does with two mappings. This is the same class of lesson v2.4 taught about camelCase/snake_case: **a static code read is not the same as verifying the resolved runtime contract**, and for Spring specifically, resolved-route verification needs to be an explicit, named check, not implied by "the annotation looks right."
- **Some commit hygiene slipped** — PATTERNS.md files for 3 phases were generated but not committed in their phase's normal commit batch, discovered only when checking `git status` mid-milestone. Low-severity but avoidable with a stricter "commit everything the phase produced, verify via git status" habit before moving to the next phase.
- **UI review scores trended down then up then down again** (18→19→17→20→15 across phases 65-68... 69) rather than monotonically improving, even with an explicit recurring-defect (CardTitle) finally closed in Phase 68. Phase 69's lowest score (15/24) came from a genuine new interaction gap introduced by fixing a different bug (Aplicar silently discarding an active search) — a reminder that fixing one UX issue can create an adjacent one, and UI review should be re-run after every fix round, not just after the initial implementation.

### Patterns Established

- Milestone-wide UI-SPEC consistency: each phase's UI-SPEC explicitly reused the prior phase's spacing/typography/color decisions verbatim rather than re-deriving them, and later phases were given the accumulated list of prior UI-review findings to avoid repeating them — this is what let Phase 68 finally close a defect that had recurred 3 times.
- Scope-descope-and-confirm: when planning reveals a requirement rests on a false premise (infra that doesn't exist, a field the API doesn't expose), stop, verify against the actual source, present the finding with a recommended resolution, and update REQUIREMENTS.md/ROADMAP.md same-session rather than silently building around it or silently dropping it.
- Backend bug fixes discovered during frontend milestones are in-scope when they're pure bug fixes to already-existing endpoints (not new capability) — done twice this milestone (Phase 66's cliente/processo cross-validation gap, and the final pesquisar() routing fix) without expanding the "no new backend work" constraint's intent.

### Key Lessons

- **Trace the fully-resolved runtime route, not just the annotation, when a controller has both class-level and method-level `@RequestMapping`/`@GetMapping` with absolute-looking paths** — Spring always concatenates, a leading `/` on the method path does not reset to the class root. Add this as an explicit check item for future Spring-backend phase/plan verification, not something assumed correct from a visual read.
- Milestone integration audits catch classes of bugs that no amount of per-phase static review will catch, because they're the only step that actually asks "does this chain of calls work end-to-end against the real other side," rather than "does this file's code look internally consistent."
- When a code-review fix resolves one interaction bug, immediately consider what NEW interaction gap it might introduce (e.g., "resetting state X on event Y" can silently discard user work with no feedback) — a fix's own UX consequences deserve the same scrutiny as the original bug.

### Cost Observations

- Model mix: opus for all planners (5 phases), sonnet for everything else (research, UI-SPEC, checkers, pattern-mapping, execution, review, fix, verification, audit) — consistent with the project's model-profile defaults.
- Sessions: 2 — the milestone was interrupted mid-Phase-68-verification by a Claude usage-window reset and resumed via a scheduled task with a detailed handoff prompt; resumed cleanly with zero rework needed.
- Notable: the milestone-level integration audit (final step) found more genuine, previously-undetected defects (1 hard runtime blocker) than any single phase's code review — a strong signal that the audit step's cost is worth it even when every individual phase reports clean.

---

## Milestone: v2.7 — Melhoria Gestão de Clientes

**Shipped:** 2026-07-02
**Phases:** 5 (70–73.1, includes 1 gap-closure insertion) | **Plans:** 6

### What Was Built

Simplification and flattening of the client identification data model: removed the `dados_tipo` JSON card entirely (backend entity + converter, frontend types + schema), added `REG_COMERCIAL` as a `DocumentoTipo` for Empresa clients, made NIF mandatory with 9-digit validation across both layers, gave the create/edit forms dynamic labels ("Nome"/"Nome Comercial", "Morada"/"Sede" based on client type), and updated the detail page + printable ficha to drop the discontinued Empresa-only fields (Nome Comercial, Representante Legal, Cargo) instead of showing them permanently blank.

### What Worked

- **Backend-first phase ordering (70 → 71 → 72 → 73) with an explicit build-breakage guard.** Flattening the frontend types (Phase 71) deliberately left 3 consumer pages non-compiling for one plan, with the very next plan (71-02) dedicated entirely to restoring `tsc --noEmit`/`pnpm build` to green before moving on — this made an otherwise risky multi-file migration verifiable at every step instead of accumulating breakage across phases.
- **Pattern-mapper before every planner spawn**, even for small phases. For Phase 72, the pattern-mapper's line-level read of the actual current forms revealed that 2 of the 3 "fixes" the roadmap phase description implied (REG_COMERCIAL selectable, NIF field position) were already done by a Phase 71 review fix — the plan correctly scoped down to just the 2 remaining changes instead of redundantly re-touching already-correct code.
- **The revision loop caught a real, empirically-verified bug in a plan's own verify command** (Phase 71): an over-escaped grep regex across XML/shell/BRE layers that would report failure even against a correct implementation. The plan-checker didn't just flag it abstractly — it ran the broken command against real and simulated-fixed source to prove the failure mode, then the fix was verified the same way on re-check.
- **Milestone audit → gap closure → re-audit closed a loop cleanly.** The first audit found CLI-05 unsatisfied via a cross-phase integration defect invisible to any single phase's static verification (a pre-milestone legacy code path interacting badly with a new Phase 71 field). A decimal gap-closure phase (73.1) was inserted, planned, executed, and code-reviewed — and the review itself caught a second, more subtle regression (JPA-lifecycle Bean Validation firing on unrelated `save()` calls) before it ever reached the audit. The re-audit confirmed both issues closed with zero new gaps.

### What Was Inefficient

- **A gsd-sdk roadmap-update tool call silently truncated ROADMAP.md to just the newly-inserted phase's stub** during Phase 73.1's planning step, dropping all milestone history, prior phase details, and the progress table. This went undetected for 4 commits (through the entire gap-closure execution) because nothing in the normal flow re-reads and validates ROADMAP.md's overall shape after a write — it was only caught when `roadmap.analyze` returned `phase_count: 0` at milestone-completion time. Recovered from git history (`git show <last-good-commit>:.planning/ROADMAP.md`), but this is a tooling reliability gap, not a process one: **any GSD workflow step that writes ROADMAP.md via the SDK should be followed by an immediate `roadmap.analyze` sanity check** (phase_count matches expectation) rather than trusting the write silently succeeded.
- **The executor for Phase 73's plan hung after committing both task edits** (a Windows stdio subprocess hang — the process stayed alive with near-zero CPU and never returned its final report). Recovered by independently verifying the commits matched the plan, re-running both automated verify gates plus a full build, then writing SUMMARY.md directly and killing the stuck task — no rework needed, but this is the second milestone in a row (after v2.6) where a Windows-specific subprocess hang required manual intervention; worth a standing runbook entry rather than re-deriving the recovery steps each time.
- **The frontend's pre-existing "NIF sync" logic (predating this milestone) was not audited for interaction with the new mandatory `nif` field during Phase 72's planning**, even though Phase 72 explicitly touched the same forms. It took a full milestone-level integration audit (not phase-level review, not phase-level verification) to surface it — a pattern now repeated across v2.6 (routing bug) and v2.7 (field overwrite bug): **legacy code paths that predate the current milestone and sit adjacent to new work are a recurring blind spot for phase-scoped checks**, and should get explicit attention during planning when a phase's `<context>` shows existing code being modified alongside new code, not just when new code is added.

### Patterns Established

- **Build-breakage guard as an explicit planner instruction**, not an assumption: when a plan is known to leave the codebase non-compiling until a dependent plan runs, say so explicitly in the planning prompt and require the dependent plan to end with a whole-app `tsc --noEmit`/`build` gate as its own must-have — this was decisive in Phase 71 landing cleanly across 2 plans with zero real-world broken-build window.
- **Gap-closure phases get the full phase lifecycle, not an abbreviated one.** Phase 73.1 went through context → pattern-mapping → planning → plan-check → revision → re-check → execution → phase-verification → code-review → fix → re-verification, exactly like a normal phase, despite being "just a bug fix." The code-review step alone caught a regression the original fix would have shipped without.
- **Re-audit after gap closure, not just re-verify the closed phase.** Phase-level VERIFICATION.md passing for 73.1 was necessary but not sufficient — only re-running the milestone-level integration checker confirmed the fix didn't introduce a new cross-cutting problem (the JPA validation-mode scoping question required tracing Spring MVC vs. Hibernate lifecycle validation as two independent systems, which is inherently a cross-file/cross-layer concern no phase-scoped check would naturally cover).

### Key Lessons

- **A phase-level `passed` VERIFICATION.md does not guarantee a requirement is satisfied end-to-end** when that requirement's implementation spans multiple phases and interacts with pre-existing code. Milestone-level integration checking is not a formality — in both v2.6 and v2.7 it found the single most severe defect of the entire milestone, undetected by every phase-level gate.
- When a fix changes validation behavior on a shared entity/model (adding a Bean Validation constraint, tightening a schema), explicitly ask "what else reads or writes this entity, and does it still hold the invariants this fix assumes?" — the JPA-lifecycle validation regression in Phase 73.1 was exactly this class of miss, caught only because code review traced actual call sites rather than trusting the fix's stated scope.
- Any tool that writes a large, structurally important file (ROADMAP.md) should have its output spot-checked immediately after the call, not assumed correct — a silent truncation can persist through several subsequent commits before anything downstream notices.

### Cost Observations

- Model mix: opus for all planners (4 phase plans + 1 gap-closure plan + 1 revision), sonnet for everything else (pattern-mapping, plan-checking, execution, verification, code-review, code-fix, integration-audit).
- Sessions: 2 — interrupted once by a Claude session usage-limit reset mid-Phase-73 pattern-mapping, resumed cleanly via scheduled wakeup with zero rework needed (both already-committed code edits were verified intact and the SUMMARY was completed directly after confirming the stuck executor's work was correct).
- Notable: this milestone needed a decimal gap-closure phase (73.1) — the second consecutive milestone (after v2.6) where the milestone-level audit, not any phase-level gate, found the most consequential defect. This is now a clear enough pattern to treat milestone audits as load-bearing rather than a final formality.

---

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Files | Requirements |
|-----------|--------|-------|------|-------|--------------|
| v2.3 Responsividade | 4 | 8 | 1 | 51 | 11/11 |
| v2.4 Ficha de Cliente | 4 | 14 | 1 | 79+ | 19/19 (post-audit-remediation) |
| v2.6 Módulo de Parecer Jurídico — UI | 5 | 6 | 1 | 7 (frontend+1 backend fix) | 9/9 (post-audit-remediation) |
| v2.7 Melhoria Gestão de Clientes | 5 (incl. 1 gap-closure) | 6 | 1 | 15 (backend+frontend) | 7/7 (post-audit-remediation) |

*Table grows with each milestone*
