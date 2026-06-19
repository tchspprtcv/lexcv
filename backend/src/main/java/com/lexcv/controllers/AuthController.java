package com.lexcv.controllers;

import com.lexcv.config.JwtTokenProvider;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ChangePasswordRequest;
import com.lexcv.dtos.LoginRequest;
import com.lexcv.dtos.LoginResponse;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
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
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email e password são obrigatórios"));
        }

        String ip = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes().getSessionId(); // Ideally real IP
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
