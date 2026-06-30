# Phase 62: Elaboração e Versionamento - Context

**Gathered:** 2026-06-30
**Status:** Ready for planning

<domain>
## Phase Boundary

O advogado responsável consegue elaborar o parecer em versões sucessivas, cada uma com conteúdo, anexo opcional e histórico rastreável. Cobre apenas o backend (entidade ParecerVersao, endpoints aninhados em ParecerController) — sem UI frontend ainda (fica para fase futura) e sem aprovação/entrega (Fase 63).

</domain>

<decisions>
## Implementation Decisions

### Escopo Backend vs Frontend
- Esta fase entrega apenas API backend — sem rota `/pareceres` no frontend ainda (consistente com a decisão da Fase 61 de que `KNOWN_SCOPES` existe mas nenhuma página de UI foi criada)
- "Consultar e comparar versões" é satisfeito por um endpoint GET de lista/detalhe — comparação visual (diff) fica fora de escopo, já listada em REQUIREMENTS.md como Future Requirement
- Anexo de versão reutiliza `Documento`/`StorageService` existentes, não duplica storage

### Modelagem de ParecerVersao
- Controlo de acesso de criação: apenas o advogado responsável (`advogadoId` da solicitação) ou ADMIN pode criar nova versão, com scope `pareceres:edit`
- Campo `conteudo` (TEXT, nullable) — uma versão pode ser só um anexo sem texto
- `numeroVersao` gerado pelo backend como `MAX(numeroVersao)+1` por solicitação, mesmo padrão de `numeroSequencial` de Cliente (bloco `synchronized` no controller)
- Versões são imutáveis após criação — sem endpoint de UPDATE; qualquer alteração gera uma nova versão

### Endpoints e Integração com StorageService
- Path aninhado: `/api/v1/pareceres/solicitacoes/{solicitacaoId}/versoes`, consistente com o padrão `/processos/{id}/fases` já existente
- Upload de anexo: campo `caminhoAnexo` (String, nullable) na entidade `ParecerVersao` (semelhante a `Documento.caminhoArquivo`); upload inline no mesmo endpoint de criação de versão via `@RequestParam MultipartFile` opcional, reutilizando `StorageService.upload()`
- Download de anexo: `GET /versoes/{versaoId}/anexo` retorna presigned URL via `StorageService.presignedUrl()` (ou método equivalente já existente)
- Tudo adicionado ao `ParecerController.java` já existente (mantém o módulo de pareceres num único controller dedicado)

### Claude's Discretion
Nenhuma resposta "You decide" foi necessária — todas as 12 questões (3 áreas × 3-4) foram aceites com as respostas recomendadas.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ParecerSolicitacao`/`ParecerSolicitacaoRepository`/`ParecerController` (Fase 61) — base para os novos endpoints de versão
- `StorageService` (`backend/src/main/java/com/lexcv/services/StorageService.java`) — `upload(tenantId, documentoId, filename, inputStream, contentType, size)` e geração de presigned URL para download
- `Documento.caminhoArquivo` — padrão de referência para o novo campo `caminhoAnexo` em `ParecerVersao`
- Padrão de numeração sequencial: `numero_cliente`/`numeroSequencial` em `Cliente.java` (bloco synchronized no controller) — replicar para `numeroVersao`

### Established Patterns
- Entidades JPA: Lombok `@Builder`/`@Getter`/`@Setter`, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `tenantId` obrigatório, `@PrePersist` para timestamps
- Endpoints aninhados sob recurso pai: ver `/processos/{id}/fases` em `ResourceController.java`
- `@PreAuthorize("hasAuthority('scope:action')")` inline por endpoint, validação adicional de "é o próprio advogado responsável ou ADMIN" feita no corpo do método (mesmo padrão do `validateAdvogado` da Fase 61)

### Integration Points
- `ParecerSolicitacaoRepository` — usado para localizar a solicitação pai e validar `advogadoId`/tenant antes de criar versão
- `StorageService` — chamado diretamente do `ParecerController` para upload/download de anexos
- Nenhuma alteração a `web/src/lib/permissions.ts` necessária nesta fase (scopes já existem desde a Fase 61)

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência específica adicional além das decisões acima — segue convenções já estabelecidas na Fase 61 e no codebase (Cliente para numeração sequencial, Documento/StorageService para anexos, Processo/fases para endpoints aninhados).

</specifics>

<deferred>
## Deferred Ideas

- UI frontend de elaboração/versionamento de pareceres — fase futura (fora do roadmap atual de 4 fases, candidato a v2.6+)
- Comparação visual (diff) entre versões — já listado em REQUIREMENTS.md como Future Requirement
- Edição de versões existentes — versões são imutáveis por design

</deferred>
