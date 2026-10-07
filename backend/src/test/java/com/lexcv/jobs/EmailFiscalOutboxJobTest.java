package com.lexcv.jobs;

import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.fiscal.email.EntregaEmailGateway;
import com.lexcv.services.fiscal.EntregaEmailReclamada;
import com.lexcv.services.fiscal.EntregaEmailTransacoes;
import com.lexcv.services.fiscal.ProcessadorEntregaEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 137-19 (ENTR-03, ENTR-05, ENTR-06): o job do outbox de email não faz nada sem SMTP (nem
 * reclama, nem fecha esgotadas -- nenhuma tentativa é gasta), fecha e notifica as esgotadas antes de
 * reclamar, processa cada item reclamado pela ordem, isola a falha de um item e só deixa passar um
 * {@link Error}.
 */
class EmailFiscalOutboxJobTest {

    private final EntregaEmailTransacoes transacoes = mock(EntregaEmailTransacoes.class);
    private final ProcessadorEntregaEmail processador = mock(ProcessadorEntregaEmail.class);
    private final EntregaEmailGateway gateway = mock(EntregaEmailGateway.class);
    private final EmailProperties propriedades = new EmailProperties(
            new EmailProperties.Smtp(null, null, null, null, null, null, Duration.ofSeconds(10),
                    Duration.ofSeconds(20)),
            new EmailProperties.Outbox(Duration.ofSeconds(30), Duration.ofSeconds(40), 7, Duration.ofMinutes(3)));

    private EmailFiscalOutboxJob job;

    @BeforeEach
    void setUp() {
        job = new EmailFiscalOutboxJob(transacoes, processador, gateway, propriedades);
        when(gateway.configurado()).thenReturn(true);
    }

    private static EntregaEmailReclamada item() {
        return new EntregaEmailReclamada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "cliente@exemplo.cv", 1, 1L, 0);
    }

    @Test
    void semSmtpNaoReclamaNemEncerraNada() {
        when(gateway.configurado()).thenReturn(false);

        assertThat(job.executarUmaVez()).isZero();

        verifyNoInteractions(transacoes, processador);
    }

    @Test
    void reclamaComLoteELeaseConfiguradosEProcessaPorOrdem() {
        EntregaEmailReclamada a = item();
        EntregaEmailReclamada b = item();
        EntregaEmailReclamada c = item();
        when(transacoes.reclamar(7, Duration.ofMinutes(3))).thenReturn(List.of(a, b, c));

        assertThat(job.executarUmaVez()).isEqualTo(3);

        InOrder ordem = inOrder(processador);
        ordem.verify(processador).processar(a);
        ordem.verify(processador).processar(b);
        ordem.verify(processador).processar(c);
    }

    @Test
    void encerraEsgotadasENotificaCadaUmaAntesDeReclamar() {
        EntregaEmailReclamada e1 = item();
        EntregaEmailReclamada e2 = item();
        EntregaEmailReclamada a = item();
        when(transacoes.encerrarEsgotadas()).thenReturn(List.of(e1, e2));
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of(a));

        assertThat(job.executarUmaVez()).isEqualTo(1);

        InOrder ordem = inOrder(transacoes, processador);
        ordem.verify(transacoes).encerrarEsgotadas();
        ordem.verify(processador).notificarEsgotada(e1);
        ordem.verify(processador).notificarEsgotada(e2);
        ordem.verify(transacoes).reclamar(7, Duration.ofMinutes(3));
        ordem.verify(processador).processar(a);
    }

    @Test
    void loteVazioNaoProcessaNada() {
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of());

        assertThat(job.executarUmaVez()).isZero();
        verifyNoInteractions(processador);
    }

    @Test
    void umaFalhaNumItemNaoImpedeOsSeguintes() {
        EntregaEmailReclamada a = item();
        EntregaEmailReclamada b = item();
        EntregaEmailReclamada c = item();
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of(a, b, c));
        doThrow(new IllegalStateException("x")).when(processador).processar(a);
        doThrow(new RuntimeException("y")).when(processador).processar(b);

        assertThat(job.executarUmaVez()).isEqualTo(3);
        verify(processador).processar(c);
    }

    @Test
    void falhaANotificarUmaEsgotadaNaoImpedeAsOutrasNemAReclamacao() {
        EntregaEmailReclamada e1 = item();
        EntregaEmailReclamada e2 = item();
        EntregaEmailReclamada a = item();
        when(transacoes.encerrarEsgotadas()).thenReturn(List.of(e1, e2));
        doThrow(new RuntimeException("x")).when(processador).notificarEsgotada(e1);
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of(a));

        assertThat(job.executarUmaVez()).isEqualTo(1);
        verify(processador).notificarEsgotada(e2);
        verify(processador).processar(a);
    }

    @Test
    void falhaAEncerrarEsgotadasNaoImpedeAReclamacao() {
        EntregaEmailReclamada a = item();
        when(transacoes.encerrarEsgotadas()).thenThrow(new RuntimeException("db down"));
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of(a));

        assertThat(job.executarUmaVez()).isEqualTo(1);
        verify(processador).processar(a);
    }

    @Test
    void executarEngoleExcecoes() {
        when(transacoes.reclamar(anyInt(), any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.executar()).doesNotThrowAnyException();
        verifyNoInteractions(processador);
    }

    @Test
    void executarEngoleUmaFalhaDoGateway() {
        when(gateway.configurado()).thenThrow(new IllegalStateException("x"));

        assertThatCode(() -> job.executar()).doesNotThrowAnyException();
        verifyNoInteractions(transacoes, processador);
    }

    @Test
    void errorNumItemNaoEEngolido() {
        EntregaEmailReclamada a = item();
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of(a));
        doThrow(new StackOverflowError()).when(processador).processar(a);

        assertThrows(StackOverflowError.class, () -> job.executar());
    }

    @Test
    void errorAoReclamarChegaAoScheduler() {
        when(transacoes.reclamar(anyInt(), any())).thenThrow(new OutOfMemoryError("simulado"));

        assertThrows(OutOfMemoryError.class, () -> job.executar());
    }

    @Test
    void agendamentoProprioESemTransacao() throws Exception {
        Method executar = EmailFiscalOutboxJob.class.getMethod("executar");
        Scheduled s = executar.getAnnotation(Scheduled.class);
        assertThat(s).isNotNull();
        assertThat(s.fixedDelayString()).isEqualTo("${app.email.outbox.intervalo:PT30S}");
        assertThat(s.initialDelayString()).isEqualTo("${app.email.outbox.atraso-inicial:PT40S}");
        assertThat(EmailFiscalOutboxJob.class.getAnnotation(Transactional.class)).isNull();
        for (Method m : EmailFiscalOutboxJob.class.getDeclaredMethods()) {
            assertThat(m.getAnnotation(Transactional.class)).as(m.getName()).isNull();
        }
    }
}
