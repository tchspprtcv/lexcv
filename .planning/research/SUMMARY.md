# Project Research Summary

**Project:** LexCV — marco v3.0 Faturação Eletrónica (eFatura CV)
**Domain:** Emissão de documentos fiscais eletrónicos (Fatura-Recibo e Nota de Crédito) por um SaaS jurídico multi-tenant, com adaptador pronto para o eFatura da DNRE (Cabo Verde)
**Researched:** 2026-10-04
**Confidence:** MEDIUM no global. ALTA no que assenta no código do repositório e nas bibliotecas; MEDIUM no formato eFatura (o XSD oficial foi lido em cópias públicas, não no portal); MEDIUM-LOW nas regras fiscais materiais.

Documentos de origem (ler para o detalhe): `STACK.md`, `FEATURES.md`, `ARCHITECTURE.md`, `PITFALLS.md` (mesma pasta).

---

## Aviso prévio: fontes primárias bloqueadas (ler primeiro)

Os quatro investigadores ficaram sem acesso a `efatura.cv` e subdomínios (`services.`, `iam.`, `middleware.`, `saft.tst.`, `dev.`), a `mf.gov.cv`, a `boe.incv.cv` e a vários sites de fornecedores: a política de saída do ambiente devolve `EGRESS_BLOCKED` ou 403. A síntese repetiu o teste a `https://efatura.cv/docs/xsd` e a `https://services.efatura.cv/api-list/` e obteve `CONNECT tunnel failed, response 403` (os restantes domínios não foram retestados). Não se tentou contornar.

O que foi realmente lido:

| Material | Como foi lido | Confiança |
|----------|---------------|-----------|
| Pacote XSD oficial da DNRE (versão técnica 2024-05-27, 20 ficheiros), XMLs de exemplo, "Read Me.txt", "XML Fields Map.txt" | Diretamente, em cópias públicas (`Kowts/efatura-cv-php`, `kriolos/kriolos-efatura`, duas cópias independentes e idênticas) | ALTA para o conteúdo do pacote; MEDIUM quanto a ser a versão vigente |
| Páginas `efatura.cv/docs/...` e Manuais Técnicos v7/v10/v11 | Só excertos devolvidos por motores de pesquisa; nenhum PDF aberto | MEDIUM |
| Decreto-Lei n.º 79/2020, Portarias 74/2020 e 16/2022, Despacho 43/2022, Código do IVA, Código do IRPS, Lei do REMPE | Só via fornecedores homologados, escritórios e consultoras (Wisedat, PayTraq, Odoo, Edicom, Miranda, VPQ, PwC, consultoria.cv, OACV); nenhum texto legal lido | MEDIUM no melhor caso; LOW em artigos e valores concretos |
| Código do repositório | Lido diretamente (ficheiro e linha) | ALTA |
| Bibliotecas | Metadados do Maven Central, BOM do Spring Boot 3.4.1 e 10 experiências executadas em JDK 21 | ALTA |

Consequência para o roadmap: nenhum REQ-ID fiscal fino deve ser fechado sem a confirmação do contabilista, e a secção "O que verificar contra fontes primárias" (mais abaixo) é um pré-requisito explícito de fases concretas, não uma nota de rodapé.

## Decisões fixadas pelo utilizador (não reabertas)

Dois emitentes (escritório para cliente, em pagamentos de honorários; plataforma LexCV para escritório, em pagamentos de subscrição registados manualmente pelo `PLATAFORMA_ADMIN`). Exatamente uma Fatura-Recibo por pagamento, emitida na mesma operação. Correção só por Nota de Crédito, documentos imutáveis, pagamento faturado não apagável. "Pronto para integração" significa formato exato do eFatura, interface de adaptador, implementação SIMULADA e estado de comunicação por documento; ligação real fora de âmbito. PDF no servidor e email ao cliente (primeiro canal SMTP). Pagamentos existentes não são faturados retroativamente. IVA e retenção ficaram para a pesquisa (resolvido abaixo).

---

## Executive Summary

A eFatura de Cabo Verde é um sistema de autorização em tempo real: o documento (DFE) é um XML (`urn:cv:efatura:xsd:v1.0`) assinado em XAdES-BES com certificado ICP-CV do emitente, enviado por REST e autorizado de forma síncrona pela DNRE. A validade jurídica vem dessa assinatura e dessa autorização; um PDF enviado por email, segundo a FAQ oficial (lida só em excerto), não é fatura eletrónica. O XSD oficial deixa a assinatura opcional (`minOccurs="0"`), o que torna a v3.0 viável: um XML sem assinatura valida contra o esquema oficial (verificado), com `RepositoryCode=3` (Teste) dentro do IUD. O LexCV v3.0 constrói tudo o que está a montante da ligação: motor de emissão transacional (uma Fatura-Recibo por pagamento, numeração sem lacunas, snapshot imutável, Nota de Crédito como única correção), modelo JAXB gerado do XSD oficial, adaptador `EfaturaGateway` com implementação simulada, PDF no servidor e email SMTP, tudo parametrizado por emitente para servir escritórios e plataforma com o mesmo núcleo.

A abordagem recomendada são sete fases (133 a 139) com um núcleo de emissão único que recebe o `tenantId` do emitente por parâmetro, outbox por linha de estado (`SKIP LOCKED`) para comunicação e email fora da transação, e imutabilidade estrutural (`@Immutable` mais repositório estreitado). Resolvemos os conflitos entre investigadores a favor de menos superfície nova: reutilizar `financeiro:*` em vez de um scope `faturas:*`; modelar a Nota de Crédito como `Pagamento` negativo (estorno) para que as quatro leituras de "pago" fiquem corretas sem as tocar; SMTP opcional; modo `EFATURA_MODE` ao nível do deployment (não por tenant) com default `SIMULADO`; nenhum armazenamento de segredos na v3.0. Regras fiscais (resposta à questão adiada): IVA normal 15%; advogados excluídos do REMPE e sem isenção específica encontrada; `valorPago` tratado como total com IVA incluído; retenção na fonte como entrada manual por pagamento, com sugestão de 20% sobre a base sem IVA. Os níveis de confiança são MEDIUM a LOW, pelo que o contabilista tem de validar antes da fase 134.

O risco n.º 1 do marco não é técnico. Documentos "Fatura-Recibo" simulados, enviados por email a clientes reais, não têm validade fiscal, e os escritórios continuam obrigados (desde 02-jun-2022, categoria B com contabilidade organizada) a emitir faturas válidas no seu software homologado. A mitigação é estrutural (IUD de teste, séries `SIM`, estado `ACEITE_SIMULADO` distinto de `AUTORIZADO` com CHECK na BD, marca visível no PDF e no email, sem QR para o portal real) e de lançamento (envio automático de email desligado por omissão por tenant até decisão do utilizador e confirmação do contabilista). Riscos de engenharia, por ordem: lacunas ou duplicados na numeração, emissão não atómica (o `createPagamento` atual engole falhas de saldo e não é transacional), fuga de isolamento (`Honorario` e `Pagamento` não têm `tenant_id`), e instalações em `ddl-auto=validate` que não arrancam sem scripts manuais. A passagem a ligação real não será só configuração: exige homologação do LexCV como software, credenciais do transmissor e uma decisão sobre custódia de certificados por escritório.

---

## Key Findings

### Recommended Stack

Só o que é novo face a `backend/pom.xml`. Todas as escolhas são Jakarta EE 10 e compatíveis com o Spring Boot 3.4.1 (o BOM gere JAXB, Angus Mail e Thymeleaf; não gere openhtmltopdf, zxing, greenmail nem xades4j). Detalhe e experiências em `STACK.md` §3, §5, §11.

