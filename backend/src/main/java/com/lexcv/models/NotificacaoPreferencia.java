package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// NOTF-24: join-table entity for per-user notification category muting.
// Semantics (locked in 93-CONTEXT.md): presence of a row = the category is
// silenced for that user; absence = default-on delivery. No boolean flag is
// stored — row existence IS the preference. Mirrors the ClienteAdvogado /
// ClienteAdministrativo tenant-scoped join-table convention.
@Entity
@Table(name = "t_notificacao_preferencia",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_notificacao_preferencia",
               columnNames = {"tenant_id", "user_id", "categoria"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacaoPreferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Values: FASE_ENTRADA | DOCUMENTO_NOVO | PROCESSO_ATRIBUIDO | PARECER_ATRIBUIDO | ...
    @Column(nullable = false)
    private String categoria;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
