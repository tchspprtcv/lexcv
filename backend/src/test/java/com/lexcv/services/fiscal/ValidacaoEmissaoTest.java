package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.MetodoPagamento;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 134 (EMIS-05, EMIS-06, D-03, D-04, D-05, D-12, D-13, D-20): regras 422 puras de
 * {@link ValidacaoEmissao} e textos controlados de {@link TextoDocumentoFiscal}. Cada recusa
 * verifica estado, código e campo.
 */
class ValidacaoEmissaoTest {

    private static final String NIF_VALIDO = "512345679";
    private static final String NOME_VALIDO = "Ana Lopes";
    private static final String MORADA_VALIDA = "Rua A, Praia";

    private static RecusaFiscalException recusa(Executable chamada, String codigo, String campo) {
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, chamada);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertEquals(codigo, e.getCodigo());
        assertEquals(campo, e.getCampo());
        assertTrue(e.getMessage() != null && !e.getMessage().isBlank());
        return e;
    }

    @Nested
    class NormalizarValor {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"0", "0.00", "-1", "100.005", "100.0050", "123456789012345678", "123456789012345678.00"})
        void recusaValoresInvalidos(String valor) {
            RecusaFiscalException e = recusa(
                    () -> ValidacaoEmissao.normalizarValor(valor == null ? null : new BigDecimal(valor)),
                    "VALOR_PAGO_INVALIDO", "valorPago");
            assertEquals("O valor pago deve ser positivo, com no máximo duas casas decimais.", e.getMessage());
        }

        @Test
        void normalizaParaDuasCasas() {
            assertEquals(new BigDecimal("1000.00"), ValidacaoEmissao.normalizarValor(new BigDecimal("1e3")));
            assertEquals(new BigDecimal("100.10"), ValidacaoEmissao.normalizarValor(new BigDecimal("100.10")));
            assertEquals(new BigDecimal("100.10"), ValidacaoEmissao.normalizarValor(new BigDecimal("100.100")));
            assertEquals(new BigDecimal("0.01"), ValidacaoEmissao.normalizarValor(new BigDecimal("0.01")));
            assertEquals(2, ValidacaoEmissao.normalizarValor(new BigDecimal("7")).scale());
        }

        @Test
        void aceitaOMaiorValorQueCabeEmNumeric19Virgula2() {
            BigDecimal maximo = new BigDecimal("99999999999999999.99");
            assertEquals(maximo, ValidacaoEmissao.normalizarValor(maximo));
        }
    }

    @Nested
    class ValidarData {

        private final LocalDate hoje = LocalDate.of(2026, 10, 4);

        @Test
        void omitidaPassaASerHoje() {
            assertEquals(hoje, ValidacaoEmissao.validarData(null, hoje));
        }

        @Test
        void hojeEAceite() {
            assertEquals(hoje, ValidacaoEmissao.validarData(LocalDate.of(2026, 10, 4), hoje));
        }

        @Test
        void ontemERecusada() {
            RecusaFiscalException e = recusa(() -> ValidacaoEmissao.validarData(hoje.minusDays(1), hoje),
                    "DATA_PAGAMENTO_RETROATIVA", "dataPagamento");
            assertEquals("Com a faturação ativa, a data do pagamento tem de ser a de hoje. "
                    + "Escolha a data de hoje ou deixe o campo vazio.", e.getMessage());
        }

        @Test
        void amanhaERecusada() {
            recusa(() -> ValidacaoEmissao.validarData(hoje.plusDays(1), hoje), "DATA_PAGAMENTO_RETROATIVA", "dataPagamento");
        }
    }

    @Nested
    class ValidarMetodo {

        @Test
        void aceitaUmMetodoConhecido() {
            assertSame(MetodoPagamento.DINHEIRO, ValidacaoEmissao.validarMetodo("DINHEIRO"));
            assertSame(MetodoPagamento.TRANSFERENCIA, ValidacaoEmissao.validarMetodo("transferencia"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "Transferência", "PIX"})
        void recusaMetodoEmFaltaOuDesconhecido(String metodo) {
            RecusaFiscalException e = recusa(() -> ValidacaoEmissao.validarMetodo(metodo),
                    "METODO_PAGAMENTO_INVALIDO", "metodo");
            assertEquals("Escolha o método de pagamento.", e.getMessage());
        }
    }

    @Nested
    class ValidarRetencao {

        @Test
        void semRetencaoDevolveNulo() {
            assertNull(ValidacaoEmissao.validarRetencao(null));
        }

        @Test
        void aceitaEValoresNormalizados() {
            assertEquals(new BigDecimal("20.00"), ValidacaoEmissao.validarRetencao(new BigDecimal("20")));
            assertEquals(new BigDecimal("0.01"), ValidacaoEmissao.validarRetencao(new BigDecimal("0.01")));
            assertEquals(new BigDecimal("100.00"), ValidacaoEmissao.validarRetencao(new BigDecimal("100")));
            assertEquals(new BigDecimal("12.50"), ValidacaoEmissao.validarRetencao(new BigDecimal("12.5")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-5", "100.01", "12.345"})
        void recusaForaDoIntervalo(String taxa) {
            RecusaFiscalException e = recusa(() -> ValidacaoEmissao.validarRetencao(new BigDecimal(taxa)),
                    "RETENCAO_INVALIDA", "retencaoPercentagem");
            assertEquals("A taxa de retenção deve ser superior a 0 e não superior a 100.", e.getMessage());
        }
    }

    @Nested
    class ValidarAdquirente {

        @Test
        void adquirenteValidoPassa() {
            ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, MORADA_VALIDA);
            ValidacaoEmissao.validarAdquirente(NIF_VALIDO, "Ana", "R".repeat(100));
            ValidacaoEmissao.validarAdquirente(NIF_VALIDO, "  " + "N".repeat(150) + "  ", "  Rua B  ");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"012345678", "12345678", "1234567890", "51234567A", ""})
        void nifInvalido(String nif) {
            RecusaFiscalException e = recusa(() -> ValidacaoEmissao.validarAdquirente(nif, NOME_VALIDO, MORADA_VALIDA),
                    "ADQUIRENTE_INCOMPLETO", "nif");
            assertEquals("O cliente não tem um NIF válido. Corrija o NIF do cliente (9 dígitos, começa por 1 a 9) "
                    + "e tente de novo.", e.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "Ab", "  Ab  "})
        void nomeCurtoOuEmFalta(String nome) {
            RecusaFiscalException e = recusa(() -> ValidacaoEmissao.validarAdquirente(NIF_VALIDO, nome, MORADA_VALIDA),
                    "ADQUIRENTE_INCOMPLETO", "nome");
            assertEquals("O nome do cliente deve ter entre 3 e 150 caracteres. Corrija o cliente e tente de novo.",
                    e.getMessage());
        }

        @Test
        void nomeComMaisDe150Caracteres() {
            recusa(() -> ValidacaoEmissao.validarAdquirente(NIF_VALIDO, "N".repeat(151), MORADA_VALIDA),
                    "ADQUIRENTE_INCOMPLETO", "nome");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void moradaEmFalta(String morada) {
            RecusaFiscalException e = recusa(() -> ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, morada),
                    "ADQUIRENTE_INCOMPLETO", "morada");
            assertEquals("A morada do cliente é obrigatória e não pode ter mais de 100 caracteres. "
                    + "Corrija o cliente e tente de novo.", e.getMessage());
        }

        @Test
        void moradaComMaisDe100Caracteres() {
            recusa(() -> ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, "R".repeat(101)),
                    "ADQUIRENTE_INCOMPLETO", "morada");
        }

        @Test
        void localidadeOpcionalAte100CaracteresPassa() {
            ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, MORADA_VALIDA, null);
            ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, MORADA_VALIDA, "   ");
            ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, MORADA_VALIDA, "L".repeat(100));
            ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, MORADA_VALIDA, "  " + "L".repeat(100) + "  ");
        }

        @Test
        void localidadeComMaisDe100CaracteresERecusada() {
            RecusaFiscalException e = recusa(
                    () -> ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, MORADA_VALIDA, "L".repeat(101)),
                    "ADQUIRENTE_INCOMPLETO", "localidade");
            assertEquals("A localidade do cliente não pode ter mais de 100 caracteres. "
                    + "Corrija o cliente e tente de novo.", e.getMessage());
        }

        @Test
        void aPrimeiraFalhaGanhaPorOrdemNifNomeMorada() {
            recusa(() -> ValidacaoEmissao.validarAdquirente(null, null, null), "ADQUIRENTE_INCOMPLETO", "nif");
            recusa(() -> ValidacaoEmissao.validarAdquirente(NIF_VALIDO, "x", null), "ADQUIRENTE_INCOMPLETO", "nome");
            recusa(() -> ValidacaoEmissao.validarAdquirente(NIF_VALIDO, NOME_VALIDO, null, "L".repeat(101)),
                    "ADQUIRENTE_INCOMPLETO", "morada");
        }
    }

    @Nested
    class ExigirChave {

        @Test
        void chaveEmFaltaERecusada() {
            RecusaFiscalException e = recusa(() -> ValidacaoEmissao.exigirChave(null),
                    "CHAVE_IDEMPOTENCIA_OBRIGATORIA", "chaveIdempotencia");
            assertEquals("Pedido sem chave de idempotência. Reabra a confirmação e tente de novo.", e.getMessage());
        }

        @Test
        void chavePresenteEDevolvida() {
            UUID chave = UUID.randomUUID();
            assertSame(chave, ValidacaoEmissao.exigirChave(chave));
        }
    }

    @Nested
    class Textos {

        @Test
        void descricaoComNumeroDoProcesso() {
            assertEquals("Honorários por serviços jurídicos — Processo n.º 123/2026",
                    TextoDocumentoFiscal.descricaoLinhaHonorarios("123/2026"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void descricaoSemNumero(String numero) {
            assertEquals("Honorários por serviços jurídicos", TextoDocumentoFiscal.descricaoLinhaHonorarios(numero));
            assertEquals(TextoDocumentoFiscal.DESCRICAO_HONORARIOS, TextoDocumentoFiscal.descricaoLinhaHonorarios(numero));
        }

        @Test
        void descricaoNuncaPassaDe200Caracteres() {
            String descricao = TextoDocumentoFiscal.descricaoLinhaHonorarios("9".repeat(500));
            assertEquals(200, descricao.length());
            assertTrue(descricao.startsWith("Honorários por serviços jurídicos — Processo n.º 999"));
        }

        @Test
        void numeroFormatado() {
            assertEquals("SIM-FR-2026/1", TextoDocumentoFiscal.numeroFormatado("SIM-FR-2026", 1));
            assertEquals("SIM-FR-2026/1234", TextoDocumentoFiscal.numeroFormatado("SIM-FR-2026", 1234L));
        }
    }
}
