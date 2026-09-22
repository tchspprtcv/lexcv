package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
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
import org.mockito.InOrder;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CR-01 (127-REVIEW.md): prova que {@link AdminController#updateUser} e
 * {@link AdminController#deleteUser} nao conseguem deixar o escritorio sem NENHUM utilizador
 * ACTIVO a deter o papel de administrador do escritorio (discriminado por proveniencia --
 * {@code moldeId} do molde global "ADMIN", nunca por nome, mesma disciplina do resto da fase).
 *
 * <p>Antes desta correcao, tres mecanismos independentes protegiam este papel -- existencia
 * ({@code OfficeRolesController#deleteRole}), piso de permissoes
 * ({@code AdminController#updateRbac}), e auto-eliminacao da PROPRIA conta
 * ({@code AdminController#deleteUser}, que ja recusava {@code principal.getUserId().equals(id)})
 * -- mas nenhum protegia a ATRIBUICAO/ACTIVACAO: {@code updateUser} podia retirar o papel do
 * unico detentor (a si proprio ou a outro utilizador com {@code users:manage} independente),
 * desativa-lo, ou {@code deleteUser} podia apaga-lo, todos em auto-trancamento silencioso de todo
 * o escritorio fora de {@code /api/v1/admin/**} -- exactamente o que PAPEL-08 proibe.
 *
 * <p>Segue a convencao de {@code AdminControllerAtribuicaoPapeisEscritorioTest}: sem MockMvc,
 * instanciacao directa do controller com colaboradores Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerUltimoAdministradorTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRepository tenantRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ADMIN_USER_ID = UUID.randomUUID();
    private static final UUID OUTRO_USER_ID = UUID.randomUUID();
    private static final Integer ADMIN_MOLDE_ID = 1;

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(UUID userId) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private AdminController novoController() {
        ResolucaoPapeisService resolucaoPapeisService =
                new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
                tenantRepository, resolucaoPapeisService, tenantRoleRepository, auditoriaRbacService);
    }

    private TenantRole papelProtegido() {
        return TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("ADMIN").moldeId(ADMIN_MOLDE_ID).sistema(true).build();
    }

    private TenantRole papelNaoProtegido() {
        return TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Consultor").moldeId(null).build();
    }

    private void stubMoldeAdmin() {
        lenient().when(roleRepository.findByNome("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(ADMIN_MOLDE_ID).nome("ADMIN").build()));
    }

    // Caso 1: o unico administrador do escritorio remove o papel protegido de SI PROPRIO via
    // tenantRoleIds (substituindo-o por um papel nao protegido) -- tem de ser recusado com 409,
    // e o utilizador nunca pode ser gravado com o novo conjunto de papeis.
    @Test
    void updateUser_unicoAdministradorRemoveOPapelProtegidoDeSiProprio_recusadoCom409() {
        autenticarComo(ADMIN_USER_ID);
        stubMoldeAdmin();

        TenantRole admin = papelProtegido();
        TenantRole outro = papelNaoProtegido();
        User utilizador = User.builder().id(ADMIN_USER_ID).tenantId(TENANT_ID).nome("Administrador")
                .email("admin@escritorio.cv").ativo(true).tenantRoles(Set.of(admin)).build();

        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(Optional.of(utilizador));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin, outro));
        // Phase 128 (Plano 04): a contagem passa a excluir o proprio utilizador -- "nenhum OUTRO
        // detentor activo" (0), nao mais "so ele" (1, valor pre-128). O significado muda por um:
        // o antigo 1L ("so ele detem, incluido na contagem") corresponde agora a 0L ("nenhum
        // outro"); o antigo 2L ("ele + mais um") corresponde agora a 1L ("mais um, excluindo-o").
        when(userRepository.countByTenantRolesIdAndAtivoTrueAndIdNot(admin.getId(), ADMIN_USER_ID)).thenReturn(0L);

        ResponseEntity<?> response = novoController().updateUser(ADMIN_USER_ID,
                Map.of("tenantRoleIds", List.of(outro.getId().toString())));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 2: o unico administrador ACTIVO e desativado (ativo: true -> false) sem lhe tocar nos
    // papeis -- tem de ser recusado com 409, sem gravar.
    @Test
    void updateUser_unicoAdministradorActivoEDesativado_recusadoCom409() {
        autenticarComo(ADMIN_USER_ID);
        stubMoldeAdmin();

        TenantRole admin = papelProtegido();
        User utilizador = User.builder().id(ADMIN_USER_ID).tenantId(TENANT_ID).nome("Administrador")
                .email("admin@escritorio.cv").ativo(true).tenantRoles(Set.of(admin)).build();

        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(Optional.of(utilizador));
        // Phase 128 (Plano 04): 0 = nenhum OUTRO detentor activo (ver comentario no Caso 1).
        when(userRepository.countByTenantRolesIdAndAtivoTrueAndIdNot(admin.getId(), ADMIN_USER_ID)).thenReturn(0L);

        ResponseEntity<?> response = novoController().updateUser(ADMIN_USER_ID, Map.of("ativo", false));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    // Caso 3: o unico administrador ACTIVO e eliminado por outro utilizador com users:manage
    // proprio -- tem de ser recusado com 409, sem apagar. (deleteUser ja recusa AUTO-eliminacao;
    // este caso prova a lacuna irma -- eliminar o ULTIMO administrador a partir de outra conta.)
    @Test
    void deleteUser_unicoAdministradorActivoEliminadoPorOutro_recusadoCom409() {
        autenticarComo(OUTRO_USER_ID);
        stubMoldeAdmin();

        TenantRole admin = papelProtegido();
        User administrador = User.builder().id(ADMIN_USER_ID).tenantId(TENANT_ID).nome("Administrador")
                .email("admin@escritorio.cv").ativo(true).tenantRoles(Set.of(admin)).build();

        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(Optional.of(administrador));
        // Phase 128 (Plano 04): 0 = nenhum OUTRO detentor activo (ver comentario no Caso 1).
        when(userRepository.countByTenantRolesIdAndAtivoTrueAndIdNot(admin.getId(), ADMIN_USER_ID)).thenReturn(0L);

        ResponseEntity<?> response = novoController().deleteUser(ADMIN_USER_ID);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(userRepository, never()).deleteById(any());
    }

    // Caso 4 (nao-regressao): com DOIS administradores activos, remover o papel protegido de UM
    // deles continua a suceder -- a guarda so recusa quando isso zeraria a contagem de detentores
    // activos, nunca antes disso.
    @Test
    void updateUser_comDoisAdministradoresActivos_removerDeUmSucede() {
        autenticarComo(ADMIN_USER_ID);
        stubMoldeAdmin();

        TenantRole admin = papelProtegido();
        TenantRole outro = papelNaoProtegido();
        User utilizador = User.builder().id(ADMIN_USER_ID).tenantId(TENANT_ID).nome("Administrador")
                .email("admin@escritorio.cv").ativo(true).tenantRoles(Set.of(admin)).build();

        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(Optional.of(utilizador));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin, outro));
        // Phase 128 (Plano 04): 1 = um OUTRO detentor activo (o antigo "2L, ele + mais um" torna-se
        // "1L, so o outro" depois de excluir o proprio utilizador da contagem) -- remover deste
        // ainda deixa um.
        when(userRepository.countByTenantRolesIdAndAtivoTrueAndIdNot(admin.getId(), ADMIN_USER_ID)).thenReturn(1L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(ADMIN_USER_ID,
                Map.of("tenantRoleIds", List.of(outro.getId().toString())));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).save(any());
    }

    // Caso 5 (Plano 04, InOrder): o lock e adquirido ANTES da contagem -- nunca depois. Prova a
    // ORDEM exacta que fecha a corrida documentada no doc-comment de guardaUltimoAdministrador,
    // nao apenas que os dois metodos sao chamados.
    @Test
    void updateUser_removendoOPapelProtegido_bloqueiaAntesDeContar() {
        autenticarComo(ADMIN_USER_ID);
        stubMoldeAdmin();

        TenantRole admin = papelProtegido();
        TenantRole outro = papelNaoProtegido();
        User utilizador = User.builder().id(ADMIN_USER_ID).tenantId(TENANT_ID).nome("Administrador")
                .email("admin@escritorio.cv").ativo(true).tenantRoles(Set.of(admin)).build();

        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(Optional.of(utilizador));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(admin, outro));
        when(userRepository.countByTenantRolesIdAndAtivoTrueAndIdNot(admin.getId(), ADMIN_USER_ID)).thenReturn(1L);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        novoController().updateUser(ADMIN_USER_ID, Map.of("tenantRoleIds", List.of(outro.getId().toString())));

        InOrder ordem = inOrder(tenantRoleRepository, userRepository);
        ordem.verify(tenantRoleRepository).bloquearParaAlteracaoDeDetentores(admin.getId());
        ordem.verify(userRepository).countByTenantRolesIdAndAtivoTrueAndIdNot(admin.getId(), ADMIN_USER_ID);
    }

    // Caso 6 (Plano 04): quando a guarda NAO e relevante -- o utilizador nunca deteve o papel
    // protegido, ou continua a dete-lo depois da operacao -- nem o lock nem a contagem sao
    // chamados. Prova que o lock nao e adquirido em toda escrita de updateUser, so quando a
    // remocao do papel protegido esta realmente em causa.
    @Test
    void updateUser_semTocarNoPapelProtegido_naoBloqueiaNemConta() {
        autenticarComo(ADMIN_USER_ID);
        stubMoldeAdmin();

        TenantRole outro = papelNaoProtegido();
        TenantRole outroAinda = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Financeiro").moldeId(null).build();
        // Este utilizador nunca deteve o papel protegido -- so papeis nao protegidos.
        User utilizador = User.builder().id(ADMIN_USER_ID).tenantId(TENANT_ID).nome("Colaborador")
                .email("colab@escritorio.cv").ativo(true).tenantRoles(Set.of(outro)).build();

        when(userRepository.findById(ADMIN_USER_ID)).thenReturn(Optional.of(utilizador));
        when(tenantRoleRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(outro, outroAinda));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = novoController().updateUser(ADMIN_USER_ID,
                Map.of("tenantRoleIds", List.of(outroAinda.getId().toString())));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(tenantRoleRepository, never()).bloquearParaAlteracaoDeDetentores(any());
        verify(userRepository, never()).countByTenantRolesIdAndAtivoTrueAndIdNot(any(), any());
    }
}
