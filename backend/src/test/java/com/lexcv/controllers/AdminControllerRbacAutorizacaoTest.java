package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.OfficeRbacUpdateRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova comportamental, por proxy real de method security (nunca por reflexão sobre a anotação
 * para os casos comportamentais), de que {@link AdminController} está gateado por AUTORIDADE de
 * permissão (Phase 127, Planos 02/03, PAPEL-08/PAPEL-09) e não pelo nome literal do papel ADMIN --
 * `users:manage` no gate de classe, `rbac:manage` em {@code getRbac} e {@code updateRbac}.
 *
 * <p>Isto substitui a prova anterior (Phase 121, ISOL-03/CR-01), cuja premissa era
 * {@code hasRole('ADMIN')} de classe, {@code hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')} em
 * {@code getRbac} e {@code hasRole('PLATAFORMA_ADMIN')} em {@code updateRbac} -- nenhuma continua
 * verdadeira depois deste plano (127-CONTEXT.md Decisão 1). O gate de {@code updateRbac} foi o
 * último a inverter: o plano 02 deixou-o deliberadamente por mexer até o plano 03 reescrever o
 * seu corpo para tenant-scoped na MESMA alteração -- ver o comentário acima do handler.
 *
 * <p>{@link AdminController} continua a ser a única classe deste codebase a combinar uma
 * anotação de classe com uma anotação de método mais específica na mesma classe -- por isso a
 * semântica "a mais específica ganha" do Spring Security continua provada aqui
 * comportamentalmente, com um proxy CGLIB real
 * ({@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()}), nunca assumida por
 * analogia com outro controller.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerRbacAutorizacaoTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;
    @Mock private ResolucaoPapeisService resolucaoPapeisService;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AdminController novoController() {
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository, auditoriaRbacService);
    }

    /**
     * Monta o proxy AOP de method security necessario para avaliar {@code @PreAuthorize} de facto
     * -- uma chamada Java direta a {@link #novoController()} nunca o faz. Copia exata do padrao
     * estabelecido em {@code PlatformAdminControllerTest.novoProxyComMethodSecurity()} (Phase 119),
     * so mudando o tipo de retorno para {@link AdminController}.
     */
    private AdminController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (AdminController) factory.getProxy();
    }

    /**
     * Autentica com uma lista crua de autoridades, sem {@link UserPrincipal} (o principal fica
     * {@code null}). Correto para {@code getRbac}/{@code updateRbac}, cujos corpos nunca leem
     * {@code SecurityContextHolder} -- mas NUNCA para {@code listUsers}, que desembrulha
     * {@code (UserPrincipal) auth.getPrincipal()} e chamaria {@code getTenantId()} sobre
     * {@code null}; para esse handler usar {@link #autenticarComoPrincipalComAuthorities}.
     *
     * <p>As autoridades passadas aqui são usadas EXATAMENTE como escritas -- nunca prefixadas
     * automaticamente com {@code "ROLE_"}. Isto é deliberado: alguns testes abaixo (a armadilha
     * {@code hasAuthority} vs {@code hasRole} de 127-CONTEXT.md Decisão 3) precisam de distinguir
     * a forma crua {@code "rbac:manage"} da forma prefixada {@code "ROLE_rbac:manage"}, e só o
     * chamador decide qual passar.
     */
    private void autenticarComoAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
    }

    /**
     * Autentica com um {@link UserPrincipal} real (não apenas uma lista de autoridades) -- exigido
     * por qualquer handler que leia {@code SecurityContextHolder} no seu corpo, como
     * {@code listUsers} (chama {@code principal.getTenantId()}). Construído diretamente via
     * {@code UserPrincipal.builder()}, nunca via {@code UserPrincipal.create}: este teste precisa
     * de controlar as autoridades cruas exatas do principal (incluindo formas que
     * {@code UserPrincipal.create} nunca produziria, como um papel renomeado com espaço), não a
     * derivação normal a partir de "roles"/"permissions".
     */
    private void autenticarComoPrincipalComAuthorities(UUID tenantId, String... authorities) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(tenantId)
                .nome("Principal de Teste")
                .email("principal@escritorio-teste.cv")
                .roles(Set.of())
                .permissions(Set.of())
                .authorities(autoridades)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, autoridades));
    }

    // ---------------------------------------------------------------------------------------
    // getRbac: hasAuthority('rbac:manage') -- Cenário 1 (127-02-PLAN.md)
    // ---------------------------------------------------------------------------------------

    // Cenário 1: a autoridade crua (não prefixada) "rbac:manage" satisfaz o novo gate.
    // Phase 127 (Plano 03): getRbac passou a tenant-scoped -- precisa de um UserPrincipal real
    // (não apenas uma lista de autoridades) e stub de tenantRoleRepository.findByTenantId, nunca
    // mais roleRepository.findAll().
    @Test
    void getRbac_comAutoridadeRbacManageCruaObtemSucesso() {
        UUID tenantId = UUID.randomUUID();
        autenticarComoPrincipalComAuthorities(tenantId, "rbac:manage");
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.getRbac());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Cenário 2: ROLE_ADMIN sozinho já não chega -- prova que o gate deixou de ser um hasRole.
    @Test
    void getRbac_comApenasRoleAdminERecusado() {
        autenticarComoAuthorities("ROLE_ADMIN");
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.getRbac());
        verify(tenantRoleRepository, never()).findByTenantId(any());
    }

    // Cenário 3: ROLE_PLATAFORMA_ADMIN sozinho já não chega -- a leitura de plataforma sobre esta
    // superfície de escritório foi retirada (PAPEL-09).
    @Test
    void getRbac_comApenasRolePlataformaAdminERecusado() {
        autenticarComoAuthorities("ROLE_PLATAFORMA_ADMIN");
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.getRbac());
        verify(tenantRoleRepository, never()).findByTenantId(any());
    }

    // Cenário 4: a armadilha hasAuthority-vs-hasRole (127-CONTEXT.md Decisão 3) fixada em teste --
    // "ROLE_rbac:manage" é a forma ERRADA (o que hasRole('rbac:manage') procuraria); com o gate
    // correto (hasAuthority), esta forma tem de continuar recusada. Se uma edição futura trocar
    // hasAuthority por hasRole aqui, este teste passa a falhar em vez de 403 silenciosamente todos
    // os chamadores reais.
    @Test
    void getRbac_comAutoridadePrefixadaRoleRbacManageERecusado() {
        autenticarComoAuthorities("ROLE_rbac:manage");
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.getRbac());
        verify(tenantRoleRepository, never()).findByTenantId(any());
    }

    // ---------------------------------------------------------------------------------------
    // listUsers: hasAuthority('users:manage') via o gate de CLASSE -- Cenários 5 e 6a
    // ---------------------------------------------------------------------------------------

    // Cenário 5: a regressão que esta tarefa existe para prevenir -- um chamador cuja única
    // autoridade em forma de papel é "ROLE_Administrador do Escritório" (um papel de escritório
    // RENOMEADO, já não "ADMIN") continua a alcançar a gestão de utilizadores, porque detém
    // "users:manage" diretamente.
    @Test
    void listUsers_comAutoridadeUsersManageEPapelDeEscritorioRenomeadoObtemSucesso() {
        UUID tenantId = UUID.randomUUID();
        autenticarComoPrincipalComAuthorities(tenantId, "users:manage", "ROLE_Administrador do Escritório");
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(proxy::listUsers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Cenário 6a: as duas autoridades não são intercambiáveis -- rbac:manage sozinho não abre a
    // gestão de utilizadores (o gate de classe).
    @Test
    void listUsers_comApenasRbacManageERecusado() {
        autenticarComoAuthorities("rbac:manage");
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, proxy::listUsers);
        verify(userRepository, never()).findByTenantId(any());
    }

    // Cenário 6b (o inverso): users:manage sozinho não abre a matriz RBAC.
    @Test
    void getRbac_comApenasUsersManageERecusado() {
        autenticarComoAuthorities("users:manage");
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.getRbac());
        verify(tenantRoleRepository, never()).findByTenantId(any());
    }

    // ---------------------------------------------------------------------------------------
    // updateRbac: hasAuthority('rbac:manage') -- Phase 127 (Plano 03): gate E corpo mudados na
    // MESMA alteração (127-CONTEXT.md Decisão 1) -- ver o comentário acima de updateRbac em
    // AdminController. O corpo agora é tenant-scoped e lê SecurityContextHolder, por isso os
    // cenários de sucesso abaixo precisam de um UserPrincipal real, não apenas autoridades cruas.
    // ---------------------------------------------------------------------------------------

    // Cenário 7: a autoridade crua "rbac:manage" satisfaz o gate e o pedido é processado.
    @Test
    void updateRbac_comAutoridadeRbacManageCruaObtemSucesso() {
        UUID tenantId = UUID.randomUUID();
        autenticarComoPrincipalComAuthorities(tenantId, "rbac:manage");
        when(tenantRoleRepository.findByTenantId(tenantId)).thenReturn(List.of());
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.updateRbac(request));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Cenário 8: ROLE_ADMIN sozinho já não chega -- prova que o gate deixou de ser um hasRole.
    @Test
    void updateRbac_comApenasRoleAdminERecusado() {
        autenticarComoAuthorities("ROLE_ADMIN");
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.updateRbac(request));
        verify(tenantRoleRepository, never()).save(any());
    }

    // Cenário 9: ROLE_PLATAFORMA_ADMIN sozinho já não chega -- a Fase 127 retira por completo o
    // acesso de escrita de plataforma sobre esta superfície de escritório (127-CONTEXT.md Decisão
    // 1/PAPEL-09); PLATAFORMA_ADMIN não detém nenhuma permissão
    // (DatabaseSeeder.upsertRolePermissions("PLATAFORMA_ADMIN", emptyList, false)), por isso nunca
    // poderia satisfazer hasAuthority('rbac:manage') de qualquer forma. Isto é a INVERSÃO
    // deliberada do comportamento anterior (Fase 121: `hasRole('PLATAFORMA_ADMIN')` passava) --
    // ler como intencional, não como regressão.
    @Test
    void updateRbac_comApenasRolePlataformaAdminERecusado() {
        autenticarComoAuthorities("ROLE_PLATAFORMA_ADMIN");
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.updateRbac(request));
        verify(tenantRoleRepository, never()).save(any());
    }

    // Cenário 10: a armadilha hasAuthority-vs-hasRole (127-CONTEXT.md Decisão 3), espelhando o
    // Cenário 4 de getRbac -- "ROLE_rbac:manage" é a forma ERRADA e tem de continuar recusada.
    @Test
    void updateRbac_comAutoridadePrefixadaRoleRbacManageERecusado() {
        autenticarComoAuthorities("ROLE_rbac:manage");
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.updateRbac(request));
        verify(tenantRoleRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------------
    // Não-regressão por reflexão -- valor exato das anotações. Complementar às provas
    // comportamentais acima, nunca substituto: reflexão sozinha nunca provaria que o proxy de
    // method security realmente aplica o valor declarado.
    // ---------------------------------------------------------------------------------------

    @Test
    void updateRbac_temAnotacaoDeMetodoComValorExatoHasAuthorityRbacManage() throws NoSuchMethodException {
        Method metodo = AdminController.class.getMethod("updateRbac", OfficeRbacUpdateRequest.class);
        PreAuthorize anotacao = metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(anotacao);
        assertEquals("hasAuthority('rbac:manage')", anotacao.value());
    }

    @Test
    void getRbac_temAnotacaoDeMetodoComValorExatoHasAuthorityRbacManage() throws NoSuchMethodException {
        Method metodo = AdminController.class.getMethod("getRbac");
        PreAuthorize anotacao = metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(anotacao);
        assertEquals("hasAuthority('rbac:manage')", anotacao.value());
    }

    @Test
    void anotacaoDeClasseAgoraHasAuthorityUsersManage() {
        PreAuthorize anotacao = AdminController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(anotacao);
        assertEquals("hasAuthority('users:manage')", anotacao.value());
    }
}
