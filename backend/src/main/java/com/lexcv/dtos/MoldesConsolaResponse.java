package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Payload único de {@code GET /api/v1/platform/moldes} (Phase 125, MOLD-02): catálogo de
 * permissões elegíveis mais a lista de moldes instanciáveis, num só pedido, sob o gate de classe
 * {@code hasRole('PLATAFORMA_ADMIN')} de {@link com.lexcv.controllers.PlatformAdminController}.
 *
 * <p>Deliberadamente **não** reutiliza a definição de par chave/rótulo do DTO de resposta de
 * {@code AdminController#getRbac()} ({@code GET /admin/rbac}): esse endpoint tem gate
 * {@code hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')} -- mais largo do que esta consola, que
 * é exclusiva de {@code PLATAFORMA_ADMIN}. Acoplar os dois contratos faria uma alteração futura à
 * superfície de administração de escritório (Phase 127, CATL-04/{@code rbac:manage}) arrastar,
 * sem necessidade, o contrato desta consola de plataforma. {@link PermissaoDto} é por isso uma
 * classe própria, de forma idêntica mas independente.
 *
 * <p>{@code escritoriosInstanciados} (em {@link MoldeDto}) é o número load-bearing de todo o
 * aviso de não-propagação do UI-SPEC: sem ele, o ecrã não consegue cumprir o requisito de
 * "impossível de ler mal" sobre a decisão de snapshot (MOLD-03) -- editar um molde nunca chega a
 * escritórios já provisionados, e é este número que prova isso ao operador antes de gravar.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoldesConsolaResponse {
    private List<PermissaoDto> permissoes;
    private List<MoldeDto> moldes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissaoDto {
        private String key;
        private String nome;
        private String descricao;
        private String modulo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoldeDto {
        private Integer id;
        private String nome;
        private List<String> permissoes;
        private long escritoriosInstanciados;
    }
}
