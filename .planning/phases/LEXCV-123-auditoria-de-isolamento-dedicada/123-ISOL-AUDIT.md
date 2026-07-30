---
phase: 123-auditoria-de-isolamento-dedicada
plan: 01
subsystem: api
tags: [tenant-isolation, security-audit, rbac, multi-tenancy]
---

# Auditoria de Isolamento Dedicada — Fase 123 (ISOL-04): Critério de Sucesso 1

**Última fase do marco v2.16, cobrindo por re-derivação independente as 3 superfícies novas desta milestone — consola de administração de tenants (Phase 120), relatório de utilização (Phase 122) e bloqueio de `PUT /api/v1/admin/rbac` (Phase 121) — antes de se considerar seguro provisionar um 2º tenant pagante real. Este ficheiro cobre o Critério de Sucesso 1 (consola + relatório); o Critério de Sucesso 2 (bloqueio de RBAC), a decisão sobre o Pitfall 1, e o veredito final da fase são acrescentados pelo Plano 02. Todo o veredito abaixo é produzido por execução direta de comando e leitura fresca do código atual — nunca por citação das alegações das Fases 120/121/122. Zero alterações de código de produção: `git status --porcelain -- backend web` ficou vazio antes e depois deste plano.**

## Nota de reutilização de formato

Formato reutilizado literalmente de dois precedentes, tal como decidido em `123-CONTEXT.md` (que cita ambos explicitamente) e nomeado pelo próprio `ROADMAP.md` ("no espírito da AUD-01 da v2.11"):

- `.planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-ISOL-AUDIT.md` — a tabela `Query / Guard | Scope confirmed | Verdict`, a secção "Comandos de reproducao", e o desfecho explícito `COVERED` / lista de fixes.
- `.planning/milestones/v2.11-phases/LEXCV-97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente/97-01-SUMMARY.md` (AUD-01, "Tenant-Isolation Audit of Notification Surfaces") — o precedente original deste formato de tabela de veredito.

Não foi inventado nenhum formato novo.

## Critério de Sucesso 1 — Veredito por superfície

> **ROADMAP.md, Fase 123, Critério de Sucesso 1:** "A consola de administração de tenants (Phase 120) e o relatório de utilização (Phase 122) são auditados e confirmados a expor dados exclusivamente através de endpoints gated a `PLATAFORMA_ADMIN`, nunca através de um endpoint tenant-scoped comum."

`TenantAdminSummaryResponse` é confirmada, por leitura integral do seu próprio ficheiro e pelo seu único produtor (`PlatformAdminController.toSummary`), como a **única** projeção de `Tenant` servida a ambas as superfícies (a consola `/plataforma` consome-a via `GET /platform/tenants`, `PUT /platform/tenants/{id}` e `PATCH /platform/tenants/{id}/ativo`; o relatório `/plataforma/relatorio` consome exatamente o mesmo `GET /platform/tenants`, sem endpoint próprio). Os seus 6 campos são `id`, `nome`, `plano`, `limiteUtilizadores`, `ativo`, `utilizadoresAtivos`; omite deliberadamente `logoDataUrl` (campo `@Lob`, potencialmente megabytes), `nif`, `email`, `telefone` e a data de criação — nenhum destes 5 campos sensíveis é alcançável por nenhuma das duas superfícies.

