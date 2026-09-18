# Proposta — Estratégia de Distribuição Multi-Tenant e Controlo de Faturação por Utilizadores

**Produto:** ALCv — plataforma de gestão de escritórios jurídicos (Cabo Verde)
**Data:** 28 de julho de 2026
**Base:** leitura do código atual (`backend/`, `web/`) e do histórico em `.planning/` (PROJECT.md, ROADMAP.md, STATE.md, MILESTONES.md, RETROSPECTIVE.md)

## 1. Resumo executivo

O ALCv já tem a fundação técnica certa para multi-tenancy — todas as entidades têm `tenant_id`, o contexto de segurança injeta-o via JWT, e há um histórico de auditorias (AUD-01) e correções de IDOR que mostram que o isolamento entre tenants é levado a sério. O que falta não é isolamento de dados — é **distribuição**: hoje, cada escritório novo significa um deployment novo (VPS, Postgres, MinIO, Docker Compose, DNS, certificado). Isso não escala e não dá controlo centralizado sobre quantos utilizadores cada cliente tem.

A recomendação central desta proposta é: **evoluir de "1 deployment por escritório" para uma instância partilhada onde cada escritório é uma `Tenant` na mesma base de dados**, reaproveitando o isolamento por `tenant_id` que já existe. Isto é o que torna "agregar escritórios" barato e rápido (criar uma linha, não uma VPS), e é o que torna o controlo de utilizadores para faturação trivial (uma tabela, não N bases de dados separadas).

Isto **reabre deliberadamente uma decisão já tomada** na v2.12 (ver secção 3) — não é um esquecimento, é uma mudança de objetivo que vale a pena assumir explicitamente.

A proposta cobre quatro blocos: (a) o que já existe e ajuda, (b) o modelo de distribuição alvo, (c) como limitar utilizadores por tenant e usar isso para faturar, e (d) um plano faseado que encaixa no vosso workflow `.planning/` existente. Termina com decisões que só tu podes tomar.

## 2. Onde estamos hoje

Factos verificados diretamente no código:

- **Isolamento por tenant já existe e é a norma do projeto.** Toda entidade de domínio tem `tenant_id`; `UserPrincipal.tenantId` vem do JWT; controllers filtram por `getTenantId()`. Isto é tratado como regra dura no `CLAUDE.md` ("must not be bypassed") e reforçado por uma auditoria dedicada (AUD-01, fechada na v2.11, zero falhas encontradas).
- **Mas, na prática, só existe (e só pode existir) um `Tenant` por deployment.** `SetupService.initializeSystem()` cria a `Tenant` e o utilizador ADMIN uma única vez, protegido por uma flag singleton (`SystemSetting.initialized`). Não há *constraint* na base de dados que impeça uma 2ª `Tenant` — é só a aplicação que impede. O próprio código já assinala isto como uma bomba-relógio conhecida: em `TenantRepository.java`, um comentário (WR-01, revisão da Phase 98) diz literalmente que a query que devolve "a tenant" assume deployment single-tenant e que **"se uma futura feature de onboarding multi-tenant permitir uma 2ª Tenant, este call site tem de ser revisitado"**. `PublicController.getBranding()` já regista um `log.warn` se detetar mais do que uma tenant. Ou seja: o código já está, ele próprio, a "pedir" esta funcionalidade.
- **A entidade `Tenant` já tem os campos certos para um SaaS multi-cliente:** `nome`, `nif`, `tipoEntidade`, `email`, `telefone`, `logoDataUrl`. Isto já suporta branding por escritório (nome + logótipo) sem alterações.
- **O armazenamento de documentos já está particionado por tenant.** `StorageService.upload()` grava no MinIO com a chave `tenantId/documentoId/ficheiro` — um único bucket, mas já namespaced. Não precisa de mudanças estruturais para suportar vários escritórios em simultâneo.
- **O email de utilizador já é único globalmente** (`User.email`, `unique = true`), não só por tenant. Isto é uma simplificação valiosa: o login pode continuar a ser "email + password" sem precisar de subdomínio por escritório para saber a que tenant pertence o utilizador — o backend resolve o `tenantId` a partir do próprio `User`, tal como já faz hoje.
- **Descoberta relevante para esta proposta:** `Role` e `Permission` são entidades **globais**, sem `tenant_id`. O endpoint `PUT /api/v1/admin/rbac` (em `AdminController`) reescreve o mapeamento role→permissions **para toda a instalação**. Hoje isso é invisível porque só há uma tenant. No dia em que houver duas, um ADMIN do Escritório A que ajuste as permissões do papel TECNICO estaria a alterar as permissões do TECNICO do Escritório B também. Isto tem de ser resolvido antes de ligar multi-tenant a sério (ver secção 7).
- **Não existe nenhum mecanismo de onboarding automatizado ou self-service.** O site institucional (`webpage/`) tem apenas um CTA de "pedir demonstração" (mailto). O único caminho de entrada de um novo cliente é manual/comercial.
- **Não existe nenhum código, campo ou plano relacionado com limites de utilizadores, planos ou faturação.** Procurei isto especificamente no histórico de planeamento — é terreno completamente livre, não há nada para desfazer.

