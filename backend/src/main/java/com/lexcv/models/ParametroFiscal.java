package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 133 (CFG-04): parâmetro fiscal com vigência (ex.: {@code IVA_TAXA_NORMAL = 15} desde
 * uma data). GLOBAL, sem tenant: é uma regra legal comum a todos os escritórios (decisão do
 * CONTEXT). Uma mudança de regra é uma nova linha com {@code vigente_desde} posterior, nunca uma
 * alteração da linha existente. Semeado por {@code DatabaseSeeder} (upsert não destrutivo).
 */
@Entity
@Table(name = "t_parametro_fiscal",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_parametro_fiscal_codigo_vigencia",
               columnNames = {"codigo", "vigente_desde"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametroFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "codigo", nullable = false, length = 64)
    private String codigo;

    @Column(name = "valor", nullable = false, precision = 9, scale = 4)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    // Sem @PrePersist com Instant.now(): o chamador define createdAt a partir do Clock injetado.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
