package com.lexcv.services.fiscal;

import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.dtos.PreVisualizacaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.Cliente;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.Honorario;
import com.lexcv.models.MetodoPagamento;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.Pagamento;
import com.lexcv.models.Processo;
import com.lexcv.models.RegimeIva;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 134 (EMIS-02..06, D-01, D-04, D-05, D-06, D-12, D-13): composição pura da Fatura-Recibo. */
class ComposicaoFaturaReciboTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 4);
    private static final BigDecimal IVA = new BigDecimal("15");
    private static final UUID TENANT = UUID.randomUUID();

    private static ConfiguracaoFiscal.ConfiguracaoFiscalBuilder cfgNormal() {
        return ConfiguracaoFiscal.builder()
                .tenantId(TENANT)
                .nif("123456789")
                .firma("Silva & Associados")
                .morada("Av. Amílcar Cabral 10")
                .localidade("Praia")
                .emailContacto("geral@silva.cv")
                .telefoneContacto("+238 260 00 00")
                .regimeIva(RegimeIva.NORMAL)
                .ativa(true);
    }

    private static Cliente.ClienteBuilder cliente() {
        return Cliente.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .nif("234567891")
                .nome("  Maria Lopes  ")
                .morada("  Rua da Praia 5  ")
                .localidade("  Mindelo ");
    }

    private static Processo processo(UUID clienteId) {
        return Processo.builder().id(UUID.randomUUID()).tenantId(TENANT).clienteId(clienteId)
                .numeroProcesso("PROC-2026/12").build();
    }

    private static PagamentoRequest req(String valor, LocalDate data, String metodo, String retencao) {
        return new PagamentoRequest(7, valor == null ? null : new BigDecimal(valor), data, metodo,
                retencao == null ? null : new BigDecimal(retencao), UUID.randomUUID());
    }

    private static void assertRecusa(RecusaFiscalException e, HttpStatus status, String codigo, String campo) {
        assertEquals(status, e.getStatus());
        assertEquals(codigo, e.getCodigo());
        assertEquals(campo, e.getCampo());
    }

    @Test
    void normalComRetencaoReproduzOsValoresDeReferencia() {
        Cliente c = cliente().build();
        Processo p = processo(c.getId());
        ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(cfgNormal().build(), c, p, 7,
                req("120000", null, "TRANSFERENCIA", "20"), HOJE, IVA);

        assertEquals(new BigDecimal("104347.83"), projeto.calculo().base());
        assertEquals(new BigDecimal("15652.17"), projeto.calculo().iva());
        assertEquals(new BigDecimal("20869.57"), projeto.calculo().retencao());
        assertEquals(new BigDecimal("99130.43"), projeto.calculo().liquidoRecebido());
        assertEquals(new BigDecimal("120000.00"), projeto.calculo().total());
        assertEquals(HOJE, projeto.dataEmissao());
        assertEquals(MetodoPagamento.TRANSFERENCIA, projeto.metodo());
        assertEquals("30", projeto.metodo().codigoMeioPagamento());
        assertEquals("Honorários por serviços jurídicos — Processo n.º PROC-2026/12", projeto.descricaoLinha());
        assertEquals(RegimeIva.NORMAL, projeto.regime());
        assertNull(projeto.motivoIsencao());
        assertEquals(c.getId(), projeto.clienteId());
        assertEquals(p.getId(), projeto.processoId());
        assertEquals(7, projeto.honorarioId());
        assertEquals("234567891", projeto.adquirenteNif());
        assertEquals("Maria Lopes", projeto.adquirenteNome());
        assertEquals("Rua da Praia 5", projeto.adquirenteMorada());
        assertEquals("Mindelo", projeto.adquirenteLocalidade());
        assertEquals("123456789", projeto.emitenteNif());
        assertEquals("Silva & Associados", projeto.emitenteFirma());
        assertEquals("Av. Amílcar Cabral 10", projeto.emitenteMorada());
        assertEquals("Praia", projeto.emitenteLocalidade());
    }

    @Test
    void dataIgualAHojeEAceite() {
        Cliente c = cliente().build();
        ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(cfgNormal().build(), c, processo(c.getId()), 7,
                req("100", HOJE, "dinheiro", null), HOJE, IVA);
        assertEquals(HOJE, projeto.dataEmissao());
        assertEquals(MetodoPagamento.DINHEIRO, projeto.metodo());
        assertEquals(new BigDecimal("0.00"), projeto.calculo().retencao());
        assertNull(projeto.calculo().taxaRetencao());
    }

    @Test
    void isentoUsaOMotivoEIgnoraATaxa() {
        ConfiguracaoFiscal cfg = cfgNormal().regimeIva(RegimeIva.ISENTO).motivoIsencaoCodigo("1").build();
        Cliente c = cliente().build();
        ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(cfg, c, processo(c.getId()), 7,
                req("5000", null, "CHEQUE", null), HOJE, null);

        assertEquals(new BigDecimal("5000.00"), projeto.calculo().base());
        assertEquals(new BigDecimal("0.00"), projeto.calculo().iva());
        assertEquals(RegimeIva.ISENTO, projeto.regime());
        assertEquals(MotivoIsencaoIva.M1, projeto.motivoIsencao());
        assertEquals("1", projeto.motivoIsencao().codigo());
        assertFalse(projeto.motivoIsencao().mencao().isBlank());

        // a taxa passada é ignorada em ISENTO
        ProjetoFaturaRecibo comTaxa = ComposicaoFaturaRecibo.compor(cfg, c, processo(c.getId()), 7,
                req("5000", null, "CHEQUE", null), HOJE, IVA);
        assertEquals(new BigDecimal("0.00"), comTaxa.calculo().iva());
    }

    @Test
    void configuracaoIncompletaRecusadaAntesDosCamposDoPedido() {
        ConfiguracaoFiscal incompleta = cfgNormal().localidade(null).build();
        Cliente c = cliente().nif(null).build();
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, () ->
                ComposicaoFaturaRecibo.compor(incompleta, c, processo(c.getId()), 7,
                        req("-1", HOJE.minusDays(1), "X", "500"), HOJE, IVA));
        assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "CONFIGURACAO_FISCAL_INCOMPLETA", null);
        assertEquals("Os dados fiscais do escritório estão incompletos. Complete-os em Definições → Faturação.",
                e.getMessage());
    }

    @Test
    void ordemDeValidacaoPrimeiraFalhaGanha() {
        ConfiguracaoFiscal cfg = cfgNormal().build();
        Cliente semNada = cliente().nif("0").nome("x").morada(" ").build();
        Processo p = processo(semNada.getId());

        assertRecusa(assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(cfg, semNada, p, 7,
                        req("0", HOJE.minusDays(1), null, "0"), HOJE, IVA)),
                HttpStatus.UNPROCESSABLE_ENTITY, "VALOR_PAGO_INVALIDO", "valorPago");
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(cfg, semNada, p, 7,
                        req("10", HOJE.minusDays(1), null, "0"), HOJE, IVA)),
                HttpStatus.UNPROCESSABLE_ENTITY, "DATA_PAGAMENTO_RETROATIVA", "dataPagamento");
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(cfg, semNada, p, 7,
                        req("10", null, null, "0"), HOJE, IVA)),
                HttpStatus.UNPROCESSABLE_ENTITY, "METODO_PAGAMENTO_INVALIDO", "metodo");
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(cfg, semNada, p, 7,
                        req("10", null, "CARTAO", "0"), HOJE, IVA)),
                HttpStatus.UNPROCESSABLE_ENTITY, "RETENCAO_INVALIDA", "retencaoPercentagem");
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(cfg, semNada, p, 7,
                        req("10", null, "CARTAO", null), HOJE, IVA)),
                HttpStatus.UNPROCESSABLE_ENTITY, "ADQUIRENTE_INCOMPLETO", "nif");
        Cliente soNif = cliente().nome("x").morada(" ").build();
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(cfg, soNif, p, 7,
                        req("10", null, "CARTAO", null), HOJE, IVA)),
                HttpStatus.UNPROCESSABLE_ENTITY, "ADQUIRENTE_INCOMPLETO", "nome");
        Cliente semMorada = cliente().morada(" ").build();
        assertRecusa(assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(cfg, semMorada, p, 7,
                        req("10", null, "CARTAO", null), HOJE, IVA)),
                HttpStatus.UNPROCESSABLE_ENTITY, "ADQUIRENTE_INCOMPLETO", "morada");
    }

    @Test
    void normalSemTaxaEErroDoChamador() {
        Cliente c = cliente().build();
        assertThrows(IllegalArgumentException.class, () -> ComposicaoFaturaRecibo.compor(cfgNormal().build(), c,
                processo(c.getId()), 7, req("100", null, "DINHEIRO", null), HOJE, null));
    }

    @Test
    void composicaoNuncaRecebeOHonorario() {
        Method compor = Arrays.stream(ComposicaoFaturaRecibo.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("compor")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(compor.getParameterTypes()).noneMatch(t -> t.equals(Honorario.class)),
                "compor não pode receber a entidade Honorario (descrição livre sob sigilo)");
        assertTrue(Arrays.asList(compor.getParameterTypes()).contains(Integer.class));
    }

    @Test
    void processoSemNumeroUsaTextoFixo() {
        Cliente c = cliente().build();
        Processo semNumero = Processo.builder().id(UUID.randomUUID()).tenantId(TENANT).clienteId(c.getId()).build();
        ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(cfgNormal().build(), c, semNumero, 7,
                req("100", null, "DINHEIRO", null), HOJE, IVA);
        assertEquals(TextoDocumentoFiscal.DESCRICAO_HONORARIOS, projeto.descricaoLinha());
    }

    @Test
    void localidadeComMaisDe100CaracteresRecusada422AntesDoInsert() {
        // WR-02 da revisão: a coluna adquirente_localidade é VARCHAR(100).
        Cliente c = cliente().localidade("L".repeat(101)).build();
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class, () -> ComposicaoFaturaRecibo.compor(
                cfgNormal().build(), c, processo(c.getId()), 7, req("100", null, "DINHEIRO", null), HOJE, IVA));
        assertRecusa(e, HttpStatus.UNPROCESSABLE_ENTITY, "ADQUIRENTE_INCOMPLETO", "localidade");

        Cliente limite = cliente().localidade(" " + "L".repeat(100) + " ").build();
        assertEquals("L".repeat(100), ComposicaoFaturaRecibo.compor(cfgNormal().build(), limite,
                processo(limite.getId()), 7, req("100", null, "DINHEIRO", null), HOJE, IVA).adquirenteLocalidade());
    }

    @Test
    void localidadeEmBrancoFicaNula() {
        Cliente c = cliente().localidade("   ").build();
        assertNull(ComposicaoFaturaRecibo.compor(cfgNormal().build(), c, processo(c.getId()), 7,
                req("100", null, "DINHEIRO", null), HOJE, IVA).adquirenteLocalidade());
        Cliente semLocalidade = cliente().localidade(null).build();
        assertNull(ComposicaoFaturaRecibo.compor(cfgNormal().build(), semLocalidade, processo(c.getId()), 7,
                req("100", null, "DINHEIRO", null), HOJE, IVA).adquirenteLocalidade());
    }

    @Test
    void respostaDePreVisualizacaoCopiaOProjeto() {
        Cliente c = cliente().build();
        ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(cfgNormal().build(), c, processo(c.getId()), 7,
                req("120000", null, "TRANSFERENCIA", "20"), HOJE, IVA);
        PreVisualizacaoFaturaResponse r = PreVisualizacaoFaturaResponse.de(projeto);
        assertEquals("FR", r.tipo());
        assertEquals("Fatura-Recibo", r.tipoRotulo());
        assertEquals("SIMULADO", r.ambiente());
        assertEquals(c.getId(), r.clienteId());
        assertEquals("Maria Lopes", r.adquirenteNome());
        assertEquals(new BigDecimal("104347.83"), r.base());
        assertEquals(new BigDecimal("15652.17"), r.iva());
        assertEquals(new BigDecimal("20869.57"), r.retencao());
        assertEquals(new BigDecimal("99130.43"), r.liquidoRecebido());
        assertEquals(new BigDecimal("120000.00"), r.total());
        assertEquals("TRANSFERENCIA", r.metodo());
        assertEquals(MetodoPagamento.TRANSFERENCIA.rotulo(), r.metodoRotulo());
        assertEquals(RegimeIva.NORMAL, r.regimeIva());
        assertNull(r.motivoIsencaoCodigo());
        assertEquals(HOJE, r.dataEmissao());
    }

    @Test
    void pedidoLegadoNuncaCopiaId() {
        PagamentoRequest r = new PagamentoRequest(9, new BigDecimal("50.00"), HOJE, "DINHEIRO", null, null);
        Pagamento p = r.paraPagamentoLegado();
        assertNull(p.getId());
        assertEquals(9, p.getHonorarioId());
        assertEquals(new BigDecimal("50.00"), p.getValorPago());
        assertEquals(HOJE, p.getDataPagamento());
        assertEquals("DINHEIRO", p.getMetodo());
    }
}
