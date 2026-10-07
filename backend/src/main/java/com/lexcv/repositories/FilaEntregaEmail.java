package com.lexcv.repositories;

import com.lexcv.models.EstadoEntregaEmail;
import com.lexcv.services.fiscal.EntregaEmailReclamada;
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
 * Phase 137 (ENTR-03, ENTR-04, ENTR-05): SQL nativo da fila de entrega por email (outbox) sobre
 * {@code t_entrega_email_fiscal}. Espelha {@link FilaComunicacaoFiscal} (Phase 136).
 *
 * <p>Não vive em {@link EntregaEmailFiscalRepository}: esse repositório tem o conjunto de métodos
 * fixado por {@code DocumentoFiscalImutabilidadeTest} (só finders por tenant, sem {@code save}). A
 * reclamação aqui é <b>deliberadamente multi-tenant</b> -- o job corre sem SecurityContext -- e é a
 * ÚNICA instrução multi-tenant da fila. Devolve o {@code tenant_id} de cada linha, e todas as
 * instruções seguintes (snapshot, renovação, resultado) ficam presas a esse tenant (T-137-24).
 *
 * <p>Os métodos exigem uma transação já aberta (MANDATORY): só correm dentro das transações curtas de
 * {@code EntregaEmailTransacoes} (ou da transação de quem cria a linha), nunca à volta da geração do
 * PDF, do MinIO ou do SMTP. Só códigos e mensagens fixos chegam à base de dados, truncados às
 * colunas (T-137-27).
 */
@Repository
public class FilaEntregaEmail {

    static final int MAX_MENSAGEM = 500;
    static final int MAX_CODIGO = 64;
    static final int MAX_DESTINATARIO = 254;

    /** Cria a linha de entrega do documento, no máximo uma vez (uk_entrega_email_fiscal_documento). */
    private static final String SQL_CRIAR_SE_AUSENTE = """
            INSERT INTO t_entrega_email_fiscal (id, tenant_id, documento_fiscal_id, estado, destinatario,
                                                tentativas, reenvios, created_at, updated_at, versao)
            VALUES (:id, :tenantId, :documentoId, :estado, :destinatario, 0, 0, :agora, :agora, 0)
            ON CONFLICT DO NOTHING
            """;

    /**
     * Reclama até {@code :lote} linhas devidas, as mais antigas primeiro. {@code proxima_tentativa_em}
     * {@code NULL} conta como devida. Um lease expirado volta a ser reclamável (recuperação de crash).
     * A tentativa é contada na reclamação, por isso um crash a meio também a consome (T-137-26).
     * Linhas com {@code tentativas >= :maxTentativas} nunca são reclamadas: ficam para
     * {@link #SQL_ENCERRAR_ESGOTADAS}. {@code DESLIGADO}, {@code SEM_EMAIL}, {@code ENVIADO} e
     * {@code FALHOU} nunca são reclamadas.
     */
    private static final String SQL_RECLAMAR = """
            WITH devidas AS (
                SELECT id FROM t_entrega_email_fiscal
                 WHERE estado = 'PENDENTE'
                   AND tentativas < :maxTentativas
                   AND (proxima_tentativa_em IS NULL OR proxima_tentativa_em <= :agora)
                   AND (lease_ate IS NULL OR lease_ate < :agora)
                 ORDER BY created_at, id
                 LIMIT :lote
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE t_entrega_email_fiscal e
               SET lease_ate = :leaseAte,
                   tentativas = e.tentativas + 1,
                   ultima_tentativa_em = :agora,
                   versao = e.versao + 1,
                   updated_at = :agora
              FROM devidas
             WHERE e.id = devidas.id
            RETURNING e.id, e.tenant_id, e.documento_fiscal_id, e.destinatario, e.tentativas, e.versao,
                      e.reenvios, e.created_at
            """;

    /**
     * Fecha em {@code FALHOU} as linhas {@code PENDENTE} que já gastaram todas as reclamações e cujo
     * lease expirou (worker morto, SMTP pendurado para lá do lease, registo do resultado a falhar).
     * A mensagem é o modelo fixo com o número de tentativas da própria linha (singular/plural); só o
     * inteiro varia. {@code versao + 1}: um worker atrasado já não grava por cima. Devolve as linhas
     * fechadas para a notificação {@code EMAIL_FISCAL_FALHOU}.
     *
     * <p>UPDATE simples (sem {@code SKIP LOCKED}): estas linhas nunca são reclamadas (cap no predicado
     * da reclamação) e, se dois workers fecharem ao mesmo tempo, o segundo espera pelo bloqueio de
     * linha, reavalia {@code estado = 'PENDENTE'} e salta-a -- cada linha é devolvida uma só vez.
     */
    private static final String SQL_ENCERRAR_ESGOTADAS = """
            UPDATE t_entrega_email_fiscal e
               SET estado = 'FALHOU',
                   ultimo_erro = 'O envio do email falhou após ' || e.tentativas
                                 || CASE WHEN e.tentativas = 1 THEN ' tentativa.' ELSE ' tentativas.' END,
                   ultimo_erro_codigo = :codigo,
                   proxima_tentativa_em = NULL,
                   lease_ate = NULL,
                   versao = e.versao + 1,
                   updated_at = :agora
             WHERE e.estado = 'PENDENTE'
               AND e.tentativas >= :maxTentativas
               AND (e.lease_ate IS NULL OR e.lease_ate < :agora)
            RETURNING e.id, e.tenant_id, e.documento_fiscal_id, e.destinatario, e.tentativas, e.versao,
                      e.reenvios, e.created_at
            """;


