package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ReprocessarComunicacaoResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.FilaComunicacaoFiscal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Phase 136 (DFE-05): reprocessamento manual da comunicação de um documento em {@code ERRO} ou
 * {@code REJEITADO}.
 *
 * <p>Numa só transação: o documento tem de existir no tenant do chamador (senão o mesmo 404 do
 * detalhe, sem oráculo); a linha de comunicação é reposta em {@code PENDENTE} por um UPDATE
 * condicional preso ao tenant ({@link FilaComunicacaoFiscal#reporPendente}); 0 linhas -> 409; e o
 * evento de auditoria é gravado na mesma transação. Nunca toca no documento nem na linha XML: a
 * próxima tentativa reutiliza o XML já gravado (mesmo IUD), se existir.
 */
@Service
@RequiredArgsConstructor
public class ReprocessamentoComunicacaoService {

    static final String CODIGO_ESTADO_INVALIDO = "COMUNICACAO_ESTADO_INVALIDO";
    static final String MSG_ESTADO_INVALIDO = "Só é possível reprocessar comunicações em erro ou rejeitadas.";
    static final String CODIGO_NAO_ENCONTRADO = "DOCUMENTO_FISCAL_NAO_ENCONTRADO";
    static final String MSG_NAO_ENCONTRADO = "Documento fiscal não encontrado.";

    private final DocumentoFiscalRepository documentoRepository;
    private final ComunicacaoFiscalRepository comunicacaoRepository;
    private final FilaComunicacaoFiscal fila;
    private final AuditoriaFiscalService auditoria;
    private final Clock clock;

    @Transactional
    public ReprocessarComunicacaoResponse reprocessar(UUID tenantId, UserPrincipal autor, UUID documentoId) {
        DocumentoFiscal documento = documentoRepository.findByIdAndTenantId(documentoId, tenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, CODIGO_NAO_ENCONTRADO,
                        MSG_NAO_ENCONTRADO));
        EstadoComunicacaoFiscal estadoAnterior = comunicacaoRepository
                .findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)
                .map(ComunicacaoFiscal::getEstado)
                .orElse(null);
        if (estadoAnterior == null || fila.reporPendente(tenantId, documentoId, clock.instant()) == 0) {
            throw estadoInvalido();
        }
        auditoria.registarReprocessamentoComunicacao(tenantId, autor, documentoId,
                documento.getNumeroFormatado(), estadoAnterior.name());
        return new ReprocessarComunicacaoResponse(EstadoComunicacaoFiscal.PENDENTE.name(), 0);
    }

    private static RecusaFiscalException estadoInvalido() {
        return new RecusaFiscalException(HttpStatus.CONFLICT, CODIGO_ESTADO_INVALIDO, MSG_ESTADO_INVALIDO);
    }
}
