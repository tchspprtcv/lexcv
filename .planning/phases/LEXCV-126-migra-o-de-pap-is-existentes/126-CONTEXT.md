# Phase 126: Migração de Papéis Existentes - Context

**Gathered:** 2026-09-21
**Status:** Ready for planning
**Mode:** Smart discuss (corrida autónoma). Não é uma fase de infraestrutura apesar de o objectivo dizer "migração" — o levantamento encontrou duas decisões de desenho reais que mudam o âmbito, ambas fixadas abaixo.

<domain>
## Phase Boundary

Todo escritório que já existe é convertido para papéis próprios, e **é nesta fase que a resolução de autoridade passa a ler `t_tenant_role`** — sem que nenhum utilizador ganhe ou perca uma única permissão efectiva.

Dentro do âmbito: a migração de dados, a alteração dos pontos de leitura de papéis, a verificação de deriva zero, e a documentação do script no arranque em duas fases.

Fora do âmbito: o CRUD de papéis pelo escritório e o ecrã editável (Phase 127), e a auditoria (Phase 128).
</domain>

<decisions>
## Implementation Decisions

### Decisão 1 — Esta fase FAZ o cutover de leitura. As anteriores não faziam; esta faz.

As Fases 124 e 125 tinham como invariante explícita "não alterar nenhuma resolução de autoridade", e os papéis instanciados nasceram deliberadamente dormentes. Essa dormência termina aqui, e tem de terminar aqui, por uma razão que o roadmap assume mas não diz: a Phase 127 promete que "alterar as permissões de um papel tem efeito na sessão já aberta dos utilizadores afectados". Isso é impossível se a resolução de autoridade ainda ler papéis globais.

Alternativa ponderada e rejeitada: manter a migração puramente aditiva (preencher `t_tenant_role` e deixar as leituras em papéis globais até à 127). Rejeitada porque empurraria para a 127 — já a maior fase do marco, com 10 requisitos — todo o risco de dados *mais* o CRUD *mais* o ecrã, e deixaria a verificação de deriva zero (MIGR-02), que é a rede de segurança desta conversão, numa fase diferente daquela que faz a conversão.

**Consequência directa:** o critério de sucesso 2 do ROADMAP — a verificação que compara, utilizador a utilizador, as permissões efectivas antes e depois, e falha alto perante qualquer divergência — deixa de ser um extra de diligência e passa a ser a única coisa que separa esta fase de uma alteração silenciosa de quem vê dados de clientes e financeiros. Tratá-la em conformidade.

### Decisão 2 — Os nove pontos de leitura, e os três que comparam nomes

`User.getRoles()` tem nove pontos de chamada em cinco ficheiros, não um:

| Ficheiro | Linhas | O que faz |
|---|---|---|
| `JwtAuthenticationFilter` | 60, 64 | resolução de autoridade por pedido |
| `AuthController` | 126, 189, 258, 259 | login, refresh, `/auth/me` |
| `AdminController` | 78, 79, 222 | listagem e resposta de utilizadores |
| `ParecerController` | 69 | `isAdvogado`, **compara o nome** |
| `ResourceController` | 503, 565 | responsável de processo, **comparam os nomes** |

Os três últimos ramificam lógica de negócio por literais de nome: `"ADVOGADO"`, `"ASSISTENTE"`, `"TECNICO"`. A Phase 127 promete explicitamente que um escritório pode renomear os seus papéis (PAPEL-04). No dia em que alguém renomeia ADVOGADO para "Advogado Sénior", `ResourceController:503` deixa de corresponder e atribuir um responsável a um processo passa a falhar com "Utilizador não tem o papel ADVOGADO" — sem erro, sem aviso, sem nada no log que aponte para a causa.

**Decisão:** estes três sítios passam a resolver por **proveniência**, não por nome. `TenantRole.moldeId` guarda de que molde o papel foi instanciado e sobrevive a qualquer renomeação. A pergunta "este utilizador é advogado?" passa a ser "este utilizador tem um papel cujo `moldeId` é o do molde ADVOGADO?".

Isto preserva exactamente o comportamento de hoje, incluindo o seu limite: um papel criado de raiz por um escritório tem `moldeId` nulo e, tal como hoje, não satisfaz a verificação. **Isso é deliberado nesta fase** — mudar quem pode ser responsável de processo é uma decisão de produto, não um efeito colateral de uma migração.

**A registar como pergunta em aberto para o utilizador, não decidir aqui:** a resposta correcta a prazo é provavelmente verificar *permissões* em vez de proveniência de papel — um escritório que crie "Advogado Júnior" com as permissões certas deveria poder ter responsáveis de processo. Isso muda comportamento observável e merece decisão explícita, não um refactor escondido numa fase de migração.

### Decisão 3 — O utilizador de plataforma não migra

`plataforma@lexcv.cv` tem o papel global `PLATAFORMA_ADMIN` via `t_user_role` (`DatabaseSeeder:552-563`). Esse papel não é instanciável (Phase 125) e o tenant reservado "ALCv" não recebe moldes (Phase 119), logo este utilizador não tem — nem pode ter — papel de escritório.

A resolução de autoridade tem, portanto, de servir **dois caminhos**: papéis de escritório para utilizadores de escritório, papel global para o utilizador de plataforma. Falhar isto tranca o administrador de plataforma para fora da sua própria consola, e fá-lo-ia de forma particularmente cruel — só se descobre depois de a migração ter corrido.

