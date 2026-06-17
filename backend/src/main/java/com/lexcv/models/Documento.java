package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_documento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Documento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "processo_id")
    private UUID processoId;

    @Column(name = "cliente_id")
    private UUID clienteId;

    private String nome;
    private String tipo;

    @Column(name = "confidencialidade")
    private String confidencialidade;

    @Column(name = "caminho_arquivo")
    private String caminhoArquivo;

    @Builder.Default
    private Integer versao = 1;

    private Long tamanho;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
