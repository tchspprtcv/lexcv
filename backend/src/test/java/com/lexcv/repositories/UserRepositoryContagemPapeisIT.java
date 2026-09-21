package com.lexcv.repositories;

import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prova, contra PostgreSQL real (Testcontainers, mesma infraestrutura de
 * {@link NotificacaoPreferenciaRepositoryIT}), que {@link UserRepository#countByTenantRolesId}
 * resolve -- é a única derived query deste repositório atravessando uma propriedade de coleção
 * ({@code User.tenantRoles}, tabela de junção {@code t_user_tenant_role}), pelo que não pode
 * ficar sem prova direta (127-02-PLAN.md Task 1).
 *
 * <p>{@code @DataJpaTest} (não {@code @SpringBootTest}) contorna por construção o bloqueio
 * MINIO_ENDPOINT -- esta fatia de contexto nunca instancia MinioConfig/SecurityConfig.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = Replace.NONE)} é obrigatório a par de
 * {@code @ServiceConnection} -- sem ele o {@code @DataJpaTest} troca silenciosamente a DataSource
 * por uma base embutida, ignorando o contentor Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryContagemPapeisIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRoleRepository tenantRoleRepository;

    private TenantRole novoTenantRole(UUID tenantId, String nome) {
        return tenantRoleRepository.save(TenantRole.builder()
                .tenantId(tenantId)
                .nome(nome)
                .sistema(false)
                .permissions(new HashSet<>())
                .build());
    }

    private User novoUser(UUID tenantId, String email, Set<TenantRole> tenantRoles) {
        return userRepository.save(User.builder()
                .tenantId(tenantId)
                .nome("Utilizador de Teste")
                .email(email)
                .passwordHash("hash-irrelevante-para-este-teste")
                .ativo(true)
                .tenantRoles(tenantRoles)
                .build());
    }

    @Test
    void countByTenantRolesId_papelSemAtribuicoes_devolveZero() {
        UUID tenantId = UUID.randomUUID();
        TenantRole papelNaoAtribuido = novoTenantRole(tenantId, "PAPEL_SEM_UTILIZADORES");

        long contagem = userRepository.countByTenantRolesId(papelNaoAtribuido.getId());

        assertEquals(0, contagem);
    }

    @Test
    void countByTenantRolesId_papelComTresAtribuicoes_devolveTres() {
        UUID tenantId = UUID.randomUUID();
        TenantRole papelAtribuido = novoTenantRole(tenantId, "PAPEL_COM_UTILIZADORES");

        novoUser(tenantId, "u1-" + UUID.randomUUID() + "@teste.cv", Set.of(papelAtribuido));
        novoUser(tenantId, "u2-" + UUID.randomUUID() + "@teste.cv", Set.of(papelAtribuido));
        novoUser(tenantId, "u3-" + UUID.randomUUID() + "@teste.cv", Set.of(papelAtribuido));

        long contagem = userRepository.countByTenantRolesId(papelAtribuido.getId());

        assertEquals(3, contagem);
    }

    /**
     * Prova de contenção por tenant (mesmo espírito de isolamento de
     * {@code AdminControllerAtribuicaoPapeisEscritorioTest}): dois tenants têm cada um o seu
     * próprio {@code TenantRole} com o MESMO nome ("ADVOGADO"), cada um com o seu próprio conjunto
     * de utilizadores atribuídos. A contagem de um papel do tenant A nunca inclui os utilizadores
     * do papel homónimo do tenant B -- o que só é verdade porque a contagem é feita pelo id do
     * TenantRole (UUID, único por linha), nunca pelo nome.
     */
    @Test
    void countByTenantRolesId_papeisHomonimosEmTenantsDiferentes_contamSeparadamente() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        TenantRole papelA = novoTenantRole(tenantA, "ADVOGADO");
        TenantRole papelB = novoTenantRole(tenantB, "ADVOGADO");

        novoUser(tenantA, "a1-" + UUID.randomUUID() + "@teste.cv", Set.of(papelA));
        novoUser(tenantA, "a2-" + UUID.randomUUID() + "@teste.cv", Set.of(papelA));
        novoUser(tenantB, "b1-" + UUID.randomUUID() + "@teste.cv", Set.of(papelB));

        assertEquals(2, userRepository.countByTenantRolesId(papelA.getId()));
        assertEquals(1, userRepository.countByTenantRolesId(papelB.getId()));
    }
}
