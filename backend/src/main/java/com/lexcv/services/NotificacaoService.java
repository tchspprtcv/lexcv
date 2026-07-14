package com.lexcv.services;

import com.lexcv.models.CategoriaNotificacao;
import com.lexcv.models.ClienteAdministrativo;
import com.lexcv.models.ClienteAdvogado;
import com.lexcv.models.Notificacao;
import com.lexcv.models.NotificacaoPreferencia;
import com.lexcv.models.User;
import com.lexcv.repositories.ClienteAdministrativoRepository;
import com.lexcv.repositories.ClienteAdvogadoRepository;
import com.lexcv.repositories.NotificacaoPreferenciaRepository;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final NotificacaoPreferenciaRepository notificacaoPreferenciaRepository;
    // NOTF-25: repositórios de junção tenant-scoped que alimentam resolverEquipaCliente — a
    // mesma dupla que ResourceController já injeta para o ramo cliente de uploadDocumento.
    private final ClienteAdvogadoRepository clienteAdvogadoRepository;
    private final ClienteAdministrativoRepository clienteAdministrativoRepository;

    // ÚNICO ponto de escrita de CRIAÇÃO de Notificacao em todo o código — nenhuma outra classe
    // deve chamar notificacaoRepository.save(...)/saveAll(...) diretamente para criar uma linha.
    // Centralizar aqui garante que "+ADMIN, nunca broadcast em massa" (NOTF-14) é a única forma
    // de uma notificação nascer.
    private static final int MAX_VARCHAR_LENGTH = 255;

    // NOTF-24: retorno Optional<Notificacao> -- "silenciado" é um terceiro resultado (validação
    // passou, nada persistido) e nunca deve ser conflacionado com o path de exceção (destinatário
    // órfão/inválido) nem com um null. Facto verificado (grep de todo o backend): nenhum caller lê
    // o valor de retorno de criar() -- os 5 métodos notificar*, o AlertasDiariosJob e os testes
    // existentes chamam-no como statement, logo esta mudança de assinatura é segura e contida a
    // este ficheiro.
    public Optional<Notificacao> criar(UUID tenantId, UUID destinatarioId, String categoria, String titulo,
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

        // NOTF-24: único guard de silenciamento de todo o subsistema, colocado AQUI -- o choke
        // point que TANTO os 5 métodos notificar* COMO o AlertasDiariosJob.notificar() (que chama
        // criar() diretamente, sem passar por nenhum notificar*) atravessam. Um guard colocado em
        // qualquer outro sítio deixaria o job diário escapar ao silenciamento (Pitfall 1).
        // isSilenciavelCategoria(...) primeiro garante curto-circuito: PRAZO_VENCIDO nunca sequer
        // consulta a preferência e é sempre entregue (Critério de Sucesso 2, defense-in-depth).
        if (CategoriaNotificacao.isSilenciavelCategoria(categoria)
                && notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(
                        tenantId, destinatarioId, categoria)) {
            log.debug("{}: categoria silenciada pelo destinatário {}, notificação não persistida",
                    categoria, destinatarioId);
            return Optional.empty();
        }

        // CR-01 (Phase 94 code review): atomic ON CONFLICT DO NOTHING upsert instead of
        // save()/saveAndFlush() -- mirrors silenciarCategoria()'s NotificacaoPreferenciaRepository
        // .upsertSilenciar() precedent (Phase 93 CR-01, same underlying bug class). id and
        // createdAt are generated here (client-side), not by the database, so the in-memory
        // Notificacao returned to the caller on the success path matches exactly what was
        // persisted -- no SELECT-after-INSERT round trip needed. See
        // NotificacaoRepository.inserirSeNaoDuplicado's Javadoc for the full trace of why
        // save()/saveAndFlush() + catch(DataIntegrityViolationException) cannot actually protect
        // the caller's ambient transaction on this method's real call paths.
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        int linhasInseridas = notificacaoRepository.inserirSeNaoDuplicado(id, tenantId, destinatarioId, categoria,
                entidadeTipo, entidadeId, titulo, mensagem, linkUrl, createdAt);
        if (linhasInseridas == 0) {
            log.warn("{}: notificação duplicada ignorada pelo índice único da BD para destinatario {}",
                    categoria, destinatarioId);
            return Optional.empty();
        }
        Notificacao n = Notificacao.builder()
                .id(id)
                .tenantId(tenantId)
                .destinatarioId(destinatarioId)
                .categoria(categoria)
                .titulo(titulo)
                .mensagem(mensagem)
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .linkUrl(linkUrl)
                .lida(false)
                .createdAt(createdAt)
                .build();
        return Optional.of(n);
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

    // NOTF-25: helper único de resolução da equipa do cliente -- devolve a união (deduplicada,
    // ordem de inserção preservada: advogados antes de administrativos) dos userId de
    // ClienteAdvogado + ClienteAdministrativo para o clienteId indicado. AMBOS os repositórios de
    // junção são consultados pelo par (clienteId, tenantId) -- nunca por clienteId sozinho -- o
    // filtro tenant_id na linha de junção É o próprio controlo de posse (Pitfall 10), sem
    // necessidade de uma consulta adicional a Cliente. Devolve o conjunto vazio, sem qualquer
    // chamada a repositório, quando clienteId é null (processo ainda sem cliente definido não tem
    // equipa a resolver). Público porque o plano 95-02 (ResourceController, ramo processo de
    // uploadDocumento) reutiliza esta MESMA implementação -- não deve existir uma segunda
    // resolução de equipa inline noutro sítio (Pitfall 3).
    // WR-03 (Phase 95 code review): declared return type narrowed from Set<UUID> to
    // LinkedHashSet<UUID> so the "advogados antes de administrativos" insertion-order guarantee
    // documented above is enforced by the compiler, not just by the concrete type chosen inside
    // the method body -- a future refactor to HashSet/Collectors.toSet() would now fail to
    // compile instead of silently breaking order-dependent callers/tests.
    public LinkedHashSet<UUID> resolverEquipaCliente(UUID tenantId, UUID clienteId) {
        if (clienteId == null) {
            return new LinkedHashSet<>();
        }
        LinkedHashSet<UUID> equipa = new LinkedHashSet<>();
        for (ClienteAdvogado ca : clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, tenantId)) {
            equipa.add(ca.getUserId());
        }
        for (ClienteAdministrativo ca : clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, tenantId)) {
            equipa.add(ca.getUserId());
        }
        return equipa;
    }

    // NOTF-27 (Phase 94): helper único que funde destinatário(s) primário(s) + fan-out ADMIN num
    // único LinkedHashSet<UUID> deduplicado ANTES do loop de criação, para que criar() seja
    // chamado no máximo uma vez por pessoa por evento -- mesmo quando o primário também é ADMIN
    // do mesmo tenant. Antes deste helper, os 4 métodos notificar* faziam uma chamada a criar()
    // para o primário e uma chamada independente e não coordenada a notificarAdmins() para o
    // fan-out; quando o primário também era ADMIN, ambas tentavam persistir uma linha para o
    // mesmo tuplo (tenant, destinatario, entidade_tipo, entidade_id, categoria), colidindo com a
    // constraint única uk_notificacao_dedup (Phase 88). Desde o CR-01 do code review da Phase 94,
    // criar() já não pode lançar DataIntegrityViolationException nesse caminho -- ver
    // NotificacaoRepository.inserirSeNaoDuplicado -- pelo que este método já não precisa de
    // apanhar essa exceção como backstop.
    // excluirUserId (ator) é removido de AMBOS os conjuntos antes do merge -- preserva o
    // comportamento pré-existente de DOCUMENTO_NOVO/PARECER_ATRIBUIDO (o autor nunca é notificado
    // da sua própria ação, mesmo sendo ADMIN).
    // NOTF-25: forma "fina" de 10 argumentos preservada tal e qual para notificarDocumentoNovo e
    // notificarParecerAtribuido (CONTEXT.md: parecer mantém-se individual) -- delega para a nova
    // forma de 11 argumentos com destinatariosSecundarios vazio, logo o comportamento observável
    // desta sobrecarga é idêntico ao de antes desta fase.
    private void criarComFanOutAdmin(UUID tenantId, String categoria, String titulo, String entidadeTipo,
                                      String entidadeId, String linkUrl, Collection<UUID> destinatariosPrimarios,
                                      String mensagemPrimario, String mensagemAdmin, UUID excluirUserId) {
        criarComFanOutAdmin(tenantId, categoria, titulo, entidadeTipo, entidadeId, linkUrl,
                destinatariosPrimarios, List.of(), mensagemPrimario, mensagemAdmin, excluirUserId);
    }

    // NOTF-25: sobrecarga de 11 argumentos que acrescenta um segundo nível de destinatários --
    // "secundários" (ex.: a equipa do cliente que não é o novo responsável) que recebem a MESMA
    // mensagem informativa que os ADMIN, distinta da mensagem em 2ª pessoa reservada aos
    // primários -- sem criar um caminho de escrita paralelo ao helper já existente (Phase 94).
    // destinatariosPrimarios ∪ destinatariosSecundarios ∪ fan-out ADMIN são fundidos no MESMO
    // LinkedHashSet deduplicado antes do loop de escrita -- um UUID presente em primários E
    // secundários (ou também ADMIN) permanece primário e é escrito uma única vez, preservando
    // exatamente a garantia de dedup do helper de 10 argumentos.
    private void criarComFanOutAdmin(UUID tenantId, String categoria, String titulo, String entidadeTipo,
                                      String entidadeId, String linkUrl, Collection<UUID> destinatariosPrimarios,
                                      Collection<UUID> destinatariosSecundarios, String mensagemPrimario,
                                      String mensagemInformativo, UUID excluirUserId) {
        // WR-01 (Phase 94 code review): validar os campos ao nível do EVENTO (partilhados por
        // todos os destinatários) uma única vez, ANTES do loop, e deixar propagar
        // IllegalArgumentException imediatamente -- em vez de deixar que a mesma falha (ex.:
        // linkUrl > 255 caracteres) seja apanhada, repetida e mal-interpretada como
        // "destinatario inválido/órfão" em CADA iteração do loop abaixo, mascarando um bug do
        // chamador como um problema de dados de um destinatário específico e causando perda
        // silenciosa da notificação para TODOS os destinatários (primário e ADMINs). A validação
        // per-destinatario dentro de criar() (linhas 53-63) mantém-se intacta como defesa em
        // profundidade -- esta chamada antecipada só cobre os campos que são idênticos em toda
        // iteração (categoria/titulo/entidadeTipo/entidadeId/linkUrl), nunca "dest"/"mensagem".
        requireNonBlank("categoria", categoria);
        requireNonBlank("titulo", titulo);
        requireNonBlank("entidadeTipo", entidadeTipo);
        requireNonBlank("entidadeId", entidadeId);
        requireMaxLength("categoria", categoria, MAX_VARCHAR_LENGTH);
        requireMaxLength("titulo", titulo, MAX_VARCHAR_LENGTH);
        requireMaxLength("entidadeTipo", entidadeTipo, MAX_VARCHAR_LENGTH);
        requireMaxLength("entidadeId", entidadeId, MAX_VARCHAR_LENGTH);
        requireMaxLength("linkUrl", linkUrl, MAX_VARCHAR_LENGTH);

        LinkedHashSet<UUID> primarios = new LinkedHashSet<>();
        for (UUID dest : destinatariosPrimarios) {
            if (dest != null && !dest.equals(excluirUserId)) {
                primarios.add(dest);
            }
        }
        LinkedHashSet<UUID> todos = new LinkedHashSet<>(primarios);
        for (UUID dest : destinatariosSecundarios) {
            if (dest != null && !dest.equals(excluirUserId)) {
                // Set deduplica por construção: um destinatário secundário que já é primário
                // não é re-adicionado -- fica marcado como primário (mensagemPrimario) e é
                // escrito uma única vez.
                todos.add(dest);
            }
        }
        // WR-02 (Phase 94 code review): AndAtivoTrue exclui admins desativados do fan-out --
        // mirrors the `ativo` check ResourceController.atribuirResponsavel already applies
        // before assigning a responsible party. Without this, a deactivated ADMIN account would
        // keep accumulating notification rows indefinitely, with nobody able to ever read/dismiss
        // them.
        for (User admin : userRepository.findByTenantIdAndRoleNameAndAtivoTrue(tenantId, "ADMIN")) {
            if (!admin.getId().equals(excluirUserId)) {
                // Set deduplica por construção: um admin que já é primário ou secundário não é
                // re-adicionado -- é aqui que a colisão de uk_notificacao_dedup deixa de
                // ser possível, em vez de ser "corrigida" depois de já ter acontecido.
                todos.add(admin.getId());
            }
        }
        for (UUID dest : todos) {
            String mensagem = primarios.contains(dest) ? mensagemPrimario : mensagemInformativo;
            try {
                // CR-01 (Phase 87 code review, iteration 2): isolate each destinatario so one
                // stale/orphaned reference can never prevent the remaining destinatarios (in
                // iteration order) from being notified.
                criar(tenantId, dest, categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
            } catch (IllegalArgumentException ex) {
                log.warn("{}: destinatario {} inválido/órfão, notificação ignorada para este destinatário",
                        categoria, dest, ex);
            }
        }
    }

    // NOTF-15/NOTF-25: entrada de nova fase no processo. Sem exclusão de ator (CONTEXT.md não a
    // exige aqui). primarios = equipa do cliente do processo (resolverEquipaCliente) ∪
    // responsavelId (nullable -- Processo ainda pode não ter responsável atribuído). Fase entrada
    // não tem distinção 2ª/3ª pessoa (CONTEXT.md), logo a mesma mensagem serve o(s) primário(s) e
    // o fan-out ADMIN -- mantém-se a forma de 10 argumentos do helper.
    public void notificarFaseEntrada(UUID tenantId, UUID processoId, UUID clienteId, UUID responsavelId,
                                      String numeroProcesso, String nomeFase, String linkUrl) {
        String numeroTexto = numeroProcesso != null ? numeroProcesso : "(sem número)";
        String titulo = "Nova fase";
        String mensagem = "O processo " + numeroTexto + " entrou na fase " + nomeFase;
        LinkedHashSet<UUID> primarios = new LinkedHashSet<>(resolverEquipaCliente(tenantId, clienteId));
        if (responsavelId != null) {
            primarios.add(responsavelId);
        }
        criarComFanOutAdmin(tenantId, "FASE_ENTRADA", titulo, "processo", processoId.toString(), linkUrl,
                primarios, mensagem, mensagem, null);
    }

    // NOTF-18/NOTF-25: processo atribuído/reatribuído. Sem exclusão de ator (CONTEXT.md não a
    // exige aqui). Mensagem do responsável em 2ª pessoa (texto travado por CONTEXT.md); resto da
    // equipa do cliente + ADMIN recebem a mensagem informativa em 3ª pessoa, sem nome do ator --
    // usa a sobrecarga de 11 argumentos do helper (destinatariosSecundarios = equipa menos o
    // responsável, para que um responsável também presente na equipa não seja escrito duas vezes
    // nem "perca" a mensagem em 2ª pessoa para a informativa).
    public void notificarProcessoAtribuido(UUID tenantId, UUID processoId, UUID clienteId, UUID responsavelId,
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
        String mensagemInformativo = "O processo " + numeroTexto + " foi atribuído a um novo responsável.";
        LinkedHashSet<UUID> equipa = new LinkedHashSet<>(resolverEquipaCliente(tenantId, clienteId));
        equipa.remove(responsavelId);
        criarComFanOutAdmin(tenantId, "PROCESSO_ATRIBUIDO", titulo, "processo", processoId.toString(), linkUrl,
                List.of(responsavelId), equipa, mensagemDest, mensagemInformativo, null);
    }

    // NOTF-16: novo documento em processo/cliente. Ator (quem fez o upload) é sempre excluído do
    // primário E do fan-out ADMIN (via excluirUserId no helper). Destinatários deduplicados pelo
    // LinkedHashSet do helper (ex.: o mesmo user é ao mesmo tempo advogado e administrativo de um
    // cliente) — preserva ordem de inserção para tornar a asserção de teste determinística.
    // Mensagem única em 3ª pessoa serve responsável e admins igualmente (não há distinção
    // 2ª/3ª pessoa aqui, ao contrário de PROCESSO_ATRIBUIDO).
    public void notificarDocumentoNovo(UUID tenantId, String documentoId, Collection<UUID> destinatarios,
                                        String nomeDocumento, String linkUrl, UUID atorId) {
        String titulo = "Novo documento";
        String mensagem = "Foi adicionado o documento \"" + nomeDocumento + "\".";
        criarComFanOutAdmin(tenantId, "DOCUMENTO_NOVO", titulo, "documento", documentoId, linkUrl,
                destinatarios == null ? List.of() : destinatarios, mensagem, mensagem, atorId);
    }

    // NOTF-19: parecer atribuído a um advogado (criação com advogado já definido, ou reatribuição
    // posterior). Ator (quem atribuiu) é sempre excluído do primário E do fan-out ADMIN (via
    // excluirUserId no helper) — cobre o caso de auto-atribuição, em que o próprio advogado é
    // quem executa a ação.
    public void notificarParecerAtribuido(UUID tenantId, String solicitacaoId, UUID advogadoId,
                                           String linkUrl, UUID atorId) {
        String titulo = "Parecer atribuído";
        String mensagemDest = "Foi-lhe atribuído um parecer jurídico.";
        String mensagemAdmin = "Um parecer jurídico foi atribuído a um advogado.";
        List<UUID> primarios = advogadoId != null ? List.of(advogadoId) : List.of();
        criarComFanOutAdmin(tenantId, "PARECER_ATRIBUIDO", titulo, "parecer_solicitacao", solicitacaoId, linkUrl,
                primarios, mensagemDest, mensagemAdmin, atorId);
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

    // NOTF-24: leitura das categorias silenciadas do próprio user -- alimenta o GET de
    // preferências (Plan 93-03). Escopado por tenant+user, nunca por user sozinho (Pitfall 10).
    public List<String> listarCategoriasSilenciadas(UUID tenantId, UUID userId) {
        return notificacaoPreferenciaRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(NotificacaoPreferencia::getCategoria)
                .toList();
    }

    // NOTF-24: silenciar uma categoria para o próprio user. Valida via CategoriaNotificacao --
    // rejeita categoria desconhecida E PRAZO_VENCIDO (a única categoria não-silenciável), mesmo
    // que o guard de criar() já a proteja por construção (defesa em profundidade explícita aqui).
    // Idempotente: uma segunda chamada para a mesma (tenant, user, categoria) não cria uma segunda
    // linha, respeitando a constraint única uk_notificacao_preferencia.
    @Transactional
    public void silenciarCategoria(UUID tenantId, UUID userId, String categoria) {
        CategoriaNotificacao resolvida = CategoriaNotificacao.fromString(categoria)
                .orElseThrow(() -> new IllegalArgumentException("categoria desconhecida: " + categoria));
        if (!resolvida.isSilenciavel()) {
            throw new IllegalArgumentException("categoria não silenciável: " + categoria);
        }
        // CR-01 (Phase 93 code review, iteration 3): upsert nativo atómico em vez de
        // existsBy...+saveAndFlush(...) dentro de um try/catch(DataIntegrityViolationException).
        // Esse padrão não funciona contra PostgreSQL real: o Postgres aborta a transação
        // INTEIRA assim que qualquer instrução viola uma constraint -- e este método é ele
        // próprio a fronteira @Transactional mais externa, pelo que apanhar a exceção
        // traduzida aqui dentro não "desaborta" a transação subjacente. Quando o método
        // retornasse normalmente, o COMMIT implícito do TransactionInterceptor seria feito
        // sobre uma transação já envenenada -- ou lança (relocando o sintoma original para o
        // commit) ou é silenciosamente tratado como ROLLBACK pelo driver, escondendo a falha
        // do chamador. O INSERT ... ON CONFLICT DO NOTHING elimina o problema por completo:
        // no caminho "já silenciada", nenhuma violação de constraint -- e portanto nenhuma
        // exceção -- é alguma vez lançada ao nível do SQL. Esta única chamada também
        // substitui o antigo pré-check existsBy...: o upsert atómico É o próprio check de
        // idempotência.
        notificacaoPreferenciaRepository.upsertSilenciar(tenantId, userId, categoria);
    }

    // NOTF-24: reativar (deixar de silenciar) uma categoria -- remove a linha de preferência se
    // existir. Idempotente: nenhum erro se a linha já não existir. @Transactional é obrigatório
    // para o delete derivado (mesma convenção do resto do repositório).
    @Transactional
    public void reativarCategoria(UUID tenantId, UUID userId, String categoria) {
        notificacaoPreferenciaRepository.deleteByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria);
    }
}
