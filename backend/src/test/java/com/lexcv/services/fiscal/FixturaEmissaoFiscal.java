package com.lexcv.services.fiscal;

import com.lexcv.models.RegimeIva;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Phase 134 (plano 07, reutilizada no plano 10 -- pública porque o IT do plano 10 vive em
 * {@code com.lexcv.controllers}): dados mínimos para emitir uma Fatura-Recibo em
 * PostgreSQL real, inseridos por JDBC e comprometidos de imediato (auto-commit), para que as
 * transações do serviço em teste os vejam.
 *
 * <p>Classe simples, sem anotações Spring: cada IT constrói-a com o seu {@link JdbcTemplate}.
 * Só preenche as colunas NOT NULL de cada tabela (mais NIF/morada do cliente, exigidos pela
 * emissão). Os parâmetros fiscais são globais: {@link #garantirParametros()} só os insere se
 * ainda não existirem.
 */
public final class FixturaEmissaoFiscal {

    public static final String NIF_EMITENTE = "512345679";
    public static final String FIRMA = "Escritório Silva & Associados";
    public static final String MORADA_EMITENTE = "Rua 5 de Julho, 12";
    public static final String MOTIVO_ISENCAO = "1";
    private static final LocalDate VIGENCIA = LocalDate.of(2000, 1, 1);

    private final JdbcTemplate jdbc;

    public FixturaEmissaoFiscal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Novo tenant com configuração fiscal completa e ativa; ISENTO usa o motivo "1". */
    public UUID criarTenantComFaturacao(RegimeIva regime) {
        return criarTenant(regime, true);
    }

    /** Novo tenant com configuração completa, ativa ou não. */
    public UUID criarTenant(RegimeIva regime, boolean ativa) {
        UUID tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO t_configuracao_fiscal (id, tenant_id, nif, firma, morada, localidade, pais_codigo, "
                        + "email_contacto, telefone_contacto, regime_iva, motivo_isencao_codigo, ativa, "
                        + "envio_email_automatico, created_at) VALUES (?, ?, ?, ?, ?, ?, 'CV', ?, ?, ?, ?, ?, false, ?)",
                UUID.randomUUID(), tenantId, NIF_EMITENTE, FIRMA, MORADA_EMITENTE, "Praia",
                "geral@silva.cv", "+238 260 00 00", regime.name(),
                regime == RegimeIva.ISENTO ? MOTIVO_ISENCAO : null, ativa,
                Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        return tenantId;
    }

    /** IVA_TAXA_NORMAL 15 e RETENCAO_SUGERIDA 20, vigentes desde 2000-01-01, se ainda não existirem. */
    public void garantirParametros() {
        inserirParametroSeFaltar("IVA_TAXA_NORMAL", "15");
        inserirParametroSeFaltar("RETENCAO_SUGERIDA", "20");
    }

    private void inserirParametroSeFaltar(String codigo, String valor) {
        jdbc.update("INSERT INTO t_parametro_fiscal (id, codigo, valor, vigente_desde, created_at) "
                        + "VALUES (?, ?, ?, ?, now()) ON CONFLICT (codigo, vigente_desde) DO NOTHING",
                UUID.randomUUID(), codigo, new BigDecimal(valor), VIGENCIA);
    }

    public UUID criarCliente(UUID tenantId, String nif, String nome, String morada) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_cliente (id, tenant_id, nome, nif, morada) VALUES (?, ?, ?, ?, ?)",
                id, tenantId, nome, nif, morada);
        return id;
    }

    public UUID criarProcesso(UUID tenantId, UUID clienteId, String numero) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO t_processo (id, tenant_id, cliente_id, numero_processo, legal_hold) "
                + "VALUES (?, ?, ?, ?, false)", id, tenantId, clienteId, numero);
        return id;
    }

    public Integer criarHonorario(UUID processoId, BigDecimal valorTotal, String descricao) {
        return jdbc.queryForObject("INSERT INTO t_honorario (processo_id, valor_total, descricao) "
                + "VALUES (?, ?, ?) RETURNING id", Integer.class, processoId, valorTotal, descricao);
    }

    /** Conta corrente com um saldo inicial (para provar que um rollback a deixa como estava). */
    public void criarContaCorrente(UUID clienteId, BigDecimal saldo) {
        jdbc.update("INSERT INTO t_conta_corrente (cliente_id, saldo, updated_at) VALUES (?, ?, now())",
                clienteId, saldo);
    }

    public int contarPagamentos(Integer honorarioId) {
        return contar("SELECT count(*) FROM t_pagamento WHERE honorario_id = ?", honorarioId);
    }

    public int contarDocumentos(UUID tenantId) {
        return contar("SELECT count(*) FROM t_documento_fiscal WHERE tenant_id = ?", tenantId);
    }

    public int contarLinhas(UUID tenantId) {
        return contar("SELECT count(*) FROM t_documento_fiscal_linha WHERE tenant_id = ?", tenantId);
    }

    public int contarComunicacoes(UUID tenantId) {
        return contar("SELECT count(*) FROM t_comunicacao_fiscal WHERE tenant_id = ?", tenantId);
    }

    public int contarEventosEmissao(UUID tenantId) {
        return contar("SELECT count(*) FROM t_audit_log WHERE tenant_id = ? AND acao = 'documento_fiscal_emitir'",
                tenantId);
    }

    /** Saldo da conta corrente, ou {@code null} quando o cliente ainda não tem conta. */
    public BigDecimal saldo(UUID clienteId) {
        List<BigDecimal> r = jdbc.queryForList("SELECT saldo FROM t_conta_corrente WHERE cliente_id = ?",
                BigDecimal.class, clienteId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Último número da série FR SIMULADO do tenant, ou {@code null} sem série. */
    public Long ultimoNumero(UUID tenantId) {
        List<Long> r = jdbc.queryForList("SELECT ultimo_numero FROM t_serie_fiscal WHERE tenant_id = ? "
                + "AND tipo_documento = 'FR' AND ambiente = 'SIMULADO'", Long.class, tenantId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Números atribuídos aos documentos do tenant, por ordem. */
    public List<Long> numerosEmitidos(UUID tenantId) {
        return jdbc.queryForList("SELECT numero FROM t_documento_fiscal WHERE tenant_id = ? ORDER BY numero",
                Long.class, tenantId);
    }

    private int contar(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
