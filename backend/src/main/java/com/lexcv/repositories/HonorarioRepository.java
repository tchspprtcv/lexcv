package com.lexcv.repositories;

import com.lexcv.models.Honorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HonorarioRepository extends JpaRepository<Honorario, Integer> {
    List<Honorario> findByProcessoId(UUID processoId);

    // Batch fetch for the daily alertas job (Plan 88-02): avoids looping findByProcessoId once
    // per processo (N+1 anti-pattern flagged in PITFALLS.md Pitfall 7).
    List<Honorario> findByProcessoIdIn(Collection<UUID> processoIds);

    /**
     * Phase 134 (CR-01 da revisão): processo atual do honorário, lido como escalar. Executa sempre
     * um SELECT (ao contrário de {@code findById}, que devolve a instância já gerida no contexto de
     * persistência), por isso vê uma eliminação confirmada por outra transação.
     */
    @Query("select h.processoId from Honorario h where h.id = :id")
    Optional<UUID> processoIdPorId(@Param("id") Integer id);
}
