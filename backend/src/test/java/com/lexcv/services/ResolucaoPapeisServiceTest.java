package com.lexcv.services;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Phase 126 Plan 02 (MIGR-02 prep): prova comportamental do resolvedor unico de autoridade --
 * caminho duplo (escritorio, senao global), predicado de proveniencia por moldeId, e o limite de
 * moldeId nulo.
 *
 * <p>Segue a convencao Mockito de {@code SetupServiceInstanciacaoMoldesTest}: sem harness
 * {@code @SpringBootTest}, colaboradores mockados, servico instanciado directamente pelo
 * construtor gerado por {@code @RequiredArgsConstructor} (TenantRoleRepository, depois
 * RoleRepository -- a mesma ordem declarada em ResolucaoPapeisService).
 */
@ExtendWith(MockitoExtension.class)
class ResolucaoPapeisServiceTest {

    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private RoleRepository roleRepository;

    private ResolucaoPapeisService service;

    @BeforeEach
    void setUp() {
        service = new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
    }

    private Permission permissao(String nome) {
        return Permission.builder().id(nome.hashCode()).nome(nome).build();
    }

    // Caso 1 -- com papeis de escritorio: nomes e permissoes vem exclusivamente dos papeis de
    // escritorio. Asserção de espaço negativo: o utilizador tem um Role global HOMÓNIMO com uma
    // permissão deliberadamente DIFERENTE, e essa permissão nunca aparece no resultado.
    @Test
    void resolverNomesEPermissoes_comPapeisDeEscritorio_vemExclusivamenteDosPapeisDeEscritorio() {
        Permission permissaoEscritorio = permissao("processos:edit");
        TenantRole tenantRole = TenantRole.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .nome("ADVOGADO")
                .moldeId(2)
                .permissions(new HashSet<>(Set.of(permissaoEscritorio)))
                .build();

        Permission permissaoGlobalDiferente = permissao("financeiro:manage");
        Role roleGlobal = Role.builder().id(9).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissaoGlobalDiferente))).build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>(Set.of(roleGlobal)))
                .tenantRoles(new HashSet<>(Set.of(tenantRole)))
                .permissions(new HashSet<>())
                .build();

        Set<String> nomes = service.resolverNomesPapeis(user);
        Set<String> permissoes = service.resolverPermissoesEfectivas(user);

        assertEquals(Set.of("ADVOGADO"), nomes);
        assertEquals(Set.of("processos:edit"), permissoes);
        assertFalse(permissoes.contains("financeiro:manage"),
                "user.getRoles() (papeis globais) nao pode ter contribuido quando ha papeis de escritorio");
    }

    // Caso 2 -- SEM papeis de escritorio (o caso plataforma@lexcv.cv): nomes e permissoes vem dos
    // papeis globais, identico a forma actual de JwtAuthenticationFilter:60-67/69. Este e o teste
    // que falha se o administrador de plataforma ficar trancado fora da sua propria consola.
    @Test
    void resolverNomesEPermissoes_semPapeisDeEscritorio_caiParaPapeisGlobais() {
        Permission permissaoGlobal = permissao("plataforma:manage");
        Role roleGlobal = Role.builder().id(1).nome("PLATAFORMA_ADMIN")
                .permissions(new HashSet<>(Set.of(permissaoGlobal))).build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>(Set.of(roleGlobal)))
                .tenantRoles(new HashSet<>())
                .permissions(new HashSet<>())
                .build();

        Set<String> nomes = service.resolverNomesPapeis(user);
        Set<String> permissoes = service.resolverPermissoesEfectivas(user);

        // Forma actual, reproduzida literalmente: JwtAuthenticationFilter.java:60-67.
        Set<String> nomesEsperados = user.getRoles().stream()
                .map(Role::getNome).collect(Collectors.toSet());
        Set<String> permissoesEsperadas = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getNome)
                .collect(Collectors.toSet());

        assertEquals(nomesEsperados, nomes);
        assertEquals(permissoesEsperadas, permissoes);
    }

    // Caso 3 -- parcela 2 (permissoes directas) sempre incluida, nos dois caminhos.
    @Test
    void resolverPermissoesEfectivas_parcela2SempreIncluida_nosDoisCaminhos() {
        Role roleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        User userCaminhoGlobal = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>(Set.of(roleGlobal)))
                .tenantRoles(new HashSet<>())
                .permissions(new HashSet<>(Set.of("directa:global")))
                .build();
        assertTrue(service.resolverPermissoesEfectivas(userCaminhoGlobal).contains("directa:global"));

        TenantRole tenantRole = TenantRole.builder().nome("ADMIN").permissions(new HashSet<>()).build();
        User userCaminhoEscritorio = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>())
                .tenantRoles(new HashSet<>(Set.of(tenantRole)))
                .permissions(new HashSet<>(Set.of("directa:escritorio")))
                .build();
        assertTrue(service.resolverPermissoesEfectivas(userCaminhoEscritorio).contains("directa:escritorio"));
    }

    // Caso 4 -- resolverNomesPapeis e resolverPermissoesEfectivas escolhem SEMPRE o mesmo lado:
    // utilizador com os dois tipos de papel, nomes e permissoes pertencem ambos ao lado de
    // escritorio.
    @Test
    void resolverNomesEPermissoes_comOsDoisTiposDePapel_escolhemSempreOMesmoLado() {
        Permission permissaoGlobal = permissao("global:only");
        Role roleGlobal = Role.builder().id(1).nome("PAPEL_GLOBAL")
                .permissions(new HashSet<>(Set.of(permissaoGlobal))).build();

        Permission permissaoEscritorio = permissao("escritorio:only");
        TenantRole tenantRole = TenantRole.builder().nome("PAPEL_ESCRITORIO")
                .permissions(new HashSet<>(Set.of(permissaoEscritorio))).build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>(Set.of(roleGlobal)))
                .tenantRoles(new HashSet<>(Set.of(tenantRole)))
                .permissions(new HashSet<>())
                .build();

        assertEquals(Set.of("PAPEL_ESCRITORIO"), service.resolverNomesPapeis(user));
        assertEquals(Set.of("escritorio:only"), service.resolverPermissoesEfectivas(user));
    }

    // Caso 5 -- temPapelDeMolde por proveniencia: sobrevive a uma renomeacao do papel de
    // escritorio -- prova que a Phase 127 (renomear papel) nao quebra este sitio.
    @Test
    void temPapelDeMolde_porProveniencia_sobreviveARenomeacao() {
        Role advogadoGlobal = Role.builder().id(7).nome("ADVOGADO").build();
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(advogadoGlobal));

        TenantRole tenantRoleRenomeado = TenantRole.builder()
                .nome("Advogado Senior")
                .moldeId(7)
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>())
                .tenantRoles(new HashSet<>(Set.of(tenantRoleRenomeado)))
                .permissions(new HashSet<>())
                .build();

        assertTrue(service.temPapelDeMolde(user, "ADVOGADO"));
    }

    // Caso 6 -- temPapelDeMolde com moldeId nulo devolve false (o limite preservado de proposito:
    // um papel criado de raiz por um escritorio nunca corresponde).
    @Test
    void temPapelDeMolde_comMoldeIdNulo_devolveFalse() {
        Role advogadoGlobal = Role.builder().id(7).nome("ADVOGADO").build();
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(advogadoGlobal));

        TenantRole tenantRoleDeRaiz = TenantRole.builder()
                .nome("ADVOGADO")
                .moldeId(null)
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>())
                .tenantRoles(new HashSet<>(Set.of(tenantRoleDeRaiz)))
                .permissions(new HashSet<>())
                .build();

        assertFalse(service.temPapelDeMolde(user, "ADVOGADO"));
    }

    // Caso 7 -- temPapelDeMolde em utilizador sem papeis de escritorio cai para comparacao por
    // nome e devolve o mesmo resultado que hoje.
    @Test
    void temPapelDeMolde_semPapeisDeEscritorio_caiParaComparacaoPorNome() {
        Role advogadoGlobal = Role.builder().id(7).nome("ADVOGADO").build();
        User userComAdvogado = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>(Set.of(advogadoGlobal)))
                .tenantRoles(new HashSet<>())
                .permissions(new HashSet<>())
                .build();
        assertTrue(service.temPapelDeMolde(userComAdvogado, "ADVOGADO"));

        Role tecnicoGlobal = Role.builder().id(8).nome("TECNICO").build();
        User userSemAdvogado = User.builder()
                .id(UUID.randomUUID())
                .roles(new HashSet<>(Set.of(tecnicoGlobal)))
                .tenantRoles(new HashSet<>())
                .permissions(new HashSet<>())
                .build();
        assertFalse(service.temPapelDeMolde(userSemAdvogado, "ADVOGADO"));
    }

    // Caso 8 -- resolverPapeisDeEscritorio devolve conjunto vazio, sem excepcao, quando
    // findByTenantIdAndNome nao encontra nada.
    @Test
    void resolverPapeisDeEscritorio_semCorrespondencia_devolveConjuntoVazioSemExcepcao() {
        UUID tenantId = UUID.randomUUID();
        Role roleGlobal = Role.builder().id(1).nome("ADVOGADO").build();
        when(tenantRoleRepository.findByTenantIdAndNome(tenantId, "ADVOGADO")).thenReturn(Optional.empty());

        Set<TenantRole> resultado = service.resolverPapeisDeEscritorio(tenantId, List.of(roleGlobal));

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }
}
