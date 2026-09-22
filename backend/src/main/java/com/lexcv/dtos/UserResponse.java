package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private UUID tenant_id;
    private String nome;
    private String email;
    // Phase 127 (Plano 05, Decisao 6): "roles" e "tenant_role_ids" respondem a perguntas
    // diferentes. "roles" sao os NOMES efectivos (via ResolucaoPapeisService.resolverNomesPapeis)
    // -- e o que a app mostra e o que os consumidores de usePermissions/useMe leem, incluindo o
    // caminho de papeis globais (utilizador ainda nao convertido). "tenant_role_ids" sao os ids
    // dos TenantRole que o utilizador detem -- a CHAVE de atribuicao que o formulario de edicao
    // usa para pre-selecionar os papeis certos na lista do proprio escritorio (UI-SPEC §2). So o
    // segundo sobrevive a uma renomeacao de papel: um nome em "roles" pode mudar de baixo do
    // utilizador, um id em "tenant_role_ids" nunca muda.
    private Set<String> roles;
    private Set<UUID> tenant_role_ids;
    private Set<String> permissions;
    private String avatar_url;
    private String telefone;
    private Boolean ativo;
    private String tenant_nome;
    private String tenant_logo_data_url;
    private String tenant_plano;
    private Integer tenant_limite_utilizadores;
    private Long tenant_utilizadores_ativos;
}
