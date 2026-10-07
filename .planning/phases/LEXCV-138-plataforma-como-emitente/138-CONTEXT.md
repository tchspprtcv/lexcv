# Phase 138: Plataforma como Emitente — Context & Architectural Decisions

## 1. Objectives & Scope
- **Goals**: A LexCV (a plataforma, através do tenant reservado `"LexCV"`) atua como emitente de Faturas-Recibo e Notas de Crédito de subscrição para os escritórios assinantes.
- **Requirements**:
  - `SUBS-01`: `PLATAFORMA_ADMIN` regista os dados fiscais da LexCV (NIF, firma, morada, regime) na consola `/plataforma`; a emissão de faturas de subscrição fica bloqueada enquanto estiverem incompletos.
  - `SUBS-02`: `PLATAFORMA_ADMIN` regista em `/plataforma` um pagamento de subscrição recebido de um escritório (valor, data, método, período coberto), o que emite na mesma operação uma Fatura-Recibo da LexCV para os dados fiscais do escritório.
  - `SUBS-03`: `PLATAFORMA_ADMIN` lista as faturas de subscrição emitidas, descarrega o PDF e emite Notas de Crédito sobre elas.
  - `SUBS-04`: O escritório recebe a fatura de subscrição por email e consulta as suas faturas de subscrição em Definições (só leitura), sem acesso a documentos emitidos a outros escritórios.
  - `SUBS-05`: As séries e a numeração da LexCV são independentes das de qualquer escritório.

## 2. Architectural Decisions
1. **Tenant Reservado "LexCV"**:
   - Já existe e é criado/mantido por `DatabaseSeeder.seedTenantPlataforma()`.
   - A configuração fiscal da plataforma reside em `t_configuracao_fiscal` com `tenant_id = lexcvTenant.getId()`.
   - As séries da plataforma residem em `t_serie_fiscal` com `tenant_id = lexcvTenant.getId()`. Assim, as séries e sequências de numeração são 100% isoladas de qualquer escritório (SUBS-05).
2. **Entidade `PagamentoSubscricao` (`t_pagamento_subscricao`)**:
   - Regista o pagamento de subscrição recebido da entidade escritório (`adquirente_tenant_id`).
   - Guarda: `id`, `adquirente_tenant_id`, `valor_pago`, `data_pagamento`, `metodo`, `periodo_inicio`, `periodo_fim`, `plano`, `criado_por_id`, `created_at`.
3. **Extensão de `DocumentoFiscal` (`t_documento_fiscal`)**:
   - Suporta tanto faturas de escritório (com `cliente_id`, `processo_id`, `honorario_id`, `pagamento_id`) como faturas de subscrição da plataforma (com `adquirente_tenant_id`, `pagamento_subscricao_id`).
   - Para faturas de subscrição:
     - `tenant_id` = ID do tenant LexCV (emitente).
     - `adquirente_tenant_id` = ID do tenant do escritório adquirente.
     - `adquirente_nif`, `adquirente_nome`, `adquirente_morada`, `adquirente_localidade` = snapshot dos dados fiscais / cadastrais do escritório no momento da emissão.
     - `cliente_id`, `processo_id`, `honorario_id`, `pagamento_id` são nulos.
     - `pagamento_subscricao_id` guarda a referência ao pagamento de subscrição.
4. **Isolamento Multi-tenant Rigoroso**:
   - `PLATAFORMA_ADMIN` tem acesso à consola `/plataforma/faturacao` para gerir dados fiscais da LexCV, registar pagamentos de subscrição, listar faturas e emitir NCs da plataforma.
   - O escritório assinante autenticado com `financeiro:view` só consegue consultar faturas de subscrição através do endpoint dedicado `/api/v1/faturacao/subscricoes` onde a consulta filtra explicitamente por `adquirente_tenant_id = currentTenantId AND tenant_id = lexcvTenantId`.
   - Nenhum escritório consegue consultar faturas de outro escritório nem faturas emitidas por outro emitente.
5. **Reutilização de Motores Existentes**:
   - A emissão de FR/NC de subscrição reutiliza a geração de IUD (Phase 136), snapshot de linhas, motor de cálculo fiscal (Phase 134), geração de PDF (OpenHTMLtoPDF, Phase 137) e outbox de email (Phase 137).
