package com.lexcv.services.fiscal;

import com.lexcv.fiscal.efatura.ResultadoComunicacao;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;

/**
 * Phase 136 (DFE-04, DFE-06): a ÚNICA função que transforma o resultado de uma comunicação no
 * estado guardado em {@code t_comunicacao_fiscal}.
 *
 * <ul>
 *   <li>Aceitação simulada: {@code ACEITE_SIMULADO}, decidido por um {@code switch} exaustivo
 *       sobre {@link AmbienteFiscal} SEM ramo por omissão. Acrescentar um ambiente real ao enum
 *       é um erro de compilação exatamente aqui, o que obriga o autor futuro a decidir o estado de
 *       propósito; neste build não existe caminho de código para uma autorização.</li>
 *   <li>Rejeição: {@code REJEITADO}, terminal de imediato.</li>
 *   <li>Erro transitório: {@code PENDENTE} enquanto {@code tentativas < MAX_TENTATIVAS}, e
 *       {@code ERRO} a partir da 8.ª tentativa falhada.</li>
 * </ul>
 *
 * <p>Função pura, sem Spring nem base de dados.
 */
public final class EstadoComunicacaoMapper {

    /** Tentativas automáticas; a 8.ª falha transitória passa a ERRO. */
    public static final int MAX_TENTATIVAS = 8;

    private EstadoComunicacaoMapper() {
    }

    /**
     * @param resultado  o que a porta devolveu
     * @param ambiente   o ambiente da linha de comunicação
     * @param tentativas tentativas já contadas (incluindo a atual, contada ao reclamar)
     */
    public static EstadoComunicacaoFiscal estadoPara(ResultadoComunicacao resultado, AmbienteFiscal ambiente,
                                                     int tentativas) {
        return switch (resultado) {
            case ResultadoComunicacao.AceiteSimulado aceite -> switch (ambiente) {
                case SIMULADO -> EstadoComunicacaoFiscal.ACEITE_SIMULADO;
            };
            case ResultadoComunicacao.Rejeitado rejeitado -> EstadoComunicacaoFiscal.REJEITADO;
            case ResultadoComunicacao.ErroTransitorio erro -> tentativas >= MAX_TENTATIVAS
                    ? EstadoComunicacaoFiscal.ERRO
                    : EstadoComunicacaoFiscal.PENDENTE;
        };
    }
}
