package com.lexcv.controllers;

import com.lexcv.config.JwtTokenProvider;
import com.lexcv.dtos.LoginRequest;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova que dois dos 3 pontos de acesso autenticado (login, refresh) recusam um tenant
 * suspenso -- o terceiro (o filtro por pedido) e coberto por
 * {@code JwtAuthenticationFilterTenantSuspensoTest}. Segue a convencao deste backend (ver
 * {@code AuthControllerGetMeTenantPlanoTest}): sem MockMvc/{@code @SpringBootTest} --
 * instanciacao direta do controller com colaboradores mockados via Mockito.
 *
 * <p>WR-01 (Phase 120 code review): {@code AuthController.login} passou a exigir um
 * {@code HttpServletRequest} (chave do limitador de tentativas falhadas, agora
 * {@code getRemoteAddr()} em vez de um id de sessao servlet -- ver
 * {@code AuthControllerLoginLockoutTest} para a prova comportamental desse limitador). Os testes
 * aqui passam um mock Mockito simples: o valor do IP e irrelevante para as assercoes de
 * suspensao de tenant deste ficheiro.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTenantSuspensoTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    private static final String SENHA_EM_TEXTO_PLANO = "Pa$$w0rd1";
    private static final String HASH_PASSWORD = "hash-de-teste";

    private AuthController novoController() {
        return new AuthController(userRepository, tenantRepository, tokenProvider, passwordEncoder);
    }

    private LoginRequest pedidoLoginValido(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(SENHA_EM_TEXTO_PLANO);
        return request;
    }

    private User utilizador(UUID id, UUID tenantId, boolean ativo, String email) {
        return User.builder()
                .id(id)
                .tenantId(tenantId)
                .nome("Utilizador de Teste")
                .email(email)
                .passwordHash(HASH_PASSWORD)
                .ativo(ativo)
                .build();
    }

    private Tenant tenantComAtivo(UUID id, Boolean ativo) {
        return Tenant.builder().id(id).nome("Tenant de Teste").ativo(ativo).build();
    }

    // ---- login ----

    @Test
    void login_comTenantSuspenso_devolve403ENuncaGeraTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "utilizador@lexcv.cv";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(utilizador(userId, tenantId, true, email)));
        when(passwordEncoder.matches(SENHA_EM_TEXTO_PLANO, HASH_PASSWORD)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, false)));

        ResponseEntity<?> response =
                novoController().login(pedidoLoginValido(email), mock(HttpServletRequest.class));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(
                Map.of("message", "O acesso da sua organização está suspenso. Contacte o suporte LexCV."),
                response.getBody());
        assertNull(response.getHeaders().get(HttpHeaders.SET_COOKIE));
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_comTenantAtivo_devolve200EGeraTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "utilizador@lexcv.cv";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(utilizador(userId, tenantId, true, email)));
        when(passwordEncoder.matches(SENHA_EM_TEXTO_PLANO, HASH_PASSWORD)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, true)));
        when(tokenProvider.generateAccessToken(any(), any(), any())).thenReturn("access-token-novo");
        when(tokenProvider.generateRefreshToken(any(), any(), any())).thenReturn("refresh-token-novo");

        ResponseEntity<?> response =
                novoController().login(pedidoLoginValido(email), mock(HttpServletRequest.class));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tokenProvider).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_comTenantInexistente_devolve403ComAMesmaFraseDeSuspensao() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "utilizador@lexcv.cv";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(utilizador(userId, tenantId, true, email)));
        when(passwordEncoder.matches(SENHA_EM_TEXTO_PLANO, HASH_PASSWORD)).thenReturn(true);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        ResponseEntity<?> response =
                novoController().login(pedidoLoginValido(email), mock(HttpServletRequest.class));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(
                Map.of("message", "O acesso da sua organização está suspenso. Contacte o suporte LexCV."),
                response.getBody());
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void login_comUtilizadorDesativado_mantemFrasePreExistenteENuncaConsultaOTenant() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String email = "utilizador@lexcv.cv";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(utilizador(userId, tenantId, false, email)));
        when(passwordEncoder.matches(SENHA_EM_TEXTO_PLANO, HASH_PASSWORD)).thenReturn(true);

        ResponseEntity<?> response =
                novoController().login(pedidoLoginValido(email), mock(HttpServletRequest.class));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(
                Map.of("message", "Esta conta está desativada. Por favor, contacte o administrador."),
                response.getBody());
        verify(tenantRepository, never()).findById(any());
    }

    // ---- refresh ----

    @Test
    void refresh_comTenantSuspenso_devolve401ENuncaGeraNovosTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(tokenProvider.validateToken("refresh-valido")).thenReturn(true);
        when(tokenProvider.getClaimsFromToken("refresh-valido")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(utilizador(userId, tenantId, true, "utilizador@lexcv.cv")));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, false)));

        ResponseEntity<?> response = novoController().refresh("refresh-valido", null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(
                Map.of("message", "Sessão inválida. O acesso da sua organização está suspenso."),
                response.getBody());
        verify(tokenProvider, never()).generateRefreshToken(any(), any(), any());
    }

    @Test
    void refresh_comTenantAtivo_devolve200EGeraNovosTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(tokenProvider.validateToken("refresh-valido")).thenReturn(true);
        when(tokenProvider.getClaimsFromToken("refresh-valido")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(utilizador(userId, tenantId, true, "utilizador@lexcv.cv")));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, true)));
        when(tokenProvider.generateAccessToken(any(), any(), any())).thenReturn("novo-access-token");
        when(tokenProvider.generateRefreshToken(any(), any(), any())).thenReturn("novo-refresh-token");

        ResponseEntity<?> response = novoController().refresh("refresh-valido", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tokenProvider).generateRefreshToken(any(), any(), any());
    }
}
