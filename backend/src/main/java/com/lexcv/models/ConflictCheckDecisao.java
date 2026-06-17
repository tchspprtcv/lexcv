package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_conflict_check_decisao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflictCheckDecisao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    // nivel: sem_conflito | potencial | sanavel | impeditivo
    @Column(name = "nivel", nullable = false)
    private String nivel;

    @Column(name = "justificativa", length = 2000)
    private String justificativa;

    @Column(name = "decisor_id", nullable = false)
    private UUID decisorId;

    @Column(name = "data_decisao", nullable = false)
    private LocalDate dataDecisao;

    @Column(name = "referencia_evidencia")
    private String referenciaEvidencia;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
