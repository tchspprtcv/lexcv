# Phase 122 — Human UAT ao vivo

**Executado:** 2026-07-30
**Ambiente:** backend `mvn spring-boot:run` (porta 8080) + frontend `pnpm dev` (porta 3000), Postgres local via `psql` bundled em `C:\Program Files\PostgreSQL\18\bin\psql.exe`
**Executor:** Claude (via `curl` para a bateria automatizada A1-A3; tentativa de Browser MCP para os cenários H1-H6 — ver bloqueio de ambiente abaixo)

## Resultado global

**A1, A2 e A3 (automatizados): CONFIRMADO, com um achado real e corrigido durante a execução (ver abaixo).**
**H1-H6 (humanos, ao vivo no browser): NÃO VERIFICADO — bloqueio de ferramenta (Browser MCP), não do produto. Ver secção dedicada.**

Este ficheiro documenta com precisão o que foi e não foi confirmado, em vez de assumir sucesso nos 6 cenários visuais que não puderam ser executados.

## A1 — Autorização positiva (`PLATAFORMA_ADMIN`)

**CONFIRMADO.** `POST /api/v1/auth/login` com `plataforma@lexcv.cv` → `200`, cookies gravados. `GET /api/v1/platform/tenants` com esses cookies → `200`, corpo com 2 tenants, cada um com os 6 campos esperados:

```json
[
  {"id":"31a1afee-1fda-4dcd-ac0b-d4ce8a6868b1","nome":"Escritorio A","plano":null,"limiteUtilizadores":null,"ativo":true,"utilizadoresAtivos":5},
  {"id":"0a5f3d6b-a233-413f-9a96-a7ddd171ae2f","nome":"LexCV","plano":null,"limiteUtilizadores":null,"ativo":true,"utilizadoresAtivos":1}
]
```

(Nota: `plano:null` neste snapshot inicial é o achado documentado abaixo — corrigido antes do A3.)

## A2 — Autorização negativa (`ADMIN` de escritório) — prova ASVS L1

**CONFIRMADO.** Novo login, jar de cookies **separado**, `admin@lexcv.cv` → `200` (autentica normalmente). `GET /api/v1/platform/tenants` com esse jar → **`403`** `{"message":"Acesso negado."}`. Não veio `200` nem `500`.

## A3 — Tenant suspenso continua na resposta

**CONFIRMADO, após corrigir um bloqueio real de ambiente encontrado durante esta task.**

### Achado ao vivo (não esperado pelo plano, investigado e corrigido nesta sessão)

A primeira tentativa de `PATCH /api/v1/platform/tenants/{id}/ativo` (suspender "Escritorio A") devolveu **`500`**:
```json
{"error":"DataIntegrityViolationException","message":"not-null property references a null or transient value: com.lexcv.models.Tenant.plano"}
```

Isto **não é o "badge de plano errado, cosmético" já documentado em `STATE.md`** — é uma regressão funcional real: `backend/migrations/120b-backfill-tenant-plano.sql` (Phase 120, CR-01) nunca tinha corrido nesta base de dados de desenvolvimento, e `Tenant.plano` tem `nullable = false` ao nível da anotação JPA desde essa fase. `@Builder.Default` só fornece um valor por omissão a entidades construídas pelo builder Lombok — não tem qualquer efeito sobre entidades que o Hibernate carrega de uma coluna já populada e ainda anulável. Confirmado por `SELECT nome, plano FROM t_tenant;` → ambos os tenants tinham `plano` a `NULL`.

**Corrigido nesta sessão**: corrida a migração pendente (`psql -f migrations/120b-backfill-tenant-plano.sql`) — `UPDATE 2` linhas, mais os dois `ALTER TABLE`. Confirmado por nova `SELECT`: ambos os tenants agora têm `plano = STARTER`. `STATE.md` foi atualizado para refletir a severidade real (bloqueador funcional, não cosmético) — ver commit `5da4de2a`.

### A3 confirmado após a correção

`PATCH .../31a1afee.../ativo` com `{"ativo":false}` → **`200`**, corpo com `"ativo":false`. Repetido o `GET /api/v1/platform/tenants` → **2 tenants ainda devolvidos** (contagem inalterada), o suspenso com `"ativo":false` e o mesmo `id`.

**Ambiente reposto no fim desta sessão:** `PATCH .../ativo` com `{"ativo":true}` → `200`, `"ativo":true` confirmado. `GET` final mostra ambos os tenants `ativo:true`, `plano:"STARTER"` — estado limpo.

