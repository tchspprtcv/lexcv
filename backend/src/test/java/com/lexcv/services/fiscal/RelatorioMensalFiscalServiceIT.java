package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.fiscal.csv.CsvFiscal;
import com.lexcv.models.RegimeIva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 137 (RELF-01; T-137-75, T-137-76, T-137-78): provas em PostgreSQL real do CSV mensal do
 * contabilista -- conteúdo e formato Excel-PT, NC negativas, linha de totais com sinal, guarda de
 * fórmulas só no Cliente/Motivo de isenção, isolamento por tenant e pelo mês, e um evento de
 * auditoria por exportação.
 *
 * <p>Andaime de {@link NotaCreditoServiceIT}: os documentos são emitidos pelos serviços reais
 * ({@link PagamentoFaturadoService}, {@link NotaCreditoService}) com relógio fixo em setembro de
 * 2026; a comunicação aceite e o XML da FR são acrescentados por JDBC.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({RelatorioMensalFiscalService.class, NotaCreditoService.class, PagamentoFaturadoService.class,
        PreVisualizacaoFaturaService.class, NumeracaoService.class, ParametroFiscalService.class,
        AuditoriaFiscalService.class, RelatorioMensalFiscalServiceIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RelatorioMensalFiscalServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant AGORA = Instant.parse("2026-09-15T13:00:00Z");
    static final YearMonth SETEMBRO = YearMonth.of(2026, 9);
    static final String CABECALHO = "Data;Tipo;Série;Número;IUD;Cliente;NIF cliente;Base;IVA;Motivo de isenção;"
            + "Retenção;Total;Documento de origem;Estado da comunicação";
    private static final BigDecimal VALOR_FR = new BigDecimal("120000.00");
    private static final AtomicLong SEQUENCIA = new AtomicLong(900_000);

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private RelatorioMensalFiscalService service;

    @Autowired
    private PagamentoFaturadoService pagamentoFaturado;

    @Autowired
    private NotaCreditoService notaCredito;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private FixturaEmissaoFiscal fixtura;

    @BeforeEach
    void preparar() {
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();
    }

    // ------------------------------------------------------------------ apoio

    private static UserPrincipal autor(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Contabilista", "ana@example.cv",
                Set.of(), Set.of(), Set.of());
    }

    /** Emite uma FR de 120 000 com retenção 20 % para um cliente novo; devolve o id da FR. */
    private UUID emitirFr(UUID tenant, String nomeCliente) {
        UUID cliente = fixtura.criarCliente(tenant, "234567891", nomeCliente, "Rua da Praia 5");
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-2026/" + SEQUENCIA.incrementAndGet());
        Integer honorario = fixtura.criarHonorario(processo, VALOR_FR, "Defesa no caso X");
        return pagamentoFaturado.registar(tenant, autor(tenant), new PagamentoRequest(honorario, VALOR_FR, null,
                "TRANSFERENCIA", new BigDecimal("20"), UUID.randomUUID())).resposta().documentoFiscal().id();
    }

    private UUID emitirNcParcial(UUID tenant, UUID frId, String valor) {
        return notaCredito.emitir(tenant, autor(tenant), frId, new NotaCreditoRequest("PARCIAL", new BigDecimal(valor),
                "CORRECAO_VALOR", "Desconto acordado", UUID.randomUUID())).resposta().id();
    }

    /** A comunicação da FR passa a ACEITE_SIMULADO e ganha o XML com um IUD conhecido. */
    private String aceitarComXml(UUID tenant, UUID documentoId) {
        String iud = "CV3260915512345679" + String.format("%027d", SEQUENCIA.incrementAndGet());
        jdbc.update("INSERT INTO t_documento_fiscal_xml (id, tenant_id, documento_fiscal_id, iud, ambiente, "
                        + "repositorio_codigo, led_codigo, versao_formato, xml, xml_sha256, gerado_em) "
                        + "VALUES (?, ?, ?, ?, 'SIMULADO', 3, 99999, '2024-05-27', '<Dfe/>', ?, ?)",
                UUID.randomUUID(), tenant, documentoId, iud, "a".repeat(64), Timestamp.from(AGORA));
        jdbc.update("UPDATE t_comunicacao_fiscal SET estado = 'ACEITE_SIMULADO', concluido_em = ? "
                + "WHERE documento_fiscal_id = ?", Timestamp.from(AGORA), documentoId);
        return iud;
    }

    /** FR inserida por JDBC numa data à escolha (fora do relógio fixo). */
    private void inserirFrNaData(UUID tenant, String data, String nomeCliente) {
        long n = SEQUENCIA.incrementAndGet();
        String serie = "SIM-FR-2026";
        jdbc.update("INSERT INTO t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, serie_codigo, ano, "
                        + "numero, numero_formatado, data_emissao, emitido_em, emitente_nif, emitente_firma, "
                        + "emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, adquirente_morada, "
                        + "cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia) "
                        + "VALUES (?, ?, 'FR', 'SIMULADO', ?, ?, 2026, ?, ?, ?, ?, '512345679', 'Firma', 'Morada', "
                        + "'NORMAL', '234567891', ?, 'Morada cliente', ?, ?, 1, ?, 'DINHEIRO', '10', 'CVE', "
                        + "15, 100, 15, 0, 115, 115, ?)",
                UUID.randomUUID(), tenant, UUID.randomUUID(), serie, n, serie + "/" + n,
                java.sql.Date.valueOf(data), Timestamp.from(AGORA), nomeCliente, UUID.randomUUID(),
                UUID.randomUUID(), (int) -n, UUID.randomUUID());
    }

    private Map<String, Object> documento(UUID id) {
        return jdbc.queryForMap("SELECT * FROM t_documento_fiscal WHERE id = ?", id);
    }

    private static String valor(Object bd) {
        return CsvFiscal.valor((BigDecimal) bd);
    }

    private static String negativo(Object bd) {
        return CsvFiscal.valor(((BigDecimal) bd).negate());
    }

    /** Bytes -> linhas (sem o BOM; o CRLF final não gera linha vazia). */
    private static List<String> linhas(byte[] conteudo) {
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(conteudo, 3));
        String texto = new String(conteudo, 3, conteudo.length - 3, StandardCharsets.UTF_8);
        assertTrue(texto.endsWith("\r\n"), "o ficheiro termina com CRLF");
        assertFalse(texto.replace("\r\n", "").contains("\n"), "só CRLF como fim de linha");
        return List.of(texto.split("\r\n"));
    }

    private static String[] celulas(String linha) {
        return linha.split(";", -1);
    }

    // ------------------------------------------------------------------ testes

    @Test
    void frENcDoMesComCabecalhoSinaisTotaisEEstados() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID fr = emitirFr(tenant, "Maria Lopes");
        UUID nc = emitirNcParcial(tenant, fr, "30000.00");
        String iud = aceitarComXml(tenant, fr);
        Map<String, Object> f = documento(fr);
        Map<String, Object> n = documento(nc);

        RelatorioMensalFiscalService.CsvMensal csv = service.exportar(tenant, autor(tenant), SETEMBRO);

        assertEquals("documentos-fiscais-simulacao-2026-09.csv", csv.nomeFicheiro());
        assertEquals(2, csv.numeroDocumentos());
        List<String> linhas = linhas(csv.conteudo());
        assertEquals(4, linhas.size(), linhas.toString());
        assertEquals(CABECALHO, linhas.get(0));

        String[] linhaFr = celulas(linhas.get(1));
        assertEquals(14, linhaFr.length);
        assertEquals("15/09/2026", linhaFr[0]);
        assertEquals("FR", linhaFr[1]);
        assertEquals(f.get("serie_codigo"), linhaFr[2]);
        assertEquals(f.get("numero_formatado"), linhaFr[3]);
        assertEquals(iud, linhaFr[4]);
        assertEquals("Maria Lopes", linhaFr[5]);
        assertEquals("234567891", linhaFr[6]);
        assertEquals(valor(f.get("total_base")), linhaFr[7]);
        assertEquals(valor(f.get("total_iva")), linhaFr[8]);
        assertEquals("", linhaFr[9]);
        assertEquals(valor(f.get("total_retencao")), linhaFr[10]);
        assertEquals(valor(f.get("total_documento")), linhaFr[11]);
        assertEquals("", linhaFr[12]);
        assertEquals("Aceite (simulação)", linhaFr[13]);
        assertTrue(linhaFr[7].matches("\\d+,\\d{2}"), linhaFr[7]);
        assertTrue(((BigDecimal) f.get("total_retencao")).signum() > 0, "a FR tem retenção");

        String[] linhaNc = celulas(linhas.get(2));
        assertEquals(14, linhaNc.length);
        assertEquals("NC", linhaNc[1]);
        assertEquals(n.get("numero_formatado"), linhaNc[3]);
        assertEquals("", linhaNc[4]);
        assertEquals(negativo(n.get("total_base")), linhaNc[7]);
        assertEquals(negativo(n.get("total_iva")), linhaNc[8]);
        assertEquals(negativo(n.get("total_retencao")), linhaNc[10]);
        assertEquals(negativo(n.get("total_documento")), linhaNc[11]);
        assertTrue(linhaNc[7].startsWith("-") && linhaNc[11].startsWith("-"), linhas.get(2));
        assertFalse(linhas.get(2).contains("'"), "valores negativos sem apóstrofo: " + linhas.get(2));
        assertEquals(f.get("numero_formatado"), linhaNc[12]);
        assertEquals("Pendente", linhaNc[13]);

        String[] totais = celulas(linhas.get(3));
        assertEquals(14, totais.length);
        assertEquals("Totais", totais[0]);
        assertEquals(CsvFiscal.valor(((BigDecimal) f.get("total_base")).subtract((BigDecimal) n.get("total_base"))),
                totais[7]);
        assertEquals(CsvFiscal.valor(((BigDecimal) f.get("total_iva")).subtract((BigDecimal) n.get("total_iva"))),
                totais[8]);
        assertEquals(CsvFiscal.valor(((BigDecimal) f.get("total_retencao"))
                .subtract((BigDecimal) n.get("total_retencao"))), totais[10]);
        assertEquals(CsvFiscal.valor(((BigDecimal) f.get("total_documento"))
                .subtract((BigDecimal) n.get("total_documento"))), totais[11]);
        for (int i : new int[]{1, 2, 3, 4, 5, 6, 9, 12, 13}) {
            assertEquals("", totais[i], "célula " + i + " da linha Totais");
        }
    }

    @Test
    void clienteComFormulaEGuardadoEONifNao() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        emitirFr(tenant, "=HYPERLINK(\"x\")");

        List<String> linhas = linhas(service.exportar(tenant, autor(tenant), SETEMBRO).conteudo());

        String[] c = celulas(linhas.get(1));
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", c[5]);
        assertEquals("234567891", c[6]);
        assertFalse(c[3].startsWith("'") || c[7].startsWith("'") || c[11].startsWith("'"), linhas.get(1));
    }

    @Test
    void ivaIsentoMostraOMotivoDaFotografiaDoEmitente() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.ISENTO);
        UUID fr = emitirFr(tenant, "Cliente Isento");
        Map<String, Object> f = documento(fr);

        String[] c = celulas(linhas(service.exportar(tenant, autor(tenant), SETEMBRO).conteudo()).get(1));

        assertEquals(FixturaEmissaoFiscal.MOTIVO_ISENCAO, f.get("emitente_motivo_isencao_codigo"));
        assertEquals(FixturaEmissaoFiscal.MOTIVO_ISENCAO + " — " + f.get("emitente_motivo_isencao_descricao"),
                c[9].startsWith("\"") ? c[9].substring(1, c[9].length() - 1) : c[9]);
        assertEquals(valor(f.get("total_iva")), c[8]);
    }

    @Test
    void outroMesEOutroTenantFicamDeFora() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID outro = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        emitirFr(tenant, "Cliente de Setembro");
        inserirFrNaData(tenant, "2026-10-01", "Cliente de Outubro");
        inserirFrNaData(tenant, "2026-08-31", "Cliente de Agosto");
        emitirFr(outro, "Cliente do Outro Escritório");

        RelatorioMensalFiscalService.CsvMensal csv = service.exportar(tenant, autor(tenant), SETEMBRO);

        String texto = new String(csv.conteudo(), StandardCharsets.UTF_8);
        assertEquals(1, csv.numeroDocumentos());
        assertEquals(3, linhas(csv.conteudo()).size());
        assertTrue(texto.contains("Cliente de Setembro"));
        assertFalse(texto.contains("Cliente de Outubro"));
        assertFalse(texto.contains("Cliente de Agosto"));
        assertFalse(texto.contains("Cliente do Outro Escritório"));
    }

    @Test
    void mesVazioDaCabecalhoETotaisAZero() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);

        RelatorioMensalFiscalService.CsvMensal csv = service.exportar(tenant, autor(tenant), YearMonth.of(2026, 8));

        assertEquals(0, csv.numeroDocumentos());
        assertEquals("documentos-fiscais-simulacao-2026-08.csv", csv.nomeFicheiro());
        assertEquals(List.of(CABECALHO, "Totais;;;;;;;0,00;0,00;;0,00;0,00;;"), linhas(csv.conteudo()));
    }

    @Test
    void cadaExportacaoGravaUmEventoDeAuditoria() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID fr = emitirFr(tenant, "Maria Lopes");
        emitirNcParcial(tenant, fr, "30000.00");
        UserPrincipal autor = autor(tenant);

        service.exportar(tenant, autor, SETEMBRO);

        List<Map<String, Object>> eventos = jdbc.queryForList("SELECT * FROM t_audit_log WHERE tenant_id = ? "
                + "AND acao = 'documento_fiscal_exportar_mes'", tenant);
        assertEquals(1, eventos.size());
        Map<String, Object> e = eventos.get(0);
        assertEquals("relatorio_fiscal", e.get("entidade_tipo"));
        assertEquals("2026-09", e.get("entidade_id"));
        assertEquals(autor.getUserId(), e.get("autor_id"));
        String detalheTexto = String.valueOf(e.get("detalhe"));
        assertFalse(detalheTexto.contains("@"), detalheTexto);
        JsonNode d = json.readTree(detalheTexto);
        assertEquals("Ana Contabilista", d.get("autorNome").asText());
        assertEquals("2026-09", d.get("mes").asText());
        assertEquals(2, d.get("numeroDocumentos").asInt());

        service.exportar(tenant, autor, YearMonth.of(2026, 8));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM t_audit_log WHERE tenant_id = ? "
                + "AND acao = 'documento_fiscal_exportar_mes'", Integer.class, tenant));
    }
}
