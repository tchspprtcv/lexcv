package com.lexcv.fiscal.efatura;

/**
 * Resultado da validação de um DFE contra o esquema oficial. Só transporta um código fixo e a
 * posição do erro -- nunca a mensagem do parser, que pode citar nomes, NIF ou valores do
 * documento (T-136-19). A mensagem bruta só vai para o log em DEBUG.
 */
public record ResultadoValidacao(boolean valido, String codigo, int linha, int coluna) {

    /** O documento viola o esquema XSD (valor, padrão, cardinalidade, tipo). */
    public static final String XSD_INVALIDO = "XSD_INVALIDO";
    /** O documento tem DOCTYPE/entidades -- recusado antes de qualquer resolução (XXE). */
    public static final String XML_PROIBIDO = "XML_PROIBIDO";
    /** Não foi possível ler o documento (vazio, nulo, erro de leitura). */
    public static final String XML_ILEGIVEL = "XML_ILEGIVEL";

    private static final ResultadoValidacao VALIDO = new ResultadoValidacao(true, null, 0, 0);

    /** Fábrica do resultado válido ({@code valido()} é o acessor do componente do record). */
    public static ResultadoValidacao sucesso() {
        return VALIDO;
    }

    public static ResultadoValidacao invalido(String codigo, int linha, int coluna) {
        return new ResultadoValidacao(false, codigo, linha, coluna);
    }
}
