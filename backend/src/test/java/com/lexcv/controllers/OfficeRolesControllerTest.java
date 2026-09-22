package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PapelCreateRequest;
import com.lexcv.dtos.PapelRenameRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.AuditoriaRbacService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 127 (Plano 04): prova de comportamento, por proxy real de method security (nunca por
 * reflexão sobre a anotação para os casos comportamentais), de que {@link OfficeRolesController}
 * está gateado por AUTORIDADE de permissão ({@code hasAuthority('rbac:manage')}) ao nível de
 * CLASSE nos três verbos, mais os dois refusais de apagar (PAPEL-05/PAPEL-08), o isolamento
 * cross-tenant (PAPEL-07) e a preservação de maiúsculas/minúsculas (PAPEL-02/PAPEL-04).
 *
 * <p>Segue exatamente as convenções de {@code AdminControllerRbacAutorizacaoTest} (proxy
 * {@link ProxyFactory} + {@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()}) e de
 * {@code AdminControllerAtribuicaoPapeisEscritorioTest} ({@code lenient()} +
 * {@code verify(..., never())} para provar isolamento multi-tenant por ausência de chamada).
 */
@ExtendWith(MockitoExtension.class)
class OfficeRolesControllerTest {

    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final Integer ADMIN_MOLDE_ID = 1;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private OfficeRolesController novoController() {
        return new OfficeRolesController(
                tenantRoleRepository, userRepository, roleRepository, permissionRepository, auditoriaRbacService);
    }

