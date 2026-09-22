package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.OfficeRbacResponse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 127 (Plano 03): prova de comportamento, não de comentário, para PAPEL-01 (leitura
 * tenant-scoped), PAPEL-07 (isolamento multi-tenant do {@code PUT}), PAPEL-08 (piso do
 * administrador do escritório, nas duas direções) e PAPEL-03 (efeito imediato numa sessão já
 * aberta) -- 127-CONTEXT.md Decisão 5: "monta dois tenants, grava no primeiro e assere que o
 * segundo não mudou".
 *
 * <p>Segue exatamente as convenções de {@link AdminControllerAtribuicaoPapeisEscritorioTest}:
 * {@link ResolucaoPapeisService} construído REAL sobre repositórios mockados (nunca stubado
 * diretamente), o idioma {@code lenient()} + {@code verify(..., never())} para provar isolamento
 * por ausência de chamada, e {@code SecurityContextHolder} povoado manualmente com um
 * {@link UserPrincipal} do tenant do caso, limpo em {@code @AfterEach}.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerRbacEscritorioTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final Integer ADMIN_MOLDE_ID = 1;
    private static final Integer PLATAFORMA_MOLDE_ID = 99;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoPrincipalDoTenant(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder().userId(UUID.randomUUID()).tenantId(tenantId).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private ResolucaoPapeisService novaResolucaoPapeisService() {
        return new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
    }

    private AdminController novoController(ResolucaoPapeisService resolucaoPapeisService) {
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository, auditoriaRbacService);
    }

    private Permission permissao(String nome) {
        return Permission.builder().nome(nome).reservadaPlataforma(false).build();
    }

    // -----------------------------------------------------------------------------------------
    // getRbac
    // -----------------------------------------------------------------------------------------

    // Caso 1 (PAPEL-01): a leitura devolve exatamente os papéis do chamador -- nunca os de outro
    // tenant, nunca lidos a partir dele.
    @Test
    void getRbac_devolveApenasPapeisDoChamador() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole advogado = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissao("clientes:view"))))
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(advogado));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        assertEquals(1, body.getPapeis().size());
        OfficeRbacResponse.PapelDto dto = body.getPapeis().get(0);
        assertEquals(advogado.getId(), dto.getId());
        assertEquals("ADVOGADO", dto.getNome());
        assertFalse(dto.isSistema());
        assertEquals(List.of("clientes:view"), dto.getPermissoes());
        verify(tenantRoleRepository, never()).findByTenantId(OUTRO_TENANT_ID);
    }

    // Caso 2 (PAPEL-08): protegido/podeApagar são computados por proveniência (moldeId), nunca
    // por nome -- este é o teste que falha se alguém reintroduzir uma comparação por nome.
    @Test
    void getRbac_protegidoEPodeApagarSaoPorProvenienciaNuncaPorNome() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        TenantRole renomeado = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Administrador do Escritório")
                .moldeId(ADMIN_MOLDE_ID)
                .build();
        TenantRole nomeadoAdminSemProveniencia = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADMIN")
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(renomeado, nomeadoAdminSemProveniencia));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        OfficeRbacResponse.PapelDto renomeadoDto = body.getPapeis().stream()
                .filter(p -> p.getId().equals(renomeado.getId())).findFirst().orElseThrow();
        OfficeRbacResponse.PapelDto semProvenienciaDto = body.getPapeis().stream()
                .filter(p -> p.getId().equals(nomeadoAdminSemProveniencia.getId())).findFirst().orElseThrow();

        assertTrue(renomeadoDto.isProtegido());
        assertFalse(renomeadoDto.isPodeApagar());
        assertFalse(semProvenienciaDto.isProtegido());
    }

    // Caso 3 (PAPEL-05, pré-condição): utilizadoresAtribuidos reflete a contagem ao vivo, e
    // podeApagar é falso sempre que essa contagem é > 0.
    @Test
    void getRbac_utilizadoresAtribuidosVemDaContagemAoVivo() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole advogado = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(advogado));
        when(userRepository.countByTenantRolesId(advogado.getId())).thenReturn(3L);

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).getRbac();

        OfficeRbacResponse.PapelDto dto = ((OfficeRbacResponse) response.getBody()).getPapeis().get(0);
        assertEquals(3L, dto.getUtilizadoresAtribuidos());
        assertFalse(dto.isPodeApagar());
    }

    // Caso 4 (PAPEL-09): PLATAFORMA_ADMIN nunca é listado, quer por nome literal quer por
    // proveniência -- um TenantRole renomeado que ainda aponte para o molde PLATAFORMA_ADMIN
    // continua excluído.
    @Test
    void getRbac_nuncaListaPapelDePlataforma() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
        when(roleRepository.findByNome("PLATAFORMA_ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(PLATAFORMA_MOLDE_ID).nome("PLATAFORMA_ADMIN").build()));

        TenantRole porNomeLiteral = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("PLATAFORMA_ADMIN").build();
        TenantRole porProveniencia = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Papel Renomeado")
                .moldeId(PLATAFORMA_MOLDE_ID).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID))
                .thenReturn(List.of(porNomeLiteral, porProveniencia));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).getRbac();

        OfficeRbacResponse body = (OfficeRbacResponse) response.getBody();
        assertTrue(body.getPapeis().isEmpty());
    }

    // -----------------------------------------------------------------------------------------
    // updateRbac
    // -----------------------------------------------------------------------------------------

    // Caso 5 (PAPEL-07, Decisão 5): um id de OUTRO tenant é recusado com 404 antes de qualquer
    // escrita, e o TenantRole desse outro tenant permanece exatamente como estava -- o tenant do
    // outro escritório nunca é sequer consultado.
    @Test
    void updateRbac_idDeOutroTenantERecusadoENuncaAlcancaOOutroTenant() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole papelDoOutroTenant = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ADVOGADO")
                .permissions(new HashSet<>(Set.of(permissao("clientes:view"))))
                .build();
        Set<Permission> permissoesOriginais = new HashSet<>(papelDoOutroTenant.getPermissions());

        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());
        // lenient(): prova por ausência de chamada que o tenant do outro escritório nunca é
        // consultado -- mesmo idioma de AdminControllerAtribuicaoPapeisEscritorioTest.
        lenient().when(tenantRoleRepository.findByTenantId(OUTRO_TENANT_ID)).thenReturn(List.of(papelDoOutroTenant));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(papelDoOutroTenant.getId());
        entrada.setPermissoes(List.of("clientes:view"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).updateRbac(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(tenantRoleRepository, never()).save(any());
        verify(tenantRoleRepository, never()).findByTenantId(OUTRO_TENANT_ID);
        assertEquals(permissoesOriginais, papelDoOutroTenant.getPermissions());
    }

    // Caso 6: uma escrita bem-sucedida nunca toca em roleRepository.save -- só TenantRole.
    @Test
    void updateRbac_sucessoNuncaEscreveTabelasGlobais() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole assistente = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ASSISTENTE").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(assistente));
        when(permissionRepository.findAllByReservadaPlataformaFalse())
                .thenReturn(List.of(permissao("clientes:view")));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(assistente.getId());
        entrada.setPermissoes(List.of("clientes:view"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).updateRbac(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tenantRoleRepository, times(1)).save(any());
        verify(roleRepository, never()).save(any());
    }

    // Caso 7 (PAPEL-08): o piso recusa a remoção de uma permissão que o papel protegido detém
    // atualmente -- 409, nomeando a chave removida, nenhuma escrita.
    @Test
    void updateRbac_floorLockRecusaRemocaoDePermissaoDoPapelProtegido() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        Permission clientesView = permissao("clientes:view");
        Permission rbacManage = permissao("rbac:manage");
        Permission usersManage = permissao("users:manage");
        TenantRole admin = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADMIN").moldeId(ADMIN_MOLDE_ID)
                .permissions(new HashSet<>(Set.of(clientesView, rbacManage, usersManage)))
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin));
        when(permissionRepository.findAllByReservadaPlataformaFalse())
                .thenReturn(List.of(rbacManage, usersManage));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(admin.getId());
        entrada.setPermissoes(List.of("rbac:manage", "users:manage")); // clientes:view omitida
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).updateRbac(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.contains("clientes:view"));
        verify(tenantRoleRepository, never()).save(any());
    }

    // Caso 8 (PAPEL-08): o piso permite adicionar uma permissão nova ao papel protegido -- o
    // conjunto gravado é a união do que já detinha com o que foi acrescentado.
    @Test
    void updateRbac_floorLockPermiteAdicaoDePermissao() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        Permission rbacManage = permissao("rbac:manage");
        Permission usersManage = permissao("users:manage");
        Permission novaPermissao = permissao("agenda:view");
        TenantRole admin = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADMIN").moldeId(ADMIN_MOLDE_ID)
                .permissions(new HashSet<>(Set.of(rbacManage, usersManage)))
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin));
        when(permissionRepository.findAllByReservadaPlataformaFalse())
                .thenReturn(List.of(rbacManage, usersManage, novaPermissao));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(admin.getId());
        entrada.setPermissoes(List.of("rbac:manage", "users:manage", "agenda:view"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).updateRbac(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository).save(captor.capture());
        Set<String> nomesGravados = captor.getValue().getPermissions().stream()
                .map(Permission::getNome).collect(Collectors.toSet());
        assertEquals(Set.of("rbac:manage", "users:manage", "agenda:view"), nomesGravados);
    }

    // Caso 9 (PAPEL-08): mesmo quando a regra de superconjunto por si só não seria violada (o
    // conjunto guardado já não detém rbac:manage/users:manage -- ex. estado editado à mão fora da
    // aplicação), a verificação independente das autoridades de gate continua a recusar.
    @Test
    void updateRbac_floorLockExigeAutoridadesDeGateMesmoSemRemocaoExplicita() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));
        when(roleRepository.findByNome("PLATAFORMA_ADMIN")).thenReturn(Optional.empty());

        Permission clientesView = permissao("clientes:view");
        TenantRole admin = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADMIN").moldeId(ADMIN_MOLDE_ID)
                .permissions(new HashSet<>(Set.of(clientesView)))
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of(clientesView));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(admin.getId());
        entrada.setPermissoes(List.of("clientes:view"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).updateRbac(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.contains("rbac:manage"));
        assertTrue(mensagem.contains("users:manage"));
        verify(tenantRoleRepository, never()).save(any());
    }

    // Caso 10: uma chave de permissão fora do catálogo servido é recusada com 400, nenhuma
    // escrita.
    @Test
    void updateRbac_chaveDesconhecidaERecusadaENuncaGrava() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole assistente = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ASSISTENTE").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(assistente));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(assistente.getId());
        entrada.setPermissoes(List.of("chave:inexistente"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResponseEntity<?> response = novoController(novaResolucaoPapeisService()).updateRbac(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String mensagem = (String) ((Map<?, ?>) response.getBody()).get("message");
        assertTrue(mensagem.contains("Permissão desconhecida"));
        verify(tenantRoleRepository, never()).save(any());
    }

    // Caso 11 (PAPEL-03): o efeito é imediato numa sessão já aberta -- sem novo login, sem
    // segunda query. Captura o TenantRole passado a save, constrói um User cujo tenantRoles
    // contém exatamente essa instância, e verifica que o resolvedor REAL de permissões efetivas
    // já devolve o novo conjunto -- exatamente a leitura que JwtAuthenticationFilter faz em cada
    // pedido autenticado.
    @Test
    void updateRbac_efeitoImediatoNumaSessaoJaAbertaSemRelogin() {
        autenticarComoPrincipalDoTenant(TENANT_ID);
        TenantRole assistente = TenantRole.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ASSISTENTE")
                .permissions(new HashSet<>())
                .build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(assistente));
        Permission clientesView = permissao("clientes:view");
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of(clientesView));

        OfficeRbacUpdateRequest.PapelPermissoesDto entrada = new OfficeRbacUpdateRequest.PapelPermissoesDto();
        entrada.setId(assistente.getId());
        entrada.setPermissoes(List.of("clientes:view"));
        OfficeRbacUpdateRequest request = new OfficeRbacUpdateRequest();
        request.setPapeis(List.of(entrada));

        ResolucaoPapeisService resolucaoPapeisService = novaResolucaoPapeisService();
        ResponseEntity<?> response = novoController(resolucaoPapeisService).updateRbac(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<TenantRole> captor = ArgumentCaptor.forClass(TenantRole.class);
        verify(tenantRoleRepository).save(captor.capture());
        TenantRole gravado = captor.getValue();

        User utilizador = User.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID)
                .tenantRoles(Set.of(gravado))
                .roles(Set.of())
                .permissions(Set.of())
                .build();

        Set<String> permissoesEfetivas = resolucaoPapeisService.resolverPermissoesEfectivas(utilizador);

        assertTrue(permissoesEfetivas.contains("clientes:view"));
    }
}
