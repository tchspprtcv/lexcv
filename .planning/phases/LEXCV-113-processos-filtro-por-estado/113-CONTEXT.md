# Phase 113: Processos — Filtro por Estado - Context

**Gathered:** 2026-07-21
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous)

<domain>
## Phase Boundary

O utilizador filtra a lista de Processos por estado, sem perder os outros filtros já aplicados.

**Descoberta central que define o âmbito desta fase:** um filtro de Estado já existe tecnicamente — `NativeSelect` completo (Todos/Triagem/Ativo/Suspenso/Encerrado/Concluído) em `web/src/app/(dashboard)/processos/page.tsx:256-268`, já ligado a `draftEstado`/`onApply`/`onClear`, já funcionando em conjunto com os outros filtros (Tribunal, Área jurídica, Cliente). Os 3 critérios de sucesso do ROADMAP.md já são tecnicamente satisfeitos pelo código existente. O problema real não é lógica em falta — é que este filtro vive escondido dentro do painel colapsável "Filtros" (`advancedOpen`, fechado por defeito), o que o torna efetivamente invisível/não descoberto. **O âmbito desta fase é promover o Estado para uma posição sempre visível na barra principal de filtros — uma mudança de layout, não de lógica.**

</domain>

<decisions>
## Implementation Decisions

### Promoção do Filtro de Estado
- Estado sai do painel avançado (`advancedOpen`) e passa a estar sempre visível na barra principal de filtros
- Posição: logo a seguir ao campo de pesquisa, antes do botão "Filtros"
- Continua a usar `NativeSelect` (mesmo componente, não trocar para Select Radix — scope creep não pedido)
- As mesmas 6 opções existentes (Todos/Triagem/Ativo/Suspenso/Encerrado/Concluído), mesmo `value`/`onChange` ligados a `draftEstado`

### O que NÃO promover / NÃO mudar
- Tribunal, Área jurídica e Cliente continuam dentro do painel avançado — só Estado tem uso frequente que justifica a promoção
- Sem contagem (totalAtivos/totalSuspensos) junto ao dropdown promovido — isso é um KPI decorativo de outro contexto, fora de âmbito
- Sem troca de componente (NativeSelect mantém-se)

### Claude's Discretion
- Comportamento exato de "aplicar imediatamente ao mudar" vs. "só ao clicar Aplicar" para o Estado promovido — a decidir durante o planeamento, olhando ao padrão real do `onApply`/`draftEstado` já existente (provavelmente mantém o mesmo padrão de "draft + Aplicar" que os outros campos já usam, para consistência, mas o executor pode avaliar se um dropdown sempre-visível merece resposta imediata sem precisar de "Aplicar")

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/native-select.tsx` — `NativeSelect` já usado para Estado (linhas 256-268) e Cliente (linhas 302-314) no mesmo ficheiro
- Estado já tem as 6 opções corretas mapeadas: `""` (Todos), `TRIAGEM`, `ATIVO`, `SUSPENSO`, `ENCERRADO`, `CONCLUIDO`

### Established Patterns
- `draftX` state + `onApply`(aplica todos de uma vez) + `onClear` (reset total) — padrão já usado para q/estado/tribunal/area/cliente nesta página
- `Processo.estado` no backend é uma `String` livre (sem enum Java), os valores válidos são só uma convenção do frontend — não há validação de enum no lado do servidor

### Integration Points
- `web/src/app/(dashboard)/processos/page.tsx` — único ficheiro a modificar; mover o bloco JSX do Estado (linhas ~251-270) para fora do `{advancedOpen ? (...) : null}` e para a barra principal (perto de onde está o campo de pesquisa/botão "Filtros")

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica além do padrão já estabelecido nesta mesma página.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
