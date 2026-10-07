package com.lexcv.fiscal.pdf;

/**
 * Phase 137 (ENTR-01; T-137-22): falha ao gerar o PDF de um documento fiscal. Mensagem fixa, sem
 * texto do documento; a causa original fica disponível para o log.
 */
public class FalhaGeracaoPdf extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String MENSAGEM = "Não foi possível gerar o PDF do documento fiscal";

    public FalhaGeracaoPdf(Throwable causa) {
        super(MENSAGEM, causa);
    }
}
