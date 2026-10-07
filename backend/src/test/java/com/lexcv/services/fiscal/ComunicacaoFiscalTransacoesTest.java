package com.lexcv.services.fiscal;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import com.lexcv.repositories.FilaComunicacaoFiscal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 137-17 (ENTR-03, T-137-71): o resultado {@code ACEITE_SIMULADO} enfileira a entrega por email
 * na MESMA transação (outbox atómico), só quando a escrita guardada pela versão atualizou a linha.
 */
class ComunicacaoFiscalTransacoesTest {

    private static final Instant AGORA = Instant.parse("2026-06-15T12:00:00Z");

    private final FilaComunicacaoFiscal fila = mock(FilaComunicacaoFiscal.class);
    private final EnfileiramentoEntregaEmail enfileiramento = mock(EnfileiramentoEntregaEmail.class);
    private final ComunicacaoFiscalTransacoes transacoes = new ComunicacaoFiscalTransacoes(fila,
            mock(DocumentoFiscalRepository.class), mock(DocumentoFiscalLinhaRepository.class),
            mock(DocumentoFiscalXmlRepository.class), mock(ComunicacaoFiscalRepository.class),
            Clock.fixed(AGORA, ZoneOffset.UTC), enfileiramento);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final ComunicacaoReclamada item = new ComunicacaoReclamada(UUID.randomUUID(), tenantId, documentoId,
            AmbienteFiscal.SIMULADO, 1, 3L, 0);

    private void filaDevolve(int linhas) {
        when(fila.registarResultado(any(), any(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(linhas);
    }

    @Test
    void aceiteGravadoEnfileiraUmaVezNaMesmaTransacao() {
        filaDevolve(1);

        int linhas = transacoes.registarResultado(item, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, null);

        assertThat(linhas).isEqualTo(1);
        verify(enfileiramento, times(1)).enfileirarAposAceite(tenantId, documentoId, AGORA);
    }

    @Test
    void leasePerdidoNaoEnfileira() {
        filaDevolve(0);

        int linhas = transacoes.registarResultado(item, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, null);

        assertThat(linhas).isZero();
        verifyNoInteractions(enfileiramento);
    }

    @Test
    void outrosEstadosNaoEnfileiram() {
        filaDevolve(1);

        transacoes.registarResultado(item, EstadoComunicacaoFiscal.PENDENTE, "X", "x", AGORA);
        transacoes.registarResultado(item, EstadoComunicacaoFiscal.REJEITADO, "X", "x", null);
        transacoes.registarResultado(item, EstadoComunicacaoFiscal.ERRO, "X", "x", null);

        verifyNoInteractions(enfileiramento);
    }
}
