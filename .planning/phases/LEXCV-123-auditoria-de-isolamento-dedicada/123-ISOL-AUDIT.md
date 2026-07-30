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
