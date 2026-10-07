package com.lexcv.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigCorsTest {

    @Test
    void corsPermiteOrigemLexcvEWildcards() {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "corsAllowedOrigins",
                "https://alcv.tech,https://www.alcv.tech,http://localhost:3000,http://localhost:3003,http://lexcv:3003,http://lexcv:*");

        CorsConfigurationSource source = config.corsConfigurationSource();
        assertNotNull(source);

        // Testa pedido de lexcv:3003
        MockHttpServletRequest requestLexcv = new MockHttpServletRequest("GET", "/api/v1/setup/status");
        requestLexcv.addHeader("Origin", "http://lexcv:3003");
        CorsConfiguration corsConfigLexcv = source.getCorsConfiguration(requestLexcv);
        assertNotNull(corsConfigLexcv);
        String matchedLexcv = corsConfigLexcv.checkOrigin("http://lexcv:3003");
        assertEquals("http://lexcv:3003", matchedLexcv);
        assertTrue(Boolean.TRUE.equals(corsConfigLexcv.getAllowCredentials()));

        // Testa pedido de outra porta lexcv:*
        String matchedLexcvPorta = corsConfigLexcv.checkOrigin("http://lexcv:8089");
        assertEquals("http://lexcv:8089", matchedLexcvPorta);

        // Testa pedido de alcv.tech
        String matchedAlcv = corsConfigLexcv.checkOrigin("https://alcv.tech");
        assertEquals("https://alcv.tech", matchedAlcv);

        // Testa pedido de origem não autorizada
        String matchedNaoAutorizado = corsConfigLexcv.checkOrigin("http://site-malicioso.com");
        assertNull(matchedNaoAutorizado);
    }

    @Test
    void corsFallbackQuandoVazioOuNuloPermiteLocalhostELexcv() {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "corsAllowedOrigins", "");

        CorsConfigurationSource source = config.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/setup/status");
        request.addHeader("Origin", "http://lexcv:3003");
        CorsConfiguration corsConfig = source.getCorsConfiguration(request);
        assertNotNull(corsConfig);

        assertEquals("http://lexcv:3003", corsConfig.checkOrigin("http://lexcv:3003"));
        assertEquals("http://localhost:3000", corsConfig.checkOrigin("http://localhost:3000"));
        assertEquals("https://alcv.tech", corsConfig.checkOrigin("https://alcv.tech"));
    }
}
