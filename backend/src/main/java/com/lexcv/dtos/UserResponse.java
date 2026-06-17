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
    private Set<String> roles;
    private Set<String> permissions;
    private String avatar_url;
    private String telefone;
    private Boolean ativo;
}
