export interface Honorario {
  id: number;
  tenant_id: string;
  processo_id: string;
  valor_total: number;
  descricao?: string;
  data_acordo?: string;
  created_at: string;
}

export interface HonorarioCreateRequest {
  processo_id: string;
  valor_total: number;
  descricao?: string;
  data_acordo?: string;
}

export interface Pagamento {
  id: number;
  tenant_id: string;
  honorario_id: number;
  valor_pago: number;
  data_pagamento: string;
  metodo?: string;
}

export interface PagamentoCreateRequest {
  honorario_id: number;
  valor_pago: number;
  data_pagamento?: string;
  metodo?: string;
}

