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
2. **Chamar MCP**: Utilize a ferramenta `call_mcp_tool` para o servidor `hostinger-vps` e chame o endpoint `VPS_updateProjectV1`.
   - Payload de exemplo: `{ "virtualMachineId": 1709247, "projectName": "lexcv" }`
3. **Feedback Visual**: Quando o MCP retornar a resposta de que o processo foi iniciado (ex: `state: "started"`), apresente uma caixa de sucesso informando o utilizador de que a Hostinger está a executar o comando `docker compose pull && docker compose up -d` na respetiva máquina.
</process>
