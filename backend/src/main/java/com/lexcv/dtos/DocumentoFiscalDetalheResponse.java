package com.lexcv.dtos;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MetodoPagamento;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.fiscal.ComposicaoNotaCredito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phase 134 (D-18, EMIS-11): detalhe só de leitura de um documento fiscal -- a fotografia do
 * emitente e do adquirente, os valores, as linhas, o estado da comunicação e as ligações à origem
 * (cliente, processo, honorário, pagamento).
 *
 * <p>Exclui deliberadamente a chave de idempotência (interna; a UI-SPEC proíbe mostrá-la) e o id
 * do utilizador que emitiu (minimização de dados, ASVS V8): só o nome fotografado é devolvido.
 *
 * <p>Phase 135 (NCRD-01..03): numa Nota de Crédito, {@code documentoOrigem} referencia a
 * Fatura-Recibo corrigida e {@code motivoCodigo}/{@code motivoRotulo}/{@code motivoTexto} trazem o
 * motivo; numa Fatura-Recibo, {@code notasCredito} lista as NC emitidas (mais recentes primeiro),
 * {@code totalCreditado} é a soma dos seus totais e {@code valorCreditavelRestante} o total da FR
 * menos essa soma -- calculados aqui, no backend, nunca na UI. Os montantes das NC são magnitudes
 * positivas (o sinal negativo vive só no pagamento de estorno). Campos que não se aplicam ao tipo
 * do documento são nulos ({@code notasCredito} é uma lista vazia).
 *
 * <p>Phase 136 (DFE-04, DFE-06): {@code comunicacao} resume o estado da comunicação, as
 * tentativas e o IUD ({@link ComunicacaoFiscalResumo}); nulo quando ainda não há linha de
 * comunicação. {@code estadoComunicacao} mantém-se para a listagem e os clientes antigos.
 *
 * <p>Phase 137 (ENTR-04, ENTR-06): {@code entregaEmail} resume a entrega por email
 * ({@link EntregaEmailResumo}); nulo quando o documento ainda não tem linha de entrega.
 */
