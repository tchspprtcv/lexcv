# Requirements: LexCV — Milestone v2.8

**Defined:** 2026-07-03
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requirements for milestone v2.8 (Refatoração Ficha de Cliente). Each maps to roadmap phases.

### Unificação Ficha/Formulário

- [ ] **CLI-12**: Utilizador visualiza e edita os dados do cliente numa única página, alternando entre modo leitura e edição via botão "Editar"
- [ ] **CLI-13**: Em modo visualização, controlos de edição (inputs/selects/botões guardar-cancelar-adicionar-remover) ficam inativos/ocultos; em modo edição tornam-se ativos
- [ ] **CLI-14**: Rota `/clientes/[id]/editar` é removida em favor do componente único em `/clientes/[id]`

### Navegação por Separadores

- [ ] **CLI-15**: Ficha de cliente apresenta 7 separadores: Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações
- [ ] **CLI-16**: Separador "Processos" lista processos do cliente (`useProcessos({cliente_id})`)
- [ ] **CLI-17**: Separador "Pareceres" lista pareceres do cliente (`usePareceres({clienteId})`)
- [ ] **CLI-18**: Separador "Contactos e Notas" apresenta os cards de Contactos e Notas atualmente na página principal

### Identificação no Card "Dados"

- [ ] **CLI-19**: Card "Dados" principal inclui identificação (NIF, tipo de documento, número de documento) como elemento do card
- [ ] **CLI-20**: Enum `documento_tipo` passa a incluir `BI`
- [ ] **CLI-21**: Valor `NIF` é removido do enum `documento_tipo` (corte limpo)
- [ ] **CLI-22**: Para Particular, tipo de documento oferece apenas CNI/BI/Passaporte
- [ ] **CLI-23**: Para Empresa, tipo de documento oferece apenas Registo Comercial
- [ ] **CLI-24**: Restrição por tipo de cliente validada em frontend (dropdown filtrado) e backend (rejeita combinações inválidas)

### Documentos Entregues (upload real)

- [ ] **CLI-25**: "Documentos Entregues" passa a lista de ficheiros carregados (upload), em vez de texto (descrição+data)
- [ ] **CLI-26**: Upload reutiliza sistema genérico `Documento`/`/documentos/upload` com `clienteId`
- [ ] **CLI-27**: Novo endpoint de listagem de documentos por cliente
- [ ] **CLI-28**: Campo "tipo" no upload é combobox — escolher tipo existente ou escrever novo
- [ ] **CLI-29**: Dados antigos de documentos entregues (texto sem ficheiro) deixam de ser editáveis na nova UI; coluna fica órfã (sem migração)

### Documentos a Tratar / Deslocações

- [ ] **CLI-30**: Separador "Documentos a Tratar" mantém lista de texto atual, isolado no seu próprio separador
- [ ] **CLI-31**: Separador "Deslocações" mantém lista de texto atual (descrição/local/data), isolado no seu próprio separador

## v2 Requirements

None deferred — full scope committed to v1 for this milestone.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Ficha impressa (`/clientes/[id]/ficha`) | Mantém-se inalterada — não faz parte do fluxo de pesquisa central desta milestone |
| CSV import/export, merge de clientes | Funcionalidades não afetadas pela refatoração da ficha |
| Unificação view/edit em processos | Fora de âmbito — só clientes nesta milestone; processos mantém páginas separadas |
| Aprovação interna de pareceres (PARC-17) | Já diferido de milestones anteriores (v2.5/v2.6/v2.7), continua fora de escopo |
| Migração/backfill de dados antigos de "documentos entregues" | Corte limpo deliberado — coluna antiga fica órfã na BD, mesmo padrão usado para `dados_tipo` na v2.7 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CLI-12 | Phase 75 | Pending |
| CLI-13 | Phase 75 | Pending |
| CLI-14 | Phase 75 | Pending |
| CLI-15 | Phase 76 | Pending |
| CLI-16 | Phase 77 | Pending |
| CLI-17 | Phase 77 | Pending |
| CLI-18 | Phase 76 | Pending |
| CLI-19 | Phase 76 | Pending |
| CLI-20 | Phase 74 | Pending |
| CLI-21 | Phase 74 | Pending |
| CLI-22 | Phase 74 | Pending |
| CLI-23 | Phase 74 | Pending |
| CLI-24 | Phase 74 | Pending |
| CLI-25 | Phase 79 | Pending |
| CLI-26 | Phase 79 | Pending |
| CLI-27 | Phase 79 | Pending |
| CLI-28 | Phase 79 | Pending |
| CLI-29 | Phase 79 | Pending |
| CLI-30 | Phase 78 | Pending |
| CLI-31 | Phase 78 | Pending |

**Coverage:**
- v1 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0 ✓

---
*Requirements defined: 2026-07-03*
*Last updated: 2026-07-03 after roadmap creation (Phases 74–79)*
