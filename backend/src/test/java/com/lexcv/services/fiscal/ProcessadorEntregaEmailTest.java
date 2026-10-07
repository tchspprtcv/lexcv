package com.lexcv.services.fiscal;

import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.fiscal.email.EntregaEmailGateway;
import com.lexcv.fiscal.email.MensagemEmailFiscal;
import com.lexcv.fiscal.email.ResultadoEnvioEmail;
import com.lexcv.fiscal.pdf.FalhaGeracaoPdf;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 137-14 (ENTR-03, ENTR-04, ENTR-05): processamento de UMA entrega reclamada, com todos os
 * colaboradores simulados.
 */
class ProcessadorEntregaEmailTest {

    private static final Instant AGORA = Instant.parse("2026-06-15T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final String NUMERO = "FR 2026A/000123";
    private static final String DESTINATARIO = "cliente@exemplo.cv";
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    private final EntregaEmailTransacoes transacoes = mock(EntregaEmailTransacoes.class);
    private final PdfDocumentoFiscalService pdfService = mock(PdfDocumentoFiscalService.class);
    private final ComposicaoEmailFiscal composicao = mock(ComposicaoEmailFiscal.class);
    private final EntregaEmailGateway gateway = mock(EntregaEmailGateway.class);
    private final NotificacaoEntregaEmailFiscal notificacao = mock(NotificacaoEntregaEmailFiscal.class);
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final MensagemEmailFiscal mensagem = new MensagemEmailFiscal(DESTINATARIO, Optional.empty(), "Assunto",
            "texto", "<p>texto</p>", List.of(new MensagemEmailFiscal.Anexo("a.pdf", "application/pdf", PDF),
            new MensagemEmailFiscal.Anexo("a.xml", "application/xml", new byte[]{1})));
    private final List<String> semTransacao = new ArrayList<>();

    private ProcessadorEntregaEmail processador;

    @BeforeEach
    void setUp() {
        processador = new ProcessadorEntregaEmail(transacoes, pdfService, composicao, gateway, notificacao, clock, LEASE);
        when(transacoes.registarResultado(any(), any(), any(), any(), any())).thenReturn(1);
        when(transacoes.renovarLease(any(), any())).thenReturn(true);
        snapshot(Optional.of(linhaXml()));
        when(pdfService.lerPdf(tenantId, documentoId)).thenAnswer(inv -> {
            registarSemTransacao("pdf");
            return PDF;
        });
        when(composicao.compor(any(), anyString(), any())).thenAnswer(inv -> {
            registarSemTransacao("composicao");
            return mensagem;
        });
        enviarDevolve(new ResultadoEnvioEmail.Enviado());
    }

    // ---- fixtures ----

    private EntregaEmailReclamada item(int tentativas) {
        return item(tentativas, DESTINATARIO);
    }

    private EntregaEmailReclamada item(int tentativas, String destinatario) {
        return new EntregaEmailReclamada(UUID.randomUUID(), tenantId, documentoId, destinatario, tentativas, 4L, 2);
    }

    private DocumentoFiscal documento() {
        return DocumentoFiscal.builder()
                .id(documentoId).tenantId(tenantId).tipo(TipoDocumentoFiscal.FR).ambiente(AmbienteFiscal.SIMULADO)
                .serieCodigo("2026A").ano(2026).numero(123L).numeroFormatado(NUMERO)
                .dataEmissao(LocalDate.of(2026, 6, 15)).emitidoEm(AGORA)
                .emitenteNif("512345679").emitenteFirma("Silva & Associados").emitenteMorada("Avenida, 1")
                .emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Cliente").adquirenteMorada("Rua 2")
                .meioPagamentoCodigo("10").moeda("CVE")
                .totalBase(new BigDecimal("100.00")).totalIva(new BigDecimal("15.00"))
                .totalRetencao(BigDecimal.ZERO).totalDocumento(new BigDecimal("115.00"))
                .valorLiquido(new BigDecimal("115.00"))
                .build();
    }

    private static DocumentoFiscalXml linhaXml() {
        DocumentoFiscalXml row = mock(DocumentoFiscalXml.class);
        when(row.getXml()).thenReturn("<Dfe/>");
        return row;
    }

    private void snapshot(Optional<DocumentoFiscalXml> xml) {
        when(transacoes.carregarSnapshot(tenantId, documentoId))
                .thenReturn(Optional.of(new SnapshotEntregaEmail(documento(), xml, Optional.empty(), Optional.empty())));
    }

    private void enviarDevolve(ResultadoEnvioEmail resultado) {
        when(gateway.enviar(any())).thenAnswer(inv -> {
            registarSemTransacao("gateway");
            return resultado;
        });
    }

    private void registarSemTransacao(String passo) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            semTransacao.add(passo);
        }
    }

    private void verificarResultado(EntregaEmailReclamada item, EstadoEntregaEmail estado, String codigo, Instant proxima) {
        verify(transacoes).registarResultado(eq(item), eq(estado), codigo == null ? isNull() : eq(codigo), any(),
                proxima == null ? isNull() : eq(proxima));
    }

    private void verificarSemNotificacao() {
        verify(notificacao, never()).notificarFalhaPersistente(any(), any(), any(), anyInt(), anyInt());
    }

    // ---- envio ----

    @Test
    void enviadoRegistaEnviadoSemNotificar() {
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        verify(transacoes).registarResultado(item, EstadoEntregaEmail.ENVIADO, null, null, null);
        verify(gateway).enviar(mensagem);
        verify(composicao).compor(any(), eq(DESTINATARIO), eq(PDF));
        verificarSemNotificacao();
    }

    @Test
    void erroTransitorioNaSegundaTentativaFicaPendenteComRecuo() {
        enviarDevolve(new ResultadoEnvioEmail.ErroTransitorio("SMTP_INDISPONIVEL", "Servidor indisponível."));
        EntregaEmailReclamada item = item(2);

        processador.processar(item);

        verify(transacoes).registarResultado(item, EstadoEntregaEmail.PENDENTE, "SMTP_INDISPONIVEL",
                "Servidor indisponível.", AGORA.plus(BackoffEntregaEmail.atraso(2)));
        verificarSemNotificacao();
    }

    @Test
    void erroTransitorioNaQuintaTentativaFalhaENotificaUmaVez() {
        enviarDevolve(new ResultadoEnvioEmail.ErroTransitorio("SMTP_INDISPONIVEL", "Servidor indisponível."));
        EntregaEmailReclamada item = item(EntregaEmailFiscal.MAX_TENTATIVAS);

        processador.processar(item);

        verify(transacoes).registarResultado(item, EstadoEntregaEmail.FALHOU, "SMTP_INDISPONIVEL",
                "Servidor indisponível.", null);
        verify(notificacao, times(1)).notificarFalhaPersistente(tenantId, documentoId, NUMERO, 2, 5);
    }

    @Test
    void erroPermanenteNaPrimeiraTentativaFalhaENotificaComUmaTentativa() {
        enviarDevolve(new ResultadoEnvioEmail.ErroPermanente("DESTINATARIO_RECUSADO", "Destinatário recusado."));
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        verify(transacoes).registarResultado(item, EstadoEntregaEmail.FALHOU, "DESTINATARIO_RECUSADO",
                "Destinatário recusado.", null);
        verify(notificacao, times(1)).notificarFalhaPersistente(tenantId, documentoId, NUMERO, 2, 1);
    }

    @Test
    void notificacaoSoDepoisDoRegistoDoResultado() {
        enviarDevolve(new ResultadoEnvioEmail.ErroPermanente("DESTINATARIO_RECUSADO", "Destinatário recusado."));
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        var ordem = inOrder(transacoes, gateway, notificacao);
        ordem.verify(transacoes).renovarLease(item, LEASE);
        ordem.verify(gateway).enviar(any());
        ordem.verify(transacoes).registarResultado(any(), any(), any(), any(), any());
        ordem.verify(notificacao).notificarFalhaPersistente(any(), any(), any(), anyInt(), anyInt());
    }

    // ---- falhas antes do envio ----

    @Test
    void snapshotEmFaltaFalhaComDocumentoInexistenteSemEnviar() {
        when(transacoes.carregarSnapshot(tenantId, documentoId)).thenReturn(Optional.empty());
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        verificarResultado(item, EstadoEntregaEmail.FALHOU, ProcessadorEntregaEmail.DOCUMENTO_INEXISTENTE, null);
        verify(gateway, never()).enviar(any());
    }

    @Test
    void xmlEmFaltaFicaPendenteComFicheiroIndisponivel() {
        snapshot(Optional.empty());
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        verificarResultado(item, EstadoEntregaEmail.PENDENTE, FicheiroFiscalIndisponivelException.CODIGO,
                AGORA.plus(BackoffEntregaEmail.atraso(1)));
        verify(gateway, never()).enviar(any());
        verify(pdfService, never()).lerPdf(any(), any());
    }

    @Test
    void pdfIndisponivelFicaPendente() {
        when(pdfService.lerPdf(tenantId, documentoId)).thenThrow(new FicheiroFiscalIndisponivelException());
        EntregaEmailReclamada item = item(3);

        processador.processar(item);

        verificarResultado(item, EstadoEntregaEmail.PENDENTE, FicheiroFiscalIndisponivelException.CODIGO,
                AGORA.plus(BackoffEntregaEmail.atraso(3)));
        verify(gateway, never()).enviar(any());
    }

    @Test
    void storageIndisponivelFicaPendente() {
        when(pdfService.lerPdf(tenantId, documentoId))
                .thenThrow(new StorageUnavailableException("minio em baixo", new RuntimeException()));
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        verificarResultado(item, EstadoEntregaEmail.PENDENTE, ProcessadorEntregaEmail.STORAGE_INDISPONIVEL,
                AGORA.plus(BackoffEntregaEmail.atraso(1)));
        verify(transacoes, never()).registarResultado(any(), any(), any(), eq("minio em baixo"), any());
        verify(gateway, never()).enviar(any());
    }

    @Test
    void falhaDeGeracaoDoPdfFicaPendente() {
        when(pdfService.lerPdf(tenantId, documentoId)).thenThrow(new FalhaGeracaoPdf(new RuntimeException("x")));
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        verificarResultado(item, EstadoEntregaEmail.PENDENTE, ProcessadorEntregaEmail.FALHA_PDF,
                AGORA.plus(BackoffEntregaEmail.atraso(1)));
        verify(gateway, never()).enviar(any());
    }

    @Test
    void falhaTransitoriaAntesDoEnvioNaQuintaTentativaFalhaENotifica() {
        when(pdfService.lerPdf(tenantId, documentoId)).thenThrow(new FicheiroFiscalIndisponivelException());
        EntregaEmailReclamada item = item(5);

        processador.processar(item);

        verificarResultado(item, EstadoEntregaEmail.FALHOU, FicheiroFiscalIndisponivelException.CODIGO, null);
        verify(notificacao).notificarFalhaPersistente(tenantId, documentoId, NUMERO, 2, 5);
    }

    @Test
    void destinatarioInvalidoFalhaSemChamarOGateway() {
        for (String invalido : List.of("", "sem-arroba", "a@b.cv\r\nBcc: x@y.cv", "a@b.cv, c@d.cv")) {
            EntregaEmailReclamada item = item(1, invalido);

            processador.processar(item);

            verificarResultado(item, EstadoEntregaEmail.FALHOU, ProcessadorEntregaEmail.DESTINATARIO_INVALIDO, null);
        }
        verify(gateway, never()).enviar(any());
        verify(composicao, never()).compor(any(), any(), any());
    }

    // ---- lease ----

    @Test
    void leasePerdidoNuncaEnviaNemRegista() {
        when(transacoes.renovarLease(any(), any())).thenReturn(false);

        processador.processar(item(1));

        verify(gateway, never()).enviar(any());
        verify(transacoes, never()).registarResultado(any(), any(), any(), any(), any());
        verificarSemNotificacao();
    }

    @Test
    void leaseRenovadoImediatamenteAntesDoEnvioComODuracaoConfigurada() {
        EntregaEmailReclamada item = item(1);

        processador.processar(item);

        var ordem = inOrder(pdfService, composicao, transacoes, gateway);
        ordem.verify(pdfService).lerPdf(tenantId, documentoId);
        ordem.verify(composicao).compor(any(), any(), any());
        ordem.verify(transacoes).renovarLease(item, LEASE);
        ordem.verify(gateway).enviar(any());
    }

    @Test
    void resultadoNaoGravadoNaoNotifica() {
        enviarDevolve(new ResultadoEnvioEmail.ErroPermanente("DESTINATARIO_RECUSADO", "Destinatário recusado."));
        when(transacoes.registarResultado(any(), any(), any(), any(), any())).thenReturn(0);

        processador.processar(item(1));

        verificarSemNotificacao();
    }

    // ---- isolamento ----

    @Test
    void excecaoInesperadaViraFalhaInternaPendente() {
        when(composicao.compor(any(), anyString(), any())).thenThrow(new IllegalStateException("texto interno"));
        EntregaEmailReclamada item = item(2);

        assertThatCode(() -> processador.processar(item)).doesNotThrowAnyException();

        verify(transacoes).registarResultado(item, EstadoEntregaEmail.PENDENTE, ProcessadorEntregaEmail.FALHA_INTERNA,
                ProcessadorEntregaEmail.MSG_FALHA_INTERNA, AGORA.plus(BackoffEntregaEmail.atraso(2)));
    }

    @Test
    void excecaoInesperadaNaQuintaTentativaFalhaENotifica() {
        when(gateway.enviar(any())).thenThrow(new RuntimeException("boom"));
        EntregaEmailReclamada item = item(5);

        assertThatCode(() -> processador.processar(item)).doesNotThrowAnyException();

        verify(transacoes).registarResultado(item, EstadoEntregaEmail.FALHOU, ProcessadorEntregaEmail.FALHA_INTERNA,
                ProcessadorEntregaEmail.MSG_FALHA_INTERNA, null);
        verify(notificacao).notificarFalhaPersistente(tenantId, documentoId, NUMERO, 2, 5);
    }

    @Test
    void excecaoNoSnapshotNoRegistoOuNaNotificacaoNuncaPropaga() {
        when(transacoes.carregarSnapshot(any(), any())).thenThrow(new RuntimeException("bd"));
        assertThatCode(() -> processador.processar(item(5))).doesNotThrowAnyException();

        when(transacoes.registarResultado(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("bd"));
        assertThatCode(() -> processador.processar(item(1))).doesNotThrowAnyException();

        doReturn(1).when(transacoes).registarResultado(any(), any(), any(), any(), any());
        when(notificacao.notificarFalhaPersistente(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("notif"));
        assertThatCode(() -> processador.processar(item(5))).doesNotThrowAnyException();
    }

    @Test
    void errorDaJvmPropaga() {
        when(gateway.enviar(any())).thenThrow(new OutOfMemoryError("simulado"));

        assertThatThrownBy(() -> processador.processar(item(1))).isInstanceOf(OutOfMemoryError.class);
    }

    // ---- esgotadas ----

    @Test
    void notificarEsgotadaUsaEpisodioETentativasDoItem() {
        EntregaEmailReclamada item = new EntregaEmailReclamada(UUID.randomUUID(), tenantId, documentoId, DESTINATARIO,
                4, 9L, 3);

        processador.notificarEsgotada(item);

        verify(notificacao).notificarFalhaPersistente(tenantId, documentoId, NUMERO, 3, 4);
    }

    @Test
    void notificarEsgotadaSemSnapshotUsaOIdENuncaLanca() {
        when(transacoes.carregarSnapshot(tenantId, documentoId)).thenReturn(Optional.empty());
        EntregaEmailReclamada item = item(5);

        processador.notificarEsgotada(item);

        verify(notificacao).notificarFalhaPersistente(tenantId, documentoId, documentoId.toString(), 2, 5);

        when(notificacao.notificarFalhaPersistente(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("x"));
        assertThatCode(() -> processador.notificarEsgotada(item)).doesNotThrowAnyException();
    }

    // ---- transações ----

    @Test
    void pdfComposicaoEGatewaySemTransacaoAtiva() {
        processador.processar(item(1));

        assertThat(semTransacao).containsExactly("pdf", "composicao", "gateway");
    }

    @Test
    void semTransactionalNaClasse() {
        assertThat(ProcessadorEntregaEmail.class.isAnnotationPresent(Transactional.class)).isFalse();
        for (Method m : ProcessadorEntregaEmail.class.getDeclaredMethods()) {
            assertThat(m.isAnnotationPresent(Transactional.class)).as(m.getName()).isFalse();
        }
    }
}
