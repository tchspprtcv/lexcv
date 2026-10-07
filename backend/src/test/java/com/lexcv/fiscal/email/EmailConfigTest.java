package com.lexcv.fiscal.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Phase 137 (ENTR-06, ENTR-03): o SMTP é opcional. Ausente ou vazio, a aplicação arranca e o
 * gateway diz "não configurado"; configurado, liga o adaptador SMTP; a meio (host sem remetente
 * válido) o arranque falha com uma mensagem que nomeia a variável e nunca mostra valores. Em
 * nenhum caso a auto-configuração de mail do Spring Boot cria um {@link JavaMailSender}.
 */
class EmailConfigTest {

    /** Sentinela: nunca pode aparecer numa mensagem de falha. */
    private static final String PASSWORD_SENTINELA = "s3nt1nela-Nao-Mostrar";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withConfiguration(UserConfigurations.of(EmailConfig.class));

    private static String causa(Throwable falha) {
        return NestedExceptionUtils.getMostSpecificCause(falha).getMessage();
    }

    private static void semJavaMailSender(AssertableApplicationContext ctx) {
        assertThat(ctx.getBeanNamesForType(JavaMailSender.class)).isEmpty();
    }

    @Test
    void semPropriedadesArrancaNaoConfigurado() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            EntregaEmailGateway gateway = ctx.getBean(EntregaEmailGateway.class);
            assertThat(gateway).isInstanceOf(NaoConfiguradoEntregaEmailGateway.class);
            assertThat(gateway.configurado()).isFalse();
            assertThat(ctx.getBean(EmailProperties.class).configurado()).isFalse();
            semJavaMailSender(ctx);
        });
    }

    @Test
    void seisValoresVaziosArrancaNaoConfigurado() {
        // O caso do compose/CI: as variáveis existem mas vêm vazias.
        runner.withPropertyValues(
                "app.email.smtp.host=",
                "app.email.smtp.port=",
                "app.email.smtp.username=",
                "app.email.smtp.password=",
                "app.email.smtp.from=",
                "app.email.smtp.starttls=").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            EntregaEmailGateway gateway = ctx.getBean(EntregaEmailGateway.class);
            assertThat(gateway).isInstanceOf(NaoConfiguradoEntregaEmailGateway.class);
            assertThat(gateway.configurado()).isFalse();
            semJavaMailSender(ctx);
        });
    }

    @Test
    void hostEFromLigamOAdaptadorSmtp() {
        runner.withPropertyValues(
                "app.email.smtp.host=smtp.example.cv",
                "app.email.smtp.port=2525",
                "app.email.smtp.username=lexcv",
                "app.email.smtp.password=" + PASSWORD_SENTINELA,
                "app.email.smtp.from=faturacao@example.cv").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            EntregaEmailGateway gateway = ctx.getBean(EntregaEmailGateway.class);
            assertThat(gateway).isInstanceOf(SmtpEntregaEmailGateway.class);
            assertThat(gateway.configurado()).isTrue();
            semJavaMailSender(ctx);
        });
    }

    @Test
    void portaEStarttlsVaziosComHostUsamOsValoresPorOmissao() {
        runner.withPropertyValues(
                "app.email.smtp.host=smtp.example.cv",
                "app.email.smtp.port=",
                "app.email.smtp.starttls=",
                "app.email.smtp.from=faturacao@example.cv").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            EmailProperties.Smtp smtp = ctx.getBean(EmailProperties.class).smtp();
            assertThat(smtp.port()).isEqualTo(587);
            assertThat(smtp.starttls()).isTrue();
            assertThat(ctx.getBean(EntregaEmailGateway.class)).isInstanceOf(SmtpEntregaEmailGateway.class);
            semJavaMailSender(ctx);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-address", "a@b@c", "Nome <x@example.cv>, y@example.cv"})
    void hostSemFromValidoImpedeOArranque(String from) {
        runner.withPropertyValues(
                "app.email.smtp.host=smtp.example.cv",
                "app.email.smtp.username=lexcv",
                "app.email.smtp.password=" + PASSWORD_SENTINELA,
                "app.email.smtp.from=" + from).run(ctx -> {
            assertThat(ctx).hasFailed();
            String mensagem = causa(ctx.getStartupFailure());
            assertThat(mensagem).contains("SMTP_FROM").doesNotContain(PASSWORD_SENTINELA);
            if (!from.isBlank()) {
                assertThat(mensagem).doesNotContain(from);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "65536"})
    void portaForaDoIntervaloImpedeOArranque(String porta) {
        runner.withPropertyValues(
                "app.email.smtp.host=smtp.example.cv",
                "app.email.smtp.port=" + porta,
                "app.email.smtp.password=" + PASSWORD_SENTINELA,
                "app.email.smtp.from=faturacao@example.cv").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(causa(ctx.getStartupFailure())).contains("SMTP_PORT").doesNotContain(PASSWORD_SENTINELA);
        });
    }

    @Test
    void outboxPorOmissao() {
        runner.run(ctx -> {
            EmailProperties.Outbox o = ctx.getBean(EmailProperties.class).outbox();
            assertThat(o.intervalo()).isEqualTo(Duration.ofSeconds(30));
            assertThat(o.atrasoInicial()).isEqualTo(Duration.ofSeconds(40));
            assertThat(o.lote()).isEqualTo(10);
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
    void applicationYmlLigaAsVariaveisSmtpComVaziosPorOmissao() throws IOException {
        PropertySource<?> yml = applicationYml();
        assertThat(String.valueOf(yml.getProperty("app.email.smtp.host"))).isEqualTo("${SMTP_HOST:}");
        assertThat(String.valueOf(yml.getProperty("app.email.smtp.port"))).isEqualTo("${SMTP_PORT:587}");
        assertThat(String.valueOf(yml.getProperty("app.email.smtp.username"))).isEqualTo("${SMTP_USERNAME:}");
        assertThat(String.valueOf(yml.getProperty("app.email.smtp.password"))).isEqualTo("${SMTP_PASSWORD:}");
        assertThat(String.valueOf(yml.getProperty("app.email.smtp.from"))).isEqualTo("${SMTP_FROM:}");
        assertThat(String.valueOf(yml.getProperty("app.email.smtp.starttls"))).isEqualTo("${SMTP_STARTTLS:true}");
        assertThat(String.valueOf(yml.getProperty("spring.task.scheduling.pool.size"))).isEqualTo("4");
        for (String nome : yml instanceof org.springframework.core.env.EnumerablePropertySource<?> e
                ? e.getPropertyNames() : new String[0]) {
            assertThat(nome).as("nunca spring.mail.*").doesNotStartWith("spring.mail.");
        }
    }

    @Test
    void valoresDoApplicationYmlSemVariaveisArrancamNaoConfigurado() throws IOException {
        PropertySource<?> yml = applicationYml();
        List<String> valores = new ArrayList<>();
        for (String chave : new String[]{
                "app.email.smtp.host", "app.email.smtp.port", "app.email.smtp.username",
                "app.email.smtp.password", "app.email.smtp.from", "app.email.smtp.starttls",
                "app.email.outbox.intervalo", "app.email.outbox.atraso-inicial",
                "app.email.outbox.lote", "app.email.outbox.lease"}) {
            Object v = yml.getProperty(chave);
            assertThat(v).as(chave).isNotNull();
            valores.add(chave + "=" + v);
        }
        runner.withPropertyValues(valores.toArray(String[]::new)).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(EntregaEmailGateway.class)).isInstanceOf(NaoConfiguradoEntregaEmailGateway.class);
            EmailProperties p = ctx.getBean(EmailProperties.class);
            assertThat(p.smtp().port()).isEqualTo(587);
            assertThat(p.smtp().starttls()).isTrue();
            assertThat(p.outbox().lote()).isEqualTo(10);
            semJavaMailSender(ctx);
        });
    }
}
