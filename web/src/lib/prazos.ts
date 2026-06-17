import type { PrazoRisco } from "@/types/processos";

/**
 * Fonte unica de verdade para o mapeamento de risco de prazo -> variant de badge.
 * Usada na lista de prazos no detalhe do processo e na listagem de processos.
 */
export function prazosRiscoToVariant(
  risco: PrazoRisco,
): "green" | "amber" | "red" {
  const map: Record<PrazoRisco, "green" | "amber" | "red"> = {
    ok: "green",
    proximo: "amber",
    vencido: "red",
  };
  return map[risco] ?? "amber";
}

/**
 * Fonte unica de verdade para o mapeamento de risco de prazo -> label de badge.
 * Usada na listagem de processos (apenas proximo/vencido renderizados).
 */
export function prazosRiscoToLabel(risco: PrazoRisco): string {
  const map: Record<PrazoRisco, string> = {
    ok: "PRAZO OK",
    proximo: "PRAZO PRÓXIMO",
    vencido: "PRAZO VENCIDO",
  };
  return map[risco] ?? "PRAZO PRÓXIMO";
}
