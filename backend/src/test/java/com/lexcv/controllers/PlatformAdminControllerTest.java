package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.dtos.TenantAdminSummaryResponse;
import com.lexcv.dtos.TenantProvisionResponse;
import com.lexcv.dtos.TenantUpdateRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantPlano;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.SetupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova o comportamento de {@link PlatformAdminController} em dois grupos, agora cobrindo os 4
 * handlers do controller (o {@code createTenant} original da Phase 119 mais os 3 acrescentados
 * pela Phase 120 Plan 02: {@code listTenants}, {@code updateTenant}, {@code setTenantAtivo}).
 *
 * <p>Grupo A (instanciação direta, sem proxy) segue a mesma convenção de todos os testes de
 * controller deste codebase (ver {@code AdminControllerLimiteUtilizadoresTest}): não existe
 * nenhum harness de contexto Spring nem de simulação de pedidos HTTP neste projeto -- o
 * controller é instanciado diretamente com {@link SetupService}, {@link TenantRepository} e
 * {@link UserRepository} mockados via Mockito, e o método sob teste é invocado como uma chamada
 * Java simples.
 *
 * <p>Grupo B precisa de algo mais: uma chamada Java direta ao controller NUNCA avalia
 * {@code @PreAuthorize} -- essa anotação só é interpretada por um proxy AOP de method security.
 * Sem esse proxy, os Success Criteria desta fase que exigem que um ADMIN de tenant normal nunca
 * alcance nenhum dos 4 handlers não teriam nenhuma prova comportamental, apenas uma leitura por
 * reflexão da string da anotação. Por isso estes casos envolvem o controller num
 * {@link ProxyFactory} CGLIB ({@code setProxyTargetClass(true)}, obrigatório porque
 * {@code PlatformAdminController} não implementa nenhuma interface) montado com o mesmo
 * {@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()} que o Spring Security usa
 * em produção via {@code @EnableMethodSecurity}, e povoam o {@link SecurityContextHolder}
 * manualmente antes de cada chamada, limpando-o em {@code @AfterEach} (como em
 * {@code AdminControllerLimiteUtilizadoresTest}). Isto continua a não introduzir nenhum harness
 * de contexto Spring nem de simulação de pedidos HTTP neste projeto.
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminControllerTest {

    @Mock
    private SetupService setupService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private SetupInitializeRequest pedidoValido() {
        SetupInitializeRequest request = new SetupInitializeRequest();
        request.setClientName("Escritorio Novo");
        request.setAdminEmail("admin@escritorionovo.cv");
        request.setAdminPassword("Pa$$w0rd1");
        return request;
    }

    private PlatformAdminController novoController() {
        return new PlatformAdminController(setupService, tenantRepository, userRepository);
    }

    /**
     * Monta o proxy AOP de method security necessário para o Grupo B -- ver o Javadoc de
     * classe acima para a justificação completa.
     */
    private PlatformAdminController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (PlatformAdminController) factory.getProxy();
    }

    private void autenticarComoRoles(String... roles) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
    }

    private void autenticarComoPlataformaAdmin(UUID tenantIdReservado) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID())
                .tenantId(tenantIdReservado)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATAFORMA_ADMIN"))));
    }

    // ---- Grupo A: comportamento do handler (instanciação direta, sem proxy) ----

    @Test
    void createTenant_devolve201ComIdENomeSemEntidadeCrua() {
        SetupInitializeRequest request = pedidoValido();
        UUID tenantId = UUID.randomUUID();
        Tenant tenantCriado = Tenant.builder().id(tenantId).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(request)).thenReturn(tenantCriado);

        ResponseEntity<?> response = novoController().createTenant(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertInstanceOf(TenantProvisionResponse.class, response.getBody());
        assertFalse(response.getBody() instanceof Tenant);
        TenantProvisionResponse body = (TenantProvisionResponse) response.getBody();
        assertEquals(tenantId, body.getId());
        assertEquals("Escritorio Novo", body.getNome());
    }

    @Test
    void createTenant_comIllegalArgumentExceptionDevolve400() {
        SetupInitializeRequest request = pedidoValido();
        when(setupService.provisionTenant(request))
                .thenThrow(new IllegalArgumentException("O email do administrador é inválido."));

        ResponseEntity<?> response = novoController().createTenant(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "O email do administrador é inválido."), response.getBody());
    }

    @Test
    void createTenant_comIllegalStateExceptionDevolve403() {
        SetupInitializeRequest request = pedidoValido();
        when(setupService.provisionTenant(request))
                .thenThrow(new IllegalStateException("O papel ADMIN não está configurado."));

        ResponseEntity<?> response = novoController().createTenant(request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of("message", "O papel ADMIN não está configurado."), response.getBody());
    }

    // WR-02 (119-REVIEW.md): TOCTOU no pre-check de email de provisionTenant -- o pedido
    // perdedor de uma corrida concorrente com o mesmo adminEmail so falha no commit/flush da
    // transacao (User.id usa GenerationType.UUID, sem round-trip a BD antes disso), pelo que a
    // DataIntegrityViolationException so pode ser apanhada aqui, a volta desta chamada, nunca
    // dentro do proprio metodo do servico. Sem este catch, cairia no handler global 500.
    @Test
    void createTenant_comDataIntegrityViolationExceptionDevolve400ComMensagemDeEmailDuplicado() {
        SetupInitializeRequest request = pedidoValido();
        when(setupService.provisionTenant(request))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ResponseEntity<?> response = novoController().createTenant(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "Já existe um utilizador com este email."), response.getBody());
    }

    @Test
    void createTenant_delegaEmProvisionTenantNuncaEmInitializeSystemOuIsInitialized() {
        SetupInitializeRequest request = pedidoValido();
        Tenant tenantCriado = Tenant.builder().id(UUID.randomUUID()).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(request)).thenReturn(tenantCriado);

        novoController().createTenant(request);

        verify(setupService, times(1)).provisionTenant(any());
        verify(setupService, never()).initializeSystem(any());
        verify(setupService, never()).isInitialized();
    }

    // ---- Grupo B: gate de autorização (proxy real de method security) ----

    @Test
    void createTenant_comRoleAdminDeTenantNormalERecusadoAntesDeOMetodoCorrer() {
        autenticarComoRoles("ADMIN");
        SetupInitializeRequest request = pedidoValido();
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.createTenant(request));
        verify(setupService, never()).provisionTenant(any());
    }

    @Test
    void createTenant_comRolePlataformaAdminPassaOGateEDevolve201() {
        autenticarComoPlataformaAdmin(UUID.randomUUID());
        SetupInitializeRequest request = pedidoValido();
        Tenant tenantCriado = Tenant.builder().id(UUID.randomUUID()).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(any())).thenReturn(tenantCriado);
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.createTenant(request));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void anotacaoDeClasseTemValorExatoHasRolePlataformaAdmin() {
        PreAuthorize anotacao = PlatformAdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(anotacao);
        assertEquals("hasRole('PLATAFORMA_ADMIN')", anotacao.value());
    }

    @Test
    void createTenant_naoLeTenantIdDoSecurityContextEDelegaComOMesmoObjetoDePedido() {
        // tenantId da tenant reservada "LexCV" (Plan 01) -- propositadamente diferente do
        // tenant que viria a ser criado; se o controller alguma vez ler
        // UserPrincipal.getTenantId() e o injetar no pedido, este teste passaria a falhar.
        autenticarComoPlataformaAdmin(UUID.randomUUID());
        SetupInitializeRequest request = pedidoValido();
        Tenant tenantCriado = Tenant.builder().id(UUID.randomUUID()).nome("Escritorio Novo").build();
        when(setupService.provisionTenant(any())).thenReturn(tenantCriado);
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        proxy.createTenant(request);

        verify(setupService).provisionTenant(same(request));
    }

    // ---- Grupo A: comportamento de listTenants/updateTenant/setTenantAtivo (Phase 120 Plan 02) ----

    @Test
    void listTenants_devolve200ComUmResumoPorTenantComAContagemDoSeuProprioId() {
        UUID tenantAId = UUID.randomUUID();
        UUID tenantBId = UUID.randomUUID();
        Tenant tenantA = Tenant.builder().id(tenantAId).nome("Escritorio A").plano(TenantPlano.STARTER)
                .limiteUtilizadores(5).ativo(true).build();
        Tenant tenantB = Tenant.builder().id(tenantBId).nome("Escritorio B").plano(TenantPlano.STANDARD)
                .limiteUtilizadores(null).ativo(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantA, tenantB));
        when(userRepository.countByTenantIdAndAtivoTrue(tenantAId)).thenReturn(3L);
        when(userRepository.countByTenantIdAndAtivoTrue(tenantBId)).thenReturn(7L);

        ResponseEntity<?> response = novoController().listTenants();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<TenantAdminSummaryResponse> corpo = (List<TenantAdminSummaryResponse>) response.getBody();
        assertNotNull(corpo);
        assertEquals(2, corpo.size());
        TenantAdminSummaryResponse resumoA = corpo.stream().filter(r -> r.getId().equals(tenantAId)).findFirst().orElseThrow();
        TenantAdminSummaryResponse resumoB = corpo.stream().filter(r -> r.getId().equals(tenantBId)).findFirst().orElseThrow();
        assertEquals(3L, resumoA.getUtilizadoresAtivos());
        assertEquals(7L, resumoB.getUtilizadoresAtivos());
    }

    @Test
    void listTenants_nuncaDevolveEntidadesCruas() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).nome("Escritorio C").plano(TenantPlano.ENTERPRISE)
                .ativo(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenant));
        when(userRepository.countByTenantIdAndAtivoTrue(any())).thenReturn(0L);

        ResponseEntity<?> response = novoController().listTenants();

        List<?> corpo = (List<?>) response.getBody();
        assertNotNull(corpo);
        corpo.forEach(item -> assertFalse(item instanceof Tenant));
    }

    @Test
    void listTenants_devolveOrdenadoPorNomeCaseInsensitiveMesmoQuandoFindAllDevolveForaDeOrdem() {
        // "bravo" (minusculo) vs "Alfa"/"Charlie" (maiusculo): a ordenacao natural de String
        // (case-sensitive) poria "bravo" DEPOIS de "Charlie" (letras minusculas > maiusculas em
        // ASCII) -- so uma comparacao case-insensitive real produz Alfa, bravo, Charlie.
        Tenant tenantCharlie = Tenant.builder().id(UUID.randomUUID()).nome("Charlie Advogados")
                .plano(TenantPlano.STARTER).ativo(true).build();
        Tenant tenantAlfa = Tenant.builder().id(UUID.randomUUID()).nome("Alfa Advogados")
                .plano(TenantPlano.STARTER).ativo(true).build();
        Tenant tenantBravo = Tenant.builder().id(UUID.randomUUID()).nome("bravo Advogados")
                .plano(TenantPlano.STARTER).ativo(true).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantCharlie, tenantAlfa, tenantBravo));
        when(userRepository.countByTenantIdAndAtivoTrue(any())).thenReturn(0L);

        ResponseEntity<?> response = novoController().listTenants();

        @SuppressWarnings("unchecked")
        List<TenantAdminSummaryResponse> corpo = (List<TenantAdminSummaryResponse>) response.getBody();
        assertNotNull(corpo);
        assertEquals(List.of("Alfa Advogados", "bravo Advogados", "Charlie Advogados"),
                corpo.stream().map(TenantAdminSummaryResponse::getNome).toList());
    }

    /**
     * Guarda o criterio de sucesso 3 da Phase 122: o relatorio de utilizacao por tenant depende
     * de um escritorio suspenso continuar a aparecer na resposta deste endpoint, com o seu
     * estado transportado ao cliente -- um escritorio suspenso continua a precisar de ser
     * faturado ou reconciliado, nao pode simplesmente desaparecer da base factual.
     *
     * <p>A implementacao atual ja cumpre isto por ausencia de qualquer condicao no pipeline de
     * {@code listTenants()} -- por isso este teste passa de imediato, sem nenhuma alteracao de
     * producao. O seu valor esta em impedir que uma refatoracao futura introduza silenciosamente
     * essa condicao: os 3 testes {@code listTenants_*} anteriores construiam todos os seus
     * fixtures com o mesmo estado, deixando esta propriedade sem nenhuma prova ate agora.
     */
    @Test
    void listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta() {
        UUID tenantAtivoId = UUID.randomUUID();
        UUID tenantSuspensoId = UUID.randomUUID();
        Tenant tenantAtivo = Tenant.builder().id(tenantAtivoId).nome("Escritorio Ativo")
                .plano(TenantPlano.STARTER).ativo(true).build();
        Tenant tenantSuspenso = Tenant.builder().id(tenantSuspensoId).nome("Escritorio Suspenso")
                .plano(TenantPlano.STANDARD).ativo(false).build();
        when(tenantRepository.findAll()).thenReturn(List.of(tenantAtivo, tenantSuspenso));
        when(userRepository.countByTenantIdAndAtivoTrue(any())).thenReturn(0L);

        ResponseEntity<?> response = novoController().listTenants();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<TenantAdminSummaryResponse> corpo = (List<TenantAdminSummaryResponse>) response.getBody();
        assertNotNull(corpo);
        assertEquals(2, corpo.size());
        TenantAdminSummaryResponse resumoSuspenso = corpo.stream()
                .filter(r -> r.getId().equals(tenantSuspensoId))
                .findFirst()
                .orElseThrow();
        assertEquals(false, resumoSuspenso.getAtivo());
    }

    @Test
    void updateTenant_comPlanoELimiteValidosDevolve200EGravaComAtivoInalterado() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenantExistente = Tenant.builder().id(tenantId).nome("Escritorio D").plano(TenantPlano.STARTER)
                .limiteUtilizadores(3).ativo(true).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantExistente));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.countByTenantIdAndAtivoTrue(tenantId)).thenReturn(2L);
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setPlano(TenantPlano.STANDARD);
        request.setLimiteUtilizadores(10);

        ResponseEntity<?> response = novoController().updateTenant(tenantId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository, times(1)).save(captor.capture());
        Tenant gravado = captor.getValue();
        assertEquals(TenantPlano.STANDARD, gravado.getPlano());
        assertEquals(10, gravado.getLimiteUtilizadores());
        assertEquals(true, gravado.getAtivo());
    }

    @Test
    void updateTenant_comLimiteUtilizadoresNuloDevolve200EGravaNulo() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenantExistente = Tenant.builder().id(tenantId).nome("Escritorio E").plano(TenantPlano.STARTER)
                .limiteUtilizadores(5).ativo(true).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantExistente));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.countByTenantIdAndAtivoTrue(tenantId)).thenReturn(0L);
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setPlano(TenantPlano.ENTERPRISE);
        request.setLimiteUtilizadores(null);

        ResponseEntity<?> response = novoController().updateTenant(tenantId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(captor.capture());
        assertEquals(null, captor.getValue().getLimiteUtilizadores());
    }

    @Test
    void updateTenant_comLimiteUtilizadoresZeroDevolve400ENuncaChamaSave() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenantExistente = Tenant.builder().id(tenantId).nome("Escritorio F").plano(TenantPlano.STARTER)
                .ativo(true).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantExistente));
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setPlano(TenantPlano.STARTER);
        request.setLimiteUtilizadores(0);

        ResponseEntity<?> response = novoController().updateTenant(tenantId, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void updateTenant_comPlanoNuloDevolve400ENuncaChamaSave() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenantExistente = Tenant.builder().id(tenantId).nome("Escritorio G").plano(TenantPlano.STARTER)
                .ativo(true).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantExistente));
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setPlano(null);
        request.setLimiteUtilizadores(5);

        ResponseEntity<?> response = novoController().updateTenant(tenantId, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void updateTenant_comIdInexistenteDevolve404() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setPlano(TenantPlano.STARTER);
        request.setLimiteUtilizadores(5);

        ResponseEntity<?> response = novoController().updateTenant(tenantId, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void setTenantAtivo_comFalseSobreTenantNormalDevolve200EGravaAtivoFalse() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenantExistente = Tenant.builder().id(tenantId).nome("Escritorio H").plano(TenantPlano.STARTER)
                .ativo(true).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantExistente));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.countByTenantIdAndAtivoTrue(tenantId)).thenReturn(0L);

        ResponseEntity<?> response = novoController().setTenantAtivo(tenantId, Map.of("ativo", false));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(captor.capture());
        assertEquals(false, captor.getValue().getAtivo());
    }

    @Test
    void setTenantAtivo_comFalseSobreLexCVDevolve400ComMensagemExataENuncaChamaSave() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenantLexCV = Tenant.builder().id(tenantId).nome("LexCV").plano(TenantPlano.ENTERPRISE)
                .ativo(true).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantLexCV));

        ResponseEntity<?> response = novoController().setTenantAtivo(tenantId, Map.of("ativo", false));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "Não é possível suspender o tenant da plataforma (LexCV)."), response.getBody());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void setTenantAtivo_comTrueSobreLexCVDevolve200EGrava() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenantLexCV = Tenant.builder().id(tenantId).nome("LexCV").plano(TenantPlano.ENTERPRISE)
                .ativo(false).build();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenantLexCV));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.countByTenantIdAndAtivoTrue(tenantId)).thenReturn(1L);

        ResponseEntity<?> response = novoController().setTenantAtivo(tenantId, Map.of("ativo", true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tenantRepository, times(1)).save(any(Tenant.class));
    }

    @Test
    void setTenantAtivo_comStringEmVezDeBooleanDevolve400ENuncaChamaSave() {
        UUID tenantId = UUID.randomUUID();

        ResponseEntity<?> response = novoController().setTenantAtivo(tenantId, Map.of("ativo", "sim"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "O campo ativo deve ser um valor booleano."), response.getBody());
        verify(tenantRepository, never()).findById(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void setTenantAtivo_comIdInexistenteDevolve404() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        ResponseEntity<?> response = novoController().setTenantAtivo(tenantId, Map.of("ativo", true));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(tenantRepository, never()).save(any());
    }

    // ---- Grupo B: gate de autorização real dos 3 handlers novos (Phase 120 Plan 02) ----

    @Test
    void listTenants_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios() {
        autenticarComoRoles("ADMIN");
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, proxy::listTenants);
        verify(tenantRepository, never()).findAll();
        verify(userRepository, never()).countByTenantIdAndAtivoTrue(any());
    }

    @Test
    void updateTenant_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios() {
        autenticarComoRoles("ADMIN");
        UUID tenantId = UUID.randomUUID();
        TenantUpdateRequest request = new TenantUpdateRequest();
        request.setPlano(TenantPlano.STARTER);
        request.setLimiteUtilizadores(5);
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.updateTenant(tenantId, request));
        verify(tenantRepository, never()).findById(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void setTenantAtivo_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios() {
        autenticarComoRoles("ADMIN");
        UUID tenantId = UUID.randomUUID();
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.setTenantAtivo(tenantId, Map.of("ativo", false)));
        verify(tenantRepository, never()).findById(any());
        verify(tenantRepository, never()).save(any());
    }

    // ---- Contenção estrutural: nenhum handler alcança Role/Permission (Phase 120 Plan 02) ----

    @Test
    void nenhumMetodoPublicoDeclaraParametroDoTipoRoleOuPermission() {
        for (Method metodo : PlatformAdminController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(metodo.getModifiers())) {
                continue;
            }
            for (Class<?> tipoParametro : metodo.getParameterTypes()) {
                assertFalse(tipoParametro.equals(Role.class) || tipoParametro.equals(Permission.class),
                        "Metodo " + metodo.getName() + " nao deve declarar parametro do tipo Role/Permission");
            }
        }
    }
}
