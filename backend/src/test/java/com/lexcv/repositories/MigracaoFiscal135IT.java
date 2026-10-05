package com.lexcv.repositories;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 135 (NCRD-01, T-135-05..07): prova, contra PostgreSQL real (Testcontainers), que o script
 * manual {@code backend/migrations/135-add-nota-credito-documento-fiscal.sql}, aplicado sobre o 134,
 * é idempotente e deixa o esquema igual ao do Hibernate, e que aplicado sobre uma base com
 * Faturas-Recibo já emitidas não lhes toca (as três colunas novas ficam NULL) e aceita Notas de
 * Crédito -- incluindo a unicidade de {@code pagamento_id} (o estorno não recebe segundo
 * documento) e o limite de 200 caracteres de {@code motivo_texto}.
 *
 * <p>Mesmo andaime de {@code MigracaoFiscal134IT}: o Hibernate cria as tabelas no schema
 * {@code public}; os scripts correm em schemas de rascunho (um por teste, apagado no fim). Sem
 * transação de teste, porque um INSERT recusado abortaria a transação partilhada.
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.integrator_provider=com.lexcv.repositories.CapturaMetadataHibernate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MigracaoFiscal135IT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SCRIPT_134 = "migrations/134-create-documento-fiscal-tables.sql";
    private static final String SCRIPT_135 = "migrations/135-add-nota-credito-documento-fiscal.sql";
    // Phase 136: toda a comparação com o esquema do Hibernate aplica também o 136 depois do 135.
    private static final String SCRIPT_136 = "migrations/136-efatura-comunicacao.sql";
    private static final List<String> TABELAS =
            List.of("t_documento_fiscal", "t_documento_fiscal_linha", "t_comunicacao_fiscal");
    private static final List<String> COLUNAS_NC = List.of("documento_origem_id", "motivo_codigo", "motivo_texto");

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

    private void apagar(String schema) {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
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

    private Set<String> indices(String schema) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT tablename, indexname, indexdef FROM pg_indexes "
                        + "WHERE schemaname = ? AND tablename IN (?, ?, ?)",
                (rs, i) -> rs.getString("tablename") + "|" + rs.getString("indexname") + "|"
                        + rs.getString("indexdef").replace(schema + ".", ""),
                schema, TABELAS.get(0), TABELAS.get(1), TABELAS.get(2)));
    }

    /** FR com as colunas da Phase 134 apenas (a base ainda não conhece as colunas da NC). */
    private UUID inserirFaturaRecibo(String schema, UUID tenantId, int pagamentoId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO " + schema + ".t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, "
                        + "serie_codigo, ano, numero, numero_formatado, data_emissao, emitido_em, emitente_nif, "
                        + "emitente_firma, emitente_morada, emitente_localidade, emitente_regime_iva, adquirente_nif, "
                        + "adquirente_nome, adquirente_morada, adquirente_localidade, cliente_id, processo_id, "
                        + "honorario_id, pagamento_id, metodo_pagamento, meio_pagamento_codigo, moeda, taxa_iva, "
                        + "total_base, total_iva, total_retencao, total_documento, valor_liquido, taxa_retencao, "
                        + "chave_idempotencia, emitido_por_id, emitido_por_nome) "
                        + "VALUES (?, ?, 'FR', 'SIMULADO', ?, 'SIM-FR-2026', 2026, 1, 'SIM-FR-2026/1', DATE '2026-03-01', "
                        + "TIMESTAMP '2026-03-01 10:00:00', '512345679', 'Escritório', 'Rua A', 'Praia', 'NORMAL', "
                        + "'512345670', 'Ana Lopes', 'Rua B', 'Mindelo', ?, ?, 7, ?, 'DINHEIRO', '10', 'CVE', 15, "
                        + "104347.83, 15652.17, 20869.57, 120000.00, 99130.43, 20, ?, ?, 'Advogada')",
                id, tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), pagamentoId,
                UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    private void inserirNotaCredito(String schema, UUID tenantId, UUID origemId, int pagamentoId, long numero,
                                    String motivoTexto) {
        jdbcTemplate.update("INSERT INTO " + schema + ".t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, "
                        + "serie_codigo, ano, numero, numero_formatado, data_emissao, emitido_em, emitente_nif, "
                        + "emitente_firma, emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, "
                        + "adquirente_morada, cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia, documento_origem_id, motivo_codigo, "
                        + "motivo_texto) "
                        + "VALUES (?, ?, 'NC', 'SIMULADO', ?, 'SIM-NC-2026', 2026, ?, ?, DATE '2026-03-02', now(), "
                        + "'512345679', 'Escritório', 'Rua A', 'NORMAL', '512345670', 'Ana Lopes', 'Rua B', "
                        + "?, ?, 7, ?, 'DINHEIRO', '10', 'CVE', 15, 17391.30, 2608.70, 3478.26, 20000.00, 16521.74, "
                        + "?, ?, 'CORRECAO_VALOR', ?)",
                UUID.randomUUID(), tenantId, UUID.randomUUID(), numero, "SIM-NC-2026/" + numero,
                UUID.randomUUID(), UUID.randomUUID(), pagamentoId, UUID.randomUUID(), origemId, motivoTexto);
    }

    private Map<String, Object> linha(String schema, UUID id) {
        return new LinkedHashMap<>(jdbcTemplate.queryForMap(
                "SELECT * FROM " + schema + ".t_documento_fiscal WHERE id = ?", id));
    }

    @Test
    void aplicadoDuasVezesSobreO134FicaIgualAoHibernate() {
        String schema = "migracao_135_idem";
        try {
            aplicar(schema, SCRIPT_134);
            aplicar(schema, SCRIPT_135);
            aplicar(schema, SCRIPT_136);
            Set<String> primeira = colunas(schema);
            Set<String> indicesPrimeira = indices(schema);
            assertDoesNotThrow(() -> aplicar(schema, SCRIPT_135));

            assertEquals(primeira, colunas(schema));
            assertEquals(indicesPrimeira, indices(schema));
            assertEquals(colunas("public"), colunas(schema));
            assertEquals(unicas("public"), unicas(schema));
            assertEquals(indices("public"), indices(schema));
            assertTrue(indices(schema).stream().anyMatch(i -> i.contains("idx_documento_fiscal_tenant_origem")));
        } finally {
            apagar(schema);
        }
    }

    @Test
    void colunasNovasSaoAnulaveisComOsTiposDoHibernate() {
        String schema = "migracao_135_tipos";
        try {
            aplicar(schema, SCRIPT_134);
            aplicar(schema, SCRIPT_135);
            Set<String> esperadas = Set.of(
                    "t_documento_fiscal|documento_origem_id|uuid|null|null|null|null|YES|null",
                    "t_documento_fiscal|motivo_codigo|character varying|32|null|null|null|YES|null",
                    "t_documento_fiscal|motivo_texto|character varying|200|null|null|null|YES|null");
            assertTrue(colunas(schema).containsAll(esperadas), "colunas: " + colunas(schema));
            assertTrue(colunas("public").containsAll(esperadas), "colunas Hibernate: " + colunas("public"));
            // pagamento_id continua NOT NULL (nenhuma constraint relaxada).
            assertTrue(colunas(schema).contains("t_documento_fiscal|pagamento_id|integer|null|32|0|null|NO|null"),
                    "colunas: " + colunas(schema));
        } finally {
            apagar(schema);
        }
    }

    @Test
    void aplicadoSobreFaturasEmitidasNaoLhesTocaEAceitaNotasDeCredito() {
        String schema = "migracao_135_dados";
        UUID tenant = UUID.randomUUID();
        try {
            aplicar(schema, SCRIPT_134);
            UUID fr = inserirFaturaRecibo(schema, tenant, 500);
            Map<String, Object> antes = linha(schema, fr);
            for (String c : COLUNAS_NC) {
                assertFalse(antes.containsKey(c), "antes do 135 a coluna " + c + " não existe");
            }

            aplicar(schema, SCRIPT_135);

            Map<String, Object> depois = linha(schema, fr);
            for (String c : COLUNAS_NC) {
                assertTrue(depois.containsKey(c), c);
                assertNull(depois.get(c), "FR existente fica com " + c + " NULL");
            }
            Map<String, Object> originais = new LinkedHashMap<>(depois);
            COLUNAS_NC.forEach(originais::remove);
            assertEquals(antes, originais, "colunas originais da FR inalteradas");

            // NC com o seu próprio pagamento (estorno) e a referência à FR.
            assertDoesNotThrow(() -> inserirNotaCredito(schema, tenant, fr, 501, 1, "Valor faturado a mais"));
            Map<String, Object> nc = jdbcTemplate.queryForMap("SELECT tipo, documento_origem_id, motivo_codigo, "
                    + "motivo_texto FROM " + schema + ".t_documento_fiscal WHERE pagamento_id = 501");
            assertEquals("NC", nc.get("tipo"));
            assertEquals(fr, nc.get("documento_origem_id"));
            assertEquals("CORRECAO_VALOR", nc.get("motivo_codigo"));
            assertEquals("Valor faturado a mais", nc.get("motivo_texto"));

            // O estorno nunca recebe segundo documento: uk_documento_fiscal_pagamento.
            DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class,
                    () -> inserirNotaCredito(schema, tenant, fr, 501, 2, "Outra"));
            assertTrue(String.valueOf(e.getMostSpecificCause().getMessage()).contains("uk_documento_fiscal_pagamento"),
                    e.getMostSpecificCause().getMessage());
        } finally {
            apagar(schema);
        }
    }

    @Test
    void motivoTextoAcimaDe200CaracteresERecusado() {
        String schema = "migracao_135_texto";
        UUID tenant = UUID.randomUUID();
        try {
            aplicar(schema, SCRIPT_134);
            aplicar(schema, SCRIPT_135);
            UUID fr = inserirFaturaRecibo(schema, tenant, 700);

            assertDoesNotThrow(() -> inserirNotaCredito(schema, tenant, fr, 701, 1, "x".repeat(200)));
            assertThrows(DataAccessException.class,
                    () -> inserirNotaCredito(schema, tenant, fr, 702, 2, "x".repeat(201)));
            assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema
                    + ".t_documento_fiscal WHERE tipo = 'NC'", Integer.class));
        } finally {
            apagar(schema);
        }
    }
}
