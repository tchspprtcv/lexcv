package com.lexcv.repositories;

import com.lexcv.models.Facto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactoRepository extends JpaRepository<Facto, Integer> {
    List<Facto> findByProcessoId(UUID processoId);
    List<Facto> findByProcessoIdOrderByOrdemAsc(UUID processoId);

    @Query("SELECT MAX(f.ordem) FROM Facto f WHERE f.processoId = :processoId")
    Optional<Integer> findMaxOrdemByProcessoId(@Param("processoId") UUID processoId);
}
