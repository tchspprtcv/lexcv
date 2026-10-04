package com.lexcv.repositories;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.SerieFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 133 (T-133-04): prova, contra PostgreSQL real (Testcontainers), que o script manual
 * {@code backend/migrations/133-create-fiscal-foundation-tables.sql} cria exatamente o esquema
 * que o Hibernate cria a partir das entidades (coluna a coluna, e o mesmo conjunto de
 * constraints UNIQUE), que é idempotente, que o Hibernate não gera CHECK nas colunas de enum, e
 * que as chaves únicas são por tenant (T-133-02).
 *
 * <p>O Hibernate cria as tabelas no schema {@code public} ({@code ddl-auto=create-drop} em
 * {@code src/test/resources/application.properties}); o script corre num schema separado
 * {@code migracao_133} via {@code search_path}, e os dois são comparados por
 * {@code information_schema}. Mesmo andaime de {@code ParecerVersaoConcorrenciaIT}
 * ({@code @DataJpaTest} + {@code Replace.NONE} + {@code @ServiceConnection}).
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.integrator_provider=com.lexcv.repositories.CapturaMetadataHibernate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MigracaoFiscal133IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SCRIPT = "migrations/133-create-fiscal-foundation-tables.sql";
    private static final String SCHEMA_SCRIPT = "migracao_133";
    private static final List<String> TABELAS =
            List.of("t_configuracao_fiscal", "t_parametro_fiscal", "t_serie_fiscal");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SerieFiscalRepository serieFiscalRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void aplicarScript() throws IOException {
        String sql = Files.readString(Path.of(SCRIPT));
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA_SCRIPT);
        // Uma única chamada: SET + script + reset correm na mesma ligação.
        jdbcTemplate.execute("SET search_path TO " + SCHEMA_SCRIPT + ";\n" + sql + "\nSET search_path TO public;");
    }

    private Set<String> colunas(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision, "
                        + "numeric_scale, datetime_precision, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name IN (?, ?, ?)",
                (rs, i) -> String.join("|",
                        rs.getString("table_name"), rs.getString("column_name"), rs.getString("data_type"),
                        String.valueOf(rs.getObject("character_maximum_length")),
                        String.valueOf(rs.getObject("numeric_precision")),
                        String.valueOf(rs.getObject("numeric_scale")),
                        String.valueOf(rs.getObject("datetime_precision")),
                        rs.getString("is_nullable")),
                schema, TABELAS.get(0), TABELAS.get(1), TABELAS.get(2)));
    }

    private Map<String, Set<String>> unicas(String schema) {
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                "SELECT tc.table_name, string_agg(kcu.column_name, ',' ORDER BY kcu.ordinal_position) AS cols "
                        + "FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name "
                        + " AND kcu.constraint_schema = tc.constraint_schema AND kcu.table_name = tc.table_name "
                        + "WHERE tc.constraint_type = 'UNIQUE' AND tc.table_schema = ? AND tc.table_name IN (?, ?, ?) "
                        + "GROUP BY tc.table_name, tc.constraint_name",
                schema, TABELAS.get(0), TABELAS.get(1), TABELAS.get(2));
        Map<String, Set<String>> porTabela = new TreeMap<>();
        for (Map<String, Object> l : linhas) {
            porTabela.computeIfAbsent((String) l.get("table_name"), k -> new HashSet<>()).add((String) l.get("cols"));
        }
        return porTabela;
    }

    @Test
    void scriptCriaExatamenteOEsquemaDoHibernate() throws IOException {
        aplicarScript();

        Set<String> hibernate = colunas("public");
        Set<String> script = colunas(SCHEMA_SCRIPT);
        assertEquals(18 + 5 + 9, hibernate.size(), "colunas Hibernate: " + hibernate);
        assertEquals(hibernate, script);

        Map<String, Set<String>> unicasHibernate = unicas("public");
        assertEquals(Map.of(
                "t_configuracao_fiscal", Set.of("tenant_id"),
                "t_parametro_fiscal", Set.of("codigo,vigente_desde"),
                "t_serie_fiscal", Set.of("tenant_id,tipo_documento,ano,ambiente")), unicasHibernate);
        assertEquals(unicasHibernate, unicas(SCHEMA_SCRIPT));
    }

    @Test
    void scriptEIdempotente() throws IOException {
        aplicarScript();
        assertDoesNotThrow(this::aplicarScript);
        assertEquals(colunas("public"), colunas(SCHEMA_SCRIPT));
    }

    @Test
    void hibernateNaoGeraCheckNasColunasDeEnum() {
        Integer checks = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'c' AND conrelid IN "
                        + "('public.t_serie_fiscal'::regclass, 'public.t_configuracao_fiscal'::regclass)",
                Integer.class);
        assertEquals(0, checks);
    }

    /**
     * WR-01 da revisão: um segundo arranque em {@code ddl-auto=update} sobre o esquema que o
     * próprio Hibernate criou não emite DDL nenhum para as tabelas fiscais. Corre o mesmo
     * {@code SchemaMigrator} do {@code update}, só em modo SCRIPT (nada é executado), e recolhe as
     * instruções. Antes da correção saía
     * {@code alter table if exists t_serie_fiscal alter column ambiente set data type varchar(32) not null}
     * (comprimento mapeado 255 por causa do {@code columnDefinition}, contra 32 na base de dados).
     */
    @Test
    void segundoArranqueEmUpdateNaoEmiteDdlFiscal() {
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

    private void inserirSerie(UUID tenantId) {
        jdbcTemplate.update("INSERT INTO t_serie_fiscal (id, tenant_id, tipo_documento, ano, ambiente, codigo, "
                        + "ultimo_numero, created_at) VALUES (?, ?, 'FR', 2026, 'SIMULADO', 'SIM-FR-2026', 0, now())",
                UUID.randomUUID(), tenantId);
    }

    private void inserirConfiguracao(UUID tenantId) {
        jdbcTemplate.update("INSERT INTO t_configuracao_fiscal (id, tenant_id, created_at) VALUES (?, ?, now())",
                UUID.randomUUID(), tenantId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serieDuplicadaNoMesmoTenantERecusada() {
        UUID tenant = UUID.randomUUID();
        try {
            inserirSerie(tenant);
            assertThrows(DataIntegrityViolationException.class, () -> inserirSerie(tenant));
        } finally {
            jdbcTemplate.update("DELETE FROM t_serie_fiscal WHERE tenant_id = ?", tenant);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mesmaChaveDeSerieEmTenantsDiferentesEPermitida() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        try {
            inserirSerie(tenantA);
            assertDoesNotThrow(() -> inserirSerie(tenantB));
        } finally {
            jdbcTemplate.update("DELETE FROM t_serie_fiscal WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duasConfiguracoesParaOMesmoTenantSaoRecusadas() {
        UUID tenant = UUID.randomUUID();
        try {
            inserirConfiguracao(tenant);
            assertThrows(DataIntegrityViolationException.class, () -> inserirConfiguracao(tenant));
        } finally {
            jdbcTemplate.update("DELETE FROM t_configuracao_fiscal WHERE tenant_id = ?", tenant);
        }
    }

    /**
     * Smoke test of the native SQL in SerieFiscalRepository against the real schema:
     * criarSeNaoExiste inserts once then is a no-op (ON CONFLICT DO NOTHING), bloquear finds the
     * row, definirLockTimeoutLocal returns the transaction-local value, and the "documents
     * issued" gate is false while ultimo_numero is 0.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void repositorioCriaSerieUmaVezEBloqueia() {
        UUID tenant = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String codigo = SerieFiscal.gerarCodigo(TipoDocumentoFiscal.FR, 2026, AmbienteFiscal.SIMULADO);
        try {
            int primeira = tx.execute(s -> serieFiscalRepository.criarSeNaoExiste(UUID.randomUUID(), tenant,
                    TipoDocumentoFiscal.FR.name(), 2026, AmbienteFiscal.SIMULADO.name(), codigo));
            int segunda = tx.execute(s -> serieFiscalRepository.criarSeNaoExiste(UUID.randomUUID(), tenant,
                    TipoDocumentoFiscal.FR.name(), 2026, AmbienteFiscal.SIMULADO.name(), codigo));
            assertEquals(1, primeira);
            assertEquals(0, segunda);

            tx.executeWithoutResult(s -> {
                assertEquals("5s", serieFiscalRepository.definirLockTimeoutLocal());
                SerieFiscal serie = serieFiscalRepository
                        .bloquear(tenant, TipoDocumentoFiscal.FR, 2026, AmbienteFiscal.SIMULADO).orElseThrow();
                assertEquals("SIM-FR-2026", serie.getCodigo());
                assertEquals(0L, serie.getUltimoNumero());
            });

            assertFalse(serieFiscalRepository.existsByTenantIdAndUltimoNumeroGreaterThan(tenant, 0L));
            jdbcTemplate.update("UPDATE t_serie_fiscal SET ultimo_numero = 1 WHERE tenant_id = ?", tenant);
            assertTrue(serieFiscalRepository.existsByTenantIdAndUltimoNumeroGreaterThan(tenant, 0L));
            assertEquals(List.of("SIM-FR-2026"), serieFiscalRepository.findByTenantIdOrderByAnoDescTipoDocumentoAsc(tenant)
                    .stream().map(SerieFiscal::getCodigo).collect(Collectors.toList()));
        } finally {
            jdbcTemplate.update("DELETE FROM t_serie_fiscal WHERE tenant_id = ?", tenant);
        }
    }
}
