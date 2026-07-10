import type { NotificacaoCategoria } from "@/types/notificacoes";

/**
 * Fonte unica de verdade para o mapeamento de categoria de notificacao -> label PT.
 * Elevado a constante de modulo (em vez de literal local a funcao) para que
 * NOTIFICACAO_CATEGORIA_OPTIONS possa derivar a lista de categorias a partir
 * deste mesmo Record exaustivo, em vez de duplicar os valores num array
 * mantido a mao.
 */
const CATEGORIA_LABEL_MAP: Record<NotificacaoCategoria, string> = {
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

export function categoriaToLabel(categoria: NotificacaoCategoria): string {
  return CATEGORIA_LABEL_MAP[categoria] ?? "Notificação";
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
 * Derivada de CATEGORIA_LABEL_MAP (Record<NotificacaoCategoria, ...>, com
 * exaustividade garantida pelo compilador) em vez de um array literal
 * duplicado a mao — assim, uma categoria nova que atualize o Record mas
 * fique esquecida aqui deixa de ser possivel, porque a lista de chaves vem
 * do proprio Record.
 */
export const NOTIFICACAO_CATEGORIA_OPTIONS: { value: NotificacaoCategoria; label: string }[] = (
  Object.keys(CATEGORIA_LABEL_MAP) as NotificacaoCategoria[]
).map((value) => ({ value, label: categoriaToLabel(value) }));

/**
 * Fonte unica de verdade para a verificacao de seguranca de `linkUrl`: aceita
 * apenas caminhos internos relativos ("/processos/123"). Rejeita URLs
 * protocol-relative ("//evil.example.com") que tambem comecam por "/" mas que
 * o browser resolve como absolutas fora da origem ao navegar.
 */
export function isInternalLinkUrl(url: string | null | undefined): url is string {
  return typeof url === "string" && url.startsWith("/") && !url.startsWith("//");
}
