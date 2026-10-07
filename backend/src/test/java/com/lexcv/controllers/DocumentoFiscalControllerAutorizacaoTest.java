package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.EstadoEmissaoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.fiscal.efatura.EfaturaGateway;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalService;
import com.lexcv.services.fiscal.DescargaDocumentoFiscalTransacoes;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.NotaCreditoService;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import com.lexcv.services.fiscal.ReenvioEmailFiscalService;
import com.lexcv.services.fiscal.ReprocessamentoComunicacaoService;
import com.lexcv.services.fiscal.ResultadoNotaCredito;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 *
 * <p>Phase 135 (NCRD-01, T-135-31): as duas rotas da Nota de Crédito exigem a autoridade EXATA
 * {@code financeiro:manage}. No servidor não há equivalência {@code manage <= edit}: quem só tem
 * {@code financeiro:edit}, {@code financeiro:view}, os dois, ou nenhuma autoridade financeiro é
 * recusado nas duas e o serviço nunca é chamado.
 *
 * <p>Phase 136 (DFE-05, T-136-53): reprocessar a comunicação exige a autoridade EXATA
 * {@code financeiro:edit} (o frontend usa {@code podeReprocessarComunicacao}, a mesma regra exata).
 * {@code financeiro:manage} sozinho, {@code view}, {@code view + manage}, a autoridade com prefixo
 * {@code ROLE_} e nenhuma autoridade são recusados -- no servidor não há cadeia de equivalências.
 *
 * <p>Phase 137 (ENTR-04, T-137-46): reenviar o email exige a mesma autoridade EXATA
 * {@code financeiro:edit} (o frontend usa {@code podeReenviarEmail}), com os mesmos casos recusados.
 *
 * <p>Phase 137 (ENTR-02, T-137-60): descarregar o PDF e o XML exige a autoridade EXATA
 * {@code financeiro:view}, o mesmo gate do detalhe (o frontend usa {@code podeLerDocumentosFiscais});
 * {@code financeiro:edit} sozinho, {@code financeiro:manage} sozinho e nenhuma autoridade são
 * recusados e o serviço nunca é chamado.
 */
class DocumentoFiscalControllerAutorizacaoTest {

    private PreVisualizacaoFaturaService preVisualizacao;
    private DocumentoFiscalService documentos;
    private NotaCreditoService notasCredito;
    private ReprocessamentoComunicacaoService reprocessamento;
    private EfaturaGateway gateway;
    private ReenvioEmailFiscalService reenvio;
    private DescargaDocumentoFiscalService descarga;