| Query / Guard | Scope confirmed | Verdict |
|---|---|---|
| Gate de classe `PlatformAdminController` (`:53`) | `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` — único gate da classe; 0 `@PreAuthorize` de método nos 4 handlers (nenhum relaxa o gate herdado) | COVERED |
| `POST /api/v1/platform/tenants` (`createTenant`, `:67`) | Herda só o gate de classe; delega em `SetupService.provisionTenant`, nunca lê `SecurityContextHolder` nem tenant do chamador | COVERED |
| `GET /api/v1/platform/tenants` (`listTenants`, `:106`) | Herda só o gate de classe; devolve todos os tenants via `tenantRepository.findAll()` mapeados para `TenantAdminSummaryResponse` — consumido por ambas as superfícies (consola `/plataforma` e relatório `/plataforma/relatorio`, via `useTenantsAdmin()`) | COVERED |
| `PUT /api/v1/platform/tenants/{id}` (`updateTenant`, `:126`) | Herda só o gate de classe; ajusta `plano`/`limiteUtilizadores`; nunca toca `ativo` | COVERED |
| `PATCH /api/v1/platform/tenants/{id}/ativo` (`setTenantAtivo`, `:165`) | Herda só o gate de classe; guarda adicional que bloqueia apenas a suspensão da tenant reservada (`TENANT_RESERVADO`), nunca a reativação | COVERED |
| `tenantRepository.findAll()` em `listTenants` (`PlatformAdminController.java:108`) | Iteração cross-tenant deliberada, documentada no próprio Javadoc do método (`:96-104`); a única fronteira de autorização é o gate de classe herdado — nenhum filtro de tenant é aplicável aqui por desenho | COVERED (c) iteração cross-tenant deliberada, gated |
| `TenantAdminSummaryResponse` (DTO partilhado, `dtos/TenantAdminSummaryResponse.java`) | 6 campos (`id`, `nome`, `plano`, `limiteUtilizadores`, `ativo`, `utilizadoresAtivos`); 0 campos sensíveis — `logoDataUrl`/`nif`/`email`/`telefone`/data de criação omitidos por desenho (Javadoc próprio do DTO); única projeção usada pelas 2 superfícies | COVERED |
| `TenantProvisionResponse` (resposta 201 de `createTenant`) | Apenas `id`+`nome`; nunca a entidade `Tenant` crua, nunca dados do utilizador administrador inicial criado em conjunto | COVERED |
| `use-platform-admin.ts` (as 4 chamadas HTTP da consola) | 5 ocorrências de `apiFetch` (1 import + 4 chamadas: `useTenantsAdmin`/`useCreateTenant`/`useUpdateTenant`/`useSetTenantAtivo`); 0 chamadas fora de `/platform/tenants*`; único ficheiro do frontend com chamadas HTTP reais a este domínio (`plataforma/columns.tsx`, `plataforma/page.tsx` e `plataforma/relatorio/columns.tsx` só importam **tipos** de `@/types/platform-admin`, não chamam `apiFetch`) | COVERED |
| Guard de página `/plataforma` (`PlataformaPage`) | `if (!me.isFetched) return null;` avaliado **antes** de `me.data?.roles?.includes("PLATAFORMA_ADMIN")` (correção WR-03, Phase 120 code review) — nunca fail-open durante o carregamento | COVERED |
| Guard de página `/plataforma/relatorio` (`RelatorioUtilizacaoPage`) | Mesma ordem `!me.isFetched` → verificação de papel; o próprio comentário do ficheiro cita explicitamente "repete aqui a mesma correção... WR-03" | COVERED |
| `GET /api/v1/auth/me` campos `tenant_*` (`UserResponse`: `tenant_id`, `tenant_nome`, `tenant_logo_data_url`, `tenant_plano`, `tenant_limite_utilizadores`) | Endpoint tenant-scoped comum (qualquer utilizador autenticado o alcança), **não** gated a `PLATAFORMA_ADMIN`. `AuthController.getMe()` resolve o `Tenant` exclusivamente via `tenantRepository.findById(principal.getTenantId())`, onde `getTenantId()` vem do `UserPrincipal` do próprio JWT do chamador — nunca de um id fornecido no pedido. Não expõe a lista de tenants nem `utilizadoresAtivos` de terceiros | COVERED (own-tenant-only via `UserPrincipal.getTenantId()` do JWT) |
| `POST /api/v1/setup/initialize` → `SetupService.initializeSystem` | Público (`permitAll()` em `SecurityConfig`), **não** gated a nenhum papel. Bloqueado pelo gate **singleton**: `SetupController.initialize` verifica `setupService.isInitialized()` antes de chamar o serviço, e `initializeSystem` volta a verificar `settings.getInitialized()` (lançando `IllegalStateException`) depois de ler a linha via `findByIdForUpdate` — serializando corridas concorrentes. Contraste: `SetupService.provisionTenant` (caminho repetível, sem gate singleton, sem leitura/escrita de `SystemSettingRepository`) só é invocado por `PlatformAdminController.createTenant`, esse sim gated a `PLATAFORMA_ADMIN` | COVERED (gate singleton, PROV-06 — não gate de papel) |
| `PublicController.getBranding()` / `TenantPublicInfoResponse` | Resposta hardcoded (`nome("LexCV")`, `logoDataUrl(null)`), zero dependências injetadas, zero leitura de repositório ou `SecurityContextHolder` — nunca resolve nenhum tenant real (ISOL-01, fechado na Phase 121) | COVERED (marca genérica por design, não relacionado com PLATAFORMA_ADMIN) |

Nenhuma outra escrita de `Tenant` existe em todo o `backend/src/main/java`: a família de padrão `tenantRepository.(save|delete|findAll)` devolve exactamente 8 ocorrências (ver secção de comandos abaixo), cobrindo só os 5 caminhos já dispostos acima mais o seed (`DatabaseSeeder`, tempo de arranque, não um endpoint HTTP) e o job diário (`AlertasDiariosJob`, já documentado como iteração cross-tenant legítima, NOTF-20/21/23). **Zero ocorrências de `tenantRepository.delete(...)` em qualquer ficheiro** — não existe nenhum caminho de eliminação de tenant.

**Fixes aplicados no Critério 1: 0**

## Comandos de reproducao — Critério 1

Avisos de ambiente para quem re-correr estes comandos (Git Bash / GNU grep 3.0 neste Windows, confirmados nesta própria execução):

1. Em padrões `grep -E`, escrever parênteses literais como classe de caracteres `[(]`, nunca `\(` (trap já documentado em `STATE.md`/`121-CONTEXT.md`).
2. `grep -c` que devolve `0` sai com código de saída `1` — nunca encadear com `&&` a seguir; usar `;` ou linhas separadas.
3. **Novo nesta sessão — MSYS path-mangling:** qualquer padrão de `grep`/`rg` que comece por uma única barra (`/platform`, `/setup`, etc.) é silenciosamente reescrito pelo runtime MSYS2 do Git Bash antes de chegar ao binário `grep`, porque parece um caminho absoluto Unix — o resultado é `0` ocorrências, mesmo quando as ocorrências reais existem. Prefixar o comando com `MSYS_NO_PATHCONV=1` desativa esta conversão. Ver "Divergências" abaixo.
4. **Novo nesta sessão — alternância `|` sem `-E`:** neste `grep` (GNU grep 3.0), um `|` não escapado dentro de um padrão passado a `grep` simples (sem `-E`) é tratado como carácter literal, não como alternância — devolve `0` falsos em vez do valor real. Usar `grep -E` (ou escapar como `\|`) sempre que o padrão precisar de alternância. Ver "Divergências" abaixo.

