package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.CodigoParametroFiscal;
import com.lexcv.models.ParametroFiscal;
import com.lexcv.repositories.ParametroFiscalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Phase 133 (CFG-04), Plan 02: o único leitor dos parâmetros fiscais com vigência.
 *
 * <p>O valor devolvido é uma PERCENTAGEM (ex.: {@code 15.0000} significa 15%); a Phase 134
 * converte-o em fração no momento do cálculo. Este serviço nunca contém uma taxa numérica: as
 * taxas são dados em {@code t_parametro_fiscal}, semeados pelo {@code DatabaseSeeder}.
 */
@Service
@RequiredArgsConstructor
public class ParametroFiscalService {

    private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");

    private final ParametroFiscalRepository parametroFiscalRepository;
    private final Clock clock;

    /**
     * Valor em vigor na data indicada: a linha mais recente com {@code vigente_desde <= data}.
     *
     * @throws RecusaFiscalException 503 {@code PARAMETRO_FISCAL_EM_FALTA} se nenhuma linha se aplica
     */
    @Transactional(readOnly = true)
    public BigDecimal valorVigente(CodigoParametroFiscal codigo, LocalDate data) {
        return parametroFiscalRepository
                .findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(codigo.name(), data)
                .map(ParametroFiscal::getValor)
                .orElseThrow(() -> new RecusaFiscalException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "PARAMETRO_FISCAL_EM_FALTA",
                        "Parâmetro fiscal sem valor em vigor nesta data."));
    }

    /** Valor em vigor hoje, na data civil de Cabo Verde (UTC-1), derivada do {@link Clock} injetado. */
    @Transactional(readOnly = true)
    public BigDecimal valorVigenteHoje(CodigoParametroFiscal codigo) {
        return valorVigente(codigo, LocalDate.now(clock.withZone(FUSO_CABO_VERDE)));
    }
}
