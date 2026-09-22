// Tipos do registo de auditoria RBAC (`/settings`, aba "Auditoria", Phase 128).
// Espelham literalmente o payload de GET /api/v1/admin/rbac/auditoria
// (backend/src/main/java/com/lexcv/controllers/AuditoriaRbacController.java,
// backend/src/main/java/com/lexcv/dtos/AuditoriaRbacEntradaDto.java, Plans 02/07).
//
// Duas coisas que 128-UI-SPEC.md assume mas o backend real (fonte da verdade, ver
// 128-07-SUMMARY.md "UI-SPEC Reconciliation") diverge:
//
// 1. O endpoint é `/admin/rbac/auditoria` (não `/admin/rbac/audit`).
// 2. O filtro de papel é `papelId` (um UUID, por id), não `papel` (uma string, por nome) --
//    um papel renomeado continua a corresponder aos seus próprios eventos antigos.

/**
 * Códigos conhecidos de `acao`, tal como escritos por `AuditoriaRbacService`. `acao` é tipado
 * como `string` em {@link AuditoriaRbacEntry}, não como este union, porque um `acao`
 * desconhecido TEM de ser representável -- ver `auditoriaEventoToSentence`, que nunca deixa um
 * código bruto chegar ao ecrã, mas também não pode assumir por tipos que só estes seis códigos
 * alguma vez existirão.
 */
export type AuditoriaRbacAcao =
  | "papel_criar"
  | "papel_renomear"
  | "papel_apagar"
  | "papel_permissoes_alterar"
  | "papel_atribuir"
  | "papel_retirar";

export type AuditoriaRbacCategoria = "papel" | "atribuicao";

export type AuditoriaRbacMotivo = "utilizador_eliminado" | "provisionamento";

export interface AuditoriaRbacEntry {
  id: number;
  timestamp: string;
  acao: string;
  categoria: AuditoriaRbacCategoria;
  autorNome: string | null;
  alvoId: string | null;
  alvoNome: string | null;
  papelId: string | null;
  papelNome: string | null;
  nomeAntigo: string | null;
  nomeNovo: string | null;
  permissoesAdicionadas: string[] | null;
  permissoesRemovidas: string[] | null;
  motivo: AuditoriaRbacMotivo | null;
}

export type AuditoriaRbacListFilters = {
  utilizadorAlvoId?: string;
  papelId?: string;
  page?: number;
  size?: number;
};

export interface AuditoriaRbacPageResponse {
  content: AuditoriaRbacEntry[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
