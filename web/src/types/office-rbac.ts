// Tipos do dominio de RBAC de escritorio (`/settings`, aba "Papéis do Escritório", Phase 127).
// Espelham literalmente o payload de GET/PUT /api/v1/admin/rbac e POST/PUT/DELETE
// /api/v1/admin/rbac/roles (backend/src/main/java/com/lexcv/dtos/OfficeRbacResponse.java,
// OfficeRbacUpdateRequest.java, PapelCreateRequest.java, PapelRenameRequest.java, Plans 02-04).
//
// Quatro coisas que um leitor futuro nao adivinha:
//
// 1. Em `OfficePermissao`, `key` e a chave tecnica `scope:action` (ex. "clientes:view") e
//    `nome` e o rotulo legivel para humanos -- a mesma inversao key/rotulo herdada da Phase 124
//    e ja documentada em `platform-moldes.ts`. `OfficePapel.permissoes` (abaixo) contem chaves
//    tecnicas nesse mesmo formato, nao rotulos -- para mostrar um rotulo e preciso cruzar com
//    `OfficeRbac.permissoes`.
//
// 2. `OfficePapel.id` e uma STRING UUID, nao um numero. `platform-moldes.ts` usa `number`
//    porque os ids de molde sao o id inteiro global de `Role`; este e o id de `TenantRole`
//    (proprio do escritorio, gerado como UUID). Os dois modulos de tipos NAO devem ser
//    fundidos -- os ids nao sao intercambiaveis e um `id` copiado de um para o outro aponta
//    para a tabela errada.
//
// 3. `protegido` e `podeApagar` sao computados no SERVIDOR e nunca devem ser re-derivados no
//    cliente. `protegido` em particular ja NAO pode ser inferido a partir de `nome` -- esta
//    fase torna o nome editavel (PAPEL-04), e a proveniencia real vem de
//    `TenantRole.moldeId` contra o `Role` global "ADMIN" (ver o doc-comment de
//    `OfficeRbacResponse.PapelDto` no backend). Um escritorio que renomeie o seu papel de
//    administrador nao pode perder a protecao por isso -- e e exactamente esse bug que uma
//    comparacao de `nome` no cliente reintroduziria. `podeApagar` e computado no servidor como
//    `utilizadoresAtribuidos === 0 && !protegido`, precisamente para que nenhum cliente tenha
//    de re-derivar essa logica booleana em duplicado.
//
// 4. `permissoes` num papel contem chaves tecnicas (o mesmo formato de `key` do ponto 1), nao
//    rotulos -- renderizar um rotulo exige cruzar com `OfficeRbac.permissoes`.

export type OfficePermissao = {
  key: string;
  nome: string;
  descricao: string | null;
  modulo: string;
};

export type OfficePapel = {
  id: string;
  nome: string;
  sistema: boolean;
  protegido: boolean;
  podeApagar: boolean;
  utilizadoresAtribuidos: number;
  permissoes: string[];
};

export type OfficeRbac = {
  papeis: OfficePapel[];
  permissoes: OfficePermissao[];
};

export type OfficeRbacUpdateRequest = {
  papeis: { id: string; permissoes: string[] }[];
};

export type PapelCreateRequest = {
  nome: string;
  permissoes: string[];
};

export type PapelRenameRequest = {
  nome: string;
};
