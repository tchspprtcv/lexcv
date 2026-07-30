---
phase: 118-frontend-indicador-de-utilizadores-no-limite
plan: 02
subsystem: ui
tags: [nextjs, react, tanstack-query, radix-tooltip, rbac, multi-tenant]

# Dependency graph
requires:
  - phase: 118-01
    provides: "GET /api/v1/auth/me returning tenant_plano (String|null) and tenant_limite_utilizadores (Integer|null) to every authenticated session"
provides:
  - "UserManagementTab renders an 'X/Y utilizadores' indicator (3 verbatim copy states: no-limit, under-limit, at-limit) sourced from useMe()'s tenant_limite_utilizadores + useAdminUsers()'s active count — zero new network fetches"
  - "'Novo Utilizador' is natively disabled at the limit, wrapped in this codebase's first working disabled-Button + firing-Tooltip composition (<span tabIndex={0}> as the actual TooltipTrigger target) — closes the bug class documented as debt since Phase 102/v2.13"
  - "handleFormSubmit's local error toast strips any 'API NNN: ' prefix (anchored regex), not just 'API 400: ' — the Phase 117 409 now renders as the clean backend sentence instead of 'API 409: ...'"
  - "pnpm verify:limite-utilizadores — 8-assertion Node-only structural regression gate, including span-wrapper-tooltip, which fails if a future refactor collapses the span wrapper back onto a bare TooltipTrigger asChild around a disabled Button"
