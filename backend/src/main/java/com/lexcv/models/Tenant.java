package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    private String nif;

    @Column(name = "tipo_entidade")
    private String tipoEntidade;

    private String email;
    private String telefone;

    @Lob
    @Column(name = "logo_data_url")
    private String logoDataUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "plano")
    private TenantPlano plano;

    // Phase 117 (limite de utilizadores por tenant): null = sem limite (plano Enterprise
    // "por acordo"); um valor numérico é o limite exato de utilizadores ativos. Nunca usar
    // sentinela mágico (-1/MAX_VALUE). Consumido por AdminController.limiteUtilizadoresExcedido,
    // chamado a partir de createUser (antes de persistir um novo utilizador) e de updateUser
    // (reativação, false -> true) — ver CR-01 em 117-REVIEW.md.
    @Column(name = "limite_utilizadores")
    private Integer limiteUtilizadores;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
