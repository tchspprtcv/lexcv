package com.lexcv.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "t_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    @JsonIgnore
    private String passwordHash;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    private String telefone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "numero_cedula")
    private String numeroCedula;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "t_user_role",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Phase 126 (MIGR-01/MIGR-03): associacao a papeis de ESCRITORIO (TenantRole), numa tabela
    // NOVA que COEXISTE com t_user_role em vez de a substituir -- D-04 do 126-CONTEXT.md: a
    // migracao e reversivel, reverter e voltar a apontar as leituras, nao restaurar uma copia
    // de seguranca. Por desenho, t_user_role.role_id mantem-se povoado depois da conversao (a
    // remocao dessa coluna e explicitamente diferida para depois de o marco fechar). Este campo
    // nasce vazio e DORMENTE: nada neste plano le tenantRoles -- o cutover de leitura e dos
    // planos 04 e 05.
    //
    // FetchType.EAGER nao e conveniencia: JwtAuthenticationFilter faz deliberadamente UMA query
    // por PK por pedido autenticado, sem qualquer memorizacao entre pedidos, porque o requisito
    // e "imediato", nao "no proximo login" (ver JwtAuthenticationFilter:50-58). Se tenantRoles
    // fosse LAZY, o cutover do plano 04 seria forcado a acrescentar uma query extra por pedido
    // ou a introduzir memorizacao -- exactamente o que aquele comentario proibe. Com EAGER, o
    // userRepository.findById que ja existe traz esta associacao de graca.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "t_user_tenant_role",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "tenant_role_id")
    )
    @Builder.Default
    private Set<TenantRole> tenantRoles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_user_permission", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission")
    @Builder.Default
    private Set<String> permissions = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
