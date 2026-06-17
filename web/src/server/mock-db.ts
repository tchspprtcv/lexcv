export type MockRole = "ADMIN" | "TECNICO" | "ADVOGADO" | "ASSISTENTE";

export type MockPermission =
  | "clientes:view"
  | "clientes:edit"
  | "processos:view"
  | "processos:edit"
  | "agenda:view"
  | "agenda:edit"
  | "documentos:view"
  | "documentos:edit"
  | "financeiro:view"
  | "financeiro:edit"
  | "rbac:manage"
  | "users:manage";

export interface MockPermissionDef {
  key: MockPermission;
  nome: string;
  descricao: string;
  modulo: string;
}

export interface MockTenant {
  id: string;
  nome: string;
}

export interface MockUser {
  id: string;
  tenant_id: string;
  nome: string;
  email: string;
  password: string;
  roles: MockRole[];
  avatar_url?: string;
  telefone?: string;
  ativo?: boolean;
  permissions?: MockPermission[];
}

export interface MockCliente {
  id: string;
  tenant_id: string;
  tipo?: string;
  nome: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  localidade?: string;
  ativo?: boolean;
  created_at: string;
}

export interface MockClienteContacto {
  id: string;
  tenant_id: string;
  cliente_id: string;
  tipo?: string;
  valor: string;
  created_at: string;
}

export interface MockClienteNota {
  id: string;
  tenant_id: string;
  cliente_id: string;
  titulo?: string;
  conteudo: string;
  created_at: string;
  updated_at?: string;
}

export interface MockProcesso {
  id: string;
  tenant_id: string;
  cliente_id: string;
  numero?: string;
  titulo?: string;
  tipo_processo?: string;
  descricao?: string;
  tribunal?: string;
  area_juridica?: string;
  estado?: string;
  created_at: string;
  updated_at?: string;
}

export interface MockParte {
  id: string;
  tenant_id: string;
  processo_id: string;
  tipo?: string;
  nome: string;
  nif?: string;
  created_at: string;
}

export interface MockFaseCatalog {
  id: string;
  tenant_id: string;
  nome: string;
  ordem: number;
}

export type MockProcessoFaseStatus = "PENDENTE" | "EM_ANDAMENTO" | "CONCLUIDA";

export interface MockProcessoFase {
  id: string;
  tenant_id: string;
  processo_id: string;
  fase_id: string;
  status: MockProcessoFaseStatus;
  created_at: string;
  updated_at?: string;
}

export interface MockMovimentacao {
  id: string;
  tenant_id: string;
  processo_id: string;
  titulo: string;
  descricao?: string;
  data: string;
  created_at: string;
}

export type MockEventoPrioridade = "BAIXA" | "MEDIA" | "ALTA";

export interface MockEvento {
  id: number;
  tenant_id: string;
  processo_id?: string;
  titulo: string;
  descricao?: string;
  data_inicio: string;
  data_fim: string;
  prioridade: MockEventoPrioridade;
  concluido: boolean;
}

export interface MockHonorario {
  id: number;
  tenant_id: string;
  processo_id: string;
  valor_total: number;
  descricao?: string;
  data_acordo?: string;
  created_at: string;
}

export interface MockPagamento {
  id: number;
  tenant_id: string;
  honorario_id?: number;
  valor_pago: number;
  data_pagamento: string;
  metodo?: string;
}

export interface MockContaCorrente {
  tenant_id: string;
  cliente_id: string;
  saldo: number;
  updated_at: string;
}

export interface MockDocumento {
  id: string;
  tenant_id: string;
  processo_id?: string;
  cliente_id?: string;
  tipo?: string;
  nome: string;
  filename: string;
  content_type: string;
  size: number;
  created_at: string;
}

export interface MockDocumentoBlob {
  bytes: ArrayBuffer;
  content_type: string;
  filename: string;
}

const tenantDemo: MockTenant = {
  id: "11111111-1111-1111-1111-111111111111",
  nome: "NOSi (Demonstração)",
};

