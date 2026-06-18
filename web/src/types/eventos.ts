export type EventoPrioridade = "BAIXA" | "MEDIA" | "ALTA";

export interface Evento {
  id: number;
  tenantId: string;
  processoId?: string;
  tipo?: string;
  titulo: string;
  descricao?: string;
  dataInicio: string;
  dataFim: string;
  prioridade: EventoPrioridade;
  concluido: boolean;
}

export interface EventoCreateRequest {
  processoId?: string;
  tipo?: string;
  titulo: string;
  descricao?: string;
  dataInicio: string;
  dataFim: string;
  prioridade?: EventoPrioridade;
  concluido?: boolean;
}

export interface EventoUpdateRequest {
  processoId?: string | null;
  tipo?: string | null;
  titulo?: string;
  descricao?: string | null;
  dataInicio?: string;
  dataFim?: string;
  prioridade?: EventoPrioridade;
  concluido?: boolean;
}

export interface UpcomingEvento {
  id: number;
  titulo: string;
  dataInicio: string;
  processoId: string; // string vazia quando null no backend
  tipo: string; // string vazia quando null no backend
}