```bash
# (A) Gate do lado do servidor — PlatformAdminController
grep -n '^@PreAuthorize' backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
# -> 53:@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")   (1 linha, valor literal confirmado)

grep -nE '^[[:space:]]+@PreAuthorize' backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
# -> (vazio) 0 linhas — nenhum handler relaxa o gate de classe

grep -nE '@(Get|Post|Put|Patch|Delete)Mapping' backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
# -> 4 linhas: 67 (POST /tenants), 106 (GET /tenants), 126 (PUT /tenants/{id}), 165 (PATCH /tenants/{id}/ativo)

(cd backend && mvn -q test -Dtest=PlatformAdminControllerTest,SetupControllerSingletonRegressaoTest,SetupServiceProvisionTenantTest)
# -> BUILD SUCCESS (exit 0). Os 3 ficheiros sao @ExtendWith(MockitoExtension.class) puro -- 0
#    ocorrencias de @SpringBootTest como anotacao viva (so em Javadoc a explicar o oposto,
#    mesmo padrao ja registado para SetupControllerSingletonRegressaoTest em 121-ISOL-AUDIT.md) --
#    correm sem BD.

# (B) Trajeto frontend -> HTTP das 2 superficies
grep -c 'apiFetch' web/src/hooks/use-platform-admin.ts
# -> 5 (1 import + 4 chamadas)

grep -oE 'apiFetch<[^(]*[(][^,)]*' web/src/hooks/use-platform-admin.ts | grep -cv 'platform'
# -> 0 (as 4 chamadas tipadas apontam todas para "/platform/tenants...")

MSYS_NO_PATHCONV=1 grep -rn '/platform' web/src --include=*.ts --include=*.tsx
# -> 9 linhas em 4 ficheiros: plataforma/columns.tsx (1, import de tipos), plataforma/page.tsx
#    (2: import de tipos + 1 comentario explicativo), plataforma/relatorio/columns.tsx (1, import
#    de tipos), use-platform-admin.ts (5: import + as 4 chamadas reais). Nenhuma chamada apiFetch
#    fora de use-platform-admin.ts.

# (C) Superficie de dados e caminhos nao-gated-por-papel
grep -cE '^[[:space:]]+private ' backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java
# -> 6

grep -cE '^[[:space:]]+private .*(logoDataUrl|nif|email|telefone|createdAt)' backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java
# -> 0

grep -rnE 'TenantAdminSummaryResponse|TenantProvisionResponse' --include=*.java backend/src/main/java
# -> 11 linhas: 8 em PlatformAdminController.java, 2 em TenantAdminSummaryResponse.java, 1 em
#    TenantProvisionResponse.java. ZERO em qualquer outro controller (PublicController.java so
#    referencia TenantPublicInfoResponse, um DTO distinto).

grep -rnE 'tenantRepository[.](save|delete|findAll)' --include=*.java backend/src/main/java
# -> 8 linhas: PlatformAdminController.java (3: findAll+save+save), AlertasDiariosJob.java
#    (1: findAll, job cross-tenant ja documentado NOTF-20/21/23), DatabaseSeeder.java (2: save+save,
#    tempo de arranque), SetupService.java (2: save+save, initializeSystem+provisionTenant).
#    Zero ".delete(" em qualquer ficheiro.
```

## Divergências entre valor esperado e valor observado

Duas divergências encontradas, ambas classificadas como quirks de ambiente/sintaxe do shell nesta instalação Windows/Git Bash/GNU grep 3.0 — **nenhuma é um achado de código, nenhuma é uma colisão de comentário** (as duas classificações já catalogadas em `121-ISOL-AUDIT.md`); são uma terceira classe, nova nesta sessão, e ficam registadas para que uma fase futura não as repita:

**1. Padrão de `grep` com barra inicial silenciosamente mangled pelo runtime MSYS2 (Git Bash)**
- Comando (tal como escrito na acção do plano): `grep -rn '/platform' web/src --include=*.ts --include=*.tsx`
- Valor esperado: ocorrências confinadas a `use-platform-admin.ts`/`types/platform-admin.ts`
- Valor observado (primeira execução, sem correção de ambiente): `0` ocorrências, código de saída `1`, mesmo em ficheiros confirmados por leitura direta a conter a string literal `/platform/tenants` (`use-platform-admin.ts:26,37,52,67`)
- Causa raiz: o runtime MSYS2 subjacente ao Git Bash neste Windows reescreve automaticamente qualquer argumento de linha de comandos que pareça um caminho absoluto Unix (começa por uma única `/`) — incluindo o próprio *padrão* passado ao `grep`, não só argumentos de caminho de ficheiro — antes de o processo `grep` alguma vez o receber. O padrão `/platform` é interpretado como um caminho, não como texto a procurar, e a pesquisa real corre contra uma string irreconhecível, devolvendo sempre zero.
- Correção/valor real: prefixar com `MSYS_NO_PATHCONV=1` desativa a conversão; a re-execução devolveu os 9 hits corretos em 4 ficheiros (ver secção de comandos acima).
- Classificação: quirk de ambiente (Windows/Git Bash/MSYS2), não um achado de código. Recomendação para o catálogo de known_traps de fases futuras: qualquer padrão de `grep`/`rg` que comece por `/` neste ambiente precisa do prefixo `MSYS_NO_PATHCONV=1`, ou deve ser reescrito sem a barra inicial (ex. `platform/tenants` em vez de `/platform`).

**2. Alternância `|` sem `-E` tratada como carácter literal neste `grep`**
- Comando (tal como escrito no critério de aceitação da Task 1 do plano): `grep -rn 'TenantAdminSummaryResponse|TenantProvisionResponse' --include=*.java backend/src/main/java`
- Valor esperado: as 11 ocorrências legítimas destes 2 DTOs, confinadas a `PlatformAdminController.java` (controller) mais os próprios ficheiros de DTO
- Valor observado (comando tal como literalmente escrito, sem `-E`): `0` linhas, código de saída `1`
- Causa raiz: confirmado por teste isolado (`grep --version` → GNU grep 3.0): sem a flag `-E`, um `|` não escapado dentro do padrão é tratado como carácter literal (regex básica POSIX), não como alternância — o `grep` procura literalmente pela substring `"TenantAdminSummaryResponse|TenantProvisionResponse"` (com o `|` incluído), que não existe em nenhum ficheiro.
- Correção/valor real: `grep -rnE '...'` (com `-E`) ou `grep -rn 'A\|B'` (com `\|` escapado, extensão GNU de BRE) devolvem ambos os 11 hits corretos, verificados por execução direta nesta sessão.
- Classificação: quirk de sintaxe do `grep` instalado (não um achado de código). Um leitor que confiasse cegamente no resultado `0`/exit-1 deste comando tal como escrito no plano teria lido "zero ocorrências em qualquer lado" — o oposto do resultado real (11 ocorrências legítimas, todas confinadas ao ficheiro esperado). Esta é exactamente a classe de falha que esta auditoria existe para prevenir: a Task 1 exige re-derivação por execução, não confiança cega no texto literal de um comando sem o re-correr e sem ler o seu código de saída.

