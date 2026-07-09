package com.lexcv.services;

import com.lexcv.models.Notificacao;
import com.lexcv.models.User;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final UserRepository userRepository;

    // ÚNICO ponto de escrita de CRIAÇÃO de Notificacao em todo o código — nenhuma outra classe
    // deve chamar notificacaoRepository.save(...)/saveAll(...) diretamente para criar uma linha.
    // Centralizar aqui garante que "+ADMIN, nunca broadcast em massa" (NOTF-14) é a única forma
    // de uma notificação nascer.
    private static final int MAX_VARCHAR_LENGTH = 255;

    public Notificacao criar(UUID tenantId, UUID destinatarioId, String categoria, String titulo,
                              String mensagem, String entidadeTipo, String entidadeId, String linkUrl) {
        if (tenantId == null || destinatarioId == null) {
            throw new IllegalArgumentException("tenantId e destinatarioId são obrigatórios");
        }
        userRepository.findById(destinatarioId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "destinatarioId não pertence ao tenant informado"));

        requireNonBlank("categoria", categoria);
        requireNonBlank("titulo", titulo);
        requireNonBlank("mensagem", mensagem);
        requireNonBlank("entidadeTipo", entidadeTipo);
        requireNonBlank("entidadeId", entidadeId);

        requireMaxLength("categoria", categoria, MAX_VARCHAR_LENGTH);
        requireMaxLength("titulo", titulo, MAX_VARCHAR_LENGTH);
        requireMaxLength("entidadeTipo", entidadeTipo, MAX_VARCHAR_LENGTH);
        requireMaxLength("entidadeId", entidadeId, MAX_VARCHAR_LENGTH);
        requireMaxLength("linkUrl", linkUrl, MAX_VARCHAR_LENGTH);

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

    private static void requireNonBlank(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " é obrigatório");
        }
    }

    private static void requireMaxLength(String fieldName, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " excede o tamanho máximo de " + maxLength + " caracteres");
        }
    }

    // Fan-out: uma linha independente por cada ADMIN atual do tenant, cada uma com o seu próprio
    // estado "lida" — nunca uma linha partilhada com uma flag "é admin". Package-private porque
    // só é chamado a partir deste serviço (e do teste, no mesmo pacote); a Phase 87 acrescentará
    // os métodos públicos notificarFaseEntrada/notificarDocumentoNovo/etc. que reutilizam este
    // helper — não são adicionados agora (gatilhos reais são fora do âmbito desta fase).
    @Transactional
    void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                          String entidadeTipo, String entidadeId, String linkUrl) {
        notificarAdmins(tenantId, categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl, null);
    }

    // Overload com exclusão de ator (Phase 87) — usado por DOCUMENTO_NOVO e PARECER_ATRIBUIDO para
    // que o autor da própria ação não seja notificado da sua ação, mesmo quando ele próprio é ADMIN.
    // O notificarAdmins de 7 args (acima) delega aqui com excluirUserId=null, preservando o
    // comportamento pré-existente (Phase 86) para FASE_ENTRADA/PROCESSO_ATRIBUIDO, que não excluem ator.
    @Transactional
    void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                          String entidadeTipo, String entidadeId, String linkUrl, UUID excluirUserId) {
        for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
            if (excluirUserId != null && excluirUserId.equals(admin.getId())) {
                continue;
            }
            // CR-01 (Phase 87 code review, iteration 2): isolate each admin so one
            // stale/orphaned admin reference can never prevent the rest of the ADMIN
            // fan-out from being notified.
            try {
                criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
            } catch (IllegalArgumentException ex) {
                log.warn("{}: admin {} inválido/órfão, notificação ADMIN ignorada para este destinatário",
                        categoria, admin.getId(), ex);
            }
        }
    }

    // NOTF-15: entrada de nova fase no processo. Sem exclusão de ator (CONTEXT.md não a exige aqui).
    // responsavelId é nullable (Processo ainda pode não ter responsável atribuído) — null-guard
    // evita que criar() lance IllegalArgumentException e rebente a transação do controller pai.
    public void notificarFaseEntrada(UUID tenantId, UUID processoId, UUID responsavelId,
                                      String numeroProcesso, String nomeFase, String linkUrl) {
        String numeroTexto = numeroProcesso != null ? numeroProcesso : "(sem número)";
        String titulo = "Nova fase";
        String mensagem = "O processo " + numeroTexto + " entrou na fase " + nomeFase;
        if (responsavelId != null) {
            // CR-01 (Phase 87 code review, iteration 2): isolate the primary recipient so a
            // stale/orphaned responsavelId can never prevent the unconditional ADMIN fan-out
            // below from running -- previously this exception propagated out of the method,
            // silently skipping notificarAdmins(...) entirely.
            try {
                criar(tenantId, responsavelId, "FASE_ENTRADA", titulo, mensagem, "processo",
                        processoId.toString(), linkUrl);
            } catch (IllegalArgumentException ex) {
                log.warn("FASE_ENTRADA: responsavelId {} inválido/órfão, notificação primária ignorada",
                        responsavelId, ex);
            }
        }
        notificarAdmins(tenantId, "FASE_ENTRADA", titulo, mensagem, "processo", processoId.toString(), linkUrl);
    }

    // NOTF-18: processo atribuído/reatribuído. Sem exclusão de ator (CONTEXT.md não a exige aqui).
    // Mensagem do destinatário em 2ª pessoa (texto travado por CONTEXT.md); mensagem do ADMIN em
    // 3ª pessoa, sem nome do ator.
    public void notificarProcessoAtribuido(UUID tenantId, UUID processoId, UUID responsavelId,
                                            String numeroProcesso, String linkUrl) {
        // WR-02 (Phase 87 code review): self-defending null-guard. Unlike
        // notificarFaseEntrada (where the ADMIN fan-out is unconditionally correct
        // because "a fase entrada happened" regardless of responsável), this method's
        // ADMIN message asserts an assignment occurred -- with a null responsavelId,
        // nothing was actually assigned, so the whole method (including the ADMIN
        // broadcast) must no-op rather than rely on every caller externally guarding
        // against null before calling.
        if (responsavelId == null) {
            return;
        }
        String numeroTexto = numeroProcesso != null ? numeroProcesso : "(sem número)";
        String titulo = "Processo atribuído";
        String mensagemDest = "Foi-lhe atribuído o processo " + numeroTexto + ".";
        String mensagemAdmin = "O processo " + numeroTexto + " foi atribuído a um novo responsável.";
        // CR-02 (Phase 87 code review, iteration 3): isolate the primary recipient, same
        // pattern as notificarFaseEntrada/notificarDocumentoNovo/notificarAdmins. Both call
        // sites (ResourceController.atribuirResponsavel) run inside an already-open
        // @Transactional method, right after their own tenant-membership validation of
        // responsavelId -- under READ_COMMITTED, a concurrent delete/deactivation of that
        // exact user can still become visible to criar()'s re-validation query before this
        // transaction commits. Uncaught, that IllegalArgumentException would roll back the
        // whole enclosing transaction, undoing an already-persisted processo reassignment.
        try {
            criar(tenantId, responsavelId, "PROCESSO_ATRIBUIDO", titulo, mensagemDest, "processo",
                    processoId.toString(), linkUrl);
        } catch (IllegalArgumentException ex) {
            log.warn("PROCESSO_ATRIBUIDO: responsavelId {} inválido/órfão, notificação primária ignorada",
                    responsavelId, ex);
        }
        notificarAdmins(tenantId, "PROCESSO_ATRIBUIDO", titulo, mensagemAdmin, "processo",
                processoId.toString(), linkUrl);
    }

    // NOTF-16: novo documento em processo/cliente. Ator (quem fez o upload) é sempre excluído do
    // primário E do fan-out ADMIN. Destinatários deduplicados via LinkedHashSet (ex.: o mesmo user
    // é ao mesmo tempo advogado e administrativo de um cliente) — preserva ordem de inserção para
    // tornar a asserção de teste determinística. Mensagem única em 3ª pessoa serve responsável e
    // admins igualmente (não há distinção 2ª/3ª pessoa aqui, ao contrário de PROCESSO_ATRIBUIDO).
    //
    // Nota de design: NÃO há dedup entre o destinatário primário e o fan-out ADMIN — se um membro
    // da equipa também for ADMIN recebe 2 linhas. Comportamento preservado de notificarAdmins
    // (Phase 86), não "corrigido" aqui.
    public void notificarDocumentoNovo(UUID tenantId, String documentoId, Collection<UUID> destinatarios,
                                        String nomeDocumento, String linkUrl, UUID atorId) {
        String titulo = "Novo documento";
        String mensagem = "Foi adicionado o documento \"" + nomeDocumento + "\".";
        Set<UUID> destinatariosUnicos = new LinkedHashSet<>(destinatarios == null ? List.of() : destinatarios);
        for (UUID dest : destinatariosUnicos) {
            if (dest != null && !dest.equals(atorId)) {
                // CR-01 (Phase 87 code review, iteration 2): isolate each destinatario so one
                // stale/orphaned reference can never prevent the remaining destinatarios (in
                // iteration order) or the unconditional ADMIN fan-out below from being notified.
                try {
                    criar(tenantId, dest, "DOCUMENTO_NOVO", titulo, mensagem, "documento", documentoId, linkUrl);
                } catch (IllegalArgumentException ex) {
                    log.warn("DOCUMENTO_NOVO: destinatario {} inválido/órfão, ignorado", dest, ex);
                }
            }
        }
        notificarAdmins(tenantId, "DOCUMENTO_NOVO", titulo, mensagem, "documento", documentoId, linkUrl, atorId);
    }

    // NOTF-19: parecer atribuído a um advogado (criação com advogado já definido, ou reatribuição
    // posterior). Ator (quem atribuiu) é sempre excluído do primário E do fan-out ADMIN — cobre o
    // caso de auto-atribuição, em que o próprio advogado é quem executa a ação.
    //
    // Mesma nota de design de notificarDocumentoNovo: sem dedup entre primário e fan-out ADMIN.
    public void notificarParecerAtribuido(UUID tenantId, String solicitacaoId, UUID advogadoId,
                                           String linkUrl, UUID atorId) {
        String titulo = "Parecer atribuído";
        String mensagemDest = "Foi-lhe atribuído um parecer jurídico.";
        String mensagemAdmin = "Um parecer jurídico foi atribuído a um advogado.";
        // CR-02 (Phase 87 code review, iteration 3): isolate the primary recipient -- same
        // reasoning as notificarProcessoAtribuido above. Both ParecerController.createSolicitacao
        // and .atribuirAdvogado run this inside an already-open @Transactional method, right
        // after validateAdvogado(...) confirms tenant membership; a concurrent deactivation/
        // deletion of that same advogado can still make criar()'s own re-validation fail before
        // this transaction commits, and an uncaught IllegalArgumentException here would roll
        // back the already-persisted parecer creation/reassignment.
        if (advogadoId != null && !advogadoId.equals(atorId)) {
            try {
                criar(tenantId, advogadoId, "PARECER_ATRIBUIDO", titulo, mensagemDest, "parecer_solicitacao",
                        solicitacaoId, linkUrl);
            } catch (IllegalArgumentException ex) {
                log.warn("PARECER_ATRIBUIDO: advogadoId {} inválido/órfão, notificação primária ignorada",
                        advogadoId, ex);
            }
        }
        notificarAdmins(tenantId, "PARECER_ATRIBUIDO", titulo, mensagemAdmin, "parecer_solicitacao",
                solicitacaoId, linkUrl, atorId);
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
