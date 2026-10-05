package com.lexcv.repositories;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.TargetDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 136 (DFE-02, DFE-04, DFE-06; T-136-04, T-136-05, T-136-07): prova, contra PostgreSQL real
 * (Testcontainers), que o script manual {@code backend/migrations/136-efatura-comunicacao.sql},
 * aplicado sobre 134 + 135 (e outra vez), deixa {@code t_comunicacao_fiscal} e
 * {@code t_documento_fiscal_xml} iguais ao esquema do Hibernate, que o CHECK
 * {@code ck_comunicacao_fiscal_autorizado_producao} existe nos dois esquemas com a mesma definição e
 * recusa {@code AUTORIZADO} fora de {@code PRODUCAO} (SQLState 23514), que linhas da era 134 ficam
 * intactas com {@code reprocessamentos = 0}, que o satélite XML recusa segundo documento e IUD
 * repetido, e que um segundo arranque em {@code ddl-auto=update} não emite DDL.
 *
 * <p>Mesmo andaime de {@code MigracaoFiscal135IT}: o Hibernate cria as tabelas no schema
 * {@code public}; os scripts correm em schemas de rascunho (um por teste, apagado no fim). Sem
 * transação de teste, porque um INSERT/UPDATE recusado abortaria a transação partilhada.
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.integrator_provider=com.lexcv.repositories.CapturaMetadataHibernate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MigracaoFiscal136IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SCRIPT_134 = "migrations/134-create-documento-fiscal-tables.sql";
    private static final String SCRIPT_135 = "migrations/135-add-nota-credito-documento-fiscal.sql";
    private static final String SCRIPT_136 = "migrations/136-efatura-comunicacao.sql";
    private static final List<String> TABELAS = List.of("t_comunicacao_fiscal", "t_documento_fiscal_xml");
    private static final String CHECK = "ck_comunicacao_fiscal_autorizado_producao";
    private static final String IUD_A = "CV3260105512345679999990200000000112345678904";
    private static final String IUD_B = "CV3260105512345679999990200000000298765432105";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DocumentoFiscalXmlRepository xmlRepository;

    private static String lerScript(String caminho) {
        try {
            return Files.readString(Path.of(caminho));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void aplicar(String schema, String caminho) {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        // Uma única chamada: SET + script + reset correm na mesma ligação.
        jdbcTemplate.execute("SET search_path TO " + schema + ";\n" + lerScript(caminho) + "\nSET search_path TO public;");
    }

    private void aplicarTodos(String schema) {
        aplicar(schema, SCRIPT_134);
        aplicar(schema, SCRIPT_135);
        aplicar(schema, SCRIPT_136);
    }

    private void apagar(String schema) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    private Set<String> colunas(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision, "
                        + "numeric_scale, datetime_precision, is_nullable, column_default "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name IN (?, ?)",
                (rs, i) -> String.join("|",
                        rs.getString("table_name"), rs.getString("column_name"), rs.getString("data_type"),
                        String.valueOf(rs.getObject("character_maximum_length")),
                        String.valueOf(rs.getObject("numeric_precision")),
                        String.valueOf(rs.getObject("numeric_scale")),
                        String.valueOf(rs.getObject("datetime_precision")),
                        rs.getString("is_nullable"),
                        String.valueOf(rs.getString("column_default"))),
                schema, TABELAS.get(0), TABELAS.get(1)));
    }

    private Map<String, Set<String>> unicas(String schema) {
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                "SELECT tc.table_name, tc.constraint_name, "
                        + "string_agg(kcu.column_name, ',' ORDER BY kcu.ordinal_position) AS cols "
                        + "FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name "
                        + " AND kcu.constraint_schema = tc.constraint_schema AND kcu.table_name = tc.table_name "
                        + "WHERE tc.constraint_type = 'UNIQUE' AND tc.table_schema = ? AND tc.table_name IN (?, ?) "
                        + "GROUP BY tc.table_name, tc.constraint_name",
                schema, TABELAS.get(0), TABELAS.get(1));
        Map<String, Set<String>> porTabela = new TreeMap<>();
        for (Map<String, Object> l : linhas) {
            porTabela.computeIfAbsent((String) l.get("table_name"), k -> new HashSet<>())
                    .add(l.get("constraint_name") + ":" + l.get("cols"));
        }
        return porTabela;
    }

    private Set<String> indices(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT tablename, indexname, indexdef FROM pg_indexes "
                        + "WHERE schemaname = ? AND tablename IN (?, ?)",
                (rs, i) -> rs.getString("tablename") + "|" + rs.getString("indexname") + "|"
                        + rs.getString("indexdef").replace(schema + ".", ""),
                schema, TABELAS.get(0), TABELAS.get(1)));
    }

    /** Nome e definição normalizada ({@code pg_get_constraintdef}) de cada CHECK das duas tabelas. */
    private Set<String> checks(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT c.relname, con.conname, pg_get_constraintdef(con.oid) AS def "
                        + "FROM pg_constraint con JOIN pg_class c ON c.oid = con.conrelid "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE con.contype = 'c' AND n.nspname = ? AND c.relname IN (?, ?)",
                (rs, i) -> rs.getString("relname") + "|" + rs.getString("conname") + "|" + rs.getString("def"),
                schema, TABELAS.get(0), TABELAS.get(1)));
    }

    /** Linha de comunicação só com as colunas da Phase 134 (como a emissão a cria). */
    private UUID inserirComunicacao134(String schema, String ambiente, String estado) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + schema + ".t_comunicacao_fiscal (id, tenant_id, documento_fiscal_id, "
                        + "ambiente, estado, tentativas, proxima_tentativa_em, created_at, updated_at, versao) "
                        + "VALUES (?, ?, ?, ?, ?, 0, NULL, TIMESTAMP WITH TIME ZONE '2026-03-01 10:00:00+00', NULL, 0)",
                id, UUID.randomUUID(), UUID.randomUUID(), ambiente, estado);
        return id;
    }

    private void inserirXml(String schema, UUID documentoId, String iud) {
        jdbcTemplate.update("INSERT INTO " + schema + ".t_documento_fiscal_xml (id, tenant_id, documento_fiscal_id, "
                        + "iud, ambiente, repositorio_codigo, led_codigo, versao_formato, xml, xml_sha256, gerado_em) "
                        + "VALUES (?, ?, ?, ?, 'SIMULADO', 3, 99999, '2024-05-27', '<Dfe/>', ?, now())",
                UUID.randomUUID(), UUID.randomUUID(), documentoId, iud, "a".repeat(64));
    }

    private static String sqlState(DataAccessException e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }

    // (a) 134 + 135 + 136, e o 136 outra vez, == Hibernate
    @Test
    void aplicadoDuasVezesSobre134E135FicaIgualAoHibernate() {
        String schema = "migracao_136_idem";
        try {
            aplicarTodos(schema);
            Set<String> primeira = colunas(schema);
            Set<String> indicesPrimeira = indices(schema);
            Set<String> checksPrimeira = checks(schema);
            assertDoesNotThrow(() -> aplicar(schema, SCRIPT_136));

            assertEquals(primeira, colunas(schema));
            assertEquals(indicesPrimeira, indices(schema));
            assertEquals(checksPrimeira, checks(schema));

            Set<String> hibernate = colunas("public");
            // t_comunicacao_fiscal: 10 (Phase 134) + 6 (Phase 136); t_documento_fiscal_xml: 11.
            assertEquals(16 + 11, hibernate.size(), "colunas Hibernate: " + hibernate);
            assertEquals(hibernate, colunas(schema));

            Map<String, Set<String>> unicasHibernate = unicas("public");
            assertEquals(Map.of(
                    "t_comunicacao_fiscal", Set.of("uk_comunicacao_fiscal_documento:documento_fiscal_id"),
                    "t_documento_fiscal_xml", Set.of(
                            "uk_documento_fiscal_xml_documento:documento_fiscal_id",
                            "uk_documento_fiscal_xml_iud:iud")), unicasHibernate);
            assertEquals(unicasHibernate, unicas(schema));

            Set<String> indicesHibernate = indices("public");
            assertTrue(indicesHibernate.stream().anyMatch(i -> i.contains("idx_comunicacao_fiscal_estado_proxima")),
                    "índices Hibernate: " + indicesHibernate);
            assertEquals(indicesHibernate, indices(schema));
        } finally {
            apagar(schema);
        }
    }

    // (b) o CHECK existe nos dois esquemas com a mesma definição normalizada
    @Test
    void checkAutorizadoProducaoIgualNoHibernateENoScript() {
        String schema = "migracao_136_check";
        try {
            aplicarTodos(schema);
            Set<String> hibernate = checks("public");
            assertEquals(1, hibernate.size(), "CHECK Hibernate: " + hibernate);
            assertTrue(hibernate.iterator().next().startsWith("t_comunicacao_fiscal|" + CHECK + "|"), hibernate.toString());
            assertTrue(hibernate.iterator().next().contains("AUTORIZADO")
                    && hibernate.iterator().next().contains("PRODUCAO"), hibernate.toString());
            assertEquals(hibernate, checks(schema));
        } finally {
            apagar(schema);
        }
    }

    // (c) AUTORIZADO fora de PRODUCAO é recusado pela base de dados (23514)
    @Test
    void autorizadoForaDeProducaoERecusadoComCheckViolation() {
        String schema = "migracao_136_autorizado";
        try {
            aplicarTodos(schema);
            UUID id = inserirComunicacao134(schema, "SIMULADO", "PENDENTE");

            DataAccessException erro = assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "UPDATE " + schema + ".t_comunicacao_fiscal SET estado = 'AUTORIZADO' WHERE id = ?", id));
            assertEquals("23514", sqlState(erro), String.valueOf(erro.getMostSpecificCause().getMessage()));
            assertTrue(String.valueOf(erro.getMostSpecificCause().getMessage()).contains(CHECK),
                    erro.getMostSpecificCause().getMessage());
            // Também na inserção direta.
            DataAccessException erroInsert = assertThrows(DataAccessException.class,
                    () -> inserirComunicacao134(schema, "SIMULADO", "AUTORIZADO"));
            assertEquals("23514", sqlState(erroInsert));

            assertEquals(1, jdbcTemplate.update(
                    "UPDATE " + schema + ".t_comunicacao_fiscal SET estado = 'ACEITE_SIMULADO' WHERE id = ?", id));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE " + schema + ".t_comunicacao_fiscal SET estado = 'ERRO' WHERE id = ?", id));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE " + schema + ".t_comunicacao_fiscal SET estado = 'REJEITADO' WHERE id = ?", id));
            // A regra é exata: só PRODUCAO permite AUTORIZADO (nenhum dos dois existe em Java).
            assertDoesNotThrow(() -> inserirComunicacao134(schema, "PRODUCAO", "AUTORIZADO"));

            // O mesmo no esquema do Hibernate (public).
            UUID publico = inserirComunicacao134("public", "SIMULADO", "PENDENTE");
            try {
                DataAccessException erroPublico = assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                        "UPDATE public.t_comunicacao_fiscal SET estado = 'AUTORIZADO' WHERE id = ?", publico));
                assertEquals("23514", sqlState(erroPublico));
            } finally {
                jdbcTemplate.update("DELETE FROM public.t_comunicacao_fiscal WHERE id = ?", publico);
            }
        } finally {
            apagar(schema);
        }
    }

    // (d) aplicado sobre linhas da era 134: mantém-nas e põe reprocessamentos = 0
    @Test
    void aplicadoSobreComunicacoesExistentesMantemAsLinhasEFazBackfill() {
        String schema = "migracao_136_dados";
        try {
            aplicar(schema, SCRIPT_134);
            aplicar(schema, SCRIPT_135);
            UUID id = inserirComunicacao134(schema, "SIMULADO", "PENDENTE");
            Map<String, Object> antes = new HashMap<>(jdbcTemplate.queryForMap(
                    "SELECT * FROM " + schema + ".t_comunicacao_fiscal WHERE id = ?", id));

            aplicar(schema, SCRIPT_136);

            Map<String, Object> depois = new HashMap<>(jdbcTemplate.queryForMap(
                    "SELECT * FROM " + schema + ".t_comunicacao_fiscal WHERE id = ?", id));
            assertEquals(0, ((Number) depois.get("reprocessamentos")).intValue());
            for (String c : List.of("lease_ate", "ultima_tentativa_em", "ultimo_erro", "ultimo_erro_codigo",
                    "concluido_em")) {
                assertTrue(depois.containsKey(c), c);
                assertNull(depois.get(c), "linha existente fica com " + c + " NULL");
            }
            Map<String, Object> originais = new HashMap<>(depois);
            List.of("reprocessamentos", "lease_ate", "ultima_tentativa_em", "ultimo_erro", "ultimo_erro_codigo",
                    "concluido_em").forEach(originais::remove);
            assertEquals(antes, originais, "colunas originais inalteradas");
            assertTrue(checks(schema).stream().anyMatch(c -> c.contains(CHECK)), "CHECK criado sobre tabela povoada");
        } finally {
            apagar(schema);
        }
    }

    // (e) satélite XML: no máximo uma linha por documento e IUD único
    @Test
    void xmlRecusaSegundoDocumentoEIudRepetido() {
        String schema = "migracao_136_xml";
        try {
            aplicarTodos(schema);
            UUID documento = UUID.randomUUID();
            inserirXml(schema, documento, IUD_A);

            DataIntegrityViolationException mesmoDocumento = assertThrows(DataIntegrityViolationException.class,
                    () -> inserirXml(schema, documento, IUD_B));
            assertTrue(String.valueOf(mesmoDocumento.getMostSpecificCause().getMessage())
                    .contains("uk_documento_fiscal_xml_documento"), mesmoDocumento.getMostSpecificCause().getMessage());

            DataIntegrityViolationException mesmoIud = assertThrows(DataIntegrityViolationException.class,
                    () -> inserirXml(schema, UUID.randomUUID(), IUD_A));
            assertTrue(String.valueOf(mesmoIud.getMostSpecificCause().getMessage())
                    .contains("uk_documento_fiscal_xml_iud"), mesmoIud.getMostSpecificCause().getMessage());

            // IUD acima de 45 caracteres recusado.
            assertThrows(DataAccessException.class, () -> inserirXml(schema, UUID.randomUUID(), IUD_B + "0"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".t_documento_fiscal_xml", Integer.class));
        } finally {
            apagar(schema);
        }
    }

    // (e') o repositório insert-only: 1 na primeira vez, 0 em conflito; finder por tenant
    @Test
    void inserirSeAusenteIgnoraConflitosEOFinderEPorTenant() {
        UUID tenant = UUID.randomUUID();
        UUID outroTenant = UUID.randomUUID();
        UUID documento = UUID.randomUUID();
        Instant gerado = Instant.parse("2026-10-05T10:00:00Z");
        try {
            assertEquals(1, xmlRepository.inserirSeAusente(UUID.randomUUID(), tenant, documento, IUD_A,
                    AmbienteFiscal.SIMULADO.name(), 3, 99999, "2024-05-27", "<Dfe/>", "b".repeat(64), gerado));
            // Mesmo documento (outro IUD) e mesmo IUD (outro documento): ignorados, sem exceção.
            assertEquals(0, xmlRepository.inserirSeAusente(UUID.randomUUID(), tenant, documento, IUD_B,
                    AmbienteFiscal.SIMULADO.name(), 3, 99999, "2024-05-27", "<Outro/>", "c".repeat(64), gerado));
            assertEquals(0, xmlRepository.inserirSeAusente(UUID.randomUUID(), tenant, UUID.randomUUID(), IUD_A,
                    AmbienteFiscal.SIMULADO.name(), 3, 99999, "2024-05-27", "<Outro/>", "c".repeat(64), gerado));

            Optional<DocumentoFiscalXml> lido = xmlRepository.findByTenantIdAndDocumentoFiscalId(tenant, documento);
            assertTrue(lido.isPresent());
            assertEquals(IUD_A, lido.get().getIud());
            assertEquals("<Dfe/>", lido.get().getXml());
            assertEquals(AmbienteFiscal.SIMULADO, lido.get().getAmbiente());
            assertEquals(3, lido.get().getRepositorioCodigo());
            assertEquals(99999, lido.get().getLedCodigo());
            assertEquals("2024-05-27", lido.get().getVersaoFormato());
            assertEquals("b".repeat(64), lido.get().getXmlSha256());
            assertEquals(gerado, lido.get().getGeradoEm().truncatedTo(ChronoUnit.SECONDS));
            assertTrue(xmlRepository.findByTenantIdAndDocumentoFiscalId(outroTenant, documento).isEmpty(),
                    "outro tenant não vê o XML");
        } finally {
            jdbcTemplate.update("DELETE FROM public.t_documento_fiscal_xml WHERE tenant_id = ?", tenant);
        }
    }

    // (f) o XML é text nos dois esquemas
    @Test
    void colunaXmlETextNosDoisEsquemas() {
        String schema = "migracao_136_tipos";
        try {
            aplicarTodos(schema);
            for (String s : List.of("public", schema)) {
                assertEquals("text", jdbcTemplate.queryForObject(
                        "SELECT data_type FROM information_schema.columns WHERE table_schema = ? "
                                + "AND table_name = 't_documento_fiscal_xml' AND column_name = 'xml'",
                        String.class, s), s);
                assertTrue(colunas(s).contains("t_comunicacao_fiscal|reprocessamentos|integer|null|32|0|null|NO|0"),
                        s + ": " + colunas(s));
            }
        } finally {
            apagar(schema);
        }
    }

    // (g) um segundo arranque em update não emite DDL para as duas tabelas
    @Test
    void segundoArranqueEmUpdateNaoEmiteDdl() {
        Metadata metadata = CapturaMetadataHibernate.metadata();
        SessionFactoryImplementor sessionFactory = CapturaMetadataHibernate.sessionFactory();
        assertNotNull(metadata, "integrator_provider não capturou o Metadata");
        assertNotNull(sessionFactory, "integrator_provider não capturou a SessionFactory");

        Map<String, Object> settings = new HashMap<>(sessionFactory.getProperties());
        List<String> ddl = new ArrayList<>();
        ScriptTargetOutput saida = new ScriptTargetOutput() {
            @Override
            public void prepare() {
                // nada
            }

            @Override
            public void accept(String comando) {
                ddl.add(comando);
            }

            @Override
            public void release() {
                // nada
            }
        };
        TargetDescriptor alvo = new TargetDescriptor() {
            @Override
            public EnumSet<TargetType> getTargetTypes() {
                return EnumSet.of(TargetType.SCRIPT);
            }

            @Override
            public ScriptTargetOutput getScriptTargetOutput() {
                return saida;
            }
        };

        sessionFactory.getServiceRegistry().requireService(SchemaManagementTool.class)
                .getSchemaMigrator(settings)
                .doMigration(metadata,
                        SchemaManagementToolCoordinator.buildExecutionOptions(settings, ExceptionHandlerHaltImpl.INSTANCE),
                        ContributableMatcher.ALL, alvo);

        List<String> fiscais = ddl.stream()
                .filter(c -> TABELAS.stream().anyMatch(t -> c.toLowerCase().contains(t)))
                .toList();
        assertEquals(List.of(), fiscais, "DDL emitido no segundo arranque: " + fiscais);
    }
}
