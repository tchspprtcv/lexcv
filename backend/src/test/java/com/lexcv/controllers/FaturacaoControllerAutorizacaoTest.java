package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.EmailAutomaticoRequest;
import com.lexcv.models.RegimeIva;
import com.lexcv.services.fiscal.ConfiguracaoFiscalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Prova comportamental, por proxy real de method security
 * ({@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()}), de que o gate de CLASSE
 * {@code hasAuthority('financeiro:manage')} de {@link FaturacaoController} recusa toda a gente sem
 * essa autoridade exata (Phase 133, Plano 05, T-133-21, PITFALLS P-24 -- matriz dos 4 papeis
 * semeados) e deixa passar quem a tem. Mesmo padrao de {@code AdminControllerRbacAutorizacaoTest}.
 */
@ExtendWith(MockitoExtension.class)
class FaturacaoControllerAutorizacaoTest {

    @Mock private ConfiguracaoFiscalService configuracaoFiscalService;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private FaturacaoController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(new FaturacaoController(configuracaoFiscalService));
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (FaturacaoController) factory.getProxy();
    }

    private UserPrincipal autenticarComoPrincipalComAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .nome("Principal de Teste")
                .email("principal@escritorio-teste.cv")
                .roles(Set.of())
                .permissions(Set.of(authorities))
                .authorities(autoridades)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, autoridades));
        return principal;
    }

    private static ConfiguracaoFiscalRequest pedido() {
        return new ConfiguracaoFiscalRequest("123456789", "Escritorio Teste", "Rua A", "Praia",
                "geral@escritorio-teste.cv", "+238 9000000", RegimeIva.NORMAL, null);
    }

    private static List<Executable> todosOsHandlers(FaturacaoController proxy) {
        return List.of(
                proxy::getConfiguracao,
                () -> proxy.putConfiguracao(pedido()),
                proxy::postAtivar,
                proxy::postDesativar,
                () -> proxy.putEmailAutomatico(new EmailAutomaticoRequest(false, null)),
                proxy::getSeries,
                proxy::getMotivosIsencao);
    }

    private void assertTodosRecusados() {
        FaturacaoController proxy = novoProxyComMethodSecurity();
        for (Executable handler : todosOsHandlers(proxy)) {
            assertThrows(AccessDeniedException.class, handler);
        }
        verifyNoInteractions(configuracaoFiscalService);
    }

    @Test
    void financeiroViewEEditNaoChegam() {
        autenticarComoPrincipalComAuthorities("financeiro:view", "financeiro:edit");
        assertTodosRecusados();
    }

    @Test
    void autoridadePrefixadaRoleFinanceiroManageERecusada() {
        autenticarComoPrincipalComAuthorities("ROLE_financeiro:manage");
        assertTodosRecusados();
    }

    // Matriz P-24: autoridades por omissao dos papeis semeados (DatabaseSeeder) sem financeiro:manage.
    @Test
    void advogadoPorOmissaoERecusado() {
        autenticarComoPrincipalComAuthorities("ROLE_ADVOGADO", "clientes:view", "clientes:edit",
                "processos:view", "processos:edit", "processos:create", "processos:manage",
                "agenda:view", "agenda:edit", "documentos:view", "documentos:edit", "financeiro:view",
                "pareceres:view", "pareceres:create", "pareceres:edit", "notificacoes:view");
        assertTodosRecusados();
    }

    @Test
    void tecnicoPorOmissaoERecusado() {
        autenticarComoPrincipalComAuthorities("ROLE_TECNICO", "clientes:view", "processos:view",
                "agenda:view", "agenda:edit", "documentos:view", "financeiro:view", "pareceres:view",
                "notificacoes:view");
        assertTodosRecusados();
    }

    @Test
    void assistentePorOmissaoERecusado() {
        autenticarComoPrincipalComAuthorities("ROLE_ASSISTENTE", "clientes:view", "clientes:edit",
                "processos:view", "agenda:view", "documentos:view", "pareceres:view", "notificacoes:view");
        assertTodosRecusados();
    }

    @Test
    void plataformaAdminSemPermissoesERecusado() {
        autenticarComoPrincipalComAuthorities("ROLE_PLATAFORMA_ADMIN");
        assertTodosRecusados();
    }

    @Test
    void roleAdminSozinhoNaoChega() {
        autenticarComoPrincipalComAuthorities("ROLE_ADMIN");
        assertTodosRecusados();
    }

    @Test
    void financeiroManageDelegaEmTodosOsHandlers() {
        UserPrincipal principal = autenticarComoPrincipalComAuthorities("ROLE_ADMIN", "financeiro:manage");
        FaturacaoController proxy = novoProxyComMethodSecurity();

        for (Executable handler : todosOsHandlers(proxy)) {
            assertDoesNotThrow(handler);
        }

        UUID tenant = principal.getTenantId();
        verify(configuracaoFiscalService).obter(tenant);
        verify(configuracaoFiscalService).guardar(org.mockito.ArgumentMatchers.eq(tenant),
                org.mockito.ArgumentMatchers.eq(principal), any(ConfiguracaoFiscalRequest.class));
        verify(configuracaoFiscalService).ativar(tenant, principal);
        verify(configuracaoFiscalService).desativar(tenant, principal);
        verify(configuracaoFiscalService).definirEmailAutomatico(org.mockito.ArgumentMatchers.eq(tenant),
                org.mockito.ArgumentMatchers.eq(principal), any(EmailAutomaticoRequest.class));
        verify(configuracaoFiscalService).listarSeries(tenant);
    }
}