    /**
     * Monta o proxy AOP de method security necessário para avaliar {@code @PreAuthorize} de
     * facto -- uma chamada Java direta a {@link #novoController()} nunca o faz. Cópia exata do
     * padrão estabelecido em {@code AdminControllerRbacAutorizacaoTest.novoProxyComMethodSecurity()}.
     */
    private OfficeRolesController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (OfficeRolesController) factory.getProxy();
    }

    /**
     * Autentica com uma lista crua de autoridades, sem {@link UserPrincipal} (o principal fica
     * {@code null}) -- correta apenas para os cenários RECUSADOS, cuja execução nunca alcança o
     * corpo do handler (o gate intercepta antes de {@code getTenantId()} ser chamado).
     */
    private void autenticarComoAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
    }

    /**
     * Autentica com um {@link UserPrincipal} real do tenant dado -- exigido por qualquer cenário
     * de SUCESSO, já que todos os handlers desta classe leem
     * {@code SecurityContextHolder} -> {@code principal.getTenantId()}.
     */
    private void autenticarComoPrincipalDoTenant(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID()).tenantId(tenantId)
                .nome("Principal de Teste").email("principal@escritorio-teste.cv")
                .roles(Set.of()).permissions(Set.of())
                .authorities(List.of(new SimpleGrantedAuthority("rbac:manage")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Permission permissao(String nome) {
        return Permission.builder().nome(nome).reservadaPlataforma(false).build();
    }

    /**
     * Resposta padrão para {@code tenantRoleRepository.saveAndFlush(any())} num teste de criação: devolve
     * a mesma entidade passada, atribuindo-lhe um id gerado se ainda não tiver um -- imita o
     * comportamento real do Hibernate (id gerado ao gravar) sem o qual
     * {@code Map.of("id", ..., "nome", ...)} no handler rebentaria com {@code NullPointerException}
     * (Map.of não aceita valores nulos).
     */
    private TenantRole simularGravacaoComIdGerado(TenantRole tenantRole) {
        if (tenantRole.getId() == null) {
            tenantRole.setId(UUID.randomUUID());
        }
        return tenantRole;
    }

    // ---------------------------------------------------------------------------------------
    // Caso 1: o gate é uma AUTORIDADE, não um papel -- provado nos três verbos.
    // ---------------------------------------------------------------------------------------

    @Test
    void createRole_comAutoridadeRbacManageCruaObtemSucesso() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(tenantRoleRepository.findByTenantIdAndNome(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> {
            TenantRole tr = inv.getArgument(0);
            tr.setId(UUID.randomUUID());
            return tr;
        });
        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Recepção");
        OfficeRolesController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.createRole(request));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createRole_comApenasRoleAdminERecusado() {
        autenticarComoAuthorities("ROLE_ADMIN");
        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Papel Qualquer");
        OfficeRolesController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.createRole(request));
        verify(tenantRoleRepository, never()).saveAndFlush(any());
    }

    // Armadilha hasAuthority-vs-hasRole (127-CONTEXT.md Decisão 3): "ROLE_rbac:manage" é a forma
    // ERRADA (o que hasRole('rbac:manage') procuraria); com o gate correto (hasAuthority), esta
    // forma tem de continuar recusada em todos os três verbos.
    @Test
    void renameRole_comAutoridadePrefixadaRoleRbacManageERecusado() {
        autenticarComoAuthorities("ROLE_rbac:manage");
        PapelRenameRequest request = new PapelRenameRequest();
        request.setNome("Novo Nome");
        UUID id = UUID.randomUUID();
        OfficeRolesController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.renameRole(id, request));
        verify(tenantRoleRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteRole_comApenasRoleAdminERecusado() {
        autenticarComoAuthorities("ROLE_ADMIN");
        UUID id = UUID.randomUUID();
        OfficeRolesController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.deleteRole(id));
        verify(tenantRoleRepository, never()).deleteById(any());
    }

    // ---------------------------------------------------------------------------------------
    // create (PAPEL-02, PAPEL-07)
    // ---------------------------------------------------------------------------------------

    // Caso 2: a escrita pertence ao tenant do chamador -- tenantId capturado igual ao do
    // principal, moldeId nulo, sistema falso; o tenant de outro escritório nunca é consultado.
    @Test
    void createRole_gravaNoTenantDoChamadorComMoldeIdNuloESistemaFalso() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(tenantRoleRepository.findByTenantIdAndNome(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));

        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Financeiro Sénior");

        ResponseEntity<?> response = novoController().createRole(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository).saveAndFlush(captor.capture());
        TenantRole gravado = captor.getValue();
        assertEquals(TENANT_ID, gravado.getTenantId());
        assertNull(gravado.getMoldeId());
        assertFalse(gravado.getSistema());
        verify(tenantRoleRepository, never()).findByTenantIdAndNome(eq(OUTRO_TENANT_ID), any());
    }

    // Caso 3: o nome é gravado exatamente como submetido -- nunca convertido para maiúsculas,
    // ao contrário de PlatformAdminController.createMolde.
    @Test
    void createRole_preservaMaiusculasEMinusculasDoNomeSubmetido() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(tenantRoleRepository.findByTenantIdAndNome(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));

        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Recepção");

        novoController().createRole(request);

        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository).saveAndFlush(captor.capture());
        assertEquals("Recepção", captor.getValue().getNome());
    }

    // Caso 4: as duas formas reservadas são recusadas, de forma case-insensitive, sem nenhuma
    // escrita.
    @Test
    void createRole_recusaNomeReservadoEmQualquerFormaOuCaixa() {
        autenticarComoPrincipalDoTenant(TENANT_ID);

        for (String nomeReservado : List.of("PLATAFORMA_ADMIN", "plataforma_admin", "ROLE_PLATAFORMA_ADMIN")) {
            PapelCreateRequest request = new PapelCreateRequest();
            request.setNome(nomeReservado);

            ResponseEntity<?> response = novoController().createRole(request);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), "falhou para: " + nomeReservado);
        }
        verify(tenantRoleRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // Caso 5: uma chave de permissão fora do catálogo servido é recusada com 400, nenhuma
    // escrita.
    @Test
    void createRole_recusaChaveDePermissaoDesconhecida() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(tenantRoleRepository.findByTenantIdAndNome(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());

        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Papel Novo");
        request.setPermissoes(List.of("chave:inexistente"));

        ResponseEntity<?> response = novoController().createRole(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.contains("Permissão desconhecida"));
        verify(tenantRoleRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // Caso 6: um nome duplicado no MESMO tenant é recusado com 409, sem escrita; o mesmo nome só
    // colidindo NOUTRO tenant é aceite -- prova que a unicidade é por-tenant.
    @Test
    void createRole_recusaDuplicadoNoMesmoTenantMasAceitaColisaoNoutroTenant() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "ADVOGADO"))
                .thenReturn(Optional.of(TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO").build()));

        PapelCreateRequest requestDuplicado = new PapelCreateRequest();
        requestDuplicado.setNome("ADVOGADO");
        ResponseEntity<?> respostaDuplicado = novoController().createRole(requestDuplicado);
        assertEquals(HttpStatus.CONFLICT, respostaDuplicado.getStatusCode());
        verify(tenantRoleRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoriaRbacService);

        // O mesmo nome, mas colidindo apenas noutro tenant -- deve ser aceite.
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "ASSISTENTE")).thenReturn(Optional.empty());
        lenient().when(tenantRoleRepository.findByTenantIdAndNome(OUTRO_TENANT_ID, "ASSISTENTE"))
                .thenReturn(Optional.of(TenantRole.builder().id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ASSISTENTE").build()));
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));

        PapelCreateRequest requestNoutroTenant = new PapelCreateRequest();
        requestNoutroTenant.setNome("ASSISTENTE");
        ResponseEntity<?> respostaAceite = novoController().createRole(requestNoutroTenant);
        assertEquals(HttpStatus.CREATED, respostaAceite.getStatusCode());
        verify(tenantRoleRepository, never()).findByTenantIdAndNome(eq(OUTRO_TENANT_ID), eq("ASSISTENTE"));
    }

    // ---------------------------------------------------------------------------------------
    // rename (PAPEL-04, PAPEL-08)
    // ---------------------------------------------------------------------------------------

    // Caso 7: renomear o papel PROTEGIDO é permitido -- permissions/moldeId/sistema ficam
    // intocados.
    @Test
    void renameRole_doPapelProtegidoEPermitidoENuncaTocaEmPermissoesMoldeIdOuSistema() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        Set<Permission> permissoesOriginais = new HashSet<>(Set.of(permissao("rbac:manage"), permissao("users:manage")));
        TenantRole admin = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADMIN")
                .moldeId(ADMIN_MOLDE_ID).sistema(true)
                .permissions(permissoesOriginais)
                .build();
        when(tenantRoleRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "Administrador do Escritório")).thenReturn(Optional.empty());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));

        PapelRenameRequest request = new PapelRenameRequest();
        request.setNome("Administrador do Escritório");

        ResponseEntity<?> response = novoController().renameRole(admin.getId(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository).saveAndFlush(captor.capture());
        TenantRole gravado = captor.getValue();
        assertEquals("Administrador do Escritório", gravado.getNome());
        assertEquals(ADMIN_MOLDE_ID, gravado.getMoldeId());
        assertTrue(gravado.getSistema());
        assertEquals(permissoesOriginais, gravado.getPermissions());
    }

    // Caso 8: um id de OUTRO tenant é recusado com 404, sem escrita, e a entidade do outro tenant
    // fica inalterada.
    @Test
    void renameRole_idDeOutroTenantERecusadoComQuatroZeroQuatroENuncaGrava() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole papelDoOutroTenant = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ADVOGADO").build();
        when(tenantRoleRepository.findById(papelDoOutroTenant.getId())).thenReturn(Optional.of(papelDoOutroTenant));

        PapelRenameRequest request = new PapelRenameRequest();
        request.setNome("Nome Tentado");

        ResponseEntity<?> response = novoController().renameRole(papelDoOutroTenant.getId(), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(tenantRoleRepository, never()).saveAndFlush(any());
        assertEquals("ADVOGADO", papelDoOutroTenant.getNome());
        verifyNoInteractions(auditoriaRbacService);
    }

    // ---------------------------------------------------------------------------------------
    // delete (PAPEL-05, PAPEL-08, PAPEL-07)
    // ---------------------------------------------------------------------------------------

    // Caso 9 (PAPEL-05): um papel atribuído a 2 utilizadores é recusado com 409 nomeando o
    // número, sem apagar.
    @Test
    void deleteRole_recusaPapelAtribuidoComContagemNaMensagem() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole assistente = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ASSISTENTE").build();
        when(tenantRoleRepository.findById(assistente.getId())).thenReturn(Optional.of(assistente));
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());
        when(userRepository.countByTenantRolesId(assistente.getId())).thenReturn(2L);

        ResponseEntity<?> response = novoController().deleteRole(assistente.getId());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.contains("2"));
        verify(tenantRoleRepository, never()).deleteById(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // Caso 10 (PAPEL-08): o papel protegido é recusado mesmo SEM nenhuma atribuição e mesmo
    // RENOMEADO -- "Administradores" já não é "ADMIN"; só a proveniência (moldeId) o protege.
    // Este é o caso que falha se a guarda for alguma vez reescrita como comparação de nome.
    @Test
    void deleteRole_recusaPapelProtegidoMesmoRenomeadoESemAtribuicoes() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole administradoresRenomeado = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Administradores")
                .moldeId(ADMIN_MOLDE_ID)
                .build();
        when(tenantRoleRepository.findById(administradoresRenomeado.getId())).thenReturn(Optional.of(administradoresRenomeado));
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());
        lenient().when(userRepository.countByTenantRolesId(administradoresRenomeado.getId())).thenReturn(0L);

        ResponseEntity<?> response = novoController().deleteRole(administradoresRenomeado.getId());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(tenantRoleRepository, never()).deleteById(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // Caso 11 (PAPEL-07): um id de OUTRO tenant é recusado com 404, sem apagar e sem sequer
    // consultar a contagem de atribuições.
    @Test
    void deleteRole_idDeOutroTenantERecusadoComQuatroZeroQuatroENuncaConsultaContagem() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole papelDoOutroTenant = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ADVOGADO").build();
        when(tenantRoleRepository.findById(papelDoOutroTenant.getId())).thenReturn(Optional.of(papelDoOutroTenant));

        ResponseEntity<?> response = novoController().deleteRole(papelDoOutroTenant.getId());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(tenantRoleRepository, never()).deleteById(any());
        verify(userRepository, never()).countByTenantRolesId(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // Caso 12: um papel sem atribuições e sem proveniência protegida é apagado com sucesso -- 204
    // e exatamente uma chamada a deleteById(id).
    @Test
    void deleteRole_apagaComSucessoPapelSemAtribuicoesENaoProtegido() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole tecnico = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("TECNICO").build();
        when(tenantRoleRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());
        when(userRepository.countByTenantRolesId(tecnico.getId())).thenReturn(0L);

        ResponseEntity<?> response = novoController().deleteRole(tecnico.getId());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(tenantRoleRepository, times(1)).deleteById(tecnico.getId());
    }

    // Caso 13 (WR-01, 127-REVIEW.md): a corrida entre a contagem (0, no momento da guarda) e o
    // deleteById -- uma atribuicao concorrente (PUT /admin/users/{id}) insere uma linha
    // t_user_tenant_role depois do count, e a FK real reagiria com
    // DataIntegrityViolationException no proprio deleteById. Simulado aqui fazendo
    // tenantRoleRepository.deleteById lancar essa excecao apesar da contagem ter devolvido 0 --
    // tem de virar 409 (a mesma recusa que a contagem teria dado se tivesse corrido depois), nunca
    // propagar como 500 nao tratado.
    @Test
    void deleteRole_corridaEntreContagemEDeleteEDevolve409EmVezDe500() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole tecnico = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("TECNICO").build();
        when(tenantRoleRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());
        when(userRepository.countByTenantRolesId(tecnico.getId())).thenReturn(0L);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("fk violation"))
                .when(tenantRoleRepository).deleteById(tecnico.getId());

        ResponseEntity<?> response = assertDoesNotThrow(() -> novoController().deleteRole(tecnico.getId()));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.contains("atribuído"));
        verifyNoInteractions(auditoriaRbacService);
    }
}
