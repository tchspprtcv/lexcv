---
phase: 89-sino-e-p-gina-de-notifica-es
verified: 2026-07-10T16:00:00Z
status: human_needed
score: 15/15 truths verified (code-level); live end-to-end confirmation pending human UAT
overrides_applied: 0
human_verification:
  - test: "Sino — contador (NOTF-08): gerar uma notificação real e confirmar que o badge incrementa dentro de ~30s sem refresh manual, e que muda de aba + voltar atualiza de imediato"
    expected: "Badge mostra a contagem correta (cap '9+'), incrementa por polling e ao refocar a aba"
    why_human: "Timing de polling (30s) e o evento de window-focus só são observáveis num browser real; não são verificáveis por grep/build estático"
  - test: "Sino — lista (NOTF-09): abrir o sino com dados reais e confirmar header, até 10 notificações, badges de categoria coloridos, e o empty state quando vazio"
    expected: "Renderização visual correta, cores do Badge conforme o mapa categoria->variant"
    why_human: "Aparência visual (cor, truncamento, espaçamento) não é verificável estaticamente"
  - test: "Sino — clique fundido marcar+navegar (NOTF-10): clicar uma notificação com link real e confirmar navegação para a entidade + contador desce de imediato; testar uma com linkUrl null via Check inline"
    expected: "Navega e marca-lida numa só ação sem confirmação; contador atualiza sem esperar pelo próximo polling"
    why_human: "Fluxo de navegação real do browser (next/link client-side routing) e o timing da atualização do contador exigem uma sessão de browser real"
  - test: "Sino — marcar todas (NOTF-11): clicar 'Marcar todas como lidas' no dropdown"
    expected: "toast de sucesso visível, contador vai a 0, botão fica desativado"
    why_human: "Aparência do toast e a transição do estado 'disabled' são comportamento de UI ao vivo"
  - test: "Página (NOTF-12): navegar via 'Ver todas as notificações' e confirmar ausência de qualquer entrada de Notificações na sidebar/bottom-nav em runtime"
    expected: "/notificacoes acessível só pelo link do sino; nenhum item de navegação visível"
    why_human: "Confirmação visual de todo o shell de navegação renderizado"
  - test: "Página — filtros (NOTF-13) e paginação contra dados reais (>20 notificações)"
    expected: "Select de categoria e chips filtram corretamente; 'Página X de Y' e Anterior/Seguinte funcionam nos limites"
    why_human: "Requer um volume real de notificações geradas pelas Phases 87/88 para exercitar >1 página"
  - test: "Cross-surface — invalidação partilhada: marcar como lida numa superfície (sino ou página) e confirmar que a outra reflete de imediato"
    expected: "Contador do sino desce ao marcar na página; marcar-todas no sino esvazia as não-lidas na página após refetch"
    why_human: "Comportamento de cache cross-component só é observável tendo as duas superfícies montadas na mesma sessão de browser"
  - test: "RBAC — gate notificacoes:view: utilizador sem o scope vê AccessDeniedState"
    expected: "AccessDeniedState em vez do conteúdo da página"
    why_human: "Requer um utilizador de teste sem o scope (hoje seedado para os 4 perfis); não exercitável sem sessão de browser dedicada"
  - test: "(REVIEW-FIX WR-01, follow-up) Confirmar visualmente que uma notificação com linkUrl contendo caracteres de controlo (TAB/LF/CR) renderiza como texto não clicável"
    expected: "Linha não navegável quando linkUrl é uma variante de bypass"
    why_human: "Requer escrever uma linha de teste diretamente na BD (nenhum gatilho de produção gera esse valor); o code reviewer já confirmou por análise estática + este verificador confirmou por teste comportamental independente (16/16 casos, ver Behavioral Spot-Checks) que a função em si está correta — este item é apenas uma confirmação visual final opcional, não um risco funcional aberto"
  - test: "(REVIEW-FIX WR-02, follow-up) Reproduzir filtrar 'Não lidas' + ir à última página + marcar o último item como lido, confirmar que a página corrige sem flash de 'Nenhuma notificação encontrada'"
    expected: "Transição direta para a página corrigida, sem commit intermédio da página fora de alcance"
    why_human: "Flash visual de um frame só é observável ao vivo; este verificador já confirmou estaticamente que `pnpm lint` não reporta `react-hooks/set-state-in-effect` neste ficheiro (zero issues), o que é a causa raiz que motivou o fix"
