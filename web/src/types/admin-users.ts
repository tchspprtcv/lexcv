// Tipos de utilizadores geridos em `/settings` (aba "Gestão de Utilizadores"), Phase 127.
// Espelham `UserResponse` (backend/src/main/java/com/lexcv/dtos/UserResponse.java) -- apenas
// os campos que a app efectivamente le, nao a totalidade do DTO Java.
//
// `roles` vs `tenant_role_ids` respondem a perguntas diferentes (Phase 127, Plano 05, Decisao
// 6, ver o doc-comment de `UserResponse` no backend): `roles` sao os NOMES efectivos
// (`ResolucaoPapeisService.resolverNomesPapeis`) -- o que a app mostra e o que
// `usePermissions`/`useMe` leem, incluindo o caminho de papeis globais (utilizador ainda nao
// convertido a papeis proprios do escritorio). `tenant_role_ids` sao os ids dos `TenantRole`
// que o utilizador detem -- a CHAVE DE ATRIBUICAO que o formulario de edicao usa para
// pre-selecionar os papeis certos na lista de papeis do proprio escritorio (UI-SPEC §2). So o
// segundo sobrevive a uma renomeacao de papel: um nome em `roles` pode mudar debaixo do
// utilizador, um id em `tenant_role_ids` nunca muda.
export type AdminUser = {
  id: string;
  tenant_id: string;
  nome: string;
  email: string;
  roles: string[];
  permissions: string[];
  tenant_role_ids: string[];
  avatar_url?: string;
  telefone?: string;
  ativo?: boolean;
  tenant_nome?: string;
  tenant_logo_data_url?: string;
  tenant_plano?: string;
  tenant_limite_utilizadores?: number;
  tenant_utilizadores_ativos?: number;
};

// Forma de escrita de POST/PUT /api/v1/admin/users. Deliberadamente SEM campo `roles` -- o
// backend recusa com 400 qualquer pedido que o inclua (Phase 127, Plano 05): a atribuicao de
// papeis passa a ser feita exclusivamente por `tenantRoleIds`, nunca por nome.
export type AdminUserSavePayload = {
  nome?: string;
  email?: string;
  password?: string;
  telefone?: string;
  avatar_url?: string;
  ativo?: boolean;
  tenantRoleIds: string[];
  permissions: string[];
};
