// Tipos do domínio de faturação (aba "Faturação" em /settings, Phase 133). Espelham os DTOs do
// backend em /api/v1/faturacao (configuração fiscal, séries, motivos de isenção, envio
// automático de email). Campos como `completa`, `nifBloqueado` e `podeDesativar` são computados
// no SERVIDOR e não devem ser re-derivados no cliente.

export type RegimeIva = "NORMAL" | "ISENTO";

export type TipoDocumentoFiscal = "FR" | "NC";

export type AmbienteFiscal = "SIMULADO";

export interface ConfiguracaoFiscal {
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
}

export interface ConfiguracaoFiscalPayload {
  nif: string;
  firma: string;
  morada: string;
  localidade: string;
  emailContacto: string;
  telefoneContacto: string;
  regimeIva: RegimeIva;
  motivoIsencaoCodigo: string | null;
}

export interface EmailAutomaticoPayload {
  ligado: boolean;
  aceiteDeclaracao?: boolean;
}

export interface SerieFiscal {
  tipoDocumento: TipoDocumentoFiscal;
  tipoDocumentoRotulo: string;
  ano: number;
  codigo: string;
  ultimoNumero: number;
  ambiente: AmbienteFiscal;
  ambienteRotulo: string;
}

export interface MotivoIsencao {
  codigo: string;
  descricao: string;
  mencao: string;
}

export type CodigoErroFaturacao =
  | "MOTIVO_ISENCAO_INVALIDO"
  | "NIF_BLOQUEADO"
  | "NIF_JA_REGISTADO"
  | "CONFIGURACAO_FISCAL_INCOMPLETA"
  | "CONFIGURACAO_FISCAL_CONCORRENTE"
  | "FATURACAO_JA_EMITIU"
  | "FATURACAO_DESLIGADA"
  | "DECLARACAO_NAO_ACEITE";