Nenhuma das duas divergências afeta o veredito de nenhuma linha da tabela acima — em ambos os casos, a re-execução corrigida confirma exatamente o resultado já esperado pelo plano.

---

*Secção do Critério de Sucesso 1 produzida pelo Plano 01 da Fase 123. Critério de Sucesso 2 (bloqueio de `PUT /api/v1/admin/rbac`), a decisão sobre o Pitfall 1 (`findByXxxId` sem `tenantId`), e o veredito final da fase são acrescentados pelo Plano 02 — nada acima é reescrito por esse acréscimo.*

---

## Critério de Sucesso 2 — Veredito por superfície

> **ROADMAP.md, Fase 123, Critério de Sucesso 2:** "O bloqueio de `PUT /api/v1/admin/rbac` (Phase 121) é confirmado sem via de contorno — nenhum outro endpoint tenant-facing continua a permitir escrever `Role`/`Permission`."

Enumeração exaustiva (não busca dirigida a `updateRbac`) de toda a escrita das tabelas globais `Role`/`Permission` em `backend/src/main/java`, mais o traçado do único override per-user que continua aberto e a confirmação de que nenhum outro controller expõe qualquer superfície de RBAC.

| Query / Guard | Scope confirmed | Verdict |
|---|---|---|
| `AdminController.updateRbac` (`PUT /api/v1/admin/rbac`, `AdminController.java:409-443`) | **Único** call site HTTP-alcançável que escreve `Role`/`Permission`. Gate de método `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (`:409`) substitui — nunca soma a — o gate de classe `hasRole('ADMIN')` só para este handler. Corpo grava via `role.setPermissions(permissions)` (`:437`) seguido de `roleRepository.save(role)` (`:438`), depois de recusar silenciosamente (`continue`) qualquer tentativa de alterar os papéis `ADMIN` ou `PLATAFORMA_ADMIN` (`:425-427`) | COVERED |
| `AdminController.getRbac` (`GET /api/v1/admin/rbac`, `AdminController.java:356-400`) | Valor literal **actual**, lido nesta fase: `@PreAuthorize("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')")` (`:356`) — alargado pelo CR-01 de `121-REVIEW.md` **depois** de `121-ISOL-AUDIT.md` ter documentado a assimetria `GET`/`PUT`. Só leitura (`roleRepository.findAll()`, `:359`); nunca escreve `Role`/`Permission`. Esta auditoria substitui o precedente nesse ponto específico (ver nota abaixo) | COVERED (leitura alargada por CR-01; escrita permanece fechada a `PLATAFORMA_ADMIN`) |
| `AdminController.updateUser` `user.setPermissions(...)` (`PUT /api/v1/admin/users/{id}`, `AdminController.java:320`) | Escreve `User.permissions` — um `Set<String>` **próprio da linha do utilizador**, nunca a matriz global `Role`→`Permission`. Tenant-scoped por `404` (`:234-236`, `!user.getTenantId().equals(principal.getTenantId())`, avaliado antes de qualquer mutação) mais a guarda de contenção `PLATAFORMA_ADMIN`/`PAPEL_PLATAFORMA_AUTORIDADE` (`:309-314`, `403` antes de `userRepository.save`, `:323`) | COVERED — override per-user own-tenant-only, distinto da matriz global (ver secção própria abaixo) |
| `DatabaseSeeder.seedRbac()` `roleRepository.save` (`DatabaseSeeder.java:377,381`) | Código de arranque. `grep -nE '@RestController\|@Controller\|Mapping' DatabaseSeeder.java` devolve **0** linhas — a classe não é `@RestController`/`@Controller` e não tem nenhum mapping HTTP, logo inalcançável por pedido | COVERED |
| `Role` / `Permission` (entidades JPA, `models/Role.java`, `models/Permission.java`) | Lidas na íntegra: `Role` tem `id`, `nome` (`@Column(unique = true)`), `permissions` (`@ManyToMany`, `@Builder.Default`); `Permission` tem `id`, `nome` (`@Column(unique = true)`). **Nenhum campo de tenant em nenhuma das duas** — tabelas globais de plataforma por desenho, o que confirma o gate a `PLATAFORMA_ADMIN` como a mitigação correta (uma escrita por um tenant afetaria todos os outros) | COVERED |
| `PlatformAdminController` | `grep -rn 'rbac' backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` devolve **0** linhas — nenhum endpoint de RBAC próprio, nenhuma referência a `Role`/`Permission`/`rolePermissions` | COVERED — sem endpoint de RBAC |
| `RbacTab` / `use-admin.ts` (frontend, `web/src/app/(dashboard)/settings/page.tsx`, `web/src/hooks/use-admin.ts:74`) | Leitura via `useAdminRbac()` mais `handleSave` já condicionado a `PLATAFORMA_ADMIN` (Phase 121, WR-01/CR-01) — confirmado por execução: `pnpm verify:bloqueio-rbac` devolve **12/12** asserções `PASS` (`A01`–`A12`), incluindo `A05-guardar-regras-sob-condicao` e `A12-matriz-so-leitura-para-nao-plataforma` | COVERED |

Nenhuma outra escrita de `Role`/`Permission` existe em todo o `backend/src/main/java`. `grep -rnE 'roleRepository[.](save|delete|saveAll|deleteAll)|permissionRepository[.](save|delete|saveAll|deleteAll)' --include=*.java backend/src/main/java` devolve exatamente **3** linhas (as já dispostas acima: `AdminController.java:438` + as 2 de `DatabaseSeeder.java`) — **zero** ocorrências de qualquer escrita de `permissionRepository`. `grep -rnE '[.]setPermissions[(]' --include=*.java backend/src/main/java` devolve exatamente **2** linhas (`AdminController.java:320` e `:437`, ambas já dispostas na tabela acima). Reforço por execução dos testes que provam estes gates de autorização em runtime: `mvn test -Dtest=AdminControllerRbacAutorizacaoTest,AdminControllerPlataformaAdminContencaoTest` → `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`.

**Nota sobre o precedente desatualizado:** `121-ISOL-AUDIT.md` (secção "Assimetria observada em `/admin/rbac`") documentou corretamente, no seu próprio momento, que `getRbac` tinha ficado sem gate de método, herdando só `hasRole('ADMIN')` de classe — um `PLATAFORMA_ADMIN` passava em `PUT` mas recebia `403` em `GET`. O CR-01 do code review dessa mesma fase (`121-REVIEW.md`) alargou `getRbac` para `hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')` — uma correção posterior à escrita de `121-ISOL-AUDIT.md`, nunca retroativamente refletida nesse ficheiro. Esta auditoria (Fase 123) lê o estado **actual** do código-fonte e regista-o como a fonte de verdade corrente; `121-ISOL-AUDIT.md` permanece correto como registo histórico do seu próprio momento, mas desatualizado nesse ponto específico — não precisa de correção retroativa, é o padrão normal de um registo auditável datado.

