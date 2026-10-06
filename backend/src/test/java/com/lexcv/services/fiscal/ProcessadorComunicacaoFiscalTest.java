package com.lexcv.services.fiscal;

import com.lexcv.fiscal.efatura.DfeMarshaller;
import com.lexcv.fiscal.efatura.DfeValidador;
import com.lexcv.fiscal.efatura.DfeXmlBuilder;
import com.lexcv.fiscal.efatura.EfaturaGateway;
import com.lexcv.fiscal.efatura.IudGerador;
import com.lexcv.fiscal.efatura.PedidoComunicacao;
import com.lexcv.fiscal.efatura.RecusaFormatoEfatura;
import com.lexcv.fiscal.efatura.ResultadoComunicacao;
import com.lexcv.fiscal.efatura.ResultadoValidacao;
import com.lexcv.fiscal.efatura.TransmissaoEfatura;
import com.lexcv.fiscal.efatura.xsd.Dfe;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 136-13: comportamento do processador por item, com todos os colaboradores simulados.
 */
class ProcessadorComunicacaoFiscalTest {

    private static final Instant AGORA = Instant.parse("2026-06-15T12:00:00Z");
    private static final String NIF = "512345679";
    private static final LocalDate DATA = LocalDate.of(2026, 6, 15);
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final ComunicacaoFiscalTransacoes transacoes = mock(ComunicacaoFiscalTransacoes.class);
    private final DfeXmlBuilder builder = mock(DfeXmlBuilder.class);
    private final DfeMarshaller marshaller = mock(DfeMarshaller.class);
    private final DfeValidador validador = mock(DfeValidador.class);
    private final IudGerador iudGerador = mock(IudGerador.class);
    private final EfaturaGateway gateway = mock(EfaturaGateway.class);
    private final NotificacaoComunicacaoFiscal notificacao = mock(NotificacaoComunicacaoFiscal.class);
    private final TransmissaoEfatura transmissao = new TransmissaoEfatura("512345679", "LEXCVSIM", "LexCV", "3.0.0");
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    private ProcessadorComunicacaoFiscal processador;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentoId = UUID.randomUUID();
    private final byte[] xmlGerado = "<Dfe gerado=\"1\"/>".getBytes(StandardCharsets.UTF_8);
    private final Dfe dfe = new Dfe();

    @BeforeEach
    void setUp() {
        processador = new ProcessadorComunicacaoFiscal(transacoes, builder, marshaller, validador, iudGerador,
                gateway, transmissao, notificacao, clock, LEASE);
        when(transacoes.registarResultado(any(), any(), any(), any(), any())).thenReturn(1);
        when(transacoes.renovarLease(any(), any())).thenReturn(true);
        when(iudGerador.gerar(anyInt(), any(), anyString(), anyInt(), anyInt(), anyLong())).thenReturn("CV3-GERADO");
        when(builder.construir(any(), anyString(), any())).thenReturn(dfe);
        when(marshaller.marshal(dfe)).thenReturn(xmlGerado);
        when(validador.validar(any())).thenReturn(ResultadoValidacao.sucesso());
        when(gateway.comunicar(any())).thenReturn(new ResultadoComunicacao.AceiteSimulado("SIMULADO-X"));
    }

    // ---- fixtures ----

    private ComunicacaoReclamada item(int tentativas) {
        return new ComunicacaoReclamada(UUID.randomUUID(), tenantId, documentoId, AmbienteFiscal.SIMULADO,
                tentativas, 3L, 2);
    }

    private DocumentoFiscal fr() {
        return documento(TipoDocumentoFiscal.FR, null, null);
    }

    private DocumentoFiscal nc() {
        return documento(TipoDocumentoFiscal.NC, UUID.randomUUID(), MotivoNotaCredito.CORRECAO_VALOR);
    }

    private DocumentoFiscal documento(TipoDocumentoFiscal tipo, UUID origem, MotivoNotaCredito motivo) {
        return documento(tipo, origem, motivo, 7L);
    }

    private DocumentoFiscal documento(TipoDocumentoFiscal tipo, UUID origem, MotivoNotaCredito motivo, long numero) {
        String serie = tipo == TipoDocumentoFiscal.FR ? "SIM-FR-2026" : "SIM-NC-2026";
        return DocumentoFiscal.builder()
                .id(documentoId).tenantId(tenantId).tipo(tipo).ambiente(AmbienteFiscal.SIMULADO)
                .serieCodigo(serie).ano(2026).numero(numero).numeroFormatado(serie + "/" + numero)
                .dataEmissao(DATA).emitidoEm(Instant.parse("2026-06-15T12:34:56Z"))
                .emitenteNif(NIF).emitenteFirma("Silva & Associados").emitenteMorada("Avenida, 1")
                .emitenteLocalidade("Praia").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Cliente Teste").adquirenteMorada("Rua 2")
                .adquirenteLocalidade("Mindelo").meioPagamentoCodigo("10").moeda("CVE")
                .documentoOrigemId(origem).motivoCodigo(motivo)
                .totalBase(new BigDecimal("100.00")).totalIva(new BigDecimal("15.00"))
                .totalRetencao(new BigDecimal("0.00")).totalDocumento(new BigDecimal("115.00"))
                .valorLiquido(new BigDecimal("115.00"))
                .build();
    }

