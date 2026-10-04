package com.lexcv.dtos;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.services.fiscal.CalculoFiscal;
import com.lexcv.services.fiscal.ProjetoFaturaRecibo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 134 (EMIS-02, D-01): pré-visualização da Fatura-Recibo, calculada no servidor pela mesma
 * função que a emissão usa. O frontend só mostra estes valores, nunca os calcula.
 * Nesta fase o tipo é sempre "FR" e o ambiente "SIMULADO".
 */
public record PreVisualizacaoFaturaResponse(
        String tipo,
        String tipoRotulo,
        String ambiente,
        UUID clienteId,
        String adquirenteNome,
        String adquirenteNif,
        String adquirenteMorada,
        String adquirenteLocalidade,
        String descricaoLinha,
        RegimeIva regimeIva,
        BigDecimal taxaIva,
        String motivoIsencaoCodigo,
        String motivoIsencaoDescricao,
        String motivoIsencaoMencao,
        BigDecimal base,
        BigDecimal iva,
        BigDecimal taxaRetencao,
        BigDecimal retencao,
        BigDecimal total,
        BigDecimal liquidoRecebido,
        String metodo,
        String metodoRotulo,
        LocalDate dataEmissao
) {

    public static PreVisualizacaoFaturaResponse de(ProjetoFaturaRecibo p) {
        CalculoFiscal.ResultadoCalculo c = p.calculo();
        MotivoIsencaoIva motivo = p.motivoIsencao();
        return new PreVisualizacaoFaturaResponse(
                TipoDocumentoFiscal.FR.name(),
                TipoDocumentoFiscal.FR.rotulo(),
                AmbienteFiscal.SIMULADO.name(),
                p.clienteId(),
                p.adquirenteNome(),
                p.adquirenteNif(),
                p.adquirenteMorada(),
                p.adquirenteLocalidade(),
                p.descricaoLinha(),
                p.regime(),
                c.taxaIva(),
                motivo == null ? null : motivo.codigo(),
                motivo == null ? null : motivo.descricao(),
                motivo == null ? null : motivo.mencao(),
                c.base(),
                c.iva(),
                c.taxaRetencao(),
                c.retencao(),
                c.total(),
                c.liquidoRecebido(),
                p.metodo().name(),
                p.metodo().rotulo(),
                p.dataEmissao());
    }
}
