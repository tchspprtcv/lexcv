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

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Files | Requirements |
|-----------|--------|-------|------|-------|--------------|
| v2.3 Responsividade | 4 | 8 | 1 | 51 | 11/11 |
| v2.4 Ficha de Cliente | 4 | 14 | 1 | 79+ | 19/19 (post-audit-remediation) |
| v2.6 Módulo de Parecer Jurídico — UI | 5 | 6 | 1 | 7 (frontend+1 backend fix) | 9/9 (post-audit-remediation) |

*Table grows with each milestone*
