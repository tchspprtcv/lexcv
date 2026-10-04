package com.lexcv.services.fiscal;

import com.lexcv.models.RegimeIva;
import com.lexcv.services.fiscal.CalculoFiscal.ResultadoCalculo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 134 (EMIS-03, EMIS-04, D-06): vetores de arredondamento da RESEARCH (calculados com
 * Python {@code decimal}, HALF_UP) e teste de propriedade sobre 200 000 totais.
 */
class CalculoFiscalTest {

    private static final BigDecimal IVA = new BigDecimal("15");
    private static final BigDecimal RETENCAO = new BigDecimal("20");

    private static BigDecimal bd(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    @ParameterizedTest(name = "{0} {1} iva={2} ret={3} -> base={4} iva={5} ret={6} liq={7}")
    @CsvSource({
            // total,   regime, iva%, ret%,  base,      iva,       retencao,  liquido
            "120000.00, NORMAL, 15,  20,    104347.83, 15652.17,  20869.57,  99130.43",
            "0.01,      NORMAL, 15,    ,    0.01,      0.00,      0.00,      0.01",
            "0.05,      NORMAL, 15,  20,    0.04,      0.01,      0.01,      0.04",
            "1.00,      NORMAL, 15,  20,    0.87,      0.13,      0.17,      0.83",
            "100.00,    NORMAL, 15,  20,    86.96,     13.04,     17.39,     82.61",
            "115.00,    NORMAL, 15,  20,    100.00,    15.00,     20.00,     95.00",
            "33.33,     NORMAL, 15,  20,    28.98,     4.35,      5.80,      27.53",
            "999999.99, NORMAL, 15,  20,    869565.21, 130434.78, 173913.04, 826086.95",
            "12345.67,  NORMAL, 15,  100,   10735.37,  1610.30,   10735.37,  1610.30",
            "50000.00,  NORMAL, 15,  0.01,  43478.26,  6521.74,   4.35,      49995.65",
            "120000.00, ISENTO,   ,  20,    120000.00, 0.00,      24000.00,  96000.00",
            // Empate exato 0.005: HALF_UP dá 0.01 (HALF_EVEN daria 0.00).
            "0.04,      ISENTO,   ,  12.5,  0.04,      0.00,      0.01,      0.03"
    })
    void reproduzOsVetoresDaInvestigacao(String total, RegimeIva regime, String iva, String ret,
                                         String base, String ivaEsperado, String retencao, String liquido) {
        ResultadoCalculo r = CalculoFiscal.calcular(bd(total), regime, bd(iva), bd(ret));

        assertEquals(bd(base), r.base());
        assertEquals(bd(ivaEsperado), r.iva());
        assertEquals(bd(retencao), r.retencao());
        assertEquals(bd(liquido), r.liquidoRecebido());
        assertEquals(bd(total), r.total());
        assertInvariantes(r, bd(total));
    }

    @Test
    void propriedadeParaTodosOsTotaisDeUmCentimoADoisMil() {
        for (long centimos = 1; centimos <= 200_000; centimos++) {
            BigDecimal total = BigDecimal.valueOf(centimos, 2);
            assertInvariantes(CalculoFiscal.calcular(total, RegimeIva.NORMAL, IVA, RETENCAO), total);
            assertInvariantes(CalculoFiscal.calcular(total, RegimeIva.NORMAL, IVA, null), total);
        }
    }

    private static void assertInvariantes(ResultadoCalculo r, BigDecimal total) {
        String ctx = "total=" + total + " -> " + r;
        assertEquals(total, r.base().add(r.iva()), "base + iva != total: " + ctx);
        assertEquals(r.liquidoRecebido(), total.subtract(r.retencao()), "total - retencao != liquido: " + ctx);
        for (BigDecimal valor : new BigDecimal[]{r.base(), r.iva(), r.retencao(), r.total(), r.liquidoRecebido()}) {
            assertEquals(2, valor.scale(), "escala != 2: " + ctx);
            assertTrue(valor.signum() >= 0, "valor negativo: " + ctx);
        }
    }

    @Test
    void isentoIgnoraATaxaDeIvaMesmoNula() {
        ResultadoCalculo comTaxa = CalculoFiscal.calcular(new BigDecimal("500.00"), RegimeIva.ISENTO, IVA, null);
        ResultadoCalculo semTaxa = CalculoFiscal.calcular(new BigDecimal("500.00"), RegimeIva.ISENTO, null, null);
        for (ResultadoCalculo r : new ResultadoCalculo[]{comTaxa, semTaxa}) {
            assertEquals(0, r.taxaIva().signum());
            assertEquals(new BigDecimal("0.00"), r.iva());
            assertEquals(new BigDecimal("500.00"), r.base());
        }
    }

    @Test
    void semRetencaoATaxaFicaNulaEARetencaoZero() {
        ResultadoCalculo r = CalculoFiscal.calcular(new BigDecimal("115.00"), RegimeIva.NORMAL, IVA, null);
        assertNull(r.taxaRetencao());
        assertEquals(new BigDecimal("0.00"), r.retencao());
        assertEquals(new BigDecimal("115.00"), r.liquidoRecebido());
        assertEquals(IVA, r.taxaIva());
    }

    @Test
    void retencaoIncideSobreABaseENaoSobreOTotal() {
        ResultadoCalculo r = CalculoFiscal.calcular(new BigDecimal("115.00"), RegimeIva.NORMAL, IVA, RETENCAO);
        assertEquals(new BigDecimal("20.00"), r.retencao());
        assertEquals(RETENCAO, r.taxaRetencao());
    }

    @Test
    void normalSemTaxaDeIvaEErroDoChamador() {
        assertThrows(IllegalArgumentException.class,
                () -> CalculoFiscal.calcular(new BigDecimal("10.00"), RegimeIva.NORMAL, null, null));
    }

    @Test
    void totalComMaisDeDuasCasasEErroDoChamador() {
        assertThrows(ArithmeticException.class,
                () -> CalculoFiscal.calcular(new BigDecimal("10.005"), RegimeIva.NORMAL, IVA, null));
    }
}
