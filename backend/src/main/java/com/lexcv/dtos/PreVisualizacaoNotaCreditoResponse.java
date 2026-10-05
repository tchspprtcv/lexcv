package com.lexcv.dtos;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.fiscal.CalculoFiscal;
import com.lexcv.services.fiscal.ProjetoNotaCredito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 135 (NCRD-01, NCRD-02): pré-visualização da Nota de Crédito, calculada no servidor pela
 * mesma função pura que a emissão usa ({@code ComposicaoNotaCredito.compor}). O frontend só mostra
 * estes valores, nunca os calcula.
 *
 * <p>Todos os montantes são magnitudes POSITIVAS (o sinal negativo vive só no {@code Pagamento} de
 * estorno). O adquirente e o motivo de isenção vêm do SNAPSHOT da Fatura-Recibo de origem, nunca do
 * cliente atual: o adquirente da NC é o da FR. A chave de idempotência nunca é exposta.
 * Nesta fase o tipo é sempre "NC" e o ambiente "SIMULADO".
 */
public record PreVisualizacaoNotaCreditoResponse(
        String tipo,
        String tipoRotulo,
        String ambiente,
        UUID documentoOrigemId,
        String documentoOrigemNumero,
        String adquirenteNome,
        String adquirenteNif,
        String adquirenteMorada,
        String adquirenteLocalidade,
        String descricaoLinha,
        String regimeIva,
        BigDecimal taxaIva,
        String motivoIsencaoCodigo,
        String motivoIsencaoDescricao,
        BigDecimal base,
        BigDecimal iva,
        BigDecimal taxaRetencao,
        BigDecimal retencao,
        BigDecimal total,
        BigDecimal liquido,
        String tipoCredito,
        String motivoCodigo,
        String motivoRotulo,
        String motivoTexto,
        BigDecimal totalOrigem,
        BigDecimal valorCreditavelAntes,
        BigDecimal valorCreditavelDepois,
        LocalDate dataEmissao
) {

    public static PreVisualizacaoNotaCreditoResponse de(DocumentoFiscal origem, ProjetoNotaCredito p) {
        CalculoFiscal.ResultadoCalculo c = p.calculo();
        RegimeIva regime = p.regime();
        boolean isento = regime == RegimeIva.ISENTO;
        return new PreVisualizacaoNotaCreditoResponse(
                TipoDocumentoFiscal.NC.name(),
                TipoDocumentoFiscal.NC.rotulo(),
                AmbienteFiscal.SIMULADO.name(),
                p.documentoOrigemId(),
                p.documentoOrigemNumero(),
                origem.getAdquirenteNome(),
                origem.getAdquirenteNif(),
                origem.getAdquirenteMorada(),
                origem.getAdquirenteLocalidade(),
                p.descricaoLinha(),
                regime == null ? null : regime.name(),
                isento ? null : c.taxaIva(),
                origem.getEmitenteMotivoIsencaoCodigo(),
                origem.getEmitenteMotivoIsencaoDescricao(),
                c.base(),
                c.iva(),
                c.taxaRetencao(),
                c.retencao(),
                c.total(),
                c.liquidoRecebido(),
                p.tipoCredito() == null ? null : p.tipoCredito().name(),
                p.motivo() == null ? null : p.motivo().name(),
                p.motivo() == null ? null : p.motivo().rotulo(),
                p.motivoTexto(),
                p.totalOrigem(),
                p.valorCreditavelAntes(),
                p.valorCreditavelDepois(),
                p.dataEmissao());
    }
}