## 3. Isto reabre uma decisão já tomada — de propósito

O `PROJECT.md` regista, como decisão confirmada na **v2.12**, que o onboarding self-service multi-instituição (com slug/subdomínio por tenant) ficou **fora de escopo**, e que "este deployment continua a servir uma única instituição, o wizard `/setup` mantém-se singleton." Faz sentido mencionar isto abertamente: esta proposta não ignora essa decisão, está a propor revisitá-la porque o objetivo mudou. Em v2.12 o objetivo era lançar a landing page com o mínimo de risco; agora o objetivo é escalar a distribuição comercial e faturar por número de utilizadores — algo que o modelo "um deployment por cliente" torna estruturalmente difícil (não há um sítio central onde ver "quantos utilizadores tem cada cliente" sem entrar em N bases de dados diferentes).

Não é preciso desfazer a decisão de "sem self-service público" — só a parte "um deployment serve só uma instituição para sempre".

## 4. Modelo de distribuição proposto

### 4.1 Partilhado por omissão, isolado como opção premium

Há dois modelos possíveis e vale a pena nomear os dois honestamente:

| | **Multi-tenant partilhado** (recomendado, por omissão) | **Isolado por cliente** (mantido como opção) |
|---|---|---|
| Como se adiciona um escritório | Cria-se uma linha `Tenant` + utilizador ADMIN — minutos | Provisiona-se VPS/Postgres/MinIO/DNS/TLS novos — mesmo scriptado, sempre mais lento |
| Custo marginal por cliente | Baixo (recursos partilhados) | Alto (infraestrutura própria por cliente) |
| Ver utilizadores de todos os clientes para faturar | Uma tabela | Agregação manual entre N bases de dados |
| Isolamento/"blast radius" | Lógico (tenant_id), já auditado | Físico, total |
| Adequado para | A generalidade dos escritórios | Cliente grande/institucional que exija infraestrutura dedicada (ex.: um cliente ligado ao SIJ que peça isolamento contratual) |

A recomendação é usar o modelo partilhado como caminho principal — é o que torna "agregar escritórios facilmente" verdade — e manter o modelo isolado (que já sabem fazer, com o `docker-compose`/Caddy/CI atual) disponível como tier "enterprise/dedicado" para o cliente raro que o exija ou pague por ele. Não é preciso escolher um e descartar o outro.

### 4.2 Mudanças concretas para ativar o modelo partilhado

1. **Substituir o gate singleton por um provisionamento repetível.** Hoje `SetupService.initializeSystem()` só corre uma vez por instalação. Proposta: manter o wizard `/setup` público para o *primeiro* arranque (bootstrap da própria plataforma), e expor uma operação equivalente — "criar tenant" — apenas para um papel novo de **administrador de plataforma** (ver ponto 3). Tecnicamente é quase o mesmo código de `SetupService`, só deixa de estar amarrado à flag `SystemSetting.initialized`.

2. **Resolver as duas suposições de "tenant única" que o próprio código já assinala.** `TenantRepository.findFirstByOrderByCreatedAtAsc()` e `PublicController.getBranding()` deixam de fazer sentido com >1 tenant. Como não há (ainda) signup público, a opção mais simples é: o site institucional deixa de tentar mostrar branding "da" tenant (não faz sentido com várias) e passa a mostrar sempre a marca ALCv genérica — resolve o problema sem introduzir subdomínios. Branding por escritório (logo/nome) continua a aparecer dentro da aplicação autenticada, que já sabe o `tenantId` do utilizador.

