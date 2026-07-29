// IN-01 (Phase 120 code review): a mesma logica de "iniciais a partir do nome"
// existia duplicada verbatim entre a celula de nome da DataTable desktop
// (columns.tsx) e o card mobile (page.tsx). Extraida para um unico ponto de
// verdade, importado por ambos.

/**
 * Deriva as iniciais (ate 2 letras, maiusculas) a partir do nome de um
 * tenant, para o avatar quadrado exibido tanto na DataTable desktop
 * (`columns.tsx`) como nos cards empilhados mobile (`page.tsx`).
 */
export function tenantInitials(nome: string): string {
  return nome
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((palavra) => palavra[0])
    .join("")
    .toUpperCase();
}
