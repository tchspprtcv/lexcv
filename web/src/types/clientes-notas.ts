export interface ClienteNota {
  id: string;
  tenant_id: string;
  cliente_id: string;
  titulo?: string;
  conteudo: string;
  created_at: string;
  updated_at?: string;
}

export interface ClienteNotaCreateRequest {
  titulo?: string;
  conteudo: string;
}

export interface ClienteNotaUpdateRequest {
  titulo?: string;
  conteudo?: string;
}

