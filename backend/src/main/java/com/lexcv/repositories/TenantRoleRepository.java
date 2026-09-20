package com.lexcv.repositories;

import com.lexcv.models.TenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {
    List<TenantRole> findByTenantId(UUID tenantId);
    Optional<TenantRole> findByTenantIdAndNome(UUID tenantId, String nome);

    // Phase 125 (MOLD-01): quantos escritorios ja instanciaram um dado molde -- o numero que a
    // consola de moldes precisa para o aviso de nao-propagacao (a alteracao a um molde nao
    // chega a escritorios ja provisionados).
    long countByMoldeId(Integer moldeId);
}
