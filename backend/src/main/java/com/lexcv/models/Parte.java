package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "t_parte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String nome;

    private String tipo;
}