---

# Phase 89: Sino e Página de Notificações Verification Report

**Phase Goal:** O utilizador consegue ver, consultar e gerir as suas notificações diretamente na interface da aplicação — contador no sino, lista rápida com atalhos, histórico completo com filtros, e marcação de leitura individual ou em massa.
**Verified:** 2026-07-10T16:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Summary

Extensive independent verification (not reliant on SUMMARY.md claims) found **zero code-level blockers**. All 5 phase artifacts exist, are substantive, are wired together and to the already-shipped Phase 86 backend API, and — for the security-relevant `isInternalLinkUrl` guard — I independently re-ran the exact bypass-string test suite from the code review (16 cases, from a file to avoid the shell-quoting artifact the review itself documented) and it passed 16/16. `pnpm --dir web build` and `pnpm --dir web lint` were run fresh by this verifier (not copied from SUMMARY output) and confirm zero errors/warnings attributable to any of the 5 phase-89 files, and the `/notificacoes` route is present in the build's static route list.

The one open item is the live, real-browser end-to-end walkthrough (Plan 89-04's `checkpoint:human-verify` task). I independently reproduced the documented blocker: `backend/.env` genuinely has zero `MINIO_ENDPOINT` occurrences, which is exactly the failure `89-04-SUMMARY.md`/`89-HUMAN-UAT.md` describe (`MinioConfig.s3Client()` throws before the Spring context can serve any endpoint, `NotificacaoController` included). This is a pre-existing, environment-only gap unrelated to any Phase 89 commit — Phase 89 touches zero backend files, and `NotificacaoController.java`/`NotificacaoRepository.java`/`NotificacaoService.java` (Phase 86) are untouched in the current git tree. Per the decision tree, the presence of legitimate pending human-verification items forces `status: human_needed` regardless of how strong the static evidence is — this is not a `gaps_found` verdict.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Sino mostra contador de não-lidas, com polling 30s + `refetchOnWindowFocus:true` (override do default global `false`), cap "9+" | VERIFIED | `web/src/hooks/use-notificacoes.ts:42-51` (`refetchInterval: 30_000`, `refetchOnWindowFocus: true`); `web/src/app/providers.tsx:14` confirms global default is `false`; `notification-bell.tsx:81` (`count > 9 ? "9+" : count`) |
| 2 | Abrir o sino mostra até 10 notificações recentes (qualquer estado de leitura), cada uma com link quando `linkUrl` presente | VERIFIED | `notification-bell.tsx:53` (`useNotificacoes({ size: 10 }, { poll: true })`), `:114` (`.slice(0, 10)`), `:119-129` (Link when `isInternalLinkUrl(n.linkUrl)`, else plain text) |
| 3 | Clique numa notificação com link marca-lida (fire-and-forget) E navega na mesma ação, sem confirmação | VERIFIED | `notification-bell.tsx:120-127`: `<Link href={n.linkUrl}>` with `onClick={() => { marcarLida.mutate(n.id); setOpen(false); }}` — `.mutate` not awaited, `<Link>`'s native navigation proceeds unblocked |
| 4 | `linkUrl` null/não-relativo renderiza texto não-navegável + afordância `Check` inline para marcar lida | VERIFIED | `notification-bell.tsx:130-148`: else-branch renders plain div + conditional `Check` button (`!n.lida`) calling `marcarLida.mutate(n.id)` |
| 5 | "Marcar todas como lidas" no sino funciona (toast.success), desativa quando `count===0`; footer "Ver todas as notificações" → `/notificacoes` | VERIFIED | `notification-bell.tsx:57-64` (`handleMarcarTodas`), `:93` (`disabled={count === 0 \|\| marcarTodas.isPending}`), `:155-161` (`<Link href="/notificacoes">`) |
| 6 | Mutações marcar-lida/marcar-todas invalidam o prefixo top-level `["notificacoes"]`, atualizando contador+listas em qualquer superfície montada de imediato | VERIFIED | `use-notificacoes.ts:61-63,75-77`: both `onSuccess` call `queryClient.invalidateQueries({ queryKey: ["notificacoes"] })` — TanStack Query v5 default prefix-match semantics catch `["notificacoes","unread-count"]` and `["notificacoes","list",...]` |
| 7 | Mapa categoria→label/variant cobre os 9 valores com fallback seguro; `NOTIFICACAO_CATEGORIA_OPTIONS` deriva as 9 opções do mesmo Record | VERIFIED | `web/src/lib/notificacao-categoria.ts:10-20` (9-key `Record`, `?? "Notificação"` fallback), `:29-44` (variant `Record`, `?? "blue"` fallback), `:54-56` (`Object.keys(...).map(...)`); `web/src/components/ui/badge.tsx:17-21` confirms `blue`/`purple`/`amber`/`red` variants all exist |
| 8 | Página `/notificacoes`: histórico mais-recentes-primeiro, paginado (size 20), sem agrupamento por data | VERIFIED | `page.tsx:54-59` (`size: 20`, no `poll` opt); backend `NotificacaoRepository.java:28` (`ORDER BY n.created_at DESC`) |
| 9 | Select de categoria + chips 3-vias (Todas/Não lidas/Lidas) filtram e repõem `page` a 0 sem passo "Aplicar" | VERIFIED | `page.tsx:84-92` (`onCategoriaChange`/`onLidaFilterChange` both call `setPage(0)`), `:162-179` (single-select via `lidaFilter === chip.value`, `aria-pressed`) |
| 10 | Linha não-lida mostra ponto azul + título semibold; `Check` marca sem navegar (NOTF-10 standalone); título de linha já-lida navega sem re-marcar | VERIFIED | `page.tsx:273-275` (`handleTitleClick`: `if (!lida) onMarcarLida(id)`), `:281` (blue dot `!lida` only), `:296-309` (standalone `Check`, sibling of `<Link>`, not nested) |
| 11 | "Marcar todas como lidas" no cabeçalho da página funciona e desativa quando não há não-lidas | VERIFIED | `page.tsx:111` (`marcarTodasDisabled = marcarTodas.isPending \|\| (unreadCount.data?.count ?? 0) === 0`), `:102-109` (`toast.success` only on resolved `mutateAsync`) |
| 12 | Paginação Anterior/Seguinte + "Página X de Y" desativada nos limites; loading/erro/vazio(2 variantes)+"Limpar filtros" inline | VERIFIED | `page.tsx:227-249` (`disabled={page===0}` / `disabled={page+1>=list.data.totalPages}`, only rendered when `totalPages>1`); `:190-214` (loading/error/2 empty variants); `:202-204` ("Limpar filtros" resets all 3 state vars) |
| 13 | Gate RBAC `notificacoes:view` → `AccessDeniedState` quando ausente | VERIFIED | `page.tsx:33-47`; backend defense-in-depth `@PreAuthorize("hasAuthority('notificacoes:view')")` on all 4 endpoints (`NotificacaoController.java`); scope seeded for all 4 roles (`DatabaseSeeder.java:303,322,333,351`); frontend `KNOWN_SCOPES` includes `"notificacoes"` (`permissions.ts:13`) |
| 14 | Sem item de "Notificações" na sidebar/bottom-nav — acesso exclusivamente pelo link do sino | VERIFIED | `dashboard-shell.tsx:41-48` (`NAV` array, no notificacoes entry); `bottom-nav.tsx` grep for "notificac" → no matches |
| 15 | Guarda same-origin em `linkUrl` (`isInternalLinkUrl`) rejeita variantes de bypass conhecidas (protocol-relative, backslash, TAB/LF/CR embutidos) em ambas as superfícies | VERIFIED | `notificacao-categoria.ts:76-83` (parser-based origin check); independently re-executed as a standalone Node script (not copied from REVIEW-FIX) — 16/16 cases passed, see Behavioral Spot-Checks |

**Score:** 15/15 truths verified at the code level. (Live end-to-end/visual confirmation is tracked separately below — see Human Verification Required.)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/types/notificacoes.ts` | `Notificacao`, `NotificacaoCategoria` (9-value union), `NotificacoesListFilters`, `NotificacoesPageResponse` | VERIFIED | All 4 exports present, camelCase, no `normalize*`/`toApiPayload` (confirmed absent by inspection) |
| `web/src/lib/notificacao-categoria.ts` | `categoriaToLabel`, `categoriaToBadgeVariant`, `NOTIFICACAO_CATEGORIA_OPTIONS` | VERIFIED | Plus `isInternalLinkUrl` (added during code-review fix iterations, correctly exported and consumed by both UI surfaces) |
| `web/src/hooks/use-notificacoes.ts` | 4 hooks: list (polling opt-in), unread-count (mandatory polling), 2 mutations (prefix invalidation) | VERIFIED | All 4 present, signatures match plan contract exactly |
| `web/src/components/shared/notification-bell.tsx` | Sino rewritten: badge+dropdown+mark-read+mark-all+footer | VERIFIED | `useUpcomingEventos`/`UpcomingEvento` (old v2.1 data source) fully removed; wired into `dashboard-shell.tsx:281` (unchanged call site) |
| `web/src/app/(dashboard)/notificacoes/page.tsx` | Dedicated history page: RBAC gate, filters, rows, pagination | VERIFIED | New file, inside `(dashboard)` route group → inherits `DashboardShell` (confirmed via `web/src/app/(dashboard)/layout.tsx`); route confirmed present in `pnpm build` output |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `useNotificacoes` | `GET /notificacoes?categoria=&lida=&page=&size=` | `apiFetch` + `buildNotificacoesSearch` | WIRED | URL/params match `NotificacaoController.listar` exactly (categoria/lida/page/size, same names) |
| `useNotificacoesUnreadCount` | `GET /notificacoes/unread-count` | `apiFetch` + `refetchInterval` | WIRED | Response shape `{count}` matches `NotificacaoController.contarNaoLidas` |
| `useMarcarNotificacaoLida`/`useMarcarTodasNotificacoesLidas` | `["notificacoes"]` prefix invalidation | `queryClient.invalidateQueries` | WIRED | Confirmed both mutation hooks use identical top-level-prefix key |
| `notification-bell.tsx` (badge) | `useNotificacoesUnreadCount` | `count = data?.count` | WIRED | |
| `notification-bell.tsx` (dropdown) | `useNotificacoes({size:10},{poll:true})` | `data.content.map` | WIRED | |
| `page.tsx` (lista) | `useNotificacoes({categoria,lida,page,size:20})` | `data.content.map` (sem poll) | WIRED | |
| `page.tsx` (Check por linha) | `useMarcarNotificacaoLida.mutate(id)` | `onClick` sem navegação | WIRED | |
| `page.tsx` (select) | `NOTIFICACAO_CATEGORIA_OPTIONS` | `.map` para `<option>` | WIRED | |
| Frontend hooks (all 4) | Backend `NotificacaoController` (Phase 86) | REST contract (paths + response shapes) | WIRED | Read both sides side-by-side; byte-for-byte match on routes, params, and JSON shapes |
| `linkUrl` values (Phase 87/88 backend) | Frontend `isInternalLinkUrl` guard | relative-path convention | WIRED | All 10 backend call sites (`ResourceController`, `ParecerController`, `AlertasDiariosJob`) build `linkUrl` from a hardcoded literal prefix + id (e.g. `"/processos/" + id`), always same-origin relative — matches what the guard expects |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `notification-bell.tsx` badge | `unread.data.count` | `NotificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse` | Yes — real tenant+destinatário-scoped SQL count | FLOWING |
| `notification-bell.tsx` dropdown | `list.data.content` | `NotificacaoRepository.buscarPorFiltros` (native query, parameterized, `ORDER BY created_at DESC`) | Yes | FLOWING |
| `page.tsx` rows | `list.data.content` | Same repository method, with `categoria`/`lida` filters bound | Yes | FLOWING |
| `page.tsx` "Marcar todas" disabled state | `unreadCount.data.count` | Same unread-count query as the bell | Yes | FLOWING |

No hardcoded/static empty returns found anywhere in the request path (`NotificacaoController` → `NotificacaoRepository`/`NotificacaoService` → hooks → components).

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Frontend compiles + typechecks + generates `/notificacoes` route | `pnpm --dir web build` (run fresh by this verifier) | "Compiled successfully", full TS check passed, `/notificacoes` listed as static (`○`) route among 24 routes | PASS |
| Lint clean for the 5 phase-89 files | `pnpm --dir web lint` (run fresh) + grep raw JSON log for `notific` | 6 errors/17 warnings total, **zero** matches for any notificacoes-related file | PASS |
| `notificacoes/page.tsx` has zero lint issues (confirms REVIEW-FIX WR-02's `react-hooks/set-state-in-effect` claim) | grep `filePath` for `notificacoes/page` in eslint JSON output | No match (file entirely clean) | PASS |
| `isInternalLinkUrl` rejects all known bypass strings, accepts legit internal paths | standalone Node script, 16 cases (ordinary paths, empty/null/undefined, `//evil.com`, backslash variants, embedded TAB/LF/CR) — run from a file per the review's own documented shell-quoting pitfall | 16/16 passed | PASS |
| Backend `NotificacaoController` contract matches frontend hook expectations | Read `NotificacaoController.java` + `use-notificacoes.ts` side-by-side | Exact match on paths/params/response shapes | PASS |
| Backend `linkUrl` values are same-origin relative paths | grep across `ResourceController`/`ParecerController`/`AlertasDiariosJob` | All 10 call sites use literal `"/xxx/" + id` prefix | PASS |
| Git history contains every commit claimed across SUMMARY/REVIEW-FIX | `git show` on 8 sampled hashes (`be23341`,`f9f6224`,`a3b50d5`,`752880c`,`7557830`,`f29ca43`,`54db769`,`4ea450a`) | All found, commit messages match | PASS |
| Documented environment blocker (`MINIO_ENDPOINT`) is real, not a fabricated excuse | `grep -i minio backend/.env` | Zero matches — independently reproduces the exact blocker described in `89-04-SUMMARY.md`/`89-HUMAN-UAT.md` | CONFIRMED (legitimate blocker) |

### Probe Execution

Step 7c: SKIPPED — no `scripts/*/tests/probe-*.sh` found, and no probe scripts declared in any Phase 89 PLAN/SUMMARY. This is a UI feature phase, not a migration/tooling phase.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|---------------|--------------|--------|----------|
| NOTF-08 | 89-01, 89-02, 89-04 | Contador de não lidas com polling 30-60s | SATISFIED | `useNotificacoesUnreadCount` (30s + refocus) wired to bell badge |
| NOTF-09 | 89-02, 89-04 | Lista com link direto para a entidade | SATISFIED | Dropdown of 10, `Link` when `linkUrl` present |
| NOTF-10 | 89-01, 89-02, 89-03, 89-04 | Marcar notificação individual como lida | SATISFIED | Bell row click + page standalone `Check`, both call `useMarcarNotificacaoLida` |
| NOTF-11 | 89-01, 89-02, 89-03, 89-04 | Marcar todas de uma vez | SATISFIED | Both surfaces have "Marcar todas como lidas" wired to `useMarcarTodasNotificacoesLidas` |
| NOTF-12 | 89-03, 89-04 | Página dedicada `/notificacoes` | SATISFIED | Page exists, RBAC-gated, reachable only via bell footer link |
| NOTF-13 | 89-01, 89-03, 89-04 | Filtros por categoria e estado lida/não-lida | SATISFIED | Select + 3-way chips wired to `useNotificacoes` filters |

**Coverage:** 6/6 requirement IDs declared across the phase's plans map exactly to the 6 IDs `REQUIREMENTS.md` assigns to Phase 89 — **no orphaned requirements**.

**Bookkeeping gap (not a code gap):** `.planning/REQUIREMENTS.md` still shows all 6 IDs as unchecked (`[ ]`) and "Pending" in the Traceability table, even though the underlying capability is implemented and verified above. Each plan's SUMMARY explicitly and consistently documented this as a deliberate deferral to Plan 89-04/the orchestrator (to avoid concurrent-worktree write conflicts on a shared file), but Plan 89-04 itself also left it untouched (`requirements-completed: []`, `files_modified: []`). This is a documentation-tracking action item for the orchestrator, not a phase-goal failure — recommend flipping the 6 checkboxes and Traceability statuses to "Complete"/"Phase 89" now that this verification confirms the implementation.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | Debt markers (`TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`) | None found | Grepped all 5 phase-89 files — zero matches |
| `web/src/components/shared/notification-bell.tsx:119-129` | 119 | IN-02 (carried from REVIEW.md): clicking an already-`lida:true` row in the bell still fires `marcarLida.mutate` (no `if (!n.lida)` guard, unlike the page's equivalent) | Info | Wasted request only; backend `marcarLida` is idempotent (re-sets `lida=true`), no data corruption or incorrect counter risk. Correctly graded non-blocking by the 3-iteration code review; this verifier concurs |
| `web/src/components/shared/notification-bell.tsx:69-84` | 69 | IN-03 (carried from REVIEW.md): icon-only bell trigger button has no `aria-label` | Info | Accessibility nit (screen readers announce only "button"); does not block the phase's stated goal (sighted-user notification management) |
| `web/src/app/(dashboard)/notificacoes/page.tsx:258,268,284` | 268 | IN-01 (carried from REVIEW.md): `isInternalLinkUrl` result stored in a `boolean` first, forcing `href={linkUrl as string}` cast instead of inline narrowing | Info | Cosmetic type-narrowing style difference from the bell's equivalent; no functional impact |

All three Info items were identified and correctly graded non-blocking across 3 independent code-review iterations (`89-REVIEW.md`) before this verification; this verifier independently re-read the current file state and confirms they are still present exactly as described (i.e., the REVIEW.md narrative matches the actual code, not just its own claims).

## Human Verification Required

See YAML frontmatter `human_verification` for the full structured list (9 items harvested from `89-04-PLAN.md`'s `checkpoint:human-verify` task / `89-HUMAN-UAT.md`, plus the 2 "requires human verification" follow-ups flagged by `89-REVIEW-FIX.md` for WR-01/WR-02 — both of which this verifier already substantially de-risked via independent static/behavioral testing, noted in each item).

**Root cause of why these remain open:** `backend/.env` has no `MINIO_ENDPOINT` value in this environment, so `MinioConfig.s3Client()` throws `URISyntaxException: Illegal character in path at index 1: ${MINIO_ENDPOINT}` during Spring context startup, which prevents the entire backend (all controllers, `NotificacaoController` included) from ever becoming reachable — independently reproduced by this verifier via `grep -i minio backend/.env` (zero matches). This is identical in kind to the blocker already documented and accepted at the same checkpoint category in Phase 87 (`87-04-SUMMARY.md`) and is unrelated to any Phase 89 commit (Phase 89's entire diff is 5 files under `web/src/`; zero backend files touched).

## Gaps Summary

No code-level gaps found. All 15 merged observable truths (4 ROADMAP success criteria + 11 plan-level truths spanning data layer, sino, and página) are backed by concrete evidence: source code read directly, a fresh independent `pnpm build`/`pnpm lint` run (not copied from SUMMARY output), an independently-reproduced 16-case behavioral test of the security-relevant `isInternalLinkUrl` guard, a git-history audit confirming all claimed commits exist, and a side-by-side comparison of the frontend hooks against the already-shipped Phase 86 backend contract (routes, params, response shapes, ordering, tenant+destinatário scoping).

The phase cannot be marked `passed` only because Plan 89-04's `checkpoint:human-verify` task — a live, real-browser, real-data walkthrough — has not been executed, blocked by a pre-existing, independently-confirmed environment issue (missing `MINIO_ENDPOINT`) outside this phase's control. This is `human_needed`, not `gaps_found`: nothing here indicates the implementation is wrong: it indicates the live confirmation step is still outstanding. Once a session with working MinIO credentials can start the backend, the 9 steps in `89-HUMAN-UAT.md` should be run against real Phase 87/88-generated notifications; given the strength of the static evidence gathered here, no code changes are expected to be needed as a result.

One non-blocking administrative item for the orchestrator: `.planning/REQUIREMENTS.md`'s NOTF-08 through NOTF-13 checkboxes/traceability statuses are still "Pending" despite the implementation being verified complete — recommend flipping them to "Complete"/Phase 89 as part of closing this phase out.

---

_Verified: 2026-07-10T16:00:00Z_
_Verifier: Claude (gsd-verifier)_
