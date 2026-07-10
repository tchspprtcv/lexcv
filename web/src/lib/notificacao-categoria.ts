import type { NotificacaoCategoria } from "@/types/notificacoes";

/**
 * Fonte unica de verdade para o mapeamento de categoria de notificacao -> label PT.
 */
export function categoriaToLabel(categoria: NotificacaoCategoria): string {
  const map: Record<NotificacaoCategoria, string> = {
    FASE_ENTRADA: "Nova fase",
    DOCUMENTO_NOVO: "Novo documento",
    PROCESSO_ATRIBUIDO: "Processo atribuído",
    PARECER_ATRIBUIDO: "Parecer atribuído",
    PRAZO_PROXIMO: "Prazo a vencer",
    PRAZO_VENCIDO: "Prazo vencido",
    EVENTO_PROXIMO: "Evento a aproximar-se",
    EVENTO_VENCIDO: "Evento em atraso",
    HONORARIO_ATRASADO: "Honorário em atraso",
  };
  return map[categoria] ?? "Notificação";
}

/**
 * Fonte unica de verdade para o mapeamento de categoria de notificacao -> variant de badge.
 */
export function categoriaToBadgeVariant(
  categoria: NotificacaoCategoria,
): "blue" | "purple" | "amber" | "red" {
  const map: Record<NotificacaoCategoria, "blue" | "purple" | "amber" | "red"> = {
    FASE_ENTRADA: "blue",
    PROCESSO_ATRIBUIDO: "blue",
    PARECER_ATRIBUIDO: "blue",
    DOCUMENTO_NOVO: "purple",
    PRAZO_PROXIMO: "amber",
    EVENTO_PROXIMO: "amber",
    PRAZO_VENCIDO: "red",
    EVENTO_VENCIDO: "red",
    HONORARIO_ATRASADO: "red",
  };
  return map[categoria] ?? "blue";
}

/**
 * Fonte unica de verdade para as opcoes do filtro de categoria (select).
 */
export const NOTIFICACAO_CATEGORIA_OPTIONS: { value: NotificacaoCategoria; label: string }[] = (
  [
    "FASE_ENTRADA",
    "DOCUMENTO_NOVO",
    "PROCESSO_ATRIBUIDO",
    "PARECER_ATRIBUIDO",
    "PRAZO_PROXIMO",
    "PRAZO_VENCIDO",
    "EVENTO_PROXIMO",
    "EVENTO_VENCIDO",
    "HONORARIO_ATRASADO",
  ] as const satisfies readonly NotificacaoCategoria[]
).map((value) => ({ value, label: categoriaToLabel(value) }));
