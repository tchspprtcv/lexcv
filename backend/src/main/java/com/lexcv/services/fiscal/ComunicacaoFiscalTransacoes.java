package com.lexcv.services.fiscal;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import com.lexcv.repositories.FilaComunicacaoFiscal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 136 (DFE-04, DFE-06): as transações CURTAS da fila de comunicação fiscal, cada uma num
 * método público deste bean.
 *
 * <p>Estão num bean separado de propósito: o job/processador chama-as através do proxy do Spring
 * (uma chamada {@code this.metodo()} dentro da mesma classe ignoraria o {@code @Transactional}) e
 * não tem ele próprio {@code @Transactional}. Assim nenhuma transação envolve a construção do XML,
 * a validação ou a chamada ao gateway: reclamar (tx 1) -> carregar snapshot (tx só de leitura) ->
 * [XML + validação fora de transação] -> gravar XML (tx 2) -> [gateway fora de transação] ->
 * registar resultado (tx 3). Nenhum método daqui faz trabalho de XML nem chama o gateway.
 *
 * <p>Depois da reclamação (multi-tenant por desenho), cada leitura e escrita usa o
 * {@code tenantId} da linha reclamada (T-136-21).
 */
@Service
public class ComunicacaoFiscalTransacoes {

    private final FilaComunicacaoFiscal fila;
    private final DocumentoFiscalRepository documentoRepository;
    private final DocumentoFiscalLinhaRepository linhaRepository;
    private final DocumentoFiscalXmlRepository xmlRepository;
    private final Clock clock;

    public ComunicacaoFiscalTransacoes(FilaComunicacaoFiscal fila, DocumentoFiscalRepository documentoRepository,
                                       DocumentoFiscalLinhaRepository linhaRepository,
                                       DocumentoFiscalXmlRepository xmlRepository, Clock clock) {
        this.fila = fila;
        this.documentoRepository = documentoRepository;
        this.linhaRepository = linhaRepository;
        this.xmlRepository = xmlRepository;
        this.clock = clock;
    }

    /**
     * Tx 1: reclama até {@code lote} linhas devidas com um lease de {@code lease}. Linhas que já
     * gastaram {@link EstadoComunicacaoMapper#MAX_TENTATIVAS} reclamações ficam de fora (WR-01).
     */
    @Transactional
    public List<ComunicacaoReclamada> reclamar(int lote, Duration lease) {
        Instant agora = clock.instant();
        return fila.reclamar(agora, agora.plus(lease), lote, EstadoComunicacaoMapper.MAX_TENTATIVAS);
    }

    /**
     * WR-01: fecha em {@code ERRO} ({@code FALHA_INTERNA}, mensagem fixa) as linhas que gastaram
     * todas as reclamações sem nunca registarem um resultado (worker morto, gateway pendurado para
     * lá do lease, registo do resultado sempre a falhar) e devolve-as para a notificação.
     */
    @Transactional
    public List<ComunicacaoReclamada> encerrarEsgotadas() {
        return fila.encerrarEsgotadas(clock.instant(), EstadoComunicacaoMapper.MAX_TENTATIVAS,
                ProcessadorComunicacaoFiscal.FALHA_INTERNA, ProcessadorComunicacaoFiscal.MSG_FALHA_INTERNA);
    }

    /**
     * Snapshot do documento, sempre pelo tenant da linha reclamada. Vazio se o documento (ou a sua
     * linha) não existir nesse tenant. Numa NC, inclui o número e o IUD da FR de origem, lidos no
     * mesmo tenant.
     */
    @Transactional(readOnly = true)
    public Optional<SnapshotComunicacao> carregarSnapshot(UUID tenantId, UUID documentoFiscalId) {
        Optional<DocumentoFiscal> documento = documentoRepository.findByIdAndTenantId(documentoFiscalId, tenantId);
        if (documento.isEmpty()) {
            return Optional.empty();
        }
        List<DocumentoFiscalLinha> linhas =
                linhaRepository.findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(tenantId, documentoFiscalId);
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        DocumentoFiscal d = documento.get();
        Optional<DocumentoFiscalXml> xmlExistente =
                xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoFiscalId);

        Optional<String> iudOrigem = Optional.empty();
        Optional<String> numeroOrigem = Optional.empty();
        if (d.getTipo() == TipoDocumentoFiscal.NC && d.getDocumentoOrigemId() != null) {
            UUID origemId = d.getDocumentoOrigemId();
            numeroOrigem = documentoRepository.findByIdAndTenantId(origemId, tenantId)
                    .map(DocumentoFiscal::getNumeroFormatado);
            iudOrigem = xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, origemId)
                    .map(DocumentoFiscalXml::getIud);
        }
        return Optional.of(new SnapshotComunicacao(d, linhas.get(0), xmlExistente, iudOrigem, numeroOrigem));
    }

    /**
     * Tx 2: grava o XML (insert-only, ignora conflito) e devolve a linha que ficou -- a nossa ou a
     * de quem ganhou a corrida para o mesmo documento. Vazio só numa colisão de IUD com outro
     * documento, que quem chama trata como transitória (novo IUD na próxima tentativa).
     */
    @Transactional
    public Optional<DocumentoFiscalXml> gravarXml(UUID tenantId, UUID documentoFiscalId, String iud,
                                                  AmbienteFiscal ambiente, int repositorioCodigo, int ledCodigo,
                                                  String versaoFormato, String xml, String xmlSha256) {
        xmlRepository.inserirSeAusente(UUID.randomUUID(), tenantId, documentoFiscalId, iud, ambiente.name(),
                repositorioCodigo, ledCodigo, versaoFormato, xml, xmlSha256, clock.instant());
        return xmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoFiscalId);
    }

    /**
     * WR-02: tx curta imediatamente antes do envio ao gateway. Renova o lease do item por mais
     * {@code lease} a partir de agora, para que o lease cubra o envio deste item (e não só o momento da
     * reclamação do lote). Devolve {@code false} se o worker já não é o dono da linha: nesse caso o
     * item NÃO pode ser enviado.
     */
    @Transactional
    public boolean renovarLease(ComunicacaoReclamada item, Duration lease) {
        Instant agora = clock.instant();
        return fila.renovarLease(item.id(), item.tenantId(), item.versao(), agora, agora.plus(lease)) == 1;
    }

    /**
     * Tx 3: regista o resultado da tentativa, guardado pela versão reclamada e pelo tenant.
     * {@code concluido_em} só é preenchido num estado terminal. Devolve as linhas atualizadas
     * (0 = lease perdido).
     */
    @Transactional
    public int registarResultado(ComunicacaoReclamada item, EstadoComunicacaoFiscal estado, String codigo,
                                 String mensagem, Instant proxima) {
        Instant agora = clock.instant();
        Instant concluido = estado.terminal() ? agora : null;
        return fila.registarResultado(item.id(), item.tenantId(), item.versao(), estado, codigo, mensagem,
                proxima, concluido, agora);
    }
}
