package com.lexcv.dtos;

import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;

import java.time.Instant;

/**
 * Phase 136 (DFE-04, DFE-06): resumo da comunicação de um documento fiscal no detalhe -- estado,
 * ambiente, IUD (do satélite XML do mesmo tenant; nulo enquanto não foi gerado), tentativas e
 * datas. Calculado só no backend; a UI mostra estes valores tal como chegam (136-UI-SPEC).
 *
 * <p>Visibilidade: {@code ultimoErro} (mensagem curta e já sanitizada, nunca texto de exceção) só
 * em {@code REJEITADO}/{@code ERRO}; {@code proximaTentativaEm} só em {@code PENDENTE}.
 */
public record ComunicacaoFiscalResumo(
        String estado,
        String ambiente,
        String iud,
        Integer tentativas,
        Instant ultimaTentativaEm,
        String ultimoErro,
        Instant proximaTentativaEm
) {

    /** {@code null} quando o documento ainda não tem linha de comunicação. */
    public static ComunicacaoFiscalResumo de(ComunicacaoFiscal c, String iudOuNulo) {
        if (c == null) {
            return null;
        }
        EstadoComunicacaoFiscal estado = c.getEstado();
        return new ComunicacaoFiscalResumo(
                estado == null ? null : estado.name(),
                c.getAmbiente() == null ? null : c.getAmbiente().name(),
                iudOuNulo,
                c.getTentativas(),
                c.getUltimaTentativaEm(),
                estado != null && estado.reprocessavel() ? c.getUltimoErro() : null,
                estado == EstadoComunicacaoFiscal.PENDENTE ? c.getProximaTentativaEm() : null);
    }
}
