package com.lexcv.repositories;

import com.lexcv.models.TenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {
    // IN-01 (125-REVIEW.md): sem chamador ainda em backend/src/main -- scaffolding deliberado
    // para fases futuras, nao codigo morto:
    //   - findByTenantId: a Phase 127 (CRUD de escritorio) precisa de listar os t_tenant_role de
    //     um tenant.
    //   - findByTenantIdAndNome: a Phase 126 (migracao) precisa de repontar t_user_role de um
    //     papel global para o TenantRole homonimo do proprio tenant.
    // Revisitar em cada uma dessas fases; se continuarem sem chamador depois delas, remover.
    List<TenantRole> findByTenantId(UUID tenantId);
    Optional<TenantRole> findByTenantIdAndNome(UUID tenantId, String nome);

    // Phase 125 (MOLD-01): quantos escritorios ja instanciaram um dado molde -- o numero que a
    // consola de moldes precisa para o aviso de nao-propagacao (a alteracao a um molde nao
    // chega a escritorios ja provisionados).
    long countByMoldeId(Integer moldeId);
}
