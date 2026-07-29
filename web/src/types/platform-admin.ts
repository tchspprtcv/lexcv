// Tipos do dominio de administracao de plataforma (consola PLATAFORMA_ADMIN, Phase 120).
//
// `limiteUtilizadores: null` significa "sem limite" — mesmo contrato ja usado
// pela Phase 117 para `Tenant.limiteUtilizadores` no backend.
//
// Os tipos de pedido/resposta para criacao de tenant ja existem em `@/types/setup`
// e sao reaproveitados tal como estao (mesmo corpo que o assistente de configuracao
// inicial envia) — nao sao redeclarados neste ficheiro.

export type TenantPlano = "STARTER" | "STANDARD" | "ENTERPRISE";

export type TenantAdminSummary = {
  id: string;
  nome: string;
  plano: TenantPlano;
  limiteUtilizadores: number | null;
  ativo: boolean;
  utilizadoresAtivos: number;
};

export type TenantUpdateRequest = {
  plano: TenantPlano;
  limiteUtilizadores: number | null;
};

export type TenantAtivoRequest = {
  ativo: boolean;
};
