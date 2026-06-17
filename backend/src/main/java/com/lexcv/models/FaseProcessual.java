package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "t_fase_processual")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaseProcessual {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String nome;
}
