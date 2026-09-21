package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.AuditLogRepository;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ParecerSolicitacaoRepository;
import com.lexcv.repositories.ParecerVersaoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import com.lexcv.services.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 126 (Plano 05): prova que {@code ParecerController#validateAdvogado} resolve por
 * PROVENIENCIA do molde ADVOGADO (via {@link ResolucaoPapeisService#temPapelDeMolde}), nao por
 * comparacao de nome, e sobrevive a uma renomeacao do papel de escritorio -- o problema concreto
 * que a Phase 127 (PAPEL-04) introduz.
 *
 * <p>Exercitado atraves de {@code PUT /{id}/atribuir} (endpoint publico), nao por invocacao
 * reflexiva do metodo privado {@code validateAdvogado} -- o andaime necessario (mock de
 * {@code parecerSolicitacaoRepository.findById}/{@code save}) e pequeno e o endpoint publico da
 * cobertura adicional gratuita ao caminho HTTP completo. Usa {@link ResolucaoPapeisService} REAL
 * sobre {@link TenantRoleRepository}/{@link RoleRepository} mockados -- convencao desta fase
 * (ver {@code AdminControllerAtribuicaoPapeisEscritorioTest}) -- e nao apenas stubada, para que a
 * resolucao verdadeira seja exercitada.
 */
@ExtendWith(MockitoExtension.class)
class ParecerControllerProveniencaPapelTest {

    @Mock private ParecerSolicitacaoRepository parecerSolicitacaoRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ProcessoRepository processoRepository;
    @Mock private ParecerVersaoRepository parecerVersaoRepository;
    @Mock private StorageService storageService;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private NotificacaoService notificacaoService;
    @Mock private RoleRepository roleRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final UUID ATOR_ID = UUID.randomUUID();
    private static final UUID SOLICITACAO_ID = UUID.randomUUID();
    private static final UUID ADVOGADO_ID = UUID.randomUUID();

    private static final Role MOLDE_ADVOGADO = Role.builder().id(1).nome("ADVOGADO").build();
    private static final Role MOLDE_ASSISTENTE = Role.builder().id(2).nome("ASSISTENTE").build();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoAtorDoTenant() {
        UserPrincipal ator = UserPrincipal.builder().userId(ATOR_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ator, null, List.of()));
    }

    private ParecerController novoController() {
        ResolucaoPapeisService resolucaoPapeisService =
                new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        return new ParecerController(parecerSolicitacaoRepository, userRepository, clienteRepository,
                processoRepository, parecerVersaoRepository, storageService, auditLogRepository,
                notificacaoService, resolucaoPapeisService);
    }

    private ParecerSolicitacao solicitacaoPendente() {
        return ParecerSolicitacao.builder()
                .id(SOLICITACAO_ID)
                .tenantId(TENANT_ID)
                .clienteId(UUID.randomUUID())
                .descricao("Parecer sobre litigio")
                .status("PENDENTE")
                .build();
    }

    private void mockarSolicitacaoESave() {
        when(parecerSolicitacaoRepository.findById(SOLICITACAO_ID)).thenReturn(Optional.of(solicitacaoPendente()));
        // lenient: os casos de recusa nunca chegam a chamar save/auditLog -- validateAdvogado
        // devolve null antes disso.
        lenient().when(parecerSolicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(auditLogRepository.save(any())).thenReturn(null);
    }

    private ResponseEntity<?> atribuir(UUID advogadoId) {
        return novoController().atribuirAdvogado(SOLICITACAO_ID, Map.of("advogadoId", advogadoId.toString()));
    }

    // Caso 1: TenantRole cujo moldeId e o do molde ADVOGADO -> aceite.
    @Test
    void tenantRoleComMoldeIdDeAdvogado_aceite() {
        autenticarComoAtorDoTenant();
        mockarSolicitacaoESave();
        TenantRole papelEscritorio = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("ADVOGADO").moldeId(MOLDE_ADVOGADO.getId()).build();
        User advogado = User.builder().id(ADVOGADO_ID).tenantId(TENANT_ID).ativo(true)
                .tenantRoles(Set.of(papelEscritorio)).build();
        when(userRepository.findById(ADVOGADO_ID)).thenReturn(Optional.of(advogado));
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(MOLDE_ADVOGADO));

        ResponseEntity<?> response = atribuir(ADVOGADO_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Caso 2 (o caso que justifica esta task): mesmo utilizador, TenantRole RENOMEADO para
    // "Advogado Senior" (nome diferente, moldeId igual) -> continua aceite. Com a comparacao por
    // nome de hoje, este caso reprovaria.
    @Test
    void tenantRoleRenomeado_moldeIdPreservado_continuaAceite() {
        autenticarComoAtorDoTenant();
        mockarSolicitacaoESave();
        TenantRole papelRenomeado = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Advogado Senior").moldeId(MOLDE_ADVOGADO.getId()).build();
        User advogado = User.builder().id(ADVOGADO_ID).tenantId(TENANT_ID).ativo(true)
                .tenantRoles(Set.of(papelRenomeado)).build();
        when(userRepository.findById(ADVOGADO_ID)).thenReturn(Optional.of(advogado));
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(MOLDE_ADVOGADO));

        ResponseEntity<?> response = atribuir(ADVOGADO_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Caso 3: TenantRole com moldeId nulo (papel criado de raiz pelo escritorio) -> recusado. O
    // limite preservado de proposito (126-CONTEXT.md, Decisao 2).
    @Test
    void tenantRoleComMoldeIdNulo_recusado() {
        autenticarComoAtorDoTenant();
        mockarSolicitacaoESave();
        TenantRole papelDeRaiz = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("Advogado Interno").moldeId(null).build();
        User advogado = User.builder().id(ADVOGADO_ID).tenantId(TENANT_ID).ativo(true)
                .tenantRoles(Set.of(papelDeRaiz)).build();
        when(userRepository.findById(ADVOGADO_ID)).thenReturn(Optional.of(advogado));
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(MOLDE_ADVOGADO));

        ResponseEntity<?> response = atribuir(ADVOGADO_ID);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // Caso 4: TenantRole cujo moldeId e o do molde ASSISTENTE -> recusado (nao e qualquer papel
    // de escritorio que serve).
    @Test
    void tenantRoleComMoldeIdDeAssistente_recusado() {
        autenticarComoAtorDoTenant();
        mockarSolicitacaoESave();
        TenantRole papelAssistente = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("ASSISTENTE").moldeId(MOLDE_ASSISTENTE.getId()).build();
        User assistente = User.builder().id(ADVOGADO_ID).tenantId(TENANT_ID).ativo(true)
                .tenantRoles(Set.of(papelAssistente)).build();
        when(userRepository.findById(ADVOGADO_ID)).thenReturn(Optional.of(assistente));
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(MOLDE_ADVOGADO));

        ResponseEntity<?> response = atribuir(ADVOGADO_ID);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // Caso 5: utilizador SEM papeis de escritorio, com papel global ADVOGADO -> aceite pelo
    // fallback por nome -- o comportamento de hoje sobrevive para utilizadores ainda nao
    // convertidos.
    @Test
    void semPapeisDeEscritorio_papelGlobalAdvogado_aceitePorFallback() {
        autenticarComoAtorDoTenant();
        mockarSolicitacaoESave();
        Role papelGlobalAdvogado = Role.builder().id(1).nome("ADVOGADO").build();
        User advogado = User.builder().id(ADVOGADO_ID).tenantId(TENANT_ID).ativo(true)
                .roles(Set.of(papelGlobalAdvogado)).build();
        when(userRepository.findById(ADVOGADO_ID)).thenReturn(Optional.of(advogado));

        ResponseEntity<?> response = atribuir(ADVOGADO_ID);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Caso 6: as guardas preexistentes continuam a fechar -- utilizador de outro tenant
    // recusado, e utilizador ativo=false recusado (guarda WR-02, Phase 87). Ambas mockadas com
    // um TenantRole cujo moldeId corresponde ao molde ADVOGADO, para isolar que e a guarda
    // preexistente (nao a verificacao de proveniencia) que recusa cada uma.
    @Test
    void guardasPreexistentesDeTenantEDeAtivo_continuamAFechar() {
        autenticarComoAtorDoTenant();
        mockarSolicitacaoESave();

        TenantRole papelOutroTenant = TenantRole.builder().id(UUID.randomUUID()).tenantId(OUTRO_TENANT_ID)
                .nome("ADVOGADO").moldeId(MOLDE_ADVOGADO.getId()).build();
        User advogadoOutroTenant = User.builder().id(ADVOGADO_ID).tenantId(OUTRO_TENANT_ID).ativo(true)
                .tenantRoles(Set.of(papelOutroTenant)).build();
        when(userRepository.findById(ADVOGADO_ID)).thenReturn(Optional.of(advogadoOutroTenant));
        assertEquals(HttpStatus.BAD_REQUEST, atribuir(ADVOGADO_ID).getStatusCode());

        UUID advogadoInativoId = UUID.randomUUID();
        TenantRole papelEscritorio = TenantRole.builder().id(UUID.randomUUID()).tenantId(TENANT_ID)
                .nome("ADVOGADO").moldeId(MOLDE_ADVOGADO.getId()).build();
        User advogadoInativo = User.builder().id(advogadoInativoId).tenantId(TENANT_ID).ativo(false)
                .tenantRoles(Set.of(papelEscritorio)).build();
        when(userRepository.findById(advogadoInativoId)).thenReturn(Optional.of(advogadoInativo));
        assertEquals(HttpStatus.BAD_REQUEST, atribuir(advogadoInativoId).getStatusCode());
    }
}
