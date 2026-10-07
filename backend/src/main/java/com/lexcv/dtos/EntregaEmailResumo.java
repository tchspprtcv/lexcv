package com.lexcv.dtos;

import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import com.lexcv.services.fiscal.RegrasEntregaEmail;

import java.time.Instant;

/**
 * Phase 137 (ENTR-04, ENTR-06): entrega por email de um documento fiscal no detalhe. Tudo é
 * calculado no backend com {@link RegrasEntregaEmail} (fonte única); a UI mostra os valores tal como
 * chegam (137-UI-SPEC).
 *
 * <ul>
 *   <li>{@code estado}: o estado apresentado ({@code NAO_CONFIGURADO} sem SMTP para as entregas
 *       que ainda não tentaram enviar).</li>
 *   <li>{@code destinatario}: o endereço gravado na linha; nulo em {@code SEM_EMAIL} e
 *       {@code NAO_CONFIGURADO}.</li>
 *   <li>{@code emailDestinatario}: o email ATUAL da ficha do cliente (mesmo tenant), nulo quando
 *       ausente ou inválido.</li>
 *   <li>{@code ultimoErro}: mensagem fixa e sanitizada, só em {@code FALHOU} e {@code PENDENTE}
 *       (nunca em {@code NAO_CONFIGURADO}); {@code proximaTentativaEm} só em {@code PENDENTE}.</li>
 *   <li>{@code reenviavel}: {@link RegrasEntregaEmail#reenviavel}.</li>
 * </ul>
 */
public record EntregaEmailResumo(
        String estado,
        String destinatario,
        String emailDestinatario,
        int tentativas,
        Instant ultimaTentativaEm,
        Instant proximaTentativaEm,
        Instant enviadoEm,
        String ultimoErro,
        boolean reenviavel
) {

    /**
     * @param linha                    a linha de entrega; {@code null} devolve {@code null}
     * @param smtp                     o SMTP está configurado nesta instalação
     * @param envioAutomatico          a opção de envio automático do escritório
     * @param comunicacao              estado da comunicação do documento (ou {@code null})
     * @param emailClienteAtualOuNulo  email atual da ficha do cliente, já validado, ou {@code null}
     */
    public static EntregaEmailResumo de(EntregaEmailFiscal linha, boolean smtp, boolean envioAutomatico,
                                        EstadoComunicacaoFiscal comunicacao, String emailClienteAtualOuNulo) {
        if (linha == null) {
            return null;
        }
        EstadoEntregaEmail guardado = linha.getEstado();
        String apresentado = RegrasEntregaEmail.estadoApresentado(guardado, smtp);
        boolean naoConfigurado = RegrasEntregaEmail.NAO_CONFIGURADO.equals(apresentado);
        String emailAtual = RegrasEntregaEmail.emailValido(emailClienteAtualOuNulo).orElse(null);
        boolean semDestinatario = naoConfigurado || guardado == EstadoEntregaEmail.SEM_EMAIL;
        return new EntregaEmailResumo(
                apresentado,
                semDestinatario ? null : linha.getDestinatario(),
                emailAtual,
                linha.getTentativas() == null ? 0 : linha.getTentativas(),
                linha.getUltimaTentativaEm(),
                guardado == EstadoEntregaEmail.PENDENTE && !naoConfigurado ? linha.getProximaTentativaEm() : null,
                linha.getEnviadoEm(),
                naoConfigurado ? null : RegrasEntregaEmail.ultimoErroVisivel(guardado, linha.getUltimoErro()),
                RegrasEntregaEmail.reenviavel(guardado, smtp, envioAutomatico, comunicacao, emailAtual != null));
    }
}
