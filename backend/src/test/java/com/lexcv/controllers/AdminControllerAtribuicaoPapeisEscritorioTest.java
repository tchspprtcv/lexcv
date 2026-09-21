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
 * Phase 126 (Plan 04): prova o caminho de escrita de {@link AdminController#createUser} /
 * {@link AdminController#updateUser} que tem de acompanhar o cutover de leitura -- sem povoar
 * {@code tenantRoles}, uma alteracao de papel devolveria {@code 200} sem nenhum efeito real na
 * autoridade do utilizador (ver o objectivo de 126-04-PLAN.md). Usa
 * {@link ResolucaoPapeisService} REAL (construido sobre {@link TenantRoleRepository}/
 * {@link RoleRepository} mockados) para que {@code resolverPapeisDeEscritorio} seja exercitado a
 * serio, nunca stubado directamente.
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

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "novo@lexcv.cv";
    private static final String PASSWORD = "Pa$$w0rd1";

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
                tenantRepository, resolucaoPapeisService);
    }

    private Map<String, Object> corpoCriacaoComRoles(List<String> roles) {
        return Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "roles", roles
        );
    }

    private User utilizadorExistente(Set<Role> roles) {
        return User.builder()
                .id(USER_ID)
                .tenantId(TENANT_ID)
                .nome("Utilizador Existente")
                .email("existente@lexcv.cv")
                .ativo(true)
                .roles(roles)
                .build();
    }

    // Caso 1: createUser com roles: ["ADVOGADO"] num tenant que tem o TenantRole homonimo -> o
    // User capturado por userRepository.save tem tenantRoles com esse papel E roles com o papel
    // global. Os dois povoados -- a fase e aditiva (126-CONTEXT.md Decisao 4).
    @Test
    void createUser_comTenantRoleHomonimo_povoaTenantRolesEMantemRolesGlobal() {
        autenticarComoPrincipalDoTenant();
        Role advogadoGlobal = Role.builder().id(1).nome("ADVOGADO").build();
        TenantRole advogadoEscritorio = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ADVOGADO").build();
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(advogadoGlobal));
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "ADVOGADO")).thenReturn(Optional.of(advogadoEscritorio));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().createUser(corpoCriacaoComRoles(List.of("ADVOGADO")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User salvo = captor.getValue();
        assertEquals(Set.of(advogadoGlobal), salvo.getRoles());
        assertEquals(Set.of(advogadoEscritorio), salvo.getTenantRoles());
    }

    // Caso 2 (o que falha se a escrita de escritorio for esquecida): updateUser a mudar de
    // ADVOGADO para ASSISTENTE -> o User capturado tem tenantRoles a apontar para o TenantRole
    // ASSISTENTE do tenant. Sem a chamada a setTenantRoles em updateUser, este caso falharia --
    // o endpoint devolveria 200 mas a autoridade do utilizador nao mudaria, porque o resolvedor
    // le o lado de escritorio (que ficaria parado em ADVOGADO) e ignoraria o papel global
    // recem-escrito.
    @Test
    void updateUser_deAdvogadoParaAssistente_atualizaTenantRolesParaORefletirNaAutoridade() {
        autenticarComoPrincipalDoTenant();
        Role advogadoGlobal = Role.builder().id(1).nome("ADVOGADO").build();
        User existente = utilizadorExistente(Set.of(advogadoGlobal));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existente));

        Role assistenteGlobal = Role.builder().id(2).nome("ASSISTENTE").build();
        TenantRole assistenteEscritorio = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).nome("ASSISTENTE").build();
        when(roleRepository.findByNome("ASSISTENTE")).thenReturn(Optional.of(assistenteGlobal));
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "ASSISTENTE")).thenReturn(Optional.of(assistenteEscritorio));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(USER_ID, Map.of("roles", List.of("ASSISTENTE")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User salvo = captor.getValue();
        assertEquals(Set.of(assistenteGlobal), salvo.getRoles());
        assertEquals(Set.of(assistenteEscritorio), salvo.getTenantRoles());
    }

    // Caso 3: tenant sem o TenantRole homonimo (por exemplo antes de a conversao correr) ->
    // tenantRoles fica vazio, sem excepcao; o utilizador continua a resolver por papeis globais
    // (ResolucaoPapeisService.usaPapeisDeEscritorio dá false quando tenantRoles esta vazio).
    @Test
    void createUser_semTenantRoleHomonimo_deixaTenantRolesVazioSemExcecao() {
        autenticarComoPrincipalDoTenant();
        Role advogadoGlobal = Role.builder().id(1).nome("ADVOGADO").build();
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(advogadoGlobal));
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "ADVOGADO")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().createUser(corpoCriacaoComRoles(List.of("ADVOGADO")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().getTenantRoles().isEmpty());
    }

    // Caso 4 (isolamento multi-tenant): um TenantRole com o mesmo nome noutro tenantId nao e
    // associado -- resolverPapeisDeEscritorio e chamado com principal.getTenantId(), nunca um
    // tenant do corpo do pedido; o mock de findByTenantIdAndNome prova qual o tenantId realmente
    // usado (so o do principal esta estubado a devolver algo; o de outro tenant, se chamado,
    // devolveria Optional.empty() por omissao do mock).
    @Test
    void createUser_comTenantRoleHomonimoNoutroTenant_naoAssociaOPapelDeOutroEscritorio() {
        autenticarComoPrincipalDoTenant();
        Role advogadoGlobal = Role.builder().id(1).nome("ADVOGADO").build();
        TenantRole advogadoDeOutroTenant = TenantRole.builder().id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID).nome("ADVOGADO").build();
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(advogadoGlobal));
        // O papel homonimo existe SO no outro tenant -- o proprio tenant do principal (TENANT_ID)
        // e estubado explicitamente para Optional.empty(), provando que resolverPapeisDeEscritorio
        // foi mesmo chamado com o tenantId do principal (nao com OUTRO_TENANT_ID, que teria
        // encontrado o papel). Mockito em modo estrito rejeitaria uma chamada com TENANT_ID sem
        // este stub explicito, por isso nao basta estubar so o outro tenant.
        when(tenantRoleRepository.findByTenantIdAndNome(TENANT_ID, "ADVOGADO")).thenReturn(Optional.empty());
        // lenient(): este stub existe para provar, por ausencia de chamada, que o controller
        // NUNCA consulta o TenantRole de outro tenant -- se o codigo estivesse errado e usasse
        // OUTRO_TENANT_ID, este stub seria consumido; como esperamos que nunca o seja, o modo
        // estrito do Mockito marcaria como stubbing nao usado sem este relaxamento.
        lenient().when(tenantRoleRepository.findByTenantIdAndNome(OUTRO_TENANT_ID, "ADVOGADO"))
                .thenReturn(Optional.of(advogadoDeOutroTenant));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().createUser(corpoCriacaoComRoles(List.of("ADVOGADO")));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().getTenantRoles().isEmpty());
        verify(tenantRoleRepository).findByTenantIdAndNome(eq(TENANT_ID), eq("ADVOGADO"));
        verify(tenantRoleRepository, never()).findByTenantIdAndNome(eq(OUTRO_TENANT_ID), any());
    }

    // Caso 5 (a guarda de plataforma continua a fechar): roles: ["PLATAFORMA_ADMIN"] devolve 403
    // e nenhuma escrita acontece -- nem global, nem de escritorio.
    @Test
    void createUser_comPapelDePlataforma_devolve403ENuncaGrava() {
        autenticarComoPrincipalDoTenant();

        ResponseEntity<?> response = novoController().createUser(corpoCriacaoComRoles(List.of("PLATAFORMA_ADMIN")));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 6: listUsers devolve as permissoes resolvidas pelo lado de escritorio para um
    // utilizador convertido, e pelo lado global para um nao convertido -- prova comportamental
    // de que a leitura (Task 3, sitio 1) tambem usa o resolvedor real.
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
