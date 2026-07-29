---
phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
plan: 03
subsystem: api
tags: [tenant-isolation, security-audit, rbac]
requirements-completed: [ISOL-01, ISOL-02]
---

# Auditoria de Isolamento de Tenant — ISOL-01 / ISOL-02

**Registo auditável, reproduzível por comando, de duas confirmações: (1) o endpoint público de branding já não resolve nenhuma tenant por heurística (ISOL-01); (2) uma varredura re-executada por 5 famílias de padrão sobre `backend/src/main/java` e `web/src` não encontra nenhum outro caminho de resolução-de-tenant-por-heurística (ISOL-02). Ambos os vereditos são COVERED — nenhum código foi alterado por esta auditoria.**

Formato de tabela de veredito reutilizado de `.planning/milestones/v2.11-phases/LEXCV-97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente/97-01-SUMMARY.md` (AUD-01, "Tenant-Isolation Audit of Notification Surfaces"). Este ficheiro existe para que a Phase 123 (ISOL-04) possa citar diretamente as tabelas abaixo em vez de repetir a varredura de raiz.

---

## ISOL-01 — Confirmação de regressão

Requisito: confirmar por **execução**, não por leitura de um docblock que se auto-declara satisfeito, que `GET /api/v1/public/branding` já não depende de `TenantRepository.findFirstByOrderByCreatedAtAsc()` (o método que resolvia "a" tenant como a mais antiga por `createdAt`, fechado como CR-02 em `119-REVIEW.md`).

Quatro peças de evidência independentes, cada uma por comando:

