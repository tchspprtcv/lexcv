package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 137 (ENTR-03, ENTR-04, ENTR-05): estado da entrega por email de um {@link DocumentoFiscal}
 * ao cliente. Satélite MUTÁVEL (o documento continua imutável): exatamente uma linha por documento
 * ({@code uk_entrega_email_fiscal_documento}), sempre com {@code tenant_id}.
 *
 * <p>A linha só é criada depois de a comunicação do documento ter chegado a
 * {@link EstadoComunicacaoFiscal#ACEITE_SIMULADO} (137-17); todas as escritas posteriores
 * (reclamar com lease, tentativa, sucesso, falha, reenvio) passam por SQL nativo em
 * {@code FilaEntregaEmail} (137-07) -- o repositório não tem {@code save}. {@code @Version}
 * protege as transições concorrentes. O nome da tabela não começa por {@code t_documento_fiscal}
 * de propósito: esse prefixo é reservado às tabelas imutáveis (DocumentoFiscalImutabilidadeTest,
 * Teste 9).
 *
 * <p>{@code destinatario} é um endereço de entrega (dado pessoal): nunca é registado em logs nem
 * em auditoria. {@code ultimo_erro} é uma mensagem curta e segura para o utilizador, nunca o texto
 * do servidor SMTP nem um stack trace. {@code reenvios} conta os reenvios manuais e identifica o
 * episódio de falha para a notificação {@code EMAIL_FISCAL_FALHOU}; tem {@code DEFAULT 0} na base de
 * dados pelo mesmo motivo de {@link ComunicacaoFiscal#getReprocessamentos()} (ddl-auto: update numa
 * tabela já povoada). {@code createdAt} vem do {@code Clock} injetado no serviço.
 */
@Entity
@Table(name = "t_entrega_email_fiscal",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_entrega_email_fiscal_documento",
               columnNames = {"documento_fiscal_id"}),
       indexes = {
               @Index(name = "idx_entrega_email_fiscal_estado_proxima", columnList = "estado, proxima_tentativa_em"),
               @Index(name = "idx_entrega_email_fiscal_tenant_estado", columnList = "tenant_id, estado")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntregaEmailFiscal {

    /** CONTEXT: no máximo 5 tentativas automáticas por episódio. Fonte única desta constante. */
    public static final int MAX_TENTATIVAS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "documento_fiscal_id", nullable = false, updatable = false)
    private UUID documentoFiscalId;

    @Convert(converter = EstadoEntregaEmailConverter.class)
    @Column(name = "estado", nullable = false, length = 32)
    private EstadoEntregaEmail estado;

    /** Endereço de entrega (dado pessoal): nunca em logs nem em auditoria. */
    @Column(name = "destinatario", length = 254)
    private String destinatario;

    @Column(name = "tentativas", nullable = false)
    @Builder.Default
    private Integer tentativas = 0;

    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    /** Linha reclamada por um worker até este instante; {@code null} = livre. */
    @Column(name = "lease_ate")
    private Instant leaseAte;

    @Column(name = "ultima_tentativa_em")
    private Instant ultimaTentativaEm;

    @Column(name = "enviado_em")
    private Instant enviadoEm;

    /** Mensagem curta, em português, segura para mostrar ao utilizador. */
    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    @Column(name = "ultimo_erro_codigo", length = 64)
    private String ultimoErroCodigo;

    /** Número de reenvios manuais (episódio de falha para a notificação). */
    @Column(name = "reenvios", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private Integer reenvios = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;
}
