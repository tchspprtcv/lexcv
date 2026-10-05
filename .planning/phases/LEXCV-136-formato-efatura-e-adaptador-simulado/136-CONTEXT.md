# Phase 136: Formato eFatura e Adaptador Simulado - Context

**Gathered:** 2026-10-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Cada documento emitido (FR da 134 e NC da 135) produz, em segundo plano e fora da transação de
emissão, um XML eFatura (DFE) validado contra o XSD oficial e um IUD de 45 caracteres marcado como
ambiente de teste; é comunicado a um adaptador de interface única (`EfaturaGateway`) cuja única
implementação no v3.0 é a simulada; cada documento mostra o seu estado de comunicação, com
retentativa automática, reprocessamento manual e notificação in-app de falha persistente. Nenhum
documento simulado pode alguma vez parecer autorizado pela DNRE. Fora: PDF/email/MinIO (137),
plataforma (138), assinatura XAdES, OAuth2, certificados e ligação real (marco futuro).
Requisitos DFE-01..07.

</domain>

<decisions>
## Implementation Decisions

### Formato e IUD
- Pacote XSD oficial 2024-05-27 (cópias públicas `Kowts/efatura-cv-php` e `kriolos/kriolos-efatura`, idênticas) vendorizado em `backend/src/main/resources/xsd/efatura/`; modelo JAXB gerado com `org.jvnet.jaxb:jaxb-maven-plugin` 4.0.16 (sem `generatePackage` global — colisão XAdES 1.3.2/1.4.1; usar `.xjb`); `jakarta.xml.bind-api`/`jaxb-runtime` geridos pelo BOM em compile (pesquisa STACK.md §3, §11)
- Validação contra o XSD com `javax.xml.validation` endurecido contra XXE (acesso externo vazio) e `LSResourceResolver` de classpath (carregamento `file:` falha no `java -jar`)
- XML gerado em segundo plano a partir do snapshot imutável do documento; guardado numa tabela satélite nova (XML em texto + SHA-256 + IUD + timestamps), NÃO em `t_documento_fiscal` (imutável) nem no MinIO (o MinIO fica para o PDF na 137)
- IUD gerado no processamento em segundo plano (não na transação de emissão) e guardado na satélite; estrutura oficial 45 chars = `CV` + repositório(1) + `AAMMDD` + NIF(9) + LED(5) + tipo(2) + número(9) + aleatório(10, `SecureRandom`) + DV Luhn(1); FR = tipo 02, NC = tipo 05
- `RepositoryCode = 3` (Teste) sempre em SIMULADO; LED sintético reservado declarado como constante, só válido em SIMULADO (o `led_codigo` da série continua nulo)
- `Transmission` (`TransmitterTaxId`, `Software{Code,Name,Version}`) por propriedades de configuração com valores de teste válidos só em SIMULADO
- NC referencia o IUD da FR original em `References` e leva `IssueReasonCode` mapeado de `MotivoNotaCredito`
- Códigos de meio de pagamento (`MetodoPagamento` → PaymentMeansCode) confirmados contra a lista do pacote XSD nesta fase

### Adaptador
- `EFATURA_MODE` ao nível do deployment, default `SIMULADO`; qualquer outro valor (incluindo `REAL`) aborta o arranque com mensagem explícita (não há implementação real neste build); gravado em cada linha de comunicação (`ambiente`)
- `EfaturaGateway` (porta) com resultado selado (`AceiteSimulado`, `Rejeitado`, `ErroTransitorio`); `SimuladoEfaturaGateway` valida o XML contra o XSD → `ACEITE_SIMULADO` se válido, `REJEITADO` se inválido; falhas injetáveis em teste
- Estados: `PENDENTE` → `ACEITE_SIMULADO` | `REJEITADO` | `ERRO` (tentativas esgotadas); `AUTORIZADO` só com `ambiente = PRODUCAO`, garantido por CHECK na BD e por uma única função de mapeamento (resultado, ambiente) → estado; sem transição simulado→real
- `FiscalOutboxJob` `@Scheduled` (≈30 s) com `SELECT … FOR UPDATE SKIP LOCKED`, lease, backoff exponencial, máximo 8 tentativas → `ERRO`; padrão `AlertasDiariosJob` (sem SecurityContext, tenantId explícito, `catch Throwable` por item); NÃO salta tenants suspensos; aumentar `spring.task.scheduling.pool.size` para ≥ 3 (hoje 1 thread — bloquearia o job das 06:00)
- A comunicação corre sempre fora da transação de emissão; nenhum I/O dentro dela

