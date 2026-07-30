---
phase: 122-relat-rio-de-utiliza-o-por-tenant
verified: 2026-07-30T09:51:22Z
status: passed (override aplicado — ver overrides abaixo)
score: 3/3 ROADMAP success criteria structurally verified (source + tests + gates + build + partial live HTTP, all independently re-derived this session) — 6/6 live-browser UAT scenarios (H1-H6) remain unconfirmed, blocked by a disclosed Browser-MCP tooling failure, not a product defect; risco residual aceite explicitamente pelo utilizador (ver overrides)
overrides_applied: 2
overrides:
  - success_criterion: 1
    must_have: "H1/H2 — navegação por clique real e renderização visual dos 4 campos/cores de Badge"
    reason: "Bloqueio de ferramenta (Browser MCP 'pane hidden/not displayed'), não do produto — confirmado por esforço extensivo de diagnóstico (reinícios de servidor, tabs novas, navegação direta, chamadas sequenciais) e por saúde confirmada do backend/frontend via curl direto durante a mesma janela. Evidência estrutural convergente: build de produção regista a rota, gate estrutural 15/15 (incl. reverificação pós-correção WR-01/WR-02), guarda de página byte-idêntica ao padrão já validado ao vivo na Fase 120."
    accepted_by: "utilizador (tchspprtcv)"
    accepted_at: "2026-07-30"
  - success_criterion: 3
    must_have: "H3/H4/H6 — Badge 'Suspenso' visível sob pesquisa, contagem visível em mobile, ausência de flash de conteúdo protegido"
    reason: "Mesmo bloqueio de ferramenta. Sub-reivindicação de dados (tenant suspenso continua na resposta da API, contagem correta) já confirmada ao vivo por HTTP real (A1-A3), incluindo deteção e correção de uma regressão real (migração pendente). Só a renderização visual/timing específicos ficam por observar diretamente."
    accepted_by: "utilizador (tchspprtcv)"
    accepted_at: "2026-07-30"
re_verification: false
human_verification:
  - test: "Como PLATAFORMA_ADMIN, clicar 'Ver Relatório' no CardHeader de /plataforma e confirmar que a navegação chega a /plataforma/relatorio num único clique"
    expected: "A rota resolve, sem erro de hidratação/consola, e mostra o ecrã do relatório"
    why_human: "Rendering/click-through real de Next.js em browser — nenhum gate de origem ou teste unitário pode provar que o Link efetivamente navega e que a rota resolve no cliente"
  - test: "Confirmar visualmente que a tabela/desktop e os cards mobile mostram os 4 campos (nome, plano, limite, utilizadores ativos) com os Badges de cor esperada (plano cinza/roxo/âmbar, estado verde/vermelho)"
    expected: "Os 4 campos e as cores aparecem exatamente como o código descreve"
    why_human: "Aparência visual (cor, layout) não é verificável por leitura de código ou grep — só uma renderização real no browser confirma"
  - test: "Comparar o número de 'utilizadores ativos' do mesmo tenant entre /plataforma e /plataforma/relatorio, lado a lado"
    expected: "Os dois ecrãs mostram exatamente o mesmo número para o mesmo tenant"
    why_human: "Coerência cruzada entre 2 ecrãs renderizados é uma verificação visual/comportamental, não estrutural — embora ambos leiam o mesmo campo de API (verificado por código), só a observação ao vivo fecha o ciclo"
  - test: "Suspender um tenant (ou usar um já suspenso) e confirmar que continua visível no relatório, com Badge 'Suspenso', inclusive sob um termo de pesquisa"
    expected: "O tenant suspenso aparece na lista/tabela filtrada, com o Badge vermelho 'Suspenso'"
    why_human: "A. 1-A3 (HTTP) já confirmou que a API devolve o tenant suspenso; o que falta é confirmar visualmente que o Badge é renderizado e que a pesquisa em texto não o esconde"
  - test: "Reduzir a viewport para <768px e confirmar que a contagem de utilizadores ativos continua visível nos cards mobile, sem cortes de layout"
    expected: "Cada card mobile mostra a linha de utilizadores/limite tal como no desktop"
    why_human: "Layout responsivo é uma verificação visual — o código inclui o bloco (confirmado por leitura e pelo gate), mas o resultado visual real não foi observado"
  - test: "Autenticar como ADMIN de escritório (não PLATAFORMA_ADMIN), navegar diretamente para /plataforma/relatorio e confirmar (a) AccessDeniedState aparece sem qualquer flash prévio da tabela/dados, e (b) a navegação lateral desse utilizador continua a ter apenas 1 entrada de plataforma"
    expected: "Nenhum flash de conteúdo protegido; zero itens de navegação lateral adicionais para o relatório"
    why_human: "Timing de renderização (ausência de flash) é uma classe de reivindicação que só observação ao vivo pode confirmar; a ausência de item de nav já está confirmada por grep (dashboard-shell.tsx, 0 ocorrências), mas o próprio HUMAN-UAT.md desta fase lista isto como um cenário humano dedicado (H6), não fechado à parte"
---

# Phase 122: Relatório de Utilização por Tenant Verification Report

**Phase Goal:** O administrador de plataforma consulta um relatório interno, por tenant, com nome/plano/limite contratado/utilizadores ativos agora — a base factual para emitir a fatura manual de cada escritório.
**Verified:** 2026-07-30T09:51:22Z
**Status:** passed (override aplicado)
**Re-verification:** No — initial verification