3. **Introduzir um papel de administrador de plataforma**, distinto do `ADMIN` de cada escritório (que continua só a gerir o seu próprio tenant). Pode ser um `Role` novo (`PLATAFORMA_ADMIN`) associado a uma tenant reservada "ALCv" — dá-te (só a ti) um ecrã interno para: listar tenants, criar um novo, ver utilizadores ativos por tenant, ajustar o limite de utilizadores/plano, suspender um tenant que não pague.

4. **Bloquear `PUT /api/v1/admin/rbac` para deixar de ser editável por cada tenant.** É a correção mais importante desta lista — sem ela, dois escritórios no mesmo deployment interferem um com o outro através de um ecrã de configurações aparentemente inofensivo. Para já, a opção de menor risco é tornar a gestão de permissões por papel uma operação de plataforma (fixa para todos os tenants), e retirar essa capacidade ao ADMIN de cada escritório. Se no futuro fizer sentido comercial ter papéis verdadeiramente por-escritório, isso implica dar `tenant_id` a `Role`, o que é uma mudança maior — não a proponho agora porque ninguém pediu essa funcionalidade ainda.

5. **Login e armazenamento não precisam de mudanças estruturais.** O email já é único globalmente (login continua "email + password", sem subdomínio). O `StorageService` já particiona por `tenantId`. O job noturno `AlertasDiariosJob` já itera todas as tenants com isolamento de falhas por tenant — é o padrão certo a repetir para qualquer processamento futuro que atravesse tenants (por exemplo, o relatório de utilização da secção 5).

## 5. Controlo de número de utilizadores e faturação

### 5.1 Modelo de dados

Adicionar dois campos a `Tenant` (migração simples, sem quebrar nada existente):

- `plano` (texto/enum) — ex.: `STARTER`, `STANDARD`, `ENTERPRISE`
- `limite_utilizadores` (inteiro) — quantos utilizadores ativos este tenant pode ter

Não é preciso uma entidade `Subscription` separada para já — só compensa se precisares de histórico de mudanças de plano ao longo do tempo. Começar simples e evoluir se for preciso.

### 5.2 Onde aplicar o limite

O ponto de aplicação natural é `AdminController.createUser` (`POST /api/v1/admin/users`), que hoje cria utilizadores sem qualquer verificação de quantos já existem. Proposta: antes de gravar, contar utilizadores `ativo=true` do tenant e comparar com `limite_utilizadores`; se atingido, devolver 409 com mensagem clara ("Limite de utilizadores atingido para o vosso plano"). Espelhar isto no frontend (desativar o botão "novo utilizador" e mostrar "X/Y utilizadores") — é exatamente o mesmo padrão de duas camadas (backend + frontend) já usado para permissões RBAC, por isso não introduz um conceito novo na equipa.

Contar só `ativo=true` significa que desativar alguém liberta um lugar imediatamente — reaproveita o campo `ativo` que já existe em `User` hoje.

### 5.3 Faturação

Aqui não há nada construído, por isso a proposta é deliberadamente o caminho mais simples que resolve o problema real ("preciso de saber quantos utilizadores cada escritório tem, para faturar"):

- Um relatório/endpoint interno (só para o administrador de plataforma) que mostra, por tenant: nome, plano, limite contratado, utilizadores ativos agora, e opcionalmente o pico de utilizadores ativos em cada mês (útil se decidires faturar pelo pico mensal em vez do valor instantâneo).
- Esse relatório é a base para emitires a fatura manualmente (transferência bancária, ou o que for prática comum em Cabo Verde) — **sem integrar nenhum gateway de pagamento para já**.
- Automatizar cobrança (Stripe ou equivalente) fica como evolução natural mais tarde, se e quando o volume de clientes justificar o esforço de integração — e vale a pena confirmar primeiro se esses gateways cobrem bem Cabo Verde/escudo antes de investir tempo nisso; é bem possível que a cobrança manual continue a ser o caminho mais prático mesmo a prazo, dado o mercado.

Como ponto de partida apenas ilustrativo (ajusta os números à vontade — não é uma recomendação de preço, só de estrutura):

