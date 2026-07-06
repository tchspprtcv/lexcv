# Phase 79: Documentos Entregues — Upload Real - Context

**Gathered:** 2026-07-06
**Status:** Ready for planning

<domain>
## Phase Boundary

O separador "Documentos Entregues" (hoje placeholder "Em breve", com a secção antiga de texto ainda vivendo dentro do tab "Dados") ganha conteúdo real: upload de ficheiros associados ao cliente via o sistema genérico de `Documento`, reutilizando toda a infraestrutura já existente (entidade, endpoints de upload/download/delete, hooks, `FileDropZone`). A secção antiga de texto (descrição+data, sem ficheiro) é removida por completo do tab "Dados" — sem migração de dados, coluna fica órfã. Não cobre alterações a `Documentos a Tratar`/`Deslocações` (já concluído na Phase 78) nem à página standalone `/documentos`.

</domain>

<decisions>
## Implementation Decisions

### Endpoint de Listagem & Fonte do Combobox de Tipo
- Novo endpoint backend `GET /clientes/{id}/documentos` — espelha exatamente o padrão já existente `GET /processos/{id}/documentos` (valida tenant do cliente, `documentoRepository.findByTenantIdAndClienteId`).
- O hook frontend `useDocumentos({cliente_id})` (já existente em `web/src/hooks/use-documentos.ts`, já constrói a query string certa) é reutilizado tal e qual — só muda o endpoint que a query efetivamente chama, passando a apontar para a rota nova `/clientes/{id}/documentos` (o `GET /documentos` genérico hoje ignora os query params `cliente_id`/`processo_id`, confirmado por leitura direta do `ResourceController` — `listDocumentos()` faz só `findByTenantId`).
- Fonte da lista de "tipos já usados" para o combobox: extraídos client-side (valores `tipo` distintos) a partir dos documentos já carregados para este cliente (mesma lista obtida pelo `useDocumentos`) — sem endpoint novo dedicado a "tipos".
- Combobox implementado com `<input list="...">` + `<datalist>` nativo do HTML.

### Remoção dos Dados Antigos & RBAC
- A secção antiga "Documentos Entregues" (lista de texto, hoje dentro do tab "Dados") é removida por completo: JSX, estado local (`documentosEntregues`, `newDocEntre`, `addDocEntreModal`) e a entrada correspondente no `useEffect` de reset de diálogos (Phase 76/78) deixam de existir.
- O campo `documentos_entregues` deixa de ser enviado no payload de "Guardar" — o backend não recebe mais este campo do frontend; a coluna/campo backend fica órfã, sem processo de migração (mesmo padrão usado para `dados_tipo` na v2.7).
- Permissão de upload/remoção de documentos no novo separador usa `documentos:edit`/`documentos:view` (scope real que o backend já aplica via `@PreAuthorize` nos endpoints de Documento) — NÃO usa `clientes:edit`, para manter as duas camadas (frontend/backend) alinhadas.
- O botão "Adicionar"/upload fica gated tanto pelo toggle `editable` da ficha (Phase 75) como pelo RBAC: `editable && permissions.can.edit("documentos")`.

### UI de Upload & Listagem no Separador
- Upload via `Dialog` "Adicionar" contendo `FileDropZone` + combobox de tipo (datalist) + `useUploadDocumentoComProgresso` (barra de progresso) — mesmo padrão do resto da ficha (Documentos a Tratar/Deslocações usam Dialog).
- Layout da lista de documentos já carregados: lista compacta `ul`/`li` (nome do ficheiro + tipo + tamanho/data + botões download/remover) — não a tabela dual-view completa da página `/documentos` standalone.
- Download por link direto (`href="/api/v1/documentos/{id}/download"`), mesmo padrão já usado em `web/src/app/(dashboard)/documentos/page.tsx`.
- Remover um documento carregado pede confirmação via `window.confirm`, mesmo padrão usado nos outros "remover" da ficha.

### Claude's Discretion
- Nome exato de variáveis/estado interno do novo componente/tab.
- Texto exato de mensagens de erro/sucesso do upload (copy livre, tom institucional em português, consistente com o resto da app).
- Se o combobox de tipo tem algum valor por defeito (ex.: vazio, ou reutilizar o fallback `"ANEXO"` já usado no backend quando `tipo` não é fornecido).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Documento` entity (`backend/src/main/java/com/lexcv/models/Documento.java`) — já suporta `clienteId` (nullable), `tipo` (String livre), `caminhoArquivo`, `mimeType`, `tamanho`, `nome`, `confidencialidade`.
- `POST /documentos/upload` (`ResourceController`) — já aceita `clienteId` como `@RequestParam` opcional; nenhuma alteração de backend necessária além do novo endpoint de listagem.
- `GET /processos/{id}/documentos` (`ResourceController`) — padrão exato a replicar para `GET /clientes/{id}/documentos`.
- `useDocumentos({cliente_id})`, `useUploadDocumento`, `useUploadDocumentoComProgresso`, `useDeleteDocumento` (`web/src/hooks/use-documentos.ts`) — já existem e são reutilizáveis sem alteração de assinatura.
- `FileDropZone` (`web/src/components/shared/file-drop-zone.tsx`) — já usado em `documentos/novo/page.tsx` e na Procuração do cliente.
- `documentos/novo/page.tsx` — padrão de referência para upload com preview, embora hoje use `Input` livre para tipo/cliente_id (a versão embutida na ficha simplifica: cliente_id fixo ao cliente atual, tipo vira combobox).
- `documentos/page.tsx` — padrão de referência para download (link direto) e remover (`useDeleteDocumento` + confirmação).

### Established Patterns
- `Dialog` "Adicionar" + estado local é o padrão já estabelecido nesta ficha para Documentos a Tratar/Deslocações (Phase 78) — replicar a mesma estrutura de Dialog/Trigger/Content/Footer.
- Permissões `permissions.can.edit("documentos")`/`permissions.can.view("documentos")` já seguem o padrão `hasScopedPermission` espelhado do backend (mesmo mecanismo usado para `processos:view`/`pareceres:view` na Phase 77).

### Integration Points
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — branch `tab === "documentosEntregues"` (hoje `PlaceholderEmBreve`) ganha o novo conteúdo; a secção antiga de texto dentro do tab "dados" é removida.
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — novo endpoint `GET /clientes/{id}/documentos`.

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica além do já documentado — seguir o padrão de Dialog+lista já estabelecido nesta ficha (Phase 78) e o padrão de upload já estabelecido em `/documentos/novo`.

</specifics>

<deferred>
## Deferred Ideas

- Migração de dados antigos de "documentos entregues" — corte limpo deliberado, decisão da milestone (v2.8).
- Endpoint dedicado de listagem de "tipos" — não necessário, extração client-side é suficiente.

</deferred>
