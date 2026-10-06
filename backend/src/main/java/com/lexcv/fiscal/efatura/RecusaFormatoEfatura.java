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
        /**
         * WR-04: o documento é imutável e guarda a firma com que foi emitido, por isso nenhum
         * reprocessamento o corrige; a mensagem não o pode sugerir. A emissão nova já é recusada
         * ({@code ValidacaoEmissao.validarFirmaEmitente}).
         */
        FIRMA_EXCEDE_150("A firma do escritório tem mais de 150 caracteres. Este documento mantém a firma com que "
                + "foi emitido e reprocessar não o corrige. Corrija os dados fiscais para os próximos documentos."),
        NUMERO_FORA_DO_LIMITE("O número do documento está fora do limite do formato eFatura."),
        TEXTO_INVALIDO("Um texto do documento não cumpre o formato eFatura."),
        /**
         * WR-03: o snapshot imutável tem um dado que não cabe no IUD ou na projeção (ex.: NIF do
         * emitente inválido, campo obrigatório em falta). Determinístico: nenhuma nova tentativa o
         * corrige.
         */
        DADOS_INVALIDOS("Os dados do documento não podem ser expressos no formato eFatura."),
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
