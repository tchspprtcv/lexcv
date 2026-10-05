package com.lexcv.services.fiscal;

import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 135 (NCRD-01, NCRD-02): resultado puro de {@link ComposicaoNotaCredito#compor}, partilhado
 * pela pré-visualização e pela emissão da Nota de Crédito.
 *
 * <p>Todos os montantes são magnitudes POSITIVAS (o sinal negativo vive só no {@code Pagamento} de
 * estorno). {@code valorCreditavelAntes} é o total da FR menos o total das NC já emitidas;
 * {@code valorCreditavelDepois} desconta também esta NC.
 */
public record ProjetoNotaCredito(UUID documentoOrigemId,
                                 String documentoOrigemNumero,
                                 TipoCredito tipoCredito,
                                 MotivoNotaCredito motivo,
                                 String motivoTexto,
                                 LocalDate dataEmissao,
                                 String descricaoLinha,
                                 RegimeIva regime,
                                 CalculoFiscal.ResultadoCalculo calculo,
                                 BigDecimal totalOrigem,
                                 BigDecimal totalCreditadoAntes,
                                 BigDecimal valorCreditavelAntes,
                                 BigDecimal valorCreditavelDepois) {
}
