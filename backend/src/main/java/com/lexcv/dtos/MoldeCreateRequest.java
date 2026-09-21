package com.lexcv.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Corpo de {@code POST /api/v1/platform/moldes} (Phase 125, MOLD-04): cria um novo molde de
 * papel, já marcado como instanciável, disponível para escritórios provisionados a partir daí.
 */
@Getter
@Setter
public class MoldeCreateRequest {
    private String nome;
    private List<String> permissoes;
}