    @BeforeEach
    void preparar() {
        preVisualizacao = mock(PreVisualizacaoFaturaService.class);
        documentos = mock(DocumentoFiscalService.class);
        notasCredito = mock(NotaCreditoService.class);
        reprocessamento = mock(ReprocessamentoComunicacaoService.class);
        gateway = mock(EfaturaGateway.class);
        reenvio = mock(ReenvioEmailFiscalService.class);
        descarga = mock(DescargaDocumentoFiscalService.class);
        when(gateway.ambiente()).thenReturn(AmbienteFiscal.SIMULADO);
        when(preVisualizacao.estadoEmissao(any())).thenReturn(EstadoEmissaoResponse.desligada());
        when(documentos.listar(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
    }

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private DocumentoFiscalController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(new DocumentoFiscalController(preVisualizacao, documentos, notasCredito,
                reprocessamento, gateway, reenvio, descarga));
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

    // ------------------------------------------------------------------ nota de crédito (Phase 135)

    private static NotaCreditoRequest pedidoNc() {
        return new NotaCreditoRequest("TOTAL", null, "ANULACAO_TOTAL", "Serviço não prestado", UUID.randomUUID());
    }

    private static Executable preVisualizarNc(DocumentoFiscalController p, UUID origem) {
        return () -> p.preVisualizarNotaCredito(origem.toString(), pedidoNc());
    }

    private static Executable emitirNc(DocumentoFiscalController p, UUID origem) {
        return () -> p.emitirNotaCredito(origem.toString(), pedidoNc());
    }

    @Test
    void manageExatoPreVisualizaEEmiteNotaCreditoComOTenantEOAutorDoPrincipal() {
        UserPrincipal principal = autenticarComAuthorities("financeiro:manage");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        UUID origem = UUID.randomUUID();
        when(notasCredito.emitir(any(), any(), any(), any())).thenReturn(
                ResultadoNotaCredito.novo(null));

        assertDoesNotThrow(preVisualizarNc(proxy, origem));
        assertDoesNotThrow(emitirNc(proxy, origem));

        verify(notasCredito).preVisualizar(eq(principal.getTenantId()), eq(origem), any(NotaCreditoRequest.class));
        verify(notasCredito).emitir(eq(principal.getTenantId()), eq(principal), eq(origem),
                any(NotaCreditoRequest.class));
    }

    static Stream<Arguments> semManage() {
        return Stream.of(
                Arguments.of((Object) new String[]{"financeiro:edit"}),
                Arguments.of((Object) new String[]{"financeiro:view"}),
                Arguments.of((Object) new String[]{"financeiro:view", "financeiro:edit"}),
                Arguments.of((Object) new String[]{"financeiro:view", "financeiro:create", "financeiro:edit"}),
                Arguments.of((Object) new String[]{"ROLE_financeiro:manage"}),
                Arguments.of((Object) new String[]{}));
    }

    @ParameterizedTest
    @MethodSource("semManage")
    void semManageExatoAPreVisualizacaoDaNcERecusada(String[] autoridades) {
        autenticarComAuthorities(autoridades);
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, preVisualizarNc(proxy, UUID.randomUUID()));
        verifyNoInteractions(notasCredito);
    }

    @ParameterizedTest
    @MethodSource("semManage")
    void semManageExatoAEmissaoDaNcERecusada(String[] autoridades) {
        autenticarComAuthorities(autoridades);
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, emitirNc(proxy, UUID.randomUUID()));
        verifyNoInteractions(notasCredito);
    }

    // ------------------------------------------------------------------ reprocessar comunicação (Phase 136)

    private static Executable reprocessar(DocumentoFiscalController p, UUID id) {
        return () -> p.reprocessarComunicacao(id.toString());
    }

    @Test
    void editExatoReprocessaComOTenantEOAutorDoPrincipal() {
        UserPrincipal principal = autenticarComAuthorities("financeiro:edit");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(reprocessar(proxy, id));

        verify(reprocessamento).reprocessar(principal.getTenantId(), principal, id);
    }

    static Stream<Arguments> semEditExato() {
        return Stream.of(
                Arguments.of((Object) new String[]{"financeiro:manage"}),
                Arguments.of((Object) new String[]{"financeiro:view"}),
                Arguments.of((Object) new String[]{"financeiro:view", "financeiro:manage"}),
                Arguments.of((Object) new String[]{"ROLE_financeiro:edit"}),
                Arguments.of((Object) new String[]{}));
    }

