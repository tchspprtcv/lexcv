# SPEC.md - Frontend LexCV

## 1. Visão Geral e Requisitos (Clarify Requirements)
A **LexCV** é uma plataforma institucional de gestão jurídica desenvolvida pela Speed Tech, desenhada para apoiar a governação e transformação digital em Cabo Verde, com foco no ecossistema do SIJ (Sistema Judicial de Cabo Verde). A plataforma centraliza a gestão de clientes, processos jurídicos, prazos (agenda), documentos e controlo financeiro básico de forma segura e interoperável. 

O escopo atual foca-se no **Fase 1 (MVP Institucional)**, que tem um prazo de 45 a 75 dias úteis. A plataforma opera num modelo arquitetónico multiplataforma, priorizando a **Web (responsivo/mobile-first)** e o **Desktop (via Tauri)** para contexto institucional seguro, além de adotar **PWA** para uso mobile na fase inicial.

**Obrigatoriedade:** O sistema é **multi-entidade institucional (multi-tenant)**, garantindo isolamento lógico de dados.

---

## 2. Arquitetura e Design
A arquitetura do frontend é desenhada para ser moderna, escalável e completamente desacoplada da lógica de negócio, consumindo uma API RESTful.

### 2.1. Stack Tecnológica Principal
*   **Framework:** Next.js (React) usando o padrão **App Router**.
*   **Linguagem:** TypeScript (tipagem estrita obrigatória).
*   **Estilização:** Tailwind CSS acoplado ao ecossistema **shadcn/ui** (utilizando a função de utilitário `cn` para consistência e acessibilidade governamental WCAG).
*   **Gestão de Estado e Fetching:** TanStack Query (React Query) acoplado com Axios ou Fetch API nativa.
*   **Validação e Formulários:** React Hook Form + Zod.

### 2.2. Padrões de Design e Integração
*   **Apresentação vs Lógica:** Regra de ouro ("Nada de lógica no frontend"). O backend é a fonte absoluta da verdade. O frontend é estritamente uma camada de apresentação e interatividade.
*   **Multi-tenant Transparente:** O frontend **não** deve expor a gestão de `tenant_id` nas rotas URL. A injeção do contexto multi-tenant deve ser feita via Token JWT interceptado nas requisições Axios/Fetch.

### 2.3. Estrutura de Pastas Sugerida (Baseada no Escopo Modular)
```text
src/
├── app/                  # Next.js App Router (Rotas e Páginas)
│   ├── (auth)/login/
│   ├── (dashboard)/
│   │   ├── clientes/     # Módulo de Gestão de Clientes
│   │   ├── processos/    # Módulo de Processos Jurídicos
│   │   ├── agenda/       # Módulo de Prazos e Eventos
│   │   ├── documentos/   # Gestão Documental
│   │   └── financeiro/   # Honorários e Pagamentos
├── components/
│   ├── ui/               # Componentes do shadcn/ui gerados automaticamente
│   └── shared/           # Componentes reutilizáveis (Layouts, Navbars)
├── hooks/                # Hooks customizados e mutações do React Query
├── lib/                  # Utilitários (ex: cn do Tailwind, axios instanciado)
├── types/                # Interfaces TypeScript globais (Schemas Base)
└── schemas/              # Validações do Zod
```

---

## 3. Contratos Técnicos (API / Interfaces)

A comunicação é baseada numa API REST com códigos de status padronizados (200, 201, 204, 400, 401, 404) e sub-recursos hierárquicos.

### 3.1. Autenticação e Interceção
*   **Autenticação:** Baseada em JWT (com evolução prevista para Keycloak).
*   O token e o `tenant_id` viajam no *header* HTTP.

### 3.2. Endpoints Críticos (React Query Keys)
*   **Auth:** `POST /login`, `POST /refresh`, `GET /me`.
*   **Clientes:** `GET /clientes`, `POST /clientes` (aceita *query params* para filtros).
*   **Processos:** `GET /processos`, `GET /processos/{id}/fases`, `GET /processos/{id}/partes`.
*   **Agenda:** `GET /agenda` (filtros críticos muito usados no frontend para visualização de calendário).
*   **Documentos:** `POST /documentos` (requer `Multipart/form-data` e submissão orientada ao MinIO via backend).

