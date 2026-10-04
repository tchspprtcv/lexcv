package com.lexcv.dtos;

import com.lexcv.models.MotivoIsencaoIva;

import java.util.Arrays;
import java.util.List;

/**
 * Phase 133 (CFG-02): um motivo oficial de isenção de IVA, lido de {@link MotivoIsencaoIva}
 * (a única fonte da lista).
 */
public record MotivoIsencaoResponse(String codigo, String descricao, String mencao) {

    /** Todos os motivos, na ordem do enum (códigos 1..21). */
    public static List<MotivoIsencaoResponse> todos() {
        return Arrays.stream(MotivoIsencaoIva.values())
                .map(m -> new MotivoIsencaoResponse(m.codigo(), m.descricao(), m.mencao()))
                .toList();
    }
}
