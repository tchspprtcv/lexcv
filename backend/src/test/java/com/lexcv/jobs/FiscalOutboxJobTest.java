package com.lexcv.jobs;

import com.lexcv.fiscal.efatura.EfaturaProperties;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.services.fiscal.ComunicacaoFiscalTransacoes;
import com.lexcv.services.fiscal.ComunicacaoReclamada;
import com.lexcv.services.fiscal.ProcessadorComunicacaoFiscal;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 136-15 (DFE-04): o job do outbox reclama um lote com o tamanho e o lease configurados,
 * processa cada item pela ordem, isola a falha de um item (mesmo um {@link Error}) e nunca lança.
 */
class FiscalOutboxJobTest {

    private final ComunicacaoFiscalTransacoes transacoes = mock(ComunicacaoFiscalTransacoes.class);
    private final ProcessadorComunicacaoFiscal processador = mock(ProcessadorComunicacaoFiscal.class);
    private final EfaturaProperties propriedades = new EfaturaProperties("SIMULADO",
            new EfaturaProperties.Transmissao("999999999", "LEXCVSIM", "LexCV", "3.0.0"),
            new EfaturaProperties.Outbox(Duration.ofSeconds(30), Duration.ofSeconds(20), 20, Duration.ofMinutes(2)),
            new EfaturaProperties.Simulado(false));

    private FiscalOutboxJob job;

    @BeforeEach
    void setUp() {
        job = new FiscalOutboxJob(transacoes, processador, propriedades);
    }

    private static ComunicacaoReclamada item() {
        return new ComunicacaoReclamada(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AmbienteFiscal.SIMULADO, 1, 1L, 0);
    }

    @Test
    void reclamaComLoteELeaseConfiguradosEProcessaPorOrdem() {
        ComunicacaoReclamada a = item();
        ComunicacaoReclamada b = item();
        ComunicacaoReclamada c = item();
        when(transacoes.reclamar(20, Duration.ofMinutes(2))).thenReturn(List.of(a, b, c));

        assertThat(job.executarUmaVez()).isEqualTo(3);

        InOrder ordem = inOrder(processador);
        ordem.verify(processador).processar(a);
        ordem.verify(processador).processar(b);
        ordem.verify(processador).processar(c);
    }

    @Test
    void loteVazioNaoProcessaNada() {
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of());

        assertThat(job.executarUmaVez()).isZero();
        verifyNoInteractions(processador);
    }

    @Test
    void umErroNoPrimeiroItemNaoImpedeOsSeguintes() {
        ComunicacaoReclamada a = item();
        ComunicacaoReclamada b = item();
        ComunicacaoReclamada c = item();
        when(transacoes.reclamar(anyInt(), any())).thenReturn(List.of(a, b, c));
        doThrow(new StackOverflowError()).when(processador).processar(a);
        doThrow(new AssertionError("x")).when(processador).processar(b);

        assertThat(job.executarUmaVez()).isEqualTo(3);
        verify(processador).processar(c);
    }

    @Test
    void reclamarQueLancaNaoPropagaDoExecutar() {
        when(transacoes.reclamar(anyInt(), any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job.executar()).doesNotThrowAnyException();
        verifyNoInteractions(processador);
    }

    @Test
    void errorAoReclamarTambemNaoPropaga() {
        when(transacoes.reclamar(anyInt(), any())).thenThrow(new OutOfMemoryError("simulado"));

        assertThatCode(() -> job.executar()).doesNotThrowAnyException();
    }

    @Test
    void agendadoComFixedDelayEAtrasoInicialESemTransacao() throws Exception {
        assertThat(FiscalOutboxJob.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Method m : FiscalOutboxJob.class.getDeclaredMethods()) {
            assertThat(m.isAnnotationPresent(Transactional.class)).as(m.getName()).isFalse();
        }
        Scheduled s = FiscalOutboxJob.class.getMethod("executar").getAnnotation(Scheduled.class);
        assertThat(s).isNotNull();
        assertThat(s.fixedDelayString()).isEqualTo("${app.efatura.outbox.intervalo:PT30S}");
        assertThat(s.initialDelayString()).isEqualTo("${app.efatura.outbox.atraso-inicial:PT20S}");
        assertThat(s.cron()).isEmpty();
        assertThat(s.fixedRateString()).isEmpty();
    }
}
