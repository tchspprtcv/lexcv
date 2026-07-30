---
phase: 124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind
plan: 02
subsystem: ui
tags: [nextjs, react, tanstack-query, typescript, tech-debt, testing]

# Dependency graph
requires:
  - phase: 124-01
    provides: "GET /api/v1/auth/me expõe tenant_utilizadores_ativos (Long), a 3ª e última fonte de UserRepository.countByTenantIdAndAtivoTrue"
provides:
  - "UserManagementTab's indicador 'X/Y utilizadores' lê tenant_utilizadores_ativos de GET /auth/me em vez de recalcular via filter() client-side, fechando o achado #2 da auditoria do marco v2.16"
  - "web/scripts/verify-limite-utilizadores-indicator.mjs reescrito (9 asserções) para proibir — não apenas exigir — o regresso do filtro client-side"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Gate script assertion rewritten in place (id + descricao + predicate juntos), nunca bypassed, quando o contrato que codifica muda — precedente reutilizável para futuras fases de fecho de dívida técnica que toquem gates de fases anteriores"

key-files:
  created: []
  modified:
    - web/src/types/auth.ts
    - web/src/app/(dashboard)/settings/page.tsx
    - web/scripts/verify-limite-utilizadores-indicator.mjs

key-decisions:
  - "activeUserCount lido diretamente de meData?.tenant_utilizadores_ativos ?? 0, no mesmo optional-chaining-com-fallback da linha vizinha (tenantUserLimit), preservando tenantUserLimit/atUserLimit/userCountLabel byte-a-byte"
  - "Comentário da derivação reescrito em prosa sem reproduzir o literal '.ativo === true' nem 'useAdminUsers()' — ambos colidiriam com os próprios critérios de aceitação grep -cF desta task"
  - "Asserção do gate 'contagem-estrita' renomeada para 'contagem-da-fonte-unica' com guarda negativa (!settingsPage.includes('.ativo === true')) em vez de criada em paralelo — id antigo removido, não deixado a coexistir"

requirements-completed: []

# Metrics
duration: ~25min
completed: 2026-07-30
---

# Phase 124 Plan 02: Eliminar Duplicação da Contagem de Utilizadores Ativos — Frontend Summary

**O indicador "X/Y utilizadores" das Definições passou a ler `tenant_utilizadores_ativos` de `GET /auth/me` em vez de recalcular a contagem com um `filter()` client-side, e o gate da Fase 118 (`verify-limite-utilizadores-indicator.mjs`) foi reescrito com uma guarda negativa que passou a proibir — em vez de exigir — o regresso desse filtro, provada por 2 regressões mínimas isoladas.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-30
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- `UserManagementTab` (`settings/page.tsx`) lê `activeUserCount` diretamente de `meData?.tenant_utilizadores_ativos ?? 0`; a segunda implementação client-side (`users?.filter((u) => u.ativo === true).length`) deixou de existir no ficheiro.
- `MeResponse` (`web/src/types/auth.ts`) ganhou `tenant_utilizadores_ativos?: number | null;`, seguindo a convenção `| null` explícita do irmão numérico `tenant_limite_utilizadores`.
- `tenantUserLimit`, `atUserLimit`, `userCountLabel` e todo o bloco de render (classes CSS, tooltip, botão desativado) ficaram byte-a-byte idênticos — confirmado por `git diff`, não apenas por inspeção.
- O gate da Fase 118 (`verify-limite-utilizadores-indicator.mjs`) passou de 8 para 9 asserções: `contagem-estrita` foi reescrita para `contagem-da-fonte-unica` (agora exige a leitura da fonte nova E proíbe, com guarda negativa, o regresso do filtro antigo) e uma asserção nova (`types-auth-tenant-utilizadores-ativos`) cobre o `| null` explícito do campo novo.
- A precisão do gate reescrito foi provada por 2 regressões mínimas de uma linha cada, cada uma isolando exactamente 1 `FAIL` no id esperado (ver secção dedicada abaixo).
- Os outros 3 gates do marco v2.16 (`verify-bloqueio-rbac`, `verify-consola-tenants`, `verify-relatorio-utilizacao`) permanecem verdes, confirmando zero não-regressão cruzada.

## Task Commits

Each task was committed atomically:

1. **Task 1: Trocar a fonte de activeUserCount para tenant_utilizadores_ativos e remover o filtro client-side** - `29c52dee` (refactor)
2. **Task 2: Reescrever a asserção de contagem do gate para a fonte nova, com guarda negativa, e provar a sua precisão** - `239919c0` (test)

_Nenhuma das duas tasks era `tdd="true"`; a plan é `type: execute`. O RED intermédio da Task 1 (ver abaixo) é um sinal verificável dentro de uma única task `auto`, não um ciclo TDD formal com commits RED/GREEN separados — por isso cada task gerou exactamente 1 commit._

