# Phase 128: Auditoria de Atribuições de Papéis - Context

**Gathered:** 2026-09-22
**Status:** Ready for planning
**Mode:** Smart discuss (corrida autónoma). Última fase do marco.

<domain>
## Phase Boundary

Toda a alteração de papel e toda a atribuição de papel feitas pela superfície de escritório ficam registadas de forma permanente, com autor, momento e alvo. O administrador de escritório consulta esse registo, restrito ao seu próprio escritório. Nenhuma superfície da aplicação permite editar ou apagar um registo.

Dentro do âmbito: gravar os eventos nos caminhos de escrita criados pela Phase 127, um endpoint de consulta com âmbito de tenant, uma secção de consulta nas Definições, e a garantia de imutabilidade.

Fora do âmbito: exportar o registo (Out of Scope em REQUIREMENTS.md, levanta questões de dados pessoais) e auditar edições de moldes pela plataforma (os moldes não pertencem a nenhum tenant e `t_audit_log.tenant_id` é obrigatório).
</domain>

<decisions>
## Implementation Decisions

### Decisão 1: reaproveitar `t_audit_log`, sem segundo registo

Já existe um registo de auditoria em uso. A entidade `AuditLog`, na tabela `t_audit_log`, tem `tenant_id`, `processo_id` (nullable), `acao`, `entidade_tipo`, `entidade_id` (String, para aceitar UUID e Integer), `autor_id` (nullable, para eventos de sistema) e `timestamp`. É escrita em cinco pontos do `ParecerController` e cinco do `ResourceController`, e consultada por `GET /processos/{id}/audit` e pelo ecrã de detalhe do processo.

Criar uma segunda tabela de auditoria só para RBAC dividiria o histórico de um escritório em dois sítios sem necessidade. Os eventos novos entram na mesma tabela, com valores novos de `acao` e `entidade_tipo`. Os valores existentes estão documentados em comentários na própria entidade. Actualizar esses comentários com os novos valores.

### Decisão 2: uma coluna nova, `detalhe`, porque AUDT-01 exige "o que mudou"

A tabela regista quem, quando, que acção e sobre que entidade. Não regista o conteúdo da mudança. AUDT-01 pede explicitamente "o que mudou" (permissões acrescentadas ou retiradas, nome antigo e novo) e AUDT-02 pede o utilizador alvo e os papéis atribuídos ou retirados. Sem um campo para isso, o registo diz que algo aconteceu mas não o quê, e deixa de servir para responder à pergunta que um escritório vai fazer ("quem deu acesso financeiro a esta pessoa?").

Decisão: acrescentar uma coluna **nullable** `detalhe` com o conteúdo da mudança em forma estruturada. É aditiva e nullable, por isso os dez pontos de escrita existentes continuam válidos sem alteração.

Isto exige a migração manual **128**. Já há oito pendentes em produção. É a única alteração de esquema desta fase e deve ser o mais pequena possível: `ADD COLUMN IF NOT EXISTS`, idempotente, no inventário do README em ordem numérica. Nota para o planeamento: `t_audit_log` **não tem script em `backend/migrations/` nem entrada no inventário**, foi criada pelo `ddl-auto: update`. O CLAUDE.md diz que todos os ambientes correm `update` hoje, logo a tabela existe em produção. Mas um script `ALTER TABLE` sobre uma tabela que o inventário nunca registou merece uma linha no README a dizer de onde ela vem.

`detalhe` guarda nomes de papéis e chaves de permissão, que são configuração. Não guarda nome, email nem outro dado pessoal do utilizador alvo: esse vai por id em `entidade_id`, resolvido para apresentação na leitura. Assim o registo não duplica dados pessoais que depois ficariam a divergir da ficha do utilizador.

### Decisão 3: o evento e a mudança gravam na mesma transacção

Um registo de auditoria que pode faltar quando a mudança aconteceu, ou existir quando a mudança falhou, não serve. O evento grava-se na mesma transacção da alteração que descreve: ou ficam ambos, ou nenhum.

Pontos de escrita a cobrir, todos criados ou alterados pela Phase 127:
- `OfficeRolesController`: criar (`POST`), renomear (`PUT /{id}`), apagar (`DELETE /{id}`)
- `AdminController.updateRbac`: alteração de permissões de papéis
- `AdminController.createUser` / `updateUser`: papéis atribuídos e retirados a um utilizador
- `AdminController.deleteUser`: o utilizador apagado perde os seus papéis. Registar como retirada, para o histórico não ficar com uma atribuição sem fim

Um pedido recusado (409 do último administrador, 403 do `PLATAFORMA_ADMIN`, 404 de outro tenant) não grava evento. Não houve mudança.

O autor e o tenant vêm do principal autenticado, nunca do corpo do pedido.

### Decisão 4: o que não se audita, e porquê

