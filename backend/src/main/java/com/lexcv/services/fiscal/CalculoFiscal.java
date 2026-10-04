package com.lexcv.services.fiscal;

import com.lexcv.models.RegimeIva;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Phase 134 (EMIS-03, EMIS-04, D-06): calculadora fiscal pura, partilhada pela pré-visualização e
 * pela emissão -- uma única função decide todos os valores, por isso os dois caminhos nunca
 * divergem ("frontend burro", D-01). Sem Spring, sem base de dados.
 *
 * <p>Regras (pendentes de validação por contabilista, ver STATE.md Pending Todos):
 * <ul>
 *   <li>O valor pago é sempre IVA incluído. Regime NORMAL:
 *       {@code base = round(total * 100 / (100 + taxaIva), 2, HALF_UP)} numa só divisão, e
 *       {@code iva = total - base} (o resíduo do arredondamento vai para o IVA), logo
 *       {@code base + iva == total} sempre.</li>
 *   <li>Regime ISENTO: {@code base = total}, IVA zero, taxa zero.</li>
 *   <li>Retenção na fonte (opcional, D-03): incide sobre a BASE, nunca sobre o total;
 *       {@code retencao = round(base * taxa / 100, 2, HALF_UP)};
 *       {@code liquidoRecebido = total - retencao}.</li>
 * </ul>
 *
 * <p>As taxas NUNCA são constantes no código: o chamador lê-as de {@code ParametroFiscalService}
 * (gate {@code ParametrosFiscaisSemConstantesTest}). O total chega já normalizado a 2 casas por
 * {@link ValidacaoEmissao#normalizarValor}; um total com mais casas é erro do chamador.
 */
public final class CalculoFiscal {

    private static final BigDecimal CEM = new BigDecimal("100");

    private CalculoFiscal() {
    }

    /**
     * Resultado do cálculo. Montantes com escala 2; {@code taxaIva} e {@code taxaRetencao} são
     * percentagens ({@code taxaRetencao} nula quando não há retenção).
     */
    public record ResultadoCalculo(BigDecimal base, BigDecimal iva, BigDecimal taxaIva,
                                   BigDecimal retencao, BigDecimal taxaRetencao,
                                   BigDecimal total, BigDecimal liquidoRecebido) {
    }

    /**
     * @param total                 valor pago, IVA incluído, já com no máximo 2 casas decimais
     * @param regime                regime de IVA do emitente
     * @param taxaIvaPct            taxa de IVA em percentagem (obrigatória em NORMAL; ignorada em ISENTO)
     * @param taxaRetencaoPctOuNull taxa de retenção em percentagem, ou {@code null} sem retenção
     * @throws IllegalArgumentException se faltar total, regime ou (em NORMAL) a taxa de IVA
     * @throws ArithmeticException      se o total tiver mais de 2 casas decimais
     */
    public static ResultadoCalculo calcular(BigDecimal total, RegimeIva regime, BigDecimal taxaIvaPct,
                                            BigDecimal taxaRetencaoPctOuNull) {
        if (total == null || regime == null) {
            throw new IllegalArgumentException("total e regime são obrigatórios");
        }
        BigDecimal t = total.setScale(2, RoundingMode.UNNECESSARY);

        BigDecimal base;
        BigDecimal iva;
        BigDecimal taxaIva;
        if (regime == RegimeIva.ISENTO) {
            base = t;
            iva = BigDecimal.ZERO.setScale(2);
            taxaIva = BigDecimal.ZERO;
        } else {
            if (taxaIvaPct == null) {
                throw new IllegalArgumentException("Regime NORMAL exige a taxa de IVA");
            }
            taxaIva = taxaIvaPct;
            base = t.multiply(CEM).divide(CEM.add(taxaIvaPct), 2, RoundingMode.HALF_UP);
            iva = t.subtract(base);
        }

        BigDecimal retencao = taxaRetencaoPctOuNull == null
                ? BigDecimal.ZERO.setScale(2)
                : base.multiply(taxaRetencaoPctOuNull).divide(CEM, 2, RoundingMode.HALF_UP);

        return new ResultadoCalculo(base, iva, taxaIva, retencao, taxaRetencaoPctOuNull, t, t.subtract(retencao));
    }
}