### Decisão 4 — A migração é reversível

Sem migration runner e com sete scripts manuais já pendentes em produção, uma migração irreversível que mexe em autorização é um risco que não se justifica. `t_user_role.role_id` mantém-se preenchido depois da conversão, em vez de ser substituído — a coluna nova coexiste com a antiga. Reverter é voltar a apontar as leituras, não restaurar uma cópia de segurança.

### Claude's Discretion
A forma concreta do esquema (coluna nova em `t_user_role` contra tabela paralela), a estrutura do script de verificação de deriva, e a organização dos testes ficam ao critério do executor, desde que as quatro decisões acima sejam respeitadas e que a verificação de deriva prove o que diz provar.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SetupService.instanciarMoldes` (Phase 125) — o mecanismo de instanciação já provado no caminho de baixo risco; a migração reutiliza-o em vez de reimplementar
- `TenantRoleRepository.findByTenantIdAndNome` — já existe, comentada na Phase 125 como sendo precisamente para esta fase repontar `t_user_role`
- `SetupServiceInstanciacaoMoldesTest.instanciarMoldes_permissoesSaoSnapshot_naoReferenciaViva` — o padrão de prova de snapshot que muta a origem depois da cópia
- `backend/migrations/` mais `README.md` (três tabelas, em ordem numérica ascendente) e a secção de arranque em duas fases do `DEPLOYMENT.md`. Próximo número livre: **127** (`126-add-tenant-role-tables.sql` foi criado na Phase 125)

### Established Patterns
- Testes Mockito; a Phase 119 estabeleceu que um `@PreAuthorize` se prova com um proxy real (`AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory`), nunca por reflexão sobre a anotação
- O filtro faz uma query por PK por pedido autenticado, deliberadamente sem memorização — ver o comentário em `JwtAuthenticationFilter:50-58`, que explica que o requisito é "imediato", não "no próximo login". A resolução nova não pode introduzir memorização que quebre isso
- `ddl-auto: update` em dev; produção pode correr `validate`, logo script manual obrigatório

### Integration Points
- `JwtAuthenticationFilter:60,64` — o ponto crítico
- `AuthController:126,189,258,259` — login, refresh, `/auth/me`
- `AdminController:78,79,222` — listagem de utilizadores
- `ParecerController:69` e `ResourceController:503,565` — os três sítios de comparação por nome
- `UserPrincipal.create` — recebe `roles` e `permissions` já resolvidos; o contrato pode manter-se mesmo mudando quem os calcula
</code_context>

<specifics>
## Specific Ideas

A verificação de deriva zero (MIGR-02) é o artefacto mais importante desta fase. Para ser real tem de comparar o **conjunto de permissões efectivas por utilizador**, que hoje é a união de: permissões dos papéis globais do utilizador, mais as suas permissões directas em `t_user_permission`, mais o bloco que `UserPrincipal.create` acrescenta por código quando o utilizador tem o papel ADMIN. Uma verificação que ignore qualquer uma das três parcelas dá luz verde a uma migração que mudou acessos.

Deve falhar alto e nomear o utilizador e as permissões divergentes — não um booleano.

Nota herdada que continua a valer: a divergência da Phase 124 (`rolePermissions` sem filtro, `systemPermissions` filtrado, resolução JWT sem filtro) permanece inerte porque nenhuma permissão do catálogo está marcada como reservada. Esta fase não deve marcar nenhuma.
</specifics>

<deferred>
## Deferred Ideas

- Verificação por permissão em vez de proveniência de papel nos três sítios de lógica de negócio — muda comportamento observável, precisa de decisão de produto (ver Decisão 2)

- **`ParecerController:411` e `ParecerController:482` — bloqueador de pré-requisito para a Phase 127, não um adiamento livre.** O plan-checker desta fase encontrou dois `principal.getRoles().contains("ADMIN")` que nem a Decisão 2, nem o `126-PATTERNS.md`, nem o levantamento inicial tinham identificado. Gatilham quem pode entregar um parecer e criar uma versão de parecer. São da mesma classe de fragilidade que a Decisão 2 existe para eliminar.

  Não são convertidos nesta fase por uma razão técnica, não de conveniência: `principal.getRoles()` devolve um `Set<String>` de nomes, não entidades, pelo que resolver por proveniência exigiria que o `UserPrincipal` passasse a transportar `moldeId` — alteração ao contrato do principal que extravasa o âmbito de uma fase de migração.

  Imediatamente depois da migração continuam correctos, porque `TenantRole.nome` é cópia literal do nome do molde. **Passam a estar errados no instante em que a Phase 127 entregar PAPEL-04 (renomear papel). A Phase 127 tem de os fechar antes de enviar essa capacidade** — caso contrário, um escritório que renomeie o seu papel de administrador perde silenciosamente a capacidade de entregar pareceres.
- Remover `t_user_role.role_id` depois de a conversão estabilizar — fase própria, depois de o marco fechar
- Corrigir a corrida de check-then-act no arranque do seeder — risco residual aceite e documentado na Phase 124 (WR-02)
- Tecto de permissões por plano (`TenantPlano`) — requisitos futuros TECT-01/TECT-02
</deferred>
