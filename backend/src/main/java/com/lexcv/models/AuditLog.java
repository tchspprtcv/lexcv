package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // nullable: documents not always linked to a processo (Pitfall #2)
    @Column(name = "processo_id")
    private UUID processoId;

    // Values: transicao_estado | conflict_check_decisao | documento_download | documento_eliminacao
    @Column(name = "acao", nullable = false)
    private String acao;

    // Values: processo | documento | conflict_check_decisao
    @Column(name = "entidade_tipo", nullable = false)
    private String entidadeTipo;

    // String to accommodate both UUID and Integer IDs across entities
    @Column(name = "entidade_id", nullable = false)
    private String entidadeId;

    // nullable: system events without a user actor
    @Column(name = "autor_id")
    private UUID autorId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) this.timestamp = LocalDateTime.now();
    }
}
