# Phase 35: Processos - Governanca Documental e Retencao - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Governanca documental por processo com classificacao, versao, confidencialidade, retencao e legal hold.
</domain>

<decisions>
## Implementation Decisions

### Classificação e Confidencialidade
- Categorias de documentos: Lista fixa base (ex: Peça processual, Prova, etc) — simples e consistente para o MVP
- Níveis de confidencialidade: Público, Interno, Restrito — liga com o RBAC existente

### Versionamento de Documentos
- Upload de nova versão: Substitui ficheiro ativo e incrementa nº de versão (histórico anterior não visível no UI MVP) — simples de implementar

### Retenção e Legal Hold
- Aplicação do Legal Hold: Flag no Processo (bloqueia apagar qualquer documento do processo) — mais seguro e fácil de gerir
- Política de Retenção: Exibir Data de Retenção/Aviso no UI (sem eliminação automática) — reduz risco no MVP

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useDocumentos`, `useUploadDocumento`, `useDeleteDocumento`, `useDownloadDocumento` hooks no frontend (`src/hooks/use-documentos.ts`)
- `Documento` JPA entity no backend (`com.lexcv.models.Documento`)

### Established Patterns
- API usa multipart/form-data para upload
- Frontend usa TanStack Query para fetch/mutations

### Integration Points
- Aba/Seção de documentos no detalhe do processo e listagem
- Modelos backend: adição de `confidencialidade`, expansão do enum/string `tipo` para `categoria`, e campos `legal_hold`, `data_retencao` no processo.
</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches
</specifics>

<deferred>
## Deferred Ideas

- Categorias dinâmicas de documentos (CRUD para admin) foi adiado; ficamos por uma lista fixa base no MVP.
- Funcionalidade de "soft-delete" automático diário por retenção adiado para fase futura; MVP terá apenas retenção manual suportada por aviso visual/calculado de data de retenção no UI.
</deferred>