**Fixes aplicados no Critério 2: 0**

### Comandos de reprodução — Critério 2

```bash
# (A) Enumeracao exaustiva de escritas de Role/Permission
grep -rnE 'roleRepository[.](save|delete|saveAll|deleteAll)|permissionRepository[.](save|delete|saveAll|deleteAll)' --include=*.java backend/src/main/java
# -> 3 linhas: AdminController.java:438 (dentro de updateRbac); DatabaseSeeder.java:377,381 (arranque)

grep -rnE '[.]setPermissions[(]' --include=*.java backend/src/main/java
# -> 2 linhas: AdminController.java:320 (user.setPermissions, dentro de updateUser); AdminController.java:437 (role.setPermissions, dentro de updateRbac)

grep -nE '@RestController|@Controller|Mapping' backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
# -> (vazio) 0 linhas -- DatabaseSeeder nao e alcancavel por HTTP

grep -rn 'rbac' --include=*.java backend/src/main/java
# -> 7 linhas: AdminController.java (@GetMapping/@PutMapping + comentarios + 1 string de permissao
#    "rbac:manage" no DTO), UserPrincipal.java (string de permissao), DatabaseSeeder.java (strings
#    de permissao/comentario) -- ZERO em PlatformAdminController.java ou qualquer outro controller

# (B) Testes de autorizacao em runtime
(cd backend && mvn test -Dtest=AdminControllerRbacAutorizacaoTest,AdminControllerPlataformaAdminContencaoTest)
# -> Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 | BUILD SUCCESS

# (C) Handler morto -- manifesto de rotas construido
node -e "const m=require('./web/.next/app-path-routes-manifest.json'); const k=Object.keys(m); console.log('rotas:',k.length,'| rotas de api:',k.filter(x=>x.includes('api')).length)"
# -> rotas: 36 | rotas de api: 0
grep -c '_api-backup' web/.next/app-path-routes-manifest.json   # -> 0 (exit 1)
grep -c 'admin/rbac' web/.next/app-path-routes-manifest.json    # -> 0 (exit 1)

# Proveniencia do manifesto (nao pode ser citado sem confirmar que reflete o codigo actual)
stat -c '%y %n' web/.next/app-path-routes-manifest.json web/src/app/_api-backup/v1/admin/rbac/route.ts
# -> manifesto: 2026-07-30 08:45:31 | route.ts: 2026-05-27 14:51:24 (manifesto mais recente -- sem necessidade de rebuild)
git log -1 --format='%h %ad %s' --date=short -- web/src/app/_api-backup
# -> 40008dc8 2026-06-17 (mais antigo que o manifesto -- confirma que o manifesto reflete o codigo actual)

# (D) Nenhum cliente HTTP vivo alcanca rotas internas do Next.js
grep -nE 'API_BASE|fetch[(]' web/src/lib/api.ts
# -> 4 linhas: API_BASE = NEXT_PUBLIC_API_BASE_PATH; fetch(`${API_BASE}${path}`, ...)
grep -rn '_api-backup' web/src --include=*.ts --include=*.tsx
# -> 0 linhas em TODO o web/src, incluindo a propria subarvore _api-backup (nao se auto-referencia por string)

# (E) Varredura negativa adicional de superficies de dados RBAC
grep -rniE 'rolePermissions|systemPermissions' web/src --include=*.ts --include=*.tsx
# -> ocorrencias confinadas a exatamente 4 ficheiros: settings/page.tsx (RbacTab, consumo vivo via
#    apiFetch real), _api-backup/v1/admin/rbac/route.ts (handler morto, ja tracado abaixo),
#    use-admin.ts (hook vivo -- so "import type" de mock-db para tipos TypeScript, apagado em
#    tempo de compilacao, mais a chamada real apiFetch("/admin/rbac")), mock-db.ts (dados
#    simulados em memoria, sem qualquer ligacao a base de dados)

(cd web && pnpm verify:bloqueio-rbac)
# -> PASS A01..A12 (12/12)

git status --porcelain -- backend web
# -> (vazio)
```

