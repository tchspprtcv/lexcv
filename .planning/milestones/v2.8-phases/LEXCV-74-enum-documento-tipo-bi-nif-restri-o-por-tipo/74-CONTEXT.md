# Phase 74: Enum `documento_tipo` (BI/NIF/Restrição por Tipo) - Context

**Gathered:** 2026-07-03
**Status:** Ready for planning

<domain>
## Phase Boundary

O tipo de documento de identificação do cliente (`documento_tipo`) reflete corretamente as opções válidas por tipo de cliente, em backend e frontend. Isto cobre: adicionar `BI` ao enum, remover `NIF` do enum (com limpeza defensiva de dados antigos), restringir as opções do dropdown consoante o `tipo` do cliente (Particular vs Empresa), e validar a combinação tanto no frontend como no backend. Não cobre a unificação view/edit (Phase 75) nem a reorganização em tabs (Phase 76+) — este é puramente o enum + validação + dropdown filtrado, ainda dentro das páginas atuais (`novo/page.tsx`, `[id]/editar/page.tsx`).

</domain>

<decisions>
## Implementation Decisions

### Modelo de Dados & Migração
- Antes de remover `NIF` do enum `DocumentoTipo`, correr uma limpeza defensiva de dados: `UPDATE t_cliente SET documento_tipo = NULL, documento_numero = NULL WHERE documento_tipo = 'NIF'` — sem isto, o Hibernate lança exceção ao desserializar (`EnumType.STRING`) qualquer registo antigo com esse valor, porque o valor deixa de existir no enum Java.
- A limpeza corre como script de migração único (seguir o padrão de migração já usado no projeto, ex. `data.sql`/seeder ou script `V*.sql`), não via código de aplicação em runtime.
- Limpar tanto `documento_tipo` como `documento_numero` desses registos — sem o tipo, o número perde sentido semântico.
- Esta limpeza defensiva NÃO conta como a "migração de funcionalidade" que o utilizador recusou explicitamente durante o planeamento da milestone ("corte limpo, sem migração de dados") — é proteção contra crash em runtime, não recriação de dados no novo formato. Mantém-se fiel à decisão original.

### Validação e Erros (Backend)
- Validação da combinação tipo-cliente/documento_tipo é feita manualmente no `ResourceController` (create e update de cliente), consistente com o estilo existente do codebase (a maioria das validações cross-field são checks ad hoc no controller).
- Erro de combinação inválida: HTTP 400 com `{"message": "..."}`, mesmo padrão dos outros erros de validação já existentes neste controller (não erro estruturado por campo, ao contrário do NIF).
- Validação aplica-se a create E update.
- Clientes já existentes na BD com combinações agora inválidas (ex.: Empresa com CNI, permitido antes desta fase) NÃO são forçados a corrigir — ficam como estão até serem editados; só submissões novas são validadas.

### Filtragem do Dropdown (Frontend)
- Extrair a lista de opções de `documento_tipo` por tipo de cliente para um módulo partilhado (ex. `web/src/lib/cliente-documento-tipo.ts`) com uma função tipo `getDocumentoTipoOptions(tipo: TipoCliente)`, substituindo os arrays `DOCUMENTO_TIPOS` atualmente duplicados em `novo/page.tsx` e `[id]/editar/page.tsx`.
- Opção "Nenhum" (vazio) mantém-se disponível para ambos os tipos — `documento_tipo` continua campo opcional.
- Ao trocar Particular↔Empresa com um `documento_tipo` já selecionado que deixa de ser válido para o novo tipo, limpar automaticamente `documento_tipo`/`documento_numero` — aproveitar o `Dialog` de confirmação de troca de tipo já existente (que já avisa sobre limpeza de dados específicos do tipo).
- No schema Zod (`web/src/schemas/clientes.ts`), remover o branch `superRefine` específico de `documento_tipo === "NIF"` (já não existe essa opção); adicionar um novo branch que valida que `documento_tipo` pertence ao conjunto permitido para o `tipo` do cliente (CNI/BI/PASSAPORTE para Particular; REG_COMERCIAL para Empresa).

### Claude's Discretion
- Nome exato do ficheiro/módulo partilhado de opções de documento_tipo no frontend.
- Mecanismo exato de migração SQL (nome do ficheiro, se via Flyway/seeder/script manual) — seguir o que já existe no projeto para alterações deste tipo.
- Mensagem exata de erro (texto em português) para a rejeição de combinação inválida no backend.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DocumentoTipo.java` (`backend/src/main/java/com/lexcv/models/DocumentoTipo.java`) — enum atual: `NIF, CNI, PASSAPORTE, REG_COMERCIAL`.
- `Cliente.java` — campo `nif` já é dedicado, obrigatório, validado (`@NotBlank` + `@Pattern` 9 dígitos) desde a v2.7; `documentoTipo`/`documentoNumero` são opcionais, sem validação cruzada hoje.
- Frontend: `DOCUMENTO_TIPOS` array e função `toDocumentoTipo` (type-guard) duplicados verbatim em `novo/page.tsx` e `[id]/editar/page.tsx` — única fonte de labels hoje.
- Padrão de confirmação de troca de tipo já existe: `pendingTipo` + `Dialog` de aviso antes de trocar Particular↔Empresa (limpa dados específicos do tipo antigo).

### Established Patterns
- Validações cross-field no backend são feitas manualmente no `ResourceController`, não via Bean Validation customizada.
- Erros de validação simples devolvem `{"message": "..."}`; o NIF é uma exceção que usa erro estruturado por campo — este padrão NÃO é seguido aqui, propositadamente (decisão desta fase).
- `documento_numero` é `optionalTrimmedString` no Zod, com `superRefine` cross-check hoje só para o caso NIF (que desaparece nesta fase).

### Integration Points
- `ResourceController` — métodos de create/update de cliente (já existentes, adicionar checks).
- `web/src/schemas/clientes.ts` — `clienteFormSchema`, branch `superRefine`.
- `novo/page.tsx` e `[id]/editar/page.tsx` — ambos consomem o novo módulo partilhado de opções (nota: Phase 75 vai unificar estas páginas num único componente, mas nesta fase ainda são independentes — o módulo partilhado de opções já reduz duplicação antecipadamente).

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica — esta é uma fase de modelo de dados + validação, sem mudança de layout.

</specifics>

<deferred>
## Deferred Ideas

None — discussão manteve-se dentro do âmbito da fase.

</deferred>