    /**
     * Só grava se a linha ainda tiver a versão reclamada (lease perdido -> 0 linhas) e o tenant certo.
     * {@code enviado_em} só muda quando é dado; em {@code ENVIADO} o erro anterior é limpo.
     */
    private static final String SQL_REGISTAR_RESULTADO = """
            UPDATE t_entrega_email_fiscal
               SET estado = :estado,
                   ultimo_erro = CASE WHEN :estado = 'ENVIADO' THEN NULL ELSE :mensagem END,
                   ultimo_erro_codigo = CASE WHEN :estado = 'ENVIADO' THEN NULL ELSE :codigo END,
                   proxima_tentativa_em = :proxima,
                   lease_ate = NULL,
                   enviado_em = COALESCE(:enviadoEm, enviado_em),
                   versao = versao + 1,
                   updated_at = :agora
             WHERE id = :id AND tenant_id = :tenantId AND versao = :versao
            """;

    /**
     * Renova o lease de UMA linha imediatamente antes do envio SMTP (T-137-25). Só renova se a linha
     * ainda tiver a versão reclamada e ainda estiver PENDENTE: 0 linhas = o worker já não é o dono e
     * NÃO pode enviar. Não muda a versão, para que o resultado continue a ser gravado com a versão
     * reclamada.
     */
    private static final String SQL_RENOVAR_LEASE = """
            UPDATE t_entrega_email_fiscal
               SET lease_ate = :leaseAte,
                   updated_at = :agora
             WHERE id = :id AND tenant_id = :tenantId AND versao = :versao AND estado = 'PENDENTE'
            """;

