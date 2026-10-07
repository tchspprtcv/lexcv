# Phase 138 Plan 06 Summary: Consulta e Descarga de Subscrições pelo Escritório (SUBS-04)

## Realizações
1. **Serviço `FaturacaoSubscricaoEscritorioService`**:
   - `listar(adquirenteTenantId, page, size)`: consulta paginada restringindo estritamente `adquirente_tenant_id = adquirenteTenantId AND tenant_id = lexcvTenantId`.
   - `detalhe(adquirenteTenantId, documentoId)`: detalhe completo da fatura de subscrição ou nota de crédito do escritório, retornando 404 se pertencer a outro escritório ou não tiver sido emitida pela plataforma.
   - `descarregarPdf(adquirenteTenantId, autor, documentoId)`: validação prévia de fronteira multi-tenant antes de delegar para `DescargaDocumentoFiscalService`.
   - `descarregarXml(adquirenteTenantId, autor, documentoId)`: validação prévia de fronteira multi-tenant antes de delegar para `DescargaDocumentoFiscalService`.
2. **Controlador `FaturacaoSubscricaoController`**:
   - `GET /api/v1/faturacao/subscricoes`: listagem das faturas de subscrição do escritório autenticado.
   - `GET /api/v1/faturacao/subscricoes/{id}`: detalhe da fatura de subscrição.
   - `GET /api/v1/faturacao/subscricoes/{id}/pdf`: download do PDF.
   - `GET /api/v1/faturacao/subscricoes/{id}/xml`: download do XML.
   - Todos os métodos protegidos com `@PreAuthorize("hasAuthority('financeiro:view')")` e extração de `tenantId` unicamente a partir do `UserPrincipal` do contexto de segurança.
3. **Testes Unitários**:
   - `FaturacaoSubscricaoEscritorioServiceTest` (4/4 testes passando com garantia de isolamento multi-tenant e 404 seguro).
   - `FaturacaoSubscricaoControllerTest` (4/4 testes passando com validação de segurança e delegação).

## Verificação
- Suite de testes unitários executada com 100% de aprovação (8/8 testes passando).
