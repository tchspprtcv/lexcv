package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PapelCreateRequest;
import com.lexcv.dtos.PapelRenameRequest;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 03: prova, sob um {@link TransactionInterceptor}
 * REAL (nunca um mock que so verifica "foi chamado"), de que os tres handlers de escrita de
 * {@link OfficeRolesController} sao {@code @Transactional} e de que o evento de auditoria
 * ({@link AuditoriaRbacService}) e a mudanca que ele descreve fazem commit ou rollback juntos --
 * mesma tecnica de {@code RecusaTransacionalTest} (Plano 02): {@link ProxyFactory} sobre um
 * controller real com repositorios mockados, {@code setProxyTargetClass(true)}, um {@link
 * TransactionInterceptor} sobre um {@link PlatformTransactionManager} mockado devolvendo um
 * {@link SimpleTransactionStatus} real, cujo campo {@code rollbackOnly} e inspecionado
 * directamente apos o commit.
 *
 * <p>Os pequenos fixtures de autenticacao/gravacao sao copiados de {@code OfficeRolesControllerTest}
 * (nao reutilizados por heranca ou extracao) -- a mesma disciplina que aquela classe ja segue ao
 * copiar de {@code AdminControllerRbacAutorizacaoTest}: cada classe de teste fica auto-contida.
 */
