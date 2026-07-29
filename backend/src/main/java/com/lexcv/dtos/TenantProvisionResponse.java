package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Resposta de {@code POST /api/v1/platform/tenants} (Phase 119/120, PROV-06). Existe
 * precisamente para nunca serializar a entidade {@code Tenant} diretamente -- a mesma
 * disciplina de "nunca entidade crua" documentada em {@code PublicController} -- devolvendo
 * apenas o suficiente para o ecrã de administração de plataforma confirmar a criação: o
 * identificador gerado e a designação da tenant recém-provisionada. Nenhum outro atributo da
 * entidade, nem qualquer dado do utilizador administrador inicial criado em conjunto, é exposto
 * neste contrato.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProvisionResponse {
    private UUID id;
    private String nome;
}
