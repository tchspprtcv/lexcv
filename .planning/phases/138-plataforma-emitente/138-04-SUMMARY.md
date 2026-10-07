# Phase 138 Plan 04 Summary: Emissão de Nota de Crédito sobre Subscrição (SUBS-03, SUBS-05)

## Realizações
1. **DTO `CriarNotaCreditoSubscricaoRequest`**:
   - Campos: `motivoCodigo` (`MotivoNotaCredito`), `motivoTexto` (`@NotBlank`, max 200), `valorTotal` (opcional para crédito parcial ou total), `chaveIdempotencia` (`UUID`).
2. **Serviço `SubscricaoNotaCreditoService`**:
   - Validação da Fatura-Recibo de origem sob o tenant reservado `LexCV`.
   - Garantia de que a origem é do tipo `TipoDocumentoFiscal.FR`.
   - Verificação de idempotência por `chaveIdempotencia` e `tenant_id`.
   - Cálculo e validação do saldo creditável remanescente (evitando sobre-creditação).
   - Numeração sequencial atómica sob lock na série `NC` (`numeracaoService.proximoNumero`).
   - Registo contabilístico de estorno (`PagamentoSubscricao` com `valorPago` negativo).
   - Persistência imutável de `DocumentoFiscal` do tipo `NC` e sua respetiva linha descritiva.
   - Registo em outbox `ComunicacaoFiscal` com estado inicial `PENDENTE`.
   - Registo de evento de auditoria (`auditoriaFiscalService.registarEmissaoNotaCredito`).
3. **Endpoint `POST /api/v1/platform/documentos-fiscais/{id}/notas-credito`**:
   - Exposto em `PlatformAdminController` sob autorização restrita `PLATAFORMA_ADMIN`.
4. **Testes Unitários**:
   - `SubscricaoNotaCreditoServiceTest` (100% dos testes passando: caminho feliz e validação de limite de crédito).
   - Atualizados testes de mock e controllers para alinhamento com nova injeção de dependências.

## Verificação
- Todos os testes de unidade de faturacao de plataforma (`SubscricaoFaturadaServiceTest`, `SubscricaoNotaCreditoServiceTest`, `PlatformFaturacaoConfigServiceTest`, `PlatformAdminControllerTest`, `PlatformAdminControllerMoldesTest`) executados com sucesso (61/61 testes passando).