## Files Created/Modified

- `web/src/types/auth.ts` - `MeResponse` ganhou `tenant_utilizadores_ativos?: number | null;` como última propriedade.
- `web/src/app/(dashboard)/settings/page.tsx` - `activeUserCount` trocado de `.filter()` client-side para `meData?.tenant_utilizadores_ativos ?? 0`; bloco de comentário de 5 linhas reescrito para 8 linhas descrevendo a fonte real.
- `web/scripts/verify-limite-utilizadores-indicator.mjs` - asserção `contagem-estrita` reescrita para `contagem-da-fonte-unica` (id, descrição e predicado, com guarda negativa); asserção nova `types-auth-tenant-utilizadores-ativos` inserida a seguir a `types-auth-tenant-limite`.

## Decisions Made

- **`t.getId()`-equivalente no frontend — usar directamente `meData?.tenant_utilizadores_ativos`, sem re-derivar nada:** a Task 1 segue à risca D-02/D-03 do `124-CONTEXT.md` — só a origem do número muda, nenhuma outra expressão é tocada, o que torna a prova de não-regressão do Critério de Sucesso 3 uma simples leitura de `git diff` (zero linhas tocadas em `tenantUserLimit`/`atUserLimit`/`userCountLabel`/classes CSS/tooltip).
- **Comentário reescrito evita reproduzir os dois literais que os próprios gates desta task procuram (`.ativo === true` e `useAdminUsers()`):** a primeira redacção do comentário mencionou `useAdminUsers()` para explicar que a lista continua a alimentar a tabela — isso elevou a contagem de `grep -cF 'useAdminUsers'` de 2 (linha-base, inalterado) para 3, falhando um critério de aceitação explícito da Task 1. Reescrito para "a lista de utilizadores carregada acima", preservando o significado sem o literal. Ver `Issues Encontrados` abaixo.
- **Guarda negativa como metade central da asserção reescrita:** seguindo D-04 e a Task 2, `contagem-da-fonte-unica` não apenas verifica a presença da leitura nova — verifica ativamente a ausência de `.ativo === true` no ficheiro inteiro. Isto converte o gate de "exige a forma antiga" para "proíbe a forma antiga", fechando permanentemente o vector de regressão que esta fase existe para eliminar.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `pnpm install` necessário — `node_modules` inexistente no worktree**
- **Found during:** Task 1, antes de correr `pnpm lint`/`pnpm build`
- **Issue:** Este worktree é um checkout fresco; `web/node_modules` não existe (não é copiado por git worktrees, é gerado localmente). `pnpm lint` falhou com `ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL Command "eslint" not found`.
- **Fix:** Corrido `pnpm install` dentro de `web/`. Todos os pacotes instalados corresponderam exactamente às versões já fixadas em `pnpm-lock.yaml` (nenhuma dependência nova, nenhuma versão diferente) — hidratação do lockfile existente, não uma instalação de pacote novo, pelo que a exclusão de Rule 3 para instalações de package manager não se aplica aqui (essa exclusão cobre adicionar um pacote novo ao projecto, não reidratar `node_modules` a partir de um lockfile já commitado).
- **Files modified:** nenhum ficheiro versionado (apenas `web/node_modules/`, já gitignored).
- **Verification:** `pnpm lint` e `pnpm build` correram com sucesso depois.

**2. [Rule 3 - Blocking] `web/.env.local` necessário para `pnpm build`**
- **Found during:** Task 1, `pnpm build`
- **Issue:** `next.config.ts` valida `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH` no arranque e lança erro se ausentes (documentado em `CLAUDE.md`). `web/.env.local` é gitignored (`.gitignore:34`, `.env*` exceto `.env.example`) e não existe neste worktree fresco. `pnpm build` falhou com `Error: BACKEND_API_ORIGIN is required`.
- **Fix:** Criado `web/.env.local` com os 2 valores exactos já documentados em `web/.env.example` (`http://localhost:8080` / `/api/v1`) — nenhum segredo envolvido, apenas URLs de desenvolvimento local já públicas no próprio repositório.
- **Files modified:** `web/.env.local` (não commitado — permanece gitignored, consistente com a convenção do projecto).
- **Verification:** `pnpm build` completou com sucesso (26 rotas, `✓ Compiled successfully`).

---

**Total deviations:** 2 auto-fixed (ambas Rule 3 — ambiente de worktree fresco sem dependências/config local instaladas, não um problema de código do plano).
**Impact on plan:** Nenhum impacto no código de produção ou no âmbito do plano — puramente infraestrutura de ambiente necessária para correr as verificações que o próprio plano exige.