const users: MockUser[] = [
  {
    id: "22222222-2222-2222-2222-222222222222",
    tenant_id: tenantDemo.id,
    nome: "Administrador",
    email: "admin@lexcv.cv",
    password: "admin123",
    roles: ["ADMIN"],
    ativo: true,
  },
  {
    id: "33333333-3333-3333-3333-333333333333",
    tenant_id: tenantDemo.id,
    nome: "Assistente",
    email: "assistente@lexcv.cv",
    password: "assist123",
    roles: ["ASSISTENTE"],
    ativo: true,
  },
];

const clientes: MockCliente[] = [
  {
    id: "44444444-4444-4444-4444-444444444444",
    tenant_id: tenantDemo.id,
    tipo: "SINGULAR",
    nome: "João Andrade",
    nif: "123456789",
    email: "joao.andrade@example.com",
    telefone: "+238 900 0000",
    morada: "Achada de Santo António",
    localidade: "Praia",
    ativo: true,
    created_at: "2026-05-01T09:00:00.000Z",
  },
  {
    id: "55555555-5555-5555-5555-555555555555",
    tenant_id: tenantDemo.id,
    tipo: "COLETIVA",
    nome: "Empresa Atlântico, SA",
    nif: "512345678",
    email: "contacto@atlantico.example",
    telefone: "+238 261 0000",
    morada: "Palmarejo",
    localidade: "Praia",
    ativo: true,
    created_at: "2026-05-10T11:30:00.000Z",
  },
];

const clientes_contactos: MockClienteContacto[] = [];
const clientes_notas: MockClienteNota[] = [];

const processos: MockProcesso[] = [
  {
    id: "66666666-6666-6666-6666-666666666666",
    tenant_id: tenantDemo.id,
    cliente_id: clientes[0].id,
    numero: "PROC-2026-0001",
    titulo: "Ação declarativa (exemplo)",
    descricao: "Processo de demonstração para validar endpoints de fases, partes e movimentações.",
    estado: "ATIVO",
    created_at: "2026-05-12T10:00:00.000Z",
    updated_at: "2026-05-12T10:00:00.000Z",
  },
  {
    id: "77777777-7777-7777-7777-777777777777",
    tenant_id: tenantDemo.id,
    cliente_id: clientes[1].id,
    numero: "PROC-2026-0002",
    titulo: "Execução fiscal (exemplo)",
    descricao: "Processo encerrado para testes de listagens e filtros no frontend.",
    estado: "ENCERRADO",
    created_at: "2026-04-05T10:00:00.000Z",
    updated_at: "2026-05-01T10:00:00.000Z",
  },
];

const fases_catalog: MockFaseCatalog[] = [
  { id: "FASE-1", tenant_id: tenantDemo.id, nome: "Distribuição", ordem: 1 },
  { id: "FASE-2", tenant_id: tenantDemo.id, nome: "Citação", ordem: 2 },
  { id: "FASE-3", tenant_id: tenantDemo.id, nome: "Contestação", ordem: 3 },
  { id: "FASE-4", tenant_id: tenantDemo.id, nome: "Audiência", ordem: 4 },
  { id: "FASE-5", tenant_id: tenantDemo.id, nome: "Sentença", ordem: 5 },
];

const partes: MockParte[] = [
  {
    id: "88888888-8888-8888-8888-888888888888",
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    tipo: "AUTOR",
    nome: clientes[0].nome,
    nif: clientes[0].nif,
    created_at: "2026-05-12T10:00:00.000Z",
  },
  {
    id: "99999999-9999-9999-9999-999999999999",
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    tipo: "RÉU",
    nome: "Réu Exemplo",
    nif: "987654321",
    created_at: "2026-05-12T10:00:00.000Z",
  },
  {
    id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    tenant_id: tenantDemo.id,
    processo_id: processos[1].id,
    tipo: "EXECUTADO",
    nome: clientes[1].nome,
    nif: clientes[1].nif,
    created_at: "2026-04-05T10:00:00.000Z",
  },
];