**Core technologies:**
- `jakarta.xml.bind-api` 4.0.2 e `org.glassfish.jaxb:jaxb-runtime` 4.0.5 (geridas pelo BOM): API e runtime JAXB; hoje a API só chega em `runtime` via Hibernate e o código gerado precisa dela em `compile`.
- `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.16: gera 137 classes a partir dos XSD oficiais vendorizados em `src/main/resources/xsd/efatura`. Não definir `generatePackage` global (colide `ObjectFactory` entre XAdES 1.3.2 e 1.4.1; reproduzido). Validar `mvn generate-sources` em JDK 23 (o Dockerfile usa `maven:3.9-eclipse-temurin-23`; a experiência foi em JDK 21).
- `io.github.openhtmltopdf:openhtmltopdf-pdfbox` 1.1.87 mais `spring-boot-starter-thymeleaf`: HTML/CSS para PDF puro Java, sem binários nativos, LGPL-2.1+. Excluir `commons-logging`; embutir fontes TTF (a imagem de runtime é Alpine).
- `com.google.zxing:core` 3.5.4: QR (só `core`, não `javase`).
- `spring-boot-starter-mail`: SMTP, com `starttls.required=true` e os três timeouts explícitos (o default do Angus Mail é infinito).
- `com.icegreen:greenmail-junit5` 2.1.14 (teste): SMTP embebido.
- JDK: `SecureRandom` (código aleatório do IUD, evita `PREDICTABLE_RANDOM`), `MessageDigest` SHA-256 (impressão digital do XML), `javax.xml.validation` com `LSResourceResolver` de classpath (o carregamento `file:` falha no `java -jar`; verificado).

**Não adicionar na v3.0 (atrás de `AssinadorDfe` e `EfaturaGateway`):** `xades4j` 2.4.1 (já pré-validado em spike: assina, valida no XSD e é verificado pelo JDK; rever a licença LGPL-3.0), cliente HTTP (Spring `RestClient` quando houver ligação), cliente OAuth2 próprio.

**Evitar:** iText (AGPL-3.0), `com.openhtmltopdf:*` (grupo abandonado em 2021), JasperReports, Chrome headless, `zxing:javase`, `javax.xml.bind`/JAXB 2.x, SDKs de terceiros como dependência, XML por concatenação de strings.

### Expected Features

Detalhe em `FEATURES.md` §6 a §9. O MVP é "tudo ou nada" porque a Nota de Crédito sem efeitos financeiros deixa o sistema incoerente.

**Must have (table stakes):**
- Dados fiscais do emitente (escritório e plataforma) e gate de pré-requisitos antes de emitir (TS-01, TS-02).
- Emissão atómica pagamento mais Fatura-Recibo mais número mais conta corrente na mesma transação; séries e numeração por emitente sem lacunas (TS-03, TS-04).
- Cálculo de IVA parametrizável, retenção opcional por pagamento, snapshot imutável, bloqueio de apagar pagamento faturado (TS-05, TS-06, TS-07, TS-08).
- Nota de Crédito total e parcial com os seus efeitos em conta corrente, `totalPago`, `HONORARIO_ATRASADO` e KPI (TS-09, TS-10).
- IUD de 45 caracteres, LED, adaptador simulado, estado de comunicação por documento, marcação inequívoca "simulado" (TS-11, TS-12, TS-13). O TS-13 é requisito de produto, não detalhe de UI.
- PDF no servidor e email assíncrono pós-commit com estado e reenvio (TS-14, TS-15).
- Subscrição da plataforma (TS-16), listagem/detalhe/download (TS-17), conservação e auditoria (TS-18), mapeamento de `Pagamento.metodo` para os meios do eFatura (TS-19).
- Pré-visualização e confirmação antes de emitir (DF-01; classificada P1 porque é o único antídoto barato contra a imutabilidade).

**Should have (v3.x):** XML em anexo (DF-02), arquivo do PDF no dossiê do cliente (DF-03), alertas in-app de email falhado (DF-05), verificador de lacunas (DF-06), resumo mensal de IVA e retenções em CSV (DF-04).

**Defer (marco de ligação real ou depois):** assinatura XAdES e certificados, autorização real, contingência, cancelamento direto de DFE, homologação do LexCV; Fatura/Recibo/Talão/Nota de Débito; clientes não residentes e consumidor final; despesas e provisões na fatura; manifestação do destinatário e autofaturação; SAF-T; cobrança online; motor automático de retenção/isenção.

### Architecture Approach

Núcleo único `FaturacaoService` que recebe `tenantId` (emitente) e `autorId` por parâmetro, sem ler o `SecurityContext`, chamado por três orquestradores `@Transactional` (`PagamentoFaturadoService`, `NotaCreditoService`, `SubscricaoFaturadaService`) e usado também pelo job. Uma só transação PostgreSQL, sem I/O externo. PDF, XML, comunicação e email correm depois do commit, a partir de linhas de estado inseridas na mesma transação (outbox sem dual-write). Zero `ALTER` a tabelas existentes: a ligação ao pagamento vive em `t_documento_fiscal.pagamento_id`. Detalhe em `ARCHITECTURE.md` §1 a §7.

**Major components:**
1. `ConfiguracaoFiscal` e `ParametroFiscal` — dados fiscais do emitente (tabela nova, não estender `t_tenant`) e taxas com data de vigência (IVA, retenção sugerida).
2. `SerieFiscal` e `NumeracaoService` — contador por `(emitente, tipo, série, ano, ambiente)` com `SELECT ... FOR UPDATE`; `UNIQUE(tenant_id, serie_id, numero)` como rede de segurança; ordem fixa de locks conta corrente depois série.
3. `DocumentoFiscal` e `DocumentoFiscalLinha` — `@Immutable`, snapshot de emitente e adquirente, repositório `Repository<...>` sem `delete*`; todo o estado mutável vive em satélites.
4. `ComunicacaoFiscal`, `EntregaDocumento`, `ArtefactoDocumentoFiscal` — estado de comunicação, de email e artefactos (PDF/XML no MinIO sob `<tenantId>/<documentoFiscalId>/...`).
5. `EfaturaGateway` mais `SimuladoEfaturaGateway`, `DfeXmlBuilder`, `DfeIdentificadorService` — formato, IUD e adaptador; função única que mapeia `(resultado, ambiente)` para estado.
6. `FiscalOutboxJob` — poller `@Scheduled` com `SKIP LOCKED`, lease e backoff; não salta tenants suspensos.
7. `PagamentoSubscricao` e `PlatformFaturacaoController` — plataforma como emitente, sob `/api/v1/platform/**` com `hasRole('PLATAFORMA_ADMIN')` ao nível da classe.

**Cortes desta síntese face a `ARCHITECTURE.md`:** `CredencialFiscal` (tabela de segredos cifrados) e utilitário AES-GCM saem da v3.0 (ver C13); `TentativaComunicacao` sai (basta `tentativas`, `ultimo_erro_*` e `ambiente` em `ComunicacaoFiscal`; reintroduzir no marco de homologação).

### Critical Pitfalls

Lista completa de 28 em `PITFALLS.md`. As seis que decidem a ordem das fases:

1. **Numeração com lacunas ou duplicados (P-01).** Não usar `SEQUENCE` (não faz rollback) nem `MAX+1` em `synchronized` (só protege uma JVM). Linha-contador bloqueada na mesma transação do documento, série do ano criada com `INSERT ... ON CONFLICT DO NOTHING`, `lock_timeout` curto, IT de concorrência com Testcontainers logo na fase 133.
2. **Emissão não atómica (P-02).** `createPagamento` (`ResourceController.java:3039-3081`) não é `@Transactional` e engole `DataAccessException` do saldo. Mover para serviço transacional e remover o `catch` neste caminho; saldo com lock, não leitura-modificação-gravação.
3. **Simulado que parece real (P-08, P-09).** O IUD real é determinístico e um simulador "bem feito" é indistinguível. Defesa em camadas (ver secção de risco legal); `@Profile("!prod")` não serve porque o perfil `prod` está morto no repositório.
4. **Fuga de isolamento entre tenants (P-04).** `Honorario` e `Pagamento` não têm `tenant_id` e têm ids inteiros adivinháveis; `deleteProcesso` deixa-os órfãos. Entidades fiscais com `tenant_id` do emitente preenchido pelo servidor, repositórios estreitos sem `findById` cru, IT com dois tenants mais o tenant de plataforma.
5. **I/O dentro da transação (P-07).** Rede segura a ligação e o lock da série. O simulador instantâneo esconde o problema; tem de obedecer ao mesmo contrato assíncrono e, em teste, injetar latência e falhas.
6. **Migrações e `ddl-auto` (P-15).** Sem Flyway, instalações em `validate` falham o arranque sem o script manual; instalações novas saltam scripts e perdem invariantes. Tabelas novas (nunca `NOT NULL` em tabela povoada), script idempotente e linha no `backend/migrations/README.md` no mesmo commit, arranque testado em `validate` e em `update`.

---

## Decisões de síntese: onde os investigadores discordam

Resumo, seguido do detalhe de cada conflito. C1 a C6 são os indicados no pedido; C7 a C13 foram encontrados na leitura cruzada.

| # | Tema | Resolução recomendada |
|---|------|-----------------------|
| C1 | RBAC: `faturas:*` vs `financeiro:*` | Reutilizar `financeiro:*` na v3.0 |
| C2 | Nota de Crédito: estorno negativo vs crédito separado | Estorno como `Pagamento` negativo (opção A de ARCHITECTURE) |
| C3 | SMTP: opcional vs obrigatório sem default | Opcional, com `MAIL_ENABLED=false` por omissão e validação ao ativar |
| C4 | Gateway por tenant vs `EFATURA_MODE` global | Global, default `SIMULADO`, só `SIMULADO` aceite na v3.0 |
| C5 | Retenção 20% vs 15% | Taxa parametrizada, sugestão 20%, editável por pagamento; 15% tratado como não confirmado |
| C6 | Host do QR | `services.efatura.cv` como hipótese, configurável; sem QR no PDF simulado |
| C7 | Gate duro vs flag `faturacao_ativa` | Flag por tenant, default `false`, ativação unidirecional validada |
| C8 | Pagamento retroativo vs data da fatura | Rejeitar (422) data diferente de hoje em Cabo Verde |
| C9 | Série e LED: formato e nulidade | Série 1-20 conforme XSD; LED sintético reservado em SIMULADO |
| C10 | PDF: HTML vs desenho direto | openhtmltopdf endurecido, com testes de SSRF/XXE |
| C11 | Idempotência por header vs corpo; TTL do URL | Campo no corpo; URL pré-assinado de TTL curto mais auditoria |
| C12 | Fase da plataforma: depende só de A+B ou também de D+E | Depois de D+E; núcleo testado com dois emitentes desde a fase B |
| C13 | Segredos: tabela cifrada vs env/volume | Nenhum armazenamento na v3.0; só a interface |

### C1. Scope RBAC novo (`faturas:*`) vs reutilizar `financeiro:*`

- **ARCHITECTURE** (§8): scope novo `faturas:view/create/manage`, mais um passo convergente no arranque que acrescente as permissões ao `TenantRole` ADMIN de cada escritório existente. **FEATURES** (TS-17) deixa as duas hipóteses em aberto. **PITFALLS** (P-24): reutilizar `financeiro:*`, zero migração de RBAC.
- Verificado na síntese (`DatabaseSeeder.java:442-476`): por omissão só o ADMIN tem `financeiro:edit` e `financeiro:manage`; ADVOGADO e TECNICO têm só `financeiro:view`; ASSISTENTE não tem nenhuma. Reutilizar dá exatamente a distribuição desejada (ASSISTENTE de fora, ADMIN emite e credita, restantes leem) sem passo de migração.
- O próprio ARCHITECTURE identifica o scope novo como a primeira extensão do catálogo depois de existirem papéis de escritório (papéis são snapshots desde a v2.17, e o papel ADMIN pode ter sido renomeado na Phase 127). Isso obriga a um runner novo, à sincronização em quatro sítios (`CATALOGO_PERMISSOES`, `UserPrincipal.create`, `KNOWN_SCOPES`, `@PreAuthorize`) e a uma decisão sobre moldes, tudo num marco que já introduz SMTP, PDF e JAXB.
- **Resolução:** `financeiro:view` lê faturas, PDF, XML e estados; `financeiro:edit` emite (a Fatura-Recibo é consequência de `POST /pagamentos`), reenvia email e reprocessa comunicação; `financeiro:manage` emite Nota de Crédito, edita configuração fiscal e séries e ativa a faturação. Plataforma: `hasRole('PLATAFORMA_ADMIN')` ao nível da classe, zero permissões `scope:action`. Rever o rótulo de `financeiro:manage` ("Eliminar Lançamentos Financeiros", `DatabaseSeeder.java:373`), que deixa de descrever o que a permissão faz.
- **Risco residual:** a edição da configuração fiscal e a emissão de NC partilham o scope de apagar lançamentos; papéis personalizados com `manage` ganham esses poderes. Aceite na v3.0. Um `faturas:*` próprio justifica-se no marco de ligação real (credenciais, certificados) e então com migração convergente.

### C2. Nota de Crédito: `Pagamento` negativo (estorno) vs crédito separado

- **ARCHITECTURE** (§3.6): a NC cria um `Pagamento` negativo no mesmo honorário, identificado por `DocumentoFiscal.pagamento_estorno_id`; as quatro leituras de "pago" (`@Formula totalPago`, saldo de `ContaCorrente`, `calculateMensalReceived`, alerta `HONORARIO_ATRASADO`) ficam corretas sem alteração. **PITFALLS** (P-14): preferir um crédito separado (`valor_creditado`) subtraído nas leituras, via "um único método de serviço". **FEATURES** (TS-10) descreve os quatro consumidores a atualizar, sem decidir.
- Verificado na síntese: `Honorario.totalPago` é `@Formula` SQL (`Honorario.java:34`), logo não pode chamar um método Java; a opção B obriga a reescrever a fórmula com subconsulta à tabela fiscal e a duplicar a lógica no KPI (`ResourceController.java:3247-3263`) e no job (`AlertasDiariosJob.java:276-277`), a classe de bug "implementações divergentes" que o projeto já pagou. `Pagamento.valorPago` não tem CHECK na BD; a guarda `> 0` está só no controller (`ResourceController.java:3053`), que o fluxo de NC não atravessa. Não é preciso afrouxar nenhuma validação.
- **Resolução: opção A.** Condições: (1) o estorno só é criado pelo `NotaCreditoService`, nunca por `POST /pagamentos`; (2) nunca gera Fatura-Recibo (`UNIQUE(pagamento_estorno_id)`); (3) não é apagável; (4) "faturado" significa existir documento com `pagamento_id = id` **ou** `pagamento_estorno_id = id`, para que o estorno não apareça como "legado sem fatura"; (5) o DTO expõe `estorno` e a UI mostra "Estorno (NC n.º ...)"; (6) teste de coerência: após NC total, saldo, `totalPago`, contribuição ao KPI e condição do alerta concordam; (7) corrigir `calculateMensalReceived` (compara só o mês sem o ano, usa `LocalDate.now()` sem zona, sem null-check) na mesma fase, porque os estornos tornam o defeito visível.
- **Semântica a documentar:** o KPI do mês conta o estorno no mês de emissão da NC (visão de caixa). **Risco residual:** qualquer leitor novo de `t_pagamento` tem de saber que existem linhas negativas; registar em Key Decisions do PROJECT.md.

### C3. SMTP: variáveis opcionais vs obrigatórias sem default

- **STACK** (§5) e **FEATURES** (TS-15): `${MAIL_HOST}` etc. sem default, seguindo CLAUDE.md. **ARCHITECTURE** (§2.1, §12): opcionais com default vazio. **PITFALLS** (P-16): `EMAIL_ENABLED` explícito e variáveis condicionais a ele.
- Verificado na síntese: `application.yml` já tem precedentes de default (`MINIO_PUBLIC_ENDPOINT:${MINIO_ENDPOINT}`, `MINIO_PRESIGNED_EXPIRY:3600`), logo "tudo obrigatório" não é absoluto. Não existe nenhuma anotação `@SpringBootTest`/`@WebMvcTest` real no projeto (só aparecem em comentários; há 7 ficheiros `@DataJpaTest`), portanto o CI de testes não parte; o que parte são as instalações existentes, porque cada variável tem de ser passada à mão em três compose e no `deploy.yml` (o bug de passthrough da v2.12).
- **Resolução:** `MAIL_ENABLED` (default `false`) e `MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` com default vazio. Com `MAIL_ENABLED=false` o `EmailSender` é o `NaoConfigurado` (entrega `NAO_CONFIGURADO`, nunca falha o pagamento). Com `MAIL_ENABLED=true` valida-se `host/port/from` no arranque (fail-fast): mantém o espírito da convenção onde ela faz sentido. STARTTLS obrigatório e três timeouts sempre definidos. Registar a exceção em CLAUDE.md e Key Decisions.
- **A verificar no primeiro plano da fase 137:** a auto-configuração do `JavaMailSender` ativa-se pela presença de `spring.mail.host`, e uma string vazia pode contar como presente (inferência do comportamento do Spring Boot, não testada). Escolher a implementação por `MAIL_ENABLED` num bean próprio, não pela auto-configuração. Checkpoint humano antes de SMTP real (`always_confirm_external_services`).

### C4. Gateway por tenant vs `EFATURA_MODE` global

- **ARCHITECTURE** (§5.1): `ConfiguracaoFiscal.modoComunicacao` por tenant, com bean real atrás de `app.efatura.real.enabled` e resolver que falha fechado. **PITFALLS** (P-08): variável de ambiente `EFATURA_MODE=SIMULADO|REAL`, obrigatória sem default, validada no arranque.
- A alavanca que decide se um documento é legalmente real não deve estar numa linha de BD que um ADMIN de escritório com permissão de gestão possa alterar, e o perfil `prod` não existe como proteção. A dimensão por tenant só tem sentido quando existir implementação real (cada contribuinte tem NIF e certificado próprios), o que não é a v3.0.
- **Resolução:** `EFATURA_MODE` ao nível do deployment, validado no arranque. Na v3.0 só `SIMULADO` é aceite; qualquer outro valor (incluindo `REAL`) aborta o arranque com mensagem explícita, porque não há implementação real neste build. Default `SIMULADO`: é o estado seguro, e exigir a variável acrescentaria atrito de deploy sem ganho. Desvio documentado à convenção de CLAUDE.md. Mantêm-se as seis camadas de ARCHITECTURE §5.4 (tipo, CHECK de BD, séries por ambiente, apresentação, IUD com `RepositoryCode`, gateway real que recusa documentos de outro ambiente). O modo fica gravado em cada documento e em `ComunicacaoFiscal.ambiente`. O marco de ligação real acrescentará a ativação por emitente, em conjunção com o modo global (as duas condições têm de se verificar).

### C5. Retenção na fonte: 20% vs 15%

- **FEATURES** (§5.2): 20% (contabilidade organizada) e 4% (REMPE), concordando Vendus CV, Wisedat, consultoria.cv e caboverdeexpert, citando o Código do IRPS; **PwC** indica 15% para a categoria B. **PITFALLS** (P-18): valores não verificados, não devem entrar em código.
- O 15% não está resolvido. FEATURES §5.3 regista que a PwC dá 15% para serviços a não residentes, o que sugere que o 15% pode ser outra figura ou uma versão antiga; é inferência, não prova. O artigo do Código do IRPS citado ("47.º") também não foi confirmado. O 4% do REMPE não se aplica a advogados.
- **Resolução:** a taxa nunca fica em constante. `ParametroFiscal` (taxa, vigência) fornece a sugestão (20%); o utilizador ativa a retenção por pagamento (checkbox), edita a taxa e o valor, a base é o valor tributável sem IVA, e a pré-visualização (DF-01) mostra o resultado. O documento grava `taxa` e `valor_retencao` por linha. O que bloqueia é a validação do contabilista antes de fechar os REQ-IDs da fase 134; a escolha 20% vs 15% não bloqueia código porque a taxa é dado. Codificação no XML já confirmada (ALTA): imposto `IR` por linha, `WithholdingTaxTotalAmount` subtraído a `PayableAmount` (STACK §2.6; resolve o "elemento XML não verificado" de FEATURES).

### C6. Host do QR: `services.efatura.cv` vs `pe.efatura.cv`

- **STACK/FEATURES:** URL `https://services.efatura.cv/v1/dfe/view/{IUD}` (excerto do manual e Edicom). O SDK não oficial `Kowts/efatura-cv-php` usa por omissão `https://pe.efatura.cv/dfe/view`. Conflito não resolvido sem o manual v11.
- **Resolução:** hipótese de trabalho `services.efatura.cv` (excerto do manual mais um terceiro, contra um SDK não oficial), prefixo configurável (`EFATURA_QR_BASE_URL`, vazio na v3.0). Os três relatórios concordam que um QR para o host real num documento simulado aponta para um DFE inexistente e dá falsa conformidade. **O PDF simulado não leva QR para nenhum host `efatura.cv`**; mostra o IUD em texto e a legenda "SIMULAÇÃO". O gerador (ZXing) é construído e testado com os dois prefixos candidatos. A consulta pública só funciona durante 1 hora após a emissão (MEDIUM): a confirmar na verificação primária antes da ligação real.

### C7. Gate duro vs flag `faturacao_ativa` (conflito adicional)

- **ARCHITECTURE/FEATURES:** emissão bloqueada com `409 CONFIGURACAO_FISCAL_INCOMPLETA` até a configuração estar completa, uma "regressão de UX deliberada" para todos os tenants; FEATURES AF-05 recusa "registar pagamento sem fatura" como via de evasão. **PITFALLS** (P-11): flag `faturacao_ativa` por tenant, default `false`, com comportamento idêntico ao atual enquanto desligada.
- **Resolução:** flag por tenant, default `false`, ativação unidirecional feita por quem tem `financeiro:manage`, e que só é aceite com `ConfiguracaoFiscal` completa. Depois da ativação cada pagamento emite Fatura-Recibo sem contornos (preserva AF-05) e não é possível desligar após o primeiro documento. Mantém-se o gate de dados em cada emissão (cliente sem NIF válido, por exemplo) com `409` e CTA. O NIF do emitente fica imutável após o primeiro documento. Plataforma: sem flag, emissão bloqueada até haver dados fiscais da LexCV. Evita-se que o dia do deploy parta o registo de pagamentos em todos os escritórios (hoje nenhum tem NIF/morada/regime; `Tenant.nif` é anulável e o tenant "LexCV" tem NIF nulo).
- **Ressalva a confirmar com o utilizador (U4):** até um tenant ativar, os seus pagamentos não geram fatura. É a extensão da regra "não retroativo" ao período de transição; se "exatamente uma por pagamento" for incondicional, a alternativa é o gate duro.

### C8. Pagamento retroativo vs data da Fatura-Recibo (conflito adicional)

- **ARCHITECTURE** (§4.4): `data_emissao` do servidor, `data_recebimento` pode ser a data do formulário. **PITFALLS** (P-10): a Fatura-Recibo só é legítima quando a data da fatura coincide com a do pagamento (excerto Wisedat, MEDIUM) e deve rejeitar-se o caso contrário. **FEATURES** (§4.2): "pagamento antecipado: data = data da receção"; fatura até ao 5.º dia útil (MEDIUM-LOW).
- Numeração sequencial deve ser cronológica; um documento datado de há três dias a seguir a um de ontem quebra a série.
- **Resolução:** com a faturação ativa, `dataPagamento` omitida passa a hoje (fuso `Atlantic/Cape_Verde`, com `Clock` injetável) e uma data diferente de hoje é rejeitada com `422 DATA_PAGAMENTO_RETROATIVA` e mensagem clara. Relaxar mais tarde é barato; apertar depois de haver documentos emitidos não é. Pendente de U5 e A3.

### C9. Série e LED: formato e nulidade (conflito adicional)

- **ARCHITECTURE:** `codigo` da série "≤10 alfanuméricos" (fonte secundária, BAIXA-MÉDIA) e `led_codigo` NULL até haver ligação real. **STACK** (XSD oficial, ALTA): `Serie` 1 a 20 caracteres, padrão `[A-Za-z0-9]+([_-][A-Za-z0-9]+)*`; `LedCode` obrigatório no cabeçalho, 1 a 99999, e presente no IUD em 5 dígitos.
- **Resolução:** vale o XSD. Série até 20 caracteres (por exemplo `FR2026`, `NC2026`, e `SIM-FR-2026` em simulação). Em SIMULADO o `DfeXmlBuilder` precisa de um LED: usar um valor sintético reservado e declarado como constante, nunca reutilizável em real (o `RepositoryCode=3` já o isola). A fase 133 mantém `led_codigo` anulável na tabela; a fase 136 resolve o valor sintético.

### C10. PDF: HTML (openhtmltopdf) vs desenho direto

- **STACK:** openhtmltopdf mais Thymeleaf (experiência E5/E6: PDF válido, acentos e QR OK). **PITFALLS** (P-17): preferir desenho direto sem motor HTML por risco de SSRF/XXE com texto livre; admite "HTML escapado sem rede".
- **Resolução:** openhtmltopdf, sob as condições de PITFALLS: templates estáticos, `th:text` com escape, resolvedor de URI que nega tudo exceto `data:` e classpath, sem JavaScript, descrição de linha controlada (por omissão "Honorários por serviços jurídicos" com referência opcional ao n.º do processo, não o texto livre do honorário, por sigilo profissional), logótipo só como snapshot controlado ou omitido. Testes: `<img src="file:///etc/passwd">` na descrição não é carregado; o texto extraído do PDF coincide com o snapshot; smoke test na imagem Alpine de runtime; corrida manual de `dependency-check` para as dependências novas. OpenPDF 3.0.5 fica como alternativa (Java 21+, excluir `brotli4j`).

### C11. Idempotência (header vs corpo) e TTL do URL de download (conflito adicional)

- **ARCHITECTURE:** `chaveIdempotencia` no corpo (evita alterar a allowlist CORS). **PITFALLS:** header `Idempotency-Key`, com bónus de forçar preflight. Verificado: `SecurityConfig.java:91` não lista `Idempotency-Key`.
- **Resolução:** campo no corpo, gerado com `crypto.randomUUID()` ao abrir o diálogo, `UNIQUE(tenant_id, chave_idempotencia)`; chave igual com payload diferente dá `409 CHAVE_REUTILIZADA`. A defesa contra POST cross-site é o `Content-Type: application/json` já exigido por `@RequestBody`.
- **Download:** URL pré-assinado com TTL curto (60 s ou menos). Verificado: `StorageService.presignedDownloadUrl(String)` usa o TTL global de 3600 s; acrescentar sobrecarga com TTL explícito e evento em `AuditLog` (precedente `documento_download`). O email anexa o PDF guardado, nunca uma ligação.

### C12. Fase da plataforma: dependências

- **ARCHITECTURE:** a fase F depende de A, B, D, E. **PITFALLS:** F pode correr em paralelo a D/E, dependendo só de A e B. **Resolução:** depois de D e E (reutiliza o pipeline de PDF, comunicação e email), mas a fase B inclui um teste de serviço com dois emitentes (tenant escritório e tenant reservado) para validar cedo a parametrização do núcleo.

### C13. Segredos: tabela cifrada vs env/volume (conflito adicional)

- **ARCHITECTURE** (§7): construir já o seam: `t_credencial_fiscal` com AES-256-GCM e `FISCAL_SECRET_KEY`, sem endpoint de upload. **PITFALLS** (P-16): nunca BD nem MinIO, `CofreCredenciais` por env ou volume montado. **STACK:** `KeyStore` PKCS#12 do JDK; a custódia por escritório é "a decisão difícil".
- **Resolução:** a v3.0 não armazena segredos. Só a interface (`CredenciaisEfaturaProvider`, devolvendo "não configurado") e `AssinadorDfe` nulo. O modelo de armazenamento correto depende de Q3 (SaaS com custódia, Middleware da DNRE, ou Emissor Público), que ninguém respondeu, e cada tabela ou endpoint de segredos sem consumidor é superfície ASVS (nível 1, bloqueio em `high`). Decidir já seria decidir às cegas. A decisão é levada ao marco de ligação real.

---

## Regras fiscais consolidadas (resposta à questão adiada pelo utilizador)

Nenhuma conclusão é HIGH: não houve acesso a texto legal. A coluna "Quem confirma" indica o desbloqueio.

| Tema | Conclusão | Confiança | Quem confirma |
|------|-----------|-----------|---------------|
| Taxa normal de IVA | 15%, sem alteração nos OE 2025 e 2026 segundo resumos PwC | MEDIUM | Contabilista (inclui orçamento retificativo de 2026, não verificado) |
| IVA em serviços jurídicos | Tributados à taxa normal; nenhuma isenção específica encontrada (o art. 9.º cobre saúde, ensino, operações financeiras, etc.). "Não encontrei" não prova "não existe" | MEDIUM-LOW | Contabilista, com leitura do Código do IVA |
| Advogados no REMPE | Excluídos: o anexo ao art. 2.º da Lei 70/VIII/2014 exclui a secção M da CAE (atividades jurídicas); a OACV declara que os profissionais liberais não estão no REMPE | MEDIUM | Contabilista |
| Consequência | Escritórios e advogados em nome individual: IVA normal 15%, contabilidade organizada, e-fatura obrigatória desde 02-jun-2022 | MEDIUM | Contabilista |
| Isenção por pequeno volume fora do REMPE | Não identificada (a referência ao "art. 53.º" é contaminação portuguesa) | LOW | Contabilista |
| Códigos de isenção no XSD | Existem 21 `TaxExemptionReasonCode`; o 20 (REMPE) não se aplica por omissão a advogados | ALTA (existência) | n/a |
| Semântica do montante | `Pagamento.valorPago` = total recebido com IVA incluído: base = round(total/1,15; 2), IVA = total menos base. Mantém inalterados `valorTotal`, `totalPago` e a conta corrente. Decisão de produto | MEDIUM (recomendação) | Utilizador (U1) |
| Retenção: quem e quando | IRPS categoria B, por entidades com contabilidade organizada, sobre prestadores pessoas singulares; só se emitente é advogado em nome individual e pagador tem contabilidade organizada (tipicamente `Cliente.tipo = Empresa`). Sociedades de advogados (IRPC) e clientes Particulares tipicamente sem retenção | MEDIUM (regra), LOW-MEDIUM (sociedades) | Contabilista |
| Retenção: taxa | 20% (contabilidade organizada); 4% (REMPE, não aplicável); 15% (PwC) por resolver | MEDIUM / LOW | Contabilista (ver C5) |
| Retenção: base | Valor tributável sem IVA (prudência; com ou sem IVA não verificado) | LOW | Contabilista |
| Retenção: entrada | Manual por pagamento (checkbox, taxa editável, valor), nunca decisão automática; sugestão contextual só como DF-08 | MEDIUM (decisão de produto) | Utilizador |
| Retenção: codificação XML | Imposto `IR` por linha; `WithholdingTaxTotalAmount` subtraído a `PayableAmount` (desde 2022-02-19) | ALTA (XSD e "Read Me.txt") | n/a |
| Retenção de IVA | Sem evidência em Cabo Verde | LOW | Contabilista |
| Conta corrente com retenção | Creditada do total do documento (dinheiro mais imposto retido pago ao Estado em nome do cliente); a Fatura-Recibo mostra o valor líquido recebido | MEDIUM (recomendação) | Contabilista |
| Subscrição LexCV | IVA 15%; retenção só se o emitente for pessoa singular com contabilidade organizada (então o escritório retém 20%); se não residente, regime diferente (retenção de 15% em serviços a não residentes segundo PwC). A entidade jurídica da LexCV não está documentada | MEDIUM-LOW / LOW | Utilizador (U2) e contabilista |
| Arredondamento | `BigDecimal` escala 2, `HALF_UP`, resíduo para o IVA; o XML admite até 5 casas decimais; tolerância de reconciliação desconhecida | LOW | Manual v11 |
| Despesas e provisões (custas) | Ficam fora da base de IVA (código 6); fora de âmbito na v3.0, mas se os escritórios as registarem como `Pagamento` seriam faturadas como honorários | LOW | Utilizador (U8) e contabilista |

Exemplo ilustrativo, base a confirmar: pagamento de 120.000,00 CVE com IVA incluído e retenção de 20% sobre a base. Base 104.347,83; IVA 15.652,17; retenção 20.869,57; recebido em dinheiro 99.130,43; conta corrente creditada de 120.000,00.

Implementação: taxa de IVA e retenção sugerida em `ParametroFiscal` com data de vigência (nunca constante `0.15`); regime do emitente `NORMAL` por omissão, `ISENTO` com código de motivo disponível; REMPE só se o contabilista o confirmar; só clientes com NIF cabo-verdiano (sem consumidor final nem não residentes na v3.0).

---

## Risco de validade legal dos documentos simulados enviados a clientes reais

**Facto (MEDIUM, excertos oficiais):** a validade jurídica do DFE decorre da assinatura digital do emitente e da autorização da DNRE em tempo real; a FAQ afirma que documentos enviados em PDF por email não são fatura eletrónica. **Facto (MEDIUM):** o escritório (categoria B com contabilidade organizada) está obrigado a emitir faturas eletrónicas válidas desde 02-jun-2022, em software aprovado pela administração fiscal.

**Exposições:**
- O cliente pode tratar o PDF como fatura válida (comprovativo de despesa, dedução de IVA). Não é: sem assinatura nem autorização.
- Dois documentos para o mesmo pagamento (o simulado do LexCV e o válido do software homologado), ou o escritório omitir o válido por julgar que o LexCV já emitiu (inferência).
- A própria LexCV, como emitente da subscrição, continua a precisar de fatura válida; o simulado não a dispensa (inferência; entidade jurídica desconhecida).
- Regime sancionatório do DL 79/2020 para emissão de documentos não conformes e requisito de software aprovado: não verificados.
- Um email enviado não se recolhe (PITFALLS: recuperação de custo ALTO).

**Mitigações a tratar como requisitos de produto (inegociáveis):**
1. `RepositoryCode=3` (Teste) no IUD e `Id` do XML; nunca um IUD de produção.
2. Séries `SIM-...` separadas, nunca reutilizadas em real (o LED controla a sequência; um documento simulado na série real deixaria um buraco no arranque).
3. Estado `ACEITE_SIMULADO`, distinto de `AUTORIZADO`, com `CHECK (estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO')` e uma única função de mapeamento; `codigo_autorizacao` só existe em produção.
4. PDF com marca visível ("SIMULAÇÃO, sem validade fiscal, não comunicado à DNRE"), título "Fatura-Recibo (simulada)" e sem QR para `efatura.cv`.
5. Email com aviso fixo no assunto e no corpo; corpo mínimo (tipo, número, valor, escritório), sem descrição do serviço nem dados de processo; `Reply-To` do escritório e remetente de domínio da plataforma com SPF/DKIM/DMARC (tarefa de infraestrutura).
6. UI com badge "Simulado" visualmente distinto de "Autorizado" e banner permanente enquanto `EFATURA_MODE=SIMULADO`.
7. `EFATURA_MODE` ao nível do deployment, validado no arranque (C4), gravado em cada documento.
8. **`envio_email_automatico` desligado por omissão em cada tenant**; um ADMIN ativa-o com reconhecimento explícito do aviso. Isto não reabre a decisão "PDF mais email": o email é construído e testado; o que se recomenda é o valor por omissão do interruptor de lançamento. Alternativa (ligado por omissão com a marcação acima) fica à decisão do utilizador (U3).

**Recomendação de lançamento:** envio automático a clientes reais só depois de (a) confirmação escrita do contabilista ou da DNRE sobre o estatuto do documento simulado (Q2) e (b) comunicação aos escritórios de que continuam a emitir a fatura válida no software homologado até haver ligação real. A UAT usa um tenant de teste com o interruptor ligado. Ligar SMTP real exige checkpoint humano.

---

## Implications for Roadmap

Os três relatórios chegam à mesma sequência de sete blocos. Mapeamento dos rótulos:

| Fase | Nome | ARCHITECTURE | PITFALLS | FEATURES | Depende de | Pesquisa |
|------|------|--------------|----------|----------|------------|----------|
| 133 | Fundação fiscal | A | F-A | TS-01, TS-02 (parte), passo 1 da ordem sugerida | nada | Standard (precedentes no repo) |
| 134 | Núcleo de emissão: Fatura-Recibo atómica | B | F-B | TS-03 a TS-08, TS-17 (parte), TS-19, DF-01; passo 2 | 133 | Sim: regras fiscais (contabilista), não técnica |
| 135 | Nota de Crédito e efeitos financeiros | C | F-C | TS-09, TS-10; passo 3 | 134 | Leve: regras de NC |
| 136 | Formato eFatura e adaptador simulado | D | F-D | TS-11, TS-12, TS-13 (backend); passo 4 | 134 | Sim: verificação primária |
| 137 | PDF, armazenamento e email | E | F-E | TS-14, TS-15, TS-13 (apresentação), TS-18; passo 5 | 134, 136 | Leve |
| 138 | Plataforma como emitente | F | F-F | TS-16; passo 6 | 133, 134, 136, 137 | Não (reutiliza o pipeline) |
| 139 | Fecho: isolamento, segurança, runbook, UAT | G | F-G | (verificação transversal) | todas | Não |

Fluxo de dependências: `133 -> 134 -> {135, 136} -> 137 -> 138 -> 139`. As fases 135 e 136 são independentes e podem correr em paralelo (partilham `application.yml` apenas no pool do scheduler, que fica na 136). A fase 134 é a maior; se o roadmapper quiser fases mais pequenas, o corte natural é entre backend (modelo, regras, emissão atómica, guardas) e UI (diálogo com pré-visualização, `/faturas`), com deslocamento de um na numeração seguinte.

### Phase 133: Fundação fiscal
**Rationale:** numeração e configuração são a base sem I/O; o IT de concorrência da numeração é o maior risco técnico e deve vir cedo.
**Delivers:** `ConfiguracaoFiscal` (tabela nova; `Tenant` não é estendido, para evitar `NOT NULL` em tabela povoada, o incidente 120/120b), `ParametroFiscal` (IVA e retenção sugerida com vigência), `SerieFiscal` e `NumeracaoService` (`FOR UPDATE`, `ON CONFLICT DO NOTHING`, `lock_timeout`), flag `faturacao_ativa` unidirecional, constante partilhada do tenant reservado, ligação RBAC a `financeiro:*` (C1) e revisão do rótulo, exceções de domínio com `code` no `GlobalExceptionHandler`, `apiFetch` a propagar `status` e `code`, ecrã Definições > Faturação, seed do tenant demo com NIF válido (o atual é `000000000`, que o IUD recusa), script de migração e linha no README.
**Addresses:** TS-01, parte de TS-02 e TS-04, FISC-1, FISC-3.
**Avoids:** P-01, P-04 (repositórios estreitos), P-11, P-15, P-24, P-25.

### Phase 134: Núcleo de emissão (Fatura-Recibo atómica)
**Rationale:** só há pipeline quando há documento; é onde as regras fiscais entram.
**Delivers:** `DocumentoFiscal` e linhas `@Immutable` com snapshots, `RegraFiscalService` (função pura), `PagamentoFaturadoService` (substitui o corpo de `createPagamento`, DTO em vez de entidade, sem o `catch` que engole o saldo), lock de `ContaCorrente`, idempotência (C11), regra de data (C8), guardas 409 em `deletePagamento`, `deleteCliente` e `deleteProcesso`, repontagem de `cliente_id` em `mergeClientes`, linhas de `ComunicacaoFiscal` e `EntregaDocumento` inseridas `PENDENTE` na mesma transação, validação do adquirente (NIF `[1-9]\d{8}`, nome 3-150, morada até 100), mapeamento de `Pagamento.metodo`, `/faturas` com listagem paginada e detalhe, diálogo de pagamento com pré-visualização, teste de serviço com dois emitentes.
**Addresses:** TS-03 a TS-08, TS-19, DF-01, FISC-2 a FISC-7.
**Avoids:** P-02, P-03, P-05, P-06, P-10, P-18, P-19, P-20, P-21, P-27.
**Gate de entrada:** confirmação do contabilista das decisões fiscais (secção anterior) antes de planear.

### Phase 135: Nota de Crédito
**Rationale:** depende do documento imutável; sem os seus efeitos o sistema fica incoerente.
**Delivers:** `NotaCreditoService`, estorno como `Pagamento` negativo (C2), teto cumulativo com lock da Fatura-Recibo original, sem NC sobre NC, mesmo emitente, NC parcial por gross-up, motivo (`IssueReasonCode` obrigatório no XSD), UI, correção de `calculateMensalReceived`, testes de coerência das quatro leituras.
**Addresses:** TS-09, TS-10.
**Avoids:** P-13, P-14.
**Pesquisa:** `IssueReasonCode` para anulação total e parcial, obrigatoriedade de `References`, prazo máximo (A4, P4).

### Phase 136: Formato eFatura e adaptador simulado
**Rationale:** pode correr em paralelo à 135; o email da 137 espera pelo estado terminal da comunicação.
**Delivers:** XSD vendorizado mais JAXB (validar em JDK 23), `DfeXmlBuilder` (Fatura-Recibo código 2, Nota de Crédito código 5), IUD (`SecureRandom`, Luhn, `RepositoryCode=3`), validação XSD no CI com resolvedor de classpath e parser endurecido contra XXE, `EfaturaGateway` mais `SimuladoEfaturaGateway` mais resolvedor por `EFATURA_MODE` (C4), `ComunicacaoFiscal` com matriz de transições testada, `FiscalOutboxJob` (`SKIP LOCKED`, lease, backoff, sem saltar tenants suspensos, pool do scheduler com 3 ou mais threads), notificação in-app de falha persistente, badges de estado, reprocessar, LED sintético (C9), configuração do `Transmission` (`TransmitterTaxId`, `Software{Code,Name,Version}`), exclusões de SpotBugs para o código gerado por pacote.
**Addresses:** TS-11, TS-12, TS-13 (backend).
**Avoids:** P-07, P-08, P-09, P-17, P-27.
**Pesquisa:** sim, ver "O que verificar contra fontes primárias".

### Phase 137: PDF, armazenamento e email
**Rationale:** o email espera pelo PDF e por estado terminal de comunicação.
**Delivers:** renderer Thymeleaf mais openhtmltopdf a partir do snapshot, fontes embutidas, locale explícito, marca "SIMULAÇÃO" (C10, C6), `StorageService.download` e sobrecarga de URL com TTL curto, artefactos sem caminho de `delete`, SMTP opcional com `MAIL_ENABLED` (C3), `EntregaDocumento` com estados e reenvio, `envio_email_automatico` desligado por omissão, plumbing completo (`.env.example`, três compose, `deploy.yml`, `DEPLOYMENT.md`), gerador de QR atrás de configuração. O poller é a fonte de verdade; `@TransactionalEventListener(AFTER_COMMIT)` só como acelerador opcional, sem `@EnableAsync` obrigatório na v3.0.
**Addresses:** TS-14, TS-15, TS-18, TS-13 (apresentação), DF-02 opcional.
**Avoids:** P-16, P-17, P-22, P-23, P-28.
**Gates humanos:** SMTP real (checkpoint) e infraestrutura SPF/DKIM/DMARC.

### Phase 138: Plataforma como emitente
**Rationale:** herda o pipeline completo; só muda emitente e adquirente.
**Delivers:** `PagamentoSubscricao`, `SubscricaoFaturadaService`, `PlatformFaturacaoController` (`hasRole('PLATAFORMA_ADMIN')` de classe, emitente = tenant do principal validado contra o reservado, nunca por nome; recusar o nome reservado em `provisionTenant`), configuração fiscal da plataforma em `/plataforma`, captura e snapshot dos dados do adquirente (editáveis no registo), NC de subscrição, `GET /platform/faturacao/saude`. Visibilidade: o escritório recebe por email e não tem rota de leitura na v3.0 (decisão U9).
**Addresses:** TS-16, FISC-6.
**Avoids:** P-12.
**Bloqueio de dados:** NIF, morada e regime da LexCV (U2). Também fornece o `TransmitterTaxId` do XML nos dois emitentes (ver Gaps).

### Phase 139: Fecho
**Delivers:** auditoria de isolamento com dois tenants mais plataforma (404 cruzado em cada endpoint, filtros realmente aplicados), matriz de quatro papéis, `spotbugs:check` limpo sem exclusões novas, `dependency-check` manual nas dependências novas, arranque em `validate` com script e em BD nova com `update`, inventário de migrações no README e `DEPLOYMENT.md`, UAT ao vivo, atualização de CLAUDE.md e Key Decisions (exclusão de email revista, desvios de variáveis de ambiente, estornos negativos).
**Avoids:** P-04, P-15, P-16, P-24 (verificação), P-26.

### Phase Ordering Rationale

- Dependências: sem série e configuração não há emissão (133); sem documento não há NC, XML nem PDF (134); o email espera pelo artefacto e pela comunicação (136, 137); a plataforma herda tudo (138).
- Agrupamento por arquitetura: transação curta e síncrona nas fases 133 a 135; tudo o que tem I/O fica pós-commit nas 136 e 137, o que evita o lock da série durante rede (P-07, P-28).
- Pitfalls: o IT de numeração e o isolamento entram na primeira fase em que existem; o modo e a marcação "simulado" nascem junto do adaptador, antes de existir qualquer PDF ou email.
- Rotulagem das fases por ambos os investigadores e por FEATURES coincide; a única divergência (C12) está resolvida.

### Research Flags

Fases que provavelmente precisam de `/gsd:plan-phase --research-phase`:
- **Fase 134:** não é pesquisa técnica; precisa da validação do contabilista (IVA incluído, retenção, regime, data) antes de planear. Verificar também por IT que `@Immutable` mais UPDATE nativo de `cliente_id` se comporta como esperado.
- **Fase 136:** leitura primária do Manual Técnico v11 e do XSD vigente (IUD, Luhn, LED, numeração, meios de pagamento, tolerância de totais, `Transmission`); build do plugin JAXB em JDK 23.
- **Fase 135:** pesquisa leve sobre `IssueReasonCode` e regras de NC.

Fases com padrões estabelecidos (saltar research-phase):
- **Fase 133:** lock de linha, migrações e RBAC têm precedentes no repositório. Só verificar `SET LOCAL lock_timeout` vs hint JPA em PostgreSQL e o DDL que o Hibernate 6.6 gera com `@Enumerated(STRING)` e `@Check` no primeiro IT.
- **Fases 137, 138, 139:** reutilizam o que existe; 137 só precisa do smoke test de fontes no Alpine e da lista de elementos obrigatórios do DFA.

---

## O que verificar contra fontes primárias, e em que fase

Nada abaixo foi verificado em fonte primária.

| Verificação | Fonte | Fase | Se estiver errado |
|-------------|-------|------|-------------------|
| Pacote XSD 2024-05-27 e Manual v11 são os vigentes (Q1) | `efatura.cv/docs/xsd`, `efatura.cv/docs/manual/` | Idealmente antes da 133; no máximo antes de fechar a 136 | Campos e restrições do snapshot mudam |
| Âmbito da numeração (NIF, LED, tipo, série, ano), registo de LED, números não usados (raiz `Event`, código 99) (Q9) | Manual v11 | 133 (forma da chave) e 136 | Chave do contador e modelo de séries |
| Composição do IUD e vetores do dígito de Luhn (Q7); o SDK declara não haver vetores oficiais | Manual v11 | 136 | IUD rejeitado |
| NC: `IssueReasonCode` para anulação total e parcial, `References` obrigatório, prazo (Q8) | Manual v11, contabilista | 135 | Documento rejeitado ou incorreto |
| Código de meio de pagamento (`PaymentMeansCode`, lista UN/ECE D19B no pacote) para `TRANSFERENCIA`, `DINHEIRO`, etc.; se `PaymentAmount` é bruto ou líquido de retenção | Manual v11, XSD | 134 (mapa) e 136 | Mapa de métodos |
| Tolerância de reconciliação de totais, casas decimais, `UnitCode` para serviços, semântica de `IsSpecimen` (Q10) | Coluna "Regras" do mapa de campos | 134 e 136 | Validação de totais |
| Conteúdo mínimo obrigatório do DFA em PDF (Q11) | Manual v11, legislação | 137 | PDF incompleto |
| Host e janela de consulta do QR (Q6) | Manual v11 | Antes da ligação real; decisão de omitir na 137 | QR errado |
| DL 79/2020 (data de 12-nov ou 28-dez-2020), conservação (10 anos), coimas, requisito de software aprovado, Despacho 43/2022, Portaria 16/2022 (contingência de 5 dias úteis) | Boletim Oficial, `efatura.cv/docs/legislacao` | Antes da decisão de lançamento (U3); no mais tardar 139 | Risco de lançamento |
| Estatuto jurídico de uma Fatura-Recibo simulada enviada a clientes reais (Q2) | DNRE e contabilista | Antes de ativar email a clientes reais | Exposição legal |
| Procedimento de homologação do software, `Software.Code`, grant OAuth, URL e credenciais de teste, custódia de certificado por escritório, transformações exigidas na assinatura (Q3 a Q5) | `helpdesk@efatura.cv`, portal de adesão | Marco de ligação real; Q3 condiciona já a interface | Arquitetura de chaves |
| Código do IVA, Código do IRPS, Lei 70/VIII/2014, OE retificativo 2026 | Boletim Oficial, contabilista | Antes de planear a 134 | Regras fiscais |
| Build do plugin JAXB em JDK 23; PDF e QR na imagem `eclipse-temurin:23-jre-alpine` | Smoke test em CI (não é fonte primária) | 136 e 137 | Build ou runtime |

---

## Perguntas em aberto

### Para o utilizador (decisões de produto)

| # | Pergunta | Recomendação desta síntese |
|---|----------|----------------------------|
| U1 | `Honorario.valorTotal` está com ou sem IVA? Condiciona `totalPago >= valorTotal` do alerta e o saldo | IVA incluído (gross-up). Se for "acrescido", o total da fatura sobe 15% e as quatro leituras mudam de significado |
| U2 | Quem é o emitente "LexCV": NIF, morada, residente em Cabo Verde, sociedade ou pessoa singular, registo de IVA? Define IVA, retenção e o `TransmitterTaxId` | Sem estes dados a fase 138 não tem UAT e a fatura de subscrição não pode ser emitida |
| U3 | Lançar emails e PDFs sem validade fiscal a clientes reais? Texto do aviso? | Interruptor desligado por omissão; ligar após Q2 e comunicação aos escritórios |
| U4 | Flag `faturacao_ativa` unidirecional (C7) ou gate duro para todos? | Flag |
| U5 | Pagamentos retroativos: rejeitar (C8)? | Rejeitar na v3.0 |
| U6 | A NC que reverte o saldo reabre o honorário (volta a "por pagar" e pode disparar `HONORARIO_ATRASADO`)? | Sim, é a consequência lógica; confirmar |
| U7 | Há clientes estrangeiros ou da diáspora na carteira? Fora de âmbito com mensagem clara? | Fora de âmbito (FISC-5) |
| U8 | Os escritórios registam provisões e custas como `Pagamento`? | Se sim, aviso na UI; tratamento fiscal fica para depois |
| U9 | O escritório vê as faturas de subscrição na app ou só por email? | Só email na v3.0 (sem leitura cruzada de tenant) |
| U10 | Descrição da linha: controlada ou texto livre do honorário (sigilo profissional)? Logótipo no PDF? | Controlada; logótipo omitido ou snapshot |
| U11 | Cortes de âmbito: `CredencialFiscal`/crypto (C13) e `TentativaComunicacao` | Cortar ambos |
| U12 | Caminho estratégico da ligação real (plataforma direta, Middleware da DNRE ou Emissor Público) | Não decidir agora, mas a interface do adaptador já o pressupõe |

### Para o contabilista

| # | Pergunta |
|---|----------|
| A1 | Retenção na categoria B: 20% ou 15%? Base com ou sem IVA? Artigo exato do Código do IRPS? Quando se aplica (advogado singular, sociedade, Particular que é empresário em nome individual)? |
| A2 | Confirmar que advogados (singulares e sociedades) estão fora do REMPE, em IVA normal de 15%, sem isenção para serviços jurídicos. Existe isenção por pequeno volume fora do REMPE? |
| A3 | Exigibilidade do IVA em pagamento antecipado ou adiantamento; é aceitável uma Fatura-Recibo datada de hoje para um recebimento anterior; prazo do 5.º dia útil |
| A4 | Nota de Crédito: prazo máximo, regularização de IVA, `IssueReasonCode` adequado ao caso de anulação total e parcial |
| A5 | Houve alteração fiscal no orçamento retificativo de 2026? |
| A6 | Regra de arredondamento (IVA por linha ou por taxa sobre a soma das bases) e tolerância |
| A7 | Conservação (10 anos) e fundamento; obrigação de SAF-T a pedido |
| A8 | Fatura de subscrição: IVA, retenção, e regime se o operador da LexCV for não residente |
| A9 | Estatuto da Fatura-Recibo simulada enviada por email (Q2), em conjunto com a DNRE |
| A10 | Despesas reembolsáveis e provisões: tratamento fora da base de IVA (código 6) |

### Para a DNRE ou documentação primária

| # | Pergunta |
|---|----------|
| P1 | Versão vigente do XSD e do Manual Técnico (v11?); histórico de alterações posterior a 2024-05-27 |
| P2 | Âmbito da sequência numérica, registo de LED (`leds`), tratamento de números não usados |
| P3 | Composição do IUD, vetores de teste do Luhn |
| P4 | Regras de NC: `References`, `IssueReasonCode`, limites; cancelamento direto (prazo) |
| P5 | Lista de códigos de meio de pagamento aceites e semântica de `PaymentAmount` |
| P6 | Conteúdo mínimo do DFA e formato exato do QR (host, janela de consulta pública) |
| P7 | Procedimento de homologação de software (LexCV como ISV), `Software.Code`, grant OAuth, URL de teste, se o transmissor tem de usar mTLS |
| P8 | Pode um SaaS assinar em nome de cada escritório, ou cada emitente precisa de certificado ICP-CV de Selo Eletrónico com custódia pela LexCV? Custo e prazo |
| P9 | Transformações exigidas na assinatura XAdES (o exemplo oficial não tem `enveloped-signature`), `Id`s fixos |
| P10 | Texto integral do DL 79/2020, Despacho 43/2022, Portaria 16/2022 (conservação, coimas, software aprovado, contingência) |

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | Bibliotecas e versões ALTA (Maven Central, BOM, 10 experiências); modelo técnico eFatura MEDIUM (XSD lido em cópias públicas, excertos do manual). Não executado: build em JDK 23, imagem Alpine, ligação à DNRE, assinatura com certificado ICP-CV |
| Features | MEDIUM-LOW | Estrutura (tipos, séries, prazos) MEDIUM por fontes concordantes; regras fiscais finas LOW-MEDIUM; nada HIGH por falta de texto legal. Contaminação de fontes (EY 19%/9%, "art. 53.º" português) detetada e descartada |
| Architecture | HIGH para integração; MEDIUM para o formato | Pontos de integração lidos ficheiro a ficheiro, padrões com precedentes no repositório. Formato eFatura nesse relatório assentava em fontes secundárias (BAIXA-MÉDIA) e está agora corroborado pelo XSD lido em STACK |
| Pitfalls | MEDIUM | Baseline de código HIGH; factos regulatórios MEDIUM-LOW; analogias com Portugal marcadas e não tratadas como regra de Cabo Verde |

**Overall confidence:** MEDIUM. Suficiente para estruturar o roadmap e começar as fases 133 e 134 depois do gate do contabilista; insuficiente para fixar REQ-IDs fiscais finos ou para afirmar conformidade legal.

### Gaps to Address

- **Fontes primárias eFatura e legislação:** ver tabela de verificação. Alguém com acesso à rede lê o pacote XSD, o Manual v11 e o DL 79/2020 antes da fase 136 (idealmente antes da 133) e regista as diferenças face à cópia vendorizada.
- **Regras fiscais materiais:** gate do contabilista antes de planear a 134; as decisões fiscais ficam como dados parametrizáveis, não como código.
- **Origem do `TransmitterTaxId` e do `Software.Code`:** o `Transmission` é obrigatório no XML (STACK §2.2) e o transmissor é o NIF do operador da plataforma mesmo nas faturas dos escritórios, mas nenhum relatório decide de onde vêm na v3.0. Proposta: propriedades de configuração (`app.efatura.software.*`, NIF do transmissor) com valores de teste só válidos em SIMULADO e obrigatórias quando houver modo real. Resolver na fase 136; depende de U2 para o NIF real.
- **Decisão de custódia de certificados (Q3):** não bloqueia a v3.0, mas condiciona o desenho do marco seguinte; recolher a resposta da DNRE em paralelo.
- **Entidade jurídica da LexCV (U2):** bloqueia a emissão da subscrição e o UAT da fase 138.
- **Estatuto legal do documento simulado (Q2):** bloqueia a ativação de email a clientes reais, não o desenvolvimento.
- **Comportamentos só inferidos, não testados:** `JavaMailSender` com `host` vazio (137), `lock_timeout` em PostgreSQL via JPA (133), `@Immutable` com UPDATE nativo (134), DDL gerado por `@Check` e enums em `update` (133).
- **Dívidas operacionais herdadas (PROJECT.md):** 10 migrações manuais pendentes numa base de cliente e 4 listas de verificação ao vivo do v2.17. As migrações novas somam-se ao inventário do README; não as esconder atrás das da v3.0.

---

## Verificações feitas na síntese (código)

| Afirmação | Evidência |
|-----------|-----------|
| Só ADMIN tem `financeiro:edit` e `financeiro:manage`; ADVOGADO e TECNICO só `view`; ASSISTENTE nenhuma | `DatabaseSeeder.java:440-476` |
| `Pagamento.valorPago` não tem restrição na BD; `> 0` só no controller | `Pagamento.java`, `ResourceController.java:3053` |
| `totalPago` é `@Formula` SQL; o alerta lê `getTotalPago() >= getValorTotal()` | `Honorario.java:34`, `AlertasDiariosJob.java:276-277` |
| Precedentes de default em `application.yml`; sem `@SpringBootTest`/`@WebMvcTest`/`@AutoConfigureMockMvc` reais (só em comentários); 7 ficheiros `@DataJpaTest` | `application.yml:48,52`, `grep` em `backend/src/test` |
| `presignedDownloadUrl` usa TTL global; CORS não permite `Idempotency-Key` | `StorageService.java:64-71`, `SecurityConfig.java:91` |
| `efatura.cv` e `services.efatura.cv` inacessíveis a partir deste ambiente | `curl`: `CONNECT tunnel failed, response 403` |

---

## Sources

### Primary (HIGH confidence)
- Código do repositório: `ResourceController.java` (`:3039-3081`, `:3156-3186`, `:3247-3263`, `:834-957`), `models/{Pagamento,Honorario,Tenant,Cliente,AuditLog}.java`, `ParecerSolicitacaoRepository.java:27`, `AuditLogRepository.java:44`, `PlatformAdminController.java`, `UserPrincipal.java`, `DatabaseSeeder.java`, `AlertasDiariosJob.java`, `StorageService.java`, `SecurityConfig.java`, `application.yml`, `backend/migrations/README.md`, `DEPLOYMENT.md`, `.planning/PROJECT.md`, `.planning/config.json`.
- Pacote XSD oficial DNRE 2024-05-27, lido em `https://github.com/Kowts/efatura-cv-php` (`resources/xsd/efatura/2024-05-27/`) e `https://github.com/kriolos/kriolos-efatura`: alta para o conteúdo do pacote, não para ser a versão vigente.
- Spring Boot 3.4.1 BOM e metadados do Maven Central (`openhtmltopdf-pdfbox` 1.1.87, `zxing:core` 3.5.4, `jaxb-maven-plugin` 4.0.16, `xades4j` 2.4.1, `greenmail-junit5` 2.1.14); 10 experiências executadas em JDK 21 (`STACK.md` §11); `findsecbugs-plugin-1.14.0`.

### Secondary (MEDIUM confidence)
- Excertos das páginas oficiais `efatura.cv/docs/` (modelo conceitual, serviços eletrónicos, documentos fiscais, ecossistema, adesão à PE, FAQs, sobre a e-fatura): REST síncrono, ZIP/Deflate, validade só com assinatura e autorização, PDF por email não é fatura eletrónica, certificados ICP-CV, ambiente de homologação.
- Manuais Técnicos v7 e v10 e referência à v11 (nenhum PDF aberto): IUD de 45 caracteres, LED até 5 dígitos, Luhn.
- Wisedat, PayTraq, Odoo `l10n_cv_efatura`, Edicom, Primavera: LED por série, numeração sem lacunas, códigos de tipo, Fatura-Recibo só com data igual à do pagamento, QR.
- Miranda Advogados e VPQ (DL 79/2020, Portaria 16/2022, Despacho 43/2022); PwC Worldwide Tax Summaries e Flash OE 2025/2026 (IVA 15%, retenções); consultoria.cv, Porton di Nos Ilhas e OACV (REMPE e profissionais liberais); INOVE (SAF-T CV).
- FindSecBugs (padrões), Hibernate 6 e `CHECK` de enums.

### Tertiary (LOW confidence)
- Vendus CV (retenção 20% e 4%, motivos de isenção), Wisedat (tabela de motivos, numeração de artigos portuguesa), caboverdeexpert: taxas e códigos a validar.
- Analogias com Portugal (ATCUD, SAF-T(PT), Portaria 195/2020): padrões de engenharia, não regra de Cabo Verde.
- **Descartadas por contaminação:** EY VAT Guide 2026 (19% e 9% não são de Cabo Verde), blog Vendus sobre "art. 53.º" (regime português), PayPro Global (SaaS).

---
*Research completed: 2026-10-04*
*Ready for roadmap: yes, com o gate do contabilista antes da fase 134 e a verificação primária antes da fase 136*
