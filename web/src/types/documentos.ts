/**
 * Forma devolvida pela API Spring em /api/v1/documentos.
 *
 * Os nomes seguem o que vem do backend (camelCase, vocabulário do domínio em
 * português). A versão anterior deste tipo descrevia a API mock de
 * `src/app/_api-backup/` — snake_case, com `size`, `created_at`, `filename` e
 * `content_type` — campos que o backend real nunca envia. Como o TypeScript dava
 * o tipo por bom, a divergência só aparecia em execução: a página de Documentos
 * rebentava em `size.toLocaleString()` assim que existisse uma linha para
 * desenhar, e `processo_id`/`cliente_id` ficavam sempre por preencher.
 */
export interface Documento {
  id: string;
  tenantId: string;
  processoId?: string;
  clienteId?: string;
  nome: string;
  tipo?: string;
  confidencialidade?: string;
  versao: number;
  /** Chave do objeto no storage: `<tenantId>/<documentoId>/<ficheiro>`. */
  caminhoArquivo?: string;
  tamanho: number;
  mimeType?: string;
  createdAt: string;
}

export type DocumentoUploadResponse = Documento;

/**
 * Filtros do formulário da página de Documentos. São nomes do lado do pedido, não
 * da resposta — `use-documentos` traduz-os para o que cada endpoint espera.
 */
export interface DocumentosListFilters {
  processo_id?: string;
  cliente_id?: string;
}

export interface DocumentoUploadPayload {
  file: File;
  processo_id?: string;
  cliente_id?: string;
  tipo?: string;
  confidencialidade?: string;
  replace_id?: string;
  nome?: string;
}
