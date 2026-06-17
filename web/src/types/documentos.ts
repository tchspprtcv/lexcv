export interface Documento {
  id: string;
  tenant_id: string;
  processo_id?: string;
  cliente_id?: string;
  tipo?: string;
  confidencialidade?: string;
  versao: number;
  nome: string;
  filename: string;
  content_type: string;
  size: number;
  created_at: string;
}

export type DocumentoUploadResponse = Documento;

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
