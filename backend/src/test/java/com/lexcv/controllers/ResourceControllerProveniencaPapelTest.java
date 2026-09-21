package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.Cliente;
import com.lexcv.models.ClienteAdministrativo;
import com.lexcv.models.ClienteAdvogado;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.*;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import com.lexcv.services.RiscoPrazoService;
import com.lexcv.services.StorageService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 126 (Plano 05): prova que {@code ResourceController#addClienteAdvogado}/
 * {@code addClienteAdministrativo} resolvem por PROVENIENCIA do molde (via
 * {@link ResolucaoPapeisService#temPapelDeMolde}), nao por comparacao de nome, incluindo o
 * predicado composto (OR de dois moldes) de {@code addClienteAdministrativo}, provado com as
 * duas metades separadamente.
 *
 * <p>Sem MockMvc/{@code @SpringBootTest} -- convencao desta codebase (ver
 * {@code ResourceControllerUploadDocumentoTest}): instanciacao directa do controller com todos
 * os colaboradores mockados, excepto {@link ResolucaoPapeisService}, que e REAL sobre
 * {@link TenantRoleRepository}/{@link RoleRepository} mockados para que
 * {@code temPapelDeMolde} seja exercitado a serio.
 */
@ExtendWith(MockitoExtension.class)
class ResourceControllerProveniencaPapelTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private ClienteContactoRepository clienteContactoRepository;
    @Mock private ClienteNotaRepository clienteNotaRepository;
    @Mock private ContaCorrenteRepository contaCorrenteRepository;
    @Mock private ProcessoRepository processoRepository;
    @Mock private ParteRepository parteRepository;
    @Mock private FaseProcessualRepository faseProcessualRepository;
    @Mock private ProcessoFaseRepository processoFaseRepository;
    @Mock private EventoRepository eventoRepository;
    @Mock private DocumentoRepository documentoRepository;
    @Mock private MovimentacaoRepository movimentacaoRepository;
    @Mock private HonorarioRepository honorarioRepository;
    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private ConflictCheckDecisaoRepository conflictCheckDecisaoRepository;
    @Mock private PrazoRepository prazoRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private StorageService storageService;
    @Mock private RiscoPrazoService riscoPrazoService;
    @Mock private NotificacaoService notificacaoService;
    @Mock private ClienteAdvogadoRepository clienteAdvogadoRepository;
    @Mock private ClienteAdministrativoRepository clienteAdministrativoRepository;
    @Mock private DecisaoRepository decisaoRepository;
    @Mock private TestemunhaRepository testemunhaRepository;
    @Mock private FactoRepository factoRepository;
    @Mock private ParecerSolicitacaoRepository parecerSolicitacaoRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private TenantRoleRepository tenantRoleRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OUTRO_TENANT_ID = UUID.randomUUID();
    private static final UUID ATOR_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private static final Role MOLDE_ADVOGADO = Role.builder().id(1).nome("ADVOGADO").build();
    private static final Role MOLDE_ASSISTENTE = Role.builder().id(2).nome("ASSISTENTE").build();
    private static final Role MOLDE_TECNICO = Role.builder().id(3).nome("TECNICO").build();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoAtorDoTenant() {
        UserPrincipal ator = UserPrincipal.builder().userId(ATOR_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ator, null, List.of()));
    }

    private ResourceController novoController() {
        ResolucaoPapeisService resolucaoPapeisService =
                new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
        return new ResourceController(
                clienteRepository, clienteContactoRepository, clienteNotaRepository, contaCorrenteRepository,
                processoRepository, parteRepository, faseProcessualRepository, processoFaseRepository,
                eventoRepository, documentoRepository, movimentacaoRepository, honorarioRepository,
                pagamentoRepository, conflictCheckDecisaoRepository, prazoRepository, userRepository,
                auditLogRepository, storageService, riscoPrazoService, notificacaoService,
                clienteAdvogadoRepository, clienteAdministrativoRepository, decisaoRepository,
                testemunhaRepository, factoRepository, parecerSolicitacaoRepository, resolucaoPapeisService);
    }

    private void mockarClienteDoTenant() {
        Cliente cliente = Cliente.builder().id(CLIENTE_ID).tenantId(TENANT_ID).build();
        when(clienteRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(cliente));
    }

    private User utilizadorComTenantRole(UUID tenantId, Integer moldeId, String nomePapel) {
        TenantRole papel = TenantRole.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .nome(nomePapel).moldeId(moldeId).build();
        return User.builder().id(USER_ID).tenantId(tenantId).ativo(true).tenantRoles(Set.of(papel)).build();
    }

    private User utilizadorComPapelGlobal(Role papelGlobal) {
        return User.builder().id(USER_ID).tenantId(TENANT_ID).ativo(true).roles(Set.of(papelGlobal)).build();
    }

    // Caso 1: addClienteAdvogado com TenantRole cujo moldeId e o do molde ADVOGADO -> 201, e o
    // ClienteAdvogado e gravado.
    @Test
    void addClienteAdvogado_comTenantRoleDeAdvogado_criaEGrava() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User advogado = utilizadorComTenantRole(TENANT_ID, MOLDE_ADVOGADO.getId(), "ADVOGADO");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(advogado));
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(MOLDE_ADVOGADO));
        when(clienteAdvogadoRepository.findByClienteIdAndUserIdAndTenantId(CLIENTE_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = novoController().addClienteAdvogado(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<ClienteAdvogado> captor = ArgumentCaptor.forClass(ClienteAdvogado.class);
        verify(clienteAdvogadoRepository).save(captor.capture());
        assertEquals(CLIENTE_ID, captor.getValue().getClienteId());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    // Caso 2 (o caso que a Phase 127 quebraria sem esta task): mesmo utilizador, TenantRole
    // RENOMEADO (nome diferente, moldeId igual) -> continua 201.
    @Test
    void addClienteAdvogado_comTenantRoleRenomeado_continuaACriar() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User advogado = utilizadorComTenantRole(TENANT_ID, MOLDE_ADVOGADO.getId(), "Advogado Senior");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(advogado));
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(MOLDE_ADVOGADO));
        when(clienteAdvogadoRepository.findByClienteIdAndUserIdAndTenantId(CLIENTE_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = novoController().addClienteAdvogado(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // Caso 3: addClienteAdvogado com moldeId nulo -> 400 com a mensagem exacta, e nunca grava.
    @Test
    void addClienteAdvogado_comMoldeIdNulo_recusaENuncaGrava() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User papelDeRaiz = utilizadorComTenantRole(TENANT_ID, null, "Advogado Interno");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(papelDeRaiz));
        when(roleRepository.findByNome("ADVOGADO")).thenReturn(Optional.of(MOLDE_ADVOGADO));

        ResponseEntity<?> response = novoController().addClienteAdvogado(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "Utilizador não tem o papel ADVOGADO"), response.getBody());
        verify(clienteAdvogadoRepository, never()).save(any());
    }

    // Caso 4: addClienteAdministrativo com moldeId de ASSISTENTE -> 201 (primeira metade do OR).
    @Test
    void addClienteAdministrativo_comMoldeIdDeAssistente_cria() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User assistente = utilizadorComTenantRole(TENANT_ID, MOLDE_ASSISTENTE.getId(), "ASSISTENTE");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(assistente));
        when(roleRepository.findByNome("ASSISTENTE")).thenReturn(Optional.of(MOLDE_ASSISTENTE));
        lenient().when(roleRepository.findByNome("TECNICO")).thenReturn(Optional.of(MOLDE_TECNICO));
        when(clienteAdministrativoRepository.findByClienteIdAndUserIdAndTenantId(CLIENTE_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = novoController().addClienteAdministrativo(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ArgumentCaptor<ClienteAdministrativo> captor = ArgumentCaptor.forClass(ClienteAdministrativo.class);
        verify(clienteAdministrativoRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    // Caso 5: addClienteAdministrativo com moldeId de TECNICO -> 201 (segunda metade do OR,
    // provada separadamente -- provar so a primeira deixaria a segunda sem rede).
    @Test
    void addClienteAdministrativo_comMoldeIdDeTecnico_cria() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User tecnico = utilizadorComTenantRole(TENANT_ID, MOLDE_TECNICO.getId(), "TECNICO");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(tecnico));
        when(roleRepository.findByNome("ASSISTENTE")).thenReturn(Optional.of(MOLDE_ASSISTENTE));
        when(roleRepository.findByNome("TECNICO")).thenReturn(Optional.of(MOLDE_TECNICO));
        when(clienteAdministrativoRepository.findByClienteIdAndUserIdAndTenantId(CLIENTE_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = novoController().addClienteAdministrativo(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // Caso 6: addClienteAdministrativo com moldeId de ADVOGADO -> 400 com a mensagem exacta.
    @Test
    void addClienteAdministrativo_comMoldeIdDeAdvogado_recusa() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User advogado = utilizadorComTenantRole(TENANT_ID, MOLDE_ADVOGADO.getId(), "ADVOGADO");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(advogado));
        when(roleRepository.findByNome("ASSISTENTE")).thenReturn(Optional.of(MOLDE_ASSISTENTE));
        when(roleRepository.findByNome("TECNICO")).thenReturn(Optional.of(MOLDE_TECNICO));

        ResponseEntity<?> response = novoController().addClienteAdministrativo(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Map.of("message", "Utilizador não tem o papel ASSISTENTE ou TECNICO"), response.getBody());
        verify(clienteAdministrativoRepository, never()).save(any());
    }

    // Caso 7: fallback por nome -- utilizador sem papeis de escritorio com papel global
    // ADVOGADO -> addClienteAdvogado devolve 201.
    @Test
    void addClienteAdvogado_semPapeisDeEscritorio_papelGlobalAdvogado_aceitePorFallback() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User advogadoGlobal = utilizadorComPapelGlobal(MOLDE_ADVOGADO);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(advogadoGlobal));
        when(clienteAdvogadoRepository.findByClienteIdAndUserIdAndTenantId(CLIENTE_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response = novoController().addClienteAdvogado(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    // Caso 8: isolamento de tenant preservado -- utilizador de outro tenant -> 404 com a
    // mensagem existente, provando que a guarda de tenant continua antes da verificacao de
    // papel.
    @Test
    void addClienteAdvogado_utilizadorDeOutroTenant_naoEncontrado() {
        autenticarComoAtorDoTenant();
        mockarClienteDoTenant();
        User advogadoOutroTenant = utilizadorComTenantRole(OUTRO_TENANT_ID, MOLDE_ADVOGADO.getId(), "ADVOGADO");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(advogadoOutroTenant));

        ResponseEntity<?> response = novoController().addClienteAdvogado(CLIENTE_ID, USER_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(Map.of("message", "Utilizador não encontrado"), response.getBody());
    }
}
