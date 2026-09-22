package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;
import java.util.UUID;

// Phase 128 (AUDT-04), Decision 6: @Immutable is half of the by-construction immutability
// guarantee -- with this annotation Hibernate's dirty-checking never issues an UPDATE for a
// loaded AuditLog instance (post-load field mutation via a getter/setter is simply never
// flushed back to the row). @PrePersist below still fires normally on first save() -- only
// post-load change tracking is suppressed, so the ten existing "build, then save once" write
// sites are unaffected. The other half of the guarantee is AuditLogRepository being narrowed
// to Repository<AuditLog, Long> (no delete*/saveAll/saveAndFlush in the compiled API) -- see
// that file's header comment. Both halves are pinned by AuditLogImutabilidadeTest.
@Immutable
@Entity
@Table(name = "t_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // nullable: documents not always linked to a processo (Pitfall #2)
    @Column(name = "processo_id")
    private UUID processoId;

    // Values: transicao_estado | conflict_check_decisao | documento_download | documento_eliminacao |
    // processo_atribuir | parecer_criar | parecer_atribuir | parecer_aprovar | parecer_entregar |
    // parecer_versao_criar | papel_criar | papel_renomear | papel_apagar | papel_permissoes_alterar |
    // papel_atribuir | papel_retirar (Phase 128, RBAC vocabulary -- entidadeTipo papel_escritorio for
    // the first four, atribuicao_papel for the last two; see AuditLogRepository.buscarEventosRbac)
    @Column(name = "acao", nullable = false)
    private String acao;

    // Values: processo | documento | conflict_check_decisao | parecer_solicitacao | parecer_versao |
    // papel_escritorio (Phase 128: entidadeId = TenantRole.id) | atribuicao_papel (Phase 128:
    // entidadeId = target User.id)
    @Column(name = "entidade_tipo", nullable = false)
    private String entidadeTipo;

    // String to accommodate both UUID and Integer IDs across entities
    @Column(name = "entidade_id", nullable = false)
    private String entidadeId;

    // nullable: system events without a user actor
    @Column(name = "autor_id")
    private UUID autorId;

    // Phase 128 (AUDT-01/AUDT-02), Decision 2 (revised): nullable, additive -- the ten existing
    // processo/parecer write sites never set it, so it stays null for them. JSON object serialized
    // to text, written only by AuditoriaRbacService (Plan 02). Keys: autorNome, papelId, papelNome,
    // alvoNome, nomeAntigo, nomeNovo, permissoesAdicionadas, permissoesRemovidas, motivo -- see
    // 128-CONTEXT.md and 128-01-PLAN.md's <interfaces> block for the exact vocabulary. Stores only
    // display names (User.nome at event time), role names and permission keys -- never an email,
    // phone or other personal field. This is a deliberate revision of the original decision (which
    // planned to resolve names at read time from the id, never storing them): AdminController
    // .deleteUser performs a hard delete (userRepository.deleteById), so an id-only record would
    // resolve to null for every event mentioning a removed user, including the very event that
    // recorded their departure -- defeating the purpose of an accountability log, whose most common
    // investigation is precisely about someone who already left. A name stored here is never
    // refreshed if the person later changes their name: it stays the name they had when the event
    // happened, which is the correct behavior for a historical record.
    @Column(name = "detalhe", columnDefinition = "text")
    private String detalhe;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (this.timestamp == null) this.timestamp = LocalDateTime.now();
    }
}
