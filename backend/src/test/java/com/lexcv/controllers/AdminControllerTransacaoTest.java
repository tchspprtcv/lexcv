package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.OfficeRbacUpdateRequest;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.AuditoriaRbacService;
import com.lexcv.services.ResolucaoPapeisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 04: prova, sob um {@link TransactionInterceptor}
 * REAL (nunca um mock que so verifica "foi chamado"), que {@code updateRbac}, {@code createUser},
 * {@code updateUser} e {@code deleteUser} sao {@code @Transactional} e que toda recusa (400/403/
 * 404/409) marca a transaccao como rollback-only via {@link RecusaTransacional#recusar} -- mesma
 * tecnica de {@code OfficeRolesControllerAuditoriaTest} (Plano 03) e de {@code
 * RecusaTransacionalTest} (Plano 02): {@link ProxyFactory} sobre um controller real com
 * repositorios mockados, {@code setProxyTargetClass(true)}, um {@link TransactionInterceptor}
 * sobre um {@link PlatformTransactionManager} mockado devolvendo um {@link
 * SimpleTransactionStatus} real, cujo campo {@code rollbackOnly} e inspecionado directamente apos
 * o commit.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTransacaoTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;
    @Mock private PlatformTransactionManager txManager;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final UUID PRINCIPAL_ID = UUID.randomUUID();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AdminController novoController() {
        ResolucaoPapeisService resolucaoPapeisService =
                new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository, auditoriaRbacService);
    }

    /**
     * Copia exata da tecnica de {@code RecusaTransacionalTest.criarProxyComTransacaoReal()} /
     * {@code OfficeRolesControllerAuditoriaTest.novoProxyComTransacaoReal()}: um {@link
     * TransactionInterceptor} verdadeiro, avaliando de facto a anotacao {@code @Transactional} de
     * cada metodo via {@link AnnotationTransactionAttributeSource}, sobre um {@link
     * PlatformTransactionManager} mockado.
     */
    private AdminController novoProxyComTransacaoReal() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                (TransactionManager) txManager, new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (AdminController) factory.getProxy();
    }

    private UserPrincipal autenticarComoPrincipalDoTenant(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).tenantId(tenantId).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        return principal;
    }

    // ---------------------------------------------------------------------------------------
    // Reflexao: as quatro escritas sao @Transactional
    // ---------------------------------------------------------------------------------------

    @Test
    void asQuatroEscritasCarregamAnotacaoTransactional() throws NoSuchMethodException {
        Method createUser = AdminController.class.getMethod("createUser", Map.class);
        Method updateUser = AdminController.class.getMethod("updateUser", UUID.class, Map.class);
        Method deleteUser = AdminController.class.getMethod("deleteUser", UUID.class);
        Method updateRbac = AdminController.class.getMethod("updateRbac", OfficeRbacUpdateRequest.class);

        assertTrue(createUser.isAnnotationPresent(Transactional.class));
        assertTrue(updateUser.isAnnotationPresent(Transactional.class));
        assertTrue(deleteUser.isAnnotationPresent(Transactional.class));
        assertTrue(updateRbac.isAnnotationPresent(Transactional.class));

        // listUsers e getRbac NAO devem ganhar a anotacao nesta fase -- so leitura.
        Method listUsers = AdminController.class.getMethod("listUsers");
        Method getRbac = AdminController.class.getMethod("getRbac");
        assertFalse(listUsers.isAnnotationPresent(Transactional.class));
        assertFalse(getRbac.isAnnotationPresent(Transactional.class));
    }

    // ---------------------------------------------------------------------------------------
    // updateUser: recusa depois de uma mutacao em memoria marca rollback-only, nunca grava
    // ---------------------------------------------------------------------------------------

    @Test
    void updateUser_recusaComRolesDepoisDeSetNome_400RollbackOnlyNuncaGrava() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        UUID alvoId = UUID.randomUUID();
        User utilizador = User.builder().id(alvoId).tenantId(TENANT_ID).nome("Nome Antigo")
                .email("alvo@escritorio.cv").ativo(true).tenantRoles(Set.of()).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizador));
        lenient().when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());

        AdminController proxy = novoProxyComTransacaoReal();

        // "nome" e processado ANTES de "roles" no corpo do metodo -- setNome("Novo Nome") corre em
        // memoria antes da recusa de "roles" mais abaixo. Sem @Transactional + recusar, esta
        // mutacao seria persistida por dirty-checking no commit apesar da recusa.
        ResponseEntity<?> response = proxy.updateUser(alvoId,
                Map.of("nome", "Novo Nome", "roles", List.of("X")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(userRepository, never()).save(any());
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    @Test
    void updateUser_idDeOutroTenant_404RollbackOnly() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        UUID alvoId = UUID.randomUUID();
        User utilizadorDeOutroTenant = User.builder().id(alvoId).tenantId(OUTRO_TENANT_ID)
                .nome("Estranho").email("estranho@outro.cv").ativo(true).tenantRoles(Set.of()).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizadorDeOutroTenant));

        AdminController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.updateUser(alvoId, Map.of("nome", "Tentativa"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(userRepository, never()).save(any());
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    // ---------------------------------------------------------------------------------------
    // updateRbac: recusa nunca grava; aceite grava tudo numa unica transaccao
    // ---------------------------------------------------------------------------------------

    @Test
    void updateRbac_permissaoDesconhecida_400RollbackOnlySaveNuncaChamado() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        TenantRole papel = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor").moldeId(null).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papel));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        lenient().when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        lenient().when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(papel.getId());
        entrada.setPermissoes(List.of("chave:inexistente"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        AdminController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.updateRbac(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(tenantRoleRepository, never()).save(any());
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    @Test
    void updateRbac_duasEntradasAceites_umaUnicaTransaccaoComDoisSaves() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        TenantRole papelA = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor A").moldeId(null).build();
        TenantRole papelB = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor B").moldeId(null).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papelA, papelB));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        lenient().when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        lenient().when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        OfficeRbacUpdateRequest.PapelPermissoesDto entradaA = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entradaA.setId(papelA.getId());
        entradaA.setPermissoes(List.of());
        OfficeRbacUpdateRequest.PapelPermissoesDto entradaB = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entradaB.setId(papelB.getId());
        entradaB.setPermissoes(List.of());
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entradaA, entradaB));

        AdminController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.updateRbac(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        InOrder ordem = inOrder(txManager, tenantRoleRepository);
        ordem.verify(txManager).getTransaction(any());
        ordem.verify(txManager).commit(status);
        verify(txManager, times(1)).getTransaction(any());
        verify(txManager, times(1)).commit(any());
        verify(tenantRoleRepository, times(2)).save(any());
        assertFalse(status.isRollbackOnly());
    }

    // ---------------------------------------------------------------------------------------
    // deleteUser: auto-eliminacao continua recusada, agora com rollback-only
    // ---------------------------------------------------------------------------------------

    @Test
    void deleteUser_deSiProprio_400RollbackOnly() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        AdminController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.deleteUser(principal.getUserId());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(userRepository, never()).deleteById(any());
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    // ---------------------------------------------------------------------------------------
    // createUser: sucesso comita sem rollback-only
    // ---------------------------------------------------------------------------------------

    @Test
    void createUser_sucesso_commitSemRollbackOnly() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        TenantRole papel = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor").moldeId(null).permissions(Set.of()).build();
        when(userRepository.findByEmail("nova@escritorio.cv")).thenReturn(Optional.empty());
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papel));
        lenient().when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        Map<String, Object> body = Map.of(
                "nome", "Nova Colaboradora",
                "email", "nova@escritorio.cv",
                "password", "Segredo@123",
                "tenantRoleIds", List.of(papel.getId().toString()));

        AdminController proxy = novoProxyComTransacaoReal();

        ResponseEntity<?> response = proxy.createUser(body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(txManager).commit(status);
        assertFalse(status.isRollbackOnly());
    }
}