Nenhuma divergência de ambiente nova encontrada nesta secção — os 2 quirks já catalogados na secção do Critério de Sucesso 1 (MSYS path-mangling em padrões com barra inicial; `|` sem `-E` tratado como carácter literal) foram evitados aqui por desenho: nenhum padrão desta secção começa por `/`, e todo padrão com alternância usou `-E`.

---

## Achado traçado e descartado — handler morto de RBAC em `_api-backup`

Esta secção é obrigatória por decisão trancada em `123-CONTEXT.md`: o achado "deve entrar na auditoria como traçado e descartado... não silenciosamente ignorado por ser óbvio."

**O que o handler faz:** `web/src/app/_api-backup/v1/admin/rbac/route.ts` exporta `GET` e `PUT`. Ambos chamam `getAuthContext(req)` (que lê um cabeçalho `Authorization: Bearer` e decodifica-o com `parseMockJwt`, a implementação de JWT simulada pré-backend) e verificam apenas `ctx.roles.includes("ADMIN")` (`:13`, `:32`) — **sem qualquer distinção de `PLATAFORMA_ADMIN`**. O `PUT` escreve diretamente `mockDb.rolePermissions = body.rolePermissions` (`:48`), sem validação de estrutura além de confirmar que a chave existe.

**Por que parece, à primeira vista, uma via de contorno tenant-facing:** um `ADMIN` de qualquer escritório (não só `PLATAFORMA_ADMIN`) passaria o gate `ctx.roles.includes("ADMIN")` deste handler, exatamente o mesmo papel que o bloqueio de `updateRbac` (Phase 121, ISOL-03) foi desenhado para excluir da escrita da matriz. Se este handler fosse alcançável, seria uma via de contorno real e completa do Critério de Sucesso 2.

**Três provas independentes de inalcançabilidade** — nenhuma delas é o único ponto de falha do argumento; a conclusão sobrevive à falha de qualquer uma isoladamente:

1. **Manifesto de rotas construído.** O prefixo `_` faz o Next.js App Router tratar `_api-backup` como pasta privada, excluída de toda a árvore de routing. Prova pelo artefacto de build, não pela documentação: `web/.next/app-path-routes-manifest.json` tem **36 rotas no total, 0 rotas de API, 0 ocorrências de `_api-backup`, 0 ocorrências de `admin/rbac`** (comandos e saída na secção "Comandos de reprodução — Critério 2" acima). Proveniência confirmada antes de citar o manifesto: `stat` mostra o manifesto datado de `2026-07-30 08:45:31`, mais recente do que o último commit a tocar `web/src/app/_api-backup` (`40008dc8`, `2026-06-17`) e do que o próprio `mtime` do ficheiro `route.ts` (`2026-05-27 14:51:24`) — o manifesto reflete o código atual, não precisou de rebuild para esta auditoria.
2. **Nenhum cliente HTTP desta app alcança rotas internas do Next.js.** `web/src/lib/api.ts`'s `apiFetch` constrói toda e qualquer URL como `` `${API_BASE}${path}` ``, onde `API_BASE` é `NEXT_PUBLIC_API_BASE_PATH` (`/api/v1`); `web/next.config.ts` reescreve `/api/v1/:path*` → `` `${BACKEND_API_ORIGIN}/api/v1/:path*` `` — sempre para o backend Spring externo, nunca para uma rota interna Next.js. Confirmado por leitura de ambos os ficheiros e por contagem: `grep -rn '_api-backup' web/src --include=*.ts --include=*.tsx` devolve **0 linhas em todo o `web/src`**, incluindo a própria subárvore `_api-backup` (o handler morto não se auto-referencia por string em lado nenhum).
3. **Dependências mortas, estruturalmente incapazes de escrever dados reais.** O handler importa `@/server/mock-db` (`mockDb.rolePermissions`/`mockDb.systemPermissions` são constantes JavaScript simples em memória — `rolePermissions`/`systemPermissions` em `web/src/server/mock-db.ts:492-565` —, sem qualquer import de driver de base de dados, ORM, ou cliente HTTP) e `@/server/request-auth` (`getAuthContext` decodifica um JWT simulado próprio via `parseMockJwt`, e espera um cabeçalho `Authorization: Bearer`, não os cookies `access_token`/`refresh_token` httpOnly que a aplicação real usa — ver `CLAUDE.md`: "Auth is JWT in httpOnly cookies... There are no bearer tokens in JS"). Mesmo numa execução hipotética deste handler, `mockDb.rolePermissions` nunca chega a tocar as tabelas `t_role`/`t_permission` do PostgreSQL — é uma estrutura de dados totalmente desconectada, e o mecanismo de autenticação que o protege nem sequer corresponde ao que o browser desta app envia.

**Disposição:** traçado e descartado — inalcançável, sem ligação a dados reais mesmo que fosse alcançado. **Não** é um achado de código a corrigir nesta fase.

**Recomendação de limpeza técnica (opcional, diferida):** remover a subárvore `web/src/app/_api-backup/` (33 ficheiros `route.ts` no total, dos quais este é apenas um) é uma limpeza técnica legítima, mas está explicitamente em `123-CONTEXT.md` > Deferred Ideas — **não executada nesta fase**. Candidato para uma fase de manutenção futura, sem urgência funcional ou de segurança.

---

## Decisão sobre o Pitfall 1 — findByXxxId sem tenantId (risco residual conhecido e aceite)

`121-ISOL-AUDIT.md` (secção "Registado mas explicitamente fora de âmbito para ISOL-02") nomeou esta fase como dona de uma decisão pendente. A decisão está tomada em `123-CONTEXT.md` e é reproduzida aqui fielmente, não reinterpretada.

