package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_parecer_versao",
        uniqueConstraints = @UniqueConstraint(columnNames = {"solicitacao_id", "numero_versao"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParecerVersao {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "solicitacao_id", nullable = false)
    private UUID solicitacaoId;

    @Column(name = "numero_versao")
    private Integer numeroVersao;

    @Column(columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "caminho_anexo")
    private String caminhoAnexo;

    @Column(name = "criado_por_id")
    private UUID criadoPorId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
