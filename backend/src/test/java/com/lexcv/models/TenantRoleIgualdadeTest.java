package com.lexcv.models;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * WR-02 (126-REVIEW.md): prova comportamental de que {@code TenantRole} tem igualdade explicita
 * por {@code (tenantId, nome)} -- o par que a sua propria {@code @UniqueConstraint}
 * (TenantRole.java:14) ja declara como identidade logica -- em vez de cair no equals/hashCode de
 * identidade de {@link Object} herdado por omissao.
 *
 * <p>O Caso 1 e o que teria falhado ANTES desta correcao: duas instancias distintas
 * (equivalentes a duas queries independentes fora de um unico persistence-context Hibernate, ex.
 * apos {@code EntityManager.clear()} ou uma sessao stateless) com o mesmo {@code (tenantId, nome)}
 * eram sempre diferentes por identidade de objecto -- exactamente a dependencia implicita que
 * {@code MigracaoPapeisEscritorioService.migrar()} assumia ao usar
 * {@code alvo.equals(user.getTenantRoles())} para decidir idempotencia (MigracaoPapeisEscritorioService.java:110).
 */
class TenantRoleIgualdadeTest {

    private TenantRole tenantRole(UUID tenantId, String nome, UUID id) {
        return TenantRole.builder().id(id).tenantId(tenantId).nome(nome).permissions(new HashSet<>()).build();
    }

    // Caso 1 -- duas INSTANCIAS DIFERENTES, mesmo (tenantId, nome), ids diferentes: tem de ser
    // iguais. Este e o caso que a dependencia implicita do identity-map do Hibernate escondia --
    // sem @EqualsAndHashCode, este teste falharia (assertEquals reprovaria, duas instancias
    // distintas nunca sao == nem .equals() por omissao).
    @Test
    void duasInstanciasComMesmoTenantIdENome_saoIguais_mesmoComIdsDiferentes() {
        UUID tenantId = UUID.randomUUID();
        TenantRole instanciaA = tenantRole(tenantId, "ADVOGADO", UUID.randomUUID());
        TenantRole instanciaB = tenantRole(tenantId, "ADVOGADO", UUID.randomUUID());

        assertEquals(instanciaA, instanciaB);
        assertEquals(instanciaA.hashCode(), instanciaB.hashCode());
    }

    // Caso 2 -- mesma nome, tenantId DIFERENTE: tem de ser diferentes -- a unicidade real e por
    // (tenant_id, nome), nunca por nome sozinho (ao contrario de Role, que e unico so por nome).
    @Test
    void mesmoNomeTenantIdDiferente_naoSaoIguais() {
        TenantRole tenantRoleA = tenantRole(UUID.randomUUID(), "ADVOGADO", UUID.randomUUID());
        TenantRole tenantRoleB = tenantRole(UUID.randomUUID(), "ADVOGADO", UUID.randomUUID());

        assertNotEquals(tenantRoleA, tenantRoleB);
    }

    // Caso 3 -- mesmo tenantId, nome DIFERENTE: tem de ser diferentes.
    @Test
    void mesmoTenantIdNomeDiferente_naoSaoIguais() {
        UUID tenantId = UUID.randomUUID();
        TenantRole tenantRoleA = tenantRole(tenantId, "ADVOGADO", UUID.randomUUID());
        TenantRole tenantRoleB = tenantRole(tenantId, "ASSISTENTE", UUID.randomUUID());

        assertNotEquals(tenantRoleA, tenantRoleB);
    }

    // Caso 4 -- a idempotencia real de MigracaoPapeisEscritorioService: um Set com uma instancia
    // "alvo" recem-calculada tem de ser .equals() a um Set com a instancia "ja persistida"
    // equivalente, mesmo vindo de objectos Java distintos -- prova directa de que
    // alvo.equals(user.getTenantRoles()) deixa de depender de identity-map do Hibernate.
    @Test
    void setsComInstanciasDistintasMasEquivalentes_saoEquals_provaDaIdempotenciaDaMigracao() {
        UUID tenantId = UUID.randomUUID();
        TenantRole calculadoAgora = tenantRole(tenantId, "ADMIN", UUID.randomUUID());
        TenantRole jaPersistido = tenantRole(tenantId, "ADMIN", UUID.randomUUID());

        Set<TenantRole> alvo = Set.of(calculadoAgora);
        Set<TenantRole> tenantRolesDoUtilizador = Set.of(jaPersistido);

        assertEquals(alvo, tenantRolesDoUtilizador);
    }
}
