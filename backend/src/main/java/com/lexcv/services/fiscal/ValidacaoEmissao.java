package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.MetodoPagamento;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 134 (EMIS-05, EMIS-06, D-03, D-04, D-12, D-13): validações puras do pedido de emissão de
 * uma Fatura-Recibo. Cada recusa é um {@link RecusaFiscalException} 422 com código e o campo do
 * pedido em causa ({@code GlobalExceptionHandler} devolve {@code {message, code, campo}}); as
 * mensagens são o texto da UI-SPEC, palavra por palavra.
 *
 * <p>Sem Spring (além de {@link HttpStatus}) e sem base de dados. Esta classe nunca lê o relógio:
 * o "hoje" de Cabo Verde é sempre um parâmetro calculado pelo chamador no servidor (P-20), de
 * modo que nenhuma data do cliente é tida como referência.
 */
public final class ValidacaoEmissao {

    public static final int NOME_MIN = 3;
    public static final int NOME_MAX = 150;

    /** Precisão de {@code numeric(19,2)}: nenhum valor pode ser arredondado em silêncio (P-19). */
    private static final int PRECISAO_MAXIMA = 19;
    private static final int CASAS_DECIMAIS = 2;
    private static final BigDecimal CEM = new BigDecimal("100");

    private static final Pattern NIF_FISCAL = Pattern.compile(ConfiguracaoFiscal.NIF_FISCAL_REGEX);

    static final String MSG_VALOR = "O valor pago deve ser positivo, com no máximo duas casas decimais.";
    static final String MSG_DATA = "Com a faturação ativa, a data do pagamento tem de ser a de hoje. "
            + "Escolha a data de hoje ou deixe o campo vazio.";
    static final String MSG_METODO = "Escolha o método de pagamento.";
    static final String MSG_RETENCAO = "A taxa de retenção deve ser superior a 0 e não superior a 100.";
    static final String MSG_NIF = "O cliente não tem um NIF válido. "
            + "Corrija o NIF do cliente (9 dígitos, começa por 1 a 9) e tente de novo.";
    static final String MSG_NOME = "O nome do cliente deve ter entre 3 e 150 caracteres. "
            + "Corrija o cliente e tente de novo.";
    static final String MSG_MORADA = "A morada do cliente é obrigatória e não pode ter mais de 100 caracteres. "
            + "Corrija o cliente e tente de novo.";
    static final String MSG_CHAVE = "Pedido sem chave de idempotência. Reabra a confirmação e tente de novo.";

    private ValidacaoEmissao() {
    }

    private static RecusaFiscalException recusa(String codigo, String mensagem, String campo) {
        return new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem, campo);
    }

    /**
     * Valor pago: positivo, no máximo 2 casas decimais (depois de retirar zeros à direita) e que
     * caiba em {@code numeric(19,2)}. Devolve o valor com escala 2.
     */
    public static BigDecimal normalizarValor(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > CASAS_DECIMAIS) {
            throw recusa("VALOR_PAGO_INVALIDO", MSG_VALOR, "valorPago");
        }
        BigDecimal normalizado = valor.setScale(CASAS_DECIMAIS, RoundingMode.UNNECESSARY);
        if (normalizado.precision() > PRECISAO_MAXIMA) {
            throw recusa("VALOR_PAGO_INVALIDO", MSG_VALOR, "valorPago");
        }
        return normalizado;
    }

    /**
     * D-12: com a faturação ativa a data do pagamento é sempre a de hoje (Cabo Verde). Omitida
     * passa a ser {@code hoje}; qualquer outra data é recusada.
     */
    public static LocalDate validarData(LocalDate pedida, LocalDate hoje) {
        if (pedida == null) {
            return hoje;
        }
        if (!pedida.equals(hoje)) {
            throw recusa("DATA_PAGAMENTO_RETROATIVA", MSG_DATA, "dataPagamento");
        }
        return pedida;
    }

    /** D-04: método obrigatório e pertencente ao conjunto fechado {@link MetodoPagamento}. */
    public static MetodoPagamento validarMetodo(String metodo) {
        return MetodoPagamento.porNome(metodo)
                .orElseThrow(() -> recusa("METODO_PAGAMENTO_INVALIDO", MSG_METODO, "metodo"));
    }

    /**
     * D-03: retenção opcional ({@code null} = sem retenção); quando presente,
     * {@code 0 < taxa <= 100} com no máximo 2 casas decimais. Devolve a taxa com escala 2.
     */
    public static BigDecimal validarRetencao(BigDecimal taxa) {
        if (taxa == null) {
            return null;
        }
        if (taxa.signum() <= 0 || taxa.compareTo(CEM) > 0 || taxa.stripTrailingZeros().scale() > CASAS_DECIMAIS) {
            throw recusa("RETENCAO_INVALIDA", MSG_RETENCAO, "retencaoPercentagem");
        }
        return taxa.setScale(CASAS_DECIMAIS, RoundingMode.UNNECESSARY);
    }

    /**
     * D-13, EMIS-06: dados mínimos do adquirente, verificados pela ordem NIF, nome, morada (a
     * primeira falha ganha). Nome e morada são avaliados sem os espaços à volta.
     */
    public static void validarAdquirente(String nif, String nome, String morada) {
        if (nif == null || !NIF_FISCAL.matcher(nif).matches()) {
            throw recusa("ADQUIRENTE_INCOMPLETO", MSG_NIF, "nif");
        }
        String nomeLimpo = nome == null ? "" : nome.trim();
        if (nomeLimpo.length() < NOME_MIN || nomeLimpo.length() > NOME_MAX) {
            throw recusa("ADQUIRENTE_INCOMPLETO", MSG_NOME, "nome");
        }
        String moradaLimpa = morada == null ? "" : morada.trim();
        if (moradaLimpa.isEmpty() || moradaLimpa.length() > ConfiguracaoFiscal.MORADA_MAX) {
            throw recusa("ADQUIRENTE_INCOMPLETO", MSG_MORADA, "morada");
        }
    }

    /** A chave de idempotência é obrigatória no caminho com faturação ativa. */
    public static UUID exigirChave(UUID chave) {
        if (chave == null) {
            throw recusa("CHAVE_IDEMPOTENCIA_OBRIGATORIA", MSG_CHAVE, "chaveIdempotencia");
        }
        return chave;
    }
}
