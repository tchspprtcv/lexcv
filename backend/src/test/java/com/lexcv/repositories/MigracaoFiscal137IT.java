package com.lexcv.repositories;

import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.TargetDescriptor;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 137 (ENTR-01, ENTR-03, ENTR-04; T-137-12, T-137-13): prova, contra PostgreSQL real
 * (Testcontainers), que o script manual {@code backend/migrations/137-entrega-documento-fiscal.sql},
 * aplicado sobre 133..136 (e outra vez), deixa {@code t_entrega_email_fiscal} e
 * {@code t_documento_fiscal_pdf} iguais ao esquema do Hibernate (colunas, chaves únicas, índices),
 * que as chaves únicas recusam duplicados (SQLState 23505), que {@code reenvios} tem
 * {@code DEFAULT 0}, que o script não cria CHECK nem chaves estrangeiras, e que um segundo
 * arranque em {@code ddl-auto=update} sobre o esquema migrado não emite DDL.
 *
 * <p>Mesmo andaime de {@code MigracaoFiscal136IT}: o Hibernate cria as tabelas no schema
 * {@code public}; os scripts correm em schemas de rascunho (um por teste, apagado no fim). Sem
 * transação de teste, porque um INSERT recusado abortaria a transação partilhada.
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.integrator_provider=com.lexcv.repositories.CapturaMetadataHibernate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigracaoFiscal137IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SCRIPT_133 = "migrations/133-create-fiscal-foundation-tables.sql";
    private static final String SCRIPT_134 = "migrations/134-create-documento-fiscal-tables.sql";
    private static final String SCRIPT_135 = "migrations/135-add-nota-credito-documento-fiscal.sql";
    private static final String SCRIPT_136 = "migrations/136-efatura-comunicacao.sql";
    private static final String SCRIPT_137 = "migrations/137-entrega-documento-fiscal.sql";
    private static final List<String> TABELAS = List.of("t_entrega_email_fiscal", "t_documento_fiscal_pdf");

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        aplicar(schema, SCRIPT_133);
        aplicar(schema, SCRIPT_134);
        aplicar(schema, SCRIPT_135);
        aplicar(schema, SCRIPT_136);
        aplicar(schema, SCRIPT_137);
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

    /** Tipo e nome de cada restrição das duas tabelas que não seja PK/UNIQUE (CHECK, FK, ...). */
    private Set<String> outrasRestricoes(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT c.relname, con.conname, con.contype FROM pg_constraint con "
                        + "JOIN pg_class c ON c.oid = con.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE con.contype NOT IN ('p', 'u') AND n.nspname = ? AND c.relname IN (?, ?)",
                (rs, i) -> rs.getString("relname") + "|" + rs.getString("conname") + "|" + rs.getString("contype"),
                schema, TABELAS.get(0), TABELAS.get(1)));
    }

    /** Linha de entrega sem {@code reenvios} (para provar o DEFAULT 0). */
    private void inserirEntrega(String schema, UUID documentoId) {
        jdbcTemplate.update("INSERT INTO " + schema + ".t_entrega_email_fiscal (id, tenant_id, documento_fiscal_id, "
                        + "estado, destinatario, tentativas, created_at, versao) "
                        + "VALUES (?, ?, ?, 'PENDENTE', 'cliente@example.cv', 0, now(), 0)",
                UUID.randomUUID(), UUID.randomUUID(), documentoId);
    }

    private void inserirPdf(String schema, UUID documentoId, String objectKey) {
        jdbcTemplate.update("INSERT INTO " + schema + ".t_documento_fiscal_pdf (id, tenant_id, documento_fiscal_id, "
                        + "object_key, sha256, tamanho_bytes, versao_modelo, gerado_em) "
                        + "VALUES (?, ?, ?, ?, ?, 1024, '1', now())",
                UUID.randomUUID(), UUID.randomUUID(), documentoId, objectKey, "a".repeat(64));
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

    // (a) 133..136 + 137, e o 137 outra vez, == Hibernate (colunas)
    @Test
    void aplicadoDuasVezesSobre133a136FicaIgualAoHibernate() {
        String schema = "migracao_137_idem";
        try {
            aplicarTodos(schema);
            Set<String> primeira = colunas(schema);
            Set<String> indicesPrimeira = indices(schema);
            Map<String, Set<String>> unicasPrimeira = unicas(schema);
            assertDoesNotThrow(() -> aplicar(schema, SCRIPT_137));

            assertEquals(primeira, colunas(schema));
            assertEquals(indicesPrimeira, indices(schema));
            assertEquals(unicasPrimeira, unicas(schema));

            Set<String> hibernate = colunas("public");
            // t_entrega_email_fiscal: 16 colunas; t_documento_fiscal_pdf: 8.
            assertEquals(16 + 8, hibernate.size(), "colunas Hibernate: " + hibernate);
            assertEquals(hibernate, colunas(schema));
        } finally {
            apagar(schema);
        }
    }

    // (b) mesmas chaves únicas e índices (por nome e definição) nos dois esquemas; sem CHECK nem FK
    @Test
    void chavesUnicasEIndicesIguaisAoHibernate() {
        String schema = "migracao_137_indices";
        try {
            aplicarTodos(schema);
            Map<String, Set<String>> unicasHibernate = unicas("public");
            assertEquals(Map.of(
                    "t_entrega_email_fiscal", Set.of("uk_entrega_email_fiscal_documento:documento_fiscal_id"),
                    "t_documento_fiscal_pdf", Set.of(
                            "uk_documento_fiscal_pdf_documento:documento_fiscal_id",
                            "uk_documento_fiscal_pdf_object_key:object_key")), unicasHibernate);
            assertEquals(unicasHibernate, unicas(schema));

            Set<String> indicesHibernate = indices("public");
            for (String nome : List.of("idx_entrega_email_fiscal_estado_proxima",
                    "idx_entrega_email_fiscal_tenant_estado")) {
                assertTrue(indicesHibernate.stream().anyMatch(i -> i.contains("|" + nome + "|")),
                        nome + " em " + indicesHibernate);
            }
            assertEquals(indicesHibernate, indices(schema));

            assertEquals(Set.of(), outrasRestricoes("public"));
            assertEquals(Set.of(), outrasRestricoes(schema));
        } finally {
            apagar(schema);
        }
    }

    // (c) segunda entrega para o mesmo documento: 23505
    @Test
    void entregaRecusaSegundaLinhaParaOMesmoDocumento() {
        String schema = "migracao_137_entrega";
        try {
            aplicarTodos(schema);
            UUID documento = UUID.randomUUID();
            inserirEntrega(schema, documento);

            DataAccessException erro = assertThrows(DataAccessException.class, () -> inserirEntrega(schema, documento));
            assertEquals("23505", sqlState(erro), String.valueOf(erro.getMostSpecificCause().getMessage()));
            assertTrue(String.valueOf(erro.getMostSpecificCause().getMessage())
                    .contains("uk_entrega_email_fiscal_documento"), erro.getMostSpecificCause().getMessage());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".t_entrega_email_fiscal", Integer.class));
        } finally {
            apagar(schema);
        }
    }

    // (d) PDF: segunda linha para o mesmo documento e object_key repetida: 23505
    @Test
    void pdfRecusaSegundoDocumentoEObjectKeyRepetida() {
        String schema = "migracao_137_pdf";
        try {
            aplicarTodos(schema);
            UUID documento = UUID.randomUUID();
            inserirPdf(schema, documento, "tenant/fiscal/a.pdf");

            DataAccessException mesmoDocumento = assertThrows(DataAccessException.class,
                    () -> inserirPdf(schema, documento, "tenant/fiscal/b.pdf"));
            assertEquals("23505", sqlState(mesmoDocumento));
            assertTrue(String.valueOf(mesmoDocumento.getMostSpecificCause().getMessage())
                    .contains("uk_documento_fiscal_pdf_documento"), mesmoDocumento.getMostSpecificCause().getMessage());

            DataAccessException mesmaChave = assertThrows(DataAccessException.class,
                    () -> inserirPdf(schema, UUID.randomUUID(), "tenant/fiscal/a.pdf"));
            assertEquals("23505", sqlState(mesmaChave));
            assertTrue(String.valueOf(mesmaChave.getMostSpecificCause().getMessage())
                    .contains("uk_documento_fiscal_pdf_object_key"), mesmaChave.getMostSpecificCause().getMessage());

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schema + ".t_documento_fiscal_pdf", Integer.class));
        } finally {
            apagar(schema);
        }
    }

    // (e) reenvios omitido fica 0, nos dois esquemas
    @Test
    void reenviosOmitidoFicaZero() {
        String schema = "migracao_137_default";
        try {
            aplicarTodos(schema);
            UUID documento = UUID.randomUUID();
            inserirEntrega(schema, documento);
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT reenvios FROM " + schema + ".t_entrega_email_fiscal WHERE documento_fiscal_id = ?",
                    Integer.class, documento));
            for (String s : List.of("public", schema)) {
                assertTrue(colunas(s).contains("t_entrega_email_fiscal|reenvios|integer|null|32|0|null|NO|0"),
                        s + ": " + colunas(s));
            }
        } finally {
            apagar(schema);
        }
    }

    // (f) segundo arranque em update sobre as tabelas criadas pelo script não emite DDL.
    // Corre por último: troca as tabelas de public pelas do script, e (a)/(b) comparam com public.
    @Test
    @Order(Integer.MAX_VALUE)
    void segundoArranqueEmUpdateNaoEmiteDdl() {
        Metadata metadata = CapturaMetadataHibernate.metadata();
        SessionFactoryImplementor sessionFactory = CapturaMetadataHibernate.sessionFactory();
        assertNotNull(metadata, "integrator_provider não capturou o Metadata");
        assertNotNull(sessionFactory, "integrator_provider não capturou a SessionFactory");

        // Substitui as tabelas criadas pelo Hibernate em public pelas do script (vazias, ninguém
        // mais as usa nesta classe) e só depois corre o migrador do Hibernate.
        jdbcTemplate.execute("DROP TABLE public.t_entrega_email_fiscal");
        jdbcTemplate.execute("DROP TABLE public.t_documento_fiscal_pdf");
        aplicar("public", SCRIPT_137);

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
