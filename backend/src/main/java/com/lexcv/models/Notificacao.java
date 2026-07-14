package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

// WR-01 (Phase 88 code review): DB-level backstop mirroring the idempotency tuple checked by
// AlertasDiariosJob.notificar(...) (existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria).
// Same precedent as Honorario's uk_honorario_processo (Phase 82) -- see also the paired manual
// production migration backend/migrations/88-add-notificacao-dedup-unique-constraint.sql
// (ddl-auto=validate in prod never creates this from the annotation alone).
@Entity
@Table(name = "t_notificacao", uniqueConstraints = @UniqueConstraint(
        name = "uk_notificacao_dedup",
        columnNames = {"tenant_id", "destinatario_id", "entidade_tipo", "entidade_id", "categoria"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "destinatario_id", nullable = false)
    private UUID destinatarioId;

    // Values: FASE_ENTRADA | DOCUMENTO_NOVO | PROCESSO_ATRIBUIDO | PARECER_ATRIBUIDO | ...
    @Column(nullable = false)
    private String categoria;

    // String to accommodate both UUID and Integer IDs across entities
    @Column(name = "entidade_tipo", nullable = false)
    private String entidadeTipo;

    @Column(name = "entidade_id", nullable = false)
    private String entidadeId;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "link_url")
    private String linkUrl;

    @Setter
    @Column(nullable = false)
    @Builder.Default
    private Boolean lida = false;

    // NOTF-26: toggle de visibilidade ortogonal a `lida` -- nula = nunca adiada; preenchida =
    // oculta das superfícies de não-lidas (badge/marcar-todas) até este instante. Mesma
    // mutabilidade (campo @Setter) de `lida`, sem entrar em uk_notificacao_dedup nem em qualquer
    // outra unique constraint -- ver backend/migrations/96-add-notificacao-snoozed-until.sql.
    @Setter
    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
