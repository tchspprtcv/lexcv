package com.lexcv.services.fiscal;

import com.lexcv.services.NotificacaoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Phase 137 (ENTR-05): notificação in-app quando a entrega por email de um documento fiscal ao
 * cliente termina em {@code FALHOU} (falha permanente ou tentativas automáticas esgotadas).
 *
 * <p><b>Destinatários:</b> exatamente os de {@code COMUNICACAO_FISCAL_FALHOU}, pela regra única
 * {@link NotificacaoComunicacaoFiscal#destinatariosFalhaFiscal(UUID)} (utilizadores ativos do mesmo
 * tenant com {@code financeiro:manage} ou {@code financeiro:edit} efetivos). A categoria
 * {@code EMAIL_FISCAL_FALHOU} não é silenciável.
 *
 * <p><b>Sem dados pessoais:</b> o texto só contém o número do documento e a contagem de
 * tentativas; o serviço nunca recebe o endereço do cliente nem o erro SMTP.
 *
 * <p><b>Episódio:</b> {@code entidadeId = documentoId + ":" + episodio}, com
 * {@code episodio = EntregaEmailFiscal.reenvios} no momento da falha; o {@code ON CONFLICT DO NOTHING}
 * de {@code NotificacaoService.criar} torna repetições do mesmo episódio inócuas e um reenvio manual
 * abre o episódio seguinte. {@code tentativas} é a contagem real do episódio (1 numa falha
 * permanente à primeira tentativa, 5 quando esgotadas), nunca um 5 fixo.
 *
 * <p><b>Depois do commit:</b> não transacional e nunca lança. O processador de email (137-14) só o
 * chama DEPOIS de a transação que grava {@code FALHOU} ter feito commit; a falha de um destinatário
 * fica registada (só ids e o tipo da exceção) e os restantes continuam a ser notificados.
 */
@Slf4j
@Service
public class NotificacaoEntregaEmailFiscal {

    public static final String CATEGORIA = "EMAIL_FISCAL_FALHOU";

    private final NotificacaoComunicacaoFiscal destinatarios;
    private final NotificacaoService notificacaoService;

    public NotificacaoEntregaEmailFiscal(NotificacaoComunicacaoFiscal destinatarios,
                                         NotificacaoService notificacaoService) {
        this.destinatarios = destinatarios;
        this.notificacaoService = notificacaoService;
    }

    /**
     * Notifica a falha persistente do envio por email de um documento.
     *
     * @param episodio   contador {@code reenvios} da linha de entrega no momento da falha
     * @param tentativas tentativas da linha quando terminou {@code FALHOU}
     * @return número de notificações efetivamente criadas (repetições do mesmo episódio não contam)
     */
    public int notificarFalhaPersistente(UUID tenantId, UUID documentoId, String numeroFormatado,
                                         int episodio, int tentativas) {
        String documento = numeroFormatado == null || numeroFormatado.isBlank()
                ? "do documento"
                : "do documento " + numeroFormatado;
        String titulo = "Falha no envio por email " + documento;
        String mensagem = "O envio do email " + documento + " ao cliente falhou após " + textoTentativas(tentativas)
                + ". Abra o documento para ver a última falha; quem tem permissão pode reenviar o email.";
        String entidadeId = documentoId + ":" + episodio;
        String linkUrl = "/financeiro/documentos-fiscais/" + documentoId;

        List<UUID> utilizadores;
        try {
            utilizadores = destinatarios.destinatariosFalhaFiscal(tenantId);
        } catch (RuntimeException e) {
            log.warn("Notificação de falha de envio por email não criada: documento {}, destinatários não resolvidos ({})",
                    documentoId, e.getClass().getSimpleName());
            return 0;
        }

        int criadas = 0;
        for (UUID destinatario : utilizadores) {
            try {
                if (notificacaoService.criar(tenantId, destinatario, CATEGORIA, titulo, mensagem,
                        NotificacaoComunicacaoFiscal.ENTIDADE_TIPO, entidadeId, linkUrl).isPresent()) {
                    criadas++;
                }
            } catch (RuntimeException e) {
                log.warn("Notificação de falha de envio por email não criada: documento {}, destinatário {} ({})",
                        documentoId, destinatario, e.getClass().getSimpleName());
            }
        }
        return criadas;
    }

    /** "1 tentativa" para n = 1, "{n} tentativas" nos restantes casos (nunca o literal "(s)"). */
    public static String textoTentativas(int n) {
        return n == 1 ? "1 tentativa" : n + " tentativas";
    }
}
