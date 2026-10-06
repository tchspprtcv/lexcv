package com.lexcv.fiscal.efatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexcv.models.AmbienteFiscal;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 136 (DFE-03, DFE-06): o adaptador simulado só aceita XML válido contra o XSD, recusa
 * outro ambiente e aceita falhas injetadas para os testes.
 */
class SimuladoEfaturaGatewayTest {

    private static final String FRE = "efatura/2024-05-27/2 InvoiceReceipt.xml";
    private static final String IUD = "CV1200520123456789000112345678901112345678904";

    private static DfeValidador validador;
    private static String xmlValido;

    @BeforeAll
    static void preparar() throws IOException {
        validador = new DfeValidador();
        xmlValido = DfeValidadorTest.exemplo(FRE);
    }

    private static PedidoComunicacao pedido(String xml) {
        return new PedidoComunicacao(UUID.randomUUID(), UUID.randomUUID(), AmbienteFiscal.SIMULADO, IUD, xml);
    }

    @Test
    void xmlValidoNoAmbienteSimuladoEAceite() {
        SimuladoEfaturaGateway gateway = new SimuladoEfaturaGateway(validador, InjetorFalhas.NENHUMA);

        ResultadoComunicacao r = gateway.comunicar(pedido(xmlValido));

        assertThat(r).isInstanceOfSatisfying(ResultadoComunicacao.AceiteSimulado.class, a -> {
            assertThat(a.referencia()).isNotBlank().startsWith("SIMULADO-").contains(IUD);
        });
    }

    @Test
    void xmlInvalidoERejeitadoComMensagemFixaComALinha() {
        SimuladoEfaturaGateway gateway = new SimuladoEfaturaGateway(validador, InjetorFalhas.NENHUMA);
        String invalido = xmlValido.replace("<RepositoryCode>1</RepositoryCode>", "<RepositoryCode>4</RepositoryCode>");
        assertThat(invalido).isNotEqualTo(xmlValido);

        ResultadoComunicacao r = gateway.comunicar(pedido(invalido));

        assertThat(r).isInstanceOfSatisfying(ResultadoComunicacao.Rejeitado.class, x -> {
            assertThat(x.codigo()).isEqualTo("XSD_INVALIDO");
            assertThat(x.mensagem()).matches("O documento não cumpre o formato eFatura \\(linha \\d+\\)\\.");
            assertThat(x.mensagem()).doesNotContain("RepositoryCode").doesNotContain("4<");
        });
    }

    @Test
    void xmlComDoctypeERejeitadoSemTextoDoParser() {
        SimuladoEfaturaGateway gateway = new SimuladoEfaturaGateway(validador, InjetorFalhas.NENHUMA);
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hostname\">]><x>&e;</x>";

        ResultadoComunicacao r = gateway.comunicar(pedido(xxe));

        assertThat(r).isInstanceOfSatisfying(ResultadoComunicacao.Rejeitado.class, x -> {
            assertThat(x.codigo()).isEqualTo("XML_PROIBIDO");
            assertThat(x.mensagem()).doesNotContain("DOCTYPE").doesNotContain("hostname");
        });
    }

    @Test
    void falhaTransitoriaInjetadaVenceMesmoComXmlValido() {
        SimuladoEfaturaGateway gateway = new SimuladoEfaturaGateway(validador, InjetorFalhas.SEMPRE_TRANSITORIA);

        ResultadoComunicacao r = gateway.comunicar(pedido(xmlValido));

        assertThat(r).isEqualTo(new ResultadoComunicacao.ErroTransitorio("FALHA_SIMULADA",
                "Falha simulada do serviço de comunicação."));
    }

    @Test
    void injetorPersonalizadoEHonrado() {
        ResultadoComunicacao.Rejeitado forcado = new ResultadoComunicacao.Rejeitado("TESTE", "Rejeição de teste.");
        SimuladoEfaturaGateway gateway = new SimuladoEfaturaGateway(validador, p -> Optional.of(forcado));

        assertThat(gateway.comunicar(pedido(xmlValido))).isSameAs(forcado);
    }

    @Test
    void injetorNenhumaNaoInjetaNada() {
        assertThat(InjetorFalhas.NENHUMA.injetar(pedido(xmlValido))).isEmpty();
        assertThat(InjetorFalhas.SEMPRE_TRANSITORIA.injetar(pedido(xmlValido))).isPresent();
    }

    @Test
    void ambienteESimulado() {
        assertThat(new SimuladoEfaturaGateway(validador, InjetorFalhas.NENHUMA).ambiente())
                .isEqualTo(AmbienteFiscal.SIMULADO);
    }

    @Test
    void pedidoRecusaCamposNulos() {
        UUID t = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        assertThatThrownBy(() -> new PedidoComunicacao(null, d, AmbienteFiscal.SIMULADO, IUD, xmlValido))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PedidoComunicacao(t, null, AmbienteFiscal.SIMULADO, IUD, xmlValido))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PedidoComunicacao(t, d, null, IUD, xmlValido))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PedidoComunicacao(t, d, AmbienteFiscal.SIMULADO, null, xmlValido))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PedidoComunicacao(t, d, AmbienteFiscal.SIMULADO, IUD, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PedidoComunicacao(t, d, AmbienteFiscal.SIMULADO, " ", xmlValido))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PedidoComunicacao(t, d, AmbienteFiscal.SIMULADO, IUD, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gatewayExigeDependencias() {
        assertThatThrownBy(() -> new SimuladoEfaturaGateway(null, InjetorFalhas.NENHUMA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SimuladoEfaturaGateway(validador, null))
                .isInstanceOf(NullPointerException.class);
    }
}
