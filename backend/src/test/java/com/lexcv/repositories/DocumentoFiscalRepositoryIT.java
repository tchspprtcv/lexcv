package com.lexcv.repositories;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.Cliente;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.Processo;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 134 (EMIS-08, EMIS-09, EMIS-11, D-15, D-17, R-01): prova, contra PostgreSQL real, os
 * filtros e a paginação da listagem, o isolamento por tenant, a imutabilidade em flush, o
 * re-apontamento nativo da fusão e os finders de lock. Mesmo andaime de {@code MigracaoFiscal133IT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DocumentoFiscalRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // pagamento_id é globalmente único: contadores partilhados por todos os testes.
    private static final AtomicInteger PAGAMENTO = new AtomicInteger(ThreadLocalRandom.current().nextInt(1, 1_000_000));
    private static final AtomicLong NUMERO = new AtomicLong(1);

    @Autowired private DocumentoFiscalRepository documentoRepo;
    @Autowired private DocumentoFiscalLinhaRepository linhaRepo;
    @Autowired private DocumentoFiscalLigacaoClienteRepository ligacaoRepo;
    @Autowired private ComunicacaoFiscalRepository comunicacaoRepo;
    @Autowired private ContaCorrenteRepository contaCorrenteRepo;
    @Autowired private ClienteRepository clienteRepo;
    @Autowired private ProcessoRepository processoRepo;
    @Autowired private ConfiguracaoFiscalRepository configuracaoRepo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transactionManager;

    // ------------------------------------------------------------------ fixtures

    private DocumentoFiscal.DocumentoFiscalBuilder doc(UUID tenantId, UUID clienteId, LocalDate data) {
        long numero = NUMERO.getAndIncrement();
        return DocumentoFiscal.builder()
                .tenantId(tenantId)
                .tipo(TipoDocumentoFiscal.FR)
                .ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.nameUUIDFromBytes(("serie-" + tenantId).getBytes()))
                .serieCodigo("SIM-FR-" + data.getYear())
                .ano(data.getYear())
                .numero(numero)
                .numeroFormatado("SIM-FR-" + data.getYear() + "/" + numero)
                .dataEmissao(data)
                .emitidoEm(Instant.parse("2026-01-01T10:00:00Z"))
                .emitenteNif("123456789")
                .emitenteFirma("Escritório Teste")
                .emitenteMorada("Rua A, Praia")
                .emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("234567891")
                .adquirenteNome("Cliente Fotografado")
                .adquirenteMorada("Rua B, Mindelo")
                .clienteId(clienteId)
                .processoId(UUID.randomUUID())
                .honorarioId(ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE))
                .pagamentoId(PAGAMENTO.getAndIncrement())
                .metodoPagamento("DINHEIRO")
                .meioPagamentoCodigo("10")
                .moeda("CVE")
                .taxaIva(new BigDecimal("15.0000"))
                .totalBase(new BigDecimal("100.00"))
                .totalIva(new BigDecimal("15.00"))
                .totalRetencao(BigDecimal.ZERO.setScale(2))
                .totalDocumento(new BigDecimal("115.00"))
                .valorLiquido(new BigDecimal("115.00"))
                .chaveIdempotencia(UUID.randomUUID());
    }

    private DocumentoFiscal guardar(UUID tenantId, UUID clienteId, LocalDate data) {
        return documentoRepo.save(doc(tenantId, clienteId, data).build());
    }

    /**
     * Phase 135: NC sobre uma FR -- o mesmo construtor {@link #doc}, com tipo NC, a referência à FR,
     * o motivo e um {@code pagamento_id} próprio (o do estorno).
     */
    private DocumentoFiscal.DocumentoFiscalBuilder nc(UUID tenantId, DocumentoFiscal origem, LocalDate data,
                                                      MotivoNotaCredito motivo) {
        return doc(tenantId, origem.getClienteId(), data)
                .tipo(TipoDocumentoFiscal.NC)
                .serieCodigo("SIM-NC-" + data.getYear())
                .documentoOrigemId(origem.getId())
                .motivoCodigo(motivo)
                .motivoTexto("Motivo de teste");
    }

    private void comunicacao(DocumentoFiscal d) {
        comunicacaoRepo.save(ComunicacaoFiscal.builder()
                .tenantId(d.getTenantId())
                .documentoFiscalId(d.getId())
                .ambiente(AmbienteFiscal.SIMULADO)
                .estado(EstadoComunicacaoFiscal.PENDENTE)
                .createdAt(Instant.now())
                .build());
    }

    private Set<UUID> ids(Page<DocumentoFiscal> p) {
        return p.getContent().stream().map(DocumentoFiscal::getId).collect(Collectors.toSet());
    }

    private Page<DocumentoFiscal> buscar(UUID tenantId, UUID clienteId, String tipo, String estado,
                                         LocalDate de, LocalDate ate) {
        return documentoRepo.buscar(tenantId, clienteId == null ? null : clienteId.toString(), tipo, estado,
                de, ate, PageRequest.of(0, 50));
    }

    private Cliente cliente(UUID tenantId) {
        return clienteRepo.save(Cliente.builder().tenantId(tenantId).nome("Cliente IT")
                .nif("2" + String.format("%08d", ThreadLocalRandom.current().nextInt(0, 99_999_999))).build());
    }

    // ------------------------------------------------------------------ isolamento

    @Test
    void tenantANuncaVeDocumentosDoTenantB() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID clienteComum = UUID.randomUUID();
        DocumentoFiscal docA = guardar(a, clienteComum, LocalDate.of(2026, 3, 1));
        DocumentoFiscal docB = guardar(b, clienteComum, LocalDate.of(2026, 3, 1));

        Page<DocumentoFiscal> paginaA = buscar(a, null, null, null, null, null);
        assertEquals(Set.of(docA.getId()), ids(paginaA));
        assertEquals(1, buscar(a, clienteComum, null, null, null, null).getTotalElements());

        assertTrue(documentoRepo.findByIdAndTenantId(docB.getId(), a).isEmpty());
        assertTrue(documentoRepo.findByIdAndTenantId(docA.getId(), a).isPresent());
        assertTrue(documentoRepo.findByTenantIdAndChaveIdempotencia(a, docB.getChaveIdempotencia()).isEmpty());
        assertEquals(List.of(docA.getId()), documentoRepo
                .findByTenantIdAndPagamentoIdIn(a, List.of(docA.getPagamentoId(), docB.getPagamentoId()))
                .stream().map(DocumentoFiscal::getId).toList());
    }

    @Test
    void existsSaoCorretosEPorTenant() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        DocumentoFiscal d = guardar(a, UUID.randomUUID(), LocalDate.of(2026, 3, 1));

        assertTrue(documentoRepo.existsByTenantIdAndPagamentoId(a, d.getPagamentoId()));
        assertTrue(documentoRepo.existsByTenantIdAndClienteId(a, d.getClienteId()));
        assertTrue(documentoRepo.existsByTenantIdAndProcessoId(a, d.getProcessoId()));
        assertTrue(documentoRepo.existsByTenantIdAndHonorarioId(a, d.getHonorarioId()));

        assertFalse(documentoRepo.existsByTenantIdAndPagamentoId(b, d.getPagamentoId()));
        assertFalse(documentoRepo.existsByTenantIdAndClienteId(b, d.getClienteId()));
        assertFalse(documentoRepo.existsByTenantIdAndProcessoId(b, d.getProcessoId()));
        assertFalse(documentoRepo.existsByTenantIdAndHonorarioId(b, d.getHonorarioId()));

        assertFalse(documentoRepo.existsByTenantIdAndClienteId(a, UUID.randomUUID()));
        assertFalse(documentoRepo.existsByTenantIdAndPagamentoId(a, -1));
    }

    // ------------------------------------------------------------------ filtros

    @Test
    void filtroClienteETipo() {
        UUID t = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        DocumentoFiscal d1 = guardar(t, c1, LocalDate.of(2026, 3, 1));
        DocumentoFiscal d2 = guardar(t, c2, LocalDate.of(2026, 3, 2));
        DocumentoFiscal nc = documentoRepo.save(doc(t, c1, LocalDate.of(2026, 3, 3)).tipo(TipoDocumentoFiscal.NC).build());

        assertEquals(Set.of(d1.getId(), d2.getId(), nc.getId()), ids(buscar(t, null, null, null, null, null)));
        assertEquals(Set.of(d1.getId(), nc.getId()), ids(buscar(t, c1, null, null, null, null)));
        assertEquals(Set.of(d1.getId(), d2.getId()), ids(buscar(t, null, "FR", null, null, null)));
        assertEquals(Set.of(nc.getId()), ids(buscar(t, null, "NC", null, null, null)));
        assertEquals(Set.of(d1.getId()), ids(buscar(t, c1, "FR", null, null, null)));
    }

    @Test
    void filtroEstadoUsaLeftJoinAComunicacao() {
        UUID t = UUID.randomUUID();
        DocumentoFiscal comPendente = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal semComunicacao = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal outroEstado = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        comunicacao(comPendente);
        em.flush();
        // Um estado futuro (Phase 136) gravado diretamente: o filtro compara o texto.
        jdbc.update("INSERT INTO t_comunicacao_fiscal (id, tenant_id, documento_fiscal_id, ambiente, estado, "
                        + "tentativas, created_at, versao) VALUES (?, ?, ?, 'SIMULADO', 'ACEITE', 0, now(), 0)",
                UUID.randomUUID(), t, outroEstado.getId());

        assertEquals(Set.of(comPendente.getId(), semComunicacao.getId(), outroEstado.getId()),
                ids(buscar(t, null, null, null, null, null)));
        assertEquals(Set.of(comPendente.getId()), ids(buscar(t, null, null, "PENDENTE", null, null)));
        assertEquals(1, buscar(t, null, null, "PENDENTE", null, null).getTotalElements());
        assertEquals(Set.of(outroEstado.getId()), ids(buscar(t, null, null, "ACEITE", null, null)));
    }

    @Test
    void filtroDePeriodoComLimitesInclusivos() {
        UUID t = UUID.randomUUID();
        DocumentoFiscal d1 = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 1, 10));
        DocumentoFiscal d2 = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 2, 15));
        DocumentoFiscal d3 = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 20));

        assertEquals(Set.of(d2.getId(), d3.getId()), ids(buscar(t, null, null, null, LocalDate.of(2026, 2, 15), null)));
        assertEquals(Set.of(d1.getId(), d2.getId()), ids(buscar(t, null, null, null, null, LocalDate.of(2026, 2, 15))));
        assertEquals(Set.of(d1.getId(), d2.getId(), d3.getId()),
                ids(buscar(t, null, null, null, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 20))));
        assertEquals(Set.of(d2.getId()),
                ids(buscar(t, null, null, null, LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 15))));
        assertTrue(ids(buscar(t, null, null, null, LocalDate.of(2026, 4, 1), null)).isEmpty());
    }

    @Test
    void paginacaoTotaisEOrdenacao() {
        UUID t = UUID.randomUUID();
        for (int i = 0; i < 25; i++) {
            guardar(t, UUID.randomUUID(), LocalDate.of(2026, 1, 1).plusDays(i % 5));
        }

        Page<DocumentoFiscal> p0 = documentoRepo.buscar(t, null, null, null, null, null, PageRequest.of(0, 10));
        assertEquals(10, p0.getContent().size());
        assertEquals(25, p0.getTotalElements());
        assertEquals(3, p0.getTotalPages());
        Page<DocumentoFiscal> p2 = documentoRepo.buscar(t, null, null, null, null, null, PageRequest.of(2, 10));
        assertEquals(5, p2.getContent().size());

        List<DocumentoFiscal> todos = documentoRepo.buscar(t, null, null, null, null, null, PageRequest.of(0, 25))
                .getContent();
        for (int i = 1; i < todos.size(); i++) {
            DocumentoFiscal anterior = todos.get(i - 1);
            DocumentoFiscal atual = todos.get(i);
            int cmpData = anterior.getDataEmissao().compareTo(atual.getDataEmissao());
            assertTrue(cmpData > 0 || (cmpData == 0 && anterior.getNumero() > atual.getNumero()),
                    "ordem esperada: data_emissao desc, numero desc");
        }
    }

    // ------------------------------------------------------------------ imutabilidade

    @Test
    void alteracaoEmMemoriaNaoChegaABaseDeDadosNoFlush() throws Exception {
        UUID t = UUID.randomUUID();
        DocumentoFiscal d = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        em.flush();
        em.clear();

        DocumentoFiscal gerido = documentoRepo.findByIdAndTenantId(d.getId(), t).orElseThrow();
        Field nome = DocumentoFiscal.class.getDeclaredField("adquirenteNome");
        nome.setAccessible(true);
        nome.set(gerido, "ALTERADO");
        Field total = DocumentoFiscal.class.getDeclaredField("totalDocumento");
        total.setAccessible(true);
        total.set(gerido, new BigDecimal("999.99"));
        em.flush();
        em.clear();

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT adquirente_nome, total_documento FROM t_documento_fiscal WHERE id = ?", d.getId());
        assertEquals("Cliente Fotografado", linha.get("adquirente_nome"));
        assertEquals(0, new BigDecimal("115.00").compareTo((BigDecimal) linha.get("total_documento")));
    }

    @Test
    void linhasPorTenantOrdenadas() {
        UUID t = UUID.randomUUID();
        DocumentoFiscal d = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        for (int n : new int[]{2, 1}) {
            linhaRepo.save(DocumentoFiscalLinha.builder().tenantId(t).documentoFiscalId(d.getId()).numeroLinha(n)
                    .descricao("Honorários").quantidade(BigDecimal.ONE).precoUnitario(new BigDecimal("100.00"))
                    .valorBase(new BigDecimal("100.00")).taxaIva(new BigDecimal("15")).valorIva(new BigDecimal("15.00"))
                    .valorRetencao(BigDecimal.ZERO).totalLinha(new BigDecimal("115.00")).build());
        }
        assertEquals(List.of(1, 2), linhaRepo.findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(t, d.getId())
                .stream().map(DocumentoFiscalLinha::getNumeroLinha).toList());
        assertTrue(linhaRepo.findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(UUID.randomUUID(), d.getId())
                .isEmpty());
    }

    // ------------------------------------------------------------------ fusão

    @Test
    void repontarClienteMoveSoOsDocumentosDoTenantESemTocarNaFotografia() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        DocumentoFiscal a1 = guardar(a, c1, LocalDate.of(2026, 3, 1));
        DocumentoFiscal a2 = guardar(a, c1, LocalDate.of(2026, 3, 2));
        DocumentoFiscal outroCliente = guardar(a, UUID.randomUUID(), LocalDate.of(2026, 3, 2));
        DocumentoFiscal deB = guardar(b, c1, LocalDate.of(2026, 3, 1));
        em.flush();

        assertEquals(2, ligacaoRepo.repontarCliente(a, c1, c2));
        em.clear();

        assertEquals(c2, documentoRepo.findByIdAndTenantId(a1.getId(), a).orElseThrow().getClienteId());
        assertEquals(c2, documentoRepo.findByIdAndTenantId(a2.getId(), a).orElseThrow().getClienteId());
        assertEquals(outroCliente.getClienteId(),
                documentoRepo.findByIdAndTenantId(outroCliente.getId(), a).orElseThrow().getClienteId());
        assertEquals(c1, documentoRepo.findByIdAndTenantId(deB.getId(), b).orElseThrow().getClienteId());
        Map<String, Object> foto = jdbc.queryForMap(
                "SELECT adquirente_nome, adquirente_nif FROM t_documento_fiscal WHERE id = ?", a1.getId());
        assertEquals("Cliente Fotografado", foto.get("adquirente_nome"));
        assertEquals("234567891", foto.get("adquirente_nif"));
        assertFalse(documentoRepo.existsByTenantIdAndClienteId(a, c1));
        assertTrue(documentoRepo.existsByTenantIdAndClienteId(b, c1));
    }

    // ------------------------------------------------------------------ locks

    @Test
    void contaCorrenteCriadaUmaVezEBloqueavel() {
        UUID clienteId = UUID.randomUUID();
        assertEquals(1, contaCorrenteRepo.criarSeNaoExiste(clienteId));
        assertEquals(0, contaCorrenteRepo.criarSeNaoExiste(clienteId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM t_conta_corrente WHERE cliente_id = ?",
                Integer.class, clienteId));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                contaCorrenteRepo.bloquearPorCliente(clienteId).orElseThrow().getSaldo()));
        assertTrue(contaCorrenteRepo.bloquearPorCliente(UUID.randomUUID()).isEmpty());
    }

    @Test
    void locksDeClienteEProcessoSaoPorTenant() {
        UUID t = UUID.randomUUID();
        Cliente c = cliente(t);
        Processo p = processoRepo.save(Processo.builder().tenantId(t).clienteId(c.getId()).numeroProcesso("P-1").build());
        em.flush();
        em.clear();

        assertTrue(clienteRepo.bloquearPorIdETenant(c.getId(), t).isPresent());
        assertTrue(clienteRepo.bloquearPorIdETenant(c.getId(), UUID.randomUUID()).isEmpty());
        assertTrue(processoRepo.bloquearPorIdETenant(p.getId(), t).isPresent());
        assertTrue(processoRepo.bloquearPorIdETenant(p.getId(), UUID.randomUUID()).isEmpty());
        assertEquals(c.getId(), processoRepo.clienteIdPorIdETenant(p.getId(), t).orElseThrow());
        assertTrue(processoRepo.clienteIdPorIdETenant(p.getId(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void ativaPorTenantDevolveVazioOuOValor() {
        UUID semConfig = UUID.randomUUID();
        UUID inativa = UUID.randomUUID();
        UUID ativa = UUID.randomUUID();
        configuracaoRepo.save(ConfiguracaoFiscal.builder().tenantId(inativa).ativa(false).createdAt(Instant.now()).build());
        configuracaoRepo.save(ConfiguracaoFiscal.builder().tenantId(ativa).ativa(true).createdAt(Instant.now()).build());
        em.flush();

        assertTrue(configuracaoRepo.ativaPorTenant(semConfig).isEmpty());
        assertEquals(Boolean.FALSE, configuracaoRepo.ativaPorTenant(inativa).orElseThrow());
        assertEquals(Boolean.TRUE, configuracaoRepo.ativaPorTenant(ativa).orElseThrow());
    }

    // ------------------------------------------------------------------ notas de crédito (Phase 135)

    @Test
    void notasDeCreditoPorOrigemSoDoTenantMaisRecentesPrimeiro() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        DocumentoFiscal fr = guardar(a, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal outraFr = guardar(a, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal nc1 = documentoRepo.save(nc(a, fr, LocalDate.of(2026, 3, 2), MotivoNotaCredito.CORRECAO_VALOR).build());
        DocumentoFiscal nc2 = documentoRepo.save(nc(a, fr, LocalDate.of(2026, 3, 5), MotivoNotaCredito.OUTRO).build());
        DocumentoFiscal nc3 = documentoRepo.save(nc(a, fr, LocalDate.of(2026, 3, 5), MotivoNotaCredito.OUTRO).build());
        documentoRepo.save(nc(a, outraFr, LocalDate.of(2026, 3, 6), MotivoNotaCredito.OUTRO).build());
        // NC de outro tenant com o mesmo documento_origem_id: nunca devolvida.
        documentoRepo.save(nc(b, fr, LocalDate.of(2026, 3, 9), MotivoNotaCredito.OUTRO).build());

        assertEquals(List.of(nc3.getId(), nc2.getId(), nc1.getId()),
                documentoRepo.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(a, fr.getId())
                        .stream().map(DocumentoFiscal::getId).toList());
        assertTrue(documentoRepo.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(
                UUID.randomUUID(), fr.getId()).isEmpty());
        assertTrue(documentoRepo.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(
                a, nc1.getId()).isEmpty());
    }

    @Test
    void documentosPorIdsSoDoTenant() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        DocumentoFiscal a1 = guardar(a, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal a2 = guardar(a, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal deB = guardar(b, UUID.randomUUID(), LocalDate.of(2026, 3, 1));

        assertEquals(Set.of(a1.getId(), a2.getId()), documentoRepo
                .findByTenantIdAndIdIn(a, List.of(a1.getId(), a2.getId(), deB.getId())).stream()
                .map(DocumentoFiscal::getId).collect(Collectors.toSet()));
        assertTrue(documentoRepo.findByTenantIdAndIdIn(b, List.of(a1.getId())).isEmpty());
    }

    @Test
    void sondaDeEstornoDistingueTipoETenant() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        DocumentoFiscal fr = guardar(a, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal notaCredito = documentoRepo.save(
                nc(a, fr, LocalDate.of(2026, 3, 2), MotivoNotaCredito.ANULACAO_TOTAL).build());

        assertTrue(documentoRepo.existsByTenantIdAndPagamentoIdAndTipo(a, notaCredito.getPagamentoId(),
                TipoDocumentoFiscal.NC));
        assertFalse(documentoRepo.existsByTenantIdAndPagamentoIdAndTipo(a, fr.getPagamentoId(),
                TipoDocumentoFiscal.NC));
        assertTrue(documentoRepo.existsByTenantIdAndPagamentoIdAndTipo(a, fr.getPagamentoId(),
                TipoDocumentoFiscal.FR));
        assertFalse(documentoRepo.existsByTenantIdAndPagamentoIdAndTipo(b, notaCredito.getPagamentoId(),
                TipoDocumentoFiscal.NC));
        // O estorno conta como "faturado" para a guarda de eliminação existente.
        assertTrue(documentoRepo.existsByTenantIdAndPagamentoId(a, notaCredito.getPagamentoId()));
    }

    @Test
    void motivoGravadoComoNomeELidoComoEnum() {
        UUID t = UUID.randomUUID();
        DocumentoFiscal fr = guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        DocumentoFiscal notaCredito = documentoRepo.save(
                nc(t, fr, LocalDate.of(2026, 3, 2), MotivoNotaCredito.ERRO_DADOS_CLIENTE).build());
        em.flush();
        em.clear();

        Map<String, Object> linha = jdbc.queryForMap("SELECT motivo_codigo, motivo_texto, documento_origem_id "
                + "FROM t_documento_fiscal WHERE id = ?", notaCredito.getId());
        assertEquals("ERRO_DADOS_CLIENTE", linha.get("motivo_codigo"));
        assertEquals("Motivo de teste", linha.get("motivo_texto"));
        assertEquals(fr.getId(), linha.get("documento_origem_id"));

        DocumentoFiscal lido = documentoRepo.findByIdAndTenantId(notaCredito.getId(), t).orElseThrow();
        assertEquals(MotivoNotaCredito.ERRO_DADOS_CLIENTE, lido.getMotivoCodigo());
        assertEquals(TipoDocumentoFiscal.NC, lido.getTipo());
        DocumentoFiscal frLida = documentoRepo.findByIdAndTenantId(fr.getId(), t).orElseThrow();
        assertEquals(null, frLida.getDocumentoOrigemId());
        assertEquals(null, frLida.getMotivoCodigo());
        assertEquals(null, frLida.getMotivoTexto());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void estornoNaoPodeReceberSegundoDocumento() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID t = UUID.randomUUID();
        DocumentoFiscal fr = tx.execute(s -> guardar(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1)));
        DocumentoFiscal notaCredito = tx.execute(s ->
                documentoRepo.save(nc(t, fr, LocalDate.of(2026, 3, 2), MotivoNotaCredito.CORRECAO_VALOR).build()));

        // Uma FR (ou outra NC) para o mesmo pagamento de estorno falha em uk_documento_fiscal_pagamento.
        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class,
                () -> tx.executeWithoutResult(s -> documentoRepo.save(doc(t, UUID.randomUUID(),
                        LocalDate.of(2026, 3, 3)).pagamentoId(notaCredito.getPagamentoId()).build())));
        assertTrue(String.valueOf(e.getMostSpecificCause().getMessage()).contains("uk_documento_fiscal_pagamento"),
                e.getMostSpecificCause().getMessage());
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(s ->
                documentoRepo.save(nc(t, fr, LocalDate.of(2026, 3, 3), MotivoNotaCredito.OUTRO)
                        .pagamentoId(notaCredito.getPagamentoId()).build())));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM t_documento_fiscal WHERE tenant_id = ?",
                Integer.class, t));
    }

    // ------------------------------------------------------------------ unicidade (commit real)

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void chaveDeIdempotenciaEPagamentoSaoUnicos() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID t = UUID.randomUUID();
        UUID chave = UUID.randomUUID();
        DocumentoFiscal original = tx.execute(s ->
                documentoRepo.save(doc(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1)).chaveIdempotencia(chave).build()));

        // mesma chave, mesmo tenant
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(s ->
                documentoRepo.save(doc(t, UUID.randomUUID(), LocalDate.of(2026, 3, 1)).chaveIdempotencia(chave).build())));
        // mesma chave noutro tenant é permitida
        tx.executeWithoutResult(s ->
                documentoRepo.save(doc(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 3, 1))
                        .chaveIdempotencia(chave).build()));
        // mesmo pagamento (global), mesmo noutro tenant
        assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(s ->
                documentoRepo.save(doc(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 3, 1))
                        .pagamentoId(original.getPagamentoId()).build())));

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM t_documento_fiscal WHERE tenant_id = ?",
                Integer.class, t));
    }
}
