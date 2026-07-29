# Phase 121 — Human UAT ao vivo

**Executado:** 2026-07-29
**Ambiente:** backend `mvn spring-boot:run` (porta 8080) + frontend `pnpm dev` (porta 3000), Postgres local via `psql` bundled em `C:\Program Files\PostgreSQL\18\bin\psql.exe`
**Executor:** Claude (via `curl` para a bateria HTTP da Task 1 + Browser MCP para o checkpoint da Task 2)

## Códigos HTTP confirmados (Task 1)

Bateria executada por esta ordem, com um único ficheiro de cookies por identidade (fora do repositório), e o mesmo objeto `rolePermissions` devolvido pelo `GET` usado verbatim como corpo do `PUT` em ambos os casos (payload no-op):

| # | Chamada | Identidade | Resultado | Esperado | Veredito |
|---|---------|-----------|-----------|----------|----------|
| 1 | `POST /auth/login` | `admin@lexcv.cv` (ADMIN de escritório) | `200` | `200` | CONFIRMADO |
| 2 | `GET /admin/rbac` | `admin@lexcv.cv` | `200` (corpo guardado como linha de base) | `200` | CONFIRMADO |
| 3 | `PUT /admin/rbac` | `admin@lexcv.cv` | **`403`** `{"message":"Acesso negado."}` | `403` (**PROVA CENTRAL**) | CONFIRMADO |
| 4 | `POST /auth/login` | `plataforma@lexcv.cv` (PLATAFORMA_ADMIN) | `200` | `200` | CONFIRMADO |
| 5 | `GET /admin/rbac` | `plataforma@lexcv.cv` | `403` `{"message":"Acesso negado."}` | `403` (assimetria intencional) | CONFIRMADO |
| 6 | `PUT /admin/rbac` | `plataforma@lexcv.cv` | **`200`** `{"message":"Permissões de perfis (RBAC) atualizadas com sucesso!"}` | `200` (**contra-teste**) | CONFIRMADO |
| 7 | `GET /admin/rbac` | `admin@lexcv.cv` (repetição) | `200`, corpo **byte a byte idêntico** à linha de base do passo 2 (`diff` sem output) | idêntico | CONFIRMADO — zero deriva |

Confirmação secundária via `psql` (contagem de permissões por papel, pós-bateria): `ADMIN=19, ADVOGADO=17, ASSISTENTE=7, PLATAFORMA_ADMIN=0, TECNICO=8` — consistente com o desenho da Phase 119 (PLATAFORMA_ADMIN sempre zero permissões com scope).

Nenhum código `500` foi observado em nenhuma chamada. `pnpm verify:bloqueio-rbac` correu antes da bateria: 11/11 `PASS`. `git status --porcelain -- backend web` vazio no fim de toda a Task 1 — nenhum ficheiro de código alterado; ficheiros de cookies e o corpo do RBAC gravados fora do repositório (scratchpad da sessão).

## Pontos verificados (Task 2 — checkpoint humano)

1. **CONFIRMADO** — Como `admin@lexcv.cv`, Definições → o separador **"Controlo de Acesso (RBAC)"** continua visível e abre normalmente.

2. **CONFIRMADO — PROVA CENTRAL.** No cabeçalho do cartão "Matriz de Regras de Acesso (RBAC)", **não existe nenhum botão "Guardar Regras"** — confirmado tanto visualmente (screenshot) como estruturalmente (`document.querySelectorAll('button')` filtrado por texto: zero botões contêm "Guardar Regras" em toda a página). No lugar dele aparece um `Badge` com ícone de cadeado e o texto exato **"Gerido pela Plataforma"**, com classes `border-neutral-200 text-neutral-900 dark:border-neutral-800 dark:text-neutral-50` — confirmado neutro (nunca azul, nunca vermelho).

