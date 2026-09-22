package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.UserResponse;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 127 (Plano 05, 127-CONTEXT.md Decisao 6): prova o caminho de atribuicao POR ID de
 * {@link AdminController#createUser} / {@link AdminController#updateUser} -- a correcao
 * estrutural da colisao entre a Fase 126 (409 em mapeamento parcial por nome) e a renomeacao de
 * papeis (Fase 127): a partir desta fase, atribuir um papel de escritorio renomeado nunca mais
 * pode devolver 409. Sucede {@code AdminControllerAtribuicaoPapeisEscritorioTest} da Fase 126
 * (Plano 04), que provava o caminho por NOME -- inteiramente substituido aqui porque esse caminho
 * deixou de existir em {@link AdminController}.
 *
 * <p>Usa {@link ResolucaoPapeisService} REAL (construido sobre {@link TenantRoleRepository}/
 * {@link RoleRepository} mockados) para que {@code resolverNomesPapeis} seja exercitado a serio
 * sempre que um caso precisar de provar autoridade efectiva, nunca stubado directamente.
 *
 * <p>Segue a convencao de {@link AdminControllerLimiteUtilizadoresTest}: sem MockMvc/
 * {@code @SpringBootTest} -- instanciacao directa do controller com colaboradores Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerAtribuicaoPapeisEscritorioTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "novo@lexcv.cv";
    private static final String PASSWORD = "Pa$$w0rd1";

    // moldeId do papel global ADVOGADO -- usado para provar que o mirror é derivado por
    // PROVENIÊNCIA (moldeId), nunca pelo nome actual (possivelmente renomeado) do TenantRole.
    private static final Integer MOLDE_ID_ADVOGADO = 1;
    private static final Integer MOLDE_ID_PLATAFORMA_ADMIN = 99;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoPrincipalDoTenant() {
        UserPrincipal principal = UserPrincipal.builder().userId(USER_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private AdminController novoController() {
        ResolucaoPapeisService resolucaoPapeisService =
                new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository, auditoriaRbacService);
    }

    private Map<String, Object> corpoCriacaoComTenantRoleIds(List<UUID> tenantRoleIds) {
        return Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "tenantRoleIds", tenantRoleIds.stream().map(UUID::toString).toList()
        );
    }

    private User utilizadorExistente(Set<TenantRole> tenantRoles) {
        return User.builder()
                .id(USER_ID)
                .tenantId(TENANT_ID)
                .nome("Utilizador Existente")
                .email("existente@lexcv.cv")
                .ativo(true)
                .tenantRoles(tenantRoles)
                .build();
    }

    private Role advogadoGlobal() {
        return Role.builder().id(MOLDE_ID_ADVOGADO).nome("ADVOGADO").build();
    }

    // Caso 1 (o regressao que este plano existe para fechar, createUser + updateUser): um
    // TenantRole renomeado de "ADVOGADO" para "Advogado Sénior", mas com moldeId ainda a apontar
    // para o molde ADVOGADO, atribui-se sem 409 -- e o mirror global t_user_role e derivado desse
    // moldeId, nunca do nome actual (possivelmente renomeado).
    @Test
    void createUser_comPapelRenomeado_atribuiSemConflitoEDerivaMirrorPorProveniencia() {
        autenticarComoPrincipalDoTenant();
        TenantRole advogadoSenior = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Advogado Sénior").moldeId(MOLDE_ID_ADVOGADO).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(advogadoSenior));
        when(roleRepository.findById(MOLDE_ID_ADVOGADO)).thenReturn(Optional.of(advogadoGlobal()));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComTenantRoleIds(List.of(advogadoSenior.getId())));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User salvo = captor.getValue();
        assertEquals(Set.of(advogadoSenior), salvo.getTenantRoles());
        assertEquals(Set.of(advogadoGlobal()), salvo.getRoles());
    }

    @Test
    void updateUser_comPapelRenomeado_atualizaSemConflitoEDerivaMirrorPorProveniencia() {
        autenticarComoPrincipalDoTenant();
        User existente = utilizadorExistente(Set.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existente));

        TenantRole advogadoSenior = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Advogado Sénior").moldeId(MOLDE_ID_ADVOGADO).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(advogadoSenior));
        when(roleRepository.findById(MOLDE_ID_ADVOGADO)).thenReturn(Optional.of(advogadoGlobal()));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(USER_ID,
                Map.of("tenantRoleIds", List.of(advogadoSenior.getId().toString())));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User salvo = captor.getValue();
        assertEquals(Set.of(advogadoSenior), salvo.getTenantRoles());
        assertEquals(Set.of(advogadoGlobal()), salvo.getRoles());
    }

    // Caso 3: um papel criado de raiz pelo escritorio (moldeId nulo) nao contribui nada para o
    // mirror -- User.roles fica vazio -- mas a autoridade EFECTIVA nao depende do mirror:
    // ResolucaoPapeisService.resolverNomesPapeis (REAL, nunca stubado aqui) continua a devolver o
    // nome do papel de escritorio porque usaPapeisDeEscritorio escolhe esse ramo sempre que
    // tenantRoles nao esta vazio.
    @Test
    void createUser_comPapelSemMolde_naoContribuiParaMirrorMasMantemAutoridadeEfetiva() {
        autenticarComoPrincipalDoTenant();
        TenantRole papelProprio = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor Externo").moldeId(null).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(papelProprio));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComTenantRoleIds(List.of(papelProprio.getId())));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User salvo = captor.getValue();
        assertEquals(Set.of(papelProprio), salvo.getTenantRoles());
        assertTrue(salvo.getRoles().isEmpty());

        ResolucaoPapeisService resolvedorReal = new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        assertTrue(resolvedorReal.resolverNomesPapeis(salvo).contains("Consultor Externo"));
    }

    // Caso 4 (PAPEL-07, createUser + updateUser): um id de TenantRole que so existe noutro tenant
    // e simplesmente ausente do mapa construido a partir de findByTenantId(TENANT_ID) -- 404,
    // nenhuma escrita, e a prova negativa de que o outro tenant nunca e sequer consultado (a
    // inalcancabilidade e estrutural: resolverPapeisEscritorioPorId so chama
    // tenantRoleRepository.findByTenantId com o tenant do principal, nunca com outro).
    @Test
    void createUser_comIdDeOutroTenant_devolve404ENuncaGravaNemConsultaOOutroTenant() {
        autenticarComoPrincipalDoTenant();
        UUID idDeOutroTenant = UUID.randomUUID();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComTenantRoleIds(List.of(idDeOutroTenant)));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(userRepository, never()).save(any());
        verify(tenantRoleRepository, never()).findByTenantId(OUTRO_TENANT_ID);
    }

    @Test
    void updateUser_comIdDeOutroTenant_devolve404ENuncaGravaNemConsultaOOutroTenant() {
        autenticarComoPrincipalDoTenant();
        User existente = utilizadorExistente(Set.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existente));
        UUID idDeOutroTenant = UUID.randomUUID();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        ResponseEntity<?> response = novoController()
                .updateUser(USER_ID, Map.of("tenantRoleIds", List.of(idDeOutroTenant.toString())));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(userRepository, never()).save(any());
        verify(tenantRoleRepository, never()).findByTenantId(OUTRO_TENANT_ID);
    }

    // Caso 5 (isolamento multi-tenant, o sucessor do caso de referencia da Fase 126): dois tenants
    // tem, cada um, um TenantRole chamado "ADVOGADO" -- ids diferentes. Submeter o id do tenant A
    // atribui exactamente a linha do tenant A; o tenant B nunca e consultado, porque
    // resolverPapeisEscritorioPorId so chama tenantRoleRepository.findByTenantId com o tenant do
    // principal autenticado.
    @Test
    void createUser_comPapelHomonimoNoutroTenant_naoAlcancaOOutroEscritorio() {
        autenticarComoPrincipalDoTenant();
        TenantRole advogadoDoTenantA = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO").build();
        // lenient(): existe so para provar, por ausencia de chamada, que o controller nunca
        // consulta os papeis do outro tenant -- se o codigo estivesse errado e usasse
        // OUTRO_TENANT_ID, este stub seria consumido; como esperamos que nunca o seja, o modo
        // estrito do Mockito marcaria como stubbing nao usado sem este relaxamento.
        lenient().when(tenantRoleRepository.findByTenantId(OUTRO_TENANT_ID))
                .thenReturn(List.of(TenantRole.builder().id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ADVOGADO").build()));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(advogadoDoTenantA));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComTenantRoleIds(List.of(advogadoDoTenantA.getId())));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Set.of(advogadoDoTenantA), captor.getValue().getTenantRoles());
        verify(tenantRoleRepository).findByTenantId(eq(TENANT_ID));
        verify(tenantRoleRepository, never()).findByTenantId(eq(OUTRO_TENANT_ID));
    }

    // Caso 6 (PAPEL-09): o papel reservado e inatingivel por DUAS vias independentes -- pelo seu
    // nome literal "PLATAFORMA_ADMIN", e por proveniencia (um TenantRole com outro nome mas cujo
    // moldeId aponta para o molde PLATAFORMA_ADMIN). As duas tem de devolver 403 e nunca gravar.
    @Test
    void createUser_comPapelReservadoPorNome_recusadoCom403ENuncaGrava() {
        autenticarComoPrincipalDoTenant();
        TenantRole plataformaAdmin = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("PLATAFORMA_ADMIN").build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(plataformaAdmin));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComTenantRoleIds(List.of(plataformaAdmin.getId())));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_comPapelReservadoPorProveniencia_recusadoCom403ENuncaGrava() {
        autenticarComoPrincipalDoTenant();
        TenantRole disfarcado = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Chefe de Escritório").moldeId(MOLDE_ID_PLATAFORMA_ADMIN).build();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(disfarcado));
        when(roleRepository.findByNome("PLATAFORMA_ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(MOLDE_ID_PLATAFORMA_ADMIN).nome("PLATAFORMA_ADMIN").build()));

        ResponseEntity<?> response = novoController()
                .createUser(corpoCriacaoComTenantRoleIds(List.of(disfarcado.getId())));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 7: selecao vazia e recusada -- substitui o "Pelo menos uma role válida é obrigatória"
    // de antes desta fase.
    @Test
    void createUser_comSelecaoVaziaDeTenantRoleIds_devolve400ENuncaGrava() {
        autenticarComoPrincipalDoTenant();

        ResponseEntity<?> response = novoController().createUser(corpoCriacaoComTenantRoleIds(List.of()));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 8: um id malformado e recusado antes de qualquer chamada a roleRepository -- so a
    // lista de papeis do tenant e consultada (para construir o mapa de resolucao), nunca a
    // resolucao do moldeId do papel reservado, que so acontece depois de TODOS os ids terem
    // passado no parsing.
    @Test
    void createUser_comIdMalformado_devolve400ENuncaConsultaORoleRepository() {
        autenticarComoPrincipalDoTenant();
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of());

        Map<String, Object> corpo = Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "tenantRoleIds", List.of("nao-e-um-uuid")
        );

        ResponseEntity<?> response = novoController().createUser(corpo);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(((String) body.get("message")).contains("nao-e-um-uuid"));
        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).findByNome(any());
    }

    // Caso 9 (T-127-28): um corpo que ainda envia "roles" e recusado explicitamente com 400 a
    // nomear "tenantRoleIds" -- nunca ignorado em silencio, para que um cliente desatualizado
    // nunca produza uma atribuicao que "nao faz nada".
    @Test
    void createUser_comCorpoRolesAntigo_devolve400ANomearTenantRoleIds() {
        autenticarComoPrincipalDoTenant();

        Map<String, Object> corpo = Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "roles", List.of("ADVOGADO")
        );

        ResponseEntity<?> response = novoController().createUser(corpo);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(((String) body.get("message")).contains("tenantRoleIds"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_comCorpoRolesAntigo_devolve400ANomearTenantRoleIds() {
        autenticarComoPrincipalDoTenant();
        User existente = utilizadorExistente(Set.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existente));

        ResponseEntity<?> response = novoController().updateUser(USER_ID, Map.of("roles", List.of("ADVOGADO")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertTrue(((String) body.get("message")).contains("tenantRoleIds"));
        verify(userRepository, never()).save(any());
    }

    // Caso 10: listUsers reporta a chave de atribuicao (tenant_role_ids) e os nomes efectivos
    // (roles) de forma independente -- um utilizador com dois papeis de escritorio traz os dois
    // ids em tenant_role_ids e os nomes actuais (possivelmente renomeados) em roles.
    @Test
    void listUsers_reportaTenantRoleIdsEnRolesDeFormaIndependente() {
        autenticarComoPrincipalDoTenant();
        TenantRole papel1 = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Advogado Sénior").build();
        TenantRole papel2 = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("TECNICO").build();
        User utilizador = User.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Com Dois Papeis").email("dois@lexcv.cv").ativo(true)
                .roles(Set.of()).tenantRoles(Set.of(papel1, papel2)).permissions(Set.of()).build();
        when(userRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(utilizador));

        ResponseEntity<?> response = novoController().listUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<UserResponse> body = (List<UserResponse>) response.getBody();
        UserResponse resposta = body.stream().filter(r -> r.getEmail().equals("dois@lexcv.cv")).findFirst().orElseThrow();
        assertEquals(Set.of(papel1.getId(), papel2.getId()), resposta.getTenant_role_ids());
        assertEquals(Set.of("Advogado Sénior", "TECNICO"), resposta.getRoles());
    }

    // Caso 11 (nao-regressao): as guardas nao relacionadas com atribuicao de papeis sobrevivem
    // inalteradas -- a recusa de PLATAFORMA_ADMIN/ROLE_PLATAFORMA_ADMIN em "permissions" (CR-01,
    // 119-REVIEW.md) e o limite de utilizadores ativos (Phase 117) continuam cobertos por
    // AdminControllerPlataformaAdminContencaoTest (Casos 9-14) e por
    // AdminControllerLimiteUtilizadoresTest respectivamente -- ambos convertidos ao contrato por
    // id nesta mesma alteracao (127-05-PLAN.md, mandatory_ripple). Nao duplicados aqui.

    // Caso adicional (herdado da Fase 126): listUsers continua a resolver por escritorio para um
    // utilizador convertido e por papeis globais para um nao convertido -- prova comportamental
    // de que a leitura tambem usa o resolvedor real, preservada da versao anterior deste ficheiro.
    @Test
    void listUsers_resolvePorEscritorioParaConvertidoEPorGlobalParaNaoConvertido() {
        autenticarComoPrincipalDoTenant();

        Permission permissaoGlobalApenas = Permission.builder().id(1).nome("financeiro:manage").build();
        Role globalNaoConvertido = Role.builder().id(1).nome("TECNICO").permissions(Set.of(permissaoGlobalApenas)).build();
        User utilizadorNaoConvertido = User.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Nao Convertido").email("nc@lexcv.cv").ativo(true)
                .roles(Set.of(globalNaoConvertido)).tenantRoles(Set.of()).permissions(Set.of()).build();

        Permission permissaoEscritorioApenas = Permission.builder().id(2).nome("processos:view").build();
        TenantRole papelConvertido = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO")
                .permissions(Set.of(permissaoEscritorioApenas)).build();
        User utilizadorConvertido = User.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).nome("Convertido").email("c@lexcv.cv").ativo(true)
                .roles(Set.of()).tenantRoles(Set.of(papelConvertido)).permissions(Set.of()).build();

        when(userRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(utilizadorNaoConvertido, utilizadorConvertido));

        ResponseEntity<?> response = novoController().listUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<UserResponse> body = (List<UserResponse>) response.getBody();
        UserResponse respostaNaoConvertido = body.stream().filter(r -> r.getEmail().equals("nc@lexcv.cv")).findFirst().orElseThrow();
        UserResponse respostaConvertido = body.stream().filter(r -> r.getEmail().equals("c@lexcv.cv")).findFirst().orElseThrow();
        assertTrue(respostaNaoConvertido.getPermissions().contains("financeiro:manage"));
        assertTrue(respostaConvertido.getPermissions().contains("processos:view"));
        assertTrue(respostaConvertido.getRoles().contains("ADVOGADO"));
    }
}
