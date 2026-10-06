package com.lexcv.fiscal.efatura;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 136 (DFE-03): o modo do adaptador eFatura é escolhido pelo deployment
 * ({@code EFATURA_MODE}, por omissão {@code SIMULADO}) e verificado no arranque.
 *
 * <p>Neste build NÃO existe implementação real (XAdES, OAuth2, envio à DNRE): o único valor
 * aceite é exatamente {@code SIMULADO} (sensível a maiúsculas, aparado). {@code REAL},
 * {@code PRODUCAO}, minúsculas ou vazio abortam o arranque com uma mensagem explícita, em vez de
 * cair silenciosamente num modo por omissão.
 *
 * <p>Camadas de "simulado nunca autorizado": este arranque só cria
 * {@link SimuladoEfaturaGateway}; o resultado selado só tem aceitação simulada; o mapeamento
 * resultado -> estado só produz {@code ACEITE_SIMULADO}; e a base de dados recusa uma linha
 * autorizada fora do ambiente de produção ({@code CHECK} da 136-02).
 */
@Configuration
@EnableConfigurationProperties(EfaturaProperties.class)
public class EfaturaConfig {

    private static final Logger log = LoggerFactory.getLogger(EfaturaConfig.class);

    static final String MODO_SIMULADO = "SIMULADO";
    private static final String PREFIXO_TRANSMISSAO = "app.efatura.transmissao.";

    private static final Map<String, String> PROPRIEDADE_POR_MENSAGEM = Map.of(
            TransmissaoEfatura.MSG_NIF, "nif-transmissor (EFATURA_TRANSMISSOR_NIF)",
            TransmissaoEfatura.MSG_CODIGO, "software-codigo (EFATURA_SOFTWARE_CODIGO)",
            TransmissaoEfatura.MSG_NOME, "software-nome (EFATURA_SOFTWARE_NOME)",
            TransmissaoEfatura.MSG_VERSAO, "software-versao (EFATURA_SOFTWARE_VERSAO)");

    @Bean
    public EfaturaGateway efaturaGateway(EfaturaProperties propriedades, DfeValidador validador) {
        String modo = propriedades.modo() == null ? "" : propriedades.modo().trim();
        if (!MODO_SIMULADO.equals(modo)) {
            throw new IllegalStateException("EFATURA_MODE='" + modo + "' não é suportado neste build: "
                    + "só SIMULADO existe (não há implementação real). Corrija EFATURA_MODE e reinicie.");
        }
        boolean falhasForcadas = propriedades.simulado().falhasForcadas();
        if (falhasForcadas) {
            log.warn("app.efatura.simulado.falhas-forcadas está ligado: toda a comunicação simulada falha de forma "
                    + "transitória (alavanca de teste).");
        }
        return new SimuladoEfaturaGateway(validador,
                falhasForcadas ? InjetorFalhas.SEMPRE_TRANSITORIA : InjetorFalhas.NENHUMA);
    }

    @Bean
    public TransmissaoEfatura transmissaoEfatura(EfaturaProperties propriedades) {
        EfaturaProperties.Transmissao t = propriedades.transmissao();
        try {
            return new TransmissaoEfatura(t.nifTransmissor(), t.softwareCodigo(), t.softwareNome(),
                    t.softwareVersao());
        } catch (IllegalArgumentException e) {
            // A mensagem do record é texto fixo; copiada para que a causa raiz do arranque falhado
            // nomeie a propriedade (sem encadear: a causa não acrescenta informação).
            String propriedade = PROPRIEDADE_POR_MENSAGEM.getOrDefault(e.getMessage(), "*");
            throw new IllegalStateException("Configuração inválida em " + PREFIXO_TRANSMISSAO + propriedade + ": "
                    + e.getMessage() + " Corrija e reinicie.");
        }
    }
}
