package com.lexcv.services.fiscal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 133 (CFG-05): número fiscal atribuído por {@link NumeracaoService#proximoNumero}.
 *
 * <p>O {@code numero} só é definitivo se a transação do chamador fizer commit: um rollback
 * liberta-o (o incremento de {@code t_serie_fiscal.ultimo_numero} é revertido) e o próximo
 * chamador recebe o mesmo número -- é isto que torna a série sem lacunas.
 *
 * @param serieId     id da série ({@code t_serie_fiscal.id})
 * @param serieCodigo código da série (ex.: {@code SIM-FR-2026})
 * @param ano         ano civil da série, em Atlantic/Cape_Verde
 * @param numero      número sequencial atribuído (1, 2, 3, ...)
 * @param dataEmissao data de Cabo Verde a que o número pertence; o documento usa este dia e
 *                    este ano, para nunca divergir da sua série
 */
public record NumeroFiscalAtribuido(UUID serieId, String serieCodigo, int ano, long numero, LocalDate dataEmissao) {
}
