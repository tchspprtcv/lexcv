package com.lexcv.fiscal.efatura;

import com.lexcv.models.AmbienteFiscal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase 136 (DFE-03, DFE-06): a única implementação de {@link EfaturaGateway} neste build.
 *
 * <p>Aceita um documento só quando o XML é válido contra o XSD oficial; recusa qualquer ambiente
 * que não seja SIMULADO; aplica primeiro o {@link InjetorFalhas}. Não contacta a rede nem o
 * sistema de ficheiros. Não é um componente Spring: o bean é criado por {@code EfaturaConfig}
 * (136-12) depois da verificação de {@code EFATURA_MODE}.
 */
public final class SimuladoEfaturaGateway implements EfaturaGateway {

    static final String AMBIENTE_INCOMPATIVEL = "AMBIENTE_INCOMPATIVEL";
    static final String MENSAGEM_AMBIENTE = "O documento não pertence ao ambiente de simulação.";
    static final String PREFIXO_REFERENCIA = "SIMULADO-";

    private final DfeValidador validador;
    private final InjetorFalhas injetor;

    public SimuladoEfaturaGateway(DfeValidador validador, InjetorFalhas injetor) {
        this.validador = Objects.requireNonNull(validador, "validador");
        this.injetor = Objects.requireNonNull(injetor, "injetor");
    }

    @Override
    public AmbienteFiscal ambiente() {
        return AmbienteFiscal.SIMULADO;
    }

    @Override
    public ResultadoComunicacao comunicar(PedidoComunicacao pedido) {
        if (pedido.ambiente() != AmbienteFiscal.SIMULADO) {
            return new ResultadoComunicacao.Rejeitado(AMBIENTE_INCOMPATIVEL, MENSAGEM_AMBIENTE);
        }
        Optional<ResultadoComunicacao> injetado = injetor.injetar(pedido);
        if (injetado.isPresent()) {
            return injetado.get();
        }
        ResultadoValidacao r = validador.validar(pedido.xml().getBytes(StandardCharsets.UTF_8));
        if (r.valido()) {
            return new ResultadoComunicacao.AceiteSimulado(PREFIXO_REFERENCIA + pedido.iud());
        }
        return new ResultadoComunicacao.Rejeitado(r.codigo(), mensagemFormato(r.linha()));
    }

    /** Mensagem fixa; só a linha do erro (nunca o texto do parser nem valores do documento). */
    static String mensagemFormato(int linha) {
        return linha > 0
                ? "O documento não cumpre o formato eFatura (linha " + linha + ")."
                : "O documento não cumpre o formato eFatura.";
    }
}
