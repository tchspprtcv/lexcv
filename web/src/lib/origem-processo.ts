import type { OrigemProcesso } from "@/types/processos";

/**
 * Fonte unica de verdade para o mapeamento de origem do processo -> label PT.
 */
export function origemProcessoToLabel(origem: OrigemProcesso): string {
  const map: Record<OrigemProcesso, string> = {
    PETICAO_INICIAL: "Petição Inicial",
    NOTIFICACOES_AVULSAS: "Notificações Avulsas",
  };
  return map[origem] ?? "Petição Inicial";
}
