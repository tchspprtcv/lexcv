package com.lexcv.services.fiscal;

import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.Cliente;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.MetodoPagamento;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.Processo;
import com.lexcv.models.RegimeIva;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Phase 134 (D-01, D-04, D-05, D-06, D-12, D-13): a ÚNICA função que compõe uma Fatura-Recibo a
 * partir do pedido. A pré-visualização ({@link PreVisualizacaoFaturaService}) e a emissão
 * chamam-na com os mesmos argumentos, por isso as duas recusam com os mesmos códigos e calculam
 * os mesmos valores ("frontend burro").
 *
 * <p>Pura: sem Spring beans, sem base de dados, sem relógio. {@code hoje} (Cabo Verde) e a taxa de
 * IVA vigente nesse dia são calculados pelo chamador, a partir do mesmo {@code hoje}.
 *
 * <p>Ordem das verificações (a primeira falha ganha): configuração completa → firma do emitente
 * (≤ 150, WR-04 da 136) → valor pago → data
 * → método → retenção → NIF, nome e morada do adquirente.
 *
 * <p>Recebe só o id do honorário, nunca a entidade: a descrição livre do honorário (sigilo
 * profissional) não pode chegar à linha do documento (D-05). Um pagamento acima do valor em
 * falta do honorário NÃO é recusado aqui -- é deliberadamente permitido (R-02), à espera da
 * validação do contabilista.
 */
public final class ComposicaoFaturaRecibo {

    static final String MSG_CONFIGURACAO_INCOMPLETA =
            "Os dados fiscais do escritório estão incompletos. Complete-os em Definições → Faturação.";

    private ComposicaoFaturaRecibo() {
    }

    /**
     * @param taxaIvaOuNull taxa de IVA vigente em {@code hoje} (obrigatória em NORMAL, ignorada em ISENTO)
     * @throws RecusaFiscalException    422 com código e campo, pela ordem acima
     * @throws IllegalArgumentException regime NORMAL sem taxa de IVA (erro do chamador)
     */
    public static ProjetoFaturaRecibo compor(ConfiguracaoFiscal cfg, Cliente cliente, Processo processo,
                                             Integer honorarioId, PagamentoRequest req, LocalDate hoje,
                                             BigDecimal taxaIvaOuNull) {
        if (!cfg.completa()) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "CONFIGURACAO_FISCAL_INCOMPLETA",
                    MSG_CONFIGURACAO_INCOMPLETA);
        }
        // WR-04 (136): uma firma com mais de 150 caracteres nunca seria comunicada (eFatura Name).
        ValidacaoEmissao.validarFirmaEmitente(cfg.getFirma(), false);
        BigDecimal valor = ValidacaoEmissao.normalizarValor(req.valorPago());
        LocalDate data = ValidacaoEmissao.validarData(req.dataPagamento(), hoje);
        MetodoPagamento metodo = ValidacaoEmissao.validarMetodo(req.metodo());
        BigDecimal retencao = ValidacaoEmissao.validarRetencao(req.retencaoPercentagem());
        ValidacaoEmissao.validarAdquirente(cliente.getNif(), cliente.getNome(), cliente.getMorada(),
                cliente.getLocalidade());

        RegimeIva regime = cfg.getRegimeIva();
        CalculoFiscal.ResultadoCalculo calculo = CalculoFiscal.calcular(valor, regime, taxaIvaOuNull, retencao);
        MotivoIsencaoIva motivo = regime == RegimeIva.ISENTO
                ? MotivoIsencaoIva.porCodigo(cfg.getMotivoIsencaoCodigo()).orElse(null)
                : null;

        return new ProjetoFaturaRecibo(
                cliente.getId(),
                processo.getId(),
                honorarioId,
                data,
                metodo,
                TextoDocumentoFiscal.descricaoLinhaHonorarios(processo.getNumeroProcesso()),
                regime,
                motivo,
                calculo,
                cfg.getNif(),
                cfg.getFirma().trim(),
                cfg.getMorada().trim(),
                limparOuNulo(cfg.getLocalidade()),
                cliente.getNif(),
                cliente.getNome().trim(),
                cliente.getMorada().trim(),
                limparOuNulo(cliente.getLocalidade()));
    }

    private static String limparOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
