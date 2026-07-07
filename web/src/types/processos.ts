// --- Phase 33: Workflow, Gates e Prazos ---

export type PrazoRisco = "ok" | "proximo" | "vencido";
export type PrazoPrioridade = "ALTA" | "MEDIA" | "BAIXA";
export type TransicaoAcao = "ativar" | "suspender" | "encerrar" | "reabrir";

// --- Phase 83: Juizo/Origem + Decisao/Facto/Testemunha ---

export type TipoDecisao =
  | "DESPACHO"
  | "DECISAO_INTERLOCUTORIA"
  | "SENTENCA"
  | "ACORDAO";

export type TipoTestemunha = "AUTOR" | "REU";

export type OrigemProcesso = "PETICAO_INICIAL" | "NOTIFICACOES_AVULSAS";

// ---

export interface Processo {
  id: string;
  tenant_id: string;
  cliente_id: string;
  numero?: string;
  titulo?: string;
  tipo_processo?: string;
  area_juridica?: string;
  tribunal?: string;
  descricao?: string;
  estado?: string;
  data_inicio?: string;
  data_fim?: string;
  responsavel_id?: string;
  responsavel_nome?: string;
  risco_mais_critico?: PrazoRisco;
  tem_prazo_escalonado?: boolean;
  legal_hold?: boolean;
  data_retencao?: string;
  juizo?: string;
  origem?: OrigemProcesso;
  created_at: string;
  updated_at?: string;
}

export interface ProcessoCreateRequest {
  cliente_id: string;
  numero?: string;
  titulo?: string;
  tipo_processo?: string;
  area_juridica?: string;
  tribunal?: string;
  descricao?: string;
  estado?: string;
  data_inicio?: string;
  data_fim?: string;
  juizo?: string;
  origem?: OrigemProcesso;
}

export interface ProcessoUpdateRequest {
  cliente_id?: string;
  numero?: string;
  titulo?: string;
  tipo_processo?: string;
  area_juridica?: string;
  tribunal?: string;
  descricao?: string;
  estado?: string;
  data_inicio?: string;
  data_fim?: string;
  legal_hold?: boolean;
  data_retencao?: string;
  juizo?: string;
}

export interface ProcessoParte {
  id: string;
  tenant_id: string;
  processo_id: string;
  tipo?: string;
  nome: string;
  nif?: string;
  created_at: string;
}

export interface ProcessoParteCreateRequest {
  tipo?: string;
  nome: string;
  nif?: string;
}

export type ProcessoFaseStatus = "PENDENTE" | "EM_ANDAMENTO" | "CONCLUIDA";

export interface ProcessoFase {
  id: number;
  processo_id: string;
  fase_id: number;
  nome?: string;
  data_inicio?: string;
  data_fim?: string;
  status: ProcessoFaseStatus;
}

export interface ProcessoFaseCreateRequest {
  nome: string;
}

export interface ProcessoFaseUpdateRequest {
  status?: ProcessoFaseStatus;
}

export interface ProcessoMovimentacao {
  id: string;
  tenant_id: string;
  processo_id: string;
  titulo: string;
  descricao?: string;
  data: string;
  created_at: string;
}

export interface ProcessoMovimentacaoCreateRequest {
  titulo: string;
  descricao?: string;
  data?: string;
}

export interface Decisao {
  id: number;
  processoId: string;
  data: string;
  tipo: TipoDecisao;
  resumo?: string;
  documentoId?: string;
}

export interface DecisaoCreateRequest {
  file?: File;
  data: string;
  tipo: TipoDecisao;
  resumo?: string;
}

export interface DecisaoUpdateRequest {
  data: string;
  tipo: TipoDecisao;
  resumo?: string;
}

export interface Testemunha {
  id: number;
  processoId: string;
  nome: string;
  contacto?: string;
  tipo?: TipoTestemunha;
  notas?: string;
}

export interface TestemunhaCreateRequest {
  nome: string;
  tipo?: TipoTestemunha;
  contacto?: string;
  notas?: string;
}

export interface TestemunhaUpdateRequest {
  nome: string;
  tipo?: TipoTestemunha;
  contacto?: string;
  notas?: string;
}

export interface Facto {
  id: number;
  processoId: string;
  descricao: string;
  data?: string;
  ordem: number;
}

export interface FactoCreateRequest {
  descricao: string;
  data?: string;
}

export interface FactoUpdateRequest {
  descricao: string;
  data?: string;
  // Must be sourced from the current Facto.ordem value, never from user input —
  // no form collects this field yet, and reusing a stale value (e.g. after a
  // concurrent reorder) would silently corrupt ordering (WR-02).
  ordem: number;
}

export type ConflictNivel =
  | "sem_conflito"
  | "potencial"
  | "sanavel"
  | "impeditivo";

export interface ConflictMatch {
  entidadeId: string;
  entidadeTipo: "cliente" | "parte";
  nome: string;
  nif?: string;
  nivelConflito: ConflictNivel;
  motivo?: string;
}

export interface ConflictCheckResponse {
  matches: ConflictMatch[];
  nivelSugerido: ConflictNivel;
}

export interface ConflictCheckDecisao {
  id: string;
  tenantId: string;
  processoId: string;
  nivel: ConflictNivel;
  justificativa?: string;
  decisorId: string;
  dataDecisao: string;
  referenciaEvidencia?: string;
  createdAt: string;
}

export interface ConflictCheckDecisaoRequest {
  nivel: ConflictNivel;
  justificativa?: string;
  referenciaEvidencia?: string;
}

export interface TransicaoInfo {
  acao: TransicaoAcao;
  label: string;
  permissaoNecessaria: "processos:edit" | "processos:manage";
  requerJustificativa: boolean;
}

export interface WorkflowResponse {
  estadoAtual: string;
  responsavelId: string | null;
  responsavelNome: string | null;
  proximoPasso: string | null;
  transicoesDisponiveis: TransicaoInfo[];
}

export interface Prazo {
  id: string;
  tenantId?: string;
  processoId?: string;
  descricao: string;
  dataLimite: string;
  prioridade: PrazoPrioridade;
  responsavelId: string | null;
  concluido: boolean;
  escalonado: boolean;
  risco: PrazoRisco;
  createdAt: string;
}

export interface PrazoCreateRequest {
  descricao: string;
  dataLimite: string;
  prioridade?: PrazoPrioridade;
  responsavelId?: string;
}

export interface TransicaoRequest {
  justificativa?: string;
}

// --- Phase 34: Timeline e Auditoria ---

export type TimelineItemType =
  | "movimentacao"
  | "transicao"
  | "evento"
  | "documento"
  | "decisao";

export interface TimelineItem {
  tipo: TimelineItemType;
  id: string;
  timestamp: string;
  titulo: string;
  descricao?: string;
  autorNome?: string;
}

export interface AuditLogEntry {
  id: number;
  acao: string;
  entidadeTipo: string;
  entidadeId: string;
  autorNome?: string;
  timestamp: string;
}

