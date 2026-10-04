# Architecture Research — Faturação Eletrónica (eFatura CV), LexCV v3.0

**Domínio:** emissão de documentos fiscais eletrónicos (Fatura-Recibo, Nota de Crédito) por um SaaS jurídico multi-tenant, com adaptador pronto para o eFatura da DNRE
**Pesquisado:** 2026-10-04
**Confiança global:** MEDIA — ALTA para tudo o que assenta no código do repositório (lido, com ficheiro:linha); BAIXA-MEDIA para os factos do formato eFatura (manual técnico oficial inacessível a partir deste ambiente — ver §14)

Âmbito: apenas **como as funcionalidades novas se integram** na arquitetura existente. Decisões já travadas pelo utilizador (dois emitentes, 1 Fatura-Recibo por pagamento na mesma operação, correção só por Nota de Crédito, adaptador simulado, PDF + email, sem retroativos) não são reabertas.

---

## 0. Decisões recomendadas (resumo para o roadmapper)

| # | Tema | Recomendação | Porquê (prova no código) |
|---|------|--------------|--------------------------|
| 1 | Fronteira de tenant | Todas as entidades novas têm `tenant_id` **do emitente**. Nenhuma tabela existente é alterada (zero `ALTER`): a ligação ao pagamento vive em `t_documento_fiscal.pagamento_id`. | `Pagamento.java:15-30` e `Honorario.java:11-37` não têm `tenant_id` (isolamento transitivo via `Processo`, risco já catalogado em `v2.14-research/PITFALLS.md` Pitfall 1). Não se estende essa dívida. |
| 2 | Atomicidade | Novo `PagamentoFaturadoService.registar(...)` `@Transactional` faz pagamento + conta corrente + número + documento + linhas + linhas de outbox. `ResourceController.createPagamento` (`:3039-3081`) passa a delegar. **Nada de I/O externo na transação** (MinIO, SMTP, DNRE). | Hoje `createPagamento` não é transacional e engole `DataAccessException` (`:3071-3078`) — incompatível com "o pagamento só existe se a fatura existir". |
| 3 | Numeração sem lacunas | **Linha-contador em `t_serie_fiscal` + `SELECT … FOR UPDATE`** (JPA `PESSIMISTIC_WRITE`) na mesma transação do documento; `UNIQUE(tenant_id, serie_id, numero)` como rede de segurança; contador por `(emitente, tipo, série, ano, ambiente)`. **Não** sequences PostgreSQL, **não** `MAX+1` em `synchronized`. | Idioma já usado em `ParecerSolicitacaoRepository.java:27`, `TenantRoleRepository.java:39`, `SystemSettingRepository`. O `synchronized (ClienteRepository.class)` de `numero_cliente` (`ResourceController.java:278`) só protege uma JVM. |
| 4 | Idempotência | `chaveIdempotencia` (UUID gerado no cliente ao abrir o diálogo) no corpo do pedido, persistida em `t_documento_fiscal` com `UNIQUE(tenant_id, chave_idempotencia)`; repetição devolve o resultado original (`200`). | Evita ALTER a `t_pagamento`; o header `Idempotency-Key` exigiria alterar `SecurityConfig.java:91` (allowlist de headers CORS). |
| 5 | Imutabilidade | `DocumentoFiscal` + linhas: `@Immutable`, sem setters, repositório estreitado a `Repository<…>` sem `delete*`. **Todo o estado mutável** (comunicação, PDF, email) vive em tabelas satélite. | Precedente exacto: `AuditLog` + `AuditLogRepository extends Repository<AuditLog, Long>` (`AuditLogRepository.java:44`, `AuditLogImutabilidadeTest`). |
| 6 | Nota de Crédito | NC = novo `DocumentoFiscal` (tipo NC) que referencia a FR e **cria um `Pagamento` negativo (estorno)** no mesmo honorário. | Faz `Honorario.totalPago` (`@Formula`, `Honorario.java:34`), o KPI mensal (`calculateMensalReceived`, `:3247`), o alerta `HONORARIO_ATRASADO` e a soma no frontend (`financeiro/[id]/page.tsx:239`) refletirem a NC **sem os tocar**. |
| 7 | Adaptador eFatura | Port `EfaturaGateway` + `SimuladoEfaturaGateway`; implementação escolhida **por tenant** (`ConfiguracaoFiscal.modoComunicacao`); outbox = a própria linha `t_comunicacao_fiscal` (inserida na transação do documento) + poller `@Scheduled` com `FOR UPDATE SKIP LOCKED`. | Padrão de job fora do SecurityContext já provado em `AlertasDiariosJob.java` (tenantId explícito, `catch Throwable` em 3 camadas). |
| 8 | "Simulado ≠ comunicado" | Estado distinto `ACEITE_SIMULADO`; `AUTORIZADO` só com `ambiente = PRODUCAO` (CHECK na BD + única função de mapeamento + gateway real recusa documentos de outro ambiente + séries por ambiente). Nenhum caminho de código promove simulado a real. | §5.4. |
| 9 | PDF/Email | Pós-commit, via outbox. PDF gerado a partir do snapshot, guardado no MinIO com `StorageService.upload` (chave `<tenantId>/<documentoFiscalId>/<ficheiro>`); email só depois de PDF + comunicação terminal-OK; estado de entrega visível e reprocessável. | `StorageService.java:39` já cobre a convenção de chaves; falta apenas um `download` por bytes (§6). |
| 10 | Segredos | v3.0 constrói só o **seam** (port `CredenciaisEfaturaProvider` + tabela cifrada AES-256-GCM + utilitário), sem endpoint de upload e sem consumidor. Segredos nunca na `ConfiguracaoFiscal` em claro, nunca em DTO/log. | Evita superfície de ataque sem consumidor; deixa a troca para real como implementação + configuração. |
| 11 | RBAC | Novo scope `faturas`: `view` / `create` / `manage`. `POST /pagamentos` continua `financeiro:edit` (a fatura é consequência obrigatória, não opção). Plataforma: **sem permissões scope:action** — endpoints novos sob `/api/v1/platform/**` com `hasRole('PLATAFORMA_ADMIN')` ao nível da classe. | `PLATAFORMA_ADMIN` tem zero permissões por desenho (`DatabaseSeeder.java:478-487`; `PlatformAdminController.java:65-69`). |
| 12 | Produção (`validate`) | Cada tabela nova exige script manual idempotente `backend/migrations/<fase>-….sql` + linha no README, **antes** do deploy. | Instalações em estágio 2 correm `ddl-auto=validate` (`DEPLOYMENT.md` "Two-Stage Boot"); sem a tabela o arranque falha. |

---

## 1. Visão geral do sistema

```
┌──────────────────────────── web/ (Next.js 16, TanStack Query) ───────────────────────────┐
│ financeiro/[id] (diálogo pagamento + chaveIdempotencia)   /faturas  /faturas/[id]        │
│ settings → separador "Faturação" (faturas:manage)         /plataforma (+ /faturacao)     │
└───────────────┬──────────────────────────────────────────────────┬───────────────────────┘
                │ /api/v1 (rewrite, cookie JWT)                    │ /api/v1/platform/**
┌───────────────▼────────────── backend/ (Spring Boot) ────────────▼───────────────────────┐
│ ResourceController (delgação fina)   FaturaController      PlatformFaturacaoController   │
│ POST /pagamentos, DELETE /pagamentos FaturaConfigController   (hasRole PLATAFORMA_ADMIN) │
│        │  getTenantId()                      │ getTenantId()           │ emitente=tenant │
│        └──────────────┬──────────────────────┴─────────────┬───────────┘  reservado      │
│                       ▼                                    ▼                              │
│        ┌─────────────────────────── SERVIÇOS (recebem tenantId explícito) ───────────┐   │
│        │ PagamentoFaturadoService ─┐                                                 │   │
│        │ NotaCreditoService ───────┼─► FaturacaoService (núcleo: emitir documento)   │   │
│        │ SubscricaoFaturadaService ┘     ├─ RegraFiscalService (pura: IVA/isenção/…) │   │
│        │                                 ├─ NumeracaoService (lock da SerieFiscal)   │   │
│        │                                 ├─ ContaCorrenteService (lock + saldo)      │   │
│        │                                 └─ DfeIdentificadorService (IUD)            │   │
│        └─────────────────────────────────────────────────────────────────────────────┘   │
│                       │  UMA transação PostgreSQL (sem I/O externo)                       │
│  ┌────────────────────▼──────────────────────────────────────────────────────────────┐   │
│  │ t_pagamento  t_conta_corrente │ t_documento_fiscal(+_linha) @Immutable             │   │
│  │ t_serie_fiscal (contador)     │ t_comunicacao_fiscal  t_entrega_documento (outbox) │   │
│  └───────────────────────────────┴───────────────────────────────────────────────────┘   │
│                       │ commit                                                            │
│  ┌────────────────────▼───────────── FiscalOutboxJob (@Scheduled, sem SecurityContext) ─┐│
│  │ claim FOR UPDATE SKIP LOCKED → (1) gerar XML+PDF → MinIO (StorageService)             ││
│  │                               (2) EfaturaGateway.comunicar  (Simulado | Dnre*)         ││
│  │                               (3) EmailFiscalSender (SMTP) → anexa PDF                 ││
│  └──────────────────────────────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────────────────────────┘
   * DnreEfaturaGateway: fora do v3.0 (credenciais/certificado); mesma interface.
```

