package com.lexcv.controllers;

import com.lexcv.config.JwtTokenProvider;
import com.lexcv.dtos.LoginRequest;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WR-01 (Phase 120 code review): prova que o limitador de tentativas falhadas de
 * {@code AuthController.login} acumula corretamente agora que a chave assenta no IP real do
 * chamador ({@code HttpServletRequest.getRemoteAddr()}), nao num id de sessao servlet.
 *
 * <p>Antes desta correcao, {@code RequestContextHolder...getSessionId()} devolvia um id novo e
 * aleatorio a cada pedido para qualquer chamador que nao reenviasse cookies (o comportamento por
 * omissao de um cliente HTTP scriptado sem cookie jar, ex.: curl/requests) -- o que tornava
 * {@code attemptKey} diferente em cada tentativa e o lockout nunca disparava. Este ficheiro simula
 * um IP estavel (mock de {@code HttpServletRequest} com {@code getRemoteAddr()} sempre a devolver
 * o mesmo valor) ao longo de varias chamadas sucessivas para provar que a 5ª tentativa falhada
 * ativa o lockout e a 6ª e recusada com 429 antes sequer de validar a password -- e que um IP
 * diferente para o mesmo email nao partilha esse lockout.
 *
 * <p>Segue a mesma convencao de {@code AuthControllerTenantSuspensoTest}: sem MockMvc/
 * {@code @SpringBootTest} -- instanciacao direta do controller com colaboradores mockados via
 * Mockito. {@code loginAttempts}/{@code lockoutTimers} sao campos de instancia de
 * {@code AuthController}, por isso cada teste usa uma UNICA instancia do controller ao longo de
 * todas as suas chamadas (tal como o bean singleton real do Spring acumula estado entre pedidos).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLoginLockoutTest {

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "alvo@lexcv.cv";
    private static final String PASSWORD_ERRADA = "password-errada";
    private static final String HASH_REAL = "hash-real-irrelevante";

    private AuthController novoController() {
        return new AuthController(userRepository, tenantRepository, tokenProvider, passwordEncoder);
    }

    private HttpServletRequest mockRequestComIp(String ip) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(ip);
        return request;
    }

    private LoginRequest pedidoComPasswordErrada() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD_ERRADA);
        return request;
    }

    @Test
    void login_com5TentativasFalhadasDoMesmoIp_bloqueiaA6aTentativaSemValidarPassword() {
        User utilizador = User.builder().email(EMAIL).passwordHash(HASH_REAL).ativo(true).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(utilizador));
        when(passwordEncoder.matches(PASSWORD_ERRADA, HASH_REAL)).thenReturn(false);

        AuthController controller = novoController();
        HttpServletRequest ipEstavel = mockRequestComIp("203.0.113.42");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<?> resposta = controller.login(pedidoComPasswordErrada(), ipEstavel);
            assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
        }

        ResponseEntity<?> sextaTentativa = controller.login(pedidoComPasswordErrada(), ipEstavel);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, sextaTentativa.getStatusCode());
        assertEquals(
                Map.of("message", "Muitas tentativas falhadas. Tente novamente mais tarde."),
                sextaTentativa.getBody());
    }

    @Test
    void login_tentativasFalhadasDeIpsDiferentesParaOMesmoEmail_naoPartilhamLockout() {
        User utilizador = User.builder().email(EMAIL).passwordHash(HASH_REAL).ativo(true).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(utilizador));
        when(passwordEncoder.matches(PASSWORD_ERRADA, HASH_REAL)).thenReturn(false);

        AuthController controller = novoController();
        HttpServletRequest ipA = mockRequestComIp("203.0.113.42");
        for (int i = 0; i < 5; i++) {
            controller.login(pedidoComPasswordErrada(), ipA);
        }

        HttpServletRequest ipB = mockRequestComIp("198.51.100.7");
        ResponseEntity<?> respostaDeOutroIp = controller.login(pedidoComPasswordErrada(), ipB);

        // Mesmo com o IP A ja bloqueado, o IP B para o mesmo email tem a sua propria chave de
        // tentativas -- prova de que a chave incorpora mesmo o IP, nao so o email.
        assertEquals(HttpStatus.UNAUTHORIZED, respostaDeOutroIp.getStatusCode());
    }
}
