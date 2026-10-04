package com.lexcv.services.fiscal;

import com.lexcv.models.MetodoPagamento;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.RegimeIva;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 134 (D-01): projeto validado de uma Fatura-Recibo, produzido por
 * {@link ComposicaoFaturaRecibo#compor}. A pré-visualização devolve-o ao utilizador; a emissão
 * fotografa-o num {@code DocumentoFiscal}. Os campos do adquirente vêm já sem espaços à volta
 * (o NIF tal como validado); {@code motivoIsencao} só existe em regime ISENTO.
 */
public record ProjetoFaturaRecibo(
        UUID clienteId,
        UUID processoId,
        Integer honorarioId,
        LocalDate dataEmissao,
        MetodoPagamento metodo,
        String descricaoLinha,
        RegimeIva regime,
        MotivoIsencaoIva motivoIsencao,
        CalculoFiscal.ResultadoCalculo calculo,
        String emitenteNif,
        String emitenteFirma,
        String emitenteMorada,
        String emitenteLocalidade,
        String adquirenteNif,
        String adquirenteNome,
        String adquirenteMorada,
        String adquirenteLocalidade
) {
}