### UI e notificação
- Badge de estado neutro na lista e no detalhe (Pendente, Aceite (simulação), Rejeitado, Erro); estados nunca usam a cor de acento
- Botão "Reprocessar comunicação" no detalhe para `ERRO`/`REJEITADO`, gated `financeiro:edit` (backend + frontend exatos); repõe a linha em PENDENTE com tentativas a zero
- IUD mostrado em texto no detalhe com a marca "Ambiente de teste — sem validade fiscal"; banner permanente "Modo simulado" enquanto `EFATURA_MODE=SIMULADO`
- Notificação in-app quando a comunicação passa a `ERRO` definitivo, para os utilizadores do escritório com `financeiro:manage`; nova categoria (ex. `COMUNICACAO_FISCAL_FALHOU`) no sistema `Notificacao` existente (dedup por documento)

### Portão das fontes primárias
- A pesquisa da fase tenta outra vez aceder a `efatura.cv` (docs/xsd, manual); se continuar bloqueado, compara as duas cópias públicas e regista as diferenças/suposições; no fim da fase o orquestrador pergunta ao utilizador se fecha a fase com o portão pendente

### Claude's Discretion
- Nomes de classes/tabelas, intervalo exato do job, formato do backoff, divisão em planos

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ComunicacaoFiscal` (PENDENTE, `@Version`) já criada pela 134/135 em cada emissão; `EstadoComunicacaoFiscal` + converter; `DocumentoFiscal`/`DocumentoFiscalLinha` snapshot imutável; `MetodoPagamento`, `MotivoNotaCredito`
- `AlertasDiariosJob` (padrão de job), `SchedulingConfig` (1 thread), `NotificacaoService` (categorias, dedup), `DocumentoFiscalController`/`DocumentoFiscalService`, web detalhe/lista de documentos fiscais, `podeRegistarPagamentos`-style exact gates
- Pesquisa: `.planning/research/STACK.md` (modelo técnico, bibliotecas, experiências), `ARCHITECTURE.md` §5, `PITFALLS.md` P-07..P-09, P-17, P-27

### Established Patterns
- Enums varchar `@Convert` + `length` (sem CHECK gerado), exceto o CHECK explícito de AUTORIZADO⇒PRODUCAO no script
- Migração manual idempotente + linha README no mesmo commit; variáveis de ambiente em `.env.example`, três compose e `deploy.yml`
- SpotBugs: excluir por pacote o código JAXB gerado; nenhuma exclusão para código próprio

### Integration Points
- `application.yml` (`EFATURA_MODE`, propriedades de transmissão, pool do scheduler), `backend/pom.xml` (JAXB + plugin)

</code_context>

<specifics>
## Specific Ideas

- Verificado na pesquisa: os XMLs oficiais de exemplo FRE/NCE validam contra o XSD sem assinatura; IUD do exemplo oficial bate com o Luhn sobre 42 dígitos
- Conflito do host do QR (services vs pe.efatura.cv) não é desta fase (PDF/QR na 137, e o PDF simulado não leva QR)

</specifics>

<deferred>
## Deferred Ideas

- Assinatura XAdES, OAuth2/credenciais, certificados ICP-CV, modo real por emitente, contingência — marco de ligação real (EFAT-01..06)

</deferred>
