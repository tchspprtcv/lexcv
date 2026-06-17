package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "t_pagamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "honorario_id", nullable = false)
    private Integer honorarioId;

    @Column(name = "valor_pago")
    private BigDecimal valorPago;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    private String metodo;
}
