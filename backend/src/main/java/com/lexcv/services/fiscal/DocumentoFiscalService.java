package com.lexcv.services.fiscal;

import com.lexcv.dtos.ComunicacaoFiscalResumo;
import com.lexcv.dtos.DocumentoFiscalDetalheResponse;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.DocumentoFiscalResumoResponse;
import com.lexcv.dtos.EntregaEmailResumo;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalLigacaoClienteRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import com.lexcv.repositories.EntregaEmailFiscalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 134 (EMIS-09, EMIS-11, EMIS-12, D-14..D-19): lado de leitura dos documentos fiscais e
 * ajudas para as guardas de eliminação e para a fusão de clientes.
 *
 * <p>É a ÚNICA porta do {@code ResourceController} para os dados fiscais: o controlador nunca
 * injeta repositórios fiscais, o que mantém simples e significativa a guarda de código-fonte
 * CFG-03 (plano 08).
 *
 * <p>Todo o método público recebe o {@code tenantId} como primeiro parâmetro (vem do principal no
 * controlador); este serviço nunca lê o contexto de segurança.
 *
 * <p>Phase 135 (NCRD-01..03), Notas de Crédito: o detalhe de uma FR traz as NC emitidas sobre ela,
 * o total creditado e o valor ainda creditável; o detalhe de uma NC traz a referência à FR de
 * origem e o motivo; a listagem traz, numa só consulta adicional por página, o número da FR de
 * origem de cada NC. Nos pagamentos, {@link #referenciasPorPagamento} devolve só Faturas-Recibo e
 * {@link #estornosPorPagamento} só NC (uma NC guarda em {@code pagamento_id} o seu estorno), por
 * isso um estorno nunca aparece como FR. {@link #eEstornoDeNotaCredito} serve a guarda de
 * eliminação própria do estorno. Origem e NC são sempre lidas com o tenant do chamador: um id de
 * outro escritório continua 404.
 *
 * <p>Phase 137 (ENTR-04, ENTR-06): o detalhe traz a entrega por email ({@link EntregaEmailResumo},
 * com o email atual da ficha do cliente lido no mesmo tenant) e a listagem o estado apresentado da
 * entrega de cada linha, numa só consulta adicional por página. Sem SMTP configurado, as entregas
 * que ainda não tentaram enviar aparecem como {@code NAO_CONFIGURADO}.
 */
@Service
public class DocumentoFiscalService {

    /** Limite defensivo do tamanho de página (o controlador devolve 400 antes). */
    static final int TAMANHO_MAXIMO = 100;

    static final String MSG_NAO_ENCONTRADO = "Documento fiscal não encontrado.";

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;
    private final ComunicacaoFiscalRepository comunicacaoFiscalRepository;
    private final DocumentoFiscalLigacaoClienteRepository documentoFiscalLigacaoClienteRepository;
    private final DocumentoFiscalXmlRepository documentoFiscalXmlRepository;
    private final EntregaEmailFiscalRepository entregaEmailFiscalRepository;
    private final ConfiguracaoFiscalRepository configuracaoFiscalRepository;
    private final ClienteRepository clienteRepository;
    private final boolean smtpConfigurado;

    public DocumentoFiscalService(DocumentoFiscalRepository documentoFiscalRepository,
                                  DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository,
                                  ComunicacaoFiscalRepository comunicacaoFiscalRepository,
                                  DocumentoFiscalLigacaoClienteRepository documentoFiscalLigacaoClienteRepository,
                                  DocumentoFiscalXmlRepository documentoFiscalXmlRepository,
                                  EntregaEmailFiscalRepository entregaEmailFiscalRepository,
                                  ConfiguracaoFiscalRepository configuracaoFiscalRepository,
                                  ClienteRepository clienteRepository,
                                  EmailProperties emailProperties) {
        this.documentoFiscalRepository = documentoFiscalRepository;
        this.documentoFiscalLinhaRepository = documentoFiscalLinhaRepository;
        this.comunicacaoFiscalRepository = comunicacaoFiscalRepository;
        this.documentoFiscalLigacaoClienteRepository = documentoFiscalLigacaoClienteRepository;
        this.documentoFiscalXmlRepository = documentoFiscalXmlRepository;
        this.entregaEmailFiscalRepository = entregaEmailFiscalRepository;
        this.configuracaoFiscalRepository = configuracaoFiscalRepository;
        this.clienteRepository = clienteRepository;
        this.smtpConfigurado = emailProperties.configurado();
    }

    /**
     * Listagem filtrada e paginada no servidor (D-17). Todos os filtros são opcionais. A ordem é a
     * da query nativa (data de emissão, ano e número, decrescentes), por isso a página não tem
     * ordenação própria. Os estados de comunicação da página são lidos numa só consulta (sem N+1).
     */
    @Transactional(readOnly = true)
    public Page<DocumentoFiscalResumoResponse> listar(UUID tenantId, UUID clienteId, TipoDocumentoFiscal tipo,
                                                      EstadoComunicacaoFiscal estado, LocalDate de, LocalDate ate,
                                                      int page, int size) {
        if (page < 0 || size < 1 || size > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Paginação fora dos limites: page=" + page + ", size=" + size);
        }
        Page<DocumentoFiscal> documentos = documentoFiscalRepository.buscar(
                tenantId,
                clienteId == null ? null : clienteId.toString(),
                tipo == null ? null : tipo.name(),
                estado == null ? null : estado.name(),
                de,
                ate,
                PageRequest.of(page, size));
        if (documentos.isEmpty()) {
            return documentos.map(d -> DocumentoFiscalResumoResponse.de(d, null));
        }
        List<UUID> ids = documentos.getContent().stream().map(DocumentoFiscal::getId).toList();
        Map<UUID, EstadoComunicacaoFiscal> estados = comunicacaoFiscalRepository
                .findByTenantIdAndDocumentoFiscalIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(ComunicacaoFiscal::getDocumentoFiscalId, ComunicacaoFiscal::getEstado,
                        (a, b) -> a));
        // Phase 135: números das FR de origem das NC da página, numa só consulta (nenhuma sem NC).
        Set<UUID> origens = documentos.getContent().stream()
                .map(DocumentoFiscal::getDocumentoOrigemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> numerosOrigem = origens.isEmpty()
                ? Map.of()
                : documentoFiscalRepository.findByTenantIdAndIdIn(tenantId, origens).stream()
                        .collect(Collectors.toMap(DocumentoFiscal::getId, DocumentoFiscal::getNumeroFormatado,
                                (a, b) -> a));
        // Phase 137: estados de entrega por email da página, numa só consulta.
        Map<UUID, String> entregas = entregaEmailFiscalRepository.findByTenantIdAndDocumentoFiscalIdIn(tenantId, ids)
                .stream()
                .filter(e -> e.getEstado() != null)
                .collect(Collectors.toMap(EntregaEmailFiscal::getDocumentoFiscalId,
                        e -> RegrasEntregaEmail.estadoApresentado(e.getEstado(), smtpConfigurado), (a, b) -> a));
        return documentos.map(d -> DocumentoFiscalResumoResponse.de(d, estados.get(d.getId()),
                d.getDocumentoOrigemId() == null ? null : numerosOrigem.get(d.getDocumentoOrigemId()),
                entregas.get(d.getId())));
    }

    /**
     * Detalhe só de leitura (D-18). Um id de outro escritório tem a mesma resposta que um id
     * inexistente: 404 {@code DOCUMENTO_FISCAL_NAO_ENCONTRADO}.
     *
     * <p>Phase 135: numa FR carrega as NC emitidas sobre ela (mais recentes primeiro); numa NC
     * carrega a referência à FR de origem, sempre no mesmo tenant.
     *
     * <p>Phase 136 (DFE-04, DFE-06): traz o resumo da comunicação (estado, tentativas, último erro
     * sanitizado, próxima tentativa) e o IUD do satélite XML, ambos lidos com o tenant do chamador.
     */
    @Transactional(readOnly = true)
    public DocumentoFiscalDetalheResponse detalhe(UUID tenantId, UUID id) {
        DocumentoFiscal documento = documentoFiscalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND,
                        "DOCUMENTO_FISCAL_NAO_ENCONTRADO", MSG_NAO_ENCONTRADO));
        List<DocumentoFiscalLinha> linhas = documentoFiscalLinhaRepository
                .findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc(tenantId, documento.getId());
        ComunicacaoFiscal comunicacao = comunicacaoFiscalRepository
                .findByTenantIdAndDocumentoFiscalId(tenantId, documento.getId())
                .orElse(null);
        EstadoComunicacaoFiscal estado = comunicacao == null ? null : comunicacao.getEstado();
        // Phase 136: o IUD vem do satélite XML do MESMO tenant; só se procura com comunicação.
        ComunicacaoFiscalResumo resumo = comunicacao == null ? null : ComunicacaoFiscalResumo.de(comunicacao,
                documentoFiscalXmlRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documento.getId())
                        .map(DocumentoFiscalXml::getIud)
                        .orElse(null));
        DocumentoFiscalRef origem = null;
        List<DocumentoFiscal> notasCredito = List.of();
        if (documento.getTipo() == TipoDocumentoFiscal.FR) {
            notasCredito = documentoFiscalRepository
                    .findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(tenantId, documento.getId());
        } else if (documento.getDocumentoOrigemId() != null) {
            origem = documentoFiscalRepository.findByIdAndTenantId(documento.getDocumentoOrigemId(), tenantId)
                    .map(DocumentoFiscalRef::de)
                    .orElse(null);
        }
        EntregaEmailResumo entrega = entregaEmailFiscalRepository
                .findByTenantIdAndDocumentoFiscalId(tenantId, documento.getId())
                .map(linha -> EntregaEmailResumo.de(linha, smtpConfigurado, envioAutomatico(tenantId), estado,
                        emailAtualDoCliente(tenantId, documento.getClienteId())))
                .orElse(null);
        return DocumentoFiscalDetalheResponse.de(documento, linhas, estado, origem, notasCredito, resumo, entrega);
    }

    /** Phase 137: a opção de envio automático do escritório (desligada sem configuração). */
    private boolean envioAutomatico(UUID tenantId) {
        return configuracaoFiscalRepository.findByTenantId(tenantId)
                .map(c -> Boolean.TRUE.equals(c.getEnvioEmailAutomatico()))
                .orElse(false);
    }

    /**
     * Phase 137 (T-137-42): email ATUAL da ficha do cliente, só se o cliente for deste tenant e o
     * endereço for válido ({@link RegrasEntregaEmail#emailValido}); senão {@code null}.
     */
    private String emailAtualDoCliente(UUID tenantId, UUID clienteId) {
        if (clienteId == null) {
            return null;
        }
        return clienteRepository.findById(clienteId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .flatMap(c -> RegrasEntregaEmail.emailValido(c.getEmail()))
                .orElse(null);
    }

    /**
     * D-19, EMIS-12: Fatura-Recibo de cada pagamento, numa só consulta. Pagamentos sem documento
     * (registados antes da ativação ou com a faturação desligada) não aparecem no mapa. Sem ids,
     * não consulta nada. Phase 135: só documentos FR -- o estorno de uma NC aparece em
     * {@link #estornosPorPagamento}, nunca aqui.
     */
    @Transactional(readOnly = true)
    public Map<Integer, DocumentoFiscalRef> referenciasPorPagamento(UUID tenantId, Collection<Integer> pagamentoIds) {
        return referenciasDoTipo(tenantId, pagamentoIds, TipoDocumentoFiscal.FR);
    }

    /**
     * Phase 135 (NCRD-03): NC de que cada pagamento é o estorno (chave = id do pagamento de estorno),
     * numa só consulta. Sem ids, não consulta nada.
     */
    @Transactional(readOnly = true)
    public Map<Integer, DocumentoFiscalRef> estornosPorPagamento(UUID tenantId, Collection<Integer> pagamentoIds) {
        return referenciasDoTipo(tenantId, pagamentoIds, TipoDocumentoFiscal.NC);
    }

    /** Phase 135 (NCRD-03): o pagamento é o estorno de uma Nota de Crédito (guarda de eliminação). */
    @Transactional(readOnly = true)
    public boolean eEstornoDeNotaCredito(UUID tenantId, Integer pagamentoId) {
        return documentoFiscalRepository.existsByTenantIdAndPagamentoIdAndTipo(tenantId, pagamentoId,
                TipoDocumentoFiscal.NC);
    }

    private Map<Integer, DocumentoFiscalRef> referenciasDoTipo(UUID tenantId, Collection<Integer> pagamentoIds,
                                                               TipoDocumentoFiscal tipo) {
        if (pagamentoIds == null || pagamentoIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, DocumentoFiscalRef> referencias = new HashMap<>();
        for (DocumentoFiscal d : documentoFiscalRepository.findByTenantIdAndPagamentoIdIn(tenantId, pagamentoIds)) {
            if (d.getTipo() == tipo) {
                referencias.put(d.getPagamentoId(), DocumentoFiscalRef.de(d));
            }
        }
        return referencias;
    }

    /** D-14: guarda de eliminação de um pagamento faturado (FR ou, Phase 135, estorno de uma NC). */
    @Transactional(readOnly = true)
    public boolean existeParaPagamento(UUID tenantId, Integer pagamentoId) {
        return documentoFiscalRepository.existsByTenantIdAndPagamentoId(tenantId, pagamentoId);
    }

    /** D-14: guarda de eliminação de um cliente com documentos. */
    @Transactional(readOnly = true)
    public boolean existeParaCliente(UUID tenantId, UUID clienteId) {
        return documentoFiscalRepository.existsByTenantIdAndClienteId(tenantId, clienteId);
    }

    /** D-14: guarda de eliminação de um processo com documentos. */
    @Transactional(readOnly = true)
    public boolean existeParaProcesso(UUID tenantId, UUID processoId) {
        return documentoFiscalRepository.existsByTenantIdAndProcessoId(tenantId, processoId);
    }

    /** D-14: guarda de eliminação de um honorário com documentos. */
    @Transactional(readOnly = true)
    public boolean existeParaHonorario(UUID tenantId, Integer honorarioId) {
        return documentoFiscalRepository.existsByTenantIdAndHonorarioId(tenantId, honorarioId);
    }

    /**
     * D-15: na fusão, os documentos do cliente absorvido passam a apontar para o que fica (só
     * {@code cliente_id}; a fotografia do adquirente não muda). {@code MANDATORY}: só corre dentro
     * da transação da fusão, depois de as duas linhas de cliente estarem bloqueadas.
     *
     * @return número de documentos re-apontados
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int repontarCliente(UUID tenantId, UUID clienteAntigo, UUID clienteNovo) {
        return documentoFiscalLigacaoClienteRepository.repontarCliente(tenantId, clienteAntigo, clienteNovo);
    }
}
