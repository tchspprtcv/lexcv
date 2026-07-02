# Phase 73: Detail Page & Printable Ficha Update - Context

**Gathered:** 2026-07-02
**Status:** Ready for planning

<domain>
## Phase Boundary

A página de detalhe do cliente (`clientes/[id]/page.tsx`) e a ficha imprimível (`clientes/[id]/ficha/page.tsx`) são atualizadas para apresentar apenas a estrutura de dados simplificada (CLI-11): rótulos dinâmicos consistentes com a Phase 72 ("Morada"/"Sede" conforme o tipo de cliente), e remoção definitiva dos campos de Empresa descontinuados (`Nome Comercial`, `Representante Legal`, `Cargo`) que hoje aparecem sempre em branco na ficha — esses campos foram explicitamente descartados do âmbito da milestone (ver `REQUIREMENTS.md` "Out of Scope").

</domain>

<decisions>
## Implementation Decisions

### Ficha Imprimível — remover campos de Empresa descontinuados
- Remover por completo os `<Field>` "Nome Comercial", "Representante Legal" e "Cargo" do bloco `isEmpresa` em `ficha/page.tsx` — não ficam como espaços em branco, desaparecem da ficha (consistente com a decisão de âmbito já registada em REQUIREMENTS.md: "Campos de representante legal e cargo na empresa — Explicitamente descartados pelo utilizador em favor de ficha simplificada")
- O campo "Sede" (atualmente sempre em branco no bloco `isEmpresa`) é removido do bloco de Identificação — a morada/sede já é apresentada na secção "Contactos" via o campo `Field label="Morada"`, que passa a ter rótulo dinâmico (ver abaixo). Não duplicar o dado em dois locais da ficha.
- O nome comercial já está coberto pelo campo `Field label="Nome"` existente (CLI-07 já unificou nome/nome comercial no campo `nome`) — não é necessário um segundo campo "Nome Comercial".

### Dynamic Labels — consistência com a Phase 72
- Página de detalhe (`[id]/page.tsx`): o `<dt>Morada</dt>` estático passa a dinâmico — "Morada" para `tipo === "PARTICULAR"`, "Sede" para `tipo === "EMPRESA"` (mesmo padrão `form.watch`-like já usado nos formulários, aqui simplesmente `cliente.data.tipo === "EMPRESA" ? "Sede" : "Morada"` já que não há formulário/watch, apenas leitura de dados carregados)
- Ficha imprimível (`ficha/page.tsx`): o `<Field label="Morada">` na secção Contactos recebe o mesmo tratamento dinâmico — "Sede" quando `isEmpresa` (variável já existente no componente), "Morada" caso contrário

### Claude's Discretion
Quaisquer outros ajustes de apresentação não cobertos acima (ex.: se "Tipo Doc." na ficha deve mostrar um rótulo amigável em vez do valor bruto do enum, ordem exata dos campos, mensagens de placeholder) ficam ao critério do Claude, seguindo os padrões visuais já estabelecidos em ambos os ficheiros.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ficha/page.tsx` já tem `const isEmpresa = cliente.tipo === "EMPRESA";` calculado — reutilizável diretamente para o rótulo dinâmico de Morada/Sede
- `[id]/page.tsx` lê `cliente.data.tipo` diretamente (sem watch, é apenas leitura de dados carregados via `useCliente`)

### Established Patterns
- Padrão de rótulo dinâmico já estabelecido na Phase 72 (`novo/page.tsx`/`editar/page.tsx`): ternário simples `tipo === "EMPRESA" ? "X" : "Y"`
- `Field` component em `ficha/page.tsx` aceita `label`/`value` como props — trivial passar um label computado
- `fmt()` helper em `ficha/page.tsx` já trata `undefined` → `BLANK` — não precisa de alteração

### Integration Points
- Nenhuma alteração de tipos/schema necessária — esta fase é puramente de apresentação sobre dados já aplanados desde a Phase 71

</code_context>

<specifics>
## Specific Ideas

Ver REQUIREMENTS.md "Out of Scope": campos de representante legal/cargo já foram explicitamente descartados pelo utilizador — esta fase apenas remove os vestígios visuais desses campos (sempre em branco) da ficha impressa.

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