const processo_fases: MockProcessoFase[] = [
  {
    id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    fase_id: "FASE-1",
    status: "CONCLUIDA",
    created_at: "2026-05-12T10:00:00.000Z",
    updated_at: "2026-05-12T10:10:00.000Z",
  },
  {
    id: "cccccccc-cccc-cccc-cccc-cccccccccccc",
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    fase_id: "FASE-2",
    status: "EM_ANDAMENTO",
    created_at: "2026-05-13T09:00:00.000Z",
    updated_at: "2026-05-15T09:00:00.000Z",
  },
  {
    id: "dddddddd-dddd-dddd-dddd-dddddddddddd",
    tenant_id: tenantDemo.id,
    processo_id: processos[1].id,
    fase_id: "FASE-5",
    status: "CONCLUIDA",
    created_at: "2026-04-05T10:00:00.000Z",
    updated_at: "2026-05-01T10:00:00.000Z",
  },
];

const movimentacoes: MockMovimentacao[] = [
  {
    id: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    titulo: "Petição inicial submetida",
    descricao: "Documento anexado e distribuído.",
    data: "2026-05-12T10:00:00.000Z",
    created_at: "2026-05-12T10:00:00.000Z",
  },
  {
    id: "ffffffff-ffff-ffff-ffff-ffffffffffff",
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    titulo: "Despacho do juiz",
    descricao: "Determinada a citação do réu.",
    data: "2026-05-15T14:30:00.000Z",
    created_at: "2026-05-15T14:30:00.000Z",
  },
];

const eventos: MockEvento[] = [
  {
    id: 1,
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    titulo: "Prazo: apresentar documento",
    data_inicio: "2026-05-28T09:00:00.000Z",
    data_fim: "2026-05-28T10:00:00.000Z",
    prioridade: "ALTA",
    concluido: false,
  },
  {
    id: 2,
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    titulo: "Audiência preliminar",
    descricao: "Comparecer com as partes e documentos de identificação.",
    data_inicio: "2026-06-15T13:00:00.000Z",
    data_fim: "2026-06-15T14:00:00.000Z",
    prioridade: "MEDIA",
    concluido: false,
  },
];

let nextEventoId = eventos.reduce((acc, e) => Math.max(acc, e.id), 0) + 1;

export function allocateEventoId() {
  const id = nextEventoId;
  nextEventoId += 1;
  return id;
}

const honorarios: MockHonorario[] = [
  {
    id: 1,
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    valor_total: 200000,
    descricao: "Honorários iniciais (exemplo)",
    data_acordo: "2026-05-01",
    created_at: "2026-05-01T09:00:00.000Z",
  },
  {
    id: 2,
    tenant_id: tenantDemo.id,
    processo_id: processos[1].id,
    valor_total: 500000,
    descricao: "Honorários finais (exemplo)",
    data_acordo: "2026-05-10",
    created_at: "2026-05-10T11:30:00.000Z",
  },
];

let nextHonorarioId = honorarios.reduce((acc, h) => Math.max(acc, h.id), 0) + 1;

export function allocateHonorarioId() {
  const id = nextHonorarioId;
  nextHonorarioId += 1;
  return id;
}

const pagamentos: MockPagamento[] = [
  {
    id: 1,
    tenant_id: tenantDemo.id,
    honorario_id: 1,
    valor_pago: 120000,
    data_pagamento: "2026-05-03",
    metodo: "TRANSFERENCIA",
  },
  {
    id: 2,
    tenant_id: tenantDemo.id,
    honorario_id: 2,
    valor_pago: 380000,
    data_pagamento: "2026-05-18",
    metodo: "DINHEIRO",
  },
];

let nextPagamentoId = pagamentos.reduce((acc, p) => Math.max(acc, p.id), 0) + 1;

export function allocatePagamentoId() {
  const id = nextPagamentoId;
  nextPagamentoId += 1;
  return id;
}

const contas_correntes: MockContaCorrente[] = [
  {
    tenant_id: tenantDemo.id,
    cliente_id: clientes[0].id,
    saldo: 45000,
    updated_at: "2026-05-20T12:00:00.000Z",
  },
  {
    tenant_id: tenantDemo.id,
    cliente_id: clientes[1].id,
    saldo: -120000,
    updated_at: "2026-05-22T12:00:00.000Z",
  },
];

const documentos: MockDocumento[] = [
  {
    id: "DOC-1",
    tenant_id: tenantDemo.id,
    processo_id: processos[0].id,
    cliente_id: processos[0].cliente_id,
    tipo: "PETICAO",
    nome: "Petição inicial (exemplo)",
    filename: "peticao-inicial.txt",
    content_type: "text/plain; charset=utf-8",
    size: 0,
    created_at: "2026-05-12T10:01:00.000Z",
  },
];

