package com.lexcv.controllers;

import com.lexcv.config.JwtTokenProvider;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantPlano;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 118 (PLAN-03): prova que {@code GET /api/v1/auth/me} passa a expor
 * {@code tenant_plano} e {@code tenant_limite_utilizadores} a qualquer sessao autenticada,
 * seja qual for o papel, sem quebrar os campos irmaos {@code tenant_nome}/
 * {@code tenant_logo_data_url} ja existentes e sem introduzir uma segunda consulta ao
 * {@code TenantRepository}.
 *
 * <p>Segue a mesma convencao de todos os testes de controller deste codebase (ver
 * {@code AdminControllerLimiteUtilizadoresTest}, {@code PublicControllerTest}): nao existe
 * harness MockMvc/{@code @SpringBootTest} neste projeto -- o controller e instanciado
 * diretamente com colaboradores mockados via Mockito, {@code getMe()} e chamado como uma
 * chamada Java simples, e o {@code SecurityContextHolder} e povoado manualmente com um
 * {@link UserPrincipal} do tenant do caso e limpo em {@code @AfterEach}.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerGetMeTenantPlanoTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private PasswordEncoder passwordEncoder;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoPrincipalDoTenant() {
        UserPrincipal principal = UserPrincipal.builder().userId(USER_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private AuthController novoController() {
        return new AuthController(userRepository, tenantRepository, tokenProvider, passwordEncoder);
    }

    @Test
    void getMe_comPlanoELimiteNumericos_devolveAmbosNoContrato() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).plano(TenantPlano.STANDARD).limiteUtilizadores(5).build()));

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertEquals("STANDARD", body.getTenant_plano());
        assertEquals(5, body.getTenant_limite_utilizadores().intValue());
    }

    @Test
    void getMe_comLimiteNull_devolveNullNuncaZero() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).plano(TenantPlano.ENTERPRISE).limiteUtilizadores(null).build()));

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertEquals("ENTERPRISE", body.getTenant_plano());
        assertNull(body.getTenant_limite_utilizadores());
    }

    @Test
    void getMe_comPlanoNull_naoLancaNullPointerException() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).plano(null).limiteUtilizadores(10).build()));

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertNull(body.getTenant_plano());
        assertEquals(10, body.getTenant_limite_utilizadores().intValue());
    }

    @Test
    void getMe_naoQuebraCamposIrmaosEConsultaTenantApenasUmaVez() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder()
                        .id(TENANT_ID)
                        .nome("Escritorio X")
                        .logoDataUrl("data:image/png;base64,AAA")
                        .plano(TenantPlano.STARTER)
                        .limiteUtilizadores(3)
                        .build()));

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertEquals("Escritorio X", body.getTenant_nome());
        assertEquals("data:image/png;base64,AAA", body.getTenant_logo_data_url());
        assertEquals("STARTER", body.getTenant_plano());
        assertEquals(3, body.getTenant_limite_utilizadores().intValue());
        verify(tenantRepository, times(1)).findById(TENANT_ID);
    }
}
