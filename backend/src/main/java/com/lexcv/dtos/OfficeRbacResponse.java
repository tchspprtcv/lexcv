package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code GET /api/v1/admin/rbac} (Phase 127, CATL-04): a matriz de papéis próprios do
 * escritório do chamador, mais o catálogo de permissões elegíveis, sob o gate de método
 * {@code hasAuthority('rbac:manage')} de {@link com.lexcv.controllers.AdminController#getRbac()}.
 *
 * <p>Deliberadamente **não** reutiliza {@link RbacResponse} (o DTO de leitura do antigo contrato
 * global) nem widen {@link MoldesConsolaResponse} (o contrato da consola de plataforma) --
 * exatamente a razão que o próprio doc-comment de {@code MoldesConsolaResponse} já antecipava:
 * "uma alteração futura à superfície de administração de escritório (Phase 127, CATL-04/
 * {@code rbac:manage})" não deve arrastar o contrato da consola de plataforma, e o inverso
 * também é verdade agora que essa alteração chegou. {@link PermissaoDefDto} é por isso uma
 * classe própria, de forma idêntica a {@code RbacResponse.PermissionDefDto} mas independente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficeRbacResponse {
    private List<PapelDto> papeis;
    private List<PermissaoDefDto> permissoes;

    /**
     * {@code protegido} tem de ser computado no servidor a partir da proveniência
     * ({@code TenantRole.moldeId} == o id do {@code Role} global chamado {@code "ADMIN"}) --
     * NUNCA derivado do nome, porque esta fase torna o nome editável (PAPEL-04): um escritório
     * que renomeie o seu papel de administrador não pode perder a proteção por isso.
     *
     * <p>{@code podeApagar} é computado no servidor como
     * {@code utilizadoresAtribuidos == 0 && !protegido}, para que nenhum cliente tenha de
     * re-derivar a mesma lógica booleana duas vezes (uma no servidor para recusar o DELETE, outra
     * no frontend para decidir o que mostrar).
     *
     * <p>{@code utilizadoresAtribuidos} é lido ao vivo no momento da projeção, tal como
     * {@code PlatformAdminController#toSummary}'s {@code utilizadoresAtivos} -- nunca em cache.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PapelDto {
        private UUID id;
        private String nome;
        private boolean sistema;
        private boolean protegido;
        private boolean podeApagar;
        private long utilizadoresAtribuidos;
        private List<String> permissoes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissaoDefDto {
        private String key;
        private String nome;
        private String descricao;
        private String modulo;
    }
}
