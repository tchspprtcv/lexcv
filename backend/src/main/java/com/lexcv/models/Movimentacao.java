package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_movimentacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movimentacao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    private String tipo;
    private String descricao;
    private LocalDateTime data;

    @Column(name = "prazo_id")
    private Integer prazoId;

    // nullable FK — populated when Movimentacao is created by a user action (e.g. state transition)
    @Column(name = "autor_id")
    private UUID autorId;
}