| Plano | Utilizadores incluídos | Indicado para |
|---|---|---|
| Starter | até 5 | escritório pequeno/individual |
| Standard | até 15 | escritório médio |
| Enterprise | por acordo | escritório grande ou infraestrutura dedicada (ver 4.1) |

## 6. Plano faseado

Estruturado para encaixar no vosso workflow de milestones (`.planning/`), do menor para o maior risco:

**Fase A — Limite de utilizadores (entrega valor já, mesmo antes de existir um 2º tenant).** Adicionar `plano`/`limite_utilizadores` a `Tenant`, aplicar o limite em `createUser`, indicador "X/Y" no frontend. Zero risco de isolamento entre tenants porque ainda só existe uma.

**Fase B — Provisionamento multi-tenant.** Papel de administrador de plataforma; ecrã interno para criar/listar tenants e ver utilização; substituir o gate singleton do `SetupService` por uma operação repetível.

**Fase C — Fechar as suposições de tenant única.** Corrigir `PublicController.getBranding`/`TenantRepository.findFirstByOrderByCreatedAtAsc`; bloquear `PUT /admin/rbac` para deixar de ser editável por tenant; correr uma auditoria de isolamento dedicada (no espírito da AUD-01) especificamente sobre estas mudanças, **antes** de deixar entrar um segundo cliente pagante real.

**Fase D — Relatório de utilização/faturação.** Endpoint/relatório de utilizadores ativos por tenant para suportar a fatura manual.

A fase C ter uma auditoria própria não é burocracia — é consistente com o que já aconteceu neste projeto: a retrospetiva da v2.9 regista que "mesmo uma milestone que a investigação classificou corretamente como 'aplicação pura de padrão, zero dependências novas' ainda assim libertou duas fugas de dados entre processos em produção" — e concluiu que o passo de auditoria não é dispensável mesmo quando o risco técnico parece baixo. Multi-tenant real é exatamente o tipo de mudança onde vale a pena repetir esse cuidado.

## 7. Riscos e o que não pode regredir

- A regra existente — `tenant_id` nunca em URLs, contexto sempre injetado via JWT — mantém-se sem exceções também nas novas telas de administrador de plataforma e no relatório de utilização.
- O ponto mais importante que esta proposta traz à superfície é o `PUT /admin/rbac` global (secção 4.2, ponto 4). É o único sítio identificado onde, sem correção, dois tenants partilhados interfeririam um com o outro. Não deixar isto para depois da Fase B.
- Há histórico real de IDOR entre tenants já encontrados e corrigidos neste projeto (módulo Parecer, v2.5/2.6; upload de documentos, Phase 79) — qualquer superfície nova que liste ou agregue dados entre tenants (o ecrã de tenants, o relatório de utilização) merece o mesmo nível de revisão que essas correções tiveram.
- Entidades filhas sem `tenant_id` próprio (que dependem do isolamento transitivo via `Processo`, por terem IDs sequenciais adivinháveis) exigem cuidado redobrado em qualquer query nova que atravesse tenants — é fácil um `JOIN` novo escapar o filtro sem ninguém notar.

## 8. Decisões em aberto — preciso da tua confirmação

1. **Onboarding:** assistido (crias tu o tenant num ecrã interno, como hoje via "pedir demonstração") vs. self-service público (um escritório regista-se sozinho e começa a pagar sem passares por ti). Recomendo assistido para já — é consistente com o que já existe (o site só tem "pedir demonstração"), e para um produto jurídico B2B num mercado pequeno como Cabo Verde, a venda assistida tende a gerar mais confiança do que signup aberto.
2. **Cobrança:** relatório de uso + fatura manual (recomendado para já) vs. gateway de pagamento automático desde já.
3. **Branding:** nome/logo dentro da aplicação (já suportado, sem mudanças) chega para já, ou precisas de subdomínio/domínio próprio por escritório desde o início? (Proponho adiar subdomínio — não é preciso para login nem para isolamento, só teria valor de marketing/vaidade.)
4. Os valores de planos/preços na secção 5.3 são só ilustrativos — os números reais são uma decisão comercial tua.

## 9. Próximo passo sugerido

Confirmar as quatro decisões acima e arrancar a Fase A como próxima milestone (`/gsd:new-milestone`) — é a parte que entrega valor imediato (limite de utilizadores por cliente) independentemente de como as outras decisões corram, e não introduz nenhum risco novo de isolamento entre tenants.