- **A conversão de arranque da Phase 126** (`MigracaoPapeisEscritorioService`) não gera eventos. Não é uma atribuição feita por uma pessoa: é uma migração de dados que preserva o acesso de cada utilizador exactamente, e a verificação de deriva zero prova isso. Registá-la inundaria o histórico de cada escritório com eventos que não correspondem a decisões de ninguém.
- **Edições de moldes pela plataforma** não entram, pelo motivo dado no Phase Boundary.
- **O provisionamento de um escritório novo** (`SetupService.provisionTenant` atribui o papel de administrador ao utilizador fundador) fica ao critério do executor. É uma atribuição real, feita pelo `PLATAFORMA_ADMIN` a partir da consola. Registá-la dá ao escritório a primeira linha do seu histórico. Se for simples de fazer na mesma transacção, fazer. Se não for, documentar e deixar de fora.

### Decisão 5: consulta restrita ao tenant, sob `rbac:manage`

`GET` de consulta com âmbito de tenant, gated por `hasAuthority('rbac:manage')`, a mesma autoridade que governa a escrita dos papéis. Devolve apenas os eventos de RBAC (filtrados por `entidade_tipo`), não os de processos, que já têm a sua própria vista. Tem de ser paginado, porque o registo cresce sem limite.

AUDT-03 exige que um escritório nunca veja eventos de outro. Provar com um teste de dois tenants, no mesmo padrão que a Phase 127 usou para o isolamento dos papéis.

### Decisão 6: imutável por construção, não por disciplina

AUDT-04 pede que o registo não seja editável nem apagável a partir de nenhuma superfície. Não chega não expor um endpoint. O `AuditLogRepository` estende `JpaRepository`, que traz `delete`, `deleteAll`, `saveAll` sobre entidades existentes, e qualquer código futuro pode chamá-los.

Tornar a imutabilidade estrutural:
- O repositório passa a expor só o que é preciso (gravar um evento novo e as consultas), por exemplo estendendo `Repository<AuditLog, Long>` com métodos declarados, em vez de `JpaRepository`. Assim os métodos de apagar deixam de existir na API, em vez de existirem e ninguém os chamar. Confirmar que os dez pontos de escrita e a consulta de processos continuam a compilar.
- A entidade marca-se como imutável para o Hibernate (`@Immutable`), para que a verificação de alterações (dirty-checking) nunca emita um `UPDATE` sobre um registo carregado.
- Um teste ou gate estrutural que falhe se aparecer um endpoint de escrita sobre o registo, ou uma chamada de remoção.

### Claude's Discretion
O formato exacto de `detalhe` (JSON ou texto estruturado), os valores novos de `acao`/`entidade_tipo`, a forma da paginação, e se o provisionamento é auditado (Decisão 4) ficam ao critério do executor, desde que as seis decisões acima sejam respeitadas.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/src/main/java/com/lexcv/models/AuditLog.java`: a entidade a estender (coluna `detalhe`, `@Immutable`, comentários de vocabulário)
- `backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java`: estende hoje `JpaRepository`, e o único método derivado é `findByTenantIdAndProcessoIdOrderByTimestampDesc`
- `ResourceController.java:2356` (`GET /processos/{id}/audit`) e `web/src/app/(dashboard)/processos/[id]/page.tsx` com `web/src/hooks/use-processos.ts`: a consulta e a vista de auditoria que já existem, a analogia directa para a consulta nova
- As dez chamadas `auditLogRepository.save(AuditLog.builder()...)` em `ParecerController` e `ResourceController`: o padrão de escrita a seguir
- `OfficeRolesController` e `AdminController` (Phase 127): os pontos de escrita a instrumentar
- O teste de isolamento de dois tenants da Phase 127 (`AdminControllerRbacEscritorioTest`): o padrão para provar AUDT-03

### Established Patterns
- `@PreAuthorize` com `hasAuthority` para permissões (não levam o prefixo `ROLE_`); prova com proxy real (`AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory`)
- Tenant e autor sempre do principal autenticado
- Frontend: TanStack Query + `apiFetch`, sem `useEffect` para chamadas de negócio, primitivos shadcn já presentes, sem dependências novas
- Migração manual obrigatória em produção. Próximo número livre: **128**

### Integration Points
- Definições (`web/src/app/(dashboard)/settings/page.tsx`): nova secção ou separador de consulta
- `web/src/hooks/use-admin.ts`: novo hook de consulta
</code_context>

<specifics>
## Specific Ideas

A pergunta que este registo tem de conseguir responder é concreta: "quem deu a esta pessoa acesso ao financeiro, e quando?". Se o ecrã de consulta não permitir chegar a essa resposta sem ler o histórico todo, falha o propósito. Pelo menos filtrar por utilizador alvo e por papel.

Os eventos devem ler-se em português de Cabo Verde e em frases humanas ("Maria Silva retirou o papel Advogado a João Pires"), não como códigos de `acao`.
</specifics>

<deferred>
## Deferred Ideas

- Exportação do registo de auditoria: Out of Scope em REQUIREMENTS.md, por dados pessoais
- Auditoria das edições de moldes pela plataforma: exigiria um registo sem tenant, ou uma decisão sobre a que tenant pertence um evento de plataforma
- Retenção e arquivo do registo: cresce sem limite; sem requisito hoje
- Levar a vista de auditoria de processos e a de RBAC para um único ecrã de histórico do escritório
</deferred>
