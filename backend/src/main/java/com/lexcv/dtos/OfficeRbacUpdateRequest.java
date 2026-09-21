package com.lexcv.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Corpo de {@code PUT /api/v1/admin/rbac} (Phase 127, CATL-04): grava as permissões dos papéis
 * próprios do escritório do chamador, em lote -- um {@link PapelPermissoesDto} por papel,
 * mirando a forma de {@link MoldesUpdateRequest} (Phase 125).
 *
 * <p>Cada entrada é identificada por {@code id} (UUID do {@code TenantRole}), NUNCA por
 * {@code nome} -- é exatamente o que a renomeação de papéis que esta fase entrega (PAPEL-04) torna
 * inseguro: um pedido em trânsito que ainda referencie o nome antigo deixaria de encontrar o
 * papel, ou pior, encontraria um papel diferente que entretanto adotou esse nome.
 */
@Getter
@Setter
public class OfficeRbacUpdateRequest {
    private List<PapelPermissoesDto> papeis;

    @Getter
    @Setter
    public static class PapelPermissoesDto {
        private UUID id;
        private List<String> permissoes;
    }
}
