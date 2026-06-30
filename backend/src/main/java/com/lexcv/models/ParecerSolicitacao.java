package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_parecer_solicitacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParecerSolicitacao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String descricao;

    @Column(name = "processo_id")
    private UUID processoId;

    @Column(name = "advogado_id")
    private UUID advogadoId;

    @Column(name = "versao_final_id")
    private UUID versaoFinalId;

    // ALTA | MEDIA | BAIXA
    @Column(nullable = false)
    @Builder.Default
    private String prioridade = "MEDIA";

    // PENDENTE | EM_ELABORACAO | EM_REVISAO | CONCLUIDO
    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDENTE";

    private LocalDate prazo;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
