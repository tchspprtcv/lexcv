package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "t_cliente",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "documento_numero"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    private String tipo;

    @Column(nullable = false)
    private String nome;

    private String nif;
    private String email;
    private String telefone;
    private String morada;
    private String localidade;
    private Boolean ativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_tipo")
    private DocumentoTipo documentoTipo;

    @Column(name = "documento_numero")
    private String documentoNumero;

    @Column(name = "ramo_atividade")
    private String ramoAtividade;

    @Column(name = "detalhes_adicionais", length = 255)
    private String detalhesAdicionais;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.ativo == null) {
            this.ativo = true;
        }
    }
}
