package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.SerieFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.SerieFiscalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Phase 133 (CFG-05): contrato unitário do {@link NumeracaoService}. */
class NumeracaoServiceTest {

    private static final Clock MEIO_DE_2026 = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    private SerieFiscalRepository repository;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        repository = mock(SerieFiscalRepository.class);
        tenantId = UUID.randomUUID();
    }

    private SerieFiscal serie(int ano, long ultimoNumero) {
        return SerieFiscal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .tipoDocumento(TipoDocumentoFiscal.FR)
                .ano(ano)
                .ambiente(AmbienteFiscal.SIMULADO)
                .codigo(SerieFiscal.gerarCodigo(TipoDocumentoFiscal.FR, ano, AmbienteFiscal.SIMULADO))
                .ultimoNumero(ultimoNumero)
                .build();
    }

    @Test
    void proximoNumero_temPropagacaoMandatory() throws NoSuchMethodException {
        Method m = NumeracaoService.class.getMethod("proximoNumero",
                UUID.class, TipoDocumentoFiscal.class, AmbienteFiscal.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }

    @Test
    void proximoNumero_incrementaSobLockNaOrdemCerta() {
        SerieFiscal serie = serie(2026, 4L);
        when(repository.bloquear(tenantId, TipoDocumentoFiscal.FR, 2026, AmbienteFiscal.SIMULADO))
                .thenReturn(Optional.of(serie));
        NumeracaoService service = new NumeracaoService(repository, MEIO_DE_2026);

        NumeroFiscalAtribuido r = service.proximoNumero(tenantId, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO);

        assertEquals(5L, r.numero());
        assertEquals(5L, serie.getUltimoNumero());
        assertEquals("SIM-FR-2026", r.serieCodigo());
        assertEquals(2026, r.ano());
        assertEquals(serie.getId(), r.serieId());
        assertEquals(LocalDate.of(2026, 6, 15), r.dataEmissao());

        InOrder ordem = inOrder(repository);
        ordem.verify(repository).definirLockTimeoutLocal();
        ordem.verify(repository).criarSeNaoExiste(any(UUID.class), eq(tenantId), eq("FR"), eq(2026),
                eq("SIMULADO"), eq("SIM-FR-2026"));
        ordem.verify(repository).bloquear(tenantId, TipoDocumentoFiscal.FR, 2026, AmbienteFiscal.SIMULADO);
    }

    @Test
    void proximoNumero_0030UtcDe1DeJaneiroPertenceAoAnoAnteriorEmCaboVerde() {
        when(repository.bloquear(tenantId, TipoDocumentoFiscal.FR, 2025, AmbienteFiscal.SIMULADO))
                .thenReturn(Optional.of(serie(2025, 0L)));
        NumeracaoService service = new NumeracaoService(repository,
                Clock.fixed(Instant.parse("2026-01-01T00:30:00Z"), ZoneOffset.UTC));

        NumeroFiscalAtribuido r = service.proximoNumero(tenantId, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO);

        assertEquals(2025, r.ano());
        assertEquals(LocalDate.of(2025, 12, 31), r.dataEmissao());
        assertEquals(1L, r.numero());
        verify(repository).criarSeNaoExiste(any(UUID.class), eq(tenantId), eq("FR"), eq(2025),
                eq("SIMULADO"), eq("SIM-FR-2025"));
    }

    @Test
    void proximoNumero_0130UtcDe1DeJaneiroJaEAnoNovo() {
        when(repository.bloquear(tenantId, TipoDocumentoFiscal.FR, 2026, AmbienteFiscal.SIMULADO))
                .thenReturn(Optional.of(serie(2026, 0L)));
        NumeracaoService service = new NumeracaoService(repository,
                Clock.fixed(Instant.parse("2026-01-01T01:30:00Z"), ZoneOffset.UTC));

        NumeroFiscalAtribuido r = service.proximoNumero(tenantId, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO);

        assertEquals(2026, r.ano());
        assertEquals(LocalDate.of(2026, 1, 1), r.dataEmissao());
    }

    @Test
    void proximoNumero_lockNaoObtidoNoBloquear_recusaSerieIndisponivel() {
        when(repository.bloquear(any(), any(), any(), any()))
                .thenThrow(new CannotAcquireLockException("lock timeout"));
        NumeracaoService service = new NumeracaoService(repository, MEIO_DE_2026);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.proximoNumero(tenantId, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO));
        assertEquals("SERIE_INDISPONIVEL", e.getCodigo());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }

    @Test
    void proximoNumero_lockNaoObtidoNoInsert_recusaSerieIndisponivel() {
        when(repository.criarSeNaoExiste(any(), any(), anyString(), anyInt(), anyString(), anyString()))
                .thenThrow(new CannotAcquireLockException("lock timeout"));
        NumeracaoService service = new NumeracaoService(repository, MEIO_DE_2026);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.proximoNumero(tenantId, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO));
        assertEquals("SERIE_INDISPONIVEL", e.getCodigo());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
    }

    @Test
    void proximoNumero_argumentosNulos_falhamAntesDoRepositorio() {
        NumeracaoService service = new NumeracaoService(repository, MEIO_DE_2026);
        assertThrows(NullPointerException.class,
                () -> service.proximoNumero(null, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO));
        assertThrows(NullPointerException.class,
                () -> service.proximoNumero(tenantId, null, AmbienteFiscal.SIMULADO));
        assertThrows(NullPointerException.class,
                () -> service.proximoNumero(tenantId, TipoDocumentoFiscal.FR, null));
        verifyNoInteractions(repository);
    }

    @Test
    void proximoNumero_serieInexistenteDepoisDoInsert_violacaoDeInvariante() {
        when(repository.bloquear(any(), any(), any(), any())).thenReturn(Optional.empty());
        NumeracaoService service = new NumeracaoService(repository, MEIO_DE_2026);

        assertThrows(IllegalStateException.class,
                () -> service.proximoNumero(tenantId, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO));
    }
}