## Issues Encountered

- **Comentário auto-referencial colidiu com o próprio critério de aceitação da Task 1:** a primeira versão do comentário reescrito em `settings/page.tsx` mencionava literalmente `useAdminUsers()`, elevando `grep -cF 'useAdminUsers'` de 2 para 3 e violando o critério "inalterado face à linha-base". Detectado imediatamente ao correr a verificação de contagens (antes de qualquer commit), corrigido reformulando a frase sem o literal, e reconfirmado 2. Nenhum commit chegou a conter a versão incorrecta.
- Nenhum outro problema de ambiente ou de grep foi encontrado; todas as contagens `grep -cF` desta sessão foram feitas com a ferramenta `Grep` dedicada (não `grep` de shell), evitando por construção o quirk do hook `rtk` documentado em `RTK.md`/`<baseline_measurements>` deste plano.

## RED Intermédio da Task 1 (confirmado)

Depois de remover o filtro client-side e antes de tocar no gate (Task 2), `cd web && pnpm -s verify:limite-utilizadores` devolveu exit code 1 com **exactamente 1 linha `FAIL`**:

```
PASS types-auth-tenant-plano
PASS types-auth-tenant-limite
PASS toast-prefix-generico
PASS use-me-auto-fetch
FAIL contagem-estrita — settings/page.tsx contem '.ativo === true' (contagem estrita, espelha countByTenantIdAndAtivoTrue) E continua a conter 'user.ativo !== false' (convencao de exibicao do badge da tabela, inalterada)
PASS copy-contract
PASS span-wrapper-tooltip
PASS layout-stack
```

7 `PASS`, 1 `FAIL`, e a falha é exactamente `contagem-estrita` — nenhuma outra asserção foi afectada, confirmando que a Task 1 não tocou em JSX, copy, CSS, nem na chamada a `useMe()`.

## Provas Negativas da Task 2 (output real)

**Prova A** — `settings/page.tsx`'s linha de derivação revertida temporariamente para `users?.filter((u) => u.ativo === true).length ?? 0`:

```
PASS types-auth-tenant-plano
PASS types-auth-tenant-limite
PASS types-auth-tenant-utilizadores-ativos
PASS toast-prefix-generico
PASS use-me-auto-fetch
FAIL contagem-da-fonte-unica — settings/page.tsx contem 'meData?.tenant_utilizadores_ativos' (contagem lida da fonte unica do backend, Phase 124) E NAO contem '.ativo === true' (guarda negativa permanente contra o regresso do filtro client-side) E continua a conter 'user.ativo !== false' (convencao de exibicao do badge da tabela, inalterada)
PASS copy-contract
PASS span-wrapper-tooltip
PASS layout-stack
```

Exactamente 1 `FAIL`, e é `contagem-da-fonte-unica` — confirmado. Revertido e reconfirmado 9 `PASS`/0 `FAIL` antes de prosseguir.

**Prova B** — `types/auth.ts`'s campo novo revertido temporariamente para `tenant_utilizadores_ativos?: number;` (sem `| null`):

```
PASS types-auth-tenant-plano
PASS types-auth-tenant-limite
FAIL types-auth-tenant-utilizadores-ativos — web/src/types/auth.ts contem 'tenant_utilizadores_ativos?: number | null;' (com '| null' explicito)
PASS toast-prefix-generico
PASS use-me-auto-fetch
PASS contagem-da-fonte-unica
PASS copy-contract
PASS span-wrapper-tooltip
PASS layout-stack
```

Exactamente 1 `FAIL`, e é `types-auth-tenant-utilizadores-ativos`, com `types-auth-tenant-limite` ainda `PASS` (prova que a asserção nova não casa por acidente a string do irmão). Revertido e reconfirmado 9 `PASS`/0 `FAIL`. `git status --porcelain web/src/` devolveu vazio depois de reverter ambas as provas, confirmando reversão integral antes do commit da Task 2.

## Contagens Finais de Grep (vs. `<baseline_measurements>` do plano)

| Ficheiro | Padrão (`grep -cF`) | Antes | Esperado Depois | Confirmado |
|---|---|---|---|---|
| `settings/page.tsx` | `ativo === true` | 1 | 0 | **0** |
| `settings/page.tsx` | `user.ativo !== false` | 1 | 1 | **1** |
| `settings/page.tsx` | `meData?.tenant_utilizadores_ativos` | 0 | 1 | **1** |
| `settings/page.tsx` | `useAdminUsers` | 2 | 2 | **2** |
| `settings/page.tsx` | `const { data: meData } = useMe();` | 1 | 1 | **1** |
| `types/auth.ts` | `tenant_limite_utilizadores?: number \| null;` | 1 | 1 | **1** |
| `types/auth.ts` | `tenant_utilizadores_ativos?: number \| null;` | 0 | 1 | **1** |
| `verify-limite-utilizadores-indicator.mjs` | `id: "` | 8 | 9 | **9** |
| `verify-limite-utilizadores-indicator.mjs` | `contagem-estrita` | 1 | 0 | **0** |

