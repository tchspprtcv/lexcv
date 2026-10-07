package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.ConfiguracaoFiscalResponse;
import com.lexcv.dtos.EmailAutomaticoRequest;
import com.lexcv.dtos.MotivoIsencaoResponse;
import com.lexcv.dtos.SerieFiscalResponse;
import com.lexcv.models.RegimeIva;
import com.lexcv.services.fiscal.ConfiguracaoFiscalService;
import com.lexcv.exceptions.RecusaFiscalException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Delegacao direta (sem proxy) de {@link FaturacaoController} para
 * {@link ConfiguracaoFiscalService} (Phase 133, Plano 05, CFG-01/CFG-02/CFG-06) e nao-regressao
 * estrutural por reflexao: gate unico de classe, tenant nunca vindo do pedido, corpos validados,
 * nenhum caminho com "audit". O gate em si e provado comportamentalmente por proxy real em
 * {@link FaturacaoControllerAutorizacaoTest}.
 */
@ExtendWith(MockitoExtension.class)
class FaturacaoControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UTILIZADOR = UUID.randomUUID();

    @Mock private ConfiguracaoFiscalService configuracaoFiscalService;

    private FaturacaoController controller;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new FaturacaoController(configuracaoFiscalService);
        List<SimpleGrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("financeiro:manage"));
        principal = UserPrincipal.builder()
                .userId(UTILIZADOR)
                .tenantId(TENANT)
                .nome("Administradora Fiscal")
                .email("admin@escritorio-teste.cv")
                .roles(Set.of())
                .permissions(Set.of("financeiro:manage"))
                .authorities(autoridades)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, autoridades));
    }

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static ConfiguracaoFiscalResponse resposta() {
        return new ConfiguracaoFiscalResponse(true, "123456789", "Escritorio Teste", "Rua A", "Praia",
                ConfiguracaoFiscalResponse.PAIS, "geral@escritorio-teste.cv", "+238 9000000",
                RegimeIva.NORMAL, null, true, false, false, false, true, false, null, null, false);
    }

    private static ConfiguracaoFiscalRequest pedido() {
        return new ConfiguracaoFiscalRequest("123456789", "Escritorio Teste", "Rua A", "Praia",
                "geral@escritorio-teste.cv", "+238 9000000", RegimeIva.NORMAL, null);
    }

    @Test
    void getConfiguracao_delegaEmObterComTenantDoPrincipal() {
        ConfiguracaoFiscalResponse esperado = resposta();
        when(configuracaoFiscalService.obter(TENANT)).thenReturn(esperado);

        ResponseEntity<ConfiguracaoFiscalResponse> r = controller.getConfiguracao();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(esperado, r.getBody());
        verify(configuracaoFiscalService).obter(TENANT);
    }

    @Test
    void putConfiguracao_delegaEmGuardarComTenantEAutor() {
        ConfiguracaoFiscalRequest req = pedido();
        ConfiguracaoFiscalResponse esperado = resposta();
        when(configuracaoFiscalService.guardar(TENANT, principal, req)).thenReturn(esperado);

        ResponseEntity<ConfiguracaoFiscalResponse> r = controller.putConfiguracao(req);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(esperado, r.getBody());
        verify(configuracaoFiscalService).guardar(TENANT, principal, req);
    }

    @Test
    void postAtivar_delegaEmAtivar() {
        ConfiguracaoFiscalResponse esperado = resposta();
        when(configuracaoFiscalService.ativar(TENANT, principal)).thenReturn(esperado);

        ResponseEntity<ConfiguracaoFiscalResponse> r = controller.postAtivar();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(esperado, r.getBody());
    }

    @Test
    void postDesativar_delegaEmDesativar() {
        ConfiguracaoFiscalResponse esperado = resposta();
        when(configuracaoFiscalService.desativar(TENANT, principal)).thenReturn(esperado);

        ResponseEntity<ConfiguracaoFiscalResponse> r = controller.postDesativar();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(esperado, r.getBody());
    }

    @Test
    void putEmailAutomatico_delegaEmDefinirEmailAutomatico() {
        EmailAutomaticoRequest req = new EmailAutomaticoRequest(true, true);
        ConfiguracaoFiscalResponse esperado = resposta();
        when(configuracaoFiscalService.definirEmailAutomatico(TENANT, principal, req)).thenReturn(esperado);

        ResponseEntity<ConfiguracaoFiscalResponse> r = controller.putEmailAutomatico(req);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(esperado, r.getBody());
    }

    @Test
    void getSeries_delegaEmListarSeries() {
        List<SerieFiscalResponse> esperado = List.of();
        when(configuracaoFiscalService.listarSeries(TENANT)).thenReturn(esperado);

        ResponseEntity<List<SerieFiscalResponse>> r = controller.getSeries();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertSame(esperado, r.getBody());
    }

    @Test
    void getMotivosIsencao_devolveAListaOficialCom21Entradas() {
        ResponseEntity<List<MotivoIsencaoResponse>> r = controller.getMotivosIsencao();

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertNotNull(r.getBody());
        assertEquals(21, r.getBody().size());
        assertEquals("1", r.getBody().get(0).codigo());
        verifyNoInteractions(configuracaoFiscalService);
    }

    @Test
    void recusaFiscalDoServicoPropagaSemAlteracao() {
        RecusaFiscalException recusa = new RecusaFiscalException(HttpStatus.CONFLICT, "CONFIG_INCOMPLETA",
                "Configuracao incompleta.");
        when(configuracaoFiscalService.ativar(TENANT, principal)).thenThrow(recusa);

        RecusaFiscalException lancada = assertThrows(RecusaFiscalException.class, controller::postAtivar);

        assertSame(recusa, lancada);
    }

    // ---------------------------------------------------------------------------------------
    // Reflexao: estrutura do controller
    // ---------------------------------------------------------------------------------------

    private static List<Method> handlers() {
        List<Method> lista = new ArrayList<>();
        for (Method m : FaturacaoController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            if (m.isAnnotationPresent(GetMapping.class) || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(DeleteMapping.class)
                    || m.isAnnotationPresent(RequestMapping.class)) {
                lista.add(m);
            }
        }
        return lista;
    }

    private static List<String> caminhos(Method m) {
        List<String> c = new ArrayList<>();
        if (m.isAnnotationPresent(GetMapping.class)) {
            GetMapping a = m.getAnnotation(GetMapping.class);
            c.addAll(Arrays.asList(a.value()));
            c.addAll(Arrays.asList(a.path()));
        }
        if (m.isAnnotationPresent(PostMapping.class)) {
            PostMapping a = m.getAnnotation(PostMapping.class);
            c.addAll(Arrays.asList(a.value()));
            c.addAll(Arrays.asList(a.path()));
        }
        if (m.isAnnotationPresent(PutMapping.class)) {
            PutMapping a = m.getAnnotation(PutMapping.class);
            c.addAll(Arrays.asList(a.value()));
            c.addAll(Arrays.asList(a.path()));
        }
        return c;
    }

    @Test
    void existemExatamenteSeteHandlers() {
        assertEquals(7, handlers().size());
    }

    @Test
    void nenhumHandlerRecebeTenantOuIdDoPedido() {
        for (Method m : handlers()) {
            for (Parameter p : m.getParameters()) {
                assertFalse(p.getType().equals(UUID.class), "Parametro UUID em " + m.getName());
                assertFalse(p.isAnnotationPresent(PathVariable.class), "@PathVariable em " + m.getName());
                assertFalse(p.isAnnotationPresent(RequestParam.class), "@RequestParam em " + m.getName());
            }
        }
    }

    @Test
    void gateUnicoDeClasseFinanceiroManageSemAnotacaoDeMetodo() {
        PreAuthorize classe = FaturacaoController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(classe);
        assertEquals("hasAuthority('financeiro:manage')", classe.value());
        for (Method m : FaturacaoController.class.getDeclaredMethods()) {
            assertNull(m.getAnnotation(PreAuthorize.class),
                    "@PreAuthorize de metodo substituiria o gate de classe: " + m.getName());
        }
    }

    @Test
    void mapeamentoBaseENenhumCaminhoContemAudit() {
        RequestMapping base = FaturacaoController.class.getAnnotation(RequestMapping.class);
        assertNotNull(base);
        assertEquals(List.of("/api/v1/faturacao"), Arrays.asList(base.value()));
        for (Method m : handlers()) {
            for (String c : caminhos(m)) {
                assertFalse(c.toLowerCase(Locale.ROOT).contains("audit"), "Caminho com 'audit': " + c);
            }
        }
    }

    @Test
    void corposDosPutSaoValidadosERequestBody() throws NoSuchMethodException {
        Method putConfig = FaturacaoController.class.getMethod("putConfiguracao", ConfiguracaoFiscalRequest.class);
        Method putEmail = FaturacaoController.class.getMethod("putEmailAutomatico", EmailAutomaticoRequest.class);
        for (Method m : List.of(putConfig, putEmail)) {
            Parameter p = m.getParameters()[0];
            assertTrue(p.isAnnotationPresent(Valid.class), "@Valid em falta: " + m.getName());
            assertTrue(p.isAnnotationPresent(RequestBody.class), "@RequestBody em falta: " + m.getName());
        }
    }
}
