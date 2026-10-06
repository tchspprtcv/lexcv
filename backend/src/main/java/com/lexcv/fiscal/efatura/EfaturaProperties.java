package com.lexcv.fiscal.efatura;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Phase 136 (DFE-03): configuração {@code app.efatura.*}.
 *
 * <ul>
 *   <li>{@code modo} ({@code EFATURA_MODE}): só {@code SIMULADO} existe neste build; qualquer
 *       outro valor impede o arranque ({@link EfaturaConfig}).</li>
 *   <li>{@code transmissao}: bloco {@code Transmission} do DFE (G9). Valores sintéticos de teste,
 *       válidos só em SIMULADO; os reais pertencem ao milestone de ligação real.</li>
 *   <li>{@code outbox}: cadência e tamanho do job de comunicação (136-15).</li>
 *   <li>{@code simulado.falhas-forcadas}: alavanca de teste que torna toda a comunicação simulada
 *       transitoriamente falhada; nunca produz uma aceitação.</li>
 * </ul>
 */
@ConfigurationProperties("app.efatura")
public record EfaturaProperties(
        String modo,
        @DefaultValue Transmissao transmissao,
        @DefaultValue Outbox outbox,
        @DefaultValue Simulado simulado) {

    /** Dados do transmissor e do software (validados por {@link TransmissaoEfatura}). */
    public record Transmissao(String nifTransmissor, String softwareCodigo, String softwareNome,
                              String softwareVersao) {
    }

    /** Job de comunicação: intervalo entre execuções, atraso inicial, lote e lease. */
    public record Outbox(
            @DefaultValue("PT30S") Duration intervalo,
            @DefaultValue("PT20S") Duration atrasoInicial,
            @DefaultValue("20") int lote,
            @DefaultValue("PT2M") Duration lease) {
    }

    /** Alavancas do adaptador simulado. */
    public record Simulado(@DefaultValue("false") boolean falhasForcadas) {
    }
}
