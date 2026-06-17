package com.lexcv.repositories;

import com.lexcv.models.ProcessoFase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProcessoFaseRepository extends JpaRepository<ProcessoFase, Integer> {
    List<ProcessoFase> findByProcessoId(UUID processoId);
}
