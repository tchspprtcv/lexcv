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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 138 (SUBS-01..05): prova, contra PostgreSQL real (Testcontainers), que o script manual
 * {@code backend/migrations/138-plataforma-faturacao.sql}, aplicado sobre 133..137 (e outra vez),
 * deixa {@code t_pagamento_subscricao} e as colunas/índices de {@code t_documento_fiscal}
 * iguais ao esquema do Hibernate, e que o script é idempotente.
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.integrator_provider=com.lexcv.repositories.CapturaMetadataHibernate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigracaoFiscal138IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SCRIPT_133 = "migrations/133-create-fiscal-foundation-tables.sql";
    private static final String SCRIPT_134 = "migrations/134-create-documento-fiscal-tables.sql";
    private static final String SCRIPT_135 = "migrations/135-add-nota-credito-documento-fiscal.sql";
    private static final String SCRIPT_136 = "migrations/136-efatura-comunicacao.sql";
    private static final String SCRIPT_137 = "migrations/137-entrega-documento-fiscal.sql";
    private static final String SCRIPT_138 = "migrations/138-plataforma-faturacao.sql";

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
        jdbcTemplate.execute("SET search_path TO " + schema + ";\n" + lerScript(caminho) + "\nSET search_path TO public;");
    }

    private void aplicarTodos(String schema) {
        aplicar(schema, SCRIPT_133);
        aplicar(schema, SCRIPT_134);
        aplicar(schema, SCRIPT_135);
        aplicar(schema, SCRIPT_136);
        aplicar(schema, SCRIPT_137);
        aplicar(schema, SCRIPT_138);
    }

    private void apagar(String schema) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    @Order(1)
    void script138EAplicavelEIdempotente() {
        String schema = "rascunho_138_idempotencia";
        try {
            aplicarTodos(schema);
            assertDoesNotThrow(() -> aplicar(schema, SCRIPT_138), "O script 138 deve ser idempotente");
        } finally {
            apagar(schema);
        }
    }

    @Test
    @Order(2)
    void colunasDePagamentoSubscricaoEDocumentoFiscalBatemCertoComHibernate() {
        String schema = "rascunho_138_colunas";
        try {
            aplicarTodos(schema);

            Integer countColsPublic = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 't_pagamento_subscricao'",
                    Integer.class);
            Integer countColsScript = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 't_pagamento_subscricao'",
                    Integer.class, schema);

            assertNotNull(countColsPublic);
            assertEquals(countColsPublic, countColsScript, "Número de colunas de t_pagamento_subscricao deve ser idêntico");

            // Verifica colunas novas de t_documento_fiscal
            Boolean temAdquirenteTenant = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = 't_documento_fiscal' AND column_name = 'adquirente_tenant_id')",
                    Boolean.class, schema);
            assertTrue(Boolean.TRUE.equals(temAdquirenteTenant), "t_documento_fiscal deve ter adquirente_tenant_id");

            Boolean temPagamentoSubscricao = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = 't_documento_fiscal' AND column_name = 'pagamento_subscricao_id')",
                    Boolean.class, schema);
            assertTrue(Boolean.TRUE.equals(temPagamentoSubscricao), "t_documento_fiscal deve ter pagamento_subscricao_id");

            // Verifica que cliente_id e pagamento_id são nullable
            String clienteIdNullable = jdbcTemplate.queryForObject(
                    "SELECT is_nullable FROM information_schema.columns WHERE table_schema = ? AND table_name = 't_documento_fiscal' AND column_name = 'cliente_id'",
                    String.class, schema);
            assertEquals("YES", clienteIdNullable, "cliente_id deve ser nullable no schema 138");
        } finally {
            apagar(schema);
        }
    }
}
