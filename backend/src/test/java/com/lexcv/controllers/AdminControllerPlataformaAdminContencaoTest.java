package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.OfficeRbacResponse;
import com.lexcv.dtos.OfficeRbacUpdateRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.ResolucaoPapeisService;
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
import java.util.Set;
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
 * <p>CR-01 (119-REVIEW.md): os Casos 9-14 provam a correcao do bypass encontrado na revisao de
 * codigo desta fase -- as guardas originais (Casos 1/2/4 acima) so inspecionavam "roles", nunca o
 * campo irmao "permissions", que {@link UserPrincipal#create} tambem vira em GrantedAuthority mas
 * sem qualquer prefixagem "ROLE_" propria da app. Isso permitia a um ADMIN de escritorio colocar
 * literalmente {@code "ROLE_PLATAFORMA_ADMIN"} em {@code permissions} (proprio ou de outro
 * utilizador) e satisfazer o {@code hasRole('PLATAFORMA_ADMIN')} do {@code PlatformAdminController}
 * na proxima requisicao, sem nunca tocar em "roles". Os Casos 9-14 mirror exatamente os Casos
 * 1/2/4/5 mas para "permissions".
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
    @Mock private ResolucaoPapeisService resolucaoPapeisService;
    @Mock private TenantRoleRepository tenantRoleRepository;

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
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository);
    }

    private Map<String, Object> corpoCriacaoComRoles(List<String> roles) {
        return Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "roles", roles
        );
    }

    // CR-01 (119-REVIEW.md): "roles" continua obrigatorio (ver a validacao de entrada de
    // createUser) mesmo nos casos que testam exclusivamente "permissions" -- usa-se sempre um
    // papel de tenant legitimo aqui para que a guarda de "roles" nunca seja o motivo do 403.
    private Map<String, Object> corpoCriacaoComPermissions(List<String> permissions) {
        return Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "roles", List.of("ADVOGADO"),
                "permissions", permissions
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
        // Phase 126 (Plan 04): updateUser tambem povoa tenantRoles quando roles nao e vazio --
        // ver AdminControllerAtribuicaoPapeisEscritorioTest para a prova comportamental completa
        // desse caminho de escrita; aqui so precisa de nao rebentar com NPE.
        when(resolucaoPapeisService.resolverPapeisDeEscritorio(any(), any())).thenReturn(Set.of());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(USER_ID, Map.of("roles", List.of("TECNICO")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
    }

    // Caso 6 — getRbac nao expoe o papel de plataforma: Phase 127 (Plano 03) tornou este handler
    // tenant-scoped -- tenantRoleRepository.findByTenantId devolve os quatro papeis de tenant mais
    // um TenantRole "PLATAFORMA_ADMIN"; a lista devolvida (agora OfficeRbacResponse.papeis, nao
    // mais o RbacResponse.rolePermissions nome-keyed) tem de o excluir.
    @Test
    void getRbac_naoExpoePapelDePlataforma() {
        autenticarComoPrincipalDoTenant();
        TenantRole admin = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADMIN").build();
        TenantRole advogado = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO").build();
        TenantRole tecnico = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("TECNICO").build();
        TenantRole assistente = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ASSISTENTE").build();
        TenantRole plataformaAdmin = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("PLATAFORMA_ADMIN").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(admin, advogado, tecnico, assistente, plataformaAdmin));

        ResponseEntity<?> response = novoController().getRbac();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        assertEquals(4, body.getPapeis().size());
        assertFalse(body.getPapeis().stream().anyMatch(p -> "PLATAFORMA_ADMIN".equals(p.getNome())));
    }

    // Caso 7 — updateRbac recusa com 403 uma entrada PLATAFORMA_ADMIN: Phase 127 (Plano 03)
    // trocou o "continue" silencioso original por uma recusa explícita antes de qualquer escrita
    // (ver o comentário do handler) -- forma mais forte da mesma contenção que este ficheiro
    // existe para provar, nunca uma regressão dela. Convertido para OfficeRbacUpdateRequest
    // (id-keyed) na MESMA alteração que mudou o gate e o corpo do handler, per 127-CONTEXT.md
    // Decisão 1.
    @Test
    void updateRbac_recusaEntradaPlataformaAdminCom403() {
        autenticarComoPrincipalDoTenant();
        TenantRole plataformaAdmin = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("PLATAFORMA_ADMIN").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(plataformaAdmin));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(plataformaAdmin.getId());
        entrada.setPermissoes(List.of("clientes:view", "users:manage"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController().updateRbac(request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(tenantRoleRepository, never()).save(any());
    }

    // Caso 8 — nao-regressao: updateRbac continua a editar papeis proprios do escritorio
    // (id-keyed desde Phase 127 Plano 03, nunca mais nome-keyed sobre Role global).
    @Test
    void updateRbac_continuaAEditarPapeisDeTenant() {
        autenticarComoPrincipalDoTenant();
        TenantRole assistente = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ASSISTENTE").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(assistente));
        when(permissionRepository.findAllByReservadaPlataformaFalse())
                .thenReturn(List.of(Permission.builder().id(1).nome("clientes:view").build()));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(assistente.getId());
        entrada.setPermissoes(List.of("clientes:view"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController().updateRbac(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tenantRoleRepository, times(1)).save(any());
    }

    // Caso 9 — createUser recusa "permissions": ["ROLE_PLATAFORMA_ADMIN"] com 403 -- reproducao
    // exata do bypass do CR-01 (119-REVIEW.md): esta e a forma ja-prefixada que
    // UserPrincipal.create vira diretamente em GrantedAuthority "ROLE_PLATAFORMA_ADMIN",
    // satisfazendo hasRole('PLATAFORMA_ADMIN') sem nunca tocar em "roles".
    @Test
    void createUser_recusaPermissionRolePlataformaAdminCom403() {
        autenticarComoPrincipalDoTenant();
        // "roles": ["ADVOGADO"] tem de resolver para uma Role real -- senao createUser devolveria
        // 400 ("Pelo menos uma role válida é obrigatória") antes de sequer chegar a guarda de
        // "permissions" sob teste aqui. Ver corpoCriacaoComPermissions.
        when(roleRepository.findByNome("ADVOGADO"))
                .thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComPermissions(List.of("ROLE_PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.toLowerCase().contains("plataforma"));
        assertTrue(mensagem.toLowerCase().contains("reservado"));
        verify(userRepository, never()).save(any());
    }

    // Caso 10 — createUser recusa tambem a forma crua "PLATAFORMA_ADMIN" em "permissions", por
    // defesa em profundidade (mesmo nao bastando por si so para satisfazer hasRole(...) hoje).
    @Test
    void createUser_recusaPermissionPlataformaAdminCruaCom403() {
        autenticarComoPrincipalDoTenant();
        when(roleRepository.findByNome("ADVOGADO"))
                .thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComPermissions(List.of("PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 11 — createUser recusa "ROLE_PLATAFORMA_ADMIN" em "permissions" mesmo misturado com
    // uma permission legitima (nao basta inspecionar so o primeiro elemento da lista).
    @Test
    void createUser_recusaPermissionPlataformaAdminMesmoMisturadaComPermissionLegitima() {
        autenticarComoPrincipalDoTenant();
        when(roleRepository.findByNome("ADVOGADO"))
                .thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));

        ResponseEntity<?> response = novoController().createUser(
                corpoCriacaoComPermissions(List.of("clientes:view", "ROLE_PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 12 — nao-regressao: createUser continua a aceitar permissions livres legitimas.
    @Test
    void createUser_continuaAAceitarPermissionsLegitimas() {
        autenticarComoPrincipalDoTenant();
        when(roleRepository.findByNome("ADVOGADO"))
                .thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).build()));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComPermissions(List.of("clientes:view")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
    }

    // Caso 13 — updateUser recusa "permissions": ["ROLE_PLATAFORMA_ADMIN"] com 403 e nao altera o
    // utilizador -- reproducao exata do caminho de auto-escalada do CR-01 (PUT
    // /api/v1/admin/users/{ownUserId} com este corpo).
    @Test
    void updateUser_recusaPermissionRolePlataformaAdminCom403ENaoAlteraUtilizador() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(utilizadorExistente()));

        ResponseEntity<?> response = novoController()
                .updateUser(USER_ID, Map.of("permissions", List.of("ROLE_PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 14 — nao-regressao: updateUser continua a permitir alterar permissions legitimas.
    @Test
    void updateUser_continuaAPermitirAlterarPermissionsLegitimas() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(utilizadorExistente()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController()
                .updateUser(USER_ID, Map.of("permissions", List.of("clientes:view")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
    }
}
