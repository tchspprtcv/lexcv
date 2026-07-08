package com.lexcv.services;

import com.lexcv.models.Notificacao;
import com.lexcv.models.User;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final UserRepository userRepository;

    // ÚNICO ponto de escrita de CRIAÇÃO de Notificacao em todo o código — nenhuma outra classe
    // deve chamar notificacaoRepository.save(...)/saveAll(...) diretamente para criar uma linha.
    // Centralizar aqui garante que "+ADMIN, nunca broadcast em massa" (NOTF-14) é a única forma
    // de uma notificação nascer.
    public Notificacao criar(UUID tenantId, UUID destinatarioId, String categoria, String titulo,
                              String mensagem, String entidadeTipo, String entidadeId, String linkUrl) {
        Notificacao n = Notificacao.builder()
                .tenantId(tenantId)
                .destinatarioId(destinatarioId)
                .categoria(categoria)
                .titulo(titulo)
                .mensagem(mensagem)
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .linkUrl(linkUrl)
                .build();
        return notificacaoRepository.save(n);
    }

    // Fan-out: uma linha independente por cada ADMIN atual do tenant, cada uma com o seu próprio
    // estado "lida" — nunca uma linha partilhada com uma flag "é admin". Package-private porque
    // só é chamado a partir deste serviço (e do teste, no mesmo pacote); a Phase 87 acrescentará
    // os métodos públicos notificarFaseEntrada/notificarDocumentoNovo/etc. que reutilizam este
    // helper — não são adicionados agora (gatilhos reais são fora do âmbito desta fase).
    void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                          String entidadeTipo, String entidadeId, String linkUrl) {
        for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
            criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
        }
    }

    // Mesmo ponto de escrita que criar(...), agora para MUTAÇÃO de estado — nenhuma outra classe
    // deve chamar notificacaoRepository.save(...) para marcar lida. @Transactional ao nível do
    // serviço (precedente: SetupService.initializeSystem, único @Transactional de serviço
    // existente) porque isto é um find-then-mutate-then-save composto. Devolve Optional vazio
    // (e nunca chama save) quando a linha não pertence ao tenant+destinatario indicados — é isto
    // que permite ao NotificacaoController (Plan 86-03) responder 404 sem nunca tocar numa linha
    // de outro destinatário.
    @Transactional
    public Optional<Notificacao> marcarLida(UUID tenantId, UUID destinatarioId, UUID id) {
        Notificacao n = notificacaoRepository.findByIdAndTenantIdAndDestinatarioId(id, tenantId, destinatarioId)
                .orElse(null);
        if (n == null) {
            return Optional.empty();
        }
        n.setLida(true);
        return Optional.of(notificacaoRepository.save(n));
    }

    // Load-mutate-saveAll, escopado por tenant+destinatario — nunca um @Modifying bulk update
    // (o código-base não tem nenhum; mantém-se essa convenção). Devolve a contagem de linhas
    // atualizadas.
    @Transactional
    public int marcarTodasLidas(UUID tenantId, UUID destinatarioId) {
        List<Notificacao> naoLidas = notificacaoRepository.findByTenantIdAndDestinatarioIdAndLidaFalse(tenantId, destinatarioId);
        naoLidas.forEach(n -> n.setLida(true));
        notificacaoRepository.saveAll(naoLidas);
        return naoLidas.size();
    }
}
