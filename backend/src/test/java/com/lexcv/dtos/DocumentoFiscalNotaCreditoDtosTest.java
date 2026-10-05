package com.lexcv.dtos;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.Pagamento;
import com.lexcv.models.RegimeIva;
import com.lexcv.services.fiscal.CalculoFiscal;
import com.lexcv.services.fiscal.ProjetoNotaCredito;
import com.lexcv.services.fiscal.TipoCredito;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 135 (NCRD-01..03): campos de Nota de Crédito nos DTOs de leitura -- detalhe da FR (NC
 * emitidas, total creditado, valor ainda creditável), detalhe da NC (origem e motivo), linha da
 * listagem (número da FR de origem) e pagamento (estorno separado de documentoFiscal).
 */
class DocumentoFiscalNotaCreditoDtosTest {

    private static DocumentoFiscal.DocumentoFiscalBuilder doc(TipoDocumentoFiscal tipo, String numero, String total) {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .tipo(tipo)
                .ambiente(AmbienteFiscal.SIMULADO)
                .numeroFormatado(numero)
                .dataEmissao(LocalDate.of(2026, 10, 5))
                .metodoPagamento("DINHEIRO")
                .totalDocumento(new BigDecimal(total));
    }

    private static DocumentoFiscal nc(String numero, String total, UUID origem, MotivoNotaCredito motivo) {
        return doc(TipoDocumentoFiscal.NC, numero, total).documentoOrigemId(origem).motivoCodigo(motivo)
                .motivoTexto("Texto do motivo").build();
    }

    @Test
    void detalheDaFrListaAsNcECalculaOCreditavel() {
        DocumentoFiscal fr = doc(TipoDocumentoFiscal.FR, "SIM-FR-2026/7", "120000.00").build();
        DocumentoFiscal nc2 = nc("SIM-NC-2026/2", "30000.00", fr.getId(), MotivoNotaCredito.OUTRO);
        DocumentoFiscal nc1 = nc("SIM-NC-2026/1", "20000.00", fr.getId(), MotivoNotaCredito.CORRECAO_VALOR);

        DocumentoFiscalDetalheResponse r = DocumentoFiscalDetalheResponse.de(fr, List.of(),
                EstadoComunicacaoFiscal.PENDENTE, null, List.of(nc2, nc1));

        assertEquals(2, r.notasCredito().size());
        assertEquals("SIM-NC-2026/2", r.notasCredito().get(0).numeroFormatado());
        assertEquals("SIM-NC-2026/1", r.notasCredito().get(1).numeroFormatado());
        assertEquals("CORRECAO_VALOR", r.notasCredito().get(1).motivoCodigo());
        assertEquals("Correção de valor", r.notasCredito().get(1).motivoRotulo());
        assertEquals(0, new BigDecimal("20000.00").compareTo(r.notasCredito().get(1).totalDocumento()));
        assertEquals(new BigDecimal("50000.00"), r.totalCreditado());
        assertEquals(new BigDecimal("70000.00"), r.valorCreditavelRestante());
        assertNull(r.documentoOrigem());
        assertNull(r.motivoCodigo());
        assertNull(r.motivoRotulo());
        assertNull(r.motivoTexto());
    }

    @Test
    void detalheDaFrSemNcTemCreditavelIgualAoTotal() {
        DocumentoFiscal fr = doc(TipoDocumentoFiscal.FR, "SIM-FR-2026/7", "115.00").build();
        DocumentoFiscalDetalheResponse r = DocumentoFiscalDetalheResponse.de(fr, List.of(), null, null, List.of());
        assertTrue(r.notasCredito().isEmpty());
        assertEquals(new BigDecimal("0.00"), r.totalCreditado());
        assertEquals(new BigDecimal("115.00"), r.valorCreditavelRestante());
    }

    @Test
    void detalheDaNcExpoeOrigemEMotivo() {
        UUID origem = UUID.randomUUID();
        DocumentoFiscal n = nc("SIM-NC-2026/1", "20000.00", origem, MotivoNotaCredito.ERRO_DADOS_CLIENTE);
        DocumentoFiscalRef ref = new DocumentoFiscalRef(origem, "SIM-FR-2026/7");

        DocumentoFiscalDetalheResponse r = DocumentoFiscalDetalheResponse.de(n, List.of(), null, ref, List.of());

        assertEquals(ref, r.documentoOrigem());
        assertEquals("ERRO_DADOS_CLIENTE", r.motivoCodigo());
        assertEquals("Erro nos dados do cliente", r.motivoRotulo());
        assertEquals("Texto do motivo", r.motivoTexto());
        assertNull(r.totalCreditado());
        assertNull(r.valorCreditavelRestante());
        assertTrue(r.notasCredito().isEmpty());
        assertEquals("NC", r.tipo());
        assertEquals("Nota de Crédito", r.tipoRotulo());
    }

