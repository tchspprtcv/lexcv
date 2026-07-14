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
 * Categorias que o utilizador NAO pode silenciar (NOTF-24).
 *
 * MANTER EM SINCRONIA com `CategoriaNotificacao.java` no backend --
 * `PRAZO_VENCIDO` e a unica categoria marcada como `silenciavel(false)` la.
 * O backend continua a ser a fonte de verdade e a validar/rejeitar (400) no
 * PUT /notificacoes/preferencias/{categoria}; esta constante existe apenas
 * para centralizar a duplicacao inevitavel do lado do frontend (a
 * alternativa correta a prazo e o GET /notificacoes/preferencias passar a
 * devolver a lista de categorias silenciaveis, tornando este array
 * desnecessario).
 */
export const NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS: readonly NotificacaoCategoria[] = [
  "PRAZO_VENCIDO",
];

/**
 * Fonte unica de verdade (do lado do frontend) para as opcoes de categoria
 * apresentadas no toggle de preferencias de notificacao -- deriva de
 * NOTIFICACAO_CATEGORIA_OPTIONS excluindo
 * NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS, em vez de um filtro inline com um
 * literal de categoria embutido no componente.
 */
export const NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS: {
  value: NotificacaoCategoria;
  label: string;
}[] = NOTIFICACAO_CATEGORIA_OPTIONS.filter(
  (o) => !NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS.includes(o.value),
);

/**
 * Fonte unica de verdade para a verificacao de seguranca de `linkUrl`: aceita
 * apenas caminhos internos relativos ("/processos/123"). Em vez de enumerar
 * caracteres/posicoes de bypass conhecidos — abordagem que ja falhou duas
 * vezes nesta mesma funcao ("//evil.com" na 1a iteracao, "/\evil.com" na
 * 2a, ambas reabertas por uma variante nova — a mais recente usando
 * TAB/LF/CR embutidos, que o algoritmo de parsing de URL WHATWG remove de
 * qualquer posicao da string antes de resolver a autoridade) — resolve o
 * valor atraves do parser de URL real (o mesmo usado pelo browser e pelo
 * Next.js ao resolver `href`) e compara a origem resultante com uma origem
 * sentinela fixa. Qualquer valor que introduza a sua propria autoridade
 * (protocol-relative, variante com barra invertida, caracteres de controlo
 * embutidos, ou qualquer futura quirk do WHATWG) resolve para uma origem
 * diferente da sentinela e e rejeitado por construcao — sem depender de
 * listar cada variante de bypass conhecida.
 */
const INTERNAL_URL_SENTINEL = "http://internal.invalid";

export function isInternalLinkUrl(url: string | null | undefined): url is string {
  if (typeof url !== "string" || !url.startsWith("/")) return false;
  try {
    return new URL(url, INTERNAL_URL_SENTINEL).origin === INTERNAL_URL_SENTINEL;
  } catch {
    return false;
  }
}
