package com.lexcv.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "t_honorario", uniqueConstraints = @UniqueConstraint(columnNames = "processo_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Honorario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    private String descricao;

    @Column(name = "data_acordo")
    private LocalDate dataAcordo;

    @Formula("(SELECT COALESCE(SUM(p.valor_pago), 0) FROM t_pagamento p WHERE p.honorario_id = id)")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private BigDecimal totalPago;
}
