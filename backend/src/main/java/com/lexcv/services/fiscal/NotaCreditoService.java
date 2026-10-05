package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.dtos.NotaCreditoResponse;
import com.lexcv.dtos.PagamentoComDocumentoResponse;
import com.lexcv.dtos.PreVisualizacaoNotaCreditoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.Cliente;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.ContaCorrente;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.Pagamento;
import com.lexcv.models.Processo;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.ContaCorrenteRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.PagamentoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.SerieFiscalRepository;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Phase 135 (NCRD-01..03): pré-visualização e emissão ATÓMICA de uma Nota de Crédito sobre uma
 * Fatura-Recibo emitida.
 *
 * <p><b>Tudo ou nada ({@link #emitir}):</b> numa única transação, o estorno (um {@link Pagamento}
 * NEGATIVO no mesmo honorário), o débito da conta corrente pelo total da NC, o próximo número da
 * série própria da NC (tipo NC, ambiente da FR de origem), o documento imutável, a linha, a comunicação
 * {@code PENDENTE} e o evento de auditoria fazem commit juntos ou nenhum. Qualquer
 * {@link RuntimeException} faz rollback de tudo, incluindo o incremento da série.
 *
 * <p><b>Ordem dos locks (CONTEXT "Ordem de locks igual à 134"):</b> configuração → cliente →
 * processo → conta corrente → série, a mesma de {@link PagamentoFaturadoService}, por isso não há
 * ciclo com a emissão da FR, a fusão de clientes ou as eliminações. {@code definirLockTimeoutLocal}
 * é a primeira instrução; uma falha de lock devolve 503 {@code FATURACAO_OCUPADA}.
 *
 * <p><b>Teto cumulativo sem corridas:</b> a soma das NC já emitidas sobre a FR é lida DEPOIS do
 * lock da configuração do tenant (CONTEXT "verificado sob o lock de configuração"). Esse lock
 * serializa todas as emissões do tenant (FR e NC); em READ COMMITTED cada instrução vê um snapshot
 * novo, por isso a soma inclui qualquer NC que tenha feito commit antes. Não é preciso bloquear a
 * linha da FR: é imutável e todo o escritor de NC toma primeiro o lock da configuração.
 *
 * <p><b>Regra OSIV:</b> cada lock é a PRIMEIRA leitura da sua linha nesta transação. O cliente
 * debitado é o da FR ({@code origem.getClienteId()}): a fusão re-aponta os documentos fiscais, e o
 * cliente ATUAL do processo pode não ser o que a FR creditou (CR-01 da revisão). O honorário é lido
 * como escalar ({@code processoIdPorId}) e a conta corrente só com lock depois do
 * {@code INSERT ... ON CONFLICT}.
 *
 * <p><b>Modelo do estorno:</b> a NC guarda em {@code pagamento_id} o id do SEU estorno (negativo);
 * {@code documento_origem_id} aponta a FR. As quatro leituras de "pago" ({@code Honorario.totalPago},
 * saldo da conta corrente, KPI mensal e alerta {@code HONORARIO_ATRASADO}) leem o mesmo ledger
 * {@code t_pagamento}, por isso ficam coerentes sem lógica duplicada. O estorno só é criado aqui.
 *
 * <p><b>Idempotência:</b> a chave é obrigatória e é procurada sob o lock da configuração, ANTES da
 * verificação da ativação (padrão WR-05 da 134): mesma chave e mesmo pedido devolvem a NC guardada;
 * mesma chave com outro pedido, ou a chave de uma FR, dão 409 {@code CHAVE_REUTILIZADA}. O
 * {@code UNIQUE(tenant_id, chave_idempotencia)} é a rede de segurança; nunca se apanha
 * {@code DataIntegrityViolationException} dentro da transação.
 *
 * <p>Os montantes vêm de {@link ComposicaoNotaCredito#compor}, a mesma função da pré-visualização,
 * com as taxas FOTOGRAFADAS na FR (nunca a taxa vigente hoje). O {@code tenantId} e o autor são
 * parâmetros: o serviço nunca lê o contexto de segurança. "Hoje" é a data civil de Cabo Verde do
 * {@link Clock} injetado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotaCreditoService {

    static final String MSG_CHAVE_REUTILIZADA =
            "Este pedido já foi usado com valores diferentes. Reveja os dados e emita a nota de crédito de novo.";

    static final String MSG_VALORES_ALTERADOS =
            "Os valores desta fatura-recibo mudaram desde a pré-visualização. Calcule a nota de crédito de novo.";

    private final ConfiguracaoFiscalRepository configuracaoFiscalRepository;
    private final SerieFiscalRepository serieFiscalRepository;
    private final HonorarioRepository honorarioRepository;
    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;
    private final ContaCorrenteRepository contaCorrenteRepository;
    private final PagamentoRepository pagamentoRepository;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;
    private final ComunicacaoFiscalRepository comunicacaoFiscalRepository;
    private final NumeracaoService numeracaoService;
    private final AuditoriaFiscalService auditoriaFiscalService;
    private final Clock clock;

    /**
     * Pré-visualização: os mesmos valores e as mesmas recusas que a emissão, sem locks, sem
     * escritas e sem número. Não exige chave de idempotência.
     *
     * @throws RecusaFiscalException 409 FATURACAO_DESLIGADA / NC_EXCEDE_ORIGINAL; 404
     *                               DOCUMENTO_FISCAL_NAO_ENCONTRADO; 422 NC_SOBRE_NC e as recusas
     *                               do pedido
     */
    @Transactional(readOnly = true)
    public PreVisualizacaoNotaCreditoResponse preVisualizar(UUID tenantId, UUID documentoOrigemId,
                                                            NotaCreditoRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(req, "req");
        LocalDate hoje = LocalDate.now(clock.withZone(PagamentoFaturadoService.FUSO_CABO_VERDE));

        configuracaoFiscalRepository.findByTenantId(tenantId)
                .filter(c -> Boolean.TRUE.equals(c.getAtiva()))
                .orElseThrow(NotaCreditoService::faturacaoDesligada);
        DocumentoFiscal origem = documentoFiscalRepository.findByIdAndTenantId(documentoOrigemId, tenantId)
                .orElseThrow(NotaCreditoService::documentoNaoEncontrado);
        List<DocumentoFiscal> notas = notasDe(tenantId, origem.getId());
        ProjetoNotaCredito projeto = ComposicaoNotaCredito.compor(origem, notas, req, hoje);
        return PreVisualizacaoNotaCreditoResponse.de(origem, projeto);
    }

    /**
     * Emite a Nota de Crédito, tudo ou nada.
     *
     * @throws RecusaFiscalException 422 CHAVE_IDEMPOTENCIA_OBRIGATORIA / NC_SOBRE_NC / recusas do
     *                               pedido; 404 DOCUMENTO_FISCAL_NAO_ENCONTRADO /
     *                               HONORARIO_NAO_ENCONTRADO; 409 FATURACAO_DESLIGADA /
     *                               CHAVE_REUTILIZADA / NC_EXCEDE_ORIGINAL / NC_VALORES_ALTERADOS /
     *                               PROCESSO_ALTERADO_TENTE_NOVAMENTE / DATA_EMISSAO_ALTERADA; 503
     *                               FATURACAO_OCUPADA / SERIE_INDISPONIVEL
     */
    @Transactional
    public ResultadoNotaCredito emitir(UUID tenantId, UserPrincipal autor, UUID documentoOrigemId,
                                       NotaCreditoRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(req, "req");

        // 1. Chave de idempotência obrigatória (antes de qualquer acesso à base de dados).
        UUID chave = ValidacaoEmissao.exigirChave(req.chaveIdempotencia());

        // 2. lock_timeout local à transação: limita TODOS os locks abaixo.
        serieFiscalRepository.definirLockTimeoutLocal();

        // 3. Lock da configuração (primeiro lock): serializa todas as emissões do tenant.
        ConfiguracaoFiscal cfg = bloquear(() -> configuracaoFiscalRepository.bloquearPorTenant(tenantId))
                .orElseThrow(NotaCreditoService::faturacaoDesligada);

        // 4. Idempotência sob o lock, ANTES da ativação (WR-05): uma NC já emitida devolve-se mesmo
        //    que a faturação tenha sido desligada entretanto.
        Optional<ResultadoNotaCredito> repetido = repetirSeJaEmitido(tenantId, chave, documentoOrigemId, req);
        if (repetido.isPresent()) {
            return repetido.get();
        }

        // 4b. A NC é um documento fiscal: exige a faturação ativa.
        if (!Boolean.TRUE.equals(cfg.getAtiva())) {
            throw faturacaoDesligada();
        }

        // 5. FR de origem no tenant do chamador (outro tenant = inexistente, 404); NC sobre NC
        //    recusada já aqui, antes de qualquer lock de linha.
        DocumentoFiscal origem = documentoFiscalRepository.findByIdAndTenantId(documentoOrigemId, tenantId)
                .orElseThrow(NotaCreditoService::documentoNaoEncontrado);
        ValidacaoNotaCredito.exigirFaturaRecibo(origem);

        // 6. Cliente da FR (CR-01 da revisão): é a conta corrente creditada pela FR que tem de ser
        //    debitada, e o cliente que a NC regista. Uma fusão re-aponta t_documento_fiscal.cliente_id
        //    (repontarCliente) com os dois clientes bloqueados, por isso este valor segue as fusões; a
        //    mudança de cliente de um processo com documentos fiscais é recusada (PUT /processos/{id}),
        //    e um processo reatribuído antes dessa guarda não desvia o débito para o cliente novo.
        UUID clienteId = origem.getClienteId();

        // 7. Lock do cliente (primeira leitura da linha). Vazio = uma fusão absorveu e apagou o
        //    cliente entre a leitura da FR e o lock (WR-02): 409 que se pode repetir, nunca 404.
        Cliente cliente = bloquear(() -> clienteRepository.bloquearPorIdETenant(clienteId, tenantId))
                .orElseThrow(NotaCreditoService::processoAlterado);

        // 8. Lock do processo (mesma ordem de locks da FR). Não se exige que o processo ainda
        //    pertença ao cliente da FR: a NC credita sempre quem a FR creditou.
        Processo processo = bloquear(() -> processoRepository.bloquearPorIdETenant(origem.getProcessoId(), tenantId))
                .orElseThrow(NotaCreditoService::processoAlterado);
        // 8b. O honorário da FR ainda pertence a este processo (escalar, nunca a cache; CR-01).
        if (!honorarioRepository.processoIdPorId(origem.getHonorarioId())
                .map(processo.getId()::equals)
                .orElse(false)) {
            throw honorarioNaoEncontrado();
        }

        // 9. "Hoje" (Cabo Verde) calculado UMA vez; NC anteriores lidas sob o lock da configuração;
        //    composição partilhada com a pré-visualização (todas as recusas 422/409 antes de escrever).
        LocalDate hoje = LocalDate.now(clock.withZone(PagamentoFaturadoService.FUSO_CABO_VERDE));
        List<DocumentoFiscal> notas = notasDe(tenantId, origem.getId());
        ProjetoNotaCredito projeto = ComposicaoNotaCredito.compor(origem, notas, req, hoje);
        CalculoFiscal.ResultadoCalculo calculo = projeto.calculo();
        // 9b. WR-01 da revisão: o documento imutável tem de ser o que o utilizador confirmou. Se outra
        //     NC foi emitida entre a pré-visualização e a confirmação, os valores mudaram: 409 e nova
        //     pré-visualização (nada foi escrito).
        exigirValoresConfirmados(req, projeto);

        // 10. Conta corrente: criar sem corrida, lock como primeira leitura, débito do total da NC.
        bloquear(() -> contaCorrenteRepository.criarSeNaoExiste(clienteId));
        ContaCorrente cc = bloquear(() -> contaCorrenteRepository.bloquearPorCliente(clienteId))
                .orElseThrow(() -> new IllegalStateException("Conta corrente inexistente depois do INSERT ON CONFLICT"));
        BigDecimal saldo = cc.getSaldo() == null ? BigDecimal.ZERO : cc.getSaldo();
        cc.setSaldo(saldo.subtract(calculo.total()));
        contaCorrenteRepository.save(cc);

        // 11. Estorno: Pagamento NEGATIVO no honorário da FR, datado de hoje, com o método da FR.
        Pagamento estorno = pagamentoRepository.save(Pagamento.builder()
                .honorarioId(origem.getHonorarioId())
                .valorPago(calculo.total().negate())
                .dataPagamento(hoje)
                .metodo(origem.getMetodoPagamento())
                .build());

        // 12. Número da série da NC: SEMPRE o último lock. Se a data mudou entretanto, rollback.
        //     IN-02 da revisão: o ambiente é o da FR de origem (uma NC nunca muda de ambiente).
        AmbienteFiscal ambiente = origem.getAmbiente();
        NumeroFiscalAtribuido numero =
                numeracaoService.proximoNumero(tenantId, TipoDocumentoFiscal.NC, ambiente);
        if (!hoje.equals(numero.dataEmissao())) {
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "DATA_EMISSAO_ALTERADA",
                    PagamentoFaturadoService.MSG_DATA_EMISSAO_ALTERADA);
        }

        // 13. Documento imutável: emitente, adquirente, regime e isenção COPIADOS da FR de origem.
        Instant agora = clock.instant();
        DocumentoFiscal documento = documentoFiscalRepository.save(DocumentoFiscal.builder()
                .tenantId(tenantId)
                .tipo(TipoDocumentoFiscal.NC)
                .ambiente(ambiente)
                .serieId(numero.serieId())
                .serieCodigo(numero.serieCodigo())
                .ano(numero.ano())
                .numero(numero.numero())
                .numeroFormatado(TextoDocumentoFiscal.numeroFormatado(numero.serieCodigo(), numero.numero()))
                .dataEmissao(numero.dataEmissao())
                .emitidoEm(agora)
                .emitenteNif(origem.getEmitenteNif())
                .emitenteFirma(origem.getEmitenteFirma())
                .emitenteMorada(origem.getEmitenteMorada())
                .emitenteLocalidade(origem.getEmitenteLocalidade())
                .emitenteRegimeIva(origem.getEmitenteRegimeIva())
                .emitenteMotivoIsencaoCodigo(origem.getEmitenteMotivoIsencaoCodigo())
                .emitenteMotivoIsencaoDescricao(origem.getEmitenteMotivoIsencaoDescricao())
                .emitenteMotivoIsencaoMencao(origem.getEmitenteMotivoIsencaoMencao())
                .adquirenteNif(origem.getAdquirenteNif())
                .adquirenteNome(origem.getAdquirenteNome())
                .adquirenteMorada(origem.getAdquirenteMorada())
                .adquirenteLocalidade(origem.getAdquirenteLocalidade())
                .clienteId(cliente.getId())
                .processoId(processo.getId())
                .honorarioId(origem.getHonorarioId())
                .pagamentoId(estorno.getId())
                .documentoOrigemId(origem.getId())
                .motivoCodigo(projeto.motivo())
                .motivoTexto(projeto.motivoTexto())
                .metodoPagamento(origem.getMetodoPagamento())
                .meioPagamentoCodigo(origem.getMeioPagamentoCodigo())
                .moeda(PagamentoFaturadoService.MOEDA)
                .taxaIva(origem.getTaxaIva())
                .totalBase(calculo.base())
                .totalIva(calculo.iva())
                .totalRetencao(calculo.retencao())
                .totalDocumento(calculo.total())
                .valorLiquido(calculo.liquidoRecebido())
                .taxaRetencao(calculo.taxaRetencao())
                .chaveIdempotencia(chave)
                .emitidoPorId(autor == null ? null : autor.getUserId())
                .emitidoPorNome(autor == null ? null : autor.getNome())
                .build());

        // 14. Linha única e comunicação PENDENTE (como a FR; a comunicação real é da Phase 136).
        documentoFiscalLinhaRepository.save(DocumentoFiscalLinha.builder()
                .tenantId(tenantId)
                .documentoFiscalId(documento.getId())
                .numeroLinha(1)
                .descricao(projeto.descricaoLinha())
                .quantidade(BigDecimal.ONE)
                .precoUnitario(calculo.base())
                .valorBase(calculo.base())
                .taxaIva(origem.getTaxaIva())
                .valorIva(calculo.iva())
                .motivoIsencaoCodigo(origem.getEmitenteMotivoIsencaoCodigo())
                .taxaRetencao(calculo.taxaRetencao())
                .valorRetencao(calculo.retencao())
                .totalLinha(calculo.total())
                .build());
        comunicacaoFiscalRepository.save(ComunicacaoFiscal.builder()
                .tenantId(tenantId)
                .documentoFiscalId(documento.getId())
                .ambiente(ambiente)
                .estado(EstadoComunicacaoFiscal.PENDENTE)
                .tentativas(0)
                .createdAt(agora)
                .build());

        // 15. Evento de auditoria na mesma transação (nunca o texto livre do motivo).
        auditoriaFiscalService.registarEmissaoNotaCredito(tenantId, autor, documento.getId(),
                documento.getNumeroFormatado(), origem.getNumeroFormatado());

        // 16. Resultado novo (o controlador responde 201).
        return ResultadoNotaCredito.novo(resposta(documento, origem, estorno, projeto.valorCreditavelDepois()));
    }

    /**
     * NC guardada para a chave, 409 CHAVE_REUTILIZADA se o pedido difere (ou a chave é de uma FR),
     * ou vazio se a chave ainda não foi usada.
     */
    private Optional<ResultadoNotaCredito> repetirSeJaEmitido(UUID tenantId, UUID chave, UUID documentoOrigemId,
                                                              NotaCreditoRequest req) {
        Optional<DocumentoFiscal> existente = documentoFiscalRepository.findByTenantIdAndChaveIdempotencia(
                tenantId, chave);
        if (existente.isEmpty()) {
            return Optional.empty();
        }
        DocumentoFiscal nc = existente.get();
        // O espaço de chaves é partilhado por FR e NC: a chave de uma FR nunca repete uma NC.
        if (nc.getTipo() != TipoDocumentoFiscal.NC
                || !Objects.equals(nc.getDocumentoOrigemId(), documentoOrigemId)
                || !mesmoMotivoETexto(nc, req)) {
            throw chaveReutilizada();
        }
        DocumentoFiscal origem = documentoFiscalRepository.findByIdAndTenantId(documentoOrigemId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Nota de crédito sem documento de origem: " + nc.getId()));
        List<DocumentoFiscal> notas = notasDe(tenantId, origem.getId());
        BigDecimal restante = restante(origem, notas);
        if (!mesmoTipoEValor(nc, req, notas, restante)) {
            throw chaveReutilizada();
        }
        Pagamento estorno = pagamentoRepository.findById(nc.getPagamentoId())
                .orElseThrow(() -> new IllegalStateException("Nota de crédito sem estorno: " + nc.getId()));
        return Optional.of(ResultadoNotaCredito.repetido(resposta(nc, origem, estorno, restante)));
    }

    /**
     * WR-01 da revisão: compara o total e o valor creditável confirmados na pré-visualização com a
     * composição feita sob o lock da configuração. Valores ausentes não são verificados.
     */
    static void exigirValoresConfirmados(NotaCreditoRequest req, ProjetoNotaCredito projeto) {
        boolean totalDiferente = req.totalEsperado() != null
                && req.totalEsperado().compareTo(projeto.calculo().total()) != 0;
        boolean creditavelDiferente = req.valorCreditavelEsperado() != null
                && req.valorCreditavelEsperado().compareTo(projeto.valorCreditavelAntes()) != 0;
        if (totalDiferente || creditavelDiferente) {
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "NC_VALORES_ALTERADOS", MSG_VALORES_ALTERADOS);
        }
    }

    /** Mesmo motivo (pelo nome) e mesmo texto (sem espaços à volta). Nunca lança. */
    static boolean mesmoMotivoETexto(DocumentoFiscal nc, NotaCreditoRequest req) {
        Optional<MotivoNotaCredito> motivo = MotivoNotaCredito.porNome(req.motivoCodigo());
        if (motivo.isEmpty() || motivo.get() != nc.getMotivoCodigo()) {
            return false;
        }
        String texto = req.motivoTexto() == null ? null : req.motivoTexto().trim();
        return texto != null && texto.equals(nc.getMotivoTexto());
    }

    /**
     * Regra do tipo na repetição. PARCIAL: o valor pedido é o total guardado. TOTAL: sem valor, a
     * FR já não tem nada por creditar E a NC guardada é a mais recente da FR -- foi ela que a
     * fechou. Sem coluna de tipo de crédito, uma PARCIAL igual ao remanescente é indistinguível de
     * um TOTAL; os dois documentos são idênticos (mesmo caminho de composição). Uma PARCIAL
     * anterior, esgotada depois por outra NC, nunca repete um TOTAL. Nunca lança.
     */
    static boolean mesmoTipoEValor(DocumentoFiscal nc, NotaCreditoRequest req, List<DocumentoFiscal> notas,
                                   BigDecimal restante) {
        Optional<TipoCredito> tipo = tipoDe(req.tipo());
        if (tipo.isEmpty()) {
            return false;
        }
        if (tipo.get() == TipoCredito.PARCIAL) {
            return req.valor() != null && nc.getTotalDocumento() != null
                    && req.valor().compareTo(nc.getTotalDocumento()) == 0;
        }
        return req.valor() == null
                && restante.signum() == 0
                && !notas.isEmpty()
                && Objects.equals(notas.get(0).getId(), nc.getId());
    }

    private static Optional<TipoCredito> tipoDe(String tipo) {
        if (tipo == null) {
            return Optional.empty();
        }
        String limpo = tipo.trim();
        return Arrays.stream(TipoCredito.values()).filter(t -> t.name().equalsIgnoreCase(limpo)).findFirst();
    }

    private List<DocumentoFiscal> notasDe(UUID tenantId, UUID origemId) {
        return documentoFiscalRepository.findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(
                tenantId, origemId);
    }

    /** Total da FR menos o total das NC emitidas sobre ela (positivo, ou zero quando esgotada). */
    private static BigDecimal restante(DocumentoFiscal origem, List<DocumentoFiscal> notas) {
        BigDecimal creditado = BigDecimal.ZERO;
        for (DocumentoFiscal n : notas) {
            if (n.getTipo() == TipoDocumentoFiscal.NC && n.getTotalDocumento() != null) {
                creditado = creditado.add(n.getTotalDocumento());
            }
        }
        return origem.getTotalDocumento().subtract(creditado);
    }

    private static NotaCreditoResponse resposta(DocumentoFiscal nc, DocumentoFiscal origem, Pagamento estorno,
                                                BigDecimal restante) {
        return new NotaCreditoResponse(
                nc.getId(),
                nc.getNumeroFormatado(),
                TipoDocumentoFiscal.NC.name(),
                origem.getId(),
                origem.getNumeroFormatado(),
                nc.getDataEmissao(),
                nc.getTotalDocumento(),
                PagamentoComDocumentoResponse.de(estorno, null, DocumentoFiscalRef.de(nc)),
                restante);
    }

    /** Converte uma falha de lock (timeout ou conflito) em 503 FATURACAO_OCUPADA. */
    private <T> T bloquear(Supplier<T> lock) {
        try {
            return lock.get();
        } catch (PessimisticLockingFailureException | PessimisticLockException | LockTimeoutException e) {
            log.warn("Lock da emissão da nota de crédito não obtido: {}", e.getClass().getSimpleName());
            throw new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA",
                    PagamentoFaturadoService.MSG_FATURACAO_OCUPADA);
        }
    }

    private static RecusaFiscalException chaveReutilizada() {
        return new RecusaFiscalException(HttpStatus.CONFLICT, "CHAVE_REUTILIZADA", MSG_CHAVE_REUTILIZADA);
    }

    private static RecusaFiscalException faturacaoDesligada() {
        return new RecusaFiscalException(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA",
                PreVisualizacaoFaturaService.MSG_FATURACAO_DESLIGADA);
    }

    private static RecusaFiscalException documentoNaoEncontrado() {
        return new RecusaFiscalException(HttpStatus.NOT_FOUND, "DOCUMENTO_FISCAL_NAO_ENCONTRADO",
                DocumentoFiscalService.MSG_NAO_ENCONTRADO);
    }

    private static RecusaFiscalException honorarioNaoEncontrado() {
        return new RecusaFiscalException(HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO",
                PreVisualizacaoFaturaService.MSG_HONORARIO_NAO_ENCONTRADO);
    }

    private static RecusaFiscalException processoAlterado() {
        return new RecusaFiscalException(HttpStatus.CONFLICT, "PROCESSO_ALTERADO_TENTE_NOVAMENTE",
                PagamentoFaturadoService.MSG_PROCESSO_ALTERADO);
    }
}