| # | Comando | Resultado literal | Classificação |
|---|---|---|---|
| 1 | `cd backend && mvn test -Dtest=PublicControllerTest` | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` | Conforme esperado — os 2 testes (`getBranding_devolveSempreAMarcaGenericaLexCV`, `getBranding_devolveSempreAMesmaRespostaIndependentementeDoEstado`) não mockam nem injetam nenhum `TenantRepository` (o controller deixou de o receber no construtor) |
| 2 | `grep -rn 'findFirstByOrderByCreatedAtAsc' --include=*.java backend/src` | Exatamente 2 ocorrências: `PublicController.java:15`, `PublicControllerTest.java:14` | Ambas em texto de Javadoc/comentário histórico (explicam o que o endpoint fazia *antes* da correção CR-02) — **zero em código vivo** |
| 3 | `grep -c 'Repository' backend/src/main/java/com/lexcv/controllers/PublicController.java` | `1` (não `0`) | A única ocorrência é a mesma linha 15 do item 2 — texto de comentário Javadoc, não código vivo. `grep -n '@RequiredArgsConstructor\|private final' backend/src/main/java/com/lexcv/controllers/PublicController.java` devolve **0** ocorrências, nem em comentário. Confirmado por leitura integral do ficheiro (42 linhas): a classe não tem construtor, não tem campos, não tem `@RequiredArgsConstructor`, e o único handler devolve uma resposta hardcoded (`TenantPublicInfoResponse.builder().nome("LexCV").logoDataUrl(null).build()`) sem nenhuma dependência injetada capaz de resolver uma tenant |
| 4 | `grep -cE 'findFirstBy\|findTopBy' backend/src/main/java/com/lexcv/repositories/TenantRepository.java` | `2` (não `0`) | Ambas as ocorrências referem-se a `findFirstByNome(String nome)` (`TenantRepository.java:24`, mais o seu próprio comentário de rationale na linha 15) — um método **distinto**, parametrizado, de procura por nome exato da tenant reservada "LexCV" (Phase 119, WR-01/`119-REVIEW.md`), não a assinatura removida `findFirstByOrderByCreatedAtAsc()` (que continua ausente — zero declarações em `TenantRepository.java`). Ver a linha de veredito ISOL-02 correspondente abaixo |

**Nota de transparência (itens 3 e 4):** os comandos automatizados literais do plano esperavam `0` para ambos, mas devolvem `1` e `2` respetivamente. Em ambos os casos a causa é a mesma: um grep de substring genérico também apanha (a) o próprio comentário de classe que documenta historicamente o método removido (item 3) e (b) um método diferente e já revisto que partilha o prefixo Spring Data `findFirstBy` mas resolve um problema distinto — procura por nome exato, não heurística de "primeira/mais antiga" (item 4). Este é o mesmo padrão já registado várias vezes em `STATE.md` (Phases 119-04, 120-01, 120-02, 121-01): comentário auto-referencial a colidir com um gate de verificação baseado em grep literal. Não é uma regressão de ISOL-01 — confirmado por leitura direta de ambas as linhas.

**Conclusão ISOL-01: COVERED** (fechado pela correção CR-02 da Phase 119; esta fase confirma por execução e por pesquisa, não reimplementa). Nenhum ficheiro sob `backend/` ou `web/` foi alterado por esta task — `git status --porcelain -- backend web` vazio.

---

## ISOL-02 — Veredito por superfície

Varredura re-executada por comando sobre `backend/src/main/java` e `web/src` (não copiada de `121-CONTEXT.md`), cobrindo 5 famílias de padrão. Cada ocorrência recebe um de três desfechos: **(a)** resolução legítima da tenant reservada por nome exato; **(b)** tabela/entidade global de plataforma por design; **(c)** iteração cross-tenant deliberada e já revista, com `tenantId` como parâmetro explícito de ciclo, nunca adivinhado. Duas superfícies adicionais traçadas não se enquadram em (a)/(b)/(c) porque não têm qualquer relação com resolução de tenant — documentadas como tal, explicitamente, em vez de forçadas numa das três categorias.

| Query / Guard | Scope confirmed | Verdict |
|---|---|---|
| `TenantRepository.findFirstByOrderByCreatedAtAsc()` | Já não existe em `TenantRepository.java` nem em nenhum outro ficheiro `.java` do repositório — os únicos 2 vestígios são texto de Javadoc em `PublicController.java:15` e `PublicControllerTest.java:14` | COVERED (fechado por CR-02, Phase 119 — ISOL-01) |
| `PublicController.getBranding()` (`PublicController.java:33-41`) | Resposta hardcoded (`nome("LexCV")`, `logoDataUrl(null)`), zero dependências injetadas, zero leitura de repositório ou `SecurityContextHolder` | COVERED (nunca resolve nenhuma tenant, genérica por design) |
| `TenantRepository.findFirstByNome(String nome)` (`TenantRepository.java:24`) | Parametrizado por nome exato; único call site é `DatabaseSeeder.seedTenantPlataforma()` (`DatabaseSeeder.java:406`) com o literal `"LexCV"` | COVERED (a) resolução legítima da tenant reservada por nome exato |
| `DatabaseSeeder.seedTenantPlataforma()` (`DatabaseSeeder.java:405-408`) | Find-or-create idempotente: `findFirstByNome("LexCV").orElseGet(() -> ...save(Tenant.builder().nome("LexCV").build()))` — nunca ordena por `createdAt` nem escolhe "a primeira" tenant qualquer | COVERED (a) seed da tenant reservada por nome literal |
| `PlatformAdminController.TENANT_RESERVADO` (`PlatformAdminController.java:61`) | Constante `"LexCV"`, usada apenas para recusar a suspensão desta tenant específica em `setTenantAtivo` (evita self-lockout do único `PLATAFORMA_ADMIN`) | COVERED (a) referência por nome exato, não heurística |
| `plataforma/columns.tsx` `TENANT_RESERVADO` (`web/src/app/(dashboard)/plataforma/columns.tsx:18`) | Constante `"LexCV"` no frontend, espelha a do backend, usada só para o badge "Plataforma" na consola de tenants | COVERED (a) referência por nome exato, não heurística |
| `AdminController.getRbac()` `roleRepository.findAll()` (`AdminController.java:348`) | `Role`/`Permission` (`Role.java`, `Permission.java`, lidos por inteiro) confirmadas sem qualquer coluna `tenant_id` — tabelas de configuração de plataforma inteiramente globais, nunca por-tenant | COVERED (b) entidade global de plataforma por design |
| `Role`/`Permission` (entidades JPA) | `Role.java`: `id`, `nome` (unique), `permissions` (ManyToMany) — nenhum campo tenant. `Permission.java`: `id`, `nome` (unique) — idem | COVERED (b) tabelas globais por design (ver também `AdminController.java:391-424`, gate de método do Plan 01 `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` em `updateRbac`, que já fecha a escrita destas tabelas globais a papéis de plataforma) |
| `PlatformAdminController.listTenants()` `tenantRepository.findAll()` (`PlatformAdminController.java:108`) | Docblock do próprio método (`:96-104`) declara "deliberadamente sem filtro de tenant"; único gate é `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` de classe (`:53`) | COVERED (c) iteração cross-tenant deliberada, já revista (Phase 119/120), gated |
| `AlertasDiariosJob.executar()` `tenantRepository.findAll()` (`AlertasDiariosJob.java:90`) | Docblock de classe (`:31-45`) confirma: corre sem `SecurityContextHolder`/JWT, `tenantId` é sempre parâmetro explícito de ciclo (nunca `getTenantId()`) | COVERED (c) iteração cross-tenant deliberada, já documentada (NOTF-20/21/23) |
| `ResourceController.executarTransicao` `.stream().filter(...).findFirst()` (`ResourceController.java:1549-1551`) | Opera sobre `TRANSICOES_PERMITIDAS` — mapa estático em memória de transições de estado de `Processo`, filtrado por `acao` — nenhum dado de `Tenant` envolvido | COVERED — não relacionado com resolução de tenant (máquina de estados, não multi-tenancy) |
| `ResourceController` cálculo de KPI `movs.get(0).getData()` (`ResourceController.java:3232-3236`) | `movs` é `List<Movimentacao>` de um único `Processo p` já percorrido dentro de um loop tenant-scoped (mesma iteração usa `documentoRepository.findByTenantIdAndProcessoId(tenantId, p.getId())` na linha seguinte, 3243); `get(0)` obtém a movimentação mais recente **daquele processo**, após sort explícito por data — nunca "a" tenant | COVERED — não relacionado com resolução de tenant (mais-recente-de-uma-lista-já-scoped, não heurística de tenant) |
| `me.data?.tenant_nome ?? "LexCV"` (4 call sites: `dashboard-shell.tsx:210,219`, `clientes/[id]/ficha/page.tsx:106`, `termo-honorarios/page.tsx:123`) | `me.data` já é populado via `GET /auth/me`, que resolve a tenant real a partir de `UserPrincipal.getTenantId()` (JWT) — o fallback `"LexCV"` só se aplica ao instante de loading/ausência de dados; nunca decide que dados são pedidos ao backend | COVERED — fallback de UI cosmético sobre dados já corretamente scoped, não um mecanismo de resolução |
| `web/src/app/layout.tsx:18` `title: "LexCV"` | Metadata estática do `<title>` do documento HTML da app Next.js — não é dado de tenant nenhum | COVERED — não relacionado com resolução de tenant (branding estático do produto) |
| Caminho de resolução de tenant da app autenticada — `getTenantId()` (`ResourceController.java:127-131`, `ParecerController.java:51-55`, `ParecerPesquisaController.java:37-41`, `PesquisaController.java:66-70`, `NotificacaoController.java:57-61`) | Todos os 5 controllers definem o mesmo helper privado, byte-a-byte idêntico: `SecurityContextHolder.getContext().getAuthentication()` → cast `UserPrincipal` → `.getTenantId()` | COVERED — sempre via JWT/contexto de segurança autenticado, nunca inferido ou adivinhado |

Zero linhas `FIXED` — nenhum código foi alterado por esta auditoria.

---

## Comandos de reproducao

As 5 famílias de padrão exigidas pelo plano, com os comandos exatos corridos (reprodutíveis por qualquer fase futura, incluindo a Phase 123):

```bash
# Família 1: findFirstBy / findTopBy
grep -rn 'findFirstBy\|findTopBy' --include=*.java backend/src/main/java   # 6 hits (ver tabela)
grep -rln 'findFirstBy\|findTopBy' web/src                                 # 0 hits

