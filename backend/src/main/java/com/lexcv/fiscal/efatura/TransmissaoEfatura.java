package com.lexcv.fiscal.efatura;

import java.util.regex.Pattern;

/**
 * Phase 136 (G9): dados do bloco {@code Transmission} -- NIF do transmissor e identificação do
 * software. Validado no construtor, para que uma configuração inválida falhe no arranque
 * (136-12) e nunca produza XML recusado pelo esquema.
 *
 * <p>Limites do XSD: {@code stSoftwareCode} {@code [A-Z0-9]}, até 10; {@code Name} 3–150;
 * {@code stSoftwareVersion} 1–50 sem espaços extra.
 */
public record TransmissaoEfatura(String nifTransmissor, String softwareCodigo, String softwareNome,
                                 String softwareVersao) {

    private static final Pattern NIF = Pattern.compile("^[1-9]\\d{8}$");
    private static final Pattern CODIGO = Pattern.compile("^[A-Z0-9]{1,10}$");
    private static final int VERSAO_MAX = 50;

    /** Mensagens fixas por campo (o arranque, 136-12, traduz cada uma na propriedade). */
    public static final String MSG_NIF = "NIF do transmissor inválido (9 dígitos, o primeiro de 1 a 9).";
    public static final String MSG_CODIGO = "Código do software inválido (A-Z e 0-9, até 10 caracteres).";
    public static final String MSG_NOME = "Nome do software inválido (3 a 150 caracteres, sem espaços extra).";
    public static final String MSG_VERSAO = "Versão do software inválida (1 a 50 caracteres, sem espaços extra).";

    public TransmissaoEfatura {
        if (nifTransmissor == null || !NIF.matcher(nifTransmissor).matches()) {
            throw new IllegalArgumentException(MSG_NIF);
        }
        if (softwareCodigo == null || !CODIGO.matcher(softwareCodigo).matches()) {
            throw new IllegalArgumentException(MSG_CODIGO);
        }
        if (softwareNome == null || !semEspacosExtra(softwareNome)
                || softwareNome.length() < MapeamentoEfatura.MIN_NOME
                || softwareNome.length() > MapeamentoEfatura.MAX_NOME) {
            throw new IllegalArgumentException(MSG_NOME);
        }
        if (softwareVersao == null || softwareVersao.isBlank() || !semEspacosExtra(softwareVersao)
                || softwareVersao.length() > VERSAO_MAX) {
            throw new IllegalArgumentException(MSG_VERSAO);
        }
    }

    private static boolean semEspacosExtra(String s) {
        return s.equals(s.trim()) && !s.contains("  ") && s.chars().noneMatch(c -> c == '\t' || c == '\n' || c == '\r');
    }
}