    /**
     * Reenvio manual (ENTR-04): novo episódio. Só a partir de FALHOU, ENVIADO ou SEM_EMAIL, no tenant
     * indicado: volta a PENDENTE, devida já, com as tentativas a zero, o erro limpo, o destinatário
     * atual do cliente e {@code reenvios + 1}. Qualquer outro estado (PENDENTE, DESLIGADO) -> 0 linhas.
     */
    private static final String SQL_REPOR_PENDENTE = """
            UPDATE t_entrega_email_fiscal
               SET estado = 'PENDENTE',
                   destinatario = :destinatario,
                   tentativas = 0,
                   proxima_tentativa_em = NULL,
                   lease_ate = NULL,
                   ultima_tentativa_em = NULL,
                   ultimo_erro = NULL,
                   ultimo_erro_codigo = NULL,
                   reenvios = reenvios + 1,
                   versao = versao + 1,
                   updated_at = :agora
             WHERE tenant_id = :tenantId AND documento_fiscal_id = :documentoId
               AND estado IN ('FALHOU', 'ENVIADO', 'SEM_EMAIL')
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Cria a linha de entrega do documento na transação de quem chama; não faz nada se já existir.
     *
     * @return 1 se criou, 0 se o documento já tinha linha
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int criarSeAusente(UUID id, UUID tenantId, UUID documentoFiscalId, EstadoEntregaEmail estado,
                              String destinatario, Instant agora) {
        return entityManager.createNativeQuery(SQL_CRIAR_SE_AUSENTE)
                .unwrap(NativeQuery.class)
                .setParameter("id", id, StandardBasicTypes.UUID)
                .setParameter("tenantId", tenantId, StandardBasicTypes.UUID)
                .setParameter("documentoId", documentoFiscalId, StandardBasicTypes.UUID)
                .setParameter("estado", estado.name(), StandardBasicTypes.STRING)
                .setParameter("destinatario", destinatario(destinatario), StandardBasicTypes.STRING)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .executeUpdate();
    }

    /**
     * Reclama até {@code lote} entregas devidas com bloqueio de linha que salta as já bloqueadas: dois
     * workers concorrentes nunca recebem a mesma linha. É a única instrução multi-tenant da fila; tudo
     * o que vem depois usa o {@code tenantId} devolvido.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EntregaEmailReclamada> reclamar(Instant agora, Instant leaseAte, int lote, int maxTentativas) {
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
     * Fecha em {@code FALHOU} as linhas que esgotaram as reclamações sem resultado registado e
     * devolve-as (para a notificação). A mensagem conta as tentativas da própria linha.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<EntregaEmailReclamada> encerrarEsgotadas(Instant agora, int maxTentativas, String codigo) {
        @SuppressWarnings("unchecked")
        List<Object[]> linhas = entityManager.createNativeQuery(SQL_ENCERRAR_ESGOTADAS)
                .unwrap(NativeQuery.class)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("maxTentativas", maxTentativas, StandardBasicTypes.INTEGER)
                .setParameter("codigo", truncar(codigo, MAX_CODIGO), StandardBasicTypes.STRING)
                .getResultList();
        return mapear(linhas);
    }

    /**
     * Renova o lease de uma linha reclamada até {@code leaseAte}. Devolve 1 se o worker ainda é o dono
     * (versão e tenant certos, ainda PENDENTE), 0 caso contrário.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int renovarLease(UUID id, UUID tenantId, long versao, Instant agora, Instant leaseAte) {
        return entityManager.createNativeQuery(SQL_RENOVAR_LEASE)
                .unwrap(NativeQuery.class)
                .setParameter("leaseAte", leaseAte, StandardBasicTypes.INSTANT)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("id", id, StandardBasicTypes.UUID)
                .setParameter("tenantId", tenantId, StandardBasicTypes.UUID)
                .setParameter("versao", versao, StandardBasicTypes.LONG)
                .executeUpdate();
    }

    /**
     * Regista o resultado de uma tentativa. Devolve 1 se gravou, 0 se a linha já não tem a versão
     * reclamada ou não pertence ao tenant. {@code mensagem} e {@code codigo} são truncados às colunas.
     *
     * @param enviadoEm só para {@code ENVIADO}; {@code null} mantém o valor atual
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int registarResultado(UUID id, UUID tenantId, long versao, EstadoEntregaEmail estado, String codigo,
                                 String mensagem, Instant proxima, Instant enviadoEm, Instant agora) {
        return entityManager.createNativeQuery(SQL_REGISTAR_RESULTADO)
                .unwrap(NativeQuery.class)
                .setParameter("estado", estado.name(), StandardBasicTypes.STRING)
                .setParameter("mensagem", truncar(mensagem, MAX_MENSAGEM), StandardBasicTypes.STRING)
                .setParameter("codigo", truncar(codigo, MAX_CODIGO), StandardBasicTypes.STRING)
                .setParameter("proxima", proxima, StandardBasicTypes.INSTANT)
                .setParameter("enviadoEm", enviadoEm, StandardBasicTypes.INSTANT)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("id", id, StandardBasicTypes.UUID)
                .setParameter("tenantId", tenantId, StandardBasicTypes.UUID)
                .setParameter("versao", versao, StandardBasicTypes.LONG)
                .executeUpdate();
    }

    /**
     * Reenvio manual: novo episódio a partir de FALHOU, ENVIADO ou SEM_EMAIL no tenant indicado.
     *
     * @return 1 se repôs, 0 se a linha não existe nesse tenant ou está noutro estado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int reporPendente(UUID tenantId, UUID documentoFiscalId, String destinatario, Instant agora) {
        return entityManager.createNativeQuery(SQL_REPOR_PENDENTE)
                .unwrap(NativeQuery.class)
                .setParameter("destinatario", destinatario(destinatario), StandardBasicTypes.STRING)
                .setParameter("agora", agora, StandardBasicTypes.INSTANT)
                .setParameter("tenantId", tenantId, StandardBasicTypes.UUID)
                .setParameter("documentoId", documentoFiscalId, StandardBasicTypes.UUID)
                .executeUpdate();
    }

    private static List<EntregaEmailReclamada> mapear(List<Object[]> linhas) {
        List<Object[]> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort(Comparator.<Object[], Instant>comparing(l -> instante(l[7]))
                .thenComparing(l -> uuid(l[0])));
        List<EntregaEmailReclamada> resultado = new ArrayList<>(ordenadas.size());
        for (Object[] l : ordenadas) {
            resultado.add(new EntregaEmailReclamada(uuid(l[0]), uuid(l[1]), uuid(l[2]), (String) l[3],
                    ((Number) l[4]).intValue(), ((Number) l[5]).longValue(), ((Number) l[6]).intValue()));
        }
        return resultado;
    }

    /**
     * O destinatário nunca é truncado (um endereço cortado seria outro endereço): acima de 254
     * caracteres é recusado. Quem chama já o validou com {@code RegrasEntregaEmail.emailValido}.
     */
    private static String destinatario(String valor) {
        if (valor != null && valor.length() > MAX_DESTINATARIO) {
            throw new IllegalArgumentException("Destinatário acima do comprimento máximo");
        }
        return valor;
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
