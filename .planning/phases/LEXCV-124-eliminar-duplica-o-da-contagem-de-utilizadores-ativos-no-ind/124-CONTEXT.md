# Phase 124: Eliminar Duplicação da Contagem de Utilizadores Ativos no Indicador de Limite - Context

**Gathered:** 2026-07-30
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança"). Origem desta fase: escolha explícita do utilizador ("Planear uma fase de limpeza primeiro") em resposta ao veredito `tech_debt` da auditoria do marco v2.16 (`v2.16-MILESTONE-AUDIT.md`), não uma descoberta de discuss-phase.

<domain>
## Phase Boundary

Esta fase fecha **um único item de dívida técnica**, encontrado pelo agente `gsd-integration-checker` durante a auditoria do marco v2.16 (achado #2, "Contagem de utilizadores ativos"), não introduz nenhuma funcionalidade nova nem requisito de produto adicional.

O indicador "X/Y utilizadores" (`web/src/app/(dashboard)/settings/page.tsx:202`, `UserManagementTab`, entregue pela Fase 118) calcula `activeUserCount` filtrando no cliente a lista completa devolvida por `GET /api/v1/admin/users`:
```
const activeUserCount = users?.filter((u) => u.ativo === true).length ?? 0;
```
Isto é uma reimplementação paralela da mesma lógica que já existe no backend em `UserRepository.countByTenantIdAndAtivoTrue` (Fase 117), reutilizada corretamente por `AdminController.limiteUtilizadoresExcedido` (o `409` real que efetivamente aplica o limite) e por `PlatformAdminController.toSummary` (consola de tenants da Fase 120 e relatório de utilização da Fase 122, que não tem endpoint próprio — reutiliza o mesmo `GET /platform/tenants`).

`GET /api/v1/auth/me` (já estendido pela Fase 118 para expor `tenant_plano`/`tenant_limite_utilizadores`) **não expõe nenhuma contagem de utilizadores ativos** — esta é a lacuna concreta que esta fase fecha.

Impacto atual: zero — os dois cálculos usam o mesmo predicado (`tenant_id` do chamador + `ativo=true`) e concordam sempre hoje. Risco: se qualquer um dos dois lados mudar de semântica no futuro (ex.: um estado "utilizador pendente/convidado" que deva ou não contar), os dois cálculos podem divergir silenciosamente — o indicador visual mostraria um número diferente do que o backend realmente aplica no `409`.

**Fora de âmbito desta fase:** o outro item de dívida técnica da mesma auditoria (as 3 migrações SQL manuais pendentes de produção — `117-add-tenant-plano-limite-utilizadores.sql`, `120-add-tenant-ativo.sql`, `120b-backfill-tenant-plano.sql`) é uma pré-condição operacional de deployment, não um defeito de código — já gerida como tal em `123-ISOL-AUDIT.md` e não requer nenhuma alteração de código. Esta fase não a toca.

</domain>

<decisions>
## Implementation Decisions

### Onde expor a contagem
- **D-01:** Estender `GET /api/v1/auth/me` com um novo campo (ex.: `tenant_utilizadores_ativos`), calculado via `UserRepository.countByTenantIdAndAtivoTrue` — reutilizar o padrão já estabelecido pela própria Fase 118 para `tenant_plano`/`tenant_limite_utilizadores` (mesmo endpoint, mesmo bloco `ifPresent`, zero nova superfície de autorização). Não criar um endpoint novo.

### Onde consumir a contagem
- **D-02:** `UserManagementTab` (`settings/page.tsx`) passa a ler o novo campo de `useMe()` em vez de derivar `activeUserCount` por `filter()` sobre `useAdminUsers()`. A lista completa de utilizadores (`GET /admin/users`) continua a ser necessária para a tabela de gestão em si — só o número usado no indicador "X/Y" muda de origem.

### Compatibilidade com os 3 estados visuais existentes
- **D-03:** Os 3 estados confirmados ao vivo na Fase 118 (sem limite → "5 utilizadores"; dentro do limite → "5/7 utilizadores" cinzento; no limite → "5/5 utilizadores · limite atingido" + botão desativado + tooltip) têm de continuar byte-a-byte idênticos depois da mudança — esta é uma correção de fonte de dados, não uma mudança de UI. Nenhuma string, cor ou classe CSS deve mudar.

### Regressão
- **D-04:** `pnpm verify:limite-utilizadores` (gate executável da Fase 118, 8 asserções) e os testes Mockito de `AuthController`/`AdminController` já existentes têm de continuar verdes sem alteração. Se o gate assumir a forma antiga do payload de `GET /auth/me`, atualizar o próprio gate para refletir o novo campo — não contorná-lo.

### Claude's Discretion
- Nome exato do novo campo JSON (`tenant_utilizadores_ativos` é a sugestão desta fase, mas o planeador pode escolher outro nome se encontrar um precedente de naming mais consistente no codebase).
- Se vale a pena remover completamente o `filter()` client-side ou apenas deixar de o usar para o indicador (ex.: pode continuar a existir para outro propósito na mesma tab) — decidir com base no que a leitura do ficheiro atual mostrar.

</decisions>

<specifics>
## Specific Ideas

Nenhuma além do que está capturado acima — o âmbito é inteiramente definido pelo achado da auditoria de integração (ver `v2.16-MILESTONE-AUDIT.md`, secção "Fase 118").

</specifics>

<canonical_refs>
## Canonical References

### Achado de origem
- `.planning/v2.16-MILESTONE-AUDIT.md` — secção "Dívida Técnica" → "Fase 118", descreve o achado completo e o raciocínio de risco
- `.planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-02-SUMMARY.md` — como o indicador foi implementado originalmente
- `.planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-HUMAN-UAT.md` — os 3 estados visuais confirmados ao vivo que esta fase não pode quebrar

### Precedente de padrão a seguir
- `.planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-01-PLAN.md` — como `tenant_plano`/`tenant_limite_utilizadores` foram adicionados ao `GET /auth/me` (mesmo padrão a repetir para a nova contagem)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UserRepository.countByTenantIdAndAtivoTrue` (backend/src/main/java/com/lexcv/repositories/UserRepository.java) — já existe, já é a fonte única reutilizada por 2 dos 3 consumidores; só falta o 3º (Fase 118) passar a usá-la
- `AuthController.getMe()` — já tem o bloco `tenantRepository.findById(...).ifPresent(...)` onde `tenant_plano`/`tenant_limite_utilizadores` foram adicionados; o novo campo entra no mesmo bloco

### Established Patterns
- Fase 118 já estabeleceu o padrão exato a repetir: estender `GET /auth/me` em vez de criar endpoint novo, zero queries novas quando possível (aqui há 1 query nova — a própria contagem — mas reutiliza um método já existente, não escreve SQL novo)

### Integration Points
- `web/src/types/auth.ts` (`MeResponse`) precisa do novo campo
- `web/src/hooks/use-me.ts` (ou equivalente) — confirmar se precisa de alteração ou se só o tipo muda
- `settings/page.tsx` (`UserManagementTab`) — troca a origem de `activeUserCount`

</code_context>

<deferred>
## Deferred Ideas

- As 3 migrações SQL manuais pendentes de produção (pré-condição de deployment, não defeito de código) — permanecem geridas por `123-ISOL-AUDIT.md`, fora do âmbito desta fase.
- Qualquer refactor mais amplo de "fonte única de verdade" para outras métricas do dashboard — não pedido, não necessário aqui.

</deferred>

---

*Phase: 124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind*
*Context gathered: 2026-07-30*
