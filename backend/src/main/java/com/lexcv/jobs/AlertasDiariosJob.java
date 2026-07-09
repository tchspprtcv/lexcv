package com.lexcv.jobs;

import com.lexcv.models.Evento;
import com.lexcv.models.Honorario;
import com.lexcv.models.Prazo;
import com.lexcv.models.Processo;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.EventoRepository;
import com.lexcv.repositories.HonorarioRepository;
import com.lexcv.repositories.NotificacaoRepository;
import com.lexcv.repositories.PrazoRepository;
import com.lexcv.repositories.ProcessoRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.NotificacaoService;
import com.lexcv.services.RiscoPrazoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Primeiro job {@code @Scheduled} do código-base: verificação diária cross-tenant de prazos de
 * processos, eventos de calendário críticos e honorários em atraso (NOTF-20/21/23).
 *
 * <p>Corre numa thread do scheduler do Spring, SEM {@code SecurityContextHolder}/JWT — por isso
 * {@code tenantId} é sempre um parâmetro explícito (nunca {@code getTenantId()}), obtido
 * iterando {@link TenantRepository#findAll()}. Reutiliza {@link RiscoPrazoService} para o risco
 * de prazo/evento (nenhuma 5ª cópia da lógica de limiar) e {@link NotificacaoService#criar} como
 * único ponto de escrita de notificações.
 *
 * <p>Isolamento de falha em 3 camadas (T-88-03): try/catch de topo em {@link #executar(LocalDate)},
 * try/catch por tenant dentro do loop de {@link TenantRepository#findAll()}, e try/catch por
 * entidade dentro de cada {@code processar*} — uma exceção não tratada num {@code @Scheduled}
 * pode cancelar silenciosamente todas as execuções futuras desse método (Spring Framework), daí
 * a defesa em profundidade.
 *
 * <p>Idempotência edge-triggered (T-88-04): antes de cada {@code criar(...)}, verifica-se
 * {@link NotificacaoRepository#existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria}
 * para o tuplo exato (tenant, destinatário, entidade, categoria) — só a primeira vez que uma
 * entidade cruza para um nível cria uma linha nova; correr o job de novo sobre dados inalterados
 * não duplica nada.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertasDiariosJob {

    // Honorário: dias sem pagamento total desde dataAcordo para ser considerado atrasado
    // (88-CONTEXT.md, lógica própria — fora do RiscoPrazoService, que só cobre prazo/evento).
    private static final int DIAS_HONORARIO_ATRASADO = 30;

    private final TenantRepository tenantRepository;
    private final ProcessoRepository processoRepository;
    private final PrazoRepository prazoRepository;
    private final EventoRepository eventoRepository;
    private final HonorarioRepository honorarioRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final UserRepository userRepository;
    private final RiscoPrazoService riscoPrazoService;
    private final NotificacaoService notificacaoService;

    // Entry point do Spring — sem parâmetros (exigido pelo @Scheduled). Zona explícita
    // Atlantic/Cape_Verde: o container corre em UTC, Cabo Verde é UTC-1 (decisão travada em
    // 88-CONTEXT.md, não usar fixedRate/fixedDelay).
    @Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")
    public void executar() {
        executar(LocalDate.now());
    }

    // Overload package-private com `hoje` injetável — é este que o teste chama diretamente,
    // nunca o executar() sem argumentos, para determinismo total sem contexto Spring.
    void executar(LocalDate hoje) {
        try {
            for (Tenant tenant : tenantRepository.findAll()) {
                try {
                    processarTenant(tenant.getId(), hoje);
                } catch (Exception e) {
                    log.error("Falha ao processar alertas diários para tenant {}", tenant.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Falha inesperada na execução do job de alertas diários", e);
        }
    }

    // Preload único por tenant (evita N+1): mapa de processos por id + lista de ADMINs, ambos
    // reutilizados pelos 3 processar* abaixo, nunca re-consultados por entidade.
    private void processarTenant(UUID tenantId, LocalDate hoje) {
        List<Processo> processos = processoRepository.findByTenantId(tenantId);
        Map<UUID, Processo> processoPorId = processos.stream()
                .collect(Collectors.toMap(Processo::getId, p -> p));
        List<User> admins = userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN");

        processarPrazos(tenantId, hoje, processoPorId, admins);
        processarEventos(tenantId, hoje, processoPorId, admins);
        processarHonorarios(tenantId, hoje, processoPorId, admins);
    }

    private void processarPrazos(UUID tenantId, LocalDate hoje, Map<UUID, Processo> processoPorId,
                                  List<User> admins) {
        for (Prazo prazo : prazoRepository.findByTenantId(tenantId)) {
            if (Boolean.TRUE.equals(prazo.getConcluido())) {
                continue;
            }
            try {
                String risco = riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade(), hoje);
                if (RiscoPrazoService.OK.equals(risco)) {
                    continue;
                }
                String categoria = RiscoPrazoService.PROXIMO.equals(risco) ? "PRAZO_PROXIMO" : "PRAZO_VENCIDO";
                Processo processo = processoPorId.get(prazo.getProcessoId());
                UUID responsavelId = processo != null ? processo.getResponsavelId() : null;
                String numeroTexto = numeroProcesso(processo);
                String titulo = RiscoPrazoService.PROXIMO.equals(risco) ? "Prazo a vencer" : "Prazo vencido";
                String mensagem = "O prazo \"" + prazo.getDescricao() + "\" do processo " + numeroTexto
                        + (RiscoPrazoService.PROXIMO.equals(risco) ? " está próximo do limite." : " está vencido.");
                String linkUrl = "/processos/" + prazo.getProcessoId();
                String entidadeId = prazo.getId().toString();

                notificar(tenantId, responsavelId, categoria, titulo, mensagem, "prazo", entidadeId, linkUrl);
                for (User admin : admins) {
                    notificar(tenantId, admin.getId(), categoria, titulo, mensagem, "prazo", entidadeId, linkUrl);
                }
            } catch (Exception e) {
                log.warn("Falha ao processar prazo {} do tenant {}", prazo.getId(), tenantId, e);
            }
        }
    }

    private void processarEventos(UUID tenantId, LocalDate hoje, Map<UUID, Processo> processoPorId,
                                   List<User> admins) {
        for (Evento evento : eventoRepository.findByTenantIdAndConcluido(tenantId, false)) {
            try {
                String risco = riscoPrazoService.computeRiscoEvento(evento.getDataInicio(), evento.getPrioridade(), hoje);
                if (RiscoPrazoService.OK.equals(risco)) {
                    continue;
                }
                String categoria = RiscoPrazoService.PROXIMO.equals(risco) ? "EVENTO_PROXIMO" : "EVENTO_VENCIDO";
                UUID processoId = evento.getProcessoId();
                Processo processo = processoId != null ? processoPorId.get(processoId) : null;
                UUID responsavelId = processo != null ? processo.getResponsavelId() : null;
                String titulo = RiscoPrazoService.PROXIMO.equals(risco) ? "Evento a aproximar-se" : "Evento em atraso";
                String mensagem = "O evento \"" + evento.getTitulo() + "\" "
                        + (RiscoPrazoService.PROXIMO.equals(risco) ? "está a aproximar-se." : "está em atraso.");
                // Evento sem processo associado (ex.: compromisso interno) -> sem página de
                // detalhe própria, aponta para a agenda geral.
                String linkUrl = processoId != null ? "/processos/" + processoId : "/agenda";
                String entidadeId = String.valueOf(evento.getId());

                notificar(tenantId, responsavelId, categoria, titulo, mensagem, "evento", entidadeId, linkUrl);
                for (User admin : admins) {
                    notificar(tenantId, admin.getId(), categoria, titulo, mensagem, "evento", entidadeId, linkUrl);
                }
            } catch (Exception e) {
                log.warn("Falha ao processar evento {} do tenant {}", evento.getId(), tenantId, e);
            }
        }
    }

    private void processarHonorarios(UUID tenantId, LocalDate hoje, Map<UUID, Processo> processoPorId,
                                      List<User> admins) {
        // Batch único via processoPorId.keySet() -- nunca findByProcessoId em loop (Pitfall 7).
        for (Honorario honorario : honorarioRepository.findByProcessoIdIn(processoPorId.keySet())) {
            try {
                if (honorario.getValorTotal() == null || honorario.getDataAcordo() == null) {
                    // Honorário ainda não preenchido na formalização (Phase 82) -- ignorado
                    // silenciosamente, nunca é um erro.
                    continue;
                }
                if (honorario.getTotalPago() != null
                        && honorario.getTotalPago().compareTo(honorario.getValorTotal()) >= 0) {
                    continue;
                }
                long dias = ChronoUnit.DAYS.between(honorario.getDataAcordo(), hoje);
                if (dias < DIAS_HONORARIO_ATRASADO) {
                    continue;
                }
                Processo processo = processoPorId.get(honorario.getProcessoId());
                UUID responsavelId = processo != null ? processo.getResponsavelId() : null;
                String numeroTexto = numeroProcesso(processo);
                String titulo = "Honorário em atraso";
                String mensagem = "O honorário do processo " + numeroTexto
                        + " está sem pagamento total há " + dias + " dias.";
                String linkUrl = "/processos/" + honorario.getProcessoId();
                String entidadeId = String.valueOf(honorario.getId());

                notificar(tenantId, responsavelId, "HONORARIO_ATRASADO", titulo, mensagem, "honorario",
                        entidadeId, linkUrl);
                for (User admin : admins) {
                    notificar(tenantId, admin.getId(), "HONORARIO_ATRASADO", titulo, mensagem, "honorario",
                            entidadeId, linkUrl);
                }
            } catch (Exception e) {
                log.warn("Falha ao processar honorário {} do tenant {}", honorario.getId(), tenantId, e);
            }
        }
    }

    // Choke point único: nunca chama notificacaoRepository.save(...) diretamente -- toda a
    // escrita passa por notificacaoService.criar(...). Idempotência via existence-check antes de
    // cada chamada; ordem responsavel-primeiro-depois-admins faz um responsavel-que-é-também-admin
    // ser deduplicado naturalmente (a segunda existence-check já encontra a linha da primeira).
    private void notificar(UUID tenantId, UUID destinatarioId, String categoria, String titulo, String mensagem,
                            String entidadeTipo, String entidadeId, String linkUrl) {
        if (destinatarioId == null) {
            return;
        }
        if (notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
                tenantId, destinatarioId, entidadeTipo, entidadeId, categoria)) {
            return;
        }
        try {
            notificacaoService.criar(tenantId, destinatarioId, categoria, titulo, mensagem, entidadeTipo,
                    entidadeId, linkUrl);
        } catch (IllegalArgumentException ex) {
            log.warn("{}: destinatario {} inválido/órfão, notificação ignorada para este destinatário",
                    categoria, destinatarioId, ex);
        } catch (DataIntegrityViolationException ex) {
            // WR-01 (Phase 88 code review): backstop for uk_notificacao_dedup (migration
            // 88-add-notificacao-dedup-unique-constraint.sql). The existence-check above is a
            // check-then-act race under concurrent execution; if the DB-level unique index still
            // rejects a duplicate insert, fail closed here instead of throwing out of this
            // per-entidade try/catch.
            log.warn("{}: notificação duplicada rejeitada pelo índice único da BD para destinatario {}, ignorada",
                    categoria, destinatarioId, ex);
        }
    }

    private static String numeroProcesso(Processo processo) {
        if (processo == null || processo.getNumeroProcesso() == null) {
            return "(sem número)";
        }
        return processo.getNumeroProcesso();
    }
}