public record DocumentoFiscalDetalheResponse(
        UUID id,
        String numeroFormatado,
        String tipo,
        String tipoRotulo,
        String ambiente,
        String serieCodigo,
        Integer ano,
        Long numero,
        LocalDate dataEmissao,
        Instant emitidoEm,
        String emitenteNif,
        String emitenteFirma,
        String emitenteMorada,
        String emitenteLocalidade,
        String emitenteRegimeIva,
        String emitenteMotivoIsencaoCodigo,
        String emitenteMotivoIsencaoDescricao,
        String emitenteMotivoIsencaoMencao,
        String adquirenteNif,
        String adquirenteNome,
        String adquirenteMorada,
        String adquirenteLocalidade,
        UUID clienteId,
        UUID processoId,
        Integer honorarioId,
        Integer pagamentoId,
        String metodoPagamento,
        String metodoPagamentoRotulo,
        String meioPagamentoCodigo,
        String moeda,
        BigDecimal taxaIva,
        BigDecimal totalBase,
        BigDecimal totalIva,
        BigDecimal taxaRetencao,
        BigDecimal totalRetencao,
        BigDecimal totalDocumento,
        BigDecimal valorLiquido,
        String estadoComunicacao,
        String emitidoPorNome,
        List<Linha> linhas,
        DocumentoFiscalRef documentoOrigem,
        String motivoCodigo,
        String motivoRotulo,
        String motivoTexto,
        BigDecimal totalCreditado,
        BigDecimal valorCreditavelRestante,
        List<NotaCreditoResumo> notasCredito,
        ComunicacaoFiscalResumo comunicacao,
        EntregaEmailResumo entregaEmail
) {

    /** Cópias imutáveis das listas (EI_EXPOSE_REP; mesmo idioma de {@code WorkflowResponse}). */
    public DocumentoFiscalDetalheResponse {
        linhas = linhas == null ? List.of() : List.copyOf(linhas);
        notasCredito = notasCredito == null ? List.of() : List.copyOf(notasCredito);
    }

    /** Phase 135: uma NC emitida sobre a FR em detalhe (total positivo). */
    public record NotaCreditoResumo(
            UUID id,
            String numeroFormatado,
            LocalDate dataEmissao,
            String motivoCodigo,
            String motivoRotulo,
            BigDecimal totalDocumento
    ) {
        public static NotaCreditoResumo de(DocumentoFiscal nc) {
            return new NotaCreditoResumo(nc.getId(), nc.getNumeroFormatado(), nc.getDataEmissao(),
                    nc.getMotivoCodigo() == null ? null : nc.getMotivoCodigo().name(),
                    nc.getMotivoCodigo() == null ? null : nc.getMotivoCodigo().rotulo(),
                    nc.getTotalDocumento());
        }
    }

    /** Uma linha do documento (nesta fase há sempre exatamente uma). */
    public record Linha(
            Integer numeroLinha,
            String descricao,
            BigDecimal quantidade,
            BigDecimal precoUnitario,
            BigDecimal valorBase,
            BigDecimal taxaIva,
            BigDecimal valorIva,
            String motivoIsencaoCodigo,
            BigDecimal taxaRetencao,
            BigDecimal valorRetencao,
            BigDecimal totalLinha
    ) {
        public static Linha de(DocumentoFiscalLinha l) {
            return new Linha(l.getNumeroLinha(), l.getDescricao(), l.getQuantidade(), l.getPrecoUnitario(),
                    l.getValorBase(), l.getTaxaIva(), l.getValorIva(), l.getMotivoIsencaoCodigo(),
                    l.getTaxaRetencao(), l.getValorRetencao(), l.getTotalLinha());
        }
    }

    /** Sem dados de Nota de Crédito (origem nula, lista de NC vazia). */
    public static DocumentoFiscalDetalheResponse de(DocumentoFiscal d, List<DocumentoFiscalLinha> linhas,
                                                    EstadoComunicacaoFiscal estadoOuNulo) {
        return de(d, linhas, estadoOuNulo, null, List.of());
    }

    /**
     * Phase 135.
     *
     * @param origemOuNulo FR de origem quando {@code d} é uma NC, senão {@code null}
     * @param notasCredito NC emitidas sobre {@code d} quando é uma FR (pela ordem a mostrar), senão vazia
     */
    public static DocumentoFiscalDetalheResponse de(DocumentoFiscal d, List<DocumentoFiscalLinha> linhas,
                                                    EstadoComunicacaoFiscal estadoOuNulo,
                                                    DocumentoFiscalRef origemOuNulo,
                                                    List<DocumentoFiscal> notasCredito) {
        return de(d, linhas, estadoOuNulo, origemOuNulo, notasCredito, null);
    }

    /**
     * Phase 136.
     *
     * @param resumoOuNulo resumo da comunicação, ou {@code null} sem linha de comunicação
     */
    public static DocumentoFiscalDetalheResponse de(DocumentoFiscal d, List<DocumentoFiscalLinha> linhas,
                                                    EstadoComunicacaoFiscal estadoOuNulo,
                                                    DocumentoFiscalRef origemOuNulo,
                                                    List<DocumentoFiscal> notasCredito,
                                                    ComunicacaoFiscalResumo resumoOuNulo) {
        return de(d, linhas, estadoOuNulo, origemOuNulo, notasCredito, resumoOuNulo, null);
    }

    /**
     * Phase 137.
     *
     * @param entregaOuNulo resumo da entrega por email, ou {@code null} sem linha de entrega
     */
    public static DocumentoFiscalDetalheResponse de(DocumentoFiscal d, List<DocumentoFiscalLinha> linhas,
                                                    EstadoComunicacaoFiscal estadoOuNulo,
                                                    DocumentoFiscalRef origemOuNulo,
                                                    List<DocumentoFiscal> notasCredito,
                                                    ComunicacaoFiscalResumo resumoOuNulo,
                                                    EntregaEmailResumo entregaOuNulo) {
        List<DocumentoFiscal> ncs = notasCredito == null ? List.of() : notasCredito;
        boolean fr = d.getTipo() == TipoDocumentoFiscal.FR;
        BigDecimal totalCreditado = null;
        BigDecimal restante = null;
        if (fr) {
            // IN-03 da revisão: a mesma definição que a composição e a emissão da NC usam.
            totalCreditado = ComposicaoNotaCredito.totalCreditado(ncs);
            restante = ComposicaoNotaCredito.valorCreditavelRestante(d, ncs);
        }
        return new DocumentoFiscalDetalheResponse(
                d.getId(),
                d.getNumeroFormatado(),
                d.getTipo().name(),
                d.getTipo().rotulo(),
                d.getAmbiente().name(),
                d.getSerieCodigo(),
                d.getAno(),
                d.getNumero(),
                d.getDataEmissao(),
                d.getEmitidoEm(),
                d.getEmitenteNif(),
                d.getEmitenteFirma(),
                d.getEmitenteMorada(),
                d.getEmitenteLocalidade(),
                d.getEmitenteRegimeIva() == null ? null : d.getEmitenteRegimeIva().name(),
                d.getEmitenteMotivoIsencaoCodigo(),
                d.getEmitenteMotivoIsencaoDescricao(),
                d.getEmitenteMotivoIsencaoMencao(),
                d.getAdquirenteNif(),
                d.getAdquirenteNome(),
                d.getAdquirenteMorada(),
                d.getAdquirenteLocalidade(),
                d.getClienteId(),
                d.getProcessoId(),
                d.getHonorarioId(),
                d.getPagamentoId(),
                d.getMetodoPagamento(),
                MetodoPagamento.porNome(d.getMetodoPagamento()).map(MetodoPagamento::rotulo)
                        .orElse(d.getMetodoPagamento()),
                d.getMeioPagamentoCodigo(),
                d.getMoeda(),
                d.getTaxaIva(),
                d.getTotalBase(),
                d.getTotalIva(),
                d.getTaxaRetencao(),
                d.getTotalRetencao(),
                d.getTotalDocumento(),
                d.getValorLiquido(),
                estadoOuNulo == null ? null : estadoOuNulo.name(),
                d.getEmitidoPorNome(),
                linhas.stream().map(Linha::de).toList(),
                origemOuNulo,
                d.getMotivoCodigo() == null ? null : d.getMotivoCodigo().name(),
                d.getMotivoCodigo() == null ? null : d.getMotivoCodigo().rotulo(),
                d.getMotivoTexto(),
                totalCreditado,
                restante,
                fr ? ncs.stream().map(NotaCreditoResumo::de).toList() : List.of(),
                resumoOuNulo,
                entregaOuNulo);
    }
}
