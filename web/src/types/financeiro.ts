import type { DocumentoFiscalRef } from "@/types/faturacao";

export interface Honorario {
  id: number;
  processoId: string;
  valorTotal: number | null;
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
  /** Phase 134 (D-19): Fatura-Recibo do pagamento; null/ausente num pagamento sem documento. */
  documentoFiscal?: DocumentoFiscalRef | null;
  /**
   * Phase 135 (NCRD-03): quando este pagamento é o estorno (negativo) de uma Nota de Crédito, a
   * referência dessa NC; `documentoFiscal` é então null. A UI mostra "Estorno (NC n.º …)".
   */
  estorno?: DocumentoFiscalRef | null;
}

export interface PagamentoCreateRequest {
  honorarioId: number;
  valorPago: number;
  dataPagamento?: string;
  metodo?: string;
  /** Phase 134: só com a faturação ativa (opcionais, para o pedido legado ficar igual). */
  retencaoPercentagem?: number;
  chaveIdempotencia?: string;
}