**O padrão:** `.planning/research/PITFALLS.md`, Pitfall 1, documenta que vários métodos de repositório (exemplo citado: `ProcessoRepository.findByClienteId(UUID)`, ~11 métodos `findByXxxId` equivalentes no total) não têm parâmetro `tenantId` na própria assinatura da query. São seguros hoje apenas porque cada call site atual revalida separadamente que a entidade-pai pertence ao tenant do chamador antes de usar a linha devolvida — a segurança é transitiva e re-derivada em cada ponto de uso, nunca imposta pela própria query. `Honorario` é o caso mais exposto: não tem nenhuma coluna `tenant_id` própria, ligando-se ao tenant apenas através de `processo_id`.

**Veredito: aceite como risco residual conhecido.** Explicitamente **não** "corrigido" e explicitamente **não** "fora de âmbito silenciosamente ignorado" — a dupla verificação já existente em cada call site é aceite como mitigação suficiente por agora. As 4 razões, tal como registadas em `123-CONTEXT.md`:

1. **Padrão pré-existente há várias milestones**, não introduzido nem agravado especificamente por esta milestone (v2.16) — o risco teórico ("qualquer tenant vs. qualquer tenant") já existia antes de qualquer trabalho multi-tenant explícito ter começado.
2. **Cada call site já confirmado**, em investigações anteriores desta sessão e em `PITFALLS.md`, a fazer a reverificação corretamente — zero indícios de um call site que a tenha omitido.
3. **Corrigir por completo exigiria alterar a assinatura de ~11 métodos de repositório e todos os seus call sites** — um refactor grande, com risco de regressão real, claramente fora do âmbito desta milestone (que é sobre as 3 superfícies novas — consola, relatório, bloqueio de RBAC — não uma auditoria de segurança total e histórica de todo o codebase).
4. **Não bloqueia a criação segura de um 2º tenant pagante real** — o risco já existe hoje independentemente de quantos tenants reais existirem, e nenhuma das 3 superfícies novas desta milestone o agrava (a Phase 120 acrescenta um *segundo* tenant à base de dados, mas não introduz nenhum caminho novo de acesso a `Honorario`/`findByClienteId` — os controllers que os usam são os mesmos de sempre, com a mesma dupla verificação de sempre).

**Recomendação clara:** a eliminação completa deste padrão exige uma fase dedicada de refactor de repositórios — matéria para uma milestone futura, não para agora. Não deve ser reaberta como decisão pendente numa fase futura sem essa fase dedicada existir primeiro.

---

## Evidência ao vivo já reunida (citada, não repetida)

**Justificação de não repetir UAT ao vivo nesta fase:** esta auditoria é uma confirmação estática + automatizada de decisões já verificadas ao vivo nas próprias fases que construíram cada superfície. Repetir esse UAT aqui não produziria informação nova sobre o comportamento em runtime — produziria apenas uma segunda execução dos mesmos cenários já documentados.

**Evidência HTTP real já recolhida, por fase:**

- **Phase 120 Plano 06:** `403` confirmado para um `ADMIN` de escritório nos 3 endpoints de plataforma (`POST`/`GET`/`PATCH /platform/tenants*`), mais o corte imediato de uma sessão já aberta ao suspender esse tenant em runtime (não apenas no próximo login).
- **Phase 121 Plano 04:** `403` real de um `ADMIN` de escritório em `PUT /admin/rbac` **e**, no mesmo endpoint, `200` de um `PLATAFORMA_ADMIN` real — o contra-teste que exclui a possibilidade de o bloqueio ter ficado universal por acidente (ou seja, prova que o gate distingue os dois papéis, não que bloqueia toda a gente).
- **Phase 122 Plano 04:** `200`/`403` confirmados no relatório de utilização, mais o tenant suspenso (Phase 120) presente na resposta do relatório com o seu estado identificado, não desaparecido da lista.

**Duas razões pelas quais a re-verificação estática + automatizada é suficiente aqui, em vez de repetir UAT ao vivo:**

(a) **Os gates estão sob cobertura de regressão automatizada permanente**, correndo a cada execução da suite de testes — não um `curl` de uma só vez, esquecível e não repetível sem esforço manual: `PlatformAdminControllerTest`, `AdminControllerRbacAutorizacaoTest`, `AdminControllerPlataformaAdminContencaoTest`, `JwtAuthenticationFilterTenantSuspensoTest`, `AuthControllerTenantSuspensoTest` — todas as 5 classes confirmadas existentes nesta sessão (`backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java`, `backend/src/test/java/com/lexcv/controllers/AuthControllerTenantSuspensoTest.java`, `backend/src/test/java/com/lexcv/config/JwtAuthenticationFilterTenantSuspensoTest.java`, mais as 2 já corridas nesta fase com `BUILD SUCCESS`).
(b) **Precedente direto da AUD-01** (`.planning/milestones/v2.11-phases/LEXCV-97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente/97-01-SUMMARY.md`), que saltou a sonda cross-tenant ao vivo sob a mesma cláusula de discrição de auditoria de confirmação, e foi aceite como suficiente pelo utilizador nessa milestone.

**Registo honesto de uma limitação real, não escondida:** a Fase 122 fechou com um override formal do utilizador para 6 cenários de verificação visual bloqueados por uma falha de ferramenta (Browser MCP, "pane hidden/not displayed"), não uma falha do produto — confirmado por saúde direta de backend/frontend via `curl` durante a mesma janela de tempo (ver `STATE.md`, decisão de Phase 122). Repetir esse trabalho aqui provavelmente reencontraria o mesmo bloqueio de ferramenta, por zero informação nova sobre o produto em si — não é uma razão para reabrir esse override, apenas uma transparência sobre o que continua por confirmar visualmente.

---

## Veredito final por superfície

Uma linha por superfície nova da v2.16, com veredito nomeado, mais a confirmação (não a repetição por fé) da afirmação do ROADMAP de que `StorageService` está fora de âmbito.

