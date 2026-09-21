package com.lexcv.seed;

import com.lexcv.services.MigracaoPapeisEscritorioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Phase 126 Plan 03 (MIGR-01/MIGR-02): corre em TODO o arranque, {@link Ordered#LOWEST_PRECEDENCE}
 * -- depois de {@link DatabaseSeeder} (ordenado 100 posições antes desta), do qual esta conversão
 * depende para {@code Role.instanciavel} já ter convergido.
 *
 * <p>Corre INCONDICIONALMENTE, sem gate {@code app.seed.enabled} -- exactamente como {@code
 * DatabaseSeeder.seedRbac()}, que corre antes do gate. A conversão tem de acontecer em produção,
 * onde {@code SEED_ENABLED=false}, senão a fase não converte nenhum escritório real.
 *
 * <p>Uma excepção lançada por {@link MigracaoPapeisEscritorioService#migrar()} — nomeadamente a
 * {@code IllegalStateException} da verificação de deriva zero — propaga sem ser capturada e
 * ABORTA O ARRANQUE por desenho: um backend que não sobe é um sintoma visível; um backend que
 * sobe com autorizações alteradas não é (T-126-15, 126-CONTEXT.md). Não capturar, não converter
 * em log, não engolir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class MigracaoPapeisRunner implements CommandLineRunner {

    private final MigracaoPapeisEscritorioService migracaoPapeisEscritorioService;

    @Override
    public void run(String... args) throws Exception {
        migracaoPapeisEscritorioService.migrar();
    }
}
