package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "t_testemunha")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Testemunha {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false)
    private String nome;

    private String contacto;

    @Enumerated(EnumType.STRING)
    private TipoTestemunha tipo;

    private String notas;
}
