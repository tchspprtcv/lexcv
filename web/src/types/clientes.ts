export interface DadosTipoParticular {
  idade?: number;
  sexo?: string;
  nacionalidade?: string;
}

export interface DadosTipoEmpresa {
  nome_comercial?: string;
  sede?: string;
  representante_legal?: string;
  cargo?: string;
}

export interface Cliente {
  id: string;
  tenant_id: string;
  tipo?: "PARTICULAR" | "EMPRESA";
  numero_cliente?: string;
  avencado?: boolean;
  dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;
  nome: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: string;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: string;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
  created_at: string;
}

export interface ClienteCreateRequest {
  tipo?: "PARTICULAR" | "EMPRESA";
  avencado?: boolean;
  dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;
  nome: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: string;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: string;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
}

export interface ClienteUpdateRequest {
  tipo?: "PARTICULAR" | "EMPRESA";
  avencado?: boolean;
  dados_tipo?: DadosTipoParticular | DadosTipoEmpresa;
  nome?: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: string;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: string;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
}

export interface ClienteContaCorrenteResponse {
  cliente_id: string;
  saldo: number;
  updated_at: string;
}

export type ClientesListFilters = {
  q?: string;
  nome?: string;
  nif?: string;
  tipo?: string;
  ativo?: boolean;
  localidade?: string;
  createdFrom?: string;
  createdTo?: string;
};