Todas as contagens coincidem exactamente com a linha-base do plano. Todas as verificações foram feitas com a ferramenta `Grep` dedicada, não `grep` de shell.

## Texto Final do Comentário Reescrito (`settings/page.tsx:196-203`)

```typescript
  // Indicador "X/Y utilizadores" (Phase 118 PLAN-03, fonte trocada na Phase 124)
  // — useMe() dedupe pela cache partilhada ["auth","me"], nao e um segundo
  // pedido de rede. X vem diretamente de tenant_utilizadores_ativos, calculado
  // no backend pela funcao unica de contagem da Phase 117, fechando a
  // duplicacao encontrada pela auditoria do marco v2.16 (Phase 124). A lista
  // de utilizadores carregada acima mantem-se apenas para alimentar a tabela
  // de gestao abaixo. A convencao de exibicao do badge "Ativo" dessa tabela e
  // separada e permanece inalterada.
```

## Critério de Sucesso 3 (ROADMAP) — prova estrutural

`git diff` de `settings/page.tsx` (Task 1) mostra que `tenantUserLimit`, `atUserLimit`, `userCountLabel`, `· limite atingido`, `flex flex-col items-end gap-2`, `tabIndex={0}` e `TooltipTrigger` não têm nenhuma linha adicionada ou removida — apenas o bloco de comentário (5→8 linhas) e a linha de `activeUserCount` (1 linha trocada) mudaram. Isto prova estruturalmente que os 3 estados visuais ("N utilizadores" / "N/M utilizadores" / "N/M utilizadores · limite atingido", cores, botão desativado, tooltip) continuam byte-a-byte idênticos, sem depender de inspecção visual — as mesmas entradas (`activeUserCount`, `tenantUserLimit`) continuam a alimentar exactamente o mesmo ternário e o mesmo JSX.

A confirmação visual ao vivo dos 3 estados (o `<human-check>` deste plano) fica deliberadamente para o UAT de fim de fase, consistente com `STATE.md`'s nota de que a verificação por browser esteve indisponível por falha de tooling durante a Fase 122 — a prova estrutural acima já cobre o essencial e não é bloqueante.

## Verificação de Não-Regressão (gates vizinhos do marco v2.16)

- `pnpm -s verify:bloqueio-rbac` (Fase 121, lê o mesmo `settings/page.tsx`) — 12/12 `PASS`, exit 0.
- `pnpm -s verify:consola-tenants` — 12/12 `PASS`, exit 0.
- `pnpm -s verify:relatorio-utilizacao` — 15/15 `PASS`, exit 0.
- `pnpm lint` — 0 erros, 18 avisos pré-existentes em ficheiros não relacionados (fora do âmbito desta task).
- `pnpm build` — sucesso, 26 rotas geradas.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- O achado #2 da auditoria de integração do marco v2.16 (`v2.16-MILESTONE-AUDIT.md`) está fechado: o indicador "X/Y utilizadores" e o `409` autoritativo de `AdminController.limiteUtilizadoresExcedido` partilham agora a mesma e única fonte (`UserRepository.countByTenantIdAndAtivoTrue`), sem nenhuma reimplementação client-side remanescente.
- O gate `verify-limite-utilizadores-indicator.mjs` está verde com 9 asserções e passou a ser uma guarda permanente contra o regresso desta dívida técnica específica — qualquer PR futuro que reintroduza `.ativo === true` em `settings/page.tsx` fará este gate falhar.
- Nenhum bloqueador identificado para o fecho do marco v2.16. A confirmação visual ao vivo dos 3 estados do indicador (não bloqueante, ver acima) fica para o UAT de fim de fase que normalmente acompanha o fecho da milestone.

## Self-Check: PASSED

- FOUND: `web/src/types/auth.ts` contém `tenant_utilizadores_ativos?: number | null;`
- FOUND: `web/src/app/(dashboard)/settings/page.tsx` contém `meData?.tenant_utilizadores_ativos`
- FOUND: `web/scripts/verify-limite-utilizadores-indicator.mjs` contém `contagem-da-fonte-unica` e `types-auth-tenant-utilizadores-ativos`
- FOUND: commit `29c52dee` (refactor — Task 1)
- FOUND: commit `239919c0` (test — Task 2)

---
*Phase: 124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind*
*Completed: 2026-07-30*