    private DocumentoFiscalLinha linha() {
        return DocumentoFiscalLinha.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).documentoFiscalId(documentoId).numeroLinha(1)
                .descricao("Honorários").quantidade(new BigDecimal("1.0000"))
                .precoUnitario(new BigDecimal("100.00")).valorBase(new BigDecimal("100.00"))
                .taxaIva(new BigDecimal("15.0000")).valorIva(new BigDecimal("15.00"))
                .valorRetencao(new BigDecimal("0.00")).totalLinha(new BigDecimal("115.00"))
                .build();
    }

    private void snapshot(DocumentoFiscal d, Optional<DocumentoFiscalXml> xml, Optional<String> iudOrigem,
                          Optional<String> numeroOrigem) {
        when(transacoes.carregarSnapshot(tenantId, documentoId))
                .thenReturn(Optional.of(new SnapshotComunicacao(d, linha(), xml, iudOrigem, numeroOrigem)));
    }

    private static DocumentoFiscalXml linhaXml(String iud, String xml) {
        DocumentoFiscalXml row = mock(DocumentoFiscalXml.class);
        when(row.getIud()).thenReturn(iud);
        when(row.getXml()).thenReturn(xml);
        return row;
    }

    private void gravarDevolve(Optional<DocumentoFiscalXml> row) {
        when(transacoes.gravarXml(any(), any(), anyString(), any(), anyInt(), anyInt(), anyString(), anyString(),
                anyString())).thenReturn(row);
    }

    private void verificarGravarNunca() {
        verify(transacoes, never()).gravarXml(any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any());
    }

    private static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    // ---- behaviours ----

    @Test
    void frSemXml_geraIudGravaXmlEnviaALinhaGravadaERegistaAceite() throws Exception {
        snapshot(fr(), Optional.empty(), Optional.empty(), Optional.empty());
        gravarDevolve(Optional.of(linhaXml("CV3-GRAVADO", "<gravado/>")));
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verify(iudGerador).gerar(3, DATA, NIF, 99999, 2, 7L);
        ArgumentCaptor<String> xml = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sha = ArgumentCaptor.forClass(String.class);
        verify(transacoes).gravarXml(eq(tenantId), eq(documentoId), eq("CV3-GERADO"), eq(AmbienteFiscal.SIMULADO),
                eq(3), eq(99999), eq("2024-05-27"), xml.capture(), sha.capture());
        assertThat(xml.getValue()).isEqualTo(new String(xmlGerado, StandardCharsets.UTF_8));
        assertThat(sha.getValue()).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(sha256(xmlGerado));

        ArgumentCaptor<PedidoComunicacao> pedido = ArgumentCaptor.forClass(PedidoComunicacao.class);
        verify(gateway).comunicar(pedido.capture());
        assertThat(pedido.getValue().iud()).isEqualTo("CV3-GRAVADO");
        assertThat(pedido.getValue().xml()).isEqualTo("<gravado/>");
        assertThat(pedido.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(pedido.getValue().documentoFiscalId()).isEqualTo(documentoId);

        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, null);
        verifyNoInteractions(notificacao);
    }

    @Test
    void xmlExistente_reutilizaSemGerarIudNemConstruir() {
        snapshot(fr(), Optional.of(linhaXml("CV3-EXISTENTE", "<existente/>")), Optional.empty(), Optional.empty());
        ComunicacaoReclamada item = item(2);

        processador.processar(item);

        verifyNoInteractions(iudGerador, builder, marshaller);
        verificarGravarNunca();
        ArgumentCaptor<PedidoComunicacao> pedido = ArgumentCaptor.forClass(PedidoComunicacao.class);
        verify(gateway).comunicar(pedido.capture());
        assertThat(pedido.getValue().iud()).isEqualTo("CV3-EXISTENTE");
        assertThat(pedido.getValue().xml()).isEqualTo("<existente/>");
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, null);
    }

    @Test
    void ncSemIudDaOrigem_eTransitorioOrigemSemIudComRecuo() {
        snapshot(nc(), Optional.empty(), Optional.empty(), Optional.of("SIM-FR-2026/1"));
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verifyNoInteractions(builder, iudGerador, gateway);
        verificarGravarNunca();
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.PENDENTE, "ORIGEM_SEM_IUD",
                "A fatura-recibo de origem ainda não foi comunicada.", AGORA.plus(Duration.ofSeconds(30)));
    }

    @Test
    void renovaOLeaseDoItemAntesDoEnvio() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(transacoes, gateway);
        ordem.verify(transacoes).renovarLease(item, LEASE);
        ordem.verify(gateway).comunicar(any());
        ordem.verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.ACEITE_SIMULADO, null, null, null);
    }

    @Test
    void leasePerdidoAntesDoEnvio_naoEnviaNemRegista() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        when(transacoes.renovarLease(any(), any())).thenReturn(false);

        processador.processar(item(8));

        verifyNoInteractions(gateway, notificacao);
        verify(transacoes, never()).registarResultado(any(), any(), any(), any(), any());
    }

    @Test
    void erroTransitorioNaOitavaTentativa_eErroENotificaComOEpisodio() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        when(gateway.comunicar(any())).thenReturn(new ResultadoComunicacao.ErroTransitorio("FALHA_SIMULADA",
                "Falha simulada do serviço de comunicação."));
        ComunicacaoReclamada item = item(8);

        processador.processar(item);

        verify(transacoes).registarResultado(eq(item), eq(EstadoComunicacaoFiscal.ERRO), eq("FALHA_SIMULADA"),
                eq("Falha simulada do serviço de comunicação."), isNull());
        verify(notificacao, times(1)).notificarFalhaPersistente(tenantId, documentoId, "SIM-FR-2026/7", 2);
    }

    @Test
    void leasePerdido_naoNotifica() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        when(gateway.comunicar(any())).thenReturn(new ResultadoComunicacao.ErroTransitorio("FALHA_SIMULADA", "x"));
        when(transacoes.registarResultado(any(), any(), any(), any(), any())).thenReturn(0);

        processador.processar(item(8));

        verifyNoInteractions(notificacao);
    }

    @Test
    void notificacaoQueLanca_naoPropaga() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        when(gateway.comunicar(any())).thenReturn(new ResultadoComunicacao.ErroTransitorio("FALHA_SIMULADA", "x"));
        when(notificacao.notificarFalhaPersistente(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> processador.processar(item(8))).doesNotThrowAnyException();
        verify(notificacao).notificarFalhaPersistente(any(), any(), any(), anyInt());
    }

    @Test
    void recusaDeFormato_eRejeitadoComOCodigoSemGravarNemEnviar() {
        snapshot(fr(), Optional.empty(), Optional.empty(), Optional.empty());
        when(builder.construir(any(), anyString(), any()))
                .thenThrow(new RecusaFormatoEfatura(RecusaFormatoEfatura.Codigo.FIRMA_EXCEDE_150));
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verificarGravarNunca();
        verifyNoInteractions(gateway);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.REJEITADO, "FIRMA_EXCEDE_150",
                RecusaFormatoEfatura.Codigo.FIRMA_EXCEDE_150.mensagem(), null);
    }

    @Test
    void dadoQueOIudRecusa_eRejeitadoDadosInvalidosSemNovaTentativa() {
        snapshot(fr(), Optional.empty(), Optional.empty(), Optional.empty());
        when(iudGerador.gerar(anyInt(), any(), anyString(), anyInt(), anyInt(), anyLong()))
                .thenThrow(new IllegalArgumentException("NIF inválido para o IUD"));
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verificarGravarNunca();
        verifyNoInteractions(gateway, builder);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.REJEITADO, "DADOS_INVALIDOS",
                RecusaFormatoEfatura.Codigo.DADOS_INVALIDOS.mensagem(), null);
    }

    @Test
    void ncSemMotivo_eRejeitadoDadosInvalidos() {
        snapshot(documento(TipoDocumentoFiscal.NC, UUID.randomUUID(), null), Optional.empty(),
                Optional.of("CV3-ORIGEM"), Optional.of("SIM-FR-2026/1"));
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verifyNoInteractions(gateway, builder, iudGerador);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.REJEITADO, "DADOS_INVALIDOS",
                RecusaFormatoEfatura.Codigo.DADOS_INVALIDOS.mensagem(), null);
    }

    @Test
    void numeroAcimaDoLimite_eRejeitadoNumeroForaDoLimiteAntesDoIud() {
        DocumentoFiscal d = documento(TipoDocumentoFiscal.FR, null, null, 1_000_000_000L);
        snapshot(d, Optional.empty(), Optional.empty(), Optional.empty());
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verifyNoInteractions(gateway, builder, iudGerador);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.REJEITADO, "NUMERO_FORA_DO_LIMITE",
                RecusaFormatoEfatura.Codigo.NUMERO_FORA_DO_LIMITE.mensagem(), null);
    }

    @Test
    void xmlInvalido_eRejeitadoXsdInvalidoSemGravarNemEnviar() {
        snapshot(fr(), Optional.empty(), Optional.empty(), Optional.empty());
        when(validador.validar(any())).thenReturn(ResultadoValidacao.invalido("XSD_INVALIDO", 12, 4));
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verificarGravarNunca();
        verifyNoInteractions(gateway);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.REJEITADO, "XSD_INVALIDO",
                "O documento não cumpre o formato eFatura (linha 12).", null);
    }

    @Test
    void colisaoDeIud_eTransitorioComRecuo() {
        snapshot(fr(), Optional.empty(), Optional.empty(), Optional.empty());
        gravarDevolve(Optional.empty());
        ComunicacaoReclamada item = item(2);

        processador.processar(item);

        verifyNoInteractions(gateway);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.PENDENTE, "IUD_COLISAO",
                "Identificador repetido; nova tentativa automática.", AGORA.plus(Duration.ofMinutes(2)));
    }

    @Test
    void excecaoInesperada_eFalhaInternaSemTextoDaExcecao() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        when(gateway.comunicar(any())).thenThrow(new RuntimeException("Ana Lopes 512345670"));
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(transacoes).registarResultado(eq(item), eq(EstadoComunicacaoFiscal.PENDENTE), eq("FALHA_INTERNA"),
                mensagem.capture(), eq(AGORA.plus(Duration.ofSeconds(30))));
        assertThat(mensagem.getValue()).isEqualTo("Falha interna ao comunicar o documento.")
                .doesNotContain("Ana").doesNotContain("512345670");
    }

    @Test
    void excecaoInesperadaNaOitavaTentativa_eErroFalhaInternaENotifica() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        when(gateway.comunicar(any())).thenThrow(new RuntimeException("Ana Lopes 512345670"));
        ComunicacaoReclamada item = item(8);

        processador.processar(item);

        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(transacoes).registarResultado(eq(item), eq(EstadoComunicacaoFiscal.ERRO), eq("FALHA_INTERNA"),
                mensagem.capture(), isNull());
        assertThat(mensagem.getValue()).doesNotContain("Ana").doesNotContain("512345670");
        verify(notificacao).notificarFalhaPersistente(tenantId, documentoId, "SIM-FR-2026/7", 2);
    }

    @Test
    void snapshotInexistente_eRejeitadoDocumentoInexistente() {
        when(transacoes.carregarSnapshot(tenantId, documentoId)).thenReturn(Optional.empty());
        ComunicacaoReclamada item = item(1);

        processador.processar(item);

        verifyNoInteractions(gateway, builder, iudGerador);
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.REJEITADO, "DOCUMENTO_INEXISTENTE",
                "O documento fiscal não foi encontrado.", null);
        verifyNoInteractions(notificacao);
    }

    @Test
    void registarResultadoQueLanca_naoPropagaNemNotifica() {
        snapshot(fr(), Optional.of(linhaXml("CV3-E", "<e/>")), Optional.empty(), Optional.empty());
        when(gateway.comunicar(any())).thenReturn(new ResultadoComunicacao.ErroTransitorio("FALHA_SIMULADA", "x"));
        doThrow(new RuntimeException("db down")).when(transacoes)
                .registarResultado(any(), any(), any(), any(), any());

        assertThatCode(() -> processador.processar(item(8))).doesNotThrowAnyException();
        verifyNoInteractions(notificacao);
    }

    @Test
    void errorDeJvm_tambemNaoPropaga() {
        when(transacoes.carregarSnapshot(any(), any())).thenThrow(new StackOverflowError());
        ComunicacaoReclamada item = item(1);

        assertThatCode(() -> processador.processar(item)).doesNotThrowAnyException();
        verify(transacoes).registarResultado(item, EstadoComunicacaoFiscal.PENDENTE, "FALHA_INTERNA",
                "Falha interna ao comunicar o documento.", AGORA.plus(Duration.ofSeconds(30)));
    }

    @Test
    void notificarEsgotada_carregaONumeroENotificaComOEpisodio() {
        snapshot(fr(), Optional.empty(), Optional.empty(), Optional.empty());

        processador.notificarEsgotada(item(8));

        verify(notificacao).notificarFalhaPersistente(tenantId, documentoId, "SIM-FR-2026/7", 2);
        verifyNoInteractions(gateway, builder, iudGerador);
    }

    @Test
    void semTransacoesNoProcessador() {
        assertThat(ProcessadorComunicacaoFiscal.class.isAnnotationPresent(
                org.springframework.transaction.annotation.Transactional.class)).isFalse();
        for (Method m : ProcessadorComunicacaoFiscal.class.getDeclaredMethods()) {
            assertThat(m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                    .as(m.getName()).isFalse();
        }
        assertThat(Modifier.isPublic(ProcessadorComunicacaoFiscal.class.getModifiers())).isTrue();
    }
}
