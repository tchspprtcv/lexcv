package com.lexcv.controllers;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prova comportamental (ISOL-03, Phase 121) de que a anotacao de metodo
 * {@code @PreAuthorize("hasRole('PLATAFORMA_ADMIN')")} de {@code updateRbac} substitui -- e nao
 * soma a -- a anotacao de classe {@code @PreAuthorize("hasRole('ADMIN')")} de
 * {@link AdminController}.
 *
 * <p>Este ficheiro existe separado de {@link AdminControllerPlataformaAdminContencaoTest} porque
 * prova uma coisa diferente: aqui prova-se o <em>gate de autorizacao</em> em si -- algo que so um
 * proxy AOP real de method security consegue avaliar, nunca uma chamada Java direta ao controller
 * (que trata {@code @PreAuthorize} como metadados inertes). O ficheiro de contencao prova
 * <em>guardas de corpo de handler</em> (ex.: {@code updateRbac} ignorar uma entrada
 * "PLATAFORMA_ADMIN"), chamando o controller diretamente -- as duas provas sao complementares, uma
 * nao substitui a outra.
 *
 * <p>{@link AdminController} e a primeira classe deste codebase a combinar uma anotacao de classe
 * com uma anotacao de metodo mais especifica na mesma classe (confirmado por grep de todos os
 * {@code @PreAuthorize} em {@code controllers/}) -- por isso a semantica "a mais especifica ganha"
 * do Spring Security e provada aqui comportamentalmente, com um proxy CGLIB real
 * ({@link AuthorizationManagerBeforeMethodInterceptor#preAuthorize()}), e nunca assumida por
 * analogia com outro controller.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerRbacAutorizacaoTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AdminController novoController() {
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder, tenantRepository);
    }

    /**
     * Monta o proxy AOP de method security necessario para avaliar {@code @PreAuthorize} de facto
     * -- uma chamada Java direta a {@link #novoController()} nunca o faz. Copia exata do padrao
     * estabelecido em {@code PlatformAdminControllerTest.novoProxyComMethodSecurity()} (Phase 119),
     * so mudando o tipo de retorno para {@link AdminController}.
     */
    private AdminController novoProxyComMethodSecurity() {
        ProxyFactory factory = new ProxyFactory(novoController());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (AdminController) factory.getProxy();
    }

    /**
     * Autentica com autoridades ja prefixadas ("ROLE_..."), nunca cruas -- ver 121-PATTERNS.md
     * para a armadilha explicita de nao copiar {@code PlatformAdminControllerTest.autenticarComoRoles}
     * (que constroi autoridades sem o prefixo "ROLE_"). Se usassemos autoridades nao prefixadas
     * aqui, uma recusa seria trivialmente verdadeira pelo motivo errado -- a autoridade nunca
     * satisfaria nenhum {@code hasRole(...)}, logo o teste nao distinguiria "o override funciona"
     * de "o contexto de seguranca estava simplesmente vazio". {@code updateRbac} nunca le
     * {@code SecurityContextHolder} no seu corpo, por isso uma lista de autoridades sem
     * {@code UserPrincipal} e suficiente (mais simples que {@code autenticarComoPlataformaAdmin}).
     */
    private void autenticarComoAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> autoridades = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
    }

    // Direcao 1: um chamador que genuinamente satisfaz o GATE DE CLASSE antigo (hasRole('ADMIN'),
    // ou seja, detem a autoridade prefixada "ROLE_ADMIN") e, mesmo assim, recusado -- prova que a
    // anotacao de metodo SUBSTITUI, e nao apenas soma a, a anotacao de classe para este metodo
    // especifico. Sem stubs: o corpo do handler nunca corre neste estado (nem em GREEN, para este
    // chamador), por isso qualquer stub ficaria por usar e dispararia UnnecessaryStubbingException
    // do modo estrito do Mockito.
    @Test
    void updateRbac_comRoleAdminDeTenantNormalERecusadoMesmoSatisfazendoOGateDeClasse() {
        autenticarComoAuthorities("ROLE_ADMIN");
        Map<String, Object> body = Map.of("rolePermissions", Map.of("ASSISTENTE", List.of("clientes:view")));
        AdminController proxy = novoProxyComMethodSecurity();

        assertThrows(AccessDeniedException.class, () -> proxy.updateRbac(body));
        verify(roleRepository, never()).save(any());
    }

    // Direcao 2 (o inverso): um chamador que NAO satisfaz de todo o hasRole('ADMIN') de classe, mas
    // DETEM ROLE_PLATAFORMA_ADMIN, ainda assim atravessa o gate -- prova que as duas anotacoes nao
    // sao combinadas com E logico (se fossem, este chamador seria tambem recusado erradamente).
    @Test
    void updateRbac_comRolePlataformaAdminPassaOGateMesmoSemHasRoleAdmin() {
        autenticarComoAuthorities("ROLE_PLATAFORMA_ADMIN");
        when(roleRepository.findByNome("ASSISTENTE"))
                .thenReturn(Optional.of(Role.builder().id(4).nome("ASSISTENTE").build()));
        when(permissionRepository.findByNome("clientes:view"))
                .thenReturn(Optional.of(Permission.builder().id(1).nome("clientes:view").build()));
        Map<String, Object> body = Map.of("rolePermissions", Map.of("ASSISTENTE", List.of("clientes:view")));
        AdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.updateRbac(body));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(roleRepository, times(1)).save(any());
    }

    // Prova, por reflexao, que updateRbac tem a sua propria anotacao de metodo com o valor exato
    // esperado -- espelha anotacaoDeClasseTemValorExatoHasRolePlataformaAdmin de
    // PlatformAdminControllerTest.
    @Test
    void updateRbac_temAnotacaoDeMetodoComValorExatoHasRolePlataformaAdmin() throws NoSuchMethodException {
        Method metodo = AdminController.class.getMethod("updateRbac", Map.class);
        PreAuthorize anotacao = metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(anotacao);
        assertEquals("hasRole('PLATAFORMA_ADMIN')", anotacao.value());
    }

    // CR-01 (121-REVIEW.md): a decisao original de 121-CONTEXT.md era deixar getRbac sem anotacao
    // de metodo -- mas isso deixava o unico chamador capaz de escrever a matriz (PLATAFORMA_ADMIN)
    // sem nenhuma forma de a ler primeiro (ver o comentario do handler). Este teste substitui
    // getRbac_continuaSemAnotacaoDeMetodo, cuja premissa (nenhuma anotacao de metodo) deixou de
    // ser verdadeira com este fix: prova, por reflexao, que getRbac passa a ter a sua propria
    // anotacao de metodo, alargada para aceitar tambem PLATAFORMA_ADMIN sem deixar de aceitar
    // ADMIN.
    @Test
    void getRbac_temAnotacaoDeMetodoComValorExatoHasRoleAdminOuPlataformaAdmin() throws NoSuchMethodException {
        Method metodo = AdminController.class.getMethod("getRbac");
        PreAuthorize anotacao = metodo.getAnnotation(PreAuthorize.class);

        assertNotNull(anotacao);
        assertEquals("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')", anotacao.value());
    }

    // CR-01 (121-REVIEW.md): prova comportamental, pelo proxy real de method security, de que um
    // chamador com apenas ROLE_PLATAFORMA_ADMIN (o unico papel que o utilizador seedado em
    // DatabaseSeeder.seedUtilizadorPlataforma alguma vez detem) agora consegue ler a matriz -- o
    // gap real que motivou este fix: antes, este chamador passava em updateRbac mas era sempre
    // recusado em getRbac, sem nenhuma forma de validar o estado atual antes de o substituir.
    @Test
    void getRbac_comRolePlataformaAdminObtemSucesso() {
        autenticarComoAuthorities("ROLE_PLATAFORMA_ADMIN");
        when(roleRepository.findAll()).thenReturn(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.getRbac());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // CR-01 (121-REVIEW.md): nao-regressao -- um ADMIN de tenant continua a conseguir ler a matriz
    // tal como antes deste fix; o alargamento e estritamente aditivo (uma segunda autoridade
    // aceite), nunca retira o acesso que ja existia.
    @Test
    void getRbac_comRoleAdminContinuaAObterSucesso() {
        autenticarComoAuthorities("ROLE_ADMIN");
        when(roleRepository.findAll()).thenReturn(List.of());
        AdminController proxy = novoProxyComMethodSecurity();

        ResponseEntity<?> response = assertDoesNotThrow(() -> proxy.getRbac());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Guarda de regressao para os restantes handlers de AdminController (utilizadores, etc.), que
    // continuam governados apenas pelo gate de classe.
    @Test
    void anotacaoDeClasseContinuaHasRoleAdmin() {
        PreAuthorize anotacao = AdminController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(anotacao);
        assertEquals("hasRole('ADMIN')", anotacao.value());
    }
}
