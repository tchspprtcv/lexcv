package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.CriarNotaCreditoSubscricaoRequest;
import com.lexcv.dtos.SubscricaoFaturaResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.PagamentoSubscricao;
import com.lexcv.models.Tenant;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalLinhaRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.PagamentoSubscricaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 138 (SUBS-03, SUBS-05): Serviço de emissão de Nota de Crédito sobre Fatura-Recibo de subscrição
 * emitida pela plataforma LexCV.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscricaoNotaCreditoService {

    private final PlatformFaturacaoConfigService platformConfigService;
    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalLinhaRepository documentoFiscalLinhaRepository;
    private final PagamentoSubscricaoRepository pagamentoSubscricaoRepository;
    private final ComunicacaoFiscalRepository comunicacaoFiscalRepository;
    private final NumeracaoService numeracaoService;
    private final AuditoriaFiscalService auditoriaFiscalService;
    private final Clock clock;

    @Transactional
    public SubscricaoFaturaResponse emitirNotaCredito(UUID documentoOrigemId, UserPrincipal autor, CriarNotaCreditoSubscricaoRequest req) {
        Objects.requireNonNull(documentoOrigemId, "documentoOrigemId");
        Objects.requireNonNull(req, "req");

        Tenant lexcvTenant = platformConfigService.obterTenantPlataforma();
        UUID lexcvTenantId = lexcvTenant.getId();

        // 1. Procura documento de origem da plataforma
        DocumentoFiscal origem = documentoFiscalRepository.findByIdAndTenantId(documentoOrigemId, lexcvTenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, "DOCUMENTO_NAO_ENCONTRADO",
                        "Fatura-Recibo de subscrição não encontrada."));

        if (origem.getTipo() != TipoDocumentoFiscal.FR) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "TIPO_DOCUMENTO_INVALIDO",
                    "Uma Nota de Crédito só pode ser emitida sobre uma Fatura-Recibo.");
        }

        // 2. Idempotência
        var ncExistente = documentoFiscalRepository.findByTenantIdAndChaveIdempotencia(lexcvTenantId, req.chaveIdempotencia());
        if (ncExistente.isPresent()) {
            DocumentoFiscal nc = ncExistente.get();
            if (!Objects.equals(nc.getDocumentoOrigemId(), documentoOrigemId)) {
                throw new RecusaFiscalException(HttpStatus.CONFLICT, "CHAVE_REUTILIZADA",
                        "A chave de idempotência já foi utilizada para outro documento.");
            }
            ComunicacaoFiscal com = comunicacaoFiscalRepository.findByTenantIdAndDocumentoFiscalId(lexcvTenantId, nc.getId()).orElse(null);
            return toResponse(nc, com != null ? com.getEstado() : EstadoComunicacaoFiscal.PENDENTE);
        }

        // 3. Teto de crédito
        List<DocumentoFiscal> ncsAnteriores = documentoFiscalRepository
                .findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc(lexcvTenantId, documentoOrigemId);
        BigDecimal totalJaCreditado = ncsAnteriores.stream()
                .map(DocumentoFiscal::getTotalDocumento)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoCreditavel = origem.getTotalDocumento().subtract(totalJaCreditado);
        if (saldoCreditavel.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENTO_TOTALMENTE_CREDITADO",
                    "A Fatura-Recibo de subscrição já se encontra totalmente creditada.");
        }

        BigDecimal valorCredito = req.valorTotal() != null ? req.valorTotal() : saldoCreditavel;
        if (valorCredito.compareTo(BigDecimal.ZERO) <= 0 || valorCredito.compareTo(saldoCreditavel) > 0) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "VALOR_CREDITO_INVALIDO",
                    "O valor a creditar (" + valorCredito + ") é inválido ou excede o saldo disponível (" + saldoCreditavel + ").");
        }

        // 4. Numeração sequencial sob lock na série NC da plataforma
        NumeroFiscalAtribuido numeroAtribuido = numeracaoService.proximoNumero(lexcvTenantId, TipoDocumentoFiscal.NC, origem.getAmbiente());

        // 5. Decomposição de base e IVA proporcionais à FR
        BigDecimal taxaIva = origem.getTaxaIva();
        CalculoFiscal.ResultadoCalculo calculo = CalculoFiscal.calcular(valorCredito, origem.getEmitenteRegimeIva(), taxaIva, null);

        Instant agora = clock.instant();

        // 6. Registo de estorno de subscrição (valor negativo)
        PagamentoSubscricao estorno = PagamentoSubscricao.builder()
                .adquirenteTenantId(origem.getAdquirenteTenantId())
                .valorPago(valorCredito.negate())
                .dataPagamento(numeroAtribuido.dataEmissao())
                .metodo(origem.getMetodoPagamento())
                .criadoPorId(autor != null ? autor.getUserId() : null)
                .createdAt(agora)
                .build();
        estorno = pagamentoSubscricaoRepository.save(estorno);

        // 7. Persistência da NC DocumentoFiscal
        String numFormatado = numeroAtribuido.serieCodigo() + "/" + numeroAtribuido.numero();
        DocumentoFiscal nc = DocumentoFiscal.builder()
                .tenantId(lexcvTenantId)
                .tipo(TipoDocumentoFiscal.NC)
                .ambiente(origem.getAmbiente())
                .serieId(numeroAtribuido.serieId())
                .serieCodigo(numeroAtribuido.serieCodigo())
                .ano(numeroAtribuido.ano())
                .numero(numeroAtribuido.numero())
                .numeroFormatado(numFormatado)
                .dataEmissao(numeroAtribuido.dataEmissao())
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
                .adquirenteTenantId(origem.getAdquirenteTenantId())
                .pagamentoSubscricaoId(estorno.getId())
                .documentoOrigemId(documentoOrigemId)
                .motivoCodigo(req.motivoCodigo())
                .motivoTexto(req.motivoTexto())
                .metodoPagamento(origem.getMetodoPagamento())
                .meioPagamentoCodigo(origem.getMeioPagamentoCodigo())
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
        nc = documentoFiscalRepository.save(nc);
        UUID ncId = nc.getId();

        // 8. Linha da NC
        DocumentoFiscalLinha linha = DocumentoFiscalLinha.builder()
                .tenantId(lexcvTenantId)
                .documentoFiscalId(ncId)
                .numeroLinha(1)
                .descricao("Estorno / Crédito ref. " + origem.getNumeroFormatado() + " - " + req.motivoTexto())
                .quantidade(new BigDecimal("1.0000"))
                .precoUnitario(calculo.base())
                .valorBase(calculo.base())
                .taxaIva(calculo.taxaIva())
                .valorIva(calculo.iva())
                .valorRetencao(BigDecimal.ZERO.setScale(2))
                .totalLinha(calculo.total())
                .build();
        documentoFiscalLinhaRepository.save(linha);

        // 9. Comunicação Fiscal PENDENTE
        ComunicacaoFiscal comunicacao = ComunicacaoFiscal.builder()
                .tenantId(lexcvTenantId)
                .documentoFiscalId(ncId)
                .ambiente(origem.getAmbiente())
                .estado(EstadoComunicacaoFiscal.PENDENTE)
                .tentativas(0)
                .proximaTentativaEm(agora)
                .createdAt(agora)
                .versao(0L)
                .build();
        comunicacaoFiscalRepository.save(comunicacao);

        // 10. Auditoria
        auditoriaFiscalService.registarEmissaoNotaCredito(lexcvTenantId, autor, ncId, nc.getNumeroFormatado(), origem.getNumeroFormatado());

        return toResponse(nc, EstadoComunicacaoFiscal.PENDENTE);
    }

    private SubscricaoFaturaResponse toResponse(DocumentoFiscal doc, EstadoComunicacaoFiscal estado) {
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
                null,
                null,
                null,
                estado
        );
    }
}
