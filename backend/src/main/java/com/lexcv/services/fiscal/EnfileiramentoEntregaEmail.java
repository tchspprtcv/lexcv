package com.lexcv.services.fiscal;

import com.lexcv.models.Cliente;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import com.lexcv.repositories.ClienteRepository;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.FilaEntregaEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 137 (ENTR-03, T-137-70/71/72/74): o ÚNICO ponto de enfileiramento da entrega por email de
 * um documento fiscal.
 *
 * <p>Chamado por {@link ComunicacaoFiscalTransacoes#registarResultado} DENTRO da transação que
 * grava {@code ACEITE_SIMULADO} (outbox atómico: o resultado da comunicação e a linha de entrega
 * fazem commit juntos ou nenhum). Nunca é chamado pela transação do registo do pagamento nem da
 * Nota de Crédito ({@code EntregaForaDaTransacaoDoPagamentoTest}); {@code MANDATORY} recusa uma
 * chamada sem transação aberta.
 *
 * <p>Estado inicial: {@code DESLIGADO} quando o envio automático do escritório está desligado (ou
 * não há configuração) -- o job nunca reclama estas linhas; {@code SEM_EMAIL} quando o cliente não
 * tem email válido; {@code PENDENTE} com o email do cliente como destinatário nos restantes casos.
 * Uma linha por documento ({@link FilaEntregaEmail#criarSeAusente}): um segundo
 * {@code ACEITE_SIMULADO} (depois de um reprocessamento) não cria outra.
 *
 * <p>Destinatário: só a ficha do cliente ({@code Cliente.email}, lida pelo tenant da linha), porque
 * o documento não guarda o email do adquirente. O endereço nunca vai para os logs.
 */
@Slf4j
@Service
public class EnfileiramentoEntregaEmail {

    private final FilaEntregaEmail fila;
    private final DocumentoFiscalRepository documentoRepository;
    private final ConfiguracaoFiscalRepository configuracaoRepository;
    private final ClienteRepository clienteRepository;

    public EnfileiramentoEntregaEmail(FilaEntregaEmail fila, DocumentoFiscalRepository documentoRepository,
                                      ConfiguracaoFiscalRepository configuracaoRepository,
                                      ClienteRepository clienteRepository) {
        this.fila = fila;
        this.documentoRepository = documentoRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.clienteRepository = clienteRepository;
    }

    /** Cria a linha de entrega do documento, se ainda não existir, na transação de quem chama. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enfileirarAposAceite(UUID tenantId, UUID documentoId, Instant agora) {
        Optional<DocumentoFiscal> documento = documentoRepository.findByIdAndTenantId(documentoId, tenantId);
        if (documento.isEmpty()) {
            log.warn("Entrega por email não enfileirada: documento fiscal {} inexistente no tenant {}",
                    documentoId, tenantId);
            return;
        }
        boolean ligado = configuracaoRepository.findByTenantId(tenantId)
                .map(c -> Boolean.TRUE.equals(c.getEnvioEmailAutomatico()))
                .orElse(false);
        if (!ligado) {
            fila.criarSeAusente(UUID.randomUUID(), tenantId, documentoId, EstadoEntregaEmail.DESLIGADO, null, agora);
            return;
        }
        Optional<String> email = emailDoCliente(tenantId, documento.get().getClienteId());
        if (email.isEmpty()) {
            fila.criarSeAusente(UUID.randomUUID(), tenantId, documentoId, EstadoEntregaEmail.SEM_EMAIL, null, agora);
            return;
        }
        fila.criarSeAusente(UUID.randomUUID(), tenantId, documentoId, EstadoEntregaEmail.PENDENTE, email.get(), agora);
    }

    /** Email válido da ficha do cliente, só se o cliente for deste tenant (T-137-72). */
    private Optional<String> emailDoCliente(UUID tenantId, UUID clienteId) {
        if (clienteId == null) {
            return Optional.empty();
        }
        return clienteRepository.findById(clienteId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .map(Cliente::getEmail)
                .flatMap(RegrasEntregaEmail::emailValido);
    }
}
