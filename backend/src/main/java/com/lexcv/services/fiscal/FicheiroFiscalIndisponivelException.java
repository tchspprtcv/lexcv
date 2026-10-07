package com.lexcv.services.fiscal;

/**
 * Phase 137 (ENTR-01, ENTR-02): o ficheiro fiscal (PDF) ainda não pode ser produzido porque o
 * documento ainda não tem XML/IUD. Não é um erro: o documento acabou de ser emitido e a
 * comunicação ainda não correu. Mensagem e código fixos; o endpoint de download (137-15) mapeia-a
 * para 503.
 */
public class FicheiroFiscalIndisponivelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String CODIGO = "FICHEIRO_INDISPONIVEL";
    public static final String MENSAGEM =
            "O ficheiro ainda não está disponível. Aguarde um momento e tente novamente.";

    public FicheiroFiscalIndisponivelException() {
        super(MENSAGEM);
    }

    public String codigo() {
        return CODIGO;
    }
}
