# Phase 138 Plan 05 Summary: Consulta e Descarga de Documentos da Plataforma (SUBS-03)

## Realizações
1. **Serviço `PlatformDocumentoFiscalService`**:
   - `listarDocumentos(tipo, estado, de, ate, page, size)`: lista paginada de todos os documentos fiscais emitidos sob o tenant reservado `LexCV`.
   - `obterDocumento(id)`: consulta detalhada com linhas, histórico de comunicações, notas de crédito e entregas de email associadas.
   - `descarregarPdf(autor, id)`: obtenção / geração a pedido de PDF via MinIO com auditoria MANDATORY (`auditoriaFiscalService.registarDescarga`).
   - `descarregarXml(autor, id)`: obtenção de XML assinado com auditoria MANDATORY.
2. **Endpoints em `PlatformAdminController`**:
   - `GET /api/v1/platform/documentos-fiscais` (paginado e com filtros opcionais).
   - `GET /api/v1/platform/documentos-fiscais/{id}`.
   - `GET /api/v1/platform/documentos-fiscais/{id}/pdf`.
   - `GET /api/v1/platform/documentos-fiscais/{id}/xml`.
   - Todos os endpoints protegidos pelo gate de classe `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")`.
3. **Testes Unitários**:
   - `PlatformDocumentoFiscalServiceTest` (4/4 testes passando com delegação e isolamento corretos).
   - `PlatformAdminControllerTest` e `PlatformAdminControllerMoldesTest` atualizados e com 100% de aprovação.

## Verificação
- Suite de testes de controlador e serviços de documentos de plataforma executada com sucesso total (56/56 testes passando).
