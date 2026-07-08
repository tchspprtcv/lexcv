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
}
