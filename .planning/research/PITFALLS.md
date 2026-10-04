# Pitfalls Research

**Domain:** Faturação eletrónica conforme o eFatura de Cabo Verde (DNRE) acrescentada a uma plataforma multi-tenant de gestão jurídica já em produção (LexCV, marco v3.0)
**Researched:** 2026-10-04
**Confidence:** MEDIUM global.
- Factos do código: HIGH (lidos directamente, com ficheiro:linha).
- Factos regulatórios de Cabo Verde: MEDIUM a LOW. Nesta sessão os sites oficiais (`efatura.cv`, `mf.gov.cv`, `governacaodigital.gov.cv`) e vários terciários (WISEDAT, Edicom) devolveram `EGRESS_BLOCKED` ao fetch, e o Context7 devolveu 403. Os factos regulatórios abaixo vêm de **resumos de resultados de pesquisa** que citam o Manual Técnico/documentação oficial, não da leitura directa do XSD nem do texto do Decreto-Lei. Cada um está marcado; nenhum deve ser tratado como contrato antes da verificação listada em "Lacunas a verificar contra fonte primária".
- Analogias com Portugal (e-fatura, SAF-T, ATCUD): MEDIUM como padrão de engenharia, NÃO verificadas como regra de Cabo Verde. Estão sempre marcadas `[Analogia PT]`.

## Legenda dos marcadores

| Marcador | Significado |
|----------|-------------|
| `[Código]` | Verificado por leitura directa do repositório (ficheiro:linha) |
| `[Fonte]` | Afirmação regulatória apoiada em fonte citada (ver Sources), MEDIUM |
| `[Analogia PT]` | Raciocínio por analogia com Portugal; não confirmado para CV |
| `[Inferência]` | Conclusão minha a partir de factos acima; não é regra externa |
| `[Não verificado]` | Precisa de confirmação (contabilista certificado, DNRE, XSD) antes de implementar |

## Vocabulário de fases usado neste documento

O roadmapper mapeia estes rótulos para as fases reais (a numeração do projecto continua em 133 e nunca reinicia).

| Rótulo | Conteúdo |
|--------|----------|
| **F-A Fundação fiscal** | Dados fiscais do emitente (escritório e plataforma), modelo de snapshot, tabelas novas, migração manual + README, scopes RBAC, repositórios com tenant embutido, flag de activação por tenant |
| **F-B Núcleo de emissão** | Séries/numeração, Fatura-Recibo emitida atomicamente com o pagamento, cálculo de IVA/arredondamento, data/fuso, idempotência, imutabilidade, bloqueio de apagar pagamentos faturados |
| **F-C Nota de Crédito** | NC total/parcial, reversão de saldo e coerência das leituras de "pago" |
| **F-D Adaptador eFatura** | Formato XML/IUD, interface, simulador, estado de comunicação, modo SIMULADO/REAL, segredos, outbox de comunicação |
| **F-E PDF + Email** | PDF a partir do snapshot, primeiro canal SMTP, outbox de email |
| **F-F Plataforma como emitente** | Registo manual de subscrição por `PLATAFORMA_ADMIN`, FR da LexCV para o escritório |
| **F-G Verificação final** | Auditoria de isolamento com 2 tenants + tenant de plataforma, gate ASVS L1, UAT, inventário de migrações |

Ordem recomendada: F-A → F-B → F-C → F-D → F-E → F-F → F-G. F-F pode correr em paralelo a F-D/F-E porque reutiliza o motor de F-B, mas depende dos dados fiscais da plataforma de F-A.

## Baseline verificado no código (muda o perfil de risco face a conselhos genéricos)

1. `[Código]` **`Honorario` e `Pagamento` não têm `tenant_id` próprio nem id não adivinhável.** Ambos têm `Integer` com `IDENTITY` (`Honorario.java:19`, `Pagamento.java:16`); `Honorario.processo_id` é um UUID simples (`Honorario.java:23`) com unicidade 1:1 (`:12`) e `Pagamento.honorario_id` um inteiro simples (`Pagamento.java:20`). O tenant só se deriva por `pagamento → honorário → processo → tenant`, re-fetch a re-fetch, em cada call site (`ResourceController.java:3045-3052`, `:3163-3170`). É a "Pitfall 1" arquivada em `.planning/milestones/v2.14-research/PITFALLS.md`, aceite como risco residual em PROJECT.md (decisão v2.16, Phase 123).
2. `[Código]` **`createPagamento` não é transaccional e o saldo da conta corrente é "best effort".** `ResourceController.java:3039-3081`: sem `@Transactional`; `pagamentoRepository.save` faz commit sozinho (`:3058`); o saldo é lido-modificado-gravado (`:3063-3070`) dentro de um `try` cujo `catch (DataAccessException)` só faz `log.warn` (`:3071-3078`) — o pagamento fica gravado mesmo que o saldo falhe. `deletePagamento` (`:3156-3186`) tem o mesmo desenho invertido: subtrai o saldo (`:3171-3183`) e só depois faz `deleteById` (`:3184`), tudo fora de transacção. Não há `@Version` nem lock em `ContaCorrente` (`ContaCorrente.java`), logo dois pagamentos concorrentes do mesmo cliente perdem uma actualização.
3. `[Código]` **Numeração actual = `MAX+1` dentro de `synchronized` de JVM.** `ResourceController.java:278-286` (cliente) e `:2233` (facto), com `findMaxNumeroSequencialByTenantId` (`ClienteRepository.java:18-19`). A decisão está registada em PROJECT.md como "sem sequence dedicada"; a constraint `(tenant_id, numero_sequencial)` só transforma a corrida em `409 tente novamente`. Aceitável para `CLI-0001`; inaceitável para números fiscais.
4. `[Código]` **Há precedente correcto de lock+unique no repositório:** `ParecerSolicitacaoRepository.java:27-29` (`@Lock(PESSIMISTIC_WRITE) findByIdForUpdate`) provado por `ParecerVersaoConcorrenciaIT` (Testcontainers, `@DataJpaTest`). **E precedente de imutabilidade estrutural:** `AuditLog` é `@Immutable` e `AuditLogRepository` estende `Repository<AuditLog, Long>` (sem `delete*`/`saveAll`), com teste que fixa o conjunto de métodos (`AuditLogImutabilidadeTest`).
5. `[Código]` **O fuso do container é UTC e o código sabe disso em dois sítios mas ignora-o noutros.** `AlertasDiariosJob.java:65-66,79-81` declara `ZoneId.of("Atlantic/Cape_Verde")` e documenta "o container corre em UTC, Cabo Verde é UTC-1". Em contrapartida `ResourceController.java:3257` usa `LocalDate.now().getMonthValue()` (fuso da JVM, e só compara o **mês**, sem o ano), `:1508` grava `dataAcordo(LocalDate.now())`, `ContaCorrente.onSave` usa `LocalDateTime.now()`. `backend/Dockerfile` usa `eclipse-temurin:23-jre-alpine`, sem `TZ`, sem fontes.
6. `[Código]` **Não existe NIF de emitente.** `Tenant.nif` é `String` livre sem validação (`Tenant.java:23`), não existe `morada` nem regime de IVA. `SetupInitializeRequest` só tem `clientName`, `logo`, `adminEmail`, `adminPassword`; `SetupService.provisionTenant` (`SetupService.java:164`) cria o `Tenant` sem NIF; `TenantUpdateRequest` só altera `plano`/`limiteUtilizadores`; `TenantAdminSummaryResponse.java:21` omite NIF de propósito. O tenant de plataforma é `Tenant.builder().nome("LexCV").build()` (`DatabaseSeeder.java:532-535`), logo **NIF nulo**. O NIF do tenant demo seedado é `000000000` (`DatabaseSeeder.java:88`).
7. `[Código]` **O tenant de plataforma é identificado pelo nome literal `"LexCV"`**, sem constraint `unique` em `t_tenant.nome` (`TenantRepository.java`, comentário) e sem recusa do nome reservado no provisionamento (`SetupService.validateRequest`, `:302-322`). `PlatformAdminController` "nunca lê o contexto de segurança" por desenho (`PlatformAdminController.java`, javadoc de classe) e `PLATAFORMA_ADMIN` tem zero permissões `scope:action`.
8. `[Código]` **O perfil `prod` é configuração morta** (CLAUDE.md; `application-prod.yml`). Guardas do tipo `@Profile("!prod")` não protegem nada hoje. A única alavanca real é uma variável de ambiente.
9. `[Código]` **O handler global devolve `ex.getMessage()` ao cliente** em qualquer excepção não tratada (`GlobalExceptionHandler.java:99-105`) e `application.yml:4` tem `server.error.include-message: always` no perfil que realmente corre. Mensagens de SMTP/HTTP/XML do adaptador chegariam ao browser.
10. `[Código]` **Entrada financeira do frontend:** `metodo` é texto livre (`financeiro/[id]/page.tsx:493-494`), `dataPagamento` é opcional (`schemas/financeiro.ts`, `pagamentoFormSchema`), `moneyString` aceita `Number("1e3")` (mesmo ficheiro), o botão só fica `disabled` enquanto `isSubmitting`/`isPending` (`page.tsx:505`, e usa `permissions.isLoading`, o idioma já corrigido noutros ecrãs pela Phase 103/105). Sem chave de idempotência.
11. `[Código]` **Cliente:** `nif` com `@Pattern(\d{9})` (`Cliente.java:38-41`) mas `jakarta.persistence.validation.mode: none` (`application.yml:26-27`) e valores legados tolerados por decisão (PROJECT.md v2.8); `email` sem qualquer validação de formato (`Cliente.java:42`); sem campo de país/nacionalidade; sem figura de "consumidor final".
12. `[Código]` **Quem apaga o quê:** `deleteProcesso` apaga sem olhar a honorários/pagamentos (`ResourceController.java:1246-1255`) e deixa-os órfãos; `deleteCliente` apaga cliente e conta corrente (`:617-630`); `mergeClientes` apaga o secundário e re-aponta processos/documentos/pareceres/contactos (`:834-956`) — o histórico (comentários CR-01/WR-02) mostra que cada entidade nova com `clienteId` já foi esquecida aqui uma vez.
13. `[Código]` **Quatro leituras independentes de "quanto foi pago":** `@Formula totalPago` (`Honorario.java:34`), saldo de `ContaCorrente`, `calculateMensalReceived` (`ResourceController.java:3247-3263`, que ainda faz `pag.getValorPago()` sem null-check) e o alerta `HONORARIO_ATRASADO` (`AlertasDiariosJob.java:276-277`).
14. `[Código]` **Plumbing de configuração:** cada variável do backend tem de ser listada à mão em `environment:` de `docker-compose.yml:52-75` (e no `hostinger`), mais `deploy.yml` e `.env.example`; `application.yml` não tem defaults (convenção). Não existe nada de SMTP em lado nenhum.
15. `[Código]` **CI:** `deploy.yml` corre `mvn -B verify` (inclui `*IT` Testcontainers) e `mvn -B spotbugs:check`, e este bloqueia o `build-and-push`; OWASP dependency-check está explicitamente adiado. `spotbugs-exclude.xml` já suprime `ENTITY_MASS_ASSIGNMENT` para `createPagamento`/`createHonorario` por nome de método e avisa "não acrescentar sem a mesma revisão".
16. `[Código]` **RBAC em 4 sítios:** `DatabaseSeeder.CATALOGO_PERMISSOES`, lista hardcoded de ADMIN em `UserPrincipal.create` (`UserPrincipal.java:41-56`, "keep in sync"), `KNOWN_SCOPES` em `web/src/lib/permissions.ts:6-14`, e `@PreAuthorize`. Desde a v2.17 os papéis de escritório são snapshots: uma permissão nova não chega a papéis já instanciados.
17. `[Código]` `SecurityConfig.java:43` desactiva CSRF; o cookie é `secure(false)` (`AuthController.java:44-52`) sem `SameSite` explícito. `.planning/config.json`: `security_enforcement: true`, ASVS L1, `block_on: high`, `safety.always_confirm_external_services: true` (ligar SMTP real exige confirmação humana).

