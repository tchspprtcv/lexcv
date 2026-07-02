# Requirements: LexCV — Milestone v2.7 Melhoria Gestão de Clientes

**Defined:** 2026-07-02
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requirements for Milestone v2.7 to improve and simplify client management.

### Simplificação e Identificação Plana

- [x] **CLI-05**: Utilizador deve fornecer um NIF válido (exatamente 9 dígitos numéricos, obrigatório) para criar ou editar qualquer cliente (Particular ou Empresa)
- [x] **CLI-06**: Dados de identificação do cliente são aplanados na BD e o armazenamento em card JSON (`dados_tipo`) é totalmente removido
- [x] **CLI-07**: O campo `nome` da tabela `t_cliente` é aproveitado para registar tanto o nome (Particular) como o nome comercial (Empresa)
- [x] **CLI-08**: O campo `morada` da tabela `t_cliente` é aproveitado para registar a morada (Particular) ou a sede (Empresa)
- [x] **CLI-09**: O tipo de identificação para Empresa deve ser `REG_COMERCIAL` no campo `documento_tipo` e o número correspondente registado em `documento_numero`
- [x] **CLI-10**: Os formulários de criação e edição são adaptados para campos planos com labels dinâmicas (ex.: "Morada" vs. "Sede")
- [ ] **CLI-11**: O detalhe do cliente e a ficha impressa são atualizados para apresentar apenas a nova estrutura de dados simplificada

## v2 Requirements (Deferred)

- **PARC-17**: Ação de aprovação interna (ADMIN) na UI de pareceres (Carregada do v2.6)
- **PARV-07**: Diff/comparação entre versões de pareceres (Carregada do v2.6)
- **PARV-08**: Editor de texto formatado (rich text) para o conteúdo da versão de pareceres (Carregada do v2.6)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Multi-registo de contactos estruturados por cliente | Fora de âmbito; email, telefone e localidade continuam como colunas individuais |
| Campos demográficos adicionais (idade, sexo, nacionalidade) | Explicitamente descartados pelo utilizador para simplificar a gestão |
| Campos de representante legal e cargo na empresa | Explicitamente descartados pelo utilizador em favor de ficha simplificada |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| CLI-05 | Phase 71 (schema layer) + Phase 72 (form UI layer) | Phase 71 complete, Phase 72 pending |
| CLI-06 | Phase 70 (backend) + Phase 71 (frontend types) | Complete |
| CLI-07 | Phase 72 | Complete |
| CLI-08 | Phase 72 | Complete |
| CLI-09 | Phase 70 | Complete |
| CLI-10 | Phase 72 | Complete |
| CLI-11 | Phase 73 | Pending |

**Coverage:**
- v1 requirements: 7 total
- Mapped to phases: 7
- Unmapped: 0 ✓

---
*Requirements defined: 2026-07-02*
*Last updated: 2026-07-02 after initial definition*
