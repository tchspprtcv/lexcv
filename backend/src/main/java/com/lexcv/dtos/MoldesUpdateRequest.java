package com.lexcv.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Corpo de {@code PUT /api/v1/platform/moldes} (Phase 125, MOLD-02/MOLD-03). A gravação é **em
 * lote** por decisão de UI (UI-SPEC secção 6): a matriz de checkboxes acumula edições locais a
 * vários moldes e o operador confirma tudo num só {@code AlertDialog}, com um só botão
 * "Confirmar e Gravar" -- daí o caminho {@code PUT /platform/moldes}, sem {@code {id}} na rota, e
 * este corpo aceitar uma lista de entradas em vez de uma única.
 */
@Getter
@Setter
public class MoldesUpdateRequest {
    private List<MoldePermissoesEntry> moldes;

    @Getter
    @Setter
    public static class MoldePermissoesEntry {
        private Integer id;
        private List<String> permissoes;
    }
}
