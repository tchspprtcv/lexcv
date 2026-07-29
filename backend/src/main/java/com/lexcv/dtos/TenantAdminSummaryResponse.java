package com.lexcv.dtos;

import com.lexcv.models.TenantPlano;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Projeção mínima de {@link com.lexcv.models.Tenant} consumida pela consola de administração de
 * tenants da Phase 120 ({@code GET /api/v1/platform/tenants}) e reutilizável, sem alterações,
 * pelo relatório de utilização da Phase 122 -- ambas as superfícies precisam exatamente destes
 * mesmos 6 campos. {@code limiteUtilizadores} a {@code null} significa "sem limite", a mesma
 * semântica de {@link com.lexcv.models.Tenant#getLimiteUtilizadores()} (nunca um sentinela
 * mágico).
 *
 * <p>Segue a mesma disciplina de "nunca a entidade crua" de {@link TenantProvisionResponse}: o
 * logótipo em Base64 (campo anotado com {@code @Lob}, potencialmente megabytes de tamanho), o
 * NIF, o email, o telefone e a data de criação são deliberadamente omitidos -- a consola não os
 * mostra, e transportá-los seria peso de payload e superfície de exposição gratuitos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAdminSummaryResponse {
    private UUID id;
    private String nome;
    private TenantPlano plano;
    private Integer limiteUtilizadores;
    private Boolean ativo;
    private long utilizadoresAtivos;
}
