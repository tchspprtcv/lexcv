# Requirements: v2.9 Melhoria Módulo Processos

## v1 Requirements

### Dados do Processo

- [x] **PROC-01**: Utilizador pode registar o Juízo do processo (campo texto livre, ao lado de Tribunal/Área Jurídica no card "Dados")
- [x] **PROC-02**: Juízo é visível na ficha do processo (view e edit)

### Tramitação

- [x] **PROC-03**: Origem do processo (Petição Inicial | Notificações Avulsas) é um campo obrigatório no formulário de intake (passo 1)
- [x] **PROC-04**: Origem é validada em ambas as camadas — frontend (Zod, passo 1) e backend (`POST /processos/intake`, que hoje não valida nada, e `CAMPOS_MINIMOS_POR_TIPO` para todos os `tipo_processo`)
- [x] **PROC-05**: Origem é visível na ficha do processo após a formalização, mas não editável (campo definido apenas no intake)

### Informação do Processo — Decisões

- [x] **PROC-06**: Utilizador pode registar uma Decisão associada ao processo (data, tipo: Despacho | Decisão Interlocutória | Sentença | Acórdão, resumo, anexo opcional)
- [x] **PROC-07**: Anexo da Decisão é submetido diretamente no formulário de criação (upload multipart num só passo, criando o Documento internamente e associando-o à Decisão)
- [x] **PROC-08**: Utilizador pode listar, editar e remover Decisões do processo, numa sub-secção dedicada dentro de "Informação do Processo"

### Informação do Processo — Factos

- [x] **PROC-09**: Utilizador pode registar um Facto associado ao processo (descrição, data, ordem)
- [ ] **PROC-10**: Utilizador pode listar, editar, remover e reordenar Factos do processo, numa sub-secção dedicada dentro de "Informação do Processo"

### Informação do Processo — Testemunhas

- [x] **PROC-11**: Utilizador pode registar uma Testemunha associada ao processo (nome, contacto, tipo: Autor | Réu, notas) — entidade própria, distinta de Partes
- [x] **PROC-12**: Utilizador pode listar, editar e remover Testemunhas do processo, numa sub-secção dedicada dentro de "Informação do Processo"

### Documentos

- [ ] **PROC-13**: Ficha do processo ganha uma aba "Documentos" dedicada (upload, listagem, download e remoção), reutilizando o sistema genérico de `Documento` e o endpoint já existente `GET /processos/{id}/documentos`

### Honorários

- [ ] **PROC-14**: Ao formalizar um processo (TRIAGEM→ATIVO), o sistema cria automaticamente um registo de Honorário associado — operação idempotente (não duplica em reformalizações/retries), com `valorTotal` em branco (nunca pré-preenchido a partir de estimativas do cliente em `honorariosPropostos`)
- [ ] **PROC-15**: Ficha do processo ganha uma ação para gerar e imprimir o "Termo de Honorários" (modelo baseado no documento de referência anexado), combinando dados de Cliente, Processo e Honorário, seguindo o padrão CSS-print da Ficha Cliente (v2.4) — sem nova dependência de PDF/docx
- [ ] **PROC-16**: Geração do Termo de Honorários bloqueia/avisa quando o `valorTotal` do Honorário ainda não foi preenchido, em vez de imprimir um documento com campos em branco

### Segurança / Integridade

- [x] **PROC-17**: Endpoints de Decisões, Factos e Testemunhas verificam a posse do processo pai (tenant + `processoId`) em todas as operações de escrita (criar/editar/remover), seguindo o padrão mais rigoroso já usado em `ProcessoFase` (não o padrão mais simples de `Parte`/`Movimentacao`)

## Future Requirements

- Decisões surfaced no Timeline aggregator existente (ao lado de movimentações/transições/eventos/documentos) — adiado por colidir com o literal `"decisao"` já usado no `TimelineItemType` para o Conflict Check; requer resolver a colisão de nomenclatura primeiro
- Fase catalog condicionalmente informado pela Origem (Petição Inicial vs. Notificação Avulsa pode implicar fases iniciais diferentes)
- Unificação de `/processos/[id]` e `/processos/[id]/editar` num único componente (paridade com Cliente, v2.8 Phase 75) — divergência arquitetural conhecida, fora do âmbito desta milestone

## Out of Scope

- Hierarquia normalizada Tribunal→Juízo (catálogo mantido) — desproporcionado para o sistema judicial de Cabo Verde (pequeno e estável); Juízo fica como texto livre
- Cross-linking Factos↔Documentos↔Testemunhas / tagging de "favorabilidade" — âmbito de ferramentas de litigation-support de gama alta (Casefleet/CaseMap+), desproporcionado para a escala do escritório
- Tracking de estado de depoimento/testemunho em Testemunha — conceito processual de common law sem equivalente direto no processo civil de Cabo Verde; comparência já coberta por Evento/Agenda
- Automação completa de carta de compromisso (engagement letter) com assinatura eletrónica/envio automático/ativação automática — nem os líderes de mercado (Clio/MyCase/PracticePanther) fazem isto nativamente
- Tracking de origem de marketing/referral dentro do campo "Origem" — conceito diferente (atribuição de marketing vs. distinção processual); mantido fora
- `origem` editável após a formalização — decisão explícita: fica fixa após o intake

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROC-01 | Phase 80 | Complete |
| PROC-02 | Phase 81 | Complete |
| PROC-03 | Phase 81 | Complete |
| PROC-04 | Phase 81 | Complete |
| PROC-05 | Phase 81 | Complete |
| PROC-06 | Phase 80 | Complete |
| PROC-07 | Phase 81 | Complete |
| PROC-08 | Phase 81 | Complete |
| PROC-09 | Phase 80 | Complete |
| PROC-10 | Phase 81 | Pending |
| PROC-11 | Phase 80 | Complete |
| PROC-12 | Phase 81 | Complete |
| PROC-13 | Phase 84 | Pending |
| PROC-14 | Phase 82 | Pending |
| PROC-15 | Phase 84 | Pending |
| PROC-16 | Phase 84 | Pending |
| PROC-17 | Phase 81 | Complete |

**Coverage:** 17/17 requirements mapped ✓ (Phase 83 — Frontend Tipos/Schemas/Hooks — is a pure integration phase supporting all of the above; it owns no requirement directly but is a hard dependency for Phase 84.)
