package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 134 (D-08): estado da comunicação de um {@link DocumentoFiscal} à plataforma eFatura.
 * Satélite MUTÁVEL (o documento é imutável): exatamente uma linha por documento
 * ({@code uk_comunicacao_fiscal_documento}), criada {@link EstadoComunicacaoFiscal#PENDENTE} na
 * mesma transação da emissão e consumida pela Phase 136.
 *
 * <p>{@code @Version} protege as transições concorrentes da Phase 136. Sem defaults na base de
 * dados (paridade simples com o script manual
 * {@code backend/migrations/134-create-documento-fiscal-tables.sql}); {@code createdAt} vem do
 * {@code Clock} injetado no serviço.
 */
@Entity
@Table(name = "t_comunicacao_fiscal",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_comunicacao_fiscal_documento",
               columnNames = {"documento_fiscal_id"}),
       indexes = @Index(name = "idx_comunicacao_fiscal_tenant_estado", columnList = "tenant_id, estado"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComunicacaoFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "documento_fiscal_id", nullable = false, updatable = false)
    private UUID documentoFiscalId;

    @Convert(converter = AmbienteFiscalConverter.class)
    @Column(name = "ambiente", nullable = false, length = 32)
    private AmbienteFiscal ambiente;

    @Convert(converter = EstadoComunicacaoFiscalConverter.class)
    @Column(name = "estado", nullable = false, length = 32)
    private EstadoComunicacaoFiscal estado;

    @Column(name = "tentativas", nullable = false)
    @Builder.Default
    private Integer tentativas = 0;

    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
