package com.lexcv.services.fiscal;

import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 135 (NCRD-01, NCRD-02, P-13): composição pura da Nota de Crédito. Vetores de referência ao
 * cêntimo sobre a FR de 120 000,00 (IVA 15, retenção 20), tetos, NC sobre NC, ISENTO, sem retenção
 * e propriedades de clamp: nenhuma coluna acumulada das NC passa a da FR, e o crédito do
 * remanescente fecha cada coluna exatamente.
 */
class ComposicaoNotaCreditoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 5);
    private static final UUID TENANT = UUID.randomUUID();
    private static final String TEXTO = "Valor faturado a mais";

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void igual(String esperado, BigDecimal real) {
        assertEquals(0, bd(esperado).compareTo(real), "esperado " + esperado + ", obtido " + real);
    }

    /** FR NORMAL a partir do CalculoFiscal (a mesma regra que a emissão da Phase 134). */
    private static DocumentoFiscal fr(String total, String taxaIva, String taxaRetencaoOuNull) {
        CalculoFiscal.ResultadoCalculo c = CalculoFiscal.calcular(bd(total), RegimeIva.NORMAL, bd(taxaIva),
                taxaRetencaoOuNull == null ? null : bd(taxaRetencaoOuNull));
        return documento(TipoDocumentoFiscal.FR, RegimeIva.NORMAL, c, null).build();
    }

    private static DocumentoFiscal.DocumentoFiscalBuilder documento(TipoDocumentoFiscal tipo, RegimeIva regime,
                                                                    CalculoFiscal.ResultadoCalculo c, UUID origem) {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .tipo(tipo)
                .numeroFormatado(tipo == TipoDocumentoFiscal.FR ? "SIM-FR-2026/7" : "SIM-NC-2026/1")
                .emitenteRegimeIva(regime)
                .taxaIva(c.taxaIva())
                .taxaRetencao(c.taxaRetencao())
                .totalBase(c.base())
                .totalIva(c.iva())
                .totalRetencao(c.retencao())
                .totalDocumento(c.total())
                .valorLiquido(c.liquidoRecebido())
                .documentoOrigemId(origem);
    }

    /** NC "emitida" a partir de um projeto (para encadear créditos). */
    private static DocumentoFiscal emitida(DocumentoFiscal origem, ProjetoNotaCredito p) {
        return documento(TipoDocumentoFiscal.NC, origem.getEmitenteRegimeIva(), p.calculo(), origem.getId())
                .motivoCodigo(p.motivo()).motivoTexto(p.motivoTexto()).build();
    }

    private static NotaCreditoRequest parcial(String valor) {
        return new NotaCreditoRequest("PARCIAL", bd(valor), "CORRECAO_VALOR", TEXTO, UUID.randomUUID());
    }

    private static NotaCreditoRequest total() {
        return new NotaCreditoRequest("TOTAL", null, "ANULACAO_TOTAL", TEXTO, UUID.randomUUID());
    }

    private static RecusaFiscalException recusa(HttpStatus status, String codigo, String campo, Runnable r) {
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, r::run);
        assertEquals(status, e.getStatus());
        assertEquals(codigo, e.getCodigo());
        assertEquals(campo, e.getCampo());
        return e;
    }

    private static void somaIgualAFr(DocumentoFiscal fr, List<DocumentoFiscal> ncs) {
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal ret = BigDecimal.ZERO;
        BigDecimal tot = BigDecimal.ZERO;
        for (DocumentoFiscal n : ncs) {
            base = base.add(n.getTotalBase());
            iva = iva.add(n.getTotalIva());
            ret = ret.add(n.getTotalRetencao());
            tot = tot.add(n.getTotalDocumento());
        }
        assertEquals(0, fr.getTotalBase().compareTo(base), "base acumulada");
        assertEquals(0, fr.getTotalIva().compareTo(iva), "IVA acumulado");
        assertEquals(0, fr.getTotalRetencao().compareTo(ret), "retenção acumulada");
        assertEquals(0, fr.getTotalDocumento().compareTo(tot), "total acumulado");
    }

    private static void nuncaExcede(DocumentoFiscal fr, List<DocumentoFiscal> ncs) {
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal ret = BigDecimal.ZERO;
        BigDecimal tot = BigDecimal.ZERO;
        for (DocumentoFiscal n : ncs) {
            assertTrue(n.getTotalBase().signum() >= 0 && n.getTotalIva().signum() >= 0
                    && n.getTotalRetencao().signum() >= 0, "montantes da NC são magnitudes positivas");
            assertEquals(0, n.getTotalBase().add(n.getTotalIva()).compareTo(n.getTotalDocumento()),
                    "base + IVA == total em cada NC");
            base = base.add(n.getTotalBase());
            iva = iva.add(n.getTotalIva());
            ret = ret.add(n.getTotalRetencao());
            tot = tot.add(n.getTotalDocumento());
        }
        assertTrue(base.compareTo(fr.getTotalBase()) <= 0, "base acumulada " + base + " > " + fr.getTotalBase());
        assertTrue(iva.compareTo(fr.getTotalIva()) <= 0, "IVA acumulado " + iva + " > " + fr.getTotalIva());
        assertTrue(ret.compareTo(fr.getTotalRetencao()) <= 0, "retenção acumulada " + ret + " > " + fr.getTotalRetencao());
        assertTrue(tot.compareTo(fr.getTotalDocumento()) <= 0, "total acumulado " + tot + " > " + fr.getTotalDocumento());
    }

    // ------------------------------------------------------------------ vetores de referência

    @Test
    void frDeReferencia() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        igual("104347.83", fr.getTotalBase());
        igual("15652.17", fr.getTotalIva());
        igual("20869.57", fr.getTotalRetencao());
    }

    @Test
    void parcialDe20000SemNcAnterior() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(), parcial("20000"), HOJE);

        igual("17391.30", p.calculo().base());
        igual("2608.70", p.calculo().iva());
        igual("3478.26", p.calculo().retencao());
        igual("20000.00", p.calculo().total());
        igual("16521.74", p.calculo().liquidoRecebido());
        igual("15", p.calculo().taxaIva());
        igual("20", p.calculo().taxaRetencao());
        igual("120000.00", p.totalOrigem());
        igual("0", p.totalCreditadoAntes());
        igual("120000.00", p.valorCreditavelAntes());
        igual("100000.00", p.valorCreditavelDepois());
        assertEquals(TipoCredito.PARCIAL, p.tipoCredito());
        assertEquals(MotivoNotaCredito.CORRECAO_VALOR, p.motivo());
        assertEquals(TEXTO, p.motivoTexto());
        assertEquals(RegimeIva.NORMAL, p.regime());
    }

    @Test
    void totalDepoisDaParcialFechaCadaColuna() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        DocumentoFiscal nc1 = emitida(fr, ComposicaoNotaCredito.compor(fr, List.of(), parcial("20000.00"), HOJE));

        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(nc1), total(), HOJE);
        igual("86956.53", p.calculo().base());
        igual("13043.47", p.calculo().iva());
        igual("17391.31", p.calculo().retencao());
        igual("100000.00", p.calculo().total());
        igual("82608.69", p.calculo().liquidoRecebido());
        igual("20000.00", p.totalCreditadoAntes());
        igual("100000.00", p.valorCreditavelAntes());
        igual("0.00", p.valorCreditavelDepois());
        assertEquals(TipoCredito.TOTAL, p.tipoCredito());
        assertEquals(MotivoNotaCredito.ANULACAO_TOTAL, p.motivo());

        somaIgualAFr(fr, List.of(nc1, emitida(fr, p)));
    }

    @Test
    void totalSemAnterioresCreditaExatamenteAFr() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(), total(), HOJE);
        igual("104347.83", p.calculo().base());
        igual("15652.17", p.calculo().iva());
        igual("20869.57", p.calculo().retencao());
        igual("120000.00", p.calculo().total());
        igual("99130.43", p.calculo().liquidoRecebido());
        igual("15", p.calculo().taxaIva());
        igual("20", p.calculo().taxaRetencao());
        igual("0.00", p.valorCreditavelDepois());
    }

    @Test
    void parcialIgualAoRemanescenteEOMesmoQueTotal() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        DocumentoFiscal nc1 = emitida(fr, ComposicaoNotaCredito.compor(fr, List.of(), parcial("20000.00"), HOJE));

        ProjetoNotaCredito viaParcial = ComposicaoNotaCredito.compor(fr, List.of(nc1), parcial("100000.00"), HOJE);
        ProjetoNotaCredito viaTotal = ComposicaoNotaCredito.compor(fr, List.of(nc1), total(), HOJE);
        assertEquals(viaTotal.calculo(), viaParcial.calculo());
        assertEquals(TipoCredito.PARCIAL, viaParcial.tipoCredito());
        igual("86956.53", viaParcial.calculo().base());
    }

    // ------------------------------------------------------------------ tetos e recusas

    @Test
    void parcialAcimaDoRemanescenteERecusada() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        DocumentoFiscal nc1 = emitida(fr, ComposicaoNotaCredito.compor(fr, List.of(), parcial("20000.00"), HOJE));

        RecusaFiscalException e = recusa(HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL", "valor",
                () -> ComposicaoNotaCredito.compor(fr, List.of(nc1), parcial("100000.01"), HOJE));
        assertEquals("O valor indicado excede o que ainda pode ser creditado nesta fatura-recibo. "
                + "Reduza o valor ou escolha crédito total.", e.getMessage());
    }

    @Test
    void nadaPorCreditarERecusadoComMensagemPropria() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        DocumentoFiscal nc1 = emitida(fr, ComposicaoNotaCredito.compor(fr, List.of(), total(), HOJE));

        RecusaFiscalException e = recusa(HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL", "valor",
                () -> ComposicaoNotaCredito.compor(fr, List.of(nc1), total(), HOJE));
        assertEquals("Esta fatura-recibo já foi totalmente creditada.", e.getMessage());
        recusa(HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL", "valor",
                () -> ComposicaoNotaCredito.compor(fr, List.of(nc1), parcial("0.01"), HOJE));
    }

    @Test
    void ncSobreNcERecusadaAntesDeQualquerValidacao() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        DocumentoFiscal nc = emitida(fr, ComposicaoNotaCredito.compor(fr, List.of(), parcial("10.00"), HOJE));
        // Pedido inválido em tudo: o primeiro erro tem de ser NC_SOBRE_NC.
        NotaCreditoRequest invalido = new NotaCreditoRequest(null, null, null, null, null);
        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "NC_SOBRE_NC", null,
                () -> ComposicaoNotaCredito.compor(nc, List.of(), invalido, HOJE));
    }

    @Test
    void ordemDasValidacoesDoPedido() {
        DocumentoFiscal fr = fr("1000.00", "15", null);
        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "TIPO_CREDITO_INVALIDO", "tipo", () -> ComposicaoNotaCredito
                .compor(fr, List.of(), new NotaCreditoRequest("X", null, null, null, null), HOJE));
        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "MOTIVO_NC_INVALIDO", "motivoCodigo", () -> ComposicaoNotaCredito
                .compor(fr, List.of(), new NotaCreditoRequest("TOTAL", null, "X", null, null), HOJE));
        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "MOTIVO_NC_OBRIGATORIO", "motivoTexto", () -> ComposicaoNotaCredito
                .compor(fr, List.of(), new NotaCreditoRequest("TOTAL", bd("1"), "OUTRO", " ", null), HOJE));
        // TOTAL não aceita valor; PARCIAL exige-o.
        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "VALOR_CREDITO_INVALIDO", "valor", () -> ComposicaoNotaCredito
                .compor(fr, List.of(), new NotaCreditoRequest("TOTAL", bd("10"), "OUTRO", TEXTO, null), HOJE));
        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "VALOR_CREDITO_INVALIDO", "valor", () -> ComposicaoNotaCredito
                .compor(fr, List.of(), new NotaCreditoRequest("PARCIAL", null, "OUTRO", TEXTO, null), HOJE));
        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "VALOR_CREDITO_INVALIDO", "valor", () -> ComposicaoNotaCredito
                .compor(fr, List.of(), new NotaCreditoRequest("PARCIAL", bd("1.001"), "OUTRO", TEXTO, null), HOJE));
    }

    // ------------------------------------------------------------------ regimes

    @Test
    void frIsentaCreditaSemIva() {
        CalculoFiscal.ResultadoCalculo c = CalculoFiscal.calcular(bd("2000.00"), RegimeIva.ISENTO, null, null);
        DocumentoFiscal fr = documento(TipoDocumentoFiscal.FR, RegimeIva.ISENTO, c, null)
                .emitenteMotivoIsencaoCodigo("01").build();

        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(), parcial("500.00"), HOJE);
        igual("500.00", p.calculo().base());
        igual("0.00", p.calculo().iva());
        igual("0", p.calculo().taxaIva());
        igual("500.00", p.calculo().total());
        assertEquals(RegimeIva.ISENTO, p.regime());
        igual("1500.00", p.valorCreditavelDepois());
    }

    @Test
    void frSemRetencaoNuncaRetemNaNc() {
        DocumentoFiscal fr = fr("1150.00", "15", null);
        ProjetoNotaCredito p1 = ComposicaoNotaCredito.compor(fr, List.of(), parcial("115.00"), HOJE);
        igual("0.00", p1.calculo().retencao());
        assertNull(p1.calculo().taxaRetencao());
        igual("115.00", p1.calculo().liquidoRecebido());

        ProjetoNotaCredito p2 = ComposicaoNotaCredito.compor(fr, List.of(emitida(fr, p1)), total(), HOJE);
        igual("0.00", p2.calculo().retencao());
        assertNull(p2.calculo().taxaRetencao());
        igual("1035.00", p2.calculo().total());
    }

    @Test
    void usaATaxaDaFrOriginalENaoUmaTaxaVigente() {
        // FR emitida a 12%: a NC usa 12 (snapshot), seja qual for a taxa de hoje.
        DocumentoFiscal fr = fr("1120.00", "12", null);
        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(), parcial("112.00"), HOJE);
        igual("100.00", p.calculo().base());
        igual("12.00", p.calculo().iva());
        igual("12", p.calculo().taxaIva());
    }

    @Test
    void dataEDescricaoDaLinha() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(), total(), HOJE);
        assertEquals(HOJE, p.dataEmissao());
        assertEquals("Crédito sobre a fatura-recibo SIM-FR-2026/7", p.descricaoLinha());
        assertEquals(fr.getId(), p.documentoOrigemId());
        assertEquals("SIM-FR-2026/7", p.documentoOrigemNumero());
    }

    @Test
    void ignoraDocumentosQueNaoSaoNotasDeCredito() {
        DocumentoFiscal fr = fr("120000.00", "15", "20");
        // Uma FR passada por engano na lista não reduz o remanescente.
        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(fr("50.00", "15", null)), total(), HOJE);
        igual("120000.00", p.calculo().total());
    }

    // ------------------------------------------------------------------ clamps (P-13)

    @Test
    void cemCreditosDeUmCentimoFechamAFrSemExcederNenhumaColuna() {
        // FR 1,00 a 15%: base 0,87 / IVA 0,13. Cada 0,01 calculado dá base 0,01 e IVA 0,00, por isso
        // sem clamp a base acumulada passaria 0,87; o clamp desvia o excedente para o IVA.
        DocumentoFiscal fr = fr("1.00", "15", "20");
        List<DocumentoFiscal> ncs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, ncs, parcial("0.01"), HOJE);
            ncs.add(emitida(fr, p));
            nuncaExcede(fr, ncs);
        }
        somaIgualAFr(fr, ncs);
        recusa(HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL", "valor",
                () -> ComposicaoNotaCredito.compor(fr, ncs, parcial("0.01"), HOJE));
    }

    @Test
    void retencaoArredondadaParaCimaNuncaPassaADaFr() {
        // FR 1,00, retenção 50%: retenção 0,44. Cada 0,03 dá retenção round(0,03*0,5)=0,02: 29 créditos
        // somariam 0,58 sem clamp.
        DocumentoFiscal fr = fr("1.00", "15", "50");
        igual("0.44", fr.getTotalRetencao());
        List<DocumentoFiscal> ncs = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            ncs.add(emitida(fr, ComposicaoNotaCredito.compor(fr, ncs, parcial("0.03"), HOJE)));
            nuncaExcede(fr, ncs);
        }
        ncs.add(emitida(fr, ComposicaoNotaCredito.compor(fr, ncs, total(), HOJE)));
        nuncaExcede(fr, ncs);
        somaIgualAFr(fr, ncs);
    }

    @Test
    void retencaoDaParcialEhCalculadaSobreABaseLimitada() {
        // WR-04 da revisão. FR 1,00 a 15%, retenção 50%: base 0,87 / IVA 0,13 / retenção 0,44. Uma NC
        // anterior (artificial) consumiu 0,80 de base e nada de IVA nem de retenção: remanescentes
        // base 0,07 / IVA 0,13 / retenção 0,44. Uma parcial de 0,10 calcula base 0,09, limitada a
        // 0,07; a retenção tem de ser round(0,07 * 50%) = 0,04, não round(0,09 * 50%) = 0,05.
        DocumentoFiscal fr = fr("1.00", "15", "50");
        igual("0.87", fr.getTotalBase());
        igual("0.44", fr.getTotalRetencao());
        CalculoFiscal.ResultadoCalculo anterior = new CalculoFiscal.ResultadoCalculo(bd("0.80"), bd("0.00"),
                bd("15"), bd("0.00"), bd("50"), bd("0.80"), bd("0.80"));
        DocumentoFiscal nc = documento(TipoDocumentoFiscal.NC, RegimeIva.NORMAL, anterior, fr.getId()).build();

        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(nc), parcial("0.10"), HOJE);

        igual("0.07", p.calculo().base());
        igual("0.03", p.calculo().iva());
        igual("0.04", p.calculo().retencao());
        igual("0.06", p.calculo().liquidoRecebido());
        igual("0.10", p.calculo().total());
    }

    @Test
    void parcialSemRetencaoNaFrTemRetencaoZero() {
        DocumentoFiscal fr = fr("1000.00", "15", null);

        ProjetoNotaCredito p = ComposicaoNotaCredito.compor(fr, List.of(), parcial("100.00"), HOJE);

        igual("0.00", p.calculo().retencao());
        igual("100.00", p.calculo().liquidoRecebido());
    }

    @Test
    void propriedadeParciaisAleatoriasMaisTotalFechamSempreAFr() {
        Random r = new Random(135L);
        String[] taxas = {"15", "8", "12.5"};
        String[] retencoes = {null, "20", "15", "33.33"};
        for (int caso = 0; caso < 300; caso++) {
            BigDecimal totalFr = BigDecimal.valueOf(1 + r.nextInt(5_000_000), 2);
            DocumentoFiscal fr = fr(totalFr.toPlainString(), taxas[r.nextInt(taxas.length)],
                    retencoes[r.nextInt(retencoes.length)]);
            List<DocumentoFiscal> ncs = new ArrayList<>();
            int partes = 1 + r.nextInt(12);
            for (int k = 0; k < partes; k++) {
                BigDecimal restante = fr.getTotalDocumento();
                for (DocumentoFiscal n : ncs) {
                    restante = restante.subtract(n.getTotalDocumento());
                }
                if (restante.compareTo(bd("0.01")) <= 0) {
                    break;
                }
                long maxCent = restante.movePointRight(2).longValueExact() - 1;
                BigDecimal valor = BigDecimal.valueOf(1 + (long) (r.nextDouble() * maxCent), 2);
                ncs.add(emitida(fr, ComposicaoNotaCredito.compor(fr, ncs, parcial(valor.toPlainString()), HOJE)));
                nuncaExcede(fr, ncs);
            }
            ncs.add(emitida(fr, ComposicaoNotaCredito.compor(fr, ncs, total(), HOJE)));
            nuncaExcede(fr, ncs);
            somaIgualAFr(fr, ncs);
        }
    }
}
