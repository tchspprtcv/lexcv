package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.TipoDocumentoFiscal;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Phase 135 (NCRD-01, NCRD-02): validações puras do pedido de emissão de uma Nota de Crédito. Cada
 * recusa é um {@link RecusaFiscalException} com código e o campo do pedido em causa
 * ({@code GlobalExceptionHandler} devolve {@code {message, code, campo}}).
 *
 * <p>Sem Spring (além de {@link HttpStatus}), sem base de dados e sem relógio. As regras do valor
 * são as de {@link ValidacaoEmissao#normalizarValor} (positivo, no máximo 2 casas, cabe em
 * {@code numeric(19,2)}), mas com código e campo próprios da NC. O teto cumulativo
 * ({@code NC_EXCEDE_ORIGINAL}, 409) vive em {@link ComposicaoNotaCredito}, porque depende das NC
 * já emitidas.
 */
public final class ValidacaoNotaCredito {

    /** Igual a {@code t_documento_fiscal.motivo_texto VARCHAR(200)} (migração 135, UI-SPEC). */
    public static final int MOTIVO_TEXTO_MAX = 200;

    /** Precisão de {@code numeric(19,2)}: nenhum valor pode ser arredondado em silêncio (P-19). */
    private static final int PRECISAO_MAXIMA = 19;
    private static final int CASAS_DECIMAIS = 2;

    static final String MSG_NC_SOBRE_NC = "Não é possível creditar uma nota de crédito. "
            + "Emita a nota de crédito sobre a fatura-recibo original.";
    static final String MSG_TIPO = "Escolha crédito total ou parcial.";
    static final String MSG_MOTIVO = "Escolha o motivo da nota de crédito.";
    static final String MSG_MOTIVO_TEXTO = "Descreva o motivo da nota de crédito (no máximo 200 caracteres).";
    static final String MSG_VALOR = "Indique um valor superior a 0, com no máximo duas casas decimais.";

    private ValidacaoNotaCredito() {
    }

    private static RecusaFiscalException recusa(String codigo, String mensagem, String campo) {
        return new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem, campo);
    }

    /** {@code TOTAL} ou {@code PARCIAL}, ignorando espaços à volta e maiúsculas/minúsculas. */
    public static TipoCredito validarTipo(String tipo) {
        if (tipo != null) {
            String limpo = tipo.trim();
            return Arrays.stream(TipoCredito.values()).filter(t -> t.name().equalsIgnoreCase(limpo)).findFirst()
                    .orElseThrow(() -> recusa("TIPO_CREDITO_INVALIDO", MSG_TIPO, "tipo"));
        }
        throw recusa("TIPO_CREDITO_INVALIDO", MSG_TIPO, "tipo");
    }

    /** Motivo da lista fechada {@link MotivoNotaCredito} (pelo {@code name()}). */
    public static MotivoNotaCredito validarMotivo(String motivoCodigo) {
        return MotivoNotaCredito.porNome(motivoCodigo)
                .orElseThrow(() -> recusa("MOTIVO_NC_INVALIDO", MSG_MOTIVO, "motivoCodigo"));
    }

    /** Texto livre obrigatório, sem espaços à volta, com no máximo {@value #MOTIVO_TEXTO_MAX} caracteres. */
    public static String validarMotivoTexto(String motivoTexto) {
        String limpo = motivoTexto == null ? "" : motivoTexto.trim();
        if (limpo.isEmpty() || limpo.length() > MOTIVO_TEXTO_MAX) {
            throw recusa("MOTIVO_NC_OBRIGATORIO", MSG_MOTIVO_TEXTO, "motivoTexto");
        }
        return limpo;
    }

    /**
     * Valor do crédito parcial (IVA incluído): positivo, no máximo 2 casas decimais (depois de
     * retirar zeros à direita) e que caiba em {@code numeric(19,2)}. Devolve o valor com escala 2.
     */
    public static BigDecimal validarValorParcial(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > CASAS_DECIMAIS) {
            throw recusaValor();
        }
        BigDecimal normalizado = valor.setScale(CASAS_DECIMAIS, RoundingMode.UNNECESSARY);
        if (normalizado.precision() > PRECISAO_MAXIMA) {
            throw recusaValor();
        }
        return normalizado;
    }

    /** Recusa do campo {@code valor} (também usada quando um crédito TOTAL traz um valor). */
    static RecusaFiscalException recusaValor() {
        return recusa("VALOR_CREDITO_INVALIDO", MSG_VALOR, "valor");
    }

    /** Só uma Fatura-Recibo pode ser creditada: creditar uma NC é recusado ({@code NC_SOBRE_NC}). */
    public static void exigirFaturaRecibo(DocumentoFiscal origem) {
        if (origem.getTipo() != TipoDocumentoFiscal.FR) {
            throw recusa("NC_SOBRE_NC", MSG_NC_SOBRE_NC, null);
        }
    }
}
