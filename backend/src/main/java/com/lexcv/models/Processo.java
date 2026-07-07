package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_processo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Processo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "responsavel_id")
    private UUID responsavelId;

    @Column(name = "numero_processo")
    private String numeroProcesso;

    @Column(name = "tipo_processo")
    private String tipoProcesso;

    @Column(name = "area_juridica")
    private String areaJuridica;

    private String tribunal;
    private String estado;
    private String juizo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem")
    private OrigemProcesso origem;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    private String descricao;

    @Column(name = "legal_hold")
    @Builder.Default
    private Boolean legalHold = false;

    @Column(name = "data_retencao")
    private LocalDate dataRetencao;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
