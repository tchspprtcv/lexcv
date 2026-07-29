package com.lexcv.dtos;

import com.lexcv.models.TenantPlano;
import lombok.Getter;
import lombok.Setter;

/**
 * Corpo de {@code PUT /api/v1/platform/tenants/{id}} (Phase 120, PROV-04): ajusta apenas
 * {@code plano} e {@code limiteUtilizadores} de um tenant já existente. {@code plano} é
 * deliberadamente do tipo do enum {@link TenantPlano}, não {@code String} -- é isso que faz o
 * Jackson recusar automaticamente, com {@code 400}, um valor que não corresponda a nenhuma das
 * suas constantes, sem necessidade de validação manual. {@code limiteUtilizadores} continua a
 * aceitar {@code null} como "sem limite", a mesma semântica de
 * {@link com.lexcv.models.Tenant#getLimiteUtilizadores()}.
 *
 * <p>Não inclui {@code ativo}: suspender/reativar um tenant tem o seu próprio endpoint dedicado
 * ({@code PATCH /platform/tenants/{id}/ativo}) -- misturar as duas operações neste único corpo
 * contrariaria a decisão explícita do UI-SPEC desta fase de nunca dobrar o interruptor de
 * suspensão/reativação dentro do diálogo de edição de plano/limite.
 */
@Getter
@Setter
public class TenantUpdateRequest {
    private TenantPlano plano;
    private Integer limiteUtilizadores;
}
