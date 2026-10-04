package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 134 (D-16, R-05, T-134-38): prova, pelo interceptor REAL de method security
 * ({@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()}), dos gates por método de
 * {@link DocumentoFiscalController}: {@code financeiro:view} para lista e detalhe;
 * {@code financeiro:edit} para a pré-visualização (o mesmo gate de registar um pagamento);
 * {@code financeiro:view} ou {@code financeiro:edit} para o estado de emissão (WR-03 da revisão). Mesmo
 * andaime de {@link FaturacaoControllerAutorizacaoTest}.
 *
 * <p><b>Duas camadas, regras diferentes:</b> o backend verifica a autoridade EXATA; o frontend
 * ({@code web/src/lib/permissions.ts}) aceita uma cadeia de equivalências ({@code view} é
 * satisfeito por view/create/edit/manage; {@code edit} por edit/manage). Um papel personalizado
 * só com {@code financeiro:edit} veria a página na UI mas receberia 403 na lista -- é o que o caso
 * {@link #soEditPreVisualizaELeOEstadoMasNaoOsDocumentos()} documenta. Na prática as camadas concordam porque os papéis
 * semeados ({@code DatabaseSeeder}/{@code UserPrincipal.create}) detêm sempre {@code view} quando
 * detêm {@code edit}: ADMIN tem view+edit+manage; ADVOGADO e TECNICO têm só view (leem, não
 * pré-visualizam -- também não registam pagamentos); ASSISTENTE não tem nenhuma autoridade
 * financeiro (recusado em tudo, como na UI).
 */
class DocumentoFiscalControllerAutorizacaoTest {

    private PreVisualizacaoFaturaService preVisualizacao;
    private DocumentoFiscalService documentos;

    @BeforeEach
    void preparar() {
        preVisualizacao = mock(PreVisualizacaoFaturaService.class);
        documentos = mock(DocumentoFiscalService.class);
        when(documentos.listar(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
    }

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private DocumentoFiscalController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(new DocumentoFiscalController(preVisualizacao, documentos));
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (DocumentoFiscalController) factory.getProxy();
    }

    private UserPrincipal autenticarComAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
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

    private static PagamentoRequest pedido() {
        return new PagamentoRequest(1, BigDecimal.TEN, null, "DINHEIRO", null, null);
    }

    private static Executable estado(DocumentoFiscalController p) {
        return p::estadoEmissao;
    }

    private static Executable listar(DocumentoFiscalController p) {
        return () -> p.listar(null, null, null, null, null, "0", "10");
    }

    private static Executable detalhe(DocumentoFiscalController p, UUID id) {
        return () -> p.detalhe(id.toString());
    }

    private static Executable preVisualizar(DocumentoFiscalController p) {
        return () -> p.preVisualizar(pedido());
    }

    private static List<Executable> leitura(DocumentoFiscalController p) {
        return List.of(estado(p), listar(p), detalhe(p, UUID.randomUUID()));
    }

    private void assertTodosRecusados() {
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        for (Executable h : leitura(proxy)) {
            assertThrows(AccessDeniedException.class, h);
        }
        assertThrows(AccessDeniedException.class, preVisualizar(proxy));
        verifyNoInteractions(preVisualizacao, documentos);
    }

    @Test
    void semAutoridadeFinanceiroTudoRecusado() {
        autenticarComAuthorities("clientes:view", "processos:view");
        assertTodosRecusados();
    }

    @Test
    void soViewLeMasNaoPreVisualiza() {
        UserPrincipal principal = autenticarComAuthorities("financeiro:view");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        for (Executable h : leitura(proxy)) {
            assertDoesNotThrow(h);
        }
        assertThrows(AccessDeniedException.class, preVisualizar(proxy));

        verify(preVisualizacao).estadoEmissao(principal.getTenantId());
        verify(preVisualizacao, org.mockito.Mockito.never()).preVisualizar(any(), any());
    }

    @Test
    void soEditPreVisualizaELeOEstadoMasNaoOsDocumentos() {
        UserPrincipal principal = autenticarComAuthorities("financeiro:edit");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        assertDoesNotThrow(preVisualizar(proxy));
        // WR-03 da revisão: quem regista pagamentos sabe se a faturação está ativa.
        assertDoesNotThrow(estado(proxy));
        verify(preVisualizacao).estadoEmissao(principal.getTenantId());
        assertThrows(AccessDeniedException.class, listar(proxy));
        assertThrows(AccessDeniedException.class, detalhe(proxy, UUID.randomUUID()));
        verifyNoInteractions(documentos);
    }

    @Test
    void soManageNaoLeOEstado() {
        autenticarComAuthorities("financeiro:manage");
        assertTodosRecusados();
    }

    @Test
    void viewEEditPassamEmTudoComOTenantDoPrincipal() {
        UserPrincipal principal = autenticarComAuthorities("financeiro:view", "financeiro:edit");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(estado(proxy));
        assertDoesNotThrow(listar(proxy));
        assertDoesNotThrow(detalhe(proxy, id));
        assertDoesNotThrow(preVisualizar(proxy));

        UUID tenant = principal.getTenantId();
        verify(preVisualizacao).estadoEmissao(tenant);
        verify(preVisualizacao).preVisualizar(eq(tenant), any(PagamentoRequest.class));
        verify(documentos).listar(eq(tenant), any(), any(), any(), any(), any(), anyInt(), anyInt());
        verify(documentos).detalhe(tenant, id);
    }

    @Test
    void autoridadePrefixadaRoleERecusada() {
        autenticarComAuthorities("ROLE_financeiro:view", "ROLE_financeiro:edit");
        assertTodosRecusados();
    }

    @Test
    void plataformaAdminSemPermissoesDeAmbitoERecusado() {
        autenticarComAuthorities("ROLE_PLATAFORMA_ADMIN");
        assertTodosRecusados();
    }

    @Test
    void roleAdminSozinhoNaoChega() {
        autenticarComAuthorities("ROLE_ADMIN");
        assertTodosRecusados();
    }

    // Matriz dos papéis semeados (autoridades por omissão, como em FaturacaoControllerAutorizacaoTest).

    @Test
    void advogadoPorOmissaoLeMasNaoPreVisualiza() {
        autenticarComAuthorities("ROLE_ADVOGADO", "clientes:view", "clientes:edit", "processos:view",
                "processos:edit", "processos:create", "processos:manage", "agenda:view", "agenda:edit",
                "documentos:view", "documentos:edit", "financeiro:view", "pareceres:view", "pareceres:create",
                "pareceres:edit", "notificacoes:view");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        for (Executable h : leitura(proxy)) {
            assertDoesNotThrow(h);
        }
        assertThrows(AccessDeniedException.class, preVisualizar(proxy));
    }

    @Test
    void assistentePorOmissaoERecusado() {
        autenticarComAuthorities("ROLE_ASSISTENTE", "clientes:view", "clientes:edit", "processos:view",
                "agenda:view", "documentos:view", "pareceres:view", "notificacoes:view");
        assertTodosRecusados();
    }

    @Test
    void adminSemeadoPassaEmTudo() {
        UserPrincipal admin = UserPrincipal.create(UUID.randomUUID(), UUID.randomUUID(), "Admin", "admin@lexcv.cv",
                Set.of("ADMIN"), Set.of(), Set.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        for (Executable h : leitura(proxy)) {
            assertDoesNotThrow(h);
        }
        assertDoesNotThrow(preVisualizar(proxy));
    }
}
