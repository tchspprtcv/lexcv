package com.lexcv.controllers;

import com.lexcv.config.JwtTokenProvider;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ChangePasswordRequest;
import com.lexcv.dtos.LoginRequest;
import com.lexcv.dtos.LoginResponse;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    // Simple in-memory rate limiter (for demonstration/basic protection)
    private final Map<String, Integer> loginAttempts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> lockoutTimers = new java.util.concurrent.ConcurrentHashMap<>();

    private org.springframework.http.ResponseCookie createCookie(String name, String value, long maxAge) {
        return org.springframework.http.ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false) // Set to true in prod (HTTPS)
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email e password são obrigatórios"));
        }

        // WR-01 (Phase 120 code review): a chave do limitador de tentativas falhadas tem de
        // assentar no IP real do chamador, nunca num id de sessao servlet. Esta app corre com
        // SessionCreationPolicy.STATELESS e sem cookie JSESSIONID persistido por um cliente JWT
        // normal, por isso RequestContextHolder...getSessionId() (usado antes) criava/devolvia
        // uma sessao nova e aleatoria a cada pedido para qualquer chamador que nao reenviasse
        // cookies -- o comportamento por omissao de qualquer cliente HTTP scriptado (ex.:
        // curl/requests sem cookie jar explicito). Isso tornava attemptKey diferente em cada
        // tentativa, pelo que loginAttempts/lockoutTimers nunca acumulavam e o lockout nunca
        // disparava. request.getRemoteAddr() nao pode ser forjado pelo cliente, ao contrario de
        // X-Forwarded-For/X-Real-IP -- mas NAO honrar esses cabecalhos tem um custo conhecido
        // neste repositorio especifico: o Caddyfile deste projeto poe o Caddy como unico ingress
        // publico para /api/*, e o docker-compose.yml de dev tambem publica a porta do proprio
        // backend diretamente no host (8089:8080) a par disso -- por isso, em producao,
        // getRemoteAddr() podera devolver sempre o endereco interno do container Caddy (nao o IP
        // real do cliente), o que colapsa attemptKey num bloqueio efetivamente por-email (ainda
        // uma melhoria estrita face ao bug original: nunca bloqueava ninguem), nao por-atacante.
        // Seguimento: nao foi possivel confirmar o estado real da firewall/infra de producao
        // (VPS com Docker publica portas via iptables de forma que tipicamente ignora regras do
        // ufw), por isso optou-se por eliminar a via de bypass na origem em vez de confiar nela.
        // docker-compose.prod.yml usa agora `ports: !reset []` no servico backend, anulando o
        // "8089:8080" herdado de docker-compose.yml -- o Caddy passa a ser o unico caminho ate
        // este endpoint em producao (rede Docker interna lexcv_net, nao publicada ao host). Com
        // essa via fechada, application-prod.yml ativa server.forward-headers-strategy=framework
        // (isolado ao perfil prod -- ver comentario la), pelo que o ip acima passa a refletir o
        // X-Forwarded-For genuino que o Caddy define. Sem o fecho da porta, confiar nesse
        // cabecalho seria pior do que o estado anterior: um atacante com acesso direto ao
        // backend poderia forja-lo para nunca acionar o lockout.
        String ip = request.getRemoteAddr();
        String attemptKey = loginRequest.getEmail() + "-" + ip;

        if (lockoutTimers.containsKey(attemptKey) && System.currentTimeMillis() < lockoutTimers.get(attemptKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "Muitas tentativas falhadas. Tente novamente mais tarde."));
        }

        User user = userRepository.findByEmail(loginRequest.getEmail()).orElse(null);
        if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            int attempts = loginAttempts.getOrDefault(attemptKey, 0) + 1;
            loginAttempts.put(attemptKey, attempts);
            if (attempts >= 5) {
                lockoutTimers.put(attemptKey, System.currentTimeMillis() + 15 * 60 * 1000); // 15 mins
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Credenciais inválidas"));
        }

        loginAttempts.remove(attemptKey);
        lockoutTimers.remove(attemptKey);

        if (user.getAtivo() == null || !user.getAtivo()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "Esta conta está desativada. Por favor, contacte o administrador."
            ));
        }

        // Phase 120 (PROV-05): gate de tenant, DEPOIS do gate de conta -- uma conta
        // desativada continua a receber a sua propria mensagem, sem revelar o estado da
        // organizacao a quem ainda nao provou pertencer a ela. Este e 1 dos 3 pontos de
        // acesso que tem de recusar um tenant suspenso (ver JwtAuthenticationFilter para a
        // re-validacao por pedido de sessoes ja ativas, e o gate equivalente em refresh()
        // abaixo). A condicao abaixo trata tenant nulo/ausente da mesma forma que um
        // tenant explicitamente suspenso -- fail-closed, mesma regra do filtro.
        Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
        if (tenant == null || !Boolean.TRUE.equals(tenant.getAtivo())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "message", "O acesso da sua organização está suspenso. Contacte o suporte LexCV."
            ));
        }

        List<String> roles = user.getRoles().stream().map(Role::getNome).collect(Collectors.toList());
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getTenantId(), roles);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getTenantId(), roles);

        LoginResponse response = LoginResponse.builder()
                .user(LoginResponse.UserResponseInfo.builder()
                        .id(user.getId())
                        .nome(user.getNome())
                        .build())
                .build();

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("access_token", accessToken, 86400).toString())
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("refresh_token", refreshToken, 2592000).toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("access_token", "", 0).toString())
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("refresh_token", "", 0).toString())
                .body(Map.of("message", "Logout efetuado com sucesso"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refresh_token", required = false) String refreshTokenFromCookie, 
                                     @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String refreshToken = refreshTokenFromCookie;
        if (refreshToken == null && authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            refreshToken = authorizationHeader.substring(7);
        }

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Token em falta"));
        }

        if (!tokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Token inválido ou expirado"));
        }

        try {
            var claims = tokenProvider.getClaimsFromToken(refreshToken);
            java.util.UUID userId = java.util.UUID.fromString(claims.getSubject());
            
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.getAtivo()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Utilizador inválido"));
            }

            // Phase 120 (PROV-05): /auth/refresh esta em permitAll() no SecurityConfig --
            // nunca passa pelo caminho autenticado do JwtAuthenticationFilter, por isso
            // precisa do seu proprio gate de tenant. Sem ele, um utilizador de um tenant
            // suspenso poderia continuar a cunhar tokens de acesso novos indefinidamente
            // atraves desta via publica. Mantém 401 (nao 403) porque e o codigo que o resto
            // deste fluxo ja usa e que o frontend interpreta como "voltar ao login".
            Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
            if (tenant == null || !Boolean.TRUE.equals(tenant.getAtivo())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "message", "Sessão inválida. O acesso da sua organização está suspenso."
                ));
            }

            List<String> roles = user.getRoles().stream().map(Role::getNome).collect(Collectors.toList());
            String newAccessToken = tokenProvider.generateAccessToken(user.getId(), user.getTenantId(), roles);
            String newRefreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getTenantId(), roles);

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("access_token", newAccessToken, 86400).toString())
                    .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("refresh_token", newRefreshToken, 2592000).toString())
                    .body(Map.of("message", "Tokens renovados com sucesso"));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Erro ao processar refresh"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Não autorizado"));
        }

        UserResponse response = UserResponse.builder()
                .id(principal.getUserId())
                .tenant_id(principal.getTenantId())
                .nome(principal.getNome() != null ? principal.getNome() : principal.getUsername())
                .email(principal.getEmail())
                .roles(principal.getRoles())
                .permissions(principal.getPermissions())
                .ativo(true)
                .build();
        
        userRepository.findById(principal.getUserId()).ifPresent(u -> {
            response.setNome(u.getNome());
            response.setTelefone(u.getTelefone());
            response.setAvatar_url(u.getAvatarUrl());
        });

        tenantRepository.findById(principal.getTenantId()).ifPresent(t -> {
            response.setTenant_nome(t.getNome());
            response.setTenant_logo_data_url(t.getLogoDataUrl());
            response.setTenant_plano(t.getPlano() != null ? t.getPlano().name() : null);
            response.setTenant_limite_utilizadores(t.getLimiteUtilizadores());
        });

        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Não autorizado"));
        }

        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Utilizador não encontrado"));
        }

        if (body.containsKey("nome")) user.setNome(body.get("nome"));
        if (body.containsKey("email")) user.setEmail(body.get("email"));
        if (body.containsKey("telefone")) user.setTelefone(body.get("telefone"));
        if (body.containsKey("avatar_url")) user.setAvatarUrl(body.get("avatar_url"));

        user = userRepository.save(user);

        Set<String> roles = user.getRoles().stream().map(Role::getNome).collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getNome)
                .collect(Collectors.toSet());
        user.getPermissions().forEach(permissions::add);

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .tenant_id(user.getTenantId())
                .nome(user.getNome())
                .email(user.getEmail())
                .telefone(user.getTelefone())
                .avatar_url(user.getAvatarUrl())
                .roles(roles)
                .permissions(permissions)
                .ativo(user.getAtivo())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Não autorizado"));
        }

        if (request.getCurrentPassword() == null || request.getNewPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Parâmetros em falta"));
        }

        if (!request.getNewPassword().matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial."));
        }

        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado"));
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "A palavra-passe atual está incorreta."));
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("access_token", "", 0).toString())
                .header(org.springframework.http.HttpHeaders.SET_COOKIE, createCookie("refresh_token", "", 0).toString())
                .body(Map.of("message", "Palavra-passe alterada com sucesso! Faça login novamente."));
    }
}
