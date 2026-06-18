export interface Honorario {
  id: number;
  processoId: string;
  valorTotal: number;
  totalPago: number; // campo computado pelo backend (soma de pagamentos)
  descricao?: string;
  dataAcordo?: string;
}

export interface HonorarioCreateRequest {
  processoId: string;
  valorTotal: number;
  descricao?: string;
  dataAcordo?: string;
}

export interface HonorarioUpdateRequest {
  valorTotal: number;
  descricao?: string;
  dataAcordo?: string;
}

export interface Pagamento {
  id: number;
  honorarioId: number;
  valorPago: number;
  dataPagamento: string;
  metodo?: string;
}

export interface PagamentoCreateRequest {
  honorarioId: number;
  valorPago: number;
  dataPagamento?: string;
  metodo?: string;
}
