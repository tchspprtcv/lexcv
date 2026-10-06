package com.lexcv.fiscal.efatura;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexcv.models.AmbienteFiscal;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Phase 136 (DFE-03): o modo do adaptador é escolhido pelo deployment e falha no arranque para
 * qualquer valor que não seja exatamente SIMULADO.
 */
class EfaturaConfigTest {

    private static final String[] TRANSMISSAO_VALIDA = {
            "app.efatura.transmissao.nif-transmissor=999999999",
            "app.efatura.transmissao.software-codigo=LEXCVSIM",
            "app.efatura.transmissao.software-nome=LexCV",
            "app.efatura.transmissao.software-versao=3.0.0",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EfaturaConfig.class, DfeValidador.class);

    private ApplicationContextRunner com(String... extra) {
        List<String> valores = new ArrayList<>(List.of(TRANSMISSAO_VALIDA));
        valores.addAll(List.of(extra));
        return runner.withPropertyValues(valores.toArray(String[]::new));
    }

    private static String causa(Throwable falha) {
        Throwable raiz = NestedExceptionUtils.getMostSpecificCause(falha);
        return raiz.getMessage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"REAL", "PRODUCAO", "simulado", "", "   ", " SIMULADOX"})
    void modoDesconhecidoImpedeOArranque(String modo) {
        com("app.efatura.modo=" + modo).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(causa(ctx.getStartupFailure()))
                    .contains("EFATURA_MODE")
                    .contains("só SIMULADO existe");
        });
    }

    @Test
    void modoAusenteImpedeOArranque() {
        com().run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(causa(ctx.getStartupFailure())).contains("EFATURA_MODE").contains("só SIMULADO existe");
        });
    }

    @Test
    void modoSimuladoCriaOGatewaySimuladoEATransmissao() {
        com("app.efatura.modo=SIMULADO").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(EfaturaGateway.class);
            assertThat(ctx.getBean(EfaturaGateway.class)).isInstanceOf(SimuladoEfaturaGateway.class);
            assertThat(ctx.getBean(EfaturaGateway.class).ambiente()).isEqualTo(AmbienteFiscal.SIMULADO);
            assertThat(ctx).hasSingleBean(TransmissaoEfatura.class);
            assertThat(ctx.getBean(TransmissaoEfatura.class))
                    .isEqualTo(new TransmissaoEfatura("999999999", "LEXCVSIM", "LexCV", "3.0.0"));
        });
    }

    @Test
    void modoComEspacosAVoltaEAceite() {
        com("app.efatura.modo= SIMULADO ").run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void codigoDeSoftwareInvalidoImpedeOArranqueNomeandoAPropriedade() {
        com("app.efatura.modo=SIMULADO", "app.efatura.transmissao.software-codigo=lexcv sim").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(causa(ctx.getStartupFailure()))
                    .contains("app.efatura.transmissao")
                    .contains("software-codigo")
                    .contains("Código do software");
        });
    }

    @Test
    void nifDoTransmissorInvalidoImpedeOArranqueNomeandoAPropriedade() {
        com("app.efatura.modo=SIMULADO", "app.efatura.transmissao.nif-transmissor=123").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(causa(ctx.getStartupFailure()))
                    .contains("app.efatura.transmissao")
                    .contains("nif-transmissor")
                    .contains("NIF do transmissor");
        });
    }

    @Test
    void transmissaoAusenteImpedeOArranque() {
        runner.withPropertyValues("app.efatura.modo=SIMULADO").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(causa(ctx.getStartupFailure())).contains("app.efatura.transmissao");
        });
    }

    private static PedidoComunicacao pedidoFre() throws IOException {
        return new PedidoComunicacao(UUID.randomUUID(), UUID.randomUUID(), AmbienteFiscal.SIMULADO,
                "CV1200520123456789000112345678901112345678904",
                DfeValidadorTest.exemplo("efatura/2024-05-27/2 InvoiceReceipt.xml"));
    }

    @Test
    void falhasForcadasTornamTodaAComunicacaoTransitoria() {
        com("app.efatura.modo=SIMULADO", "app.efatura.simulado.falhas-forcadas=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EfaturaGateway.class).comunicar(pedidoFre()))
                    .isInstanceOf(ResultadoComunicacao.ErroTransitorio.class);
        });
    }

    @Test
    void semFalhasForcadasOExemploOficialEAceite() {
        com("app.efatura.modo=SIMULADO").run(ctx -> {
            assertThat(ctx.getBean(EfaturaProperties.class).simulado().falhasForcadas()).isFalse();
            assertThat(ctx.getBean(EfaturaGateway.class).comunicar(pedidoFre()))
                    .isInstanceOf(ResultadoComunicacao.AceiteSimulado.class);
        });
    }

    @Test
    void valoresPorOmissaoDoOutbox() {
        com("app.efatura.modo=SIMULADO").run(ctx -> {
            EfaturaProperties.Outbox o = ctx.getBean(EfaturaProperties.class).outbox();
            assertThat(o.intervalo()).isEqualTo(Duration.ofSeconds(30));
            assertThat(o.atrasoInicial()).isEqualTo(Duration.ofSeconds(20));
            assertThat(o.lote()).isEqualTo(20);
            assertThat(o.lease()).isEqualTo(Duration.ofMinutes(2));
        });
    }

    // ---- application.yml ----

    private static PropertySource<?> applicationYml() throws IOException {
        List<PropertySource<?>> fontes = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        assertThat(fontes).hasSize(1);
        return fontes.get(0);
    }

    @Test
    void applicationYmlTemOModoPorOmissaoEOPoolDoScheduler() throws IOException {
        PropertySource<?> yml = applicationYml();
        assertThat(String.valueOf(yml.getProperty("app.efatura.modo"))).isEqualTo("${EFATURA_MODE:SIMULADO}");
        assertThat(String.valueOf(yml.getProperty("spring.task.scheduling.pool.size"))).isEqualTo("3");
        assertThat(String.valueOf(yml.getProperty("app.efatura.simulado.falhas-forcadas"))).isEqualTo("false");
    }

    @Test
    void valoresPorOmissaoDoApplicationYmlArrancamEmSimulado() throws IOException {
        PropertySource<?> yml = applicationYml();
        List<String> valores = new ArrayList<>();
        for (String chave : new String[]{
                "app.efatura.modo",
                "app.efatura.transmissao.nif-transmissor",
                "app.efatura.transmissao.software-codigo",
                "app.efatura.transmissao.software-nome",
                "app.efatura.transmissao.software-versao",
                "app.efatura.outbox.intervalo",
                "app.efatura.outbox.atraso-inicial",
                "app.efatura.outbox.lote",
                "app.efatura.outbox.lease",
                "app.efatura.simulado.falhas-forcadas"}) {
            Object v = yml.getProperty(chave);
            assertThat(v).as(chave).isNotNull();
            valores.add(chave + "=" + v);
        }
        runner.withPropertyValues(valores.toArray(String[]::new)).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EfaturaGateway.class).ambiente()).isEqualTo(AmbienteFiscal.SIMULADO);
            assertThat(ctx.getBean(TransmissaoEfatura.class).softwareCodigo()).isEqualTo("LEXCVSIM");
        });
    }
}
