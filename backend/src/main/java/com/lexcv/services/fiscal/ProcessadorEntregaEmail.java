package com.lexcv.services.fiscal;

import com.lexcv.exceptions.StorageUnavailableException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.fiscal.email.EntregaEmailGateway;
import com.lexcv.fiscal.email.MensagemEmailFiscal;
import com.lexcv.fiscal.email.ResultadoEnvioEmail;
import com.lexcv.fiscal.pdf.FalhaGeracaoPdf;
import com.lexcv.models.EntregaEmailFiscal;
import com.lexcv.models.EstadoEntregaEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Phase 137 (ENTR-03, ENTR-04, ENTR-05): processa UMA entrega por email reclamada da fila.
 *
 * <p>Passos: snapshot (tx só de leitura, tenant da linha) -> destinatário revalidado -> PDF
 * ({@link PdfDocumentoFiscalService#lerPdf}, fora de transação) -> composição
 * ({@link ComposicaoEmailFiscal}) -> renovação do lease (tx curta) IMEDIATAMENTE antes do envio ->
 * um único envio pela porta {@link EntregaEmailGateway} (fora de transação) -> registo do resultado
 * (tx curta, guardada pela versão) -> se a linha passou a {@code FALHOU}, notificação
 * {@code EMAIL_FISCAL_FALHOU} uma vez por episódio, DEPOIS do commit do resultado, com a contagem
 * real de tentativas.
 *
 * <p>Estados: {@code Enviado} -> {@code ENVIADO}; {@code ErroPermanente} -> {@code FALHOU};
 * {@code ErroTransitorio} -> {@code PENDENTE} com {@link BackoffEntregaEmail#atraso}, ou
 * {@code FALHOU} na {@link EntregaEmailFiscal#MAX_TENTATIVAS}.ª tentativa.
 *
 * <p>Sem anotações de transação: todas as transações vivem em {@link EntregaEmailTransacoes}.
 *
 * <p>Isolamento (T-137-58, T-137-59): {@link #processar} nunca lança uma {@link Exception}; um
 * {@link Error} da JVM propaga. Só códigos e mensagens FIXOS chegam à base de dados; os logs levam
 * ids, a tentativa e a classe da exceção -- nunca o destinatário, o assunto nem o texto do documento.
 */
@Slf4j
@Service
public class ProcessadorEntregaEmail {

    static final String DOCUMENTO_INEXISTENTE = "DOCUMENTO_INEXISTENTE";
    static final String MSG_DOCUMENTO_INEXISTENTE = "O documento fiscal não foi encontrado.";
    static final String DESTINATARIO_INVALIDO = "DESTINATARIO_INVALIDO";
    static final String MSG_DESTINATARIO_INVALIDO = "O endereço de email do cliente não é válido.";
    static final String STORAGE_INDISPONIVEL = "STORAGE_INDISPONIVEL";
    static final String MSG_STORAGE_INDISPONIVEL = "O armazenamento de ficheiros está indisponível.";
    static final String FALHA_PDF = "FALHA_PDF";
    static final String MSG_FALHA_PDF = "Não foi possível gerar o PDF do documento.";
    static final String FALHA_INTERNA = "FALHA_INTERNA";
    static final String MSG_FALHA_INTERNA = "Falha interna ao preparar o envio.";

    private final EntregaEmailTransacoes transacoes;
    private final PdfDocumentoFiscalService pdfService;
    private final ComposicaoEmailFiscal composicao;
    private final EntregaEmailGateway gateway;
    private final NotificacaoEntregaEmailFiscal notificacao;
    private final Clock clock;
    private final Duration lease;

    @Autowired
    public ProcessadorEntregaEmail(EntregaEmailTransacoes transacoes, PdfDocumentoFiscalService pdfService,
                                   ComposicaoEmailFiscal composicao, EntregaEmailGateway gateway,
                                   NotificacaoEntregaEmailFiscal notificacao, Clock clock,
                                   EmailProperties propriedades) {
        this(transacoes, pdfService, composicao, gateway, notificacao, clock, propriedades.outbox().lease());
    }

    ProcessadorEntregaEmail(EntregaEmailTransacoes transacoes, PdfDocumentoFiscalService pdfService,
                            ComposicaoEmailFiscal composicao, EntregaEmailGateway gateway,
                            NotificacaoEntregaEmailFiscal notificacao, Clock clock, Duration lease) {
        this.transacoes = transacoes;
        this.pdfService = pdfService;
        this.composicao = composicao;
        this.gateway = gateway;
        this.notificacao = notificacao;
        this.clock = clock;
        this.lease = lease;
    }

    /** Processa um item. Nunca lança uma {@link Exception} (um {@link Error} da JVM propaga). */
    public void processar(EntregaEmailReclamada item) {
        String[] numeroFormatado = new String[1];
        ResultadoEnvioEmail resultado;
        try {
            resultado = enviar(item, numeroFormatado);
        } catch (LeasePerdido perdido) {
            log.warn("Lease perdido antes do envio do email do documento fiscal {}: não enviado",
                    item.documentoFiscalId());
            return;
        } catch (Exception e) {
            log.error("Falha interna ao preparar o email do documento fiscal {} (entrega {}, tentativa {}): {}",
                    item.documentoFiscalId(), item.id(), item.tentativas(), e.getClass().getSimpleName());
            resultado = new ResultadoEnvioEmail.ErroTransitorio(FALHA_INTERNA, MSG_FALHA_INTERNA);
        }

        EstadoEntregaEmail estado;
        int linhas;
        try {
            estado = estadoPara(resultado, item.tentativas());
            Instant proxima = estado == EstadoEntregaEmail.PENDENTE
                    ? clock.instant().plus(BackoffEntregaEmail.atraso(Math.max(1, item.tentativas())))
                    : null;
            linhas = transacoes.registarResultado(item, estado, codigo(resultado), mensagem(resultado), proxima);
        } catch (Exception e) {
            log.error("Resultado do envio do email do documento fiscal {} não registado (entrega {}): {}",
                    item.documentoFiscalId(), item.id(), e.getClass().getSimpleName());
            return;
        }
        if (linhas != 1) {
            log.warn("Lease perdido no envio do email do documento fiscal {}: resultado ignorado",
                    item.documentoFiscalId());
            return;
        }
        if (estado == EstadoEntregaEmail.FALHOU) {
            notificar(item, numeroFormatado[0]);
        }
    }

    /**
     * Notifica uma linha que o job fechou em {@code FALHOU} por ter esgotado as reclamações sem
     * resultado ({@link EntregaEmailTransacoes#encerrarEsgotadas}). Nunca lança uma {@link Exception}.
     */
    public void notificarEsgotada(EntregaEmailReclamada item) {
        notificar(item, null);
    }

    private ResultadoEnvioEmail enviar(EntregaEmailReclamada item, String[] numeroFormatado) {
        Optional<SnapshotEntregaEmail> carregado =
                transacoes.carregarSnapshot(item.tenantId(), item.documentoFiscalId());
        if (carregado.isEmpty()) {
            return new ResultadoEnvioEmail.ErroPermanente(DOCUMENTO_INEXISTENTE, MSG_DOCUMENTO_INEXISTENTE);
        }
        SnapshotEntregaEmail snapshot = carregado.get();
        numeroFormatado[0] = snapshot.documento().getNumeroFormatado();

        Optional<String> destinatario = RegrasEntregaEmail.emailValido(item.destinatario());
        if (destinatario.isEmpty()) {
            return new ResultadoEnvioEmail.ErroPermanente(DESTINATARIO_INVALIDO, MSG_DESTINATARIO_INVALIDO);
        }
        if (snapshot.xml().isEmpty()) {
            return indisponivel();
        }

        byte[] pdf;
        try {
            pdf = pdfService.lerPdf(item.tenantId(), item.documentoFiscalId());
        } catch (FicheiroFiscalIndisponivelException e) {
            return indisponivel();
        } catch (StorageUnavailableException e) {
            return new ResultadoEnvioEmail.ErroTransitorio(STORAGE_INDISPONIVEL, MSG_STORAGE_INDISPONIVEL);
        } catch (FalhaGeracaoPdf e) {
            return new ResultadoEnvioEmail.ErroTransitorio(FALHA_PDF, MSG_FALHA_PDF);
        }

        MensagemEmailFiscal mensagem = composicao.compor(snapshot, destinatario.get(), pdf);

        // O lease do lote foi dado na reclamação; renova-o para ESTE item mesmo antes do envio. Se
        // outro worker já reclamou a linha, este não envia (nunca dois envios do mesmo documento).
        if (!transacoes.renovarLease(item, lease)) {
            throw new LeasePerdido();
        }
        return gateway.enviar(mensagem);
    }

    private static ResultadoEnvioEmail indisponivel() {
        return new ResultadoEnvioEmail.ErroTransitorio(FicheiroFiscalIndisponivelException.CODIGO,
                FicheiroFiscalIndisponivelException.MENSAGEM);
    }

    private static EstadoEntregaEmail estadoPara(ResultadoEnvioEmail resultado, int tentativas) {
        return switch (resultado) {
            case ResultadoEnvioEmail.Enviado enviado -> EstadoEntregaEmail.ENVIADO;
            case ResultadoEnvioEmail.ErroPermanente permanente -> EstadoEntregaEmail.FALHOU;
            case ResultadoEnvioEmail.ErroTransitorio transitorio -> tentativas >= EntregaEmailFiscal.MAX_TENTATIVAS
                    ? EstadoEntregaEmail.FALHOU
                    : EstadoEntregaEmail.PENDENTE;
        };
    }

    private static String codigo(ResultadoEnvioEmail resultado) {
        return switch (resultado) {
            case ResultadoEnvioEmail.Enviado enviado -> null;
            case ResultadoEnvioEmail.ErroPermanente p -> p.codigo();
            case ResultadoEnvioEmail.ErroTransitorio t -> t.codigo();
        };
    }

    private static String mensagem(ResultadoEnvioEmail resultado) {
        return switch (resultado) {
            case ResultadoEnvioEmail.Enviado enviado -> null;
            case ResultadoEnvioEmail.ErroPermanente p -> p.mensagem();
            case ResultadoEnvioEmail.ErroTransitorio t -> t.mensagem();
        };
    }

    /** Episódio = {@code reenvios}; tentativas = contagem real da linha (W2: "1 tentativa" ou "{n} tentativas"). */
    private void notificar(EntregaEmailReclamada item, String numeroFormatado) {
        try {
            String numero = numeroFormatado;
            if (numero == null) {
                numero = transacoes.carregarSnapshot(item.tenantId(), item.documentoFiscalId())
                        .map(s -> s.documento().getNumeroFormatado())
                        .orElse(item.documentoFiscalId().toString());
            }
            notificacao.notificarFalhaPersistente(item.tenantId(), item.documentoFiscalId(), numero,
                    item.reenvios(), item.tentativas());
        } catch (Exception e) {
            log.error("Notificação da falha de envio do email do documento fiscal {} não criada: {}",
                    item.documentoFiscalId(), e.getClass().getSimpleName());
        }
    }

    /** O worker deixou de ser o dono da linha antes do envio. Só circula dentro desta classe. */
    private static final class LeasePerdido extends RuntimeException {
        private static final long serialVersionUID = 1L;

        LeasePerdido() {
            super(null, null, false, false);
        }
    }
}
