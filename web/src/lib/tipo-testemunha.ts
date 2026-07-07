import type { TipoTestemunha } from "@/types/processos";

/**
 * Fonte unica de verdade para o mapeamento de tipo de testemunha -> label PT.
 */
export function tipoTestemunhaToLabel(tipo: TipoTestemunha): string {
  const map: Record<TipoTestemunha, string> = {
    AUTOR: "Autor",
    REU: "Réu",
  };
  return map[tipo] ?? "Autor";
}
