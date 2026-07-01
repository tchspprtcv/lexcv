export type ParecerStatus = "PENDENTE" | "EM_ELABORACAO" | "EM_REVISAO" | "CONCLUIDO";
export type ParecerPrioridade = "ALTA" | "MEDIA" | "BAIXA";

export interface ParecerSolicitacao {
  id: string;
  tenantId: string;
  clienteId: string;
  descricao: string;
  processoId?: string;
  advogadoId?: string;
  versaoFinalId?: string;
  prioridade: ParecerPrioridade;
  status: ParecerStatus;
  prazo?: string;
  createdAt: string;
}

export interface ParecerVersao {
  id: string;
  tenantId: string;
  solicitacaoId: string;
  numeroVersao: number;
  conteudo?: string;
  caminhoAnexo?: string;
  criadoPorId?: string;
  createdAt: string;
  aprovado: boolean;
  aprovadoPorId?: string;
  aprovadoEm?: string;
}
