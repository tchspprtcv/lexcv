package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ReenviarEmailResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.EntregaEmailFiscalRepository;
import com.lexcv.repositories.FilaEntregaEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 137 (ENTR-04): reenvio manual do email de um documento fiscal ({@code financeiro:edit}
 * exato, verificado no controlador).
 *
 * <p>Numa só transação, por esta ordem (cada recusa com código fixo, antes de qualquer escrita):
 * <ol>
 *   <li>documento no tenant do chamador, senão 404 {@code DOCUMENTO_FISCAL_NAO_ENCONTRADO} (o mesmo
 *       do detalhe, sem oráculo);</li>
 *   <li>linha de entrega existente, senão 409 {@code ENTREGA_ESTADO_INVALIDO};</li>
 *   <li>SMTP configurado na instalação, senão 422 {@code SMTP_NAO_CONFIGURADO};</li>
 *   <li>envio automático ligado no escritório, senão 422 {@code ENVIO_EMAIL_DESLIGADO} (nunca se
 *       contorna a opção do escritório, T-137-50);</li>
 *   <li>comunicação {@code ACEITE_SIMULADO}, senão 422 {@code COMUNICACAO_NAO_ACEITE};</li>
 *   <li>estado reenviável ({@code FALHOU}, {@code ENVIADO}, {@code SEM_EMAIL}), senão 409;</li>
 *   <li>email ATUAL e válido na ficha do cliente (mesmo tenant), senão 422 {@code SEM_EMAIL_CLIENTE};</li>
 *   <li>{@link FilaEntregaEmail#reporPendente} preso ao tenant e ao estado; 0 linhas (corrida
 *       perdida) -> 409.</li>
 * </ol>
 * A entrega volta a {@code PENDENTE} com tentativas a zero e um novo episódio ({@code reenvios + 1});
 * o evento de auditoria grava na mesma transação, sem o endereço. Não há chamada SMTP aqui: o job
 * envia na próxima execução.
 */
@Service
@RequiredArgsConstructor
public class ReenvioEmailFiscalService {

    static final String CODIGO_NAO_ENCONTRADO = "DOCUMENTO_FISCAL_NAO_ENCONTRADO";
    static final String MSG_NAO_ENCONTRADO = "Documento fiscal não encontrado.";
    static final String CODIGO_ESTADO_INVALIDO = "ENTREGA_ESTADO_INVALIDO";
    static final String MSG_ESTADO_INVALIDO =
            "O email deste documento não pode ser reenviado no estado atual. Atualize e verifique o estado.";
    static final String CODIGO_SMTP_NAO_CONFIGURADO = "SMTP_NAO_CONFIGURADO";
    static final String MSG_SMTP_NAO_CONFIGURADO = "O servidor de email não está configurado nesta instalação.";
    static final String CODIGO_ENVIO_DESLIGADO = "ENVIO_EMAIL_DESLIGADO";
    static final String MSG_ENVIO_DESLIGADO =
            "O envio automático por email está desligado nas definições de faturação.";
    static final String CODIGO_COMUNICACAO_NAO_ACEITE = "COMUNICACAO_NAO_ACEITE";
    static final String MSG_COMUNICACAO_NAO_ACEITE =
            "O documento só pode ser enviado depois de aceite na comunicação.";
    static final String CODIGO_SEM_EMAIL_CLIENTE = "SEM_EMAIL_CLIENTE";
    static final String MSG_SEM_EMAIL_CLIENTE = "O cliente continua sem email registado.";

    private final DocumentoFiscalRepository documentoRepository;
    private final EntregaEmailFiscalRepository entregaRepository;
    private final ConfiguracaoFiscalRepository configuracaoRepository;
    private final ComunicacaoFiscalRepository comunicacaoRepository;
    private final ClienteRepository clienteRepository;
    private final FilaEntregaEmail fila;
    private final AuditoriaFiscalService auditoria;
    private final EmailProperties emailProperties;
    private final Clock clock;

    @Transactional
    public ReenviarEmailResponse reenviar(UUID tenantId, UserPrincipal autor, UUID documentoId) {
        DocumentoFiscal documento = documentoRepository.findByIdAndTenantId(documentoId, tenantId)
                .orElseThrow(() -> new RecusaFiscalException(HttpStatus.NOT_FOUND, CODIGO_NAO_ENCONTRADO,
                        MSG_NAO_ENCONTRADO));
        EntregaEmailFiscal entrega = entregaRepository.findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)
                .orElseThrow(ReenvioEmailFiscalService::estadoInvalido);
        if (!emailProperties.configurado()) {
            throw recusa(CODIGO_SMTP_NAO_CONFIGURADO, MSG_SMTP_NAO_CONFIGURADO);
        }
        boolean envioAutomatico = configuracaoRepository.findByTenantId(tenantId)
                .map(c -> Boolean.TRUE.equals(c.getEnvioEmailAutomatico()))
                .orElse(false);
        if (!envioAutomatico) {
            throw recusa(CODIGO_ENVIO_DESLIGADO, MSG_ENVIO_DESLIGADO);
        }
        EstadoComunicacaoFiscal comunicacao = comunicacaoRepository
                .findByTenantIdAndDocumentoFiscalId(tenantId, documentoId)
                .map(ComunicacaoFiscal::getEstado)
                .orElse(null);
        if (comunicacao != EstadoComunicacaoFiscal.ACEITE_SIMULADO) {
            throw recusa(CODIGO_COMUNICACAO_NAO_ACEITE, MSG_COMUNICACAO_NAO_ACEITE);
        }
        EstadoEntregaEmail estadoAnterior = entrega.getEstado();
        if (estadoAnterior == null || !estadoAnterior.reenviavelPorEstado()) {
            throw estadoInvalido();
        }
        String destinatario = emailAtualDoCliente(tenantId, documento.getClienteId())
                .orElseThrow(() -> recusa(CODIGO_SEM_EMAIL_CLIENTE, MSG_SEM_EMAIL_CLIENTE));
        if (fila.reporPendente(tenantId, documentoId, destinatario, clock.instant()) == 0) {
            throw estadoInvalido();
        }
        auditoria.registarReenvioEmail(tenantId, autor, documentoId, documento.getNumeroFormatado(),
                estadoAnterior.name());
        return new ReenviarEmailResponse(EstadoEntregaEmail.PENDENTE.name(), 0);
    }

    /** Email atual da ficha do cliente, só se o cliente for deste tenant e o endereço for válido. */
    private Optional<String> emailAtualDoCliente(UUID tenantId, UUID clienteId) {
        if (clienteId == null) {
            return Optional.empty();
        }
        return clienteRepository.findById(clienteId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .flatMap(c -> RegrasEntregaEmail.emailValido(c.getEmail()));
    }

    private static RecusaFiscalException estadoInvalido() {
        return new RecusaFiscalException(HttpStatus.CONFLICT, CODIGO_ESTADO_INVALIDO, MSG_ESTADO_INVALIDO);
    }

    private static RecusaFiscalException recusa(String codigo, String mensagem) {
        return new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensagem);
    }
}