3. **CONFIRMADO — Tooltip por rato.** Rato sobre o distintivo, aguardados ~1.2s: `data-state` do `[data-slot="tooltip-trigger"]` mudou para `"delayed-open"` e o tooltip renderizou com o texto exato: *"As regras de acesso por perfil (RBAC) passaram a ser uma configuração fixa e comum a toda a plataforma LexCV — já não podem ser alteradas a partir de um escritório individual."* — idêntico ao contrato de copywriting do UI-SPEC.

4. **CONFIRMADO — Tooltip por teclado.** Sem tocar no rato: foco inicial posto por script no botão do separador "Controlo de Acesso (RBAC)" (simulando o ponto de partida de uma navegação real), depois **2 pressões de `Tab`** reais (não `.focus()` direto) até o `document.activeElement` ser exatamente o `<span tabindex="0" data-slot="tooltip-trigger">` do distintivo. O mesmo `data-state="delayed-open"` e o mesmo texto exato apareceram, **só por foco de teclado, sem qualquer interação de rato**. Terceira utilização bem-sucedida desta composição Tooltip+`<span tabIndex={0}>` neste codebase (a primeira foi a Phase 118, a segunda a Phase 120).

5. **CONFIRMADO** — A matriz continua viva: a tabela renderiza com as colunas de perfil. Clicado o checkbox de `TECNICO` na linha "Gerir Clientes" (estado inicial `false`/`disabled=false`) — alternou para `true` após o clique (confirmado via inspeção do estado `checked` antes/depois). O checkbox de `ADMIN` na mesma linha manteve-se `disabled=true` e `checked=true` durante todo o teste — imutável, como esperado. Página recarregada a seguir para descartar a alternância local (não há botão de gravar para a persistir — limitação conhecida e aceite, documentada no Plan 02).

6. **CONFIRMADO — Mobile.** Janela redimensionada para 375×812. O cabeçalho do cartão RBAC passa a coluna única e o distintivo "Gerido pela Plataforma" aparece alinhado à esquerda, imediatamente abaixo do título e da descrição — sem sobreposição nem corte de texto.

7. **CONFIRMADO — observação factual, intencional, não é defeito.** Sessão terminada e reautenticado como `plataforma@lexcv.cv`. Em Definições, aparecem **apenas** os separadores "O Meu Perfil" e "Segurança" — o separador "Controlo de Acesso (RBAC)" (e "Gestão de Utilizadores") **não aparece** para este perfil. Isto é exatamente o efeito pretendido por `121-CONTEXT.md`: `hasRbacManage` (`can.manage("rbac") || isAdmin`) não foi alargado para incluir `PLATAFORMA_ADMIN`, e esta fase não cria (por decisão explícita) um ecrã de edição de RBAC para a plataforma. Registado para a Phase 123 usar como facto de partida, não como gap a fechar aqui.

8. **CONFIRMADO — Ambiente limpo.** A Task 1 não escreveu dados reais (o `PUT` de ambas as identidades usou o payload no-op, confirmado pelo `GET` idêntico antes/depois no ponto 7 da bateria HTTP). A única alteração local do ponto 5 (um checkbox) nunca foi persistida (não existe ação de gravar) e foi descartada com o reload da página. `git status --porcelain -- backend web` vazio.

## Resumo

Os 8 pontos exigidos por este plano têm veredito `CONFIRMADO`. Nenhum `FALHOU`, nenhum `NÃO VERIFICADO`. O Critério de Sucesso 3 do ROADMAP (o `403` para o `ADMIN` de escritório) deixa de ser uma afirmação verificada apenas pelo proxy AOP montado à mão no Plan 01 — foi visto a acontecer contra o contexto Spring real, com o contra-teste do `PLATAFORMA_ADMIN` (`200`) a excluir a hipótese de um bloqueio universal acidental. O Critério de Sucesso 4 (a ação de gravar desaparece da interface) foi visto no browser, com as duas vias de acesso ao Tooltip (rato e teclado) confirmadas separadamente, tal como exigido. A matriz RBAC persistida terminou exatamente como começou.

---
*Phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac*
*Completed: 2026-07-29*
