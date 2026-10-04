# Phase 133: Fundação Fiscal - Context

**Gathered:** 2026-10-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Cada escritório regista os seus dados fiscais e ativa a faturação de forma segura; o sistema passa a ter
parâmetros fiscais com vigência (IVA 15%, retenção sugerida 20%) e um serviço de numeração sequencial
sem lacunas por série (emitente, tipo, ano, ambiente). Nenhum documento fiscal é emitido nesta fase
(isso é a 134) — o `NumeracaoService` é entregue e provado por teste de integração, sem consumidor de
produção ainda. Escritórios que não ativem a faturação não veem nenhuma mudança de comportamento.
Requisitos: CFG-01..06.

</domain>

<decisions>
## Implementation Decisions

### Dados fiscais e ecrã
- Novo separador "Faturação" em Definições (`web/src/app/(dashboard)/settings/page.tsx`, `TabId`), visível e editável só com `financeiro:manage` (backend `@PreAuthorize("hasAuthority('financeiro:manage')")` + frontend `hasScopedPermission` — ambas as camadas concordam)
- Tabela nova `t_configuracao_fiscal`, 1:1 com o tenant (`tenant_id` UNIQUE), sem ALTER em `t_tenant`
- Regime de IVA: `NORMAL` ou `ISENTO`; quando `ISENTO`, código de motivo obrigatório escolhido da lista oficial `TaxExemptionReasonCode` do XSD eFatura (21 códigos, guardados como dado/enum com rótulo PT)
- Campos: NIF (9 dígitos, primeiro 1–9, validado em backend e frontend), firma, morada (≤100 caracteres), localidade, email e telefone de contacto; país fixo Cabo Verde

### Ativação
- Botão "Ativar faturação" com diálogo de confirmação que explica a irreversibilidade após o primeiro documento; só aceite com dados fiscais completos (backend recusa com 409/422 e código de erro)
- Antes do primeiro documento emitido a faturação pode ser desligada; depois do primeiro documento não (o backend verifica a existência de documento — na 133 ainda não existem, por isso o gancho é uma verificação contra a futura tabela/contador de séries: "existe série com ultimo_numero > 0")
- Depois do primeiro documento só o NIF fica bloqueado; restantes campos editáveis e só afetam documentos futuros (os documentos guardam snapshot, fase 134)
- Ativação, desativação e alterações aos dados fiscais registadas na auditoria imutável existente do escritório (padrão da Phase 128, frases em português de Cabo Verde)

### Parâmetros e numeração
- Tabela global `t_parametro_fiscal` (código, valor decimal, vigente_desde, sem tenant — é regra legal comum), semeada por upsert não-destrutivo com `IVA_TAXA_NORMAL=15` e `RETENCAO_SUGERIDA=20`; serviço lê o valor vigente numa data; sem UI de edição no v3.0
- `t_serie_fiscal` por (tenant_id, tipo_documento, ano, ambiente), criada automaticamente (`INSERT ... ON CONFLICT DO NOTHING`); código gerado, com prefixo `SIM-` em ambiente simulado (ex.: `SIM-FR-2026`, `SIM-NC-2026`); `led_codigo` anulável (resolvido na 136)
- `NumeracaoService.proximoNumero(...)` com `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`, precedente `ParecerSolicitacaoRepository.java:27`) na transação do chamador (`Propagation.MANDATORY`), `lock_timeout` curto, `UNIQUE(tenant_id, serie_id, numero)` como rede de segurança na tabela de documentos da 134; ano calculado em `Atlantic/Cape_Verde` via `Clock` injetável; numeração reinicia a 1 por ano
- Prova: teste de integração Testcontainers PostgreSQL com chamadas concorrentes reais (padrão `ParecerVersaoConcorrenciaIT`)
- O separador mostra as séries só para leitura (tipo, ano, código, último número)

### Interruptor de email
- No separador Faturação, desligado por omissão; ligar abre diálogo com checkbox obrigatória "Compreendo que os documentos simulados não têm validade fiscal"
- Guarda `envio_email_aceite_por` (user id) e `envio_email_aceite_em`; evento na auditoria
- Só pode ser ligado com a faturação ativa; o efeito (envio real) chega na fase 137

### Claude's Discretion
- Nomes exatos de DTOs/endpoints (sugestão: `GET/PUT /api/v1/faturacao/configuracao`, `POST /api/v1/faturacao/ativar`, `POST /api/v1/faturacao/desativar`, `PUT /api/v1/faturacao/email-automatico`, `GET /api/v1/faturacao/series`), divisão em planos, estrutura de serviço
- Constante partilhada do tenant reservado da plataforma, se necessária para validações futuras

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/settings/page.tsx` — separadores manuais (`TabId`), `auditoria-tab.tsx` como modelo de separador
- `web/src/lib/permissions.ts#hasScopedPermission`, `web/src/lib/api.ts#apiFetch`, hooks `web/src/hooks/use-*.ts`
- `AuditLog`/`AuditLogRepository` imutáveis e `AuditoriaRbacService` (Phase 128) para eventos de auditoria
- `ParecerSolicitacaoRepository.java:27` (lock pessimista), `ParecerVersaoConcorrenciaIT` (Testcontainers)
- `RecusaTransacional` para recusas dentro de `@Transactional`; `GlobalExceptionHandler`

### Established Patterns
- Multi-tenancy: `getTenantId()` do `UserPrincipal`; nunca `tenant_id` em URL
- Sem migration runner: `ddl-auto update` + script idempotente em `backend/migrations/` + linha no README no mesmo commit
- Catálogo de permissões em `t_permission`; esta fase NÃO cria permissões novas
- NIF de cliente já validado (v2.7) — reutilizar a regra, apertando o primeiro dígito 1–9

### Integration Points
- `SecurityConfig` / `@EnableMethodSecurity`; novo controller `FaturacaoController` sob `/api/v1/faturacao`
- `DatabaseSeeder` para semear `t_parametro_fiscal` (upsert) e dar NIF válido ao tenant demo (atual `000000000` é recusado pelo IUD)
- `AlertasDiariosJob.java:66` — precedente de `ZoneId.of("Atlantic/Cape_Verde")`

</code_context>

<specifics>
## Specific Ideas

- Pesquisa: `.planning/research/SUMMARY.md` (Phase 133, C1, C7, C9) e `ARCHITECTURE.md` §2–§3
- Valores semeados são parâmetros, nunca constantes (`0.15` proibido em código)

</specifics>

<deferred>
## Deferred Ideas

- UI de edição de parâmetros fiscais pela plataforma — futuro
- Dados fiscais da plataforma LexCV — fase 138

</deferred>
