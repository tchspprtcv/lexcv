package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 134 (EMIS-01, EMIS-08, D-07): documento fiscal emitido (nesta fase só a Fatura-Recibo).
 *
 * <p><b>Fotografia (snapshot).</b> Os dados do emitente e do adquirente são colunas planas
 * copiadas no momento da emissão -- NUNCA referências às linhas vivas de
 * {@code ConfiguracaoFiscal} ou {@code Cliente}. Alterar depois o cliente ou os dados fiscais do
 * escritório não muda um documento já emitido (EMIS-08).
 *
 * <p><b>Imutável.</b> Um documento nunca é editado nem apagado: {@code @Immutable} impede o
 * Hibernate de emitir UPDATE por dirty-checking, todas as colunas são {@code updatable = false}
 * e a classe não tem setters. A única exceção é {@code cliente_id}, re-apontado por um UPDATE
 * nativo na fusão de clientes (D-15). O estado da comunicação vive no satélite mutável
 * {@link ComunicacaoFiscal}.
 *
 * <p>{@code chave_idempotencia} é interna (deduplicação de pedidos repetidos) e nunca é mostrada
 * ao utilizador. {@code emitidoEm} e {@code dataEmissao} são definidos pelo serviço a partir do
 * {@code Clock} injetado (sem {@code @PrePersist}). Esquema manual equivalente:
 * {@code backend/migrations/134-create-documento-fiscal-tables.sql}.
 */
@Immutable
@Entity
@Table(name = "t_documento_fiscal",
       uniqueConstraints = {
               @UniqueConstraint(name = "uk_documento_fiscal_numero",
                                 columnNames = {"tenant_id", "serie_id", "numero"}),
               @UniqueConstraint(name = "uk_documento_fiscal_pagamento",
                                 columnNames = {"pagamento_id"}),
               @UniqueConstraint(name = "uk_documento_fiscal_chave",
                                 columnNames = {"tenant_id", "chave_idempotencia"})
       },
       indexes = {
               @Index(name = "idx_documento_fiscal_tenant_data", columnList = "tenant_id, data_emissao"),
               @Index(name = "idx_documento_fiscal_tenant_cliente", columnList = "tenant_id, cliente_id"),
               @Index(name = "idx_documento_fiscal_tenant_processo", columnList = "tenant_id, processo_id"),
               @Index(name = "idx_documento_fiscal_tenant_honorario", columnList = "tenant_id, honorario_id")
       })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DocumentoFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    // varchar(32) sem CHECK via @Convert (P-15); comprimento explícito, sem definição de coluna manual (WR-01).
    @Convert(converter = TipoDocumentoFiscalConverter.class)
    @Column(name = "tipo", nullable = false, length = 32, updatable = false)
    private TipoDocumentoFiscal tipo;

    @Convert(converter = AmbienteFiscalConverter.class)
    @Column(name = "ambiente", nullable = false, length = 32, updatable = false)
    private AmbienteFiscal ambiente;

    @Column(name = "serie_id", nullable = false, updatable = false)
    private UUID serieId;

    @Column(name = "serie_codigo", nullable = false, length = 20, updatable = false)
    private String serieCodigo;

    @Column(name = "ano", nullable = false, updatable = false)
    private Integer ano;

    @Column(name = "numero", nullable = false, updatable = false)
    private Long numero;

    @Column(name = "numero_formatado", nullable = false, length = 40, updatable = false)
    private String numeroFormatado;

    @Column(name = "data_emissao", nullable = false, updatable = false)
    private LocalDate dataEmissao;

    @Column(name = "emitido_em", nullable = false, updatable = false)
    private Instant emitidoEm;

    // ---- Emitente (fotografia de ConfiguracaoFiscal) ----

    @Column(name = "emitente_nif", nullable = false, length = 9, updatable = false)
    private String emitenteNif;

    @Column(name = "emitente_firma", nullable = false, length = 200, updatable = false)
    private String emitenteFirma;

    @Column(name = "emitente_morada", nullable = false, length = 100, updatable = false)
    private String emitenteMorada;

    @Column(name = "emitente_localidade", length = 100, updatable = false)
    private String emitenteLocalidade;

    @Convert(converter = RegimeIvaConverter.class)
    @Column(name = "emitente_regime_iva", nullable = false, length = 32, updatable = false)
    private RegimeIva emitenteRegimeIva;

    @Column(name = "emitente_motivo_isencao_codigo", length = 2, updatable = false)
    private String emitenteMotivoIsencaoCodigo;

    @Column(name = "emitente_motivo_isencao_descricao", length = 200, updatable = false)
    private String emitenteMotivoIsencaoDescricao;

    @Column(name = "emitente_motivo_isencao_mencao", length = 100, updatable = false)
    private String emitenteMotivoIsencaoMencao;

    // ---- Adquirente (fotografia do Cliente) ----

    @Column(name = "adquirente_nif", nullable = false, length = 9, updatable = false)
    private String adquirenteNif;

    @Column(name = "adquirente_nome", nullable = false, length = 150, updatable = false)
    private String adquirenteNome;

    @Column(name = "adquirente_morada", nullable = false, length = 100, updatable = false)
    private String adquirenteMorada;

    // R-04: localidade do cliente fotografada quando existir (nula caso contrário).
    @Column(name = "adquirente_localidade", length = 100, updatable = false)
    private String adquirenteLocalidade;

    // ---- Origem ----

    /**
     * Cliente de origem. É a ÚNICA coluna alguma vez re-apontada: a fusão de clientes (D-15)
     * faz um UPDATE nativo; o Hibernate nunca a altera.
     */
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    @Column(name = "processo_id", nullable = false, updatable = false)
    private UUID processoId;

    @Column(name = "honorario_id", nullable = false, updatable = false)
    private Integer honorarioId;

    @Column(name = "pagamento_id", nullable = false, updatable = false)
    private Integer pagamentoId;

    // ---- Pagamento ----

    // MetodoPagamento.name(); String para não acoplar o esquema ao enum.
    @Column(name = "metodo_pagamento", nullable = false, length = 32, updatable = false)
    private String metodoPagamento;

    @Column(name = "meio_pagamento_codigo", nullable = false, length = 3, updatable = false)
    private String meioPagamentoCodigo;

    @Column(name = "moeda", nullable = false, length = 3, updatable = false)
    private String moeda;

    // ---- Totais ----

    @Column(name = "taxa_iva", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal taxaIva;

    @Column(name = "total_base", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalBase;

    @Column(name = "total_iva", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalIva;

    @Column(name = "total_retencao", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalRetencao;

    @Column(name = "total_documento", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalDocumento;

    @Column(name = "valor_liquido", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal valorLiquido;

    @Column(name = "taxa_retencao", precision = 7, scale = 4, updatable = false)
    private BigDecimal taxaRetencao;

    // ---- Controlo ----

    // Interna: nunca mostrada ao utilizador.
    @Column(name = "chave_idempotencia", nullable = false, updatable = false)
    private UUID chaveIdempotencia;

    @Column(name = "emitido_por_id", updatable = false)
    private UUID emitidoPorId;

    @Column(name = "emitido_por_nome", length = 255, updatable = false)
    private String emitidoPorNome;
}
