package com.lexcv.repositories;

import com.lexcv.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByTenantId(UUID tenantId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName")
    List<User> findByTenantIdAndRoleName(@Param("tenantId") UUID tenantId, @Param("roleName") String roleName);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome IN :roleNames")
    List<User> findByTenantIdAndRoleNameIn(@Param("tenantId") UUID tenantId, @Param("roleNames") List<String> roleNames);

    // WR-02 (Phase 94 code review): variant of findByTenantIdAndRoleName that excludes
    // deactivated accounts, used by NotificacaoService's ADMIN fan-out so a deactivated ADMIN
    // does not keep accumulating notification rows indefinitely (mirrors the `ativo` check
    // ResourceController.atribuirResponsavel already applies before assigning a responsible
    // party). Added as a separate method (not a change to findByTenantIdAndRoleName in place)
    // to avoid altering AlertasDiariosJob's existing ADMIN fan-out behavior, which was not part
    // of this review's scope.
    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName AND u.ativo = true")
    List<User> findByTenantIdAndRoleNameAndAtivoTrue(@Param("tenantId") UUID tenantId, @Param("roleName") String roleName);

    // Phase 117 (limite de utilizadores por tenant): UNICA fonte de verdade para "utilizadores
    // ativos de um tenant" no codebase. Consumida por AdminController.limiteUtilizadoresExcedido
    // (chamado a partir de createUser e de updateUser, na reativação false -> true — ver CR-01 em
    // 117-REVIEW.md) para aplicar Tenant.limiteUtilizadores; as Phases 120 (consola de tenants) e
    // 122 (relatório de utilização) reutilizam este mesmo método (Success Criteria 4 da fase) —
    // não duplicar esta contagem noutro sítio.
    long countByTenantIdAndAtivoTrue(UUID tenantId);

    // Phase 127 (Plano 02, PAPEL-05): quantos utilizadores detêm um dado TenantRole -- a guarda
    // do plano 04 usa este número para recusar DELETE /admin/rbac/roles/{id} quando > 0
    // (127-CONTEXT.md Decisão 4: "a verificação é por contagem, não por tentativa-e-erro sobre
    // uma violação de chave estrangeira"). Derivada sobre a propriedade de coleção
    // User.tenantRoles (t_user_tenant_role, EAGER, ver User.java:77-84), no mesmo idioma de
    // countByTenantIdAndAtivoTrue acima e de TenantRoleRepository.countByMoldeId -- nunca um
    // findBy...(id) seguido de .isEmpty()/.size(), e nunca um save() dentro de um
    // try/catch(DataIntegrityViolationException) sobre a FK: uma contagem não muta estado e não
    // paga o custo de carregar a lista inteira de utilizadores só para os contar.
    long countByTenantRolesId(UUID tenantRoleId);
}
