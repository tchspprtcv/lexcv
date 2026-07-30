package com.lexcv.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WR-01 seguimento (Phase 120 code review): prova o mecanismo concreto de que depende o
 * comentário em {@code AuthController.login} -- que {@code server.forward-headers-strategy=framework}
 * (ativo só no perfil prod, ver {@code application-prod.yml}) faz o {@link ForwardedHeaderFilter}
 * reescrever {@code HttpServletRequest.getRemoteAddr()} a partir de {@code X-Forwarded-For}, e
 * que sem esse filtro o valor bruto do socket é que prevalece. Isto só é seguro em produção
 * porque {@code docker-compose.prod.yml} fecha com {@code ports: !reset []} o acesso direto à
 * porta do host do backend (ver comentário em {@code AuthController.login}); este teste não
 * cobre essa parte de infraestrutura, só a mecânica Spring que a decisão assume.
 *
 * <p>Segue a convenção deste backend de instanciar diretamente a classe sob teste em vez de
 * subir um {@code @SpringBootTest} -- aqui nem sequer há mocks, {@link ForwardedHeaderFilter} é
 * usado tal como o Spring Boot o regista quando {@code forward-headers-strategy: framework}.
 */
class ForwardedHeaderFilterRemoteAddrTest {

    @Test
    void comForwardedHeaderFilter_getRemoteAddrReflecteXForwardedFor() throws Exception {
        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5"); // IP interno do container Caddy na rede Docker
        request.addHeader("X-Forwarded-For", "203.0.113.42");

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals("203.0.113.42", chain.getRequest().getRemoteAddr());
    }

    @Test
    void semForwardedHeaderFilter_getRemoteAddrIgnoraXForwardedFor() {
        // Contraste direto: o mesmo pedido, sem o filtro, mantém o IP bruto do socket -- prova
        // de que é mesmo o filtro (e não algo implícito no servlet container) que faz a troca.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.42");

        assertEquals("172.18.0.5", request.getRemoteAddr());
    }

    @Test
    void applicationProdYml_ativaForwardHeadersStrategyFramework() throws Exception {
        // Guarda de regressão para o comentário em AuthController.login: se esta chave for
        // removida de application-prod.yml, o filtro acima nunca é registado em produção e o
        // comentário passa a descrever um estado que já não existe.
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-prod.yml")) {
            assertNotNull(in, "application-prod.yml deveria estar no classpath de teste");

            @SuppressWarnings("unchecked")
            Map<String, Object> root = new Yaml().load(in);
            @SuppressWarnings("unchecked")
            Map<String, Object> server = (Map<String, Object>) root.get("server");

            assertEquals("framework", server.get("forward-headers-strategy"));
        }
    }
}
