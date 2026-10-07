import type {
  AmbienteFiscal,
  EstadoComunicacaoFiscal,
  RegimeIva,
  SerieFiscal,
  TipoDocumentoFiscal,
} from "@/types/faturacao";

/**
 * Phase 138 (SUBS-01..05): Tipos do domínio de faturação de subscrição da plataforma LexCV.
 */

export interface PlatformConfiguracaoFiscal {
  configurada: boolean;
  nif: string | null;
  firma: string | null;
  morada: string | null;
  localidade: string | null;
  pais: string;
  emailContacto: string | null;
  telefoneContacto: string | null;
  regimeIva: RegimeIva | null;
  motivoIsencaoCodigo: string | null;
  completa: boolean;
  ativa: boolean;
  documentosEmitidos: boolean;
  nifBloqueado: boolean;
  podeDesativar: boolean;
  envioEmailAutomatico: boolean;
  envioEmailAceitePorNome: string | null;
  envioEmailAceiteEm: string | null;
  smtpConfigurado: boolean;
}

export interface PlatformConfiguracaoFiscalPayload {
  nif: string;
  firma: string;
  morada: string;
  localidade: string;
  emailContacto: string;
  telefoneContacto: string;
  regimeIva: RegimeIva;
  motivoIsencaoCodigo: string | null;
}

export type MotivoNotaCredito = "ANULACAO_TOTAL" | "CORRECAO_VALOR" | "ERRO_DADOS_CLIENTE" | "OUTRO";

export interface RegistarPagamentoSubscricaoRequest {
  tenantId: string;
  valorPago: number;
  dataPagamento: string; // YYYY-MM-DD
  metodo: string;
  periodoInicio: string; // YYYY-MM-DD
  periodoFim: string; // YYYY-MM-DD
  plano?: string;
  chaveIdempotencia: string; // UUID
}

export interface CriarNotaCreditoSubscricaoRequest {
  motivoCodigo: MotivoNotaCredito;
  motivoTexto: string;
  valorTotal?: number;
  chaveIdempotencia: string; // UUID
}

export interface SubscricaoFaturaResponse {
  documentoId: string;
  pagamentoSubscricaoId: string;
  tipo: TipoDocumentoFiscal;
  serieCodigo: string;
  numero: number;
  numeroFormatado: string;
  dataEmissao: string;
  emitidoEm: string;
  adquirenteTenantId: string;
  adquirenteNome: string;
  adquirenteNif: string;
  totalBase: number;
  totalIva: number;
  totalDocumento: number;
  metodoPagamento: string;
  periodoInicio: string | null;
  periodoFim: string | null;
  plano: string | null;
  estadoComunicacao: EstadoComunicacaoFiscal;
}

export interface PlatformDocumentoFiscalFiltros {
  tipo?: TipoDocumentoFiscal;
  estado?: EstadoComunicacaoFiscal;
  de?: string;
  ate?: string;
  page?: number;
  size?: number;
}
