package com.lexcv.dtos;

import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MetodoPagamento;

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
        List<Linha> linhas
) {

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

    public static DocumentoFiscalDetalheResponse de(DocumentoFiscal d, List<DocumentoFiscalLinha> linhas,
                                                    EstadoComunicacaoFiscal estadoOuNulo) {
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
                linhas.stream().map(Linha::de).toList());
    }
}
