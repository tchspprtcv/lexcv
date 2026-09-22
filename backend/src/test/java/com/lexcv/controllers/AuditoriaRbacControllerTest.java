package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.AuditoriaRbacEntradaDto;
import com.lexcv.services.AuditoriaRbacService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 128 (AUDT-03/AUDT-04), Plano 07: prova de comportamento, por proxy real de method
 * security (nunca por reflexao sobre a anotacao para os casos comportamentais), de que
 * {@link AuditoriaRbacController} esta gateado por AUTORIDADE ({@code hasAuthority('rbac:manage')})
 * ao nivel de CLASSE, de que o tenant vem exclusivamente do principal autenticado, de que os
 * bounds de paginacao sao respeitados e de que a classe expoe apenas GET. Segue exatamente as
 * convencoes de {@code OfficeRolesControllerTest} (proxy {@link ProxyFactory} +
 * {@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()}).
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaRbacControllerTest {

    @Mock
    private AuditoriaRbacService auditoriaRbacService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AuditoriaRbacController novoController() {
        return new AuditoriaRbacController(auditoriaRbacService);
    }

    /**
     * Monta o proxy AOP de method security necessario para avaliar {@code @PreAuthorize} de
     * facto -- uma chamada Java direta a {@link #novoController()} nunca o faz. Copia exata do
     * padrao estabelecido em {@code OfficeRolesControllerTest.novoProxyComMethodSecurity()}.
     */
    private AuditoriaRbacController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (AuditoriaRbacController) factory.getProxy();
    }

    private void autenticarComoAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
    }

    private void autenticarComoPrincipalDoTenant(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID()).tenantId(tenantId)
                .nome("Principal de Teste").email("principal@escritorio-teste.cv")
                .roles(Set.of()).permissions(Set.of())
                .authorities(List.of(new SimpleGrantedAuthority("rbac:manage")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Page<AuditoriaRbacEntradaDto> umaPaginaComUmaEntrada() {
        AuditoriaRbacEntradaDto dto = AuditoriaRbacEntradaDto.builder()
                .id(1L)
                .acao("papel_criar")
                .categoria("papel")
                .build();
        return new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
    }

    // ---------------------------------------------------------------------------------------
    // Gate: e uma AUTORIDADE, nao um papel.
    // ---------------------------------------------------------------------------------------

    @Test
    void listar_comAutoridadeRbacManageObtemSucesso() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(auditoriaRbacService.listar(eq(TENANT_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(umaPaginaComUmaEntrada());
        AuditoriaRbacController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.listar(null, null, 0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void listar_comApenasUsersManageERecusado() {
        autenticarComoAuthorities("users:manage");
        AuditoriaRbacController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.listar(null, null, 0, 20));
        verifyNoInteractions(auditoriaRbacService);
    }

    @Test
    void listar_semNenhumaAutoridadeERecusado() {
        autenticarComoAuthorities();
        AuditoriaRbacController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.listar(null, null, 0, 20));
        verifyNoInteractions(auditoriaRbacService);
    }

    // ---------------------------------------------------------------------------------------
    // Tenant sempre do principal (AUDT-03).
    // ---------------------------------------------------------------------------------------

    @Test
    void listar_chamaServicoComTenantDoPrincipalENuncaComOutroTenant() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(auditoriaRbacService.listar(eq(TENANT_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(umaPaginaComUmaEntrada());

        novoController().listar(null, null, 0, 20);

        verify(auditoriaRbacService).listar(eq(TENANT_ID), isNull(), isNull(), any(Pageable.class));
        verify(auditoriaRbacService, never()).listar(eq(OUTRO_TENANT_ID), any(), any(), any());
    }

    @Test
    void listar_handlerNaoDeclaraParametroDeTenant() throws NoSuchMethodException {
        Method metodo = AuditoriaRbacController.class.getDeclaredMethod(
                "listar", UUID.class, UUID.class, int.class, int.class);

        for (Parameter parametro : metodo.getParameters()) {
            String nome = parametro.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(nome.equals("tenantid") || nome.equals("tenant"),
                    "Handler nao deve declarar um parametro de tenant: " + parametro.getName());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Filtros passados tal e qual.
    // ---------------------------------------------------------------------------------------

    @Test
    void listar_passaUtilizadorAlvoIdEPapelIdParaOServicoSemAlteracao() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        UUID utilizadorAlvoId = UUID.randomUUID();
        UUID papelId = UUID.randomUUID();
        when(auditoriaRbacService.listar(eq(TENANT_ID), eq(utilizadorAlvoId), eq(papelId), any(Pageable.class)))
                .thenReturn(umaPaginaComUmaEntrada());

        novoController().listar(utilizadorAlvoId, papelId, 0, 20);

        verify(auditoriaRbacService).listar(eq(TENANT_ID), eq(utilizadorAlvoId), eq(papelId), any(Pageable.class));
    }

    @Test
    void listar_semFiltrosPassaNulosParaOServico() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(auditoriaRbacService.listar(eq(TENANT_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(umaPaginaComUmaEntrada());

        novoController().listar(null, null, 0, 20);

        verify(auditoriaRbacService).listar(eq(TENANT_ID), isNull(), isNull(), any(Pageable.class));
    }

    // ---------------------------------------------------------------------------------------
    // Bounds de paginacao (T-128-35), copiados verbatim de NotificacaoController.listar.
    // ---------------------------------------------------------------------------------------

    @Test
    void listar_pageNegativoDevolveQuatroCentosSemChamarServico() {
        autenticarComoPrincipalDoTenant(TENANT_ID);

        ResponseEntity<?> response = novoController().listar(null, null, -1, 20);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("page deve ser >= 0 e size deve estar entre 1 e 100",
                ((Map<?, ?>) response.getBody()).get("message"));
        verifyNoInteractions(auditoriaRbacService);
    }

    @Test
    void listar_sizeZeroDevolveQuatroCentosSemChamarServico() {
        autenticarComoPrincipalDoTenant(TENANT_ID);

        ResponseEntity<?> response = novoController().listar(null, null, 0, 0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(auditoriaRbacService);
    }

    @Test
    void listar_sizeAcimaDeCemDevolveQuatroCentosSemChamarServico() {
        autenticarComoPrincipalDoTenant(TENANT_ID);

        ResponseEntity<?> response = novoController().listar(null, null, 0, 101);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(auditoriaRbacService);
    }

    @Test
    void listar_defeitosDePaginaETamanhoProduzemPageRequestZeroVinte() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(auditoriaRbacService.listar(eq(TENANT_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(umaPaginaComUmaEntrada());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        novoController().listar(null, null, 0, 20);

        verify(auditoriaRbacService).listar(eq(TENANT_ID), isNull(), isNull(), captor.capture());
        assertEquals(PageRequest.of(0, 20), captor.getValue());
    }

    // ---------------------------------------------------------------------------------------
    // Envelope da resposta.
    // ---------------------------------------------------------------------------------------

    @Test
    void listar_corpoDaRespostaTemExatamenteAsCincoChaves() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(auditoriaRbacService.listar(eq(TENANT_ID), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(umaPaginaComUmaEntrada());

        ResponseEntity<?> response = novoController().listar(null, null, 0, 20);

        Map<?, ?> corpo = (Map<?, ?>) response.getBody();
        assertEquals(Set.of("content", "totalElements", "totalPages", "page", "size"), corpo.keySet());
    }

    // ---------------------------------------------------------------------------------------
    // So-leitura (AUDT-04): a classe declara apenas handlers @GetMapping.
    // ---------------------------------------------------------------------------------------

    @Test
    void classeDeclaraApenasHandlersGetMapping() {
        for (Method metodo : AuditoriaRbacController.class.getDeclaredMethods()) {
            assertFalse(metodo.isAnnotationPresent(PostMapping.class),
                    "Metodo nao deve ter @PostMapping: " + metodo.getName());
            assertFalse(metodo.isAnnotationPresent(PutMapping.class),
                    "Metodo nao deve ter @PutMapping: " + metodo.getName());
            assertFalse(metodo.isAnnotationPresent(PatchMapping.class),
                    "Metodo nao deve ter @PatchMapping: " + metodo.getName());
            assertFalse(metodo.isAnnotationPresent(DeleteMapping.class),
                    "Metodo nao deve ter @DeleteMapping: " + metodo.getName());
        }

        boolean temPeloMenosUmGetMapping = Arrays.stream(AuditoriaRbacController.class.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(GetMapping.class));
        assertTrue(temPeloMenosUmGetMapping, "A classe deve declarar pelo menos um handler @GetMapping");
    }
}
