---
name: gsd-hostinger-deploy
description: "Atualiza o projeto na VPS Hostinger puxando as novas imagens docker via Hostinger VPS Connector (MCP)"
---

<objective>
Utilizar a integração MCP (`hostinger-vps`) para desencadear automaticamente a atualização de um projeto Docker Compose numa Máquina Virtual da Hostinger, substituindo a necessidade de um passo SSH no GitHub Actions.
</objective>

<context>
Argumentos recebidos: $ARGUMENTS
- O projeto padrão é `lexcv`.
- O Virtual Machine ID padrão é `1709247`.
</context>

<process>
1. **Verificar Parâmetros**: Extrair o nome do projeto e o ID da VM a partir de `$ARGUMENTS`. Se estiverem vazios, assuma `projectName: "lexcv"` e `virtualMachineId: 1709247`.
2. **Obter Configuração Atual**: Chame `VPS_getProjectContentsV1` com `{ "virtualMachineId": 1709247, "projectName": "lexcv" }` para obter o `content` e o `environment` atuais do projeto.
3. **Reaplicar Stack via MCP**: Chame o endpoint `VPS_createNewProjectV1` com `{ "virtualMachineId": 1709247, "project_name": "lexcv", "content": content, "environment": environment }`. Isto desencadeia a ação `docker_compose_up`, que força o pull das imagens mais recentes (`docker compose pull`) e recria os contentores preservando todos os volumes de dados existentes.
4. **Feedback Visual**: Apresente a confirmação de que o processo `docker_compose_up` foi disparado e informe o utilizador.
</process>
