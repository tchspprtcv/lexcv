package com.lexcv.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ClienteMergeRequest;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.Pagamento;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.*;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import com.lexcv.services.RiscoPrazoService;
import com.lexcv.services.StorageService;
import com.lexcv.services.fiscal.AuditoriaFiscalService;
import com.lexcv.services.fiscal.DocumentoFiscalService;
import com.lexcv.services.fiscal.FixturaEmissaoFiscal;
import com.lexcv.services.fiscal.NumeracaoService;
import com.lexcv.services.fiscal.PagamentoFaturadoService;
import com.lexcv.services.fiscal.ParametroFiscalService;
import com.lexcv.services.fiscal.PreVisualizacaoFaturaService;
import com.lexcv.services.fiscal.ResultadoPagamentoFaturado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * Phase 134 (EMIS-09, D-14, D-15, R-01, CFG-03): as guardas de eliminação e a fusão de clientes
 * sob concorrência real em PostgreSQL, contra emissões em curso.
 *
 * <p>Uma emissão "em curso" é {@link PagamentoFaturadoService#registar} chamado dentro de um
 * {@link TransactionTemplate} que só faz commit quando o teste liberta um latch: os locks de
 * cliente/processo/conta corrente/série ficam seguros até lá. O {@link ResourceController} é
 * construído à mão (sem proxy), por isso cada chamada a um handler é embrulhada num
 * {@code TransactionTemplate}, que reproduz a fronteira {@code @Transactional} do handler. Aí o
 * {@code RecusaTransacional.recusar} cai no ramo {@code NoTransactionException}, o que é aceitável:
 * as recusas não escrevem nada. Cada thread de trabalho define o seu próprio SecurityContext.
 *
 * <p>Prova: (a/c) uma emissão em curso bloqueia apagar o cliente/processo, que depois recusa com
 * 409; (b) apagar o cliente em curso faz a emissão falhar sem órfãos; (d) a fusão espera a emissão
 * e re-aponta o documento sem mudar a fotografia do adquirente; (e) carga mista sem deadlock
 * (40P01) nem documento órfão; (f) faturação desligada não cria linhas fiscais; (g) uma emissão em
 * curso bloqueia apagar o honorário, que depois recusa com 409; (h) apagar o honorário em curso
 * faz a emissão, que já o tinha lido, falhar sem documento nem pagamento órfão (CR-01 da revisão);
 * (i/j) apagar um pagamento legado durante uma emissão ou uma fusão não perde nenhum movimento da
 * conta corrente (WR-01 da revisão); (k) repetir o pedido de uma FR já emitida depois de a
 * faturação ser desligada devolve o mesmo resultado, sem segundo pagamento (WR-05 da revisão).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({PagamentoFaturadoService.class, PreVisualizacaoFaturaService.class, DocumentoFiscalService.class,
        NumeracaoService.class, ParametroFiscalService.class, AuditoriaFiscalService.class,
        GuardasDocumentoFiscalConcorrenciaIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuardasDocumentoFiscalConcorrenciaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class Apoio {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-15T13:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /** Phase 137: DocumentoFiscalService lê se o SMTP está configurado; aqui não está. */
        @Bean
        com.lexcv.fiscal.email.EmailProperties emailProperties() {
            return new com.lexcv.fiscal.email.EmailProperties(
                    new com.lexcv.fiscal.email.EmailProperties.Smtp(null, 587, null, null, null, true,
                            java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(20)),
                    new com.lexcv.fiscal.email.EmailProperties.Outbox(java.time.Duration.ofSeconds(30),
                            java.time.Duration.ofSeconds(40), 10, java.time.Duration.ofMinutes(2)));
        }
    }

    private static final String SQLSTATE_DEADLOCK = "40P01";
    private static final BigDecimal VALOR = new BigDecimal("120000.00");

    private static final String SQL_ORFAOS = "SELECT count(*) FROM t_documento_fiscal d "
            + "WHERE NOT EXISTS (SELECT 1 FROM t_cliente c WHERE c.id = d.cliente_id)";

    private static final String SQL_ORFAOS_HONORARIO = "SELECT count(*) FROM t_documento_fiscal d "
            + "WHERE NOT EXISTS (SELECT 1 FROM t_honorario h WHERE h.id = d.honorario_id)";

    private static final String SQL_PAGAMENTOS_ORFAOS = "SELECT count(*) FROM t_pagamento p "
            + "WHERE NOT EXISTS (SELECT 1 FROM t_honorario h WHERE h.id = p.honorario_id)";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PagamentoFaturadoService pagamentoFaturadoService;
    @Autowired private DocumentoFiscalService documentoFiscalService;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ClienteContactoRepository clienteContactoRepository;
    @Autowired private ClienteNotaRepository clienteNotaRepository;
    @Autowired private ContaCorrenteRepository contaCorrenteRepository;
    @Autowired private ProcessoRepository processoRepository;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private HonorarioRepository honorarioRepository;
    @Autowired private PagamentoRepository pagamentoRepository;
    @Autowired private ClienteAdvogadoRepository clienteAdvogadoRepository;
    @Autowired private ClienteAdministrativoRepository clienteAdministrativoRepository;
    @Autowired private ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    private FixturaEmissaoFiscal fixtura;
    private TransactionTemplate tx;
    private ResourceController controller;
    private ExecutorService executor;

    /** Todas as falhas observadas (para a verificação de deadlock no fim de cada teste). */
    private final List<Throwable> falhas = new CopyOnWriteArrayList<>();

    private record Cenario(UUID tenantId, UUID clienteId, UUID processoId, Integer honorarioId) {
    }

    @BeforeEach
    void preparar() {
        fixtura = new FixturaEmissaoFiscal(jdbc);
        fixtura.garantirParametros();
        tx = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(8);
        falhas.clear();
        controller = new ResourceController(
                clienteRepository, clienteContactoRepository, clienteNotaRepository,
                contaCorrenteRepository, processoRepository, mock(ParteRepository.class),
                mock(FaseProcessualRepository.class), mock(ProcessoFaseRepository.class), mock(EventoRepository.class),
                documentoRepository, mock(MovimentacaoRepository.class), honorarioRepository,
                pagamentoRepository, mock(ConflictCheckDecisaoRepository.class), mock(PrazoRepository.class),
                mock(UserRepository.class), mock(AuditLogRepository.class), mock(StorageService.class),
                mock(RiscoPrazoService.class), mock(NotificacaoService.class), clienteAdvogadoRepository,
                clienteAdministrativoRepository, mock(DecisaoRepository.class),
                mock(TestemunhaRepository.class), mock(FactoRepository.class),
                parecerSolicitacaoRepository, mock(ResolucaoPapeisService.class),
                pagamentoFaturadoService, documentoFiscalService);
    }

    @AfterEach
    void terminar() {
        executor.shutdownNow();
        SecurityContextHolder.clearContext();
        for (Throwable t : falhas) {
            assertFalse(eDeadlock(t), "deadlock observado: " + t);
        }
    }

    // ------------------------------------------------------------------ apoio

    private Cenario cenario(UUID tenant, String sufixo) {
        UUID cliente = fixtura.criarCliente(tenant, "234567891", "Cliente " + sufixo, "Rua " + sufixo);
        UUID processo = fixtura.criarProcesso(tenant, cliente, "P-" + sufixo);
        Integer honorario = fixtura.criarHonorario(processo, new BigDecimal("900000.00"), "Honorário " + sufixo);
        return new Cenario(tenant, cliente, processo, honorario);
    }

    private static UserPrincipal principal(UUID tenant) {
        return UserPrincipal.create(UUID.randomUUID(), tenant, "Ana Emissora", "ana@example.cv",
                Set.of(), Set.of("financeiro:manage", "clientes:edit", "processos:edit"), Set.of());
    }

    private static PagamentoRequest pedido(Integer honorarioId) {
        return new PagamentoRequest(honorarioId, VALOR, null, "DINHEIRO", null, UUID.randomUUID());
    }

    private static <T> T comoUtilizador(UUID tenant, Supplier<T> chamada) {
        UserPrincipal p = principal(tenant);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
        try {
            return chamada.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Handler numa thread própria, dentro de uma transação (a fronteira do @Transactional). */
    private Future<ResponseEntity<?>> handler(UUID tenant, Supplier<ResponseEntity<?>> chamada) {
        return executor.submit(() -> comoUtilizador(tenant, () -> tx.execute(s -> chamada.get())));
    }

    /** Emissão que faz o trabalho todo, sinaliza e só faz commit quando {@code libertar} abrir. */
    private Future<ResultadoPagamentoFaturado> emissaoSegura(Cenario c, CountDownLatch emitida,
                                                            CountDownLatch libertar) {
        return executor.submit(() -> tx.execute(s -> {
            ResultadoPagamentoFaturado r =
                    pagamentoFaturadoService.registar(c.tenantId(), principal(c.tenantId()), pedido(c.honorarioId()));
            emitida.countDown();
            aguardar(libertar);
            return r;
        }));
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch não foi libertado a tempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private int esperasDeLock() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
        return n == null ? 0 : n;
    }

    /** Prova que a operação está parada num lock de linha (pg_stat_activity) e não terminou. */
    private void esperarBloqueado(Future<?> f) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (esperasDeLock() == 0) {
            if (System.nanoTime() > limite) {
                fail("a operação concorrente nunca ficou à espera de um lock");
            }
            Thread.sleep(50);
        }
        assertFalse(f.isDone(), "a operação devia estar bloqueada pelo lock da outra transação");
    }

    private <T> T resultado(Future<T> f) throws Exception {
        try {
            return f.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            falhas.add(e.getCause());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> corpo(ResponseEntity<?> r) {
        return (Map<String, Object>) r.getBody();
    }

    private static boolean eDeadlock(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql && SQLSTATE_DEADLOCK.equals(sql.getSQLState())) {
                return true;
            }
            if (c.getMessage() != null && c.getMessage().toLowerCase().contains("deadlock")) {
                return true;
            }
        }
        return false;
    }

    private int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private boolean clienteExiste(UUID id) {
        return contar("SELECT count(*) FROM t_cliente WHERE id = ?", id) == 1;
    }

    // ------------------------------------------------------------------ a

    @Test
    void emissaoEmCursoBloqueiaApagarCliente() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, "a");
        CountDownLatch emitida = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        Future<ResultadoPagamentoFaturado> a = emissaoSegura(c, emitida, libertar);
        assertTrue(emitida.await(30, TimeUnit.SECONDS));
        Future<ResponseEntity<?>> b = handler(tenant, () -> controller.deleteCliente(c.clienteId()));
        esperarBloqueado(b);

        libertar.countDown();
        ResultadoPagamentoFaturado emissao = resultado(a);
        ResponseEntity<?> resposta = resultado(b);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("CLIENTE_COM_DOCUMENTOS_FISCAIS", corpo(resposta).get("code"));
        assertTrue(clienteExiste(c.clienteId()));
        assertEquals(1, fixtura.contarDocumentos(tenant));
        UUID clienteDoDocumento = jdbc.queryForObject("SELECT cliente_id FROM t_documento_fiscal WHERE id = ?",
                UUID.class, emissao.resposta().documentoFiscal().id());
        assertEquals(c.clienteId(), clienteDoDocumento);
    }

    // ------------------------------------------------------------------ b

    @Test
    void apagarClienteEmCursoFazEmissaoFalharSemOrfaos() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, "b");
        Long serieAntes = fixtura.ultimoNumero(tenant);
        CountDownLatch apagado = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        Future<ResponseEntity<?>> b = executor.submit(() -> comoUtilizador(tenant, () -> tx.execute(s -> {
            ResponseEntity<?> r = controller.deleteCliente(c.clienteId());
            apagado.countDown();
            aguardar(libertar);
            return r;
        })));
        assertTrue(apagado.await(30, TimeUnit.SECONDS));
        Future<ResultadoPagamentoFaturado> a = executor.submit(() ->
                pagamentoFaturadoService.registar(tenant, principal(tenant), pedido(c.honorarioId())));
        // A emissão tem lock_timeout de 5 s: libertar logo que esteja comprovadamente à espera.
        esperarBloqueado(a);
        libertar.countDown();

        assertEquals(HttpStatus.OK, resultado(b).getStatusCode());
        ExecutionException erro = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> a.get(30, TimeUnit.SECONDS));
        RecusaFiscalException recusa = assertInstanceOf(RecusaFiscalException.class, erro.getCause());
        // IN-05 da revisão: o cliente desapareceu enquanto a emissão esperava pelo lock dele -- o
        // mesmo 404 CLIENTE_NAO_ENCONTRADO que a pré-visualização dá para este estado.
        assertEquals(HttpStatus.NOT_FOUND, recusa.getStatus(), recusa.getCodigo());
        assertEquals("CLIENTE_NAO_ENCONTRADO", recusa.getCodigo());

        assertFalse(clienteExiste(c.clienteId()));
        assertEquals(0, fixtura.contarPagamentos(c.honorarioId()));
        assertEquals(0, fixtura.contarDocumentos(tenant));
        assertEquals(0, fixtura.contarLinhas(tenant));
        assertEquals(serieAntes, fixtura.ultimoNumero(tenant));
        assertEquals(0, contar(SQL_ORFAOS));
    }

    // ------------------------------------------------------------------ c

    @Test
    void emissaoEmCursoBloqueiaApagarProcesso() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, "c");
        CountDownLatch emitida = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        Future<ResultadoPagamentoFaturado> a = emissaoSegura(c, emitida, libertar);
        assertTrue(emitida.await(30, TimeUnit.SECONDS));
        Future<ResponseEntity<?>> b = handler(tenant, () -> controller.deleteProcesso(c.processoId()));
        esperarBloqueado(b);

        libertar.countDown();
        resultado(a);
        ResponseEntity<?> resposta = resultado(b);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("PROCESSO_COM_DOCUMENTOS_FISCAIS", corpo(resposta).get("code"));
        assertEquals(1, contar("SELECT count(*) FROM t_processo WHERE id = ?", c.processoId()));
        assertEquals(1, fixtura.contarDocumentos(tenant));
    }

    // ------------------------------------------------------------------ d

    @Test
    void fusaoDepoisDeEmissaoEmCursoRepontaDocumentos() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID primario = fixtura.criarCliente(tenant, "212345678", "Primário Lda", "Avenida Principal");
        Cenario secundario = cenario(tenant, "d");
        CountDownLatch emitida = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        Future<ResultadoPagamentoFaturado> a = emissaoSegura(secundario, emitida, libertar);
        assertTrue(emitida.await(30, TimeUnit.SECONDS));
        Future<ResponseEntity<?>> fusao = handler(tenant,
                () -> controller.mergeClientes(new ClienteMergeRequest(primario, secundario.clienteId())));
        esperarBloqueado(fusao);

        libertar.countDown();
        ResultadoPagamentoFaturado emissao = resultado(a);
        ResponseEntity<?> resposta = resultado(fusao);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(1, corpo(resposta).get("moved_documentos_fiscais"));
        assertFalse(clienteExiste(secundario.clienteId()));
        Map<String, Object> doc = jdbc.queryForMap(
                "SELECT cliente_id, adquirente_nome, adquirente_nif FROM t_documento_fiscal WHERE id = ?",
                emissao.resposta().documentoFiscal().id());
        assertEquals(primario, doc.get("cliente_id"));
        assertEquals("Cliente d", doc.get("adquirente_nome"));
        assertEquals("234567891", doc.get("adquirente_nif"));
        assertEquals(0, contar(SQL_ORFAOS));
    }

    // ------------------------------------------------------------------ e

    @Test
    void semDeadlockEmCargaMista() throws Exception {
        for (int ronda = 0; ronda < 10; ronda++) {
            UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
            UUID primario = fixtura.criarCliente(tenant, "212345678", "Primário " + ronda, "Rua P");
            Cenario secundario = cenario(tenant, "e-s-" + ronda);
            UUID primario2 = fixtura.criarCliente(tenant, "212345679", "Primário B " + ronda, "Rua PB");
            Cenario secundario2 = cenario(tenant, "e-s2-" + ronda);
            Cenario alheio = cenario(tenant, "e-u-" + ronda);

            List<Callable<Object>> tarefas = new ArrayList<>();
            tarefas.add(() -> pagamentoFaturadoService.registar(tenant, principal(tenant),
                    pedido(secundario.honorarioId())));
            tarefas.add(() -> comoUtilizador(tenant, () -> tx.execute(s ->
                    controller.mergeClientes(new ClienteMergeRequest(primario, secundario.clienteId())))));
            // A mesma fusão com os argumentos trocados: só a ordem ascendente de UUID evita o ciclo.
            tarefas.add(() -> comoUtilizador(tenant, () -> tx.execute(s ->
                    controller.mergeClientes(new ClienteMergeRequest(secundario.clienteId(), primario)))));
            // Outro par, argumentos invertidos (secundário' como principal).
            tarefas.add(() -> comoUtilizador(tenant, () -> tx.execute(s ->
                    controller.mergeClientes(new ClienteMergeRequest(secundario2.clienteId(), primario2)))));
            tarefas.add(() -> comoUtilizador(tenant, () -> tx.execute(s ->
                    controller.deleteProcesso(alheio.processoId()))));

            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<Object> t : tarefas) {
                futuros.add(executor.submit(() -> {
                    partida.await();
                    return t.call();
                }));
            }
            partida.countDown();

            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            for (Future<Object> f : futuros) {
                long resta = Math.max(1, limite - System.nanoTime());
                try {
                    f.get(resta, TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    // Recusas (404/409/503) são resultados legítimos de uma corrida; só o deadlock não.
                    falhas.add(e.getCause());
                    assertFalse(eDeadlock(e.getCause()), "deadlock na ronda " + ronda + ": " + e.getCause());
                } catch (TimeoutException e) {
                    fail("ronda " + ronda + " não terminou em 30 s (bloqueio mútuo?)");
                }
            }

            assertEquals(0, contar(SQL_ORFAOS), "documento fiscal órfão na ronda " + ronda);
            assertEquals(0, contar("SELECT count(*) FROM t_processo WHERE id = ?", alheio.processoId()));
        }
        assertEquals(0, contar(SQL_ORFAOS));
        assertTrue(falhas.stream().noneMatch(GuardasDocumentoFiscalConcorrenciaIT::eDeadlock),
                "nenhuma falha pode ser " + SQLSTATE_DEADLOCK);
    }

    // ------------------------------------------------------------------ g

    @Test
    void emissaoEmCursoBloqueiaApagarHonorario() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, "g");
        CountDownLatch emitida = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        Future<ResultadoPagamentoFaturado> a = emissaoSegura(c, emitida, libertar);
        assertTrue(emitida.await(30, TimeUnit.SECONDS));
        Future<ResponseEntity<?>> b = handler(tenant, () -> controller.deleteHonorario(c.honorarioId()));
        esperarBloqueado(b);

        libertar.countDown();
        resultado(a);
        ResponseEntity<?> resposta = resultado(b);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("HONORARIO_COM_DOCUMENTOS_FISCAIS", corpo(resposta).get("code"));
        assertEquals(1, contar("SELECT count(*) FROM t_honorario WHERE id = ?", c.honorarioId()));
        assertEquals(1, fixtura.contarDocumentos(tenant));
        assertEquals(0, contar(SQL_ORFAOS_HONORARIO));
    }

    // ------------------------------------------------------------------ h

    @Test
    void emissaoEApagarHonorarioEmSimultaneoSemOrfaos() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, "h");
        Long serieAntes = fixtura.ultimoNumero(tenant);
        CountDownLatch apagado = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        // A eliminação segura o lock do processo (e a linha do honorário apagada) até ao latch.
        Future<ResponseEntity<?>> b = executor.submit(() -> comoUtilizador(tenant, () -> tx.execute(s -> {
            ResponseEntity<?> r = controller.deleteHonorario(c.honorarioId());
            apagado.countDown();
            aguardar(libertar);
            return r;
        })));
        assertTrue(apagado.await(30, TimeUnit.SECONDS));
        // A emissão lê o honorário (ainda visível: a eliminação não fez commit), bloqueia o cliente
        // e fica à espera do lock do processo.
        Future<ResultadoPagamentoFaturado> a = executor.submit(() ->
                pagamentoFaturadoService.registar(tenant, principal(tenant), pedido(c.honorarioId())));
        esperarBloqueado(a);
        libertar.countDown();

        assertEquals(HttpStatus.NO_CONTENT, resultado(b).getStatusCode());
        ExecutionException erro = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> a.get(30, TimeUnit.SECONDS));
        RecusaFiscalException recusa = assertInstanceOf(RecusaFiscalException.class, erro.getCause());
        assertEquals(HttpStatus.NOT_FOUND, recusa.getStatus());
        assertEquals("HONORARIO_NAO_ENCONTRADO", recusa.getCodigo());

        assertEquals(0, contar("SELECT count(*) FROM t_honorario WHERE id = ?", c.honorarioId()));
        assertEquals(0, fixtura.contarPagamentos(c.honorarioId()));
        assertEquals(0, fixtura.contarDocumentos(tenant));
        assertEquals(0, fixtura.contarLinhas(tenant));
        assertEquals(0, fixtura.contarComunicacoes(tenant));
        assertEquals(serieAntes, fixtura.ultimoNumero(tenant));
        assertEquals(0, contar(SQL_ORFAOS_HONORARIO));
        assertEquals(0, contar(SQL_PAGAMENTOS_ORFAOS));
        BigDecimal saldo = fixtura.saldo(c.clienteId());
        assertTrue(saldo == null || saldo.signum() == 0, "a conta corrente não pode ter sido creditada: " + saldo);
    }

    // ------------------------------------------------------------------ i

    private Integer pagamentoLegado(Integer honorarioId, String valor) {
        return pagamentoRepository.save(Pagamento.builder().honorarioId(honorarioId)
                .valorPago(new BigDecimal(valor)).dataPagamento(java.time.LocalDate.of(2026, 6, 1))
                .metodo("Dinheiro").build()).getId();
    }

    @Test
    void apagarPagamentoLegadoDuranteEmissaoNaoPerdeOCredito() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, "i");
        fixtura.criarContaCorrente(c.clienteId(), new BigDecimal("500.00"));
        Integer legado = pagamentoLegado(c.honorarioId(), "100.00");
        CountDownLatch emitida = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        // A emissão credita +120 000 e segura o lock da conta corrente até ao latch.
        Future<ResultadoPagamentoFaturado> a = emissaoSegura(c, emitida, libertar);
        assertTrue(emitida.await(30, TimeUnit.SECONDS));
        // deletePagamento não é @Transactional (P-02): chamado sem transação, como em produção.
        Future<ResponseEntity<?>> b = executor.submit(() -> comoUtilizador(tenant,
                () -> controller.deletePagamento(legado)));
        esperarBloqueado(b);

        libertar.countDown();
        resultado(a);
        assertEquals(HttpStatus.NO_CONTENT, resultado(b).getStatusCode());

        assertEquals(0, new BigDecimal("120400.00").compareTo(fixtura.saldo(c.clienteId())),
                "saldo: " + fixtura.saldo(c.clienteId()));
    }

    // ------------------------------------------------------------------ j

    @Test
    void apagarPagamentoLegadoDuranteFusaoDebitaOClienteQueFica() throws Exception {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        UUID primario = fixtura.criarCliente(tenant, "212345678", "Primário J", "Avenida J");
        Cenario secundario = cenario(tenant, "j");
        fixtura.criarContaCorrente(primario, new BigDecimal("1000.00"));
        fixtura.criarContaCorrente(secundario.clienteId(), new BigDecimal("500.00"));
        Integer legado = pagamentoLegado(secundario.honorarioId(), "100.00");
        CountDownLatch fundida = new CountDownLatch(1);
        CountDownLatch libertar = new CountDownLatch(1);

        // A fusão bloqueia as duas contas correntes e só faz commit quando o latch abrir.
        Future<ResponseEntity<?>> fusao = executor.submit(() -> comoUtilizador(tenant, () -> tx.execute(s -> {
            ResponseEntity<?> r = controller.mergeClientes(new ClienteMergeRequest(primario, secundario.clienteId()));
            fundida.countDown();
            aguardar(libertar);
            return r;
        })));
        assertTrue(fundida.await(30, TimeUnit.SECONDS));
        Future<ResponseEntity<?>> b = executor.submit(() -> comoUtilizador(tenant,
                () -> controller.deletePagamento(legado)));
        esperarBloqueado(b);

        libertar.countDown();
        assertEquals(HttpStatus.OK, resultado(fusao).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, resultado(b).getStatusCode());

        assertNull(fixtura.saldo(secundario.clienteId()));
        assertEquals(0, new BigDecimal("1400.00").compareTo(fixtura.saldo(primario)),
                "saldo: " + fixtura.saldo(primario));
    }

    // ------------------------------------------------------------------ k

    @Test
    void repeticaoDepoisDeDesligarAFaturacaoDevolveOMesmoResultado() {
        UUID tenant = fixtura.criarTenantComFaturacao(RegimeIva.NORMAL);
        Cenario c = cenario(tenant, "k");
        PagamentoRequest req = pedido(c.honorarioId());

        ResponseEntity<?> primeira = comoUtilizador(tenant, () -> controller.createPagamento(req));
        assertEquals(HttpStatus.CREATED, primeira.getStatusCode());
        BigDecimal saldoDepoisDaEmissao = fixtura.saldo(c.clienteId());

        // O administrador desliga a faturação antes de o cliente repetir o pedido (sem resposta).
        jdbc.update("UPDATE t_configuracao_fiscal SET ativa = false WHERE tenant_id = ?", tenant);

        ResponseEntity<?> repetida = comoUtilizador(tenant, () -> controller.createPagamento(req));

        assertEquals(HttpStatus.OK, repetida.getStatusCode());
        com.lexcv.dtos.PagamentoComDocumentoResponse a =
                assertInstanceOf(com.lexcv.dtos.PagamentoComDocumentoResponse.class, primeira.getBody());
        com.lexcv.dtos.PagamentoComDocumentoResponse b =
                assertInstanceOf(com.lexcv.dtos.PagamentoComDocumentoResponse.class, repetida.getBody());
        assertEquals(a.id(), b.id());
        assertEquals(a.documentoFiscal().id(), b.documentoFiscal().id());
        assertEquals(1, fixtura.contarPagamentos(c.honorarioId()));
        assertEquals(1, fixtura.contarDocumentos(tenant));
        assertEquals(0, saldoDepoisDaEmissao.compareTo(fixtura.saldo(c.clienteId())));

        // Mesma chave com outro valor: 409, e continua sem segundo pagamento.
        PagamentoRequest diferente = new PagamentoRequest(c.honorarioId(), new BigDecimal("1.00"), null, "DINHEIRO",
                null, req.chaveIdempotencia());
        RecusaFiscalException recusa = org.junit.jupiter.api.Assertions.assertThrows(RecusaFiscalException.class,
                () -> comoUtilizador(tenant, () -> controller.createPagamento(diferente)));
        assertEquals("CHAVE_REUTILIZADA", recusa.getCodigo());
        assertEquals(1, fixtura.contarPagamentos(c.honorarioId()));
    }

    // ------------------------------------------------------------------ f

    @Test
    void faturacaoDesligadaNaoCriaLinhasFiscais() {
        UUID semConfiguracao = UUID.randomUUID();
        UUID desligada = fixtura.criarTenant(RegimeIva.NORMAL, false);

        for (UUID tenant : List.of(semConfiguracao, desligada)) {
            Cenario c = cenario(tenant, "f-" + tenant);
            ResponseEntity<?> r = comoUtilizador(tenant, () -> controller.createPagamento(
                    new PagamentoRequest(c.honorarioId(), new BigDecimal("100.00"), null, "Transferência", null, null)));

            assertEquals(HttpStatus.CREATED, r.getStatusCode());
            Pagamento pag = assertInstanceOf(Pagamento.class, r.getBody());
            assertNotNull(pag.getId());
            assertEquals(1, fixtura.contarPagamentos(c.honorarioId()));
            assertEquals(0, fixtura.contarDocumentos(tenant));
            assertEquals(0, fixtura.contarLinhas(tenant));
            assertEquals(0, fixtura.contarComunicacoes(tenant));
            assertEquals(0, contar("SELECT count(*) FROM t_serie_fiscal WHERE tenant_id = ?", tenant));
            assertNull(fixtura.ultimoNumero(tenant));
            assertEquals(0, new BigDecimal("100.00").compareTo(fixtura.saldo(c.clienteId())));
        }
    }
}
