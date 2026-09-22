package com.lexcv.repositories;

import com.lexcv.models.TenantRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 04: fecha a corrida de "ultimo administrador"
    // (127-REVIEW.md CR-01) entre dois pedidos concorrentes que reduziriam ambos, cada um vendo a
    // mesma contagem "antes", os detentores activos do papel protegido a zero. Mesmo precedente de
    // ParecerSolicitacaoRepository#findByIdForUpdate (WR-04, Phase 87): um lock PESSIMISTIC_WRITE
    // de uma UNICA linha, adquirido ANTES da contagem que decide se a operacao pode prosseguir, e
    // mantido ate ao commit da transaccao do chamador (so funciona dentro de @Transactional -- o
    // Plano 04 garante isso nos tres pontos de chamada da guarda). Serializa QUALQUER operacao que
    // possa reduzir os detentores activos do papel protegido -- os tres call sites de
    // AdminController#guardaUltimoAdministrador: updateUser (remocao via tenantRoleIds e
    // desativacao via "ativo": false) e deleteUser.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tr FROM TenantRole tr WHERE tr.id = :id")
    Optional<TenantRole> bloquearParaAlteracaoDeDetentores(@Param("id") UUID id);
}