const documentos_blobs = new Map<string, MockDocumentoBlob>();

{
  const encoder = new TextEncoder();
  const blob = encoder.encode("Petição inicial (exemplo) - conteúdo fictício.\n");
  const bytes = blob.buffer.slice(blob.byteOffset, blob.byteOffset + blob.byteLength);
  documentos_blobs.set(documentos[0].id, {
    bytes,
    content_type: documentos[0].content_type,
    filename: documentos[0].filename,
  });
  documentos[0].size = bytes.byteLength;
}

const systemPermissions: MockPermissionDef[] = [
  { key: "clientes:view", nome: "Visualizar Clientes", descricao: "Ver lista e detalhes de clientes", modulo: "Clientes" },
  { key: "clientes:edit", nome: "Gerir Clientes", descricao: "Criar, editar e apagar clientes", modulo: "Clientes" },
  { key: "processos:view", nome: "Visualizar Processos", descricao: "Ver lista e detalhes de processos judiciais", modulo: "Processos" },
  { key: "processos:edit", nome: "Gerir Processos", descricao: "Criar, editar, alterar fases e apagar processos", modulo: "Processos" },
  { key: "agenda:view", nome: "Visualizar Agenda", descricao: "Ver calendário e prazos/eventos", modulo: "Agenda" },
  { key: "agenda:edit", nome: "Gerir Agenda", descricao: "Criar, editar e concluir eventos/prazos", modulo: "Agenda" },
  { key: "documentos:view", nome: "Visualizar Documentos", descricao: "Ver e descarregar documentos", modulo: "Documentos" },
  { key: "documentos:edit", nome: "Gerir Documentos", descricao: "Carregar e apagar documentos", modulo: "Documentos" },
  { key: "financeiro:view", nome: "Visualizar Financeiro", descricao: "Ver honorários, pagamentos e conta corrente", modulo: "Financeiro" },
  { key: "financeiro:edit", nome: "Gerir Financeiro", descricao: "Lançar honorários, pagamentos e gerir conta corrente", modulo: "Financeiro" },
  { key: "rbac:manage", nome: "Gerir Permissões (RBAC)", descricao: "Alterar regras de acesso globais por função", modulo: "Administração" },
  { key: "users:manage", nome: "Gerir Utilizadores", descricao: "Criar, ativar/desativar, e configurar utilizadores", modulo: "Administração" },
];

export type MockRolePermissions = Record<MockRole, MockPermission[]>;

const rolePermissions: MockRolePermissions = {
  ADMIN: [
    "clientes:view", "clientes:edit",
    "processos:view", "processos:edit",
    "agenda:view", "agenda:edit",
    "documentos:view", "documentos:edit",
    "financeiro:view", "financeiro:edit",
    "rbac:manage", "users:manage"
  ],
  TECNICO: [
    "clientes:view",
    "processos:view",
    "agenda:view", "agenda:edit",
    "documentos:view",
    "financeiro:view"
  ],
  ADVOGADO: [
    "clientes:view", "clientes:edit",
    "processos:view", "processos:edit",
    "agenda:view", "agenda:edit",
    "documentos:view", "documentos:edit",
    "financeiro:view"
  ],
  ASSISTENTE: [
    "clientes:view", "clientes:edit",
    "processos:view",
    "agenda:view",
    "documentos:view"
  ],
};

export function getUserPermissions(user: MockUser): MockPermission[] {
  const rolePerms = user.roles.flatMap((r) => rolePermissions[r] || []);
  const customPerms = user.permissions || [];
  return Array.from(new Set([...rolePerms, ...customPerms]));
}

export const mockDb = {
  tenants: [tenantDemo] as MockTenant[],
  users,
  clientes,
  clientes_contactos,
  clientes_notas,
  processos,
  partes,
  fases_catalog,
  processo_fases,
  movimentacoes,
  eventos,
  honorarios,
  pagamentos,
  contas_correntes,
  documentos,
  documentos_blobs,
  rolePermissions,
  systemPermissions,
};
