package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_prazo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prazo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "data_limite", nullable = false)
    private LocalDate dataLimite;

    // ALTA | MEDIA | BAIXA
    @Column(nullable = false)
    @Builder.Default
    private String prioridade = "MEDIA";

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean concluido = false;

    // escalonado: true when risco is proximo or vencido
    @Column(nullable = false)
    @Builder.Default
    private Boolean escalonado = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
