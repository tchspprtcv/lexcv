export type DocumentoTipo = "NIF" | "CNI" | "PASSAPORTE" | "REG_COMERCIAL";

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

export interface ClienteAdvogadoUser {
  id: string;
  nome: string;
  email?: string;
  telefone?: string;
  numeroCedula?: string;
}

export interface Cliente {
  id: string;
  tenant_id: string;
  tipo?: "PARTICULAR" | "EMPRESA";
  numero_cliente?: string;
  avencado?: boolean;
  nome: string;
  nif: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: DocumentoTipo;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: DocumentoTipo;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
  created_at: string;
  procuracao_key?: string;
  descricao_caso?: string;
  documentos_entregues?: DocumentoEntregue[];
  documentos_a_tratar?: DocumentoATratar[];
  deslocacoes?: Deslocacao[];
  honorarios_propostos?: HonorariosPropostos;
  idade?: number;
  sexo?: string;
  nacionalidade?: string;
}

export interface ClienteCreateRequest {
  tipo?: "PARTICULAR" | "EMPRESA";
  avencado?: boolean;
  nome: string;
  nif: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: DocumentoTipo;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: DocumentoTipo;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
}

export interface ClienteUpdateRequest {
  tipo?: "PARTICULAR" | "EMPRESA";
  avencado?: boolean;
  nome?: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  documento_tipo?: DocumentoTipo;
  documento_numero?: string;
  ramo_atividade?: string;
  detalhes_adicionais?: string;
  documentoTipo?: DocumentoTipo;
  documentoNumero?: string;
  ramoAtividade?: string;
  detalhesAdicionais?: string;
  descricao_caso?: string;
  documentos_entregues?: DocumentoEntregue[];
  documentos_a_tratar?: DocumentoATratar[];
  deslocacoes?: Deslocacao[];
  honorarios_propostos?: HonorariosPropostos;
  descricaoCaso?: string;
  documentosEntregues?: DocumentoEntregue[];
  documentosATratar?: DocumentoATratar[];
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
