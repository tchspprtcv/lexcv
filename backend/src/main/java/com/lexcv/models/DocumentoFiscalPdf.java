package com.lexcv.models;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 137 (ENTR-01): registo do PDF de um {@link DocumentoFiscal}, gerado uma única vez e
 * guardado no MinIO sob o prefixo do tenant ({@code object_key}).
 *
 * <p>Satélite INSERT-ONLY: no máximo uma linha por documento
 * ({@code uk_documento_fiscal_pdf_documento}) e chave de objeto única
 * ({@code uk_documento_fiscal_pdf_object_key}). Entidade imutável para o Hibernate, todas as
 * colunas {@code updatable = false}, sem setters nem builder; o {@code id} é atribuído por quem
 * chama {@link com.lexcv.repositories.DocumentoFiscalPdfRepository#inserirSeAusente}. Uma linha
 * significa "este PDF existe e é o que é servido": nunca é regenerado nem substituído.
 * {@code sha256} é o hex do SHA-256 dos bytes guardados; {@code versaoModelo} identifica a versão
 * do template usada.
 */
@Immutable
@Entity
@Table(name = "t_documento_fiscal_pdf",
       uniqueConstraints = {
               @UniqueConstraint(name = "uk_documento_fiscal_pdf_documento", columnNames = {"documento_fiscal_id"}),
               @UniqueConstraint(name = "uk_documento_fiscal_pdf_object_key", columnNames = {"object_key"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentoFiscalPdf {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "documento_fiscal_id", nullable = false, updatable = false)
    private UUID documentoFiscalId;

    @Column(name = "object_key", nullable = false, length = 512, updatable = false)
    private String objectKey;

    @Column(name = "sha256", nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private Long tamanhoBytes;

    @Column(name = "versao_modelo", nullable = false, length = 20, updatable = false)
    private String versaoModelo;

    @Column(name = "gerado_em", nullable = false, updatable = false)
    private Instant geradoEm;
}
