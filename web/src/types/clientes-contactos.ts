export interface ClienteContacto {
  id: string;
  tenant_id: string;
  cliente_id: string;
  tipo?: string;
  valor: string;
  created_at: string;
}

export interface ClienteContactoCreateRequest {
  tipo?: string;
  valor: string;
}

export interface ClienteContactoUpdateRequest {
  tipo?: string;
  valor?: string;
}

