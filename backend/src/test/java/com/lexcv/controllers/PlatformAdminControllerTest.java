package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.dtos.TenantProvisionResponse;
import com.lexcv.models.Tenant;
import com.lexcv.services.SetupService;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova o comportamento de {@link PlatformAdminController} em dois grupos.
 *
 * <p>Grupo A (Casos 1-4, instanciação direta, sem proxy) segue a mesma convenção de todos os
 * testes de controller deste codebase (ver {@code AdminControllerLimiteUtilizadoresTest}): não
 * existe harness MockMvc/{@code @SpringBootTest} neste projeto -- o controller é instanciado
 * diretamente com {@link SetupService} mockado via Mockito, e o método sob teste é invocado como
 * uma chamada Java simples.
 *
 * <p>Grupo B (Casos 5, 6 e 8) precisa de algo mais: uma chamada Java direta ao controller NUNCA
 * avalia {@code @PreAuthorize} -- essa anotação só é interpretada por um proxy AOP de method
 * security. Sem esse proxy, o Success Criterion 4 da Phase 119 ("uma recusa de autorização
 * chega ao cliente como 403") não teria nenhuma prova comportamental, apenas uma leitura por
 * reflexão da string da anotação (o que o Caso 7 já cobre à parte, sem envolver o proxy). Por
 * isso estes três casos envolvem o controller num {@link ProxyFactory} CGLIB
 * ({@code setProxyTargetClass(true)}, obrigatório porque {@code PlatformAdminController} não
 * implementa nenhuma interface) montado com o mesmo
 * {@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()} que o Spring Security usa
 * em produção via {@code @EnableMethodSecurity}, e povoam o {@link SecurityContextHolder}
 * manualmente antes de cada chamada, limpando-o em {@code @AfterEach} (como em
 * {@code AdminControllerLimiteUtilizadoresTest}). Isto continua a não introduzir nenhum harness
 * MockMvc/{@code @SpringBootTest} neste projeto.
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminControllerTest {

    @Mock
    private SetupService setupService;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private SetupInitializeRequest pedidoValido() {
        SetupInitializeRequest request = new SetupInitializeRequest();
        request.setClientName("Escritorio Novo");
        request.setAdminEmail("admin@escritorionovo.cv");
        request.setAdminPassword("Pa$$w0rd1");
        return request;
    }

    private PlatformAdminController novoController() {
        return new PlatformAdminController(setupService);
    }

    /**
     * Monta o proxy AOP de method security necessário para o Grupo B -- ver o Javadoc de
     * classe acima para a justificação completa.
     */
    private PlatformAdminController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (PlatformAdminController) factory.getProxy();
    }

    private void autenticarComoRoles(String... roles) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
    }

    private void autenticarComoPlataformaAdmin(UUID tenantIdReservado) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(tenantIdReservado)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATAFORMA_ADMIN"))));
    }

    // ---- Grupo A: comportamento do handler (instanciação direta, sem proxy) ----

    @Test
    void createTenant_devolve201ComIdENomeSemEntidadeCrua() {
        SetupInitializeRequest request = pedidoValido();
        UUID tenantId = UUID.randomUUID();
        Tenant tenantCriado = Tenant.builder().id(tenantId).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(request)).thenReturn(tenantCriado);

        ResponseEntity<?> response = novoController().createTenant(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertInstanceOf(TenantProvisionResponse.class, response.getBody());
        assertFalse(response.getBody() instanceof Tenant);
        TenantProvisionResponse body = (TenantProvisionResponse) response.getBody();
        assertEquals(tenantId, body.getId());
        assertEquals("Escritorio Novo", body.getNome());
    }

    @Test
    void createTenant_comIllegalArgumentExceptionDevolve400() {
        SetupInitializeRequest request = pedidoValido();
        when(setupService.provisionTenant(request))
                .thenThrow(new IllegalArgumentException("O email do administrador é inválido."));

        ResponseEntity<?> response = novoController().createTenant(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "O email do administrador é inválido."), response.getBody());
    }

    @Test
    void createTenant_comIllegalStateExceptionDevolve403() {
        SetupInitializeRequest request = pedidoValido();
        when(setupService.provisionTenant(request))
                .thenThrow(new IllegalStateException("O papel ADMIN não está configurado."));

        ResponseEntity<?> response = novoController().createTenant(request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of("message", "O papel ADMIN não está configurado."), response.getBody());
    }

    @Test
    void createTenant_delegaEmProvisionTenantNuncaEmInitializeSystemOuIsInitialized() {
        SetupInitializeRequest request = pedidoValido();
        Tenant tenantCriado = Tenant.builder().id(UUID.randomUUID()).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(request)).thenReturn(tenantCriado);

        novoController().createTenant(request);

        verify(setupService, times(1)).provisionTenant(any());
        verify(setupService, never()).initializeSystem(any());
        verify(setupService, never()).isInitialized();
    }

    // ---- Grupo B: gate de autorização (proxy real de method security) ----

    @Test
    void createTenant_comRoleAdminDeTenantNormalERecusadoAntesDeOMetodoCorrer() {
        autenticarComoRoles("ADMIN");
        SetupInitializeRequest request = pedidoValido();
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.createTenant(request));
        verify(setupService, never()).provisionTenant(any());
    }

    @Test
    void createTenant_comRolePlataformaAdminPassaOGateEDevolve201() {
        autenticarComoPlataformaAdmin(UUID.randomUUID());
        SetupInitializeRequest request = pedidoValido();
        Tenant tenantCriado = Tenant.builder().id(UUID.randomUUID()).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(any())).thenReturn(tenantCriado);
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.createTenant(request));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void anotacaoDeClasseTemValorExatoHasRolePlataformaAdmin() {
        PreAuthorize anotacao = PlatformAdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(anotacao);
        assertEquals("hasRole('PLATAFORMA_ADMIN')", anotacao.value());
    }

    @Test
    void createTenant_naoLeTenantIdDoSecurityContextEDelegaComOMesmoObjetoDePedido() {
        // tenantId da tenant reservada "LexCV" (Plan 01) -- propositadamente diferente do
        // tenant que viria a ser criado; se o controller alguma vez ler
        // UserPrincipal.getTenantId() e o injetar no pedido, este teste passaria a falhar.
        autenticarComoPlataformaAdmin(UUID.randomUUID());
        SetupInitializeRequest request = pedidoValido();
        Tenant tenantCriado = Tenant.builder().id(UUID.randomUUID()).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(any())).thenReturn(tenantCriado);
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        proxy.createTenant(request);

        verify(setupService).provisionTenant(same(request));
    }
}
