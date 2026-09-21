package com.lexcv.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
@Builder
public class UserPrincipal implements UserDetails {
    private final UUID userId;
    private final UUID tenantId;
    private final String nome;
    private final String email;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;
    // Phase 127 (PAPEL-04, 127-CONTEXT.md Decisao 2a): ids de molde dos papeis efectivos do
    // utilizador, resolvidos UMA vez pelo filtro de autenticacao via
    // ResolucaoPapeisService.resolverMoldeIds -- ver o comentario la para os dois ramos
    // (escritorio/global). @Builder.Default para que os muitos fixtures de teste que usam
    // UserPrincipal.builder() sem este campo continuem a compilar e nunca sofram NPE.
    @Builder.Default
    private final Set<Integer> moldeIds = Set.of();

    public static UserPrincipal create(UUID userId, UUID tenantId, String nome, String email, Set<String> roles, Set<String> dbPermissions, Set<Integer> moldeIds) {
        Set<String> permissions = new java.util.HashSet<>(dbPermissions);
        
        Set<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toSet());
        
        if (roles.contains("ADMIN")) {
            // Keep in sync with DatabaseSeeder.CATALOGO_PERMISSOES (Phase 124).
            permissions.addAll(java.util.Arrays.asList(
                    "clientes:view", "clientes:edit",
                    "processos:view", "processos:edit",
                    "processos:create", "processos:manage",
                    "agenda:view", "agenda:edit",
                    "documentos:view", "documentos:edit",
                    "financeiro:view", "financeiro:edit", "financeiro:manage",
                    "rbac:manage", "users:manage",
                    "pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage",
                    "notificacoes:view"
            ));
        }

        permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        return UserPrincipal.builder()
                .userId(userId)
                .tenantId(tenantId)
                .nome(nome)
                .email(email)
                .roles(roles)
                .permissions(permissions)
                .authorities(authorities)
                .moldeIds(moldeIds != null ? moldeIds : Set.of())
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.unmodifiableCollection(authorities);
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
