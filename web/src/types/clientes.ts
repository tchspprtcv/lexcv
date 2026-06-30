export interface DocumentoEntregue {
  descricao: string;
  data?: string;
}

export interface DocumentoATratar {
  descricao: string;
}

export interface Deslocacao {
  descricao: string;
  local?: string;
  data?: string;
}

export interface HonorariosPropostos {
  total?: number;
  totalPorExtenso?: string;
  previsao?: string;
}

export interface Cliente {
  id: string;
  tenant_id: string;
  tipo?: string;
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
  descricao_caso?: string;
  documentos_entregues?: DocumentoEntregue[];
  documentos_a_tratar?: DocumentoATratar[];
  deslocacoes?: Deslocacao[];
  honorarios_propostos?: HonorariosPropostos;
}

export interface ClienteCreateRequest {
  tipo?: string;
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
  tipo?: string;
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
  descricao_caso?: string;
  descricaoCaso?: string;
  documentosEntregues?: DocumentoEntregue[];
  documentosATratar?: DocumentoATratar[];
  deslocacoes?: Deslocacao[];
  honorariosPropostos?: HonorariosPropostos;
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
