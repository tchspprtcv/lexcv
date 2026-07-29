package com.lexcv.config;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Claims claims = tokenProvider.getClaimsFromToken(jwt);
                UUID userId = UUID.fromString(claims.getSubject());

                User user = userRepository.findById(userId).orElse(null);
                // Phase 120 (PROV-05): resolver o tenant apenas quando ha utilizador, para nao
                // gastar uma query quando o utilizador nem existe.
                Tenant tenant = user != null ? tenantRepository.findById(user.getTenantId()).orElse(null) : null;

                // Esta e a via que garante efeito IMEDIATO de uma suspensao de tenant sobre
                // sessoes JA ativas (ROADMAP Success Criterion 4) -- nao apenas no proximo
                // login. Reutiliza exatamente a mesma re-validacao por pedido ja provada para a
                // desativacao de utilizador (user.getAtivo() abaixo), alargada para tambem
                // exigir tenant.getAtivo(). O token JWT continua criptograficamente valido; e
                // esta verificacao, repetida em TODO pedido autenticado, que recusa. Custo
                // aceite conscientemente: uma segunda query (por PK indexada) por pedido
                // autenticado -- deliberadamente sem nenhuma forma de memorizacao/reutilizacao
                // entre pedidos, porque o requisito e "imediato", nao "no proximo login".
                if (user != null && user.getAtivo() && tenant != null && Boolean.TRUE.equals(tenant.getAtivo())) {
                    Set<String> roles = user.getRoles().stream()
                            .map(Role::getNome)
                            .collect(Collectors.toSet());

                    Set<String> permissions = user.getRoles().stream()
                            .flatMap(r -> r.getPermissions().stream())
                            .map(Permission::getNome)
                            .collect(Collectors.toSet());

                    user.getPermissions().forEach(permissions::add);

                    UserPrincipal principal = UserPrincipal.create(
                            user.getId(),
                            user.getTenantId(),
                            user.getNome(),
                            user.getEmail(),
                            roles,
                            permissions
                    );

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (user == null) {
                    logger.warn("JWT valid but user " + userId + " not found");
                } else if (!Boolean.TRUE.equals(user.getAtivo())) {
                    logger.warn("JWT valid but user " + userId + " is deactivated");
                } else {
                    logger.warn("JWT valid but tenant " + user.getTenantId() + " is suspended or missing");
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
