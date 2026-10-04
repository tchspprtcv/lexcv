package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.CodigoParametroFiscal;
import com.lexcv.models.ParametroFiscal;
import com.lexcv.repositories.ParametroFiscalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 133 (CFG-04), Plan 02: leitura do valor de um parâmetro fiscal em vigor numa data.
 * Os valores usados aqui são dados de teste (fixtures), não constantes de produção.
 */
@ExtendWith(MockitoExtension.class)
class ParametroFiscalServiceTest {

    @Mock
    private ParametroFiscalRepository parametroFiscalRepository;

    private ParametroFiscalService servico(Instant instante) {
        return new ParametroFiscalService(parametroFiscalRepository, Clock.fixed(instante, ZoneOffset.UTC));
    }

    private static ParametroFiscal linha(String valor) {
        return ParametroFiscal.builder()
                .codigo("IVA_TAXA_NORMAL")
                .valor(new BigDecimal(valor))
                .vigenteDesde(LocalDate.of(2000, 1, 1))
                .build();
    }

    @Test
    void valorVigente_devolveValorDaLinhaEConsultaComCodigoEDataExatos() {
        LocalDate data = LocalDate.of(2026, 10, 4);
        when(parametroFiscalRepository.findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                "IVA_TAXA_NORMAL", data)).thenReturn(Optional.of(linha("15.0000")));

        BigDecimal valor = servico(Instant.parse("2026-10-04T12:00:00Z"))
                .valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, data);

        assertEquals(0, valor.compareTo(new BigDecimal("15.0000")));
        verify(parametroFiscalRepository)
                .findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc("IVA_TAXA_NORMAL", data);
    }

    @Test
    void valorVigente_semLinha_recusaComParametroFiscalEmFalta503() {
        when(parametroFiscalRepository.findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                any(), any())).thenReturn(Optional.empty());

        RecusaFiscalException ex = assertThrows(RecusaFiscalException.class,
                () -> servico(Instant.parse("2026-10-04T12:00:00Z"))
                        .valorVigente(CodigoParametroFiscal.RETENCAO_SUGERIDA, LocalDate.of(1999, 12, 31)));

        assertEquals("PARAMETRO_FISCAL_EM_FALTA", ex.getCodigo());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void valorVigenteHoje_usaDataDeCaboVerde_antesDaMeiaNoiteLocal() {
        LocalDate esperada = LocalDate.of(2026, 12, 31);
        when(parametroFiscalRepository.findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                eq("IVA_TAXA_NORMAL"), eq(esperada))).thenReturn(Optional.of(linha("15")));

        servico(Instant.parse("2027-01-01T00:30:00Z")).valorVigenteHoje(CodigoParametroFiscal.IVA_TAXA_NORMAL);

        verify(parametroFiscalRepository)
                .findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc("IVA_TAXA_NORMAL", esperada);
    }

    @Test
    void valorVigenteHoje_usaDataDeCaboVerde_depoisDaMeiaNoiteLocal() {
        LocalDate esperada = LocalDate.of(2027, 1, 1);
        when(parametroFiscalRepository.findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                eq("IVA_TAXA_NORMAL"), eq(esperada))).thenReturn(Optional.of(linha("15")));

        servico(Instant.parse("2027-01-01T01:30:00Z")).valorVigenteHoje(CodigoParametroFiscal.IVA_TAXA_NORMAL);

        verify(parametroFiscalRepository)
                .findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc("IVA_TAXA_NORMAL", esperada);
    }
}