### 3.3. Modelos de Dados Frontend (TypeScript / Zod Schemas Baseados no ERD)

Com base no dicionário de dados relacional fornecido, aqui estão as interfaces estritas a usar no TS:

**Schema de Cliente (Mapeamento de `t_cliente`):**
```typescript
interface Cliente {
  id: string; // UUID
  tipo: string;
  nome: string;
  nif?: string;
  email?: string;
  telefone?: string;
  morada?: string;
  created_at: string; // ISO Date String
}
```

**Schema de Processo (Mapeamento de `t_processo`):**
```typescript
interface Processo {
  id: string; // UUID
  cliente_id: string; // UUID
  numero_processo?: string;
  tipo_processo?: string;
  area_juridica?: string;
  tribunal?: string;
  estado?: string;
  data_inicio?: string; // ISO Date String
  data_fim?: string; // ISO Date String
  descricao?: string;
}
```

**Schema de Documento (Mapeamento de `t_documento`):**
```typescript
interface Documento {
  id: string; // UUID
  processo_id?: string;
  cliente_id?: string;
  nome?: string;
  tipo?: string;
  versao: number;
  tamanho: number;
  mime_type?: string;
  created_at: string;
}
```

---

## 4. Regras de Negócio Críticas (Frontend)
1.  **Isolamento Absoluto Multi-Tenant:** O frontend nunca deve permitir transitar IDs de *tenants* através de formulários do utilizador. Toda a submissão e listagem obedece ao token do utilizador autenticado e é validada pelo backend.
2.  **Upload de Ficheiros:** Todos os uploads devem transitar com `Multipart/form-data`. Os ficheiros **não são processados localmente**, devendo ser encaminhados para a API de onde serão armazenados no Object Storage (MinIO) de forma assíncrona.
3.  **Controlo Baseado em Papéis (RBAC):** Renderizações condicionais no frontend devem ler os perfis de acesso (Administrador, Técnico, Advogado, Assistente) providenciados pelo payload JWT/Endpoint do utilizador e ocultar rotas/botões não permitidos.

---

## 5. Fluxo de Trabalho de IA (Instruções para a IA geradora de código)
Para manter este *codebase* sem alucinações ou deriva técnica, **qualquer IA que escreva código neste repositório DEVE seguir estas instruções exatas:**

1.  **App Router Only:** Utilize exclusivamente a sintaxe do Next.js App Router (`page.tsx`, `layout.tsx`, `route.ts`). Não escreva código para o padrão antigo *Pages Router*.
2.  **UI System:** **Sempre** utilize componentes do **shadcn/ui** combinados com **Tailwind CSS**. Não introduza ficheiros CSS customizados, SASS ou Styled-Components. Para concatenar classes, use a função `cn()` importada de `lib/utils`. A acessibilidade (WCAG) é crítica e mantida pelo `shadcn`.
3.  **Data Fetching:** Toda e qualquer busca de dados (GET) ou mutações (POST, PUT, DELETE) deve ser feita usando **React Query (TanStack Query)**. Nunca utilize `useEffect` para carregar dados de APIs de negócio.
4.  **Validação Front-End:** Todos os formulários devem ser gerados com **React Hook Form** e ter obrigatoriamente um esquema **Zod** explícito antes do componente, mapeando o contrato técnico listado na Seção 3.3.
5.  **Arquitetura Passiva:** **NUNCA crie lógica de cálculo complexa ou validação de regras de negócio em componentes.** O frontend é um consumidor "burro" da API Spring Boot. Se a IA se deparar com a necessidade de calcular honorários processuais ou prazos jurídicos, a resposta deve ser apenas um mapeamento da variável vinda do Backend.
6.  **Tipagem Strict:** Não utilize `any`. Todas as chamadas de API através do Axios/Fetch devem ter os tipos de requisição e de resposta definidos (Ex: `axios.get<Cliente[]>('/clientes')`).