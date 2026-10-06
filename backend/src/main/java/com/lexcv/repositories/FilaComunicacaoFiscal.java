package com.lexcv.repositories;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.services.fiscal.ComunicacaoReclamada;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Phase 136 (DFE-04, DFE-06): SQL nativo da fila de comunicação fiscal (outbox) sobre
 * {@code t_comunicacao_fiscal}.
 *
 * <p>Não vive em {@link ComunicacaoFiscalRepository}: esse repositório tem o conjunto de métodos
 * fixado por {@code DocumentoFiscalImutabilidadeTest} e todos os seus finders exigem um tenant. A
 * reclamação aqui é <b>deliberadamente multi-tenant</b> -- o job corre sem SecurityContext e não
 * salta tenants suspensos (136-CONTEXT) -- e é a ÚNICA instrução multi-tenant da fila. Devolve o
 * {@code tenant_id} de cada linha, e todas as instruções seguintes (snapshot, XML, resultado)
 * ficam presas a esse tenant (T-136-21).
 *
 * <p>Os métodos exigem uma transação já aberta (MANDATORY): só correm dentro das transações curtas de
 * {@code ComunicacaoFiscalTransacoes}, nunca à volta de construção de XML ou de chamadas ao
 * gateway.
 */
@Repository
public class FilaComunicacaoFiscal {

    static final int MAX_MENSAGEM = 500;
    static final int MAX_CODIGO = 64;

    /**
     * Reclama até {@code :lote} linhas devidas, as mais antigas primeiro. {@code proxima_tentativa_em}
     * {@code NULL} conta como devida (as linhas criadas pelas Phases 134/135 nascem assim). Um lease
     * expirado volta a ser reclamável (recuperação de crash). A tentativa é contada na reclamação,
     * por isso um crash a meio também a consome (T-136-23).
     *
     * <p>WR-01: uma linha que já gastou {@code :maxTentativas} reclamações nunca volta a ser
     * reclamada. Se o worker que a tinha morreu (ou o registo do resultado falhou), ela fica para
     * {@link #SQL_ENCERRAR_ESGOTADAS}, que a fecha em {@code ERRO} -- assim uma linha "venenosa"
     * não é reclamada para sempre nem ocupa lugar no lote.
     */
    private static final String SQL_RECLAMAR = """
            WITH devidas AS (
                SELECT id FROM t_comunicacao_fiscal
                 WHERE estado = 'PENDENTE'
                   AND tentativas < :maxTentativas
                   AND (proxima_tentativa_em IS NULL OR proxima_tentativa_em <= :agora)
                   AND (lease_ate IS NULL OR lease_ate < :agora)
                 ORDER BY created_at, id
                 LIMIT :lote
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE t_comunicacao_fiscal c
               SET lease_ate = :leaseAte,
                   tentativas = c.tentativas + 1,
                   ultima_tentativa_em = :agora,
                   versao = c.versao + 1,
                   updated_at = :agora
              FROM devidas
             WHERE c.id = devidas.id
            RETURNING c.id, c.tenant_id, c.documento_fiscal_id, c.ambiente, c.tentativas, c.versao,
                      c.reprocessamentos, c.created_at
            """;

    /**
     * WR-01: fecha em {@code ERRO} as linhas {@code PENDENTE} que já gastaram todas as reclamações e
     * cujo lease expirou (o worker morreu, ficou pendurado para lá do lease, ou não conseguiu registar
     * o resultado). Código e mensagem fixos (vêm de quem chama). {@code versao + 1}: um worker atrasado
     * que ainda tenha a linha já não grava por cima. Devolve as linhas fechadas para notificação.
     */
    private static final String SQL_ENCERRAR_ESGOTADAS = """
            WITH esgotadas AS (
                SELECT id FROM t_comunicacao_fiscal
                 WHERE estado = 'PENDENTE'
                   AND tentativas >= :maxTentativas
                   AND (lease_ate IS NULL OR lease_ate < :agora)
                 ORDER BY created_at, id
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE t_comunicacao_fiscal c
               SET estado = 'ERRO',
                   ultimo_erro = :mensagem,
                   ultimo_erro_codigo = :codigo,
                   proxima_tentativa_em = NULL,
                   lease_ate = NULL,
                   concluido_em = :agora,
                   versao = c.versao + 1,
                   updated_at = :agora
              FROM esgotadas
             WHERE c.id = esgotadas.id
            RETURNING c.id, c.tenant_id, c.documento_fiscal_id, c.ambiente, c.tentativas, c.versao,
                      c.reprocessamentos, c.created_at
            """;

    /** Só grava se a linha ainda tiver a versão reclamada (lease perdido -> 0 linhas) e o tenant certo. */
    private static final String SQL_REGISTAR_RESULTADO = """
            UPDATE t_comunicacao_fiscal
               SET estado = :estado,
                   ultimo_erro = :mensagem,
                   ultimo_erro_codigo = :codigo,
                   proxima_tentativa_em = :proxima,
                   lease_ate = NULL,
                   concluido_em = :concluido,
                   versao = versao + 1,
                   updated_at = :agora
             WHERE id = :id AND tenant_id = :tenantId AND versao = :versao
            """;

