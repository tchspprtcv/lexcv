package com.lexcv.models;

/**
 * Estado da comunicação de um documento fiscal à plataforma eFatura, guardado no satélite mutável
 * {@code t_comunicacao_fiscal}.
 *
 * <p>Phase 134 (D-08) criou {@link #PENDENTE}; a Phase 136 fixa os estados finais do v3.0:
 * {@code PENDENTE -> ACEITE_SIMULADO | REJEITADO | ERRO}. {@link #ACEITE_SIMULADO} é o resultado
 * do adaptador simulado e nunca significa autorização da DNRE. {@link #ERRO} só chega quando as
 * tentativas automáticas se esgotam.
 *
 * <p>Não existe constante "autorizado" neste build (nem ambiente de produção em
 * {@link AmbienteFiscal}): AUTORIZADO só existe como proibição na base de dados, pelo
 * {@code CHECK ck_comunicacao_fiscal_autorizado_producao} ({@code estado <> 'AUTORIZADO' OR
 * ambiente = 'PRODUCAO'}). A coluna continua {@code varchar(32)} gravada por
 * {@link EstadoComunicacaoFiscalConverter} (sem {@code CHECK} de enum).
 */
public enum EstadoComunicacaoFiscal {
    PENDENTE("Pendente"),
    ACEITE_SIMULADO("Aceite (simulação)"),
    REJEITADO("Rejeitado"),
    ERRO("Erro");

    private final String rotulo;

    EstadoComunicacaoFiscal(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /** {@code true} para os estados em que o "Reprocessar comunicação" é permitido. */
    public boolean reprocessavel() {
        return this == REJEITADO || this == ERRO;
    }

    /** {@code true} quando o job de comunicação já não volta a pegar na linha sozinho. */
    public boolean terminal() {
        return this == ACEITE_SIMULADO || this == REJEITADO || this == ERRO;
    }
}
