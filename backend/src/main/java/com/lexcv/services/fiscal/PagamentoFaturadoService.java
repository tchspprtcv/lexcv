package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.DocumentoFiscalRef;
import com.lexcv.dtos.PagamentoComDocumentoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.Cliente;
import com.lexcv.models.CodigoParametroFiscal;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.ContaCorrente;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.Honorario;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.Pagamento;
import com.lexcv.models.Processo;
import com.lexcv.models.RegimeIva;
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
import com.lexcv.services.RecebidoNoMes;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Phase 134 (EMIS-01, EMIS-03..07, EMIS-10, D-06..D-12, D-20, R-01, R-03): emissão ATÓMICA de uma
 * Fatura-Recibo ao registar um pagamento de honorários num escritório com faturação ativa.
 *
 * <p>Numa única transação ({@link #registar}): pagamento + crédito da conta corrente pelo TOTAL
 * (D-09) + próximo número da série (sem lacunas) + documento imutável + linha + linha
 * {@code PENDENTE} de comunicação (D-08, ambiente SIMULADO) + evento de auditoria (R-03). Qualquer
 * {@link RuntimeException} faz rollback de tudo, incluindo o incremento da série.
 *
 * <p><b>Ordem dos locks (R-01):</b> configuração → cliente → processo → conta corrente → série.
 * Todos os caminhos que tocam estas linhas (fusão de clientes, eliminações) tomam o cliente antes
 * do processo e o processo antes da conta corrente, e só a emissão toma a configuração e a série,
 * por isso não há ciclo (sem deadlock). A série é SEMPRE o último lock
 * ({@link NumeracaoService}). {@code definirLockTimeoutLocal} é a primeira instrução: limita a 5s
 * TODOS os locks, não só o da série; uma falha de lock devolve 503 {@code FATURACAO_OCUPADA}.
 *
 * <p><b>Regra OSIV (WR-02 da Phase 133):</b> com open-in-view, um lock sobre uma instância já
 * gerida devolve-a sem a reidratar. Por isso cada lock é a PRIMEIRA leitura da sua linha nesta
 * transação: o cliente do processo é lido como escalar ({@code clienteIdPorIdETenant}); a conta
 * corrente é criada por {@code INSERT ... ON CONFLICT DO NOTHING} e lida só com lock (nunca
 * {@code findByClienteId} neste caminho); o controlador decide a delegação pelo escalar
 * {@link #faturacaoAtiva}.
 *
 * <p><b>Idempotência (D-10):</b> a procura pela chave corre DEPOIS do lock da configuração e
 * ANTES da verificação da ativação (WR-05 da revisão; ver também {@link #resultadoGuardado}). Esse
 * lock serializa todas as emissões do escritório: um pedido repetido espera que o primeiro faça
 * commit e, em READ COMMITTED, cada instrução vê um snapshot novo, por isso a procura encontra o
 * documento já gravado. Mesma chave e mesmo pedido → o mesmo resultado; mesma chave com valores
 * diferentes → 409 {@code CHAVE_REUTILIZADA}. Os UNIQUE da tabela são a rede de segurança: se
 * dispararem, a exceção surge no commit, fora daqui, e o handler global responde 500 com
 * referência (WR-06). Nunca se apanha {@code DataIntegrityViolationException} dentro da transação
 * (o PostgreSQL já a abortou).
 *
 * <p>Pagar acima do valor em falta do honorário continua permitido (R-02), tal como as regras de
 * cálculo, à espera da validação do contabilista (STATE.md Pending Todos) antes da faturação real.
 *
 * <p>O {@code tenantId} e o autor são parâmetros: o serviço nunca lê o contexto de segurança nem o
 * pedido HTTP. "Hoje" é a data civil de Cabo Verde do {@link Clock} injetado, calculada uma vez.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoFaturadoService {

    /** IN-04 da revisão: o mesmo fuso que o KPI mensal usa (uma só definição, em {@link RecebidoNoMes}). */
    static final ZoneId FUSO_CABO_VERDE = RecebidoNoMes.FUSO_CABO_VERDE;

    static final String MOEDA = "CVE";

    static final String MSG_CHAVE_REUTILIZADA =
            "Este pedido já foi usado com valores diferentes. Reveja os dados e registe o pagamento de novo.";
    static final String MSG_PROCESSO_ALTERADO = "O processo ou o cliente foi alterado entretanto. Tente novamente.";
    static final String MSG_DATA_EMISSAO_ALTERADA = "A data mudou durante a emissão. Tente novamente.";
    static final String MSG_FATURACAO_OCUPADA = "A faturação está ocupada. Tente novamente dentro de instantes.";

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
    private final ParametroFiscalService parametroFiscalService;
    private final AuditoriaFiscalService auditoriaFiscalService;
    private final Clock clock;

    /**
     * IN-04 da revisão: mês civil corrente em Cabo Verde segundo o {@link Clock} injetado -- o mesmo
     * relógio que data o estorno de uma Nota de Crédito, por isso o KPI mensal e o estorno nunca
     * discordam do mês e os testes podem fixá-lo.
     */
    public YearMonth mesCorrente() {
        return YearMonth.now(clock.withZone(FUSO_CABO_VERDE));
    }

    /**
     * O escritório tem a faturação ativa? Lê só o escalar {@code ativa}, sem pôr a configuração no
     * contexto de persistência (o lock posterior em {@link #registar} é a primeira leitura da linha).
     */
    @Transactional(readOnly = true)
    public boolean faturacaoAtiva(UUID tenantId) {
        return configuracaoFiscalRepository.ativaPorTenant(tenantId).orElse(false);
    }

    /**
     * Regista o pagamento e emite a sua Fatura-Recibo, tudo ou nada.
     *
     * @throws RecusaFiscalException 422 CHAVE_IDEMPOTENCIA_OBRIGATORIA / HONORARIO_OBRIGATORIO / os
     *                               422 de {@link ComposicaoFaturaRecibo#compor}; 404
     *                               HONORARIO_NAO_ENCONTRADO / CLIENTE_NAO_ENCONTRADO; 409 FATURACAO_DESLIGADA /
     *                               CHAVE_REUTILIZADA / PROCESSO_ALTERADO_TENTE_NOVAMENTE /
     *                               DATA_EMISSAO_ALTERADA; 503 FATURACAO_OCUPADA / SERIE_INDISPONIVEL
     */
    @Transactional
    public ResultadoPagamentoFaturado registar(UUID tenantId, UserPrincipal autor, PagamentoRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(req, "req");

        // 1. Chave de idempotência obrigatória (antes de qualquer acesso à base de dados).
        UUID chave = ValidacaoEmissao.exigirChave(req.chaveIdempotencia());

        // 2. lock_timeout local à transação: limita TODOS os locks abaixo (Pitfall 3).
        serieFiscalRepository.definirLockTimeoutLocal();

        // 3. Lock da configuração (primeiro lock, CR-01). Sem configuração não há documentos.
        ConfiguracaoFiscal cfg = bloquear(() -> configuracaoFiscalRepository.bloquearPorTenant(tenantId))
                .orElseThrow(PagamentoFaturadoService::faturacaoDesligada);

        // 4. Idempotência sob o lock da configuração (D-10), ANTES de olhar para a ativação
        //    (WR-05 da revisão): um pedido repetido de uma FR já emitida devolve o resultado
        //    guardado mesmo que a faturação tenha sido desligada entretanto.
        Optional<ResultadoPagamentoFaturado> repetido = repetirSeJaEmitido(tenantId, chave, req);
        if (repetido.isPresent()) {
            return repetido.get();
        }

        // 4b. A ativação pode ter mudado entre a verificação escalar do controlador e o lock.
        if (!Boolean.TRUE.equals(cfg.getAtiva())) {
            throw faturacaoDesligada();
        }

        // 5. Honorário → cliente do processo (escalar, tenant-scoped; nada fica no contexto).
        if (req.honorarioId() == null) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "HONORARIO_OBRIGATORIO",
                    PreVisualizacaoFaturaService.MSG_HONORARIO_OBRIGATORIO, "honorarioId");
        }
        Honorario honorario = honorarioRepository.findById(req.honorarioId())
                .orElseThrow(PagamentoFaturadoService::honorarioNaoEncontrado);
        UUID clienteId = processoRepository.clienteIdPorIdETenant(honorario.getProcessoId(), tenantId)
                .orElseThrow(PagamentoFaturadoService::honorarioNaoEncontrado);

        // 6. Lock do cliente (primeira leitura da linha). Cliente inexistente (ou apagado enquanto
        //    se esperava pelo lock): 404 CLIENTE_NAO_ENCONTRADO, o mesmo código da pré-visualização
        //    para o mesmo estado (IN-05 da revisão). O 409 PROCESSO_ALTERADO fica para a corrida
        //    que um novo pedido resolve (o processo mudou de cliente, passo 7).
        Cliente cliente = bloquear(() -> clienteRepository.bloquearPorIdETenant(clienteId, tenantId))
                .orElseThrow(PagamentoFaturadoService::clienteNaoEncontrado);

        // 7. Lock do processo; uma fusão pode tê-lo movido para outro cliente entretanto.
        Processo processo = bloquear(() -> processoRepository.bloquearPorIdETenant(honorario.getProcessoId(), tenantId))
                .orElseThrow(PagamentoFaturadoService::processoAlterado);
        if (!cliente.getId().equals(processo.getClienteId())) {
            throw processoAlterado();
        }
        // 7b. O honorário ainda existe neste processo (CR-01 da revisão)? Foi lido no passo 5 sem
        //     lock; deleteHonorario serializa-se pelo lock do processo, por isso, com o lock já
        //     concedido, uma eliminação concorrente já fez commit e este SELECT (escalar, nunca
        //     a cache do contexto) vê-a. Sem esta verificação ficaria uma FR imutável e um
        //     pagamento a apontar para um honorário inexistente (as colunas não têm FK).
        if (!honorarioRepository.processoIdPorId(honorario.getId())
                .map(processo.getId()::equals)
                .orElse(false)) {
            throw honorarioNaoEncontrado();
        }

        // 8. "Hoje" (Cabo Verde) calculado UMA vez; taxa vigente nesse dia; composição partilhada
        //    com a pré-visualização (as recusas 422 acontecem aqui, antes de qualquer escrita).
        LocalDate hoje = LocalDate.now(clock.withZone(FUSO_CABO_VERDE));
        BigDecimal taxaIva = cfg.getRegimeIva() == RegimeIva.NORMAL
                ? parametroFiscalService.valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, hoje)
                : null;
        ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(cfg, cliente, processo, honorario.getId(),
                req, hoje, taxaIva);
        CalculoFiscal.ResultadoCalculo calculo = projeto.calculo();

        // 9. Conta corrente: criar sem corrida, lock como primeira leitura, crédito do TOTAL (D-09).
        //    O INSERT ... ON CONFLICT pode esperar por um INSERT concorrente ainda sem commit: com o
        //    lock_timeout, essa espera também é 503 FATURACAO_OCUPADA, não 500 (IN-04 da revisão).
        bloquear(() -> contaCorrenteRepository.criarSeNaoExiste(clienteId));
        ContaCorrente cc = bloquear(() -> contaCorrenteRepository.bloquearPorCliente(clienteId))
                .orElseThrow(() -> new IllegalStateException("Conta corrente inexistente depois do INSERT ON CONFLICT"));
        BigDecimal saldo = cc.getSaldo() == null ? BigDecimal.ZERO : cc.getSaldo();
        cc.setSaldo(saldo.add(calculo.total()));
        contaCorrenteRepository.save(cc);

        // 10. Pagamento (data = hoje, método = nome do enum).
        Pagamento pagamento = pagamentoRepository.save(Pagamento.builder()
                .honorarioId(honorario.getId())
                .valorPago(calculo.total())
                .dataPagamento(hoje)
                .metodo(projeto.metodo().name())
                .build());

        // 11. Número da série: SEMPRE o último lock. Se a data mudou entretanto, rollback (Pitfall 4).
        NumeroFiscalAtribuido numero =
                numeracaoService.proximoNumero(tenantId, TipoDocumentoFiscal.FR, AmbienteFiscal.SIMULADO);
        if (!hoje.equals(numero.dataEmissao())) {
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "DATA_EMISSAO_ALTERADA", MSG_DATA_EMISSAO_ALTERADA);
        }

        // 12. Documento imutável com a fotografia do emitente e do adquirente (D-07).
        Instant agora = clock.instant();
        MotivoIsencaoIva motivo = projeto.motivoIsencao();
        DocumentoFiscal documento = documentoFiscalRepository.save(DocumentoFiscal.builder()
                .tenantId(tenantId)
                .tipo(TipoDocumentoFiscal.FR)
                .ambiente(AmbienteFiscal.SIMULADO)
                .serieId(numero.serieId())
                .serieCodigo(numero.serieCodigo())
                .ano(numero.ano())
                .numero(numero.numero())
                .numeroFormatado(TextoDocumentoFiscal.numeroFormatado(numero.serieCodigo(), numero.numero()))
                .dataEmissao(numero.dataEmissao())
                .emitidoEm(agora)
                .emitenteNif(projeto.emitenteNif())
                .emitenteFirma(projeto.emitenteFirma())
                .emitenteMorada(projeto.emitenteMorada())
                .emitenteLocalidade(projeto.emitenteLocalidade())
                .emitenteRegimeIva(projeto.regime())
                .emitenteMotivoIsencaoCodigo(motivo == null ? null : motivo.codigo())
                .emitenteMotivoIsencaoDescricao(motivo == null ? null : motivo.descricao())
                .emitenteMotivoIsencaoMencao(motivo == null ? null : motivo.mencao())
                .adquirenteNif(projeto.adquirenteNif())
                .adquirenteNome(projeto.adquirenteNome())
                .adquirenteMorada(projeto.adquirenteMorada())
                .adquirenteLocalidade(projeto.adquirenteLocalidade())
                .clienteId(projeto.clienteId())
                .processoId(projeto.processoId())
                .honorarioId(projeto.honorarioId())
                .pagamentoId(pagamento.getId())
                .metodoPagamento(projeto.metodo().name())
                .meioPagamentoCodigo(projeto.metodo().codigoMeioPagamento())
                .moeda(MOEDA)
                .taxaIva(calculo.taxaIva())
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

        // 13. Linha única (descrição controlada, D-05) e comunicação PENDENTE (D-08).
        documentoFiscalLinhaRepository.save(DocumentoFiscalLinha.builder()
                .tenantId(tenantId)
                .documentoFiscalId(documento.getId())
                .numeroLinha(1)
                .descricao(projeto.descricaoLinha())
                .quantidade(BigDecimal.ONE)
                .precoUnitario(calculo.base())
                .valorBase(calculo.base())
                .taxaIva(calculo.taxaIva())
                .valorIva(calculo.iva())
                .motivoIsencaoCodigo(motivo == null ? null : motivo.codigo())
                .taxaRetencao(calculo.taxaRetencao())
                .valorRetencao(calculo.retencao())
                .totalLinha(calculo.total())
                .build());
        comunicacaoFiscalRepository.save(ComunicacaoFiscal.builder()
                .tenantId(tenantId)
                .documentoFiscalId(documento.getId())
                .ambiente(AmbienteFiscal.SIMULADO)
                .estado(EstadoComunicacaoFiscal.PENDENTE)
                .tentativas(0)
                .createdAt(agora)
                .build());

        // 14. Evento de auditoria na mesma transação (R-03).
        auditoriaFiscalService.registarEmissao(tenantId, autor, documento.getId(), documento.getNumeroFormatado());

        // 15. Resultado novo (o controlador responde 201).
        return ResultadoPagamentoFaturado.novo(
                PagamentoComDocumentoResponse.de(pagamento, DocumentoFiscalRef.de(documento)));
    }

    /**
     * WR-05 da revisão: com a faturação DESLIGADA, um pedido que traz uma chave de idempotência
     * pode ser a repetição de uma Fatura-Recibo emitida antes de a faturação ser desligada (o
     * cliente não recebeu a resposta). O controlador chama isto antes do caminho legado: se a chave
     * já tiver documento, devolve o resultado guardado (ou 409 CHAVE_REUTILIZADA com valores
     * diferentes) em vez de registar um segundo pagamento sem documento. Sem chave, ou sem
     * documento para ela, devolve vazio e o caminho legado segue inalterado.
     *
     * <p>Toma o mesmo lock da configuração que {@link #registar} (sem filtrar pela ativação), por
     * isso espera pelo commit de uma emissão em curso com a mesma chave e encontra-a.
     */
    @Transactional
    public Optional<ResultadoPagamentoFaturado> resultadoGuardado(UUID tenantId, PagamentoRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (req == null || req.chaveIdempotencia() == null) {
            return Optional.empty();
        }
        serieFiscalRepository.definirLockTimeoutLocal();
        if (bloquear(() -> configuracaoFiscalRepository.bloquearPorTenant(tenantId)).isEmpty()) {
            return Optional.empty();
        }
        return repetirSeJaEmitido(tenantId, req.chaveIdempotencia(), req);
    }

    /** Resultado guardado para a chave, 409 CHAVE_REUTILIZADA se o pedido difere, ou vazio. */
    private Optional<ResultadoPagamentoFaturado> repetirSeJaEmitido(UUID tenantId, UUID chave, PagamentoRequest req) {
        Optional<DocumentoFiscal> existente = documentoFiscalRepository.findByTenantIdAndChaveIdempotencia(
                tenantId, chave);
        if (existente.isEmpty()) {
            return Optional.empty();
        }
        DocumentoFiscal doc = existente.get();
        // Phase 135 (T-135-15): o espaço de chaves por tenant é partilhado por FR e NC
        // (UNIQUE(tenant_id, chave_idempotencia)); a chave de uma NC nunca é a repetição de uma FR.
        if (doc.getTipo() != TipoDocumentoFiscal.FR) {
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "CHAVE_REUTILIZADA", MSG_CHAVE_REUTILIZADA);
        }
        if (!mesmoPedido(doc, req)) {
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "CHAVE_REUTILIZADA", MSG_CHAVE_REUTILIZADA);
        }
        Pagamento guardado = pagamentoRepository.findById(doc.getPagamentoId())
                .orElseThrow(() -> new IllegalStateException("Documento fiscal sem pagamento: " + doc.getId()));
        return Optional.of(ResultadoPagamentoFaturado.repetido(
                PagamentoComDocumentoResponse.de(guardado, DocumentoFiscalRef.de(doc))));
    }

    /**
     * O pedido repetido é o mesmo que gerou {@code doc}? Compara honorário, valor (por
     * {@code compareTo}), método (sem espaços, sem distinguir maiúsculas), retenção (null-safe) e,
     * quando enviada, a data. Nunca lança: um valor malformado conta como diferente. Um documento
     * que não seja FR (Phase 135: uma NC com a mesma chave) nunca é o mesmo pedido.
     */
    static boolean mesmoPedido(DocumentoFiscal doc, PagamentoRequest req) {
        if (doc.getTipo() != TipoDocumentoFiscal.FR) {
            return false; // Phase 135: defesa em profundidade, uma NC nunca é o mesmo pedido de uma FR
        }
        if (!Objects.equals(doc.getHonorarioId(), req.honorarioId())) {
            return false;
        }
        if (req.valorPago() == null || doc.getTotalDocumento() == null
                || req.valorPago().compareTo(doc.getTotalDocumento()) != 0) {
            return false;
        }
        if (req.metodo() == null || doc.getMetodoPagamento() == null
                || !req.metodo().trim().equalsIgnoreCase(doc.getMetodoPagamento())) {
            return false;
        }
        if (!mesmaTaxa(req.retencaoPercentagem(), doc.getTaxaRetencao())) {
            return false;
        }
        return req.dataPagamento() == null || req.dataPagamento().equals(doc.getDataEmissao());
    }

    private static boolean mesmaTaxa(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    /** Converte uma falha de lock (timeout ou conflito) em 503 FATURACAO_OCUPADA. */
    private <T> T bloquear(Supplier<T> lock) {
        try {
            return lock.get();
        } catch (PessimisticLockingFailureException | PessimisticLockException | LockTimeoutException e) {
            log.warn("Lock da emissão fiscal não obtido: {}", e.getClass().getSimpleName());
            throw new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE, "FATURACAO_OCUPADA", MSG_FATURACAO_OCUPADA);
        }
    }

    private static RecusaFiscalException faturacaoDesligada() {
        return new RecusaFiscalException(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA",
                PreVisualizacaoFaturaService.MSG_FATURACAO_DESLIGADA);
    }

    private static RecusaFiscalException honorarioNaoEncontrado() {
        return new RecusaFiscalException(HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO",
                PreVisualizacaoFaturaService.MSG_HONORARIO_NAO_ENCONTRADO);
    }

    private static RecusaFiscalException clienteNaoEncontrado() {
        return new RecusaFiscalException(HttpStatus.NOT_FOUND, "CLIENTE_NAO_ENCONTRADO",
                PreVisualizacaoFaturaService.MSG_CLIENTE_NAO_ENCONTRADO);
    }

    private static RecusaFiscalException processoAlterado() {
        return new RecusaFiscalException(HttpStatus.CONFLICT, "PROCESSO_ALTERADO_TENTE_NOVAMENTE",
                MSG_PROCESSO_ALTERADO);
    }
}
