package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_conta_corrente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContaCorrente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cliente_id", nullable = false, unique = true)
    private UUID clienteId;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal saldo = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
