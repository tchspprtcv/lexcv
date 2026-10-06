package com.lexcv.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Sole responsibility: turn on Spring's @Scheduled infrastructure (auto-configures a
// ThreadPoolTaskScheduler; application.yml sets spring.task.scheduling.pool.size: 3 since Phase 136,
// so FiscalOutboxJob never blocks AlertasDiariosJob). Kept as its own dedicated @Configuration class
// (not on BackendApplication) matching the one-concern-per-@Configuration convention already
// set by MinioConfig/SecurityConfig. The AlertasDiariosJob @Scheduled trigger (Plan 88-02)
// will not fire without this being present somewhere in the context.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
