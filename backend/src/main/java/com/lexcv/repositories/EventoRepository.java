package com.lexcv.repositories;

import com.lexcv.models.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventoRepository extends JpaRepository<Evento, Integer> {
    List<Evento> findByTenantId(UUID tenantId);
    List<Evento> findByTenantIdAndProcessoId(UUID tenantId, UUID processoId);
    List<Evento> findByTenantIdAndConcluido(UUID tenantId, Boolean concluido);
    List<Evento> findByTenantIdAndDataInicioBetween(UUID tenantId, LocalDateTime start, LocalDateTime end);
}
