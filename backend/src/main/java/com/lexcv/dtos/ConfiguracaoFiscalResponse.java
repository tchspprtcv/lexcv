package com.lexcv.dtos;

import com.lexcv.models.RegimeIva;

import java.time.Instant;

/**
 * Phase 133 (CFG-01/02/03/06): leitura da configuração fiscal do escritório.
 *
 * <ul>
 *   <li>{@code configurada = false} quando o tenant nunca guardou dados (nenhuma linha é criada
 *       pela leitura -- CFG-03).</li>
 *   <li>{@code pais} é sempre "Cabo Verde".</li>
 *   <li>{@code nifBloqueado == documentosEmitidos}; {@code podeDesativar == ativa && !documentosEmitidos}.
 *       São indicações para a UI; o servidor volta a verificar em cada pedido.</li>
 *   <li>{@code envioEmailAceitePorNome} só é preenchido quando o envio está ligado e o utilizador
 *       pertence ao mesmo tenant.</li>
 * </ul>
 */
public record ConfiguracaoFiscalResponse(
        boolean configurada,
        String nif,
        String firma,
        String morada,
        String localidade,
        String pais,
        String emailContacto,
        String telefoneContacto,
        RegimeIva regimeIva,
        String motivoIsencaoCodigo,
        boolean completa,
        boolean ativa,
        boolean documentosEmitidos,
        boolean nifBloqueado,
        boolean podeDesativar,
        boolean envioEmailAutomatico,
        String envioEmailAceitePorNome,
        Instant envioEmailAceiteEm
) {
    public static final String PAIS = "Cabo Verde";
}