---

## Critical Pitfalls

### P-01: Numeração fiscal com lacunas ou duplicados (copiar o padrão `MAX+1`/`synchronized` ou usar uma SEQUENCE)

**What goes wrong:**
Dois escritórios (ou duas instâncias do backend, ou duas threads) emitem em simultâneo e obtêm o mesmo número; ou um rollback consome o número e deixa um buraco na série. Em modo real, a DNRE controla a sequencialidade por LED, por isso um buraco local passa a ser rejeição de comunicação `[Fonte]` (ver P-09).

**Why it happens:**
O padrão de numeração que já existe e "funciona" é `MAX+1` em `synchronized (ClienteRepository.class)` (`ResourceController.java:278-286`, `:2233`) — protege uma só JVM, e a constraint única só converte a corrida em erro. A alternativa tentadora, uma SEQUENCE/IDENTITY do Postgres, **não faz rollback**: qualquer transacção falhada deixa um buraco. Terceira armadilha: reservar o número num passo prévio ("pré-alocar e depois preencher"), que também deixa buracos.

**How to avoid:**
- Tabela de séries `t_fatura_serie` (`id` UUID, `tenant_id`, `tipo_documento`, `codigo`, `proximo_numero`, `led_codigo` anulável, `activa`) com `UNIQUE (tenant_id, tipo_documento, codigo)`.
- Atribuir o número **dentro da mesma transacção que insere o documento**: `findByIdForUpdate` com `PESSIMISTIC_WRITE` (idioma já provado em `ParecerSolicitacaoRepository.java:27-29`), ler `proximo_numero`, incrementar, inserir o documento. Rollback devolve o número → série sem buracos.
- Barreira final na BD: `UNIQUE (tenant_id, serie_id, numero)` no documento fiscal.
- Série criada preguiçosamente por `INSERT ... ON CONFLICT DO NOTHING` (nunca por `MAX` da aplicação), porque os tenants existentes não têm séries e não há backfill possível nem desejável.
- Ordem de locks fixa (série primeiro, depois conta corrente) para evitar deadlock entre dois pagamentos; nada de PDF/SMTP/adaptador dentro desta transacção (P-07).
- Política de reinício anual e formato do número: decisão em aberto, ver "Questões para a fase de discussão".
- Teste: IT Testcontainers no estilo de `ParecerVersaoConcorrenciaIT` — N threads obtêm 1..N contíguos; um rollback forçado liberta o número; dois tenants começam ambos em 1.

**Warning signs:**
`MAX(`, `synchronized`, `@GeneratedValue` ou `SEQUENCE` a produzir o campo `numero`; revisão de código que veja o número calculado fora do método transaccional que insere o documento; `SELECT numero ... ORDER BY numero` com saltos.

**Phase to address:** F-B (modelo de série em F-A).

---

### P-02: Emissão não atómica com o pagamento e com o saldo (a Fatura-Recibo "na mesma operação" exige uma só transacção)

**What goes wrong:**
Fica um pagamento sem Fatura-Recibo (viola "exactamente uma por pagamento"), ou uma fatura sem pagamento, ou saldo da conta corrente dessincronizado.

**Why it happens:**
O ponto de partida não é atómico (baseline 2). O instinto é "pôr `@Transactional` no controller e acrescentar a emissão": isso muda silenciosamente o comportamento do `catch (DataAccessException)` — uma excepção que atravessa o proxy de um repositório Spring Data marca a transacção externa como *rollback-only*, o `catch` engole-a, e o commit falha com `UnexpectedRollbackException` (500) em vez do "log e segue" actual. Quem não percebe isto mantém o `catch` e obtém um comportamento pior ou, pior ainda, remove o `@Transactional` para "o 500 desaparecer".

**How to avoid:**
- Um único método de serviço `@Transactional` (p.ex. `FaturacaoService.registarPagamentoEFaturar`) que faz, por esta ordem: validar tenant e dados fiscais (falhar antes de consumir número) → gravar `Pagamento` → obter número da série (lock) → gravar `Fatura` + linhas + snapshot → actualizar saldo → gravar linha de comunicação/outbox. Qualquer falha reverte tudo.
- **Sem o `catch` que só regista** neste caminho: o saldo faz parte da unidade de trabalho.
- Saldo por `UPDATE t_conta_corrente SET saldo = saldo + :v WHERE cliente_id = :id` (atómico) ou lock da linha — não leitura-modificação-gravação (`ResourceController.java:3063-3070`). Criar a conta corrente em falta com `INSERT ... ON CONFLICT DO NOTHING` (hoje `orElseGet(save)` corre contra `cliente_id UNIQUE`).
- `UNIQUE (pagamento_id)` na Fatura-Recibo é a garantia na BD de "exactamente uma por pagamento".
- Pagamentos antigos (sem Fatura-Recibo) continuam a ser apagáveis; o bloqueio é por *existência* de fatura para aquele `pagamento_id`, verificado também no `deletePagamento` (`:3156-3186`), que tem de passar a ser transaccional e a falhar com 409.

**Warning signs:**
`@Transactional` adicionado ao controller sem remover o `catch`; testes que só provam o caminho feliz; `Pagamento` com `id` mas sem linha em `t_fatura`; saldo ≠ soma dos pagamentos menos NCs.

**Phase to address:** F-B.

---

### P-03: Dupla emissão por duplo clique, dois separadores ou retry de rede

**What goes wrong:**
O utilizador clica duas vezes, ou o pedido demora e ele repete: dois pagamentos e duas Fatura-Recibo para o mesmo recebimento (dois números fiscais, ambos imutáveis, só corrigíveis com NC).

**Why it happens:**
A única defesa actual é o botão desactivado enquanto a mutação está pendente (`page.tsx:505`). Não cobre dois separadores, timeout + clique novo, nem um retry de infraestrutura. O backend não tem noção de idempotência, e `UNIQUE (pagamento_id)` só impede segunda fatura para o *mesmo* pagamento, não um segundo pagamento.

**How to avoid:**
- Chave de idempotência gerada no cliente (`crypto.randomUUID()` ao abrir o diálogo, renovada só após sucesso/fecho) enviada em cabeçalho `Idempotency-Key`.
- Persistir a chave **na mesma transacção** em `t_fatura` com `UNIQUE (tenant_id, chave_idempotencia)`. Em colisão, devolver o resultado original (pagamento + fatura) com o mesmo código HTTP; chave igual com payload diferente → 422.
- O cabeçalho personalizado tem um efeito lateral útil: pedidos cross-site simples não o conseguem enviar sem preflight CORS — defesa em profundidade num sistema com CSRF desligado e cookie sem `SameSite` explícito (baseline 17).
- A comunicação ao adaptador tem a sua própria idempotência (o IUD é a chave natural, P-07/P-09): reenviar o mesmo DFE nunca pode criar um segundo documento na DNRE.
- Confirmar que o Caddy não tem retries configurados para POST.

**Warning signs:**
Dois pagamentos idênticos com poucos segundos de diferença; ausência de cabeçalho/coluna de idempotência no desenho; teste que não simula dois pedidos concorrentes com a mesma chave.

**Phase to address:** F-B (frontend em F-G/UX).

---

### P-04: Fuga de isolamento entre tenants por herança do modelo transitivo (Honorario/Pagamento) e por ids inteiros

**What goes wrong:**
Uma fatura, um PDF ou um XML de um escritório é lido (ou emitido!) por outro; ou um documento fiscal perde a sua ligação ao tenant quando o `Processo` é apagado.

**Why it happens:**
- O tenant de um pagamento só se deriva via processo (baseline 1), e o `deleteProcesso` deixa honorários e pagamentos órfãos (`:1246-1255`): depois disso a derivação dá `processo == null` e o documento fiscal que dependa dela deixa de ter dono.
- IDs inteiros sequenciais são adivinháveis (o próprio PROJECT.md já o registou para `Decisao/Facto/Testemunha`).
- O padrão `findById` + comparação de `tenantId` no fim é correcto mas depende da disciplina em cada call site (~11 métodos de repositório sem `tenantId` na query, risco residual aceite na v2.16).
- O bug v2.9 (`?processo_id=` aceite e ignorado) é a forma típica de falhar: filtro definido, não aplicado.

**How to avoid:**
- Cada documento fiscal tem `emitente_tenant_id NOT NULL` próprio, preenchido pelo servidor a partir do principal — **nunca** do corpo do pedido, nem derivado do processo. Para documentos da plataforma, `adquirente_tenant_id` (ver P-12).
- PK `UUID` (como `Cliente`), mas **sem confiar nisso**: repositórios fiscais estreitos, `extends Repository<Fatura, UUID>`, só com métodos `findByIdAndEmitenteTenantId` e listagens com tenant (precedente `AuditLogRepository`, com teste a fixar o conjunto de métodos). Um `findById` sem tenant simplesmente não compila.
- Na emissão, validar a cadeia `pagamento → honorário → processo` **uma vez**, dentro do serviço, e comparar com o tenant do principal antes de consumir número.
- Resposta 404 (não 403) para documento de outro tenant.
- Download de PDF/XML servido pela API com verificação de tenant + scope a cada pedido e evento em `AuditLog` (precedente `documento_download`, `ResourceController.java:2917-2943`). Se usar URL pré-assinada, TTL curto (≤60 s): o URL é um bearer token que sobrevive ao logout (o actual dura 3600 s).
- Filtros do endpoint de listagem (`honorario_id`, `cliente_id`, datas) com teste que prova que são aplicados.
- IT com **dois tenants + o tenant de plataforma**, mesmo token de pesquisa/NIF em todos, a afirmar zero linhas cruzadas por endpoint (precedentes `DocumentoRepositoryIT`, `PesquisaRepositoryIT`).

**Warning signs:**
Repositório fiscal que estende `JpaRepository`; `findById` sem tenant; `tenant_id` nulo ou derivado de `processo`; endpoint de listagem cujo parâmetro de filtro não aparece na query; testes só como ADMIN de um tenant.

**Phase to address:** F-A (repositórios e esquema), F-G (auditoria com 2 tenants). Severidade ASVS L1: **alta, bloqueante**.

---

### P-05: Documento fiscal dependente de linhas que o sistema deixa apagar ou fundir (snapshot e conservação)

**What goes wrong:**
Um `DELETE /clientes/{id}`, `DELETE /processos/{id}`, uma fusão de clientes ou um `DELETE /pagamentos/{id}` deixam faturas a apontar para nada, ou mostram dados do cliente já alterados. Em CV os documentos fiscais têm de ser conservados e arquivados `[Fonte: DL 79/2020 regula "emissão, conservação e arquivo"]`; o prazo (em Portugal, 10 anos) é `[Não verificado]` para CV.

**Why it happens:**
`deleteCliente` (`:617-630`), `deleteProcesso` (`:1246-1255`), `mergeClientes` (`:834-956`) e `deletePagamento` (`:3156-3186`) não sabem que existe uma fatura. Se a fatura lê nome/NIF/morada do `Cliente` vivo (para PDF, XML, email), o PDF de hoje não é o de ontem. A fusão já esqueceu entidades novas duas vezes (CR-01/WR-02).

**How to avoid:**
- **Snapshot imutável no documento:** emitente (nome, NIF, morada, regime), adquirente (nome, NIF, morada, email, país), linhas (descrição, base, taxa, IVA, total), moeda, totais, `emitido_por_user_id` **e** `emitido_por_nome` (um `User` pode ser apagado — mesmo raciocínio do `AuditLog.detalhe`).
- `cliente_id`, `processo_id`, `pagamento_id` como colunas de referência simples, sem `ON DELETE CASCADE` e sem `orphanRemoval`; o modelo inteiro não usa `@ManyToOne`.
- Pré-verificação 409 em `deleteCliente`, `deleteProcesso`, `deletePagamento` quando existam documentos fiscais.
- No `mergeClientes`, re-apontar `cliente_id` das faturas do secundário para o principal (só a referência, nunca o snapshot) na mesma transacção; acrescentar ao teste de fusão.
- Não existe endpoint de apagar tenant (só suspensão): manter assim.

