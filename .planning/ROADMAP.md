# Roadmap: v1.10 Finalização Módulo Agendamento

## Phases

### Phase 43: Backend - Recorrência e Serviço de Notificações
**Goal**: Atualizar o `Evento` para suportar recorrência e criar o `NotificationService` para envio diário de alertas de e-mail.
**Requirements**: REC-03, NOTIF-01
**Success Criteria**:
- Endpoint `/eventos` suporta geração em lote de eventos com base em recorrência (`frequencia`, `dataFimRecorrencia`).
- Spring Boot Starter Mail instalado e configurado em `application.yml`.
- Job `@Scheduled` corre sem erros e regista logs (ou envia emails mock) para eventos/prazos a 7, 3 e 1 dia.

### Phase 44: Frontend - Integração `react-big-calendar`
**Goal**: Substituir a grelha de calendário estática pela biblioteca `react-big-calendar` com suporte a drag-and-drop e vistas diária/semanal.
**Requirements**: CAL-01, CAL-02, CAL-03
**Success Criteria**:
- O pacote `react-big-calendar` está instalado e renderiza corretamente os eventos e prazos.
- O calendário suporta arrastar eventos para reagendar (drag and drop) que aciona chamadas `PUT /eventos/:id`.
- O utilizador pode alternar entre vista de Mês, Semana, Dia e Lista.

### Phase 45: Frontend - UI de Recorrência e Notificações
**Goal**: Permitir configurar recorrência na criação de eventos e apresentar um sino de notificações in-app.
**Requirements**: REC-01, REC-02, NOTIF-02
**Success Criteria**:
- O formulário `/agenda/novo` contém os campos de "Repetir" (Diária, Semanal, Mensal) e "Repetir até".
- O sino de notificações na Topbar apresenta alertas visuais para prazos/eventos fatais na próxima semana.

## Traceability

- **NOTIF-01**: Phase 43
- **NOTIF-02**: Phase 45
- **CAL-01**: Phase 44
- **CAL-02**: Phase 44
- **CAL-03**: Phase 44
- **REC-01**: Phase 45
- **REC-02**: Phase 45
- **REC-03**: Phase 43
