package com.lexcv.models;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 136 (DFE-01, DFE-02): XML eFatura (DFE) gerado em segundo plano a partir do snapshot
 * imutável de um {@link DocumentoFiscal}, com o seu IUD de 45 caracteres.
 *
 * <p>Satélite INSERT-ONLY: no máximo uma linha por documento ({@code uk_documento_fiscal_xml_documento})
 * e IUD único ({@code uk_documento_fiscal_xml_iud}). Entidade imutável para o Hibernate, todas as colunas
 * {@code updatable = false}, sem setters nem builder; o {@code id} é atribuído por quem chama
 * {@link com.lexcv.repositories.DocumentoFiscalXmlRepository#inserirSeAusente}. Uma vez escrita,
 * a linha é reutilizada para sempre (mesmo IUD, mesmos bytes). {@code xmlSha256} é o hex do SHA-256
 * dos bytes UTF-8 exatos guardados em {@code xml}. Esquema manual:
 * {@code backend/migrations/136-efatura-comunicacao.sql}.
 */
@Immutable
@Entity
@Table(name = "t_documento_fiscal_xml",
       uniqueConstraints = {
               @UniqueConstraint(name = "uk_documento_fiscal_xml_documento", columnNames = {"documento_fiscal_id"}),
               @UniqueConstraint(name = "uk_documento_fiscal_xml_iud", columnNames = {"iud"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentoFiscalXml {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "documento_fiscal_id", nullable = false, updatable = false)
    private UUID documentoFiscalId;

    @Column(name = "iud", nullable = false, length = 45, updatable = false)
    private String iud;

    @Convert(converter = AmbienteFiscalConverter.class)
    @Column(name = "ambiente", nullable = false, length = 32, updatable = false)
    private AmbienteFiscal ambiente;

    @Column(name = "repositorio_codigo", nullable = false, updatable = false)
    private Integer repositorioCodigo;

    @Column(name = "led_codigo", nullable = false, updatable = false)
    private Integer ledCodigo;

    @Column(name = "versao_formato", nullable = false, length = 20, updatable = false)
    private String versaoFormato;

    @Column(name = "xml", nullable = false, columnDefinition = "text", updatable = false)
    private String xml;

    @Column(name = "xml_sha256", nullable = false, length = 64, updatable = false)
    private String xmlSha256;

    @Column(name = "gerado_em", nullable = false, updatable = false)
    private Instant geradoEm;
}
