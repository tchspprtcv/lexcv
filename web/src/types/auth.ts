export type Role = "ADMIN" | "TECNICO" | "ADVOGADO" | "ASSISTENTE";

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  user: {
    id: string;
    nome: string;
  };
  access_token?: string;
  refresh_token?: string;
}

export interface MeResponse {
  id: string;
  tenant_id: string;
  nome: string;
  email: string;
  roles: Role[];
  avatar_url?: string;
  telefone?: string;
  ativo?: boolean;
  permissions: string[];
}
