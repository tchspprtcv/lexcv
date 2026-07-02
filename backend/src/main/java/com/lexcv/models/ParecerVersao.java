package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_parecer_versao",
        uniqueConstraints = @UniqueConstraint(columnNames = {"solicitacao_id", "numero_versao"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParecerVersao implements Persistable<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "solicitacao_id", nullable = false)
    private UUID solicitacaoId;

    @Column(name = "numero_versao")
    private Integer numeroVersao;

    @Column(columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "caminho_anexo")
    private String caminhoAnexo;

    @Column(name = "criado_por_id")
    private UUID criadoPorId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean aprovado = false;

    @Column(name = "aprovado_por_id")
    private UUID aprovadoPorId;

    @Column(name = "aprovado_em")
    private LocalDateTime aprovadoEm;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ParecerController.createVersao() assigns `id` via UUID.randomUUID() before save() (needed
    // up front as the MinIO object-key prefix for the attachment). With @GeneratedValue(UUID)
    // and no version field, Spring Data's default isNew() check falls back to "id == null", so a
    // pre-assigned non-null id makes save() route through merge() instead of persist() — Hibernate
    // then tries to UPDATE a row that doesn't exist yet and throws
    // ObjectOptimisticLockingFailureException. createdAt is only populated by @PrePersist, so it's
    // null for a genuinely new, unsaved instance and always non-null for anything loaded via
    // findById() — a reliable signal independent of the manually-assigned id.
    @Override
    public boolean isNew() {
        return this.createdAt == null;
    }
}