**Warning signs:**
Fatura cuja `cliente_id` não resolve; PDF gerado depois de o cliente editar o NIF que mostra o NIF novo; `deleteProcesso` a devolver 200 com faturas associadas.

**Phase to address:** F-B (snapshot e guardas de delete), F-G (teste de fusão/apagar).

---

### P-06: Imutabilidade "por convenção" — e o estado de comunicação que tem de mudar

**What goes wrong:**
Alguém acrescenta um `PUT /faturas/{id}`, um setter, um `save()` sobre uma entidade carregada, ou guarda o PDF como `Documento` genérico e este é apagável por `DELETE /documentos/{id}` (`ResourceController.java:2946-2981`, exige só `documentos:edit`). A listagem `GET /documentos` mostraria também o PDF a um `ASSISTENTE` sem `financeiro:view`.

**Why it happens:**
A imutabilidade "total" colide com o estado de comunicação, que muda (`PENDENTE → ENVIADO → ACEITE/REJEITADO`). Quem põe `estado` na entidade do documento tem de abandonar `@Immutable`. O reflexo de reutilizar `Documento`/MinIO já pronto (`StorageService`) é forte.

**How to avoid:**
- Dividir: `Fatura` (+ linhas) **imutável e sem setters** (`@Immutable`, `@Getter` + builder, construtor protegido), repositório estreito; `FaturaComunicacao` (tentativas append-only e estado actual) e `FaturaEnvioEmail` (outbox) mutáveis, ligadas por `fatura_id`.
- Não usar a entidade `Documento` para PDFs fiscais; chave MinIO própria (`<tenantId>/faturas/<faturaId>.pdf`) com SHA-256 guardado na `Fatura`.
- Um `DELETE`/`PUT` sobre faturas simplesmente não existe como rota. Anulação = NC (F-C).
- Gatilhos de BD ou `CHECK` parciais **não** são fiáveis aqui: o `ddl-auto: update` não os cria e o "Path A — fresh install" do README salta todos os scripts menos o `111` (P-15). Se existirem, a aplicação tem de os verificar no arranque.

**Warning signs:**
`@Data`/`@Setter` em entidades fiscais; `JpaRepository` nos repositórios fiscais; coluna `estado` na tabela do documento; PDF aparece na lista de Documentos.

**Phase to address:** F-A (modelo), F-B/F-D (partição documento/comunicação).

---

### P-07: Chamar o adaptador eFatura ou enviar email dentro da transacção de base de dados

**What goes wrong:**
A chamada de rede segura a ligação da BD e o lock da linha de série durante segundos; com o pool por defeito (Hikari, 10 ligações) alguns pedidos lentos esgotam o pool. Pior: o adaptador/SMTP tem sucesso e a transacção faz rollback — a DNRE (ou o cliente) fica com um documento que a BD não tem, e o número local pode ser reutilizado. Em sentido inverso, um erro de SMTP reverte uma fatura legítima.

**Why it happens:**
É a forma natural de escrever "emitir e comunicar". E o **simulador esconde o problema**: é instantâneo e nunca falha, por isso o desenho que o usa dentro da transacção parece funcionar até o adaptador real chegar.

**How to avoid:**
- Transacção 1 (curta): grava `Fatura` + `FaturaComunicacao(estado=PENDENTE)` + `FaturaEnvioEmail(PENDENTE)` e faz commit. Só depois, fora de qualquer transacção, um worker comunica/envia e regista cada tentativa numa transacção própria (outbox).
- Worker `@Scheduled` com `SELECT ... FOR UPDATE SKIP LOCKED LIMIT n` (seguro com >1 instância; `@Scheduled` corre em todas) e isolamento de falha por linha, copiando a disciplina em 4 camadas de `AlertasDiariosJob` (capturar `Throwable`, uma linha falhada não pára as outras). Backoff e máximo de tentativas; botão "reenviar" manual.
- `@TransactionalEventListener(AFTER_COMMIT)` só como acelerador, nunca como único mecanismo (perde-se num crash).
- Timeouts explícitos: Jakarta Mail tem `mail.smtp.connectiontimeout`/`timeout`/`writetimeout` **infinitos por defeito** (`[Não verificado]` nesta sessão, conhecimento do componente) — definir os três; idem para o cliente HTTP da DNRE.
- **O simulador obedece ao mesmo contrato assíncrono** e, em testes, injecta latência e falhas aleatórias; um teste prova que o rollback não deixa comunicação pendente.

**Warning signs:**
`@Transactional` a envolver `adaptador.comunicar(...)` ou `mailSender.send(...)`; ausência de tabela de outbox/estado por tentativa; `spring.mail.*` sem timeouts.

**Phase to address:** F-D (adaptador/outbox), F-E (email).

---

### P-08: Simulador a vazar para produção — documentos que "parecem comunicados"

**What goes wrong:**
Em produção, faturas ficam com estado "Comunicada", um IUD plausível e um QR code que parece verdadeiro, sem nunca terem passado pela DNRE. O escritório acredita-se conforme.

**Why it happens:**
- `@Profile("!prod")` não funciona: o perfil `prod` não é activado em lado nenhum (baseline 8).
- O IUD real é determinístico (45 caracteres com dígito de controlo Luhn `[Fonte]`): um simulador que o gere "bem" é indistinguível do real.
- Um único `enum` de estado onde `ACEITE` serve para ambos.

**How to avoid:**
- Variável de ambiente obrigatória **sem default** (convenção do projecto) `EFATURA_MODE=SIMULADO|REAL`, validada no arranque (falhar se ausente ou inválida), listada em `.env.example`, nos compose e no `deploy.yml`. Se `REAL`, o bean simulado nem é construído (`@ConditionalOnProperty`); um teste de contexto prova que `REAL` + simulador falha o arranque.
- O modo usado fica **gravado em cada tentativa de comunicação** (`modo`), e o estado do simulador é terminal e distinto (`SIMULADO`), nunca `ACEITE`.
- O IUD simulado é marcado como tal (prefixo reservado ou dígito de controlo deliberadamente inválido) e o QR/rodapé do PDF mostra "DOCUMENTO SIMULADO — SEM VALOR FISCAL" enquanto o modo não for `REAL`.
- Banner visível na UI sempre que o modo for `SIMULADO` (o modo é público e não é um segredo; expor num endpoint autenticado).
- **Séries de simulação separadas das séries a registar na DNRE.** Documentos simulados que consumam a série que será ligada ao LED real fazem o go-live começar com um buraco/ conflito `[Fonte: o LED controla a sequencialidade]` (P-09).

**Warning signs:**
Bean simulado `@Primary` sem condição; estado `ACEITE`/"Comunicada" alcançável com o simulador; PDF sem marca de água; nenhum campo de modo na tabela de comunicação.

**Phase to address:** F-D (modo, estados), F-E (marca de água), F-G (verificação).

---

### P-09: Construir contra o formato eFatura errado/desactualizado e tratar o simulador como "documento fiscal"

**What goes wrong:**
O "formato exacto" fica certo para a versão errada do Manual Técnico, ou fica certo no XML mas errado na identificação (IUD/LED/tipo/número), e o salto para a ligação real exige reescrever o modelo.

**Why it happens:**
`[Fonte]` O Manual Técnico teve várias versões e o mais acessível na pesquisa (v7) não é o mais recente (v11 referido): a composição do IUD passou a **45 caracteres**, o dígito de controlo passou a **Luhn**, o LED passou a máximo **5 dígitos**, a numeração dos tipos de documento mudou e o modo de emissão saiu do IUD. Tipos (`[Fonte]`, numeração a confirmar): Fatura Eletrónica (01), Fatura-Recibo Eletrónica (02), Talão de Venda (03), Recibo (04), Nota de Crédito (05). `[Fonte]` O DFE é XML com estrutura própria, assinado com certificado ICP-CV e sujeito a autorização da DNRE em tempo real (Despacho n.º 43/2022 de 11 de abril, ao abrigo do art. 6.º do Decreto-Lei n.º 79/2020; DL de 12 de novembro segundo Miranda e VPQ, coerente com a entrada em vigor a 13/11/2020 — alguns resumos dizem 28 de dezembro, discrepância não resolvida). Sem essa assinatura e autorização, um documento gerado pelo LexCV **não tem validade fiscal**.

**How to avoid:**
- Ao começar F-D, descarregar o Manual Técnico **mais recente** e o XSD de `efatura.cv` (esta sessão não conseguiu) e fixar a versão em código (`VERSAO_FORMATO_EFATURA`), gravada em cada documento (`versao_formato`).
- Teste automático: gerar o XML e **validá-lo contra o XSD oficial** no CI (com `SchemaFactory` endurecido contra XXE, P-17).
- IUD gerado por função pura com testes de vectores tirados do manual (incluindo Luhn); não inventar algoritmo de dígito de controlo de NIF por analogia com Portugal.
- Modelo de dados guarda já o que o DFE exige (tipo, LED, número, data, NIF emitente/adquirente, linhas com código de motivo de não liquidação quando `Tipo de Imposto = NA` `[Fonte]`, referência ao documento original na NC `[Fonte]`) para a ligação real não obrigar a migrar histórico.
- Comunicar claramente ao utilizador e na documentação que, em modo `SIMULADO`, o documento é uma *pré-fatura sem valor fiscal* (risco de "falsa conformidade"); o requisito de software de faturação aprovado pela Administração Tributária `[Fonte, LOW]` e a custódia de certificados por escritório (P-16) ficam como pergunta para o marco da ligação real.

**Warning signs:**
Código a citar "manual v7"; XML nunca validado contra XSD; um único campo `estado` a misturar emissão e comunicação; copy de produto a chamar "Fatura" a um documento simulado sem ressalva.

**Phase to address:** F-D.

---

### P-10: A Fatura-Recibo só é legítima quando a data da fatura coincide com a do pagamento — e o sistema permite pagamentos retroactivos, sem data e parciais

**What goes wrong:**
Emitem-se Fatura-Recibo com data de emissão ≠ data do recebimento (pagamento registado dias depois), ou sobre pagamentos parciais de um honorário, em violação da regra do documento.

**Why it happens:**
`[Fonte]` A Fatura-Recibo Eletrónica agrega fatura + recibo e só pode ser emitida quando a data da fatura e a do pagamento coincidem (pronto pagamento); as faturas devem ser emitidas, em regra, até ao 5.º dia útil seguinte ao momento em que o imposto é devido. No código, `dataPagamento` é opcional na UI e anulável na entidade (`Pagamento.java`, `schemas/financeiro.ts`), o utilizador pode escolher qualquer data, e `Honorario` admite vários pagamentos parciais. A decisão bloqueada "exactamente uma Fatura-Recibo por pagamento" é compatível com o negócio, mas deixa de o ser quando o pagamento é retroactivo.

**How to avoid:**
- A data fiscal é **sempre do servidor** em `Atlantic/Cape_Verde` (P-20). No pedido, `dataPagamento` ausente ⇒ hoje (CV); se vier preenchida e ≠ hoje, **rejeitar a emissão da Fatura-Recibo** com mensagem clara (ou desviar para um fluxo distinto — Fatura + Recibo — se o produto o decidir). Nunca "ajustar" silenciosamente a data de um lado.
- Levar a decisão para a fase de discussão (ver "Questões para a fase de discussão"): pagamentos retroactivos, adiantamentos e parciais, e exigibilidade do IVA `[Analogia PT: em Portugal os adiantamentos tornam o imposto exigível; não verificado para CV]`.
- Teste: pagamento com data de ontem não gera Fatura-Recibo com data de hoje.

**Warning signs:**
A UI deixa `dataPagamento` vazio ou no passado e a fatura sai na mesma; datas de pagamento e de emissão guardadas em campos diferentes sem regra a relacioná-los.

**Phase to address:** F-B (regra), decisão em F-A/discussão.

---

### P-11: Os emitentes não têm dados fiscais — activar a faturação "para todos" parte o registo de pagamentos que já funciona

