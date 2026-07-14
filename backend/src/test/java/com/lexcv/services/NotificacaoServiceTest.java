package com.lexcv.services;

import com.lexcv.models.ClienteAdministrativo;
import com.lexcv.models.ClienteAdvogado;
import com.lexcv.models.Notificacao;
import com.lexcv.models.NotificacaoPreferencia;
import com.lexcv.models.User;
import com.lexcv.repositories.ClienteAdministrativoRepository;
import com.lexcv.repositories.ClienteAdvogadoRepository;
import com.lexcv.repositories.NotificacaoPreferenciaRepository;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 *
 * CR-01 (Phase 94 code review): criar()'s persistence mechanism changed from
 * notificacaoRepository.save(...) to the atomic notificacaoRepository.inserirSeNaoDuplicado(...)
 * upsert (ON CONFLICT DO NOTHING) -- every test below that exercises criar() (directly or via a
 * notificar* wrapper) stubs/verifies inserirSeNaoDuplicado(...) instead of save(...). Tests that
 * exercise marcarLida()/marcarTodasLidas() are unaffected (those still use save()/saveAll()) and
 * are unchanged.
 *
 * WR-02 (Phase 94 code review): the ADMIN fan-out query was renamed from
 * findByTenantIdAndRoleName to findByTenantIdAndRoleNameAndAtivoTrue so deactivated admins are
 * excluded -- every stub of the ADMIN fan-out below uses the new method name.
 */
