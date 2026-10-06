package com.lexcv.fiscal.efatura;

import com.lexcv.models.AmbienteFiscal;

/**
 * Phase 136 (DFE-03): a ÚNICA porta por onde um documento fiscal sai do sistema.
 *
 * <p>Neste build só existe {@link SimuladoEfaturaGateway}, que valida o XML contra o XSD e nunca
 * contacta a rede. A implementação real (assinatura XAdES, OAuth2, envio à plataforma eFatura)
 * pertence ao milestone de ligação real e não existe aqui; o bean é escolhido em arranque por
 * {@code EfaturaConfig} (136-12), que aborta para qualquer modo que não seja SIMULADO.
 *
 * <p>O resultado é selado ({@link ResultadoComunicacao}): uma aceitação simulada nunca pode ser
 * confundida com uma autorização da DNRE.
 */
public interface EfaturaGateway {

    /** O ambiente que esta implementação serve. */
    AmbienteFiscal ambiente();

    /**
     * Comunica um documento. Nunca lança por causa do conteúdo: qualquer problema vira um
     * resultado com código e mensagem fixos.
     */
    ResultadoComunicacao comunicar(PedidoComunicacao pedido);
}
