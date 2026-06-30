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

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Files | Requirements |
|-----------|--------|-------|------|-------|--------------|
| v2.3 Responsividade | 4 | 8 | 1 | 51 | 11/11 |
| v2.4 Ficha de Cliente | 4 | 14 | 1 | 79+ | 19/19 (post-audit-remediation) |

*Table grows with each milestone*
