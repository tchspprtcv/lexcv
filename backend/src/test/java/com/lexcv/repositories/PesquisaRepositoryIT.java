package com.lexcv.repositories;

import com.lexcv.models.Cliente;
import com.lexcv.models.Documento;
import com.lexcv.models.ParecerSolicitacao;
import com.lexcv.models.Processo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Prova, contra PostgreSQL real (Testcontainers), as duas garantias de mais alto risco da
 * Phase 111 (Pesquisa Global): SRCH-07 (isolamento de tenant — zero vazamento cross-tenant em
 * cada um dos 4 tipos de entidade, a garantia de maior severidade desta plataforma per
 * CLAUDE.md) e SRCH-02 (correspondências exatas/prefixo em identificadores estruturados
 * ordenadas antes de correspondências por substring), mais accent-folding e o LIMIT por tipo.
 * Réplica direta do padrão estabelecido por {@code NotificacaoRepositoryIT} (Phase 91):
 * {@code @DataJpaTest} + {@code @AutoConfigureTestDatabase(Replace.NONE)} +
 * {@code @Testcontainers} + {@code PostgreSQLContainer<>("postgres:16-alpine")}.
 *
 * {@code @DataJpaTest} cria as tabelas a partir do mapeamento das entidades (ddl-auto), mas não
 * tem equivalente para {@code CREATE EXTENSION} — sem o {@code @BeforeEach} abaixo, as chamadas a
 * {@code unaccent(...)} nas 4 queries pesquisarGlobal falhariam em runtime com
 * "function unaccent(text) does not exist" contra a base de dados fresca do contentor.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PesquisaRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private ParecerSolicitacaoRepository parecerSolicitacaoRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void habilitarExtensoesDePesquisa() {
        entityManager.createNativeQuery("CREATE EXTENSION IF NOT EXISTS unaccent").executeUpdate();
        entityManager.createNativeQuery("CREATE EXTENSION IF NOT EXISTS pg_trgm").executeUpdate();
    }

    private Cliente persistirCliente(UUID tenantId, String nome, String numeroCliente, String nif, String documentoNumero) {
        Cliente cliente = Cliente.builder()
                .tenantId(tenantId)
                .nome(nome)
                .nif(nif)
                .numeroCliente(numeroCliente)
                .documentoNumero(documentoNumero)
                .build();
        return clienteRepository.save(cliente);
    }

    private Processo persistirProcesso(UUID tenantId, UUID clienteId, String numeroProcesso, String descricao) {
        Processo processo = Processo.builder()
                .tenantId(tenantId)
                .clienteId(clienteId)
                .numeroProcesso(numeroProcesso)
                .descricao(descricao)
                .build();
        return processoRepository.save(processo);
    }

    private Documento persistirDocumento(UUID tenantId, UUID clienteId, String nome, String tipo) {
        Documento documento = Documento.builder()
                .tenantId(tenantId)
                .clienteId(clienteId)
                .nome(nome)
                .tipo(tipo)
                .build();
        return documentoRepository.save(documento);
    }

    private ParecerSolicitacao persistirParecer(UUID tenantId, UUID clienteId, String descricao) {
        ParecerSolicitacao parecer = ParecerSolicitacao.builder()
                .tenantId(tenantId)
                .clienteId(clienteId)
                .descricao(descricao)
                .build();
        return parecerSolicitacaoRepository.save(parecer);
    }

    /**
     * SRCH-07 — a garantia de maior risco desta fase. Dois tenants, cada um com um Cliente +
     * Processo + Documento + ParecerSolicitacao cujo campo pesquisável contém o mesmo token
     * distintivo; chamar pesquisarGlobal como tenant A nunca pode devolver nenhuma linha do
     * tenant B, em nenhum dos 4 tipos (4 assertions, uma por tipo, per PLAN.md).
     */
    @Test
    void pesquisarGlobal_isolaPorTenant_zeroVazamentoEmTodosOsQuatroTipos() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String token = "Zeta9k";

        Cliente clienteA = persistirCliente(tenantA, token + " Ferreira", "CLI-A001", "111222333", "DOC-A-001");
        Cliente clienteB = persistirCliente(tenantB, token + " Ferreira", "CLI-B001", "444555666", "DOC-B-001");

        Processo processoA = persistirProcesso(tenantA, clienteA.getId(), "PROC-A-001", "Contencioso " + token);
        Processo processoB = persistirProcesso(tenantB, clienteB.getId(), "PROC-B-001", "Contencioso " + token);

        Documento documentoA = persistirDocumento(tenantA, clienteA.getId(), token + " Contrato.pdf", "CONTRATO");
        Documento documentoB = persistirDocumento(tenantB, clienteB.getId(), token + " Contrato.pdf", "CONTRATO");

        ParecerSolicitacao parecerA = persistirParecer(tenantA, clienteA.getId(), "Parecer sobre " + token);
        ParecerSolicitacao parecerB = persistirParecer(tenantB, clienteB.getId(), "Parecer sobre " + token);

        List<Cliente> clientes = clienteRepository.pesquisarGlobal(tenantA, token, token, 5);
        assertEquals(1, clientes.size());
        assertEquals(clienteA.getId(), clientes.get(0).getId());
        assertEquals(tenantA, clientes.get(0).getTenantId());
        assertFalse(clientes.stream().anyMatch(c -> c.getId().equals(clienteB.getId())));

        List<Processo> processos = processoRepository.pesquisarGlobal(tenantA, token, token, 5);
        assertEquals(1, processos.size());
        assertEquals(processoA.getId(), processos.get(0).getId());
        assertEquals(tenantA, processos.get(0).getTenantId());
        assertFalse(processos.stream().anyMatch(p -> p.getId().equals(processoB.getId())));

        List<Documento> documentos = documentoRepository.pesquisarGlobal(tenantA, token, 5);
        assertEquals(1, documentos.size());
        assertEquals(documentoA.getId(), documentos.get(0).getId());
        assertEquals(tenantA, documentos.get(0).getTenantId());
        assertFalse(documentos.stream().anyMatch(d -> d.getId().equals(documentoB.getId())));

        List<ParecerSolicitacao> pareceres = parecerSolicitacaoRepository.pesquisarGlobal(tenantA, token, 5);
        assertEquals(1, pareceres.size());
        assertEquals(parecerA.getId(), pareceres.get(0).getId());
        assertEquals(tenantA, pareceres.get(0).getTenantId());
        assertFalse(pareceres.stream().anyMatch(p -> p.getId().equals(parecerB.getId())));
    }

    /**
     * SRCH-02 — correspondência exata (case-insensitive) em numero_cliente rankeia antes de
     * correspondências por substring apenas em nome, mesmo quando ambas passam pelo WHERE.
     */
    @Test
    void pesquisarGlobal_cliente_correspondenciaExataNumeroClienteRankeiaAntesDeSubstringEmNome() {
        UUID tenantId = UUID.randomUUID();
        String termo = "0042";

        Cliente exato = persistirCliente(tenantId, "Maria Silva", termo, "100200300", "DOC-EXATO-1");
        persistirCliente(tenantId, "Empresa 0042 Serviços Lda", "CLI-1000", "200300400", "DOC-SUB-1");
        persistirCliente(tenantId, "Sociedade Zero0042Zero", "CLI-1001", "300400500", "DOC-SUB-2");

        List<Cliente> resultados = clienteRepository.pesquisarGlobal(tenantId, termo, termo, 5);

        assertFalse(resultados.isEmpty());
        assertEquals(exato.getId(), resultados.get(0).getId());
    }

    /**
     * SRCH-02 — mesma garantia aplicada a Processo: correspondência exata em numero_processo
     * rankeia antes de correspondências por substring em descricao.
     */
    @Test
    void pesquisarGlobal_processo_correspondenciaExataNumeroProcessoRankeiaAntesDeSubstringEmDescricao() {
        UUID tenantId = UUID.randomUUID();
        Cliente cliente = persistirCliente(tenantId, "Cliente Processo Teste", "CLI-2000", "400500600", "DOC-PROC-1");
        String termo = "PROC-7777";

        Processo exato = persistirProcesso(tenantId, cliente.getId(), termo, "Processo de cobrança comum");
        persistirProcesso(tenantId, cliente.getId(), "PROC-0001", "Ação referente ao caso " + termo + " arquivado");
        persistirProcesso(tenantId, cliente.getId(), "PROC-0002", "Recurso " + termo + " em segunda instância");

        List<Processo> resultados = processoRepository.pesquisarGlobal(tenantId, termo, termo, 5);

        assertFalse(resultados.isEmpty());
        assertEquals(exato.getId(), resultados.get(0).getId());
    }

    /**
     * Accent-folding — unaccent() aplicado a ambos os lados (coluna e termo) garante que uma
     * pesquisa sem diacríticos ("Conceicao") encontra um nome guardado com diacríticos
     * ("Conceição").
     */
    @Test
    void pesquisarGlobal_cliente_ignoraDiacriticos_ConceicaoEncontraNomeComCedilha() {
        UUID tenantId = UUID.randomUUID();
        Cliente conceicao = persistirCliente(tenantId, "Maria da Conceição", "CLI-3000", "500600700", "DOC-ACC-1");

        List<Cliente> resultados = clienteRepository.pesquisarGlobal(tenantId, "Conceicao", "Conceicao", 5);

        assertEquals(1, resultados.size());
        assertEquals(conceicao.getId(), resultados.get(0).getId());
    }

    /**
     * CONTEXT.md: no máximo 5 resultados por tipo de entidade — 7 Clientes coincidentes devem
     * devolver exatamente 5.
     */
    @Test
    void pesquisarGlobal_cliente_respeitaLimiteDeCincoResultados() {
        UUID tenantId = UUID.randomUUID();
        String termo = "Alfa7x";
        for (int i = 0; i < 7; i++) {
            persistirCliente(tenantId, "Cliente " + termo + " " + i, "CLI-40" + i, "60070080" + i, "DOC-LIM-" + i);
        }

        List<Cliente> resultados = clienteRepository.pesquisarGlobal(tenantId, termo, termo, 5);

        assertEquals(5, resultados.size());
    }

    /**
     * WR-02 (re-review): none of the tests above use a termo containing '%', '_', or '\\', so
     * they cannot distinguish a correctly-escaped {@code ILIKE ... ESCAPE '\'} implementation
     * from a subtly broken one (e.g. a dropped {@code ESCAPE} clause, or
     * {@code PesquisaController#escapeLike} no longer being applied before the repository
     * call reaches this query) — raw and escaped values are identical whenever the input has
     * no wildcard metacharacters, which is all the coverage above exercises. Seeds, per entity
     * type, a "decoy" row that would ONLY be matched if '_' were interpreted as ILIKE's
     * built-in "any single character" wildcard, alongside a target row that genuinely contains
     * the literal underscore. Cross-entity shape mirrors
     * pesquisarGlobal_isolaPorTenant_zeroVazamentoEmTodosOsQuatroTipos above.
     */
    @Test
    void pesquisarGlobal_termoComUnderscoreLiteral_naoAgeComoCoringaDeUmCaractereEmNenhumDosQuatroTipos() {
        UUID tenantId = UUID.randomUUID();
        String termo = "ESC_9k";
        String termoEscapado = "ESC\\_9k"; // mirrors PesquisaController#escapeLike("ESC_9k")

        Cliente clienteAncora = persistirCliente(tenantId, "Cliente Ancora", "CLI-U-ANC", "700800909", "DOC-U-ANC");

        // 'X' stands in for the position a bare (unescaped) '_' would treat as "any single
        // character" — under correct escaping this decoy must NOT match a search for "ESC_9k".
        Cliente clienteDecoy = persistirCliente(tenantId, "Cliente Decoy", "ESCX9k", "700800900", "DOC-U-C1");
        Cliente clienteAlvo = persistirCliente(tenantId, "Cliente Alvo", "ESC_9k", "700800901", "DOC-U-C2");

        Processo processoDecoy = persistirProcesso(tenantId, clienteAncora.getId(), "ESCX9k", "Processo comum");
        Processo processoAlvo = persistirProcesso(tenantId, clienteAncora.getId(), "ESC_9k", "Processo comum");

        Documento documentoDecoy = persistirDocumento(tenantId, clienteAncora.getId(), "ESCX9k.pdf", "CONTRATO");
        Documento documentoAlvo = persistirDocumento(tenantId, clienteAncora.getId(), "ESC_9k.pdf", "CONTRATO");

        ParecerSolicitacao parecerDecoy = persistirParecer(tenantId, clienteAncora.getId(), "Parecer ESCX9k final");
        ParecerSolicitacao parecerAlvo = persistirParecer(tenantId, clienteAncora.getId(), "Parecer ESC_9k final");

        List<Cliente> clientes = clienteRepository.pesquisarGlobal(tenantId, termo, termoEscapado, 5);
        assertEquals(1, clientes.size());
        assertEquals(clienteAlvo.getId(), clientes.get(0).getId());
        assertFalse(clientes.stream().anyMatch(c -> c.getId().equals(clienteDecoy.getId())));

        List<Processo> processos = processoRepository.pesquisarGlobal(tenantId, termo, termoEscapado, 5);
        assertEquals(1, processos.size());
        assertEquals(processoAlvo.getId(), processos.get(0).getId());
        assertFalse(processos.stream().anyMatch(p -> p.getId().equals(processoDecoy.getId())));

        List<Documento> documentos = documentoRepository.pesquisarGlobal(tenantId, termoEscapado, 5);
        assertEquals(1, documentos.size());
        assertEquals(documentoAlvo.getId(), documentos.get(0).getId());
        assertFalse(documentos.stream().anyMatch(d -> d.getId().equals(documentoDecoy.getId())));

        List<ParecerSolicitacao> pareceres = parecerSolicitacaoRepository.pesquisarGlobal(tenantId, termoEscapado, 5);
        assertEquals(1, pareceres.size());
        assertEquals(parecerAlvo.getId(), pareceres.get(0).getId());
        assertFalse(pareceres.stream().anyMatch(p -> p.getId().equals(parecerDecoy.getId())));
    }

    /**
     * WR-02 (re-review): proves the tier-0 exact-match {@code CASE WHEN} genuinely needs the
     * raw, unescaped {@code :termo} for its equality check, not {@code :termoEscapado}.
     * numero_cliente stores the literal value "100%" — a '%' character as plain data, not a
     * pattern. If tier-0 ever compared against {@code :termoEscapado} instead ("100\%"), the
     * equality would never hold (the escaped value's literal backslash never appears in stored
     * data), silently demoting 'exato' to the tier-1 prefix branch — where it ties with
     * 'prefixo' (whose numero_cliente also starts with "100%") on tier, and the created_at
     * DESC tie-break would then put the later-created 'prefixo' first, flipping
     * resultados.get(0) away from 'exato'. Under the current, correct implementation, 'exato'
     * is tier 0 and 'prefixo' is tier 1, so 'exato' sorts first regardless of creation order.
     */
    @Test
    void pesquisarGlobal_cliente_correspondenciaExataComPercentLiteral_usaTermoBrutoNaoEscapadoNoCaseWhen() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        String termo = "100%";
        String termoEscapado = "100\\%"; // mirrors PesquisaController#escapeLike("100%")

        Cliente exato = persistirCliente(tenantId, "Maria Silva", termo, "100200300", "DOC-PCT-EXATO");
        // Guarantees a strictly later created_at for 'prefixo' regardless of clock resolution,
        // so the created_at DESC tie-break this test relies on (see Javadoc above) is
        // deterministic rather than flaky.
        Thread.sleep(5);
        Cliente prefixo = persistirCliente(tenantId, "Prefixo Decoy", termo + "EXTRA", "100200301", "DOC-PCT-PREFIXO");

        List<Cliente> resultados = clienteRepository.pesquisarGlobal(tenantId, termo, termoEscapado, 5);

        assertEquals(2, resultados.size());
        assertEquals(exato.getId(), resultados.get(0).getId());
    }
}
