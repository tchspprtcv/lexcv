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
}
