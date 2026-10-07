package com.lexcv.services.fiscal;

import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.EstadoEntregaEmail;

import java.util.Optional;

/**
 * Phase 137 (ENTR-03, ENTR-04, ENTR-06): regras puras e partilhadas da entrega por email de um
 * documento fiscal. "Frontend burro" (137-UI-SPEC): o backend calcula o estado apresentado e se o
 * reenvio é permitido; a UI mostra os valores tal como chegam.
 *
 * <p>Fonte única: o lado de leitura (137-11), o serviço de reenvio (137-12), o processador do job
 * (137-14) e o enfileiramento (137-17) chamam todos estes métodos -- nenhum deles reimplementa a
 * regra.
 */
public final class RegrasEntregaEmail {

    /** Estado de apresentação derivado quando o SMTP não está configurado; nunca é gravado. */
    public static final String NAO_CONFIGURADO = "NAO_CONFIGURADO";

    /** RFC 5321: comprimento máximo de um caminho de envio. */
    private static final int MAX_EMAIL = 254;

    /** Separadores que nunca podem aparecer num destinatário único (listas, nomes, comentários). */
    private static final String SEPARADORES = ",;<>()[]\\\":";

    private RegrasEntregaEmail() {
    }

    /**
     * Estado mostrado ao utilizador. Sem SMTP configurado, as entregas que ainda não tentaram
     * enviar ({@code PENDENTE}, {@code DESLIGADO}, {@code SEM_EMAIL}) aparecem como
     * {@link #NAO_CONFIGURADO}; {@code ENVIADO} e {@code FALHOU} mantêm o histórico.
     *
     * @return {@code null} quando {@code guardado} é {@code null} (documento sem linha de entrega)
     */
    public static String estadoApresentado(EstadoEntregaEmail guardado, boolean smtpConfigurado) {
        if (guardado == null) {
            return null;
        }
        if (!smtpConfigurado && guardado != EstadoEntregaEmail.ENVIADO && guardado != EstadoEntregaEmail.FALHOU) {
            return NAO_CONFIGURADO;
        }
        return guardado.name();
    }

    /**
     * "Reenviar email" só é permitido a partir de {@code FALHOU}, {@code ENVIADO} ou
     * {@code SEM_EMAIL} ({@link EstadoEntregaEmail#reenviavelPorEstado()}), com SMTP configurado,
     * envio automático ligado, comunicação {@code ACEITE_SIMULADO} e o cliente com email válido
     * agora. Nunca a partir de {@code DESLIGADO} (opção do escritório) nem de {@code PENDENTE}.
     */
    public static boolean reenviavel(EstadoEntregaEmail guardado, boolean smtpConfigurado, boolean envioAutomatico,
                                     EstadoComunicacaoFiscal comunicacao, boolean clienteTemEmail) {
        return guardado != null
                && guardado.reenviavelPorEstado()
                && smtpConfigurado
                && envioAutomatico
                && comunicacao == EstadoComunicacaoFiscal.ACEITE_SIMULADO
                && clienteTemEmail;
    }

    /**
     * Validação conservadora do destinatário (T-137-15): recusa tudo o que possa injetar
     * cabeçalhos SMTP ou designar vários destinatários -- caracteres de controlo (incluindo CR/LF
     * e TAB), espaços, separadores, mais de um {@code @}, parte local ou domínio vazios, domínio
     * sem ponto e mais de 254 caracteres. O adaptador SMTP (137-08) volta a analisar o endereço
     * com {@code InternetAddress} em modo estrito.
     *
     * @return o endereço aparado, ou vazio quando inválido
     */
    public static Optional<String> emailValido(String bruto) {
        if (bruto == null) {
            return Optional.empty();
        }
        String email = bruto.strip();
        if (email.isEmpty() || email.length() > MAX_EMAIL) {
            return Optional.empty();
        }
        for (int i = 0; i < email.length(); i++) {
            char c = email.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || SEPARADORES.indexOf(c) >= 0) {
                return Optional.empty();
            }
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0 || arroba != email.lastIndexOf('@')) {
            return Optional.empty();
        }
        String dominio = email.substring(arroba + 1);
        if (dominio.isEmpty() || dominio.startsWith(".") || dominio.endsWith(".")
                || !dominio.contains(".") || dominio.contains("..")) {
            return Optional.empty();
        }
        return Optional.of(email);
    }

    /** {@code ultimoErro} só é visível em {@code FALHOU} e {@code PENDENTE}; {@code null} nos outros. */
    public static String ultimoErroVisivel(EstadoEntregaEmail estado, String ultimoErro) {
        if (estado == EstadoEntregaEmail.FALHOU || estado == EstadoEntregaEmail.PENDENTE) {
            return ultimoErro;
        }
        return null;
    }
}
