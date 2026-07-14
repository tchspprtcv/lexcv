package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.*;
import com.lexcv.repositories.*;
import com.lexcv.services.NotificacaoService;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * WR-02 (Phase 95 code review): {@code uploadDocumento}'s processo-branch team-notification
 * wiring -- building {@code dests} from {@code resolverEquipaCliente(tenantId,
 * proc.getClienteId())} plus {@code proc.getResponsavelId()} and handing it to
 * {@code notificarDocumentoNovo} -- lives entirely in {@link ResourceController} and had no
 * automated coverage. {@code NotificacaoServiceTest} already proves {@code resolverEquipaCliente}
 * and {@code criarComFanOutAdmin} in isolation; this test proves the controller-level assembly
 * point that glues them together for the upload flow.
 *
 * <p>No MockMvc/{@code @SpringBootTest} harness exists anywhere in this codebase (see
 * {@code NotificacaoServiceTest}, {@code AlertasDiariosJobTest}): every existing test instantiates
 * the class under test directly with Mockito-mocked collaborators and calls the method under test
 * as a plain Java call, with no Spring context. This test follows the same convention:
 * {@link ResourceController} is instantiated directly (its {@code @PreAuthorize} annotation is
 * therefore inert here, same as in production unit tests of any other {@code @PreAuthorize}
 * -guarded method in this codebase) and {@link NotificacaoService} is wired as a REAL instance
 * (mirroring {@code AlertasDiariosJobTest}'s "collaborator with no further collaborators is used
 * real" pattern) so the assertions below exercise the actual merge/dedup logic in
 * {@code criarComFanOutAdmin}, not a mocked stand-in for it -- the same depth of proof
 * {@code NotificacaoServiceTest} uses for the sibling {@code notificarFaseEntrada}/
 * {@code notificarProcessoAtribuido} call sites.
 */
@ExtendWith(MockitoExtension.class)
class ResourceControllerUploadDocumentoTest {

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
    @Mock private ClienteAdvogadoRepository clienteAdvogadoRepository;
    @Mock private ClienteAdministrativoRepository clienteAdministrativoRepository;
    @Mock private DecisaoRepository decisaoRepository;
    @Mock private TestemunhaRepository testemunhaRepository;
    @Mock private FactoRepository factoRepository;
    @Mock private ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    // Collaborators of the REAL NotificacaoService instance (not ResourceController constructor
    // params themselves).
    @Mock private NotificacaoRepository notificacaoRepository;
    @Mock private NotificacaoPreferenciaRepository notificacaoPreferenciaRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ATOR_ID = UUID.randomUUID();
    private static final UUID PROCESSO_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID = UUID.randomUUID();
    private static final UUID RESPONSAVEL_ID = UUID.randomUUID();
    private static final UUID ADVOGADO_EQUIPA = UUID.randomUUID();
    private static final UUID ADMINISTRATIVO_EQUIPA = UUID.randomUUID();

    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadDocumento_comProcessoId_notificaEquipaDoClienteMaisResponsavel() throws Exception {
        UserPrincipal ator = UserPrincipal.builder().userId(ATOR_ID).tenantId(TENANT_ID).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ator, null, List.of()));

        Processo processo = Processo.builder()
                .id(PROCESSO_ID)
                .tenantId(TENANT_ID)
                .clienteId(CLIENTE_ID)
                .responsavelId(RESPONSAVEL_ID)
                .build();
        when(processoRepository.findById(PROCESSO_ID)).thenReturn(Optional.of(processo));

        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(CLIENTE_ID, TENANT_ID)).thenReturn(List.of(
                ClienteAdvogado.builder().clienteId(CLIENTE_ID).tenantId(TENANT_ID).userId(ADVOGADO_EQUIPA).build()));
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(CLIENTE_ID, TENANT_ID)).thenReturn(List.of(
                ClienteAdministrativo.builder().clienteId(CLIENTE_ID).tenantId(TENANT_ID).userId(ADMINISTRATIVO_EQUIPA).build()));

        when(userRepository.findById(ADVOGADO_EQUIPA))
                .thenReturn(Optional.of(User.builder().id(ADVOGADO_EQUIPA).tenantId(TENANT_ID).build()));
        when(userRepository.findById(ADMINISTRATIVO_EQUIPA))
                .thenReturn(Optional.of(User.builder().id(ADMINISTRATIVO_EQUIPA).tenantId(TENANT_ID).build()));
        when(userRepository.findById(RESPONSAVEL_ID))
                .thenReturn(Optional.of(User.builder().id(RESPONSAVEL_ID).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of());

        when(notificacaoRepository.inserirSeNaoDuplicado(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("contrato.pdf");
        when(file.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(3L);

        when(storageService.upload(any(), any(), any(), any(InputStream.class), any(), anyLong()))
                .thenReturn("object-key");
        when(documentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService notificacaoService = new NotificacaoService(notificacaoRepository, userRepository,
                notificacaoPreferenciaRepository, clienteAdvogadoRepository, clienteAdministrativoRepository);

        ResourceController controller = new ResourceController(
                clienteRepository, clienteContactoRepository, clienteNotaRepository, contaCorrenteRepository,
                processoRepository, parteRepository, faseProcessualRepository, processoFaseRepository,
                eventoRepository, documentoRepository, movimentacaoRepository, honorarioRepository,
                pagamentoRepository, conflictCheckDecisaoRepository, prazoRepository, userRepository,
                auditLogRepository, storageService, riscoPrazoService, notificacaoService,
                clienteAdvogadoRepository, clienteAdministrativoRepository, decisaoRepository,
                testemunhaRepository, factoRepository, parecerSolicitacaoRepository);

        ResponseEntity<?> response = controller.uploadDocumento(file, PROCESSO_ID, null, null, null, null);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(3)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(Set.of(ADVOGADO_EQUIPA, ADMINISTRATIVO_EQUIPA, RESPONSAVEL_ID),
                Set.copyOf(destinatarioCaptor.getAllValues()));
        assertEquals(3, destinatarioCaptor.getAllValues().size());
        // A equipa do cliente (advogado + administrativo, via resolverEquipaCliente) mais o
        // responsável do processo recebem exatamente uma notificação cada -- prova que a
        // montagem de `dests` em uploadDocumento (ResourceController) está corretamente ligada
        // ao helper de resolução de equipa, sem depender apenas da cobertura isolada de
        // NotificacaoServiceTest sobre resolverEquipaCliente/criarComFanOutAdmin.
    }
}