    @Test
    void fabricaAntigaDeTresArgumentosNaoTemDadosDeNc() {
        DocumentoFiscal fr = doc(TipoDocumentoFiscal.FR, "SIM-FR-2026/7", "115.00").build();
        DocumentoFiscalDetalheResponse r = DocumentoFiscalDetalheResponse.de(fr, List.of(), null);
        assertTrue(r.notasCredito().isEmpty());
        assertNull(r.documentoOrigem());
        assertNull(r.motivoCodigo());
    }

    @Test
    void listaDeNcECopiadaENaoModificavel() {
        DocumentoFiscal fr = doc(TipoDocumentoFiscal.FR, "SIM-FR-2026/7", "100.00").build();
        List<DocumentoFiscal> ncs = new ArrayList<>(List.of(nc("SIM-NC-2026/1", "10.00", fr.getId(),
                MotivoNotaCredito.OUTRO)));
        DocumentoFiscalDetalheResponse r = DocumentoFiscalDetalheResponse.de(fr, List.of(), null, null, ncs);
        ncs.clear();
        assertEquals(1, r.notasCredito().size());
        assertThrows(UnsupportedOperationException.class, () -> r.notasCredito().clear());
    }

    @Test
    void resumoExpoeONumeroDaOrigem() {
        UUID origem = UUID.randomUUID();
        DocumentoFiscal n = nc("SIM-NC-2026/1", "20.00", origem, MotivoNotaCredito.OUTRO);
        DocumentoFiscalResumoResponse r = DocumentoFiscalResumoResponse.de(n, null, "SIM-FR-2026/7");
        assertEquals(origem, r.documentoOrigemId());
        assertEquals("SIM-FR-2026/7", r.documentoOrigemNumero());

        DocumentoFiscal fr = doc(TipoDocumentoFiscal.FR, "SIM-FR-2026/7", "20.00").build();
        DocumentoFiscalResumoResponse rf = DocumentoFiscalResumoResponse.de(fr, null);
        assertNull(rf.documentoOrigemId());
        assertNull(rf.documentoOrigemNumero());
    }

    @Test
    void pagamentoSeparaDocumentoFiscalDeEstorno() {
        Pagamento p = Pagamento.builder().id(9).honorarioId(1).valorPago(new BigDecimal("100.00"))
                .dataPagamento(LocalDate.of(2026, 10, 5)).metodo("DINHEIRO").build();
        DocumentoFiscalRef fr = new DocumentoFiscalRef(UUID.randomUUID(), "SIM-FR-2026/1");
        DocumentoFiscalRef nc = new DocumentoFiscalRef(UUID.randomUUID(), "SIM-NC-2026/1");

        PagamentoComDocumentoResponse comFr = PagamentoComDocumentoResponse.de(p, fr);
        assertEquals(fr, comFr.documentoFiscal());
        assertNull(comFr.estorno());

        PagamentoComDocumentoResponse estorno = PagamentoComDocumentoResponse.de(p, null, nc);
        assertNull(estorno.documentoFiscal());
        assertEquals(nc, estorno.estorno());
    }

    private static ProjetoNotaCredito projeto(DocumentoFiscal fr, RegimeIva regime, String taxaIva) {
        CalculoFiscal.ResultadoCalculo c = new CalculoFiscal.ResultadoCalculo(
                new BigDecimal("17391.30"), new BigDecimal("2608.70"), new BigDecimal(taxaIva),
                new BigDecimal("3478.26"), new BigDecimal("20.0000"),
                new BigDecimal("20000.00"), new BigDecimal("16521.74"));
        return new ProjetoNotaCredito(fr.getId(), fr.getNumeroFormatado(), TipoCredito.PARCIAL,
                MotivoNotaCredito.CORRECAO_VALOR, "Valor faturado a mais", LocalDate.of(2026, 10, 5),
                "Crédito sobre SIM-FR-2026/7", regime, c, new BigDecimal("120000.00"),
                new BigDecimal("0.00"), new BigDecimal("120000.00"), new BigDecimal("100000.00"));
    }

