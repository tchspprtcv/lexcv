package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.RbacResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 119 (Plan 03): prova as quatro guardas de contencao do papel {@code PLATAFORMA_ADMIN} em
 * {@link AdminController} -- createUser, updateUser, getRbac e updateRbac.
 *
 * <p>Sem estas guardas, a partir do momento em que o Plan 01 seeda o papel {@code PLATAFORMA_ADMIN}
 * (incondicionalmente, em todo o arranque), um {@code ADMIN} de um escritorio normal poderia:
 * (a) atribuir esse papel a um utilizador novo via {@code POST /api/v1/admin/users}, ou
 * (b) promover um utilizador ja existente (incluindo a propria conta) via
 * {@code PUT /api/v1/admin/users/{id}}. {@link UserPrincipal#create} deriva autoridades
 * {@code ROLE_*} genericamente a partir de qualquer papel guardado na base de dados, sem
 * allowlist -- por isso, uma vez atribuido, o papel satisfaz de imediato o
 * {@code @PreAuthorize("hasRole('PLATAFORMA_ADMIN')")} do {@code PlatformAdminController} (Plan 04),
 * dando a esse {@code ADMIN} de escritorio acesso a {@code POST /api/v1/platform/tenants} --
 * criacao arbitraria de tenants por um cliente qualquer. Sem esta contencao, o Success Criterion 4
 * da Phase 119 seria falso na pratica, apesar de o gate do Plan 04 estar correto isoladamente.
 *
 * <p>Os Casos 3, 5 e 8 provam, em paralelo, que os quatro papeis de tenant (ADMIN, ADVOGADO,
 * TECNICO, ASSISTENTE) continuam geriveis exatamente como antes -- estas guardas sao recusas de um
 * unico nome de papel, nunca allowlists positivas que pudessem estreitar os caminhos legitimos.
 *
 * <p>Segue a mesma convencao de todos os testes de controller deste codebase (ver
 * {@link AdminControllerLimiteUtilizadoresTest}): nao existe harness MockMvc/{@code @SpringBootTest}
 * neste projeto -- o controller e instanciado diretamente com colaboradores mockados via Mockito,
 * o metodo sob teste e invocado como uma chamada Java simples, e o {@code SecurityContextHolder} e
 * povoado manualmente com um {@link UserPrincipal} do tenant do caso e limpo em {@code @AfterEach}.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerPlataformaAdminContencaoTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "novo@lexcv.cv";
    private static final String PASSWORD = "Pa$$w0rd";

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoPrincipalDoTenant() {
        UserPrincipal principal = UserPrincipal.builder().userId(USER_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private AdminController novoController() {
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder, tenantRepository);
    }

    private Map<String, Object> corpoCriacaoComRoles(List<String> roles) {
        return Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "roles", roles
        );
    }

    private User utilizadorExistente() {
        return User.builder()
                .id(USER_ID)
                .tenantId(TENANT_ID)
                .nome("Utilizador Existente")
                .email("existente@lexcv.cv")
                .ativo(true)
                .build();
    }

    // Caso 1 — createUser recusa PLATAFORMA_ADMIN com 403, antes do lookup do papel.
    @Test
    void createUser_recusaPlataformaAdminCom403EAntesDoLookup() {
        autenticarComoPrincipalDoTenant();

        ResponseEntity<?> response = novoController().createUser(corpoCriacaoComRoles(List.of("PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.toLowerCase().contains("plataforma"));
        assertTrue(mensagem.toLowerCase().contains("reservado"));
        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).findByNome("PLATAFORMA_ADMIN");
    }

    // Caso 2 — createUser recusa PLATAFORMA_ADMIN mesmo misturado com um papel legitimo (nao basta
    // inspecionar so o primeiro elemento da lista).
    @Test
    void createUser_recusaPlataformaAdminMesmoMisturadoComPapelLegitimo() {
        autenticarComoPrincipalDoTenant();

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComRoles(List.of("ADVOGADO", "PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 3 — nao-regressao: createUser continua a funcionar para papeis de tenant.
    @Test
    void createUser_continuaAFuncionarParaPapeisDeTenant() {
        autenticarComoPrincipalDoTenant();
        when(roleRepository.findByNome("ADVOGADO"))
                .thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).build()));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().createUser(corpoCriacaoComRoles(List.of("ADVOGADO")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
    }

    // Caso 4 — updateUser recusa PLATAFORMA_ADMIN com 403 e nao altera os papeis (segundo caminho de
    // escalada: promover uma conta ja existente, incluindo a propria).
    @Test
    void updateUser_recusaPlataformaAdminCom403ENaoAlteraPapeis() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(utilizadorExistente()));

        ResponseEntity<?> response = novoController()
                .updateUser(USER_ID, Map.of("roles", List.of("PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 5 — nao-regressao: updateUser continua a permitir mudar entre papeis de tenant.
    @Test
    void updateUser_continuaAPermitirMudarEntrePapeisDeTenant() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(utilizadorExistente()));
        when(roleRepository.findByNome("TECNICO")).thenReturn(Optional.of(Role.builder().id(2).nome("TECNICO").build()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(USER_ID, Map.of("roles", List.of("TECNICO")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
    }

    // Caso 6 — getRbac nao expoe o papel de plataforma: roleRepository.findAll() devolve os quatro
    // papeis de tenant mais um Role "PLATAFORMA_ADMIN"; o mapa devolvido tem de o excluir.
    @Test
    void getRbac_naoExpoePapelDePlataforma() {
        Role admin = Role.builder().id(1).nome("ADMIN").build();
        Role advogado = Role.builder().id(2).nome("ADVOGADO").build();
        Role tecnico = Role.builder().id(3).nome("TECNICO").build();
        Role assistente = Role.builder().id(4).nome("ASSISTENTE").build();
        Role plataformaAdmin = Role.builder().id(5).nome("PLATAFORMA_ADMIN").build();
        when(roleRepository.findAll()).thenReturn(List.of(admin, advogado, tecnico, assistente, plataformaAdmin));

        ResponseEntity<?> response = novoController().getRbac();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RbacResponse body = (RbacResponse) response.getBody();
        assertEquals(4, body.getRolePermissions().size());
        assertFalse(body.getRolePermissions().containsKey("PLATAFORMA_ADMIN"));
    }

    // Caso 7 — updateRbac ignora uma entrada PLATAFORMA_ADMIN: o endpoint continua a responder
    // normalmente, mas as permissoes do papel reservado nunca sao tocadas.
    @Test
    void updateRbac_ignoraEntradaPlataformaAdmin() {
        Map<String, Object> body = Map.of(
                "rolePermissions", Map.of("PLATAFORMA_ADMIN", List.of("clientes:view", "users:manage"))
        );

        ResponseEntity<?> response = novoController().updateRbac(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(roleRepository, never()).save(any());
        verify(roleRepository, never()).findByNome("PLATAFORMA_ADMIN");
    }

    // Caso 8 — nao-regressao: updateRbac continua a editar papeis de tenant.
    @Test
    void updateRbac_continuaAEditarPapeisDeTenant() {
        when(roleRepository.findByNome("ASSISTENTE"))
                .thenReturn(Optional.of(Role.builder().id(4).nome("ASSISTENTE").build()));
        when(permissionRepository.findByNome("clientes:view"))
                .thenReturn(Optional.of(Permission.builder().id(1).nome("clientes:view").build()));

        Map<String, Object> body = Map.of(
                "rolePermissions", Map.of("ASSISTENTE", List.of("clientes:view"))
        );

        ResponseEntity<?> response = novoController().updateRbac(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(roleRepository, times(1)).save(any());
    }
}
