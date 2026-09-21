// Tipos do dominio de moldes da plataforma (consola PLATAFORMA_ADMIN, Phase 125).
// Espelham literalmente o payload de GET/PUT/POST /api/v1/platform/moldes
// (backend/src/main/java/com/lexcv/dtos/MoldesConsolaResponse.java e afins, Plan 03).
//
// Tres coisas que um leitor futuro nao adivinha:
//
// 1. Em `MoldePermissao`, `key` e a chave tecnica `scope:action` (ex. "clientes:view")
//    e `nome` e o rotulo legivel para humanos -- a inversao e herdada do contrato de
//    `GET /admin/rbac` da Phase 124 e e uma armadilha conhecida (ver
//    `PlatformAdminController#toPermissaoDto`, que documenta a mesma troca).
//
// 2. `escritoriosInstanciados` (em `MoldeSummary`) e o numero de escritorios que ja
//    tem uma copia propria daquele molde. E o dado load-bearing do aviso de
//    nao-propagacao (MOLD-03): editar um molde nunca chega a escritorios ja
//    provisionados, e e este numero que prova isso ao operador antes de gravar.
//    Nunca e opcional nem tem default no cliente -- o backend devolve-o sempre
//    (campo primitivo `long` do lado Java, nunca null).
//
// 3. `MoldeSummary.permissoes` contem chaves tecnicas (o mesmo formato de `key`
//    acima), nao rotulos -- para mostrar um rotulo e preciso cruzar com
//    `MoldesConsola.permissoes`.

export type MoldePermissao = {
  key: string;
  nome: string;
  descricao: string | null;
  modulo: string;
};

export type MoldeSummary = {
  id: number;
  nome: string;
  permissoes: string[];
  escritoriosInstanciados: number;
};

export type MoldesConsola = {
  permissoes: MoldePermissao[];
  moldes: MoldeSummary[];
};

export type MoldesUpdateRequest = {
  moldes: { id: number; permissoes: string[] }[];
};

export type MoldeCreateRequest = {
  nome: string;
  permissoes: string[];
};