@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificacaoPreferenciaRepository notificacaoPreferenciaRepository;

    @Mock
    private ClienteAdvogadoRepository clienteAdvogadoRepository;

    @Mock
    private ClienteAdministrativoRepository clienteAdministrativoRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID DESTINATARIO_A = UUID.randomUUID();
    private static final UUID DESTINATARIO_B = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @Test
    void criar_doisDestinatariosDistintos_geramLinhasIndependentesComEstadoLidaProprio() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        when(userRepository.findById(DESTINATARIO_B))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_B).tenantId(TENANT_ID).build()));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link");
        service.criar(TENANT_ID, DESTINATARIO_B, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link");

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(2)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        List<UUID> destinatarios = destinatarioCaptor.getAllValues();
        assertEquals(DESTINATARIO_A, destinatarios.get(0));
        assertEquals(DESTINATARIO_B, destinatarios.get(1));
        // Linhas independentes, nunca uma linha partilhada — prova o Critério de Sucesso 2
        // no lado da escrita.
    }

    @Test
    void criar_destinatarioDeOutroTenant_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(UUID.randomUUID()).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link"));
        verify(notificacaoRepository, never()).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // Prova o exato cenário IDOR-adjacent que o filtro .filter(u -> tenantId.equals(...))
        // existe para bloquear — fecha o gap apontado pelo WR-01 da iteração 2.
    }

    @Test
    void criar_tituloExcede255Caracteres_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "x".repeat(256), "m", "processo", "id-1", "/link"));
        verify(notificacaoRepository, never()).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // Prova o requireMaxLength(...) contra o VARCHAR(255) de "titulo".
    }

    @Test
    void criar_camposComTamanhoExcedido_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "x".repeat(256), "t", "m", "processo", "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "x".repeat(256), "id-1", "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "x".repeat(256), "/link"));
        assertThrows(IllegalArgumentException.class, () ->
                service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "x".repeat(256)));
        verify(notificacaoRepository, never()).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // Prova requireMaxLength(...) para os quatro campos ainda não cobertos (categoria,
        // entidadeTipo, entidadeId, linkUrl) — "titulo" já está coberto pelo teste anterior.
        // Mesma razão do teste de requireNonBlank: uma remoção silenciosa de uma destas
        // chamadas não seria apanhada por um teste que só exercita um campo diferente.
    }

    @Test
    void criar_camposObrigatoriosEmBranco_lancaIllegalArgumentException() {
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

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
        verify(notificacaoRepository, never()).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // Prova, campo a campo, que os cinco requireNonBlank(...) em criar() (categoria, titulo,
        // mensagem, entidadeTipo, entidadeId) estão todos realmente ligados — uma futura remoção
        // "silenciosa" de qualquer uma destas chamadas passaria a falhar aqui.
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

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        Optional<Notificacao> resultado = service.marcarLida(TENANT_ID, DESTINATARIO_A, ID);

        assertTrue(resultado.isPresent());
        assertTrue(resultado.get().getLida());
        verify(notificacaoRepository, times(1)).save(any());
    }

    @Test
    void marcarLida_naoPertenceAoDestinatarioOuInexistente_retornaVazioENuncaChamaSave() {
        when(notificacaoRepository.findByIdAndTenantIdAndDestinatarioId(ID, TENANT_ID, DESTINATARIO_A))
                .thenReturn(Optional.empty());

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
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
        // NOTF-26: assinatura ganhou o parâmetro `agora` (predicado de visibilidade de
        // snooze) -- any() porque este teste não exercita snooze, só o find-mutate-saveAll.
        when(notificacaoRepository.findByTenantIdAndDestinatarioIdAndLidaFalse(eq(TENANT_ID), eq(DESTINATARIO_A), any()))
                .thenReturn(List.of(n1, n2));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        int count = service.marcarTodasLidas(TENANT_ID, DESTINATARIO_A);

        assertEquals(2, count);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notificacao>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificacaoRepository, times(1)).saveAll(captor.capture());
        for (Notificacao n : captor.getValue()) {
            assertTrue(n.getLida());
        }
    }

    // --- Plan 95-01 Task 1: resolverEquipaCliente (NOTF-25) ---

    @Test
    void resolverEquipaCliente_uniaoAdvogadosEAdministrativos_dedupTenantScoped() {
        UUID clienteId = UUID.randomUUID();
        UUID advogadoSo = UUID.randomUUID();
        UUID compartilhado = UUID.randomUUID();
        UUID administrativoSo = UUID.randomUUID();
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of(
                ClienteAdvogado.builder().clienteId(clienteId).tenantId(TENANT_ID).userId(advogadoSo).build(),
                ClienteAdvogado.builder().clienteId(clienteId).tenantId(TENANT_ID).userId(compartilhado).build()));
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of(
                ClienteAdministrativo.builder().clienteId(clienteId).tenantId(TENANT_ID).userId(compartilhado).build(),
                ClienteAdministrativo.builder().clienteId(clienteId).tenantId(TENANT_ID).userId(administrativoSo).build()));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        Set<UUID> equipa = service.resolverEquipaCliente(TENANT_ID, clienteId);

        assertEquals(Set.of(advogadoSo, compartilhado, administrativoSo), equipa);
        assertEquals(3, equipa.size());
        verify(clienteAdvogadoRepository, times(1)).findByClienteIdAndTenantId(clienteId, TENANT_ID);
        verify(clienteAdministrativoRepository, times(1)).findByClienteIdAndTenantId(clienteId, TENANT_ID);
        // Prova a assinatura (clienteId, TENANT_ID) -- nunca clienteId sozinho (Pitfall 10) -- e
        // o dedup: "compartilhado" aparece nos dois repositórios mas só uma vez no conjunto
        // resultante.
    }

    @Test
    void resolverEquipaCliente_clienteIdNulo_devolveVazio() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        Set<UUID> equipa = service.resolverEquipaCliente(TENANT_ID, null);

        assertTrue(equipa.isEmpty());
        verify(clienteAdvogadoRepository, never()).findByClienteIdAndTenantId(any(), any());
        verify(clienteAdministrativoRepository, never()).findByClienteIdAndTenantId(any(), any());
        // Nenhuma chamada a repositório quando clienteId é null -- processo ainda sem cliente
        // definido não tem equipa a resolver.
    }

    // --- Phase 87 Task 1: wrappers do lado processo ---

    @Test
    void notificarFaseEntrada_responsavelNaoNulo_geraLinhaResponsavelELinhaAdmin() {
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId))
                .thenReturn(Optional.of(User.builder().id(responsavelId).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarFaseEntrada(TENANT_ID, processoId, clienteId, responsavelId, "PROC-0001", "Instrução",
                "/processos/" + processoId + "?tab=fases");

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> categoriaCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entidadeTipoCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificacaoRepository, times(2)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                categoriaCaptor.capture(), entidadeTipoCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals(List.of(responsavelId, admin.getId()), destinatarioCaptor.getAllValues());
        for (String categoria : categoriaCaptor.getAllValues()) {
            assertEquals("FASE_ENTRADA", categoria);
        }
        for (String entidadeTipo : entidadeTipoCaptor.getAllValues()) {
            assertEquals("processo", entidadeTipo);
        }
    }

    @Test
    void notificarFaseEntrada_responsavelNulo_geraApenasLinhaAdminSemExcecao() {
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarFaseEntrada(TENANT_ID, processoId, clienteId, null, "PROC-0001", "Instrução", "/link"));
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // Null-guard: responsavelId nulo nunca chama criar() para o responsável, mas ADMIN é sempre notificado.
    }

    @Test
    void notificarProcessoAtribuido_responsavelNaoNulo_geraLinhaResponsavelComMensagemAtribuidaELinhaAdmin() {
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId))
                .thenReturn(Optional.of(User.builder().id(responsavelId).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarProcessoAtribuido(TENANT_ID, processoId, clienteId, responsavelId, "PROC-0002", "/link");

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> mensagemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> categoriaCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificacaoRepository, times(2)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                categoriaCaptor.capture(), any(), any(), any(), mensagemCaptor.capture(), any(), any());
        List<UUID> destinatarios = destinatarioCaptor.getAllValues();
        List<String> mensagens = mensagemCaptor.getAllValues();
        List<String> categorias = categoriaCaptor.getAllValues();
        assertEquals(responsavelId, destinatarios.get(0));
        assertTrue(mensagens.get(0).startsWith("Foi-lhe atribuído o processo "));
        assertEquals(admin.getId(), destinatarios.get(1));
        assertEquals("PROCESSO_ATRIBUIDO", categorias.get(1));
    }

    @Test
    void notificarProcessoAtribuido_responsavelInvalido_naoLancaExcecaoEAdminAindaRecebeLinha() {
        // CR-02 (Phase 87 code review, iteration 3): a stale/orphaned responsavelId (e.g.
        // deleted/deactivated between the controller's own validation and this call) must not
        // let criar()'s IllegalArgumentException escape and roll back the enclosing
        // @Transactional controller method -- mirrors notificarFaseEntrada_responsavelNulo_...
        // but for an *invalid* (non-null) recipient rather than a null one.
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId)).thenReturn(Optional.empty());
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarProcessoAtribuido(TENANT_ID, processoId, clienteId, responsavelId, "PROC-0003", "/link"));

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(admin.getId(), destinatarioCaptor.getValue());
        // O responsavelId órfão nunca chega a inserirSeNaoDuplicado, mas o fan-out ADMIN ainda ocorre.
    }

    // --- Plan 95-01 Task 2: equipa do cliente (NOTF-25) ---

    @Test
    void notificarFaseEntrada_equipaDoCliente_todaEquipaMaisResponsavelMaisAdmin() {
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        UUID advogadoEquipa = UUID.randomUUID();
        UUID administrativoEquipa = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId))
                .thenReturn(Optional.of(User.builder().id(responsavelId).tenantId(TENANT_ID).build()));
        when(userRepository.findById(advogadoEquipa))
                .thenReturn(Optional.of(User.builder().id(advogadoEquipa).tenantId(TENANT_ID).build()));
        when(userRepository.findById(administrativoEquipa))
                .thenReturn(Optional.of(User.builder().id(administrativoEquipa).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of(
                ClienteAdvogado.builder().clienteId(clienteId).tenantId(TENANT_ID).userId(advogadoEquipa).build()));
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of(
                ClienteAdministrativo.builder().clienteId(clienteId).tenantId(TENANT_ID).userId(administrativoEquipa).build()));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarFaseEntrada(TENANT_ID, processoId, clienteId, responsavelId, "PROC-0006", "Instrução", "/link");

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(4)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(Set.of(advogadoEquipa, administrativoEquipa, responsavelId, admin.getId()),
                Set.copyOf(destinatarioCaptor.getAllValues()));
        assertEquals(4, destinatarioCaptor.getAllValues().size());
        // Toda a equipa do cliente (advogado + administrativo) + responsável + admin recebem
        // exatamente uma linha cada, todas com a mesma mensagem -- prova que FASE_ENTRADA já não
        // notifica apenas o responsável (Critério de Sucesso 1, NOTF-25).
    }

    @Test
    void notificarProcessoAtribuido_equipa_responsavel2aPessoaEquipa3aPessoa() {
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        UUID membroEquipa = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId))
                .thenReturn(Optional.of(User.builder().id(responsavelId).tenantId(TENANT_ID).build()));
        when(userRepository.findById(membroEquipa))
                .thenReturn(Optional.of(User.builder().id(membroEquipa).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of(
                ClienteAdvogado.builder().clienteId(clienteId).tenantId(TENANT_ID).userId(membroEquipa).build()));
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarProcessoAtribuido(TENANT_ID, processoId, clienteId, responsavelId, "PROC-0007", "/link");

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> mensagemCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificacaoRepository, times(3)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), mensagemCaptor.capture(), any(), any());
        List<UUID> destinatarios = destinatarioCaptor.getAllValues();
        List<String> mensagens = mensagemCaptor.getAllValues();
        int indiceResponsavel = destinatarios.indexOf(responsavelId);
        int indiceMembroEquipa = destinatarios.indexOf(membroEquipa);
        int indiceAdmin = destinatarios.indexOf(admin.getId());
        assertTrue(indiceResponsavel >= 0 && indiceMembroEquipa >= 0 && indiceAdmin >= 0);
        assertTrue(mensagens.get(indiceResponsavel).startsWith("Foi-lhe atribuído"));
        assertTrue(mensagens.get(indiceMembroEquipa).startsWith("O processo"));
        assertTrue(mensagens.get(indiceAdmin).startsWith("O processo"));
        // O responsável recebe a mensagem em 2ª pessoa; o resto da equipa e o ADMIN recebem a
        // mesma mensagem informativa em 3ª pessoa -- prova o split 2ª/3ª pessoa (Critério de
        // Sucesso 2, NOTF-25).
    }

    // --- Phase 87 Task 2: wrappers com exclusão de ator (DOCUMENTO_NOVO, PARECER_ATRIBUIDO) ---

    private static final UUID ATOR = UUID.randomUUID();

    @Test
    void notificarDocumentoNovo_atorNaListaDeDestinatarios_atorNuncaRecebeLinha() {
        UUID userX = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(userX))
                .thenReturn(Optional.of(User.builder().id(userX).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarDocumentoNovo(TENANT_ID, "doc-1", List.of(userX, ATOR), "contrato.pdf", "/link", ATOR);

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(2)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        List<UUID> destinatarios = destinatarioCaptor.getAllValues();
        assertEquals(List.of(userX, admin.getId()), destinatarios);
        assertFalse(destinatarios.contains(ATOR));
        // O ator (userX duplicado com ele mesmo na lista de destinatários) nunca chega a inserirSeNaoDuplicado.
    }

    @Test
    void notificarDocumentoNovo_destinatariosDuplicados_semAdmin_geraUmaUnicaLinha() {
        UUID userX = UUID.randomUUID();
        when(userRepository.findById(userX))
                .thenReturn(Optional.of(User.builder().id(userX).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarDocumentoNovo(TENANT_ID, "doc-2", List.of(userX, userX), "contrato.pdf", "/link", ATOR);

        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // Dedup por Set: destinatário duplicado gera uma só linha.
    }

    @Test
    void notificarDocumentoNovo_adminIgualAoAtor_adminExcluidoDoFanOut() {
        User admin = User.builder().id(ATOR).tenantId(TENANT_ID).build();
        UUID userX = UUID.randomUUID();
        when(userRepository.findById(userX))
                .thenReturn(Optional.of(User.builder().id(userX).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarDocumentoNovo(TENANT_ID, "doc-3", List.of(userX), "contrato.pdf", "/link", ATOR);

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(userX, destinatarioCaptor.getValue());
        // O ADMIN que também é o ator é excluído do fan-out (nunca recebe findById nem inserirSeNaoDuplicado).
    }

    @Test
    void notificarParecerAtribuido_advogadoDiferenteDoAtor_geraLinhaAdvogadoELinhaAdmin() {
        UUID advogadoId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(advogadoId))
                .thenReturn(Optional.of(User.builder().id(advogadoId).tenantId(TENANT_ID).build()));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarParecerAtribuido(TENANT_ID, "sol-1", advogadoId, "/link", ATOR);

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> categoriaCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entidadeTipoCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificacaoRepository, times(2)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                categoriaCaptor.capture(), entidadeTipoCaptor.capture(), any(), any(), any(), any(), any());
        List<UUID> destinatarios = destinatarioCaptor.getAllValues();
        assertEquals(List.of(advogadoId, admin.getId()), destinatarios);
        for (String categoria : categoriaCaptor.getAllValues()) {
            assertEquals("PARECER_ATRIBUIDO", categoria);
        }
        for (String entidadeTipo : entidadeTipoCaptor.getAllValues()) {
            assertEquals("parecer_solicitacao", entidadeTipo);
        }
    }

    @Test
    void notificarParecerAtribuido_advogadoInvalido_naoLancaExcecaoEAdminAindaRecebeLinha() {
        // CR-02 (Phase 87 code review, iteration 3): mirrors
        // notificarProcessoAtribuido_responsavelInvalido_... above -- a stale/orphaned
        // advogadoId must not let criar()'s IllegalArgumentException escape and roll back
        // the enclosing @Transactional controller method (createSolicitacao/atribuirAdvogado).
        UUID advogadoId = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(advogadoId)).thenReturn(Optional.empty());
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarParecerAtribuido(TENANT_ID, "sol-3", advogadoId, "/link", ATOR));

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(admin.getId(), destinatarioCaptor.getValue());
        // O advogadoId órfão nunca chega a inserirSeNaoDuplicado, mas o fan-out ADMIN ainda ocorre.
    }

    @Test
    void notificarParecerAtribuido_advogadoIgualAoAtor_geraApenasLinhaAdmin() {
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.notificarParecerAtribuido(TENANT_ID, "sol-2", ATOR, "/link", ATOR);

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(admin.getId(), destinatarioCaptor.getValue());
        // Auto-atribuição: o próprio advogado (== ator) é excluído do destinatário primário;
        // só o ADMIN (que não é o ator) recebe linha.
    }

    // --- Plan 93-02: mute guard em criar() + métodos de preferência (NOTF-24) ---

    @Test
    void criar_categoriaSilenciada_naoPersisteEDevolveOptionalVazio() {
        // Este é EXATAMENTE o path que AlertasDiariosJob.notificar() percorre: uma chamada
        // direta a criar(...), sem passar por nenhum dos 5 métodos notificar*. Provar que o
        // guard aqui bloqueia a persistência prova, por construção, o Critério de Sucesso 3
        // (o job diário respeita o silenciamento sem qualquer alteração própria).
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        when(notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(
                TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA")).thenReturn(true);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        Optional<Notificacao> resultado = service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m",
                "processo", "id-1", "/link");

        assertTrue(resultado.isEmpty());
        verify(notificacaoRepository, never()).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void criar_categoriaSemPreferencia_persisteEDevolveOptionalPresente() {
        // Comportamento default-on preservado: ausência de linha de preferência == entregar.
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        when(notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(
                TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA")).thenReturn(false);
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        Optional<Notificacao> resultado = service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m",
                "processo", "id-1", "/link");

        assertTrue(resultado.isPresent());
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void criar_prazoVencidoComPreferenciaDeSilenciamento_aindaPersiste() {
        // Critério de Sucesso 2: PRAZO_VENCIDO é sempre entregue. isSilenciavelCategoria(...)
        // retorna false para PRAZO_VENCIDO, logo o guard nunca sequer consulta a preferência --
        // não é necessário stubar existsBy... para provar isto (short-circuit).
        when(userRepository.findById(DESTINATARIO_A))
                .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        Optional<Notificacao> resultado = service.criar(TENANT_ID, DESTINATARIO_A, "PRAZO_VENCIDO", "t", "m",
                "processo", "id-1", "/link");

        assertTrue(resultado.isPresent());
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificacaoPreferenciaRepository, never()).existsByTenantIdAndUserIdAndCategoria(any(), any(), any());
    }

    @Test
    void silenciarCategoria_prazoVencido_lancaIllegalArgumentException() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.silenciarCategoria(TENANT_ID, DESTINATARIO_A, "PRAZO_VENCIDO"));
        // CR-01 (iteração 3): upsertSilenciar (não mais save/saveAndFlush) é o método de
        // persistência que a validação deve impedir de ser chamado.
        verify(notificacaoPreferenciaRepository, never()).upsertSilenciar(any(), any(), any());
    }

    @Test
    void silenciarCategoria_categoriaDesconhecida_lancaIllegalArgumentException() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertThrows(IllegalArgumentException.class, () ->
                service.silenciarCategoria(TENANT_ID, DESTINATARIO_A, "categoria_inexistente"));
        verify(notificacaoPreferenciaRepository, never()).upsertSilenciar(any(), any(), any());
    }

    @Test
    void silenciarCategoria_categoriaValida_delegaIdempotenciaAoUpsertAtomicoSemPreCheckExistsBy() {
        // CR-01 (Phase 93 code review, iteration 3): existsBy...+saveAndFlush(...) dentro de
        // um try/catch(DataIntegrityViolationException) foi substituído por um único upsert
        // nativo atómico (INSERT ... ON CONFLICT DO NOTHING) -- ver
        // NotificacaoPreferenciaRepository.upsertSilenciar. Esse padrão anterior não
        // sobrevive a uma transação real no PostgreSQL: apanhar a exceção traduzida
        // localmente não impede o commit implícito subsequente de falhar (ou de ser tratado
        // como rollback silencioso) contra uma transação já abortada pelo servidor. Com o
        // upsert atómico, silenciarCategoria() já não faz nenhum pré-check existsBy... --
        // a query nativa É o próprio check de idempotência, tanto para a primeira chamada
        // como para chamadas repetidas -- pelo que um repositório mockado já não consegue
        // (nem deve tentar) provar "não persiste segunda linha": essa garantia agora vive
        // inteiramente na query nativa e está coberta contra PostgreSQL real em
        // NotificacaoPreferenciaRepositoryIT#upsertSilenciar_duasTransacoesConcorrentes_....
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.silenciarCategoria(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA");

        verify(notificacaoPreferenciaRepository, times(1))
                .upsertSilenciar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA");
        verify(notificacaoPreferenciaRepository, never())
                .existsByTenantIdAndUserIdAndCategoria(any(), any(), any());
    }

    @Test
    void reativarCategoria_chamaDeleteDerivado() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        service.reativarCategoria(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA");

        verify(notificacaoPreferenciaRepository, times(1))
                .deleteByTenantIdAndUserIdAndCategoria(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA");
    }

    @Test
    void listarCategoriasSilenciadas_devolveCategoriasDoUser() {
        NotificacaoPreferencia pref = NotificacaoPreferencia.builder()
                .tenantId(TENANT_ID).userId(DESTINATARIO_A).categoria("DOCUMENTO_NOVO").build();
        when(notificacaoPreferenciaRepository.findByTenantIdAndUserId(TENANT_ID, DESTINATARIO_A))
                .thenReturn(List.of(pref));

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);
        List<String> categorias = service.listarCategoriasSilenciadas(TENANT_ID, DESTINATARIO_A);

        assertTrue(categorias.contains("DOCUMENTO_NOVO"));
    }

    // --- Phase 94 (NOTF-27): colisão dedup quando o destinatário primário também é ADMIN ---

    @Test
    void notificarFaseEntrada_responsavelTambemAdmin_umaUnicaLinhaSemExcecao() {
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        User admin = User.builder().id(responsavelId).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId)).thenReturn(Optional.of(admin));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarFaseEntrada(TENANT_ID, processoId, clienteId, responsavelId, "PROC-0004", "Instrução", "/link"));

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(responsavelId, destinatarioCaptor.getValue());
        // O responsável também é ADMIN do mesmo tenant: apenas UMA linha é persistida (dedup),
        // nunca uma colisão de uk_notificacao_dedup — prova o Critério de Sucesso 1 (NOTF-27).
    }

    @Test
    void notificarProcessoAtribuido_responsavelTambemAdmin_umaUnicaLinha2aPessoaSemExcecao() {
        UUID processoId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        UUID responsavelId = UUID.randomUUID();
        User admin = User.builder().id(responsavelId).tenantId(TENANT_ID).build();
        when(userRepository.findById(responsavelId)).thenReturn(Optional.of(admin));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, TENANT_ID)).thenReturn(List.of());
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarProcessoAtribuido(TENANT_ID, processoId, clienteId, responsavelId, "PROC-0005", "/link"));

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> mensagemCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), mensagemCaptor.capture(), any(), any());
        assertEquals(responsavelId, destinatarioCaptor.getValue());
        assertTrue(mensagemCaptor.getValue().startsWith("Foi-lhe atribuído o processo "));
        // A 2ª pessoa vence sobre a 3ª quando o primário também é admin — apenas uma linha.
    }

    @Test
    void notificarDocumentoNovo_destinatarioTambemAdmin_umaUnicaLinhaSemExcecao() {
        UUID userX = UUID.randomUUID();
        User admin = User.builder().id(userX).tenantId(TENANT_ID).build();
        when(userRepository.findById(userX)).thenReturn(Optional.of(admin));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarDocumentoNovo(TENANT_ID, "doc-4", List.of(userX), "contrato.pdf", "/link", ATOR));

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(userX, destinatarioCaptor.getValue());
        // O destinatário também é ADMIN do mesmo tenant: apenas uma linha, sem colisão.
    }

    @Test
    void notificarParecerAtribuido_advogadoTambemAdmin_umaUnicaLinha2aPessoaSemExcecao() {
        UUID advogadoId = UUID.randomUUID();
        User admin = User.builder().id(advogadoId).tenantId(TENANT_ID).build();
        when(userRepository.findById(advogadoId)).thenReturn(Optional.of(admin));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarParecerAtribuido(TENANT_ID, "sol-4", advogadoId, "/link", ATOR));

        ArgumentCaptor<UUID> destinatarioCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> mensagemCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificacaoRepository, times(1)).inserirSeNaoDuplicado(any(), any(), destinatarioCaptor.capture(),
                any(), any(), any(), any(), mensagemCaptor.capture(), any(), any());
        assertEquals(advogadoId, destinatarioCaptor.getValue());
        assertTrue(mensagemCaptor.getValue().startsWith("Foi-lhe atribuído um parecer"));
        // O advogado também é ADMIN do mesmo tenant: apenas uma linha, mensagem em 2ª pessoa.
    }

    @Test
    void notificarDocumentoNovo_inserirSeNaoDuplicadoRetorna0_naoPropagaEContinuaFanOut() {
        // CR-01 (Phase 94 code review): substitui o antigo teste
        // notificarDocumentoNovo_saveLancaDataIntegrityViolation_naoPropagaEContinuaFanOut, que
        // simulava a colisão de uk_notificacao_dedup como uma DataIntegrityViolationException
        // lançada por save(). Essa simulação já não corresponde à implementação real: criar()
        // agora usa um upsert nativo ON CONFLICT DO NOTHING (inserirSeNaoDuplicado), que nunca
        // lança exceção no caminho de duplicado -- reporta o "não inseriu" através do valor de
        // retorno (0 linhas afetadas), não de uma exceção. Este teste propaga uma linha
        // de "duplicado" e outra de "inserido com sucesso" sem que a primeira interrompa o
        // fan-out para a segunda.
        UUID userX = UUID.randomUUID();
        User admin = User.builder().id(UUID.randomUUID()).tenantId(TENANT_ID).build();
        when(userRepository.findById(userX))
                .thenReturn(Optional.of(User.builder().id(userX).tenantId(TENANT_ID).build()));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findByTenantIdAndRoleNameAndAtivoTrue(TENANT_ID, "ADMIN")).thenReturn(List.of(admin));
        when(notificacaoRepository.inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0)
                .thenReturn(1);

        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository, notificacaoPreferenciaRepository,
                clienteAdvogadoRepository, clienteAdministrativoRepository);

        assertDoesNotThrow(() ->
                service.notificarDocumentoNovo(TENANT_ID, "doc-5", List.of(userX), "contrato.pdf", "/link", ATOR));

        verify(notificacaoRepository, times(2)).inserirSeNaoDuplicado(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // A primeira chamada colide com uk_notificacao_dedup (simulado via retorno 0, "duplicado")
        // e criar() devolve Optional.empty() sem lançar nada; a segunda (fan-out ADMIN) ainda
        // persiste (retorno 1) — nada propaga para fora do método.
    }
}
