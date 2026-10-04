package com.lexcv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Phase 133: {@link Clock} injetável (UTC) para todo o código fiscal.
 *
 * <p>Os serviços derivam datas de Cabo Verde com {@code ZoneId.of("Atlantic/Cape_Verde")} a
 * partir deste {@code Clock} (ex.: {@code LocalDate.now(clock.withZone(FUSO_CABO_VERDE))}),
 * nunca com {@code LocalDate.now()} sem argumentos, que usa o fuso da JVM/container -- mesmo
 * precedente de {@code AlertasDiariosJob.FUSO_CABO_VERDE} (WR-02 da Phase 88). Os testes
 * injetam {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