| Superfície | Fase que a construiu | Veredito | Evidência-chave |
|---|---|---|---|
| Consola de administração de tenants (`/plataforma`, `PlatformAdminController`) | Phase 120 | **COVERED** | Gate de classe único `hasRole('PLATAFORMA_ADMIN')`, 0 exceções de método; `TenantAdminSummaryResponse` com 6 campos, 0 sensíveis; guard de página `!me.isFetched` antes do papel (WR-03) — tabela completa no Critério de Sucesso 1 (Plano 01) |
| Relatório de utilização (`/plataforma/relatorio`) | Phase 122 | **COVERED** | Consome o mesmo `GET /platform/tenants` gated, sem endpoint próprio; mesma contagem `countByTenantIdAndAtivoTrue` da Phase 117 (fonte única); tenant suspenso confirmado presente na resposta (`listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta`) — tabela completa no Critério de Sucesso 1 (Plano 01) |
| Bloqueio de RBAC (`PUT /api/v1/admin/rbac`) | Phase 121 | **COVERED** | `AdminController.updateRbac` provado, por enumeração exaustiva (não busca dirigida), como o único call site HTTP-alcançável que escreve `Role`/`Permission`; handler morto de `_api-backup` traçado e descartado com 3 provas; override per-user de `updateUser` dispositionado como own-tenant-only — tabela completa no Critério de Sucesso 2 acima |
| `StorageService` (upload/download de documentos) | Fora de âmbito desta milestone (já tenant-partitioned) | **COVERED — confirmado por grep, não copiado por fé** | `grep -n 'tenantId' backend/src/main/java/com/lexcv/services/StorageService.java` devolve 2 linhas: `upload(UUID tenantId, ...)` na assinatura do método (`:39`) e `objectKey = tenantId.toString() + "/" + documentoId.toString() + "/" + ...` (`:42`) — cada objeto MinIO é gravado sob um prefixo de caminho próprio do tenant, confirmando o particionamento que o `ROADMAP.md` já declarava sem exigir alteração de código nesta milestone |

**Fixes aplicados nesta fase (Plano 02): 0**
**Fixes aplicados em toda a Fase 123 (Planos 01+02): 0**

---

## Pré-condição para provisionar um 2º tenant pagante real

O Critério de Sucesso 3 exige que o veredito esteja "documentado antes de se considerar segura a criação de um 2º tenant pagante real fora de teste." Declaração explícita de go/no-go:

**Pré-condições de deployment que continuam a aplicar-se** (já registadas em `STATE.md`, reconfirmadas aqui, não repetidas por fé):

1. `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` — script de execução manual, tem de correr contra a base de dados de produção antes ou junto do próximo deploy (não existe runner de migrações neste repositório).
2. `backend/migrations/120-add-tenant-ativo.sql` — idem, execução manual obrigatória antes/junto do próximo deploy.
3. `backend/migrations/120b-backfill-tenant-plano.sql` — corrida apenas nesta base de dados de **desenvolvimento** (2026-07-30, durante o UAT ao vivo da Phase 122). Um deployment de produção que a saltar sofre um `500 DataIntegrityViolationException` real na **primeira** tentativa de suspender um tenant contra dados pré-existentes (`@Builder.Default` só fornece omissão para entidades construídas via builder Lombok, não para entidades que o Hibernate carrega de uma coluna já populada e ainda anulável) — confirmado em runtime nesta sessão do projeto, não uma suposição teórica. Este é um bloqueador **funcional**, não cosmético.
4. **Restrição de ordem do Risco da Phase 121** (`ROADMAP.md`, nota de Risco): não usar a consola de criação de tenants da Phase 120 em produção para um 2º tenant real antes de a Phase 121 (bloqueio de RBAC) estar também em produção. Estado atual: o código de ambas as fases (120 e 121) já reside no mesmo repositório/branch nesta altura — a restrição só é relevante para um deploy parcial ou desatualizado que tivesse o código da Phase 120 sem o da Phase 121; um deploy a partir do estado atual do repositório inclui as duas em conjunto, pelo que esta restrição de ordem está satisfeita **desde que o deploy de produção parta do estado atual do repositório**, não de uma revisão antiga.

**Declaração de go/no-go:** com as 4 pré-condições acima aplicadas (as 3 migrações manuais executadas em produção e o deploy partindo do estado atual do repositório, não de uma revisão antiga), as 3 superfícies novas desta milestone estão confirmadas, por prova executável nesta fase e nas fases 120/121/122, a isolar corretamente os dados entre tenants. **GO condicional** — condicional às 4 pré-condições de deployment listadas, não a trabalho de código adicional.

### O que esta auditoria NÃO cobriu

Delimitação explícita de âmbito, para que nenhum leitor sobre-interprete o veredito acima como mais amplo do que é:

- **O padrão do Pitfall 1 em todo o codebase histórico** — aceite como risco residual conhecido (secção própria acima), não eliminado. Continua a exigir dupla verificação manual em qualquer novo call site que reutilize `findByXxxId` sem `tenantId`.
- **Segurança de infraestrutura/rede** (firewall, configuração TLS, hardening do Docker/VPS/Caddy) — fora do âmbito de qualquer auditoria desta milestone, que se concentra exclusivamente em isolamento de dados ao nível da aplicação.
- **A limpeza do handler morto `_api-backup`** — confirmada inofensiva e inalcançável, mas a sua remoção (33 ficheiros) permanece diferida como limpeza técnica opcional, não executada nesta fase.

---

*Secção do Critério de Sucesso 2, decisão do Pitfall 1, evidência ao vivo citada, veredito final por superfície e pré-condição de go/no-go produzidos pelo Plano 02 da Fase 123 — nada nas secções acima do Critério de Sucesso 1 foi reescrito por este acréscimo. Zero alterações de código de produção: `git status --porcelain -- backend web` vazio antes e depois deste plano.*
