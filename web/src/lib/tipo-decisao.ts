import type { TipoDecisao } from "@/types/processos";

/**
 * Fonte unica de verdade para o mapeamento de tipo de decisao -> label PT.
 */
export function tipoDecisaoToLabel(tipo: TipoDecisao): string {
  const map: Record<TipoDecisao, string> = {
    DESPACHO: "Despacho",
    DECISAO_INTERLOCUTORIA: "Decisão Interlocutória",
    SENTENCA: "Sentença",
    ACORDAO: "Acórdão",
  };
  return map[tipo] ?? "Despacho";
}
