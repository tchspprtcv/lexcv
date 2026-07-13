import type { PrazoRisco } from "@/types/processos";

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
  recurrenceRule?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  recurrenceEndDate?: string;
  recurrenceExceptions?: string;
  isRecurrenceInstance?: boolean;
  recurrenceInstanceDate?: string;
  risco?: PrazoRisco;
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
  recurrenceRule?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  recurrenceEndDate?: string;
}

export interface EventoUpdateRequest {
  // WR-05 (92-REVIEW.md): narrowed from `| null` — updateEvento's partial-update pattern
  // (`if (payload.getX() != null) evento.setX(...)`) can't distinguish an omitted field from
  // an explicit null, so there is currently no way to actually clear these via PUT /eventos/{id}
  // despite the wider type previously implying otherwise. Revert to `| null` if/when the backend
  // gains a proper PATCH DTO that can express "clear this field".
  processoId?: string;
  tipo?: string;
  titulo?: string;
  descricao?: string;
  dataInicio?: string;
  dataFim?: string;
  prioridade?: EventoPrioridade;
  concluido?: boolean;
  recurrenceRule?: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  recurrenceEndDate?: string;
}
