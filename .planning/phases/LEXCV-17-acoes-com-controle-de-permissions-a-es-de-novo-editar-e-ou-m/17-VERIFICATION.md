# Phase 17 Verification

## Objetivo

Validar que o frontend esconde ações e bloqueia páginas quando o utilizador não tem permissões efetivas (`view`, `create`, `edit`, `manage`) para o módulo.

## Checklist (Manual)

### Perfis

- Perfil A: apenas `*:view` (sem `*:edit`)
- Perfil B: `*:edit` (ou `*:manage`) para o módulo testado

### Navegação

- Sidebar mostra apenas módulos com `:view`
- CTAs de “Novo ...” aparecem apenas com `:create` (ou fallback `:edit`/`:manage`)

### Páginas (bloqueio por URL)

- Aceder diretamente a `/clientes` sem `clientes:view` mostra “Acesso negado” e não dispara queries do módulo
- Aceder diretamente a `/processos` sem `processos:view` mostra “Acesso negado” e não dispara queries do módulo
- Aceder diretamente a `/agenda` sem `agenda:view` mostra “Acesso negado” e não dispara queries do módulo
- Aceder diretamente a `/documentos` sem `documentos:view` mostra “Acesso negado” e não dispara queries do módulo
- Aceder diretamente a `/financeiro` sem `financeiro:view` mostra “Acesso negado” e não dispara queries do módulo

### Ações

- Clientes: botões “Editar” e “Apagar” só aparecem com `clientes:edit` (ou `clientes:manage`)
- Processos: ações de adicionar/editar sub-recursos só aparecem com `processos:edit` (ou `processos:manage`)
- Agenda: “Novo evento” e “Editar/Concluir” só aparecem com `agenda:edit` (ou `agenda:manage`)
- Documentos: “Upload” e “Apagar” só aparecem com `documentos:edit` (ou `documentos:manage`)
- Financeiro: “Novo honorário” e “Registar pagamento” só aparecem com `financeiro:edit` (ou `financeiro:manage`)

