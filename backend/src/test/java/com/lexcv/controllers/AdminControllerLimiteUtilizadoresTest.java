package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 117 (PLAN-02/PLAN-04): prova os 4 comportamentos do limite de utilizadores por tenant
 * em {@code AdminController.createUser} -- 409 no limite, 201 abaixo do limite, bypass total
 * quando {@code Tenant.limiteUtilizadores} e {@code null}, e libertacao imediata da vaga depois
 * de um utilizador ser desativado (a contagem de ativos e lida ao vivo em cada pedido, nunca
 * memoizada).
 *
 * <p>CR-01 (117-REVIEW.md): tambem prova o mesmo limite aplicado em {@code AdminController.updateUser}
 * na transicao ativo=false -&gt; ativo=true (reativacao), via o helper partilhado
 * {@code limiteUtilizadoresExcedido} -- 409 ao reativar no limite, 200 ao reativar abaixo do limite, e
 * confirma que um update que nao mexe em {@code ativo} nunca chama a contagem/tenant lookup.
 *
 * <p>Segue a mesma convencao de todos os testes de controller deste codebase (ver
 * {@code ResourceControllerUploadDocumentoTest}): nao existe harness MockMvc/{@code @SpringBootTest}
 * neste projeto -- o controller e instanciado diretamente com colaboradores mockados via Mockito,
 * o metodo sob teste e invocado como uma chamada Java simples, e o {@code SecurityContextHolder}
 * e povoado manualmente com um {@link UserPrincipal} do tenant do caso e limpo em
 * {@code @AfterEach}.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerLimiteUtilizadoresTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
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

    private Map<String, Object> corpoValido() {
        return Map.of(
                "nome", "Novo Utilizador",
                "email", EMAIL,
                "password", PASSWORD,
                "roles", List.of("ADVOGADO")
        );
    }

    private AdminController novoController() {
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder, tenantRepository);
    }

    @Test
    void createUser_noLimiteDevolve409ENaoGravaNada() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).limiteUtilizadores(3).build()));
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(3L);

        ResponseEntity<?> response = novoController().createUser(corpoValido());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(Map.of("message", "Limite de utilizadores atingido para o vosso plano."), response.getBody());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_abaixoDoLimiteDevolve201EGravaUmaVez() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).limiteUtilizadores(3).build()));
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(2L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().createUser(corpoValido());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void createUser_limiteNuloNuncaBloqueiaENaoExecutaContagem() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));
        // limiteUtilizadores fica null (sem limite) por omissao -- o contrato estabelecido em 117-01.
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).build()));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().createUser(corpoValido());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(userRepository, never()).countByTenantIdAndAtivoTrue(any());
    }

    @Test
    void createUser_contagemAoVivoLibertaVagaAposDesativacao() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).limiteUtilizadores(3).build()));
        // Simula um utilizador desativado entre os dois pedidos: a 1a chamada ve 3 ativos (no
        // limite), a 2a ja ve 2 (vaga libertada) -- prova que a contagem e lida ao vivo em cada
        // pedido e nunca memoizada (PLAN-04).
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(3L, 2L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminController controller = novoController();
        ResponseEntity<?> primeiraResposta = controller.createUser(corpoValido());
        ResponseEntity<?> segundaResposta = controller.createUser(corpoValido());

        assertEquals(HttpStatus.CONFLICT, primeiraResposta.getStatusCode());
        assertEquals(HttpStatus.CREATED, segundaResposta.getStatusCode());
    }

    // WR-03 (117-REVIEW.md): antes do fix, o limite era verificado incondicionalmente em
    // createUser, mesmo quando o pedido trazia "ativo": false -- um caso que nunca poderia
    // aumentar a contagem de ativos, mas ainda assim era rejeitado com 409 quando o tenant
    // estava no limite. Este teste prova que o helper deixou de ser chamado nesse caso (nem
    // tenantRepository nem a contagem são tocados), pelo que a criação passa sempre a 201,
    // independentemente do estado real do limite.
    @Test
    void createUser_comAtivoFalseNuncaVerificaLimiteEDevolve201() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(Role.builder().id(1).nome("ADVOGADO").build()));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-irrelevante");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> corpo = new HashMap<>(corpoValido());
        corpo.put("ativo", false);

        ResponseEntity<?> response = novoController().createUser(corpo);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
        verify(tenantRepository, never()).findById(any());
        verify(userRepository, never()).countByTenantIdAndAtivoTrue(any());
    }

    // CR-01 (117-REVIEW.md): AdminController.updateUser era o segundo caminho capaz de tornar um
    // utilizador ativo (reativacao) sem qualquer verificacao do limite, tornando-o totalmente
    // contornavel. Os 3 testes seguintes provam o fix: reativar no limite bloqueia (409, sem save),
    // reativar abaixo do limite passa (200, com save), e um update que nao mexe em `ativo` nunca
    // chama a contagem nem o tenant lookup (custo zero para o caminho comum).

    private User utilizadorExistente(boolean ativo) {
        return User.builder()
                .id(USER_ID)
                .tenantId(TENANT_ID)
                .nome("Utilizador Existente")
                .email("existente@lexcv.cv")
                .ativo(ativo)
                .build();
    }

    @Test
    void updateUser_reativarNoLimiteDevolve409ENaoGravaNada() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(utilizadorExistente(false)));
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).limiteUtilizadores(3).build()));
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(3L);

        ResponseEntity<?> response = novoController().updateUser(USER_ID, Map.of("ativo", true));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(Map.of("message", "Limite de utilizadores atingido para o vosso plano."), response.getBody());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_reativarAbaixoDoLimiteDevolve200EGravaUmaVez() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(utilizadorExistente(false)));
        when(tenantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).limiteUtilizadores(3).build()));
        when(userRepository.countByTenantIdAndAtivoTrue(TENANT_ID)).thenReturn(2L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(USER_ID, Map.of("ativo", true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
    }

    @Test
    void updateUser_editarUtilizadorAtivoSemTocarEmAtivoNuncaVerificaLimite() {
        autenticarComoPrincipalDoTenant();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(utilizadorExistente(true)));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(USER_ID, Map.of("nome", "Nome Atualizado"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, times(1)).save(any());
        verify(tenantRepository, never()).findById(any());
        verify(userRepository, never()).countByTenantIdAndAtivoTrue(any());
    }
}
