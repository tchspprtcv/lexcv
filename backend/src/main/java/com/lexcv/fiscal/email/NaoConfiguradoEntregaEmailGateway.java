package com.lexcv.fiscal.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 137 (ENTR-06): a porta de email quando não há SMTP configurado. O job de envio não a chama
 * (sai antes, 137-19); isto é só uma rede de segurança que nunca envia nada.
 */
public final class NaoConfiguradoEntregaEmailGateway implements EntregaEmailGateway {

    private static final Logger log = LoggerFactory.getLogger(NaoConfiguradoEntregaEmailGateway.class);

    public static final String CODIGO = "SMTP_NAO_CONFIGURADO";
    public static final String MENSAGEM = "O servidor de email não está configurado nesta instalação.";

    @Override
    public boolean configurado() {
        return false;
    }

    @Override
    public ResultadoEnvioEmail enviar(MensagemEmailFiscal mensagem) {
        log.warn("Envio de email pedido sem servidor SMTP configurado; nada foi enviado");
        return new ResultadoEnvioEmail.ErroTransitorio(CODIGO, MENSAGEM);
    }
}
