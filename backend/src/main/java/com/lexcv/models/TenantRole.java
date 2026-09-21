package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Phase 125 (MOLD-01/MOLD-03): papel de escritorio -- a copia por tenant de um molde de
// t_role. Unico por (tenant_id, nome), consistente com as restantes constraints por tenant do
// projeto (ex. Cliente (tenant_id, documento_numero)): unicidade global impediria dois
// escritorios de terem ambos um papel "ADVOGADO", o que e exactamente o ponto do marco v2.17.
@Entity
@Table(name = "t_tenant_role", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "nome"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// WR-02 (126-REVIEW.md): igualdade explicita por (tenantId, nome) -- o par que
// @UniqueConstraint acima ja declara como identidade logica desta entidade, o mesmo padrao que
// Role.java:15 usa para "nome" (unico globalmente ali). Sem isto, TenantRole caia no equals/
// hashCode de identidade de Object, e a idempotencia de
// MigracaoPapeisEscritorioService.migrar() (alvo.equals(user.getTenantRoles())) so funcionava
// por coincidencia do identity-map do Hibernate dentro da mesma transaccao -- uma garantia real
// mas implicita, nunca documentada como requisito, e que um refactor futuro (duas transaccoes,
// EntityManager.clear()/.detach(), sessao stateless) quebraria silenciosamente. "id" fica de
// fora de proposito -- e gerado, nao faz parte da identidade logica, e dois TenantRole com o
// mesmo (tenantId, nome) mas ids diferentes (ex.: linha recriada apos remocao) devem continuar a
// contar como a mesma entidade para este calculo.
@EqualsAndHashCode(of = {"tenantId", "nome"})
public class TenantRole {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    // Proveniencia historica, NAO uma associacao JPA navegavel (D: "Snapshot, not live
    // reference", 125-CONTEXT.md). Deliberadamente um Integer cru -- nunca @ManyToOne, nunca
    // @JoinColumn sobre Role, nunca cascade. Duas consequencias: (a) editar o molde nao pode
    // chegar a esta linha por nenhum caminho de navegacao JPA -- e por isso que o ecra de
    // moldes tem de avisar explicitamente que a alteracao nao se propaga a escritorios ja
    // provisionados; (b) nao existe FK na base de dados (ver backend/migrations/126-
    // add-tenant-role-tables.sql), para que um molde possa vir a ser apagado no futuro sem
    // tornar esta linha historica irremovivel.
    @Column(name = "molde_id")
    private Integer moldeId;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean sistema = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "t_tenant_role_permission",
        joinColumns = @JoinColumn(name = "tenant_role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}
