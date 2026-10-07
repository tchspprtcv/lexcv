package com.lexcv.services.fiscal;

import com.lexcv.models.Notificacao;
import com.lexcv.services.NotificacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 137 (ENTR-05, T-137-08..11): a falha persistente do envio por email notifica, uma vez por
 * episódio, exatamente os destinatários de {@code COMUNICACAO_FISCAL_FALHOU}, com texto fixo que só
 * contém o número do documento e a contagem real de tentativas do episódio.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacaoEntregaEmailFiscalTest {

    private static final String NUMERO = "SIM-FR-2026/7";

    @Mock
    private NotificacaoComunicacaoFiscal destinatarios;
    @Mock
    private NotificacaoService notificacaoService;

    private NotificacaoEntregaEmailFiscal servico;
    private final UUID tenant = UUID.randomUUID();
    private final UUID documento = UUID.randomUUID();
    private final UUID u1 = UUID.randomUUID();
    private final UUID u2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        servico = new NotificacaoEntregaEmailFiscal(destinatarios, notificacaoService);
        when(destinatarios.destinatariosFalhaFiscal(tenant)).thenReturn(List.of(u1, u2));
        when(notificacaoService.criar(any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString())).thenReturn(Optional.of(new Notificacao()));
    }

    @Test
    void notificaCadaDestinatarioComOsArgumentosDaUiSpec() {
        assertEquals(2, servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0, 5));
        for (UUID u : List.of(u1, u2)) {
            verify(notificacaoService).criar(tenant, u, "EMAIL_FISCAL_FALHOU",
                    "Falha no envio por email do documento " + NUMERO,
                    "O envio do email do documento " + NUMERO + " ao cliente falhou após 5 tentativas. "
                            + "Abra o documento para ver a última falha; quem tem permissão pode reenviar o email.",
                    "documento_fiscal", documento + ":0", "/financeiro/documentos-fiscais/" + documento);
        }
    }

    @Test
    void falhaPermanenteNaPrimeiraTentativaUsaSingular() {
        servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0, 1);
        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService, times(2)).criar(eq(tenant), any(), anyString(), anyString(), mensagem.capture(),
                anyString(), anyString(), anyString());
        for (String m : mensagem.getAllValues()) {
            assertTrue(m.contains("ao cliente falhou após 1 tentativa. Abra o documento"), m);
            assertFalse(m.contains("5 tentativas"), m);
            assertFalse(m.contains("(s)"), m);
        }
    }

    @Test
    void textoTentativasEContagemReal() {
        assertEquals("1 tentativa", NotificacaoEntregaEmailFiscal.textoTentativas(1));
        assertEquals("2 tentativas", NotificacaoEntregaEmailFiscal.textoTentativas(2));
        assertEquals("5 tentativas", NotificacaoEntregaEmailFiscal.textoTentativas(5));
    }

    @Test
    void mesmoEpisodioJaNotificadoNaoConta() {
        when(notificacaoService.criar(eq(tenant), eq(u1), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenReturn(Optional.empty());
        assertEquals(1, servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0, 5));
    }

    @Test
    void episodiosDiferentesTemChavesDeDedupDiferentes() {
        servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0, 5);
        servico.notificarFalhaPersistente(tenant, documento, NUMERO, 1, 2);
        ArgumentCaptor<String> entidadeId = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService, times(2)).criar(eq(tenant), eq(u1), anyString(), anyString(), anyString(),
                anyString(), entidadeId.capture(), anyString());
        assertEquals(List.of(documento + ":0", documento + ":1"), entidadeId.getAllValues());
    }

    @Test
    void falhaNumDestinatarioNaoImpedeOsOutrosNemLanca() {
        when(notificacaoService.criar(eq(tenant), eq(u1), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString())).thenThrow(new IllegalStateException("base de dados"));
        int[] criadas = {-1};
        assertDoesNotThrow(() -> criadas[0] = servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0, 5));
        assertEquals(1, criadas[0]);
        verify(notificacaoService).criar(eq(tenant), eq(u2), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void falhaAResolverDestinatariosDevolveZeroSemLancar() {
        when(destinatarios.destinatariosFalhaFiscal(tenant)).thenThrow(new IllegalStateException("x"));
        int[] criadas = {-1};
        assertDoesNotThrow(() -> criadas[0] = servico.notificarFalhaPersistente(tenant, documento, NUMERO, 0, 5));
        assertEquals(0, criadas[0]);
        verify(notificacaoService, never()).criar(any(), any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void numeroNuloUsaTextoNeutroSemArroba() {
        servico.notificarFalhaPersistente(tenant, documento, null, 0, 3);
        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService, times(2)).criar(eq(tenant), any(), anyString(), titulo.capture(), mensagem.capture(),
                anyString(), anyString(), anyString());
        assertEquals("Falha no envio por email do documento", titulo.getValue());
        assertEquals("O envio do email do documento ao cliente falhou após 3 tentativas. "
                + "Abra o documento para ver a última falha; quem tem permissão pode reenviar o email.",
                mensagem.getValue());
        for (String t : List.of(titulo.getValue(), mensagem.getValue())) {
            assertFalse(t.contains("null"), t);
            assertFalse(t.contains("@"), t);
        }
    }

    @Test
    void textoNuncaContemArroba() {
        servico.notificarFalhaPersistente(tenant, documento, NUMERO, 2, 5);
        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService, times(2)).criar(eq(tenant), any(), anyString(), titulo.capture(), mensagem.capture(),
                anyString(), anyString(), anyString());
        for (String t : titulo.getAllValues()) {
            assertFalse(t.contains("@"), t);
        }
        for (String t : mensagem.getAllValues()) {
            assertFalse(t.contains("@"), t);
        }
    }

    @Test
    void construtorSoRecebeOsDestinatariosEONotificacaoService() {
        Constructor<?>[] construtores = NotificacaoEntregaEmailFiscal.class.getConstructors();
        assertEquals(1, construtores.length);
        assertEquals(List.of(NotificacaoComunicacaoFiscal.class, NotificacaoService.class),
                Arrays.asList(construtores[0].getParameterTypes()));
        assertEquals("EMAIL_FISCAL_FALHOU", NotificacaoEntregaEmailFiscal.CATEGORIA);
    }
}
