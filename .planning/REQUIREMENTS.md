# Milestone v1.10 Requirements

## Active

### Notificações
- [ ] **NOTIF-01**: O sistema deve enviar um email de alerta com 7, 3 e 1 dia de antecedência para Prazos Criticos e audiências.
- [ ] **NOTIF-02**: O sistema deve notificar in-app (sino de notificações) sobre eventos próximos.

### Calendário Avançado
- [ ] **CAL-01**: O calendário de agenda deve suportar vistas Diária, Semanal, Mensal e Lista (Agenda).
- [ ] **CAL-02**: O utilizador deve poder reagendar um evento arrastando e soltando (Drag and Drop) na vista do calendário.
- [ ] **CAL-03**: O utilizador deve poder redimensionar (resize) um evento para alterar a sua duração.

### Eventos Recorrentes
- [ ] **REC-01**: Ao criar um evento, o utilizador deve poder escolher a frequência de repetição (Diária, Semanal, Mensal).
- [ ] **REC-02**: O utilizador deve poder definir uma data limite (até quando repete).
- [ ] **REC-03**: A plataforma deve gerar as instâncias de repetição corretamente na base de dados para permitir edições isoladas.

## Out of Scope
- [ ] Integração nativa com Outlook/Google Calendar (apenas para uma milestone futura, requer OAuth2 e permissões).
