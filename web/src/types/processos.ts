// --- Phase 33: Workflow, Gates e Prazos ---

export type PrazoRisco = "ok" | "proximo" | "vencido";
export type PrazoPrioridade = "ALTA" | "MEDIA" | "BAIXA";
export type TransicaoAcao = "ativar" | "suspender" | "encerrar" | "reabrir";

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

