package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.RegistarPagamentoSubscricaoRequest;
import com.lexcv.dtos.SubscricaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.*;
import com.lexcv.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 138 (SUBS-02, SUBS-05): Serviço de emissão atómica de Fatura-Recibo de subscrição
 * emitida pela plataforma LexCV para um escritório cliente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscricaoFaturadaService {

    private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");

    private final PlatformFaturacaoConfigService platformConfigService;
    private final TenantRepository tenantRepository;
    private final ConfiguracaoFiscalRepository configuracaoFiscalRepository;
    private final PagamentoSubscricaoRepository pagamentoSubscricaoRepository;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;
    private final ComunicacaoFiscalRepository comunicacaoFiscalRepository;
    private final EntregaEmailFiscalRepository entregaEmailFiscalRepository;
    private final NumeracaoService numeracaoService;
    private final ParametroFiscalService parametroFiscalService;
    private final AuditoriaFiscalService auditoriaFiscalService;
    private final Clock clock;

    @Transactional
    public SubscricaoFaturaResponse registarPagamentoFaturado(UserPrincipal autor, RegistarPagamentoSubscricaoRequest req) {
        Objects.requireNonNull(req, "req");

        Tenant lexcvTenant = platformConfigService.obterTenantPlataforma();
        UUID lexcvTenantId = lexcvTenant.getId();

        // 1. Valida se a plataforma está configurada e pronta para emitir (SUBS-01)
        platformConfigService.validarProntaParaEmitir();
        ConfiguracaoFiscal lexcvConfig = configuracaoFiscalRepository.findByTenantId(lexcvTenantId)
                .orElseThrow(() -> new IllegalStateException("Configuração da plataforma não encontrada"));

        // 2. Valida o tenant adquirente (não pode ser a própria LexCV)
        if (lexcvTenantId.equals(req.tenantId())) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "TENANT_INVALIDO",
                    "Não é possível emitir uma fatura de subscrição para a própria plataforma.");
        }

        Tenant adquirenteTenant = tenantRepository.findById(req.tenantId())
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, "TENANT_NAO_ENCONTRADO",
                        "Escritório não encontrado com o ID fornecido."));

        // 3. Idempotência
        var docExistente = documentoFiscalRepository.findByTenantIdAndChaveIdempotencia(lexcvTenantId, req.chaveIdempotencia());
        if (docExistente.isPresent()) {
            DocumentoFiscal doc = docExistente.get();
            if (!Objects.equals(doc.getAdquirenteTenantId(), req.tenantId())
                    || doc.getTotalDocumento().compareTo(req.valorPago()) != 0) {
                throw new RecusaFiscalException(HttpStatus.CONFLICT, "CHAVE_REUTILIZADA",
                        "A chave de idempotência já foi utilizada com parâmetros diferentes.");
            }
            PagamentoSubscricao pag = doc.getPagamentoSubscricaoId() != null
                    ? pagamentoSubscricaoRepository.findById(doc.getPagamentoSubscricaoId()).orElse(null)
                    : null;
            ComunicacaoFiscal com = comunicacaoFiscalRepository.findByTenantIdAndDocumentoFiscalId(lexcvTenantId, doc.getId()).orElse(null);
            return toResponse(doc, pag, com != null ? com.getEstado() : EstadoComunicacaoFiscal.PENDENTE);
        }

        // 4. Numeração sequencial sob lock na série da plataforma (SUBS-05)
        AmbienteFiscal ambiente = AmbienteFiscal.SIMULADO;
        NumeroFiscalAtribuido numeroAtribuido = numeracaoService.proximoNumero(lexcvTenantId, TipoDocumentoFiscal.FR, ambiente);

        // 5. Cálculo fiscal
        BigDecimal taxaIva = lexcvConfig.getRegimeIva() == RegimeIva.ISENTO
                ? BigDecimal.ZERO
                : parametroFiscalService.valorVigenteHoje(CodigoParametroFiscal.IVA_TAXA_NORMAL);
        CalculoFiscal.ResultadoCalculo calculo = CalculoFiscal.calcular(req.valorPago(), lexcvConfig.getRegimeIva(), taxaIva, null);

        Instant agora = clock.instant();

        // 6. Dados cadastrais / fiscais do adquirente (fotografia snapshot)
        ConfiguracaoFiscal adquirenteConfig = configuracaoFiscalRepository.findByTenantId(adquirenteTenant.getId()).orElse(null);
        String adqNif = (adquirenteConfig != null && adquirenteConfig.getNif() != null && !adquirenteConfig.getNif().isBlank())
                ? adquirenteConfig.getNif()
                : (adquirenteTenant.getNif() != null && !adquirenteTenant.getNif().isBlank() ? adquirenteTenant.getNif() : "999999999");
        String adqNome = (adquirenteConfig != null && adquirenteConfig.getFirma() != null && !adquirenteConfig.getFirma().isBlank())
                ? adquirenteConfig.getFirma()
                : adquirenteTenant.getNome();
        String adqMorada = (adquirenteConfig != null && adquirenteConfig.getMorada() != null && !adquirenteConfig.getMorada().isBlank())
                ? adquirenteConfig.getMorada()
                : "Praia";
        String adqLocalidade = (adquirenteConfig != null && adquirenteConfig.getLocalidade() != null)
                ? adquirenteConfig.getLocalidade()
                : "Praia";

        String plano = req.plano() != null && !req.plano().isBlank()
                ? req.plano()
                : (adquirenteTenant.getPlano() != null ? adquirenteTenant.getPlano().name() : "STARTER");

        // 7. Persistência de PagamentoSubscricao
        PagamentoSubscricao pagamento = PagamentoSubscricao.builder()
                .adquirenteTenantId(adquirenteTenant.getId())
                .valorPago(req.valorPago())
                .dataPagamento(req.dataPagamento())
                .metodo(req.metodo())
                .periodoInicio(req.periodoInicio())
                .periodoFim(req.periodoFim())
                .plano(plano)
                .criadoPorId(autor != null ? autor.getUserId() : null)
                .createdAt(agora)
                .build();
        pagamento = pagamentoSubscricaoRepository.save(pagamento);

        // 8. Persistência de DocumentoFiscal
        String numFormatado = numeroAtribuido.serieCodigo() + "/" + numeroAtribuido.numero();
        DocumentoFiscal doc = DocumentoFiscal.builder()
                .tenantId(lexcvTenantId)
                .tipo(TipoDocumentoFiscal.FR)
                .ambiente(ambiente)
                .serieId(numeroAtribuido.serieId())
                .serieCodigo(numeroAtribuido.serieCodigo())
                .ano(numeroAtribuido.ano())
                .numero(numeroAtribuido.numero())
                .numeroFormatado(numFormatado)
                .dataEmissao(numeroAtribuido.dataEmissao())
                .emitidoEm(agora)
                .emitenteNif(lexcvConfig.getNif())
                .emitenteFirma(lexcvConfig.getFirma())
                .emitenteMorada(lexcvConfig.getMorada())
                .emitenteLocalidade(lexcvConfig.getLocalidade())
                .emitenteRegimeIva(lexcvConfig.getRegimeIva())
                .emitenteMotivoIsencaoCodigo(lexcvConfig.getMotivoIsencaoCodigo())
                .emitenteMotivoIsencaoDescricao(lexcvConfig.getMotivoIsencaoCodigo() != null
                        ? MotivoIsencaoIva.porCodigo(lexcvConfig.getMotivoIsencaoCodigo()).map(MotivoIsencaoIva::descricao).orElse(null) : null)
                .emitenteMotivoIsencaoMencao(lexcvConfig.getMotivoIsencaoCodigo() != null
                        ? MotivoIsencaoIva.porCodigo(lexcvConfig.getMotivoIsencaoCodigo()).map(MotivoIsencaoIva::mencao).orElse(null) : null)
                .adquirenteNif(adqNif)
                .adquirenteNome(adqNome)
                .adquirenteMorada(adqMorada)
                .adquirenteLocalidade(adqLocalidade)
                .adquirenteTenantId(adquirenteTenant.getId())
                .pagamentoSubscricaoId(pagamento.getId())
                .metodoPagamento(req.metodo())
                .meioPagamentoCodigo(obterMeioPagamentoCodigo(req.metodo()))
                .moeda("CVE")
                .taxaIva(calculo.taxaIva())
                .totalBase(calculo.base())
                .totalIva(calculo.iva())
                .totalRetencao(BigDecimal.ZERO.setScale(2))
                .totalDocumento(calculo.total())
                .valorLiquido(calculo.liquidoRecebido())
                .chaveIdempotencia(req.chaveIdempotencia())
                .emitidoPorId(autor != null ? autor.getUserId() : null)
                .emitidoPorNome(autor != null ? autor.getNome() : "Plataforma LexCV")
                .build();
        doc = documentoFiscalRepository.save(doc);
        UUID documentoId = doc.getId();

        // 9. Persistência da Linha
        String descLinha = "Subscrição LexCV - Plano " + plano + " (" + req.periodoInicio() + " a " + req.periodoFim() + ")";
        DocumentoFiscalLinha linha = DocumentoFiscalLinha.builder()
                .tenantId(lexcvTenantId)
                .documentoFiscalId(documentoId)
                .numeroLinha(1)
                .descricao(descLinha)
                .quantidade(new BigDecimal("1.0000"))
                .precoUnitario(calculo.base())
                .valorBase(calculo.base())
                .taxaIva(calculo.taxaIva())
                .valorIva(calculo.iva())
                .valorRetencao(BigDecimal.ZERO.setScale(2))
                .totalLinha(calculo.total())
                .build();
        documentoFiscalLinhaRepository.save(linha);

        // 10. Comunicação Fiscal Outbox (inicialmente PENDENTE)
        ComunicacaoFiscal comunicacao = ComunicacaoFiscal.builder()
                .tenantId(lexcvTenantId)
                .documentoFiscalId(documentoId)
                .ambiente(ambiente)
                .estado(EstadoComunicacaoFiscal.PENDENTE)
                .tentativas(0)
                .proximaTentativaEm(agora)
                .createdAt(agora)
                .versao(0L)
                .build();
        comunicacaoFiscalRepository.save(comunicacao);

        // 11. Auditoria
        auditoriaFiscalService.registarEmissao(lexcvTenantId, autor, documentoId, doc.getNumeroFormatado());

        return toResponse(doc, pagamento, EstadoComunicacaoFiscal.PENDENTE);
    }

    private String obterMeioPagamentoCodigo(String metodo) {
        if (metodo == null) return "OU";
        return switch (metodo.toUpperCase()) {
            case "TRANSFERENCIA_BANCARIA", "TRANSFERENCIA" -> "TB";
            case "VINTI4", "CARTAO_CREDITO", "CARTAO_DEBITO" -> "CC";
            case "NUMERARIO", "DINHEIRO" -> "NU";
            case "CHEQUE" -> "CH";
            default -> "OU";
        };
    }

    private SubscricaoFaturaResponse toResponse(DocumentoFiscal doc, PagamentoSubscricao pag, EstadoComunicacaoFiscal estado) {
        return new SubscricaoFaturaResponse(
                doc.getId(),
                doc.getPagamentoSubscricaoId(),
                doc.getTipo(),
                doc.getSerieCodigo(),
                doc.getNumero(),
                doc.getNumeroFormatado(),
                doc.getDataEmissao(),
                doc.getEmitidoEm(),
                doc.getAdquirenteTenantId(),
                doc.getAdquirenteNome(),
                doc.getAdquirenteNif(),
                doc.getTotalBase(),
                doc.getTotalIva(),
                doc.getTotalDocumento(),
                doc.getMetodoPagamento(),
                pag != null ? pag.getPeriodoInicio() : null,
                pag != null ? pag.getPeriodoFim() : null,
                pag != null ? pag.getPlano() : null,
                estado
        );
    }
}