**Nota de override (adicionada após este relatório, mesma data):** Este relatório foi produzido com veredito `human_needed`, identificando corretamente que os critérios de sucesso 1 e 3 tinham sub-reivindicações visuais (H1-H6) por confirmar ao vivo, bloqueadas por uma falha de ferramenta (Browser MCP), e recomendou explicitamente que a decisão de aceitar a evidência disponível fosse feita conscientemente por um humano, formalizada como uma entrada `overrides:`. O utilizador reviu esta situação e instruiu diretamente: "aceite as evidencias e continue" — uma autorização explícita e datada. Essa instrução foi agora formalizada como as 2 entradas em `overrides:` no frontmatter acima, ligadas aos critérios de sucesso 1 e 3 especificamente. O corpo deste relatório permanece inalterado desde a sua produção original — reflete com precisão o que foi e não foi confirmado ao vivo; apenas o veredito final e o frontmatter foram atualizados para refletir a decisão do utilizador.

**Adversarial stance applied:** started from the hypothesis that "4 plans complete, UTIL-01 closed, gates green" was executor self-reporting, not proof, and that the phase's own documented tooling blocker (Browser MCP, H1-H6 not run) was being papered over. Did not take `122-01..04-SUMMARY.md`, `122-REVIEW.md`, or `122-HUMAN-UAT.md` on their word — independently re-read every file this phase touched (`relatorio/page.tsx`, `relatorio/columns.tsx`, `plataforma/page.tsx`, `verify-relatorio-utilizacao.mjs`, `PlatformAdminControllerTest.java`'s new test, `PlatformAdminController.java`, `UserRepository.java`, `Tenant.java`, `TenantAdminSummaryResponse`-equivalent DTO wiring, `use-platform-admin.ts`, `dashboard-shell.tsx`) line-by-line; independently re-ran the full backend suite, the phase's own dedicated test class, SpotBugs, `pnpm lint`, `pnpm build`, and the phase's own 15-assertion structural gate, all fresh in this session rather than trusting the numbers already printed earlier in the session; independently confirmed the exact commit-scoped diff (`git diff --stat f18bd049~1..HEAD`) matches the union of all 4 plans' declared file scope with zero backend production-code changes and zero scope creep; independently re-derived the "single source of truth" claim for `utilizadoresAtivos` by grepping the entire backend for every call site of `countByTenantIdAndAtivoTrue` (exactly 2, both tracing to the same repository method); and specifically hunted for the failure mode this project's own history flags (a gate assertion that passes for the wrong reason) — found it already caught and fixed by this phase's own review (WR-01/WR-02, commit `66bfac72`), confirmed the fix is real by reading the current gate source, not just the review's narrative. Per this task's explicit instruction not to soften the call on the missing live-visual UAT, this report treats the 6 unconfirmed browser scenarios (H1-H6) as a genuine, unresolved gap in confirmation depth — not waved away by the strength of the indirect evidence — and lands on `human_needed` rather than `passed`, consistent with how this same project's own Phase 120/121 verifications only reached `passed` once every browser-only claim had actually been exercised live.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — authoritative contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Um ecrã de relatório, acessível só a `PLATAFORMA_ADMIN`, mostra na interface, para cada tenant: nome, plano, limite de utilizadores contratado, e utilizadores ativos neste momento | ✓ VERIFIED (structural + partial live) — **visual rendering sub-claim not live-confirmed, see Human Verification** | **Access control:** `PlatformAdminController.java:53` `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` at class level (unchanged by this phase, confirmed by direct reading) gates `GET /platform/tenants`, the sole data source for this screen. `listTenants_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios` (`PlatformAdminControllerTest.java:519-526`, read in full) proves via a **real Spring AOP `ProxyFactory`** (not a mock) that a `ROLE_ADMIN`-only caller is denied `AccessDeniedException` before touching any repository (`verify(tenantRepository, never()).findAll()`). Independently re-ran fresh: `mvn test -Dtest=PlatformAdminControllerTest` → `Tests run: 27, Failures: 0, Errors: 0`. Live-confirmed beyond the unit-test proxy in `122-HUMAN-UAT.md` (A1/A2, this session): `plataforma@lexcv.cv` → `200`, `admin@lexcv.cv` (separate cookie jar) → exactly `403`, not `200`/`500`. Frontend mirrors this with `relatorio/page.tsx:38-52`: `if (!me.isFetched) return null;` resolves before the role check (`!me.data?.roles?.includes("PLATAFORMA_ADMIN")` → `AccessDeniedState`), byte-identical guard order to `/plataforma`'s already-shipped, already-live-UAT'd WR-03 fix (Phase 120). **4-field display:** `relatorio/columns.tsx` (119 lines, read in full) defines exactly 4 `ColumnDef`s — `nome`, `plano`, `utilizadores` (shows `utilizadoresAtivos`/`limiteUtilizadores` as "X/Y" or "X · sem limite"), `estado` (Ativo/Suspenso Badge) — and 0 `acoes` column; `relatorio/page.tsx` (192 lines, read in full) renders the same 4 fields again in the mobile-card branch. Independently re-ran `node scripts/verify-relatorio-utilizacao.mjs`: **15/15 PASS**, exit 0. Independently re-ran `pnpm build`: `✓ Compiled successfully`, TypeScript clean, and the build's own route table lists `○ /plataforma/relatorio` as a registered static route (not just that the files parse). **What is NOT yet confirmed:** that this actually renders correctly, with correct colors/layout, in a real browser, and that a real click from `/plataforma` navigates there — H1/H2 in Human Verification below. |
| 2 | Os números apresentados usam a mesma contagem de "utilizador ativo" da Phase 117 (`ativo=true`) — uma única fonte de verdade, nunca um cálculo paralelo | ✓ VERIFIED | Grepped the entire backend for every call site of `countByTenantIdAndAtivoTrue`: exactly **2** exist in the whole codebase — `AdminController.java:122` (Phase 117/118's "X/Y utilizadores" indicator) and `PlatformAdminController.java:198` inside `toSummary(Tenant)` (used by both `/plataforma` (Phase 120) and `/plataforma/relatorio` (this phase) — the same DTO, the same method, the same query). `UserRepository.java:38` declares this as the sole derived-query method of its kind (no `@Query`, no competing aggregate). On the frontend, `relatorio/columns.tsx:79` (`accessorFn: (tenant) => tenant.utilizadoresAtivos`) and `relatorio/page.tsx:121,167` read `tenant.utilizadoresAtivos` directly from the API response — zero `.reduce`/`.filter`/`.map`/`.length` recomputation anywhere in either file (confirmed by direct reading and by gate assertion `utilizadores-sem-recalculo`, which isolates the exact column-def block and asserts no aggregation verbs appear in it — re-ran fresh, PASS). This is a pure data-provenance claim, not a rendering claim — it does not depend on live browser confirmation, and is fully closed by source-level evidence. |
| 3 | Tenants suspensos (Phase 120) continuam visíveis no relatório com o seu estado identificado, em vez de desaparecerem da lista | ✓ VERIFIED (structural + live HTTP) — **visual Badge rendering sub-claim not live-confirmed, see Human Verification** | `PlatformAdminController.listTenants()` (`:106-113`, read directly): `tenantRepository.findAll().stream().map(this::toSummary).sorted(...).collect(...)` — **zero `.filter()`** anywhere in the pipeline, confirmed by direct reading (doc-comment at `:100-101` states this is deliberate: "Deliberadamente sem filtro de tenant"). `listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta` (`PlatformAdminControllerTest.java:333-356`, read in full) proves this server-side: 2 fixtures (one `.ativo(true)`, one `.ativo(false)`), asserts `corpo.size() == 2` and the suspended one's `getAtivo()` is `false` in the response body — this is the **first** of the file's `listTenants_*` tests to use a `.ativo(false)` fixture (the 3 pre-existing ones all used `.ativo(true)` only), closing a genuine, previously-unproven regression-coverage gap. Independently re-ran: PASS (part of the 27/27 `PlatformAdminControllerTest` run above). On the frontend, `relatorio/page.tsx`'s `tenantsFiltrados` memo (`:60-65`) filters **only** by `t.nome.toLowerCase().includes(termo)` — confirmed by direct reading, no `ativo`/`suspenso` condition anywhere in the memo, matching gate assertion `relatorio-sem-filtro-de-estado` (re-ran, PASS). The `estado` column (`columns.tsx:110-118`) and the mobile-card equivalent (`page.tsx:151-156`) render `Badge variant={ativo ? "green" : "red"}` with text "Ativo"/"Suspenso" directly from `tenant.ativo`, no intermediate transformation. **Live-confirmed at the HTTP layer** in `122-HUMAN-UAT.md` (A3, this session, with a genuine unplanned finding — see Anti-Patterns/Gaps): after fixing a real pending-migration regression, `PATCH .../ativo {"ativo":false}` → `200`, and the subsequent `GET /platform/tenants` still returned **2** tenants, the suspended one with `"ativo":false` and the same `id` — not silently dropped. **What is NOT yet confirmed:** that the red "Suspenso" Badge actually renders as expected in a real browser, and that it remains visible under an active search term typed into the real UI — H3 in Human Verification below. |

**Score:** 3/3 ROADMAP success criteria structurally verified via independent source re-reading, independently re-run tests/gates/build, and (for criteria 1 and 3) partial live-HTTP confirmation. Criterion 2 is fully closed (a data-provenance claim, not a rendering claim). Criteria 1 and 3 each carry one sub-claim — real visual rendering — that no source-level gate can prove and that has not yet been observed live; see **Human Verification Required** and the status determination below.

### Plan-Level Must-Have Truths (supporting detail, 23 total across 4 plans)

**Plan 01 (report route + columns) — 7/7 structurally verified**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| Route shows nome/plano/limite/utilizadores per tenant | ✓ VERIFIED | `relatorio/columns.tsx` 4 cells, read in full; route registered in `pnpm build`'s table |
| Non-`PLATAFORMA_ADMIN` sees `AccessDeniedState`, never the list | ✓ VERIFIED (structural) | `page.tsx:44-51`; not live-clicked (H6/access-denied scenario) |
| Guard fails closed before `useMe()` resolves — no premature fetch | ✓ VERIFIED | `page.tsx:40-42`, `!me.isFetched` returns `null` before `RelatorioUtilizacaoContent` (which alone calls `useTenantsAdmin()`) ever mounts |
| `utilizadoresAtivos` read directly, no client recalculation | ✓ VERIFIED | `columns.tsx:79`, gate assertion `utilizadores-sem-recalculo` re-run PASS |
| Suspended tenant keeps a row with a "Suspenso" Badge, no filter removes it | ✓ VERIFIED (structural + HTTP) | `columns.tsx:110-118`; regression test; A3 live HTTP |
| Utilizadores count visible on mobile, not just desktop | ✓ VERIFIED (structural) | `page.tsx:158-175` mobile block includes the same figure; not visually observed |
| Zero mutation actions on this screen | ✓ VERIFIED | Full-file read of both new files: no `useCreateTenant`/`useUpdateTenant`/`useSetTenantAtivo`/`mutateAsync`/`AlertDialog`/CSV; gate assertion `relatorio-sem-mutacoes` re-run PASS |

**Plan 02 (backend regression guard) — 4/4 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| Automated test proves `ativo=false` tenant appears in `GET /platform/tenants`'s body | ✓ VERIFIED | `PlatformAdminControllerTest.java:333-356`, read in full; re-ran, PASS |
| Test passes against unmodified production code (regression guard, not a fix) | ✓ VERIFIED | `git diff --stat f18bd049~1..HEAD -- backend` shows exactly 1 backend file changed, the test itself; `PlatformAdminController.java`/`toSummary` untouched |
| A future filter regression would fail this suite | ✓ VERIFIED (by construction) | Test asserts `corpo.size() == 2` and the suspended entry's `getAtivo()==false` — a `.filter(Tenant::getAtivo)` added later would break both assertions |
| Zero backend production files touched by this phase | ✓ VERIFIED | Same `git diff --stat` — only `PlatformAdminControllerTest.java` (+37) on the backend side, whole phase |

**Plan 03 (entry link + structural gate) — 6/6 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| "Ver Relatório" button reaches the report in one click (structurally) | ✓ VERIFIED (structural) | `plataforma/page.tsx:169-174`, `<Link href="/plataforma/relatorio">`; real click-through not live-tested (H1) |
| Button sits left of "Criar Tenant", outline variant, doesn't compete with the CTA | ✓ VERIFIED | `page.tsx:169`, `variant="outline"`; gate assertion `entrada-ver-relatorio` (post-WR-01-fix, isolates to the correct `<Button>` block), re-ran PASS |
| "Criar Tenant" stays literally unchanged (handler, classes, icon) | ✓ VERIFIED | `page.tsx:175-181`, `onClick={() => setIsFormOpen(true)}` appears exactly once, `bg-blue-600 hover:bg-blue-700 text-white` intact; gate assertion `entrada-ordem-e-criar-tenant-intocado` (post-WR-02-fix), re-ran PASS |
| No permanent sidebar nav item added | ✓ VERIFIED | `grep -n "plataforma/relatorio" dashboard-shell.tsx` → 0 matches (independently re-run); gate assertion `sem-segundo-item-de-nav` PASS |
| Executable gate proves 15 structural properties | ✓ VERIFIED | `web/scripts/verify-relatorio-utilizacao.mjs` (331 lines, read in full); independently re-ran: 15/15 PASS, exit 0 |
| Gate fails precisely on injected regressions | ✓ VERIFIED (by review + my own re-read) | `122-03-SUMMARY.md`'s 3 negative-proof regressions each isolated exactly 1/15 to FAIL (not independently re-injected by me, but the gate's current isolation logic — reading `verify-relatorio-utilizacao.mjs:239-290` directly — genuinely slices to the specific `<Button>`/`</Button>` block rather than testing the whole file, which is the mechanism that makes this claim true; this is the exact fix applied in commit `66bfac72` in response to `122-REVIEW.md`'s WR-01/WR-02, confirmed present in the current file) |

**Plan 04 (live UAT, partial) — 1/6 truths live-confirmed, 5/6 not yet confirmed live**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `GET /platform/tenants` → `200` for `PLATAFORMA_ADMIN`, `403` for tenant `ADMIN`, proved by real HTTP | ✓ CONFIRMADO (live) | `122-HUMAN-UAT.md` A1/A2: `200` / `403` against the real running backend, separate cookie jars |
| `/plataforma/relatorio` renders in-browser and shows the 4 fields | ✗ NÃO VERIFICADO | Browser-MCP tooling blocker (see below); not fabricated as PASS |
| Utilizadores count matches between `/plataforma` and `/plataforma/relatorio` for the same tenant | ✗ NÃO VERIFICADO | Same blocker — both screens read the same API field (verified by code), but cross-screen visual comparison wasn't observed |
| A live-suspended tenant keeps a row with "Suspenso" Badge | ⚠ PARTIALLY CONFIRMADO | A3 (HTTP-level) confirmed via `122-HUMAN-UAT.md`, including discovering+fixing a real regression (see Gaps); the Badge's actual on-screen rendering was not observed |
| Utilizadores count stays visible below 768px | ✗ NÃO VERIFICADO | Same blocker |
| Platform-admin sidebar nav still has exactly one entry | ✗ NÃO VERIFICADO (partially covered by static grep) | `dashboard-shell.tsx` grep confirms 0 references to the report route; live on-screen confirmation not observed |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx` | Static 4-column array (nome/plano/utilizadores/estado), no actions column | ✓ VERIFIED | 119 lines, read in full; `pnpm build`/`tsc --noEmit` clean |
| `web/src/app/(dashboard)/plataforma/relatorio/page.tsx` | `/plataforma/relatorio` route, `PLATAFORMA_ADMIN`-gated, read-only | ✓ VERIFIED | 192 lines, read in full; registered as `○ /plataforma/relatorio` in `pnpm build`'s route table |
| `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` | New `listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta` test, zero production changes | ✓ VERIFIED | Test present (`:333-356`), read in full; `mvn test -Dtest=PlatformAdminControllerTest` → 27/27 fresh |
| `web/src/app/(dashboard)/plataforma/page.tsx` | "Ver Relatório" entry link in `CardHeader`, "Criar Tenant" untouched | ✓ VERIFIED | `:163-183`, read in full |
| `web/scripts/verify-relatorio-utilizacao.mjs` | 15-assertion Node-only structural gate | ✓ VERIFIED | 331 lines, read in full; re-ran fresh: 15/15 PASS, exit 0 |
| `web/package.json` | `verify:relatorio-utilizacao` script entry | ✓ VERIFIED | Present; ran via `node scripts/verify-relatorio-utilizacao.mjs` directly and confirmed the npm-script wiring exists |
| `.planning/phases/LEXCV-122-.../122-HUMAN-UAT.md` | Live UAT record, honest about what wasn't confirmed | ✓ VERIFIED | 83 lines; documents A1-A3 CONFIRMADO with real HTTP bodies, H1-H6 explicitly NÃO VERIFICADO with a dated, reasoned user decision to accept and proceed |
| `.planning/phases/LEXCV-122-.../122-REVIEW.md` | Deep code review, fix verification | ✓ VERIFIED | 125 lines; 0 Critical, 2 Warnings (both resolved + reverified in Round 2 with a negative-proof correction), 3 Info; Final Verdict APROVADA |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `relatorio/page.tsx` | `useTenantsAdmin()` → `GET /api/v1/platform/tenants` | `import { useTenantsAdmin } from "@/hooks/use-platform-admin"`, unmodified hook | ✓ WIRED | `page.tsx:14,58`; hook body unchanged (`use-platform-admin.ts`, read in full) |
| `relatorio/page.tsx` | `relatorioColumns` | `import { relatorioColumns } from "./columns"`, passed directly (no factory call) | ✓ WIRED | `page.tsx:18,184`: `<DataTable columns={relatorioColumns} data={tenantsFiltrados} .../>` |
| `relatorio/columns.tsx` | `TENANT_RESERVADO` | `import { TENANT_RESERVADO } from "../columns"` (not redeclared) | ✓ WIRED | `columns.tsx:10`; confirmed no local `= "LexCV"` literal in the file |
| `relatorio/columns.tsx` | `tenant.utilizadoresAtivos` | Direct field read via `accessorFn`, no aggregation | ✓ WIRED | `columns.tsx:79`; gate assertion `utilizadores-sem-recalculo` PASS |
| `PlatformAdminController.toSummary()` | `UserRepository.countByTenantIdAndAtivoTrue` | Direct method call, same method Phase 117/118 use | ✓ WIRED | `PlatformAdminController.java:198`; only 2 call sites in the whole backend |
| `plataforma/page.tsx` | `/plataforma/relatorio` | `next/link` `<Link href="/plataforma/relatorio">` inside a `Button asChild` | ✓ WIRED (structural) | `page.tsx:170-173`; route confirmed registered in `pnpm build`; **real click-through not live-tested** |
| `web/package.json` | `web/scripts/verify-relatorio-utilizacao.mjs` | `"verify:relatorio-utilizacao": "node scripts/verify-relatorio-utilizacao.mjs"` | ✓ WIRED | Independently re-ran via direct `node` invocation, 15/15 PASS |
| `dashboard-shell.tsx` | *(nothing, deliberately)* | No new nav entry added | ✓ CONFIRMED-ABSENT (by design) | `grep -c "plataforma/relatorio" dashboard-shell.tsx` → 0, re-run this session |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `relatorio/columns.tsx`'s `utilizadores` cell / `relatorio/page.tsx`'s mobile line | `tenant.utilizadoresAtivos` | `useTenantsAdmin()` → `GET /platform/tenants` → `PlatformAdminController.toSummary()` → `userRepository.countByTenantIdAndAtivoTrue(tenant.getId())` (real JPA derived query, no static/mock value) | Yes — genuine DB-backed count; live-confirmed non-static in `122-HUMAN-UAT.md` A1 (`"utilizadoresAtivos":5` and `:1` for 2 different real tenants, not both zero/placeholder) | ✓ FLOWING |
| `relatorio/columns.tsx`'s `estado` cell | `tenant.ativo` | Same `GET /platform/tenants` response, `toSummary()` → `tenant.getAtivo()`, no transformation | Yes — live-confirmed to flip `true`→`false`→`true` correctly across the A3 suspend/reactivate round-trip in `122-HUMAN-UAT.md` | ✓ FLOWING |
| `relatorio/page.tsx`'s access guard | `me.data?.roles` | `useMe()` → `GET /auth/me`, shared query cache `["auth","me"]` with `/plataforma`, unmodified by this phase | Yes — same JWT/DB-backed role source already live-proven in Phases 120/121 | ✓ FLOWING |
| `relatorio/page.tsx`'s `tenantsFiltrados` | `tenants.data` filtered by `searchTerm` | `useTenantsAdmin()`'s query result, filtered client-side only by `nome` | Yes — no `ativo` condition anywhere in the filter, confirmed by direct reading | ✓ FLOWING |

### Behavioral Spot-Checks

All checks below were independently re-executed in this verification session (not copied from any prior report). Per this workflow's constraint against starting servers/services during spot-checks, the backend/frontend dev servers (already stopped since `122-04`'s session) were **not** restarted by this verifier; the A1-A3 HTTP battery already recorded in `122-HUMAN-UAT.md` (with real request/response bodies) is accepted as the live-HTTP evidence layer, cross-checked here against the actual authorization/data-shape source code rather than re-executed blindly.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| New backend regression test, isolated | `mvn test -Dtest=PlatformAdminControllerTest` | `Tests run: 27, Failures: 0, Errors: 0` | ✓ PASS |
| Full backend regression suite | `mvn test` | `Tests run: 183, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` | ✓ PASS |
| SAST (SpotBugs + FindSecBugs, ASVS L1 gate) | `mvn spotbugs:check` | `BugInstance size is 0`, `Error size is 0`, `BUILD SUCCESS` | ✓ PASS |
| Frontend structural gate (this phase's own, post-review-fix) | `node scripts/verify-relatorio-utilizacao.mjs` | 15/15 `PASS`, exit 0 | ✓ PASS |
| Frontend lint | `pnpm lint` | `0 errors, 18 warnings` — none in any Phase 122 file (top-issue file list contains only unrelated pre-existing files) | ✓ PASS |
| Frontend production build + route registration | `pnpm build` | Compiled successfully; TypeScript clean; route table includes `○ /plataforma/relatorio` | ✓ PASS |
| Diff-scope matches declared plan scope exactly, whole phase | `git diff --stat f18bd049~1..HEAD -- backend web` | Exactly 6 files: `PlatformAdminControllerTest.java`, `web/package.json`, `verify-relatorio-utilizacao.mjs`, `plataforma/page.tsx`, `relatorio/columns.tsx`, `relatorio/page.tsx` — zero backend production code, zero scope creep | ✓ PASS |
| Single source of truth for active-user count, re-derived | `grep -rn "countByTenantIdAndAtivoTrue" backend/src/main` | Exactly 2 call sites: `AdminController.java:122`, `PlatformAdminController.java:198` | ✓ PASS |
| WR-01/WR-02 gate fix genuinely present (not just claimed) | Direct read of `verify-relatorio-utilizacao.mjs:239-290` | Both `entrada-ver-relatorio` and `entrada-ordem-e-criar-tenant-intocado` slice to the specific `<Button>...</Button>` block before testing, not the whole file | ✓ PASS |
| Debt-marker scan on every file this phase touched | `grep -nE "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER" -i` on all 6 changed files | All hits are false positives (JSX `placeholder="..."` input hints; Portuguese words containing the substring "todo/todos/metodo") | ✓ PASS (no real markers) |
| Live HTTP authorization + suspended-tenant-visibility battery | `122-HUMAN-UAT.md` A1-A3 (curl against the real running backend, this session) | `200`/`403`/`200` as expected, suspended tenant present post-fix with unchanged count | ✓ PASS (recorded live; not re-executed by this verifier — servers were not running and were not started, per spot-check constraints) |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` convention exists anywhere in this repository (confirmed: no top-level `scripts/`, no `backend/scripts/`; `web/scripts/` contains only `verify-*.mjs` structural gates, none named `probe-*`). Neither the PLAN/SUMMARY files nor the ROADMAP success criteria for this phase reference probe-based verification. Not applicable to this phase (consistent with Phase 121's own finding).

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|-------------|--------|----------|
| UTIL-01 | 122-01, 122-03 (closed by 122-03) | Relatório interno (só administrador de plataforma) mostra, por tenant: nome, plano, limite contratado, utilizadores ativos agora | ✓ SATISFIED (structural; visual rendering pending human confirmation) | `.planning/REQUIREMENTS.md:37` marked `[x]`, traceability table (`:83`) maps `UTIL-01 | Phase 122 | Complete`; route built (122-01), made reachable by one click (122-03, `requirements mark-complete` correctly deferred until this point, matching the Phase 120 Plan 02/03/04 precedent); all 4 fields confirmed present in source and compiled build |

**Orphan check:** `.planning/REQUIREMENTS.md`'s traceability table maps exactly `UTIL-01` to "Phase 122" — identical to the union of requirement IDs declared across this phase's 4 plans' `requirements:` frontmatter. `ISOL-04` correctly maps to Phase 123, not claimed here. **No orphaned requirements.**

### Anti-Patterns Found

No blocking anti-patterns. Scanned every file this phase touched (`relatorio/page.tsx`, `relatorio/columns.tsx`, `plataforma/page.tsx`, `verify-relatorio-utilizacao.mjs`, `PlatformAdminControllerTest.java`'s new test) for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER`, empty-implementation patterns, and hardcoded-empty-data patterns.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `relatorio/page.tsx`, `plataforma/page.tsx` | various | Legitimate JSX `placeholder="Pesquisar tenant por nome..."` input hints | N/A — false positive | None |
| `PlatformAdminControllerTest.java` | various | Case-insensitive substring hits on Portuguese words (`metodo`, `todos`) | N/A — false positive | None; no real debt marker in any file this phase touched |
| `web/scripts/verify-relatorio-utilizacao.mjs` (WR-01/WR-02, `122-REVIEW.md`) | `:239-263` (pre-fix) | Two gate assertions tested `variant="outline"`/button-class substrings against the **whole file** instead of the specific `<Button>` block, meaning a regression on the actual "Ver Relatório"/"Criar Tenant" buttons could still pass if the same string existed elsewhere in the file (it does: the "Plataforma" Badge also uses `variant="outline"`; `EditarTenantForm`'s "Guardar" button uses the identical blue classes) | ⚠ WARNING (RESOLVED) | Fixed in commit `66bfac72` ("fix(122): WR-01/WR-02 isolar asserções do gate ao botão certo"), independently confirmed present in the current file (`:246-262`, isolates via `lastIndexOf("<Button", ...)` / `indexOf("</Button>", ...)`) and independently re-ran: 15/15 PASS. This was the review's own most important finding given the visual-UAT blocker — closing it restores the gate as a genuine regression net, not a false-positive-prone one. No further action needed. |
| `relatorio/columns.tsx:12-16`, `relatorio/page.tsx:139-146` (IN-01, `122-REVIEW.md`) | — | The `plano→Badge variant` mapping is now duplicated in 4 places across the codebase (2 pre-existing in `plataforma/columns.tsx`/`page.tsx`, 2 new here) | ℹ️ INFO | Non-blocking; a future 4th `TenantPlano` value could silently fall through the ternary-shaped copies without a compiler error. Recommend extracting a shared helper, not required to close this phase. |
| `relatorio/columns.tsx:10`, `relatorio/page.tsx:17` (IN-02, `122-REVIEW.md`) | — | Both new files import `TENANT_RESERVADO` from `../columns` (the write-capable, action-heavy sibling file), coupling this read-only screen's module graph to code it doesn't otherwise need | ℹ️ INFO | Not a functional bug (confirmed no `TenantAcoesCell`/`Tooltip`/`Pencil`/`Unlock` tokens leak into the report's own files, per gate assertion `colunas-sem-celula-de-acoes`); a maintainability note, not required to close this phase. |
| `plataforma/page.tsx:163,168-182` (IN-03, `122-REVIEW.md`) | — | The new 2-button `<div>` inside `CardHeader` has no own wrap/shrink class; at very narrow viewports the 2 buttons could sit side-by-side without independently wrapping | ℹ️ INFO | Cosmetic, explicitly flagged by the review as pending visual UAT (which did not run) — folded into Human Verification below rather than treated as a separate blocking item. |

### Human Verification Required

**This section is the direct answer to whether the missing live-visual UAT (H1-H6) leaves any ROADMAP success criterion genuinely unconfirmed — it does, for the "renders in the interface" and "state visually identified" sub-clauses of Criteria 1 and 3, and this is why overall status is `human_needed`, not `passed`.**

`122-04-PLAN.md`'s own `must_haves.truths` frontmatter (read directly, not inferred) lists 6 live scenarios; only the HTTP-provable one (authorization 200/403) was actually exercised against a real running backend this session. The other 5 — real in-browser rendering, real click-navigation, cross-screen number consistency as observed, mobile layout, and access-denied-without-flash + single-nav-entry as observed — remain in the state `122-HUMAN-UAT.md` itself honestly records: **NÃO VERIFICADO**, not fabricated as PASS, due to a documented Browser-MCP tooling failure ("the Browser pane is currently hidden/not displayed") that affected `form_input`/`left_click`/`screenshot`/`get_page_text` while simple metadata calls kept working — i.e., a transport/tooling problem, not a reproducible product defect (the backend and frontend were independently confirmed healthy via direct `curl` throughout the same window).

### 1. One-click reachability from `/plataforma`

**Test:** As `PLATAFORMA_ADMIN`, click "Ver Relatório" in `/plataforma`'s `CardHeader` and confirm real navigation to `/plataforma/relatorio`.
**Expected:** The route resolves without a console/hydration error and shows the report screen.
**Why human:** Real Next.js client-side navigation and route resolution cannot be proven by source reading, a structural gate, or a production build succeeding — those confirm the `Link`/`href` and the compiled route exist, not that a real click in a hydrated browser lands correctly.

### 2. Visual rendering of the 4 fields and Badge colors (desktop + mobile)

**Test:** Confirm the DataTable (desktop) and stacked cards (mobile, <768px) show nome/plano/limite/utilizadores-ativos with the expected Badge colors (plano: gray/purple/amber; estado: green/red).
**Expected:** All 4 fields visible, correct colors, no layout breakage.
**Why human:** Visual appearance (color, spacing, wrapping) is explicitly outside what code reading or grep-based gates can verify.

### 3. Cross-screen number consistency

**Test:** Compare the "utilizadores ativos" figure for the same tenant between `/plataforma` and `/plataforma/relatorio`, side by side.
**Expected:** Identical numbers for the same tenant on both screens.
**Why human:** Both screens are proven, by source reading, to read the same `utilizadoresAtivos` API field with no client-side recompute — but the actual side-by-side visual comparison has not been observed.

### 4. Suspended tenant remains visible (with Badge) under an active search term

**Test:** With a tenant suspended, type a search term matching its name in the report's search box and confirm it still appears with a red "Suspenso" Badge.
**Expected:** The suspended tenant is not hidden by the search UI, Badge renders correctly.
**Why human:** A1-A3 already proved, at the HTTP layer, that the API includes the suspended tenant and that the frontend's search memo filters only by name (source-confirmed) — but the actual Badge rendering and live search interaction were not observed.

### 5. Mobile layout integrity below 768px

**Test:** Resize to a mobile viewport and confirm the utilizadores/limite line renders correctly under each stacked card, without clipping.
**Expected:** Full readability of the count line on narrow screens.
**Why human:** Responsive layout is a visual-only claim; the source contains the relevant markup (confirmed and gate-checked) but its rendered result was not observed. This also covers `122-REVIEW.md`'s IN-03 (the new 2-button `CardHeader` cluster's own wrap behavior at very narrow widths).

### 6. Access-denied without a content flash, and single sidebar nav entry

**Test:** As a tenant `ADMIN` (not `PLATAFORMA_ADMIN`), navigate directly to `/plataforma/relatorio` and confirm `AccessDeniedState` appears with no prior flash of the tenant table/data; separately confirm this user's sidebar still shows exactly one platform-related entry.
**Expected:** No flash of protected content; no second nav item for the report.
**Why human:** Frame-by-frame rendering timing ("no flash") is a real-time behavior claim that only live observation settles, even though the guard's logical order (`!me.isFetched` before the role check, matching the already-live-UAT'd `/plataforma` pattern) is independently confirmed correct by direct reading. The nav-entry absence is independently confirmed by static grep (0 occurrences of the report route in `dashboard-shell.tsx`) but is still listed here because `122-HUMAN-UAT.md`/`122-04-PLAN.md` scope it as a dedicated live scenario (H6), not one this verifier will silently mark closed on grep alone.

**Context the human reviewing this report should know:** the user already reviewed this exact gap on 2026-07-30 (`122-HUMAN-UAT.md`, "Decisão do utilizador" section) and explicitly instructed accepting the available evidence and proceeding without re-running H1-H6 in that session, citing all-green automated gates and zero defects found in A1-A3. That is a legitimate, dated, disclosed operational decision — but it is **not** a formal verification override (no `must_have`/`reason`/`accepted_by`/`accepted_at` entry exists in this file's frontmatter tying it to a specific ROADMAP success criterion), and this report does not silently convert it into one. It is surfaced here, prominently, precisely so the decision of whether to (a) retry H1-H6 in a working browser session, or (b) formally add an `overrides:` entry to this file's frontmatter and proceed, is made consciously by a human reviewing this exact verification — matching this agent's own Escalation Gate design.

### Gaps Summary

No code-level gaps block this phase's 3 ROADMAP success criteria. Every artifact required by the 4 plans exists, is substantive (not a stub — full read of both new frontend files and the new backend test confirms real logic, not placeholders), is wired (imports/usages independently traced), and — where a data-flow claim applies — the data genuinely flows from a real DB-backed query, not a static/mock value. Zero backend production code was touched this phase (confirmed by exact commit-range diff), matching the phase's own "deliberately lean, zero new backend code" framing. All regression gates (183/183 backend tests, 0 SpotBugs findings, 0 frontend lint errors, 15/15 on this phase's own structural gate, a clean production build with the route registered) were independently re-executed by this verifier this session, not accepted from the narrative. A deep code review found 2 real Warnings (both about the gate's own assertion precision, not the product) and both were fixed and re-verified with a negative-proof correction (commit `66bfac72`), independently confirmed present in the current gate source by this verifier.

The one genuine, disclosed gap is **evidence completeness, not implementation correctness**: 6 of the 4-plans'-declared 9 live-UAT scenarios (H1-H6) were not executed due to a Browser-MCP tooling failure, not a reproducible product defect (backend/frontend were repeatedly confirmed healthy via direct HTTP during the same window). Two of those 6 scenarios (H2's rendering claim and H3's suspended-tenant-Badge claim) map directly onto the literal wording of ROADMAP Success Criteria 1 ("mostra na interface") and 3 ("com o seu estado identificado") — clauses that, by this project's own established verification convention (see Phase 120/121, both of which only reached `passed` after every browser-only claim was live-exercised), require live confirmation to be fully closed, not just strong indirect/structural evidence. Per this task's explicit instruction to be direct rather than soften this call: **this phase's live-UAT completeness gap is real, and this verifier lands on `human_needed`, not `passed`, on that basis** — while stating equally clearly that zero evidence of an actual defect was found anywhere in the source, tests, gates, build, or the partial live-HTTP battery that did run, and that a real, unrelated regression (the pending `120b-backfill-tenant-plano.sql` migration) was found and fixed as a byproduct of this phase's own live-UAT attempt, which is itself a positive signal about the rigor already applied.

**Recommendation:** retry `122-HUMAN-UAT.md`'s H1-H6 in a session where the Browser MCP pane is confirmed healthy before formally closing this phase's live verification, or have a human explicitly add an `overrides:` entry to this file's frontmatter accepting the current evidence (noting the already-dated 2026-07-30 user decision in `122-HUMAN-UAT.md` as the basis) and proceed to Phase 123 anyway — either is a legitimate path, but it is a decision for the developer, not one this verifier will make silently.

---

_Verified: 2026-07-30T09:51:22Z_
_Verifier: Claude (gsd-verifier)_
