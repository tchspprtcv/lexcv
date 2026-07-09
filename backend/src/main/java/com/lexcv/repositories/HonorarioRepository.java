package com.lexcv.repositories;

import com.lexcv.models.Honorario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HonorarioRepository extends JpaRepository<Honorario, Integer> {
    List<Honorario> findByProcessoId(UUID processoId);

    // Batch fetch for the daily alertas job (Plan 88-02): avoids looping findByProcessoId once
    // per processo (N+1 anti-pattern flagged in PITFALLS.md Pitfall 7).
    List<Honorario> findByProcessoIdIn(Collection<UUID> processoIds);
}
