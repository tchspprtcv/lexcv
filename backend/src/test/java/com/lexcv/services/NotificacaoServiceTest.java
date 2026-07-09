package com.lexcv.services;

import com.lexcv.models.Notificacao;
import com.lexcv.models.User;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Primeiro teste Mockito do backend — RiscoPrazoServiceTest é JUnit 5 puro porque
 * RiscoPrazoService não tem colaboradores; NotificacaoService tem dois (NotificacaoRepository,
 * UserRepository), pelo que precisa de mocks. Não existe H2/Testcontainers neste projeto, logo
 * os repositórios são mockados em vez de se usar uma base de dados real.
 *
 * Prova automaticamente as duas garantias de isolamento por-destinatário exigidas pelo NOTF-14:
 * linhas independentes por destinatário (Critério de Sucesso 2, lado da escrita) e uma linha por
 * ADMIN atual do tenant no fan-out (Critério de Sucesso 3) — mais o contrato de mutação de
 * estado lido/não-lido escopado por tenant+destinatario de que o NotificacaoController
 * (Plan 86-03) depende.
 */
@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private UserRepository userRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DESTINATARIO_A = UUID.randomUUID();
    private static final UUID DESTINATARIO_B = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @Test
    void criar_doisDestinatariosDistintos_geramLinhasIndependentesComEstadoLidaProprio() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        when(userRepository.findById(DESTINATARIO_B))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_B).tenantId(TENANT_ID).build()));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link");
        service.criar(TENANT_ID, DESTINATARIO_B, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link");

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
        List<Notificacao> saved = captor.getAllValues();
        assertEquals(DESTINATARIO_A, saved.get(0).getDestinatarioId());
        assertEquals(DESTINATARIO_B, saved.get(1).getDestinatarioId());
        // Linhas independentes, nunca uma linha partilhada — prova o Critério de Sucesso 2
        // no lado da escrita.
    }

    @Test
    void criar_destinatarioDeOutroTenant_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(UUID.randomUUID()).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link"));
        verify(notificacaoRepository, never()).save(any());
        // Prova o exato cenário IDOR-adjacent que o filtro .filter(u -> tenantId.equals(...))
        // existe para bloquear — fecha o gap apontado pelo WR-01 da iteração 2.
    }

    @Test
    void criar_tituloExcede255Caracteres_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "x".repeat(256), "m", "processo", "id-1", "/link"));
        verify(notificacaoRepository, never()).save(any());
        // Prova o requireMaxLength(...) contra o VARCHAR(255) de "titulo".
    }

    @Test
    void criar_camposComTamanhoExcedido_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "x".repeat(256), "t", "m", "processo", "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "x".repeat(256), "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "x".repeat(256), "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "x".repeat(256)));
        verify(notificacaoRepository, never()).save(any());
        // Prova requireMaxLength(...) para os quatro campos ainda não cobertos (categoria,
        // entidadeTipo, entidadeId, linkUrl) — "titulo" já está coberto pelo teste anterior.
        // Mesma razão do teste de requireNonBlank: uma remoção silenciosa de uma destas
        // chamadas não seria apanhada por um teste que só exercita um campo diferente.
    }

    @Test
    void criar_camposObrigatoriosEmBranco_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, null, "t", "m", "processo", "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "  ", "m", "processo", "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "", "processo", "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", null, "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", " ", "/link"));
        verify(notificacaoRepository, never()).save(any());
        // Prova, campo a campo, que os cinco requireNonBlank(...) em criar() (categoria, titulo,
        // mensagem, entidadeTipo, entidadeId) estão todos realmente ligados — uma futura remoção
        // "silenciosa" de qualquer uma destas chamadas passaria a falhar aqui.
    }

    @Test
    void notificarComFanOutAdmin_umaLinhaPorAdminAtualDoTenant() {
        User admin1 = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        User admin2 = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN"))
                .thenReturn(List.of(admin1, admin2));
        when(userRepository.findById(admin1.getId())).thenReturn(Optional.of(admin1));
        when(userRepository.findById(admin2.getId())).thenReturn(Optional.of(admin2));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarAdmins(TENANT_ID, "DOCUMENTO_NOVO", "t", "m", "documento", "id-2", "/link");

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
        for (Notificacao n : captor.getAllValues()) {
            assertFalse(n.getLida());
        }
        List<UUID> destinatarios = captor.getAllValues().stream()
                .map(Notificacao::getDestinatarioId)
                .toList();
        assertEquals(List.of(admin1.getId(), admin2.getId()), destinatarios);
        // Fan-out: uma linha por ADMIN atual, nunca uma linha partilhada com uma flag
        // "é admin" — prova o Critério de Sucesso 3. A asserção acima é a que efetivamente
        // prova o targeting por-destinatario (não apenas a contagem de chamadas a save()).
    }

    @Test
    void marcarLida_pertenceAoDestinatario_marcaLidaTrueEChamaSaveUmaVez() {
        Notificacao existente = Notificacao.builder()
                .id(ID)
                .tenantId(TENANT_ID)
                .destinatarioId(DESTINATARIO_A)
                .categoria("FASE_ENTRADA")
                .titulo("t")
                .mensagem("m")
                .entidadeTipo("processo")
                .entidadeId("id-1")
                .lida(false)
                .build();
        when(notificacaoRepository.findByIdAndTenantIdAndDestinatarioId(ID, TENANT_ID, DESTINATARIO_A))
                .thenReturn(Optional.of(existente));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        Optional<Notificacao> resultado = service.marcarLida(TENANT_ID, DESTINATARIO_A, ID);

        assertTrue(resultado.isPresent());
        assertTrue(resultado.get().getLida());
        verify(notificacaoRepository, times(1)).save(any());
    }

    @Test
    void marcarLida_naoPertenceAoDestinatarioOuInexistente_retornaVazioENuncaChamaSave() {
        when(notificacaoRepository.findByIdAndTenantIdAndDestinatarioId(ID, TENANT_ID, DESTINATARIO_A))
                .thenReturn(Optional.empty());

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        Optional<Notificacao> resultado = service.marcarLida(TENANT_ID, DESTINATARIO_A, ID);

        assertTrue(resultado.isEmpty());
        verify(notificacaoRepository, never()).save(any());
        // Prova que o serviço — não só o controller — recusa mutar uma linha de outro
        // destinatário ou uma linha inexistente.
    }

    @Test
    void marcarTodasLidas_marcaTodasNaoLidasDoDestinatarioEChamaSaveAllUmaVez() {
        Notificacao n1 = Notificacao.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).destinatarioId(DESTINATARIO_A)
                .categoria("FASE_ENTRADA").titulo("t1").mensagem("m1")
                .entidadeTipo("processo").entidadeId("id-1").lida(false).build();
        Notificacao n2 = Notificacao.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).destinatarioId(DESTINATARIO_A)
                .categoria("DOCUMENTO_NOVO").titulo("t2").mensagem("m2")
                .entidadeTipo("documento").entidadeId("id-2").lida(false).build();
        when(notificacaoRepository.findByTenantIdAndDestinatarioIdAndLidaFalse(TENANT_ID, DESTINATARIO_A))
                .thenReturn(List.of(n1, n2));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        int count = service.marcarTodasLidas(TENANT_ID, DESTINATARIO_A);

        assertEquals(2, count);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notificacao>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoRepository, times(1)).saveAll(captor.capture());
        for (Notificacao n : captor.getValue()) {
            assertTrue(n.getLida());
        }
    }

    // --- Phase 87 Task 1: notificarAdmins (actor exclusion overload) + wrappers do lado processo ---

    @Test
    void notificarAdminsComExclusao_umAdminExcluido_apenasOOutroRecebeLinha() {
        User admin1 = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        User admin2 = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN"))
                .thenReturn(List.of(admin1, admin2));
        when(userRepository.findById(admin2.getId())).thenReturn(Optional.of(admin2));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarAdmins(TENANT_ID, "DOCUMENTO_NOVO", "t", "m", "documento", "id-3", "/link", admin1.getId());

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(1)).save(captor.capture());
        assertEquals(admin2.getId(), captor.getValue().getDestinatarioId());
        // O admin excluído (admin1, == excluirUserId) nunca chega a save().
    }

    @Test
    void notificarFaseEntrada_responsavelNaoNulo_geraLinhaResponsavelELinhaAdmin() {
        UUID processoId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId))
                .thenReturn(Optional.of(User.builder().id(responsavelId).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarFaseEntrada(TENANT_ID, processoId, responsavelId, "PROC-0001", "Instrução",
                "/processos/" + processoId + "?tab=fases");

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
        List<UUID> destinatarios = captor.getAllValues().stream().map(Notificacao::getDestinatarioId).toList();
        assertEquals(List.of(responsavelId, admin.getId()), destinatarios);
        for (Notificacao n : captor.getAllValues()) {
            assertEquals("FASE_ENTRADA", n.getCategoria());
            assertEquals("processo", n.getEntidadeTipo());
        }
    }

    @Test
    void notificarFaseEntrada_responsavelNulo_geraApenasLinhaAdminSemExcecao() {
        UUID processoId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

        assertDoesNotThrow(() ->
                service.notificarFaseEntrada(TENANT_ID, processoId, null, "PROC-0001", "Instrução", "/link"));
        verify(notificacaoRepository, times(1)).save(any());
        // Null-guard: responsavelId nulo nunca chama criar() para o responsável, mas ADMIN é sempre notificado.
    }

    @Test
    void notificarProcessoAtribuido_responsavelNaoNulo_geraLinhaResponsavelComMensagemAtribuidaELinhaAdmin() {
        UUID processoId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId))
                .thenReturn(Optional.of(User.builder().id(responsavelId).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarProcessoAtribuido(TENANT_ID, processoId, responsavelId, "PROC-0002", "/link");

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
        List<Notificacao> saved = captor.getAllValues();
        assertEquals(responsavelId, saved.get(0).getDestinatarioId());
        assertTrue(saved.get(0).getMensagem().startsWith("Foi-lhe atribuído o processo "));
        assertEquals(admin.getId(), saved.get(1).getDestinatarioId());
        assertEquals("PROCESSO_ATRIBUIDO", saved.get(1).getCategoria());
    }

    // --- Phase 87 Task 2: wrappers com exclusão de ator (DOCUMENTO_NOVO, PARECER_ATRIBUIDO) ---

    private static final UUID ATOR = UUID.randomUUID();

    @Test
    void notificarDocumentoNovo_atorNaListaDeDestinatarios_atorNuncaRecebeLinha() {
        UUID userX = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(userX))
                .thenReturn(Optional.of(User.builder().id(userX).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarDocumentoNovo(TENANT_ID, "doc-1", List.of(userX, ATOR), "contrato.pdf", "/link", ATOR);

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
        List<UUID> destinatarios = captor.getAllValues().stream().map(Notificacao::getDestinatarioId).toList();
        assertEquals(List.of(userX, admin.getId()), destinatarios);
        assertFalse(destinatarios.contains(ATOR));
        // O ator (userX duplicado com ele mesmo na lista de destinatários) nunca chega a save().
    }

    @Test
    void notificarDocumentoNovo_destinatariosDuplicados_semAdmin_geraUmaUnicaLinha() {
        UUID userX = UUID.randomUUID();
        when(userRepository.findById(userX))
                .thenReturn(Optional.of(User.builder().id(userX).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of());
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarDocumentoNovo(TENANT_ID, "doc-2", List.of(userX, userX), "contrato.pdf", "/link", ATOR);

        verify(notificacaoRepository, times(1)).save(any());
        // Dedup por Set: destinatário duplicado gera uma só linha.
    }

    @Test
    void notificarDocumentoNovo_adminIgualAoAtor_adminExcluidoDoFanOut() {
        User admin = User.builder().id(ATOR).tenantId(TENANT_ID).build();
        UUID userX = UUID.randomUUID();
        when(userRepository.findById(userX))
                .thenReturn(Optional.of(User.builder().id(userX).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarDocumentoNovo(TENANT_ID, "doc-3", List.of(userX), "contrato.pdf", "/link", ATOR);

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(1)).save(captor.capture());
        assertEquals(userX, captor.getValue().getDestinatarioId());
        // O ADMIN que também é o ator é excluído do fan-out (nunca recebe findById nem save).
    }

    @Test
    void notificarParecerAtribuido_advogadoDiferenteDoAtor_geraLinhaAdvogadoELinhaAdmin() {
        UUID advogadoId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(advogadoId))
                .thenReturn(Optional.of(User.builder().id(advogadoId).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarParecerAtribuido(TENANT_ID, "sol-1", advogadoId, "/link", ATOR);

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
        List<UUID> destinatarios = captor.getAllValues().stream().map(Notificacao::getDestinatarioId).toList();
        assertEquals(List.of(advogadoId, admin.getId()), destinatarios);
        for (Notificacao n : captor.getAllValues()) {
            assertEquals("PARECER_ATRIBUIDO", n.getCategoria());
            assertEquals("parecer_solicitacao", n.getEntidadeTipo());
        }
    }

    @Test
    void notificarParecerAtribuido_advogadoIgualAoAtor_geraApenasLinhaAdmin() {
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        service.notificarParecerAtribuido(TENANT_ID, "sol-2", ATOR, "/link", ATOR);

        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(1)).save(captor.capture());
        assertEquals(admin.getId(), captor.getValue().getDestinatarioId());
        // Auto-atribuição: o próprio advogado (== ator) é excluído do destinatário primário;
        // só o ADMIN (que não é o ator) recebe linha.
    }
}