## H1–H6 — NÃO VERIFICADO (bloqueio de ferramenta, não do produto)

Tentativa extensa e de boa-fé de completar os 6 cenários visuais/interativos via Browser MCP, incluindo:
- Reinício limpo do frontend (cache Turbopack limpo, servidor reiniciado) após uma instabilidade inicial de compilação.
- Diagnóstico e confirmação de que a rota raiz `/` tem um problema de hidratação conhecido desta sessão (uma `<div hidden="">` presa numa fronteira Suspense React que nunca resolve em navegação "fria") — contornado ao navegar diretamente para `/login`, que renderizou corretamente (texto "Entrar"/"Email"/"Password" confirmado via `get_page_text`).
- Múltiplas tentativas subsequentes de preencher o formulário de login (`form_input`) falharam consistentemente com o erro **"the Browser pane is currently hidden"** — um erro de ferramenta/transporte, não da aplicação (confirmado: chamadas de leitura simples como `tabs_context` continuavam a responder normalmente durante o mesmo período).
- Backend e frontend confirmados saudáveis durante todo este período via `curl` direto (ver A1-A3 acima, todos bem-sucedidos nesta mesma janela de tempo).

**Nenhum dos 6 cenários H1-H6 foi observado ao vivo.** Não estão a ser registados como `FALHOU` (nenhuma evidência de defeito foi observada) nem como `CONFIRMADO` (nenhuma evidência de sucesso foi observada) — estão **NÃO VERIFICADO** por um bloqueio da ferramenta de automação de browser, não do código deste projeto. Todo o código, gates automatizados (`pnpm lint`, `pnpm build`, `pnpm verify:relatorio-utilizacao` 15/15, `mvn test`, `mvn spotbugs:check`) e a bateria HTTP A1-A3 acima já confirmam, por evidência independente de código e de comportamento HTTP real, que:
- A rota `/plataforma/relatorio` existe e compila (confirmado no `pnpm build` de `122-01-SUMMARY.md`).
- A autorização real (`200`/`403`) está provada (A1/A2 acima, não apenas por leitura de código).
- O tenant suspenso é devolvido pela API (A3 acima).

O que **não pôde** ser confirmado sem o browser: a navegação por clique real a partir de `/plataforma`, a renderização visual exata dos 4 campos e dos Badges, a coerência visual cruzada dos números entre os dois ecrãs, o layout do cartão mobile, a ausência de flash de conteúdo no cartão de acesso negado, e a contagem de entradas de navegação lateral.

## Observações de ambiente registadas (não são defeitos desta fase)

- A exceção de peso de letra do título de `/plataforma/relatorio` (600 em vez do habitual 700 da app) está documentada e aprovada em `122-UI-SPEC.md` — não é uma inconsistência a corrigir, não pôde ser confirmada visualmente nesta sessão mas não é uma preocupação nova.
- A questão do `plano` nulo em tenants legados (ver achado A3 acima) estava pendente exatamente como `STATE.md` já indicava — a diferença encontrada nesta sessão foi a **severidade real** (bloqueador funcional, não cosmético), não a existência do problema em si.

## Resumo

3 de 9 cenários (A1-A3) confirmados ao vivo com evidência HTTP real, incluindo a descoberta e correção de um bloqueador de ambiente genuíno (migração pendente causando `500` em qualquer escrita sobre um tenant pré-existente). 6 de 9 cenários (H1-H6) não puderam ser executados devido a um bloqueio da ferramenta de automação de browser desta sessão — não uma falha do produto. `UTIL-01` já foi fechado pelo Plan 03 com base em evidência de código + gate estrutural; esta sessão não reabre nem refecha esse requisito.

## Decisão do utilizador (2026-07-30)

O utilizador reviu este registo e instruiu explicitamente: **aceitar a evidência disponível e continuar**, sem repetir os cenários H1-H6 nesta sessão. Decisão registada aqui para o histórico, dado que os 6 cenários visuais permanecem tecnicamente "não verificados ao vivo" — a aceitação é uma decisão consciente do operador, com base em: todos os gates automatizados a verde, os 3 cenários que puderam correr (A1-A3) confirmados sem qualquer defeito, e zero indícios de regressão em qualquer evidência recolhida (código, testes, HTTP real). Não é uma reinterpretação silenciosa do critério — é uma aceitação explícita e datada do risco residual.

---
*Phase: 122-relat-rio-de-utiliza-o-por-tenant*
*Completed (parcialmente — ver secção H1-H6): 2026-07-30*
