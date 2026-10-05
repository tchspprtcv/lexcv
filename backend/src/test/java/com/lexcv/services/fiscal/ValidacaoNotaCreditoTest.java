package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 135 (NCRD-01, NCRD-02): regras 422 puras de {@link ValidacaoNotaCredito} e o texto da linha
 * de uma Nota de Crédito. Cada recusa verifica estado, código, campo e mensagem.
 */
class ValidacaoNotaCreditoTest {

    private static RecusaFiscalException recusa(Executable chamada, String codigo, String campo, String mensagem) {
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, chamada);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(codigo, e.getCodigo());
        assertEquals(campo, e.getCampo());
        assertEquals(mensagem, e.getMessage());
        return e;
    }

    @Nested
    class ValidarTipo {

        @Test
        void aceitaTotalEParcialSemEspacosNemMaiusculas() {
            assertEquals(TipoCredito.TOTAL, ValidacaoNotaCredito.validarTipo("TOTAL"));
            assertEquals(TipoCredito.PARCIAL, ValidacaoNotaCredito.validarTipo(" parcial "));
            assertEquals(TipoCredito.TOTAL, ValidacaoNotaCredito.validarTipo("Total"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "PARCIALMENTE", "NC", "1"})
        void recusaOutros(String tipo) {
            recusa(() -> ValidacaoNotaCredito.validarTipo(tipo), "TIPO_CREDITO_INVALIDO", "tipo",
                    "Escolha crédito total ou parcial.");
        }
    }

    @Nested
    class ValidarMotivo {

        @Test
        void aceitaOsNomesDoEnum() {
            for (MotivoNotaCredito m : MotivoNotaCredito.values()) {
                assertEquals(m, ValidacaoNotaCredito.validarMotivo(m.name()));
            }
            assertEquals(MotivoNotaCredito.OUTRO, ValidacaoNotaCredito.validarMotivo(" outro "));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"NAO_EXISTE", "Anulação total"})
        void recusaDesconhecidos(String motivo) {
            recusa(() -> ValidacaoNotaCredito.validarMotivo(motivo), "MOTIVO_NC_INVALIDO", "motivoCodigo",
                    "Escolha o motivo da nota de crédito.");
        }
    }

    @Nested
    class ValidarMotivoTexto {

        private static final String MSG = "Descreva o motivo da nota de crédito (no máximo 200 caracteres).";

        @Test
        void devolveTextoSemEspacos() {
            assertEquals("Valor faturado a mais", ValidacaoNotaCredito.validarMotivoTexto("  Valor faturado a mais "));
        }

        @Test
        void aceitaExatamente200() {
            String texto = "a".repeat(200);
            assertEquals(texto, ValidacaoNotaCredito.validarMotivoTexto("  " + texto + "  "));
            assertEquals(200, ValidacaoNotaCredito.MOTIVO_TEXTO_MAX);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        void recusaVazio(String texto) {
            recusa(() -> ValidacaoNotaCredito.validarMotivoTexto(texto), "MOTIVO_NC_OBRIGATORIO", "motivoTexto", MSG);
        }

        @Test
        void recusaAcimaDe200() {
            recusa(() -> ValidacaoNotaCredito.validarMotivoTexto("a".repeat(201)), "MOTIVO_NC_OBRIGATORIO",
                    "motivoTexto", MSG);
        }
    }

    @Nested
    class ValidarValorParcial {

        private static final String MSG = "Indique um valor superior a 0, com no máximo duas casas decimais.";

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "100.005", "123456789012345678", "123456789012345678.00"})
        void recusaInvalidos(String valor) {
            recusa(() -> ValidacaoNotaCredito.validarValorParcial(valor == null ? null : new BigDecimal(valor)),
                    "VALOR_CREDITO_INVALIDO", "valor", MSG);
        }

        @Test
        void normalizaParaDuasCasas() {
            assertEquals(new BigDecimal("20000.00"), ValidacaoNotaCredito.validarValorParcial(new BigDecimal("20000")));
            assertEquals(new BigDecimal("0.01"), ValidacaoNotaCredito.validarValorParcial(new BigDecimal("0.010")));
            assertEquals(new BigDecimal("1000.00"), ValidacaoNotaCredito.validarValorParcial(new BigDecimal("1e3")));
        }
    }

    @Nested
    class ExigirFaturaRecibo {

        @Test
        void notaDeCreditoERecusada() {
            DocumentoFiscal nc = DocumentoFiscal.builder().tipo(TipoDocumentoFiscal.NC).build();
            RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                    () -> ValidacaoNotaCredito.exigirFaturaRecibo(nc));
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
            assertEquals("NC_SOBRE_NC", e.getCodigo());
            assertNull(e.getCampo());
            assertEquals("Não é possível creditar uma nota de crédito. "
                    + "Emita a nota de crédito sobre a fatura-recibo original.", e.getMessage());
        }

        @Test
        void faturaReciboPassa() {
            DocumentoFiscal fr = DocumentoFiscal.builder().tipo(TipoDocumentoFiscal.FR).build();
            assertDoesNotThrow(() -> ValidacaoNotaCredito.exigirFaturaRecibo(fr));
        }
    }

    @Nested
    class DescricaoLinha {

        @Test
        void textoComONumeroDaOrigem() {
            assertEquals("Crédito sobre a fatura-recibo SIM-FR-2026/1",
                    TextoDocumentoFiscal.descricaoLinhaNotaCredito("SIM-FR-2026/1"));
        }

        @Test
        void truncadoAoMaximo() {
            String d = TextoDocumentoFiscal.descricaoLinhaNotaCredito("X".repeat(500));
            assertEquals(TextoDocumentoFiscal.DESCRICAO_MAX, d.length());
            assertEquals("Crédito sobre a fatura-recibo X", d.substring(0, 31));
        }
    }
}
