package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta de {@code POST /api/v1/platform/moldes} (Phase 125, MOLD-04). Projeção mínima do
 * molde recém-criado -- id e nome -- na mesma disciplina de "nunca a entidade crua" que
 * {@link TenantProvisionResponse} já segue para tenants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoldeProvisionResponse {
    private Integer id;
    private String nome;
}
