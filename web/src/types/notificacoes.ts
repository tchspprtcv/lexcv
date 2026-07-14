export type NotificacaoCategoria =
  | "FASE_ENTRADA"
  | "DOCUMENTO_NOVO"
  | "PROCESSO_ATRIBUIDO"
  | "PARECER_ATRIBUIDO"
  | "PRAZO_PROXIMO"
  | "PRAZO_VENCIDO"
  | "EVENTO_PROXIMO"
  | "EVENTO_VENCIDO"
  | "HONORARIO_ATRASADO";

export interface Notificacao {
  id: string;
  categoria: NotificacaoCategoria;
  entidadeTipo: string;
  entidadeId: string;
  titulo: string;
  mensagem: string;
  linkUrl: string | null;
  lida: boolean;
  createdAt: string;
  snoozedUntil: string | null;
}

export type NotificacoesListFilters = {
  categoria?: NotificacaoCategoria;
  lida?: boolean;
  page?: number;
  size?: number;
};

export interface NotificacoesPageResponse {
  content: Notificacao[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface NotificacaoPreferenciasResponse {
  silenciadas: NotificacaoCategoria[];
}
