package com.lexcv.jobs;

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

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
 * <p>A implementação do scan (Task 2 / GREEN) preenche {@link #executar(LocalDate)}; esta versão
 * (Task 1 / RED) é um esqueleto compilável com corpo vazio, para que
 * {@code AlertasDiariosJobTest} corra e falhe por razões comportamentais (nenhuma notificação é
 * criada), não por erro de compilação.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertasDiariosJob {

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
    // Corpo implementado na Task 2 (GREEN).
    void executar(LocalDate hoje) {
    }
}
