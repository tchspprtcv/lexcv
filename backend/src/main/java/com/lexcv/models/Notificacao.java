package com.lexcv.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

// WR-01 (Phase 88 code review): DB-level backstop mirroring the idempotency tuple checked by
// AlertasDiariosJob.notificar(...) (existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria).
// Same precedent as Honorario's uk_honorario_processo (Phase 82) -- see also the paired manual
// production migration backend/migrations/88-add-notificacao-dedup-unique-constraint.sql
// (ddl-auto=validate in prod never creates this from the annotation alone).
@Entity
@Table(name = "t_notificacao", uniqueConstraints = @UniqueConstraint(
        name = "uk_notificacao_dedup",
        columnNames = {"tenant_id", "destinatario_id", "entidade_tipo", "entidade_id", "categoria"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "destinatario_id", nullable = false)
    private UUID destinatarioId;

    // Values: FASE_ENTRADA | DOCUMENTO_NOVO | PROCESSO_ATRIBUIDO | PARECER_ATRIBUIDO | ...
    @Column(nullable = false)
    private String categoria;

    // String to accommodate both UUID and Integer IDs across entities
    @Column(name = "entidade_tipo", nullable = false)
    private String entidadeTipo;

    @Column(name = "entidade_id", nullable = false)
    private String entidadeId;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "link_url")
    private String linkUrl;

    @Setter
    @Column(nullable = false)
    @Builder.Default
    private Boolean lida = false;

    // NOTF-26: toggle de visibilidade ortogonal a `lida` -- nula = nunca adiada; preenchida =
    // oculta das superfícies de não-lidas (badge/marcar-todas) até este instante. Mesma
    // mutabilidade (campo @Setter) de `lida`, sem entrar em uk_notificacao_dedup nem em qualquer
    // outra unique constraint -- ver backend/migrations/96-add-notificacao-snoozed-until.sql.
    //
    // WR-02 (Phase 96 code review): @JsonFormat com sufixo 'Z' literal explícito. Todo o resto
    // do codebase usa LocalDateTime naive sem qualquer configuração Jackson de fuso horário (grep
    // de backend/src/main/resources não encontra nenhuma), o que é inofensivo enquanto o valor só
    // alimenta formatação de rótulo (ex.: createdAt em notification-bell.tsx, só usado com
    // toLocaleDateString). snoozedUntil é diferente: o frontend faz `new Date(snoozedUntil) >
    // new Date()` para DECIDIR visibilidade (notification-bell.tsx, notificacoes/page.tsx), e
    // `new Date("...sem Z")` é interpretado pelo motor JS como hora LOCAL DO BROWSER, não do
    // servidor -- um mismatch de fuso horário entre o container (que corre em UTC, ver
    // AlertasDiariosJob.FUSO_CABO_VERDE) e o browser do utilizador desalinhava silenciosamente o
    // predicado de "ainda adiado?". Anexar 'Z' torna explícito que o instante já é UTC (verdade,
    // dado que o container roda em UTC), sem exigir a migração mais ampla do tipo da coluna para
    // Instant/OffsetDateTime. Convenção para novos campos time-sensíveis que GATEIAM
    // comportamento (não apenas formatam um rótulo): aplicar o mesmo @JsonFormat aqui.
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Setter
    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
