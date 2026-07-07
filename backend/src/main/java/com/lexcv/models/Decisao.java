package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "t_decisao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Decisao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDecisao tipo;

    @Column(columnDefinition = "TEXT")
    private String resumo;

    @Column(name = "documento_id")
    private UUID documentoId;
}
