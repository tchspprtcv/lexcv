package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "t_facto", uniqueConstraints = @UniqueConstraint(columnNames = {"processo_id", "ordem"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "processo_id", nullable = false)
    private UUID processoId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descricao;

    private LocalDate data;

    @Column(nullable = false)
    private Integer ordem;
}
