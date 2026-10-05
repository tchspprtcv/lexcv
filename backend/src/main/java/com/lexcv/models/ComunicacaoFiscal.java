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
 * <p>{@code @Version} protege as transições concorrentes da Phase 136. {@code createdAt} vem do
 * {@code Clock} injetado no serviço. Esquema manual:
 * {@code backend/migrations/134-create-documento-fiscal-tables.sql} +
 * {@code 136-efatura-comunicacao.sql}.
 *
 * <p>Phase 136 (DFE-04, DFE-06): colunas da fila de comunicação ({@code lease_ate},
 * {@code ultima_tentativa_em}, {@code ultimo_erro} -- mensagem curta e segura para o utilizador,
 * nunca um stack trace --, {@code ultimo_erro_codigo}, {@code concluido_em},
 * {@code reprocessamentos}); a próxima tentativa reutiliza {@code proxima_tentativa_em}.
 * {@code reprocessamentos} tem {@code DEFAULT 0} na base de dados -- desvio deliberado da regra
 * "sem defaults" para que {@code ddl-auto: update} consiga acrescentar a coluna NOT NULL a uma
 * tabela já povoada (o script 136 faz o mesmo backfill). O {@code @Check} da classe garante na
 * base de dados que nenhuma linha fora de {@code ambiente = 'PRODUCAO'} pode alguma vez ficar
 * {@code AUTORIZADO}.
 */
@Entity
@Table(name = "t_comunicacao_fiscal",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_comunicacao_fiscal_documento",
               columnNames = {"documento_fiscal_id"}),
       indexes = {
               @Index(name = "idx_comunicacao_fiscal_tenant_estado", columnList = "tenant_id, estado"),
               @Index(name = "idx_comunicacao_fiscal_estado_proxima", columnList = "estado, proxima_tentativa_em")})
@org.hibernate.annotations.Check(name = "ck_comunicacao_fiscal_autorizado_producao",
        constraints = "estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO'")
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

    /** Phase 136: linha reclamada por um worker até este instante; {@code null} = livre. */
    @Column(name = "lease_ate")
    private Instant leaseAte;

    @Column(name = "ultima_tentativa_em")
    private Instant ultimaTentativaEm;

    /** Phase 136: mensagem curta, em português, segura para mostrar ao utilizador. */
    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    @Column(name = "ultimo_erro_codigo", length = 64)
    private String ultimoErroCodigo;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    /** Phase 136: número de reprocessamentos manuais (episódio de falha para a notificação). */
    @Column(name = "reprocessamentos", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private Integer reprocessamentos = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
