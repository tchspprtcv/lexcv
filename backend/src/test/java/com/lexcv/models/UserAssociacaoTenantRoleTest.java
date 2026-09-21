package com.lexcv.models;

import jakarta.persistence.JoinTable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 126 (MIGR-01): prova estrutural pura -- sem Mockito, sem contexto Spring -- de que a
 * associacao nova {@code User.tenantRoles} coexiste com a associacao antiga {@code User.roles}
 * em vez de a substituir (D-04 do 126-CONTEXT.md: migracao reversivel). Segue o precedente de
 * asserto por reflexao sobre campos declarados estabelecido por
 * {@code DatabaseSeederInstanciabilidadeMoldesTest} (Phase 125, 5.o caso).
 *
 * <p>Cada um dos quatro casos falha silenciosamente uma regressao futura diferente:
 * (a) a associacao nova nao aponta a {@code t_user_tenant_role}; (b) a associacao antiga foi
 * "simplificada"/removida; (c) as duas associacoes passaram a partilhar tabela de juncao,
 * misturando papeis globais com papeis de escritorio; (d) {@code tenantRoles} deixou de ser
 * EAGER, quebrando a invariante de "uma query por pedido" de {@code JwtAuthenticationFilter}.
 */
class UserAssociacaoTenantRoleTest {

    private static Field campoDeclarado(String nome) {
        return Optional.ofNullable(fieldOrNull(nome))
                .orElseThrow(() -> new AssertionError("Campo '" + nome + "' nao declarado em User"));
    }

    private static Field fieldOrNull(String nome) {
        try {
            return User.class.getDeclaredField(nome);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Test
    void tenantRoles_usaJoinTableNovaComColunasCorrectas() {
        Field campo = campoDeclarado("tenantRoles");
        JoinTable joinTable = campo.getAnnotation(JoinTable.class);
        if (joinTable == null) {
            fail("Campo 'tenantRoles' tem de ter @JoinTable");
        }

        assertEquals("t_user_tenant_role", joinTable.name());
        assertEquals(1, joinTable.joinColumns().length);
        assertEquals("user_id", joinTable.joinColumns()[0].name());
        assertEquals(1, joinTable.inverseJoinColumns().length);
        assertEquals("tenant_role_id", joinTable.inverseJoinColumns()[0].name());
    }

    @Test
    void roles_continuaAExistirEContinuaApontarATUserRole() {
        Field campo = campoDeclarado("roles");
        JoinTable joinTable = campo.getAnnotation(JoinTable.class);
        if (joinTable == null) {
            fail("Campo 'roles' tem de continuar a ter @JoinTable");
        }

        // Asserto de reversibilidade -- falha se alguem "simplificar" a associacao antiga.
        assertEquals("t_user_role", joinTable.name());
        assertEquals("user_id", joinTable.joinColumns()[0].name());
        assertEquals("role_id", joinTable.inverseJoinColumns()[0].name());
    }

    @Test
    void asDuasAssociacoesNaoPartilhamNomeDeJoinTable() {
        String nomeTabelaRoles = campoDeclarado("roles").getAnnotation(JoinTable.class).name();
        String nomeTabelaTenantRoles = campoDeclarado("tenantRoles").getAnnotation(JoinTable.class).name();

        assertNotEquals(nomeTabelaRoles, nomeTabelaTenantRoles,
                "roles e tenantRoles nao podem partilhar tabela de juncao -- misturaria papeis "
                        + "globais com papeis de escritorio numa so tabela");
    }

    @Test
    void tenantRoles_eFetchTypeEager() {
        Field campo = campoDeclarado("tenantRoles");
        jakarta.persistence.ManyToMany manyToMany = campo.getAnnotation(jakarta.persistence.ManyToMany.class);
        if (manyToMany == null) {
            fail("Campo 'tenantRoles' tem de ter @ManyToMany");
        }

        assertEquals(jakarta.persistence.FetchType.EAGER, manyToMany.fetch(),
                "tenantRoles tem de ser EAGER -- LAZY quebraria a invariante de 'uma query por "
                        + "pedido' de JwtAuthenticationFilter");
        assertTrue(true);
    }
}
