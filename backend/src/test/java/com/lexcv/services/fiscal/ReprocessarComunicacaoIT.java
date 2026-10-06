package com.lexcv.services.fiscal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ReprocessarComunicacaoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.repositories.FilaComunicacaoFiscal;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 136 (DFE-05): o reprocessamento manual em PostgreSQL real -- reposição condicional
 * (só ERRO/REJEITADO), presa ao tenant, auditada na mesma transação, sem tocar no documento nem
 * na linha XML.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({ReprocessamentoComunicacaoService.class, FilaComunicacaoFiscal.class, AuditoriaFiscalService.class,
        ReprocessarComunicacaoIT.Apoio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReprocessarComunicacaoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final Instant T0 = Instant.parse("2026-06-15T13:00:00Z");
    static final Instant AGORA = Instant.parse("2026-06-16T09:30:00Z");

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

    private static final AtomicLong SEQUENCIA = new AtomicLong(1);
    private static final String ACAO = "documento_fiscal_reprocessar_comunicacao";

    @Autowired
    private ReprocessamentoComunicacaoService service;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenantId;
    private UserPrincipal autor;

    @BeforeEach
    void preparar() {
        tenantId = UUID.randomUUID();
        autor = UserPrincipal.create(UUID.randomUUID(), tenantId, "Ana Reprocessa", "ana@example.cv",
                Set.of(), Set.of("financeiro:edit"), Set.of());
    }

    // ------------------------------------------------------------------ apoio JDBC

    private UUID documento(UUID tenant) {
        UUID id = UUID.randomUUID();
        long n = SEQUENCIA.getAndIncrement();
        String serie = "SIM-FR-2026";
        jdbc.update("INSERT INTO t_documento_fiscal (id, tenant_id, tipo, ambiente, serie_id, serie_codigo, ano, "
                        + "numero, numero_formatado, data_emissao, emitido_em, emitente_nif, emitente_firma, "
                        + "emitente_morada, emitente_regime_iva, adquirente_nif, adquirente_nome, adquirente_morada, "
                        + "cliente_id, processo_id, honorario_id, pagamento_id, metodo_pagamento, "
                        + "meio_pagamento_codigo, moeda, taxa_iva, total_base, total_iva, total_retencao, "
                        + "total_documento, valor_liquido, chave_idempotencia) "
                        + "VALUES (?, ?, 'FR', 'SIMULADO', ?, ?, 2026, ?, ?, ?, ?, '512345679', 'Firma', 'Morada', "
                        + "'NORMAL', '234567891', 'Cliente', 'Morada cliente', ?, ?, 1, ?, 'DINHEIRO', '10', 'CVE', "
                        + "15, 100, 15, 0, 115, 115, ?)",
                id, tenant, UUID.randomUUID(), serie, n, serie + "/" + n,
                java.sql.Date.valueOf("2026-06-15"), Timestamp.from(T0), UUID.randomUUID(), UUID.randomUUID(),
                (int) -n, UUID.randomUUID());
        jdbc.update("INSERT INTO t_documento_fiscal_xml (id, tenant_id, documento_fiscal_id, iud, ambiente, "
                        + "repositorio_codigo, led_codigo, versao_formato, xml, xml_sha256, gerado_em) "
                        + "VALUES (?, ?, ?, ?, 'SIMULADO', 3, 99999, '2024-05-27', '<Dfe/>', ?, ?)",
                UUID.randomUUID(), tenant, id, "CV3260615512345679" + String.format("%027d", n),
                "a".repeat(64), Timestamp.from(T0));
        return id;
    }

    private void comunicacao(UUID tenant, UUID documentoId, String estado, int tentativas, boolean comErro) {
        jdbc.update("INSERT INTO t_comunicacao_fiscal (id, tenant_id, documento_fiscal_id, ambiente, estado, "
                        + "tentativas, proxima_tentativa_em, ultimo_erro, ultimo_erro_codigo, concluido_em, "
                        + "lease_ate, created_at, versao, reprocessamentos) "
                        + "VALUES (?, ?, ?, 'SIMULADO', ?, ?, NULL, ?, ?, ?, NULL, ?, 4, 0)",
                UUID.randomUUID(), tenant, documentoId, estado, tentativas,
                comErro ? "Falha simulada do serviço de comunicação." : null,
                comErro ? "FALHA_SIMULADA" : null,
                "PENDENTE".equals(estado) ? null : Timestamp.from(T0), Timestamp.from(T0));
    }

    private Map<String, Object> linha(UUID documentoId) {
        return jdbc.queryForMap("SELECT * FROM t_comunicacao_fiscal WHERE documento_fiscal_id = ?", documentoId);
    }

    private int eventos(UUID tenant) {
        return jdbc.queryForObject("SELECT count(*) FROM t_audit_log WHERE tenant_id = ? AND acao = ?",
                Integer.class, tenant, ACAO);
    }

    private Map<String, Object> documentoEXml(UUID documentoId) {
        return jdbc.queryForMap("SELECT d.numero_formatado, d.total_documento, x.iud, x.xml, x.xml_sha256, "
                + "(SELECT count(*) FROM t_documento_fiscal_xml) AS xmls, "
                + "(SELECT count(*) FROM t_documento_fiscal) AS docs "
                + "FROM t_documento_fiscal d JOIN t_documento_fiscal_xml x ON x.documento_fiscal_id = d.id "
                + "WHERE d.id = ?", documentoId);
    }

    private static Instant instante(Object valor) {
        return valor == null ? null : ((Timestamp) valor).toInstant();
    }

    private void assertReposta(UUID documentoId) {
        Map<String, Object> l = linha(documentoId);
        assertEquals("PENDENTE", l.get("estado"));
        assertEquals(0, ((Number) l.get("tentativas")).intValue());
        assertEquals(AGORA, instante(l.get("proxima_tentativa_em")));
        assertNull(l.get("ultimo_erro"));
        assertNull(l.get("ultimo_erro_codigo"));
        assertNull(l.get("lease_ate"));
        assertNull(l.get("concluido_em"));
        assertEquals(1, ((Number) l.get("reprocessamentos")).intValue());
        assertEquals(5L, ((Number) l.get("versao")).longValue());
    }

    // ------------------------------------------------------------------ testes

    @Test
    void erroVoltaAPendenteComTentativasAZeroEContadorMaisUm() throws Exception {
        UUID doc = documento(tenantId);
        comunicacao(tenantId, doc, "ERRO", 8, true);

        ReprocessarComunicacaoResponse r = service.reprocessar(tenantId, autor, doc);

        assertEquals(new ReprocessarComunicacaoResponse("PENDENTE", 0), r);
        assertReposta(doc);

        assertEquals(1, eventos(tenantId));
        Map<String, Object> ev = jdbc.queryForMap(
                "SELECT entidade_tipo, entidade_id, autor_id, detalhe FROM t_audit_log WHERE tenant_id = ? AND acao = ?",
                tenantId, ACAO);
        assertEquals("documento_fiscal", ev.get("entidade_tipo"));
        assertEquals(doc.toString(), ev.get("entidade_id"));
        assertEquals(autor.getUserId(), ev.get("autor_id"));
        JsonNode d = json.readTree((String) ev.get("detalhe"));
        Set<String> chaves = new HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "numeroFormatado", "estadoAnterior"), chaves);
        assertEquals("Ana Reprocessa", d.get("autorNome").asText());
        assertEquals("ERRO", d.get("estadoAnterior").asText());
        assertEquals(linhaNumero(doc), d.get("numeroFormatado").asText());
    }

    private String linhaNumero(UUID doc) {
        return jdbc.queryForObject("SELECT numero_formatado FROM t_documento_fiscal WHERE id = ?", String.class, doc);
    }

    @Test
    void rejeitadoTambemEReposto() {
        UUID doc = documento(tenantId);
        comunicacao(tenantId, doc, "REJEITADO", 1, true);

        assertEquals(new ReprocessarComunicacaoResponse("PENDENTE", 0), service.reprocessar(tenantId, autor, doc));
        assertReposta(doc);
        assertEquals(1, eventos(tenantId));
    }

    @Test
    void repetirLogoDepoisDa409ENaoMudaNada() {
        UUID doc = documento(tenantId);
        comunicacao(tenantId, doc, "ERRO", 8, true);
        service.reprocessar(tenantId, autor, doc);
        Map<String, Object> antes = linha(doc);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.reprocessar(tenantId, autor, doc));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("COMUNICACAO_ESTADO_INVALIDO", e.getCodigo());
        assertEquals(antes, linha(doc));
        assertEquals(1, eventos(tenantId));
    }

    @Test
    void aceiteSimuladoDa409SemAlteracoesNemEvento() {
        UUID doc = documento(tenantId);
        comunicacao(tenantId, doc, "ACEITE_SIMULADO", 1, false);
        Map<String, Object> antes = linha(doc);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.reprocessar(tenantId, autor, doc));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("COMUNICACAO_ESTADO_INVALIDO", e.getCodigo());
        assertEquals(antes, linha(doc));
        assertEquals(0, eventos(tenantId));
    }

    @Test
    void semLinhaDeComunicacaoDa409() {
        UUID doc = documento(tenantId);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.reprocessar(tenantId, autor, doc));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("COMUNICACAO_ESTADO_INVALIDO", e.getCodigo());
        assertEquals(0, eventos(tenantId));
    }

    @Test
    void documentoDeOutroTenantDa404ENaoTocaNaLinhaDele() {
        UUID outro = UUID.randomUUID();
        UUID docOutro = documento(outro);
        comunicacao(outro, docOutro, "ERRO", 8, true);
        Map<String, Object> antes = linha(docOutro);

        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.reprocessar(tenantId, autor, docOutro));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("DOCUMENTO_FISCAL_NAO_ENCONTRADO", e.getCodigo());
        assertEquals("Documento fiscal não encontrado.", e.getMessage());
        assertEquals(antes, linha(docOutro));
        assertEquals(0, eventos(tenantId));
        assertEquals(0, eventos(outro));
    }

    @Test
    void documentoInexistenteDa404() {
        RecusaFiscalException e = assertThrows(RecusaFiscalException.class,
                () -> service.reprocessar(tenantId, autor, UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("DOCUMENTO_FISCAL_NAO_ENCONTRADO", e.getCodigo());
    }

    @Test
    void documentoELinhaXmlFicamIntactos() {
        UUID doc = documento(tenantId);
        comunicacao(tenantId, doc, "ERRO", 8, true);
        Map<String, Object> antes = documentoEXml(doc);

        service.reprocessar(tenantId, autor, doc);

        assertEquals(antes, documentoEXml(doc));
    }
}
