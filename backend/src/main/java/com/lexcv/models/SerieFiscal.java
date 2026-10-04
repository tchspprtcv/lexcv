package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 133 (CFG-05): série de numeração fiscal por (tenant, tipo de documento, ano, ambiente),
 * com o contador {@code ultimo_numero} (0 = nenhum documento emitido). A chave única é a do
 * CONTEXT; {@code codigo} é gerado ({@link #gerarCodigo}) e NÃO faz parte da chave.
 *
 * <p>Criada apenas por {@code SerieFiscalRepository.criarSeNaoExiste} (INSERT ... ON CONFLICT DO
 * NOTHING) e incrementada sob lock pessimista ({@code SerieFiscalRepository.bloquear}).
 * {@code led_codigo} fica nulo até à Phase 136.
 */
@Entity
@Table(name = "t_serie_fiscal",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_serie_fiscal",
               columnNames = {"tenant_id", "tipo_documento", "ano", "ambiente"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SerieFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // varchar sem CHECK: novos tipos não exigem DROP CONSTRAINT.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, columnDefinition = "varchar(32) not null")
    private TipoDocumentoFiscal tipoDocumento;

    @Column(name = "ano", nullable = false)
    private Integer ano;

    // varchar sem CHECK: a Phase 136 acrescenta um ambiente real sem alteração de esquema.
    @Enumerated(EnumType.STRING)
    @Column(name = "ambiente", nullable = false, columnDefinition = "varchar(32) not null")
    private AmbienteFiscal ambiente;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "led_codigo")
    private Integer ledCodigo;

    @Column(name = "ultimo_numero", nullable = false)
    @Builder.Default
    private Long ultimoNumero = 0L;

    // Sem @PrePersist com Instant.now(): o INSERT nativo usa now(); outros chamadores usam o Clock.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Código da série: prefixo do ambiente + tipo + "-" + ano (ex.: {@code SIM-FR-2026}).
     * No máximo 20 caracteres e conforme o padrão do XSD
     * {@code [A-Za-z0-9]+([_-][A-Za-z0-9]+)*}.
     */
    public static String gerarCodigo(TipoDocumentoFiscal tipo, int ano, AmbienteFiscal ambiente) {
        return ambiente.prefixoSerie() + tipo.name() + "-" + ano;
    }
}
