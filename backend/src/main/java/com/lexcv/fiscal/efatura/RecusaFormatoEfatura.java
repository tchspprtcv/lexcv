package com.lexcv.fiscal.efatura;

/**
 * Phase 136: o snapshot não pode ser expresso no formato eFatura. O processador (136-13) grava
 * {@link #codigo()} e {@link #mensagem()} na comunicação ({@code REJEITADO}, ou transitório para
 * {@code ORIGEM_SEM_IUD}).
 *
 * <p>A mensagem é SEMPRE o texto fixo do código, seguro para o utilizador: nunca transporta a
 * mensagem de uma causa nem valores do documento.
 */
public final class RecusaFormatoEfatura extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Códigos fixos com a respetiva mensagem em português. */
    public enum Codigo {
        FIRMA_EXCEDE_150("A firma do escritório tem mais de 150 caracteres. Corrija os dados fiscais."),
        NUMERO_FORA_DO_LIMITE("O número do documento está fora do limite do formato eFatura."),
        TEXTO_INVALIDO("Um texto do documento não cumpre o formato eFatura."),
        ORIGEM_SEM_IUD("A fatura-recibo de origem ainda não foi comunicada.");

        private final String mensagem;

        Codigo(String mensagem) {
            this.mensagem = mensagem;
        }

        public String mensagem() {
            return mensagem;
        }
    }

    private final Codigo tipo;

    public RecusaFormatoEfatura(Codigo tipo) {
        super(tipo.mensagem());
        this.tipo = tipo;
    }

    /** O código fixo (ex.: {@code FIRMA_EXCEDE_150}), gravado em {@code ultimo_erro_codigo}. */
    public String codigo() {
        return tipo.name();
    }

    /** A mensagem fixa do código, gravada em {@code ultimo_erro}. */
    public String mensagem() {
        return tipo.mensagem();
    }

    public Codigo tipo() {
        return tipo;
    }
}