---

## 2. Mapa de integração: o que é NOVO e o que é MODIFICADO

### 2.1 Componentes existentes que têm de ser modificados

| Ficheiro:linha | Alteração | Risco / nota |
|----------------|-----------|--------------|
| `controllers/ResourceController.java:3039-3081` (`createPagamento`) | Passa de entidade `@RequestBody Pagamento` (`:3041`, mass-assignment) para DTO `PagamentoCreateRequest` (+ `chaveIdempotencia`) e delega em `PagamentoFaturadoService`. Resposta passa a incluir resumo do documento fiscal. | **Mudança de comportamento deliberada:** o `catch (DataAccessException)` que engole a falha do saldo (`:3071-3078`) desaparece — falhar o saldo passa a reverter pagamento **e** fatura. Atualizar os testes que constroem este controller com argumentos posicionais (`ResourceControllerProveniencaPapelTest`, `ResourceControllerUploadDocumentoTest`): novo campo no **fim** da lista de dependências. |
| `ResourceController.java:3156-3186` (`deletePagamento`) | Guarda `409 PAGAMENTO_FATURADO` se existir `DocumentoFiscal` com `pagamento_id = id` **ou** `pagamento_estorno_id = id`. Remove o `catch` que engole o saldo. | Locked decision: pagamento faturado não se apaga. |
| `ResourceController.java:3025-3037` (`listHonorarioPagamentos`) | Devolve DTO (`PagamentoResponse`) com `documentoFiscalId`, `numeroFormatado`, `faturado`, `estorno` em vez da entidade (`:3036`). | Aditivo para o frontend; usar uma query com `LEFT JOIN` (sem N+1 — Pitfall 3 arquivado). |
| `ResourceController.java:834-957` (`mergeClientes`) | Repontar `t_documento_fiscal.cliente_id` do secundário para o primário (UPDATE **nativo**, restrito a `tenant_id`), porque o secundário é apagado no fim. | Única escrita permitida em coluna de documento fiscal; é referência de navegação, não conteúdo fiscal (§3.3). Verificar se `@Immutable` + UPDATE nativo se comporta como esperado (IT). |
| `models/Pagamento.java`, `models/Honorario.java` | **Sem alteração** (escolha da Opção A, §3.6). | Se a fase decidir marcar estornos com coluna, é o único `ALTER` possível: `ADD COLUMN IF NOT EXISTS tipo …`. |
| `repositories/ContaCorrenteRepository.java` | Novo `findByClienteIdForUpdate` (`PESSIMISTIC_WRITE`). | O read-modify-write atual (`ResourceController.java:3063-3070`) perde atualizações sob concorrência; com NC e pagamentos passa a ser relevante. |
| `services/StorageService.java:39-95` | Novo `byte[] download(String objectKey)` (para anexar o PDF ao email). Reutiliza `upload` tal como está. | Nunca chamar `delete` para artefactos fiscais; teste que o garanta. |
| `seed/DatabaseSeeder.java:346-388` (`CATALOGO_PERMISSOES`) | +3 entradas `faturas:view/create/manage` (módulo "Faturação"; `ordem` entre 131-139 para ficar depois de Financeiro, ou 210+ no fim). Molde `ADMIN` recebe-as automaticamente (`:440`, `permissionMap.values()`). | `reservadaPlataforma=false` (ofertadas aos escritórios). |
| `config/UserPrincipal.java:41-52` (lista hardcoded do ADMIN) | +3 permissões. `UserPrincipalCatalogoSyncTest` falha se divergir — é a rede de segurança. | Ver §8: **primeira extensão do catálogo depois de existirem papéis de escritório (snapshots)**. |
| `config/SchedulingConfig.java` + `application.yml` | `spring.task.scheduling.pool.size` ≥ 3 (ou bean `TaskScheduler` próprio). | O comentário do próprio ficheiro admite scheduler de **1 thread**: um poller fiscal bloquearia o `AlertasDiariosJob` das 06:00 (e vice-versa). |
| `config/GlobalExceptionHandler.java` | Handlers para exceções de domínio fiscal com campo `code` (`CONFIGURACAO_FISCAL_INCOMPLETA`, `PAGAMENTO_FATURADO`, `SERIE_INDISPONIVEL`, `CREDITO_EXCEDIDO`, `ADQUIRENTE_INVALIDO`). | Serviço lança `RuntimeException` ⇒ rollback automático (não devolver `ResponseEntity` de erro de dentro da transação — ver `RecusaTransacional`). |
| `application.yml`, `backend/.env.example`, `docker-compose.yml:52-74`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml:58-73`, `.github/workflows/deploy.yml`, `DEPLOYMENT.md` | Novas variáveis SMTP (opcionais, com default vazio) e `FISCAL_SECRET_KEY` (opcional no v3.0). | CLAUDE.md diz "todas as variáveis são obrigatórias, sem defaults": SMTP **tem** de ser opcional, senão instalações existentes deixam de arrancar. `MINIO_PUBLIC_ENDPOINT:${…}` é o precedente de default. |
| `pom.xml` | `spring-boot-starter-mail`; biblioteca de PDF; (QR). Decisão de biblioteca = STACK.md. | O plugin `dependency-check` tem `failBuildOnCVSS=7` e há SpotBugs/FindSecBugs: licença/CVE da lib de PDF decide a escolha. |
| `web/src/lib/permissions.ts:5-13` | `"faturas"` em `KNOWN_SCOPES`. | A cadeia de fallback do frontend (`view ← edit/manage/create`) é **mais larga** que o backend (`hasAuthority` exato): atribuir `faturas:view` explicitamente a quem tiver `create`/`manage`. |
| `web/src/lib/api.ts` (`apiFetch`) | Propagar `status` e `code` do corpo de erro (hoje só `json.message` sobrevive, `api.ts:30-34`). | Necessário para o CTA "Configurar dados fiscais" ao receber `CONFIGURACAO_FISCAL_INCOMPLETA`. |
| `web/src/hooks/use-financeiro.ts` (`useCreatePagamento`), `types/financeiro.ts`, `schemas/financeiro.ts`, `app/(dashboard)/financeiro/[id]/page.tsx:139-231` | Chave de idempotência por abertura do diálogo; mostrar número da fatura; esconder "apagar" quando `faturado`; invalidar cache `faturas`. | `totalPago` do ecrã (`:239`) soma a lista — fica correto com estornos negativos. |
| `web/src/components/shared/dashboard-shell.tsx:60-76` | Item de nav "Faturas" (`requiredPermission: "faturas:view"`). | `/plataforma` já tem item próprio gated por papel (`platformNavItem`). |
| `web/src/app/(dashboard)/settings/page.tsx` | Novo separador "Faturação" (gated `faturas:manage`), padrão dos separadores `rbac`/`auditoria` (`:154-220`). | Separadores são botões manuais com `TabId`. |
| `web/src/app/(dashboard)/plataforma/columns.tsx` + `page.tsx` | Ação por linha "Registar pagamento de subscrição" (Dialog). | Padrão de painéis/dialogs já existente (`criar-tenant-panel.tsx`). |
| `backend/migrations/README.md` | Uma linha por script novo (regra do próprio README: "mesmo commit"). | Instalações em `validate`. |

### 2.2 Componentes novos

| Pacote / ficheiro | Responsabilidade |
|-------------------|------------------|
| `models/ConfiguracaoFiscal`, `SerieFiscal`, `DocumentoFiscal`, `DocumentoFiscalLinha`, `ComunicacaoFiscal`, `TentativaComunicacao`, `ArtefactoDocumentoFiscal`, `EntregaDocumento`, `PagamentoSubscricao`, `CredencialFiscal` (+ enums `TipoDocumentoFiscal`, `AmbienteFiscal`, `EstadoComunicacao`, `EstadoEntrega`, `RegimeIva`) | Persistência (§3). |
| `repositories/*` (um por entidade) | **Todos os finders recebem `tenantId`**; sem `findById` cru exposto a controllers; `DocumentoFiscalRepository` e `TentativaComunicacaoRepository` estreitados (`Repository<…>`). |
| `services/fiscal/FaturacaoService` | Núcleo único: dado emitente + adquirente + linhas → documento numerado persistido (não lê SecurityContext). |
| `services/fiscal/PagamentoFaturadoService`, `NotaCreditoService`, `SubscricaoFaturadaService` | Orquestradores `@Transactional` por caso de uso. |
| `services/fiscal/NumeracaoService` | Aloca número sob lock de `SerieFiscal` (find-or-create da série do ano). |
| `services/fiscal/RegraFiscalService` | Função pura (IVA, isenções, retenção, arredondamento); devolve `regraFiscalVersao`. Isolada porque as regras vêm da pesquisa fiscal do marco, não deste documento. |
| `services/fiscal/efatura/EfaturaGateway`, `SimuladoEfaturaGateway`, `EfaturaGatewayResolver`, `DfeXmlBuilder`, `DfeIdentificadorService` | Port/adaptadores e formato (§5). |
| `services/fiscal/pdf/PdfFaturaRenderer`, `services/fiscal/email/EmailFiscalSender` | Pós-commit (§6). |
| `services/fiscal/segredos/SegredosService`, `CredenciaisEfaturaProvider` | Seam de segredos (§7). |
| `jobs/FiscalOutboxJob` | Poller (§5.3). |
| `controllers/FaturaController`, `FaturaConfigController`, `PlatformFaturacaoController` | APIs (§9). Controllers separados — não engordar `ResourceController` (3384 linhas). |
| `backend/migrations/<fase>-create-fiscal-*.sql` | Tabelas novas para `validate`. |
| `web/src/app/(dashboard)/faturas/**`, `plataforma/faturacao/**`, `hooks/use-faturas.ts`, `hooks/use-platform-faturacao.ts`, `types/faturas.ts`, `schemas/faturas.ts` | Frontend. |

---

## 3. Modelo de dados

Convenções do repositório a manter: tabelas `t_*`, colunas `snake_case`, UUID gerado (`GenerationType.UUID`) para entidades novas (os `Integer` IDENTITY de `Pagamento`/`Honorario` são enumeráveis — não replicar em documentos fiscais), `BigDecimal` em `numeric(19,2)`, `tenant_id UUID NOT NULL` sem FK JPA (relações são colunas UUID nuas em todo o código).

### 3.1 Entidades

| Entidade (tabela) | Campos principais | Constraints | Mutável? |
|-------------------|-------------------|-------------|----------|
| **ConfiguracaoFiscal** (`t_configuracao_fiscal`) | `tenant_id`, `nif` (9 dígitos, mesmo padrão de `Cliente.java`), `denominacao`, `morada`, `localidade`, `email_fiscal`, `telefone`, `regime_iva`, `modo_comunicacao` (`SIMULADO` por defeito), `envio_email_automatico`, `created_at/updated_at/updated_by` | `UNIQUE(tenant_id)`. Método `completa()` (NIF válido + denominação + morada + regime). **NIF só editável enquanto o tenant não tiver `DocumentoFiscal`.** | Sim |
| **SerieFiscal** (`t_serie_fiscal`) | `tenant_id`, `tipo_documento` (`FR`/`NC`), `codigo` (≤10 alfanum.), `ano`, `ambiente`, `led_codigo` (NULL até haver real), `ultimo_numero`, `ativa` | `UNIQUE(tenant_id, tipo_documento, codigo, ano, ambiente)` | Só o contador |
| **DocumentoFiscal** (`t_documento_fiscal`) | `tenant_id` (emitente), `tipo`, `serie_id`, `serie_codigo`, `ano`, `numero`, `numero_formatado`, `data_emissao` (data em Cabo Verde, **nunca** do utilizador), `emitido_em`, `data_recebimento`, `ambiente`, `iud`, **snapshot emitente** `emitente_nif/nome/morada/localidade/regime_iva`, **snapshot adquirente** `adquirente_tipo` (`CLIENTE`/`ESCRITORIO`), `adquirente_nif/nome/morada/email`, `cliente_id` (NULL p/ plataforma), `escritorio_tenant_id` (NULL p/ escritório), `moeda`, `total_liquido/total_iva/total_retencao/total_documento`, `regra_fiscal_versao`, origem: `pagamento_id` \| `pagamento_subscricao_id` (FR) / `documento_origem_id` + `pagamento_estorno_id` (NC), `motivo` (NC), `emitido_por_id`, `emitido_por_nome`, `chave_idempotencia` | `UNIQUE(tenant_id, serie_id, numero)`, `UNIQUE(iud)`, `UNIQUE(pagamento_id)`, `UNIQUE(pagamento_subscricao_id)`, `UNIQUE(pagamento_estorno_id)`, `UNIQUE(tenant_id, chave_idempotencia)`, `@Check` "exatamente uma origem". Índices `(tenant_id, data_emissao)`, `(tenant_id, cliente_id)`. | **Não** (`@Immutable`), salvo `cliente_id` por merge (§3.3) |
| **DocumentoFiscalLinha** (`t_documento_fiscal_linha`) | `tenant_id`, `documento_fiscal_id`, `numero_linha`, `linha_origem_id` (NC), `descricao`, `quantidade`, `preco_unitario`, `valor_liquido`, `taxa_iva`, `valor_iva`, `motivo_isencao_codigo/texto`, `valor_retencao`, `total_linha` | `UNIQUE(documento_fiscal_id, numero_linha)` | Não |
| **ComunicacaoFiscal** (`t_comunicacao_fiscal`) — *outbox + estado* | `tenant_id`, `documento_fiscal_id` (1:1), `ambiente`, `estado`, `tentativas`, `proxima_tentativa_em`, `bloqueado_ate`, `ultimo_erro_codigo/mensagem` (sanitizados), `codigo_autorizacao` (**só real**), `referencia_externa` (simulado/homologação), `comunicado_em`, `@Version` | `UNIQUE(documento_fiscal_id)`, índice `(estado, proxima_tentativa_em)`, `@Check (estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO')` | Sim |
| **TentativaComunicacao** (`t_comunicacao_fiscal_tentativa`) | `tenant_id`, `comunicacao_id`, `n`, `iniciado_em`, `duracao_ms`, `resultado`, `codigo`, `mensagem`, `ambiente`, `payload_sha256` (hash do XML, não o XML — PII) | append-only, `@Immutable` | Não |
| **ArtefactoDocumentoFiscal** (`t_documento_fiscal_artefacto`) | `tenant_id`, `documento_fiscal_id`, `tipo` (`PDF`/`XML`), `object_key`, `sha256`, `tamanho`, `content_type`, `gerado_em` | `UNIQUE(documento_fiscal_id, tipo)` | Só criação |
| **EntregaDocumento** (`t_entrega_documento`) — *email* | `tenant_id`, `documento_fiscal_id`, `canal` (`EMAIL`), `destinatario_email` (snapshot), `estado` (`PENDENTE`/`EM_ENVIO`/`ENVIADO`/`FALHADO`/`SEM_DESTINATARIO`/`NAO_CONFIGURADO`), `tentativas`, `proxima_tentativa_em`, `bloqueado_ate`, `ultimo_erro`, `enviado_em`, `message_id`, `@Version` | `UNIQUE(documento_fiscal_id, canal)`; "reenviar" repõe `PENDENTE` | Sim |
| **PagamentoSubscricao** (`t_pagamento_subscricao`) | `tenant_id` (= **tenant da plataforma**, emitente), `escritorio_tenant_id` (cliente pagador), `plano` (snapshot de `TenantPlano`), `periodo_inicio/fim`, `valor`, `moeda`, `data_pagamento`, `metodo`, `referencia`, `registado_por_id`, `created_at` | índice `(tenant_id, escritorio_tenant_id, data_pagamento)`; não se apaga (como o pagamento faturado) | Não |
| **CredencialFiscal** (`t_credencial_fiscal`) — *seam, §7* | `tenant_id`, `tipo`, `ciphertext`, `iv`, `key_version`, `fingerprint`, `validade` | `UNIQUE(tenant_id, tipo)` | Sim |

Contagem: 9 tabelas operacionais + 1 de seam. `TentativaComunicacao` é o candidato natural a cortar se o prazo apertar (o estado atual + `ultimo_erro` bastam para a UI); mantê-la só se se quiser trilho de auditoria para a futura homologação.

### 3.2 Relações com o modelo existente

```
Cliente ─< Processo ─1 Honorario ─< Pagamento ──(unique)── DocumentoFiscal[FR] ─< Linha
                                       ▲ estorno (valor_pago < 0) ───(unique)── DocumentoFiscal[NC] ─(documento_origem_id)→ FR
t_tenant(plataforma) ─< PagamentoSubscricao ──(unique)── DocumentoFiscal[FR]   (adquirente = escritório)
DocumentoFiscal 1─1 ComunicacaoFiscal 1─< TentativaComunicacao
DocumentoFiscal 1─< ArtefactoDocumentoFiscal(PDF|XML)   DocumentoFiscal 1─1 EntregaDocumento(EMAIL)
```
Sem `@ManyToOne`/`@OneToMany`: o código inteiro usa colunas UUID nuas (confirmado em `Processo.java`, `Cliente.java`, `Documento.java`); manter.

### 3.3 Imutabilidade e snapshots

- **Snapshot no momento da emissão**: emitente (de `ConfiguracaoFiscal`) e adquirente (de `Cliente` — NIF é obrigatório de 9 dígitos desde v2.7, `Cliente.java`; email é opcional ⇒ pode não haver destinatário — estado `SEM_DESTINATARIO`, não é erro). Nunca ler `Cliente`/`Tenant` para renderizar um documento já emitido.
- `DocumentoFiscal`/`Linha`: `@Immutable`, `@Column(updatable = false)`, sem setters, repositório `Repository<DocumentoFiscal, UUID>` expondo só `save` (persist de novo), `findBy…TenantId…`. Teste `DocumentoFiscalImutabilidadeTest` espelhando `AuditLogImutabilidadeTest`.
- Fronteira "conteúdo fiscal vs. estado operacional": conteúdo = imutável; **estado de comunicação, PDF, email = satélites mutáveis**. Por isso `pdf_object_key` **não** está em `DocumentoFiscal`.
- `cliente_id` é a única referência não-fiscal que pode ser repontada (merge de clientes) — por UPDATE nativo restrito a `tenant_id` e testado. Alternativa mais pura: não guardar `cliente_id` e navegar por `pagamento → honorario → processo → cliente` (já acompanha o merge), à custa de joins nas listagens. **Recomendação:** guardar `cliente_id` (listagens rápidas) + repontar no merge.
- Sem trigger de BD no v3.0: não há *migration runner* (README de migrações) e cada trigger seria um script manual por instalação. Registar como endurecimento possível para o marco de integração real.
- `emitido_por_nome` é snapshot (como `AuditLog.detalhe`, que justifica o mesmo: `deleteUser` é *hard delete*).

### 3.4 Matriz de isolamento de tenant (obrigatória para cada entidade nova)

| Entidade | `tenant_id` significa | Como é lido nos controllers | Como é lido no job (sem SecurityContext) | Teste exigido |
|----------|-----------------------|-----------------------------|-------------------------------------------|---------------|
| ConfiguracaoFiscal, SerieFiscal, DocumentoFiscal, Linha, Artefacto, Entrega, Comunicação, Tentativa, CredencialFiscal | Emitente | `findByIdAndTenantId(id, getTenantId())`; `tenantId` **nunca** vem de parâmetro/corpo | O poller reclama linhas **cross-tenant** (única query sem filtro, existe só no job); cada passo seguinte recebe `row.tenantId` e volta a validar `(id, tenantId)` | IT Testcontainers com 2 tenants + mesma chave (precedente `NotificacaoRepositoryIT`, `PesquisaRepositoryIT`) |
| PagamentoSubscricao | **Tenant da plataforma** (emitente) | Só `PlatformFaturacaoController`; `escritorio_tenant_id` vem do path e é validado contra `t_tenant` | n/a | IT: escritório A não consegue ver/descarregar (não há rota tenant-scoped para estas linhas) |
| Pagamento/Honorario (existentes, **sem** `tenant_id`) | — | Mantém-se o idioma "recarregar `Processo` e comparar `tenantId`" (`ResourceController.java:3045-3052`); o serviço **asserta** `processo.tenantId == emitente` antes de criar o documento | n/a | O IT acima inclui "pagamento do tenant B referenciado a partir do tenant A ⇒ 404" |

Regra de revisão: nenhum método de repositório novo sem `tenantId` na assinatura (o risco residual de ~11 métodos `findByXxxId` está documentado em `PROJECT.md:203`). Chave MinIO `<tenantId>/<documentoFiscalId>/<ficheiro>` é resolvida a partir da linha da BD **depois** da verificação de tenant — nunca construída a partir de input.

### 3.5 Subscrições (entidade nova obrigatória)

`PagamentoSubscricao` pertence ao **emitente** (tenant reservado "LexCV") para manter o invariante "o dono do documento é o dono do pagamento"; o escritório pagador é `escritorio_tenant_id`. Consequências:
- O escritório **não** tem rota para ver estas faturas no v3.0 (recebe-as por email). Dar visibilidade exigiria uma leitura cross-tenant explícita e esconder `emitido_por_nome` (precedente T-128-29: nunca gravar identidade de operador de plataforma no registo de outro tenant). Manter fora do âmbito.
- Dados do adquirente: pré-preenchidos de `ConfiguracaoFiscal` do escritório (leitura cross-tenant **estreita**: só nif/denominação/morada/email), editáveis no ato do registo e gravados em snapshot. Se o escritório não tiver configuração, o formulário pede-os (não bloquear a plataforma por um dado que ela própria pode introduzir).
- Resolução do tenant emitente da plataforma: usar o `tenantId` do principal `PLATAFORMA_ADMIN` **e** validar `TENANT_RESERVADO.equals(tenant.getNome())`. Não usar `findFirstByNome("LexCV")` como fonte (o nome não é único — `TenantRepository.java:15-24`, WR-01 da Phase 119). O literal `"LexCV"` já está copiado em `PlatformAdminController.java:75`, `MigracaoPapeisEscritorioService.java:49` e `DatabaseSeeder.java:533`; recomenda-se uma constante partilhada em vez de uma 4ª cópia.
- Não acoplar o pagamento de subscrição a `plano`/`limiteUtilizadores` (fora do âmbito; `PUT /platform/tenants/{id}` continua separado).

### 3.6 Nota de Crédito e conta corrente

- NC referencia a FR (`documento_origem_id`) e as suas linhas (`linha_origem_id`); parcial = quantidades/valores por linha até ao saldo creditável (`total FR − Σ NC anteriores`). Dois pedidos concorrentes para a mesma FR: `findByIdForUpdate` sobre a FR (lock sem UPDATE é válido em entidade `@Immutable`) para serializar a verificação do teto.
- **Estorno como `Pagamento` negativo (Opção A, recomendada):** `NotaCreditoService` insere `Pagamento(honorarioId, valorPago = −total, metodo = "ESTORNO")` e ajusta `ContaCorrente` com o mesmo delta. Resultado: `Honorario.totalPago` (`Honorario.java:34`), KPI mensal (`ResourceController.java:3247-3262`), alerta de honorário em atraso (`AlertasDiariosJob`) e soma do frontend passam a ser líquidos **sem alteração**. O estorno é identificado por `DocumentoFiscal.pagamento_estorno_id` (sem ALTER), nunca emite FR (não passa por `POST /pagamentos`) e não é apagável.
- **Opção B rejeitada:** subtrair NC em `@Formula` + KPI + job + frontend = 4 sítios a manter em sincronia (a classe de bug "5 implementações divergentes" que o projeto já pagou duas vezes).
- NC de subscrição: documento + email; não mexe em conta corrente (não existe para a plataforma).
- Pergunta de produto a confirmar na fase: NC que reverte o saldo **reabre** o honorário (volta a ficar "por pagar" e pode disparar `HONORARIO_ATRASADO`). É a consequência lógica de "reverter o saldo"; confirmar que é desejado.

---

## 4. Emissão atómica, numeração e idempotência

### 4.1 Esqueleto da transação (`PagamentoFaturadoService.registar`)

```java
@Transactional                          // propaga RuntimeException => rollback total
public ResultadoPagamentoFaturado registar(UUID tenantId, UUID autorId, PagamentoCommand c) {
  // 1. idempotência: findByTenantIdAndChaveIdempotencia → devolver o original
  // 2. Honorario→Processo→tenant (idioma de ResourceController:3045-3052) → 404
  // 3. ConfiguracaoFiscal(tenantId).completa() senão CONFIGURACAO_FISCAL_INCOMPLETA (antes de escrever)
  // 4. Cliente (findByIdAndTenantId) → snapshot adquirente
  // 5. RegraFiscalService.calcular(...)  — pura
  // 6. ContaCorrente findByClienteIdForUpdate  → lock #1
  // 7. save Pagamento (dataPagamento default = hoje em CV); saldo += valor   (NÃO engolir DataAccessException)
  // 8. NumeracaoService.proximo(tenantId, FR, ano)                            → lock #2 (o mais tarde possível)
  // 9. save DocumentoFiscal + Linhas + ComunicacaoFiscal(PENDENTE) + EntregaDocumento(PENDENTE|SEM_DESTINATARIO)
  return ...;                           // PDF/XML/DNRE/email: tudo FORA, via outbox
}
```
O controlador apanha `DataIntegrityViolationException` **fora** da fronteira transacional (precedente: `PlatformAdminController.java:103-132` apanha a violação de unicidade fora do `@Transactional` de `provisionTenant`; dentro da mesma transação o PostgreSQL já a deixou em estado abortado).

### 4.2 Numeração sem lacunas — recomendação única

**Contador em `t_serie_fiscal`, bloqueado com `SELECT … FOR UPDATE` na mesma transação que insere o documento.**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select s from SerieFiscal s where s.tenantId = :t and s.tipoDocumento = :tipo "
     + "and s.codigo = :cod and s.ano = :ano and s.ambiente = :amb")
Optional<SerieFiscal> bloquear(...);      // idioma de ParecerSolicitacaoRepository#findByIdForUpdate
// numero = serie.getUltimoNumero() + 1; serie.setUltimoNumero(numero);   -- mesma transação do INSERT do documento
```

Porquê funciona: o número só é consumido por uma transação que também insere o documento; rollback reverte ambos ⇒ **sem lacunas por construção**; o lock serializa por série/emitente (dezenas de documentos/dia num escritório: contenção irrelevante). `UNIQUE(tenant_id, serie_id, numero)` é a rede de segurança e `UNIQUE(iud)` um segundo cinto. A chave do contador inclui `ano` (a numeração do eFatura é única por NIF+ano+LED+tipo e o ano é de 2 dígitos — fonte secundária, §14) e `ambiente`.

| Alternativa | Veredicto |
|-------------|-----------|
| `SEQUENCE`/`IDENTITY` PostgreSQL | **Rejeitada.** `nextval` não é transacional: um rollback deixa buraco. Exatamente o requisito oposto. |
| `MAX(numero)+1` em `synchronized` (idioma de `numero_cliente`, `ResourceController.java:276-285`; `Facto.ordem`, `:2233`) | **Rejeitada.** Só protege uma JVM; sob >1 instância perde para a `UNIQUE` com erro visível ao utilizador **depois** de o pagamento já ter sido tentado. |
| `MAX+1` + `UNIQUE` + retry | Gapless mas ruidoso (retry reexecuta pagamento+saldo). O lock de linha bloqueia em vez de falhar. |
| `pg_advisory_xact_lock` | Equivalente em garantia; menos idiomático neste código-base e invisível em `pg_locks` por tabela. Só se a fase preferir. |
| `UPDATE … RETURNING` atómico | Aceitável como micro-otimização do mesmo mecanismo (a mesma linha fica bloqueada até ao commit); preferir o idioma `PESSIMISTIC_WRITE` já testado aqui. |

Detalhes obrigatórios:
- **Find-or-create da série do ano** (1.º documento de janeiro, concorrente): `INSERT … ON CONFLICT DO NOTHING` (nativo) e depois bloquear; nunca `findFirst` + `save` (corrida → violação de unicidade a meio da transação de pagamento).
- **Timeout de lock:** `SET LOCAL lock_timeout = '5s'` no início da transação (o hint JPA `jakarta.persistence.lock.timeout` não é fiável em PostgreSQL — verificar na fase). Erro ⇒ `SERIE_INDISPONIVEL` 503, nunca bloquear a thread HTTP indefinidamente.
- **Ordem de locks fixa** (conta corrente → série) em **todos** os casos de uso (pagamento, NC, subscrição) para impedir deadlocks. A série é o lock mais curto (adquirido o mais tarde possível).
- **IT de concorrência** (Testcontainers; precedente `ParecerVersaoConcorrenciaIT`): N threads a emitir na mesma série ⇒ números `1..N` sem repetições nem buracos; e um caso em que a transação falha depois de alocar ⇒ próximo número reutilizado.

### 4.3 Idempotência contra duplo envio

- Cliente gera `crypto.randomUUID()` ao **abrir** o diálogo de pagamento (não ao submeter) e envia-o em `chaveIdempotencia`; botão desativado enquanto `mutation.isPending`.
- Servidor: lookup inicial por `(tenant_id, chave)`; se existir ⇒ `200` com o resultado original (verificar que `honorarioId`/`valor` coincidem, senão `409 CHAVE_REUTILIZADA`). Corrida (dois pedidos simultâneos): ambos passam o lookup, o segundo bloqueia no lock da série, e depois falha na `UNIQUE(tenant_id, chave_idempotencia)` ⇒ rollback integral (inclui o seu pagamento e o seu número) ⇒ o controlador relê e devolve o do primeiro.
- Aplicar a mesma chave a NC e a pagamento de subscrição.

### 4.4 Ano, data e fuso

A data/ano de emissão calculam-se **sempre** em `ZoneId.of("Atlantic/Cape_Verde")` (UTC−1; o contentor corre em UTC) — o precedente e o aviso estão em `AlertasDiariosJob.java:66` e `:79-81`. Entre 00:00-01:00 UTC de 1 de janeiro ainda é 31 de dezembro em Cabo Verde: usar `LocalDate.now()` nu emitiria com o ano/série errados. Injetar um `Clock` para testabilidade. A data de emissão nunca é a do formulário (`Pagamento.dataPagamento` pode ser retroativa ou nula — `calculateMensalReceived` já protege contra nula): vai em `data_recebimento`.

---

## 5. Adaptador eFatura, outbox e estados

### 5.1 Port / adaptadores

```java
public interface EfaturaGateway {
    AmbienteFiscal modo();                                   // SIMULADO | HOMOLOGACAO | PRODUCAO
    ResultadoComunicacao comunicar(DocumentoComunicavel doc, Optional<CredenciaisEfatura> cred);
}
public sealed interface ResultadoComunicacao {
    record Aceite(AmbienteFiscal ambiente, String referencia, String codigoAutorizacao) {}   // codigoAutorizacao só real
    record Rejeitado(String codigo, String mensagem) {}      // definitivo (regra de negócio)
    record ErroTransitorio(String mensagem, Duration sugestao) {}
}
```
- `DocumentoComunicavel` = projeção imutável do snapshot (sem entidades JPA a atravessar o port).
- `DfeXmlBuilder` (função pura snapshot → XML no formato eFatura) é **partilhado** pelo simulado e pelo real: o simulado valida o XML contra o XSD oficial (se for obtido na pesquisa da fase) — assim a troca futura não muda o formato, só o destino.
- `EfaturaGatewayResolver` escolhe o bean por `ConfiguracaoFiscal.modoComunicacao` do **tenant do documento** (cada contribuinte tem NIF/certificado próprios ⇒ a passagem a real é por escritório, gradual). O bean real é `@ConditionalOnProperty("app.efatura.real.enabled")`; se faltar, o resolver **falha fechado** (documento fica `PENDENTE` com erro explícito) — nunca cai para o simulado.
- `SimuladoEfaturaGateway`: determinístico, sem rede, devolve `Aceite(SIMULADO, "SIM-…", codigoAutorizacao=null)`. Permite também injetar falhas (propriedade de teste) para testar retries.

### 5.2 Estados de comunicação

```
PENDENTE ──claim──► EM_ENVIO ──Aceite(SIMULADO)────► ACEITE_SIMULADO   (terminal; NÃO é fiscalmente comunicado)
   ▲                   │      ──Aceite(HOMOLOG.)────► ACEITE_HOMOLOGACAO (terminal)
   │                   │      ──Aceite(PRODUCAO)────► AUTORIZADO         (terminal; só aqui há codigoAutorizacao)
   │ backoff           │      ──ErroTransitorio─────► PENDENTE (tentativas++, proxima_tentativa_em)
   └───────────────────┘      ──tentativas esgotadas► FALHADO  ──[reprocessar manual]──► PENDENTE
                              ──Rejeitado────────────► REJEITADO ──[reprocessar manual]──► PENDENTE
```
Transições vivem **num único método** (`ComunicacaoFiscal.transitar(...)`) com teste da matriz completa; `ACEITE_SIMULADO → AUTORIZADO` não existe.

### 5.3 Outbox transacional + poller

- **Outbox = a própria linha** `t_comunicacao_fiscal` (e `t_entrega_documento`) inserida **na mesma transação** do documento ⇒ nunca há documento sem trabalho pendente, nem trabalho para um documento que fez rollback. Não há dual-write.
- `FiscalOutboxJob`: `@Scheduled(fixedDelay = 15s)` (+ opcional `@TransactionalEventListener(AFTER_COMMIT)` só para reduzir latência; o poller é a fonte de verdade). Copia o padrão de `AlertasDiariosJob`: sem SecurityContext, `tenantId` sempre explícito (vem da linha), `catch Throwable` por lote e por item (uma exceção não tratada num `@Scheduled` pode cancelar execuções futuras).
- **Claim:** query nativa `… WHERE estado='PENDENTE' AND proxima_tentativa_em <= now() ORDER BY … LIMIT :lote FOR UPDATE SKIP LOCKED`, depois `EM_ENVIO` + `bloqueado_ate = now()+lease` numa transação curta; **I/O fora de qualquer transação**; resultado gravado noutra transação curta (`@Version`). Linhas `EM_ENVIO` com lease expirado são reclamadas (recuperação de crash). Seguro com várias instâncias.
- **Backoff:** exponencial com teto e jitter (ex.: 1m, 5m, 15m, 1h, 6h), N tentativas (comunicação ~8, email ~5) ⇒ `FALHADO`. `tentativas`, `ultimo_erro`, `proxima_tentativa_em` visíveis na UI.
- **Não saltar tenants suspensos** (`ativo=false`): diferença deliberada face a `AlertasDiariosJob` (que salta suspensos). Uma obrigação fiscal de comunicar/entregar não se suspende com o acesso do escritório.
- Pipeline por documento (passos idempotentes, cada um com o seu estado): (1) gerar XML + PDF → MinIO + `ArtefactoDocumentoFiscal`; (2) comunicar; (3) email — só quando PDF existe **e** a comunicação está em estado terminal-OK (`ACEITE_SIMULADO`/`ACEITE_HOMOLOGACAO`/`AUTORIZADO`).
- **Scheduler:** `SchedulingConfig` auto-configura 1 thread; subir `spring.task.scheduling.pool.size` (documentação Spring Boot: `ThreadPoolTaskScheduler` usa 1 thread por omissão).
- Falha persistente (`FALHADO`) ⇒ notificação **in-app** ao(s) ADMIN do escritório (categoria nova em `Notificacao`/`CategoriaNotificacao`, não silenciável — consistente com "notificações continuam in-app"). Para a plataforma: `GET /platform/faturacao/saude` com contagens por estado cross-tenant.

### 5.4 O simulado nunca pode parecer "comunicado à DNRE" (defesa em camadas)

1. **Tipo:** `Aceite` transporta o `ambiente`; **uma única** função mapeia `(resultado, ambiente) → EstadoComunicacao`; `AUTORIZADO` só sai para `PRODUCAO`. O simulado só sabe construir `Aceite(SIMULADO, …)`.
2. **BD:** `@Check (estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO')`; `codigo_autorizacao` é NULL fora de produção (a referência simulada vai para `referencia_externa`).
3. **Séries por ambiente:** documentos emitidos em simulação ficam em séries `SIMULADO` (código visivelmente distinto, ex. prefixo `SIM`) e **nunca** são reenviados; a passagem a produção exige série nova (LED registado na DNRE) e a numeração real começa limpa. O gateway real recusa `doc.ambiente != modo()`.
4. **Passagem a real é uma ação explícita** (`faturas:manage`): exige credenciais configuradas e `app.efatura.real.enabled`; não há "promover" documentos antigos.
5. **Apresentação:** PDF com marca "SIMULAÇÃO — sem validade fiscal, não comunicado à DNRE" (rodapé/marca de água), **sem** QR para o portal público de verificação em simulado; email com o mesmo aviso; badge "Simulado" na UI.
6. **IUD:** gerado no emissor (`DfeIdentificadorService`) com o código de repositório derivado do ambiente (o eFatura distingue 1 Principal / 2 Homologação / 3 Teste — fonte secundária, **confirmar** na fase D); nunca um IUD de produção para um documento simulado.

**Risco de negócio a assinalar ao roadmapper (CRÍTICO):** enquanto só existir o simulado, as faturas emitidas **não têm validade fiscal** e vão por email a clientes reais. A mitigação é toda de apresentação (item 5) — a decisão de lançar a funcionalidade em produção antes da ligação real é do produto/jurídico.

### 5.5 Rejeição e correção

Um `REJEITADO` consome número (numeração sem lacunas) e o documento é imutável: a correção operacional é "reprocessar" (reenvio do mesmo conteúdo após corrigir configuração/credenciais) ou, se o conteúdo estiver errado, **Nota de Crédito + nova fatura**. As regras exatas de rejeição/anulação do eFatura são trabalho da fase de integração real (fora do v3.0), mas o modelo de estados já as comporta.

---

## 6. PDF, armazenamento e email

- **Renderização:** `PdfFaturaRenderer` (porta; lib por STACK.md) lê **apenas** o snapshot persistido (+ logótipo opcional de `Tenant.logoDataUrl`, só apresentação). O PDF guardado é a rendição legal; só se regenera se faltar (download "auto-curativo" a partir do snapshot).
- **Armazenamento:** `StorageService.upload(tenantId, documentoFiscalId, "FR-A-2026-00001.pdf", …)` ⇒ chave `<tenantId>/<documentoFiscalId>/<ficheiro>` (`StorageService.java:39-57`), registada em `ArtefactoDocumentoFiscal`. IDs de documento fiscal não colidem com `Documento.id` (UUID aleatório). Não existe caminho de código que chame `StorageService.delete` para artefactos fiscais (os fluxos de apagar documentos usam `t_documento.caminho_arquivo`); teste que o afirme. Sugestão operacional: versionamento no bucket.
- **Download:** `GET /faturas/{id}/pdf` ⇒ após `faturas:view` + `(id, tenantId)`, devolve URL pré-assinado (`StorageService.presignedDownloadUrl`, `:64`; mesmo padrão de `documentos/{id}/download`, `ResourceController.java:2917-2937`). Se o artefacto ainda não existe ⇒ `202`/`pdfDisponivel=false` e a UI repete a consulta (polling, como o sino de notificações — não há WebSocket/SSE no projeto).
- **Email (primeiro canal SMTP):** porta `EmailFiscalSender` + implementação `JavaMailSender`; `NaoConfiguradoEmailSender` quando `SMTP_HOST` está vazio ⇒ entrega fica `NAO_CONFIGURADO` (visível, **não** falha o pagamento). Sempre **pós-commit** pelo outbox. Anexa o PDF lido do MinIO (`download`, novo). Destinatário = snapshot `adquirente_email`, validado (`InternetAddress` estrito; sem CR/LF) — nunca construir cabeçalhos com input livre; assunto fixo com o número do documento. Semântica **at-least-once**: um crash após o SMTP aceitar e antes de gravar `ENVIADO` pode duplicar um email (aceitável; registar `message_id`).
- **Visibilidade/retry:** `EntregaDocumento.estado/ultimo_erro/tentativas` mostrados no detalhe da fatura; ação "Reenviar" (`faturas:create`) repõe `PENDENTE`. PII: não registar corpo/endereços em INFO.

---

## 7. Configuração fiscal por tenant e segredos

- **Dados não secretos** em `t_configuracao_fiscal` (§3.1). `Tenant.nif` (`Tenant.java:23`, nullable e não preenchido por `provisionTenant`, `SetupService.java:164-168`) **não** é a fonte fiscal: a `ConfiguracaoFiscal` é. A plataforma tem a sua própria (editada por `PLATAFORMA_ADMIN` em `/plataforma`, §9).
- **Porta de emissão bloqueada** até a configuração estar completa (`409 CONFIGURACAO_FISCAL_INCOMPLETA`): **regressão de UX deliberada** — hoje um ADMIN regista pagamentos sem NIF próprio. Mitigar com CTA direto para Definições → Faturação, seed do tenant demo e nota de rollout.
- **Segredos futuros** (certificado PKCS#12/selo eletrónico, palavra-passe, token/URL do middleware, credenciais DNRE):
  - Tabela `t_credencial_fiscal` com `ciphertext` AES-256-GCM, IV aleatório de 96 bits, AAD = `tenantId|tipo`, `key_version`; chave-mestra em `FISCAL_SECRET_KEY` (env, **opcional** no v3.0, obrigatória só quando `app.efatura.real.enabled`).
  - API **só de escrita** (futura); leitura devolve `{configurado, fingerprint, validade}`. Nunca em DTO, `toString` (`@ToString.Exclude`; atenção: `MinioProperties` usa `@Data`, não copiar), log nem `application.yml`.
  - **No v3.0:** construir o seam (port + entidade + `SegredosService` + testes) mas **não** expor upload — não há consumidor e cada endpoint de segredos é superfície ASVS (config.json: `security_enforcement: true`, nível 1, bloqueio em `high`).
  - Topologia da futura ligação (SaaS multi-contribuinte vs. um middleware por escritório; custódia do selo eletrónico de cada escritório pela LexCV) é **a** pergunta aberta do marco de integração real — não decidir aqui.

---

## 8. RBAC

| Permissão | Concede | Notas |
|-----------|---------|-------|
| `faturas:view` | Listar/ver faturas e NC, estado de comunicação/email, descarregar PDF | |
| `faturas:create` | Emitir **Nota de Crédito**, reenviar email, reprocessar comunicação `FALHADO`/`REJEITADO` | A FR **não** precisa disto: nasce de `POST /pagamentos` (`financeiro:edit`) |
| `faturas:manage` | Configuração fiscal do escritório (NIF, morada, regime, séries, modo de comunicação) | Alterações sensíveis; ADMIN por omissão |

- **Backend:** `@PreAuthorize("hasAuthority('faturas:…')")` por handler (padrão de `ResourceController`). `POST /pagamentos` fica `financeiro:edit`; `DELETE /pagamentos/{id}` fica `financeiro:manage` + guarda de faturado.
- **Adoção em escritórios existentes (descoberta importante):** esta é a **primeira extensão do catálogo depois de existirem papéis de escritório** (`t_tenant_role` é snapshot dos moldes — `SetupService.java:282-298` copia `new HashSet<>(molde.getPermissions())`). Todas as permissões anteriores (Phase 124) foram criadas *antes* da existência de `TenantRole`. Consequências: (a) o molde `ADMIN` recebe-as no arranque (`seedRbac`, `DatabaseSeeder.java:416-440`, corre incondicionalmente); (b) o `TenantRole` ADMIN de cada escritório **existente não** as recebe; (c) o administrador continua a ter acesso **enquanto o papel se chamar "ADMIN"**, porque `UserPrincipal.create` (`UserPrincipal.java:41-52`) acrescenta a lista hardcoded — mas o papel pode ter sido renomeado (Phase 127) e a matriz RBAC mostraria checkboxes desligadas para um papel que na prática tem acesso. **Recomendação:** passo convergente e idempotente no arranque (a seguir a `MigracaoPapeisRunner`, `Ordered.LOWEST_PRECEDENCE`) que acrescenta ao `TenantRole` com `moldeId == molde ADMIN` as permissões do catálogo em falta — arranque de sistema, não decisão de pessoa (mesma classificação que a Phase 128 deu a `MigracaoPapeisEscritorioService`, sem evento de auditoria). Moldes `ADVOGADO`/`TECNICO`/`ASSISTENTE` **não** recebem `faturas:*` por omissão (decisão conservadora; o ADMIN concede via RBAC).
- **Fallback do frontend ≠ backend:** `hasScopedPermission` aceita `edit/manage/create` como `view` (`permissions.ts`), mas `hasAuthority` do backend é exato. Quem receber `faturas:create` tem de receber também `faturas:view` explicitamente, senão a UI mostra e o backend recusa.
- **Plataforma:** `PLATAFORMA_ADMIN` mantém **zero** permissões scope:action (`DatabaseSeeder.java:478-487`). Os endpoints de faturação da plataforma ficam em `/api/v1/platform/**` num `PlatformFaturacaoController` com `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` ao nível da classe — mesmo gate de `PlatformAdminController.java:65-69`, cobre handlers futuros. Um `ADMIN` de escritório não o alcança (guardas de `AdminController` contra auto-atribuição do papel reservado). Se, no futuro, a plataforma precisar de operadores com poderes parciais, o mecanismo previsto é `Permission.reservadaPlataforma = true` (`Permission.java`), não conceder `faturas:*`.
- Os serviços **não** leem `SecurityContext`: recebem `tenantId`/`autorId`. O controlador do escritório passa `getTenantId()`; o da plataforma passa o tenant reservado validado. Assim o mesmo núcleo serve os dois emitentes e o job.

---

## 9. API e frontend

### 9.1 Endpoints

| Método e caminho | Gate | Notas |
|------------------|------|-------|
| `POST /api/v1/pagamentos` (**modificado**) | `financeiro:edit` | corpo: `honorarioId, valorPago, dataPagamento?, metodo?, chaveIdempotencia`. `201` `{pagamento, documentoFiscal:{id, numeroFormatado, estadoComunicacao, pdfDisponivel, estadoEmail}}`; repetição ⇒ `200`. Erros com `code`. |
| `GET /api/v1/honorarios/{id}/pagamentos` (**modificado**) | `financeiro:view` | DTO com `faturado/estorno/documentoFiscalId/numeroFormatado`. |
| `DELETE /api/v1/pagamentos/{id}` (**modificado**) | `financeiro:manage` | `409 PAGAMENTO_FATURADO`. |
| `GET /api/v1/faturas` | `faturas:view` | filtros `tipo, estado, de, ate, clienteId, q`; paginação; projeção (sem N+1). |
| `GET /api/v1/faturas/{id}` | `faturas:view` | linhas, comunicação, entrega, NC associadas. |
| `GET /api/v1/faturas/{id}/pdf` | `faturas:view` | `{url}` pré-assinado ou `pdfDisponivel=false`. |
| `POST /api/v1/faturas/{id}/nota-credito` | `faturas:create` | `{linhas[]\|total, motivo, chaveIdempotencia}`. |
| `POST /api/v1/faturas/{id}/reenviar-email` · `POST /api/v1/faturas/{id}/comunicacao/reprocessar` | `faturas:create` | |
| `GET/PUT /api/v1/faturas/configuracao` · `GET /api/v1/faturas/series` | `faturas:manage` | PUT recusa mudar NIF se já existem documentos. |
| `POST /api/v1/platform/tenants/{id}/pagamentos-subscricao` · `GET …/pagamentos-subscricao` | `hasRole('PLATAFORMA_ADMIN')` | `{valor, periodoInicio, periodoFim, dataPagamento, metodo, referencia, adquirente?, chaveIdempotencia}` |
| `GET /api/v1/platform/faturas` (+ `/{id}`, `/{id}/pdf`, `/{id}/nota-credito`, `/{id}/reenviar-email`) · `GET/PUT /api/v1/platform/faturacao/configuracao` · `GET /api/v1/platform/faturacao/saude` | idem | emitente = tenant reservado |

Valores monetários: manter o contrato atual (`number`, 2 casas; Jackson lê `BigDecimal` do texto do token — sem perda); o servidor é a única fonte dos totais — o frontend nunca recalcula IVA.

### 9.2 Frontend

- `financeiro/[id]/page.tsx`: diálogo gera `chaveIdempotencia`; toast "Fatura FR … emitida"; ligação para `/faturas/[id]`; "apagar" escondido para `faturado`/`estorno`; tratar `CONFIGURACAO_FISCAL_INCOMPLETA` com CTA para Definições → Faturação (requer `apiFetch` com `code`).
- `/faturas` (lista com o `DataTable` partilhado e exportação CSV com o escape anti-injeção já corrigido na Phase 104 — `lib/csv.ts`), `/faturas/[id]` (linhas, badges de comunicação — **"Simulado" visualmente distinto de "Autorizado"** — e de email, botão PDF, diálogo de NC, polling enquanto `PENDENTE`/PDF em falta).
- Definições → separador "Faturação" (`faturas:manage`): dados fiscais, séries, modo.
- `/plataforma`: ação "Registar pagamento de subscrição" por linha de tenant; nova página `plataforma/faturacao` (lista de faturas da LexCV, configuração fiscal da plataforma, saúde da fila). Hooks `use-faturas.ts`, `use-platform-faturacao.ts` (chaves de cache `["faturas", …]`, `["platform","faturacao", …]`; mutações invalidam também `["honorarios","pagamentos",id]` e `["clientes","conta-corrente"]`).
- Antes de escrever código de framework: ler `web/node_modules/next/dist/docs/` (aviso de `web/AGENTS.md`/CLAUDE.md sobre Next 16).

---

## 10. Ordem de construção sugerida (numeração de fases ≥ 133, atribuída pelo roadmapper)

| Fase | Conteúdo | NOVO | MODIFICADO | Depende de | Pesquisa |
|------|----------|------|------------|------------|----------|
| **A. Fundação fiscal** | Permissões `faturas:*`, `ConfiguracaoFiscal`, `SerieFiscal`, `NumeracaoService` (lock), DTO/erros base, separador "Faturação" | entidades+repos+migração `…-create-fiscal-config-series.sql`, `NumeracaoService`, `FaturaConfigController`, UI de configuração, **passo convergente de permissões do ADMIN** | `DatabaseSeeder.CATALOGO_PERMISSOES`, `UserPrincipal`, `permissions.ts`, `SchedulingConfig`, `GlobalExceptionHandler` | — | Padrão; **IT de concorrência da numeração é o maior risco técnico — cedo** |
| **B. Fatura-Recibo atómica (honorários)** | `DocumentoFiscal`+linhas imutáveis, `RegraFiscalService`, `PagamentoFaturadoService`, idempotência, guarda de apagar, lock de conta corrente, listagem/detalhe `/faturas` | `DocumentoFiscal*`, `FaturacaoService`, `PagamentoFaturadoService`, `FaturaController`, páginas `/faturas` | `ResourceController` (`createPagamento`, `deletePagamento`, `listHonorarioPagamentos`, `mergeClientes`), `ContaCorrenteRepository`, `financeiro/[id]`, `use-financeiro`, `apiFetch` | A | **Regras fiscais (IVA/isenção/retenção/arredondamento) — a pesquisa do marco; bloqueia `RegraFiscalService`** |
| **C. Nota de Crédito** | `NotaCreditoService`, estorno, teto cumulativo, UI | `NotaCreditoService`, diálogo NC | — (reutiliza B) | B | Regras de NC parcial/IVA |
| **D. Adaptador + outbox + comunicação simulada** | Port, `SimuladoEfaturaGateway`, `DfeXmlBuilder`, IUD, `ComunicacaoFiscal`(+tentativa), `FiscalOutboxJob`, badges, reprocessar, notificação in-app de falha | `services/fiscal/efatura/**`, `jobs/FiscalOutboxJob`, migração | `application.yml` (pool do scheduler) | B (pode correr em paralelo a C) | **Manual Técnico v10 + XSD: IUD, LED, estados, rejeições — ler diretamente (§14)** |
| **E. PDF + MinIO + email SMTP** | Renderer, artefactos, `download`, `EntregaDocumento`, SMTP opcional, UI de estado/reenvio | `pdf/**`, `email/**`, `ArtefactoDocumentoFiscal`, `EntregaDocumento`, migração | `StorageService`, `pom.xml`, `.env.example`, 3 composes, `deploy.yml`, `DEPLOYMENT.md` | B, D (email espera comunicação) | Elementos legais obrigatórios do PDF; QR; licença/CVE da lib |
| **F. Faturação da plataforma** | `PagamentoSubscricao`, `SubscricaoFaturadaService`, `PlatformFaturacaoController`, config fiscal da plataforma, UI `/plataforma` | entidade+migração, controller, páginas | `plataforma/columns.tsx`, `page.tsx` | A, B, D, E (reutiliza o pipeline completo) | Captura de dados do adquirente |
| **G. Fecho** | Auditoria de isolamento e segurança (estilo `123-ISOL-AUDIT.md`), seam de segredos (§7), runbook (`migrations/README.md`, `DEPLOYMENT.md`), UAT ao vivo | `SegredosService`, `CredencialFiscal`, testes de isolamento | docs | todas | — |

Racional: A antes de tudo porque numeração e configuração são o fundamento sem I/O; B antes de C/D/E porque só existe pipeline quando existe documento; D antes de E porque o email espera o estado terminal da comunicação e o artefacto XML; F por último porque herda o pipeline completo — **mas** o núcleo recebe o emitente por parâmetro desde B e a fase B deve incluir um teste de serviço com **dois emitentes** (tenant escritório + tenant plataforma) para validar cedo a parametrização. C e D são independentes e podem correr em paralelo (`config.json`: paralelização ao nível do plano ativa).

Cada fase com tabelas novas fecha com **script de migração + linha no README** (instalações em `validate`).

---

## 11. Padrões a seguir e anti-padrões

**Padrões**
1. **Núcleo parametrizado por emitente** (`tenantId` explícito, sem SecurityContext) — serve escritório, plataforma e job.
2. **Lock de linha para numeração** + `UNIQUE` como cinto (§4.2).
3. **Outbox por linha de estado** + `SKIP LOCKED` + lease + backoff (§5.3).
4. **Imutabilidade estrutural** (`@Immutable` + repositório estreitado) em vez de disciplina.
5. **Falhar fechado** (resolver de gateway, config fiscal incompleta, série indisponível).
6. **Exceções de domínio → rollback**, mapeadas em `GlobalExceptionHandler` com `code`.

**Anti-padrões (específicos deste marco)**
- **I/O dentro da transação** (MinIO/SMTP/DNRE): segura o lock da série e da conta corrente durante rede lenta ⇒ serializa a faturação do escritório inteiro.
- **Engolir `DataAccessException`** dentro da transação (como `ResourceController.java:3071-3078`): em PostgreSQL a transação fica abortada e o `catch` esconde-o.
- **Sequence ou `MAX+1`** para numeração fiscal (§4.2).
- **`LocalDate.now()` sem zona** para ano/série (§4.4).
- **Ler `Cliente`/`ConfiguracaoFiscal` para reimprimir** um documento emitido (quebra imutabilidade).
- **Escrever `AUTORIZADO` a partir de um caminho que não seja o mapeamento único** (§5.4).
- **Receber a entidade `Pagamento` no corpo** (mass-assignment; `ResourceController.java:3041`) — usar DTO.
- **Pôr `ComunicacaoFiscal`/PDF/email em `t_documento_fiscal`**: obriga a quebrar a imutabilidade.
- **Adicionar `ROLE_*` ou permissões `faturas:*` a `PLATAFORMA_ADMIN`**.
- **Fazer o frontend recalcular totais/IVA**.

---

## 12. Operação: migrações, variáveis, escalabilidade

- **Migrações:** instalações em estágio 2 (`validate`, `SEED_ENABLED=false`) **falham o arranque** sem as tabelas. Um script idempotente por fase (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`, tipos exatamente iguais aos das entidades — `validate` compara colunas/tipos) + linha no README no mesmo commit. Precedentes: `86-create-notificacao-table.sql`, `126-…`, `127-…`. Constraints/`@Check` declarados na entidade só nascem sozinhos em `update`; no script têm de ser repetidos. Acrescentar às "dívidas operacionais" a inventariação do estado de cada novo script por BD.
- **Variáveis novas (todas opcionais no v3.0):** `SMTP_HOST/PORT/USER/PASSWORD/FROM/STARTTLS`, `MAIL_ENABLED`, `FISCAL_SECRET_KEY`, `EFATURA_REAL_ENABLED=false`. Atualizar `.env.example`, os três compose files e `deploy.yml`.
- **Estimativa de carga:** escritório jurídico ⇒ dezenas de documentos/dia/tenant. Um lock por série é irrelevante; o gargalo realista é o poller (SMTP/MinIO) — lote pequeno + `SKIP LOCKED` escala horizontalmente.

| Preocupação | 100 utilizadores | 10K | 1M |
|-------------|------------------|-----|----|
| Numeração | lock de série | idem (por emitente) | particionar por emitente já é natural |
| Outbox | 1 instância, poll 15s | N instâncias com `SKIP LOCKED` | fila externa (fora de âmbito) |
| Listagens de faturas | índice `(tenant_id, data_emissao)` | paginação por cursor | partição por ano |

---

## 13. Pontos de integração externos

| Serviço | Padrão | Notas |
|---------|--------|-------|
| DNRE eFatura | `EfaturaGateway` (simulado agora; real depois) | Real: o eFatura usa XML próprio, assinatura digital (certificado qualificado/selo, via SISP), "Middleware" gratuito da DNRE instalado na rede do contribuinte, e modo de contingência offline (fontes secundárias, §14). Avaliar `kriolos/kriolos-efatura` (Java/Maven, Apache-2.0, modelo gerado dos XSD, XAdES) como candidato para o adaptador real — **não** para o v3.0. |
| SMTP | `EmailFiscalSender` sobre `JavaMailSender` | Opcional por configuração; sem SMTP a emissão **não** falha. |
| MinIO | `StorageService` existente | + `download`; sem `delete` para artefactos fiscais. |

---

## 14. Lacunas e confiança

| Área | Confiança | Nota |
|------|-----------|------|
| Pontos de integração no código (ficheiro:linha) | **ALTA** | Lidos diretamente. |
| Padrões (lock de linha, outbox, imutabilidade) | **ALTA** | Precedentes no próprio repositório; outbox corroborado por fontes públicas. |
| `spring.task.scheduling` com 1 thread por omissão | **ALTA** | Documentação Spring Boot + comentário de `SchedulingConfig.java`. |
| `SET LOCAL lock_timeout` vs hint JPA em PostgreSQL | **MÉDIA-BAIXA** | Verificar na fase A (não confirmado com a documentação Hibernate: Context7 e o proxy bloquearam o acesso). |
| `@Immutable` + UPDATE nativo de `cliente_id` | **MÉDIA** | Esperado funcionar (SQL nativo ignora o Hibernate); validar com IT na fase B. |
| Formato eFatura (tipos 01/02/05; IUD de 45 caracteres com ano de 2 dígitos, LED de 5, código aleatório de 10, dígito Luhn; repositório 1/2/3; série ≤10 alfanum.; numeração contínua sem lacunas por NIF+ano+LED+tipo; um LED por série; NC referencia a FR; middleware + XML assinado SHA-256; contingência offline) | **BAIXA-MÉDIA** | Só por **resumos de pesquisa** de fontes secundárias (WISEDAT, Edicom, Odoo) — `efatura.cv`, `saft.tst.efatura.cv`, `middleware.efatura.cv`, `wisedat.pt` e `edicom.pt` estão **bloqueados** por proxy neste ambiente (`EGRESS_BLOCKED`). Antes da fase D: ler o Manual Técnico v10.0 e os XSD diretamente. |
| Regras fiscais (IVA, isenções, retenção, arredondamento, regime) | **n/a** | Fora deste documento por decisão do marco; `RegraFiscalService` isola o ponto de entrada. |
| Topologia/custódia de certificados no SaaS | **n/a** | Pergunta aberta do marco de integração real. |

**Decisões que o roadmapper deve levar a discussão (não resolvidas aqui):** (1) lançar em produção com PDF/email de documentos sem validade fiscal; (2) NC reabre o honorário; (3) email de documento simulado a clientes reais — enviar ou segurar até à ligação real (a arquitetura suporta ambas, por `envio_email_automatico`); (4) moldes que recebem `faturas:view`; (5) cortar `TentativaComunicacao`.

---

## 15. Fontes

**Código (ALTA):** `backend/.../controllers/ResourceController.java` (`:138-142, :276-285, :834-957, :2917-2937, :3025-3081, :3156-3186, :3247-3262`), `models/Pagamento.java`, `models/Honorario.java:34`, `models/Tenant.java`, `models/Cliente.java`, `models/AuditLog.java`, `repositories/AuditLogRepository.java:44`, `ParecerSolicitacaoRepository.java:27`, `TenantRoleRepository.java:39`, `TenantRepository.java:15-24`, `controllers/PlatformAdminController.java:65-132`, `config/UserPrincipal.java:41-52`, `config/JwtAuthenticationFilter.java:58-91`, `config/SchedulingConfig.java`, `config/SecurityConfig.java:91`, `jobs/AlertasDiariosJob.java:66-81`, `services/StorageService.java:39-95`, `services/SetupService.java:154-298`, `seed/DatabaseSeeder.java:346-488, :532-535`, `seed/MigracaoPapeisRunner.java`, `backend/migrations/README.md`, `DEPLOYMENT.md` (Two-Stage Boot), `docker-compose.yml:52-74`, `.planning/PROJECT.md`, `.planning/milestones/v2.14-research/PITFALLS.md` (Pitfall 1), `web/src/lib/permissions.ts`, `web/src/lib/api.ts`, `web/src/hooks/use-financeiro.ts`, `web/src/app/(dashboard)/financeiro/[id]/page.tsx`, `web/src/components/shared/dashboard-shell.tsx`, `web/src/app/(dashboard)/settings/page.tsx`, `web/src/app/(dashboard)/plataforma/**`.

**Externas (MÉDIA/BAIXA — ver §14):**
- Spring Boot, Task Execution and Scheduling — https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
- eFatura CV, documentos fiscais / modelo conceitual — https://efatura.cv/docs/manual/documentos-fiscais/ , https://efatura.cv/docs/manual/modelo-conceitual/ (conhecidos só por resultados de pesquisa; domínio bloqueado)
- Manual Técnico v10.0 — https://www.saft.tst.efatura.cv/assets/files/manual-tecnico-da-fatura-eletronica-v10.0-81ac76da0d05ec36abdb626087cda762.pdf (não aberto)
- WISEDAT, regras de emissão / comunicação de DFE — https://www.wisedat.pt/regras-de-emissao-de-faturas-cabo-verde/ , https://www.wisedat.pt/kb/emissao-dfe-cabo-verde/
- Edicom, conformidade eFatura CV — https://edicom.pt/blog/como-cumprir-fatura-eletronica-cabo-verde
- Middleware eFatura CV — https://middleware.efatura.cv/docs/faqs/conceitos/
- Bibliotecas de referência — https://github.com/kriolos/kriolos-efatura (Java, XSD/XAdES), https://github.com/akira-io/laravel-efatura
- Transactional outbox + `FOR UPDATE SKIP LOCKED` — https://www.matthewswong.com/en/blog/transactional-outbox-pattern-postgres/ , https://www.rabinarayanpatra.com/snippets/java/outbox-publisher

---
*Architecture research for: faturação eletrónica eFatura CV em LexCV (milestone v3.0)*
*Researched: 2026-10-04*
