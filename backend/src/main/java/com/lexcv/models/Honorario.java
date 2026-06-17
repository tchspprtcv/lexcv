package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "t_honorario")
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
}
