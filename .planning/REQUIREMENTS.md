# Requirements: LexCV

**Defined:** 2026-06-29
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v2.4 Requirements

Milestone: Ficha de Cliente — adaptar o módulo de clientes para espelhar a ficha real do escritório.

### PERF — Perfil Base do Cliente

- [ ] **PERF-01**: Sistema gera automaticamente um `numero_cliente` sequencial (ex: CLI-0001) por tenant ao criar um cliente
- [ ] **PERF-02**: Utilizador vê o `numero_cliente` na listagem de clientes e na ficha individual
- [ ] **PERF-03**: Utilizador selecciona o tipo de cliente (Particular ou Empresa) no formulário de criação/edição
- [ ] **PERF-04**: Utilizador indica se o cliente é "Avençado" (flag booleano) visível na ficha e na listagem

### PART — Dados Pessoais (Particular)

- [ ] **PART-01**: Utilizador preenche campos demográficos do cliente particular: idade, sexo, nacionalidade
- [ ] **PART-02**: Utilizador regista o número de BI ou Passaporte do cliente particular

### EMP — Dados da Entidade Coletiva (Empresa)

- [ ] **EMP-01**: Quando tipo = Empresa, formulário apresenta campos de entidade coletiva: nome comercial, NIF, sede, representante legal, cargo do representante
- [ ] **EMP-02**: Campos de entidade coletiva substituem os campos demográficos no formulário (formulário dinâmico por tipo)

### PROC — Procuração

- [ ] **PROC-01**: Upload de documento de procuração é obrigatório para todos os clientes (Particular e Empresa)
- [ ] **PROC-02**: Utilizador pode visualizar e substituir o documento de procuração na ficha do cliente

### INT — Intake do Caso

- [ ] **INT-01**: Utilizador regista a descrição do caso no intake do cliente
- [ ] **INT-02**: Utilizador associa advogados ao cliente com nome, número de cédula e contacto
- [ ] **INT-03**: Utilizador regista os administrativos que intervêm no processo do cliente
- [ ] **INT-04**: Utilizador regista documentos entregues pelo cliente (lista)
- [ ] **INT-05**: Utilizador regista documentos a tratar (lista)
- [ ] **INT-06**: Utilizador regista deslocações a realizar (lista)
- [ ] **INT-07**: Utilizador regista honorários propostos no intake: valor total, valor por extenso, previsão

### FICH — Vista de Ficha Imprimível

- [ ] **FICH-01**: Utilizador acede a uma vista de ficha do cliente que reproduz o formato real do escritório
- [ ] **FICH-02**: Utilizador imprime ou exporta a ficha do cliente (print CSS / botão de impressão)

## Future Requirements

### Integrações Futuras

- **FUT-01**: Assinatura digital da ficha de cliente (integração com plataforma de assinaturas)
- **FUT-02**: Export da ficha para PDF via servidor (sem dependência de print browser)
- **FUT-03**: Portal do cliente para auto-preenchimento de dados

## Out of Scope

| Feature | Reason |
|---------|--------|
| Workflow de aprovação de cliente | Complexidade de negócio, fase posterior |
| Integração com bases de dados externas (ex: BI nacional) | Requer API institucional, fora do MVP |
| Histórico de alterações da ficha (audit trail completo) | Coberto parcialmente pelo sistema de auditoria existente |
| Assinatura digital | Adiado para integração futura (FUT-01) |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PERF-01 | TBD | Pending |
| PERF-02 | TBD | Pending |
| PERF-03 | TBD | Pending |
| PERF-04 | TBD | Pending |
| PART-01 | TBD | Pending |
| PART-02 | TBD | Pending |
| EMP-01 | TBD | Pending |
| EMP-02 | TBD | Pending |
| PROC-01 | TBD | Pending |
| PROC-02 | TBD | Pending |
| INT-01 | TBD | Pending |
| INT-02 | TBD | Pending |
| INT-03 | TBD | Pending |
| INT-04 | TBD | Pending |
| INT-05 | TBD | Pending |
| INT-06 | TBD | Pending |
| INT-07 | TBD | Pending |
| FICH-01 | TBD | Pending |
| FICH-02 | TBD | Pending |

**Coverage:**
- v2.4 requirements: 19 total
- Mapped to phases: 0
- Unmapped: 19 ⚠️ (roadmap pending)

---
*Requirements defined: 2026-06-29*
*Last updated: 2026-06-29 after initial definition*