@ExtendWith(MockitoExtension.class)
class OfficeRolesControllerAuditoriaTest {

    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;
    @Mock private PlatformTransactionManager txManager;

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
     * Copia exata da tecnica de {@code RecusaTransacionalTest.criarProxyComTransacaoReal()}: um
     * {@link TransactionInterceptor} verdadeiro, avaliando de facto a anotacao {@code
     * @Transactional} de cada metodo via {@link AnnotationTransactionAttributeSource}, sobre um
     * {@link PlatformTransactionManager} mockado.
     */
    private OfficeRolesController novoProxyComTransacaoReal() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                (TransactionManager) txManager, new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (OfficeRolesController) factory.getProxy();
    }

    private UserPrincipal autenticarComoPrincipalDoTenant(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(UUID.randomUUID()).tenantId(tenantId)
                .nome("Principal de Teste").email("principal@escritorio-teste.cv")
                .roles(Set.of()).permissions(Set.of())
                .authorities(List.of(new SimpleGrantedAuthority("rbac:manage")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return principal;
    }

    private TenantRole simularGravacaoComIdGerado(TenantRole tenantRole) {
        if (tenantRole.getId() == null) {
            tenantRole.setId(UUID.randomUUID());
        }
        return tenantRole;
    }

    // ---------------------------------------------------------------------------------------
    // Reflexao: as tres escritas sao @Transactional
    // ---------------------------------------------------------------------------------------

    @Test
    void asTresEscritasCarregamAnotacaoTransactional() throws NoSuchMethodException {
        Method createRole = OfficeRolesController.class.getMethod("createRole", PapelCreateRequest.class);
        Method renameRole = OfficeRolesController.class.getMethod("renameRole", UUID.class, PapelRenameRequest.class);
        Method deleteRole = OfficeRolesController.class.getMethod("deleteRole", UUID.class);

        assertTrue(createRole.isAnnotationPresent(Transactional.class));
        assertTrue(renameRole.isAnnotationPresent(Transactional.class));
        assertTrue(deleteRole.isAnnotationPresent(Transactional.class));
    }

    // ---------------------------------------------------------------------------------------
    // createRole
    // ---------------------------------------------------------------------------------------

    @Test
    void createRole_gravaEventoNaMesmaTransaccaoDepoisDoSaveAndFlush() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        when(tenantRoleRepository.findByTenantIdAndNome(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));

        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Recepção");
        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.createRole(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        InOrder ordem = inOrder(txManager, tenantRoleRepository, auditoriaRbacService);
        ordem.verify(txManager).getTransaction(any());
        ordem.verify(tenantRoleRepository).saveAndFlush(any());
        ArgumentCaptor<TenantRole> papelCaptor = ArgumentCaptor.forClass(TenantRole.class);
        ordem.verify(auditoriaRbacService)
                .registarPapelCriado(eq(principal.getTenantId()), eq(principal), papelCaptor.capture());
        ordem.verify(txManager).commit(status);
        assertEquals("Recepção", papelCaptor.getValue().getNome());
        assertFalse(status.isRollbackOnly());
    }

    @Test
    void createRole_eventoQueFalhaArrastaOPapelERebentaARollbackEComitNuncaAcontece() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        when(tenantRoleRepository.findByTenantIdAndNome(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));
        doThrow(new RuntimeException("falha ao serializar detalhe"))
                .when(auditoriaRbacService).registarPapelCriado(any(), any(), any());

        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Recepção");
        OfficeRolesController proxy = novoProxyComTransacaoReal();

        assertThrows(RuntimeException.class, () -> proxy.createRole(request));

        verify(txManager).rollback(status);
        verify(txManager, never()).commit(any());
    }

    @Test
    void createRole_saveAndFlushLancaDataIntegrityViolation_naoChamaEventoEComitaComRollbackOnly() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        when(tenantRoleRepository.findByTenantIdAndNome(eq(TENANT_ID), any())).thenReturn(Optional.empty());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(tenantRoleRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        PapelCreateRequest request = new PapelCreateRequest();
        request.setNome("Recepção");
        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.createRole(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(auditoriaRbacService, never()).registarPapelCriado(any(), any(), any());
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    // ---------------------------------------------------------------------------------------
    // renameRole
    // ---------------------------------------------------------------------------------------

    @Test
    void renameRole_gravaEventoComNomeAntigoENomeNovo() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        TenantRole papel = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Antigo").build();
        when(tenantRoleRepository.findById(papel.getId())).thenReturn(Optional.of(papel));
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "Novo")).thenReturn(Optional.empty());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));

        PapelRenameRequest request = new PapelRenameRequest();
        request.setNome("Novo");
        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.renameRole(papel.getId(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditoriaRbacService).registarPapelRenomeado(TENANT_ID, principal, papel.getId(), "Antigo", "Novo");
        verify(txManager).commit(status);
        assertFalse(status.isRollbackOnly());
    }

    @Test
    void renameRole_paraOMesmoNomeDevolveDuzentosENuncaGravaEvento() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        TenantRole papel = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Mesmo").build();
        when(tenantRoleRepository.findById(papel.getId())).thenReturn(Optional.of(papel));
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "Mesmo")).thenReturn(Optional.empty());
        when(tenantRoleRepository.saveAndFlush(any())).thenAnswer(inv -> simularGravacaoComIdGerado(inv.getArgument(0)));

        PapelRenameRequest request = new PapelRenameRequest();
        request.setNome("Mesmo");
        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.renameRole(papel.getId(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditoriaRbacService, never()).registarPapelRenomeado(any(), any(), any(), any(), any());
        verify(txManager).commit(status);
        assertFalse(status.isRollbackOnly());
    }

    @Test
    void renameRole_idDeOutroTenantERecusadoSemAuditoriaEComRollbackOnly() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        TenantRole papelDoOutroTenant = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ADVOGADO").build();
        when(tenantRoleRepository.findById(papelDoOutroTenant.getId())).thenReturn(Optional.of(papelDoOutroTenant));

        PapelRenameRequest request = new PapelRenameRequest();
        request.setNome("Nome Tentado");
        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.renameRole(papelDoOutroTenant.getId(), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(auditoriaRbacService);
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    // ---------------------------------------------------------------------------------------
    // deleteRole
    // ---------------------------------------------------------------------------------------

    @Test
    void deleteRole_gravaEventoComOPapelCarregadoDepoisDoDeleteEDoFlush() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        TenantRole tecnico = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("TECNICO").build();
        when(tenantRoleRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());
        when(userRepository.countByTenantRolesId(tecnico.getId())).thenReturn(0L);

        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.deleteRole(tecnico.getId());

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        InOrder ordem = inOrder(tenantRoleRepository, auditoriaRbacService, txManager);
        ordem.verify(tenantRoleRepository).deleteById(tecnico.getId());
        ordem.verify(tenantRoleRepository).flush();
        ordem.verify(auditoriaRbacService).registarPapelApagado(TENANT_ID, principal, tecnico);
        ordem.verify(txManager).commit(status);
        assertFalse(status.isRollbackOnly());
    }

    @Test
    void deleteRole_papelProtegidoERecusadoSemAuditoriaEComRollbackOnly() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        TenantRole admin = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADMIN").moldeId(ADMIN_MOLDE_ID).build();
        when(tenantRoleRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));

        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.deleteRole(admin.getId());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verifyNoInteractions(auditoriaRbacService);
        verify(tenantRoleRepository, never()).deleteById(any());
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    @Test
    void deleteRole_idDeOutroTenantERecusadoSemAuditoriaEComRollbackOnly() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        TenantRole papelDoOutroTenant = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ADVOGADO").build();
        when(tenantRoleRepository.findById(papelDoOutroTenant.getId())).thenReturn(Optional.of(papelDoOutroTenant));

        OfficeRolesController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.deleteRole(papelDoOutroTenant.getId());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(auditoriaRbacService);
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    // ---------------------------------------------------------------------------------------
    // Tenant sempre do principal (nunca do alvo carregado)
    // ---------------------------------------------------------------------------------------

    // Prova, com um UUID CAPTURADO (nao apenas um matcher eq() que poderia coincidir por
    // construcao do fixture), que o tenantId passado a registarPapelApagado e literalmente
    // principal.getTenantId() -- nunca tenantRole.getTenantId() lido de outro sitio, mesmo que
    // neste caso os dois valores sejam iguais depois da guarda 404 ja ter passado.
    @Test
    void deleteRole_oTenantIdPassadoAoEventoVemLiteralmenteDoPrincipal() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        TenantRole tecnico = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("TECNICO").build();
        when(tenantRoleRepository.findById(tecnico.getId())).thenReturn(Optional.of(tecnico));
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());
        when(userRepository.countByTenantRolesId(tecnico.getId())).thenReturn(0L);

        OfficeRolesController proxy = novoProxyComTransacaoReal();
        proxy.deleteRole(tecnico.getId());

        ArgumentCaptor<UUID> tenantIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(auditoriaRbacService).registarPapelApagado(tenantIdCaptor.capture(), eq(principal), eq(tecnico));
        assertEquals(principal.getTenantId(), tenantIdCaptor.getValue());
        assertEquals(TENANT_ID, tenantIdCaptor.getValue());
    }
}