**What goes wrong:**
No dia do deploy, nenhum escritório tem NIF/morada/regime (baseline 6); se "registar pagamento" passa a exigir fatura, **todos** os pagamentos passam a falhar. Ou, ao contrário, a emissão avança com NIF nulo/`000000000` e gera documentos inválidos.

**Why it happens:**
`Tenant.nif` nunca teve caminho de captura e o tenant de plataforma tem NIF nulo por construção. Adicionar colunas `NOT NULL` a `t_tenant` já povoada é o erro recorrente do projecto (120/120b).

**How to avoid:**
- Dados fiscais numa tabela própria `t_dados_fiscais (tenant_id UNIQUE, nif, nome_fiscal, morada, regime_iva, ...)` (ou colunas anuláveis sem `NOT NULL`), preenchida por um ecrã de ADMIN do escritório e, para a plataforma, por `PLATAFORMA_ADMIN`.
- Flag **`faturacao_activa` por tenant, por defeito `false`**: com a flag desligada o registo de pagamentos comporta-se exactamente como hoje (coerente com "pagamentos existentes não são faturados retroactivamente"). Só liga quando os dados estão completos (NIF 9 dígitos, nome, morada, regime, série criada). Com a flag ligada e dados incompletos, **falha fechada com 409** *antes* de gravar o pagamento e de consumir número.
- Depois da primeira emissão, **bloquear a alteração do NIF/série do emitente** (o NIF está no IUD e a série está ligada ao LED): exige acção explícita de plataforma.
- Validar NIF com o mesmo `@Pattern` de 9 dígitos e rejeitar o placeholder `000000000` e duplicados entre tenants (P-25).

**Warning signs:**
Pagamentos a devolver 500/409 para tenants existentes após deploy; fatura com NIF `000000000`; `faturacao_activa` com default `true`.

**Phase to address:** F-A.

---

### P-12: Plataforma como emitente — confusão emitente/adquirente e identificação do tenant de plataforma por nome

**What goes wrong:**
A fatura de subscrição sai com o emitente errado (tenant do chamador, ou um tenant que se chama "LexCV"), ou com o adquirente sem NIF; ou o escritório não consegue ver a fatura que lhe foi emitida; ou, para a ver, abre-se uma leitura cruzada sem controlo.

**Why it happens:**
- `PlatformAdminController` por desenho nunca lê o tenant do chamador; o novo endpoint de emissão será o primeiro handler de plataforma a precisar de um emitente concreto.
- O emitente seria obtido por `findFirstByNome("LexCV")` (`TenantRepository`), sem `unique` em `nome` e sem recusa do nome reservado no `provisionTenant` (baseline 7); o seeder já documenta a corrida que cria duas linhas "LexCV".
- O adquirente (escritório) não tem NIF/morada capturados e `TenantAdminSummaryResponse` omite-os de propósito.
- `PLATAFORMA_ADMIN` não tem permissões de tenant; reutilizar `ResourceController`/`financeiro:*` não serve.
- Não há tabela de preços (`TenantPlano` não tem valor): o montante é input manual.

**How to avoid:**
- Emitente = **tenant do principal `PLATAFORMA_ADMIN` autenticado** (o utilizador de plataforma pertence ao tenant reservado), validado contra o tenant reservado e protegido por `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` ao nível da classe, num controller/serviço **novo e separado** sob `/api/v1/platform/**`. Não resolver o emitente por nome. Recusar o nome reservado em `provisionTenant`. Documentar que este handler é a excepção deliberada a "plataforma não lê tenant".
- Dados fiscais do adquirente (escritório) num DTO próprio só para plataforma (não alterar `TenantAdminSummaryResponse`); exigir NIF/morada completos antes de emitir; snapshot no documento.
- Decidir já como o escritório vê as suas faturas de subscrição: ou só por email (sem leitura cruzada) ou endpoint de leitura `findByIdAndAdquirenteTenantId` com scope próprio e teste de isolamento. Não deixar para "depois".
- Montante e período da subscrição inseridos pelo `PLATAFORMA_ADMIN` no momento (validação de intervalo, escala 2); nunca vêm do cliente web sem verificação.
- Séries da plataforma separadas das de escritório, com `tenant_id` do tenant reservado.

**Warning signs:**
`findFirstByNome("LexCV")` no código de faturação; `getTenantId()` do `ResourceController` a ser usado num handler de plataforma; emissão que só falha em produção porque o tenant de plataforma não tem NIF.

**Phase to address:** F-A (dados), F-F (endpoint), F-G (isolamento).

---

### P-13: Nota de Crédito que excede o original, credita uma NC, ou corre em simultâneo

**What goes wrong:**
Duas NC parciais em paralelo somam mais do que a Fatura-Recibo; uma NC é emitida sobre outra NC; uma NC refere um documento que nunca existiu; o IVA das NC parciais ultrapassa o IVA original por arredondamento.

**Why it happens:**
`[Fonte]` O DFE guarda referência ao documento original numa NC (campo de referência a outros DFE). Que a soma das NC não exceda o original e que não se credite uma NC são **invariantes de desenho** `[Inferência; Analogia PT: em Portugal o documento rectificativo referencia o original]`, não regras CV confirmadas — mas sem elas o sistema permite estados absurdos. Dois pedidos concorrentes passam ambos a verificação "ainda cabe".

**How to avoid:**
- NC referencia obrigatoriamente `fatura_origem_id` com `tipo = FR` (validar tipo; recusar NC sobre NC) do **mesmo** emitente (`emitente_tenant_id` igual, nunca por id apenas).
- `SELECT ... FOR UPDATE` na linha da fatura original e verificação `soma(NC existentes) + nova ≤ total` na mesma transacção; segundo lock: a série (ordem fixa, P-01).
- NC parcial por valor: base e IVA repartidos pela mesma regra de arredondamento do original; a última NC clampa ao remanescente para o IVA acumulado nunca exceder o original.
- Efeitos da NC na conta corrente na mesma transacção, atómicos (P-02).
- IT de concorrência (duas NC parciais em simultâneo) e teste de "NC sobre NC → 422".

**Warning signs:**
`NC.total` não validado contra o remanescente; ausência de lock no original; campo de referência opcional.

**Phase to address:** F-C.

---

### P-14: A NC reverte o saldo mas as outras três leituras de "pago" continuam a contar o pagamento

**What goes wrong:**
Após uma NC total, o honorário continua "pago" (`@Formula totalPago`), o KPI "recebido no mês" não desce, e o alerta `HONORARIO_ATRASADO` fica calado — enquanto a conta corrente já foi revertida. O sistema discorda de si próprio.

**Why it happens:**
Baseline 13: quatro leituras independentes. O requisito diz "revertendo o saldo da conta corrente" e esquece as restantes. Além disso `calculateMensalReceived` já é defeituoso: compara só o número do mês (`:3257`) e faz `pag.getValorPago()` sem null-check.

**How to avoid:**
- Decidir **uma** forma de modelar o efeito: (a) a NC gera um movimento de estorno no livro de pagamentos (linha negativa ligada à NC; exige afrouxar a validação `valorPago > 0` de forma controlada), ou (b) as três leituras passam a subtrair o `valor_creditado`. Recomendação: (b) com um único método de serviço "valor líquido pago por honorário" reutilizado por `@Formula`/KPI/alertas, em vez de espalhar subtracções.
- Corrigir `calculateMensalReceived` (ano + mês, fuso CV, null-check) quando o tocar.
- Testes: NC total → `totalPago` líquido, KPI e alerta consistentes com o saldo.

**Warning signs:**
Só `contaCorrenteRepository` é tocado pelo código da NC; honorário "pago" com NC total emitida.

**Phase to address:** F-C.

---

### P-15: Armadilhas de migração e de `ddl-auto` (não há runner; `validate` em produção; 10 scripts já pendentes)

**What goes wrong:**
- Instalações em `validate` não arrancam até o script manual correr; instalações novas (Path A) saltam o script e ficam **sem** os invariantes que só o script cria.
- `ALTER ... ADD COLUMN ... NOT NULL` em tabela povoada falha (já aconteceu: `Tenant.ativo`/`plano`, 120/120b).
- `@Enumerated(STRING)` gera `CHECK` na criação; acrescentar um valor ao enum mais tarde **não** actualiza o `CHECK` com `update` `[Fonte: discussão Hibernate 6]` — o estado de comunicação vai evoluir.

**Why it happens:**
O README é o inventário autoritativo e "só 8 de 18 scripts são re-executáveis"; há 10 scripts pendentes numa base de cliente. Índices parciais, triggers e `CHECK` complexos não são expressáveis por anotações JPA, logo `update` nunca os cria.

