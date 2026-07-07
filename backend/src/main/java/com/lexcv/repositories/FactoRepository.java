package com.lexcv.repositories;

import com.lexcv.models.Facto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FactoRepository extends JpaRepository<Facto, Integer> {
    List<Facto> findByProcessoId(UUID processoId);
    List<Facto> findByProcessoIdOrderByOrdemAsc(UUID processoId);
}
