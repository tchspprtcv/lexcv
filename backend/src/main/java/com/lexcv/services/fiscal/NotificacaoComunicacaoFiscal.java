package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.User;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.ResolucaoPapeisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 136 (DFE-07): notificação in-app quando a comunicação de um documento fiscal passa a
 * {@code ERRO} definitivo (tentativas automáticas esgotadas).
 *
 * <p><b>Destinatários:</b> os utilizadores ATIVOS do mesmo escritório ({@code findByTenantId} com o
 * tenant da linha de comunicação, nunca outro) cujas permissões EFETIVAS incluem
 * {@code financeiro:manage}. "Efetivas" = exatamente o que o filtro de autenticação compõe a cada
 * pedido: {@code UserPrincipal.create} sobre {@link ResolucaoPapeisService#resolverNomesPapeis} e
 * {@link ResolucaoPapeisService#resolverPermissoesEfectivas} (incluindo o bloco que o papel ADMIN
 * recebe por código). Não se escolhe por nome de papel (ao contrário de {@code AlertasDiariosJob}):
 * com os papéis de escritório do v2.17, "ADMIN" não equivale a "tem financeiro:manage".
 *
 * <p><b>Depois do commit:</b> o método não é transacional. Quem chama (o job de comunicação) só o
 * invoca DEPOIS de a transação que grava {@code ERRO} ter feito commit; cada
 * {@code NotificacaoService.criar} corre na sua própria transação curta do repositório. Assim uma
 * notificação falhada nunca reverte o {@code ERRO}, e o método nunca lança: a falha de um
 * destinatário fica registada (só ids) e os restantes continuam a ser notificados.
 *
 * <p><b>Dedup por episódio:</b> {@code entidadeId = documentoId + ":" + episodio}, em que
 * {@code episodio} é o contador {@code reprocessamentos} da comunicação no momento da falha. O
 * {@code ON CONFLICT DO NOTHING} de {@code criar} torna repetições do mesmo episódio inócuas, e uma
 * nova falha depois de um reprocessamento (episódio seguinte) volta a notificar. A categoria não é
 * silenciável ({@code CategoriaNotificacao.COMUNICACAO_FISCAL_FALHOU}).
 */
@Slf4j
@Service
public class NotificacaoComunicacaoFiscal {

    public static final String CATEGORIA = "COMUNICACAO_FISCAL_FALHOU";
    public static final String ENTIDADE_TIPO = "documento_fiscal";
    public static final String PERMISSAO = "financeiro:manage";

    private final UserRepository userRepository;
    private final ResolucaoPapeisService resolucaoPapeisService;
    private final NotificacaoService notificacaoService;

    public NotificacaoComunicacaoFiscal(UserRepository userRepository,
                                        ResolucaoPapeisService resolucaoPapeisService,
                                        NotificacaoService notificacaoService) {
        this.userRepository = userRepository;
        this.resolucaoPapeisService = resolucaoPapeisService;
        this.notificacaoService = notificacaoService;
    }

    /**
     * Notifica a falha persistente da comunicação de um documento.
     *
     * @return número de notificações efetivamente criadas (repetições do mesmo episódio não contam)
     */
    public int notificarFalhaPersistente(UUID tenantId, UUID documentoId, String numeroFormatado, int episodio) {
        String titulo = "Falha na comunicação do documento " + numeroFormatado;
        String mensagem = "A comunicação do documento " + numeroFormatado + " falhou após várias tentativas. "
                + "Abra o documento e use \"Reprocessar comunicação\".";
        String entidadeId = documentoId + ":" + episodio;
        String linkUrl = "/financeiro/documentos-fiscais/" + documentoId;

        List<User> utilizadores;
        try {
            utilizadores = userRepository.findByTenantId(tenantId);
        } catch (RuntimeException e) {
            log.warn("Notificação de falha de comunicação não criada: documento {}, utilizadores não lidos ({})",
                    documentoId, e.getClass().getSimpleName());
            return 0;
        }

        int criadas = 0;
        for (User utilizador : utilizadores) {
            try {
                if (!Boolean.TRUE.equals(utilizador.getAtivo()) || !temPermissao(utilizador)) {
                    continue;
                }
                if (notificacaoService.criar(tenantId, utilizador.getId(), CATEGORIA, titulo, mensagem,
                        ENTIDADE_TIPO, entidadeId, linkUrl).isPresent()) {
                    criadas++;
                }
            } catch (RuntimeException e) {
                log.warn("Notificação de falha de comunicação não criada: documento {}, destinatário {} ({})",
                        documentoId, utilizador.getId(), e.getClass().getSimpleName());
            }
        }
        return criadas;
    }

    /** Mesmas permissões que o {@code JwtAuthenticationFilter} põe no principal deste utilizador. */
    private boolean temPermissao(User utilizador) {
        UserPrincipal principal = UserPrincipal.create(utilizador.getId(), utilizador.getTenantId(),
                utilizador.getNome(), utilizador.getEmail(),
                resolucaoPapeisService.resolverNomesPapeis(utilizador),
                resolucaoPapeisService.resolverPermissoesEfectivas(utilizador),
                Set.of());
        return principal.getPermissions().contains(PERMISSAO);
    }
}
