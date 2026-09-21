package com.lexcv.config;

import com.lexcv.seed.DatabaseSeeder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IN-02 (124-REVIEW.md): torna testavel a afirmacao feita pelo comentario acima da lista
 * hardcoded de permissoes ADMIN em {@link UserPrincipal#create}
 * ("Keep in sync with DatabaseSeeder.CATALOGO_PERMISSOES (Phase 124)"). Ate agora essa
 * sincronia era garantida apenas por convencao -- nenhum teste falhava se as duas listas
 * divergissem.
 *
 * <p>Le, por reflexao, a chave tecnica de cada {@code DatabaseSeeder.CatalogoEntry} da lista
 * privada {@code CATALOGO_PERMISSOES}, e compara-a, por igualdade de conjuntos, com as
 * permissoes que {@link UserPrincipal#create} atribui a um utilizador com o papel ADMIN e
 * nenhuma permissao vinda da base de dados. Uma futura fase que adicione, remova ou renomeie
 * uma entrada em {@code CATALOGO_PERMISSOES} sem espelhar a mudanca aqui faz este teste falhar
 * -- fechando a lacuna descrita em IN-02.
 */
class UserPrincipalCatalogoSyncTest {

    @Test
    void permissoesAdminHardcoded_mantemSeEmSincroniaComCatalogoPermissoes() throws Exception {
        Set<String> chavesCatalogo = lerChavesCatalogoPermissoes();

        UserPrincipal admin = UserPrincipal.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Admin Teste",
                "admin@teste.cv",
                Set.of("ADMIN"),
                Set.of(),
                Set.of());

        assertEquals(chavesCatalogo, admin.getPermissions());
    }

    private Set<String> lerChavesCatalogoPermissoes() throws Exception {
        Field campo = DatabaseSeeder.class.getDeclaredField("CATALOGO_PERMISSOES");
        campo.setAccessible(true);
        List<?> catalogo = (List<?>) campo.get(null);

        Set<String> chaves = new HashSet<>();
        for (Object entrada : catalogo) {
            Method chaveMethod = entrada.getClass().getDeclaredMethod("chave");
            chaveMethod.setAccessible(true);
            chaves.add((String) chaveMethod.invoke(entrada));
        }
        return chaves;
    }
}
