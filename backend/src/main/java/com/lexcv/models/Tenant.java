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

    // Phase 120 (PROV-05): campo persistido que decide se o tenant inteiro tem acesso --
    // `true` = tenant utilizavel; `false` = suspenso, sem acesso (login, refresh e qualquer
    // pedido autenticado passam a recusar todos os utilizadores deste tenant, mesmo em
    // sessoes ja ativas -- ver JwtAuthenticationFilter). Mesmo nome e polaridade de
    // User.ativo (nao "suspenso") por consistencia com o resto do codebase. O
    // columnDefinition e obrigatorio, nao cosmetico: em dev/CI (ddl-auto=update) o
    // Hibernate emitiria `alter table t_tenant add column ativo boolean not null` contra
    // uma tabela ja povoada, que o PostgreSQL rejeita (linhas existentes ficariam sem
    // valor) -- com o `default true` embutido no DDL, o ALTER passa e preenche as linhas
    // existentes automaticamente. A tenant reservada "LexCV" (DatabaseSeeder.
    // seedTenantPlataforma(), que constroi com Tenant.builder().nome("LexCV").build() sem
    // passar `ativo`) depende do @Builder.Default abaixo para nascer ativa.
    @Column(name = "ativo", nullable = false, columnDefinition = "boolean not null default true")
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
