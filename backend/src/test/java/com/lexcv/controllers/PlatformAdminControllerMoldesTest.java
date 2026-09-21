package com.lexcv.controllers;

import com.lexcv.dtos.MoldeCreateRequest;
import com.lexcv.dtos.MoldeProvisionResponse;
import com.lexcv.dtos.MoldesConsolaResponse;
import com.lexcv.dtos.MoldesUpdateRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
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
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova o comportamento dos 3 handlers novos de {@link PlatformAdminController} (Phase 125,
 * MOLD-02/MOLD-03/MOLD-04): {@code listMoldes}, {@code updateMoldes}, {@code createMolde}.
 *
 * <p>Segue o mesmo andaime de dois grupos que {@link PlatformAdminControllerTest} (Phase 119/120)
 * já estabeleceu -- ver o Javadoc de classe daquele ficheiro para a justificação completa; aqui
 * repetida de forma resumida por ser o único ponto de referência deste ficheiro:
 *
 * <p>Grupo A (instanciação direta, sem proxy) prova comportamento -- o controller é instanciado
 * diretamente com os 6 colaboradores mockados via Mockito e cada método é invocado como uma
 * chamada Java simples.
 *
 * <p>Grupo B prova autoridade. Uma chamada Java direta ao controller NUNCA avalia
 * {@code @PreAuthorize} -- essa anotação só é interpretada por um proxy AOP de method security
 * ({@code AuthorizationManagerBeforeMethodInterceptor}). <b>Ler a anotação por reflexão não é
 * prova aceitável</b> (convenção da Phase 119): por isso estes casos envolvem o controller num
 * {@link ProxyFactory} CGLIB ({@code setProxyTargetClass(true)}, obrigatório porque o controller
 * não implementa nenhuma interface) montado com o mesmo interceptor que o Spring Security usa em
 * produção via {@code @EnableMethodSecurity}.
 *
 * <p><b>Os casos 5 (updateMoldes nunca escreve em t_tenant_role) e 10 (ADMIN de escritório
 * recusado nos 3 handlers) não podem ser apagados por nenhum refactor futuro:</b> o 5 é a prova
 * do lado do servidor de MOLD-03 (snapshot -- editar um molde nunca chega a escritórios já
 * provisionados); o 10 é a prova do gate cross-tenant (T-125-17) que mantém esta superfície
 * inalcançável a um ADMIN de escritório.
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminControllerMoldesTest {

    @Mock
    private SetupService setupService;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private TenantRoleRepository tenantRoleRepository;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private PlatformAdminController novoController() {
        return new PlatformAdminController(setupService, tenantRepository, userRepository,
                roleRepository, permissionRepository, tenantRoleRepository);
    }

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

    private Permission permissao(Integer id, String nome, String rotulo, String modulo, Integer ordem) {
        return Permission.builder()
                .id(id)
                .nome(nome)
                .rotulo(rotulo)
                .descricao(rotulo)
                .modulo(modulo)
                .ordem(ordem)
                .reservadaPlataforma(false)
                .build();
    }

    private Role molde(Integer id, String nome, Set<Permission> permissoes) {
        return Role.builder()
                .id(id)
                .nome(nome)
                .instanciavel(true)
                .permissions(permissoes)
                .build();
    }

    // ---- Grupo A: comportamento de listMoldes ----

    @Test
    void listMoldes_devolveCatalogoFiltradoOrdenadoPorOrdemComNulosNoFimEExcluiSemRotulo() {
        Permission clientesView = permissao(1, "clientes:view", "Ver Clientes", "Clientes", 2);
        Permission processosView = permissao(2, "processos:view", "Ver Processos", "Processos", 1);
        Permission semOrdem = permissao(3, "extra:view", "Ver Extra", "Extra", null);
        Permission rotuloNulo = permissao(4, "sem:rotulo", null, "Extra", 1);
        Permission rotuloBranco = permissao(5, "sem:rotulo2", "   ", "Extra", 1);
        when(permissionRepository.findAllByReservadaPlataformaFalse())
                .thenReturn(List.of(clientesView, processosView, semOrdem, rotuloNulo, rotuloBranco));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of());

        ResponseEntity<?> response = novoController().listMoldes();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        MoldesConsolaResponse corpo = (MoldesConsolaResponse) response.getBody();
        assertNotNull(corpo);
        assertEquals(3, corpo.getPermissoes().size());
        assertEquals(List.of("processos:view", "clientes:view", "extra:view"),
                corpo.getPermissoes().stream().map(MoldesConsolaResponse.PermissaoDto::getKey).toList());
    }

    @Test
    void listMoldes_devolveEscritoriosInstanciadosPorMoldeAPartirDeCountByMoldeId() {
        Role advogado = molde(10, "ADVOGADO", new HashSet<>());
        Role tecnico = molde(11, "TECNICO", new HashSet<>());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(advogado, tecnico));
        when(tenantRoleRepository.countByMoldeId(10)).thenReturn(0L);
        when(tenantRoleRepository.countByMoldeId(11)).thenReturn(3L);

        ResponseEntity<?> response = novoController().listMoldes();

        MoldesConsolaResponse corpo = (MoldesConsolaResponse) response.getBody();
        assertNotNull(corpo);
        MoldesConsolaResponse.MoldeDto dtoAdvogado = corpo.getMoldes().stream()
                .filter(m -> m.getId().equals(10)).findFirst().orElseThrow();
        MoldesConsolaResponse.MoldeDto dtoTecnico = corpo.getMoldes().stream()
                .filter(m -> m.getId().equals(11)).findFirst().orElseThrow();
        assertEquals(0L, dtoAdvogado.getEscritoriosInstanciados());
        assertEquals(3L, dtoTecnico.getEscritoriosInstanciados());
        verify(tenantRoleRepository, times(1)).countByMoldeId(10);
        verify(tenantRoleRepository, times(1)).countByMoldeId(11);
    }

    @Test
    void listMoldes_nuncaListaPlataformaAdminMesmoQueOFiltroSqlRegredisse() {
        Role plataformaAdmin = molde(99, "PLATAFORMA_ADMIN", new HashSet<>());
        Role advogado = molde(10, "ADVOGADO", new HashSet<>());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(plataformaAdmin, advogado));
        when(tenantRoleRepository.countByMoldeId(any())).thenReturn(0L);

        ResponseEntity<?> response = novoController().listMoldes();

        MoldesConsolaResponse corpo = (MoldesConsolaResponse) response.getBody();
        assertNotNull(corpo);
        assertTrue(corpo.getMoldes().stream().noneMatch(m -> "PLATAFORMA_ADMIN".equals(m.getNome())));
        assertEquals(1, corpo.getMoldes().size());
    }

    // ---- Grupo A: comportamento de updateMoldes ----

    @Test
    void updateMoldes_gravaOConjuntoDePermissoesPedido() {
        Permission clientesView = permissao(1, "clientes:view", "Ver Clientes", "Clientes", 1);
        Permission processosEdit = permissao(2, "processos:edit", "Editar Processos", "Processos", 2);
        Role advogado = molde(10, "ADVOGADO", new HashSet<>());
        when(roleRepository.findById(10)).thenReturn(Optional.of(advogado));
        when(permissionRepository.findAllByReservadaPlataformaFalse())
                .thenReturn(List.of(clientesView, processosEdit));
        lenient().when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(advogado));
        lenient().when(tenantRoleRepository.countByMoldeId(any())).thenReturn(0L);

        MoldesUpdateRequest.MoldePermissoesEntry entrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        entrada.setId(10);
        entrada.setPermissoes(List.of("clientes:view", "processos:edit"));
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(entrada));

        ResponseEntity<?> response = novoController().updateMoldes(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(1)).save(captor.capture());
        Set<String> chavesGravadas = captor.getValue().getPermissions().stream()
                .map(Permission::getNome).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("clientes:view", "processos:edit"), chavesGravadas);
    }

    /**
     * Caso mais importante do ficheiro (MOLD-03): a edição de um molde nunca toca em
     * {@code t_tenant_role}. Não pode ser apagado por nenhum refactor futuro.
     */
    @Test
    void updateMoldes_nuncaEscreveEmTTenantRole() {
        Permission clientesView = permissao(1, "clientes:view", "Ver Clientes", "Clientes", 1);
        Role advogado = molde(10, "ADVOGADO", new HashSet<>());
        when(roleRepository.findById(10)).thenReturn(Optional.of(advogado));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of(clientesView));
        lenient().when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(advogado));
        lenient().when(tenantRoleRepository.countByMoldeId(any())).thenReturn(5L);

        MoldesUpdateRequest.MoldePermissoesEntry entrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        entrada.setId(10);
        entrada.setPermissoes(List.of("clientes:view"));
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(entrada));

        novoController().updateMoldes(request);

        verify(tenantRoleRepository, never()).save(any());
        verify(tenantRoleRepository, never()).delete(any());
        verify(tenantRoleRepository, never()).deleteAll();
        verify(tenantRoleRepository, never()).findByTenantId(any());
        // A unica interacao permitida com tenantRoleRepository e a contagem de leitura dentro de
        // toMoldeDto, usada para reprojectar a resposta.
        verify(tenantRoleRepository, times(1)).countByMoldeId(anyInt());
    }

    @Test
    void updateMoldes_comIdInexistenteDevolve404ENuncaGrava() {
        when(roleRepository.findById(999)).thenReturn(Optional.empty());
        MoldesUpdateRequest.MoldePermissoesEntry entrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        entrada.setId(999);
        entrada.setPermissoes(List.of());
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(entrada));

        ResponseEntity<?> response = novoController().updateMoldes(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateMoldes_comPapelNaoInstanciavelDevolve400ENuncaGrava() {
        Role naoMolde = Role.builder().id(20).nome("QUALQUER").instanciavel(false).permissions(new HashSet<>()).build();
        when(roleRepository.findById(20)).thenReturn(Optional.of(naoMolde));
        MoldesUpdateRequest.MoldePermissoesEntry entrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        entrada.setId(20);
        entrada.setPermissoes(List.of());
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(entrada));

        ResponseEntity<?> response = novoController().updateMoldes(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateMoldes_comPlataformaAdminDevolve400ENuncaGrava() {
        Role plataformaAdmin = Role.builder().id(1).nome("PLATAFORMA_ADMIN").instanciavel(false).permissions(new HashSet<>()).build();
        when(roleRepository.findById(1)).thenReturn(Optional.of(plataformaAdmin));
        MoldesUpdateRequest.MoldePermissoesEntry entrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        entrada.setId(1);
        entrada.setPermissoes(List.of());
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(entrada));

        ResponseEntity<?> response = novoController().updateMoldes(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateMoldes_comChaveDePermissaoDesconhecidaDevolve400NomeandoAChaveENuncaGrava() {
        Role advogado = molde(10, "ADVOGADO", new HashSet<>());
        when(roleRepository.findById(10)).thenReturn(Optional.of(advogado));
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        MoldesUpdateRequest.MoldePermissoesEntry entrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        entrada.setId(10);
        entrada.setPermissoes(List.of("chave:inexistente"));
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(entrada));

        ResponseEntity<?> response = novoController().updateMoldes(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("chave:inexistente"));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateMoldes_comListaDeMoldesVaziaDevolve400ENuncaGrava() {
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of());

        ResponseEntity<?> response = novoController().updateMoldes(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(roleRepository, never()).save(any());
    }

    // WR-02 (125-REVIEW.md): resolvido era um Map<Role, ...> chaveado por Role.equals() (sobre
    // "nome"), pelo que duas entradas do pedido com o mesmo id resolviam para a mesma chave e a
    // segunda sobrescrevia silenciosamente a primeira -- so a ultima entrada duplicada era
    // gravada, contradizendo a promessa de validate-then-write do proprio doc-comment do metodo.
    // Prova que o pedido inteiro e agora recusado, nomeando o id duplicado, e que roleRepository
    // nunca chega a gravar nada.
    @Test
    void updateMoldes_comIdDuplicadoDevolve400NomeandoOIdENuncaGrava() {
        Role advogado = molde(10, "ADVOGADO", new HashSet<>());
        lenient().when(roleRepository.findById(10)).thenReturn(Optional.of(advogado));
        lenient().when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());

        MoldesUpdateRequest.MoldePermissoesEntry primeiraEntrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        primeiraEntrada.setId(10);
        primeiraEntrada.setPermissoes(List.of());
        MoldesUpdateRequest.MoldePermissoesEntry segundaEntradaDuplicada = new MoldesUpdateRequest.MoldePermissoesEntry();
        segundaEntradaDuplicada.setId(10);
        segundaEntradaDuplicada.setPermissoes(List.of());
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(primeiraEntrada, segundaEntradaDuplicada));

        ResponseEntity<?> response = novoController().updateMoldes(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("10"));
        verify(roleRepository, never()).save(any());
    }

    // ---- Grupo A: comportamento de createMolde ----

    @Test
    void createMolde_normalizaNomeParaMaiusculasEGravaComInstanciavelTrue() {
        when(roleRepository.findByNome("SUPERVISOR")).thenReturn(Optional.empty());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role r = invocation.getArgument(0);
            r.setId(50);
            return r;
        });
        MoldeCreateRequest request = new MoldeCreateRequest();
        request.setNome("  supervisor  ");
        request.setPermissoes(List.of());

        ResponseEntity<?> response = novoController().createMolde(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        MoldeProvisionResponse corpo = (MoldeProvisionResponse) response.getBody();
        assertNotNull(corpo);
        assertEquals("SUPERVISOR", corpo.getNome());
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertEquals("SUPERVISOR", captor.getValue().getNome());
        assertEquals(Boolean.TRUE, captor.getValue().getInstanciavel());
    }

    @Test
    void createMolde_recusaPlataformaAdminEmQualquerCaixaComMensagemExataENuncaGrava() {
        MoldeCreateRequest request = new MoldeCreateRequest();
        request.setNome("plataforma_admin");
        request.setPermissoes(List.of());

        ResponseEntity<?> response = novoController().createMolde(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(java.util.Map.of("message", "Este nome está reservado à plataforma e não pode ser usado como molde."),
                response.getBody());
        verify(roleRepository, never()).save(any());
        verify(roleRepository, never()).findByNome(any());
    }

    @Test
    void createMolde_recusaNomeDuplicadoViaPreCheckENuncaGrava() {
        Role existente = molde(10, "ADVOGADO", new HashSet<>());
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(existente));
        MoldeCreateRequest request = new MoldeCreateRequest();
        request.setNome("advogado");
        request.setPermissoes(List.of());

        ResponseEntity<?> response = novoController().createMolde(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void createMolde_convertDataIntegrityViolationExceptionEmDuplicacaoConcorrenteEm400() {
        when(roleRepository.findByNome("SUPERVISOR")).thenReturn(Optional.empty());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(roleRepository.save(any(Role.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        MoldeCreateRequest request = new MoldeCreateRequest();
        request.setNome("SUPERVISOR");
        request.setPermissoes(List.of());

        ResponseEntity<?> response = novoController().createMolde(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(java.util.Map.of("message", "Já existe um papel com este nome."), response.getBody());
    }

    // ---- Grupo B: gate de autorização real dos 3 handlers novos ----

    /**
     * Caso mais importante do ficheiro junto com o 5 (nunca escreve em t_tenant_role): prova, por
     * proxy AOP real, que um ADMIN de escritório é recusado nos 3 handlers novos. Não pode ser
     * apagado por nenhum refactor futuro.
     */
    @Test
    void listMoldes_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios() {
        autenticarComoRoles("ADMIN");
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, proxy::listMoldes);
        verify(roleRepository, never()).findAllByInstanciavelTrue();
        verify(permissionRepository, never()).findAllByReservadaPlataformaFalse();
    }

    @Test
    void updateMoldes_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios() {
        autenticarComoRoles("ADMIN");
        MoldesUpdateRequest.MoldePermissoesEntry entrada = new MoldesUpdateRequest.MoldePermissoesEntry();
        entrada.setId(10);
        entrada.setPermissoes(List.of());
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of(entrada));
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.updateMoldes(request));
        verify(roleRepository, never()).findById(any());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void createMolde_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios() {
        autenticarComoRoles("ADMIN");
        MoldeCreateRequest request = new MoldeCreateRequest();
        request.setNome("SUPERVISOR");
        request.setPermissoes(List.of());
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.createMolde(request));
        verify(roleRepository, never()).findByNome(any());
        verify(roleRepository, never()).save(any());
    }

    @Test
    void listMoldes_comRolePlataformaAdminAtravessaOGate() {
        // hasRole('PLATAFORMA_ADMIN') exige a autoridade prefixada "ROLE_PLATAFORMA_ADMIN" --
        // autenticarComoRoles nao acrescenta o prefixo (ao contrario de UserPrincipal.create para
        // roles), por isso o valor passado aqui tem de o incluir explicitamente.
        autenticarComoRoles("ROLE_PLATAFORMA_ADMIN");
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of());
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(proxy::listMoldes);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void updateMoldes_comRolePlataformaAdminAtravessaOGate() {
        autenticarComoRoles("ROLE_PLATAFORMA_ADMIN");
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of());
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.updateMoldes(request));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createMolde_comRolePlataformaAdminAtravessaOGate() {
        autenticarComoRoles("ROLE_PLATAFORMA_ADMIN");
        when(roleRepository.findByNome("SUPERVISOR")).thenReturn(Optional.empty());
        when(permissionRepository.findAllByReservadaPlataformaFalse()).thenReturn(List.of());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
            Role r = invocation.getArgument(0);
            r.setId(50);
            return r;
        });
        MoldeCreateRequest request = new MoldeCreateRequest();
        request.setNome("SUPERVISOR");
        request.setPermissoes(List.of());
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.createMolde(request));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // Contexto vazio -- nenhuma autenticacao definida no SecurityContextHolder (nem sequer um
    // Authentication anonimo). O interceptor de method security nao consegue sequer avaliar
    // hasRole(...) sem um Authentication presente, pelo que a excecao real e
    // AuthenticationCredentialsNotFoundException, nao AccessDeniedException -- ambas recusam o
    // pedido antes do metodo correr, mas sao tipos irmaos (as duas estendem
    // SpringSecurityException, nenhuma estende a outra), por isso o teste afirma o tipo exato em
    // vez de generalizar para AccessDeniedException.

    @Test
    void listMoldes_semAutenticacaoNenhumaERecusado() {
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AuthenticationCredentialsNotFoundException.class, proxy::listMoldes);
    }

    @Test
    void updateMoldes_semAutenticacaoNenhumaERecusado() {
        MoldesUpdateRequest request = new MoldesUpdateRequest();
        request.setMoldes(List.of());
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> proxy.updateMoldes(request));
    }

    @Test
    void createMolde_semAutenticacaoNenhumaERecusado() {
        MoldeCreateRequest request = new MoldeCreateRequest();
        request.setNome("SUPERVISOR");
        request.setPermissoes(List.of());
        PlatformAdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> proxy.createMolde(request));
    }
}
