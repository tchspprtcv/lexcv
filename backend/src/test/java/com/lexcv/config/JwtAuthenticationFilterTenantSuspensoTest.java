package com.lexcv.config;

import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova comportamental de que {@link JwtAuthenticationFilter#doFilterInternal} re-valida o
 * tenant do utilizador em TODAS as requisições (Phase 120, PROV-05) -- não apenas o utilizador,
 * como já acontecia antes desta fase. Este é o mecanismo que garante efeito IMEDIATO sobre
 * sessões já ativas de um tenant acabado de suspender (ROADMAP Success Criterion 4): o token
 * JWT continua criptograficamente válido, mas deixa de povoar o {@link SecurityContextHolder},
 * e o {@code .anyRequest().authenticated()} do {@code SecurityConfig} converte isso num 401 na
 * própria requisição seguinte, sem logout nem re-login.
 *
 * <p>Segue a convenção de testes deste backend (ver {@code PlatformAdminControllerTest},
 * {@code AdminControllerLimiteUtilizadoresTest}): sem MockMvc/{@code @SpringBootTest} --
 * instanciação direta da classe sob teste com dependências mockadas via Mockito.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTenantSuspensoTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Claims claims;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter novoFiltro() {
        return new JwtAuthenticationFilter(tokenProvider, userRepository, tenantRepository);
    }

    private void stubTokenValidoPara(UUID userId) {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-de-teste");
        when(tokenProvider.validateToken("token-de-teste")).thenReturn(true);
        when(tokenProvider.getClaimsFromToken("token-de-teste")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(userId.toString());
    }

    private User utilizador(UUID id, UUID tenantId, boolean ativo) {
        return User.builder()
                .id(id)
                .tenantId(tenantId)
                .nome("Utilizador de Teste")
                .email("teste@lexcv.cv")
                .ativo(ativo)
                .build();
    }

    private Tenant tenantComAtivo(UUID id, Boolean ativo) {
        return Tenant.builder()
                .id(id)
                .nome("Tenant de Teste")
                .ativo(ativo)
                .build();
    }

    private void correrFiltro() throws Exception {
        novoFiltro().doFilterInternal(request, response, filterChain);
    }

    // Caso 1 (caminho feliz, anti-regressao): utilizador ativo + tenant ativo -> autenticado.
    @Test
    void utilizadorAtivoComTenantAtivoFicaAutenticado() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador(userId, tenantId, true)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, true)));

        correrFiltro();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertInstanceOf(UserPrincipal.class, auth.getPrincipal());
        assertEquals(tenantId, ((UserPrincipal) auth.getPrincipal()).getTenantId());
        verify(filterChain).doFilter(request, response);
    }

    // Caso 2 (o coracao desta fase): utilizador ativo mas tenant suspenso -> deixa de autenticar,
    // apesar de o token continuar criptograficamente valido.
    @Test
    void utilizadorAtivoComTenantSuspensoDeixaDeFicarAutenticado() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador(userId, tenantId, true)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, false)));

        correrFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // Caso 3 (fail-closed em dados corrompidos): tenant com ativo=null e tratado como suspenso.
    @Test
    void tenantComAtivoNuloEhTratadoComoSuspenso() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador(userId, tenantId, true)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, null)));

        correrFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // Caso 4 (tenant inexistente): tenantRepository.findById devolve Optional.empty().
    @Test
    void tenantInexistenteImpedeAutenticacao() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador(userId, tenantId, true)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        correrFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // Caso 5 (regressao do gate pre-existente): utilizador desativado com tenant ativo continua
    // sem autenticar, exatamente como antes desta fase.
    @Test
    void utilizadorDesativadoComTenantAtivoContinuaSemAutenticacao() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador(userId, tenantId, false)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, true)));

        correrFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // Caso 6 (a cadeia nunca parte): mesmo na falha combinada mais adversa (utilizador
    // desativado E tenant suspenso em simultaneo), o filtro nunca curto-circuita a cadeia --
    // apenas deixa de povoar o contexto de seguranca, e nenhuma excecao escapa do filtro
    // (o try/catch existente e fail-closed). E o .anyRequest().authenticated() do
    // SecurityConfig que transforma a ausencia de autenticacao num 401.
    @Test
    void nemUtilizadorDesativadoNemTenantSuspensoInterrompemACadeiaDeFiltros() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        stubTokenValidoPara(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(utilizador(userId, tenantId, false)));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantComAtivo(tenantId, false)));

        assertDoesNotThrow(this::correrFiltro);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // Caso 7 (nao ha consulta desnecessaria): quando o utilizador nao existe, o tenant nunca
    // chega a ser consultado.
    @Test
    void utilizadorInexistenteNuncaConsultaOTenant() throws Exception {
        UUID userId = UUID.randomUUID();
        stubTokenValidoPara(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        correrFiltro();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verify(tenantRepository, never()).findById(any());
    }
}
