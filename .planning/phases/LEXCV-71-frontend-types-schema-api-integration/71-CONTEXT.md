# Phase 71: Frontend Types, Schema & API Integration - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss determined no grey areas)

<domain>
## Phase Boundary

Os tipos TypeScript do cliente (`web/src/types/clientes.ts`) refletem o modelo aplanado do backend (sem `dados_tipo`), e o Zod schema (`web/src/schemas/clientes.ts`) exige NIF obrigatório com validação de 9 dígitos numéricos para qualquer tipo de cliente (Particular ou Empresa) (CLI-05, CLI-06). Esta fase é apenas tipos/schema/integração de API — os formulários de criação/edição são adaptados na Phase 72 (que depende desta), e a página de detalhe/ficha na Phase 73.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Fase de infraestrutura (tipos TypeScript + validação Zod, sem alteração de UI visível) — todas as escolhas de implementação ficam ao critério do Claude: mensagens de erro de validação, forma exata do tipo aplanado, e como o schema deriva o `documento_tipo`/`documento_numero`/`nif` a partir do backend já atualizado na Phase 70 (REG_COMERCIAL agora disponível). Seguir requisitos CLI-05 (NIF obrigatório, 9 dígitos numéricos, para Particular OU Empresa) e CLI-06 (remover `dados_tipo` dos tipos TS).

</decisions>

<code_context>
## Existing Code Insights

Ficheiros relevantes conhecidos: `web/src/types/clientes.ts` (tipos atuais, provavelmente ainda com `dados_tipo`), `web/src/schemas/clientes.ts` (Zod schema de criação/edição). Contexto de código adicional (hooks `use-clientes.ts`, uso em formulários) será levantado durante plan-phase/pattern-mapper.

</code_context>

<specifics>
## Specific Ideas

Nenhuma — fase de infraestrutura. Seguir CLI-05 (NIF: exatamente 9 dígitos numéricos, obrigatório) e CLI-06 (aplanar tipos, remover `dados_tipo`).

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
