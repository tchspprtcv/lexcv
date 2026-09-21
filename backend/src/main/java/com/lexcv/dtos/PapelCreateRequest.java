package com.lexcv.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Corpo de {@code POST /api/v1/admin/rbac/papeis} (Phase 127, PAPEL-01): cria um novo papel
 * próprio do escritório do chamador, com o conjunto inicial de permissões dado.
 */
@Getter
@Setter
public class PapelCreateRequest {
    private String nome;
    private List<String> permissoes;
}
