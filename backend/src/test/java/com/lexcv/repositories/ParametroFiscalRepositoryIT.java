package com.lexcv.repositories;

import com.lexcv.models.ParametroFiscal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 133 (CFG-04), Plan 02: prova contra PostgreSQL real que a consulta de vigência escolhe a
 * linha mais recente com {@code vigente_desde <= data} e que a constraint
 * {@code uk_parametro_fiscal_codigo_vigencia} recusa duplicados. Os valores (15/16) são dados de
 * teste. Mesmo scaffolding de {@code NotificacaoPreferenciaRepositoryIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ParametroFiscalRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ParametroFiscalRepository parametroFiscalRepository;

    private ParametroFiscal linha(String codigo, String valor, LocalDate desde) {
        return ParametroFiscal.builder()
                .codigo(codigo)
                .valor(new BigDecimal(valor))
                .vigenteDesde(desde)
                .createdAt(Instant.parse("2026-10-04T12:00:00Z"))
                .build();
    }

    @Test
    void valorVigente_escolheALinhaMaisRecenteAteAData() {
        parametroFiscalRepository.saveAndFlush(linha("IVA_TAXA_NORMAL", "15", LocalDate.of(2000, 1, 1)));
        parametroFiscalRepository.saveAndFlush(linha("IVA_TAXA_NORMAL", "16", LocalDate.of(2030, 1, 1)));

        Optional<ParametroFiscal> antes = parametroFiscalRepository
                .findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        "IVA_TAXA_NORMAL", LocalDate.of(2029, 12, 31));
        Optional<ParametroFiscal> noDia = parametroFiscalRepository
                .findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        "IVA_TAXA_NORMAL", LocalDate.of(2030, 1, 1));
        Optional<ParametroFiscal> antesDeTudo = parametroFiscalRepository
                .findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        "IVA_TAXA_NORMAL", LocalDate.of(1999, 12, 31));

        assertEquals(0, antes.orElseThrow().getValor().compareTo(new BigDecimal("15")));
        assertEquals(0, noDia.orElseThrow().getValor().compareTo(new BigDecimal("16")));
        assertTrue(antesDeTudo.isEmpty());
    }

    @Test
    void mesmaChaveCodigoVigencia_recusada() {
        parametroFiscalRepository.saveAndFlush(linha("IVA_TAXA_NORMAL", "15", LocalDate.of(2000, 1, 1)));

        assertThrows(DataIntegrityViolationException.class, () -> parametroFiscalRepository
                .saveAndFlush(linha("IVA_TAXA_NORMAL", "16", LocalDate.of(2000, 1, 1))));
    }
}
