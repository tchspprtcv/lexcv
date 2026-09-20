# Requirements: ALCv — Marco v2.17 RBAC por Escritório

**Defined:** 2026-09-20
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v2.17 Requirements

### Papéis do Escritório

- [ ] **PAPEL-01**: Administrador de escritório vê a lista dos papéis do seu escritório, e apenas desses
- [ ] **PAPEL-02**: Administrador de escritório cria um papel novo, escolhendo nome e conjunto de permissões
- [ ] **PAPEL-03**: Administrador de escritório altera as permissões de um papel do seu escritório e a mudança tem efeito na sessão dos utilizadores afetados
- [ ] **PAPEL-04**: Administrador de escritório renomeia um papel do seu escritório
- [ ] **PAPEL-05**: Administrador de escritório apaga um papel que não esteja atribuído a nenhum utilizador, e é impedido de apagar um que esteja
- [ ] **PAPEL-06**: Administrador de escritório atribui um ou mais papéis do escritório a um utilizador, ao criar e ao editar
- [ ] **PAPEL-07**: Nenhuma alteração feita por um escritório altera os papéis, permissões ou acessos de qualquer outro escritório
- [ ] **PAPEL-08**: O papel de administrador do escritório não pode ser apagado nem despojado das permissões que o tornam administrador
- [ ] **PAPEL-09**: O papel `PLATAFORMA_ADMIN` não é listado, atribuível nem alcançável a partir de qualquer ecrã ou endpoint de escritório

### Moldes e Provisionamento

- [ ] **MOLD-01**: Um escritório provisionado de novo nasce com os papéis-molde já instanciados como cópia própria, prontos a atribuir
- [ ] **MOLD-02**: `PLATAFORMA_ADMIN` consulta e edita os moldes na consola `/plataforma`
- [ ] **MOLD-03**: Editar um molde não altera nenhum papel já instanciado em nenhum escritório
- [ ] **MOLD-04**: `PLATAFORMA_ADMIN` cria um molde novo, que passa a ser instanciado nos escritórios provisionados a partir daí

### Catálogo de Permissões

- [ ] **CATL-01**: O catálogo de permissões (nome técnico, rótulo, descrição, categoria) é servido a partir da base de dados, não de uma lista embutida no código do controller
- [ ] **CATL-02**: O arranque semeia e actualiza o catálogo sem apagar nenhuma atribuição já existente
- [ ] **CATL-03**: As permissões reservadas à plataforma não são oferecidas ao escritório em nenhuma superfície
- [ ] **CATL-04**: `rbac:manage` passa a ser a autoridade que de facto governa quem edita permissões dentro de um escritório

### Migração

- [ ] **MIGR-01**: A migração instancia os moldes em cada tenant existente e repõe as atribuições actuais sem que nenhum utilizador ganhe ou perca acesso
- [ ] **MIGR-02**: Existe uma verificação pós-migração que compara, por utilizador, o conjunto de permissões efectivas antes e depois, e falha se divergirem
- [ ] **MIGR-03**: A migração está documentada em `backend/migrations/README.md`, com o seu lugar no arranque em duas fases do `DEPLOYMENT.md`

### Auditoria de Atribuições

- [ ] **AUDT-01**: Cada criação, alteração e remoção de papel fica registada com autor, momento e o que mudou
- [ ] **AUDT-02**: Cada atribuição e remoção de papel a um utilizador fica registada com autor, momento e utilizador alvo
- [ ] **AUDT-03**: Administrador de escritório consulta o registo de auditoria do seu escritório, e apenas desse
- [ ] **AUDT-04**: O registo de auditoria não é editável nem apagável a partir de nenhuma superfície da aplicação

## Future Requirements

Reconhecidos, fora deste marco.

### Tecto por Plano

- **TECT-01**: Cada `TenantPlano` define o subconjunto de permissões que o escritório pode usar ao compor papéis
- **TECT-02**: A descida de plano degrada os papéis existentes de forma previsível e comunicada

### Convergência de Overrides

- **OVER-01**: Os overrides por utilizador (`t_user_permission`) são convertidos em papéis do escritório e a tabela é retirada

## Out of Scope

| Feature | Reason |
|---------|--------|
| Tecto de permissões por plano de subscrição | O escritório compõe com o catálogo todo menos as reservadas; ligar RBAC a `TenantPlano` é alavanca comercial que se acrescenta depois sem quebrar o modelo — decisão explícita na abertura do marco |
| Abolir `t_user_permission` | Os overrides aditivos por utilizador mantêm-se exactamente como estão; migrá-los agora acrescenta risco de migração ao marco que já traz a migração de `t_user_role` |
| Sincronização molde → papel instanciado | Snapshot na instanciação foi a decisão tomada: o escritório é dono do seu papel. A sincronização com marcação de desvio foi ponderada e rejeitada por complexidade |
| Hierarquia ou herança entre papéis | Nenhum caso de uso real hoje; a cadeia de fallback `manage → edit → create` em `permissions.ts` já cobre o que existe |
| Permissões ao nível do registo (por processo ou cliente) | Salto de modelo muito maior do que papéis por escritório; nada no produto hoje o pede |
| Escritório criar permissões novas | O catálogo é da plataforma. Um escritório compõe papéis com as permissões que existem, não inventa autoridades que o backend não conhece |
| Exportação do registo de auditoria | Consulta no ecrã chega para a exigência actual; exportar levanta questões de dados pessoais que merecem tratamento próprio |

## Traceability

Preenchido durante a criação do roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PAPEL-01 | — | Pending |
| PAPEL-02 | — | Pending |
| PAPEL-03 | — | Pending |
| PAPEL-04 | — | Pending |
| PAPEL-05 | — | Pending |
| PAPEL-06 | — | Pending |
| PAPEL-07 | — | Pending |
| PAPEL-08 | — | Pending |
| PAPEL-09 | — | Pending |
| MOLD-01 | — | Pending |
| MOLD-02 | — | Pending |
| MOLD-03 | — | Pending |
| MOLD-04 | — | Pending |
| CATL-01 | — | Pending |
| CATL-02 | — | Pending |
| CATL-03 | — | Pending |
| CATL-04 | — | Pending |
| MIGR-01 | — | Pending |
| MIGR-02 | — | Pending |
| MIGR-03 | — | Pending |
| AUDT-01 | — | Pending |
| AUDT-02 | — | Pending |
| AUDT-03 | — | Pending |
| AUDT-04 | — | Pending |

**Coverage:**
- v2.17 requirements: 24 total
- Mapped to phases: 0
- Unmapped: 24 ⚠️

---
*Requirements defined: 2026-09-20*
*Last updated: 2026-09-20 after milestone v2.17 kickoff*
