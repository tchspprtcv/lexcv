package com.lexcv.services.fiscal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Phase 137 (ENTR-03, ENTR-04, ENTR-06; T-137-15, T-137-18): regras partilhadas da entrega por email. */
class RegrasEntregaEmailTest {

    // ---- estadoApresentado ----

    @ParameterizedTest
    @EnumSource(value = EstadoEntregaEmail.class, names = {"PENDENTE", "DESLIGADO", "SEM_EMAIL"})
    void semSmtpOsEstadosSemEnvioSaoNaoConfigurado(EstadoEntregaEmail estado) {
        assertThat(RegrasEntregaEmail.estadoApresentado(estado, false)).isEqualTo("NAO_CONFIGURADO");
        assertThat(RegrasEntregaEmail.NAO_CONFIGURADO).isEqualTo("NAO_CONFIGURADO");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEntregaEmail.class, names = {"ENVIADO", "FALHOU"})
    void semSmtpOHistoricoMantemSe(EstadoEntregaEmail estado) {
        assertThat(RegrasEntregaEmail.estadoApresentado(estado, false)).isEqualTo(estado.name());
    }

    @ParameterizedTest
    @EnumSource(EstadoEntregaEmail.class)
    void comSmtpOEstadoGuardadoEApresentado(EstadoEntregaEmail estado) {
        assertThat(RegrasEntregaEmail.estadoApresentado(estado, true)).isEqualTo(estado.name());
    }

    @Test
    void estadoNuloENulo() {
        assertThat(RegrasEntregaEmail.estadoApresentado(null, true)).isNull();
        assertThat(RegrasEntregaEmail.estadoApresentado(null, false)).isNull();
    }

    // ---- reenviavel ----

    @ParameterizedTest
    @EnumSource(value = EstadoEntregaEmail.class, names = {"FALHOU", "ENVIADO", "SEM_EMAIL"})
    void reenviavelQuandoTodasAsCondicoesSeVerificam(EstadoEntregaEmail estado) {
        assertThat(RegrasEntregaEmail.reenviavel(estado, true, true, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true))
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEntregaEmail.class, names = {"FALHOU", "ENVIADO", "SEM_EMAIL"})
    void naoReenviavelQuandoFalhaQualquerCondicao(EstadoEntregaEmail estado) {
        EstadoComunicacaoFiscal aceite = EstadoComunicacaoFiscal.ACEITE_SIMULADO;
        assertThat(RegrasEntregaEmail.reenviavel(estado, false, true, aceite, true)).isFalse();
        assertThat(RegrasEntregaEmail.reenviavel(estado, true, false, aceite, true)).isFalse();
        assertThat(RegrasEntregaEmail.reenviavel(estado, true, true, aceite, false)).isFalse();
        assertThat(RegrasEntregaEmail.reenviavel(estado, true, true, null, true)).isFalse();
        for (EstadoComunicacaoFiscal c : EstadoComunicacaoFiscal.values()) {
            if (c != aceite) {
                assertThat(RegrasEntregaEmail.reenviavel(estado, true, true, c, true)).as(c.name()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEntregaEmail.class, names = {"DESLIGADO", "PENDENTE"})
    void desligadoEPendenteNuncaSaoReenviaveis(EstadoEntregaEmail estado) {
        assertThat(RegrasEntregaEmail.reenviavel(estado, true, true, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true))
                .isFalse();
    }

    @Test
    void estadoNuloNaoEReenviavel() {
        assertThat(RegrasEntregaEmail.reenviavel(null, true, true, EstadoComunicacaoFiscal.ACEITE_SIMULADO, true))
                .isFalse();
    }

    // ---- emailValido ----

    @Test
    void emailValidoDevolveOValorAparado() {
        assertThat(RegrasEntregaEmail.emailValido("  ana@exemplo.cv ")).isEqualTo(Optional.of("ana@exemplo.cv"));
        assertThat(RegrasEntregaEmail.emailValido("ana.silva+fiscal@escritorio.exemplo.cv"))
                .isEqualTo(Optional.of("ana.silva+fiscal@escritorio.exemplo.cv"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "a@b@c.cv", "sem-arroba", "x@y.cv\r\nBcc: z@w.cv", "x@y.cv\nBcc: z@w.cv", "x@y.cv\rz",
            "ana\t@exemplo.cv", "ana@exem\u0007plo.cv", "ana@exemplo.cv\u0000", "ana\u0085@exemplo.cv",
            "ana,b@exemplo.cv", "ana@exemplo.cv;b@c.cv", "<ana@exemplo.cv>", "Ana <ana@exemplo.cv>",
            "ana silva@exemplo.cv", "@exemplo.cv", "ana@", "ana@exemplo", "ana@.", "ana@exemplo."})
    void emailInvalidoERecusado(String bruto) {
        assertThat(RegrasEntregaEmail.emailValido(bruto)).as("[" + bruto + "]").isEmpty();
    }

    @Test
    void emailNuloERecusado() {
        assertThat(RegrasEntregaEmail.emailValido(null)).isEmpty();
    }

    @Test
    void emailComMaisDe254CaracteresERecusado() {
        String dominio = "@exemplo.cv";
        String no254 = "a".repeat(254 - dominio.length()) + dominio;
        assertThat(no254).hasSize(254);
        assertThat(RegrasEntregaEmail.emailValido(no254)).isEqualTo(Optional.of(no254));
        assertThat(RegrasEntregaEmail.emailValido("a" + no254)).isEmpty();
    }

    // ---- ultimoErroVisivel ----

    @ParameterizedTest
    @EnumSource(value = EstadoEntregaEmail.class, names = {"FALHOU", "PENDENTE"})
    void ultimoErroVisivelEmFalhouEPendente(EstadoEntregaEmail estado) {
        assertThat(RegrasEntregaEmail.ultimoErroVisivel(estado, "msg")).isEqualTo("msg");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEntregaEmail.class, names = {"ENVIADO", "DESLIGADO", "SEM_EMAIL"})
    void ultimoErroEscondidoNosOutrosEstados(EstadoEntregaEmail estado) {
        assertThat(RegrasEntregaEmail.ultimoErroVisivel(estado, "msg")).isNull();
    }

    @Test
    void ultimoErroComEstadoNuloENulo() {
        assertThat(RegrasEntregaEmail.ultimoErroVisivel(null, "msg")).isNull();
    }
}
