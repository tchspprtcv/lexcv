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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 124 (achado de integração #2 da auditoria do marco v2.16): prova que
 * {@code GET /api/v1/auth/me} passa a expor {@code tenant_utilizadores_ativos}, calculado
 * exclusivamente pela função de contagem única do repositório de utilizadores (Fase 117), a
 * qualquer sessão autenticada. Fecha a duplicação em que o indicador "X/Y utilizadores" do
 * frontend (Fase 118) recalculava esta mesma contagem no cliente por {@code filter()} sobre a
 * lista completa de utilizadores — depois desta fase o cliente deixa de ter razão para o fazer,
 * porque o backend passa a fornecer o número diretamente.
 *
 * <p>Segue a mesma convenção de todos os testes de controller deste codebase (ver
 * {@code AuthControllerGetMeTenantPlanoTest}, {@code AdminControllerLimiteUtilizadoresTest}): não
 * existe harness MockMvc/{@code @SpringBootTest}/{@code @WebMvcTest} neste projeto — o controller
 * é instanciado diretamente com colaboradores mockados via Mockito, {@code getMe()} é chamado
 * como uma chamada Java simples, e o {@code SecurityContextHolder} é povoado manualmente com um
 * {@link UserPrincipal} do tenant do caso e limpo em {@code @AfterEach}.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerGetMeUtilizadoresAtivosTest {

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
    void getMe_comTenantEncontrado_devolveContagemDeUtilizadoresAtivosDaFonteUnica() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).plano(TenantPlano.STANDARD).limiteUtilizadores(5).build()));
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(3L);

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertEquals(3L, body.getTenant_utilizadores_ativos().longValue());
        assertEquals("STANDARD", body.getTenant_plano());
        assertEquals(5, body.getTenant_limite_utilizadores().intValue());
    }

    @Test
    void getMe_comZeroUtilizadoresAtivos_devolveZeroEmVezDeNulo() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).plano(TenantPlano.STANDARD).limiteUtilizadores(5).build()));
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(0L);

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertNotNull(body.getTenant_utilizadores_ativos());
        assertEquals(0L, body.getTenant_utilizadores_ativos().longValue());
    }

    @Test
    void getMe_semTenantEncontrado_deixaContagemNulaENaoConsultaOsUtilizadores() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertNull(body.getTenant_utilizadores_ativos());
        assertNull(body.getTenant_plano());
        assertNull(body.getTenant_limite_utilizadores());
        verify(userRepository, never()).countByTenantIdAndAtivoTrue(any());
    }

    @Test
    void getMe_consultaTenantEContagemExatamenteUmaVezCada() {
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
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(2L);

        ResponseEntity<?> response = novoController().getMe();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserResponse body = (UserResponse) response.getBody();
        assertEquals("Escritorio X", body.getTenant_nome());
        assertEquals("data:image/png;base64,AAA", body.getTenant_logo_data_url());
        assertEquals("STARTER", body.getTenant_plano());
        assertEquals(3, body.getTenant_limite_utilizadores().intValue());
        assertEquals(2L, body.getTenant_utilizadores_ativos().longValue());
        verify(tenantRepository, times(1)).findById(TENANT_ID);
        verify(userRepository, times(1)).countByTenantIdAndAtivoTrue(TENANT_ID);
    }
}
