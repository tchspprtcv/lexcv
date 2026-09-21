package com.lexcv.dtos;

import lombok.Getter;
import lombok.Setter;

/**
 * Corpo de {@code PUT /api/v1/admin/rbac/papeis/{id}/nome} (Phase 127, PAPEL-02): renomeia um
 * papel próprio já existente do escritório do chamador. Altera apenas o nome apresentado -- nunca
 * as permissões nem os utilizadores já atribuídos.
 */
@Getter
@Setter
public class PapelRenameRequest {
    private String nome;
}
