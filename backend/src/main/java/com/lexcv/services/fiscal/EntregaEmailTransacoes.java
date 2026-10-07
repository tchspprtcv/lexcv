package com.lexcv.services.fiscal;

import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import com.lexcv.repositories.FilaEntregaEmail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 137 (ENTR-03, ENTR-04, ENTR-05): as transações CURTAS da fila de entrega por email, cada uma
 * num método público deste bean. Espelha {@link ComunicacaoFiscalTransacoes}.
 *
 * <p>Estão num bean separado de propósito: o job/processador (137-14) chama-as através do proxy do
 * Spring (uma chamada {@code this.metodo()} dentro da mesma classe ignoraria o
 * {@code @Transactional}) e não tem ele próprio {@code @Transactional}. Assim nenhuma transação
 * envolve a geração do PDF, o MinIO ou o SMTP: reclamar (tx 1) -> carregar snapshot (tx só de
 * leitura) -> [PDF + MinIO fora de transação] -> renovar lease (tx curta) -> [SMTP fora de
 * transação] -> registar resultado (tx 2). Nenhum método daqui faz esse trabalho.
 *
 * <p>Depois da reclamação (multi-tenant por desenho), cada leitura e escrita usa o {@code tenantId}
 * da linha reclamada (T-137-24).
 */
@Service
public class EntregaEmailTransacoes {

    /** Código gravado quando uma linha esgota as tentativas sem resultado registado. */
    public static final String TENTATIVAS_ESGOTADAS = "TENTATIVAS_ESGOTADAS";

    private final FilaEntregaEmail fila;
    private final DocumentoFiscalRepository documentoRepository;
    private final DocumentoFiscalXmlRepository xmlRepository;
    private final ConfiguracaoFiscalRepository configuracaoRepository;
    private final Clock clock;

    public EntregaEmailTransacoes(FilaEntregaEmail fila, DocumentoFiscalRepository documentoRepository,
                                  DocumentoFiscalXmlRepository xmlRepository,
                                  ConfiguracaoFiscalRepository configuracaoRepository, Clock clock) {
        this.fila = fila;
        this.documentoRepository = documentoRepository;
        this.xmlRepository = xmlRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.clock = clock;
    }

    /**
     * Tx 1: reclama até {@code lote} entregas devidas com um lease de {@code lease}. Linhas que já
     * gastaram {@link EntregaEmailFiscal#MAX_TENTATIVAS} reclamações ficam de fora.
     */
    @Transactional
    public List<EntregaEmailReclamada> reclamar(int lote, Duration lease) {
        Instant agora = clock.instant();
        return fila.reclamar(agora, agora.plus(lease), lote, EntregaEmailFiscal.MAX_TENTATIVAS);
    }

    /**
     * Fecha em {@code FALHOU} ({@link #TENTATIVAS_ESGOTADAS}, "O envio do email falhou após {n}
     * tentativa(s).") as linhas que gastaram todas as reclamações sem nunca registarem um resultado e
     * devolve-as para a notificação {@code EMAIL_FISCAL_FALHOU}.
     */
    @Transactional
    public List<EntregaEmailReclamada> encerrarEsgotadas() {
        return fila.encerrarEsgotadas(clock.instant(), EntregaEmailFiscal.MAX_TENTATIVAS, TENTATIVAS_ESGOTADAS);
    }

    /**
     * Snapshot do documento, sempre pelo tenant da linha reclamada. Vazio se o documento não existir
     * nesse tenant. Numa NC inclui o número da FR de origem, lido no mesmo tenant.
     */
    @Transactional(readOnly = true)
    public Optional<SnapshotEntregaEmail> carregarSnapshot(UUID tenantId, UUID documentoFiscalId) {
        Optional<DocumentoFiscal> documento = documentoRepository.findByIdAndTenantId(documentoFiscalId, tenantId);
        if (documento.isEmpty()) {
            return Optional.empty();
        }
        DocumentoFiscal d = documento.get();
        Optional<DocumentoFiscalXml> xml = xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoFiscalId);
        Optional<String> numeroOrigem = Optional.empty();
        if (d.getTipo() == TipoDocumentoFiscal.NC && d.getDocumentoOrigemId() != null) {
            numeroOrigem = documentoRepository.findByIdAndTenantId(d.getDocumentoOrigemId(), tenantId)
                    .map(DocumentoFiscal::getNumeroFormatado);
        }
        Optional<String> replyTo = configuracaoRepository.findByTenantId(tenantId)
                .map(ConfiguracaoFiscal::getEmailContacto)
                .filter(e -> !e.isBlank());
        return Optional.of(new SnapshotEntregaEmail(d, xml, numeroOrigem, replyTo));
    }

    /**
     * Tx curta imediatamente antes do envio SMTP: renova o lease por mais {@code lease} a partir de
     * agora. Devolve {@code false} se o worker já não é o dono da linha: nesse caso NÃO pode enviar.
     */
    @Transactional
    public boolean renovarLease(EntregaEmailReclamada item, Duration lease) {
        Instant agora = clock.instant();
        return fila.renovarLease(item.id(), item.tenantId(), item.versao(), agora, agora.plus(lease)) == 1;
    }

    /**
     * Tx 2: regista o resultado da tentativa, guardado pela versão reclamada e pelo tenant.
     * {@code enviado_em} só é preenchido em {@code ENVIADO}. Devolve as linhas atualizadas
     * (0 = lease perdido).
     */
    @Transactional
    public int registarResultado(EntregaEmailReclamada item, EstadoEntregaEmail estado, String codigo,
                                 String mensagem, Instant proxima) {
        Instant agora = clock.instant();
        Instant enviadoEm = estado == EstadoEntregaEmail.ENVIADO ? agora : null;
        return fila.registarResultado(item.id(), item.tenantId(), item.versao(), estado, codigo, mensagem,
                proxima, enviadoEm, agora);
    }
}
