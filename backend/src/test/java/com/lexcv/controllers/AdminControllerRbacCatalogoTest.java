package com.lexcv.controllers;

import com.lexcv.dtos.RbacResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.ResolucaoPapeisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 124 (Plan 02): prova que {@link AdminController#getRbac()} constrói
 * {@code systemPermissions} a partir das linhas devolvidas por
 * {@link PermissionRepository#findAllByReservadaPlataformaFalse()} -- não de uma lista Java
 * hardcoded (CATL-01) -- e que permissões reservadas à plataforma ou sem rótulo utilizável nunca
 * chegam a essa lista (CATL-03).
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

    private AdminController novoController() {
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder, tenantRepository, resolucaoPapeisService);
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
    // handler nunca chama findAll() -- só o método derivado que exclui reservadas ao nível de SQL.
    @Test
    void getRbac_systemPermissionsVemExclusivamenteDoRepositorio() {
        lenient().when(roleRepository.findAll()).thenReturn(List.of());
        List<Permission> catalogo = List.of(
                permissao("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes", 10),
                permissao("clientes:edit", "Gerir Clientes", "Criar, editar e apagar clientes", "Clientes", 20),
                permissao("agenda:view", "Visualizar Agenda", "Ver calendário e prazos/eventos", "Agenda", 70));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(catalogo);

        ResponseEntity<?> response = novoController().getRbac();

        RbacResponse body = (RbacResponse) response.getBody();
        assertEquals(3, body.getSystemPermissions().size());
        verify(permissionRepository, never()).findAll();
    }

    // Teste 2: mapeamento de campos -- key <- Permission.nome (chave técnica), nome <-
    // Permission.rotulo (rótulo legível). A troca dos dois é invisível ao compilador.
    @Test
    void getRbac_mapeiaKeyDaChaveTecnicaENomeDoRotulo() {
        lenient().when(roleRepository.findAll()).thenReturn(List.of());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of(
                permissao("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes", 10)));

        ResponseEntity<?> response = novoController().getRbac();

        RbacResponse body = (RbacResponse) response.getBody();
        RbacResponse.PermissionDefDto dto = body.getSystemPermissions().get(0);
        assertEquals("clientes:view", dto.getKey());
        assertEquals("Visualizar Clientes", dto.getNome());
        assertEquals("Ver lista e detalhes de clientes", dto.getDescricao());
        assertEquals("Clientes", dto.getModulo());
    }

    // Teste 4: uma permissão sem rótulo (null ou em branco) nunca chega a systemPermissions.
    @Test
    void getRbac_permissaoSemRotuloNuncaApareceEmSystemPermissions() {
        lenient().when(roleRepository.findAll()).thenReturn(List.of());
        List<Permission> catalogo = List.of(
                permissao("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes", 10),
                permissao("sem:rotulo", null, "descricao qualquer", "Clientes", 20),
                permissao("rotulo:em-branco", "   ", "descricao qualquer", "Clientes", 30));

        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(catalogo);

        ResponseEntity<?> response = novoController().getRbac();

        RbacResponse body = (RbacResponse) response.getBody();
        assertEquals(1, body.getSystemPermissions().size());
        assertEquals("clientes:view", body.getSystemPermissions().get(0).getKey());
    }

    // Teste 5: ordem estável por "ordem" (30, 10, 20 em ordem de chegada arbitrária -> 10, 20, 30),
    // e uma entrada com ordem nula sai em último lugar, sem exceção.
    @Test
    void getRbac_ordenaPorOrdemComNullPorUltimo() {
        lenient().when(roleRepository.findAll()).thenReturn(List.of());
        List<Permission> catalogo = List.of(
                permissao("financeiro:manage", "Eliminar Lançamentos Financeiros", "desc", "Financeiro", 30),
                permissao("clientes:view", "Visualizar Clientes", "desc", "Clientes", 10),
                permissao("clientes:edit", "Gerir Clientes", "desc", "Clientes", 20),
                permissao("sem:ordem", "Sem Ordem", "desc", "Administração", null));

        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(catalogo);

        ResponseEntity<?> response = novoController().getRbac();

        RbacResponse body = (RbacResponse) response.getBody();
        List<String> chaves = body.getSystemPermissions().stream()
                .map(RbacResponse.PermissionDefDto::getKey)
                .toList();
        assertEquals(List.of("clientes:view", "clientes:edit", "financeiro:manage", "sem:ordem"), chaves);
    }

    // Teste 6: o laço de papéis não regrediu -- PLATAFORMA_ADMIN continua excluído de
    // rolePermissions, e as chaves técnicas de cada papel continuam a sair corretamente.
    @Test
    void getRbac_rolePermissionsContinuaAExcluirPlataformaAdminEADevolverAsChaves() {
        Role advogado = Role.builder().id(2).nome("ADVOGADO")
                .permissions(new java.util.HashSet<>(List.of(
                        permissao("clientes:view", "Visualizar Clientes", "desc", "Clientes", 10))))
                .build();
        Role plataformaAdmin = Role.builder().id(99).nome("PLATAFORMA_ADMIN")
                .permissions(new java.util.HashSet<>()).build();
        when(roleRepository.findAll()).thenReturn(List.of(advogado, plataformaAdmin));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());

        ResponseEntity<?> response = novoController().getRbac();

        RbacResponse body = (RbacResponse) response.getBody();
        assertFalse(body.getRolePermissions().containsKey("PLATAFORMA_ADMIN"));
        assertTrue(body.getRolePermissions().containsKey("ADVOGADO"));
        assertEquals(List.of("clientes:view"), body.getRolePermissions().get("ADVOGADO"));
    }
}
