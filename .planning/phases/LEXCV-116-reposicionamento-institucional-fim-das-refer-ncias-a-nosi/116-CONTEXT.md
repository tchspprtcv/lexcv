# Phase 116: Reposicionamento Institucional — Fim das Referências a NOSi - Context

**Gathered:** 2026-07-27
**Status:** Ready for planning

<domain>
## Phase Boundary

O LexCV deixa de se descrever, em toda a sua superfície viva (documentação de projeto, landing pública, documentação técnica de referência e dados de demonstração seedados), como ligado à NOSi — passando a referenciar corretamente o SIJ (Sistema Judicial de Cabo Verde) como o seu ecossistema-alvo. Corretção de posicionamento/copy pura — sem nova funcionalidade, sem integração técnica real com o SIJ. `.planning/PROJECT.md` já foi editado na abertura da milestone (fora desta fase); esta fase cobre os 3 ficheiros restantes e fecha formalmente SIJ-01..04.

</domain>

<decisions>
## Implementation Decisions

### Texto de Substituição Institucional
- `webpage/src/components/trust-section.tsx` — card "Ecossistema Cabo Verde" (título mantido): `desc` passa de "Desenhado para a realidade institucional cabo-verdiana, alinhado ao ecossistema NOSi." para "Desenhado para a realidade institucional cabo-verdiana, alinhado ao ecossistema do SIJ (Sistema Judicial de Cabo Verde)."
- `.trae/documents/SPEC.md` (linha 4) — "...com foco no ecossistema do NOSi." passa a "...com foco no ecossistema do SIJ (Sistema Judicial de Cabo Verde)." — mesma formulação usada em PROJECT.md e no card da webpage, consistência entre os 3 documentos
- Nenhum outro texto em nenhum dos 2 ficheiros é alterado — apenas a substring/frase que menciona NOSi

### Identidade do Tenant de Demonstração (DatabaseSeeder.java)
- `nome`: "NOSi (Demonstração)" → "Gabinete Jurídico Demonstração"
- `tipoEntidade`: "PUBLICO" → "PRIVADO" (campo `String` livre em `Tenant.java`, sem enum — mudança segura; um gabinete jurídico genérico é tipicamente um escritório privado, coerente com o novo nome)
- `nif`: "500100200" → "000000000" (placeholder claramente fictício)
- `email`: "contacto@nosi.cv" → "contacto@lexcv.cv" (mesmo domínio já usado pelos utilizadores seed `admin@lexcv.cv`/`assistente@lexcv.cv`)
- `telefone`: "+238 2607900" → "+238 200 0000" — fora do texto literal de SIJ-04 (que só menciona nome/NIF/email), mas decisão explícita do utilizador: o número atual parece ser um contacto institucional real da NOSi, deixá-lo intacto seria um resíduo não-textual da mesma associação que o resto da fase está a remover
- Nenhum outro campo do tenant (id, createdAt, etc.) é tocado
- `web/src/server/mock-db.ts` (mock legado, mesma string "NOSi (Demonstração)") fica FORA de âmbito — confirmado em REQUIREMENTS.md Out of Scope, já marcado "ignore unless migrating" em CLAUDE.md

### Claude's Discretion
Nenhuma — todas as decisões desta fase foram explicitamente confirmadas pelo utilizador (2 áreas, "Accept all" em ambas).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Nenhum — fase é edição direta de strings literais existentes, sem novos componentes/endpoints/hooks

### Established Patterns
- `Tenant.tipoEntidade` é `private String tipoEntidade;` em `backend/src/main/java/com/lexcv/models/Tenant.java` (linha 26) — sem `@Enumerated`/enum, aceita qualquer string, confirmado seguro para alterar o valor seedado sem migração de schema
- `DatabaseSeeder.run()` só executa quando `seedEnabled=true` E a tabela de tenants está vazia (guarda `tenantRepository.count() > 0 || ...` early-return) — a alteração só afeta instalações que corram o seed pela primeira vez, não corrompe dados já existentes

### Integration Points
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` linhas 63-70 (bloco `Tenant.builder()...build()`)
- `webpage/src/components/trust-section.tsx` linha 8 (array `CONFIANCA`, objeto do ícone `Building2`)
- `.trae/documents/SPEC.md` linha 4 (primeiro parágrafo da secção "Visão Geral e Requisitos")

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual/exemplo externo — texto de substituição definido diretamente nesta discussão (ver seção Decisions acima), replicando a formulação já usada em PROJECT.md ("alinhada ao ecossistema do SIJ (Sistema Judicial de Cabo Verde)").

</specifics>

<deferred>
## Deferred Ideas

None — discussão manteve-se dentro do âmbito da fase (os 3 ficheiros do requisito + a decisão adicional sobre o telefone do tenant seed, que é uma extensão natural do mesmo SIJ-04).

</deferred>