# Família 2: .findAll() (iteração cross-tenant)
grep -rn '\.findAll()' --include=*.java backend/src/main/java              # 3 hits (AdminController, PlatformAdminController, AlertasDiariosJob)
grep -rn '\.findAll()' web/src                                             # 0 hits

# Família 3: .get(0) e stream().findFirst() (primeiro elemento como se fosse "a" tenant)
grep -rn '\.get(0)\|findFirst()' --include=*.java backend/src/main/java    # 2 hits (ambos não relacionados com tenant, ver tabela)
grep -rn '\.get(0)' web/src                                                # 0 hits
grep -rn '\.findFirst()' web/src                                           # 0 hits

# Família 4: literal "LexCV"
grep -rn '"LexCV"' --include=*.java backend/src/main/java                  # 13 hits (comentários + 4 usos legítimos em código vivo)
grep -rn '"LexCV"' web/src                                                 # 6 hits (4 fallback de UI + 1 metadata + 1 constante reservada)

# Família 5: findFirstByOrderByCreatedAtAsc (reutilizado da Task 1 ISOL-01)
grep -rn 'findFirstByOrderByCreatedAtAsc' --include=*.java backend/src     # 2 hits, ambos em comentário/Javadoc

# Evidência ISOL-01 adicional
cd backend && mvn test -Dtest=PublicControllerTest                         # Tests run: 2, Failures: 0, Errors: 0
grep -c 'Repository' backend/src/main/java/com/lexcv/controllers/PublicController.java          # 1 (comentário, ver nota acima)
grep -n '@RequiredArgsConstructor\|private final' backend/src/main/java/com/lexcv/controllers/PublicController.java  # 0 hits
grep -cE 'findFirstBy\|findTopBy' backend/src/main/java/com/lexcv/repositories/TenantRepository.java  # 2 (findFirstByNome, ver nota acima)
```

---

## Registado mas explicitamente fora de âmbito para ISOL-02

`.planning/research/PITFALLS.md`, **Pitfall 1** ("Tenant isolation leak via an entity that has no `tenant_id` column of its own") documenta que `ProcessoRepository.findByClienteId(UUID)` e assinaturas `findByXxxId`-sem-`tenantId` equivalentes (~11 métodos no repositório) são seguras hoje apenas porque cada call site revalida separadamente o tenant da entidade-pai antes de usar a linha devolvida — não porque a própria query seja tenant-scoped. `Honorario`, em particular, não tem nenhuma coluna `tenant_id` própria; a sua única ligação de posse é `processo_id`.

Este é um concern **real, pré-existente, IDOR-adjacente**, anterior à v2.16 por várias milestones (o próprio `PITFALLS.md` documenta que o projeto já enviou e corrigiu duas vezes esta exata classe de bug: os filtros silenciosamente ignorados de `GET /honorarios?processo_id=X` e `GET /documentos?processo_id=X`, v2.9). **Não é newly risky especificamente por causa de um 2º tenant pagante** — ao contrário de ISOL-03 (o gate de `PUT /admin/rbac`, que a Phase 120 tornou explorável ao habilitar provisionamento multi-tenant real).

**Explicitamente NÃO é um achado desta auditoria ISOL-02.** Dono nomeado: **Phase 123 (ISOL-04)** decide se este padrão merece a sua própria fase de correção ou se o double-check já existente em cada call site é aceite como mitigação suficiente.

---

## Assimetria observada em `/admin/rbac`

Confirmado por leitura direta de `AdminController.java` (estado atual, pós-Plan-01): a classe mantém `@PreAuthorize("hasRole('ADMIN')")` ao nível de classe (`:28`). `updateRbac` (`PUT /rbac`, `:398-399` em diante) ganhou o gate de método `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` no Plan 01 desta fase, substituindo — não somando — o gate de classe para este handler específico. `getRbac` (`GET /rbac`, `:346-389`) foi **deliberadamente deixado sem gate de método**, continuando a herdar apenas `hasRole('ADMIN')`.

Consequência mecânica, literal e intencional: um `PLATAFORMA_ADMIN` (que não detém `ROLE_ADMIN`) **passa** o gate de `PUT /api/v1/admin/rbac` mas continua a receber `403` em `GET /api/v1/admin/rbac`. Isto não é um defeito — é o resultado direto dos Critérios de Sucesso da Phase 121, que nomeiam apenas o `PUT` (ver `121-CONTEXT.md`: "success criterion 3 names PUT specifically, not GET"). Um ecrã de leitura/edição de RBAC dedicado a `PLATAFORMA_ADMIN` está explicitamente diferido — não implicado pelos critérios de sucesso desta fase, e não construído aqui.

Registado aqui para que a Phase 123 não o redescubra como surpresa.

---

*Auditoria produzida pelo Plan 03 da Phase 121. Nenhum ficheiro sob `backend/` ou `web/` foi alterado — confirmado por `git status --porcelain -- backend web` vazio antes e depois desta auditoria.*
