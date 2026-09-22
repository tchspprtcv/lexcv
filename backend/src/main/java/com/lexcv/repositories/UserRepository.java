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

    // Phase 127 fix (CR-01, 127-REVIEW.md): quantos utilizadores ACTIVOS detêm um dado TenantRole
    // -- a guarda de "último administrador" em AdminController usa este número para recusar
    // qualquer operação (remoção de atribuição, desativação, eliminação de utilizador) que
    // deixaria o papel de administrador do escritório sem nenhum detentor activo, trancando o
    // escritório inteiro fora de /api/v1/admin/**. Variante de countByTenantRolesId acima que
    // também filtra por `ativo = true`, no mesmo idioma de findByTenantIdAndRoleNameAndAtivoTrue
    // -- um utilizador já desativado não conta como "detentor" para este efeito, porque já não
    // exerce a autoridade do papel.
    long countByTenantRolesIdAndAtivoTrue(UUID tenantRoleId);

    // Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 04: variante de countByTenantRolesIdAndAtivoTrue
    // acima que exclui explicitamente o utilizador que esta a ser alterado -- a guarda de "ultimo
    // administrador" passa a contar "quantos OUTROS detentores activos existem", nunca "quantos
    // detentores activos existem incluindo este". Necessario porque, sob @Transactional (Plano
    // 04), o Hibernate pode fazer AUTO flush da mutacao pendente deste utilizador (ex.:
    // user.setAtivo(false) ja aplicado em memoria por outra parte do mesmo metodo, ou uma
    // colecao ja reatribuida) antes de uma query sobre User correr -- nesse caso a antiga
    // assuncao "a sua propria linha ainda nao foi gravada, por isso ele continua contado" deixa
    // de ser garantida. Contar os OUTROS e correcto quer tenha havido flush ou nao: se este
    // utilizador vai deixar de contar, ele nunca deveria fazer parte da contagem que decide se a
    // operacao pode prosseguir.
    long countByTenantRolesIdAndAtivoTrueAndIdNot(UUID tenantRoleId, UUID userId);
}
