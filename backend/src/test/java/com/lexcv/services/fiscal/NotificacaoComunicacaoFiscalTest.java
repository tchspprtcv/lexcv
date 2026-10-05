package com.lexcv.services.fiscal;

import com.lexcv.models.Notificacao;
import com.lexcv.models.User;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 136 (DFE-07, T-136-08..11): a falha persistente de comunicação notifica, uma vez por
 * episódio, cada utilizador ATIVO do mesmo escritório cujas permissões efetivas (as mesmas que o
 * filtro de autenticação compõe) incluem {@code financeiro:manage}; a falha de um destinatário não
 * impede os outros nem chega a quem chama.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacaoComunicacaoFiscalTest {

    private static final String NUMERO = "SIM-FR-2026/7";

    @Mock
    private UserRepository userRepository;
    @Mock
    private ResolucaoPapeisService resolucaoPapeisService;
    @Mock
    private NotificacaoService notificacaoService;

    private NotificacaoComunicacaoFiscal servico;
    private final UUID tenant = UUID.randomUUID();
    private final UUID documento = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        servico = new NotificacaoComunicacaoFiscal(userRepository, resolucaoPapeisService, notificacaoService);
        when(notificacaoService.criar(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.of(new Notificacao()));
    }

    private User utilizador(Boolean ativo, Set<String> permissoes) {
        return utilizador(ativo, Set.of(), permissoes);
    }

    private User utilizador(Boolean ativo, Set<String> papeis, Set<String> permissoes) {
        User u = User.builder().id(UUID.randomUUID()).tenantId(tenant).nome("Utilizador").email(UUID.randomUUID() + "@x.cv")
                .passwordHash("x").build();
        u.setAtivo(ativo);
        when(resolucaoPapeisService.resolverNomesPapeis(u)).thenReturn(papeis);
        when(resolucaoPapeisService.resolverPermissoesEfectivas(u)).thenReturn(permissoes);
        return u;
    }

    @Test
    void notificaSoOsAtivosComFinanceiroManageEfetivo() {
        User a = utilizador(true, Set.of("financeiro:manage"));
        User b = utilizador(true, Set.of("financeiro:view", "financeiro:edit"));
        User c = utilizador(false, Set.of("financeiro:manage"));
        User d = utilizador(null, Set.of("financeiro:manage"));
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of(a, b, c, d));

        int criadas = servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0);

        assertEquals(1, criadas);
        verify(notificacaoService, times(1)).criar(eq(tenant), eq(a.getId()), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(notificacaoService, never()).criar(any(), eq(b.getId()), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(notificacaoService, never()).criar(any(), eq(c.getId()), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
        verify(notificacaoService, never()).criar(any(), eq(d.getId()), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    /** O papel ADMIN recebe financeiro:manage no UserPrincipal (como no filtro de autenticação). */
    @Test
    void adminRecebeComoNoFiltroDeAutenticacao() {
        User admin = utilizador(true, Set.of("ADMIN"), Set.of());
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of(admin));

        assertEquals(1, servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0));
        verify(notificacaoService).criar(eq(tenant), eq(admin.getId()), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void argumentosSaoOsDaUiSpec() {
        User a = utilizador(true, Set.of("financeiro:manage"));
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of(a));

        servico.notificarFalhaPersistente(tenant, documento, NUMERO, 2);

        verify(notificacaoService).criar(tenant, a.getId(), "COMUNICACAO_FISCAL_FALHOU",
                "Falha na comunicação do documento SIM-FR-2026/7",
                "A comunicação do documento SIM-FR-2026/7 falhou após várias tentativas. "
                        + "Abra o documento e use \"Reprocessar comunicação\".",
                "documento_fiscal", documento + ":2", "/financeiro/documentos-fiscais/" + documento);
    }

    @Test
    void episodiosDiferentesTemChavesDeDedupDiferentes() {
        User a = utilizador(true, Set.of("financeiro:manage"));
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of(a));

        servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0);
        servico.notificarFalhaPersistente(tenant, documento, NUMERO, 1);

        ArgumentCaptor<String> entidadeId = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService, times(2)).criar(eq(tenant), eq(a.getId()), anyString(), anyString(), anyString(),
                eq("documento_fiscal"), entidadeId.capture(), anyString());
        assertEquals(List.of(documento + ":0", documento + ":1"), entidadeId.getAllValues());
        assertNotEquals(entidadeId.getAllValues().get(0), entidadeId.getAllValues().get(1));
    }

    @Test
    void mesmoEpisodioDuplicadoNaoContaComoCriada() {
        User a = utilizador(true, Set.of("financeiro:manage"));
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of(a));
        when(notificacaoService.criar(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.of(new Notificacao()), Optional.empty());

        assertEquals(1, servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0));
        assertEquals(0, servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0));
    }

    @Test
    void falhaNumDestinatarioNaoImpedeOsOutrosNemLanca() {
        User a = utilizador(true, Set.of("financeiro:manage"));
        User b = utilizador(true, Set.of("financeiro:manage"));
        User c = utilizador(true, Set.of("financeiro:manage"));
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of(a, b, c));
        when(notificacaoService.criar(any(), eq(a.getId()), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenThrow(new IllegalArgumentException("órfão"));
        when(notificacaoService.criar(any(), eq(b.getId()), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenThrow(new IllegalStateException("base de dados"));

        int[] criadas = new int[1];
        assertDoesNotThrow(() -> criadas[0] = servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0));

        assertEquals(1, criadas[0]);
        verify(notificacaoService).criar(eq(tenant), eq(c.getId()), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void falhaAResolverPermissoesDeUmUtilizadorNaoImpedeOsOutros() {
        User a = utilizador(true, Set.of("financeiro:manage"));
        User b = utilizador(true, Set.of("financeiro:manage"));
        when(resolucaoPapeisService.resolverPermissoesEfectivas(a)).thenThrow(new IllegalStateException("lazy"));
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of(a, b));

        assertEquals(1, servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0));
        verify(notificacaoService).criar(eq(tenant), eq(b.getId()), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void falhaALerUtilizadoresDevolveZeroSemLancar() {
        when(userRepository.findByTenantId(tenant)).thenThrow(new IllegalStateException("base de dados"));

        int[] criadas = {-1};
        assertDoesNotThrow(() -> criadas[0] = servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0));
        assertEquals(0, criadas[0]);
    }

    @Test
    void procuraUtilizadoresSoNoTenantDado() {
        when(userRepository.findByTenantId(tenant)).thenReturn(List.of());

        assertEquals(0, servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0));

        verify(userRepository).findByTenantId(tenant);
        verifyNoMoreInteractions(userRepository);
        verify(notificacaoService, never()).criar(any(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }
}
