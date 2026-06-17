import type { ConflictNivel } from "@/types/processos";

/**
 * Fonte unica de verdade para o mapeamento de nivel de conflito -> variant de badge.
 * Usada no wizard (Step 2, Step 3) e na seccao de conflito do detalhe do processo.
 */
export function conflictNivelToVariant(
  nivel: ConflictNivel,
): "green" | "amber" | "blue" | "red" {
  const map: Record<ConflictNivel, "green" | "amber" | "blue" | "red"> = {
    sem_conflito: "green",
    potencial: "amber",
    sanavel: "blue",
    impeditivo: "red",
  };
  return map[nivel] ?? "amber";
}

/**
 * Fonte unica de verdade para o mapeamento de nivel de conflito -> label de badge.
 * Usada no wizard (Step 2, Step 3) e na seccao de conflito do detalhe do processo.
 */
export function conflictNivelToLabel(nivel: ConflictNivel): string {
  const map: Record<ConflictNivel, string> = {
    sem_conflito: "SEM CONFLITO",
    potencial: "POTENCIAL",
    sanavel: "SANÁVEL",
    impeditivo: "IMPEDITIVO",
  };
  return map[nivel] ?? "POTENCIAL";
}
