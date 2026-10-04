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
  | "CONFIGURACAO_FISCAL_INCOMPLETA"
  | "CONFIGURACAO_FISCAL_CONCORRENTE"
  | "FATURACAO_JA_EMITIU"
  | "FATURACAO_DESLIGADA"
  | "DECLARACAO_NAO_ACEITE"
  // Phase 134 -- emissão da Fatura-Recibo e guardas de eliminação.
  | "DATA_PAGAMENTO_RETROATIVA"
  | "ADQUIRENTE_INCOMPLETO"
  | "METODO_PAGAMENTO_INVALIDO"
  | "RETENCAO_INVALIDA"
  | "VALOR_PAGO_INVALIDO"
  | "CHAVE_IDEMPOTENCIA_OBRIGATORIA"
  | "HONORARIO_OBRIGATORIO"
  | "CHAVE_REUTILIZADA"
  | "PROCESSO_ALTERADO_TENTE_NOVAMENTE"
  | "DATA_EMISSAO_ALTERADA"
  | "FATURACAO_OCUPADA"
  | "PAGAMENTO_FATURADO"
  | "CLIENTE_COM_DOCUMENTOS_FISCAIS"
  | "PROCESSO_COM_DOCUMENTOS_FISCAIS"
  | "HONORARIO_COM_DOCUMENTOS_FISCAIS"
  | "DOCUMENTO_FISCAL_NAO_ENCONTRADO";

// ---------------------------------------------------------------------------------------------
// Phase 134 -- Fatura-Recibo nos honorários. Espelham os DTOs de DocumentoFiscalController e da
// lista de pagamentos (Jackson: BigDecimal -> number, LocalDate -> "AAAA-MM-DD", Instant -> ISO,
// UUID -> string). Os valores vêm SEMPRE calculados pelo backend (D-01): o cliente só os mostra.

/** Nomes do enum `MetodoPagamento` do backend (o rótulo vem em `METODOS_PAGAMENTO`). */
export type MetodoPagamento = "DINHEIRO" | "TRANSFERENCIA" | "CHEQUE" | "CARTAO" | "OUTRO";

export type EstadoComunicacaoFiscal = "PENDENTE";

/** GET /faturacao/estado-emissao (financeiro:view). */
export interface EstadoEmissao {
  ativa: boolean;
  ambiente: AmbienteFiscal | null;
  /** RETENCAO_SUGERIDA vigente, em percentagem; null quando não há parâmetro. */
  taxaRetencaoSugerida: number | null;
}

/** POST /faturacao/pre-visualizacao (financeiro:edit) -- nada é gravado. */
export interface PreVisualizacaoFatura {
  tipo: TipoDocumentoFiscal;
  tipoRotulo: string;
  ambiente: AmbienteFiscal;
  clienteId: string;
  adquirenteNome: string;
  adquirenteNif: string;
  adquirenteMorada: string;
  adquirenteLocalidade: string | null;
  descricaoLinha: string;
  regimeIva: RegimeIva;
  taxaIva: number | null;
  motivoIsencaoCodigo: string | null;
  motivoIsencaoDescricao: string | null;
  motivoIsencaoMencao: string | null;
  base: number;
  iva: number;
  taxaRetencao: number | null;
  retencao: number;
  total: number;
  liquidoRecebido: number;
  metodo: MetodoPagamento;
  metodoRotulo: string;
  dataEmissao: string;
}

/** Referência curta de um documento (lista de pagamentos, resposta do registo). */
export interface DocumentoFiscalRef {
  id: string;
  numeroFormatado: string;
}

export interface DocumentoFiscalResumo {
  id: string;
  numeroFormatado: string;
  tipo: TipoDocumentoFiscal;
  tipoRotulo: string;
  ambiente: AmbienteFiscal;
  dataEmissao: string;
  clienteId: string;
  adquirenteNome: string;
  adquirenteNif: string;
  totalDocumento: number;
  estadoComunicacao: EstadoComunicacaoFiscal | null;
}

export interface DocumentoFiscalLinha {
  numeroLinha: number;
  descricao: string;
  quantidade: number;
  precoUnitario: number;
  valorBase: number;
  taxaIva: number | null;
  valorIva: number;
  motivoIsencaoCodigo: string | null;
  taxaRetencao: number | null;
  valorRetencao: number;
  totalLinha: number;
}

/** GET /documentos-fiscais/{id} (financeiro:view) -- só de leitura. */
export interface DocumentoFiscalDetalhe {
  id: string;
  numeroFormatado: string;
  tipo: TipoDocumentoFiscal;
  tipoRotulo: string;
  ambiente: AmbienteFiscal;
  serieCodigo: string;
  ano: number;
  numero: number;
  dataEmissao: string;
  emitidoEm: string;
  emitenteNif: string;
  emitenteFirma: string;
  emitenteMorada: string;
  emitenteLocalidade: string | null;
  emitenteRegimeIva: RegimeIva;
  emitenteMotivoIsencaoCodigo: string | null;
  emitenteMotivoIsencaoDescricao: string | null;
  emitenteMotivoIsencaoMencao: string | null;
  adquirenteNif: string;
  adquirenteNome: string;
  adquirenteMorada: string;
  adquirenteLocalidade: string | null;
  clienteId: string;
  processoId: string;
  honorarioId: number;
  pagamentoId: number;
  metodoPagamento: MetodoPagamento;
  metodoPagamentoRotulo: string;
  meioPagamentoCodigo: string;
  moeda: string;
  taxaIva: number | null;
  totalBase: number;
  totalIva: number;
  taxaRetencao: number | null;
  totalRetencao: number;
  totalDocumento: number;
  valorLiquido: number;
  estadoComunicacao: EstadoComunicacaoFiscal | null;
  emitidoPorNome: string | null;
  linhas: DocumentoFiscalLinha[];
}

/** GET /documentos-fiscais -- paginação no servidor (D-17). */
export interface PaginaDocumentosFiscais {
  content: DocumentoFiscalResumo[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

/** Filtros da listagem; strings vazias contam como ausentes. Datas em "AAAA-MM-DD". */
export interface DocumentosFiscaisFiltros {
  clienteId?: string;
  de?: string;
  ate?: string;
  tipo?: TipoDocumentoFiscal | "";
  estado?: EstadoComunicacaoFiscal | "";
  page: number;
  size: number;
}