**How to avoid:**
- Tabelas **novas** em vez de colunas `NOT NULL` em tabelas povoadas; colunas novas em tabelas existentes sempre anuláveis (ou com `columnDefinition ... default`, como `Tenant.ativo`). Não tocar `t_pagamento` com `NOT NULL`: a ligação pagamento→fatura vive em `t_fatura.pagamento_id UNIQUE`.
- Todos os invariantes críticos expressos em JPA/`@Column`/`@UniqueConstraint`/`@Check` (este só na criação da tabela) para que o `update` os crie em Path A. O que não couber vai para o script **e** para uma verificação no arranque (precedente: `VerificacaoDerivaPapeisService`) que falha rápido se faltar.
- `precision`/`scale` explícitos em todas as colunas monetárias e de taxa (senão `validate` e `update` divergem, e o default de `BigDecimal` é `numeric(38,2)`).
- Estados e tipos como `columnDefinition = "varchar(32) not null"` (sem `CHECK` gerado), validados na aplicação.
- Script `<fase>-create-faturacao-tables.sql` idempotente (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`) com cabeçalho "o quê / porquê / o que parte sem ele / é re-executável", **linha nova na tabela do README no mesmo commit**, e entrada em "Known execution status". Testar o arranque com `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` contra uma BD com o script aplicado **e** contra uma BD nova com `update`.
- Pré-requisito de deploy documentado em `DEPLOYMENT.md` ("Two-Stage Boot") e no handoff de F-G.

**Warning signs:**
Entidade nova sem linha no README; `nullable = false` numa coluna acrescentada a tabela existente; aplicação a arrancar bem em dev (`update`) mas a recusar em `validate`; invariantes só presentes onde o script correu.

**Phase to address:** F-A (esquema e script), F-G (inventário e verificação em `validate`).

---

### P-16: Segredos, certificados e erros — o que nunca pode ir para a BD, o MinIO, os logs ou a resposta HTTP

**What goes wrong:**
Certificado ICP-CV/palavra-passe, credenciais SMTP ou dados fiscais ficam em colunas, em ficheiros no bucket, em logs ou em mensagens de erro devolvidas ao browser.

**Why it happens:**
- Convenção do projecto: segredos só por variáveis de ambiente (`application.yml` importa `.env`), mas certificados são **ficheiros + palavra-passe**, e a natural tentação é uma coluna `certificado_pfx` por tenant.
- `[Fonte]` Cada emitente (pessoa colectiva) precisa de certificado qualificado (selo electrónico) ICP-CV e o transmissor de um certificado de autenticação web (SSL-EV): numa SaaS multi-tenant a **custódia por escritório** é uma questão de arquitectura que o desenho do adaptador não pode ignorar, mesmo que a ligação real fique para depois.
- O catch-all devolve `ex.getMessage()` (baseline 9): excepções de `MailAuthenticationException`/`MessagingException`/HTTP incluem nomes de host, endereços e por vezes corpo da resposta.
- `log.warn(..., ex)` é o idioma do código; uma excepção de BD/SMTP pode transportar NIF/email.
- Novas variáveis têm de ser acrescentadas a **3 compose + deploy.yml + .env.example** (a v2.12 já teve um bug de falta de passthrough de env em produção) e, se obrigatórias sem default, partem qualquer contexto Spring que não as tenha (CI, `@SpringBootTest`) — o bloqueio recorrente `MINIO_ENDPOINT`.

**How to avoid:**
- Interface `CofreCredenciais`/`ProvedorCredenciais` atrás do adaptador: hoje lê variáveis/ficheiros montados (caminho por env, montado como volume/Docker secret); **nunca** BD, **nunca** MinIO, nunca fixture/commit. Nesta fase o simulador não precisa de nenhuma.
- SMTP: `MAIL_HOST/PORT/USER/PASSWORD` + `EMAIL_ENABLED` explícito; sem `EMAIL_ENABLED=true` o envio é um `EmailSender` que regista só o `id` do documento (nunca endereço/NIF/nome) e marca "não enviado". Variáveis condicionais a `EMAIL_ENABLED` para não partir o CI.
- Handler de excepções: nova excepção de domínio (`FaturacaoException`, `ComunicacaoException`) com mensagem fixa em português; o catch-all não pode ecoar mensagens destas. Rever `server.error.include-message: always`.
- Checkpoint humano antes de ligar SMTP real (`always_confirm_external_services`).
- Grep de logs: nenhum `log.*` com `nif`, `email`, `nome`, `descricao` do documento; ids apenas.

**Warning signs:**
Coluna/ficheiro de certificado em BD/MinIO; `System.getenv`/`@Value` espalhados; mensagens de erro SMTP visíveis no toast; `.env.example` sem as variáveis novas.

**Phase to address:** F-D (credenciais), F-E (SMTP), F-G (verificação ASVS V7/V14). Severidade: **alta**.

---

### P-17: SpotBugs/FindSecBugs bloqueiam o CI — XXE, header injection, templates, mass assignment — e as dependências novas não passam por SCA

**What goes wrong:**
`mvn spotbugs:check` falha o job `test` e bloqueia o `build-and-push`; ou, pior, alguém suprime em `spotbugs-exclude.xml` para "desbloquear" e o defeito real fica.

**Why it happens:**
Há três superfícies novas: **XML** (gerar o DFE, validar contra XSD, ler a resposta da DNRE), **email** (JavaMail) e **PDF/template** (HTML ou desenho directo). Detectores relevantes do FindSecBugs `[Fonte]`: `XXE_DOCUMENT`/`XXE_SAXPARSER`/`XXE_XMLREADER` (e variantes de `Transformer`/StAX), `SMTP_HEADER_INJECTION`, `TEMPLATE_INJECTION_*`, `SPEL_INJECTION`, `XML_DECODER`. Mais o já suprimido `ENTITY_MASS_ASSIGNMENT` para `@RequestBody` tipado como entidade JPA.

**How to avoid:**
- **Gerar** XML por StAX (`XMLStreamWriter`) ou JAXB, não por `DocumentBuilder`/`Transformer`. **Ler/validar** XML (resposta DNRE, XSD) com `XMLConstants.FEATURE_SECURE_PROCESSING`, `disallow-doctype-decl` ou `ACCESS_EXTERNAL_DTD/SCHEMA = ""`; teste com payload `<!DOCTYPE ... SYSTEM "file:///etc/passwd">` numa resposta simulada da DNRE.
- Email: `MimeMessageHelper`, validar o endereço (`InternetAddress.validate()` + regex estrita, sem `\r\n`, um único destinatário), nome do remetente/assunto sem controlo do utilizador; teste com `\r\nBcc:` no nome do cliente.
- Templates **estáticos** em ficheiro; dados só como variáveis escapadas; nunca construir o template a partir de dados. Se HTML→PDF: escapar tudo, desligar carregamento de recursos externos e JS (logótipo e `descricao` são texto livre → SSRF/leitura de ficheiros, cf. [SSRF/XXE em geradores de PDF](https://securityboulevard.com/2019/05/ssrf-and-xxe-vulnerabilities-in-pdfreactor/)); preferir desenho directo (OpenPDF/PDFBox) sem motor HTML.
- DTO/`record` explícito nos novos endpoints (`dtos/` já tem precedente) — **nunca** `@RequestBody` tipado como entidade, e **não** acrescentar entradas novas a `spotbugs-exclude.xml`.
- Nomes de ficheiro fixos (`fatura-<uuid>.pdf`) para evitar `PATH_TRAVERSAL_IN`; sanitizar texto livre antes de logar (`CRLF_INJECTION_LOGS`).
- Correr `mvn spotbugs:check` localmente a cada plano de F-D/F-E, não só no CI; correr `dependency-check:check` manualmente uma vez para as dependências novas (PDF, mail, QR) porque o CI adiou o SCA.
- Licenças: evitar iText ≥ 7 (AGPL) para código fechado; verificar licença de qualquer biblioteca de PDF/QR antes de adicionar.

**Warning signs:**
`DocumentBuilderFactory.newInstance()` sem endurecimento; `setSubject`/`setRecipients` com texto do cliente; entrada nova em `spotbugs-exclude.xml`; PR a falhar só no CI.

**Phase to address:** F-D, F-E, e F-G (gate). Severidade: **alta** (o gate bloqueia em `high`).

---

## Moderate Pitfalls

### P-18: IVA, regime de isenção e retenção na fonte assumidos em vez de configurados

**What goes wrong:**
Taxa de 15% hardcoded; escritório isento ou em regime simplificado a emitir com IVA; retenção na fonte ignorada, ou assumida com a taxa errada.

**Why it happens:**
O sistema não tem hoje qualquer conceito fiscal. `[Fonte, LOW]` A taxa normal de IVA é 15%; a isenção/não liquidação exige código de motivo (`Tipo de Imposto = NA` → "Código de Motivo de não Liquidação de Imposto") e há regimes simplificados de pequenos contribuintes em que a emissão de fatura pode ter outro enquadramento; a retenção de IRPS (categoria B) sobre honorários pagos por entidades com contabilidade organizada é paga pelo **adquirente** (valores citados nos resumos — 20% e 4% no regime simplificado — são **`[Não verificado]`** e não devem entrar em código). Resultados de pesquisa misturaram CV com regras portuguesas (p.ex. limite de isenção do artigo 53.º), o que reforça a necessidade de validação.

**How to avoid:**
- Regime, taxa, código de isenção e indicação de retenção **como dados** (`t_dados_fiscais` + código por linha), com validação de contabilista certificado antes de F-B, e testes com casos fornecidos por ele.
- Definir e mostrar na UI a semântica do montante: `valorPago` é **total recebido (IVA incluído)** ou líquido? Gravar explicitamente; hoje `Honorario.valorTotal`/`valorPago` não têm semântica fiscal e `totalPago` vs `valorTotal` alimenta o alerta de atraso.
- Retenção: cliente (empresa) paga menos do que o total do documento. Decidir como conta corrente, `totalPago` e a Fatura-Recibo representam o valor recebido vs o valor do documento (pergunta em aberto).

**Warning signs:**
Constante `0.15` no código; `valor_pago` a servir de base de IVA sem decisão escrita; ausência de campo de motivo de isenção.

**Phase to address:** F-A (decisão e dados), F-B (cálculo).

---

### P-19: Arredondamento e precisão — IVA por linha vs total, `BigDecimal` sem escala, PDF ≠ BD ≠ XML

**What goes wrong:**
`base + IVA ≠ total` por 0,01; o PDF mostra um total, o XML outro; a DNRE rejeita por inconsistência de totais.

**Why it happens:**
`createPagamento` usa `pag.getValorPago()` depois do `save` (`:3069`), ou seja, o valor **do pedido**, não o arredondado pelo Postgres (`numeric(38,2)`); o JSON `1e3` e valores como `100.005` entram (Zod aceita `Number("1e3")`). CVE tem 2 casas decimais (ISO 4217). A regra de agregação do IVA (por linha somado vs por taxa sobre a soma das bases) varia entre sistemas `[Analogia PT: SAF-T calcula/valida por taxa sobre a soma das bases, tolerâncias de 0,01]`; a regra e a tolerância de CV estão `[Não verificado]` no XSD/Manual v11.

**How to avoid:**
- Entrada: `valorPago` normalizado (`setScale(2, HALF_UP)`), rejeitar >2 casas, ≤0, e magnitude absurda, **antes** de calcular.
- Uma única função pura `CalculoFiscal` em `BigDecimal` com `RoundingMode.HALF_UP` explícito e uma regra escolhida e documentada; resultado **gravado** (linha, resumo por taxa, totais) e lido por XML, PDF e API — **nunca recalculado** a jusante.
- Se `valorPago` é bruto: `base = round(bruto / (1+taxa))`, `IVA = bruto − base` (o resíduo vai ao IVA) para garantir `base + IVA = bruto`.
- Testes parametrizados de invariantes (`base+IVA=total`, soma das linhas = totais, NC parciais ≤ original) com centenas de montantes, incluindo valores nos limites de arredondamento.
- Formatação de números/datas no PDF com `Locale` explícito (`pt-CV`/`pt-PT`), porque o container Alpine corre com locale por defeito (inglês) e produziria "1,234.50".

**Warning signs:**
`double`/`float`; `new BigDecimal(double)`; `setScale` ausente; cálculo repetido no serviço de PDF; discrepâncias de 0,01 entre PDF e XML.

**Phase to address:** F-B.

---

### P-20: Data e hora fiscais no fuso errado

**What goes wrong:**
Entre as 23:00 e as 24:00 de Cabo Verde (00:00–01:00 UTC) o `LocalDate.now()` do container devolve o dia seguinte: a fatura sai com data errada, o IUD (que embebe a data `[Fonte]`) fica errado, e a regra "data da fatura = data do pagamento" (P-10) falha.

**Why it happens:**
Baseline 5: container em UTC, UTC-1 em Cabo Verde, e o código mistura `LocalDate.now()` (JVM) com o `ZoneId` explícito do job. `LocalDateTime` na BD é sem fuso.

**How to avoid:**
- Um `Clock` injectável (`Clock.system(ZoneId.of("Atlantic/Cape_Verde"))`) usado por **todo** o código de faturação; `data_emissao` (`LocalDate`, CV) e `emitido_em` (`Instant`/`timestamptz`, UTC) guardados em separado. Nunca `LocalDate.now()` sem argumento no pacote fiscal.
- Cabo Verde não tem horário de verão (UTC-1 constante) `[Não verificado nesta sessão; conhecimento geral]`, mas usar o `ZoneId`, não um offset fixo.
- Teste com `Clock.fixed` às 23:30 CV e às 00:30 CV; PDF mostra a data CV.
- Considerar fixar `-Duser.timezone`/`TZ` no container **não** substitui o `Clock` (o job existente prova que o projecto prefere zona explícita).

**Warning signs:**
`LocalDate.now()`/`LocalDateTime.now()` sem zona nos novos serviços; testes que só correm a meio do dia.

**Phase to address:** F-B.

---

### P-21: Adquirente sem NIF, estrangeiro, "consumidor final", NIF legado inválido, email inválido

**What goes wrong:**
Cliente legado com NIF nulo/inválido; cliente estrangeiro (diáspora, empresa estrangeira) sem NIF de 9 dígitos; tentação de fabricar um NIF genérico; email com `;` ou vários endereços.

**Why it happens:**
Baseline 11: NIF só validado a nível de controller (`validation.mode: none`), valores legados tolerados, sem país, sem "consumidor final", `email` livre. `[Analogia PT]` Em Portugal "Consumidor final" usa um NIF genérico; para CV a regra para identificação do adquirente e o NIF genérico estão `[Não verificado]`. Num escritório de advogados o cliente é quase sempre identificado (procuração), pelo que "consumidor final" é raro, mas estrangeiros não.

**How to avoid:**
- Validação **no momento da emissão**, sobre o snapshot: NIF 9 dígitos + nome + morada; falhar fechado com mensagem que diga **o que corrigir** (e onde: ficha do cliente), antes de gravar o pagamento.
- Não fabricar NIF genérico. "Consumidor final" e "cliente estrangeiro" ficam decisão de produto explícita (ver questões); se fora de âmbito, bloquear com mensagem clara e registar.
- Não inventar algoritmo de dígito de controlo para o NIF de CV.
- Email: validar formato e unicidade de destinatário antes de pôr na outbox; o resto do fluxo não depende de o email existir (documento emitido mesmo sem email; estado "sem destinatário").
- Mostrar ao utilizador, antes de confirmar, nome/NIF do adquirente que vai constar (P-26).

**Warning signs:**
`nif` default/placeholder no código; NIF do cliente lido no momento do PDF e não do snapshot.

**Phase to address:** F-B (validação), F-E (email).

---

### P-22: O PDF não corresponde ao documento guardado; fontes e locale no Alpine; sigilo profissional no texto da linha

**What goes wrong:**
O PDF reemitido difere do original (cliente editou nome, logo mudou, cálculo recomputado); em produção o PDF falha ou mostra caracteres errados; a descrição do honorário (texto livre) revela o caso do cliente no PDF, no email e no XML para a DNRE.

**Why it happens:**
- Gerar o PDF a partir de linhas vivas (`Cliente`, `Tenant.logoDataUrl`, `Honorario.descricao`).
- `eclipse-temurin:23-jre-alpine` não traz fontconfig/fontes: bibliotecas que dependem de AWT/Java2D falham em Alpine mesmo funcionando no Windows/macOS de desenvolvimento (`[Não verificado]`, problema conhecido de imagens Alpine).
- `Honorario.descricao` é texto livre (`Honorario.java`) e pode conter nomes de partes, números de processo, matéria. `[Analogia PT]` Em Portugal o dever de sigilo do advogado leva a descrições genéricas em faturas; a regra equivalente no Estatuto da Ordem dos Advogados de CV está `[Não verificado]`. Lei de protecção de dados: Lei n.º 133/V/2001, alterada pelas Leis 41/VIII/2013 e 121/IX/2021, com CNPD `[Fonte]`.

**How to avoid:**
- Gerar o PDF **só** a partir do snapshot da `Fatura` e guardar os bytes + SHA-256 no momento da emissão (MinIO, chave própria); reenvios e downloads servem o artefacto guardado. Segunda impressão rotulada "Duplicado"/"2.ª via" `[Analogia PT]`.
- Embutir fontes (TTF com licença livre) como recurso do classpath; correr o teste de PDF **na imagem de runtime** (ou num IT com a mesma base) para apanhar o problema de Alpine antes do deploy.
- Texto da linha: descrição **controlada** (p.ex. "Honorários por serviços jurídicos" + referência opcional ao número de processo) e não o texto livre do honorário por defeito; qualquer texto livre editável só antes da emissão, mostrado ao utilizador, e fora do email.
- Logótipo: omitir ou snapshot controlado (é um data URL livre e mutável).
- Teste: extrair o texto do PDF e comparar com o snapshot (totais, número, NIF).

**Warning signs:**
`ClienteRepository`/`TenantRepository` injectados no serviço de PDF; PDF diferente após editar o cliente; stack trace de fontes só em container.

**Phase to address:** F-E (PDF), F-A/F-B (política de descrição).

---

### P-23: Email — PII em trânsito, header injection, entregabilidade, links pré-assinados

**What goes wrong:**
Documento fiscal (nome, NIF, morada, valor, serviço jurídico prestado) enviado a destinatário errado ou em claro sem controlo; spam/rejeição por falta de SPF/DKIM; link do PDF que expira ou serve como bearer; erro de SMTP que reverte a fatura (P-07) ou vaza ao cliente (P-16).

**Why it happens:**
É o primeiro canal SMTP do projecto: não há padrão, nem infra, nem política (PROJECT.md mantém as notificações in-app; email só para documentos fiscais). `Cliente.email` não é validado (baseline 11). O remetente é a plataforma, em nome de um escritório.

**How to avoid:**
- Outbox (P-07), um destinatário por mensagem, endereço validado, `Reply-To` do escritório, `From` de domínio controlado pela plataforma com SPF/DKIM/DMARC configurados (tarefa de infra, checkpoint humano).
- Anexar o PDF guardado (não enviar URL pré-assinada, TTL por defeito 3600 s).
- Corpo mínimo: tipo, número, valor total, escritório; **sem** descrição do serviço nem dados de processo. Assunto sem texto livre do cliente.
- Estados de envio distintos: `ENVIADO_SMTP` ≠ entregue; sem destinatário válido ⇒ "sem email", nunca falha da emissão.
- Logs só com id do documento.

**Warning signs:**
Corpo do email a interpolar `descricao`; `mailSender.send` num `@Transactional`; envio a `cliente.getEmail()` sem validação.

**Phase to address:** F-E. Severidade: média-alta.

---

### P-24: RBAC — scopes novos em quatro sítios, papéis snapshot e o `ASSISTENTE`

**What goes wrong:**
Um scope novo (`faturas:*`) não aparece em papéis já instanciados (v2.17: editar um molde nunca propaga), o `ADMIN` hardcoded tem-no mas outros papéis não, o frontend e o backend divergem, ou um `ASSISTENTE` (sem `financeiro:view`) vê faturas/PDFs.

**Why it happens:**
Baseline 16. Acrescentar permissões exige mexer em `CATALOGO_PERMISSOES`, `UserPrincipal.create`, `KNOWN_SCOPES` e nos moldes, e uma migração que dê o scope aos papéis certos dos tenants existentes — o que a conversão zero-drift da Phase 126 foi desenhada para *não* permitir sem verificação.

**How to avoid:**
- **Reutilizar `financeiro:*`** para as faturas de escritório (`financeiro:view` ler, `financeiro:edit` emitir via pagamento, `financeiro:manage` NC): zero migração de RBAC e o `ASSISTENTE` fica automaticamente de fora. Plataforma: gate `hasRole('PLATAFORMA_ADMIN')` de classe (precedente). Configuração fiscal do escritório: decidir o scope (não esconder atrás de `users:manage` por conveniência).
- Se se criar scope novo: actualizar os 4 sítios no mesmo plano, `UserPrincipalCatalogoSyncTest`, `permissions.ts`, e planear a atribuição aos papéis existentes.
- Matriz de testes 4 papéis (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) em cada endpoint novo, e `permissions.isFetched` (nunca `!isLoading`) nos ecrãs. "Ambas as camadas têm de concordar" (CLAUDE.md).

**Warning signs:**
Scope novo só no `@PreAuthorize`; testes só com ADMIN; `isLoading` nos guards.

**Phase to address:** F-A (decisão), F-G (matriz).

---

### P-25: Colisões entre tenants em séries/números e NIF duplicado entre emitentes

**What goes wrong:**
Duas séries "FR/2026" de tenants diferentes colidem (chave global); dois tenants com o mesmo NIF (reprovisionado, copiado ou o `000000000` do demo) partilham o mesmo emitente fiscal e, em modo real, o mesmo LED/sequência na DNRE.

**Why it happens:**
`t_tenant.nome` e `nif` não têm `unique`; o projecto tem precedentes de unicidades por tenant (`(tenant_id, documento_numero)`) mas a tentação é uma chave global `numero`.

**How to avoid:**
- Todas as unicidades de documento/série **compostas com `tenant_id`**: `UNIQUE (tenant_id, tipo, codigo)` na série; `UNIQUE (emitente_tenant_id, serie_id, numero)` no documento. Nunca uma SEQUENCE global.
- `UNIQUE` parcial/validação de aplicação para `nif` do emitente entre tenants (único activo); recusar `000000000`.
- IT: dois tenants emitem em paralelo na "mesma" série lógica; ambos obtêm 1..N independentes e nenhum vê o outro.

**Warning signs:**
Índice único só em `numero` ou `codigo`; dois tenants a partilhar NIF na BD de dev.

**Phase to address:** F-A (constraints), F-B (IT).

---

## Minor Pitfalls

### P-26: Frontend — gates de permissão, confirmação irreversível, estados e compatibilidade de resposta

**What goes wrong:**
Guard `!permissions.isLoading` (race já corrigida 3 vezes); emissão irreversível sem pré-visualização do que vai constar (erro de NIF é o erro mais comum e só se corrige com NC + nova fatura); resposta de `POST /pagamentos` que muda de forma e parte o hook existente; botão "apagar pagamento" visível para pagamentos faturados.

**How to avoid:**
- `permissions.isFetched`; diálogo de confirmação com nome/NIF do adquirente, valor, IVA, data fiscal e aviso "documento imutável"; `moneyString` endurecido (regex decimal, sem notação científica); resposta **aditiva** (manter os campos de `Pagamento` e acrescentar `fatura`); esconder apagar quando há fatura (e o backend recusa de qualquer forma, P-02); banner `SIMULADO` (P-08); polling do estado de comunicação (30 s, padrão do projecto). `[Fonte, LOW]` A DNRE admite anulação em certas condições (documento ainda não entregue; erro de NIF): enviar o PDF por email automaticamente à emissão fecha essa janela — coerente com a decisão "só NC", mas reforça a pré-visualização.

**Phase to address:** F-G (UI), F-B (contrato de resposta).

### P-27: Sobre-engenharia por analogia com Portugal

**What goes wrong:**
Implementar cadeia de hash/assinatura RSA do SAF-T(PT) e ATCUD "porque é assim que se faz".

**Why it happens / How to avoid:**
`[Fonte]` Em CV a validade vem da assinatura ICP-CV do XML + autorização em tempo real + IUD (QR), não de hash encadeado. O SAF-T(CV) existe como obrigação separada de exportação a pedido `[Fonte]` — não é âmbito do v3.0, mas o modelo deve permitir exportá-lo depois. Não construir cadeia de hash nem ATCUD.

**Phase to address:** F-B/F-D (revisão de âmbito).

### P-28: Desempenho — lock da série, PDF no thread do pedido, listagens sem paginação, N+1

**What goes wrong:**
Lock da série mantido durante PDF/rede; PDF gerado no pedido HTTP (CPU/memória, multipart já permite 50 MB); listagem com `findByTenantId` + stream (padrão do `listClientes`); hidratação de nomes por linha.

**How to avoid:**
Transacção curta (P-01/P-07); PDF gerado após commit e guardado; listagens paginadas no SQL com `tenant_id` primeiro; hidratação em lote com `findAllById` + `Map` (precedente "lookup Map" da Phase 104).

**Phase to address:** F-B/F-E.

---

## Technical Debt Patterns

| Shortcut | Benefício imediato | Custo a longo prazo | Quando aceitável |
|----------|--------------------|---------------------|------------------|
| Reutilizar `Documento`/`StorageService` genérico para PDFs fiscais | Zero código novo de armazenamento | PDFs apagáveis por `documentos:edit`, visíveis a quem não tem `financeiro:view` (P-06) | Nunca |
| Pôr a lógica de emissão em `ResourceController` (já 3384 linhas) | Menos ficheiros | Fronteira transaccional confusa, testes difíceis, `catch` herdado (P-02) | Nunca; serviço novo + controller novo, e `createPagamento` só delega |
| SEQUENCE/IDENTITY do Postgres para o número fiscal | Simples, sem lock | Lacunas em qualquer rollback (P-01) | Nunca |
| `@Async`/`@TransactionalEventListener` como único mecanismo de comunicação/email | Pouco código | Perda silenciosa em crash, sem retry nem auditoria (P-07) | Só como acelerador de uma outbox |
| Constantes `0.15`, NIF genérico, motivo de isenção fixos | Arranca depressa | Documentos errados para qualquer escritório fora do caso típico (P-18/P-21) | Nunca |
| Simulador como bean `@Primary` sem modo | Demo imediata | Falsa conformidade em produção (P-08) | Nunca |
| Estado de comunicação na tabela do documento imutável | Uma tabela | Obriga a retirar `@Immutable` (P-06) | Nunca |
| `ddl-auto: update` como "migração" das tabelas novas | Funciona em dev | `validate` em produção não arranca; invariantes ausentes em Path A (P-15) | Só em dev, acompanhado do script e da linha no README |
| Calcular IVA no frontend para "mostrar já" | Pré-visualização rápida | Dois cálculos a divergir (frontend é "burro" por decisão do projecto) | Nunca; mostrar o resultado devolvido por um endpoint de pré-cálculo |
| Suprimir um achado do SpotBugs para o CI passar | Desbloqueia o merge | Defeito real escondido (P-17) | Nunca sem a revisão de 1 a 1 que o ficheiro exige |

## Integration Gotchas

| Integração | Erro comum | Abordagem correcta |
|------------|-----------|--------------------|
| DNRE / eFatura (futuro real) | Chamar dentro da transacção; reenviar sem idempotência; confiar no XML sem XSD; certificado em BD | Outbox, IUD como chave de idempotência, validação XSD no CI, `CofreCredenciais` por env/volume (P-07/P-09/P-16) |
| SMTP (primeiro canal) | Timeouts infinitos por defeito; `send` em transacção; endereço sem validar | Três timeouts definidos, outbox, validação estrita, `EMAIL_ENABLED` explícito (P-07/P-23) |
| MinIO | Guardar PDF fiscal como `Documento`; URL pré-assinada de 3600 s por email | Chave `<tenantId>/faturas/<id>.pdf`, SHA-256, servir pela API com tenant check (P-04/P-06) |
| Biblioteca de PDF | Motor HTML a carregar recursos externos; fontes de sistema em Alpine; locale por defeito | Desenho directo ou HTML escapado sem rede; fontes embutidas; `Locale` explícito (P-17/P-22) |
| PostgreSQL | `FOR UPDATE` mantido durante I/O; ordem de locks inconsistente; `CHECK` de enum não actualizado | Transacção curta, ordem fixa série→conta corrente, `varchar` sem `CHECK` (P-01/P-15) |
| Spring `@Transactional` | `catch` de `DataAccessException` dentro da transacção; `@Transactional` em método `private`/chamada interna | `catch` removido, serviço separado e invocado por bean (P-02) |
| Docker/Compose | Variável nova só em `.env.example` | Acrescentar a `docker-compose.yml`, `docker-compose.hostinger.yml`, `.prod`, `deploy.yml` (P-16) |
| Frontend `apiFetch` | Toast automático com `message` do backend (incluindo erros internos) | Mensagens de domínio fixas; o catch-all não ecoa excepções (P-16) |

## Performance Traps

| Trap | Sintomas | Prevenção | Quando parte |
|------|----------|-----------|--------------|
| Lock da série mantido durante PDF/rede | Emissões serializadas, timeouts, pool Hikari esgotado | Transacção curta; PDF e adaptador pós-commit | Poucas emissões concorrentes do mesmo escritório já se notam se houver I/O dentro |
| Outbox sem `SKIP LOCKED` | Duas instâncias a enviar o mesmo email/DFE | `FOR UPDATE SKIP LOCKED` + idempotência por IUD | A partir da 2.ª instância |
| Listagem de faturas via `findByTenantId` + stream | Latência proporcional ao histórico | Paginação em SQL, `tenant_id` primeiro, índice `(emitente_tenant_id, data_emissao DESC)` | Milhares de documentos por escritório |
| PDF no thread do pedido | Picos de CPU/memória, pedidos lentos | Gerar uma vez pós-commit, servir o guardado | Emissões em rajada |
| Hidratar nomes por linha | N+1 nas listagens | `findAllById` + `Map` | Listas com dezenas de linhas |
| Reprocessar a outbox sem limite | Tempestade de retries quando o SMTP/DNRE está em baixo | Backoff exponencial e máximo de tentativas | Primeira indisponibilidade prolongada |

## Security Mistakes

| Erro | Risco | Prevenção | Gravidade (ASVS L1) |
|------|-------|-----------|---------------------|
| Fatura/PDF sem `tenant_id` próprio ou lida por `findById` | Fuga entre tenants (a fronteira principal do produto) | Repositório estreito com tenant; IT com 2 tenants + plataforma (P-04) | Alta, bloqueia |
| `emitente`/`série`/`tenant` aceites do corpo do pedido | Emitir em nome de outro escritório | Tudo derivado do principal e validado no serviço | Alta |
| XML da DNRE lido sem endurecimento (XXE) | Leitura de ficheiros/SSRF a partir do backend | Parser sem DTD/entidades externas; teste com payload (P-17) | Alta, bloqueia (FindSecBugs) |
| Header injection em email (nome/email do cliente) | Envio a terceiros, phishing a partir do domínio | Validação estrita, `MimeMessageHelper`, teste `\r\nBcc:` (P-17/P-23) | Alta |
| Certificado/credenciais em BD, MinIO, logs ou mensagens | Falsificação fiscal, abuso de SMTP | Env/volume, `CofreCredenciais`, excepções de domínio (P-16) | Alta |
| Erro interno ecoado por `GlobalExceptionHandler` | Divulgação de hosts, NIF, SQL | Excepções de domínio com mensagem fixa (P-16) | Média |
| PII (NIF, email, nome, descrição) em logs | Violação de dados pessoais (Lei 133/V/2001 e alterações) | Logar só ids; sanitizar texto livre (CRLF) | Média |
| URL pré-assinada longa para PDF | Link como bearer que sobrevive ao logout | Servir pela API ou TTL ≤ 60 s + auditoria | Média |
| CSRF desligado + cookie sem `SameSite` em endpoints irreversíveis | POST cross-site que emite documentos | `Idempotency-Key` (preflight), `Content-Type: application/json` obrigatório | Média (risco pré-existente, agravado) |
| Texto livre no PDF/HTML (descrição, logo) | XSS/SSRF no gerador, fuga de sigilo | Template estático, escape, sem recursos externos, descrição controlada (P-17/P-22) | Alta se HTML→PDF |
| Entidade JPA em `@RequestBody` de novo endpoint | Mass assignment (`id`, `tenantId`, `numero`) | DTO/`record`, `id` nunca do cliente | Alta |

## UX Pitfalls

| Pitfall | Impacto no utilizador | Melhor abordagem |
|---------|----------------------|------------------|
| Emissão irreversível sem pré-visualização | Erro de NIF só corrigível com NC + nova fatura | Diálogo com nome/NIF/valor/IVA/data e aviso de imutabilidade |
| Mensagens técnicas de falha de dados fiscais | Utilizador não sabe o que corrigir | "Falta o NIF do cliente X — corrija na ficha" com link |
| Documento simulado com aspecto de real | Falsa sensação de conformidade | Banner e marca de água `SIMULADO — SEM VALOR FISCAL` |
| Estado de comunicação ambíguo ("enviado") | Confusão entre SMTP enviado, comunicado, aceite | Estados separados por canal: emissão, comunicação DNRE, email |
| Botão apagar pagamento visível quando há fatura | Frustração, 409 | Esconder e explicar; backend recusa de qualquer forma |
| Tenant sem dados fiscais bloqueado sem explicação | Percepção de regressão | Faturação desligada por defeito + assistente de configuração |
| Guard `!permissions.isLoading` | Flash de "Acesso negado" | `permissions.isFetched` |
| Escritório sem acesso à sua fatura de subscrição | Pedidos de suporte | Decisão explícita de visibilidade (P-12) |

## "Looks Done But Isn't" Checklist

- [ ] **Numeração:** parece correcta num teste sequencial — verificar com N threads, rollback forçado e dois tenants (IT estilo `ParecerVersaoConcorrenciaIT`); confirmar que nenhum `MAX(`/`synchronized` gera `numero`.
- [ ] **Atomicidade:** emissão a funcionar — forçar falha a meio (série, saldo, fatura) e ver que não sobra `Pagamento` órfão nem saldo alterado; confirmar que o `catch` que só regista já não existe neste caminho.
- [ ] **Idempotência:** o mesmo `Idempotency-Key` duas vezes (sequencial e concorrente) devolve o mesmo documento e não cria segundo pagamento.
- [ ] **Isolamento:** 2 tenants + tenant de plataforma; cada endpoint (listar, ver, PDF, XML, NC, estado) devolve 404 cruzado; filtros por query são aplicados (lição v2.9).
- [ ] **Imutabilidade:** não existe rota/método/`save` que altere `Fatura`; repositório estreito e teste que fixa os métodos; PDF não aparece em `GET /documentos`.
- [ ] **Delete guards:** `deletePagamento`, `deleteCliente`, `deleteProcesso` devolvem 409 com fatura; fusão de clientes re-aponta referências sem tocar no snapshot.
- [ ] **Modo SIMULADO:** com `EFATURA_MODE=REAL` e simulador presente o arranque falha; PDF e UI marcam `SIMULADO`; estado nunca `ACEITE` com o simulador; séries simuladas separadas.
- [ ] **Fuso:** teste com `Clock.fixed` 23:30 e 00:30 CV; nenhum `LocalDate.now()` sem zona no pacote.
- [ ] **Arredondamento:** testes de invariantes com montantes de fronteira; PDF = XML = BD; `valorPago` com >2 casas rejeitado.
- [ ] **NC:** não excede o original (concorrência incluída), recusa NC sobre NC, `totalPago`/KPI/alerta/saldo coerentes.
- [ ] **XML/XSD:** XML gerado valida contra o XSD da versão fixada; resposta com `DOCTYPE` malicioso é rejeitada.
- [ ] **Email:** teste `\r\nBcc:`; timeouts configurados; envio fora da transacção; nenhum `log.*` com NIF/email/nome.
- [ ] **PDF:** corre na imagem Alpine de runtime; fontes embutidas; locale explícito; texto extraído = snapshot.
- [ ] **Migração:** linha no README + "Known execution status" no mesmo commit; arranque em `validate` com script e em BD nova com `update`; nenhum `NOT NULL` novo em tabela povoada.
- [ ] **Configuração:** variáveis novas em `.env.example`, os compose, `deploy.yml`; CI/Testcontainers arrancam sem SMTP real.
- [ ] **SAST/SCA:** `mvn spotbugs:check` limpo sem entradas novas em `spotbugs-exclude.xml`; `dependency-check:check` manual para as dependências novas.
- [ ] **RBAC:** matriz ADMIN/ADVOGADO/TECNICO/ASSISTENTE; `ASSISTENTE` sem acesso a faturas; frontend e backend concordam; `permissions.isFetched`.
- [ ] **Regressão:** registo de pagamento num tenant com `faturacao_activa=false` comporta-se exactamente como antes.
- [ ] **Plataforma:** emitente = tenant do `PLATAFORMA_ADMIN`, nunca por nome; adquirente com NIF/morada; escritório vê (ou não vê) a sua fatura por decisão explícita e testada.

## Recovery Strategies

| Pitfall | Custo | Passos de recuperação |
|---------|-------|----------------------|
| Número duplicado/lacuna detectado | ALTO | Parar emissão da série; inventariar buracos (`ORDER BY numero`); em modo SIMULADO marcar e abrir série nova; em REAL, tratar com a DNRE antes de reabrir; acrescentar o IT em falta |
| Fatura emitida com dados errados (NIF/valor) | MÉDIO | NC total + nova Fatura-Recibo (dois números); conta corrente coerente; auditar via `AuditLog` |
| Dupla emissão por retry | MÉDIO | NC da duplicada; implementar `Idempotency-Key` antes de reabrir |
| Simulados tomados por reais em produção | ALTO | Desactivar emissão; comunicar aos escritórios; reemitir na série real após go-live; corrigir modo, marca de água e estados |
| Fuga entre tenants (fatura/PDF) | ALTO | Desactivar endpoint; auditar `AuditLog`/logs de acesso; tratar como incidente de confidencialidade; adicionar IT do tipo afectado; registar em Key Decisions (como as correcções da v2.9) |
| Credencial/certificado exposto | ALTO | Revogar e rodar na origem (SMTP/ICP-CV); rever logs e mensagens; mover para o mecanismo de env/volume |
| Email para destinatário errado | ALTO (irreversível) | Registar incidente; contactar; rever validação e conteúdo mínimo do email |
| Boot a falhar em `validate` por script em falta | BAIXO | Executar o script do README (um de cada vez, `ON_ERROR_STOP=1`); não forçar `update` em produção |
| `CHECK` de enum recusa valor novo | BAIXO | Apagar/recriar o `CHECK` manualmente; passar a `varchar` sem `CHECK` |
| PDF difere do original | MÉDIO | Servir o artefacto guardado; se inexistente, regenerar do snapshot e marcar "Duplicado" |

## Pitfall-to-Phase Mapping

| Pitfall | Fase de prevenção | Verificação |
|---------|-------------------|-------------|
| P-01 Numeração | F-A (série) + F-B | IT concorrência/rollback/2 tenants |
| P-02 Atomicidade | F-B | IT com falha injectada; ausência do `catch` que só regista |
| P-03 Idempotência | F-B (+UX F-G) | Teste de duas chaves iguais, sequencial e concorrente |
| P-04 Isolamento tenant | F-A + F-G | IT 2 tenants + plataforma, 404 cruzado, repositório estreito com teste de métodos |
| P-05 Snapshot/apagar/fundir | F-B + F-G | Testes de delete 409 e de fusão; PDF após editar cliente |
| P-06 Imutabilidade | F-A + F-B/F-D | Teste de assinatura de repositório; sem setters; PDF fora de `/documentos` |
| P-07 Adaptador/SMTP fora da tx | F-D + F-E | Teste com latência/falha; rollback não deixa pendências |
| P-08 Simulador em produção | F-D + F-E + F-G | Arranque falha com `REAL`+simulador; marca de água; estados |
| P-09 Formato/versão/validade | F-D | Validação XSD no CI; vectores IUD; versão gravada |
| P-10 FR e data do pagamento | F-B (decisão em F-A) | Teste pagamento retroactivo |
| P-11 Dados fiscais/regressão | F-A | Tenant sem dados + flag desligada = comportamento antigo |
| P-12 Plataforma como emitente | F-A + F-F + F-G | Teste de emitente = tenant do principal; nome reservado recusado; visibilidade decidida |
| P-13 NC invariantes | F-C | IT NC concorrentes; NC sobre NC → 422 |
| P-14 Leituras de "pago" | F-C | Teste de coerência saldo/`totalPago`/KPI/alerta |
| P-15 Migrações/`ddl-auto` | F-A + F-G | Arranque `validate` + `update`; README no commit |
| P-16 Segredos/erros | F-D + F-E + F-G | Revisão ASVS V7/V14; grep de logs; sem env em falta no CI |
| P-17 SpotBugs/XXE/templates | F-D + F-E + F-G | `spotbugs:check` limpo; testes XXE e header injection |
| P-18 IVA/regime/retenção | F-A (decisão) + F-B | Casos do contabilista como testes |
| P-19 Arredondamento | F-B | Testes de invariantes parametrizados |
| P-20 Fuso/data | F-B | `Clock.fixed` 23:30/00:30 |
| P-21 Adquirente | F-B | Testes de NIF ausente/inválido/estrangeiro |
| P-22 PDF = snapshot | F-E | Texto extraído = snapshot; teste em Alpine |
| P-23 Email | F-E | Header injection, timeouts, outbox |
| P-24 RBAC | F-A + F-G | Matriz 4 papéis; `UserPrincipalCatalogoSyncTest` |
| P-25 Colisões séries/NIF | F-A + F-B | IT 2 tenants na mesma série lógica |
| P-26 Frontend | F-G | `isFetched`, confirmação, esconder apagar |
| P-27 Sobre-engenharia PT | F-B/F-D | Revisão de âmbito |
| P-28 Desempenho | F-B + F-E | Listagem paginada; PDF pós-commit |

## Questões para a fase de discussão (decisões que bloqueiam o desenho)

1. **Pagamentos retroactivos, parciais e adiantamentos** vs a regra "Fatura-Recibo só com data da fatura = data do pagamento": rejeitar, forçar hoje, ou fluxo Fatura + Recibo? (P-10)
2. **Semântica do montante e IVA/retenção**: `valorPago` é bruto ou líquido; regime por escritório; retenção na fonte e como aparece na fatura e na conta corrente. Exige contabilista certificado. (P-18)
3. **Cliente estrangeiro e "consumidor final"**: em âmbito ou recusados com mensagem? (P-21)
4. **Série e LED**: política de reinício anual, formato do número, mapeamento série→LED, e separação definitiva entre séries simuladas e séries reais. (P-01/P-08/P-09)
5. **Fatura de subscrição**: como o escritório a vê (só email, ou leitura cruzada com scope próprio). (P-12)
6. **Efeito da NC**: estorno no livro de pagamentos vs `valor_creditado` nas quatro leituras de "pago". (P-14)
7. **Activação por tenant** (`faturacao_activa`): quem liga, com que validação, e comportamento transitório. (P-11)
8. **Política da descrição da linha** (sigilo profissional) e logótipo no PDF. (P-22)
9. **Prazo de conservação e RGPD/Lei 133/V/2001** (apagar vs conservar). (P-05)
10. **Custódia de certificados por escritório** (impacta a interface do adaptador desde já, mesmo com ligação real fora de âmbito). (P-16)

## Lacunas a verificar contra fonte primária (antes de F-D)

- Manual Técnico da Fatura Eletrónica v11 (ou mais recente) e XSD em `efatura.cv`: composição exacta do IUD, numeração dos tipos de documento, regra de numeração (por NIF/LED/tipo/ano?), tolerância de totais, campos de retenção e de pagamento (lista de códigos de modalidade), código de motivo de não liquidação, campos obrigatórios do adquirente, requisitos de QR/IUD impressos. Esta sessão não conseguiu aceder (egress bloqueado).
- Texto do Decreto-Lei n.º 79/2020 e Despacho n.º 43/2022: conservação/arquivo (prazo), coimas, requisito de software aprovado, data de publicação (12 de novembro vs 28 de dezembro).
- Regras CV para identificação do adquirente (NIF genérico, limiares) e regime de pequenos contribuintes.
- Código do IVA de CV e Código do IRPS: taxa normal, isenções, retenção na fonte sobre honorários de advogados.
- Estatuto da Ordem dos Advogados de CV: dever de sigilo e descrição de serviços em faturas.
- Comportamento de `ddl-auto: update` do Hibernate 6.6 com `@Enumerated(STRING)` e `@Check` (verificar o DDL gerado no primeiro IT).
- Fontes/AWT na imagem `eclipse-temurin:23-jre-alpine` com a biblioteca de PDF escolhida.

## Sources

**Codebase (HIGH):** `CLAUDE.md`; `.planning/PROJECT.md` (Key Decisions: v2.9 filtros ignorados, `Decisao/Facto/Testemunha` com ids adivinháveis, `numero_cliente`, v2.16 Pitfall 1, v2.17 papéis snapshot); `.planning/milestones/v2.14-research/PITFALLS.md`; `backend/migrations/README.md`; `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`:278-286`, `:617-630`, `:834-956`, `:1246-1255`, `:2233`, `:2917-2981`, `:3039-3186`, `:3247-3263`); `models/{Honorario,Pagamento,ContaCorrente,Tenant,Cliente,AuditLog}.java`; `repositories/{ParecerSolicitacaoRepository,AuditLogRepository,ClienteRepository,TenantRepository}.java`; `services/SetupService.java`; `controllers/{PlatformAdminController,AuthController}.java`; `config/{SecurityConfig,UserPrincipal,GlobalExceptionHandler}.java`; `jobs/AlertasDiariosJob.java`; `seed/DatabaseSeeder.java`; `application.yml`, `application-prod.yml`; `backend/Dockerfile`, `docker-compose.yml`, `.github/workflows/deploy.yml`, `spotbugs-exclude.xml`, `pom.xml`; `.planning/config.json`; `web/src/{hooks/use-financeiro.ts,schemas/financeiro.ts,lib/api.ts,lib/permissions.ts,app/(dashboard)/financeiro/[id]/page.tsx}`.

**Regulatório CV (MEDIUM/LOW: resumos de pesquisa, primárias bloqueadas ao fetch):**
- [Manual Técnico v7 (efatura.cv)](https://efatura.cv/assets/files/manual-tecnico-v7-216ba5a0643ea57e50cfbbf26b47e746.pdf) e [Manual Técnico v10 (saft.tst.efatura.cv)](https://www.saft.tst.efatura.cv/assets/files/manual-tecnico-da-fatura-eletronica-v10.0-81ac76da0d05ec36abdb626087cda762.pdf): existência de versões sucessivas; mudanças de IUD/LED/Luhn/tipos.
- [Documentos Fiscais (efatura.cv)](https://efatura.cv/docs/manual/documentos-fiscais/) e [Modelo Conceitual](https://efatura.cv/docs/manual/modelo-conceitual/): tipos de DFE, LED por série, sequencialidade (via resumos).
- [Regras de emissão de Faturas Eletrónicas, WISEDAT](https://www.wisedat.pt/regras-de-emissao-de-faturas-cabo-verde/) e [Comunicação de DFE, WISEDAT](https://www.wisedat.pt/kb/emissao-dfe-cabo-verde/): FRE só com data da fatura = data do pagamento; prazo de emissão; LED; anulação.
- [Miranda Advogados — Regime Jurídico da Fatura Eletrónica](https://www.mirandalawfirm.com/pt/conhecimento-media/publications/alerts/aprovado-regime-juridico-da-fatura-eletronica-e-dos-documentos-eletronicos-fiscalmente-relevantes) e [VPQ Advogados — Requisitos para processamento de faturas eletrónicas (Despacho 43/2022)](https://www.vpqadvogados.com/xms/files/RECURSOS/Newsletters/Requisitos_para_processamento_de_faturas_eletronicas_e_de_documentos_fiscalmente_relevantes_-_Legal_Alert_VPQ_-PT-.pdf): DL 79/2020, XML, assinatura ICP-CV, IUD.
- [Adesão à Plataforma Eletrónica (efatura.cv)](https://efatura.cv/docs/guides/adesao-pe/): certificados ICP-CV (selo electrónico e SSL-EV), LED por série (via resumo).
- [Nota Técnica INOVE — SAF-T CV](https://inove.cv/nota-tecnica-faturacao-eletronica-e-standard-audit-file-for-tax-purposes-cabo-verde-saft-cv-tudo-o-que-deve-saber/): SAF-T(CV) como obrigação separada.
- [Cegid Vendus CV — motivos de isenção](https://www.vendus.cv/blog/isencao-de-iva/), [Código do IVA CV (Lobo Carmona)](https://lobocarmona.com/storage/74/CIVA_Cabo-Verde-12.01.2022.pdf), [Lei n.º 78/VIII/2014 IRPS](https://www.ministeriopublico.cv/index.php/ministerio-publico/legislacao/category/13-ministerio-publico-na-jurisdicao-fiscal-e-aduaneira?download=229:codigo-de-irps): taxa normal e retenção (LOW; resumos misturaram CV e PT).
- [CNPD CV — Lei 41/VIII/2013](https://www.cnpd.cv/wp-content/uploads/2025/03/Lei-n-41_VIII_2013-regime-juridico-geral-de-proteccao-de-dados-pessoais-das-pessoas-singulares.pdf) e [Lei 121/IX/2021](https://www.cnpd.cv/wp-content/uploads/2025/03/Lei-121.IX_.2021-alteracao-Lei-41.VIII_.2013.pdf): protecção de dados.

**Analogia Portugal (MEDIUM como engenharia, não é regra CV):** [Portaria n.º 195/2020 (ATCUD/QR)](https://info.portaldasfinancas.gov.pt/pt/informacao_fiscal/legislacao/diplomas_legislativos/Documents/Portaria_195_2020.pdf), [FAQ AT — Séries/ATCUD](https://info.portaldasfinancas.gov.pt/pt/apoio_contribuinte/questoes_frequentes/Pages/faqs-00883.aspx).

**Ferramentas e plataforma (MEDIUM):** [FindSecBugs — padrões](https://find-sec-bugs.github.io/bugs.htm) (`XXE_*`, `SMTP_HEADER_INJECTION`, `TEMPLATE_INJECTION_*`, `SPEL_INJECTION`, via pesquisa); [Hibernate 6 e CHECK de enums não actualizado por `update`](https://adeogooladipo.medium.com/hibernate-6-deep-dive-handling-enums-in-postgresql-398fc4b89608); [SSRF/XXE em geradores de PDF](https://securityboulevard.com/2019/05/ssrf-and-xxe-vulnerabilities-in-pdfreactor/).

---
*Pitfalls research for: faturação eletrónica (eFatura CV) em plataforma jurídica multi-tenant (LexCV v3.0)*
*Researched: 2026-10-04*