    @ParameterizedTest
    @MethodSource("semEditExato")
    void semEditExatoOReprocessamentoERecusado(String[] autoridades) {
        autenticarComAuthorities(autoridades);
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, reprocessar(proxy, UUID.randomUUID()));
        verifyNoInteractions(reprocessamento);
    }

    // ------------------------------------------------------------------ reenviar email (Phase 137)

    private static Executable reenviar(DocumentoFiscalController p, UUID id) {
        return () -> p.reenviarEmail(id.toString());
    }

    @Test
    void editExatoReenviaOEmailComOTenantEOAutorDoPrincipal() {
        UserPrincipal principal = autenticarComAuthorities("financeiro:edit");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(reenviar(proxy, id));

        verify(reenvio).reenviar(principal.getTenantId(), principal, id);
    }

    @ParameterizedTest
    @MethodSource("semEditExato")
    void semEditExatoOReenvioDeEmailERecusado(String[] autoridades) {
        autenticarComAuthorities(autoridades);
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, reenviar(proxy, UUID.randomUUID()));
        verifyNoInteractions(reenvio);
    }

    @Test
    void advogadoPorOmissaoNaoEmiteNotaCredito() {
        autenticarComAuthorities("ROLE_ADVOGADO", "financeiro:view", "processos:manage");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, preVisualizarNc(proxy, UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, emitirNc(proxy, UUID.randomUUID()));
        verifyNoInteractions(notasCredito);
    }

    // ------------------------------------------------------------------ descarregar PDF/XML (Phase 137)

    private static Executable descarregarPdf(DocumentoFiscalController p, String id) {
        return () -> p.descarregarPdf(id);
    }

    private static Executable descarregarXml(DocumentoFiscalController p, String id) {
        return () -> p.descarregarXml(id);
    }

    @Test
    void viewExatoDescarregaPdfEXmlComOTenantEOAutorDoPrincipal() {
        UserPrincipal principal = autenticarComAuthorities("financeiro:view");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        UUID id = UUID.randomUUID();
        when(descarga.descarregarPdf(any(), any(), any()))
                .thenReturn(new DescargaDocumentoFiscalService.DescargaPdf("https://minio/x", "FR-1.pdf", 3600L));
        when(descarga.descarregarXml(any(), any(), any()))
                .thenReturn(new DescargaDocumentoFiscalTransacoes.XmlDescarregavel(new byte[]{1}, "FR-1.xml"));

        assertDoesNotThrow(descarregarPdf(proxy, id.toString()));
        assertDoesNotThrow(descarregarXml(proxy, id.toString()));

        verify(descarga).descarregarPdf(principal.getTenantId(), principal, id);
        verify(descarga).descarregarXml(principal.getTenantId(), principal, id);
    }

    static Stream<Arguments> semViewExato() {
        return Stream.of(
                Arguments.of((Object) new String[]{"financeiro:edit"}),
                Arguments.of((Object) new String[]{"financeiro:manage"}),
                Arguments.of((Object) new String[]{"ROLE_financeiro:view"}),
                Arguments.of((Object) new String[]{}));
    }

    @ParameterizedTest
    @MethodSource("semViewExato")
    void semViewExatoAsDescargasSaoRecusadas(String[] autoridades) {
        autenticarComAuthorities(autoridades);
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, descarregarPdf(proxy, UUID.randomUUID().toString()));
        assertThrows(AccessDeniedException.class, descarregarXml(proxy, UUID.randomUUID().toString()));
        verifyNoInteractions(descarga);
    }

    @Test
    void idMalformadoNasDescargasDa404SemChamarOServico() {
        autenticarComAuthorities("financeiro:view");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();

        for (ResponseEntity<?> r : List.of(proxy.descarregarPdf("nao-e-uuid"), proxy.descarregarXml("nao-e-uuid"))) {
            assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
            assertEquals(Map.of("message", "Documento fiscal não encontrado.", "code", "DOCUMENTO_FISCAL_NAO_ENCONTRADO"),
                    r.getBody());
        }
        verifyNoInteractions(descarga);
    }

    @Test
    void pdfRespondeUrlNomeEValidade() {
        autenticarComAuthorities("financeiro:view");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        when(descarga.descarregarPdf(any(), any(), any()))
                .thenReturn(new DescargaDocumentoFiscalService.DescargaPdf("https://minio/x", "FR-2026A-000123.pdf", 900L));

        ResponseEntity<?> r = proxy.descarregarPdf(UUID.randomUUID().toString());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Map.of("url", "https://minio/x", "nomeFicheiro", "FR-2026A-000123.pdf", "expiresIn", 900L),
                r.getBody());
    }

    @Test
    void xmlRespondeAnexoUtf8ComONomeDoDocumento() {
        autenticarComAuthorities("financeiro:view");
        DocumentoFiscalController proxy = novoProxyComMethodSecurity();
        byte[] bytes = "<Dfe nome=\"Conceição\"/>".getBytes(StandardCharsets.UTF_8);
        when(descarga.descarregarXml(any(), any(), any()))
                .thenReturn(new DescargaDocumentoFiscalTransacoes.XmlDescarregavel(bytes, "FR-2026A-000123.xml"));

        ResponseEntity<?> r = proxy.descarregarXml(UUID.randomUUID().toString());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("application/xml;charset=UTF-8", r.getHeaders().getContentType().toString());
        String disposicao = r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposicao.startsWith("attachment; filename=\"FR-2026A-000123.xml\""), disposicao);
        assertArrayEquals(bytes, (byte[]) r.getBody());
    }

    // ------------------------------------------------------------------ matriz existente

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