    @Test
    void preVisualizacaoNcMapeiaProjetoEUsaSnapshotDaFr() {
        DocumentoFiscal fr = doc(TipoDocumentoFiscal.FR, "SIM-FR-2026/7", "120000.00")
                .adquirenteNome("Cliente Snapshot").adquirenteNif("123456789")
                .adquirenteMorada("Rua Snapshot").adquirenteLocalidade("Praia")
                .emitenteMotivoIsencaoCodigo(null).emitenteMotivoIsencaoDescricao(null)
                .build();

        PreVisualizacaoNotaCreditoResponse r = PreVisualizacaoNotaCreditoResponse.de(fr,
                projeto(fr, RegimeIva.NORMAL, "15.0000"));

        assertEquals("NC", r.tipo());
        assertEquals("Nota de Crédito", r.tipoRotulo());
        assertEquals("SIMULADO", r.ambiente());
        assertEquals(fr.getId(), r.documentoOrigemId());
        assertEquals("SIM-FR-2026/7", r.documentoOrigemNumero());
        assertEquals("Cliente Snapshot", r.adquirenteNome());
        assertEquals("123456789", r.adquirenteNif());
        assertEquals("Rua Snapshot", r.adquirenteMorada());
        assertEquals("Praia", r.adquirenteLocalidade());
        assertEquals("Crédito sobre SIM-FR-2026/7", r.descricaoLinha());
        assertEquals("NORMAL", r.regimeIva());
        assertEquals(new BigDecimal("15.0000"), r.taxaIva());
        assertEquals(new BigDecimal("17391.30"), r.base());
        assertEquals(new BigDecimal("2608.70"), r.iva());
        assertEquals(new BigDecimal("20.0000"), r.taxaRetencao());
        assertEquals(new BigDecimal("3478.26"), r.retencao());
        assertEquals(new BigDecimal("20000.00"), r.total());
        assertEquals(new BigDecimal("16521.74"), r.liquido());
        assertEquals("PARCIAL", r.tipoCredito());
        assertEquals("CORRECAO_VALOR", r.motivoCodigo());
        assertEquals("Correção de valor", r.motivoRotulo());
        assertEquals("Valor faturado a mais", r.motivoTexto());
        assertEquals(new BigDecimal("120000.00"), r.totalOrigem());
        assertEquals(new BigDecimal("120000.00"), r.valorCreditavelAntes());
        assertEquals(new BigDecimal("100000.00"), r.valorCreditavelDepois());
        assertEquals(LocalDate.of(2026, 10, 5), r.dataEmissao());
        assertNull(r.motivoIsencaoCodigo());
    }

    @Test
    void preVisualizacaoNcIsentaNaoTemTaxaECopiaMotivoIsencaoDaFr() {
        DocumentoFiscal fr = doc(TipoDocumentoFiscal.FR, "SIM-FR-2026/8", "120000.00")
                .emitenteMotivoIsencaoCodigo("M1").emitenteMotivoIsencaoDescricao("Isento art. 9")
                .build();

        PreVisualizacaoNotaCreditoResponse r = PreVisualizacaoNotaCreditoResponse.de(fr,
                projeto(fr, RegimeIva.ISENTO, "0"));

        assertEquals("ISENTO", r.regimeIva());
        assertNull(r.taxaIva());
        assertEquals("M1", r.motivoIsencaoCodigo());
        assertEquals("Isento art. 9", r.motivoIsencaoDescricao());
    }

    @Test
    void pedidoDeNotaCreditoLeOsValoresConfirmadosDoJson() throws Exception {
        // WR-01 da revisão: os dois campos novos chegam do JSON; sem eles o pedido continua válido.
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        UUID chave = UUID.randomUUID();
        NotaCreditoRequest com = om.readValue("{\"tipo\":\"TOTAL\",\"valor\":null,\"motivoCodigo\":\"ANULACAO_TOTAL\","
                + "\"motivoTexto\":\"x\",\"chaveIdempotencia\":\"" + chave + "\",\"totalEsperado\":120000.00,"
                + "\"valorCreditavelEsperado\":120000}", NotaCreditoRequest.class);
        assertEquals(0, new BigDecimal("120000.00").compareTo(com.totalEsperado()));
        assertEquals(0, new BigDecimal("120000").compareTo(com.valorCreditavelEsperado()));
        assertEquals(chave, com.chaveIdempotencia());

        NotaCreditoRequest sem = om.readValue("{\"tipo\":\"PARCIAL\",\"valor\":10,\"motivoCodigo\":\"OUTRO\","
                + "\"motivoTexto\":\"x\"}", NotaCreditoRequest.class);
        assertNull(sem.totalEsperado());
        assertNull(sem.valorCreditavelEsperado());
        assertEquals(0, BigDecimal.TEN.compareTo(sem.valor()));
    }
}
