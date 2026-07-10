---
phase: 89-sino-e-pagina-de-notificacoes
plan: 02
subsystem: ui
tags: [tanstack-query, react-query, typescript, notificacoes, sino, polling]

# Dependency graph
requires:
  - phase: 89-01
    provides: "useNotificacoesUnreadCount / useNotificacoes(filters, opts) / useMarcarNotificacaoLida / useMarcarTodasNotificacoesLidas hooks, Notificacao/NotificacaoCategoria types, categoriaToLabel/categoriaToBadgeVariant map — the full data contract this plan's sino rewrite consumes verbatim"
provides:
  - "web/src/components/shared/notification-bell.tsx rewritten in-place: badge with 30s polling + window-refocus, dropdown of up to 10 Notificacao (any read state) with categoria badges, click-to-mark-read-and-navigate (fire-and-forget) fused action, mark-all-as-read with toast, footer link to /notificacoes"
  - "Same-origin relative-path guard (linkUrl.startsWith(\"/\")) on the dropdown's row-to-Link decision, closing the open-redirect/dangerous-scheme threat (T-89-02-01) from the phase threat register"
affects: [89-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "First consumer of the 89-01 opts.poll flag: useNotificacoes({ size: 10 }, { poll: true }) for the dropdown body, paired with useNotificacoesUnreadCount() (mandatory polling) for the badge"
    - "Conditional-Link-vs-plain-text row rendering with a startsWith(\"/\") same-origin guard (defense-in-depth against a contractually-relative but not statically-typed linkUrl field)"

key-files:
  created: []
  modified:
    - web/src/components/shared/notification-bell.tsx

key-decisions:
  - "Title font-weight implemented as font-normal (read) / font-semibold (unread) rather than the plan's literal 'font-medium base + bold on unread' shorthand — deferred to 89-UI-SPEC's explicit, checker-approved Typography table (Regular 400 for read titles, Semibold 600 for unread titles), which is the more precise authoritative source the plan itself points to"
  - "Dropdown body implements exactly the two decision points the plan's action text specifies (list.isPending -> loading; then empty vs list) with no separate isError branch in the dropdown itself — apiFetch's automatic toast.error already surfaces fetch failures app-wide, and the plan's <action> text for this task does not ask for a bespoke inline error string here (unlike the /notificacoes page in Plan 89-03)"
  - "Marcar-todas handler uses an async/try-catch (one of the two options the plan explicitly sanctions) so toast.success fires only after a resolved mutateAsync(), with an intentionally empty catch (commented) since apiFetch's own toast.error already reports the failure"

requirements-completed: [NOTF-08, NOTF-09, NOTF-10, NOTF-11]

# Metrics
duration: ~15min
completed: 2026-07-10
---

# Phase 89 Plan 2: Sino de Notificações Summary

**Reescrita completa dos internos de `notification-bell.tsx`: badge com polling 30s+refocus, dropdown de até 10 `Notificacao` com badges de categoria e ação fundida marcar-lida+navegar, botão marcar-todas com toast, e guarda same-origin em `linkUrl` — shell Popover e call site preservados 1:1.**

## Performance

- **Duration:** ~15 min (inclui `pnpm install` + `.env.local` para o checkout fresco do worktree, mesma necessidade documentada em 89-01)
- **Completed:** 2026-07-10T09:41:08Z
- **Tasks:** 1/1 completed
- **Files modified:** 1

## Accomplishments
- `NotificationBell` deixou de depender de `useUpcomingEventos`/`UpcomingEvento` (v2.1, agenda-only) e passou a consumir os 4 hooks de `Notificacao` persistida shipados no Plan 89-01: `useNotificacoesUnreadCount()` para o badge, `useNotificacoes({ size: 10 }, { poll: true })` para o corpo do dropdown.
- Clique numa linha com `linkUrl` relativo (`startsWith("/")`) marca-a como lida (fire-and-forget, `marcarLida.mutate(id)`) e navega no mesmo clique via `<Link onClick=...>` — sem confirmação, conforme decisão travada em CONTEXT.md. `linkUrl` nulo ou não-relativo cai num ramo de texto simples com uma afordância `Check` inline para ainda permitir marcar como lida.
- "Marcar todas como lidas" no header do dropdown: desativado quando `count === 0` ou durante a mutação (label "A marcar..."), `toast.success(...)` apenas em sucesso.
- Footer atualizado para "Ver todas as notificações" → `/notificacoes`; header "Notificações"; empty state "Sem notificações por agora."; locale de data corrigido de `pt-PT` (bug pré-existente) para `pt-CV` (alinhado com o resto da app).
- Shell `Popover`/`PopoverTrigger`/`PopoverContent className="w-80 p-0"`, a markup do botão+badge circular, e a estrutura de bordas header/body/footer mantidos verbatim — nenhuma mudança visual fora do escopo dos dados/copy.

## Task Commits

Each task was committed atomically:

1. **Task 1: Reescrever notification-bell.tsx para consumir Notificacao persistida** - `752880c` (feat)

## Files Created/Modified
- `web/src/components/shared/notification-bell.tsx` - Corpo do componente reescrito: imports trocados (`useNotificacoesUnreadCount`/`useNotificacoes`/`useMarcarNotificacaoLida`/`useMarcarTodasNotificacoesLidas`, `categoriaToLabel`/`categoriaToBadgeVariant`, `Badge`, `toast`, `Check`/`CheckCheck`); novo componente interno `NotificacaoConteudo` (título com peso condicional lida/não-lida + badge de categoria + mensagem truncada + data `pt-CV`); guarda same-origin no clique da linha; botão marcar-todas no header; export nomeado e `export default` inalterados.

## Decisions Made
- Ver `key-decisions` no frontmatter (peso de título via UI-SPEC Typography table; dropdown com 2 estados de decisão, sem bloco de erro dedicado; handler marcar-todas via async/try-catch).
- `CheckCheck` usado no botão "Marcar todas como lidas" do dropdown (não só a página) — a lista de imports do plano ("Ícones lucide-react adicionais... CheckCheck (marcar-todas)") pede o ícone especificamente para este ficheiro, e mantém paridade visual com a versão homóloga da página (Plan 89-03).
- Badge de categoria usado no tamanho/estilo default do componente (`text-xs`, `rounded-full`) sem overrides adicionais — já satisfaz "texto pequeno" pedido no plano sem introduzir uma variante nova.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Instalar dependências frontend neste worktree**
- **Found during:** Verificação da Task 1 (`pnpm --dir web build`)
- **Issue:** `web/node_modules` não existe neste checkout de worktree (git worktrees não replicam diretórios ignorados/untracked de outro worktree) — bloqueava build e lint.
- **Fix:** `pnpm install --frozen-lockfile` dentro de `web/`. Confirmado via `git status --short -- web/pnpm-lock.yaml web/package.json` antes/depois que nenhum ficheiro mudou — apenas materializa o lockfile já existente, não é instalação de pacote novo (fora da exclusão da Regra 3).
- **Files modified:** nenhum ficheiro tracked (node_modules é gitignored)
- **Verification:** `pnpm --dir web build` e `pnpm --dir web lint` completaram com sucesso a seguir.
- **Committed in:** n/a (gitignored)

**2. [Rule 3 - Blocking] `web/.env.local` local para desbloquear o build**
- **Found during:** Verificação da Task 1 (`pnpm --dir web build`)
- **Issue:** `next.config.ts` lança `BACKEND_API_ORIGIN is required` no load da config; este worktree não tem `.env.local` (gitignored, específico por developer, conforme documentado no `CLAUDE.md`).
- **Fix:** Criado `web/.env.local` com os valores placeholder exatos de `web/.env.example` (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`).
- **Files modified:** `web/.env.local` (confirmado gitignored via `git check-ignore -v`; não commitado)
- **Verification:** `pnpm --dir web build` completou com sucesso — Turbopack compile, TypeScript check completo, 23 rotas estáticas geradas.
- **Committed in:** n/a (gitignored, intencionalmente local)

### Judgment Call — requirement-tracking scope

**3. `.planning/REQUIREMENTS.md` intencionalmente não modificado para NOTF-08/09/10/11**
- Este plano declara `requirements: [NOTF-08, NOTF-09, NOTF-10, NOTF-11]` e implementa de facto os 4 comportamentos no sino. No entanto, o Plan 89-01 (Wave 1) já documentou e estabeleceu o precedente de que estes 4 IDs (e os 2 restantes da fase) são re-declarados em `89-04-PLAN.md` (Wave 3, `depends_on: [89-02, 89-03]`, `autonomous: false`) como o plano dedicado de verificação end-to-end contra dados reais.
- Mantive esse precedente por três razões: (a) consistência com a decisão já registada por 89-01 nesta mesma fase; (b) este plano corre em paralelo com 89-03 (página `/notificacoes`, worktree próprio) — editar `.planning/REQUIREMENTS.md` a partir de um worktree isolado arrisca conflito/perda de escrita quando o orquestrador fizer merge de ambos os worktrees da Wave 2; (c) 89-04 está estruturalmente posicionado (plano não-autónomo, gate humano) para confirmar estas truths após o sino E a página existirem, exatamente o padrão que 89-01 já tinha justificado.
- `requirements-completed` no frontmatter desta SUMMARY é populado mecanicamente (cópia do campo `requirements` do próprio plano) para fins de dependency-graph/context-assembly, sem tocar na tabela de rastreabilidade partilhada.

---

**Total deviations:** 2 auto-fixed (ambas Regra 3, setup de ambiente, sem alterações de código), 1 judgment call de rastreamento de requisitos (documentado, sem mutação de ficheiro partilhado).
**Impact on plan:** Zero impacto no ficheiro entregue ou no seu comportamento. Os auto-fixes foram apenas setup de ambiente local (não commitado/gitignored) necessário para correr a verificação do próprio plano.

## Issues Encountered
None além do setup de ambiente/tooling documentado acima — nenhum bug ou retrabalho necessário no ficheiro entregue; a verificação automatizada (`pnpm --dir web build`) passou à primeira tentativa após o setup do ambiente, e `pnpm --dir web lint` confirmou zero problemas no ficheiro modificado.

## User Setup Required

None - nenhuma configuração de serviço externo necessária. O `web/.env.local` local criado para desbloquear a verificação do build é gitignored e corresponde ao passo normal de setup por developer já documentado em `CLAUDE.md`/`web/.env.example` — não é um requisito novo introduzido por este plano.

## Next Phase Readiness

- O sino está pronto e funcional consumindo a API de notificações persistida; nenhuma dependência pendente para o Plan 89-04 do lado deste ficheiro.
- Plan 89-03 (página `/notificacoes`, worktree paralelo, mesma Wave 2) não tem overlap de ficheiros com este plano — `web/src/components/shared/notification-bell.tsx` (aqui) vs. `web/src/app/(dashboard)/notificacoes/page.tsx` (89-03).
- Verificação end-to-end visual (contador a incrementar com dados reais, clique a marcar+navegar, marcar-todas) fica para o checkpoint do Plan 89-04, conforme já estabelecido pelo Plan 89-01 e reconfirmado aqui.
- `.planning/REQUIREMENTS.md` checkbox completion para NOTF-08/09/10/11 deferido para Plan 89-04 (ver Deviations item 3) — nenhuma ação necessária de 89-03, é uma responsabilidade de Wave-3/orquestrador.
- Sem blockers para 89-04.

## Self-Check: PASSED

- FOUND: web/src/components/shared/notification-bell.tsx
- FOUND commit: 752880c

---
*Phase: 89-sino-e-p-gina-de-notifica-es*
*Plan: 02*
*Completed: 2026-07-10*
