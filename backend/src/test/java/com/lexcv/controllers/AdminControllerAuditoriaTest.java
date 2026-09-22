package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.OfficeRbacUpdateRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 128 (AUDT-01/AUDT-02, 128-CONTEXT.md), Plano 05: prova de comportamento (nunca de
 * comentario) para os quatro caminhos de escrita de {@link AdminController} instrumentados pelo
 * Plano 05 -- {@code updateRbac} (AUDT-01), e {@code createUser}/{@code updateUser}/
 * {@code deleteUser} (AUDT-02).
 *
 * <p>Cada caso de sucesso captura os argumentos REAIS passados a {@link AuditoriaRbacService}
 * (nunca so a contagem de chamadas), e cada caso de recusa prova, por
 * {@code verifyNoInteractions(auditoriaRbacService)}, que uma requisicao recusada nunca produz um
 * evento. Segue a convencao de {@link AdminControllerAtribuicaoPapeisEscritorioTest} (fixtures de
 * atribuicao por id), {@link AdminControllerRbacEscritorioTest} (fixtures de updateRbac/piso) e
 * {@link AdminControllerUltimoAdministradorTest} (fixture do ultimo administrador) -- sem
 * MockMvc/{@code @SpringBootTest}, instanciacao directa do controller com colaboradores Mockito.
 *
 * <p>O ultimo caso reusa a tecnica de {@link AdminControllerTransacaoTest} ({@link ProxyFactory} +
 * {@link TransactionInterceptor} REAL sobre um {@link PlatformTransactionManager} mockado) para
 * provar que uma falha DENTRO de {@code registarAtribuicoes} reverte tambem a mudanca que o
 * evento descreveria -- nunca um commit parcial.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerAuditoriaTest {

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
    private static final Integer ADMIN_MOLDE_ID = 1;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal autenticarComoPrincipalDoTenant(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).tenantId(tenantId).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        return principal;
    }

    private AdminController novoController() {
        ResolucaoPapeisService resolucaoPapeisService =
                new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository, auditoriaRbacService);
    }

    /**
     * Copia exata da tecnica de {@code AdminControllerTransacaoTest.novoProxyComTransacaoReal()}:
     * um {@link TransactionInterceptor} verdadeiro sobre um {@link PlatformTransactionManager}
     * mockado, avaliando de facto a anotacao {@code @Transactional} de cada metodo.
     */
    private AdminController novoProxyComTransacaoReal() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                (TransactionManager) txManager, new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (AdminController) factory.getProxy();
    }

    private Permission permissao(String nome) {
        return Permission.builder().nome(nome).reservadaPlataforma(false).build();
    }

    // ---------------------------------------------------------------------------------------
    // updateRbac (AUDT-01)
    // ---------------------------------------------------------------------------------------

    // Um papel ganha uma permissao nova, outro e resubmetido sem qualquer alteracao real --
    // registarPermissoesAlteradas e chamado para os DOIS (o ciclo de escrita nao filtra por
    // diff vazio; e o proprio servico, real em producao, que decide nao gravar), mas com
    // argumentos diferentes: o primeiro com adicionadas={"financeiro:view"}, o segundo com os
    // dois lados vazios.
    @Test
    void updateRbac_doisPapeisUmAlteradoUmInalterado_registaPermissoesAlteradasComOsArgumentosCorretos() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);

        TenantRole papelA = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor A").moldeId(null).permissions(new HashSet<>()).build();
        TenantRole papelB = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor B").moldeId(null).permissions(new HashSet<>(Set.of(permissao("agenda:view")))).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papelA, papelB));

        Permission financeiroView = permissao("financeiro:view");
        Permission agendaView = permissao("agenda:view");
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of(financeiroView, agendaView));
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        OfficeRbacUpdateRequest.PapelPermissoesDto entradaA = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entradaA.setId(papelA.getId());
        entradaA.setPermissoes(List.of("financeiro:view"));
        OfficeRbacUpdateRequest.PapelPermissoesDto entradaB = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entradaB.setId(papelB.getId());
        entradaB.setPermissoes(List.of("agenda:view"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entradaA, entradaB));

        ResponseEntity<?> response = novoController().updateRbac(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditoriaRbacService).registarPermissoesAlteradas(
                eq(TENANT_ID), eq(principal), eq(papelA), eq(Set.of("financeiro:view")), eq(Set.of()));
        verify(auditoriaRbacService).registarPermissoesAlteradas(
                eq(TENANT_ID), eq(principal), eq(papelB), eq(Set.of()), eq(Set.of()));
    }

    // O piso do administrador (PAPEL-08) recusa a remocao de uma permissao de gate do papel
    // protegido com 409, ANTES de qualquer tenantRoleRepository.save -- auditoriaRbacService
    // nunca e tocado.
    @Test
    void updateRbac_recusaPorPisoDoAdministrador_auditoriaNuncaTocada() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        Permission clientesView = permissao("clientes:view");
        Permission rbacManage = permissao("rbac:manage");
        Permission usersManage = permissao("users:manage");
        TenantRole admin = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("ADMIN").moldeId(ADMIN_MOLDE_ID)
                .permissions(new HashSet<>(Set.of(clientesView, rbacManage, usersManage)))
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of(rbacManage, usersManage));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(admin.getId());
        entrada.setPermissoes(List.of("rbac:manage", "users:manage")); // clientes:view omitida
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController().updateRbac(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(tenantRoleRepository, never()).save(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // ---------------------------------------------------------------------------------------
    // createUser (AUDT-02)
    // ---------------------------------------------------------------------------------------

    @Test
    void createUser_comTenantRoleIds_registaAtribuicaoParaCadaPapelAtribuido() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);

        TenantRole papelA = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor A").moldeId(null).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papelA));
        when(userRepository.findByEmail("nova@escritorio.cv")).thenReturn(Optional.empty());
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        Map<String, Object> body = Map.of(
                "nome", "Nova Colaboradora",
                "email", "nova@escritorio.cv",
                "password", "Segredo@123",
                "tenantRoleIds", List.of(papelA.getId().toString()));

        ResponseEntity<?> response = novoController().createUser(body);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<User> alvoCaptor = ArgumentCaptor.forClass(User.class);
        verify(auditoriaRbacService).registarAtribuicoes(
                eq(TENANT_ID), eq(principal), alvoCaptor.capture(), eq(Set.of()), eq(Set.of(papelA)), isNull());
        assertEquals("nova@escritorio.cv", alvoCaptor.getValue().getEmail());
    }

    // ---------------------------------------------------------------------------------------
    // updateUser (AUDT-02)
    // ---------------------------------------------------------------------------------------

    // Troca {A,B} -> {B,C}: "antes" tem de ser o snapshot PRE-MUTACAO (capturado antes de
    // qualquer setTenantRoles), nunca a colecao ja mutada -- e exactamente esta distincao que
    // este caso prova.
    @Test
    void updateUser_trocandoPapeis_registaAtribuicoesComOAntesEODepoisCorretos() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        UUID alvoId = UUID.randomUUID();

        TenantRole papelA = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel A").moldeId(null).build();
        TenantRole papelB = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel B").moldeId(null).build();
        TenantRole papelC = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel C").moldeId(null).build();

        User utilizador = User.builder().id(alvoId).tenantId(TENANT_ID).nome("Alvo")
                .email("alvo@escritorio.cv").ativo(true).tenantRoles(new HashSet<>(Set.of(papelA, papelB))).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizador));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papelB, papelC));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(alvoId,
                Map.of("tenantRoleIds", List.of(papelB.getId().toString(), papelC.getId().toString())));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(auditoriaRbacService).registarAtribuicoes(
                eq(TENANT_ID), eq(principal), eq(utilizador), eq(Set.of(papelA, papelB)), eq(Set.of(papelB, papelC)), isNull());
    }

    // Um pedido que so muda "nome" nunca submete "tenantRoleIds" -- registarAtribuicoes nao pode
    // ser chamado, mesmo que o utilizador ja detenha papeis.
    @Test
    void updateUser_alterandoApenasNome_naoRegistaAtribuicao() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        UUID alvoId = UUID.randomUUID();

        TenantRole papelExistente = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel Existente").build();
        User utilizador = User.builder().id(alvoId).tenantId(TENANT_ID).nome("Nome Antigo")
                .email("alvo@escritorio.cv").ativo(true).tenantRoles(Set.of(papelExistente)).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizador));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(alvoId, Map.of("nome", "Nome Novo"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verifyNoInteractions(auditoriaRbacService);
    }

    // tenantRoleIds contendo o papel reservado a plataforma -- 403 antes de qualquer mutacao,
    // nenhum evento.
    @Test
    void updateUser_tenantRoleIdsComPapelDePlataforma_403SemAuditoria() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        UUID alvoId = UUID.randomUUID();

        User utilizador = User.builder().id(alvoId).tenantId(TENANT_ID).nome("Alvo")
                .email("alvo@escritorio.cv").ativo(true).tenantRoles(Set.of()).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizador));

        TenantRole plataformaAdmin = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("PLATAFORMA_ADMIN").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(plataformaAdmin));

        ResponseEntity<?> response = novoController().updateUser(alvoId,
                Map.of("tenantRoleIds", List.of(plataformaAdmin.getId().toString())));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // tenantRoleIds contendo um id que so existe noutro tenant -- 404 antes de qualquer mutacao,
    // nenhum evento.
    @Test
    void updateUser_tenantRoleIdsComIdDeOutroTenant_404SemAuditoria() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        UUID alvoId = UUID.randomUUID();

        User utilizador = User.builder().id(alvoId).tenantId(TENANT_ID).nome("Alvo")
                .email("alvo@escritorio.cv").ativo(true).tenantRoles(Set.of()).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizador));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        UUID idDeOutroTenant = UUID.randomUUID();
        ResponseEntity<?> response = novoController().updateUser(alvoId,
                Map.of("tenantRoleIds", List.of(idDeOutroTenant.toString())));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // Remover o papel protegido do UNICO administrador activo -- 409 pela guarda de ultimo
    // administrador (Plano 04), antes de qualquer save; nenhum evento.
    @Test
    void updateUser_removendoPapelProtegidoDoUltimoAdministrador_409SemAuditoria() {
        UUID adminUserId = PRINCIPAL_ID;
        autenticarComoPrincipalDoTenant(TENANT_ID, adminUserId);
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));

        TenantRole admin = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("ADMIN").moldeId(ADMIN_MOLDE_ID).sistema(true).build();
        TenantRole outro = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor").moldeId(null).build();
        User utilizador = User.builder().id(adminUserId).tenantId(TENANT_ID).nome("Administrador")
                .email("admin@escritorio.cv").ativo(true).tenantRoles(Set.of(admin)).build();

        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(utilizador));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin, outro));
        // countByTenantRolesIdAndAtivoTrueAndIdNot nao stubado -- default Mockito para long e
        // 0L, que e exactamente "nenhum OUTRO detentor activo" (Plano 04), o valor que fecha
        // esta guarda com 409.

        ResponseEntity<?> response = novoController().updateUser(adminUserId,
                Map.of("tenantRoleIds", List.of(outro.getId().toString())));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // ---------------------------------------------------------------------------------------
    // deleteUser (AUDT-02, Decisao 3, T-128-25)
    // ---------------------------------------------------------------------------------------

    // O utilizador eliminado detinha dois papeis -- registarAtribuicoes recebe antes={A,B},
    // depois=vazio e motivo == "utilizador_eliminado" (AuditoriaRbacService.MOTIVO_UTILIZADOR_ELIMINADO),
    // e e chamado DEPOIS de userRepository.deleteById (InOrder) -- nunca antes, porque o snapshot
    // "antes" e que tem de ser capturado antes do delete, nao a chamada ao servico em si.
    @Test
    void deleteUser_deUtilizadorComDoisPapeis_registaRetiradaParaCadaPapelAposDeleteById() {
        UserPrincipal principal = autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        UUID alvoId = UUID.randomUUID();

        TenantRole papelA = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel A").moldeId(null).build();
        TenantRole papelB = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel B").moldeId(null).build();
        User utilizador = User.builder().id(alvoId).tenantId(TENANT_ID).nome("Alvo a Eliminar")
                .email("alvo@escritorio.cv").ativo(true).tenantRoles(Set.of(papelA, papelB)).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizador));

        ResponseEntity<?> response = novoController().deleteUser(alvoId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        InOrder ordem = inOrder(userRepository, auditoriaRbacService);
        ordem.verify(userRepository).deleteById(alvoId);
        ordem.verify(auditoriaRbacService).registarAtribuicoes(
                eq(TENANT_ID), eq(principal), eq(utilizador), eq(Set.of(papelA, papelB)), eq(Set.of()),
                eq(AuditoriaRbacService.MOTIVO_UTILIZADOR_ELIMINADO));
    }

    @Test
    void deleteUser_idDeOutroTenant_404SemAuditoria() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        UUID alvoId = UUID.randomUUID();
        User utilizadorDeOutroTenant = User.builder().id(alvoId).tenantId(OUTRO_TENANT_ID).nome("Estranho")
                .email("estranho@outro.cv").ativo(true).tenantRoles(Set.of()).build();
        when(userRepository.findById(alvoId)).thenReturn(Optional.of(utilizadorDeOutroTenant));

        ResponseEntity<?> response = novoController().deleteUser(alvoId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(userRepository, never()).deleteById(any());
        verifyNoInteractions(auditoriaRbacService);
    }

    // ---------------------------------------------------------------------------------------
    // Rollback: uma falha DENTRO do evento de auditoria reverte tambem a mudanca (Plano 04's
    // @Transactional boundary, provado aqui com um TransactionInterceptor REAL).
    // ---------------------------------------------------------------------------------------

    @Test
    void createUser_ondeRegistarAtribuicoesLanca_fazRollbackNuncaComita() {
        autenticarComoPrincipalDoTenant(TENANT_ID, PRINCIPAL_ID);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        TenantRole papel = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor").moldeId(null).permissions(Set.of()).build();
        when(userRepository.findByEmail("evento-falha@escritorio.cv")).thenReturn(Optional.empty());
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papel));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        doThrow(new IllegalStateException("falha simulada ao gravar o evento de auditoria"))
                .when(auditoriaRbacService).registarAtribuicoes(any(), any(), any(), any(), any(), any());

        Map<String, Object> body = Map.of(
                "nome", "Nova Colaboradora",
                "email", "evento-falha@escritorio.cv",
                "password", "Segredo@123",
                "tenantRoleIds", List.of(papel.getId().toString()));

        AdminController proxy = novoProxyComTransacaoReal();

        assertThrows(IllegalStateException.class, () -> proxy.createUser(body));

        verify(txManager, never()).commit(any());
        verify(txManager).rollback(status);
    }
}
