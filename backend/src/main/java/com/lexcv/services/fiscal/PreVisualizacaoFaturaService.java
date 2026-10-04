package com.lexcv.services.fiscal;

import com.lexcv.dtos.EstadoEmissaoResponse;
import com.lexcv.dtos.PagamentoRequest;
import com.lexcv.dtos.PreVisualizacaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.Cliente;
import com.lexcv.models.CodigoParametroFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.Honorario;
import com.lexcv.models.Processo;
import com.lexcv.models.RegimeIva;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.ProcessoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Phase 134 (EMIS-02, D-01, D-03, R-05): pré-visualização da Fatura-Recibo, SEM efeitos.
 *
 * <p>Calcula, com a mesma {@link ComposicaoFaturaRecibo#compor} da emissão, o adquirente, a base,
 * o IVA (ou o motivo de isenção), a retenção, o total e o líquido do pagamento que o utilizador
 * vai registar, e recusa com os mesmos códigos. Nunca escreve: nem pagamento, nem documento, nem
 * número de série, nem conta corrente; também não toma locks.
 *
 * <p>O {@code tenantId} é sempre um parâmetro (vem do principal no controlador); este serviço
 * nunca lê o contexto de segurança. "Hoje" é a data civil de Cabo Verde derivada do
 * {@link Clock} injetado, e a mesma data serve para validar o pedido e para ler a taxa de IVA
 * (sem divergências à meia-noite).
 */
@Service
public class PreVisualizacaoFaturaService {

    private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");

    static final String MSG_FATURACAO_DESLIGADA = "A faturação não está ativa neste escritório.";
    static final String MSG_HONORARIO_OBRIGATORIO = "Escolha o honorário a que o pagamento se refere.";
    static final String MSG_HONORARIO_NAO_ENCONTRADO = "Honorário não encontrado.";
    static final String MSG_CLIENTE_NAO_ENCONTRADO = "Cliente não encontrado.";

    private final ConfiguracaoFiscalRepository configuracaoFiscalRepository;
    private final HonorarioRepository honorarioRepository;
    private final ProcessoRepository processoRepository;
    private final ClienteRepository clienteRepository;
    private final ParametroFiscalService parametroFiscalService;
    private final Clock clock;

    public PreVisualizacaoFaturaService(ConfiguracaoFiscalRepository configuracaoFiscalRepository,
                                        HonorarioRepository honorarioRepository,
                                        ProcessoRepository processoRepository,
                                        ClienteRepository clienteRepository,
                                        ParametroFiscalService parametroFiscalService,
                                        Clock clock) {
        this.configuracaoFiscalRepository = configuracaoFiscalRepository;
        this.honorarioRepository = honorarioRepository;
        this.processoRepository = processoRepository;
        this.clienteRepository = clienteRepository;
        this.parametroFiscalService = parametroFiscalService;
        this.clock = clock;
    }

    /**
     * @throws RecusaFiscalException 409 FATURACAO_DESLIGADA; 422 HONORARIO_OBRIGATORIO; 404
     *                               HONORARIO_NAO_ENCONTRADO (inexistente ou de outro escritório,
     *                               mesma mensagem); 404 CLIENTE_NAO_ENCONTRADO; e os 422 de
     *                               {@link ComposicaoFaturaRecibo#compor}
     */
    @Transactional(readOnly = true)
    public PreVisualizacaoFaturaResponse preVisualizar(UUID tenantId, PagamentoRequest req) {
        ConfiguracaoFiscal cfg = configuracaoFiscalRepository.findByTenantId(tenantId)
                .filter(c -> Boolean.TRUE.equals(c.getAtiva()))
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA",
                        MSG_FATURACAO_DESLIGADA));

        if (req.honorarioId() == null) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "HONORARIO_OBRIGATORIO",
                    MSG_HONORARIO_OBRIGATORIO, "honorarioId");
        }
        Honorario honorario = honorarioRepository.findById(req.honorarioId())
                .orElseThrow(PreVisualizacaoFaturaService::honorarioNaoEncontrado);
        Processo processo = processoRepository.findById(honorario.getProcessoId())
                .filter(p -> tenantId.equals(p.getTenantId()))
                .orElseThrow(PreVisualizacaoFaturaService::honorarioNaoEncontrado);
        Cliente cliente = clienteRepository.findById(processo.getClienteId())
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, "CLIENTE_NAO_ENCONTRADO",
                        MSG_CLIENTE_NAO_ENCONTRADO));

        LocalDate hoje = LocalDate.now(clock.withZone(FUSO_CABO_VERDE));
        // Regime nulo: deixa a taxa nula e compor recusa a configuração como incompleta.
        BigDecimal taxaIva = cfg.getRegimeIva() == RegimeIva.NORMAL
                ? parametroFiscalService.valorVigente(CodigoParametroFiscal.IVA_TAXA_NORMAL, hoje)
                : null;

        ProjetoFaturaRecibo projeto = ComposicaoFaturaRecibo.compor(cfg, cliente, processo, honorario.getId(),
                req, hoje, taxaIva);
        return PreVisualizacaoFaturaResponse.de(projeto);
    }

    /**
     * Estado da faturação para o formulário de pagamento. Lê só o escalar {@code ativa} (sem pôr
     * a configuração no contexto de persistência); com a faturação ativa acrescenta o ambiente e
     * a taxa de retenção sugerida, lida do parâmetro {@code RETENCAO_SUGERIDA} vigente hoje.
     */
    @Transactional(readOnly = true)
    public EstadoEmissaoResponse estadoEmissao(UUID tenantId) {
        boolean ativa = configuracaoFiscalRepository.ativaPorTenant(tenantId).orElse(false);
        if (!ativa) {
            return EstadoEmissaoResponse.desligada();
        }
        LocalDate hoje = LocalDate.now(clock.withZone(FUSO_CABO_VERDE));
        BigDecimal sugerida = parametroFiscalService.valorVigente(CodigoParametroFiscal.RETENCAO_SUGERIDA, hoje);
        return new EstadoEmissaoResponse(true, AmbienteFiscal.SIMULADO.name(), sugerida);
    }

    private static RecusaFiscalException honorarioNaoEncontrado() {
        return new RecusaFiscalException(HttpStatus.NOT_FOUND, "HONORARIO_NAO_ENCONTRADO",
                MSG_HONORARIO_NAO_ENCONTRADO);
    }
}