    /**
     * Phase 136-14 (DFE-05): reprocessamento manual. Só uma comunicação em ERRO ou REJEITADO do
     * tenant indicado volta a PENDENTE, devida já, com as tentativas a zero, o erro limpo e o
     * contador de reprocessamentos + 1 (novo episódio de falha). Qualquer outro estado -> 0 linhas.
     */
    private static final String SQL_REPOR_PENDENTE = """
            UPDATE t_comunicacao_fiscal
               SET estado = 'PENDENTE',
                   tentativas = 0,
                   proxima_tentativa_em = :agora,
                   ultimo_erro = NULL,
                   ultimo_erro_codigo = NULL,
                   lease_ate = NULL,
                   concluido_em = NULL,
                   reprocessamentos = reprocessamentos + 1,
                   versao = versao + 1,
                   updated_at = :agora
             WHERE tenant_id = :tenantId AND documento_fiscal_id = :documentoId
               AND estado IN ('ERRO', 'REJEITADO')
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Reclama até {@code lote} comunicações devidas com bloqueio de linha que salta as já bloqueadas: dois
     * workers concorrentes nunca recebem a mesma linha. Cada linha reclamada fica com o lease até
     * {@code leaseAte}, {@code tentativas + 1} e {@code versao + 1}. Linhas com
     * {@code tentativas >= maxTentativas} não são reclamadas (WR-01).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ComunicacaoReclamada> reclamar(Instant agora, Instant leaseAte, int lote, int maxTentativas) {
        if (lote <= 0) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = entityManager.createNativeQuery(SQL_RECLAMAR)
                .unwrap(NativeQuery.class)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("leaseAte", leaseAte, StandardBasicTypes.INSTANT)
                .setParameter("lote", lote, StandardBasicTypes.INTEGER)
                .setParameter("maxTentativas", maxTentativas, StandardBasicTypes.INTEGER)
                .getResultList();
        return mapear(linhas);
    }

    /**
     * WR-01: fecha em {@code ERRO} as linhas que esgotaram as reclamações sem resultado registado e
     * devolve-as (para a notificação). Código e mensagem são gravados truncados às colunas.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ComunicacaoReclamada> encerrarEsgotadas(Instant agora, int maxTentativas, String codigo,
                                                        String mensagem) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = entityManager.createNativeQuery(SQL_ENCERRAR_ESGOTADAS)
                .unwrap(NativeQuery.class)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("maxTentativas", maxTentativas, StandardBasicTypes.INTEGER)
                .setParameter("codigo", truncar(codigo, MAX_CODIGO), StandardBasicTypes.STRING)
                .setParameter("mensagem", truncar(mensagem, MAX_MENSAGEM), StandardBasicTypes.STRING)
                .getResultList();
        return mapear(linhas);
    }

    private static List<ComunicacaoReclamada> mapear(List<Object[]> linhas) {
        List<Object[]> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort(Comparator.<Object[], Instant>comparing(l -> instante(l[7]))
                .thenComparing(l -> uuid(l[0])));
        List<ComunicacaoReclamada> resultado = new ArrayList<>(ordenadas.size());
        for (Object[] l : ordenadas) {
            resultado.add(new ComunicacaoReclamada(uuid(l[0]), uuid(l[1]), uuid(l[2]),
                    AmbienteFiscal.valueOf((String) l[3]), ((Number) l[4]).intValue(),
                    ((Number) l[5]).longValue(), ((Number) l[6]).intValue()));
        }
        return resultado;
    }

    /**
     * Regista o resultado de uma tentativa. Devolve 1 se gravou, 0 se a linha já não tem a versão
     * reclamada (outro worker reclamou-a depois de o lease expirar) ou não pertence ao tenant.
     * {@code mensagem} e {@code codigo} são truncados defensivamente às colunas (T-136-24).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int registarResultado(UUID id, UUID tenantId, long versao, EstadoComunicacaoFiscal estado,
                                 String codigo, String mensagem, Instant proxima, Instant concluido,
                                 Instant agora) {
        return entityManager.createNativeQuery(SQL_REGISTAR_RESULTADO)
                .unwrap(NativeQuery.class)
                .setParameter("estado", estado.name(), StandardBasicTypes.STRING)
                .setParameter("mensagem", truncar(mensagem, MAX_MENSAGEM), StandardBasicTypes.STRING)
                .setParameter("codigo", truncar(codigo, MAX_CODIGO), StandardBasicTypes.STRING)
                .setParameter("proxima", proxima, StandardBasicTypes.INSTANT)
                .setParameter("concluido", concluido, StandardBasicTypes.INSTANT)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("id", id, StandardBasicTypes.UUID)
                .setParameter("tenantId", tenantId, StandardBasicTypes.UUID)
                .setParameter("versao", versao, StandardBasicTypes.LONG)
                .executeUpdate();
    }

    /**
     * Repõe em PENDENTE a comunicação em falha de um documento do tenant. Devolve 1 se repôs, 0 se
     * a linha não existe nesse tenant ou não está em ERRO/REJEITADO.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int reporPendente(UUID tenantId, UUID documentoFiscalId, Instant agora) {
        return entityManager.createNativeQuery(SQL_REPOR_PENDENTE)
                .unwrap(NativeQuery.class)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("tenantId", tenantId, StandardBasicTypes.UUID)
                .setParameter("documentoId", documentoFiscalId, StandardBasicTypes.UUID)
                .executeUpdate();
    }

    static String truncar(String valor, int maximo) {
        if (valor == null || valor.length() <= maximo) {
            return valor;
        }
        return valor.substring(0, maximo);
    }

    private static UUID uuid(Object valor) {
        return valor instanceof UUID u ? u : UUID.fromString(valor.toString());
    }

    private static Instant instante(Object valor) {
        if (valor instanceof Instant i) {
            return i;
        }
        if (valor instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (valor instanceof Timestamp t) {
            return t.toInstant();
        }
        return Instant.parse(valor.toString());
    }
}
