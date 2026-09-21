package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.ParecerVersao;
import com.lexcv.models.Role;
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
 * Phase 127 Plano 01 (PAPEL-04, 127-CONTEXT.md Decisao 2a): prova comportamental de que
 * {@code ParecerController.entregarSolicitacao}/{@code createVersao} decidem por PROVENIENCIA do
 * molde ADMIN (via {@link ResolucaoPapeisService#temPapelDeMolde(UserPrincipal, String)}), nao por
 * comparacao do nome literal "ADMIN" -- o problema concreto que esta fase introduz ao deixar um
 * escritorio renomear o seu proprio papel de administrador.
 *
 * <p>Segue a mesma convencao de {@link ParecerControllerProveniencaPapelTest}: {@link
 * ResolucaoPapeisService} REAL sobre {@link TenantRoleRepository}/{@link RoleRepository}
 * mockados -- nunca {@code temPapelDeMolde} stubado directamente -- e principal colocado na
 * {@link SecurityContextHolder}, nao um {@code User} carregado (estes dois sitios so tem o
 * principal, ao contrario de {@code validateAdvogado}).
 */
@ExtendWith(MockitoExtension.class)
class ParecerControllerEntregaProveniencaTest {

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
    private static final UUID SOLICITACAO_ID = UUID.randomUUID();
    private static final UUID VERSAO_ID = UUID.randomUUID();
    private static final UUID ADVOGADO_RESPONSAVEL_ID = UUID.randomUUID();
    private static final UUID OUTRO_UTILIZADOR_ID = UUID.randomUUID();

    private static final Role MOLDE_ADMIN = Role.builder().id(1).nome("ADMIN").build();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(UUID userId, Set<Integer> moldeIds) {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(userId)
                .tenantId(TENANT_ID)
                // Nomes deliberadamente NAO incluem "ADMIN" -- e exactamente o caso que prova a
                // regressao: um escritorio renomeou o papel, mas a proveniencia (moldeId)
                // sobrevive.
                .roles(Set.of("Administrador do Escritório"))
                .moldeIds(moldeIds)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
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
                .advogadoId(ADVOGADO_RESPONSAVEL_ID)
                .build();
    }

    private void mockarEntrega() {
        when(parecerSolicitacaoRepository.findById(SOLICITACAO_ID)).thenReturn(Optional.of(solicitacaoPendente()));
        ParecerVersao versao = ParecerVersao.builder().id(VERSAO_ID).solicitacaoId(SOLICITACAO_ID).build();
        lenient().when(parecerVersaoRepository.findById(VERSAO_ID)).thenReturn(Optional.of(versao));
        lenient().when(parecerSolicitacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(auditLogRepository.save(any())).thenReturn(null);
    }

    private void mockarCriacaoDeVersao() {
        when(parecerSolicitacaoRepository.findByIdForUpdate(SOLICITACAO_ID)).thenReturn(Optional.of(solicitacaoPendente()));
        lenient().when(parecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId(SOLICITACAO_ID)).thenReturn(Optional.empty());
        lenient().when(parecerVersaoRepository.save(any())).thenAnswer(inv -> {
            ParecerVersao v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(UUID.randomUUID());
            }
            return v;
        });
        lenient().when(auditLogRepository.save(any())).thenReturn(null);
    }

    private ResponseEntity<?> entregar(UUID userId, Set<Integer> moldeIds) {
        autenticarComo(userId, moldeIds);
        mockarEntrega();
        return novoController().entregarSolicitacao(SOLICITACAO_ID, VERSAO_ID);
    }

    private ResponseEntity<?> criarVersao(UUID userId, Set<Integer> moldeIds) {
        autenticarComo(userId, moldeIds);
        mockarCriacaoDeVersao();
        return novoController().createVersao(SOLICITACAO_ID, "Conteudo do parecer", null);
    }

    // --- entregarSolicitacao -------------------------------------------------------------

    // Caso 1 (o caso que justifica esta task): principal cujos moldeIds contem o moldeId do
    // molde ADMIN, mas cujos roles NAO contem "ADMIN" (papel de escritorio renomeado) -> aceite.
    // Com a comparacao por nome literal de antes deste plano, este caso reprovaria.
    @Test
    void entregar_moldeIdAdminComPapelRenomeado_aceite() {
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(MOLDE_ADMIN));

        ResponseEntity<?> response = entregar(OUTRO_UTILIZADOR_ID, Set.of(MOLDE_ADMIN.getId()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Caso 2: principal e o advogado responsavel pela solicitacao, mas nao carrega o moldeId
    // ADMIN -> aceite pela via de responsabilidade.
    @Test
    void entregar_advogadoResponsavelSemMoldeAdmin_aceite() {
        lenient().when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());

        ResponseEntity<?> response = entregar(ADVOGADO_RESPONSAVEL_ID, Set.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // Caso 3: principal nem e o advogado responsavel nem carrega o moldeId ADMIN -> recusado com
    // 403 e a mensagem exacta, inalterada.
    @Test
    void entregar_semAdminESemResponsavel_recusadoComMensagemExacta() {
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(MOLDE_ADMIN));

        ResponseEntity<?> response = entregar(OUTRO_UTILIZADOR_ID, Set.of(99));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of("message", "Apenas o advogado responsável ou ADMIN pode entregar o parecer"),
                response.getBody());
    }

    // --- createVersao ----------------------------------------------------------------------

    // Caso 4: mesmo regressao que o Caso 1, agora no endpoint de criar versao.
    @Test
    void criarVersao_moldeIdAdminComPapelRenomeado_aceite() {
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(MOLDE_ADMIN));

        ResponseEntity<?> response = criarVersao(OUTRO_UTILIZADOR_ID, Set.of(MOLDE_ADMIN.getId()));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // Caso 5: advogado responsavel sem moldeId ADMIN -> aceite pela via de responsabilidade.
    @Test
    void criarVersao_advogadoResponsavelSemMoldeAdmin_aceite() {
        lenient().when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());

        ResponseEntity<?> response = criarVersao(ADVOGADO_RESPONSAVEL_ID, Set.of());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // Caso 6: nem responsavel nem ADMIN -> 403 com a mensagem exacta, inalterada.
    @Test
    void criarVersao_semAdminESemResponsavel_recusadoComMensagemExacta() {
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(MOLDE_ADMIN));

        ResponseEntity<?> response = criarVersao(OUTRO_UTILIZADOR_ID, Set.of(99));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(Map.of("message", "Apenas o advogado responsável ou ADMIN pode criar uma versão"),
                response.getBody());
    }
}
