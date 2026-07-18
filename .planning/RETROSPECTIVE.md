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

## Milestone: v2.8 — Refatoração Ficha de Cliente

**Shipped:** 2026-07-06
**Phases:** 6 (74–79) | **Plans:** 13 | **Tasks:** 26

### What Was Built

Unified the cliente ficha's view/edit split into a single component (`/clientes/[id]/editar` removed entirely) with an in-place `isEditing` toggle, then reorganized it into 7 tabs replicating processos' button-toggle pattern: Dados (identificação as its own sub-section), Contactos e Notas, Processos/Pareceres (wired to already-existing `useProcessos`/`usePareceres` hooks via lazy-mount sub-components), Documentos a Tratar/Deslocações (pure relocation of existing text-list UI), and Documentos Entregues (the only phase with new backend work — a tenant-scoped `GET /clientes/{id}/documentos` endpoint, replacing a legacy text list with real file upload reusing the generic `Documento` system). The `documento_tipo` enum was restructured first (BI added, NIF removed, restricted by client type) since every later phase's identification UI depended on the final value set.

### What Worked

- **Sequencing the enum phase (74) before the UI restructuring phases (75-79) that depend on it** — every later phase could assume `documento_tipo`'s final value set without any mid-milestone rework. This mirrors v2.7's "backend-first" lesson but applied at the requirements-dependency level rather than just build-breakage.
- **Smart-discuss grey-area tables before every phase, even "obvious" relocation phases.** Phase 78 (relocating 2 existing list sections into their own tabs) surfaced a real ambiguity via the plan-checker: the smart-discuss's own assumption about read-mode visibility contradicted what the actual code did (`isEditing ? (...) : null` hides the whole section, not just the Adicionar button). Caught and corrected in CONTEXT.md *and* the UI-SPEC before any code was written — cheaper than catching it in execution.
- **Pattern-mapper surfacing pre-existing infrastructure gaps before planning, not during execution.** For Phase 79, the pattern-mapper independently confirmed (by reading Phase 77's actual merged code) that the "hook `enabled` param" idea from the UI-SPEC draft was never how the prior phase actually solved lazy-fetch — it used lazy-mount sub-components instead. The planner was redirected to the proven pattern before writing a single task, avoiding a redundant hook refactor.
- **Code review on the final phase caught 3 genuinely critical, ship-blocking bugs that would have silently broken the whole feature**: a FormData key-name mismatch (`cliente_id` vs backend's `clienteId`) that persisted every cliente-tab upload with `clienteId = null` (uploads would never show up in the client's own document list — undetectable by `tsc`/`pnpm build`, since FormData field names are untyped strings), a download link pointed at a JSON-returning endpoint instead of the file stream, and a missing tenant-ownership check on upload. All three were traced end-to-end and confirmed fixed in the same session, with two follow-up review rounds surfacing (and closing) 2 more real findings before the milestone audit ran.

### What Was Inefficient

- **Session usage limits interrupted execution mid-phase multiple times** (during phase 74's verifier, phase 76's planner) — recovered cleanly each time via scheduled wakeups timed to the stated reset window, with zero rework, but the milestone effectively spanned more wall-clock time than active work time as a result. Worth normalizing "reschedule wakeup to reset time, then resume the exact next step" as the default recovery pattern rather than re-deriving it per interruption.
- **`*-PATTERNS.md` files were generated but not committed immediately after pattern-mapping** for at least one phase (77) — discovered only when its executor's worktree didn't have the file and had to fall back to inline plan details. Same class of miss as v2.6's commit-hygiene lesson; fixed mid-milestone by committing PATTERNS.md right after generation for all subsequent phases, but the fix should have been the default from phase 1.
- **5 consecutive phases (75-79) modified the same single file** (`clientes/[id]/page.tsx`), which made every phase's pattern-mapper/planner/executor need to re-derive current line numbers from scratch each time (line drift after every merge) — unavoidable given the milestone's shape, but a reminder that "everything lands in one file" phases carry a structural tax that "modify 2-3 separate files" phases don't.
- **3 of 6 phases (75, 76, 79) ended `human_needed` rather than `passed`** — not because of code defects (all scored 100% on automated must-haves), but because this environment had no running backend/DB/browser to exercise live upload, tab-switching, and save round-trips. This is an environment limitation repeated across every milestone this project has run so far, not new to v2.8, but worth flagging again: **live UAT capability would likely have caught the FormData key mismatch (CR-01) before code review did**, since it's exactly the kind of bug that's invisible to static analysis but obvious the first time someone actually uploads a file and checks the list.

### Patterns Established

- **Requirement-dependency-first phase ordering**: when a later phase's UI depends on an earlier phase's data model output (not just its code compiling), sequence the data-model phase first even if the UI phases would otherwise be "more visible" or higher-priority-feeling — this was the explicit rationale for placing enum work (74) before the 5 UI-restructuring phases.
- **Lazy-mount sub-components, not hook `enabled` params, for per-tab conditional fetching.** Established in Phase 77 (`ClienteProcessosTab`/`ClienteParecerTab`), confirmed and reused in Phase 79 (`ClienteDocumentosEntreguesTab`) — avoids touching shared hook signatures that have other call sites, keeps the lazy-fetch behavior local to the one component that needs it.
- **Corrections discovered post-planning (pattern-mapper/plan-checker) get written back into CONTEXT.md, not just patched silently into the plan.** Phase 78's read-mode visibility correction and Phase 79's lazy-mount redirection were both propagated back to the phase's own CONTEXT.md/UI-SPEC.md so the artifact trail stays internally consistent for any future re-read.

### Key Lessons

1. **A snake_case/camelCase FormData field mismatch is functionally invisible to `tsc --noEmit` and `pnpm build`** — `form.set("cliente_id", ...)` type-checks perfectly fine even when the backend's `@RequestParam` expects `clienteId`. This is the third milestone in a row (after v2.4's JSON key mismatch and v2.6's Spring route-concatenation bug) where a wire-format/contract mismatch survived every static check and was only caught by a reviewer actually tracing the two sides of the contract against each other. **This class of bug needs an explicit "trace the wire contract, don't trust the type" check as a standing item in code review for any FormData/multipart upload code**, the same way v2.6 established it for Spring route resolution.
2. **When a phase relocates existing UI/state without redesigning it, verify the *actual current gating behavior* by reading the code — don't assume the "obvious" interpretation (e.g., "list stays visible, only the add button hides") is what's already there.** Phase 78's grey-area answer was wrong until the pattern-mapper caught it against real source; the fix was cheap because it happened before execution, not after.
3. **On a request-body wire-format bug (CR-01), self-review from the same agent that wrote the code may not catch it — an independent code-review pass with an explicit instruction to trace the contract end-to-end is what worked.** The original executor's own grep-based acceptance criteria checked that fields were *sent*, not that the *names* matched what the receiving side expected.

### Cost Observations

- Model mix: opus for all planners (6 phases + 2 gap-closure re-plans), sonnet for everything else (research, UI-SPEC researchers/checkers, pattern-mapping, execution, code-review/fix, verification, integration audit).
- Sessions: interrupted at least twice by Claude usage-limit resets (phase 74 verification, phase 76 planning) — both resumed cleanly via scheduled wakeup timed to the stated reset window, zero rework needed either time.
- Notable: this milestone needed 2 gap-closure rounds on Phase 74 alone (frontend legacy-value preservation, then a related backend gap the first fix exposed) plus 2 code-review fix rounds on the final phase (3 critical + 4 warning findings total) — more iteration than v2.7's single gap-closure phase, but every round found genuinely new, real issues rather than re-litigating the same one, suggesting the iteration cost was earning its keep rather than thrashing.

---

## Milestone: v2.9 — Melhoria Módulo Processos

**Shipped:** 2026-07-08
**Phases:** 5 (80–84) | **Plans:** 12 | **Tasks:** 25

### What Was Built

Deepened the processos module with structured legal-case data end-to-end: `Processo.juizo`/`origem` plus three new lean child entities (`Decisao`, `Facto`, `Testemunha`) at the data layer (Phase 80); 12 REST endpoints for their CRUD with the stricter `ProcessoFase`-style double tenant+processoId ownership check, plus `juizo`/`origem` wired through the full Processo create/update/intake/list lifecycle (Phase 81); an idempotent, transactional Honorário auto-creation on processo formalização with `valorTotal` always starting `null` (Phase 82, deliberately isolated since it concentrated the milestone's highest financial-safety risk); frontend types/Zod schemas/12 TanStack Query hooks with a genuine executable round-trip proof against the shared `juizo`/`origem` mapping module (Phase 83); and the full UI — required Origem in intake, editable Juízo, 4 new tabs (Decisões/Factos/Testemunhas/Documentos), a printable Termo de Honorários route with a hard print-block on missing `valorTotal`, and a user-requested mid-milestone extension refactoring the pre-existing Partes/Fases tabs to the same Dialog "Adicionar" pattern (Phase 84). The milestone's own audit then found and closed 2 real cross-phase integration bugs before shipping.

### What Worked

- **Milestone research (STACK/FEATURES/ARCHITECTURE/PITFALLS) correctly predicted this would be a pure pattern-reuse milestone with zero new dependencies** — confirmed true across all 5 phases; the only real risk research flagged (the project's 3-times-recurring camelCase/snake_case mapping bug) was the one thing Phase 83 was explicitly built to close, and it held.
- **Pattern-mapper reading the *live* backend source (not the plan/research docs) before Phase 83's frontend types were written** caught that `updateDecisao`/`createTestemunha`/`updateTestemunha` had shifted to a `Map<String,Object>` request body mid-milestone (a Phase 81 code-review fix), which would have silently mismatched if the frontend hooks had been built against the originally-planned entity-typed shape instead.
- **The plan-checker caught a genuinely fake regression test before execution**: Phase 83's round-trip verification script was planned as a hand-copied reimplementation of the mapping logic rather than an import of the real functions — exactly the "two independently-maintained copies silently diverge" bug class the script existed to prevent. Caught and fixed in one revision cycle, before a single line of the fake test ran green for the wrong reason.
- **Code review across every phase, not just the riskiest one, kept finding real bugs**: Phase 82's review caught a critical cross-layer bug (backend correctly returning `valorTotal: null`, but the existing `/financeiro` pages' TypeScript type declared it non-nullable and crashed on render — every single `formalizar` action would have broken two already-shipped pages). Phase 83's review, while tracing the same `toProcessoApiPayload`/`normalizeProcesso` functions it was asked to extend, independently found and fixed two *pre-existing* bugs unrelated to this milestone's own diff (`legal_hold`/`data_retencao` silently dropped on every processo edit; the processos list showing "Sem número" for every row). Phase 84's review caught a broken Fases "Guardar" save path introduced by its own Partes/Fases Dialog refactor.
- **The milestone-level integration audit earned its keep on the very last step**: both Termo de Honorários and the new Documentos tab passed every phase-level check (types compiled, hooks were called correctly, backend endpoints existed) yet were both silently broken at the seam between Phase 82/pre-existing-Financeiro and Phase 84 — a query-string filter (`processo_id`) sent by the frontend and silently ignored by two separate backend list endpoints, so both features displayed/blocked/deleted data for the *wrong* processo in any tenant with more than one formalized case. Neither individual phase's automated check could have caught this; it is exactly the class of gap a milestone-level cross-phase check exists for.

### What Was Inefficient

- **Two rounds of code-review-then-fix were needed on Phase 81 (7 findings) and Phase 84 (5 findings)** rather than one — in both cases the *fix itself* introduced a new, narrower defect (Phase 81: `updateFacto` gained no `DataIntegrityViolationException` handling when the sibling `createFacto` fix added a unique constraint that now also applies to updates; Phase 84: the dead-code-removal fix for one warning was correct but is exactly the kind of "large removal in a 2500-line file" change worth a dedicated re-review, which it got). Not wasted work — every round found something real — but a reminder that a fix to one finding can quietstly reopen an adjacent one in the same file, especially when the fix adds a new constraint or removes a gating condition.
- **5 of 12 plans across the milestone all touched the same single file** (`processos/[id]/page.tsx`, growing to ~2550 lines) — identical structural tax to v2.8's `clientes/[id]/page.tsx` pattern (5 consecutive phases, same file). Sequential (not parallel) execution was chosen up front specifically to avoid this, at some wall-clock cost, and it did prevent any merge conflict — but every plan/pattern-map/review pass still had to re-derive current line numbers from scratch.
- **3 of 5 phases (81, 82, 84) ended `human_needed` rather than `passed`** — same environment limitation as every prior milestone (no running dev server/authenticated session available), not a code defect. Notably, this is also where the Termo de Honorários/Documentos integration bug was hiding — a live click-through of "formalize two different processos, open each one's Termo de Honorários" would likely have surfaced it before the milestone audit did.

### Patterns Established

- **Milestone-level integration audits should specifically re-verify query-parameter filtering end-to-end, not just contract/type shape** — both bugs found here were cases where the frontend correctly sent a filter param and the backend correctly accepted the GET request (just silently ignoring the param), a shape that's invisible to `tsc`, `mvn package`, and any single-phase code review that reads one side of the contract without independently re-deriving what the *other* side actually does with the input.
- **When a code-review fix adds a new constraint or removes a gating condition, re-review the sibling code paths in the same file, not just the fixed line** — established explicitly after Phase 81's `updateFacto` gap (sibling to the fixed `createFacto`) and reinforced by the two-round pattern on Phase 84.
- **User-requested scope extensions mid-phase (Partes/Fases → Dialog refactor) are cheap to absorb when they land in a phase already touching the same file/pattern**, and get written into CONTEXT.md as an explicit locked decision rather than treated as out-of-band — same discipline already established in v2.8.

### Key Lessons

1. **A query-string filter parameter silently ignored server-side is now a confirmed 4th instance of "the wire contract looks right on both sides but isn't," alongside the project's 3 prior camelCase/snake_case incidents.** The common thread across all 4: each side of the contract "looks correct" when read in isolation, and only an explicit end-to-end trace (or, in this case, a milestone-level integration check) catches it. Recommend the pattern-mapper/plan-checker explicitly enumerate query/path parameters an endpoint *accepts in its signature* vs. what a consuming hook *sends*, not just whether the endpoint exists and compiles.
2. **Isolating the highest-risk work into its own phase (Phase 82, Honorário auto-creation) paid off again** — same lesson as v2.8's enum-first sequencing, applied to financial/idempotency risk instead of data-model risk. The phase's own code review caught the one bug that mattered (the `/financeiro` page crash) with full focus, uncomplicated by unrelated Decisão/Facto/Testemunha changes landing in the same review pass.
3. **A milestone that research correctly predicts as "pure pattern application, zero new dependencies" can still ship two production data-leakage bugs** — technical familiarity with the pattern doesn't substitute for verifying the *specific* wiring between phases. The audit step is not a formality even on a low-technical-risk milestone.

### Cost Observations

- Model mix: opus for all planners (5 phases, all first-pass — no gap-closure re-plans needed this milestone), sonnet for everything else (pattern-mapping, execution, code-review/fix, verification, UI-SPEC research/check, integration audit).
- Sessions: continuous, no usage-limit interruptions this milestone (unlike v2.8's two interruptions).
- Notable: this is the first milestone in the project's history where the *milestone-level audit itself* (not a phase-level code review) found and closed genuine bugs rather than only confirming requirement coverage — validating the audit step's design intent directly, not just its process compliance.

---

## Milestone: v2.10 — Notificações e Alertas

**Shipped:** 2026-07-10
**Phases:** 5 (85–89) | **Plans:** 14 | **Tasks:** 29

### What Was Built

A complete, persisted notification system built from zero prior infrastructure. `RiscoPrazoService` (Phase 85) consolidated 4 inconsistent backend "prazo crítico" computations into one shared source. `Notificacao`/`NotificacaoService`/`NotificacaoController` (Phase 86) shipped the project's first per-recipient-private entity with strict tenant+destinatário dual-scoping and ADMIN fan-out. Phase 87 wired 4 event-triggered alerts (fase entrada, novo documento, processo atribuído, parecer atribuído) into existing controllers, including a brand-new processo reatribuição flow (`PUT /processos/{id}/atribuir` + two-step `ReatribuirResponsavelControl` UI) that didn't exist before. Phase 88 shipped the codebase's first `@Scheduled`/cross-tenant background job — a daily risk scan with 4-layer failure isolation and edge-triggered idempotent notifications. Phase 89 closed the loop with a full sino rewrite (polling+refocus badge, fused mark-read+navigate) and a new `/notificacoes` history page. Milestone audit: 16/16 requirements satisfied, 0 cross-phase integration blockers.

### What Worked

- **Parallelizing the two independent foundation phases (85 + 86) at milestone start**, exactly as research predicted — neither depended on the other, and running them concurrently in separate worktrees cost nothing while saving a full sequential phase's wall-clock time.
- **Worktree isolation for parallel waves held up cleanly all milestone** — every wave with 2+ independent plans (86's 3-plan wave, 89's 89-02/89-03 pair) ran genuinely concurrently with zero merge conflicts, because the plan-checker verified disjoint file sets before dispatch every time.
- **The 3-iteration adversarial code-review loop earned its keep repeatedly**, but Phase 89's `isInternalLinkUrl` same-origin guard is the standout case: each of the 3 rounds found a *genuinely different* bypass class (literal `//host` → backslash-at-index-1 → embedded TAB/LF/CR anywhere in the string) before the final round replaced character-enumeration entirely with a parser-based check (`new URL(url, sentinel).origin === sentinel`) that closes the whole vulnerability class instead of one more specific string. Three rounds of "fix the reported symptom" converged on "fix the underlying defect shape" exactly as the process is designed to do.
- **The milestone-level integration audit found zero cross-phase blockers this time** (vs. v2.9's 2 real bugs) — a genuine signal, not a rubber stamp: the checker re-ran the actual test suite (44/44), re-compiled, and traced 2 full E2E chains (fase-entrada→sino→tab-navigation; prazo-vencido→idempotent-notification→filterable) through current source before concluding this.

### What Was Inefficient

- **The exact same environment blocker (`MINIO_ENDPOINT` missing from `backend/.env`) recurred in Phase 87 and again in Phase 89**, blocking live E2E UAT both times for unrelated reasons (the S3Client bean fails Spring context startup for the whole app, not just notification endpoints). It was independently re-diagnosed from scratch both times rather than fixed or worked around once — a documented root-cause note after Phase 87 would have saved the second rediscovery in Phase 89.
- **Windows `git worktree remove --force` file-locking failures were 100% reproducible this milestone** — every single worktree merge (Phase 85, 86, 87, and 3 separate merges within Phase 89 alone) hit "worktree_remove_failed" from the automated cleanup tool, even though the underlying merge had already succeeded every time. The manual recovery sequence (verify via `git log` that the merge landed, `git worktree remove --force`, `git worktree prune`, manual directory removal if needed, `git branch -D`) is no longer an edge case in this environment — it is the default path.
- **A genuine `gsd-sdk` staleness bug was found at milestone close**: `init.milestone-op`'s `completed_phases`/`all_phases_complete` fields reported `0`/`false` despite `ROADMAP.md` and `phases.list` both correctly showing 5/5 phases complete. Worked around by trusting the ROADMAP.md progress table as ground truth instead of the SDK's derived counter — the counter itself needs a fix in a future GSD update.
- **A Bash-tool JSON-manifest construction bug cost a wasted round-trip on the very first worktree merge**: a heredoc containing Windows double-backslash paths silently lost one level of escaping, producing invalid JSON for `worktree.cleanup-wave`. Switching to forward-slash paths (valid on Windows, valid unescaped in JSON) fixed it permanently for the rest of the milestone.

### Patterns Established

- **Same-origin link guards must be parser-based (`new URL(url, sentinel).origin === sentinel`), never character-position/prefix enumeration** — established the hard way, after 3 escalating review rounds each found a different bypass in a character-based check. This should be the default starting implementation for any future same-origin validation in this codebase, not something arrived at after 3 rounds of review.
- **`useXxx(filters, { poll })` — one hook, an explicit opt-in polling flag** — the shape for "identical data, one consumer polls (a dropdown/badge), one doesn't (a full history page)," avoiding two near-duplicate hooks. Established by Phase 89's `useNotificacoes`.
- **Top-level query-key-prefix invalidation (`["notificacoes"]`, not a deeper `["notificacoes","list"]`)** is the pattern for "one mutation must refresh N queries sharing a namespace" — TanStack Query's prefix-match semantics catch every dependent query without the call site needing to enumerate them.

### Key Lessons

1. **Blocklist-style validation (character/prefix enumeration) against a browser URL-parsing spec is inherently incomplete** — WHATWG URL parsing has more edge cases (whitespace/control-character stripping, backslash-as-slash normalization) than a single review pass will enumerate. Start same-origin/URL-safety checks parser-based; don't wait for 3 rounds of adversarial review to arrive there.
2. **A recurring environment blocker should be root-caused once and documented as a fix-or-workaround, not re-diagnosed fresh each time it blocks a different phase.** By Phase 89 this was the 3rd occurrence of the identical MinIO failure in this milestone alone (2nd within this milestone, at least 1 prior in v2.9) — the diagnostic cost is now larger than the cost of just fixing `backend/.env` once.
3. **On Windows, treat worktree-removal failure as the expected outcome, not the exception.** Every single merge this milestone needed the manual cleanup sequence; budgeting for it upfront (rather than re-verifying "did this actually fail?" each time) would save a diagnostic step per merge across a 5-phase, ~10-worktree milestone.

### Cost Observations

- Model mix: opus for all 5 phase planners (all first-pass, no gap-closure re-plans needed); sonnet for pattern-mapping, execution, UI research/checking, every code-review/fix iteration, verification, and the milestone integration audit.
- Sessions: continuous, with periodic transient API/classifier outages requiring resume (not restart) of interrupted agents — handled via `SendMessage` to the same agent id each time, preserving partial progress.
- Notable: this milestone had the highest per-phase code-review intensity to date — every phase used its full 3-iteration cap (vs. v2.9's 1-2 rounds varying by phase), and Phase 89 specifically never reached a clean "0 findings" state even at round 3 (the same-origin guard finally converged on round 3's parser-based fix, right at the cap). Worth watching whether a future milestone with a similarly security-sensitive surface needs either a higher iteration cap or an explicit escalation path when round 3 still finds new issues.

---

## Milestone: v2.11 — Auditoria Técnica e Notificações Avançadas

**Shipped:** 2026-07-14
**Phases:** 8 (90–97) | **Plans:** 21 | **Tasks:** 39

### What Was Built

A dual-purpose milestone: close accumulated technical debt AND expand notifications. SpotBugs/FindSecBugs fixed against JDK 23 bytecode and committed (Phase 90). The project's first real integration-test infrastructure — Testcontainers+PostgreSQL, covering the native `buscarPorFiltros` query and the `numeroVersao` concurrency lock, with a CI gate (Phase 91). Agenda consolidated onto `RiscoPrazoService` for both Prazos and Eventos, removing the orphaned `/eventos/upcoming` endpoint (Phase 92). A 4-phase sequential notification chain, forced sequential by mechanical collision on `NotificacaoService.java`: per-user category mute with `PRAZO_VENCIDO` protected (Phase 93); a pre-existing ADMIN/team-member dedup collision bug fixed (Phase 94); notification fan-out expanded from single `responsavelId` to the whole cliente team via `resolverEquipaCliente` (Phase 95); snooze/adiar for prazo reminders with 1/3/7-day presets (Phase 96). The milestone closed with a cross-cutting audit (Phase 97): tenant isolation re-confirmed on all 3 new notification surfaces, ~40 UAT scenarios closed across 8 historical phases, `DocumentoTipo` labels translated + NIF test coverage added, and — for the first time in this project's history — the recurring `MINIO_ENDPOINT` environment blocker genuinely resolved (native Postgres + real MinIO container), not merely documented as a workaround.

### What Worked

- **The research-driven 3-track parallel structure held up exactly as planned**: Phases 90/91/92 (zero file overlap with each other or the notification chain) ran independently; Phases 93→94→95→96 correctly ran sequential despite logical independence, because all four collide mechanically on `NotificacaoService.java`; Phase 97 correctly ran last, seeing the milestone's final integration shape.
- **Genuinely resolving `MINIO_ENDPOINT` this session** (rather than working around it again) unlocked real, live, authenticated E2E verification — not just code-review — for NOTF-24 (mute toggle), AGD-35 (risco field), NOTF-25 (team fan-out notification), and the full NOTF-26 snooze flow (badge drop, history, bell-hide). This is a qualitative shift from every prior milestone in this project, which all accepted `human_needed` verification status by necessity.
- **The plan-checker caught 2 real blockers in Phase 97's own UAT-closure plan before execution**: 11 of Phase 89's UAT scenarios were unassigned in the first draft, and a code citation cited the wrong line range entirely (would have planted a false citation into the phase's own evidence document). Both were fixed in one revision round.
- **Worktree-isolated parallel execution for Phase 97's Wave 1** (3 concurrent plans — tenant-isolation audit, DocumentoTipo/NIF fixes, UAT closure) merged with zero conflicts, confirmed by the plan-checker's file-overlap check before dispatch.
- **The milestone-level integration audit found and fixed a real gap in the same session it was found**, rather than deferring it a second time: `isEventoCritico`'s use of `dataFim` (vs. `listEventos`/`AlertasDiariosJob`'s `dataInicio`) — a genuine 5th divergent "prazo crítico" implementation that Phase 92's own goal was supposed to eliminate, flagged by Phase 92's own code review as deferred to Phase 97, but missed by Phase 97's bounded AUD-05 scope until the milestone-audit integration-checker rediscovered it independently.

### What Was Inefficient

- **The `dataInicio`/`dataFim` divergence (above) fell through the cracks between two phases** despite being explicitly named in both `92-REVIEW.md` and `92-CONTEXT.md` as "deferred to Phase 97" — a deferred item recorded only in a phase-level review document, without a corresponding line in STATE.md's Pending Todos, is invisible to whichever later phase is supposed to pick it up. It survived only because the milestone-level integration-checker does independent source comparison rather than trusting phase claims.
- **The same class of Postgres-transaction-abort bug was independently discovered twice** (Phase 93's preference-mute upsert, Phase 94's dedup fix) — `save()`/`saveAndFlush()` + a local `catch(DataIntegrityViolationException)` does not protect an enclosing `@Transactional` method's commit on Postgres (unlike H2/MySQL, which don't abort the whole transaction on a single constraint violation). Both times converged on the same fix (atomic native `INSERT ... ON CONFLICT DO NOTHING`), but the underlying platform-semantics lesson wasn't captured as a standing pitfall after the first occurrence.
- **Docker Desktop 4.80 / Testcontainers 1.20.4's npipe incompatibility persisted the entire milestone**, despite Phase 91 existing specifically to build the integration-test infrastructure this blocked from running locally. The tests compile and are logically sound (confirmed via direct code review), but were never actually executed in this sandbox — only in CI, unverified this session.
- **A session interruption during Phase 92's second code-review-fix iteration** left an orphaned worktree/branch and a recovery-sentinel file; recovery required manually verifying the interrupted fixer's commits had already landed (via `git merge-base --is-ancestor`) before cleaning up — no work was lost, but it consumed a diagnostic pass that a cleaner interruption-handling contract could have avoided.

### Patterns Established

- **Atomic native `INSERT ... ON CONFLICT DO NOTHING` upsert queries** are the correct fix for "Postgres aborts the whole transaction on any constraint violation" — not `try/catch` around `save()`. Established in Phase 93 (`NotificacaoPreferenciaRepository.upsertSilenciar`), reused in Phase 94 (`NotificacaoRepository.inserirSeNaoDuplicado`).
- **`criarComFanOutAdmin` helper**: merge primary recipient(s) + team + ADMIN fan-out into a single deduplicated `LinkedHashSet<UUID>` before the notification-creation loop, rather than looping over each tier separately — established in Phase 94, extended with an 11-arg overload in Phase 95 for the team tier.
- **A single choke-point service method (`criar()`) can absorb 4 sequential feature additions (mute, dedup, team fan-out, snooze) across 4 phases without becoming unmaintainable**, provided each addition is a narrow, orthogonal guard/field rather than a rewrite of the method's control flow.

### Key Lessons

1. **A deferred cross-phase item needs a durable, centrally-tracked home (STATE.md Pending Todos), not just a mention in a phase-level REVIEW.md/CONTEXT.md** — the latter is invisible to whatever later phase is supposed to close it, and only an independent, source-comparing audit (not a phase's own bounded scope) reliably catches the gap.
2. **PostgreSQL's whole-transaction-abort-on-constraint-violation semantics are a standing pitfall for this codebase specifically** (most prior H2/MySQL-trained instincts about local try/catch recovery don't hold) — worth a permanent note in PITFALLS-style documentation now that it's recurred twice.
3. **Resolving a recurring environment blocker for real (not documenting a workaround) has compounding value** — this session's actual MinIO+Postgres fix immediately converted 5 phases' worth of `human_needed` verification into genuine live-tested confirmations, something no prior milestone in this project's history had been able to do.

### Cost Observations

- Model mix: opus for all phase planners; sonnet for pattern-mapping, execution (including parallel worktree executors), plan-checking, code-review/fix iterations, phase verification, and the milestone integration audit.
- Sessions: one continuous session covering milestone setup, all 8 phases' full discuss→plan→check→execute→verify→review→fix cycles, live browser-based UAT verification, and the complete milestone lifecycle (audit→complete→cleanup).
- Notable: this is the first milestone in the project where the orchestrator itself (not a spawned subagent) performed live browser verification directly against a running dev environment mid-session, closing several `human_needed` items with real evidence rather than accepting them as pending — a capability unlocked entirely by this session's genuine resolution of the `MINIO_ENDPOINT` blocker.

---

## Milestone: v2.12 — Landing Page

**Shipped:** 2026-07-15
**Phases:** 3 (98–100) | **Plans:** 9 | **Tasks:** 21

### What Was Built

The application's first real public, unauthenticated surface. A new `GET /api/v1/public/branding` backend endpoint (Phase 98) exposing exactly `{nome, logoDataUrl}` via an explicit copy-DTO, never the `Tenant` entity, with an exact-literal `SecurityConfig` allowlist entry. A brand-new standalone Next.js 16 app `webpage/` (Phase 99) — scaffolded from scratch (own `package.json`/`next.config.ts`/`tsconfig.json`), with a setup-status gate stripped of every authentication branch, a server-side branding fetch (structurally CORS-free by construction), and a full landing page (Hero, Funcionalidades, Confiança Institucional, Contacto, dark/light mode) built from byte-copied `web/` UI primitives. Full routing/deployment wiring across dev, prod, and Hostinger (Phase 100) via Next.js's official Multi-Zones pattern (`assetPrefix`), keeping 3 independently-maintained Caddy configuration sources consistent, plus a 3rd CI/CD image artifact. Phase 100's own success criteria mandated a live `docker compose up` verification rather than isolated checks — deliberately, because this exact area (`docker-compose.hostinger.yml`'s embedded Caddy heredoc) had already produced 4 production bug-fix commits earlier in the same session.

### What Worked

- **The two-phases-in-parallel, one-phase-last structure held up exactly as designed**: Phase 98 (backend) and Phase 99 (`webpage/`) share zero files and have zero mutual dependency — Phase 99 was planned to consume a stub payload if built first; Phase 100 (routing) correctly waited for both to produce real artifacts (an actual endpoint shape, a buildable Docker image) before touching the historically fragile Caddy/Compose area.
- **The mandated live `docker compose up` verification (Phase 100-04) caught two genuine runtime bugs that all prior static/unit-level checks missed**: a relative-URL `fetch()` in `webpage/src/lib/setup.ts` that would have silently disabled the `/setup` redirect gate in every real deployment (Node/Edge `fetch` rejects relative URLs; the bug was invisible in isolated `pnpm dev` testing), and a missing `@Transactional(readOnly = true)` on `PublicController.getBranding()`'s `@Lob` read that only crashed for a tenant with a real persisted logo (the automated unit tests only covered the null-logo case). Both were found and fixed live, in the same session, not deferred.
- **Two independent findings from this milestone's git history (the `$`-interpolation footgun from `67e2120`/`534fa92`) were correctly treated as hard constraints, not just cautionary notes** — every task touching `docker-compose.hostinger.yml`'s heredoc carried an explicit acceptance criterion asserting zero new `$` characters, and the plan-checker empirically reproduced the historical bug class to prove the guard actually catches a reintroduction, not just a generic "does it start" check.
- **A code-review auto-fix iteration self-corrected its own regression**: iteration 2's fix for a `BACKEND_API_ORIGIN` validation gap moved the validation call to module scope, which — unnoticed by that same pass — placed it outside the fail-open `try/catch` blocks it was meant to protect, converting a contained config error into a total site outage. The very next re-review iteration caught this via an executable reproduction (not just a code read) and iteration 3 fixed it correctly (validation moved back inside the async function bodies). Neither the mistake nor the fix required user intervention.
- **When code review flagged a real, orthogonal, pre-existing bug (Hostinger's `docker-compose.hostinger.yml` missing a MinIO console route present in `Caddyfile.prod`) whose only two candidate fixes had already been tried and reverted earlier in this exact session**, the fixer investigated the actual git history independently, discovered *both* fixes had already failed for structural reasons (file-mounting unreliable on Hostinger; a bcrypt hash cannot satisfy the heredoc's "zero `$`" constraint by construction), and correctly deferred rather than reintroducing a known-bad pattern to force a fix.

### What Was Inefficient

- **The gsd-sdk's `roadmap.analyze`, `phases.list`, and `milestone.complete` verbs do not resolve this project's milestone-scoped phase directory convention** (`.planning/milestones/v{version}-phases/{PROJECT_CODE}-{N}-{slug}/`), even though `init.phase-op` and `find-phase` resolve it correctly — this silently produced `disk_status: "no_directory"`, `completed_phases: 0`, and empty accomplishment lists at multiple points this session (milestone init, phase discovery, and finally `milestone.complete` itself), requiring manual correction of `MILESTONES.md` and `STATE.md` after the fact. Flagged as a standalone gsd-sdk bug for a dedicated fix — this is a tooling gap, not a project defect, but it cost real orchestration time working around it.
- **The same class of "obvious fix breaks something else" surfaced twice** in the Docker/pnpm-supply-chain area: a `docker build` failure from pnpm's `minimumReleaseAge` guard needed a package-scoped exception, but the first two attempts (`.npmrc`, then a scalar YAML value) silently had zero effect because pnpm 11+ moved this setting out of `.npmrc` into `pnpm-workspace.yaml` and requires a proper YAML list — this took 3 iterations of empirical Docker-build testing to root-cause, rather than being caught by documentation lookup first.
- **A live-testing executor used a broad `taskkill //F //IM node.exe //T`** to stop a background dev-server smoke test, which kills every Node process on the machine rather than the one it started — caught and corrected via explicit instruction to subsequent parallel executors (use PID-scoped termination), but the first occurrence could have disrupted unrelated processes.

### Patterns Established

- **Server-side data fetching from a public, unauthenticated Next.js app structurally eliminates CORS as a concern** — if a fetch never executes in a browser (Server Component or Edge middleware only, confirmed by grepping for `"use client"` importers), the browser-enforced CORS mechanism never applies, regardless of what origins are or aren't configured on the backend.
- **Next.js Multi-Zones via `assetPrefix`** is the correct, officially-documented pattern for two independently-built Next.js apps sharing one domain with path-based routing — only the non-default zone needs `assetPrefix`; the existing default zone (`web/`) needs zero changes.
- **Historically-fragile, hand-authored embedded config (a Docker Compose `entrypoint: sh -c "echo '...' > file"` heredoc) must be treated as a genuinely separate, non-DRY config source** from any sibling file with similar-looking content (`Caddyfile.prod`) — reconciling them by unifying isn't always safe, and forcing a fix to close a drift gap can reintroduce a bug already fixed for a structural reason (a bcrypt hash cannot appear `$`-free).

### Key Lessons

1. **Mandating live, end-to-end verification (not just static config review or isolated dev-server checks) for infrastructure changes finds bugs that no other verification layer catches** — this milestone's single `docker compose up` test surfaced 2 of the 3 real bugs found this session, exactly the outcome the milestone's own ROADMAP anticipated when it wrote that requirement in reaction to this project's own recent incident history.
2. **A code-review auto-fix loop's own fixes need the same adversarial re-verification as the original findings** — trusting a fix's self-report (rather than re-deriving from source and re-testing) would have shipped a fix that converts a minor bug into a total outage.
3. **When a reviewer's suggested fix would repeat an approach already tried and reverted in the project's own recent git history, checking that history before applying the fix is not optional** — it's the difference between fixing a bug and reintroducing one.

### Cost Observations

- Model mix: opus for all phase planners; sonnet for codebase scouting, pattern research, execution (including parallel worktree executors), plan-checking, code-review/fix iterations, phase verification, and the milestone integration audit.
- Sessions: one continuous session covering milestone setup (from a prior session), all 3 phases' full discuss→plan→check→execute→verify→review→fix cycles, live Docker Compose verification, and the complete milestone lifecycle (audit→complete→cleanup).
- Notable: this is the first milestone in the project where the orchestrator itself started Docker Desktop mid-session specifically to satisfy a phase's mandated live-verification requirement, and where a code-review fix pass's own regression was caught and corrected within the same automated iteration loop rather than surfacing only at milestone audit.

---

## Milestone: v2.13 — Refactor UI/UX (shadcn/ui)

**Shipped:** 2026-07-18
**Phases:** 10 (101–110) | **Plans:** 42 | **Tasks:** 86

### What Was Built

A full retrofit of the design system across both apps (`web/` and `webpage/`) onto official shadcn/ui primitives, replacing 15 categories of hand-rolled UI accumulated since v1.1. Foundation (Phase 101) formally initialized the shadcn CLI on both apps, merged the semantic token set, added ~16 missing primitives, unified all Radix packages onto the single `radix-ui` package, and swapped the deprecated Toast for Sonner. Reconciliation (Phase 102) diffed 14 pre-existing hand-rolled components against the official registry with zero variant/prop loss. Seven module-migration phases (103–109) then swapped toggle-button tabs, native selects, ad hoc progress bars, manual dropdowns, custom tooltips, a hand-rolled notification badge, and a static setup checklist for their shadcn equivalents across Dashboard, a newly-built shared DataTable pattern (adopted once, reused by 5 list screens), Clientes+Processos, Agenda, Documentos+Financeiro, Pareceres, and Notificações/Settings/Setup. The public landing `webpage/` (Phase 110) gained functional mobile navigation via `Sheet` and a Hero/Contacto recomposition onto `Card`/`Badge`. 33/33 requirements satisfied; every phase closed a full code-review + goal-backward-verification + 6-pillar UI-audit pipeline.

### What Worked

- **The Foundation→Reconciliation sequential gate held exactly as designed**: no module phase started until both were fully complete, avoiding the "visibly half-migrated app" pitfall the milestone's own research flagged as the top risk.
- **The shared DataTable pattern (Phase 104), built once, was genuinely reused (not reimplemented) by all 5 declared list screens** — confirmed independently by the milestone-close integration audit, which specifically checked for per-module reinvention and found none.
- **Live human visual checkpoints kept finding real, code-review-invisible bugs before code review even started**: a `flex-wrap` layout bug on a single-child `TabsList` (Phase 105, found via measured computed layout, not code reading), a repeated `!isLoading` RBAC race across 10 page guards (Phase 105, same bug class as Phase 103's own fix), a CSS cascade tie-break that would have silently broken dark mode the moment any component started consuming the new tokens (Phase 101).
- **The Windows "stdio hang" background-agent failure pattern (first diagnosed in Phase 109) was recognized and recovered from cleanly on its second occurrence (Phase 110)** — direct git-state inspection of the stuck agent's worktree, rather than trusting the agent's own report or waiting indefinitely.
- **A milestone-close cross-phase integration audit earned its keep even after every individual phase had already passed its own verification** — it found 2 real gaps invisible to any single phase's own scoped verifier by design (a stale RBAC guard on the one screen no module phase's declared scope included; a 4th upload-progress site that fell between two phases' declared boundaries).

### What Was Inefficient

- **The `permissions.isFetched` RBAC-race fix had to be independently rediscovered and reapplied phase-by-phase** (103, then again at a 10-page scale in 105, then 106, 107, 108) rather than being fixed once centrally and grepped for project-wide — and even after 5 rounds of rediscovery, `notificacoes/page.tsx` still slipped through every phase's scope and was only caught by the milestone-close audit.
- **Worktree branches created from stale/ancient bases (documented Claude Code issue #2015) recurred across nearly every phase's parallel wave executions**, each requiring the same manual "confirm strict ancestor, then fast-forward/reset" recovery — a known, recurring tooling friction never fixed at the source.
- **Extended Browser-pane environment instability spanning Phases 105, 108, and 109's live UAT sessions was initially misattributed to tooling flakiness** across multiple phases before being root-caused (via `preview_logs({level:"error"})`, not assumed) to 2 genuine, unrelated, pre-existing React hydration-mismatch bugs — the diagnosis took until Phase 109 to land, after 3 phases' worth of UAT had already worked around the symptom rather than the cause.

### Patterns Established

- **Mobile nav drawers close via a direct `onClick` per item, never a `usePathname`/`useEffect` watcher** — established after Phase 109 found the watcher pattern misses same-route and same-page-anchor clicks; correctly reused and extended (a `matchMedia` resize-close listener, a materially different concern) in Phase 110 without regressing the same bug.
- **`Card` used as a pure layout/spacing wrapper around a raw heading, never `CardTitle`** — consistently applied across every phase that composed a new section onto `Card`, avoiding a silent `<h1>`/`<h2>`→`<h3>` heading-semantic demotion.
- **`tailwind-merge` does not deduplicate across different Tailwind modifier groups** — a bare utility and its `dark:` counterpart both survive merge and the `dark:` variant wins at runtime by CSS specificity, so any override of a shadcn primitive's default variant colors must explicitly restate the `dark:` counterpart for every color utility, never just the light-mode value.

### Key Lessons

1. **A cross-phase integration audit at milestone close is not redundant with per-phase verification, even when every phase individually passed** — each phase's verifier only checks its own declared scope by design; only a dedicated cross-phase pass catches gaps that fall between two phases' boundaries.
2. **A recurring "mysterious tooling flakiness" complaint deserves a direct root-cause pass against actual error logs before being written off as environment noise** — 2 real, fixable bugs hid behind that assumption for 2+ phases before someone checked `preview_logs` instead of just retrying.
3. **A pattern fix discovered mid-milestone (RBAC race, Phase 103) needs a project-wide grep sweep at the moment it's fixed, not a phase-by-phase rediscovery** — 5 separate phases independently found and fixed the same bug class in their own files; a single sweep at Phase 103's own close would have caught most of them for the cost of one grep.

### Cost Observations

- Model mix: sonnet throughout — planning, parallel worktree-isolated execution, code review/fix loops, goal-backward verification, and 6-pillar UI audits on every one of the 10 phases.
- Sessions: one extended, continuous autonomous session covering all 10 phases' full discuss→UI-spec→plan→execute→code-review→verify→UI-audit pipelines plus the complete milestone close-out lifecycle (audit→complete→cleanup), spanning 2026-07-15 to 2026-07-18.
- Notable: the largest phase-count milestone to date (10 phases, 42 plans, 86 tasks) with zero phases skipping any pipeline stage — every phase, including the smallest (103, 1 plan), closed a full code-review + verification + UI-audit cycle.

---

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Files | Requirements |
|-----------|--------|-------|------|-------|--------------|
| v2.3 Responsividade | 4 | 8 | 1 | 51 | 11/11 |
| v2.4 Ficha de Cliente | 4 | 14 | 1 | 79+ | 19/19 (post-audit-remediation) |
| v2.6 Módulo de Parecer Jurídico — UI | 5 | 6 | 1 | 7 (frontend+1 backend fix) | 9/9 (post-audit-remediation) |
| v2.7 Melhoria Gestão de Clientes | 5 (incl. 1 gap-closure) | 6 | 1 | 15 (backend+frontend) | 7/7 (post-audit-remediation) |
| v2.8 Refatoração Ficha de Cliente | 6 (incl. 2 gap-closure plans) | 13 | 3 | 81 | 20/20 (post-review-remediation) |
| v2.9 Melhoria Módulo Processos | 5 | 12 | 2 | 97 | 17/17 (post-audit-remediation) |
| v2.10 Notificações e Alertas | 5 | 14 | 3 | 29 (backend+web+migrations) | 16/16 (0 audit gaps) |
| v2.11 Auditoria Técnica e Notificações Avançadas | 8 | 21 | 3 | 133 | 15/15 (1 integration gap found+fixed at audit) |
| v2.12 Landing Page | 3 | 9 | 1 | 79 | 16/16 (0 audit gaps; 3 runtime bugs found+fixed live during execution) |
| v2.13 Refactor UI/UX (shadcn/ui) | 10 | 42 | 3 | 97 | 33/33 (2 integration gaps found at audit — 1 fixed, 1 deferred as small tech debt) |

*Table grows with each milestone*
