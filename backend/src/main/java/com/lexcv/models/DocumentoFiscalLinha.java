package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Phase 134 (D-05, D-07): linha de um {@link DocumentoFiscal}. Nesta fase cada Fatura-Recibo tem
 * exatamente uma linha, com descrição controlada ({@code TextoDocumentoFiscal}) -- nunca a
 * descrição livre do honorário (sigilo profissional).
 *
 * <p>Imutável como o documento: {@code @Immutable}, todas as colunas {@code updatable = false},
 * sem setters. {@code documento_fiscal_id} é um UUID simples (sem {@code @ManyToOne}, convenção
 * do código). Esquema manual: {@code backend/migrations/134-create-documento-fiscal-tables.sql}.
 */
@Immutable
@Entity
@Table(name = "t_documento_fiscal_linha",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_documento_fiscal_linha_numero",
               columnNames = {"documento_fiscal_id", "numero_linha"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DocumentoFiscalLinha {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "documento_fiscal_id", nullable = false, updatable = false)
    private UUID documentoFiscalId;

    @Column(name = "numero_linha", nullable = false, updatable = false)
    private Integer numeroLinha;

    @Column(name = "descricao", nullable = false, length = 200, updatable = false)
    private String descricao;

    @Column(name = "quantidade", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal precoUnitario;

    @Column(name = "valor_base", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal valorBase;

    @Column(name = "taxa_iva", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal taxaIva;

    @Column(name = "valor_iva", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal valorIva;

    @Column(name = "motivo_isencao_codigo", length = 2, updatable = false)
    private String motivoIsencaoCodigo;

    @Column(name = "taxa_retencao", precision = 7, scale = 4, updatable = false)
    private BigDecimal taxaRetencao;

    @Column(name = "valor_retencao", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal valorRetencao;

    @Column(name = "total_linha", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalLinha;
}
