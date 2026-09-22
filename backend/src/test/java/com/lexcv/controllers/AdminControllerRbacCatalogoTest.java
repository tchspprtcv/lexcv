package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.OfficeRbacResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.AuditoriaRbacService;
import com.lexcv.services.ResolucaoPapeisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 124 (Plan 02): prova que {@link AdminController#getRbac()} constrói
 * {@code permissoes} (o catálogo) a partir das linhas devolvidas por
 * {@link PermissionRepository#findAllByReservadaPlataformaFalse()} -- não de uma lista Java
 * hardcoded (CATL-01) -- e que permissões reservadas à plataforma ou sem rótulo utilizável nunca
 * chegam a essa lista (CATL-03).
 *
 * <p>Phase 127 (Plano 03, PAPEL-01/PAPEL-09): retargetado de {@code RbacResponse} (nome-keyed,
 * global) para {@link OfficeRbacResponse} (id-keyed, tenant-scoped) -- {@code getRbac} passou a
 * ler {@code TenantRole} do tenant do chamador, nunca {@code Role} globais. Acrescenta uma prova
 * nova (Teste 6): um {@code TenantRole} com proveniência do molde {@code PLATAFORMA_ADMIN} nunca
 * aparece em {@code papeis}, mesmo que o seu nome não seja literalmente "PLATAFORMA_ADMIN".
 *
 * <p>Este ficheiro é complementar -- não substituto -- de {@link AdminControllerRbacAutorizacaoTest}
 * (que prova o gate de autorização de {@code getRbac}/{@code updateRbac} via proxy AOP real de
 * method security) e de {@link AdminControllerPlataformaAdminContencaoTest} (que prova as guardas
 * de contenção do papel {@code PLATAFORMA_ADMIN} nos quatro handlers de {@link AdminController}).
 * Aqui não se monta nenhum proxy: o alvo é o corpo do handler, não a anotação {@code @PreAuthorize}
 * que o precede -- essa já está provada nos outros dois ficheiros, que não são alterados.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerRbacCatalogoTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;
    @Mock private ResolucaoPapeisService resolucaoPapeisService;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoPrincipalDoTenant() {
        UserPrincipal principal = UserPrincipal.builder().userId(UUID.randomUUID()).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private AdminController novoController() {
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository, auditoriaRbacService);
    }

    private Permission permissao(String nome, String rotulo, String descricao, String modulo, Integer ordem) {
        return Permission.builder()
                .id(1)
                .nome(nome)
                .rotulo(rotulo)
                .descricao(descricao)
                .modulo(modulo)
                .ordem(ordem)
                .reservadaPlataforma(false)
                .build();
    }

    // Teste 1 + 3: origem dos dados vem exclusivamente do repositório (nunca de código Java), e o
    // handler nunca chama roleRepository.findAll() -- so o metodo derivado que exclui reservadas
    // ao nivel de SQL, e tenantRoleRepository.findByTenantId para os papeis do proprio escritorio.
    @Test
    void getRbac_permissoesVemExclusivamenteDoRepositorio() {
        autenticarComoPrincipalDoTenant();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        List<Permission> catalogo = List.of(
                permissao("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes", 10),
                permissao("clientes:edit", "Gerir Clientes", "Criar, editar e apagar clientes", "Clientes", 20),
                permissao("agenda:view", "Visualizar Agenda", "Ver calendário e prazos/eventos", "Agenda", 70));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(catalogo);

        ResponseEntity<?> response = novoController().getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        assertEquals(3, body.getPermissoes().size());
        verify(roleRepository, never()).findAll();
    }

    // Teste 2: mapeamento de campos -- key <- Permission.nome (chave técnica), nome <-
    // Permission.rotulo (rótulo legível). A troca dos dois é invisível ao compilador.
    @Test
    void getRbac_mapeiaKeyDaChaveTecnicaENomeDoRotulo() {
        autenticarComoPrincipalDoTenant();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of(
                permissao("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes", 10)));

        ResponseEntity<?> response = novoController().getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        OfficeRbacResponse.PermissaoDefDto dto = body.getPermissoes().get(0);
        assertEquals("clientes:view", dto.getKey());
        assertEquals("Visualizar Clientes", dto.getNome());
        assertEquals("Ver lista e detalhes de clientes", dto.getDescricao());
        assertEquals("Clientes", dto.getModulo());
    }

    // Teste 4: uma permissão sem rótulo (null ou em branco) nunca chega ao catálogo servido.
    @Test
    void getRbac_permissaoSemRotuloNuncaApareceNoCatalogo() {
        autenticarComoPrincipalDoTenant();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        List<Permission> catalogo = List.of(
                permissao("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes", 10),
                permissao("sem:rotulo", null, "descricao qualquer", "Clientes", 20),
                permissao("rotulo:em-branco", "   ", "descricao qualquer", "Clientes", 30));

        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(catalogo);

        ResponseEntity<?> response = novoController().getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        assertEquals(1, body.getPermissoes().size());
        assertEquals("clientes:view", body.getPermissoes().get(0).getKey());
    }

    // Teste 5: ordem estável por "ordem" (30, 10, 20 em ordem de chegada arbitrária -> 10, 20, 30),
    // e uma entrada com ordem nula sai em último lugar, sem exceção.
    @Test
    void getRbac_ordenaPorOrdemComNullPorUltimo() {
        autenticarComoPrincipalDoTenant();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        List<Permission> catalogo = List.of(
                permissao("financeiro:manage", "Eliminar Lançamentos Financeiros", "desc", "Financeiro", 30),
                permissao("clientes:view", "Visualizar Clientes", "desc", "Clientes", 10),
                permissao("clientes:edit", "Gerir Clientes", "desc", "Clientes", 20),
                permissao("sem:ordem", "Sem Ordem", "desc", "Administração", null));

        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(catalogo);

        ResponseEntity<?> response = novoController().getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        List<String> chaves = body.getPermissoes().stream()
                .map(OfficeRbacResponse.PermissaoDefDto::getKey)
                .toList();
        assertEquals(List.of("clientes:view", "clientes:edit", "financeiro:manage", "sem:ordem"), chaves);
    }

    // Teste 6 (Phase 127, PAPEL-09 -- substitui o antigo teste de rolePermissions): um TenantRole
    // cujo moldeId aponta para o Role global PLATAFORMA_ADMIN nunca aparece em papeis, mesmo que o
    // seu nome não seja literalmente "PLATAFORMA_ADMIN" -- a proveniência é o discriminador, não o
    // nome, exatamente como o doc-comment de OfficeRbacResponse.PapelDto exige.
    @Test
    void getRbac_papeisExcluiTenantRoleComProvenienciaDePlataformaAdmin() {
        autenticarComoPrincipalDoTenant();
        Role plataformaAdmin = Role.builder().id(99).nome("PLATAFORMA_ADMIN").build();
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.of(plataformaAdmin));
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());

        TenantRole advogado = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO")
                .permissions(Set.of(permissao("clientes:view", "Visualizar Clientes", "desc", "Clientes", 10)))
                .build();
        TenantRole renomeadoDePlataforma = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel Renomeado")
                .moldeId(99)
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(advogado, renomeadoDePlataforma));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());

        ResponseEntity<?> response = novoController().getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        List<String> nomes = body.getPapeis().stream().map(OfficeRbacResponse.PapelDto::getNome).toList();
        assertFalse(nomes.contains("Papel Renomeado"));
        assertTrue(nomes.contains("ADVOGADO"));
        assertEquals(List.of("clientes:view"), body.getPapeis().get(0).getPermissoes());
    }
}