affects: ["118-03 (human live-verification of this indicator)", "Phase 120 PROV-04 (UI to edit plano/limiteUtilizadores will likely touch this same CardHeader area)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Disabled Button + working Tooltip composition: <span tabIndex={0}> is the real TooltipTrigger asChild target, wrapping a natively-disabled <Button> — required because buttonVariants bakes in disabled:pointer-events-none, which silently kills a Tooltip aimed directly at a disabled Button"
    - "Node-only structural source gate (2nd instance in this repo, after verify-juizo-origem-roundtrip.mjs): reads target files as plain text, strips comments, asserts string/regex invariants — used here because no test framework exists in web/ and this fase deliberately does not introduce one"

key-files:
  created:
    - web/scripts/verify-limite-utilizadores-indicator.mjs
  modified:
    - web/src/types/auth.ts
    - web/src/app/(dashboard)/settings/page.tsx
    - web/package.json

key-decisions:
  - "useMe() called directly inside UserManagementTab (auto-fetch, TanStack Query dedupes on the shared [\"auth\",\"me\"] cache key already warmed by the parent SettingsPage's usePermissions()) instead of prop-drilling from SettingsPage — matches the sub-component's existing self-fetching convention (useAdminUsers/useAdminRbac)"
  - "Active-user count (X) uses strict u.ativo === true, mirroring the backend's countByTenantIdAndAtivoTrue exactly — deliberately different from the table's own display convention (user.ativo !== false, which treats undefined as active for the 'Ativo' badge). The gate script's contagem-estrita assertion requires both forms to coexist; 'harmonizing' them later is an intentional gate failure, not a bug"
  - "tenantUserLimit computed as me?.tenant_limite_utilizadores ?? null and compared via tenantUserLimit !== null (never a truthy/falsy check) so a real limit of 0 is not silently skipped, and an undefined/null me or limit is never coerced into '0 seats' — both are treated identically as 'sem limite'"
  - "Frontend disabled state stays UX-only: no new client-side validation was added to handleFormSubmit or handleCreateClick. The Phase 117 backend 409 (AdminController.limiteUtilizadoresExcedido, untouched by this plan) remains the sole authoritative enforcement, per CLAUDE.md's two-layer RBAC-mirroring pattern applied here to a 409 instead of a 403"
  - "Generalized the local toast's status-prefix strip from the hardcoded err.message.replace(\"API 400: \", \"\") to the anchored err.message.replace(/^API \\d{3}: /, \"\") — fixes the 409 case with a 1-line change, without touching web/src/lib/api.ts's already-generic automatic toast"

patterns-established:
  - "span-wrapper Tooltip technique for disabled controls: <Tooltip><TooltipTrigger asChild><span tabIndex={0}><Button disabled>...</Button></span></TooltipTrigger><TooltipContent>...</TooltipContent></Tooltip> — first real application in this codebase (previously only a decorative-dot precedent existed in pareceres/[id]/page.tsx); safe to copy verbatim for any future disabled-button-needs-a-tooltip case"

requirements-completed: [PLAN-03]

# Metrics
duration: ~8min
completed: 2026-07-29
---

# Phase 118 Plan 02: Frontend — Indicador de Utilizadores no Limite Summary

**`UserManagementTab` agora mostra "X/Y utilizadores" (3 estados de copy verbatim), desativa nativamente "Novo Utilizador" no limite com um tooltip que realmente dispara via a tecnica `<span tabIndex={0}>` — a primeira correção efetiva desta composição neste codebase, fechando a dívida documentada desde a Phase 102 (v2.13) — e o toast local do `409` deixa de mostrar o prefixo `API 409: `.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-07-29T03:10:14-01:00 (primeiro commit RED)
- **Completed:** 2026-07-29T03:17:31-01:00 (ultimo commit GREEN)
- **Tasks:** 2/2 completed
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments

- `MeResponse` (`web/src/types/auth.ts`) estendido com `tenant_plano?: string` e `tenant_limite_utilizadores?: number | null`, com `| null` explícito para nunca coagir "sem limite" a `0` — consome diretamente a forma JSON entregue pelo Plan 01.
- `handleFormSubmit`'s catch block generalizado de `err.message.replace("API 400: ", "")` para `err.message.replace(/^API \d{3}: /, "")` — o `409` da Phase 117 (limite atingido) agora renderiza como a frase limpa do backend em vez de `"API 409: Limite de utilizadores atingido..."`.
- `UserManagementTab` chama `useMe()` (dedupe pela cache partilhada `["auth","me"]`, sem novo pedido de rede) e deriva `activeUserCount`/`tenantUserLimit`/`atUserLimit`/`userCountLabel`, renderizando o contador nas 3 formas verbatim do UI-SPEC diretamente por cima do botão "Novo Utilizador".
- Botão "Novo Utilizador" fica nativamente `disabled` no limite, envolvido pela técnica span-wrapper (`<span tabIndex={0}>` como alvo real do `TooltipTrigger asChild`) — a primeira composição Tooltip+Button-desativado que efetivamente funciona neste codebase, em vez de continuar a ser dívida documentada.
- `pnpm verify:limite-utilizadores` — novo gate de 8 assercoes de origem (Node puro, sem dependências, no estilo de `verify-juizo-origem-roundtrip.mjs`), incluindo a assercao estrutural `span-wrapper-tooltip` que reprova especificamente a forma antiga (bug) da composição.
- Regressão completa verde: `pnpm lint` (0 erros, os mesmos 18 avisos pré-existentes, nenhum nos ficheiros tocados) e `pnpm build` (type-check TypeScript incluído) limpos em ambos os checkpoints GREEN.

## Task Commits

Cada task seguiu RED/GREEN como dois commits atómicos separados (`tdd="true"`, mesmo padrão da Phase 117/118-01):

1. **Task 1 (RED): failing gate para MeResponse + toast prefix** - `0991f6c` (test) — script novo com A1-A3, confirmadamente a falhar as 3 assercoes contra o código pré-alteração.
2. **Task 1 (GREEN): MeResponse + toast prefix genérico** - `d6c2d7f` (feat) — `web/src/types/auth.ts` + bloco `catch` de `handleFormSubmit`; 3/3 assercoes PASS, lint limpo.
3. **Task 2 (RED): failing gates para indicador X/Y + span-wrapper** - `19d4c15` (test) — extensão do script com A4-A8; as 3 assercoes da Task 1 continuam PASS, as 5 novas falham (JSX ainda não implementado).
4. **Task 2 (GREEN): indicador X/Y + botão desativado com tooltip funcional** - `df234ec` (feat) — `CardHeader` do `UserManagementTab` reescrito; 8/8 assercoes PASS, lint limpo, build limpo.

**Plan metadata:** committed separately after este SUMMARY (ver commit final).

## Files Created/Modified

- `web/scripts/verify-limite-utilizadores-indicator.mjs` (created) — gate executável de 8 assercoes de origem, Node puro sem dependências.
- `web/package.json` (modified) — 1 linha nova em `scripts`: `"verify:limite-utilizadores": "node scripts/verify-limite-utilizadores-indicator.mjs"`; zero alterações a `dependencies`/`devDependencies`.
- `web/src/types/auth.ts` (modified) — `MeResponse` ganha `tenant_plano?: string` e `tenant_limite_utilizadores?: number | null`.
- `web/src/app/(dashboard)/settings/page.tsx` (modified) — import de `useMe`; derivações `activeUserCount`/`tenantUserLimit`/`atUserLimit`/`userCountLabel` no corpo do `UserManagementTab`; `CardHeader` reescrito com o contador + botão condicionalmente desativado/tooltip; regex genérico no `catch` de `handleFormSubmit`.

## Forma final do `CardHeader` (`UserManagementTab`)

```tsx
<CardHeader className="flex flex-row items-center justify-between space-y-0">
  <div>
    <CardTitle className="text-xl font-semibold">Utilizadores Registados</CardTitle>
    <CardDescription>
      Lista de profissionais com credenciais de acesso ao sistema LexCV.
    </CardDescription>
  </div>
  <div className="flex flex-col items-end gap-2">
    <span
      className={
        atUserLimit
          ? "text-xs font-semibold text-red-600 dark:text-red-400"
          : "text-xs text-slate-500 dark:text-slate-400"
      }
    >
      {userCountLabel}
    </span>
    {atUserLimit ? (
      <Tooltip>
        <TooltipTrigger asChild>
          <span tabIndex={0}>
            <Button
              disabled
              className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
            >
              <Plus className="h-4 w-4" />
              Novo Utilizador
            </Button>
          </span>
        </TooltipTrigger>
        <TooltipContent>
          Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga.
        </TooltipContent>
      </Tooltip>
    ) : (
      <Button
        onClick={handleCreateClick}
        className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
      >
        <Plus className="h-4 w-4" />
        Novo Utilizador
      </Button>
    )}
  </div>
</CardHeader>
```

## Técnica span-wrapper: primeira aplicação efetiva neste codebase

`.planning/PROJECT.md`'s v2.13 decision log (Phase 102) regista `Tooltip` sobre um botão `disabled` como dívida documentada, deliberadamente não corrigida na altura — `buttonVariants` (`web/src/components/ui/button.tsx:8`) embute `disabled:pointer-events-none`, o que torna um `TooltipTrigger asChild` diretamente sobre um `<Button disabled>` num tooltip morto e silencioso (nenhum erro, apenas nunca dispara). Este plano é a primeira vez que essa composição é efetivamente corrigida em vez de adiada: o `<span tabIndex={0}>` que envolve o `<Button disabled>` é o alvo real do `TooltipTrigger`, herdando o único precedente parcial existente (`web/src/app/(dashboard)/pareceres/[id]/page.tsx:302-311`, que usava a mesma técnica de span focável mas para um ponto decorativo, não para um botão).

**Bug idêntico ainda por corrigir, fora de âmbito desta fase (confirmado por `118-CONTEXT.md`/`118-PATTERNS.md`):** `web/src/components/profile/user-password-form.tsx:62` tem o mesmo padrão `err.message.replace("API 400: ", "")` hardcoded — evidência de que é um padrão copiado, não um caso isolado. Candidato a limpeza futura; não tocado por este plano por estar explicitamente fora do ficheiro-alvo declarado em `118-CONTEXT.md`.

## Decisions Made

Ver `key-decisions` no frontmatter. Nenhuma decisão desviou do que `118-CONTEXT.md`/`118-UI-SPEC.md`/`118-PATTERNS.md` já tinham fixado — este plano foi execução direta de um contrato já totalmente especificado.

## Deviations from Plan

None — plan executado exatamente como escrito. Todos os gates de aceitação (8 assercoes do script, contagens `grep -c`, `git diff --name-only` dos ficheiros proibidos, lint, build) passaram após a implementação, sem necessidade de correções ao abrigo das Regras 1-3.

## Issues Encountered

Um único ajuste interno, apanhado pelo próprio processo de verificação antes de qualquer commit (não uma correção pós-facto): o comentário explicativo adicionado junto às derivações do `UserManagementTab` citava inicialmente o literal `user.ativo !== false` como referência de código, o que fazia `grep -c 'user.ativo !== false' settings/page.tsx` devolver `2` em vez do `1` exigido pelo critério de aceitação da Task 2 (a linha real da tabela). Reescrito para descrever a convenção em prosa sem repetir o literal exato, confirmado `grep -c` = 1 antes do commit GREEN. Nenhum impacto no código funcional — o comentário nunca chegou a ser commitado na forma incorreta.

## User Setup Required

None — nenhuma configuração de serviço externo necessária. Nenhuma variável de ambiente nova, nenhuma migração nova.

## Next Phase Readiness

- **118-03** (verificação humana ao vivo, último plan da Phase 118) pode avançar: os 3 estados do contador, o tooltip por rato e por teclado sobre o botão desativado, e o toast do `409` forçado estão todos implementados e providos por um gate automatizado verde — falta apenas a confirmação visual/interativa ao vivo que só um humano pode dar.
- Nenhum bloqueio. `web/src/lib/api.ts`, `web/src/hooks/use-me.ts`, `web/src/components/ui/button.tsx`, `web/src/components/ui/tooltip.tsx`, `web/src/app/providers.tsx` e `web/src/components/profile/user-password-form.tsx` confirmados fora do diff deste plano (ver Self-Check).
- Phase 120 (PROV-04, UI para editar `plano`/`limiteUtilizadores`) provavelmente vai tocar na mesma zona do `CardHeader` — nenhuma ação necessária agora, apenas uma nota de proximidade de código para o planeamento futuro.

## STRIDE Mitigation Verdicts

Por `118-02-PLAN.md`'s `<threat_model>`, verificado por asserção direta contra o código entregue:

- **T-118-06 (Elevation of Privilege — botão desativado no cliente): CONFIRMADO.** `handleCreateClick` está inalterado; nenhuma validação nova foi acrescentada a `handleFormSubmit` que bloqueie a chamada à API. O `disabled` é puramente um espelho de UX — removê-lo em devtools reabre o formulário local, mas `POST /api/v1/admin/users` continua a devolver `409` via `AdminController.limiteUtilizadoresExcedido` (Phase 117, fora do diff deste plano), agora apresentado como toast limpo graças à Task 1.
- **T-118-07 (Information Disclosure — renderização de `tenant_plano`/`tenant_limite_utilizadores`): CONFIRMADO.** A superfície de renderização continua confinada ao `UserManagementTab`, alcançável apenas através do separador "Utilizadores" já gated por `hasUsersManage` (`users:manage`/ADMIN) no `SettingsPage` pai — este plano não introduz nenhum gate novo nem o remove; a exposição de dados em si já tinha sido avaliada e aceite no Plan 01.
- **T-118-08 (Injection/XSS — texto do contador, tooltip e toast): CONFIRMADO.** Todas as strings novas são literais estáticas ou inteiros derivados de `users.length`/de um campo JSON numérico, renderizados via JSX (escape automático do React); zero `dangerouslySetInnerHTML` introduzido. O `replace` novo é ancorado (`^API \d{3}: `) e só remove um prefixo de formato conhecido.
- **T-118-09 (Tampering — regressão silenciosa da técnica span-wrapper): CONFIRMADO.** A assercao `span-wrapper-tooltip` do gate (`pnpm verify:limite-utilizadores`) valida estruturalmente as 3 condições (adjacência `TooltipTrigger asChild` → `<span tabIndex={0}>`, presença de `disabled` no mesmo bloco `<Tooltip>`, e `tabIndex={0}` antes de `disabled`) — um refactor futuro que remova o wrapper falha este gate antes de chegar a produção.
- **T-118-10 (Spoofing — estado do cliente desatualizado, duas abas abertas): CONFIRMADO.** Nenhum polling ou invalidação adicional foi introduzido (`useMe()` mantém o `staleTime: 60_000` já existente); o cenário de pior caso continua a ser um `409` do backend, agora exibido de forma clara pela correção da Task 1.
- **T-118-SC (Tampering — cadeia de fornecimento npm/pnpm): CONFIRMADO.** `git diff b989d9d HEAD -- web/package.json` mostra exatamente 1 linha acrescentada em `scripts` (`verify:limite-utilizadores`, Node puro, sem dependência associada) e zero alterações a `dependencies`/`devDependencies`. Gate de legitimidade de pacotes não se aplica.

## Verification Gate Results

| Gate | Result |
|------|--------|
| `pnpm verify:limite-utilizadores` (final) | 8/8 assercoes PASS, exit 0 |
| `pnpm lint` (final) | 0 erros, 18 avisos pré-existentes inalterados (nenhum nos ficheiros tocados), exit 0 |
| `pnpm build` (final, inclui type-check) | BUILD SUCCESS, 24 rotas geradas, exit 0 |
| `git diff --name-only` (118-01 tip → HEAD) | exatamente os 4 ficheiros de `files_modified` — `web/package.json`, `web/scripts/verify-limite-utilizadores-indicator.mjs`, `web/src/app/(dashboard)/settings/page.tsx`, `web/src/types/auth.ts` |
| `git diff --name-only` — ficheiros proibidos (`api.ts`, `use-me.ts`, `user-password-form.tsx`, `button.tsx`, `tooltip.tsx`, `providers.tsx`) | zero linhas |
| `grep -c` das 6 assercoes de origem (Task 1+2 acceptance criteria) | todas com a contagem exata exigida (1 ou 2, conforme o critério) |

---
*Phase: 118-frontend-indicador-de-utilizadores-no-limite*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `web/scripts/verify-limite-utilizadores-indicator.mjs`
- FOUND: `web/src/types/auth.ts`
- FOUND: `web/src/app/(dashboard)/settings/page.tsx`
- FOUND: `web/package.json`
- FOUND: `.planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-02-SUMMARY.md`
- FOUND commit: `0991f6c` (test — RED, Task 1)
- FOUND commit: `d6c2d7f` (feat — GREEN, Task 1)
- FOUND commit: `19d4c15` (test — RED, Task 2)
- FOUND commit: `df234ec` (feat — GREEN, Task 2)
