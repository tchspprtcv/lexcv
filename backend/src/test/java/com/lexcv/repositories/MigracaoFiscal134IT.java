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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
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
import java.io.UncheckedIOException;
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
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 134 (T-134-02, T-134-03, T-134-04): prova, contra PostgreSQL real (Testcontainers), que o
 * script manual {@code backend/migrations/134-create-documento-fiscal-tables.sql} cria exatamente
 * o esquema que o Hibernate cria a partir de {@code DocumentoFiscal}, {@code DocumentoFiscalLinha}
 * e {@code ComunicacaoFiscal} (colunas com defaults, constraints UNIQUE e índices), que é
 * idempotente, que não há CHECK nas colunas de enum, que um segundo arranque em
 * {@code ddl-auto=update} não emite DDL para estas tabelas, que o bloco do índice único de
 * {@code t_conta_corrente.cliente_id} cobre os dois ramos (cria quando falta; aborta sem criar
 * nada quando há duplicados) e que as chaves únicas são por tenant.
 *
 * <p>Mesmo andaime de {@code MigracaoFiscal133IT}: o Hibernate cria as tabelas no schema
 * {@code public}; o script corre num schema separado via {@code search_path}.
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.integrator_provider=com.lexcv.repositories.CapturaMetadataHibernate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MigracaoFiscal134IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SCRIPT = "migrations/134-create-documento-fiscal-tables.sql";
    // Phase 135: as colunas da Nota de Crédito chegam pelo script seguinte; "scripts == Hibernate"
    // passa a significar 134 + 135 aplicados por ordem.
    private static final String SCRIPT_135 = "migrations/135-add-nota-credito-documento-fiscal.sql";
    private static final String SCHEMA_SCRIPT = "migracao_134";
    private static final List<String> TABELAS =
            List.of("t_documento_fiscal", "t_documento_fiscal_linha", "t_comunicacao_fiscal");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static String lerScript() {
        return lerScript(SCRIPT);
    }

    private static String lerScript(String caminho) {
        try {
            return Files.readString(Path.of(caminho));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Aplica 134 e depois 135 (Phase 135) no schema de rascunho. */
    private void aplicarScript() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA_SCRIPT);
        // Uma única chamada: SET + scripts + reset correm na mesma ligação.
        jdbcTemplate.execute("SET search_path TO " + SCHEMA_SCRIPT + ";\n" + lerScript() + "\n"
                + lerScript(SCRIPT_135) + "\nSET search_path TO public;");
    }

    /**
     * Corre o script num schema de rascunho dentro de uma transação própria; {@code SET LOCAL}
     * garante que o {@code search_path} volta ao normal no commit ou no rollback.
     */
    private void aplicarScriptEmTransacao(String schema) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                jdbcTemplate.execute("SET LOCAL search_path TO " + schema + ";\n" + lerScript()));
    }

    private Set<String> colunas(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision, "
                        + "numeric_scale, datetime_precision, is_nullable, column_default "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name IN (?, ?, ?)",
                (rs, i) -> String.join("|",
                        rs.getString("table_name"), rs.getString("column_name"), rs.getString("data_type"),
                        String.valueOf(rs.getObject("character_maximum_length")),
                        String.valueOf(rs.getObject("numeric_precision")),
                        String.valueOf(rs.getObject("numeric_scale")),
                        String.valueOf(rs.getObject("datetime_precision")),
                        rs.getString("is_nullable"),
                        String.valueOf(rs.getString("column_default"))),
                schema, TABELAS.get(0), TABELAS.get(1), TABELAS.get(2)));
    }

    private Map<String, Set<String>> unicas(String schema) {
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                "SELECT tc.table_name, tc.constraint_name, "
                        + "string_agg(kcu.column_name, ',' ORDER BY kcu.ordinal_position) AS cols "
                        + "FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name "
                        + " AND kcu.constraint_schema = tc.constraint_schema AND kcu.table_name = tc.table_name "
                        + "WHERE tc.constraint_type = 'UNIQUE' AND tc.table_schema = ? AND tc.table_name IN (?, ?, ?) "
                        + "GROUP BY tc.table_name, tc.constraint_name",
                schema, TABELAS.get(0), TABELAS.get(1), TABELAS.get(2));
        Map<String, Set<String>> porTabela = new TreeMap<>();
        for (Map<String, Object> l : linhas) {
            porTabela.computeIfAbsent((String) l.get("table_name"), k -> new HashSet<>())
                    .add(l.get("constraint_name") + ":" + l.get("cols"));
        }
        return porTabela;
    }

    /** Nome e definição de cada índice (sem o prefixo do schema), por tabela. */
    private Set<String> indices(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT tablename, indexname, indexdef FROM pg_indexes "
                        + "WHERE schemaname = ? AND tablename IN (?, ?, ?)",
                (rs, i) -> rs.getString("tablename") + "|" + rs.getString("indexname") + "|"
                        + rs.getString("indexdef").replace(schema + ".", ""),
                schema, TABELAS.get(0), TABELAS.get(1), TABELAS.get(2)));
    }

    private Set<String> nomesIndices(String schema, String tabela) {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = ? AND tablename = ?",
                String.class, schema, tabela));
    }

    @Test
    void scriptCriaExatamenteOEsquemaDoHibernate() {
        aplicarScript();

        Set<String> hibernate = colunas("public");
        // Phase 135: +3 colunas em t_documento_fiscal (documento_origem_id, motivo_codigo, motivo_texto).
        assertEquals(43 + 14 + 10, hibernate.size(), "colunas Hibernate: " + hibernate);
        assertEquals(hibernate, colunas(SCHEMA_SCRIPT));

        Map<String, Set<String>> unicasHibernate = unicas("public");
        assertEquals(Map.of(
                "t_documento_fiscal", Set.of(
                        "uk_documento_fiscal_numero:tenant_id,serie_id,numero",
                        "uk_documento_fiscal_pagamento:pagamento_id",
                        "uk_documento_fiscal_chave:tenant_id,chave_idempotencia"),
                "t_documento_fiscal_linha", Set.of(
                        "uk_documento_fiscal_linha_numero:documento_fiscal_id,numero_linha"),
                "t_comunicacao_fiscal", Set.of(
                        "uk_comunicacao_fiscal_documento:documento_fiscal_id")), unicasHibernate);
        assertEquals(unicasHibernate, unicas(SCHEMA_SCRIPT));

        Set<String> indicesHibernate = indices("public");
        assertTrue(nomesIndices("public", "t_documento_fiscal").containsAll(Set.of(
                "idx_documento_fiscal_tenant_data", "idx_documento_fiscal_tenant_cliente",
                "idx_documento_fiscal_tenant_processo", "idx_documento_fiscal_tenant_honorario",
                "idx_documento_fiscal_tenant_origem")),
                "índices Hibernate: " + indicesHibernate);
        assertTrue(nomesIndices("public", "t_comunicacao_fiscal").contains("idx_comunicacao_fiscal_tenant_estado"));
        assertEquals(indicesHibernate, indices(SCHEMA_SCRIPT));
    }

    @Test
    void scriptEIdempotente() {
        aplicarScript();
        Set<String> primeira = colunas(SCHEMA_SCRIPT);
        Set<String> indicesPrimeira = indices(SCHEMA_SCRIPT);
        assertDoesNotThrow(this::aplicarScript);
        assertEquals(primeira, colunas(SCHEMA_SCRIPT));
        assertEquals(indicesPrimeira, indices(SCHEMA_SCRIPT));
        assertEquals(colunas("public"), colunas(SCHEMA_SCRIPT));
    }

    @Test
    void hibernateNaoGeraCheckNasColunasDeEnum() {
        Integer checks = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE contype = 'c' AND conrelid IN "
                        + "('public.t_documento_fiscal'::regclass, 'public.t_documento_fiscal_linha'::regclass, "
                        + "'public.t_comunicacao_fiscal'::regclass)",
                Integer.class);
        assertEquals(0, checks);
    }

    /**
     * Um segundo arranque em {@code ddl-auto=update} sobre o esquema que o próprio Hibernate criou
     * não emite DDL para as tabelas de documentos fiscais (mesmo método de
     * {@code MigracaoFiscal133IT.segundoArranqueEmUpdateNaoEmiteDdlFiscal}).
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

    private void criarContaCorrenteSemIndice(String schema) {
        jdbcTemplate.execute("CREATE SCHEMA " + schema);
        jdbcTemplate.execute("CREATE TABLE " + schema + ".t_conta_corrente ("
                + "id serial PRIMARY KEY, cliente_id uuid NOT NULL, saldo numeric(19,2), updated_at timestamp(6))");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void contaCorrenteGanhaIndiceUnicoQuandoFalta() {
        String semIndice = "cc_sem_indice_134";
        String duplicado = "cc_duplicado_134";
        try {
            // Ramo 1: sem índice único e sem duplicados -> o script cria uk_conta_corrente_cliente.
            criarContaCorrenteSemIndice(semIndice);
            jdbcTemplate.update("INSERT INTO " + semIndice + ".t_conta_corrente (cliente_id, saldo) VALUES (?, 0)",
                    UUID.randomUUID());
            aplicarScriptEmTransacao(semIndice);
            assertTrue(nomesIndices(semIndice, "t_conta_corrente").contains("uk_conta_corrente_cliente"));
            // Idempotente também neste ramo: o índice já existe, o bloco não faz nada.
            assertDoesNotThrow(() -> aplicarScriptEmTransacao(semIndice));

            // Ramo 2: cliente_id duplicado -> aborta com mensagem clara e não cria NADA.
            criarContaCorrenteSemIndice(duplicado);
            UUID cliente = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + duplicado + ".t_conta_corrente (cliente_id, saldo) VALUES (?, 0), (?, 0)",
                    cliente, cliente);
            DataAccessException erro = assertThrows(DataAccessException.class,
                    () -> aplicarScriptEmTransacao(duplicado));
            assertTrue(String.valueOf(erro.getMostSpecificCause().getMessage()).contains("cliente_id duplicado"),
                    "mensagem: " + erro.getMostSpecificCause().getMessage());
            assertEquals(Set.of("t_conta_corrente_pkey"), nomesIndices(duplicado, "t_conta_corrente"));
            Integer tabelasFiscais = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN (?, ?, ?)",
                    Integer.class, duplicado, TABELAS.get(0), TABELAS.get(1), TABELAS.get(2));
            assertEquals(0, tabelasFiscais, "o script abortado não pode deixar tabelas criadas");
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + semIndice + " CASCADE");
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + duplicado + " CASCADE");
        }
    }

    private void inserirDocumento(UUID tenantId, UUID serieId, long numero, UUID chave) {
        int pagamentoId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        jdbcTemplate.update("INSERT INTO t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, serie_codigo, ano, "
                        + "numero, numero_formatado, data_emissao, emitido_em, emitente_nif, emitente_firma, "
                        + "emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, adquirente_morada, "
                        + "cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia) "
                        + "VALUES (?, ?, 'FR', 'SIMULADO', ?, 'SIM-FR-2026', 2026, ?, ?, current_date, now(), "
                        + "'512345679', 'Escritório', 'Rua A', 'NORMAL', '512345670', 'Ana Lopes', 'Rua B', "
                        + "?, ?, 1, ?, 'DINHEIRO', '10', 'CVE', 15, 100.00, 15.00, 0.00, 115.00, 115.00, ?)",
                UUID.randomUUID(), tenantId, serieId, numero, "SIM-FR-2026/" + numero,
                UUID.randomUUID(), UUID.randomUUID(), pagamentoId, chave);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unicidadesPorTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID serie = UUID.randomUUID();
        UUID chave = UUID.randomUUID();
        try {
            // (tenant, serie, numero): mesmo número noutro tenant aceite; no mesmo tenant recusado.
            inserirDocumento(tenantA, serie, 1, UUID.randomUUID());
            assertDoesNotThrow(() -> inserirDocumento(tenantB, serie, 1, UUID.randomUUID()));
            assertThrows(DataIntegrityViolationException.class,
                    () -> inserirDocumento(tenantA, serie, 1, UUID.randomUUID()));

            // (tenant, chave_idempotencia): mesma chave noutro tenant aceite; no mesmo tenant recusada.
            inserirDocumento(tenantA, serie, 2, chave);
            assertDoesNotThrow(() -> inserirDocumento(tenantB, serie, 2, chave));
            assertThrows(DataIntegrityViolationException.class,
                    () -> inserirDocumento(tenantA, serie, 3, chave));
        } finally {
            jdbcTemplate.update("DELETE FROM t_documento_fiscal WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        }
    }
}
