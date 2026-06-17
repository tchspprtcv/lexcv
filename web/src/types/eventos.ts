export type EventoPrioridade = "BAIXA" | "MEDIA" | "ALTA";

export interface Evento {
  id: number;
  tenant_id: string;
  processo_id?: string;
  titulo: string;
  descricao?: string;
  data_inicio: string;
  data_fim: string;
  prioridade: EventoPrioridade;
  concluido: boolean;
}

export interface EventoCreateRequest {
  processo_id?: string;
  titulo: string;
  descricao?: string;
  data_inicio: string;
  data_fim: string;
  prioridade?: EventoPrioridade;
  concluido?: boolean;
}

export interface EventoUpdateRequest {
  processo_id?: string | null;
  titulo?: string;
  descricao?: string | null;
  data_inicio?: string;
  data_fim?: string;
  prioridade?: EventoPrioridade;
  concluido?: boolean;
}
